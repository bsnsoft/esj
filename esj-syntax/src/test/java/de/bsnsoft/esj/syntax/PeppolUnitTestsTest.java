package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.xr.XmlFrontDoor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OpenPeppol's own Schematron unit tests, run through the pack {@code esj packs fetch}
 * makes: the evidence that the fetched and compiled rule sets say what their publisher's
 * build says they say.
 *
 * <p>Each file of {@code rules/unit-UBL-PEPPOL/} and {@code rules/unit-CII-PEPPOL/} is a test
 * set of the VEFA validator format: a configuration, and tests that each hold a document —
 * mostly a fragment, not a complete invoice — and the rules it expects to fire as an error or
 * a warning, or not to fire ({@code success}), sometimes with the number of times. The
 * configuration names the rule sets the publisher's build runs: {@code base-3.0} the Peppol
 * rule set of the syntax alone, the profile {@code 01} the EN 16931 rule set with it. Those
 * rule sets are run here over each document as the syntax engine runs them, without the XML
 * Schema in front of them, because a fragment is not a document the schema admits and the
 * publisher's tests do not ask about the schema.
 *
 * <p>The result is recorded per rule identifier, as the ledger of {@code conformance/syntax/}
 * records the CEN rules: a rule is identical where every expectation of every test about it
 * holds, and differing otherwise. The figures and every difference are in
 * {@code conformance/peppol/README.md}; the test holds them.
 */
class PeppolUnitTestsTest {

    private static final String VEFA = "http://difi.no/xsd/vefa/validator/1.0";

    /**
     * One row of the rule table of the README: identifier, then for each release of
     * {@link PeppolEvidence#RECIPES}, in that order, the expectations and the result, or a
     * dash where the release has no test about the rule.
     */
    private static final Pattern LEDGER_ROW = Pattern.compile(
            "^\\| `([A-Z0-9-]+)` \\| ([^|]+?) \\| ([^|]+?) \\|$", Pattern.MULTILINE);

    /** One cell of that table. */
    private static final Pattern CELL = Pattern.compile("(\\d+) (identical|differing)");

    /** What each release's run reads and runs: test sets, empty ones, tests, expectations, rules. */
    private static final Map<String, List<Integer>> COUNTS = Map.of(
            "peppol-bis-billing-3.0.20", List.of(102, 2, 354, 354, 58),
            "peppol-bis-billing-3.0.21", List.of(110, 2, 493, 493, 62));

    /** The rule sets each configuration of the test sets runs, by component name. */
    private static final Map<String, List<String>> CONFIGURATIONS = Map.of(
            "peppolbis-en16931-base-3.0-ubl", List.of("peppol-ubl-schematron"),
            "peppolbis-en16931-base-3.0-cii", List.of("peppol-cii-schematron"),
            "peppolbis-en16931-01-3.0-ubl-invoice",
            List.of("en16931-ubl-schematron", "peppol-ubl-schematron"));

    /**
     * The rules whose expectations do not all hold, each with the reason. Empty where the
     * compiled rule sets answer every test the way the publisher's tests expect.
     */
    private static final Map<String, String> DIFFERING = Map.of();

    @TempDir
    Path packs;

    @ParameterizedTest
    @ValueSource(strings = {"peppol-bis-billing-3.0.20", "peppol-bis-billing-3.0.21"})
    void theFetchedRuleSetsFireWhatOpenPeppolsOwnTestsExpect(String recipe) throws IOException {
        PeppolEvidence.assumeNetwork();
        assertTrue(PeppolEvidence.RECIPES.contains(recipe));
        long start = System.nanoTime();
        PackFetcher.Result fetched = PackFetcher.fetch(PackRecipes.named(recipe),
                packs, false, PeppolEvidence.download(), LocalDate.now(ZoneOffset.UTC),
                "esj test");
        Duration fetchAndCompile = Duration.ofNanos(System.nanoTime() - start);
        Pack pack = fetched.pack();

        List<String> paths = PeppolEvidence.files(recipe).keySet().stream()
                .filter(path -> path.startsWith("rules/unit-UBL-PEPPOL/")
                        || path.startsWith("rules/unit-CII-PEPPOL/"))
                .toList();
        long fetchStart = System.nanoTime();
        // A hundred small files, each its own request: fetched side by side, they cost what
        // the slowest of them costs rather than the sum.
        paths.parallelStream().forEach(path -> PeppolEvidence.fetch(recipe, path));
        Duration fetchTests = Duration.ofNanos(System.nanoTime() - fetchStart);

        Map<String, Rule> rules = new TreeMap<>();
        int testSets = 0;
        int empty = 0;
        int tests = 0;
        int expectations = 0;
        List<String> failures = new ArrayList<>();
        long runStart = System.nanoTime();
        for (String path : paths) {
            XdmNode testSet = XmlFrontDoor.rootElement(
                    XmlFrontDoor.parse(PeppolEvidence.fetch(recipe, path)));
            List<String> components = CONFIGURATIONS.get(testSet.attribute("configuration"));
            assertTrue(components != null, path + " names a configuration this test knows: "
                    + testSet.attribute("configuration"));
            testSets++;
            if (children(testSet, "test").isEmpty()) {
                // Two files of the publisher hold their tests in a comment ("Rule is
                // commented out"): a test set with nothing to run.
                empty++;
            }
            int index = 0;
            for (XdmNode test : children(testSet, "test")) {
                index++;
                tests++;
                Map<String, List<Severity>> fired = fire(pack, components, document(test));
                for (XdmNode expected : children(child(test, "assert"), null)) {
                    String kind = expected.getNodeName().getLocalName();
                    if (kind.equals("description")) {
                        continue;
                    }
                    expectations++;
                    String code = expected.getStringValue().trim();
                    String number = expected.attribute("number");
                    boolean holds = holds(kind, number, fired.getOrDefault(code, List.of()));
                    Rule rule = rules.computeIfAbsent(code, Rule::new);
                    rule.expectations++;
                    if (!holds) {
                        rule.failed++;
                        failures.add(path + " test " + index + ": expected " + kind
                                + (number == null ? "" : " x" + number) + " " + code
                                + ", fired " + fired.getOrDefault(code, List.of()));
                    }
                }
            }
        }
        Duration run = Duration.ofNanos(System.nanoTime() - runStart);

        List<String> differing = rules.values().stream().filter(rule -> rule.failed > 0)
                .map(rule -> rule.code).toList();
        write(recipe, testSets, empty, tests, expectations, rules, failures, fetchAndCompile,
                fetchTests, run);
        List<Integer> counts = COUNTS.get(recipe);
        assertEquals(counts.get(0), testSets, "every test set of the two directories was read");
        assertEquals(counts.get(1), empty, "test sets that hold no test");
        assertEquals(counts.get(2), tests, "the tests of the others were run");
        assertEquals(counts.get(3), expectations);
        assertEquals(counts.get(4), rules.size(), "the rules those tests are about");
        assertEquals(DIFFERING.keySet().stream().sorted().toList(), differing,
                "the rules whose expectations do not all hold:\n"
                        + String.join("\n", failures));
        assertFalse(rules.isEmpty());
        Map<String, String> measured = new TreeMap<>();
        rules.values().forEach(rule -> measured.put(rule.code, rule.expectations + " "
                + (rule.failed == 0 ? "identical" : "differing")));
        assertEquals(recorded(recipe), measured,
                "conformance/peppol/README.md records every rule with its expectations");
        assertEquals(6, pack.components().size(), "two schema sets and four rule sets");
        assertEquals(30, pack.files().size(), "the files the pack writes besides its manifest");
    }

    /**
     * Returns the rules the table of {@code conformance/peppol/README.md} records for one
     * release.
     */
    private static Map<String, String> recorded(String recipe) {
        int column = PeppolEvidence.RECIPES.indexOf(recipe);
        Map<String, String> recorded = new TreeMap<>();
        Matcher row = LEDGER_ROW.matcher(Corpus.text("/conformance/peppol/README.md"));
        while (row.find()) {
            String cell = row.group(2 + column).trim();
            Matcher matcher = CELL.matcher(cell);
            if (matcher.matches()) {
                recorded.put(row.group(1), matcher.group(1) + " " + matcher.group(2));
            } else {
                assertEquals("—", cell, "a cell of the rule table is a count and a result,"
                        + " or a dash");
            }
        }
        return recorded;
    }

    /** Runs the rule sets of a configuration over one document. */
    private static Map<String, List<Severity>> fire(Pack pack, List<String> components,
                                                    XdmNode document) {
        Map<String, List<Severity>> fired = new LinkedHashMap<>();
        for (String name : components) {
            PackComponent component = pack.components().stream()
                    .filter(candidate -> candidate.name().equals(name))
                    .findFirst().orElseThrow();
            String entry = component.entries().values().iterator().next();
            SchematronCheck.Result result = SchematronCheck.run(document, pack, component,
                    entry, Optional.empty(), Budget.of(Duration.ofMinutes(5)));
            for (SyntaxFinding finding : result.findings()) {
                fired.computeIfAbsent(finding.code(), code -> new ArrayList<>())
                        .add(finding.flag());
            }
        }
        return fired;
    }

    /** Tells whether one expectation of a test holds over what fired. */
    private static boolean holds(String kind, String number, List<Severity> fired) {
        return switch (kind) {
            case "success" -> fired.isEmpty();
            case "error", "warning" -> {
                Severity level = kind.equals("error") ? Severity.FATAL : Severity.WARNING;
                long count = fired.stream().filter(flag -> flag == level).count();
                yield number == null ? count > 0 : count == Long.parseLong(number.trim());
            }
            default -> throw new AssertionError("an expectation this test does not know: "
                    + kind);
        };
    }

    /**
     * Returns the document of a test as a document of its own, so that the rule sets see
     * its element as the root, as the publisher's build does.
     */
    private static XdmNode document(XdmNode test) {
        XdmNode element = null;
        for (XdmNode child : test.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT
                    && !VEFA.equals(child.getNodeName().getNamespace())) {
                element = child;
                break;
            }
        }
        assertTrue(element != null, "a test holds a document");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Serializer serializer = XmlFrontDoor.processor().newSerializer(bytes);
        serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
        try {
            serializer.serializeNode(element);
        } catch (SaxonApiException e) {
            throw new AssertionError(e);
        }
        return XmlFrontDoor.parse(bytes.toByteArray());
    }

    private static List<XdmNode> children(XdmNode parent, String name) {
        List<XdmNode> children = new ArrayList<>();
        for (XdmNode child : parent.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT
                    && VEFA.equals(child.getNodeName().getNamespace())
                    && (name == null || child.getNodeName().getLocalName().equals(name))) {
                children.add(child);
            }
        }
        return children;
    }

    private static XdmNode child(XdmNode parent, String name) {
        List<XdmNode> found = children(parent, name);
        assertEquals(1, found.size(), "one " + name + " in a test");
        return found.get(0);
    }

    /** Writes the ledger of this run where the build keeps what it made. */
    private static void write(String recipe, int testSets, int empty, int tests, int expectations,
                              Map<String, Rule> rules, List<String> failures,
                              Duration fetchAndCompile, Duration fetchTests, Duration run)
            throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("test sets ").append(testSets).append(" (").append(empty)
                .append(" without a test), tests ").append(tests)
                .append(", expectations ").append(expectations).append(", rules ")
                .append(rules.size()).append('\n');
        long identical = rules.values().stream().filter(rule -> rule.failed == 0).count();
        text.append("identical ").append(identical).append(", differing ")
                .append(rules.size() - identical).append('\n');
        text.append("fetch and compile the pack ").append(fetchAndCompile.toMillis())
                .append(" ms, fetch the test sets ").append(fetchTests.toMillis())
                .append(" ms, run them ").append(run.toMillis()).append(" ms\n\n");
        for (Rule rule : rules.values()) {
            text.append(rule.code).append(' ').append(rule.failed == 0 ? "identical"
                    : "differing").append(" (").append(rule.expectations)
                    .append(" expectations").append(rule.failed == 0 ? ""
                            : ", " + rule.failed + " not met").append(")\n");
        }
        text.append('\n');
        failures.forEach(failure -> text.append(failure).append('\n'));
        Path out = Path.of("target", "peppol-evidence");
        Files.createDirectories(out);
        Files.writeString(out.resolve("unit-tests-" + recipe + ".txt"), text.toString(),
                StandardCharsets.UTF_8);
        System.out.println(text);
    }

    /** What the tests expect of one rule, and how much of it holds. */
    private static final class Rule {

        private final String code;
        private int expectations;
        private int failed;

        private Rule(String code) {
            this.code = code;
        }
    }
}
