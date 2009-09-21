/*
 * Copyright © 2009 Reinier Zwitserloot and Roel Spilker.
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
package lombok.eclipse;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import lombok.eclipse.EclipseAST.Node;

import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.parser.Parser;

/**
 * Entry point for the Eclipse Parser patch that lets lombok modify the Abstract Syntax Tree as generated by
 * Eclipse's parser implementations. This class is injected into the appropriate OSGi ClassLoader and can thus
 * use any classes that belong to org.eclipse.jdt.(apt.)core.
 * 
 * Note that, for any Method body, if Bit24 is set, the Eclipse parser has been patched to never attempt to
 * (re)parse it. You should set Bit24 on any MethodDeclaration object you inject into the AST:
 * 
 * <code>methodDeclaration.bits |= ASTNode.Bit24; //0x800000</code>
 * 
 * @author rzwitserloot
 * @author rspilker
 */
public class TransformEclipseAST {
	private final EclipseAST ast;
	//The patcher hacks this field onto CUD. It's public.
	private static final Field astCacheField;
	private static final HandlerLibrary handlers;
	
	private static boolean disableLombok = false;
	
	static {
		Field f = null;
		HandlerLibrary l = null;
		try {
			l = HandlerLibrary.load();
			f = CompilationUnitDeclaration.class.getDeclaredField("$lombokAST");
		} catch ( Throwable t ) {
			Eclipse.error(null, "Problem initializing lombok", t);
			disableLombok = true;
		}
		astCacheField = f;
		handlers = l;
	}
	
	private static final List<String> DONT_RUN_LIST = Collections.unmodifiableList(Arrays.asList(
			"org.eclipse.jdt.internal.corext.util.CodeFormatterUtil."
			));
	
	
	/**
	 * Returns 'true' if the stack trace indicates lombok definitely SHOULD run for this parse job, by checking the context,
	 * and returns 'false' if the stack trace indicates lombok should definitely NOT run.
	 * 
	 * Returns null if it can't tell (you probably should default to running lombok if you don't know).
	 */
	private static Boolean analyzeStackTrace(StackTraceElement[] trace) {
		for ( StackTraceElement e : trace )
			if ( e.toString().contains(DONT_RUN_LIST.get(0)) ) return false;
		return null;
		//potential speedup: if trace contains org.eclipse.swt.widgets. -> stop - nothing interesting ever follows that. I think.
	}
	
	/**
	 * This method is called immediately after Eclipse finishes building a CompilationUnitDeclaration, which is
	 * the top-level AST node when Eclipse parses a source file. The signature is 'magic' - you should not
	 * change it!
	 * 
	 * Eclipse's parsers often operate in diet mode, which means many parts of the AST have been left blank.
	 * Be ready to deal with just about anything being null, such as the Statement[] arrays of the Method AST nodes.
	 * 
	 * @param parser The Eclipse parser object that generated the AST.
	 * @param ast The AST node belonging to the compilation unit (java speak for a single source file).
	 */
	public static void transform(Parser parser, CompilationUnitDeclaration ast) {
		if ( disableLombok ) return;
		
		Boolean parse = analyzeStackTrace(new Throwable().getStackTrace());
		
		if ( parse != null && parse == false ) return;
		
		try {
			EclipseAST existing = getCache(ast);
			if ( existing == null ) {
				existing = new EclipseAST(ast);
				setCache(ast, existing);
			} else existing.reparse();
			new TransformEclipseAST(existing).go();
		} catch ( Throwable t ) {
			try {
				String message = "Lombok can't parse this source: " + t.toString();
				
				EclipseAST.addProblemToCompilationResult(ast, false, message, 0, 0);
				t.printStackTrace();
			} catch ( Throwable t2 ) {
				try {
					Eclipse.error(ast, "Can't create an error in the problems dialog while adding: " + t.toString(), t2);
				} catch ( Throwable t3 ) {
					//This seems risky to just silently turn off lombok, but if we get this far, something pretty
					//drastic went wrong. For example, the eclipse help system's JSP compiler will trigger a lombok call,
					//but due to class loader shenanigans we'll actually get here due to a cascade of
					//ClassNotFoundErrors. This is the right action for the help system (no lombok needed for that JSP compiler,
					//of course). 'disableLombok' is static, but each context classloader (e.g. each eclipse OSGi plugin) has
					//it's own edition of this class, so this won't turn off lombok everywhere.
					disableLombok = true;
				}
			}
		}
	}
	
	private static EclipseAST getCache(CompilationUnitDeclaration ast) {
		if ( astCacheField == null ) return null;
		try {
			return (EclipseAST)astCacheField.get(ast);
		} catch ( Exception e ) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static void setCache(CompilationUnitDeclaration ast, EclipseAST cache) {
		if ( astCacheField != null ) try {
			astCacheField.set(ast, cache);
		} catch ( Exception ignore ) {
			ignore.printStackTrace();
		}
	}
	
	public TransformEclipseAST(EclipseAST ast) {
		this.ast = ast;
	}
	
	/**
	 * First handles all lombok annotations except PrintAST, then calls all non-annotation based handlers.
	 * then handles any PrintASTs.
	 */
	public void go() {
		handlers.skipPrintAST();
		ast.traverse(new AnnotationVisitor());
		handlers.callASTVisitors(ast);
		handlers.skipAllButPrintAST();
		ast.traverse(new AnnotationVisitor());
	}
	
	private static class AnnotationVisitor extends EclipseASTAdapter {
		@Override public void visitAnnotationOnField(FieldDeclaration field, Node annotationNode, Annotation annotation) {
			if ( annotationNode.isHandled() ) return;
			CompilationUnitDeclaration top = (CompilationUnitDeclaration) annotationNode.top().get();
			boolean handled = handlers.handle(top, annotationNode, annotation);
			if ( handled ) annotationNode.setHandled();
		}
		
		@Override public void visitAnnotationOnMethodArgument(Argument arg, AbstractMethodDeclaration method, Node annotationNode, Annotation annotation) {
			if ( annotationNode.isHandled() ) return;
			CompilationUnitDeclaration top = (CompilationUnitDeclaration) annotationNode.top().get();
			boolean handled = handlers.handle(top, annotationNode, annotation);
			if ( handled ) annotationNode.setHandled();
		}
		
		@Override public void visitAnnotationOnLocal(LocalDeclaration local, Node annotationNode, Annotation annotation) {
			if ( annotationNode.isHandled() ) return;
			CompilationUnitDeclaration top = (CompilationUnitDeclaration) annotationNode.top().get();
			boolean handled = handlers.handle(top, annotationNode, annotation);
			if ( handled ) annotationNode.setHandled();
		}
		
		@Override public void visitAnnotationOnMethod(AbstractMethodDeclaration method, Node annotationNode, Annotation annotation) {
			if ( annotationNode.isHandled() ) return;
			CompilationUnitDeclaration top = (CompilationUnitDeclaration) annotationNode.top().get();
			boolean handled = handlers.handle(top, annotationNode, annotation);
			if ( handled ) annotationNode.setHandled();
		}
		
		@Override public void visitAnnotationOnType(TypeDeclaration type, Node annotationNode, Annotation annotation) {
			if ( annotationNode.isHandled() ) return;
			CompilationUnitDeclaration top = (CompilationUnitDeclaration) annotationNode.top().get();
			boolean handled = handlers.handle(top, annotationNode, annotation);
			if ( handled ) annotationNode.setHandled();
		}
	}
}
