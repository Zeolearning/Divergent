package analysis;

import com.github.divergent.model.AbstractEdge;
import com.github.divergent.model.Graph;
import com.github.divergent.utils.DotDumper;
import org.junit.jupiter.api.Test;

import java.io.File;

public class GraphTest {

	@Test
	public void testBasicGraph() {
		class IntEdge extends AbstractEdge<Integer> {
			protected IntEdge(Integer source, Integer target) {
				super(source, target);
			}

			@Override
			public String getLabel() {
				return null;
			}
		}
		Graph<Integer, IntEdge> graph = new Graph<>();
		for (int i = 0; i < 10; ++i) {
			for (int j = i + 1; j < 10; ++j) {
				graph.addEdge(i, j, new IntEdge(i, j));
			}
		}
		System.out.println(graph);

		DotDumper<Integer, IntEdge> dumper = new DotDumper<>();
		dumper.dump(graph, new File("output/TestDump.dot"));
	}


}