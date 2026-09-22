package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.bsnsoft.esj.model.Registry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * The templates and the extension registry the tests of this module render with.
 *
 * <p>The example templates of {@code examples/templates/} are the ones a reader of the
 * repository sees, so they are the ones the tests use: a template that is broken is a
 * failing build rather than a surprise for whoever copies it.
 *
 * <p>{@link #withB2c()} is the core registry with the B2C extension of {@code model/b2c/}
 * loaded beside it, which is the registry {@code esj render --extension b2c} works
 * against. The layout does not need it — a template carries the identifier, the place, the
 * label and the data type of every term it gives a place to — and the tests that render
 * without it say so.
 */
final class Templates {

    /** Where the example templates sit on the test classpath. */
    private static final String DIRECTORY = "/examples/templates/";

    private Templates() {
        throw new AssertionError("no instances");
    }

    /** Returns the directory the example templates were copied into for the tests. */
    static Path directory() {
        URL url = Templates.class.getResource(DIRECTORY);
        assertNotNull(url, "the example templates are on the test classpath");
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads an example template of the repository the way a caller of the library reads
     * one: from its file, with the files it names beside it.
     *
     * @param file the name of the template file
     * @return the template
     */
    static RenderTemplate example(String file) {
        return RenderTemplate.read(directory().resolve(file));
    }

    /**
     * Reads a template written in the test itself, whose references are answered out of
     * the example directory.
     *
     * @param json the template
     * @return the template
     */
    static RenderTemplate of(String json) {
        return RenderTemplate.of(json.getBytes(StandardCharsets.UTF_8), Templates::beside);
    }

    /**
     * Reads a template written in the test itself that refers to one file the test drew,
     * with every other reference answered out of the example directory.
     *
     * <p>The busy letterhead of {@link Artwork#busyLetterhead()} is such a file: it is a
     * test fixture rather than an example of the repository, because what it is for is to
     * be in the way.
     *
     * @param json      the template
     * @param reference the name the template refers to that file by
     * @param file      the file
     * @return the template
     */
    static RenderTemplate of(String json, String reference, byte[] file) {
        return RenderTemplate.of(json.getBytes(StandardCharsets.UTF_8),
                name -> reference.equals(name) ? file : beside(name));
    }

    /** Returns the core registry with the B2C extension registry loaded beside it. */
    static Registry withB2c() {
        return Registry.en16931().withExtension(Registry.b2cExtension());
    }

    /** Answers a reference out of the example directory, or with nothing where there is none. */
    private static byte[] beside(String reference) {
        try (InputStream in = Templates.class.getResourceAsStream(DIRECTORY + reference)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
