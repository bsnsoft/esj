package de.bsnsoft.esj.rules.en16931.v2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleOracle;
import de.bsnsoft.esj.upgrade.UpgradeNote;
import de.bsnsoft.esj.upgrade.UpgradeResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * What this pack says about the conformance corpus written up to this edition.
 *
 * <p>The corpus is 86 invoices the official validator accepts and eleven documents of the
 * format, all of them written for the 2017 edition. {@code esj upgrade} moves each of them
 * to this one and the pack runs over the result, which is the only corpus of this edition
 * there can be while no syntax binds it. Every finding is recorded in
 * {@code conformance/rules-2026/ledger.json} and compared here, exactly: a finding that
 * appears, disappears or moves to another path is a change in what this pack says about a
 * document nobody changed.
 *
 * <p>Two claims rest on that comparison and are asserted on their own.
 *
 * <p>A rule this edition leaves unchanged must decide the upgraded document the way the pack
 * of the default edition decides the original — that is what {@code downgrade} claims of it,
 * and a rule that spoke here and not there would be a rule that was carried over wrongly.
 *
 * <p>A rule this edition changed or adds may well speak where the default pack is silent,
 * and every identifier that does is named in {@code ledger.md} with what the edition changed
 * to make it speak. The list is asserted here so that a finding cannot appear without the
 * page saying why.
 */
class Edition2026CorpusTest {

    /** The identifiers of rules this edition changed or adds that speak about the corpus. */
    private static final Set<String> EXPLAINED = new TreeSet<>(List.of(
            "BR-69", "BR-70", "BR-71", "BR-72", "BR-73", "BR-75",
            "BR-CO-16", "BR-CO-25", "BR-CO-40", "BR-DEC-44"));

    /**
     * The rules this edition adds for the identification scheme it makes mandatory, with the
     * business term each one is about.
     */
    private static final Map<String, String> SCHEME_RULES = Map.of(
            "BR-69", "BT-29", "BR-70", "BT-30", "BR-71", "BT-46", "BR-72", "BT-47",
            "BR-73", "BT-60", "BR-74", "BT-61", "BR-75", "BT-71");

    private static final Pattern TERM = Pattern.compile("BT-[0-9]+");

    /** One finding of the ledger: the document, the rule, the severity and where it looked. */
    private record Row(String document, String code, String severity, List<String> paths) {

        @Override
        public String toString() {
            return document + " " + code + " [" + severity + "] " + paths;
        }
    }

    private static List<Row> recorded() {
        Object corpus = Evidence2026.get(
                Evidence2026.json("/conformance/rules-2026/ledger.json"), "corpus");
        List<Row> rows = new ArrayList<>();
        for (Object element : (List<?>) Evidence2026.get(corpus, "findings")) {
            Map<?, ?> row = (Map<?, ?>) element;
            List<String> paths = new ArrayList<>();
            for (Object path : (List<?>) row.get("paths")) {
                paths.add((String) path);
            }
            rows.add(new Row((String) row.get("document"), (String) row.get("code"),
                    (String) row.get("severity"), List.copyOf(paths)));
        }
        return rows;
    }

    private static List<Row> run() {
        List<Row> rows = new ArrayList<>();
        for (String name : Evidence2026.corpus()) {
            SemanticDocument document = Evidence2026.upgraded(name);
            for (RuleFinding finding : Evidence2026.ENGINE.evaluate(document)) {
                rows.add(new Row(name, finding.code(), finding.severity().token(),
                        finding.paths()));
            }
        }
        return rows;
    }

    @Test
    void whatThePackSaysAboutTheUpgradedCorpusIsWhatTheLedgerRecords() {
        assertEquals(recorded(), run(), "conformance/rules-2026/ledger.json is a golden file:"
                + " a finding that appears, disappears or moves is a change in what this pack"
                + " says about a document nobody changed");
    }

    @Test
    void theFiguresOfTheLedgerAreTheFiguresOfTheRun() {
        Object corpus = Evidence2026.get(
                Evidence2026.json("/conformance/rules-2026/ledger.json"), "corpus");
        List<String> names = Evidence2026.corpus();
        int quiet = 0;
        for (String name : names) {
            if (Evidence2026.ENGINE.evaluate(Evidence2026.upgraded(name)).isEmpty()) {
                quiet++;
            }
        }

        assertEquals(names.size(), Evidence2026.number(corpus, "documents"));
        assertEquals(names.size(), Evidence2026.number(corpus, "upgraded"));
        assertEquals(0, Evidence2026.number(corpus, "refused"));
        assertEquals(quiet, Evidence2026.number(corpus, "withoutAFinding"));
        assertEquals(run().size(), ((List<?>) Evidence2026.get(corpus, "findings")).size());
    }

    /**
     * A rule whose oracle is the downgrade decides the upgraded document the way the pack of
     * the default edition decides the document it was made from.
     */
    @Test
    void anUnchangedRuleSaysAboutTheUpgradedDocumentWhatTheDefaultPackSaysAboutTheOriginal() {
        List<String> drifted = new ArrayList<>();
        for (String name : Evidence2026.corpus()) {
            Set<String> now = new LinkedHashSet<>(
                    Evidence2026.codes(Evidence2026.ENGINE, Evidence2026.upgraded(name)));
            Set<String> then = new LinkedHashSet<>(Evidence2026.codes(
                    Evidence2026.DEFAULT_PACK, Evidence2026.document(name)));
            for (String code : Evidence2026.ENGINE.ruleIds()) {
                if (Evidence2026.ENGINE.oracleOf(code).orElseThrow() != RuleOracle.DOWNGRADE) {
                    continue;
                }
                if (now.contains(code) != then.contains(code)) {
                    drifted.add(name + ": " + code);
                }
            }
        }

        assertEquals(List.of(), drifted, "a rule this edition leaves unchanged decides the"
                + " upgraded document the way the pack of the default edition decides the"
                + " document it was made from");
    }

    /**
     * The rules about a missing identification scheme speak exactly where {@code esj upgrade}
     * reports one, document by document and term by term.
     *
     * <p>A scheme is the one thing an upgrade cannot supply, so the run reports every value
     * that lacks it and leaves it as it was. The two are independent readings of the same
     * fact: one from the mapping file, one from the rules. Where they part, one of them is
     * wrong.
     */
    @Test
    void theSchemeRulesSpeakExactlyWhereTheUpgradeReportsAMissingScheme() {
        Map<String, Set<String>> reported = new TreeMap<>();
        Map<String, Set<String>> found = new TreeMap<>();
        for (String name : Evidence2026.corpus()) {
            UpgradeResult result = Evidence2026.upgrade(name);
            for (UpgradeNote note : result.report().notes()) {
                if (note.kind() == UpgradeNote.Kind.SCHEME_MISSING) {
                    reported.computeIfAbsent(name, key -> new TreeSet<>())
                            .add(term(note.path().orElseThrow().toString()));
                }
            }
            for (RuleFinding finding : Evidence2026.ENGINE.evaluate(result.require())) {
                String term = SCHEME_RULES.get(finding.code());
                if (term == null) {
                    continue;
                }
                for (String path : finding.paths()) {
                    assertEquals(term, term(path), finding.code() + " speaks about " + path);
                }
                found.computeIfAbsent(name, key -> new TreeSet<>()).add(term);
            }
        }

        assertTrue(!reported.isEmpty(), "the corpus is to exercise the scheme rules");
        assertEquals(reported, found, "the rules about a missing identification scheme and the"
                + " upgrade report name the same documents and the same terms");
    }

    /** Returns the last business term a path names. */
    private static String term(String path) {
        Matcher matcher = TERM.matcher(path);
        String last = null;
        while (matcher.find()) {
            last = matcher.group();
        }
        return last;
    }

    /** Every rule this edition changed or adds that speaks about the corpus is explained. */
    @Test
    void everyChangedOrNewRuleThatSpeaksIsOneTheLedgerExplains() {
        Set<String> spoke = new TreeSet<>();
        for (Row row : run()) {
            if (Evidence2026.ENGINE.oracleOf(row.code()).orElseThrow() == RuleOracle.CASES) {
                spoke.add(row.code());
            }
        }
        String page = Evidence2026.text("/conformance/rules-2026/ledger.md");

        assertEquals(EXPLAINED, spoke, "a rule this edition changed or adds speaks about the"
                + " upgraded corpus and conformance/rules-2026/ledger.md does not say why");
        for (String code : spoke) {
            assertTrue(page.contains(code),
                    "conformance/rules-2026/ledger.md does not name " + code);
        }
    }
}
