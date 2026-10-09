package de.bsnsoft.esj.cli.serve;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * What the heap of a server has to hold, and the parallelism that fits into the heap it has.
 *
 * <p>The server reads no document, but every call of a tool reads what its child wrote, builds
 * a result from it and writes an answer, and every request is a message held while it is
 * read. Neither follows the document. A result keeps at most about {@link Results#KEPT}
 * characters of what a child wrote, whatever {@code --limits} lets the child read; and all of
 * it — what the child wrote, read token by token, the result, the answer written from it into
 * a {@link Spool} — exists only while the call holds the place of a child
 * ({@link Calls#call}), so at most {@code max-jobs} calls hold it at once, however many wait,
 * finish, or are being read by clients that read slowly or not at all. Every other thread
 * holds a request: a message of at most {@link Messages#MAX_CHARACTERS} characters and
 * {@link Messages#MAX_VALUES} values, a batch of at most {@link Mcp#BATCH} messages and one
 * call, the heap part of a spool it sends from, {@link Spool#MEMORY}. The heap a server needs
 * is therefore {@code BASE + max-jobs × CALL + (max-queue + extra) × REQUEST}, where the
 * threads beyond the children are those of the queue and the {@code extra} of the transport.
 *
 * <p>The three amounts are at least twice what was measured (October 2026, on
 * the jar with G1 and with the serial collector, with {@code --limits large}): a server that
 * answers nothing but small calls needs 7 to 8 MiB; a call that carries the largest result a
 * tool makes — a {@code get} of three values of 32 Ki characters that every text and every
 * JSON of the result escapes, about 2.5 MB of answer — adds at most 3 MiB while it runs; a
 * thread that holds a message at both its bounds, about 0.2 MiB. The measure is the smallest
 * heap at which a server survives the calls, found with a ballast of live memory beside it;
 * the figures, with the summaries of a million values, validations with fifty thousand
 * findings and the rest of the tools, are in {@code docs/serve.md}.
 *
 * <p>Where the heap of the process is smaller than that, the server does not start with a
 * parallelism it cannot hold — a heap exhausted by the answers of several large documents at
 * once would end the server and every call with it, which is what the process boundary is
 * for. It lowers {@code --max-queue} to {@code --max-jobs}, then both together, until they
 * fit, gives the queue back what the heap still holds, and says so on its error stream; it
 * refuses to start where not even one call fits.
 *
 * <p>The disk is held the same way ({@link Disk}): every running child is given
 * {@link Jobs#MAX_WRITTEN} bytes of {@code --max-disk} before it starts, and beside the
 * children the disk has to hold the files of at least one call ({@link #callFiles(long)}).
 * Where {@code --max-disk} holds fewer children than {@code --max-jobs}, the server lowers
 * {@code --max-jobs} before it fits the heap, and says so; it refuses to start where not even
 * one child fits. What else the disk holds — the store, the answers that wait for slow
 * clients, the bodies of waiting calls — is refused when it does not fit, call by call.
 */
final class Capacity {

    private static final long MIB = 1024L * 1024;

    /** The most heap one call holds while it holds the place of a child: twice its measure. */
    static final long CALL = 6 * MIB;

    /** The most heap one thread without a child holds: five times its measure, rounded up. */
    static final long REQUEST = MIB;

    /** The heap of a server beside its calls and requests: four times its measure. */
    static final long BASE = 32 * MIB;

    /** The threads of the HTTP server beyond the children and the queue. */
    static final int HTTP_EXTRA = 16;

    /**
     * What the standard streams hold beside their calls: the line being read and its copy,
     * and the one worker beyond the children and the queue.
     */
    static final int STDIO_EXTRA = 3;

    private Capacity() {
    }

    /**
     * Returns what one call holds on disk beside its child's share, at most: an upload over
     * MCP, its base64 as it was read and its bytes in the store at once — a request body or
     * the copy of a file named by path, with an answer of the child's own bytes, holds less.
     *
     * @param maxUpload the largest document, {@code --max-upload}
     * @return the bytes
     */
    static long callFiles(long maxUpload) {
        return Calls.base64Length(maxUpload) + maxUpload;
    }

    /**
     * Returns how many children a disk holds at once beside the files of one call.
     *
     * @param maxDisk   the bound of the disk, {@code --max-disk}
     * @param maxUpload the largest document, {@code --max-upload}
     * @return the children, 0 where not even one fits
     */
    static int jobsOnDisk(long maxDisk, long maxUpload) {
        long left = maxDisk - callFiles(maxUpload);
        return left < Jobs.MAX_WRITTEN ? 0 : (int) Math.min(Integer.MAX_VALUE,
                left / Jobs.MAX_WRITTEN);
    }

    /**
     * Returns settings whose children fit on the disk: {@code --max-jobs} lowered to what
     * {@code --max-disk} holds, with a warning.
     *
     * @param config the settings
     * @param log    where the warning goes
     * @return the settings
     * @throws IllegalArgumentException where not even one child fits
     */
    static ServeConfig fitDisk(ServeConfig config, Consumer<String> log) {
        int fits = jobsOnDisk(config.maxDisk(), config.maxUpload());
        long one = Jobs.MAX_WRITTEN + callFiles(config.maxUpload());
        if (fits < 1) {
            throw new IllegalArgumentException("--max-disk " + Disk.mib(config.maxDisk())
                    + " holds not even one call; with --max-upload "
                    + Disk.mib(config.maxUpload()) + " it needs at least " + Disk.mib(one)
                    + ": " + Disk.mib(Jobs.MAX_WRITTEN) + " for what the child writes and "
                    + Disk.mib(callFiles(config.maxUpload())) + " for the files of the call");
        }
        if (fits >= config.maxJobs()) {
            return config;
        }
        log.accept("warning: --max-disk " + Disk.mib(config.maxDisk()) + " holds the files of "
                + fits + " children at once, " + Disk.mib(Jobs.MAX_WRITTEN) + " each beside "
                + Disk.mib(callFiles(config.maxUpload())) + " for the files of one call, not"
                + " the " + config.maxJobs() + " asked for, which need "
                + Disk.mib(config.maxJobs() * Jobs.MAX_WRITTEN + callFiles(config.maxUpload()))
                + "; running with " + fits);
        return config.withJobs(fits, config.maxQueue(), config.queueWait(), config.jobHeap(),
                config.jobTimeout());
    }

    /**
     * Says, once the temporary directory is made, how much heap the server needs for its
     * calls and how much disk it holds itself to, and warns where the file system of the
     * directory has less room than {@code --max-disk}: a write past what it has fails however
     * the server counts. The line names nothing of the machine but the processors that the
     * default {@code --max-jobs} follows, so that every packaged artefact writes the same.
     *
     * @param config the settings the server runs with
     * @param extra  the requests of the transport that run no child
     * @param store  the temporary directory
     * @param log    where the lines go
     */
    static void report(ServeConfig config, int extra, Store store, Consumer<String> log) {
        log.accept("bounds: heap " + mib(needed(config.maxJobs(), config.maxQueue(), extra))
                + " for " + count(config.maxJobs(), "child", "children") + " and "
                + count(config.maxQueue(), "call", "calls") + " waiting; disk "
                + Disk.mib(config.maxDisk()) + " (--max-disk), "
                + Disk.mib(Jobs.MAX_WRITTEN) + " of it for each running child");
        checkDisk(store, log);
    }

    /**
     * Warns where the file system of the temporary directory has less room than
     * {@code --max-disk}.
     *
     * @param store the temporary directory
     * @param log   where the warning goes
     */
    static void checkDisk(Store store, Consumer<String> log) {
        try {
            long usable = java.nio.file.Files.getFileStore(store.root()).getUsableSpace();
            if (usable < store.disk().limit()) {
                log.accept("warning: the file system of " + store.root() + " has "
                        + Disk.mib(usable) + " free, less than --max-disk "
                        + Disk.mib(store.disk().limit()) + "; a call is refused where it is"
                        + " full rather than where --max-disk is reached");
            }
        } catch (java.io.IOException | SecurityException e) {
            // A file system that does not say: --max-disk is the bound.
        }
    }

    /**
     * Returns the heap a server needs.
     *
     * @param jobs  how many children run at once
     * @param queue how many calls wait
     * @param extra the requests that run no child
     * @return the bytes
     */
    static long needed(int jobs, int queue, int extra) {
        return BASE + jobs * CALL + (long) (queue + extra) * REQUEST;
    }

    /**
     * Returns settings whose parallelism fits into a heap.
     *
     * @param config the settings
     * @param extra  the requests of the transport that run no child
     * @param heap   the heap of this process, {@link Runtime#maxMemory()}
     * @param log    where the warning goes, where the parallelism is lowered
     * @return the settings, with fewer children or a shorter queue where those fit
     * @throws IllegalArgumentException where not even one call fits
     */
    static ServeConfig fit(ServeConfig config, int extra, long heap, Consumer<String> log) {
        config = fitDisk(config, log);
        int jobs = config.maxJobs();
        int queue = config.maxQueue();
        if (needed(jobs, queue, extra) <= heap) {
            return config;
        }
        // A queue longer than the children it waits for goes first; then a child and a
        // place in the queue at a time; and what the heap still holds goes back to the queue.
        while (needed(jobs, queue, extra) > heap) {
            if (queue > jobs) {
                queue--;
            } else if (jobs > 1) {
                jobs--;
                queue = Math.min(queue, jobs);
            } else if (queue > 0) {
                queue--;
            } else {
                throw new IllegalArgumentException("the heap of this process, " + mib(heap)
                        + ", holds not even one call; it needs at least "
                        + mib(needed(1, 0, extra)) + " (-Xmx, or ESJ_SERVE_HEAP in the"
                        + " container image)");
            }
        }
        while (queue < config.maxQueue() && needed(jobs, queue + 1, extra) <= heap) {
            queue++;
        }
        log.accept("warning: the heap of this process, " + mib(heap) + ", holds "
                + jobs + " children at once and " + queue + " calls waiting, not the "
                + config.maxJobs() + " and " + config.maxQueue() + " asked for, which need "
                + mib(needed(config.maxJobs(), config.maxQueue(), extra))
                + " (-Xmx, or ESJ_SERVE_HEAP in the container image); running with those");
        return config.withJobs(jobs, queue, config.queueWait(), config.jobHeap(),
                config.jobTimeout());
    }

    private static String mib(long bytes) {
        return String.format(Locale.ROOT, "%d MiB", bytes / MIB);
    }

    private static String count(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }
}
