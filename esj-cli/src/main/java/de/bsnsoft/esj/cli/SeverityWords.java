package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.validate.Severity;

/**
 * The words the reports of this tool write a severity with, one vocabulary per source.
 *
 * <p>The libraries have one {@link Severity}. The reports quote each source in its own
 * words, as they did before the libraries shared one: an official artefact flags
 * {@code fatal}, {@code warning} and {@code information}, a rule pack {@code fatal},
 * {@code warning} and {@code info}, and the structural validator and the container checks
 * write the words of the specification, {@link Severity#token()}.
 */
final class SeverityWords {

    private SeverityWords() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the word an official artefact writes a severity with.
     *
     * @param severity the severity
     * @return {@code fatal}, {@code warning} or {@code information}
     */
    static String artefact(Severity severity) {
        return switch (severity) {
            case ERROR -> "fatal";
            case WARNING -> "warning";
            case INFO -> "information";
        };
    }

    /**
     * Returns the word a rule pack writes a severity with.
     *
     * @param severity the severity
     * @return {@code fatal}, {@code warning} or {@code info}
     */
    static String rule(Severity severity) {
        return switch (severity) {
            case ERROR -> "fatal";
            case WARNING -> "warning";
            case INFO -> "info";
        };
    }
}
