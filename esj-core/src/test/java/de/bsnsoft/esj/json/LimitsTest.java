package de.bsnsoft.esj.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.SemanticDocument;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Checks the reader limits of the specification, section 12.2. */
class LimitsTest {

    private static final String DOCUMENT = "{\"format\":\"EN16931-Semantic-JSON\","
            + "\"version\":\"0.1\",\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\","
            + "\"values\":{\"/BT-1\":\"RE-1\"}}";

    @Test
    void theDefaultsAreTheTableOfTheSpecification() {
        Limits defaults = Limits.defaults();
        assertEquals(67_108_864L, defaults.maxDocumentBytes());
        assertEquals(100_000, defaults.maxValues());
        assertEquals(16, defaults.maxPathSegments());
        assertEquals(256, defaults.maxPathBytes());
        assertEquals(1_048_576L, defaults.maxStringBytes());
        assertEquals(33_554_432L, defaults.maxBinaryValueBytes());
        assertEquals(50_331_648L, defaults.maxTotalBinaryBytes());
        assertEquals(32, defaults.maxExtensionDepth());
        assertEquals(100_000, defaults.maxExtensionNodes());
    }

    @Test
    void theDefaultsAreOneSharedValue() {
        assertSame(Limits.defaults(), Limits.defaults());
        assertEquals(Limits.defaults(), Limits.builder().build());
    }

    @Test
    void aBuilderChangesOneBoundAndKeepsTheRest() {
        Limits changed = Limits.defaults().toBuilder().maxValues(7).build();
        assertEquals(7, changed.maxValues());
        assertEquals(Limits.defaults().maxDocumentBytes(), changed.maxDocumentBytes());
        assertNotEquals(Limits.defaults(), changed);
    }

    @Test
    void everyBoundCanBeSet() {
        Limits limits = Limits.builder()
                .maxDocumentBytes(1)
                .maxValues(2)
                .maxValueMembers(3)
                .maxPathSegments(4)
                .maxPathBytes(5)
                .maxStringBytes(6)
                .maxBinaryValueBytes(7)
                .maxTotalBinaryBytes(8)
                .maxExtensionDepth(9)
                .maxExtensionNodes(10)
                .build();
        assertEquals(new Limits(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), limits);
    }

    @Test
    void aBoundThatIsNotPositiveIsRefused() {
        assertThrows(EsjFormatException.class, () -> Limits.builder().maxValues(0).build());
        assertThrows(EsjFormatException.class,
                () -> Limits.builder().maxValueMembers(0).build());
        assertThrows(EsjFormatException.class, () -> Limits.builder().maxDocumentBytes(-1).build());
        assertThrows(EsjFormatException.class,
                () -> Limits.builder().maxExtensionDepth(-3).build());
        assertThrows(EsjFormatException.class,
                () -> Limits.builder().maxExtensionNodes(0).build());
    }

    @Test
    void aDocumentBoundLargerThanAByteArrayIsRefused() {
        assertThrows(EsjFormatException.class,
                () -> Limits.builder().maxDocumentBytes(Long.MAX_VALUE).build());
        assertThrows(EsjFormatException.class,
                () -> Limits.builder().maxDocumentBytes(Limits.MAX_DOCUMENT_BYTES + 1).build());
        assertEquals(Limits.MAX_DOCUMENT_BYTES,
                Limits.builder().maxDocumentBytes(Limits.MAX_DOCUMENT_BYTES).build()
                        .maxDocumentBytes());
    }

    /**
     * The twin of the document bound. A reader enlarges the extension-depth bound by the
     * nesting of the envelope before it configures its parser with it, so the top of the
     * {@code int} range is a configuration no reader can enforce and one that a parser
     * would answer with an exception of its own about a negative depth. The bound is
     * refused where it is given, and the largest value it accepts is still accepted.
     */
    @Test
    void anExtensionDepthLargerThanAParserCanBeGivenIsRefused() {
        assertThrows(EsjFormatException.class,
                () -> Limits.builder().maxExtensionDepth(Integer.MAX_VALUE).build());
        assertThrows(EsjFormatException.class,
                () -> Limits.builder().maxExtensionDepth(Limits.MAX_EXTENSION_DEPTH + 1).build());
        assertEquals(Limits.MAX_EXTENSION_DEPTH,
                Limits.builder().maxExtensionDepth(Limits.MAX_EXTENSION_DEPTH).build()
                        .maxExtensionDepth());
    }

    /**
     * A reader built with the largest extension depth there is reads an ordinary document.
     * The bound reaches the parser as a sum, and this is what says the sum is one a parser
     * accepts rather than one that wrapped.
     */
    @Test
    @Timeout(value = 30)
    void aReaderIsBuiltWithTheLargestExtensionDepthThereIs() {
        Limits limits = Limits.builder().maxExtensionDepth(Limits.MAX_EXTENSION_DEPTH).build();
        SemanticDocument document = EsjReader.withLimits(limits)
                .read(DOCUMENT.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, document.values().size());
    }

    /**
     * The large end of the document bound is where the buffer arithmetic of
     * {@code EsjReader.drain} lives. A bound of a whole gibibyte and the largest bound
     * there is both have to read a small stream and return, rather than spin on a buffer
     * that cannot grow.
     */
    @Test
    @Timeout(value = 30)
    void aStreamIsReadWithTheLargestBoundsThereAre() {
        for (long bound : new long[] {
                1L << 30, Limits.MAX_DOCUMENT_BYTES - 1, Limits.MAX_DOCUMENT_BYTES}) {
            Limits limits = Limits.builder().maxDocumentBytes(bound).build();
            SemanticDocument document = EsjReader.withLimits(limits).read(
                    new ByteArrayInputStream(DOCUMENT.getBytes(StandardCharsets.UTF_8)));
            assertEquals(1, document.values().size(), Long.toString(bound));
        }
    }

    @Test
    void aStreamLongerThanTheBoundIsRefused() {
        Limits limits = Limits.builder().maxDocumentBytes(DOCUMENT.length() - 1L).build();
        assertThrows(EsjLimitException.class, () -> EsjReader.withLimits(limits).read(
                new ByteArrayInputStream(DOCUMENT.getBytes(StandardCharsets.UTF_8))));
    }
}
