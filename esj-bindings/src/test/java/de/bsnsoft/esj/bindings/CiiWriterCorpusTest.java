package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.syntax.Engine;
import de.bsnsoft.esj.syntax.SyntaxFinding;
import de.bsnsoft.esj.syntax.SyntaxValidator;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The writer over the whole corpus and over the examples, against the official validation
 * artefacts and against itself.
 *
 * <p>This is the measurement {@code conformance/writers/cii-roundtrip.md} describes and
 * {@code cii-roundtrip.json} records, and the test does not trust either of them: it writes
 * every document again, validates every result with the pack, reads every result back and
 * compares it with what it was written from, and fails unless every fatal finding is one the
 * report names, every differing path is one the report classifies, and every count is the
 * recorded one. A document that starts to validate fails as loudly as one that stops.
 */
class CiiWriterCorpusTest {

    private static final String REPORT = Corpus.ROOT + "writers/cii-roundtrip.json";

    /**
     * Every corpus instance read, written as a cross industry invoice, and put through the
     * validation artefacts of its profile.
     */
    @Test
    void whatTheValidationArtefactsSayIsWhatTheReportSays() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        Map<String, Set<String>> byRule = new TreeMap<>();
        int accepted = 0;
        int fromCii = 0;
        StreamingReader reader = new StreamingReader();
        for (String instance : Corpus.corpus()) {
            if (instance.contains("uncefact")) {
                fromCii++;
            }
            SemanticDocument source = reader.read(Corpus.instance(instance)).document();
            List<SyntaxFinding> fatal =
                    SyntaxValidator.validate(CiiWriter.write(source)).fatal();
            if (fatal.isEmpty()) {
                accepted++;
            }
            for (SyntaxFinding finding : fatal) {
                byRule.computeIfAbsent(finding.code(), key -> new LinkedHashSet<>())
                        .add(instance);
            }
        }
        Map<String, Object> corpus = Reports.object(report.get("corpus"));
        assertEquals(Corpus.corpus().size(), Reports.number(corpus.get("instances")));
        assertEquals(fromCii, Reports.number(corpus.get("fromCii")));
        assertEquals(Corpus.corpus().size() - fromCii, Reports.number(corpus.get("fromUbl")));
        assertEquals(Reports.number(corpus.get("accepted")), accepted,
                "instances the validation artefacts report nothing fatal on");
        assertEquals(Reports.number(corpus.get("refused")),
                Corpus.corpus().size() - accepted);

        Map<String, Integer> recorded = new TreeMap<>();
        for (Object entry : Reports.array(report.get("refusals"))) {
            Map<String, Object> refusal = Reports.object(entry);
            recorded.put(Reports.text(refusal.get("rule")),
                    Reports.number(refusal.get("instances")));
        }
        assertEquals(recorded.keySet(), byRule.keySet(),
                "the rules the corpus makes fire on what the writer produced");
        for (Map.Entry<String, Set<String>> fired : byRule.entrySet()) {
            assertEquals(recorded.get(fired.getKey()), fired.getValue().size(),
                    "instances that make " + fired.getKey() + " fire: " + fired.getValue());
        }
    }

    /**
     * Every corpus instance written and read back, against the document it was written
     * from. A document that came from a cross industry invoice has to come back unchanged;
     * one that came from UBL may lose what this syntax has no place for, and the report
     * says which paths those are.
     */
    @Test
    void everyDifferingPathIsOneTheReportExplains() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        List<Loss> losses = losses(report);
        Map<String, Integer> paths = new LinkedHashMap<>();
        Map<String, Set<String>> instances = new LinkedHashMap<>();
        int identical = 0;
        int differingPaths = 0;
        StreamingReader reader = new StreamingReader();
        for (String instance : Corpus.corpus()) {
            SemanticDocument source = reader.read(Corpus.instance(instance)).document();
            WriteResult result = CiiWriter.writeWithReport(source, WriterOptions.defaults());
            SemanticDocument back = reader.read(result.xml()).document();
            List<String> differences = differences(source, back);
            if (differences.isEmpty()) {
                assertTrue(result.report().isComplete(), instance + ": a document that comes"
                        + " back unchanged lost nothing, so the report says so");
                identical++;
                continue;
            }
            assertFalse(instance.contains("uncefact"), instance + ": a document that came"
                    + " from this syntax goes back into it unchanged, and these paths"
                    + " differ: " + differences);
            differingPaths += differences.size();
            for (String path : differences) {
                Loss loss = classify(losses, path, instance);
                paths.merge(loss.id(), 1, Integer::sum);
                instances.computeIfAbsent(loss.id(), key -> new LinkedHashSet<>())
                        .add(instance);
                assertTrue(result.report().notes().stream()
                                .anyMatch(note -> note.path().equals(path)
                                        && note.kind().name().equals(loss.note())),
                        instance + ": " + path + " is missing and the writer's report does"
                                + " not say so");
            }
        }
        Map<String, Object> corpus = Reports.object(report.get("corpus"));
        assertEquals(Reports.number(corpus.get("identical")), identical);
        assertEquals(Reports.number(corpus.get("differing")),
                Corpus.corpus().size() - identical);
        assertEquals(Reports.number(corpus.get("differingPaths")), differingPaths);
        for (Loss loss : losses) {
            assertEquals(loss.paths(), paths.getOrDefault(loss.id(), 0),
                    "paths lost to " + loss.id());
            assertEquals(loss.instances(), instances.getOrDefault(loss.id(), Set.of()).size(),
                    "instances losing something to " + loss.id());
        }
    }

    /**
     * The same measurement over the documents the XSLT path of {@code esj-xr} builds from
     * the same corpus, which is the second column of the report. The two readers do not
     * hand the writer the same documents — {@code conformance/readers.md} says where they
     * differ — and a page that gave one of the two figures would be choosing the
     * flattering one.
     */
    @Test
    void theDocumentsTheXsltPathBuildsMeasureTheSame() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        Map<String, Object> xslt = Reports.object(report.get("xslt"));
        StreamingReader reader = new StreamingReader();
        XrImporter importer = new XrImporter();
        int accepted = 0;
        int identical = 0;
        for (String instance : Corpus.corpus()) {
            SemanticDocument source = importer.importXml(Corpus.instance(instance));
            byte[] xml = CiiWriter.write(source);
            if (SyntaxValidator.validate(xml).fatal().isEmpty()) {
                accepted++;
            }
            if (differences(source, reader.read(xml).document()).isEmpty()) {
                identical++;
            }
        }
        assertEquals(Reports.number(xslt.get("accepted")), accepted);
        assertEquals(Reports.number(xslt.get("identical")), identical);
    }

    /**
     * The ten examples of the 2017 edition this syntax holds whole. They are structurally
     * complete, and the three built from the mandatory terms alone do not satisfy the
     * business rules, so the parser and the schema modules have to be silent on every one of
     * them and the rules that fire are pinned by name.
     */
    @Test
    void everyExampleIsACrossIndustryInvoiceTheSchemaAccepts() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        Map<String, Object> examples = Reports.object(report.get("examples"));
        Map<String, Object> rules = Reports.object(report.get("exampleRules"));
        Map<String, Integer> fired = new TreeMap<>();
        StreamingReader reader = new StreamingReader();
        int structural = 0;
        int ruleClean = 0;
        int identical = 0;
        for (String name : Examples.names()) {
            SemanticDocument document = EsjReader.strict().read(Examples.bytes(name));
            byte[] xml = CiiWriter.write(document);
            List<SyntaxFinding> fatal = SyntaxValidator.validate(xml).fatal();
            if (fatal.stream().noneMatch(finding -> finding.engine() != Engine.SCHEMATRON)) {
                structural++;
            }
            if (fatal.isEmpty()) {
                ruleClean++;
            }
            for (SyntaxFinding finding : fatal) {
                fired.merge(finding.code(), 1, Integer::sum);
            }
            assertEquals(List.of(), differences(document, reader.read(xml).document()),
                    name + ": the example goes into this syntax and comes back unchanged");
            identical++;
        }
        assertEquals(Examples.names().size(), Reports.number(examples.get("count")));
        assertEquals(Reports.number(examples.get("structural")), structural,
                "examples the parser and the schema modules say nothing fatal about");
        assertEquals(Reports.number(examples.get("ruleClean")), ruleClean);
        assertEquals(Reports.number(examples.get("identical")), identical);
        assertEquals(new TreeSet<>(rules.keySet()), new TreeSet<>(fired.keySet()),
                "the rules the examples make fire");
        for (Map.Entry<String, Integer> entry : fired.entrySet()) {
            assertEquals(Reports.number(rules.get(entry.getKey())), entry.getValue(),
                    "findings of " + entry.getKey());
        }
    }

    /** The prose of the report carries the numbers the data carries. */
    @Test
    void theProseAndTheDataSayTheSame() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        Map<String, Object> corpus = Reports.object(report.get("corpus"));
        List<String> rows = Corpus.text(Corpus.ROOT + "writers/cii-roundtrip.md").lines()
                .filter(line -> line.startsWith("|")).toList();
        for (String measure : List.of("accepted", "refused", "identical", "differing")) {
            assertEquals(List.of(String.valueOf(Reports.number(corpus.get(measure)))),
                    cells(rows, "`" + measure + "`"),
                    "the summary table carries the number of " + measure + " instances");
        }
        assertEquals(List.of(String.valueOf(Reports.number(corpus.get("instances")))),
                cells(rows, "`total`"));
        for (Object entry : Reports.array(report.get("refusals"))) {
            Map<String, Object> refusal = Reports.object(entry);
            assertEquals(List.of(String.valueOf(Reports.number(refusal.get("instances"))),
                            "`" + Reports.text(refusal.get("cause")) + "`"),
                    cells(rows, "`" + Reports.text(refusal.get("rule")) + "`"),
                    "the table of refusals carries " + refusal.get("rule"));
        }
        for (Loss loss : losses(report)) {
            assertEquals(List.of("`" + loss.note() + "`", String.valueOf(loss.instances()),
                            String.valueOf(loss.paths())),
                    cells(rows, "`" + loss.id() + "`"),
                    "the table of losses carries the counts of " + loss.id());
        }
        Map<String, Object> examples = Reports.object(report.get("examples"));
        for (String measure : List.of("structural", "ruleClean")) {
            assertEquals(List.of(String.valueOf(Reports.number(examples.get(measure)))),
                    cells(rows, "`" + measure + "`"),
                    "the table of examples carries " + measure);
        }
    }

    /**
     * Returns the cells of the one table row whose first cell carries a token, the first
     * cell itself excluded.
     */
    private static List<String> cells(List<String> rows, String token) {
        List<String> found = null;
        for (String row : rows) {
            List<String> parts = new ArrayList<>(List.of(row.split("\\|")));
            parts.removeIf(String::isBlank);
            if (parts.isEmpty() || !parts.get(0).contains(token)) {
                continue;
            }
            if (found != null) {
                // The example table and the summary table both carry `identical`, and the
                // two rows say the same number about different documents.
                continue;
            }
            found = parts.subList(1, parts.size()).stream().map(String::strip).toList();
        }
        assertFalse(found == null, "no row of cii-roundtrip.md carries " + token);
        return found;
    }

    private static Loss classify(List<Loss> losses, String path, String instance) {
        Loss found = null;
        for (Loss loss : losses) {
            if (loss.pattern().matcher(path).matches()) {
                assertFalse(found != null, instance + ": " + path + " matches two causes");
                found = loss;
            }
        }
        assertFalse(found == null, instance + ": nothing in cii-roundtrip.md explains why "
                + path + " did not survive the round trip");
        return found;
    }

    /** Returns the semantic paths at which two documents disagree, in canonical order. */
    private static List<String> differences(SemanticDocument before, SemanticDocument after) {
        Map<SemanticPath, SemanticValue> a = before.values();
        Map<SemanticPath, SemanticValue> b = after.values();
        Set<SemanticPath> all = new TreeSet<>(a.keySet());
        all.addAll(b.keySet());
        List<String> differences = new ArrayList<>();
        for (SemanticPath path : all) {
            if (!Objects.equals(a.get(path), b.get(path))) {
                differences.add(path.toString());
            }
        }
        return differences;
    }

    private static List<Loss> losses(Map<String, Object> report) {
        List<Loss> losses = new ArrayList<>();
        for (Object entry : Reports.array(report.get("losses"))) {
            Map<String, Object> loss = Reports.object(entry);
            losses.add(new Loss(Reports.text(loss.get("id")), Reports.text(loss.get("note")),
                    Pattern.compile(Reports.text(loss.get("pathPattern"))),
                    Reports.number(loss.get("instances")),
                    Reports.number(loss.get("paths"))));
        }
        assertFalse(losses.isEmpty(), "the report names what the syntax has no place for");
        return losses;
    }

    /** One family of semantic paths the syntax has no place for. */
    private record Loss(String id, String note, Pattern pattern, int instances, int paths) { }
}
