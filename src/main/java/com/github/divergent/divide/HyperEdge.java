package com.github.divergent.divide;

import com.github.divergent.model.AbstractEdge;
import com.github.divergent.model.Patch;

import java.util.Objects;

public class HyperEdge extends AbstractEdge<Patch> {

	public enum Type {
		REFACTOR, DEPEND, FORMAT, COMMENT, CLONE, TRIVIAL
	}

	private final Type type;
	public HyperEdge(Patch source, Patch target, Type type) {
		super(source, target);
		this.type = type;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		if (!super.equals(o))
			return false;
		HyperEdge that = (HyperEdge) o;
		return type == that.type;
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), type);
	}

	@Override
	public String getLabel() {
		return type.name();
	}
}