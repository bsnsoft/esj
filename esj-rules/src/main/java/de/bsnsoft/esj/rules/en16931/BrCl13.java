package de.bsnsoft.esj.rules.en16931;

import java.util.List;

/**
 * {@code BR-CL-13}: the identification scheme of an item classification identifier (BT-158)
 * is one of the UNTDID 7143 entries.
 *
 * <p>Written in Java because a scheme is a supplementary component of a value and not a
 * business term; {@link SchemeInList} carries the reasoning.
 */
public final class BrCl13 extends SchemeInList {

    /** Creates the rule. */
    public BrCl13() {
        super("BR-CL-13", "untdid-7143", "EN 16931-1, 6.3, Table 2, BT-158",
                List.of(new Scheme("/BG-25/*/BG-31/BT-158/*", "BT-158",
                        "item classification identifier")));
    }
}
