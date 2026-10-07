package de.bsnsoft.esj.rules.internal.en16931.v2026;

/**
 * {@code BR-G-01}: an invoice that categorises anything as export outside the EU carries a VAT breakdown of that
 * category for every exemption reason it used.
 *
 * <p>Written in Java because the statement compares two sets of business group instances,
 * which the rule language has no operator for; {@link BreakdownPerReason} carries the
 * reasoning.
 */
public final class BrG01 extends BreakdownPerReason {

    /** Creates the rule. */
    public BrG01() {
        super("BR-G-01", "G", "export outside the EU", false,
                "EN 16931-1:2026, 6.4.3.4.10, Table 11, BR-G-1");
    }
}
