package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A row of a table and the lines hanging under it are one block, and a page break does not
 * go through it.
 *
 * <p>A reader of an invoice reads a line as one thing: the row, its description, its item
 * identifiers, the allowances on it. A page break in the middle of that leaves the reader
 * turning the page to find out what was bought, and it happened — the standard invoice of
 * this repository on the example image template used to lose the third of the three lines
 * hanging under its first row to the next page. The block now moves whole.
 *
 * <p>The exception is stated and asserted here too: a block taller than an empty page body
 * cannot be moved to a page it would fit on, because there is none. That one is cut, at a
 * line boundary, under the repeated header, and the page that carries the rest of it is
 * opened by the identifier of the row.
 */
class TableRowsTest {

    /** The layouts this holds on: the generic one, plain and on each example template. */
    static List<String> templates() {
        List<String> templates = new ArrayList<>();
        templates.add("");
        templates.addAll(List.of("letterhead.json", "image.json", "gross.json"));
        return templates;
    }

    /**
     * The case that used to be cut: the standard invoice of this repository, whose first
     * line carries three lines under its row, on the example image template.
     */
    @ParameterizedTest
    @MethodSource("templates")
    void theLinesHangingUnderARowAreOnThePageOfThatRow(String template) {
        SemanticDocument document = Corpus.example("standard-invoice");
        RenderOptions options = template.isEmpty()
                ? RenderOptions.defaults().layout(Layout.GENERIC)
                : RenderOptions.defaults().with(Templates.example(template));

        byte[] pdf = new PdfRenderer().render(document, options);

        assertRowsAreWhole(pdf, "Sensor module SM-100",
                List.of("Sensor module, housing grey", "Nummer der Auftragsposition: 10",
                        "Artikelnummer: SM-100"),
                "examples/standard-invoice on " + (template.isEmpty() ? "no template"
                        : template));
    }

    /** The same holds in the letter layout, whose table is the same table. */
    @Test
    void theSameHoldsInTheLetterLayout() {
        byte[] pdf = new PdfRenderer().render(Corpus.example("standard-invoice"),
                RenderOptions.defaults().with(Templates.example("letter.json")));

        assertRowsAreWhole(pdf, "Sensor module SM-100",
                List.of("Sensor module, housing grey", "Nummer der Auftragsposition: 10",
                        "Artikelnummer: SM-100"),
                "examples/standard-invoice on letter.json");
    }

    /**
     * Eighty lines, each with a line hanging under it, over several pages: not one of the
     * eighty is parted from its own text. The identifier of a line and the word in the line
     * under it are different words, so a page can be asked which of the two it carries.
     *
     * @param layout the layout, both of which draw the same table
     */
    @ParameterizedTest
    @EnumSource(Layout.class)
    void noRowOfALongInvoiceIsPartedFromItsOwnDetails(Layout layout) {
        byte[] pdf = new PdfRenderer().render(Documents.withDetailedLines(80),
                RenderOptions.defaults().layout(layout));

        int pages = Pdf.pages(pdf);
        assertTrue(pages > 2, "eighty detailed lines take more than two pages, not " + pages);
        List<String> parted = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            String text = Pdf.flat(Pdf.textOfPage(pdf, page));
            for (int line = 0; line < 80; line++) {
                if (text.contains("NOTE" + line) && !text.contains("ROW" + line)) {
                    parted.add("line " + line + " on page " + page);
                }
            }
        }
        assertEquals(List.of(), parted, "no detail line is parted from its row");
    }

    /**
     * A block taller than an empty page body is cut, because no page would hold it whole.
     * The cut falls at a line boundary and the page that carries the rest is opened by the
     * identifier of the row, which is what a reader needs to know whose text this is.
     *
     * @param layout the layout, both of which draw the same table
     */
    @ParameterizedTest
    @EnumSource(Layout.class)
    void aBlockTallerThanAPageIsStillCut(Layout layout) {
        for (RenderLanguage language : RenderLanguage.values()) {
            byte[] pdf = new PdfRenderer().render(Documents.withALineDescribedAtLength(3000),
                    RenderOptions.in(language).layout(layout));

            int pages = Pdf.pages(pdf);
            assertTrue(pages > 2, "a description of three thousand words takes more than two "
                    + "pages in " + language + ", not " + pages);
            assertTrue(Pdf.flat(pdf).contains("ROW1 (" + Word.CONTINUED.in(language) + ")"),
                    "and the page that carries the rest says whose text it is");
        }
    }

    /**
     * A row whose own cell is taller than a page is cut as well, and the same applies where
     * the table is the first thing on the page: the heading and the column header are not
     * left alone at the foot of a page for a block that could never be moved.
     *
     * @param layout the layout, both of which draw the same table
     */
    @ParameterizedTest
    @EnumSource(Layout.class)
    void aRowTallerThanAPageDoesNotStrandItsHeading(Layout layout) {
        byte[] pdf = new PdfRenderer().render(Documents.withALineNamedAtLength(3000),
                RenderOptions.defaults().layout(layout));

        int pages = Pdf.pages(pdf);
        assertTrue(pages > 2, "an item name of three thousand words takes more than two "
                + "pages, not " + pages);
        String heading = Word.LINES.in(RenderLanguage.GERMAN);
        for (int page = 1; page <= pages; page++) {
            String text = Pdf.flat(Pdf.textOfPage(pdf, page));
            int at = text.indexOf(heading);
            if (at >= 0) {
                assertTrue(text.substring(at).contains("word0"),
                        "page " + page + " announces the table and shows a row of it");
            }
        }
    }

    /**
     * A quantity and the name of its unit are one statement, and a column boundary does
     * not go through it either. The corpus carries the case that used to break: an
     * invoice of energy readings whose quantities run to four digits and whose units are
     * written out — <i>3.875 Kilowattstunden</i> stood as <i>3.875</i> over
     * <i>Kilowattstunden</i> beside a description column with two hundred points to
     * spare. The generic layout writes the code of the unit and keeps it beside the
     * figure for the same reason.
     *
     * @param layout the layout the invoice is rendered in
     */
    @ParameterizedTest
    @EnumSource(Layout.class)
    void aQuantityKeepsTheNameOfItsUnitBesideIt(Layout layout) {
        byte[] pdf = new PdfRenderer().render(
                Corpus.instance("business-cases/standard/03.01a-INVOICE_ubl.xml"),
                RenderOptions.defaults().layout(layout));
        String unit = layout == Layout.LETTER ? "Kilowattstunden" : "KWH";

        String text = Pdf.text(pdf);

        for (String quantity : List.of("935", "3.875", "1.746")) {
            String cell = quantity + " " + unit;
            assertTrue(text.lines().anyMatch(line -> line.contains(cell)),
                    layout + ": '" + cell + "' stands on one line: " + text);
        }
    }

    /**
     * And it keeps it beside an item name of four hundred characters, which is the case
     * the equal share of the table is there for: the name takes the width that is left,
     * whether it is four hundred characters of words or one word of four hundred
     * characters, and neither of them costs the quantity the name of its unit.
     */
    @Test
    void aQuantityKeepsItsUnitBesideAnItemNameOfFourHundredCharacters() {
        for (String name : List.of("Sensor ".repeat(57).strip(), "S".repeat(400))) {
            SemanticDocument document = Documents.oneLineAtNineteenPerCent().toBuilder()
                    .set(SemanticPath.of("/BG-25/0/BT-129"), SemanticValue.of("3875"))
                    .set(SemanticPath.of("/BG-25/0/BT-130"), SemanticValue.of("KWH"))
                    .set(SemanticPath.of("/BG-25/0/BG-31/BT-153"), SemanticValue.of(name))
                    .build();

            byte[] pdf = new PdfRenderer().render(document,
                    RenderOptions.defaults().layout(Layout.LETTER));
            String text = Pdf.text(pdf);

            assertTrue(text.lines().anyMatch(line -> line.contains("3.875 Kilowattstunden")),
                    "the quantity keeps its unit beside an item name of "
                            + name.length() + " characters: " + text);
            assertTrue(Pdf.shows(text, name), "and the name itself is on the page");
        }
    }

    /**
     * The letter layout gives every row of the table of lines the same rhythm: a hairline
     * closes a row whether or not anything hangs under it, so the only thing that parts a
     * row without a description further from the next row than one with a description is
     * the description itself.
     *
     * <p>It is measured off the paper. A row with two hanging lines says how tall one
     * hanging line is — the distance between the two of them; a row with one hanging line
     * and a row with none then stand at distances that differ by that height and by
     * nothing else worth seeing. Before the hairline closed every row, they differed by it
     * and by the air around it as well, which is what a reader saw as two rhythms.
     */
    @Test
    void everyRowOfTheLetterTableStandsAtTheSameRhythm() {
        byte[] pdf = new PdfRenderer().render(Documents.withLinesDetailedInTurn(9),
                RenderOptions.defaults().layout(Layout.LETTER));
        List<Pdf.Run> runs = Pdf.runs(pdf, 1);

        float hangingLine = baseline(runs, "NOTE2") - baseline(runs, "MARK2");
        float withOne = baseline(runs, "ROW1") - baseline(runs, "ROW2");
        float withNone = baseline(runs, "ROW3") - baseline(runs, "ROW4");

        assertTrue(hangingLine > 5f, "a hanging line is measurable: " + hangingLine);
        assertTrue(Math.abs(withOne - withNone - hangingLine) < 2.5f,
                "a row with one line under it stands one line further from the next row "
                        + "than a row with none: one line is " + hangingLine
                        + ", the two distances are " + withOne + " and " + withNone);
    }

    /**
     * The row that closes the table of lines never stands alone on a page.
     *
     * <p>It is one figure with one label, and what tells a reader what it is a sum of is
     * the column of figures above it. A page break between the rule that ends the table
     * and the row that closes it leaves that figure at the top of a page with no row of
     * the table on it, where it reads as the first row of the block of totals instead.
     * Where the break falls depends on how much of the lower edge a letterhead asks for,
     * so the bottom margin is swept over a range that used to put it exactly there.
     *
     * @param lines how many lines the specimen has
     * @param from  the first bottom margin of the sweep, in points
     * @param to    the last one
     */
    @ParameterizedTest
    @CsvSource({"8, 118, 140", "6, 186, 200"})
    void theRowThatClosesTheTableStandsOnAPageWithARowOfIt(int lines, int from, int to) {
        SemanticDocument document = Documents.withDetailedLines(lines);
        List<String> wrong = new ArrayList<>();
        for (int bottom = from; bottom <= to; bottom++) {
            RenderTemplate template = Templates.of("""
                    {"template": "esj-render-template/0.1", "layout": "letter",
                     "margins": {"first": {"bottom": %d}}}""".formatted(bottom));

            byte[] pdf = new PdfRenderer().render(document,
                    RenderOptions.defaults().with(template));

            for (int page = 1; page <= Pdf.pages(pdf); page++) {
                String text = Pdf.flat(Pdf.textOfPage(pdf, page));
                if (text.contains("Summe aller Positionen") && !text.contains("ROW")) {
                    wrong.add("a bottom margin of " + bottom + " points puts the sum on "
                            + "page " + page + ", which carries no row of the table");
                }
            }
        }
        assertEquals(List.of(), wrong, "the sum of the lines hangs on the table");
    }

    /**
     * And it does not stand alone on a page when the text of the last row flows over one.
     *
     * <p>The rule that ends the table and the row that closes it are asked for with the
     * last row, which holds wherever that row block fits on a page. It does not fit when
     * the description of the last line is taller than a page: the row is then cut, the
     * text flows, and the end of the table used to land on a page of its own with no line
     * of the table on it. The last line of that text now goes over with it, and the page
     * that carries the sum carries a line of the table or the line that says whose text it
     * is.
     *
     * <p>Both ways of being taller than a page are swept: a description hanging under the
     * row, and an item name in a cell of the row itself.
     *
     * @param words   how many words the long text has
     * @param hanging whether it hangs under the row or stands in a cell of it
     * @param from    the first bottom margin of the sweep, in points
     * @param to      the last one
     */
    @ParameterizedTest
    @CsvSource({"1500, true, 50, 69", "1000, true, 50, 70", "1000, true, 86, 107",
                "1500, false, 50, 59", "1500, false, 160, 169"})
    void theEndOfTheTableGoesWithTheTextThatFlowsOverAPage(int words, boolean hanging,
                                                           int from, int to) {
        SemanticDocument document = hanging ? Documents.withALineDescribedAtLength(words)
                : Documents.withALineNamedAtLength(words);
        List<String> wrong = new ArrayList<>();
        for (int bottom = from; bottom <= to; bottom++) {
            RenderTemplate template = Templates.of("""
                    {"template": "esj-render-template/0.1", "layout": "letter",
                     "margins": {"first": {"bottom": %d}}}""".formatted(bottom));

            byte[] pdf = new PdfRenderer().render(document,
                    RenderOptions.defaults().with(template));

            for (int page = 1; page <= Pdf.pages(pdf); page++) {
                String text = Pdf.flat(Pdf.textOfPage(pdf, page));
                if (text.contains("Summe aller Positionen") && !text.contains("ROW")) {
                    wrong.add("a bottom margin of " + bottom + " points puts the sum on "
                            + "page " + page + ", which carries no line of the table");
                }
            }
        }
        assertEquals(List.of(), wrong, "the sum of the lines hangs on the table");
    }

    /**
     * A row block the table moved to a page whole stays whole on it: the lines hanging
     * under the row stand under it, and the next page does not open with them under the
     * name of a row it does not carry.
     *
     * <p>A hanging line asked for room for the line that names its row on a page that has
     * just begun — and asked for it on the page the row stood on as well, where that line
     * is never written. The block of the row had been measured without it, so a row that
     * fit with a line of room to spare kept its cells on one page and sent its period to
     * the next under <i>6 (Fortsetzung)</i>, which is what the sixth line of
     * {@code examples/multiple-lines} did at the foot of page one. The bottom margin is swept
     * so that every row of the example meets the foot of a page, in both layouts.
     *
     * @param layout the layout
     */
    @ParameterizedTest
    @EnumSource(Layout.class)
    void aRowBlockThatFitsIsNeverPartedFromTheLinesUnderIt(Layout layout) {
        SemanticDocument document = Corpus.example("multiple-lines");
        String carried = "(" + Word.CONTINUED.in(RenderLanguage.GERMAN) + ")";
        List<String> wrong = new ArrayList<>();
        for (int bottom = 40; bottom <= 240; bottom += 2) {
            RenderTemplate template = Templates.of("""
                    {"template": "esj-render-template/0.1", "layout": "%s",
                     "margins": {"first": {"bottom": %d}}}"""
                    .formatted(layout == Layout.LETTER ? "letter" : "generic", bottom));

            byte[] pdf = new PdfRenderer().render(document,
                    RenderOptions.defaults().with(template));

            String text = Pdf.flat(pdf);
            if (text.contains(carried)) {
                wrong.add("a bottom margin of " + bottom + " points parts a row: "
                        + text.substring(Math.max(0, text.indexOf(carried) - 40),
                                text.indexOf(carried) + carried.length()));
            }
        }
        assertEquals(List.of(), wrong, "every row of the example stands whole on a page");
    }

    /**
     * The label of the row that closes the table is never written over its figure.
     *
     * <p>It is set flush right, ending where the figure begins, and the figure itself is
     * set flush with the right edge of its column — a figure wider than the column
     * reaches out of it to the left, which is where the two used to meet. Where the room
     * left of the figure is not enough, the label wraps into it like every other label of
     * the layout, and where even a word of it does not fit, the label goes and the figure
     * stays in the column it closes.
     *
     * <p>The claim is read off the paper, character by character: two characters of one
     * baseline whose boxes reach into one another are two characters a reader cannot
     * read, and set text never does that of its own accord.
     *
     * @param language the language of the rendering
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void theLabelOfTheClosingRowIsNeverWrittenOverItsFigure(RenderLanguage language) {
        List<String> wrong = new ArrayList<>();
        for (int right = 280; right <= 460; right += 20) {
            RenderTemplate template = Templates.of("""
                    {"template": "esj-render-template/0.1", "layout": "letter",
                     "margins": {"first": {"left": 40, "right": %d},
                                 "following": {"left": 40, "right": %d}}}"""
                    .formatted(right, right));

            byte[] pdf = new PdfRenderer().render(Corpus.example("standard-invoice"),
                    RenderOptions.in(language).with(template));

            for (int page = 1; page <= Pdf.pages(pdf); page++) {
                for (String pair : Pdf.overprinted(pdf, page)) {
                    wrong.add("a text width of " + (595 - 40 - right) + " points, page "
                            + page + ": " + pair);
                }
            }
        }
        assertEquals(List.of(), wrong, "nothing of the letter is written over anything else");
    }

    /** Returns the baseline of the run of a page that says a text, in points. */
    private static float baseline(List<Pdf.Run> runs, String text) {
        for (Pdf.Run run : runs) {
            if (run.text().contains(text)) {
                return run.baseline();
            }
        }
        throw new AssertionError("no run of the page says '" + text + "'");
    }

    /**
     * Asserts that wherever a hanging line of a row is on a page, the row itself is on that
     * page too.
     */
    private static void assertRowsAreWhole(byte[] pdf, String row, List<String> hanging,
                                           String what) {
        int pages = Pdf.pages(pdf);
        List<String> parted = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            String text = Pdf.flat(Pdf.textOfPage(pdf, page));
            for (String line : hanging) {
                if (Pdf.shows(text, line) && !Pdf.shows(text, row)) {
                    parted.add("'" + line + "' is on page " + page + " and its row is not");
                }
            }
        }
        assertEquals(List.of(), parted, what);
        String whole = Pdf.flat(pdf);
        for (String line : hanging) {
            assertTrue(Pdf.shows(whole, line), what + ": '" + line + "' is in the rendering");
        }
    }
}
