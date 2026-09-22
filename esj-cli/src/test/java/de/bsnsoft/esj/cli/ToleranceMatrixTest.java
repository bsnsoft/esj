package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.bindings.ReaderOptions;
import de.bsnsoft.esj.bindings.StreamingReader;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleSeverity;
import de.bsnsoft.esj.rules.en16931.En16931;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * How closely release 1.3.16 asks the two figures of a rule to agree, read out of the
 * vendored artefacts.
 *
 * <p>Nine rules of the pack compare a stated amount with a sum and one compares it with a
 * product, and the two official artefacts do not ask for the same closeness: one of them
 * accepts a stated amount within one unit of the invoice currency and the other asks for
 * equality, and which of the two is the lenient one changes with the rule. A pack written
 * over business terms has one arithmetic per rule, so it faults from the wider of the two
 * readings on and warns inside the zone only one artefact grants, naming that artefact.
 *
 * <p>That matrix is a fact about two files this repository carries, and a fact stated in
 * prose drifts from the files the first time a release is added. So it is read here out
 * of the artefacts themselves and weighed against what
 * {@code conformance/rules/ledger.json} records, rule by rule and syntax by syntax. What
 * the reading is worth is decided elsewhere: {@code OracleTest} runs both engines over a
 * mutation at the boundary of every row.
 *
 * <p>The reading is mechanical. An assertion that subtracts one unit from the stated
 * amount and compares the difference with the figure allows a tolerance; the comparison
 * operator says whether the boundary itself is inside it. Anything else is an equality.
 * No expression and no assertion text of the artefacts is carried into this repository.
 *
 * <p>One thing the reading cannot see is whether an assertion can report at all. Two rows
 * are read as an equality that never fires, because the CII assertion takes its two
 * figures from a node where neither is written, and {@code ledger.json} records that
 * beside the reading as {@code canFire}. It changes what the pack may say about a
 * difference inside their zone: no artefact faults it, so the warning names the standard,
 * and the row's {@code warns} is {@code none} rather than a syntax. The message itself is
 * asserted here, because a sentence about two artefacts is the kind of claim that survives
 * the file it is about.
 */
class ToleranceMatrixTest {

    /** The vendored artefact of each syntax, as a test class path resource. */
    private static final Map<String, String> ARTEFACTS = Map.of(
            "ubl", "/de/bsnsoft/esj/syntax/packs/xrechnung/3.0.2/2026-08-31"
                    + "/cen/1.3.16/EN16931-UBL-validation.xslt",
            "cii", "/de/bsnsoft/esj/syntax/packs/xrechnung/3.0.2/2026-08-31"
                    + "/cen/1.3.16/EN16931-CII-validation.xslt");

    private static final String EQUALITY = "equality";
    private static final String ONE_UNIT = "within one unit";
    private static final String ONE_UNIT_CLOSED = "within one unit, boundary included";

    /** The three readings, from the strictest to the widest. */
    private static final List<String> WIDTH =
            List.of(EQUALITY, ONE_UNIT, ONE_UNIT_CLOSED);

    /** The reader {@code esj validate} uses when nothing is chosen. */
    private static final StreamingReader READER = new StreamingReader(ReaderOptions.builder()
            .registry(XrImporter.defaultRegistry())
            .build());

    /** The pack this build carries. */
    private static final RuleEngine ENGINE = En16931.engine(XrImporter.defaultRegistry());

    /** The numbers the pages of this repository write out, as they write them. */
    private static final List<String> WRITTEN = List.of("no", "one", "two", "three", "four",
            "five", "six", "seven", "eight", "nine", "ten");

    @Test
    void theLedgerRecordsWhatTheArtefactsAsk() {
        Map<?, ?> recorded = readings();

        for (Map.Entry<?, ?> rule : recorded.entrySet()) {
            String id = (String) rule.getKey();
            Map<?, ?> row = (Map<?, ?>) rule.getValue();
            for (String syntax : List.of("ubl", "cii")) {
                assertEquals(row.get(syntax), reading(syntax, id),
                        "conformance/rules/ledger.json records what the " + syntax
                                + " artefact of release 1.3.16 asks of " + id);
            }
        }
    }

    /**
     * The pack faults from the wider of the two readings on, which is what makes it safe to
     * run beside either artefact: it faults nothing that the artefact of the document's own
     * syntax would have accepted. Where the two readings differ it warns inside the zone
     * between them, and the row names the syntax whose artefact faults a figure there.
     */
    @Test
    void thePackFaultsFromTheWiderOfTheTwoReadingsOnAndWarnsBelowIt() {
        Map<?, ?> recorded = readings();

        for (Map.Entry<?, ?> rule : recorded.entrySet()) {
            Map<?, ?> row = (Map<?, ?>) rule.getValue();
            int ubl = WIDTH.indexOf((String) row.get("ubl"));
            int cii = WIDTH.indexOf((String) row.get("cii"));
            assertTrue(ubl >= 0 && cii >= 0, rule.getKey() + " records a reading this test knows");
            assertEquals(WIDTH.get(Math.max(ubl, cii)), row.get("pack"),
                    rule.getKey() + " faults from the wider of the two readings on");
            String strict = ubl < cii ? "UBL" : "CII";
            assertEquals(ubl == cii ? null : (fires(row) ? strict : "none"), row.get("warns"),
                    rule.getKey() + " names the artefact that faults a figure inside the"
                            + " zone, or none where the strict assertion cannot report");
        }
    }

    /**
     * The figure the pages quote is the one the ledger carries, and the ledger carries what
     * the matrix shows: a page that says a number no row supports is the defect this test is
     * here to catch.
     */
    @Test
    void theFigureThePagesQuoteIsTheNumberOfRowsThatDisagree() {
        long disagreeing = readings().values().stream()
                .filter(row -> !((Map<?, ?>) row).get("ubl").equals(((Map<?, ?>) row).get("cii")))
                .count();

        assertEquals(disagreeing, ((Number) tolerances().get("disagreeing")).longValue(),
                "conformance/rules/ledger.json counts the rows whose artefacts disagree");
        String written = WRITTEN.get((int) disagreeing);
        assertTrue(Oracle.text("/conformance/rules/ledger.md").toLowerCase(Locale.ROOT)
                        .contains(written + " of the ten rows"),
                "conformance/rules/ledger.md does not say that " + written + " of the ten rows"
                        + " disagree");
        assertTrue(Oracle.text("/docs/validation.md").contains(written + " rules"),
                "docs/validation.md does not say that " + written + " rules disagree");
    }

    /**
     * The figure the ledger quotes for the mutations of the family is the number the set
     * carries, so that a mutation added or removed is a figure that moves rather than a
     * sentence that goes quietly out of date.
     */
    @Test
    void theFigureTheLedgerQuotesIsTheNumberOfMutationsAimedAtTheFamily() {
        long aimed = Oracle.all().stream()
                .filter(mutation -> readings().containsKey(mutation.rule()))
                .count();

        assertTrue(Oracle.text("/conformance/rules/ledger.md")
                        .contains("carries " + aimed + " mutations aimed at these ten rules"),
                "conformance/rules/ledger.md does not say that the set carries " + aimed
                        + " mutations aimed at these ten rules");
    }

    /**
     * A warning inside a zone no artefact faults says what is true of it.
     *
     * <p>It is the one row where the sentence a reader takes away cannot be read off the
     * matrix: the CII assertion is an equality, and an equality that cannot fire faults
     * nothing. So the warning is measured rather than described, on the mutation that
     * states a taxable amount half a unit away from the sum.
     */
    @Test
    void theWarningOfARowNoArtefactFaultsNamesTheStandard() {
        RuleFinding finding = warning("af08-tol-ubl", "BR-AF-08");

        assertTrue(finding.message().endsWith("and the standard asks the two to be equal:"
                        + " the official UBL artefact of release 1.3.16 admits a difference"
                        + " this small, and the CII assertion of that release cannot report"
                        + " it."),
                "BR-AF-08 warns with what release 1.3.16 does: " + finding.message());
    }

    /** The other rows say what they have always said: the other artefact faults it. */
    @Test
    void theWarningOfARowOneArtefactFaultsNamesThatArtefact() {
        RuleFinding finding = warning("s08-tol-ubl", "BR-S-08");

        assertTrue(finding.message().endsWith("and the official CII artefact of release"
                        + " 1.3.16 faults a difference this small."),
                "BR-S-08 warns with the artefact that faults the figure: "
                        + finding.message());
    }

    /**
     * Returns the warning one rule makes over one mutation of the set.
     *
     * @param mutation the identifier of the mutation
     * @param rule     the rule identifier
     * @return the finding, which the test fails without
     */
    private static RuleFinding warning(String mutation, String rule) {
        Oracle.Mutation aimed = Oracle.all().stream()
                .filter(one -> one.id().equals(mutation))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the set carries " + mutation));
        SemanticDocument document = READER.read(Oracle.apply(aimed)).document();
        Optional<RuleFinding> found = ENGINE.evaluate(document).stream()
                .filter(finding -> finding.code().equals(rule))
                .filter(finding -> finding.severity() == RuleSeverity.WARNING)
                .findFirst();
        assertTrue(found.isPresent(), mutation + " makes " + rule + " warn");
        return found.orElseThrow();
    }

    /** Every rule of the family is in the matrix, and the matrix holds nothing else. */
    @Test
    void theMatrixCoversTheWholeFamily() {
        assertEquals(List.of("BR-AE-08", "BR-AF-08", "BR-AG-08", "BR-CO-17", "BR-E-08",
                        "BR-G-08", "BR-IC-08", "BR-O-08", "BR-S-08", "BR-Z-08"),
                List.copyOf(readings().keySet().stream().map(String.class::cast).sorted()
                        .toList()));
    }

    /**
     * Tells whether a row records the assertion of the strict reading as one that can
     * report at all. {@code Oracle.json} reads every scalar as a string, so the recorded
     * {@code false} arrives as one; a row that says nothing can report.
     */
    private static boolean fires(Map<?, ?> row) {
        return !"false".equals(row.get("canFire"));
    }

    private static Map<?, ?> tolerances() {
        Map<?, ?> ledger = (Map<?, ?>) Oracle.json(
                Oracle.bytes("/conformance/rules/ledger.json"));
        return (Map<?, ?>) ledger.get("tolerances");
    }

    private static Map<?, ?> readings() {
        return (Map<?, ?>) tolerances().get("readings");
    }

    /**
     * Returns what one artefact asks of one rule.
     *
     * @param syntax the artefact, {@code ubl} or {@code cii}
     * @param rule   the rule identifier
     * @return one of the three readings
     */
    private static String reading(String syntax, String rule) {
        String assertion = assertionOf(Oracle.text(ARTEFACTS.get(syntax)), rule);
        for (int at = 0; at + 1 < assertion.length(); at++) {
            if (assertion.charAt(at) != '-') {
                continue;
            }
            int comparison = comparisonAfterSubtractingOne(assertion, at);
            if (comparison >= 0) {
                return assertion.startsWith("<=", comparison) ? ONE_UNIT_CLOSED : ONE_UNIT;
            }
        }
        return EQUALITY;
    }

    /**
     * Returns where the comparison stands that this hyphen subtracts one for, or -1 where
     * the hyphen is not the one unit of a tolerance.
     *
     * @param assertion the expression
     * @param at        the position of the hyphen
     * @return the position of the comparison operator, or -1
     */
    private static int comparisonAfterSubtractingOne(String assertion, int at) {
        int digit = skip(assertion, at + 1);
        if (digit >= assertion.length() || assertion.charAt(digit) != '1') {
            return -1;
        }
        int after = skip(assertion, digit + 1);
        if (after < assertion.length() && assertion.charAt(after) == ')') {
            after = skip(assertion, after + 1);
        }
        return after < assertion.length() && assertion.charAt(after) == '<' ? after : -1;
    }

    /** Returns the position of the first character from {@code from} that is not a space. */
    private static int skip(String assertion, int from) {
        int at = from;
        while (at < assertion.length() && assertion.charAt(at) == ' ') {
            at++;
        }
        return at;
    }

    /**
     * Returns the test expression of the assertion an artefact reports one rule from.
     *
     * <p>A compiled Schematron writes the expression once as the condition that holds and
     * once as the attribute of the report it writes when it does not, and the rule
     * identifier stands inside the second. So the expression is the one that ends where
     * the identifier begins.
     */
    private static String assertionOf(String artefact, String rule) {
        String marker = "<xsl:attribute name=\"id\">" + rule + "</xsl:attribute>";
        int identifier = artefact.indexOf(marker);
        assertTrue(identifier > 0, "the artefact reports " + rule);
        String opening = "<svrl:failed-assert test=\"";
        int start = artefact.lastIndexOf(opening, identifier) + opening.length();
        String between = artefact.substring(start, identifier);
        return unescape(between.substring(0, between.lastIndexOf("\">")));
    }

    /** Returns an attribute value with the five XML entity references resolved. */
    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }
}
