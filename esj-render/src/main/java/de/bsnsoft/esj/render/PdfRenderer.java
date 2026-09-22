package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

/**
 * Renders an ESJ document as a PDF, laid out by this project with PDFBox.
 *
 * <p>Where {@link HtmlRenderer} hands the document to somebody else's stylesheet, this
 * renderer draws the page itself: one generic layout for every document of the model,
 * driven by the term registry rather than by a template. There is no FOP in it, no
 * HTML-to-PDF engine and no browser — a PDF this project writes, with its fonts embedded,
 * so that the result is the same everywhere.
 *
 * <h2>What is on the page</h2>
 *
 * <p>The parties and the identification of the document, the notes and the delivery, the
 * invoice lines as a table, the allowances and charges of the document, the VAT breakdown
 * per category, the totals, and then the payment instructions, the payee, the tax
 * representative and the supporting documents. Under a final heading stands everything the
 * layout above has no place of its own for, with its label and its semantic path, so that
 * a value of the document is never quietly missing: that every value of every document of
 * the conformance corpus is in its rendering is a test of this module.
 *
 * <p>An attachment (BT-125) is the one exception, and a stated one: its file name, its
 * media type and its size in bytes are printed, its content is not. A few hundred kilobytes
 * of base64 on paper would help nobody, and a PDF that carries another file inside it is
 * the business of the hybrid-PDF work rather than of a rendering.
 *
 * <h2>The rendering is net, and derives nothing</h2>
 *
 * <p>EN 16931-1 core terms keep their net semantics and so does this rendering. It shows
 * the net line amount and the net unit price of every line, the VAT per category as the
 * document states it, and the totals as the document states them, gross among them at the
 * end. It computes nothing: no gross line amount, no gross unit price, no total of its own.
 * BT-114 is the invoice rounding amount of the standard and is labelled as that, never used
 * as a balancing field. A value of an extension is shown under the label of its own term
 * and never in the place of a core term. {@code docs/rendering.md} says it at length.
 *
 * <h2>The file is PDF/A-3b</h2>
 *
 * <p>Every rendering is an archival file: the two faces are embedded, an output intent
 * carries the ICC profile that says how the greys of the page are to be read, and an XMP
 * packet declares part 3 of ISO 19005 at conformance level B and repeats what the
 * information dictionary says, whose title is the invoice number and nothing else. Level B,
 * the readable one, and not level A, which would need a tagged document this layout cannot
 * honestly promise. {@link Pdfa} is what that consists of; a test validates every rendering
 * of the conformance corpus with veraPDF.
 *
 * <h2>A branded rendering</h2>
 *
 * <p>{@link RenderOptions#with(RenderTemplate)} puts a letterhead under the page, a logo on
 * it, a colour scheme over it, the caller's fonts in it and the caller's margins around it,
 * and gives the terms of a model extension the places the template declares for them. The
 * layout underneath is the same one: the same sections in the same order, every value of
 * the document on a page, and the figures of EN 16931-1 still the net ones. A figure a
 * template placed is marked as a figure that was displayed, and the note under the block it
 * stands in says what that means.
 *
 * <h2>Determinism</h2>
 *
 * <p>Rendering the same document twice gives the same bytes. Nothing of the machine, the
 * moment or the run takes part: the producer and the creator are fixed strings, the
 * document carries no creation or modification date and its XMP packet carries none either,
 * its file identifier is a digest of the file rather than a number from the clock, and the
 * embedded font subsets are a function of the text. A rendering is a function of the
 * document, the language, the page size, the template and the version of this module —
 * which is what lets one be checked in, diffed or compared between two runs of a pipeline.
 *
 * <h2>Untrusted content</h2>
 *
 * <p>The text of an invoice is written into the page as text. Nothing of a document becomes
 * an instruction of the PDF: there is no script, no action, no link and no embedded file in
 * what this renderer writes, whatever the invoice says. A character the vendored font has
 * no glyph for is replaced by a question mark rather than raised as an error, because an
 * invoice with one unusual character in a product description still has to be readable.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class PdfRenderer {

    /** What the PDF says produced it. It carries no version and no build for a reason. */
    private static final String PRODUCER = "EN16931 Semantic JSON (esj-render)";

    /** What the PDF says created it, which for a rendering is the same thing. */
    private static final String CREATOR = PRODUCER;

    /**
     * The number the draft identifier is derived from. PDFBox hashes it together with the
     * document information; a clock would make every rendering a different file. What the
     * rendering ends up carrying is a digest of itself; see {@link #saved}.
     */
    private static final long FIXED_DOCUMENT_ID = 0L;

    /** How many bytes of the digest the file identifier is, which is what PDF writes. */
    private static final int IDENTIFIER_BYTES = 16;

    private final Registry registry;

    /**
     * Creates a renderer that knows the core model and the XRechnung extension.
     */
    public PdfRenderer() {
        this(XrImporter.defaultRegistry());
    }

    /**
     * Creates a renderer with a registry of its caller's choosing.
     *
     * <p>The registry decides the semantic data type of a term, and with it how a value is
     * written down — a decimal with a thousands separator, a date in the picture of the
     * language — and it decides the English label of a term. A value whose term the
     * registry does not know is still rendered, as text, under its path.
     *
     * @param registry the registry
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    public PdfRenderer(Registry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Renders a document in German on A4.
     *
     * @param document the document to render
     * @return the PDF
     * @throws IllegalArgumentException if the document names an edition of the semantic
     *                                  model that the registry of this renderer does not
     *                                  describe
     * @throws RenderException          if the rendering could not be produced
     * @throws NullPointerException     if {@code document} is {@code null}
     */
    public byte[] render(SemanticDocument document) {
        return render(document, RenderOptions.defaults());
    }

    /**
     * Renders a document.
     *
     * @param document the document to render
     * @param options  the language, the paper, the branded template and the page bound
     * @return the PDF
     * @throws IllegalArgumentException if the document names an edition of the semantic
     *                                  model that the registry of this renderer does not
     *                                  describe
     * @throws RenderLimitException     if the rendering runs past the page bound of the
     *                                  options
     * @throws TemplateException        if the template could not be used for this document
     * @throws RenderException          if the rendering could not be produced
     * @throws NullPointerException     if an argument is {@code null}
     */
    public byte[] render(SemanticDocument document, RenderOptions options) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(options, "options");
        if (!registry.describes(document.semanticModel())) {
            throw new IllegalArgumentException("the document names the semantic model "
                    + document.semanticModel() + " and this renderer knows "
                    + registry.semanticModel());
        }
        try (PDDocument pdf = new PDDocument()) {
            pdf.setDocumentId(FIXED_DOCUMENT_ID);
            PDDocumentInformation information = new PDDocumentInformation();
            information.setProducer(PRODUCER);
            information.setCreator(CREATOR);
            String title = Pdfa.title(document.value(SemanticPath.of("/BT-1"))
                    .map(SemanticValue::content).orElse(null));
            if (title != null) {
                information.setTitle(title);
            }
            pdf.setDocumentInformation(information);
            Pdfa.declare(pdf, information);
            RenderTemplate template = options.template().orElse(null);
            Layout layout = options.layout().orElseGet(
                    () -> template == null ? Layout.GENERIC : template.layout());
            LetterOptions base = template == null
                    ? LetterOptions.defaults() : template.letter();
            LetterOptions letter = options.paymentCode()
                    .map(base::withPaymentCode).orElse(base);
            Margins first = Margins.defaults();
            Margins following = Margins.defaults();
            if (layout == Layout.LETTER) {
                first = LetterLayout.marginsFirst(letter, options.pageSize());
                following = LetterLayout.marginsFollowing(options.pageSize());
            }
            if (template != null) {
                first = template.marginsFirst(first);
                following = template.marginsFollowing(following);
            }
            Fonts fonts = Fonts.embeddedIn(pdf,
                    template == null ? null : template.regularFont(),
                    template == null ? null : template.boldFont());
            try (Sheet sheet = new Sheet(pdf, options.pageSize(), fonts, first, following,
                    template == null ? Palette.defaults() : template.palette(),
                    Backdrop.of(pdf, template),
                    layout == Layout.LETTER
                            ? PageMarks.of(letter.foldMarks(), letter.holeMark()) : null,
                    options.maxPages())) {
                layout(document, registry, options, sheet, template, layout, letter).draw();
            }
            return saved(pdf);
        } catch (IOException e) {
            throw new RenderException("the document could not be rendered as a PDF", e);
        }
    }

    /** Returns the layout that draws this document on this sheet. */
    private static InvoiceLayout layout(SemanticDocument document, Registry registry,
                                        RenderOptions options, Sheet sheet,
                                        RenderTemplate template, Layout which,
                                        LetterOptions letter) {
        return which == Layout.LETTER
                ? new LetterLayout(document, registry, options.language(), sheet, template,
                        letter)
                : new PdfLayout(document, registry, options.language(), sheet, template);
    }

    /**
     * Writes the file, and gives it an identifier of its own.
     *
     * <p>ISO 32000-1, 14.4 gives {@code /ID} the job of identifying the file's content,
     * and an archive that deduplicates reads it. A number hashed with the information
     * dictionary would not do that here: the dictionary is BT-1 and two constant strings,
     * so the language, the paper, the template and the pages themselves would not reach
     * the identifier and two renderings of one invoice number would be indistinguishable
     * by it. A digest of the file does reach all of them, at the price of writing the
     * file once to have something to hash and once more to carry the answer. Both halves
     * are a function of the rendering, so the result is still the same bytes twice.
     *
     * @param pdf the finished document
     * @return the bytes of the rendering
     * @throws IOException if the document could not be written
     */
    private static byte[] saved(PDDocument pdf) throws IOException {
        byte[] draft = write(pdf);
        COSString identifier = new COSString(digest(draft));
        COSArray both = new COSArray();
        both.add(identifier);
        both.add(identifier);
        pdf.getDocument().getTrailer().setItem(COSName.ID, both);
        return write(pdf);
    }

    private static byte[] write(PDDocument pdf) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        pdf.save(bytes);
        return bytes.toByteArray();
    }

    /** Returns as many bytes of the SHA-256 of these bytes as a file identifier holds. */
    private static byte[] digest(byte[] bytes) {
        try {
            return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(bytes),
                    IDENTIFIER_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime", e);
        }
    }

    /**
     * Returns the registry this renderer reads types and English labels from.
     *
     * @return the registry
     */
    public Registry registry() {
        return registry;
    }
}
