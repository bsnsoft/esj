package de.bsnsoft.esj.rules.en16931;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.rules.RuleContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of an invoice the VAT rules of EN 16931 are about, read once and put side by
 * side.
 *
 * <p>Four groups of the model carry a VAT category code and, with it, an amount and usually
 * a rate: the invoice line, the document level allowance, the document level charge and the
 * VAT breakdown. Every VAT rule that the rule language cannot express is a statement about
 * two of those groups at once — the taxable amount of a breakdown against the lines,
 * allowances and charges of its category — and a statement of that shape needs the three
 * values of one group instance together. A rule cannot read them one instance at a time,
 * because it may not write an occurrence index; it reads each business term as a pattern and
 * joins the answers by the group instance they lie in, which is what this class does.
 *
 * <p>The cost is one pass per business term and not one pass per instance, so an invoice of
 * three hundred thousand lines costs three hundred thousand reads and not their square. Each
 * of the four joins is made once for a whole run and shared by every rule that asks for it
 * ({@link RuleContext#shared(String, java.util.function.Supplier)}): fifteen rules of this
 * pack read these four groups, and without that each of them would join the lines again.
 *
 * <p>{@link Totals} is the second half of the same concern. A rule that compares a VAT
 * breakdown with the parts of the invoice in its category must not walk the lines once per
 * breakdown, because an invoice may carry many of both and the product of the two is a cost
 * with no bound in the document. The lines, the charges and the allowances are therefore
 * bucketed by VAT category and by category and rate in one pass, once per run, and a
 * breakdown is a lookup.
 *
 * <p>Instances are read at the document context. A rule that uses this class therefore
 * declares the context {@code /}, reports once for the document, and names the group
 * instance it faulted in its message.
 */
final class Vat {

    private Vat() {
        throw new AssertionError("no instances");
    }

    /**
     * One instance of a group that carries a VAT category: where it is, which category it
     * states, at which rate, and with which amount.
     *
     * @param path     the path of the group instance
     * @param category the VAT category code the instance states, or {@code null}
     * @param rate     the VAT rate the instance states, or {@code null}
     * @param amount   the amount the instance carries, or {@code null}
     */
    record Item(SemanticPath path, String category, BigDecimal rate, BigDecimal amount) {

        /**
         * Tells whether this instance states a VAT category.
         *
         * @param code the category code
         * @return whether the instance states exactly that code
         */
        boolean is(String code) {
            return code.equals(category);
        }
    }

    private static final List<String> LINES =
            List.of("/BG-25/*/BG-30/BT-151", "/BG-25/*/BG-30/BT-152", "/BG-25/*/BT-131");

    private static final List<String> ALLOWANCES =
            List.of("/BG-20/*/BT-95", "/BG-20/*/BT-96", "/BG-20/*/BT-92");

    private static final List<String> CHARGES =
            List.of("/BG-21/*/BT-102", "/BG-21/*/BT-103", "/BG-21/*/BT-99");

    private static final List<String> BREAKDOWNS =
            List.of("/BG-23/*/BT-118", "/BG-23/*/BT-119", "/BG-23/*/BT-116");

    /**
     * Every path pattern this class reads from the document root.
     *
     * <p>A rule that uses this class declares these as its own root patterns
     * ({@link de.bsnsoft.esj.rules.JavaRule#roots()}), so that the index of a
     * document is built for them in the one pass it makes anyway rather than rebuilt when the
     * first VAT rule asks.
     */
    static final List<String> ROOTS = concat(LINES, ALLOWANCES, CHARGES, BREAKDOWNS);

    @SafeVarargs
    private static List<String> concat(List<String>... parts) {
        List<String> all = new ArrayList<>();
        for (List<String> part : parts) {
            all.addAll(part);
        }
        return List.copyOf(all);
    }

    /** The invoice lines, with the invoiced item VAT category, its rate and the net amount. */
    static List<Item> lines(RuleContext context) {
        return join(context, "Vat.lines", 2, LINES);
    }

    /** The document level allowances, with the VAT category, its rate and the amount. */
    static List<Item> allowances(RuleContext context) {
        return join(context, "Vat.allowances", 2, ALLOWANCES);
    }

    /** The document level charges, with the VAT category, its rate and the amount. */
    static List<Item> charges(RuleContext context) {
        return join(context, "Vat.charges", 2, CHARGES);
    }

    /** The VAT breakdown groups, with the VAT category, its rate and the taxable amount. */
    static List<Item> breakdowns(RuleContext context) {
        return join(context, "Vat.breakdowns", 2, BREAKDOWNS);
    }

    /**
     * Returns what the invoice lines, the document level charges and the document level
     * allowances come to, bucketed by VAT category and by VAT category and rate.
     *
     * <p>This is the sum every {@code *-08} rule of the standard compares a VAT breakdown
     * with: the line net amounts, plus the charges, minus the allowances, of one category.
     * It is built in one pass over the three joins and shared by the whole run, so that a
     * document with many breakdowns and many lines costs the sum of the two and not their
     * product.
     *
     * @param context the invoice, at the document context
     * @return the buckets
     */
    static Totals totals(RuleContext context) {
        return context.shared("Vat.totals", () -> Totals.of(
                lines(context), charges(context), allowances(context)));
    }

    /**
     * The net amount of the lines, charges and allowances of each VAT category, and of each
     * category at each rate.
     *
     * <p>A category with no line, charge or allowance is not a key of either map and reads
     * as zero, which is what a sum over nothing is. A rate is keyed by its numeric value and
     * not by the way it was written, because {@code 7} and {@code 7.00} are one rate; an
     * instance that states no rate joins no rate bucket, since a rule that restricts by rate
     * is asking about instances that state one.
     */
    static final class Totals {

        private final Map<String, BigDecimal> byCategory;
        private final Map<String, BigDecimal> byCategoryAndRate;

        private Totals(Map<String, BigDecimal> byCategory,
                       Map<String, BigDecimal> byCategoryAndRate) {
            this.byCategory = byCategory;
            this.byCategoryAndRate = byCategoryAndRate;
        }

        private static Totals of(List<Item> lines, List<Item> charges, List<Item> allowances) {
            Map<String, BigDecimal> byCategory = new HashMap<>();
            Map<String, BigDecimal> byCategoryAndRate = new HashMap<>();
            add(byCategory, byCategoryAndRate, lines, false);
            add(byCategory, byCategoryAndRate, charges, false);
            add(byCategory, byCategoryAndRate, allowances, true);
            return new Totals(byCategory, byCategoryAndRate);
        }

        private static void add(Map<String, BigDecimal> byCategory,
                                Map<String, BigDecimal> byCategoryAndRate,
                                List<Item> items, boolean subtract) {
            for (Item item : items) {
                if (item.category() == null || item.amount() == null) {
                    continue;
                }
                BigDecimal amount = subtract ? item.amount().negate() : item.amount();
                byCategory.merge(item.category(), amount, BigDecimal::add);
                if (item.rate() != null) {
                    byCategoryAndRate.merge(key(item.category(), item.rate()), amount,
                            BigDecimal::add);
                }
            }
        }

        private static String key(String code, BigDecimal rate) {
            return code + " @ " + rate.stripTrailingZeros().toPlainString();
        }

        /**
         * Returns what the instances of one VAT category come to.
         *
         * @param code the VAT category code
         * @return the sum, exactly, which is zero over no instance
         */
        BigDecimal of(String code) {
            return byCategory.getOrDefault(code, BigDecimal.ZERO);
        }

        /**
         * Returns what the instances of one VAT category at one rate come to.
         *
         * @param code the VAT category code
         * @param rate the VAT rate
         * @return the sum, exactly, which is zero over no instance
         */
        BigDecimal of(String code, BigDecimal rate) {
            return byCategoryAndRate.getOrDefault(key(code, rate), BigDecimal.ZERO);
        }
    }

    private static List<Item> join(RuleContext context, String key, int depth,
                                   List<String> patterns) {
        return context.shared(key, () ->
                joined(context, depth, patterns.get(0), patterns.get(1), patterns.get(2)));
    }

    private static List<Item> joined(RuleContext context, int depth, String categoryPattern,
                                     String ratePattern, String amountPattern) {
        Map<SemanticPath, String> categories = new LinkedHashMap<>();
        for (Map.Entry<SemanticPath, String> read : context.texts(categoryPattern)) {
            categories.put(read.getKey().prefix(depth), read.getValue());
        }
        Map<SemanticPath, BigDecimal> rates = numbers(context, ratePattern, depth);
        Map<SemanticPath, BigDecimal> amounts = numbers(context, amountPattern, depth);
        List<SemanticPath> instances = new ArrayList<>(categories.keySet());
        for (SemanticPath instance : amounts.keySet()) {
            if (!categories.containsKey(instance)) {
                instances.add(instance);
            }
        }
        instances.sort(null);
        List<Item> items = new ArrayList<>(instances.size());
        for (SemanticPath instance : instances) {
            items.add(new Item(instance, categories.get(instance), rates.get(instance),
                    amounts.get(instance)));
        }
        return List.copyOf(items);
    }

    private static Map<SemanticPath, BigDecimal> numbers(RuleContext context, String pattern, int depth) {
        Map<SemanticPath, BigDecimal> found = new LinkedHashMap<>();
        for (Map.Entry<SemanticPath, BigDecimal> read : context.decimals(pattern)) {
            found.put(read.getKey().prefix(depth), read.getValue());
        }
        return found;
    }

    /**
     * Counts the instances of one VAT category.
     *
     * @param items the instances
     * @param code  the VAT category code
     * @return how many state that code
     */
    static int count(List<Item> items, String code) {
        int found = 0;
        for (Item item : items) {
            if (item.is(code)) {
                found++;
            }
        }
        return found;
    }

    /**
     * Tells whether an invoice line, a document level allowance or a document level charge
     * states a VAT category.
     *
     * @param context the invoice, at the document context
     * @param code    the VAT category code
     * @return whether at least one of the three states it
     */
    static boolean used(RuleContext context, String code) {
        return count(lines(context), code) > 0
                || count(allowances(context), code) > 0
                || count(charges(context), code) > 0;
    }
}
