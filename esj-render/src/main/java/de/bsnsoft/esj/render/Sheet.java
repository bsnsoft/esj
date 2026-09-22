package de.bsnsoft.esj.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * A stack of pages with a cursor, and the few things that may be drawn on it.
 *
 * <p>This is the whole of the typesetting of this module: a page has a margin, the cursor
 * runs down it, a block that does not fit starts a new page, and a table that spills over
 * repeats its header at the top of the next one. There is no float, no column balancing
 * and no widow control, because an invoice needs none of them and every one of them would
 * be a decision this project would then have to defend.
 *
 * <p>Nothing here knows what an invoice is. What it knows is text, a line of a table and a
 * horizontal rule; {@link PdfLayout} is where those become a seller, a line item and a
 * total.
 *
 * <p>An instance writes one document, on one thread, and is closed when the content is
 * complete. The footers are written after that, over the finished pages, because a page
 * number of a page count cannot be written before the count is known.
 */
final class Sheet implements AutoCloseable {

    /** How much taller a line is than the size of the type in it. */
    static final float LINE_SPACING = 1.22f;

    /** How far a rule moves the cursor down, which a caller adding one up needs. */
    static final float RULE_HEIGHT = 1f;

    /** One millimetre in PostScript points, which is what printed matter is measured in. */
    static final float MM = 72f / 25.4f;

    /** The type size of the compact head of a page after the first. */
    static final float HEAD_SIZE = 8f;

    /** The air between that head and the rule under it, as the title of page one keeps. */
    private static final float HEAD_RULE_GAP = 3f;

    /**
     * The air under that rule, before the text of the page begins.
     *
     * <p>It is the air a section heading leaves under its own rule, so that whatever
     * begins a page after the first — the repeated header of a table, the heading of a
     * section — stands at the distance from the rule above it that it stands at on page
     * one.
     */
    private static final float HEAD_GAP_BELOW = 4f;

    private final PDDocument document;
    private final PageSize size;
    private final Fonts fonts;
    private final Margins following;
    private final Palette palette;
    private final Backdrop backdrop;
    private final PageMarks marks;
    private final int maxPages;
    private final List<PDPage> pages = new ArrayList<>();

    private PDPageContentStream stream;
    private float cursor;
    private Margins margins;

    /** The margins of the first page, which a layout may deepen at the foot. */
    private Margins first;

    /** How much of that bottom margin the layout reserved for furniture of its own. */
    private float reserved;

    /** How much of the top of a page after the first the compact head takes, or zero. */
    private float head;
    private Runnable onNewPage = () -> {
        // Nothing repeats at the top of a page until a table asks for it.
    };

    /**
     * Opens a sheet with its first page.
     *
     * @param document  the document the pages belong to
     * @param size      the paper
     * @param fonts     the faces embedded in that document
     * @param first     the margins of the first page
     * @param following the margins of every page after it
     * @param palette   the colours the sheet draws in
     * @param backdrop  what is drawn under the text of every page, or {@code null}
     * @param marks     the fold and punch marks printed in the left margin
     * @param maxPages  how many pages this rendering may have
     * @throws RenderException      if the first page could not be opened
     * @throws RenderLimitException if {@code maxPages} is less than one
     */
    Sheet(PDDocument document, PageSize size, Fonts fonts, Margins first, Margins following,
          Palette palette, Backdrop backdrop, PageMarks marks, int maxPages) {
        this.document = document;
        this.size = size;
        this.fonts = fonts;
        this.first = first;
        this.following = following;
        this.palette = palette;
        this.backdrop = backdrop;
        this.marks = marks;
        this.maxPages = maxPages;
        this.margins = first;
        newPage();
    }

    /** Returns the x of the left edge of the text. */
    float left() {
        return margins.left();
    }

    /** Returns the x of the right edge of the text. */
    float right() {
        return size.width() - margins.right();
    }

    /** Returns how wide the text is. */
    float width() {
        return right() - left();
    }

    /** Returns how tall the paper is, which a block placed from the top edge needs. */
    float pageHeight() {
        return size.height();
    }

    /** Returns the y the next block starts at. */
    float y() {
        return cursor;
    }

    /**
     * Returns the y the text of this page stops at, which the page footer sits under. A
     * band that belongs at the foot of a letter is anchored here rather than drawn at the
     * cursor.
     */
    float bottom() {
        return margins.bottom();
    }

    /**
     * Returns how tall a band the first page is able to reserve at its foot.
     *
     * <p>Half of what that page has for text, and not more: a page whose foot is taller
     * than its letter is a sender with more to say about itself than about the invoice,
     * and the layout that asks for the band decides what to do where it is not given one
     * ({@link #reserveAtFoot(float)}).
     *
     * @return the height, which is zero on a page with no room at all
     */
    float roomAtFoot() {
        return Math.max((size.height() - first.top() - first.bottom()) / 2f, 0f);
    }

    /**
     * Reserves a band at the foot of the first page for furniture the layout draws there
     * itself, and keeps the text of that page out of it.
     *
     * <p>A business letter carries the sender's own details in the foot of its first
     * page. They are printed matter rather than a block of the letter: they stand at a
     * place the paper gives them, the text of the page stops above them, and the pages
     * after the first carry the page footer alone. So the layout measures them before it
     * fills the page and asks for the band here; the bottom margin of the first page
     * deepens by it, and the page footer stays where it sits on every other page.
     *
     * <p>The band is granted whole or not at all. A band cut to the room left would be a
     * band the caller draws outside of — over the letter it was about to write — so a
     * height beyond {@link #roomAtFoot()} is refused instead, and the caller asks before
     * it commits.
     *
     * @param height how tall the band is
     * @return the y it stands on, which is where the text of the page stopped before
     * @throws IllegalStateException    if the sheet has opened a second page already
     * @throws IllegalArgumentException if the band is taller than {@link #roomAtFoot()}
     */
    float reserveAtFoot(float height) {
        if (pages.size() > 1) {
            throw new IllegalStateException(
                    "the foot of the first page is reserved before the page is filled");
        }
        if (height > roomAtFoot()) {
            throw new IllegalArgumentException("a band of " + height + " points is more"
                    + " than the foot of the first page can hold, which is " + roomAtFoot());
        }
        float floor = first.bottom();
        if (height > 0) {
            reserved = height;
            first = new Margins(first.left(), first.right(), first.top(), floor + height);
            margins = first;
        }
        return floor;
    }

    /**
     * Reserves the top of every page after the first for the compact head that names the
     * document, and keeps the text of those pages under it.
     *
     * <p>The head is furniture of a page, as the page footer is, and the pages it stands
     * on are pages a letterhead prints its own head on. So it stands <em>under</em> the
     * top margin rather than inside it: the margin is the one distance a template states
     * about the head of its paper, and furniture of the layout written above that line
     * would be written over whatever the paper prints there. The text of the page begins
     * under the head, which is what keeps the two apart.
     *
     * <p>It is reserved before the second page is opened, because a page that is already
     * filled cannot give the room back. {@link #finish(String, String, String)} writes the
     * head into it.
     *
     * @throws IllegalStateException if the sheet has opened a second page already
     */
    void headOnFollowingPages() {
        if (pages.size() > 1) {
            throw new IllegalStateException(
                    "the head of the pages after the first is reserved before they are opened");
        }
        head = headHeight();
    }

    /** Returns how much of the top of a page a compact head and its rule take. */
    static float headHeight() {
        return lineHeight(HEAD_SIZE) + HEAD_RULE_GAP + RULE_HEIGHT + HEAD_GAP_BELOW;
    }

    /**
     * Returns how tall the text of a page may be, from the top margin to the bottom.
     *
     * <p>Where the first page and the pages after it keep different margins, this is the
     * smaller of the two. It is asked in order to decide whether a block can ever fit on
     * a page of its own, and the honest answer to that is the answer for the shorter page.
     */
    float usableHeight() {
        return size.height() - Math.max(first.top() + first.bottom(),
                following.top() + following.bottom() + head);
    }

    /**
     * Returns how tall the text of the next page may be.
     *
     * <p>It is asked in order to decide whether a block that does not fit here could be
     * moved whole, and the page it would be moved to is a page after the first. Where a
     * layout keeps a tall first page — a business letter begins below the address field —
     * that is more room than {@link #usableHeight()} reports, and moving the block is the
     * right answer rather than cutting it.
     *
     * @return the height of the text of a page after the first
     */
    float nextPageHeight() {
        return size.height() - following.top() - following.bottom() - head;
    }

    /**
     * Returns how many pages this sheet has opened, which is how a block that drew
     * furniture beside itself tells whether it is still on the page it drew it on.
     *
     * @return the number of pages so far
     */
    int pages() {
        return pages.size();
    }

    /** Returns the faces of this sheet. */
    Fonts fonts() {
        return fonts;
    }

    /** Returns the colours this sheet draws in. */
    Palette palette() {
        return palette;
    }

    /**
     * Moves the cursor down.
     *
     * @param height how far, in points
     */
    void down(float height) {
        cursor -= height;
    }

    /**
     * Tells whether a block of a height still fits on the current page.
     *
     * @param height the height of the block
     * @return {@code true} if it fits
     */
    boolean fits(float height) {
        return cursor - height >= margins.bottom();
    }

    /**
     * Returns how many lines of a height still fit on this page, which is what a table
     * asks before it decides how much of a row it can draw here.
     *
     * @param lineHeight the height of one line
     * @return the number of whole lines, which may be zero
     */
    int roomInLines(float lineHeight) {
        return (int) Math.floor((cursor - margins.bottom()) / lineHeight);
    }

    /**
     * Starts a new page where a block of a height does not fit on this one.
     *
     * @param height the height of the block that is about to be drawn
     */
    void require(float height) {
        if (!fits(height)) {
            newPage();
        }
    }

    /**
     * Sets what is drawn at the top of every page this sheet opens from now on, which is
     * how a table repeats its header.
     *
     * @param header what to draw, or {@code null} for nothing
     */
    void onNewPage(Runnable header) {
        this.onNewPage = header == null ? () -> {
            // Nothing repeats.
        } : header;
    }

    /**
     * Opens a new page, draws the letterhead under it and runs whatever repeats at the
     * top of one.
     *
     * @throws RenderLimitException if the page would be past the bound of this rendering
     */
    void newPage() {
        if (pages.size() >= maxPages) {
            throw new RenderLimitException("this rendering reached the bound of " + maxPages
                    + (maxPages == 1 ? " page" : " pages") + " this run allows");
        }
        boolean isFirst = pages.isEmpty();
        margins = isFirst ? first : following;
        try {
            if (stream != null) {
                stream.close();
            }
            PDRectangle box = new PDRectangle(size.width(), size.height());
            PDPage page = new PDPage(box);
            document.addPage(page);
            pages.add(page);
            stream = new PDPageContentStream(document, page);
            if (backdrop != null) {
                backdrop.paint(stream, box, isFirst);
            }
            if (marks != null) {
                marks.paint(this, size);
            }
            cursor = size.height() - margins.top() - (isFirst ? 0f : head);
        } catch (IOException e) {
            throw new RenderException("a page could not be opened", e);
        }
        onNewPage.run();
    }

    /**
     * Returns how tall a line of type of a size is.
     *
     * @param size the type size in points
     * @return the line height in points
     */
    static float lineHeight(float size) {
        return size * LINE_SPACING;
    }

    /**
     * Breaks a text into the lines it takes at a width. Line feeds of the text are kept;
     * everything else is broken at spaces, and a single word that is wider than the
     * column is broken where it has to be rather than run into the next column.
     *
     * @param text  the text, already passed through the face
     * @param face  the face it will be written in
     * @param size  the type size
     * @param width the width available
     * @return the lines, at least one
     */
    static List<String> wrap(String text, Fonts.Face face, float size, float width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            wrapParagraph(paragraph, face, size, width, lines);
        }
        return lines;
    }

    /**
     * Returns how wide the widest piece of a text is that {@link #wrap} cannot break: a
     * word, or a part of one that a hyphen or a slash ends.
     *
     * <p>A caller that has to set a text without breaking a word asks this what width that
     * takes. Anything narrower wraps inside a word, which is what a column header must
     * never do.
     *
     * @param text the text, already passed through the face
     * @param face the face it will be written in
     * @param size the type size
     * @return the width in points
     */
    static float unbreakable(String text, Fonts.Face face, float size) {
        float widest = 0;
        for (String paragraph : text.split("\n", -1)) {
            for (String piece : pieces(paragraph)) {
                widest = Math.max(widest, face.width(piece.stripTrailing(), size));
            }
        }
        return widest;
    }

    /**
     * Writes one line of text. The whole line is one text-showing operation, so a reader
     * and a text extractor see it as one run.
     *
     * @param text     the line
     * @param x        where it starts
     * @param baseline the baseline it sits on
     * @param face     the face
     * @param size     the type size
     * @param ink      the colour to write it in
     */
    void show(String text, float x, float baseline, Fonts.Face face, float size, Ink ink) {
        if (text.isEmpty()) {
            return;
        }
        try {
            stream.beginText();
            colour(ink);
            stream.setFont(face.font(), size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(text);
            stream.endText();
        } catch (IOException e) {
            throw new RenderException("a line of text could not be written", e);
        }
    }

    /**
     * Returns how tall a text is when it is wrapped to a width.
     *
     * @param text  the text
     * @param face  the face
     * @param size  the type size
     * @param width the width available
     * @return the height in points
     */
    static float measure(String text, Fonts.Face face, float size, float width) {
        return wrap(face.showable(text), face, size, width).size() * lineHeight(size);
    }

    /**
     * Writes a block of text at a y of this page, wrapped to a width, without touching
     * the cursor and without a page break. This is what a caller uses who has already
     * made room for the block — two columns beside each other, for one.
     *
     * @param text  the text
     * @param x     where it starts
     * @param top   the top of the block
     * @param width the width available
     * @param face  the face
     * @param size  the type size
     * @param ink   the colour to write it in
     * @return how tall the block turned out
     */
    float blockAt(String text, float x, float top, float width, Fonts.Face face, float size,
                  Ink ink) {
        List<String> lines = wrap(face.showable(text), face, size, width);
        float baseline = top - size;
        for (String line : lines) {
            show(line, x, baseline, face, size, ink);
            baseline -= lineHeight(size);
        }
        return lines.size() * lineHeight(size);
    }

    /**
     * Writes a block of text at the cursor, wrapped to a width, and moves the cursor
     * below it. A block taller than what is left of the page continues on the next one,
     * line by line, so that a long description cannot run off the paper.
     *
     * @param text  the text
     * @param x     where it starts
     * @param width the width available
     * @param face  the face
     * @param size  the type size
     * @param ink   the colour to write it in
     * @return how tall the block turned out
     */
    float block(String text, float x, float width, Fonts.Face face, float size, Ink ink) {
        List<String> lines = wrap(face.showable(text), face, size, width);
        float height = lineHeight(size);
        for (String line : lines) {
            require(height);
            show(line, x, cursor - size, face, size, ink);
            cursor -= height;
        }
        return lines.size() * height;
    }

    /**
     * Draws a hairline rule across the text width at the cursor and moves the cursor
     * below it.
     *
     * @param ink the colour of the rule
     */
    void rule(Ink ink) {
        rule(left(), right(), cursor, ink);
        cursor -= RULE_HEIGHT;
    }

    /** Draws a hairline rule across the text width in the rule colour of this sheet. */
    void rule() {
        rule(palette.rule());
    }

    /**
     * Draws a hairline rule between two x positions at a y.
     *
     * @param fromX where it starts
     * @param toX   where it ends
     * @param atY   the y it sits on
     * @param ink   the colour of the rule
     */
    void rule(float fromX, float toX, float atY, Ink ink) {
        try {
            if (ink.isGrey()) {
                stream.setStrokingColor(ink.red());
            } else {
                stream.setStrokingColor(ink.red(), ink.green(), ink.blue());
            }
            stream.setLineWidth(0.5f);
            stream.moveTo(fromX, atY);
            stream.lineTo(toX, atY);
            stream.stroke();
        } catch (IOException e) {
            throw new RenderException("a rule could not be drawn", e);
        }
    }

    /**
     * Fills a rectangle, which is what a table header sits on.
     *
     * @param x      the left edge
     * @param y      the bottom edge
     * @param width  the width
     * @param height the height
     * @param ink    the colour of the fill
     */
    void fill(float x, float y, float width, float height, Ink ink) {
        try {
            colour(ink);
            stream.addRect(x, y, width, height);
            stream.fill();
        } catch (IOException e) {
            throw new RenderException("a box could not be filled", e);
        }
    }

    /**
     * Fills a grid of equal squares in one colour, which is what a matrix of modules is.
     *
     * <p>The squares of a row that touch are one rectangle, and all the rectangles of the
     * grid are one path with one fill: a symbol of two thousand modules is then a few
     * hundred operators rather than two thousand of them, and the file stays a file a
     * reader opens quickly. The result on the page is the same picture either way.
     *
     * @param left   the left edge of the grid
     * @param top    its top edge
     * @param module the side of one square
     * @param cells  which cells are filled, row zero at the top
     * @param ink    the colour of the fill
     */
    void fillGrid(float left, float top, float module, boolean[][] cells, Ink ink) {
        try {
            colour(ink);
            int rectangles = 0;
            for (int row = 0; row < cells.length; row++) {
                boolean[] line = cells[row];
                int column = 0;
                while (column < line.length) {
                    if (!line[column]) {
                        column++;
                        continue;
                    }
                    int end = column;
                    while (end < line.length && line[end]) {
                        end++;
                    }
                    stream.addRect(left + column * module, top - (row + 1) * module,
                            (end - column) * module, module);
                    rectangles++;
                    column = end;
                }
            }
            if (rectangles > 0) {
                stream.fill();
            }
        } catch (IOException e) {
            throw new RenderException("a grid of squares could not be drawn", e);
        }
    }

    /**
     * Closes the content of the sheet and writes the footer of every page: what the
     * caller gives on the left, and the page number of the page count on the right. The
     * count is why this happens at the end.
     *
     * @param identity  what stands in the footer on the left, usually the invoice number
     * @param pageOf    how the page number reads, with {@code %1$d} for the page and
     *                  {@code %2$d} for the count
     */
    void finish(String identity, String pageOf) {
        finish(identity, pageOf, null);
    }

    /**
     * Closes the content of the sheet, writes the footer of every page and, where a head
     * is given, the compact head of every page after the first.
     *
     * <p>The head says what letter the page belongs to and the footer counts the pages, so
     * a page says each of the two once: a page number over the text and the same page
     * number under it is one of them asking to be read twice. The head is written here
     * rather than while the pages are drawn because it belongs to a page that is finished,
     * as the count does. It stands in the room {@link #headOnFollowingPages()} kept for it
     * under the top margin, which the text of those pages begins below.
     *
     * @param identity what stands in the footer on the left, usually the invoice number
     * @param pageOf   how the page number reads, with {@code %1$d} for the page and
     *                 {@code %2$d} for the count
     * @param head     what the compact head of a following page says, or {@code null} for
     *                 a rendering that has none
     * @throws IllegalStateException if a head is given that no page kept room for
     */
    void finish(String identity, String pageOf, String head) {
        String written = head == null ? "" : head;
        if (!written.isEmpty() && this.head == 0f) {
            throw new IllegalStateException("a compact head is written into the room a page"
                    + " kept for it, and this sheet kept none");
        }
        close();
        float size = 7.5f;
        Fonts.Face face = fonts.regular();
        String left = face.showable(identity);
        for (int i = 0; i < pages.size(); i++) {
            margins = i == 0 ? first : following;
            String right = face.showable(
                    String.format(Locale.ROOT, pageOf, i + 1, pages.size()));
            try (PDPageContentStream footer = new PDPageContentStream(document, pages.get(i),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream = footer;
                // The band a layout reserved at the foot of the first page is furniture of
                // that page and not margin: the footer of the page sits under it, at the
                // distance from the edge of the paper it keeps on every other page.
                float baseline = margins.bottom() - (i == 0 ? reserved : 0f)
                        - Margins.FOOTER_INSET;
                rule(left(), right(), baseline + 12f, palette.rule());
                show(left, left(), baseline, face, size, palette.muted());
                show(right, right() - face.width(right, size), baseline, face, size,
                        palette.muted());
                if (!written.isEmpty() && i > 0) {
                    float top = this.size.height() - margins.top();
                    show(face.showable(written), left(), top - HEAD_SIZE, face, HEAD_SIZE,
                            palette.muted());
                    rule(left(), right(),
                            top - lineHeight(HEAD_SIZE) - HEAD_RULE_GAP, palette.rule());
                }
            } catch (IOException e) {
                throw new RenderException("a page footer could not be written", e);
            }
        }
        stream = null;
    }

    /** Closes the content stream of the current page. */
    @Override
    public void close() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                throw new RenderException("the content of a page could not be closed", e);
            }
            stream = null;
        }
    }

    /** Sets the fill colour, as a grey where the three components agree. */
    private void colour(Ink ink) throws IOException {
        if (ink.isGrey()) {
            stream.setNonStrokingColor(ink.red());
        } else {
            stream.setNonStrokingColor(ink.red(), ink.green(), ink.blue());
        }
    }

    private static void wrapParagraph(String paragraph, Fonts.Face face, float size,
                                      float width, List<String> lines) {
        if (paragraph.isEmpty()) {
            lines.add("");
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String piece : pieces(paragraph)) {
            String candidate = line + piece;
            if (face.width(candidate.stripTrailing(), size) <= width || line.length() == 0
                    && piece.isBlank()) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
            }
            String next = piece.stripLeading();
            if (face.width(next, size) <= width) {
                line.append(next);
            } else {
                line.append(breakLongWord(next, face, size, width, lines));
            }
        }
        lines.add(line.toString().stripTrailing());
    }

    /**
     * Cuts a paragraph at the places a line may be broken: after a run of spaces, and
     * after a hyphen or a slash that stands between two letters or digits. The second is
     * what keeps a label like {@code Register-/Registriernummer} from being cut in the
     * middle of a word; a hyphen that is a minus sign is not a break opportunity, because
     * nothing before it is a word.
     */
    private static List<String> pieces(String paragraph) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            piece.append(c);
            if (breaksAfter(paragraph, i)) {
                pieces.add(piece.toString());
                piece.setLength(0);
            }
        }
        if (piece.length() > 0) {
            pieces.add(piece.toString());
        }
        return pieces;
    }

    private static boolean breaksAfter(String text, int index) {
        char c = text.charAt(index);
        if (c == ' ') {
            return index + 1 >= text.length() || text.charAt(index + 1) != ' ';
        }
        if (c != '-' && c != '/') {
            return false;
        }
        return index > 0 && wordly(text.charAt(index - 1))
                && index + 1 < text.length() && Character.isLetterOrDigit(text.charAt(index + 1));
    }

    /** Tells whether a character continues a word for the purpose of breaking one. */
    private static boolean wordly(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '/';
    }

    /**
     * Puts the head of a word that is wider than its column on lines of its own and
     * returns the tail that still fits.
     */
    private static String breakLongWord(String word, Fonts.Face face, float size, float width,
                                        List<String> lines) {
        StringBuilder piece = new StringBuilder();
        int i = 0;
        while (i < word.length()) {
            int codePoint = word.codePointAt(i);
            int step = Character.charCount(codePoint);
            String candidate = piece.toString() + word.substring(i, i + step);
            if (face.width(candidate, size) > width && piece.length() > 0) {
                lines.add(piece.toString());
                piece.setLength(0);
            }
            piece.append(word, i, i + step);
            i += step;
        }
        return piece.toString();
    }
}
