package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import java.time.Duration;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code esj embed}: put the invoice into the PDF that shows it.
 *
 * <p>It takes a PDF/A-3 file and an invoice in any input this tool reads, and writes a
 * hybrid invoice: the same pages, with the cross industry invoice attached as an
 * associated file, named in the embedded files name tree, and declared in the XMP packet
 * by the Factur-X extension schema. {@code esj validate} reads such a file back and says
 * what the container and the invoice each are.
 *
 * <p>The rendering it embeds into is normally one of this tool's own — {@code esj render}
 * writes PDF/A-3b, and {@code esj render --embed cii} is the two commands in one — but any
 * PDF/A-3 file is accepted, so a sender whose pages come from somewhere else keeps them.
 *
 * <p><b>Nothing is converted and nothing is overwritten.</b> A PDF/A-1 or PDF/A-2 input is
 * refused rather than lifted to part 3, because that means changing pages this tool has not
 * drawn; a file that already carries something that could be the invoice is refused rather
 * than given a second one. {@code docs/pdf-output.md} lists every refusal and why it is one.
 * All of them are statements about the two inputs, so all of them leave with
 * {@link ExitCode#INPUT}.
 *
 * <p>The profile is the document's own by default: BT-24 says which specification the
 * invoice is written to, and the container has to declare the same thing, or a consumer
 * that reads the packet and one that reads the invoice are told two different things.
 * {@code --profile} is for a caller who wants the claim written explicitly, and a document
 * whose BT-24 says something else is then refused rather than relabelled.
 *
 * <p>What the cross industry invoice inside the container had no place for is written to
 * the error stream, exactly as {@code esj convert --to cii} writes it: this file is the
 * archived record of the invoice, and a term that did not reach it is one nobody will
 * notice again.
 *
 * <p>The same invoice goes in a second time as an ESJ document, under the name
 * {@code invoice.esj.json} and declared as an enclosure rather than as the invoice. It is
 * written where it and the cross industry invoice are two accounts of one invoice, which
 * is what a term the syntax has no place for does not break and a core value the writer
 * had to leave out does; a line on the error stream says which of the two happened, and
 * {@code --no-esj} leaves it out. {@code docs/pdf-output.md} has the rule.
 */
@Command(name = "embed",
        description = "Write an invoice into a PDF/A-3 file, so that the pages a person"
                + " reads carry the invoice a machine reads.",
        sortOptions = false)
final class EmbedCommand implements Callable<Integer> {

    /**
     * How long this command may take where the caller named no number of its own and lent
     * a validator. Writing the attachment costs what reading the document cost; the
     * validator is a process of somebody else's, and a bound that nobody set is the one
     * every other command that starts one gives it.
     */
    private static final Duration DEFAULT_MAX_RUNTIME = Duration.ofMinutes(5);

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<pdf>",
            description = "The PDF/A-3 file the invoice is written into.")
    private String pdf;

    @Parameters(index = "1", paramLabel = "<file|->",
            description = "The invoice, in any input this tool reads, or - for the standard"
                    + " input.")
    private String file;

    @Option(order = 10, names = "--out", paramLabel = "<file|->", required = true,
            description = "Where the hybrid invoice is written: a file, or - for the"
                    + " standard output.")
    private String out;

    @Option(order = 20, names = "--profile", paramLabel = "<profile>",
            description = "The profile the container declares: EN16931, BASIC, EXTENDED or"
                    + " XRECHNUNG. Default: the one BT-24 of the document names.")
    private String profile;

    @Option(order = 30, names = "--name", paramLabel = "<name>",
            description = "The container specification the file declares itself under,"
                    + " named by what it calls the attachment: factur-x.xml (Factur-X 1.0"
                    + " and ZUGFeRD 2.1 and later) or zugferd-invoice.xml (ZUGFeRD 2.0)."
                    + " Default: factur-x.xml.")
    private String name;

    @Option(order = 40, names = "--from", paramLabel = "<ubl|cii|esj>",
            description = "Read the invoice as this syntax instead of recognizing it.")
    private String from;

    @Option(order = 50, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that its terms are imported"
                    + " instead of being reported as unknown. Two names"
                    + " separated by a comma load both.")
    private String extension;

    @Option(order = 60, names = "--verapdf", paramLabel = "<path>",
            description = "Check the PDF/A conformance of the file the invoice goes into"
                    + " with a veraPDF installation of your own, instead of believing what"
                    + " that file declares about itself: the directory it was installed"
                    + " into, or its executable. A file the validator rejects is refused.")
    private String verapdf;

    @Option(order = 70, names = "--no-esj",
            description = "Do not attach the invoice as an ESJ document beside the XML."
                    + " By default it goes in as invoice.esj.json, where it and the XML"
                    + " are two accounts of one invoice.")
    private boolean noEsj;

    EmbedCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        Extensions extensions = Options.extension(extension);
        if (verapdf != null) {
            console.options().defaultMaxRuntime(DEFAULT_MAX_RUNTIME);
        }
        Deadline deadline = Deadline.of(console.options().maxRuntime());
        Input container = Input.read(pdf, console);
        Input invoice = Input.read(file, console);
        Loaded loaded = Loaded.read(invoice, Options.from(from), extensions, console);
        loaded.reportNotes(console);
        SemanticDocument document = loaded.require(console);

        byte[] hybrid = Embedding.into(container.bytes(), document,
                Embedding.options(profile, name, verapdf, !noEsj, extensions, document,
                        console, deadline),
                console);
        Output.write(out, hybrid, console);
        console.verbose("embedded " + invoice.name() + " (" + loaded.syntax().label() + ", "
                + document.values().size() + " values) into " + container.name() + ", "
                + hybrid.length + " bytes");
        return ExitCode.SUCCESS;
    }
}
