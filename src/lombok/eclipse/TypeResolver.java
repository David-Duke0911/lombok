package lombok.eclipse;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import lombok.core.TypeLibrary;
import lombok.eclipse.EclipseAST.Node;

import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ImportReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;

public class TypeResolver {
	private final TypeLibrary library;
	private Collection<String> imports;
	
	
	public TypeResolver(TypeLibrary library, EclipseAST.Node top) {
		this.library = library;
		this.imports = makeImportList((CompilationUnitDeclaration) top.getEclipseNode());
	}
	
	private static Collection<String> makeImportList(CompilationUnitDeclaration declaration) {
		Set<String> imports = new HashSet<String>();
		if ( declaration.currentPackage != null ) imports.add(toQualifiedName(declaration.currentPackage.getImportName()) + ".*");
		if ( declaration.imports != null ) for ( ImportReference importStatement : declaration.imports ) {
			imports.add(toQualifiedName(importStatement.getImportName()));
		}
		return imports;
	}
	
	public Collection<String> findTypeMatches(Node context, TypeReference type) {
		Collection<String> potentialMatches = library.findCompatible(toQualifiedName(type.getTypeName()));
		if ( potentialMatches.isEmpty() ) return Collections.emptyList();
		
		if ( type.getTypeName().length > 1 ) return potentialMatches;
		
		String simpleName = new String(type.getTypeName()[0]);
		
		//If there's an import statement that explicitly imports a 'Getter' that isn't any of our potentials, return no matches.
		if ( nameConflictInImportList(simpleName, potentialMatches) ) return Collections.emptyList();
		
		//Check if any of our potentials is even imported in the first place. If not: no matches.
		potentialMatches = eliminateImpossibleMatches(potentialMatches);
		if ( potentialMatches.isEmpty() ) return Collections.emptyList();
		
		//Find a lexically accessible type of the same simple name in the same Compilation Unit. If it exists: no matches.
		Node n = context;
		while ( n != null ) {
			if ( n.getEclipseNode() instanceof TypeDeclaration ) {
				char[] name = ((TypeDeclaration)n.getEclipseNode()).name;
				if ( name != null && new String(name).equals(simpleName) ) return Collections.emptyList();
			}
			n = n.up();
		}
		
		// The potential matches we found by comparing the import statements is our matching set. Return it.
		return potentialMatches;
	}
	
	private Collection<String> eliminateImpossibleMatches(Collection<String> potentialMatches) {
		Set<String> results = new HashSet<String>();
		
		for ( String importedType : imports ) {
			Collection<String> reduced = library.findCompatible(importedType);
			reduced.retainAll(potentialMatches);
			results.addAll(reduced);
		}
		
		return results;
	}
	
	private boolean nameConflictInImportList(String simpleName, Collection<String> potentialMatches) {
		for ( String importedType : imports ) {
			if ( !toSimpleName(importedType).equals(simpleName) ) continue;
			if ( potentialMatches.contains(importedType) ) continue;
			return true;
		}
		
		return false;
	}
	
	private static String toSimpleName(String typeName) {
		int idx = typeName.lastIndexOf('.');
		return idx == -1 ? typeName : typeName.substring(idx+1);
	}
	
	private static String toQualifiedName(char[][] typeName) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for ( char[] c : typeName ) {
			sb.append(first ? "" : ".").append(c);
			first = false;
		}
		return sb.toString();
	}
}
