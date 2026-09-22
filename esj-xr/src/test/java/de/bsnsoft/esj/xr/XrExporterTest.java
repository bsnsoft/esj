package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The exporter on documents written for one question each: how a component is written, how
 * a group the XR representation flattens is placed, what happens to content it cannot
 * carry, and what the report says about each of them.
 *
 * <p>The round trip over real invoices is {@link XrRoundTripTest}; these are the cases the
 * conformance corpus has no instance of, because a document produced from UBL or CII never
 * has them.
 */
class XrExporterTest {

    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

    private static final String OPEN =
            "<xr:invoice xmlns:xr=\"urn:ce.eu:en16931:2017:xoev-de:kosit:standard:xrechnung-1\">\n";

    @Test
    void writesTheElementsOfTheSchemaInTheOrderOfTheSchema() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-22/BT-112", "119")
                .put("/BT-1", "RE-1")
                .put("/BG-22/BT-106", "100")
                .build();

        assertEquals(HEADER + OPEN
                        + "   <xr:Invoice_number xr:id=\"BT-1\">RE-1</xr:Invoice_number>\n"
                        + "   <xr:DOCUMENT_TOTALS xr:id=\"BG-22\">\n"
                        + "      <xr:Sum_of_Invoice_line_net_amount xr:id=\"BT-106\">100"
                        + "</xr:Sum_of_Invoice_line_net_amount>\n"
                        + "      <xr:Invoice_total_amount_with_VAT xr:id=\"BT-112\">119"
                        + "</xr:Invoice_total_amount_with_VAT>\n"
                        + "   </xr:DOCUMENT_TOTALS>\n"
                        + "</xr:invoice>\n",
                xr(document));
    }

    @Test
    void writesADocumentWithoutValuesAsAnEmptyRoot() {
        assertEquals(HEADER + "<xr:invoice xmlns:xr=\"urn:ce.eu:en16931:2017:xoev-de:kosit:"
                        + "standard:xrechnung-1\"/>\n",
                xr(SemanticDocument.builder().build()));
    }

    @Test
    void writesTheSameDocumentTheSameWayTwice() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", "RE-1")
                .put("/BG-4/BT-29/0", SemanticValue.identifier("0197842701377", "0088"))
                .put("/BG-4/BT-29/1", SemanticValue.identifier("12345", "0002"))
                .build();
        XrExporter exporter = new XrExporter();

        assertArrayEquals(exporter.toXr(document), exporter.toXr(document));
    }

    @Test
    void writesTheSchemeOfAnIdentifierAsTheAttributeTheSchemaDefines() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-2/BT-24", SemanticValue.identifier("urn:cen.eu:en16931:2017", "CEN", "1"))
                .build();

        assertTrue(xr(document).contains("<xr:Specification_identifier xr:id=\"BT-24\""
                        + " scheme_identifier=\"CEN\" scheme_version_identifier=\"1\">"
                        + "urn:cen.eu:en16931:2017</xr:Specification_identifier>"),
                xr(document));
    }

    @Test
    void writesTheMediaTypeAndTheFileNameOfABinaryObject() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-24/0/BT-122", "REF-1")
                .put("/BG-24/0/BT-125", SemanticValue.binary(
                        "hello".getBytes(StandardCharsets.UTF_8), "text/plain", "note.txt"))
                .build();

        assertTrue(xr(document).contains("<xr:Attached_document xr:id=\"BT-125\""
                        + " mime_code=\"text/plain\" filename=\"note.txt\">aGVsbG8="
                        + "</xr:Attached_document>"),
                xr(document));
    }

    @Test
    void namesAComponentTheElementOfThatTermCannotCarry() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-3", SemanticValue.identifier("380", "UNTDID"))
                .build();

        ExportResult exported = new XrExporter().toXrWithReport(document);

        assertEquals(1, exported.report().notes(ExportNote.Kind.COMPONENT_DROPPED).size());
        assertEquals("/BT-3", exported.report().notes().get(0).location());
        assertTrue(text(exported).contains("<xr:Invoice_type_code xr:id=\"BT-3\">380<"),
                "the value itself is written; only the component is dropped");
    }

    @Test
    void escapesTheCharactersXmlGivesAMeaningTo() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-1/0/BT-22", "a & b < c > d \" e")
                .build();

        assertTrue(xr(document).contains(
                        ">a &amp; b &lt; c &gt; d \" e</xr:Invoice_note>"),
                xr(document));
    }

    @Test
    void keepsTheLineBreaksAndTheSpacesOfATextValue() {
        SemanticValue note = SemanticValue.of("  first line\nsecond line  ");
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-1/0/BT-22", note)
                .build();

        assertTrue(xr(document).contains(
                        ">  first line\nsecond line  </xr:Invoice_note>"), xr(document));
        assertEquals(note, reread(document).value(SemanticPath.of("/BG-1/0/BT-22")).orElseThrow());
    }

    @Test
    void leavesOutAValueWhoseContentXmlCannotCarry() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", "RE-1")
                .put("/BG-1/0/BT-22", "a bell: ")
                .build();

        ExportResult exported = new XrExporter().toXrWithReport(document);

        assertEquals(List.of(new ExportNote(ExportNote.Kind.NOT_REPRESENTABLE, "/BG-1/0/BT-22",
                        "the content of BT-22 carries a character XML 1.0 cannot hold, so the"
                                + " value was left out")),
                exported.report().notes());
        assertTrue(text(exported).contains("RE-1"), "the rest of the document is written");
        assertTrue(!text(exported).contains("INVOICE_NOTE"),
                "the group that held nothing else is not written either");
    }

    @Test
    void writesAGroupTheXrRepresentationFlattensWhereThatRepresentationPutsIt() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-13/BG-14/BT-73", "2026-01-01")
                .build();

        assertEquals(HEADER + OPEN
                        + "   <xr:INVOICING_PERIOD xr:id=\"BG-14\">\n"
                        + "      <xr:Invoicing_period_start_date xr:id=\"BT-73\">2026-01-01"
                        + "</xr:Invoicing_period_start_date>\n"
                        + "   </xr:INVOICING_PERIOD>\n"
                        + "</xr:invoice>\n",
                xr(document),
                "BG-14 lies inside BG-13 in the standard and beside it in the XR"
                        + " representation, and the delivery information holds nothing here");
        assertEquals(document.values(), reread(document).values());
    }

    @Test
    void writesTheOccurrencesOfARepeatedTermInIndexOrder() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-4/BT-29/0", "first")
                .put("/BG-4/BT-29/1", "second")
                .build();

        assertTrue(xr(document).indexOf(">first<") < xr(document).indexOf(">second<"));
        assertEquals(document.values(), reread(document).values());
    }

    @Test
    void namesTheOccurrencesBehindAGapInTheIndices() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/2/BT-126", "3")
                .build();

        ExportResult exported = new XrExporter().toXrWithReport(document);

        assertEquals(1, exported.report().notes(ExportNote.Kind.NO_ELEMENT).size());
        assertEquals("/BG-25/2/BT-126", exported.report().notes().get(0).location());
    }

    @Test
    void namesAValueAtAPositionTheRegistryDoesNotRecord() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-4/BT-1", "RE-1")
                .build();

        ExportResult exported = new XrExporter().toXrWithReport(document);

        assertEquals(1, exported.report().notes(ExportNote.Kind.NO_ELEMENT).size());
        assertTrue(exported.report().notes().get(0).message().contains("BT-1"));
    }

    @Test
    void namesAnExtensionTermAnExporterWithoutThatRegistryCannotPlace() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BG-DEX-01/0/BT-126", "1.1")
                .build();

        ExportResult core = new XrExporter(Registry.en16931()).toXrWithReport(document);
        ExportResult combined = new XrExporter().toXrWithReport(document);

        assertEquals(List.of("/BG-25/0/BG-DEX-01/0/BT-126"),
                core.report().notes().stream().map(ExportNote::location).toList());
        assertTrue(core.report().notes().get(0).message().contains("no registry"),
                core.report().notes().get(0).message());
        assertEquals(List.of(), combined.report().notes(),
                "the default registry knows the extension and writes the sub invoice line");
    }

    @Test
    void namesTheExtensionSubtreeItCannotCarry() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", "RE-1")
                .extension("de.example.vendor", ExtensionValue.of("kept elsewhere"))
                .build();

        ExportResult exported = new XrExporter().toXrWithReport(document);

        assertEquals(List.of(new ExportNote(ExportNote.Kind.EXTENSIONS_DROPPED,
                        "de.example.vendor",
                        "the XR representation carries business terms and nothing else, so the"
                                + " extension subtree of this owner was left out")),
                exported.report().notes());
    }

    @Test
    void writesTheNoteSubjectCodeAsAnElementOfItsOwn() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-1/0/BT-21", "AAB")
                .put("/BG-1/0/BT-22", "#AAB#the note as UBL would carry it")
                .build();

        assertTrue(xr(document).contains(
                        "<xr:Invoice_note_subject_code xr:id=\"BT-21\">AAB"
                                + "</xr:Invoice_note_subject_code>"),
                xr(document));
        assertEquals(document.values(), reread(document).values(),
                "the importer of an XR document does not split a note, so the two terms"
                        + " come back as they went in");
    }

    @Test
    void refusesADocumentOfAnEditionItsRegistryDoesNotDescribe() {
        SemanticDocument document = SemanticDocument.builder()
                .semanticModel("EN16931-1:2099")
                .put("/BT-1", "RE-1")
                .build();

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new XrExporter().toXr(document));

        assertTrue(refusal.getMessage().contains("EN16931-1:2099"), refusal.getMessage());
    }

    @Test
    void writesADocumentAnImporterWithoutTheExtensionReadsAsCoreTermsOnly() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BG-DEX-01/0/BT-126", "1.1")
                .build();

        SemanticDocument read = new XrImporter(Registry.en16931(),
                XrImporter.DEFAULT_MAX_INPUT_BYTES)
                .fromXr(new XrExporter().toXr(document));

        assertEquals(List.of(SemanticPath.of("/BG-25/0/BT-126")),
                List.copyOf(read.values().keySet()));
    }

    private static String xr(SemanticDocument document) {
        return new String(new XrExporter().toXr(document), StandardCharsets.UTF_8);
    }

    private static String text(ExportResult exported) {
        return new String(exported.xr(), StandardCharsets.UTF_8);
    }

    private static SemanticDocument reread(SemanticDocument document) {
        return new XrImporter().fromXr(new XrExporter().toXr(document));
    }
}
