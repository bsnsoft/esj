package de.bsnsoft.esj.syntax;

/**
 * Which of the three checks produced a finding.
 *
 * <p>The order of the constants is the order the checks run in and the first key a
 * report sorts its findings by, so a reader meets the parser's complaints before the
 * schema's and the schema's before the rules'.
 */
public enum Engine {

    /** The XML parser: well-formedness, the encoding, and what the parser refuses. */
    PARSER("parser"),

    /** The XML Schema modules of the syntax. */
    XSD("xsd"),

    /** A compiled Schematron rule set of the pack. */
    SCHEMATRON("schematron");

    private final String token;

    Engine(String token) {
        this.token = token;
    }

    /**
     * Returns the token a report writes this engine with.
     *
     * @return the token
     */
    public String token() {
        return token;
    }
}
