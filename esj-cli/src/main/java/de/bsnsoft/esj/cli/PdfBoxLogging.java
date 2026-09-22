package de.bsnsoft.esj.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Decides where the diagnostics of the PDF library go.
 *
 * <p>PDFBox writes through Apache Commons Logging, which — with no other binding on the
 * class path, and the self-contained jar carries none — writes through
 * {@link java.util.logging}. Left alone, that means the recovery messages of a damaged
 * file arrive on the error stream of this process formatted by the virtual machine: with
 * a severity word in the machine's default language, in a tool whose output is English
 * only; with no marker that separates them from this tool's own {@code error:} and
 * {@code warning:} lines; and outside the escaping every other line of output goes
 * through, although a message may quote a name the document chose.
 *
 * <p>So the choice is made here rather than left to the ambient machine. The records are
 * kept off the error stream, and {@code --verbose} shows them, escaped and one to a line,
 * as what they are: the library explaining what it did to read a broken file. What the
 * tool has to say about such a file it says itself, as a {@code PDF-STRUCTURE} finding.
 *
 * <p>This is a process-wide setting and it is installed once. It is not a general logging
 * configuration: only the two package loggers of the library are touched, and a run that
 * configured {@code java.util.logging} for its own purposes keeps every other logger.
 */
final class PdfBoxLogging {

    /** The package loggers of the library and of the font library it brings. */
    private static final String[] PACKAGES = {"org.apache.pdfbox", "org.apache.fontbox"};

    /**
     * The loggers this class configured, held so that the log manager, which keeps only a
     * weak reference to a logger, does not collect them together with the configuration.
     */
    private static final List<Logger> CONFIGURED = new ArrayList<>();

    /** The console of the run in progress, which the handler writes to. */
    private static volatile Console console;

    private static boolean installed;

    private PdfBoxLogging() {
        throw new AssertionError("no instances");
    }

    /**
     * Routes the records of the PDF library into one console.
     *
     * @param target the streams of the run, or {@code null} to drop the records
     */
    static synchronized void route(Console target) {
        console = target;
        if (installed) {
            return;
        }
        installed = true;
        Handler handler = new Sink();
        for (String name : PACKAGES) {
            Logger logger = Logger.getLogger(name);
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.ALL);
            logger.addHandler(handler);
            CONFIGURED.add(logger);
        }
    }

    /** The handler that writes a record of the library where this tool wants it. */
    private static final class Sink extends Handler {

        @Override
        public void publish(LogRecord record) {
            Console target = console;
            if (target == null || record == null || record.getMessage() == null) {
                return;
            }
            target.verbose("pdf library: " + ValueText.oneLine(record.getMessage()));
        }

        @Override
        public void flush() {
            // The console flushes itself; there is nothing buffered here.
        }

        @Override
        public void close() {
            // The handler owns no resource: it writes into the console of the run.
        }
    }
}
