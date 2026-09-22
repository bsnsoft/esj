package de.bsnsoft.esj.rules.en16931;

import java.util.List;

/**
 * {@code BR-CL-21}: the identification scheme of an item standard identifier (BT-157) is one
 * published by the ISO/IEC 6523 maintenance agency.
 *
 * <p>Written in Java because a scheme is a supplementary component of a value and not a
 * business term; {@link SchemeInList} carries the reasoning.
 */
public final class BrCl21 extends SchemeInList {

    /** Creates the rule. */
    public BrCl21() {
        super("BR-CL-21", "iso-6523-icd", "EN 16931-1, 6.3, Table 2, BT-157",
                List.of(new Scheme("/BG-25/*/BG-31/BT-157", "BT-157", "item standard identifier")));
    }
}
