package de.bsnsoft.esj.rules.en16931.v2026;

/**
 * {@code BR-Z-08}: the taxable amount of a VAT breakdown categorised zero rated is what the invoice
 * lines, document level charges and taxes and document level allowances under that breakdown
 * come to.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has no
 * operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrZ08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrZ08() {
        super("BR-Z-08", "Z", "zero rated", false,
                "EN 16931-1:2026, 6.4.3.4.2, Table 7, BR-Z-8");
    }
}
