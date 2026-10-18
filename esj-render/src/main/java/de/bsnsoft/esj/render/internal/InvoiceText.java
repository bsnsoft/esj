package de.bsnsoft.esj.render.internal;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.render.RenderLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The invoice as plain text, for a terminal: the letter of {@link LetterLayout} without its
 * paper.
 *
 * <p>The text is written from {@link LetterLayout#model()}, the same collection the letter
 * draws from, so it shows the same facts under the same names, writes a value the way the
 * letter writes it — the number and date pictures of the language, a percentage as the
 * document wrote it, an IBAN in groups of four, a code by its name with the code listed once
 * under the closing heading — and has the same closing section, which carries every value no
 * block had a place for. What it leaves out is what only paper has: the address field of an
 * envelope, the foot of the first page and the payment code. The order is the one a reader
 * reads a letter in, top to bottom: the seller, the buyer, the title and the reference data,
 * the lines, the allowances and charges, the totals, the payment details, the notes and the
 * further details.
 *
 * <p>It is plain text and nothing else: rules of {@code -} and {@code =}, no colour, no
 * box-drawing character, every value in UTF-8 as the document wrote it, and nothing a
 * terminal reads as a command — every value goes through {@link Characters}, which takes the
 * controls and the characters that steer the reading order out, and a tabulator becomes a
 * space. No line is longer than the width it is given and nothing is cut: a value that does
 * not fit wraps, at a space where it has one and inside a word where it has none. A table
 * whose columns cannot stand beside each other in the width is written row by row, each cell
 * under the header of its column. The same document and width give the same text.
 */
public final class InvoiceText {

    /** The width a text is written in where nothing else says one. */
    public static final int DEFAULT_WIDTH = 100;

    /** The narrowest width a text is written in. */
    public static final int MIN_WIDTH = 40;

    /** The widest width a text is written in. */
    public static final int MAX_WIDTH = 1000;

    /** The air between two columns of a table, and between a label and its value. */
    private static final int GAP = 2;

    /** The narrowest the wrapping column of a table becomes before the table is stacked. */
    private static final int MIN_WRAP = 16;

    /** How wide the block of totals is, at most. */
    private static final int TOTALS_WIDTH = 52;

    /** How far a wrapped line of a block is indented under its first line. */
    private static final int HANG = 2;

    private final RenderLanguage language;
    private final int width;
    private final List<String> out = new ArrayList<>();

    private InvoiceText(RenderLanguage language, int width) {
        this.language = language;
        this.width = width;
    }

    /**
     * Writes a document as text.
     *
     * @param document the document
     * @param registry the registry of its edition, which gives the types and the names
     * @param language the language of the labels, the dates and the decimal separator
     * @param width    how many characters a line may take, between {@link #MIN_WIDTH} and
     *                 {@link #MAX_WIDTH}
     * @return the text, every line ended by a line feed
     * @throws IllegalArgumentException if the width is out of bounds
     */
    public static String render(SemanticDocument document, Registry registry,
                                RenderLanguage language, int width) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(language, "language");
        if (width < MIN_WIDTH || width > MAX_WIDTH) {
            throw new IllegalArgumentException("a text is " + MIN_WIDTH + " to " + MAX_WIDTH
                    + " characters wide, not " + width);
        }
        LetterLayout.Model model = new LetterLayout(document, registry, language, null, null,
                LetterOptions.defaults()).model();
        InvoiceText text = new InvoiceText(language, width);
        text.write(model);
        return text.joined();
    }

    // ------------------------------------------------------------------- the letter

    private void write(LetterLayout.Model model) {
        List<String> seller = new ArrayList<>();
        model.seller().forEach(seller::addAll);
        block(Word.SELLER, seller);
        block(Word.BUYER, model.recipient());
        if (!model.title().isEmpty()) {
            blank();
            List<String> title = wrap(oneLine(model.title()), width);
            out.addAll(title);
            out.add("=".repeat(widest(title)));
            if (!model.preceding().isEmpty()) {
                out.addAll(hanging(oneLine(model.preceding()), 0));
            }
        }
        if (!model.reference().isEmpty()) {
            blank();
            definitions(model.reference());
        }
        if (model.lines() != null) {
            InvoiceLayout.LineTable lines = model.lines();
            section(Word.LINES);
            table(lines.headers(), lines.rightAligned(), lines.rows(), lines.hanging(), 1,
                    lines.closing());
        }
        adjustments(Word.ALLOWANCES, model.allowances());
        adjustments(Word.CHARGES, model.charges());
        if (model.totals() != null && !model.totals().isEmpty()) {
            section(Word.TOTALS);
            totals(model.totals());
        }
        if (!model.payment().isEmpty()) {
            section(Word.PAYMENT);
            List<InvoiceLayout.Field> fields = new ArrayList<>();
            model.payment().forEach(fields::addAll);
            definitions(fields);
        }
        notes(model.notes());
        if (!model.codes().isEmpty() || !model.rest().isEmpty()) {
            section(Word.FURTHER_DETAILS);
            definitions(model.codes());
            if (!model.codes().isEmpty() && !model.rest().isEmpty()) {
                blank();
            }
            definitions(model.rest());
        }
    }

    /** Writes a heading and the lines of a party under it, where there are any. */
    private void block(Word heading, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        section(heading);
        for (String line : lines) {
            out.addAll(hanging(oneLine(line), 0));
        }
    }

    private void adjustments(Word heading, InvoiceLayout.AdjustmentTable table) {
        if (table == null) {
            return;
        }
        section(heading);
        table(List.of(table.headers()), table.rightAligned(), table.rows(), table.hanging(), 0,
                null);
    }

    /** Writes the notes: the subject, the text in its paragraphs, and whatever else each says. */
    private void notes(List<LetterLayout.Note> notes) {
        if (notes.isEmpty()) {
            return;
        }
        section(Word.NOTES);
        boolean first = true;
        for (LetterLayout.Note note : notes) {
            if (!first) {
                blank();
            }
            first = false;
            if (!note.subject().isEmpty()) {
                out.addAll(hanging(oneLine(note.subject()), 0));
            }
            if (!note.text().isEmpty()) {
                out.addAll(wrap(plain(note.text()), width));
            }
            for (InvoiceLayout.Field field : note.rest()) {
                out.addAll(hanging(oneLine(field.label() + ": " + field.value()), 0));
            }
        }
    }

    // ------------------------------------------------------------------- blocks

    /** Writes a section heading, underlined, after a blank line where anything came before. */
    private void section(Word heading) {
        blank();
        List<String> lines = wrap(heading.in(language), width);
        out.addAll(lines);
        out.add("-".repeat(widest(lines)));
    }

    /** Adds a blank line, unless the text is empty or ends in one. */
    private void blank() {
        if (!out.isEmpty() && !out.get(out.size() - 1).isEmpty()) {
            out.add("");
        }
    }

    /**
     * Writes labels and the values beside them. A label is never wrapped beside its value: a
     * label wider than its column stands on lines of its own and the value under it, so that
     * the lines of a value are never interleaved with anything else.
     */
    private void definitions(List<InvoiceLayout.Field> fields) {
        if (fields.isEmpty()) {
            return;
        }
        int widest = 0;
        for (InvoiceLayout.Field field : fields) {
            widest = Math.max(widest, length(oneLine(field.label())));
        }
        int label = Math.min(widest, Math.max(12, width * 2 / 5));
        int room = width - label - GAP;
        if (room < MIN_WRAP) {
            label = 0;
        }
        for (InvoiceLayout.Field field : fields) {
            String name = oneLine(field.label());
            String value = plain(field.value());
            if (label > 0 && length(name) <= label) {
                List<String> values = wrap(value, room);
                if (values.isEmpty()) {
                    out.add(name);
                    continue;
                }
                out.add(pad(name, label) + spaces(GAP) + values.get(0));
                for (String line : values.subList(1, values.size())) {
                    out.add(spaces(label + GAP) + line);
                }
            } else {
                out.addAll(wrap(name, width));
                int indent = label > 0 ? label + GAP : HANG;
                for (String line : wrap(value, width - indent)) {
                    out.add(spaces(indent) + line);
                }
            }
        }
    }

    /**
     * Writes the totals as a narrow block at the right: a label and its figure per row, and a
     * double rule over the amount due.
     */
    private void totals(List<LetterLayout.Row> rows) {
        int block = Math.min(TOTALS_WIDTH, width);
        String left = spaces(width - block);
        for (LetterLayout.Row row : rows) {
            String label = oneLine(row.label());
            String value = oneLine(row.value());
            if (row.due()) {
                out.add(left + "=".repeat(block));
            }
            int figure = length(value);
            int room = block - figure - GAP;
            if (room >= 12) {
                List<String> labels = wrap(label, room);
                if (labels.isEmpty()) {
                    labels = List.of("");
                }
                out.add(left + pad(labels.get(0), room) + spaces(GAP) + value);
                for (String line : labels.subList(1, labels.size())) {
                    out.add(left + line);
                }
            } else {
                for (String line : wrap(label, block)) {
                    out.add(left + line);
                }
                for (String line : wrap(value, block - HANG)) {
                    out.add(left + spaces(HANG) + line);
                }
            }
        }
    }

    // ------------------------------------------------------------------- tables

    /**
     * Writes a table: its header, a rule, the rows with what hangs under each, a closing
     * rule and the row that closes it. Every column but one is as wide as its widest cell and
     * holds every cell on one line; the one column that wraps takes what is left. Where the
     * width cannot hold the columns that way, the rows are written one under another.
     *
     * @param headers the column headers
     * @param right   which columns are set flush right
     * @param rows    the cells, row by row
     * @param hanging the lines that hang under each row
     * @param wrap    the column whose cells wrap
     * @param closing the row that closes the table, or {@code null}
     */
    private void table(List<String> headers, boolean[] right, List<String[]> rows,
                       List<List<String>> hanging, int wrap, Table.Closing closing) {
        int columns = headers.size();
        List<String[]> cells = new ArrayList<>(rows.size());
        for (String[] row : rows) {
            String[] clean = new String[columns];
            for (int c = 0; c < columns; c++) {
                clean[c] = c < row.length ? oneLine(row[c]) : "";
            }
            cells.add(clean);
        }
        String[] names = new String[columns];
        int[] need = new int[columns];
        int[] natural = new int[columns];
        for (int c = 0; c < columns; c++) {
            names[c] = oneLine(headers.get(c));
            need[c] = longestWord(names[c]);
            natural[c] = length(names[c]);
            for (String[] row : cells) {
                if (c != wrap) {
                    need[c] = Math.max(need[c], length(row[c]));
                }
                natural[c] = Math.max(natural[c], length(row[c]));
            }
        }
        String closingValue = closing == null ? "" : oneLine(closing.value());
        if (closing != null) {
            need[closing.column()] = Math.max(need[closing.column()], length(closingValue));
            natural[closing.column()] = Math.max(natural[closing.column()],
                    length(closingValue));
        }
        need[wrap] = Math.min(natural[wrap], Math.max(MIN_WRAP, need[wrap]));
        int gaps = (columns - 1) * GAP;
        int fixed = 0;
        for (int c = 0; c < columns; c++) {
            fixed += c == wrap ? 0 : need[c];
        }
        if (fixed + gaps + need[wrap] > width) {
            stacked(names, cells, hanging, closing, closingValue);
            return;
        }
        // A header is held to two lines where the width allows it: a column of figures
        // under a name broken over four lines is a column a reader has to decipher.
        int[] two = new int[columns];
        int roomy = 0;
        for (int c = 0; c < columns; c++) {
            two[c] = need[c];
            if (c != wrap) {
                while (two[c] < natural[c] && wrap(names[c], two[c]).size() > 2) {
                    two[c]++;
                }
                roomy += two[c];
            }
        }
        if (roomy + gaps + need[wrap] <= width) {
            need = two;
            fixed = roomy;
        }
        int[] column = new int[columns];
        int available = width - gaps - fixed;
        for (int c = 0; c < columns; c++) {
            column[c] = c == wrap ? Math.min(natural[wrap], available) : need[c];
        }
        int extra = available - column[wrap];
        for (int c = 0; c < columns && extra > 0; c++) {
            if (c != wrap) {
                int add = Math.min(extra, natural[c] - column[c]);
                column[c] += add;
                extra -= add;
            }
        }
        header(names, right, column);
        String rule = rule(column);
        out.add(rule);
        // What hangs under a row starts under its wrapping column, and never at the margin,
        // where it would read as a row of its own.
        int at = wrap == 0 ? HANG : 0;
        for (int c = 0; c < wrap; c++) {
            at += column[c] + GAP;
        }
        boolean spaced = false;
        for (int r = 0; r < cells.size(); r++) {
            spaced |= !hanging.get(r).isEmpty() || wrap(cells.get(r)[wrap], column[wrap]).size() > 1;
        }
        for (int r = 0; r < cells.size(); r++) {
            if (spaced && r > 0) {
                out.add("");
            }
            String[] row = cells.get(r);
            List<String> wrapped = wrap(row[wrap], column[wrap]);
            int lines = Math.max(1, wrapped.size());
            for (int i = 0; i < lines; i++) {
                String[] line = new String[columns];
                for (int c = 0; c < columns; c++) {
                    line[c] = c == wrap ? (i < wrapped.size() ? wrapped.get(i) : "")
                            : i == 0 ? row[c] : "";
                }
                out.add(cellsLine(line, right, column));
            }
            for (String detail : hanging.get(r)) {
                for (String line : hanging(oneLine(detail), at)) {
                    out.add(line);
                }
            }
        }
        out.add(rule);
        if (closing != null) {
            closingRow(closing, closingValue, right, column);
        }
    }

    /** Writes the header of a table: each name wrapped in its column, the lines at the foot. */
    private void header(String[] names, boolean[] right, int[] column) {
        List<List<String>> wrapped = new ArrayList<>();
        int lines = 1;
        for (int c = 0; c < names.length; c++) {
            List<String> name = wrap(names[c], column[c]);
            wrapped.add(name);
            lines = Math.max(lines, name.size());
        }
        for (int i = 0; i < lines; i++) {
            String[] line = new String[names.length];
            for (int c = 0; c < names.length; c++) {
                List<String> name = wrapped.get(c);
                int first = lines - name.size();
                line[c] = i >= first ? name.get(i - first) : "";
            }
            out.add(cellsLine(line, right, column));
        }
    }

    /** Writes the row that closes a table: the figure in its column, the label left of it. */
    private void closingRow(Table.Closing closing, String value, boolean[] right,
                            int[] column) {
        int before = 0;
        for (int c = 0; c < closing.column(); c++) {
            before += column[c] + GAP;
        }
        String label = oneLine(closing.label());
        int room = before - GAP;
        String[] line = new String[column.length];
        java.util.Arrays.fill(line, "");
        line[closing.column()] = value;
        String figure = cellsLine(line, right, column);
        if (room >= MIN_WRAP) {
            List<String> labels = wrap(label, room);
            for (int i = 0; i < labels.size() - 1; i++) {
                out.add(pad(labels.get(i), room, true));
            }
            String last = labels.isEmpty() ? "" : labels.get(labels.size() - 1);
            out.add(pad(last, room, true) + figure.substring(Math.min(figure.length(),
                    offset(figure, room))));
        } else {
            out.addAll(wrap(label, width));
            out.add(figure);
        }
    }

    /**
     * Writes the rows of a table that does not fit the width one under another: every cell
     * under the header of its column, and what hangs under the row after it.
     */
    private void stacked(String[] names, List<String[]> cells, List<List<String>> hanging,
                         Table.Closing closing, String closingValue) {
        for (int r = 0; r < cells.size(); r++) {
            if (r > 0) {
                out.add("");
            }
            List<InvoiceLayout.Field> fields = new ArrayList<>();
            String[] row = cells.get(r);
            for (int c = 0; c < names.length; c++) {
                if (!row[c].isEmpty()) {
                    fields.add(new InvoiceLayout.Field("", names[c], row[c], false));
                }
            }
            definitions(fields);
            for (String detail : hanging.get(r)) {
                out.addAll(hanging(oneLine(detail), HANG));
            }
        }
        if (closing != null) {
            out.add("");
            definitions(List.of(new InvoiceLayout.Field("", oneLine(closing.label()),
                    closingValue, false)));
        }
    }

    private String rule(int[] column) {
        StringBuilder rule = new StringBuilder();
        for (int c = 0; c < column.length; c++) {
            if (c > 0) {
                rule.append(spaces(GAP));
            }
            rule.append("-".repeat(column[c]));
        }
        return rule.toString();
    }

    /** Returns one line of a table: each cell aligned in its column, the columns parted. */
    private static String cellsLine(String[] cells, boolean[] right, int[] column) {
        StringBuilder line = new StringBuilder();
        for (int c = 0; c < cells.length; c++) {
            if (c > 0) {
                line.append(spaces(GAP));
            }
            line.append(pad(cells[c], column[c], c < right.length && right[c]));
        }
        return line.toString();
    }

    // ------------------------------------------------------------------- lines

    /**
     * Returns a text wrapped to a width, starting at a column, its second and later lines
     * indented under the first.
     */
    private List<String> hanging(String text, int at) {
        List<String> lines = new ArrayList<>();
        List<String> wrapped = wrap(text, width - at - HANG);
        for (int i = 0; i < wrapped.size(); i++) {
            lines.add(spaces(i == 0 ? at : at + HANG) + wrapped.get(i));
        }
        return lines;
    }

    /**
     * Returns a text in lines of at most a number of characters: each paragraph on lines of
     * its own, broken at a space where a word fits and inside a word where it does not.
     * Nothing is dropped but the spaces a line is broken at.
     *
     * @param text the text, its paragraphs ended by a line feed
     * @param room how many characters a line may take
     * @return the lines, empty for an empty text
     */
    static List<String> wrap(String text, int room) {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            return lines;
        }
        int limit = Math.max(1, room);
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            int used = 0;
            boolean any = false;
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                any = true;
                String rest = word;
                while (length(rest) > limit) {
                    if (used > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                        used = 0;
                    }
                    int cut = rest.offsetByCodePoints(0, limit);
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                if (rest.isEmpty()) {
                    continue;
                }
                int size = length(rest);
                if (used == 0) {
                    line.append(rest);
                    used = size;
                } else if (used + 1 + size <= limit) {
                    line.append(' ').append(rest);
                    used += 1 + size;
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(rest);
                    used = size;
                }
            }
            if (used > 0) {
                lines.add(line.toString());
            } else if (!any) {
                lines.add("");
            }
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private String joined() {
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        StringBuilder text = new StringBuilder();
        for (String line : out) {
            text.append(line.stripTrailing()).append('\n');
        }
        return text.toString();
    }

    // ------------------------------------------------------------------- characters

    /** Returns a text as a terminal may show it, its line ends kept. */
    private static String plain(String text) {
        return Characters.plain(text).replace('\t', ' ');
    }

    /** Returns a text as a terminal may show it, on one line. */
    private static String oneLine(String text) {
        return Characters.oneLine(text).replace('\t', ' ');
    }

    /** Returns how many characters a text takes on a line. */
    static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    private static int longestWord(String text) {
        int longest = 0;
        for (String word : text.split(" ")) {
            longest = Math.max(longest, length(word));
        }
        return longest;
    }

    private static int widest(List<String> lines) {
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, length(line));
        }
        return widest;
    }

    /** Returns the index in a text after a number of characters. */
    private static int offset(String text, int characters) {
        return text.offsetByCodePoints(0, Math.min(characters, length(text)));
    }

    private static String pad(String text, int size) {
        return pad(text, size, false);
    }

    private static String pad(String text, int size, boolean right) {
        int missing = size - length(text);
        if (missing <= 0) {
            return text;
        }
        return right ? spaces(missing) + text : text + spaces(missing);
    }

    private static String spaces(int count) {
        return " ".repeat(Math.max(0, count));
    }
}
