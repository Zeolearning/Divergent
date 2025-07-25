package com.github.divergent.model;

import info.debatty.java.stringsimilarity.Cosine;

import java.util.ArrayList;
import java.util.List;

public class Region {
	private final int index;
	private int begin;
	private int end;
	private final FileView view;
	private final List<Info> tokens;
	private List<String> code;
	
	public Region(FileView view, int index, int begin, int end) {
		this.index = index;
		this.begin = begin;
		this.end = end;
		this.view = view;
		this.code = new ArrayList<>();
		this.tokens = new ArrayList<>();
	}

	public boolean intersect(int begin, int end) {
		if (this.end < begin || this.begin > end) {
			return false;
		}
		return true;
	}

	public boolean pathEquals(Region that) {
		if (that == null) {
			return false;
		}
		return getPath().equals(that.getPath());
	}

	public double cosine(Region that) {
		if (that == null)
			return 0;
		String s1 = String.join(" ", formatCode());
		String s2 = String.join(" ", that.formatCode());
		return new Cosine().similarity(s1, s2);
	}

	public void addInfo(Info info) {
		tokens.add(info);
	}

	public int getIndex() {
		return index;
	}

	public String getPath() {
		return view.getPath();
	}

	public int getBegin() {
		return begin;
	}

	public int getEnd() {
		return end;
	}

	public List<Info> getTokens() {
		return tokens;
	}

	public void setCode(List<String> code) {
		this.code = code;
	}

	public List<String> getCode() {
		return code;
	}

	public List<String> formatCode() {
		return code.stream().map(String::strip).toList();
	}

	@Override
	public String toString() {
		return String.format("Region[%s](%d-%d)", view.getPath(), begin, end);
	}
}
