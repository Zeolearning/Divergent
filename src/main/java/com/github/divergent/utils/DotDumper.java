package com.github.divergent.utils;

import com.github.divergent.model.AbstractEdge;
import com.github.divergent.model.Graph;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DotDumper<N, E extends AbstractEdge<N>> {
	private static final Logger logger = LogManager.getLogger(DotDumper.class);
	private static final String INDENT = "  ";
	private PrintStream out;
	private Function<N, String> nodeLabeler = null;

	public void dump(Graph<N, E> graph, File output) {
		try (PrintStream out = new PrintStream(new FileOutputStream(output))) {
			this.out = out;
			var L = graph.getNodes().stream().toList();
			this.nodeLabeler = IntStream.range(0, L.size()).boxed()
										.collect(Collectors.toMap(L::get, String::valueOf))::get;
			out.println("digraph G {");
			out.println("  node [shape=box, style=rounded];");
			graph.forEach(this::dumpNode);
			graph.forEach(n -> graph.getOutEdgesOf(n).forEach(this::dumpEdge));
			out.println("}");
		} catch (FileNotFoundException e) {
			logger.error("Fail to dump graph to {}", output.getAbsolutePath(), e.getCause());
		} finally {
			this.nodeLabeler = null;
		}
	}

	private void dumpNode(N node) {
		dumpElement(node, nodeLabeler, Object::toString);
	}

	private String edgeToString(E edge) {
		return nodeLabeler.apply(edge.getSource()) + " -> " + nodeLabeler.apply(edge.getTarget());
	}

	private void dumpEdge(E edge) {
		dumpElement(edge, this::edgeToString, AbstractEdge::getLabel);
	}

	private <T> void dumpElement(T elem, Function<T, String> toString, Function<T, String> labeler) {
		out.print(INDENT);
		out.print(toString.apply(elem));
		String label = labeler.apply(elem);
		if (label != null) {
			out.printf(" [label=\"%s\"]", label);
		}
		out.println(';');
	}

}
