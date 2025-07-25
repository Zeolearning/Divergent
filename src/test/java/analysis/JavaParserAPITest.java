package analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.*;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.ResolvedTypeVariable;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserVariableDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import com.github.javaparser.utils.VisitorSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import static java.lang.String.format;

public class JavaParserAPITest {
	private static final String TEST_ROOT = "src/test/resources/javaparser";
	private static final Logger logger = LoggerFactory.getLogger(JavaParserAPITest.class);
	private static JavaParser parser;

	@BeforeAll
	public static void initialize() {
		CombinedTypeSolver typeSolver = new CombinedTypeSolver(new JavaParserTypeSolver(TEST_ROOT),
				new ReflectionTypeSolver());
		ParserConfiguration config = new ParserConfiguration();
		config.setSymbolResolver(new JavaSymbolSolver(typeSolver));
		parser = new JavaParser(config);

	}

	private CompilationUnit parseTestCase(String testcase) {
		String path = format("%s/%s/%s", System.getProperty("user.dir"), TEST_ROOT, testcase);
		File f = new File(path);
		try {
			return parser.parse(f).getResult().orElse(null);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testClassScope() {
		CompilationUnit root = parseTestCase("ClassScope.java");
		for (VariableDeclarator var : root.findAll(VariableDeclarator.class)) {
			ClassOrInterfaceType type = var.getType().asClassOrInterfaceType();
			System.out.println(type.getNameWithScope());
		}
		System.out.println("-".repeat(20));
		root.findAll(ObjectCreationExpr.class).forEach(e -> {
			System.out.println(e);
			System.out.println(e.getScope());
			System.out.println("-".repeat(20));
		});
	}

	//	EnumDeclaration
	//		|--- EnumConstant
	//				|--- Inner Members
	//		|--- Other Body Members
	@Test
	public void testEnumConstant() {
		CompilationUnit root = parseTestCase("EnumConstant.java");
		EnumDeclaration enumClass = (EnumDeclaration) root.getType(0);
		for (EnumConstantDeclaration enumConstant : enumClass.getEntries()) {
			System.out.println((enumConstant.getParentNode().get() == enumClass));
		}
	}

	@Test
	public void testImportUsage() {
		CompilationUnit root = parseTestCase("ImportUsage.java");
		root.getImports().forEach(x -> {
			System.out.println(x);
			System.out.println(x.getNameAsString());
			System.out.println(x.getName().getId());
			System.out.println("-".repeat(20));
		});
	}

	@Test
	public void testArrayType() {
		CompilationUnit root = parseTestCase("ArrayType.java");
		root.findAll(VariableDeclarator.class).forEach(v -> {
			System.out.println(v.getType());
			System.out.println(v.getType().getElementType());
		});

	}

	@Test
	public void testVisitorSet() {
		CompilationUnit root = parseTestCase("Type.java");
		List<Type> types = root.findAll(Type.class).stream().toList();

		VisitorSet<Type> set = new VisitorSet<>(new ObjectIdentityHashCodeVisitor(), new ObjectIdentityEqualsVisitor());
		set.addAll(types);

		System.out.println(types);
		System.out.println(set);
		System.out.println(new HashSet<>(types));
	}

	@Test
	public void testParameter() {
		CompilationUnit root = parseTestCase("Parameter.java");
		root.findAll(MethodDeclaration.class).forEach(m -> {
			m.getParameters().forEach(p -> {
				Type type = p.getType();
				ResolvedType resolved = type.resolve();
				System.out.printf("%s -> %s%n", type, resolved.getClass());
				System.out.println(type.getElementType().resolve().getClass());

				if (resolved instanceof ReferenceTypeImpl impl) {
					System.out.println(impl.getTypeParametersMap());
				}
				System.out.println("-".repeat(20));
			});
		});

	}

	@Test
	public void testName() {
		CompilationUnit root = parseTestCase("Name.java");
		root.findAll(NameExpr.class).forEach(n -> {
			ResolvedValueDeclaration resolved = n.resolve();
			if (resolved instanceof JavaParserVariableDeclaration v) {
				var name = v.getVariableDeclarator().getName();
				System.out.println(n.getName() == name);
				System.out.println(name.getRange());
				System.out.println(n.getName().getRange());
			}
		});
	}

	@Test
	public void testTypeParam() {
		CompilationUnit root = parseTestCase("TypeParam.java");
		root.findAll(VariableDeclarator.class).forEach(v -> {
			System.out.println("Variable: " + v);

			ResolvedType resolved = v.getType().resolve();
			if (resolved instanceof ResolvedTypeVariable typeVar) {
				System.out.println(typeVar.asTypeParameter());
			}
			if (resolved instanceof ReferenceTypeImpl ltype) {
				System.out.println("Declared type params: " + ltype.typeParametersValues());

				ResolvedType rvalue = v.getInitializer().get().calculateResolvedType();
				if (rvalue instanceof ReferenceTypeImpl rtype) {
					System.out.println("rvalue's type params: " + rtype.getTypeParametersMap());
					ReferenceTypeImpl ans;

					if (!rtype.getTypeDeclaration().get().getTypeParameters().isEmpty() &&
							rtype.getTypeParametersMap().isEmpty()) {
						ans = new ReferenceTypeImpl(rtype.getTypeDeclaration().get(),
								ltype.typeParametersValues());
					} else {
						ans = rtype;
					}
					System.out.println(ans);
				}
			}
			System.out.println("-".repeat(20));
		});
	}

	@Test
	public void testArrayExpr() {
		CompilationUnit root = parseTestCase("Type.java");
		root.findAll(VariableDeclarator.class).forEach(v -> {
			String name = v.getName().getId();
			if (name.equals("arr")) {
				Expression init = v.getInitializer().get();
				ArrayCreationExpr ace = (ArrayCreationExpr) init;
				System.out.println(ace.calculateResolvedType());
				ace.getInitializer().ifPresent(aie -> {
					System.out.println(aie + ": " + aie.getClass());
					aie.getValues().forEach(e -> System.out.println(e.getClass()));
				});
			}
		});
	}

	@Test
	public void testFieldScope() {
		CompilationUnit root = parseTestCase("FieldScope.java");
		root.findAll(MethodDeclaration.class).forEach(m -> {
			System.out.println(m.getSignature());
			if (m.getName().getId().equals("main")) {
				m.findAll(FieldAccessExpr.class).forEach(n -> {
					System.out.printf("%s, scope: %s%n", n, n.getScope());
					n.getParentNode().ifPresent(par -> System.out.println(par.getClass()));
					System.out.println(n.getName().getRange());
					if (n.resolve() instanceof JavaParserFieldDeclaration jpfd) {
						System.out.println(jpfd.getVariableDeclarator().getName().getRange());
					}
					System.out.println("-".repeat(20));
				});
			}
		});
	}

	@Test
	public void testNameExpr() {
		CompilationUnit root = parseTestCase("NameExpr.java");
		root.findAll(NameExpr.class).forEach(e -> {
			System.out.println(e.getRange());
			System.out.println(e.resolve().getClass());
		});
	}

	@Test
	public void testStatement() {
		CompilationUnit root = parseTestCase("Statement.java");
		root.findAll(IfStmt.class).forEach(ifStmt -> {
			System.out.println(ifStmt.getThenStmt().getClass());
			System.out.println(ifStmt.getElseStmt());
		});
	}

	@Test
	public void testLambda() {
		CompilationUnit root = parseTestCase("Lambda.java");

//		root.findAll(MethodCallExpr.class).forEach(e -> {
//			System.out.println(e.resolve());
//		});
		root.findAll(LambdaExpr.class).forEach(lambda -> {
			var impl = (ReferenceTypeImpl) lambda.calculateResolvedType();
			System.out.println(impl);
			System.out.println(impl.getTypeDeclaration());
		});

		root.findAll(NameExpr.class).forEach(e -> {
			System.out.println(e);
			System.out.println(e.calculateResolvedType().describe());
			System.out.println("-".repeat(20));
		});
	}

	@Test
	public void testComment() {
		CompilationUnit root = parseTestCase("Comment.java");
		root.getAllContainedComments().forEach(c -> {
			System.out.println(c);
			System.out.println(c.getRange());
			System.out.println(c.getClass());
			System.out.println("-".repeat(20));
		});
	}

	@Test
	public void testJPRange() {
		CompilationUnit root = parseTestCase("Statement.java");
		root.findAll(MethodDeclaration.class).forEach(m -> System.out.println(m.getRange()));
	}

	@Test
	public void testStaticMethodCall() {
		CompilationUnit root = parseTestCase("StaticMethodCall.java");
		root.findAll(MethodCallExpr.class).forEach(expr -> {
			System.out.println(expr);
			System.out.println(expr.getNameAsString());
//			System.out.println(expr.resolve());
		});
	}

	@Test
	public void testTypeVariable() {
		CompilationUnit root = parseTestCase("TypeVariable.java");
		root.findAll(MethodDeclaration.class).forEach(m -> {
			Type type = m.getType();
			ResolvedType resolve = type.resolve();
			System.out.println(type.isReferenceType());
			System.out.println(resolve);
			System.out.println(resolve.isReferenceType());
		});
	}

	@Test
	public void testMultiSource() throws FileNotFoundException {
		CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
		typeSolver.add(new JavaParserTypeSolver("src/main/java"));
		typeSolver.add(new JavaParserTypeSolver("src/test/java"));

		ParserConfiguration config = new ParserConfiguration()
				.setSymbolResolver(new JavaSymbolSolver(typeSolver));

		JavaParser parser = new JavaParser(config);
		CompilationUnit root = parser.parse(new File("src/test/java/diff/IntegralTest.java")).getResult().get();
		root.findAll(Type.class).stream()
				.filter(type -> type.toString().equals("Revision"))
				.findAny()
				.ifPresent(type -> System.out.println(type.resolve()));
	}

}