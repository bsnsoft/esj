package de.bsnsoft.esj.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.invoice.code.AllowanceReason;
import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.Country;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.invoice.code.CustomCode;
import de.bsnsoft.esj.invoice.code.ElectronicAddressScheme;
import de.bsnsoft.esj.invoice.code.InvoiceType;
import de.bsnsoft.esj.invoice.code.Unit;
import de.bsnsoft.esj.invoice.code.VatCategory;
import de.bsnsoft.esj.typed.build.Profile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The read side: the same words over a document that already exists. */
class InvoiceReadTest {

    @Test
    void anInvoiceComesBackThroughTheSameWords() {
        SemanticDocument document = Invoice.create(Profile.EN16931)
                .number("RE-2026-0211")
                .issued(LocalDate.of(2026, 5, 12))
                .currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address("Musterweg 12", "10117", "Beispielstadt", Country.DE)
                        .contact("Sales", "+49 30 000000", "sales@example.invalid")
                        .electronicAddress("invoice@example.invalid",
                                ElectronicAddressScheme.ELECTRONIC_MAIL))
                .buyer(Party.named("Muster AG")
                        .address("Beispielallee 3", "20095", "Musterstadt", Country.DE))
                .buyerReference("04011000-12345-34")
                .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000", "BYLADEM1001"),
                        PaymentTerms.days(30, "Payable within 30 days without deduction."))
                .note("Delivered ex works.")
                .line(Line.of("Sensor module SM-100").quantity(100, Unit.PIECE).unitPrice("12")
                        .vat(Vat.standard(19))
                        .allowance(Allowance.percent(10, AllowanceReason.DISCOUNT)
                                .reason("Quantity discount")))
                .build();

        Invoice invoice = Invoice.read(document);

        assertEquals("RE-2026-0211", invoice.number());
        assertEquals(LocalDate.of(2026, 5, 12), invoice.issued());
        assertEquals(InvoiceType.COMMERCIAL_INVOICE, invoice.type());
        assertEquals(CurrencyCode.EUR, invoice.currency());
        assertEquals(LocalDate.of(2026, 6, 11), invoice.dueDate().orElseThrow());
        assertEquals("04011000-12345-34", invoice.buyerReference().orElseThrow());
        assertEquals(List.of("Delivered ex works."), invoice.notes());

        Party seller = invoice.seller();
        assertEquals("Example GmbH", seller.name());
        assertEquals("DE123456789", seller.vatId().orElseThrow());
        assertEquals(Country.DE, seller.address().orElseThrow().country());
        assertEquals("Beispielstadt", seller.address().orElseThrow().city().orElseThrow());
        assertEquals("sales@example.invalid", seller.contact().orElseThrow().email().orElseThrow());
        assertEquals(ElectronicAddressScheme.ELECTRONIC_MAIL,
                seller.electronicAddress().orElseThrow().scheme());
        assertEquals("Muster AG", invoice.buyer().name());

        PaymentMeans payment = invoice.payment().orElseThrow();
        assertEquals("58", payment.code().code());
        assertEquals("DE89370400440532013000", payment.account().orElseThrow());
        assertEquals("BYLADEM1001", payment.serviceProvider().orElseThrow());
        assertEquals("Payable within 30 days without deduction.",
                invoice.paymentTerms().orElseThrow().text().orElseThrow());

        Line line = invoice.lines().get(0);
        assertEquals("Sensor module SM-100", line.name());
        assertEquals(Unit.PIECE, line.unit().orElseThrow());
        assertEquals(new BigDecimal("100"), line.quantity().orElseThrow());
        assertEquals(new BigDecimal("12"), line.unitPrice().orElseThrow());
        assertEquals(VatCategory.STANDARD, line.vat().orElseThrow().category());
        assertEquals(new BigDecimal("19"), line.vat().orElseThrow().rate().orElseThrow());
        assertEquals(new BigDecimal("1080"), line.netAmount().orElseThrow());
        assertEquals(AllowanceReason.DISCOUNT,
                line.allowances().get(0).reasonCode().orElseThrow());

        InvoiceTotals totals = invoice.totals().orElseThrow();
        assertEquals(new BigDecimal("1080"), totals.lineNetTotal());
        assertEquals(0, new BigDecimal("1285.20").compareTo(totals.amountDue()));
        assertEquals(VatCategory.STANDARD, invoice.vatBreakdown().get(0).vat().category());
    }

    @Test
    void aCodeTheSnapshotDoesNotCarryIsWrittenAndComesBackAsACustomCode() {
        Coded unit = Unit.custom("QQQ");
        InvoiceResult result = Invoice.create(Profile.EN16931)
                .number("RE-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .line(Line.of("Sensor").quantity(2, unit).unitPrice("12").vat(Vat.standard(19)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."))
                .buildReport();

        // The enum is a spelling aid and never a verdict: the code is written, and the
        // rule pack is what says it is not on the snapshot it decides against.
        assertTrue(result.report().lines().stream().anyMatch(line -> line.contains("BR-CL-23")),
                String.join("; ", result.report().lines()));

        Line line = Invoice.read(result.document()).lines().get(0);
        Coded read = line.unit().orElseThrow();
        assertInstanceOf(CustomCode.class, read);
        assertEquals("QQQ", read.code());
        assertTrue(read.publishedName().isEmpty());
    }

    @Test
    void aDocumentWithoutTotalsHasNone() {
        SemanticDocument document = Invoice.draft(Profile.EN16931)
                .number("RE-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .document();

        assertTrue(Invoice.read(document).totals().isEmpty());
        assertEquals("Example GmbH", Invoice.read(document).seller().name());
    }
}
