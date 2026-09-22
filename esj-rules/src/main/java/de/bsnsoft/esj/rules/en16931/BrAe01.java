package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-AE-01}: an invoice that categorises an invoice line, a document level allowance or a
 * document level charge as reverse charge carries exactly one VAT breakdown group with that VAT
 * category code.
 *
 * <p>Written in Java because the statement counts a filtered set, which the
 * rule language has no operator for; {@link ExactlyOneBreakdown} carries the reasoning.
 */
public final class BrAe01 extends ExactlyOneBreakdown {

    /** Creates the rule. */
    public BrAe01() {
        super("BR-AE-01", "AE", "reverse charge", "EN 16931-1, 6.4.3.4.4, Table 9, BR-AE-1");
    }
}
