package de.bsnsoft.esj.render;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

/**
 * The letterhead and the logo of a branded rendering, drawn under the text of every page.
 *
 * <p>A letterhead is either one page of a PDF or a raster image, and the two are prepared
 * once for the document that will carry them: a PDF page becomes a form object, imported
 * with its own resources — its fonts, its images, its colour spaces — so that what the
 * page of the letterhead drew is what this page draws; an image becomes one image object.
 * Every page of the rendering then references that one object rather than carrying a copy
 * of it, which is what keeps a hundred-page invoice on a letterhead the size of a
 * one-page invoice on the same letterhead.
 *
 * <p>The first page and the pages after it may have letterheads of their own, because a
 * printed letterhead usually does: the address field, the logo and the sender's details
 * on the first sheet, a narrow band on the rest. A template that names only the first
 * gives the same one to all of them; a template that names only the following sheet
 * leaves the first page bare, which is what it asked for.
 *
 * <p>An instance belongs to the document it was prepared for.
 */
final class Backdrop {

    private final PDFormXObject firstForm;
    private final PDFormXObject followingForm;
    private final PDImageXObject firstImage;
    private final PDImageXObject followingImage;
    private final PDImageXObject logo;
    private final RenderTemplate.Logo logoPlacement;

    private Backdrop(PDFormXObject firstForm, PDFormXObject followingForm,
                     PDImageXObject firstImage, PDImageXObject followingImage,
                     PDImageXObject logo, RenderTemplate.Logo logoPlacement) {
        this.firstForm = firstForm;
        this.followingForm = followingForm;
        this.firstImage = firstImage;
        this.followingImage = followingImage;
        this.logo = logo;
        this.logoPlacement = logoPlacement;
    }

    /**
     * Prepares the letterhead and the logo of a template for a document.
     *
     * @param target   the document the pages belong to
     * @param template the template, or {@code null} for a rendering without one
     * @return the backdrop, or {@code null} where nothing is drawn under the text
     * @throws TemplateException if a letterhead or a logo could not be read
     */
    static Backdrop of(PDDocument target, RenderTemplate template) {
        if (template == null || template.letterheadFirst() == null
                && template.letterheadFollowing() == null && template.logo() == null) {
            return null;
        }
        RenderTemplate.Artwork first = template.letterheadFirst();
        RenderTemplate.Artwork following = template.letterheadFollowing();
        LayerUtility layers = new LayerUtility(target);
        PDFormXObject firstForm = form(layers, first);
        PDImageXObject firstImage = image(target, first);
        boolean shared = following == first;
        return new Backdrop(firstForm,
                shared ? firstForm : form(layers, following),
                firstImage,
                shared ? firstImage : image(target, following),
                template.logo() == null ? null
                        : image(target, template.logo().bytes(), "the logo"),
                template.logo());
    }

    /**
     * Draws the letterhead and, where the template asks for it, the logo.
     *
     * @param stream the content stream of a page, before anything else is written to it
     * @param page   the size of that page
     * @param first  whether this is the first page of the rendering
     */
    void paint(PDPageContentStream stream, PDRectangle page, boolean first) {
        PDFormXObject form = first ? firstForm : followingForm;
        PDImageXObject sheet = first ? firstImage : followingImage;
        try {
            if (form != null) {
                stream.saveGraphicsState();
                PDRectangle box = form.getBBox();
                stream.transform(Matrix.getScaleInstance(
                        box.getWidth() == 0 ? 1f : page.getWidth() / box.getWidth(),
                        box.getHeight() == 0 ? 1f : page.getHeight() / box.getHeight()));
                stream.drawForm(form);
                stream.restoreGraphicsState();
            } else if (sheet != null) {
                stream.drawImage(sheet, 0f, 0f, page.getWidth(), page.getHeight());
            }
            if (logo != null && (first || logoPlacement.everyPage())) {
                stream.drawImage(logo, logoPlacement.x(),
                        page.getHeight() - logoPlacement.y() - logoPlacement.height(),
                        logoPlacement.width(), logoPlacement.height());
            }
        } catch (IOException e) {
            throw new RenderException("the letterhead could not be drawn", e);
        }
    }

    private static PDFormXObject form(LayerUtility layers, RenderTemplate.Artwork artwork) {
        if (artwork == null || artwork.kind() != RenderTemplate.Artwork.Kind.PDF) {
            return null;
        }
        try (PDDocument source = Loader.loadPDF(artwork.bytes())) {
            if (artwork.page() > source.getNumberOfPages()) {
                throw new TemplateException("the letterhead has " + source.getNumberOfPages()
                        + (source.getNumberOfPages() == 1 ? " page" : " pages")
                        + " and the template asks for page " + artwork.page());
            }
            return layers.importPageAsForm(source, artwork.page() - 1);
        } catch (IOException e) {
            throw new TemplateException("the letterhead PDF could not be read: "
                    + e.getMessage(), e);
        }
    }

    private static PDImageXObject image(PDDocument target, RenderTemplate.Artwork artwork) {
        if (artwork == null || artwork.kind() != RenderTemplate.Artwork.Kind.IMAGE) {
            return null;
        }
        return image(target, artwork.bytes(), "the letterhead image");
    }

    private static PDImageXObject image(PDDocument target, byte[] bytes, String what) {
        try {
            return PDImageXObject.createFromByteArray(target, bytes, what);
        } catch (IOException e) {
            throw new TemplateException(what + " could not be read: " + e.getMessage(), e);
        }
    }
}
