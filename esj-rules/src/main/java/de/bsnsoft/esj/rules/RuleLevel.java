package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.validate.Severity;
import java.util.Optional;

/**
 * The words a rule file and a rule finding write a severity with: {@code fatal},
 * {@code warning} and {@code info}, the spellings of {@link Severity#ERROR},
 * {@link Severity#WARNING} and {@link Severity#INFO} in the rule language.
 */
final class RuleLevel {

    private RuleLevel() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the severity a rule file may declare for a rule.
     *
     * <p>{@link Severity#INFO} is deliberately not among the answers: it is the engine's
     * and not the rule author's. It carries no judgement about the document and is used
     * for the one thing an engine has to be able to say beside a verdict — that a rule was
     * not decided.
     *
     * @param token the token as it stands in the rule file
     * @return the severity, or an empty optional if the token is not one a rule may
     *         declare
     */
    static Optional<Severity> declared(String token) {
        if ("fatal".equals(token)) {
            return Optional.of(Severity.ERROR);
        }
        if ("warning".equals(token)) {
            return Optional.of(Severity.WARNING);
        }
        return Optional.empty();
    }

    /**
     * Returns the word a rule finding writes a severity with.
     *
     * @param severity the severity
     * @return {@code fatal}, {@code warning} or {@code info}
     */
    static String word(Severity severity) {
        return switch (severity) {
            case ERROR -> "fatal";
            case WARNING -> "warning";
            case INFO -> "info";
        };
    }
}
