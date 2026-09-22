package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.EsjException;
import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.xr.XrException;
import de.bsnsoft.esj.xr.XrLimitException;
import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * The entry point of the {@code esj} command line tool.
 *
 * <p>The tool owns no knowledge about invoices. It reads bytes, recognizes which of the
 * three syntaxes they are written in, hands them to {@code esj-core} or to {@code esj-xr},
 * and writes back what those return — a document on the standard output, everything else
 * on the standard error stream. Every rule about paths, values, canonical bytes,
 * cardinalities and syntax bindings lives in the libraries, and a change to one of those
 * rules is a change there and not here.
 *
 * <p>The process boundary is here too. Nothing a command can raise leaves this class:
 * an exception becomes one line and an exit code of {@link ExitCode}, an {@code Error}
 * becomes {@link ExitCode#INTERNAL} — or {@link ExitCode#LIMIT} where it is the heap or
 * the stack of this run running out, which is a resource of the run and not a defect of
 * the tool — rather than the status the virtual machine would pick, and an output that
 * could not be written in full becomes {@link ExitCode#OUTPUT} rather than a silent
 * truncation with a successful exit code. A consumer that closed the
 * pipe is not such a failure and does not change the code the command reached; see
 * {@link Console}.
 *
 * <p>The diagnostics of the PDF library are routed here too, before a command can open a
 * PDF; see {@link PdfBoxLogging}. A library does not get to write onto the error stream of
 * this process in the language of the machine it happens to run on.
 *
 * <p>The deadline of {@code --max-runtime} is here as well, because the one thing it must
 * be able to do is end a process that is no longer able to end itself; see
 * {@link RuntimeLimit}. It is armed by the parser, cancelled when the run is over, and
 * what ends the process is passed in, so that a test can watch it fire without taking the
 * test runner with it.
 *
 * <p>{@link #run(String[], InputStream, OutputStream, OutputStream)} is the whole tool
 * with its streams passed in. That is what makes a test of the command line an ordinary
 * unit test: it runs the same code a user runs, in the same process, and reads back the
 * bytes, the diagnostics and the exit code. {@link #main(String[])} is that method with
 * the streams of the process and a call to {@code System.exit}.
 */
public final class Main {

    /** The buffer each output stream of the process is given, in bytes. */
    private static final int BUFFER = 8192;

    private Main() {
        throw new AssertionError("no instances");
    }

    /**
     * Runs the tool and ends the process with its exit code.
     *
     * <p>The two output streams are the file descriptors of the process rather than
     * {@link System#out} and {@link System#err}. A {@link java.io.PrintStream} swallows
     * every {@link java.io.IOException} and records only that one happened, which leaves
     * the tool unable to tell a full disk from a reader that stopped reading — and those
     * two deserve opposite answers. The standard output is wrapped in an
     * {@link OutputSink}, which is where that question is decided; the error stream needs
     * no such wrapper, because a diagnostic that could not be written is dropped either
     * way.
     *
     * <p>The process writes English, whatever the machine is configured for. The schema
     * validator of the platform writes its messages in the default locale and offers no
     * supported way to be asked for another — the property for it is accepted and
     * ignored — so a report of this tool would otherwise carry a German or a Japanese
     * sentence beside its English rows, and two machines would produce two different
     * reports of the same document. Fixing it belongs to the process rather than to the
     * libraries: it is the whole application's answer, and a caller that embeds
     * {@link #run(String[], InputStream, OutputStream, OutputStream)} keeps the locale
     * it chose.
     *
     * @param args the command line
     */
    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        System.exit(run(args, System.in, OutputSink.of(FileDescriptor.out, BUFFER),
                new BufferedOutputStream(new FileOutputStream(FileDescriptor.err), BUFFER)));
    }

    /**
     * Runs the tool over the given streams and returns its exit code.
     *
     * <p>No stream is closed: they belong to the caller. Both output streams are flushed
     * before this method returns.
     *
     * @param args the command line
     * @param in   the stream a file name of {@code -} reads from
     * @param out  the stream a document is written to
     * @param err  the stream diagnostics, warnings and errors are written to
     * @return one of the codes of {@link ExitCode}
     */
    public static int run(String[] args, InputStream in, OutputStream out, OutputStream err) {
        return run(args, in, out, err, code -> Runtime.getRuntime().halt(code));
    }

    /**
     * Runs the tool over the given streams and with the given way of ending the process.
     *
     * <p>{@code halt} is what {@code --max-runtime} calls when the deadline passes. The
     * process has to end there and then, so the tool that a user runs passes
     * {@link Runtime#halt(int)}; a test passes something that records the code instead,
     * which is the only way to watch a deadline fire without ending the run that is
     * watching.
     *
     * @param args the command line
     * @param in   the stream a file name of {@code -} reads from
     * @param out  the stream a document is written to
     * @param err  the stream diagnostics, warnings and errors are written to
     * @param halt what ends the process when the deadline passes
     * @return one of the codes of {@link ExitCode}
     */
    static int run(String[] args, InputStream in, OutputStream out, OutputStream err,
                   IntConsumer halt) {
        GlobalOptions options = new GlobalOptions();
        Console console = new Console(in, out, err, options);
        // Before anything opens a PDF: the diagnostics of the PDF library belong to this
        // tool's output and not to whatever the virtual machine was configured with.
        PdfBoxLogging.route(console);
        picocli.CommandLine command = new picocli.CommandLine(new EsjCommand(console))
                .addSubcommand(new ConvertCommand(console))
                .addSubcommand(new UpgradeCommand(console))
                .addSubcommand(new ValidateCommand(console))
                .addSubcommand(new RenderCommand(console))
                .addSubcommand(new EmbedCommand(console))
                .addSubcommand(new InspectCommand(console))
                .addSubcommand(new ExtractCommand(console))
                .addSubcommand(new GetCommand(console))
                .addSubcommand(new ListCommand(console))
                .addSubcommand(new DiffCommand(console))
                .addSubcommand(new CanonicalizeCommand(console));
        command.setOut(console.outWriter());
        command.setErr(console.errWriter());
        command.setExecutionExceptionHandler(
                (exception, line, parseResult) -> report(exception, console));
        command.setParameterExceptionHandler((exception, unused) -> usage(exception, console));
        RuntimeLimit deadline = new RuntimeLimit(console, halt);
        options.onRuntimeLimit(deadline::arm);
        long started = System.nanoTime();
        try {
            int code = command.execute(args);
            console.verbose("finished in " + (System.nanoTime() - started) / 1_000_000 + " ms");
            console.flush();
            return console.outputFailed() ? outputFailure(console) : code;
        } catch (OutOfMemoryError error) {
            return outOfMemory(error, console);
        } catch (StackOverflowError error) {
            return outOfStack(error, console);
        } catch (Throwable throwable) {
            // picocli hands an Exception to the handler above; an Error walks past it,
            // and the default handler of the virtual machine would then end the process
            // with status 1, which this tool has promised means that a validation found
            // an error. Catching Throwable is what a process boundary is for.
            return internal(throwable, console);
        } finally {
            deadline.cancel();
            console.flush();
            PdfBoxLogging.route(null);
        }
    }

    /**
     * Reports that the caller did not receive everything the command wrote.
     *
     * <p>This is the only voice for a failed write. {@link Console} remembers the
     * refusal and carries on; saying it there as well would report one event twice, and
     * the first of the two sentences would be the exception in Java's words, which is
     * what {@code --debug} is for.
     */
    private static int outputFailure(Console console) {
        console.error("writing the output failed, so it is incomplete");
        console.outputCause().ifPresent(cause -> trace(cause, console));
        return ExitCode.OUTPUT;
    }

    /**
     * Reports that the heap of this run ran out.
     *
     * <p>It is a resource of this run running out and not a defect of the tool, so it is
     * {@link ExitCode#LIMIT} and not {@link ExitCode#INTERNAL}: the ceiling that was
     * reached was chosen by whoever started the process, the document is not thereby
     * unreadable, and a caller told that the tool has a defect would be sent after the one
     * thing that cannot help. The line names the ceiling, so that the caller can see at
     * once whose number it is.
     *
     * <p>This is the second-best answer and the documentation says so. Recovering from an
     * {@code OutOfMemoryError} inside the process that ran out is unreliable — the report
     * below allocates, and so may fail — which is why {@code -XX:+ExitOnOutOfMemoryError}
     * is on every command line the deployment documentation prescribes and why the line
     * asks for it. It is here for the run that was started without it, which would
     * otherwise be told that a heap ceiling of its own choosing is a bug in this tool.
     */
    private static int outOfMemory(OutOfMemoryError error, Console console) {
        console.error("out of memory: this run has a heap ceiling of "
                + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MiB and did not"
                + " finish inside it, so there is no verdict on the document; give the"
                + " process a larger -Xmx or this run smaller limits, and put"
                + " -XX:+ExitOnOutOfMemoryError on the command line, which ends the process"
                + " at once instead of leaving the recovery to a tool that may not survive"
                + " it");
        trace(error, console);
        return ExitCode.LIMIT;
    }

    /**
     * Reports that the stack of this run ran out.
     *
     * <p>Same reasoning as {@link #outOfMemory}: a depth that outgrew the stack this
     * thread was given is a resource of this run, not a defect of the tool, so the run
     * reached no verdict about the document.
     */
    private static int outOfStack(StackOverflowError error, Console console) {
        console.error("out of stack: this run nested deeper than the stack of its thread"
                + " allows, so there is no verdict on the document; give the process a"
                + " larger -Xss, or this run smaller limits");
        trace(error, console);
        return ExitCode.LIMIT;
    }

    /** Reports a throwable no command turned into an answer, without a stack trace. */
    private static int internal(Throwable throwable, Console console) {
        console.error("internal error: " + throwable);
        trace(throwable, console);
        return ExitCode.INTERNAL;
    }

    /**
     * Turns an exception a command threw into one line and an exit code.
     *
     * <p>A stack trace is printed only under {@code --debug}: a tool that answers a
     * mistyped file name with forty lines of Java teaches its user to stop reading its
     * output.
     */
    private static int report(Exception exception, Console console) {
        if (exception instanceof CliException cli) {
            console.error(cli.getMessage());
            trace(exception, console);
            return cli.exitCode();
        }
        if (exception instanceof EsjLimitException || exception instanceof XrLimitException) {
            // A bound of this run that a command did not turn into a CliException itself.
            // It is not a defect of the document, so it is not ExitCode.INPUT; see
            // ExitCode.LIMIT.
            console.error(console.options().bounds()
                    .refusal("the input", exception.getMessage()));
            trace(exception, console);
            return ExitCode.LIMIT;
        }
        if (exception instanceof EsjException || exception instanceof XrException) {
            // A rule of the format or of a syntax binding that the input broke, met
            // somewhere a command did not turn it into a CliException itself.
            console.error(exception.getMessage());
            trace(exception, console);
            return ExitCode.INPUT;
        }
        if (exception instanceof UncheckedIOException) {
            // Reading failed: a failure to write is not raised at all, but remembered by
            // the console and reported once in outputFailure, with ExitCode.OUTPUT.
            console.error(exception.getMessage());
            trace(exception, console);
            return ExitCode.INPUT;
        }
        return internal(exception, console);
    }

    /** Refuses a command line the parser could not make sense of. */
    private static int usage(picocli.CommandLine.ParameterException exception, Console console) {
        console.error(exception.getMessage());
        PrintWriter writer = console.errWriter();
        picocli.CommandLine line = exception.getCommandLine();
        if (!picocli.CommandLine.UnmatchedArgumentException
                .printSuggestions(exception, writer)) {
            line.usage(writer);
        }
        writer.flush();
        return ExitCode.INPUT;
    }

    private static void trace(Throwable exception, Console console) {
        if (console.options().debug()) {
            PrintWriter writer = console.errWriter();
            exception.printStackTrace(writer);
            writer.flush();
        }
    }
}
