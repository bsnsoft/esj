package de.bsnsoft.esj.typed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.handler.DocumentCollector;
import de.bsnsoft.esj.handler.Replay;
import de.bsnsoft.esj.handler.SemanticHandler;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.json.ReadResult;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.typed.DerivationReport.Derived;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.validate.NotEvaluatedReason;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.validate.ValidationResult;
import de.bsnsoft.esj.validate.ValidationStatus;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The snippets of {@code docs/java-api.md}, of {@code README.md} and of
 * {@code docs/getting-started.md}, one test per snippet, so that no page can describe an API
 * the modules do not have. The lines between the comment naming a page and its section and
 * the assertions are the snippet as that page prints it; a change to one is a change to the
 * other. A snippet two pages show verbatim names both.
 */
class ReadmeExamplesTest {

    @TempDir
    Path directory;

    private Path writeExample(String name) throws IOException {
        Path file = directory.resolve(name + ".esj.json");
        Files.write(file, Examples.pretty(name));
        return file;
    }

    @Test
    void readADocumentFromAFile() throws IOException {
        Path file = writeExample("standard-invoice");

        // docs/java-api.md, docs/getting-started.md: Reading a document
        SemanticDocument document = EsjReader.strict().read(Files.readAllBytes(file));

        assertEquals("EN16931-1:2017+A1:2019/AC:2020", document.semanticModel());
        assertEquals(81, document.values().size());
    }

    @Test
    void readADocumentAndCollectTheFindingsInsteadOfThrowing() {
        byte[] bytes = Examples.invalid("object-without-component");

        // docs/java-api.md: Reading a document
        ReadResult result = EsjReader.strict().readWithFindings(bytes);
        List<String> problems = result.findings().stream().map(Finding::toString).toList();

        assertFalse(result.isWellFormed());
        assertFalse(problems.isEmpty());
        assertTrue(problems.get(0).startsWith("ESJ-L1-VALUE-SHAPE"));
    }

    @Test
    void readWithLimitsOfTheCallersOwn() {
        // docs/java-api.md: Reading a document
        EsjReader reader = EsjReader.withLimits(
                Limits.defaults().toBuilder().maxValues(5_000).build());

        assertEquals(5_000, reader.limits().maxValues());
        assertEquals(Limits.defaults().maxDocumentBytes(), reader.limits().maxDocumentBytes());
    }

    @Test
    void addressAValueByItsBusinessTerm() {
        SemanticDocument document = Examples.document("standard-invoice");

        // docs/java-api.md: Layers
        String invoiceNumber = document.value(SemanticPath.of("/BT-1"))
                .map(SemanticValue::asString)
                .orElseThrow();
        BigDecimal firstLineNet = document.value(SemanticPath.of("/BG-25/0/BT-131"))
                .map(SemanticValue::asDecimal)
                .orElseThrow();

        assertEquals("RE-2026-0042", invoiceNumber);
        assertEquals(new BigDecimal("250"), firstLineNet);
    }

    @Test
    void iterateEveryValueInCanonicalOrder() {
        SemanticDocument document = Examples.document("standard-invoice");

        // docs/java-api.md: Layers
        List<String> lines = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            SemanticPath path = entry.getKey();
            SemanticValue value = entry.getValue();
            lines.add(path + " " + value.canonicalContent());
        }

        assertEquals(81, lines.size());
        assertEquals("/BT-1 RE-2026-0042", lines.get(0));
        assertEquals("/BT-2 2026-02-03", lines.get(1));
    }

    @Test
    void validateAgainstTheRegistryAtBothLayersOrAtOne() {
        SemanticDocument document = Examples.document("standard-invoice");

        // docs/java-api.md: Validating
        Registry registry = Registry.en16931();
        ValidationResult result = StructuralValidator.validate(document, registry);
        ValidationStatus status = result.status();

        ValidationResult cardinality = StructuralValidator.validate(
                document, registry, EnumSet.of(ValidationLayer.L3));

        assertEquals(ValidationStatus.INDETERMINATE, status);
        assertEquals(List.of(), result.findings());
        assertEquals(List.of(), cardinality.findings());
    }

    /**
     * The composition of the three layers, guarded the way the page guards it: the model
     * layers are asked only of a document the reader read whole, because a member layer L1
     * rejected is absent from what they would measure (specification, section 9.3).
     */
    @Test
    void composeTheLayersOfTheReaderAndOfTheValidator() {
        Registry registry = Registry.en16931();

        // docs/java-api.md: The three states
        ReadResult read = EsjReader.strict()
                .readWithFindings(Examples.canonical("standard-invoice"));
        ValidationResult verdict = read.isWellFormed()
                ? read.validation().merge(
                        StructuralValidator.validate(read.orElseThrow(), registry))
                : read.validation();

        assertEquals(ValidationStatus.VALID, verdict.status());
    }

    /**
     * The other half of the guard: a document with one defective member reaches the model
     * layers not at all, so no finding claims that a term the file carries is missing.
     */
    @Test
    void aDocumentThatFailedLayerOneIsNotMeasuredAgainstTheModelLayers() {
        Registry registry = Registry.en16931();
        byte[] bytes = Examples.invalid("empty-string-value");

        ReadResult read = EsjReader.strict().readWithFindings(bytes);
        ValidationResult verdict = read.isWellFormed()
                ? read.validation().merge(
                        StructuralValidator.validate(read.orElseThrow(), registry))
                : read.validation();

        assertEquals(ValidationStatus.INVALID, verdict.status());
        assertEquals(List.of(FindingCode.ESJ_L1_EMPTY_STRING),
                verdict.findings().stream().map(Finding::code).toList(),
                "no cardinality finding about a term the document carries");
        assertEquals(NotEvaluatedReason.PRECEDING_LAYER_FAILED,
                verdict.notEvaluated().get(ValidationLayer.L2));
    }

    @Test
    void validateWithAnExtensionRegistryLoadedBesideTheCoreOne() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-25/0/BG-DEX-01/0/BG-DEX-07/BT-146",
                        SemanticValue.ofDecimal(new BigDecimal("12.5")))
                .build();

        // docs/java-api.md: The three states
        Registry registry = Registry.en16931().withExtension(Registry.xrechnungExtension());
        ValidationResult result = StructuralValidator.validate(
                document, registry, EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(), result.findings());
    }

    @Test
    void canonicalizeADocumentAndTakeItsDigests() {
        SemanticDocument document = Examples.document("extended");

        // docs/java-api.md: Canonical form and digests
        byte[] canonical = Canonicalizer.canonicalBytes(document);
        String semanticDigest = Canonicalizer.semanticDigest(document);
        String documentDigest = Canonicalizer.documentDigest(document);

        assertArrayEquals(Examples.canonical("extended"), canonical);
        assertEquals(64, semanticDigest.length());
        assertEquals(Canonicalizer.semanticDigest(Examples.document("minimal")), semanticDigest);
        assertNotEquals(semanticDigest, documentDigest);
    }

    @Test
    void canonicalizeBytesWithoutBuildingADocument() {
        byte[] bytes = Examples.pretty("extended");

        // docs/java-api.md: Canonical form and digests
        byte[] fromBytes = Canonicalizer.canonicalize(bytes);

        assertArrayEquals(Examples.canonical("extended"), fromBytes);
    }

    @Test
    void writeAPrettyFileAndCanonicalBytes() throws IOException {
        SemanticDocument document = Examples.document("minimal");
        Path file = directory.resolve("invoice.esj.json");

        // docs/java-api.md: Canonical form and digests
        Files.write(file, EsjWriter.pretty().toBytes(document));
        byte[] canonical = EsjWriter.canonical().toBytes(document);

        assertEquals(new String(Examples.pretty("minimal"), StandardCharsets.UTF_8).stripTrailing(),
                Files.readString(file).stripTrailing());
        assertArrayEquals(Examples.canonical("minimal"), canonical);
    }

    @Test
    void buildADocumentWithTheBuilder() {
        // docs/java-api.md: Building a document
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", "RE-2026-0001")
                .put("/BT-2", SemanticValue.ofDate(LocalDate.of(2026, 1, 15)))
                .put("/BT-3", "380")
                .put("/BT-5", "EUR")
                .put("/BG-4/BT-27", "Example GmbH")
                .put("/BG-4/BT-29/0", SemanticValue.identifier("0088123456785", "0088"))
                .put("/BG-25/0/BT-129", SemanticValue.ofDecimal(new BigDecimal("1")))
                .put("/BG-25/0/BT-131", SemanticValue.ofDecimal(new BigDecimal("100")))
                .source(SemanticDocument.Source.ofSyntax("UBL"))
                .build();

        assertEquals(8, document.values().size());
        assertEquals("UBL", document.source().orElseThrow().syntax().orElseThrow());
        assertEquals("/BT-1", document.values().firstKey().toString());
    }

    @Test
    void amendADocumentThroughItsBuilder() {
        SemanticDocument document = Examples.document("standard-invoice");

        // docs/java-api.md: Building a document
        SemanticDocument amended = document.toBuilder()
                .put("/BT-11", "PRJ-2026-7")
                .remove("/BT-19")
                .remove("/BT-10")
                .put("/BT-10", "04011000-12345-34")
                .build();

        assertEquals(SemanticValue.of("PRJ-2026-7"),
                amended.value(SemanticPath.of("/BT-11")).orElseThrow());
        assertEquals(SemanticValue.of("04011000-12345-34"),
                amended.value(SemanticPath.of("/BT-10")).orElseThrow());
        assertTrue(amended.value(SemanticPath.of("/BT-19")).isEmpty());
        assertTrue(document.value(SemanticPath.of("/BT-19")).isPresent());
    }

    /**
     * The snippet of the {@code docs/java-api.md} section "Building an invoice", which
     * writes {@code examples/allowances.esj.json}. The assertion is on the canonical bytes:
     * the snippet is the example or the test fails, and neither can drift away from the
     * other. {@code docs/getting-started.md} writes the same invoice through the domain API
     * of {@code esj-invoice}, whose test asserts the same bytes.
     */
    @Test
    void buildAnInvoiceThroughTheEditor() {
        // docs/java-api.md: Building an invoice
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceNumber("RE-2026-0211")
                .issueDate(LocalDate.of(2026, 5, 12))
                .typeCode("380")
                .currencyCode("EUR")
                .paymentDueDate(LocalDate.of(2026, 6, 11))
                .paymentTerms("Payable within 30 days without deduction.")
                .processControl(c -> c.specificationIdentifier("urn:cen.eu:en16931:2017"));

        invoice.seller()
                .name("Example GmbH")
                .vatIdentifier("DE123456789")
                .postalAddress(a -> a.addressLine1("Musterweg 12").city("Beispielstadt")
                        .postCode("10117").countryCode("DE"));

        invoice.buyer()
                .name("Muster AG")
                .vatIdentifier("DE987654321")
                .postalAddress(a -> a.addressLine1("Beispielallee 3").city("Musterstadt")
                        .postCode("20095").countryCode("DE"));

        invoice.paymentInstructions(p -> p
                .paymentMeansTypeCode("58")
                .creditTransfer(t -> t.accountIdentifier("DE89370400440532013000")));

        invoice.documentLevelAllowance(a -> a
                        .amount(new BigDecimal("100"))
                        .vatCategoryCode("S")
                        .vatRate(new BigDecimal("19"))
                        .reason("Annual volume rebate")
                        .reasonCode("100"))
                .documentLevelAllowance(a -> a
                        .amount(new BigDecimal("50"))
                        .vatCategoryCode("S")
                        .vatRate(new BigDecimal("7"))
                        .reason("Annual volume rebate")
                        .reasonCode("100"));

        invoice.invoiceLine(line -> line
                        .identifier("1")
                        .quantity(new BigDecimal("100"), "H87")
                        .allowance(a -> a
                                .amount(new BigDecimal("120"))
                                .baseAmount(new BigDecimal("1200"))
                                .percentage(new BigDecimal("10"))
                                .reason("Quantity discount")
                                .reasonCode("95"))
                        .price(p -> p.netPrice(new BigDecimal("12")))
                        .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                        .item(i -> i.name("Sensor module SM-100")))
                .invoiceLine(line -> line
                        .identifier("2")
                        .quantity(new BigDecimal("5"), "H87")
                        .allowance(a -> a
                                .amount(new BigDecimal("25"))
                                .reason("Damaged packaging")
                                .reasonCode("64"))
                        .price(p -> p.netPrice(new BigDecimal("200")))
                        .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("7")))
                        .item(i -> i.name("Printed documentation set")));

        invoice.derive(Totals.STANDARD);

        SemanticDocument document = invoice.document();

        assertArrayEquals(Examples.canonical("allowances"),
                EsjWriter.canonical().toBytes(document));
        assertEquals(2, En16931.view(document).invoiceLines().size());
        assertEquals("Sensor module SM-100",
                En16931.view(document).invoiceLines().get(0).item().name());
    }

    /**
     * The shorter form of that snippet, the editor path {@code docs/getting-started.md}
     * links to below the domain API: an invoice deliberately incomplete — no totals, no VAT
     * breakdown — so that the validator is the answer to "is it finished", validated and
     * written out both ways.
     */
    @Test
    void createValidateAndWriteAnInvoice() throws IOException {
        Path file = directory.resolve("invoice.esj.json");
        Path canonicalFile = directory.resolve("invoice.canonical.esj.json");

        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceNumber("RE-2026-0211").issueDate(LocalDate.of(2026, 5, 12))
                .typeCode("380").currencyCode("EUR");
        invoice.seller().name("Example GmbH").vatIdentifier("DE123456789");
        invoice.buyer().name("Muster AG");
        invoice.invoiceLine(line -> line
                .identifier("1")
                .quantity(new BigDecimal("2"), "H87")
                .price(p -> p.netPrice(new BigDecimal("12")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                .item(i -> i.name("Sensor module SM-100")));
        invoice.derive(Totals.STANDARD);

        SemanticDocument document = invoice.document();
        ValidationResult result = StructuralValidator.validate(document, Registry.en16931());
        ValidationStatus status = result.status();

        Files.write(file, EsjWriter.pretty().toBytes(document));
        Files.write(canonicalFile, EsjWriter.canonical().toBytes(document));

        assertEquals(SemanticValue.of("RE-2026-0211"),
                document.value(SemanticPath.of("/BT-1")).orElseThrow());
        assertEquals(new BigDecimal("24"),
                document.value(SemanticPath.of("/BG-25/0/BT-131")).orElseThrow().asDecimal());
        assertEquals(new BigDecimal("28.56"),
                En16931.view(document).documentTotals().amountDueForPayment());
        assertEquals(1, En16931.view(document).invoiceLines().size());
        assertEquals(ValidationStatus.INVALID, status,
                "the invoice the page builds is deliberately incomplete");
        assertTrue(result.findings().stream().anyMatch(Finding::isError));
        assertEquals(document.values(),
                EsjReader.strict().read(Files.readAllBytes(file)).values());
        assertArrayEquals(EsjWriter.canonical().toBytes(document),
                Files.readAllBytes(canonicalFile));
    }

    @Test
    void amendADocumentThroughTheEditor() {
        SemanticDocument document = Examples.document("standard-invoice");

        // docs/java-api.md: Building an invoice
        InvoiceEditor amended = En16931.edit(document);
        amended.invoiceLines().remove(0);
        amended.buyerReference("KOST-4711");
        // an extension term has no generated setter in 0.1; the builder is the way to it
        amended.builder().put("/BG-DEX-09/0/BT-DEX-001", "retention");
        SemanticDocument changed = amended.document();

        assertEquals(3, En16931.view(document).invoiceLines().size());
        assertEquals(2, En16931.view(changed).invoiceLines().size());
        assertEquals("On-site installation",
                En16931.view(changed).invoiceLines().get(0).item().name());
        assertEquals(SemanticValue.of("retention"),
                changed.value(SemanticPath.of("/BG-DEX-09/0/BT-DEX-001")).orElseThrow());
    }

    @Test
    void readTheSameDocumentThroughTheTypedView() {
        SemanticDocument document = Examples.document("standard-invoice");

        // docs/java-api.md: Layers
        Invoice invoice = En16931.view(document);
        LocalDate issueDate = invoice.issueDate();
        String sellerName = invoice.seller().name();
        BigDecimal amountDue = invoice.documentTotals().amountDueForPayment();
        List<String> items = new ArrayList<>();
        for (InvoiceLine line : invoice.invoiceLines()) {
            items.add(line.item().name() + " " + line.netAmount());
        }

        assertEquals(LocalDate.of(2026, 2, 3), issueDate);
        assertEquals("Example GmbH", sellerName);
        assertEquals(new BigDecimal("2915.5"), amountDue);
        assertEquals(3, items.size());
        assertEquals("Sensor module SM-100 250", items.get(0));
    }

    @Test
    void replayADocumentThroughAHandler() {
        SemanticDocument document = Examples.document("minimal");

        // docs/java-api.md: Replaying a document
        List<String> events = new ArrayList<>();
        Replay.replay(document, new SemanticHandler() {

            @Override
            public void beginGroup(SemanticPath groupPath) {
                events.add("begin " + groupPath);
            }

            @Override
            public void value(SemanticPath path, SemanticValue value) {
                events.add("value " + path);
            }

            @Override
            public void endGroup(SemanticPath groupPath) {
                events.add("end " + groupPath);
            }
        });

        assertEquals("value /BT-1", events.get(0));
        assertEquals("begin /BG-2", events.get(4));
        assertEquals("end /BG-25/0", events.get(events.size() - 1));
        assertEquals(document.values().size(),
                events.stream().filter(event -> event.startsWith("value ")).count());
    }

    @Test
    void collectTheEventsBackIntoADocument() {
        SemanticDocument document = Examples.document("multiple-lines");

        // docs/java-api.md: Replaying a document
        DocumentCollector collector = new DocumentCollector();
        Replay.replay(document, collector);
        SemanticDocument copy = collector.document();

        assertEquals(document.values(), copy.values());
    }

    @Test
    void readTheReportTheDerivationHandsBack() {
        InvoiceEditor invoice = En16931.newInvoice();
        invoice.invoiceNumber("RE-2026-0211")
                .issueDate(LocalDate.of(2026, 5, 12))
                .typeCode("380")
                .currencyCode("EUR");
        invoice.invoiceLine(line -> line
                .identifier("1")
                .quantity(new BigDecimal("3"), "H87")
                .price(p -> p.netPrice(new BigDecimal("12.335")))
                .vat(v -> v.vatCategoryCode("S").vatRate(new BigDecimal("19")))
                .item(i -> i.name("Sensor module SM-100")));

        // docs/java-api.md: Deriving the totals
        DerivationReport report = invoice.derive(Totals.STANDARD);

        List<Derived> written = report.values();
        List<Derived> rounded = report.roundings();
        Optional<Derived> lineNet = report.at("/BG-25/0/BT-131");

        assertFalse(written.isEmpty());
        assertTrue(lineNet.isPresent());
        assertEquals(new BigDecimal("37.01"), lineNet.orElseThrow().value(),
                "three at 12.335 is 37.005, rounded half up to two decimals once");
        assertTrue(rounded.contains(lineNet.orElseThrow()),
                "the report says where it had to round");
        assertEquals(new BigDecimal("12.335"),
                En16931.view(invoice.document()).invoiceLines().get(0).price().netPrice(),
                "the unit price is of an unlimited type and is left at the scale it was given");
    }
}
