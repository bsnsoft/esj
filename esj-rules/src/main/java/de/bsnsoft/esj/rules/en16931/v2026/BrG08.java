package de.bsnsoft.esj.rules.en16931.v2026;

/**
 * {@code BR-G-08}: the taxable amount of a VAT breakdown categorised export outside the EU is what the invoice
 * lines, document level charges and taxes and document level allowances under that breakdown
 * come to.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has no
 * operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrG08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrG08() {
        super("BR-G-08", "G", "export outside the EU", false,
                "EN 16931-1:2026, 6.4.3.4.10, Table 11, BR-G-8");
    }
}
