package de.bsnsoft.esj.b2c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.typed.InvoiceEditor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The three gross authoring policies, over invoices whose amounts are computed by hand in the
 * assertions: 99.99 with VAT at 19 per cent is 84.025210 net a unit and 84.03 a line, and the
 * cent the rounding of the line loses comes back as the invoice rounding amount.
 *
 * <p>Every derived invoice is checked as a whole: layer L1 by writing and reading it back,
 * layers L2 and L3 against the core registry with the extension loaded, and the business rules
 * of EN 16931-1 against the pack {@code en16931/1.3.16}, whose conditions BR-CO-10 to BR-CO-17
 * are the arithmetic of the totals.
 */
class GrossAuthoringTest {

    @Test
    void oneUnitPricedGrossAtNineteenPerCentLosesACentToTheLineAndGetsItBackAsBt114() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("99.99");
        gross.displayedGrossTotal("99.99");

        AuthoringReport report = gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
        SemanticDocument document = invoice.document();

        // 9999 / 119 = 84.025210084..., cut half up to six fraction digits.
        assertEquals("84.02521", Invoices.at(document, "/BG-25/0/BG-29/BT-146"));
        assertEquals(1, report.cuts().size(), "the quotient does not terminate");
        assertEquals("BT-146", report.cuts().get(0).term());
        // 84.025210 x 1, rounded half up to two decimals.
        assertEquals("84.03", Invoices.at(document, "/BG-25/0/BT-131"));
        // 84.03 x 19 / 100 = 15.9657, rounded half up to two decimals.
        assertEquals("15.97", Invoices.at(document, "/BG-23/0/BT-117"));
        assertEquals("100", Invoices.at(document, "/BG-22/BT-112"));
        // 99.99 - 100.00: the cent the line lost.
        assertEquals("-0.01", Invoices.at(document, "/BG-22/BT-114"));
        assertEquals("99.99", Invoices.at(document, "/BG-22/BT-115"));
        assertEquals(Optional.of(new BigDecimal("-0.01")), report.roundingAmount());
        assertEquals("GROSS_UNIT_AUTHORING", report.policy());

        Invoices.isAConformantInvoice(document);
        Invoices.arithmeticWasChecked(document);
        assertEquals(List.of(),
                B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING));
    }

    @Test
    void theThreeLineConsumerInvoiceOfTheExamplesComesOutOfTheGrossFigures() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        Invoices.line(invoice, "2", "Shower hose 1.5 m");
        Invoices.line(invoice, "3", "Sealing tape 12 m");

        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("99.99").displayedGrossLineTotal("99.99")
                .displayedLineVatAmount("15.96");
        gross.line(1).displayedGrossUnitPrice("19.99").displayedGrossLineTotal("39.98")
                .displayedLineVatAmount("6.38");
        gross.line(2).displayedGrossUnitPrice("4.50").displayedGrossLineTotal("13.50")
                .displayedLineVatAmount("2.16");
        gross.displayedGrossTotal("153.47");

        AuthoringReport report = gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
        SemanticDocument document = invoice.document();

        // 1999 / 119 = 16.798319327..., 450 / 119 = 3.781512605...
        assertEquals("16.798319", Invoices.at(document, "/BG-25/1/BG-29/BT-146"));
        assertEquals("3.781513", Invoices.at(document, "/BG-25/2/BG-29/BT-146"));
        // 16.798319 x 2 = 33.596638 and 3.781513 x 3 = 11.344539, each rounded once.
        assertEquals("33.6", Invoices.at(document, "/BG-25/1/BT-131"));
        assertEquals("11.34", Invoices.at(document, "/BG-25/2/BT-131"));
        // 84.03 + 33.60 + 11.34 = 128.97, and 128.97 x 19 / 100 = 24.5043.
        assertEquals("128.97", Invoices.at(document, "/BG-22/BT-106"));
        assertEquals("24.5", Invoices.at(document, "/BG-22/BT-110"));
        assertEquals("153.47", Invoices.at(document, "/BG-22/BT-115"));
        assertNull(Invoices.at(document, "/BG-22/BT-114"),
                "the lines come to the agreed total exactly, so there is no rounding amount");
        assertEquals(Optional.empty(), report.roundingAmount(),
                "the report says what the document says: there was no rounding step");
        assertTrue(report.notes().stream().anyMatch(note -> note.contains("153.47")),
                report.notes().toString());

        assertEquals(new String(Examples.canonical("b2c-gross"), StandardCharsets.UTF_8),
                new String(EsjWriter.canonical().toBytes(document), StandardCharsets.UTF_8),
                "the policy writes the example of the repository, figure for figure");
        Invoices.isAConformantInvoice(document);
        assertEquals(List.of(),
                B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING));
    }

    @Test
    void sevenPerCentAndZeroPerCentAndTwoRatesInOneInvoice() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "S", "7", "Cook book");
        Invoices.line(invoice, "1", "Z", "0", "Postage stamp");
        Invoices.line(invoice, "1", "Sensor module SM-100");

        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("9.99");
        gross.line(1).displayedGrossUnitPrice("5.00");
        gross.line(2).displayedGrossUnitPrice("99.99");
        gross.displayedGrossTotal("114.98");

        gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
        SemanticDocument document = invoice.document();

        // 999 / 107 = 9.336448598...; 500 / 100 = 5 exactly; 9999 / 119 = 84.025210084...
        assertEquals("9.336449", Invoices.at(document, "/BG-25/0/BG-29/BT-146"));
        assertEquals("5", Invoices.at(document, "/BG-25/1/BG-29/BT-146"));
        assertEquals("9.34", Invoices.at(document, "/BG-25/0/BT-131"));
        assertEquals("5", Invoices.at(document, "/BG-25/1/BT-131"));
        assertEquals("84.03", Invoices.at(document, "/BG-25/2/BT-131"));
        // Three breakdowns in the order the lines use them: S 7, Z 0, S 19.
        assertEquals("0.65", Invoices.at(document, "/BG-23/0/BT-117"));
        assertEquals("0", Invoices.at(document, "/BG-23/1/BT-117"));
        assertEquals("15.97", Invoices.at(document, "/BG-23/2/BT-117"));
        // 9.34 + 5.00 + 84.03 = 98.37, VAT 0.65 + 0 + 15.97 = 16.62.
        assertEquals("98.37", Invoices.at(document, "/BG-22/BT-106"));
        assertEquals("16.62", Invoices.at(document, "/BG-22/BT-110"));
        assertEquals("114.99", Invoices.at(document, "/BG-22/BT-112"));
        assertEquals("-0.01", Invoices.at(document, "/BG-22/BT-114"));
        assertEquals("114.98", Invoices.at(document, "/BG-22/BT-115"));

        Invoices.isAConformantInvoice(document);
        Invoices.arithmeticWasChecked(document);
    }

    @Test
    void aPriceBaseQuantityOfTenIsRefusedUntilTheOptionsSayItIsMeant() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "3", "Sealing tape 12 m");
        invoice.invoiceLines().get(0).price(price -> price.baseQuantity(BigDecimal.TEN, "H87"));
        Gross.on(invoice).line(0).displayedGrossUnitPrice("4.50");

        PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                () -> Gross.on(invoice).derive(GrossAuthoring.GROSS_UNIT_AUTHORING));
        assertEquals("no item price base quantity other than one", refused.precondition());
        assertEquals("BT-149", refused.term());

        Gross.on(invoice).derive(GrossAuthoring.GROSS_UNIT_AUTHORING.with(
                AuthoringOptions.standard().withBaseQuantity(true)));
        SemanticDocument document = invoice.document();

        // 4.50 buys ten, so 3.781513 is the net price of ten and 3 of them come to 1.1344539.
        assertEquals("3.781513", Invoices.at(document, "/BG-25/0/BG-29/BT-146"));
        assertEquals("1.13", Invoices.at(document, "/BG-25/0/BT-131"));
        Invoices.isAConformantInvoice(document);
    }

    @Test
    void aLineAllowanceIsRefusedUntilTheOptionsSayHowItIsMeant() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        invoice.invoiceLines().get(0).allowance(allowance -> allowance
                .amount(new BigDecimal("10.00")).reason("Introductory discount"));
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("99.99");

        PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                () -> gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING));
        assertEquals("no line allowance and no line charge", refused.precondition());
        assertEquals("BG-27", refused.term());

        gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING.with(
                AuthoringOptions.standard().withLineAllowancesAndCharges(true)));
        SemanticDocument document = invoice.document();

        // The gross figure is the price before the allowance: 84.025210 - 10.00 = 74.025210.
        assertEquals("84.02521", Invoices.at(document, "/BG-25/0/BG-29/BT-146"));
        assertEquals("74.03", Invoices.at(document, "/BG-25/0/BT-131"));
        Invoices.isAConformantInvoice(document);
    }

    @Test
    void aDocumentLevelChargeIsAddedAfterTheLinesAndTheRoundingStillLands() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        invoice.documentLevelCharge(charge -> charge
                .amount(new BigDecimal("5.00")).vatCategoryCode("S")
                .vatRate(new BigDecimal("19")).reason("Carriage"));
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("99.99");
        gross.displayedGrossTotal("105.94");

        AuthoringReport report = gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
        SemanticDocument document = invoice.document();

        // 84.03 + 5.00 = 89.03 taxable, 89.03 x 19 / 100 = 16.9157 -> 16.92.
        assertEquals("89.03", Invoices.at(document, "/BG-22/BT-109"));
        assertEquals("16.92", Invoices.at(document, "/BG-22/BT-110"));
        assertEquals("105.95", Invoices.at(document, "/BG-22/BT-112"));
        assertEquals("-0.01", Invoices.at(document, "/BG-22/BT-114"));
        assertEquals("105.94", Invoices.at(document, "/BG-22/BT-115"));
        assertTrue(report.rounding().orElseThrow().why().contains("105.95"));
        Invoices.isAConformantInvoice(document);
    }

    @Test
    void aCreditNotePricedGrossIsDerivedTheSameWay() {
        InvoiceEditor invoice = Invoices.consumer();
        invoice.typeCode("381");
        Invoices.line(invoice, "1", "Shower fitting SF-20, returned");
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("99.99");
        gross.displayedGrossTotal("99.99");

        gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
        SemanticDocument document = invoice.document();

        assertEquals("381", Invoices.at(document, "/BT-3"));
        assertEquals("84.03", Invoices.at(document, "/BG-25/0/BT-131"));
        assertEquals("99.99", Invoices.at(document, "/BG-22/BT-115"));
        Invoices.isAConformantInvoice(document);
    }

    @Test
    void aMissingGrossFigureIsARefusalAndNoGuess() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");

        PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                () -> Gross.on(invoice).derive(GrossAuthoring.GROSS_UNIT_AUTHORING));
        assertEquals("GROSS_UNIT_AUTHORING", refused.policy());
        assertEquals("BT-B2C-001", refused.term());
        assertEquals("/BG-25/0", refused.path().toString());
    }

    @Test
    void withoutAnAgreedTotalNoRoundingAmountIsWrittenAndTheReportSaysSo() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        Gross.on(invoice).line(0).displayedGrossUnitPrice("99.99");

        AuthoringReport report =
                Gross.on(invoice).derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
        SemanticDocument document = invoice.document();

        assertEquals(Optional.empty(), report.roundingAmount());
        assertNull(Invoices.at(document, "/BG-22/BT-114"));
        assertEquals("100", Invoices.at(document, "/BG-22/BT-115"));
        assertTrue(report.notes().get(0).contains("BT-B2C-010"));
        Invoices.isAConformantInvoice(document);
    }

    @Test
    void aRoundingDifferenceBeyondTheLimitTheOptionsNameIsRefused() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("99.99");
        gross.displayedGrossTotal("80.00");

        PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                () -> gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING.with(
                        AuthoringOptions.standard()
                                .withMaxRoundingAmount(new BigDecimal("0.05")))));
        assertEquals("a rounding amount within the limit of this run", refused.precondition());
        assertEquals("BT-114", refused.term());
    }

    @Test
    void aLinePricedGrossAsAWholeGivesTheLineFirstAndTheUnitPriceFromIt() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Repair kit, complete");
        Invoices.line(invoice, "3", "Sealing tape 12 m");
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossLineTotal("29.99");
        gross.line(1).displayedGrossLineTotal("13.50");
        gross.displayedGrossTotal("43.49");

        gross.derive(GrossAuthoring.GROSS_LINE_AUTHORING);
        SemanticDocument document = invoice.document();

        // 2999 / 119 = 25.20168..., rounded once to 25.20; over one unit that is the price.
        assertEquals("25.2", Invoices.at(document, "/BG-25/0/BT-131"));
        assertEquals("25.2", Invoices.at(document, "/BG-25/0/BG-29/BT-146"));
        // 1350 / 119 = 11.34453..., rounded once to 11.34; over three units, 3.78 exactly.
        assertEquals("11.34", Invoices.at(document, "/BG-25/1/BT-131"));
        assertEquals("3.78", Invoices.at(document, "/BG-25/1/BG-29/BT-146"));
        // 36.54 taxable, 36.54 x 19 / 100 = 6.9426 -> 6.94.
        assertEquals("36.54", Invoices.at(document, "/BG-22/BT-106"));
        assertEquals("6.94", Invoices.at(document, "/BG-22/BT-110"));
        assertEquals("43.48", Invoices.at(document, "/BG-22/BT-112"));
        assertEquals("0.01", Invoices.at(document, "/BG-22/BT-114"));
        assertEquals("43.49", Invoices.at(document, "/BG-22/BT-115"));

        Invoices.isAConformantInvoice(document);
        Invoices.arithmeticWasChecked(document);
        assertEquals(List.of(),
                B2cConsistency.check(document, GrossAuthoring.GROSS_LINE_AUTHORING));
    }

    @Test
    void aQuantitySoLargeThatSixFractionDigitsMissTheLineIsRefusedNamingTheScale() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "3000000", "Sealing tape 12 m");
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossLineTotal("13.50");

        PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                () -> gross.derive(GrossAuthoring.GROSS_LINE_AUTHORING));
        assertEquals("an item net price scale the invoiced quantity carries back",
                refused.precondition());
        assertTrue(refused.getMessage().contains("withNetPriceScale"));

        gross.derive(GrossAuthoring.GROSS_LINE_AUTHORING.with(
                AuthoringOptions.standard().withNetPriceScale(12)));
        assertEquals("11.34", Invoices.at(invoice.document(), "/BG-25/0/BT-131"));
    }

    @Test
    void anAgreedTotalIsSpreadOverTheLinesWithTheRemainderOnTheLast() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Consulting, day one");
        Invoices.line(invoice, "1", "Consulting, day two");
        Invoices.line(invoice, "1", "Consulting, day three");
        for (int index = 0; index < 3; index++) {
            invoice.invoiceLines().get(index).price(price -> price.netPrice(BigDecimal.TEN));
        }
        Gross gross = Gross.on(invoice);
        gross.displayedGrossTotal("100.00");

        AuthoringReport report = gross.derive(GrossAuthoring.GROSS_TOTAL_AUTHORING);
        SemanticDocument document = invoice.document();

        // Three equal bases of 11.90 gross: 33.33, 33.33 and the remainder 33.34.
        List<String> shares = report.notes().stream()
                .filter(note -> note.startsWith("the line at")).toList();
        assertEquals(3, shares.size(), report.notes().toString());
        assertTrue(shares.get(2).contains("33.34"), shares.get(2));
        // 3333 / 119 does not terminate at two decimals, and the note says so.
        assertTrue(report.notes().stream()
                        .anyMatch(note -> note.contains("rounded half up to 28.01")),
                report.notes().toString());
        // 3333 / 119 = 28.008403... -> 28.01; 3334 / 119 = 28.016806... -> 28.02.
        assertEquals("28.01", Invoices.at(document, "/BG-25/0/BT-131"));
        assertEquals("28.01", Invoices.at(document, "/BG-25/1/BT-131"));
        assertEquals("28.02", Invoices.at(document, "/BG-25/2/BT-131"));
        // 84.04 taxable, 84.04 x 19 / 100 = 15.9676 -> 15.97.
        assertEquals("84.04", Invoices.at(document, "/BG-22/BT-106"));
        assertEquals("100.01", Invoices.at(document, "/BG-22/BT-112"));
        assertEquals("-0.01", Invoices.at(document, "/BG-22/BT-114"));
        assertEquals("100", Invoices.at(document, "/BG-22/BT-115"));
        assertNull(Invoices.at(document, "/BG-25/0/BT-B2C-002"),
                "a share of an agreed total is no figure the customer was shown");

        Invoices.isAConformantInvoice(document);
        Invoices.arithmeticWasChecked(document);
        assertEquals(List.of(),
                B2cConsistency.check(document, GrossAuthoring.GROSS_TOTAL_AUTHORING));
    }

    @Test
    void anAgreedTotalIsNotSpreadOverALineThatCarriesAGrossFigureOfItsOwn() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Consulting, day one");
        invoice.invoiceLines().get(0).price(price -> price.netPrice(BigDecimal.TEN));
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("11.90");
        gross.displayedGrossTotal("11.90");

        PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                () -> gross.derive(GrossAuthoring.GROSS_TOTAL_AUTHORING));
        assertEquals("no gross figure on a line", refused.precondition());
        assertEquals("BT-B2C-001", refused.term());
        assertTrue(refused.getMessage().contains("GROSS_UNIT_AUTHORING"));
    }

    @Test
    void anAgreedTotalIsNotSpreadOverAnInvoiceThatCarriesADocumentLevelCharge() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Consulting, day one");
        invoice.invoiceLines().get(0).price(price -> price.netPrice(BigDecimal.TEN));
        invoice.documentLevelCharge(charge -> charge
                .amount(new BigDecimal("5.00")).vatCategoryCode("S")
                .vatRate(new BigDecimal("19")).reason("Carriage"));
        Gross gross = Gross.on(invoice);
        gross.displayedGrossTotal("20.00");

        PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                () -> gross.derive(GrossAuthoring.GROSS_TOTAL_AUTHORING));
        assertEquals("no document level allowance and no document level charge",
                refused.precondition());
        assertEquals("BG-21", refused.term());
    }

    @Test
    void anInvoiceWithNoLineIsRefusedByEveryPolicy() {
        for (GrossAuthoring policy : List.of(GrossAuthoring.GROSS_UNIT_AUTHORING,
                GrossAuthoring.GROSS_LINE_AUTHORING, GrossAuthoring.GROSS_TOTAL_AUTHORING)) {
            InvoiceEditor invoice = Invoices.consumer();
            Gross.on(invoice).displayedGrossTotal("10.00");
            PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                    () -> Gross.on(invoice).derive(policy), policy.name());
            assertEquals("BG-25", refused.term());
        }
    }

    @Test
    void theOptionsRefuseAScaleBelowSixFractionDigits() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthoringOptions.standard().withNetPriceScale(2));
        assertEquals(6, AuthoringOptions.standard().netPriceScale());
        assertEquals("GROSS_UNIT_AUTHORING[netPriceScale=6, lineAllowancesAndCharges=false,"
                + " baseQuantity=false]", GrossAuthoring.GROSS_UNIT_AUTHORING.toString());
    }

    @Test
    void aPrepaymentIsNoRoundingAndLeavesTheAmountDueAtWhatIsStillOwed() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        invoice.documentTotals(totals -> totals.paidAmount(new BigDecimal("50.00")));
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("99.99");
        gross.displayedGrossTotal("99.99");

        AuthoringReport report = gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING);
        SemanticDocument document = invoice.document();

        // The customer agreed to 99.99 with VAT and has paid 50.00 of it in advance.
        assertEquals("100", Invoices.at(document, "/BG-22/BT-112"));
        assertEquals("50", Invoices.at(document, "/BG-22/BT-113"));
        assertEquals("-0.01", Invoices.at(document, "/BG-22/BT-114"));
        assertEquals("49.99", Invoices.at(document, "/BG-22/BT-115"));
        assertEquals(Optional.of(new BigDecimal("-0.01")), report.roundingAmount());

        Invoices.isAConformantInvoice(document);
        Invoices.arithmeticWasChecked(document);
        assertEquals(List.of(),
                B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING));
    }

    @Test
    void anAgreedTotalThatIsNoRoundingApartIsRefusedWithoutTheCallerNamingALimit() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("99.99");
        gross.displayedGrossTotal("999.99");

        PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                () -> gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING));
        assertEquals("a rounding amount within the limit of this run", refused.precondition());
        assertEquals("BT-114", refused.term());
        // One line and one VAT breakdown are rounded once each, so a cent each is the bound.
        assertTrue(refused.getMessage().contains("beyond the 0.02"), refused.getMessage());
    }

    @Test
    void aLineOfACategoryWhoseRateIsZeroAndStatesNoneIsRefusedNamingBt152() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "E", null, "Insurance cover");
        Gross.on(invoice).line(0).displayedGrossUnitPrice("100.00");

        PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                () -> Gross.on(invoice).derive(GrossAuthoring.GROSS_UNIT_AUTHORING));
        assertEquals("a VAT rate on every line of a category whose rate is zero",
                refused.precondition());
        assertEquals("BT-152", refused.term());
    }

    @Test
    void theOverlayWritesNoInvoiceLineAndSaysSoWhereThereIsNoneToWriteInto() {
        InvoiceEditor invoice = Invoices.consumer();

        IndexOutOfBoundsException refused = assertThrows(IndexOutOfBoundsException.class,
                () -> Gross.on(invoice).line(0));
        assertTrue(refused.getMessage().contains("BG-25"), refused.getMessage());
        assertTrue(refused.getMessage().contains("the core editor writes the lines"),
                refused.getMessage());
        assertThrows(IndexOutOfBoundsException.class,
                () -> B2c.edit(invoice).line(0, line -> line.displayedGrossUnitPrice(
                        BigDecimal.ONE)));
    }

}
