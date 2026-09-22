package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-O-01}: an invoice that categorises an invoice line, a document level allowance or a
 * document level charge as not subject to VAT carries exactly one VAT breakdown group with that VAT
 * category code.
 *
 * <p>Written in Java because the statement counts a filtered set, which the
 * rule language has no operator for; {@link ExactlyOneBreakdown} carries the reasoning.
 */
public final class BrO01 extends ExactlyOneBreakdown {

    /** Creates the rule. */
    public BrO01() {
        super("BR-O-01", "O", "not subject to VAT", "EN 16931-1, 6.4.3.4.7, Table 12, BR-O-1");
    }
}
