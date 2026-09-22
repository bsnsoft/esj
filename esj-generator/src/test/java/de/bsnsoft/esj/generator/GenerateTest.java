package de.bsnsoft.esj.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks the generator against the files it produced: the sources of {@code esj-typed}
 * and the model schema that are checked in are the ones the registry yields, and two runs
 * over the same registry produce the same bytes.
 *
 * <p>The paths are relative to this module, which is where the build runs the test from.
 */
class GenerateTest {

    private static final Path REPOSITORY = Path.of("..");
    private static final Path TYPED = REPOSITORY.resolve("esj-typed/src/main/java");
    private static final String PACKAGE_2017 = TypedSources.DEFAULT_PACKAGE;
    private static final String PACKAGE_2026 = "de.bsnsoft.esj.typed.v2026";
    private static final Path PACKAGE = TYPED.resolve(path(PACKAGE_2017));
    private static final Path PACKAGE_DIRECTORY_2026 = TYPED.resolve(path(PACKAGE_2026));
    private static final Path BUILD_PACKAGE = PACKAGE.resolve("build");
    private static final Path SCHEMA = REPOSITORY.resolve("schema/esj-en16931-2017.schema.json");
    private static final Path SCHEMA_2026 = REPOSITORY.resolve("schema/esj-en16931-2026.schema.json");
    private static final Path FORMAT_SCHEMA = REPOSITORY.resolve("schema/esj.schema.json");
    private static final Path EXTENSION = REPOSITORY.resolve("model/xrechnung/3.0.2.json");
    private static final Path DERIVABLE = REPOSITORY.resolve("model/derivable-terms.json");
    private static final Path PROFILES = REPOSITORY.resolve("model/profiles");
    private static final Path INVOICE = REPOSITORY.resolve("esj-invoice/src/main/java");
    private static final Path CODE_PACKAGE =
            INVOICE.resolve("de/bsnsoft/esj/invoice/code");
    private static final Path ENUM_FACTS = REPOSITORY.resolve("model/enums.json");
    private static final Path RULES = REPOSITORY.resolve("rules");
    private static final Path B2C = REPOSITORY.resolve("model/b2c/0.1.json");
    private static final Path OVERLAY = REPOSITORY.resolve("esj-b2c/src/main/java");
    private static final Path OVERLAY_PACKAGE =
            OVERLAY.resolve("de/bsnsoft/esj/b2c");

    @Test
    void theCheckedInSourcesAreTheOnesTheRegistryYields(@TempDir Path directory)
            throws IOException {
        Path typed = directory.resolve("typed");
        Path schema = directory.resolve(SCHEMA.getFileName());
        Path invoice = invoice(directory);
        Generate.generate(options(typed, schema, invoice));

        List<String> generated = fileNames(typed.resolve(path(PACKAGE_2017)));
        assertFalse(generated.isEmpty(), "the generator emitted nothing");
        for (String name : generated) {
            assertEquals(read(typed.resolve(path(PACKAGE_2017)).resolve(name)),
                    read(PACKAGE.resolve(name)),
                    name + " is out of date; regenerate it");
        }
        assertEquals(generated, generatedFileNames(PACKAGE),
                "the checked-in package holds exactly the generated files the registry yields");

        Path build = typed.resolve("de/bsnsoft/esj/typed/build");
        List<String> chains = fileNames(build);
        assertFalse(chains.isEmpty(), "the generator emitted no constrained builder");
        for (String name : chains) {
            assertEquals(read(build.resolve(name)), read(BUILD_PACKAGE.resolve(name)),
                    name + " is out of date; regenerate it");
        }
        assertEquals(chains, generatedFileNames(BUILD_PACKAGE),
                "the checked-in build package holds exactly the generated files the inputs yield");
        Path code = invoice.resolve("de/bsnsoft/esj/invoice/code");
        List<String> enums = fileNames(code);
        assertFalse(enums.isEmpty(), "the generator emitted no code list enum");
        for (String name : enums) {
            assertEquals(read(code.resolve(name)), read(CODE_PACKAGE.resolve(name)),
                    name + " is out of date; regenerate it");
        }
        assertEquals(enums, generatedFileNames(CODE_PACKAGE, JavaText.CODE_LIST_HEADER_MARKER),
                "the checked-in code package holds exactly the enums the snapshots yield");
        assertEquals(read(schema), read(SCHEMA), "the model schema is out of date; regenerate it");
    }

    @Test
    void twoRunsOverOneRegistryProduceTheSameBytes(@TempDir Path directory) throws IOException {
        Path first = directory.resolve("first");
        Path second = directory.resolve("second");
        Path firstSchema = first.resolve(SCHEMA.getFileName());
        Path secondSchema = second.resolve(SCHEMA.getFileName());
        Path firstInvoice = directory.resolve("first-invoice");
        Path secondInvoice = directory.resolve("second-invoice");
        Generate.generate(options(first, firstSchema, firstInvoice));
        Generate.generate(options(second, secondSchema, secondInvoice));

        Path firstPackage = first.resolve(path(PACKAGE_2017));
        Path secondPackage = second.resolve(path(PACKAGE_2017));
        assertEquals(fileNames(firstPackage), fileNames(secondPackage));
        for (String name : fileNames(firstPackage)) {
            assertEquals(read(firstPackage.resolve(name)), read(secondPackage.resolve(name)), name);
        }
        Path firstBuild = firstPackage.resolve("build");
        Path secondBuild = secondPackage.resolve("build");
        assertEquals(fileNames(firstBuild), fileNames(secondBuild));
        for (String name : fileNames(firstBuild)) {
            assertEquals(read(firstBuild.resolve(name)), read(secondBuild.resolve(name)), name);
        }
        Path firstCode = firstInvoice.resolve("de/bsnsoft/esj/invoice/code");
        Path secondCode = secondInvoice.resolve("de/bsnsoft/esj/invoice/code");
        assertEquals(fileNames(firstCode), fileNames(secondCode));
        for (String name : fileNames(firstCode)) {
            assertEquals(read(firstCode.resolve(name)), read(secondCode.resolve(name)), name);
        }
        assertEquals(read(firstSchema), read(secondSchema));
    }

    @Test
    void everyCodeListEnumCarriesTheHeaderNamingItsPackAndItsSnapshots(@TempDir Path directory)
            throws IOException {
        Path invoice = invoice(directory);
        Generate.generate(options(directory.resolve("typed"),
                directory.resolve(SCHEMA.getFileName()), invoice));
        Path emitted = invoice.resolve("de/bsnsoft/esj/invoice/code");

        List<String> names = fileNames(emitted);
        assertTrue(names.contains("Unit.java"), "the units of measure");
        assertTrue(names.contains("VatCategory.java"), "the VAT categories");
        for (String name : names) {
            List<String> lines = Files.readAllLines(emitted.resolve(name), StandardCharsets.UTF_8);
            assertEquals(JavaText.CODE_LIST_HEADER_MARKER, lines.get(0), name);
            assertTrue(lines.get(1).startsWith("// of the rule pack en16931 1.3.16: "), name);
            assertTrue(lines.get(1).contains(" of 2026-09-19"), name + " names the snapshot day");
            assertEquals("// Do not edit.", lines.get(2), name);
        }
        String unit = read(emitted.resolve("Unit.java"));
        assertTrue(unit.contains("PIECE(\"H87\", \"piece\", \"unece-rec20\")"),
                "a constant carries the code, the published name and the list it came from");
        assertTrue(unit.contains("PIECE_XPP(\"XPP\", \"Piece\", \"unece-rec21\")"),
                "the second code of a name keeps the name and is told apart by its code");
        assertTrue(read(emitted.resolve("VatCategory.java")).contains("STANDARD(\"S\","),
                "a constant the configuration names is spelled as it names it");
    }

    @Test
    void aConstantNameIsDerivedFromTheCodeOrTheNameAndNeverInvented() {
        assertEquals("PIECE", EnumSources.constantName("piece"));
        assertEquals("SEPA_CREDIT_TRANSFER", EnumSources.constantName("SEPA credit transfer"));
        assertEquals("MANUFACTURER_S_CONSUMER_DISCOUNT",
                EnumSources.constantName("Manufacturer\u2019s consumer discount"));
        assertEquals("NUMERO_D_ENTREPRISE", EnumSources.constantName("Num\u00e9ro d'entreprise"),
                "a letter with a diacritic is decomposed to its base letter");
        assertEquals("VATEX_EU_132_1A", EnumSources.constantName("VATEX-EU-132-1A"));
        assertEquals("EUR", EnumSources.constantName("EUR"));
        assertEquals("", EnumSources.constantName("   "));
        assertEquals("A_NAME_LONGER_THAN_THE_SIXTY_CHARACTERS_A_CONSTANT_NAME_MAY",
                EnumSources.constantName(
                        "a name longer than the sixty characters a constant name may carry"),
                "a name too long is cut at an underscore and never in a word");
    }

    @Test
    void everyGeneratedFileCarriesTheThreeLineHeader(@TempDir Path directory) throws IOException {
        Path typed = directory.resolve("typed");
        Generate.generate(options(typed, directory.resolve(SCHEMA.getFileName()),
                invoice(directory)));
        Path emitted = typed.resolve(path(PACKAGE_2017));
        List<Path> files = new ArrayList<>();
        for (String name : fileNames(emitted)) {
            files.add(emitted.resolve(name));
        }
        Path build = emitted.resolve("build");
        for (String name : fileNames(build)) {
            files.add(build.resolve(name));
        }
        for (Path file : files) {
            String name = file.getFileName().toString();
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals(JavaText.HEADER_MARKER, lines.get(0), name);
            assertEquals("// EN 16931-1:2017+A1:2019/AC:2020, registry version 0.1.",
                    lines.get(1), name);
            assertEquals("// Do not edit.", lines.get(2), name);
        }
    }

    @Test
    void aFileTheRegistryNoLongerYieldsIsRemoved(@TempDir Path directory) throws IOException {
        Path typed = directory.resolve("typed");
        Path emitted = typed.resolve(path(PACKAGE_2017));
        Files.createDirectories(emitted);
        Path stale = emitted.resolve("FormerGroup.java");
        Files.writeString(stale, JavaText.HEADER_MARKER + "\n// old\n// Do not edit.\n");
        Path handWritten = emitted.resolve("Totals.java");
        Files.writeString(handWritten, "package de.bsnsoft.esj.typed;\n");

        Generate.generate(options(typed, directory.resolve(SCHEMA.getFileName()),
                invoice(directory)));

        assertFalse(Files.exists(stale), "a stale generated file is removed");
        assertTrue(Files.exists(handWritten), "a file that is not generated is left alone");
    }

    @Test
    void anExtensionRegistryAddsItsGroupsToTheView(@TempDir Path directory) throws IOException {
        Path typed = directory.resolve("typed");
        Generate.generate(options(typed, directory.resolve(SCHEMA.getFileName()),
                invoice(directory), Optional.of(EXTENSION)));
        Path emitted = typed.resolve(path(PACKAGE_2017));
        List<String> names = fileNames(emitted);

        assertTrue(names.contains("SubInvoiceLine.java"), "the extension group BG-DEX-01");
        assertTrue(names.contains("SubInvoiceLineItem.java"),
                "a name that clashes with a core group is qualified by its parent");
        assertTrue(names.contains("ThirdPartyPayment.java"), "the extension group BG-DEX-09");
        assertTrue(read(emitted.resolve("InvoiceLine.java")).contains("subInvoiceLines()"),
                "the core group carries the accessor of the extension group");
        assertTrue(read(emitted.resolve("SubInvoiceLine.java")).contains("netAmount()"),
                "a core term an extension group reuses keeps its slug");
        assertTrue(read(directory.resolve(SCHEMA.getFileName())).contains("BG-DEX-01"),
                "the model schema names the paths of the extension");
    }

    @Test
    void aRecursiveExtensionGroupIsEmittedOnceAsATypeThatRefersToItself(@TempDir Path directory)
            throws IOException {
        Path typed = directory.resolve("typed");
        assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> Generate.generate(options(typed,
                        directory.resolve(SCHEMA.getFileName()), invoice(directory),
                        Optional.of(EXTENSION))),
                "a group that carries itself must not unroll the generator");
        Path emitted = typed.resolve(path(PACKAGE_2017));

        Registry registry = Registry.en16931().withExtension(Registry.xrechnungExtension());
        assertTrue(registry.children("BG-DEX-01").stream()
                        .anyMatch(term -> "BG-DEX-01".equals(term.id())),
                "BG-DEX-01 carries itself, which is what makes this test worth having");

        int groups = 1;
        for (Term term : registry.terms()) {
            if (term.isGroup()) {
                groups++;
            }
        }
        assertEquals(4 * groups + 1, fileNames(emitted).size(),
                "every group yields one view, one view implementation, one editor and one"
                        + " editor implementation, no group yields two, and the entry point"
                        + " of the edition is the one file that belongs to no group");

        assertTrue(read(emitted.resolve("SubInvoiceLine.java"))
                        .contains("List<SubInvoiceLine> subInvoiceLines();"),
                "the recursive group is a type reference in the view");
        assertTrue(read(emitted.resolve("SubInvoiceLineEditor.java"))
                        .contains("EditorList<SubInvoiceLineEditor> subInvoiceLines();"),
                "the recursive group is a type reference in the editor");
        assertTrue(read(emitted.resolve("SubInvoiceLineEdit.java"))
                        .contains("SubInvoiceLineEdit::new"),
                "the editor of the recursive group builds instances of itself");
        assertFalse(fileNames(emitted).contains("SubInvoiceLineSubInvoiceLine.java"),
                "the recursive group is never unrolled into a second type");
    }

    @Test
    void everyGroupYieldsAViewAnEditorAndOneImplementationOfEach(@TempDir Path directory)
            throws IOException {
        Path typed = directory.resolve("typed");
        Generate.generate(options(typed, directory.resolve(SCHEMA.getFileName()),
                invoice(directory)));
        Path emitted = typed.resolve(path(PACKAGE_2017));
        List<String> names = fileNames(emitted);

        for (String name : names) {
            if (name.equals("En16931.java")) {
                continue;
            }
            if (!name.endsWith("View.java") && !name.endsWith("Editor.java")
                    && !name.endsWith("Edit.java")) {
                String type = name.substring(0, name.length() - ".java".length());
                assertTrue(names.contains(type + "View.java"), type + " has a view implementation");
                assertTrue(names.contains(type + "Editor.java"), type + " has an editor");
                assertTrue(names.contains(type + "Edit.java"),
                        type + " has an editor implementation");
            }
        }
        assertTrue(names.contains("InvoiceEditor.java"), "the root of the document has an editor");
    }

    @Test
    void aSchemeListOfTheRegistryReachesTheJavadocAndNothingElse(@TempDir Path directory)
            throws IOException {
        Path typed = directory.resolve("typed");
        Generate.generate(options(typed, directory.resolve(SCHEMA.getFileName()),
                invoice(directory)));
        Path emitted = typed.resolve(path(PACKAGE_2017));

        String editor = read(emitted.resolve("SellerEditor.java"));
        assertTrue(editor.contains("semantic data type Identifier, scheme from the ISO 6523 ICD"),
                "the scheme list of BT-29 and BT-30 is stated in the editor");
        assertTrue(editor.contains("the identification scheme, a code of the ISO 6523 ICD list"),
                "the overload that takes a scheme names the list it comes from");
        assertTrue(read(emitted.resolve("Seller.java")).contains("scheme from the ISO 6523 ICD"),
                "the read view states it too");
        assertFalse(read(emitted.resolve("SellerEdit.java")).contains("ISO 6523"),
                "no implementation checks a code list: that is a business rule");
    }

    @Test
    void theGeneratorRefusesAnIncompleteCommandLine() {
        assertThrows(IllegalArgumentException.class,
                () -> Generate.Options.parse(new String[] {"--typed"}));
        assertThrows(IllegalArgumentException.class,
                () -> Generate.Options.parse(new String[] {"--typed", "a", "--schema", "b"}));
        assertThrows(IllegalArgumentException.class, () -> Generate.Options.parse(new String[] {
            "--typed", "a", "--schema", "b", "--format-schema", "c", "--derivable", "d",
            "--profiles", "e", "--other", "f"}));
    }

    @Test
    void theGeneratorReadsTheOptionsItDocuments() {
        Generate.Options options = Generate.Options.parse(new String[] {
            "--typed", "a", "--schema", "b", "--format-schema", "c", "--derivable", "d",
            "--profiles", "e", "--enums", "f", "--enum-facts", "g", "--rules", "h",
            "--extension", "i", "--edition", "2026", "--typed-package", "j.k",
            "--overlay", "l", "--overlay-prefix", "M"});
        assertEquals(Optional.of(Path.of("a")), options.typedDirectory());
        assertEquals("j.k", options.typedPackage());
        assertEquals(Path.of("b"), options.schemaFile());
        assertEquals(Path.of("c"), options.formatSchema());
        assertEquals("2026", options.edition());
        assertEquals(Optional.of(Path.of("d")), options.derivable());
        assertEquals(Optional.of(Path.of("e")), options.profiles());
        assertEquals(Optional.of(Path.of("f")), options.enumDirectory());
        assertEquals(Optional.of(Path.of("g")), options.enumFacts());
        assertEquals(Optional.of(Path.of("h")), options.rules());
        assertEquals(Optional.of(Path.of("i")), options.extension());
        assertEquals(Optional.of(Path.of("l")), options.overlayDirectory());
        assertEquals(Optional.of("M"), options.overlayPrefix());
    }

    @Test
    void anOverlayIsGeneratedFromTheRegistryTheExtensionOptionNames() {
        assertThrows(IllegalArgumentException.class, () -> Generate.Options.parse(new String[] {
            "--typed", "a", "--schema", "b", "--format-schema", "c", "--derivable", "d",
            "--profiles", "e", "--enums", "f", "--enum-facts", "g", "--rules", "h",
            "--overlay", "i", "--overlay-prefix", "K"}));
    }

    /**
     * The constrained builder and the code list enums are written for the default edition
     * alone, and each of the two groups of options is given whole or not at all.
     */
    @Test
    void theGeneratorRefusesOneHalfOfAnOptionGroup() {
        assertThrows(IllegalArgumentException.class, () -> Generate.Options.parse(new String[] {
            "--typed", "a", "--schema", "b", "--format-schema", "c", "--derivable", "d"}),
                "the constrained builder needs the profile overlays too");
        assertThrows(IllegalArgumentException.class, () -> Generate.Options.parse(new String[] {
            "--typed", "a", "--schema", "b", "--format-schema", "c", "--enums", "d",
            "--enum-facts", "e"}),
                "the code list enums need the rule directory too");
        assertThrows(IllegalArgumentException.class, () -> Generate.Options.parse(new String[] {
            "--schema", "b", "--format-schema", "c", "--derivable", "d", "--profiles", "e"}),
                "the constrained builder is written into the source root --typed names");
    }

    @Test
    void theConstrainedBuilderHasOneChainPerBusinessGroupAndOnePerProfileThatDiffers(
            @TempDir Path directory) throws IOException {
        Path typed = directory.resolve("typed");
        Generate.generate(options(typed, directory.resolve(SCHEMA.getFileName()),
                invoice(directory)));
        Path emitted = typed.resolve(path(PACKAGE_2017)).resolve("build");
        List<String> names = fileNames(emitted);

        int groups = 1;
        for (Term term : Registry.en16931().terms()) {
            if (term.isGroup()) {
                groups++;
            }
        }
        long chains = names.stream().filter(name -> name.endsWith("Steps.java")).count();
        assertEquals(groups, chains, "the base profile has one chain per group and one for the"
                + " document");
        for (String name : names) {
            if (name.endsWith("Steps.java") || name.endsWith("StepsXrechnung.java")) {
                String chain = read(emitted.resolve(name));
                assertTrue(chain.contains("public interface Start"), name + " has a first step");
                assertTrue(chain.contains("public interface Buildable"),
                        name + " has a terminal step");
                assertTrue(names.contains(name.replace("Steps", "Build")),
                        name + " has an implementation");
            }
        }

        String invoice = read(emitted.resolve("InvoiceSteps.java"));
        assertEquals(List.of("Start", "WithInvoiceNumber", "WithIssueDate", "WithTypeCode",
                "WithCurrencyCode", "WithSeller", "WithBuyer", "Buildable"), interfaces(invoice));
        assertEquals(List.of("Start", "WithInvoiceNumber", "WithIssueDate", "WithTypeCode",
                "WithCurrencyCode", "WithBuyerReference", "WithSeller", "WithBuyer",
                "WithPaymentInstructions", "Buildable"),
                interfaces(read(emitted.resolve("InvoiceStepsXrechnung.java"))),
                "the profile narrows BT-10 and BG-16 to mandatory, so both are steps");
        assertFalse(names.contains("DocumentTotalsStepsXrechnung.java"),
                "a group whose chain a profile does not change keeps the base profile's types");
    }

    @Test
    void aRecursiveExtensionGroupYieldsOneChainAndDoesNotUnrollTheBuilder(@TempDir Path directory)
            throws IOException {
        Path typed = directory.resolve("typed");
        assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> Generate.generate(options(typed,
                        directory.resolve(SCHEMA.getFileName()), invoice(directory),
                        Optional.of(EXTENSION))),
                "a group that carries itself must not unroll the generator");
        Path emitted = typed.resolve(path(PACKAGE_2017)).resolve("build");
        List<String> names = fileNames(emitted);

        assertTrue(names.contains("SubInvoiceLineSteps.java"), "the extension group BG-DEX-01");
        assertFalse(names.contains("SubInvoiceLineSubInvoiceLineSteps.java"),
                "the recursive group is never unrolled into a second chain");
        assertTrue(read(emitted.resolve("SubInvoiceLineSteps.java"))
                        .contains("Function<SubInvoiceLineSteps.Start,"),
                "the recursive group is a type reference in its own chain");
    }

    @Test
    void theCheckedInOverlayIsTheOneTheExtensionRegistryYields(@TempDir Path directory)
            throws IOException {
        Path overlay = overlay(directory, B2C, "B2c");
        Path emitted = overlay.resolve("de/bsnsoft/esj/b2c");

        List<String> generated = fileNames(emitted);
        assertFalse(generated.isEmpty(), "the generator emitted no overlay");
        for (String name : generated) {
            assertEquals(read(emitted.resolve(name)), read(OVERLAY_PACKAGE.resolve(name)),
                    name + " is out of date; regenerate it");
        }
        assertEquals(generated, generatedFileNames(OVERLAY_PACKAGE),
                "the checked-in overlay package holds exactly the files the registry yields");
    }

    @Test
    void twoRunsOverOneExtensionProduceTheSameOverlayBytes(@TempDir Path directory)
            throws IOException {
        Path first = overlay(directory.resolve("first"), B2C, "B2c")
                .resolve("de/bsnsoft/esj/b2c");
        Path second = overlay(directory.resolve("second"), B2C, "B2c")
                .resolve("de/bsnsoft/esj/b2c");

        assertEquals(fileNames(first), fileNames(second));
        for (String name : fileNames(first)) {
            assertEquals(read(first.resolve(name)), read(second.resolve(name)), name);
        }
    }

    @Test
    void anOverlayCarriesTheTermsOfItsExtensionAndNoAccessorOfTheCoreModel(@TempDir Path directory)
            throws IOException {
        Path emitted = overlay(directory, B2C, "B2c").resolve("de/bsnsoft/esj/b2c");

        assertEquals(List.of("B2c.java", "B2cInvoice.java", "B2cInvoiceEdit.java",
                        "B2cInvoiceEditor.java", "B2cInvoiceLine.java", "B2cInvoiceLineEdit.java",
                        "B2cInvoiceLineEditor.java", "B2cInvoiceLineView.java",
                        "B2cInvoiceView.java"),
                fileNames(emitted),
                "one entry point, and a view, an editor and one implementation of each for the"
                        + " root of the document and for the one core group the extension hangs"
                        + " terms under");

        String invoice = read(emitted.resolve("B2cInvoice.java"));
        assertTrue(invoice.contains("Optional<BigDecimal> displayedGrossTotal();"),
                "the term of the extension at the root of the document");
        assertTrue(invoice.contains("List<B2cInvoiceLine> lines();"),
                "the way down to the terms of the extension inside BG-25");
        assertFalse(invoice.contains("invoiceNumber"),
                "the overlay repeats no accessor of the core model");
        assertFalse(invoice.contains("seller"), "the overlay repeats no accessor of the core model");

        String line = read(emitted.resolve("B2cInvoiceLine.java"));
        for (String member : List.of("displayedGrossUnitPrice", "displayedGrossLineTotal",
                "displayedLineVatAmount")) {
            assertTrue(line.contains("Optional<BigDecimal> " + member + "();"), member);
        }
        assertFalse(line.contains("netAmount"), "BT-131 belongs to the typed view of the core"
                + " model, which the overlay leaves alone");
        assertFalse(line.contains("quantity"), "BT-129 belongs to the typed view of the core model");

        String editor = read(emitted.resolve("B2cInvoiceEditor.java"));
        assertTrue(editor.contains("B2cInvoiceEditor displayedGrossTotal(BigDecimal value);"));
        assertTrue(editor.contains("List<B2cInvoiceLineEditor> lines();"));
        assertTrue(editor.contains("B2cInvoiceEditor line(int index,"),
                "one instance of a repeatable core group is written in a block");
        assertFalse(editor.contains("EditorList"),
                "the overlay hands out no handle that appends or removes an instance of a core"
                        + " group");
        assertTrue(read(emitted.resolve("B2c.java")).contains("Registry.b2cExtension()"),
                "the entry point reaches the registry of its extension by the name its prefix"
                        + " yields");
    }

    @Test
    void theOverlayOfARecursiveExtensionIsEmittedOnceAndReachesEveryGroupOfIt(
            @TempDir Path directory) throws IOException {
        Path emitted = assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> overlay(directory, EXTENSION, "Xrechnung")
                        .resolve("de/bsnsoft/esj/xrechnung"),
                "a group that carries itself must not unroll the generator");
        List<String> names = fileNames(emitted);

        assertTrue(names.contains("XrechnungSubInvoiceLine.java"), "the extension group BG-DEX-01");
        assertTrue(names.contains("XrechnungThirdPartyPayment.java"),
                "the extension group BG-DEX-09");
        assertTrue(names.contains("XrechnungInvoiceLine.java"),
                "the core group BG-25 is emitted because the extension hangs a group under it");
        assertFalse(names.contains("XrechnungSeller.java"),
                "a core group with nothing of the extension below it gets no overlay type");
        assertFalse(names.contains("XrechnungSubInvoiceLineSubInvoiceLine.java"),
                "the recursive group is never unrolled into a second type");

        String sub = read(emitted.resolve("XrechnungSubInvoiceLine.java"));
        assertTrue(sub.contains("List<XrechnungSubInvoiceLine> subInvoiceLines();"),
                "the recursive group is a type reference in the overlay");
        assertFalse(sub.contains("netAmount"),
                "BT-131 is a core term the extension group reuses, and the overlay shows it not");
        assertTrue(read(emitted.resolve("XrechnungThirdPartyPayment.java"))
                        .contains("String type();"),
                "a mandatory business term of the extension is returned directly");
        assertTrue(read(emitted.resolve("XrechnungInvoice.java"))
                        .contains("List<XrechnungInvoiceLine> lines();"),
                "the way down to BG-25 reads the same in every overlay");
    }

    @Test
    void theOverlayRefusesAShapeItsRuntimeCannotCarry(@TempDir Path directory) throws IOException {
        Registry core = Registry.en16931();
        Path repeatable = registry(directory, "BT-X-001", "\"max\": \"n\", \"datatype\": \"Text\"");
        Path identifier = registry(directory, "BT-X-002", "\"max\": 1, \"datatype\": \"Identifier\"");

        assertEquals("the overlay has no way to carry the repeatable business term BT-X-001",
                assertThrows(IllegalStateException.class,
                        () -> emit(core, repeatable)).getMessage());
        assertTrue(assertThrows(IllegalStateException.class, () -> emit(core, identifier))
                        .getMessage()
                        .startsWith("the overlay has no way to carry BT-X-002, whose semantic data"
                                + " type Identifier has supplementary components"));
    }

    private static Map<String, String> emit(Registry core, Path extension) throws IOException {
        try (InputStream in = Files.newInputStream(extension)) {
            Registry loaded = Registry.load(in);
            return new OverlaySources(core.withExtension(loaded), loaded,
                    OverlaySources.packageOf("X"), "X", "// header\n").sources();
        }
    }

    /**
     * Writes a one-term extension registry for the shapes the overlay refuses, which no
     * registry of the repository carries.
     */
    private static Path registry(Path directory, String id, String facts) throws IOException {
        Path file = directory.resolve(id + ".json");
        Files.createDirectories(directory);
        Files.writeString(file, """
                {
                  "format": "EN16931-Semantic-JSON-registry",
                  "version": "0.1",
                  "model": "X",
                  "edition": "X 0.1",
                  "imports": [
                    {
                      "model": "EN16931-1",
                      "edition": "EN 16931-1:2017+A1:2019/AC:2020"
                    }
                  ],
                  "terms": [
                    {
                      "id": "%s",
                      "kind": "BT",
                      "name": "A term",
                      "slug": "aTerm",
                      "parent": null,
                      "path": ["%s"],
                      "depth": 0,
                      "min": 0,
                      %s,
                      "components": [],
                      "order": 1,
                      "description": "A term."
                    }
                  ]
                }
                """.formatted(id, id, facts), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Runs the generator in overlay mode and returns the source root it wrote into. The four
     * artefacts of the core model are named but never written: a run with an overlay
     * directory emits the overlay and nothing else.
     */
    private static Path overlay(Path directory, Path extension, String prefix) throws IOException {
        Path root = directory.resolve("overlay");
        Generate.generate(new Generate.Options(Optional.of(directory.resolve("typed")),
                PACKAGE_2017, directory.resolve("model.schema.json"), FORMAT_SCHEMA,
                Registry.DEFAULT_EDITION, Optional.of(DERIVABLE), Optional.of(PROFILES),
                Optional.of(invoice(directory)), Optional.of(ENUM_FACTS), Optional.of(RULES),
                Optional.of(extension), Optional.of(root), Optional.of(prefix)));
        assertFalse(Files.exists(directory.resolve("typed")),
                "a run that writes an overlay leaves the typed view of the core model alone");
        assertFalse(Files.exists(directory.resolve("model.schema.json")),
                "a run that writes an overlay leaves the model schema alone");
        return root;
    }

    private static List<String> interfaces(String source) {
        List<String> names = new ArrayList<>();
        for (String line : source.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("public interface ")) {
                String name = trimmed.substring("public interface ".length());
                names.add(name.split("[ {]")[0]);
            }
        }
        return names;
    }

    /**
     * A run that names no edition and no package writes the artefacts of the edition the
     * repository defaults to, into the package that edition's view lies in.
     */
    @Test
    void theEditionAndThePackageDefaultToTheOnesOfTheFirstEdition() {
        Generate.Options options = Generate.Options.parse(new String[] {
            "--schema", "b", "--format-schema", "c"});
        assertEquals(Optional.empty(), options.typedDirectory());
        assertEquals(Registry.DEFAULT_EDITION, options.edition());
        assertEquals(TypedSources.DEFAULT_PACKAGE, options.typedPackage());
    }

    /**
     * The checked-in model schema of the 2026 edition is the one that edition's registry
     * yields. The test is skipped where the build carries no 2026 registry: the files of
     * that edition are separable, and the Maven profile {@code without-edition-2026}
     * leaves them out.
     */
    @Test
    @EnabledIf("carriesEdition2026")
    void theCheckedInSchemaOfTheSecondEditionIsTheOneItsRegistryYields(@TempDir Path directory)
            throws IOException {
        Path schema = directory.resolve(SCHEMA_2026.getFileName());
        Generate.generate(editionOptions(Optional.empty(), PACKAGE_2017, schema, "2026"));

        assertEquals(read(SCHEMA_2026), read(schema),
                "the model schema of the 2026 edition is out of date; regenerate it");
        assertTrue(read(schema).contains("EN 16931-1:2026"), "it names its own edition");
        assertTrue(read(schema).contains("/BT-166"), "it names the term of the new type Time");
        assertFalse(read(SCHEMA).contains("/BT-166"),
                "the schema of the 2017 edition knows no term of that edition");
    }

    /**
     * The checked-in typed view of the 2026 edition is the one that edition's registry
     * yields, it lies in a package of its own, and it is a view of that edition alone: no
     * accessor of a term the 2026 model does not have, and none of the 2017 model missing.
     * Skipped where the build carries no 2026 registry, as the schema test above is.
     */
    @Test
    @EnabledIf("carriesEdition2026")
    void theCheckedInViewOfTheSecondEditionIsTheOneItsRegistryYields(@TempDir Path directory)
            throws IOException {
        Path typed = directory.resolve("typed");
        Generate.generate(editionOptions(Optional.of(typed), PACKAGE_2026,
                directory.resolve(SCHEMA_2026.getFileName()), "2026"));

        Path emitted = typed.resolve(path(PACKAGE_2026));
        List<String> generated = fileNames(emitted);
        assertFalse(generated.isEmpty(), "the generator emitted nothing");
        for (String name : generated) {
            assertEquals(read(emitted.resolve(name)),
                    read(PACKAGE_DIRECTORY_2026.resolve(name)),
                    name + " is out of date; regenerate it");
        }
        assertEquals(generated, generatedFileNames(PACKAGE_DIRECTORY_2026),
                "the checked-in package holds exactly the generated files the registry yields");
    }

    /**
     * The two views are two views. Each one names the groups of its own edition, each one
     * lies in its own package, and neither offers an accessor the other edition's model
     * does not have.
     */
    @Test
    @EnabledIf("carriesEdition2026")
    void neitherViewCarriesATermOfTheOtherEdition() throws IOException {
        assertTrue(Files.exists(PACKAGE_DIRECTORY_2026.resolve("PaymentTerm.java")),
                "BG-33 is a group of the 2026 edition");
        assertFalse(Files.exists(PACKAGE.resolve("PaymentTerm.java")),
                "and of no earlier one");
        assertTrue(read(PACKAGE_DIRECTORY_2026.resolve("Invoice.java")).contains("OffsetTime"),
                "BT-166 is a term of the new semantic data type Time");
        assertFalse(read(PACKAGE.resolve("Invoice.java")).contains("OffsetTime"),
                "which the 2017 model does not have");
        assertTrue(read(PACKAGE.resolve("Invoice.java")).contains("BT-20 Payment terms"),
                "BT-20 stands at the root of a 2017 document");
        assertFalse(read(PACKAGE_DIRECTORY_2026.resolve("Invoice.java")).contains("BT-20"),
                "and inside BG-33 in a 2026 one");
        assertTrue(read(PACKAGE_DIRECTORY_2026.resolve("PaymentTerm.java")).contains("BT-20"),
                "which is where the 2026 view carries it");
    }

    /**
     * The derivation policies are hand-written against the view of one edition, so the
     * hook that reaches them is emitted for that view and for no other. The arithmetic of
     * the standard is stated per edition, and a policy that computed a 2026 invoice by the
     * rules of 2017 would be wrong without saying so.
     */
    @Test
    @EnabledIf("carriesEdition2026")
    void onlyTheViewTheDerivationPoliciesWereWrittenForReachesThem() throws IOException {
        assertTrue(read(PACKAGE.resolve("InvoiceEditor.java")).contains("derive(Totals policy)"));
        assertFalse(read(PACKAGE_DIRECTORY_2026.resolve("InvoiceEditor.java")).contains("derive("));
    }

    static boolean carriesEdition2026() {
        return Registry.editions().contains("2026");
    }

    /**
     * A full run over the default edition: the typed view, the constrained builder and the
     * code list enums, which is what the {@code generate} profile of the build asks for.
     */
    private static Generate.Options options(Path typed, Path schema, Path invoice) {
        return options(typed, schema, invoice, Optional.empty());
    }

    private static Generate.Options options(Path typed, Path schema, Path invoice,
                                            Optional<Path> extension) {
        return new Generate.Options(Optional.of(typed), PACKAGE_2017, schema, FORMAT_SCHEMA,
                Registry.DEFAULT_EDITION, Optional.of(DERIVABLE), Optional.of(PROFILES),
                Optional.of(invoice), Optional.of(ENUM_FACTS), Optional.of(RULES), extension,
                Optional.empty(), Optional.empty());
    }

    /**
     * A run over one edition that writes the typed view and the model schema and nothing
     * else: no edition but the default one carries a constrained builder or a domain API.
     */
    private static Generate.Options editionOptions(Optional<Path> typed, String typedPackage,
                                                   Path schema, String edition) {
        return new Generate.Options(typed, typedPackage, schema, FORMAT_SCHEMA, edition,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static String path(String packageName) {
        return packageName.replace('.', '/');
    }

    private static List<String> fileNames(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> generatedFileNames(Path directory) throws IOException {
        return generatedFileNames(directory, JavaText.HEADER_MARKER);
    }

    private static List<String> generatedFileNames(Path directory, String marker)
            throws IOException {
        List<String> names = new ArrayList<>();
        for (String name : fileNames(directory)) {
            if (read(directory.resolve(name)).startsWith(marker)) {
                names.add(name);
            }
        }
        return names;
    }

    private static Path invoice(Path directory) {
        return directory.resolve("invoice");
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
