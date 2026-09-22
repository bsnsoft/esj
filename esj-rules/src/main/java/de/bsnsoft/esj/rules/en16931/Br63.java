package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-63}: the buyer electronic address (BT-49) carries the identification scheme it was issued
 * under.
 *
 * <p>Written in Java because a scheme is a supplementary component of a value and not a
 * business term; {@link SchemeIdentifier} carries the reasoning.
 */
public final class Br63 extends SchemeIdentifier {

    /** Creates the rule. */
    public Br63() {
        super("BR-63", "/BG-7/BT-49", "BT-49", "buyer electronic address", "EN 16931-1, 6.4.1, Table 3, BR-63");
    }
}
