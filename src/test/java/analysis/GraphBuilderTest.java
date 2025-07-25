package analysis;

import com.github.divergent.analysis.GraphBuilder;
import com.github.divergent.model.*;
import com.github.divergent.utils.DotDumper;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.github.divergent.model.Edge.Type.*;
import static java.lang.String.format;

public class GraphBuilderTest {
	private final Snapshot snapshot = new Snapshot(new File(System.getProperty("user.dir")));
	private final String TEST_DIR = "src/test/resources/testresc";

	private void addView(String path) {
		FileView view = new FileView(path);
		view.addRegion(new Region(view, 0, 1, 1000));
		snapshot.addView(path, view);
	}

	@Test
	public void testBuildImport() {
		String path = format("%s/%s.java", TEST_DIR, "Validate");
		addView(path);
		GraphBuilder builder = new GraphBuilder(snapshot);
		Graph<TreeNode, Edge> graph = builder.getGraph();
		graph.getNodes().forEach(x -> System.out.println(x + " -> " + x.getInfo()));
		System.out.println(graph);
		DotDumper<TreeNode, Edge> dumper = new DotDumper<>();
		dumper.dump(graph, new File("output/TestDump.dot"));
	}
	
	@Test
	public void testClassHierarchy() {
		String path = format("%s/%s.java", TEST_DIR, "hierarchy/TestHierarchy");
		addView(path);
		GraphBuilder builder = new GraphBuilder(snapshot);
		Graph<TreeNode, Edge> graph = builder.getGraph();
		System.out.println(graph);
	}

	@Test
	public void testMethodOverride() {
		String path = format("%s/%s.java", TEST_DIR, "override/TestOverride");
		addView(path);

		GraphBuilder builder = new GraphBuilder(snapshot);
		Graph<TreeNode, Edge> graph = builder.getGraph();
		System.out.println(graph.toString(n -> true, e -> e.getLabel().equals(OVERRIDE.name())));
	}

	@Test
	public void testReferenceType() {
		String path = format("%s/%s.java", TEST_DIR, "Validate");
		addView(path);
		GraphBuilder builder = new GraphBuilder(snapshot);
		Graph<TreeNode, Edge> graph = builder.getGraph();
		System.out.println(graph.toString(n -> true, e -> e.getLabel().equals(REFERENCE.name())));
	}

}
