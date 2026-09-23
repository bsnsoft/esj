package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every place of a PDF that can refer to an embedded file, and what counts as one file.
 *
 * <p>A file can be embedded from the name tree, from the associated files array of the
 * document or of a page, from a file attachment annotation and from the associated files
 * array of an annotation, and a reader of a hybrid invoice may look in any of them. So all
 * of them are enumerated, joined on the embedded file stream: a stream two places refer to
 * is one attachment, and a stream only one place refers to is an attachment all the same,
 * with its place said.
 */
class AttachmentPlacesTest {

    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";

    private static final String OTHER_CII =
            "business-cases/standard/01.02a-INVOICE_uncefact.xml";

    /** An ordinary hybrid invoice: one invoice, in the tree and in the array. */
    private static byte[] hybrid() {
        return Pdfs.builder()
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X, Conformance.instance(CII)))
                .nameTreeKeysAreNames()
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();
    }

    /** A second invoice, declared as the document, under the invoice's own name. */
    private static Pdfs.Attachment second() {
        byte[] content = Conformance.instance(OTHER_CII);
        return new Pdfs.Attachment(Pdfs.FACTUR_X, content, Pdfs.XML, Pdfs.ALTERNATIVE,
                false, content.length, false, null);
    }

    @Test
    void aFileAttachmentAnnotationCarryingASecondInvoiceIsASecondCandidate() {
        byte[] pdf = PdfEdit.annotate(hybrid(), second());

        try (PdfContainer container = PdfContainer.open(pdf)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);

            assertEquals(2, located.all().size());
            EmbeddedFile hidden = located.all().get(1).file();
            assertFalse(hidden.inNameTree());
            assertEquals(List.of(new EmbeddedFile.Reference(
                            EmbeddedFile.Place.FILE_ATTACHMENT_ANNOTATION, 1, "F")),
                    hidden.references());
            assertEquals(2, located.candidates().size());
            assertThrows(AmbiguousInvoiceAttachmentException.class, located::single);

            ContainerFinding outside = finding(ContainerChecks.run(container, located),
                    "PDF-EMBEDDED-NOT-IN-TREE");
            assertEquals(ContainerFinding.Severity.WARNING, outside.severity());
            assertTrue(outside.message().contains("the attachment 2 \"factur-x.xml\" (CII,"
                    + " object "), outside.message());
            assertTrue(outside.message().contains("a file attachment annotation on page 1"),
                    outside.message());
        }
    }

    @Test
    void thePageAssociatedFilesArrayCarryingASecondInvoiceIsASecondCandidate() {
        byte[] pdf = PdfEdit.associateWithThePage(hybrid(), second());

        InvoiceAttachments located = located(pdf);

        assertEquals(2, located.candidates().size());
        assertEquals(List.of(new EmbeddedFile.Reference(
                        EmbeddedFile.Place.PAGE_ASSOCIATED_FILES, 1, "F")),
                located.all().get(1).file().references());
        assertThrows(AmbiguousInvoiceAttachmentException.class, located::single);
    }

    @Test
    void theAssociatedFilesArrayOfAnAnnotationIsAPlaceToo() {
        byte[] pdf = PdfEdit.associateWithAnAnnotation(hybrid(), second());

        InvoiceAttachments located = located(pdf);

        assertEquals(2, located.candidates().size());
        assertEquals(EmbeddedFile.Place.ANNOTATION_ASSOCIATED_FILES,
                located.all().get(1).file().references().get(0).place());
    }

    @Test
    void anAnnotationThatIsNoFileAttachmentCarriesNoFile() {
        // Only a file attachment annotation carries a file; a text annotation with a file
        // specification under /FS is not one a viewer offers, and is not counted.
        byte[] pdf = PdfEdit.textAnnotationNamingAFile(hybrid(), second());

        InvoiceAttachments located = located(pdf);

        assertEquals(1, located.all().size());
        assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
    }

    @Test
    void anInvoiceOnlyAnAnnotationCarriesIsReadAndItsPlaceIsSaid() {
        byte[] page = Pdfs.builder().xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931")).build();
        byte[] content = Conformance.instance(CII);
        byte[] pdf = PdfEdit.annotate(page, new Pdfs.Attachment(Pdfs.FACTUR_X, content,
                Pdfs.XML, Pdfs.ALTERNATIVE, false, content.length, false, null));

        try (PdfContainer container = PdfContainer.open(pdf)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);

            LocatedAttachment invoice = located.single();
            assertEquals(AttachmentKind.CII_INVOICE, invoice.kind());
            List<ContainerFinding> findings = ContainerChecks.run(container, located);
            assertEquals(ContainerFinding.Severity.WARNING,
                    finding(findings, "PDF-EMBEDDED-NOT-IN-TREE").severity());
            assertEquals(ContainerFinding.Severity.ERROR,
                    finding(findings, "PDF-AF-ABSENT").severity(),
                    "and the document's array does not declare it either");
        }
    }

    @Test
    void oneStreamReferredToFromTheTreeAndFromAnAnnotationIsOneAttachment() {
        byte[] pdf = PdfEdit.annotateTheFirstEntry(hybrid());

        InvoiceAttachments located = located(pdf);

        assertEquals(1, located.all().size());
        EmbeddedFile file = located.all().get(0).file();
        assertEquals(List.of(EmbeddedFile.Place.NAME_TREE,
                        EmbeddedFile.Place.DOCUMENT_ASSOCIATED_FILES,
                        EmbeddedFile.Place.FILE_ATTACHMENT_ANNOTATION),
                file.references().stream().map(EmbeddedFile.Reference::place).toList());
        assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
    }

    @Test
    void twoFileSpecificationsOfOneStreamAreOneAttachment() {
        // A reader that follows either of the two keys is handed the same bytes, so the
        // container names one file however it is spelt.
        byte[] pdf = PdfEdit.secondSpecificationOfTheFirstStream(hybrid(), "invoice.xml");

        try (PdfContainer container = PdfContainer.open(pdf)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);

            assertEquals(1, located.all().size());
            assertEquals(List.of(Pdfs.FACTUR_X, "invoice.xml"),
                    located.all().get(0).file().nameTreeKeys());
            assertTrue(located.duplicateNames().isEmpty());
            assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
            assertTrue(located.named("invoice.xml").isPresent(),
                    "a key the tree lists it under names it");
            assertFalse(ContainerChecks.run(container, located).stream()
                    .anyMatch(finding -> finding.code().startsWith("PDF-EMBEDDED")));
        }
    }

    @Test
    void aFileSpecificationWithAnotherStreamUnderItsUnicodeEntryHoldsTwoFiles() {
        byte[] pdf = PdfEdit.anotherStreamUnderTheUnicodeEntry(hybrid(),
                Conformance.instance(OTHER_CII));

        try (PdfContainer container = PdfContainer.open(pdf)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);

            assertEquals(2, located.all().size(), "one attachment per stream");
            assertEquals(2, located.candidates().size());
            assertThrows(AmbiguousInvoiceAttachmentException.class, located::single);
            assertThrows(AmbiguousInvoiceAttachmentException.class,
                    () -> located.named(Pdfs.FACTUR_X));

            List<InvoiceAttachments.DuplicateName> duplicates = located.duplicateNames();
            assertEquals(2, duplicates.size(), "the key, and the file specification");
            InvoiceAttachments.DuplicateName specification = duplicates.get(1);
            assertEquals(InvoiceAttachments.DuplicateName.Source.FILE_SPECIFICATION,
                    specification.source());
            assertEquals(List.of("F", "UF"), specification.entries());

            List<ContainerFinding> findings = ContainerChecks.run(container, located);
            assertTrue(findings.stream()
                    .filter(finding -> finding.code().equals("PDF-EMBEDDED-DUPLICATE-NAME"))
                    .anyMatch(finding -> finding.message().contains("under the entries /F, /UF"
                            + " of its embedded file dictionary")), findings.toString());
        }
    }

    @Test
    void aStreamOfferedAsAFileSpecificationIsNotTakenForOneThatEmbedsNothing() {
        // The first entry of the tree names the invoice's stream itself where a file
        // specification belongs; the second names a file specification of that stream. The
        // first embeds nothing and is an attachment of its own; the second is the invoice,
        // with its content.
        byte[] pdf = PdfEdit.streamAsTheValueOfAnEntry(hybrid(), "a");

        InvoiceAttachments located = located(pdf);

        assertEquals(2, located.all().size());
        assertFalse(located.all().get(0).file().hasContent());
        assertEquals(AttachmentKind.CII_INVOICE, located.single().kind());
        assertTrue(located.single().file().hasContent());
    }

    @Test
    void aPageTreeThatLoopsIsWalkedOnce() {
        byte[] pdf = PdfEdit.pageTreeThatLoops(PdfEdit.annotate(hybrid(), second()));

        InvoiceAttachments located = located(pdf);

        assertEquals(2, located.all().size());
        assertEquals(1, located.all().get(1).file().references().get(0).page());
    }

    @Test
    void theWalkOfThePagesIsBounded() {
        byte[] pdf = PdfEdit.withMorePages(hybrid(), 40);

        PdfLimitException refused = assertThrows(PdfLimitException.class,
                () -> PdfContainer.open(pdf, PdfLimits.defaults().withMaxObjectStreamObjects(30))
                        .close());

        assertTrue(refused.getMessage().contains("objects this reader walks on the pages"),
                refused.getMessage());
    }

    @Test
    void pagesAreNumberedInTheOrderOfTheTree() {
        byte[] withPages = PdfEdit.withMorePages(hybrid(), 2);
        byte[] content = "notes".getBytes(StandardCharsets.UTF_8);
        byte[] pdf = PdfEdit.annotate(withPages, new Pdfs.Attachment("notes.txt", content,
                "text/plain", "Supplement", false, content.length, false, null));

        InvoiceAttachments located = located(pdf);

        assertEquals(1, located.all().get(1).file().references().get(0).page(),
                "the annotation stands on the first page");
        assertEquals(AttachmentKind.CII_INVOICE, located.single().kind(),
                "a text beside the invoice is no candidate, wherever it lies");
    }

    private static InvoiceAttachments located(byte[] pdf) {
        return InvoiceAttachments.locate(PdfContainer.open(pdf));
    }

    private static ContainerFinding finding(List<ContainerFinding> findings, String code) {
        return findings.stream().filter(finding -> finding.code().equals(code)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + code + " among " + findings));
    }
}
