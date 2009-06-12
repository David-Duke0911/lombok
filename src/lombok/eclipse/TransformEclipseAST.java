package lombok.eclipse;

import java.lang.reflect.Field;

import lombok.eclipse.EclipseAST.Node;

import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.parser.Parser;

/**
 * Entry point for the Eclipse Parser patch that lets lombok modify the Abstract Syntax Tree as generated by
 * eclipse's parser implementations. This class is injected into the appropriate OSGi ClassLoader and can thus
 * use any classes that belong to org.eclipse.jdt.(apt.)core.
 * 
 * Note that, for any Method body, if Bit24 is set, the eclipse parser has been patched to never attempt to
 * (re)parse it. You should set Bit24 on any MethodDeclaration object you inject into the AST:
 * 
 * <code>methodDeclaration.bits |= 0x80000;</code>
 * 
 * @author rzwitserloot
 * @author rspilker
 */
public class TransformEclipseAST {
	private final EclipseAST ast;
	//The patcher hacks this field onto CUD. It's public.
	private static final Field astCacheField;
	private static final HandlerLibrary handlers;
	
	static {
		Field f = null;
		HandlerLibrary l = null;
		try {
			l = HandlerLibrary.load();
		} catch ( Exception e ) {
			e.printStackTrace();
		}
		try {
			f = CompilationUnitDeclaration.class.getDeclaredField("$lombokAST");
		} catch ( NoSuchFieldException ignore ) {
			ignore.printStackTrace();
		}
		astCacheField = f;
		handlers = l;
	}
	
	/**
	 * This method is called immediately after eclipse finishes building a CompilationUnitDeclaration, which is
	 * the top-level AST node when eclipse parses a source file. The signature is 'magic' - you should not
	 * change it!
	 * 
	 * Eclipse's parsers often operate in diet mode, which means many parts of the AST have been left blank.
	 * Be ready to deal with just about anything being null, such as the Statement[] arrays of the Method AST nodes.
	 * 
	 * @param parser The eclipse parser object that generated the AST.
	 * @param ast The AST node belonging to the compilation unit (java speak for a single source file).
	 */
	public static void transform(Parser parser, CompilationUnitDeclaration ast) {
		EclipseAST existing = getCache(ast);
		if ( existing == null ) {
			existing = new EclipseAST(ast);
			setCache(ast, existing);
		} else existing.reparse();
		new TransformEclipseAST(existing).go();
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
	
	public void go() {
		ast.traverse(new AnnotationVisitor());
	}
	
	private static class AnnotationVisitor extends EclipseASTAdapter {
		@Override public void visitField(Node node, FieldDeclaration field) {
			if ( field.annotations == null ) return;
			for ( Annotation annotation : field.annotations ) {
				handlers.handle((CompilationUnitDeclaration) node.top().node, node, annotation);
			}
		}
		
		@Override public void visitLocal(Node node, LocalDeclaration local) {
			if ( local.annotations == null ) return;
			for ( Annotation annotation : local.annotations ) {
				handlers.handle((CompilationUnitDeclaration) node.top().node, node, annotation);
			}
		}
		
		@Override public void visitMethod(Node node, AbstractMethodDeclaration method) {
			if ( method.annotations == null ) return;
			for ( Annotation annotation : method.annotations ) {
				handlers.handle((CompilationUnitDeclaration) node.top().node, node, annotation);
			}
		}
		
		@Override public void visitType(Node node, TypeDeclaration type) {
			if ( type.annotations == null ) return;
			for ( Annotation annotation : type.annotations ) {
				handlers.handle((CompilationUnitDeclaration) node.top().node, node, annotation);
			}
		}
	}
}
