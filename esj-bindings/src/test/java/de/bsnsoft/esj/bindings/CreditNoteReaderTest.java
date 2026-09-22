package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The one UBL Credit Note of the repository, read both ways.
 *
 * <p>The conformance corpus is 41 cross industry invoices and 45 UBL Invoices, so without
 * this document {@code model/bindings/ubl-creditnote.json} — one of the three tables of
 * this module — would be exercised by no test at all. {@code conformance/creditnote/}
 * holds the document, what it is for and what the two readers make of it; this test is
 * what holds the table to it.
 */
class CreditNoteReaderTest {

    private static final String XML = Corpus.ROOT + "creditnote/credit-note_ubl.xml";

    private static final String ESJ = Corpus.ROOT + "creditnote/credit-note_ubl.esj.json";

    /**
     * The four defects the table carried until a credit note existed to read, each at the
     * value it now produces. They are spelled out here rather than left to the golden,
     * because a golden that changes says only that something moved.
     */
    @Test
    void readsTheTermsTheTableUsedToLose() {
        Map<String, SemanticValue> values = byPath(read());
        assertEquals("PR-2026-0007", content(values, "/BT-11"),
                "BT-11 is the document reference whose type code is 50, not the first one");
        assertEquals("OBJ-42", content(values, "/BT-18"),
                "BT-18 is the document reference whose type code is 130");
        assertEquals("AAJ", values.get("/BT-18").scheme());
        assertEquals("DE987654321", content(values, "/BG-7/BT-48"),
                "BT-48 is the buyer's value added tax registration, not its tax number");
        assertEquals("DE555666777", content(values, "/BG-11/BT-63"));
        assertEquals("S", content(values, "/BG-23/0/BT-118"));
        assertEquals("S", content(values, "/BG-25/0/BG-30/BT-151"));
        assertEquals("ATT-1", content(values, "/BG-24/0/BT-122"),
                "the supporting document group is the reference that is neither of those"
                        + " two, and it is the first one");
        assertEquals(null, content(values, "/BG-24/1/BT-122"),
                "and it is the only one");
    }

    /** The checked-in document is what this reader writes, as it is for the corpus. */
    @Test
    void theCheckedInDocumentIsWhatThisReaderWrites() {
        assertArrayEquals(Corpus.bytes(ESJ), EsjWriter.pretty().toBytes(read()),
                "conformance/creditnote carries what the streaming reader builds");
    }

    @Test
    void writesADocumentTheValidatorAccepts() {
        List<Finding> findings = StructuralValidator.validate(read(),
                new StreamingReader().options().registry(), Set.of(ValidationLayer.L2))
                .findings();
        assertTrue(findings.isEmpty(), findings.toString());
    }

    /**
     * The two readers over the same credit note. They agree on every business term but the
     * occurrence indices of BG-24: the table keeps the group off the two document
     * references whose document type code is 130 or 50, because those carry BT-18 and
     * BT-11, and the stylesheet leaves out only the one whose code is 50. The stylesheet
     * therefore reads the invoiced object identifier as a supporting document as well, and
     * the index of the real one moves. The difference is written out here and explained in
     * {@code conformance/creditnote/README.md} and in
     * {@code conformance/bindings/crosscheck.md}; a difference anywhere else fails.
     */
    @Test
    void thetwoReadersDifferOnlyWhereTheRecordSaysSo() {
        SemanticDocument streaming = read();
        SemanticDocument stylesheets = new XrImporter().importXml(Corpus.bytes(XML));
        Set<SemanticPath> all = new TreeSet<>(streaming.values().keySet());
        all.addAll(stylesheets.values().keySet());
        List<String> differing = new ArrayList<>();
        for (SemanticPath path : all) {
            SemanticValue mine = streaming.values().get(path);
            SemanticValue theirs = stylesheets.values().get(path);
            if (mine == null || theirs == null || !mine.equals(theirs)) {
                differing.add(path.toString());
            }
        }
        assertEquals(List.of("/BG-24/0/BT-122", "/BG-24/0/BT-123", "/BG-24/1/BT-122",
                        "/BG-24/1/BT-123"), differing,
                "the two readers part on the supporting document group and nowhere else");
    }

    private static SemanticDocument read() {
        return new StreamingReader().read(Corpus.bytes(XML)).document();
    }

    private static Map<String, SemanticValue> byPath(SemanticDocument document) {
        Map<String, SemanticValue> values = new TreeMap<>();
        document.values().forEach((path, value) -> values.put(path.toString(), value));
        return values;
    }

    private static String content(Map<String, SemanticValue> values, String path) {
        SemanticValue value = values.get(path);
        return value == null ? null : value.canonicalContent();
    }
}
