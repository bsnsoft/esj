package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.render.HtmlRenderer;
import de.bsnsoft.esj.render.Layout;
import de.bsnsoft.esj.render.PageSize;
import de.bsnsoft.esj.render.PdfRenderer;
import de.bsnsoft.esj.render.RenderContentException;
import de.bsnsoft.esj.render.RenderLanguage;
import de.bsnsoft.esj.render.RenderOptions;
import de.bsnsoft.esj.render.RenderLimitException;
import de.bsnsoft.esj.render.RenderResult;
import de.bsnsoft.esj.render.RenderTemplate;
import de.bsnsoft.esj.render.TemplateException;
import de.bsnsoft.esj.xr.ExportNote;
import de.bsnsoft.esj.xr.XrImporter;
import de.bsnsoft.esj.xr.XrSyntax;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code esj render}: write a document as something a person reads.
 *
 * <p>The command reads any input the other commands read and hands the document to
 * {@code esj-render}: a PDF laid out by this project — the letter layout unless
 * {@code --layout generic} or the template says otherwise, as for a caller of the library —
 * or, with {@code --html}, one self-contained HTML page produced by the vendored KoSIT
 * XRechnung visualization. Both are net renderings of the EN 16931 core and derive nothing;
 * {@code docs/rendering.md} says what each of them shows.
 *
 * <p>The rendering goes to {@code --out} and never to the standard output by default, because
 * a PDF written into a terminal that nobody redirected is a lost minute for its user and a
 * scrambled terminal afterwards. {@code --out -} asks for the standard output in as many
 * words, and that is the form a pipeline uses.
 *
 * <p>{@code --embed cii} makes the rendering a hybrid invoice: the pages this command drew,
 * with the same document attached to them as a cross industry invoice and declared in the
 * metadata as Factur-X. It is the lead use case in one command — the file a person reads and
 * the invoice a machine reads are then one file — and {@code esj embed} is the same step over
 * a PDF that came from somewhere else. What the attachment had no place for is written to
 * the error stream, as {@code esj convert --to cii} writes it.
 *
 * <p>{@code --page} and {@code --layout} are properties of the PDF. The HTML page is laid
 * out by a browser and has neither, so a run that names one of them with {@code --html} is
 * told that it took no effect rather than being refused: the rendering it asked for is the
 * rendering it receives.
 */
@Command(name = "render",
        description = "Write a document as a PDF, or with --html as one self-contained HTML"
                + " page, for a human reader.",
        sortOptions = false)
final class RenderCommand implements Callable<Integer> {

    /** How many notes of the export report are printed before they are counted instead. */
    private static final int NOTE_LINES = 20;

    /**
     * How long a rendering may take where the caller named no number of its own.
     *
     * <p>A renderer draws until the document is drawn. The PDF rendering builds every
     * page in memory before the first byte is written, so a document that is inside every
     * bound of the input can still be one whose rendering is not: a value of a megabyte
     * in a narrow column is four hundred pages, and fifty of them are a heap. A deadline
     * is the bound that fits a command whose cost is in the drawing rather than in the
     * reading, and this is the one it has when nobody chose another.
     */
    private static final Duration DEFAULT_MAX_RUNTIME = Duration.ofMinutes(5);

    @Mixin
    private final GlobalFlags flags;

    private final Console console;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<file|->",
            description = "The document to render, or - for the standard input.")
    private String file;

    @Option(order = 10, names = "--out", paramLabel = "<file|->", required = true,
            description = "Where the rendering is written: a file, or - for the standard"
                    + " output. It has no default, because a PDF on a terminal nobody"
                    + " redirected helps no one.")
    private String out;

    @Option(order = 20, names = "--html",
            description = "Write one self-contained HTML page instead of a PDF.")
    private boolean html;

    @Option(order = 30, names = "--lang", paramLabel = "<de|en>", defaultValue = "de",
            description = "The language of the labels, the dates and the decimal separator."
                    + " It says nothing about the content of the invoice. Default: de.")
    private String lang;

    @Option(order = 40, names = "--page", paramLabel = "<A4|LETTER>", defaultValue = "A4",
            description = "The paper the PDF is laid out for. The HTML page has no paper and"
                    + " ignores it. Default: A4.")
    private String page;

    @Option(order = 45, names = "--layout", paramLabel = "<generic|letter>",
            description = "The page layout of the PDF: the letter layout, which is the"
                    + " shape of a business letter, or the generic layout, which is the"
                    + " shape of the semantic model and shows every term under its own"
                    + " label. Every term occurrence of the document reaches a page in"
                    + " either. Without this the template decides, and where it says"
                    + " nothing, or there is none, the PDF is the letter. The HTML page has"
                    + " no layout of this tool's and says so. See docs/rendering.md.")
    private String layout;

    @Option(order = 50, names = "--template", paramLabel = "<file>",
            description = "Render the PDF on a branded template: a JSON file naming a"
                    + " letterhead, a logo, colours, fonts, page margins and the places it"
                    + " gives to the terms of a model extension. The files it names are read"
                    + " beside it. The HTML page has no template and says so. See"
                    + " docs/templates.md.")
    private String template;

    @Option(order = 55, names = "--no-payment-code",
            description = "Leave out the EPC QR code, known in Germany as the GiroCode, that"
                    + " the letter layout draws in the payment block where the invoice"
                    + " states a credit transfer in euro. It carries what the block prints"
                    + " beside it and nothing else. Letter layout only; a template may leave"
                    + " it out as well, and this wins over the template. See"
                    + " docs/letter-layout.md.")
    private boolean noPaymentCode;

    @Option(order = 60, names = "--embed", paramLabel = "<cii>",
            description = "Attach the invoice to the PDF as a cross industry invoice, so"
                    + " that one command writes the hybrid file: the pages a person reads"
                    + " and the invoice a machine reads, declared as Factur-X. The profile"
                    + " is the one BT-24 of the document names; esj embed is the same step"
                    + " over a PDF this tool did not render. PDF only.")
    private String embed;

    @Option(order = 70, names = "--from", paramLabel = "<ubl|cii|esj>",
            description = "Read the input as this syntax instead of recognizing it.")
    private String from;

    @Option(order = 80, names = "--extension", paramLabel = "<xrechnung|b2c>",
            description = "Load an extension registry, so that its terms are imported and"
                    + " rendered instead of being reported as unknown. Two names"
                    + " separated by a comma load both.")
    private String extension;

    @Option(order = 90, names = "--verapdf", paramLabel = "<path>",
            description = "With --embed cii, check the PDF/A conformance of the rendering"
                    + " before the invoice is written into it, with a veraPDF installation"
                    + " of your own: the directory it was installed into, or its"
                    + " executable.")
    private String verapdf;

    @Option(order = 100, names = "--no-esj",
            description = "With --embed cii, do not attach the invoice as an ESJ document"
                    + " beside the XML. By default it goes in as invoice.esj.json, where"
                    + " it and the XML are two accounts of one invoice.")
    private boolean noEsj;

    RenderCommand(Console console) {
        this.console = console;
        this.flags = new GlobalFlags(console.options());
    }

    @Override
    public Integer call() {
        RenderLanguage language = language(lang);
        PageSize size = Options.pageSize("--page", page);
        Extensions extensions = Options.extension(extension);
        if (html && given("--page")) {
            console.warning("--page is the paper of the PDF; the HTML page has none, so the"
                    + " rendering is the same with and without it");
        }
        if (html && layout != null) {
            console.warning("--layout is the page layout of the PDF; the HTML page is the"
                    + " layout of the vendored visualization, so the rendering is the same"
                    + " with and without it");
        }
        if (html && noPaymentCode) {
            console.warning("--no-payment-code is about the payment block of the letter"
                    + " layout of the PDF; the HTML page draws no code either way");
        }
        if (html && template != null) {
            console.warning("--template is a layout of the PDF; the HTML page is the layout"
                    + " of the vendored visualization, so the rendering is the same with and"
                    + " without it");
        }
        boolean embedding = embedding(embed, html);
        if (verapdf != null && !embedding) {
            console.warning("--verapdf checks the file the invoice is written into, and"
                    + " this run embeds nothing; nothing was validated");
        }
        if (noEsj && !embedding) {
            console.warning("--no-esj is about what goes into the container beside the"
                    + " invoice, and this run embeds nothing; the rendering is the same"
                    + " with and without it");
        }

        console.options().defaultMaxRuntime(DEFAULT_MAX_RUNTIME);
        Deadline deadline = Deadline.of(console.options().maxRuntime());
        Input input = Input.read(file, console);
        Loaded loaded = Loaded.read(input, Options.from(from), extensions, console);
        loaded.reportNotes(console);
        SemanticDocument document = loaded.require(console);
        Registry registry = Editions.require(document, extensions);
        RenderOptions options = RenderOptions.in(language).on(size)
                .withMaxPages(console.options().bounds().maxRenderPages());
        if (template != null && !html) {
            options = options.with(branded(template));
        }
        if (layout != null && !html) {
            options = options.layout(layout(layout));
        }
        if (noPaymentCode && !html) {
            options = options.withPaymentCode(false);
        }

        byte[] rendering = html
                ? htmlOf(document, registry, options)
                : pdfOf(document, registry, options);
        if (embedding) {
            // The invoice that goes into the container is a cross industry invoice, and
            // whoever validates the container next is entitled to the levels of that
            // syntax, which are not obliged to be the levels the source was judged by.
            String profile = Validation.customizationId(document);
            List<String> shifted = LevelShift.stricterInTarget(loaded.syntax().xrSyntax(),
                    XrSyntax.CII, profile);
            if (!shifted.isEmpty()) {
                console.information(LevelShift.line(shifted,
                        loaded.syntax().xrSyntax().orElseThrow(), XrSyntax.CII));
            }
            rendering = Embedding.into(rendering, document,
                    Embedding.options(null, null, verapdf, !noEsj, extensions, document,
                            console, deadline),
                    console);
        }
        Output.write(out, rendering, console);
        console.verbose("rendered " + input.name() + " (" + loaded.syntax().label() + ", "
                + document.values().size() + " values) as " + (html ? "HTML" : "PDF") + " in "
                + language.code()
                + (html ? "" : " on " + size + (layout == null ? ""
                        : " in the " + layout.toLowerCase(Locale.ROOT) + " layout"))
                + (embedding ? " with the invoice embedded" : "") + ", "
                + rendering.length + " bytes");
        return ExitCode.SUCCESS;
    }

    /**
     * Tells whether this run embeds the invoice into its own rendering.
     *
     * <p>An HTML page is not a container and carries no attachment, so a run that asks
     * for both is refused rather than warned about. The other options of this command
     * that the HTML page has no use for change what a page looks like and not what it
     * carries — the caller still gets the rendering they asked for — while a caller who
     * wrote {@code --embed} and received a page with nothing in it would have to open the
     * file to find that out.
     */
    private static boolean embedding(String token, boolean html) {
        if (token == null) {
            return false;
        }
        if (!"cii".equals(token)) {
            throw CliException.input("--embed takes cii, not '" + token + "'");
        }
        if (html) {
            throw CliException.input("--embed writes the invoice into a PDF container, and"
                    + " an HTML page is not one; render the PDF to embed into, or leave"
                    + " --embed out");
        }
        return true;
    }

    /**
     * Renders the HTML page and says on the error stream what did not reach it.
     *
     * <p>The page is produced by the vendored XRechnung visualization, which is written
     * for one edition of the semantic model and has nowhere to put a business term of
     * another. A document of another edition is therefore refused rather than rendered in
     * part: a rendering that silently left the payment terms of an invoice off the page
     * would be worse than none, because its reader cannot tell. The PDF is this project's
     * own layout, is driven by the registry of the document's own edition and needs no
     * such refusal.
     */
    private byte[] htmlOf(SemanticDocument document, Registry registry, RenderOptions options) {
        Registry stylesheets = XrImporter.defaultRegistry();
        if (!stylesheets.describes(document.semanticModel())) {
            throw Editions.refuse(document, "the vendored visualization stylesheets render "
                    + stylesheets.semanticModel());
        }
        RenderResult result = render(() ->
                new HtmlRenderer(registry).renderWithReport(document, options));
        reportExportNotes(result);
        console.warning("this page carries the content of a document somebody else wrote;"
                + " open it as you would any file from a stranger. See docs/rendering.md");
        return result.html().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] pdfOf(SemanticDocument document, Registry registry, RenderOptions options) {
        return render(() -> new PdfRenderer(registry).render(document, options));
    }

    /**
     * Reads a branded template, and turns a template that is not one into an answer about
     * the command line rather than about the invoice.
     *
     * <p>A template belongs to the party that renders: it is configuration of this run and
     * not something a sender wrote, so a template that cannot be read leaves with
     * {@link ExitCode#INPUT} and the sentence of the renderer, which names the member of
     * the file it is about.
     */
    private static RenderTemplate branded(String file) {
        try {
            return RenderTemplate.read(Path.of(file));
        } catch (TemplateException e) {
            throw CliException.input("cannot use the render template " + file + ": "
                    + e.getMessage(), e);
        }
    }

    /**
     * Runs a renderer and turns a document it refuses into an answer about the document.
     *
     * <p>A document of an edition of the semantic model this build does not describe is a
     * property of the input and not a defect of the tool, so it leaves with
     * {@link ExitCode#INPUT} and one line rather than as an internal error.
     *
     * <p>A rendering that runs past the page bound of this run is the other way round: a
     * bound of the run and no verdict on the document, so it leaves with
     * {@link ExitCode#LIMIT} like every other limit, and nothing is written.
     *
     * <p>So is a value whose content is not of the shape its semantic data type asks for.
     * The HTML rendering is produced by a stylesheet that reads a typed value as the type
     * says, and content that is not a lexical form of that type stops it. That is a
     * statement about the document and not a defect of this tool either, and the message
     * of the engine underneath names the value, so it is passed on as it stands. A
     * document is read before it is rendered but not validated, and {@code esj validate}
     * is the command that says what is wrong with such a value.
     *
     * @param <T>       what the rendering produces
     * @param rendering the rendering to run
     * @return what it produced
     */
    private <T> T render(Supplier<T> rendering) {
        try {
            return rendering.get();
        } catch (RenderLimitException e) {
            // A bound of this run, and no statement about the invoice: the same document
            // renders where more pages are allowed. The sentence that says how to raise it
            // is the one every other bound of this tool uses.
            throw CliException.limit(e.getMessage()
                    + console.options().bounds().hint(Bound.RENDER_PAGES), e);
        } catch (TemplateException e) {
            throw CliException.input("cannot render this document on this template: "
                    + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw CliException.input("cannot render this document: " + e.getMessage(), e);
        } catch (RenderContentException e) {
            throw CliException.input("cannot render this document: "
                    + e.getCause().getMessage(), e);
        }
    }

    /**
     * Reports what the export to the XR representation left behind.
     *
     * <p>The HTML rendering goes through that representation, so a value the representation
     * has no place for is a value the page does not show. Saying so is the difference between
     * a rendering a reader can rely on and one that quietly omits.
     */
    private void reportExportNotes(RenderResult result) {
        List<ExportNote> notes = result.report().notes();
        if (notes.isEmpty()) {
            return;
        }
        console.warning(notes.size() + (notes.size() == 1 ? " value of" : " values of")
                + " the document did not reach the HTML rendering");
        int printed = 0;
        for (ExportNote note : notes) {
            if (!console.options().verbose() && printed == NOTE_LINES) {
                console.diagnostic("  ... and " + (notes.size() - printed)
                        + " more; run with --verbose for all of them");
                return;
            }
            console.diagnostic("  " + note);
            printed++;
        }
    }

    /** Returns whether an option was written on this command line. */
    private boolean given(String option) {
        return spec.commandLine().getParseResult().hasMatchedOption(option);
    }

    /** Returns the language of a {@code --lang} token. */
    private static RenderLanguage language(String token) {
        if ("de".equals(token)) {
            return RenderLanguage.GERMAN;
        }
        if ("en".equals(token)) {
            return RenderLanguage.ENGLISH;
        }
        throw CliException.input("--lang takes de or en, not '" + token + "'");
    }

    /**
     * Returns the page layout of a {@code --layout} token.
     *
     * <p>A layout the caller names wins over the one the template names, which is what a
     * caller who writes it on the command line means by writing it there.
     */
    private static Layout layout(String token) {
        for (Layout value : Layout.values()) {
            if (value.name().equalsIgnoreCase(token)) {
                return value;
            }
        }
        throw CliException.input("--layout takes generic or letter, not '" + token + "'");
    }

    /** Returns the paper of a {@code --page} token, whichever case it was written in. */
}
