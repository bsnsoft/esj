package de.bsnsoft.esj.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.model.JsonTree;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.validate.StructuralValidator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Every document of the corpus and every example, written as the 2026 edition and back.
 *
 * <p>This is the measurement {@code conformance/editions/upgrade.md} describes and
 * {@code upgrade.json} beside it records, and the test does not trust either of them: it
 * upgrades every document again, validates every result against the registry of the 2026
 * edition, downgrades every result and compares the canonical bytes with the ones it
 * started from. A figure that moves in either direction fails this test.
 *
 * <p>The round trip is run without provenance, which is the whole of the difference
 * between the bytes going out and the bytes coming back: {@code source} is set where a
 * caller hands over the bytes the result derives from, and the command line does, so a
 * round trip through {@code esj upgrade} differs from its input in that one member and in
 * nothing else.
 */
class UpgradeCorpusTest {

    private static final String LEDGER = "/conformance/editions/upgrade.json";

    /** The terms whose identification scheme the 2026 edition made mandatory. */
    private static final List<String> SCHEMES =
            List.of("BT-29", "BT-30", "BT-46", "BT-47", "BT-60", "BT-61", "BT-71");

    static boolean carries2026() {
        return Registry.editions().contains("2026") && Corpus.has(LEDGER);
    }

    @Test
    @EnabledIf("carries2026")
    void whatTheCorpusDoesIsWhatTheLedgerSays() {
        Measurement measured = measure();
        Object ledger = JsonTree.of(Corpus.text(LEDGER));
        assertEquals(Registry.forEdition("2017").edition(), JsonTree.text(ledger, "from"));
        assertEquals(Registry.forEdition("2026").edition(), JsonTree.text(ledger, "to"));

        Map<String, Object> corpus = JsonTree.map(ledger, "corpus");
        assertEquals(measured.documents, JsonTree.number(corpus, "documents"));
        assertEquals(measured.instances, JsonTree.number(corpus, "instances"));
        assertEquals(measured.examples, JsonTree.number(corpus, "examples"));

        Map<String, Object> upgrade = JsonTree.map(ledger, "upgrade");
        assertEquals(measured.upgraded, JsonTree.number(upgrade, "upgraded"));
        assertEquals(measured.refused, JsonTree.number(upgrade, "refused"));
        assertEquals(measured.moved, JsonTree.number(upgrade, "pathsMoved"));
        assertEquals(measured.withMovedPaths, JsonTree.number(upgrade, "documentsWithMovedPaths"));

        Map<String, Object> structure = JsonTree.map(ledger, "structure");
        assertEquals(measured.clean, JsonTree.number(structure, "clean"));
        assertEquals(measured.schemeGapsOnly, JsonTree.number(structure, "schemeGapsOnly"));
        assertEquals(measured.otherFindings, JsonTree.number(structure, "otherFindings"));

        Map<String, Object> recorded = JsonTree.map(ledger, "openPoints");
        Map<String, Integer> expected = new TreeMap<>();
        for (Map.Entry<String, Object> entry : recorded.entrySet()) {
            expected.put(entry.getKey(), ((Number) entry.getValue()).intValue());
        }
        assertEquals(expected, measured.openPoints, "the open points the corpus produces");

        Map<String, Object> downgrade = JsonTree.map(ledger, "downgrade");
        assertEquals(measured.downgraded, JsonTree.number(downgrade, "downgraded"));
        assertEquals(measured.identical, JsonTree.number(downgrade, "byteIdentical"));
        assertEquals(measured.downgradeRefused, JsonTree.number(downgrade, "refused"));
    }

    /**
     * The page beside the data says the same numbers. A ledger nobody reads is a ledger
     * that drifts, and the page is the one that is read.
     */
    @Test
    @EnabledIf("carries2026")
    void thePageSaysWhatTheDataSays() {
        Measurement measured = measure();
        String page = Corpus.text("/conformance/editions/upgrade.md");
        for (String figure : List.of(
                "| Documents | " + measured.documents + " |",
                "| `upgraded` | " + measured.upgraded + " |",
                "| `refused` | " + measured.refused + " |",
                "| Paths moved | " + measured.moved + " |",
                "| `byteIdentical` | " + measured.identical + " |")) {
            assertTrue(page.contains(figure), "conformance/editions/upgrade.md says " + figure);
        }
    }

    /** Runs the measurement: every document up, checked, and back. */
    private static Measurement measure() {
        Measurement measurement = new Measurement();
        Registry target = Registry.forEdition("2026");
        UpgradeOptions options = UpgradeOptions.builder()
                .extension(Registry.xrechnungExtension())
                .extension(Registry.b2cExtension())
                .build();
        List<String> names = new ArrayList<>(Corpus.corpus());
        measurement.instances = names.size();
        List<String> examples = new ArrayList<>();
        for (String example : Corpus.examples()) {
            SemanticDocument document = Corpus.document(example);
            if (Registry.forEdition("2017").describes(document.semanticModel())) {
                examples.add(example);
            }
        }
        measurement.examples = examples.size();
        names.addAll(examples);
        measurement.documents = names.size();

        for (String name : names) {
            SemanticDocument source = Corpus.document(name);
            UpgradeResult up = EditionUpgrade.apply(source, "2026", options);
            count(measurement, up);
            if (!up.isUpgraded()) {
                measurement.refused++;
                continue;
            }
            measurement.upgraded++;
            int moved = up.report().rewritten();
            measurement.moved += moved;
            if (moved > 0) {
                measurement.withMovedPaths++;
            }
            classify(measurement, up.require(), target);
            UpgradeResult back = EditionUpgrade.apply(up.require(), "2017", options);
            if (!back.isUpgraded()) {
                measurement.downgradeRefused++;
                continue;
            }
            measurement.downgraded++;
            if (Arrays.equals(Canonicalizer.canonicalBytes(source),
                    Canonicalizer.canonicalBytes(back.require()))) {
                measurement.identical++;
            }
        }
        return measurement;
    }

    /** Counts the open points of one run by kind. */
    private static void count(Measurement measurement, UpgradeResult result) {
        for (UpgradeNote note : result.report().notes()) {
            if (note.severity() == UpgradeNote.Severity.OPEN_POINT) {
                measurement.openPoints.merge(note.kind().token(), 1, Integer::sum);
            }
        }
    }

    /**
     * Says what the registry of the target edition makes of one result: nothing, the
     * scheme components the edition made mandatory, or something else.
     */
    private static void classify(Measurement measurement, SemanticDocument result,
                                 Registry target) {
        boolean schemes = false;
        boolean other = false;
        for (Finding finding : StructuralValidator.validate(result, target).findings()) {
            if (!finding.isError()) {
                continue;
            }
            if (finding.code() == FindingCode.ESJ_L2_COMPONENT_MISSING
                    && SCHEMES.contains(finding.path().term())) {
                schemes = true;
                continue;
            }
            other = true;
        }
        if (other) {
            measurement.otherFindings++;
        } else if (schemes) {
            measurement.schemeGapsOnly++;
        } else {
            measurement.clean++;
        }
    }

    /** What one run of the measurement found. */
    private static final class Measurement {

        private final Map<String, Integer> openPoints = new TreeMap<>();
        private int documents;
        private int instances;
        private int examples;
        private int upgraded;
        private int refused;
        private int moved;
        private int withMovedPaths;
        private int clean;
        private int schemeGapsOnly;
        private int otherFindings;
        private int downgraded;
        private int identical;
        private int downgradeRefused;
    }
}
