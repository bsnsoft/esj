package de.bsnsoft.esj.rules.en16931;

import java.util.List;

/**
 * {@code BR-CL-07}: the identification scheme of an object identifier is one of the UNTDID
 * 1153 entries.
 *
 * <p>The standard says it of the invoiced object identifier (BT-18) and of the invoice line
 * object identifier (BT-128) in the same words, and the artefacts decide both under this one
 * identifier.
 *
 * <p>Written in Java because a scheme is a supplementary component of a value and not a
 * business term; {@link SchemeInList} carries the reasoning.
 */
public final class BrCl07 extends SchemeInList {

    /** Creates the rule. */
    public BrCl07() {
        super("BR-CL-07", "untdid-1153", "EN 16931-1, 6.3, Table 2, BT-18 and BT-128",
                List.of(new Scheme("/BT-18", "BT-18", "invoiced object identifier"),
                        new Scheme("/BG-25/*/BT-128", "BT-128", "invoice line object identifier")));
    }
}
