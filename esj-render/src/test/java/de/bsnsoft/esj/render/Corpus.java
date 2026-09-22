package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The documents the tests of this module render: the invoice instances of the conformance
 * corpus of the repository and the examples beside it.
 *
 * <p>The corpus is enumerated the way {@code esj-xr} enumerates it, out of the checksum
 * block of {@code conformance/kosit/README.md}, so that both modules render the same list
 * and neither can drift from the attribution that list is part of.
 */
final class Corpus {

    /**
     * The examples the baseline rendering shows whole, in the order of
     * {@code examples/README.md}. {@code b2c-gross} is not among them: its four terms come
     * from an extension registry, the XR representation the stylesheets read has no element
     * for them, and the baseline rendering shows no extension
     * ({@code HtmlRendererTest} measures that, and {@code docs/rendering.md} states it).
     */
    static final List<String> EXAMPLES = List.of(
            "minimal",
            "standard-invoice",
            "multiple-lines",
            "allowances",
            "charges",
            "self-billed",
            "credit-note",
            "extended",
            "extension-depth");

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

    /** Reads one instance of the corpus into an ESJ document. */
    static SemanticDocument instance(String relativePath) {
        return new XrImporter().importXml(bytes("/conformance/kosit/" + relativePath));
    }

    /** Reads one example of the repository. */
    static SemanticDocument example(String name) {
        return EsjReader.strict().read(bytes("/examples/" + name + ".esj.json"));
    }

    /** Returns the SHA-256 of some bytes in lowercase hexadecimal. */
    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime implements SHA-256", e);
        }
    }

    /**
     * Returns the NOTICE of this repository, as the jar of this module carries it.
     *
     * <p>It is looked for by its first line rather than taken as the first resource named
     * {@code META-INF/NOTICE}: several jars on a test classpath carry a file of that name,
     * the build tool's own among them, and which of them comes first is not this project's
     * business.
     */
    static String notice() {
        try {
            for (URL url : Collections.list(
                    Corpus.class.getClassLoader().getResources("META-INF/NOTICE"))) {
                try (InputStream in = url.openStream()) {
                    String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    if (text.startsWith("EN16931 Semantic JSON (ESJ)")) {
                        return text;
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new IllegalStateException("the NOTICE of this repository is not on the classpath");
    }

    /** Returns a resource of the test classpath as bytes. */
    static byte[] bytes(String resource) {
        try (InputStream in = Corpus.class.getResourceAsStream(resource)) {
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
