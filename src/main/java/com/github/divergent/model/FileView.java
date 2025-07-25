package com.github.divergent.model;

import java.util.ArrayList;
import java.util.List;

public class FileView {
	private String content;
	private final String path;
	private final List<Region> regions;

	public FileView(String path) {
		this.path = path;
		this.regions = new ArrayList<>();
	}

	public List<Region> getRegions() {
		return regions;
	}

	public void addRegion(Region r) {
		regions.add(r);
	}

	public void setContent(String content) {
		this.content = content;
	}

	public boolean isEmpty() {
		return regions.isEmpty();
	}

	public String getPath() {
		return path;
	}

	public String getContent() {
		return content;
	}
}
