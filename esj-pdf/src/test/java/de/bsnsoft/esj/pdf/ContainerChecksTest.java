package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** What the container says about the invoice, checked against the invoice. */
class ContainerChecksTest {

    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";
    private static final String UBL = "business-cases/standard/01.01a-INVOICE_ubl.xml";

    /** The escape character, which a message must never carry into a terminal. */
    private static final char ESCAPE = 0x1B;

    @Test
    void findsNothingWrongWithAWellFormedContainer() {
        byte[] pdf = Pdfs.facturX(Conformance.instance(CII));

        List<ContainerFinding> findings = check(pdf);

        assertEquals(List.of("PDF-STRUCTURE-PDFA"), codes(findings));
        assertEquals(ContainerFinding.Severity.INFO, findings.get(0).severity());
        assertTrue(findings.get(0).message().contains("was not validated"),
                findings.get(0).message());
    }

    @Test
    void saysWhenTheInvoiceIsNotAnAssociatedFile() {
        byte[] content = Conformance.instance(CII);
        byte[] pdf = Pdfs.builder()
                .attach(new Pdfs.Attachment(Pdfs.FACTUR_X, content, Pdfs.XML, null, false,
                        content.length, false, null))
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        ContainerFinding finding = one(check(pdf), "PDF-AF-ABSENT");

        assertEquals(ContainerFinding.Category.PDF_AF, finding.category());
        assertEquals(ContainerFinding.Severity.ERROR, finding.severity());
    }

    @Test
    void saysWhenTheRelationshipIsNotTheOneAHybridInvoiceDeclares() {
        byte[] content = Conformance.instance(CII);
        byte[] pdf = Pdfs.builder()
                .attach(new Pdfs.Attachment(Pdfs.FACTUR_X, content, Pdfs.XML, "Supplement",
                        true, content.length, false, null))
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        ContainerFinding finding = one(check(pdf), "PDF-AF-RELATIONSHIP");

        assertEquals(ContainerFinding.Severity.WARNING, finding.severity());
        assertTrue(finding.message().contains("Supplement"), finding.message());
    }

    @Test
    void saysWhenTheXmpPacketNamesAnotherAttachment() {
        byte[] right = Pdfs.facturX("rechnung.xml", Conformance.instance(CII), "EN 16931");
        byte[] wrong = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .xmp(Pdfs.xmp("somewhere-else.xml", "EN 16931"))
                .build();

        assertFalse(codes(check(right)).contains("PDF-XMP-FILENAME"), codes(check(right)) + "");
        ContainerFinding finding = one(check(wrong), "PDF-XMP-FILENAME");
        assertEquals(ContainerFinding.Severity.ERROR, finding.severity());
        assertTrue(finding.message().contains("somewhere-else.xml"), finding.message());
    }

    @Test
    void saysWhenThereIsNoXmpPacketAtAll() {
        byte[] pdf = Pdfs.builder().attach(Pdfs.FACTUR_X, Conformance.instance(CII)).build();

        ContainerFinding finding = one(check(pdf), "PDF-XMP-ABSENT");

        assertEquals(ContainerFinding.Category.PDF_XMP, finding.category());
        assertEquals(ContainerFinding.Severity.WARNING, finding.severity());
    }

    @Test
    void saysWhenTheXmpPacketCarriesNoFacturXProperty() {
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .xmp("""
                        <x:xmpmeta xmlns:x="adobe:ns:meta/">
                          <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                            <rdf:Description rdf:about=""
                                xmlns:dc="http://purl.org/dc/elements/1.1/">
                              <dc:format>application/pdf</dc:format>
                            </rdf:Description>
                          </rdf:RDF>
                        </x:xmpmeta>
                        """)
                .build();

        ContainerFinding finding = one(check(pdf), "PDF-XMP-SCHEMA");

        assertEquals(ContainerFinding.Severity.WARNING, finding.severity());
    }

    @Test
    void saysWhenTheDeclaredMediaTypeIsNoXmlType() {
        byte[] content = Conformance.instance(CII);
        byte[] pdf = Pdfs.builder()
                .attach(new Pdfs.Attachment(Pdfs.FACTUR_X, content, "application/pdf",
                        Pdfs.ALTERNATIVE, true, content.length, false, null))
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        ContainerFinding finding = one(check(pdf), "PDF-EMBEDDED-MIME");

        assertEquals(ContainerFinding.Category.PDF_EMBEDDED, finding.category());
        assertTrue(finding.message().contains("application/pdf"), finding.message());
    }

    @Test
    void saysWhenTheConventionalNameHoldsSomethingElse() {
        byte[] spoofed = Pdfs.facturX(Pdfs.FACTUR_X, Conformance.instance(UBL), "EN 16931");
        byte[] notXml = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, "this is not xml".getBytes(StandardCharsets.UTF_8))
                .build();

        assertTrue(one(check(spoofed), "PDF-EMBEDDED-NAME").message().contains("UBL invoice"));
        assertTrue(one(check(notXml), "PDF-EMBEDDED-NAME").message().contains("not XML"));
    }

    @Test
    void saysSoWhenSeveralAttachmentsCouldBeTheInvoice() {
        // A container specification declares one invoice attachment. Two that both claim
        // to be it is a defect of the container whether or not a caller resolved it with
        // a selector, and the listing of the attachments is a listing and not a verdict.
        byte[] pdf = Pdfs.builder()
                .attach(Pdfs.FACTUR_X, Conformance.instance(CII))
                .attach("xrechnung.xml", Conformance.instance(UBL))
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        ContainerFinding finding = one(check(pdf), "PDF-EMBEDDED-SEVERAL");

        assertEquals(ContainerFinding.Severity.WARNING, finding.severity());
        assertTrue(finding.message().contains("2 attachments"), finding.message());
        assertTrue(finding.message().contains("xrechnung.xml"), finding.message());
    }

    @Test
    void saysWhenTheDeclaredSizeIsNotTheDecodedSize() {
        byte[] content = Conformance.instance(CII);
        byte[] pdf = Pdfs.builder()
                .attach(new Pdfs.Attachment(Pdfs.FACTUR_X, content, Pdfs.XML,
                        Pdfs.ALTERNATIVE, true, 42, false, null))
                .xmp(Pdfs.xmp(Pdfs.FACTUR_X, "EN 16931"))
                .build();

        ContainerFinding finding = one(check(pdf, EmbeddedFile::content), "PDF-EMBEDDED-SIZE");

        assertTrue(finding.message().contains("42"), finding.message());
    }

    @Test
    void saysWhenTheProfileTheContainerDeclaresIsNotTheProfileTheInvoiceWrites() {
        byte[] pdf = Pdfs.facturX(Pdfs.FACTUR_X, Conformance.instance(CII), "MINIMUM");

        PdfImportResult result = PdfInvoiceImporter.importPdf(pdf);

        ContainerFinding finding =
                result.pdf().finding("PDF-XMP-CONFORMANCE-MISMATCH").orElseThrow();
        assertEquals(ContainerFinding.Severity.ERROR, finding.severity());
        assertTrue(finding.message().contains("MINIMUM"), finding.message());
        // The instances of the corpus are XRechnung invoices, so the specification
        // identifier of this one names that profile and not the core of EN 16931.
        assertTrue(finding.message().contains("XRECHNUNG"), finding.message());
        assertTrue(result.pdf().hasErrors());
    }

    @Test
    void escapesAFragmentOfTheDocumentBeforePuttingItInAMessage() {
        String hostile = "in" + ESCAPE + "voice\"x.pdf";
        byte[] pdf = Pdfs.builder().attach(hostile, Conformance.instance(CII)).build();

        ContainerFinding finding = one(check(pdf), "PDF-EMBEDDED-NAME");

        assertTrue(finding.message().contains("\\u001B"), finding.message());
        assertTrue(finding.message().contains("\\\""), finding.message());
        assertEquals(-1, finding.message().indexOf(ESCAPE), finding.message());
    }

    private static List<ContainerFinding> check(byte[] pdf) {
        return check(pdf, file -> { });
    }

    private static List<ContainerFinding> check(byte[] pdf, Consumer<EmbeddedFile> before) {
        try (PdfContainer container = PdfContainer.open(pdf, PdfLimits.defaults())) {
            InvoiceAttachments located = InvoiceAttachments.locate(container);
            container.embeddedFiles().forEach(before);
            return ContainerChecks.run(container, located);
        }
    }

    private static List<String> codes(List<ContainerFinding> findings) {
        return findings.stream().map(ContainerFinding::code).toList();
    }

    private static ContainerFinding one(List<ContainerFinding> findings, String code) {
        return findings.stream()
                .filter(finding -> finding.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no finding " + code + " in " + codes(findings)));
    }
}
