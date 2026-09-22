package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportReport;
import de.bsnsoft.esj.imports.ImportResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the importer says about a source element the syntax binding selects into no business
 * group.
 *
 * <p>The transformation picks the elements of a business group by a value inside them, and an
 * element that states no such value is picked by nothing: it parses against the schema of its
 * syntax, it arrives, and none of the business terms below it reach the semantic document. The
 * loss is real and used to be silent, which meant a report told a caller the import was clean.
 * These cases are the shapes of it — a missing indicator, a missing type code, a currency the
 * invoice does not name, an identification scheme that selects no term — in both syntaxes.
 */
class XrCoverageTest {

    private static final String CII = "business-cases/standard/01.01a-INVOICE_uncefact.xml";
    private static final String UBL = "technical-cases/cius/01.01_comprehensive_test_ubl.xml";
    private static final String CII_SCHEMES =
            "technical-cases/cius/01.03_comprehensive_test_uncefact.xml";

    private final XrImporter importer = new XrImporter();

    @Test
    void reportsAPaymentMeansWithoutItsTypeCode() {
        ImportResult result = importer.importXmlWithReport(
                without(Conformance.instance(CII), "<ram:TypeCode>58</ram:TypeCode>"));

        assertEquals(List.of("BG-16"), lost(result.report()));
        assertFalse(result.document().value(SemanticPath.of("/BG-16/0/BT-81")).isPresent(),
                "the payment instructions did not reach the document");
    }

    @Test
    void reportsAnAllowanceWithoutItsIndicator() {
        byte[] source = Conformance.instance(UBL);
        String indicator = "<cbc:ChargeIndicator>false</cbc:ChargeIndicator>";
        assertTrue(new String(source, StandardCharsets.UTF_8).contains(indicator),
                "the instance this case is built from states a document level allowance");

        ImportResult result = importer.importXmlWithReport(without(source, indicator));

        assertEquals(List.of("BG-20 or BG-21"), lost(result.report()));
    }

    @Test
    void reportsAPriceAllowanceWithoutItsIndicator() {
        byte[] source = Conformance.instance(UBL);
        String indicator = "        <cbc:ChargeIndicator>false</cbc:ChargeIndicator>\n";
        assertTrue(new String(source, StandardCharsets.UTF_8).contains(indicator),
                "the instance this case is built from states a price allowance");

        ImportResult result = importer.importXmlWithReport(without(source, indicator));

        assertEquals(List.of("BG-29"), lost(result.report()));
        assertFalse(result.document().value(SemanticPath.of("/BG-25/0/BG-29/BT-147")).isPresent(),
                "the price allowance did not reach the document");
    }

    @Test
    void reportsATaxTotalInACurrencyTheInvoiceDoesNotName() {
        byte[] source = Conformance.instance(CII);
        String currency = "<ram:InvoiceCurrencyCode>EUR</ram:InvoiceCurrencyCode>";
        assertTrue(new String(source, StandardCharsets.UTF_8).contains(currency),
                "the instance this case is built from states an invoice currency");

        ImportResult result = importer.importXmlWithReport(without(source, currency));

        assertEquals(List.of("BG-22"), lost(result.report()));
        assertFalse(result.document().value(SemanticPath.of("/BG-22/BT-110")).isPresent(),
                "the invoice total VAT amount did not reach the document");
    }

    @Test
    void reportsTheSameLossInUbl() {
        byte[] source = Conformance.instance(UBL);
        String currency = "<cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>";
        assertTrue(new String(source, StandardCharsets.UTF_8).contains(currency),
                "the instance this case is built from states a document currency");

        ImportResult result = importer.importXmlWithReport(without(source, currency));

        assertEquals(List.of("BG-22"), lost(result.report()));
        assertFalse(result.document().value(SemanticPath.of("/BG-22/BT-110")).isPresent(),
                "the invoice total VAT amount did not reach the document");
    }

    @Test
    void reportsATaxRegistrationOfAnotherScheme() {
        ImportResult result = importer.importXmlWithReport(
                instead(Conformance.instance(CII_SCHEMES), "schemeID=\"VA\"",
                        "schemeID=\"XX\""));

        assertEquals(List.of("BG-4", "BG-7"), lost(result.report()),
                "the instance states a VAT registration for the seller and for the buyer");
        assertFalse(result.document().value(SemanticPath.of("/BG-4/BT-31")).isPresent(),
                "a registration of another scheme is not the seller VAT identifier");
    }

    @Test
    void reportsAGlobalIdentifierWithoutItsScheme() {
        byte[] source = Conformance.instance(CII_SCHEMES);
        String identifier = "<ram:GlobalID schemeID=\"0088\">";
        assertTrue(new String(source, StandardCharsets.UTF_8).contains(identifier),
                "the instance this case is built from states a global identifier");

        ImportResult result = importer.importXmlWithReport(
                instead(source, identifier, "<ram:GlobalID>"));

        assertEquals(List.of("BG-4"), lost(result.report()));
        assertFalse(result.document().value(SemanticPath.of("/BG-4/BT-29/0")).isPresent(),
                "the stylesheet reads this element by the scheme it states, so the"
                        + " identifier did not reach the document");
    }

    @Test
    void reportsAUblPartyTaxSchemeThatIsNotValueAddedTax() {
        byte[] source = Conformance.instance(UBL);
        ImportResult whole = importer.importXmlWithReport(source);
        assertTrue(whole.document().value(SemanticPath.of("/BG-7/BT-48")).isPresent(),
                "the instance this case is built from states a buyer VAT identifier");

        ImportResult result = importer.importXmlWithReport(
                instead(source, "<cbc:ID>VAT</cbc:ID>", "<cbc:ID>FC</cbc:ID>"));

        assertTrue(lost(result.report()).contains("BG-7"), "the buyer registration is lost");
        assertFalse(result.document().value(SemanticPath.of("/BG-7/BT-48")).isPresent(),
                "a registration of another tax is not the buyer VAT identifier");
    }

    @Test
    void saysNothingAboutTheCorpus() {
        for (String instance : Conformance.corpus()) {
            ImportResult result =
                    importer.importXmlWithReport(Conformance.instance(instance));
            assertEquals(List.of(), lost(result.report()),
                    instance + " is a document the binding carries whole");
        }
    }

    /** Returns the groups the report says the binding could not classify. */
    private static List<String> lost(ImportReport report) {
        return report.notes(ImportNote.Kind.UNPLACEABLE).stream()
                .filter(note -> note.message().contains("did not reach the semantic document"))
                .map(ImportNote::location)
                .toList();
    }

    /** Returns the document with every occurrence of one string replaced by another. */
    private static byte[] instead(byte[] xml, String from, String to) {
        String text = new String(xml, StandardCharsets.UTF_8);
        if (!text.contains(from)) {
            throw new AssertionError("the instance does not carry " + from);
        }
        return text.replace(from, to).getBytes(StandardCharsets.UTF_8);
    }

    /** Returns the document with the first occurrence of one element removed. */
    private static byte[] without(byte[] xml, String element) {
        String text = new String(xml, StandardCharsets.UTF_8);
        int at = text.indexOf(element);
        if (at < 0) {
            throw new AssertionError("the instance does not carry " + element);
        }
        return (text.substring(0, at) + text.substring(at + element.length()))
                .getBytes(StandardCharsets.UTF_8);
    }
}
