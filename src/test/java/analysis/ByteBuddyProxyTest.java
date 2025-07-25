package analysis;

import com.github.divergent.analysis.RunProxy;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;

public class ByteBuddyProxyTest {
	private final String WORK_SPACE = System.getProperty("user.dir");
	private final File JP_TEST_DIR = new File(WORK_SPACE, "src/test/resources/javaparser");
	private final JavaParser parser = new JavaParser();

	protected static class Visitor extends VoidVisitorAdapter<Void> {
		public Visitor() {
			System.out.println("Default");
		}

		public void test() {
			System.out.println("Test Method");
		}

		@Override
		public void visit(CompilationUnit n, Void arg) {
			System.out.println("<Enter>");
			n.getTypes().accept(this, arg);
			System.out.println("<Exit>");
		}

		@Override
		public void visit(ClassOrInterfaceDeclaration n, Void arg) {
			System.out.println(n);
			throw new RuntimeException("Exception in Visitor");
		}
	}

	@Test
	public void testRunProxy() throws FileNotFoundException {
		File log = new File("output/exception.log");
		Visitor proxy = RunProxy.createProxy(Visitor.class, new RunProxy.ExceptionLogger(log),
									null, null, null);

		File f = new File(JP_TEST_DIR, "Type.java");
		CompilationUnit root = parser.parse(f).getResult().get();
		proxy.test();
		proxy.visit(root, null);
	}

}

