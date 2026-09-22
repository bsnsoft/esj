package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-S-08}: the taxable amount of a VAT breakdown categorised standard rated is what the
 * invoice lines, document level charges and document level allowances of that category at the same rate
 * come to.
 *
 * <p>Release 1.3.16 reads the comparison two ways: the UBL artefact admits a stated amount
 * within one unit of the invoice currency of the sum, the CII artefact asks for equality.
 * A difference of a unit or more is fatal here; a difference below a unit is a warning
 * that names the CII artefact.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has
 * no operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrS08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrS08() {
        super("BR-S-08", "S", "standard rated",
                true, Tolerance.UBL, "EN 16931-1, 6.4.3.3.2, Table 6, BR-S-8");
    }
}
