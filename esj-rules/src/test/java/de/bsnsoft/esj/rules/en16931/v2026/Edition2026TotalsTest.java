package de.bsnsoft.esj.rules.en16931.v2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.model.MinorUnits;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleSeverity;
import de.bsnsoft.esj.typed.DerivationException;
import de.bsnsoft.esj.typed.DerivationReport;
import de.bsnsoft.esj.typed.v2026.En16931;
import de.bsnsoft.esj.typed.v2026.InvoiceEditor;
import de.bsnsoft.esj.typed.v2026.Totals;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The totals derivation of the typed view of EN 16931-1:2026, over the minor units of this
 * pack's currency snapshot, weighed by invoices computed by hand in currencies of zero, two
 * and three fraction digits and then run through this pack.
 *
 * <p>The expected amounts below were worked out on paper from the formulas of the edition,
 * each rounded half up once, to the minor unit of its currency; the document writes them in
 * the canonical form of a decimal, without trailing zeros, and the report at the scale of the
 * currency. A derived invoice that the
 * pack faulted would be a disagreement between the policy and the rules, which is what the
 * last step of every case looks for. Neither is corroborated by an official artefact; no
 * artefact of this edition is published.
 */
class Edition2026TotalsTest {

    private static final MinorUnits UNITS = En16931V2026.minorUnits();

    private static final RuleEngine ENGINE =
            new En16931V2026().engine(Registry.forSemanticModel(En16931.SEMANTIC_MODEL)
                    .orElseThrow());

    @Test
    void theSnapshotOfThePackGivesTheMinorUnits() {
        assertEquals(OptionalInt.of(2), UNITS.minorUnit("EUR"));
        assertEquals(OptionalInt.of(0), UNITS.minorUnit("JPY"));
        assertEquals(OptionalInt.of(3), UNITS.minorUnit("KWD"));
        assertTrue(UNITS.minorUnit("XAU").isEmpty(), "a precious metal has no minor unit");
        assertTrue(UNITS.source().contains("en16931-2026"));
    }

    /**
     * Euro, two fraction digits. Line 1: 3 × 12.335 = 37.005, rounded to 37.01. Line 2:
     * 2 × 10 + 0.25 − 1.50 = 18.75. Line 3, zero rated: 100.00. Standard rate 19 %:
     * 37.01 + 18.75 − 5.00 + 10.00 = 60.76, tax 11.5444, rounded to 11.54. Totals 155.76,
     * 5.00, 10.00, 160.76, 11.54, 172.30; due 172.30 − 20.00 + 12.50 = 164.80; in the
     * accounting currency 11.54 × 1.085 = 12.5209, rounded to 12.52.
     */
    @Test
    void anEuroInvoiceRoundsToTwoDigits() {
        InvoiceEditor invoice = invoice("EUR");
        SemanticDocument.Builder builder = invoice.builder();
        line(builder, 0, "3", "12.335", null, "S", "19");
        line(builder, 1, "2", "10", "1", "S", "19");
        builder.put("/BG-25/1/BG-27/0/BT-136", "1.5")
                .put("/BG-25/1/BG-27/0/BT-139", "Loyalty")
                .put("/BG-25/1/BG-28/0/BT-141", "0.25")
                .put("/BG-25/1/BG-28/0/BT-144", "Packing");
        line(builder, 2, "1", "100", null, "Z", "0");
        builder.put("/BG-20/0/BT-92", "5").put("/BG-20/0/BT-95", "S")
                .put("/BG-20/0/BT-96", "19").put("/BG-20/0/BT-97", "Discount")
                .put("/BG-21/0/BT-99", "10").put("/BG-21/0/BT-102", "S")
                .put("/BG-21/0/BT-103", "19").put("/BG-21/0/BT-104", "Freight")
                .put("/BG-22/BG-34/0/BT-179", "12.5")
                .put("/BG-22/BG-34/0/BT-180", "Deposit on returnable crates")
                .put("/BG-22/BT-113", "20")
                .put("/BT-6", "USD").put("/BT-167", "1.085");

        DerivationReport report = invoice.derive(Totals.of(UNITS));
        SemanticDocument document = invoice.document();

        assertAmount(document, "/BG-25/0/BT-131", "37.01");
        assertAmount(document, "/BG-25/1/BT-131", "18.75");
        assertAmount(document, "/BG-25/2/BT-131", "100");
        assertAmount(document, "/BG-23/0/BT-116", "60.76");
        assertAmount(document, "/BG-23/0/BT-117", "11.54");
        assertAmount(document, "/BG-23/1/BT-116", "100");
        assertAmount(document, "/BG-23/1/BT-117", "0");
        assertAmount(document, "/BG-22/BT-106", "155.76");
        assertAmount(document, "/BG-22/BT-107", "5");
        assertAmount(document, "/BG-22/BT-108", "10");
        assertAmount(document, "/BG-22/BT-109", "160.76");
        assertAmount(document, "/BG-22/BT-110", "11.54");
        assertAmount(document, "/BG-22/BT-111", "12.52");
        assertAmount(document, "/BG-22/BT-112", "172.3");
        assertAmount(document, "/BG-22/BT-115", "164.8");
        assertEquals(List.of("/BG-25/0/BT-131", "/BG-23/0/BT-117", "/BG-22/BT-111"),
                report.roundings().stream().map(value -> value.path().toString()).toList(),
                "one rounding per amount that is a product or a quotient, and none elsewhere");
        assertEquals("172.30", report.at("/BG-22/BT-112").orElseThrow().value().toPlainString(),
                "the report gives an amount at the scale of its currency");
        assertEquals("12.335", text(document, "/BG-25/0/BG-29/BT-146"),
                "a unit price is never rounded");
        assertNoFatalFinding(document);
    }

    /**
     * Yen, no fraction digits. Line 1: 3 × 333.33 = 999.99, rounded to 1000. Line 2:
     * 2.5 × 120 = 300. At 10 %: 1000 − 50 = 950, tax 95; at 8 %: 300, tax 24. Totals 1300,
     * 50, 1250, 119, 1369; due 1369 + 1 of rounding = 1370.
     */
    @Test
    void aYenInvoiceRoundsToWholeAmounts() {
        InvoiceEditor invoice = invoice("JPY");
        SemanticDocument.Builder builder = invoice.builder();
        line(builder, 0, "3", "333.33", null, "S", "10");
        line(builder, 1, "2.5", "120", "1", "S", "8");
        builder.put("/BG-20/0/BT-92", "50").put("/BG-20/0/BT-95", "S")
                .put("/BG-20/0/BT-96", "10").put("/BG-20/0/BT-97", "Discount")
                .put("/BG-22/BT-114", "1");

        invoice.derive(Totals.of(UNITS));
        SemanticDocument document = invoice.document();

        assertAmount(document, "/BG-25/0/BT-131", "1000");
        assertAmount(document, "/BG-25/1/BT-131", "300");
        assertAmount(document, "/BG-23/0/BT-116", "950");
        assertAmount(document, "/BG-23/0/BT-117", "95");
        assertAmount(document, "/BG-23/1/BT-116", "300");
        assertAmount(document, "/BG-23/1/BT-117", "24");
        assertAmount(document, "/BG-22/BT-106", "1300");
        assertAmount(document, "/BG-22/BT-109", "1250");
        assertAmount(document, "/BG-22/BT-110", "119");
        assertAmount(document, "/BG-22/BT-112", "1369");
        assertAmount(document, "/BG-22/BT-115", "1370");
        assertFalse(document.value(SemanticPath.of("/BG-22/BT-108")).isPresent());
        assertNoFatalFinding(document);
    }

    /**
     * Kuwaiti dinar, three fraction digits. Line 1: 7 × 1.23456 = 8.64192, rounded to 8.642.
     * Line 2: 10.0005, rounded half up to 10.001. At 5 %: 18.643, tax 0.93215, rounded to
     * 0.932. Totals 18.643, 0.932, 19.575.
     */
    @Test
    void aDinarInvoiceRoundsToThreeDigits() {
        InvoiceEditor invoice = invoice("KWD");
        SemanticDocument.Builder builder = invoice.builder();
        line(builder, 0, "7", "1.23456", null, "S", "5");
        line(builder, 1, "1", "10.0005", null, "S", "5");

        invoice.derive(Totals.of(UNITS));
        SemanticDocument document = invoice.document();

        assertAmount(document, "/BG-25/0/BT-131", "8.642");
        assertAmount(document, "/BG-25/1/BT-131", "10.001");
        assertAmount(document, "/BG-23/0/BT-116", "18.643");
        assertAmount(document, "/BG-23/0/BT-117", "0.932");
        assertAmount(document, "/BG-22/BT-106", "18.643");
        assertAmount(document, "/BG-22/BT-110", "0.932");
        assertAmount(document, "/BG-22/BT-112", "19.575");
        assertAmount(document, "/BG-22/BT-115", "19.575");
        assertNoFatalFinding(document);
    }

    /**
     * The total VAT amount in the accounting currency carries the fraction digits of that
     * currency and not those of the invoice. Yen accounted in dinar: 119 × 0.002035 =
     * 0.242165, rounded to 0.242. Euro accounted in yen: 0.19 × 162.5 = 30.875, rounded to 31.
     */
    @Test
    void theTotalInTheAccountingCurrencyCarriesTheDigitsOfThatCurrency() {
        InvoiceEditor yen = invoice("JPY");
        line(yen.builder(), 0, "3", "333.33", null, "S", "10");
        line(yen.builder(), 1, "2.5", "120", "1", "S", "8");
        yen.builder().put("/BG-20/0/BT-92", "50").put("/BG-20/0/BT-95", "S")
                .put("/BG-20/0/BT-96", "10").put("/BG-20/0/BT-97", "Discount")
                .put("/BT-6", "KWD").put("/BT-167", "0.002035");
        InvoiceEditor euro = invoice("EUR");
        line(euro.builder(), 0, "1", "1", null, "S", "19");
        euro.builder().put("/BT-6", "JPY").put("/BT-167", "162.5");

        yen.derive(Totals.of(UNITS));
        euro.derive(Totals.of(UNITS));

        assertAmount(yen.document(), "/BG-22/BT-110", "119");
        assertAmount(yen.document(), "/BG-22/BT-111", "0.242");
        assertNoFatalFinding(yen.document());
        assertAmount(euro.document(), "/BG-22/BT-110", "0.19");
        assertAmount(euro.document(), "/BG-22/BT-111", "31");
        assertNoFatalFinding(euro.document());
    }

    @Test
    void aCurrencyTheSnapshotGivesNoMinorUnitIsRefusedByName() {
        for (String currency : List.of("XAU", "ZZZ")) {
            InvoiceEditor invoice = invoice(currency);
            line(invoice.builder(), 0, "1", "100", null, "S", "19");
            DerivationException refused = assertThrows(DerivationException.class,
                    () -> invoice.derive(Totals.of(UNITS)));
            assertEquals("BT-5", refused.term());
            assertTrue(refused.getMessage().contains(currency), refused.getMessage());
            assertTrue(refused.getMessage().contains("iso-4217"), refused.getMessage());
            assertFalse(invoice.document().value(SemanticPath.of("/BG-25/0/BT-131")).isPresent(),
                    "nothing is written before the refusal");
        }
    }

    @Test
    void anAmountWithMoreDigitsThanItsCurrencyIsRefusedAndNotRounded() {
        InvoiceEditor invoice = invoice("JPY");
        line(invoice.builder(), 0, "1", "1000", null, "S", "10");
        invoice.builder().put("/BG-20/0/BT-92", "50.5").put("/BG-20/0/BT-95", "S")
                .put("/BG-20/0/BT-96", "10").put("/BG-20/0/BT-97", "Discount");
        DerivationException refused = assertThrows(DerivationException.class,
                () -> invoice.derive(Totals.of(UNITS)));
        assertEquals("BT-92", refused.term());
        assertEquals("/BG-20/0/BT-92", refused.path().toString());
    }

    private static InvoiceEditor invoice(String currency) {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.builder()
                .put("/BT-1", "RE-2026-0042")
                .put("/BT-2", "2026-03-02")
                .put("/BT-3", "380")
                .put("/BT-5", currency)
                .put("/BT-9", "2026-04-01")
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2026")
                .put("/BG-4/BT-27", "Example GmbH")
                .put("/BG-4/BT-31", "DE123456789")
                .put("/BG-4/BG-5/BT-40", "DE")
                .put("/BG-7/BT-44", "Muster AG")
                .put("/BG-7/BG-8/BT-55", "DE");
        return invoice;
    }

    private static void line(SemanticDocument.Builder builder, int index, String quantity,
                             String price, String base, String category, String rate) {
        String line = "/BG-25/" + index;
        builder.put(line + "/BT-126", String.valueOf(index + 1))
                .put(line + "/BT-129", quantity)
                .put(line + "/BT-130", "C62")
                .put(line + "/BG-29/BT-146", price)
                .put(line + "/BG-30/BT-151", category)
                .put(line + "/BG-30/BT-152", rate)
                .put(line + "/BG-31/BT-153", "Item " + (index + 1));
        if (base != null) {
            builder.put(line + "/BG-29/BT-149", base);
        }
    }

    private static void assertAmount(SemanticDocument document, String path, String expected) {
        assertEquals(expected, text(document, path), path);
    }

    private static String text(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path)).orElseThrow(
                () -> new AssertionError("nothing at " + path)).asString();
    }

    private static void assertNoFatalFinding(SemanticDocument document) {
        List<RuleFinding> fatal = ENGINE.evaluate(document).stream()
                .filter(finding -> finding.severity() == RuleSeverity.FATAL)
                .toList();
        assertEquals(Map.of(), fatal.stream().collect(Collectors.toMap(
                RuleFinding::code, RuleFinding::message, (one, other) -> one)),
                "the rule pack of the edition faults the derived invoice");
    }
}
