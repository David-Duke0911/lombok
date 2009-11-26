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
package lombok.delombok;

import java.io.IOException;
import java.io.Writer;

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.main.OptionName;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Options;

public class CommentPreservingParser {
	
	private final String encoding;

	public CommentPreservingParser() {
		this("utf-8");
	}
	
	public CommentPreservingParser(String encoding) {
		this.encoding = encoding;
	}
	
	public ParseResult parseFile(String fileName) throws IOException {
		Context context = new Context();
		
		Options.instance(context).put(OptionName.ENCODING, encoding);
		
		CommentCollectingScanner.Factory.preRegister(context);
		
		JavaCompiler compiler = new JavaCompiler(context) {
			@Override
			protected boolean keepComments() {
				return true;
			}
		};
		compiler.genEndPos = true;
		
		Comments comments = new Comments();
		context.put(Comments.class, comments);

		comments.comments = List.nil();
		@SuppressWarnings("deprecation")
		JCCompilationUnit cu = compiler.parse(fileName);
		return new ParseResult(comments.comments, cu);
	}

	static class Comments {
		List<Comment> comments = List.nil();
		
		void add(int pos, String content) {
			comments = comments.append(new Comment(pos, content));
		}
	}
	
	public static class ParseResult {
		private final List<Comment> comments;
		private final JCCompilationUnit compilationUnit;
		
		private ParseResult(List<Comment> comments, JCCompilationUnit compilationUnit) {
			this.comments = comments;
			this.compilationUnit = compilationUnit;
		}
		
		public void print(Writer out) {
			compilationUnit.accept(new PrettyCommentsPrinter(out, compilationUnit, comments));
		}
	}
}