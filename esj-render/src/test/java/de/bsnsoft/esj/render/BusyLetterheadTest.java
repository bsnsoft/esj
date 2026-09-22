package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The letter on paper that is already printed on.
 *
 * <p>A restrained letterhead tells a layout nothing: it keeps to the head of the sheet and
 * everything below is free. Real paper is not like that — it prints a contact block into
 * the corner the information block of DIN 5008 wants, a foot along the lower edge, and a
 * coloured shape along one side. {@link Artwork#busyLetterhead()} draws such a sheet, with
 * its printed zones known here as rectangles, and the claim of these cases is the one that
 * matters on it: <b>nothing the layout writes is printed over what the paper already
 * carries</b>, on the first page and on the pages after it, in both languages.
 *
 * <p>What a template has to state for that is a distance on each of three sides, and
 * nothing else: a right margin that clears the shape, a bottom margin that clears the
 * printed foot, and — only where it wants the information block rather than the reference
 * line — how far down its printed head reaches. The reference line needs none of the
 * third: it stands in the flow between the margins, so it is clear of the corner without
 * being told about it, which is why it is the default.
 *
 * <p>The text is read back out of the rendering with its box on the paper
 * ({@link Pdf#runs(byte[], int)}) rather than asked of the layout, and the paper itself
 * carries no text at all, so every run that comes back is a run the letter wrote.
 */
class BusyLetterheadTest {

    /** One millimetre in points, which is what a business letter is measured in. */
    private static final float MM = 72f / 25.4f;

    /**
     * How much of the lower edge a template gives to the printed foot of this paper.
     *
     * <p>The printed foot is 20 mm tall and the page footer sits {@code FOOTER_INSET}
     * points below the top of the bottom margin, so the margin is the one plus the other
     * and a little air.
     */
    private static final float BOTTOM = 20f * MM + Margins.FOOTER_INSET + 12f;

    /** How far the coloured shape reaches in, which is what the right margin clears. */
    private static final float RIGHT = 62f * MM;

    /**
     * How much of the head of a following sheet is printed on, plus the air over the text.
     *
     * <p>The compact head of a page and the text under it both stand below this line, so
     * it is the one distance the paper's printed head asks the template for.
     */
    private static final float FOLLOWING_TOP = Artwork.FOLLOWING_HEAD_DEPTH + 14f;

    /** The template a sender of this paper writes: four distances and the default line. */
    private static RenderTemplate withTheReferenceLine() {
        return busy("""
                {"template": "esj-render-template/0.1", "layout": "letter",
                 "letterhead": {"first": {"file": "busy.pdf", "page": 1},
                                "following": {"file": "busy.pdf", "page": 2}},
                 "margins": {"first": {"bottom": %s, "right": %s},
                             "following": {"top": %s, "bottom": %s, "right": %s}},
                 "letter": {"information": "line"}}"""
                .formatted(BOTTOM, RIGHT, FOLLOWING_TOP, BOTTOM, RIGHT));
    }

    /** Returns a template of this test whose letterhead is the busy sheet. */
    private static RenderTemplate busy(String json) {
        return Templates.of(json, "busy.pdf", Artwork.busyLetterhead());
    }

    /** The document with the most head data the corpus has: many references, long values. */
    private static SemanticDocument comprehensive() {
        return Corpus.instance("technical-cases/cius/01.01_comprehensive_test_ubl.xml");
    }

    /**
     * Nothing of the letter is printed over the paper: not on the first page, which
     * carries the address field, the reference line and the seller's own details, and not
     * on the pages after it, which carry the compact head, the tables and the closing
     * section.
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void nothingTheLayoutWritesReachesIntoThePrintedMatter(RenderLanguage language) {
        byte[] pdf = new PdfRenderer().render(comprehensive(),
                RenderOptions.in(language).with(withTheReferenceLine()));

        int pages = Pdf.pages(pdf);
        assertTrue(pages > 1, "the comprehensive instance takes more than one page");
        assertEquals(List.of(), overprinted(pdf, pages),
                "every page keeps its text off the printed matter of the paper");
    }

    /** And the same paper with the standard invoice of the examples on it. */
    @Test
    void andNothingOfTheExampleInvoiceDoesEither() {
        byte[] pdf = new PdfRenderer().render(Corpus.example("standard-invoice"),
                RenderOptions.defaults().with(withTheReferenceLine()));

        assertEquals(List.of(), overprinted(pdf, Pdf.pages(pdf)),
                "every page keeps its text off the printed matter of the paper");
    }

    /**
     * The seller's own details are furniture of page one on this paper too: they stand
     * above the page footer, and the page footer stands above the printed foot the
     * template handed over in {@code margins.first.bottom}. Paper with a printed foot is
     * the case that decides whether that margin is a distance the layout honours or a
     * number nobody reads.
     */
    @Test
    void theSellerDetailsStandAboveThePrintedFootOfThePaper() {
        byte[] pdf = new PdfRenderer().render(Corpus.example("standard-invoice"),
                RenderOptions.defaults().with(withTheReferenceLine()));

        float lowest = Float.MAX_VALUE;
        for (Pdf.Run run : Pdf.runs(pdf, 1)) {
            if (run.text().contains("HRB 12345")) {
                lowest = Math.min(lowest, run.baseline());
            }
        }
        assertTrue(lowest < Float.MAX_VALUE,
                "the register of the sender stands in the foot of page one");
        assertTrue(lowest > Artwork.FOOT.top(),
                "and above the printed foot, which ends " + Artwork.FOOT.top()
                        + " points up, while the details sit at " + lowest);
    }

    /**
     * The compact head of a following page stands under the top margin of that page, and
     * the text of the page under the head.
     *
     * <p>It is the claim the case above makes for the printed head of the second sheet,
     * measured instead of looked for: a template states one distance for the head of its
     * following sheet, and everything the layout writes there — its own head first — keeps
     * below it.
     */
    @Test
    void theCompactHeadOfAFollowingPageStandsUnderTheTopMarginOfThatPage() {
        byte[] pdf = new PdfRenderer().render(comprehensive(),
                RenderOptions.defaults().with(withTheReferenceLine()));

        float top = 841.89f - FOLLOWING_TOP;
        for (int page = 2; page <= Pdf.pages(pdf); page++) {
            float highest = 0;
            for (Pdf.Run run : Pdf.runs(pdf, page)) {
                highest = Math.max(highest, run.top());
            }
            assertTrue(highest <= top, "page " + page + " begins at " + highest
                    + ", under the top margin the template states, which is at " + top);
        }
        float head = 0;
        float rest = 0;
        for (Pdf.Run run : Pdf.runs(pdf, 2)) {
            if (run.text().startsWith("Rechnung 1234567")) {
                head = run.top();
            } else {
                rest = Math.max(rest, run.top());
            }
        }
        assertTrue(head > 0, "the compact head names the letter on page two");
        assertTrue(head > rest, "and stands above everything else on it: the head tops at "
                + head + " and the rest at " + rest);
    }

    /**
     * The page footer of a following page stands above the bottom margin of that page,
     * which is where the printed foot of this paper ends.
     */
    @Test
    void thePageFooterOfAFollowingPageStandsAboveThePrintedFoot() {
        byte[] pdf = new PdfRenderer().render(comprehensive(),
                RenderOptions.defaults().with(withTheReferenceLine()));

        int pages = Pdf.pages(pdf);
        assertTrue(pages > 1, "the comprehensive instance takes more than one page");
        for (int page = 2; page <= pages; page++) {
            float lowest = Float.MAX_VALUE;
            for (Pdf.Run run : Pdf.runs(pdf, page)) {
                lowest = Math.min(lowest, run.baseline());
            }
            assertTrue(lowest > Artwork.FOOT.top(), "page " + page + " writes as low as "
                    + lowest + ", and the printed foot ends " + Artwork.FOOT.top()
                    + " points up");
        }
    }

    /**
     * The reference line takes the width the margins leave it: fewer columns on a page
     * whose right side is printed on, and the same fields in them.
     */
    @Test
    void theReferenceLineIsSetInTheWidthTheMarginsLeaveIt() {
        byte[] narrow = new PdfRenderer().render(comprehensive(),
                RenderOptions.defaults().with(withTheReferenceLine()));
        byte[] wide = new PdfRenderer().render(comprehensive(),
                RenderOptions.defaults().layout(Layout.LETTER));

        for (String value : List.of("1234567", "13.04.2018", "65002278", "ABC123456789",
                "PR12345678", "ATU123456789")) {
            assertTrue(Pdf.shows(narrow, value), value + " is on the narrow page");
            assertTrue(Pdf.shows(wide, value), value + " is on the wide one");
        }
        assertTrue(titleBaseline(narrow) < titleBaseline(wide),
                "the narrower text width takes more rows, so the title stands lower: "
                        + titleBaseline(narrow) + " against " + titleBaseline(wide));
    }

    /**
     * Where a template wants the information block instead, it says how far down its
     * printed head reaches and the block starts under it.
     *
     * <p>This paper is not the paper for that choice — its right side is printed on and
     * the block stands over it — and the case says only what it is about: the block is
     * clear of the contact block at the top right, which without the declaration it is
     * not.
     */
    @Test
    void theBlockStartsBelowThePrintedHeadTheTemplateDeclares() {
        RenderTemplate template = busy("""
                {"template": "esj-render-template/0.1", "layout": "letter",
                 "letterhead": {"first": {"file": "busy.pdf", "page": 1}},
                 "margins": {"first": {"bottom": %s}},
                 "letter": {"information": "block", "printedHead": %s}}"""
                .formatted(BOTTOM, 60f * MM));

        byte[] pdf = new PdfRenderer().render(comprehensive(),
                RenderOptions.defaults().with(template));

        List<String> wrong = new ArrayList<>();
        for (Pdf.Run run : Pdf.runs(pdf, 1)) {
            if (Artwork.CONTACT.holds(run)) {
                wrong.add(run.text() + " at " + run.left() + ".." + run.baseline());
            }
        }
        assertEquals(List.of(), wrong, "the block starts below the printed contact block");
        assertTrue(Pdf.textInArea(pdf, 1, 125 * MM, 60 * MM, 75 * MM, 60 * MM)
                        .contains("1234567"),
                "and it carries the head data under it");
    }

    /**
     * Returns how far above the lower edge the title of the letter sits, which is how far
     * down the reference line over it pushed the letter.
     */
    private static float titleBaseline(byte[] pdf) {
        for (Pdf.Run run : Pdf.runs(pdf, 1)) {
            if (run.text().startsWith("Rechnung 1234567")) {
                return run.baseline();
            }
        }
        throw new AssertionError("the title of the letter is on its first page");
    }

    /**
     * Returns the runs of a rendering that stand on printed matter, page by page. The
     * first sheet of this paper and the sheets after it print different things, so each
     * page is asked about the zones of its own sheet.
     */
    private static List<String> overprinted(byte[] pdf, int pages) {
        List<String> wrong = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            List<Artwork.Zone> zones = page == 1
                    ? List.of(Artwork.CONTACT, Artwork.FOOT, Artwork.SIDE)
                    : List.of(Artwork.FOLLOWING_HEAD, Artwork.FOOT, Artwork.SIDE);
            for (Pdf.Run run : Pdf.runs(pdf, page)) {
                for (Artwork.Zone zone : zones) {
                    if (zone.holds(run)) {
                        wrong.add("page " + page + ": \'" + run.text() + "\' at "
                                + run.left() + ".." + run.right() + " / " + run.baseline()
                                + ".." + run.top());
                    }
                }
            }
        }
        return wrong;
    }
}
