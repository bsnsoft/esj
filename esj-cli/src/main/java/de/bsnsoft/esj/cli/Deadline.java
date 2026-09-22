package de.bsnsoft.esj.cli;

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
 * The time one command was given, and what is left of it.
 *
 * <p>It exists because a bound that covers half of a run is not a bound. Taking the bytes
 * in, reading an XML invoice into a semantic document and running the official artefacts
 * over it are the expensive parts of {@code esj validate}, and on a document built to be
 * expensive any of them can be the largest — so {@code --max-runtime} is one number over
 * all of them, spent in the order the work is done: what one step takes is taken off what
 * the next may take.
 *
 * <p>The first step is the read, and it is inside the bound for the same reason the others
 * are. A bounded number of bytes is not a bounded amount of time: a named pipe whose
 * writer stops writing without closing the descriptor, and a file on a file system that
 * has stopped answering, both hand a reader a wait with no end in it. A caller who set a
 * number because the document came from a stranger gets that number.
 *
 * <p>Neither half can be told to stop, so a step that outlasts the time is run on a thread
 * of its own and abandoned rather than stopped: the caller is answered at the deadline
 * with {@link ExitCode#LIMIT} and no verdict, and the thread is a daemon so that a process
 * which has given up on a document can leave without waiting for it. The command line runs
 * one document per process, which is what makes abandoning it enough; the same reasoning
 * is written out in the budget of the syntax engine, which this class is the outer half
 * of.
 */
final class Deadline {

    private static final ExecutorService RUNS = Executors.newCachedThreadPool(new Daemons());

    private final Duration total;
    private final long deadlineNanos;

    private Deadline(Duration total) {
        this.total = total;
        this.deadlineNanos = System.nanoTime() + total.toNanos();
    }

    /**
     * Starts the clock.
     *
     * @param total how long the whole command may take
     * @return the deadline
     */
    static Deadline of(Duration total) {
        return new Deadline(total);
    }

    /**
     * Returns the time the whole command was given.
     *
     * @return the total
     */
    Duration total() {
        return total;
    }

    /**
     * Runs one step of the command inside what is left of the time.
     *
     * @param what the step, named in English for the message
     * @param step the step
     * @param <T>  what the step produces
     * @return what the step produced
     * @throws CliException if the step was still running at the deadline
     */
    <T> T within(String what, Callable<T> step) {
        Duration left = remaining(what);
        Future<T> running = RUNS.submit(step);
        try {
            return running.get(left.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            running.cancel(true);
            throw outOfTime(what);
        } catch (InterruptedException e) {
            running.cancel(true);
            Thread.currentThread().interrupt();
            throw CliException.limit("the thread running this command was interrupted"
                    + " while " + what + " was running", e);
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

    /**
     * Returns what is left of the time, for a step that measures itself.
     *
     * @param next the step the time is for, named in English for the message
     * @return the remaining time, which is always positive
     * @throws CliException if none is left
     */
    Duration remaining(String next) {
        Duration left = Duration.ofNanos(deadlineNanos - System.nanoTime());
        if (left.isZero() || left.isNegative()) {
            throw CliException.limit(next + " did not start: the " + total.toMillis()
                    + " ms this run was given had gone by the time the rest of it was"
                    + " done, so this document has no verdict; --max-runtime gives it"
                    + " more");
        }
        return left;
    }

    private CliException outOfTime(String what) {
        return CliException.limit(what + " was still running after " + total.toMillis()
                + " ms, which is the time this run was given, so this document has no"
                + " verdict; --max-runtime gives it more");
    }

    /** Names the threads and makes them daemons, so an abandoned one holds nothing up. */
    private static final class Daemons implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "esj-cli");
            thread.setDaemon(true);
            return thread;
        }
    }
}
