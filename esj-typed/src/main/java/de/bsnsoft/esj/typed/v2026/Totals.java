package de.bsnsoft.esj.typed.v2026;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.MinorUnits;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import de.bsnsoft.esj.typed.DerivationException;
import de.bsnsoft.esj.typed.DerivationReport;
import de.bsnsoft.esj.typed.runtime.TermPaths;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Derives the amounts an invoice of EN 16931-1:2026 adds up to: the invoice line net amounts,
 * the VAT breakdown and the document totals, from the prices, quantities, allowances, charges
 * and third party charges the invoice already carries.
 *
 * <p>It is the policy of that edition, as {@link de.bsnsoft.esj.typed.Totals} is the policy
 * of the default one, and it is a policy of the SDK and not part of the format: a caller asks
 * for it and gets a {@link DerivationReport} back.
 *
 * <pre>{@code
 * InvoiceEditor invoice = En16931.newInvoice();
 * // ... write the parties, the lines and their prices ...
 * invoice.derive(Totals.of(minorUnits));
 * SemanticDocument document = invoice.document();
 * }</pre>
 *
 * <p>How many fraction digits an amount of this edition carries is not a constant. The
 * registry of the edition bounds each amount by the minor unit of the currency it is written
 * in (clause 6.5.13, Table 28), and the minor unit of a currency is a fact of a dated
 * snapshot of the currency list, which the rule pack of the edition carries. The policy is
 * therefore built over the {@link MinorUnits} of that snapshot; it reads the bound of every
 * amount it writes from the registry, for the invoice currency (BT-5), or for the VAT
 * accounting currency (BT-6) where the amount is written in that one. A currency the snapshot
 * gives no minor unit is refused by its code, never rounded to a number the policy made up.
 *
 * <p>The arithmetic is the one the edition states:
 *
 * <ul>
 *   <li>Invoice line net amount (BT-131) = item net price (BT-146) ÷ item price base quantity
 *       (BT-149, one where it is not stated) × invoiced quantity (BT-129) + the line charge
 *       or tax amounts (BT-141) − the line allowance amounts (BT-136) (BR-CO-32).</li>
 *   <li>One VAT breakdown (BG-23) per combination of VAT category code, VAT rate, exemption
 *       reason and specification code, exemption reason text and goods/services code that
 *       the lines (BT-151, BT-152, BT-195, BT-194, BT-196), the document level allowances
 *       (BT-95, BT-96, BT-174, BT-173, BT-213) and the document level charges and taxes
 *       (BT-102, BT-103, BT-176, BT-175, BT-214) use (BR-CO-18). Its taxable amount (BT-116)
 *       is what the parts of that combination come to, lines and charges added and
 *       allowances taken off (the eighth rule of each VAT category); its tax amount (BT-117)
 *       is BT-116 × BT-119 ÷ 100 (the ninth). A combination that states no rate gets the tax
 *       amount zero.</li>
 *   <li>BT-106 = Σ BT-131, BT-107 = Σ BT-92, BT-108 = Σ BT-99, BT-109 = BT-106 − BT-107 +
 *       BT-108, BT-110 = Σ BT-117, BT-112 = BT-109 + BT-110 (BR-CO-10 to BR-CO-15), and the
 *       amount due BT-115 = BT-112 − BT-113 + Σ BT-179 + BT-114 (BR-CO-16): what the seller
 *       collects for somebody else (BG-34) is paid with the invoice and is not part of its
 *       totals. BT-107 and BT-108 are written where the invoice has a document
 *       level allowance or charge and removed where it has none.</li>
 *   <li>Where the invoice states the VAT accounting currency (BT-6) and its exchange rate
 *       (BT-167), BT-111 = BT-110 × BT-167 (BR-CO-48), in the accounting currency. Where it
 *       states only one of them, BT-111 is left as it is.</li>
 * </ul>
 *
 * <p>Rounding is half up, away from zero (clause 6.5.14), once per amount, at the three results
 * that are products or quotients — the line net amount, the category tax amount and the total
 * VAT in the accounting currency — to the fraction digits the registry gives that amount in
 * its currency. Every other amount is a sum of amounts that already carry at most that many
 * digits, and is written at that scale without being rounded. Item net price, invoiced
 * quantity, item price base quantity, VAT rate and exchange rate are read at the scale the
 * caller gave and are never rounded, normalised or cut; whether they keep within the bounds
 * the edition gives them is for the rule pack to say. Every intermediate result is an exact
 * {@link BigDecimal}.
 *
 * <p>An amount the policy reads and adds up — a line charge or allowance, a document level
 * allowance or charge, a third party charge, the paid amount, the rounding amount, a line net
 * amount it keeps — has to keep within the digits its currency allows, because a sum of it
 * would carry the excess into a total. A value that does not is refused by its path, not
 * rounded: it is the caller's value.
 *
 * <p>The derivation refuses rather than guesses. It throws {@link DerivationException},
 * naming the term and the group instance, where the invoice states no currency, where a line
 * states no VAT category, where a category levied at a rate (S, L, M) states none, where a
 * base quantity is zero or negative (BR-CO-31) or stands without a price, where the invoice
 * has no line, where a part is categorised under a national scheme or a margin scheme (N, D,
 * F, I, J) — clause 6.4.3.4.13 says that under those the taxable amount is not the sum of the
 * net amounts, so no sum of the invoice gives it — and where a VAT breakdown is stated in a
 * currency other than the invoice currency (BT-184), which this policy does not compute. A
 * line that already carries a net amount keeps it and is refused where the formula gives a
 * different one, unless {@link #withOverwriteLines(boolean)} says to replace it; that exact
 * comparison is this policy's own strictness, stricter than the tolerance of BR-CO-32.
 *
 * <p>Everything the run takes out is reported like everything it writes: a stated BT-107 or
 * BT-108 with nothing behind it, and a VAT breakdown no part of the invoice matches. A stated
 * breakdown is matched by the combination it states — by VAT category code and rate alone
 * where no part of that category states an exemption reason or a goods/services code, which
 * is how the rule pack of the edition reads "where applicable" — and the terms of it the parts
 * do not determine (BT-120, BT-121, BT-210, BT-184) are put back.
 *
 * <p>One pass. The cost is linear in the number of invoice lines and their allowances and
 * charges.
 *
 * <p>No validation artefact of this edition is published, so nothing here was compared with
 * one; the rule pack of the edition checks what this policy writes, and neither is
 * corroborated by an official artefact.
 */
public final class Totals {

    /** The VAT category codes under which VAT is levied at a rate. */
    private static final List<String> RATED_CATEGORIES = List.of("S", "L", "M");

    /**
     * The VAT category codes of the national schemes and the margin schemes, under which the
     * taxable amount is not the net amount of the parts (clause 6.4.3.4.13).
     */
    private static final List<String> NOT_FROM_PARTS = List.of("N", "D", "F", "I", "J");

    private static final SemanticPath LINES = SemanticPath.group("/BG-25");
    private static final SemanticPath DOCUMENT_ALLOWANCES = SemanticPath.group("/BG-20");
    private static final SemanticPath DOCUMENT_CHARGES = SemanticPath.group("/BG-21");
    private static final SemanticPath BREAKDOWNS = SemanticPath.group("/BG-23");
    private static final SemanticPath TOTALS = SemanticPath.group("/BG-22");
    private static final SemanticPath THIRD_PARTY_CHARGES =
            TermPaths.group(TOTALS, "BG-34");

    /** The business terms of one VAT breakdown (BG-23), in the order of the model. */
    private static final List<String> BREAKDOWN_TERMS = List.of(
            "BT-184", "BT-116", "BT-117", "BT-118", "BT-119", "BT-120", "BT-121", "BT-210");

    /** The terms of a stated breakdown the parts do not determine and a rebuild puts back. */
    private static final List<String> KEPT_TERMS = List.of("BT-120", "BT-121", "BT-210",
            "BT-184");

    private final MinorUnits minorUnits;
    private final boolean overwriteLines;

    private Totals(MinorUnits minorUnits, boolean overwriteLines) {
        this.minorUnits = minorUnits;
        this.overwriteLines = overwriteLines;
    }

    /**
     * Returns the policy that rounds to the minor units of one snapshot of the currency list,
     * with hand-written line net amounts kept.
     *
     * @param minorUnits the minor units the rule pack of the edition decides against
     * @return the policy
     * @throws NullPointerException if {@code minorUnits} is {@code null}
     */
    public static Totals of(MinorUnits minorUnits) {
        return new Totals(Objects.requireNonNull(minorUnits, "minorUnits"), false);
    }

    /**
     * Returns this policy with the recomputation of hand-written line net amounts switched on
     * or off.
     *
     * @param overwrite whether a line that already carries BT-131 is recomputed and its
     *                  amount replaced; with {@code false} it is kept, and a formula that
     *                  gives a different one is a refusal
     * @return the policy
     */
    public Totals withOverwriteLines(boolean overwrite) {
        return new Totals(minorUnits, overwrite);
    }

    /**
     * Returns the minor units this policy rounds to.
     *
     * @return the minor units
     */
    public MinorUnits minorUnits() {
        return minorUnits;
    }

    /**
     * Tells whether hand-written line net amounts are recomputed.
     *
     * @return whether a line that carries BT-131 is recomputed
     */
    public boolean overwriteLines() {
        return overwriteLines;
    }

    /**
     * Derives the amounts into a document builder.
     *
     * @param builder the builder holding the invoice; its prices, quantities, allowances and
     *                charges are read and its amounts are written
     * @return what was written, what was removed and where it was rounded
     * @throws DerivationException      if the invoice does not state what the derivation
     *                                  needs, or if a line net amount it carries contradicts
     *                                  the formula and the policy keeps line amounts
     * @throws IllegalArgumentException if the builder names another edition than
     *                                  {@link En16931#SEMANTIC_MODEL}
     * @throws NullPointerException     if {@code builder} is {@code null}
     */
    public DerivationReport apply(SemanticDocument.Builder builder) {
        Objects.requireNonNull(builder, "builder");
        if (!En16931.SEMANTIC_MODEL.equals(builder.semanticModel())) {
            throw new IllegalArgumentException("this derivation computes the amounts of "
                    + En16931.SEMANTIC_MODEL + " and the document names "
                    + builder.semanticModel());
        }
        return new Run(builder, this).derive();
    }

    @Override
    public String toString() {
        return "Totals[" + En16931.SEMANTIC_MODEL + ", overwriteLines=" + overwriteLines
                + ", " + minorUnits + "]";
    }

    /** The registry of the edition, read once, for the fraction digit bound of each amount. */
    private static final class Model {

        private static final Registry REGISTRY = Registry.forSemanticModel(En16931.SEMANTIC_MODEL)
                .orElseThrow(() -> new IllegalStateException("this build carries the typed view"
                        + " of " + En16931.SEMANTIC_MODEL + " and not its registry"));

        private Model() {
        }

        static Term term(String termId) {
            return REGISTRY.term(termId).orElseThrow(() -> new IllegalStateException(
                    "the registry of " + En16931.SEMANTIC_MODEL + " has no term " + termId));
        }
    }

    /** One run of the derivation over one builder. */
    private static final class Run {

        private final SemanticDocument.Builder builder;
        private final Totals policy;
        private final List<DerivationReport.Derived> written = new ArrayList<>();
        private final List<DerivationReport.Removed> removed = new ArrayList<>();
        private final Map<String, Category> categories = new LinkedHashMap<>();
        private final Set<String> divided = new LinkedHashSet<>();
        private final List<Stated> stated = new ArrayList<>();
        private final Map<String, Stated> statedByKey = new LinkedHashMap<>();
        private String currency;

        Run(SemanticDocument.Builder builder, Totals policy) {
            this.builder = builder;
            this.policy = policy;
        }

        DerivationReport derive() {
            currency = currency();
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
            BigDecimal sumOfAllowances = documentParts(DOCUMENT_ALLOWANCES, "BT-92",
                    Part.ALLOWANCE);
            BigDecimal sumOfCharges = documentParts(DOCUMENT_CHARGES, "BT-99", Part.CHARGE);
            BigDecimal sumOfVat = breakdowns();
            totals(sumOfLines, sumOfAllowances, sumOfCharges, sumOfVat);
            return new DerivationReport(written, removed);
        }

        /** Reads the invoice currency and makes sure the snapshot gives it a minor unit. */
        private String currency() {
            SemanticPath path = TermPaths.value(SemanticPath.root(), "BT-5");
            String code = builder.value(path).map(SemanticValue::asString).orElseThrow(() ->
                    new DerivationException("the invoice states no invoice currency code (BT-5),"
                            + " and the number of fraction digits of every amount follows it",
                            path, "BT-5"));
            knownCurrency(code, path, "BT-5");
            return code;
        }

        private void knownCurrency(String code, SemanticPath path, String termId) {
            if (policy.minorUnits.minorUnit(code).isEmpty()) {
                throw new DerivationException("the currency code " + code + " (" + termId
                        + ") has no minor unit in " + policy.minorUnits.source() + ", so the"
                        + " number of fraction digits of its amounts is not known and is not"
                        + " guessed", path, termId);
            }
        }

        /** Returns how many fraction digits the registry gives an amount in a currency. */
        private int digits(String termId, String code) {
            OptionalInt digits = policy.minorUnits.fractionDigits(Model.term(termId), code);
            if (digits.isEmpty()) {
                throw new IllegalStateException("the registry of " + En16931.SEMANTIC_MODEL
                        + " gives " + termId + " no fraction digit bound in " + code);
            }
            return digits.getAsInt();
        }

        /**
         * Reads one invoice line, writes its net amount where the policy computes it, files it
         * under its VAT combination, and returns the amount that goes into BT-106.
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
            if (base != null && base.signum() <= 0) {
                throw new DerivationException(
                        "the invoice line at " + line + " states an item price base quantity"
                                + " (BT-149) of " + base.toPlainString() + "; a base quantity"
                                + " is not negative (BR-CO-31) and a net amount cannot be"
                                + " divided by zero",
                        line, "BT-149");
            }
            SemanticPath netPath = TermPaths.value(line, "BT-131");
            BigDecimal statedNet = decimal(netPath).orElse(null);
            BigDecimal net;
            if (statedNet == null || policy.overwriteLines) {
                Computed computed = compute(line, price, base);
                net = computed.value();
                builder.set(netPath, SemanticValue.ofDecimal(net));
                written.add(new DerivationReport.Derived(netPath, "BT-131", net, computed.rounded(),
                        statedNet != null && statedNet.compareTo(net) != 0,
                        "item net price (BT-146) over item price base quantity (BT-149) times"
                                + " invoiced quantity (BT-129), plus the line charges and taxes"
                                + " (BT-141) and less the line allowances (BT-136)"));
            } else {
                net = inBounds(statedNet, netPath, "BT-131", "invoice line at " + line);
                if (price != null) {
                    BigDecimal computed = compute(line, price, base).value();
                    if (computed.compareTo(statedNet) != 0) {
                        throw new DerivationException(
                                "the invoice line at " + line + " states the invoice line net"
                                        + " amount (BT-131) " + statedNet.toPlainString() + ", and"
                                        + " its price, quantity, charges and allowances come to "
                                        + computed.toPlainString() + "; switch overwriteLines on"
                                        + " to replace the stated amount",
                                netPath, "BT-131");
                    }
                }
            }
            file(line, "BG-30/BT-151", "BG-30/BT-152", "BG-30/BT-195", "BG-30/BT-194",
                    "BG-31/BT-196", net, Part.LINE);
            return net;
        }

        /** The line net amount of BR-CO-32, with its one rounding step. */
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
            BigDecimal charges = amounts(line, "BG-28", "BT-141", "line charge or tax");
            BigDecimal allowances = amounts(line, "BG-27", "BT-136", "line allowance");
            BigDecimal numerator = price.multiply(quantity)
                    .add(charges.subtract(allowances).multiply(divisor));
            BigDecimal net = numerator.divide(divisor, digits("BT-131", currency),
                    RoundingMode.HALF_UP);
            return new Computed(net, !isExact(numerator, divisor, net));
        }

        /** Adds up the amounts of a repeatable group inside an invoice line. */
        private BigDecimal amounts(SemanticPath line, String groupId, String termId,
                                   String what) {
            SemanticPath group = TermPaths.group(line, groupId);
            BigDecimal sum = BigDecimal.ZERO;
            int count = builder.occurrences(group);
            for (int index = 0; index < count; index++) {
                SemanticPath instance = TermPaths.indexed(group, index);
                BigDecimal amount = decimal(instance, termId).orElseThrow(() ->
                        new DerivationException("the " + what + " at " + instance
                                + " states no amount (" + termId + ") to add up",
                                instance, termId));
                sum = sum.add(inBounds(amount, TermPaths.value(instance, termId), termId,
                        what + " at " + instance));
            }
            return sum;
        }

        /** Reads the document level allowances or charges and returns their sum. */
        private BigDecimal documentParts(SemanticPath group, String amountId, Part part) {
            BigDecimal sum = BigDecimal.ZERO;
            int count = builder.occurrences(group);
            for (int index = 0; index < count; index++) {
                SemanticPath instance = TermPaths.indexed(group, index);
                BigDecimal amount = decimal(instance, amountId)
                        .orElseThrow(() -> new DerivationException(
                                "the " + part.what() + " at " + instance + " states no amount ("
                                        + amountId + ")",
                                instance, amountId));
                inBounds(amount, TermPaths.value(instance, amountId), amountId,
                        part.what() + " at " + instance);
                if (part == Part.ALLOWANCE) {
                    file(instance, "BT-95", "BT-96", "BT-174", "BT-173", "BT-213", amount, part);
                } else {
                    file(instance, "BT-102", "BT-103", "BT-176", "BT-175", "BT-214", amount,
                            part);
                }
                sum = sum.add(amount);
            }
            return sum;
        }

        /**
         * Files an amount under the VAT combination its group instance states, and refuses
         * where the category is missing, asks for a rate that is not there, or is one whose
         * taxable amount no sum of the invoice gives.
         */
        private void file(SemanticPath instance, String categoryId, String rateId,
                          String exemptionCodeId, String exemptionTextId, String goodsCodeId,
                          BigDecimal amount, Part part) {
            String code = text(instance, categoryId).orElseThrow(() -> new DerivationException(
                    "the " + part.what() + " at " + instance + " states no VAT category code ("
                            + lastTerm(categoryId) + "), so its amount belongs to no VAT breakdown",
                    instance, lastTerm(categoryId)));
            if (NOT_FROM_PARTS.contains(code)) {
                throw new DerivationException(
                        "the " + part.what() + " at " + instance + " is categorised \"" + code
                                + "\", a national or margin scheme under which the taxable"
                                + " amount is not the net amount (clause 6.4.3.4.13), so this"
                                + " derivation cannot compute its VAT breakdown",
                        instance, lastTerm(categoryId));
            }
            BigDecimal rate = decimal(instance, rateId).orElse(null);
            if (rate == null && RATED_CATEGORIES.contains(code)) {
                throw new DerivationException(
                        "the " + part.what() + " at " + instance + " is categorised \"" + code
                                + "\", under which VAT is levied at a rate, and states no VAT"
                                + " rate (" + lastTerm(rateId) + ")",
                        instance, lastTerm(rateId));
            }
            Combination combination = new Combination(code, rate,
                    text(instance, exemptionCodeId).orElse(null),
                    text(instance, exemptionTextId).orElse(null),
                    text(instance, goodsCodeId).orElse(null));
            if (combination.statesAReason()) {
                divided.add(code);
            }
            categories.computeIfAbsent(combination.key(), ignored -> new Category(combination))
                    .add(amount, part);
        }

        /**
         * Rebuilds the VAT breakdown (BG-23), one group instance per combination, and returns
         * the sum of the category tax amounts.
         */
        private BigDecimal breakdowns() {
            rememberStated();
            builder.removeUnder(BREAKDOWNS);
            int scale = digits("BT-116", currency);
            int taxScale = digits("BT-117", currency);
            BigDecimal sumOfVat = BigDecimal.ZERO;
            int index = 0;
            for (Category category : categories.values()) {
                Combination combination = category.combination();
                Stated before = statedByKey.get(matchKey(combination));
                if (before != null) {
                    before.match();
                }
                SemanticPath instance = TermPaths.indexed(BREAKDOWNS, index++);
                BigDecimal taxable = category.taxableAmount()
                        .setScale(scale, RoundingMode.UNNECESSARY);
                writeBreakdown(instance, "BT-116", taxable, false, before,
                        "the invoice line net amounts (BT-131) of this VAT combination, less the"
                                + " document level allowances (BT-92) and plus the document level"
                                + " charges and taxes (BT-99) of the same combination");
                BigDecimal rate = combination.rate();
                BigDecimal tax = rate == null
                        ? BigDecimal.ZERO.setScale(taxScale)
                        : taxable.multiply(rate).divide(BigDecimal.valueOf(100), taxScale,
                                RoundingMode.HALF_UP);
                boolean rounded = rate != null
                        && !isExact(taxable.multiply(rate), BigDecimal.valueOf(100), tax);
                writeBreakdown(instance, "BT-117", tax, rounded, before, rate == null
                        ? "no rate is stated for this VAT combination, so the tax amount is zero"
                        : "the VAT category taxable amount (BT-116) times the VAT category rate"
                                + " (BT-119) over one hundred");
                builder.set(TermPaths.value(instance, "BT-118"),
                        SemanticValue.of(combination.code()));
                if (rate != null) {
                    builder.set(TermPaths.value(instance, "BT-119"), SemanticValue.ofDecimal(rate));
                }
                combination.write(builder, instance);
                if (before != null) {
                    before.restore(builder, instance);
                }
                sumOfVat = sumOfVat.add(tax);
            }
            for (Stated entry : stated) {
                entry.report(removed);
            }
            return sumOfVat;
        }

        /**
         * Remembers every VAT breakdown the invoice carries before the group is rebuilt, filed
         * under the combination it states, and refuses one in a currency other than the
         * invoice currency.
         */
        private void rememberStated() {
            int count = builder.occurrences(BREAKDOWNS);
            for (int index = 0; index < count; index++) {
                SemanticPath instance = TermPaths.indexed(BREAKDOWNS, index);
                Optional<String> breakdownCurrency = text(instance, "BT-184");
                if (breakdownCurrency.isPresent() && !breakdownCurrency.get().equals(currency)) {
                    throw new DerivationException("the VAT breakdown at " + instance + " is"
                            + " stated in " + breakdownCurrency.get() + " (BT-184) and the invoice"
                            + " currency (BT-5) is " + currency + "; this derivation writes the"
                            + " breakdown in the invoice currency only",
                            TermPaths.value(instance, "BT-184"), "BT-184");
                }
                Map<String, SemanticValue> values = new LinkedHashMap<>();
                for (String termId : BREAKDOWN_TERMS) {
                    builder.value(TermPaths.value(instance, termId))
                            .ifPresent(value -> values.put(termId, value));
                }
                Optional<String> code = text(instance, "BT-118");
                String why;
                String key = null;
                if (code.isEmpty()) {
                    why = "the breakdown states no VAT category code (BT-118), so no part of this"
                            + " invoice can be matched to it";
                } else {
                    key = matchKey(new Combination(code.get(),
                            decimal(instance, "BT-119").orElse(null),
                            text(instance, "BT-121").orElse(null),
                            text(instance, "BT-120").orElse(null),
                            text(instance, "BT-210").orElse(null)));
                    why = "no invoice line, document level allowance or document level charge"
                            + " or tax states the VAT combination of this breakdown";
                }
                Stated entry = new Stated(instance, values, why);
                stated.add(entry);
                if (key != null && statedByKey.putIfAbsent(key, entry) != null) {
                    entry.duplicate();
                }
            }
        }

        /**
         * Returns the key a stated breakdown and a combination of the parts are matched by:
         * the whole combination where the parts divide the category by exemption reason or
         * goods/services code, and the category and rate alone where they do not.
         */
        private String matchKey(Combination combination) {
            return divided.contains(combination.code())
                    ? combination.key()
                    : new Combination(combination.code(), combination.rate(), null, null, null)
                            .key();
        }

        /** Writes the document totals (BG-22). */
        private void totals(BigDecimal sumOfLines, BigDecimal sumOfAllowances,
                            BigDecimal sumOfCharges, BigDecimal sumOfVat) {
            write("BT-106", sumOfLines, "the sum of the invoice line net amounts (BT-131)");
            sum("BT-107", sumOfAllowances, DOCUMENT_ALLOWANCES, "BG-20",
                    "document level allowance",
                    "the sum of the document level allowance amounts (BT-92)");
            sum("BT-108", sumOfCharges, DOCUMENT_CHARGES, "BG-21",
                    "document level charge or tax",
                    "the sum of the document level charge or tax amounts (BT-99)");
            BigDecimal withoutVat = sumOfLines.subtract(sumOfAllowances).add(sumOfCharges);
            write("BT-109", withoutVat,
                    "the sum of the line net amounts (BT-106) less the allowances (BT-107) and plus"
                            + " the charges and taxes (BT-108) on document level");
            write("BT-110", sumOfVat, "the sum of the VAT category tax amounts (BT-117)");
            accountingCurrency(sumOfVat);
            BigDecimal withVat = withoutVat.add(sumOfVat);
            write("BT-112", withVat,
                    "the total without VAT (BT-109) plus the total VAT amount (BT-110)");
            BigDecimal paid = stated("BT-113", "paid amount");
            BigDecimal rounding = stated("BT-114", "rounding amount");
            BigDecimal thirdParty = thirdPartyCharges();
            write("BT-115", withVat.subtract(paid).add(thirdParty).add(rounding),
                    "the total with VAT (BT-112) less the paid amount (BT-113), plus what the"
                            + " seller collects for others (BT-179) and the rounding amount"
                            + " (BT-114)");
        }

        /** Reads an amount of the document totals the invoice states, zero where it has none. */
        private BigDecimal stated(String termId, String what) {
            SemanticPath path = TermPaths.value(TOTALS, termId);
            return decimal(path).map(value -> inBounds(value, path, termId, what))
                    .orElse(BigDecimal.ZERO);
        }

        /** Adds up what the seller collects for others (BG-34). */
        private BigDecimal thirdPartyCharges() {
            BigDecimal sum = BigDecimal.ZERO;
            int count = builder.occurrences(THIRD_PARTY_CHARGES);
            for (int index = 0; index < count; index++) {
                SemanticPath instance = TermPaths.indexed(THIRD_PARTY_CHARGES, index);
                BigDecimal amount = decimal(instance, "BT-179").orElseThrow(() ->
                        new DerivationException("the third party charge at "
                                + instance + " states no amount (BT-179)", instance, "BT-179"));
                sum = sum.add(inBounds(amount, TermPaths.value(instance, "BT-179"), "BT-179",
                        "third party charge at " + instance));
            }
            return sum;
        }

        /**
         * Writes a sum that is a term only where the group it is over has an instance, and
         * reports the value the invoice stated there where the group has none.
         */
        private void sum(String termId, BigDecimal value, SemanticPath group, String groupId,
                         String what, String how) {
            if (builder.occurrences(group) == 0) {
                SemanticPath path = TermPaths.value(TOTALS, termId);
                builder.value(path).ifPresent(before -> removed.add(new DerivationReport.Removed(
                        path, termId, before.asString(),
                        "the invoice carries no " + what + " (" + groupId + "), so the sum of"
                                + " them is not a term of it")));
                builder.remove(path);
                return;
            }
            write(termId, value, how);
        }

        /**
         * Writes BT-111 where the invoice states the VAT accounting currency and the exchange
         * rate into it.
         */
        private void accountingCurrency(BigDecimal sumOfVat) {
            SemanticPath codePath = TermPaths.value(SemanticPath.root(), "BT-6");
            Optional<String> code = builder.value(codePath).map(SemanticValue::asString);
            Optional<BigDecimal> rate = decimal(TermPaths.value(SemanticPath.root(), "BT-167"));
            if (code.isEmpty() || rate.isEmpty()) {
                return;
            }
            knownCurrency(code.get(), codePath, "BT-6");
            BigDecimal exact = sumOfVat.multiply(rate.get());
            BigDecimal converted = exact.setScale(digits("BT-111", code.get()),
                    RoundingMode.HALF_UP);
            SemanticPath path = TermPaths.value(TOTALS, "BT-111");
            BigDecimal before = decimal(path).orElse(null);
            put(path, "BT-111", converted, converted.compareTo(exact) != 0, before,
                    "the total VAT amount (BT-110) times the VAT accounting currency exchange"
                            + " rate (BT-167), in the VAT accounting currency (BT-6)");
        }

        /** Writes a document total, a sum written at the scale its currency gives it. */
        private void write(String termId, BigDecimal value, String how) {
            SemanticPath path = TermPaths.value(TOTALS, termId);
            BigDecimal before = decimal(path).orElse(null);
            put(path, termId, value.setScale(digits(termId, currency), RoundingMode.UNNECESSARY),
                    false, before, how);
        }

        /**
         * Writes an amount of the rebuilt VAT breakdown. What it may replace is the amount the
         * breakdown of the same combination stated before the rebuild.
         */
        private void writeBreakdown(SemanticPath instance, String termId, BigDecimal value,
                                    boolean rounded, Stated before, String how) {
            put(TermPaths.value(instance, termId), termId, value, rounded,
                    before == null ? null : before.amount(termId), how);
        }

        private void put(SemanticPath path, String termId, BigDecimal value, boolean rounded,
                            BigDecimal before, String how) {
            builder.set(path, SemanticValue.ofDecimal(value));
            written.add(new DerivationReport.Derived(path, termId, value, rounded,
                    before != null && before.compareTo(value) != 0, how));
        }

        /**
         * Returns an amount the run adds up, after making sure it carries no more fraction
         * digits than its currency allows it; a sum of it would carry the excess into a total.
         */
        private BigDecimal inBounds(BigDecimal amount, SemanticPath path, String termId,
                                    String what) {
            int allowed = digits(termId, currency);
            if (amount.stripTrailingZeros().scale() > allowed) {
                throw new DerivationException("the " + what + " states " + termId + " "
                        + amount.toPlainString() + ", which has more fraction digits than the "
                        + allowed + " an amount of " + termId + " in " + currency + " carries ("
                        + policy.minorUnits.source() + "); the derivation adds it up and does not"
                        + " round a value it was given", path, termId);
            }
            return amount;
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
        CHARGE("document level charge or tax");

        private final String what;

        Part(String what) {
            this.what = what;
        }

        String what() {
            return what;
        }
    }

    /**
     * What one VAT breakdown of this edition stands for: a category, a rate, an exemption
     * reason and specification code, an exemption reason text and a goods/services code, each
     * of the last four absent where nothing states it.
     */
    private record Combination(String code, BigDecimal rate, String exemptionCode,
                               String exemptionText, String goodsCode) {

        /** The character that keeps the parts of a key apart; no code or text carries it. */
        private static final char SEPARATOR = 31;

        String key() {
            StringBuilder key = new StringBuilder(code).append(SEPARATOR)
                    .append(rate == null ? "" : rate.stripTrailingZeros().toPlainString());
            for (String part : new String[] {exemptionCode, exemptionText, goodsCode}) {
                key.append(SEPARATOR).append(part == null ? "\u0000" : part);
            }
            return key.toString();
        }

        boolean statesAReason() {
            return exemptionCode != null || exemptionText != null || goodsCode != null;
        }

        /** Writes the exemption reason and the goods/services code the combination states. */
        void write(SemanticDocument.Builder builder, SemanticPath instance) {
            if (exemptionCode != null) {
                builder.set(TermPaths.value(instance, "BT-121"), SemanticValue.of(exemptionCode));
            }
            if (exemptionText != null) {
                builder.set(TermPaths.value(instance, "BT-120"), SemanticValue.of(exemptionText));
            }
            if (goodsCode != null) {
                builder.set(TermPaths.value(instance, "BT-210"), SemanticValue.of(goodsCode));
            }
        }
    }

    /** The amounts of one VAT combination, as they come in. */
    private static final class Category {

        private final Combination combination;
        private BigDecimal lines = BigDecimal.ZERO;
        private BigDecimal allowances = BigDecimal.ZERO;
        private BigDecimal charges = BigDecimal.ZERO;

        Category(Combination combination) {
            this.combination = combination;
        }

        void add(BigDecimal amount, Part part) {
            switch (part) {
                case LINE -> lines = lines.add(amount);
                case ALLOWANCE -> allowances = allowances.add(amount);
                case CHARGE -> charges = charges.add(amount);
            }
        }

        Combination combination() {
            return combination;
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

        void match() {
            matched = true;
        }

        void duplicate() {
            why = "an earlier VAT breakdown states the same VAT combination, and one combination"
                    + " has one breakdown";
        }

        BigDecimal amount(String termId) {
            SemanticValue value = values.get(termId);
            return value == null ? null : value.asDecimal();
        }

        /** Puts back what the parts of the invoice do not determine and the rebuild left out. */
        void restore(SemanticDocument.Builder builder, SemanticPath rebuilt) {
            for (String termId : KEPT_TERMS) {
                SemanticValue value = values.get(termId);
                SemanticPath path = TermPaths.value(rebuilt, termId);
                if (value != null && builder.value(path).isEmpty()) {
                    builder.set(path, value);
                }
            }
        }

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
