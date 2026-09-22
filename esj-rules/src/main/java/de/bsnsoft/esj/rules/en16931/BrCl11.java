package de.bsnsoft.esj.rules.en16931;

import java.util.List;

/**
 * {@code BR-CL-11}: the identification scheme of a legal registration identifier is one
 * published by the ISO/IEC 6523 maintenance agency.
 *
 * <p>Three business terms say so: the seller (BT-30), the buyer (BT-47) and the payee
 * (BT-61) legal registration identifier.
 *
 * <p>Written in Java because a scheme is a supplementary component of a value and not a
 * business term; {@link SchemeInList} carries the reasoning.
 */
public final class BrCl11 extends SchemeInList {

    /** Creates the rule. */
    public BrCl11() {
        super("BR-CL-11", "iso-6523-icd", "EN 16931-1, 6.3, Table 2, BT-30, BT-47 and BT-61",
                List.of(new Scheme("/BG-4/BT-30", "BT-30", "seller legal registration identifier"),
                        new Scheme("/BG-7/BT-47", "BT-47", "buyer legal registration identifier"),
                        new Scheme("/BG-10/BT-61", "BT-61", "payee legal registration identifier")));
    }
}
