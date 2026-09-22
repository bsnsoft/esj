package de.bsnsoft.esj.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * The output layer of the tool: the one place that writes to the standard streams.
 *
 * <p>Everything the tool produces goes through here, and nothing else in this module
 * refers to {@code System.out}, {@code System.err} or {@code System.in}. That is what
 * lets a test run a whole command in its own process and read back what a user would
 * have seen, and it is what keeps the rule of the idea paper — the document on the
 * standard output, every diagnostic on the standard error — a property of one class
 * rather than a habit spread over nine commands.
 *
 * <p>Text is written as UTF-8 and lines end with a single line feed, on every platform.
 * A document is written as bytes, exactly as the writer of {@code esj-core} produced
 * them: the canonical form ends without a newline and the pretty form ends with one, and
 * this class adds nothing to either.
 *
 * <p>The two streams are buffered differently, because they are read differently. The
 * document is one payload and is flushed when there is no more of it; a diagnostic is a
 * line somebody is waiting for, so it is flushed as it is written. A run that is
 * interrupted — a terminal closed, a container killed — is the run whose diagnostics
 * matter most, and buffering them until the command ends is how they would be lost.
 *
 * <p>A write to the standard output that fails is remembered rather than thrown, and
 * {@link #outputFailed()} is where the caller asks; {@link Main} turns that into one
 * sentence and {@link ExitCode#OUTPUT}. A full disk leaves a truncated document behind,
 * and a successful exit code beside it would tell a script that the bytes it received are
 * the bytes the tool produced. A failure on the error stream is dropped instead: a
 * diagnostic about a diagnostic that could not be written has nowhere to go.
 *
 * <p>A consumer that closed the pipe is not a failure at all. The tool has no answer to
 * give a reader that is gone, so the rest of the output is written nowhere, silently, and
 * the command keeps the verdict it reached. Which of the two a refused write is follows
 * from what the stream is attached to and not from the words the platform wrote into the
 * exception; see {@link OutputSink}.
 */
final class Console {

    private final InputStream in;
    private final OutputStream out;
    private final OutputStream err;
    private final GlobalOptions options;
    private boolean writeFailed;
    private boolean consumerGone;
    private IOException outputCause;

    Console(InputStream in, OutputStream out, OutputStream err, GlobalOptions options) {
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * Returns a console that writes everything to the error stream.
     *
     * <p>It is for the one case where the standard output belongs to something else: a
     * command asked to write a file to the standard output has nowhere to put the lines a
     * person reads, and mixing the two would corrupt the file. The lines then go where
     * every other diagnostic of this tool goes.
     *
     * @return the console
     */
    Console toErrorStream() {
        return new Console(in, err, err, options);
    }

    /** Returns the standard input of the process. */
    InputStream in() {
        return in;
    }

    /** Returns the flags that were given on the command line. */
    GlobalOptions options() {
        return options;
    }

    /** Writes bytes to the standard output, unchanged. */
    void bytes(byte[] content) {
        write(out, content);
    }

    /** Writes text to the standard output, with no line ending of its own. */
    void print(String text) {
        write(out, text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Tells whether a write to the standard output failed, including one a
     * {@link PrintStream} swallowed.
     *
     * <p>The question is about the standard output alone. A diagnostic that could not be
     * written has nowhere to go — that is the reason {@link #write} only remembers a
     * failure on the error stream — and turning it into an exit code and a sentence about
     * the output gives it somewhere to go after all, in the one place where the sentence
     * is false: the output may have been written in full.
     *
     * @return {@code true} if what the caller received is not what the tool produced
     */
    boolean outputFailed() {
        if (writeFailed) {
            return true;
        }
        return out instanceof PrintStream output && output.checkError();
    }

    /**
     * Returns the exception the failed write raised, for {@code --debug}.
     *
     * <p>It is the cause of the failure and not the report of it: the sentence the user
     * reads is {@link Main}'s, in English and without a Java class name, and this is the
     * detail underneath it.
     *
     * @return the exception, or empty where nothing failed or a {@link PrintStream}
     *         swallowed it
     */
    Optional<IOException> outputCause() {
        return Optional.ofNullable(outputCause);
    }

    /** Writes one line to the standard output. */
    void line(String text) {
        print(text + "\n");
    }

    /** Writes one empty line to the standard output. */
    void line() {
        print("\n");
    }

    /**
     * Writes one line to the standard error stream, unprefixed, and flushes it.
     *
     * <p>What is buffered on the standard output is written out first, so that a log that
     * receives both streams reads in the order the tool wrote them: the diagnostic that
     * points at a report stays above the report, and the report that points at the error
     * stream stays below the lines it points at. Where nothing is buffered that costs
     * nothing.
     */
    void diagnostic(String text) {
        flush(out);
        write(err, (text + "\n").getBytes(StandardCharsets.UTF_8));
        flush(err);
    }

    /**
     * Writes one line to the standard error stream and to nothing else, for the deadline
     * of {@code --max-runtime}.
     *
     * <p>{@link #diagnostic(String)} flushes the standard output first, so that a log
     * which receives both streams reads in the order the tool wrote them. At a deadline
     * that ordering costs more than it is worth: the standard output is the stream most
     * likely to be the reason the run is late — a consumer that has stopped reading its
     * pipe blocks the flush for as long as it likes — and a watchdog that waits there ends
     * the process whenever the consumer feels like it rather than when the deadline
     * passed. So this one line goes straight to the error stream, and what is buffered on
     * the standard output is left where it is: the run is being abandoned, and those bytes
     * are not a result.
     *
     * @param text the line, without its line ending
     */
    void deadline(String text) {
        write(err, (text + "\n").getBytes(StandardCharsets.UTF_8));
        flush(err);
    }

    /** Writes a warning to the standard error stream. */
    void warning(String text) {
        diagnostic("warning: " + text);
    }

    /** Writes an error to the standard error stream. */
    void error(String text) {
        diagnostic("error: " + text);
    }

    /**
     * Writes an information line to the standard error stream, whether or not
     * {@code --verbose} was given.
     *
     * <p>It is neither a warning nor a trace: nothing fell short and the exit code does
     * not change, and the line is there because the run put something into its result
     * that the caller did not ask for by name. A convention of a binding table is the
     * case — the syntax requires an element no business term states — and a caller who
     * hears about it only under {@code --verbose} would ship the value without knowing.
     *
     * @param text the line, without its line ending and without a prefix
     */
    void information(String text) {
        diagnostic("info: " + text);
    }

    /** Writes a line to the standard error stream if {@code --verbose} was given. */
    void verbose(String text) {
        if (options.verbose()) {
            information(text);
        }
    }

    /** Returns a writer over the standard output, for the usage text picocli prints. */
    PrintWriter outWriter() {
        return new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
    }

    /** Returns a writer over the standard error stream, for the usage text picocli prints. */
    PrintWriter errWriter() {
        return new PrintWriter(new OutputStreamWriter(err, StandardCharsets.UTF_8), true);
    }

    /**
     * Flushes both output streams, the error stream first. A failure is remembered rather
     * than thrown: this runs after the command has finished, where an exception would
     * replace the verdict the command reached instead of adding to it, and
     * {@link #outputFailed()} is where the caller asks.
     */
    void flush() {
        flush(err);
        flush(out);
    }

    private void flush(OutputStream stream) {
        if (stream == out && stopped()) {
            return;
        }
        try {
            stream.flush();
        } catch (IOException e) {
            failed(stream, e);
        }
    }

    /**
     * Writes to one of the two streams, and remembers what a refused write on the
     * standard output was.
     *
     * <p>Once the standard output has refused a write, nothing more is written to it.
     * Either the reader is gone, and there is nobody to write for, or writing failed, and
     * the next attempt would fail in the same way; in both cases the command carries on
     * doing what it was asked and the answer is given once, at the end.
     */
    private void write(OutputStream stream, byte[] content) {
        if (stream == out && stopped()) {
            return;
        }
        try {
            stream.write(content);
        } catch (IOException e) {
            failed(stream, e);
        }
    }

    /**
     * Records a refused write: nothing at all on the error stream, the reader having left
     * or the writing having failed on the standard output.
     */
    private void failed(OutputStream stream, IOException e) {
        if (stream != out) {
            return;
        }
        if (out instanceof OutputSink sink && sink.consumerCanLeave()) {
            consumerGone = true;
            return;
        }
        writeFailed = true;
        if (outputCause == null) {
            outputCause = e;
        }
    }

    /** Tells whether the standard output has stopped taking bytes, for either reason. */
    private boolean stopped() {
        return consumerGone || writeFailed;
    }
}
