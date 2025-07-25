package com.github.divergent.divide;

import java.util.*;
import java.util.stream.Collectors;

public class DisjointSet<E> {
	private final Map<E, Entry> entries = new HashMap<>();
	private int setCount;

	public DisjointSet() {
		this.setCount = 0;
	}

	public DisjointSet(Collection<E> elems) {
		elems.forEach(elem -> entries.put(elem, new Entry(elem)));
		this.setCount = entries.size();
	}

	public void add(E elem) {
		if (!entries.containsKey(elem)) {
			entries.put(elem, new Entry(elem));
			setCount++;
		}
	}

	public boolean contains(E elem) {
		return entries.containsKey(elem);
	}

	public boolean merge(E e1, E e2) {
		Entry root1 = findRootEntry(entries.get(e1));
		Entry root2 = findRootEntry(entries.get(e2));

		if (root1 == root2) {
			return false;
		} else { // union by rank
			if (root1.rank > root2.rank) {
				root2.parent = root1;
				root1.rank += root2.rank;
			} else {
				root1.parent = root2;
				root2.rank += root1.rank;
			}
			--setCount;
			return true;
		}
	}

	public boolean isConnected(E e1, E e2) {
		Entry root1 = findRootEntry(entries.get(e1));
		Entry root2 = findRootEntry(entries.get(e2));
		return root1 == root2;
	}

	public E findRoot(E e) {
		return findRootEntry(entries.get(e)).elem;
	}

	public int numberOfSets() {
		return setCount;
	}

	public Collection<List<E>> getDisjointSets() {
		return entries.keySet()
				.stream()
				.collect(Collectors.groupingBy(this::findRoot, Collectors.toList()))
				.values();
	}

	private Entry findRootEntry(Entry e) {
		if (e != e.parent) {
			e.parent = findRootEntry(e.parent);
		}
		return e.parent;
	}

	private class Entry {
		private final E elem;
		private Entry parent;
		private int rank;

		private Entry(E elem) {
			this.elem = elem;
			this.parent = this;
			this.rank = 0;
		}
	}
}
