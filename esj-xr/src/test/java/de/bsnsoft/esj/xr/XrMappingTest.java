package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportResult;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Checks the paths and values of one business case of the test suite, hand read from the
 * two source documents, and the two places where the shape of the XR representation and
 * the shape of the standard differ.
 */
class XrMappingTest {

    private static final String UBL = "business-cases/standard/01.01a-INVOICE_ubl.xml";
    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";
    private static final String EXTENSION = "business-cases/extension/04.01a-INVOICE_ubl.xml";
    private static final String ATTACHMENT_UBL = "business-cases/standard/01.15a-INVOICE_ubl.xml";
    private static final String ATTACHMENT_CII =
            "business-cases/standard/01.15a-INVOICE_uncefact.xml";

    private static final String LINE_AMOUNTS =
            "<cbc:InvoicedQuantity unitCode=\"C62\">1</cbc:InvoicedQuantity>"
                    + "<cbc:LineExtensionAmount currencyID=\"EUR\">1.00</cbc:LineExtensionAmount>";

    private static final String PRICE = "<cac:Price><cbc:PriceAmount currencyID=\"EUR\">1.00"
            + "</cbc:PriceAmount></cac:Price>";

    private final XrImporter importer = new XrImporter();

    @Test
    void readsTheUblInvoice() {
        SemanticDocument document = importer.importUbl(Conformance.instance(UBL));

        assertEquals(SemanticValue.of("123456XX"), value(document, "/BT-1"));
        assertEquals(SemanticValue.ofDate(LocalDate.of(2016, 4, 4)), value(document, "/BT-2"));
        assertEquals(SemanticValue.of("380"), value(document, "/BT-3"));
        assertEquals(SemanticValue.of("EUR"), value(document, "/BT-5"));
        assertEquals(SemanticValue.of("[Seller name]"), value(document, "/BG-4/BT-27"));
        assertEquals(SemanticValue.identifier("seller@email.de", "EM"),
                value(document, "/BG-4/BT-34"));
        assertEquals(SemanticValue.of("[Buyer name]"), value(document, "/BG-7/BT-44"));
        assertEquals(SemanticValue.of("DE"), value(document, "/BG-4/BG-5/BT-40"));

        assertEquals(SemanticValue.ofDecimal(new BigDecimal("288.79")),
                value(document, "/BG-25/0/BT-131"));
        assertEquals(SemanticValue.ofDecimal(new BigDecimal("26.07")),
                value(document, "/BG-25/1/BT-131"));
        assertEquals(SemanticValue.ofDecimal(BigDecimal.ONE),
                value(document, "/BG-25/0/BT-129"));
        assertEquals(SemanticValue.of("XPP"), value(document, "/BG-25/0/BT-130"));
        assertEquals(SemanticValue.ofDecimal(new BigDecimal("288.79")),
                value(document, "/BG-25/0/BG-29/BT-146"));
        assertEquals(SemanticValue.of("S"), value(document, "/BG-25/0/BG-30/BT-151"));
        assertEquals(SemanticValue.ofDecimal(new BigDecimal("7")),
                value(document, "/BG-25/0/BG-30/BT-152"));
        assertEquals(SemanticValue.ofDecimal(new BigDecimal("7")),
                value(document, "/BG-23/0/BT-119"));
    }

    /**
     * The same business case in the other syntax. The two documents are written from the
     * same data, so the terms that both syntaxes carry arrive with the same values,
     * although nothing in the importer knows one element name of either.
     */
    @Test
    void readsTheSameInvoiceFromCii() {
        SemanticDocument ubl = importer.importUbl(Conformance.instance(UBL));
        SemanticDocument cii = importer.importCii(Conformance.instance(CII));

        for (String path : List.of("/BT-1", "/BT-2", "/BT-3", "/BT-5", "/BG-4/BT-27",
                "/BG-4/BT-34", "/BG-4/BG-5/BT-40", "/BG-7/BT-44", "/BG-25/0/BT-131",
                "/BG-25/1/BT-131", "/BG-25/0/BG-29/BT-146", "/BG-25/0/BG-30/BT-152",
                "/BG-22/BT-112", "/BG-23/0/BT-116")) {
            assertEquals(value(ubl, path), value(cii, path), "both syntaxes give " + path);
        }
        assertEquals(SemanticValue.of("ADU"), value(cii, "/BG-1/0/BT-21"));
        assertEquals("CII", cii.source().orElseThrow().syntax().orElseThrow());
    }

    /**
     * The CII document writes the scheme the syntax binding gives a VAT identifier. The
     * standard gives BT-31 no scheme component, so the value keeps its content and loses
     * the scheme, and the report says which term that happened at.
     */
    @Test
    void dropsASchemeTheStandardDoesNotGiveTheTerm() {
        ImportResult result = importer.importCiiWithReport(Conformance.instance(CII));

        assertEquals(SemanticValue.of("DE 123456789"),
                value(result.document(), "/BG-4/BT-31"));
        assertTrue(result.report().notes(ImportNote.Kind.COMPONENT_DROPPED).stream()
                        .anyMatch(note -> note.location().equals("/BG-4/BT-31")),
                "the dropped scheme is in the report: " + result.report().notes());
    }

    /**
     * The XR representation writes BG-14 beside BG-13; the standard puts it inside. The
     * importer follows the standard and opens the delivery information implicitly, which
     * it may do because a delivery information occurs at most once.
     */
    @Test
    void putsTheInvoicingPeriodWhereTheStandardPutsIt() {
        SemanticDocument document = importer.importUbl(Conformance.instance(EXTENSION));

        assertEquals(SemanticValue.ofDate(LocalDate.of(2019, 2, 1)),
                value(document, "/BG-13/BG-14/BT-73"));
        assertEquals(SemanticValue.ofDate(LocalDate.of(2019, 5, 7)),
                value(document, "/BG-13/BG-14/BT-74"));
        assertFalse(document.value(SemanticPath.of("/BG-14/BT-73")).isPresent(),
                "the path of the XR tree is not the path of the document");
    }

    /**
     * An extension group is read like any other group, because the registry of the
     * extension is loaded beside the core registry and answers the same questions.
     */
    @Test
    void readsTheExtensionGroups() {
        SemanticDocument document = importer.importUbl(Conformance.instance(EXTENSION));

        assertEquals(SemanticValue.of("1 1"),
                value(document, "/BG-25/0/BG-DEX-01/0/BT-126"));
        assertEquals(SemanticValue.ofDecimal(new BigDecimal("335.79")),
                value(document, "/BG-25/0/BG-DEX-01/0/BT-131"));
        assertEquals(SemanticValue.ofDecimal(new BigDecimal("335.79")),
                value(document, "/BG-25/0/BG-DEX-01/0/BG-DEX-07/BT-146"));
        assertEquals(SemanticValue.of("Demontage Sanitär"),
                value(document, "/BG-25/0/BG-DEX-01/0/BG-DEX-02/BT-153"));
    }

    /**
     * Without the extension registry the extension groups are not values but notes, and
     * the core terms of the document arrive as before.
     */
    @Test
    void leavesExtensionGroupsOutWithoutTheirRegistry() {
        XrImporter core = new XrImporter(de.bsnsoft.esj.model.Registry.en16931(),
                XrImporter.DEFAULT_MAX_INPUT_BYTES);
        ImportResult result = core.importUblWithReport(Conformance.instance(EXTENSION));

        assertEquals(SemanticValue.of("12345"),
                value(result.document(), "/BT-1"));
        assertFalse(result.report().notes(ImportNote.Kind.UNKNOWN_TERM).isEmpty(),
                "the extension groups are reported as unknown");
        assertTrue(result.document().values().keySet().stream()
                        .noneMatch(SemanticPath::hasExtensionSegment),
                "no extension path reaches the document");
    }

    /**
     * A sub invoice line inside a sub invoice line, as deep as a reader running the
     * default limits can read it back. Six levels spend twelve of the sixteen segments a
     * path may carry, and the values of the sixth level still fit.
     */
    @Test
    void nestsTheSubInvoiceLineAsDeepAsAReaderReadsItBack() {
        ImportResult result = importer.importUblWithReport(subInvoiceLines(6));
        SemanticDocument document = result.document();

        assertEquals(SemanticValue.of("level 6"),
                value(document, "/BG-25/0" + "/BG-DEX-01/0".repeat(6) + "/BG-DEX-02/BT-153"));
        assertEquals(List.of(), result.report().notes(ImportNote.Kind.PATH_TOO_LONG),
                "nothing was too deep to write");
        assertEquals(document, EsjReader.strict().read(Canonicalizer.canonicalBytes(document)),
                "a reader running the defaults reads the document back");
    }

    /**
     * The seventh level does not fit, and the document says so instead of carrying a path
     * that a reader running the defaults refuses. The level is skipped whole, with
     * everything below it, and the note names the path it would have taken.
     */
    @Test
    void leavesOutWhatWouldNotFitIntoAPathAndSaysSo() {
        ImportResult result = importer.importUblWithReport(subInvoiceLines(7));
        SemanticDocument document = result.document();
        String seventh = "/BG-25/0" + "/BG-DEX-01/0".repeat(7);

        assertEquals(SemanticValue.of("level 6"),
                value(document, "/BG-25/0" + "/BG-DEX-01/0".repeat(6) + "/BG-DEX-02/BT-153"));
        assertFalse(document.value(SemanticPath.of(seventh + "/BG-DEX-02/BT-153")).isPresent(),
                "the seventh level reached no path");
        assertEquals(List.of(seventh),
                result.report().notes(ImportNote.Kind.PATH_TOO_LONG).stream()
                        .map(ImportNote::location).toList(),
                "one note names the level that was left out");
        assertEquals(document, EsjReader.strict().read(Canonicalizer.canonicalBytes(document)),
                "what was written is a document a reader running the defaults reads back");
    }

    /**
     * The bound belongs to the reader the document is written for, not to the importer. A
     * caller that reads with a wider profile imports with it and gets the deeper levels.
     */
    @Test
    void followsTheDeeperLevelsForAReaderThatAcceptsThem() {
        Limits wide = Limits.defaults().toBuilder().maxPathSegments(32).maxPathBytes(512).build();
        XrImporter deep =
                new XrImporter(XrImporter.defaultRegistry(), XrImporter.DEFAULT_MAX_INPUT_BYTES, wide);

        ImportResult result = deep.importUblWithReport(subInvoiceLines(7));

        assertEquals(wide, deep.readerLimits());
        assertEquals(SemanticValue.of("level 7"),
                value(result.document(), "/BG-25/0" + "/BG-DEX-01/0".repeat(7) + "/BG-DEX-02/BT-153"));
        assertEquals(List.of(), result.report().notes(ImportNote.Kind.PATH_TOO_LONG),
                "nothing was too deep for this profile");
    }

    /**
     * Builds a UBL invoice whose one invoice line carries the given number of sub invoice
     * lines inside one another, each of them with an item whose name says which level it
     * is.
     */
    private static byte[] subInvoiceLines(int levels) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:"
                + "CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:"
                + "CommonBasicComponents-2\">"
                + "<cbc:ID>RE-2026-0001</cbc:ID><cbc:IssueDate>2026-01-01</cbc:IssueDate>"
                + "<cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>"
                + "<cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>"
                + "<cac:InvoiceLine><cbc:ID>1</cbc:ID>" + LINE_AMOUNTS
                + subInvoiceLine(1, levels)
                + item("the line") + PRICE
                + "</cac:InvoiceLine></Invoice>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    /** Builds one sub invoice line, with the next level inside it. */
    private static String subInvoiceLine(int level, int levels) {
        return "<cac:SubInvoiceLine><cbc:ID>" + level + "</cbc:ID>" + LINE_AMOUNTS
                + (level < levels ? subInvoiceLine(level + 1, levels) : "")
                + item("level " + level) + PRICE
                + "</cac:SubInvoiceLine>";
    }

    private static String item(String name) {
        return "<cac:Item><cbc:Name>" + name + "</cbc:Name></cac:Item>";
    }

    /**
     * An occurrence that produces nothing spends no index. The first of two notes is
     * empty, so the note that remains is the first occurrence of BG-1 and not the second,
     * and the cardinality layer has nothing to complain about.
     */
    @Test
    void spendsAnIndexOnWhatIsRecordedAndNotOnWhatIsVisited() {
        SemanticDocument leading = importer.importUbl(notes("", "second"));
        SemanticDocument middle = importer.importUbl(notes("first", "", "third"));

        assertEquals(SemanticValue.of("second"), value(leading, "/BG-1/0/BT-22"));
        assertEquals(List.of("/BG-1/0/BT-22"), notePaths(leading));
        assertEquals(List.of("/BG-1/0/BT-22", "/BG-1/1/BT-22"), notePaths(middle));
        assertEquals(List.of(), indexGaps(leading), "the indices are dense");
        assertEquals(List.of(), indexGaps(middle), "the indices are dense");
    }

    /**
     * The UBL syntax binding writes the note subject code as a prefix inside the note,
     * and the vendored stylesheet does not split it. The named normalization of
     * {@link XrNormalization#UBL_NOTE_SUBJECT_CODE} does, so a UBL note arrives as the
     * two business terms the standard gives it.
     */
    @Test
    void splitsTheNoteSubjectCodeOutOfAUblNote() {
        SemanticDocument document = importer.importUbl(notes("#ADU#Our terms apply."));

        assertEquals(SemanticValue.of("ADU"), value(document, "/BG-1/0/BT-21"));
        assertEquals(SemanticValue.of("Our terms apply."),
                value(document, "/BG-1/0/BT-22"));
    }

    /**
     * Only the prefix the syntax binding writes is a code. A note that begins with a
     * number sign in another shape, and a note that is nothing but the prefix and would
     * leave BT-22 without a value, are stored as they arrived.
     */
    @Test
    void leavesANoteAloneWhereThePrefixIsNoSubjectCode() {
        assertEquals(SemanticValue.of("#adu#lowercase"),
                value(importer.importUbl(notes("#adu#lowercase")), "/BG-1/0/BT-22"));
        assertEquals(SemanticValue.of("#ADUX#four letters"),
                value(importer.importUbl(notes("#ADUX#four letters")), "/BG-1/0/BT-22"));
        assertEquals(SemanticValue.of("#ADU#"),
                value(importer.importUbl(notes("#ADU#")), "/BG-1/0/BT-22"));
        assertEquals(SemanticValue.of("no prefix at all"),
                value(importer.importUbl(notes("no prefix at all")), "/BG-1/0/BT-22"));
    }

    /**
     * The normalization is named and switchable. An importer that was given none keeps
     * the text as the stylesheet wrote it, which is what a caller who wants the XR
     * representation itself asks for.
     */
    @Test
    void keepsThePrefixWhereTheNormalizationIsNotRun() {
        XrImporter plain = new XrImporter(XrImporter.defaultRegistry(),
                XrImporter.DEFAULT_MAX_INPUT_BYTES, Limits.defaults(), Set.of());
        SemanticDocument document = plain.importUbl(notes("#ADU#Our terms apply."));

        assertEquals(Set.of(), plain.normalizations());
        assertEquals(SemanticValue.of("#ADU#Our terms apply."),
                value(document, "/BG-1/0/BT-22"));
        assertFalse(document.value(SemanticPath.of("/BG-1/0/BT-21")).isPresent(),
                "without the normalization the code stays inside the text");
    }

    /**
     * A prefix is no code where the stylesheet already found one. The UBL stylesheet
     * reads BT-21 out of a note element that holds nothing but three capitals; where it
     * did, the code of that BG-1 instance is settled and the text below it is a note that
     * happens to begin with a number sign.
     */
    @Test
    void leavesTheTextAloneWhereTheStylesheetAlreadyFoundACode() {
        SemanticDocument document = importer.importUbl(notes("REG", "#ADU#Betriebsstätte"));

        assertEquals(SemanticValue.of("REG"), value(document, "/BG-1/0/BT-21"));
        assertEquals(SemanticValue.of("#ADU#Betriebsstätte"),
                value(document, "/BG-1/0/BT-22"));
    }

    /**
     * The document the importer writes fits the reader it writes for. The bound on the
     * number of values ends the import where it is reached and the report says so, and
     * what came back is a document that reader accepts.
     */
    @Test
    void stopsWhereTheDocumentReachesTheNumberOfValuesTheReaderAdmits() {
        Limits few = Limits.defaults().toBuilder().maxValues(6).build();
        XrImporter narrow = new XrImporter(
                XrImporter.defaultRegistry(), XrImporter.DEFAULT_MAX_INPUT_BYTES, few);

        ImportResult result = narrow.importUblWithReport(notes("one", "two", "three", "four"));

        assertEquals(6, result.document().values().size());
        assertEquals(1, result.report().notes(ImportNote.Kind.LIMIT_REACHED).size(),
                "one note, because the import ends at the bound: " + result.report().notes());
        assertEquals(result.document(), EsjReader.withLimits(few)
                        .read(Canonicalizer.canonicalBytes(result.document())),
                "a reader running that profile reads the document back");
    }

    /**
     * A value that is too large by itself is left out by itself, and the rest of the
     * document arrives.
     */
    @Test
    void leavesOutAValueLongerThanTheReaderAdmits() {
        Limits shortStrings = Limits.defaults().toBuilder().maxStringBytes(32).build();
        XrImporter narrow = new XrImporter(
                XrImporter.defaultRegistry(), XrImporter.DEFAULT_MAX_INPUT_BYTES, shortStrings);

        ImportResult result = narrow.importUblWithReport(notes("x".repeat(64), "short"));

        assertEquals(List.of("/BG-1/0/BT-22"), notePaths(result.document()));
        assertEquals(SemanticValue.of("short"),
                value(result.document(), "/BG-1/0/BT-22"));
        assertEquals(List.of("/BG-1/0/BT-22"),
                result.report().notes(ImportNote.Kind.LIMIT_REACHED).stream()
                        .map(ImportNote::location).toList());
        assertEquals(result.document(), EsjReader.withLimits(shortStrings)
                        .read(Canonicalizer.canonicalBytes(result.document())),
                "a reader running that profile reads the document back");
    }

    /**
     * The size of the document is measured exactly rather than estimated. An importer
     * whose bound is the length of the canonical form writes that form whole; one byte
     * less and it stops and says so.
     */
    @Test
    void measuresTheSizeOfTheDocumentItIsBuilding() {
        byte[] invoice = Conformance.instance(UBL);
        SemanticDocument document = importer.importUbl(invoice);
        int length = Canonicalizer.canonicalBytes(document).length;

        ImportResult exact = importerFor(length).importUblWithReport(invoice);
        ImportResult short1 = importerFor(length - 1).importUblWithReport(invoice);

        assertEquals(document, exact.document(), "the whole document fits its own length");
        assertEquals(List.of(), exact.report().notes(ImportNote.Kind.LIMIT_REACHED));
        assertFalse(short1.report().notes(ImportNote.Kind.LIMIT_REACHED).isEmpty(),
                "one byte less does not fit");
        assertTrue(short1.document().values().size() < document.values().size(),
                "and the document that came back is the smaller one");
    }

    private static XrImporter importerFor(int maxDocumentBytes) {
        return new XrImporter(XrImporter.defaultRegistry(), XrImporter.DEFAULT_MAX_INPUT_BYTES,
                Limits.defaults().toBuilder().maxDocumentBytes(maxDocumentBytes).build());
    }

    /** Returns the paths of the invoice notes of a document, in canonical order. */
    private static List<String> notePaths(SemanticDocument document) {
        return document.values().keySet().stream()
                .map(SemanticPath::toString)
                .filter(path -> path.startsWith("/BG-1/"))
                .toList();
    }

    /** Returns the cardinality findings that report an occurrence index with a gap. */
    private static List<String> indexGaps(SemanticDocument document) {
        return StructuralValidator.validate(document, XrImporter.defaultRegistry(),
                        EnumSet.of(ValidationLayer.L3)).findings().stream()
                .filter(finding -> finding.code() == FindingCode.ESJ_L3_INDEX_GAP)
                .map(Finding::message)
                .toList();
    }

    /** Builds a UBL invoice that carries the given document level notes, in order. */
    private static byte[] notes(String... notes) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:"
                + "CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:"
                + "CommonBasicComponents-2\">"
                + "<cbc:CustomizationID>urn:cen.eu:en16931:2017</cbc:CustomizationID>"
                + "<cbc:ID>RE-2026-0001</cbc:ID><cbc:IssueDate>2026-01-01</cbc:IssueDate>"
                + "<cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>");
        for (String note : notes) {
            xml.append("<cbc:Note>").append(note).append("</cbc:Note>");
        }
        xml.append("<cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode></Invoice>");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * An attachment arrives as bytes from either syntax. Both stylesheets write the
     * content as base64 and the two supplementary components as attributes, so the
     * importer decodes once and gets the same file twice.
     */
    @Test
    void readsAnAttachmentFromBothSyntaxes() {
        SemanticValue fromUbl = value(
                importer.importUbl(Conformance.instance(ATTACHMENT_UBL)), "/BG-24/0/BT-125");
        SemanticValue fromCii = value(
                importer.importCii(Conformance.instance(ATTACHMENT_CII)), "/BG-24/0/BT-125");

        assertEquals("application/pdf", fromUbl.mimeCode());
        assertEquals(fromUbl.filename(), fromCii.filename());
        assertEquals(fromUbl.canonicalContent(), fromCii.canonicalContent(),
                "the same file from both syntaxes");
        assertArrayEquals(fromUbl.asBytes(), fromCii.asBytes(), "the same file from both syntaxes");
        assertTrue(fromUbl.asBytes().length > 1000, "the file is the decoded attachment");
        assertEquals("%PDF", new String(fromUbl.asBytes(), 0, 4, StandardCharsets.ISO_8859_1),
                "the decoded bytes are the file and not its encoding");
    }

    private static SemanticValue value(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path))
                .orElseThrow(() -> new AssertionError("the document has no value at " + path));
    }
}
