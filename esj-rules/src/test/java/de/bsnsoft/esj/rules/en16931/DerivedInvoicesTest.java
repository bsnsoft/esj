package de.bsnsoft.esj.rules.en16931;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.json.ReadResult;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.typed.InvoiceLineEditor;
import de.bsnsoft.esj.typed.Totals;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.StructuralValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The derivation policy of {@code esj-typed} against the rule pack of this module.
 *
 * <p>Two things are asserted about each invoice of the table, and the second is the point of
 * the table. The first is arithmetic: every amount below was worked out by hand from the
 * formulas of EN 16931-1 and is compared against what the policy wrote, so that a change in
 * the policy cannot quietly change an amount. The second is agreement: the derived document is
 * written, read back through the format layer, validated against the registry, and run through
 * the EN 16931 rule pack, and none of the four may have anything to say. An arithmetic that
 * satisfies the business rules of the standard is what a caller is really asking for when it
 * asks for the totals to be computed, and that statement can be made only here, where the
 * policy and the pack are both on the class path.
 *
 * <p>The cases walk the ground the formulas cover: one line and many, one VAT rate and
 * several, base quantities that divide and base quantities that do not, line charges and line
 * allowances, document level allowances and charges, the categories under which no VAT is
 * levied, a paid amount, a rounding amount, and a credit note.
 */
class DerivedInvoicesTest {

    /**
     * One invoice of the table: what it is called, what is written into the base invoice to
     * make it, and the amounts it must come to.
     *
     * @param name     what the case is called, which is also its name in the test report
     * @param invoice  what this case adds to {@link #base()}
     * @param expected the canonical content of every derived value, worked out by hand
     */
    private record Case(String name, Consumer<InvoiceEditor> invoice, Map<String, String> expected) {

        @Override
        public String toString() {
            return name;
        }
    }

    private static List<Case> cases() {
        List<Case> cases = new ArrayList<>();

        cases.add(new Case("one line at the standard rate",
                invoice -> invoice.invoiceLine(line(1, "2", "50", "S", "19")),
                amounts("/BG-25/0/BT-131", "100",
                        "/BG-23/0/BT-116", "100", "/BG-23/0/BT-117", "19",
                        "/BG-22/BT-106", "100", "/BG-22/BT-109", "100", "/BG-22/BT-110", "19",
                        "/BG-22/BT-112", "119", "/BG-22/BT-115", "119")));

        cases.add(new Case("one line at the reduced rate",
                invoice -> invoice.invoiceLine(line(1, "3", "10", "S", "7")),
                amounts("/BG-25/0/BT-131", "30",
                        "/BG-23/0/BT-116", "30", "/BG-23/0/BT-117", "2.1",
                        "/BG-22/BT-106", "30", "/BG-22/BT-110", "2.1",
                        "/BG-22/BT-112", "32.1", "/BG-22/BT-115", "32.1")));

        cases.add(new Case("one line, zero rated",
                invoice -> invoice.invoiceLine(line(1, "1", "80", "Z", "0")),
                amounts("/BG-25/0/BT-131", "80",
                        "/BG-23/0/BT-116", "80", "/BG-23/0/BT-117", "0",
                        "/BG-23/0/BT-118", "Z", "/BG-23/0/BT-119", "0",
                        "/BG-22/BT-110", "0", "/BG-22/BT-112", "80", "/BG-22/BT-115", "80")));

        cases.add(new Case("two lines at one rate",
                invoice -> invoice.invoiceLine(line(1, "2", "50", "S", "19"))
                        .invoiceLine(line(2, "4", "25", "S", "19")),
                amounts("/BG-25/0/BT-131", "100", "/BG-25/1/BT-131", "100",
                        "/BG-23/0/BT-116", "200", "/BG-23/0/BT-117", "38",
                        "/BG-22/BT-106", "200", "/BG-22/BT-112", "238")));

        cases.add(new Case("two lines at two rates",
                invoice -> invoice.invoiceLine(line(1, "1", "100", "S", "19"))
                        .invoiceLine(line(2, "1", "200", "S", "7")),
                amounts("/BG-22/BT-106", "300",
                        "/BG-23/0/BT-119", "19", "/BG-23/0/BT-116", "100",
                        "/BG-23/0/BT-117", "19",
                        "/BG-23/1/BT-119", "7", "/BG-23/1/BT-116", "200",
                        "/BG-23/1/BT-117", "14",
                        "/BG-22/BT-110", "33", "/BG-22/BT-112", "333")));

        cases.add(new Case("five lines at two rates",
                invoice -> invoice.invoiceLine(line(1, "10", "12.5", "S", "19"))
                        .invoiceLine(line(2, "3", "99.99", "S", "19"))
                        .invoiceLine(line(3, "1", "0.01", "S", "19"))
                        .invoiceLine(line(4, "7", "4.35", "S", "7"))
                        .invoiceLine(line(5, "2", "1000", "S", "7")),
                amounts("/BG-25/0/BT-131", "125", "/BG-25/1/BT-131", "299.97",
                        "/BG-25/2/BT-131", "0.01", "/BG-25/3/BT-131", "30.45",
                        "/BG-25/4/BT-131", "2000",
                        "/BG-23/0/BT-116", "424.98", "/BG-23/0/BT-117", "80.75",
                        "/BG-23/1/BT-116", "2030.45", "/BG-23/1/BT-117", "142.13",
                        "/BG-22/BT-106", "2455.43", "/BG-22/BT-110", "222.88",
                        "/BG-22/BT-112", "2678.31")));

        cases.add(new Case("a base quantity that divides",
                invoice -> invoice.invoiceLine(line -> line.identifier("1")
                        .quantity(new BigDecimal("250"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("3"))
                                .baseQuantity(new BigDecimal("100"), "H87"))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Screw M4"))),
                amounts("/BG-25/0/BT-131", "7.5",
                        "/BG-23/0/BT-116", "7.5", "/BG-23/0/BT-117", "1.43",
                        "/BG-22/BT-112", "8.93")));

        cases.add(new Case("a base quantity that does not divide",
                invoice -> invoice.invoiceLine(line -> line.identifier("1")
                        .quantity(new BigDecimal("1"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("100"))
                                .baseQuantity(new BigDecimal("3"), "H87"))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("One third of a pallet"))),
                amounts("/BG-25/0/BT-131", "33.33",
                        "/BG-23/0/BT-117", "6.33", "/BG-22/BT-112", "39.66")));

        cases.add(new Case("a price with six fraction digits",
                invoice -> invoice.invoiceLine(line -> line.identifier("1")
                        .quantity(new BigDecimal("7"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("1.234567")))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Cable, per metre"))),
                amounts("/BG-25/0/BG-29/BT-146", "1.234567",
                        "/BG-25/0/BT-131", "8.64",
                        "/BG-23/0/BT-117", "1.64", "/BG-22/BT-112", "10.28")));

        cases.add(new Case("a line amount that has to be rounded",
                invoice -> invoice.invoiceLine(line -> line.identifier("1")
                        .quantity(new BigDecimal("3"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("0.125")))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Washer"))),
                amounts("/BG-25/0/BT-131", "0.38",
                        "/BG-23/0/BT-117", "0.07", "/BG-22/BT-112", "0.45")));

        cases.add(new Case("a line charge",
                invoice -> invoice.invoiceLine(line -> line.identifier("1")
                        .quantity(new BigDecimal("10"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("5")))
                        .charge(charge -> charge.amount(new BigDecimal("7.5")).reason("Packing"))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Sensor module SM-100"))),
                amounts("/BG-25/0/BT-131", "57.5",
                        "/BG-23/0/BT-116", "57.5", "/BG-23/0/BT-117", "10.93",
                        "/BG-22/BT-112", "68.43")));

        cases.add(new Case("a line allowance",
                invoice -> invoice.invoiceLine(line -> line.identifier("1")
                        .quantity(new BigDecimal("10"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("5")))
                        .allowance(allowance -> allowance.amount(new BigDecimal("8"))
                                .reason("Quantity discount"))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Sensor module SM-100"))),
                amounts("/BG-25/0/BT-131", "42",
                        "/BG-23/0/BT-116", "42", "/BG-23/0/BT-117", "7.98",
                        "/BG-22/BT-112", "49.98")));

        cases.add(new Case("a line charge and a line allowance over a base quantity",
                invoice -> invoice.invoiceLine(line -> line.identifier("1")
                        .quantity(new BigDecimal("100"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("3"))
                                .baseQuantity(new BigDecimal("10"), "H87"))
                        .charge(charge -> charge.amount(new BigDecimal("7.5")).reason("Packing"))
                        .allowance(allowance -> allowance.amount(new BigDecimal("2.5"))
                                .reason("Quantity discount"))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Screw M4"))),
                amounts("/BG-25/0/BT-131", "35",
                        "/BG-23/0/BT-116", "35", "/BG-23/0/BT-117", "6.65",
                        "/BG-22/BT-112", "41.65")));

        cases.add(new Case("a document level allowance",
                invoice -> {
                    invoice.invoiceLine(line(1, "1", "100", "S", "19"));
                    invoice.documentLevelAllowance(allowance -> allowance
                            .amount(new BigDecimal("10")).vatCategoryCode("S")
                            .vatRate(new BigDecimal("19")).reason("Loyalty discount"));
                },
                amounts("/BG-22/BT-106", "100", "/BG-22/BT-107", "10", "/BG-22/BT-109", "90",
                        "/BG-23/0/BT-116", "90", "/BG-23/0/BT-117", "17.1",
                        "/BG-22/BT-110", "17.1", "/BG-22/BT-112", "107.1")));

        cases.add(new Case("a document level charge",
                invoice -> {
                    invoice.invoiceLine(line(1, "1", "100", "S", "19"));
                    invoice.documentLevelCharge(charge -> charge
                            .amount(new BigDecimal("10")).vatCategoryCode("S")
                            .vatRate(new BigDecimal("19")).reason("Freight"));
                },
                amounts("/BG-22/BT-106", "100", "/BG-22/BT-108", "10", "/BG-22/BT-109", "110",
                        "/BG-23/0/BT-116", "110", "/BG-23/0/BT-117", "20.9",
                        "/BG-22/BT-112", "130.9")));

        cases.add(new Case("a document level allowance and charge at two rates",
                invoice -> {
                    invoice.invoiceLine(line(1, "1", "100", "S", "19"))
                            .invoiceLine(line(2, "1", "100", "S", "7"));
                    invoice.documentLevelAllowance(allowance -> allowance
                                    .amount(new BigDecimal("10")).vatCategoryCode("S")
                                    .vatRate(new BigDecimal("19")).reason("Loyalty discount"))
                            .documentLevelCharge(charge -> charge
                                    .amount(new BigDecimal("20")).vatCategoryCode("S")
                                    .vatRate(new BigDecimal("7")).reason("Freight"));
                },
                amounts("/BG-22/BT-106", "200", "/BG-22/BT-107", "10", "/BG-22/BT-108", "20",
                        "/BG-22/BT-109", "210",
                        "/BG-23/0/BT-116", "90", "/BG-23/0/BT-117", "17.1",
                        "/BG-23/1/BT-116", "120", "/BG-23/1/BT-117", "8.4",
                        "/BG-22/BT-110", "25.5", "/BG-22/BT-112", "235.5")));

        cases.add(new Case("exempt from VAT",
                invoice -> {
                    invoice.invoiceLine(line(1, "1", "100", "E", "0"));
                    invoice.vatBreakdown(breakdown -> breakdown.vatCategoryCode("E")
                            .vatRate(BigDecimal.ZERO)
                            .exemptionReasonText("Exempt under national law"));
                },
                amounts("/BG-23/0/BT-116", "100", "/BG-23/0/BT-117", "0",
                        "/BG-23/0/BT-120", "Exempt under national law",
                        "/BG-22/BT-110", "0", "/BG-22/BT-112", "100")));

        cases.add(new Case("reverse charge",
                invoice -> {
                    invoice.buyer().vatIdentifier("DE987654321");
                    invoice.invoiceLine(line(1, "2", "125", "AE", "0"));
                    invoice.vatBreakdown(breakdown -> breakdown.vatCategoryCode("AE")
                            .vatRate(BigDecimal.ZERO)
                            .exemptionReasonText("Reverse charge")
                            .exemptionReasonCode("VATEX-EU-AE"));
                },
                amounts("/BG-25/0/BT-131", "250",
                        "/BG-23/0/BT-116", "250", "/BG-23/0/BT-117", "0",
                        "/BG-23/0/BT-121", "VATEX-EU-AE",
                        "/BG-22/BT-110", "0", "/BG-22/BT-112", "250")));

        cases.add(new Case("standard rated beside reverse charge",
                invoice -> {
                    invoice.buyer().vatIdentifier("DE987654321");
                    invoice.invoiceLine(line(1, "40", "85", "S", "19"))
                            .invoiceLine(line(2, "12", "120", "AE", "0"));
                    invoice.vatBreakdown(breakdown -> breakdown.vatCategoryCode("AE")
                            .vatRate(BigDecimal.ZERO)
                            .exemptionReasonText("Reverse charge")
                            .exemptionReasonCode("VATEX-EU-AE"));
                },
                amounts("/BG-25/0/BT-131", "3400", "/BG-25/1/BT-131", "1440",
                        "/BG-23/0/BT-118", "S", "/BG-23/0/BT-116", "3400",
                        "/BG-23/0/BT-117", "646",
                        "/BG-23/1/BT-118", "AE", "/BG-23/1/BT-116", "1440",
                        "/BG-23/1/BT-117", "0",
                        "/BG-22/BT-106", "4840", "/BG-22/BT-110", "646",
                        "/BG-22/BT-112", "5486")));

        cases.add(new Case("not subject to VAT",
                invoice -> {
                    // BR-O-2: an invoice that states this category carries no VAT identifier,
                    // so the seller is identified by its legal registration instead (BR-CO-26).
                    invoice.seller().vatIdentifier("").legalRegistrationIdentifier("HRB 12345");
                    invoice.invoiceLine(line -> line.identifier("1")
                            .quantity(new BigDecimal("1"), "C62")
                            .price(price -> price.netPrice(new BigDecimal("40")))
                            .vat(vat -> vat.vatCategoryCode("O"))
                            .item(item -> item.name("Membership fee")));
                    invoice.vatBreakdown(breakdown -> breakdown.vatCategoryCode("O")
                            .exemptionReasonText("Not subject to VAT"));
                },
                amounts("/BG-25/0/BT-131", "40",
                        "/BG-23/0/BT-118", "O", "/BG-23/0/BT-116", "40",
                        "/BG-23/0/BT-117", "0",
                        "/BG-22/BT-110", "0", "/BG-22/BT-112", "40")));

        cases.add(new Case("export outside the European Union",
                invoice -> {
                    invoice.invoiceLine(line(1, "5", "300", "G", "0"));
                    invoice.vatBreakdown(breakdown -> breakdown.vatCategoryCode("G")
                            .vatRate(BigDecimal.ZERO)
                            .exemptionReasonCode("VATEX-EU-G"));
                },
                amounts("/BG-25/0/BT-131", "1500",
                        "/BG-23/0/BT-116", "1500", "/BG-23/0/BT-117", "0",
                        "/BG-22/BT-112", "1500")));

        cases.add(new Case("a paid amount",
                invoice -> {
                    invoice.invoiceLine(line(1, "1", "100", "S", "19"));
                    invoice.documentTotals(totals -> totals.paidAmount(new BigDecimal("19")));
                },
                amounts("/BG-22/BT-112", "119", "/BG-22/BT-113", "19", "/BG-22/BT-115", "100")));

        cases.add(new Case("a rounding amount",
                invoice -> {
                    invoice.invoiceLine(line(1, "3", "33.33", "S", "19"));
                    invoice.documentTotals(totals -> totals.roundingAmount(new BigDecimal("0.01")));
                },
                amounts("/BG-25/0/BT-131", "99.99", "/BG-23/0/BT-117", "19",
                        "/BG-22/BT-112", "118.99", "/BG-22/BT-114", "0.01",
                        "/BG-22/BT-115", "119")));

        cases.add(new Case("a paid amount and a rounding amount together",
                invoice -> {
                    invoice.invoiceLine(line(1, "1", "100", "S", "19"));
                    invoice.documentTotals(totals -> totals.paidAmount(new BigDecimal("50"))
                            .roundingAmount(new BigDecimal("0.02")));
                },
                amounts("/BG-22/BT-112", "119", "/BG-22/BT-115", "69.02")));

        cases.add(new Case("a credit note",
                invoice -> {
                    invoice.typeCode("381");
                    invoice.invoiceLine(line(1, "2", "149.5", "S", "19"));
                },
                amounts("/BG-25/0/BT-131", "299",
                        "/BG-23/0/BT-116", "299", "/BG-23/0/BT-117", "56.81",
                        "/BG-22/BT-112", "355.81", "/BG-22/BT-115", "355.81")));

        cases.add(new Case("a line amount that is negative",
                invoice -> invoice.invoiceLine(line(1, "-2", "50", "S", "19")),
                amounts("/BG-25/0/BT-131", "-100",
                        "/BG-23/0/BT-116", "-100", "/BG-23/0/BT-117", "-19",
                        "/BG-22/BT-106", "-100", "/BG-22/BT-112", "-119")));

        return List.copyOf(cases);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void aDerivedInvoiceHoldsTheAmountsOfTheStandardAndBreaksNoRule(Case derived) {
        InvoiceEditor invoice = base();
        derived.invoice().accept(invoice);

        invoice.derive(Totals.STANDARD);
        SemanticDocument document = invoice.document();

        for (Map.Entry<String, String> expected : derived.expected().entrySet()) {
            assertEquals(expected.getValue(), content(document, expected.getKey()),
                    expected.getKey());
        }
        assertEquals(List.of(), formatFindings(document), "ESJ format, layer L1");
        assertEquals(List.of(),
                StructuralValidator.validate(document, Registry.en16931()).findings(),
                "the registry, layers L2 and L3");
        assertEquals(List.of(), fatal(document), "the EN 16931 rule pack");
    }

    @Test
    void theTableCoversTheGroundTheFormulasCover() {
        assertTrue(cases().size() >= 20, "the table has " + cases().size() + " cases");
        assertEquals(cases().size(),
                cases().stream().map(Case::name).distinct().count(),
                "every case has its own name");
    }

    /**
     * Returns the invoice every case is written into: complete in everything that is not an
     * amount, so that a finding of the pack is about the derivation and about nothing else.
     */
    private static InvoiceEditor base() {
        // Two classes of this repository are called En16931: the entry point of the typed
        // editor, which is meant here, and the pack of this package. The name is written out
        // rather than imported, so that neither has to give way to the other.
        InvoiceEditor invoice = de.bsnsoft.esj.typed.En16931.newInvoice();
        invoice.invoiceNumber("RE-2026-0001")
                .issueDate(LocalDate.of(2026, 1, 15))
                .typeCode("380")
                .currencyCode("EUR")
                .paymentDueDate(LocalDate.of(2026, 2, 15))
                .processControl(control ->
                        control.specificationIdentifier("urn:cen.eu:en16931:2017"));
        invoice.seller()
                .name("Example GmbH")
                .vatIdentifier("DE123456789")
                .postalAddress(address -> address.city("Musterstadt").countryCode("DE"));
        invoice.buyer()
                .name("Muster AG")
                .postalAddress(address -> address.city("Beispielstadt").countryCode("DE"));
        return invoice;
    }

    /** One invoice line stated the short way: quantity, net price, VAT category and rate. */
    private static Consumer<InvoiceLineEditor> line(
            int number, String quantity, String price, String category, String rate) {
        return line -> line.identifier(String.valueOf(number))
                .quantity(new BigDecimal(quantity), "C62")
                .price(editor -> editor.netPrice(new BigDecimal(price)))
                .vat(vat -> vat.vatCategoryCode(category).vatRate(new BigDecimal(rate)))
                .item(item -> item.name("Item " + number));
    }

    private static Map<String, String> amounts(String... pathsAndContents) {
        Map<String, String> expected = new LinkedHashMap<>();
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            expected.put(pathsAndContents[i], pathsAndContents[i + 1]);
        }
        return Collections.unmodifiableMap(expected);
    }

    private static String content(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path))
                .map(SemanticValue::canonicalContent)
                .orElse("(absent)");
    }

    /** What the format layer says about the document once it has been written and read back. */
    private static List<Finding> formatFindings(SemanticDocument document) {
        ReadResult result = EsjReader.strict()
                .readWithFindings(EsjWriter.pretty().toBytes(document));
        assertTrue(result.isWellFormed());
        return result.findings();
    }

    private static List<RuleFinding> fatal(SemanticDocument document) {
        return Pack.ENGINE.evaluate(document).stream().filter(RuleFinding::fatal).toList();
    }
}
