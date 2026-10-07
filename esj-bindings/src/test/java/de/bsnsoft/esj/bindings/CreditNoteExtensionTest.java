package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.syntax.SyntaxFinding;
import de.bsnsoft.esj.syntax.SyntaxValidator;
import de.bsnsoft.esj.xml.InvoiceSyntax;
import de.bsnsoft.esj.xr.XrImporter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The sub credit note line of the XRechnung extension.
 *
 * <p>The tailoring model of the extension binds the sub invoice line for UBL Invoice alone,
 * and {@code model/bindings/ubl-creditnote.json} carries it as two corrections. The document
 * of {@code conformance/creditnote/} is KoSIT test case 04.01a written as a credit note: the
 * same values, a credit note type code, and the elements the credit note names differently.
 * Read as a credit note it has to say what the invoice says.
 */
class CreditNoteExtensionTest {

    private static final String XML = Corpus.ROOT + "creditnote/04.01a-CREDITNOTE_ubl.xml";

    private static final String ESJ = Corpus.ROOT + "creditnote/04.01a-CREDITNOTE_ubl.esj.json";

    /** The invoice the credit note was written from, as the corpus holds it. */
    private static final String INVOICE = "business-cases/extension/04.01a-INVOICE_ubl.xml";

    @Test
    void readsTheSubLinesAtEveryDepth() {
        SemanticDocument document = read();
        assertEquals("1 1", content(document, "/BG-25/0/BG-DEX-01/0/BT-126"));
        assertEquals("335.79", content(document, "/BG-25/0/BG-DEX-01/0/BT-131"));
        assertEquals("1 1 2", content(document, "/BG-25/0/BG-DEX-01/0/BG-DEX-01/1/BT-126"));
        assertEquals("32.5",
                content(document, "/BG-25/0/BG-DEX-01/0/BG-DEX-01/1/BT-129"),
                "the quantity of a sub credit note line is its cbc:CreditedQuantity");
        assertEquals("MTR", content(document, "/BG-25/0/BG-DEX-01/0/BG-DEX-01/1/BT-130"));
        assertEquals("S",
                content(document, "/BG-25/1/BG-DEX-01/1/BG-DEX-01/0/BG-DEX-06/BT-151"));
        assertEquals(130, document.values().keySet().stream()
                .filter(path -> path.toString().contains("/BG-DEX-01/")).count());
    }

    /** The litmus test: the invoice and the credit note of the same data, the same values. */
    @Test
    void theCreditNoteSaysWhatTheInvoiceSays() {
        SemanticDocument invoice = EsjReader.strict().read(Corpus.esj(INVOICE));
        SemanticDocument creditNote = read();
        assertEquals(List.of("/BT-3"), differences(invoice, creditNote),
                "the two differ in the invoice type code and nowhere else");
        assertEquals("381", content(creditNote, "/BT-3"));
    }

    /** The checked-in document is what this reader writes, as it is for the corpus. */
    @Test
    void theCheckedInDocumentIsWhatThisReaderWrites() {
        assertArrayEquals(Corpus.bytes(ESJ), EsjWriter.pretty().toBytes(read()),
                "conformance/creditnote carries what the streaming reader builds");
    }

    /**
     * ESJ to UBL Credit Note and back: the sub lines are written as nested
     * {@code cac:SubCreditNoteLine} elements with a {@code cbc:CreditedQuantity}, the
     * official artefacts accept the result, and reading it gives the document back.
     */
    @Test
    void writesTheSubLinesAsSubCreditNoteLinesAndReadsThemBack() {
        SemanticDocument document = EsjReader.strict().read(Corpus.bytes(ESJ));
        WriteResult result = UblWriter.writeWithReport(document, WriterOptions.defaults());
        assertEquals(InvoiceSyntax.UBL_CREDIT_NOTE, result.report().syntax());
        assertTrue(result.report().isComplete(), result.report().notes().toString());
        String xml = new String(result.xml(), StandardCharsets.UTF_8);
        assertEquals(13, count(xml, "<cac:SubCreditNoteLine>"),
                "thirteen sub lines over two levels");
        assertFalse(xml.contains("SubInvoiceLine"), "no element of the invoice");
        assertFalse(xml.contains("InvoicedQuantity"), "no element of the invoice");
        List<SyntaxFinding> fatal = SyntaxValidator.validate(result.xml()).fatal();
        assertEquals(List.of(), fatal.stream().map(SyntaxFinding::code).toList(),
                "XSD and Schematron of the pack accept the written credit note");
        assertEquals(List.of(), differences(document, reader().read(result.xml()).document()),
                "the document comes back unchanged");
    }

    /**
     * The XSLT path of {@code esj-xr} over the same document. The vendored KoSIT stylesheet
     * reads the terms of a sub credit note line from absolute paths of the credit note line
     * — so every sub line carries the values of the first credit note line of the document —
     * does not descend into a nested sub line, and matches the VAT group on an element the
     * schema does not have. Every difference lies inside a sub line; the core terms agree.
     * {@code conformance/creditnote/README.md} and {@code conformance/readers.md} record it.
     */
    @Test
    void theXsltPathPartsInsideTheSubLinesAndNowhereElse() {
        SemanticDocument streaming = read();
        SemanticDocument stylesheets = new XrImporter().read(Corpus.bytes(XML)).document();
        List<String> differing = differences(streaming, stylesheets);
        assertEquals(114, differing.size());
        assertTrue(differing.stream().allMatch(path -> path.contains("/BG-DEX-01/")),
                differing.toString());
        assertEquals("1", content(stylesheets, "/BG-25/1/BG-DEX-01/0/BT-126"),
                "the stylesheet gives a sub line of the second line the first line's number");
        assertEquals("818.04", content(stylesheets, "/BG-25/1/BG-DEX-01/0/BT-131"));
    }

    private static StreamingReader reader() {
        return new StreamingReader();
    }

    private static SemanticDocument read() {
        return reader().read(Corpus.bytes(XML)).document();
    }

    private static String content(SemanticDocument document, String path) {
        SemanticValue value = document.values().get(SemanticPath.of(path));
        return value == null ? null : value.canonicalContent();
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }

    private static List<String> differences(SemanticDocument before, SemanticDocument after) {
        Map<SemanticPath, SemanticValue> a = before.values();
        Map<SemanticPath, SemanticValue> b = after.values();
        Set<SemanticPath> all = new TreeSet<>(a.keySet());
        all.addAll(b.keySet());
        List<String> differences = new ArrayList<>();
        for (SemanticPath path : all) {
            if (!Objects.equals(a.get(path), b.get(path))) {
                differences.add(path.toString());
            }
        }
        return differences;
    }
}
