package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.xr.XrException;
import de.bsnsoft.esj.xr.XrImporter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Reading the invoice out of a container, and the ways that ends without one. */
class PdfInvoiceImporterTest {

    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";
    private static final String UBL = "business-cases/standard/01.01a-INVOICE_ubl.xml";

    private static final byte[] ZUGFERD_1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rsm:CrossIndustryDocument
                xmlns:rsm="urn:ferd:CrossIndustryDocument:invoice:1p0">
              <rsm:SpecifiedExchangedDocumentContext/>
            </rsm:CrossIndustryDocument>
            """.getBytes(StandardCharsets.UTF_8);

    private final XrImporter importer = new XrImporter();

    @Test
    void readsTheInvoiceAndReportsTheContainerAroundIt() {
        byte[] pdf = Pdfs.facturX(Conformance.instance(CII));

        PdfImportResult result = PdfInvoiceImporter.importPdf(pdf);

        assertEquals("CII", result.document().source().orElseThrow().syntax().orElseThrow());
        assertEquals(Pdfs.FACTUR_X, result.attachment().name());
        assertEquals(AttachmentKind.CII_INVOICE, result.attachment().kind());
        assertEquals(1, result.pdf().attachments().size());
        assertEquals(Optional.of(FacturXProfile.XRECHNUNG), result.pdf().profile());
        assertEquals(Optional.of("PDF/A-3B"),
                result.pdf().pdfa().map(PdfaIdentification::describe));
        assertTrue(result.en16931Invoice());
        assertEquals(List.of("PDF-STRUCTURE-PDFA"),
                result.pdf().findings(ContainerFinding.Category.PDF_STRUCTURE).stream()
                        .map(ContainerFinding::code).toList());
        assertEquals(List.of(), result.pdf().findings(ContainerFinding.Category.PDF_AF));
    }

    @Test
    void readsAnInvoiceOutOfAContainerThatSaysNothingAboutIt() {
        byte[] ubl = Conformance.instance(UBL);
        byte[] pdf = Pdfs.builder()
                .attach(new Pdfs.Attachment("anything.xml", ubl, null, null, false,
                        ubl.length, false, null))
                .build();

        PdfImportResult result = PdfInvoiceImporter.importPdf(pdf);

        assertEquals("UBL", result.document().source().orElseThrow().syntax().orElseThrow());
        assertTrue(result.pdf().facturX().isEmpty());
        assertTrue(result.pdf().finding("PDF-XMP-ABSENT").isPresent());
        assertTrue(result.pdf().finding("PDF-AF-ABSENT").isPresent());
        assertTrue(result.pdf().finding("PDF-EMBEDDED-MIME").isPresent());
    }

    @Test
    void saysThatAMinimumProfileIsNoEn16931Invoice() {
        // The profile is taken from the invoice where the invoice writes one, so a
        // document without BT-24 is the case where the container's declaration decides.
        byte[] stripped = withoutSpecificationIdentifier(Conformance.instance(CII));
        byte[] pdf = Pdfs.facturX(Pdfs.FACTUR_X, stripped, "MINIMUM");

        PdfImportResult result = PdfInvoiceImporter.importPdf(pdf);

        assertEquals(Optional.of(FacturXProfile.MINIMUM), result.pdf().profile());
        assertFalse(result.en16931Invoice());
    }

    @Test
    void takesAnUnknownProfileForAnOrdinaryInvoiceRatherThanRefusingToCheckIt() {
        byte[] pdf = Pdfs.builder().attach("invoice.xml", Conformance.instance(CII)).build();

        PdfImportResult result = PdfInvoiceImporter.importPdf(pdf);

        assertTrue(result.en16931Invoice());
    }

    @Test
    void refusesZugferd1AsUnsupportedRatherThanAsAbsent() {
        byte[] pdf = Pdfs.builder().attach(Pdfs.ZUGFERD_1, ZUGFERD_1).build();

        UnsupportedInvoiceException thrown = assertThrows(UnsupportedInvoiceException.class,
                () -> PdfInvoiceImporter.importPdf(pdf));

        assertEquals(AttachmentKind.ZUGFERD_1, thrown.attachment().kind());
        assertTrue(thrown.getMessage().contains("ZUGFeRD 1.0"), thrown.getMessage());
    }

    @Test
    void refusesAPdfWithoutAStructuredInvoice() {
        byte[] pdf = Pdfs.builder().build();

        NoInvoiceAttachmentException thrown = assertThrows(NoInvoiceAttachmentException.class,
                () -> PdfInvoiceImporter.importPdf(pdf));

        assertEquals(List.of(), thrown.attachments());
    }

    @Test
    void refusesToChooseBetweenTwoInvoices() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .attach("xrechnung.xml", Conformance.instance(UBL))
                .build();

        AmbiguousInvoiceAttachmentException thrown =
                assertThrows(AmbiguousInvoiceAttachmentException.class,
                        () -> PdfInvoiceImporter.importPdf(pdf));

        assertEquals(2, thrown.candidates().size());
    }

    @Test
    void refusesAnInvoiceThatWasCutOffAtABoundRatherThanImportingHalfOfIt() {
        byte[] padded = padded(Conformance.instance(CII), 512 * 1024);
        byte[] pdf = Pdfs.facturX(Pdfs.FACTUR_X, padded, "EN 16931");
        PdfLimits limits = PdfLimits.defaults().withMaxAttachmentBytes(16 * 1024);

        PdfLimitException thrown = assertThrows(PdfLimitException.class,
                () -> PdfInvoiceImporter.importPdf(pdf, limits, importer));

        assertTrue(thrown.getMessage().contains("16384"), thrown.getMessage());
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void readsTheSameDocumentThroughAContainerAsOnItsOwn(String instance) {
        byte[] xml = Conformance.instance(instance);
        byte[] pdf = Pdfs.facturX("invoice.xml", xml, "EN 16931");

        byte[] direct;
        try {
            direct = canonical(importer.importXml(xml));
        } catch (XrException e) {
            // An instance the importer refuses is refused the same way through the
            // container: the container is not part of the answer.
            assertThrows(e.getClass(), () -> PdfInvoiceImporter.importPdf(pdf));
            return;
        }

        assertArrayEquals(direct, canonical(PdfInvoiceImporter.importPdf(pdf).document()),
                instance);
    }

    static List<String> corpus() {
        return Conformance.corpus();
    }

    private static byte[] canonical(SemanticDocument document) {
        return Canonicalizer.canonicalBytes(document);
    }

    /** Appends whitespace to a document, which changes nothing but its length. */
    private static byte[] padded(byte[] xml, int extra) {
        byte[] padded = Arrays.copyOf(xml, xml.length + extra);
        Arrays.fill(padded, xml.length, padded.length, (byte) ' ');
        return padded;
    }

    /**
     * Removes BT-24 from an instance, so that the profile can only come from the
     * container.
     */
    private static byte[] withoutSpecificationIdentifier(byte[] cii) {
        String text = new String(cii, StandardCharsets.UTF_8);
        int start = text.indexOf("<ram:GuidelineSpecifiedDocumentContextParameter>");
        int end = text.indexOf("</ram:GuidelineSpecifiedDocumentContextParameter>");
        if (start < 0 || end < 0) {
            throw new IllegalStateException("this instance carries no BT-24");
        }
        String stripped = text.substring(0, start)
                + text.substring(end + "</ram:GuidelineSpecifiedDocumentContextParameter>".length());
        return stripped.getBytes(StandardCharsets.UTF_8);
    }
}
