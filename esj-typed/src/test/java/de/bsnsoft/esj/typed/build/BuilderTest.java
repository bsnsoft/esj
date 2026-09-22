package de.bsnsoft.esj.typed.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** What the constrained builder refuses, and what a profile adds to its chain. */
class BuilderTest {

    private static final Function<InvoiceLineSteps.Start, InvoiceLineSteps.Buildable> LINE =
            line -> line.identifier("1")
                    .quantity(new BigDecimal("2"), "H87")
                    .price(price -> price.netPrice(new BigDecimal("12.50")))
                    .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                    .item(item -> item.name("Sensor module SM-100"));

    @Test
    void aProfileWritesTheValuesItFixesWhenTheBuilderIsOpened() {
        SemanticDocument core = InvoiceBuilder.draft(Profile.EN16931).document();
        SemanticDocument xrechnung = InvoiceBuilder.draft(Profile.XRECHNUNG_3_0).document();

        assertEquals("urn:cen.eu:en16931:2017", content(core, "/BG-2/BT-24"));
        assertEquals("urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0",
                content(xrechnung, "/BG-2/BT-24"));
        assertEquals("urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
                content(xrechnung, "/BG-2/BT-23"));
    }

    @Test
    void aDraftWithoutLinesIsRefusedAtRunTimeWithTheTermsItLacks() {
        InvoiceDraft draft = InvoiceBuilder.draft(Profile.EN16931);
        draft.edit(invoice -> invoice
                .invoiceNumber("RE-1")
                .issueDate(LocalDate.of(2026, 2, 3))
                .typeCode("380")
                .currencyCode("EUR")
                .seller(seller -> seller.name("Example GmbH")
                        .postalAddress(address -> address.countryCode("DE")))
                .buyer(buyer -> buyer.name("Muster AG")
                        .postalAddress(address -> address.countryCode("DE"))));

        BuildException refused = assertThrows(BuildException.class, draft::build);

        List<String> missing = refused.report().missing().stream().map(MissingTerm::term).toList();
        assertEquals(List.of("BG-25"), missing);
        assertTrue(refused.getMessage().contains("BG-25 INVOICE LINE is missing"),
                refused.getMessage());
    }

    @Test
    void aDraftNamesEveryTermItLacksByIdentifierAndByName() {
        BuildException refused = assertThrows(BuildException.class,
                () -> InvoiceBuilder.draft(Profile.EN16931).build());

        List<String> missing = refused.report().missing().stream()
                .map(term -> term.term() + " " + term.name())
                .toList();
        assertEquals(List.of("BT-1 Invoice number", "BT-2 Invoice issue date",
                "BT-3 Invoice type code", "BT-5 Invoice currency code", "BG-4 SELLER",
                "BG-7 BUYER", "BG-25 INVOICE LINE"), missing);
    }

    @Test
    void aProfileNarrowingIsAskedForByTheDraftAsWell() {
        InvoiceDraft draft = InvoiceBuilder.draft(Profile.XRECHNUNG_3_0);
        List<String> missing = draft.validate().missing().stream()
                .map(MissingTerm::term)
                .toList();

        assertTrue(missing.contains("BT-10"), "the buyer reference: " + missing);
        assertTrue(missing.contains("BG-16"), "the payment instructions: " + missing);
        assertFalse(missing.contains("BT-23"), "the profile fixed it: " + missing);
    }

    @Test
    void theXrechnungChainAsksForTheBuyerReferenceAndTheElectronicAddresses() {
        SemanticDocument document = InvoiceBuilder.create(Profile.XRECHNUNG_3_0)
                .invoiceNumber("RE-1")
                .issueDate(LocalDate.of(2026, 2, 3))
                .typeCode("380")
                .currencyCode("EUR")
                .buyerReference("KOST-4711")
                .seller(seller -> seller
                        .name("Example GmbH")
                        .electronicAddress("invoices@example.invalid", "EM")
                        .postalAddress(address -> address
                                .city("Beispielstadt")
                                .postCode("10117")
                                .countryCode("DE"))
                        .contact(contact -> contact
                                .name("Accounts receivable")
                                .telephone("+49 30 1234567")
                                .email("billing@example.invalid")))
                .buyer(buyer -> buyer
                        .name("Muster AG")
                        .electronicAddress("rechnung@muster.invalid", "EM")
                        .postalAddress(address -> address
                                .city("Musterstadt")
                                .postCode("20095")
                                .countryCode("DE")))
                .paymentInstructions(payment -> payment.paymentMeansTypeCode("58"))
                .invoiceLine(LINE)
                .build();

        assertEquals("KOST-4711", content(document, "/BT-10"));
        assertEquals("invoices@example.invalid", content(document, "/BG-4/BT-34"));
        assertEquals("rechnung@muster.invalid", content(document, "/BG-7/BT-49"));
    }

    @Test
    void aMemberTheModelAllowsOnceIsRefusedTheSecondTime() {
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> InvoiceBuilder.create(Profile.EN16931)
                        .invoiceNumber("RE-1")
                        .issueDate(LocalDate.of(2026, 2, 3))
                        .typeCode("380")
                        .currencyCode("EUR")
                        .seller(seller -> seller.name("Example GmbH")
                                .postalAddress(address -> address.countryCode("DE")))
                        .buyer(buyer -> buyer.name("Muster AG")
                                .postalAddress(address -> address.countryCode("DE")))
                        .invoiceLine(LINE)
                        .buyerReference("KOST-4711")
                        .buyerReference("KOST-4712"));

        assertTrue(refused.getMessage().startsWith("BT-10 Buyer reference occurs at most once"),
                refused.getMessage());
    }

    @Test
    void aRepeatableMemberIsWrittenAsOftenAsTheModelAllows() {
        SemanticDocument document = InvoiceBuilder.create(Profile.EN16931)
                .invoiceNumber("RE-1")
                .issueDate(LocalDate.of(2026, 2, 3))
                .typeCode("380")
                .currencyCode("EUR")
                .seller(seller -> seller.name("Example GmbH")
                        .postalAddress(address -> address.countryCode("DE"))
                        .identifier("4399901000018", "0088")
                        .identifier("DE-12345"))
                .buyer(buyer -> buyer.name("Muster AG")
                        .postalAddress(address -> address.countryCode("DE")))
                .invoiceLine(LINE)
                .invoiceLine(line -> line.identifier("2")
                        .quantity(new BigDecimal("1"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("100")))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Training")))
                .build();

        assertEquals("4399901000018", content(document, "/BG-4/BT-29/0"));
        assertEquals("DE-12345", content(document, "/BG-4/BT-29/1"));
        assertEquals("25", content(document, "/BG-25/0/BT-131"));
        assertEquals("100", content(document, "/BG-25/1/BT-131"));
    }

    @Test
    void theBusinessRulesTheCallerSuppliesDecideTheBuildAsWell() {
        InvoiceRules refuseEverything = document -> List.of(
                new RuleViolation("BR-EXAMPLE-1", "this invoice is not acceptable here",
                        List.of("/BT-1")));

        BuildException refused = assertThrows(BuildException.class,
                () -> InvoiceBuilder.draft(Profile.EN16931)
                        .edit(invoice -> invoice.invoiceNumber("RE-1"))
                        .validateOrThrow(refuseEverything));

        assertEquals(List.of("BR-EXAMPLE-1"),
                refused.report().violations().stream().map(RuleViolation::rule).toList());
    }

    @Test
    void aBusinessRuleThatIsNotFatalIsReportedAndRefusesNothing() {
        InvoiceRules sayTwoThingsAndRefuseNothing = document -> List.of(
                new RuleViolation("BR-EXAMPLE-2", "worth saying", List.of("/BT-1"), false),
                new RuleViolation("BR-EXAMPLE-3", "not decided", List.of("/BT-1"), false));

        BuildReport report = completeInvoice().buildReport(sayTwoThingsAndRefuseNothing);

        assertTrue(report.ok(), report.lines().toString());
        assertEquals(List.of("BR-EXAMPLE-2", "BR-EXAMPLE-3"),
                report.violations().stream().map(RuleViolation::rule).toList());
        assertTrue(report.lines().stream().anyMatch(line -> line.contains("not fatal")));
        assertEquals("25", content(completeInvoice().build(), "/BG-25/0/BT-131"));
    }

    private static InvoiceDraft completeInvoice() {
        return InvoiceBuilder.draft(Profile.EN16931)
                .edit(invoice -> invoice.invoiceNumber("RE-1")
                        .issueDate(LocalDate.of(2026, 2, 3))
                        .typeCode("380")
                        .currencyCode("EUR"))
                .edit(invoice -> invoice.seller(seller -> seller.name("Example GmbH")
                        .postalAddress(address -> address.countryCode("DE"))))
                .edit(invoice -> invoice.buyer(buyer -> buyer.name("Muster AG")
                        .postalAddress(address -> address.countryCode("DE"))))
                .edit(invoice -> invoice.invoiceLine(line -> line.identifier("1")
                        .quantity(new BigDecimal("2"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("12.50")))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Sensor module SM-100"))));
    }

    @Test
    void theEditorIsTheWayToWhatTheBuilderDoesNotOffer() {
        InvoiceSteps.Buildable invoice = InvoiceBuilder.create(Profile.EN16931)
                .invoiceNumber("RE-1")
                .issueDate(LocalDate.of(2026, 2, 3))
                .typeCode("380")
                .currencyCode("EUR")
                .seller(seller -> seller.name("Example GmbH")
                        .postalAddress(address -> address.countryCode("DE")))
                .buyer(buyer -> buyer.name("Muster AG")
                        .postalAddress(address -> address.countryCode("DE")))
                .invoiceLine(LINE);
        invoice.editor().builder().put("/BT-6", "CHF");
        invoice.derive();

        assertEquals("CHF", content(invoice.document(), "/BT-6"));
        assertTrue(invoice.validate().ok(), invoice.validate().lines().toString());
    }

    private static String content(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path))
                .orElseThrow(() -> new AssertionError("no value at " + path))
                .content();
    }
}
