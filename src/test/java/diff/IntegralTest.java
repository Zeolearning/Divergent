package diff;

import com.github.divergent.divide.Cluster;
import com.github.divergent.model.Info;
import com.github.divergent.model.Patch;
import com.github.divergent.model.Diff;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.*;

import static com.github.divergent.divide.TokenDiff.executeCommand;
import static com.github.divergent.divide.TokenDiff.parseInfo;


public class IntegralTest {
    private static final Logger logger = LogManager.getLogger(IntegralTest.class);

    @Test
    public void testGitRepo() throws Exception {
        String userDir = System.getProperty("user.dir");
        File project = new File(userDir);
        File copy = new File("/root/evaluation/output");
        Diff diff = new Diff(project, copy, "a989211", "080c04f", false);
        Cluster cluster = new Cluster(diff);
        cluster.compute();
        List<List<Patch>> groups = cluster.getResult();

        logger.trace("Group size: {}", groups.size());
        for (int i = 0; i < groups.size(); i++) {
            logger.trace("Group {}({}): {}", i + 1, groups.get(i).size(), groups.get(i));
        }
        diff.cleanUp();
    }

//    @Test
//    public void testGitWordDiff() throws IOException, InterruptedException {
//        ProcessBuilder processBuilder = new ProcessBuilder("git", "diff", "--word-diff=porcelain", "a.txt", "b.txt");
//        processBuilder.directory(new File("/Users/user/Desktop/diff"));
//        Process process = processBuilder.start();
//        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//        String line;
//        while ((line = reader.readLine()) != null) {
//            System.out.println(line);  // 打印 diff 输出
//        }
//
//        // 等待 diff 命令执行完毕并获取退出码
//        int exitCode = process.waitFor();
//        System.out.println("Git diff executed with exit code: " + exitCode);
//    }

//    @Test
//    public void testTokenDiff() throws IOException, InterruptedException {
//        File tmpDir = new File("C:\\Users\\user\\Desktop\\divergent\\evaluation\\output\\nomulus");
//
//        File f1 = new File(tmpDir, "f1.txt");
//        File f2 = new File(tmpDir, "f2.txt");
//
//        List<Info> delete = parseInfo(executeCommand(tmpDir, f2, f1), FileUtils.readLines(f1, Charset.defaultCharset()));
//        List<Info> insert = parseInfo(executeCommand(tmpDir, f1, f2), FileUtils.readLines(f2, Charset.defaultCharset()));
//
//        logger.trace("delete: {}", delete);
//        logger.trace("insert: {}", insert);
//    }

}
