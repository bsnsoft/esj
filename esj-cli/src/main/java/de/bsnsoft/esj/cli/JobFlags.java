package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.cli.serve.ServeConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The options {@code esj serve} and {@code esj mcp} share: where documents and templates may
 * come from, and the bounds of the child processes every call runs in.
 */
@Command
final class JobFlags {

    /** A heap ceiling as {@code -Xmx} takes it. */
    private static final Pattern HEAP = Pattern.compile("[1-9][0-9]{0,6}[kKmMgG]?");

    @Option(order = 200, names = "--allow-dir", paramLabel = "<directory>",
            description = "A directory a call may name a file in with path; repeatable. A path"
                    + " is resolved to its real path, every link followed, and refused where"
                    + " that lies outside every such directory. esj serve takes no path"
                    + " without one; esj mcp takes any readable file without one.")
    private List<Path> allowDirs = new ArrayList<>();

    @Option(order = 210, names = "--templates", paramLabel = "<directory>",
            description = "The directory of the render templates a call names by name: every"
                    + " <name>.json in it. See docs/templates.md.")
    private Path templates;

    @Option(order = 220, names = "--packs", paramLabel = "<directory>",
            description = "A pack directory every validation and inspection is given, as"
                    + " esj validate --packs; repeatable, and beside the directories"
                    + " ESJ_PACKS names.")
    private List<Path> packs = new ArrayList<>();

    @Option(order = 230, names = "--limits", paramLabel = "<default|large>",
            defaultValue = "default",
            description = "The limit profile every child reads within, as for every other"
                    + " command. Default: default.")
    private String limits;

    @Option(order = 240, names = "--max-jobs", paramLabel = "<count>",
            description = "How many child processes run at once. Default: half the processors,"
                    + " at least 1 and at most what --max-disk holds.")
    private Integer maxJobs;

    @Option(order = 250, names = "--max-queue", paramLabel = "<count>",
            description = "How many calls wait for a child beyond those; a call past them is"
                    + " refused at once (HTTP 503). Default: --max-jobs.")
    private Integer maxQueue;

    @Option(order = 260, names = "--queue-wait", paramLabel = "<duration>",
            converter = Numbers.Runtime.class,
            description = "How long a call waits for a child. Default: 10s.")
    private Duration queueWait = ServeConfig.DEFAULT_QUEUE_WAIT;

    @Option(order = 270, names = "--job-heap", paramLabel = "<size>",
            description = "The heap ceiling of each child, as -Xmx takes it. Default:"
                    + " ESJ_MAX_HEAP, the ceiling of the container image, or 512m.")
    private String jobHeap;

    @Option(order = 280, names = "--job-timeout", paramLabel = "<duration>",
            converter = Numbers.Runtime.class,
            description = "How long each child may run: its --max-runtime, and it is killed"
                    + " 5 s after. Default: 5m.")
    private Duration jobTimeout = ServeConfig.DEFAULT_JOB_TIMEOUT;

    @Option(order = 290, names = "--max-upload", paramLabel = "<bytes>",
            converter = Numbers.ByteCount.class,
            description = "The largest document a call takes, with a k, M or G suffix for"
                    + " multiples of 1024. Default: 32M.")
    private long maxUpload = ServeConfig.DEFAULT_MAX_UPLOAD;

    @Option(order = 295, names = "--max-disk", paramLabel = "<bytes>",
            converter = Numbers.ByteCount.class,
            description = "How many bytes the temporary directory holds at once: request bodies"
                    + " and uploads as they are read, 64M for each running child from its"
                    + " start, answers that wait for their client, the uploads and artefacts"
                    + " kept. A call that does not fit is refused (HTTP 503, Retry-After); the"
                    + " server starts only with the --max-jobs it holds. Default: 448M, within"
                    + " the 512 MiB tmpfs of dist/compose.yaml.")
    private long maxDisk = ServeConfig.DEFAULT_MAX_DISK;

    /**
     * Applies these options to settings.
     *
     * @param config the settings so far
     * @return the settings with these options
     */
    ServeConfig apply(ServeConfig config) {
        String heap = jobHeap != null ? jobHeap
                : config.environment().getOrDefault("ESJ_MAX_HEAP", ServeConfig.DEFAULT_JOB_HEAP);
        if (!HEAP.matcher(heap).matches()) {
            throw CliException.input("--job-heap takes a size as -Xmx does, such as 512m or 1g,"
                    + " not '" + heap + "'");
        }
        if (!"default".equals(limits) && !"large".equals(limits)) {
            throw CliException.input("--limits takes default or large, not '" + limits + "'");
        }
        if (templates != null && !Files.isDirectory(templates)) {
            throw CliException.input("--templates " + templates + " is not a directory");
        }
        for (Path directory : allowDirs) {
            if (!Files.isDirectory(directory)) {
                throw CliException.input("--allow-dir " + directory + " is not a directory");
            }
        }
        for (Path directory : packs) {
            if (!Files.isDirectory(directory)) {
                throw CliException.input("--packs " + directory + " is not a directory");
            }
        }
        // By default no more children than the disk holds, so that the default asks for
        // nothing the server would have to lower with a warning.
        int jobs = maxJobs == null ? ServeConfig.defaultMaxJobs(maxDisk, maxUpload) : maxJobs;
        if (jobs < 1) {
            throw CliException.input("--max-jobs is at least 1");
        }
        int queue = maxQueue == null ? jobs : maxQueue;
        if (queue < 0) {
            throw CliException.input("--max-queue is 0 or more");
        }
        List<Path> absolutePacks = new ArrayList<>();
        packs.forEach(pack -> absolutePacks.add(pack.toAbsolutePath()));
        ServeConfig withJobs = config.withJobs(jobs, queue, queueWait, heap, jobTimeout)
                .withAccess(config.allowedOrigins(), List.copyOf(allowDirs),
                        Optional.ofNullable(templates).map(Path::toAbsolutePath),
                        absolutePacks, config.publicUrl());
        return withJobs.withStorage(maxUpload, config.requestTimeout(), config.ttl(),
                config.maxStored(), config.maxStoredBytes(), config.tempRoot())
                .withDisk(maxDisk)
                .withProcess(config.child(), config.environment(), limits, config.log());
    }
}
