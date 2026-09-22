package de.bsnsoft.esj.b2c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.typed.En16931;
import de.bsnsoft.esj.typed.Invoice;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Checks the overlay against the example {@code examples/b2c-gross.esj.json}: it is read
 * through the overlay, and it is written once more through the typed editor of the core
 * model and the overlay editor together, which has to produce the same canonical bytes.
 *
 * <p>The other half of what is checked here is what the overlay does <em>not</em> do: on a
 * document without the extension it is empty, opening it writes nothing, and it appends no
 * invoice line.
 */
class OverlayTest {

    private static final Set<ValidationLayer> STRUCTURE =
            EnumSet.of(ValidationLayer.L2, ValidationLayer.L3);

    @Test
    void theExampleIsReadThroughTheOverlay() {
        B2cInvoice b2c = B2c.of(Examples.document("b2c-gross"));

        assertEquals(Optional.of(new BigDecimal("153.47")), b2c.displayedGrossTotal());
        assertEquals(3, b2c.lines().size());
        assertEquals(List.of("99.99", "19.99", "4.50"), grossUnitPrices(b2c));
        assertEquals(Optional.of(new BigDecimal("39.98")),
                b2c.lines().get(1).displayedGrossLineTotal());
        assertEquals(Optional.of(new BigDecimal("2.16")),
                b2c.lines().get(2).displayedLineVatAmount());
    }

    @Test
    void theOverlayAndTheTypedViewOfTheCoreModelAddressTheSameInvoiceLine() {
        SemanticDocument document = Examples.document("b2c-gross");
        Invoice invoice = En16931.view(document);
        B2cInvoice b2c = B2c.of(invoice);

        assertEquals(invoice.invoiceLines().size(), b2c.lines().size(),
                "the overlay shows one line per line of the core model");
        for (int index = 0; index < b2c.lines().size(); index++) {
            assertEquals(invoice.invoiceLines().get(index).path(), b2c.lines().get(index).path(),
                    "line " + index);
        }
        assertEquals(SemanticPath.root(), b2c.path());
        assertEquals(SemanticPath.group("/BG-25/0"), b2c.lines().get(0).path());
        assertSame(document, b2c.document());
    }

    @Test
    void theExampleIsWrittenThroughTheCoreEditorAndTheOverlayTogether() {
        InvoiceEditor invoice = consumerInvoice();

        B2c.edit(invoice)
                .displayedGrossTotal(new BigDecimal("153.47"))
                .line(0, line -> line
                        .displayedGrossUnitPrice(new BigDecimal("99.99"))
                        .displayedGrossLineTotal(new BigDecimal("99.99"))
                        .displayedLineVatAmount(new BigDecimal("15.96")))
                .line(1, line -> line
                        .displayedGrossUnitPrice(new BigDecimal("19.99"))
                        .displayedGrossLineTotal(new BigDecimal("39.98"))
                        .displayedLineVatAmount(new BigDecimal("6.38")))
                .line(2, line -> line
                        .displayedGrossUnitPrice(new BigDecimal("4.50"))
                        .displayedGrossLineTotal(new BigDecimal("13.50"))
                        .displayedLineVatAmount(new BigDecimal("2.16")));

        SemanticDocument document = invoice.document();
        assertArrayEquals(Examples.canonical("b2c-gross"),
                EsjWriter.canonical().toBytes(document));
        assertEquals(Examples.document("b2c-gross").values(), document.values());
    }

    @Test
    void whatTheTwoEditorsWroteTogetherPassesTheValidatorWithTheRegistryTheOverlayCarries() {
        SemanticDocument document = Examples.document("b2c-gross");

        assertEquals(List.of(), StructuralValidator
                .validate(document, B2c.of(document).registry(), STRUCTURE).findings());
        assertSame(B2c.registry(), B2c.edit(SemanticDocument.builder()).registry());

        List<Finding> withoutIt = StructuralValidator
                .validate(document, Registry.en16931(), STRUCTURE).findings();
        assertEquals(10, withoutIt.size(),
                "the four terms of the extension, ten values across the document");
        for (Finding finding : withoutIt) {
            assertEquals(FindingCode.ESJ_L2_NOT_CHECKED, finding.code());
        }
    }

    @Test
    void anOverlayOverADocumentWithoutTheExtensionIsEmptyAndWritesNothingUntilASetterIsCalled() {
        SemanticDocument document = Examples.document("standard-invoice");
        B2cInvoice b2c = B2c.of(document);

        assertEquals(Optional.empty(), b2c.displayedGrossTotal());
        assertEquals(3, b2c.lines().size(), "the lines of the core model are there");
        for (B2cInvoiceLine line : b2c.lines()) {
            assertEquals(Optional.empty(), line.displayedGrossUnitPrice());
            assertEquals(Optional.empty(), line.displayedGrossLineTotal());
            assertEquals(Optional.empty(), line.displayedLineVatAmount());
        }

        InvoiceEditor invoice = En16931.edit(document);
        B2cInvoiceEditor editor = B2c.edit(invoice);
        editor.lines();
        editor.registry();
        assertEquals(document.values(), invoice.document().values(),
                "an overlay that is only opened and read leaves the document as it was");

        editor.displayedGrossTotal(new BigDecimal("2915.50"));
        assertEquals(Optional.of(new BigDecimal("2915.5")),
                B2c.of(invoice.document()).displayedGrossTotal(),
                "the setter writes the canonical spelling of the decimal, which has no"
                        + " trailing zero (specification, section 6.4)");
        assertEquals(document.values().size() + 1, invoice.document().values().size(),
                "one setter writes one value and nothing else");
    }

    @Test
    void settingAValueBackToNothingRemovesIt() {
        SemanticDocument document = Examples.document("b2c-gross");
        InvoiceEditor invoice = En16931.edit(document);

        B2c.edit(invoice)
                .displayedGrossTotal(null)
                .line(0, line -> line.displayedGrossUnitPrice(null));

        B2cInvoice b2c = B2c.of(invoice.document());
        assertEquals(Optional.empty(), b2c.displayedGrossTotal());
        assertEquals(Optional.empty(), b2c.lines().get(0).displayedGrossUnitPrice());
        assertEquals(Optional.of(new BigDecimal("99.99")),
                b2c.lines().get(0).displayedGrossLineTotal(),
                "the value beside it stays");
        assertEquals(document.values().size() - 2, invoice.document().values().size());
    }

    @Test
    void theOverlayAppendsNoInvoiceLineAndRefusesAPositionTheDocumentDoesNotHave() {
        B2cInvoiceEditor empty = B2c.edit(SemanticDocument.builder());

        assertEquals(List.of(), empty.lines());
        assertThrows(IndexOutOfBoundsException.class,
                () -> empty.line(0, line -> line.displayedGrossLineTotal(BigDecimal.ONE)));
        assertTrue(empty.document().values().isEmpty(),
                "a refused write leaves the document empty");
    }

    private static List<String> grossUnitPrices(B2cInvoice b2c) {
        return b2c.lines().stream()
                .map(line -> line.displayedGrossUnitPrice().orElseThrow())
                .map(price -> price.setScale(2).toPlainString())
                .toList();
    }

    /**
     * Builds the core half of {@code examples/b2c-gross.esj.json} through the typed editor:
     * a three-line consumer invoice whose business terms are the net terms EN 16931-1
     * defines, without one figure of the extension.
     *
     * @return the editor, so that the overlay can go on writing into the same builder
     */
    private static InvoiceEditor consumerInvoice() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceNumber("RE-2026-0731")
                .issueDate(LocalDate.of(2026, 4, 14))
                .typeCode("380")
                .currencyCode("EUR")
                .paymentDueDate(LocalDate.of(2026, 4, 28))
                .paymentTerms("Payable within 14 days without deduction.");
        invoice.processControl().specificationIdentifier("urn:cen.eu:en16931:2017");

        invoice.seller()
                .name("Beispiel Handels GmbH")
                .vatIdentifier("DE123456789")
                .electronicAddress("shop@beispiel.invalid", "EM")
                .postalAddress(address -> address
                        .addressLine1("Werkstrasse 8")
                        .city("Beispielstadt")
                        .postCode("10117")
                        .countryCode("DE"));

        invoice.buyer()
                .name("Jana Muster")
                .postalAddress(address -> address
                        .addressLine1("Lindenweg 4")
                        .city("Musterstadt")
                        .postCode("20095")
                        .countryCode("DE"));

        invoice.paymentInstructions(payment -> {
            payment.paymentMeansTypeCode("58").remittanceInformation("RE-2026-0731");
            payment.creditTransfers().add(transfer -> transfer
                    .accountIdentifier("DE89370400440532013000")
                    .accountName("Beispiel Handels GmbH"));
        });

        invoice.documentTotals()
                .sumOfLineNetAmounts(new BigDecimal("128.97"))
                .totalWithoutVat(new BigDecimal("128.97"))
                .totalVatAmount(new BigDecimal("24.50"))
                .totalWithVat(new BigDecimal("153.47"))
                .amountDueForPayment(new BigDecimal("153.47"));

        invoice.vatBreakdowns().add(vat -> vat
                .taxableAmount(new BigDecimal("128.97"))
                .taxAmount(new BigDecimal("24.50"))
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("19")));

        line(invoice, "1", "1", "84.03", "84.02521", "Shower fitting SF-20");
        line(invoice, "2", "2", "33.60", "16.798319", "Shower hose 1.5 m");
        line(invoice, "3", "3", "11.34", "3.781513", "Sealing tape 12 m");
        return invoice;
    }

    private static void line(InvoiceEditor invoice,
                             String identifier,
                             String quantity,
                             String netAmount,
                             String netPrice,
                             String itemName) {
        invoice.invoiceLines().add(line -> line
                .identifier(identifier)
                .quantity(new BigDecimal(quantity), "H87")
                .netAmount(new BigDecimal(netAmount))
                .price(price -> price.netPrice(new BigDecimal(netPrice)))
                .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                .item(item -> item.name(itemName)));
    }
}
