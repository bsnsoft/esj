package de.bsnsoft.esj.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A table on a {@link Sheet}: fixed columns, wrapping cells, and a header that comes back
 * at the top of every page the table runs onto.
 *
 * <p>A row and the lines that hang under it are one block. A cell with a long description
 * simply makes the block taller, and a block that does not fit on the rest of a page moves
 * to the next one whole rather than being cut between the row and its own details. Only a
 * block taller than an empty page body is cut, under the repeated header, because there is
 * no page it would fit on. That is the behaviour an invoice of three hundred lines needs.
 *
 * <p>Between the rows of the table a caller may put lines that span several columns — the
 * note of an invoice line, an allowance on it, one of its item attributes. Those are
 * written a size smaller and indented, so that a reader sees them as belonging to the row
 * above rather than as rows of their own, and the air under them together with the hairline
 * that closes the block keeps them nearer to their own row than to the next one. Where they
 * run onto the next page, the row they hang under is named again above them, because under
 * the repeated header a description with nothing beside it belongs to no line a reader can
 * see.
 *
 * <p>The heading of the table is drawn by the table rather than by its caller, and neither
 * it nor the column header is drawn before the first row is: the three are required
 * together, so a page cannot end with a heading and a header that introduce nothing. A
 * caller hands the heading to {@link #begin(Runnable, float)} and the table calls it back
 * once it knows where the first row goes.
 */
final class Table {

    /** Says that no column of a table holds cells that have to be kept on one line. */
    static final int NO_COLUMN = -1;

    /**
     * A row that closes a table: one figure in one of its columns, and what it is called.
     *
     * @param label  what the figure is called
     * @param value  the figure, already written the way the language writes one
     * @param column the column the figure stands in
     */
    record Closing(String label, String value, int column) {

        /**
         * Keeps a closing row.
         *
         * @throws NullPointerException if the label or the figure is {@code null}
         */
        Closing {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
        }
    }

    /** The type size of a cell. */
    static final float SIZE = 8.2f;

    /**
     * The smallest type the column header of a table is ever set in.
     *
     * <p>A header is set in {@link #SIZE} like everything else in the table, and only a
     * table whose columns cannot hold one word of a header at that size sets it smaller —
     * far enough to hold the widest word of them all, and never past this bound. Below it
     * a header is not a header a buyer reads off a printed page.
     */
    private static final float MIN_HEADER_SIZE = 6.6f;

    /** The type size of a line that hangs under a row. */
    static final float DETAIL_SIZE = 7.6f;

    /** The air above and below the text of a row. */
    private static final float ROW_PADDING = 3.2f;

    /** The air between the header row and the first row under it. */
    private static final float HEADER_GAP = 1.5f;

    /** The air between the text of a row and the first line hanging under it. */
    private static final float DETAIL_GAP = 1.4f;

    /** The air under a block of detail lines, above the hairline that closes it. */
    private static final float BLOCK_GAP = 4.4f;

    /** The air under that hairline, before the next row begins. */
    private static final float BLOCK_GAP_BELOW = 3.4f;

    /** The air between two columns. */
    private static final float COLUMN_GAP = 6f;

    /** The air between the rule under the last row and a row closing the table. */
    private static final float CLOSING_GAP = 4.6f;

    /** The air under the rule that ends the table. */
    private static final float END_GAP = 2f;

    /**
     * How much of an empty page body a row block may take and still be moved whole.
     *
     * <p>A row and what hangs under it are one thing to a reader, so a block is moved to
     * the next page rather than cut — but a block that takes most of a page empties the
     * page before it, and a letter whose first page carries an address, a title and a hand
     * of white is not a letter a business sends. Half a page is where the two claims meet.
     * Above it the row itself is still kept whole, with its column header and the line
     * that names it; only what hangs under the row flows onto the next page.
     */
    private static final float WHOLE_ROW_SHARE = 0.5f;

    private final Sheet sheet;
    private final float[] columnX;
    private final float[] columnWidth;
    private final boolean[] rightAligned;
    private final String[] headers;
    private final String continued;
    private final int detailColumn;

    /** Draws the heading this table stands under, once the table knows where it stands. */
    private Runnable heading;

    /** How much room that heading takes. */
    private float headingHeight;

    /** Whether the heading and the column header have been drawn. */
    private boolean open;

    /** The type the column header is set in, which a table without the width for it lowers. */
    private float headerSize = SIZE;

    /** Whether a page began under this table and nothing on it has said whose row it is. */
    private boolean carried;

    /** Whether the row before this one carried lines hanging under it. */
    private boolean detailed;

    /** Whether a hairline closes every row of this table rather than a detailed one. */
    private boolean everyRow;

    /** Whether a row has been drawn. */
    private boolean any;

    /** The row closing this table, where one was announced. */
    private Closing announced;

    /** The identifier of the last row drawn, as the carry-over line names it. */
    private String rowLabel = "";

    /**
     * Lays out the columns of a table over the width of the sheet.
     *
     * @param sheet     the sheet the table is drawn on
     * @param headers   the header of every column, already in the language of the rendering
     * @param weights   the relative width of every column; they need not add up to anything
     * @param right     which columns are set flush right, which the amounts are
     * @param continued the word that says a row is carried over from the page before,
     *                  already in the language of the rendering
     * @param detail    the column a line hanging under a row starts in
     * @throws IllegalArgumentException if the three arrays are of different lengths, or the
     *                                  detail column is not one of them
     * @throws NullPointerException     if an argument is {@code null}
     */
    Table(Sheet sheet, String[] headers, float[] weights, boolean[] right, String continued,
          int detail) {
        this(sheet, headers, weights, right, continued, detail, List.of());
    }

    /**
     * Lays out the columns of a table over the width of the sheet, against the rows it
     * will be given.
     *
     * @param sheet     the sheet the table is drawn on
     * @param headers   the header of every column, already in the language of the rendering
     * @param weights   the relative width of every column; they need not add up to anything
     * @param right     which columns are set flush right, which the amounts are
     * @param continued the word that says a row is carried over from the page before,
     *                  already in the language of the rendering
     * @param detail    the column a line hanging under a row starts in
     * @param content   the cells the table will be given, which decide how much width a
     *                  column can spare; empty where the caller does not know them yet
     * @throws IllegalArgumentException if the three arrays are of different lengths, or the
     *                                  detail column is not one of them
     * @throws NullPointerException     if an argument is {@code null}
     */
    Table(Sheet sheet, String[] headers, float[] weights, boolean[] right, String continued,
          int detail, List<String[]> content) {
        this(sheet, headers, weights, right, continued, detail, content, NO_COLUMN);
    }

    /**
     * Lays out the columns of a table over the width of the sheet, against the rows it
     * will be given, one of them holding cells that are not parted.
     *
     * @param sheet     the sheet the table is drawn on
     * @param headers   the header of every column, already in the language of the rendering
     * @param weights   the relative width of every column; they need not add up to anything
     * @param right     which columns are set flush right, which the amounts are
     * @param continued the word that says a row is carried over from the page before,
     *                  already in the language of the rendering
     * @param detail    the column a line hanging under a row starts in
     * @param content   the cells the table will be given, which decide how much width a
     *                  column can spare; empty where the caller does not know them yet
     * @param whole     the column whose cell is one statement and is kept on one line
     *                  where the table has the width for it, or {@link #NO_COLUMN}
     * @throws IllegalArgumentException if the three arrays are of different lengths, or the
     *                                  detail column is not one of them
     * @throws NullPointerException     if an argument is {@code null}
     */
    Table(Sheet sheet, String[] headers, float[] weights, boolean[] right, String continued,
          int detail, List<String[]> content, int whole) {
        if (headers.length != weights.length || headers.length != right.length) {
            throw new IllegalArgumentException("a table column needs a header, a width and an "
                    + "alignment");
        }
        if (detail < 0 || detail >= headers.length) {
            throw new IllegalArgumentException("the detail column is one of the columns of "
                    + "the table");
        }
        if (whole != NO_COLUMN && (whole < 0 || whole >= headers.length)) {
            throw new IllegalArgumentException("the column kept on one line is one of the "
                    + "columns of the table");
        }
        this.sheet = Objects.requireNonNull(sheet, "sheet");
        this.headers = headers.clone();
        this.rightAligned = right.clone();
        this.continued = Objects.requireNonNull(continued, "continued");
        this.detailColumn = detail;
        this.columnX = new float[headers.length];
        this.columnWidth = new float[headers.length];
        float total = 0;
        for (float weight : weights) {
            total += weight;
        }
        float available = sheet.width() - COLUMN_GAP * (headers.length - 1);
        for (int i = 0; i < headers.length; i++) {
            columnWidth[i] = available * weights[i] / total;
        }
        sizeColumns(available, List.copyOf(content), whole);
        fitHeaderWords(available);
        float x = sheet.left();
        for (int i = 0; i < headers.length; i++) {
            columnX[i] = x;
            x += columnWidth[i] + COLUMN_GAP;
        }
    }

    /**
     * Gives every column the width its header and its own cells ask for, at the cost of
     * the columns that have width to spare.
     *
     * <p>A header wraps, and a header narrower than one of its own words wraps inside that
     * word: <i>Bruttoeinzel/preis</i> is not a column of a letter. A cell wraps too, and a
     * quantity broken into <i>30</i> and <i>Packstücke</i> beside a description column that
     * is half empty is not one either. The weights of a layout are a proportion rather than
     * a measurement, and a template that adds columns beside them makes every one of them
     * narrower, so the proportion alone cannot answer this.
     *
     * <p>Every column asks for the width of its header and of the widest cell it will be
     * given, and never for more than an equal share of the paper, so one long description
     * cannot claim the table. A column narrower than what it asks for is given the
     * difference; a column wider gives up the surplus, in proportion to the surplus and
     * only as far as the others need it. Where the paper cannot hold one word of every
     * header even so, every column keeps its share of what there is: a table of thirty
     * columns is a table nothing can set.
     *
     * <p>One column may hold cells that are not two things but one — a quantity and the
     * name of its unit — and that column asks a second time, out of the width no column
     * asked for: an equal share is a rule about greed and not about the paper, and
     * <i>3.875</i> over <i>Kilowattstunden</i> beside a description column with two
     * hundred points to spare is a reader putting a figure back together. It is given
     * what is free and no more, so no other column loses what its own header and cells
     * need; where a table has nothing free, the cell wraps as it always did.
     *
     * @param available the width the columns share, the gaps between them already taken off
     * @param content   the cells the table will be given, which may be empty
     * @param whole     the column whose cells are kept on one line, or {@link #NO_COLUMN}
     */
    private void sizeColumns(float available, List<String[]> content, int whole) {
        Fonts.Face header = sheet.fonts().bold();
        Fonts.Face cell = sheet.fonts().regular();
        float equalShare = available / headers.length;
        float[] want = new float[headers.length];
        float onOneLine = 0;
        float asked = 0;
        for (int i = 0; i < headers.length; i++) {
            float unbreakable = longestWord(headers[i], header);
            float widest = widestLine(headers[i], header);
            for (String[] row : content) {
                if (i >= row.length || row[i] == null || row[i].isEmpty()) {
                    continue;
                }
                unbreakable = Math.max(unbreakable,
                        Math.min(longestWord(row[i], cell), equalShare));
                widest = Math.max(widest, widestLine(row[i], cell));
            }
            want[i] = Math.max(unbreakable, Math.min(widest, equalShare));
            if (i == whole) {
                onOneLine = Math.min(widest, available);
            }
            asked += want[i];
        }
        if (asked > available) {
            for (int i = 0; i < headers.length; i++) {
                want[i] *= available / asked;
            }
        } else if (whole != NO_COLUMN && onOneLine > want[whole]) {
            want[whole] += Math.min(onOneLine - want[whole], available - asked);
        }
        float owed = 0;
        float spare = 0;
        for (int i = 0; i < headers.length; i++) {
            owed += Math.max(0f, want[i] - columnWidth[i]);
            spare += Math.max(0f, columnWidth[i] - want[i]);
        }
        if (owed <= 0f || spare <= 0f) {
            return;
        }
        float share = Math.min(1f, owed / spare);
        for (int i = 0; i < headers.length; i++) {
            columnWidth[i] = columnWidth[i] < want[i]
                    ? want[i]
                    : columnWidth[i] - (columnWidth[i] - want[i]) * share;
        }
    }

    /**
     * Gives every column the width one word of its header takes, at the cost of the
     * columns that have width to spare, and lowers the type of the header where the paper
     * cannot hold one word of every header even so.
     *
     * <p>{@link #sizeColumns(float, List, int)} asks for that width and gets it wherever
     * the columns together ask for no more than there is. Where they ask for more, every
     * column is cut back in proportion — and a column cut below its own longest word wraps
     * inside that word: <i>Bruttobetra / g der Position</i> is a header a reader has to
     * read twice, and a template that adds two columns to a line table is enough to make
     * one. So the header words are asked for again here, after the widths are settled and
     * out of the width of the columns that have some to give, and a header is broken
     * between words or not at all.
     *
     * <p>Where the columns cannot hold one word of every header side by side at the type
     * size of the table, the header is set smaller — as far as it takes and never past
     * {@link #MIN_HEADER_SIZE}. A table of thirty columns is still a table nothing can
     * set, and there the header wraps inside a word as it always did; what this rules out
     * is a table that had the width and gave it to the wrong column.
     *
     * @param available the width the columns share, the gaps between them already taken off
     */
    private void fitHeaderWords(float available) {
        Fonts.Face face = sheet.fonts().bold();
        float[] need = new float[headers.length];
        float asked = headerWords(face, headerSize, need);
        if (asked > available) {
            headerSize = Math.max(MIN_HEADER_SIZE, headerSize * available / asked);
            asked = headerWords(face, headerSize, need);
            if (asked > available) {
                return;
            }
        }
        float owed = 0;
        float spare = 0;
        for (int i = 0; i < headers.length; i++) {
            owed += Math.max(0f, need[i] - columnWidth[i]);
            spare += Math.max(0f, columnWidth[i] - need[i]);
        }
        if (owed <= 0f || spare <= 0f) {
            return;
        }
        float share = Math.min(1f, owed / spare);
        for (int i = 0; i < headers.length; i++) {
            columnWidth[i] = columnWidth[i] < need[i]
                    ? need[i]
                    : columnWidth[i] - (columnWidth[i] - need[i]) * share;
        }
    }

    /**
     * Fills in how wide the widest piece of every header is that a line break cannot go
     * through, and returns their sum.
     */
    private float headerWords(Fonts.Face face, float size, float[] need) {
        float asked = 0;
        for (int i = 0; i < headers.length; i++) {
            need[i] = Sheet.unbreakable(face.showable(headers[i]), face, size);
            asked += need[i];
        }
        return asked;
    }

    /** Returns how wide the longest word of a text is. */
    private static float longestWord(String text, Fonts.Face face) {
        return widest(text, face, "\\s+");
    }

    /**
     * Returns how wide the longest line of a text is. A cell that carries line breaks of
     * its own is as wide as its widest line and not as wide as all of them laid end to
     * end, and a face has no glyph for a line break to measure anyway.
     */
    private static float widestLine(String text, Fonts.Face face) {
        return widest(text, face, "\\R");
    }

    /** Returns how wide the widest piece of a text is, cut at a separator. */
    private static float widest(String text, Fonts.Face face, String separator) {
        float widest = 0;
        for (String piece : face.showable(text).split(separator)) {
            widest = Math.max(widest, face.width(piece, SIZE));
        }
        return widest;
    }

    /**
     * Announces the table and the heading it stands under. Nothing is drawn yet.
     *
     * <p>A heading alone at the foot of a page is a heading of nothing, and a heading with
     * the column header of an empty table under it is no better. So the table waits: the
     * first row knows exactly how tall it is, the heading and the header are asked for
     * together with it, and the three begin the same page.
     *
     * @param head          draws the heading of this table at the cursor
     * @param height        how much room that heading takes, in points
     * @throws NullPointerException if {@code head} is {@code null}
     */
    void begin(Runnable head, float height) {
        this.heading = Objects.requireNonNull(head, "head");
        this.headingHeight = height;
    }

    /**
     * Closes every row of this table with a hairline rather than only a row that carries
     * lines hanging under it.
     *
     * <p>Rows of the same table stand at different distances from one another otherwise:
     * a row with a description under it is parted from the next by air, a rule and air
     * again, a row without one by the air of a row alone. One rhythm reads as one table.
     */
    void ruleUnderEveryRow() {
        everyRow = true;
    }

    /**
     * Says that a row closing this table follows the row that is drawn next.
     *
     * <p>The rule that ends a table and the row that closes it are one thing to a reader:
     * a figure alone at the top of a page, with the column it is the sum of on the page
     * before, reads as the first row of whatever block follows it. So the last row of the
     * table asks for the two together with itself, and the three move to the next page
     * together where they do not fit here.
     *
     * @param closing the row, as {@link #closing(Closing)} will be given it
     * @throws IllegalArgumentException if its column is not one of this table's
     * @throws NullPointerException     if it is {@code null}
     */
    void closingFollows(Closing closing) {
        Objects.requireNonNull(closing, "closing");
        checkedColumn(closing.column());
        announced = closing;
    }

    /**
     * Returns how much room the end of the table and the row closing it take under a row.
     *
     * @param detailsUnderRow whether lines hang under that row, which decides whether a
     *                        hairline parts it from what follows
     * @return the height, or zero where no closing row was announced
     */
    private float closingTail(boolean detailsUnderRow) {
        if (announced == null) {
            return 0f;
        }
        float parting = everyRow || detailsUnderRow ? BLOCK_GAP : 0f;
        return parting + Sheet.RULE_HEIGHT + END_GAP + CLOSING_GAP + closingHeight(announced);
    }

    /** Returns how tall a closing row is, the wrapping of its label included. */
    private float closingHeight(Closing closing) {
        return Math.max(1, closingLines(closing).size()) * Sheet.lineHeight(SIZE);
    }

    /**
     * Returns where the figure of a closing row begins, which is where its label has to
     * stop.
     *
     * <p>A figure is set flush with the right edge of its column, and one wider than the
     * column reaches out of it to the left. So the figure decides where the room for the
     * label ends, and not the column boundary it is aligned on.
     */
    private float closingFigureLeft(Closing closing) {
        int column = closing.column();
        Fonts.Face face = sheet.fonts().bold();
        float value = face.width(face.showable(closing.value()), SIZE);
        return Math.min(columnX[column], columnX[column] + columnWidth[column] - value);
    }

    /**
     * Breaks the label of a closing row into the lines it takes left of its column.
     *
     * <p>A label that does not fit beside its figure is wrapped like every other label of
     * the module rather than written over the figure, and a label with no room at all —
     * a column that begins at the left edge of the sheet — is left out: the figure is
     * aligned with the column it closes and says what it is by standing there.
     */
    private List<String> closingLines(Closing closing) {
        float room = closingFigureLeft(closing) - COLUMN_GAP - sheet.left();
        Fonts.Face face = sheet.fonts().bold();
        String written = face.showable(closing.label());
        if (room <= 0) {
            return List.of();
        }
        if (face.width(written, SIZE) <= room) {
            return List.of(written);
        }
        List<String> lines = Sheet.wrap(written, face, SIZE, room);
        for (String line : lines) {
            if (face.width(line, SIZE) > room) {
                // A word wider than the room is a word that would be written over the
                // figure whatever is done with the lines around it, so the label goes and
                // the figure stays: it is aligned with the column it closes and says what
                // it is by standing there.
                return List.of();
            }
        }
        return lines;
    }

    /** Returns a column of this table, or refuses one that is not. */
    private int checkedColumn(int column) {
        if (column < 0 || column >= headers.length) {
            throw new IllegalArgumentException("the column of a closing row is one of the "
                    + "columns of the table");
        }
        return column;
    }

    /** Returns a tail that a page after this one could hold together with a block. */
    private float tailThatFits(float block, float tail) {
        return block + tail <= sheet.nextPageHeight() ? tail : 0f;
    }

    /** Tells whether a hairline stands between the row last drawn and the next one. */
    private boolean parted() {
        return everyRow ? any : detailed;
    }

    /** Ends the table: no more headers, and a rule under the last row. */
    void end() {
        open(0f);
        sheet.onNewPage(null);
        if (parted()) {
            sheet.down(BLOCK_GAP);
        }
        sheet.require(END_GAP);
        sheet.rule();
        sheet.down(END_GAP);
    }

    /**
     * Draws a row that closes the table under it: a figure in one of its columns, aligned
     * with the figures above it, and a label ending where that column begins.
     *
     * <p>It is drawn after {@link #end()}, so it stands under the rule that closes the
     * table and after the last row of it rather than on every page the table ran over. It
     * is one statement of the document and not a figure the table added up.
     *
     * @param closing the row
     * @throws IllegalArgumentException if its column is not one of this table's
     * @throws NullPointerException     if it is {@code null}
     */
    void closing(Closing closing) {
        Objects.requireNonNull(closing, "closing");
        checkedColumn(closing.column());
        Fonts.Face face = sheet.fonts().bold();
        float height = Sheet.lineHeight(SIZE);
        List<String> lines = closingLines(closing);
        float block = closingHeight(closing);
        float stop = closingFigureLeft(closing) - COLUMN_GAP;
        sheet.require(CLOSING_GAP + block);
        sheet.down(CLOSING_GAP);
        float top = sheet.y();
        // The label is written before the figure, as a reader reads the row: a text
        // extractor reads a PDF in the order its content stream was written.
        for (int line = 0; line < lines.size(); line++) {
            String written = lines.get(line);
            sheet.show(written, stop - face.width(written, SIZE),
                    top - line * height - SIZE, face, SIZE, sheet.palette().text());
        }
        // The figure stands beside the last line of its label, which is the line a reader
        // reads it off, and in the column the figures above it stand in.
        writeAt(face.showable(closing.value()), closing.column(),
                top - (block - height) - SIZE, face, SIZE, sheet.palette().text());
        sheet.down(block);
    }

    /**
     * Draws a row that nothing hangs under.
     *
     * @param cells one text per column; an empty string leaves the column blank
     * @param bold  whether the row is set in the bold face, which a total is
     */
    void row(String[] cells, boolean bold) {
        row(cells, bold, List.of());
    }

    /**
     * Draws a row together with the lines that hang under it, as one block.
     *
     * <p>The cells are drawn one after another, each of them whole: every line of a cell is
     * written before the next cell begins. That is invisible on the page and load bearing
     * off it — a text extractor reads a PDF in the order its content stream was written, and
     * a description that was wrapped over three lines has to come back as one run of text
     * rather than with the quantity and the price of the row threaded through it.
     *
     * <p>The block moves to the next page rather than being cut, unless it is taller than
     * an empty page body — the body of a page after the first, which is the page it would
     * be moved to; then it is cut at a line boundary and continues under the repeated
     * header, the cut falling at the same line in every column, and the page that carries
     * the rest of it is opened by the identifier of the row.
     *
     * @param cells   one text per column; an empty string leaves the column blank
     * @param bold    whether the row is set in the bold face, which a total is
     * @param details the lines hanging under the row, in the order they are read
     * @throws NullPointerException if an argument is {@code null}
     */
    void row(String[] cells, boolean bold, List<String> details) {
        Objects.requireNonNull(details, "details");
        Fonts.Face face = bold ? sheet.fonts().bold() : sheet.fonts().regular();
        List<List<String>> wrapped = new ArrayList<>(cells.length);
        int lines = 1;
        for (int i = 0; i < cells.length; i++) {
            List<String> column =
                    Sheet.wrap(face.showable(cells[i]), face, SIZE, columnWidth[i]);
            wrapped.add(column);
            lines = Math.max(lines, column.size());
        }
        float height = Sheet.lineHeight(SIZE);
        List<List<String>> hanging = wrappedDetails(details);
        float separator = parted() ? BLOCK_GAP + Sheet.RULE_HEIGHT + BLOCK_GAP_BELOW : 0f;
        float block = lines * height + ROW_PADDING + detailsHeight(hanging);
        // Where this is the first row, the heading and the column header stand above it and
        // are part of what has to fit: a block that cannot be moved whole together with
        // them cannot be moved whole at all, and is cut here rather than leaving the two
        // alone at the foot of a page.
        float opening = open ? 0f : headingHeight + headerHeight() + HEADER_GAP;
        float own = lines * height + ROW_PADDING;
        boolean whole = opening + separator + block <= sheet.nextPageHeight()
                && separator + block <= sheet.nextPageHeight() * WHOLE_ROW_SHARE;
        boolean cellsWhole = !whole && opening + separator + own <= sheet.nextPageHeight();
        // Where a row closes the table under this one, it and the rule above it are asked
        // for with the row — unless no page would hold the three, in which case asking
        // for them would move the row for nothing.
        float tail = closingTail(!hanging.isEmpty());
        float wholeTail = tailThatFits(separator + block, tail);
        float ownTail = tailThatFits(separator + own, tail);
        // The heading and the header are drawn with this row where it is the first one,
        // and they are asked for together with exactly what the row needs.
        open(whole ? separator + block + wholeTail
                : cellsWhole ? separator + own + ownTail : separator + height + ROW_PADDING);
        if (whole) {
            // A block that fits on a page of its own is moved to the next page whole
            // rather than cut between the row and the lines that belong to it.
            sheet.require(separator + block + wholeTail);
        } else if (cellsWhole) {
            // A block too tall for that keeps its own row together and lets the lines
            // under it flow: a cell is one run of text and is never parted, and a reader
            // who turns the page meets the line that names the row it came from.
            sheet.require(separator + own + ownTail);
        }
        if (parted()) {
            separator();
        }
        String label = identifier(wrapped);
        // What has to stand under the last line of this row: its padding, and the end of
        // the table with the row that closes it where this row is the last one and nothing
        // hangs under it. The lines that do hang under it carry the tail themselves.
        float underCells = ROW_PADDING + (hanging.isEmpty() ? tail : 0f);
        boolean keepBack = hanging.isEmpty() && tail > 0
                && height + Sheet.lineHeight(DETAIL_SIZE) + underCells <= sheet.nextPageHeight();
        int drawn = 0;
        while (drawn < lines) {
            int room = sheet.roomInLines(height);
            if (room < 1) {
                sheet.newPage();
                room = Math.max(1, sheet.roomInLines(height));
            }
            if (drawn > 0 && carried) {
                // The rest of a row that was cut at a page break opens this page under the
                // repeated column header, where nothing else says which row it belongs to.
                carried = false;
                writeCarryOver(label, sheet.left(), sheet.width());
                room = Math.max(1, sheet.roomInLines(height));
            }
            int rest = lines - drawn;
            if (keepBack && rest <= room && !sheet.fits(rest * height + underCells)) {
                // The rest of the row fits here and what has to follow it does not, so a
                // line is kept back and goes over with it: the page that carries the end
                // of the table carries a line of the table.
                room = rest - 1;
            }
            int take = Math.min(room, rest);
            if (take < 1) {
                sheet.newPage();
                continue;
            }
            float top = sheet.y();
            for (int i = 0; i < cells.length; i++) {
                List<String> column = wrapped.get(i);
                for (int line = drawn; line < drawn + take && line < column.size(); line++) {
                    writeAt(column.get(line), i, top - (line - drawn) * height - SIZE,
                            face, SIZE, sheet.palette().text());
                }
            }
            sheet.down(take * height);
            drawn += take;
        }
        sheet.down(ROW_PADDING);
        // Whatever page this row ended on, it is on it: its details need no introduction
        // until the next page begins.
        carried = false;
        rowLabel = label;
        if (!hanging.isEmpty()) {
            sheet.down(DETAIL_GAP);
            for (int i = 0; i < hanging.size(); i++) {
                // The end of the table and the row that closes it stand under the last of
                // these lines, so that line asks for them together with itself: a text
                // taller than a page flows, and what follows it must not land on a page of
                // its own with no line of the table on it.
                detail(hanging.get(i), i == hanging.size() - 1 ? tail : 0f);
            }
        }
        detailed = !hanging.isEmpty();
        any = true;
    }

    /** Draws the air, the hairline and the air that part one row block from the next. */
    private void separator() {
        sheet.require(BLOCK_GAP + Sheet.RULE_HEIGHT + BLOCK_GAP_BELOW
                + Sheet.lineHeight(SIZE));
        sheet.down(BLOCK_GAP);
        sheet.rule();
        sheet.down(BLOCK_GAP_BELOW);
    }

    /** Breaks every line hanging under a row into the lines it takes in its column. */
    private List<List<String>> wrappedDetails(List<String> details) {
        Fonts.Face face = sheet.fonts().regular();
        float width = sheet.right() - columnX[detailColumn];
        List<List<String>> hanging = new ArrayList<>(details.size());
        for (String text : details) {
            hanging.add(Sheet.wrap(face.showable(text), face, DETAIL_SIZE, width));
        }
        return hanging;
    }

    /** Returns how tall the lines hanging under a row are, the air above them included. */
    private static float detailsHeight(List<List<String>> hanging) {
        if (hanging.isEmpty()) {
            return 0f;
        }
        int lines = 0;
        for (List<String> detail : hanging) {
            lines += detail.size();
        }
        return DETAIL_GAP + lines * Sheet.lineHeight(DETAIL_SIZE);
    }

    /**
     * Draws one line that hangs under the row above and spans from the detail column to the
     * right edge of the table.
     *
     * @param lines the line, already broken to the width it has
     * @param after what has to stand under the last of those lines and move with it, which
     *              is the end of the table and the row that closes it, or zero
     */
    private void detail(List<String> lines, float after) {
        Fonts.Face face = sheet.fonts().regular();
        float x = columnX[detailColumn];
        float width = sheet.right() - x;
        float height = Sheet.lineHeight(DETAIL_SIZE);
        // One hanging line is one run of text, whatever width it was broken to: a
        // description wrapped over three lines has to come back out of the file as one
        // run, and a page break between the second and the third would part it. The line
        // that names the row on a page that has just begun is part of what is asked for,
        // so the three do not move to a page the introduction then pushes them off.
        float block = lines.size() * height + height;
        float tail = after > 0 && 2 * height + after <= sheet.nextPageHeight() ? after : 0f;
        if (block + tail <= sheet.nextPageHeight()) {
            sheet.require(block + tail);
        }
        for (int line = 0; line < lines.size(); line++) {
            float below = line == lines.size() - 1 ? tail : 0f;
            sheet.require(height + below);
            if (carried) {
                carried = false;
                // A page that has just begun holds two lines of this size, so the
                // introduction and the line it introduces cannot be parted here.
                sheet.require(2 * height + below);
                writeCarryOver(rowLabel, x, width);
            }
            sheet.show(lines.get(line), x, sheet.y() - DETAIL_SIZE, face, DETAIL_SIZE,
                    sheet.palette().muted());
            sheet.down(height);
        }
    }

    /**
     * Draws the heading and the column header, once, before the first row.
     *
     * @param first how much of what follows the header must fit beside it
     */
    private void open(float first) {
        if (open) {
            return;
        }
        open = true;
        sheet.require(headingHeight + headerHeight() + HEADER_GAP + first);
        heading.run();
        header();
        sheet.onNewPage(this::carryHeader);
    }

    /** Draws the header at the top of a new page and remembers that the page is new. */
    private void carryHeader() {
        header();
        carried = true;
    }

    /**
     * Writes which row the lines under the repeated header belong to — the identifier of
     * a row that a reader last saw on the page before.
     *
     * <p>Two things need it, and they need the same line. A detail line that hangs under
     * a row and flows onto the next page is one; the other is a row whose own cell was
     * too tall for the page it began on, whose remaining lines would otherwise stand
     * under the repeated column header with the identifier column already spent.
     *
     * @param label what names the row, empty for a row nothing names
     * @param x     where the line starts
     * @param width how wide it may be
     */
    private void writeCarryOver(String label, float x, float width) {
        if (label.isEmpty()) {
            return;
        }
        Fonts.Face face = sheet.fonts().regular();
        String text = label + " (" + continued + ")";
        String line = Sheet.wrap(face.showable(text), face, DETAIL_SIZE, width).get(0);
        sheet.show(line, x, sheet.y() - DETAIL_SIZE, face, DETAIL_SIZE, sheet.palette().muted());
        sheet.down(Sheet.lineHeight(DETAIL_SIZE));
    }

    /** Returns what names a row: the first line of its first cell that carries text. */
    private static String identifier(List<List<String>> wrapped) {
        for (List<String> column : wrapped) {
            if (!column.isEmpty() && !column.get(0).isEmpty()) {
                return column.get(0);
            }
        }
        return "";
    }

    private void header() {
        Fonts.Face face = sheet.fonts().bold();
        float height = headerHeight();
        sheet.fill(sheet.left(), sheet.y() - height, sheet.width(), height,
                sheet.palette().tableHeaderFill());
        float top = sheet.y() - ROW_PADDING;
        for (int i = 0; i < headers.length; i++) {
            List<String> column =
                    Sheet.wrap(face.showable(headers[i]), face, headerSize, columnWidth[i]);
            for (int line = 0; line < column.size(); line++) {
                writeAt(column.get(line), i,
                        top - line * Sheet.lineHeight(headerSize) - headerSize,
                        face, headerSize, sheet.palette().tableHeaderText());
            }
        }
        sheet.down(height + HEADER_GAP);
    }

    private float headerHeight() {
        Fonts.Face face = sheet.fonts().bold();
        int lines = 1;
        for (int i = 0; i < headers.length; i++) {
            lines = Math.max(lines, Sheet
                    .wrap(face.showable(headers[i]), face, headerSize, columnWidth[i]).size());
        }
        return lines * Sheet.lineHeight(headerSize) + 2 * ROW_PADDING;
    }

    private void writeAt(String line, int column, float baseline, Fonts.Face face, float size,
                         Ink ink) {
        float x = rightAligned[column]
                ? columnX[column] + columnWidth[column] - face.width(line, size)
                : columnX[column];
        sheet.show(line, x, baseline, face, size, ink);
    }
}
