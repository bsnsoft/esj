package de.bsnsoft.esj.validate;

/**
 * How much a finding weighs (specification, section 9.5).
 *
 * <p>It is the one severity of every check of this project: the structural validator, the
 * syntax engine ({@code SyntaxFinding}), the business rules ({@code RuleFinding},
 * {@code JavaRule}) and the container checks of a PDF ({@code ContainerFinding}). The words
 * an artefact or a rule file uses for a level — {@code fatal} and {@code information} in a
 * Schematron flag, {@code fatal} in a rule pack — are spellings of {@link #ERROR} and
 * {@link #INFO}; a report that quotes them maps them itself.
 */
public enum Severity {

    /**
     * The document fails the check; a finding of this severity decides the verdict. A
     * Schematron artefact and a rule pack call it {@code fatal}.
     */
    ERROR("error"),

    /** The document passes the check, but something is worth saying. */
    WARNING("warning"),

    /**
     * No judgement at all: the report that something could not be checked or decided, or
     * a fact a check states. It is not a defect of the document and must not be presented
     * as one. A Schematron artefact calls it {@code information}.
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
