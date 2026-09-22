package de.bsnsoft.esj.typed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.validate.ValidationResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Checks that the generated editor writes what the document is supposed to carry: the
 * example {@code examples/standard-invoice.esj.json} is built once more through names
 * alone and has to come out as the same canonical bytes.
 */
class EditorTest {

    private static final String LINE_NOTE =
            "Delivery and performance according to contract RV-2025-118.\n"
                    + "Please quote the invoice number with the payment.";

    /**
     * Builds {@code examples/standard-invoice.esj.json} through the editor, without a
     * single semantic path.
     *
     * @return the editor, so that a test can go on writing into it
     */
    private static InvoiceEditor standardInvoice() {
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
                .paymentTerms("Payable within 30 days without deduction.");

        invoice.notes().add(note -> note.note(LINE_NOTE));
        invoice.processControl().specificationIdentifier("urn:cen.eu:en16931:2017");

        invoice.seller()
                .name("Example GmbH")
                .tradingName("Example Systems")
                .legalRegistrationIdentifier("HRB 12345")
                .vatIdentifier("DE123456789")
                .additionalLegalInformation(
                        "Managing director: Alex Beispiel, register court Beispielstadt HRB 12345")
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
        invoice.seller().identifiers().add("4399901000018", "0088");

        invoice.buyer()
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
                        .email("purchasing@muster.invalid"));

        invoice.delivery(delivery -> delivery.actualDeliveryDate(LocalDate.of(2026, 1, 30)));

        invoice.paymentInstructions(payment -> {
            payment.paymentMeansTypeCode("58").remittanceInformation("RE-2026-0042");
            payment.creditTransfers().add(transfer -> transfer
                    .accountIdentifier("DE89370400440532013000")
                    .accountName("Example GmbH")
                    .serviceProviderIdentifier("COBADEFFXXX"));
        });

        invoice.documentTotals()
                .sumOfLineNetAmounts(new BigDecimal("2450"))
                .totalWithoutVat(new BigDecimal("2450"))
                .totalVatAmount(new BigDecimal("465.5"))
                .totalWithVat(new BigDecimal("2915.5"))
                .amountDueForPayment(new BigDecimal("2915.5"));

        invoice.vatBreakdowns().add(vat -> vat
                .taxableAmount(new BigDecimal("2450"))
                .taxAmount(new BigDecimal("465.5"))
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("19")));

        invoice.invoiceLines().add(line -> line
                .identifier("1")
                .quantity(new BigDecimal("10"))
                .quantityUnitCode("H87")
                .netAmount(new BigDecimal("250"))
                .purchaseOrderLineReference("10")
                .price(price -> price.netPrice(new BigDecimal("25")))
                .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                .item(item -> item
                        .name("Sensor module SM-100")
                        .description("Sensor module, housing grey")
                        .sellerIdentifier("SM-100")));

        invoice.invoiceLines().add(line -> line
                .identifier("2")
                .quantity(new BigDecimal("3"))
                .quantityUnitCode("DAY")
                .netAmount(new BigDecimal("1440"))
                .purchaseOrderLineReference("20")
                .price(price -> price.netPrice(new BigDecimal("480")))
                .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                .item(item -> item.name("On-site installation")));

        invoice.invoiceLines().add(line -> line
                .identifier("3")
                .note("Two sessions of four hours.")
                .quantity(new BigDecimal("8"))
                .quantityUnitCode("HUR")
                .netAmount(new BigDecimal("760"))
                .price(price -> price.netPrice(new BigDecimal("95")))
                .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                .item(item -> item.name("Training")));

        return invoice;
    }

    @Test
    void anExampleBuiltThroughNamesAloneIsTheExampleTheRepositoryHolds() {
        SemanticDocument document = standardInvoice().document();

        assertArrayEquals(Examples.canonical("standard-invoice"),
                EsjWriter.canonical().toBytes(document));
        assertEquals(Examples.document("standard-invoice").values(), document.values());
    }

    @Test
    void whatTheEditorWritesPassesTheValidatorWithoutAFinding() {
        ValidationResult result =
                StructuralValidator.validate(standardInvoice().document(), Registry.en16931());

        assertEquals(List.of(), result.findings());
        assertEquals(Set.of(ValidationLayer.L2, ValidationLayer.L3), result.evaluated());
    }

    @Test
    void anEmptyEditorWritesAnEmptyDocument() {
        SemanticDocument document = En16931.newInvoice().document();

        assertTrue(document.values().isEmpty());
        assertEquals("EN16931-1:2017+A1:2019/AC:2020", document.semanticModel());
    }

    @Test
    void anOptionalGroupThatIsNeverWrittenIntoLeavesNothingBehind() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.payee();
        invoice.seller().contact();
        invoice.seller().postalAddress();
        invoice.payee(payee -> { });

        assertTrue(invoice.document().values().isEmpty());
        assertTrue(En16931.view(invoice.document()).payee().isEmpty());
    }

    @Test
    void aGroupComesIntoBeingWhenAValueIsWrittenIntoIt() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.payee(payee -> payee.name("Factoring GmbH"));

        assertEquals(List.of(SemanticPath.of("/BG-10/BT-59")),
                List.copyOf(invoice.document().values().keySet()));
        assertEquals("Factoring GmbH",
                En16931.view(invoice.document()).payee().orElseThrow().name());
    }

    @Test
    void aSetterWithNullOrAnEmptyStringRemovesTheValue() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.buyerReference("KOST-4711").seller().name("Example GmbH");
        invoice.seller().legalRegistrationIdentifier("HRB 12345");

        invoice.buyerReference(null);
        invoice.seller().name("");
        invoice.seller().legalRegistrationIdentifier((String) null);

        assertTrue(invoice.document().values().isEmpty());
    }

    @Test
    void aSetterWritesOverTheValueThatIsAlreadyThere() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceNumber("RE-1").invoiceNumber("RE-2");

        assertEquals(SemanticValue.of("RE-2"),
                invoice.document().value(SemanticPath.of("/BT-1")).orElseThrow());
    }

    @Test
    void anIdentifierCarriesTheComponentsTheOverloadNames() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceLines().add(line -> line.item(item -> {
            item.standardIdentifier("9873242", "0088");
            item.classificationIdentifiers().add("65434568", "TST", "19.05.01");
        }));

        SemanticDocument document = invoice.document();
        assertEquals(SemanticValue.identifier("9873242", "0088"),
                document.value(SemanticPath.of("/BG-25/0/BG-31/BT-157")).orElseThrow());
        assertEquals(SemanticValue.identifier("65434568", "TST", "19.05.01"),
                document.value(SemanticPath.of("/BG-25/0/BG-31/BT-158/0")).orElseThrow());
    }

    @Test
    void aBinaryObjectIsWrittenAsCanonicalBase64WithBothComponents() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.additionalSupportingDocuments().add(document -> document
                .reference("ANL-1")
                .attachment("%PDF-1.4".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "application/pdf", "anlage.pdf"));

        SemanticValue value = invoice.document()
                .value(SemanticPath.of("/BG-24/0/BT-125")).orElseThrow();
        assertEquals("JVBERi0xLjQ=", value.canonicalContent());
        assertEquals("application/pdf", value.mimeCode());
        assertEquals("anlage.pdf", value.filename());

        invoice.additionalSupportingDocuments().get(0).attachment(null, null, null);
        assertTrue(invoice.document().value(SemanticPath.of("/BG-24/0/BT-125")).isEmpty());
    }

    @Test
    void addingToAGroupListAppendsAtTheEndAndCountsWhatExists() {
        InvoiceEditor invoice = En16931.newInvoice();
        EditorList<InvoiceLineEditor> lines = invoice.invoiceLines();

        assertEquals(0, lines.size());
        lines.add(line -> line.identifier("1"));
        assertEquals(1, lines.size());
        lines.add().identifier("2");
        assertEquals(2, lines.size());
        assertEquals(SemanticPath.group("/BG-25/1"), lines.get(1).path());
    }

    @Test
    void removingAGroupInstanceMovesEveryLaterInstanceDownOneIndex() {
        InvoiceEditor invoice = standardInvoice();
        invoice.invoiceLines().remove(0);

        List<InvoiceLine> lines = En16931.view(invoice.document()).invoiceLines();
        assertEquals(2, lines.size());
        assertEquals("On-site installation", lines.get(0).item().name());
        assertEquals("Training", lines.get(1).item().name());
        assertEquals(new BigDecimal("480"), lines.get(0).price().netPrice());
        assertFalse(invoice.document().values().containsKey(SemanticPath.of("/BG-25/2/BT-126")));
    }

    @Test
    void removingAnInnerInstanceLeavesTheOuterIndicesAlone() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceLines().add(line -> line.allowances()
                .add(allowance -> allowance.amount(new BigDecimal("1")))
                .add(allowance -> allowance.amount(new BigDecimal("2"))));
        invoice.invoiceLines().add(line -> line.allowances()
                .add(allowance -> allowance.amount(new BigDecimal("3"))));

        invoice.invoiceLines().get(0).allowances().remove(0);

        assertEquals(List.of("/BG-25/0/BG-27/0/BT-136", "/BG-25/1/BG-27/0/BT-136"),
                invoice.document().values().keySet().stream().map(SemanticPath::toString).toList());
        assertEquals(new BigDecimal("2"),
                En16931.view(invoice.document()).invoiceLines().get(0).allowances().get(0).amount());
    }

    @Test
    void clearingAGroupListRemovesEveryInstance() {
        InvoiceEditor invoice = standardInvoice();
        invoice.invoiceLines().clear();

        assertEquals(0, invoice.invoiceLines().size());
        assertTrue(invoice.document().values().keySet().stream()
                .noneMatch(path -> path.toString().startsWith("/BG-25")));
    }

    @Test
    void aValueListAppendsReadsRemovesAndEmpties() {
        InvoiceEditor invoice = En16931.newInvoice();
        IdentifierList identifiers = invoice.seller().identifiers();
        identifiers.add("first").add("second", "0088").add(Identifier.of("third"));

        assertEquals(3, identifiers.size());
        assertEquals(Identifier.of("second", "0088"), identifiers.get(1));

        identifiers.remove(0);
        assertEquals(2, identifiers.size());
        assertEquals(Identifier.of("second", "0088"), identifiers.get(0));
        assertEquals(Identifier.of("third"), identifiers.get(1));

        identifiers.clear();
        assertEquals(0, identifiers.size());
        assertTrue(invoice.document().values().isEmpty());
    }

    @Test
    void aListRefusesAnIndexItHasNoOccurrenceAt() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceLines().add(line -> line.identifier("1"));

        assertThrows(IndexOutOfBoundsException.class, () -> invoice.invoiceLines().get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> invoice.invoiceLines().remove(-1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> invoice.seller().identifiers().get(0));
    }

    @Test
    void anEditorOverAnExistingDocumentChangesACopyOfIt() {
        SemanticDocument original = Examples.document("standard-invoice");

        InvoiceEditor invoice = En16931.edit(original);
        invoice.buyerReference("KOST-4712");
        invoice.seller().tradingName(null);
        SemanticDocument changed = invoice.document();

        assertEquals(original.values().size() - 1, changed.values().size());
        assertEquals("KOST-4712", En16931.view(changed).buyerReference().orElseThrow());
        assertTrue(En16931.view(changed).seller().tradingName().isEmpty());
        assertEquals("KOST-4711", En16931.view(original).buyerReference().orElseThrow());
        assertEquals("Example Systems", En16931.view(original).seller().tradingName().orElseThrow());
    }

    @Test
    void anEditorThatChangesNothingReturnsTheDocumentItWasOpenedOver() {
        for (String name : Examples.NAMES) {
            SemanticDocument original = Examples.document(name);
            assertEquals(original, En16931.edit(original).document(), name);
        }
    }

    @Test
    void everyEditorNamesItsPathAndShareTheOneBuilder() {
        InvoiceEditor invoice = En16931.newInvoice();
        SellerPostalAddressEditor address = invoice.seller().postalAddress();

        assertEquals(SemanticPath.root(), invoice.path());
        assertEquals(SemanticPath.group("/BG-4/BG-5"), address.path());
        assertEquals("SellerPostalAddressEditor[/BG-4/BG-5]", address.toString());

        address.builder().put("/BT-1", "RE-2026-0001");
        assertEquals("RE-2026-0001", En16931.view(invoice.document()).invoiceNumber().value());
    }
}
