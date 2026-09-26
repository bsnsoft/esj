package de.bsnsoft.esj.rules.en16931.v2026;

/**
 * {@code BR-O-01}: an invoice that categorises anything as not subject to VAT carries a VAT breakdown of that
 * category for every exemption reason it used.
 *
 * <p>Written in Java because the statement compares two sets of business group instances,
 * which the rule language has no operator for; {@link BreakdownPerReason} carries the
 * reasoning.
 */
public final class BrO01 extends BreakdownPerReason {

    /** Creates the rule. */
    public BrO01() {
        super("BR-O-01", "O", "not subject to VAT", false,
                "EN 16931-1:2026, 6.4.3.4.12, Table 12, BR-O-1");
    }
}
