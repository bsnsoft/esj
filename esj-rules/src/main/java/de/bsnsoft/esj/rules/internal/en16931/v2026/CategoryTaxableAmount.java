package de.bsnsoft.esj.rules.internal.en16931.v2026;

import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.validate.Severity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * The taxable amount of a VAT breakdown is what the parts of the invoice under that
 * breakdown come to: the invoice line net amounts, plus the document level charges and
 * taxes, minus the document level allowances.
 *
 * <p>Every VAT category of the standard states this rule, and in this edition a part belongs
 * to a breakdown when the two agree on the VAT category, on the rate where the category
 * levies at one, and — where the invoice divides the category by exemption reason
 * ({@link VatParts#divided}) — on the exemption reason and specification code, the exemption
 * reason text and the goods or services code. That last half is what the 2017 edition did not
 * ask for, and it is why the rule of that edition cannot be taken over: two breakdowns of one
 * category and one rate that differ in their exemption reason carry two sums here and one
 * there.
 *
 * <p>The comparison is exact. This edition rounds the computed amount to the number of
 * fraction digits the minor unit of the invoice currency (BT-5) gives it, clause 6.5.14, and
 * states no tolerance for this rule; where the invoice names a currency the code list
 * snapshot of the pack gives no minor unit, the rule reports nothing rather than rounding to
 * a number it guessed.
 *
 * <p>No official validation artefact exists for this edition, so nothing here is compared
 * with one. The rule is weighed by the hand-computed cases of {@code conformance/rules-2026}.
 *
 * <p>The rule is a statement about the document and is evaluated once. The parts are bucketed
 * by their whole key in one pass for the run ({@link VatParts#totals}), so an invoice with
 * many breakdowns and many lines costs the sum of the two counts and not their product.
 */
abstract class CategoryTaxableAmount implements JavaRule {

    private final String id;
    private final String code;
    private final String name;
    private final boolean byRate;
    private final String source;

    /**
     * Creates the rule.
     *
     * @param id     the identifier the standard gives it
     * @param code   the VAT category code of UNTDID 5305 the rule is about
     * @param name   what that code means, in English, for the message
     * @param byRate whether the parts are matched per VAT rate, which they are for the
     *               categories in which a tax is levied at a rate
     * @param source the clause of the standard the rule states
     */
    CategoryTaxableAmount(String id, String code, String name, boolean byRate, String source) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.byRate = byRate;
        this.source = source;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public final String context() {
        return "/";
    }

    @Override
    public final List<String> terms() {
        return VatParts.TERMS;
    }

    @Override
    public final List<String> roots() {
        return VatParts.ROOTS;
    }

    @Override
    public final String source() {
        return source;
    }

    @Override
    public final Optional<String> check(RuleContext context) {
        Optional<Integer> minorUnit = VatParts.minorUnit(context);
        if (minorUnit.isEmpty()) {
            return Optional.empty();
        }
        VatParts.Totals totals = VatParts.totals(context);
        for (VatParts.Part breakdown : VatParts.breakdowns(context)) {
            if (!breakdown.is(code) || breakdown.amount() == null) {
                continue;
            }
            if (byRate && breakdown.rate() == null) {
                continue;
            }
            BigDecimal expected = totals.under(breakdown, byRate)
                    .setScale(minorUnit.get(), RoundingMode.HALF_UP);
            if (breakdown.amount().compareTo(expected) == 0) {
                continue;
            }
            return Optional.of("The VAT breakdown at " + breakdown.path() + " is categorised \""
                    + code + "\" (" + name + ")"
                    + (byRate ? " at the rate " + breakdown.rate() + " per cent" : "")
                    + breakdown.reason()
                    + " and states the taxable amount (BT-116) " + breakdown.amount()
                    + "; the invoice lines, document level charges and taxes and document level"
                    + " allowances under that breakdown come to " + expected + ".");
        }
        return Optional.empty();
    }
}
