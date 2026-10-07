package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.validate.Severity;

/**
 * The words a Schematron artefact and a pack manifest write a level with: {@code fatal},
 * {@code warning} and {@code information}, the spellings of {@link Severity#ERROR},
 * {@link Severity#WARNING} and {@link Severity#INFO}.
 */
final class SchematronFlag {

    private SchematronFlag() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the severity an artefact's flag names.
     *
     * @param flag the flag, as the artefact spells it; an unknown or missing flag is a
     *             fatal one, because a rule whose level cannot be read is not a rule to
     *             pass over quietly
     * @return the severity
     */
    static Severity of(String flag) {
        for (Severity severity : Severity.values()) {
            if (word(severity).equalsIgnoreCase(flag)) {
                return severity;
            }
        }
        return Severity.ERROR;
    }

    /**
     * Returns the word an artefact writes a severity with.
     *
     * @param severity the severity
     * @return {@code fatal}, {@code warning} or {@code information}
     */
    static String word(Severity severity) {
        return switch (severity) {
            case ERROR -> "fatal";
            case WARNING -> "warning";
            case INFO -> "information";
        };
    }
}
