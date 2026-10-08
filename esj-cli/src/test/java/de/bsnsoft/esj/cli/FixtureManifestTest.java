package de.bsnsoft.esj.cli;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.TermKind;
import de.bsnsoft.esj.bindings.ReaderOptions;
import de.bsnsoft.esj.bindings.StreamingReader;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.json.ReadResult;
import de.bsnsoft.esj.model.Cardinality;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import de.bsnsoft.esj.rules.RuleDefinition;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RulePack;
import de.bsnsoft.esj.rules.RulePackSource;
import de.bsnsoft.esj.rules.RulePackSources;
import de.bsnsoft.esj.rules.RulePacks;
import de.bsnsoft.esj.rules.en16931.En16931Pack;
import de.bsnsoft.esj.upgrade.EditionUpgrade;
import de.bsnsoft.esj.upgrade.UpgradeOptions;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.validate.NotEvaluatedReason;
import de.bsnsoft.esj.validate.Severity;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.validate.ValidationResult;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The fixture manifest of {@code conformance/fixtures/}: a list of cases that is written in
 * no programming language, so that a second implementation of this format can be measured
 * against the same material as the first.
 *
 * <p>The manifest is not maintained by hand and is not asserted against by hand either. This
 * test builds it from the implementation — reading every document, canonicalizing it,
 * digesting it, validating it, and evaluating the rule pack over every mutation of the
 * corpus — and then compares the result with the checked-in file byte for byte. A defect in
 * the implementation therefore shows up as a difference in the manifest, and the manifest
 * cannot record an expectation the implementation does not meet: it is correct by
 * construction or the build is red.
 *
 * <p>Run with {@code -Desj.fixtures.rewrite=true} the same run writes the files instead of
 * comparing them, which is how the manifest is regenerated after the corpus, the registry or
 * a finding code has changed. That path writes into the working tree, relative to this
 * module's directory, and is the one place in this module that knows a path outside it.
 *
 * <p>Some parts of the manifest are not derived from anything. The candidate strings of the
 * grammars, the bounds of the {@code bounds} section and the sets of registry files of
 * {@code registryChecks} are written down below, and what the manifest records about them —
 * that a candidate is accepted or rejected and with which finding code, what a validation
 * answers under those bounds, whether a loader takes those files — is measured here; a case
 * whose answer changes changes the manifest. The quotients of the arithmetic section are
 * written in their pack, {@code conformance/fixtures/arithmetic/pack.json}, and this test
 * holds the reference implementation to every one of them before it records what the pack
 * reports.
 *
 * <p>Every validation is recorded whole, as an outcome: the status, the layers not
 * evaluated with their reasons, and every finding with its path, code, subject and severity,
 * in the order this implementation reports them. A binding is compared with it in that order
 * where the specification fixes one, and as a set elsewhere ({@code run.py}).
 */
class FixtureManifestTest {

    /** Where the manifest lives, on the class path and in the tree. */
    private static final String DIRECTORY = "conformance/fixtures/";

    /** The same directory in the working tree, for the rewrite run. */
    private static final Path TREE = Path.of("..", "conformance", "fixtures");

    /** The pages of the two bindings, which state what the manifest holds. */
    private static final List<Path> BINDING_PAGES = List.of(
            Path.of("..", "bindings", "typescript", "README.md"),
            Path.of("..", "bindings", "csharp", "README.md"));

    /** The name of the manifest itself. */
    private static final String MANIFEST = "manifest.json";

    /** The manifest part that carries the fixtures of the later edition. */
    private static final String LATER_EDITION_PART = "manifest-en16931-2026.json";

    /** The edition that part belongs to, as {@code model/en16931/} names it. */
    private static final String LATER_EDITION = "2026";

    /** The file that carries the rule cases, which are too many to read beside the rest. */
    private static final String CASES = "cases-en16931-1.3.16.json";

    /** The schema of the manifest, of its parts and of the rule case file. */
    private static final String SCHEMA = "manifest.schema.json";

    /**
     * The cases the rule pack of the later edition is weighed by, as that pack's evidence
     * records them: a document of the corpus written up to the edition, and the changes
     * that make a rule hold and then speak.
     */
    private static final String LATER_EDITION_CASES = "conformance/rules-2026/cases/cases.json";

    /** The options the documents of those cases are written up to the later edition with. */
    private static final UpgradeOptions UPGRADE = UpgradeOptions.defaults()
            .withExtensions(List.of(Registry.xrechnungExtension()));

    /** The format identifier of a manifest file. */
    private static final String FORMAT = "EN16931-Semantic-JSON-Fixtures";

    /**
     * The version of the fixture contract, raised when a member changes meaning. Version 2
     * records the whole answer of a validation — status, the layers not evaluated, every
     * finding with its subject and its severity — where version 1 recorded the errors.
     */
    private static final String CONTRACT_VERSION = "2";

    /** The negative fixtures of layer L1 that pin what a reader reports, one defect each. */
    private static final String READER_FIXTURES = DIRECTORY + "reader";

    /** The negative fixtures of the model layers that pin the full list of their findings. */
    private static final String MODEL_FIXTURES = DIRECTORY + "model";

    /** Documents that are read, whatever their verdict, and pin what a validator says of them. */
    private static final String DOCUMENT_FIXTURES = DIRECTORY + "documents";

    /** The two documents of the specification, appendix B, as files with their twins. */
    private static final String ANNEX_B = DIRECTORY + "annex-b";

    /** Documents read under the bounds a case of the {@code bounds} section names. */
    private static final String BOUND_FIXTURES = DIRECTORY + "bounds";

    /** Registries written for the manifest: the ones a loader refuses, and two it takes. */
    private static final String TEST_REGISTRIES = DIRECTORY + "registries";

    /** Whether this run writes the files instead of comparing them. */
    private static final boolean REWRITE = Boolean.getBoolean("esj.fixtures.rewrite");

    /**
     * The registry every core fixture is measured against: the core edition with the terms of
     * every extension registry of the repository. A fixture that carries an extension term is
     * measured rather than reported as not checked (specification, section 5.6), so a binding
     * that left one of those registries out fails the manifest instead of passing it silently.
     */
    private static final Registry COMBINED = Registry.en16931WithXrechnung()
            .withExtension(Registry.b2cExtension());

    /**
     * The two layers a document the reader accepted still has to pass. Layer L1 is the
     * reader's own answer and is decided by the bytes, so it is not asked here.
     */
    private static final Set<ValidationLayer> MODEL_AND_CARDINALITY =
            EnumSet.of(ValidationLayer.L2, ValidationLayer.L3);

    /** The document the value grammar candidates are substituted into. */
    private static final String GRAMMAR_BASE = "examples/minimal.esj.json";

    /** The registry of the default edition, as the repository checks it in. */
    private static final String CORE_REGISTRY =
            "model/en16931/" + Registry.defaultEditionKey() + ".json";

    /** The extension registries of the repository, in the order a binding loads them. */
    private static final List<String> EXTENSION_REGISTRIES =
            List.of("model/xrechnung/3.0.2.json", "model/b2c/0.1.json");

    /** The registry of the later edition, under the key the repository files it by. */
    private static final String LATER_EDITION_REGISTRY =
            "model/en16931/" + LATER_EDITION + ".json";

    /** The rule pack, compiled once: it is evaluated over every mutation of the corpus. */
    private static final RuleEngine ENGINE = En16931Pack.engine(COMBINED);

    /**
     * The pack of the rule language whose rules pin its arithmetic, as the repository files it.
     * Its quotients are written by hand in the pack, beside a note on what each one pins.
     */
    private static final String ARITHMETIC_PACK = DIRECTORY + "arithmetic/pack.json";

    /**
     * The rules of that pack that report nothing: the two about a division by zero, whose
     * quotient is absent. Every other rule of it asserts that a quotient is not the value
     * worked out by hand for it, and reports exactly where this implementation computes that
     * value.
     */
    private static final Set<String> ARITHMETIC_SILENT = Set.of("DIV-BY-ZERO-EQ", "DIV-BY-ZERO-NE");

    private final EsjReader reader = EsjReader.strict();

    /**
     * The registries a binding carries when it answers a {@code validate} request: one per
     * edition, the default one combined with the extension registries of the repository. The
     * validator uses the one that describes the edition a document names, and none where none
     * does (specification, section 9.2).
     */
    private static final List<Registry> CARRIED = carried();

    private static List<Registry> carried() {
        List<Registry> registries = new ArrayList<>(List.of(COMBINED));
        if (theLaterEditionIsThere()) {
            registries.add(Registry.forEdition(LATER_EDITION));
        }
        return List.copyOf(registries);
    }

    /**
     * The fixtures of {@code conformance/fixtures/model/} that are validated with registries of
     * their own rather than with the ones a binding carries: the bound on a maximum cardinality
     * greater than one is reached by no published edition, so a registry written for the
     * manifest declares one.
     */
    private static final Map<String, List<String>> VALIDATED_WITH = Map.of(
            MODEL_FIXTURES + "/max-cardinality.esj.json",
            List.of(TEST_REGISTRIES + "/cardinality.json"));

    /**
     * A value grammar of the specification, section 6, with the term it is measured at and
     * the candidates that decide it.
     *
     * @param datatype the registry datatype, as the registry spells it
     * @param code     the finding code a content outside the grammar draws
     * @param path     a path of {@link #GRAMMAR_BASE} whose term carries that datatype
     * @param accept   contents inside the grammar, as they are written in {@code values}
     * @param reject   contents outside it, as they are written in {@code values}
     */
    private record Grammar(SemanticType datatype,
                           String code,
                           String path,
                           List<SemanticValue> accept,
                           List<SemanticValue> reject) {
    }

    /** Returns a value written as a JSON string in a document. */
    private static SemanticValue plain(String content) {
        return SemanticValue.of(content);
    }

    /** Returns an attachment, which is always written in the object form (section 6.7). */
    private static SemanticValue attachment(String base64) {
        return new SemanticValue(base64, null, null, "application/pdf", "note.pdf");
    }

    /** A decimal of 65 characters, one past the bound of the specification, section 6.4. */
    private static String tooLongDecimal() {
        return "1".repeat(65);
    }

    /** A decimal of 64 characters, which is the longest the grammar admits. */
    private static String longestDecimal() {
        return "1".repeat(64);
    }

    /**
     * The value grammars, with the candidates that pin them down. Every rule the prose of
     * the specification, section 6.4, 6.5 and 6.7 states as an example is here, so that an
     * implementation that reads the prose and one that reads this table agree.
     */
    private static List<Grammar> grammars() {
        List<Grammar> grammars = new ArrayList<>();
        grammars.add(new Grammar(SemanticType.AMOUNT, "ESJ-L2-DECIMAL", "/BG-22/BT-106",
                List.of(plain("0"), plain("100"), plain("-100"), plain("0.5"),
                        plain("42.015"), plain("-0.01"), plain(longestDecimal()),
                        plain("-" + "1".repeat(63)), plain("0." + "1".repeat(62)),
                        plain("1".repeat(32) + "." + "1".repeat(31))),
                List.of(plain("100.00"), plain("007"), plain("1E2"), plain("1e-3"),
                        plain("+1"), plain("100."), plain(".5"), plain("-0"),
                        plain("-0.00"), plain("1 000"), plain("1,5"), plain("0x10"),
                        plain(tooLongDecimal()), plain("-" + "1".repeat(64)),
                        plain("0." + "1".repeat(63)), plain("100\n"), plain(" 100"),
                        plain("\uff11"), plain("1.10"), plain("00.5"))));
        grammars.add(new Grammar(SemanticType.QUANTITY, "ESJ-L2-DECIMAL", "/BG-25/0/BT-129",
                List.of(plain("1"), plain("-2.5"), plain("0.0001"), plain(longestDecimal())),
                List.of(plain("1.0"), plain("01"), plain("1e3"), plain(tooLongDecimal()))));
        grammars.add(new Grammar(SemanticType.UNIT_PRICE_AMOUNT, "ESJ-L2-DECIMAL",
                "/BG-25/0/BG-29/BT-146",
                List.of(plain("100"), plain("0.123456"), plain(longestDecimal())),
                List.of(plain("100.000000"), plain("-0"), plain(tooLongDecimal()))));
        grammars.add(new Grammar(SemanticType.PERCENTAGE, "ESJ-L2-DECIMAL", "/BG-23/0/BT-119",
                List.of(plain("19"), plain("7.5"), plain("0")),
                List.of(plain("19.0"), plain("19%"), plain("-0.0"))));
        grammars.add(new Grammar(SemanticType.DATE, "ESJ-L2-DATE", "/BT-2",
                List.of(plain("2026-01-15"), plain("2024-02-29"), plain("1000-01-01"),
                        plain("9999-12-31"), plain("2000-02-29"), plain("1600-02-29"),
                        plain("2026-04-30"), plain("2026-12-31")),
                List.of(plain("2026-02-30"), plain("2023-02-29"), plain("0999-12-31"),
                        plain("2026-1-5"), plain("20260115"), plain("2026-01-15Z"),
                        plain("2026-13-01"), plain("2026-01-32"), plain("2100-02-29"),
                        plain("1900-02-29"), plain("2026-04-31"), plain("2026-00-10"),
                        plain("2026-01-00"), plain("0000-01-01"), plain("+2026-01-15"),
                        plain("2026-01-15\n"), plain("2026-01-15T00:00:00"),
                        plain("\uff12026-01-15"))));
        grammars.add(new Grammar(SemanticType.BINARY_OBJECT, "ESJ-L2-BASE64",
                "/BG-24/0/BT-125",
                List.of(attachment("QUJDRQ=="), attachment("QUJD"), attachment("QQ=="),
                        attachment("QUE="), attachment("AAAA"), attachment("+/8="),
                        attachment("+/+/")),
                List.of(attachment("QUJDRR=="), attachment("QUJDRQ"), attachment("QUJD RQ=="),
                        attachment("QUJDR-=="), attachment("QUJDRQ==="), attachment("QUJ="),
                        attachment("Q==="), attachment("===="), attachment("QQ==QUJD"),
                        attachment("QUJD\n"), attachment("Qa=="), attachment("QW=="),
                        attachment("QUC="), attachment("+/=="), attachment("QUJD_-8="),
                        attachment("QQ"), attachment("Q"))));
        return List.copyOf(grammars);
    }

    /**
     * A grammar of the envelope, measured in the place a document writes it: the edition
     * grammar of the specification, section 4.4 as the value of {@code semanticModel}, and the
     * owner-token grammar of section 4.6 as the name of the one member of {@code extensions}.
     *
     * @param grammar the name of the grammar, as the manifest writes it
     * @param code    the finding code a candidate outside the grammar draws at layer L1
     * @param member  the envelope member the candidate is written into
     * @param accept  candidates inside the grammar
     * @param reject  candidates outside it
     */
    private record EnvelopeGrammar(String grammar, String code, String member,
                                   List<String> accept, List<String> reject) {
    }

    /**
     * The two envelope grammars, with the candidates that pin them down: every rule of the
     * prose of sections 4.4 and 4.6 that a candidate can show. An edition the grammar admits
     * and no registry describes is accepted at layer L1 and reported at layer L2 as unknown,
     * which is not what the table measures: it holds layer L1 alone.
     */
    private static List<EnvelopeGrammar> envelopeGrammars() {
        return List.of(
                new EnvelopeGrammar("edition", "ESJ-L1-ENVELOPE-VALUE", "semanticModel",
                        List.of("EN16931-1:2017+A1:2019/AC:2020", "EN16931-1:2099",
                                "FOO-BAR:2099", "A:0999", "lower-case:2017", "X1-Y2-Z3:2017",
                                "X:2017+A1:2019+A2:2020/AC:2021", "X:2017/AC1:2020",
                                "X:2017/AC:2020+A2:2021/AC2:2022", "X:2017+A10:2019"),
                        List.of("EN16931-1", "EN16931-1:17", "EN16931-1:20170",
                                "EN 16931-1:2017", " EN16931-1:2017", "EN16931-1:2017 ",
                                "EN16931-1:2017+a1:2019", "EN16931-1:2017/ac:2020",
                                "EN16931-1:2017+A:2019", "EN16931-1:2017/AC", "-EN:2017",
                                "EN-:2017", "EN--1:2017", ":2017", "EN16931-1:2017+",
                                "EN16931-1:2017/AC:2020/AC:2021", "EN16931_1:2017",
                                "EN16931-1:2017\n", "\u00c9N:2017", "EN16931-1:2017+A1:2019/",
                                "")),
                new EnvelopeGrammar("owner-token", "ESJ-L1-OWNER-TOKEN", "extensions",
                        List.of("de.example.vendor", "example-vendor_2", "bt-example", "a", "Z9",
                                "1", "a.b", "a_b", "a-b", "BTX", "bT-1", "x".repeat(128)),
                        List.of("urn:example:v/2", "de.ex\u00e4mple.vendor", "BT-1",
                                "BG-example", "BT-", ".a", "a.", "-a", "a-", "_a", "a_", "a b",
                                "x".repeat(129), "a/b", "", "de.example.vendor\n", "a:b")));
    }

    /**
     * The value grammar of the later edition, which is the one type that edition adds. It
     * belongs to the part file, because a distribution without that edition carries neither
     * the registry that gives a term the type nor a document to measure it in.
     */
    private static Grammar laterEditionGrammar() {
        return new Grammar(SemanticType.TIME, "ESJ-L2-TIME", "/BT-166",
                List.of(plain("09:15:00Z"), plain("00:00:00Z"), plain("23:59:59+14:00"),
                        plain("12:00:00-05:30"), plain("12:00:00+01:00"),
                        plain("12:00:00-14:00"), plain("12:00:00+13:59"),
                        plain("12:00:00-00:30"), plain("12:00:00+00:01")),
                List.of(plain("09:15:00"), plain("09:15:00+00:00"), plain("09:15:00-00:00"),
                        plain("9:15:00Z"), plain("09:15Z"), plain("24:00:00Z"),
                        plain("23:59:60Z"), plain("09:15:00.5Z"), plain("09:15:00z"),
                        plain("09:15:00+15:00"), plain("12:00:00+14:01"),
                        plain("12:00:00-14:01"), plain("12:00:00+1:00"),
                        plain("12:00:00+0100"), plain("09:15:00,5Z"), plain("12:60:00Z"),
                        plain("12:00:00+01:60"), plain("12:00:00Z\n"), plain("T12:00:00Z")));
    }

    // ---------------------------------------------------------------- the tests

    @Test
    void theManifestIsWhatThisImplementationAnswers() {
        expect(MANIFEST, manifest().bytes());
    }

    @Test
    void theRuleCasesAreWhatTheRulePackAnswers() {
        expect(CASES, cases().bytes());
    }

    @Test
    @EnabledIf("theLaterEditionIsThere")
    void thePartOfTheLaterEditionIsWhatThisImplementationAnswers() {
        expect(LATER_EDITION_PART, laterEditionPart().bytes());
    }

    @Test
    @EnabledIf("theLaterEditionPackIsThere")
    void theRuleCasesOfTheLaterEditionAreWhatItsPackAnswers() {
        RulePackSource source = laterEditionPack().orElseThrow();
        expect(laterEditionCasesFile(source), laterEditionCases(source).bytes());
    }

    /**
     * The part of an edition this distribution does not carry is not on the class path
     * either. Both are decided by {@code model/en16931/2026.paths}, and a tree that keeps
     * one without the other would leave a binding with a fixture it cannot run.
     */
    @Test
    void thePartOfAnEditionIsThereExactlyWhenTheEditionIs() {
        assertEquals(theLaterEditionIsThere(), resource(DIRECTORY + LATER_EDITION_PART) != null,
                LATER_EDITION_PART + " is present exactly when the registry of that edition is");
    }

    /**
     * Every fixture file the manifest names is in the repository. The manifest is a contract
     * for an implementation that has the tree and nothing else, so a name in it that no file
     * answers is the one defect the byte comparison above cannot find.
     */
    @Test
    void everyFileTheManifestNamesIsThere() {
        for (String file : referencedFiles()) {
            assertNotNull(resource(file), file + " is named by the manifest and is not there");
        }
    }

    /**
     * Every file of the manifest this build carries has the shape the schema documents,
     * which is the shape the bindings read. The comparisons above hold the files to this
     * implementation; this holds them to the schema. The part of the later edition and the
     * rule case file of its pack are checked where the build carries them.
     */
    @Test
    void everyFileOfTheManifestHasTheShapeOfTheSchema() {
        Schema schema = schema();
        List<String> files = new ArrayList<>(List.of(MANIFEST, CASES));
        if (theLaterEditionIsThere()) {
            files.add(LATER_EDITION_PART);
        }
        laterEditionPack().ifPresent(source -> files.add(laterEditionCasesFile(source)));
        for (String file : files) {
            assertEquals(List.of(),
                    schema.validate(Fixtures.text(DIRECTORY + file), InputFormat.JSON),
                    DIRECTORY + file + " has the shape of " + DIRECTORY + SCHEMA);
        }
    }

    /**
     * The schema tells the two kinds of file apart: a manifest names its part, a rule case
     * file its pack and no part, and either written the other way fails it.
     */
    @Test
    void theSchemaTellsAManifestFromARuleCaseFile() {
        Schema schema = schema();
        String manifest = Fixtures.text(DIRECTORY + MANIFEST);
        String cases = Fixtures.text(DIRECTORY + CASES);

        assertFalse(schema.validate(manifest.replaceFirst("\n  \"part\": \"core\",", ""),
                InputFormat.JSON).isEmpty(), "a manifest names its part");
        assertFalse(schema.validate(cases.replaceFirst("\n  \"pack\": ",
                        "\n  \"part\": \"core\",\n  \"pack\": "),
                InputFormat.JSON).isEmpty(), "a rule case file names none");
        assertFalse(schema.validate(cases.replaceFirst("\"" + FORMAT + "-Rules\"",
                        "\"" + FORMAT + "\""),
                InputFormat.JSON).isEmpty(), "and says what it is");
    }

    /** Returns the schema of the manifest files, as the repository carries it. */
    private static Schema schema() {
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(Fixtures.text(DIRECTORY + SCHEMA), InputFormat.JSON);
    }

    /**
     * What a binding's own page says the manifest holds is what the manifest holds.
     *
     * <p>A page that counts the manifest goes stale the moment a fixture is added, and the
     * suite that would notice is the binding's own — which the Java reactor does not run.
     * So the counts are pinned here, where every change to the corpus passes: a phase that
     * adds a document and leaves the sentence behind fails the build that made the change
     * instead of the build somebody runs afterwards.
     *
     * <p>The numbers are read from the checked-in files of the whole manifest, the part of
     * the later edition included, because that is what the pages count: each of them says
     * in the same sentence that two documents and one rejected document come from the part a
     * build without that edition leaves out. Held against one such build the pages would
     * have to carry two numbers and would state neither of them plainly — so a build that
     * carries only the default edition does not hold them at all.
     */
    @Test
    @EnabledIf("theBindingPagesCountEveryEdition")
    void theBindingPagesCountTheManifestThisBuildRuns() {
        int documents = 0;
        int invalid = 0;
        for (String file : List.of(MANIFEST, LATER_EDITION_PART)) {
            Path manifest = TREE.resolve(file);
            if (!Files.exists(manifest)) {
                continue;
            }
            documents += rows(manifest, "documents");
            invalid += rows(manifest, "invalid");
        }
        for (Path page : BINDING_PAGES) {
            String text = readTree(page);
            assertCount(page, text, " documents a reader reads", documents);
            assertCount(page, text, " documents that have to be rejected", invalid);
        }
    }

    /**
     * Holds one phrase of one page to the number the manifest puts in front of it.
     *
     * <p>A page is free not to count the manifest at all — the C# page does not — but a
     * page that carries the phrase with another number before it is wrong, and that is
     * what is asserted.
     */
    private static void assertCount(Path page, String text, String phrase, int expected) {
        int at = text.indexOf(phrase);
        if (at < 0) {
            return;
        }
        int start = at;
        while (start > 0 && Character.isDigit(text.charAt(start - 1))) {
            start--;
        }
        assertEquals(String.valueOf(expected), text.substring(start, at),
                page + " counts the manifest: it holds " + expected + phrase);
    }

    /** Counts the elements of one top-level array of a manifest file. */
    private static int rows(Path manifest, String field) {
        try (JsonParser parser = new JsonFactory().createParser(manifest.toFile())) {
            int depth = 0;
            while (parser.nextToken() != null) {
                JsonToken token = parser.currentToken();
                if (token == JsonToken.START_ARRAY && depth == 1
                        && field.equals(parser.currentName())) {
                    int rows = 0;
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        parser.skipChildren();
                        rows++;
                    }
                    return rows;
                }
                if (token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT) {
                    depth++;
                } else if (token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) {
                    depth--;
                }
            }
            return 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a file of the repository, relative to this module's directory. */
    private static String readTree(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean theBindingPagesAreThere() {
        return Files.exists(TREE.resolve(MANIFEST))
                && BINDING_PAGES.stream().allMatch(Files::exists);
    }

    /** The pages count the whole manifest, so they are held to it only where the whole of it is checked in. */
    static boolean theBindingPagesCountEveryEdition() {
        return theBindingPagesAreThere() && Files.exists(TREE.resolve(LATER_EDITION_PART));
    }

    // ------------------------------------------------------------- the manifest

    /** Builds the manifest of the default edition. */
    private Manifest.Object manifest() {
        Manifest.Object manifest = Manifest.object()
                .put("format", FORMAT)
                .put("version", CONTRACT_VERSION)
                .put("part", "core")
                .put("specification", "SPEC.md")
                .put("parts", Manifest.of(List.of(LATER_EDITION_PART)))
                .put("registries", coreRegistries());

        Manifest.Array documents = Manifest.array();
        for (String file : coreDocuments()) {
            documents.add(document(file));
        }
        manifest.put("documents", documents);

        Manifest.Array invalid = Manifest.array();
        for (String file : invalidFixtures(false)) {
            invalid.add(invalid(file));
        }
        manifest.put("invalid", invalid);

        manifest.put("bounds", boundSection());
        manifest.put("registryChecks", registryCheckSection());
        manifest.put("canonicalOrder", canonicalOrder());

        Manifest.Array tables = Manifest.array();
        for (Grammar grammar : grammars()) {
            tables.add(grammar(grammar, GRAMMAR_BASE, COMBINED));
        }
        for (EnvelopeGrammar grammar : envelopeGrammars()) {
            tables.add(envelopeGrammar(grammar, GRAMMAR_BASE));
        }
        manifest.put("grammars", tables);

        manifest.put("rules", Manifest.object()
                .put("pack", En16931Pack.PACK_ID + "/" + En16931Pack.version())
                .put("directory", "rules/" + En16931Pack.PACK_ID + "/" + En16931Pack.version())
                .put("casesFile", CASES)
                .put("cases", Oracle.all().size()));

        manifest.put("arithmetic", arithmetic());
        return manifest;
    }

    /** Builds the manifest part of the later edition. */
    private Manifest.Object laterEditionPart() {
        Registry registry = Registry.forEdition(LATER_EDITION);
        Manifest.Object part = Manifest.object()
                .put("format", FORMAT)
                .put("version", CONTRACT_VERSION)
                .put("part", "en16931-" + LATER_EDITION)
                .put("semanticModel", registry.semanticModel())
                .put("whenEditionAbsent", "ESJ-L2-EDITION-UNKNOWN")
                .put("registries", Manifest.array()
                        .add(registry(LATER_EDITION_REGISTRY, registry)));

        Manifest.Array documents = Manifest.array();
        for (String file : laterEditionDocuments()) {
            documents.add(document(file));
        }
        part.put("documents", documents);

        Manifest.Array invalid = Manifest.array();
        for (String file : invalidFixtures(true)) {
            invalid.add(invalid(file));
        }
        part.put("invalid", invalid);

        Manifest.Array tables = Manifest.array();
        tables.add(grammar(laterEditionGrammar(), laterEditionDocuments().get(0), registry));
        part.put("grammars", tables);

        laterEditionPack().ifPresent(source -> part.put("rules", Manifest.object()
                .put("pack", source.pack().id() + "/" + source.pack().version())
                .put("directory", "rules/" + source.pack().id() + "/" + source.pack().version())
                .put("casesFile", laterEditionCasesFile(source))
                .put("cases", 2L * ((List<?>) member(laterEditionCaseSource(), "cases")).size())));
        return part;
    }

    /** Builds the rule cases: every mutation of the corpus, in the form a binding reads. */
    private Manifest.Object cases() {
        Manifest.Object file = Manifest.object()
                .put("format", FORMAT + "-Rules")
                .put("version", CONTRACT_VERSION)
                .put("pack", En16931Pack.PACK_ID + "/" + En16931Pack.version())
                .put("directory", "rules/" + En16931Pack.PACK_ID + "/" + En16931Pack.version());

        StreamingReader streaming = new StreamingReader(ReaderOptions.defaults()
                .withRegistry(COMBINED));
        Map<String, SemanticDocument> bases = new LinkedHashMap<>();
        Manifest.Array array = Manifest.array();
        for (Oracle.Mutation mutation : Oracle.all()) {
            String base = "conformance/esj/" + mutation.source() + ".esj.json";
            SemanticDocument unchanged = bases.computeIfAbsent(base,
                    name -> reader.read(Fixtures.bytes(name)));
            SemanticDocument mutated = streaming.read(Oracle.apply(mutation)).document();

            Manifest.Array changes = changes(unchanged, mutated);
            SemanticDocument rebuilt = rebuild(unchanged, mutated);
            assertEquals(mutated.values(), rebuilt.values(),
                    mutation.id() + ": the changes rebuild the document the reader built");

            List<String> reported = reported(ENGINE, rebuilt, false);
            List<String> warnings = reported(ENGINE, rebuilt, true);
            assertEquals(mutation.expectedNative(), reported,
                    mutation.id() + ": the pack over the rebuilt document");
            assertEquals(mutation.expectedWarnings(), warnings,
                    mutation.id() + ": which of its findings decide no verdict");

            array.add(Manifest.object()
                    .put("id", mutation.id())
                    .put("rule", mutation.rule())
                    .put("base", base)
                    .put("changes", changes)
                    .put("expect", Manifest.object()
                            .put("rules", Manifest.of(reported))
                            .put("warnings", Manifest.of(warnings))));
        }
        return file.put("cases", array);
    }

    /**
     * Builds the rule cases of the later edition, in the form a binding reads.
     *
     * <p>No syntax binds that edition, so no instance of it exists to mutate. Its cases are the
     * ones its pack is weighed by: a document of the corpus written up to the edition, then
     * changed so that the rule the case is for holds, then changed once more so that it
     * speaks. A binding does not write documents up to an edition — the manifest does not
     * cover {@code esj upgrade} — so the documents the changes start from are carried in the
     * file itself, and each case records both states with what the pack reports about each.
     */
    private Manifest.Object laterEditionCases(RulePackSource source) {
        RuleEngine engine = source.engine(Registry.forEdition(LATER_EDITION));
        Object written = laterEditionCaseSource();
        Manifest.Object bases = Manifest.object();
        Map<String, SemanticDocument> documents = new LinkedHashMap<>();
        for (Map.Entry<?, ?> named : ((Map<?, ?>) member(written, "documents")).entrySet()) {
            String name = (String) named.getKey();
            SemanticDocument upgraded = EditionUpgrade.apply(
                    reader.read(Fixtures.bytes((String) member(named.getValue(), "source"))),
                    LATER_EDITION, UPGRADE).require();
            SemanticDocument base = changed(upgraded, (List<?>) member(named.getValue(), "prepare"));
            documents.put(name, base);
            bases.put(name, tree(Oracle.json(Canonicalizer.canonicalBytes(base))));
        }
        Manifest.Array array = Manifest.array();
        Map<String, Integer> seen = new TreeMap<>();
        for (Object element : (List<?>) member(written, "cases")) {
            String rule = (String) member(element, "rule");
            String name = (String) member(element, "document");
            SemanticDocument base = documents.get(name);
            SemanticDocument holds = changed(base, (List<?>) member(element, "holds"));
            SemanticDocument speaks = changed(holds, (List<?>) member(element, "speaks"));
            int count = seen.merge(rule, 1, Integer::sum);
            String id = rule.toLowerCase(Locale.ROOT) + (count == 1 ? "" : "-" + count);
            array.add(laterEditionCase(engine, id + "-holds", rule, name, base, holds));
            array.add(laterEditionCase(engine, id + "-speaks", rule, name, base, speaks));
        }
        return Manifest.object()
                .put("format", FORMAT + "-Rules")
                .put("version", CONTRACT_VERSION)
                .put("pack", source.pack().id() + "/" + source.pack().version())
                .put("directory", "rules/" + source.pack().id() + "/" + source.pack().version())
                .put("bases", bases)
                .put("cases", array);
    }

    /** One case of the later edition: a named base document, the changes, the report. */
    private static Manifest.Object laterEditionCase(RuleEngine engine, String id, String rule,
                                                    String base, SemanticDocument unchanged,
                                                    SemanticDocument changed) {
        SemanticDocument rebuilt = rebuild(unchanged, changed);
        assertEquals(changed.values(), rebuilt.values(),
                id + ": the changes rebuild the document the case describes");
        return Manifest.object()
                .put("id", id)
                .put("rule", rule)
                .put("baseDocument", base)
                .put("changes", changes(unchanged, changed))
                .put("expect", Manifest.object()
                        .put("rules", Manifest.of(reported(engine, rebuilt, false)))
                        .put("warnings", Manifest.of(reported(engine, rebuilt, true))));
    }

    /** The case file of the pack of the later edition, as its evidence records it. */
    private static Object laterEditionCaseSource() {
        return Oracle.json(Fixtures.bytes(LATER_EDITION_CASES));
    }

    /** The rule pack of the later edition, where this build carries one. */
    private static Optional<RulePackSource> laterEditionPack() {
        if (!theLaterEditionIsThere()) {
            return Optional.empty();
        }
        return RulePackSources.forRegistry(Registry.forEdition(LATER_EDITION));
    }

    static boolean theLaterEditionPackIsThere() {
        return laterEditionPack().isPresent();
    }

    /** The name of the rule case file of a pack, which carries the pack in it. */
    private static String laterEditionCasesFile(RulePackSource source) {
        return "cases-" + source.pack().id() + "-" + source.pack().version() + ".json";
    }

    /**
     * Makes the changes a case of the later edition writes: a value, a value with its
     * identification scheme, the removal of a value, or the removal of a group instance.
     */
    private static SemanticDocument changed(SemanticDocument document, List<?> changes) {
        SemanticDocument.Builder builder = document.toBuilder();
        if (changes == null) {
            return builder.build();
        }
        for (Object change : changes) {
            String path = (String) member(change, "path");
            if (flag(change, "delete")) {
                builder.remove(SemanticPath.of(path));
            } else if (flag(change, "deleteUnder")) {
                builder.removeUnder(SemanticPath.group(path));
            } else {
                String scheme = (String) member(change, "scheme");
                String content = (String) member(change, "value");
                builder.set(SemanticPath.of(path), scheme == null
                        ? SemanticValue.of(content)
                        : SemanticValue.identifier(content, scheme));
            }
        }
        return builder.build();
    }

    private static Object member(Object object, String name) {
        return ((Map<?, ?>) object).get(name);
    }

    /** Tells whether a member is the literal true, which the tree reader returns as text. */
    private static boolean flag(Object object, String name) {
        return "true".equals(String.valueOf(member(object, name)));
    }

    /** A tree of maps, lists, strings and flags as the manifest writes it. */
    private static Manifest tree(Object node) {
        if (node instanceof Map<?, ?> members) {
            Manifest.Object object = Manifest.object();
            for (Map.Entry<?, ?> entry : members.entrySet()) {
                object.put((String) entry.getKey(), tree(entry.getValue()));
            }
            return object;
        }
        if (node instanceof List<?> elements) {
            Manifest.Array array = Manifest.array();
            for (Object element : elements) {
                array.add(tree(element));
            }
            return array;
        }
        if (node instanceof Long number) {
            return Manifest.of(number);
        }
        return Manifest.of((String) node);
    }

    // ------------------------------------------------------------- the sections

    /**
     * The arithmetic of the rule language: a pack whose rules pin the division of
     * {@code rules/README.md}, the document it is run over, and what it reports there.
     *
     * <p>The quotients are the one part of this section that is written by hand, in the pack,
     * and what the manifest records about them is measured here. A rule of the pack that is
     * not about a division by zero asserts that a quotient is not the value worked out for it,
     * so it is reported exactly where this implementation divides as the language says; the
     * assertion below holds the reference to every one of them, and a quotient it computed
     * differently leaves the build red rather than a manifest that expects the wrong digits.
     */
    private Manifest.Object arithmetic() {
        RulePack pack = RulePacks.read(new ByteArrayInputStream(Fixtures.bytes(ARITHMETIC_PACK)),
                ARITHMETIC_PACK);
        RuleEngine engine = RuleEngine.compile(pack, COMBINED);
        SemanticDocument base = reader.read(Fixtures.bytes(GRAMMAR_BASE));
        List<String> reported = reported(engine, base, false);
        List<String> warnings = reported(engine, base, true);
        List<String> worked = pack.rules().stream().map(RuleDefinition::id)
                .filter(id -> !ARITHMETIC_SILENT.contains(id)).sorted().toList();
        assertEquals(worked, reported,
                "every quotient of " + ARITHMETIC_PACK + " is the one worked out by hand");
        return Manifest.object()
                .put("pack", ARITHMETIC_PACK)
                .put("base", GRAMMAR_BASE)
                .put("expect", Manifest.object()
                        .put("rules", Manifest.of(reported))
                        .put("warnings", Manifest.of(warnings)));
    }

    /**
     * The registries a binding loads before it runs the core manifest: the registry of the
     * default edition and the extension registry the conformance corpus needs.
     */
    private Manifest.Array coreRegistries() {
        return Manifest.array()
                .add(registry(CORE_REGISTRY, Registry.forEdition(Registry.defaultEditionKey())))
                .add(registry(EXTENSION_REGISTRIES.get(0), Registry.xrechnungExtension()))
                .add(registry(EXTENSION_REGISTRIES.get(1), Registry.b2cExtension()));
    }

    /**
     * The registries one document was measured with, as files of the repository: the core
     * registry of its edition, and the extension registries combined into it. A binding reads
     * this rather than deriving it, so that a build carrying fewer registries than the
     * manifest was written with shows up as a difference here and not as a document whose
     * extension terms were quietly not checked (specification, section 5.6).
     *
     * @param document the document, as the reader read it
     * @return the files, core registry first
     */
    private static Manifest.Array registriesOf(SemanticDocument document) {
        Manifest.Array files = Manifest.array();
        if (theLaterEditionIsThere()
                && Registry.forEdition(LATER_EDITION).describes(document.semanticModel())) {
            return files.add(Manifest.of(LATER_EDITION_REGISTRY));
        }
        // The core registry and its extensions, also for an edition no registry describes:
        // those are what the document was measured with, and they answer that they do not
        // describe it (specification, section 9.2).
        files.add(Manifest.of(CORE_REGISTRY));
        EXTENSION_REGISTRIES.forEach(file -> files.add(Manifest.of(file)));
        return files;
    }

    private Manifest.Object registry(String file, Registry registry) {
        Manifest.Object object = Manifest.object()
                .put("file", file)
                .put("semanticModel", registry.semanticModel())
                .put("terms", registry.terms().size())
                .put("businessTerms", count(registry, TermKind.BT))
                .put("businessGroups", count(registry, TermKind.BG));
        Manifest.Array sample = Manifest.array();
        for (Term term : samples(registry)) {
            sample.add(sample(registry, term));
        }
        return object.put("samplePaths", sample);
    }

    private static long count(Registry registry, TermKind kind) {
        return registry.terms().stream().filter(term -> term.kind() == kind).count();
    }

    /**
     * The sample of terms the manifest writes down: the first term of every semantic data
     * type the registry uses, and every term that declares a supplementary component. The
     * choice is made from the registry rather than written here, so that a type or a
     * component added to an edition appears in the sample without this test being edited.
     */
    private static List<Term> samples(Registry registry) {
        Map<String, Term> byDatatype = new TreeMap<>();
        List<Term> withComponents = new ArrayList<>();
        for (Term term : registry.terms()) {
            Optional<SemanticType> datatype = term.datatype();
            if (datatype.isPresent()) {
                byDatatype.putIfAbsent(datatype.get().registryDatatype(), term);
            }
            if (!term.components().isEmpty()) {
                withComponents.add(term);
            }
        }
        Set<Term> chosen = new LinkedHashSet<>(byDatatype.values());
        chosen.addAll(withComponents);
        List<Term> sample = new ArrayList<>(chosen);
        sample.sort((one, other) -> Integer.compare(one.order(), other.order()));
        return List.copyOf(sample);
    }

    private static Manifest.Object sample(Registry registry, Term term) {
        Cardinality cardinality = registry.cardinality(term.id());
        Manifest.Object object = Manifest.object()
                .put("path", pathOf(registry, term))
                .put("term", term.id())
                .put("kind", term.kind().name())
                .put("min", cardinality.min());
        object.put("max", cardinality.isUnbounded()
                ? Manifest.of("n") : Manifest.of(cardinality.max()));
        term.datatype().ifPresent(type -> object.put("datatype", type.registryDatatype()));
        Manifest.Array components = Manifest.array();
        for (Component component : term.components()) {
            components.add(Manifest.object()
                    .put("member", component.role().jsonMember())
                    .put("min", component.cardinality().min()));
        }
        return object.putUnlessEmpty("components", components);
    }

    /** The canonical path of the first occurrence of a term, indices and all. */
    private static String pathOf(Registry registry, Term term) {
        StringBuilder path = new StringBuilder();
        for (String id : term.path()) {
            path.append('/').append(id);
            if (repeats(registry, id)) {
                path.append("/0");
            }
        }
        return path.toString();
    }

    /**
     * Whether a step of a path carries an occurrence index. An extension registry describes
     * its own terms and roots them in a group of the core model (specification, section 5.6),
     * so a step it does not know is looked up in the combined registry, which is where that
     * group is described.
     */
    private static boolean repeats(Registry registry, String id) {
        return registry.term(id).isPresent()
                ? registry.isRepeatable(id)
                : COMBINED.isRepeatable(id);
    }

    /** One document: where it is, what it digests to, and what the three layers say. */
    private Manifest.Object document(String file) {
        byte[] bytes = Fixtures.bytes(file);
        SemanticDocument document = reader.read(bytes);
        Manifest.Object object = Manifest.object().put("file", file);
        String canonical = canonicalFileOf(file);
        if (canonical != null) {
            object.put("canonical", canonical);
            assertArrayEquals(Fixtures.bytes(canonical), Canonicalizer.canonicalBytes(document),
                    canonical + " is the canonical form of " + file);
        }
        return object
                .put("semanticModel", document.semanticModel())
                .put("registries", registriesOf(document))
                .put("values", document.values().size())
                .put("canonicalBytes", Canonicalizer.canonicalBytes(document).length)
                .put("semanticDigest", Canonicalizer.semanticDigest(document))
                .put("documentDigest", Canonicalizer.documentDigest(document))
                .put("outcome", outcome(validation(bytes, Limits.defaults(), CARRIED)));
    }

    /**
     * Validates the bytes of a document as a binding answers a {@code validate} request: the
     * reader decides layer L1 under the limits given, and where it built a document the
     * structural validator decides L2 and L3 against the registry of its edition among those
     * given; the two results are composed (specification, section 9.5).
     */
    private static ValidationResult validation(byte[] bytes, Limits limits, List<Registry> registries) {
        ReadResult read = EsjReader.withLimits(limits).readWithFindings(bytes);
        ValidationResult result = read.validation();
        if (read.isWellFormed()) {
            result = result.merge(StructuralValidator.validate(read.orElseThrow(), registries,
                    MODEL_AND_CARDINALITY));
        }
        return result;
    }

    /**
     * The whole answer of a validation, as the manifest records it: the status, every layer not
     * evaluated with its reason, and every finding — errors, warnings and information alike —
     * with its path, code, subject and severity, in the order they were reported. An empty
     * subject is recorded as the empty string, so that it is compared like any other
     * (specification, section 9.5).
     */
    private static Manifest.Object outcome(ValidationResult result) {
        Manifest.Array layers = Manifest.array();
        for (Map.Entry<ValidationLayer, NotEvaluatedReason> layer
                : new TreeMap<>(result.notEvaluated()).entrySet()) {
            layers.add(Manifest.object().inline()
                    .put("layer", layer.getKey().name())
                    .put("reason", layer.getValue().token()));
        }
        Manifest.Array findings = Manifest.array();
        for (Finding finding : result.findings()) {
            findings.add(Manifest.object().inline()
                    .put("path", finding.path().toString())
                    .put("code", finding.code().code())
                    .put("subject", finding.subject())
                    .put("severity", finding.severity().name().toLowerCase(Locale.ROOT)));
        }
        return Manifest.object()
                .put("status", result.status().name())
                .put("notEvaluated", layers)
                .put("findings", findings);
    }

    /**
     * One negative fixture: the layer that catches the defect and the whole answer of a
     * validation, which section 9.6 of the specification fixes in full — how far a reader
     * reads, the order of its findings, and the checks of one path at layer L2. Where the
     * reader builds a document, the fixture carries its digests as well, because content that
     * a model layer refuses passes the reader and the canonicalizer unchanged (sections 3.2
     * and 3.4).
     *
     * <p>A fixture of {@code conformance/fixtures/model/} may name the registries it is
     * validated with ({@link #VALIDATED_WITH}); the runner passes them with the request, and
     * the binding uses those instead of the ones it carries.
     *
     * @param file the fixture, as a path in the repository
     * @return its entry
     */
    private Manifest.Object invalid(String file) {
        byte[] bytes = Fixtures.bytes(file);
        List<String> named = VALIDATED_WITH.get(file);
        List<Registry> registries = named == null ? CARRIED : List.of(combined(named));
        ReadResult read = reader.readWithFindings(bytes);
        ValidationResult result = validation(bytes, Limits.defaults(), registries);
        Manifest.Object entry = Manifest.object()
                .put("file", file)
                .put("layer", layer(read, result));
        if (named != null) {
            entry.put("registries", Manifest.of(named));
        }
        if (read.isWellFormed()) {
            SemanticDocument document = read.orElseThrow();
            entry.put("values", document.values().size())
                    .put("canonicalBytes", Canonicalizer.canonicalBytes(document).length)
                    .put("semanticDigest", Canonicalizer.semanticDigest(document))
                    .put("documentDigest", Canonicalizer.documentDigest(document));
        }
        return entry.put("outcome", outcome(result));
    }

    /**
     * The layer that catches the defect of a negative fixture: {@code limit} where the reader
     * stopped at a bound and found nothing else, {@code L1} where it refused the document, the
     * layer of the first error a validator reported over a document the reader built, and
     * {@code business-rule} where no layer of this specification reports one.
     */
    private static String layer(ReadResult read, ValidationResult result) {
        if (!read.isWellFormed()) {
            boolean limit = read.findings().stream().filter(Finding::isError)
                    .allMatch(finding -> finding.code() == FindingCode.ESJ_L1_LIMIT);
            return limit ? "limit" : "L1";
        }
        return result.findings().stream().filter(Finding::isError).findFirst()
                .map(finding -> finding.code().layer().name()).orElse("business-rule");
    }

    /** Reads registry files of the repository and combines every one after the first with it. */
    private static Registry combined(List<String> files) {
        Registry combined = null;
        for (String file : files) {
            Registry read;
            try (InputStream in = resource(file).openStream()) {
                read = Registry.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            combined = combined == null ? read : combined.withExtension(read);
        }
        return combined;
    }

    // ------------------------------------------------------------- bounds and registries

    /**
     * A document read under bounds of the specification, section 12.2, each named as
     * {@link Limits} and every binding name it: the cases of the {@code bounds} section.
     *
     * @param file   the document
     * @param limits the bounds that replace the defaults, in the order they are written
     */
    private record Bound(String file, Map<String, Long> limits) {
    }

    private static Bound bound(String file, Object... pairs) {
        Map<String, Long> limits = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            limits.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return new Bound(file, limits);
    }

    /**
     * Every bound of the specification, section 12.2 at its limit and one past it, with the
     * cases that decide what a bound is measured on: the normalized string inside
     * {@code values} and the raw one outside it, the string bound before the fixed value, the
     * edition grammar, the owner-token grammar and the path grammar, the number token inside
     * and outside {@code extensions}, the structure a reader walks past, the two string bounds
     * on a value object, a member name past the bound in each kind of object, which names the
     * object and not the name, the formula of the total binary content, the text
     * order in which a bound stops a reader, and the order of the checks of one token: a
     * string or a name is read as a whole JSON token before its bound, a lone surrogate comes
     * before the bound of a string and after the bound of a name. A byte bound is never set
     * below 64, so that nothing but the member a case is about reaches it.
     */
    private static List<Bound> bounds() {
        String minimal = "examples/minimal.esj.json";
        long size = Fixtures.bytes(minimal).length;
        long values = EsjReader.strict().read(Fixtures.bytes(minimal)).values().size();
        String b = BOUND_FIXTURES + "/";
        return List.of(
                bound(minimal, "maxDocumentBytes", size),
                bound(minimal, "maxDocumentBytes", size - 1),
                bound(minimal, "maxValues", values),
                bound(minimal, "maxValues", values - 1),
                bound(minimal, "maxPathSegments", 4),
                bound(minimal, "maxPathSegments", 3),
                bound(minimal, "maxPathBytes", 21),
                bound(minimal, "maxPathBytes", 20),
                bound(b + "string.esj.json", "maxStringBytes", 64),
                bound(b + "string.esj.json", "maxStringBytes", 63),
                bound(b + "normalized-string.esj.json", "maxStringBytes", 64),
                bound(b + "normalized-string.esj.json", "maxStringBytes", 63),
                bound(b + "extension-string.esj.json", "maxStringBytes", 65),
                bound(b + "extension-string.esj.json", "maxStringBytes", 64),
                bound(b + "source-syntax.esj.json", "maxStringBytes", 65),
                bound(b + "source-syntax.esj.json", "maxStringBytes", 64),
                bound(b + "envelope-string.esj.json", "maxStringBytes", 65),
                bound(b + "envelope-string.esj.json", "maxStringBytes", 64),
                bound(b + "semantic-model.esj.json", "maxStringBytes", 65),
                bound(b + "semantic-model.esj.json", "maxStringBytes", 64),
                bound(b + "member-name.esj.json", "maxStringBytes", 65),
                bound(b + "member-name.esj.json", "maxStringBytes", 64),
                bound(b + "owner-token.esj.json", "maxStringBytes", 129),
                bound(b + "owner-token.esj.json", "maxStringBytes", 128),
                bound(b + "long-bad-path.esj.json", "maxPathBytes", 65),
                bound(b + "long-bad-path.esj.json", "maxPathBytes", 64),
                bound(b + "long-bad-path.esj.json", "maxStringBytes", 64),
                bound(b + "name-in-source.esj.json", "maxStringBytes", 65),
                bound(b + "name-in-source.esj.json", "maxStringBytes", 64),
                bound(b + "name-in-value-object.esj.json", "maxStringBytes", 65),
                bound(b + "name-in-value-object.esj.json", "maxStringBytes", 64),
                bound(b + "name-in-extension.esj.json", "maxStringBytes", 65),
                bound(b + "name-in-extension.esj.json", "maxStringBytes", 64),
                bound(b + "number-token.esj.json", "maxStringBytes", 65),
                bound(b + "number-token.esj.json", "maxStringBytes", 64),
                bound(b + "number-in-values.esj.json", "maxStringBytes", 65),
                bound(b + "number-in-values.esj.json", "maxStringBytes", 64),
                bound(b + "walked-past-string.esj.json", "maxStringBytes", 64),
                bound(b + "walked-past-number.esj.json", "maxStringBytes", 65),
                bound(b + "walked-past-number.esj.json", "maxStringBytes", 64),
                bound(b + "walked-past-name.esj.json", "maxStringBytes", 65),
                bound(b + "walked-past-name.esj.json", "maxStringBytes", 64),
                bound(b + "walked-past-depth.esj.json", "maxExtensionDepth", 3),
                bound(b + "walked-past-depth.esj.json", "maxExtensionDepth", 2),
                bound(b + "walked-past-value-member.esj.json", "maxExtensionDepth", 3),
                bound(b + "walked-past-value-member.esj.json", "maxExtensionDepth", 2),
                bound(b + "value-object-strings.esj.json",
                        "maxStringBytes", 64, "maxBinaryValueBytes", 128),
                bound(b + "value-object-strings.esj.json",
                        "maxStringBytes", 63, "maxBinaryValueBytes", 128),
                bound(b + "value-object-strings.esj.json",
                        "maxStringBytes", 64, "maxBinaryValueBytes", 127),
                bound(b + "value-object-strings.esj.json",
                        "maxStringBytes", 128, "maxBinaryValueBytes", 64),
                bound(b + "empty-before-limit.esj.json", "maxStringBytes", 64),
                bound(b + "limit-before-empty.esj.json", "maxStringBytes", 64),
                bound(b + "value-object-empty-before-limit.esj.json", "maxStringBytes", 64),
                bound(b + "value-object-limit-before-empty.esj.json", "maxStringBytes", 64),
                bound(b + "value-object-members.esj.json", "maxValueMembers", 2),
                bound(b + "value-object-members.esj.json", "maxValueMembers", 1),
                bound(b + "binary-total.esj.json", "maxTotalBinaryBytes", 144),
                bound(b + "binary-total.esj.json", "maxTotalBinaryBytes", 143),
                bound(b + "binary-total-padding.esj.json", "maxTotalBinaryBytes", 10),
                bound(b + "binary-total-padding.esj.json", "maxTotalBinaryBytes", 9),
                bound("examples/extension-depth.esj.json", "maxExtensionDepth", 32),
                bound("examples/extension-depth.esj.json", "maxExtensionDepth", 31),
                bound(b + "extension-nodes.esj.json", "maxExtensionNodes", 6),
                bound(b + "extension-nodes.esj.json", "maxExtensionNodes", 5),
                bound(b + "long-string-surrogate.esj.json", "maxStringBytes", 64),
                bound(b + "long-name-surrogate.esj.json", "maxStringBytes", 200),
                bound(b + "long-name-surrogate.esj.json", "maxStringBytes", 64),
                bound(b + "long-string-unclosed.esj.json", "maxStringBytes", 64),
                bound(b + "long-name-bad-escape.esj.json", "maxStringBytes", 64));
    }

    /** Returns the defaults with every bound a case names replaced. */
    private static Limits limitsOf(Map<String, Long> named) {
        Limits limits = Limits.defaults();
        for (Map.Entry<String, Long> bound : named.entrySet()) {
            long value = bound.getValue();
            limits = switch (bound.getKey()) {
                case "maxDocumentBytes" -> limits.withMaxDocumentBytes(value);
                case "maxValues" -> limits.withMaxValues(Math.toIntExact(value));
                case "maxValueMembers" -> limits.withMaxValueMembers(Math.toIntExact(value));
                case "maxPathSegments" -> limits.withMaxPathSegments(Math.toIntExact(value));
                case "maxPathBytes" -> limits.withMaxPathBytes(Math.toIntExact(value));
                case "maxStringBytes" -> limits.withMaxStringBytes(value);
                case "maxBinaryValueBytes" -> limits.withMaxBinaryValueBytes(value);
                case "maxTotalBinaryBytes" -> limits.withMaxTotalBinaryBytes(value);
                case "maxExtensionDepth" -> limits.withMaxExtensionDepth(Math.toIntExact(value));
                case "maxExtensionNodes" -> limits.withMaxExtensionNodes(Math.toIntExact(value));
                default -> throw new IllegalArgumentException("no bound is called " + bound.getKey());
            };
        }
        return limits;
    }

    private Manifest.Array boundSection() {
        Manifest.Array array = Manifest.array();
        for (Bound bound : bounds()) {
            Manifest.Object limits = Manifest.object().inline();
            bound.limits().forEach(limits::put);
            array.add(Manifest.object()
                    .put("file", bound.file())
                    .put("limits", limits)
                    .put("outcome", outcome(validation(Fixtures.bytes(bound.file()),
                            limitsOf(bound.limits()), CARRIED))));
        }
        return array;
    }

    /**
     * The sets of registry files a loader is asked to read and combine, the first as the core
     * and every further one as an extension of it (specification, section 10): the registries
     * of the repository, which it takes, and the registries of
     * {@code conformance/fixtures/registries/}, each breaking one rule a loader holds a
     * registry to, alone and combined with the edition it names.
     */
    private static List<List<String>> registryChecks() {
        String core = CORE_REGISTRY;
        String xr = EXTENSION_REGISTRIES.get(0);
        String b2c = EXTENSION_REGISTRIES.get(1);
        String t = TEST_REGISTRIES + "/";
        List<List<String>> checks = new ArrayList<>();
        checks.add(List.of(core));
        checks.add(List.of(core, xr, b2c));
        checks.add(List.of(core, core));
        checks.add(List.of(core, xr, xr));
        checks.add(List.of(t + "cardinality.json"));
        checks.add(List.of(t + "extension.json"));
        checks.add(List.of(core, t + "extension.json"));
        checks.add(List.of(core, xr, t + "extension.json"));
        checks.add(List.of(t + "cardinality.json", t + "extension.json"));
        for (String refused : List.of("binary-without-components.json",
                "scheme-version-without-scheme.json", "mandatory-version-beside-optional-scheme.json",
                "duplicate-identifier.json", "no-imports.json", "identifier-without-namespace.json",
                "two-namespaces.json", "redefines-core-term.json", "imports-another-model.json",
                "identifier-of-another-extension.json")) {
            checks.add(List.of(t + refused));
            checks.add(List.of(core, t + refused));
        }
        checks.add(List.of(core, xr, t + "identifier-of-another-extension.json"));
        return List.copyOf(checks);
    }

    private Manifest.Array registryCheckSection() {
        Manifest.Array array = Manifest.array();
        for (List<String> files : registryChecks()) {
            boolean accepted;
            try {
                combined(files);
                accepted = true;
            } catch (de.bsnsoft.esj.EsjFormatException refused) {
                accepted = false;
            }
            array.add(Manifest.object().put("files", Manifest.of(files)).put("accepted", accepted));
        }
        return array;
    }

    /**
     * The canonical order cases: a document whose members are written in the wrong order,
     * and the bytes a canonicalizer has to produce from it. The first is the extended
     * example scrambled, so that its canonical form is the one already checked in beside
     * that example and this repository states it once.
     */
    private Manifest.Array canonicalOrder() {
        Manifest.Array array = Manifest.array();
        array.add(canonicalOrder(DIRECTORY + "canonical-order/scrambled.esj.json",
                "examples/extended.canonical.esj.json"));
        for (String name : CANONICAL_ORDER) {
            array.add(canonicalOrder(DIRECTORY + "canonical-order/" + name + ".esj.json",
                    DIRECTORY + "canonical-order/" + name + ".canonical.esj.json"));
        }
        return array;
    }

    /**
     * The canonical order cases beside their canonical bytes in
     * {@code conformance/fixtures/canonical-order/}: indices, every escape of JSON, the corners
     * of the path order, the numbers of {@code extensions}, and value objects written in reverse.
     */
    private static final List<String> CANONICAL_ORDER =
            List.of("indices", "escaping", "corners", "numbers", "value-object-order");

    private Manifest.Object canonicalOrder(String scrambled, String canonical) {
        SemanticDocument document = reader.read(Fixtures.bytes(scrambled));
        byte[] bytes = Canonicalizer.canonicalBytes(document);
        assertArrayEquals(Fixtures.bytes(canonical), bytes,
                canonical + " is the canonical form of " + scrambled);
        return Manifest.object()
                .put("scrambled", scrambled)
                .put("canonical", canonical)
                .put("values", document.values().size())
                .put("documentDigest", Canonicalizer.documentDigest(document));
    }

    /**
     * One value grammar, measured: every candidate is substituted into the base document at
     * the path of the table, and the finding the model layer reports about that path decides
     * which list it belongs in.
     */
    private Manifest.Object grammar(Grammar grammar, String base, Registry registry) {
        SemanticDocument document = reader.read(Fixtures.bytes(base));
        SemanticPath path = SemanticPath.of(grammar.path());
        Manifest.Array accept = Manifest.array();
        for (SemanticValue candidate : grammar.accept()) {
            assertEquals(List.of(), codesAt(document, registry, path, candidate),
                    grammar.datatype() + " accepts " + candidate.content());
            accept.add(value(candidate));
        }
        Manifest.Array reject = Manifest.array();
        for (SemanticValue candidate : grammar.reject()) {
            assertEquals(List.of(grammar.code()), codesAt(document, registry, path, candidate),
                    grammar.datatype() + " rejects " + candidate.content());
            reject.add(value(candidate));
        }
        return Manifest.object()
                .put("datatype", grammar.datatype().registryDatatype())
                .put("code", grammar.code())
                .put("base", base)
                .put("path", grammar.path())
                .put("accept", accept)
                .put("reject", reject);
    }

    /**
     * One envelope grammar, measured: every candidate is written into the base document — as
     * the value of {@code semanticModel}, or as the name of the one member of
     * {@code extensions}, whose value is the string {@code x} — and the codes of the errors
     * layer L1 reports decide which list it belongs in.
     */
    private Manifest.Object envelopeGrammar(EnvelopeGrammar grammar, String base) {
        String canonical = new String(Canonicalizer.canonicalBytes(reader.read(Fixtures.bytes(base))),
                StandardCharsets.UTF_8);
        Manifest.Array accept = Manifest.array();
        for (String candidate : grammar.accept()) {
            assertEquals(List.of(), layerOneErrors(written(canonical, grammar.member(), candidate)),
                    grammar.grammar() + " accepts " + candidate);
            accept.add(Manifest.of(candidate));
        }
        Manifest.Array reject = Manifest.array();
        for (String candidate : grammar.reject()) {
            assertEquals(List.of(grammar.code()),
                    layerOneErrors(written(canonical, grammar.member(), candidate)),
                    grammar.grammar() + " rejects " + candidate);
            reject.add(Manifest.of(candidate));
        }
        return Manifest.object()
                .put("grammar", grammar.grammar())
                .put("code", grammar.code())
                .put("base", base)
                .put("member", grammar.member())
                .put("accept", accept)
                .put("reject", reject);
    }

    /** The canonical text of a document with a candidate written into one envelope member. */
    private static byte[] written(String canonical, String member, String candidate) {
        String text;
        if (member.equals("semanticModel")) {
            int at = canonical.indexOf("\"semanticModel\":\"") + "\"semanticModel\":".length();
            int end = canonical.indexOf('"', at + 1) + 1;
            text = canonical.substring(0, at) + jsonString(candidate) + canonical.substring(end);
        } else {
            text = canonical.substring(0, canonical.length() - 1)
                    + ",\"extensions\":{" + jsonString(candidate) + ":\"x\"}}";
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** A string as JSON writes it, with the escapes of the specification, section 7.5. */
    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }

    /** The codes of the errors of layer L1 a validation of some bytes reports. */
    private static List<String> layerOneErrors(byte[] bytes) {
        List<String> codes = new ArrayList<>();
        for (Finding finding : validation(bytes, Limits.defaults(), CARRIED).findings()) {
            if (finding.isError() && finding.code().layer() == ValidationLayer.L1) {
                codes.add(finding.code().code());
            }
        }
        return codes;
    }

    /** The model layer codes reported about one path after a candidate is put there. */
    private static List<String> codesAt(SemanticDocument base,
                                        Registry registry,
                                        SemanticPath path,
                                        SemanticValue candidate) {
        SemanticDocument document = base.toBuilder().set(path, candidate).build();
        List<String> codes = new ArrayList<>();
        for (Finding finding : StructuralValidator
                .validate(document, registry, EnumSet.of(ValidationLayer.L2)).findings()) {
            if (finding.isError() && finding.path().equals(path)) {
                codes.add(finding.code().code());
            }
        }
        return codes;
    }

    // ---------------------------------------------------------------- the pieces

    /** A value as a document writes it: a string, or the object of section 6.1. */
    private static Manifest value(SemanticValue value) {
        if (!value.hasComponents()) {
            return Manifest.of(value.content());
        }
        Manifest.Object object = Manifest.object().put("value", value.content());
        if (value.scheme() != null) {
            object.put("scheme", value.scheme());
        }
        if (value.schemeVersion() != null) {
            object.put("schemeVersion", value.schemeVersion());
        }
        if (value.mimeCode() != null) {
            object.put("mimeCode", value.mimeCode());
        }
        if (value.filename() != null) {
            object.put("filename", value.filename());
        }
        return object;
    }

    /** The changes that turn one document's values into another's, in canonical order. */
    private static Manifest.Array changes(SemanticDocument before, SemanticDocument after) {
        Manifest.Array array = Manifest.array();
        Set<SemanticPath> paths = new TreeSet<>();
        paths.addAll(before.values().keySet());
        paths.addAll(after.values().keySet());
        for (SemanticPath path : paths) {
            SemanticValue was = before.values().get(path);
            SemanticValue now = after.values().get(path);
            if (now == null) {
                array.add(Manifest.object().put("path", path.toString()).put("remove", true));
            } else if (!now.equals(was)) {
                array.add(Manifest.object().put("path", path.toString())
                        .put("value", value(now)));
            }
        }
        return array;
    }

    /** Applies the same changes the manifest records, the way a binding will apply them. */
    private static SemanticDocument rebuild(SemanticDocument before, SemanticDocument after) {
        SemanticDocument.Builder builder = before.toBuilder();
        Set<SemanticPath> paths = new TreeSet<>();
        paths.addAll(before.values().keySet());
        paths.addAll(after.values().keySet());
        for (SemanticPath path : paths) {
            SemanticValue was = before.values().get(path);
            SemanticValue now = after.values().get(path);
            if (now == null) {
                builder.remove(path);
            } else if (!now.equals(was)) {
                builder.set(path, now);
            }
        }
        return builder.build();
    }

    /** The rule identifiers a pack reports over a document: all of them, or the warnings. */
    private static List<String> reported(RuleEngine engine, SemanticDocument document,
                                         boolean warningsOnly) {
        Set<String> codes = new TreeSet<>();
        for (RuleFinding finding : engine.evaluate(document)) {
            if (!warningsOnly || finding.severity() == Severity.WARNING) {
                codes.add(finding.code());
            }
        }
        return List.copyOf(codes);
    }

    /** The canonical form beside a document, where the repository carries one. */
    private static String canonicalFileOf(String file) {
        String canonical = file.substring(0, file.length() - ".esj.json".length())
                + ".canonical.esj.json";
        return resource(canonical) == null ? null : canonical;
    }

    // ----------------------------------------------------------------- the files

    /**
     * The documents of the default edition: the examples, the conformance corpus, the documents
     * of {@code conformance/fixtures/documents/} and the worked example of the specification,
     * appendix B.
     */
    private static List<String> coreDocuments() {
        List<String> files = new ArrayList<>(documentsIn("examples"));
        files.addAll(documentsIn("conformance/esj"));
        files.addAll(documentsIn(DOCUMENT_FIXTURES));
        files.addAll(documentsIn(ANNEX_B));
        files.removeIf(FixtureManifestTest::belongsToTheLaterEdition);
        return List.copyOf(files);
    }

    /** The documents of the later edition, the example its grammar table is measured in first. */
    private static List<String> laterEditionDocuments() {
        List<String> files = new ArrayList<>(documentsIn("examples"));
        files.addAll(documentsIn(ANNEX_B));
        return files.stream().filter(FixtureManifestTest::belongsToTheLaterEdition).toList();
    }

    /**
     * The negative fixtures of an edition: those of {@code examples/invalid/}, one per kind of
     * error, and those of {@code conformance/fixtures/reader/} and {@code model/}, which pin
     * every variant the specification decides.
     */
    private static List<String> invalidFixtures(boolean laterEdition) {
        List<String> files = new ArrayList<>(documentsIn("examples/invalid"));
        files.addAll(documentsIn(READER_FIXTURES));
        files.addAll(documentsIn(MODEL_FIXTURES));
        return files.stream()
                .filter(file -> belongsToTheLaterEdition(file) == laterEdition)
                .toList();
    }

    /**
     * A fixture belongs to the later edition when its name says so. The names are the ones
     * {@code model/en16931/2026.paths} lists, and a file of that edition under another name
     * would be left in the tree by the distribution that leaves the edition out, so the two
     * statements have to agree — which is what {@code Edition2026PathsTest} checks.
     */
    private static boolean belongsToTheLaterEdition(String file) {
        return file.contains("/edition-" + LATER_EDITION);
    }

    /** The pretty documents of a directory of the repository, sorted, canonical forms aside. */
    private static List<String> documentsIn(String directory) {
        Path root = pathOf(resource(directory));
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .map(file -> directory + "/" + root.relativize(file).toString()
                            .replace(File.separatorChar, '/'))
                    .filter(file -> file.endsWith(".esj.json"))
                    .filter(file -> !file.endsWith(".canonical.esj.json"))
                    .filter(file -> directory.equals("examples/invalid")
                            || !file.startsWith("examples/invalid/"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every file the manifest and its parts name, so that each can be looked for. */
    private List<String> referencedFiles() {
        List<String> files = new ArrayList<>();
        files.addAll(coreDocuments());
        files.addAll(invalidFixtures(false));
        files.add("examples/extended.canonical.esj.json");
        files.add(DIRECTORY + "canonical-order/scrambled.esj.json");
        for (String name : CANONICAL_ORDER) {
            files.add(DIRECTORY + "canonical-order/" + name + ".esj.json");
            files.add(DIRECTORY + "canonical-order/" + name + ".canonical.esj.json");
        }
        bounds().forEach(bound -> files.add(bound.file()));
        registryChecks().forEach(files::addAll);
        VALIDATED_WITH.values().forEach(files::addAll);
        files.add(DIRECTORY + CASES);
        files.add(ARITHMETIC_PACK);
        files.add(DIRECTORY + SCHEMA);
        files.add(GRAMMAR_BASE);
        files.add(CORE_REGISTRY);
        files.addAll(EXTENSION_REGISTRIES);
        files.add(DIRECTORY + "run.py");
        return files;
    }

    static boolean theLaterEditionIsThere() {
        return Registry.editionKeys().contains(LATER_EDITION);
    }

    /**
     * A file of the repository on the test class path. The registries are the one kind of
     * file the build does not copy under its own name: they are put beside the class that
     * loads them, so a reference below {@code model/} is resolved there.
     */
    private static URL resource(String file) {
        URL url = FixtureManifestTest.class.getResource("/" + file);
        if (url == null && file.startsWith("model/")) {
            url = Registry.class.getResource(file.substring("model/".length()));
        }
        return url;
    }

    private static Path pathOf(URL url) {
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(url.toString(), e);
        }
    }

    /**
     * Compares a generated file with the one in the repository, or writes it when the run
     * was asked to.
     */
    private static void expect(String name, byte[] generated) {
        if (REWRITE) {
            write(TREE.resolve(name), generated);
            return;
        }
        byte[] checkedIn = Fixtures.bytes(DIRECTORY + name);
        assertEquals(new String(checkedIn, StandardCharsets.UTF_8),
                new String(generated, StandardCharsets.UTF_8),
                DIRECTORY + name + " is what this implementation answers; regenerate it with"
                        + " -Desj.fixtures.rewrite=true");
    }

    private static void write(Path file, byte[] bytes) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
