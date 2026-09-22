package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.cos.COSStream;
import org.junit.jupiter.api.Test;

/**
 * The budget under the streams the library decodes for itself.
 *
 * <p>An object stream and a cross-reference stream are decoded by the parser while it
 * opens a file, into a buffer that grows until the stream ends, so a container of half a
 * megabyte ends the process at any heap unless the bytes are counted as they are made.
 * These tests run in the {@code -Xmx256m} the surefire plugin of this module sets and
 * every fixture carries half a gibibyte of Flate: a file that reached the library
 * unmeasured would end this process rather than fail an assertion.
 *
 * <p>Each fixture is a place where a reading of the file with a scan of this module's own
 * and the library's own lexer can part — a comment between a name and its value, a string
 * holding the bracket pair that ends a dictionary, the {@code endstream} keyword inside
 * the compressed data, a length written as a reference, padding wider than any window.
 * None of them can part here, because the dictionary and the extent both come from the
 * library.
 */
class DecodeBudgetTest {

    /** One invoice of the conformance corpus, as an ordinary container carries it. */
    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";

    /** How much the hostile fixtures decode to: more than the heap of this run. */
    private static final long BOMB = 512L * 1024 * 1024;

    @Test
    void anObjectStreamThatInflatesWithoutEndIsRefusedBeforeTheLibraryDecodesIt() {
        assertRefused(Pdfs.objectStreamBomb(BOMB), "an object stream");
    }

    @Test
    void aCrossReferenceStreamThatInflatesWithoutEndIsRefusedTheSameWay() {
        assertRefused(Pdfs.crossReferenceStreamBomb(BOMB), "a cross-reference stream");
    }

    @Test
    void aCommentBetweenTheTypeAndItsValueChangesNothing() {
        assertRefused(Pdfs.objectStreamBomb(bomb(), "/Type\n% a comment\n/ObjStm", ""),
                "an object stream");
    }

    @Test
    void aCommentBetweenTheDictionaryAndTheStreamKeywordChangesNothing() {
        assertRefused(Pdfs.objectStreamBomb(bomb(), "/Type /ObjStm", "\n% a comment"),
                "an object stream");
    }

    @Test
    void aStringHoldingTheBracketPairThatEndsADictionaryChangesNothing() {
        assertRefused(Pdfs.objectStreamBomb(bomb(), "/Junk (>>) /Type /ObjStm", ""),
                "an object stream");
    }

    @Test
    void aLengthWrittenAsAReferenceChangesNothing() {
        byte[] payload = bomb();
        assertRefused(Pdfs.objectStreamBomb(payload, "/Type /ObjStm", "", "/Length 11 0 R",
                "11 0 obj\n" + payload.length + "\nendobj\n"), "an object stream");
    }

    @Test
    void theEndstreamKeywordInsideTheCompressedDataChangesNothing() {
        byte[] payload = Pdfs.deflatedWithEndstreamInside(BOMB);

        assertRefused(Pdfs.objectStreamBomb(payload, "/Type /ObjStm", "", "/Length 11 0 R",
                "11 0 obj\n" + payload.length + "\nendobj\n"), "an object stream");
    }

    @Test
    void aCrossReferenceStreamWithAPredictorIsMeasuredThroughIt() {
        assertRefused(Pdfs.crossReferenceStreamBomb(bomb(),
                " /DecodeParms << /Predictor 12 /Columns 7 >>"), "a cross-reference stream");
    }

    @Test
    void aDictionaryWiderThanAnyWindowIsNotAWayPast() {
        // The three shapes that walked past a scan with a window of its own: a string, a
        // comment and padding, each of them wider than any window such a scan can afford.
        // Whether the library indexes the stream at all is its decision; what is asserted
        // here is that the file does not end this process, which is what it did when the
        // bound was a scan that had given up on the dictionary.
        assertHeldWithoutDecoding(Pdfs.objectStreamBomb(bomb(),
                "/Junk (" + "x".repeat(70000) + ") /Type /ObjStm", ""));
        assertHeldWithoutDecoding(Pdfs.objectStreamBomb(bomb(),
                "% " + "x".repeat(70000) + "\n/Type /ObjStm", ""));
        assertHeldWithoutDecoding(Pdfs.objectStreamBomb(bomb(), "/Type /ObjStm", "",
                "/Length " + bomb().length + " /Junk (" + "y".repeat(70000) + ")", ""));
    }

    @Test
    void aStructuralStreamInsideTheBoundIsLeftAlone() {
        byte[] pdf = Pdfs.objectStreamBomb(Pdfs.deflatedZeros(4096), "/Type /ObjStm", "");

        try (PdfContainer container = assertDoesNotThrow(() -> PdfContainer.open(pdf))) {
            assertEquals(0, container.embeddedFiles().size(),
                    "the file carries no attachment, and was opened all the same");
        }
    }

    @Test
    void anObjectStreamDeclaringAPredictorRowIsRefusedBeforeTheChainIsRun() {
        // A structural stream that carries a predictor at all is the cross-reference
        // stream, and its row is a tag byte and the three widths of /W. This one declares
        // a row of half a gibibyte, which the library allocates twice from the dictionary
        // before either decoder writes a byte — so it is refused from the dictionary.
        assertRefusedWith(Pdfs.objectStreamWith("/Type /ObjStm /N 1 /First 8"
                + " /Filter /FlateDecode /DecodeParms << /Predictor 12 /Colors 1"
                + " /BitsPerComponent 8 /Columns 268435455 >> /Length 8",
                new byte[8]), "predictor row");
    }

    @Test
    void anObjectStreamWhosePredictorOverflowsTheLibrarysIntIsRefusedAsMalformed() {
        // The measure path, which is where the library decodes a stream for itself: a row
        // that is negative in sixty-four bits and 268 435 455 in the thirty-two the
        // library uses. The values are refused before anything is multiplied.
        byte[] pdf = Pdfs.objectStreamWith("/Type /ObjStm /N 1 /First 8"
                + " /Filter /FlateDecode /DecodeParms << /Predictor 12 /Colors 8"
                + " /BitsPerComponent 15790321 /Columns -17 >> /Length 8", new byte[8]);

        PdfFormatException refused = assertThrows(PdfFormatException.class,
                () -> PdfContainer.open(pdf));

        assertTrue(refused.getMessage().contains("no predictor stage this format defines"),
                refused.getMessage());
    }

    @Test
    void aCrossReferenceStreamDeclaringAPredictorRowIsRefusedTheSameWay() {
        assertRefusedWith(Pdfs.crossReferenceStreamBomb(new byte[8],
                " /DecodeParms << /Predictor 12 /Colors 1 /BitsPerComponent 8"
                        + " /Columns 268435455 >>"), "predictor row");
    }

    @Test
    void aStructuralStreamWhoseChainThisReaderDoesNotRunIsRefusedRatherThanHandedOver() {
        // Twenty-seven bytes of JPEG declaring twenty thousand by twenty thousand samples.
        // Measuring it is not possible here, and handing it to the library unmeasured is
        // four hundred million pixels; the object stream of a hybrid invoice is deflated,
        // so the chain is the answer and nothing is lost by refusing it.
        byte[] raster = {
            (byte) 0xFF, (byte) 0xD8,
            (byte) 0xFF, (byte) 0xC0, 0x00, 0x11, 0x08, 0x4E, 0x20, 0x4E, 0x20,
            0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
            (byte) 0xFF, (byte) 0xDA, 0x00, 0x08, 0x01, 0x01};

        assertRefusedWith(Pdfs.objectStreamWith("/Type /ObjStm /N 1 /First 8"
                + " /Filter /DCTDecode /Length " + raster.length, raster),
                "a chain this reader does not decode");
    }

    @Test
    void anObjectStreamDeclaringMoreObjectsThanAContainerMayIsRefused() {
        // The bytes of an object stream bound the bytes it decodes to and nothing else:
        // an empty string costs a few bytes there and a few dozen once the library has
        // built it. The count stands in the dictionary, beside everything else that is
        // read at the moment the stream is measured.
        assertRefusedWith(Pdfs.objectStreamWith("/Type /ObjStm /N 3000000 /First 8"
                + " /Filter /FlateDecode /Length " + Pdfs.deflatedZeros(4096).length,
                Pdfs.deflatedZeros(4096)), "declares 3000000 objects");
    }

    @Test
    void aStreamReachedWhileThisReaderIsDecodingAnotherIsMeasuredAllTheSame() {
        // The /Filter of the attachment is written as an indirect reference to an object
        // that lives inside an object stream carrying half a gibibyte of Flate. Resolving
        // it is what makes the library decode that object stream, and it is resolved in
        // the one window in which this module is busy with a stream of its own. A
        // suspension that named the container rather than the stream would let the
        // object stream through unmeasured, which at this heap is this process.
        byte[] pdf = Pdfs.indirectFilterInObjectStream(
                Pdfs.deflated("<?xml version=\"1.0\"?><a/>".getBytes(StandardCharsets.UTF_8)),
                bomb());

        PdfLimitException refused = assertThrows(PdfLimitException.class,
                () -> read(pdf, PdfLimits.defaults()));

        assertTrue(refused.getMessage().startsWith("an object stream"),
                "the object stream was measured, not decoded: " + refused.getMessage());
    }

    @Test
    void aSuspensionNamesOneStreamAndNotTheContainer() {
        DecodeBudget budget = new DecodeBudget(64, 64);
        COSStream one = new COSStream();
        COSStream other = new COSStream();

        budget.suspend(one);

        assertTrue(budget.suspendedFor(one));
        assertFalse(budget.suspendedFor(other),
                "every other stream of the file is measured while this one is decoded");

        budget.resume(one);

        assertFalse(budget.suspendedFor(one));
    }

    @Test
    void theBudgetCountsWhatThisReaderDecodesOutOfAContainerAsWell() {
        byte[] pdf = Pdfs.facturX(Conformance.instance(CII));

        PdfLimitException refused = assertThrows(PdfLimitException.class,
                () -> read(pdf, PdfLimits.defaults().withMaxDecodedBytes(64)));

        assertTrue(refused.getMessage().contains("decodes to more than the 64 bytes"),
                "the budget is one number for the whole container: " + refused.getMessage());
    }

    /** Reads what a container carries, which is what makes it decode anything. */
    private static void read(byte[] pdf, PdfLimits limits) {
        try (PdfContainer container = PdfContainer.open(pdf, limits)) {
            container.xmpPacket();
            container.embeddedFiles().forEach(EmbeddedFile::content);
        }
    }

    private static void assertRefused(byte[] pdf, String what) {
        PdfLimitException refused = assertThrows(PdfLimitException.class,
                () -> PdfContainer.open(pdf));

        assertTrue(refused.getMessage().startsWith(what),
                "the refusal names what was too large: " + refused.getMessage());
    }

    /**
     * Asserts that a file the library does not decode the hostile stream of is opened, or
     * refused, without the stream ever being decoded — which at this heap is the whole of
     * the assertion.
     */
    private static void assertHeldWithoutDecoding(byte[] pdf) {
        try (PdfContainer container = PdfContainer.open(pdf)) {
            assertEquals(0, container.embeddedFiles().size(),
                    "nothing was read out of the file");
        } catch (PdfException refused) {
            assertTrue(refused instanceof PdfLimitException || refused instanceof PdfFormatException,
                    "a file this reader cannot read is refused, not decoded: "
                            + refused.getMessage());
        }
    }

    /** Asserts that a file is refused by a bound, in the words the refusal is about. */
    private static void assertRefusedWith(byte[] pdf, String phrase) {
        PdfLimitException refused = assertThrows(PdfLimitException.class,
                () -> PdfContainer.open(pdf));

        assertTrue(refused.getMessage().contains(phrase),
                "the refusal says which bound was met: " + refused.getMessage());
    }

    private static byte[] bomb() {
        return Pdfs.deflatedZeros(BOMB);
    }
}
