package de.bsnsoft.esj.render;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;

/**
 * Reads a rendering the way a reader of it does: as the text that is printed on the pages.
 *
 * <p>The tests of this module ask whether a value of the document is on the page, and the
 * answer is read back out of the PDF with PDFBox itself rather than out of the layout that
 * wrote it — a test that asked the layout what it had drawn would only be asking it to
 * agree with itself.
 *
 * <p>The text is extracted in the order the content stream was written rather than sorted
 * by position, which is what makes a wrapped cell readable: the layout writes every line
 * of a cell before the next cell begins, so a description that took three lines comes back
 * as one run of text instead of with the quantity and the price of its row between its
 * lines.
 *
 * <p>Two forms of the text are offered, because a line break is not a difference a reader
 * sees. {@link #flat(String)} collapses every run of whitespace into one space, which finds
 * a value that was wrapped between two of its words. {@link #tight(String)} removes
 * whitespace altogether, which finds a value whose single long word had to be broken where
 * no space was — an identifier of forty characters in a narrow column, for one.
 */
final class Pdf {

    /**
     * How finely a page is rasterised before a code is looked for on it.
     *
     * <p>Enough that the smallest module of the largest symbol the guideline allows is
     * several pixels wide, which is what a decoder needs to find the three corners.
     */
    private static final int SCAN_DPI = 300;

    /** How far apart two characters may sit and still count as one baseline. */
    private static final float BASELINE_TOLERANCE = 0.5f;

    /** How far two characters may reach into one another before a reader sees it. */
    private static final float OVERLAP_TOLERANCE = 1f;

    /**
     * Ask the decoder to look harder for the corners of a symbol.
     *
     * <p>A page of an invoice is full of small dark shapes, and the quick search of the
     * decoder takes some of them for the corner patterns of a code. A telephone held over
     * the paper sees the code and little else; this is how a whole page is given the same
     * chance.
     */
    private static final Map<DecodeHintType, Object> HARDER =
            Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE);

    private Pdf() {
        throw new AssertionError("no instances");
    }

    /** Returns the text of a rendering with the runs of whitespace collapsed. */
    static String flat(byte[] pdf) {
        return flat(text(pdf));
    }

    /** Returns a text with the runs of whitespace collapsed. */
    static String flat(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    /** Returns a text without any whitespace at all. */
    static String tight(String text) {
        return text.replaceAll("\\s+", "");
    }

    /** Returns the text of a rendering, page by page, as PDFBox extracts it. */
    static String text(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the text of one page of a rendering, counted from one. */
    static String textOfPage(byte[] pdf, int page) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(document);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the text inside a rectangle of a page, which is how a test asks whether a
     * block landed where the geometry of a business letter puts it.
     *
     * @param pdf    the rendering
     * @param page   the page, counted from one
     * @param x      the left edge of the rectangle, in points from the left edge of the page
     * @param y      its top edge, in points below the top edge of the page
     * @param width  how wide it is
     * @param height how tall it is
     * @return the text inside it
     */
    static String textInArea(byte[] pdf, int page, float x, float y, float width,
                             float height) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.addRegion("area", new Rectangle2D.Float(x, y, width, height));
            stripper.extractRegions(document.getPage(page - 1));
            return flat(stripper.getTextForRegion("area"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the runs of text of a page with the band of paper each of them stands on.
     *
     * <p>A test of a page asks two kinds of question: what does it say, and where does it
     * say it. This answers the second one in the measurement a letter is written in —
     * points above the bottom edge of the paper — so that a test can say that nothing of
     * the invoice reaches into the foot the page reserved for the sender's details.
     *
     * <p>The band and the two edges are the box a reader sees the run in, which is what
     * a test asks of when the paper under the page is printed matter of its own.
     *
     * @param pdf  the rendering
     * @param page the page, counted from one
     * @return the runs, in the order the content stream wrote them
     */
    static List<Run> runs(byte[] pdf, int page) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            float height = document.getPage(page - 1).getMediaBox().getHeight();
            Runs runs = new Runs(height);
            runs.setStartPage(page);
            runs.setEndPage(page);
            runs.getText(document);
            return runs.found();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * One run of text of a page.
     *
     * @param text     what it says
     * @param baseline the baseline it sits on, in points above the bottom edge
     * @param top      the top of its glyphs, in points above the bottom edge
     * @param left     its left edge, in points from the left edge of the paper
     * @param right    its right edge, in points from the left edge of the paper
     * @param size     the type size it is set in, in points
     */
    record Run(String text, float baseline, float top, float left, float right, float size) {

        /**
         * Returns the top of the line this run was set on, in points above the bottom edge:
         * the layouts of this module set a line of a size at a top by putting its baseline
         * that size below it, so this is where the block the run begins stands.
         *
         * @return the top of the line
         */
        float lineTop() {
            return baseline + size;
        }
    }

    /** A text stripper that keeps the runs of a page with their place on the paper. */
    private static final class Runs extends PDFTextStripper {

        private final float height;
        private final List<Run> found = new ArrayList<>();

        private Runs(float height) throws IOException {
            this.height = height;
        }

        List<Run> found() {
            return List.copyOf(found);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            float baseline = Float.MAX_VALUE;
            float top = 0;
            float left = Float.MAX_VALUE;
            float right = 0;
            float size = 0;
            for (TextPosition position : positions) {
                if (position.getUnicode().isBlank()) {
                    continue;
                }
                float y = height - position.getY();
                baseline = Math.min(baseline, y);
                top = Math.max(top, y + position.getHeight());
                left = Math.min(left, position.getX());
                right = Math.max(right, position.getX() + position.getWidth());
                // The size the text was set in by its operator, which is the size in
                // points on a page drawn without a scaling matrix; the size in points PDFBox
                // works out from the matrix is rounded to a whole point.
                size = Math.max(size, position.getFontSize());
            }
            if (top > 0) {
                found.add(new Run(flat(text), baseline, top, left, right, size));
            }
        }
    }

    /**
     * Returns what a page draws that is not text: every rule it strokes and every area it
     * fills, each with the box it takes on the paper.
     *
     * <p>The text of a page says what stands on it and {@link #runs(byte[], int)} where; the
     * rules and the filled areas say where one block of a page ends and the next begins — the
     * rule under a title, the grey band of a column header, the modules of a code. A test of
     * the rhythm of a page measures the white between them, and has to read them off the
     * paper for the same reason it reads the text off it.
     *
     * @param pdf  the rendering
     * @param page the page, counted from one
     * @return the marks, in the order the content streams draw them
     */
    static List<Mark> marks(byte[] pdf, int page) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDPage sheet = document.getPage(page - 1);
            Marks marks = new Marks(sheet);
            marks.processPage(sheet);
            return marks.found();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * One thing a page draws that is not text: a stroked rule or a filled area, with the
     * box it takes in points from the lower left corner of the paper. A rule is a line, so
     * its box is as tall as the line is — nothing.
     *
     * @param stroked whether it is a stroke rather than a fill
     * @param left    its left edge
     * @param right   its right edge
     * @param bottom  its lower edge
     * @param top     its upper edge
     */
    record Mark(boolean stroked, float left, float right, float bottom, float top) {
    }

    /** Collects the paths a page strokes and fills, with the box of each. */
    private static final class Marks extends PDFGraphicsStreamEngine {

        private final List<Mark> found = new ArrayList<>();
        private final GeneralPath path = new GeneralPath();

        private Marks(PDPage page) {
            super(page);
        }

        List<Mark> found() {
            return List.copyOf(found);
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            path.moveTo(p0.getX(), p0.getY());
            path.lineTo(p1.getX(), p1.getY());
            path.lineTo(p2.getX(), p2.getY());
            path.lineTo(p3.getX(), p3.getY());
            path.closePath();
        }

        @Override
        public void drawImage(PDImage image) {
            // The layouts of this module draw no picture of their own.
        }

        @Override
        public void clip(int windingRule) {
            // A clip draws nothing.
        }

        @Override
        public void moveTo(float x, float y) {
            path.moveTo(x, y);
        }

        @Override
        public void lineTo(float x, float y) {
            path.lineTo(x, y);
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            path.curveTo(x1, y1, x2, y2, x3, y3);
        }

        @Override
        public Point2D getCurrentPoint() {
            return path.getCurrentPoint();
        }

        @Override
        public void closePath() {
            path.closePath();
        }

        @Override
        public void endPath() {
            path.reset();
        }

        @Override
        public void strokePath() {
            add(true);
        }

        @Override
        public void fillPath(int windingRule) {
            add(false);
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            add(false);
        }

        @Override
        public void shadingFill(COSName shadingName) {
            // A shading is no rule and no block of the layout.
        }

        private void add(boolean stroked) {
            if (path.getCurrentPoint() != null) {
                Rectangle2D box = path.getBounds2D();
                found.add(new Mark(stroked, (float) box.getMinX(), (float) box.getMaxX(),
                        (float) box.getMinY(), (float) box.getMaxY()));
            }
            path.reset();
        }
    }

    /**
     * Returns the characters of a page that are drawn over one another.
     *
     * <p>Two glyphs on one baseline whose boxes overlap are two glyphs a reader cannot
     * read: a label written into the cell of its own figure, a block drawn over another.
     * Set text never overlaps, because the position of a character is the one the
     * advance of the character before it gives it, so anything this finds is a collision
     * between two things the layout drew independently.
     *
     * @param pdf  the rendering
     * @param page the page, counted from one
     * @return one line per pair found, empty where the page reads
     */
    static List<String> overprinted(byte[] pdf, int page) {
        Map<Integer, List<TextPosition>> lines = new TreeMap<>();
        for (TextPosition glyph : glyphs(pdf, page)) {
            lines.computeIfAbsent(Math.round(glyph.getY() / BASELINE_TOLERANCE),
                    baseline -> new ArrayList<>()).add(glyph);
        }
        List<String> found = new ArrayList<>();
        for (List<TextPosition> line : lines.values()) {
            // Sorted by their left edge, two characters that reach into one another are
            // neighbours: anything further along starts further right still.
            line.sort(Comparator.comparing(TextPosition::getX));
            for (int i = 1; i < line.size(); i++) {
                TextPosition left = line.get(i - 1);
                TextPosition right = line.get(i);
                float overlap = left.getX() + left.getWidth() - right.getX();
                if (overlap > OVERLAP_TOLERANCE) {
                    found.add("'" + left.getUnicode() + "' at " + left.getX() + " and '"
                            + right.getUnicode() + "' at " + right.getX() + " on the "
                            + "baseline " + left.getY() + " overlap by " + overlap);
                }
            }
        }
        return List.copyOf(found);
    }

    /** Returns every character of a page that says something, with its box. */
    private static List<TextPosition> glyphs(byte[] pdf, int page) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            Glyphs glyphs = new Glyphs();
            glyphs.setStartPage(page);
            glyphs.setEndPage(page);
            glyphs.getText(document);
            return glyphs.found();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A text stripper that keeps every character of a page with its box. */
    private static final class Glyphs extends PDFTextStripper {

        private final List<TextPosition> found = new ArrayList<>();

        private Glyphs() throws IOException {
        }

        List<TextPosition> found() {
            return List.copyOf(found);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            for (TextPosition position : positions) {
                if (!position.getUnicode().isBlank()) {
                    found.add(position);
                }
            }
        }
    }

    /**
     * Returns the QR codes printed on the pages of a rendering, in page order.
     *
     * <p>They are read the way a payer reads one: the page is rasterised and the picture
     * of it is handed to a decoder — the reader of the same library the renderer asked
     * for the matrix, which is as close to a telephone held over the paper as a build
     * gets. What comes back is the text the code carries, and a page without one
     * contributes nothing.
     *
     * @param pdf the rendering
     * @return the decoded texts, one per code found
     */
    static List<String> codes(byte[] pdf) {
        List<String> found = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image =
                        renderer.renderImageWithDPI(page, SCAN_DPI, ImageType.GRAY);
                try {
                    found.add(new QRCodeReader().decode(new BinaryBitmap(
                                    new HybridBinarizer(new Grey(image))), HARDER).getText());
                } catch (NotFoundException | ChecksumException | FormatException e) {
                    // The page carries no code, which is an answer and not a failure.
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.copyOf(found);
    }

    /**
     * A rasterised page as the decoder wants it: one byte of brightness per pixel. The
     * library's own class for this lives in an artefact this project does not depend on,
     * and a grey image is four lines of it.
     */
    private static final class Grey extends LuminanceSource {

        private final byte[] pixels;

        private Grey(BufferedImage image) {
            super(image.getWidth(), image.getHeight());
            this.pixels = new byte[image.getWidth() * image.getHeight()];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    pixels[y * image.getWidth() + x] = (byte) (image.getRGB(x, y) & 0xff);
                }
            }
        }

        @Override
        public byte[] getRow(int y, byte[] row) {
            byte[] into = row == null || row.length < getWidth() ? new byte[getWidth()] : row;
            System.arraycopy(pixels, y * getWidth(), into, 0, getWidth());
            return into;
        }

        @Override
        public byte[] getMatrix() {
            return pixels;
        }
    }

    /** Returns how many pages a rendering has. */
    static int pages(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the width and the height of a page of a rendering, in points. */
    static float[] pageSize(byte[] pdf, int page) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            var box = document.getPage(page - 1).getMediaBox();
            return new float[] {box.getWidth(), box.getHeight()};
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tells whether a rendering shows a text, wrapped or broken. */
    static boolean shows(byte[] pdf, String value) {
        return shows(text(pdf), value);
    }

    /** Tells whether an extracted text shows a value, wrapped or broken. */
    static boolean shows(String text, String value) {
        return flat(text).contains(flat(value)) || tight(text).contains(tight(value));
    }
}
