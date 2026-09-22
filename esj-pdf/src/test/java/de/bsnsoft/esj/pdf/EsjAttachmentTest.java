package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.bindings.BindingSyntax;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.bindings.StreamingReader;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.xr.XrImporter;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The ESJ document a hybrid invoice carries beside the invoice XML: what is written, when
 * it is left out, and what it is on the way back in.
 *
 * <p>The rule it is written under is the subject of {@link EsjAgreementTest}; this class is
 * about the container. What matters here is that the second attachment is an enclosure and
 * stays one — the invoice of the file is still its XML, the reader never offers the ESJ
 * document as a candidate, and a file that carries nothing but the ESJ document carries no
 * electronic invoice.
 */
class EsjAttachmentTest {

    /** An invoice of the corpus, which the profile XRECHNUNG writes in BT-24. */
    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";

    /** The options the corpus invoice is embedded under. */
    private static final EmbedOptions XRECHNUNG = EmbedOptions.of(FacturXProfile.XRECHNUNG);

    @Test
    void theEsjDocumentIsWrittenBesideTheInvoiceAsAnEnclosure() {
        SemanticDocument document = document();

        byte[] hybrid = FacturX.embed(Pdfs.pdfa3(), document, XRECHNUNG);

        try (PdfContainer container = PdfContainer.open(hybrid)) {
            EmbeddedFile file = named(container, EsjAttachment.NAME);
            assertEquals(Optional.of(EsjAttachment.MEDIA_TYPE), file.declaredMediaType());
            assertEquals(Optional.of(EsjAttachment.RELATIONSHIP),
                    file.associatedRelationship());
            assertTrue(file.associated(), "the catalog's /AF array names it");
            assertEquals(OptionalLong.of(
                            Canonicalizer.canonicalBytes(document).length),
                    file.declaredSize(), "the size it decodes to is declared");
            assertArrayEquals(Canonicalizer.canonicalBytes(document),
                    file.content().bytes(), "and it carries the canonical bytes");
        }
    }

    /**
     * The second attachment is not an invoice and is never offered as one. The reader
     * still finds exactly one electronic invoice in the file, which is the XML.
     */
    @Test
    void theEsjDocumentIsNeverACandidateForTheInvoice() {
        byte[] hybrid = FacturX.embed(Pdfs.pdfa3(), document(), XRECHNUNG);

        try (PdfContainer container = PdfContainer.open(hybrid)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);
            LocatedAttachment esj = EsjAttachment.in(located).orElseThrow();
            assertEquals(AttachmentKind.ESJ_DOCUMENT, esj.kind());
            assertFalse(esj.kind().isInvoice(), "it is no invoice");
            assertEquals(List.of(), located.candidates().stream()
                            .filter(candidate -> candidate.kind() == AttachmentKind.ESJ_DOCUMENT)
                            .toList(),
                    "and it is not among the attachments that could be the invoice");
            assertEquals(2, located.all().size(), "the container carries two attachments");
            assertEquals(AttachmentKind.CII_INVOICE, located.single().kind(),
                    "and the one that is the invoice is still the XML");
        }
    }

    /**
     * A container carrying the ESJ document and no invoice XML carries no electronic
     * invoice. This version reads the invoice out of the XML and out of nothing else.
     */
    @Test
    void aFileCarryingOnlyTheEsjDocumentCarriesNoInvoice() {
        byte[] pdf = Pdfs.builder()
                .xmp(Pdfs.pdfaXmp(3, "B"))
                .attach(esjAttachment(Canonicalizer.canonicalBytes(document())))
                .build();

        NoInvoiceAttachmentException refused = assertThrows(
                NoInvoiceAttachmentException.class, () -> PdfInvoiceImporter.importPdf(pdf));

        assertEquals(1, refused.attachments().size());
        assertEquals(AttachmentKind.ESJ_DOCUMENT, refused.attachments().get(0).kind());
    }

    /**
     * Where the cross industry invoice cannot carry a core value of the document, the two
     * files would not be two accounts of one invoice, so no ESJ document is attached and
     * the result says which paths it was. The XML goes in as before.
     */
    @Test
    void aDocumentTheSyntaxCannotCarryWholeGetsNoEsjDocument() {
        SemanticDocument document = document().toBuilder()
                .put("/BG-3/0/BT-25", "RE-2025-0006")
                .put("/BG-3/1/BT-25", "RE-2025-0007")
                .build();

        EmbedResult result = FacturX.embedWithReport(Pdfs.pdfa3(), document, XRECHNUNG);

        assertFalse(result.esjAttached(), "no ESJ document is attached");
        assertTrue(result.esjOmitted().orElseThrow().contains("/BG-3/1/BT-25"),
                result.esjOmitted().orElseThrow());
        try (PdfContainer container = PdfContainer.open(result.pdf())) {
            assertEquals(1, container.embeddedFiles().size(), "the invoice alone");
            assertEquals(Pdfs.FACTUR_X, container.embeddedFiles().get(0).name());
        }
    }

    /**
     * A term the binding table does not bind — here one of no published model at all — is
     * what the attachment is for: it is not in the XML, and carrying it is no
     * disagreement.
     */
    @Test
    void aTermTheSyntaxDoesNotBindKeepsTheEsjDocument() {
        SemanticDocument document = document().toBuilder()
                .put("/BT-B2C-010", "119.00")
                .build();

        EmbedResult result = FacturX.embedWithReport(Pdfs.pdfa3(), document, XRECHNUNG);

        assertTrue(result.esjAttached(), result.esjOmitted().orElse(""));
        try (PdfContainer container = PdfContainer.open(result.pdf())) {
            SemanticDocument carried = EsjReader.strict()
                    .read(named(container, EsjAttachment.NAME).content().bytes());
            assertEquals(Optional.of("119.00"),
                    carried.value(SemanticPath.of("/BT-B2C-010"))
                            .map(SemanticValue::content),
                    "the term the XML has no place for is in the attachment");
        }
    }

    /**
     * The switch writes the file this project wrote before the attachment existed: the
     * invoice alone, and the bytes of that file unchanged. The golden of that shape is in
     * {@code esj-render}, over a rendering, which is the deterministic input this module
     * has no renderer for.
     */
    @Test
    void theSwitchWritesTheFileWithoutTheAttachment() {
        byte[] pages = Pdfs.pdfa3();

        byte[] hybrid = FacturX.embed(pages, document(), XRECHNUNG.withEsj(false));

        try (PdfContainer container = PdfContainer.open(hybrid)) {
            assertEquals(1, container.embeddedFiles().size(), "one attachment");
            assertEquals(Pdfs.FACTUR_X, container.embeddedFiles().get(0).name());
        }
        assertArrayEquals(hybrid, FacturX.embed(pages, document(),
                XRECHNUNG.withEsj(false)), "and the same bytes on every run");
    }

    /** Both attachments are a function of the document, so the file still is. */
    @Test
    void embeddingTheSameInvoiceTwiceGivesTheSameBytes() {
        byte[] pages = Pdfs.pdfa3();
        SemanticDocument document = document();

        assertArrayEquals(FacturX.embed(pages, document, XRECHNUNG),
                FacturX.embed(pages, document, XRECHNUNG));
    }

    /** The attachment written into a file agrees with the invoice beside it. */
    @Test
    void whatIsWrittenSatisfiesTheRuleItIsWrittenUnder() {
        SemanticDocument document = document();
        byte[] hybrid = FacturX.embed(Pdfs.pdfa3(), document, XRECHNUNG);

        try (PdfContainer container = PdfContainer.open(hybrid)) {
            SemanticDocument esj = EsjReader.strict()
                    .read(named(container, EsjAttachment.NAME).content().bytes());
            SemanticDocument xml = new StreamingReader()
                    .read(named(container, Pdfs.FACTUR_X).content().bytes()).document();

            assertEquals(List.of(),
                    EsjAgreement.differences(esj, xml, BindingSyntax.CII),
                    "the two files of this container are two accounts of one invoice");
        }
    }

    /** A label on an attachment that holds no JSON object does not make it an ESJ document. */
    @Test
    void theLabelAloneDoesNotMakeAnAttachmentAnEsjDocument() {
        byte[] pdf = Pdfs.builder()
                .xmp(Pdfs.pdfaXmp(3, "B"))
                .attach(Pdfs.Attachment.invoice(Pdfs.FACTUR_X,
                        CiiWriter.write(document())))
                .attach(esjAttachment(
                        "not a JSON object at all".getBytes(StandardCharsets.UTF_8)))
                .build();

        try (PdfContainer container = PdfContainer.open(pdf)) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);
            assertEquals(Optional.empty(), EsjAttachment.in(located));
            assertEquals(AttachmentKind.NOT_XML, located.all().get(1).kind());
        }
    }

    /** Returns an attachment of the shape this project writes the ESJ document as. */
    private static Pdfs.Attachment esjAttachment(byte[] content) {
        return new Pdfs.Attachment(EsjAttachment.NAME, content, EsjAttachment.MEDIA_TYPE,
                EsjAttachment.RELATIONSHIP, true, content.length, false, null, null);
    }

    private static EmbeddedFile named(PdfContainer container, String name) {
        return container.embeddedFiles().stream()
                .filter(file -> name.equals(file.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no attachment named " + name));
    }

    private static SemanticDocument document() {
        return new XrImporter().importXml(Conformance.instance(CII));
    }
}
