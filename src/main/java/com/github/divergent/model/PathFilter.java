package com.github.divergent.model;

import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import static com.github.divergent.utils.StringReps.isJavaFile;
import static com.github.divergent.utils.StringReps.isValidPath;

public class PathFilter extends TreeFilter  {

	@Override
	public boolean include(TreeWalk walker) {
		String path = walker.getPathString();
		if (walker.isSubtree()) {
			return true;
		}
		return isJavaFile(path) && isValidPath(path);
	}

	@Override
	public boolean shouldBeRecursive() {
		return true;
	}

	@Override
	public TreeFilter clone() {
		return this;
	}
}

