package de.bsnsoft.esj.rules.internal.en16931.v2026;

/**
 * {@code BR-IG-08}: the taxable amount of a VAT breakdown categorised Canary Islands tax is what the invoice
 * lines, document level charges and taxes and document level allowances under that breakdown
 * come to.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has no
 * operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrIg08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrIg08() {
        super("BR-IG-08", "L", "Canary Islands tax", false,
                "EN 16931-1:2026, 6.4.3.4.15, Table 14, BR-IG-8");
    }
}
