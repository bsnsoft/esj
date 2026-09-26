package de.bsnsoft.esj.rules.en16931.v2026;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.rules.RuleContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The parts of an invoice the VAT rules of EN 16931-1:2026 are about, read once and put side
 * by side.
 *
 * <p>Four groups of the model carry a VAT category code and, with it, an amount, usually a
 * rate, and — new in this edition — an exemption reason and specification code, an exemption
 * reason text and a goods or services code: the invoice line, the document level allowance,
 * the document level charge or tax and the VAT breakdown. The rules of this edition that
 * compare a breakdown with the parts of the invoice under it match on all of those, which is
 * why the 2017 reading of the same rules cannot be carried over: a breakdown and a line that
 * agree on the category and differ on the exemption reason belong to two sums in this edition
 * and to one in the earlier one.
 *
 * <p>The cost is one pass per business term and not one pass per instance, and each join is
 * made once for a whole run and shared by every rule that asks for it
 * ({@link RuleContext#shared(String, java.util.function.Supplier)}). The sums are bucketed by
 * the whole key in one pass as well, and so are the combinations the breakdowns state and the
 * breakdowns a converted breakdown is paired with: a part of the invoice finds its breakdown
 * by one look-up and not by a pass over them all, so an invoice with many breakdowns and many
 * lines costs the sum of the two counts and not their product.
 */
final class VatParts {

    private VatParts() {
        throw new AssertionError("no instances");
    }

    /**
     * One instance of a group that carries a VAT category.
     *
     * @param path          the path of the group instance
     * @param category      the VAT category code the instance states, or {@code null}
     * @param rate          the VAT rate the instance states, or {@code null}
     * @param amount        the amount the instance carries, or {@code null}
     * @param exemptionCode the exemption reason and specification code, or {@code null}
     * @param exemptionText the exemption reason text, or {@code null}
     * @param goodsCode     the goods or services code, or {@code null}
     */
    record Part(SemanticPath path, String category, BigDecimal rate, BigDecimal amount,
                String exemptionCode, String exemptionText, String goodsCode) {

        /**
         * Tells whether this instance states a VAT category.
         *
         * @param code the category code
         * @return whether the instance states exactly that code
         */
        boolean is(String code) {
            return code.equals(category);
        }

        /**
         * Returns the key the rules of this edition match on, without the rate.
         *
         * @param divided whether the invoice divides this instance's VAT category by
         *                exemption reason, which is what makes the three terms part of the
         *                key at all
         * @return the category alone, or the category with the exemption reason and
         *         specification code, the exemption reason text and the goods or services
         *         code, in one string
         */
        String key(boolean divided) {
            return divided
                    ? join(category, exemptionCode, exemptionText, goodsCode)
                    : join(category);
        }

        /**
         * Returns the key with the rate, which the categories that levy at a rate match on.
         *
         * @param divided as for {@link #key(boolean)}
         * @return the key of {@link #key(boolean)} with the rate
         */
        String keyWithRate(boolean divided) {
            return key(divided) + SEPARATOR + "@"
                    + (rate == null ? "" : rate.stripTrailingZeros().toPlainString());
        }

        /**
         * Tells whether this instance states any of the three terms by which this edition
         * divides a VAT category.
         *
         * @return whether it states an exemption reason and specification code, an exemption
         *         reason text or a goods or services code
         */
        boolean statesAReason() {
            return exemptionCode != null || exemptionText != null || goodsCode != null;
        }

        /**
         * Returns what the exemption reason and the goods code of this instance are, for a
         * message.
         *
         * @return a phrase naming them, empty where the instance states none of the three
         */
        String reason() {
            List<String> parts = new ArrayList<>();
            if (exemptionCode != null) {
                parts.add("the exemption reason and specification code " + exemptionCode);
            }
            if (exemptionText != null) {
                parts.add("an exemption reason text");
            }
            if (goodsCode != null) {
                parts.add("the goods or services code " + goodsCode);
            }
            return parts.isEmpty() ? "" : " with " + String.join(", ", parts);
        }
    }

    /**
     * What two instances are compared on, as a key of a hash set: the category, the rate where
     * it is compared, and — where the invoice divides the category by exemption reason — the
     * exemption reason and specification code, the exemption reason text and, where it is
     * compared, the goods or services code.
     *
     * <p>Two keys are equal exactly where the instances they stand for agree on each of those
     * in the sense of {@link Objects#equals}; the rate carries no trailing zeros, so that
     * {@code 19} and {@code 19.00} are one key, as they are one number.
     *
     * @param category      the VAT category code
     * @param rate          the rate without trailing zeros, or {@code null} where the rate is
     *                      not compared or the instance states none
     * @param exemptionCode the exemption reason and specification code, where compared
     * @param exemptionText the exemption reason text, where compared
     * @param goodsCode     the goods or services code, where compared
     */
    record Combination(String category, BigDecimal rate, String exemptionCode,
                       String exemptionText, String goodsCode) {

        /**
         * Returns what an instance states, as far as a rule compares it.
         *
         * @param part          the instance
         * @param withRate      whether the rate is part of the key; an instance that states
         *                      no rate has none in it either way
         * @param divided       whether the invoice divides the category of the instance by
         *                      exemption reason
         * @param withGoodsCode whether the goods or services code is part of the key where
         *                      the category is divided
         * @return the key
         */
        static Combination of(Part part, boolean withRate, boolean divided,
                              boolean withGoodsCode) {
            BigDecimal rate = withRate && part.rate() != null
                    ? part.rate().stripTrailingZeros() : null;
            return divided
                    ? new Combination(part.category(), rate, part.exemptionCode(),
                            part.exemptionText(), withGoodsCode ? part.goodsCode() : null)
                    : new Combination(part.category(), rate, null, null, null);
        }
    }

    /**
     * The character that keeps the parts of a key apart, chosen because no code or text of
     * a business term carries it.
     */
    private static final char SEPARATOR = 31;

    private static String join(String... values) {
        StringBuilder key = new StringBuilder();
        for (String value : values) {
            key.append(value == null ? "" : value).append(SEPARATOR);
        }
        return key.toString();
    }

    private static final List<String> BREAKDOWNS = List.of(
            "/BG-23/*/BT-118", "/BG-23/*/BT-119", "/BG-23/*/BT-116",
            "/BG-23/*/BT-121", "/BG-23/*/BT-120", "/BG-23/*/BT-210");

    private static final List<String> LINES = List.of(
            "/BG-25/*/BG-30/BT-151", "/BG-25/*/BG-30/BT-152", "/BG-25/*/BT-131",
            "/BG-25/*/BG-30/BT-195", "/BG-25/*/BG-30/BT-194", "/BG-25/*/BG-31/BT-196");

    private static final List<String> ALLOWANCES = List.of(
            "/BG-20/*/BT-95", "/BG-20/*/BT-96", "/BG-20/*/BT-92",
            "/BG-20/*/BT-174", "/BG-20/*/BT-173", "/BG-20/*/BT-213");

    private static final List<String> CHARGES = List.of(
            "/BG-21/*/BT-102", "/BG-21/*/BT-103", "/BG-21/*/BT-99",
            "/BG-21/*/BT-176", "/BG-21/*/BT-175", "/BG-21/*/BT-214");

    /** The business terms every rule of this package declares that it reads. */
    static final List<String> TERMS = List.of(
            "BT-5", "BT-92", "BT-95", "BT-96", "BT-99", "BT-102", "BT-103", "BT-116", "BT-117",
            "BT-118", "BT-119", "BT-120", "BT-121", "BT-131", "BT-151", "BT-152", "BT-173",
            "BT-174", "BT-175", "BT-176", "BT-184", "BT-194", "BT-195", "BT-196", "BT-210",
            "BT-213", "BT-214");

    /** The currency a VAT breakdown names for its amounts. */
    private static final String BREAKDOWN_CURRENCY = "/BG-23/*/BT-184";

    /** The tax amount of a VAT breakdown. */
    private static final String BREAKDOWN_TAX = "/BG-23/*/BT-117";

    /** Every path pattern this class reads from the document root. */
    static final List<String> ROOTS = roots();

    private static List<String> roots() {
        List<String> all = new ArrayList<>();
        all.addAll(BREAKDOWNS);
        all.addAll(LINES);
        all.addAll(ALLOWANCES);
        all.addAll(CHARGES);
        all.add(BREAKDOWN_TAX);
        all.add(BREAKDOWN_CURRENCY);
        return List.copyOf(all);
    }

    /**
     * Returns the VAT breakdown groups whose amounts are in the invoice currency.
     *
     * <p>This edition lets a breakdown state its amounts in the VAT accounting currency
     * instead, and names the currency in BT-184; where it names none, the invoice currency
     * (BT-5) is meant. Every comparison of a breakdown with the parts of the invoice under it
     * is a comparison in the invoice currency, so a breakdown in another currency is not one
     * of these. {@link #accounting(RuleContext)} returns the others.
     *
     * @param context the invoice, at the document context
     * @return the breakdowns in the invoice currency, in document order
     */
    static List<Part> breakdowns(RuleContext context) {
        return context.shared("VatParts.breakdowns", () -> {
            List<Part> inInvoiceCurrency = new ArrayList<>();
            for (Part breakdown : allBreakdowns(context)) {
                if (inInvoiceCurrency(context, breakdown)) {
                    inInvoiceCurrency.add(breakdown);
                }
            }
            return List.copyOf(inInvoiceCurrency);
        });
    }

    /**
     * Returns the VAT breakdown groups that name a currency other than the invoice currency
     * for their amounts.
     *
     * @param context the invoice, at the document context
     * @return those breakdowns, in document order
     */
    static List<Part> accounting(RuleContext context) {
        return context.shared("VatParts.accounting", () -> {
            List<Part> elsewhere = new ArrayList<>();
            for (Part breakdown : allBreakdowns(context)) {
                if (!inInvoiceCurrency(context, breakdown)) {
                    elsewhere.add(breakdown);
                }
            }
            return List.copyOf(elsewhere);
        });
    }

    /**
     * Returns the currency a VAT breakdown names for its amounts.
     *
     * @param context   the invoice, at the document context
     * @param breakdown the breakdown
     * @return the currency code of BT-184, or an empty optional where the breakdown names none
     */
    static Optional<String> currencyOf(RuleContext context, Part breakdown) {
        return Optional.ofNullable(currencies(context).get(breakdown.path()));
    }

    /**
     * Returns the tax amount a VAT breakdown states.
     *
     * @param context   the invoice, at the document context
     * @param breakdown the breakdown
     * @return the tax amount (BT-117), or an empty optional where the breakdown states none
     */
    static Optional<BigDecimal> taxAmount(RuleContext context, Part breakdown) {
        return Optional.ofNullable(context.shared("VatParts.taxAmounts",
                () -> Map.copyOf(numbers(context, BREAKDOWN_TAX, 2))).get(breakdown.path()));
    }

    private static List<Part> allBreakdowns(RuleContext context) {
        return join(context, "VatParts.allBreakdowns", 2, BREAKDOWNS);
    }

    private static Map<SemanticPath, String> currencies(RuleContext context) {
        return context.shared("VatParts.currencies",
                () -> Map.copyOf(texts(context, BREAKDOWN_CURRENCY, 2)));
    }

    private static boolean inInvoiceCurrency(RuleContext context, Part breakdown) {
        String currency = currencies(context).get(breakdown.path());
        return currency == null || currency.equals(context.text("/BT-5").orElse(null));
    }

    /**
     * Returns the invoice lines.
     *
     * @param context the invoice, at the document context
     * @return the lines, in document order
     */
    static List<Part> lines(RuleContext context) {
        return join(context, "VatParts.lines", 2, LINES);
    }

    /**
     * Returns the document level allowances.
     *
     * @param context the invoice, at the document context
     * @return the allowances, in document order
     */
    static List<Part> allowances(RuleContext context) {
        return join(context, "VatParts.allowances", 2, ALLOWANCES);
    }

    /**
     * Returns the document level charges and taxes.
     *
     * @param context the invoice, at the document context
     * @return the charges and taxes, in document order
     */
    static List<Part> charges(RuleContext context) {
        return join(context, "VatParts.charges", 2, CHARGES);
    }

    /**
     * Returns the taxable amounts of the invoice bucketed by the key the rules of this
     * edition match on: the line net amounts plus the document level charges and taxes less
     * the document level allowances.
     *
     * @param context the invoice, at the document context
     * @return the buckets, by key without the rate and by key with it
     */
    static Totals totals(RuleContext context) {
        return context.shared("VatParts.totals", () -> Totals.of(divided(context),
                lines(context), charges(context), allowances(context)));
    }

    /**
     * Returns the combinations the VAT breakdowns in the invoice currency state, each once.
     *
     * <p>A breakdown is entered twice: with its rate, for a part that states one, and without
     * it, for a part that states none and is matched on the rest. The exemption reason and the
     * goods or services code are part of the key where the invoice divides the category
     * ({@link #divided}). Whether a part of the invoice has a breakdown of its own is then one
     * look-up of {@code Combination.of(part, true, divided, true)}, which is what makes the
     * question cost the number of parts plus the number of breakdowns and not their product.
     *
     * @param context the invoice, at the document context
     * @return the combinations, as {@link Combination#of} builds them
     */
    static Set<Combination> combinations(RuleContext context) {
        return context.shared("VatParts.combinations", () -> {
            Set<String> split = divided(context);
            Set<Combination> stated = new HashSet<>();
            for (Part breakdown : breakdowns(context)) {
                if (breakdown.category() == null) {
                    continue;
                }
                boolean divides = split.contains(breakdown.category());
                stated.add(Combination.of(breakdown, false, divides, true));
                stated.add(Combination.of(breakdown, true, divides, true));
            }
            return Set.copyOf(stated);
        });
    }

    /**
     * Returns the VAT breakdowns in the invoice currency by the key a breakdown in the VAT
     * accounting currency is paired on: the category, the rate, the exemption reason and
     * specification code, the exemption reason text and the goods or services code
     * ({@link Part#keyWithRate(boolean)} with every term). Where two breakdowns state the same
     * key, the first in document order is the one kept, which is the one a pass over them in
     * that order would have found.
     *
     * @param context the invoice, at the document context
     * @return the breakdowns, by that key
     */
    static Map<String, Part> partners(RuleContext context) {
        return context.shared("VatParts.partners", () -> {
            Map<String, Part> first = new HashMap<>();
            for (Part breakdown : breakdowns(context)) {
                first.putIfAbsent(breakdown.keyWithRate(true), breakdown);
            }
            return Map.copyOf(first);
        });
    }

    /**
     * Returns the VAT categories this invoice divides by exemption reason.
     *
     * <p>This edition asks for one VAT breakdown per exemption reason and says of the
     * comparison between a breakdown and the parts under it that it holds "where
     * applicable". What makes it applicable is read here as the invoice stating one of the
     * three terms the comparison is about — the exemption reason and specification code, the
     * exemption reason text and the goods or services code — on an invoice line, a document
     * level allowance or a document level charge or tax of that category. Where none of them
     * is stated, the category is not divided, a part belongs to a breakdown by category
     * alone, and the rules decide what the rules of the 2017 edition decide about the same
     * document. The reading is this project's, as everything about this edition's rules is;
     * {@code conformance/rules-2026/ledger.md} says so and names what it costs.
     *
     * @param context the invoice, at the document context
     * @return the VAT category codes that are divided, which may be empty
     */
    static Set<String> divided(RuleContext context) {
        return context.shared("VatParts.divided", () -> {
            Set<String> codes = new LinkedHashSet<>();
            for (List<Part> group : List.of(lines(context), allowances(context),
                    charges(context))) {
                for (Part part : group) {
                    if (part.category() != null && part.statesAReason()) {
                        codes.add(part.category());
                    }
                }
            }
            return Set.copyOf(codes);
        });
    }

    /**
     * Returns the keys the invoice lines, the document level allowances and the document
     * level charges and taxes of one VAT category state.
     *
     * @param context the invoice, at the document context
     * @param code    the VAT category code
     * @return the parts of that category, in document order
     */
    static List<Part> of(RuleContext context, String code) {
        List<Part> parts = new ArrayList<>();
        for (List<Part> group : List.of(lines(context), allowances(context), charges(context))) {
            for (Part part : group) {
                if (part.is(code)) {
                    parts.add(part);
                }
            }
        }
        return List.copyOf(parts);
    }

    /** The sums of the invoice, by the key the rules of this edition match on. */
    static final class Totals {

        private final Set<String> divided;
        private final Map<String, BigDecimal> byKey;
        private final Map<String, BigDecimal> byKeyAndRate;

        private Totals(Set<String> divided, Map<String, BigDecimal> byKey,
                       Map<String, BigDecimal> byKeyAndRate) {
            this.divided = divided;
            this.byKey = byKey;
            this.byKeyAndRate = byKeyAndRate;
        }

        private static Totals of(Set<String> divided, List<Part> lines, List<Part> charges,
                                 List<Part> allowances) {
            Map<String, BigDecimal> byKey = new HashMap<>();
            Map<String, BigDecimal> byKeyAndRate = new HashMap<>();
            add(divided, byKey, byKeyAndRate, lines, false);
            add(divided, byKey, byKeyAndRate, charges, false);
            add(divided, byKey, byKeyAndRate, allowances, true);
            return new Totals(divided, byKey, byKeyAndRate);
        }

        private static void add(Set<String> divided, Map<String, BigDecimal> byKey,
                                Map<String, BigDecimal> byKeyAndRate,
                                List<Part> parts, boolean subtract) {
            for (Part part : parts) {
                if (part.category() == null || part.amount() == null) {
                    continue;
                }
                boolean split = divided.contains(part.category());
                BigDecimal amount = subtract ? part.amount().negate() : part.amount();
                byKey.merge(part.key(split), amount, BigDecimal::add);
                if (part.rate() != null) {
                    byKeyAndRate.merge(part.keyWithRate(split), amount, BigDecimal::add);
                }
            }
        }

        /**
         * Returns what the parts of the invoice under one breakdown come to.
         *
         * @param breakdown the breakdown
         * @param byRate    whether the category levies at a rate, and the rate is part of
         *                  the key
         * @return the sum, exactly, which is zero over no part
         */
        BigDecimal under(Part breakdown, boolean byRate) {
            boolean split = divided.contains(breakdown.category());
            return byRate
                    ? byKeyAndRate.getOrDefault(breakdown.keyWithRate(split), BigDecimal.ZERO)
                    : byKey.getOrDefault(breakdown.key(split), BigDecimal.ZERO);
        }
    }

    /**
     * Returns the number of fraction digits the currency of the invoice admits, which is what
     * this edition rounds every amount to.
     *
     * @param context the invoice, at the document context
     * @return the minor unit, or an empty optional where the invoice names no currency or the
     *         snapshot gives it none, in which case a rule that rounds reports nothing
     */
    static Optional<Integer> minorUnit(RuleContext context) {
        return context.text("/BT-5").flatMap(currency ->
                context.codeList("iso-4217").minorUnit(currency));
    }

    private static List<Part> join(RuleContext context, String key, int depth, List<String> patterns) {
        return context.shared(key, () -> joined(context, depth, patterns));
    }

    private static List<Part> joined(RuleContext context, int depth, List<String> patterns) {
        Map<SemanticPath, String> categories = texts(context, patterns.get(0), depth);
        Map<SemanticPath, BigDecimal> rates = numbers(context, patterns.get(1), depth);
        Map<SemanticPath, BigDecimal> amounts = numbers(context, patterns.get(2), depth);
        Map<SemanticPath, String> exemptionCodes = texts(context, patterns.get(3), depth);
        Map<SemanticPath, String> exemptionTexts = texts(context, patterns.get(4), depth);
        Map<SemanticPath, String> goodsCodes = texts(context, patterns.get(5), depth);
        Set<SemanticPath> instances = new LinkedHashSet<>(categories.keySet());
        instances.addAll(amounts.keySet());
        List<SemanticPath> ordered = new ArrayList<>(instances);
        ordered.sort(null);
        List<Part> parts = new ArrayList<>(ordered.size());
        for (SemanticPath instance : ordered) {
            parts.add(new Part(instance, categories.get(instance), rates.get(instance),
                    amounts.get(instance), exemptionCodes.get(instance),
                    exemptionTexts.get(instance), goodsCodes.get(instance)));
        }
        return List.copyOf(parts);
    }

    private static Map<SemanticPath, String> texts(RuleContext context, String pattern, int depth) {
        Map<SemanticPath, String> found = new LinkedHashMap<>();
        for (Map.Entry<SemanticPath, String> read : context.texts(pattern)) {
            found.put(read.getKey().prefix(depth), read.getValue());
        }
        return found;
    }

    private static Map<SemanticPath, BigDecimal> numbers(RuleContext context, String pattern, int depth) {
        Map<SemanticPath, BigDecimal> found = new LinkedHashMap<>();
        for (Map.Entry<SemanticPath, BigDecimal> read : context.decimals(pattern)) {
            found.put(read.getKey().prefix(depth), read.getValue());
        }
        return found;
    }
}
