package com.github.divergent.divide;

import com.github.divergent.model.Info;
import com.github.javaparser.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class TokenDiff {
	private static final Logger logger = LoggerFactory.getLogger(TokenDiff.class);

	// Get info for added tokens
	public static List<Info> parseInfo(List<String> output, List<String> code) {
		List<Info> result = new ArrayList<>();
		int row = 0, col = 0;
		for (int i = 5; i < output.size(); i++) {
			String s = output.get(i);
			char ch = s.charAt(0);
			int len = s.length() - 1;
			switch (ch) {
				case '-':
					break;
				case '~':
					if (row < code.size() && col == code.get(row).length()) {
						row += 1;
						col = 0;
					}
					break;
				case '+':
					result.add(new Info(new Position(row, col + 1), new Position(row, col + len)));
				default:
					col += len;
			}
		}
		return result;
	}

	public static List<String> executeCommand(File dir, File f1, File f2) {
		List<String> output = new ArrayList<>();
		try {
			ProcessBuilder builder = new ProcessBuilder("git", "diff", "--word-diff=porcelain", f1.getName(), f2.getName());
//			System.out.println(String.join(" ", builder.command()));
			builder.directory(dir);
			Process process = builder.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String s;
			while ((s = reader.readLine()) != null) {
				output.add(s);
			}
			int exitCode = process.waitFor();
			assert exitCode != 2;
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
		return output;
	}
}
