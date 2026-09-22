package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.Test;

/**
 * What a stream that inflates without end costs.
 *
 * <p>Every fixture here decodes to half a gibibyte of nothing out of a few kilobytes of
 * Flate, which is more than the heap this module's tests run in — see the {@code argLine}
 * of the surefire plugin in {@code esj-pdf/pom.xml}. That is the whole point: a decoder
 * that holds the decoded bytes before it applies a bound ends this process, and these
 * tests then fail rather than pass quietly. A bound that holds while the bytes are being
 * produced costs what the bound says and no more.
 */
class BoundedStreamTest {

    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";

    /** How much the fixtures of this class decode to: more than the heap of this run. */
    private static final long BOMB = 512L * 1024 * 1024;

    @Test
    void anAttachmentThatInflatesWithoutEndIsCutOffAtTheBound() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.bomb("bomb.bin", BOMB))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            EmbeddedFile file = container.embeddedFiles().get(0);

            assertEquals(1024, file.head(1024).length,
                    "a window costs the window and not what the stream would produce");

            AttachmentContent content = file.content();
            assertTrue(content.truncated(), "the stream carries more than the bound");
            assertEquals(container.limits().maxAttachmentBytes(), content.length());
        }
    }

    @Test
    void anAttachmentThatInflatesWithoutEndDoesNotStopTheInvoiceBesideIt() {
        // The classification decodes a window of every attachment, so a stream nobody
        // chose can end a run that was about another one. Anyone who can add a file to an
        // invoice PDF would then be able to deny its recipient the verdict.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .attach(Pdfs.Attachment.bomb("logo.png", BOMB))
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);

            assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
            assertEquals(List.of(AttachmentKind.CII_INVOICE, AttachmentKind.NOT_XML),
                    located.all().stream().map(LocatedAttachment::kind).toList());
        }
    }

    @Test
    void aDecodeThatFailedGivesBackWhatItReserved() {
        // The bound on all the attachments together counts the bytes that were decoded.
        // A decode that ended in an exception produced none, and a reservation left
        // standing would make the next attachment pay for it.
        byte[] invoice = Conformance.instance(CII);
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.filtered("logo.jpg",
                        new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xC0},
                        COSName.DCT_DECODE, "Supplement"))
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, invoice))
                .build();
        PdfLimits limits = PdfLimits.defaults()
                .withMaxAttachmentBytes(invoice.length)
                .withMaxTotalAttachmentBytes(invoice.length);

        try (PdfContainer container = PdfContainer.open(pdf, limits)) {
            assertThrows(PdfFormatException.class,
                    () -> container.embeddedFiles().get(0).content());

            AttachmentContent read = container.embeddedFiles().get(1).content();

            assertEquals(invoice.length, read.length());
            assertFalse(read.truncated(), "the reservation of the failed decode was given"
                    + " back, so this attachment had the whole bound to itself");
        }
    }

    @Test
    void anXmpPacketThatInflatesWithoutEndIsABoundAndNotACrash() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .xmpStream(Pdfs.deflatedZeros(BOMB))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            PdfLimitException refused =
                    assertThrows(PdfLimitException.class, container::xmpPacket);
            assertTrue(refused.getMessage().contains("XMP packet"), refused.getMessage());
        }
    }

    @Test
    void aFilterWhoseDecoderAllocatesFirstIsNotRunAtAll() {
        // A capped output stream bounds what a filter writes, which bounds nothing at all
        // for a decoder that builds the whole raster from the numbers in the file before
        // it writes a byte. The header below declares twenty thousand by twenty thousand
        // pixels in under a hundred bytes, and an image decoder handed it allocates four
        // hundred million pixels — far past the heap of this run — before the cap is ever
        // consulted. An electronic invoice is not a JPEG, so the filter is refused.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.filtered("logo.jpg", jpegHeader(20000, 20000),
                        COSName.DCT_DECODE, "Supplement"))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            EmbeddedFile file = container.embeddedFiles().get(0);

            PdfFormatException refused =
                    assertThrows(PdfFormatException.class, () -> file.head(1024));
            assertTrue(refused.getMessage().contains("which this reader does not decode"),
                    refused.getMessage());
        }
    }

    @Test
    void aStreamThisReaderDoesNotDecodeDoesNotStopTheInvoiceBesideIt() {
        // The same availability property the bomb beside an invoice has: anyone who can
        // add a file to a hybrid invoice would otherwise deny its recipient a verdict, by
        // attaching a picture instead of a bomb.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .attach(Pdfs.Attachment.filtered("logo.jpg", jpegHeader(40000, 40000),
                        COSName.DCT_DECODE, "Supplement"))
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);

            assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
            assertEquals(AttachmentKind.UNREADABLE, located.all().get(1).kind());
        }
    }

    @Test
    void anOrdinaryAttachmentIsReadWholeAndIsNotReportedAsCutOff() {
        byte[] invoice = Conformance.instance(CII);
        byte[] pdf = Pdfs.facturX(invoice);

        try (PdfContainer container = PdfContainer.open(pdf)) {
            AttachmentContent content = container.embeddedFiles().get(0).content();

            assertFalse(content.truncated());
            assertEquals(invoice.length, content.length());
            assertEquals(new String(invoice, StandardCharsets.UTF_8),
                    new String(content.bytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Returns the first bytes of a JPEG declaring a raster of a size of the caller's
     * choosing: the start of image and a baseline frame header (ISO/IEC 10918-1, B.2.2).
     * Nothing decodes it here, and that is what the test above is about.
     */
    private static byte[] jpegHeader(int width, int height) {
        return new byte[] {
            (byte) 0xFF, (byte) 0xD8,                    // start of image
            (byte) 0xFF, (byte) 0xC0, 0x00, 0x11, 0x08,  // baseline frame, 8 bits a sample
            (byte) (height >> 8), (byte) height,
            (byte) (width >> 8), (byte) width,
            0x03,                                        // three components
            0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01
        };
    }
    @Test
    void aStreamIsMeasuredUnderItsOwnDecodeParameters() {
        // A predictor is part of the decoding and changes how much comes out of it: each
        // row of the Flate output carries one tag byte that the predictor consumes. A
        // measurement taken with an empty parameter dictionary would run the decoder
        // under rules the library does not use and answer a different number.
        int rows = 100;
        int columns = 4;
        byte[] flate = Pdfs.deflatedZeros((long) rows * (columns + 1));
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.FILTER, COSName.FLATE_DECODE);
        COSDictionary parameters = new COSDictionary();
        parameters.setInt(COSName.PREDICTOR, 12);
        parameters.setInt(COSName.COLUMNS, columns);
        dictionary.setItem(COSName.DECODE_PARMS, parameters);

        long measured = BoundedStream.measure(new ByteArrayInputStream(flate), dictionary,
                List.of(COSName.FLATE_DECODE), 1 << 20);

        assertEquals((long) rows * columns, measured,
                "the predictor of the stream is part of what the stream decodes to");
    }

    @Test
    void aPredictorRowIsRefusedBeforeTheChainIsRunAtAll() {
        // The row is the one allocation of a Flate chain that a bound on the output cannot
        // reach: the library builds two buffers of it out of the parameters before either
        // decoder writes a byte, and 268 435 455 columns of one eight-bit component is the
        // widest row that does not overflow its arithmetic — half a gibibyte, twice, for a
        // dictionary entry. Nothing here is decoded, so the fixture is eight bytes long.
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.FILTER, COSName.FLATE_DECODE);
        dictionary.setItem(COSName.DECODE_PARMS, Pdfs.predictor(268435455));

        PdfLimitException refused = assertThrows(PdfLimitException.class,
                () -> BoundedStream.measure(new ByteArrayInputStream(new byte[8]),
                        dictionary, List.of(COSName.FLATE_DECODE), 1 << 20));

        assertTrue(refused.getMessage().contains("declares a predictor row of 268435455"),
                refused.getMessage());
    }

    @Test
    void anAttachmentDeclaringSuchAPredictorIsRefusedTheSameWay() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.predicted(Pdfs.FACTUR_X,
                        Pdfs.deflatedZeros(4096), 268435455))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            EmbeddedFile file = container.embeddedFiles().get(0);

            PdfLimitException refused =
                    assertThrows(PdfLimitException.class, () -> file.head(1024));
            assertTrue(refused.getMessage().contains("predictor row"), refused.getMessage());
        }
    }

    @Test
    void anXmpPacketDeclaringSuchAPredictorIsRefusedTheSameWay() {
        // The packet is read on every container, whatever its attachments turn out to be.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .xmpStream(Pdfs.deflatedZeros(4096))
                .xmpDecodeParms(Pdfs.predictor(268435455))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            PdfLimitException refused =
                    assertThrows(PdfLimitException.class, container::xmpPacket);
            assertTrue(refused.getMessage().contains("predictor row"), refused.getMessage());
        }
    }

    @Test
    void aPredictorRowThatOverflowsTheLibrarysIntIsRefusedRatherThanWavedThrough() {
        // The library computes the row in int: /Colors 8 by /BitsPerComponent 15790321 is
        // 126 322 568, and a /Columns of -17 makes that wrap to 2 147 483 640, a row of
        // 268 435 455 bytes allocated twice. A guard that multiplied the same numbers in
        // long would see -268 435 456 and let the stream through. None of the three has a
        // meaning below one, so the stage is refused for what it declares.
        COSDictionary dictionary = new COSDictionary();
        dictionary.setItem(COSName.FILTER, COSName.FLATE_DECODE);
        dictionary.setItem(COSName.DECODE_PARMS, Pdfs.predictor(12, 8, 15790321, -17));

        PdfFormatException refused = assertThrows(PdfFormatException.class,
                () -> BoundedStream.measure(new ByteArrayInputStream(new byte[8]),
                        dictionary, List.of(COSName.FLATE_DECODE), 1 << 20));

        assertTrue(refused.getMessage().contains("no predictor stage this format defines"),
                refused.getMessage());
    }

    @Test
    void aPredictorParameterOutsideTheValuesOfThisFormatIsMalformed() {
        // Zero and below are refused for all three, because the library's behaviour there
        // is undefined rather than safe, and /BitsPerComponent is one of five numbers.
        List<COSDictionary> malformed = List.of(
                Pdfs.predictor(12, 0, 8, 4),
                Pdfs.predictor(12, -1, 8, 4),
                Pdfs.predictor(12, 1, 0, 4),
                Pdfs.predictor(12, 1, -1, 4),
                Pdfs.predictor(12, 1, 8, 0),
                Pdfs.predictor(12, 1, 8, -1),
                Pdfs.predictor(12, 1, 3, 4),
                Pdfs.predictor(12, 1, 32, 4),
                Pdfs.predictor(3, 1, 8, 4));

        for (COSDictionary parameters : malformed) {
            COSDictionary dictionary = new COSDictionary();
            dictionary.setItem(COSName.FILTER, COSName.FLATE_DECODE);
            dictionary.setItem(COSName.DECODE_PARMS, parameters);

            assertThrows(PdfFormatException.class,
                    () -> BoundedStream.measure(new ByteArrayInputStream(new byte[8]),
                            dictionary, List.of(COSName.FLATE_DECODE), 1 << 20),
                    () -> "accepted " + parameters);
        }
    }

    @Test
    void anAttachmentDeclaringSuchAPredictorIsMalformedTooAndSoIsASmallerOne() {
        // The two shapes cost 769 and 770 bytes on disk and two buffers of 256 and 128
        // mebibytes in the library: /Colors 1 /BitsPerComponent 1073741824 /Columns -3 is
        // the second one, and neither is decoded here.
        for (COSDictionary parameters : List.of(Pdfs.predictor(12, 8, 15790321, -17),
                Pdfs.predictor(12, 1, 1073741824, -3))) {
            byte[] pdf = Pdfs.builder()
                    .attach(Pdfs.Attachment.predicted(Pdfs.FACTUR_X,
                            Pdfs.deflatedZeros(4096), parameters))
                    .build();

            try (PdfContainer container = PdfContainer.open(pdf)) {
                EmbeddedFile file = container.embeddedFiles().get(0);

                assertThrows(PdfFormatException.class, () -> file.head(1024),
                        () -> "accepted " + parameters);
            }
        }
    }

    @Test
    void anXmpPacketDeclaringSuchAPredictorIsMalformedTheSameWay() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .xmpStream(Pdfs.deflatedZeros(4096))
                .xmpDecodeParms(Pdfs.predictor(12, 8, 15790321, -17))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            assertThrows(PdfFormatException.class, container::xmpPacket);
        }
    }

    @Test
    void aFilterChainThisReaderDoesNotRunIsNotMeasured() {
        long measured = BoundedStream.measure(new ByteArrayInputStream(new byte[8]),
                new COSDictionary(), List.of(COSName.DCT_DECODE), 1 << 20);

        assertEquals(BoundedStream.NOT_MEASURABLE, measured,
                "where this reader cannot decode a stream it says nothing about its size");
    }

    @Test
    void aStreamStillProducingAtTheBoundIsAnsweredAsPastIt() {
        long measured = BoundedStream.measure(new ByteArrayInputStream(Pdfs.deflatedZeros(BOMB)),
                new COSDictionary(), List.of(COSName.FLATE_DECODE), 1 << 20);

        assertEquals(BoundedStream.PAST_THE_BOUND, measured,
                "the bound is met while the bytes are made, and none of them is kept");
    }
}
