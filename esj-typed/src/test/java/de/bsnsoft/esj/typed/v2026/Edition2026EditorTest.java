package de.bsnsoft.esj.typed.v2026;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.validate.ValidationResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The typed view of EN 16931-1:2026, checked against the example it builds.
 * {@code examples/edition-2026.esj.json} is written here once more through names alone
 * and has to come out as the same bytes, so the example and the view of that edition
 * cannot drift apart.
 *
 * <p>The invoice exercises what the 2026 edition adds and the 2017 edition has no address
 * for: the payment terms group with an early payment discount and a late payment penalty,
 * the charges a seller collects on behalf of a third party, the delivery information and
 * the preceding invoice reference of a single invoice line, and the invoice issue time,
 * the one term of the new semantic data type Time.
 *
 * <p>This test is compiled where the build carries the files of that edition. The Maven
 * profile {@code without-edition-2026} leaves them out, and this package with them.
 */
class Edition2026EditorTest {

    /** What the seller collects for a third party, and what the buyer is told about it. */
    private static final String THIRD_PARTY =
            "Packaging levy collected for the national recycling scheme, ordinance 2025/14.";

    private static final String TERMS =
            "Payable within 30 days without deduction. Two per cent discount within ten days.";

    /**
     * Builds {@code examples/edition-2026.esj.json} through the editor, without a single
     * semantic path.
     *
     * @return the editor, so that a test can go on writing into it
     */
    private static InvoiceEditor invoice() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceNumber("RE-2026-0501")
                .issueDate(LocalDate.of(2026, 9, 1))
                .issueTime(OffsetTime.of(9, 15, 0, 0, ZoneOffset.ofHours(2)))
                .typeCode("380")
                .currencyCode("EUR");
        invoice.buyerReference().add("KOST-2026-11");
        invoice.processControl().specificationIdentifier("urn:cen.eu:en16931:2026");

        invoice.seller()
                .name("Example GmbH")
                .vatIdentifier("DE123456789")
                .postalAddress(address -> address
                        .addressLine1("Hauptstrasse 1")
                        .city("Saarbruecken")
                        .postCode("66111")
                        .countryCode("DE"));
        invoice.buyer()
                .name("Beispiel AG")
                .postalAddress(address -> address
                        .addressLine1("Werkstrasse 18")
                        .city("Trier")
                        .postCode("54290")
                        .countryCode("DE"));

        invoice.paymentTerm(terms -> {
            terms.paymentTerms(TERMS);
            terms.earlyPaymentDiscount(discount -> discount
                    .endDate(LocalDate.of(2026, 9, 11))
                    .percentage(new BigDecimal("2")));
            terms.latePaymentPenalty(penalty -> penalty
                    .startDate(LocalDate.of(2026, 10, 2))
                    .yearlyInterestPercentage(new BigDecimal("9.2")));
        });

        invoice.invoiceLine(line -> {
            line.identifier("1")
                    .quantity(new BigDecimal("4"), "H87")
                    .netAmount(new BigDecimal("1000"));
            line.price().netPrice(new BigDecimal("250"));
            line.vat().vatCategoryCode("S").vatRate(new BigDecimal("19"));
            line.item().name("Control unit XC-40");
            line.delivery(delivery -> {
                delivery.partyName("Beispiel AG, works Trier")
                        .actualDeliveryDate(LocalDate.of(2026, 8, 28));
                delivery.address(address -> address
                        .city("Trier")
                        .countryCode("DE"));
                delivery.period(period -> period
                        .startDate(LocalDate.of(2026, 8, 24))
                        .endDate(LocalDate.of(2026, 8, 28)));
            });
            line.precedingInvoiceReference(preceding -> preceding
                    .reference("RE-2026-0488")
                    .issueDate(LocalDate.of(2026, 8, 3)));
        });

        invoice.vatBreakdown(breakdown -> breakdown
                .taxableAmount(new BigDecimal("1000"))
                .taxAmount(new BigDecimal("190"))
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("19")));

        invoice.documentTotals(totals -> {
            totals.sumOfLineNetAmounts(new BigDecimal("1000"))
                    .totalWithoutVat(new BigDecimal("1000"))
                    .totalVatAmount(new BigDecimal("190"))
                    .totalWithVat(new BigDecimal("1190"))
                    .amountDueForPayment(new BigDecimal("1215"));
            totals.thirdPartyCharge(charge -> charge
                    .amount(new BigDecimal("25"))
                    .specification(THIRD_PARTY));
        });
        return invoice;
    }

    @Test
    void theEditorWritesTheExampleOfTheRepository() {
        SemanticDocument document = invoice().document();

        assertArrayEquals(example("edition-2026.canonical.esj.json"),
                EsjWriter.canonical().toBytes(document),
                "the canonical form of the example is what the editor writes");
        assertArrayEquals(example("edition-2026.esj.json"),
                EsjWriter.pretty().toBytes(document),
                "and its pretty form is the same document");
    }

    @Test
    void theExampleIsStructurallyValidAgainstTheRegistryOfItsOwnEdition() {
        SemanticDocument document = EsjReader.strict().read(example("edition-2026.esj.json"));
        assertEquals("EN16931-1:2026", document.semanticModel());

        ValidationResult result = StructuralValidator.validate(document,
                Registry.forEdition("2026"),
                EnumSet.of(ValidationLayer.L2, ValidationLayer.L3));

        assertEquals(List.of(), result.findings().stream().filter(Finding::isError).toList());
    }

    @Test
    void thePrettyFormAndTheCanonicalFormAreTheSameDocument() {
        assertArrayEquals(example("edition-2026.canonical.esj.json"),
                Canonicalizer.canonicalize(example("edition-2026.esj.json")));
    }

    @Test
    void theViewReadsBackWhatTheEditorWrote() {
        Invoice view = En16931.view(EsjReader.strict().read(example("edition-2026.esj.json")));

        assertEquals("RE-2026-0501", view.invoiceNumber().value());
        assertEquals(Optional.of(OffsetTime.of(9, 15, 0, 0, ZoneOffset.ofHours(2))),
                view.issueTime());
        assertEquals(1, view.paymentTerms().size());
        PaymentTerm terms = view.paymentTerms().get(0);
        assertEquals(Optional.of(TERMS), terms.paymentTerms());
        assertEquals(LocalDate.of(2026, 9, 11), terms.earlyPaymentDiscounts().get(0).endDate());
        assertEquals(Optional.of(new BigDecimal("9.2")),
                terms.latePaymentPenalties().get(0).yearlyInterestPercentage());
        assertEquals(new BigDecimal("25"),
                view.documentTotals().thirdPartyCharges().get(0).amount());
        InvoiceLineDelivery delivery = view.invoiceLines().get(0).delivery().orElseThrow();
        assertEquals(Optional.of("DE"), delivery.address().map(InvoiceLineDeliveryAddress::countryCode));
        assertEquals("RE-2026-0488",
                view.invoiceLines().get(0).precedingInvoiceReferences().get(0).reference());
    }

    /**
     * A time keeps the offset it was written with. It is the one thing the new semantic
     * data type adds over a date, and a Java type that dropped it would turn one instant
     * into another.
     */
    @Test
    void theTimeOfTheInvoiceKeepsItsOffset() {
        Invoice view = En16931.view(EsjReader.strict().read(example("edition-2026.esj.json")));
        OffsetTime time = view.issueTime().orElseThrow();

        assertEquals(ZoneOffset.ofHours(2), time.getOffset());
        assertTrue(read("/examples/edition-2026.esj.json").contains("\"09:15:00+02:00\""),
                "the document carries the time with its offset, not a local time");
    }

    /**
     * The 2026 view addresses BT-20 inside the payment terms group, where that edition
     * puts it, and the paths it writes are that edition's paths and not the 2017 ones.
     */
    @Test
    void theTermsThatMovedAreWrittenWhereThisEditionHasThem() {
        SemanticDocument document = invoice().document();

        assertTrue(document.value(SemanticPath.of("/BG-33/0/BT-20")).isPresent());
        assertFalse(document.value(SemanticPath.of("/BT-20")).isPresent());
        assertTrue(document.value(SemanticPath.of("/BG-25/0/BG-37/BG-26/BT-134")).isPresent());
        assertFalse(document.value(SemanticPath.of("/BG-25/0/BG-26/BT-134")).isPresent());
    }

    private static byte[] example(String name) {
        return bytes("/examples/" + name);
    }

    private static String read(String resource) {
        return new String(bytes(resource), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String resource) {
        try (InputStream in = Edition2026EditorTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the test classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
