package com.github.divergent.model;

import com.github.divergent.utils.StringReps;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Graph<N, E extends AbstractEdge<N>> implements Iterable<N> {
	private final Set<N> nodes;
	private final Map<N, Set<E>> preds;
	private final Map<N, Set<E>> succs;

	public Graph() {
		nodes = new HashSet<>();
		preds = new HashMap<>();
		succs = new HashMap<>();
	}

	public void clear(){
		this.nodes.clear();
		this.preds.clear();
		this.succs.clear();
	}

	public void addNode(N node) {
		nodes.add(node);
		preds.computeIfAbsent(node, __ -> new HashSet<>());
		succs.computeIfAbsent(node, __ -> new HashSet<>());
	}

	public void addEdge(N source, N target, E edge) {
		addNode(source);
		addNode(target);
		succs.get(source).add(edge);
		preds.get(target).add(edge);
	}

	public boolean hasSelfLoop() {
		return nodes.stream().anyMatch(n -> hasEdge(n, n));
	}

	public boolean hasEdge(N source, N target) {
		return succs.get(source).stream().anyMatch(e -> target.equals(e.target));
	}

	public Set<E> getOutEdgesOf(N node) {
		return new HashSet<>(succs.get(node));
	}

	public Set<N> getSuccsOf(N node) {
		return succs.get(node).stream()
				.map(e -> e.target)
				.collect(Collectors.toSet());
	}

	public Set<N> getPredsOf(N node) {
		return preds.get(node).stream()
				.map(e -> e.source)
				.collect(Collectors.toSet());
	}

	public Set<N> getNodes() {
		return nodes;
	}

	public Set<E> getEdges() {
		return preds.values().stream().flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());
	}

	public int inDegreeOf(N node) {
		return preds.get(node).size();
	}

	public int outDegreeOf(N node) {
		return succs.get(node).size();
	}

	public int getNodeCount() {
		return nodes.size();
	}

	public String toString(Predicate<N> fn, Predicate<E> fe) {
		StringBuilder sb = new StringBuilder();
		sb.append("[\n");
		for (N node : nodes) {
			if (!fn.test(node)) {
				continue;
			}
			List<E> remain = succs.get(node).stream().filter(fe).toList();
			if (!remain.isEmpty()) {
				String s = node.toString().strip();
				String t = remain.stream().map(e -> e.target.toString()).collect(Collectors.joining(", "));
				sb.append(String.format("\t%s -> [%s]\n", s, t));
			}
		}
		sb.append(']');
		return sb.toString();

	}

	@Override
	public String toString() {
		return toString(n -> true, e -> true);
	}

	@Override
	public Iterator<N> iterator() {
		return nodes.iterator();
	}

	@Override
	public void forEach(Consumer<? super N> action) {
		nodes.forEach(action);
	}
}
