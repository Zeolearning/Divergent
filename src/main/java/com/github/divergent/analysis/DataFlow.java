package com.github.divergent.analysis;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.ResolvedTypeVariable;
import com.github.javaparser.resolution.types.parametrization.ResolvedTypeParametersMap;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserVariableDeclaration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class DataFlow extends VoidVisitorAdapter<Void> {
	private static final Logger logger = LogManager.getLogger(DataFlow.class);
	private final MapFact<Node, Node> useToDef;
	private final MapFact<Node, Node> control;
	private final MapFact<Node, SetFact<Node>> callFact;
	private final MapFact<Node, SetFact<ResolvedType>> typeRefs;
	private final List<Parameter> params;
	private final List<Statement> statements;

	public DataFlow(List<Parameter> params, List<Statement> statements) {
		this.params = params;
		this.statements = statements;
		this.useToDef = new MapFact<>();
		this.control = new MapFact<>();
		this.typeRefs = new MapFact<>();
		this.callFact = new MapFact<>();
	}

	public void analyze() {
		params.forEach(p -> p.accept(this, null));
		statements.forEach(s -> s.accept(this, null));
	}

	// ======= for statements =======

	@Override
	public void visit(IfStmt n, Void arg) {
		Expression condition = n.getCondition();
		n.getElseStmt().ifPresent(stmt -> {
			control.put(stmt, condition);
			control.put(n.getThenStmt(), condition);
			stmt.accept(this, arg);
		});
	}

	@Override
	public void visit(ForStmt n, Void arg) {
		n.getInitialization().accept(this, arg);
		n.getCompare().ifPresent(cmp -> {
			cmp.accept(this, arg);
			control.put(n.getBody(), cmp);
		});
		n.getBody().accept(this, arg);
		n.getUpdate().accept(this, arg);
	}

	@Override
	public void visit(DoStmt n, Void arg) {
		n.getBody().accept(this, arg);
		n.getCondition().accept(this, arg);
		control.put(n.getBody(), n.getCondition());
	}

	@Override
	public void visit(WhileStmt n, Void arg) {
		n.getCondition().accept(this, arg);
		n.getBody().accept(this, arg);
		control.put(n.getBody(), n.getCondition());
	}

	@Override
	public void visit(TryStmt n, Void arg) {
		BlockStmt block = n.getTryBlock();
		for (CatchClause clause : n.getCatchClauses()) {
			control.put(clause, block);
		}
	}

	// ======= for expressions =======

	@Override
	public void visit(Parameter n, Void arg) {
		Type type = n.getType().getElementType();
		if (type.isReferenceType()) {
			ResolvedType resolved = type.resolve();
			typeRefs.put(n.getName(), SetFact.of(resolved));
		}
	}

	@Override
	public void visit(VariableDeclarator n, Void arg) {
		var name = n.getName();
		if (typeRefs.hasKey(name)) {
			return;
		}
		Type type = n.getType();
		var output = new SetFact<ResolvedType>();
		n.getInitializer().ifPresent(init -> {
			init.accept(this, arg);

			typeRefs.ifKeyExists(init, value -> {
				ResolvedType base = getBaseType(type.resolve());
				var ltype = (ReferenceTypeImpl) base;
				if (ltype.typeParametersValues().isEmpty()) {
					output.merge(value);
				} else {
					// combine class type with generic type
					value.stream().filter(ResolvedType::isReferenceType).forEach(elem -> {
						var rtype = elem.asReferenceType();
						var opt = rtype.getTypeDeclaration();
						assert opt.isPresent();

						ResolvedReferenceTypeDeclaration clas = opt.get();
						if (clas.getTypeParameters().size() == rtype.getTypeParametersMap().size()) {
							output.add(elem);
						} else {
							output.add(new ReferenceTypeImpl(clas, ltype.typeParametersValues()));
						}
					});
				}
			});
		});
		if (type.isArrayType() || !output.isEmpty()) {
			typeRefs.put(name, output);
		}
	}

	@Override
	public void visit(NameExpr n, Void arg) {
		try {
			ResolvedValueDeclaration resolved = n.resolve();
			var name = acquireName(resolved);
			if (name != null) {
				useToDef.put(n, name);
				typeRefs.assign(n, name);
				if (resolved instanceof JavaParserFieldDeclaration field) {
					field.getVariableDeclarator().accept(this, arg);
				}
			}
		} catch (Exception e) {
			ResolvedType type = n.calculateResolvedType();
//			logger.error("{}, {}", n, type);
			typeRefs.put(n, SetFact.of(type));
		}
	}

	@Override
	public void visit(ObjectCreationExpr n, Void arg) {
		n.getScope().ifPresent(s -> s.accept(this, arg));
		ResolvedType type = n.calculateResolvedType();
		typeRefs.put(n, SetFact.of(type));
	}

	@Override
	public void visit(ArrayCreationExpr n, Void arg) {
		Type type = n.getElementType();
		if (!type.isReferenceType()) {
			return;
		}
		n.getInitializer().ifPresent(init -> {
			init.accept(this, arg);
			typeRefs.assign(n, init);
		});
	}

	@Override
	public void visit(ArrayInitializerExpr n, Void arg) {
		// should extract out as a new method to avoid nested expressions
		var output = new SetFact<ResolvedType>();
		for (Expression value : n.getValues()) {
			value.accept(this, arg);
			typeRefs.ifKeyExists(value, output::merge);
		}
		if (!output.isEmpty()) {
			typeRefs.put(n, output);
		}
	}

	@Override
	public void visit(ArrayAccessExpr n, Void arg) {
		Expression scope = n.getName(), index = n.getIndex();
		scope.accept(this, arg);
		index.accept(this, arg);

		// need to bind def-use to variable
		Node def = useToDef.get(scope);
		if (def != null) {
			useToDef.put(n, def);
			typeRefs.put(n, typeRefs.get(def));
		} else {
			ResolvedType type = getBaseType(n.calculateResolvedType());
			if (type.isReferenceType()) {
				typeRefs.put(n, SetFact.of(type));
			}
		}
	}

	// TODO: need complete test
	@Override
	public void visit(AssignExpr n, Void arg) {
		Expression lhs = n.getTarget(), rhs = n.getValue();
		lhs.accept(this, arg);
		rhs.accept(this, arg);

		typeRefs.ifKeyExists(rhs, value -> {
			// type of lhs: Name | FieldAccess | ArrayAccess
			// need to get definition
			if (useToDef.hasKey(lhs)) {
				Node def = useToDef.get(lhs);
				// check array access
				if (lhs instanceof ArrayAccessExpr e) {
					if (!e.calculateResolvedType().isArray()) {
						typeRefs.get(def).merge(value);
					}
				} else {
					typeRefs.put(def, value);
				}
			}
			// can use object ref directly
			typeRefs.put(n, value.copy());
		});
	}

	// TODO: need complete test
	@Override
	public void visit(FieldAccessExpr n, Void arg) {
		// Each FieldAccessExpr takes a scope
		Expression scope = n.getScope();
		if (scope.isThisExpr()) {
			ResolvedValueDeclaration resolved = n.resolve();
			ResolvedType type = getBaseType(resolved.getType());
			if (!type.isReferenceType()) {
				return;
			}
			if (resolved instanceof JavaParserFieldDeclaration field) {
				VariableDeclarator var = field.getVariableDeclarator();
				var.accept(this, arg);
				SimpleName name = var.getName();
				useToDef.put(n, name);
				if (typeRefs.hasKey(name)) {
					typeRefs.put(n, typeRefs.get(name));
					return;
				}
			}
		} else {
			scope.accept(this, arg);
			if (typeRefs.hasKey(scope)) {
				if (scope instanceof ArrayAccessExpr e && e.calculateResolvedType().isArray()) {
					return;
				}
				String field = n.getName().getId();
				SetFact<ResolvedType> output = new SetFact<>();
				typeRefs.get(scope).forEach(elem -> {
					if (elem instanceof ReferenceTypeImpl impl) {
						impl.getFieldType(field).ifPresent(type -> {
							ResolvedType ret = replaceTypeVariables(getBaseType(type), impl.typeParametersMap());
							if (ret.isReferenceType()) {
								output.add(ret);
							}
						});
					}
				});
				if (!output.isEmpty()) {
					typeRefs.put(n, output);
					return;
				}
			}
		}
		// no matched condition
		ResolvedType type = getBaseType(n.calculateResolvedType());
		if (type.isReferenceType()) {
			typeRefs.put(n, SetFact.of(type));
		}
	}

	// TODO: need test
	@Override
	public void visit(MethodCallExpr n, Void voidArg) {
		List<Expression> args = n.getArguments();
		args.forEach(arg -> arg.accept(this, voidArg));

		boolean found = false;
		if (n.getScope().isPresent()) {
			Expression scope = n.getScope().get();
			scope.accept(this, voidArg);
			if (typeRefs.hasKey(scope)) {
				SetFact<ResolvedType> types = typeRefs.get(scope);
//				logger.info("{}, {}, {}", n, scope, types);

				SetFact<Node> callees = SetFact.of();
				SetFact<ResolvedType> output = SetFact.of();
				for (ResolvedType type : types) {
					var impl = (ReferenceTypeImpl) type;
					var methods = impl.getAllMethodsVisibleToInheritors();
					for (ResolvedMethodDeclaration method : methods) {
						if (!method.getName().equals(n.getNameAsString()) || method.getNumberOfParams() != args.size()) {
							continue;
						}
						boolean adapt = true;
						for (int i = 0; i < args.size(); i++) {
							try {
								ResolvedType paramType = method.getParam(i).getType();
								Expression arg = args.get(i);
								if (typeRefs.hasKey(arg)) {
									adapt &= typeRefs.get(arg).stream().anyMatch(paramType::isAssignableBy);
								} else {
									adapt &= paramType.isAssignableBy(arg.calculateResolvedType());
								}
							} catch (Exception ignored) {

							}
						}
						if (adapt) {
							found = true;
							method.toAst().ifPresent(callees::add);
							ResolvedType ret = getBaseType(method.getReturnType());
							if (ret.isReferenceType()) {
								ResolvedType replaced = replaceTypeVariables(ret, impl.typeParametersMap());
								output.add(replaced);
							}
						}
					}
				}
				if (!callees.isEmpty()) {
					callFact.put(n, callees);
				}
				if (!output.isEmpty()) {
					typeRefs.put(n, output);
				}
			}
		}
		if (!found) {
			n.resolve().toAst().ifPresent(method -> callFact.put(n, SetFact.of(method)));
//			logger.error("{} -> {}", n, n.resolve().toAst());
			ResolvedType type = getBaseType(n.calculateResolvedType());
			if (type.isReferenceType()) {
				typeRefs.put(n, SetFact.of(type));
			}
		}
	}

	// TODO: LambdaExpr

	@Override
	public void visit(MethodReferenceExpr n, Void arg) {
		Expression scope = n.getScope();
		scope.accept(this, arg);
		if (typeRefs.hasKey(scope)) {
			SetFact<ResolvedType> types = typeRefs.get(scope);
			SetFact<Node> output = SetFact.of();
			for (ResolvedType type : types) {
				var impl = (ReferenceTypeImpl) type;
				var methods = impl.getAllMethodsVisibleToInheritors();
				for (ResolvedMethodDeclaration method : methods) {
					if (method.isStatic() && method.getName().equals(n.getId())) {
//						logger.error("{} -> {}", n, method);
						method.toAst().ifPresent(output::add);
					}
				}
			}
		} else {
			n.resolve().toAst().ifPresent(method -> callFact.put(n, SetFact.of(method)));
		}
	}

	@Override
	public void visit(TypeExpr n, Void arg) {
		ResolvedType type = n.calculateResolvedType();
		typeRefs.put(n, SetFact.of(type));
	}

	@Override
	public void visit(CastExpr n, Void arg) {
		n.getExpression().accept(this, arg);
		ResolvedType type = getBaseType(n.getType().resolve());
		if (type.isReferenceType()) {
			typeRefs.put(n, SetFact.of(type));
		}
	}

	@Override
	public void visit(InstanceOfExpr n, Void arg) {
		n.getExpression().accept(this, arg);
		ResolvedType type = n.getType().getElementType().resolve();
		if (type.isReferenceType()) {
			typeRefs.put(n, SetFact.of(type));
			n.getPattern().ifPresent(pattern -> typeRefs.put(pattern.asNameExpr().getName(), SetFact.of(type)));
		}
	}

	@Override
	public void visit(NormalAnnotationExpr n, Void arg) {
		for (MemberValuePair pair : n.getPairs()) {
			pair.getValue().accept(this, arg);
		}
	}

	@Override
	public void visit(SingleMemberAnnotationExpr n, Void arg) {
		n.getMemberValue().accept(this, arg);
	}

	private ResolvedType replaceTypeVariables(ResolvedType type, ResolvedTypeParametersMap map) {
		if (type instanceof ResolvedTypeVariable) {
			return map.getValue(type.asTypeParameter());
		}
		if (type instanceof ReferenceTypeImpl impl) {
			List<ResolvedType> replaced = impl.typeParametersValues().stream()
					.map(t -> replaceTypeVariables(t, map)).toList();

			assert impl.getTypeDeclaration().isPresent();
			return new ReferenceTypeImpl(impl.getTypeDeclaration().get(), replaced);
		}
		return type;
	}

	private SimpleName acquireName(ResolvedValueDeclaration n) {
		if (n instanceof JavaParserClassDeclaration cls) {
			return cls.getWrappedNode().getName();
		}
		if (n instanceof JavaParserVariableDeclaration var) {
			return var.getVariableDeclarator().getName();
		}
		if (n instanceof JavaParserFieldDeclaration field) {
			return field.getVariableDeclarator().getName();
		}
		if (n instanceof JavaParserParameterDeclaration param) {
			return param.getWrappedNode().getName();
		}
		return null;
	}

	private ResolvedType getBaseType(ResolvedType type) {
		for (; type.isArray(); type = type.asArrayType().getComponentType()) ;
		return type;
	}

	public MapFact<Node, SetFact<ResolvedType>> getTypeRefs() {
		return typeRefs;
	}

	public MapFact<Node, Node> getUseToDef() {
		return useToDef;
	}

	public MapFact<Node, Node> getControl() {
		return control;
	}

	public MapFact<Node, SetFact<Node>> getCallFact() {
		return callFact;
	}
}
