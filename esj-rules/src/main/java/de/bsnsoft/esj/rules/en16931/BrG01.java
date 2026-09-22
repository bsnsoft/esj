package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-G-01}: an invoice that categorises an invoice line, a document level allowance or a
 * document level charge as export outside the EU carries exactly one VAT breakdown group with that VAT
 * category code.
 *
 * <p>Written in Java because the statement counts a filtered set, which the
 * rule language has no operator for; {@link ExactlyOneBreakdown} carries the reasoning.
 */
public final class BrG01 extends ExactlyOneBreakdown {

    /** Creates the rule. */
    public BrG01() {
        super("BR-G-01", "G", "export outside the EU", "EN 16931-1, 6.4.3.4.6, Table 11, BR-G-1");
    }
}
