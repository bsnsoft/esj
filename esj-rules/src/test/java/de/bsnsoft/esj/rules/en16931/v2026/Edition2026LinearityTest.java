package de.bsnsoft.esj.rules.en16931.v2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.CodeLists;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RulePack;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The pack of this edition stays linear in the number of invoice lines and in the number of
 * VAT breakdowns.
 *
 * <p>Its rules join four groups that carry a VAT category and match a breakdown against the
 * parts of the invoice under it. Written naively that is a scan of the lines per breakdown,
 * whose cost is the product of two counts an invoice may both make large; written as it is,
 * the parts are bucketed by their whole key in one pass for the run, and the cost is the sum
 * of the two. These measurements are what keep the second from quietly becoming the first.
 *
 * <p>They are ratios and not stopwatch readings, and they are taken on the processor time of
 * the measuring thread rather than on the clock, because a build machine runs other work at
 * the same time. The bound is generous for the same reason: what a quadratic pass would do to
 * a ratio is not a few per cent.
 */
class Edition2026LinearityTest {

    private static final Registry REGISTRY = Registry.forEdition("2026");

    private static final RuleEngine ENGINE = new En16931V2026Pack().engine(REGISTRY);

    /**
     * The rules of the pack written in Java, compiled alone: the manifest with its code list
     * snapshots and its Java rules, and none of the rules written in the rule language.
     */
    private static final RuleEngine JAVA_RULES = javaRulesAlone();

    /** The two sizes measured, ten times apart. */
    private static final int SMALL = 2_000;

    private static final int LARGE = 20_000;

    /**
     * How many times the cost per line may grow over ten times the lines before the shape of
     * the answer is called into question. A linear pass holds it near one; a quadratic one
     * would multiply it by ten.
     */
    private static final double BOUND = 3.0;

    @Test
    void theCostGrowsWithTheNumberOfLinesAndNotWithItsSquare() {
        evaluate(invoice(SMALL));
        evaluate(invoice(LARGE));

        double small = perLine(SMALL);
        double large = perLine(LARGE);

        assertTrue(large <= small * BOUND,
                "ten times the lines cost " + (large / small)
                        + " times as much per line, which is more than a linear pass explains"
                        + " (" + small + " against " + large + " nanoseconds per line)");
    }

    private static double perLine(int lines) {
        SemanticDocument document = invoice(lines);
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long started = threads.getCurrentThreadCpuTime();
        evaluate(document);
        return (threads.getCurrentThreadCpuTime() - started) / (double) lines;
    }

    /**
     * Ten times the combinations cost at most {@value #BOUND} times as much per combination.
     *
     * <p>The rules that join a group instance with another one are written in Java, because
     * the rule language has no operator for a join: every line, allowance and charge finds the
     * breakdown of its combination ({@code BR-CO-18}), every part of an exempt category the
     * breakdown of its exemption reason (the {@code *-01} rules), and every breakdown in the
     * VAT accounting currency its partner in the invoice currency ({@code BR-CO-49},
     * {@code BR-CO-50}). A join written as a scan costs the product of the two counts. The
     * invoice measured here gives every line a combination of its own, so the breakdowns grow
     * with the lines, and the Java rules run alone: the join is almost all they cost, so a
     * product shows in the ratio instead of sitting under the linear cost of the two hundred
     * rules written in the rule language, which
     * {@link #theCostGrowsWithTheNumberOfLinesAndNotWithItsSquare()} measures.
     */
    @Test
    void theCostGrowsWithTheBreakdownsAndNotWithTheirProductWithTheLines() {
        SemanticDocument few = combinations(SMALL);
        SemanticDocument many = combinations(LARGE);
        JAVA_RULES.evaluate(few);
        JAVA_RULES.evaluate(many);

        double small = cheapest(few) / SMALL;
        double large = cheapest(many) / LARGE;

        assertTrue(large <= small * BOUND,
                "ten times the combinations cost " + (large / small)
                        + " times as much per combination, which is more than a linear pass"
                        + " explains (" + small + " against " + large
                        + " nanoseconds per combination)");
    }

    /**
     * Returns what the Java rules alone cost over a document, in nanoseconds of the processor
     * time of this thread: the least of three runs, because every disturbance a measurement
     * meets adds to it and none takes anything away.
     */
    private static double cheapest(SemanticDocument document) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        double least = Double.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            long started = threads.getCurrentThreadCpuTime();
            List<RuleFinding> findings = JAVA_RULES.evaluate(document);
            least = Math.min(least, threads.getCurrentThreadCpuTime() - started);
            assertEquals(List.of(), findings, "the measured invoice must not fault a rule whose"
                    + " cost is measured, or the rule leaves its loop early");
        }
        return least;
    }

    private static RuleEngine javaRulesAlone() {
        RulePack pack = new En16931V2026Pack().pack();
        RulePack javaOnly = new RulePack(pack.id(), pack.version(), pack.edition(),
                pack.verifiedAgainst(), pack.description(), pack.codeLists(),
                pack.codeListSources(), pack.javaRules(), List.of(), List.of(), List.of());
        return RuleEngine.compile(javaOnly, REGISTRY, CodeLists.bundled(pack),
                En16931V2026Pack.javaRules());
    }

    private static void evaluate(SemanticDocument document) {
        ENGINE.evaluate(document);
    }

    /**
     * An invoice of many lines at one rate, with one breakdown per hundred lines so that both
     * counts of the measurement grow together.
     */
    private static SemanticDocument invoice(int lines) {
        SemanticDocument.Builder builder = SemanticDocument.builder()
                .semanticModel(Documents2026.EDITION)
                .put("/BT-1", "RE-2026-0001")
                .put("/BT-2", "2026-01-15")
                .put("/BT-3", "380")
                .put("/BT-5", "EUR")
                .put("/BT-9", "2026-02-15")
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2026")
                .put("/BG-4/BT-27", "Example GmbH")
                .put("/BG-4/BT-31", "DE123456789")
                .put("/BG-4/BG-5/BT-40", "DE")
                .put("/BG-7/BT-44", "Muster AG")
                .put("/BG-7/BG-8/BT-55", "DE");
        BigDecimal total = BigDecimal.valueOf(lines).multiply(new BigDecimal("100"));
        put(builder, "/BG-22/BT-106", total.toPlainString());
        put(builder, "/BG-22/BT-109", total.toPlainString());
        put(builder, "/BG-22/BT-112", total.toPlainString());
        put(builder, "/BG-22/BT-115", total.toPlainString());
        int breakdowns = Math.max(1, lines / 100);
        for (int i = 0; i < breakdowns; i++) {
            BigDecimal taxable = BigDecimal.valueOf(lines / breakdowns)
                    .multiply(new BigDecimal("100"));
            put(builder, "/BG-23/" + i + "/BT-116", taxable.toPlainString());
            put(builder, "/BG-23/" + i + "/BT-117", "0");
            put(builder, "/BG-23/" + i + "/BT-118", "Z");
            put(builder, "/BG-23/" + i + "/BT-119", "0");
            put(builder, "/BG-23/" + i + "/BT-120", "Reason " + i);
        }
        for (int i = 0; i < lines; i++) {
            put(builder, "/BG-25/" + i + "/BT-126", String.valueOf(i + 1));
            put(builder, "/BG-25/" + i + "/BT-129", "1");
            put(builder, "/BG-25/" + i + "/BT-130", "C62");
            put(builder, "/BG-25/" + i + "/BT-131", "100");
            put(builder, "/BG-25/" + i + "/BG-29/BT-146", "100");
            put(builder, "/BG-25/" + i + "/BG-29/BT-149", "1");
            put(builder, "/BG-25/" + i + "/BG-30/BT-151", "Z");
            put(builder, "/BG-25/" + i + "/BG-30/BT-152", "0");
            put(builder, "/BG-25/" + i + "/BG-30/BT-194", "Reason " + (i % breakdowns));
            put(builder, "/BG-25/" + i + "/BG-31/BT-153", "Line " + i);
        }
        return builder.build();
    }

    /**
     * An invoice of twice as many lines as combinations. Half the lines are at the standard
     * rate, each at a rate of its own, with a breakdown in the invoice currency and one in the
     * VAT accounting currency for that rate; the other half are exempt, each for a reason of
     * its own, with a breakdown for that reason. The breakdowns in the invoice currency are
     * written in the reverse order of the lines, so that a scan in document order would walk
     * past most of them.
     */
    private static SemanticDocument combinations(int count) {
        SemanticDocument.Builder builder = SemanticDocument.builder()
                .semanticModel(Documents2026.EDITION)
                .put("/BT-1", "RE-2026-0001")
                .put("/BT-2", "2026-01-15")
                .put("/BT-3", "380")
                .put("/BT-5", "EUR")
                .put("/BT-6", "SEK")
                .put("/BT-9", "2026-02-15")
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2026")
                .put("/BG-4/BT-27", "Example GmbH")
                .put("/BG-4/BT-31", "DE123456789")
                .put("/BG-4/BG-5/BT-40", "DE")
                .put("/BG-7/BT-44", "Muster AG")
                .put("/BG-7/BG-8/BT-55", "DE");
        put(builder, "/BT-167", "10");
        BigDecimal vat = BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            BigDecimal rate = BigDecimal.valueOf(i + 1L, 2);
            String percent = plain(rate);
            line(builder, i, "S", percent, null);
            String at = "/BG-23/" + (count - 1 - i);
            put(builder, at + "/BT-116", "100");
            put(builder, at + "/BT-117", percent);
            put(builder, at + "/BT-118", "S");
            put(builder, at + "/BT-119", percent);
            String converted = "/BG-23/" + (count + i);
            put(builder, converted + "/BT-116", "1000");
            put(builder, converted + "/BT-117", plain(rate.multiply(BigDecimal.TEN)));
            put(builder, converted + "/BT-118", "S");
            put(builder, converted + "/BT-119", percent);
            put(builder, converted + "/BT-184", "SEK");
            vat = vat.add(rate);
            line(builder, count + i, "E", "0", "Reason " + i);
            String exempt = "/BG-23/" + (3 * count - 1 - i);
            put(builder, exempt + "/BT-116", "100");
            put(builder, exempt + "/BT-117", "0");
            put(builder, exempt + "/BT-118", "E");
            put(builder, exempt + "/BT-119", "0");
            put(builder, exempt + "/BT-120", "Reason " + i);
        }
        BigDecimal net = BigDecimal.valueOf(200L * count);
        put(builder, "/BG-22/BT-106", plain(net));
        put(builder, "/BG-22/BT-109", plain(net));
        put(builder, "/BG-22/BT-110", plain(vat));
        put(builder, "/BG-22/BT-111", plain(vat.multiply(BigDecimal.TEN)));
        put(builder, "/BG-22/BT-112", plain(net.add(vat)));
        put(builder, "/BG-22/BT-115", plain(net.add(vat)));
        return builder.build();
    }

    /** One invoice line of a hundred euro in a category, at a rate, for a reason or none. */
    private static void line(SemanticDocument.Builder builder, int index, String category,
                             String rate, String reason) {
        String at = "/BG-25/" + index;
        put(builder, at + "/BT-126", String.valueOf(index + 1));
        put(builder, at + "/BT-129", "1");
        put(builder, at + "/BT-130", "C62");
        put(builder, at + "/BT-131", "100");
        put(builder, at + "/BG-29/BT-146", "100");
        put(builder, at + "/BG-29/BT-149", "1");
        put(builder, at + "/BG-30/BT-151", category);
        put(builder, at + "/BG-30/BT-152", rate);
        if (reason != null) {
            put(builder, at + "/BG-30/BT-194", reason);
        }
        put(builder, at + "/BG-31/BT-153", "Line " + index);
    }

    /** A decimal in the form of this format: no exponent and no trailing fraction zero. */
    private static String plain(BigDecimal number) {
        return number.stripTrailingZeros().toPlainString();
    }

    private static void put(SemanticDocument.Builder builder, String path, String content) {
        builder.set(SemanticPath.of(path), SemanticValue.of(content));
    }
}
