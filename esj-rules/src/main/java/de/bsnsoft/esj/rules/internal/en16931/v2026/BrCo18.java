package de.bsnsoft.esj.rules.internal.en16931.v2026;

import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code BR-CO-18}: an invoice carries a VAT breakdown for every combination of VAT category,
 * rate, exemption reason and goods or services code its lines, document level allowances and
 * document level charges and taxes use.
 *
 * <p>The 2017 edition asked for one breakdown at all. This edition asks for one per combination,
 * with the exemption reason and the goods or services code counting where they are used. What
 * has to be decided is whether each part of the invoice finds a breakdown that agrees with it,
 * which compares two sets of group instances and is why the rule is written in Java.
 *
 * <p>A part agrees with a VAT breakdown in the invoice currency when the two state the same
 * category; the same rate, where the part states one; and — where the invoice divides the
 * category by exemption reason ({@link VatParts#divided}) — the same exemption reason and
 * specification code, exemption reason text and goods or services code. A part that states no
 * rate is matched on the rest. A breakdown in the VAT accounting currency is not one of the
 * breakdowns this rule counts ({@link VatParts#breakdowns}).
 *
 * <p>The combinations the breakdowns state are entered in a hash set once for the run
 * ({@link VatParts#combinations}), and each part is one look-up in it: an invoice with many
 * lines and many breakdowns costs the sum of the two counts and not their product.
 *
 * <p>No official validation artefact exists for this edition, so nothing here is compared with
 * one. The rule is weighed by the hand-computed cases of {@code conformance/rules-2026}.
 */
public final class BrCo18 implements JavaRule {

    /** Creates the rule. */
    public BrCo18() {
    }

    @Override
    public String id() {
        return "BR-CO-18";
    }

    @Override
    public RuleSeverity severity() {
        return RuleSeverity.FATAL;
    }

    @Override
    public String context() {
        return "/";
    }

    @Override
    public List<String> terms() {
        return VatParts.TERMS;
    }

    @Override
    public List<String> roots() {
        return VatParts.ROOTS;
    }

    @Override
    public String source() {
        return "EN 16931-1:2026, 6.4.2, Table 4, BR-CO-18";
    }

    @Override
    public Optional<String> check(RuleContext context) {
        Set<VatParts.Combination> stated = VatParts.combinations(context);
        Set<String> divided = VatParts.divided(context);
        for (List<VatParts.Part> group : List.of(VatParts.lines(context),
                VatParts.allowances(context), VatParts.charges(context))) {
            for (VatParts.Part part : group) {
                if (part.category() == null || stated.contains(VatParts.Combination.of(part,
                        true, divided.contains(part.category()), true))) {
                    continue;
                }
                return Optional.of("The invoice states " + part.path() + " under the VAT category \""
                        + part.category() + "\""
                        + (part.rate() == null ? "" : " at the rate "
                                + part.rate().toPlainString() + " per cent")
                        + part.reason() + ", and no VAT breakdown (BG-23) in the invoice currency"
                        + " states that combination; this edition asks for one breakdown per"
                        + " combination of category, rate, exemption reason and goods or services"
                        + " code.");
            }
        }
        return Optional.empty();
    }
}
