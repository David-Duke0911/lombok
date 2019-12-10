class BuilderSingularWithPrefixesWithSetterPrefix {
	private java.util.List<String> _elems;
	@java.lang.SuppressWarnings("all")
	BuilderSingularWithPrefixesWithSetterPrefix(final java.util.List<String> elems) {
		this._elems = elems;
	}
	@java.lang.SuppressWarnings("all")
	public static class BuilderSingularWithPrefixesWithSetterPrefixBuilder {
		@java.lang.SuppressWarnings("all")
		private java.util.ArrayList<String> elems;
		@java.lang.SuppressWarnings("all")
		BuilderSingularWithPrefixesWithSetterPrefixBuilder() {
		}
		@java.lang.SuppressWarnings("all")
		public BuilderSingularWithPrefixesWithSetterPrefixBuilder withElem(final String elem) {
			if (this.elems == null) this.elems = new java.util.ArrayList<String>();
			this.elems.add(elem);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		public BuilderSingularWithPrefixesWithSetterPrefixBuilder withElems(final java.util.Collection<? extends String> elems) {
			if (this.elems == null) this.elems = new java.util.ArrayList<String>();
			this.elems.addAll(elems);
			return this;
		}
		@java.lang.SuppressWarnings("all")
		public BuilderSingularWithPrefixesWithSetterPrefixBuilder clearElems() {
			if (this.elems != null) this.elems.clear();
			return this;
		}
		@java.lang.SuppressWarnings("all")
		public BuilderSingularWithPrefixesWithSetterPrefix build() {
			java.util.List<String> elems;
			switch (this.elems == null ? 0 : this.elems.size()) {
			case 0: 
				elems = java.util.Collections.emptyList();
				break;
			case 1: 
				elems = java.util.Collections.singletonList(this.elems.get(0));
				break;
			default: 
				elems = java.util.Collections.unmodifiableList(new java.util.ArrayList<String>(this.elems));
			}
			return new BuilderSingularWithPrefixesWithSetterPrefix(elems);
		}
		@java.lang.Override
		@java.lang.SuppressWarnings("all")
		public java.lang.String toString() {
			return "BuilderSingularWithPrefixesWithSetterPrefix.BuilderSingularWithPrefixesWithSetterPrefixBuilder(elems=" + this.elems + ")";
		}
	}
	@java.lang.SuppressWarnings("all")
	public static BuilderSingularWithPrefixesWithSetterPrefixBuilder builder() {
		return new BuilderSingularWithPrefixesWithSetterPrefixBuilder();
	}
}
