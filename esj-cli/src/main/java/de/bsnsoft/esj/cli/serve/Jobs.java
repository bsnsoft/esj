package de.bsnsoft.esj.cli.serve;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The child processes: how many run at once, how many wait, and how each is started,
 * bounded and stopped.
 *
 * <p>A child is this tool started again ({@link SelfCommand}) with one command, in a working
 * directory of its own, its standard input the document as a file and its error stream
 * redirected to a file there. Its standard output passes through this process into a file
 * of that directory, a buffer at a time, so that it is held to its bound exactly and grows
 * nothing here. It is given the heap ceiling of {@code --job-heap}, the deadline of
 * {@code --job-timeout} as its own {@code --max-runtime}, and an environment built from a
 * short list of names rather than inherited: the token of the server and every other
 * variable of its operator stay here. Where it outlives its deadline by
 * {@link #KILL_GRACE} it is killed from outside; where what it wrote outgrows
 * {@link #MAX_WRITTEN} it is killed as well, and so it is where the call is cancelled or
 * the server stops. What it wrote stays in its files: this process reads them as streams and
 * holds none of them.
 *
 * <p>Its directory is given its share of {@code --max-disk}, {@link #MAX_WRITTEN} bytes,
 * before the child starts, and a child whose share does not fit is not started
 * ({@link Disk}). Its standard output is held to the share as it arrives; its files are
 * measured every {@link #POLL_MILLIS} milliseconds, and once it has ended they are counted
 * in place of the share.
 */
final class Jobs implements AutoCloseable {

    /** How long past its own deadline a child is given before it is killed. */
    static final Duration KILL_GRACE = Duration.ofSeconds(5);

    /**
     * The most a child may write: its standard output, its error stream and its files
     * together, and the share of {@code --max-disk} its directory is given before it starts.
     */
    static final long MAX_WRITTEN = 64L * 1024 * 1024;

    /** The most of its error stream that is kept. */
    static final int MAX_STDERR = 16 * 1024;

    /** How often a running child is looked at. */
    private static final long POLL_MILLIS = 100;

    /** The variables of this process a child is given, where they are set. */
    private static final List<String> INHERITED = List.of("PATH", "HOME", "LANG", "LC_ALL",
            "LC_CTYPE", "TZ", "TMPDIR", "ESJ_PACKS", "SystemRoot", "TEMP", "TMP");

    private final ServeConfig config;
    private final Disk disk;
    private final Semaphore slots;
    private final AtomicInteger waiting = new AtomicInteger();
    private final Set<Process> running = ConcurrentHashMap.newKeySet();
    private final List<String> prefix;
    private final Map<String, String> environment;
    private volatile boolean closed;

    /**
     * Prepares the children of one server.
     *
     * @param config the settings
     * @param disk   the account of the temporary directory, in whose directory of the
     *               server's own files the working directories of the children are made
     */
    Jobs(ServeConfig config, Disk disk) {
        this.config = config;
        this.disk = disk;
        this.slots = new Semaphore(config.maxJobs(), true);
        this.prefix = config.child().isEmpty() ? SelfCommand.of(config.jobHeap())
                : config.child();
        this.environment = environment(config.environment());
    }

    /** Builds the environment of a child from the names of {@link #INHERITED}. */
    static Map<String, String> environment(Map<String, String> of) {
        Map<String, String> chosen = new LinkedHashMap<>();
        for (String name : INHERITED) {
            String value = of.get(name);
            if (value != null) {
                chosen.put(name, value);
            }
        }
        return Map.copyOf(chosen);
    }

    /** Returns the command line a child starts with, before its command. */
    List<String> prefix() {
        return prefix;
    }

    /** The command line was refused because every child is busy and the queue is full. */
    static final class Busy extends Exception {
        private static final long serialVersionUID = 1L;

        Busy(String message) {
            super(message);
        }
    }

    /**
     * The cancellation of one call: a client that no longer wants the answer says so, and
     * the call stops waiting for a child, or its child is ended.
     */
    static final class Cancel {

        /** The cancellation of a call nobody can cancel. */
        static final Cancel NEVER = new Cancel();

        private volatile boolean cancelled;

        /** Cancels the call. */
        void cancel() {
            if (this != NEVER) {
                cancelled = true;
            }
        }

        /** Tells whether the call was cancelled. */
        boolean cancelled() {
            return cancelled;
        }
    }

    /** The call was cancelled while it waited for a child. */
    static final class Cancelled extends Exception {
        private static final long serialVersionUID = 1L;

        Cancelled() {
            super("the call was cancelled");
        }
    }

    /** A place to run one child, held from {@link #acquire(Cancel)} to {@link #close()}. */
    final class Slot implements AutoCloseable {
        private final Path directory;
        private boolean released;

        private Slot(Path directory) {
            this.directory = directory;
        }

        /** Returns the working directory of this child, empty until it runs. */
        Path directory() {
            return directory;
        }

        @Override
        public void close() {
            if (!released) {
                released = true;
                disk.deleteTree(directory);
                slots.release();
            }
        }
    }

    /**
     * Takes a slot, waiting up to {@code --queue-wait} where every child is busy, and gives
     * its working directory its share of the disk.
     *
     * @param cancel the cancellation of the call, looked at while it waits
     * @return the slot, with a fresh working directory
     * @throws Busy      where the queue is full, no slot came free in time, or the server
     *                   is shutting down
     * @throws Cancelled where the call was cancelled while it waited
     * @throws Disk.Full where the share of a child does not fit beside what the server holds
     */
    Slot acquire(Cancel cancel) throws Busy, Cancelled, Disk.Full {
        if (closed) {
            throw new Busy("the server is shutting down");
        }
        if (cancel.cancelled()) {
            throw new Cancelled();
        }
        if (!slots.tryAcquire()) {
            if (waiting.incrementAndGet() > config.maxQueue()) {
                waiting.decrementAndGet();
                throw new Busy("all " + config.maxJobs() + " children are busy and "
                        + config.maxQueue() + " requests are waiting");
            }
            try {
                long deadline = System.nanoTime() + config.queueWait().toNanos();
                boolean taken = false;
                while (!taken) {
                    long left = deadline - System.nanoTime();
                    if (left <= 0) {
                        throw new Busy("no child came free within "
                                + config.queueWait().toSeconds() + " s");
                    }
                    taken = slots.tryAcquire(Math.min(left, TimeUnit.MILLISECONDS.toNanos(
                            POLL_MILLIS)), TimeUnit.NANOSECONDS);
                    if (!taken && cancel.cancelled()) {
                        throw new Cancelled();
                    }
                    if (!taken && closed) {
                        throw new Busy("the server is shutting down");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Busy("interrupted while waiting for a child");
            } finally {
                waiting.decrementAndGet();
            }
        }
        if (closed) {
            slots.release();
            throw new Busy("the server is shutting down");
        }
        Path directory;
        try {
            directory = Files.createTempDirectory(disk.directory(), "job-");
        } catch (IOException e) {
            slots.release();
            throw new UncheckedIOException(e);
        }
        try {
            disk.reserve(directory, MAX_WRITTEN);
        } catch (Disk.Full full) {
            Trees.delete(directory);
            slots.release();
            throw full;
        }
        return new Slot(directory);
    }

    /**
     * What a child left behind.
     *
     * @param exitCode   its exit code, or -1 where it was killed
     * @param killed     why it was killed, empty where it ended by itself
     * @param stdout     the file of what it wrote to the standard output, in the working
     *                   directory of the slot; it is read from there and never whole
     * @param stderr     the beginning of what it wrote to the error stream
     * @param millis     how long it ran
     */
    record Result(int exitCode, String killed, Path stdout, String stderr, long millis) {

        /** Tells whether the child was killed from outside. */
        boolean wasKilled() {
            return !killed.isEmpty();
        }
    }

    /**
     * Runs one child and waits for it.
     *
     * @param slot      the slot it runs in
     * @param arguments the command of the tool and its arguments
     * @param stdin     the document, read as the standard input; {@code null} for none
     * @param cancel    the cancellation of the call: where it comes, the child is ended
     * @return what it left behind
     */
    Result run(Slot slot, List<String> arguments, Path stdin, Cancel cancel) {
        List<String> command = new ArrayList<>(prefix);
        command.addAll(arguments);
        // The deadline and the limit profile of the server, the child's own, so that it
        // stops itself with exit code 7 and a line that says why before it is killed.
        command.add("--max-runtime");
        command.add(config.jobTimeout().toMillis() + "ms");
        if (!"default".equals(config.limits())) {
            command.add("--limits");
            command.add(config.limits());
        }
        Path out = slot.directory().resolve(".stdout");
        Path err = slot.directory().resolve(".stderr");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(slot.directory().toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(err.toFile());
        builder.redirectInput(stdin == null ? ProcessBuilder.Redirect.from(nullDevice().toFile())
                : ProcessBuilder.Redirect.from(stdin.toFile()));
        builder.environment().clear();
        builder.environment().putAll(environment);
        long started = System.nanoTime();
        if (closed) {
            return new Result(-1, "shutdown", out, "", 0);
        }
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("the child process could not be started", e);
        }
        running.add(process);
        Drain drain = new Drain(process.getInputStream(), out);
        Thread draining = new Thread(drain, "esj-serve-stdout");
        draining.setDaemon(true);
        draining.start();
        String killed = "";
        try {
            long deadline = started + config.jobTimeout().plus(KILL_GRACE).toNanos();
            while (!process.waitFor(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                if (closed) {
                    killed = "shutdown";
                    break;
                }
                if (cancel.cancelled()) {
                    killed = "cancelled";
                    break;
                }
                if (System.nanoTime() > deadline) {
                    killed = "timeout";
                    break;
                }
                if (drain.failure() != null) {
                    killed = "disk";
                    break;
                }
                // The files beside the standard output, which the drain holds to what is left.
                long files = Disk.size(slot.directory()) - drain.written();
                drain.besides(files);
                if (drain.past() || drain.written() + files > MAX_WRITTEN) {
                    killed = "output";
                    break;
                }
            }
            if (killed.isEmpty() && closed) {
                // Ended by close() while it waited: the exit code of a signal, not a crash.
                killed = "shutdown";
            }
            if (!killed.isEmpty()) {
                stop(process);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            killed = closed ? "shutdown" : "interrupted";
            stop(process);
        } catch (RuntimeException e) {
            stop(process);
            throw e;
        } finally {
            running.remove(process);
            drain.finish(draining);
        }
        long written = disk.settle(slot.directory());
        if (killed.isEmpty() && drain.failure() != null) {
            killed = "disk";
        }
        if (killed.isEmpty() && (drain.past() || written > MAX_WRITTEN)) {
            killed = "output";
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        int code = killed.isEmpty() ? process.exitValue() : -1;
        return new Result(code, killed, out, text(err), millis);
    }

    /**
     * Copies the standard output of a child into its file as it arrives, and stops at the
     * child's share: the bytes past it are not written, and the child is killed.
     */
    private static final class Drain implements Runnable {
        private final InputStream in;
        private final Path file;
        private volatile long written;
        private volatile long besides;
        private volatile boolean past;
        private volatile boolean abandoned;
        private volatile IOException failure;

        Drain(InputStream in, Path file) {
            this.in = in;
            this.file = file;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[64 * 1024];
            try (OutputStream sink = Files.newOutputStream(file)) {
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (written + read + besides > MAX_WRITTEN) {
                        past = true;
                        break;
                    }
                    sink.write(buffer, 0, read);
                    written += read;
                }
            } catch (IOException e) {
                if (!abandoned) {
                    failure = e;
                }
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    // The child is gone or going.
                }
            }
        }

        /** Returns the bytes written so far. */
        long written() {
            return written;
        }

        /** Says how many bytes the child's other files hold, which its output may not pass. */
        void besides(long bytes) {
            besides = Math.max(0, bytes);
        }

        /** Tells whether the output passed the child's share. */
        boolean past() {
            return past;
        }

        /** Returns why the copy failed, if it did. */
        IOException failure() {
            return failure;
        }

        /** Waits for the copy to end, once the child has: a child's child may hold the pipe. */
        void finish(Thread thread) {
            try {
                thread.join(TimeUnit.SECONDS.toMillis(5));
                if (thread.isAlive()) {
                    abandoned = true;
                    in.close();
                    thread.join(TimeUnit.SECONDS.toMillis(5));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // Closed already.
            }
        }
    }

    private static Path nullDevice() {
        return Path.of(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null");
    }

    /** Ends a child: politely, then for good. */
    private static void stop(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static String text(Path file) {
        if (!Files.exists(file)) {
            return "";
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(MAX_STDERR);
            return new String(head, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /** Returns how many children run now. */
    int running() {
        return running.size();
    }

    /** Tells whether the children were stopped, and no new one is started. */
    boolean closed() {
        return closed;
    }

    /**
     * Stops every child and refuses new ones. A call whose child is stopped here, or that
     * waits for one, is told that the server is shutting down.
     */
    @Override
    public void close() {
        closed = true;
        List<Process> children = List.copyOf(running);
        children.forEach(Process::destroy);
        for (Process process : children) {
            stop(process);
        }
    }
}
