package de.bsnsoft.esj.rules.internal.en16931.v2026;

import de.bsnsoft.esj.rules.internal.en16931.SchemeIdentifier;

/**
 * {@code BR-73}: the payee identifier (BT-60) carries the identification scheme it was issued under.
 *
 * <p>The scheme component of this term is optional in the 2017 edition and mandatory in this
 * one, which is the one place where the 2026 edition tightens the semantic model. Written in
 * Java because a scheme is a supplementary component of a value and not a business term;
 * {@link SchemeIdentifier} carries the reasoning.
 */
public final class Br73 extends SchemeIdentifier {

    /** Creates the rule. */
    public Br73() {
        super("BR-73", "/BG-10/BT-60", "BT-60", "payee identifier",
                "EN 16931-1:2026, 6.4.1, Table 3, BR-73");
    }
}
