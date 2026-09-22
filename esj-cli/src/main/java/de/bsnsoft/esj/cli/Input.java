package de.bsnsoft.esj.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

/**
 * One input of a command: where it came from and what it holds.
 *
 * <p>The tool reads an input completely before it does anything with it, because every
 * command needs the bytes twice — once to recognize the syntax and once to parse it —
 * and because the digest of the source belongs to the document that is built from it.
 * The read is bounded, so a stream that never ends costs a bounded amount of memory and
 * is then refused: an ESJ document by the size this run reads, an XML input by what the
 * importer of this run reads, which is the smaller of the two and is known from the first
 * bytes. Both are bounds of {@link Bounds} and both are policy rather than a statement
 * about the document, so an input that outgrows one leaves with {@link ExitCode#LIMIT} and
 * the refusal names the switch that raises it.
 *
 * @param name  what to call this input in a message: the path as it was written, or
 *              {@code <stdin>}
 * @param bytes the content
 */
record Input(String name, byte[] bytes) {

    /** The name a message gives the standard input. */
    static final String STDIN_NAME = "<stdin>";

    /** The argument that asks for the standard input. */
    static final String STDIN_ARGUMENT = "-";

    /** The length of an input that has none to ask for, such as a stream. */
    private static final long UNKNOWN_SIZE = -1;

    /** How many bytes are read at a time. */
    private static final int CHUNK = 8192;

    /**
     * Returns what a message calls an input before it has been read.
     *
     * <p>A step that is bounded in time has to be named in the message that says the
     * bound was reached, and the read is the one step whose name cannot come from the
     * input itself, because there is no input yet.
     *
     * @param argument a file name, or {@code -} for the standard input
     * @return the name, which is the argument or {@code <stdin>}
     */
    static String label(String argument) {
        return STDIN_ARGUMENT.equals(argument) ? STDIN_NAME : argument;
    }

    /**
     * Tells whether these bytes are a PDF rather than a document.
     *
     * <p>A PDF is a container: the invoice inside it goes down the same path as a file of
     * that syntax, and the caller opens it with {@link Container} first.
     *
     * @return {@code true} if the bytes begin with the PDF file header
     */
    boolean isPdf() {
        return InputDetector.isPdf(bytes);
    }

    /**
     * Reads one input.
     *
     * <p>An input of no bytes is refused here rather than handed on, because every
     * later step would describe it as something it is not: the detector would report a
     * byte sequence it does not recognize and send the user after a syntax that is not
     * the problem, and {@code --from} would turn that into a complaint about a document
     * that is not there. A pipeline whose first stage produced nothing is a common
     * enough way to arrive here to be worth its own sentence.
     *
     * @param argument a file name, or {@code -} for the standard input
     * @param console  the streams of the process
     * @return the input
     * @throws CliException if the file cannot be read, is empty, or is larger than the
     *                      bound that applies to its kind
     */
    static Input read(String argument, Console console) {
        Bounds bounds = console.options().bounds();
        if (STDIN_ARGUMENT.equals(argument)) {
            return nonEmpty(new Input(STDIN_NAME,
                    drain(console.in(), STDIN_NAME, UNKNOWN_SIZE, bounds)));
        }
        Path path = path(argument);
        if (!Files.exists(path)) {
            throw CliException.input("no such file: " + argument);
        }
        if (Files.isDirectory(path)) {
            throw CliException.input("not a file: " + argument);
        }
        try (InputStream in = Files.newInputStream(path)) {
            return nonEmpty(new Input(argument, drain(in, argument, size(path), bounds)));
        } catch (IOException e) {
            throw CliException.input("cannot read " + argument + ": " + reason(e, argument), e);
        }
    }

    /** Returns the length of a regular file, or {@link #UNKNOWN_SIZE} where it has none. */
    private static long size(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : UNKNOWN_SIZE;
        } catch (IOException e) {
            return UNKNOWN_SIZE;
        }
    }

    private static Input nonEmpty(Input input) {
        if (input.bytes().length == 0) {
            throw CliException.input(input.name() + " is empty");
        }
        return input;
    }

    /**
     * Says why a file could not be read.
     *
     * <p>The message of a {@link FileSystemException} is the file name and nothing else,
     * so {@code "cannot read " + argument + ": " + e.getMessage()} stutters the name and
     * never names the reason. The type of the exception is where the reason is, and these
     * four are the ones a user meets.
     */
    private static String reason(IOException e, String argument) {
        if (e instanceof AccessDeniedException) {
            return "permission denied";
        }
        if (e instanceof NoSuchFileException) {
            return "no such file";
        }
        if (e instanceof NotDirectoryException) {
            return "a component of the path is not a directory";
        }
        if (e instanceof FileSystemLoopException) {
            return "too many symbolic links";
        }
        if (e instanceof FileSystemException system && system.getReason() != null) {
            return system.getReason();
        }
        String message = e.getMessage();
        return message == null || message.equals(argument) ? e.toString() : message;
    }

    private static Path path(String argument) {
        try {
            return Path.of(argument);
        } catch (InvalidPathException e) {
            throw CliException.input("not a usable file name: " + argument, e);
        }
    }

    /**
     * Reads a stream to its end, or to one byte past the bound that applies to it,
     * whichever comes first. The stream is not closed: the standard input belongs to the
     * process.
     *
     * <p>Three bounds apply, all of them this run's. An ESJ document is held to the
     * document size of {@link Bounds#maxDocumentBytes()}; an XML input is held to
     * {@link Bounds#maxInputBytes()}, because collecting sixty four mebibytes in order to
     * refuse them at four is memory spent on an answer that was already decided; a PDF is
     * held to {@link Bounds#maxPdfBytes()}.
     *
     * <p>Which of the three it is is asked of the first bytes, and asked again as more of
     * them arrive: a prefix that is nothing but a byte order mark or an indentation
     * answers neither way, and a caller that writes the input in chunks of its own
     * choosing would otherwise decide the bound by where it put its first flush. The
     * question is put to the first {@link #CHUNK} bytes, kept aside for it, and once
     * those are full without an answer the larger bound stands: an input whose first
     * eight kibibytes are whitespace is rare enough to be read under it and refused by
     * the reader that knows what it is.
     *
     * <p>An input whose length is known — a file argument — is then read into one array of
     * that length, and not into a buffer that is copied out afterwards. The difference is
     * the whole of the input a second time, at the moment the input is largest, and it
     * decides whether a process with a given heap reads a file the bounds of its profile
     * accept. The growing buffer is kept for the standard input, which has no length to
     * ask for.
     *
     * @param known  the length of the input where it has one, or {@link #UNKNOWN_SIZE}
     * @param bounds the bounds of this run
     */
    private static byte[] drain(InputStream in, String name, long known, Bounds bounds) {
        byte[] prefix = new byte[CHUNK];
        int prefixLength = 0;
        Shape shape = null;
        boolean ended = false;
        try {
            while (shape == null && prefixLength < prefix.length) {
                int read = in.read(prefix, prefixLength, prefix.length - prefixLength);
                if (read < 0) {
                    ended = true;
                    break;
                }
                prefixLength += read;
                shape = shape(prefix, prefixLength);
            }
            // The prefix is full and still says nothing, so the larger bound stands; see
            // the note above.
            Shape kind = shape == null ? Shape.ESJ : shape;
            long max = bound(kind, bounds);
            if (known != UNKNOWN_SIZE && known > max) {
                throw tooLarge(name, max, kind, bounds);
            }
            if (prefixLength > max) {
                throw tooLarge(name, max, kind, bounds);
            }
            if (ended) {
                return prefixLength == prefix.length
                        ? prefix
                        : Arrays.copyOf(prefix, prefixLength);
            }
            return known == UNKNOWN_SIZE
                    ? collect(in, name, prefix, prefixLength, max, kind, bounds)
                    : sized(in, name, prefix, prefixLength, known, max, kind, bounds);
        } catch (IOException e) {
            throw CliException.input("cannot read " + name + ": " + reason(e, name), e);
        }
    }

    /** Returns the bound that applies to an input of this shape. */
    private static long bound(Shape shape, Bounds bounds) {
        return switch (shape) {
            case PDF -> bounds.maxPdfBytes();
            case XML -> bounds.maxInputBytes();
            case ESJ -> bounds.maxDocumentBytes();
        };
    }

    /**
     * Reads an input whose length is known into one array of that length.
     *
     * <p>One byte is asked for past the end of it, and not allocated for: a file that is
     * longer than it said is either past the bound, which is a refusal, or was written to
     * while it was being read, which is answered by the growing buffer the standard input
     * uses. The ordinary case — a file of the length it has — returns the array that was
     * filled, with nothing copied and nothing else live.
     */
    private static byte[] sized(InputStream in,
                                String name,
                                byte[] prefix,
                                int prefixLength,
                                long known,
                                long max,
                                Shape shape,
                                Bounds bounds) throws IOException {
        int cap = (int) Math.min(known, max);
        if (cap <= prefixLength) {
            // The file shrank since it was measured, or it is shorter than the prefix.
            return Arrays.copyOf(prefix, prefixLength);
        }
        byte[] bytes = new byte[cap];
        System.arraycopy(prefix, 0, bytes, 0, prefixLength);
        int filled = prefixLength + in.readNBytes(bytes, prefixLength, cap - prefixLength);
        if (filled < cap) {
            return Arrays.copyOf(bytes, filled);
        }
        int past = in.read();
        if (past < 0) {
            return bytes;
        }
        if (cap == max) {
            throw tooLarge(name, max, shape, bounds);
        }
        return collect(in, name, bytes, cap, past, max, shape, bounds);
    }

    /**
     * Reads the rest of a stream whose length is not known, or is not what it said, into
     * a buffer that grows.
     */
    private static byte[] collect(InputStream in,
                                  String name,
                                  byte[] head,
                                  int headLength,
                                  long max,
                                  Shape shape,
                                  Bounds bounds) throws IOException {
        return collect(in, name, head, headLength, -1, max, shape, bounds);
    }

    /**
     * Reads the rest of a stream into a buffer that grows, starting from what has already
     * been read and, where there is one, the byte that was read past it.
     */
    private static byte[] collect(InputStream in,
                                  String name,
                                  byte[] head,
                                  int headLength,
                                  int past,
                                  long max,
                                  Shape shape,
                                  Bounds bounds) throws IOException {
        ByteArrayOutputStream collected =
                new ByteArrayOutputStream(firstAllocation(headLength, max));
        collected.write(head, 0, headLength);
        long total = headLength;
        if (past >= 0) {
            collected.write(past);
            total++;
        }
        byte[] chunk = new byte[CHUNK];
        for (int read = in.read(chunk); read >= 0; read = in.read(chunk)) {
            total += read;
            if (total > max) {
                throw tooLarge(name, max, shape, bounds);
            }
            collected.write(chunk, 0, read);
        }
        return collected.toByteArray();
    }

    /**
     * What the first bytes of an input say it is, which decides the bound it is read
     * within before anything has parsed it.
     */
    private enum Shape {

        /** A PDF, read within the bound on a file rather than on a document. */
        PDF,

        /** XML of some syntax, read within the bound the importer reads within. */
        XML,

        /** Anything else, which is an ESJ document or an input nothing will recognize. */
        ESJ
    }

    /**
     * Returns what a prefix of the input says it is, or {@code null} where the prefix is
     * too short to say.
     */
    private static Shape shape(byte[] prefix, int length) {
        Optional<Boolean> pdf = InputDetector.beginsPdf(prefix, length);
        if (pdf.isEmpty()) {
            return null;
        }
        if (Boolean.TRUE.equals(pdf.orElseThrow())) {
            return Shape.PDF;
        }
        return InputDetector.beginsXml(prefix, length)
                .map(xml -> Boolean.TRUE.equals(xml) ? Shape.XML : Shape.ESJ)
                .orElse(null);
    }

    /**
     * Refuses an input that is longer than the bound that applies to it, naming the bound
     * and the switch that raises it.
     */
    private static CliException tooLarge(String name, long max, Shape shape, Bounds bounds) {
        return CliException.limit(switch (shape) {
            case XML -> name + " is larger than the " + max + " bytes of XML this run reads"
                    + bounds.hint(Bound.INPUT_BYTES);
            case PDF -> name + " is larger than the " + max + " bytes a PDF may have in this"
                    + " run" + bounds.hint(Bound.PDF_BYTES);
            case ESJ -> name + " is larger than the " + max + " bytes a document may have in"
                    + " this run" + bounds.hint(Bound.DOCUMENT_BYTES);
        });
    }

    /**
     * Returns the size of the first allocation of a buffer that grows: what has already
     * been read, and never more than the bound that applies, so that an input which is
     * about to be refused is not allocated for first.
     */
    private static int firstAllocation(int read, long max) {
        return (int) Math.min(Math.max(read, CHUNK), max);
    }
}
