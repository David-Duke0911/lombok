import java.io.IOException;
public class ValInTryWithResources {
	public void whyTryInsteadOfCleanup() throws IOException {
		try (java.io.InputStream in = getClass().getResourceAsStream("ValInTryWithResources.class")) {
			final java.io.InputStream i = in;
			final int j = in.read();
		}
	}
}
