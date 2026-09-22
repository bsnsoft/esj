package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * What the layout of the PDF rendering does with a document it has no complete reading
 * for, and what it does when the paper runs out in the middle of something.
 *
 * <p>The coverage tests render documents that carry everything, and a document that
 * carries everything never asks these questions. A price group without its price, a page
 * that ends under a heading and a row whose details would open the next page are all
 * cases of one rule: a value of the document is on a page, and a reader can tell which
 * row, which block and which heading it belongs to.
 */
class PdfLayoutTest {

    /** The column headers of the tables, whose words a page may end with legitimately. */
    private static final List<Word> COLUMNS = List.of(
            Word.COLUMN_LINE, Word.COLUMN_ITEM, Word.COLUMN_QUANTITY, Word.COLUMN_UNIT_PRICE,
            Word.COLUMN_VAT, Word.COLUMN_NET_AMOUNT, Word.COLUMN_REASON, Word.COLUMN_BASE,
            Word.COLUMN_PERCENTAGE, Word.COLUMN_AMOUNT, Word.COLUMN_CATEGORY, Word.COLUMN_RATE,
            Word.COLUMN_TAXABLE, Word.COLUMN_VAT_AMOUNT);

    /** The headings of the sections, in the language the assertions read. */
    private static final List<Word> HEADINGS = List.of(
            Word.SELLER, Word.BUYER, Word.INVOICE_DATA, Word.NOTES, Word.DELIVERY,
            Word.LINES, Word.ALLOWANCES, Word.CHARGES, Word.VAT_BREAKDOWN, Word.TOTALS,
            Word.PAYMENT, Word.PAYEE, Word.TAX_REPRESENTATIVE, Word.SUPPORTING_DOCUMENTS,
            Word.OTHER_TERMS);

    /**
     * A price base quantity without a price says nothing in the unit-price column, so the
     * column shows nothing — but the document states it, and a value the document states
     * is on a page. It is printed under the heading the layout keeps for what it has no
     * column of its own for.
     */
    @Test
    void aPriceBaseQuantityWithoutItsPriceIsStillOnAPage() {
        SemanticDocument document =
                Documents.withPartialPriceDetails(null, "5", "KGM");

        byte[] pdf = new PdfRenderer().render(document, RenderOptions.in(RenderLanguage.ENGLISH));

        assertTrue(Pdf.shows(pdf, "Item price base quantity: 5"),
                "the base quantity hangs under the row of its line");
        assertTrue(Pdf.shows(pdf, "Item price base quantity unit of measure code: KGM"),
                "and so does its unit");
    }

    /** The same holds for a unit without the quantity it belongs to. */
    @Test
    void aPriceBaseUnitWithoutAQuantityIsStillOnAPage() {
        SemanticDocument with = Documents.withPartialPriceDetails("100.00", null, "KGM");
        SemanticDocument without = Documents.withPartialPriceDetails("100.00", null, null);

        byte[] pdf = new PdfRenderer().render(with, RenderOptions.in(RenderLanguage.ENGLISH));

        assertTrue(Pdf.shows(pdf, "Item price base quantity unit of measure code: KGM"),
                "the unit of the price base quantity is printed");
        assertFalse(Arrays.equals(pdf, new PdfRenderer().render(without,
                        RenderOptions.in(RenderLanguage.ENGLISH))),
                "a document that states one more value renders to something else");
    }

    /** Where the group is complete the column reads as it always did. */
    @Test
    void aCompletePriceGroupStillReadsAsOneCell() {
        SemanticDocument document = Documents.withPartialPriceDetails("100.00", "5", "KGM");

        byte[] pdf = new PdfRenderer().render(document, RenderOptions.in(RenderLanguage.ENGLISH));

        assertTrue(Pdf.shows(pdf, "100.00 per 5 KGM"),
                "the price, the quantity it is for and its unit are one cell");
        assertFalse(Pdf.shows(pdf, "Item price base quantity:"),
                "and nothing of the group is printed a second time");
    }

    /**
     * The German localization calls the total without VAT and the total with it the same
     * thing, and the layout says which is which with the words the localization itself
     * has for it rather than with a term identifier.
     */
    @Test
    void theGermanTotalsSayWhichFigureCarriesVat() {
        String text = Pdf.flat(new PdfRenderer().render(Corpus.example("standard-invoice")));

        assertTrue(text.contains("Gesamtsumme netto"), "the total without VAT says netto");
        assertTrue(text.contains("Gesamtsumme brutto"), "and the one with VAT says brutto");
        assertFalse(text.contains("Gesamtsumme (BT-109)"),
                "so neither of them needs a term identifier to be read");
    }

    /**
     * The column header of the totals names the currency of the document, BT-5, and one
     * figure of that table is stated in another: BT-111 is the VAT total in the accounting
     * currency BT-6. Its row names that currency itself, so no figure stands under a
     * header that speaks for a different one.
     */
    @Test
    void theVatTotalInTheAccountingCurrencyNamesItsOwnCurrency() {
        SemanticDocument document = Documents.oneLineAtNineteenPerCent().toBuilder()
                .put("/BT-6", "CHF")
                .put("/BG-22/BT-111", "41.00")
                .build();

        String text = Pdf.flat(new PdfRenderer().render(document,
                RenderOptions.in(RenderLanguage.ENGLISH)));

        assertTrue(text.contains("(CHF) 41.00"),
                "the row of BT-111 names the accounting currency: " + text);
        assertTrue(text.contains("(EUR)"),
                "and the header keeps naming the currency of the document: " + text);
    }

    /** A heading is never the last thing on a page: what it heads begins beside it. */
    @Test
    void aSectionHeadingIsNotLeftAloneAtTheFootOfAPage() {
        for (String example : Corpus.EXAMPLES) {
            SemanticDocument document = Corpus.example(example);
            for (RenderLanguage language : RenderLanguage.values()) {
                for (PageSize size : PageSize.values()) {
                    byte[] pdf = new PdfRenderer().render(document,
                            RenderOptions.in(language).on(size));
                    assertNoPageEndsWithAHeading(pdf, example, language, size);
                }
            }
        }
    }

    /**
     * A detail line of an invoice line does not open a page its row did not reach. A
     * reader of that page would see a description under the repeated header of the table
     * and above the row of the next line, with nothing to say which line it belongs to.
     */
    @Test
    void theDetailsOfALineBeginOnThePageOfItsRow() {
        int lines = 80;
        byte[] pdf = new PdfRenderer().render(Documents.withDetailedLines(lines));

        int pages = Pdf.pages(pdf);
        assertTrue(pages > 2, "eighty lines take more than two pages, not " + pages);
        for (int page = 1; page <= pages; page++) {
            String text = Pdf.flat(Pdf.textOfPage(pdf, page));
            for (int line = 0; line < lines; line++) {
                if (text.contains("NOTE" + line)) {
                    assertTrue(text.contains("ROW" + line),
                            "the detail of line " + line + " is on page " + page
                                    + ", and so is the row it hangs under");
                }
            }
        }
    }

    /**
     * A page does not end with a heading and the column header of the table under it
     * either. The header repeats itself at the top of the next page, so a page that ends
     * with one has spent its last lines announcing a table twice and showing no row of it.
     */
    @Test
    void aSectionHeadingIsNotLeftAtTheFootOfAPageWithOnlyATableHeaderUnderIt() {
        for (String instance : Corpus.instances()) {
            SemanticDocument document = Corpus.instance(instance);
            for (RenderLanguage language : RenderLanguage.values()) {
                for (PageSize size : PageSize.values()) {
                    byte[] pdf = new PdfRenderer().render(document,
                            RenderOptions.in(language).on(size));
                    assertNoPageEndsWithAHeading(pdf, instance, language, size);
                }
            }
        }
    }

    /**
     * Where the details of a row run onto the next page, the identifier of the row is
     * repeated above them. Under the repeated header a description with no row beside it
     * belongs to no line a reader can see, and the next row on the page is not the one it
     * hangs under.
     */
    @Test
    void detailsThatRunOntoTheNextPageSayWhichRowTheyBelongTo() {
        for (RenderLanguage language : RenderLanguage.values()) {
            byte[] pdf = new PdfRenderer().render(Documents.withALineDescribedAtLength(3000),
                    RenderOptions.in(language));

            int pages = Pdf.pages(pdf);
            assertTrue(pages > 2, "a description of three thousand words takes more than two "
                    + "pages, not " + pages);
            String marker = "ROW1 (" + Word.CONTINUED.in(language) + ")";
            for (int page = 2; page <= pages; page++) {
                String text = Pdf.flat(Pdf.textOfPage(pdf, page));
                if (text.contains("word")) {
                    assertTrue(text.contains(marker),
                            "page " + page + " in " + language.code() + " carries the tail of "
                                    + "the description and says whose it is, and it begins '"
                                    + text.substring(0, Math.min(120, text.length())) + "'");
                }
            }
        }
    }

    /**
     * The same holds for a row whose own cell was too tall for the page it began on. The
     * head of the row — the line identifier, the quantity, the price — was drawn on the
     * page before, so the rest of it opens the next page with the column of the identifier
     * already spent and nothing but the repeated column header above it. It gets the same
     * carry-over line the detail lines get.
     */
    @Test
    void aRowCutAtAPageBreakSaysWhichRowItIs() {
        for (RenderLanguage language : RenderLanguage.values()) {
            byte[] pdf = new PdfRenderer().render(Documents.withALineNamedAtLength(400),
                    RenderOptions.in(language));

            int pages = Pdf.pages(pdf);
            assertTrue(pages > 1, "an item name of four hundred words takes more than one "
                    + "page, not " + pages);
            String marker = "ROW1 (" + Word.CONTINUED.in(language) + ")";
            for (int page = 2; page <= pages; page++) {
                String text = Pdf.flat(Pdf.textOfPage(pdf, page));
                if (text.contains("word")) {
                    assertTrue(text.contains(marker),
                            "page " + page + " in " + language.code() + " carries the tail of "
                                    + "the row and says whose it is, and it begins '"
                                    + text.substring(0, Math.min(120, text.length())) + "'");
                }
            }
        }
    }

    private static void assertNoPageEndsWithAHeading(byte[] pdf, String example,
                                                     RenderLanguage language, PageSize size) {
        int pages = Pdf.pages(pdf);
        for (int page = 1; page <= pages; page++) {
            String last = lastLineBeforeTheFooter(Pdf.textOfPage(pdf, page));
            String lastWithARowUnderIt =
                    lastLineBeforeTheTableHeader(Pdf.textOfPage(pdf, page), language);
            for (Word heading : HEADINGS) {
                assertFalse(heading.in(language).equals(last),
                        example + " in " + language.code() + " on " + size + ": page " + page
                                + " ends with the heading '" + last + "' and nothing under it");
                assertFalse(heading.in(language).equals(lastWithARowUnderIt),
                        example + " in " + language.code() + " on " + size + ": page " + page
                                + " ends with the heading '" + lastWithARowUnderIt + "' and the "
                                + "header of its table, and no row under the two");
            }
        }
    }

    /**
     * Returns the last line of a page that is not its footer. The footer is written after
     * the content of the page, in a stream of its own, so it is the last line the text
     * extractor reads and the line before it is the last one a reader sees.
     */
    private static String lastLineBeforeTheFooter(String page) {
        List<String> lines = page.lines().map(String::strip)
                .filter(line -> !line.isEmpty()).toList();
        if (lines.size() < 2) {
            return "";
        }
        return lines.get(lines.size() - 2);
    }

    /**
     * Returns the last line of a page that is neither its footer nor part of the header of
     * a table. A heading, the header it stands over and nothing else is a page that
     * announces a table it never begins, and the header alone is not what a reader came
     * for.
     */
    private static String lastLineBeforeTheTableHeader(String page, RenderLanguage language) {
        List<String> lines = new ArrayList<>(page.lines().map(String::strip)
                .filter(line -> !line.isEmpty()).toList());
        if (lines.size() < 2) {
            return "";
        }
        lines.remove(lines.size() - 1);
        while (!lines.isEmpty() && isTableHeader(lines.get(lines.size() - 1), language)) {
            lines.remove(lines.size() - 1);
        }
        return lines.isEmpty() ? "" : lines.get(lines.size() - 1);
    }

    /**
     * Tells whether a line of a page is a line of the header of a table: every word of it
     * is a word of a column header, or the currency the money columns are labelled with.
     */
    private static boolean isTableHeader(String line, RenderLanguage language) {
        Set<String> words = new LinkedHashSet<>();
        for (Word column : COLUMNS) {
            words.addAll(Arrays.asList(column.in(language).split("\\s+")));
        }
        for (String word : line.split("\\s+")) {
            if (!word.isEmpty() && !words.contains(word) && !word.matches("\\(\\p{Lu}{3}\\)")) {
                return false;
            }
        }
        return true;
    }
}
