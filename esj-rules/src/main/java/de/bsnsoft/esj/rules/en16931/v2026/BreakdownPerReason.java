package de.bsnsoft.esj.rules.en16931.v2026;

import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An invoice that categorises a line, a document level allowance or a document level charge
 * or tax under one of the categories in which no tax is levied carries a VAT breakdown of
 * that category for every exemption reason it used.
 *
 * <p>The 2017 edition asked for exactly one breakdown of the category; this edition asks for
 * one per exemption reason — the exemption reason and specification code and the exemption
 * reason text — and, for the intra-community category, one per goods or services code as
 * well. What has to be decided is therefore not how many breakdowns of a category there are
 * but whether each reason used on the invoice has a breakdown of its own, which no operator
 * of the rule language reaches: the comparison is between two sets of group instances.
 *
 * <p>The edition makes that comparison where it is applicable, and what makes it applicable
 * is read here as the invoice stating one of the three terms on a line, a document level
 * allowance or a document level charge or tax of the category ({@link VatParts#divided}). An
 * invoice that states none of them is not divided and is decided as the 2017 edition decides
 * it.
 *
 * <p>No official validation artefact exists for this edition, so nothing here is compared
 * with one. The rule is weighed by the hand-computed cases of {@code conformance/rules-2026}.
 *
 * <p>The rule is a statement about the document and is evaluated once. It reads the four
 * groups that carry a VAT category in one pass each ({@link VatParts}), enters what the
 * breakdowns of its category state in a hash set, and looks each part of that category up in
 * it, so that many parts and many breakdowns cost the sum of the two counts and not their
 * product.
 */
abstract class BreakdownPerReason implements JavaRule {

    private final String id;
    private final String code;
    private final String name;
    private final boolean withGoodsCode;
    private final String source;

    /**
     * Creates the rule.
     *
     * @param id            the identifier the standard gives it
     * @param code          the VAT category code of UNTDID 5305 the rule is about
     * @param name          what that code means, in English, for the message
     * @param withGoodsCode whether the goods or services code is part of what a breakdown is
     *                      asked per, which this edition states for the intra-community
     *                      category alone
     * @param source        the clause of the standard the rule states
     */
    BreakdownPerReason(String id, String code, String name, boolean withGoodsCode, String source) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.withGoodsCode = withGoodsCode;
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
        boolean divided = VatParts.divided(context).contains(code);
        Set<VatParts.Combination> stated = new HashSet<>();
        for (VatParts.Part breakdown : VatParts.breakdowns(context)) {
            if (breakdown.is(code)) {
                stated.add(VatParts.Combination.of(breakdown, false, divided, withGoodsCode));
            }
        }
        for (VatParts.Part part : VatParts.of(context, code)) {
            if (!stated.contains(VatParts.Combination.of(part, false, divided, withGoodsCode))) {
                return Optional.of("The invoice states " + part.path() + " under the VAT category \""
                        + code + "\" (" + name + ")" + part.reason()
                        + ", and no VAT breakdown (BG-23) of that category states the same"
                        + " exemption reason; this edition asks for one breakdown per reason.");
            }
        }
        return Optional.empty();
    }
}
