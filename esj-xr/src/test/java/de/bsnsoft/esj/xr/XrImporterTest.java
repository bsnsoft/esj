package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportReport;
import de.bsnsoft.esj.imports.ImportResult;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The behaviour of the importer around its input: what it reads, what it refuses. */
class XrImporterTest {

    private static final String UBL = "business-cases/standard/01.01a-INVOICE_ubl.xml";
    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";

    private final XrImporter importer = new XrImporter();

    @Test
    void readsACreditNote() {
        ImportResult result = importer.importUblWithReport(Instances.bytes("/ubl/credit-note.xml"));
        SemanticDocument document = result.document();

        assertEquals(SemanticValue.of("CN-2026-0007"), value(document, "/BT-1"));
        assertEquals(SemanticValue.of("381"), value(document, "/BT-3"));
        assertEquals(SemanticValue.of("Example GmbH"), value(document, "/BG-4/BT-27"));
        assertEquals(SemanticValue.ofDecimal(new java.math.BigDecimal("2")),
                value(document, "/BG-25/0/BT-129"));
        assertEquals("UBL", document.source().orElseThrow().syntax().orElseThrow());
        assertEquals(List.of(), StructuralValidator.validate(document, importer.registry(),
                EnumSet.of(ValidationLayer.L2)).findings());
        assertTrue(result.report().isEmpty(), "nothing was left out: " + result.report().notes());
    }

    @Test
    void recognizesTheSyntaxOfADocument() {
        assertEquals("UBL", importer.importXml(Conformance.instance(UBL))
                .source().orElseThrow().syntax().orElseThrow());
        assertEquals("CII", importer.importXml(Conformance.instance(CII))
                .source().orElseThrow().syntax().orElseThrow());
    }

    @Test
    void refusesADocumentOfAnotherSyntaxThanTheOneAskedFor() {
        byte[] ubl = Conformance.instance(UBL);
        byte[] cii = Conformance.instance(CII);

        assertThrows(XrSyntaxException.class, () -> importer.importCii(ubl));
        assertThrows(XrSyntaxException.class, () -> importer.importUbl(cii));
    }

    @Test
    void refusesARootElementItDoesNotKnow() {
        byte[] document = utf8("<order xmlns=\"urn:example:orders\"><id>1</id></order>");

        XrSyntaxException thrown =
                assertThrows(XrSyntaxException.class, () -> importer.importXml(document));
        assertEquals("urn:example:orders", thrown.namespace());
        assertEquals("order", thrown.localName());
    }

    @Test
    void doesNotTakeTheXrRepresentationForASourceSyntax() {
        byte[] xr = Instances.bytes("/xr/notes.xml");

        assertThrows(XrSyntaxException.class, () -> importer.importXml(xr));
        assertEquals(XrImporter.XR_PROVENANCE,
                importer.fromXr(xr).source().orElseThrow().syntax().orElseThrow());
    }

    @Test
    void refusesSomethingThatIsNotTheXrRepresentation() {
        byte[] ubl = Conformance.instance(UBL);

        assertThrows(XrSyntaxException.class, () -> importer.fromXr(ubl));
    }

    /** The provenance digest is taken over the bytes that were handed in, not over the XR tree. */
    @Test
    void recordsTheDigestOfTheInput() {
        byte[] ubl = Conformance.instance(UBL);

        assertEquals(sha256(ubl), importer.importUbl(ubl).source().orElseThrow()
                .sha256().orElseThrow());
    }

    @Test
    void refusesADocumentTypeDeclaration() {
        byte[] document = utf8("<?xml version=\"1.0\"?><!DOCTYPE invoice ["
                + "<!ENTITY greeting \"hello\">]>"
                + "<invoice xmlns=\"urn:example:orders\">&greeting;</invoice>");

        assertThrows(XrFormatException.class, () -> importer.importXml(document));
    }

    @Test
    void resolvesNoExternalEntity() {
        byte[] document = utf8("<?xml version=\"1.0\"?><!DOCTYPE invoice ["
                + "<!ENTITY secret SYSTEM \"file:///etc/passwd\">]>"
                + "<invoice xmlns=\"urn:example:orders\">&secret;</invoice>");

        assertThrows(XrFormatException.class, () -> importer.importXml(document));
    }

    @Test
    void refusesBytesThatAreNotXml() {
        byte[] document = utf8("this is not a document");

        assertThrows(XrFormatException.class, () -> importer.importXml(document));
    }

    @Test
    void refusesAnInputLargerThanItsBound() {
        XrImporter small = new XrImporter(XrImporter.defaultRegistry(), 64);
        byte[] document = Conformance.instance(UBL);

        XrLimitException thrown =
                assertThrows(XrLimitException.class, () -> small.importXml(document));
        assertTrue(thrown.getMessage().contains("64"), thrown.getMessage());
    }

    @Test
    void rejectsAnImpossibleBound() {
        Registry registry = XrImporter.defaultRegistry();

        assertThrows(IllegalArgumentException.class, () -> new XrImporter(registry, 0));
    }

    /**
     * Every kind of note, on a document written to produce them.
     *
     * <p>What the importer leaves out is in the report, and what it keeps is everything the
     * format can hold — including a value whose mandatory supplementary component the source
     * did not write. That one is deliberately <em>not</em> a document the model layer
     * accepts: an importer does not decide whether a document is right, and an identifier
     * dropped on the way in is an identifier neither the structural layer nor the business
     * rules of the standard can report. So the note and the model finding stand together,
     * and the last assertion here is that the model layer reports exactly that one thing.
     */
    @Test
    void describesEverythingItCouldNotUse() {
        ImportResult result = importer.fromXrWithReport(Instances.bytes("/xr/notes.xml"));
        SemanticDocument document = result.document();
        ImportReport report = result.report();

        assertEquals(SemanticValue.of("RE-2026-0001"), value(document, "/BT-1"));
        assertEquals(SemanticValue.of("urn:example:first"),
                value(document, "/BG-2/BT-24"));
        assertEquals(SemanticValue.of("DE123456789"), value(document, "/BG-4/BT-31"));
        assertFalse(document.value(SemanticPath.of("/BT-2")).isPresent(), "no date was written");
        assertFalse(document.value(SemanticPath.of("/BT-3")).isPresent(), "no code was written");
        assertEquals(SemanticValue.of("office@example.test"), value(document, "/BG-4/BT-34"),
                "the address is kept, and that it names no scheme is the validator's finding");

        assertEquals(List.of("BT-9999"), locations(report, ImportNote.Kind.UNKNOWN_TERM));
        assertEquals(List.of("BT-146"), locations(report, ImportNote.Kind.UNPLACEABLE));
        assertEquals(List.of("/BT-2", "/BG-22/BT-112"), locations(report, ImportNote.Kind.MALFORMED));
        assertEquals(List.of("/BT-3"), locations(report, ImportNote.Kind.EMPTY));
        assertEquals(List.of("/BG-2/BT-24"), locations(report, ImportNote.Kind.DUPLICATE_PATH));
        assertEquals(List.of("/BG-4/BT-31"), locations(report, ImportNote.Kind.COMPONENT_DROPPED));
        assertEquals(List.of("/BG-4/BT-34"), locations(report, ImportNote.Kind.COMPONENT_MISSING));

        List<Finding> model = StructuralValidator.validate(document, importer.registry(),
                EnumSet.of(ValidationLayer.L2)).findings();
        assertEquals(List.of("ESJ-L2-COMPONENT-MISSING at /BG-4/BT-34"),
                model.stream().map(finding -> finding.code().code() + " at " + finding.path())
                        .toList(),
                "the one thing the importer kept and the model layer refuses");
    }

    /**
     * An amount the source wrote with more fraction digits than the type carries.
     *
     * <p>The canonical decimal form writes no trailing zero, so {@code 1090.900} and
     * {@code 1090.9} are one value and the document holds the shorter one. Nothing is wrong
     * with the number, and everything downstream that asks how many fraction digits the
     * sender wrote — the decimal rules of EN 16931 are the family that does — can no longer
     * tell. So the importer says it at the path it happened.
     */
    @Test
    void saysWhereItCouldNotCarryTheFractionDigitsOfAnAmount() {
        byte[] source = Conformance.instance(CII);
        String written = new String(source, StandardCharsets.UTF_8)
                .replace("<ram:LineTotalAmount>314.86</ram:LineTotalAmount>",
                        "<ram:LineTotalAmount>314.860</ram:LineTotalAmount>");

        ImportResult result =
                importer.importXmlWithReport(written.getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of("/BG-22/BT-106"),
                locations(result.report(), ImportNote.Kind.SCALE_REDUCED));
        assertEquals(List.of(ImportNote.Level.WARNING),
                result.report().notes(ImportNote.Kind.SCALE_REDUCED).stream()
                        .map(ImportNote::level).toList(),
                "the notation did not reach the document, so a reader is told without --verbose");
    }

    /** An amount whose extra digit is not a zero keeps it, and nothing is remarked. */
    @Test
    void keepsAFractionDigitThatIsNotAZero() {
        byte[] source = Conformance.instance(CII);
        String written = new String(source, StandardCharsets.UTF_8)
                .replace("<ram:LineTotalAmount>314.86</ram:LineTotalAmount>",
                        "<ram:LineTotalAmount>314.861</ram:LineTotalAmount>");

        ImportResult result =
                importer.importXmlWithReport(written.getBytes(StandardCharsets.UTF_8));

        assertEquals(SemanticValue.ofDecimal(new java.math.BigDecimal("314.861")),
                value(result.document(), "/BG-22/BT-106"));
        assertEquals(List.of(), locations(result.report(), ImportNote.Kind.SCALE_REDUCED));
    }

    private static List<String> locations(ImportReport report, ImportNote.Kind kind) {
        return report.notes(kind).stream().map(ImportNote::location).toList();
    }

    private static SemanticValue value(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path))
                .orElseThrow(() -> new AssertionError("the document has no value at " + path));
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
