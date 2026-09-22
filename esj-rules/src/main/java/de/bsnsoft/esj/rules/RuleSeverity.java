package de.bsnsoft.esj.rules;

import java.util.Locale;
import java.util.Optional;

/**
 * How much a rule finding weighs.
 *
 * <p>A rule of a pack declares {@link #FATAL} or {@link #WARNING} and nothing else: those
 * are the two levels a publisher of business rules sets, and they are what the rule
 * language admits. {@link #INFO} is the engine's own and cannot be written in a rule file.
 * It carries no judgement about the document at all and is used for the one thing an
 * engine has to be able to say beside a verdict — that a rule was not decided, because a
 * value it reads does not spell what its semantic data type requires. That is a defect the
 * structural validator already reports at layer L2, and reporting it a second time as a
 * business rule failure would make one problem look like two.
 */
public enum RuleSeverity {

    /** The document fails the rule; a fatal finding decides the verdict. */
    FATAL("fatal"),

    /** The document passes the rule, and something about it is worth saying. */
    WARNING("warning"),

    /** No judgement: the rule could not be decided on this document. */
    INFO("info");

    private final String token;

    RuleSeverity(String token) {
        this.token = token;
    }

    /**
     * Returns the lower case token this severity is written with in a rule file and in a
     * report.
     *
     * @return the token, for example {@code fatal}
     */
    public String token() {
        return token;
    }

    /**
     * Returns the severity a rule file may declare for a rule.
     *
     * <p>{@link #INFO} is deliberately not among the answers: it is the engine's and not
     * the rule author's.
     *
     * @param token the token as it stands in the rule file
     * @return the severity, or an empty optional if the token is not one a rule may
     *         declare
     */
    public static Optional<RuleSeverity> declared(String token) {
        if (FATAL.token.equals(token)) {
            return Optional.of(FATAL);
        }
        if (WARNING.token.equals(token)) {
            return Optional.of(WARNING);
        }
        return Optional.empty();
    }

    /**
     * Returns the token, so that a severity prints as it is written.
     *
     * @return the token
     */
    @Override
    public String toString() {
        return token.toLowerCase(Locale.ROOT);
    }
}
