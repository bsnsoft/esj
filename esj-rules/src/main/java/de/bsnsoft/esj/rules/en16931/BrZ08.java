package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-Z-08}: the taxable amount of a VAT breakdown categorised zero rated is what the
 * invoice lines, document level charges and document level allowances of that category
 * come to.
 *
 * <p>Release 1.3.16 reads the comparison two ways: the CII artefact admits a stated amount
 * within one unit of the invoice currency of the sum, the UBL artefact asks for equality.
 * A difference of a unit or more is fatal here; a difference below a unit is a warning
 * that names the UBL artefact.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has
 * no operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrZ08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrZ08() {
        super("BR-Z-08", "Z", "zero rated",
                false, Tolerance.CII, "EN 16931-1, 6.4.3.4.2, Table 7, BR-Z-8");
    }
}
