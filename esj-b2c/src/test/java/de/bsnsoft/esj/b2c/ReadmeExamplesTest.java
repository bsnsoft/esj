package de.bsnsoft.esj.b2c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.invoice.Line;
import de.bsnsoft.esj.invoice.Party;
import de.bsnsoft.esj.invoice.PaymentMeans;
import de.bsnsoft.esj.invoice.PaymentTerms;
import de.bsnsoft.esj.invoice.Vat;
import de.bsnsoft.esj.invoice.code.Country;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.invoice.code.Unit;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.typed.build.Profile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every snippet of {@code docs/b2c.md}, run, and one case beyond them: the consumer invoice
 * the feature list of {@code README.md} promises, which no page prints as a snippet, so that
 * the promise that the core terms stay the net ones of the standard is measured somewhere.
 */
class ReadmeExamplesTest {

    @Test
    void writingTheFiguresAndDerivingTheNetInvoice() {
        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "1", "Shower fitting SF-20");

        // docs/b2c.md: Writing the figures
        Gross gross = Gross.on(invoice);
        gross.line(0).displayedGrossUnitPrice("99.99");
        gross.displayedGrossTotal("99.99");

        B2cInvoice shown = B2c.of(invoice.document());
        assertEquals(new BigDecimal("99.99"), shown.displayedGrossTotal().orElseThrow());
        assertEquals(new BigDecimal("99.99"),
                shown.lines().get(0).displayedGrossUnitPrice().orElseThrow());

        // docs/b2c.md: Deriving the net invoice
        AuthoringReport report = gross.derive(GrossAuthoring.GROSS_UNIT_AUTHORING);

        SemanticDocument document = invoice.document();
        // The page states these five figures.
        assertEquals("84.02521", Invoices.at(document, "/BG-25/0/BG-29/BT-146"));
        assertEquals("84.03", Invoices.at(document, "/BG-25/0/BT-131"));
        assertEquals("15.97", Invoices.at(document, "/BG-22/BT-110"));
        assertEquals("100", Invoices.at(document, "/BG-22/BT-112"));
        assertEquals("-0.01", Invoices.at(document, "/BG-22/BT-114"));
        assertEquals(new BigDecimal("-0.01"), report.roundingAmount().orElseThrow());
        Invoices.isAConformantInvoice(document);

        // docs/b2c.md: What a policy guarantees — the call the section names, and its shape
        List<RuleFinding> findings =
                B2cConsistency.check(document, GrossAuthoring.GROSS_UNIT_AUTHORING);

        assertEquals(List.of(), findings);
    }

    @Test
    void theOptionsSnippetSaysThatAPriceBaseQuantityIsMeant() {
        // docs/b2c.md: Options
        GrossAuthoring policy = GrossAuthoring.GROSS_UNIT_AUTHORING.with(
                AuthoringOptions.standard().withBaseQuantity(true));

        InvoiceEditor invoice = Invoices.consumer();
        Invoices.line(invoice, "3", "Sealing tape 12 m");
        invoice.invoiceLines().get(0).price(price -> price.baseQuantity(BigDecimal.TEN, "H87"));
        Gross.on(invoice).line(0).displayedGrossUnitPrice("4.50");
        Gross.on(invoice).derive(policy);

        assertEquals("1.13", Invoices.at(invoice.document(), "/BG-25/0/BT-131"));
        assertTrue(policy.options().baseQuantity());
    }

    @Test
    void theConsumerInvoiceKeepsTheNetTermsOfTheStandard() {
        GrossInvoice grossDraft = GrossInvoice.draft(Profile.EN16931)
                .edit(draft -> draft
                        .number("RE-2026-0731")
                        .issued(LocalDate.of(2026, 4, 14))
                        .currency(CurrencyCode.EUR)
                        .seller(Party.named("Beispiel Handels GmbH").vatId("DE123456789")
                                .address("Werkstrasse 8", "10117", "Beispielstadt", Country.DE))
                        .buyer(Party.named("Jana Muster")
                                .address("Lindenweg 4", "20095", "Musterstadt", Country.DE))
                        .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
                                PaymentTerms.days(14,
                                        "Payable within 14 days without deduction.")));
        Line fitting = Line.of("Shower fitting SF-20")
                .quantity(1, Unit.PIECE).vat(Vat.standard(19));

        GrossResult consumer = grossDraft
                .line(GrossItem.perUnit(fitting, "99.99"))
                .build(GrossAuthoring.GROSS_UNIT_AUTHORING);

        assertTrue(consumer.ok(), () -> String.join("\n", consumer.report().lines()));
        // The core terms the README promises stay the net ones of the standard.
        assertEquals("84.03", Invoices.at(consumer.document(), "/BG-25/0/BT-131"));
        assertEquals("100", Invoices.at(consumer.document(), "/BG-22/BT-112"));
        assertEquals(new BigDecimal("99.99"),
                B2c.of(consumer.document()).lines().get(0).displayedGrossUnitPrice().orElseThrow());
    }

    @Test
    void theDomainApiSnippet() {
        // docs/b2c.md: The domain API
        GrossResult result = GrossInvoice.draft(Profile.EN16931)
                .edit(draft -> draft
                        .number("RE-2026-0731")
                        .issued(LocalDate.of(2026, 4, 14))
                        .currency(CurrencyCode.EUR)
                        .seller(Party.named("Beispiel Handels GmbH").vatId("DE123456789")
                                .address("Werkstrasse 8", "10117", "Beispielstadt", Country.DE))
                        .buyer(Party.named("Jana Muster")
                                .address("Lindenweg 4", "20095", "Musterstadt", Country.DE))
                        .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
                                PaymentTerms.days(14,
                                        "Payable within 14 days without deduction.")))
                .line(GrossItem.perUnit(Line.of("Shower fitting SF-20")
                        .quantity(1, Unit.PIECE).vat(Vat.standard(19)), "99.99"))
                .displayedGrossTotal("99.99")
                .build(GrossAuthoring.GROSS_UNIT_AUTHORING);

        assertTrue(result.ok(), () -> String.join("\n", result.report().lines()));
        assertEquals("99.99", Invoices.at(result.document(), "/BG-22/BT-115"));
    }
}
