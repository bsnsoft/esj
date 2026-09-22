package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.xr.ExportResult;
import de.bsnsoft.esj.xr.XmlFrontDoor;
import de.bsnsoft.esj.xr.XrExporter;
import java.util.Objects;

/**
 * Renders an ESJ document as the HTML of the KoSIT XRechnung visualization.
 *
 * <p>This is one of the two renderings of the project, and its point is that none of it is
 * this project's design; {@link PdfRenderer} is the other, and that one this project laid
 * out itself. The document is written as the semantic XR representation by
 * {@link XrExporter} and handed to the stylesheet {@code xrechnung-html.xsl}, which is
 * vendored unmodified beside this class. The result is a single self-contained HTML file —
 * the style sheet and the two scripts are inlined by the stylesheet itself — with the
 * layout, the labels and the five tabs that recipients of electronic invoices in Germany
 * already know, in German or in English.
 *
 * <h2>What is rendered, and in which terms</h2>
 *
 * <p>The rendering is net, as the semantic model is. It shows the net line amounts, the net
 * unit prices, the VAT breakdown per category and then the totals up to the amount due for
 * payment. It derives nothing: no gross line amount, no gross unit price and no figure that
 * EN 16931-1 does not define, and it writes nothing into a core business term. BT-114 is
 * the invoice rounding amount of the standard and is shown as that. A document may well
 * carry consumer-oriented gross figures — they belong to an extension of the model, they
 * are shown by a renderer that was asked for them, and this one is not that renderer.
 *
 * <p>Every business term of the registry has a place in the layout: a document that carries a
 * value at each of the 203 value paths of the core model and the XRechnung extension renders
 * with every one of them on the page, in both languages, which is a test of this module. What
 * a rendering does not show is what could not reach it — the {@code extensions} subtree above
 * all — and {@link #renderWithReport} names that. An attachment (BT-125) is carried into the
 * page whole, in a hidden element a download link reads out, so a rendering is at least as
 * large as the attachments of the invoice. {@code docs/rendering.md} has the details, and the
 * two places where a document from a stranger decides more than its own text.
 *
 * <h2>Determinism</h2>
 *
 * <p>The same document rendered twice gives the same string. Nothing of the machine, the
 * moment or the run takes part: no timestamp, no producer string and no identifier that
 * counts up. The rendering depends on the document, on the language and on the version of
 * the stylesheet this module ships, and on nothing else.
 *
 * <h2>Untrusted content</h2>
 *
 * <p>An invoice is a document from a stranger, and its text is written into the HTML as
 * text. The serializer escapes it, so a description that contains {@code <script>} or an
 * ampersand reaches the reader as those characters and not as markup; the scripts in the
 * result are the two vendored ones and no other.
 *
 * <p>Two places in the vendored stylesheet write a value of the document somewhere other than
 * into text: an external document location (BT-124) becomes the target of a link, and the
 * download link of an attachment carries BT-122, the media type and the file name into the
 * arguments of a script call inside single quotes. This module does not correct the vendored
 * file, whose bytes stay upstream's; it overrides the one template that writes those two
 * places, in a stylesheet of its own that imports the vendored one. A location whose scheme is
 * not {@code http}, {@code https} or {@code mailto} is written as text rather than as a link,
 * so a {@code javascript:} URI is something to read and not something to follow, and the three
 * values of the script call are escaped for the string literals they stand in.
 *
 * <p>A character that directs the reading order — the right-to-left override and its family —
 * is replaced by a space on the way in, as it is in the PDF rendering, so that the page cannot
 * be made to read differently from the document it was made from.
 *
 * <p>A rendering is still a document written by a stranger. A caller who shows renderings of
 * documents from strangers in a browser serves them from an origin of their own, in a
 * sandboxed frame and under a content security policy, as they would any other document they
 * did not write. {@code docs/rendering.md} says it at length.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class HtmlRenderer {

    private final XrExporter exporter;

    /**
     * Creates a renderer that knows the core model and the XRechnung extension, which is
     * the registry the exporter of {@code esj-xr} uses by default.
     */
    public HtmlRenderer() {
        this.exporter = new XrExporter();
    }

    /**
     * Creates a renderer with a registry of its caller's choosing.
     *
     * <p>The registry decides which terms can be written into the XR representation at
     * all, and therefore which of them the stylesheet can be asked to show. A registry
     * without the XRechnung extension renders a document of core terms and names every
     * value below an extension term in the report of {@link #renderWithReport}.
     *
     * @param registry the registry the export to the XR representation goes by
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    public HtmlRenderer(Registry registry) {
        this.exporter = new XrExporter(Objects.requireNonNull(registry, "registry"));
    }

    /**
     * Renders a document in German.
     *
     * @param document the document to render
     * @return a self-contained HTML document
     * @throws IllegalArgumentException if the document names an edition of the semantic
     *                                  model that the registry of this renderer does not
     *                                  describe
     * @throws RenderException          if the rendering could not be produced
     * @throws NullPointerException     if {@code document} is {@code null}
     */
    public String render(SemanticDocument document) {
        return render(document, RenderOptions.defaults());
    }

    /**
     * Renders a document.
     *
     * @param document the document to render
     * @param options  the language; the page size of the options is for the PDF renderer
     * @return a self-contained HTML document
     * @throws IllegalArgumentException if the document names an edition of the semantic
     *                                  model that the registry of this renderer does not
     *                                  describe
     * @throws RenderException          if the rendering could not be produced
     * @throws NullPointerException     if an argument is {@code null}
     */
    public String render(SemanticDocument document, RenderOptions options) {
        return renderWithReport(document, options).html();
    }

    /**
     * Renders a document and keeps the report of what did not reach the stylesheet.
     *
     * @param document the document to render
     * @param options  the language; the page size of the options is for the PDF renderer
     * @return the rendering and the report
     * @throws IllegalArgumentException if the document names an edition of the semantic
     *                                  model that the registry of this renderer does not
     *                                  describe
     * @throws RenderException          if the rendering could not be produced
     * @throws NullPointerException     if an argument is {@code null}
     */
    public RenderResult renderWithReport(SemanticDocument document, RenderOptions options) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(options, "options");
        ExportResult exported = exporter.toXrWithReport(document);
        String html = KositHtml.transform(
                XmlFrontDoor.parse(Characters.plain(exported.xr())), options.language());
        return new RenderResult(html, exported.report());
    }
}
