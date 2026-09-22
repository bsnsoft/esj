package de.bsnsoft.esj.rules.en16931;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.RuleEngine;

/** What the tests of the EN 16931 pack need in hand: the engine, and files of the repository. */
final class Pack {

    /** The pack compiled against the registry of the edition it is written for. */
    static final RuleEngine ENGINE = En16931.engine(Registry.en16931());

    private Pack() {
    }

    /**
     * Returns a file of the repository that the build copies onto the test class path.
     *
     * @param resource the resource path, absolute
     * @return its text, UTF-8
     */
    static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }

    /**
     * Returns the bytes of a file of the repository that the build copies onto the test
     * class path.
     *
     * @param resource the resource path, absolute
     * @return its bytes
     */
    static byte[] bytes(String resource) {
        try (InputStream in = Pack.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("this build carries no " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
