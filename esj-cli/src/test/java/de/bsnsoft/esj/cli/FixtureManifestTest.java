package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import de.bsnsoft.esj.json.ReadResult;
import de.bsnsoft.esj.model.Cardinality;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import de.bsnsoft.esj.rules.RuleDefinition;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RulePack;
import de.bsnsoft.esj.rules.RulePacks;
import de.bsnsoft.esj.rules.RuleSeverity;
import de.bsnsoft.esj.rules.en16931.En16931;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

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
 * <p>Two parts of the manifest are not derived from anything. The candidate strings of the
 * value grammars are written down below, and what the manifest records about them — that
 * each is accepted or rejected, and with which finding code — is measured here; a candidate
 * whose outcome changes changes the manifest. The quotients of the arithmetic section are
 * written in their pack, {@code conformance/fixtures/arithmetic/pack.json}, and this test
 * holds the reference implementation to every one of them before it records what the pack
 * reports.
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

    /** The format identifier of a manifest file. */
    private static final String FORMAT = "EN16931-Semantic-JSON-Fixtures";

    /** The version of the fixture contract, raised when a member changes meaning. */
    private static final String CONTRACT_VERSION = "1";

    /** Whether this run writes the files instead of comparing them. */
    private static final boolean REWRITE = Boolean.getBoolean("esj.fixtures.rewrite");

    /**
     * The registry every core fixture is measured against: the core edition with the terms of
     * every extension registry of the repository. A fixture that carries an extension term is
     * measured rather than reported as not checked (specification, section 5.6), so a binding
     * that left one of those registries out fails the manifest instead of passing it silently.
     */
    private static final Registry COMBINED = XrImporter.defaultRegistry()
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
            "model/en16931/" + Registry.DEFAULT_EDITION + ".json";

    /** The extension registries of the repository, in the order a binding loads them. */
    private static final List<String> EXTENSION_REGISTRIES =
            List.of("model/xrechnung/3.0.2.json", "model/b2c/0.1.json");

    /** The registry of the later edition, under the key the repository files it by. */
    private static final String LATER_EDITION_REGISTRY =
            "model/en16931/" + LATER_EDITION + ".json";

    /** The rule pack, compiled once: it is evaluated over every mutation of the corpus. */
    private static final RuleEngine ENGINE = En16931.engine(COMBINED);

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
                        plain("42.015"), plain("-0.01"), plain(longestDecimal())),
                List.of(plain("100.00"), plain("007"), plain("1E2"), plain("1e-3"),
                        plain("+1"), plain("100."), plain(".5"), plain("-0"),
                        plain("-0.00"), plain("1 000"), plain("1,5"), plain("0x10"),
                        plain(tooLongDecimal()))));
        grammars.add(new Grammar(SemanticType.DATE, "ESJ-L2-DATE", "/BT-2",
                List.of(plain("2026-01-15"), plain("2024-02-29"), plain("1000-01-01"),
                        plain("9999-12-31")),
                List.of(plain("2026-02-30"), plain("2023-02-29"), plain("0999-12-31"),
                        plain("2026-1-5"), plain("20260115"), plain("2026-01-15Z"),
                        plain("2026-13-01"), plain("2026-01-32"))));
        grammars.add(new Grammar(SemanticType.BINARY_OBJECT, "ESJ-L2-BASE64",
                "/BG-24/0/BT-125",
                List.of(attachment("QUJDRQ=="), attachment("QUJD"), attachment("QQ==")),
                List.of(attachment("QUJDRR=="), attachment("QUJDRQ"), attachment("QUJD RQ=="),
                        attachment("QUJDR-=="), attachment("QUJDRQ==="))));
        return List.copyOf(grammars);
    }

    /**
     * The value grammar of the later edition, which is the one type that edition adds. It
     * belongs to the part file, because a distribution without that edition carries neither
     * the registry that gives a term the type nor a document to measure it in.
     */
    private static Grammar laterEditionGrammar() {
        return new Grammar(SemanticType.TIME, "ESJ-L2-TIME", "/BT-166",
                List.of(plain("09:15:00Z"), plain("00:00:00Z"), plain("23:59:59+14:00"),
                        plain("12:00:00-05:30"), plain("12:00:00+01:00")),
                List.of(plain("09:15:00"), plain("09:15:00+00:00"), plain("09:15:00-00:00"),
                        plain("9:15:00Z"), plain("09:15Z"), plain("24:00:00Z"),
                        plain("23:59:60Z"), plain("09:15:00.5Z"), plain("09:15:00z"),
                        plain("09:15:00+15:00")));
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
     * implementation; this holds them to the schema. The part of the later edition is
     * checked where the build carries it.
     */
    @Test
    void everyFileOfTheManifestHasTheShapeOfTheSchema() {
        Schema schema = schema();
        List<String> files = new ArrayList<>(List.of(MANIFEST, CASES));
        if (theLaterEditionIsThere()) {
            files.add(LATER_EDITION_PART);
        }
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
     * in the same sentence that one document and one rejected row come from the part a
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
            assertCount(page, text, " conformant documents", documents);
            assertCount(page, text, " rows for the documents that have to be rejected",
                    invalid);
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
            invalidRows(file).forEach(invalid::add);
        }
        manifest.put("invalid", invalid);

        manifest.put("canonicalOrder", canonicalOrder());

        Manifest.Array tables = Manifest.array();
        for (Grammar grammar : grammars()) {
            tables.add(grammar(grammar, GRAMMAR_BASE, COMBINED));
        }
        manifest.put("grammars", tables);

        manifest.put("rules", Manifest.object()
                .put("pack", En16931.PACK_ID + "/" + En16931.VERSION)
                .put("directory", "rules/" + En16931.PACK_ID + "/" + En16931.VERSION)
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
            invalidRows(file).forEach(invalid::add);
        }
        part.put("invalid", invalid);

        Manifest.Array tables = Manifest.array();
        tables.add(grammar(laterEditionGrammar(), laterEditionDocuments().get(0), registry));
        part.put("grammars", tables);
        return part;
    }

    /** Builds the rule cases: every mutation of the corpus, in the form a binding reads. */
    private Manifest.Object cases() {
        Manifest.Object file = Manifest.object()
                .put("format", FORMAT + "-Rules")
                .put("version", CONTRACT_VERSION)
                .put("pack", En16931.PACK_ID + "/" + En16931.VERSION)
                .put("directory", "rules/" + En16931.PACK_ID + "/" + En16931.VERSION);

        StreamingReader streaming = new StreamingReader(ReaderOptions.builder()
                .registry(COMBINED)
                .build());
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
                .add(registry(CORE_REGISTRY, Registry.forEdition(Registry.DEFAULT_EDITION)))
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
        if (COMBINED.describes(document.semanticModel())) {
            files.add(Manifest.of(CORE_REGISTRY));
            EXTENSION_REGISTRIES.forEach(file -> files.add(Manifest.of(file)));
            return files;
        }
        return files.add(Manifest.of(LATER_EDITION_REGISTRY));
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
        SemanticDocument document = reader.read(Fixtures.bytes(file));
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
                .putUnlessEmpty("findings", findings(document));
    }

    /** The errors the three layers report, in the order the validator reports them. */
    private Manifest.Array findings(SemanticDocument document) {
        Manifest.Array array = Manifest.array();
        for (Finding finding : StructuralValidator
                .validate(document, registryFor(document), MODEL_AND_CARDINALITY).findings()) {
            if (finding.isError()) {
                Manifest.Object entry = Manifest.object()
                        .put("path", finding.path().toString())
                        .put("code", finding.code().code());
                if (!finding.subject().isEmpty()) {
                    entry.put("subject", finding.subject());
                }
                array.add(entry);
            }
        }
        return array;
    }

    /**
     * One negative fixture, as one row per code it draws: the layer that catches the defect,
     * the code and the path the finding names.
     *
     * <p>The reader is asked for its findings rather than for the exception it would raise,
     * because the specification, section 9.5 fixes what {@code path} points at for every
     * code and an exception carries the member access alone. A binding whose reader named
     * the document root where the defect has an address would otherwise pass this manifest.
     *
     * <p>A document may be wrong in two ways at layer L1 and draw two codes: section 9.6
     * lets {@code ESJ-L1-SURROGATE} stand beside the code a value object's shape or member
     * set draws, because the two checks read two different strings. Every error the reader
     * reports is therefore a row of its own, so that a binding which reports one of the two
     * and not the other is caught. The model layers answer one code per fixture here: what
     * a validator reports over a whole document is the business of the {@code documents}
     * part.
     *
     * @param file the fixture, as a path in the repository
     * @return its rows, in the order the findings were reported
     */
    private List<Manifest.Object> invalidRows(String file) {
        byte[] bytes = Fixtures.bytes(file);
        ReadResult read = reader.readWithFindings(bytes);
        if (!read.isWellFormed()) {
            return read.findings().stream().filter(Finding::isError)
                    .map(finding -> Manifest.object().put("file", file)
                            .put("layer", finding.code() == FindingCode.ESJ_L1_LIMIT
                                    ? "limit" : finding.code().layer().name())
                            .put("code", finding.code().code())
                            .put("path", finding.path().toString()))
                    .toList();
        }
        SemanticDocument document = read.document().orElseThrow();
        List<Finding> errors = StructuralValidator
                .validate(document, registryFor(document), MODEL_AND_CARDINALITY)
                .findings().stream().filter(Finding::isError).toList();
        if (errors.isEmpty()) {
            return List.of(Manifest.object().put("file", file).put("layer", "business-rule"));
        }
        Finding first = errors.get(0);
        return List.of(Manifest.object().put("file", file)
                .put("layer", first.code().layer().name())
                .put("code", first.code().code())
                .put("path", first.path().toString()));
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
        array.add(canonicalOrder(DIRECTORY + "canonical-order/indices.esj.json",
                DIRECTORY + "canonical-order/indices.canonical.esj.json"));
        return array;
    }

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
            if (!warningsOnly || finding.severity() == RuleSeverity.WARNING) {
                codes.add(finding.code());
            }
        }
        return List.copyOf(codes);
    }

    private static Registry registryFor(SemanticDocument document) {
        if (COMBINED.describes(document.semanticModel())) {
            return COMBINED;
        }
        return Registry.forSemanticModel(document.semanticModel()).orElseThrow(
                () -> new AssertionError("no registry for " + document.semanticModel()));
    }

    /** The canonical form beside a document, where the repository carries one. */
    private static String canonicalFileOf(String file) {
        String canonical = file.substring(0, file.length() - ".esj.json".length())
                + ".canonical.esj.json";
        return resource(canonical) == null ? null : canonical;
    }

    // ----------------------------------------------------------------- the files

    /** The documents of the default edition: the examples and the conformance corpus. */
    private static List<String> coreDocuments() {
        List<String> files = new ArrayList<>(documentsIn("examples"));
        files.removeIf(FixtureManifestTest::belongsToTheLaterEdition);
        files.addAll(documentsIn("conformance/esj"));
        return List.copyOf(files);
    }

    private static List<String> laterEditionDocuments() {
        return documentsIn("examples").stream()
                .filter(FixtureManifestTest::belongsToTheLaterEdition)
                .toList();
    }

    private static List<String> invalidFixtures(boolean laterEdition) {
        return documentsIn("examples/invalid").stream()
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
        files.add(DIRECTORY + "canonical-order/indices.esj.json");
        files.add(DIRECTORY + "canonical-order/indices.canonical.esj.json");
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
        return Registry.editions().contains(LATER_EDITION);
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
