package de.bsnsoft.esj.render.internal;

import de.bsnsoft.esj.render.RenderEngineException;
import de.bsnsoft.esj.render.RenderException;
import de.bsnsoft.esj.render.TemplateException;
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
 * <p>A character that ends a line is no exception either. A value may carry line breaks,
 * and a block breaks its lines where they are, while the page footer, a figure of the
 * totals and a text measured for the width of a cell are one line, with a space where the
 * value ended one ({@link Face#line(String)}). Neither form hands a line end to the font,
 * which has no glyph for one.
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
                throw new RenderEngineException("the font " + resource + " is not on the classpath");
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
                throw new RenderEngineException("a vendored font could not be embedded", e);
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
         * Returns a text as this face writes it in a block: every line end as a line feed,
         * every control character and every character that directs the reading order as a
         * space, as {@link Characters#plain(String)} says, the tabulator as a space, and
         * every character this face has no glyph for as a question mark.
         *
         * <p>The line feed is the one character left in that has no glyph, because a block
         * breaks its lines at it before anything is measured ({@link Sheet#wrap}). A text
         * that stands on one line is asked for with {@link #line(String)} instead.
         *
         * @param text the text of the document
         * @return the text that can be written with this face, line by line
         */
        String showable(String text) {
            String plain = Characters.plain(text);
            boolean clean = true;
            for (int i = 0; i < plain.length() && clean; i++) {
                char c = plain.charAt(i);
                clean = c == '\n' || (c >= ' ' && (c < 0x7f || has(plain.codePointAt(i))));
            }
            if (clean) {
                return plain;
            }
            StringBuilder shown = new StringBuilder(plain.length());
            int i = 0;
            while (i < plain.length()) {
                int codePoint = plain.codePointAt(i);
                if (codePoint == '\n') {
                    shown.append('\n');
                } else if (codePoint < ' ') {
                    // The tabulator: nothing else below the space is left by now.
                    shown.append(' ');
                } else if (has(codePoint)) {
                    shown.appendCodePoint(codePoint);
                } else {
                    shown.append(REPLACEMENT);
                }
                i += Character.charCount(codePoint);
            }
            return shown.toString();
        }

        /**
         * Returns a text as this face writes it on one line: as {@link #showable(String)}
         * does, with a space where a line ends. The page footer, a figure of the totals and
         * the compact head of a following page are one line each, whatever the document
         * wrote into the value they show.
         *
         * @param text the text of the document
         * @return the text that can be written with this face on one line
         */
        String line(String text) {
            return showable(text).replace('\n', ' ');
        }

        /**
         * Returns how wide a text is at a font size, in points.
         *
         * @param text the text, already passed through {@link #showable(String)} or
         *             {@link #line(String)}
         * @param size the font size in points
         * @return the width in points
         * @throws RenderException if the text cannot be measured with this face
         */
        float width(String text, float size) {
            Float known = widths.get(text);
            if (known == null) {
                // A line feed is the one character a showable text keeps without a glyph,
                // and a text that is measured is a line: one that still carries a line
                // feed is measured as the line it is written as, with a space there.
                known = width(text.indexOf('\n') < 0 ? text : text.replace('\n', ' '));
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
                throw new RenderEngineException("a text could not be measured: " + e.getMessage(),
                        e);
            }
        }

        private boolean has(int codePoint) {
            return glyphs.computeIfAbsent(codePoint, point -> cmap.getGlyphId(point) != 0);
        }
    }
}
