package de.bsnsoft.esj.rules.internal.en16931.v2026;

/**
 * {@code BR-AE-01}: an invoice that categorises anything as reverse charge carries a VAT breakdown of that
 * category for every exemption reason it used.
 *
 * <p>Written in Java because the statement compares two sets of business group instances,
 * which the rule language has no operator for; {@link BreakdownPerReason} carries the
 * reasoning.
 */
public final class BrAe01 extends BreakdownPerReason {

    /** Creates the rule. */
    public BrAe01() {
        super("BR-AE-01", "AE", "reverse charge", false,
                "EN 16931-1:2026, 6.4.3.4.6, Table 9, BR-AE-1");
    }
}
