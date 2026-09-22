package de.bsnsoft.esj.validate;

/**
 * How much a finding weighs (specification, section 9.5).
 */
public enum Severity {

    /** The document fails the layer. */
    ERROR("error"),

    /** The document passes the layer, but something is worth saying. */
    WARNING("warning"),

    /**
     * No judgement at all. Its only use in this version is the report that something
     * could not be checked, which is not a defect of the document and must not be
     * presented as one.
     */
    INFO("info");

    private final String token;

    Severity(String token) {
        this.token = token;
    }

    /**
     * Returns the lowercase name the specification uses for this severity.
     *
     * @return the token, for example {@code error}
     */
    public String token() {
        return token;
    }
}
