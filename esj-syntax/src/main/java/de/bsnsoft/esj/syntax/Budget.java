package de.bsnsoft.esj.syntax;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The time one validation was given, and what is left of it.
 *
 * <p>Neither the schema validator of the platform nor an XSLT processor can be told to
 * stop, so a step that outlasts its budget is run on a thread of its own and abandoned
 * rather than stopped: the caller is answered at the deadline with a
 * {@link SyntaxLimitException}, the thread is interrupted in case the step happens to
 * notice, and it is a daemon thread so that a process which has given up on a document
 * can leave without waiting for it. A caller that needs the machine back as well as the
 * answer needs a process around the run, not a smaller number here.
 *
 * <p>The budget is the whole validation's, not each step's. A document that spends four
 * of its five minutes in the schema has one minute left for the rules, which is the
 * arithmetic a caller who set the number expects.
 */
final class Budget {

    private static final ExecutorService RUNS = Executors.newCachedThreadPool(new Daemons());

    private final long deadlineNanos;
    private final Duration total;

    private Budget(Duration total) {
        this.total = total;
        this.deadlineNanos = System.nanoTime() + total.toNanos();
    }

    /**
     * Starts a budget.
     *
     * @param total how long the whole validation may take
     * @return the budget
     */
    static Budget of(Duration total) {
        return new Budget(total);
    }

    /**
     * Returns what is left of the budget, which may be zero or negative.
     *
     * @return the remaining time
     */
    Duration remaining() {
        return Duration.ofNanos(deadlineNanos - System.nanoTime());
    }

    /**
     * Runs one step of the validation inside what is left of the budget.
     *
     * @param what the step, named in English for the message
     * @param step the step
     * @param <T>  what the step produces
     * @return what the step produced
     * @throws SyntaxLimitException if the step was still running at the deadline
     */
    <T> T call(String what, Callable<T> step) {
        Duration left = remaining();
        if (left.isZero() || left.isNegative()) {
            throw SyntaxLimitException.outOfTime(what, total);
        }
        Future<T> running = RUNS.submit(step);
        try {
            return running.get(left.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            running.cancel(true);
            throw SyntaxLimitException.outOfTime(what, total);
        } catch (InterruptedException e) {
            running.cancel(true);
            Thread.currentThread().interrupt();
            throw new SyntaxLimitException("the thread validating this document was"
                    + " interrupted while " + what + " was running");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(what + " failed", cause);
        }
    }

    /** Names the threads and makes them daemons, so an abandoned one holds nothing up. */
    private static final class Daemons implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "esj-syntax");
            thread.setDaemon(true);
            return thread;
        }
    }
}
