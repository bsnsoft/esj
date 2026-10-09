package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.syntax.SyntaxOptions;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The options that mean the same thing wherever they are written: once for a run of the
 * tool, not once per command.
 *
 * <p>They are held outside the command objects because {@code esj --verbose convert x} and
 * {@code esj convert --verbose x} have to set the same thing, and picocli restores the
 * initial value of an option field when it starts parsing the command that declares it.
 * {@link GlobalFlags} is the mixin that both places share; this is what it writes to.
 *
 * <p>The limits are kept as the profile and the overrides that were asked for, and the
 * configuration is built from them on the first question and then kept: one run has one
 * set of bounds, and a command that asked twice would otherwise be able to receive two
 * answers. The overrides are applied on top of the profile whichever order the two were
 * written in, because {@code --limits large --max-values 1000} and the same line reversed
 * ask for the same thing — a profile with one bound moved.
 */
final class GlobalOptions {

    /**
     * How long a command that reads a document may take where {@code --max-runtime} names
     * no number: the default of the syntax engine, so that one number holds for every
     * command and for every step of {@code esj validate}.
     */
    static final Duration DEFAULT_MAX_RUNTIME = SyntaxOptions.defaults().maxRuntime();

    private boolean verbose;
    private boolean debug;
    private boolean strict;
    private String attachment;
    private Integer attachmentIndex;
    private Importer importer = Importer.DEFAULT;
    private String profile = Bounds.DEFAULT_PROFILE;
    private final Map<Bound, Long> overrides = new EnumMap<>(Bound.class);
    private Bounds bounds;
    private Duration maxRuntime;
    private Duration defaultMaxRuntime = DEFAULT_MAX_RUNTIME;
    private Consumer<Duration> runtimeLimit = duration -> { };
    private Map<String, String> environment = Map.of();

    /** Tells whether the tool was asked to explain what it does on the error stream. */
    boolean verbose() {
        return verbose;
    }

    /** Tells whether the tool was asked to print a stack trace when something fails. */
    boolean debug() {
        return debug;
    }

    /** Turns on {@code --verbose}. A flag is never turned off again once it was given. */
    void enableVerbose() {
        this.verbose = true;
    }

    /** Turns on {@code --debug}. */
    void enableDebug() {
        this.debug = true;
    }

    /**
     * Tells whether this run repairs a document whose bytes are not written in the
     * encoding it declares, or refuses it.
     *
     * @return {@code true} where {@code --strict} was given
     */
    boolean strict() {
        return strict;
    }

    void enableStrict() {
        this.strict = true;
    }

    /**
     * Returns the name of the attachment of a PDF this run reads, where one was named.
     *
     * @return the name as {@code --attachment} spelled it, or {@code null}
     */
    String attachment() {
        return attachment;
    }

    void attachment(String name) {
        this.attachment = name;
    }

    /**
     * Returns the position of the attachment of a PDF this run reads, where one was named.
     *
     * <p>It is the position the tool itself printed, counted from one in the order the
     * container was enumerated, and it is the selector for the case a name cannot settle:
     * two attachments of one name.
     *
     * @return the position as {@code --attachment-index} spelled it, or {@code null}
     */
    Integer attachmentIndex() {
        return attachmentIndex;
    }

    void attachmentIndex(int position) {
        this.attachmentIndex = position;
    }

    /**
     * Returns the reader this run imports XML with.
     *
     * @return the reader {@code --importer} named, or {@link Importer#DEFAULT}
     */
    Importer importer() {
        return importer;
    }

    /**
     * Records the reader {@code --importer} named.
     *
     * @param token the token
     * @throws CliException if it names no reader this version ships
     */
    void importer(String token) {
        this.importer = Importer.ofToken(token);
    }

    /**
     * Records the profile {@code --limits} named. The name is checked when the bounds are
     * built, which is where the answer is needed and where a refusal reads as one line
     * about the request rather than as a parser message.
     */
    void profile(String name) {
        this.profile = name;
        this.bounds = null;
    }

    /** Records one bound a {@code --max-…} switch set. */
    void bound(Bound bound, long value) {
        overrides.put(bound, value);
        this.bounds = null;
    }

    /**
     * Returns the resource bounds of this run.
     *
     * @return the profile with the overrides applied
     * @throws CliException if the profile is not one this version ships, or a value is one
     *                      no configuration can hold
     */
    Bounds bounds() {
        if (bounds == null) {
            Bounds built = Bounds.profile(profile);
            for (Map.Entry<Bound, Long> override : overrides.entrySet()) {
                built = built.with(override.getKey(), override.getValue());
            }
            bounds = built;
        }
        return bounds;
    }

    /**
     * Installs what {@code --max-runtime} starts. {@link Main} passes the watchdog of the
     * run; a caller that installs nothing has a tool whose deadline is the caller's own.
     */
    void onRuntimeLimit(Consumer<Duration> arm) {
        this.runtimeLimit = arm;
    }

    /**
     * Records the run's deadline and starts the watchdog, from the parser, as
     * {@code --max-runtime} is read.
     */
    void maxRuntime(Duration duration) {
        this.maxRuntime = duration;
        runtimeLimit.accept(duration);
    }

    /**
     * Arms the watchdog at the default, for a command that reads a document and was not
     * given a number of its own.
     *
     * <p>{@code --max-runtime} arms the watchdog as it is parsed, which is where the
     * clock should start. A command whose work can run away without ever asking anything
     * about the time — an importer over a document built to be slow, the PDF rendering
     * drawing pages until the heap is gone — needs a deadline even where nobody wrote one,
     * and {@link Main} asks for it here before every command that reads a document. A
     * number the caller gave stands: this does nothing where one was written.
     */
    void armDefaultMaxRuntime() {
        if (maxRuntime == null) {
            maxRuntime(defaultMaxRuntime);
        }
    }

    /**
     * Replaces the default deadline, for a test that cannot wait five minutes to watch it
     * fire. The tool a user runs never calls this.
     *
     * @param duration the deadline of a command that reads a document and names none
     */
    void defaultMaxRuntime(Duration duration) {
        this.defaultMaxRuntime = duration;
    }

    /**
     * Returns how long the run may take.
     *
     * <p>One number serves both halves of the deadline. A command that can spend the time
     * step by step reads it here and enforces it itself, which is how a run that ran out
     * still names the step it ran out in; {@link RuntimeLimit} stands behind that for the
     * commands that do not and for work that has stopped answering.
     *
     * @return the deadline, or {@link #DEFAULT_MAX_RUNTIME} where none was asked for
     */
    Duration maxRuntime() {
        return maxRuntime == null ? defaultMaxRuntime : maxRuntime;
    }

    /**
     * Records the environment of the process the run reads its settings from.
     *
     * <p>It is handed in rather than read by whoever needs it, so that a test runs the tool
     * with the environment it chose and a developer's own settings reach no test.
     *
     * @param environment the variables, by name
     */
    void environment(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
    }

    /**
     * Returns one variable of the environment the run was started with.
     *
     * @param name the name of the variable
     * @return its value, or an empty optional where it is not set
     */
    Optional<String> environment(String name) {
        return Optional.ofNullable(environment.get(name));
    }

    /**
     * Returns the whole environment the run was started with, for {@code esj serve} and
     * {@code esj mcp}, which choose the environment of their children from it.
     *
     * @return the variables, by name
     */
    Map<String, String> environment() {
        return environment;
    }
}
