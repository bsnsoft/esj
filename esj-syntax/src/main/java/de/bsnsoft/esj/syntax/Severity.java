package de.bsnsoft.esj.syntax;

/**
 * How serious a finding is.
 *
 * <p>For a Schematron finding this is the flag the artefact itself set on the rule, not
 * a judgement of this module: the bodies that own the rules decide which of them make a
 * document invalid, and a rule they levelled as a warning stays a warning here even
 * where it reads like an error. For the parser and the schema it is fatal, because a
 * document that is not well-formed or not valid against its schema is not a document the
 * rules can be run on at all.
 */
public enum Severity {

    /** The document is invalid. Any one of these decides the verdict. */
    FATAL("fatal"),

    /** Worth reporting, and not a reason to refuse the document. */
    WARNING("warning"),

    /** Reported for information, which some artefacts level a rule as. */
    INFORMATION("information");

    private final String token;

    Severity(String token) {
        this.token = token;
    }

    /**
     * Returns the token a report writes this severity with, which is the flag an artefact
     * uses.
     *
     * @return the token
     */
    public String token() {
        return token;
    }

    /**
     * Returns the severity an artefact's flag names.
     *
     * @param flag the flag, as the artefact spells it; an unknown or missing flag is a
     *             fatal one, because a rule whose level cannot be read is not a rule to
     *             pass over quietly
     * @return the severity
     */
    public static Severity ofFlag(String flag) {
        for (Severity severity : values()) {
            if (severity.token.equalsIgnoreCase(flag)) {
                return severity;
            }
        }
        return FATAL;
    }
}
