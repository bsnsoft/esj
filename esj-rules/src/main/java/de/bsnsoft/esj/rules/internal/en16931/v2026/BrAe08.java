package de.bsnsoft.esj.rules.internal.en16931.v2026;

/**
 * {@code BR-AE-08}: the taxable amount of a VAT breakdown categorised reverse charge is what the invoice
 * lines, document level charges and taxes and document level allowances under that breakdown
 * come to.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has no
 * operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrAe08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrAe08() {
        super("BR-AE-08", "AE", "reverse charge", false,
                "EN 16931-1:2026, 6.4.3.4.6, Table 9, BR-AE-8");
    }
}
