package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.rules.en16931.En16931;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * What the engine costs at the size this project designs for.
 *
 * <p>Invoices of eighty megabytes with several hundred thousand lines are real, so the claim
 * that the engine is linear in the number of lines is measured rather than asserted, and it is
 * measured on every build: a change that turned a linear pass into a quadratic one fails here
 * rather than being noticed in the field.
 *
 * <p>The test is a ratio and not a stopwatch reading. Absolute times depend on the machine and
 * would make the build fail on a slow one; what must hold is the shape of the curve, so the
 * assertion is that ten times the lines costs well under ten times the squared factor —
 * a bound loose enough that no ordinary machine noise reaches it and tight enough that a scan
 * per line cannot pass it. The absolute times are printed for the record.
 *
 * <p><strong>What is measured is the processor time of the measuring thread, and the least of
 * {@value #RUNS} runs.</strong> A busy machine takes the wall clock away from a thread without
 * giving it any more work to do, and the two sizes of a ratio are measured minutes apart, so a
 * machine that grows busy between them moves the ratio and not the curve: measured by the wall
 * clock on this machine, one and the same evaluation cost 2771 ms idle and 29216 ms with two
 * dozen spinning processes beside it — a factor of 10.5 — while its processor time went from
 * 2676 ms to 3310 ms, a factor of 1.2. That is why this test failed once at a load average
 * above twenty and passed on the rerun. The bounds below are the ones it always had; the
 * measurement is what changed, and it changed towards what the bounds were always about.
 *
 * <p><strong>How large the largest document is, is a decision of whoever runs the build.</strong>
 * The document is built in memory, and three hundred thousand invoice lines need a heap that a
 * modest continuous integration machine does not have. So the largest size is the system
 * property {@value #MAX_LINES_PROPERTY} and its default is a hundred thousand lines, which the
 * two gibibytes this module asks for carry; the full size of the field report is one command
 * away and is what the table in {@code rules/README.md} was measured with:
 *
 * <pre>{@code mvn -pl esj-rules test -Desj.rules.linearity.maxLines=300000 -DargLine=-Xmx4g}</pre>
 *
 * <p>Sizes above the maximum are left out and the sizes below it are kept, so the curve is
 * always measured over at least one factor of ten.
 *
 * <p>The pack is five rules of the three kinds that cost differently: one that sums over every
 * line, one that is evaluated once per line, one that walks a group inside a line, one that
 * counts, and one that asks the same document-wide aggregate as the first — which the engine
 * is expected to answer without walking the lines a second time.
 *
 * <p>An invoice has a second count, and the second measurement is over the pack this build
 * ships rather than over a synthetic one. A rule that compares a VAT breakdown with the parts
 * of the invoice in its category has both the lines and the breakdowns to be linear in, and a
 * rule written in Java is not in the synthetic pack at all, so the shape that matters there is
 * invisible to the first measurement. The second varies the two counts independently and
 * asserts that what a breakdown costs does not grow with the number of lines, which is the
 * difference between a sum and a product.
 */
class LinearityTest {

    /** The system property that says how large the largest measured document is. */
    static final String MAX_LINES_PROPERTY = "esj.rules.linearity.maxLines";

    /** The largest size that is measured when the property is not set. */
    private static final int DEFAULT_MAX_LINES = 100_000;

    /** The sizes the curve is measured at, as far as the maximum allows. */
    private static final int[] SIZES = {10_000, 100_000, 300_000};

    /** How often a document is evaluated; the least of the runs is what is compared. */
    private static final int RUNS = 3;

    /** The processor-time clock of the measuring thread, where the runtime keeps one. */
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

    private static final String PACK = Packs.file(
            Packs.rule("BR-A",
                    "{\"eq\": [{\"value\": \"/BG-22/BT-106\"}, {\"sum\": \"/BG-25/*/BT-131\"}]}"),
            Packs.rule("BR-B", "/BG-25/*",
                    "{\"eq\": [{\"value\": \"/BT-131\"}, {\"round\": [{\"mul\": ["
                            + "{\"value\": \"/BT-129\"}, {\"value\": \"/BG-29/BT-146\"}]}, 2]}]}"),
            Packs.rule("BR-C", "/BG-25/*",
                    "{\"eq\": [{\"sum\": \"/BG-27/*/BT-136\"}, {\"const\": \"0\"}]}"),
            Packs.rule("BR-D", "{\"gt\": [{\"count\": \"/BG-25/*\"}, {\"const\": 0}]}"),
            Packs.rule("BR-E",
                    "{\"le\": [{\"sum\": \"/BG-25/*/BT-131\"}, {\"value\": \"/BG-22/BT-112\"}]}"));

    /**
     * Returns the sizes this run measures: every declared size up to the maximum, and the
     * smallest one in any case.
     *
     * @return the sizes, ascending
     */
    private static List<Integer> sizes() {
        int max = Integer.getInteger(MAX_LINES_PROPERTY, DEFAULT_MAX_LINES);
        List<Integer> kept = new ArrayList<>();
        for (int size : SIZES) {
            if (size <= max) {
                kept.add(size);
            }
        }
        if (kept.isEmpty()) {
            kept.add(SIZES[0]);
        }
        return kept;
    }

    /**
     * Returns what one evaluation of that document costs, in milliseconds of processor
     * time: the least of {@value #RUNS} runs, the first of which warms the engine up.
     *
     * @param document the document to evaluate
     * @return the cost of the cheapest run
     */
    private static double evaluate(SemanticDocument document) {
        return cost(Packs.engine(PACK), document, findings ->
                assertEquals(List.of(), findings, "the synthetic invoice is arithmetically sound"));
    }

    /**
     * Returns what one evaluation costs, in milliseconds of the processor time of this
     * thread: the least of {@value #RUNS} runs, the first of which warms the engine up.
     *
     * <p>The least is taken rather than the mean because every disturbance a measurement
     * can meet — another process, a collection, a page fault — adds to it and none takes
     * anything away, so the cheapest run is the closest this machine came to what the
     * work costs.
     *
     * @param engine   the compiled pack
     * @param document the document to evaluate
     * @param check    what the findings of every run have to satisfy
     * @return the cost of the cheapest run
     */
    private static double cost(RuleEngine engine, SemanticDocument document,
                               Consumer<List<RuleFinding>> check) {
        double least = Double.MAX_VALUE;
        for (int run = 0; run < RUNS; run++) {
            long started = processorNanos();
            List<RuleFinding> findings = engine.evaluate(document);
            least = Math.min(least, (processorNanos() - started) / 1e6);
            check.accept(findings);
        }
        return least;
    }

    /**
     * Returns the processor time this thread has had, and falls back to the wall clock
     * where the runtime keeps no such count. Every runtime this project builds on keeps
     * one; a runtime that does not is measured as this test was measured before, which is
     * a fair measurement on a machine that is not busy.
     *
     * @return the reading, in nanoseconds, of whatever clock this runtime offers
     */
    private static long processorNanos() {
        if (!THREADS.isCurrentThreadCpuTimeSupported()) {
            return System.nanoTime();
        }
        if (!THREADS.isThreadCpuTimeEnabled()) {
            THREADS.setThreadCpuTimeEnabled(true);
        }
        return THREADS.getCurrentThreadCpuTime();
    }

    @Test
    void theCostGrowsWithTheNumberOfLinesAndNotWithItsSquare() {
        List<Integer> sizes = sizes();
        List<Double> millis = new ArrayList<>(sizes.size());
        System.out.printf(Locale.ROOT, "esj-rules, five rules, the cheapest of %d evaluations,"
                + " processor time (largest size from -D%s):%n", RUNS, MAX_LINES_PROPERTY);
        for (int lines : sizes) {
            double elapsed = evaluate(Documents.scale(lines));
            millis.add(elapsed);
            System.out.printf(Locale.ROOT, "  %,10d lines  %8.1f ms  (%.4f ms per thousand)%n",
                    lines, elapsed, elapsed / lines * 1000);
        }
        for (int i = 0; i < sizes.size(); i++) {
            for (int j = i + 1; j < sizes.size(); j++) {
                assertGrowsAtMostLinearly(sizes.get(i), millis.get(i), sizes.get(j), millis.get(j));
            }
        }
    }

    /** The smaller of the two line counts and of the two breakdown counts crossed below. */
    private static final int FEW = 2_000;

    /** The larger breakdown count. */
    private static final int MANY_BREAKDOWNS = 20_000;

    /** The larger line count. */
    private static final int MANY_LINES = 50_000;

    /**
     * The factor by which a breakdown may cost more over the larger document than over the
     * smaller one before this is called a product rather than a sum.
     *
     * <p>Three is slack for the part of the added cost that is not the rule: eighteen thousand
     * more breakdown groups are eighteen thousand more group instances to index, and that cost
     * is paid over both line counts and lands in both increments. A scan of the lines per
     * breakdown is six times the smaller increment at these sizes, so three separates the two
     * without sitting on either.
     */
    private static final double SLACK = 3.0;

    @Test
    void theCostOfAVatBreakdownDoesNotGrowWithTheNumberOfLines() {
        RuleEngine engine = En16931.engine(Packs.REGISTRY);
        engine.evaluate(Documents.breakdowns(100, 10));
        System.out.printf(Locale.ROOT, "esj-rules, the pack this build ships, the cheapest of"
                + " %d evaluations, processor time:%n", RUNS);
        double overFewLines = measure(engine, FEW, MANY_BREAKDOWNS) - measure(engine, FEW, FEW);
        double overManyLines =
                measure(engine, MANY_LINES, MANY_BREAKDOWNS) - measure(engine, MANY_LINES, FEW);

        double allowed = Math.max(overFewLines, 1.0) * SLACK;
        assertTrue(overManyLines <= allowed,
                () -> String.format(Locale.ROOT,
                        "%,d more VAT breakdowns cost %.1f ms over %,d invoice lines and %.1f ms"
                                + " over %,d, so a breakdown is priced by the number of lines",
                        MANY_BREAKDOWNS - FEW, overFewLines, FEW, overManyLines, MANY_LINES));
    }

    /**
     * Runs the shipped pack over a document of that shape and returns what the run cost.
     *
     * @param engine     the compiled pack
     * @param lines      how many invoice lines the document states
     * @param breakdowns how many VAT breakdowns it states
     * @return the cost of the cheapest evaluation, in milliseconds of processor time
     */
    private static double measure(RuleEngine engine, int lines, int breakdowns) {
        SemanticDocument document = Documents.breakdowns(lines, breakdowns);
        double elapsed = cost(engine, document, findings -> {
            for (RuleFinding finding : findings) {
                assertTrue(!"BR-S-08".equals(finding.code()),
                        () -> "the measured document must not fault the rule whose cost is"
                                + " measured, or the rule leaves its loop early: "
                                + finding.message());
            }
        });
        System.out.printf(Locale.ROOT, "  %,7d lines  %,7d breakdowns  %8.1f ms%n",
                lines, breakdowns, elapsed);
        return elapsed;
    }

    /**
     * Asserts that the time per line did not grow by more than a factor of four between two
     * sizes.
     *
     * <p>Four is the slack: a document ten times larger does not fit the same caches, and
     * the collections its allocation costs are paid by the thread that measures. A pass
     * that scanned the document once per line would be ten times worse per line here, not
     * four.
     */
    private static void assertGrowsAtMostLinearly(int fewer, double fewerMillis,
                                                  int more, double moreMillis) {
        double perLineFewer = fewerMillis / fewer;
        double perLineMore = moreMillis / more;
        assertTrue(perLineMore <= perLineFewer * 4.0,
                () -> String.format(Locale.ROOT, "%,d lines cost %.4f ms per thousand and %,d lines cost %.4f,"
                                + " which is more than four times as much per line",
                        fewer, perLineFewer * 1000, more, perLineMore * 1000));
    }
}
