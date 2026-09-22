package de.bsnsoft.esj.invoice;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** The example documents of the repository, read from the test class path. */
final class Examples {

    private Examples() {
    }

    /**
     * Returns the canonical golden file of an example.
     *
     * @param name the name of the example, without its extension
     * @return the bytes of {@code examples/<name>.canonical.esj.json}
     */
    static byte[] canonical(String name) {
        String resource = "/examples/" + name + ".canonical.esj.json";
        try (InputStream in = Examples.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the test class path");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
