package de.bsnsoft.esj.typed;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.typed.runtime.TermPaths;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Derives the amounts an invoice adds up to: the invoice line net amounts, the VAT breakdown
 * and the document totals, from the prices, quantities, allowances and charges the invoice
 * already carries.
 *
 * <p>It is a policy of the SDK and not part of the format. Nothing in {@code esj-core}
 * computes, {@link InvoiceEditor#document()} returns what was written and nothing more, and a
 * document is never quietly completed on its way out. A caller that wants the totals derived
 * asks for it, in one line, and gets a {@link DerivationReport} back:
 *
 * <pre>{@code
 * InvoiceEditor invoice = En16931.newInvoice();
 * // ... write the parties, the lines and their prices ...
 * invoice.derive(Totals.STANDARD);
 * SemanticDocument document = invoice.document();
 * }</pre>
 *
 * <p>The arithmetic is the one EN 16931-1 states, and it is not configurable:
 *
 * <ul>
 *   <li>Invoice line net amount (BT-131) = invoiced quantity (BT-129) × item net price
 *       (BT-146) ÷ item price base quantity (BT-149, one where it is not stated) + the line
 *       charge amounts (BT-141) − the line allowance amounts (BT-136), rounded half up to two
 *       decimals (Annex A.1.1).</li>
 *   <li>One VAT breakdown (BG-23) per pair of VAT category code and VAT rate that the lines,
 *       the document level allowances and the document level charges use. Its taxable amount
 *       (BT-116) is the line net amounts of that pair, less the document level allowance
 *       amounts (BT-92) and plus the document level charge amounts (BT-99) of that pair; its
 *       tax amount (BT-117) is BT-116 × BT-119 ÷ 100, rounded half up to two decimals
 *       (BR-CO-17). A pair that states no rate — a category under which no VAT is levied —
 *       gets the tax amount zero.</li>
 *   <li>BT-106 = Σ BT-131, BT-107 = Σ BT-92, BT-108 = Σ BT-99, BT-109 = BT-106 − BT-107 +
 *       BT-108, BT-110 = Σ BT-117, BT-112 = BT-109 + BT-110, BT-115 = BT-112 − BT-113 +
 *       BT-114 (BR-CO-10 to BR-CO-16). BT-107 and BT-108 are written where the invoice has a
 *       document level allowance or charge and removed where it has none, so that an empty
 *       sum is an absent term rather than a zero.</li>
 * </ul>
 *
 * <p>Everything the run takes out is reported like everything it writes. A stated BT-107 or
 * BT-108 with no document level allowance or charge behind it, and a VAT breakdown for a
 * category and rate no part of the invoice uses, are removed and named in
 * {@link DerivationReport#removals()} with the value they carried. A breakdown is matched to
 * the one the invoice stated by VAT category code and rate, not by index, so a written amount
 * counts as a replacement only where the same category stated a different one.
 *
 * <p>Decimals follow the typing of the standard. Item net price, invoiced quantity, item
 * price base quantity and VAT rate are of the unlimited semantic data types (6.5,
 * AC 8 to AC 10): this class reads them at whatever scale the caller gave and never rounds,
 * normalises or cuts one. Every intermediate result is an exact {@link BigDecimal}. Rounding
 * happens once per amount, half up to two decimals, at the two results the standard rounds —
 * the line net amount and the VAT category tax amount — and the totals are sums of amounts
 * that are already rounded and are never rounded a second time (6.5.13).
 *
 * <p>The derivation refuses rather than guesses. Where a line states no VAT category, where a
 * category that is levied at a rate states none, where a base quantity stands without a
 * price, where an allowance states no category, or where the invoice has no line at all, it
 * throws {@link DerivationException} naming the term and the group instance. Where a line
 * already carries a net amount, the standard policy keeps it and refuses if the formula gives
 * a different one; {@code TotalsOptions.standard().withOverwriteLines(true)} replaces it
 * instead. Annex A.1.1 does not enforce the line calculation, so that refusal is this
 * policy's own strictness and not a rule of the standard: it is there because an invoice
 * whose lines and totals disagree is a mistake worth stopping at, and the option is the way
 * past it.
 *
 * <p>One pass. The cost is linear in the number of invoice lines and their allowances and
 * charges; nothing is quadratic and nothing holds the whole invoice a second time.
 *
 * <p>This policy is the arithmetic of one edition, {@link En16931#SEMANTIC_MODEL}: its
 * formulae are that edition's, its groups are that edition's paths and its rounding is that
 * edition's two fraction digits. A builder that names another edition is refused rather than
 * computed, because a later edition adds amounts these formulae know nothing of and the
 * result would be an invoice whose totals leave business content out. The derivation of
 * another edition is that edition's own policy.
 */
public final class Totals {

    /**
     * The scale of the semantic data type Amount in the 2017 edition of the standard: two
     * fraction digits (Table 26). It is a fact of that edition and of this policy with it.
     */
    private static final int AMOUNT_SCALE = 2;

    /** The VAT category codes of UNTDID 5305 under which VAT is levied at a rate. */
    private static final List<String> RATED_CATEGORIES = List.of("S", "L", "M");

    private static final SemanticPath LINES = SemanticPath.group("/BG-25");
    private static final SemanticPath DOCUMENT_ALLOWANCES = SemanticPath.group("/BG-20");
    private static final SemanticPath DOCUMENT_CHARGES = SemanticPath.group("/BG-21");
    private static final SemanticPath BREAKDOWNS = SemanticPath.group("/BG-23");

    /** The business terms of one VAT breakdown (BG-23), in the order of the model. */
    private static final List<String> BREAKDOWN_TERMS =
            List.of("BT-116", "BT-117", "BT-118", "BT-119", "BT-120", "BT-121");
    private static final SemanticPath TOTALS = SemanticPath.group("/BG-22");

    /**
     * The policy of EN 16931-1 with nothing varied: hand-written invoice line net amounts are
     * kept, and the invoice total VAT amount in accounting currency (BT-111) is left alone.
     */
    public static final Totals STANDARD = new Totals(TotalsOptions.standard());

    private final TotalsOptions options;

    private Totals(TotalsOptions options) {
        this.options = options;
    }

    /**
     * Returns the policy that runs with the given options.
     *
     * @param options what to vary
     * @return the policy
     * @throws NullPointerException if {@code options} is {@code null}
     */
    public static Totals of(TotalsOptions options) {
        return new Totals(Objects.requireNonNull(options, "options"));
    }

    /**
     * Returns the options this policy runs with.
     *
     * @return the options
     */
    public TotalsOptions options() {
        return options;
    }

    /**
     * Derives the amounts into a document builder.
     *
     * @param builder the builder holding the invoice; its prices, quantities, allowances and
     *                charges are read and its amounts are written
     * @return what was written and where it was rounded
     * @throws DerivationException      if the invoice does not state what the derivation
     *                                  needs, or if a line net amount it carries contradicts
     *                                  the formula and {@code TotalsOptions.overwriteLines}
     *                                  is off
     * @throws IllegalArgumentException if the builder names another edition than {@link
     *                                  En16931#SEMANTIC_MODEL}
     * @throws NullPointerException     if {@code builder} is {@code null}
     */
    public DerivationReport apply(SemanticDocument.Builder builder) {
        Objects.requireNonNull(builder, "builder");
        if (!En16931.SEMANTIC_MODEL.equals(builder.semanticModel())) {
            throw new IllegalArgumentException("this derivation computes the amounts of "
                    + En16931.SEMANTIC_MODEL + " and the document names "
                    + builder.semanticModel());
        }
        return new Run(builder, options).derive();
    }

    /**
     * Derives the amounts into a document builder, without a policy object in between. It is
     * the form for a caller that holds a builder rather than an editor.
     *
     * @param builder the builder holding the invoice
     * @param options what to vary
     * @return what was written and where it was rounded
     * @throws DerivationException      if the invoice does not state what the derivation
     *                                  needs, or if a line net amount it carries contradicts
     *                                  the formula and {@code TotalsOptions.overwriteLines}
     *                                  is off
     * @throws IllegalArgumentException if the builder names another edition than {@link
     *                                  En16931#SEMANTIC_MODEL}
     * @throws NullPointerException     if an argument is {@code null}
     */
    public static DerivationReport derive(SemanticDocument.Builder builder, TotalsOptions options) {
        return of(options).apply(builder);
    }

    @Override
    public String toString() {
        return "Totals[overwriteLines=" + options.overwriteLines()
                + ", vatAccountingCurrencyRate="
                + options.vatAccountingCurrencyRate().map(BigDecimal::toPlainString).orElse("none")
                + "]";
    }

    /** One run of the derivation over one builder. */
    private static final class Run {

        private final SemanticDocument.Builder builder;
        private final TotalsOptions options;
        private final List<DerivationReport.Derived> written = new ArrayList<>();
        private final Map<String, Category> categories = new LinkedHashMap<>();
        private final List<DerivationReport.Removed> removed = new ArrayList<>();
        private final List<Stated> stated = new ArrayList<>();
        private final Map<String, Stated> statedByCategory = new LinkedHashMap<>();

        Run(SemanticDocument.Builder builder, TotalsOptions options) {
            this.builder = builder;
            this.options = options;
        }

        DerivationReport derive() {
            int lines = builder.occurrences(LINES);
            if (lines == 0) {
                throw new DerivationException(
                        "the invoice carries no invoice line (BG-25), and an invoice with no line"
                                + " has no amounts to add up",
                        SemanticPath.root(), "BG-25");
            }
            BigDecimal sumOfLines = BigDecimal.ZERO;
            for (int index = 0; index < lines; index++) {
                sumOfLines = sumOfLines.add(line(TermPaths.indexed(LINES, index)));
            }
            BigDecimal sumOfAllowances = documentAllowances();
            BigDecimal sumOfCharges = documentCharges();
            BigDecimal sumOfVat = breakdowns();
            totals(sumOfLines, sumOfAllowances, sumOfCharges, sumOfVat);
            return new DerivationReport(written, removed);
        }

        /**
         * Reads one invoice line, writes its net amount where the policy computes it, files it
         * under its VAT category and rate, and returns the amount that goes into BT-106.
         */
        private BigDecimal line(SemanticPath line) {
            BigDecimal price = decimal(line, "BG-29/BT-146").orElse(null);
            BigDecimal base = decimal(line, "BG-29/BT-149").orElse(null);
            if (base != null && price == null) {
                throw new DerivationException(
                        "the invoice line at " + line + " states an item price base quantity"
                                + " (BT-149) of " + base.toPlainString() + " and no item net price"
                                + " (BT-146) for it to be the base of",
                        line, "BT-146");
            }
            if (base != null && base.signum() == 0) {
                throw new DerivationException(
                        "the invoice line at " + line + " states an item price base quantity"
                                + " (BT-149) of zero, which no net amount can be divided by",
                        line, "BT-149");
            }
            SemanticPath netPath = TermPaths.value(line, "BT-131");
            BigDecimal stated = decimal(netPath).orElse(null);
            BigDecimal net;
            if (stated == null || options.overwriteLines()) {
                Computed computed = compute(line, price, base);
                net = computed.value();
                builder.set(netPath, SemanticValue.ofDecimal(net));
                written.add(new DerivationReport.Derived(netPath, "BT-131", net, computed.rounded(),
                        stated != null && stated.compareTo(net) != 0,
                        "invoiced quantity (BT-129) times item net price (BT-146) over item price"
                                + " base quantity (BT-149), plus the line charges (BT-141) and less"
                                + " the line allowances (BT-136)"));
            } else {
                net = stated;
                if (price != null) {
                    BigDecimal computed = compute(line, price, base).value();
                    if (computed.compareTo(stated) != 0) {
                        throw new DerivationException(
                                "the invoice line at " + line + " states the invoice line net"
                                        + " amount (BT-131) " + stated.toPlainString() + ", and its"
                                        + " price, quantity, charges and allowances come to "
                                        + computed.toPlainString() + "; switch overwriteLines on to"
                                        + " replace the stated amount",
                                netPath, "BT-131");
                    }
                }
            }
            file(line, "BG-30/BT-151", "BG-30/BT-152", net, Part.LINE);
            return net;
        }

        /** The line net amount of Annex A.1.1, with its one rounding step. */
        private Computed compute(SemanticPath line, BigDecimal price, BigDecimal base) {
            if (price == null) {
                throw new DerivationException(
                        "the invoice line at " + line + " states no item net price (BT-146), so its"
                                + " invoice line net amount (BT-131) cannot be computed",
                        line, "BT-146");
            }
            BigDecimal quantity = decimal(line, "BT-129").orElseThrow(() -> new DerivationException(
                    "the invoice line at " + line + " states no invoiced quantity (BT-129), so its"
                            + " invoice line net amount (BT-131) cannot be computed",
                    line, "BT-129"));
            BigDecimal divisor = base == null ? BigDecimal.ONE : base;
            BigDecimal charges = amounts(line, "BG-28", "BT-141");
            BigDecimal allowances = amounts(line, "BG-27", "BT-136");
            BigDecimal numerator = price.multiply(quantity)
                    .add(charges.subtract(allowances).multiply(divisor));
            BigDecimal net = numerator.divide(divisor, AMOUNT_SCALE, RoundingMode.HALF_UP);
            return new Computed(net, !isExact(numerator, divisor, net));
        }

        /** Adds up the amounts of a repeatable group inside an invoice line. */
        private BigDecimal amounts(SemanticPath line, String groupId, String termId) {
            SemanticPath group = TermPaths.group(line, groupId);
            BigDecimal sum = BigDecimal.ZERO;
            int count = builder.occurrences(group);
            for (int index = 0; index < count; index++) {
                SemanticPath instance = TermPaths.indexed(group, index);
                sum = sum.add(decimal(instance, termId).orElseThrow(() -> new DerivationException(
                        "the group at " + instance + " states no amount (" + termId + ") to add up",
                        instance, termId)));
            }
            return sum;
        }

        /** Reads the document level allowances (BG-20) and returns their sum. */
        private BigDecimal documentAllowances() {
            return documentParts(DOCUMENT_ALLOWANCES, "BT-92", "BT-95", "BT-96", Part.ALLOWANCE);
        }

        /** Reads the document level charges (BG-21) and returns their sum. */
        private BigDecimal documentCharges() {
            return documentParts(DOCUMENT_CHARGES, "BT-99", "BT-102", "BT-103", Part.CHARGE);
        }

        private BigDecimal documentParts(SemanticPath group, String amountId, String categoryId,
                                         String rateId, Part part) {
            BigDecimal sum = BigDecimal.ZERO;
            int count = builder.occurrences(group);
            for (int index = 0; index < count; index++) {
                SemanticPath instance = TermPaths.indexed(group, index);
                BigDecimal amount = decimal(instance, amountId)
                        .orElseThrow(() -> new DerivationException(
                                "the " + part.what() + " at " + instance + " states no amount ("
                                        + amountId + ")",
                                instance, amountId));
                file(instance, categoryId, rateId, amount, part);
                sum = sum.add(amount);
            }
            return sum;
        }

        /**
         * Files an amount under the VAT category and rate its group instance states, and
         * refuses where the category is missing or asks for a rate that is not there.
         */
        private void file(SemanticPath instance, String categoryId, String rateId,
                          BigDecimal amount, Part part) {
            String code = text(instance, categoryId).orElseThrow(() -> new DerivationException(
                    "the " + part.what() + " at " + instance + " states no VAT category code ("
                            + lastTerm(categoryId) + "), so its amount belongs to no VAT breakdown",
                    instance, lastTerm(categoryId)));
            BigDecimal rate = decimal(instance, rateId).orElse(null);
            if (rate == null && RATED_CATEGORIES.contains(code)) {
                throw new DerivationException(
                        "the " + part.what() + " at " + instance + " is categorised \"" + code
                                + "\", under which VAT is levied at a rate, and states no VAT rate ("
                                + lastTerm(rateId) + ")",
                        instance, lastTerm(rateId));
            }
            categories.computeIfAbsent(key(code, rate), ignored -> new Category(code, rate))
                    .add(amount, part);
        }

        /**
         * Rebuilds the VAT breakdown (BG-23), one group instance per VAT category and rate, and
         * returns the sum of the category tax amounts.
         */
        private BigDecimal breakdowns() {
            rememberStated();
            builder.removeUnder(BREAKDOWNS);
            BigDecimal sumOfVat = BigDecimal.ZERO;
            int index = 0;
            for (Map.Entry<String, Category> entry : categories.entrySet()) {
                Category category = entry.getValue();
                Stated before = statedByCategory.get(entry.getKey());
                if (before != null) {
                    before.match();
                }
                SemanticPath instance = TermPaths.indexed(BREAKDOWNS, index++);
                BigDecimal taxable = category.taxableAmount();
                writeBreakdown(instance, "BT-116", taxable, false, before,
                        "the invoice line net amounts (BT-131) of this VAT category, less the"
                                + " document level allowances (BT-92) and plus the document level"
                                + " charges (BT-99) of the same category");
                BigDecimal rate = category.rate();
                BigDecimal tax = rate == null
                        ? BigDecimal.ZERO
                        : taxable.multiply(rate).divide(BigDecimal.valueOf(100),
                                AMOUNT_SCALE, RoundingMode.HALF_UP);
                boolean rounded = rate != null
                        && !isExact(taxable.multiply(rate), BigDecimal.valueOf(100), tax);
                writeBreakdown(instance, "BT-117", tax, rounded, before, rate == null
                        ? "no VAT is levied under this VAT category, so the tax amount is zero"
                        : "the VAT category taxable amount (BT-116) times the VAT category rate"
                                + " (BT-119) over one hundred");
                builder.set(TermPaths.value(instance, "BT-118"),
                        SemanticValue.of(category.code()));
                if (rate != null) {
                    builder.set(TermPaths.value(instance, "BT-119"), SemanticValue.ofDecimal(rate));
                }
                if (before != null) {
                    before.restoreExemption(builder, instance);
                }
                sumOfVat = sumOfVat.add(tax);
            }
            reportRemovedBreakdowns();
            return sumOfVat;
        }

        /**
         * Remembers every VAT breakdown (BG-23) the invoice carries before the group is rebuilt
         * from the lines, filed under the VAT category code and rate it states.
         *
         * <p>The snapshot is what lets the run say afterwards what became of each of them. A
         * breakdown whose category the run arrives at again is the one whose amounts a written
         * value replaces and whose exemption reason (BT-120, BT-121) — the one thing in a
         * breakdown that cannot be computed — is put back. A breakdown no category of this
         * invoice matches is removed whole, and every value it carried is reported. The
         * comparison is per category and not per path, because the indices of the rebuilt group
         * are not the indices of the invoice as it was written.
         */
        private void rememberStated() {
            int count = builder.occurrences(BREAKDOWNS);
            for (int index = 0; index < count; index++) {
                SemanticPath instance = TermPaths.indexed(BREAKDOWNS, index);
                Map<String, SemanticValue> values = new LinkedHashMap<>();
                for (String termId : BREAKDOWN_TERMS) {
                    builder.value(TermPaths.value(instance, termId))
                            .ifPresent(value -> values.put(termId, value));
                }
                Optional<String> code = text(instance, "BT-118");
                String why;
                String category = null;
                if (code.isEmpty()) {
                    why = "the breakdown states no VAT category code (BT-118), so no category of"
                            + " this invoice can be matched to it";
                } else {
                    category = key(code.get(), decimal(instance, "BT-119").orElse(null));
                    why = "no invoice line, document level allowance or document level charge is"
                            + " categorised \"" + code.get() + "\" at this VAT rate";
                }
                Stated entry = new Stated(instance, values, why);
                stated.add(entry);
                if (category != null && statedByCategory.putIfAbsent(category, entry) != null) {
                    entry.duplicate();
                }
            }
        }

        /** Reports every stated VAT breakdown the rebuild put nothing in the place of. */
        private void reportRemovedBreakdowns() {
            for (Stated entry : stated) {
                entry.report(removed);
            }
        }

        /** Writes the document totals (BG-22). */
        private void totals(BigDecimal sumOfLines, BigDecimal sumOfAllowances,
                            BigDecimal sumOfCharges, BigDecimal sumOfVat) {
            write(TOTALS, "BT-106", sumOfLines, false,
                    "the sum of the invoice line net amounts (BT-131)");
            sum(TOTALS, "BT-107", sumOfAllowances, DOCUMENT_ALLOWANCES, "BG-20",
                    "document level allowance",
                    "the sum of the document level allowance amounts (BT-92)");
            sum(TOTALS, "BT-108", sumOfCharges, DOCUMENT_CHARGES, "BG-21",
                    "document level charge",
                    "the sum of the document level charge amounts (BT-99)");
            BigDecimal withoutVat = sumOfLines.subtract(sumOfAllowances).add(sumOfCharges);
            write(TOTALS, "BT-109", withoutVat, false,
                    "the sum of the line net amounts (BT-106) less the allowances (BT-107) and plus"
                            + " the charges (BT-108) on document level");
            write(TOTALS, "BT-110", sumOfVat, false,
                    "the sum of the VAT category tax amounts (BT-117)");
            accountingCurrency(sumOfVat);
            BigDecimal withVat = withoutVat.add(sumOfVat);
            write(TOTALS, "BT-112", withVat, false,
                    "the total without VAT (BT-109) plus the total VAT amount (BT-110)");
            BigDecimal paid = decimal(TOTALS, "BT-113").orElse(BigDecimal.ZERO);
            BigDecimal rounding = decimal(TOTALS, "BT-114").orElse(BigDecimal.ZERO);
            write(TOTALS, "BT-115", withVat.subtract(paid).add(rounding), false,
                    "the total with VAT (BT-112) less the paid amount (BT-113) and plus the"
                            + " rounding amount (BT-114)");
        }

        /**
         * Writes a sum that is a term only where the group it is over has an instance, and
         * reports the value the invoice stated there where the group has none.
         */
        private void sum(SemanticPath parent, String termId, BigDecimal value,
                         SemanticPath group, String groupId, String what, String how) {
            if (builder.occurrences(group) == 0) {
                SemanticPath path = TermPaths.value(parent, termId);
                builder.value(path).ifPresent(before -> removed.add(new DerivationReport.Removed(
                        path, termId, before.asString(),
                        "the invoice carries no " + what + " (" + groupId + "), so the sum of"
                                + " them is not a term of it")));
                builder.remove(path);
                return;
            }
            write(parent, termId, value, false, how);
        }

        /** Writes BT-111 where the caller gave the exchange rate that carries BT-110 into it. */
        private void accountingCurrency(BigDecimal sumOfVat) {
            Optional<BigDecimal> rate = options.vatAccountingCurrencyRate();
            if (rate.isEmpty()) {
                return;
            }
            BigDecimal exact = sumOfVat.multiply(rate.get());
            BigDecimal converted = exact.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            write(TOTALS, "BT-111", converted, converted.compareTo(exact) != 0,
                    "the total VAT amount (BT-110) at the exchange rate into the VAT accounting"
                            + " currency (BT-6)");
        }

        private void write(SemanticPath parent, String termId, BigDecimal value, boolean rounded,
                           String how) {
            write(parent, termId, value, rounded, decimal(path(parent, termId)).orElse(null), how);
        }

        /**
         * Writes an amount of the rebuilt VAT breakdown. What it may replace is the amount the
         * breakdown of the same VAT category and rate stated before the rebuild, and not
         * whatever stood at the same index.
         */
        private void writeBreakdown(SemanticPath instance, String termId, BigDecimal value,
                                    boolean rounded, Stated before, String how) {
            write(instance, termId, value, rounded,
                    before == null ? null : before.amount(termId), how);
        }

        private void write(SemanticPath parent, String termId, BigDecimal value, boolean rounded,
                           BigDecimal before, String how) {
            SemanticPath path = TermPaths.value(parent, termId);
            builder.set(path, SemanticValue.ofDecimal(value));
            written.add(new DerivationReport.Derived(path, termId, value, rounded,
                    before != null && before.compareTo(value) != 0, how));
        }

        private Optional<BigDecimal> decimal(SemanticPath parent, String terms) {
            return decimal(path(parent, terms));
        }

        private Optional<BigDecimal> decimal(SemanticPath path) {
            return builder.value(path).map(SemanticValue::asDecimal);
        }

        private Optional<String> text(SemanticPath parent, String terms) {
            return builder.value(path(parent, terms)).map(SemanticValue::asString);
        }

        /** Resolves a term under a group instance, following group steps where there are any. */
        private static SemanticPath path(SemanticPath parent, String terms) {
            SemanticPath path = parent;
            String[] steps = terms.split("/");
            for (int i = 0; i < steps.length - 1; i++) {
                path = TermPaths.group(path, steps[i]);
            }
            return TermPaths.value(path, steps[steps.length - 1]);
        }

        private static String lastTerm(String terms) {
            return terms.substring(terms.lastIndexOf('/') + 1);
        }

        private static String key(String code, BigDecimal rate) {
            return code + "@" + (rate == null ? "" : rate.stripTrailingZeros().toPlainString());
        }

        /** Tells whether a quotient came out without anything having to be cut away. */
        private static boolean isExact(BigDecimal numerator, BigDecimal divisor,
                                       BigDecimal quotient) {
            return quotient.multiply(divisor).compareTo(numerator) == 0;
        }
    }

    /** An amount the formula arrived at, and whether the last step had to round to reach it. */
    private record Computed(BigDecimal value, boolean rounded) {
    }

    /** Which part of the invoice an amount that carries a VAT category comes from. */
    private enum Part {

        LINE("invoice line"),
        ALLOWANCE("document level allowance"),
        CHARGE("document level charge");

        private final String what;

        Part(String what) {
            this.what = what;
        }

        String what() {
            return what;
        }
    }

    /** The amounts of one VAT category at one rate, as they come in. */
    private static final class Category {

        private final String code;
        private final BigDecimal rate;
        private BigDecimal lines = BigDecimal.ZERO;
        private BigDecimal allowances = BigDecimal.ZERO;
        private BigDecimal charges = BigDecimal.ZERO;

        Category(String code, BigDecimal rate) {
            this.code = code;
            this.rate = rate;
        }

        void add(BigDecimal amount, Part part) {
            switch (part) {
                case LINE -> lines = lines.add(amount);
                case ALLOWANCE -> allowances = allowances.add(amount);
                case CHARGE -> charges = charges.add(amount);
            }
        }

        String code() {
            return code;
        }

        BigDecimal rate() {
            return rate;
        }

        BigDecimal taxableAmount() {
            return lines.subtract(allowances).add(charges);
        }
    }

    /**
     * One VAT breakdown (BG-23) as the invoice stated it, taken before the group is rebuilt:
     * where it stood, what it carried, and why nothing would take its place.
     */
    private static final class Stated {

        private final SemanticPath instance;
        private final Map<String, SemanticValue> values;
        private String why;
        private boolean matched;

        Stated(SemanticPath instance, Map<String, SemanticValue> values, String why) {
            this.instance = instance;
            this.values = values;
            this.why = why;
        }

        /** Records that a category of this run arrived at this breakdown again. */
        void match() {
            matched = true;
        }

        /** Records that an earlier breakdown already states this VAT category and rate. */
        void duplicate() {
            why = "an earlier VAT breakdown states the same VAT category code (BT-118) and VAT"
                    + " category rate (BT-119), and one category has one breakdown";
        }

        /** Returns the amount this breakdown stated for a term, or {@code null}. */
        BigDecimal amount(String termId) {
            SemanticValue value = values.get(termId);
            return value == null ? null : value.asDecimal();
        }

        /** Puts the exemption reason back, the one thing in a breakdown that is not computed. */
        void restoreExemption(SemanticDocument.Builder builder, SemanticPath rebuilt) {
            for (String termId : List.of("BT-120", "BT-121")) {
                SemanticValue value = values.get(termId);
                if (value != null) {
                    builder.set(TermPaths.value(rebuilt, termId), value);
                }
            }
        }

        /** Reports every value of this breakdown where the rebuild put nothing in its place. */
        void report(List<DerivationReport.Removed> removed) {
            if (matched) {
                return;
            }
            for (Map.Entry<String, SemanticValue> entry : values.entrySet()) {
                removed.add(new DerivationReport.Removed(
                        TermPaths.value(instance, entry.getKey()), entry.getKey(),
                        entry.getValue().asString(), why));
            }
        }
    }
}
