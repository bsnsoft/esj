package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.model.Registry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The UBL writer on documents written for one question each, so that a failure names one
 * thing.
 *
 * <p>{@code UblWriterCorpusTest} measures whether the writer produces documents the official
 * artefacts accept; these tests say what it does and why, one rule of the binding at a time.
 */
class UblWriterTest {

    /** Where the validation artefact of the pack lists the codes each document admits. */
    private static final String ARTEFACT =
            "/de/bsnsoft/esj/syntax/packs/xrechnung/3.0.2/2026-08-31"
                    + "/cen/1.3.16/EN16931-UBL-validation.xslt";

    /** The one shape the artefact writes an admitted code list in. */
    private static final Pattern ADMITTED = Pattern.compile(
            "cbc:(Invoice|CreditNote)TypeCode and \\(\\(not\\(contains\\(normalize-space\\(\\.\\),"
                    + " ' '\\)\\) and contains\\(' ([0-9 ]+) '");

    /**
     * The UBL writer is the same engine as the cross industry one and tells a term of an
     * untransported registry apart from a loss the same way: handed the B2C registry, it
     * leaves the ten values of the example behind by design and drops nothing.
     */
    @Test
    void leavesTheTermsOfAnUntransportedRegistryBehindByDesign() {
        SemanticDocument document = EsjReader.strict().read(Examples.bytes("b2c-gross"));

        WriteReport report = UblWriter.writeWithReport(document, WriterOptions.builder()
                .extensions(List.of(Registry.b2cExtension())).build()).report();

        assertEquals(0, report.dropped());
        assertTrue(report.isComplete(), report.toString());
        assertEquals(Map.of("ESJ-B2C 0.1",
                        List.of("BT-B2C-010", "BT-B2C-001", "BT-B2C-002", "BT-B2C-003")),
                report.byDesign());
    }

    @Test
    void writesTheDocumentElementAndTheNamespacesOfTheSyntax() {
        String xml = write(SemanticDocument.builder().put("/BT-1", "RE-1").build());
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Invoice "),
                xml.substring(0, 80));
        assertContains(xml, "xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"");
        assertContains(xml, "xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:"
                + "CommonAggregateComponents-2\"");
        assertContains(xml, "<cbc:ID>RE-1</cbc:ID>");
    }

    @Test
    void putsSiblingsInTheOrderTheSchemaDeclaresThem() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-22/BT-115", "119.00")
                .put("/BG-22/BT-106", "100.00")
                .put("/BG-22/BT-112", "119.00")
                .build());
        assertOrder(xml, "<cbc:LineExtensionAmount", "<cbc:TaxInclusiveAmount",
                "<cbc:PayableAmount");
    }

    /**
     * UBL declares the currency of an amount a required attribute and the semantic model
     * states it once. The two totals the table itself conditions on a currency element are
     * the exception, and the writer copies the element the condition names.
     */
    @Test
    void writesTheCurrencyOfTheDocumentOnEveryAmount() {
        String xml = write(SemanticDocument.builder()
                .put("/BT-5", "EUR")
                .put("/BT-6", "GBP")
                .put("/BG-22/BT-106", "100.00")
                .put("/BG-22/BT-110", "19.00")
                .put("/BG-22/BT-111", "16.50")
                .build());
        assertContains(xml, "<cbc:LineExtensionAmount currencyID=\"EUR\">100.00"
                + "</cbc:LineExtensionAmount>");
        assertContains(xml, "<cbc:TaxAmount currencyID=\"EUR\">19.00</cbc:TaxAmount>");
        assertContains(xml, "<cbc:TaxAmount currencyID=\"GBP\">16.50</cbc:TaxAmount>");
    }

    /**
     * UBL has one element for the note and none for the subject code of the note, and the
     * binding writes the code in front of the text between two number signs.
     */
    @Test
    void writesTheNoteSubjectCodeAsAPrefixOfTheNote() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-1/0/BT-21", "AAI")
                .put("/BG-1/0/BT-22", "Terms of delivery apply.")
                .put("/BG-1/1/BT-22", "A note without a code.")
                .build());
        assertContains(xml, "<cbc:Note>#AAI#Terms of delivery apply.</cbc:Note>");
        assertContains(xml, "<cbc:Note>A note without a code.</cbc:Note>");
    }

    /**
     * A subject code without the note it belongs in front of has nowhere to go: the reader
     * splits nothing out of an element that is the prefix and nothing else.
     */
    @Test
    void namesASubjectCodeThatHasNoNoteToStandInFrontOf() {
        WriteResult result = UblWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-1/0/BT-21", "AAI").build(), WriterOptions.defaults());
        assertEquals(1, result.report().dropped());
        assertEquals(WriteNote.Kind.VALUE_NEEDS_COMPANION,
                result.report().notes().get(0).kind());
        assertFalse(new String(result.xml(), StandardCharsets.UTF_8).contains("#AAI#"));
    }

    /**
     * The value added tax point date code is UNTDID 2005 in the semantic model and in this
     * syntax, so nothing is translated on the way out; the cross industry invoice is where
     * the translation happens.
     */
    @Test
    void writesTheTaxPointDateCodeAsTheStandardSpellsIt() {
        assertContains(write(SemanticDocument.builder().put("/BT-8", "35").build()),
                "<cbc:DescriptionCode>35</cbc:DescriptionCode>");
    }

    /**
     * The two business groups the semantic model states no element of in this syntax, and
     * whose terms are bound where the standard puts them.
     */
    @Test
    void writesTheTermsOfAGroupTheSyntaxStatesNoElementOf() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2017")
                .put("/BG-2/BT-23", "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0")
                .build());
        assertContains(xml, "<cbc:CustomizationID>urn:cen.eu:en16931:2017</cbc:CustomizationID>");
        assertContains(xml, "<cbc:ProfileID>urn:fdc:peppol.eu:2017:poacc:billing:01:1.0"
                + "</cbc:ProfileID>");
    }

    /**
     * Two terms in one element told apart by an attribute: the party identifier of the payee
     * is every occurrence whose scheme is not SEPA, and the bank assigned creditor
     * identifier is the one whose scheme is.
     */
    @Test
    void tellsTheCreditorIdentifierFromThePartyIdentifier() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-10/BT-59", "[Payee name]")
                .put("/BG-10/BT-60", "74")
                .put("/BG-16/BG-19/BT-90", "DE98ZZZ09999999999")
                .build());
        assertContains(xml, "<cbc:ID>74</cbc:ID>");
        assertContains(xml, "<cbc:ID schemeID=\"SEPA\">DE98ZZZ09999999999</cbc:ID>");
    }

    /**
     * The supporting document group is the additional document reference that is neither the
     * invoiced object identifier nor, in a credit note, the project reference.
     */
    @Test
    void keepsTheSupportingDocumentOffTheOtherReferences() {
        String xml = write(SemanticDocument.builder()
                .put("/BT-18", SemanticValue.identifier("OBJ-42", "AAJ"))
                .put("/BG-24/0/BT-122", "ATT-1")
                .build());
        assertContains(xml, "<cbc:DocumentTypeCode>130</cbc:DocumentTypeCode>");
        assertContains(xml, "<cbc:ID schemeID=\"AAJ\">OBJ-42</cbc:ID>");
        assertContains(xml, "<cbc:ID>ATT-1</cbc:ID>");
        assertEquals(2, count(xml, "<cac:AdditionalDocumentReference>"),
                "the two references are two elements");
        assertEquals(1, count(xml, "<cbc:DocumentTypeCode>"),
                "and the supporting document states no type code");
    }

    /** The extension nests an invoice line in itself, and the depth is a fact of the path. */
    @Test
    void writesASubInvoiceLineAtTheDepthTheSemanticPathStates() {
        String xml = write(SemanticDocument.builder()
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BG-DEX-01/0/BT-126", "1.1")
                .put("/BG-25/0/BG-DEX-01/0/BG-DEX-01/0/BT-126", "1.1.1")
                .build());
        assertContains(xml, "<cac:SubInvoiceLine>");
        assertContains(xml, "<cbc:ID>1.1.1</cbc:ID>");
        assertEquals(2, count(xml, "<cac:SubInvoiceLine>"));
    }

    /**
     * Which of the two UBL documents a semantic document becomes is a fact of the invoice
     * type code, and the codes are the ones the validation artefact of the pack admits on a
     * credit note and not on an invoice.
     */
    @Test
    void choosesTheDocumentTypeFromTheInvoiceTypeCode() {
        assertEquals(BindingSyntax.UBL_INVOICE, syntaxOf("380"));
        assertEquals(BindingSyntax.UBL_CREDIT_NOTE, syntaxOf("381"));
        assertEquals(BindingSyntax.UBL_INVOICE, syntaxOf("81"),
                "the artefact admits 81 on both documents, so it stays an invoice");
        assertEquals(BindingSyntax.UBL_INVOICE,
                UblWriter.syntaxOf(SemanticDocument.builder().put("/BT-1", "RE-1").build(),
                        UblWriter.DocumentType.AUTO),
                "a document with no invoice type code is an invoice");
        assertEquals(BindingSyntax.UBL_INVOICE,
                UblWriter.syntaxOf(creditNote(), UblWriter.DocumentType.INVOICE));
        assertEquals(BindingSyntax.UBL_CREDIT_NOTE,
                UblWriter.syntaxOf(SemanticDocument.builder().put("/BT-3", "380").build(),
                        UblWriter.DocumentType.CREDIT_NOTE));
    }

    /**
     * The codes above are read out of the validation artefact of the pack rather than
     * remembered: the artefact admits one set of UNTDID 1001 codes on the invoice type code
     * and another on the credit note type code, and the difference is the list the writer
     * carries.
     */
    @Test
    void theCreditNoteCodesAreTheOnesTheArtefactAdmitsOnACreditNoteAlone() {
        Matcher matcher = ADMITTED.matcher(Corpus.text(ARTEFACT));
        List<String> invoice = new ArrayList<>();
        List<String> creditNote = new ArrayList<>();
        while (matcher.find()) {
            (matcher.group(1).equals("Invoice") ? invoice : creditNote)
                    .addAll(List.of(matcher.group(2).split(" ")));
        }
        assertFalse(invoice.isEmpty(), "the artefact admits codes on an invoice");
        assertFalse(creditNote.isEmpty(), "the artefact admits codes on a credit note");
        List<String> only = creditNote.stream().distinct()
                .filter(code -> !invoice.contains(code))
                .sorted((left, right) ->
                        Integer.compare(Integer.parseInt(left), Integer.parseInt(right)))
                .toList();
        assertEquals(only, UblWriter.creditNoteCodes());
    }

    @Test
    void writesACreditNoteAsTheOtherDocumentElement() {
        String xml = new String(UblWriter.write(creditNote()), StandardCharsets.UTF_8);
        assertContains(xml, "<CreditNote xmlns=\"urn:oasis:names:specification:ubl:schema:"
                + "xsd:CreditNote-2\"");
        assertContains(xml, "<cbc:CreditNoteTypeCode>381</cbc:CreditNoteTypeCode>");
    }

    /**
     * A term the syntax writes inside an element a business group owns, while the semantic
     * model puts the term outside that group.
     *
     * <p>UBL has no {@code cbc:DueDate} on a credit note, so the table binds BT-9 inside
     * the payment means, and BT-9 stands outside BG-16. A payment means of its own would
     * be refused by the schema, which requires {@code cbc:PaymentMeansCode} in every one
     * of them, and the canonical path order writes BT-9 before BG-16, so the second
     * element would be the one with the code.
     */
    @Test
    void writesADueDateIntoThePaymentMeansTheGroupOwns() {
        WriteResult result = UblWriter.writeWithReport(SemanticDocument.builder()
                .put("/BT-3", "381")
                .put("/BT-9", "2026-03-20")
                .put("/BG-16/BT-81", "58")
                .put("/BG-16/BG-17/0/BT-84", "DE12500105170648489890")
                .build(), WriterOptions.defaults());
        String xml = new String(result.xml(), StandardCharsets.UTF_8);
        assertEquals(1, count(xml, "<cac:PaymentMeans>"), xml);
        assertOrder(xml, "<cbc:PaymentMeansCode>58<", "<cbc:PaymentDueDate>2026-03-20<",
                "<cac:PayeeFinancialAccount>");
        assertEquals(List.of(), result.report().losses(), xml);
    }

    /**
     * The same term where the document states no payment means at all. There is no element
     * of a UBL credit note that holds it: a payment means of its own is refused by the
     * schema, and a payment means code nobody stated is not the writer's to supply. So the
     * value is left out, counted as a loss and named, and the document the writer produces
     * is one the schema accepts.
     */
    @Test
    void leavesOutADueDateNoPaymentMeansOfTheDocumentCanHold() {
        WriteResult result = UblWriter.writeWithReport(SemanticDocument.builder()
                .put("/BT-3", "381")
                .put("/BT-9", "2026-03-20")
                .build(), WriterOptions.defaults());
        String xml = new String(result.xml(), StandardCharsets.UTF_8);
        assertFalse(xml.contains("cac:PaymentMeans"), xml);
        assertEquals(1, result.report().notes(WriteNote.Kind.VALUE_NEEDS_GROUP).size(),
                result.report().notes().toString());
        assertEquals(1, result.report().losses().size(), xml);
        assertEquals(1, result.report().dropped(), xml);
    }

    /**
     * An element the writer made for a value it then declined to write is taken back out.
     *
     * <p>The note subject code of UBL is written in front of the content of the note it
     * belongs to, and a code without a note is left out. The element it would have stood
     * in must go with it: {@code PEPPOL-EN16931-R008} refuses an empty element, and the
     * finding would be about an element the writer invented.
     */
    @Test
    void takesBackOutAnElementItDeclinedToFill() {
        WriteResult result = UblWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-1/0/BT-21", "AAI")
                .build(), WriterOptions.defaults());
        assertFalse(new String(result.xml(), StandardCharsets.UTF_8).contains("cbc:Note"),
                new String(result.xml(), StandardCharsets.UTF_8));
        assertEquals(1, result.report().notes(WriteNote.Kind.VALUE_NEEDS_COMPANION).size());
    }

    /** The same where every character of the value is one XML 1.0 cannot carry. */
    @Test
    void takesBackOutAnElementWhoseValueNoXmlDocumentCanHold() {
        WriteResult result = UblWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-1/0/BT-22", "\u0001\u0002")
                .build(), WriterOptions.defaults());
        assertFalse(new String(result.xml(), StandardCharsets.UTF_8).contains("cbc:Note"),
                new String(result.xml(), StandardCharsets.UTF_8));
        assertEquals(1,
                result.report().notes(WriteNote.Kind.CHARACTER_NOT_REPRESENTABLE).size());
        assertEquals(1, result.report().dropped());
    }

    /**
     * An attribute the schema requires and the semantic model does not fill is named the
     * way a required element is. BT-5 states the currency of every amount of a UBL
     * document once, and a document without it leaves every one of them without the
     * attribute.
     */
    @Test
    void namesAnAttributeTheSchemaAsksForAndTheDocumentDoesNotState() {
        WriteResult result = UblWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-22/BT-106", "100.00")
                .put("/BG-22/BT-112", "119.00")
                .build(), WriterOptions.defaults());
        assertEquals(2, named(result, "the attribute currencyID"),
                result.report().notes().toString());
        assertTrue(result.report().isFaithful(),
                "an attribute the syntax asks for is no loss of the document");
    }

    /**
     * The element the schema requires whose content a business term of the model carries
     * and this document does not state.
     */
    @Test
    void namesATermTheSyntaxRequiresAndTheDocumentDoesNotState() {
        WriteResult result = UblWriter.writeWithReport(SemanticDocument.builder()
                .put("/BG-23/0/BT-116", "100.00")
                .build(), WriterOptions.defaults());
        assertEquals(1, named(result, WriteNote.Kind.TERM_NOT_STATED,
                        "requires cbc:TaxAmount in /Invoice/cac:TaxTotal and the document"
                        + " does not state BT-110"),
                result.report().notes().toString());
        assertTrue(result.report().isFaithful(),
                "an element the syntax asks for is no loss of the document");
    }

    /**
     * The three elements this syntax requires that the semantic model does not state, and
     * what {@code model/bindings} says is written at each of them.
     */
    @Test
    void writesTheConventionsOfTheBindingTable() {
        String card = write(SemanticDocument.builder()
                .put("/BG-16/BG-18/BT-87", "1234")
                .build());
        assertContains(card, "<cbc:NetworkID>NA</cbc:NetworkID>");
        String order = write(SemanticDocument.builder().put("/BT-14", "SO-1").build());
        assertContains(order, "<cbc:ID>NA</cbc:ID>");
        String registration = write(SemanticDocument.builder()
                .put("/BG-4/BT-32", "123/4567/8901")
                .build());
        assertContains(registration, "<cbc:ID>FC</cbc:ID>");
    }

    /** A convention is named in the report, and it is not an incompleteness. */
    @Test
    void namesEveryConventionItAppliedAndStaysComplete() {
        WriteResult result = UblWriter.writeWithReport(
                EsjReader.strict().read(Examples.bytes("charges")),
                WriterOptions.defaults());
        assertEquals(1, named(result, WriteNote.Kind.CONVENTION_APPLIED, "cbc:NetworkID"),
                result.report().notes().toString());
        assertTrue(result.report().isComplete(),
                "a document the syntax accepts is complete: " + result.report().notes());
        assertTrue(result.report().notes(WriteNote.Kind.CONVENTION_APPLIED).get(0)
                        .message().contains("Peppol BIS Billing 3.0"),
                "the note names where the value comes from");
    }

    /** The one convention whose value the caller chooses, and the one it may not be. */
    @Test
    void writesTheTaxRegistrationSchemeTheCallerAsksFor() {
        SemanticDocument document =
                SemanticDocument.builder().put("/BG-4/BT-32", "123/4567/8901").build();
        assertContains(new String(UblWriter.write(document, WriterOptions.builder()
                        .taxRegistrationScheme("TAX").build()), StandardCharsets.UTF_8),
                "<cbc:ID>TAX</cbc:ID>");
        assertThrows(IllegalArgumentException.class,
                () -> WriterOptions.builder().taxRegistrationScheme("VAT"),
                "VAT is the scheme of BT-31");
        assertThrows(IllegalArgumentException.class,
                () -> WriterOptions.builder().taxRegistrationScheme(" "));
    }

    /**
     * A document whose BT-13 is character for character the conventional value, beside a
     * BT-14, cannot come back from this syntax, and the writer says so.
     */
    @Test
    void saysThatAValueOfItsOwnReadsBackAsTheConvention() {
        WriteResult result = UblWriter.writeWithReport(SemanticDocument.builder()
                .put("/BT-13", "NA")
                .put("/BT-14", "SO-1")
                .build(), WriterOptions.defaults());
        assertEquals(List.of("/BT-13"),
                result.report().notes(WriteNote.Kind.VALUE_READS_AS_CONVENTION).stream()
                        .map(WriteNote::path).toList());
        assertTrue(result.report().isFaithful(), "the value is in the written document");
        assertEquals(Set.of(SemanticPath.of("/BT-14")),
                new StreamingReader().read(result.xml()).document().values().keySet(),
                "and the round trip loses it, which is what the note says");
    }

    /** A BT-13 that is the conventional value without a BT-14 is an ordinary value. */
    @Test
    void saysNothingWhereTheConditionOfTheConventionDoesNotHold() {
        WriteResult result = UblWriter.writeWithReport(SemanticDocument.builder()
                .put("/BT-13", "NA")
                .build(), WriterOptions.defaults());
        assertEquals(List.of(),
                result.report().notes(WriteNote.Kind.VALUE_READS_AS_CONVENTION));
        assertEquals(Set.of(SemanticPath.of("/BT-13")),
                new StreamingReader().read(result.xml()).document().values().keySet());
    }

    @Test
    void saysWhatItDidWithTheExtensionsMember() {
        WriteResult result = UblWriter.writeWithReport(SemanticDocument.builder()
                .put("/BT-1", "RE-1")
                .extension("example.esj-test", ExtensionValue.of("a token"))
                .build(), WriterOptions.defaults());
        assertEquals(1, result.report().notes(WriteNote.Kind.EXTENSIONS_DROPPED).size());
    }

    @Test
    void writesTheSameBytesTwice() {
        StreamingReader reader = new StreamingReader();
        for (String instance : Corpus.corpus()) {
            SemanticDocument document = reader.read(Corpus.instance(instance)).document();
            assertArrayEquals(UblWriter.write(document), UblWriter.write(document), instance);
        }
    }

    @Test
    void holdsToTheBoundOnTheOutput() {
        SemanticDocument document = SemanticDocument.builder().put("/BT-1", "RE-1").build();
        assertThrows(BindingLimitException.class, () -> UblWriter.write(document,
                WriterOptions.builder().maxOutputBytes(32).build()));
    }

    @Test
    void writesOneLineWhenAskedTo() {
        byte[] xml = UblWriter.write(SemanticDocument.builder().put("/BT-1", "RE-1").build(),
                WriterOptions.builder().indent(false).build());
        assertFalse(new String(xml, StandardCharsets.UTF_8).contains("\n  "));
    }

    /** Returns how often the report names an element by a fragment of its path. */
    private static int named(WriteResult result, String fragment) {
        return named(result, WriteNote.Kind.ELEMENT_NOT_STATED, fragment);
    }

    /** Returns how often a note of one kind carries a fragment of text. */
    private static int named(WriteResult result, WriteNote.Kind kind, String fragment) {
        return (int) result.report().notes(kind).stream()
                .filter(note -> note.message().contains(fragment)).count();
    }

    private static BindingSyntax syntaxOf(String code) {
        return UblWriter.syntaxOf(SemanticDocument.builder().put("/BT-3", code).build(),
                UblWriter.DocumentType.AUTO);
    }

    private static SemanticDocument creditNote() {
        return SemanticDocument.builder().put("/BT-1", "CN-1").put("/BT-3", "381").build();
    }

    private static String write(SemanticDocument document) {
        return new String(UblWriter.write(document), StandardCharsets.UTF_8);
    }

    private static int count(String xml, String fragment) {
        int found = 0;
        for (int at = xml.indexOf(fragment); at >= 0; at = xml.indexOf(fragment, at + 1)) {
            found++;
        }
        return found;
    }

    private static void assertContains(String haystack, String needle) {
        assertTrue(haystack.contains(needle), needle + "\nis not in\n" + haystack);
    }

    private static void assertOrder(String xml, String... fragments) {
        int at = -1;
        for (String fragment : fragments) {
            int found = xml.indexOf(fragment);
            assertTrue(found > at, fragment + " stands out of order in\n" + xml);
            at = found;
        }
    }
}
