package de.bsnsoft.esj.rules.en16931.v2026;

import de.bsnsoft.esj.rules.en16931.SchemeIdentifier;

/**
 * {@code BR-69}: the seller identifier (BT-29) carries the identification scheme it was issued under.
 *
 * <p>The scheme component of this term is optional in the 2017 edition and mandatory in this
 * one, which is the one place where the 2026 edition tightens the semantic model. Written in
 * Java because a scheme is a supplementary component of a value and not a business term;
 * {@link SchemeIdentifier} carries the reasoning.
 */
public final class Br69 extends SchemeIdentifier {

    /** Creates the rule. */
    public Br69() {
        super("BR-69", "/BG-4/BT-29/*", "BT-29", "seller identifier",
                "EN 16931-1:2026, 6.4.1, Table 3, BR-69");
    }
}
