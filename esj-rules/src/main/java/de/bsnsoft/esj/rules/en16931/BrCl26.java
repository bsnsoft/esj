package de.bsnsoft.esj.rules.en16931;

import java.util.List;

/**
 * {@code BR-CL-26}: the identification scheme of the deliver to location identifier (BT-71)
 * is one published by the ISO/IEC 6523 maintenance agency.
 *
 * <p>Written in Java because a scheme is a supplementary component of a value and not a
 * business term; {@link SchemeInList} carries the reasoning.
 */
public final class BrCl26 extends SchemeInList {

    /** Creates the rule. */
    public BrCl26() {
        super("BR-CL-26", "iso-6523-icd", "EN 16931-1, 6.3, Table 2, BT-71",
                List.of(new Scheme("/BG-13/BT-71", "BT-71", "deliver to location identifier")));
    }
}
