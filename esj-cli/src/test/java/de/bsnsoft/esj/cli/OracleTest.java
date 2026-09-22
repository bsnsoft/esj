package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.bindings.ReaderOptions;
import de.bsnsoft.esj.bindings.StreamingReader;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleSeverity;
import de.bsnsoft.esj.rules.en16931.En16931;
import de.bsnsoft.esj.syntax.SyntaxFinding;
import de.bsnsoft.esj.syntax.SyntaxValidator;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The official artefacts as the oracle of the EN 16931 rule pack.
 *
 * <p>The pack is written over business terms and the artefacts are written over the XPath of
 * two syntaxes, so the only way to weigh one against the other is to put the same bytes
 * through both: read the document with the reader {@code esj validate} uses when nothing is
 * chosen and run the pack, run the released Schematron of its profile on the same document,
 * and compare the rule identifiers. Both engines are in this
 * module's class path, so the comparison is made in the build and needs no installation and
 * no download.
 *
 * <p>Documents that are right cannot answer the question — an engine that reports nothing
 * passes that test — so the input is the corpus broken on purpose:
 * {@code conformance/rules/mutations/mutations.json} holds one change per rule, with what
 * each engine reported when the ledger was taken down. This test re-runs both engines and
 * fails on any change to either answer.
 *
 * <p>An outcome is about the identifiers, as it always was. Which of the pack's findings
 * decide no verdict is recorded beside them, under {@code warns}, and asserted as well: that
 * is where the pack says the official artefact of the other syntax faults a figure its own
 * artefact accepts, and it would otherwise read as an ordinary agreement or difference.
 *
 * <p>Three outcomes are recorded and each is asserted differently. A mutation that
 * <em>agrees</em> must produce the same set of identifiers on both sides — that is the claim
 * the ledger is about. A mutation that <em>differs</em> must produce exactly the two sets
 * recorded, and must name the cause, which {@code conformance/rules/ledger.md} explains; a
 * difference that moved is a difference nobody looked at. A <em>silent</em> mutation is a near
 * miss, a value at the edge of what a rule allows, and both engines must say nothing about it.
 */
class OracleTest {

    /** The reader {@code esj validate} uses when nothing is chosen. */
    private static final StreamingReader READER = new StreamingReader(ReaderOptions.builder()
            .registry(XrImporter.defaultRegistry())
            .build());

    private static final RuleEngine ENGINE = En16931.engine(XrImporter.defaultRegistry());

    /** The causes {@code ledger.md} explains; a mutation may not invent a new one. */
    private static final Set<String> CAUSES = Set.of(
            "operand-removed", "artefacts-not-chosen", "not-in-the-release",
            "artefact-cannot-fire", "category-bound-differently", "term-lost-on-import",
            "notation-lost-on-import", "artefact-reads-the-norm-narrowly",
            "artefacts-disagree-on-a-tolerance");

    static List<Oracle.Mutation> mutations() {
        return Oracle.mutations();
    }

    /**
     * Returns the identifiers the pack reports over the document the reader builds: all of
     * them, or only the ones whose finding decides no verdict.
     *
     * @param xml      the bytes
     * @param warnings whether to return the warnings alone
     * @return the identifiers, in order
     */
    private static List<String> nativeFindings(byte[] xml, boolean warnings) {
        SemanticDocument document = READER.read(xml).document();
        Set<String> codes = new TreeSet<>();
        for (RuleFinding finding : ENGINE.evaluate(document)) {
            if (!warnings || finding.severity() == RuleSeverity.WARNING) {
                codes.add(finding.code());
            }
        }
        return List.copyOf(codes);
    }

    private static List<String> officialFindings(byte[] xml) {
        Set<String> codes = new TreeSet<>();
        for (SyntaxFinding finding : SyntaxValidator.validate(xml).findings()) {
            String category = finding.category().label();
            if (category.equals("EN-BR") || category.equals("EN-DEC") || category.equals("EN-CL")) {
                codes.add(finding.code());
            }
        }
        return List.copyOf(codes);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void bothEnginesReportWhatTheLedgerRecords(Oracle.Mutation mutation) {
        byte[] xml = Oracle.apply(mutation);

        assertEquals(mutation.expectedNative(), nativeFindings(xml, false),
                mutation.id() + ": the EN 16931 rule pack over the imported document");
        assertEquals(mutation.expectedWarnings(), nativeFindings(xml, true),
                mutation.id() + ": which of the pack's findings decide no verdict");
        assertEquals(mutation.expectedOfficial(), officialFindings(xml),
                mutation.id() + ": the official Schematron of release 1.3.16 over the same bytes");
    }

    /**
     * A mutation the ledger calls agreeing is one where the two engines name the same rules,
     * and the rule it is aimed at is one of them. Anything else would be a mutation that
     * measures nothing.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void anAgreeingMutationNamesItsRuleOnBothSides(Oracle.Mutation mutation) {
        if (!mutation.outcome().equals("agrees")) {
            return;
        }

        assertEquals(mutation.expectedNative(), mutation.expectedOfficial(),
                mutation.id() + " is recorded as agreeing");
        assertTrue(mutation.expectedNative().contains(mutation.rule()),
                mutation.id() + " is aimed at " + mutation.rule() + ", which it does not report");
    }

    /** A near miss is a value at the edge of what a rule allows, and nothing may be said. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void aNearMissIsSilentOnBothSides(Oracle.Mutation mutation) {
        if (!mutation.outcome().equals("silent")) {
            return;
        }

        assertEquals(List.of(), mutation.expectedNative(), mutation.id());
        assertEquals(List.of(), mutation.expectedOfficial(), mutation.id());
        assertEquals("", mutation.rule(), mutation.id() + " is aimed at no rule");
    }

    /** A difference that is not explained is a defect nobody has looked at. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void aDifferenceNamesACauseTheLedgerExplains(Oracle.Mutation mutation) {
        if (!mutation.outcome().equals("differs")) {
            return;
        }

        assertTrue(CAUSES.contains(mutation.cause()),
                mutation.id() + " names the cause " + mutation.cause()
                        + ", which conformance/rules/ledger.md does not explain");
        assertNotEquals(mutation.expectedNative(), mutation.expectedOfficial(),
                mutation.id() + " is recorded as differing and the two sets are the same");
    }

    /** Every mutation changes the instance it is made from, or it validates the corpus again. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void changesTheInstanceItIsMadeFrom(Oracle.Mutation mutation) {
        assertNotEquals(new String(Oracle.instance(mutation)),
                new String(Oracle.apply(mutation)),
                mutation.id() + " changes the instance it is made from");
    }

    /**
     * The ledger is the summary of the set, and the two are held together here: every rule of
     * the pack has an outcome, every outcome is one of the three the ledger knows, and the
     * totals are the counts of that table.
     */
    @Test
    void theLedgerSaysWhatTheMutationSetMeasured() {
        Map<?, ?> ledger = (Map<?, ?>) Oracle.json(
                Oracle.bytes("/conformance/rules/ledger.json"));
        Map<?, ?> rules = (Map<?, ?>) ledger.get("rules");
        Map<?, ?> totals = (Map<?, ?>) ledger.get("totals");

        assertEquals(new TreeSet<>(ENGINE.ruleIds()), new TreeSet<>(keys(rules)),
                "the ledger names every rule of the pack and no other");
        Map<String, Long> counted = new TreeMap<>();
        for (Object outcome : rules.values()) {
            counted.merge((String) outcome, 1L, Long::sum);
        }
        assertEquals(counted.getOrDefault("agrees", 0L), totals.get("agreeing"));
        assertEquals(counted.getOrDefault("partly", 0L), totals.get("partly"));
        assertEquals(counted.getOrDefault("differs", 0L), totals.get("explained"));
        assertEquals(counted.getOrDefault("not exercised", 0L), totals.get("notExercised"));
        assertEquals((long) rules.size(), totals.get("implemented"));
        assertEquals(0L, totals.get("open"), "a difference nobody explained is an open one");
    }

    /**
     * What the ledger says about a rule is what the set shows about it: a rule it calls
     * agreeing has an agreeing mutation and no differing one, one it calls differing has a
     * differing mutation and no agreeing one, one it calls partly has both, and one it calls
     * not exercised has no mutation at all.
     */
    @Test
    void everyRuleOfTheLedgerIsWhatItsMutationsShow() {
        Map<?, ?> ledger = (Map<?, ?>) Oracle.json(
                Oracle.bytes("/conformance/rules/ledger.json"));
        Map<?, ?> rules = (Map<?, ?>) ledger.get("rules");
        Set<String> agreeing = new LinkedHashSet<>();
        Set<String> differing = new LinkedHashSet<>();
        for (Oracle.Mutation mutation : Oracle.all()) {
            if (mutation.outcome().equals("agrees")) {
                agreeing.add(mutation.rule());
            } else if (mutation.outcome().equals("differs")) {
                differing.add(mutation.rule());
            }
        }

        for (Object rule : keys(rules)) {
            String id = (String) rule;
            switch ((String) rules.get(id)) {
                case "agrees" -> {
                    assertTrue(agreeing.contains(id),
                            id + " is recorded as agreeing and has no agreeing mutation");
                    assertFalse(differing.contains(id),
                            id + " is recorded as agreeing and has a differing mutation");
                }
                case "differs" -> {
                    assertTrue(differing.contains(id),
                            id + " is recorded as differing and has no differing mutation");
                    assertFalse(agreeing.contains(id),
                            id + " is recorded as differing and has an agreeing mutation");
                }
                case "partly" -> {
                    assertTrue(agreeing.contains(id),
                            id + " is recorded as partly agreeing and has no agreeing mutation");
                    assertTrue(differing.contains(id),
                            id + " is recorded as partly agreeing and has no differing mutation");
                }
                default -> {
                    assertFalse(agreeing.contains(id) || differing.contains(id),
                            id + " is recorded as not exercised and has a mutation");
                }
            }
        }
    }

    /**
     * The page and the file say the same thing.
     *
     * <p>A ledger whose prose and whose figures drift apart is worse than none: the figures are
     * what a reader takes away, and they are in the page rather than in the file. So every
     * total of {@code ledger.json} has to appear in {@code ledger.md}, and the page has to name
     * every cause a mutation of the set claims.
     */
    @Test
    void theLedgerPageQuotesTheFiguresOfTheLedgerFile() {
        Map<?, ?> ledger = (Map<?, ?>) Oracle.json(
                Oracle.bytes("/conformance/rules/ledger.json"));
        Map<?, ?> totals = (Map<?, ?>) ledger.get("totals");
        String page = Oracle.text("/conformance/rules/ledger.md");

        for (Map.Entry<?, ?> total : totals.entrySet()) {
            assertTrue(page.contains(String.valueOf(total.getValue())),
                    "conformance/rules/ledger.md does not name the " + total.getKey()
                            + " of ledger.json, which is " + total.getValue());
        }
        assertTrue(page.contains(String.valueOf(Oracle.all().size())),
                "the page does not say how many mutations were run");
        for (Oracle.Mutation mutation : Oracle.all()) {
            if (!mutation.cause().isEmpty()) {
                assertTrue(((Map<?, ?>) ledger.get("causes")).containsKey(mutation.cause()),
                        mutation.cause() + " is not one of the causes ledger.json lists");
            }
        }
    }

    /**
     * The pages that quote the ledger quote the current one.
     *
     * <p>{@code README.md} and {@code docs/validation.md} repeat the figures of
     * {@code ledger.json} for a reader who never opens the ledger, and a repeated figure is
     * the one that goes stale: the ledger is regenerated by this test and the pages are
     * written by hand. Every total the ledger holds therefore has to stand on both pages as a
     * number of its own, and the count of differences they name — the rules that agree on one
     * shape, those that differ and those the oracle cannot be asked about — has to be the sum
     * the ledger comes to.
     */
    @Test
    void thePagesThatQuoteTheLedgerQuoteTheCurrentOne() {
        Map<?, ?> ledger = (Map<?, ?>) Oracle.json(
                Oracle.bytes("/conformance/rules/ledger.json"));
        Map<?, ?> totals = (Map<?, ?>) ledger.get("totals");
        Map<?, ?> mutations = (Map<?, ?>) ledger.get("mutations");

        for (String resource : List.of("/README.md", "/docs/validation.md")) {
            String page = Oracle.text(resource);
            for (Map.Entry<?, ?> total : totals.entrySet()) {
                assertTrue(names(page, total.getValue()),
                        resource + " does not name the " + total.getKey()
                                + " of ledger.json, which is " + total.getValue());
            }
            assertTrue(names(page, mutations.get("total")),
                    resource + " does not name the " + mutations.get("total")
                            + " mutations the ledger was taken down over");
        }

        long differences = number(totals.get("partly")) + number(totals.get("explained"))
                + number(totals.get("notExercised"));
        for (String resource : List.of("/docs/validation.md",
                "/conformance/rules/ledger.md")) {
            assertTrue(names(Oracle.text(resource), differences),
                    resource + " does not name the " + differences
                            + " differences the totals of ledger.json come to");
        }
    }

    /**
     * Tells whether a page writes a figure as a number of its own rather than inside one.
     *
     * <p>A comma after the digits ends a clause where a digit follows it in a grouped
     * number, so the two are told apart rather than both refused: a page that writes
     * "differs outright for 16, and" names 16, and one that writes 16,000 does not.
     */
    private static boolean names(String page, Object figure) {
        return Pattern.compile("(?<![0-9.,])" + Pattern.quote(String.valueOf(figure))
                + "(?![0-9.])(?!,[0-9])").matcher(page).find();
    }

    /** Reads a figure of the ledger, which JSON hands over as a number of some width. */
    private static long number(Object figure) {
        return ((Number) figure).longValue();
    }

    private static List<Object> keys(Map<?, ?> map) {
        return new ArrayList<>(map.keySet());
    }
}
