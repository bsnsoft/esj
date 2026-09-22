package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** The example documents of the repository, read from the test class path. */
final class Examples {

    private Examples() {
    }

    /**
     * Reads an example into a document.
     *
     * @param name the name of the example, without its extension
     * @return the document
     */
    static SemanticDocument document(String name) {
        return EsjReader.strict().read(bytes("/examples/" + name + ".esj.json"));
    }

    /**
     * Returns the canonical golden file of an example.
     *
     * @param name the name of the example, without its extension
     * @return the bytes of {@code examples/<name>.canonical.esj.json}
     */
    static byte[] canonical(String name) {
        return bytes("/examples/" + name + ".canonical.esj.json");
    }

    private static byte[] bytes(String resource) {
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
