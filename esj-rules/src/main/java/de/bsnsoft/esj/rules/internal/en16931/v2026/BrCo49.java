package de.bsnsoft.esj.rules.internal.en16931.v2026;

import de.bsnsoft.esj.rules.RuleContext;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * {@code BR-CO-49}: the taxable amount of a VAT breakdown in the VAT accounting currency is the
 * taxable amount of the breakdown in the invoice currency, multiplied by the exchange rate.
 *
 * <p>Written in Java because the two breakdowns are paired by what they state, which the rule
 * language has no operator for; {@link AccountingConversion} carries the reasoning.
 */
public final class BrCo49 extends AccountingConversion {

    /** Creates the rule. */
    public BrCo49() {
        super("BR-CO-49", "BT-116", "taxable amount");
    }

    @Override
    Optional<BigDecimal> amount(RuleContext context, VatParts.Part breakdown) {
        return Optional.ofNullable(breakdown.amount());
    }
}
