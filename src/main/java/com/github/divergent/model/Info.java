package com.github.divergent.model;

import com.github.javaparser.Position;

import java.util.Objects;

public class Info {
	public final Position begin;
	public final Position end;

	public Info(Position begin, Position end) {
		this.begin = begin;
		this.end = end;
	}

	public boolean intersect(Info that) {
		if (begin.isAfter(that.end) || end.isBefore(that.begin)) {
			return false;
		}
		return true;
	}

	public Info rowOffset(int offset) {
		Position s = new Position(begin.line + offset, begin.column);
		Position t = new Position(end.line + offset, end.column);
		return new Info(s, t);
	}

	@Override
	public String toString() {
		return "Info{" +
				"begin=" + begin +
				", end=" + end +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Info info = (Info) o;
		return Objects.equals(begin, info.begin) && Objects.equals(end, info.end);
	}

	@Override
	public int hashCode() {
		return Objects.hash(begin, end);
	}
}
