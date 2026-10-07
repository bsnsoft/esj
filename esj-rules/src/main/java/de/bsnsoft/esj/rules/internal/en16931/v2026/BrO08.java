package de.bsnsoft.esj.rules.internal.en16931.v2026;

/**
 * {@code BR-O-08}: the taxable amount of a VAT breakdown categorised not subject to VAT is what the invoice
 * lines, document level charges and taxes and document level allowances under that breakdown
 * come to.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has no
 * operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrO08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrO08() {
        super("BR-O-08", "O", "not subject to VAT", false,
                "EN 16931-1:2026, 6.4.3.4.12, Table 12, BR-O-8");
    }
}
