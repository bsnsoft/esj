package de.bsnsoft.esj.rules.en16931;

import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The taxable amount of a VAT breakdown is what the parts of the invoice in that VAT
 * category come to: the invoice line net amounts, plus the document level charges, minus the
 * document level allowances.
 *
 * <p>Every VAT category of the standard states this rule and it is the one rule of the pack
 * that no closed operator set reaches. The sum is over a <em>filtered</em> set — the lines of
 * one category, and for the categories that carry a rate the lines of one category at one
 * rate — and the language has no filter under an aggregate. So it is written here, once, and
 * the nine rules that use it differ in three constants.
 *
 * <p>How closely the two have to agree is decided rule by rule, because the two official
 * artefacts of release 1.3.16 decide it rule by rule and do not agree with each other. For
 * eight of the nine rules one of the two admits a stated amount within one unit of the
 * invoice currency of the sum and the other asks for equality, and which of the two is the
 * lenient one changes with the category; only {@code BR-O-08} asks for equality in both.
 * This pack is written over business terms and cannot be two things at once, so it says
 * both: a difference of one unit or more, which both artefacts fault, is a fatal finding,
 * and a difference inside the tolerance only one of them grants is a warning that says what
 * is true of that difference in the release. Usually that is the artefact of the other
 * syntax, which faults it, and a document meant for the lenient syntax therefore keeps its
 * verdict while a document meant for the strict one is not a surprise. For {@code BR-AF-08}
 * and {@code BR-AG-08} the strict assertion cannot report at all, so nothing faults a
 * difference inside their zone and the warning names the standard instead.
 * {@code conformance/rules/ledger.md} carries the matrix, read out of the artefacts by a
 * test, and names the mutations that measure the zone, its two edges and what lies beyond.
 *
 * <p>A tolerance of one unit is the open interval the artefacts write: a difference of
 * exactly one unit is faulted by both of them and is fatal here.
 *
 * <p>The rule is a statement about the document and is evaluated once. It reads four business
 * terms of four groups and joins them, which costs one pass per term and not one per
 * breakdown. The filtered sum is a lookup and not a scan: the lines, the document level
 * charges and the document level allowances are bucketed by VAT category, and by category and
 * rate, in one pass for the whole run ({@link Vat#totals}), so an invoice of three hundred
 * thousand lines and thirty thousand breakdowns costs their sum and not their product.
 */
abstract class CategoryTaxableAmount implements JavaRule {

    /**
     * Which official artefact of release 1.3.16 admits a difference below one unit of the
     * invoice currency between the stated taxable amount and the sum, where they differ.
     */
    enum Tolerance {

        /** Neither: both artefacts ask for the same number. */
        NEITHER(""),

        /** The UBL artefact; the CII artefact asks for equality and reports it. */
        UBL(", and the official CII artefact of release 1.3.16 faults a difference this"
                + " small."),

        /** The CII artefact; the UBL artefact asks for equality and reports it. */
        CII(", and the official UBL artefact of release 1.3.16 faults a difference this"
                + " small."),

        /**
         * The UBL artefact, and nothing faults a difference inside the zone: the CII
         * assertion of release 1.3.16 asks for equality and cannot report at all, because
         * it reads the two figures from a node where neither is written
         * ({@code conformance/rules/ledger.md}, the cause {@code artefact-cannot-fire}).
         * The warning is then not about the other artefact but about the standard, which
         * asks the two to be equal.
         */
        UBL_CII_CANNOT_REPORT(", and the standard asks the two to be equal: the official"
                + " UBL artefact of release 1.3.16 admits a difference this small, and the"
                + " CII assertion of that release cannot report it.");

        private final String warning;

        Tolerance(String warning) {
            this.warning = warning;
        }

        /**
         * Returns what a warning about a difference inside the tolerance ends with, which
         * is what is true of that difference in release 1.3.16.
         *
         * @return the sentence, empty where both artefacts ask for equality and there is
         *         no zone to warn in
         */
        String warning() {
            return warning;
        }
    }

    private final String id;
    private final String code;
    private final String name;
    private final String source;
    private final boolean byRate;
    private final Tolerance tolerance;

    /**
     * Creates the rule.
     *
     * @param id     the identifier the official validation artefacts give it
     * @param code   the VAT category code of UNTDID 5305 the rule is about
     * @param name   what that code means, in English, for the message
     * @param byRate whether the sum is taken per VAT rate, which it is for the categories in
     *               which VAT is levied at a rate
     * @param tolerance which official artefact admits a difference below one unit of the
     *                  invoice currency, where the two do not ask the same
     * @param source the clause of the standard the rule states
     */
    CategoryTaxableAmount(String id,
                          String code,
                          String name,
                          boolean byRate,
                          Tolerance tolerance,
                          String source) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.byRate = byRate;
        this.tolerance = tolerance;
        this.source = source;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final RuleSeverity severity() {
        return RuleSeverity.FATAL;
    }

    @Override
    public final String context() {
        return "/";
    }

    @Override
    public final List<String> terms() {
        return List.of("BT-92", "BT-95", "BT-96", "BT-99", "BT-102", "BT-103",
                "BT-116", "BT-118", "BT-119", "BT-131", "BT-151", "BT-152");
    }

    @Override
    public final List<String> roots() {
        return Vat.ROOTS;
    }

    @Override
    public final String source() {
        return source;
    }

    @Override
    public final Optional<String> check(RuleContext context) {
        return decide(context, true);
    }

    @Override
    public final Optional<String> warn(RuleContext context) {
        return tolerance == Tolerance.NEITHER ? Optional.empty() : decide(context, false);
    }

    /**
     * Walks the VAT breakdowns of the category and returns the message of the first one
     * whose stated taxable amount is not what the parts of the invoice come to.
     *
     * @param context the invoice
     * @param fatal   whether to look for a difference both artefacts fault, which is one
     *                unit of the invoice currency or more, or for one inside the tolerance
     *                only one of the two grants
     * @return the message, or an empty optional
     */
    private Optional<String> decide(RuleContext context, boolean fatal) {
        Vat.Totals totals = Vat.totals(context);
        for (Vat.Item breakdown : Vat.breakdowns(context)) {
            if (!breakdown.is(code) || breakdown.amount() == null) {
                continue;
            }
            if (byRate && breakdown.rate() == null) {
                continue;
            }
            BigDecimal rate = breakdown.rate();
            BigDecimal expected = byRate ? totals.of(code, rate) : totals.of(code);
            BigDecimal apart = breakdown.amount().subtract(expected).abs();
            boolean beyond = tolerance == Tolerance.NEITHER
                    ? apart.signum() != 0
                    : apart.compareTo(BigDecimal.ONE) >= 0;
            boolean inside = !beyond && apart.signum() != 0;
            if (fatal ? !beyond : !inside) {
                continue;
            }
            return Optional.of("The VAT breakdown at " + breakdown.path() + " is categorised \""
                    + code + "\" (" + name + ")" + (byRate ? " at the rate " + rate + " per cent" : "")
                    + " and states the taxable amount (BT-116) " + breakdown.amount()
                    + "; the invoice lines, document level charges and document level allowances of"
                    + " that category come to " + expected
                    + (fatal
                            ? (tolerance == Tolerance.NEITHER
                                    ? "."
                                    : ", which is a unit of the invoice currency or more away.")
                            : tolerance.warning()));
        }
        return Optional.empty();
    }
}
