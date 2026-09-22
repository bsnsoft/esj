package de.bsnsoft.esj.cli;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * The watchdog behind {@code --max-runtime}: one daemon thread that ends the process when
 * the run takes longer than it was given.
 *
 * <p>An external timeout stays the primary guard. A caller that can spawn a process can
 * also kill one, and killing it is the strongest {@code finally} there is: it takes the
 * heap, the threads, the state of the transformer and the temporary files with it, which
 * no code running inside the process can promise. This option is for the callers that
 * cannot — a cron line, a shell without {@code timeout(1)}, a runner that only collects an
 * exit code — and as a second layer for those that can.
 *
 * <p>It ends the process with {@link Runtime#halt(int)} rather than with
 * {@code System.exit}: a shutdown hook, a finalizer or a flush that is itself stuck on the
 * work the deadline was about would hold the exit open for as long as the work would have
 * taken, which is the one thing a deadline must not do. One line goes to the error stream
 * first, and the code is {@link ExitCode#LIMIT}, because a document that took too long for
 * this run is not thereby an invalid document.
 *
 * <p>That line is written to the error stream and to nothing else, and a second timer is
 * started before it: the same rule applies to the deadline's own report as to everything
 * else the process does at that moment. A consumer that has stopped reading the standard
 * output blocks a writer on it indefinitely, so a watchdog that flushed the standard
 * output before saying anything would end the process when that consumer came back rather
 * than when the deadline passed — which is the failure it exists to prevent. The second
 * timer holds for the error stream what the first holds for everything else: after
 * {@link #WRITE_GRACE_MILLIS} the process ends whether the line was written or not.
 *
 * <p>It is the second half of the deadline rather than the whole of it. A command that
 * can spend its time step by step — {@code esj validate} and its {@link Deadline} — stops
 * itself at the number the caller gave and reports which step ran out in, which is the
 * better answer of the two. The watchdog therefore waits {@link #HANDOVER_MILLIS} past
 * that number before it ends the process, so that the polite answer arrives where there
 * is one; where there is none, because the command holds no deadline of its own or
 * because the work has stopped answering altogether, this is what is left.
 *
 * <p>The clock starts when the switch is read, which is a few milliseconds after the
 * process did — the virtual machine has already started by then, and this class has no
 * honest way to measure that part. A caller that wants the whole life of the process
 * bounded measures it from outside.
 */
final class RuntimeLimit {

    /**
     * How long the deadline waits for its own line to reach the error stream before it
     * ends the process regardless.
     *
     * <p>Long enough that an error stream anybody is reading has taken the line, short
     * enough that it is not a deadline of its own.
     */
    private static final long WRITE_GRACE_MILLIS = 250;

    /**
     * How long the watchdog waits past the deadline for the command to stop itself.
     *
     * <p>Long enough that a command which enforces the deadline itself gets to write its
     * report, short enough that it is not a deadline of its own.
     */
    private static final long HANDOVER_MILLIS = 500;

    private final Console console;
    private final IntConsumer halt;
    private Thread watchdog;

    /**
     * Creates a watchdog.
     *
     * @param console the streams of the process, for the one line it writes
     * @param halt    what ends the process, normally {@code Runtime.getRuntime()::halt}
     */
    RuntimeLimit(Console console, IntConsumer halt) {
        this.console = Objects.requireNonNull(console, "console");
        this.halt = Objects.requireNonNull(halt, "halt");
    }

    /**
     * Starts the watchdog. Called from the parser, so that the clock starts before the
     * command does; a second call replaces the first, which is what a switch written
     * twice on one command line asks for.
     *
     * @param runtime how long the run may take
     */
    synchronized void arm(Duration runtime) {
        cancel();
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(runtime.toMillis() + HANDOVER_MILLIS);
        Thread thread = new Thread(() -> await(deadline, runtime), "esj-max-runtime");
        thread.setDaemon(true);
        watchdog = thread;
        thread.start();
    }

    /**
     * Stops the watchdog, because the run is over. A watchdog that outlived its run would
     * end a process that is already writing its answer.
     */
    synchronized void cancel() {
        if (watchdog != null) {
            watchdog.interrupt();
            watchdog = null;
        }
    }

    private void await(long deadline, Duration runtime) {
        try {
            for (long left = deadline - System.nanoTime(); left > 0;
                    left = deadline - System.nanoTime()) {
                TimeUnit.NANOSECONDS.sleep(left);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        fallback();
        console.deadline("esj: runtime limit of " + Numbers.Runtime.text(runtime)
                + " reached; no verdict on the document");
        halt.accept(ExitCode.LIMIT);
    }

    /**
     * Starts the bare timer that ends the process whatever becomes of the line above it.
     * It carries no console and no state, so there is nothing left for it to block on.
     */
    private void fallback() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(WRITE_GRACE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            halt.accept(ExitCode.LIMIT);
        }, "esj-max-runtime-halt");
        thread.setDaemon(true);
        thread.start();
    }
}
