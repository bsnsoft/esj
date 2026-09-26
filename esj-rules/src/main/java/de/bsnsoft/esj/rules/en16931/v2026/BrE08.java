package de.bsnsoft.esj.rules.en16931.v2026;

/**
 * {@code BR-E-08}: the taxable amount of a VAT breakdown categorised exempt from VAT is what the invoice
 * lines, document level charges and taxes and document level allowances under that breakdown
 * come to.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has no
 * operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrE08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrE08() {
        super("BR-E-08", "E", "exempt from VAT", false,
                "EN 16931-1:2026, 6.4.3.4.4, Table 8, BR-E-8");
    }
}
