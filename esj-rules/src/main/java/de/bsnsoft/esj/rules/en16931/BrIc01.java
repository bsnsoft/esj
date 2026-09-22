package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-IC-01}: an invoice that categorises an invoice line, a document level allowance or a
 * document level charge as intra-community supply carries exactly one VAT breakdown group with that VAT
 * category code.
 *
 * <p>Written in Java because the statement counts a filtered set, which the
 * rule language has no operator for; {@link ExactlyOneBreakdown} carries the reasoning.
 */
public final class BrIc01 extends ExactlyOneBreakdown {

    /** Creates the rule. */
    public BrIc01() {
        super("BR-IC-01", "K", "intra-community supply", "EN 16931-1, 6.4.3.4.5, Table 10, BR-IC-1");
    }
}
