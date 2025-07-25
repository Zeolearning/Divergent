package com.github.divergent.utils;

import com.github.divergent.model.PathFilter;
import com.google.common.collect.Lists;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.NullOutputStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class GitUtils {
	public static Repository openRepository(File dir) throws IOException {
		final String ext = Constant.DOT_GIT_EXT;
		Repository repo;
		if (dir.exists()) {
			repo = new FileRepositoryBuilder()
					.setGitDir(dir.getName().equals(ext) ? dir : new File(dir, ext))
					.readEnvironment()
					.setMustExist(true)
					.build();
		} else {
			throw new FileNotFoundException(String.format(".git not found under %s", dir.getCanonicalFile()));
		}
		return repo;
	}

	public static List<RevCommit> getCommits(File file) {
		try (Repository repo = openRepository(file)) {
			Git git = new Git(repo);
			return Lists.newArrayList(git.log().call());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static AbstractTreeIterator getTreeIterator(Repository repo, ObjectId objectId){
		CanonicalTreeParser parser;
		try {
			RevWalk walk = new RevWalk(repo);
			RevCommit commit = walk.parseCommit(objectId);
			RevTree tree = walk.parseTree(commit.getTree().getId());

			parser = new CanonicalTreeParser();
			ObjectReader reader = repo.newObjectReader();
			parser.reset(reader, tree.getId());
			walk.dispose();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return parser;
	}

	public static AbstractTreeIterator getTreeIterator(Repository repo, String hash) throws IOException {
		return getTreeIterator(repo, repo.resolve(hash));
	}

	public static List<DiffEntry> detectDiff(Repository repo, AbstractTreeIterator iter1, AbstractTreeIterator iter2) {
		try {
			DiffFormatter df = new DiffFormatter(NullOutputStream.INSTANCE);
			df.setRepository(repo);
			df.setDetectRenames(true);
			df.setDiffComparator(RawTextComparator.DEFAULT);
//			df.setPathFilter(new PathFilter());
			df.setPathFilter(PathSuffixFilter.create(".java"));
			df.setDiffAlgorithm(DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM));

			List<DiffEntry> ret = df.scan(iter1, iter2);
			iter1.reset();
			iter2.reset();
			return ret;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static byte[] read(Repository repo, AbbreviatedObjectId id) {
		return read(repo, id.toObjectId());
	}

	public static byte[] read(Repository repo, ObjectId id) {
		try {
			ObjectLoader loader = repo.open(id);
			return loader.getBytes();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
