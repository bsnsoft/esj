package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.Canonicalizer;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code esj canonicalize}: the canonical bytes of a document, and the two digests taken
 * over them.
 *
 * <p>The output ends without a newline, because the canonical form of the specification,
 * section 7 ends without one and a command that added one would not be writing the
 * canonical form. A shell prompt therefore lands at the end of the last line; that is the
 * correct behaviour and not a defect.
 */
@Command(name = "canonicalize",
        description = "Write the canonical bytes of a document, or the two digests taken over"
                + " them.",
        sortOptions = false)
final class CanonicalizeCommand implements Callable<Integer> {

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<file|->",
            description = "The document to canonicalize, or - for the standard input.")
    private String file;

    @Option(order = 10, names = "--digest",
            description = "Print the semantic digest and the document digest instead of the"
                    + " bytes.")
    private boolean digest;

    @Option(order = 20, names = "--from", paramLabel = "<ubl|cii|esj>",
            description = "Read the input as this syntax instead of recognizing it.")
    private String from;

    @Option(order = 30, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that its terms are imported as"
                    + " values instead of being reported as unknown. Two names"
                    + " separated by a comma load both.")
    private String extension;

    CanonicalizeCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        Input input = Input.read(file, console);
        Loaded loaded = Loaded.read(input, Options.from(from), Options.extension(extension),
                console);
        loaded.reportNotes(console);
        SemanticDocument document = loaded.require(console);

        if (digest) {
            console.line("semantic: " + Canonicalizer.semanticDigest(document));
            console.line("document: " + Canonicalizer.documentDigest(document));
        } else {
            console.bytes(Canonicalizer.canonicalBytes(document));
        }
        return ExitCode.SUCCESS;
    }
}
