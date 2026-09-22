package de.bsnsoft.esj.rules.en16931;

import java.util.List;

/**
 * {@code BR-CL-10}: the identification scheme of a party identifier is one published by the
 * ISO/IEC 6523 maintenance agency.
 *
 * <p>Three business terms say so: the seller identifier (BT-29), which repeats, the buyer
 * identifier (BT-46) and the payee identifier (BT-60).
 *
 * <p>The UBL artefact of the release admits one further value, {@code SEPA}, for a party
 * identification of the seller or the payee. That is an effect of the binding rather than a
 * statement of the standard: the syntax writes the bank assigned creditor identifier (BT-90)
 * in the same element, and the rule has to let it through. BT-90 is a business term of its
 * own here, so this engine sees it as BT-90 and this rule never meets it.
 *
 * <p>Written in Java because a scheme is a supplementary component of a value and not a
 * business term; {@link SchemeInList} carries the reasoning.
 */
public final class BrCl10 extends SchemeInList {

    /** Creates the rule. */
    public BrCl10() {
        super("BR-CL-10", "iso-6523-icd", "EN 16931-1, 6.3, Table 2, BT-29, BT-46 and BT-60",
                List.of(new Scheme("/BG-4/BT-29/*", "BT-29", "seller identifier"),
                        new Scheme("/BG-7/BT-46", "BT-46", "buyer identifier"),
                        new Scheme("/BG-10/BT-60", "BT-60", "payee identifier")));
    }
}
