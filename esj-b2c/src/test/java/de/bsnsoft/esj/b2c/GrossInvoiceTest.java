package de.bsnsoft.esj.b2c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.invoice.Draft;
import de.bsnsoft.esj.invoice.Line;
import de.bsnsoft.esj.invoice.Party;
import de.bsnsoft.esj.invoice.PaymentMeans;
import de.bsnsoft.esj.invoice.PaymentTerms;
import de.bsnsoft.esj.invoice.Vat;
import de.bsnsoft.esj.invoice.code.Country;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.invoice.code.Unit;
import de.bsnsoft.esj.typed.build.Profile;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The domain API for a shop that prices gross: the same words, the same {@code InvoiceResult}
 * and the same {@code BuildReport} the net domain API returns, with the gross figure per line
 * and the authoring report beside them.
 */
class GrossInvoiceTest {

    @Test
    void oneLinePricedGrossPerUnitBuildsAConformantInvoice() {
        GrossResult result = shop(GrossInvoice.draft(Profile.EN16931))
                .line(GrossItem.perUnit(Line.of("Shower fitting SF-20")
                        .quantity(1, Unit.PIECE).vat(Vat.standard(19)), "99.99"))
                .displayedGrossTotal("99.99")
                .build(GrossAuthoring.GROSS_UNIT_AUTHORING);

        SemanticDocument document = result.document();
        assertTrue(result.ok(), () -> String.join("\n", result.report().lines()));
        assertEquals("99.99", Invoices.at(document, "/BG-25/0/BT-B2C-001"));
        assertEquals("84.02521", Invoices.at(document, "/BG-25/0/BG-29/BT-146"));
        assertEquals("84.03", Invoices.at(document, "/BG-25/0/BT-131"));
        assertEquals("-0.01", Invoices.at(document, "/BG-22/BT-114"));
        assertEquals("99.99", Invoices.at(document, "/BG-22/BT-115"));
        assertEquals("GROSS_UNIT_AUTHORING", result.authoring().policy());
        Invoices.isAConformantInvoice(document);
        assertEquals(List.of(),
                B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING));
    }

    @Test
    void threeLinesAtMixedRatesPricedGrossPerUnit() {
        GrossResult result = shop(GrossInvoice.draft(Profile.EN16931))
                .line(GrossItem.perUnit(Line.of("Shower fitting SF-20")
                        .quantity(1, Unit.PIECE).vat(Vat.standard(19)), "99.99"))
                .line(GrossItem.perUnit(Line.of("Cook book")
                        .quantity(2, Unit.PIECE).vat(Vat.standard(7)), "9.99"))
                .line(GrossItem.perUnit(Line.of("Sealing tape 12 m")
                        .quantity(3, Unit.PIECE).vat(Vat.standard(19)), "4.50"))
                .displayedGrossTotal("133.47")
                .build(GrossAuthoring.GROSS_UNIT_AUTHORING);

        SemanticDocument document = result.document();
        assertTrue(result.ok(), () -> String.join("\n", result.report().lines()));
        // 999 / 107 = 9.336448598... over two units is 18.672898 -> 18.67.
        assertEquals("9.336449", Invoices.at(document, "/BG-25/1/BG-29/BT-146"));
        assertEquals("18.67", Invoices.at(document, "/BG-25/1/BT-131"));
        // 84.03 + 11.34 = 95.37 at 19 per cent, 18.67 at 7 per cent.
        assertEquals("95.37", Invoices.at(document, "/BG-23/0/BT-116"));
        assertEquals("18.12", Invoices.at(document, "/BG-23/0/BT-117"));
        assertEquals("18.67", Invoices.at(document, "/BG-23/1/BT-116"));
        assertEquals("1.31", Invoices.at(document, "/BG-23/1/BT-117"));
        // 114.04 + 19.43 = 133.47, which is what the customer was shown.
        assertEquals("114.04", Invoices.at(document, "/BG-22/BT-109"));
        assertEquals("19.43", Invoices.at(document, "/BG-22/BT-110"));
        assertNull(Invoices.at(document, "/BG-22/BT-114"));
        assertEquals("133.47", Invoices.at(document, "/BG-22/BT-115"));
        Invoices.isAConformantInvoice(document);
    }

    @Test
    void aPackagePricedGrossPerLine() {
        GrossResult result = shop(GrossInvoice.draft(Profile.EN16931))
                .line(GrossItem.perLine(Line.of("Repair kit, complete")
                        .quantity(1, Unit.PIECE).vat(Vat.standard(19)), "29.99"))
                .displayedGrossTotal("29.99")
                .build(GrossAuthoring.GROSS_LINE_AUTHORING);

        SemanticDocument document = result.document();
        assertTrue(result.ok(), () -> String.join("\n", result.report().lines()));
        assertEquals("29.99", Invoices.at(document, "/BG-25/0/BT-B2C-002"));
        assertNull(Invoices.at(document, "/BG-25/0/BT-B2C-001"),
                "no unit price with VAT was shown, so none is recorded");
        assertEquals("25.2", Invoices.at(document, "/BG-25/0/BT-131"));
        assertEquals("29.99", Invoices.at(document, "/BG-22/BT-115"));
        Invoices.isAConformantInvoice(document);
    }

    @Test
    void aCreditNotePricedGross() {
        GrossResult result = shop(GrossInvoice.creditNote(Profile.EN16931))
                .edit(draft -> draft.precedingInvoice("RE-2026-0731",
                        LocalDate.of(2026, 4, 14)))
                .line(GrossItem.perUnit(Line.of("Shower fitting SF-20, returned")
                        .quantity(1, Unit.PIECE).vat(Vat.standard(19)), "99.99"))
                .displayedGrossTotal("99.99")
                .build(GrossAuthoring.GROSS_UNIT_AUTHORING);

        SemanticDocument document = result.document();
        assertTrue(result.ok(), () -> String.join("\n", result.report().lines()));
        assertEquals("381", Invoices.at(document, "/BT-3"));
        assertEquals("99.99", Invoices.at(document, "/BG-22/BT-115"));
        Invoices.isAConformantInvoice(document);
    }

    @Test
    void theDraftUnderneathTakesEveryMemberOfTheDomainApiAndTheVatAmountShownPerLine() {
        GrossInvoice invoice = shop(GrossInvoice.draft(Profile.EN16931))
                .line(GrossItem.perUnit(Line.of("Shower fitting SF-20")
                                .quantity(1, Unit.PIECE).vat(Vat.standard(19)), "99.99")
                        .displayedLineVatAmount("15.96"));

        assertEquals("15.96", Invoices.at(invoice.document(), "/BG-25/0/BT-B2C-003"));
        assertTrue(invoice.draft() instanceof Draft);
        assertEquals("RE-2026-0731", Invoices.at(invoice.document(), "/BT-1"));
    }

    @Test
    void aLineWithoutTheFigureThePolicyReadsIsARefusalAndNoGuess() {
        GrossInvoice invoice = shop(GrossInvoice.draft(Profile.EN16931))
                .line(GrossItem.of(Line.of("Shower fitting SF-20")
                        .quantity(1, Unit.PIECE).vat(Vat.standard(19))));

        PolicyPreconditionException refused = assertThrows(PolicyPreconditionException.class,
                () -> invoice.build(GrossAuthoring.GROSS_UNIT_AUTHORING));
        assertEquals("BT-B2C-001", refused.term());
    }


    @Test
    void theReportOfTheDomainApiChecksTheTermsOfTheExtensionInsteadOfPassingThemOver() {
        GrossResult result = shop(GrossInvoice.draft(Profile.EN16931))
                .line(GrossItem.perUnit(Line.of("Shower fitting SF-20")
                        .quantity(1, Unit.PIECE).vat(Vat.standard(19)), "99.99"))
                .displayedGrossTotal("99.99")
                .build(GrossAuthoring.GROSS_UNIT_AUTHORING);

        assertEquals(List.of(), result.report().findings().stream()
                        .map(finding -> finding.code().code() + " " + finding.path()).toList(),
                "the check runs against the core registry with this extension loaded");
    }

    /** The members every invoice of this test carries, written through the domain API. */
    private static GrossInvoice shop(GrossInvoice invoice) {
        return invoice.edit(header());
    }

    private static Consumer<Draft> header() {
        return draft -> draft
                .number("RE-2026-0731")
                .issued(LocalDate.of(2026, 4, 14))
                .currency(CurrencyCode.EUR)
                .seller(Party.named("Beispiel Handels GmbH").vatId("DE123456789")
                        .address("Werkstrasse 8", "10117", "Beispielstadt", Country.DE))
                .buyer(Party.named("Jana Muster")
                        .address("Lindenweg 4", "20095", "Musterstadt", Country.DE))
                .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
                        PaymentTerms.days(14, "Payable within 14 days without deduction."));
    }
}
