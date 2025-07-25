package analysis;

import com.github.divergent.analysis.GraphBuilder;
import com.github.divergent.model.*;
import org.junit.jupiter.api.Test;

import java.io.File;

public class DataFlowTest {
	private final Snapshot snapshot = new Snapshot(new File(System.getProperty("user.dir")));
	private final String TEST_DIR = "src/test/resources/testresc";

	@Test
	public void testVariable() {
		String path = String.format("%s/%s.java", TEST_DIR, "dataflow/TestDataFlow");
		FileView view = new FileView(path);
		view.addRegion(new Region(view, 0, 1, 1000));
		snapshot.addView(path, view);
		new GraphBuilder(snapshot).build();
	}
}
