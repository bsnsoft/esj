package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportReport;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the importer over the whole conformance corpus and holds it against what is checked
 * in beside the corpus.
 *
 * <p>Five things are asserted per instance and one over the corpus as a whole. Per
 * instance: the bytes are the ones the attribution records, the model layer of the
 * validator accepts what the importer built, that document agrees with the ESJ file
 * checked in under {@code conformance/esj/} at every path but the ones
 * {@code conformance/readers.json} records this path as losing, that file reads back into
 * an equal document, and the import report shows that nothing of the instance was left
 * unplaced. Over the corpus: the cardinality findings are exactly the ones the ledger
 * records.
 *
 * <p>The checked-in files are the output of the streaming reader of {@code esj-bindings},
 * which is the reader the command line tool uses by default, and this module is the
 * oracle that reader is measured against. The tolerance above is therefore not a way of
 * excusing a difference: every path it admits is one {@code conformance/readers.md}
 * names, with the reason and the count, and a path outside that list fails here. Where an
 * instance produces no difference at all — 80 of the 86 — the canonical bytes have to be
 * equal, which is the assertion this test made about every instance before the two readers
 * parted company at one term.
 *
 * <p>The cardinality layer is recorded rather than asserted away. It measures a document
 * against the cardinalities of the standard, and where an instance of the suite leaves a
 * mandatory term out the finding belongs to that instance rather than to this importer.
 * Recording it in the ledger and asserting the ledger keeps both properties: the findings
 * do not fail the build, and none of them appears or disappears unnoticed.
 */
class ConformanceCorpusTest {

    private static final EnumSet<ValidationLayer> MODEL = EnumSet.of(ValidationLayer.L2);
    private static final EnumSet<ValidationLayer> CARDINALITY = EnumSet.of(ValidationLayer.L3);

    /** One recorded cardinality finding: instance, code, path and message. */
    private static final Pattern LEDGER_LINE =
            Pattern.compile("^(\\S+)  (ESJ-L3-\\S+)  (/\\S*)  (\\S.*)$");

    /**
     * The {@code pathPattern} of a cause of {@code conformance/readers.json} whose kind is
     * {@code overcome}, that is a difference the streaming reader does not have.
     */
    private static final Pattern OVERCOME = Pattern.compile(
            "\"kind\": \"overcome\"[^{}]*?\"pathPattern\": \"(.*?)\"", Pattern.DOTALL);

    /**
     * The semantic paths at which this path is allowed to differ from the checked-in
     * documents: the ones {@code conformance/readers.json} records as differences the
     * streaming reader has overcome. Reading them from that file rather than writing them
     * out here keeps one statement of the difference in the repository.
     */
    private static final List<Pattern> TOLERATED = tolerated();

    private final XrImporter importer = new XrImporter();

    static List<String> corpus() {
        return Conformance.corpus();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void importsEveryInstanceIntoTheCheckedInDocument(String instance) {
        byte[] xml = Conformance.instance(instance);
        assertEquals(Conformance.instances().get(instance), Conformance.sha256(xml),
                "the instance is the one the attribution of the corpus records");

        SemanticDocument document = importer.importXml(xml);

        assertFalse(document.values().isEmpty(), "the document carries values");
        assertEquals(instance.endsWith("_uncefact.xml") ? "CII" : "UBL",
                document.source().orElseThrow().syntax().orElseThrow(),
                "the provenance names the syntax that was read");
        assertEquals(List.of(), StructuralValidator.validate(document, importer.registry(), MODEL).findings(),
                "the model layer accepts what the importer built");

        byte[] canonical = Canonicalizer.canonicalBytes(document);
        SemanticDocument checkedIn = EsjReader.strict().read(Conformance.esj(instance));
        List<String> differing = differingPaths(document, checkedIn);
        for (String path : differing) {
            assertTrue(TOLERATED.stream().anyMatch(pattern -> pattern.matcher(path).matches()),
                    instance + ": " + path + " differs from the document checked in under"
                            + " conformance/esj and conformance/readers.json does not record"
                            + " this path as one the stylesheets lose");
        }
        if (differing.isEmpty()) {
            assertArrayEquals(Canonicalizer.canonicalize(Conformance.esj(instance)), canonical,
                    "the import agrees with the checked-in document at every path, so the"
                            + " two canonical byte sequences are equal");
        }

        SemanticDocument reread = EsjReader.strict().read(canonical);
        assertEquals(document, reread, "the document survives the canonical form");
        assertArrayEquals(canonical, Canonicalizer.canonicalBytes(reread),
                "canonicalizing twice gives the same bytes");
    }

    /**
     * Nothing in the whole corpus goes missing on the way in. Every element the stylesheets
     * emit carries an identifier the registries know, every one of them has a place in the
     * semantic model, every content spells a value of its type, no element is empty and no
     * two elements claim the same path. The one thing the importer does drop is a
     * supplementary component the standard does not give the term it arrived at — a VAT
     * scheme on a VAT identifier — and that is reported as such.
     *
     * <p>This assertion is the strongest honesty guarantee of the corpus: it says that the
     * ESJ file beside each instance holds everything the stylesheets read out of it except
     * those dropped components — 89 of them over the whole corpus, and the only notes the
     * corpus produces at all.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void leavesNothingOfAnyInstanceUnplaced(String instance) {
        ImportReport report = importer.importXmlWithReport(Conformance.instance(instance)).report();

        assertEquals(List.of(), report.notes(ImportNote.Kind.UNPLACEABLE),
                "every element the stylesheets write has a place in the semantic model");
        assertEquals(List.of(), report.notes(ImportNote.Kind.UNKNOWN_TERM),
                "every identifier the stylesheets write is one the registries know");
        assertEquals(List.of(), report.notes(ImportNote.Kind.MALFORMED),
                "every content the stylesheets write spells a value of its type");
        assertEquals(List.of(), report.notes(ImportNote.Kind.EMPTY),
                "the stylesheets write no empty element");
        assertEquals(List.of(), report.notes(ImportNote.Kind.DUPLICATE_PATH),
                "no two elements resolve to the same path");
        assertEquals(List.of(), report.notes(ImportNote.Kind.PATH_TOO_LONG),
                "no element lies at a path too long for a reader running the defaults");
        assertEquals(List.of(), report.notes(ImportNote.Kind.LIMIT_REACHED),
                "no value and no document of this corpus reaches a limit of those defaults");
        assertEquals(List.of(), report.notes(ImportNote.Kind.COMPONENT_MISSING),
                "no value arrives without a supplementary component the standard requires");
    }

    @Test
    void producesExactlyTheRecordedCardinalityFindings() {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> instance : Conformance.instances().entrySet()) {
            SemanticDocument document = importer.importXml(Conformance.instance(instance.getKey()));
            for (Finding finding : StructuralValidator.validate(
                    document, importer.registry(), CARDINALITY).findings()) {
                assertEquals(ValidationLayer.L3, finding.code().layer(),
                        "the cardinality layer reports cardinality findings and nothing else");
                found.add(instance.getKey() + "  " + finding.code().code() + "  "
                        + finding.path() + "  " + finding.message());
            }
        }
        assertEquals(recordedCardinalityFindings(), found,
                "conformance/ledger/l3-findings.md records what the corpus produces");
    }

    /**
     * Reads the path patterns of {@code conformance/readers.json} whose cause is one the
     * streaming reader has overcome. The file is small and its shape is fixed, so the two
     * members that matter are read with one expression each rather than with a parser.
     */
    private static List<Pattern> tolerated() {
        String report = Conformance.text(Conformance.ROOT + "readers.json");
        List<Pattern> patterns = new ArrayList<>();
        Matcher matcher = OVERCOME.matcher(report);
        while (matcher.find()) {
            patterns.add(Pattern.compile(matcher.group(1).replace("\\\\", "\\")));
        }
        assertFalse(patterns.isEmpty(),
                "conformance/readers.json records what the two readers do differently");
        return patterns;
    }

    /** Returns the paths at which two documents disagree, in canonical order. */
    private static List<String> differingPaths(SemanticDocument left, SemanticDocument right) {
        Set<SemanticPath> all = new TreeSet<>(left.values().keySet());
        all.addAll(right.values().keySet());
        List<String> differing = new ArrayList<>();
        for (SemanticPath path : all) {
            if (!Objects.equals(left.values().get(path), right.values().get(path))) {
                differing.add(path.toString());
            }
        }
        return differing;
    }

    /** Reads the recorded findings out of the ledger, one per matching line. */
    private static List<String> recordedCardinalityFindings() {
        List<String> recorded = new ArrayList<>();
        for (String line : Conformance.ledger("l3-findings.md").split("\\R")) {
            Matcher matcher = LEDGER_LINE.matcher(line);
            if (matcher.matches()) {
                recorded.add(line);
            }
        }
        assertFalse(recorded.isEmpty(), "the ledger records findings in the documented format");
        return recorded;
    }
}
