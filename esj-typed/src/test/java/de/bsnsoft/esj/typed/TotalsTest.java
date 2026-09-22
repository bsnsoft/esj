package de.bsnsoft.esj.typed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The derivation policy {@link Totals}: the arithmetic of EN 16931-1 over the parts of an
 * invoice, the one rounding step per amount, and the refusals.
 *
 * <p>Two of the tests are about the example documents of the repository rather than about
 * numbers written into this file. They are the strongest thing that can be asserted here: an
 * example is a golden file that a dozen other tests already compare against, so an arithmetic
 * that reproduces it byte for byte is an arithmetic that agrees with everything the
 * repository says about those invoices.
 */
class TotalsTest {

    /**
     * The examples whose amounts a derivation writes in full.
     *
     * <p>{@code minimal} and {@code extended} are left out on purpose: they are zero rated
     * invoices that state no invoice total VAT amount (BT-110), which BR-CO-15 allows and which
     * this policy does not do — a derived invoice states every document total, including a VAT
     * amount of zero. {@code extension-depth} carries no invoice line at all.
     */
    private static final List<String> DERIVABLE = List.of(
            "standard-invoice", "multiple-lines", "allowances", "charges", "self-billed",
            "credit-note", "b2c-gross");

    /** The document totals a derivation computes, as opposed to those a caller states. */
    private static final List<String> DERIVED_TOTALS = List.of(
            "/BG-22/BT-106", "/BG-22/BT-107", "/BG-22/BT-108", "/BG-22/BT-109", "/BG-22/BT-110",
            "/BG-22/BT-111", "/BG-22/BT-112", "/BG-22/BT-115");

    @Test
    void theStandardInvoiceExampleIsWrittenWithoutOneTotalTypedByHand() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceNumber("RE-2026-0042")
                .issueDate(LocalDate.of(2026, 2, 3))
                .typeCode("380")
                .currencyCode("EUR")
                .paymentDueDate(LocalDate.of(2026, 3, 5))
                .buyerReference("KOST-4711")
                .contractReference("RV-2025-118")
                .purchaseOrderReference("BE-2026-0091")
                .buyerAccountingReference("Cost centre 4711")
                .paymentTerms("Payable within 30 days without deduction.")
                .note(n -> n.note("Delivery and performance according to contract RV-2025-118.\n"
                        + "Please quote the invoice number with the payment."))
                .processControl(c -> c.specificationIdentifier("urn:cen.eu:en16931:2017"));

        invoice.seller()
                .name("Example GmbH")
                .tradingName("Example Systems")
                .legalRegistrationIdentifier("HRB 12345")
                .vatIdentifier("DE123456789")
                .additionalLegalInformation("Managing director: Alex Beispiel, register court"
                        + " Beispielstadt HRB 12345")
                .electronicAddress("invoices@example.invalid", "EM")
                .postalAddress(a -> a.addressLine1("Musterweg 12").city("Beispielstadt")
                        .postCode("10117").countryCode("DE"))
                .contact(c -> c.name("Accounts receivable").telephone("+49 30 1234567")
                        .email("billing@example.invalid"))
                .identifiers().add("4399901000018", "0088");

        invoice.buyer()
                .name("Muster AG")
                .identifier("4399902000024", "0088")
                .vatIdentifier("DE987654321")
                .electronicAddress("rechnung@muster.invalid", "EM")
                .postalAddress(a -> a.addressLine1("Beispielallee 3").city("Musterstadt")
                        .postCode("20095").countryCode("DE"))
                .contact(c -> c.name("Purchasing").telephone("+49 40 7654321")
                        .email("purchasing@muster.invalid"));

        invoice.delivery(d -> d.actualDeliveryDate(LocalDate.of(2026, 1, 30)));

        invoice.paymentInstructions(p -> p
                .paymentMeansTypeCode("58")
                .remittanceInformation("RE-2026-0042")
                .creditTransfer(t -> t.accountIdentifier("DE89370400440532013000")
                        .accountName("Example GmbH")
                        .serviceProviderIdentifier("COBADEFFXXX")));

        invoice.invoiceLine(line -> line
                        .identifier("1")
                        .quantity(new BigDecimal("10"), "H87")
                        .purchaseOrderLineReference("10")
                        .price(p -> p.netPrice(new BigDecimal("25")))
                        .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(i -> i.name("Sensor module SM-100")
                                .description("Sensor module, housing grey")
                                .sellerIdentifier("SM-100")))
                .invoiceLine(line -> line
                        .identifier("2")
                        .quantity(new BigDecimal("3"), "DAY")
                        .purchaseOrderLineReference("20")
                        .price(p -> p.netPrice(new BigDecimal("480")))
                        .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(i -> i.name("On-site installation")))
                .invoiceLine(line -> line
                        .identifier("3")
                        .note("Two sessions of four hours.")
                        .quantity(new BigDecimal("8"), "HUR")
                        .price(p -> p.netPrice(new BigDecimal("95")))
                        .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(i -> i.name("Training")));

        DerivationReport report = invoice.derive(Totals.STANDARD);

        assertArrayEquals(Examples.canonical("standard-invoice"),
                EsjWriter.canonical().toBytes(invoice.document()));
        assertEquals(List.of(), report.roundings());
        assertEquals(new BigDecimal("2915.50"), report.at("/BG-22/BT-115").orElseThrow().value());
    }

    private static List<String> derivable() {
        return DERIVABLE;
    }

    @ParameterizedTest
    @MethodSource("derivable")
    void anExampleIsReproducedFromItsOwnPrices(String name) {
        SemanticDocument example = Examples.document(name);
        SemanticDocument.Builder builder = withoutDerivedAmounts(example);

        Totals.derive(builder, TotalsOptions.standard());

        assertEquals(example.values(), builder.build().values());
    }

    @Test
    void everyExampleIsEitherDerivedOrLeftOutForAStatedReason() {
        List<String> excluded = List.of("minimal", "extended", "extension-depth");

        assertTrue(Examples.NAMES.containsAll(DERIVABLE));
        assertTrue(Examples.NAMES.containsAll(excluded));
        assertEquals(Examples.NAMES.size(), DERIVABLE.size() + excluded.size());
    }

    @Test
    void theLineNetAmountIsTheFormulaOfTheStandard() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("100"), "H87")
                .price(p -> p.netPrice(new BigDecimal("3")).baseQuantity(new BigDecimal("10"), "H87"))
                .charge(c -> c.amount(new BigDecimal("7.5")).reason("Packing"))
                .allowance(a -> a.amount(new BigDecimal("2.5")).reason("Discount"))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));

        invoice.derive(Totals.STANDARD);

        assertEquals(new BigDecimal("35"), decimal(invoice, "/BG-25/0/BT-131"));
        assertEquals(new BigDecimal("35"), decimal(invoice, "/BG-23/0/BT-116"));
        assertEquals(new BigDecimal("6.65"), decimal(invoice, "/BG-23/0/BT-117"));
    }

    @Test
    void aBaseQuantityIsOneWhereTheInvoiceDoesNotStateIt() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("3"), "H87")
                .price(p -> p.netPrice(new BigDecimal("19.99")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));

        invoice.derive(Totals.STANDARD);

        assertEquals(new BigDecimal("59.97"), decimal(invoice, "/BG-25/0/BT-131"));
    }

    @Test
    void theLineNetAmountIsRoundedHalfUpOnceAndTheRoundingIsReported() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("3"), "H87")
                .price(p -> p.netPrice(new BigDecimal("0.125")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));

        DerivationReport report = invoice.derive(Totals.STANDARD);

        assertEquals(new BigDecimal("0.38"), decimal(invoice, "/BG-25/0/BT-131"));
        assertEquals(List.of("/BG-25/0/BT-131", "/BG-23/0/BT-117"),
                report.roundings().stream().map(value -> value.path().toString()).toList());
        assertTrue(report.at("/BG-25/0/BT-131").orElseThrow().rounded());
        assertFalse(report.at("/BG-22/BT-106").orElseThrow().rounded());
    }

    @Test
    void aNonTerminatingQuotientIsRoundedRatherThanRefused() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .price(p -> p.netPrice(new BigDecimal("100"))
                        .baseQuantity(new BigDecimal("3"), "H87"))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));

        invoice.derive(Totals.STANDARD);

        assertEquals(new BigDecimal("33.33"), decimal(invoice, "/BG-25/0/BT-131"));
    }

    @Test
    void thePricesQuantitiesAndRatesTheCallerGaveAreLeftAtTheirOwnScale() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("2.5"), "H87")
                .price(p -> p.netPrice(new BigDecimal("12.345678"))
                        .baseQuantity(new BigDecimal("1.5"), "H87"))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19.5"))));

        invoice.derive(Totals.STANDARD);

        SemanticDocument document = invoice.document();
        assertEquals("2.5", content(document, "/BG-25/0/BT-129"));
        assertEquals("12.345678", content(document, "/BG-25/0/BG-29/BT-146"));
        assertEquals("1.5", content(document, "/BG-25/0/BG-29/BT-149"));
        assertEquals("19.5", content(document, "/BG-25/0/BG-30/BT-152"));
        assertEquals("19.5", content(document, "/BG-23/0/BT-119"));
        assertEquals(new BigDecimal("20.58"), decimal(invoice, "/BG-25/0/BT-131"));
    }

    @Test
    void oneBreakdownPerCategoryAndRateInTheOrderTheyAreMetIn() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceLine(line -> line
                        .quantity(new BigDecimal("1"), "H87")
                        .price(p -> p.netPrice(new BigDecimal("100")))
                        .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("7"))))
                .invoiceLine(line -> line
                        .quantity(new BigDecimal("1"), "H87")
                        .price(p -> p.netPrice(new BigDecimal("200")))
                        .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))))
                .invoiceLine(line -> line
                        .quantity(new BigDecimal("2"), "H87")
                        .price(p -> p.netPrice(new BigDecimal("50")))
                        .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("7"))));

        invoice.derive(Totals.STANDARD);

        SemanticDocument document = invoice.document();
        assertEquals("7", content(document, "/BG-23/0/BT-119"));
        assertEquals(new BigDecimal("200"), decimal(invoice, "/BG-23/0/BT-116"));
        assertEquals(new BigDecimal("14"), decimal(invoice, "/BG-23/0/BT-117"));
        assertEquals("19", content(document, "/BG-23/1/BT-119"));
        assertEquals(new BigDecimal("200"), decimal(invoice, "/BG-23/1/BT-116"));
        assertEquals(new BigDecimal("38"), decimal(invoice, "/BG-23/1/BT-117"));
        assertEquals(new BigDecimal("52"), decimal(invoice, "/BG-22/BT-110"));
        assertTrue(document.value(SemanticPath.of("/BG-23/2/BT-116")).isEmpty());
    }

    @Test
    void documentLevelAllowancesAndChargesMoveTheirOwnCategory() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .price(p -> p.netPrice(new BigDecimal("1000")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        invoice.documentLevelAllowance(a -> a.amount(new BigDecimal("100"))
                        .vatCategoryCode("S").vatRate(new BigDecimal("19")).reason("Rebate"))
                .documentLevelCharge(c -> c.amount(new BigDecimal("30"))
                        .vatCategoryCode("S").vatRate(new BigDecimal("19")).reason("Freight"));

        invoice.derive(Totals.STANDARD);

        assertEquals(new BigDecimal("1000"), decimal(invoice, "/BG-22/BT-106"));
        assertEquals(new BigDecimal("100"), decimal(invoice, "/BG-22/BT-107"));
        assertEquals(new BigDecimal("30"), decimal(invoice, "/BG-22/BT-108"));
        assertEquals(new BigDecimal("930"), decimal(invoice, "/BG-22/BT-109"));
        assertEquals(new BigDecimal("930"), decimal(invoice, "/BG-23/0/BT-116"));
        assertEquals(new BigDecimal("176.7"), decimal(invoice, "/BG-23/0/BT-117"));
        assertEquals(new BigDecimal("1106.7"), decimal(invoice, "/BG-22/BT-112"));
    }

    @Test
    void aSumOverNoGroupIsAnAbsentTermAndNotAZero() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .price(p -> p.netPrice(new BigDecimal("10")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        invoice.documentTotals(t -> t.sumOfAllowances(new BigDecimal("999"))
                .sumOfCharges(new BigDecimal("999")));

        invoice.derive(Totals.STANDARD);

        SemanticDocument document = invoice.document();
        assertTrue(document.value(SemanticPath.of("/BG-22/BT-107")).isEmpty());
        assertTrue(document.value(SemanticPath.of("/BG-22/BT-108")).isEmpty());
    }

    @Test
    void aCategoryUnderWhichNoVatIsLeviedGetsZeroAndKeepsItsExemptionReason() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("2"), "H87")
                .price(p -> p.netPrice(new BigDecimal("60")))
                .vat(v -> v.vatCategoryCode("AE").vatRate(BigDecimal.ZERO)));
        invoice.vatBreakdown(v -> v.vatCategoryCode("AE").vatRate(BigDecimal.ZERO)
                .exemptionReasonText("Reverse charge")
                .exemptionReasonCode("VATEX-EU-AE"));

        invoice.derive(Totals.STANDARD);

        SemanticDocument document = invoice.document();
        assertEquals(new BigDecimal("120"), decimal(invoice, "/BG-23/0/BT-116"));
        assertEquals(BigDecimal.ZERO, decimal(invoice, "/BG-23/0/BT-117"));
        assertEquals("Reverse charge", content(document, "/BG-23/0/BT-120"));
        assertEquals("VATEX-EU-AE", content(document, "/BG-23/0/BT-121"));
        assertEquals(BigDecimal.ZERO, decimal(invoice, "/BG-22/BT-110"));
        assertEquals(new BigDecimal("120"), decimal(invoice, "/BG-22/BT-112"));
    }

    @Test
    void aCategoryWithoutARateStatesNoRateInItsBreakdown() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .price(p -> p.netPrice(new BigDecimal("40")))
                .vat(v -> v.vatCategoryCode("O")));

        invoice.derive(Totals.STANDARD);

        SemanticDocument document = invoice.document();
        assertEquals("O", content(document, "/BG-23/0/BT-118"));
        assertTrue(document.value(SemanticPath.of("/BG-23/0/BT-119")).isEmpty());
        assertEquals(BigDecimal.ZERO, decimal(invoice, "/BG-23/0/BT-117"));
    }

    @Test
    void thePaidAndRoundingAmountsOfTheInvoiceCarryIntoTheAmountDue() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .price(p -> p.netPrice(new BigDecimal("100")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        invoice.documentTotals(t -> t.paidAmount(new BigDecimal("19"))
                .roundingAmount(new BigDecimal("0.02")));

        invoice.derive(Totals.STANDARD);

        assertEquals(new BigDecimal("119"), decimal(invoice, "/BG-22/BT-112"));
        assertEquals(new BigDecimal("100.02"), decimal(invoice, "/BG-22/BT-115"));
    }

    @Test
    void theAccountingCurrencyTotalIsWrittenOnlyWhereAnExchangeRateIsGiven() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .price(p -> p.netPrice(new BigDecimal("1000")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        invoice.vatAccountingCurrencyCode("SEK");

        invoice.derive(Totals.STANDARD);
        assertTrue(invoice.document().value(SemanticPath.of("/BG-22/BT-111")).isEmpty());

        DerivationReport report = invoice.derive(Totals.of(TotalsOptions.standard()
                .withVatAccountingCurrencyRate(new BigDecimal("11.4321"))));

        assertEquals(new BigDecimal("2172.1"), decimal(invoice, "/BG-22/BT-111"));
        assertTrue(report.at("/BG-22/BT-111").orElseThrow().rounded());
    }

    @Test
    void aStatedLineNetAmountIsKeptAndMayBeReplacedOnRequest() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("2"), "H87")
                .netAmount(new BigDecimal("24"))
                .price(p -> p.netPrice(new BigDecimal("12")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));

        DerivationReport kept = invoice.derive(Totals.STANDARD);

        assertTrue(kept.at("/BG-25/0/BT-131").isEmpty());
        assertEquals(new BigDecimal("24"), decimal(invoice, "/BG-25/0/BT-131"));

        invoice.invoiceLines().get(0).price(p -> p.netPrice(new BigDecimal("13")));
        DerivationReport replaced = invoice.derive(
                Totals.of(TotalsOptions.standard().withOverwriteLines(true)));

        assertTrue(replaced.at("/BG-25/0/BT-131").isPresent());
        assertEquals(new BigDecimal("26"), decimal(invoice, "/BG-25/0/BT-131"));
        assertEquals(new BigDecimal("26"), decimal(invoice, "/BG-22/BT-106"));
    }

    @Test
    void aTotalWrittenByHandIsRewrittenAndSaidToHaveBeenRewritten() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("2"), "H87")
                .price(p -> p.netPrice(new BigDecimal("12")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        invoice.documentTotals(t -> t.sumOfLineNetAmounts(new BigDecimal("1")));

        DerivationReport report = invoice.derive(Totals.STANDARD);

        assertEquals(new BigDecimal("24"), decimal(invoice, "/BG-22/BT-106"));
        assertTrue(report.at("/BG-22/BT-106").orElseThrow().replaced());
        assertTrue(report.replacements().stream()
                .anyMatch(value -> value.term().equals("BT-106")));
        assertFalse(report.at("/BG-22/BT-109").orElseThrow().replaced());
        assertEquals(List.of(), report.removals());
    }

    @Test
    void aBreakdownWrittenByHandForTheSameCategoryIsRewrittenAndSaidToHaveBeenRewritten() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("2"), "H87")
                .price(p -> p.netPrice(new BigDecimal("12")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        invoice.vatBreakdown(b -> b.vatCategoryCode("S").vatRate(new BigDecimal("19"))
                .taxableAmount(new BigDecimal("500")).taxAmount(new BigDecimal("95")));

        DerivationReport report = invoice.derive(Totals.STANDARD);

        assertEquals(new BigDecimal("24"), decimal(invoice, "/BG-23/0/BT-116"));
        assertTrue(report.at("/BG-23/0/BT-116").orElseThrow().replaced());
        assertTrue(report.at("/BG-23/0/BT-117").orElseThrow().replaced());
        assertEquals(List.of(), report.removals());
    }

    @Test
    void aStatedSumOfAllowancesWithNoAllowanceBehindItIsRemovedAndReported() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("2"), "H87")
                .price(p -> p.netPrice(new BigDecimal("12")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        invoice.documentTotals(t -> t.sumOfAllowances(new BigDecimal("50"))
                .sumOfCharges(new BigDecimal("70")));

        DerivationReport report = invoice.derive(Totals.STANDARD);

        assertEquals(Optional.empty(), invoice.builder().value(SemanticPath.of("/BG-22/BT-107")));
        assertEquals(Optional.empty(), invoice.builder().value(SemanticPath.of("/BG-22/BT-108")));
        assertEquals(List.of("/BG-22/BT-107 removed, it stated 50 (the invoice carries no document"
                        + " level allowance (BG-20), so the sum of them is not a term of it)",
                        "/BG-22/BT-108 removed, it stated 70 (the invoice carries no document"
                        + " level charge (BG-21), so the sum of them is not a term of it)"),
                report.removals().stream().map(Object::toString).toList());
        assertEquals(List.of("BT-107", "BT-108"),
                report.removals().stream().map(DerivationReport.Removed::term).toList());
    }

    @Test
    void aBreakdownForACategoryNoPartOfTheInvoiceUsesIsRemovedAndReported() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("2"), "H87")
                .price(p -> p.netPrice(new BigDecimal("12")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        invoice.vatBreakdown(b -> b.vatCategoryCode("S").vatRate(new BigDecimal("19"))
                .taxableAmount(new BigDecimal("24")).taxAmount(new BigDecimal("4.56")));
        invoice.vatBreakdown(b -> b.vatCategoryCode("Z").taxableAmount(new BigDecimal("500"))
                .taxAmount(new BigDecimal("0")));

        DerivationReport report = invoice.derive(Totals.STANDARD);

        assertEquals(1, invoice.builder().occurrences(SemanticPath.group("/BG-23")));
        assertEquals(List.of(), report.replacements());
        assertEquals(List.of("/BG-23/1/BT-116", "/BG-23/1/BT-117", "/BG-23/1/BT-118"),
                report.removals().stream().map(value -> value.path().toString()).toList());
        assertEquals(List.of("500", "0", "Z"),
                report.removals().stream().map(DerivationReport.Removed::value).toList());
        assertTrue(report.removals().get(0).why().contains("categorised \"Z\""));
    }

    @Test
    void aBreakdownThatSaysWhichCategoryItIsNotIsRemovedAndReported() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("2"), "H87")
                .price(p -> p.netPrice(new BigDecimal("12")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        invoice.builder().put("/BG-23/0/BT-116", SemanticValue.ofDecimal(new BigDecimal("500")));

        DerivationReport report = invoice.derive(Totals.STANDARD);

        assertEquals(List.of("/BG-23/0/BT-116"),
                report.removals().stream().map(value -> value.path().toString()).toList());
        assertTrue(report.removals().get(0).why().contains("no VAT category code (BT-118)"));
    }

    @Test
    void theExemptionReasonOfABreakdownSurvivesTheRebuildOfItsCategory() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("2"), "H87")
                .price(p -> p.netPrice(new BigDecimal("12")))
                .vat(v -> v.vatCategoryCode("Z")));
        invoice.vatBreakdown(b -> b.vatCategoryCode("Z")
                .exemptionReasonText("Zero rated goods"));

        DerivationReport report = invoice.derive(Totals.STANDARD);

        assertEquals(List.of(), report.removals());
        assertEquals("Zero rated goods", invoice.builder()
                .value(SemanticPath.of("/BG-23/0/BT-120")).orElseThrow().asString());
    }

    @Test
    void aLineWhoseStatedAmountContradictsItsPriceIsRefused() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("2"), "H87")
                .netAmount(new BigDecimal("25"))
                .price(p -> p.netPrice(new BigDecimal("12")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));

        DerivationException refusal =
                assertThrows(DerivationException.class, () -> invoice.derive(Totals.STANDARD));

        assertEquals("BT-131", refusal.term());
        assertEquals(SemanticPath.of("/BG-25/0/BT-131"), refusal.path());
        assertTrue(refusal.getMessage().contains("24"), refusal.getMessage());
    }

    @Test
    void anInvoiceWithoutALineIsRefused() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceNumber("RE-1");

        DerivationException refusal =
                assertThrows(DerivationException.class, () -> invoice.derive(Totals.STANDARD));

        assertEquals("BG-25", refusal.term());
        assertEquals(SemanticPath.root(), refusal.path());
    }

    @Test
    void aLineWithoutAVatCategoryIsRefused() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .price(p -> p.netPrice(new BigDecimal("10"))));

        DerivationException refusal =
                assertThrows(DerivationException.class, () -> invoice.derive(Totals.STANDARD));

        assertEquals("BT-151", refusal.term());
        assertEquals(SemanticPath.group("/BG-25/0"), refusal.path());
    }

    @Test
    void aRatedCategoryWithoutARateIsRefused() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .price(p -> p.netPrice(new BigDecimal("10")))
                .vat(v -> v.vatCategoryCode("S")));

        DerivationException refusal =
                assertThrows(DerivationException.class, () -> invoice.derive(Totals.STANDARD));

        assertEquals("BT-152", refusal.term());
    }

    @Test
    void aLineWithoutAPriceOrAQuantityIsRefused() {
        InvoiceEditor withoutPrice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        assertEquals("BT-146", assertThrows(DerivationException.class,
                () -> withoutPrice.derive(Totals.STANDARD)).term());

        InvoiceEditor withoutQuantity = lines(line -> line
                .price(p -> p.netPrice(new BigDecimal("10")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        assertEquals("BT-129", assertThrows(DerivationException.class,
                () -> withoutQuantity.derive(Totals.STANDARD)).term());
    }

    @Test
    void aBaseQuantityWithoutAPriceIsRefused() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .netAmount(new BigDecimal("10"))
                .price(p -> p.baseQuantity(new BigDecimal("10"), "H87"))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));

        DerivationException refusal =
                assertThrows(DerivationException.class, () -> invoice.derive(Totals.STANDARD));

        assertEquals("BT-146", refusal.term());
        assertTrue(refusal.getMessage().contains("BT-149"), refusal.getMessage());
    }

    @Test
    void aBaseQuantityOfZeroIsRefused() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .price(p -> p.netPrice(new BigDecimal("10")).baseQuantity(BigDecimal.ZERO, "H87"))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));

        assertEquals("BT-149", assertThrows(DerivationException.class,
                () -> invoice.derive(Totals.STANDARD)).term());
    }

    @Test
    void aDocumentLevelAllowanceWithoutACategoryIsRefused() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("1"), "H87")
                .price(p -> p.netPrice(new BigDecimal("10")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));
        invoice.documentLevelAllowance(a -> a.amount(new BigDecimal("1")).reason("Rebate"));

        DerivationException refusal =
                assertThrows(DerivationException.class, () -> invoice.derive(Totals.STANDARD));

        assertEquals("BT-95", refusal.term());
        assertEquals(SemanticPath.group("/BG-20/0"), refusal.path());
    }

    @Test
    void theReportNamesEveryValueItWroteAndHowItGotThere() {
        InvoiceEditor invoice = lines(line -> line
                .quantity(new BigDecimal("4"), "H87")
                .price(p -> p.netPrice(new BigDecimal("2.5")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19"))));

        DerivationReport report = invoice.derive(Totals.STANDARD);

        assertEquals(List.of("/BG-25/0/BT-131", "/BG-23/0/BT-116", "/BG-23/0/BT-117",
                        "/BG-22/BT-106", "/BG-22/BT-109", "/BG-22/BT-110", "/BG-22/BT-112",
                        "/BG-22/BT-115"),
                report.values().stream().map(value -> value.path().toString()).toList());
        assertEquals(Optional.empty(), report.at("/BG-22/BT-107"));
        assertTrue(report.at("/BG-22/BT-112").orElseThrow().how().contains("BT-109"));
        assertTrue(report.at("/BG-22/BT-112").orElseThrow().toString()
                .startsWith("/BG-22/BT-112 = 11.90"));
    }

    @Test
    void theStandardPolicyIsTheOneWithNothingVaried() {
        assertSame(Totals.STANDARD.options(), TotalsOptions.standard());
        assertFalse(TotalsOptions.standard().overwriteLines());
        assertEquals(Optional.empty(), TotalsOptions.standard().vatAccountingCurrencyRate());
        assertEquals("Totals[overwriteLines=false, vatAccountingCurrencyRate=none]",
                Totals.STANDARD.toString());
        assertThrows(IllegalArgumentException.class,
                () -> TotalsOptions.standard().withVatAccountingCurrencyRate(BigDecimal.ZERO));
    }

    @Test
    void theStaticFormWritesIntoABuilderThatNoEditorWasOpenedOver() {
        SemanticDocument.Builder builder = SemanticDocument.builder()
                .put("/BG-25/0/BT-129", SemanticValue.ofDecimal(new BigDecimal("2")))
                .put("/BG-25/0/BG-29/BT-146", SemanticValue.ofDecimal(new BigDecimal("5.5")))
                .put("/BG-25/0/BG-30/BT-151", "Z")
                .put("/BG-25/0/BG-30/BT-152", SemanticValue.ofDecimal(BigDecimal.ZERO));

        DerivationReport report = Totals.derive(builder, TotalsOptions.standard());

        assertEquals(new BigDecimal("11.00"), report.at("/BG-22/BT-115").orElseThrow().value());
        assertEquals(SemanticValue.ofDecimal(new BigDecimal("11")),
                builder.build().value(SemanticPath.of("/BG-22/BT-112")).orElseThrow());
    }

    private static InvoiceEditor lines(Consumer<InvoiceLineEditor> line) {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceLine(line);
        return invoice;
    }

    @Test
    void aDocumentOfAnotherEditionIsRefusedRatherThanComputed() {
        SemanticDocument.Builder builder = SemanticDocument.builder()
                .semanticModel("EN16931-1:2026")
                .put(SemanticPath.of("/BG-25/0/BT-129"), SemanticValue.of("1"))
                .put(SemanticPath.of("/BG-25/0/BG-29/BT-146"), SemanticValue.of("100"));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Totals.derive(builder, TotalsOptions.standard()));

        assertTrue(refused.getMessage().contains(En16931.SEMANTIC_MODEL)
                        && refused.getMessage().contains("EN16931-1:2026"),
                "the refusal names both editions: " + refused.getMessage());
        assertTrue(builder.build().value(SemanticPath.of("/BG-22/BT-112")).isEmpty(),
                "a refused derivation writes nothing");
    }

    private static BigDecimal decimal(InvoiceEditor invoice, String path) {
        return invoice.builder().value(SemanticPath.of(path)).orElseThrow().asDecimal();
    }

    private static String content(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path)).orElseThrow().canonicalContent();
    }

    /**
     * Returns the example with everything a derivation computes taken out of it: the invoice
     * line net amounts, the taxable and tax amounts of the VAT breakdown, and the document
     * totals. What stays is what a caller writes — prices, quantities, categories, rates, and
     * the exemption reasons no arithmetic can produce.
     */
    private static SemanticDocument.Builder withoutDerivedAmounts(SemanticDocument example) {
        SemanticDocument.Builder builder = example.toBuilder();
        for (SemanticPath path : example.values().keySet()) {
            String text = path.toString();
            if (text.endsWith("/BT-131")
                    || text.matches("/BG-23/\\d+/BT-11[67]")
                    || DERIVED_TOTALS.contains(text)) {
                builder.remove(path);
            }
        }
        return builder;
    }
}
