package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The gross layout a consumer expects, and the rule it is built to keep.
 *
 * <p>EN 16931 invoices are net invoices, and a consumer bought the fitting for 99.99
 * including VAT. The figures the buyer was shown are recorded by a model extension, not by
 * a core business term, and the branded template of this project gives them a place: a
 * column beside the net unit price, one beside the net line amount, one for the VAT of a
 * line where one was shown, and a row among the totals. The net figures, the VAT breakdown
 * and the totals of the standard stay on the page beside them, and every displayed figure
 * is marked as one.
 *
 * <p>{@link Templates#withB2c()} is the registry of {@code model/b2c/}, the one
 * {@code esj render --extension b2c} works against. A build without it renders the same
 * page, because a template carries the identifier, the position, the label and the
 * semantic data type of every term it places; both are tested, because both happen.
 */
class GrossLayoutTest {

    /** The net figures of the document, which a gross layout does not replace. */
    private static final List<String> NET_GERMAN =
            List.of("84,03", "252,09", "16,80", "268,89", "51,09", "319,98");

    /** The figures the buyer was shown, which only the extension carries. */
    private static final List<String> DISPLAYED_GERMAN =
            List.of("99,99", "299,97", "47,88", "19,99", "319,96");

    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void theDisplayedFiguresAreOnThePageBesideTheNetOnes(RenderLanguage language) {
        String text = Pdf.text(render(language, Templates.withB2c()));

        for (String net : localized(NET_GERMAN, language)) {
            assertTrue(Pdf.shows(text, net), "the net figure " + net + " is on the page");
        }
        for (String displayed : localized(DISPLAYED_GERMAN, language)) {
            assertTrue(Pdf.shows(text, displayed),
                    "the displayed figure " + displayed + " is on the page");
        }
        assertTrue(Pdf.shows(text, Word.VAT_BREAKDOWN.in(language)),
                "and the VAT breakdown of the standard is still there");
    }

    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void everyDisplayedFigureIsLabelledAsOne(RenderLanguage language) {
        String text = Pdf.text(render(language, Templates.withB2c()));
        String displayed = Word.DISPLAYED.in(language);

        for (Placement placement : Templates.example("gross.json").placements()) {
            String header = placement.label(language) + " (" + displayed;
            assertTrue(Pdf.shows(text, header),
                    "the place of " + placement.term() + " is labelled '" + header
                            + "' and the page reads " + Pdf.flat(text).substring(0, 400));
        }
        assertTrue(Pdf.shows(text, Word.DISPLAYED_NOTE.in(language)),
                "and the note says what a displayed figure is");
    }

    /**
     * The template brings the label and the semantic data type, so a build whose registry
     * has never heard of the extension still shows the figure under a name and written as
     * an amount. That is what lets the gross template ship before the extension does.
     */
    @Test
    void aRegistryThatDoesNotKnowTheExtensionChangesNothing() {
        String known = Pdf.flat(render(RenderLanguage.GERMAN, Templates.withB2c()));
        String unknown = Pdf.flat(render(RenderLanguage.GERMAN, Registry.en16931()));

        assertEquals(known, unknown,
                "the page is the same whether or not the registry knows the four terms");
        assertTrue(unknown.contains("99,99"),
                "and the figure is written as an amount either way");
    }

    /**
     * The template and the registry name the same four terms, with the same semantic data
     * types. The template can be read without a registry, so nothing in the renderer holds
     * the two together; this test does, and it is what makes the placed figure of a build
     * that has the registry the placed figure of a build that has not.
     */
    @Test
    void theTemplateAndTheRegistryAgree() {
        Registry registry = Templates.withB2c();

        List<Placement> placements = Templates.example("gross.json").placements();

        assertEquals(List.of("BT-B2C-001", "BT-B2C-003", "BT-B2C-002", "BT-B2C-010"),
                placements.stream().map(Placement::term).toList(),
                "the template places the terms of the extension registry");
        for (Placement placement : placements) {
            Term term = registry.term(placement.term()).orElseThrow(() ->
                    new AssertionError("the registry knows " + placement.term()));
            assertEquals(term.datatype().orElseThrow(), placement.type(),
                    "the type the template carries for " + placement.term()
                            + " is the type the registry gives it");
            for (RenderLanguage language : RenderLanguage.values()) {
                assertNotNull(placement.label(language),
                        placement.term() + " is labelled in " + language.code());
            }
        }
    }

    /**
     * A placed value is placed once. The last section of the rendering prints what no
     * section had a place for, and a figure that stood in its column must not stand there
     * again.
     */
    @Test
    void aPlacedFigureIsNotPrintedTwice() {
        String text = Pdf.flat(render(RenderLanguage.GERMAN, Templates.withB2c()));

        assertEquals(1, occurrences(text, "319,96"),
                "the displayed total stands in its row and nowhere else");
        assertFalse(text.contains("(B2C)"),
                "and no placed term falls through to the generic marking of an extension");
    }

    /**
     * The places a template gives the terms of an extension are places of the layout, not
     * of one layout. The letter draws the same four figures the generic layout draws, in
     * the columns of its line table and in its block of totals, and marks none of them as
     * an extension value that had nowhere to go.
     */
    @ParameterizedTest
    @EnumSource(Layout.class)
    void aTemplatePlacesItsTermsInEitherLayout(Layout layout) {
        String text = Pdf.flat(new PdfRenderer(Templates.withB2c())
                .render(Documents.grossB2cInvoice(), RenderOptions.defaults()
                        .with(Templates.example("gross.json")).layout(layout)));

        for (String displayed : DISPLAYED_GERMAN) {
            assertTrue(Pdf.shows(text, displayed),
                    "the displayed figure " + displayed + " is in the " + layout + " layout");
        }
        for (String net : NET_GERMAN) {
            assertTrue(Pdf.shows(text, net),
                    "and the net figure " + net + " stands beside it");
        }
        assertFalse(text.contains("(B2C)"),
                "and no placed term falls through to the generic marking of an extension");
    }

    /**
     * A template that places terms costs a document without them nothing: no column, no
     * row, no note. The same template renders a business invoice as the generic layout
     * renders it.
     */
    @Test
    void aDocumentWithoutTheExtensionGetsNoGrossColumn() {
        SemanticDocument business = Corpus.example("standard-invoice");

        String text = Pdf.flat(new PdfRenderer(Templates.withB2c()).render(business,
                RenderOptions.defaults().with(Templates.example("gross.json"))));

        assertFalse(text.contains(Word.DISPLAYED.in(RenderLanguage.GERMAN)),
                "nothing on the page is marked as displayed");
        assertFalse(text.contains(Word.DISPLAYED_NOTE.in(RenderLanguage.GERMAN)),
                "and the note is not printed either");
    }

    /**
     * Without a template the same document is the generic net rendering: the extension
     * values are printed as values of an extension, under their own term and marked with
     * its namespace, and no column of the layout is theirs.
     */
    @Test
    void withoutATemplateTheExtensionIsJustAValue() {
        String text = Pdf.flat(new PdfRenderer(Templates.withB2c())
                .render(Documents.grossB2cInvoice()));

        assertTrue(text.contains("(B2C)"), "the values are marked as an extension's");
        assertTrue(text.contains("99,99"), "and printed");
        assertFalse(text.contains(Word.DISPLAYED.in(RenderLanguage.GERMAN)),
                "and nothing of the gross layout appears");
    }

    @Test
    void theGrossRenderingIsTheSameBytesTwice() {
        assertArrayEquals(render(RenderLanguage.GERMAN, Templates.withB2c()),
                render(RenderLanguage.GERMAN, Templates.withB2c()),
                "a consumer invoice on the gross template renders to the same bytes twice");
    }

    /**
     * The columns a template adds do not squeeze the columns of the layout into the words
     * of their headers. A column gives up only what neither its header nor its own cells
     * need, so every figure of a line still stands whole on one line of the page.
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void theColumnsATemplateAddsDoNotBreakTheCellsOfTheOthers(RenderLanguage language) {
        String text = Pdf.text(render(language, Templates.withB2c()));

        List<String> figures = new ArrayList<>(localized(NET_GERMAN, language));
        figures.addAll(localized(DISPLAYED_GERMAN, language));
        for (String figure : figures) {
            assertTrue(text.lines().anyMatch(line -> line.contains(figure)),
                    "the figure " + figure + " stands whole on one line: " + text);
        }
    }

    /**
     * No word of a column header is split.
     *
     * <p>A header wraps between words, and where one word is wider than its column the
     * table takes the width from the columns that have it to spare or sets the header a
     * size smaller. It used to be cut wherever it ran out of column: with the two gross
     * places of the B2C extension beside the columns of the layout, a German line table
     * printed <i>Bruttoeinzelpr / eis</i> and <i>Bruttobetra / g der Position</i>.
     *
     * <p>The template of this case carries those two labels, so that what is measured is
     * the table rather than the wording of an example: a template may name a term
     * whatever it names it. Two widths are asked, because it is the same question twice —
     * the full text width of A4, where eight columns stand beside each other, and a text
     * width of 375 points, which is what a letterhead printed along both edges leaves and
     * what used to break the words of the layout\'s own headers as well. Both layouts are
     * asked, because the two draw their tables with the same table.
     *
     * @param side   the left and right margin of the page, in points
     * @param layout the layout, as a template names it
     */
    @ParameterizedTest
    @CsvSource({"56, generic", "110, generic", "56, letter", "110, letter"})
    void noWordOfAColumnHeaderIsSplit(int side, String layout) {
        RenderTemplate template = Templates.of("""
                {"template": "esj-render-template/0.1", "layout": "%s",
                 "margins": {"first": {"left": %d, "right": %d},
                             "following": {"left": %d, "right": %d}},
                 "extensionTerms": [
                   {"term": "BT-B2C-001", "position": "line.unitPrice",
                    "type": "UnitPriceAmount",
                    "label": {"en": "Gross unit price", "de": "Bruttoeinzelpreis"}},
                   {"term": "BT-B2C-002", "position": "line.amount", "type": "Amount",
                    "label": {"en": "Gross line amount", "de": "Bruttobetrag der Position"}}
                 ]}""".formatted(layout, side, side, side, side));

        byte[] pdf = new PdfRenderer(Templates.withB2c()).render(
                Documents.grossB2cInvoice(), RenderOptions.defaults().with(template));

        String header = String.join(" ", headerRuns(pdf));
        for (String word : List.of("Nr.", "Bezeichnung", "Menge", "Einzelpreis", "netto",
                "USt.", "Nettobetrag", "Bruttoeinzelpreis", "Bruttobetrag", "angezeigt")) {
            assertTrue(header.contains(word), "'" + word + "' stands whole in the header of"
                    + " a table set in " + (595 - 2 * side) + " points in the " + layout
                    + " layout: " + header);
        }
    }

    /**
     * Returns the runs of the column header of the line table: what stands between the
     * heading of the table and its first row.
     */
    private static List<String> headerRuns(byte[] pdf) {
        float heading = 0;
        float firstRow = 0;
        for (Pdf.Run run : Pdf.runs(pdf, 1)) {
            if (run.text().equals(Word.LINES.in(RenderLanguage.GERMAN))) {
                heading = run.baseline();
            } else if (run.text().equals("Shower fitting")) {
                firstRow = run.top();
            }
        }
        assertTrue(heading > 0, "the heading of the line table is on the first page");
        assertTrue(firstRow > 0, "and so is its first row");
        List<String> runs = new ArrayList<>();
        for (Pdf.Run run : Pdf.runs(pdf, 1)) {
            if (run.top() < heading && run.top() > firstRow) {
                runs.add(run.text());
            }
        }
        return runs;
    }

    /** Renders the consumer invoice on the gross template. */
    private static byte[] render(RenderLanguage language, Registry registry) {
        return new PdfRenderer(registry).render(Documents.grossB2cInvoice(),
                RenderOptions.in(language).with(Templates.example("gross.json")));
    }

    /** Returns the figures as the language writes them. */
    private static List<String> localized(List<String> german, RenderLanguage language) {
        return language == RenderLanguage.GERMAN ? german
                : german.stream().map(GrossLayoutTest::swapDecimalMarks).toList();
    }

    /** Returns a figure with its decimal mark and its group mark exchanged. */
    private static String swapDecimalMarks(String german) {
        StringBuilder swapped = new StringBuilder(german.length());
        for (int at = 0; at < german.length(); at++) {
            char mark = german.charAt(at);
            swapped.append(mark == '.' ? ',' : mark == ',' ? '.' : mark);
        }
        return swapped.toString();
    }

    private static int occurrences(String text, String value) {
        int seen = 0;
        int at = text.indexOf(value);
        while (at >= 0) {
            seen++;
            at = text.indexOf(value, at + 1);
        }
        return seen;
    }
}
