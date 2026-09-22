package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-64}: the item standard identifier (BT-157) carries the identification scheme it was issued
 * under.
 *
 * <p>Written in Java because a scheme is a supplementary component of a value and not a
 * business term; {@link SchemeIdentifier} carries the reasoning.
 */
public final class Br64 extends SchemeIdentifier {

    /** Creates the rule. */
    public Br64() {
        super("BR-64", "/BG-25/*/BG-31/BT-157", "BT-157", "item standard identifier", "EN 16931-1, 6.4.1, Table 3, BR-64");
    }
}
