package de.bsnsoft.esj.invoice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.invoice.code.AllowanceReason;
import de.bsnsoft.esj.invoice.code.ChargeReason;
import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.Country;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.invoice.code.ElectronicAddressScheme;
import de.bsnsoft.esj.invoice.code.Unit;
import de.bsnsoft.esj.invoice.code.VatExemptionReason;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.typed.build.BuildException;
import de.bsnsoft.esj.typed.build.BuildReport;
import de.bsnsoft.esj.typed.build.InvoiceBuilder;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.typed.build.Profile;
import de.bsnsoft.esj.typed.build.RuleViolation;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The domain API over the constrained builder: what it writes, and what it refuses. */
class DomainInvoiceTest {

    @TempDir
    private Path directory;

    /**
     * The two snippets of the {@code docs/getting-started.md} section "Create an invoice,
     * validate it, write it": the invoice the domain API writes, which is
     * {@code examples/allowances.esj.json}, and the two lines that write it out. The
     * assertion is on the canonical bytes, so the snippet is the example or the test fails.
     */
    @Test
    void theGettingStartedExampleBuildsTheAllowancesGolden() throws IOException {
        Path file = directory.resolve("invoice.esj.json");
        Path canonicalFile = directory.resolve("invoice.canonical.esj.json");

        // docs/getting-started.md: Create an invoice, validate it, write it
        SemanticDocument document = Invoice.create(Profile.EN16931)
                .number("RE-2026-0211")
                .issued(LocalDate.of(2026, 5, 12))
                .currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address("Musterweg 12", "10117", "Beispielstadt", Country.DE))
                .buyer(Party.named("Muster AG").vatId("DE987654321")
                        .address("Beispielallee 3", "20095", "Musterstadt", Country.DE))
                .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
                        PaymentTerms.days(30, "Payable within 30 days without deduction."))
                .line(Line.of("Sensor module SM-100").quantity(100, Unit.PIECE).unitPrice("12")
                        .vat(Vat.standard(19))
                        .allowance(Allowance.percent(10, AllowanceReason.DISCOUNT)
                                .reason("Quantity discount")))
                .line(Line.of("Printed documentation set").quantity(5, Unit.PIECE).unitPrice("200")
                        .vat(Vat.standard(7))
                        .allowance(Allowance.amount("25", AllowanceReason.SPECIAL_AGREEMENT)
                                .reason("Damaged packaging")))
                .allowance(Allowance.amount("100", AllowanceReason.SPECIAL_REBATE)
                        .reason("Annual volume rebate").vat(Vat.standard(19)))
                .allowance(Allowance.amount("50", AllowanceReason.SPECIAL_REBATE)
                        .reason("Annual volume rebate").vat(Vat.standard(7)))
                .build();

        // docs/getting-started.md: Create an invoice, validate it, write it
        Files.write(file, EsjWriter.pretty().toBytes(document));
        Files.write(canonicalFile, EsjWriter.canonical().toBytes(document));

        assertArrayEquals(Examples.canonical("allowances"),
                EsjWriter.canonical().toBytes(document));
        assertEquals(document.values(),
                EsjReader.strict().read(Files.readAllBytes(file)).values());
        assertArrayEquals(Examples.canonical("allowances"), Files.readAllBytes(canonicalFile));
    }

    /** The snippet of the {@code docs/java-api.md} section "The domain API", read side. */
    @Test
    void theJavaApiPageSnippetBuildsAndReadsBack() {
        SemanticDocument document = Invoice.draft(Profile.EN16931)
                .number("RE-2026-0211")
                .issued(LocalDate.of(2026, 5, 12))
                .currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address("Musterweg 12", "10117", "Beispielstadt", Country.DE))
                .buyer(Party.named("Muster AG")
                        .address("Beispielallee 3", "20095", "Musterstadt", Country.DE))
                .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
                        PaymentTerms.days(30, "Payable within 30 days without deduction."))
                .line(Line.of("Sensor module SM-100").quantity(100, Unit.PIECE).unitPrice("12")
                        .vat(Vat.standard(19)))
                .build();

        // docs/java-api.md: The domain API
        Invoice invoice = Invoice.read(document);
        BigDecimal due = invoice.totals().orElseThrow().amountDue();
        Coded unit = invoice.lines().get(0).unit().orElseThrow();

        assertEquals(0, new BigDecimal("1428.00").compareTo(due));
        assertEquals(Unit.PIECE, unit);
    }

    @Test
    void theDraftWritesTheSameInvoiceInAnyOrder() {
        SemanticDocument chained = Invoice.create(Profile.EN16931)
                .number("RE-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789").address(Address.in(
                        Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .line(Line.of("Sensor").quantity(2, Unit.PIECE).unitPrice("12")
                        .vat(Vat.standard(19)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."))
                .build();

        Draft draft = Invoice.draft(Profile.EN16931);
        draft.line(Line.of("Sensor").quantity(2, Unit.PIECE).unitPrice("12").vat(Vat.standard(19)));
        draft.buyer(Party.named("Muster AG").address(Address.in(Country.DE)));
        draft.seller(Party.named("Example GmbH").vatId("DE123456789")
                .address(Address.in(Country.DE)));
        draft.paymentTerms(PaymentTerms.days(30, "Payable within 30 days."));
        draft.currency(CurrencyCode.EUR).issued(LocalDate.of(2026, 5, 12)).number("RE-1");

        assertArrayEquals(EsjWriter.canonical().toBytes(chained),
                EsjWriter.canonical().toBytes(draft.build()));
    }

    @Test
    void theLineNumbersItselfAndTheTotalsAreDerived() {
        SemanticDocument document = minimal()
                .line(Line.of("Second").quantity(1, Unit.PIECE).unitPrice("8")
                        .vat(Vat.standard(19)))
                .build();

        assertEquals(SemanticValue.of("1"), value(document, "/BG-25/0/BT-126"));
        assertEquals(SemanticValue.of("2"), value(document, "/BG-25/1/BT-126"));
        assertEquals(new BigDecimal("32"), value(document, "/BG-22/BT-106").asDecimal());
        assertEquals(new BigDecimal("6.08"), value(document, "/BG-22/BT-110").asDecimal());
        assertEquals(new BigDecimal("38.08"), value(document, "/BG-22/BT-115").asDecimal());
    }

    @Test
    void aCreditNoteCarriesTheTypeCodeOfACreditNote() {
        SemanticDocument document = Invoice.creditNote(Profile.EN16931)
                .number("CN-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .line(Line.of("Sensor").quantity(1, Unit.PIECE).unitPrice("12")
                        .vat(Vat.standard(19)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."))
                .build();

        assertEquals(SemanticValue.of("381"), value(document, "/BT-3"));
    }

    @Test
    void theXrechnungProfileFixesItsProcessControlAndAsksForItsOwnTerms() {
        Draft draft = Invoice.draft(Profile.XRECHNUNG_3_0)
                .number("RE-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address("Musterweg 12", "10117", "Beispielstadt", Country.DE)
                        .contact("Sales", "+49 30 000000", "sales@example.invalid")
                        .electronicAddress("invoice@example.invalid",
                                ElectronicAddressScheme.ELECTRONIC_MAIL))
                .buyer(Party.named("Muster AG")
                        .address("Beispielallee 3", "20095", "Musterstadt", Country.DE)
                        .electronicAddress("ap@muster.invalid",
                                ElectronicAddressScheme.ELECTRONIC_MAIL))
                .line(Line.of("Sensor").quantity(1, Unit.PIECE).unitPrice("12")
                        .vat(Vat.standard(19)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."));

        BuildException missing = assertThrows(BuildException.class, draft::build);
        assertTrue(missing.getMessage().contains("BT-10"), missing.getMessage());

        SemanticDocument document = draft.buyerReference("04011000-12345-34")
                .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"))
                .build();
        assertEquals(SemanticValue.of("urn:cen.eu:en16931:2017"
                        + "#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0"),
                value(document, "/BG-2/BT-24"));
        assertEquals(SemanticValue.of("urn:fdc:peppol.eu:2017:poacc:billing:01:1.0"),
                value(document, "/BG-2/BT-23"));
    }

    @Test
    void everyVatFactoryWritesItsCategoryAndItsRate() {
        assertEquals("S", Vat.standard(19).category().code());
        assertEquals(new BigDecimal("19"), Vat.standard(19).rate().orElseThrow());
        assertEquals("Z", Vat.zero().category().code());
        assertEquals(BigDecimal.ZERO, Vat.zero().rate().orElseThrow());
        assertEquals("E", Vat.exempt(VatExemptionReason.VATEX_EU_132_1A).category().code());
        assertEquals("AE", Vat.reverseCharge().category().code());
        assertEquals("VATEX-EU-AE",
                Vat.reverseCharge().exemptionReasonCode().orElseThrow().code());
        assertEquals("K", Vat.intraCommunity().category().code());
        assertEquals("G", Vat.export().category().code());
        assertEquals("O", Vat.notSubject().category().code());
        assertTrue(Vat.notSubject().rate().isEmpty());
    }

    @Test
    void anExemptionReasonReachesTheVatBreakdownThroughTheDerivation() {
        SemanticDocument document = Invoice.create(Profile.EN16931)
                .number("RE-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").vatId("FR12345678901")
                        .address(Address.in(Country.FR)))
                .line(Line.of("Sensor").quantity(2, Unit.PIECE).unitPrice("12")
                        .vat(Vat.intraCommunity()))
                .delivery(Delivery.on(LocalDate.of(2026, 5, 10))
                        .address("Rue de l'Exemple 1", "75001", "Paris", Country.FR))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."))
                .build();

        assertEquals(SemanticValue.of("K"), value(document, "/BG-23/0/BT-118"));
        assertEquals(SemanticValue.of("VATEX-EU-IC"), value(document, "/BG-23/0/BT-121"));
        assertEquals(new BigDecimal("0"), value(document, "/BG-23/0/BT-117").asDecimal());
    }

    @Test
    void everyPaymentFactoryWritesTheCodeAndTheAccountThatGoWithIt() {
        assertEquals("58", PaymentMeans.sepaCreditTransfer("DE89").code().code());
        assertEquals("DE89", PaymentMeans.sepaCreditTransfer("DE89").account().orElseThrow());
        assertEquals("BYLADEM1001",
                PaymentMeans.sepaCreditTransfer("DE89", "BYLADEM1001").serviceProvider()
                        .orElseThrow());
        assertEquals("30", PaymentMeans.creditTransfer("123").code().code());
        assertEquals("48", PaymentMeans.card("1234", "A. Muster").code().code());
        assertEquals("59", PaymentMeans.directDebit("M-1", "DE98ZZZ", "DE89").code().code());
        assertEquals("M-1",
                PaymentMeans.directDebit("M-1", "DE98ZZZ", "DE89").mandateReference()
                        .orElseThrow());
    }

    @Test
    void paymentTermsCountTheirDaysFromTheIssueDate() {
        SemanticDocument document = minimal()
                .paymentTerms(PaymentTerms.days(14, "Payable within 14 days."))
                .build();

        assertEquals(SemanticValue.of("2026-05-26"), value(document, "/BT-9"));
        assertEquals(SemanticValue.of("Payable within 14 days."), value(document, "/BT-20"));
    }

    @Test
    void aPercentageOnALineTakesTheLineAsItsBaseAndRoundsOnce() {
        SemanticDocument document = Invoice.create(Profile.EN16931)
                .number("RE-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .line(Line.of("Sensor").quantity(3, Unit.PIECE).unitPrice("0.335")
                        .vat(Vat.standard(19))
                        .allowance(Allowance.percent(new BigDecimal("3.5"),
                                AllowanceReason.DISCOUNT)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."))
                .build();

        assertEquals(new BigDecimal("1.01"),
                value(document, "/BG-25/0/BG-27/0/BT-137").asDecimal());
        assertEquals(new BigDecimal("0.04"),
                value(document, "/BG-25/0/BG-27/0/BT-136").asDecimal());
        assertEquals(new BigDecimal("3.5"), value(document, "/BG-25/0/BG-27/0/BT-138").asDecimal());
        assertEquals(new BigDecimal("0.97"), value(document, "/BG-25/0/BT-131").asDecimal());
    }

    @Test
    void anUnlimitedScaleIsPassedThroughUnchanged() {
        SemanticDocument document = Invoice.create(Profile.EN16931)
                .number("RE-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .line(Line.of("Cable").quantity("2.5", Unit.of("MTR"))
                        .unitPrice("1.234567").vat(Vat.standard(19)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."))
                .build();

        assertEquals(SemanticValue.of("1.234567"), value(document, "/BG-25/0/BG-29/BT-146"));
        assertEquals(SemanticValue.of("2.5"), value(document, "/BG-25/0/BT-129"));
        assertEquals(new BigDecimal("3.09"), value(document, "/BG-25/0/BT-131").asDecimal());
    }

    @Test
    void aChargeIsWrittenAtTheDocumentLevelAndOnALine() {
        SemanticDocument document = minimal()
                .charge(Charge.amount("10", ChargeReason.FREIGHT_SERVICE).vat(Vat.standard(19)))
                .build();

        assertEquals(SemanticValue.of("FC"), value(document, "/BG-21/0/BT-105"));
        assertEquals(new BigDecimal("10"), value(document, "/BG-22/BT-108").asDecimal());
    }

    @Test
    void theEditorIsTheWayToATermTheDomainLayerHasNoWordFor() {
        SemanticDocument document = minimal()
                .edit(invoice -> invoice.taxPointDate(LocalDate.of(2026, 5, 10)))
                .build();

        assertEquals(SemanticValue.of("2026-05-10"), value(document, "/BT-7"));
    }

    @Test
    void aRefusalNamesTheTermThePartyHasNoneOf() {
        Party party = Party.named("Muster AG").taxRegistration("HRB 1234");

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> Invoice.draft(Profile.EN16931).buyer(party));
        assertTrue(refusal.getMessage().contains("BT-32"), refusal.getMessage());

        IllegalArgumentException payee = assertThrows(IllegalArgumentException.class,
                () -> Invoice.draft(Profile.EN16931).payee(Party.named("Factor GmbH")
                        .vatId("DE123456789")));
        assertTrue(payee.getMessage().contains("BT-31"), payee.getMessage());
    }

    @Test
    void aLineAllowanceRefusesAVatOfItsOwn() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> Line.of("Sensor").allowance(
                        Allowance.amount("1", AllowanceReason.DISCOUNT).vat(Vat.standard(19))));
        assertTrue(refusal.getMessage().contains("BG-27"), refusal.getMessage());
    }

    @Test
    void aDocumentLevelAllowanceRefusesAPercentageWithoutABase() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> minimal().allowance(Allowance.percent(10, AllowanceReason.DISCOUNT)
                        .vat(Vat.standard(19))));
        assertTrue(refusal.getMessage().contains("BT-93"), refusal.getMessage());
    }

    @Test
    void anIncompleteInvoiceIsReportedRatherThanThrownWhereTheCallerAsksForAReport() {
        Draft draft = Invoice.draft(Profile.EN16931);
        draft.number("RE-1").currency(CurrencyCode.EUR);

        InvoiceResult result = draft.buildReport();

        assertFalse(result.ok());
        BuildReport report = result.report();
        assertTrue(report.lines().stream().anyMatch(line -> line.contains("BT-2")),
                String.join("; ", report.lines()));
        assertTrue(report.lines().stream().anyMatch(line -> line.contains("BG-25")),
                String.join("; ", report.lines()));
    }

    @Test
    void paymentTermsInDaysWithoutAnIssueDateAreRefusedRatherThanDropped() {
        Draft draft = Invoice.draft(Profile.EN16931)
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."));

        IllegalStateException refusal = assertThrows(IllegalStateException.class, draft::build);
        assertTrue(refusal.getMessage().contains("BT-2"), refusal.getMessage());
    }

    /**
     * The two snippets of the {@code docs/java-api.md} section "Layers" that write an
     * invoice: the generated builder and the domain API over it, one layer apart and the
     * same invoice, which is the claim the section makes.
     */
    @Test
    void theLayersSectionWritesTheSameInvoiceTwoLayersApart() {
        // docs/java-api.md: Layers, the constrained builder
        SemanticDocument built = InvoiceBuilder.create(Profile.EN16931)
                .invoiceNumber("RE-2026-0211")
                .issueDate(LocalDate.of(2026, 5, 12))
                .typeCode("380")
                .currencyCode("EUR")
                .seller(seller -> seller
                        .name("Example GmbH")
                        .postalAddress(address -> address.countryCode("DE"))
                        .vatIdentifier("DE123456789"))
                .buyer(buyer -> buyer
                        .name("Muster AG")
                        .postalAddress(address -> address.countryCode("DE")))
                .invoiceLine(line -> line
                        .identifier("1")
                        .quantity(new BigDecimal("2"), "H87")
                        .price(price -> price.netPrice(new BigDecimal("12.50")))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Sensor module SM-100")))
                .paymentDueDate(LocalDate.of(2026, 6, 11))
                .paymentTerms("Payable within 30 days.")
                .build();

        // docs/java-api.md: Layers, the domain API
        SemanticDocument written = Invoice.create(Profile.EN16931)
                .number("RE-2026-0211")
                .issued(LocalDate.of(2026, 5, 12))
                .currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .line(Line.of("Sensor module SM-100").quantity(2, Unit.PIECE)
                        .unitPrice("12.50").vat(Vat.standard(19)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."))
                .build();

        assertArrayEquals(EsjWriter.canonical().toBytes(built),
                EsjWriter.canonical().toBytes(written));
    }

    @Test
    void aCreditNoteNamesTheInvoiceItCredits() {
        SemanticDocument document = Invoice.creditNote(Profile.EN16931)
                .number("CN-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .precedingInvoice("RE-2026-0001", LocalDate.of(2026, 3, 2))
                .line(Line.of("Sensor").quantity(1, Unit.PIECE).unitPrice("12")
                        .vat(Vat.standard(19)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."))
                .build();

        assertEquals("RE-2026-0001", value(document, "/BG-3/0/BT-25").canonicalContent());
        assertEquals("2026-03-02", value(document, "/BG-3/0/BT-26").canonicalContent());
        PrecedingInvoice read = Invoice.read(document).precedingInvoices().get(0);
        assertEquals("RE-2026-0001", read.number());
        assertEquals(LocalDate.of(2026, 3, 2), read.issued().orElseThrow());
    }

    @Test
    void aSecondSellerIsRefusedRatherThanMergedIntoTheFirst() {
        Draft draft = Invoice.draft(Profile.EN16931)
                .seller(Party.named("Example GmbH").identifier("SELLER-1")
                        .address(Address.in(Country.DE)));

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> draft.seller(Party.named("Other GmbH").identifier("OTHER-1")
                        .address(Address.in(Country.DE))));

        assertTrue(refusal.getMessage().contains("BG-4"), refusal.getMessage());
        assertEquals("SELLER-1", value(draft.document(), "/BG-4/BT-29/0").canonicalContent());
        assertTrue(draft.document().value(SemanticPath.of("/BG-4/BT-29/1")).isEmpty());
    }

    @Test
    void aSecondSetOfPaymentInstructionsIsRefusedThroughTheStepChainToo() {
        Draft draft = minimal()
                .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"))
                .draft();

        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> draft.payment(PaymentMeans.sepaCreditTransfer("DE02120300000000202051")));

        assertTrue(refusal.getMessage().contains("BG-16"), refusal.getMessage());
    }

    @Test
    void aLineWrittenThroughTheEditorIsCountedWhenTheNextLineIsNumbered() {
        SemanticDocument document = minimal()
                .edit(editor -> editor.invoiceLine(line -> line
                        .identifier("A")
                        .quantity(new BigDecimal("1"), "H87")
                        .netAmount(new BigDecimal("1"))
                        .price(price -> price.netPrice(new BigDecimal("1")))
                        .vat(vat -> vat.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(item -> item.name("Written past the domain layer"))))
                .line(Line.of("Numbered by the draft").quantity(1, Unit.PIECE).unitPrice("1")
                        .vat(Vat.standard(19)))
                .build();

        assertEquals("1", value(document, "/BG-25/0/BT-126").canonicalContent());
        assertEquals("A", value(document, "/BG-25/1/BT-126").canonicalContent());
        assertEquals("3", value(document, "/BG-25/2/BT-126").canonicalContent());
    }

    @Test
    void twoLinesUnderOneIdentifierAreNamedWhenTheInvoiceIsBuilt() {
        InvoiceSteps.Buildable invoice = minimal()
                .line(Line.of("Second").identifier("1").quantity(1, Unit.PIECE).unitPrice("1")
                        .vat(Vat.standard(19)));

        IllegalStateException refusal = assertThrows(IllegalStateException.class, invoice::build);

        assertTrue(refusal.getMessage().contains("BT-126"), refusal.getMessage());
    }

    @Test
    void anInvoiceAgainstAPrepaymentStatesWhatWasPaidAndWhatIsLeft() {
        SemanticDocument document = minimal().paidAmount(new BigDecimal("10.00")).build();

        assertEquals("10", value(document, "/BG-22/BT-113").canonicalContent());
        assertEquals(new BigDecimal("18.56"),
                Invoice.read(document).totals().orElseThrow().amountDue());
    }

    @Test
    void theWriteSideAndTheReadSideCoverTheSameReferencesAndAttachments() {
        SemanticDocument document = minimal()
                .contractReference("CT-7")
                .projectReference("PR-3")
                .purchaseOrderReference("PO-9")
                .attachment(Attachment.referencing("TS-1").description("Time sheet"))
                .build();

        Invoice invoice = Invoice.read(document);
        assertEquals("CT-7", invoice.contractReference().orElseThrow());
        assertEquals("PR-3", invoice.projectReference().orElseThrow());
        assertEquals("PO-9", invoice.purchaseOrderReference().orElseThrow());
        assertEquals("TS-1", invoice.attachments().get(0).reference());
        assertEquals("Time sheet", invoice.attachments().get(0).description().orElseThrow());
    }

    @Test
    void anAttachmentReadBackEqualsTheOneThatWasWritten() {
        Attachment written = Attachment.embedded("TS-1", "time sheet".getBytes(UTF_8),
                        "application/pdf", "hours.pdf")
                .description("Time sheet");
        SemanticDocument document = minimal().attachment(written).build();

        Attachment read = Invoice.read(document).attachments().get(0);

        assertEquals(written, read);
        assertEquals(written.hashCode(), read.hashCode());
        assertEquals("Attachment[BT-122=TS-1, BT-123=Time sheet,"
                + " BT-125=10 bytes of application/pdf as hours.pdf]", read.toString());
        assertNotEquals(written, Attachment.referencing("TS-1"));
    }

    @Test
    void aContactNamesEachOfItsThreeParts() {
        Contact fluent = Contact.named("Sales").telephone("+49 30 000000")
                .email("sales@example.invalid");

        assertEquals(Contact.of("Sales", "+49 30 000000", "sales@example.invalid"), fluent);
        assertEquals("Sales", fluent.name().orElseThrow());
        assertEquals("+49 30 000000", fluent.telephone().orElseThrow());
        assertEquals("sales@example.invalid", fluent.email().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> Contact.named(" "));
    }

    /**
     * A finding the rule pack does not call fatal reaches the report and refuses nothing.
     * BR-CO-17 admits a VAT category tax amount one unit of the currency away from the rate
     * applied to the taxable amount, and says so; the invoice is built all the same.
     */
    @Test
    void aNonFatalFindingOfTheRulePackIsReportedAndRefusesNothing() {
        Draft draft = Invoice.draft(Profile.EN16931)
                .number("RE-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."));
        draft.edit(invoice -> invoice.vatBreakdown(vat -> vat
                .vatCategoryCode("S")
                .vatRate(new BigDecimal("19"))
                .taxableAmount(new BigDecimal("1000.00"))
                .taxAmount(new BigDecimal("191.00"))));

        BuildReport report = draft.validate();
        List<RuleViolation> tolerated = report.violations().stream()
                .filter(violation -> "BR-CO-17".equals(violation.rule()))
                .toList();

        assertEquals(1, tolerated.size(), report.lines().toString());
        assertFalse(tolerated.get(0).fatal(), tolerated.get(0).toString());
    }

    private static InvoiceSteps.Buildable minimal() {
        return Invoice.create(Profile.EN16931)
                .number("RE-1").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
                .seller(Party.named("Example GmbH").vatId("DE123456789")
                        .address(Address.in(Country.DE)))
                .buyer(Party.named("Muster AG").address(Address.in(Country.DE)))
                .line(Line.of("Sensor").quantity(2, Unit.PIECE).unitPrice("12")
                        .vat(Vat.standard(19)))
                .paymentTerms(PaymentTerms.days(30, "Payable within 30 days."));
    }

    private static SemanticValue value(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path)).orElseThrow(
                () -> new AssertionError(path + " is not in the document"));
    }
}
