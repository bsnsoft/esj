package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the conformance corpus of the repository from the test class path.
 *
 * <p>The corpus is enumerated from the checksum block of
 * {@code conformance/kosit/README.md}, as it is in {@code esj-xr}: the attribution and the
 * enumeration are then the same list, and a file that is not attributed is not a file the
 * tests run.
 */
final class Corpus {

    /** One line of the checksum block: a lowercase digest, two spaces, a relative path. */
    private static final Pattern CHECKSUM = Pattern.compile("^([0-9a-f]{64})  (\\S.*)$");

    private Corpus() {
        throw new AssertionError("no instances");
    }

    /** Returns the relative paths of the instances, for a parameterized test. */
    static List<String> instances() {
        List<String> instances = new ArrayList<>();
        for (String line : text("/conformance/kosit/README.md").split("\\R")) {
            Matcher matcher = CHECKSUM.matcher(line);
            if (matcher.matches()) {
                instances.add(matcher.group(2));
            }
        }
        if (instances.isEmpty()) {
            throw new IllegalStateException("conformance/kosit/README.md lists no instance");
        }
        return instances;
    }

    /** Returns the bytes of one instance of the corpus. */
    static byte[] instance(String relativePath) {
        return bytes("/conformance/kosit/" + relativePath);
    }

    /** Returns a resource of the test class path as bytes. */
    static byte[] bytes(String resource) {
        try (InputStream in = Corpus.class.getResourceAsStream(resource)) {
            assertNotNull(in, "the resource " + resource + " is on the test class path");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns a resource of the test class path as text. */
    static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }
}
