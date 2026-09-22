package de.bsnsoft.esj.syntax;

import java.time.Duration;
import java.util.Optional;

/**
 * Signals that a run reached a limit it was configured with, and therefore reached no
 * verdict.
 *
 * <p>The limits are the caller's policy rather than a property of the document: how many
 * bytes of input to accept and how long to let the artefacts run. A document that is too
 * large for the configured bound, and one whose Schematron run outlasts the configured
 * time, are both documents this run did not check — not invalid documents.
 *
 * <p>Where the limit was the time, {@link #budget()} names the budget this validation was
 * given. A caller that spends one bound of its own across several steps — reading a
 * document and then validating it, as the {@code esj} command line does — hands this
 * validation what is left rather than what its user asked for, and needs both numbers to
 * say what happened: a message that named only the remainder reads as a tool ignoring the
 * option that was set.
 */
public final class SyntaxLimitException extends SyntaxException {

    private static final long serialVersionUID = 1L;

    /**
     * The time budget this validation was given, or {@code null} where the limit that
     * was reached was not a time.
     *
     * @serial
     */
    private final Duration budget;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public SyntaxLimitException(String message) {
        this(message, null);
    }

    private SyntaxLimitException(String message, Duration budget) {
        super(message);
        this.budget = budget;
    }

    /**
     * Returns the time budget this validation was given, where the limit that was reached
     * was the time.
     *
     * @return the budget, empty where the limit was not a time
     */
    public Optional<Duration> budget() {
        return Optional.ofNullable(budget);
    }

    /**
     * Creates the exception for a run that outlasted its time budget.
     *
     * @param what       what was running, in English
     * @param maxRuntime the budget it was given
     * @return the exception
     */
    static SyntaxLimitException outOfTime(String what, Duration maxRuntime) {
        return new SyntaxLimitException(what + " was still running after "
                + maxRuntime.toMillis() + " ms, which is the time this validation was"
                + " given, so this document has no verdict", maxRuntime);
    }
}
