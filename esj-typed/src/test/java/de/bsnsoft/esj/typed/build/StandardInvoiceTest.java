package de.bsnsoft.esj.typed.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.typed.InvoiceLineEditor;
import de.bsnsoft.esj.typed.SellerEditor;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The example {@code examples/standard-invoice.esj.json}, written once through the step
 * chain and once through a draft, both times without its derived amounts, and compared
 * against the file after {@code derive()}.
 *
 * <p>Two things are asserted at once: that the builder writes the same document the
 * repository publishes, down to the bytes, and that the amounts the file states are the
 * ones the derivation policy computes from the prices and quantities above them.
 */
class StandardInvoiceTest {

    private static final String NOTE =
            "Delivery and performance according to contract RV-2025-118.\n"
                    + "Please quote the invoice number with the payment.";

    private static final String LEGAL_INFORMATION =
            "Managing director: Alex Beispiel, register court Beispielstadt HRB 12345";

    @Test
    void theExampleBuiltThroughTheStepChainEqualsItsGoldenBytes() {
        SemanticDocument document = InvoiceBuilder.create(Profile.EN16931)
                .invoiceNumber("RE-2026-0042")
                .issueDate(LocalDate.of(2026, 2, 3))
                .typeCode("380")
                .currencyCode("EUR")
                .seller(seller -> seller
                        .name("Example GmbH")
                        .postalAddress(address -> address
                                .countryCode("DE")
                                .addressLine1("Musterweg 12")
                                .city("Beispielstadt")
                                .postCode("10117"))
                        .tradingName("Example Systems")
                        .identifier("4399901000018", "0088")
                        .legalRegistrationIdentifier("HRB 12345")
                        .vatIdentifier("DE123456789")
                        .additionalLegalInformation(LEGAL_INFORMATION)
                        .electronicAddress("invoices@example.invalid", "EM")
                        .contact(contact -> contact
                                .name("Accounts receivable")
                                .telephone("+49 30 1234567")
                                .email("billing@example.invalid")))
                .buyer(buyer -> buyer
                        .name("Muster AG")
                        .postalAddress(address -> address
                                .countryCode("DE")
                                .addressLine1("Beispielallee 3")
                                .city("Musterstadt")
                                .postCode("20095"))
                        .identifier("4399902000024", "0088")
                        .vatIdentifier("DE987654321")
                        .electronicAddress("rechnung@muster.invalid", "EM")
                        .contact(contact -> contact
                                .name("Purchasing")
                                .telephone("+49 40 7654321")
                                .email("purchasing@muster.invalid")))
                .invoiceLine(line -> line
                        .identifier("1")
                        .quantity(new BigDecimal("10"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("25")))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item
                                .name("Sensor module SM-100")
                                .description("Sensor module, housing grey")
                                .sellerIdentifier("SM-100"))
                        .purchaseOrderLineReference("10"))
                .invoiceLine(line -> line
                        .identifier("2")
                        .quantity(new BigDecimal("3"), "DAY")
                        .price(price -> price.netPrice(new BigDecimal("480")))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("On-site installation"))
                        .purchaseOrderLineReference("20"))
                .invoiceLine(line -> line
                        .identifier("3")
                        .quantity(new BigDecimal("8"), "HUR")
                        .price(price -> price.netPrice(new BigDecimal("95")))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Training"))
                        .note("Two sessions of four hours."))
                .paymentDueDate(LocalDate.of(2026, 3, 5))
                .buyerReference("KOST-4711")
                .contractReference("RV-2025-118")
                .purchaseOrderReference("BE-2026-0091")
                .buyerAccountingReference("Cost centre 4711")
                .paymentTerms("Payable within 30 days without deduction.")
                .note(note -> note.note(NOTE))
                .delivery(delivery -> delivery.actualDeliveryDate(LocalDate.of(2026, 1, 30)))
                .paymentInstructions(payment -> payment
                        .paymentMeansTypeCode("58")
                        .remittanceInformation("RE-2026-0042")
                        .creditTransfer(transfer -> transfer
                                .accountIdentifier("DE89370400440532013000")
                                .accountName("Example GmbH")
                                .serviceProviderIdentifier("COBADEFFXXX")))
                .build();

        assertEquals(Golden.example("standard-invoice"), Golden.pretty(document));
    }

    @Test
    void theSameExampleWrittenAsADraftEqualsTheSameBytes() {
        InvoiceDraft draft = InvoiceBuilder.draft(Profile.EN16931);
        draft.edit(StandardInvoiceTest::writeAsDraft);

        assertEquals(Golden.example("standard-invoice"), Golden.pretty(draft.build()));
    }

    @Test
    void theDerivationReportNamesTheAmountsItWrote() {
        InvoiceDraft draft = InvoiceBuilder.draft(Profile.EN16931).edit(
                StandardInvoiceTest::writeAsDraft);
        draft.build();

        assertTrue(draft.derivationReport().isPresent(), "build() derives");
        assertEquals(new BigDecimal("250.00"),
                draft.derivationReport().orElseThrow().at("/BG-25/0/BT-131").orElseThrow().value());
        assertEquals(new BigDecimal("2915.50"),
                draft.derivationReport().orElseThrow().at("/BG-22/BT-115").orElseThrow().value());
    }

    /** The same invoice, written in the order a mapping from another model would have. */
    private static void writeAsDraft(InvoiceEditor invoice) {
        invoice.currencyCode("EUR")
                .typeCode("380")
                .invoiceNumber("RE-2026-0042")
                .issueDate(LocalDate.of(2026, 2, 3))
                .paymentDueDate(LocalDate.of(2026, 3, 5))
                .buyerReference("KOST-4711")
                .contractReference("RV-2025-118")
                .purchaseOrderReference("BE-2026-0091")
                .buyerAccountingReference("Cost centre 4711")
                .paymentTerms("Payable within 30 days without deduction.")
                .note(note -> note.note(NOTE))
                .delivery(delivery -> delivery.actualDeliveryDate(LocalDate.of(2026, 1, 30)))
                .paymentInstructions(payment -> payment
                        .paymentMeansTypeCode("58")
                        .remittanceInformation("RE-2026-0042")
                        .creditTransfer(transfer -> transfer
                                .accountIdentifier("DE89370400440532013000")
                                .accountName("Example GmbH")
                                .serviceProviderIdentifier("COBADEFFXXX")));

        SellerEditor seller = invoice.seller();
        seller.name("Example GmbH")
                .tradingName("Example Systems")
                .legalRegistrationIdentifier("HRB 12345")
                .vatIdentifier("DE123456789")
                .additionalLegalInformation(LEGAL_INFORMATION)
                .electronicAddress("invoices@example.invalid", "EM")
                .postalAddress(address -> address
                        .addressLine1("Musterweg 12")
                        .city("Beispielstadt")
                        .postCode("10117")
                        .countryCode("DE"))
                .contact(contact -> contact
                        .name("Accounts receivable")
                        .telephone("+49 30 1234567")
                        .email("billing@example.invalid"));
        seller.identifiers().add("4399901000018", "0088");

        invoice.buyer(buyer -> buyer
                .name("Muster AG")
                .identifier("4399902000024", "0088")
                .vatIdentifier("DE987654321")
                .electronicAddress("rechnung@muster.invalid", "EM")
                .postalAddress(address -> address
                        .addressLine1("Beispielallee 3")
                        .city("Musterstadt")
                        .postCode("20095")
                        .countryCode("DE"))
                .contact(contact -> contact
                        .name("Purchasing")
                        .telephone("+49 40 7654321")
                        .email("purchasing@muster.invalid")));

        line(invoice, "1", "10", "H87", "25")
                .purchaseOrderLineReference("10")
                .item(item -> item
                        .name("Sensor module SM-100")
                        .description("Sensor module, housing grey")
                        .sellerIdentifier("SM-100"));
        line(invoice, "2", "3", "DAY", "480")
                .purchaseOrderLineReference("20")
                .item(item -> item.name("On-site installation"));
        line(invoice, "3", "8", "HUR", "95")
                .note("Two sessions of four hours.")
                .item(item -> item.name("Training"));
    }

    private static InvoiceLineEditor line(InvoiceEditor invoice,
                                          String identifier,
                                          String quantity,
                                          String unit,
                                          String price) {
        return invoice.invoiceLines().add()
                .identifier(identifier)
                .quantity(new BigDecimal(quantity), unit)
                .price(step -> step.netPrice(new BigDecimal(price)))
                .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")));
    }
}
