package com.github.divergent.analysis;

import com.github.divergent.model.*;
import com.github.divergent.utils.StringReps;
import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.*;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import com.github.javaparser.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.github.divergent.utils.StringReps.isValidPath;
import static com.github.divergent.model.Edge.Type.*;
import static com.github.javaparser.ast.Modifier.Keyword.*;
import static com.github.javaparser.ast.body.CallableDeclaration.Signature;

public class GraphBuilder {
    private static final Logger logger = LogManager.getLogger(GraphBuilder.class);
    private final Snapshot snapshot;
    private final CombinedTypeSolver typeSolver;
    private final JavaParserAdapter parser;
    private final Graph<TreeNode, Edge> graph;

    public GraphBuilder(Snapshot snapshot) {
        this.snapshot = snapshot;
        this.typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        this.initTypeSolvers();

        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.parser = JavaParserAdapter.of(new JavaParser(config));
        this.graph = new Graph<>();
    }

    private void initTypeSolvers() {
        try {
            Files.walkFileTree(snapshot.getRoot().toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (isValidPath(dir.toString())) {
                        typeSolver.add(new JavaParserTypeSolver(dir.toFile()));
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.error(e);
            throw new RuntimeException(e);
        }
    }

    public boolean build() {
        File base = snapshot.getRoot();
        AtomicBoolean flag = new AtomicBoolean(true);
        snapshot.getViewMap().forEach((path, view) -> {
            try {
                File file = new File(base, path);
                // Parse file to get range for each AST node
                CompilationUnit root = parser.parse(file);
                root.accept(new GeneralVisitor(root), view);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IllegalStateException | ParseProblemException e) {
                flag.set(false);
            }
        });
        JavaParserFacade.clearInstances();
        return flag.get();
    }

    private void addEdge(Node x, Node y, Edge.Type type) {
//		logger.debug("{} -> {}", n1, n2);
        TreeNode source = new TreeNode(x, getNodePath(x));
        TreeNode target = new TreeNode(y, getNodePath(y));
        Edge edge = new Edge(source, target, type);
        graph.addEdge(source, target, edge);
    }

    private String getNodePath(Node node) {
        CompilationUnit root = node.findCompilationUnit().get();
        return root.getStorage().get().getPath().toString();
    }

    public Graph<TreeNode, Edge> getGraph() {
        return graph;
    }

    private class GeneralVisitor extends VoidVisitorAdapter<FileView> {
        private final CompilationUnit root;
        private final Set<Type> visited;

        public GeneralVisitor(CompilationUnit root) {
            this.root = root;
            this.visited = new HashSet<>();
        }


        // ======= analysis for class structure =======

        @Override
        public void visit(ImportDeclaration n, FileView arg) {
            if (isOutside(n, arg)) {
                return;
            }
            String imported = n.getNameAsString();
            if (isJRELibrary(imported)) {
                return;
            }
            SymbolReference<ResolvedReferenceTypeDeclaration> symbolRef;
            if (n.isStatic() && !n.isAsterisk()) {
                int offset = n.getName().getId().length() + 1;
                symbolRef = typeSolver.tryToSolveType(StringReps.removeTail(imported, offset));
            } else {
                symbolRef = typeSolver.tryToSolveType(imported);
            }
            if (!symbolRef.isSolved()) {
                return;
            }

            ResolvedReferenceTypeDeclaration resolved = symbolRef.getCorrespondingDeclaration();
            TypeDeclaration<?> target = unwrapResolved(resolved);
            if (target == null) {
                return;
            }

            String packageName = n.findCompilationUnit().flatMap(CompilationUnit::getPackageDeclaration)
                    .map(PackageDeclaration::getNameAsString).orElse(null);

            boolean isEnum = target.isEnumDeclaration();
            boolean commonPackage = resolved.getPackageName().equals(packageName);

            Predicate<NodeWithModifiers<?>> checkAccess = x -> x.hasModifier(PUBLIC) || (!x.hasModifier(PRIVATE) && commonPackage);
            if (n.isAsterisk()) {
                if (n.isStatic()) {
                    if (isEnum) {
                        target.asEnumDeclaration().getEntries().forEach(entry -> addEdge(n, entry, IMPORT));
                    }
                    for (MethodDeclaration method : target.getMethods()) {
                        if (method.isStatic() && checkAccess.test(method)) {
//                            System.out.println("Imported static method: " + method.getDeclarationAsString());
                            addEdge(n, method, IMPORT);
                        }
                    }
                    for (FieldDeclaration field : target.getFields()) {
                        if (field.isStatic() && checkAccess.test(field)) {
                            addEdge(n, field, IMPORT);
                        }
                    }
                } else {
                    for (BodyDeclaration<?> member : target.getMembers()) {
                        if ((member instanceof TypeDeclaration<?> cls) && checkAccess.test(cls)) {
                            addEdge(n, cls, IMPORT);
                        }
                    }
                }
            } else {
                String name = n.getName().getId();
                if (n.isStatic()) {
                    if (isEnum) {
                        for (EnumConstantDeclaration entry : target.asEnumDeclaration().getEntries()) {
                            if (entry.getName().getId().equals(name)) {
                                addEdge(n, entry, IMPORT);
                                break;
                            }
                        }
                    }
                    for (MethodDeclaration method : target.getMethodsByName(name)) {
                        if (method.isStatic() && checkAccess.test(method)) {
                            addEdge(n, method, IMPORT);
                        }
                    }
                    target.getFieldByName(name).ifPresent(field -> addEdge(n, field, IMPORT));
                } else {
                    addEdge(n, target, IMPORT);
                }
            }
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, FileView arg) {
            if (isOutside(n, arg)) {
                return;
            }
            Stream.concat(
                    n.getExtendedTypes().stream().map(t -> new Pair<>(t, EXTEND)),
                    n.getImplementedTypes().stream().map(t -> new Pair<>(t, IMPLEMENT))
            ).forEach(pair -> {
                ClassOrInterfaceType type = pair.a;
                analyzeType(type);
                ClassOrInterfaceDeclaration superClass = getClassOrInterface(type);
                if (superClass != null) {
                    addEdge(n, superClass, pair.b);
                }
            });
            n.getMembers().accept(this, arg);
            n.getAnnotations().forEach(expr -> expr.accept(this, arg));
            createOverload(n);
        }

        @Override
        public void visit(EnumDeclaration n, FileView arg) {
            if (isOutside(n, arg)) {
                return;
            }
            n.getImplementedTypes().forEach(type -> {
                analyzeType(type);
                ClassOrInterfaceDeclaration parent = getClassOrInterface(type);
                if (parent != null) {
                    addEdge(n, parent, IMPLEMENT);
                }
            });
            n.getMembers().accept(this, arg);
            n.getEntries().accept(this, arg);
            n.getAnnotations().forEach(expr -> expr.accept(this, arg));
            createOverload(n);
        }

        private void createOverload(TypeDeclaration<?> cls) {
            List<MethodDeclaration> methods = new ArrayList<>(cls.getMethods());
            methods.sort(Comparator.comparing(NodeWithSimpleName::getNameAsString));

            for (int i = 1; i < methods.size(); i++) {
                MethodDeclaration a = methods.get(i), b = methods.get(i - 1);
                if (a.getNameAsString().equals(b.getNameAsString())) {
                    addEdge(a, b, OVERLOAD);
                }
            }
        }

        @Override
        public void visit(FieldDeclaration n, FileView arg) {
            if (!isOutside(n, arg)) {
                n.getVariables().forEach(v -> {
                    analyzeType(v.getType());
                    v.getInitializer().ifPresent(e -> e.accept(this, arg));
                });
                n.getAnnotations().forEach(expr -> expr.accept(this, arg));
            }
        }

        @Override
        @SuppressWarnings("OptionalGetWithoutIsPresent")
        public void visit(MethodDeclaration n, FileView arg) {
            if (isOutside(n, arg)) {
                return;
            }
            // is not abstract
            n.getBody().ifPresent(body -> {
                body.accept(this, arg);
                // has Override annotation
                if (n.getAnnotationByName("Override").isPresent()) {
                    Node parent = n.getParentNode().get();
                    MethodDeclaration target = null;
                    Signature signature = n.getSignature();

                    if (parent instanceof ClassOrInterfaceDeclaration cls) {
                        target = resolveOverride(cls, n.getSignature());
                    } else {
                        if (parent instanceof EnumConstantDeclaration entry) {
                            EnumDeclaration outerEnum = (EnumDeclaration) entry.getParentNode().get();
                            // If outer enum has no such method, go to interface
                            if ((target = getMethodBySignature(outerEnum, signature)) == null) {
                                parent = outerEnum;
                            }
                        }
                        if (parent instanceof EnumDeclaration e) {
                            for (ClassOrInterfaceType type : e.getImplementedTypes()) {
                                ClassOrInterfaceDeclaration cls = getClassOrInterface(type);
                                if (cls == null) {
                                    continue;
                                }
                                if ((target = lookupInterface(cls, signature)) != null) {
                                    break;
                                }
                            }
                        }
                    }
                    if (target != null) {
                        addEdge(n, target, OVERRIDE);
                    }
                }
                applyDataFlow(n.getParameters(), body.getStatements());
            });
            analyzeType(n.getType());
            n.getThrownExceptions().forEach(this::analyzeType);
            n.getParameters().forEach(param -> analyzeType(param.getType()));
            n.getAnnotations().forEach(expr -> expr.accept(this, arg));
//            for (Node child : n.getChildNodes()) {
//                addEdge(n, child, CONTAIN);
//            }
        }

        @Override
        public void visit(ConstructorDeclaration n, FileView arg) {
            if (!isOutside(n, arg)) {
                n.getThrownExceptions().forEach(this::analyzeType);
                applyDataFlow(n.getParameters(), n.getBody().getStatements());
            }
        }

        @Override
        public void visit(LocalClassDeclarationStmt n, FileView arg) {
            n.getClassDeclaration().accept(this, arg);
        }

        // TODO: need test
        @Override
        public void visit(ClassOrInterfaceType n, FileView arg) {
            analyzeType(n);
            n.getTypeArguments().ifPresent(types -> types.forEach(this::analyzeType));
        }

        @Override
        public void visit(MethodCallExpr n, FileView arg) {
            if (isOutside(n, arg)) {
                return;
            }
//            logger.error("{}", n.getScope().map(Expression::getClass).orElse(null));
            n.getScope().ifPresent(scope -> scope.accept(this, arg));
            createRef(n, n.getNameAsString(), 1);
            n.getArguments().forEach(v -> v.accept(this, arg));
        }


        @Override
        public void visit(MethodReferenceExpr n, FileView arg) {
//            logger.error(n);
            n.getScope().accept(this, arg);
            createRef(n, n.getId(), 1);
            n.getTypeArguments().ifPresent(types -> {
                for (Type type : types) {
                    this.analyzeType(type);
                }
            });
        }

        @Override
        public void visit(MarkerAnnotationExpr n, FileView arg) {
            if (!isOutside(n, arg)) {
                createRef(n, n.getName().getId(), -1);
            }
        }

        @Override
        public void visit(NormalAnnotationExpr n, FileView arg) {
            if (!isOutside(n, arg)) {
                createRef(n, n.getName().getId(), -1);
            }
        }

        @Override
        public void visit(SingleMemberAnnotationExpr n, FileView arg) {
            if (!isOutside(n, arg)) {
                createRef(n, n.getName().getId(), -1);
            }
        }

        @Override
        public void visit(NameExpr n, FileView arg) {
            createRef(n, n.getNameAsString(), 0);
        }

        public void createRef(Node node, String name, int staticMark) {
            for (ImportDeclaration stmt : root.getImports()) {
                if (stmt.isAsterisk() || staticMark == (stmt.isStatic() ? -1 : 1)) {
                    continue;
                }
                if (name.equals(stmt.getName().getId())) {
                    addEdge(node, stmt, REFERENCE);
                }
            }
        }


        @Override
        public void visit(ClassExpr n, FileView arg) {
            analyzeType(n.getType());
        }

        // ======= end visitor logic =======

        private void applyDataFlow(List<Parameter> params, List<Statement> statements) {
            // Process def-use and type here
            File log = new File("output/exception.log");
            // call analyzeType() for params or vars
            DataFlow proxy = RunProxy.createProxy(DataFlow.class, new RunProxy.ExceptionLogger(log), null,
                    new Class[]{List.class, List.class}, new Object[]{params, statements});

            proxy.analyze();
            proxy.getUseToDef().forEach((use, def) -> addEdge(use, def, DEF_USE));
            proxy.getControl().forEach((condition, stmt) -> addEdge(condition, stmt, CONTROL));
            proxy.getCallFact().forEach((site, callees) -> {
                for (Node callee : callees) {
                    addEdge(site, callee, METHOD_CALL);
                }
            });

//			logger.trace(proxy.getCallFact());
//			logger.trace(proxy.getTypeRefs().toString(n -> n instanceof SimpleName));
//			logger.trace(proxy.getUseToDef().toString());
        }

        public boolean isJRELibrary(String imported) {
            return Stream.of("java.", "javax.", "javafx.", "jdk.", "sun.").anyMatch(imported::startsWith);
        }

        private boolean isOutside(Node node, FileView view) {
            boolean[] inside = {false};
            node.getRange().ifPresent(range -> {
                for (Region region : view.getRegions()) {
                    inside[0] |= region.intersect(range.begin.line, range.end.line);
                }
            });
            return !inside[0];
        }

        private MethodDeclaration getMethodBySignature(TypeDeclaration<?> cls, Signature signature) {
            return cls.getMethods().stream()
                    .filter(m -> m.getSignature().equals(signature))
                    .findFirst()
                    .orElse(null);
        }

        private void analyzeType(Type type) {
            type = type.getElementType();
            if (!type.isReferenceType() || visited.contains(type)) {
                return;
            }
            visited.add(type);
            ClassOrInterfaceType ctype = type.asClassOrInterfaceType();
            ResolvedReferenceTypeDeclaration resolved = resolveReferenceType(ctype);
            String scope = StringReps.beforeChar(ctype.getNameWithScope(), '.');
            String qname;

            if (resolved != null) {
                qname = StringReps.removeTail(resolved.getQualifiedName(), ctype.getNameWithScope().length() + 1);
                var cls = unwrapResolved(resolved);
                if (cls != null && inCommonPackage(cls, root) && !cls.getName().equals(ctype.getName())) {
                    addEdge(ctype, cls, DEF_USE);
                }
            } else {
                qname = null;
            }

            for (ImportDeclaration stmt : root.getImports()) {
                if (stmt.isStatic()) {
                    continue;
                }
                boolean flag = stmt.isAsterisk() ? stmt.getNameAsString().equals(qname) : stmt.getName().getId().equals(scope);
                if (flag) {
                    addEdge(ctype, stmt, REFERENCE);
                }
            }
        }

        // Only process class which declared in local file system
        private TypeDeclaration<?> unwrapResolved(ResolvedReferenceTypeDeclaration resolved) {
            if (resolved instanceof JavaParserClassDeclaration n) {
                return n.getWrappedNode();
            }
            if (resolved instanceof JavaParserInterfaceDeclaration n) {
                return n.getWrappedNode();
            }
            if (resolved instanceof JavaParserEnumDeclaration n) {
                return n.getWrappedNode();
            }
            if (resolved instanceof JavaParserAnnotationDeclaration n) {
                return (AnnotationDeclaration) n.toAst().orElse(null);
            }
            return null;
        }

        private ClassOrInterfaceDeclaration getClassOrInterface(Type type) {
            ResolvedReferenceTypeDeclaration resolved = resolveReferenceType(type);
            return (ClassOrInterfaceDeclaration) unwrapResolved(resolved);
        }

        private ResolvedReferenceTypeDeclaration resolveReferenceType(Type type) {
            try {
                ResolvedType resolved = type.getElementType().resolve();
                if (resolved.isReferenceType()) {
                    return resolved.asReferenceType()
                            .getTypeDeclaration()
                            .orElse(null);
                }
            } catch (UnsolvedSymbolException ignored) {

            }
            return null;
        }

        private ClassOrInterfaceDeclaration getSuperClass(ClassOrInterfaceDeclaration c) {
            var types = c.getExtendedTypes();
            return types.isEmpty() ? null : getClassOrInterface(types.get(0));
        }

        private MethodDeclaration resolveOverride(ClassOrInterfaceDeclaration cls, Signature signature) {
            for (var c = cls; (c = getSuperClass(c)) != null; ) {
                var method = getMethodBySignature(c, signature);
                if (method != null && isVisibleForOverride(method)) {
                    return method;
                }
            }
            for (var c = cls; c != null; c = getSuperClass(c)) {
                for (ClassOrInterfaceType type : c.getImplementedTypes()) {
                    var iface = getClassOrInterface(type);
                    if (iface != null) {
                        var method = lookupInterface(iface, signature);
                        if (method != null) {
                            return method;
                        }
                    }
                }
            }
            return null;
        }

        private boolean isVisibleForOverride(MethodDeclaration method) {
            if (method.isStatic() || method.isPrivate() || method.isFinal()) {
                return false;
            }
            return !method.isDefault() || inCommonPackage(method, root);
        }

        private MethodDeclaration lookupInterface(ClassOrInterfaceDeclaration cls, Signature signature) {
            MethodDeclaration method = getMethodBySignature(cls, signature);
            if (method != null && isVisibleForOverride(method)) {
                return method;
            }
            for (ClassOrInterfaceType type : cls.getExtendedTypes()) {
                var iface = getClassOrInterface(type);
                if (iface != null && (method = lookupInterface(iface, signature)) != null) {
                    return method;
                }
            }
            return null;
        }

        private boolean inCommonPackage(Node x, Node y) {
            if ((x.findRootNode() instanceof CompilationUnit rx) &&
                    (y.findRootNode() instanceof CompilationUnit ry)) {
                return rx.getPackageDeclaration().equals(ry.getPackageDeclaration());
            }
            return false;
        }
    }

}
