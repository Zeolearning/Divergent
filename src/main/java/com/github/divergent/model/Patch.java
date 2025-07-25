package com.github.divergent.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Patch {
	private Region prev;
	private Region next;
	private final List<String> actions;

	public Patch() {
		this.actions = new ArrayList<>();
	}


	public Patch(Region prev, Region next) {
		this.prev = prev;
		this.next = next;
		this.actions = new ArrayList<>();
	}

	public boolean hasPrev() {
		return prev != null;
	}

	public boolean hasNext() {
		return next != null;
	}

	public void setPrev(Region prev) {
		this.prev = prev;
	}

	public void setNext(Region next) {
		this.next = next;
	}

	public Region getPrev() {
		return prev;
	}

	public Region getNext() {
		return next;
	}

	public double cosine(Patch that) {
		if (prev == null) {
			return next.cosine(that.next);
		} else if (next == null) {
			return prev.cosine(that.prev);
		} else {
			return Math.min(next.cosine(that.next), prev.cosine(that.prev));
		}
	}

	public void addAction(String action) {
		actions.add(action);
	}

	public List<String> getActions() {
		return actions;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		Patch patch = (Patch) o;
		return Objects.equals(prev, patch.prev) && Objects.equals(next, patch.next) && Objects.equals(actions, patch.actions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prev, next, actions);
	}

	@Override
	public String toString() {
		return String.format("Patch{prev=%s, next=%s}", prev, next);
	}
}


