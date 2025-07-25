package com.github.divergent.divide;

import com.github.divergent.analysis.GraphBuilder;
import com.github.divergent.model.*;
import com.github.divergent.model.Patch;
import com.github.divergent.utils.DotDumper;
import com.github.javaparser.Position;
import gr.uom.java.xmi.diff.CodeRange;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.divergent.divide.HyperEdge.Type;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import static com.github.divergent.divide.TokenDiff.executeCommand;
import static com.github.divergent.divide.TokenDiff.parseInfo;
import static com.github.divergent.utils.StringReps.isComment;
import static org.apache.commons.io.FilenameUtils.separatorsToUnix;

public class Cluster {
    private static final Logger logger = LogManager.getLogger(Cluster.class);
    private final Diff diff;
    private final List<List<Patch>> groups;
    private final Graph<Patch, HyperEdge> graph;
    private boolean failed = false;
    private static int count = 0;
    private ScheduledExecutorService exitService = Executors.newSingleThreadScheduledExecutor();

    public Cluster(Diff diff) {
        this.diff = diff;
        this.groups = new ArrayList<>();
        this.graph = new Graph<>();
    }
    public void cleanUp(){
        this.groups.clear();
        this.graph.clear();
    }
    public void compute() {
        buildGraph();
        if (failed) {
            return;
        }

        detectTextual();
        groups.addAll(new Tarjan<>(graph).getComponents());
        Set<Patch> visited = groups.stream().flatMap(List::stream).collect(Collectors.toSet());
        DisjointSet<Patch> union = new DisjointSet<>();
        for (Patch cur : diff.getPatchList()) {
            if (visited.contains(cur)) {
                continue;
            }
            union.add(cur);
            for (Patch succ : graph.getSuccsOf(cur)) {
                if (!visited.contains(succ)) {
                    union.add(succ);
                    union.merge(cur, succ);
                }
            }
        }
        groups.addAll(union.getDisjointSets());
//        logger.debug("Final group count: {}", groups.size());
    }

    private class MergeTask extends Thread {
        private final Snapshot snapshot;
        public MergeTask(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public void run() {
            GraphBuilder builder = new GraphBuilder(snapshot);
            if (!builder.build()) {
                failed = true;
                return;
            }
            Graph<TreeNode, Edge> treeNodeGraph = builder.getGraph();
            Map<TreeNode, List<Patch>> coverMap = new HashMap<>(treeNodeGraph.getNodeCount());

            int prefixLen = snapshot.getRoot().getPath().length() + 1;
            for (TreeNode node : treeNodeGraph.getNodes()) {
                String path = separatorsToUnix(node.getPath().substring(prefixLen));
                FileView view = snapshot.getView(path);
                Info info = node.getInfo();
                if (view == null) {
                    continue;
                }
                List<Patch> cover = new ArrayList<>();
                for (Region region : view.getRegions()) {
                    boolean flag = region.getTokens().stream().anyMatch(info::intersect);
                    if (flag) {
                        cover.add(diff.getPatch(region.getIndex()));
                    }
                }
                if (!cover.isEmpty()) {
                    coverMap.put(node, cover);
                }
            }
            coverMap.forEach((node, cover) -> {
//                logger.error("{} -> {}", node, cover);
                List<Patch> succs = treeNodeGraph.getSuccsOf(node).stream().filter(coverMap::containsKey)
                        .flatMap(succ -> coverMap.get(succ).stream()).toList();
                for (Patch src : cover) {
                    for (Patch tgt : succs) {
                        addEdge(src, tgt, Type.DEPEND);
                    }
                }
            });

//            DotDumper<TreeNode, Edge> dumper = new DotDumper<>();
//            dumper.dump(treeNodeGraph, new File(String.format("output/hag%d.dot", count)));
//            count++;
        }

        @Override
        public UncaughtExceptionHandler getUncaughtExceptionHandler() {
            return (thread, e) -> {
                logger.error("Thread {} throws an exception: {} - {}", thread.getName(), e.getClass().getName(), e.getMessage());
                Arrays.stream(e.getStackTrace()).forEach(trace -> {
                    logger.error("\t{}", trace);
                });
                exitService.schedule(() -> {
                    System.exit(1);
                }, 15, TimeUnit.SECONDS);
            };
        }
    }

    private void buildGraph() {
        StopWatch.getInstance().mark("graph");
        for (Patch patch : diff.getPatchList()) {
            graph.addNode(patch);
            tokenDiff(patch.getPrev(), patch.getNext());
        }
        MergeTask task1 = new MergeTask(diff.getPrev());
        MergeTask task2 = new MergeTask(diff.getNext());
        task1.start();
        task2.start();

        detectRefactor();
        detectClone();
        try {
            task1.join();
            task2.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        StopWatch.getInstance().finish("graph");

        DotDumper<Patch, HyperEdge> dumper = new DotDumper<>();
        dumper.dump(graph, new File("output/patch.dot"));
    }

    private synchronized void addEdge(Patch source, Patch target, Type type) {
        // There is no loop in final graph
        if (!source.equals(target)) {
            graph.addEdge(source, target, new HyperEdge(source, target, type));
        }
    }

    public void tokenDiff(Region prev, Region next) {
        if (prev == null) {
            Info info = new Info(new Position(next.getBegin(), 1), new Position(next.getEnd() + 1, 0));
            next.addInfo(info);
        } else if (next == null) {
            Info info = new Info(new Position(prev.getBegin(), 1), new Position(prev.getEnd() + 1, 0));
            prev.addInfo(info);
        } else {
            File dir = diff.getTempDir();
            List<String> code1 = prev.getCode();
            List<String> code2 = next.getCode();
            try {
                File f1 = new File(dir, "f1.txt");
                File f2 = new File(dir, "f2.txt");
                FileUtils.writeLines(f1, code1);
                FileUtils.writeLines(f2, code2);

                List<Info> delete = parseInfo(executeCommand(dir, f2, f1), code1);
                List<Info> insert = parseInfo(executeCommand(dir, f1, f2), code2);
                for (Info info : delete) {
                    prev.addInfo(info.rowOffset(prev.getBegin()));
                }
                for (Info info : insert) {
                    next.addInfo(info.rowOffset(next.getBegin()));
                }
//                FileUtils.delete(f1);
//                FileUtils.delete(f2);
            } catch (IOException e) {
                logger.error("Exception occurred when token diff: {}", e.getMessage());
            }
        }
    }

    private void detectRefactor() {
        StopWatch.getInstance().mark("refactor");
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
        List<Refactoring> refs = new ArrayList<>();
        try {
            miner.detectAtDirectories(diff.getPrev().getRoot(), diff.getNext().getRoot(), new RefactoringHandler() {
                @Override
                public void handle(String id, List<Refactoring> result) {
                    refs.addAll(result);
                }
            });
        } catch (RuntimeException e) {
            failed = true;
            logger.error("Failed to detect refactoring due to: {}", e.getMessage());
        }
//        logger.debug(refs);
        StopWatch.getInstance().finish("refactor");

        for (Refactoring ref : refs) {
            Set<Integer> index = new HashSet<>();
            getInvolvedRegions(ref.leftSide(), diff.getPrev(), index);
            getInvolvedRegions(ref.rightSide(), diff.getNext(), index);
            List<Patch> involved = index.stream().map(diff::getPatch).toList();

            for (int i = 1; i < involved.size(); i++) {
                Patch x = involved.get(i), y = involved.get(i - 1);
                addEdge(x, y, Type.REFACTOR);
                addEdge(y, x, Type.REFACTOR);
            }
        }
    }

    private void getInvolvedRegions(List<CodeRange> codeRanges, Snapshot snapshot, Set<Integer> index) {
        codeRanges.forEach(codeRange -> {
            FileView view = snapshot.getView(codeRange.getFilePath());
            if (view != null) {
                for (Region region : view.getRegions()) {
                    if (region.intersect(codeRange.getStartLine(), codeRange.getEndLine())) {
                        index.add(region.getIndex());
                    }
                }
            }else{
                System.out.println("Failed to get view: " + codeRange.getFilePath());
            }
        });
    }

    private void detectClone() {
        List<Patch> items = diff.getPatchList();
        var i = items.listIterator();
        while (i.hasNext()) {
            Patch pi = i.next();
            var j = items.listIterator(i.nextIndex());
            while (j.hasNext()) {
                Patch pj = j.next();
                if (pathEquals(pi, pj) && pi.cosine(pj) >= 0.85) {
                    addEdge(pi, pj, Type.CLONE);
                }
            }
        }
    }

    private void detectTextual() {
        List<Patch> ret = new ArrayList<>();
        List<Patch> patches = diff.getPatchList();
        for (Patch patch : patches) {
            boolean flag = true;
            if (patch.hasPrev()) {
                Region prev = patch.getPrev();
                flag &= isComment(prev.formatCode()) || prev.getTokens().isEmpty();
            }
            if (patch.hasNext()) {
                Region next = patch.getNext();
                flag &= isComment(next.formatCode()) || next.getTokens().isEmpty();
            }
            if (!flag) {
                continue;
            }
            for (Patch pre : ret) {
                if (pathEquals(patch, pre)) {
                    addEdge(patch, pre, Type.TRIVIAL);
                }
            }
            ret.add(patch);
        }
    }

    private boolean pathEquals(Patch x, Patch y) {
        boolean res = true;
        if (x.hasPrev()) {
            res &= x.getPrev().pathEquals(y.getPrev());
        }
        if (x.hasNext()) {
            res &= x.getNext().pathEquals(y.getNext());
        }
        return res;
    }

    public boolean isFailed() {
        return failed;
    }

    public List<List<Patch>> getResult() {
        return groups;
    }
}