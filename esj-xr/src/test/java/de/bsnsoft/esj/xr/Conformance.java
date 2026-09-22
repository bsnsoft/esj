package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the conformance corpus of the repository from the classpath, where the build of
 * this module copies it to.
 *
 * <p>The corpus is not enumerated from a directory of the file system and not from a list
 * written beside it. It is enumerated from the checksum block of
 * {@code conformance/kosit/README.md}: a file that is not listed there with its digest is
 * not part of the corpus, and a file whose bytes no longer match its digest fails before
 * anything is imported. The attribution and the enumeration are then the same list, and
 * neither can drift away from the other.
 */
final class Conformance {

    /** The root of the corpus on the test classpath. */
    static final String ROOT = "/conformance/";

    /** One line of the checksum block: a lowercase digest, two spaces, a relative path. */
    private static final Pattern CHECKSUM = Pattern.compile("^([0-9a-f]{64})  (\\S.*)$");

    private Conformance() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the instances of the corpus with their recorded digests, in the order
     * {@code conformance/kosit/README.md} lists them.
     */
    static Map<String, String> instances() {
        Map<String, String> instances = new LinkedHashMap<>();
        for (String line : text(ROOT + "kosit/README.md").split("\\R")) {
            Matcher matcher = CHECKSUM.matcher(line);
            if (matcher.matches()) {
                instances.put(matcher.group(2), matcher.group(1));
            }
        }
        if (instances.isEmpty()) {
            throw new IllegalStateException("conformance/kosit/README.md lists no instance");
        }
        return instances;
    }

    /** Returns the relative paths of the instances, for a parameterized test. */
    static List<String> corpus() {
        return new ArrayList<>(instances().keySet());
    }

    /** Returns the bytes of one instance of the corpus. */
    static byte[] instance(String relativePath) {
        return bytes(ROOT + "kosit/" + relativePath);
    }

    /** Returns the bytes of the ESJ document checked in for one instance, in pretty form. */
    static byte[] esj(String relativePath) {
        return bytes(ROOT + "esj/" + relativePath + ".esj.json");
    }

    /** Returns the text of one file of the ledger. */
    static String ledger(String name) {
        return text(ROOT + "ledger/" + name);
    }

    /** Returns the SHA-256 of some bytes in lowercase hexadecimal. */
    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime implements SHA-256", e);
        }
    }

    /** Returns a resource of the test classpath as bytes. */
    static byte[] bytes(String resource) {
        try (InputStream in = Conformance.class.getResourceAsStream(resource)) {
            assertNotNull(in, "the resource " + resource + " is on the test classpath");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns a resource of the test classpath as text. */
    static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }
}
