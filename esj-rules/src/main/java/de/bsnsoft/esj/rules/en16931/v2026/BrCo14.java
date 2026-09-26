package de.bsnsoft.esj.rules.en16931.v2026;

import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code BR-CO-14}: the tax amounts of the VAT breakdowns in the invoice currency add up to the
 * invoice total VAT amount (BT-110).
 *
 * <p>This edition lets a VAT breakdown state its amounts in the VAT accounting currency (BT-184
 * names that currency) and states this rule in the invoice currency. The sum is therefore taken
 * over the breakdowns that name no currency or name the invoice currency (BT-5), which is a sum
 * over a filtered set of group instances: the rule language has no operator for it, and the rule
 * is written in Java for that reason alone.
 *
 * <p>The sum is rounded once, half up, to the minor unit of the invoice currency, clause 6.5.14,
 * and compared exactly, as the 2017 edition compared it at two fraction digits. Where the invoice
 * names a currency the snapshot gives no minor unit, the rule answers nothing rather than
 * rounding to a number it guessed.
 *
 * <p>No official validation artefact exists for this edition, so nothing here is compared with
 * one. The rule is weighed by the hand-computed cases of {@code conformance/rules-2026}.
 */
public final class BrCo14 implements JavaRule {

    private static final List<String> TERMS = declared();

    private static List<String> declared() {
        List<String> terms = new ArrayList<>(VatParts.TERMS);
        terms.add("BT-110");
        return List.copyOf(terms);
    }

    /** Creates the rule. */
    public BrCo14() {
    }

    @Override
    public String id() {
        return "BR-CO-14";
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
        return TERMS;
    }

    @Override
    public List<String> roots() {
        return VatParts.ROOTS;
    }

    @Override
    public String source() {
        return "EN 16931-1:2026, 6.4.2, Table 4, BR-CO-14";
    }

    @Override
    public Optional<String> check(RuleContext context) {
        Optional<BigDecimal> stated = context.decimal("/BG-22/BT-110");
        if (stated.isEmpty()) {
            return Optional.empty();
        }
        Optional<Integer> minorUnit = VatParts.minorUnit(context);
        if (minorUnit.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (VatParts.Part breakdown : VatParts.breakdowns(context)) {
            sum = sum.add(VatParts.taxAmount(context, breakdown).orElse(BigDecimal.ZERO));
        }
        BigDecimal expected = sum.setScale(minorUnit.get(), RoundingMode.HALF_UP);
        if (stated.get().compareTo(expected) == 0) {
            return Optional.empty();
        }
        return Optional.of("The VAT category tax amounts (BT-117) of the VAT breakdowns in the"
                + " invoice currency add up to " + expected.toPlainString() + ", and the invoice"
                + " total VAT amount (BT-110) carries " + stated.get().toPlainString() + ".");
    }
}
