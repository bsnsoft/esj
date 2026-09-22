package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What the container reads out of a PDF, and what it refuses to read. */
class PdfContainerTest {

    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";

    @Test
    void readsTheAttachmentTheAssociatedFilesArrayNames() {
        byte[] pdf = Pdfs.facturX(Conformance.instance(CII));

        try (PdfContainer container = PdfContainer.open(pdf, PdfLimits.defaults())) {
            List<EmbeddedFile> files = container.embeddedFiles();

            assertEquals(1, files.size());
            EmbeddedFile file = files.get(0);
            assertEquals(Pdfs.FACTUR_X, file.name());
            assertEquals(Optional.of(Pdfs.XML), file.declaredMediaType());
            assertEquals(Optional.of(Pdfs.ALTERNATIVE), file.associatedRelationship());
            assertTrue(file.associated());
            assertTrue(file.hasContent());
            assertArrayEqualsTo(Conformance.instance(CII), file.content().bytes());
            assertFalse(file.content().truncated());
        }
    }

    @Test
    void readsTheFacturXPropertiesAndThePdfaDeclaration() {
        byte[] pdf = Pdfs.facturX(Conformance.instance(CII));

        try (PdfContainer container = PdfContainer.open(pdf, PdfLimits.defaults())) {
            FacturXMetadata metadata = container.facturX().orElseThrow();

            assertEquals(FacturXMetadata.FACTUR_X_NAMESPACE, metadata.namespace());
            assertEquals(Optional.of("INVOICE"), metadata.documentType());
            assertEquals(Optional.of(Pdfs.FACTUR_X), metadata.documentFileName());
            assertEquals(Optional.of("1.0"), metadata.version());
            assertEquals(Optional.of(FacturXProfile.EN_16931), metadata.profile());

            PdfaIdentification pdfa = container.pdfaIdentification().orElseThrow();
            assertEquals(3, pdfa.part());
            assertEquals(Optional.of("B"), pdfa.conformance());
            assertEquals("PDF/A-3B", pdfa.describe());
            assertTrue(container.xmpPacket().isPresent());
        }
    }

    @Test
    void readsAFileWithoutAnXmpPacketAndWithoutAttachments() {
        byte[] pdf = Pdfs.builder().build();

        try (PdfContainer container = PdfContainer.open(pdf, PdfLimits.defaults())) {
            assertEquals(List.of(), container.embeddedFiles());
            assertEquals(Optional.empty(), container.xmpPacket());
            assertEquals(Optional.empty(), container.facturX());
            assertEquals(Optional.empty(), container.pdfaIdentification());
        }
    }

    @Test
    void refusesAnEncryptedFileEvenWhereTheEmptyPasswordWouldOpenIt() {
        // PDF/A forbids encryption, so an encrypted file is not a container of an
        // electronic invoice; and decrypting one writes its plaintext through a path this
        // module does not measure. The refusal comes before anything is decrypted.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .userPassword("")
                .build();

        PdfAccessException refused = assertThrows(PdfAccessException.class,
                () -> PdfContainer.open(pdf, PdfLimits.defaults()));

        assertTrue(refused.getMessage().contains("PDF/A forbids"), refused.getMessage());
    }

    @Test
    void refusesAnEncryptedFileWhoseTrailerTheParserRebuiltWithoutTheEntry() {
        // Where startxref is unusable the trailer is rebuilt, and the rebuild carries
        // /Encrypt only when it found an info dictionary as well as a catalog; its
        // fallback drops the entry. The trailer the library ends up with therefore says
        // nothing about encryption, and the file would be read as an ordinary container.
        // The encryption dictionary is still an object of the file, and it is refused as
        // one.
        byte[] pdf = Pdfs.declaredEncryptionWithoutAUsableTrailer(
                Pdfs.deflated(Conformance.instance(CII)));

        PdfAccessException refused = assertThrows(PdfAccessException.class,
                () -> PdfContainer.open(pdf, PdfLimits.defaults()));

        assertTrue(refused.getMessage().contains("PDF/A forbids"), refused.getMessage());
    }

    @Test
    void refusesAnEncryptedFileWhoseVersionAndRevisionAreWrittenAsReferences() {
        // A value behind an indirect reference is the same value. A reader that
        // recognized the shape of an encryption dictionary only where the file spelled
        // the entries out would be told by the file whether to look.
        byte[] pdf = Pdfs.declaredEncryptionWithIndirectVersion(
                Pdfs.deflated(Conformance.instance(CII)));

        PdfAccessException refused = assertThrows(PdfAccessException.class,
                () -> PdfContainer.open(pdf, PdfLimits.defaults()));

        assertTrue(refused.getMessage().contains("PDF/A forbids"), refused.getMessage());
    }

    @Test
    void readsAFileWhoseEncryptionDictionaryNoParseEverHoldsAndDecryptsNothingOfIt() {
        // The residual case, and the property that holds without a condition. The
        // declaration is in a rebuilt-away trailer entry and the dictionary it pointed at
        // is compressed into an object stream nothing asks for, so no parse of this file
        // ever holds it and the refusal does not fire. Nothing is decrypted either: this
        // reader builds no security handler and takes no password, so the streams of the
        // file stay exactly the bytes they are. An attachment that really was ciphertext
        // therefore decodes to nothing, and is reported as an attachment nothing was read
        // from rather than as an invoice.
        byte[] ciphertext = new byte[64];
        Arrays.fill(ciphertext, (byte) 0x5a);
        byte[] pdf = Pdfs.declaredEncryptionInsideAnObjectStream(ciphertext);

        try (PdfContainer container = PdfContainer.open(pdf, PdfLimits.defaults())) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);

            assertEquals(1, located.all().size(), "the attachment is enumerated");
            assertEquals(List.of(), located.invoices(),
                    "and nothing in it spells an invoice, because it was never deciphered");
            assertFalse(located.all().get(0).kind().isInvoice(),
                    "the bytes of the attachment are what they were");
        }
        assertThrows(NoInvoiceAttachmentException.class,
                () -> PdfInvoiceImporter.importPdf(pdf),
                "so the file is reported as carrying no invoice");
    }

    @Test
    void refusesAnEncryptedFileThatNeedsARealPassword() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .userPassword("secret")
                .build();

        assertThrows(PdfAccessException.class,
                () -> PdfContainer.open(pdf, PdfLimits.defaults()));
    }

    @Test
    void refusesBytesThatAreNoPdf() {
        byte[] xml = Conformance.instance(CII);

        PdfFormatException thrown = assertThrows(PdfFormatException.class,
                () -> PdfContainer.open(xml, PdfLimits.defaults()));
        assertTrue(thrown.getMessage().contains("file header"), thrown.getMessage());
    }

    @Test
    void refusesATruncatedFile() {
        byte[] pdf = Pdfs.facturX(Conformance.instance(CII));
        byte[] half = Arrays.copyOf(pdf, pdf.length / 2);

        assertThrows(PdfException.class, () -> {
            try (PdfContainer container = PdfContainer.open(half, PdfLimits.defaults())) {
                InvoiceAttachments.locate(container).single();
            }
        });
    }

    @Test
    void refusesAFileThatNeedsAPassword() {
        byte[] pdf = Pdfs.builder().userPassword("open sesame").build();

        PdfAccessException thrown = assertThrows(PdfAccessException.class,
                () -> PdfContainer.open(pdf, PdfLimits.defaults()));
        assertTrue(thrown.getMessage().contains("no password"), thrown.getMessage());
    }

    @Test
    void refusesAFileLargerThanTheBound() {
        byte[] pdf = Pdfs.facturX(Conformance.instance(CII));
        PdfLimits limits = PdfLimits.defaults().withMaxPdfBytes(128);

        PdfLimitException thrown = assertThrows(PdfLimitException.class,
                () -> PdfContainer.open(pdf, limits));
        assertTrue(thrown.getMessage().contains("128"), thrown.getMessage());
    }

    @Test
    void refusesAFileWithMoreAttachmentsThanItEnumerates() {
        Pdfs.Builder builder = Pdfs.builder();
        for (int i = 0; i < 500; i++) {
            builder.attach("file-" + i + ".txt", ("filler " + i).getBytes(StandardCharsets.UTF_8));
        }
        byte[] pdf = builder.build();

        PdfLimitException thrown = assertThrows(PdfLimitException.class,
                () -> PdfContainer.open(pdf, PdfLimits.defaults()));
        assertTrue(thrown.getMessage().contains("64"), thrown.getMessage());
    }

    @Test
    void cutsOffAStreamThatInflatesPastTheBoundRatherThanHoldingIt() {
        // A small Flate stream that decodes to a megabyte: the file is tiny and the
        // attachment is not. The bound is met while decoding and the content says so.
        byte[] bomb = new byte[1024 * 1024];
        Arrays.fill(bomb, (byte) ' ');
        byte[] pdf = Pdfs.builder().attach("bomb.xml", bomb).build();
        PdfLimits limits = PdfLimits.defaults().withMaxAttachmentBytes(4096);

        assertTrue(pdf.length < 64 * 1024,
                "the file itself stays small: " + pdf.length + " bytes");
        try (PdfContainer container = PdfContainer.open(pdf, limits)) {
            AttachmentContent content = container.embeddedFiles().get(0).content();

            assertTrue(content.truncated());
            assertEquals(4096, content.length());
        }
    }

    @Test
    void refusesToDecodeMoreThanTheTotalBound() {
        byte[] filler = new byte[64 * 1024];
        Arrays.fill(filler, (byte) 'x');
        byte[] pdf = Pdfs.builder()
                .attach("one.bin", filler)
                .attach("two.bin", filler)
                .build();
        PdfLimits limits = PdfLimits.defaults()
                .withMaxAttachmentBytes(64 * 1024)
                .withMaxTotalAttachmentBytes(64 * 1024);

        try (PdfContainer container = PdfContainer.open(pdf, limits)) {
            assertEquals(64 * 1024, container.embeddedFiles().get(0).content().length());

            PdfLimitException thrown = assertThrows(PdfLimitException.class,
                    () -> container.embeddedFiles().get(1).content());
            assertTrue(thrown.getMessage().contains("two.bin"), thrown.getMessage());
        }
    }

    @Test
    void refusesAnXmpPacketLargerThanTheBound() {
        byte[] pdf = Pdfs.facturX(Conformance.instance(CII));
        PdfLimits limits = PdfLimits.defaults().withMaxXmpBytes(16);

        try (PdfContainer container = PdfContainer.open(pdf, limits)) {
            assertThrows(PdfLimitException.class, container::xmpPacket);
        }
    }

    @Test
    void reportsAFileWhoseCrossReferenceOffsetPointsAtNothing() {
        byte[] pdf = Pdfs.facturX(Conformance.instance(CII));
        byte[] broken = withStartxref(pdf, 7);

        try (PdfContainer container = PdfContainer.open(broken, PdfLimits.defaults())) {
            assertTrue(container.structureFindings().stream()
                            .anyMatch(finding -> finding.code().equals("PDF-STRUCTURE-XREF")),
                    container.structureFindings().toString());
        }
    }

    /**
     * Rewrites the offset the trailer gives for the cross-reference data, keeping the
     * length of the file so that nothing else moves.
     */
    private static byte[] withStartxref(byte[] pdf, long offset) {
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        int keyword = text.lastIndexOf("startxref");
        int start = keyword + "startxref".length() + 1;
        int end = start;
        while (end < text.length() && text.charAt(end) >= '0' && text.charAt(end) <= '9') {
            end++;
        }
        String replacement = String.valueOf(offset);
        while (replacement.length() < end - start) {
            replacement = "0" + replacement;
        }
        String rewritten = text.substring(0, start) + replacement + text.substring(end);
        return rewritten.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void assertArrayEqualsTo(byte[] expected, byte[] actual) {
        assertTrue(Arrays.equals(expected, actual),
                "expected " + expected.length + " bytes, got " + actual.length);
    }
}
