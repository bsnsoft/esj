package de.bsnsoft.esj.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * The two faces a PDF rendering is written in, embedded in the document that uses them.
 *
 * <p>The faces are the regular and the bold weight of Liberation Sans, vendored in this
 * module under the SIL Open Font License; {@code fonts/README.md} beside them records the
 * release and the digests. They are embedded rather than referenced, and no standard-14
 * font takes part in a rendering: a standard-14 font is not embedded, what it can show
 * depends on the machine the document is opened on, and an invoice that says
 * {@code Grünstraße} has to say it everywhere.
 *
 * <p>Only the glyphs a document uses are embedded. The subset is a function of the text
 * and of the font file — PDFBox derives even the six-letter subset tag from the set of
 * glyphs — so the same document embeds the same bytes on every run, which is what lets a
 * rendering be compared byte for byte.
 *
 * <h2>A character the font does not have</h2>
 *
 * <p>An invoice is a document from a stranger and may carry any character at all, while a
 * font carries a few thousand. A code point this face has no glyph for is replaced by a
 * question mark before it reaches the page, because the alternative is an exception in the
 * middle of a rendering: a renderer that refused an invoice over one character in a
 * product description would be worse than useless. The replacement is visible, which is
 * the point — a reader sees that something was there.
 *
 * <p>An instance belongs to the document it was created for and is used by one rendering,
 * on one thread.
 */
final class Fonts {

    /** Where the vendored faces sit on the classpath of this module. */
    private static final String DIRECTORY = "/de/bsnsoft/esj/render/fonts/";

    /** The bytes of the two faces, read once; a face is parsed per document from them. */
    private static final byte[] REGULAR_BYTES = bytes(DIRECTORY + "LiberationSans-Regular.ttf");

    /** The bytes of the bold face. */
    private static final byte[] BOLD_BYTES = bytes(DIRECTORY + "LiberationSans-Bold.ttf");

    /** What stands in for a code point the face has no glyph for. */
    private static final char REPLACEMENT = '?';

    /**
     * How many measured texts a face remembers. Measuring costs a pass over the string and
     * the same words are measured again for every line of every row, so remembering them
     * is worth it; remembering all of them would make the cache as large as the invoice,
     * and an invoice can be eighty megabytes.
     */
    private static final int WIDTH_CACHE_LIMIT = 4096;

    private final Face regular;
    private final Face bold;

    private Fonts(Face regular, Face bold) {
        this.regular = regular;
        this.bold = bold;
    }

    /**
     * Embeds the two faces in a document.
     *
     * @param document the document that will use them
     * @return the faces
     * @throws RenderException if a face could not be read or embedded
     */
    static Fonts embeddedIn(PDDocument document) {
        return embeddedIn(document, null, null);
    }

    /**
     * Embeds two faces in a document: the ones a branded template brings, or the vendored
     * ones where it brings none.
     *
     * <p>A template that brings a face brings both weights, and it brings TrueType files:
     * those are what this module embeds, and a font in another container is refused here
     * rather than silently replaced by the vendored one. Which licence a caller's font is
     * under is the caller's matter, and {@code docs/templates.md} says what that licence
     * has to permit.
     *
     * @param document the document that will use them
     * @param regular  the regular face, or {@code null} for the vendored one
     * @param bold     the bold face, or {@code null} for the vendored one
     * @return the faces
     * @throws TemplateException if a face a template brought could not be embedded
     * @throws RenderException   if a vendored face could not be read or embedded
     */
    static Fonts embeddedIn(PDDocument document, byte[] regular, byte[] bold) {
        return new Fonts(
                regular == null ? Face.load(document, REGULAR_BYTES) : brought(document, regular),
                bold == null ? Face.load(document, BOLD_BYTES) : brought(document, bold));
    }

    /** Embeds a face a template brought, and says so where the file is not one. */
    private static Face brought(PDDocument document, byte[] bytes) {
        try {
            return Face.load(document, bytes);
        } catch (RenderException e) {
            throw new TemplateException("fonts: a face this template brings is not a"
                    + " TrueType font this renderer can embed", e);
        }
    }

    /**
     * Returns the regular face, which carries the text of a rendering.
     *
     * @return the regular face
     */
    Face regular() {
        return regular;
    }

    /**
     * Returns the bold face, which carries the headings, the table headers and the amount
     * due for payment.
     *
     * @return the bold face
     */
    Face bold() {
        return bold;
    }

    private static byte[] bytes(String resource) {
        try (InputStream in = Fonts.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new RenderException("the font " + resource + " is not on the classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One face: the embedded font and the character map that says what it can show. */
    static final class Face {

        private final PDType0Font font;
        private final CmapLookup cmap;
        private final Map<Integer, Boolean> glyphs = new HashMap<>();
        private final Map<String, Float> widths = new HashMap<>();

        private Face(PDType0Font font, CmapLookup cmap) {
            this.font = font;
            this.cmap = cmap;
        }

        private static Face load(PDDocument document, byte[] bytes) {
            try {
                TrueTypeFont ttf = new TTFParser().parse(new RandomAccessReadBuffer(bytes));
                return new Face(PDType0Font.load(document, ttf, true),
                        ttf.getUnicodeCmapLookup());
            } catch (IOException e) {
                throw new RenderException("a vendored font could not be embedded", e);
            }
        }

        /**
         * Returns the embedded font, for the content stream.
         *
         * @return the font
         */
        PDType0Font font() {
            return font;
        }

        /**
         * Returns the text with every character this face cannot show replaced by a
         * question mark, and every control character by a space. A line feed is left
         * alone: the layout splits lines on it before anything is measured.
         *
         * <p>A character that directs the reading order counts as a control character
         * here, whether or not the face has a glyph for it. The right-to-left override
         * and its family are invisible and they change what a text says without changing
         * what it is: a file name that reads one way on the page and another in the
         * document it was taken from. A rendering that showed the text of an invoice as
         * something other than the text the invoice stores would be the wrong kind of
         * faithful, so those characters become spaces like the other controls.
         *
         * @param text the text of the document
         * @return the text that can be written with this face
         */
        String showable(String text) {
            boolean clean = true;
            for (int i = 0; i < text.length() && clean; i++) {
                char c = text.charAt(i);
                clean = c >= ' ' && c != 0x7f
                        && (c < 0x80
                                || (!Characters.directional(c)
                                        && has(text.codePointAt(i))));
            }
            if (clean) {
                return text;
            }
            StringBuilder shown = new StringBuilder(text.length());
            int i = 0;
            while (i < text.length()) {
                int codePoint = text.codePointAt(i);
                int step = Character.charCount(codePoint);
                if (codePoint == '\n') {
                    shown.append('\n');
                } else if (codePoint < ' ' || codePoint == 0x7f
                        || Characters.directional(codePoint)) {
                    shown.append(' ');
                } else if (has(codePoint)) {
                    shown.appendCodePoint(codePoint);
                } else {
                    shown.append(REPLACEMENT);
                }
                i += step;
            }
            return shown.toString();
        }

        /**
         * Returns how wide a text is at a font size, in points.
         *
         * @param text the text, already passed through {@link #showable(String)}
         * @param size the font size in points
         * @return the width in points
         * @throws RenderException if the text cannot be measured with this face
         */
        float width(String text, float size) {
            Float known = widths.get(text);
            if (known == null) {
                known = width(text);
                if (widths.size() < WIDTH_CACHE_LIMIT) {
                    widths.put(text, known);
                }
            }
            return known * size / 1000f;
        }

        private float width(String text) {
            try {
                return font.getStringWidth(text);
            } catch (IOException | IllegalArgumentException e) {
                throw new RenderException("a text could not be measured: " + e.getMessage(), e);
            }
        }

        private boolean has(int codePoint) {
            return glyphs.computeIfAbsent(codePoint, point -> cmap.getGlyphId(point) != 0);
        }
    }
}
