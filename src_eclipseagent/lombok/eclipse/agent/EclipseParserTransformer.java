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
package lombok.eclipse.agent;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Transforms Eclipse's <code>org.eclipse.jdt.internal.compiler.parser.Parser</code> class,
 * which handles all the actual java source to AST tree conversion in eclipse.
 * 
 * Transformations applied:<ul>
 * <li>The <code>CompilationUnitDeclaration endParse(int)</code> method is instrumented to call
 * the lombok framework via ClassLoaderWorkaround to TransformEclipseAST with the CompilationUnitDeclaration
 * right before it returns the CompilationUnitDeclaration.</li>
 * <li>The <code>getMethodBodies(CompilationUnitDeclaration)</code> is similarly instrumented; eclipse uses a
 * 2-step parsing system, where the bodies of methods aren't filled in until its actually neccessary. Lombok
 * gets a chance to change the AST immediately after either step.</li>
 * <li>The <code>parse(MethodDeclaration, CUD)</code>, <code>parse(ConstructorDeclaration, CUD)</code> and
 * <code>parse(Initializer, TypeDeclaration, CUD)</code> methods could all be theoretically called to
 * flesh out just one part of a half-finished AST tree. These methods are all instrumented to call Lombok
 * with the CUD before returning it to whomever wanted it filled out.</li>
 */
class EclipseParserTransformer {
	private static final String COMPILER_PKG =
		"Lorg/eclipse/jdt/internal/compiler/ast/";
	private static final String TARGET_STATIC_CLASS = "java/lombok/eclipse/ClassLoaderWorkaround";
	private static final String TARGET_STATIC_METHOD_NAME = "transformCompilationUnitDeclaration";
	private static final String TARGET_STATIC_METHOD_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)V";
	
	private static final Map<String, Class<? extends MethodVisitor>> rewriters;
	
	static {
		Map<String, Class<? extends MethodVisitor>> map = new HashMap<String, Class<? extends MethodVisitor>>();
		map.put(String.format("endParse(I)%sCompilationUnitDeclaration;", COMPILER_PKG), EndParsePatcher.class);
		map.put(String.format("getMethodBodies(%sCompilationUnitDeclaration;)V", COMPILER_PKG), GetMethodBodiesPatcher.class);
		map.put(String.format("parse(%1$sMethodDeclaration;%1$sCompilationUnitDeclaration;)V", COMPILER_PKG), ParseBlockContainerPatcher.class);
		map.put(String.format("parse(%1$sConstructorDeclaration;%1$sCompilationUnitDeclaration;Z)V", COMPILER_PKG), ParseBlockContainerPatcher.class);
		map.put(String.format("parse(%1$sInitializer;%1$sTypeDeclaration;%1$sCompilationUnitDeclaration;)V", COMPILER_PKG), ParseBlockContainerPatcher.class);
		rewriters = Collections.unmodifiableMap(map);
	}
	
	byte[] transform(byte[] classfileBuffer) {
		ClassReader reader = new ClassReader(classfileBuffer);
		ClassWriter writer = new ClassWriter(reader, 0);
		
		ClassAdapter adapter = new ParserPatcherAdapter(writer);
		reader.accept(adapter, 0);
		return writer.toByteArray();
	}
	
	static RuntimeException sneakyThrow(Throwable t) {
		if ( t == null ) throw new NullPointerException("t");
		EclipseParserTransformer.<RuntimeException>sneakyThrow0(t);
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private static <T extends Throwable> void sneakyThrow0(Throwable t) throws T {
		throw (T)t;
	}
	
	private static class ParserPatcherAdapter extends ClassAdapter {
		public ParserPatcherAdapter(ClassVisitor cv) {
			super(cv);
		}
		
		@Override public MethodVisitor visitMethod(int access, String name, String desc,
				String signature, String[] exceptions) {
			MethodVisitor writerVisitor = super.visitMethod(access, name, desc, signature, exceptions);
			Class<? extends MethodVisitor> targetVisitorClass = rewriters.get(name+desc);
			if ( targetVisitorClass == null ) return writerVisitor;
			
			try {
				Constructor<? extends MethodVisitor> c = targetVisitorClass.getDeclaredConstructor(MethodVisitor.class);
				c.setAccessible(true);
				return c.newInstance(writerVisitor);
			} catch ( InvocationTargetException e ) {
				throw sneakyThrow(e.getCause());
			} catch ( Exception e ) {
				//NoSuchMethodException: We know they exist.
				//IllegalAccessException: We called setAccessible.
				//InstantiationException: None of these classes are abstract.
				throw sneakyThrow(e);
			}
		}
	}
	
	private static final int BIT24 = 0x800000;
	
	static class GetMethodBodiesPatcher extends MethodAdapter {
		GetMethodBodiesPatcher(MethodVisitor mv) {
			super(mv);
		}
		
		@Override public void visitInsn(int opcode) {
			if ( opcode == Opcodes.RETURN ) {
				//injects: ClassLoaderWorkaround.transformCUD(parser, compilationUnitDeclaration);
				super.visitVarInsn(Opcodes.ALOAD, 0);
				super.visitVarInsn(Opcodes.ALOAD, 1);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET_STATIC_CLASS,
						TARGET_STATIC_METHOD_NAME, TARGET_STATIC_METHOD_DESC);
			}
			super.visitInsn(opcode);
		}
	}
	
	static class ParseBlockContainerPatcher extends MethodAdapter {
		ParseBlockContainerPatcher(MethodVisitor mv) {
			super(mv);
		}
		
		@Override public void visitCode() {
			//injects: if ( constructorDeclaration.bits & BIT24 > 0 ) return;
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitFieldInsn(Opcodes.GETFIELD, "org/eclipse/jdt/internal/compiler/ast/ASTNode", "bits", "I");
			mv.visitLdcInsn(Integer.valueOf(BIT24));
			mv.visitInsn(Opcodes.IAND);
			Label l0 = new Label();
			mv.visitJumpInsn(Opcodes.IFLE, l0);
			mv.visitInsn(Opcodes.RETURN);
			mv.visitLabel(l0);
			mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
			super.visitCode();
		}
	}
	
	static class EndParsePatcher extends MethodAdapter {
		private static final String TARGET_STATIC_METHOD_NAME = "transformCompilationUnitDeclaration";
		
		EndParsePatcher(MethodVisitor mv) {
			super(mv);
		}
		
		@Override public void visitInsn(int opcode) {
			if ( opcode == Opcodes.ARETURN ) {
				//injects: ClassLoaderWorkaround.transformCUD(parser, compilationUnitDeclaration);
				super.visitInsn(Opcodes.DUP);
				super.visitVarInsn(Opcodes.ALOAD, 0);
				super.visitInsn(Opcodes.SWAP);
				super.visitMethodInsn(Opcodes.INVOKESTATIC, TARGET_STATIC_CLASS,
						TARGET_STATIC_METHOD_NAME, TARGET_STATIC_METHOD_DESC);
			}
			
			super.visitInsn(opcode);
		}
	}
}
