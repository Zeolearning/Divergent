package com.github.divergent.model;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Snapshot {
	private static final Logger logger = LogManager.getLogger(Snapshot.class);
	private final File root;
	private final Map<String, FileView> cache;

	public Snapshot(File file) {
		this.root = file;
		this.cache = new HashMap<>();
		if (!file.mkdirs()) {
			logger.info("Path {} already exists", file);
		}
	}

	public void makeFile(String path, String text) throws IOException {
		File file = new File(root, path);
		FileUtils.createParentDirectories(file);
		if (!file.createNewFile()) {
			logger.warn("File {} already exists", path);
		}
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(text.getBytes());
		fos.close();
	}

	public Map<String, String> getContentMap() {
		return cache.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getContent()));
	}

	public Map<String, FileView> getViewMap() {
		return cache;
	}

	public FileView getView(String path) {
		return cache.get(path);
	}

	public void addView(String path, FileView view) {
		cache.put(path, view);
	}

	public File getRoot() {
		return root;
	}

	public void reset() {
		cache.clear();
    }
}
