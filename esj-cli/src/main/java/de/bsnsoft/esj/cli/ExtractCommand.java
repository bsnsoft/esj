package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.pdf.LocatedAttachment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Takes the electronic invoice out of a PDF and writes it somewhere, unchanged.
 *
 * <p>It is the one command that hands back the bytes of the source rather than a document
 * of this project's own: the attachment leaves exactly as it arrived, so that whatever the
 * next tool is — a validator of the syntax binding, a viewer, an archive — reads what the
 * sender wrote and not what this tool made of it.
 *
 * <p><strong>The name of an attachment is never used as a file name.</strong> It is a
 * string whoever wrote the PDF chose, so it may carry path separators, {@code ..}, a
 * misleading double extension or control characters ({@code SPEC.md}, section 12.5). The
 * output goes to the standard output, or to the path the caller gave with {@code --out}
 * and to no other.
 */
@Command(name = "extract",
        description = "Write the electronic invoice of a PDF to a file or to the standard"
                + " output, or list what the PDF carries.",
        sortOptions = false)
final class ExtractCommand implements Callable<Integer> {

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<file|->",
            description = "The PDF to read, or - for the standard input.")
    private String file;

    @Option(order = 10, names = "--out", paramLabel = "<file|->",
            description = "Where to write the attachment. The default is the standard"
                    + " output, which - names as well.")
    private String out;

    @Option(order = 20, names = "--list",
            description = "List every attachment of the PDF instead of writing one: what its"
                    + " bytes are, what the container says its media type and size are, and"
                    + " what it declares the attachment to be to the document.")
    private boolean list;

    ExtractCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        Input input = Input.read(file, console);
        if (!input.isPdf()) {
            throw CliException.input(input.name() + " is not a PDF, and esj extract takes"
                    + " the electronic invoice out of a PDF; an XML invoice needs nothing"
                    + " taken out of it");
        }
        if (list) {
            return list(input);
        }
        Container.Invoice invoice = Container.read(input, console).requireInvoice();
        if (out == null || Input.STDIN_ARGUMENT.equals(out)) {
            console.bytes(invoice.bytes());
        } else {
            write(invoice.bytes());
        }
        console.verbose("wrote " + invoice.bytes().length + " bytes of "
                + invoice.attachment().kind().describe());
        return ExitCode.SUCCESS;
    }

    /** Writes every attachment of the container as one line, numbered. */
    private int list(Input input) {
        Container container = Container.list(input, console);
        console.line("Input:        " + input.name());
        console.line("Attachments:  " + container.attachments().size());
        int ordinal = 1;
        for (LocatedAttachment attachment : container.attachments()) {
            console.line("  " + ordinal++ + "  " + Container.detail(attachment));
        }
        return ExitCode.SUCCESS;
    }

    /**
     * Writes the attachment to the path the caller named.
     *
     * <p>A failure to write is {@link ExitCode#OUTPUT} and never {@link ExitCode#INPUT}: a
     * full disk, a directory that is not there and a file that may not be written say
     * nothing at all about the invoice.
     */
    private void write(byte[] bytes) {
        Path path;
        try {
            path = Path.of(out);
        } catch (InvalidPathException e) {
            throw CliException.input("--out is not a usable file name: " + out, e);
        }
        try {
            Files.write(path, bytes);
        } catch (IOException e) {
            throw CliException.output("cannot write " + out + ": " + e.getMessage(), e);
        }
        console.verbose("wrote " + out);
    }
}
