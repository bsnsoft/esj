package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-E-01}: an invoice that categorises an invoice line, a document level allowance or a
 * document level charge as exempt from VAT carries exactly one VAT breakdown group with that VAT
 * category code.
 *
 * <p>Written in Java because the statement counts a filtered set, which the
 * rule language has no operator for; {@link ExactlyOneBreakdown} carries the reasoning.
 */
public final class BrE01 extends ExactlyOneBreakdown {

    /** Creates the rule. */
    public BrE01() {
        super("BR-E-01", "E", "exempt from VAT", "EN 16931-1, 6.4.3.4.3, Table 8, BR-E-1");
    }
}
