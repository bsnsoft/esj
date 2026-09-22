package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs every instance of the conformance corpus through the whole engine and compares the
 * answer with the one written down in {@code conformance/syntax/ledger.json}.
 *
 * <p>The ledger is a golden file: not "there were no findings", which would say nothing
 * about whether anything ran, but every finding of every instance with the component that
 * made it, both levels it carries and the location it names. A finding that appears,
 * disappears or changes one of its levels is a change in what the official rules say
 * about a document, and this test stops the build until the new answer has been looked at
 * and written down. The README beside the ledger says what the artefacts make of the
 * corpus, and {@code ledger.md} what the official validator made of the same documents.
 */
class SyntaxCorpusTest {

    private static final Map<String, Expected> LEDGER = ledger();

    static List<String> corpus() {
        return Corpus.instances();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void answersWhatTheLedgerRecords(String instance) {
        SyntaxReport report = SyntaxValidator.validate(Corpus.instance(instance));
        Expected expected = LEDGER.get(instance);
        assertTrue(expected != null, "conformance/syntax/ledger.json records " + instance);

        assertEquals(expected.syntax(), report.syntax().orElseThrow().name(),
                instance + " is read as the syntax the ledger records");
        assertEquals(expected.profile(), report.customizationId(),
                instance + " names the profile the ledger records");
        assertEquals(expected.verdict(), report.verdict().name(),
                instance + " has the verdict the ledger records");
        assertEquals(expected.findings(), lines(report),
                instance + " produces the findings the ledger records");
        assertEquals(report.findings().size(),
                report.fatal().size() + report.warnings().size(),
                instance + ": every finding either decides the verdict or is listed beside"
                        + " it");
        assertEquals(expected.verdict().equals("VALID"), report.fatal().isEmpty(),
                instance + ": the verdict is valid exactly when no finding is fatal");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void passesTheSchemaOfItsSyntax(String instance) {
        SyntaxReport report = SyntaxValidator.validate(Corpus.instance(instance));

        assertEquals(List.of(), report.findings(Engine.XSD),
                instance + " is valid against the schema modules of its syntax");
        assertEquals(List.of(), report.findings(Engine.PARSER),
                instance + " is XML the front door of this project reads");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void runsBothRuleSetsOverIt(String instance) {
        SyntaxReport report = SyntaxValidator.validate(Corpus.instance(instance));

        List<String> ran = report.ran().stream().map(ComponentRun::component).toList();
        assertEquals(3, ran.size(), instance + " is checked by the schema and both rule"
                + " sets, and this run did " + ran);
        assertTrue(ran.stream().anyMatch(name -> name.endsWith("-xsd")),
                instance + " is checked against a schema; " + ran);
        assertTrue(ran.stream().filter(name -> name.contains("schematron")).count() == 2,
                instance + " is checked against the EN 16931 rules and the rules of the"
                        + " specification it names; " + ran);
        assertTrue(report.profileNote().isEmpty(),
                instance + " names a profile the pack recognizes");
    }

    /** One finding of the ledger as one line, which is what a failure prints. */
    private static List<String> lines(SyntaxReport report) {
        List<String> lines = new ArrayList<>();
        for (SyntaxFinding finding : report.findings()) {
            lines.add(line(finding.engine().token(), finding.component(),
                    finding.category().label(), finding.severity().token(),
                    finding.flag().token(), finding.code(), finding.location()));
        }
        return lines;
    }

    private static String line(String engine, String component, String category,
                               String severity, String flag, String code,
                               String location) {
        return engine + " " + component + " " + category + " " + severity + " (flag "
                + flag + ") " + code + " at " + location;
    }

    /** What the ledger records for one instance. */
    private record Expected(String syntax, String profile, String verdict,
                            List<String> findings) {
    }

    private static Map<String, Expected> ledger() {
        Object root = PackJson.read(Corpus.bytes("/conformance/syntax/ledger.json"),
                "conformance/syntax/ledger.json");
        Map<String, Expected> recorded = new LinkedHashMap<>();
        Map<?, ?> document = (Map<?, ?>) root;
        assertEquals(Packs.bundled().get(0).directory(), document.get("pack"),
                "the ledger was taken down with the pack this module carries");
        for (Object element : (List<?>) document.get("instances")) {
            Map<?, ?> entry = (Map<?, ?>) element;
            List<String> findings = new ArrayList<>();
            for (Object found : (List<?>) entry.get("findings")) {
                Map<?, ?> finding = (Map<?, ?>) found;
                findings.add(line(text(finding, "engine"), text(finding, "component"),
                        text(finding, "category"), text(finding, "severity"),
                        text(finding, "flag"), text(finding, "code"),
                        text(finding, "location")));
            }
            recorded.put(text(entry, "instance"), new Expected(text(entry, "syntax"),
                    text(entry, "profile"), text(entry, "verdict"), findings));
        }
        return recorded;
    }

    private static String text(Map<?, ?> object, String member) {
        return (String) object.get(member);
    }
}
