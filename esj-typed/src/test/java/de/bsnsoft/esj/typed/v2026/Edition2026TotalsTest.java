package de.bsnsoft.esj.typed.v2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.MinorUnits;
import de.bsnsoft.esj.typed.DerivationException;
import de.bsnsoft.esj.typed.DerivationReport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The totals derivation of the 2026 view over minor units handed in by the test, so that
 * the arithmetic, the matching of stated breakdowns and the refusals are weighed without the
 * rule pack. The same policy over the pack's own snapshot, and the pack's verdict on what it
 * writes, are weighed in the rule module.
 */
class Edition2026TotalsTest {

    private static final MinorUnits UNITS = MinorUnits.of(
            Map.of("EUR", 2, "USD", 2, "JPY", 0, "BHD", 3), "four currencies of a test");

    private static final Totals POLICY = Totals.of(UNITS);

    @Test
    void aBahrainiDinarInvoiceIsRoundedToThreeDigitsOnce() {
        InvoiceEditor invoice = invoice("BHD");
        line(invoice.builder(), 0, "3", "0.33335", "S", "10");
        DerivationReport report = invoice.derive(POLICY);
        SemanticDocument document = invoice.document();

        // 3 × 0.33335 = 1.00005, rounded half up to 1.000; tax 0.1000 is exact.
        assertEquals("1", text(document, "/BG-25/0/BT-131"));
        assertEquals("0.1", text(document, "/BG-23/0/BT-117"));
        assertEquals("1.1", text(document, "/BG-22/BT-115"));
        assertEquals(List.of("/BG-25/0/BT-131"), paths(report.roundings()));
        assertEquals("1.000", report.at("/BG-25/0/BT-131").orElseThrow().value().toPlainString());
        assertTrue(report.at("/BG-25/0/BT-131").orElseThrow().toString()
                .contains("rounded half up to three decimals"));
    }

    @Test
    void theThirdPartyChargesEnterTheAmountDueAndNoOtherTotal() {
        InvoiceEditor invoice = invoice("EUR");
        line(invoice.builder(), 0, "1", "100", "S", "19");
        invoice.builder().put("/BG-22/BG-34/0/BT-179", "10")
                .put("/BG-22/BG-34/0/BT-180", "Deposit")
                .put("/BG-22/BG-34/1/BT-179", "2.5")
                .put("/BG-22/BG-34/1/BT-180", "Fee");
        invoice.derive(POLICY);
        SemanticDocument document = invoice.document();

        assertEquals("100", text(document, "/BG-22/BT-109"));
        assertEquals("119", text(document, "/BG-22/BT-112"));
        assertEquals("131.5", text(document, "/BG-22/BT-115"));
    }

    @Test
    void theAccountingCurrencyTotalIsWrittenOnlyWithCurrencyAndRate() {
        InvoiceEditor both = invoice("EUR");
        line(both.builder(), 0, "1", "100", "S", "19");
        both.builder().put("/BT-6", "JPY").put("/BT-167", "161.237");
        both.derive(POLICY);
        // 19 × 161.237 = 3063.503, rounded to the yen: 3064.
        assertEquals("3064", text(both.document(), "/BG-22/BT-111"));

        InvoiceEditor rateOnly = invoice("EUR");
        line(rateOnly.builder(), 0, "1", "100", "S", "19");
        rateOnly.builder().put("/BT-167", "1.1").put("/BG-22/BT-111", "7");
        rateOnly.derive(POLICY);
        assertEquals("7", text(rateOnly.document(), "/BG-22/BT-111"),
                "without BT-6 the stated value is left as it is");

        InvoiceEditor unknown = invoice("EUR");
        line(unknown.builder(), 0, "1", "100", "S", "19");
        unknown.builder().put("/BT-6", "XAU").put("/BT-167", "0.0005");
        DerivationException refused =
                assertThrows(DerivationException.class, () -> unknown.derive(POLICY));
        assertEquals("BT-6", refused.term());
        assertTrue(refused.getMessage().contains("XAU"));
    }

    @Test
    void anExemptionReasonStatedOnlyOnTheBreakdownIsKept() {
        InvoiceEditor invoice = invoice("EUR");
        line(invoice.builder(), 0, "1", "100", "E", "0");
        invoice.builder().put("/BG-23/0/BT-116", "90").put("/BG-23/0/BT-118", "E")
                .put("/BG-23/0/BT-119", "0").put("/BG-23/0/BT-120", "Exempt under a directive")
                .put("/BG-23/0/BT-121", "VATEX-EU-132");
        DerivationReport report = invoice.derive(POLICY);
        SemanticDocument document = invoice.document();

        assertEquals("100", text(document, "/BG-23/0/BT-116"));
        assertEquals("VATEX-EU-132", text(document, "/BG-23/0/BT-121"));
        assertEquals("Exempt under a directive", text(document, "/BG-23/0/BT-120"));
        assertEquals(List.of("/BG-23/0/BT-116"), paths(report.replacements()));
        assertTrue(report.removals().isEmpty());
    }

    @Test
    void anExemptionReasonOnTheLinesDividesTheCategory() {
        InvoiceEditor invoice = invoice("EUR");
        SemanticDocument.Builder builder = invoice.builder();
        line(builder, 0, "1", "100", "E", "0");
        builder.put("/BG-25/0/BG-30/BT-195", "VATEX-EU-132");
        line(builder, 1, "1", "50", "E", "0");
        builder.put("/BG-25/1/BG-30/BT-195", "VATEX-EU-143")
                .put("/BG-25/1/BG-31/BT-196", "S");
        line(builder, 2, "1", "20", "E", "0");
        builder.put("/BG-23/0/BT-116", "170").put("/BG-23/0/BT-118", "E")
                .put("/BG-23/0/BT-119", "0").put("/BG-23/0/BT-121", "VATEX-EU-79-C");
        DerivationReport report = invoice.derive(POLICY);
        SemanticDocument document = invoice.document();

        assertEquals("100", text(document, "/BG-23/0/BT-116"));
        assertEquals("VATEX-EU-132", text(document, "/BG-23/0/BT-121"));
        assertEquals("50", text(document, "/BG-23/1/BT-116"));
        assertEquals("VATEX-EU-143", text(document, "/BG-23/1/BT-121"));
        assertEquals("S", text(document, "/BG-23/1/BT-210"));
        assertEquals("20", text(document, "/BG-23/2/BT-116"));
        assertFalse(document.value(SemanticPath.of("/BG-23/2/BT-121")).isPresent(),
                "the part without a reason gets a breakdown without one");
        assertFalse(document.value(SemanticPath.of("/BG-23/3/BT-118")).isPresent());
        assertEquals(List.of("/BG-23/0/BT-116", "/BG-23/0/BT-118", "/BG-23/0/BT-119",
                "/BG-23/0/BT-121"), removedPaths(report),
                "a divided category matches the whole combination, and this one no part states");
    }

    @Test
    void aSumWithNothingBehindItIsRemovedAndReported() {
        InvoiceEditor invoice = invoice("EUR");
        line(invoice.builder(), 0, "1", "100", "S", "19");
        invoice.builder().put("/BG-22/BT-107", "3");
        DerivationReport report = invoice.derive(POLICY);

        assertFalse(invoice.document().value(SemanticPath.of("/BG-22/BT-107")).isPresent());
        assertEquals(List.of("/BG-22/BT-107"), removedPaths(report));
    }

    @Test
    void aStatedLineAmountIsKeptCheckedOrReplaced() {
        InvoiceEditor kept = invoice("EUR");
        line(kept.builder(), 0, "3", "12.335", "S", "19");
        kept.builder().put("/BG-25/0/BT-131", "37");
        DerivationException refused =
                assertThrows(DerivationException.class, () -> kept.derive(POLICY));
        assertEquals("BT-131", refused.term());

        DerivationReport report = kept.derive(POLICY.withOverwriteLines(true));
        assertEquals("37.01", text(kept.document(), "/BG-25/0/BT-131"));
        assertTrue(report.replacements().stream()
                .anyMatch(value -> value.path().toString().equals("/BG-25/0/BT-131")));
        assertTrue(POLICY.withOverwriteLines(true).overwriteLines());
        assertFalse(POLICY.overwriteLines());
    }

    @Test
    void whatThePolicyCannotComputeIsRefusedByName() {
        assertRefused("BT-5", builder -> builder.remove(SemanticPath.of("/BT-5")));
        assertRefused("BT-151",
                builder -> builder.remove(SemanticPath.of("/BG-25/0/BG-30/BT-151")));
        assertRefused("BT-152",
                builder -> builder.remove(SemanticPath.of("/BG-25/0/BG-30/BT-152")));
        assertRefused("BT-149", builder -> builder.put("/BG-25/0/BG-29/BT-149", "-1"));
        assertRefused("BT-149", builder -> builder.put("/BG-25/0/BG-29/BT-149", "0"));
        assertRefused("BT-151", builder -> builder.set(
                SemanticPath.of("/BG-25/0/BG-30/BT-151"), SemanticValue.of("F")));
        assertRefused("BT-151", builder -> builder.set(
                SemanticPath.of("/BG-25/0/BG-30/BT-151"), SemanticValue.of("N")));
        assertRefused("BT-184", builder -> builder.put("/BG-23/0/BT-184", "USD")
                .put("/BG-23/0/BT-118", "S").put("/BG-23/0/BT-116", "100"));
        assertRefused("BT-179", builder -> builder.put("/BG-22/BG-34/0/BT-180", "Deposit"));
        assertRefused("BT-113", builder -> builder.put("/BG-22/BT-113", "0.001"));
        assertRefused("BT-136", builder -> builder.put("/BG-25/0/BG-27/0/BT-136", "0.125")
                .put("/BG-25/0/BG-27/0/BT-139", "Loyalty"));
    }

    @Test
    void aBuilderOfAnotherEditionIsRefused() {
        SemanticDocument.Builder other = SemanticDocument.builder()
                .semanticModel(de.bsnsoft.esj.typed.En16931.SEMANTIC_MODEL);
        assertThrows(IllegalArgumentException.class, () -> POLICY.apply(other));
        assertThrows(NullPointerException.class, () -> Totals.of(null));
        assertTrue(POLICY.toString().contains("four currencies of a test"));
    }

    private static void assertRefused(String term,
                                      Consumer<SemanticDocument.Builder> change) {
        InvoiceEditor invoice = invoice("EUR");
        line(invoice.builder(), 0, "1", "100", "S", "19");
        change.accept(invoice.builder());
        DerivationException refused =
                assertThrows(DerivationException.class, () -> invoice.derive(POLICY));
        assertEquals(term, refused.term(), refused.getMessage());
    }

    private static InvoiceEditor invoice(String currency) {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.builder().put("/BT-1", "1").put("/BT-5", currency);
        return invoice;
    }

    private static void line(SemanticDocument.Builder builder, int index, String quantity,
                             String price, String category, String rate) {
        String line = "/BG-25/" + index;
        builder.put(line + "/BT-129", quantity)
                .put(line + "/BG-29/BT-146", price)
                .put(line + "/BG-30/BT-151", category)
                .put(line + "/BG-30/BT-152", rate);
    }

    private static String text(SemanticDocument document, String path) {
        Optional<String> value = document.value(SemanticPath.of(path)).map(v -> v.asString());
        return value.orElseThrow(() -> new AssertionError("nothing at " + path));
    }

    private static List<String> paths(List<DerivationReport.Derived> values) {
        return values.stream().map(value -> value.path().toString()).toList();
    }

    private static List<String> removedPaths(DerivationReport report) {
        return report.removals().stream().map(value -> value.path().toString()).toList();
    }
}
