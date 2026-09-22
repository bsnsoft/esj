package de.bsnsoft.esj.rules.en16931;

/**
 * {@code BR-65}: the item classification identifier (BT-158) carries the identification scheme it was issued
 * under.
 *
 * <p>Written in Java because a scheme is a supplementary component of a value and not a
 * business term; {@link SchemeIdentifier} carries the reasoning.
 */
public final class Br65 extends SchemeIdentifier {

    /** Creates the rule. */
    public Br65() {
        super("BR-65", "/BG-25/*/BG-31/BT-158/*", "BT-158", "item classification identifier", "EN 16931-1, 6.4.1, Table 3, BR-65");
    }
}
