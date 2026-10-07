package de.bsnsoft.esj.cli.v2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.bindings.ReaderOptions;
import de.bsnsoft.esj.bindings.StreamingReader;
import de.bsnsoft.esj.cli.Oracle;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleOracle;
import de.bsnsoft.esj.rules.en16931.En16931Pack;
import de.bsnsoft.esj.rules.en16931.v2026.En16931V2026Pack;
import de.bsnsoft.esj.upgrade.EditionUpgrade;
import de.bsnsoft.esj.upgrade.UpgradeOptions;
import de.bsnsoft.esj.upgrade.UpgradeResult;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The official artefacts as the oracle of the rules the EN 16931-1:2026 pack took over.
 *
 * <p>No artefact release covers that edition. A rule it leaves unchanged can still be weighed
 * against one, by the route its {@code oracle} member names: the mutated instance is read into
 * ESJ, written up to the edition with {@code esj upgrade}, decided by the pack of the edition,
 * and written back. Where the document that comes back is the one that went in, byte for byte
 * in the canonical form, the verdict the official Schematron of release 1.3.16 gives the
 * original is a verdict about the same invoice, and the two can be put side by side.
 *
 * <p>The input is the mutation set of the default pack, which {@code OracleTest} of this module
 * holds against both engines on every build: what that file records as the official answer is
 * therefore the official answer, measured, and this test does not run Saxon a second time over
 * the same 448 documents to hear it again.
 *
 * <p>{@code conformance/rules-2026/ledger.json} records the outcome per rule and this test
 * recomputes all of it. What it adds to the ledger of the default pack is the second column of
 * the comparison: over every mutation and every rule, this pack over the upgraded document says
 * what that pack says over the document it was made from, so the differences against the
 * artefacts are that pack's own and are explained in its ledger rather than here.
 */
class Edition2026DowngradeTest {

    private static final String LEDGER = "/conformance/rules-2026/ledger.json";

    private static final StreamingReader READER = new StreamingReader(ReaderOptions.defaults()
            .withRegistry(Registry.en16931WithXrechnung()));

    private static final RuleEngine EDITION = new En16931V2026Pack().engine(Registry.forEdition("2026"));

    private static final RuleEngine DEFAULT_PACK = En16931Pack.engine(Registry.en16931WithXrechnung());

    private static final UpgradeOptions OPTIONS = UpgradeOptions.defaults()
            .withExtensions(List.of(Registry.xrechnungExtension()));

    /** What one run of the comparison found. */
    private record Measurement(int held,
                               int refused,
                               int agreeing,
                               int drifted,
                               Map<String, String> rules,
                               Map<String, List<String>> differences) {
    }

    private static Set<String> carriedOver() {
        Set<String> rules = new TreeSet<>();
        for (String rule : EDITION.ruleIds()) {
            if (EDITION.oracleOf(rule).orElseThrow() == RuleOracle.DOWNGRADE) {
                rules.add(rule);
            }
        }
        return rules;
    }

    private static Set<String> codes(RuleEngine engine, SemanticDocument document) {
        Set<String> codes = new TreeSet<>();
        for (RuleFinding finding : engine.evaluate(document)) {
            codes.add(finding.code());
        }
        return codes;
    }

    private static Measurement measure() {
        Set<String> carried = carriedOver();
        Map<String, List<String>> differences = new TreeMap<>();
        Set<String> exercised = new TreeSet<>();
        int held = 0;
        int refused = 0;
        int agreeing = 0;
        int drifted = 0;
        for (Oracle.Mutation mutation : Oracle.all()) {
            byte[] xml = Oracle.apply(mutation);
            SemanticDocument source = READER.read(xml).document();
            UpgradeResult up = EditionUpgrade.apply(source, "2026", OPTIONS);
            UpgradeResult back = up.isUpgraded()
                    ? EditionUpgrade.apply(up.require(), "2017", OPTIONS)
                    : up;
            if (!up.isUpgraded() || !back.isUpgraded()
                    || !Arrays.equals(Canonicalizer.canonicalBytes(source),
                            Canonicalizer.canonicalBytes(back.require()))) {
                refused++;
                continue;
            }
            held++;
            Set<String> here = codes(EDITION, up.require());
            Set<String> there = codes(DEFAULT_PACK, source);
            Set<String> official = new TreeSet<>(mutation.expectedOfficial());
            boolean same = true;
            for (String rule : carried) {
                boolean byThisPack = here.contains(rule);
                boolean byTheArtefacts = official.contains(rule);
                if (byThisPack || byTheArtefacts || there.contains(rule)) {
                    exercised.add(rule);
                }
                if (byThisPack != byTheArtefacts) {
                    same = false;
                    differences.computeIfAbsent(rule, key -> new ArrayList<>())
                            .add(mutation.id());
                }
                if (byThisPack != there.contains(rule)) {
                    drifted++;
                }
            }
            if (same) {
                agreeing++;
            }
        }
        Map<String, String> rules = new LinkedHashMap<>();
        for (String rule : carried) {
            rules.put(rule, !exercised.contains(rule) ? "not exercised"
                    : differences.containsKey(rule) ? "differs" : "identical");
        }
        return new Measurement(held, refused, agreeing, drifted, rules, differences);
    }

    @Test
    void whatTheTwoEnginesSayAboutEveryMutationIsWhatTheLedgerRecords() {
        Measurement measured = measure();
        Object ledger = Evidence.get(Evidence.json(LEDGER), "downgrade");

        assertEquals(Oracle.all().size(), Evidence.number(ledger, "mutations"));
        assertEquals(measured.held(), Evidence.number(ledger, "roundTripHeld"));
        assertEquals(measured.refused(), Evidence.number(ledger, "roundTripRefused"));
        assertEquals(measured.agreeing(),
                Evidence.number(ledger, "mutationsWhereEveryRuleAgrees"));
        assertEquals(measured.drifted(),
                Evidence.number(ledger, "findingsThatDifferFromTheDefaultPack"));
        assertEquals(measured.rules(), Evidence.get(ledger, "rules"));
        assertEquals(measured.differences(), Evidence.get(ledger, "differences"));
    }

    /**
     * The totals of the ledger are the counts of the outcomes it records, and no difference is
     * left open.
     */
    @Test
    void theTotalsAreTheCountsOfTheOutcomes() {
        Object ledger = Evidence.get(Evidence.json(LEDGER), "downgrade");
        Object totals = Evidence.get(ledger, "totals");
        Map<String, Integer> counted = new TreeMap<>();
        for (Object outcome : ((Map<?, ?>) Evidence.get(ledger, "rules")).values()) {
            counted.merge((String) outcome, 1, Integer::sum);
        }

        assertEquals(carriedOver().size(), Evidence.number(totals, "rules"));
        assertEquals(counted.getOrDefault("identical", 0), Evidence.number(totals, "identical"));
        assertEquals(counted.getOrDefault("differs", 0), Evidence.number(totals, "differing"));
        assertEquals(counted.getOrDefault("not exercised", 0),
                Evidence.number(totals, "notExercised"));
        assertEquals(0, Evidence.number(totals, "open"),
                "a difference nobody explained is an open one");
    }

    /**
     * A difference against the artefacts falls only on a mutation the default set already
     * records as differing, with the cause written on it.
     *
     * <p>That is what lets this ledger leave the explanation of each difference to the ledger
     * of the default pack: a difference on a mutation that set calls agreeing would be one of
     * this pack's own, and nobody would have explained it.
     */
    @Test
    void everyDifferenceFallsOnAMutationWhoseCauseTheDefaultSetNames() {
        Map<String, Oracle.Mutation> byId = new TreeMap<>();
        for (Oracle.Mutation mutation : Oracle.all()) {
            byId.put(mutation.id(), mutation);
        }
        Object ledger = Evidence.get(Evidence.json(LEDGER), "downgrade");
        for (Map.Entry<?, ?> difference
                : ((Map<?, ?>) Evidence.get(ledger, "differences")).entrySet()) {
            for (Object id : (List<?>) difference.getValue()) {
                Oracle.Mutation mutation = byId.get((String) id);
                assertTrue(mutation != null, difference.getKey() + ": no mutation " + id);
                assertEquals("differs", mutation.outcome(),
                        difference.getKey() + " differs on " + id
                                + ", which the default set records as agreeing");
                assertTrue(mutation.cause() != null && !mutation.cause().isBlank(),
                        id + " carries no cause");
            }
        }
    }

    /**
     * Every rule this pack writes out and every rule it took over is counted once, and the
     * ledger says how many there are of each.
     */
    @Test
    void theLedgerCountsTheOraclesOfThePack() {
        Object oracles = Evidence.get(Evidence.json(LEDGER), "oracles");
        int cases = EDITION.ruleIds().size() - carriedOver().size();

        assertEquals(carriedOver().size(), Evidence.number(oracles, "downgrade"));
        assertEquals(cases, Evidence.number(oracles, "cases"));
    }

    /**
     * The page beside the data says the same figures.
     *
     * <p>A ledger whose prose and whose figures drift apart is worse than none: the figures are
     * what a reader takes away, and they are in the page rather than in the file.
     */
    @Test
    void theLedgerPageQuotesTheFiguresOfTheLedgerFile() {
        Object ledger = Evidence.json(LEDGER);
        String page = Evidence.text("/conformance/rules-2026/ledger.md");
        Object downgrade = Evidence.get(ledger, "downgrade");
        Object totals = Evidence.get(downgrade, "totals");
        Object corpus = Evidence.get(ledger, "corpus");
        Object cases = Evidence.get(ledger, "cases");
        Map<String, Object> figures = new LinkedHashMap<>();
        for (String member : List.of("mutations", "roundTripHeld",
                "mutationsWhereEveryRuleAgrees", "findingsThatDifferFromTheDefaultPack")) {
            figures.put(member, Evidence.get(downgrade, member));
        }
        for (String member : List.of("rules", "identical", "differing", "notExercised", "open")) {
            figures.put(member, Evidence.get(totals, member));
        }
        for (String member : List.of("documents", "withoutAFinding")) {
            figures.put(member, Evidence.get(corpus, member));
        }
        for (String member : List.of("withACase", "cases", "thatCannotBeExercised")) {
            figures.put(member, Evidence.get(cases, member));
        }
        figures.put("findings", ((List<?>) Evidence.get(corpus, "findings")).size());
        figures.put("pack", EDITION.ruleIds().size());

        for (Map.Entry<String, Object> figure : figures.entrySet()) {
            assertTrue(names(page, figure.getValue()),
                    "conformance/rules-2026/ledger.md does not name the " + figure.getKey()
                            + " of ledger.json, which is " + figure.getValue());
        }
        for (Map.Entry<String, String> rule : measure().rules().entrySet()) {
            if (!rule.getValue().equals("identical")) {
                assertTrue(page.contains(rule.getKey()), "conformance/rules-2026/ledger.md does"
                        + " not name " + rule.getKey() + ", which is " + rule.getValue());
            }
        }
    }

    /** Tells whether a page writes a figure as a number of its own rather than inside one. */
    private static boolean names(String page, Object figure) {
        return Pattern.compile("(?<![0-9.,])" + Pattern.quote(String.valueOf(figure))
                + "(?![0-9.])(?!,[0-9])").matcher(page).find();
    }
}
