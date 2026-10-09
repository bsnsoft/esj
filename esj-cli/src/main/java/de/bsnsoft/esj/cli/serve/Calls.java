package de.bsnsoft.esj.cli.serve;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Runs one call of a tool: checks its arguments, finds its document, runs the child, reads
 * what it wrote, and hands the files it wrote to the front door.
 *
 * <p>The three front doors call this one method with what they took from the caller: the
 * name of the tool, its arguments, where the document is ({@link Source}) and how a file
 * reaches the caller ({@link Delivery}). Everything a call can come to is an
 * {@link Outcome}; nothing a child does raises out of here.
 */
final class Calls {

    /** Where the document of a call is. */
    sealed interface Source {
        /** A request body, already read into a file of this process, removed after the call. */
        record Body(Path file) implements Source {
        }

        /** An upload, by its identifier. */
        record Upload(String id) implements Source {
        }

        /** A file named by its path. */
        record File(String path) implements Source {
        }

        /** No document. */
        record None() implements Source {
        }
    }

    /** How a file a call wrote reaches the caller. */
    @FunctionalInterface
    interface Delivery {
        /**
         * Delivers one file.
         *
         * @param output what the file is
         * @param file   where the child wrote it
         * @return the description the result carries, or nothing where the file stays here
         * @throws Refused where it cannot be delivered
         */
        Optional<Jv.Obj> deliver(Tools.Output output, Path file) throws Refused;
    }

    /**
     * How one front door takes a call.
     *
     * @param raw            whether the answer of {@code validate} is the child's report
     *                       itself, passed on as a file ({@link Outcome#raw()})
     * @param completeReport whether a validation whose result had to be cut carries the
     *                       child's complete report as a file beside it
     * @param cancel         the cancellation of the call
     */
    record Options(boolean raw, boolean completeReport, Jobs.Cancel cancel) {

        /** The REST API: the report itself, and no cancellation. */
        static final Options REST = new Options(true, false, Jobs.Cancel.NEVER);

        /**
         * A call over MCP.
         *
         * @param completeReport whether the transport delivers files a call did not ask for
         * @param cancel         its cancellation
         * @return the options
         */
        static Options mcp(boolean completeReport, Jobs.Cancel cancel) {
            return new Options(false, completeReport, cancel);
        }
    }

    /** The seconds after which a call refused while the server shuts down may be tried again. */
    static final long SHUTDOWN_RETRY_AFTER = 5;

    /** The complete report of a validation, where its result had to be cut. */
    static final Tools.Output COMPLETE_REPORT = new Tools.Output("complete-report",
            "validation.json", "application/json");

    /** A call that is refused before or after its child ran. */
    static final class Refused extends Exception {
        private static final long serialVersionUID = 1L;

        private final transient Outcome outcome;

        Refused(Outcome.Status status, String message) {
            super(message);
            this.outcome = Outcome.error(status, -1, message);
        }

        Refused(Outcome.Status status, String message, long retryAfter) {
            super(message);
            this.outcome = Outcome.error(status, -1, message).retryAfter(retryAfter);
        }

        Outcome outcome() {
            return outcome;
        }
    }

    /**
     * Returns the outcome of a call that found no room on the disk ({@code --max-disk}).
     *
     * @param full what did not fit
     * @return the outcome, to be tried again after {@link Disk#RETRY_AFTER} seconds
     */
    static Outcome noRoom(Disk.Full full) {
        return Outcome.error(Outcome.Status.FULL, -1, full.getMessage())
                .retryAfter(Disk.RETRY_AFTER);
    }

    private final ServeConfig config;
    private final Store store;
    private final Jobs jobs;
    private final Tools.Mode mode;
    private final List<Tools.Tool> tools;
    private final List<Path> allowed;

    /**
     * Prepares the calls of one front door.
     *
     * @param config the settings
     * @param store  the uploads and artefacts
     * @param jobs   the children
     * @param mode   the front door
     */
    Calls(ServeConfig config, Store store, Jobs jobs, Tools.Mode mode) {
        this.config = config;
        this.store = store;
        this.jobs = jobs;
        this.mode = mode;
        this.tools = Tools.all(mode, Templates.names(config.templates()));
        this.allowed = realDirectories(config.allowedDirs());
    }

    private static List<Path> realDirectories(List<Path> directories) {
        List<Path> real = new ArrayList<>();
        for (Path directory : directories) {
            try {
                Path resolved = directory.toRealPath();
                if (!Files.isDirectory(resolved)) {
                    throw new IllegalArgumentException("--allow-dir " + directory
                            + " is not a directory");
                }
                real.add(resolved);
            } catch (IOException e) {
                throw new IllegalArgumentException("--allow-dir " + directory
                        + " cannot be read: " + e.getMessage(), e);
            }
        }
        return List.copyOf(real);
    }

    /** Returns the tools of this front door. */
    List<Tools.Tool> tools() {
        return tools;
    }

    /** Tells whether a {@code path} is taken over HTTP. */
    boolean takesPaths() {
        return mode == Tools.Mode.STDIO || !allowed.isEmpty();
    }

    /** Returns the store. */
    Store store() {
        return store;
    }

    /**
     * Runs one call and hands what it came to to the front door while the call still holds
     * its child's place.
     *
     * <p>Everything of a call whose size follows its document — what the child wrote, as it
     * is read, the result built from it, the answer the front door writes from the result —
     * exists only while the call holds the place of a child: {@code answer} turns the
     * outcome into what the front door sends, a {@link Spool} or a file, before the place is
     * given back. So at most {@code --max-jobs} calls hold that much heap at once, however
     * many wait, finish or are being read by slow clients ({@link Capacity}).
     *
     * @param <T>       what the front door sends
     * @param tool      the tool
     * @param given     the arguments of the tool, without those of the front door
     * @param source    where the document is
     * @param delivery  how the files it writes reach the caller
     * @param options   how the front door takes it
     * @param answer    turns the outcome into what the front door sends; it runs once, while
     *                  the call holds its child's place where it ran a child
     * @return what {@code answer} made of the outcome
     */
    <T> T call(Tools.Tool tool, Jv.Obj given, Source source, Delivery delivery,
               Options options, Function<Outcome, T> answer) {
        Tools.Arguments arguments;
        try {
            arguments = check(tool, given);
        } catch (Refused refused) {
            return answer.apply(refused.outcome());
        }
        if ("upload".equals(tool.name())) {
            return answer.apply(upload(arguments));
        }
        List<Path> temporary = new ArrayList<>();
        if (source instanceof Source.Body body) {
            temporary.add(body.file());
        }
        try {
            Path stdin = document(tool, source, temporary);
            return run(tool, arguments, stdin, delivery, options, answer);
        } catch (Refused refused) {
            return answer.apply(refused.outcome());
        } finally {
            temporary.forEach(store.disk()::delete);
        }
    }

    private <T> T run(Tools.Tool tool, Tools.Arguments arguments, Path stdin,
                      Delivery delivery, Options options, Function<Outcome, T> answer)
            throws Refused {
        Tools.Plan plan = Tools.plan(tool, arguments, config.templates(), config.packs());
        Jobs.Slot slot;
        try {
            slot = jobs.acquire(options.cancel());
        } catch (Jobs.Busy busy) {
            if (jobs.closed()) {
                return answer.apply(Outcome.error(Outcome.Status.BUSY, -1,
                        "the server is shutting down").retryAfter(SHUTDOWN_RETRY_AFTER));
            }
            return answer.apply(Outcome.error(Outcome.Status.BUSY, -1, "the server is busy: "
                    + busy.getMessage()).retryAfter(Math.max(1, config.queueWait().toSeconds())));
        } catch (Jobs.Cancelled cancelled) {
            return answer.apply(cancelled());
        } catch (Disk.Full full) {
            // Before the child starts: what it may write does not fit beside what is held.
            return answer.apply(noRoom(full));
        }
        try (slot) {
            Jobs.Result result = jobs.run(slot, plan.arguments(), stdin, options.cancel());
            config.log().accept("  child " + tool.name() + " exit=" + result.exitCode()
                    + (result.wasKilled() ? " killed=" + result.killed() : "") + " "
                    + result.millis() + "ms");
            if (result.wasKilled()) {
                return answer.apply(killed(result));
            }
            if (!answers(tool, result.exitCode())) {
                return answer.apply(failed(result));
            }
            Outcome outcome = result(tool, arguments, plan, slot.directory(), result);
            outcome = deliver(outcome, plan, slot.directory(), delivery);
            if ("validate".equals(tool.name())) {
                if (options.raw()) {
                    outcome = outcome.withRaw(keep(result.stdout()));
                } else if (options.completeReport()) {
                    outcome = completeReport(outcome, result.stdout(), delivery);
                }
            }
            return answer.apply(outcome);
        }
    }

    private static Outcome cancelled() {
        return Outcome.error(Outcome.Status.CANCELLED, -1, "the call was cancelled");
    }

    /**
     * Moves the standard output of a child out of its working directory, for the caller; it
     * keeps the bytes it was counted with ({@link Disk#move}).
     */
    private Path keep(Path stdout) {
        try {
            Path kept = store.disk().newFile("answer-", ".json");
            store.disk().move(stdout, kept);
            return kept;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Delivers the complete report of a validation whose result had to be cut, where the
     * delivery takes it. A store with no room for it costs the file, not the call.
     */
    private Outcome completeReport(Outcome outcome, Path stdout, Delivery delivery) {
        if (!(outcome.structured().get("complete").orElse(null) instanceof Jv.Bool complete)
                || complete.value()) {
            return outcome;
        }
        Optional<Jv.Obj> description;
        try {
            description = delivery.deliver(COMPLETE_REPORT, stdout);
        } catch (Refused refused) {
            return outcome.withText(List.of("(the complete report could not be kept: "
                    + refused.getMessage() + ")"));
        }
        if (description.isEmpty()) {
            return outcome;
        }
        List<Jv> files = new ArrayList<>();
        if (outcome.structured().get("files").orElse(null) instanceof Jv.Arr earlier) {
            files.addAll(earlier.items());
        }
        files.add(description.get());
        return outcome.with("files", Jv.array(files)).withText(List.of(COMPLETE_REPORT.role()
                + ": " + where(description.get())));
    }

    /**
     * Returns the outcome of a child that was killed from outside.
     *
     * @param result what the child left behind
     * @return the outcome: no verdict of any kind
     */
    static Outcome killed(Jobs.Result result) {
        if ("cancelled".equals(result.killed())) {
            return cancelled();
        }
        if ("shutdown".equals(result.killed())) {
            return Outcome.error(Outcome.Status.BUSY, -1, "the server is shutting down")
                    .retryAfter(SHUTDOWN_RETRY_AFTER);
        }
        if ("disk".equals(result.killed())) {
            return Outcome.error(Outcome.Status.FULL, -1, "the temporary directory took no"
                    + " more of what the child wrote, so there is no verdict on the document; try"
                    + " again later").retryAfter(Disk.RETRY_AFTER);
        }
        String message = switch (result.killed()) {
            case "timeout" -> "the child did not finish within --job-timeout and was stopped,"
                    + " so there is no verdict on the document";
            case "output" -> "the child wrote more than " + Jobs.MAX_WRITTEN + " bytes, its"
                    + " output and its files together, and was stopped, so there is no verdict on"
                    + " the document";
            default -> "the child was stopped (" + result.killed() + ")";
        };
        return Outcome.error(Outcome.Status.LIMIT, -1, message);
    }

    private static Outcome failed(Jobs.Result result) {
        Outcome.Status status = Outcome.Status.ofExitCode(result.exitCode());
        String stderr = Results.safe(result.stderr().strip());
        List<String> lines = stderr.lines().limit(4).toList();
        String message = lines.isEmpty() ? "the child left with exit code " + result.exitCode()
                : Results.cut(String.join("\n", lines), 1000);
        if (status == Outcome.Status.LIMIT && result.exitCode() == 3) {
            message = "the child ran out of its heap of --job-heap and was ended, so there is"
                    + " no verdict on the document";
        }
        return Outcome.error(status, result.exitCode(), message);
    }

    /** Tells whether an exit code is an answer of this tool rather than a failure. */
    private static boolean answers(Tools.Tool tool, int exitCode) {
        return switch (tool.name()) {
            case "validate", "inspect" -> exitCode == 0 || exitCode == 1 || exitCode == 9;
            case "convert" -> exitCode == 0 || exitCode == 8;
            default -> exitCode == 0;
        };
    }

    private Outcome result(Tools.Tool tool, Tools.Arguments arguments, Tools.Plan plan,
                           Path directory, Jobs.Result result) throws Refused {
        int exit = result.exitCode();
        try {
            return switch (tool.name()) {
                case "validate" -> {
                    try (InputStream in = stream(result.stdout())) {
                        yield Results.validate(in, exit);
                    }
                }
                case "summary" -> {
                    try (InputStream in = stream(result.stdout())) {
                        yield Results.summary(in);
                    }
                }
                case "get" -> {
                    try (InputStream in = stream(result.stdout())) {
                        yield Results.get(in, arguments.strings("paths"));
                    }
                }
                case "convert" -> {
                    Path file = directory.resolve(plan.outputs().get(0).file());
                    Optional<Jv> inline = Optional.empty();
                    if (exit == 0 && Files.exists(file) && size(file) <= Results.INLINE) {
                        byte[] bytes = read(file);
                        inline = Optional.of("esj".equals(arguments.string("to"))
                                ? Jv.parse(bytes, Results.INLINE)
                                : Jv.of(new String(bytes, StandardCharsets.UTF_8)));
                    }
                    try (InputStream in = stream(result.stdout())) {
                        yield Results.convert(in, exit, inline);
                    }
                }
                case "render" -> Results.render(arguments.string("format"),
                        !"html".equals(arguments.string("format"))
                                && "cii".equals(arguments.string("embed")));
                case "extract" -> {
                    if (arguments.flag("list")) {
                        yield Results.attachments(Results.page(result.stdout()));
                    }
                    Path file = directory.resolve("invoice.xml");
                    yield Results.extracted(Files.exists(file) && size(file) <= Results.INLINE
                            ? Optional.of(new String(read(file), StandardCharsets.UTF_8))
                            : Optional.empty());
                }
                case "inspect" -> Results.inspect(Results.page(result.stdout()), exit);
                default -> throw new IllegalStateException(tool.name());
            };
        } catch (IOException | Jv.JsonException e) {
            // The tool's own output is always one JSON value; anything else is a defect.
            throw new Refused(Outcome.Status.INTERNAL, "the child wrote no JSON report");
        }
    }

    private static InputStream stream(Path file) throws IOException {
        return new java.io.BufferedInputStream(Files.newInputStream(file), 64 * 1024);
    }

    /** Hands every file the child wrote to the front door and names them in the result. */
    private Outcome deliver(Outcome outcome, Tools.Plan plan, Path directory, Delivery delivery)
            throws Refused {
        List<Jv> files = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        boolean inline = outcome.structured().get("document").isPresent()
                || outcome.structured().get("xml").isPresent();
        for (Tools.Output output : plan.outputs()) {
            Path file = directory.resolve(output.file());
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            Optional<Jv.Obj> description = delivery.deliver(output, file);
            if (description.isPresent()) {
                files.add(description.get());
                lines.add(output.role() + ": " + where(description.get()));
            } else if (!inline) {
                throw new Refused(Outcome.Status.ARGUMENTS, "this call wrote a file ("
                        + output.role() + ", " + size(file) + " bytes) that the answer does not"
                        + " carry: name where to write it with " + Tools.OUT);
            }
        }
        if (files.isEmpty()) {
            return outcome;
        }
        return outcome.with("files", Jv.array(files)).withText(lines);
    }

    /** Returns where a delivered file is: its URL, or its path. */
    private static String where(Jv.Obj description) {
        return description.string("url").or(() -> description.string("path")).orElse("");
    }

    /** Finds the document of a call and returns the file the child reads it from. */
    private Path document(Tools.Tool tool, Source source, List<Path> temporary)
            throws Refused {
        if (!tool.document()) {
            return null;
        }
        if (source instanceof Source.Body body) {
            return body.file();
        }
        if (source instanceof Source.Upload upload) {
            // An upload, or a file a call wrote: a hybrid PDF that render wrote is validated
            // by its id as well.
            return store.get(upload.id())
                    .orElseThrow(() -> new Refused(Outcome.Status.NOT_FOUND, "no upload or"
                            + " artefact " + Results.safe(Results.cut(upload.id(), 40))
                            + " is kept here; both are kept for " + config.ttl().toMinutes()
                            + " minutes"))
                    .file();
        }
        if (source instanceof Source.File file) {
            return file(file.path(), temporary);
        }
        throw new Refused(Outcome.Status.ARGUMENTS, mode == Tools.Mode.STDIO
                ? "this tool reads a document: name it with " + Tools.PATH
                : "this tool reads a document: send it as the request body, or name an upload"
                        + " with " + Tools.DOCUMENT
                        + (allowed.isEmpty() ? "" : " or a file with " + Tools.PATH));
    }

    /** Resolves a {@code path}: confined to the allowed directories where there are any. */
    private Path file(String given, List<Path> temporary) throws Refused {
        if (mode == Tools.Mode.HTTP && allowed.isEmpty()) {
            throw new Refused(Outcome.Status.ARGUMENTS, Tools.PATH + " is not taken here: the"
                    + " server was started without --allow-dir");
        }
        Path real = confined(given, true);
        long length = size(real);
        if (length > config.maxUpload()) {
            throw new Refused(Outcome.Status.TOO_LARGE, "the file is " + length + " bytes, more"
                    + " than the " + config.maxUpload() + " this server takes (--max-upload)");
        }
        if (allowed.isEmpty()) {
            return real;
        }
        // A file with a second name may have that name outside every allowed directory: a
        // hard link is the same file under two names, and its real path is the one it was
        // asked by. Such a file is not read, before and after the copy.
        Object identity = oneName(given, real, null);
        // A copy, read through a final component that may not be a link: the child reads
        // the bytes that were checked to lie inside, whatever happens to the name later.
        Path copy;
        try {
            copy = store.disk().newFile("path-", ".in");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        temporary.add(copy);
        try (InputStream in = Files.newInputStream(real, LinkOption.NOFOLLOW_LINKS);
             OutputStream out = store.disk().write(copy)) {
            byte[] buffer = new byte[64 * 1024];
            long copied = 0;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                copied += read;
                if (copied > config.maxUpload()) {
                    throw new Refused(Outcome.Status.TOO_LARGE, "the file grew past"
                            + " --max-upload while it was read");
                }
                out.write(buffer, 0, read);
            }
        } catch (Disk.Full full) {
            throw new Refused(Outcome.Status.FULL, full.getMessage(), Disk.RETRY_AFTER);
        } catch (IOException e) {
            throw new Refused(Outcome.Status.NOT_FOUND, "the file cannot be read: "
                    + e.getMessage());
        }
        oneName(given, real, identity);
        return copy;
    }

    /**
     * Refuses a file that has more than one name, and a file that is no longer the one it
     * was.
     *
     * @param given    the path the caller wrote, for the message
     * @param real     the real path of the file
     * @param identity the identity the file had, or {@code null} the first time
     * @return its identity: its device and inode
     */
    private static Object oneName(String given, Path real, Object identity) throws Refused {
        try {
            int links = ((Number) Files.getAttribute(real, "unix:nlink",
                    LinkOption.NOFOLLOW_LINKS)).intValue();
            Object key = Files.readAttributes(real,
                    java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).fileKey();
            if (links > 1) {
                throw new Refused(Outcome.Status.FORBIDDEN, given + " has " + links + " names"
                        + " (hard links), and another may lie outside the directories this"
                        + " server was allowed to read (--allow-dir); a file with one name is"
                        + " read");
            }
            if (identity != null && !identity.equals(key)) {
                throw new Refused(Outcome.Status.FORBIDDEN, given + " was replaced while it"
                        + " was read");
            }
            return key == null ? "" : key;
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            throw new Refused(Outcome.Status.FORBIDDEN, "this system cannot tell whether "
                    + given + " has another name, so no file is read by path here");
        } catch (IOException e) {
            throw new Refused(Outcome.Status.NOT_FOUND, given + " cannot be read: "
                    + e.getMessage());
        }
    }

    /**
     * Resolves a path against the allowed directories: its real path, every link followed,
     * has to lie inside one of them.
     *
     * @param given  the path the caller wrote; a relative one is taken from the first
     *               allowed directory, or from the working directory where there is none
     * @param exists whether the file has to exist
     * @return the real path, or for a file that does not exist yet, the real path of its
     *         directory with its name
     */
    Path confined(String given, boolean exists) throws Refused {
        Path path;
        try {
            path = Path.of(given);
        } catch (InvalidPathException e) {
            throw new Refused(Outcome.Status.ARGUMENTS, "not a usable path: " + e.getReason());
        }
        if (!path.isAbsolute() && !allowed.isEmpty()) {
            path = allowed.get(0).resolve(path);
        }
        Path real;
        try {
            if (exists) {
                real = path.toRealPath();
                if (!Files.isRegularFile(real)) {
                    throw new Refused(Outcome.Status.ARGUMENTS, given + " is not a file");
                }
            } else {
                Path parent = path.toAbsolutePath().getParent();
                if (parent == null || path.getFileName() == null) {
                    throw new Refused(Outcome.Status.ARGUMENTS, given + " names no file");
                }
                real = parent.toRealPath().resolve(path.getFileName());
                if (Files.isSymbolicLink(real) || Files.isDirectory(real)) {
                    throw new Refused(Outcome.Status.ARGUMENTS, given + " is a link or a"
                            + " directory, and a result is written to a file only");
                }
            }
        } catch (NoSuchFileException e) {
            throw new Refused(Outcome.Status.NOT_FOUND, "no such file: " + given);
        } catch (IOException e) {
            throw new Refused(Outcome.Status.NOT_FOUND, given + " cannot be read: "
                    + e.getMessage());
        }
        if (!allowed.isEmpty() && allowed.stream().noneMatch(real::startsWith)) {
            throw new Refused(Outcome.Status.FORBIDDEN, given + " lies outside the directories"
                    + " this server was allowed to read (--allow-dir)");
        }
        return real;
    }

    /**
     * The most characters of the {@code content_base64} of an upload: the base64 of
     * {@code --max-upload} bytes.
     *
     * @param maxUpload the largest upload, in bytes
     * @return the most characters
     */
    static long base64Length(long maxUpload) {
        return (maxUpload + 2) / 3 * 4 + 4;
    }

    private Outcome upload(Tools.Arguments arguments) {
        Jv content = arguments.values().get("content_base64");
        long most = base64Length(config.maxUpload());
        long length = content instanceof Jv.Blob blob ? blob.characters()
                : ((Jv.Str) content).value().length();
        if (length > most || (content instanceof Jv.Blob blob && !blob.complete())) {
            return Outcome.error(Outcome.Status.TOO_LARGE, -1, "the document is larger than the "
                    + config.maxUpload() + " bytes this server takes (--max-upload)");
        }
        // The content is decoded as it is stored, from the file it was written to while the
        // message was read, or from the string of a message that carried it.
        InputStream encoded;
        try {
            encoded = content instanceof Jv.Blob blob ? Files.newInputStream(blob.file())
                    : new java.io.ByteArrayInputStream(((Jv.Str) content).value()
                            .getBytes(StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (InputStream decoded = new NotBase64(Base64.getMimeDecoder().wrap(encoded))) {
            Store.Entry entry = store.upload(decoded, arguments.string("name"));
            return uploaded(entry);
        } catch (NotBase64.Failure e) {
            return Outcome.error(Outcome.Status.ARGUMENTS, -1, "content_base64 is not base64: "
                    + e.getMessage());
        } catch (Store.TooLarge e) {
            return Outcome.error(Outcome.Status.TOO_LARGE, -1, e.getMessage());
        } catch (Store.Full e) {
            return Outcome.error(Outcome.Status.FULL, -1, e.getMessage())
                    .retryAfter(Math.max(1, config.ttl().toSeconds()));
        } catch (Disk.Full full) {
            return noRoom(full);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tells a defect of the base64 apart from a failure of the file it is read from. */
    private static final class NotBase64 extends java.io.FilterInputStream {

        /** The base64 was not base64. */
        static final class Failure extends IOException {
            private static final long serialVersionUID = 1L;

            Failure(String message) {
                super(message);
            }
        }

        NotBase64(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            try {
                return super.read();
            } catch (IOException e) {
                throw new Failure(e.getMessage());
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            try {
                return super.read(buffer, offset, length);
            } catch (IOException e) {
                throw new Failure(e.getMessage());
            }
        }
    }

    /**
     * Returns the result of a stored upload.
     *
     * @param entry the upload
     * @return the outcome, naming its identifier
     */
    static Outcome uploaded(Store.Entry entry) {
        Jv.Obj result = Jv.object()
                .put("id", entry.id())
                .put("sha256", entry.sha256())
                .put("bytes", entry.bytes())
                .put("expires", entry.expires().toString())
                .build();
        return new Outcome(Outcome.Status.OK, -1, "Stored " + entry.bytes() + " bytes as document "
                + entry.id() + " (SHA-256 " + entry.sha256() + "), kept until "
                + entry.expires() + ".", result, Optional.empty(), Optional.empty());
    }

    /**
     * Checks the arguments of a call against the parameters of its tool and fills in the
     * defaults.
     *
     * @param tool  the tool
     * @param given the arguments
     * @return the checked arguments
     * @throws Refused where an argument is unknown, of the wrong type or out of range
     */
    static Tools.Arguments check(Tools.Tool tool, Jv.Obj given) throws Refused {
        Map<String, Jv> values = new LinkedHashMap<>();
        for (Map.Entry<String, Jv> member : given.members().entrySet()) {
            String name = member.getKey();
            Tools.Param param = tool.param(name).orElseThrow(() -> new Refused(
                    Outcome.Status.ARGUMENTS, tool.name() + " takes no parameter " + name
                            + "; it takes " + names(tool)));
            Jv value = member.getValue();
            if (value instanceof Jv.Null) {
                continue;
            }
            values.put(name, checked(tool, param, value));
        }
        for (Tools.Param param : tool.params()) {
            if (!values.containsKey(param.name())) {
                if (param.required()) {
                    throw new Refused(Outcome.Status.ARGUMENTS, tool.name() + " needs "
                            + param.name());
                }
                param.fallback().ifPresent(fallback -> values.put(param.name(),
                        param.type() == Tools.Type.BOOLEAN ? Jv.of(Boolean.parseBoolean(fallback))
                                : Jv.of(fallback)));
            }
        }
        return new Tools.Arguments(values);
    }

    private static Jv checked(Tools.Tool tool, Tools.Param param, Jv value) throws Refused {
        String where = tool.name() + " " + param.name();
        switch (param.type()) {
            case BOOLEAN -> {
                if (!(value instanceof Jv.Bool)) {
                    throw new Refused(Outcome.Status.ARGUMENTS, where + " is true or false");
                }
                return value;
            }
            case INTEGER -> {
                if (!(value instanceof Jv.Num)) {
                    throw new Refused(Outcome.Status.ARGUMENTS, where + " is a number");
                }
                return value;
            }
            case STRING -> {
                if ("content_base64".equals(param.name()) && value instanceof Jv.Blob) {
                    return value;
                }
                if (!(value instanceof Jv.Str str)) {
                    throw new Refused(Outcome.Status.ARGUMENTS, where + " is a string");
                }
                text(where, param, str.value());
                return value;
            }
            case STRINGS -> {
                List<Jv> items = new ArrayList<>();
                if (value instanceof Jv.Str str) {
                    for (String part : str.value().split(",")) {
                        if (!part.isBlank()) {
                            items.add(Jv.of(part.strip()));
                        }
                    }
                } else if (value instanceof Jv.Arr array) {
                    items.addAll(array.items());
                } else {
                    throw new Refused(Outcome.Status.ARGUMENTS, where + " is a list of strings");
                }
                if ("paths".equals(param.name())
                        && (items.isEmpty() || items.size() > Tools.MAX_PATHS)) {
                    throw new Refused(Outcome.Status.ARGUMENTS, where + " takes 1 to "
                            + Tools.MAX_PATHS + " paths");
                }
                for (Jv item : items) {
                    if (!(item instanceof Jv.Str str)) {
                        throw new Refused(Outcome.Status.ARGUMENTS, where
                                + " is a list of strings");
                    }
                    text(where, param, str.value());
                    if ("paths".equals(param.name())
                            && !Tools.SEMANTIC_PATH.matcher(str.value()).matches()) {
                        throw new Refused(Outcome.Status.ARGUMENTS, where + ": '"
                                + Results.safe(Results.cut(str.value(), 80)) + "' is not a"
                                + " semantic path such as /BT-1 or /BG-25/*/BT-131");
                    }
                }
                return Jv.array(items);
            }
            default -> throw new IllegalStateException(param.type().name());
        }
    }

    private static void text(String where, Tools.Param param, String value) throws Refused {
        if (!param.choices().isEmpty() && !param.choices().contains(value)) {
            throw new Refused(Outcome.Status.ARGUMENTS, where + " takes "
                    + String.join(", ", param.choices()) + ", not '"
                    + Results.safe(Results.cut(value, 80)) + "'");
        }
        if (!"content_base64".equals(param.name())
                && (value.length() > 4096 || value.codePoints().anyMatch(c -> c < 0x20))) {
            throw new Refused(Outcome.Status.ARGUMENTS, where + " is too long or carries a"
                    + " control character");
        }
    }

    private static String names(Tools.Tool tool) {
        List<String> names = new ArrayList<>();
        tool.params().forEach(param -> names.add(param.name()));
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the description of a file in a result.
     *
     * @param output what it is
     * @param name   the name it is offered under
     * @param bytes  its length
     * @param sha256 its digest
     * @return the description, without where it is
     */
    static Jv.Builder describe(Tools.Output output, String name, long bytes, String sha256) {
        return Jv.object()
                .put("role", output.role())
                .put("name", name)
                .put("mediaType", output.mediaType())
                .put("bytes", bytes)
                .put("sha256", sha256);
    }

    /**
     * The delivery of the standard streams: a file is copied to the {@code out} the caller
     * named, or stays here where none was named.
     *
     * @param out where to write the file, if anywhere
     * @return the delivery
     */
    Delivery toPath(Optional<String> out) {
        return (output, file) -> {
            if (out.isEmpty()) {
                return Optional.empty();
            }
            Path target = confined(out.get(), false);
            try {
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return Optional.of(describe(output, target.getFileName().toString(),
                        Files.size(target), Store.sha256(target))
                        .put("path", target.toString()).build());
            } catch (IOException e) {
                throw new Refused(Outcome.Status.INTERNAL, "cannot write " + out.get() + ": "
                        + e.getMessage());
            }
        };
    }

    /**
     * The delivery of HTTP: a file becomes an artefact, downloaded from its URL.
     *
     * @param base the URL the server is reached at, without a trailing slash, where it is
     *             known; without it, the URL is the path on this server, which a client
     *             resolves against the URL it reached the server at
     * @return the delivery
     */
    Delivery toStore(Optional<String> base) {
        return (output, file) -> {
            try {
                Store.Entry entry = store.artifact(file, output.file(), output.mediaType());
                String path = "/api/artifacts/" + entry.id();
                return Optional.of(describe(output, entry.name(), entry.bytes(), entry.sha256())
                        .put("url", base.map(known -> known + path).orElse(path))
                        .put("expires", entry.expires().toString())
                        .build());
            } catch (Store.Full e) {
                throw new Refused(Outcome.Status.FULL, e.getMessage(),
                        Math.max(1, config.ttl().toSeconds()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /** The names of the parameters a front door adds. */
    static Set<String> frontDoor() {
        return Set.of(Tools.DOCUMENT, Tools.PATH, Tools.OUT);
    }
}
