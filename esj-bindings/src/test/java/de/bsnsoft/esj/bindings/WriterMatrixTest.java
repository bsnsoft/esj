package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.syntax.SyntaxFinding;
import de.bsnsoft.esj.syntax.SyntaxValidator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The UBL/CII matrix through the semantic core, over the 40 business cases the corpus
 * carries in both syntaxes.
 *
 * <p>This is the measurement {@code conformance/writers/matrix.md} describes and
 * {@code matrix.json} records. For every pair the test reads the UBL file, writes it as a
 * cross industry invoice, reads the CII file, writes it as a UBL document, puts both results
 * through the official validation artefacts of the pack, reads both back and compares each
 * with the other syntax's original — four verdicts per pair, recomputed here rather than
 * trusted.
 *
 * <p>Every differing path has to be one {@code conformance/ledger/pairs.json} already
 * records for that pair, with the cause it records: a difference between the two files of
 * the suite survives a conversion, and anything else is a defect of a writer. A path the
 * ledger does not carry therefore fails this test, and so does a count that has moved in
 * either direction — a pair that starts to agree has to be written down before it counts.
 */
class WriterMatrixTest {

    private static final String UBL_SUFFIX = "_ubl.xml";
    private static final String CII_SUFFIX = "_uncefact.xml";
    private static final String REPORT = Corpus.ROOT + "writers/matrix.json";

    /** The direction that reads a UBL file and writes a cross industry invoice. */
    private static final String UBL_TO_CII = "ublToCii";

    /** The direction that reads a cross industry invoice and writes a UBL document. */
    private static final String CII_TO_UBL = "ciiToUbl";

    /** The whole matrix, computed once: every pair takes two writes and two validations. */
    private static final List<Outcome> OUTCOMES = compute();

    /** What one business case came to in both directions. */
    private record Outcome(String stem, List<String> ciiFatal, List<String> ublFatal,
            List<String> toCii, List<String> toUbl, List<WriteNote> ciiNotes,
            List<WriteNote> ublNotes, List<String> ciiLost, List<String> ublLost) {
    }

    /** The recorded report, read once. */
    private static Map<String, Object> report() {
        return Reports.parse(Corpus.text(REPORT));
    }

    /** Runs both conversions of every pair of the corpus. */
    private static List<Outcome> compute() {
        StreamingReader reader = new StreamingReader();
        List<Outcome> outcomes = new ArrayList<>();
        for (String stem : stems()) {
            SemanticDocument ubl = reader.read(Corpus.instance(stem + UBL_SUFFIX)).document();
            SemanticDocument cii = reader.read(Corpus.instance(stem + CII_SUFFIX)).document();
            WriteResult toCii = CiiWriter.writeWithReport(ubl, WriterOptions.defaults());
            WriteResult toUbl = UblWriter.writeWithReport(cii, WriterOptions.defaults());
            SemanticDocument backCii = reader.read(toCii.xml()).document();
            SemanticDocument backUbl = reader.read(toUbl.xml()).document();
            outcomes.add(new Outcome(stem, fatal(toCii.xml()), fatal(toUbl.xml()),
                    differences(backCii, cii), differences(backUbl, ubl),
                    toCii.report().notes(), toUbl.report().notes(),
                    differences(ubl, backCii), differences(cii, backUbl)));
        }
        return List.copyOf(outcomes);
    }

    /** Returns the business cases the corpus carries in both syntaxes, in corpus order. */
    private static List<String> stems() {
        List<String> stems = new ArrayList<>();
        for (String instance : Corpus.corpus()) {
            if (instance.endsWith(UBL_SUFFIX)) {
                String stem = instance.substring(0, instance.length() - UBL_SUFFIX.length());
                if (Corpus.instances().containsKey(stem + CII_SUFFIX)) {
                    stems.add(stem);
                }
            }
        }
        assertFalse(stems.isEmpty(), "the corpus holds business cases in both syntaxes");
        return stems;
    }

    /** The report covers exactly the pairs of the corpus, and the recorded verdicts hold. */
    @Test
    void everyPairIsRecordedWithTheVerdictsItProduces() {
        Map<String, Map<String, Object>> recorded = new LinkedHashMap<>();
        for (Object entry : Reports.array(report().get("pairs"))) {
            Map<String, Object> pair = Reports.object(entry);
            recorded.put(Reports.text(pair.get("stem")), pair);
        }
        assertEquals(stems(), new ArrayList<>(recorded.keySet()),
                "matrix.json records exactly the pairs of the corpus, in corpus order");
        for (Outcome outcome : OUTCOMES) {
            Map<String, Object> pair = recorded.get(outcome.stem());
            assertVerdict(outcome.stem(), Reports.object(pair.get(UBL_TO_CII)),
                    outcome.ciiFatal(), outcome.toCii());
            assertVerdict(outcome.stem(), Reports.object(pair.get(CII_TO_UBL)),
                    outcome.ublFatal(), outcome.toUbl());
        }
    }

    /** The totals of the report are the totals of the verdicts. */
    @Test
    void theTotalsAreWhatTheFortyPairsAddUpTo() {
        Map<String, Object> totals = Reports.object(report().get("totals"));
        Map<String, Integer> counted = new TreeMap<>();
        counted.put("pairs", OUTCOMES.size());
        for (Outcome outcome : OUTCOMES) {
            count(counted, UBL_TO_CII, outcome.ciiFatal(), outcome.toCii());
            count(counted, CII_TO_UBL, outcome.ublFatal(), outcome.toUbl());
        }
        counted.put("explained",
                counted.get("ublToCiiPaths") + counted.get("ciiToUblPaths"));
        counted.put("unexplained", 0);
        for (Map.Entry<String, Integer> measure : counted.entrySet()) {
            assertEquals(Reports.number(totals.get(measure.getKey())),
                    measure.getValue().intValue(), "the recorded " + measure.getKey());
        }
        assertEquals(counted.keySet(), new TreeSet<>(totals.keySet()),
                "matrix.json records these totals and no others");
    }

    /**
     * Every differing path is a difference the litmus ledger records for that pair, with the
     * cause it records. A path the ledger does not carry is a writer that lost something.
     */
    @Test
    void everyDifferenceIsOneTheLitmusLedgerAlreadyRecords() {
        Map<String, Map<String, String>> ledger = litmus();
        Map<String, int[]> byCause = new TreeMap<>();
        for (Outcome outcome : OUTCOMES) {
            Map<String, String> pair = ledger.get(outcome.stem());
            assertFalse(pair == null, "conformance/ledger/pairs.json records "
                    + outcome.stem());
            add(byCause, pair, outcome.stem(), outcome.toCii(), 0);
            add(byCause, pair, outcome.stem(), outcome.toUbl(), 1);
        }
        Map<String, int[]> recorded = new TreeMap<>();
        for (Object entry : Reports.array(report().get("causes"))) {
            Map<String, Object> cause = Reports.object(entry);
            recorded.put(Reports.text(cause.get("cause")),
                    new int[] {Reports.number(cause.get("ublToCiiPaths")),
                            Reports.number(cause.get("ciiToUblPaths"))});
            assertEquals("fixture", Reports.text(cause.get("kind")),
                    "the kind the litmus ledger gives " + cause.get("cause"));
        }
        assertEquals(recorded.keySet(), byCause.keySet(),
                "the causes the matrix runs into");
        for (Map.Entry<String, int[]> cause : byCause.entrySet()) {
            assertEquals(recorded.get(cause.getKey())[0], cause.getValue()[0],
                    "paths of " + cause.getKey() + " in UBL to CII");
            assertEquals(recorded.get(cause.getKey())[1], cause.getValue()[1],
                    "paths of " + cause.getKey() + " in CII to UBL");
        }
    }

    /** Every fatal finding is a rule the report names, with the pairs that make it fire. */
    @Test
    void everyRefusalIsARuleTheReportNames() {
        Map<String, Set<String>> byRule = new TreeMap<>();
        for (Outcome outcome : OUTCOMES) {
            assertEquals(List.of(), outcome.ciiFatal(), outcome.stem()
                    + ": the cross industry invoice written from the UBL file is accepted");
            for (String code : outcome.ublFatal()) {
                byRule.computeIfAbsent(code, key -> new LinkedHashSet<>())
                        .add(outcome.stem());
            }
        }
        Map<String, Integer> recorded = new TreeMap<>();
        for (Object entry : Reports.array(report().get("refusals"))) {
            Map<String, Object> refusal = Reports.object(entry);
            assertEquals(CII_TO_UBL, Reports.text(refusal.get("direction")),
                    "only the UBL profile refuses anything here");
            recorded.put(Reports.text(refusal.get("rule")),
                    Reports.number(refusal.get("pairs")));
        }
        assertEquals(recorded.keySet(), byRule.keySet(), "the rules the matrix makes fire");
        for (Map.Entry<String, Set<String>> fired : byRule.entrySet()) {
            assertEquals(recorded.get(fired.getKey()), fired.getValue().size(),
                    "pairs that make " + fired.getKey() + " fire: " + fired.getValue());
        }
    }

    /**
     * Where a writer drops a value at a path the two files disagree about anyway, the
     * comparison cannot see the loss. Those places are recorded, and the writer's own report
     * is what proves each of them, so a second one has to be written down before this test
     * goes green again.
     */
    @Test
    void everyLossTheComparisonCannotSeeIsRecorded() {
        Map<String, List<String>> masked = new TreeMap<>();
        Map<String, String> kinds = new TreeMap<>();
        for (Outcome outcome : OUTCOMES) {
            masked(masked, kinds, outcome.stem(), UBL_TO_CII, outcome.ciiLost(),
                    outcome.ciiNotes());
            masked(masked, kinds, outcome.stem(), CII_TO_UBL, outcome.ublLost(),
                    outcome.ublNotes());
        }
        Map<String, List<String>> recorded = new TreeMap<>();
        for (Object entry : Reports.array(report().get("masked"))) {
            Map<String, Object> loss = Reports.object(entry);
            String key = Reports.text(loss.get("stem")) + " " + loss.get("direction");
            recorded.put(key, Reports.array(loss.get("paths")).stream()
                    .map(Reports::text).toList());
            assertEquals(kinds.get(key), Reports.text(loss.get("note")),
                    "the note the writer gives the loss at " + key);
        }
        assertEquals(recorded, masked, "the losses a comparison of the pair cannot see");
    }

    /** The prose of the report carries the numbers the data carries. */
    @Test
    void theProseAndTheDataSayTheSame() {
        Map<String, Object> report = report();
        Map<String, Object> totals = Reports.object(report.get("totals"));
        List<String> rows = Corpus.text(Corpus.ROOT + "writers/matrix.md").lines()
                .filter(line -> line.startsWith("|")).toList();
        for (String measure : List.of("accepted", "refused", "equal", "differing", "paths")) {
            assertEquals(List.of(number(totals, UBL_TO_CII + capitalize(measure)),
                            number(totals, CII_TO_UBL + capitalize(measure))),
                    cells(rows, "`" + measure + "`"),
                    "the table of verdicts carries " + measure);
        }
        for (Object entry : Reports.array(report.get("refusals"))) {
            Map<String, Object> refusal = Reports.object(entry);
            assertEquals(List.of(String.valueOf(Reports.number(refusal.get("pairs"))),
                            "`" + Reports.text(refusal.get("cause")) + "`"),
                    cells(rows, "`" + Reports.text(refusal.get("rule")) + "`"),
                    "the table of refusals carries " + refusal.get("rule"));
        }
        for (Object entry : Reports.array(report.get("causes"))) {
            Map<String, Object> cause = Reports.object(entry);
            assertEquals(List.of(Reports.text(cause.get("kind")),
                            String.valueOf(Reports.number(cause.get("ublToCiiPaths"))),
                            String.valueOf(Reports.number(cause.get("ciiToUblPaths")))),
                    cells(rows, "`" + Reports.text(cause.get("cause")) + "`"),
                    "the table of causes carries " + cause.get("cause"));
        }
    }

    /** Asserts the three recorded verdicts of one direction of one pair. */
    private static void assertVerdict(String stem, Map<String, Object> recorded,
            List<String> fatal, List<String> differences) {
        assertEquals(Boolean.valueOf(fatal.isEmpty()), recorded.get("accepted"),
                stem + ": what the validation artefacts say, " + fatal);
        assertEquals(Boolean.valueOf(differences.isEmpty()), recorded.get("equal"),
                stem + ": what the other file of the pair says, " + differences);
        assertEquals(Reports.number(recorded.get("differingPaths")), differences.size(),
                stem + ": differing paths " + differences);
    }

    /** Adds one direction of one pair to the counted totals. */
    private static void count(Map<String, Integer> counted, String direction,
            List<String> fatal, List<String> differences) {
        counted.merge(direction + "Accepted", fatal.isEmpty() ? 1 : 0, Integer::sum);
        counted.merge(direction + "Refused", fatal.isEmpty() ? 0 : 1, Integer::sum);
        counted.merge(direction + "Equal", differences.isEmpty() ? 1 : 0, Integer::sum);
        counted.merge(direction + "Differing", differences.isEmpty() ? 0 : 1, Integer::sum);
        counted.merge(direction + "Paths", differences.size(), Integer::sum);
    }

    /** Adds the differences of one direction of one pair to the counts per cause. */
    private static void add(Map<String, int[]> byCause, Map<String, String> pair, String stem,
            List<String> differences, int column) {
        for (String path : differences) {
            String cause = pair.get(path);
            assertFalse(cause == null, stem + ": " + path + " differs from the other syntax"
                    + " after the conversion and conformance/ledger/pairs.json does not"
                    + " record it as a difference of the two files, so a writer lost it");
            byCause.computeIfAbsent(cause, key -> new int[2])[column]++;
        }
    }

    /** Adds the losses of one direction of one pair that the comparison cannot see. */
    private static void masked(Map<String, List<String>> masked, Map<String, String> kinds,
            String stem, String direction, List<String> lost, List<WriteNote> notes) {
        if (lost.isEmpty()) {
            return;
        }
        String key = stem + " " + direction;
        Set<String> named = new TreeSet<>();
        for (String path : lost) {
            for (WriteNote note : notes) {
                if (note.path().equals(path)) {
                    named.add(note.kind().name());
                }
            }
        }
        assertEquals(1, named.size(), key + ": the writer names every path it lost, " + lost);
        masked.put(key, lost);
        kinds.put(key, named.iterator().next());
    }

    /** Returns the litmus ledger as a path-to-cause map per business case. */
    private static Map<String, Map<String, String>> litmus() {
        Map<String, Map<String, String>> ledger = new LinkedHashMap<>();
        Map<String, Object> pairs =
                Reports.parse(Corpus.text(Corpus.ROOT + "ledger/pairs.json"));
        for (Object entry : Reports.array(pairs.get("pairs"))) {
            Map<String, Object> pair = Reports.object(entry);
            Map<String, String> causes = new LinkedHashMap<>();
            for (Object difference : Reports.array(pair.get("differences"))) {
                Map<String, Object> recorded = Reports.object(difference);
                causes.put(Reports.text(recorded.get("path")),
                        Reports.text(recorded.get("cause")));
            }
            ledger.put(Reports.text(pair.get("stem")), causes);
        }
        return ledger;
    }

    /** Returns the codes of the fatal findings the validation artefacts report. */
    private static List<String> fatal(byte[] xml) {
        return SyntaxValidator.validate(xml).fatal().stream()
                .map(SyntaxFinding::code).toList();
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

    /** Returns a recorded total as the text a table cell carries. */
    private static String number(Map<String, Object> totals, String key) {
        Object value = totals.get(key);
        assertTrue(value != null, "matrix.json records the total " + key);
        return String.valueOf(Reports.number(value));
    }

    /** Returns a measure name as the second half of a total's name. */
    private static String capitalize(String measure) {
        return Character.toUpperCase(measure.charAt(0)) + measure.substring(1);
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
            assertTrue(found == null, "two rows of matrix.md carry " + token);
            found = parts.subList(1, parts.size()).stream().map(String::strip).toList();
        }
        assertFalse(found == null, "no row of matrix.md carries " + token);
        return found;
    }
}
