package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-O-08}: the taxable amount of a VAT breakdown categorised not subject to VAT is what the
 * invoice lines, document level charges and document level allowances of that category
 * come to.
 *
 * <p>Release 1.3.16 asks for equality in both artefacts, and this is the one rule of the nine
 * where they agree, so the comparison here is exact.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has
 * no operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrO08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrO08() {
        super("BR-O-08", "O", "not subject to VAT",
                false, Tolerance.NEITHER, "EN 16931-1, 6.4.3.4.7, Table 12, BR-O-8");
    }
}
