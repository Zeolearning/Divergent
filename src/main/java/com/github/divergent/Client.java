package com.github.divergent;

import com.alibaba.fastjson2.*;
import com.github.divergent.divide.Cluster;
import com.github.divergent.divide.StopWatch;
import com.github.divergent.model.Diff;
import com.github.divergent.model.Patch;
import com.github.divergent.model.Region;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Properties;
import java.awt.*;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat;
import static com.alibaba.fastjson2.JSONWriter.Feature.WriteNulls;


public class Client {
    private static final Logger logger = LogManager.getLogger(Client.class);
    private static final File projects;
    private static final File dataset;
    private static final File output;
    private static File timeLog, groups;
    private static final boolean debug = false;

    static {
        try (InputStream in = Client.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new RuntimeException("找不到 config.properties");
            }
            Properties prop = new Properties();
            prop.load(in);

            projects = new File(prop.getProperty("projects.path"));
            dataset  = new File(prop.getProperty("dataset.path"));
            output   = new File(prop.getProperty("output.path"));

            // 可选：确保目录存在
            if (!projects.exists())  projects.mkdirs();
            if (!dataset.exists())   dataset.mkdirs();
            if (!output.exists())    output.mkdirs();

        } catch (Exception e) {
            throw new ExceptionInInitializerError("加载配置失败: " + e.getMessage());
        }
    }

    // args[4] = {projects, dataset, output, repos}
    public static void main(String[] args) throws Exception {
        if (output.exists()) {
//            FileUtils.cleanDirectory(output);
        } else {
            FileUtils.forceMkdirParent(output);
        }
        if (!projects.exists() || !dataset.exists()) {
            logger.error("Please check the path of projects and dataset");
            return;
        }
        timeLog = new File(output, "logs");
        groups = new File(output, "groups");
        FileUtils.forceMkdir(timeLog);
        FileUtils.forceMkdir(groups);

        if (debug) {
            analyze("nomulus",true);
            return;
        }
        boolean first = true; // 默认值
        String name="groovy";
        for (String arg : args) {
            if (arg.startsWith("--first=")) {
                String value = arg.substring("--first=".length());
                first = Boolean.parseBoolean(value);
            }
            else if(arg.startsWith("--repo=")) {
                String value = arg.substring("--repo=".length());
                name=value;
            }
        }

//        List<String> names = Files.readLines(new File("./evaluation/data-scripts/repos.txt"),
//                                            Charset.defaultCharset());

        logger.info("Start analyzing {}", name);
        try {
            analyze(name,first);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static void analyze(String name,boolean first) throws Exception {
        String jsonFile = name + ".json";
        File log = new File(timeLog, name + ".log");
        File temp = new File(output, name);
        File repo = new File(projects, name);

        byte[] bytes = FileUtils.readFileToByteArray(new File(dataset, jsonFile));
        JSONArray input = JSON.parseArray(bytes);
        JSONArray output = new JSONArray();

        long sum = 0;
        int size = input.size(), failCount = 0;
        File outFile = new File(groups, jsonFile);

        for (int i = 0; i < size; i++) {
            List<String> commits = input.getJSONArray(i).toList(String.class);
            String hash1 = commits.get(0), hash2 = commits.get(commits.size() - 1);

            StopWatch stopWatch = StopWatch.getInstance();
            stopWatch.mark("analyze");
            Diff diff = new Diff(repo, temp, hash1, hash2, true);
            Cluster cluster = new Cluster(diff);
            cluster.compute();
            if (cluster.isFailed()) {
                try (FileWriter fw = new FileWriter(outFile, true)) { // append 模式
                    if (!first) {
                        fw.write(",\n");
                    }
                    JSONObject obj = toJSONObject(cluster.getResult(), i);
                    String jsonText = JSON.toJSONString(obj, PrettyFormat, WriteNulls);
                    fw.write(jsonText);
                }
                logger.error("Case {} has errors and will be skipped", i + 1);
                failCount++;
            } else {
                long cost = stopWatch.finish("analyze");
                sum += cost;
                String timeRep = String.format("Case %d/%d: run cost %dms, refactor cost %dms",
                        i, size - 1, cost, stopWatch.getTime("refactor"));
                logger.info(timeRep);
                FileUtils.writeLines(log, List.of(timeRep), true);
//                output.add(toJSONObject(cluster.getResult(), i));
                JSONObject obj = toJSONObject(cluster.getResult(), i);
                String jsonText = JSON.toJSONString(obj, PrettyFormat, WriteNulls);
                try (FileWriter fw = new FileWriter(outFile, true)) { // append 模式
                    if (!first) {
                        fw.write(",\n");
                    }
                    fw.write(jsonText);
                }
                first = false;
            }
            diff.cleanUp();
            stopWatch.reset();
            JavaParserFacade.clearInstances();
            cluster.cleanUp();
            if (debug) {
                break;
            }
        }

        assert size != failCount;
        String avgRep = String.format("Total cost %dms, average %dms per case", sum, sum / (size - failCount));
        logger.debug(avgRep);
        logger.debug("Fail count: {}", failCount);
        FileUtils.writeLines(log, List.of(avgRep), true);

//        String data = JSON.toJSONString(output, PrettyFormat, WriteNulls);
//        FileUtils.write(new File(groups, jsonFile), data, Charset.defaultCharset());
        FileUtils.deleteDirectory(temp);
    }

    private static JSONObject toJSONObject(List<List<Patch>> result, int index) {
        JSONArray array = new JSONArray();
        for (List<Patch> list : result) {
            JSONArray group = new JSONArray();
            for (Patch item : list) {
                JSONObject obj = new JSONObject();
                if (item.hasPrev()) {
                    Region prev = item.getPrev();
                    obj.put("leftPath", prev.getPath());
                    obj.put("leftBegin", prev.getBegin());
                    obj.put("leftEnd", prev.getEnd());
                } else {
                    obj.put("leftPath", null);
                }
                if (item.hasNext()) {
                    Region next = item.getNext();
                    obj.put("rightPath", next.getPath());
                    obj.put("rightBegin", next.getBegin());
                    obj.put("rightEnd", next.getEnd());
                } else {
                    obj.put("rightPath", null);
                }
                group.add(obj);
            }
            array.add(group);
        }
        JSONObject ret = new JSONObject();
        ret.put("groups", array);
        ret.put("index", index);
        return ret;
    }

}
