package de.bsnsoft.esj.typed;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** The example documents and the two schemas of the repository, read from the test classpath. */
final class Examples {

    /**
     * The example documents of the default edition, in the order of the table in
     * {@code examples/README.md}. The example of the 2026 edition is not one of them: it
     * is read where it is measured against the registry and the schema of its own
     * edition, and a build made without that edition does not carry it.
     */
    static final List<String> NAMES = List.of(
            "minimal",
            "standard-invoice",
            "multiple-lines",
            "allowances",
            "charges",
            "self-billed",
            "credit-note",
            "b2c-gross",
            "extended",
            "extension-depth");

    private Examples() {
    }

    /**
     * Reads an example into a document.
     *
     * @param name the name of the example, without its extension
     * @return the document
     */
    static SemanticDocument document(String name) {
        return EsjReader.strict().read(pretty(name));
    }

    /**
     * Returns the pretty form of an example.
     *
     * @param name the name of the example, without its extension
     * @return the bytes of {@code examples/<name>.esj.json}
     */
    static byte[] pretty(String name) {
        return bytes("/examples/" + name + ".esj.json");
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

    /**
     * Returns a negative fixture.
     *
     * @param name the name of the fixture, without its extension
     * @return the bytes of {@code examples/invalid/<name>.esj.json}
     */
    static byte[] invalid(String name) {
        return bytes("/examples/invalid/" + name + ".esj.json");
    }

    /**
     * Returns the names of the negative fixtures that lie in the repository.
     *
     * @return the file names without their extension, sorted
     */
    static List<String> invalidNames() {
        URL url = Examples.class.getResource("/examples/invalid");
        if (url == null) {
            throw new IllegalStateException("the negative fixtures are not on the test classpath");
        }
        try (Stream<Path> files = Files.list(Path.of(url.toURI()))) {
            List<String> names = new ArrayList<>();
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".esj.json"))
                    .map(name -> name.substring(0, name.length() - ".esj.json".length()))
                    .sorted()
                    .forEach(names::add);
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the relative paths of the ESJ documents of the conformance corpus, sorted.
     *
     * @return the paths below {@code conformance/esj/}, for example
     *         {@code business-cases/standard/01.01a-INVOICE_ubl.xml.esj.json}
     */
    static List<String> conformanceDocuments() {
        URL url = Examples.class.getResource("/conformance/esj");
        if (url == null) {
            throw new IllegalStateException("the conformance corpus is not on the test classpath");
        }
        try {
            Path root = Path.of(url.toURI());
            try (Stream<Path> files = Files.walk(root)) {
                return files.filter(path -> path.getFileName().toString().endsWith(".esj.json"))
                        .map(path -> root.relativize(path).toString())
                        .sorted()
                        .toList();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns one ESJ document of the conformance corpus.
     *
     * @param relativePath the path below {@code conformance/esj/}
     * @return the bytes of the document
     */
    static byte[] conformanceDocument(String relativePath) {
        return bytes("/conformance/esj/" + relativePath);
    }

    /**
     * Returns a schema of the repository.
     *
     * @param fileName the file name inside {@code schema/}
     * @return the schema, decoded as UTF-8
     */
    static String schema(String fileName) {
        return new String(bytes("/schema/" + fileName), StandardCharsets.UTF_8);
    }

    /**
     * Returns the {@code $id} of a schema.
     *
     * @param schema the schema text
     * @return the value of its {@code $id} member
     */
    static String schemaIdentifier(String schema) {
        try (JsonParser parser = new JsonFactory().createParser(schema)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalStateException("a schema is a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                if ("$id".equals(field)) {
                    return parser.getText();
                }
                parser.skipChildren();
            }
            throw new IllegalStateException("the schema carries no $id");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] bytes(String resource) {
        try (InputStream in = Examples.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the test classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
