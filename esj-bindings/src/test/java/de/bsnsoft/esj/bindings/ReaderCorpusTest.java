package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportResult;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The two readers over the same 86 invoices.
 *
 * <p>This is the comparison {@code conformance/readers.md} describes and
 * {@code conformance/readers.json} records. The test does not trust the report: it reads
 * every instance of the corpus twice, once with the streaming reader of this module and
 * once with the XSLT path of {@code esj-xr}, and fails unless every difference is one the
 * report classifies and the counts are the recorded ones. A difference that appears fails
 * as loudly as one that disappears, which is the point: the report is the record of what
 * the two readers do, and it is worth nothing if the code can move away from it quietly.
 *
 * <p>Both readers are run here rather than one of them being compared with a checked in
 * file, because the files under {@code conformance/esj/} are the output of the streaming
 * reader — it is the default of the command line tool — and comparing a reader with its
 * own output would compare nothing. That those files <em>are</em> its output is asserted
 * below, once, so that the goldens cannot drift away from the reader that writes them.
 */
class ReaderCorpusTest {

    private static final String REPORT = Corpus.ROOT + "readers.json";

    @Test
    void everyDifferenceIsOneTheReportExplains() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        List<Cause> causes = causes(report);
        Map<String, Integer> paths = new LinkedHashMap<>();
        Map<String, Set<String>> instances = new LinkedHashMap<>();
        int identical = 0;
        int differing = 0;
        int differingPaths = 0;

        StreamingReader reader = new StreamingReader();
        XrImporter importer = new XrImporter();
        for (String instance : Corpus.corpus()) {
            ImportResult result = reader.read(Corpus.instance(instance));
            SemanticDocument stylesheets = importer.importXml(Corpus.instance(instance));
            List<Difference> differences = compare(result.document(), stylesheets);
            if (differences.isEmpty()) {
                identical++;
                assertArrayEquals(Canonicalizer.canonicalBytes(stylesheets),
                        Canonicalizer.canonicalBytes(result.document()),
                        instance + ": the two readers agree on every value, so they agree"
                                + " on the canonical bytes");
                continue;
            }
            differing++;
            differingPaths += differences.size();
            for (Difference difference : differences) {
                Cause cause = classify(causes, difference, instance);
                paths.merge(cause.id(), 1, Integer::sum);
                instances.computeIfAbsent(cause.id(), key -> new LinkedHashSet<>())
                        .add(instance);
            }
        }

        Map<String, Object> corpus = Reports.object(report.get("corpus"));
        assertEquals(Corpus.corpus().size(), Reports.number(corpus.get("instances")));
        assertEquals(identical, Reports.number(corpus.get("identical")),
                "instances the two readers agree on byte for byte");
        assertEquals(differing, Reports.number(corpus.get("differing")));
        assertEquals(differingPaths, Reports.number(corpus.get("differingPaths")));
        for (Cause cause : causes) {
            assertEquals(cause.paths(), paths.getOrDefault(cause.id(), 0),
                    "paths with the cause " + cause.id());
            assertEquals(cause.instances(),
                    instances.getOrDefault(cause.id(), Set.of()).size(),
                    "instances with the cause " + cause.id());
        }
    }

    /**
     * The one difference between the two readers that no instance of the corpus reaches,
     * on a document written for it.
     *
     * <p>UBL requires {@code cac:OrderReference/cbc:ID} as soon as BT-14 is written, and
     * the {@code conventions} member of the binding table says what stands there where a
     * document states no purchase order reference. The streaming reader knows that and
     * leaves BT-13 out; the vendored stylesheets read the conventional value as BT-13,
     * which is a reference nobody made.
     */
    @Test
    void everyDifferenceBeyondTheCorpusIsOneTheReportRecords() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        byte[] document = Documents.ubl("<cac:OrderReference><cbc:ID>NA</cbc:ID>"
                + "<cbc:SalesOrderID>SO-1</cbc:SalesOrderID></cac:OrderReference>");
        List<Difference> differences =
                compare(new StreamingReader().read(document).document(),
                        new XrImporter().importXml(document));
        List<?> recorded = Reports.array(report.get("beyondTheCorpus"));
        assertEquals(recorded.size(), differences.size(), differences.toString());
        for (int at = 0; at < recorded.size(); at++) {
            Map<String, Object> entry = Reports.object(recorded.get(at));
            assertEquals(Reports.text(entry.get("path")), differences.get(at).path());
            assertEquals(Reports.text(entry.get("side")), differences.get(at).side());
            assertEquals("overcome", Reports.text(entry.get("kind")),
                    "the streaming reader is the one that is right here");
        }
    }

    /**
     * The files under {@code conformance/esj/} are what this reader writes. They are the
     * project's statement of what each instance of the corpus is as a semantic document,
     * and the reader that writes them is the one the command line tool reads with by
     * default, so this is the assertion that keeps the two together: the pretty form of
     * what the reader builds, byte for byte.
     */
    @Test
    void theCheckedInDocumentsAreWhatThisReaderWrites() {
        StreamingReader reader = new StreamingReader();
        for (String instance : Corpus.corpus()) {
            SemanticDocument document = reader.read(Corpus.instance(instance)).document();
            assertArrayEquals(Corpus.esj(instance),
                    EsjWriter.pretty().toBytes(document),
                    instance + ": conformance/esj carries what the streaming reader builds");
        }
    }

    @Test
    void writesADocumentTheValidatorAccepts() {
        StreamingReader reader = new StreamingReader();
        for (String instance : Corpus.corpus()) {
            SemanticDocument document = reader.read(Corpus.instance(instance)).document();
            List<Finding> findings = StructuralValidator.validate(document,
                    reader.options().registry(), Set.of(ValidationLayer.L2)).findings();
            assertTrue(findings.isEmpty(), instance + ": " + findings);
        }
    }

    /**
     * What the streaming reader has to say about the corpus beyond the values. The XSLT
     * path reports 89 dropped supplementary components and nothing else; this reader reads
     * no component the standard does not give its term, so it reports none of those, and
     * it does report every place where a syntax element repeats at a term the semantic
     * model allows once — content of the source that no semantic document can hold.
     */
    @Test
    void reportsWhatDidNotReachTheDocument() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        Map<String, Integer> occurrences = new TreeMap<>();
        Map<String, Set<String>> instances = new TreeMap<>();
        StreamingReader reader = new StreamingReader();
        for (String instance : Corpus.corpus()) {
            String syntax = instance.contains("uncefact") ? "CII" : "UBL";
            for (ImportNote note : reader.read(Corpus.instance(instance)).report().notes()) {
                String where = note.kind() + " " + note.location();
                occurrences.merge(where, 1, Integer::sum);
                occurrences.merge(where + " " + syntax, 1, Integer::sum);
                instances.computeIfAbsent(where, key -> new TreeSet<>()).add(instance);
                instances.computeIfAbsent(where + " " + syntax, key -> new TreeSet<>())
                        .add(instance);
            }
        }
        Map<String, Object> recorded = Reports.object(report.get("notes"));
        assertEquals(new TreeSet<>(recorded.keySet()),
                new TreeSet<>(instances.keySet().stream()
                        .filter(where -> !where.endsWith(" CII") && !where.endsWith(" UBL"))
                        .toList()),
                "the kinds and places the reader reports over the corpus");
        for (Map.Entry<String, Object> entry : recorded.entrySet()) {
            String where = entry.getKey();
            Map<String, Object> counts = Reports.object(entry.getValue());
            assertEquals(Reports.number(counts.get("occurrences")),
                    occurrences.getOrDefault(where, 0), "occurrences at " + where);
            assertEquals(Reports.number(counts.get("instances")),
                    instances.getOrDefault(where, Set.of()).size(), "instances at " + where);
            for (Map.Entry<String, Object> syntax
                    : Reports.object(counts.get("bySyntax")).entrySet()) {
                String key = where + " " + syntax.getKey();
                Map<String, Object> split = Reports.object(syntax.getValue());
                assertEquals(Reports.number(split.get("occurrences")),
                        occurrences.getOrDefault(key, 0), "occurrences at " + key);
                assertEquals(Reports.number(split.get("instances")),
                        instances.getOrDefault(key, Set.of()).size(), "instances at " + key);
            }
        }
    }

    @Test
    void theProseAndTheDataSayTheSame() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        Map<String, Object> corpus = Reports.object(report.get("corpus"));
        List<String> rows = Corpus.text(Corpus.ROOT + "readers.md").lines()
                .filter(line -> line.startsWith("|")).toList();
        assertEquals(List.of(String.valueOf(Reports.number(corpus.get("identical")))),
                cells(rows, "`identical`"),
                "the summary table carries the number of identical instances");
        assertEquals(List.of(String.valueOf(Reports.number(corpus.get("differing")))),
                cells(rows, "`differing`"));
        assertEquals(List.of(String.valueOf(Reports.number(corpus.get("instances")))),
                cells(rows, "`total`"));
        for (Cause cause : causes(report)) {
            assertEquals(List.of(cause.kind(), String.valueOf(cause.instances()),
                            String.valueOf(cause.paths())),
                    cells(rows, "`" + cause.id() + "`"),
                    "the table of causes carries the counts of " + cause.id());
        }
        for (Object entry : Reports.array(report.get("beyondTheCorpus"))) {
            Map<String, Object> beyond = Reports.object(entry);
            assertEquals(List.of(Reports.text(beyond.get("kind")), "1"),
                    cells(rows, "`" + Reports.text(beyond.get("id")) + "`"),
                    "the table beyond the corpus carries " + beyond.get("id"));
        }
    }

    /**
     * Returns the cells of the one table row whose first cell carries a token, the first
     * cell itself excluded. A number in the prose that no longer matches the data is a
     * page that says something the run does not, which is the whole failure mode a checked
     * in report has.
     */
    private static List<String> cells(List<String> rows, String token) {
        List<String> found = null;
        for (String row : rows) {
            List<String> parts = new ArrayList<>(List.of(row.split("\\|")));
            parts.removeIf(String::isBlank);
            if (parts.isEmpty() || !parts.get(0).contains(token)) {
                continue;
            }
            assertFalse(found != null, "two rows carry " + token);
            found = parts.subList(1, parts.size()).stream().map(String::strip).toList();
        }
        assertFalse(found == null, "no row of readers.md carries " + token);
        return found;
    }

    private static Cause classify(List<Cause> causes, Difference difference, String instance) {
        Cause found = null;
        for (Cause cause : causes) {
            if (cause.matches(difference)) {
                assertFalse(found != null, instance + ": " + difference.path()
                        + " matches both " + (found == null ? "" : found.id()) + " and "
                        + cause.id());
                found = cause;
            }
        }
        assertFalse(found == null, instance + ": nothing in conformance/readers.md explains "
                + difference.path() + " (" + difference.side() + ")");
        return found;
    }

    private static List<Difference> compare(SemanticDocument streaming,
                                            SemanticDocument xslt) {
        Map<SemanticPath, SemanticValue> mine = streaming.values();
        Map<SemanticPath, SemanticValue> theirs = xslt.values();
        Set<SemanticPath> all = new TreeSet<>(mine.keySet());
        all.addAll(theirs.keySet());
        List<Difference> differences = new ArrayList<>();
        for (SemanticPath path : all) {
            SemanticValue a = mine.get(path);
            SemanticValue b = theirs.get(path);
            if (a == null) {
                differences.add(new Difference(path.toString(), "xslt-only"));
            } else if (b == null) {
                differences.add(new Difference(path.toString(), "streaming-only"));
            } else if (!a.equals(b)) {
                differences.add(new Difference(path.toString(), "differing-value"));
            }
        }
        return differences;
    }

    private static List<Cause> causes(Map<String, Object> report) {
        List<Cause> causes = new ArrayList<>();
        for (Object entry : Reports.array(report.get("causes"))) {
            Map<String, Object> cause = Reports.object(entry);
            causes.add(new Cause(Reports.text(cause.get("id")), Reports.text(cause.get("kind")),
                    Reports.text(cause.get("side")),
                    Pattern.compile(Reports.text(cause.get("pathPattern"))),
                    Reports.number(cause.get("paths")), Reports.number(cause.get("instances"))));
        }
        assertFalse(causes.isEmpty(), "the report names causes");
        return causes;
    }

    /** One difference between the two readers at one semantic path. */
    private record Difference(String path, String side) { }

    /** One cause the report gives for a family of differences. */
    private record Cause(String id,
                         String kind,
                         String side,
                         Pattern pathPattern,
                         int paths,
                         int instances) {

        private boolean matches(Difference difference) {
            return side.equals(difference.side())
                    && pathPattern.matcher(difference.path()).matches();
        }
    }
}
