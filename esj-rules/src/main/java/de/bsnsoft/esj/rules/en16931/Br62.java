package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-62}: the seller electronic address (BT-34) carries the identification scheme it was issued
 * under.
 *
 * <p>Written in Java because a scheme is a supplementary component of a value and not a
 * business term; {@link SchemeIdentifier} carries the reasoning.
 */
public final class Br62 extends SchemeIdentifier {

    /** Creates the rule. */
    public Br62() {
        super("BR-62", "/BG-4/BT-34", "BT-34", "seller electronic address", "EN 16931-1, 6.4.1, Table 3, BR-62");
    }
}
