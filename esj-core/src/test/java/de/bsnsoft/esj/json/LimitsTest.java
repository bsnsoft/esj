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
import java.util.List;
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
        assertEquals(Limits.defaults(), Limits.defaults());
    }

    @Test
    void aWithMethodChangesOneBoundAndKeepsTheRest() {
        Limits changed = Limits.defaults().withMaxValues(7);
        assertEquals(7, changed.maxValues());
        assertEquals(Limits.defaults().maxDocumentBytes(), changed.maxDocumentBytes());
        assertNotEquals(Limits.defaults(), changed);
    }

    @Test
    void everyBoundCanBeSet() {
        Limits limits = Limits.defaults()
                .withMaxDocumentBytes(1)
                .withMaxValues(2)
                .withMaxValueMembers(3)
                .withMaxPathSegments(4)
                .withMaxPathBytes(5)
                .withMaxStringBytes(6)
                .withMaxBinaryValueBytes(7)
                .withMaxTotalBinaryBytes(8)
                .withMaxExtensionDepth(9)
                .withMaxExtensionNodes(10);
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), List.of(
                limits.maxDocumentBytes(), (long) limits.maxValues(),
                (long) limits.maxValueMembers(), (long) limits.maxPathSegments(),
                (long) limits.maxPathBytes(), limits.maxStringBytes(),
                limits.maxBinaryValueBytes(), limits.maxTotalBinaryBytes(),
                (long) limits.maxExtensionDepth(), (long) limits.maxExtensionNodes()));
        assertEquals(limits, Limits.defaults().withMaxDocumentBytes(1).withMaxValues(2)
                .withMaxValueMembers(3).withMaxPathSegments(4).withMaxPathBytes(5)
                .withMaxStringBytes(6).withMaxBinaryValueBytes(7).withMaxTotalBinaryBytes(8)
                .withMaxExtensionDepth(9).withMaxExtensionNodes(10), "limits are values");
    }

    @Test
    void aBoundThatIsNotPositiveIsRefused() {
        assertThrows(EsjFormatException.class, () -> Limits.defaults().withMaxValues(0));
        assertThrows(EsjFormatException.class,
                () -> Limits.defaults().withMaxValueMembers(0));
        assertThrows(EsjFormatException.class, () -> Limits.defaults().withMaxDocumentBytes(-1));
        assertThrows(EsjFormatException.class,
                () -> Limits.defaults().withMaxExtensionDepth(-3));
        assertThrows(EsjFormatException.class,
                () -> Limits.defaults().withMaxExtensionNodes(0));
    }

    @Test
    void aDocumentBoundLargerThanAByteArrayIsRefused() {
        assertThrows(EsjFormatException.class,
                () -> Limits.defaults().withMaxDocumentBytes(Long.MAX_VALUE));
        assertThrows(EsjFormatException.class,
                () -> Limits.defaults().withMaxDocumentBytes(Limits.MAX_DOCUMENT_BYTES + 1));
        assertEquals(Limits.MAX_DOCUMENT_BYTES,
                Limits.defaults().withMaxDocumentBytes(Limits.MAX_DOCUMENT_BYTES)
                        .maxDocumentBytes());
    }

    /**
     * The twin of the document bound. The depth of the whole document is the
     * extension-depth bound enlarged by the nesting of the envelope, so the top of the
     * {@code int} range is a configuration no reader can count to, and one that would wrap
     * around to a negative depth. The bound is refused where it is given, and the largest
     * value it accepts is still accepted (specification, section 12.2).
     */
    @Test
    void anExtensionDepthLargerThanTheDocumentCanBeCountedToIsRefused() {
        assertThrows(EsjFormatException.class,
                () -> Limits.defaults().withMaxExtensionDepth(Integer.MAX_VALUE));
        assertThrows(EsjFormatException.class,
                () -> Limits.defaults().withMaxExtensionDepth(Limits.MAX_EXTENSION_DEPTH + 1));
        assertEquals(Limits.MAX_EXTENSION_DEPTH,
                Limits.defaults().withMaxExtensionDepth(Limits.MAX_EXTENSION_DEPTH)
                        .maxExtensionDepth());
    }

    /**
     * A reader built with the largest extension depth there is reads an ordinary document:
     * nothing the reader derives from the bound wrapped around.
     */
    @Test
    @Timeout(value = 30)
    void aReaderIsBuiltWithTheLargestExtensionDepthThereIs() {
        Limits limits = Limits.defaults().withMaxExtensionDepth(Limits.MAX_EXTENSION_DEPTH);
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
            Limits limits = Limits.defaults().withMaxDocumentBytes(bound);
            SemanticDocument document = EsjReader.withLimits(limits).read(
                    new ByteArrayInputStream(DOCUMENT.getBytes(StandardCharsets.UTF_8)));
            assertEquals(1, document.values().size(), Long.toString(bound));
        }
    }

    @Test
    void aStreamLongerThanTheBoundIsRefused() {
        Limits limits = Limits.defaults().withMaxDocumentBytes(DOCUMENT.length() - 1L);
        assertThrows(EsjLimitException.class, () -> EsjReader.withLimits(limits).read(
                new ByteArrayInputStream(DOCUMENT.getBytes(StandardCharsets.UTF_8))));
    }
}
