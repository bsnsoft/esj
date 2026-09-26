package de.bsnsoft.esj.rules.en16931.v2026;

import de.bsnsoft.esj.rules.RuleContext;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * {@code BR-CO-50}: the tax amount of a VAT breakdown in the VAT accounting currency is the tax
 * amount of the breakdown in the invoice currency, multiplied by the exchange rate.
 *
 * <p>Written in Java because the two breakdowns are paired by what they state, which the rule
 * language has no operator for; {@link AccountingConversion} carries the reasoning.
 */
public final class BrCo50 extends AccountingConversion {

    /** Creates the rule. */
    public BrCo50() {
        super("BR-CO-50", "BT-117", "tax amount");
    }

    @Override
    Optional<BigDecimal> amount(RuleContext context, VatParts.Part breakdown) {
        return VatParts.taxAmount(context, breakdown);
    }
}
