package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Reads the fixtures of this module from the classpath. The invoice instances of the
 * conformance corpus are not fixtures of this module; {@link Conformance} reads those.
 */
final class Instances {

    private Instances() {
        throw new AssertionError("no instances");
    }

    /** Returns a resource of this module as bytes. */
    static byte[] bytes(String resource) {
        try (InputStream in = Instances.class.getResourceAsStream(resource)) {
            assertNotNull(in, "the resource " + resource + " is on the test classpath");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns a resource of this module as text, with the trailing newline removed. */
    static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8).strip();
    }
}
