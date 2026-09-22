package de.bsnsoft.esj.rules;

import java.util.Objects;

/**
 * What kind of thing a rule finding is about.
 *
 * <p>A reader who has to tell an arithmetic error of the standard from a restriction on
 * how a number may be spelled from a code that is not on its list needs that distinction
 * before the rule identifier, and a program filtering findings needs it as a token rather
 * than as a prefix it has to parse. The three EN 16931 categories are the semantic half of the
 * table in {@code docs/validation.md}; the syntax engine carries the others, and {@link #B2C}
 * belongs to an extension of this project and is documented in {@code docs/b2c.md}.
 *
 * <p>The category is derived from the family of the rule identifier and never declared in
 * a rule file, so that a pack cannot file a rule under a category its identifier
 * contradicts.
 */
public enum RuleCategory {

    /** A business rule of EN 16931: what an invoice must contain and what its numbers must come to. */
    EN_BR("EN-BR"),

    /** A decimal rule of EN 16931: how many fraction digits an amount may spell. */
    EN_DEC("EN-DEC"),

    /** A code list rule of EN 16931: whether a code is one of the list it must come from. */
    EN_CL("EN-CL"),

    /**
     * A consistency check of an extension of this project: whether the guarantee a derivation
     * policy of the SDK claims holds for the document it wrote. It is no rule of EN 16931, no
     * statement about conformance to the ESJ format, and no part of what {@code esj validate}
     * runs; {@code docs/b2c.md} has the checks.
     */
    B2C("B2C");

    private final String token;

    RuleCategory(String token) {
        this.token = token;
    }

    /**
     * Returns the token this category is written with in a report.
     *
     * @return the token, for example {@code EN-BR}
     */
    public String token() {
        return token;
    }

    /**
     * Returns the category of a rule identifier.
     *
     * <p>The two families that are not business rules in the narrow sense name themselves:
     * {@code BR-DEC-*} restricts the spelling of an amount and {@code BR-CL-*} the
     * membership of a code. Everything else — the integrity constraints {@code BR-*}, the
     * conditions {@code BR-CO-*} and the VAT category families {@code BR-S-*},
     * {@code BR-Z-*}, {@code BR-E-*}, {@code BR-AE-*}, {@code BR-IC-*}, {@code BR-G-*},
     * {@code BR-O-*}, {@code BR-IG-*} and {@code BR-IP-*} — is a business rule.
     *
     * <p>{@code B2C-*} is the one family that is not a rule of EN 16931: it belongs to the
     * consistency checks of the B2C extension of this project.
     *
     * @param ruleId the rule identifier
     * @return the category the identifier belongs to
     * @throws NullPointerException if {@code ruleId} is {@code null}
     */
    public static RuleCategory of(String ruleId) {
        Objects.requireNonNull(ruleId, "ruleId");
        if (ruleId.startsWith("B2C-")) {
            return B2C;
        }
        if (ruleId.startsWith("BR-DEC-")) {
            return EN_DEC;
        }
        if (ruleId.startsWith("BR-CL-")) {
            return EN_CL;
        }
        return EN_BR;
    }

    /**
     * Returns the token, so that a category prints as it is reported.
     *
     * @return the token
     */
    @Override
    public String toString() {
        return token;
    }
}
