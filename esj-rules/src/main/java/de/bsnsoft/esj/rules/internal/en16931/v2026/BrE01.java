package de.bsnsoft.esj.rules.internal.en16931.v2026;

/**
 * {@code BR-E-01}: an invoice that categorises anything as exempt from VAT carries a VAT breakdown of that
 * category for every exemption reason it used.
 *
 * <p>Written in Java because the statement compares two sets of business group instances,
 * which the rule language has no operator for; {@link BreakdownPerReason} carries the
 * reasoning.
 */
public final class BrE01 extends BreakdownPerReason {

    /** Creates the rule. */
    public BrE01() {
        super("BR-E-01", "E", "exempt from VAT", false,
                "EN 16931-1:2026, 6.4.3.4.4, Table 8, BR-E-1");
    }
}
