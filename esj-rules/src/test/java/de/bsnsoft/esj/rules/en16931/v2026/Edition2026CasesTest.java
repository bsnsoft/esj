package de.bsnsoft.esj.rules.en16931.v2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.rules.RuleOracle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * The cases of {@code conformance/rules-2026/cases/cases.json}: for every rule this pack
 * writes out rather than takes over, a document of the conformance corpus the rule is silent
 * on and the same document with one thing changed that it speaks on.
 *
 * <p>The cases are data rather than code, as the mutations of the default pack are, so that
 * the change a rule is weighed by stands beside the rule and can be read without reading a
 * test. What the file records is measured again on every build: which identifiers the pack
 * reports about the first document, which appear when the change is made, and which go away.
 *
 * <p>A case is the whole of the evidence for a rule whose oracle is {@code cases}. It shows
 * that the engine decides what the rule was written to say; that what was written is what
 * the standard means is a reading of the text and is not something a test can establish.
 */
class Edition2026CasesTest {

    private static final Object FILE =
            Evidence2026.json("/conformance/rules-2026/cases/cases.json");

    /** The documents of the corpus the cases are made from, with their changes made. */
    private static final Map<String, SemanticDocument> DOCUMENTS = prepare();

    private static Map<String, SemanticDocument> prepare() {
        Map<String, SemanticDocument> documents = new LinkedHashMap<>();
        Map<?, ?> named = (Map<?, ?>) Evidence2026.get(FILE, "documents");
        for (Map.Entry<?, ?> entry : named.entrySet()) {
            Object document = entry.getValue();
            documents.put((String) entry.getKey(), Evidence2026.apply(
                    Evidence2026.upgraded((String) Evidence2026.get(document, "source")),
                    (List<?>) Evidence2026.get(document, "prepare")));
        }
        return Map.copyOf(documents);
    }

    private static List<?> cases() {
        return (List<?>) Evidence2026.get(FILE, "cases");
    }

    private static List<String> strings(Object array) {
        List<String> values = new ArrayList<>();
        if (array != null) {
            for (Object value : (List<?>) array) {
                values.add((String) value);
            }
        }
        return values;
    }

    /**
     * The invoices the cases start from are invoices this pack has nothing to say about.
     *
     * <p>Otherwise a case would measure the document it is made from rather than the change
     * it makes, and a rule that spoke about every one of them would look like a rule that
     * decides something.
     */
    @Test
    void everyDocumentTheCasesStartFromIsOneThePackIsSilentOn() {
        Map<String, List<String>> spoke = new LinkedHashMap<>();
        for (Map.Entry<String, SemanticDocument> document : DOCUMENTS.entrySet()) {
            List<String> codes = Evidence2026.codes(Evidence2026.ENGINE, document.getValue());
            if (!codes.isEmpty()) {
                spoke.put(document.getKey(), codes);
            }
        }

        assertEquals(Map.of(), spoke);
    }

    @TestFactory
    List<DynamicTest> everyCaseDecidesItsRule() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Object element : cases()) {
            Object one = element;
            String rule = (String) Evidence2026.get(one, "rule");
            String what = (String) Evidence2026.get(one, "what");
            tests.add(DynamicTest.dynamicTest(rule + ": " + what, () -> {
                SemanticDocument base = DOCUMENTS.get((String) Evidence2026.get(one, "document"));
                assertTrue(base != null,
                        rule + " names a document the file does not describe");
                SemanticDocument holds =
                        Evidence2026.apply(base, (List<?>) Evidence2026.get(one, "holds"));
                SemanticDocument speaks =
                        Evidence2026.apply(holds, (List<?>) Evidence2026.get(one, "speaks"));
                List<String> onHolds = Evidence2026.codes(Evidence2026.ENGINE, holds);
                List<String> onSpeaks = Evidence2026.codes(Evidence2026.ENGINE, speaks);
                List<String> appeared = new ArrayList<>(onSpeaks);
                appeared.removeAll(onHolds);
                List<String> gone = new ArrayList<>(onHolds);
                gone.removeAll(onSpeaks);

                assertFalse(onHolds.contains(rule),
                        rule + " speaks about the document it is to be silent on");
                assertTrue(appeared.contains(rule),
                        rule + " is silent about the document it is to speak on");
                assertEquals(strings(Evidence2026.get(one, "onHolds")), onHolds,
                        rule + ": what the pack reports about the document it is silent on");
                assertEquals(strings(Evidence2026.get(one, "expect")), appeared,
                        rule + ": what the change makes the pack report");
                assertEquals(strings(Evidence2026.get(one, "gone")), gone,
                        rule + ": what the change takes away");
            }));
        }
        return tests;
    }

    /**
     * Every rule this pack writes out has a case, or stands among the rules no document of
     * this format can make speak, with the reason.
     */
    @Test
    void everyRuleWrittenOutIsWeighedByACaseOrSaysWhyItCannotBe() {
        TreeSet<String> covered = new TreeSet<>();
        for (Object one : cases()) {
            covered.add((String) Evidence2026.get(one, "rule"));
        }
        for (Object one : (List<?>) Evidence2026.get(FILE, "cannotBeExercised")) {
            assertFalse(((String) Evidence2026.get(one, "why")).isBlank());
            covered.add((String) Evidence2026.get(one, "rule"));
        }
        TreeSet<String> written = new TreeSet<>();
        for (String rule : Evidence2026.ENGINE.ruleIds()) {
            if (Evidence2026.ENGINE.oracleOf(rule).orElseThrow() == RuleOracle.CASES) {
                written.add(rule);
            }
        }

        assertEquals(written, covered);
    }

    /** Every case is about a rule this pack writes out, and about no other. */
    @Test
    void everyCaseIsAboutARuleThisPackWritesOut() {
        for (Object one : cases()) {
            String rule = (String) Evidence2026.get(one, "rule");
            assertEquals(RuleOracle.CASES, Evidence2026.ENGINE.oracleOf(rule).orElse(null),
                    rule + " is weighed by a case and is not a rule this pack writes out");
        }
    }

    /** The ledger counts the cases the file holds. */
    @Test
    void theLedgerCountsWhatTheFileHolds() {
        Object ledger = Evidence2026.get(
                Evidence2026.json("/conformance/rules-2026/ledger.json"), "cases");
        TreeSet<String> rules = new TreeSet<>();
        for (Object one : cases()) {
            rules.add((String) Evidence2026.get(one, "rule"));
        }

        assertEquals(cases().size(), Evidence2026.number(ledger, "cases"));
        assertEquals(rules.size(), Evidence2026.number(ledger, "withACase"));
        assertEquals(((List<?>) Evidence2026.get(FILE, "cannotBeExercised")).size(),
                Evidence2026.number(ledger, "thatCannotBeExercised"));
    }

    /**
     * The ledger says of every rule whose oracle is {@code cases} what stands behind it: a
     * case, or the reason no case can be made.
     */
    @Test
    void theLedgerNamesTheOutcomeOfEveryRuleWeighedByCases() {
        Map<String, String> outcomes = new TreeMap<>();
        for (Object one : cases()) {
            outcomes.put((String) Evidence2026.get(one, "rule"), "case");
        }
        for (Object one : (List<?>) Evidence2026.get(FILE, "cannotBeExercised")) {
            outcomes.put((String) Evidence2026.get(one, "rule"), "cannot be exercised");
        }
        Object ledger = Evidence2026.get(
                Evidence2026.json("/conformance/rules-2026/ledger.json"), "cases");

        assertEquals(outcomes, Evidence2026.get(ledger, "outcomes"));
        assertEquals(outcomes.size(), Evidence2026.number(ledger, "rules"));
    }
}
