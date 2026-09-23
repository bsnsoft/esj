package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The letter layout: what a recipient sees, and where.
 *
 * <p>The claims here are the ones a business letter has to keep. The recipient stands in
 * the address field a window envelope shows, the facts a reader looks for first stand in
 * the block beside it, the document says by name what kind of document it is, a code a
 * reader does not read is written under its name and listed once under the closing
 * heading, and every page after the first says what letter it belongs to. Underneath all
 * of that the rule of this module is unchanged: the figures are the net ones of
 * EN 16931-1, nothing is derived, and nothing of the document is lost — the last of which
 * is {@link LetterCoverageTest} over the whole corpus.
 *
 * <p>The blocks are asked for by their place on the paper rather than by their order in
 * the text, because a window envelope does not read text: it has a hole at a distance from
 * the top left corner of the paper, and either the recipient is behind it or the letter
 * cannot be posted.
 */
class LetterLayoutTest {

    /** One millimetre in points, which is what a business letter is measured in. */
    private static final float MM = 72f / 25.4f;

    /** The document most of the cases render: a small invoice with everything in it. */
    private static SemanticDocument invoice() {
        return Corpus.example("standard-invoice");
    }

    /** Returns the options of a letter in a language, without a template. */
    private static RenderOptions letter(RenderLanguage language) {
        return RenderOptions.in(language).layout(Layout.LETTER);
    }

    // ---------------------------------------------------------------- which layout runs

    /**
     * The letter is the default. A caller that names no layout gets the letter a business
     * sends, and so does a caller whose template names none; the generic layout, the shape
     * of the semantic model, is asked for by name.
     */
    @Test
    void theLetterIsWhatACallerGetsWithoutAsking() {
        byte[] letter = new PdfRenderer().render(invoice(),
                RenderOptions.defaults().layout(Layout.LETTER));
        RenderTemplate silent = Templates.of("{\"template\": \"esj-render-template/0.1\"}");

        assertEquals(Layout.LETTER, RenderOptions.DEFAULT_LAYOUT, "the default is the letter");
        assertArrayEquals(letter, new PdfRenderer().render(invoice()),
                "no layout named is the letter layout");
        assertArrayEquals(
                new PdfRenderer().render(invoice(),
                        RenderOptions.defaults().with(silent).layout(Layout.LETTER)),
                new PdfRenderer().render(invoice(), RenderOptions.defaults().with(silent)),
                "and a template that names no layout leaves it at the letter");
        assertNotEquals(Corpus.sha256(letter), Corpus.sha256(new PdfRenderer().render(invoice(),
                        RenderOptions.defaults().layout(Layout.GENERIC))),
                "the generic layout is another rendering");
    }

    /** A template may choose the layout, and the caller's own choice wins over it. */
    @Test
    void theTemplateChoosesTheLayoutAndTheCallerOverrulesIt() {
        RenderTemplate letter = Templates.example("letter.json");
        RenderTemplate generic = Templates.example("letterhead.json");

        String fromLetter = Pdf.flat(new PdfRenderer().render(invoice(),
                RenderOptions.defaults().with(letter)));
        String overruled = Pdf.flat(new PdfRenderer().render(invoice(),
                RenderOptions.defaults().with(letter).layout(Layout.GENERIC)));
        String fromGeneric = Pdf.flat(new PdfRenderer().render(invoice(),
                RenderOptions.defaults().with(generic)));
        String overruledToALetter = Pdf.flat(new PdfRenderer().render(invoice(),
                RenderOptions.defaults().with(generic).layout(Layout.LETTER)));

        assertTrue(fromLetter.contains(Word.FURTHER_DETAILS.in(RenderLanguage.GERMAN)),
                "the template asked for the letter layout and got it");
        assertFalse(fromLetter.contains(Word.SELLER.in(RenderLanguage.GERMAN) + " "),
                "which has no block of parties");
        assertTrue(overruled.contains(Word.SELLER.in(RenderLanguage.GERMAN)),
                "and a caller that names the generic layout gets that one: " + overruled);
        assertTrue(fromGeneric.contains(Word.SELLER.in(RenderLanguage.GERMAN) + " "),
                "a template that names the generic layout gets it over the default: "
                        + fromGeneric);
        assertFalse(overruledToALetter.contains(Word.SELLER.in(RenderLanguage.GERMAN) + " "),
                "and a caller that names the letter gets the letter on it");
    }

    // ---------------------------------------------------------------- the head of the letter

    /**
     * The recipient is behind the window of the envelope: inside the field of DIN 5008
     * form B, 85 by 45 mm, 20 mm from the left edge of the paper and 45 mm below its top.
     * The sender stands in the first 5 mm of that field, as a letter shows it.
     */
    @ParameterizedTest
    @EnumSource(PageSize.class)
    void theRecipientStandsInTheAddressField(PageSize size) {
        byte[] pdf = new PdfRenderer().render(invoice(),
                letter(RenderLanguage.GERMAN).on(size));

        String field = Pdf.textInArea(pdf, 1, 20 * MM, 45 * MM, 85 * MM, 45 * MM);

        assertTrue(field.contains("Muster AG"), "the buyer is in the field: " + field);
        assertTrue(field.contains("Beispielallee 3"), "with its street");
        assertTrue(field.contains("20095 Musterstadt"), "its post code and its city");
        assertTrue(field.contains("Deutschland"), "and its country by name");
        assertTrue(field.startsWith("Example GmbH"),
                "the sender stands above it, as an envelope shows it: " + field);
    }

    /**
     * The address field is the one block of the letter the margins of a template never
     * move. A window envelope has its hole where it has it: form B 45 mm and form A 27 mm
     * below the top edge, 20 mm from the left, 85 by 45 mm, whatever the template says
     * about the rest of the page. Everything else follows the margins — the title stands
     * at the left margin the template states, which is how the case tells that the
     * margins were read at all.
     *
     * @param form the form of the address field, as a template names it
     */
    @ParameterizedTest
    @ValueSource(strings = {"din5008-b", "din5008-a"})
    void theAddressFieldStaysWhereDin5008PutsItWhateverTheMargins(String form) {
        float top = "din5008-b".equals(form) ? 45 * MM : 27 * MM;
        RenderTemplate template = Templates.of("""
                {"template": "esj-render-template/0.1", "layout": "letter",
                 "letter": {"addressWindow": "%s"},
                 "margins": {"first": {"top": 30, "left": 90, "right": 70, "bottom": 80},
                             "following": {"top": 90, "left": 90, "right": 70,
                                           "bottom": 80}}}""".formatted(form));

        byte[] pdf = new PdfRenderer().render(invoice(), RenderOptions.defaults().with(template));

        float height = Pdf.pageSize(pdf, 1)[1];
        List<String> wrong = new ArrayList<>();
        float recipientTop = Float.NEGATIVE_INFINITY;
        for (Pdf.Run run : Pdf.runs(pdf, 1)) {
            boolean sender = run.text().startsWith("Example GmbH · Musterweg 12");
            // The seller's own country stands in the foot of the page as well, so the
            // recipient is looked for in the upper half of it.
            boolean recipient = List.of("Muster AG", "Beispielallee 3", "20095 Musterstadt",
                    "Deutschland").contains(run.text()) && run.baseline() > height / 2;
            if (!sender && !recipient) {
                continue;
            }
            if (Math.abs(run.left() - 20 * MM) > 0.5f) {
                wrong.add("'" + run.text() + "' starts " + run.left() + " points from the left");
            }
            if (run.lineTop() > height - top + 0.5f || run.baseline() < height - top - 45 * MM) {
                wrong.add("'" + run.text() + "' stands outside the field, at " + run.baseline());
            }
            if (sender && Math.abs(run.lineTop() - (height - top)) > 0.5f) {
                wrong.add("the sender line does not open the field: " + run.lineTop());
            }
            if (recipient) {
                recipientTop = Math.max(recipientTop, run.lineTop());
            }
        }
        assertEquals(List.of(), wrong, "the field is where DIN 5008 puts it");
        assertEquals(height - top - 5 * MM, recipientTop, 0.5f,
                "the recipient begins under the sender band of 5 mm");
        for (Pdf.Run run : Pdf.runs(pdf, 1)) {
            if (run.text().equals("Rechnung RE-2026-0042")) {
                assertEquals(90f, run.left(), 0.5f,
                        "and the title keeps the left margin the template states");
                return;
            }
        }
        throw new AssertionError("the title of the letter is on its first page");
    }

    /**
     * The head data — the number, the dates and the references — stands in the reference
     * line: in the flow under the address zone and over the title, across the text width,
     * and not in the corner at the top right. That corner is where a letterhead prints its
     * own contact block, and the reference line is what a letter uses instead.
     */
    @ParameterizedTest
    @EnumSource(PageSize.class)
    void theHeadDataStandsInTheReferenceLine(PageSize size) {
        byte[] pdf = new PdfRenderer().render(invoice(),
                letter(RenderLanguage.GERMAN).on(size));

        String line = Pdf.textInArea(pdf, 1, 20 * MM, 95 * MM, 180 * MM, 45 * MM);

        for (String value : List.of("RE-2026-0042", "03.02.2026", "05.03.2026", "KOST-4711",
                "BE-2026-0091", "RV-2025-118")) {
            assertTrue(line.contains(value),
                    "the reference line carries " + value + ": " + line);
        }
        assertEquals("", Pdf.textInArea(pdf, 1, 125 * MM, 45 * MM, 75 * MM, 45 * MM).strip(),
                "and the corner at the top right is free");
        int title = Pdf.flat(pdf).indexOf("Rechnung RE-2026-0042");
        assertTrue(title > Pdf.flat(pdf).indexOf("KOST-4711"),
                "the line stands over the title of the letter");
    }

    /**
     * A template may have it the other way, for paper whose top right is free: the block
     * of DIN 5008, at 125 mm from the left edge and 45 mm below the top. The two carry the
     * same facts — where they stand is a decision about the paper and not about the
     * invoice.
     */
    @ParameterizedTest
    @EnumSource(PageSize.class)
    void aTemplateMayPutTheHeadDataInTheBlockBesideTheAddressField(PageSize size) {
        RenderTemplate template = Templates.of("""
                {"template": "esj-render-template/0.1", "layout": "letter",
                 "letter": {"information": "block"}}""");

        byte[] pdf = new PdfRenderer().render(invoice(),
                RenderOptions.defaults().on(size).with(template));

        String block = Pdf.textInArea(pdf, 1, 125 * MM, 45 * MM, 75 * MM, 60 * MM);
        for (String value : List.of("RE-2026-0042", "03.02.2026", "05.03.2026", "KOST-4711",
                "BE-2026-0091", "RV-2025-118")) {
            assertTrue(block.contains(value),
                    "the information block carries " + value + ": " + block);
        }
        String under = Pdf.textInArea(pdf, 1, 20 * MM, 95 * MM, 100 * MM, 25 * MM);
        assertFalse(under.contains("KOST-4711") || under.contains("BE-2026-0091"),
                "and there is no reference line under the address zone: " + under);
    }

    /**
     * The seller's VAT identifier stands in the head data too, under the word a business
     * letter uses for it, and stands there once: the foot of a letter is on its last page,
     * and a recipient who checks a business invoice by that number reads it on the page in
     * front of them.
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void theSellersVatIdentifierStandsInTheHeadData(RenderLanguage language) {
        SemanticDocument document = invoice().toBuilder()
                .put("/BG-4/BT-32", "12/345/67890").build();

        byte[] pdf = new PdfRenderer().render(document, letter(language));
        String line = Pdf.textInArea(pdf, 1, 20 * MM, 95 * MM, 180 * MM, 45 * MM);
        String flat = Pdf.flat(pdf);

        assertTrue(line.contains(Word.VAT_IDENTIFIER.in(language)),
                "the VAT identifier is labelled in the reference line: " + line);
        assertTrue(line.contains("DE123456789"), "with its value under the label: " + line);
        assertTrue(line.contains(Word.TAX_NUMBER.in(language)),
                "and the tax number beside it: " + line);
        assertTrue(line.contains("12/345/67890"), "with its value: " + line);
        assertEquals(flat.indexOf("DE123456789"), flat.lastIndexOf("DE123456789"),
                "the VAT identifier is on the letter once: " + flat);
        assertEquals(flat.indexOf("12/345/67890"), flat.lastIndexOf("12/345/67890"),
                "and so is the tax number: " + flat);
    }

    /**
     * A value too wide for one column of the reference line takes two, and one too wide
     * for two is wrapped inside its own cell. Either way it stays inside the room it was
     * given: the cell beside it carries a different fact, and a reader who cannot tell
     * where one ends reads the wrong one.
     */
    @Test
    void aValueOfTheReferenceLineNeverOverprintsTheCellBesideIt() {
        String order = "PO-" + "8".repeat(120);
        SemanticDocument document = invoice().toBuilder()
                .set(SemanticPath.of("/BT-13"), SemanticValue.of(order))
                .set(SemanticPath.of("/BT-12"), SemanticValue.of("C-" + "4".repeat(40)))
                .build();

        byte[] pdf = new PdfRenderer().render(document, letter(RenderLanguage.GERMAN));

        List<Pdf.Run> runs = Pdf.runs(pdf, 1);
        assertTrue(Pdf.shows(pdf, order), "the long value is on the page whole");
        for (Pdf.Run run : runs) {
            assertTrue(run.right() <= 200 * MM + 1f,
                    "'" + run.text() + "' stays inside the text width: " + run.right());
        }
    }

    /**
     * With no address field the recipient is written at the head of the letter instead:
     * where the envelope is not what the letter is for, the addressee is still the first
     * thing on it and stands above the title, not under the closing heading among the
     * values the layout had no place for. What goes with the field goes with it — the
     * sender line an envelope shows over the addressee is not written.
     */
    @Test
    void anAddressFieldCanBeLeftOut() {
        RenderTemplate template = Templates.of("""
                {"template": "esj-render-template/0.1", "layout": "letter",
                 "letter": {"addressWindow": "none"}}""");

        byte[] pdf = new PdfRenderer().render(invoice(), RenderOptions.defaults().with(template));
        String flat = Pdf.flat(pdf);

        assertFalse(flat.contains("Example GmbH · Musterweg 12"),
                "no address field means no sender line over one: " + flat);
        int buyer = flat.indexOf("Muster AG");
        int title = flat.indexOf("Rechnung RE-2026-0042");
        assertTrue(buyer >= 0, "the buyer is on the page: " + flat);
        assertTrue(title > buyer, "and stands above the title of the letter: " + flat);
        String closing = flat.substring(
                flat.indexOf(Word.FURTHER_DETAILS.in(RenderLanguage.GERMAN)));
        for (String left : List.of("Muster AG", "Beispielallee 3", "20095 Musterstadt",
                "/BG-7/BG-8/BT-55")) {
            assertFalse(closing.contains(left),
                    "'" + left + "' is not left to the closing heading: " + closing);
        }
    }

    // ---------------------------------------------------------------- codes by name

    /**
     * The title says what kind of document this is, by name and not by code, and the code
     * stands once under the closing heading so that the page hides nothing.
     */
    @Test
    void theTitleNamesTheKindOfDocumentAndTheCodeIsListedOnce() {
        String text = Pdf.flat(new PdfRenderer().render(invoice(), letter(RenderLanguage.GERMAN)));

        assertTrue(text.contains("Rechnung RE-2026-0042"), "the title: " + text);
        assertTrue(text.contains(Word.CODE_DOCUMENT_TYPE.in(RenderLanguage.GERMAN)
                + " 380 = Rechnung"), "and the code, once, under the closing heading");
    }

    /** A unit, a means of payment and a VAT category are written under their names too. */
    @Test
    void aUnitAMeansOfPaymentAndAVatCategoryAreWrittenUnderTheirNames() {
        RenderLanguage german = RenderLanguage.GERMAN;
        String text = Pdf.flat(new PdfRenderer().render(invoice(), letter(german)));

        assertTrue(text.contains("10 Stück"), "the quantity carries the name of its unit");
        assertTrue(text.contains("8 Stunde"), "and so does the next one");
        assertTrue(text.contains("SEPA-Überweisung"), "the means of payment by name");
        for (String line : List.of(Word.CODE_UNIT.in(german) + " H87 = Stück",
                Word.CODE_UNIT.in(german) + " HUR = Stunde",
                Word.CODE_PAYMENT_MEANS.in(german) + " 58 = SEPA-Überweisung",
                Word.CODE_VAT_CATEGORY.in(german) + " S = Regelsteuersatz",
                Word.CODE_COUNTRY.in(german) + " DE = Deutschland")) {
            assertTrue(text.contains(line), "the closing heading lists " + line + ": " + text);
        }
    }

    /** A code no table of this repository names is written as the code it is. */
    @Test
    void aCodeWithoutANameIsWrittenAsTheCode() {
        SemanticDocument document = invoice().toBuilder()
                .remove("/BG-25/0/BT-130").put("/BG-25/0/BT-130", "XZZ").build();

        String text = Pdf.flat(new PdfRenderer().render(document, letter(RenderLanguage.GERMAN)));

        assertTrue(text.contains("10 XZZ"), "the unit stands as its code: " + text);
        assertFalse(text.contains(Word.CODE_UNIT.in(RenderLanguage.GERMAN) + " XZZ ="),
                "and nothing pretends to name it");
    }

    /**
     * The name of a country is data of this repository and not of the runtime. A rendering
     * under another default locale is the same rendering, which the golden files assert as
     * bytes and this asserts as the name a reader sees.
     */
    @Test
    void aCountryIsNamedFromTheCheckedInTableAndNotFromTheRuntime() {
        java.util.Locale machine = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            String text = Pdf.flat(new PdfRenderer().render(invoice(),
                    letter(RenderLanguage.GERMAN)));
            assertTrue(text.contains("Deutschland"),
                    "the country is named in the language of the rendering: " + text);
        } finally {
            java.util.Locale.setDefault(machine);
        }
    }

    // ---------------------------------------------------------------- the money

    /**
     * The letter is net and derives nothing. The document states 200.00 for its line and
     * 238.00 as its gross total; the gross of the line, and the gross of its unit price,
     * are figures no term of the document carries and no letter writes.
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void theLetterIsNetAndDerivesNothing(RenderLanguage language) {
        String text = Pdf.flat(new PdfRenderer().render(
                Documents.oneLineAtNineteenPerCent(), letter(language)));

        assertTrue(text.contains(Formats.decimal("200.00", 2, language)),
                "the net line amount is on the page");
        assertTrue(text.contains(Formats.decimal("238.00", 2, language)),
                "and the gross total the document states");
        assertFalse(text.contains(Formats.decimal("119.00", 2, language)),
                "no gross unit price is computed: " + text);
    }

    /**
     * A rate is written the way it is written. The trailing zeros of a fraction are not
     * on the page — a rate of nineteen per cent reads {@code 19 %} — and no digit the
     * document states is missing from it.
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void aRateIsWrittenWithTheDecimalPlacesTheDocumentWrote(RenderLanguage language) {
        SemanticDocument document = invoice().toBuilder()
                .set(SemanticPath.of("/BG-23/0/BT-119"), SemanticValue.of("10.70"))
                .set(SemanticPath.of("/BG-25/0/BG-30/BT-152"), SemanticValue.of("10.70"))
                .build();
        boolean german = language == RenderLanguage.GERMAN;

        String text = Pdf.flat(new PdfRenderer().render(document, letter(language)));

        assertTrue(text.contains(german ? "10,7 %" : "10.7 %"),
                "10.70 per cent reads as the rate it is: " + text);
        assertFalse(text.contains(german ? "10,70 %" : "10.70 %"),
                "without the trailing zero the figure does not carry: " + text);
        assertTrue(text.contains("19 %"), "and the rate of the other lines is 19 %: " + text);
        assertFalse(text.contains(german ? "19,00 %" : "19.00 %"),
                "not two decimal places that say nothing: " + text);
    }

    /**
     * BT-20 is the payment terms of the standard, and the letter labels it that. The
     * localization of the visualization names the term by what a German invoice most
     * often puts in it, which over a sentence about when to pay is not what a reader is
     * looking for.
     */
    @Test
    void thePaymentTermsStandUnderTheWordALetterUsesForThem() {
        RenderLanguage german = RenderLanguage.GERMAN;
        String terms = "Payable within 30 days without deduction.";

        String text = Pdf.flat(new PdfRenderer().render(invoice(), letter(german)));
        String english = Pdf.flat(
                new PdfRenderer().render(invoice(), letter(RenderLanguage.ENGLISH)));

        assertTrue(text.contains(Word.PAYMENT_TERMS.in(german) + " " + terms),
                "the terms stand under the word a letter uses: " + text);
        assertFalse(text.contains("Skonto"),
                "and not under the long name the localization carries: " + text);
        assertTrue(english.contains(
                        Word.PAYMENT_TERMS.in(RenderLanguage.ENGLISH) + " " + terms),
                "and in English under the English word: " + english);
    }

    /** BT-114 is the rounding amount of the standard here too, and labelled as that. */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void theRoundingAmountIsLabelledAsTheRoundingAmount(RenderLanguage language) {
        SemanticDocument document = Documents.oneLineAtNineteenPerCent().toBuilder()
                .remove("/BG-22/BT-114").put("/BG-22/BT-114", "0.00").build();
        String label = Labels.of(de.bsnsoft.esj.model.Registry.en16931(), language)
                .of("BT-114");

        String text = Pdf.flat(new PdfRenderer().render(document, letter(language)));

        assertTrue(text.contains(label + " " + Formats.decimal("0.00", 2, language)),
                "the rounding amount stands under its own label: " + text);
    }

    /**
     * The sum of the invoice lines closes the table of them rather than opening the block
     * of totals: it is the one total that is a statement about the table above it, and a
     * reader checks it by running down the column it stands in. So it stands in that
     * column, aligned with the net amounts of the lines to the point, under the rule that
     * closes the table and above the block of totals — which now begins with the figure
     * that follows it.
     */
    @Test
    void theSumOfTheLinesHangsOnTheTableOfLines() {
        RenderLanguage german = RenderLanguage.GERMAN;
        Labels labels =
                Labels.of(de.bsnsoft.esj.model.Registry.en16931(), german);

        byte[] pdf = new PdfRenderer().render(invoice(), letter(german));
        List<Pdf.Run> runs = Pdf.runs(pdf, 1);
        String text = Pdf.flat(pdf);

        Pdf.Run sum = only(runs, "2.450,00");
        Pdf.Run lastLine = only(runs, "760,00");
        Pdf.Run netTotal = first(runs, "2.450,00 EUR");
        assertEquals(lastLine.right(), sum.right(), 0.05f,
                "the sum stands in the column of the net amounts, flush with them");
        assertTrue(sum.baseline() < lastLine.baseline(), "under the last line of the table");
        assertTrue(sum.baseline() > netTotal.baseline(), "and over the block of totals");
        assertTrue(text.contains(labels.of("BT-106") + " 2.450,00 "),
                "it carries its own label, and no currency beside a header that names one: "
                        + text);
        assertEquals(1, occurrences(text, labels.of("BT-106")),
                "and the letter writes it once: " + text);
    }

    /**
     * An invoice without a single line keeps that sum in the block of totals, because
     * there is no table of lines for it to close and nothing of the document is dropped.
     */
    @Test
    void aDocumentWithoutLinesKeepsTheSumInTheBlockOfTotals() {
        SemanticDocument.Builder builder = SemanticDocument.builder();
        Documents.oneLineAtNineteenPerCent().values().forEach((path, value) -> {
            if (!path.toString().startsWith("/BG-25")) {
                builder.put(path, value);
            }
        });
        SemanticDocument document = builder.build();
        Labels labels = Labels.of(de.bsnsoft.esj.model.Registry.en16931(),
                RenderLanguage.GERMAN);

        String text = Pdf.flat(new PdfRenderer().render(document,
                letter(RenderLanguage.GERMAN)));

        assertTrue(text.contains(labels.of("BT-106")),
                "the sum of the lines is on the page: " + text);
    }

    /**
     * A table that runs over several pages carries the sum after its last line and
     * nowhere else: it closes the table, it is not a figure carried forward.
     */
    @Test
    void theSumStandsAfterTheLastLineOfATableThatRunsOverPages() {
        SemanticDocument document = Documents.withDetailedLines(60);
        Labels labels = Labels.of(de.bsnsoft.esj.model.Registry.en16931(),
                RenderLanguage.GERMAN);

        byte[] pdf = new PdfRenderer().render(document, letter(RenderLanguage.GERMAN));

        assertTrue(Pdf.pages(pdf) > 2, "sixty lines take more than two pages");
        assertEquals(1, occurrences(Pdf.flat(pdf), labels.of("BT-106")),
                "the sum is written once");
        int lastRow = page(pagesOf(pdf), "ROW59");
        assertEquals(lastRow, page(pagesOf(pdf), labels.of("BT-106")),
                "on the page the last line of the table ended on");
    }

    /**
     * Every specimen of the repository closes its table with the sum of its lines: once,
     * after the heading of the table and before the block of totals, whatever stands
     * between the two. The shapes that put something there are rendered as well — an
     * allowance and a charge on both levels together with a prepayment, which is the
     * invoice that carries every row the block of totals has.
     */
    @Test
    void everySpecimenClosesItsTableWithTheSumOfItsLines() {
        RenderLanguage german = RenderLanguage.GERMAN;
        Labels labels =
                Labels.of(de.bsnsoft.esj.model.Registry.en16931(), german);
        List<SemanticDocument> specimens = new ArrayList<>();
        for (String example : Corpus.EXAMPLES) {
            specimens.add(Corpus.example(example));
        }
        specimens.add(Corpus.example("allowances").toBuilder()
                .put("/BG-21/0/BT-99", "40.00")
                .put("/BG-21/0/BT-102", "S")
                .put("/BG-21/0/BT-103", "19")
                .put("/BG-21/0/BT-104", "Freight")
                .put("/BG-22/BT-113", "100.00")
                .build());

        for (SemanticDocument document : specimens) {
            if (document.value(SemanticPath.of("/BG-22/BT-106")).isEmpty()
                    || document.value(SemanticPath.of("/BG-25/0/BT-126")).isEmpty()) {
                continue;
            }
            String text = Pdf.flat(new PdfRenderer().render(document, letter(german)));
            String sum = labels.of("BT-106");
            String number = document.value(SemanticPath.of("/BT-1")).orElseThrow().content();

            String figure = Formats.decimal(
                    document.value(SemanticPath.of("/BG-22/BT-106")).orElseThrow().content(),
                    2, german);
            String currency =
                    document.value(SemanticPath.of("/BT-5")).orElseThrow().content();

            assertEquals(1, occurrences(text, sum), number + " writes the sum once: " + text);
            assertTrue(text.indexOf(Word.LINES.in(german)) < text.indexOf(sum),
                    number + ": the sum stands under the table of lines");
            assertTrue(text.indexOf(sum) < text.indexOf(labels.of("BT-109")),
                    number + ": and over the block of totals");
            assertTrue(text.contains(sum + " " + figure),
                    number + ": with the figure the document states: " + text);
            assertFalse(text.contains(sum + " " + figure + " " + currency),
                    number + ": and without a currency, which its column header carries, "
                            + "as a row of the block of totals would: " + text);
        }
    }

    /**
     * The closing section and the payment block are not parted for nothing: a section a
     * page could hold whole begins on the page it fits on, or on a fresh page, and never
     * with two rows at the foot of one page and the rest overleaf.
     *
     * <p>Swept over every number of lines from one to forty, because where a section falls
     * on the paper is decided by what stands above it and by nothing else.
     */
    @Test
    void aClosingSectionAndAPaymentBlockStandWhole() {
        RenderLanguage german = RenderLanguage.GERMAN;
        List<String> closing = List.of(Word.FURTHER_DETAILS.in(german),
                "urn:cen.eu:en16931:2017", "C62", "380");
        List<String> payment = List.of(Word.PAYMENT.in(german),
                "DE89 3704 0044 0532 0130 00", "Konto Muster", "COBADEFFXXX");
        for (int lines = 1; lines <= 40; lines++) {
            SemanticDocument document = Documents.withDetailedLines(lines).toBuilder()
                    .put("/BG-16/0/BT-81", "58")
                    .put("/BG-16/0/BG-17/0/BT-84", "DE89370400440532013000")
                    .put("/BG-16/0/BG-17/0/BT-85", "Konto Muster")
                    .put("/BG-16/0/BG-17/0/BT-86", "COBADEFFXXX")
                    .build();

            List<String> pages = pagesOf(new PdfRenderer().render(document, letter(german)));

            assertOnOnePage(pages, closing, lines + " lines: the closing section");
            assertOnOnePage(pages, payment, lines + " lines: the payment block");
        }
    }

    /** Asserts that every one of a list of texts stands on one and the same page. */
    private static void assertOnOnePage(List<String> pages, List<String> texts, String what) {
        int first = page(pages, texts.get(0));
        assertTrue(first > 0, what + " is in the rendering");
        for (String text : texts) {
            assertEquals(first, page(pages, text),
                    what + " stands whole on page " + first + ": '" + text + "' does not");
        }
    }

    /** Returns the text of every page of a rendering, with its whitespace collapsed. */
    private static List<String> pagesOf(byte[] pdf) {
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= Pdf.pages(pdf); page++) {
            pages.add(Pdf.flat(Pdf.textOfPage(pdf, page)));
        }
        return pages;
    }

    /** Returns the first run of a page that says a text, in the order they were written. */
    private static Pdf.Run first(List<Pdf.Run> runs, String text) {
        for (Pdf.Run run : runs) {
            if (run.text().equals(text)) {
                return run;
            }
        }
        throw new AssertionError("no run of the page says '" + text + "'");
    }

    /** Returns the one run of a page that says a text. */
    private static Pdf.Run only(List<Pdf.Run> runs, String text) {
        List<Pdf.Run> found = new ArrayList<>();
        for (Pdf.Run run : runs) {
            if (run.text().equals(text)) {
                found.add(run);
            }
        }
        assertEquals(1, found.size(), "one run of the page says '" + text + "'");
        return found.get(0);
    }

    /**
     * The amount due is the last row of the block and the only one in the bold face, which
     * is the figure the letter is about.
     */
    @Test
    void theAmountDueClosesTheBlockOfTotals() {
        RenderLanguage german = RenderLanguage.GERMAN;
        String text = Pdf.flat(new PdfRenderer().render(invoice(), letter(german)));
        String due = Labels.of(de.bsnsoft.esj.model.Registry.en16931(), german)
                .of("BT-115");

        int amountDue = text.indexOf(due);
        assertTrue(amountDue > 0, "the amount due is on the page: " + text);
        assertTrue(amountDue > text.indexOf("2.450,00"), "after the net sums");
        assertTrue(text.contains(due + " 2.915,50 EUR"),
                "with the figure the document states beside it");
    }

    /** An account identifier that is an IBAN is written in groups of four. */
    @Test
    void anIbanIsWrittenInGroupsOfFour() {
        String text = Pdf.flat(new PdfRenderer().render(invoice(), letter(RenderLanguage.GERMAN)));

        assertTrue(text.contains("DE89 3704 0044 0532 0130 00"),
                "the IBAN is grouped as a reader reads it out: " + text);
        assertEquals("1234567890", LetterLayout.grouped("1234567890"),
                "and an account number that is not an IBAN is left alone");
    }

    /**
     * BT-111 is the VAT total in the accounting currency of BT-6, and it is written in
     * that currency. A figure of one currency behind the code of another is a wrong
     * figure, and the letter has no column header to say which currency a row is in, so
     * the row says it itself.
     */
    @Test
    void theVatTotalInTheAccountingCurrencyIsWrittenInThatCurrency() {
        SemanticDocument document = Documents.oneLineAtNineteenPerCent().toBuilder()
                .put("/BT-6", "CHF")
                .put("/BG-22/BT-111", "41.00")
                .build();

        String text = Pdf.flat(
                new PdfRenderer().render(document, letter(RenderLanguage.ENGLISH)));

        assertTrue(text.contains("41.00 CHF"),
                "the accounting currency stands behind the figure: " + text);
        assertFalse(text.contains("41.00 EUR"),
                "and the currency of the document does not: " + text);
    }

    /**
     * A quantity and the name of its unit stand on one line. A column is as wide as the
     * widest cell it is given, at the cost of a column that has width to spare, so a unit
     * written out in full is neither broken inside its word nor parted from its figure.
     */
    @Test
    void aQuantityAndTheNameOfItsUnitStandOnOneLine() {
        SemanticDocument document = Documents.oneLineAtNineteenPerCent().toBuilder()
                .set(SemanticPath.of("/BG-25/0/BT-129"), SemanticValue.of("12"))
                .set(SemanticPath.of("/BG-25/0/BT-130"), SemanticValue.of("MWH"))
                .build();

        String text = Pdf.text(new PdfRenderer().render(document, letter(RenderLanguage.GERMAN)));

        assertTrue(text.lines().anyMatch(line -> line.contains("12 Megawattstunden")),
                "the figure and the name of the unit on one line: " + text);
    }

    /**
     * An address field that cannot hold everything loses its tail. It stops at the first
     * line it has no room for rather than skipping that one and writing the shorter ones
     * behind it, so a letter never shows a street and a town under a missing addressee.
     * What is left out stands under the closing heading, whole.
     */
    @Test
    void aFullAddressFieldLosesItsTailAndNeverItsHead() {
        String name = "Verwaltungsgemeinschaft ".repeat(30).strip();
        SemanticDocument document = invoice().toBuilder()
                .set(SemanticPath.of("/BG-7/BT-44"), SemanticValue.of(name)).build();

        byte[] pdf = new PdfRenderer().render(document, letter(RenderLanguage.GERMAN));
        String field = Pdf.textInArea(pdf, 1, 20 * MM, 45 * MM, 85 * MM, 45 * MM);
        String flat = Pdf.flat(pdf);
        String closing = flat.substring(
                flat.indexOf(Word.FURTHER_DETAILS.in(RenderLanguage.GERMAN)));

        assertFalse(field.contains("Beispielallee 3"),
                "a name the field cannot hold takes the lines behind it with it: " + field);
        assertFalse(field.contains("Musterstadt"), "including the town: " + field);
        for (String left : List.of("Verwaltungsgemeinschaft", "Beispielallee 3",
                "Musterstadt")) {
            assertTrue(closing.contains(left),
                    "'" + left + "' stands under the closing heading: " + closing);
        }
    }

    /**
     * A payment block taller than a page takes its heading onto every page it runs onto,
     * and no account is parted by the break: a page that opened with an IBAN under no
     * heading would say nothing about what the number is for, and an IBAN whose holder
     * stands on the page after it is the half of a letter that has to be right.
     */
    @Test
    void aPaymentBlockThatRunsOnKeepsItsHeadingAndEveryAccountWhole() {
        SemanticDocument.Builder builder = Documents.oneLineAtNineteenPerCent().toBuilder();
        for (int i = 0; i < 28; i++) {
            builder.put("/BG-16/" + i + "/BT-81", "58");
            for (int j = 0; j < 2; j++) {
                String account = "/BG-16/" + i + "/BG-17/" + j;
                // Not written as an IBAN, so the page carries the identifier as it stands
                // and a test can look for it.
                builder.put(account + "/BT-84", "ACC-" + i + "-" + j)
                        .put(account + "/BT-85", "Holder " + i + "-" + j)
                        .put(account + "/BT-86", "BIC-" + i + "-" + j);
            }
        }

        byte[] pdf = new PdfRenderer().render(builder.build(), letter(RenderLanguage.GERMAN));
        String heading = Word.PAYMENT.in(RenderLanguage.GERMAN);
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= Pdf.pages(pdf); page++) {
            pages.add(Pdf.flat(Pdf.textOfPage(pdf, page)));
        }

        int first = -1;
        int last = -1;
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).contains("Holder 0-0")) {
                first = i;
            }
            if (pages.get(i).contains("Holder 27-1")) {
                last = i;
            }
        }
        assertTrue(first >= 0 && last > first, "the block runs over more than one page");
        for (int i = first; i <= last; i++) {
            assertTrue(pages.get(i).contains(heading),
                    "page " + (i + 1) + " of the block carries the heading: " + pages.get(i));
        }
        for (int i = 0; i < 28; i++) {
            for (int j = 0; j < 2; j++) {
                int account = page(pages, "ACC-" + i + "-" + j);
                assertTrue(account > 0, "the account ACC-" + i + "-" + j + " is on a page");
                assertEquals(account, page(pages, "Holder " + i + "-" + j),
                        "its holder stands on the page of the account identifier");
                assertEquals(account, page(pages, "BIC-" + i + "-" + j),
                        "and so does its bank");
            }
        }
    }

    /** Returns the page a text stands on, counted from one, or zero where it is on none. */
    private static int page(List<String> pages, String text) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).contains(text)) {
                return i + 1;
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- the foot, and the pages

    /**
     * The seller's own details stand in the foot of the <b>first</b> page, in columns
     * under a rule, and the pages after it carry the page footer alone — which is what a
     * business letter does, and what a reader of a letter that runs over pages needs: the
     * details of the sender are not on the page read last.
     */
    @Test
    void theSellerDetailsStandInTheFootOfTheFirstPage() {
        byte[] pdf = new PdfRenderer().render(invoice(), letter(RenderLanguage.GERMAN));

        int pages = Pdf.pages(pdf);
        String first = Pdf.flat(Pdf.textOfPage(pdf, 1));
        assertTrue(pages > 1, "the standard invoice as a letter takes more than one page");
        assertTrue(first.contains("Musterweg 12"),
                "the address of the sender is in the foot of page one: " + first);
        assertTrue(first.contains("HRB 12345"), "and its register number: " + first);
        for (int page = 2; page <= pages; page++) {
            assertFalse(Pdf.flat(Pdf.textOfPage(pdf, page)).contains("HRB 12345"),
                    "page " + page + " carries the page footer alone");
        }
    }

    /**
     * And nothing of the letter is written into that foot. The page reserves it before it
     * is filled, so every run of text on page one is either the letter above the foot, or
     * the foot itself, or the page footer under it — measured on the page the README
     * renders, on the example template.
     */
    @Test
    void nothingOfTheLetterReachesIntoTheFootOfItsFirstPage() {
        byte[] pdf = new PdfRenderer().render(invoice(),
                RenderOptions.defaults().with(Templates.example("letter.json")));
        List<String> ofTheFoot = List.of("+49 30 1234567", "billing@example.invalid",
                "HRB 12345 (" + Word.FOOT_REGISTRATION.in(RenderLanguage.GERMAN) + ")");

        List<Pdf.Run> runs = Pdf.runs(pdf, 1);

        float zoneTop = 0;
        for (Pdf.Run run : runs) {
            if (ofTheFoot.stream().anyMatch(line -> run.text().contains(line))) {
                zoneTop = Math.max(zoneTop, run.top());
            }
        }
        assertTrue(zoneTop > LetterLayout.BOTTOM,
                "the foot of the sender stands on page one, above the page footer: " + runs);
        List<String> wrong = new ArrayList<>();
        for (Pdf.Run run : runs) {
            boolean pageFooter = run.top() <= LetterLayout.BOTTOM;
            boolean inTheFoot = run.baseline() >= LetterLayout.BOTTOM - 1
                    && run.top() <= zoneTop + 1;
            boolean body = run.baseline() >= zoneTop;
            if (!pageFooter && !inTheFoot && !body) {
                wrong.add(run.text() + " sits at " + run.baseline() + ".." + run.top());
            }
        }

        assertEquals(List.of(), wrong, "every run of page one is the letter above the foot ("
                + zoneTop + " points up), the foot itself, or the page footer under it");
    }

    /**
     * A foot the first page cannot hold is not written as a foot. The sender's details
     * are measured before the page is filled, and where they need more room than the page
     * has to give they stand under the closing heading — as they do for a letterhead that
     * prints them — instead of being drawn over the letter that was about to be written.
     *
     * <p>The second claim is the one that matters on the paper: nothing of page one is
     * written over anything else of it. It is asked of the boxes the runs of text come
     * back in, so it holds whatever the reason a block might have had to stand there.
     */
    @Test
    void aFootTheFirstPageCannotHoldGoesUnderTheClosingHeadingInstead() {
        SemanticDocument document = Documents.withMoreSellerDetailsThanAFootHolds();

        byte[] pdf = new PdfRenderer().render(document, letter(RenderLanguage.GERMAN));

        String text = Pdf.flat(pdf);
        assertTrue(text.contains("/BG-4/BT-33"),
                "the legal information stands under the closing heading, with its path");
        assertTrue(text.contains("HRB 12345"), "and so does the register of the sender");
        assertEquals(List.of(), overprinted(pdf, 1),
                "and nothing of the first page is written over anything else of it");
    }

    /**
     * And the letter that does have a foot keeps the same claim: the band is furniture of
     * page one, and page one carries no two runs of text in the same place.
     */
    @Test
    void nothingOfTheLetterIsWrittenOverAnythingElseOfIt() {
        byte[] pdf = new PdfRenderer().render(invoice(),
                RenderOptions.defaults().with(Templates.example("letter.json")));

        for (int page = 1; page <= Pdf.pages(pdf); page++) {
            assertEquals(List.of(), overprinted(pdf, page), "page " + page + " is written once");
        }
    }

    /** Returns the pairs of runs of a page whose boxes meet, which is one over the other. */
    private static List<String> overprinted(byte[] pdf, int page) {
        List<Pdf.Run> runs = Pdf.runs(pdf, page);
        List<String> over = new ArrayList<>();
        for (int i = 0; i < runs.size(); i++) {
            for (int j = i + 1; j < runs.size(); j++) {
                Pdf.Run a = runs.get(i);
                Pdf.Run b = runs.get(j);
                float slack = 0.5f;
                if (a.right() > b.left() + slack && a.left() < b.right() - slack
                        && a.top() > b.baseline() + slack && a.baseline() < b.top() - slack) {
                    over.add("'" + a.text() + "' over '" + b.text() + "'");
                }
            }
        }
        return over;
    }

    /**
     * A template whose letterhead prints those details already sends them under the
     * closing heading instead. Nothing is dropped: the values are on the page either way,
     * which is the whole rule of this module.
     */
    @Test
    void aLetterheadThatPrintsThemAlreadyTakesThemOutOfTheFoot() {
        RenderTemplate template = Templates.of("""
                {"template": "esj-render-template/0.1", "layout": "letter",
                 "letter": {"sellerDetails": "details"}}""");

        String text = Pdf.flat(new PdfRenderer().render(invoice(),
                RenderOptions.defaults().with(template)));

        assertTrue(text.contains("/BG-4/BT-30"),
                "the register number stands under the closing heading, with its path: " + text);
        assertTrue(text.contains("HRB 12345"), "and its value is on the page");
    }

    /**
     * Every page after the first says which letter it belongs to, and counts the pages
     * once: the compact head names the document, the footer under the text carries the
     * page of the page count. The same number over the text and under it is one of them
     * asking to be read twice.
     */
    @Test
    void everyFollowingPageCarriesTheCompactHeadAndCountsThePagesInItsFoot() {
        byte[] pdf = new PdfRenderer().render(Documents.withDetailedLines(60),
                letter(RenderLanguage.GERMAN));

        int pages = Pdf.pages(pdf);
        assertTrue(pages > 2, "sixty detailed lines take more than two pages, not " + pages);
        for (int page = 2; page <= pages; page++) {
            String head = Pdf.textInArea(pdf, page, 0, 0, 600, 25 * MM);
            String counted = "Seite " + page + " von " + pages;
            assertTrue(head.contains("Rechnung RE-2026-0815"),
                    "page " + page + " says what letter it is: " + head);
            assertFalse(head.contains(counted),
                    "and does not count the pages a second time: " + head);
            assertEquals(1, occurrences(Pdf.flat(Pdf.textOfPage(pdf, page)), counted),
                    "page " + page + " carries '" + counted + "' once, in its foot");
        }
    }

    /** Returns how often a text carries another one. */
    private static int occurrences(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            found++;
        }
        return found;
    }

    // ---------------------------------------------------------------- the type of the document

    /** The credit note of the examples: 381, with the invoice it credits in BG-3. */
    private static SemanticDocument creditNote() {
        return Corpus.example("credit-note");
    }

    /**
     * A credit note says so where an invoice word would say the opposite: the number and
     * the date of the reference line are those of a credit note, and the figure the totals
     * close with is the amount credited. The due date keeps its name, and nothing else
     * changes.
     *
     * @param language the language of the rendering
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void aCreditNoteSaysSoInItsHeadDataAndItsTotals(RenderLanguage language) {
        byte[] pdf = new PdfRenderer().render(creditNote(), letter(language));

        String line = Pdf.textInArea(pdf, 1, 20 * MM, 95 * MM, 180 * MM, 45 * MM);
        String text = Pdf.flat(pdf);
        boolean german = language == RenderLanguage.GERMAN;
        for (String label : german
                ? List.of("Gutschriftsnummer", "Gutschriftsdatum", "Fälligkeitsdatum")
                : List.of("Credit note number", "Credit note date", "Payment due date")) {
            assertTrue(line.contains(label), "the reference line reads " + label + ": " + line);
        }
        for (String label : german ? List.of("Rechnungsnummer", "Rechnungsdatum")
                : List.of("Invoice number", "Invoice issue date")) {
            assertFalse(line.contains(label), "and not " + label + ": " + line);
        }
        assertTrue(text.contains(german ? "Gutschriftsbetrag 355,81 EUR"
                        : "Amount credited 355.81 EUR"),
                "the totals close with the amount credited: " + text);
        assertFalse(text.contains(german ? "Fälliger Betrag" : "Amount due for payment"),
                "and not with an amount due: " + text);
    }

    /**
     * The invoice a document refers to stands under its title: a credit note is read
     * against the invoice it credits, and a reader looks for that one under the title
     * that says what the letter is, not among the terms at its end. The number and the
     * date are the document's, the date written as the letter writes a date, and they
     * stand on the letter once.
     *
     * @param language the language of the rendering
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void theInvoiceACreditNoteCreditsStandsUnderItsTitle(RenderLanguage language) {
        byte[] pdf = new PdfRenderer().render(creditNote(), letter(language));

        boolean german = language == RenderLanguage.GERMAN;
        String title = german ? "Gutschrift GU-2026-0004" : "Credit note GU-2026-0004";
        String under = german ? "zur Rechnung RE-2026-0042 vom 03.02.2026"
                : "to invoice RE-2026-0042 of 2026-02-03";
        float titleLine = baselineOf(Pdf.runs(pdf, 1), title);
        float underLine = baselineOf(Pdf.runs(pdf, 1), under);
        assertTrue(underLine < titleLine && underLine > titleLine - 30f,
                "the line stands right under the title: " + underLine + " under "
                        + titleLine);
        String text = Pdf.flat(pdf);
        assertEquals(text.indexOf("RE-2026-0042"), text.lastIndexOf("RE-2026-0042"),
                "the number of the invoice is on the letter once: " + text);
        String closing = text.substring(text.indexOf(Word.FURTHER_DETAILS.in(language)));
        assertFalse(closing.contains("/BG-3/"),
                "and the closing heading no longer lists it: " + closing);
    }

    /**
     * A credit note gets no payment code, even where the template asks for one and the
     * document states a credit transfer account the code could carry. The code asks its
     * reader to pay into the account beside it; the account of a credit note is where the
     * seller pays the buyer. The payment block is printed as the document states it, and
     * the same document as an invoice gets its code.
     */
    @Test
    void aCreditNoteGetsNoPaymentCodeWhateverTheTemplateAsks() {
        RenderTemplate asking = Templates.of("""
                {"template": "esj-render-template/0.1", "layout": "letter",
                 "letter": {"paymentCode": true}}""");
        assertTrue(PaymentCode.of(creditNote()).isPresent(),
                "the credit note states a credit transfer a code could carry");

        for (RenderLanguage language : RenderLanguage.values()) {
            byte[] pdf = new PdfRenderer().render(creditNote(),
                    RenderOptions.in(language).with(asking));
            String text = Pdf.flat(pdf);
            assertFalse(text.contains(Word.PAYMENT_CODE.in(language)),
                    "no code is drawn on a credit note: " + text);
            assertEquals(0, symbolsDrawn(pdf), "and no symbol either");
            assertTrue(text.contains(Word.PAYMENT.in(language))
                            && text.contains("DE12 5001 0517 0648 4898 90"),
                    "and the payment block stands as the document states it: " + text);
        }
        SemanticDocument invoice = creditNote().toBuilder()
                .set(SemanticPath.of("/BT-3"), SemanticValue.of("380")).build();
        byte[] control = new PdfRenderer().render(invoice, RenderOptions.defaults().with(asking));
        assertTrue(Pdf.flat(control).contains(Word.PAYMENT_CODE.in(RenderLanguage.GERMAN)),
                "the same document as an invoice carries the code");
        assertEquals(1, symbolsDrawn(control), "drawn as one symbol");
    }

    /**
     * A self-billed invoice gets no payment code either, even where the template asks for
     * one and the document states a credit transfer the code could carry. The buyer issues
     * it and sends it to the seller, so its reader is the party the payment goes to, and the
     * code would ask them to pay into their own account. The payment block is printed as the
     * document states it, and the words stay those of an invoice.
     *
     * <p>The example of the repository is settled by direct debit, which carries no code in
     * any case, so the case turns it into a SEPA credit transfer into the seller's account
     * first — the specimen a code would be drawn for, as the same document as an invoice
     * shows.
     */
    @Test
    void aSelfBilledInvoiceGetsNoPaymentCodeWhateverTheTemplateAsks() {
        RenderTemplate asking = Templates.of("""
                {"template": "esj-render-template/0.1", "layout": "letter",
                 "letter": {"paymentCode": true}}""");
        SemanticDocument document = Corpus.example("self-billed").toBuilder()
                .set(SemanticPath.of("/BG-16/BT-81"), SemanticValue.of("58"))
                .removeUnder(SemanticPath.group("/BG-16/BG-19"))
                .put("/BG-16/BG-17/0/BT-84", "DE89370400440532013000")
                .put("/BG-16/BG-17/0/BT-85", "Example GmbH")
                .build();
        assertEquals("389", document.value(SemanticPath.of("/BT-3")).orElseThrow().content(),
                "the example is a self-billed invoice");
        assertTrue(PaymentCode.of(document).isPresent(),
                "and now states a credit transfer a code could carry");

        for (RenderLanguage language : RenderLanguage.values()) {
            byte[] pdf = new PdfRenderer().render(document,
                    RenderOptions.in(language).with(asking));
            String text = Pdf.flat(pdf);
            assertFalse(text.contains(Word.PAYMENT_CODE.in(language)),
                    "no code is drawn on a self-billed invoice: " + text);
            assertEquals(0, symbolsDrawn(pdf), "and no symbol either");
            assertTrue(text.contains(Word.PAYMENT.in(language))
                            && text.contains("DE89 3704 0044 0532 0130 00"),
                    "the payment block stands as the document states it: " + text);
            boolean german = language == RenderLanguage.GERMAN;
            assertTrue(text.contains(german ? "Fälliger Betrag" : "Amount due for payment"),
                    "and the totals close with the amount due of an invoice: " + text);
        }
        SemanticDocument invoice = document.toBuilder()
                .set(SemanticPath.of("/BT-3"), SemanticValue.of("380")).build();
        byte[] control = new PdfRenderer().render(invoice, RenderOptions.defaults().with(asking));
        assertTrue(Pdf.flat(control).contains(Word.PAYMENT_CODE.in(RenderLanguage.GERMAN)),
                "the same document as an invoice carries the code");
        assertEquals(1, symbolsDrawn(control), "drawn as one symbol");
    }

    /**
     * The types that are self-billed without being a credit note are the types the table
     * of names of this module calls self-billed, less its credit notes: a code that joined
     * the table under that name and not the set would be a self-billed invoice with a
     * payment code under it.
     */
    @Test
    void theSelfBilledTypesAreTheOnesTheTableCallsSelfBilled() {
        Set<String> named = new TreeSet<>();
        for (int code = 0; code < 1000; code++) {
            String written = String.valueOf(code);
            DisplayNames.of(DisplayNames.CodeList.INVOICE_TYPE, written, RenderLanguage.ENGLISH)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith("self"))
                    .ifPresent(name -> named.add(written));
        }
        named.removeAll(LetterLayout.CREDIT_NOTES);
        assertEquals(named, new TreeSet<>(LetterLayout.SELF_BILLED),
                "the self-billed types of the letter are the ones the table names");
        assertTrue(named.contains("389"), "among them the self-billed invoice: " + named);
    }

    /**
     * Returns how many symbols a rendering draws: filled areas as wide as they are tall and
     * larger than any mark of the layout's text, which is what the modules of a code make
     * together — they are filled as one path.
     */
    private static int symbolsDrawn(byte[] pdf) {
        int found = 0;
        for (int page = 1; page <= Pdf.pages(pdf); page++) {
            for (Pdf.Mark mark : Pdf.marks(pdf, page)) {
                float width = mark.right() - mark.left();
                float height = mark.top() - mark.bottom();
                if (!mark.stroked() && width > 40f && Math.abs(width - height) < 1f) {
                    found++;
                }
            }
        }
        return found;
    }

    /**
     * The types the letter treats as credit notes are the types the table of names of
     * this module calls a credit note, and no others: a code that joined the table under
     * that name and not the set would be a credit note with a payment code under it.
     */
    @Test
    void theCreditNotesAreTheTypesTheTableCallsACreditNote() {
        Set<String> named = new TreeSet<>();
        for (int code = 0; code < 1000; code++) {
            String written = String.valueOf(code);
            DisplayNames.of(DisplayNames.CodeList.INVOICE_TYPE, written, RenderLanguage.ENGLISH)
                    .filter(name -> name.toLowerCase(Locale.ROOT).contains("credit note"))
                    .ifPresent(name -> named.add(written));
        }
        assertEquals(named, new TreeSet<>(LetterLayout.CREDIT_NOTES),
                "the credit notes of the letter are the ones the table names");
        assertTrue(named.containsAll(List.of("81", "83", "261", "262", "296", "308", "381")),
                "which are at least the seven of the brief: " + named);
    }

    /**
     * A corrected invoice names the invoice it corrects under its title and is otherwise
     * an invoice: its labels are an invoice's, and its credit transfer carries a code.
     * The corpus instance names the invoice without a date, so the line is the number
     * alone.
     *
     * @param language the language of the rendering
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void aCorrectedInvoiceNamesTheInvoiceItCorrects(RenderLanguage language) {
        SemanticDocument document =
                Corpus.instance("business-cases/standard/01.18a-INVOICE_ubl.xml");
        byte[] pdf = new PdfRenderer().render(document, letter(language));

        boolean german = language == RenderLanguage.GERMAN;
        List<Pdf.Run> runs = Pdf.runs(pdf, 1);
        float title = baselineOf(runs,
                (german ? "Rechnungskorrektur" : "Corrected invoice") + " PRG1502112");
        float under = baselineOf(runs, german ? "zur Rechnung PRG1502168"
                : "to invoice PRG1502168");
        assertTrue(under < title && under > title - 30f, "the corrected invoice is named"
                + " under the title");
        String line = Pdf.textInArea(pdf, 1, 20 * MM, 95 * MM, 180 * MM, 45 * MM);
        assertTrue(line.contains(german ? "Rechnungsnummer" : "Invoice number"),
                "a corrected invoice is an invoice: " + line);
        assertTrue(Pdf.flat(pdf).contains(Word.PAYMENT_CODE.in(language)),
                "and its credit transfer carries a code");
    }

    /**
     * A self-billed invoice keeps the words of an invoice: the table of names gives it its
     * title, and the labels of the letter stay those of an invoice. What it does not keep is
     * the payment code, which the case above is about.
     *
     * @param language the language of the rendering
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void aSelfBilledInvoiceKeepsTheWordsOfAnInvoice(RenderLanguage language) {
        SemanticDocument document =
                Corpus.instance("business-cases/standard/01.20a-INVOICE_ubl.xml");
        String number = document.value(SemanticPath.of("/BT-1")).orElseThrow().content();
        byte[] pdf = new PdfRenderer().render(document, letter(language));

        boolean german = language == RenderLanguage.GERMAN;
        String text = Pdf.flat(pdf);
        assertTrue(text.contains((german ? "Gutschrift im Gutschriftverfahren"
                : "Self-billed invoice") + " " + number), "the title names the type: " + text);
        String line = Pdf.textInArea(pdf, 1, 20 * MM, 95 * MM, 180 * MM, 45 * MM);
        assertTrue(line.contains(german ? "Rechnungsnummer" : "Invoice number"),
                "the number is an invoice number: " + line);
        assertTrue(text.contains(german ? "Fälliger Betrag" : "Amount due for payment"),
                "and the totals close with the amount due: " + text);
        assertFalse(text.contains(german ? "Gutschriftsbetrag" : "Amount credited"),
                "not with an amount credited: " + text);
    }

    /**
     * Up to three preceding invoices stand under the title, each with its date where the
     * document states one; the line joins them and computes nothing.
     *
     * @param language the language of the rendering
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void threePrecedingInvoicesStandUnderTheTitle(RenderLanguage language) {
        byte[] pdf = new PdfRenderer().render(Documents.withPrecedingInvoices(3),
                letter(language));

        String text = Pdf.flat(pdf);
        assertTrue(text.contains(language == RenderLanguage.GERMAN
                        ? "Schlussrechnung Bau RE-2026-0815 zu den Rechnungen RE-2026-0001 vom"
                                + " 01.01.2026, RE-2026-0002 vom 01.02.2026, RE-2026-0003"
                        : "Final construction invoice RE-2026-0815 to invoices RE-2026-0001 of"
                                + " 2026-01-01, RE-2026-0002 of 2026-02-01, RE-2026-0003"),
                "the three stand under the title: " + text);
        assertFalse(text.contains("/BG-3/"),
                "and none of them is left to the closing heading: " + text);
    }

    /**
     * Four or more are not a line under a title: they stay under the closing heading, each
     * with its label and its path, and the title stands alone.
     */
    @Test
    void fourPrecedingInvoicesStayUnderTheClosingHeading() {
        byte[] pdf = new PdfRenderer().render(Documents.withPrecedingInvoices(4),
                letter(RenderLanguage.GERMAN));

        String text = Pdf.flat(pdf);
        assertFalse(text.contains(Word.PRECEDING_INVOICES.in(RenderLanguage.GERMAN)),
                "no line under the title names them: " + text);
        String closing = text.substring(
                text.indexOf(Word.FURTHER_DETAILS.in(RenderLanguage.GERMAN)));
        for (int invoice = 0; invoice < 4; invoice++) {
            assertTrue(closing.contains("/BG-3/" + invoice + "/BT-25"),
                    "the closing heading lists preceding invoice " + invoice + ": " + closing);
        }
    }

    /** Returns the baseline of the run of a page that says exactly a text. */
    private static float baselineOf(List<Pdf.Run> runs, String text) {
        for (Pdf.Run run : runs) {
            if (run.text().equals(text)) {
                return run.baseline();
            }
        }
        throw new AssertionError("no run of the page says '" + text + "': " + runs);
    }

    // ---------------------------------------------------------------- the following pages

    /**
     * A page after the first keeps the rhythm of page one: whatever opens it — the column
     * header of a table that goes on, the rows of the totals, the heading of a section —
     * stands exactly as far under the rule of the compact head as the first section of
     * page one stands under the rule of the title.
     *
     * <p>The white is read off the paper: from the rule to the top of the line the first
     * block begins with, or to the top of the band its column header is set on. The head
     * used to keep less than half of what the title keeps, and the author's printed pages
     * showed it — a page whose first block stood up against the head. It holds on a
     * following sheet whose template states a top margin of its own as well, because the
     * head stands under that margin and the text under the head.
     */
    @Test
    void aPageAfterTheFirstKeepsTheRhythmOfPageOne() {
        List<RenderTemplate> papers = List.of(
                Templates.of("""
                        {"template": "esj-render-template/0.1", "layout": "letter"}"""),
                Templates.of("""
                        {"template": "esj-render-template/0.1", "layout": "letter",
                         "margins": {"following": {"top": 120}}}"""));
        List<String> wrong = new ArrayList<>();
        for (String example : List.of("allowances", "multiple-lines")) {
            SemanticDocument document = Corpus.example(example);
            String number = document.value(SemanticPath.of("/BT-1")).orElseThrow().content();
            for (RenderTemplate paper : papers) {
                for (RenderLanguage language : RenderLanguage.values()) {
                    byte[] pdf = new PdfRenderer().render(document,
                            RenderOptions.in(language).with(paper));
                    int pages = Pdf.pages(pdf);
                    assertTrue(pages > 1, example + " takes more than one page");
                    float underTheTitle = whiteUnderTheRuleUnder(pdf, 1, number);
                    for (int page = 2; page <= pages; page++) {
                        float underTheHead = whiteUnderTheRuleUnder(pdf, page, number);
                        if (Math.abs(underTheHead - underTheTitle) > 0.05f) {
                            wrong.add(example + " in " + language + ", page " + page + ": "
                                    + underTheHead + " points under the head, and page one"
                                    + " keeps " + underTheTitle + " under its title");
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), wrong, "every page opens at the distance page one keeps");
    }

    /**
     * The block of totals and the payment block are not set up against what stands above
     * them: the totals keep a section's distance under the row that closes the table of
     * lines, and the heading of the payment block keeps it under the amount due — on the
     * page after the first as on the first.
     *
     * <p>The white is measured between the line boxes the two lines are set in, which is
     * how the layout spaces its blocks: the line a row takes, and then the gap of a
     * section.
     */
    @Test
    void theTotalsAndThePaymentBlockKeepASectionsDistanceFromWhatStandsAboveThem() {
        List<String> wrong = new ArrayList<>();
        for (String example : List.of("standard-invoice", "multiple-lines", "credit-note")) {
            for (RenderLanguage language : RenderLanguage.values()) {
                byte[] pdf = new PdfRenderer().render(Corpus.example(example),
                        letter(language));
                boolean german = language == RenderLanguage.GERMAN;
                String closing = german ? "Summe aller Positionen"
                        : "Sum of Invoice line net amount";
                String firstTotal = german ? "Gesamtsumme netto"
                        : "Invoice total amount without VAT";
                String due = example.equals("credit-note")
                        ? Word.AMOUNT_CREDITED.in(language)
                        : german ? "Fälliger Betrag" : "Amount due for payment";
                for (int page = 1; page <= Pdf.pages(pdf); page++) {
                    List<Pdf.Run> runs = Pdf.runs(pdf, page);
                    check(wrong, example + " in " + language + ", page " + page + ", totals",
                            find(runs, closing), find(runs, firstTotal));
                    check(wrong, example + " in " + language + ", page " + page + ", payment",
                            find(runs, due), find(runs, Word.PAYMENT.in(language)));
                }
            }
        }
        assertEquals(List.of(), wrong, "each block keeps a section's distance from the one"
                + " above it");
    }

    /** Records where the line under another stands closer to it than a section's gap. */
    private static void check(List<String> wrong, String where, Pdf.Run above, Pdf.Run under) {
        if (above == null || under == null) {
            return;
        }
        float bottom = above.baseline() - (Sheet.lineHeight(above.size()) - above.size());
        float white = bottom - under.lineTop();
        if (white < InvoiceLayout.SECTION_GAP - 0.05f) {
            wrong.add(where + ": " + white + " points of white");
        }
    }

    /** Returns the run of a page that says exactly a text, or {@code null}. */
    private static Pdf.Run find(List<Pdf.Run> runs, String text) {
        for (Pdf.Run run : runs) {
            if (run.text().equals(text)) {
                return run;
            }
        }
        return null;
    }

    /**
     * Returns the white between the rule under the line that ends in the number of the
     * document — the title of page one, the compact head of a page after it — and the
     * first block under that rule: the top of the line that block begins with, or the top
     * of the band it is set on.
     */
    private static float whiteUnderTheRuleUnder(byte[] pdf, int page, String number) {
        float line = Float.NEGATIVE_INFINITY;
        List<Pdf.Run> runs = Pdf.runs(pdf, page);
        for (Pdf.Run run : runs) {
            if (run.text().endsWith(" " + number)) {
                line = Math.max(line, run.baseline());
            }
        }
        assertTrue(line > 0, "page " + page + " carries a line ending in '" + number + "'");
        List<Pdf.Mark> marks = Pdf.marks(pdf, page);
        float rule = Float.NEGATIVE_INFINITY;
        for (Pdf.Mark mark : marks) {
            if (mark.stroked() && mark.top() < line && mark.right() - mark.left() > 100f) {
                rule = Math.max(rule, mark.top());
            }
        }
        assertTrue(rule > 0, "page " + page + " rules the line off from the text under it");
        float first = Float.NEGATIVE_INFINITY;
        for (Pdf.Run run : runs) {
            if (run.lineTop() < rule - 0.01f) {
                first = Math.max(first, run.lineTop());
            }
        }
        for (Pdf.Mark mark : marks) {
            if (!mark.stroked() && mark.top() < rule - 0.01f) {
                first = Math.max(first, mark.top());
            }
        }
        assertTrue(first > 0, "and something stands under the rule of page " + page);
        return rule - first;
    }

    /**
     * The last row of the block of totals never opens a page on its own.
     *
     * <p>A block of totals taller than a page flows, and the amount due at the top of a
     * page with the sums it follows from on the page before reads as a figure of whatever
     * comes next. It is asked for together with the row above it, so the two move
     * together. The bottom margin is swept over the range that used to part them.
     */
    @Test
    void theLastRowOfTheTotalsNeverOpensAPageOnItsOwn() {
        SemanticDocument document = Documents.withManyVatCategories(32);
        List<String> wrong = new ArrayList<>();
        for (int bottom = 104; bottom <= 120; bottom++) {
            RenderTemplate template = Templates.of("""
                    {"template": "esj-render-template/0.1", "layout": "letter",
                     "margins": {"first": {"bottom": %d}}}""".formatted(bottom));

            byte[] pdf = new PdfRenderer().render(document,
                    RenderOptions.defaults().with(template));

            for (int page = 1; page <= Pdf.pages(pdf); page++) {
                String text = Pdf.flat(Pdf.textOfPage(pdf, page));
                if (text.contains("Fälliger Betrag") && !text.contains("Gesamtsumme brutto")
                        && !text.contains("Umsatzsteuerbetrag")) {
                    wrong.add("a bottom margin of " + bottom + " points leaves the amount"
                            + " due alone on page " + page);
                }
            }
        }
        assertEquals(List.of(), wrong, "the amount due keeps a row of its block with it");
    }

    // ---------------------------------------------------------------- the foot of page one

    /**
     * The foot of the first page writes its values compactly: the value first, an
     * identification scheme behind it as the code it is, and no label in front of a value
     * that says what it is.
     *
     * <p>Three columns of a letter foot are the narrowest text on the page, and the labels
     * of the register are written for a block of definitions: <i>Seller legal registration
     * identifier: HRB 12345</i> took most of a column for one fact, and <i>Kennung:
     * 4399901000018 (Schema der Kennung: 0088)</i> took two lines of it. An identifier the
     * document states without a scheme carries the word of the letter for it behind the
     * value. The closing heading, which has the width of the page for it, keeps the full
     * labels — and this case says both.
     *
     * @param language the language of the rendering
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void theFootOfTheFirstPageWritesItsValuesCompactly(RenderLanguage language) {
        byte[] pdf = new PdfRenderer().render(invoice(),
                RenderOptions.in(language).with(Templates.example("letter.json")));

        List<String> foot = new ArrayList<>();
        for (Pdf.Run run : Pdf.runs(pdf, 1)) {
            if (run.baseline() < 120f && run.baseline() > 60f) {
                foot.add(run.text());
            }
        }
        assertTrue(foot.contains("Accounts receivable"),
                "the contact point heads its column without a label: " + foot);
        assertTrue(foot.contains("+49 30 1234567"),
                "a telephone number stands in the foot without a label: " + foot);
        assertTrue(foot.contains("billing@example.invalid"),
                "and so does an e-mail address: " + foot);
        assertTrue(foot.contains("invoices@example.invalid (EM)"),
                "an electronic address carries its scheme as the code it is: " + foot);
        assertTrue(foot.contains("4399901000018 (0088)"),
                "and so does an identifier, the value first: " + foot);
        assertTrue(foot.contains("HRB 12345 (" + Word.FOOT_REGISTRATION.in(language) + ")"),
                "a register number without a scheme carries the word for it: " + foot);
        List<String> labels = language == RenderLanguage.GERMAN
                ? List.of("Name:", "Kennung:", "Register-/Registriernummer:",
                        "Schema der Kennung", "Weitere rechtliche Informationen")
                : List.of("Seller contact point", "Seller identifier",
                        "Seller legal registration identifier", "Scheme identifier",
                        "Seller additional legal information");
        for (String line : foot) {
            for (String label : labels) {
                assertFalse(line.contains(label),
                        "no line of the foot carries a label of the register: " + line);
            }
        }
        if (language == RenderLanguage.GERMAN) {
            assertTrue(Pdf.flat(Pdf.textOfPage(pdf, 2))
                            .contains("Kennung (/BG-7/BT-46) 4399902000024 "
                                    + "(Schema der Kennung: 0088)"),
                    "the closing heading keeps the full labels");
        }
    }

    /**
     * A seller identifier stated without a scheme carries the word for it behind the
     * value, and so does the VAT identifier where the head data does not carry it — the
     * foot never writes a value a reader cannot place.
     */
    @Test
    void anIdentifierWithoutASchemeCarriesTheWordForIt() {
        SemanticDocument document = invoice().toBuilder()
                .removeUnder(SemanticPath.of("/BG-4/BT-29"))
                .put("/BG-4/BT-29/0", "LIEF-4711").build();

        byte[] pdf = new PdfRenderer().render(document,
                RenderOptions.defaults().with(Templates.example("letter.json")));

        List<String> foot = new ArrayList<>();
        for (Pdf.Run run : Pdf.runs(pdf, 1)) {
            if (run.baseline() < 120f && run.baseline() > 60f) {
                foot.add(run.text());
            }
        }
        assertTrue(foot.contains("LIEF-4711 ("
                        + Word.FOOT_IDENTIFIER.in(RenderLanguage.GERMAN) + ")"),
                "the identifier says what it is behind its value: " + foot);
    }

    /** The fold and punch marks are printed only where the template asks for them. */
    @Test
    void theMarksArePrintedOnlyWhenTheTemplateAsksForThem() {
        byte[] without = new PdfRenderer().render(invoice(), letter(RenderLanguage.GERMAN));
        byte[] off = new PdfRenderer().render(invoice(), RenderOptions.defaults()
                .with(Templates.of("""
                        {"template": "esj-render-template/0.1", "layout": "letter",
                         "letter": {"foldMarks": false, "holeMark": false}}""")));
        byte[] on = new PdfRenderer().render(invoice(), RenderOptions.defaults()
                .with(Templates.of("""
                        {"template": "esj-render-template/0.1", "layout": "letter",
                         "letter": {"foldMarks": true, "holeMark": true}}""")));

        assertArrayEquals(without, off, "marks turned off are the letter without them");
        assertNotEquals(Corpus.sha256(without), Corpus.sha256(on),
                "and marks turned on are printed");
        assertEquals(Pdf.flat(without), Pdf.flat(on),
                "a mark is printed matter and says nothing, so the text is the same");
    }
}
