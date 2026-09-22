package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-AF-08}: the taxable amount of a VAT breakdown categorised IGIC, the general indirect tax of the Canary Islands is what the
 * invoice lines, document level charges and document level allowances of that category at the same rate
 * come to.
 *
 * <p>Release 1.3.16 reads the comparison two ways: the UBL artefact admits a stated amount
 * within one unit of the invoice currency of the sum, the CII artefact asks for equality —
 * and the CII assertion cannot report at all, because it reads the rate and the taxable
 * amount from a node where neither is written. A difference of a unit or more is fatal
 * here; a difference below a unit is a warning, and it names the standard, which asks for
 * equality, rather than an artefact that would fault it, because neither does.
 *
 * <p>Written in Java because the sum is over a filtered set, which the rule language has
 * no operator for; {@link CategoryTaxableAmount} carries the reasoning and the arithmetic.
 */
public final class BrAf08 extends CategoryTaxableAmount {

    /** Creates the rule. */
    public BrAf08() {
        super("BR-AF-08", "L", "IGIC, the general indirect tax of the Canary Islands",
                true, Tolerance.UBL_CII_CANNOT_REPORT, "EN 16931-1, 6.4.3.4.8, Table 13, BR-IG-8");
    }
}
