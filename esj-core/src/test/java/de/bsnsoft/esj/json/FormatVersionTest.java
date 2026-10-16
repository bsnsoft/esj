package de.bsnsoft.esj.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.Esj;
import de.bsnsoft.esj.SemanticDocument;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The documents of format version 0.1 that {@code conformance/fixtures/version-0.1/} keeps:
 * a reader of format 1.0 reads them, and writes them in the version they carry, so that their
 * canonical bytes are byte for byte the ones checked in beside them while the format was
 * 0.1, and their digests are the ones they had (specification, sections 4.3, 7.3 and 8.3).
 *
 * <p>The canonical twins were written by the release of this project that wrote format 0.1,
 * before the format became 1.0, and are not regenerated: they are the record of what a stored
 * 0.1 document hashes to.
 */
class FormatVersionTest {

    private static final String DIRECTORY = "/conformance/fixtures/version-0.1/";

    private final EsjReader reader = EsjReader.strict();

    @ParameterizedTest
    @ValueSource(strings = {"minimal", "extended", "01.01a-INVOICE_ubl.xml"})
    void aDocumentOfVersionZeroPointOneKeepsItsCanonicalBytesAndItsDigests(String name) {
        byte[] canonical = bytes(DIRECTORY + name + ".canonical.esj.json");

        SemanticDocument document = reader.read(bytes(DIRECTORY + name + ".esj.json"));

        assertEquals("0.1", document.formatVersion());
        assertArrayEquals(canonical, Canonicalizer.canonicalBytes(document),
                name + ": the canonical bytes are the ones of format 0.1");
        assertTrue(new String(canonical, StandardCharsets.UTF_8)
                .startsWith("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","));
        assertEquals(sha256(canonical), Canonicalizer.documentDigest(document));
        assertArrayEquals(canonical, Canonicalizer.canonicalize(canonical),
                "canonicalizing the canonical bytes again changes nothing");
    }

    /** The corpus document was written in pretty form by the streaming reader, as it is still. */
    @ParameterizedTest
    @ValueSource(strings = {"01.01a-INVOICE_ubl.xml"})
    void aPrettyDocumentOfVersionZeroPointOneIsWrittenOutAsItWasRead(String name) {
        byte[] pretty = bytes(DIRECTORY + name + ".esj.json");

        assertArrayEquals(pretty, EsjWriter.pretty().toBytes(reader.read(pretty)));
    }

    /**
     * The same values as a document of version 0.1 and of version 1.0: one semantic digest,
     * two document digests, because the document digest covers {@code version} and the
     * semantic digest does not (specification, sections 8.2 and 8.3).
     */
    @ParameterizedTest
    @ValueSource(strings = {"minimal", "extended"})
    void theSameValuesInTwoVersionsShareTheSemanticDigestAndNotTheDocumentDigest(String name) {
        SemanticDocument earlier = reader.read(bytes(DIRECTORY + name + ".esj.json"));
        SemanticDocument current = reader.read(Examples.pretty(name));

        assertEquals("0.1", earlier.formatVersion());
        assertEquals(Esj.formatVersion(), current.formatVersion());
        assertEquals(earlier.values(), current.values());
        assertEquals(Canonicalizer.semanticDigest(earlier), Canonicalizer.semanticDigest(current));
        assertNotEquals(Canonicalizer.documentDigest(earlier),
                Canonicalizer.documentDigest(current));
        assertEquals(new String(Examples.canonical(name), StandardCharsets.UTF_8),
                new String(Canonicalizer.canonicalBytes(earlier), StandardCharsets.UTF_8)
                        .replace("\"version\":\"0.1\"", "\"version\":\"1.0\""),
                "the two canonical forms differ in the version and nowhere else");
    }

    private static byte[] bytes(String resource) {
        try (InputStream in = FormatVersionTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the test classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
