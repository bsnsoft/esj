package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-Z-01}: an invoice that categorises an invoice line, a document level allowance or a
 * document level charge as zero rated carries exactly one VAT breakdown group with that VAT
 * category code.
 *
 * <p>Written in Java because the statement counts a filtered set, which the
 * rule language has no operator for; {@link ExactlyOneBreakdown} carries the reasoning.
 */
public final class BrZ01 extends ExactlyOneBreakdown {

    /** Creates the rule. */
    public BrZ01() {
        super("BR-Z-01", "Z", "zero rated", "EN 16931-1, 6.4.3.4.2, Table 7, BR-Z-1");
    }
}
