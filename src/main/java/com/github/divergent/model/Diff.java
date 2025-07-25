package com.github.divergent.model;

import com.github.divergent.divide.StopWatch;
import com.github.divergent.utils.StringReps;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.treewalk.*;
import org.eclipse.jgit.util.io.NullOutputStream;

import java.io.*;
import java.util.*;

import static com.github.divergent.utils.GitUtils.*;
import static org.eclipse.jgit.diff.DiffEntry.ChangeType;

public class Diff {
	private static final Logger logger = LogManager.getLogger(Diff.class);
	private final Snapshot prev;
	private final Snapshot next;
	private final File temp;
	private final Map<Integer, Patch> indexer;

	public Diff(File project, File tempDir, String hash1, String hash2, boolean onlyDiff) throws IOException {
		logger.info("Comparing: {} -- {}", hash1 ,hash2);

		this.indexer = new HashMap<>();
		this.temp = tempDir.getCanonicalFile();
		this.prev = new Snapshot(new File(temp, hash1));
		this.next = new Snapshot(new File(temp, hash2));

		Repository repo = openRepository(project);
		var iter1 = getTreeIterator(repo, hash1);
		var iter2 = getTreeIterator(repo, hash2);
		if (!onlyDiff) {
			deepCopy(repo, iter1, prev);
			deepCopy(repo, iter2, next);
		}
		process(repo, detectDiff(repo, iter1, iter2), onlyDiff);
	}

	private void deepCopy(Repository repo, AbstractTreeIterator iter, Snapshot snapshot) {
		try (TreeWalk walk = new TreeWalk(repo)) {
			walk.addTree(iter);
			walk.setRecursive(true);
//			walk.setFilter(new PathFilter());
			while (walk.next()) {
				byte[] bytes = read(repo, walk.getObjectId(0));
				snapshot.makeFile(walk.getPathString(), new String(bytes));
			}
			iter.reset();
		} catch (IOException e) {
			logger.error(e);
		}
	}

	private void process(Repository repo, List<DiffEntry> diff, boolean onlyDiff) throws IOException {
		DiffFormatter df = new DiffFormatter(NullOutputStream.INSTANCE);
		df.setRepository(repo);
		df.setDiffComparator(RawTextComparator.WS_IGNORE_CHANGE);
		StopWatch.getInstance().mark("diff");

		int index = 0;
		for (DiffEntry entry : diff) {
			ChangeType changeType = entry.getChangeType();

			EditList edits = df.toFileHeader(entry).toEditList();
			FileView leftView = new FileView(entry.getOldPath());
			FileView rightView = new FileView(entry.getNewPath());

			for (Edit edit : edits) {
				Region left = null, right = null;
				if (changeType != ChangeType.ADD) {
					left = new Region(leftView, index, edit.getBeginA() + 1, edit.getEndA());
					if (edit.getLengthA() > 0) {
						leftView.addRegion(left);
					}
				}
				if (changeType != ChangeType.DELETE) {
					right =  new Region(rightView, index, edit.getBeginB() + 1, edit.getEndB());
					if (edit.getLengthB() > 0 ) {
						rightView.addRegion(right);
					}
				}
				indexer.put(index, new Patch(left, right));
				index++;
			}
			if (!leftView.isEmpty()) {
				String content = new String(read(repo, entry.getOldId()));
				saveView(leftView, content, prev, onlyDiff);
			}
			if (!rightView.isEmpty()) {
				String content = new String(read(repo, entry.getNewId()));
				saveView(rightView, content, next, onlyDiff);
			}
		}
		StopWatch.getInstance().finish("diff");
	}

	private void saveView(FileView view, String content, Snapshot snapshot, boolean onlyDiff) throws IOException {
		view.setContent(content);
		List<String> codes = StringReps.splitToLines(content);
		view.getRegions().forEach(r -> r.setCode(codes.subList(r.getBegin() - 1, r.getEnd())));
		snapshot.addView(view.getPath(), view);
		if (onlyDiff) {
			snapshot.makeFile(view.getPath(), content);
		}
	}

	public void cleanUp() throws IOException {
		prev.reset();
		next.reset();
		indexer.clear();
		FileUtils.deleteDirectory(temp);
	}

	public Patch getPatch(int index) {
		return indexer.get(index);
	}

	public Snapshot getPrev() {
		return prev;
	}

	public Snapshot getNext() {
		return next;
	}

	public List<Patch> getPatchList() {
		return indexer.values().stream().toList();
	}

	public File getTempDir() {
		return temp;
	}

}
