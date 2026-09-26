package de.bsnsoft.esj.rules.en16931.v2026;

/**
 * {@code BR-S-08}: the taxable amount of a VAT breakdown categorised standard or reduced rate is what the invoice
 * lines, document level charges and taxes and document level allowances under that breakdown
 * come to.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has no
 * operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrS08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrS08() {
        super("BR-S-08", "S", "standard or reduced rate", true,
                "EN 16931-1:2026, 6.4.3.3.2, Table 6, BR-S-8");
    }
}
