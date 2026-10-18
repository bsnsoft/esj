package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.internal.report.ValidationOutcome;
import de.bsnsoft.esj.pdf.AttachmentKind;
import de.bsnsoft.esj.pdf.EmbeddedFile;
import de.bsnsoft.esj.pdf.FacturXProfile;
import de.bsnsoft.esj.pdf.NoInvoiceAttachmentException;
import de.bsnsoft.esj.pdf.PdfAccessException;
import de.bsnsoft.esj.render.PageSize;
import de.bsnsoft.esj.render.PaymentCode;
import de.bsnsoft.esj.render.RenderLanguage;
import de.bsnsoft.esj.render.RenderOptions;
import de.bsnsoft.esj.render.RenderResult;
import de.bsnsoft.esj.render.RenderTemplate;
import de.bsnsoft.esj.render.internal.ReportOptions;
import de.bsnsoft.esj.render.internal.ViewPage;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.xr.ExportNote;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code esj view}: show an invoice to the person who received it — in the browser, as one
 * page with a tab per view, or with {@code --text} in the terminal.
 *
 * <p>An electronic invoice has no file type of its own. A double-click on one shows raw XML,
 * or a PDF without the data inside it; this command is what a file manager can open
 * instead. It reads what every other command reads, writes a directory — the page, a copy of
 * the received PDF, the letter rendered from the data, the KoSIT visualization, and with
 * {@code --report} the report as an HTML file and as a PDF — and hands the page to the
 * browser of the desktop. The page says at the top what the invoice is and, with
 * {@code --report}, whether it is valid; under it the views are tabs. Nothing is fetched and
 * nothing is sent; the page runs no script. {@code docs/view.md} says what is on it.
 *
 * <p><strong>Every outcome is a page.</strong> From a file manager nobody sees the error
 * stream, so a PDF without an invoice, a PDF with several candidates, a file that is not an
 * invoice and a limit of this run each open a page that says what the file is and what to
 * ask the sender for. The exit code is the one {@code esj render} leaves with for the same
 * file, and with {@code --report} the one {@code esj validate} leaves with. What only a
 * terminal can cause — a name that is no readable file, a command line that does not parse,
 * a template that is not one — is answered on the error stream alone, as every other
 * command answers it.
 *
 * <p><strong>One read.</strong> Without {@code --report} the file is read as
 * {@code esj render} reads it, repairing an encoding with one warning line; with it the run is
 * {@code esj validate}'s, strict, and the facts, the letter, the KoSIT visualization and the
 * report are drawn from the document that validation read. Every file beside the page is
 * the bytes the command it stands for writes for the same file and the same language, so a
 * report kept from the page is the report {@code esj validate --report} writes.
 *
 * <p><strong>The terminal.</strong> {@code --text} writes no page and opens nothing: it
 * prints the invoice as {@code esj render --text} does, and with {@code --report} the lines
 * {@code esj validate} prints under it — for a terminal over SSH, where no browser opens. A
 * dead end prints the line the page would open with.
 *
 * <p>The directory is never removed by this command: the browser reads the page after the
 * command has ended, and the page says where it lies.
 */
@Command(name = "view",
        description = "Show an invoice: in the browser, as one page with a tab each for the"
                + " PDF as received, the invoice rendered from its data, the KoSIT view and,"
                + " with --report, the validation report; with --text in the terminal."
                + " See docs/view.md.",
        sortOptions = false)
final class ViewCommand implements Callable<Integer>, ReadsADocument {

    /** The page in the directory. */
    static final String PAGE = "index.html";

    /** The directory the received PDF is copied into. */
    static final String RECEIVED = "received";

    /** The longest stem of a file name the page's files are named with. */
    private static final int STEM_LENGTH = 80;

    /** The characters a stem does not carry, besides separators and controls. */
    private static final String UNSAFE = "<>:\"|?*#%/\\";

    /** The option a bound of this run names in the sentence that says how to raise it. */
    private static final Pattern RAISES = Pattern.compile("(--max-[a-z-]+) raises that bound");

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Parameters(index = "0", paramLabel = "<file>",
            description = "The invoice to show: UBL, CII, ESJ, or a PDF.")
    private String file;

    @Option(order = 10, names = "--report",
            description = "Check the invoice as esj validate does and show the report in a"
                    + " tab of its own, also kept beside the page as an HTML file and as a"
                    + " PDF. The exit code is then the verdict's.")
    private boolean report;

    @Option(order = 15, names = "--text",
            description = "Print the invoice in the terminal instead, as esj render --text"
                    + " does, and with --report the lines of esj validate under it. No page"
                    + " is written and nothing is opened.")
    private boolean text;

    @Option(order = 16, names = "--width", paramLabel = "<columns>",
            description = "With --text, how many characters a line takes, 40 to 1000."
                    + " Default: COLUMNS where it is 60 or more, otherwise 100.")
    private Integer width;

    @Option(order = 20, names = "--lang", paramLabel = "<de|en>",
            description = "The language of the page, the letter, the KoSIT view and the"
                    + " report. Default: German where LC_ALL, LC_MESSAGES or LANG (on"
                    + " Windows the locale of the user) says German, English otherwise.")
    private String lang;

    @Option(order = 30, names = "--template", paramLabel = "<file>",
            description = "Render the letter on a branded template, as esj render"
                    + " --template does. See docs/templates.md.")
    private String template;

    @Option(order = 35, names = "--no-payment-code",
            description = "Leave out the EPC QR code, known in Germany as the GiroCode, that"
                    + " the letter layout draws in the payment block where the invoice"
                    + " states a credit transfer in euro. It carries what the block prints"
                    + " beside it and nothing else. Letter layout only; a template may leave"
                    + " it out as well, and this wins over the template. See"
                    + " docs/letter-layout.md.")
    private boolean noPaymentCode;

    @Option(order = 40, names = "--out", paramLabel = "<directory>",
            description = "Write the page and its files into this directory, created where"
                    + " it is missing, and open nothing: the path of the page is printed."
                    + " Without it the directory is a new one in the temporary directory, on"
                    + " Linux below esj-view in the downloads directory.")
    private String out;

    @Option(order = 50, names = "--from", paramLabel = "<ubl|cii|esj>",
            description = "Read the input as this syntax instead of recognizing it.")
    private String from;

    @Option(order = 60, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that its terms are read, rendered"
                    + " and checked instead of being reported as unknown. Two names"
                    + " separated by a comma load both.")
    private String extension;

    @Option(order = 70, names = "--pack", paramLabel = "<directory|id>",
            description = "With --report, run this validation pack instead of the one that"
                    + " recognizes the profile, as esj validate --pack does.")
    private String pack;

    @Option(order = 72, names = "--packs", paramLabel = "<directory>",
            description = "With --report, add a pack directory, as esj validate --packs"
                    + " does. Repeatable, and added to the directories ESJ_PACKS names.")
    private List<String> packs = new ArrayList<>();

    @Option(order = 80, names = "--output", paramLabel = "<text|json>", defaultValue = "text",
            description = "What is printed: the path of the page, or an object with the page,"
                    + " the directory, whether it was opened, the state, the files written,"
                    + " the message and, with --report, the validation esj validate --output"
                    + " json prints.")
    private String output;

    /** The files written beside the page, by their member in the JSON object. */
    private final Map<String, Path> written = new LinkedHashMap<>();

    /** Whether a file of the directory was not written in full. */
    private boolean failedWrite;

    ViewCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        boolean json = json(output);
        if (text && json) {
            throw CliException.input("--text prints the invoice and --output json describes a"
                    + " page; leave out one of them");
        }
        int columns = RenderCommand.textWidth(width, console);
        if (width != null && !text) {
            console.warning("--width is the width of the text --text prints; without --text"
                    + " the page is the same with and without it");
        }
        RenderLanguage language = lang == null ? Desktop.language(console.options())
                : RenderCommand.language(lang);
        Extensions extensions = Options.extension(extension);
        Options.from(from);
        if (text && out != null) {
            console.warning("--out is the directory of the page, and --text writes no page;"
                    + " nothing is written there");
        }
        if (text && template != null) {
            console.warning("--template is the template of the letter, and --text prints the"
                    + " invoice as text; the text is the same with and without it");
        }
        // A template is configuration of this run and not something a sender wrote: one that
        // is not a template is the caller's mistake, answered where the caller typed it.
        RenderTemplate branded = template == null || text ? null
                : RenderCommand.branded(template);
        Path source = readable(file);
        if (!report && (pack != null || !packs.isEmpty())) {
            console.warning("--pack and --packs choose the pack of the validation --report"
                    + " runs; without --report nothing is validated");
        }
        Deadline deadline = Deadline.of(console.options().maxRuntime());
        PackChoice chosen = report
                ? deadline.within("reading the validation packs",
                        () -> PackChoice.of(pack, packs, console))
                : null;
        if (text) {
            return text(language, extensions, chosen, deadline, columns);
        }

        String name = source.getFileName().toString();
        String stem = stem(name);
        Path directory = directory(stem);
        ViewPage.Content content = new ViewPage.Content(language, name, directory.toString(),
                VersionProvider.tool());
        if (report) {
            content.reportAsked();
        }
        int code = view(content, directory, stem, language, extensions, branded, chosen,
                deadline);

        Path page = directory.resolve(PAGE);
        write(page, ViewPage.html(content).getBytes(StandardCharsets.UTF_8));
        if (failedWrite && !Files.isRegularFile(page)) {
            return ExitCode.OUTPUT;
        }
        boolean opened = out == null && Desktop.display(console.options())
                && Desktop.open(console.options(), page);
        if (json) {
            json(page, directory, opened);
        } else {
            console.line(page.toString());
        }
        if (out == null && Desktop.display(console.options()) && !opened) {
            console.warning("could not open a browser; the page is at " + page);
        }
        return failedWrite ? ExitCode.OUTPUT : code;
    }

    /** The outcome of the run, as the JSON object names it. */
    private String state = "read";

    /** The error text of a dead end, for the JSON object. */
    private String message;

    /** What the validation found, with {@code --report}. */
    private Validation.Report validation;

    /** What the syntax engine did, with {@code --report}. */
    private SyntaxCheck syntax;

    /** The input, once it was read. */
    private Input input;

    /** The document, read as {@code render} or {@code validate} reads it. */
    private Loaded loaded;

    /**
     * Reads the file: as {@code esj render} reads it, or with {@code --report} as
     * {@code esj validate} does, validating it.
     *
     * @return the document, or {@code null} where a strict read built none
     * @throws CliException where the file leads nowhere
     */
    private SemanticDocument read(Extensions extensions, PackChoice chosen,
                                  Deadline deadline) {
        input = deadline.within(Input.label(file) + ": reading the input",
                () -> Input.read(file, console));
        if (report) {
            ValidationRun run = new ValidationRun(console, from, extensions,
                    null, false, true, Options.level(null));
            loaded = run.load(input, deadline);
            loaded.reportNotes(console);
            ValidationRun.Result checked =
                    run.check(input, loaded, chosen, Optional::empty, deadline);
            syntax = checked.syntax();
            validation = checked.report();
            return loaded.document().orElse(null);
        }
        loaded = deadline.within(input.name() + ": reading the document into the"
                        + " semantic model",
                () -> Loaded.read(input, Options.from(from), extensions, console));
        loaded.reportNotes(console);
        return loaded.require(console);
    }

    /**
     * Reads the file, draws what the page shows and writes the files beside it.
     *
     * @return the exit code of the run, before the page itself is written
     */
    private int view(ViewPage.Content content, Path directory, String stem,
                     RenderLanguage language, Extensions extensions, RenderTemplate branded,
                     PackChoice chosen, Deadline deadline) {
        SemanticDocument document;
        try {
            document = read(extensions, chosen, deadline);
        } catch (CliException e) {
            return deadEnd(content, directory, e);
        }
        if (input.isPdf()) {
            received(content, directory);
        }
        header(content, document, extensions);

        Optional<Finding> limit = validation == null ? Optional.empty() : validation.limit();
        if (limit.isPresent()) {
            // An ESJ document a bound of this run stopped: the run was validate's and its
            // report says what it reached, NO VERDICT, so the page is the dead end of a limit
            // with that report under it.
            state = "limit";
            message = console.options().bounds()
                    .refusal(validation.input(), limit.orElseThrow().message());
            console.error(message);
            content.deadEnd(ViewPage.DeadEnd.limit(limit(message)));
            reports(content, directory, stem, extensions, deadline, language);
            return ExitCode.LIMIT;
        }

        boolean letterLimited = letter(content, directory, stem, document, language,
                extensions, branded, deadline);
        kosit(content, directory, stem, document, language, extensions, deadline);
        if (!report) {
            return letterLimited ? ExitCode.LIMIT : ExitCode.SUCCESS;
        }
        boolean delivered = reports(content, directory, stem, extensions, deadline, language);
        int verdict = Validation.exitCode(validation);
        // As esj validate: a report asked for and not delivered is an output missing,
        // unless the verdict is invalid, which is the answer the caller came for.
        return delivered || verdict == ExitCode.VALIDATION ? verdict : ExitCode.OUTPUT;
    }

    // ------------------------------------------------------------------- the terminal

    /**
     * Prints the invoice as text, and with {@code --report} the lines of the validation under
     * it; or, where the file leads nowhere, the line the page would open with.
     *
     * @return the exit code: the one {@code esj render} leaves with, or with {@code --report}
     *         the verdict's
     */
    private int text(RenderLanguage language, Extensions extensions, PackChoice chosen,
                     Deadline deadline, int columns) {
        SemanticDocument document;
        try {
            document = read(extensions, chosen, deadline);
        } catch (CliException e) {
            console.error(e.getMessage());
            console.line(ViewPage.state(deadEndOf(e), language, report));
            return e.exitCode();
        }
        Optional<Finding> limit = validation == null ? Optional.empty() : validation.limit();
        if (limit.isPresent()) {
            String refusal = console.options().bounds()
                    .refusal(validation.input(), limit.orElseThrow().message());
            console.error(refusal);
            console.line(ViewPage.state(ViewPage.DeadEnd.limit(limit(refusal)), language,
                    true));
            validationLines(columns);
            return ExitCode.LIMIT;
        }
        Optional<String> refused = document == null ? Optional.of("no document was built")
                : unreadable(loaded);
        if (refused.isPresent()) {
            console.warning("the invoice was not rendered: " + refused.orElseThrow());
            console.line(ViewPage.notRendered(language));
        } else {
            try {
                byte[] rendered = deadline.within(loaded.name() + ": rendering the text",
                        () -> RenderCommand.text(document,
                                Editions.require(document, extensions), language, columns,
                                console));
                console.bytes(rendered);
            } catch (CliException e) {
                if (e.exitCode() == ExitCode.LIMIT && !report) {
                    console.error(e.getMessage());
                    return ExitCode.LIMIT;
                }
                console.warning("the invoice was not rendered: " + e.getMessage());
                console.line(ViewPage.notRendered(language));
            }
        }
        if (!report) {
            return ExitCode.SUCCESS;
        }
        validationLines(columns);
        return Validation.exitCode(validation);
    }

    /** Prints the lines {@code esj validate} prints, under a rule. */
    private void validationLines(int columns) {
        console.line();
        console.line("-".repeat(columns));
        console.line();
        ValidateCommand.lines(console, validation, syntax);
    }

    // ------------------------------------------------------------------- dead ends

    /**
     * Makes the page the dead end a refusal of the read stands for, with the received PDF
     * where there is one to show.
     *
     * @return the exit code of the refusal, which is the one every other command leaves with
     */
    private int deadEnd(ViewPage.Content content, Path directory, CliException e) {
        ViewPage.DeadEnd end = deadEndOf(e);
        state = switch (end.kind()) {
            case NO_INVOICE -> "no-invoice";
            case NOT_ESTABLISHED -> "not-established";
            case SEVERAL -> "several";
            case UNSUPPORTED -> "unsupported";
            case ENCRYPTED -> "encrypted";
            case NOT_READ -> "not-read";
            case LIMIT -> "limit";
        };
        message = e.getMessage();
        console.error(message);
        content.deadEnd(end);
        // An encrypted file is not copied: nothing of it was read, and a page that framed it
        // would hand the reader a file this tool refused to open.
        if (input != null && input.isPdf() && end.kind() != ViewPage.DeadEnd.Kind.ENCRYPTED) {
            received(content, directory);
        }
        return e.exitCode();
    }

    /** Returns the dead end a refusal of the read is: which, its candidates, its bound. */
    private ViewPage.DeadEnd deadEndOf(CliException e) {
        ViewPage.DeadEnd.Kind kind = kind(e);
        return switch (kind) {
            case SEVERAL -> ViewPage.DeadEnd.several(candidates(e));
            case LIMIT -> ViewPage.DeadEnd.limit(limit(e.getMessage()));
            default -> ViewPage.DeadEnd.of(kind);
        };
    }

    /**
     * Returns a bound of this run as a reader is told about it: the time the run was given,
     * the pages of the letter, or a size, with the option the message names to raise it.
     *
     * @param refusal the message of the bound, as every command of this tool writes it
     * @return the bound
     */
    static ViewPage.Limit limit(String refusal) {
        if (refusal.contains("--max-runtime")) {
            return new ViewPage.Limit(ViewPage.Limit.Kind.TIME, "--max-runtime");
        }
        Matcher raises = RAISES.matcher(refusal);
        String option = raises.find() ? raises.group(1) : null;
        return new ViewPage.Limit("--max-pages".equals(option) ? ViewPage.Limit.Kind.PAGES
                : ViewPage.Limit.Kind.SIZE, option);
    }

    /** Returns which dead end a refusal of the read is, by its exit code and its cause. */
    static ViewPage.DeadEnd.Kind kind(CliException e) {
        if (e.exitCode() == ExitCode.LIMIT) {
            return ViewPage.DeadEnd.Kind.LIMIT;
        }
        if (e.exitCode() == ExitCode.UNSUPPORTED) {
            return ViewPage.DeadEnd.Kind.UNSUPPORTED;
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof Container.Several) {
                return ViewPage.DeadEnd.Kind.SEVERAL;
            }
            if (cause instanceof NoInvoiceAttachmentException none) {
                return none.unestablished().isEmpty() ? ViewPage.DeadEnd.Kind.NO_INVOICE
                        : ViewPage.DeadEnd.Kind.NOT_ESTABLISHED;
            }
            if (cause instanceof PdfAccessException) {
                return ViewPage.DeadEnd.Kind.ENCRYPTED;
            }
        }
        return ViewPage.DeadEnd.Kind.NOT_READ;
    }

    /** Returns the rows of the table of candidates a refusal of several names. */
    private List<ViewPage.Candidate> candidates(CliException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof Container.Several several) {
                List<ViewPage.Candidate> rows = new ArrayList<>();
                for (Container.Several.Candidate candidate : several.candidates()) {
                    EmbeddedFile attached = candidate.attachment().file();
                    AttachmentKind kind = candidate.attachment().kind();
                    rows.add(new ViewPage.Candidate(candidate.position(),
                            candidate.attachment().name(), kind(kind), kind.describe(),
                            attached.declaredSize(), attached.associatedRelationship(),
                            "esj view --report --attachment-index " + candidate.position()
                                    + " " + quoted(file)));
                }
                return rows;
            }
        }
        return List.of();
    }

    private static ViewPage.Candidate.Kind kind(AttachmentKind kind) {
        return switch (kind) {
            case CII_INVOICE -> ViewPage.Candidate.Kind.CII_INVOICE;
            case UBL_INVOICE -> ViewPage.Candidate.Kind.UBL_INVOICE;
            case UBL_CREDIT_NOTE -> ViewPage.Candidate.Kind.UBL_CREDIT_NOTE;
            case UNDETERMINED -> ViewPage.Candidate.Kind.UNDETERMINED;
            case UNREADABLE -> ViewPage.Candidate.Kind.UNREADABLE;
            default -> ViewPage.Candidate.Kind.OTHER;
        };
    }

    /**
     * Returns a file name as a word of a POSIX shell: as it stands where it needs no quotes,
     * in single quotes otherwise, so that the command on the page can be pasted.
     */
    static String quoted(String word) {
        if (!word.isEmpty() && word.chars().allMatch(c -> c < 128
                && (Character.isLetterOrDigit(c) || "_./-+=:@,".indexOf(c) >= 0))) {
            return word;
        }
        return "'" + word.replace("'", "'\\''") + "'";
    }

    // ------------------------------------------------------------------- the header

    /** Copies the received PDF, unchanged, under its own name into {@code received/}. */
    private void received(ViewPage.Content content, Path directory) {
        String name = Path.of(file).getFileName().toString();
        Path copy = directory.resolve(RECEIVED).resolve(name);
        if (write(copy, input.bytes())) {
            content.received(name);
            written.put("received", copy);
        }
    }

    /**
     * Gives the header what the invoice says: the document, and where the container declares
     * a profile that carries no EN 16931 invoice, that profile.
     */
    private void header(ViewPage.Content content, SemanticDocument document,
                        Extensions extensions) {
        Optional<Container> container = loaded.container();
        Optional<FacturXProfile> profile = validation != null ? validation.profile()
                : document == null ? Optional.empty()
                        : container.flatMap(found -> found.profile(document));
        profile.filter(found -> !found.isEn16931Invoice())
                .ifPresent(found -> content.notEn16931(found.conformanceLevel()));
        if (document != null) {
            content.document(document, Editions.forDocument(document, extensions).orElse(null));
        }
    }

    // ------------------------------------------------------------------- the views

    /**
     * Renders the letter, as {@code esj render} renders it for this file and this language,
     * and writes it beside the page; or says why there is none.
     *
     * @return whether the rendering reached a bound of this run
     */
    private boolean letter(ViewPage.Content content, Path directory, String stem,
                           SemanticDocument document, RenderLanguage language,
                           Extensions extensions, RenderTemplate branded, Deadline deadline) {
        if (document == null || unreadable(loaded).isPresent()) {
            content.letter(ViewPage.Letter.refused());
            return false;
        }
        RenderOptions options = RenderCommand.options(console, language, PageSize.A4);
        RenderOptions letter = branded == null ? options : options.withTemplate(branded);
        if (noPaymentCode) {
            letter = letter.withPaymentCode(PaymentCode.OMIT);
        }
        RenderOptions chosen = letter;
        byte[] pdf;
        try {
            pdf = deadline.within(loaded.name() + ": rendering the letter",
                    () -> RenderCommand.pdf(document, Editions.require(document, extensions),
                            chosen, console));
        } catch (CliException e) {
            if (e.exitCode() == ExitCode.LIMIT) {
                if (report) {
                    console.warning("the letter was not written: " + e.getMessage());
                } else {
                    console.error(e.getMessage());
                }
                content.letter(ViewPage.Letter.limited(limit(e.getMessage())));
                return true;
            }
            console.warning("the letter was not written: " + e.getMessage());
            content.letter(ViewPage.Letter.refused());
            return false;
        }
        String name = stem + ".letter.pdf";
        if (write(directory.resolve(name), pdf)) {
            written.put("letter", directory.resolve(name));
            content.letter(ViewPage.Letter.written(name));
        } else {
            content.letter(ViewPage.Letter.refused());
        }
        return false;
    }

    /**
     * Renders the KoSIT visualization, as {@code esj render --html} renders it for this file
     * and this language, and writes it beside the page with what did not reach it; where it
     * cannot be rendered, the page has no tab for it.
     */
    private void kosit(ViewPage.Content content, Path directory, String stem,
                       SemanticDocument document, RenderLanguage language,
                       Extensions extensions, Deadline deadline) {
        if (document == null || unreadable(loaded).isPresent()) {
            return;
        }
        RenderOptions options = RenderCommand.options(console, language, PageSize.A4);
        RenderResult result;
        try {
            result = deadline.within(loaded.name() + ": rendering the HTML page",
                    () -> RenderCommand.html(document, Editions.require(document, extensions),
                            options, console));
        } catch (CliException e) {
            console.warning("the HTML rendering was not written: " + e.getMessage());
            return;
        }
        String name = stem + ".kosit.html";
        if (write(directory.resolve(name), result.html().getBytes(StandardCharsets.UTF_8))) {
            written.put("html", directory.resolve(name));
            content.kosit(name, result.report().notes().stream()
                    .map(ExportNote::toString).toList());
        }
    }

    /**
     * Returns why the reader of an ESJ input built a document no other command renders: a
     * document that fails the format layer is refused by {@code esj render}, and the page
     * says so in its words rather than drawing what that command would not.
     */
    private Optional<String> unreadable(Loaded loaded) {
        Optional<Finding> error = loaded.formatFindings().stream().filter(Finding::isError)
                .filter(finding -> finding.code() != FindingCode.ESJ_L1_LIMIT).findFirst();
        return error.map(found -> loaded.name() + " is not a well-formed ESJ document: "
                + found);
    }

    /**
     * Draws the report, as {@code esj validate --report} draws it for this file and this
     * language, in both its forms, writes them beside the page and puts its sections on it.
     *
     * @return whether both forms were delivered
     */
    private boolean reports(ViewPage.Content content, Path directory, String stem,
                            Extensions extensions, Deadline deadline, RenderLanguage language) {
        ReportOptions options = ReportOptions.defaults().withLanguage(language);
        ValidationOutcome outcome = Outcome.of(input, loaded, validation);
        String html = stem + ".report.html";
        String pdf = stem + ".report.pdf";
        boolean htmlWritten = report(false, directory.resolve(html), "reportHtml", input, loaded,
                extensions, deadline, options);
        boolean pdfWritten = report(true, directory.resolve(pdf), "reportPdf", input, loaded,
                extensions, deadline, options);
        content.report(outcome, pdfWritten ? pdf : null);
        return htmlWritten && pdfWritten;
    }

    private boolean report(boolean pdf, Path target, String member, Input input, Loaded loaded,
                           Extensions extensions, Deadline deadline, ReportOptions options) {
        byte[] bytes;
        try {
            bytes = ValidationRun.report(pdf, input, loaded, validation, extensions, deadline,
                    options);
        } catch (CliException | EsjLimitException e) {
            console.error("no " + (pdf ? "PDF" : "HTML") + " report was written: "
                    + e.getMessage());
            return false;
        }
        if (!write(target, bytes)) {
            return false;
        }
        written.put(member, target);
        return true;
    }

    // ------------------------------------------------------------------- files

    /**
     * Returns the input as a path, where it names a file this run can read, and refuses it
     * otherwise without a page: a name that is no readable file is a mistake of the command
     * line, which only a terminal can make.
     */
    private Path readable(String argument) {
        if (Input.STDIN_ARGUMENT.equals(argument)) {
            throw CliException.input("esj view writes a page that links to the file it shows;"
                    + " name a file rather than the standard input");
        }
        Path path;
        try {
            path = Path.of(argument);
        } catch (InvalidPathException e) {
            throw CliException.input("not a usable file name: " + argument, e);
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            // Input says why, in the words every other command uses.
            Input.read(argument, console);
            throw CliException.input("cannot read " + argument);
        }
        if (path.getFileName() == null) {
            throw CliException.input("not a file: " + argument);
        }
        return path;
    }

    /**
     * Returns the directory of this run: the one {@code --out} names, created, or a new one
     * where the desktop keeps such directories.
     */
    private Path directory(String stem) {
        try {
            if (out != null) {
                Path named = Path.of(out).toAbsolutePath().normalize();
                Files.createDirectories(named);
                return named;
            }
            return Desktop.directory(console.options(), stem).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw CliException.input("not a usable directory name: " + out, e);
        } catch (IOException e) {
            throw CliException.output("cannot create the directory of the page"
                    + (out == null ? "" : " " + out) + ": " + reason(e), e);
        }
    }

    /**
     * Writes one file of the directory, replacing what was there, and remembers a file that
     * was not written in full.
     *
     * @return whether it was written
     */
    private boolean write(Path target, byte[] bytes) {
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            console.verbose("wrote " + bytes.length + " bytes to " + target);
            return true;
        } catch (IOException e) {
            console.error("cannot write " + target + ": " + reason(e));
            failedWrite = true;
            return false;
        }
    }

    private static String reason(IOException e) {
        if (e instanceof FileSystemException system && system.getReason() != null) {
            return system.getReason();
        }
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    /**
     * Returns the name a file of the page is given: the name of the input without its last
     * extension, a separator, a control character and any of {@code <>:"|?*#%} as {@code _},
     * and at most {@value #STEM_LENGTH} characters.
     *
     * @param name the name of the input file
     * @return the stem
     */
    static String stem(String name) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        StringBuilder stem = new StringBuilder();
        int count = 0;
        for (int i = 0; i < base.length() && count < STEM_LENGTH; ) {
            int c = base.codePointAt(i);
            boolean unsafe = c < 0x20 || c == 0x7f || (c >= 0x80 && c < 0xa0)
                    || UNSAFE.indexOf(c) >= 0;
            if (unsafe) {
                stem.append('_');
            } else {
                stem.appendCodePoint(c);
            }
            count++;
            i += Character.charCount(c);
        }
        return stem.length() == 0 ? "_" : stem.toString();
    }

    // ------------------------------------------------------------------- output

    /** Writes the object {@code --output json} prints in place of the path. */
    private void json(Path page, Path directory, boolean opened) {
        Json.write(console, generator -> {
            generator.writeStartObject();
            generator.writeStringField("page", page.toString());
            generator.writeStringField("directory", directory.toString());
            generator.writeBooleanField("opened", opened);
            generator.writeStringField("state", state);
            generator.writeObjectFieldStart("files");
            for (String member : List.of("received", "letter", "html", "reportHtml",
                                         "reportPdf")) {
                Path path = written.get(member);
                if (path == null) {
                    generator.writeNullField(member);
                } else {
                    generator.writeStringField(member, path.toString());
                }
            }
            generator.writeEndObject();
            if (message == null) {
                generator.writeNullField("message");
            } else {
                generator.writeStringField("message", message);
            }
            if (validation == null) {
                generator.writeNullField("validation");
            } else {
                generator.writeFieldName("validation");
                Reports.json(generator, validation);
            }
            generator.writeEndObject();
        });
    }

    private static boolean json(String token) {
        if ("json".equals(token)) {
            return true;
        }
        if ("text".equals(token)) {
            return false;
        }
        throw CliException.input("--output takes text or json, not '" + token + "'");
    }
}
