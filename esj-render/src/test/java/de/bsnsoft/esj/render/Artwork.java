package de.bsnsoft.esj.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * The letterhead, the image letterhead and the mark the example templates of this
 * repository are built on.
 *
 * <p>There is no third-party artwork in this project, and there is none in its examples
 * either: what {@code examples/templates/} carries is drawn here, out of one name, one
 * hairline and three squares, in the font this module already vendors. That keeps the
 * examples free of anybody else's picture, and it lets a test say that the files checked
 * in are the files this code draws.
 *
 * <p>The artwork is restrained on purpose, and that is a statement about the rendering
 * rather than about taste: a letterhead of this project is a sender's identity added to
 * the page the layout draws, not a second design laid over it. So it keeps to the head of
 * the sheet, above everything the layout writes — no band of colour behind the text, no
 * tint under the foot, nothing at all on the paper the letter is written on. A branded
 * rendering and a plain one are the same page with a name, a mark and a rule on it.
 *
 * <p>The name is the whole of the text on it. An address, a register number or the word
 * for a kind of document printed on the sheet would be a second sender and a second title
 * beside the ones the rendering writes out of the invoice — and it would contradict them
 * for every document but the one it was written for.
 *
 * <p>Everything here is deterministic. The PDF carries a fixed document identifier and no
 * date, as every rendering of this module does, and the images are drawn with plain
 * geometry — no text, so that no font of the machine takes part in a raster.
 */
final class Artwork {

    /** The sender's name on the example letterhead. Nobody's: an example company. */
    static final String SENDER = "Example GmbH";

    /** The colour of the name, the mark and the accent rule. */
    static final Color BRAND = new Color(0x12, 0x3a, 0x63);

    /** One millimetre in points, which is what a business letter is measured in. */
    private static final float MM = 72f / 25.4f;

    /** Where the text of a letter begins, which is where the artwork begins. */
    private static final float LEFT = 20f * MM;

    /** Where it ends, which is where the mark and the accent rule end. */
    private static final float RIGHT = 200f * MM;

    /** The type size of the name on the first sheet. */
    private static final float NAME_SIZE = 15f;

    /** The type size of the name on the sheets after it. */
    private static final float FOLLOWING_NAME_SIZE = 9.5f;

    /**
     * How far below the top edge the baseline of the name sits on the first sheet.
     *
     * <p>Inside the 45 mm a DIN 5008 letter keeps free above its address field, and well
     * above the sender line the layout writes at the top of that field.
     */
    static final float NAME_BASELINE = 64f;

    /** How far below the top edge it sits on the sheets after it. */
    static final float FOLLOWING_NAME_BASELINE = 30f;

    /** How far below the top edge the accent rule of the first sheet sits. */
    static final float RULE_BELOW_TOP = 86f;

    /** How far below the top edge the accent rule of the following sheets sits. */
    static final float FOLLOWING_RULE_BELOW_TOP = 40f;

    /** How thick that rule is. */
    private static final float RULE_WEIGHT = 0.8f;

    /** The identifier every document this class writes carries, so that it has no clock. */
    private static final long FIXED_DOCUMENT_ID = 0L;

    /** How tall an A4 sheet is, which the zones of the busy letterhead are measured on. */
    private static final float A4_HEIGHT = 841.89f;

    /** How wide it is. */
    private static final float A4_WIDTH = 595.276f;

    /** The contact block the busy letterhead prints at the top right, down to 60 mm. */
    static final Zone CONTACT =
            new Zone(425f, 570f, A4_HEIGHT - 60f * MM, A4_HEIGHT);

    /** The foot it prints along the lower edge, in the lowest 20 mm of the sheet. */
    static final Zone FOOT = new Zone(0f, A4_WIDTH, 0f, 20f * MM);

    /** The coloured shape along its right side, which reaches 62 mm in at half the height. */
    static final Zone SIDE = new Zone(A4_WIDTH - 62f * MM, A4_WIDTH, 0f, A4_HEIGHT);

    /**
     * How far down the head of the second sheet of the busy letterhead is printed on.
     *
     * <p>A real letterhead prints its following sheet too — a narrow band with the name of
     * the sender across it — and a page that repeats its head on such paper has the same
     * question to answer as the first page has.
     */
    static final float FOLLOWING_HEAD_DEPTH = 26f * MM;

    /** The band that second sheet prints across its head. */
    static final Zone FOLLOWING_HEAD =
            new Zone(0f, A4_WIDTH, A4_HEIGHT - FOLLOWING_HEAD_DEPTH, A4_HEIGHT);

    /**
     * A rectangle of printed matter, in points from the bottom left corner of the sheet.
     *
     * <p>Points from the bottom, because that is the measurement text comes back out of a
     * PDF in ({@link Pdf#runs(byte[], int)}), and a test that had to convert one of the two
     * would be a test with a chance to convert it wrongly.
     *
     * @param left   the left edge
     * @param right  the right edge
     * @param bottom the lower edge
     * @param top    the upper edge
     */
    record Zone(float left, float right, float bottom, float top) {

        /**
         * Tells whether a run of text of a page overlaps this rectangle.
         *
         * @param run the run
         * @return whether the two meet, by more than the hair a shared edge is
         */
        boolean holds(Pdf.Run run) {
            float slack = 0.5f;
            return run.right() > left + slack && run.left() < right - slack
                    && run.top() > bottom + slack && run.baseline() < top - slack;
        }
    }

    private Artwork() {
        throw new AssertionError("no instances");
    }

    /**
     * Draws the example letterhead: two A4 pages of white paper, the first with the name
     * of the sender and an accent rule under it, the second with the name a size smaller.
     *
     * @return the PDF
     */
    static byte[] letterhead() {
        try (PDDocument pdf = new PDDocument()) {
            pdf.setDocumentId(FIXED_DOCUMENT_ID);
            PDType0Font bold = font(pdf, "LiberationSans-Bold.ttf");
            sheet(pdf, bold, true);
            sheet(pdf, bold, false);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            pdf.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Draws the example letterhead as an image: white paper with the accent rule of the
     * sheet on it, one pixel per point.
     *
     * <p>An image carries no name. Text in a raster would be text set in a font of the
     * machine that drew it, and this project draws the same picture everywhere; the name
     * belongs to the PDF letterhead, which brings the vendored face with it.
     *
     * @param first whether this is the first sheet, whose rule sits lower
     * @return the PNG
     */
    static byte[] band(boolean first) {
        int width = Math.round(PDRectangle.A4.getWidth());
        int height = Math.round(PDRectangle.A4.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setColor(Color.WHITE);
        canvas.fillRect(0, 0, width, height);
        canvas.setColor(BRAND);
        float y = first ? RULE_BELOW_TOP : FOLLOWING_RULE_BELOW_TOP;
        canvas.fillRect(Math.round(LEFT), Math.round(y), Math.round(RIGHT - LEFT),
                Math.max(1, Math.round(RULE_WEIGHT)));
        canvas.dispose();
        return png(image);
    }

    /**
     * Draws the example mark: three squares of the brand colour, the middle one open,
     * which is a logo in the sense that matters here — an image a template puts at a
     * place and a size.
     *
     * @return the PNG
     */
    static byte[] mark() {
        BufferedImage image = new BufferedImage(160, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = image.createGraphics();
        canvas.setColor(Color.WHITE);
        canvas.fillRect(0, 0, 160, 48);
        canvas.setColor(BRAND);
        canvas.setStroke(new BasicStroke(4f));
        for (int square = 0; square < 3; square++) {
            int x = 4 + square * 56;
            if (square == 1) {
                canvas.drawRect(x + 2, 6, 36, 36);
            } else {
                canvas.fillRect(x, 4, 40, 40);
            }
        }
        canvas.dispose();
        return png(image);
    }

    /**
     * Draws the busy letterhead the geometry tests stand on: one A4 sheet with a contact
     * block at the top right, a printed foot along the lower edge and a coloured shape
     * along the right side.
     *
     * <p>It is the paper the example letterhead is not. Real letterheads print into the
     * corner DIN 5008 gives the information block, print along an edge, and print a foot
     * of their own, and a layout that only ever meets a restrained sheet never finds out
     * whether its margins hold. The zones are {@link #CONTACT}, {@link #FOOT} and
     * {@link #SIDE}, and a test asks of the text that comes back out of the rendering that
     * none of it is inside one of them.
     *
     * <p>Nothing on it is text. Every printed thing is a filled shape that stands for one —
     * a bar for a line of the contact block, a bar for a line of the foot, a square for an
     * emblem — so that every run of text a test reads back out of a page is a run the
     * layout wrote and not one the paper brought.
     *
     * <p>It carries two sheets, as a printed letterhead does. The second is the paper the
     * pages after the first are on: a band across the head ({@link #FOLLOWING_HEAD}) and
     * the same printed foot ({@link #FOOT}). A layout that only ever meets a blank second
     * sheet never finds out whether its own head and its page footer keep to the white the
     * template declared.
     *
     * @return the PDF, two A4 pages
     */
    static byte[] busyLetterhead() {
        try (PDDocument pdf = new PDDocument()) {
            pdf.setDocumentId(FIXED_DOCUMENT_ID);
            float height = PDRectangle.A4.getHeight();
            float width = PDRectangle.A4.getWidth();
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                wedge(content, width, height);
                for (int line = 0; line < 5; line++) {
                    float y = height - 40f - line * 26f;
                    fill(content, CONTACT.left() + 14f, y, 118f, 7f);
                    fill(content, CONTACT.left(), y - 1f, 9f, 9f);
                }
                foot(content);
            }
            PDPage following = new PDPage(PDRectangle.A4);
            pdf.addPage(following);
            try (PDPageContentStream content = new PDPageContentStream(pdf, following)) {
                fill(content, 40f, height - FOLLOWING_HEAD_DEPTH + 12f, 240f, 10f);
                fill(content, 440f, height - FOLLOWING_HEAD_DEPTH + 10f, 40f, 14f);
                foot(content);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            pdf.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Draws the foot both sheets of the busy letterhead print along their lower edge. */
    private static void foot(PDPageContentStream content) throws IOException {
        fill(content, 40f, 45f, 210f, 8f);
        fill(content, 40f, 27f, 160f, 8f);
        fill(content, 500f, 14f, 40f, 34f);
    }

    /** Draws the coloured shape along the right side, deepest at half the height. */
    private static void wedge(PDPageContentStream content, float width, float height)
            throws IOException {
        content.setNonStrokingColor(0.42f, 0.62f, 0.72f);
        content.moveTo(width, 0);
        content.lineTo(width, height);
        content.lineTo(width - 95f, height);
        content.lineTo(SIDE.left(), height / 2f);
        content.lineTo(width - 95f, 0);
        content.closePath();
        content.fill();
    }

    private static void sheet(PDDocument pdf, PDType0Font bold, boolean first)
            throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        float height = PDRectangle.A4.getHeight();
        try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
            write(content, SENDER, LEFT,
                    height - (first ? NAME_BASELINE : FOLLOWING_NAME_BASELINE), bold,
                    first ? NAME_SIZE : FOLLOWING_NAME_SIZE);
            fill(content, LEFT,
                    height - (first ? RULE_BELOW_TOP : FOLLOWING_RULE_BELOW_TOP),
                    RIGHT - LEFT, RULE_WEIGHT);
        }
    }

    private static void fill(PDPageContentStream content, float x, float y, float width,
                             float height) throws IOException {
        content.setNonStrokingColor(BRAND.getRed() / 255f, BRAND.getGreen() / 255f,
                BRAND.getBlue() / 255f);
        content.addRect(x, y, width, height);
        content.fill();
    }

    private static void write(PDPageContentStream content, String text, float x, float y,
                              PDType0Font face, float size) throws IOException {
        content.beginText();
        content.setNonStrokingColor(BRAND.getRed() / 255f, BRAND.getGreen() / 255f,
                BRAND.getBlue() / 255f);
        content.setFont(face, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private static PDType0Font font(PDDocument pdf, String name) throws IOException {
        TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(
                Corpus.bytes("/de/bsnsoft/esj/render/fonts/" + name)));
        return PDType0Font.load(pdf, ttf, true);
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
