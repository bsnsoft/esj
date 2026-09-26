package de.bsnsoft.esj.rules.en16931.v2026;

/**
 * {@code BR-IC-08}: the taxable amount of a VAT breakdown categorised intra-community supply is what the invoice
 * lines, document level charges and taxes and document level allowances under that breakdown
 * come to.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has no
 * operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrIc08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrIc08() {
        super("BR-IC-08", "K", "intra-community supply", false,
                "EN 16931-1:2026, 6.4.3.4.8, Table 10, BR-IC-8");
    }
}
