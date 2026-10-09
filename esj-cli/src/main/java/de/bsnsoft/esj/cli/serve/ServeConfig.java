package de.bsnsoft.esj.cli.serve;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The settings of one {@code esj serve} or {@code esj mcp} process.
 *
 * <p>Every number here is a ceiling the operator chose: how many child processes run at
 * once and how many requests wait for one, how much heap and time each child is given,
 * how large an upload may be, how long an upload or an artefact is kept and how much of
 * them is kept at once. The command line commands fill it; a test builds one directly.
 *
 * @param version        the version of the tool, for {@code serverInfo} and the OpenAPI
 *                       description
 * @param bind           the address the HTTP server listens on
 * @param port           the port, 0 for one the system chooses
 * @param publicUrl      the URL the server is reached at from outside, for the links to
 *                       artefacts; absent, it is taken from the {@code Host} of the request
 *                       where that is one this server answers to, and a link is the path on
 *                       this server otherwise
 * @param token          the bearer token every request to {@code /api} and {@code /mcp}
 *                       must carry; absent, none is asked for
 * @param allowedOrigins the browser origins a request may come from beside a loopback one
 * @param allowedHosts   the values of the {@code Host} header, beside the loopback names
 *                       and the bind address with the port of the server and the hosts of
 *                       {@code allowedOrigins}, from which absolute links are built where no
 *                       {@code publicUrl} is set
 * @param allowedDirs    the directories a {@code path} may name; empty, a {@code path} is
 *                       refused over HTTP and anything readable is taken over stdio
 * @param templates      the directory the render templates are taken from by name
 * @param packs          the pack directories of {@code --packs}, handed to every child that
 *                       validates or inspects
 * @param limits         the limit profile handed to every child, {@code default} or
 *                       {@code large}
 * @param maxJobs        how many child processes run at once
 * @param maxQueue       how many requests wait for one beyond that
 * @param queueWait      how long a request waits for one
 * @param jobHeap        the heap ceiling of a child, as {@code -Xmx} takes it
 * @param jobTimeout     how long a child may run before it is stopped
 * @param maxUpload      the largest document a request may carry, in bytes
 * @param requestTimeout how long the reading of one request may take
 * @param ttl            how long an upload or an artefact is kept
 * @param maxStored      how many uploads and artefacts are kept at once
 * @param maxStoredBytes how many bytes of them are kept at once
 * @param maxDisk        how many bytes the temporary directory holds at once: uploads,
 *                       artefacts, request bodies, answers that wait for their client and
 *                       the files of the running children ({@link Disk})
 * @param tempRoot       the directory the temporary directory of this process is made in
 * @param child          the command line a child process starts with, before the command
 *                       of the tool; empty, it is the command this process was started
 *                       with ({@link SelfCommand})
 * @param environment    the environment of this process, from which the children's is
 *                       chosen
 * @param log            where one line per request goes
 */
public record ServeConfig(
        String version,
        String bind,
        int port,
        Optional<String> publicUrl,
        Optional<String> token,
        List<String> allowedOrigins,
        List<String> allowedHosts,
        List<Path> allowedDirs,
        Optional<Path> templates,
        List<Path> packs,
        String limits,
        int maxJobs,
        int maxQueue,
        Duration queueWait,
        String jobHeap,
        Duration jobTimeout,
        long maxUpload,
        Duration requestTimeout,
        Duration ttl,
        int maxStored,
        long maxStoredBytes,
        long maxDisk,
        Path tempRoot,
        List<String> child,
        Map<String, String> environment,
        Consumer<String> log) {

    /** The default heap ceiling of a child: the ceiling of every packaged artefact. */
    public static final String DEFAULT_JOB_HEAP = "512m";

    /** The default time a child may run: the built-in deadline of the command line. */
    public static final Duration DEFAULT_JOB_TIMEOUT = Duration.ofMinutes(5);

    /** The default largest upload. */
    public static final long DEFAULT_MAX_UPLOAD = 32L * 1024 * 1024;

    /** How long an upload or an artefact is kept by default. */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    /** How many uploads and artefacts are kept at once by default. */
    public static final int DEFAULT_MAX_STORED = 256;

    /** How many bytes of them are kept at once by default: half the default tmpfs of
     * {@code dist/compose.yaml}, so that a full store leaves room for the calls. */
    public static final long DEFAULT_MAX_STORED_BYTES = 256L * 1024 * 1024;

    /**
     * How many bytes the temporary directory holds at once by default: within the 512 MiB
     * tmpfs of {@code dist/compose.yaml}, with the share of one child to spare.
     */
    public static final long DEFAULT_MAX_DISK = 448L * 1024 * 1024;

    /** How long the reading of one request may take by default. */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** How long a request waits for a free child by default. */
    public static final Duration DEFAULT_QUEUE_WAIT = Duration.ofSeconds(10);

    /**
     * Checks the settings and copies the lists and the map they hold.
     *
     * @param version        the version of the tool, for {@code serverInfo} and the OpenAPI
     *                       description
     * @param bind           the address the HTTP server listens on
     * @param port           the port, 0 for one the system chooses
     * @param publicUrl      the URL the server is reached at from outside, for the links to
     *                       artefacts; absent, it is taken from the {@code Host} of the request
     *                       where that is one this server answers to, and a link is the path
     *                       on this server otherwise
     * @param token          the bearer token every request to {@code /api} and {@code /mcp}
     *                       must carry; absent, none is asked for
     * @param allowedOrigins the browser origins a request may come from beside a loopback one
     * @param allowedHosts   the values of the {@code Host} header, beside the loopback names
     *                       and the bind address with the port of the server and the hosts of
     *                       {@code allowedOrigins}, from which absolute links are built where
     *                       no {@code publicUrl} is set
     * @param allowedDirs    the directories a {@code path} may name; empty, a {@code path} is
     *                       refused over HTTP and anything readable is taken over stdio
     * @param templates      the directory the render templates are taken from by name
     * @param packs          the pack directories of {@code --packs}, handed to every child that
     *                       validates or inspects
     * @param limits         the limit profile handed to every child, {@code default} or
     *                       {@code large}
     * @param maxJobs        how many child processes run at once
     * @param maxQueue       how many requests wait for one beyond that
     * @param queueWait      how long a request waits for one
     * @param jobHeap        the heap ceiling of a child, as {@code -Xmx} takes it
     * @param jobTimeout     how long a child may run before it is stopped
     * @param maxUpload      the largest document a request may carry, in bytes
     * @param requestTimeout how long the reading of one request may take
     * @param ttl            how long an upload or an artefact is kept
     * @param maxStored      how many uploads and artefacts are kept at once
     * @param maxStoredBytes how many bytes of them are kept at once
     * @param maxDisk        how many bytes the temporary directory holds at once: uploads,
     *                       artefacts, request bodies, answers that wait for their client and
     *                       the files of the running children ({@link Disk})
     * @param tempRoot       the directory the temporary directory of this process is made in
     * @param child          the command line a child process starts with, before the command
     *                       of the tool; empty, it is the command this process was started
     *                       with ({@link SelfCommand})
     * @param environment    the environment of this process, from which the children's is
     *                       chosen
     * @param log            where one line per request goes
     */
    public ServeConfig {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(bind, "bind");
        publicUrl = Objects.requireNonNull(publicUrl, "publicUrl").map(ServeConfig::trimSlash);
        Objects.requireNonNull(token, "token");
        allowedOrigins = List.copyOf(allowedOrigins);
        allowedHosts = List.copyOf(allowedHosts);
        allowedDirs = List.copyOf(allowedDirs);
        Objects.requireNonNull(templates, "templates");
        packs = List.copyOf(packs);
        Objects.requireNonNull(limits, "limits");
        if (maxJobs < 1) {
            throw new IllegalArgumentException("--max-jobs must be at least 1");
        }
        if (maxQueue < 0) {
            throw new IllegalArgumentException("--max-queue must not be negative");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("--port must be between 0 and 65535");
        }
        if (maxUpload < 1) {
            throw new IllegalArgumentException("--max-upload must be at least one byte");
        }
        if (maxDisk < 1) {
            throw new IllegalArgumentException("--max-disk must be at least one byte");
        }
        Objects.requireNonNull(jobHeap, "jobHeap");
        Objects.requireNonNull(jobTimeout, "jobTimeout");
        Objects.requireNonNull(queueWait, "queueWait");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        Objects.requireNonNull(ttl, "ttl");
        Objects.requireNonNull(tempRoot, "tempRoot");
        child = List.copyOf(child);
        environment = Map.copyOf(environment);
        Objects.requireNonNull(log, "log");
    }

    /**
     * Returns the default settings: loopback, port 8080, no token, no directories, half the
     * processors as children — no more than the default disk holds — and the defaults of the
     * constants of this class.
     *
     * @param version the version of the tool
     * @param log     where one line per request goes
     * @return the settings
     */
    public static ServeConfig defaults(String version, Consumer<String> log) {
        return new ServeConfig(version, "127.0.0.1", 8080, Optional.empty(), Optional.empty(),
                List.of(), List.of(), List.of(), Optional.empty(), List.of(), "default",
                defaultMaxJobs(DEFAULT_MAX_DISK, DEFAULT_MAX_UPLOAD),
                defaultMaxJobs(DEFAULT_MAX_DISK, DEFAULT_MAX_UPLOAD), DEFAULT_QUEUE_WAIT,
                DEFAULT_JOB_HEAP,
                DEFAULT_JOB_TIMEOUT, DEFAULT_MAX_UPLOAD, DEFAULT_REQUEST_TIMEOUT, DEFAULT_TTL,
                DEFAULT_MAX_STORED, DEFAULT_MAX_STORED_BYTES, DEFAULT_MAX_DISK,
                Path.of(System.getProperty("java.io.tmpdir")), List.of(), System.getenv(), log);
    }

    /**
     * Returns half the processors of this machine, and at least one.
     *
     * @return the number of children that run at once by default
     */
    public static int defaultMaxJobs() {
        return Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    }

    /**
     * Returns half the processors of this machine, at least one and at most the children a
     * disk holds beside the files of one call ({@link Capacity#jobsOnDisk(long, long)}).
     *
     * @param maxDisk   the bound of the temporary directory, {@code --max-disk}
     * @param maxUpload the largest document, {@code --max-upload}
     * @return the number of children that run at once by default
     */
    public static int defaultMaxJobs(long maxDisk, long maxUpload) {
        return Math.max(1, Math.min(defaultMaxJobs(), Capacity.jobsOnDisk(maxDisk, maxUpload)));
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Returns a copy listening on another address and port.
     *
     * @param newBind the address
     * @param newPort the port, 0 for one the system chooses
     * @return the copy
     */
    public ServeConfig withListen(String newBind, int newPort) {
        return new ServeConfig(version, newBind, newPort, publicUrl, token, allowedOrigins,
                allowedHosts, allowedDirs, templates, packs, limits, maxJobs, maxQueue, queueWait,
                jobHeap, jobTimeout, maxUpload, requestTimeout, ttl, maxStored, maxStoredBytes,
                maxDisk, tempRoot, child, environment, log);
    }

    /**
     * Returns a copy with another token.
     *
     * @param newToken the token, or empty for none
     * @return the copy
     */
    public ServeConfig withToken(Optional<String> newToken) {
        return new ServeConfig(version, bind, port, publicUrl, newToken, allowedOrigins,
                allowedHosts, allowedDirs, templates, packs, limits, maxJobs, maxQueue, queueWait,
                jobHeap, jobTimeout, maxUpload, requestTimeout, ttl, maxStored, maxStoredBytes,
                maxDisk, tempRoot, child, environment, log);
    }

    /**
     * Returns a copy with other bounds of the children.
     *
     * @param newMaxJobs    how many run at once
     * @param newMaxQueue   how many calls wait beyond those
     * @param newQueueWait  how long a call waits
     * @param newJobHeap    the heap ceiling of each
     * @param newJobTimeout how long each may run
     * @return the copy
     */
    public ServeConfig withJobs(int newMaxJobs, int newMaxQueue, Duration newQueueWait,
                                String newJobHeap, Duration newJobTimeout) {
        return new ServeConfig(version, bind, port, publicUrl, token, allowedOrigins, allowedHosts,
                allowedDirs, templates, packs, limits, newMaxJobs, newMaxQueue, newQueueWait,
                newJobHeap, newJobTimeout, maxUpload, requestTimeout, ttl, maxStored,
                maxStoredBytes, maxDisk, tempRoot, child, environment, log);
    }

    /**
     * Returns a copy with other access settings.
     *
     * @param newOrigins   the browser origins allowed beside loopback ones
     * @param newDirs      the directories a path may name a file in
     * @param newTemplates the template directory
     * @param newPacks     the pack directories
     * @param newPublicUrl the URL the server is reached at from outside
     * @return the copy
     */
    public ServeConfig withAccess(List<String> newOrigins, List<Path> newDirs,
                                  Optional<Path> newTemplates, List<Path> newPacks,
                                  Optional<String> newPublicUrl) {
        return new ServeConfig(version, bind, port, newPublicUrl, token, newOrigins, allowedHosts,
                newDirs, newTemplates, newPacks, limits, maxJobs, maxQueue, queueWait, jobHeap,
                jobTimeout, maxUpload, requestTimeout, ttl, maxStored, maxStoredBytes, maxDisk,
                tempRoot, child, environment, log);
    }

    /**
     * Returns a copy with other values of the {@code Host} header to build links from.
     *
     * @param newHosts the values, each a host with or without a port
     * @return the copy
     */
    public ServeConfig withHosts(List<String> newHosts) {
        return new ServeConfig(version, bind, port, publicUrl, token, allowedOrigins, newHosts,
                allowedDirs, templates, packs, limits, maxJobs, maxQueue, queueWait, jobHeap,
                jobTimeout, maxUpload, requestTimeout, ttl, maxStored, maxStoredBytes, maxDisk,
                tempRoot, child, environment, log);
    }

    /**
     * Returns a copy with other bounds of requests and of the store.
     *
     * @param newMaxUpload      the largest document
     * @param newRequestTimeout how long reading a request may take
     * @param newTtl            how long an upload or artefact is kept
     * @param newMaxStored      how many are kept at once
     * @param newMaxStoredBytes how many bytes of them are kept at once
     * @param newTempRoot       where the temporary directory is made
     * @return the copy
     */
    public ServeConfig withStorage(long newMaxUpload, Duration newRequestTimeout, Duration newTtl,
                                   int newMaxStored, long newMaxStoredBytes, Path newTempRoot) {
        return new ServeConfig(version, bind, port, publicUrl, token, allowedOrigins, allowedHosts,
                allowedDirs, templates, packs, limits, maxJobs, maxQueue, queueWait, jobHeap,
                jobTimeout, newMaxUpload, newRequestTimeout, newTtl, newMaxStored,
                newMaxStoredBytes, maxDisk, newTempRoot, child, environment, log);
    }

    /**
     * Returns a copy with another bound of what the temporary directory holds.
     *
     * @param newMaxDisk the most bytes it holds at once
     * @return the copy
     */
    public ServeConfig withDisk(long newMaxDisk) {
        return new ServeConfig(version, bind, port, publicUrl, token, allowedOrigins, allowedHosts,
                allowedDirs, templates, packs, limits, maxJobs, maxQueue, queueWait, jobHeap,
                jobTimeout, maxUpload, requestTimeout, ttl, maxStored, maxStoredBytes, newMaxDisk,
                tempRoot, child, environment, log);
    }

    /**
     * Returns a copy with another child command, environment, limit profile and log.
     *
     * @param newChild       the command line of a child, or empty for this process's own
     * @param newEnvironment the environment the children's is chosen from
     * @param newLimits      the limit profile of the children
     * @param newLog         where one line per request goes
     * @return the copy
     */
    public ServeConfig withProcess(List<String> newChild, Map<String, String> newEnvironment,
                                   String newLimits, Consumer<String> newLog) {
        return new ServeConfig(version, bind, port, publicUrl, token, allowedOrigins, allowedHosts,
                allowedDirs, templates, packs, newLimits, maxJobs, maxQueue, queueWait, jobHeap,
                jobTimeout, maxUpload, requestTimeout, ttl, maxStored, maxStoredBytes, maxDisk,
                tempRoot, newChild, newEnvironment, newLog);
    }
}
