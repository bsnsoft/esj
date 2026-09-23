package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.report.ValidationOutcome;
import de.bsnsoft.esj.xr.ExportNote;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

/**
 * Writes what a validation run came to as one file a person reads: an HTML page, or a PDF.
 *
 * <p>The report is the answer to the question a caller asks with {@code esj validate
 * invoice.pdf}: which bytes were judged, by which released rules, what each check came to,
 * what was found, what the verdict is — and the invoice itself, so that the reader of the
 * report and the reader of the invoice are looking at the same document. One file, no
 * second resource, nothing fetched when it is opened, and the same bytes for the same
 * input and the same options.
 *
 * <h2>It reports and never decides</h2>
 *
 * <p>Nothing here weighs a finding, adds up a row or reaches a verdict. A
 * {@link ValidationOutcome} carries what the run decided and this class puts it on a page,
 * in the order the run wrote it. That is the only way the report, the lines the command
 * printed and the machine-readable form of the same run can be one answer, and it is why a
 * renderer that sorted findings again would be a defect rather than a courtesy.
 *
 * <h2>What it never claims</h2>
 *
 * <p>The report says what the run said and not a word more. A container's PDF/A conformance
 * is <em>declared</em> and not validated by this project, a check that did not run is
 * printed as one that did not run, a syntax block over an input that was never XML is
 * printed as not applicable, and every pack is named with its version. Each of those is a
 * row or a sentence the run put in the outcome; this class has no vocabulary of its own for
 * any of them.
 *
 * <h2>The two forms</h2>
 *
 * <p>{@link #html(ValidationOutcome, SemanticDocument, ReportOptions)} is one page with an
 * inline style sheet and no script of its own. The rendered invoice goes into a sandboxed
 * {@code iframe} written with {@code srcdoc}, so that the page stays one file and the style
 * sheet of the vendored visualization cannot reach the report around it; {@link ReportHtml}
 * sets out what that costs and why the frame runs scripts in an origin of its own.
 *
 * <p>{@link #pdf(ValidationOutcome, SemanticDocument, ReportOptions)} writes the same
 * content as pages, in the fonts and on the paper of {@link PdfRenderer}, and the invoice
 * follows it in the same file as the generic layout of that renderer draws it — from the
 * document itself, so nothing of it is left behind on the way. A printed report is
 * therefore the whole of the report and the whole of the invoice, which is what the framed
 * page cannot give, and the page says so where it applies.
 *
 * <h2>Determinism</h2>
 *
 * <p>The same outcome, the same document and the same options give the same string and the
 * same bytes. Nothing of the machine, the moment or the run takes part: there is no clock
 * in either form, the PDF carries no date and a fixed producer, and the moment a report
 * prints is the one the caller passed in {@link ReportOptions#time()} and is printed as
 * given.
 *
 * <h2>A document that cannot be rendered</h2>
 *
 * <p>A report is most needed for a document something is wrong with, so a document a
 * renderer refuses — an edition this build holds no registry for, a value whose content is
 * not a lexical form of its type — does not take the report down with it. The report is
 * written, and in place of the invoice stands the sentence that says what refused it. The
 * verdict and the findings are unaffected: they were decided before this class was called.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class ReportRenderer {

    /** What the PDF says produced it, as in {@link PdfRenderer}: no version, no build. */
    private static final String PRODUCER = "EN16931 Semantic JSON (esj-render)";

    /** The number the document identifier is derived from; a clock would end determinism. */
    private static final long FIXED_DOCUMENT_ID = 0L;

    private final Registry registry;

    /** Creates a renderer that knows the core model and the XRechnung extension. */
    public ReportRenderer() {
        this(XrImporter.defaultRegistry());
    }

    /**
     * Creates a renderer with a registry of its caller's choosing, which is the registry
     * the invoice inside the report is rendered against.
     *
     * @param registry the registry
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    public ReportRenderer(Registry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Writes a report as one self-contained HTML page.
     *
     * @param outcome  what the run came to
     * @param document the document that was judged, or {@code null} where the run built
     *                 none — the report is then the check table and the findings alone
     * @param options  the language, whether the invoice is part of the report, and the
     *                 moment to print
     * @return the page
     * @throws RenderException      if the invoice could not be produced for a reason that
     *                              is not a property of the document
     * @throws NullPointerException if {@code outcome} or {@code options} is {@code null}
     */
    public String html(ValidationOutcome outcome, SemanticDocument document,
                       ReportOptions options) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(options, "options");
        if (!wanted(document, options)) {
            return ReportHtml.of(outcome, options, ReportInvoice.none());
        }
        // The frame of the HTML report is the vendored XRechnung visualization, which is
        // written for one edition of the semantic model and has nowhere to put a business
        // term of another. A report that framed a page with the payment terms silently
        // missing would be a proof about a document other than the one it displays, so the
        // invoice section says why it is empty instead. The PDF form is this project's own
        // layout and is driven by the registry of the document's own edition, so it needs
        // no such refusal.
        Registry stylesheets = XrImporter.defaultRegistry();
        if (!stylesheets.describes(document.semanticModel())) {
            return ReportHtml.of(outcome, options, ReportInvoice.refused(
                    "the vendored visualization renders " + stylesheets.semanticModel()
                            + " and this document names " + document.semanticModel()));
        }
        // Before anything is rendered, and not after: the rendering is what does not fit,
        // so a report that made it in order to measure it would spend the heap it is
        // trying to save and would lose a verdict that had already been reached.
        int estimate = ReportContent.renderingCharacters(document);
        if (estimate > ReportContent.MAX_INVOICE_CHARACTERS) {
            return ReportHtml.of(outcome, options,
                    ReportInvoice.tooLargeDocument(document.values().size()));
        }
        try {
            return ReportHtml.of(outcome, options, framed(new HtmlRenderer(registry)
                    .renderWithReport(document, options.rendering())));
        } catch (IllegalArgumentException | RenderContentException e) {
            return ReportHtml.of(outcome, options, ReportInvoice.refused(refusal(e)));
        }
    }

    /**
     * Returns the invoice of the HTML form: the rendering for the frame, and what did not
     * reach it.
     *
     * <p>The rendering goes through the XR representation, and that representation carries
     * the business terms of the semantic model and nothing else. A value it has no place
     * for — a subtree of {@code extensions}, a term the loaded registry has no element for,
     * a character XML 1.0 cannot hold — is a value the frame does not show, and a report
     * that showed such a rendering under the digest of the whole document without a word
     * would be a proof about a document other than the one it displays. So the report asks
     * the renderer what it left behind and says it.
     *
     * <p>A rendering larger than {@link ReportContent#MAX_INVOICE_CHARACTERS} is left out
     * altogether, with the sentence that says how large it is and where the invoice is to
     * be had. The page writes the whole rendering into one attribute, escaped character by
     * character, so an invoice of a few megabytes makes a file no reader opens; a report
     * about a large invoice is more useful without the invoice than unopenable with it.
     *
     * <p>That bound is applied to {@link ReportContent#renderingCharacters(SemanticDocument)}
     * before the rendering is made, and this is the second measurement of the same bound,
     * over the rendering that was made. It is kept because the first one is an estimate,
     * and an estimate that came out too low must not be the last word on a page a reader
     * has to open.
     */
    private static ReportInvoice framed(RenderResult result) {
        String rendering = result.html();
        if (rendering.length() > ReportContent.MAX_INVOICE_CHARACTERS) {
            return ReportInvoice.tooLarge(rendering.length());
        }
        return ReportInvoice.of(rendering, result.report().notes().stream()
                .map(ExportNote::toString).toList());
    }

    /**
     * Writes a report as a PDF, with the invoice after it in the same file.
     *
     * @param outcome  what the run came to
     * @param document the document that was judged, or {@code null} where the run built
     *                 none
     * @param options  the language, whether the invoice is part of the report, and the
     *                 moment to print
     * @return the PDF
     * @throws RenderException      if the file could not be written
     * @throws NullPointerException if {@code outcome} or {@code options} is {@code null}
     */
    public byte[] pdf(ValidationOutcome outcome, SemanticDocument document,
                      ReportOptions options) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(options, "options");
        try (PDDocument pdf = new PDDocument()) {
            pdf.setDocumentId(FIXED_DOCUMENT_ID);
            PDDocumentInformation information = new PDDocumentInformation();
            information.setProducer(PRODUCER);
            information.setCreator(PRODUCER);
            pdf.setDocumentInformation(information);
            Fonts fonts = Fonts.embeddedIn(pdf);
            try (Sheet sheet = new Sheet(pdf, options.pageSize(), fonts, Margins.defaults(),
                    Margins.defaults(), Palette.defaults(), null, null,
                    options.rendering().maxPages())) {
                pages(outcome, document, options, sheet);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            pdf.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new RenderException("the report could not be written as a PDF", e);
        }
    }

    /**
     * Draws the report and then the invoice, and closes the sheet with the footers.
     *
     * <p>The invoice is drawn by {@link PdfLayout} on the same sheet, which is what makes
     * the file one document, and it starts on a page of its own so that the report does not
     * end halfway down a page of the invoice. The footer is written once over the whole
     * file and names the report rather than the invoice number the invoice alone would put
     * there: what a reader is holding is a report, and its pages are numbered as one.
     */
    private void pages(ValidationOutcome outcome, SemanticDocument document,
                       ReportOptions options, Sheet sheet) {
        ReportLayout report = new ReportLayout(outcome, options, sheet);
        report.draw();
        if (!wanted(document, options)) {
            finish(report, sheet, options);
            return;
        }
        ReportInvoice invoice = refusal(document).map(ReportInvoice::refused)
                .orElseGet(ReportInvoice::none);
        report.invoiceSection(invoice);
        if (invoice.refusal().isPresent()) {
            finish(report, sheet, options);
            return;
        }
        sheet.newPage();
        new PdfLayout(document, registry, options.language(), sheet, null).drawContent();
        finish(report, sheet, options);
    }

    /**
     * Closes the sheet with the footer of the report on every page, the name of the input
     * held to the room the page count leaves it: the pages are all drawn by now, so the
     * count is known and so is how wide it is.
     */
    private static void finish(ReportLayout report, Sheet sheet, ReportOptions options) {
        String pageOf = ReportWord.PAGE_OF.in(options.language());
        sheet.finish(report.footer(sheet.footerRoom(pageOf)), pageOf);
    }

    /**
     * Returns why this layout will not take the document, before a page of it is begun.
     *
     * <p>The two answers are the two a layout has: an edition no registry of this build
     * describes, and a term no loaded registry knows. Both are asked here rather than
     * caught while drawing, because a page that has been written cannot be taken back, and
     * a report whose invoice breaks off in the middle of a line would be worse than one
     * that says plainly that the invoice was not rendered. The wording is the registry's
     * own, so that the two forms of the report say the same thing about the same document.
     *
     * @param document the document the report is about
     * @return the sentence that stands in place of the invoice, or empty where it renders
     */
    private Optional<String> refusal(SemanticDocument document) {
        if (!registry.describes(document.semanticModel())) {
            return Optional.of("the document names the semantic model "
                    + document.semanticModel() + " and this renderer knows "
                    + registry.semanticModel());
        }
        for (SemanticPath path : document.values().keySet()) {
            for (String id : path.termIds()) {
                if (registry.term(id).isEmpty()) {
                    return Optional.of("the registry does not know the term " + id);
                }
            }
        }
        return Optional.empty();
    }

    /** Tells whether this run has an invoice to show and was asked to show it. */
    private static boolean wanted(SemanticDocument document, ReportOptions options) {
        return document != null && options.includeInvoice();
    }

    /** Returns why a renderer refused a document, in the words of whatever refused it. */
    private static String refusal(RuntimeException e) {
        Throwable cause = e instanceof RenderContentException ? e.getCause() : e;
        String message = cause == null ? null : cause.getMessage();
        return message == null ? e.toString() : message;
    }
}
