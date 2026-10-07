package de.bsnsoft.esj.render.internal;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.render.Layout;
import de.bsnsoft.esj.render.RenderEngineException;
import de.bsnsoft.esj.render.RenderOptions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

/**
 * Draws one invoice as a PDF: the engine behind {@link de.bsnsoft.esj.render.PdfRenderer},
 * which checks its arguments and hands them here.
 */
public final class InvoicePdf {

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

    private InvoicePdf() {
        throw new AssertionError("no instances");
    }

    /**
     * Renders a document whose edition the registry describes.
     *
     * @param document the document
     * @param registry the registry the layout reads types and labels from
     * @param options  the options of the rendering
     * @param template the parsed template, or {@code null} for an unbranded rendering
     * @return the PDF
     */
    public static byte[] render(SemanticDocument document, Registry registry,
                                RenderOptions options, Template template) {
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
            Layout layout = options.layout()
                    .or(() -> template == null ? Optional.empty() : template.layout())
                    .orElse(RenderOptions.DEFAULT_LAYOUT);
            LetterOptions base = template == null
                    ? LetterOptions.defaults() : template.letter();
            LetterOptions letter = switch (options.paymentCode()) {
                case TEMPLATE -> base;
                case DRAW -> base.withPaymentCode(true);
                case OMIT -> base.withPaymentCode(false);
            };
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
            throw new RenderEngineException("the document could not be rendered as a PDF", e);
        }
    }

    /** Returns the layout that draws this document on this sheet. */
    private static InvoiceLayout layout(SemanticDocument document, Registry registry,
                                        RenderOptions options, Sheet sheet,
                                        Template template, Layout which,
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

}
