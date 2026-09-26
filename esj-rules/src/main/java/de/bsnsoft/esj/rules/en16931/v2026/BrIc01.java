package de.bsnsoft.esj.rules.en16931.v2026;

/**
 * {@code BR-IC-01}: an invoice that categorises anything as intra-community supply carries a VAT breakdown of that
 * category for every exemption reason it used and for every goods or services code.
 *
 * <p>Written in Java because the statement compares two sets of business group instances,
 * which the rule language has no operator for; {@link BreakdownPerReason} carries the
 * reasoning.
 */
public final class BrIc01 extends BreakdownPerReason {

    /** Creates the rule. */
    public BrIc01() {
        super("BR-IC-01", "K", "intra-community supply", true,
                "EN 16931-1:2026, 6.4.3.4.8, Table 10, BR-IC-1");
    }
}
