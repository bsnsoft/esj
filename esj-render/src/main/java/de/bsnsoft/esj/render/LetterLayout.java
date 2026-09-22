package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The letter layout: the invoice as the letter a business sends, on a {@link Sheet}.
 *
 * <p>It shows exactly the same document as {@link PdfLayout} and it shows all of it. What
 * it changes is where a value stands and how a code is written: the recipient goes into the
 * address field of a window envelope, the facts a reader looks for first — number, dates,
 * references — stand in the reference line under it, the lines are a table, the totals are a
 * narrow block on the right, the payment details follow with the code of a credit transfer
 * beside them ({@link PaymentCode}), and the sender's own business details stand in the foot
 * of the first page, in the band the page reserves for them before it is filled. A code a reader does not read is written under its name
 * out of {@link DisplayNames}, and the code itself is listed once under the closing
 * heading, so the page never hides what the document says.
 *
 * <p>Everything the letter has no place of its own for stands under that closing heading
 * with its label and its semantic path, exactly as in the generic layout. Nothing is
 * derived: no carried-forward sum, no computed gross figure, no sentence that combines two
 * values into a statement. What is done to a value is formatting — the number picture and
 * the date picture of the language, and an account identifier that is an IBAN written in
 * groups of four.
 *
 * <h2>The geometry</h2>
 *
 * <p>The address field, the information block beside it and the fold and punch marks sit at
 * the distances DIN 5008 gives for a business letter, which are the distances a window
 * envelope and a two-hole punch are built to. On US Letter the same distances from the top
 * left corner are used, because the envelope is what they are for and not the paper.
 *
 * <p>The address field is the one block those distances own. Everything else the letter
 * writes — the reference line, the title, the notes, the tables, the totals, the payment
 * block, the closing section and the foot — stands between the left and the right margin,
 * so that a template whose letterhead prints a decorated edge keeps the text clear of it by
 * saying so in {@code margins}. The address field cannot: the hole of a window envelope is
 * where it is.
 */
final class LetterLayout extends InvoiceLayout {

    /** The type size of the title of the letter. */
    private static final float TITLE_SIZE = 14f;

    /** The type size of the recipient in the address field. */
    private static final float RECIPIENT_SIZE = 10f;

    /** The type size of the sender line above the recipient. */
    private static final float SENDER_SIZE = 6.4f;

    /** The type size of a value of the information block and of the reference line. */
    private static final float INFO_SIZE = 8f;

    /** The type size of a label over a value of the reference line. */
    private static final float REFERENCE_LABEL_SIZE = 6.4f;

    /** The air between two columns of the reference line. */
    private static final float REFERENCE_GAP = 10f;

    /**
     * The narrowest column the reference line is set in.
     *
     * <p>It decides how many columns a paper gets: as many as fit, and no more than
     * {@link #REFERENCE_MAX_COLUMNS}. The text width of A4 and of US Letter in this layout
     * is the same 180 mm, which gives five; a template that keeps its text clear of a
     * decorated edge gets three or four out of what is left.
     */
    private static final float REFERENCE_MIN_COLUMN = 95f;

    /** The most columns the reference line is set in, however wide the paper is. */
    private static final int REFERENCE_MAX_COLUMNS = 5;

    /** The air under one row of the reference line. */
    private static final float REFERENCE_ROW_GAP = 5f;

    /** The air over the rule that closes the reference line, and under it. */
    private static final float REFERENCE_RULE_GAP = 4f;

    /** The type size of a note above the table of lines. */
    private static final float NOTE_SIZE = 8.6f;

    /**
     * The type size of a line of the letter foot.
     *
     * <p>Not the size of a remark. What stands there is the seller's address, its contact
     * and its registers, and those are read off a page, off a scan and by somebody whose
     * eyes are not what they were.
     */
    private static final float FOOT_SIZE = 7.5f;

    /** The type size of a remark: the subject of a note, a row under a row of the totals. */
    private static final float SMALL_SIZE = 6.8f;

    /** The air over the rule of the letter foot, which the text of the page stops at. */
    private static final float FOOT_AIR = 6f;

    /** The type size of a row of the totals. */
    private static final float TOTAL_SIZE = 8.8f;

    /** The type size of the amount due, which is the figure the letter is about. */
    private static final float DUE_SIZE = 10.4f;

    /** How far the text of the letter stands from the left edge of the paper. */
    static final float LEFT = 20f * Sheet.MM;

    /** How far from the left edge the text of the letter ends. */
    static final float RIGHT_EDGE = 200f * Sheet.MM;

    /** How wide the address field is. */
    private static final float WINDOW_WIDTH = 85f * Sheet.MM;

    /** How tall it is. */
    private static final float WINDOW_HEIGHT = 45f * Sheet.MM;

    /** How much of its top the sender line takes. */
    private static final float SENDER_BAND = 5f * Sheet.MM;

    /** How far from the left edge the information block begins. */
    private static final float INFO_LEFT = 125f * Sheet.MM;

    /** How far below the top edge it begins. */
    private static final float INFO_TOP = 45f * Sheet.MM;

    /** The narrowest the information block is allowed to become between the margins. */
    private static final float INFO_MIN_WIDTH = 100f;

    /** The air between the printed head of a letterhead and the block under it. */
    private static final float INFO_HEAD_GAP = 8f;

    /** The white the letter keeps below its text, which the page footer sits in. */
    static final float BOTTOM = 25f * Sheet.MM;

    /**
     * The white a page after the first keeps above everything it carries.
     *
     * <p>The compact head stands under it and the text of the page under the head
     * ({@link Sheet#headOnFollowingPages()}), so a template that states a top margin for
     * its following sheet keeps both of them off what that sheet prints. Twenty
     * millimetres is what a business letter gives a following page, and the head and the
     * text together then begin where the text of a following page began when the head
     * stood inside the margin.
     */
    static final float FOLLOWING_TOP = 20f * Sheet.MM;

    /** The air between the head of the letter and the text under it. */
    static final float BODY_GAP = 8f * Sheet.MM;

    /** How wide the block of totals is. */
    private static final float TOTALS_WIDTH = 85f * Sheet.MM;

    /** The air under one row of the totals. */
    private static final float ROW_GAP = 1.4f;

    /** The air between two columns of the letter foot. */
    private static final float FOOT_GAP = 14f;

    /** The air between the rule of the letter foot and the columns under it. */
    private static final float FOOT_BAND_GAP = 5f;

    /** The letters and digits an account identifier is grouped in where it is an IBAN. */
    private static final int IBAN_GROUP = 4;

    /**
     * How wide the payment code is, quiet zone included.
     *
     * <p>Thirty millimetres is the size a code of this kind is printed at on paper a
     * person holds: a telephone reads it from a hand's distance, and a symbol of the
     * version the guideline bounds it at still has modules of half a millimetre.
     */
    private static final float CODE_SIDE = 30f * Sheet.MM;

    /** The air between the payment block and the code standing to the right of it. */
    private static final float CODE_GAP = 10f;

    /** The air between the code and the word under it. */
    private static final float CODE_CAPTION_GAP = 3f;

    /** The qualifier a code list puts behind a name to tell it from another one. */
    private static final Pattern BRACKETED =
            Pattern.compile("\\s*[\\[(][^\\])]*[\\])]\\s*$");

    private final LetterOptions letter;

    /** The codes a name was written for, each once, by list and then by code. */
    private final Map<String, Field> codes = new TreeMap<>();

    /** What the letter has to say about itself under the closing heading, in order. */
    private final List<Field> remarks = new ArrayList<>();

    /** The title of the letter, which is also the head of every page after the first. */
    private String title = "";

    /**
     * Prepares the letter layout of a document.
     *
     * @param document the document
     * @param registry the registry the types and the English labels come from
     * @param language the language of the rendering
     * @param sheet    the sheet to draw on
     * @param template the branded template, or {@code null} for a letter on plain paper
     * @param letter   what the sender decided about the letter itself
     */
    LetterLayout(SemanticDocument document, Registry registry, RenderLanguage language,
                 Sheet sheet, RenderTemplate template, LetterOptions letter) {
        super(document, registry, language, sheet, template);
        this.letter = letter;
    }

    /**
     * Returns the margins of the first page of a letter: the text begins under the address
     * field and the information block beside it.
     *
     * @param letter what the sender decided about the letter
     * @param size   the paper
     * @return the margins
     */
    static Margins marginsFirst(LetterOptions letter, PageSize size) {
        float top = letter.window() == LetterOptions.Window.NONE
                ? INFO_TOP
                : letter.window().topInMillimetres() * Sheet.MM + WINDOW_HEIGHT + BODY_GAP;
        return new Margins(LEFT, rightMargin(size), top, BOTTOM);
    }

    /**
     * Returns the margins of every page after the first, whose top holds the compact head.
     *
     * @param size the paper
     * @return the margins
     */
    static Margins marginsFollowing(PageSize size) {
        return new Margins(LEFT, rightMargin(size), FOLLOWING_TOP, BOTTOM);
    }

    /**
     * Returns the white right of the text, so that the text and the information block end
     * at the same place whatever the paper is.
     */
    private static float rightMargin(PageSize size) {
        return Math.max(size.width() - RIGHT_EDGE, 10f);
    }

    /** Whether the sum of the invoice lines was written as the closing row of the table. */
    private boolean sumOnTheTable;

    /** Draws the whole letter and writes its heads and footers. */
    @Override
    void draw() {
        reserve();
        // Every page after the first is opened under the compact head, so the room for it
        // is kept before one is opened. A letter whose document states neither a type nor
        // a number has no head to write and keeps nothing.
        if (!documentTitle().isEmpty()) {
            sheet.headOnFollowingPages();
        }
        // The head data is taken before anything is drawn, because the foot of the first
        // page prints what is left of the seller and two of these fields — the VAT
        // identifier and the tax number — belong beside the address rather than under the
        // letter. Where they stand on the page is decided further down.
        List<Field> reference = referenceFields();
        float lowest = head(reference);
        // The foot of the first page is furniture and is drawn before the letter is
        // written: the page it stands on has to know how much of itself it has for text.
        footOfTheFirstPage();
        settle(lowest);
        if (letter.window() == LetterOptions.Window.NONE) {
            flowRecipient();
        }
        referenceLine(reference);
        title();
        notes();
        lines();
        documentAllowancesAndCharges("BG-20", Word.ALLOWANCES);
        documentAllowancesAndCharges("BG-21", Word.CHARGES);
        totals();
        payment();
        furtherDetails();
        sheet.finish(identity(), Word.PAGE_OF.in(language), title);
    }

    // ---------------------------------------------------------------- what a layout decides

    @Override
    float itemColumnWeight() {
        return 4.2f;
    }

    @Override
    float lineColumnWeight() {
        return 0.8f;
    }

    @Override
    boolean ruleUnderEveryLine() {
        return true;
    }

    /**
     * Hangs the sum of the invoice lines on the table of them: the figure in the column
     * the net amount of every line stands in, under the rule that closes the table.
     *
     * <p>BT-106 is the one total that is a statement about the table above it rather than
     * about the invoice, and a reader checks it by running down that column. Standing at
     * the head of the block of totals it was a figure in a different column, in a
     * different width, a section away from the figures it is the sum of. It is written
     * here and therefore not written there; a document that states it without stating a
     * single line keeps it in the block, since there is no table for it to close.
     *
     * @param netAmount the column the net amount of a line stands in
     * @return the row that closes the table, or {@code null} where the document states
     *         no sum of its lines
     */
    @Override
    Table.Closing closingRow(int netAmount) {
        for (SemanticPath totals : instances(SemanticPath.root(), "BG-22")) {
            SemanticPath path = path(totals, "BT-106");
            String value = read(path);
            if (value.isEmpty()) {
                continue;
            }
            place(path, value);
            sumOnTheTable = true;
            // The header of the column names the currency, so the figure does not.
            return new Table.Closing(label(path), value, netAmount);
        }
        return null;
    }

    /**
     * Returns a percentage as the document wrote it — {@code 19 %}, {@code 7 %},
     * {@code 10,7 %} — and every other value the way the generic layout writes it.
     *
     * <p>A rate on a letter is read as a word and not as a figure in a column of money:
     * two decimal places behind every rate are two digits that say nothing, and a reader
     * who has to tell 19 from 19.5 is told by the digits the document states. Nothing is
     * rounded and no digit of the value is lost — only the trailing zeros of the fraction,
     * which the figure does not carry. It holds for every percentage of the letter: the
     * rate of a line, the rate of a VAT category, and the percentage of an allowance or a
     * charge.
     */
    @Override
    String formatted(SemanticType type, String content) {
        return Formats.asWritten(type, content, language);
    }

    /**
     * Returns the name of a code, and remembers the code so that the closing heading can
     * list it. A code no table of names knows is written as the code it is.
     */
    @Override
    String codeName(String termId, String code) {
        Optional<String> name = nameOf(termId, code);
        name.ifPresent(written -> remember(termId, code, written));
        return name.orElse(code);
    }

    /**
     * Returns the name a table of this repository gives a code, without remembering it.
     *
     * <p>A name is remembered where it is written, and a page writes a name only where it
     * has room for it: the recipient of a letter whose address field is full keeps its
     * country under the closing heading, and a line there that read <i>Country DE =
     * Germany</i> would say the page had written a name it never wrote.
     *
     * @param termId the identifier of the term the code belongs to
     * @param code   the code, as the document spells it
     * @return the name, or empty where no table of this repository names it
     */
    private Optional<String> nameOf(String termId, String code) {
        if (code.isEmpty()) {
            return Optional.empty();
        }
        return DisplayNames.CodeList.of(codeList(termId))
                .flatMap(list -> DisplayNames.of(list, code, language));
    }

    /** Remembers a code the page wrote the name of, under the list it belongs to. */
    private void remember(String termId, String code, String name) {
        DisplayNames.CodeList.of(codeList(termId))
                .ifPresent(list -> record(list, code, name));
    }

    /**
     * Returns the name of a unit beside a quantity: the name of more than one where the
     * quantity is not exactly one, and the name the page writes rather than the name the
     * code list carries.
     */
    @Override
    String unitShown(SemanticPath prefix, String term, String quantity) {
        String code = cell(prefix, term);
        Optional<DisplayNames.CodeList> list = DisplayNames.CodeList.of(codeList(term));
        if (code.isEmpty() || list.isEmpty()) {
            return code;
        }
        boolean several = !quantity.isEmpty() && !isOne(quantity);
        Optional<String> name = several
                ? DisplayNames.ofSeveral(list.get(), code, language)
                : DisplayNames.of(list.get(), code, language);
        if (name.isEmpty()) {
            return code;
        }
        // The closing heading carries the name of one, which is the name the code list
        // carries: the line there is the fact behind the word on the page.
        DisplayNames.of(list.get(), code, language)
                .ifPresent(written -> record(list.get(), code, written));
        return onPage(name.get());
    }

    /**
     * Returns the name of a unit as a quantity is written with it.
     *
     * <p>A code list tells two units apart that a page does not have to: it calls MIN
     * <i>minute [unit of time]</i> because it also carries a minute of arc, and TNE
     * <i>tonne (metric ton)</i> because a ton is not one everywhere. Beside a figure that
     * reads <i>30</i>, the bracket is not a disambiguation but noise, and the line under
     * the closing heading carries the name of the list word for word, so nothing of the
     * fact is lost by leaving it off the quantity.
     *
     * @param name the name the table carries
     * @return the name a quantity is written with
     */
    static String onPage(String name) {
        return BRACKETED.matcher(name).replaceFirst("");
    }

    /**
     * Returns what hangs under a row of the table of lines: one line per allowance, per
     * charge and per attribute of the item, and then whatever else the line carries.
     *
     * <p>A line-level allowance is five business terms, and five label-and-value lines per
     * allowance under a row are the data sheet this layout exists to replace: a row of
     * seven allowances closed a page by itself. On one line the five read as the one thing
     * they are — what was taken off, what of, and why — and nothing is combined into a
     * figure or into a sentence: every value stands beside the word for it, and a value the
     * layout has no word for stands there too, under the label the model gives it.
     */
    @Override
    List<String> lineDetails(SemanticPath line) {
        List<String> details = new ArrayList<>();
        for (SemanticPath attribute : instances(child(line, "BG-31"), "BG-32")) {
            details.add(attribute(attribute));
        }
        // The period of a line and the quantity its unit price is a price for are two
        // terms each that say one thing; they read as one line for the same reason an
        // allowance does.
        for (String group : new String[] {"BG-26", "BG-29"}) {
            List<String> written = detailsOf(collect(child(line, group)));
            if (!written.isEmpty()) {
                details.add(String.join(" · ", written));
            }
        }
        for (SemanticPath allowance : instances(line, "BG-27")) {
            details.add(adjustment(allowance, Word.LINE_ALLOWANCE, "BT-136", "BT-137",
                    "BT-138", "BT-139"));
        }
        for (SemanticPath charge : instances(line, "BG-28")) {
            details.add(adjustment(charge, Word.LINE_CHARGE, "BT-141", "BT-142", "BT-143",
                    "BT-144"));
        }
        details.addAll(detailsOf(collect(line)));
        return details;
    }

    /** Returns the one line an item attribute takes: what it is called and what it says. */
    private String attribute(SemanticPath attribute) {
        String named = join(Word.ITEM_ATTRIBUTE.in(language), cell(attribute, "BT-160"));
        String value = cell(attribute, "BT-161");
        return rest(value.isEmpty() ? named : named + ": " + value, attribute);
    }

    /**
     * Returns the one line an allowance or a charge on an invoice line takes.
     *
     * @param group      the group instance
     * @param word       what the line calls it
     * @param amountTerm the term of the amount
     * @param baseTerm   the term of the amount it was worked out on
     * @param rateTerm   the term of the percentage
     * @param reasonTerm the term of the reason in words
     * @return the line
     */
    private String adjustment(SemanticPath group, Word word, String amountTerm,
                              String baseTerm, String rateTerm, String reasonTerm) {
        List<String> parts = new ArrayList<>();
        parts.add(join(word.in(language), amount(cell(group, amountTerm))));
        String percentage = cell(group, rateTerm);
        if (!percentage.isEmpty()) {
            parts.add(join(Word.COLUMN_PERCENTAGE.in(language), percentage));
        }
        String base = cell(group, baseTerm);
        if (!base.isEmpty()) {
            parts.add(join(Word.COLUMN_BASE.in(language), amount(base)));
        }
        String reason = cell(group, reasonTerm);
        if (!reason.isEmpty()) {
            parts.add(reason);
        }
        return rest(String.join(" · ", parts), group);
    }

    /** Appends whatever a group carries beyond the terms the line already named. */
    private String rest(String line, SemanticPath group) {
        StringBuilder text = new StringBuilder(line);
        for (Field field : collect(group)) {
            text.append(" · ").append(field.label()).append(' ').append(field.value());
        }
        return text.toString();
    }

    /**
     * Returns the VAT of a row of a table: the rate the document states.
     *
     * <p>A column of a line is narrow and a category of the standard has a long name —
     * <i>Steuerschuldnerschaft des Leistungsempfängers</i> is one — so the name goes where
     * there is room for it and where a reader looks for it anyway: the VAT breakdown in the
     * block of totals, which names every category the invoice uses. The code itself is
     * listed under the closing heading, so a rate of zero can always be traced back to the
     * category it belongs to. Where the document states no rate at all, the cell carries
     * the name rather than nothing.
     */
    @Override
    String vatShown(SemanticPath prefix, String categoryTerm, String rateTerm) {
        String category = cell(prefix, categoryTerm);
        String rate = cell(prefix, rateTerm);
        String name = categoryName(categoryTerm, category);
        // A category no table of this repository names is written as the code it is, beside
        // the rate: the closing heading carries no line for it, so the cell is the only
        // place a reader would meet it.
        if (name.isEmpty()) {
            return join(rate, category);
        }
        return rate.isEmpty() ? name : rate;
    }

    /**
     * Returns the name of a VAT category and remembers its code, or the empty string where
     * no table of this repository names it.
     */
    private String categoryName(String categoryTerm, String category) {
        Optional<String> name = nameOf(categoryTerm, category);
        name.ifPresent(written -> remember(categoryTerm, category, written));
        return name.orElse("");
    }

    /** Remembers a code whose name the page writes, so that the code reaches a page too. */
    private void record(DisplayNames.CodeList list, String code, String name) {
        codes.putIfAbsent(list.ordinal() + "|" + code,
                new Field(code, list.word().in(language), code + " = " + name, false));
    }

    // ---------------------------------------------------------------- the head of the letter

    /**
     * Draws the address field, and the information block beside it where the template
     * asked for the block rather than for the reference line, on the first page.
     *
     * @param reference the head data, for the block
     * @return the lowest y anything of the head reached, so that the text can begin under it
     */
    private float head(List<Field> reference) {
        float lowest = sheet.y();
        if (letter.window() != LetterOptions.Window.NONE) {
            lowest = Math.min(lowest, window());
        }
        return letter.information() == LetterOptions.Information.BLOCK
                ? Math.min(lowest, information(reference)) : lowest;
    }

    /** Draws the sender line and the recipient into the address field. */
    private float window() {
        float top = sheet.pageHeight() - letter.window().topInMillimetres() * Sheet.MM;
        Fonts.Face face = sheet.fonts().regular();
        String sender = senderLine();
        if (!sender.isEmpty()) {
            sheet.blockAt(sender, LEFT, top, WINDOW_WIDTH, face, SENDER_SIZE, palette.muted());
        }
        List<String> written = recipient(WINDOW_WIDTH, WINDOW_HEIGHT - SENDER_BAND);
        return lines(written, LEFT, top - SENDER_BAND, WINDOW_WIDTH, RECIPIENT_SIZE);
    }

    /**
     * Writes the recipient at the head of the letter in the ordinary flow, which is what a
     * letter with no address field carries instead of one — a letter that is not posted,
     * or one whose letterhead prints an address field of its own.
     */
    private void flowRecipient() {
        Fonts.Face face = sheet.fonts().regular();
        List<String> written = recipient(sheet.width(), Float.MAX_VALUE);
        if (written.isEmpty()) {
            return;
        }
        for (String line : written) {
            sheet.block(line, sheet.left(), sheet.width(), face, RECIPIENT_SIZE,
                    palette.text());
        }
        sheet.down(BODY_GAP);
    }

    /**
     * Returns the one line above the address field: the sender, as an envelope shows it.
     *
     * <p>Nothing here is marked placed. The line repeats what the letter says elsewhere —
     * in its foot, or under its closing heading where the letterhead already prints it —
     * and a repetition that claimed a value would take it away from the place it belongs.
     */
    private String senderLine() {
        SemanticPath seller = SemanticPath.group("/BG-4");
        SemanticPath address = child(seller, "BG-5");
        List<String> parts = new ArrayList<>();
        for (SemanticPath path : List.of(path(seller, "BT-27"), path(address, "BT-35"))) {
            String value = read(path);
            if (!value.isEmpty()) {
                parts.add(value);
            }
        }
        String town = join(read(path(address, "BT-38")), read(path(address, "BT-37")));
        if (!town.isEmpty()) {
            parts.add(town);
        }
        return String.join(" · ", parts);
    }

    /**
     * Returns the lines of the recipient that the room they have holds, and marks every
     * value one of them shows placed.
     *
     * @param width  how wide a line may be
     * @param budget how tall the whole block may be
     * @return the lines
     */
    private List<String> recipient(float width, float budget) {
        SemanticPath buyer = SemanticPath.group("/BG-7");
        SemanticPath address = child(buyer, "BG-8");
        Bounded block = new Bounded(width, budget);
        for (SemanticPath path : List.of(path(buyer, "BT-44"), path(buyer, "BT-45"),
                path(address, "BT-50"), path(address, "BT-51"), path(address, "BT-163"))) {
            block.add(read(path), path);
        }
        SemanticPath post = path(address, "BT-53");
        SemanticPath city = path(address, "BT-52");
        block.add(join(read(post), read(city)), post, city);
        SemanticPath subdivision = path(address, "BT-54");
        block.add(read(subdivision), subdivision);
        SemanticPath country = path(address, "BT-55");
        String code = read(country);
        Optional<String> named = nameOf("BT-55", code);
        if (block.add(named.orElse(code), country)) {
            named.ifPresent(written -> remember("BT-55", code, written));
        }
        return block.lines();
    }

    /**
     * The lines of a block and the room they have.
     *
     * <p>The address field of a window envelope is a hole of a stated size, so what
     * decides whether a line belongs in it is how tall the line turns out and not how many
     * lines came before: a recipient of five values whose name wraps over three lines is
     * taller than one of seven that do not. A line the block has no room for is not
     * written, and the value behind it is not marked placed, so the closing section prints
     * it — which is the promise of this layout rather than a fallback.
     *
     * <p>The block closes at the first line it cannot hold, and the shorter lines behind
     * that one are not slipped in above it. An address is read from the top down: what a
     * full field has to lose is its tail, never the name of the addressee with the street
     * and the town left standing under the gap.
     */
    private final class Bounded {

        private final List<String> written = new ArrayList<>();
        private final float width;
        private final float budget;
        private float used;
        private boolean full;

        Bounded(float width, float budget) {
            this.width = width;
            this.budget = budget;
        }

        /**
         * Adds a line where the block still has the height for it.
         *
         * @param line  the line, empty for a value the document does not carry
         * @param shown the paths the line writes, marked placed where it is written
         * @return whether the line was written
         */
        boolean add(String line, SemanticPath... shown) {
            if (line.isEmpty() || full) {
                return false;
            }
            float height =
                    Sheet.measure(line, sheet.fonts().regular(), RECIPIENT_SIZE, width);
            if (used + height > budget) {
                full = true;
                return false;
            }
            used += height;
            written.add(line);
            for (SemanticPath path : shown) {
                place(path, read(path));
            }
            return true;
        }

        /** Returns the lines the block holds. */
        List<String> lines() {
            return written;
        }
    }

    /** Writes a block of lines at a place of the page and returns the y it ended at. */
    private float lines(List<String> written, float x, float top, float width, float size) {
        float y = top;
        Fonts.Face face = sheet.fonts().regular();
        for (String line : written) {
            y -= sheet.blockAt(line, x, y, width, face, size, palette.text());
        }
        return y;
    }

    /**
     * Returns the number, the dates and the references a reader looks for before reading
     * anything else, in the order a business letter names them, and marks them placed.
     *
     * <p>The seller's VAT identifier and its tax registration identifier close the list
     * where the document states them. They are what a recipient checks a business invoice
     * by, and the foot of a letter stands on its last page: the two belong on the page a
     * reader has in front of them. A value taken here is placed, so neither the foot of
     * the letter nor the closing heading prints it a second time.
     *
     * <p>The same list stands in the reference line and in the information block. Which of
     * the two the letter draws is a decision about the paper and not about the invoice.
     *
     * @return the fields, with every label that two of them share made unambiguous
     */
    private List<Field> referenceFields() {
        SemanticPath buyer = SemanticPath.group("/BG-7");
        SemanticPath seller = SemanticPath.group("/BG-4");
        SemanticPath delivery = SemanticPath.group("/BG-13");
        SemanticPath period = child(delivery, "BG-14");
        List<Field> fields = new ArrayList<>();
        add(fields, SemanticPath.of("/BT-1"));
        add(fields, SemanticPath.of("/BT-2"));
        add(fields, path(delivery, "BT-72"));
        add(fields, path(period, "BT-73"));
        add(fields, path(period, "BT-74"));
        add(fields, SemanticPath.of("/BT-9"));
        add(fields, SemanticPath.of("/BT-10"));
        add(fields, SemanticPath.of("/BT-13"));
        add(fields, SemanticPath.of("/BT-14"));
        add(fields, SemanticPath.of("/BT-12"));
        add(fields, SemanticPath.of("/BT-11"));
        customerNumber(fields, path(buyer, "BT-46"));
        add(fields, path(seller, "BT-31"), Word.VAT_IDENTIFIER);
        add(fields, path(seller, "BT-32"), Word.TAX_NUMBER);
        return disambiguate(fields);
    }

    /**
     * Draws the information block beside the address field, for paper whose top right is
     * free.
     *
     * <p>It begins at the place DIN 5008 gives it, or under the printed head the template
     * declares, whichever is lower, and it ends at the right margin rather than at a fixed
     * width — a template that keeps its text clear of a decorated edge keeps this block
     * clear of it too. Where the margins leave it less than {@link #INFO_MIN_WIDTH}, it
     * starts further left instead of becoming a column of broken words.
     *
     * @param fields the head data
     * @return the y the block ended at
     */
    private float information(List<Field> fields) {
        float right = sheet.right();
        float x = Math.max(sheet.left(), Math.min(INFO_LEFT, right - INFO_MIN_WIDTH));
        float top = sheet.pageHeight()
                - Math.max(INFO_TOP, letter.printedHead() + INFO_HEAD_GAP);
        return top - fieldsAt(fields, x, top, Math.max(right - x, INFO_MIN_WIDTH),
                0.5f, INFO_SIZE);
    }

    /**
     * Draws the reference line: the number, the dates and the references across the text
     * width, under the address zone and over the title, closed by a fine rule.
     *
     * <p>It is the default, and it is the one form of the head data that asks nothing of
     * the paper. A letterhead prints its own contact block at the top right, or a
     * decorated edge, or both; the block of DIN 5008 sits in that corner and a template
     * has to measure its way around it. The reference line stands in the flow between the
     * margins, so it is clear of whatever the letterhead prints as soon as the margins are.
     *
     * <p>Small labels stand over their values in equal columns, as many as the text width
     * holds, and the fields run on into further rows. A value too wide for one column takes
     * two, and one too wide for two wraps inside its cell: a value never reaches into the
     * column beside it, because the cell beside it is a different fact.
     *
     * @param fields the head data
     */
    private void referenceLine(List<Field> fields) {
        if (letter.information() != LetterOptions.Information.LINE || fields.isEmpty()) {
            return;
        }
        int columns = Math.max(1, Math.min(REFERENCE_MAX_COLUMNS,
                (int) Math.floor(sheet.width() / REFERENCE_MIN_COLUMN)));
        float column = (sheet.width() - (columns - 1) * REFERENCE_GAP) / columns;
        for (List<Cell> row : rows(fields, columns, column)) {
            drawReferenceRow(row, column);
        }
        sheet.down(REFERENCE_RULE_GAP);
        sheet.rule();
        sheet.down(REFERENCE_RULE_GAP);
    }

    /**
     * Cuts the fields of the reference line into rows of a number of columns.
     *
     * @param fields  the fields
     * @param columns how many columns a row has
     * @param column  how wide one of them is
     * @return the rows
     */
    private List<List<Cell>> rows(List<Field> fields, int columns, float column) {
        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> row = new ArrayList<>();
        int used = 0;
        for (Field field : fields) {
            int span = Math.min(columns, span(field, column));
            if (used + span > columns) {
                rows.add(row);
                row = new ArrayList<>();
                used = 0;
            }
            row.add(new Cell(field, span));
            used += span;
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }

    /** Returns how many columns a field takes: two where one is too narrow for its value. */
    private int span(Field field, float column) {
        Fonts.Face face = sheet.fonts().regular();
        return face.width(face.showable(field.value()), INFO_SIZE) > column ? 2 : 1;
    }

    /** Returns how wide a cell of a span is, the columns it joins and the air between them. */
    private static float widthOf(int span, float column) {
        return span * column + (span - 1) * REFERENCE_GAP;
    }

    /** Draws one row of the reference line and moves the cursor under it. */
    private void drawReferenceRow(List<Cell> row, float column) {
        float height = 0;
        for (Cell cell : row) {
            height = Math.max(height, height(cell, column));
        }
        sheet.require(height + REFERENCE_ROW_GAP);
        float top = sheet.y();
        float x = sheet.left();
        Fonts.Face face = sheet.fonts().regular();
        for (Cell cell : row) {
            float width = widthOf(cell.span(), column);
            float y = top;
            for (String line : lines(cell.field().label(), REFERENCE_LABEL_SIZE, width)) {
                sheet.show(line, x, y - REFERENCE_LABEL_SIZE, face, REFERENCE_LABEL_SIZE,
                        palette.muted());
                y -= Sheet.lineHeight(REFERENCE_LABEL_SIZE);
            }
            for (String line : lines(cell.field().value(), INFO_SIZE, width)) {
                sheet.show(line, x, y - INFO_SIZE, face, INFO_SIZE, palette.text());
                y -= Sheet.lineHeight(INFO_SIZE);
            }
            x += width + REFERENCE_GAP;
        }
        sheet.down(height + REFERENCE_ROW_GAP);
    }

    /** Returns how tall a cell of the reference line turns out at a column width. */
    private float height(Cell cell, float column) {
        float width = widthOf(cell.span(), column);
        return lines(cell.field().label(), REFERENCE_LABEL_SIZE, width).size()
                        * Sheet.lineHeight(REFERENCE_LABEL_SIZE)
                + lines(cell.field().value(), INFO_SIZE, width).size()
                        * Sheet.lineHeight(INFO_SIZE);
    }

    /** Returns the lines a text takes in a cell of the reference line. */
    private List<String> lines(String text, float size, float width) {
        Fonts.Face face = sheet.fonts().regular();
        return Sheet.wrap(face.showable(text), face, size, width);
    }

    /**
     * Adds the value at a path to a block under a word of this layout rather than under
     * the label of the register, and marks it placed.
     *
     * @param fields the block
     * @param path   the path of the value
     * @param word   what this layout calls it
     */
    private void add(List<Field> fields, SemanticPath path, Word word) {
        document.value(path).ifPresent(value -> {
            if (placed.add(path)) {
                fields.add(new Field(path.term(), word.in(language),
                        withComponents(path, value), false));
            }
        });
    }

    /**
     * Adds the number the seller files the buyer under to the head data.
     *
     * <p>BT-46 is the buyer identifier of the standard and its label says so; at the head
     * of a letter it is the customer number, which is what a reader is looking for there.
     * The scheme the identifier is stated in belongs to the identifier and not to the head
     * of the letter, so the value is not marked placed here and reaches the closing
     * heading whole, scheme and all.
     *
     * @param fields the head data
     * @param path   the path of BT-46
     */
    private void customerNumber(List<Field> fields, SemanticPath path) {
        document.value(path).ifPresent(value -> fields.add(new Field(path.term(),
                Word.CUSTOMER_NUMBER.in(language), shown(path, value), false)));
    }

    /** Moves the cursor under whatever the head of the letter reached. */
    private void settle(float lowest) {
        float target = lowest - BODY_GAP;
        if (sheet.y() > target) {
            sheet.down(sheet.y() - target);
        }
    }

    /**
     * Returns what the title of the letter will say, without writing anything down and
     * without marking a value placed: the name of the document type and the number of the
     * document, which is also what the compact head of a following page carries.
     *
     * @return the title, empty where the document states neither
     */
    private String documentTitle() {
        SemanticPath type = SemanticPath.of("/BT-3");
        String code = read(type);
        return join(nameOf("BT-3", code).orElse(code), read(SemanticPath.of("/BT-1")));
    }

    /**
     * Writes the title of the letter: what kind of document this is, by name, and the
     * number it carries.
     */
    private void title() {
        SemanticPath type = SemanticPath.of("/BT-3");
        String kind = codeName("BT-3", read(type));
        place(type, read(type));
        title = join(kind, read(SemanticPath.of("/BT-1")));
        if (title.isEmpty()) {
            return;
        }
        sheet.down(2f);
        sheet.block(title, sheet.left(), sheet.width(), sheet.fonts().bold(), TITLE_SIZE,
                palette.heading());
        sheet.down(3f);
        sheet.rule();
    }

    /** Writes the notes of the invoice as paragraphs above the table. */
    private void notes() {
        List<SemanticPath> notes = instances(SemanticPath.root(), "BG-1");
        if (notes.isEmpty()) {
            return;
        }
        sheet.down(SECTION_GAP);
        Fonts.Face face = sheet.fonts().regular();
        for (SemanticPath note : notes) {
            SemanticPath subject = path(note, "BT-21");
            String code = cell(note, "BT-21");
            if (!code.isEmpty()) {
                sheet.block(label(subject) + ": " + code, sheet.left(), sheet.width(), face,
                        SMALL_SIZE, palette.muted());
            }
            String text = cell(note, "BT-22");
            if (!text.isEmpty()) {
                sheet.block(text, sheet.left(), sheet.width(), face, NOTE_SIZE, palette.text());
            }
            for (Field field : collect(note)) {
                sheet.block(field.label() + ": " + field.value(), sheet.left(), sheet.width(),
                        face, SMALL_SIZE, palette.muted());
            }
            sheet.down(4f);
        }
    }

    // ---------------------------------------------------------------- the money

    /**
     * Draws the totals: the sums of the document, the VAT per category out of the
     * breakdown, and the amount due last and in bold.
     */
    private void totals() {
        List<SemanticPath> documents = instances(SemanticPath.root(), "BG-22");
        List<SemanticPath> breakdown = instances(SemanticPath.root(), "BG-23");
        List<Placement> displayed =
                carried(Placement.Position.TOTALS, List.of(SemanticPath.root()));
        if (documents.isEmpty() && breakdown.isEmpty() && displayed.isEmpty()) {
            return;
        }
        List<Row> rows = new ArrayList<>();
        for (SemanticPath totals : documents) {
            for (String term : new String[] {"BT-106", "BT-107", "BT-108", "BT-109"}) {
                if (!(sumOnTheTable && "BT-106".equals(term))) {
                    row(rows, totals, term, false);
                }
            }
        }
        for (SemanticPath category : breakdown) {
            vatRows(rows, category);
        }
        for (SemanticPath totals : documents) {
            for (String term : new String[] {"BT-110", "BT-111", "BT-112", "BT-113",
                                             "BT-114"}) {
                row(rows, totals, term, false);
            }
            row(rows, totals, "BT-115", true);
            for (Field field : disambiguate(collect(totals))) {
                rows.add(new Row(field.label(), field.value(), false, false));
            }
        }
        for (Placement placement : displayed) {
            rows.add(new Row(header(placement, false),
                    placedCell(SemanticPath.root(), placement), false, false));
        }
        drawTotals(rows);
        if (!displayed.isEmpty()) {
            displayedNote();
        }
    }

    /**
     * Adds one row of the totals, where the document carries that term.
     *
     * <p>A figure without VAT and the figure with it are called the same thing by the
     * localization of the visualization, which keeps them apart by the table each of them
     * stands in; a block that lists them under one another cannot, so the word for it is
     * written behind the label whenever the language has one.
     */
    private void row(List<Row> rows, SemanticPath totals, String term, boolean due) {
        SemanticPath path = path(totals, term);
        String value = cell(totals, term);
        if (value.isEmpty()) {
            return;
        }
        String name = labels.netOrGross(term).map(word -> label(path) + " " + word)
                .orElseGet(() -> label(path));
        rows.add(new Row(name, amount(value, currencyOf(term)), due, false));
    }

    /**
     * Adds the rows of one category of the VAT breakdown: the tax under the name of the
     * category and the rate, the amount it was calculated on under it, and the reason the
     * document states where it states one.
     */
    private void vatRows(List<Row> rows, SemanticPath category) {
        String code = cell(category, "BT-118");
        String rate = cell(category, "BT-119");
        String name = categoryName("BT-118", code);
        // The standard rate is what a rate beside a percentage means, so naming it there
        // would only be the page saying the same thing twice.
        String vat = rate.isEmpty() || !"S".equals(code)
                ? join(rate, name.isEmpty() ? code : name) : rate;
        String base = cell(category, "BT-116");
        if (!base.isEmpty()) {
            // The base belongs to the rate under it and has to say so: a column of figures
            // that names one of two rows leaves a reader guessing which base bore which
            // tax. Both rows are set at the same size for the same reason — they are one
            // statement about one category, not a figure and a remark about it.
            rows.add(new Row(join(Word.COLUMN_TAXABLE.in(language), vat), amount(base),
                    false, false));
        }
        String tax = cell(category, "BT-117");
        if (!tax.isEmpty() || !vat.isEmpty()) {
            rows.add(new Row(join(Word.COLUMN_VAT_AMOUNT.in(language), vat), amount(tax),
                    false, false));
        }
        for (Field field : collect(category)) {
            rows.add(new Row(field.label(), field.value(), false, true));
        }
    }

    /** Returns an amount with the currency of the document behind it. */
    private String amount(String value) {
        return amount(value, currency);
    }

    /**
     * Returns an amount with a currency behind it.
     *
     * <p>The block of totals has no column header to carry a currency, so every row
     * carries its own — which is what lets the one row stated in another currency say so
     * where a table could not.
     *
     * @param value the figure, formatted
     * @param code  the currency code, empty where there is none to write
     * @return the text of the cell
     */
    private static String amount(String value, String code) {
        return value.isEmpty() || code.isEmpty() ? value : value + " " + code;
    }

    /**
     * Draws the block of totals, narrow and against the right edge of the text.
     *
     * <p>The block is kept together where it fits on an empty page, for the reason a row of
     * a table is: a reader reads the sums as one thing, and a page break between the net
     * total and the amount due is a reader turning the page to find out what to pay. A
     * block too tall for a page of its own flows, and the amount due keeps the rule above
     * it either way.
     */
    private void drawTotals(List<Row> rows) {
        float width = Math.min(TOTALS_WIDTH, sheet.width());
        float x = sheet.right() - width;
        sheet.down(SECTION_GAP);
        float block = 0;
        for (Row row : rows) {
            block += height(row, width);
        }
        if (block <= sheet.nextPageHeight()) {
            sheet.require(block);
        }
        Fonts.Face regular = sheet.fonts().regular();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float size = size(row);
            Fonts.Face face = row.due() ? sheet.fonts().bold() : regular;
            Ink ink = row.muted() ? palette.muted() : palette.text();
            // The last row of a block that flows is asked for together with the row above
            // it: the amount due alone at the top of a page, with the sums it follows from
            // on the page before, reads as a figure of whatever comes next.
            float widow = i == rows.size() - 2 ? height(rows.get(i + 1), width) : 0f;
            if (row.due()) {
                sheet.require(Sheet.lineHeight(size) + 6f);
                sheet.down(3f);
                sheet.rule(x, sheet.right(), sheet.y(), palette.rule());
                sheet.down(3f);
            }
            float value = face.width(face.showable(row.value()), size);
            List<String> label = label(row, face, size, width - value - 10f);
            float height = label.size() * Sheet.lineHeight(size);
            sheet.require(height
                    + (height + widow <= sheet.nextPageHeight() ? widow : 0f));
            float top = sheet.y();
            for (int line = 0; line < label.size(); line++) {
                sheet.show(label.get(line), x, top - line * Sheet.lineHeight(size) - size,
                        face, size, ink);
            }
            sheet.show(face.showable(row.value()), sheet.right() - value, top - size, face,
                    size, ink);
            sheet.down(height + ROW_GAP);
        }
    }

    /** Returns the type size a row of the totals is set in. */
    private static float size(Row row) {
        return row.due() ? DUE_SIZE : row.muted() ? SMALL_SIZE : TOTAL_SIZE;
    }

    /** Returns the lines the label of a row takes beside its figure. */
    private List<String> label(Row row, Fonts.Face face, float size, float width) {
        return Sheet.wrap(face.showable(row.label()), face, size, width);
    }

    /** Returns how tall a row of the totals is, the air under it and its rule included. */
    private float height(Row row, float width) {
        float size = size(row);
        Fonts.Face face = row.due() ? sheet.fonts().bold() : sheet.fonts().regular();
        float value = face.width(face.showable(row.value()), size);
        return label(row, face, size, width - value - 10f).size() * Sheet.lineHeight(size)
                + ROW_GAP + (row.due() ? 6f : 0f);
    }

    /**
     * Draws the payment block: the terms, the means of payment by name, and the accounts
     * the invoice names, an account identifier that is an IBAN in groups of four.
     *
     * <p>An account is written whole before the next one begins. A reader pays from one
     * account, and an IBAN whose holder and whose bank stand three rows further down under
     * a number in their label is a reader reading indices rather than an invoice.
     *
     * <p>The block is written in parts, and each part is kept on one page where one page
     * could hold it: a bank account parted by a page break is the half of the letter that
     * has to be right. A block that runs onto the next page takes its heading with it,
     * because a page that opens with an IBAN and no word above it is a page that says
     * nothing about what the number is for.
     */
    private void payment() {
        List<List<Field>> parts = new ArrayList<>();
        List<Field> terms = new ArrayList<>();
        // BT-20 is the payment terms of the standard, and the localization of the
        // visualization names it by what a German invoice most often puts in it. Over a
        // sentence about when to pay, the name of the term is what a reader is looking for.
        add(terms, SemanticPath.of("/BT-20"), Word.PAYMENT_TERMS);
        part(parts, terms);
        for (SemanticPath instructions : instances(SemanticPath.root(), "BG-16")) {
            String qualifier = instructionQualifier(instructions);
            List<Field> means = new ArrayList<>();
            SemanticPath path = path(instructions, "BT-81");
            String code = read(path);
            if (!code.isEmpty()) {
                place(path, code);
                String name = codeName("BT-81", code);
                // A row that shows the name of the means is not a row about a code, so it
                // is not labelled as one; the code itself stands under the closing heading.
                means.add(new Field("BT-81",
                        qualifier + (name.equals(code) ? label(path)
                                : Word.CODE_PAYMENT_MEANS.in(language)),
                        name, false));
            }
            part(parts, means);
            for (SemanticPath account : instances(instructions, "BG-17")) {
                List<Field> one = new ArrayList<>();
                account(one, instructions, account);
                part(parts, one);
            }
            part(parts, qualified(collect(instructions), qualifier));
        }
        if (parts.isEmpty()) {
            return;
        }
        drawPayment(told(parts));
    }

    /**
     * Returns what names one of several payment instructions, ending in a middle dot, and
     * the empty string where the document states one instruction.
     *
     * <p>Every label and every caption of the payment block is qualified against the whole
     * block rather than against the instruction it stands under, because that is what a
     * reader sees: a letter that states two credit transfers of one account each carried
     * two rows called <i>IBAN</i> and a code that said which of them it paid into by
     * saying nothing. {@link #groupQualifier(SemanticPath, SemanticPath)} cannot see that
     * from inside an instruction — it names the groups <em>below</em> the prefix it is
     * given, and the accounts do not repeat there — so the instruction is named here.
     *
     * <p>BG-16 is 0..1 in every registry this build carries, so a document that states two
     * payment instructions is one the model refuses and no command of this tool accepts.
     * The qualification is written all the same, because a layout draws the document it is
     * handed rather than deciding what a document may say: it is handed one by a caller of
     * the library as well as by {@code esj render}, and a page that printed two rows called
     * <i>IBAN</i> under one code would be the worse answer to a document that is wrong. The
     * case a conformant document reaches is the other one — several BG-17 accounts under
     * one instruction — and there this returns the empty string and
     * {@link #groupQualifier(SemanticPath, SemanticPath)} numbers the accounts.
     *
     * @param instructions the instruction, index and all
     * @return the qualifier, ending in a middle dot, or the empty string
     */
    private String instructionQualifier(SemanticPath instructions) {
        if (instances(SemanticPath.root(), "BG-16").size() < 2
                || instructions.index().isEmpty()) {
            return "";
        }
        return Word.PAYMENT_INSTRUCTION.in(language) + " "
                + (instructions.index().orElseThrow().value() + 1) + " · ";
    }

    /** Returns fields under a qualifier, which is how a part of the block is told apart. */
    private static List<Field> qualified(List<Field> fields, String qualifier) {
        if (qualifier.isEmpty()) {
            return fields;
        }
        List<Field> named = new ArrayList<>(fields.size());
        for (Field field : fields) {
            named.add(field.renamed(qualifier + field.label()));
        }
        return named;
    }

    /** Adds a part of the payment block, where it has fields. */
    private static void part(List<List<Field>> parts, List<Field> fields) {
        if (!fields.isEmpty()) {
            parts.add(new ArrayList<>(fields));
        }
    }

    /**
     * Returns the parts of the payment block with every label that more than one field of
     * the whole block carries made unambiguous.
     *
     * <p>A label is ambiguous against the block a reader sees rather than against the part
     * it happens to stand in, so the parts are told apart as one list and cut into parts
     * again afterwards.
     *
     * @param parts the parts
     * @return the parts, with their labels made unambiguous
     */
    private List<List<Field>> told(List<List<Field>> parts) {
        List<Field> all = new ArrayList<>();
        parts.forEach(all::addAll);
        List<Field> shown = disambiguate(all);
        List<List<Field>> cut = new ArrayList<>(parts.size());
        int at = 0;
        for (List<Field> part : parts) {
            cut.add(List.copyOf(shown.subList(at, at + part.size())));
            at += part.size();
        }
        return cut;
    }

    /**
     * Draws the payment block: the heading, then every part whole where a page can hold
     * it, with the heading repeated at the top of every page the block runs onto.
     *
     * @param parts the parts of the block
     */
    private void drawPayment(List<List<Field>> parts) {
        PaymentCode code = paymentCode();
        float aside = code == null ? 0f : CODE_SIDE + CODE_GAP;
        float width = sheet.width() - aside;
        float narrow = valueRoom(width);
        float beside = code == null ? 0f : codeHeight(code);
        float whole = 0;
        for (List<Field> part : parts) {
            whole += definitionsHeight(part, width, narrow);
        }
        float first = definitionsHeight(parts.get(0), width, narrow);
        section(Word.PAYMENT,
                follows(Math.max(whole, beside), Math.max(first, beside)));
        sheet.onNewPage(() -> heading(Word.PAYMENT));
        if (code != null) {
            // The section above kept the code with its heading where a page could hold
            // the two. A first part taller than a whole page is the case where it could
            // not, and there the code goes to the top of the next page with the heading
            // rather than over the foot of this one.
            sheet.require(beside);
        }
        int page = sheet.pages();
        float foot = sheet.y() - beside;
        try {
            if (code != null) {
                drawCode(code);
            }
            for (List<Field> part : parts) {
                float height = definitionsHeight(part, width,
                        roomBeside(code, page, foot, narrow, aside));
                if (height + headingHeight() <= sheet.nextPageHeight()) {
                    sheet.require(height);
                }
                // One field at a time, because how far a value may run is a question
                // about where the field lands: the rows that stand beside the code stop
                // short of it, the rows under it and every row of a later page run to the
                // right margin. The columns themselves do not move.
                for (Field field : part) {
                    definitions(List.of(field), sheet.left(), width,
                            roomBeside(code, page, foot, narrow, aside));
                }
            }
        } finally {
            sheet.onNewPage(null);
        }
        // The code is furniture beside the block rather than a row of it, so the cursor
        // knows nothing about it: a block of four rows beside a code of thirty
        // millimetres would let the next section begin over the code. Where the block
        // ended above the code and on the same page, the cursor is taken to its foot.
        if (code != null && sheet.pages() == page && sheet.y() > foot) {
            sheet.down(sheet.y() - foot);
        }
    }

    /**
     * Returns how far a value of the payment block may run where the cursor stands now.
     *
     * <p>The code is drawn at the right of the block and is as tall as it is: a value
     * beside it stops short of it, and a value under it — or on a page the code is not on
     * — runs on to the right margin, which is what an account holder of a long name needs
     * in order to stay on one line. The label column and the place the values begin at
     * are the same in both, so the block reads as one block.
     *
     * @param code   the code drawn beside the block, or {@code null} where none is
     * @param page   the page the code was drawn on
     * @param foot   the y the code ends at on that page
     * @param narrow the room a value has beside the code
     * @param aside  what the code and the air before it take
     * @return the room a value has here
     */
    private float roomBeside(PaymentCode code, int page, float foot, float narrow,
                             float aside) {
        if (code != null && sheet.pages() == page && sheet.y() > foot) {
            return narrow;
        }
        return narrow + aside;
    }

    /**
     * Returns the code of the credit transfer this document states, or {@code null} where
     * the letter draws none.
     *
     * <p>An element the guideline holds to a length is written to that length, and the
     * closing heading says so: what the code carries is then a little less than what the
     * letter prints, and a reader who compares the two is told why rather than left to
     * wonder.
     */
    private PaymentCode paymentCode() {
        if (!letter.paymentCode()) {
            return null;
        }
        PaymentCode code = PaymentCode.of(document).orElse(null);
        if (code != null) {
            for (PaymentCode.Remark remark : code.remarks()) {
                remarks.add(new Field("", Word.PAYMENT_CODE.in(language),
                        said(remark).in(language), false));
            }
        }
        return code;
    }

    /** Returns what the closing heading says about a difference of the code. */
    private static Word said(PaymentCode.Remark remark) {
        return switch (remark) {
            case BENEFICIARY_CUT -> Word.PAYMENT_CODE_NAME_CUT;
            case REMITTANCE_REPLACED -> Word.PAYMENT_CODE_REMITTANCE_REPLACED;
            case BIC_LEFT_OUT -> Word.PAYMENT_CODE_BIC_LEFT_OUT;
        };
    }

    /**
     * Returns the caption under the code: the name a reader knows it by, and before it
     * the name and number of the account it pays into where the document states more
     * than one.
     *
     * <p>A letter that prints three accounts and one code has to say which of them the
     * code is for, and it says it in the words the block above already numbers them in.
     * A document with one account is not told a number, because a number that is always
     * one tells a reader nothing.
     *
     * @param code the code
     * @return the caption
     */
    private String codeCaption(PaymentCode code) {
        return instructionQualifier(code.instruction())
                + groupQualifier(code.instruction(), path(code.account(), "BT-84"))
                + Word.PAYMENT_CODE.in(language);
    }

    /** Returns the lines the caption of a code takes under it. */
    private List<String> captionLines(PaymentCode code) {
        Fonts.Face face = sheet.fonts().regular();
        return Sheet.wrap(face.showable(codeCaption(code)), face, FOOT_SIZE, CODE_SIDE);
    }

    /**
     * Returns how tall a code stands with its caption under it.
     *
     * @param code the code
     * @return the height, in points
     */
    private float codeHeight(PaymentCode code) {
        return CODE_SIDE + CODE_CAPTION_GAP
                + captionLines(code).size() * Sheet.lineHeight(FOOT_SIZE);
    }

    /**
     * Draws the payment code at the right of the payment block, with its caption under
     * it, and leaves the cursor where it found it: the account data is set in the width
     * beside the code and runs on under it at the full width of the page.
     *
     * <p>The modules are filled squares of the page rather than a picture. The colour is
     * black whatever a template's palette says, because what has to be read here is not a
     * heading but a symbol, and a scanner reads it off the contrast.
     *
     * @param code the code
     */
    private void drawCode(PaymentCode code) {
        float left = sheet.right() - CODE_SIDE;
        float top = sheet.y();
        float module = CODE_SIDE / (code.modules() + 2f * PaymentCode.QUIET_ZONE);
        float quiet = module * PaymentCode.QUIET_ZONE;
        sheet.fillGrid(left + quiet, top - quiet, module, code.cells(), Ink.BLACK);
        Fonts.Face face = sheet.fonts().regular();
        float baseline = top - CODE_SIDE - CODE_CAPTION_GAP - FOOT_SIZE;
        for (String line : captionLines(code)) {
            sheet.show(line, left + (CODE_SIDE - face.width(line, FOOT_SIZE)) / 2f,
                    baseline, face, FOOT_SIZE, palette.muted());
            baseline -= Sheet.lineHeight(FOOT_SIZE);
        }
    }

    /**
     * Adds one account of a payment instruction to the payment block: the identifier, the
     * bank and the holder, in the order a reader copies them, and then whatever else that
     * account carries.
     *
     * <p>The three are labelled in the words a letter uses rather than in the words the
     * register uses. <i>Payment account identifier</i> beside a figure written in groups of
     * four is the register describing an IBAN to somebody who is looking at one.
     */
    private void account(List<Field> fields, SemanticPath instructions, SemanticPath account) {
        String qualifier = instructionQualifier(instructions)
                + groupQualifier(instructions, path(account, "BT-84"));
        SemanticPath identifier = path(account, "BT-84");
        String value = read(identifier);
        if (!value.isEmpty()) {
            place(identifier, value);
            fields.add(new Field("BT-84",
                    qualifier + (isIban(value) ? Word.ACCOUNT_IBAN.in(language)
                            : label(identifier)),
                    grouped(value), false));
        }
        named(fields, account, "BT-86", qualifier, Word.ACCOUNT_BIC);
        named(fields, account, "BT-85", qualifier, Word.ACCOUNT_HOLDER);
        fields.addAll(qualified(collect(account), qualifier));
    }

    /** Adds a term of an account under the word a letter calls it by, and places it. */
    private void named(List<Field> fields, SemanticPath account, String term,
                       String qualifier, Word word) {
        SemanticPath path = path(account, term);
        String value = read(path);
        if (value.isEmpty()) {
            return;
        }
        place(path, value);
        fields.add(new Field(term, qualifier + word.in(language), value, false));
    }

    /**
     * Returns an account identifier in groups of four where it is an IBAN, and as it
     * stands where it is anything else.
     *
     * <p>An IBAN is written in groups of four on paper and nowhere else, which is why the
     * grouping happens here and not in {@link Formats}: it is how a reader reads one back
     * to somebody on the telephone. The shape is the one an IBAN has — two letters, two
     * digits and at most thirty more letters or digits — so a domestic account number is
     * left alone.
     *
     * @param value the account identifier
     * @return the text to show
     */
    static String grouped(String value) {
        if (!isIban(value)) {
            return value;
        }
        StringBuilder text = new StringBuilder(value.length() + value.length() / IBAN_GROUP);
        for (int i = 0; i < value.length(); i++) {
            if (i > 0 && i % IBAN_GROUP == 0) {
                text.append(' ');
            }
            text.append(value.charAt(i));
        }
        return text.toString();
    }

    /** Tells whether an account identifier is written the way an IBAN is written. */
    private static boolean isIban(String value) {
        if (value.length() < 5 || value.length() > 34) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = i < 2 ? c >= 'A' && c <= 'Z'
                    : i < 4 ? c >= '0' && c <= '9'
                    : c >= '0' && c <= '9' || c >= 'A' && c <= 'Z';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- the foot of the letter

    /**
     * Collects the sender's own business details into the three columns of the letter
     * foot: who and where, how to reach them, and what the registers say.
     *
     * @return the columns, empty where the seller states nothing
     */
    private List<List<String>> foot() {
        SemanticPath seller = SemanticPath.group("/BG-4");
        SemanticPath address = child(seller, "BG-5");
        SemanticPath contact = child(seller, "BG-6");
        List<String> who = new ArrayList<>();
        for (String term : new String[] {"BT-27", "BT-28"}) {
            plain(who, path(seller, term));
        }
        for (String term : new String[] {"BT-35", "BT-36", "BT-162"}) {
            plain(who, path(address, term));
        }
        SemanticPath post = path(address, "BT-38");
        SemanticPath city = path(address, "BT-37");
        String town = join(read(post), read(city));
        if (!town.isEmpty()) {
            place(post, read(post));
            place(city, read(city));
            who.add(town);
        }
        plain(who, path(address, "BT-39"));
        SemanticPath country = path(address, "BT-40");
        String named = codeName("BT-40", read(country));
        if (!named.isEmpty()) {
            place(country, read(country));
            who.add(named);
        }
        List<String> reach = new ArrayList<>();
        labelled(reach, path(contact, "BT-41"));
        // A telephone number, an e-mail address and an electronic address say what they
        // are, and a label in front of one is a word taking the width of the value.
        for (String term : new String[] {"BT-42", "BT-43"}) {
            unlabelled(reach, path(contact, term));
        }
        unlabelled(reach, path(seller, "BT-34"));
        List<String> registers = new ArrayList<>();
        for (String term : new String[] {"BT-29", "BT-30", "BT-31", "BT-32"}) {
            labelled(registers, path(seller, term));
        }
        // What the sender has to state by law is a sentence they wrote themselves, and a
        // label in front of it would be this layout introducing it.
        unlabelled(registers, path(seller, "BT-33"));
        rest(registers, seller);
        List<List<String>> columns = new ArrayList<>();
        for (List<String> column : List.of(who, reach, registers)) {
            // A column nothing fills is no column: the ones after it move left, so a sender
            // who states no contact gets two columns rather than a gap in the middle.
            if (!column.isEmpty()) {
                columns.add(column);
            }
        }
        return columns;
    }

    /**
     * Adds everything else the seller states to the last column of the foot, under its
     * label and in the short form the foot writes.
     *
     * <p>A seller identifier is one of them: the standard lets a seller state several, so
     * the document carries them under an index and the foot cannot ask for them by name.
     * They are written here, in the canonical order of the document, with whatever else of
     * the seller no block of the letter had a place for.
     *
     * @param column the column of the foot
     * @param seller the seller group
     */
    private void rest(List<String> column, SemanticPath seller) {
        for (SemanticPath path : List.copyOf(document.values().keySet())) {
            if (path.startsWith(seller) && !path.equals(seller)) {
                footLine(column, path, qualifiedLabel(seller, path) + ": ");
            }
        }
    }

    /** Adds the value at a path to a column as it stands, and marks it placed. */
    private void plain(List<String> column, SemanticPath path) {
        String value = read(path);
        if (!value.isEmpty()) {
            place(path, value);
            column.add(value);
        }
    }

    /**
     * Adds the value at a path to a column under its label, and marks it placed.
     *
     * <p>A value the head of the letter already carries is left out: the VAT identifier
     * and the tax number stand in the reference line, or in the information block where
     * the template asked for that one, and a foot that repeated them would print the same
     * fact twice on one page.
     */
    private void labelled(List<String> column, SemanticPath path) {
        footLine(column, path, label(path) + ": ");
    }

    /**
     * Adds the value at a path to a column with nothing in front of it, and marks it
     * placed. It is for the values of a letter foot that say what they are: a telephone
     * number, an electronic address, the line of legal information a sender writes there.
     */
    private void unlabelled(List<String> column, SemanticPath path) {
        footLine(column, path, "");
    }

    /**
     * Adds one line of the letter foot: what stands before the value, and the value in the
     * short form the foot writes.
     *
     * <p>Three columns of a letter foot are the narrowest text on the page, and the labels
     * of the register are written for a block of definitions rather than for them. So the
     * value comes first and an identification scheme is written as the code it is —
     * {@code 4399901234567 (GLN)} — where the details section spells the component out
     * under its own name. Nothing is dropped either way: the code is on the page, and what
     * the register calls the scheme stands in the section that has the width for it.
     *
     * @param column the column of the foot
     * @param path   the path of the value
     * @param before what stands in front of the value, ending in its own punctuation
     */
    private void footLine(List<String> column, SemanticPath path, String before) {
        Optional<SemanticValue> value = document.value(path);
        if (value.isEmpty() || placed.contains(path)) {
            return;
        }
        String shown = shown(path, value.get());
        if (shown.isEmpty()) {
            return;
        }
        String scheme = value.get().scheme();
        place(path, shown);
        column.add(before + shown + (scheme == null ? "" : " (" + scheme + ")"));
    }

    /**
     * Reserves the foot of the first page for the sender's own business details, and
     * writes them there.
     *
     * <p>They are printed matter rather than a block of the letter. A business letter
     * carries them in the foot of its first page — that is where a reader looks for them,
     * and a letter that runs over pages would otherwise carry them on the page a reader
     * reads last. So they are measured before the page is filled, the page reserves what
     * they need at its foot ({@link Sheet#reserveAtFoot(float)}), and the text of the
     * letter stops above them. Nothing of the letter is ever written over them, and a
     * sender who states more of them gets a shorter first page rather than a line of the
     * invoice on its own foot.
     *
     * <p>The pages after the first carry the page footer alone, as a letter does.
     *
     * <p>A foot the page cannot give the room for is not a foot. The details of a sender
     * that states pages of legal information would stand over the letter rather than under
     * it, so where they need more than {@link Sheet#roomAtFoot()} they are written under
     * the closing heading instead, exactly as for a letterhead that prints them already.
     * Nothing is dropped either way. Measuring them is the only way to know how tall they
     * are, and collecting them is how they are measured, so what that marked as shown on
     * the page is given back where the page does not show it.
     */
    private void footOfTheFirstPage() {
        if (letter.sellerDetails() != LetterOptions.SellerDetails.FOOTER) {
            return;
        }
        Set<SemanticPath> before = new LinkedHashSet<>(placed);
        Map<String, Field> listed = new TreeMap<>(codes);
        List<List<String>> columns = foot();
        float height = footHeight(columns);
        if (height > 0 && height <= sheet.roomAtFoot()) {
            drawFoot(columns, sheet.reserveAtFoot(height), height);
            return;
        }
        placed.clear();
        placed.addAll(before);
        codes.clear();
        codes.putAll(listed);
    }

    /**
     * Returns how tall the foot of the letter turns out: the tallest of its columns, the
     * rule over them, the air under that rule and the air over it that keeps the text of
     * the page off it.
     *
     * @param columns the columns, as {@link #foot()} collected them
     * @return the height, or zero where there is no foot to write
     */
    private float footHeight(List<List<String>> columns) {
        if (columns.isEmpty()) {
            return 0;
        }
        float width = footColumnWidth(columns.size());
        Fonts.Face face = sheet.fonts().regular();
        float height = 0;
        for (List<String> column : columns) {
            float used = 0;
            for (String line : column) {
                used += Sheet.measure(line, face, FOOT_SIZE, width);
            }
            height = Math.max(height, used);
        }
        return height == 0 ? 0
                : height + Sheet.RULE_HEIGHT + FOOT_BAND_GAP + FOOT_AIR;
    }

    /** Returns how wide one column of the letter foot is. */
    private float footColumnWidth(int columns) {
        return (sheet.width() - (columns - 1) * FOOT_GAP) / columns;
    }

    /**
     * Draws the columns of the letter foot in the band reserved for them, under a rule.
     *
     * <p>They are set in the text colour and not in the grey of a label, and a size a
     * reader reads: the foot of a letter carries the address a payment is sent to and the
     * register a company is entered in, and those are read off paper, off a scan and by
     * somebody who is not looking for them.
     *
     * @param columns the columns, as {@link #foot()} collected them
     * @param floor   the y the band stands on
     * @param height  how tall the band is
     */
    private void drawFoot(List<List<String>> columns, float floor, float height) {
        float width = footColumnWidth(columns.size());
        Fonts.Face face = sheet.fonts().regular();
        float rule = floor + height - FOOT_AIR;
        sheet.rule(sheet.left(), sheet.right(), rule, palette.rule());
        float top = rule - FOOT_BAND_GAP;
        for (int i = 0; i < columns.size(); i++) {
            float x = sheet.left() + i * (width + FOOT_GAP);
            float y = top;
            for (String line : columns.get(i)) {
                y -= sheet.blockAt(line, x, y, width, face, FOOT_SIZE, palette.text());
            }
        }
    }

    /**
     * The closing section: the codes whose names the page wrote, each once, and then every
     * value the letter had no place of its own for, with its label and its path.
     */
    private void furtherDetails() {
        List<Field> rest = remainingFields();
        List<Field> written = List.copyOf(codes.values());
        List<Field> said = List.copyOf(remarks);
        if (rest.isEmpty() && written.isEmpty() && said.isEmpty()) {
            return;
        }
        List<Field> shown = disambiguate(rest);
        float whole = definitionsHeight(written, sheet.width())
                + definitionsHeight(said, sheet.width())
                + definitionsHeight(shown, sheet.width());
        section(Word.FURTHER_DETAILS, follows(whole, whole));
        if (!written.isEmpty()) {
            definitions(written, sheet.left(), sheet.width());
        }
        if (!said.isEmpty()) {
            definitions(said, sheet.left(), sheet.width());
        }
        if (!shown.isEmpty()) {
            definitions(shown, sheet.left(), sheet.width());
        }
    }

    /**
     * Returns how much of a section has to stand on the page its heading stands on.
     *
     * <p>A section that a page could hold whole begins on the page it fits on, or on a
     * fresh one — two rows of it at the foot of a page and the rest overleaf is a section
     * broken for nothing. A section taller than a page has to break somewhere, and then
     * what is asked for beside the heading is as much of it as can be promised: the part
     * the caller names, or the two field lines that keep a heading from standing alone.
     *
     * @param whole how tall the whole section is, its heading not counted
     * @param least how much of it has to follow the heading where the whole cannot
     * @return the height to require beside the heading
     */
    private float follows(float whole, float least) {
        if (whole + headingHeight() <= sheet.nextPageHeight()) {
            return whole;
        }
        return least + headingHeight() <= sheet.nextPageHeight() ? least
                : Sheet.lineHeight(FIELD_SIZE) * 2;
    }

    /**
     * Draws labels and the values beside them at a place of the page, without touching the
     * cursor and without a page break, which is what a block beside the address field is.
     *
     * @param fields     the fields
     * @param x          where the block starts
     * @param top        the top of the block
     * @param width      how wide it is
     * @param labelShare how much of that width the label takes
     * @param size       the type size
     * @return how tall the block turned out
     */
    private float fieldsAt(List<Field> fields, float x, float top, float width,
                           float labelShare, float size) {
        float labelWidth = width * labelShare;
        float valueX = x + labelWidth + LABEL_GAP;
        float valueWidth = width - labelWidth - LABEL_GAP;
        float lineHeight = Sheet.lineHeight(size);
        Fonts.Face face = sheet.fonts().regular();
        float drawn = 0;
        for (Field field : fields) {
            List<String> label =
                    Sheet.wrap(face.showable(field.label()), face, size, labelWidth);
            List<String> value =
                    Sheet.wrap(face.showable(field.value()), face, size, valueWidth);
            for (int i = 0; i < label.size(); i++) {
                sheet.show(label.get(i), x, top - drawn - i * lineHeight - size, face, size,
                        palette.muted());
            }
            for (int i = 0; i < value.size(); i++) {
                sheet.show(value.get(i), valueX, top - drawn - i * lineHeight - size, face,
                        size, palette.text());
            }
            drawn += Math.max(label.size(), value.size()) * lineHeight + 1f;
        }
        return drawn;
    }

    /**
     * One cell of the reference line.
     *
     * @param field what stands in it
     * @param span  how many columns it takes
     */
    private record Cell(Field field, int span) {
    }

    /**
     * One row of the block of totals.
     *
     * @param label what the row is called
     * @param value the figure, with the currency of the document behind it
     * @param due   whether this is the amount due, which closes the block
     * @param muted whether the row is a remark under the row above it
     */
    private record Row(String label, String value, boolean due, boolean muted) {
    }
}
