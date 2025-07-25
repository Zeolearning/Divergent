package com.github.divergent.model;

import com.github.divergent.utils.StringReps;
import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

public class TreeNode {
	static Logger logger = LogManager.getLogger(TreeNode.class);
	private final Node node;
	private final String path;
	private final Info info;

	public TreeNode(Node node, String path) {
		this.node = node;
		this.path = path;
		Range range = node.getRange().orElse(null);
		if (range == null) {
			logger.error(toString());
			assert false;
		}
		this.info = new Info(range.begin, range.end);
	}

	public String getPath() {
		return path;
	}

	public Info getInfo() {
		return info;
	}

	public Node getNode() {
		return node;
	}

	@Override
	public String toString() {
		return StringReps.objectToString(node);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		TreeNode that = (TreeNode) o;
		return Objects.equals(node, that.node) && Objects.equals(path, that.path) && Objects.equals(info, that.info);
	}

	@Override
	public int hashCode() {
		return Objects.hash(node, path, info);
	}
}
