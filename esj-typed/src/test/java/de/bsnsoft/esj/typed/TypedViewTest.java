package de.bsnsoft.esj.typed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Checks that the generated view reads what the document carries, and nothing else. */
class TypedViewTest {

    private static Invoice view(String example) {
        return En16931.view(Examples.document(example));
    }

    @Test
    void aBusinessTermAtTheRootIsReadByItsSlug() {
        assertEquals(Identifier.of("RE-2026-0107"), view("multiple-lines").invoiceNumber());
    }

    @Test
    void aTermInsideAGroupIsReadThroughTheGroup() {
        assertEquals("Example GmbH", view("multiple-lines").seller().name());
        assertEquals("DE", view("multiple-lines").seller().postalAddress().countryCode());
    }

    @Test
    void aRepeatableGroupIsReadAsAListInTheOrderOfItsIndices() {
        Invoice invoice = view("multiple-lines");
        List<InvoiceLine> lines = invoice.invoiceLines();
        assertEquals(10, lines.size());
        assertEquals(new BigDecimal("590"), lines.get(2).netAmount());
        assertEquals(Identifier.of("3"), lines.get(2).identifier());
        assertEquals(SemanticPath.group("/BG-25/2"), lines.get(2).path());
    }

    @Test
    void anAbsentOptionalTermIsAnEmptyOptional() {
        Invoice invoice = view("multiple-lines");
        assertTrue(invoice.vatAccountingCurrencyCode().isEmpty());
        assertTrue(invoice.seller().tradingName().isEmpty());
    }

    @Test
    void anAbsentOptionalGroupIsAnEmptyOptional() {
        Invoice invoice = view("minimal");
        assertTrue(invoice.seller().contact().isEmpty());
        assertTrue(invoice.delivery().isEmpty());
        assertTrue(invoice.paymentInstructions().isEmpty());
    }

    @Test
    void aGroupInstanceExistsExactlyWhenAValueLiesUnderIt() {
        Invoice invoice = view("standard-invoice");
        assertTrue(invoice.seller().contact().isPresent());
        assertEquals(SemanticPath.group("/BG-4/BG-6"), invoice.seller().contact().orElseThrow().path());
    }

    @Test
    void nestedRepeatingGroupsAreReadPerInstanceOfTheirParent() {
        Invoice invoice = view("allowances");
        assertEquals(2, invoice.documentLevelAllowances().size());
        List<InvoiceLine> lines = invoice.invoiceLines();
        List<Allowance> first = lines.get(0).allowances();
        assertEquals(1, first.size());
        assertEquals(new BigDecimal("120"), first.get(0).amount());
        assertEquals(new BigDecimal("10"), first.get(0).percentage().orElseThrow());
        assertEquals(SemanticPath.group("/BG-25/0/BG-27/0"), first.get(0).path());
        assertEquals(2, lines.size());
        assertEquals(1, lines.get(1).allowances().size());
        assertTrue(view("multiple-lines").invoiceLines().get(0).allowances().isEmpty());
    }

    @Test
    void aRepeatableBusinessTermIsReadAsAList() {
        Item item = view("multiple-lines").invoiceLines().get(0).item();
        List<Identifier> classifications = item.classificationIdentifiers();
        assertEquals(2, classifications.size());
        assertEquals("43201404", classifications.get(0).value());
        assertEquals(Optional.of("SRV"), classifications.get(0).scheme());
        assertEquals(Optional.of("26.0301"), classifications.get(0).schemeVersion());
        assertEquals(Optional.of("ZZZ"), classifications.get(1).scheme());
        assertEquals(Optional.empty(), classifications.get(1).schemeVersion());
        assertTrue(view("minimal").invoiceLines().get(0).item().classificationIdentifiers().isEmpty());
    }

    @Test
    void theThirdAddressLineOfTheCorrigendumHasItsOwnSlug() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-4/BG-5/BT-35", "Musterstrasse 1")
                .put("/BG-4/BG-5/BT-162", "Building C")
                .put("/BG-7/BG-8/BT-163", "Second floor")
                .build();
        Invoice invoice = En16931.view(document);
        assertEquals("Building C", invoice.seller().postalAddress().addressLine3().orElseThrow());
        assertEquals("Second floor", invoice.buyer().postalAddress().addressLine3().orElseThrow());
        assertTrue(invoice.seller().postalAddress().addressLine2().isEmpty());
    }

    @Test
    void aMandatoryGroupIsViewedEvenWhereTheDocumentCarriesNothingUnderIt() {
        Invoice invoice = En16931.view(SemanticDocument.builder()
                .put("/BT-1", "RE-2026-0001")
                .build());
        SellerPostalAddress address = invoice.seller().postalAddress();
        assertEquals(SemanticPath.group("/BG-4/BG-5"), address.path());
        assertTrue(address.city().isEmpty());
    }

    @Test
    void aMandatoryTermTheDocumentDoesNotCarryIsReportedWithItsPath() {
        Invoice invoice = En16931.view(SemanticDocument.builder()
                .put("/BT-1", "RE-2026-0001")
                .build());
        MissingValueException thrown =
                assertThrows(MissingValueException.class, () -> invoice.seller().name());
        assertEquals(SemanticPath.of("/BG-4/BT-27"), thrown.path());
    }

    @Test
    void aContentThatIsNoValueOfItsSemanticDataTypeIsReportedWithItsPath() {
        Invoice invoice = En16931.view(SemanticDocument.builder()
                .put("/BT-2", "the third of February")
                .build());
        ValueTypeException thrown = assertThrows(ValueTypeException.class, invoice::issueDate);
        assertEquals(SemanticPath.of("/BT-2"), thrown.path());
        assertEquals(SemanticType.DATE, thrown.expected());
        assertTrue(thrown.getMessage().contains("Date"));
    }

    @Test
    void anAttachmentWithoutItsMandatoryComponentsIsReportedWithItsPath() {
        Invoice invoice = En16931.view(SemanticDocument.builder()
                .put("/BG-24/0/BT-125", "QUJD")
                .build());
        ValueTypeException thrown = assertThrows(ValueTypeException.class,
                () -> invoice.additionalSupportingDocuments().get(0).attachment());
        assertEquals(SemanticPath.of("/BG-24/0/BT-125"), thrown.path());
        assertEquals(SemanticType.BINARY_OBJECT, thrown.expected());
    }

    @Test
    void anAttachmentIsReadAsItsBytesAndItsComponents() {
        Invoice invoice = En16931.view(SemanticDocument.builder()
                .put("/BG-24/0/BT-125",
                        SemanticValue.binary("PDF".getBytes(StandardCharsets.UTF_8),
                                "application/pdf", "note.pdf"))
                .build());
        BinaryObject attachment = invoice.additionalSupportingDocuments().get(0)
                .attachment().orElseThrow();
        assertArrayEquals("PDF".getBytes(StandardCharsets.UTF_8), attachment.bytes());
        assertEquals("application/pdf", attachment.mimeCode());
        assertEquals("note.pdf", attachment.filename());
    }

    @Test
    void aViewCarriesItsDocumentAndItsPath() {
        SemanticDocument document = Examples.document("standard-invoice");
        Invoice invoice = En16931.view(document);
        assertSame(document, invoice.document());
        assertTrue(invoice.path().isRoot());
        assertSame(document, invoice.seller().document());
        assertEquals(SemanticPath.group("/BG-4"), invoice.seller().path());
        assertEquals("Seller[/BG-4]", invoice.seller().toString());
        assertEquals("Invoice[]", invoice.toString());
    }

    @Test
    void occurrencesAreCountedUpToTheFirstIndexTheDocumentDoesNotCarry() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-23/0/BT-116", "100")
                .put("/BG-23/2/BT-116", "200")
                .build();
        List<VatBreakdown> breakdowns = En16931.view(document).vatBreakdowns();
        assertEquals(1, breakdowns.size());
        assertFalse(breakdowns.isEmpty());
    }
}
