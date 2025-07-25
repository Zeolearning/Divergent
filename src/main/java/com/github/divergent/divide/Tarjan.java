package com.github.divergent.divide;

import com.github.divergent.model.Graph;

import java.util.*;

public class Tarjan<N> {
	private final List<List<N>> components = new ArrayList<>();

	public Tarjan(Graph<N, ?> graph) {
		this.compute(graph);
	}

	private void compute(Graph<N, ?> graph) {
		// use iterative (non-recursive) algorithm to avoid stack overflow
		// for large graph
		int index = 0;
		int size = graph.getNodeCount();
		Map<N, Integer> indexes = new HashMap<>(size);
		Map<N, Integer> lows = new HashMap<>(size);
		Deque<N> stack = new ArrayDeque<>();
		Set<N> inStack = new HashSet<>();
		for (N curr : graph) {
			if (indexes.containsKey(curr)) {
				continue;
			}
			Deque<N> workStack = new ArrayDeque<>();
			workStack.push(curr);
			while (!workStack.isEmpty()) {
				N node = workStack.peek();
				if (!indexes.containsKey(node)) {
					indexes.put(node, index);
					lows.put(node, index);
					++index;
					stack.push(node);
					inStack.add(node);
				}
				boolean hasUnvisited = false;
				for (N succ : graph.getSuccsOf(node)) {
					if (!indexes.containsKey(succ)) {
						workStack.push(succ);
						hasUnvisited = true;
						break;
					} else if (indexes.get(node) < indexes.get(succ)) {
						// node->succ is a forward edge
						lows.put(node, Math.min(lows.get(node), lows.get(succ)));
					} else if (inStack.contains(succ)) {
						lows.put(node, Math.min(lows.get(node), indexes.get(succ)));
					}
				}
				if (!hasUnvisited) {
					if (lows.get(node).equals(indexes.get(node))) {
						collectSCC(node, stack, inStack, graph);
					}
					workStack.pop();
				}
			}
		}
	}

	private void collectSCC(N node, Deque<N> stack, Set<N> inStack, Graph<N, ?> graph) {
		List<N> scc = new ArrayList<>();
		N v;
		do {
			v = stack.pop();
			inStack.remove(v);
			scc.add(v);
		} while (node != v);
		if (scc.size() > 1) {
			components.add(scc);
		} else {
			N n = scc.get(0);
			if (graph.hasEdge(n, n)) {
				components.add(scc);
			}
		}
	}

	public List<List<N>> getComponents() {
		return components;
	}
}