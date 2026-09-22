package de.bsnsoft.esj.cli;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Where a command writes a file it was asked to produce.
 *
 * <p>Every other command of this tool writes its result to the standard output, because the
 * result is text a pipeline reads. A rendering is neither: it is a PDF or a page meant to be
 * opened, and the common case is a file. {@code -} still asks for the standard output, so
 * that a pipeline can have it, and it is the same stream and the same failure handling as
 * everywhere else — a write that fails there is remembered by {@link Console} and reported
 * once, with {@link ExitCode#OUTPUT}.
 *
 * <p>A write to a named file that fails is reported here instead, with the same exit code:
 * the caller did not receive the bytes the command produced, and that is what code 6 means,
 * whichever of the two destinations was asked for.
 */
final class Output {

    private Output() {
        throw new AssertionError("no instances");
    }

    /**
     * Writes the bytes to the destination a {@code --out} argument names.
     *
     * @param argument the destination: a file name, or {@code -} for the standard output
     * @param content  the bytes to write
     * @param console  the streams of this run
     */
    static void write(String argument, byte[] content, Console console) {
        if (Input.STDIN_ARGUMENT.equals(argument)) {
            console.bytes(content);
            return;
        }
        Path path = path(argument);
        try {
            Files.write(path, content);
        } catch (IOException e) {
            throw CliException.output("cannot write " + argument + ": " + reason(e, argument),
                    e);
        }
        console.verbose("wrote " + content.length + " bytes to " + argument);
    }

    /** Returns what a caller is told about a write that did not happen. */
    private static String reason(IOException e, String argument) {
        if (e instanceof AccessDeniedException) {
            return "permission denied";
        }
        if (e instanceof NoSuchFileException) {
            return "no such directory";
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
}
