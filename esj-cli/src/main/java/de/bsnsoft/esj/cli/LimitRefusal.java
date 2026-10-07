package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.EsjLimitException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The one place where a bound a library reached becomes {@link ExitCode#LIMIT}.
 *
 * <p>Every library of this project raises an {@link EsjLimitException} for a bound it
 * reaches, in whichever module it is met — a reader, a writer, the PDF container, the
 * syntax engine, a renderer — so this tool answers all of them in one way: no verdict, the
 * exit code for a limit, and one line that says which bound was met and how to raise it.
 * A command catches none of them. What it adds is the wording of that line, because the
 * command knows what the library does not: the name of its input, the switch a bound
 * belongs to, what is left of the clock. It hands that wording to {@link #during} together
 * with the step that may reach a bound, and a limit that reaches {@link Main} without one
 * is worded for "the input".
 *
 * <p>A command that makes something else of a limit — a row of a report that did not run
 * rather than the end of the command — catches it itself, because that is not a refusal.
 */
final class LimitRefusal {

    private LimitRefusal() {
        throw new AssertionError("no instances");
    }

    /** How a command words the refusal of one step, out of what the library said. */
    @FunctionalInterface
    interface Wording {

        /**
         * Returns the line the refusal is written as.
         *
         * @param limit what the library raised
         * @return the message of a {@link CliException#limit(String)}
         */
        String of(EsjLimitException limit);
    }

    /**
     * Runs one step of a command, and refuses a bound a library reaches in it.
     *
     * @param wording how the refusal is worded
     * @param step    the step
     * @param <T>     what the step produces
     * @return what the step produced
     * @throws CliException with {@link ExitCode#LIMIT} if the step reached a bound
     */
    static <T> T during(Wording wording, Supplier<T> step) {
        Objects.requireNonNull(wording, "wording");
        try {
            return step.get();
        } catch (EsjLimitException limit) {
            throw refused(wording, limit);
        }
    }

    /**
     * Returns the refusal of a bound a library reached.
     *
     * @param wording how the refusal is worded
     * @param limit   what the library raised
     * @return the condition the tool leaves with, {@link ExitCode#LIMIT}
     */
    static CliException refused(Wording wording, EsjLimitException limit) {
        return CliException.limit(wording.of(limit), limit);
    }

    /**
     * Tells whether a bound was the time a step was given rather than a resource of the
     * document, which is the one bound whose remedy is not a larger number for the input.
     *
     * @param limit what the library raised
     * @return {@code true} if the bound is the {@code maxRuntime} of the syntax engine
     */
    static boolean outOfTime(EsjLimitException limit) {
        return limit.bound().map(bound -> "maxRuntime".equals(bound.name())).orElse(false);
    }
}
