public class NonNullWithAlternateException {
	@lombok.NonNull
	private String test;
	public void testMethod(@lombok.NonNull String arg) {
		if (arg == null) {
			throw new java.lang.IllegalArgumentException("arg");
		}
		System.out.println(arg);
	}
	@java.lang.SuppressWarnings("all")
	public void setTest(@lombok.NonNull final String test) {
		if (test == null) {
			throw new java.lang.IllegalArgumentException("test");
		}
		this.test = test;
	}
}