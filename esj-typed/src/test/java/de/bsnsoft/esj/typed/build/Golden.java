package de.bsnsoft.esj.typed.build;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The example documents of the repository, read from the test class path. */
final class Golden {

    private Golden() {
    }

    /**
     * Returns the text of {@code examples/<name>.esj.json}, without trailing whitespace.
     *
     * @param name the name of the example, without its extension
     * @return the text of the example
     */
    static String example(String name) {
        String resource = "/examples/" + name + ".esj.json";
        try (InputStream in = Golden.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("not on the test class path: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    /**
     * Returns a document in the pretty form the examples are written in.
     *
     * @param document the document
     * @return its text, without trailing whitespace
     */
    static String pretty(SemanticDocument document) {
        return EsjWriter.pretty().toText(document).stripTrailing();
    }
}
