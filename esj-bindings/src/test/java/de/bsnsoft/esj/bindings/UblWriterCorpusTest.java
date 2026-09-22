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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The UBL writer over the whole corpus, over the examples and over the one credit note,
 * against the official validation artefacts and against itself.
 *
 * <p>This is the measurement {@code conformance/writers/ubl-roundtrip.md} describes and
 * {@code ubl-roundtrip.json} records, and the test does not trust either of them: it writes
 * every document again, validates every result with the pack, reads every result back and
 * compares it with what it was written from, and fails unless every fatal finding is one the
 * report names and every count is the recorded one. A document that starts to validate fails
 * as loudly as one that stops.
 */
class UblWriterCorpusTest {

    private static final String REPORT = Corpus.ROOT + "writers/ubl-roundtrip.json";

    /**
     * Every corpus instance read, written as a UBL document, and put through the validation
     * artefacts of its profile.
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
                    SyntaxValidator.validate(UblWriter.write(source)).fatal();
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
     * Every corpus instance written and read back, against the document it was written from.
     * Nothing this syntax cannot carry is in the corpus, so every one of them has to come
     * back unchanged and the writer's report has to say that it carried everything.
     */
    @Test
    void everyInstanceComesBackUnchanged() {
        Map<String, Object> corpus =
                Reports.object(Reports.parse(Corpus.text(REPORT)).get("corpus"));
        StreamingReader reader = new StreamingReader();
        int identical = 0;
        int differingPaths = 0;
        for (String instance : Corpus.corpus()) {
            SemanticDocument source = reader.read(Corpus.instance(instance)).document();
            WriteResult result = UblWriter.writeWithReport(source, WriterOptions.defaults());
            List<String> differences =
                    differences(source, reader.read(result.xml()).document());
            assertEquals(List.of(), differences, instance + ": the document goes into this"
                    + " syntax and comes back unchanged");
            assertEquals(0, result.report().dropped(),
                    instance + ": every value reached the syntax");
            identical++;
            differingPaths += differences.size();
        }
        assertEquals(Reports.number(corpus.get("identical")), identical);
        assertEquals(Reports.number(corpus.get("differing")),
                Corpus.corpus().size() - identical);
        assertEquals(Reports.number(corpus.get("differingPaths")), differingPaths);
    }

    /**
     * What the writer says while it writes, against what the report says it says. The one
     * refusal above is an element of the {@code unstated} table and every element the
     * writer supplied a conventional value for is an element of the {@code conventions}
     * table, both named as the document was written and before anything was validated.
     */
    @Test
    void theWriterNamesEveryElementTheDocumentDoesNotState() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        Map<String, Set<String>> instances = new TreeMap<>();
        Map<String, Integer> notes = new TreeMap<>();
        StreamingReader reader = new StreamingReader();
        for (String instance : Corpus.corpus()) {
            SemanticDocument source = reader.read(Corpus.instance(instance)).document();
            WriteResult result = UblWriter.writeWithReport(source, WriterOptions.defaults());
            for (WriteNote note : result.report().notes()) {
                assertTrue(note.kind() == WriteNote.Kind.CONVENTION_APPLIED
                                || note.kind() == WriteNote.Kind.TERM_NOT_STATED,
                        instance + ": " + note);
                String id = recorded(report, note, instance);
                instances.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(instance);
                notes.merge(id, 1, Integer::sum);
            }
        }
        for (String member : List.of("conventions", "unstated")) {
            for (Object entry : Reports.array(report.get(member))) {
                Map<String, Object> recorded = Reports.object(entry);
                String id = Reports.text(recorded.get("id"));
                assertEquals(Reports.number(recorded.get("instances")),
                        instances.getOrDefault(id, Set.of()).size(),
                        "instances at " + recorded.get("element"));
                assertEquals(Reports.number(recorded.get("notes")),
                        notes.getOrDefault(id, 0), "notes about " + recorded.get("element"));
            }
        }
        assertEquals(instances.keySet(), new TreeSet<>(identifiers(report)),
                "the report names the elements the writer names and no others");
    }

    /** Returns the identifiers of both tables of the report. */
    private static Set<String> identifiers(Map<String, Object> report) {
        Set<String> identifiers = new TreeSet<>();
        for (String member : List.of("conventions", "unstated")) {
            for (Object entry : Reports.array(report.get(member))) {
                identifiers.add(Reports.text(Reports.object(entry).get("id")));
            }
        }
        return identifiers;
    }

    /**
     * The same measurement over the documents the XSLT path of {@code esj-xr} builds from
     * the same corpus, which is the second column of the report.
     */
    @Test
    void theDocumentsTheXsltPathBuildsMeasureTheSame() {
        Map<String, Object> xslt =
                Reports.object(Reports.parse(Corpus.text(REPORT)).get("xslt"));
        StreamingReader reader = new StreamingReader();
        XrImporter importer = new XrImporter();
        int accepted = 0;
        int identical = 0;
        for (String instance : Corpus.corpus()) {
            SemanticDocument source = importer.importXml(Corpus.instance(instance));
            byte[] xml = UblWriter.write(source);
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

    /** The ten examples of the repository, and which of them the schema modules accept. */
    @Test
    void everyExampleIsAUblDocumentThatComesBackUnchanged() {
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
            byte[] xml = UblWriter.write(document);
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
        assertEquals(Reports.number(examples.get("structural")), structural);
        assertEquals(Reports.number(examples.get("ruleClean")), ruleClean);
        assertEquals(Reports.number(examples.get("identical")), identical);
        assertEquals(new TreeSet<>(rules.keySet()), new TreeSet<>(fired.keySet()),
                "the rules the examples make fire");
        for (Map.Entry<String, Integer> entry : fired.entrySet()) {
            assertEquals(Reports.number(rules.get(entry.getKey())), entry.getValue(),
                    "findings of " + entry.getKey());
        }
    }

    /**
     * The credit note. No instance of the corpus is one — every one of them states the
     * invoice type code 380 — so this document and the credit note of {@code examples/} are
     * what carry {@code model/bindings/ubl-creditnote.json} through the writer.
     */
    @Test
    void theCreditNoteIsWrittenAsACreditNoteAndAccepted() {
        StreamingReader reader = new StreamingReader();
        List<SemanticDocument> documents = List.of(
                reader.read(Corpus.bytes(Corpus.ROOT + "creditnote/credit-note_ubl.xml"))
                        .document(),
                EsjReader.strict().read(Examples.bytes("credit-note")));
        for (SemanticDocument document : documents) {
            WriteResult result =
                    UblWriter.writeWithReport(document, WriterOptions.defaults());
            assertEquals(BindingSyntax.UBL_CREDIT_NOTE, result.report().syntax(),
                    "the invoice type code 381 makes this a credit note");
            assertTrue(result.report().isComplete(), result.report().notes().toString());
            assertEquals(List.of(), SyntaxValidator.validate(result.xml()).fatal().stream()
                    .map(SyntaxFinding::code).toList());
            assertEquals(List.of(),
                    differences(document, reader.read(result.xml()).document()));
        }
    }

    /** The prose of the report carries the numbers the data carries. */
    @Test
    void theProseAndTheDataSayTheSame() {
        Map<String, Object> report = Reports.parse(Corpus.text(REPORT));
        Map<String, Object> corpus = Reports.object(report.get("corpus"));
        List<String> rows = Corpus.text(Corpus.ROOT + "writers/ubl-roundtrip.md").lines()
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
        for (Object entry : Reports.array(report.get("conventions"))) {
            Map<String, Object> convention = Reports.object(entry);
            assertEquals(List.of("`" + Reports.text(convention.get("value")) + "`",
                            String.valueOf(Reports.number(convention.get("instances")))),
                    cells(rows, "`" + Reports.text(convention.get("element")) + "`"),
                    "the table of conventions carries " + convention.get("element"));
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
        assertFalse(found == null, "no row of ubl-roundtrip.md carries " + token);
        return found;
    }

    /** Returns the identifier of the element family a note is about. */
    private static String recorded(Map<String, Object> report, WriteNote note,
            String instance) {
        String member = note.kind() == WriteNote.Kind.CONVENTION_APPLIED
                ? "conventions" : "unstated";
        String found = null;
        for (Object entry : Reports.array(report.get(member))) {
            Map<String, Object> recorded = Reports.object(entry);
            String element = Reports.text(recorded.get("element"));
            boolean empty = "empty".equals(Reports.text(recorded.get("shape")));
            if (note.message().contains(element)
                    && empty == note.message().contains("written empty")) {
                assertFalse(found != null, instance + ": two entries of ubl-roundtrip.md"
                        + " explain " + note);
                found = Reports.text(recorded.get("id"));
            }
        }
        assertFalse(found == null, instance + ": nothing in ubl-roundtrip.md explains "
                + note);
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
}
