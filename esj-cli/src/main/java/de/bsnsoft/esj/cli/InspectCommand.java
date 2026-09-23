package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.pdf.FacturXProfile;
import de.bsnsoft.esj.pdf.PdfaIdentification;
import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.PackException;
import de.bsnsoft.esj.syntax.PackSelection;
import de.bsnsoft.esj.syntax.PackSource;
import de.bsnsoft.esj.syntax.Packs;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.xr.XrSyntax;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code esj inspect}: what a document says, in a page a person can read.
 *
 * <p>It is the command for the question "what did I just receive". It names the syntax
 * the bytes were written in, the few terms that identify an invoice, the totals, the two
 * digests, the validation pack that would be run against it, and then the same report
 * {@code esj validate} prints — including the sentence about the business rules, because
 * a summary that looked complete and said nothing about what it left unchecked would be
 * the most misleading output this tool could produce.
 *
 * <p>It names that pack and does not run it. Compiling the official artefacts of a
 * profile costs seconds, and the command whose promise is a quick page about a file one
 * has just received is the wrong place to spend them; {@code esj validate} is the command
 * that runs them, and the syntax block of this page says so in as many words.
 *
 * <p>The page names the reader the document came through, because this tool ships two of
 * them and they do not agree everywhere: a page that said what an invoice holds without
 * saying who read it would leave its reader unable to reproduce it.
 *
 * <p>A term that the document does not carry is shown as absent rather than left out, so
 * that the shape of the page does not depend on the document. That holds for the totals
 * too: a report with a variable number of lines cannot be compared with
 * {@code diff <(esj inspect a) <(esj inspect b)}, and a total that is missing is
 * information about the invoice rather than a reason to say nothing.
 */
@Command(name = "inspect",
        description = "Summarize a document: what it is, who it is between, what it comes"
                + " to, and what validation says about it. For a PDF it also says what the"
                + " container carries and what it declares about it.",
        sortOptions = false)
final class InspectCommand implements Callable<Integer> {

    /** The width the labels are padded to. */
    private static final int LABEL_WIDTH = 28;

    /** What is printed where a term is not in the document. */
    private static final String ABSENT = "(absent)";

    /** The group of one invoice line, which both editions of the standard put here. */
    private static final String INVOICE_LINE = "/BG-25";

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<file|->",
            description = "The document to summarize, or - for the standard input.")
    private String file;

    @Option(order = 10, names = "--from", paramLabel = "<ubl|cii|esj>",
            description = "Read the input as this syntax instead of recognizing it.")
    private String from;

    @Option(order = 20, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that its terms are imported and"
                    + " checked instead of being reported as unknown. Two names"
                    + " separated by a comma load both.")
    private String extension;

    @Option(order = 30, names = "--pack", paramLabel = "<directory|id>",
            description = "Name this validation pack instead of the bundled one that"
                    + " recognizes the profile. esj --list-packs shows what is bundled.")
    private String pack;

    InspectCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        Extensions extensions = Options.extension(extension);
        Pack chosen = SyntaxPacks.resolve(pack);
        Input input = Input.read(file, console);
        Loaded loaded;
        try {
            loaded = Loaded.inspect(input, Options.from(from), extensions, console);
        } catch (CliException refusal) {
            showContainer(input);
            throw refusal;
        }
        loaded.reportNotes(console);
        SemanticDocument document = loaded.require(console);

        line("Input", input.name());
        loaded.container().ifPresent(pdf -> container(pdf, document));
        line("Detected syntax", loaded.syntax().label());
        line("Read with", loaded.importerLabel());
        line("Semantic model", Editions.describe(document, extensions));
        line("Profile (BT-24)", value(document, "/BG-2/BT-24"));
        line("Type code (BT-3)", value(document, "/BT-3"));
        line("Invoice number (BT-1)", value(document, "/BT-1"));
        line("Issue date (BT-2)", value(document, "/BT-2"));
        line("Currency (BT-5)", value(document, "/BT-5"));
        line("Seller (BT-27)", value(document, "/BG-4/BT-27"));
        line("Buyer (BT-44)", value(document, "/BG-7/BT-44"));
        line("Invoice lines", String.valueOf(invoiceLines(document)));
        line("Line net total (BT-106)", value(document, "/BG-22/BT-106"));
        line("Total without VAT (BT-109)", value(document, "/BG-22/BT-109"));
        line("Total VAT (BT-110)", value(document, "/BG-22/BT-110"));
        line("Total with VAT (BT-112)", value(document, "/BG-22/BT-112"));
        line("Amount due (BT-115)", value(document, "/BG-22/BT-115"));
        line("Validation pack", validationPack(loaded, document, chosen));
        line("Semantic digest", Canonicalizer.semanticDigest(document));
        line("Document digest", Canonicalizer.documentDigest(document));
        console.line();

        Validation.Report report = Validation.run(loaded,
                SyntaxCheck.notRun(SyntaxCheck.NOT_INSPECTED),
                Validation.WrittenRequest.notRun(Options.VIA_CII,
                        Coverage.Cause.NOT_RUN_BY_THIS_COMMAND, WrittenCheck.NOT_INSPECTED),
                Validation.RuleRequest.notRun(RuleCheck.NOT_INSPECTED),
                ValidationLayer.L3, extensions);
        if (report.container().isPresent()) {
            Reports.containerChecks(console, report);
            console.line();
        }
        // The exit code of this command is a verdict, so the page it prints carries the
        // verdict too: a run that leaves with 1 and shows nothing that failed is a run
        // its reader has to guess about. It is the same verdict esj validate reaches and
        // is worded the same way, which for this command means it is never VALID: this
        // page runs no official artefact, so a component of the complete check is always
        // missing and the last line says which.
        Reports.notApplicable(console, report);
        Reports.text(console, report);
        Reports.verdicts(console, report);
        return Validation.exitCode(report);
    }

    /**
     * Returns the pack {@code esj validate} would run against this document.
     *
     * <p>Which pack applies follows from two facts of the document, its syntax and the
     * customization identifier of BT-24, so the answer can be given without opening a
     * single artefact. An ESJ input has no syntax to bind, and the line says that rather
     * than naming a pack that nothing would be run from.
     *
     * <p>Naming a pack is not the same as saying that the whole of it would apply. Where
     * the document names a profile the pack carries no rule set for, the name alone would
     * suggest a check that is not on offer, so the note of the selection — the same
     * sentence {@code esj validate} prints — follows the name.
     *
     * <p>Where the pack is a directory the caller named, the line says so. An identity is
     * what a manifest claims, and a directory is free to claim the one this build
     * carries.
     */
    private static String validationPack(Loaded loaded,
                                         SemanticDocument document,
                                         Pack chosen) {
        Optional<XrSyntax> syntax = loaded.syntax().xrSyntax();
        if (syntax.isEmpty()) {
            return SyntaxCheck.NO_XML;
        }
        String profile = find(document, "/BG-2/BT-24")
                .map(SemanticValue::canonicalContent).orElse("");
        try {
            PackSelection selection = chosen == null
                    ? Packs.select(syntax.orElseThrow(), profile)
                    : chosen.select(syntax.orElseThrow(), profile);
            return selection.pack().directory()
                    + (selection.pack().source() == PackSource.BUNDLED
                            ? "" : " (supplied with --pack)")
                    + selection.profileNote().map(note -> " (" + note + ")").orElse("");
        } catch (PackException e) {
            throw CliException.input("the validation pack cannot be read: " + e.getMessage(), e);
        }
    }

    /**
     * Writes what the container is and what it carries.
     *
     * <p>Everything here is what the file says about itself, and the lines say so where it
     * matters: the PDF/A conformance is a declaration this tool does not validate, and the
     * names, media types and sizes of the attachments are what the container claims rather
     * than what was measured.
     */
    private void container(Container pdf, SemanticDocument document) {
        line("Detected", Container.LABEL);
        pdf.invoice().ifPresent(invoice ->
                line("Embedded invoice", Container.describe(invoice)));
        declarations(pdf, pdf.profile(document).map(FacturXProfile::conformanceLevel));
    }

    /**
     * Shows what a container carries when no document came out of it.
     *
     * <p>This is the command for the question "what did I just receive", and a container
     * that carries two attachments either of which could be the invoice is an answer to
     * it: the run has no verdict to print, but it can print what it found and how it
     * classified each attachment, which is what the caller needs in order to pick one with
     * {@code --attachment}. The refusal follows on the error stream and the run still
     * leaves with {@link ExitCode#INPUT}: nothing was inspected, so there is no exit 0
     * here. Where the container itself is what could not be read, the refusal says so and
     * this adds nothing.
     */
    private void showContainer(Input input) {
        if (!input.isPdf()) {
            return;
        }
        Container pdf;
        try {
            pdf = Container.list(input, console);
        } catch (CliException unreadable) {
            return;
        }
        line("Input", input.name());
        line("Detected", Container.LABEL);
        declarations(pdf, pdf.declaredProfile().map(FacturXProfile::conformanceLevel));
        console.line();
    }

    /** Writes what the container declares about itself and lists what it carries. */
    private void declarations(Container pdf, Optional<String> profile) {
        line("PDF/A (declared)", pdf.pdfa()
                .map(PdfaIdentification::describe)
                .map(declared -> declared + " — declared, not validated")
                .orElse("none declared"));
        line("Factur-X profile", profile.orElse(ABSENT));
        line("Attachments", String.valueOf(pdf.attachments().size()));
        pdf.listing().forEach(console::line);
    }

    /**
     * Returns how many invoice lines a document carries.
     *
     * <p>Counted from the paths of the document rather than through a typed view, because
     * a view belongs to one edition and this page is printed for a document of whichever
     * edition it names. Both editions of the standard put the invoice line at
     * {@code /BG-25}, and an occurrence index is what separates one line from the next
     * (specification, section 5.3).
     */
    private static long invoiceLines(SemanticDocument document) {
        SemanticPath lines = SemanticPath.group(INVOICE_LINE);
        return document.values().keySet().stream()
                .filter(path -> path.startsWith(lines))
                .map(path -> path.prefix(2))
                .distinct()
                .count();
    }

    private static String value(SemanticDocument document, String path) {
        return find(document, path)
                .map(value -> ValueText.oneLine(value.canonicalContent()))
                .orElse(ABSENT);
    }

    private static Optional<SemanticValue> find(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path));
    }

    private void line(String label, String text) {
        StringBuilder padded = new StringBuilder(label).append(':');
        while (padded.length() < LABEL_WIDTH) {
            padded.append(' ');
        }
        console.line(padded + text);
    }
}
