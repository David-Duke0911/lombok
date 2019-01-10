/*
 * Copyright (C) 2015-2018 The Project Lombok Authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.javac.handlers.singulars;

import static lombok.javac.Javac.*;
import static lombok.javac.handlers.JavacHandlerUtil.*;

import java.util.Collections;

import lombok.core.GuavaTypeMap;
import lombok.core.LombokImmutableList;
import lombok.core.handlers.HandlerUtil;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import lombok.javac.handlers.JavacHandlerUtil;
import lombok.javac.handlers.JavacSingularsRecipes.ExpressionMaker;
import lombok.javac.handlers.JavacSingularsRecipes.JavacSingularizer;
import lombok.javac.handlers.JavacSingularsRecipes.SingularData;
import lombok.javac.handlers.JavacSingularsRecipes.StatementMaker;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

abstract class JavacGuavaSingularizer extends JavacSingularizer {
	protected String getSimpleTargetTypeName(SingularData data) {
		return GuavaTypeMap.getGuavaTypeName(data.getTargetFqn());
	}
	
	protected String getBuilderMethodName(SingularData data) {
		String simpleTypeName = getSimpleTargetTypeName(data);
		if ("ImmutableSortedSet".equals(simpleTypeName) || "ImmutableSortedMap".equals(simpleTypeName)) return "naturalOrder";
		return "builder";
	}
	
	@Override public java.util.List<JavacNode> generateFields(SingularData data, JavacNode builderType, JCTree source) {
		JavacTreeMaker maker = builderType.getTreeMaker();
		String simpleTypeName = getSimpleTargetTypeName(data);
		JCExpression type = JavacHandlerUtil.chainDots(builderType, "com", "google", "common", "collect", simpleTypeName, "Builder");
		type = addTypeArgs(getTypeArgumentsCount(), false, builderType, type, data.getTypeArgs(), source);
		
		JCVariableDecl buildField = maker.VarDef(maker.Modifiers(Flags.PRIVATE), data.getPluralName(), type, null);
		return Collections.singletonList(injectFieldAndMarkGenerated(builderType, buildField));
	}
	
	@Override public void generateMethods(SingularData data, boolean deprecate, JavacNode builderType, JCTree source, boolean fluent, ExpressionMaker returnTypeMaker, StatementMaker returnStatementMaker) {
		doGenerateMethods(data, deprecate, builderType, source, fluent, returnTypeMaker, returnStatementMaker);
	}
	
	@Override
	protected JCStatement generateClearStatements(JavacTreeMaker maker, SingularData data, JavacNode builderType) {
		JCExpression thisDotField = maker.Select(maker.Ident(builderType.toName("this")), data.getPluralName());
		return maker.Exec(maker.Assign(thisDotField, maker.Literal(CTC_BOT, null)));
	}

	@Override
	protected void generateSingularMethod(boolean deprecate, JavacTreeMaker maker, JCExpression returnType, JCStatement returnStatement, SingularData data, JavacNode builderType, JCTree source, boolean fluent) {
		LombokImmutableList<String> suffixes = getArgumentSuffixes();
		Name[] names = new Name[suffixes.size()];
		for (int i = 0; i < names.length; i++) {
			String s = suffixes.get(i);
			Name n = data.getSingularName();
			names[i] = s.isEmpty() ? n : builderType.toName(s);
		}

		ListBuffer<JCStatement> statements = new ListBuffer<JCStatement>();
		statements.append(createConstructBuilderVarIfNeeded(maker, data, builderType, source));
		JCExpression thisDotFieldDotAdd = chainDots(builderType, "this", data.getPluralName().toString(), getAddMethodName());
		ListBuffer<JCExpression> invokeAddExprBuilder = new ListBuffer<JCExpression>();
		for (Name name : names) {
			invokeAddExprBuilder.append(maker.Ident(name));
		}
		List<JCExpression> invokeAddExpr = invokeAddExprBuilder.toList();
		JCExpression invokeAdd = maker.Apply(List.<JCExpression>nil(), thisDotFieldDotAdd, invokeAddExpr);
		JCStatement st = maker.Exec(invokeAdd);
		statements.append(st);

		ListBuffer<JCVariableDecl> params = new ListBuffer<JCVariableDecl>();
		for (int i = 0; i < names.length; i++) {
			params.append(generateSingularMethodParameter(i, maker, data, builderType, source, names[i]));
		}

		finishAndInjectSingularMethod(maker, returnType, returnStatement, data, builderType, source, fluent, deprecate, statements, params.toList(), getAddMethodName());
	}
	
	@Override
	protected void generatePluralMethod(boolean deprecate, JavacTreeMaker maker, JCExpression returnType, JCStatement returnStatement, SingularData data, JavacNode builderType, JCTree source, boolean fluent) {
		JCModifiers mods = makeMods(maker, builderType, deprecate);
		ListBuffer<JCStatement> statements = new ListBuffer<JCStatement>();
		statements.append(createConstructBuilderVarIfNeeded(maker, data, builderType, source));
		JCExpression thisDotFieldDotAddAll = chainDots(builderType, "this", data.getPluralName().toString(), getAddMethodName() + "All");
		JCExpression invokeAddAll = maker.Apply(List.<JCExpression>nil(), thisDotFieldDotAddAll, List.<JCExpression>of(maker.Ident(data.getPluralName())));
		statements.append(maker.Exec(invokeAddAll));
		if (returnStatement != null) statements.append(returnStatement);
		JCBlock body = maker.Block(0, statements.toList());
		Name methodName = data.getPluralName();
		long paramFlags = JavacHandlerUtil.addFinalIfNeeded(Flags.PARAMETER, builderType.getContext());
		if (!fluent) methodName = builderType.toName(HandlerUtil.buildAccessorName(getAddMethodName() + "All", methodName.toString()));
		JCExpression paramType;
		String aaTypeName = getAddAllTypeName();
		if (aaTypeName.startsWith("java.lang.") && aaTypeName.indexOf('.', 11) == -1) {
			paramType = genJavaLangTypeRef(builderType, aaTypeName.substring(10));
		} else {
			paramType = chainDotsString(builderType, aaTypeName);
		}
		paramType = addTypeArgs(getTypeArgumentsCount(), true, builderType, paramType, data.getTypeArgs(), source);
		JCVariableDecl param = maker.VarDef(maker.Modifiers(paramFlags), data.getPluralName(), paramType, null);
		finishAndInjectMethod(maker, returnType, builderType, source, mods, body, methodName, List.of(param));
    }
	
	@Override public void appendBuildCode(SingularData data, JavacNode builderType, JCTree source, ListBuffer<JCStatement> statements, Name targetVariableName, String builderVariable) {
		JavacTreeMaker maker = builderType.getTreeMaker();
		List<JCExpression> jceBlank = List.nil();
		
		JCExpression varType = chainDotsString(builderType, data.getTargetFqn());
		int argumentsCount = getTypeArgumentsCount();
		varType = addTypeArgs(argumentsCount, false, builderType, varType, data.getTypeArgs(), source);
		
		JCExpression empty; {
			//ImmutableX.of()
			JCExpression emptyMethod = chainDots(builderType, "com", "google", "common", "collect", getSimpleTargetTypeName(data), "of");
			List<JCExpression> invokeTypeArgs = createTypeArgs(argumentsCount, false, builderType, data.getTypeArgs(), source);
			empty = maker.Apply(invokeTypeArgs, emptyMethod, jceBlank);
		}
		
		JCExpression invokeBuild; {
			//this.pluralName.build();
			invokeBuild = maker.Apply(jceBlank, chainDots(builderType, builderVariable, data.getPluralName().toString(), "build"), jceBlank);
		}
		
		JCExpression isNull; {
			//this.pluralName == null
			isNull = maker.Binary(CTC_EQUAL, maker.Select(maker.Ident(builderType.toName(builderVariable)), data.getPluralName()), maker.Literal(CTC_BOT, null));
		}
		
		JCExpression init = maker.Conditional(isNull, empty, invokeBuild); // this.pluralName == null ? ImmutableX.of() : this.pluralName.build()
		
		JCStatement jcs = maker.VarDef(maker.Modifiers(0), data.getPluralName(), varType, init);
		statements.append(jcs);
	}
	
	protected JCStatement createConstructBuilderVarIfNeeded(JavacTreeMaker maker, SingularData data, JavacNode builderType, JCTree source) {
		List<JCExpression> jceBlank = List.nil();
		
		JCExpression thisDotField = maker.Select(maker.Ident(builderType.toName("this")), data.getPluralName());
		JCExpression thisDotField2 = maker.Select(maker.Ident(builderType.toName("this")), data.getPluralName());
		JCExpression cond = maker.Binary(CTC_EQUAL, thisDotField, maker.Literal(CTC_BOT, null));
		
		JCExpression create = maker.Apply(jceBlank, chainDots(builderType, "com", "google", "common", "collect", getSimpleTargetTypeName(data), getBuilderMethodName(data)), jceBlank);
		JCStatement thenPart = maker.Exec(maker.Assign(thisDotField2, create));
		
		return maker.If(cond, thenPart, null);
	}
	
	protected abstract LombokImmutableList<String> getArgumentSuffixes();
	protected abstract String getAddMethodName();
	protected abstract String getAddAllTypeName();
	
	protected int getTypeArgumentsCount() {
		return getArgumentSuffixes().size();
	}
}
