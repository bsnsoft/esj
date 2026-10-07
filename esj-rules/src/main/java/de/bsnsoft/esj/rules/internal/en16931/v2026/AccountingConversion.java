package de.bsnsoft.esj.rules.internal.en16931.v2026;

import de.bsnsoft.esj.rules.JavaRule;
import de.bsnsoft.esj.rules.RuleContext;
import de.bsnsoft.esj.validate.Severity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An amount of a VAT breakdown in the VAT accounting currency is the same amount of the
 * breakdown in the invoice currency, multiplied by the exchange rate.
 *
 * <p>This edition lets an invoice state its VAT breakdowns a second time in the VAT accounting
 * currency (BT-6): the breakdown names that currency in BT-184. The two statements of the rule
 * compare the taxable amount (BR-CO-49) and the tax amount (BR-CO-50) of such a breakdown with
 * those of the breakdown in the invoice currency that states the same combination — the same
 * category, rate, exemption reason and specification code, exemption reason text and goods or
 * services code. Finding that partner is a join of group instances, which the rule language has
 * no operator for; the breakdowns in the invoice currency are put in a hash map by that key once
 * for the run ({@link VatParts#partners}), so that each converted breakdown finds its partner by
 * one look-up and the join costs the number of breakdowns and not its square.
 *
 * <p>The product is rounded once, half up, to the minor unit of the accounting currency, clause
 * 6.5.14, and compared with the tolerance of that clause: a thousandth of the amount stated in
 * the accounting currency, never below 0,01 and never above 1. Table 4 writes the total VAT
 * amount in the accounting currency (BT-111) as the base of that tolerance and Annex A the amount
 * of the breakdown itself; clause 6.5.14 says the amount provided, and this rule follows it. The
 * rate is multiplied, as the rule states it.
 *
 * <p>A breakdown with no partner, an invoice without an exchange rate or accounting currency, and
 * an accounting currency the snapshot gives no minor unit are not decided: the rule answers
 * nothing. No official validation artefact exists for this edition, so nothing here is compared
 * with one. The rule is weighed by the hand-computed cases of {@code conformance/rules-2026}.
 */
abstract class AccountingConversion implements JavaRule {

    private static final BigDecimal THOUSANDTH = new BigDecimal("0.001");
    private static final BigDecimal FLOOR = new BigDecimal("0.01");

    private final String id;
    private final String term;
    private final String name;
    private final List<String> terms;

    /**
     * Creates the rule.
     *
     * @param id   the identifier the standard gives it
     * @param term the business term compared, BT-116 or BT-117
     * @param name what that term is, for the message
     */
    AccountingConversion(String id, String term, String name) {
        this.id = id;
        this.term = term;
        this.name = name;
        List<String> all = new ArrayList<>(VatParts.TERMS);
        all.addAll(List.of("BT-6", "BT-167"));
        this.terms = List.copyOf(all);
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
        return terms;
    }

    @Override
    public final List<String> roots() {
        return VatParts.ROOTS;
    }

    @Override
    public final String source() {
        return "EN 16931-1:2026, 6.4.2, Table 4, " + id;
    }

    /**
     * Returns the amount of a breakdown this rule compares.
     *
     * @param context   the invoice, at the document context
     * @param breakdown the breakdown
     * @return the amount, or an empty optional where the breakdown states none
     */
    abstract Optional<BigDecimal> amount(RuleContext context, VatParts.Part breakdown);

    @Override
    public final Optional<String> check(RuleContext context) {
        Optional<BigDecimal> rate = context.decimal("/BT-167");
        Optional<String> currency = context.text("/BT-6");
        if (rate.isEmpty() || currency.isEmpty()) {
            return Optional.empty();
        }
        Optional<Integer> minorUnit = context.codeList("iso-4217").minorUnit(currency.get());
        if (minorUnit.isEmpty()) {
            return Optional.empty();
        }
        Map<String, VatParts.Part> partners = VatParts.partners(context);
        for (VatParts.Part converted : VatParts.accounting(context)) {
            if (!currency.equals(VatParts.currencyOf(context, converted))) {
                continue;
            }
            Optional<VatParts.Part> partner =
                    Optional.ofNullable(partners.get(converted.keyWithRate(true)));
            if (partner.isEmpty()) {
                continue;
            }
            Optional<BigDecimal> stated = amount(context, converted);
            Optional<BigDecimal> original = amount(context, partner.get());
            if (stated.isEmpty() || original.isEmpty()) {
                continue;
            }
            BigDecimal expected = original.get().multiply(rate.get())
                    .setScale(minorUnit.get(), RoundingMode.HALF_UP);
            BigDecimal tolerance = THOUSANDTH.multiply(stated.get().abs()).max(FLOOR)
                    .min(BigDecimal.ONE);
            if (stated.get().subtract(expected).abs().compareTo(tolerance) <= 0) {
                continue;
            }
            return Optional.of("The VAT breakdown at " + converted.path() + " states the " + name
                    + " (" + term + ") " + stated.get().toPlainString() + " in the VAT accounting"
                    + " currency " + context.escape(currency.get()) + ", and the breakdown at "
                    + partner.get().path() + " states " + original.get().toPlainString()
                    + " in the invoice currency, which at the exchange rate (BT-167) "
                    + rate.get().toPlainString() + " comes to " + expected.toPlainString() + ".");
        }
        return Optional.empty();
    }
}
