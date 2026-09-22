package de.bsnsoft.esj.json;

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

/** The example documents of the repository, read from the test classpath. */
final class Examples {

    /**
     * The example documents of the default edition, in the order of the table in
     * {@code examples/README.md}. The example of the 2026 edition is not one of them: it
     * is read where it is measured against the registry of its own edition, and a build
     * made without that edition does not carry it.
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
     * Returns the format schema of the repository.
     *
     * @return an open stream on {@code schema/esj.schema.json}
     */
    static InputStream schema() {
        InputStream in = Examples.class.getResourceAsStream("/schema/esj.schema.json");
        if (in == null) {
            throw new IllegalStateException("the format schema is not on the test classpath");
        }
        return in;
    }

    /**
     * Returns a resource as text.
     *
     * @param resource the absolute classpath name
     * @return the content, decoded as UTF-8
     */
    static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8);
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
