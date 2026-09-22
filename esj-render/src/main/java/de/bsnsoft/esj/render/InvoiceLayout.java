package de.bsnsoft.esj.render;

import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.PathSegment;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.TermKind;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the two page layouts of this module have in common: reading a value out of the
 * document, writing it down for a reader, labelling it, and keeping track of which values
 * have reached a page.
 *
 * <p>The last of those is the one that matters. A layout takes the values it has a place
 * for and marks them {@linkplain #placed placed}; whatever is left when the layout is done
 * is printed under a closing heading with its label and its semantic path. A value of the
 * document that reached a layout of this module is therefore in the rendering — that is a
 * test of this module rather than an intention, and it is the same statement for the
 * generic layout and for the letter layout.
 *
 * <p>The rendering is net and derives nothing. It shows the net line amount and the net
 * unit price of a line, the VAT per category as the document states it and the totals as
 * the document states them; it computes no gross line amount, no gross unit price and no
 * figure at all, and it writes nothing into a business term. BT-114 is the rounding amount
 * of EN 16931-1 and is labelled as that, never used as a balancing field. A value of an
 * extension is shown with the label of its own term and is never put in the place of a
 * core term.
 */
abstract class InvoiceLayout {

    /** The type size of a section heading. */
    static final float SECTION_SIZE = 10f;

    /** The type size of a label and of the value beside it. */
    static final float FIELD_SIZE = 8.4f;

    /** The air between two sections. */
    static final float SECTION_GAP = 11f;

    /** How much of the width of a field block the label takes. */
    static final float LABEL_SHARE = 0.42f;

    /** The gap between a label and the value beside it. */
    static final float LABEL_GAP = 6f;

    /** The air between a section heading and the rule under it. */
    static final float HEADING_RULE_GAP = 2f;

    /** The air between that rule and the first thing under the heading. */
    static final float HEADING_GAP_BELOW = 4f;

    /**
     * The words of a group name that are abbreviations rather than words, and keep their
     * capitals where the rest of the name loses them.
     */
    private static final Set<String> ABBREVIATIONS = Set.of("VAT");

    final SemanticDocument document;
    final Registry registry;
    final RenderLanguage language;
    final Labels labels;
    final Sheet sheet;
    final Palette palette;
    final List<Placement> placements;
    final Set<SemanticPath> placed = new LinkedHashSet<>();
    final String currency;

    /** The code of the VAT accounting currency, BT-6, empty where there is none. */
    final String accountingCurrency;

    /** How many instances of every repeatable group the document carries, counted once. */
    private Map<SemanticPath, Integer> groupCounts;

    /**
     * Prepares a layout of a document.
     *
     * @param document the document
     * @param registry the registry the types and the English labels come from
     * @param language the language of the rendering
     * @param sheet    the sheet to draw on
     * @param template the branded template, or {@code null} for a rendering without one
     */
    InvoiceLayout(SemanticDocument document, Registry registry, RenderLanguage language,
                  Sheet sheet, RenderTemplate template) {
        this.document = document;
        this.registry = registry;
        this.language = language;
        this.labels = Labels.of(registry, language);
        this.sheet = sheet;
        this.palette = sheet.palette();
        this.placements = template == null ? List.of() : template.placements();
        this.currency = document.value(SemanticPath.of("/BT-5"))
                .map(SemanticValue::content).orElse("");
        this.accountingCurrency = document.value(SemanticPath.of("/BT-6"))
                .map(SemanticValue::content).orElse("");
    }

    /**
     * Returns the currency a figure of the totals is stated in.
     *
     * <p>All but one of them are stated in the currency of the document, BT-5. BT-111 is
     * the VAT total in the accounting currency BT-6 — the figure a seller who invoices in
     * one currency and accounts for the tax in another has to report — and writing it
     * under the code of the document would turn a second currency into a second, wrong
     * figure. Where the document names no accounting currency the figure is written
     * without a code rather than under the wrong one.
     *
     * @param term the identifier of the business term
     * @return the currency code to write behind the figure, empty for none
     */
    String currencyOf(String term) {
        return "BT-111".equals(term) ? accountingCurrency : currency;
    }

    /** Draws the whole document and writes the page furniture. */
    abstract void draw();

    /** Returns the invoice number, which stands in the footer of every page. */
    String identity() {
        return document.value(SemanticPath.of("/BT-1"))
                .map(SemanticValue::content).orElse("");
    }

    /**
     * Claims, before anything is drawn, the values the template will put in a place of
     * its own.
     *
     * <p>The sections of a layout take what nobody has taken yet, in the order they are
     * drawn in, and the totals come late. A term a template places among them would
     * otherwise have been printed by an earlier section as an ordinary value, and would
     * then stand on the page twice. What is claimed here is exactly what
     * {@link #placedCell(SemanticPath, Placement)} will print: the paths the document
     * actually carries, and no others.
     */
    void reserve() {
        for (Placement placement : placements) {
            if (placement.position().isLine()) {
                for (SemanticPath line : instances(SemanticPath.root(), "BG-25")) {
                    reserve(placedPath(line, placement));
                }
            } else {
                reserve(placedPath(SemanticPath.root(), placement));
            }
        }
    }

    private void reserve(SemanticPath path) {
        if (document.value(path).isPresent()) {
            placed.add(path);
        }
    }

    // ---------------------------------------------------------------- blocks

    /** Draws a heading and a block of definitions under it, where the block has content. */
    void stacked(Word heading, List<Field> fields) {
        if (fields.isEmpty()) {
            return;
        }
        section(heading);
        definitions(disambiguate(fields), sheet.left(), sheet.width());
    }

    /** Draws a heading and, under it, everything a group carries that is not placed yet. */
    void collectedSection(Word heading, String groupId) {
        List<Field> fields = new ArrayList<>();
        for (SemanticPath instance : instances(SemanticPath.root(), groupId)) {
            fields.addAll(collect(instance));
        }
        stacked(heading, fields);
    }

    /**
     * The closing section: every value the layout above has no place of its own for,
     * printed with its label and the path that addresses it, and the owner of every
     * extension subtree the document carries.
     *
     * @param heading the heading of the section
     */
    void otherTerms(Word heading) {
        stacked(heading, remainingFields());
    }

    /**
     * Returns everything the layout has not placed, as labels and values, and marks it
     * placed. This is what makes the statement of this module true: a value nobody had a
     * place for is still a value on a page, under the path that addresses it.
     *
     * @return the fields
     */
    List<Field> remainingFields() {
        List<Field> fields = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            if (placed.contains(entry.getKey())) {
                continue;
            }
            // An attachment is named and measured here as it is where a layout has a
            // section for it: a few hundred kilobytes of base64 on paper help nobody, and
            // a closing section that printed them would be hundreds of pages long.
            boolean binary = datatype(entry.getKey().term(), SemanticType.TEXT)
                    == SemanticType.BINARY_OBJECT;
            fields.add(new Field(entry.getKey().term(),
                    qualifiedLabel(SemanticPath.root(), entry.getKey())
                            + " (" + entry.getKey() + ")",
                    binary ? attachment(entry.getValue())
                            : withComponents(entry.getKey(), entry.getValue()),
                    false));
            placed.add(entry.getKey());
        }
        for (Map.Entry<String, ExtensionValue> extension : document.extensions().entrySet()) {
            fields.add(new Field(extension.getKey(), extension.getKey(),
                    Word.EXTENSION_DATA.in(language), false));
        }
        return fields;
    }

    void section(Word heading) {
        section(heading, Sheet.lineHeight(FIELD_SIZE) * 2);
    }

    /**
     * Draws a section heading, and keeps it with the beginning of what follows it.
     *
     * <p>A heading alone at the foot of a page is a heading of nothing. The block under
     * it says how much of itself has to fit beside the heading — two field lines for a
     * block of definitions — and the two are required together, so either both stand here
     * or both start the next page. A table says it differently, in
     * {@link #tableUnder(Word, Table)}: it alone knows how tall its first row is.
     *
     * @param heading the heading
     * @param follows how much of what comes after it must fit on the same page
     */
    void section(Word heading, float follows) {
        sheet.down(SECTION_GAP);
        sheet.require(headingHeight() + follows);
        heading(heading);
    }

    /**
     * Announces a table under a section heading. Neither is drawn here.
     *
     * <p>A table cannot say beforehand how much of itself has to fit beside its heading,
     * because that is the height of a first row it has not seen yet — one line or three,
     * with a detail line under it or without. So it is handed the heading instead and
     * draws it when it draws that row, and the heading, the column header and the row
     * begin the same page or all three begin the next one.
     *
     * @param heading the heading
     * @param table   the table that stands under it
     */
    void tableUnder(Word heading, Table table) {
        sheet.down(SECTION_GAP);
        table.begin(() -> heading(heading), headingHeight());
    }

    /** Writes a section heading at the cursor: the words, and a rule under them. */
    void heading(Word heading) {
        sheet.block(heading.in(language), sheet.left(), sheet.width(),
                sheet.fonts().bold(), SECTION_SIZE, palette.heading());
        sheet.down(HEADING_RULE_GAP);
        sheet.rule();
        sheet.down(HEADING_GAP_BELOW);
    }

    /**
     * Returns how much room a section heading takes: one line of it, the air around the
     * rule and the rule itself. It is what {@link #heading(Word)} spends, to the point,
     * because a heading that is reserved short is a heading whose first row lands on the
     * next page.
     */
    static float headingHeight() {
        return Sheet.lineHeight(SECTION_SIZE) + HEADING_RULE_GAP + Sheet.RULE_HEIGHT
                + HEADING_GAP_BELOW;
    }

    void definitions(List<Field> fields, float x, float width) {
        layoutDefinitions(fields, x, width, null, valueRoom(width));
    }

    /**
     * Draws a block of definitions whose values are held to a room of their own.
     *
     * <p>The label column and the place the values begin at stay where the width puts
     * them; only how far a value may run before it wraps is the caller's. That is what a
     * block with furniture beside part of it needs — the rows beside it are set short and
     * the rows under it are set long, and the column of values does not move between the
     * two.
     *
     * @param fields    the fields
     * @param x         the left edge of the block
     * @param width     the width the columns are laid out in
     * @param valueRoom how wide a value may be before it wraps
     */
    void definitions(List<Field> fields, float x, float width, float valueRoom) {
        layoutDefinitions(fields, x, width, null, valueRoom);
    }

    void definitionsAt(List<Field> fields, float x, float top, float width) {
        layoutDefinitions(fields, x, width, top, valueRoom(width));
    }

    /**
     * Returns how wide a value of a block of definitions is, where nothing stands beside
     * it: what the width leaves over once the label column and the gap are taken.
     *
     * @param width the width of the block
     * @return the room a value has
     */
    static float valueRoom(float width) {
        return width - width * LABEL_SHARE - LABEL_GAP;
    }

    float definitionsHeight(List<Field> fields, float width) {
        return definitionsHeight(fields, width, valueRoom(width));
    }

    /**
     * Returns how tall a block of definitions is whose values are held to a room.
     *
     * @param fields    the fields
     * @param width     the width the columns are laid out in
     * @param valueRoom how wide a value may be before it wraps
     * @return the height, in points
     */
    float definitionsHeight(List<Field> fields, float width, float valueRoom) {
        float labelWidth = width * LABEL_SHARE;
        float height = 0;
        Fonts.Face face = sheet.fonts().regular();
        for (Field field : fields) {
            height += Math.max(Sheet.measure(field.label(), face, FIELD_SIZE, labelWidth),
                    Sheet.measure(field.value(), face, FIELD_SIZE, valueRoom)) + 1.5f;
        }
        return height;
    }

    /**
     * Draws a block of labels and the values beside them. With a {@code top} it draws
     * there and never breaks the page, which is what two columns beside each other need;
     * without one it draws at the cursor and continues on the next page where it has to.
     *
     * <p>A label is written whole before the value beside it begins, and both before the
     * next field starts. That is invisible on the page and load bearing off it: a text
     * extractor reads a PDF in the order its content stream was written, and a value
     * wrapped over three lines has to come back as one run of text rather than with its
     * own label threaded through it.
     */
    float layoutDefinitions(List<Field> fields, float x, float width, Float top,
                            float valueWidth) {
        float labelWidth = width * LABEL_SHARE;
        float valueX = x + labelWidth + LABEL_GAP;
        float lineHeight = Sheet.lineHeight(FIELD_SIZE);
        Fonts.Face face = sheet.fonts().regular();
        float drawn = 0;
        for (Field field : fields) {
            Fonts.Face values = field.bold() ? sheet.fonts().bold() : face;
            List<String> labelLines =
                    Sheet.wrap(face.showable(field.label()), face, FIELD_SIZE, labelWidth);
            List<String> valueLines =
                    Sheet.wrap(values.showable(field.value()), values, FIELD_SIZE, valueWidth);
            float height = Math.max(labelLines.size(), valueLines.size()) * lineHeight;
            if (top == null && height > sheet.usableHeight()) {
                // A field taller than a whole page cannot stand beside its label, so the
                // two are stacked and each of them flows over as many pages as it needs.
                sheet.block(field.label(), x, width, face, FIELD_SIZE, palette.muted());
                sheet.block(field.value(), x, width, values, FIELD_SIZE, palette.text());
                sheet.down(1.5f);
                continue;
            }
            float fieldTop;
            if (top == null) {
                sheet.require(height);
                fieldTop = sheet.y();
            } else {
                fieldTop = top - drawn;
            }
            for (int i = 0; i < labelLines.size(); i++) {
                sheet.show(labelLines.get(i), x, fieldTop - i * lineHeight - FIELD_SIZE,
                        face, FIELD_SIZE, palette.muted());
            }
            for (int i = 0; i < valueLines.size(); i++) {
                sheet.show(valueLines.get(i), valueX, fieldTop - i * lineHeight - FIELD_SIZE,
                        values, FIELD_SIZE, palette.text());
            }
            if (top == null) {
                sheet.down(height + 1.5f);
            }
            drawn += height + 1.5f;
        }
        return drawn;
    }

    // ---------------------------------------------------------------- values

    /**
     * Returns every value under a group instance that is not placed yet, as labels and
     * values, in canonical path order, and marks them placed. The label of a value that
     * sits in a group below the instance carries the names of the groups between the two,
     * so that an allowance of a line reads as an allowance of that line.
     *
     * @param prefix the group instance
     * @return the fields
     */
    List<Field> collect(SemanticPath prefix) {
        List<Field> fields = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            SemanticPath path = entry.getKey();
            if (!path.startsWith(prefix) || path.equals(prefix) || placed.contains(path)) {
                continue;
            }
            placed.add(path);
            fields.add(new Field(path.term(), qualifiedLabel(prefix, path),
                    withComponents(path, entry.getValue()),
                    "BT-115".equals(path.term())));
        }
        return fields;
    }

    void add(List<Field> fields, SemanticPath path) {
        document.value(path).ifPresent(value -> {
            if (placed.add(path)) {
                fields.add(new Field(path.term(), label(path),
                        withComponents(path, value), false));
            }
        });
    }

    /** Returns the value of a term of a group instance, formatted, and marks it placed. */
    String cell(SemanticPath prefix, String... terms) {
        SemanticPath path = path(prefix, terms);
        String shown = read(path);
        if (!shown.isEmpty()) {
            placed.add(path);
        }
        return shown;
    }

    /** Returns the value at a path, formatted, without marking it placed. */
    String read(SemanticPath path) {
        return document.value(path).map(value -> withComponents(path, value)).orElse("");
    }

    /** Returns the path of a term below a prefix, term by term. */
    static SemanticPath path(SemanticPath prefix, String... terms) {
        SemanticPath path = prefix;
        for (String term : terms) {
            path = child(path, term);
        }
        return path;
    }

    /** Marks a path placed where its value is one the rendering shows. */
    void place(SemanticPath path, String shown) {
        if (!shown.isEmpty()) {
            placed.add(path);
        }
    }

    String attachment(SemanticValue value) {
        List<String> parts = new ArrayList<>();
        if (value.filename() != null) {
            parts.add(value.filename());
        }
        if (value.mimeCode() != null) {
            parts.add(value.mimeCode());
        }
        parts.add(Word.SIZE.in(language) + " " + Formats.bytes(size(value), language));
        parts.add(Word.ATTACHMENT_NOT_SHOWN.in(language));
        return String.join(", ", parts);
    }

    private static long size(SemanticValue value) {
        try {
            return value.asBytes().length;
        } catch (RuntimeException e) {
            return value.content().length();
        }
    }

    String withComponents(SemanticPath path, SemanticValue value) {
        StringBuilder text = new StringBuilder(shown(path, value));
        for (Component.Role role : Component.Role.values()) {
            String component = component(value, role);
            if (component != null) {
                text.append(" (").append(labels.ofComponent(path.term(), role))
                        .append(": ").append(component).append(')');
            }
        }
        return text.toString();
    }

    private static String component(SemanticValue value, Component.Role role) {
        return switch (role) {
            case SCHEME -> value.scheme();
            case SCHEME_VERSION -> value.schemeVersion();
            case MIME_CODE -> value.mimeCode();
            case FILENAME -> value.filename();
        };
    }

    String shown(SemanticPath path, SemanticValue value) {
        return formatted(datatype(path.term(), SemanticType.TEXT), value.content());
    }

    /**
     * Returns the form a value of a semantic data type takes on the page.
     *
     * <p>Every value a layout writes goes through here, the ones a template gave a place
     * to included, so a layout that writes a type differently writes it differently
     * everywhere. The generic layout writes what {@link Formats} writes; the letter
     * layout differs in one type, and says so there.
     *
     * @param type    the semantic data type of the term
     * @param content the content of the value, as the document spells it
     * @return the text to show
     */
    String formatted(SemanticType type, String content) {
        return Formats.value(type, content, language);
    }

    /**
     * Returns the semantic data type of a term, or a fallback.
     *
     * <p>A term the registry of this rendering does not know is not an error here. A
     * document may carry a term of an extension this build has no registry for — it was
     * read as ESJ, and a reader does not need a registry — and such a value is rendered
     * like every other one, written down as the fallback says. The registry is asked
     * through {@link Registry#term(String)} rather than through its typed accessors for
     * exactly that reason: those refuse a term they do not know.
     *
     * @param termId   the identifier of the term
     * @param fallback the type to write the value down with where nothing knows the term
     * @return the type
     */
    SemanticType datatype(String termId, SemanticType fallback) {
        return registry.term(termId).flatMap(Term::datatype).orElse(fallback);
    }

    /** Returns the code list a term is written against, or {@code null} for none. */
    String codeList(String termId) {
        return registry.term(termId).flatMap(Term::codeList).orElse(null);
    }

    /** Joins two values with a space, dropping the ones that are not there. */
    static String join(String first, String second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return first + " " + second;
    }

    String money(Word column) {
        return currency.isEmpty() ? column.in(language)
                : column.in(language) + " (" + currency + ")";
    }

    /** Returns the lines a block of fields makes under a row of a table. */
    static List<String> detailsOf(List<Field> fields) {
        List<String> details = new ArrayList<>(fields.size());
        for (Field field : fields) {
            details.add(field.label() + ": " + field.value());
        }
        return details;
    }

    // ---------------------------------------------------------------- what a layout decides

    /**
     * Returns how wide the item column of the table of lines is, against the others.
     *
     * @return the weight of the column
     */
    float itemColumnWeight() {
        return 3.5f;
    }

    /**
     * Returns how wide the column of the line identifier is, against the others.
     *
     * @return the weight of the column
     */
    float lineColumnWeight() {
        return 1.2f;
    }

    /**
     * Returns how a code is written on the page.
     *
     * <p>The generic layout writes the code the document carries, because that is what a
     * proof of the document needs. The letter layout writes the name of it and lists the
     * code once under its closing heading.
     *
     * @param termId the identifier of the term the code belongs to
     * @param code   the code, as the document spells it
     * @return the text to show
     */
    String codeName(String termId, String code) {
        return code;
    }

    /**
     * Tells whether a hairline closes every row of the table of invoice lines.
     *
     * <p>The generic layout parts a row from the next one where the first carries lines
     * hanging under it, which is what tells those lines apart from the row below them.
     * A layout that writes a letter wants one rhythm for all its rows and says so.
     *
     * @return whether every row is closed by a hairline
     */
    boolean ruleUnderEveryLine() {
        return false;
    }

    /**
     * Returns the row that closes the table of invoice lines under it, or {@code null}
     * where nothing closes it.
     *
     * <p>It is asked for before the last row of the table is drawn, because the table
     * keeps the two on one page and the label decides how tall the row is. Nothing closes
     * the table of the generic layout: it lists every total in a table of its own, which
     * is the picture a proof of the document needs — the sums stand together and in one
     * place.
     *
     * @param netAmount the column the net amount of a line stands in
     * @return the closing row, or {@code null}
     */
    Table.Closing closingRow(int netAmount) {
        return null;
    }

    /**
     * Returns how the VAT of a row of a table is written: the category the document states
     * and the rate beside it.
     *
     * @param prefix       the group instance the two terms sit in
     * @param categoryTerm the term of the VAT category code
     * @param rateTerm     the term of the VAT rate
     * @return the text of the cell
     */
    String vatShown(SemanticPath prefix, String categoryTerm, String rateTerm) {
        return join(cell(prefix, categoryTerm), cell(prefix, rateTerm));
    }

    void lines() {
        List<SemanticPath> lines = instances(SemanticPath.root(), "BG-25");
        if (lines.isEmpty()) {
            return;
        }
        List<Placement> afterUnitPrice = carried(Placement.Position.LINE_UNIT_PRICE, lines);
        List<Placement> afterVat = carried(Placement.Position.LINE_VAT, lines);
        List<Placement> afterAmount = carried(Placement.Position.LINE_AMOUNT, lines);
        Columns columns = new Columns();
        columns.add(Word.COLUMN_LINE.in(language), lineColumnWeight(), false);
        columns.add(Word.COLUMN_ITEM.in(language), itemColumnWeight(), false);
        columns.addWhole(Word.COLUMN_QUANTITY.in(language), 1.1f, true);
        columns.add(money(Word.COLUMN_UNIT_PRICE), 1.7f, true);
        columns.addPlaced(afterUnitPrice, 1.7f);
        columns.add(Word.COLUMN_VAT.in(language), 1.0f, false);
        columns.addPlaced(afterVat, 1.2f);
        int netAmount = columns.next();
        columns.add(money(Word.COLUMN_NET_AMOUNT), 1.4f, true);
        columns.addPlaced(afterAmount, 1.4f);
        // The rows are read before the table is built: a column can only be told how much
        // of its width it has to keep by the cells it is going to be given.
        List<String[]> rows = new ArrayList<>();
        List<List<String>> hanging = new ArrayList<>();
        for (SemanticPath line : lines) {
            List<String> cells = new ArrayList<>();
            cells.add(cell(line, "BT-126"));
            cells.add(cell(line, "BG-31", "BT-153"));
            cells.add(quantity(line));
            cells.add(unitPrice(line));
            placedCells(cells, line, afterUnitPrice);
            cells.add(vatShown(child(line, "BG-30"), "BT-151", "BT-152"));
            placedCells(cells, line, afterVat);
            cells.add(cell(line, "BT-131"));
            placedCells(cells, line, afterAmount);
            String description = cell(line, "BG-31", "BT-154");
            List<String> details = new ArrayList<>();
            if (!description.isEmpty()) {
                details.add(description);
            }
            details.addAll(lineDetails(line));
            rows.add(cells.toArray(new String[0]));
            hanging.add(details);
        }
        Table table = columns.table(rows);
        if (ruleUnderEveryLine()) {
            table.ruleUnderEveryRow();
        }
        tableUnder(Word.LINES, table);
        Table.Closing closing = closingRow(netAmount);
        for (int i = 0; i < rows.size(); i++) {
            if (i == rows.size() - 1 && closing != null) {
                table.closingFollows(closing);
            }
            table.row(rows.get(i), false, hanging.get(i));
        }
        table.end();
        if (closing != null) {
            table.closing(closing);
        }
        if (!afterUnitPrice.isEmpty() || !afterVat.isEmpty() || !afterAmount.isEmpty()) {
            displayedNote();
        }
    }

    private String quantity(SemanticPath line) {
        SemanticPath amount = path(line, "BT-129");
        return join(cell(line, "BT-129"), unitShown(line, "BT-130", content(amount)));
    }

    /**
     * Returns everything that hangs under a row of the table of lines and is not placed
     * yet, as the lines a reader sees.
     *
     * <p>The generic layout writes one label and one value per term, which is what a proof
     * of the document is. A layout that writes a letter says so differently, and says it
     * in {@link LetterLayout}.
     *
     * @param line the invoice line
     * @return the lines hanging under its row
     */
    List<String> lineDetails(SemanticPath line) {
        return detailsOf(collect(line));
    }

    /**
     * Returns the unit of a quantity as this layout writes it.
     *
     * <p>The quantity is handed over because a name has a number: three of something are
     * not one of it, and a letter that writes the name of a unit writes the one the
     * quantity asks for. The generic layout writes the code and ignores it.
     *
     * @param prefix   the group instance the unit code sits in
     * @param term     the identifier of the term of the unit code
     * @param quantity the quantity the unit belongs to, as the document spells it
     * @return the text to show
     */
    String unitShown(SemanticPath prefix, String term, String quantity) {
        return codeName(term, cell(prefix, term));
    }

    /** Returns the content of a value as the document spells it, unformatted. */
    String content(SemanticPath path) {
        return document.value(path).map(SemanticValue::content).orElse("");
    }

    /**
     * Tells whether a number, as the document spells it, is exactly one.
     *
     * @param written the content of the value, or the empty string for a value the
     *                document does not carry
     * @return whether it is one
     */
    static boolean isOne(String written) {
        if (written.isEmpty()) {
            return false;
        }
        try {
            return new BigDecimal(written).compareTo(BigDecimal.ONE) == 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Returns the unit price of a line: the net price, and where the document states a
     * base quantity for it, the quantity that price is for.
     *
     * <p>A value is marked placed only where it is shown. The price base quantity
     * (BT-149) and its unit (BT-150) say what the price is for and mean nothing without
     * the price, so a group that carries one of them without the other says nothing here
     * — and a value that was read and then dropped would be a value the reader never
     * sees, since the last section of the rendering prints what no section placed.
     * A partial price group therefore leaves its values to that section, and so does a
     * base quantity of one: <i>per 1 piece</i> says what <i>the unit price</i> already
     * says, and the two terms reach the page under the closing heading instead.
     */
    private String unitPrice(SemanticPath line) {
        SemanticPath pricePath = path(line, "BG-29", "BT-146");
        SemanticPath quantityPath = path(line, "BG-29", "BT-149");
        SemanticPath unitPath = path(line, "BG-29", "BT-150");
        String price = read(pricePath);
        String baseQuantity = read(quantityPath);
        if (price.isEmpty() || baseQuantity.isEmpty() || isOne(content(quantityPath))) {
            place(pricePath, price);
            return price;
        }
        String baseUnit = unitShown(child(line, "BG-29"), "BT-150", content(quantityPath));
        place(pricePath, price);
        place(quantityPath, baseQuantity);
        return price + " " + Word.PER.in(language) + " "
                + join(baseQuantity, baseUnit);
    }

    void documentAllowancesAndCharges(String groupId, Word heading) {
        List<SemanticPath> groups = instances(SemanticPath.root(), groupId);
        if (groups.isEmpty()) {
            return;
        }
        boolean allowance = "BG-20".equals(groupId);
        String amount = allowance ? "BT-92" : "BT-99";
        String base = allowance ? "BT-93" : "BT-100";
        String percentage = allowance ? "BT-94" : "BT-101";
        String category = allowance ? "BT-95" : "BT-102";
        String rate = allowance ? "BT-96" : "BT-103";
        String reason = allowance ? "BT-97" : "BT-104";
        List<String[]> rows = new ArrayList<>();
        List<List<String>> hanging = new ArrayList<>();
        for (SemanticPath group : groups) {
            rows.add(new String[] {
                    cell(group, reason),
                    cell(group, base),
                    cell(group, percentage),
                    vatShown(group, category, rate),
                    cell(group, amount)});
            hanging.add(detailsOf(collect(group)));
        }
        Table table = new Table(sheet,
                new String[] {Word.COLUMN_REASON.in(language),
                              money(Word.COLUMN_BASE),
                              Word.COLUMN_PERCENTAGE.in(language),
                              Word.COLUMN_VAT.in(language),
                              money(Word.COLUMN_AMOUNT)},
                new float[] {3.4f, 1.3f, 1.1f, 1.2f, 1.3f},
                new boolean[] {false, true, true, false, true},
                Word.CONTINUED.in(language), 0, rows);
        tableUnder(heading, table);
        for (int i = 0; i < rows.size(); i++) {
            table.row(rows.get(i), false, hanging.get(i));
        }
        table.end();
    }

    /**
     * The columns of the table of invoice lines while they are being collected: the fixed
     * ones of the layout and, beside each of them, the ones a template placed there.
     */
    private final class Columns {

        private final List<String> headers = new ArrayList<>();
        private final List<Float> weights = new ArrayList<>();
        private final List<Boolean> right = new ArrayList<>();

        /** The column whose cells are one statement, or {@link Table#NO_COLUMN}. */
        private int whole = Table.NO_COLUMN;

        /** Returns the index the next column added will have. */
        int next() {
            return headers.size();
        }

        /** Adds one column of the layout itself. */
        void add(String header, float weight, boolean rightAligned) {
            headers.add(header);
            weights.add(weight);
            right.add(rightAligned);
        }

        /**
         * Adds one column of the layout whose cell is one statement rather than a text
         * that may wrap where it runs out of room.
         *
         * <p>The quantity is that column: the figure and the name of its unit are read as
         * one word — <i>3.875 Kilowattstunden</i> — and a column that parts them is a
         * column a reader has to put back together.
         *
         * @param header       the header of the column
         * @param weight       its relative width
         * @param rightAligned whether it is set flush right
         */
        void addWhole(String header, float weight, boolean rightAligned) {
            whole = headers.size();
            add(header, weight, rightAligned);
        }

        /** Adds one column per placement, as wide as the column it stands beside. */
        void addPlaced(List<Placement> shown, float weight) {
            for (Placement placement : shown) {
                add(header(placement, true), weight, true);
            }
        }

        /**
         * Returns the table these columns describe.
         *
         * @param rows the rows the table will be given, which decide how much width a
         *             column can spare
         * @return the table
         */
        Table table(List<String[]> rows) {
            float[] widths = new float[weights.size()];
            boolean[] aligned = new boolean[right.size()];
            for (int i = 0; i < widths.length; i++) {
                widths[i] = weights.get(i);
                aligned[i] = right.get(i);
            }
            return new Table(sheet, headers.toArray(new String[0]), widths, aligned,
                    Word.CONTINUED.in(language), 1, rows, whole);
        }
    }

    // ------------------------------------------------- terms a template gives a place

    /**
     * Returns the placements of a position that the document actually fills.
     *
     * <p>A template says which extension terms it has a place for; a document says which
     * of them it carries. The page shows the intersection, so a template written for a
     * consumer invoice costs a business invoice from the same sender no column at all.
     *
     * @param position where in the layout the placements stand
     * @param prefixes the group instances to look under — the invoice lines, or the root
     *                 of the document for a place among the totals
     * @return the placements to show, in the order the template wrote them
     */
    List<Placement> carried(Placement.Position position, List<SemanticPath> prefixes) {
        List<Placement> shown = new ArrayList<>();
        for (Placement placement : placements) {
            if (placement.position() != position) {
                continue;
            }
            for (SemanticPath prefix : prefixes) {
                if (document.value(placedPath(prefix, placement)).isPresent()) {
                    shown.add(placement);
                    break;
                }
            }
        }
        return shown;
    }

    /** Appends one cell per placement to a row of the table of invoice lines. */
    void placedCells(List<String> cells, SemanticPath line, List<Placement> shown) {
        for (Placement placement : shown) {
            cells.add(placedCell(line, placement));
        }
    }

    /**
     * Returns the value a placement puts here, written the way a reader reads it, and
     * marks it placed so that the last section of the rendering does not print it again.
     *
     * <p>The semantic data type decides how a figure is written down. It comes from the
     * registry of the rendering where that registry knows the extension, and from the
     * template where it does not: a template that gives a place to a term brings the type
     * of it, so that a gross unit price is a unit price on the page even in a build that
     * carries no registry for the extension it comes from.
     */
    String placedCell(SemanticPath prefix, Placement placement) {
        SemanticPath path = placedPath(prefix, placement);
        Optional<SemanticValue> value = document.value(path);
        if (value.isEmpty()) {
            return "";
        }
        placed.add(path);
        SemanticType type = datatype(placement.term(),
                placement.type() == null ? SemanticType.TEXT : placement.type());
        return formatted(type, value.get().content());
    }

    /**
     * Returns what a placed column or a placed row is called: the name the template gives
     * the term, or the one the registry gives it, and behind it the word that says the
     * figure is one that was displayed rather than a business term of the standard.
     *
     * @param placement the placement
     * @param money     whether the currency of the document belongs in the header too,
     *                  which it does for a column of figures and not for a row of one
     * @return the label
     */
    String header(Placement placement, boolean money) {
        String name = placement.label(language);
        if (name == null) {
            name = labels.of(placement.term());
        }
        String marked = Word.DISPLAYED.in(language);
        return money && !currency.isEmpty()
                ? name + " (" + marked + ", " + currency + ")"
                : name + " (" + marked + ")";
    }

    /**
     * Writes the one sentence that says what a displayed figure is: a figure out of a
     * model extension, recording what a buyer was shown, beside the net figures of
     * EN 16931-1 rather than instead of them.
     */
    void displayedNote() {
        sheet.down(2f);
        sheet.block(Word.DISPLAYED_NOTE.in(language), sheet.left(), sheet.width(),
                sheet.fonts().regular(), 7.2f, palette.muted());
    }

    /** Returns the path a placement fills under a group instance. */
    static SemanticPath placedPath(SemanticPath prefix, Placement placement) {
        return SemanticPath.of(prefix + "/" + placement.term());
    }

    // ---------------------------------------------------------------- labels

    /**
     * Returns the label of a value, preceded by the name and the number of every
     * repeatable group between a prefix and the value itself: the second allowance of an
     * invoice line reads as the second allowance of that line, counted from one, because
     * a reader counts from one.
     *
     * <p>A group that occurs once is not named. Its terms are labelled unambiguously
     * already — an address line of the seller stands in the block of the seller — and
     * naming it would only put a heading into every label of a block.
     *
     * @param from the group instance the label is read under
     * @param path the path of the value
     * @return the label
     */
    String qualifiedLabel(SemanticPath from, SemanticPath path) {
        return groupQualifier(from, path) + label(path);
    }

    /**
     * Returns the part of a qualified label that stands before the label itself: the name
     * and the number of every repeatable group between a prefix and a value, each closed
     * by a middle dot, and the empty string where there is none.
     *
     * <p>A group the document carries exactly once is not named, which is what the
     * paragraph above means by a group that occurs once: one payment instruction with one
     * account reads <i>IBAN</i> and not <i>Credit transfer 1 · IBAN</i>, because a number
     * that is always one tells a reader nothing.
     *
     * @param from the group instance the label is read under
     * @param path the path of the value
     * @return the qualifier, ending in a middle dot, or the empty string
     */
    String groupQualifier(SemanticPath from, SemanticPath path) {
        StringBuilder label = new StringBuilder();
        for (SemanticPath group : path.groupPaths()) {
            if (group.segments().size() <= from.segments().size() || !group.startsWith(from)
                    || group.index().isEmpty() || occurrences(group) < 2) {
                continue;
            }
            label.append(marked(qualifier(group.term()), group.termSegment()))
                    .append(' ').append(group.index().orElseThrow().value() + 1)
                    .append(" · ");
        }
        return label.toString();
    }

    /**
     * Returns how many occurrences of a group stand beside the one a path names, itself
     * included.
     *
     * <p>The whole document is counted once, on the first question, and the answer is
     * kept: a label is asked for this per value, and counting the values of a three
     * hundred thousand line invoice per label would be the rendering's running time.
     *
     * @param group the path of one group instance, index and all
     * @return how many instances of that group the document carries under the same parent
     */
    private int occurrences(SemanticPath group) {
        if (groupCounts == null) {
            groupCounts = countGroups();
        }
        return groupCounts.getOrDefault(withoutIndex(group), 1);
    }

    /** Counts, for every repeatable group of the document, how many instances it has. */
    private Map<SemanticPath, Integer> countGroups() {
        Map<SemanticPath, Set<SemanticPath>> found = new HashMap<>();
        for (SemanticPath path : document.values().keySet()) {
            for (SemanticPath group : path.groupPaths()) {
                if (group.index().isPresent()) {
                    found.computeIfAbsent(withoutIndex(group), key -> new HashSet<>())
                            .add(group);
                }
            }
        }
        Map<SemanticPath, Integer> counts = new HashMap<>(found.size());
        found.forEach((group, instances) -> counts.put(group, instances.size()));
        return counts;
    }

    /** Returns the path of a group instance without the index that picks the instance. */
    private static SemanticPath withoutIndex(SemanticPath group) {
        return group.prefix(group.segments().size() - 1);
    }

    /**
     * Returns the label of the term a path ends at, with the namespace of its extension
     * where it has one.
     *
     * @param path the path
     * @return the label
     */
    String label(SemanticPath path) {
        return marked(labels.of(path.term()), path.termSegment());
    }

    /**
     * Returns a name with the namespace of its extension behind it, where the term is not
     * one of the core model.
     *
     * <p>An extension term is rendered like every other value of the document — under the
     * name its own registry gives it, in the place its group gives it — and that is
     * exactly why it is marked. A reader who sees <i>Gross unit price</i> beside the net
     * figures of the standard has to be able to tell at a glance that the first is not a
     * business term of EN 16931-1, whoever wrote the extension and whatever it called the
     * term. The namespace of the identifier says so in one short token and is there in
     * every extension by construction (specification, section 5.6), so nothing depends on
     * the renderer having the extension's registry loaded.
     *
     * @param name the name of the term or group
     * @param term the segment the name belongs to
     * @return the name, with the namespace of an extension behind it
     */
    static String marked(String name, PathSegment.Term term) {
        return term.isExtension() ? name + " (" + term.namespace() + ")" : name;
    }

    /**
     * Returns the name of a group as it reads inside a label.
     *
     * <p>The registry writes the name of a group in capitals, as the tables of the
     * standard do, and a layout writes its section headings itself for exactly that
     * reason: capitals shout, and a heading that shouts is still a heading. Inside a
     * label they are not a heading but the first half of a sentence, so a name that
     * carries no small letter is written as one — an abbreviation that is a word of its
     * own keeps its capitals, because it is not shouting.
     *
     * <p>A localized name is left alone. It comes from the vendored localization, it is
     * written the way the language writes a noun, and re-casing it would only be this
     * module having an opinion about German orthography. So is an identifier standing in
     * for a name no registry of this rendering knows: an identifier is spelled the way it
     * is spelled.
     *
     * @param termId the identifier of the group
     * @return the name of the group, as a label carries it
     */
    String qualifier(String termId) {
        String name = labels.of(termId);
        if (name.equals(termId) || name.chars().anyMatch(Character::isLowerCase)) {
            return name;
        }
        StringBuilder written = new StringBuilder(name.length());
        for (String word : name.split(" ", -1)) {
            if (written.length() > 0) {
                written.append(' ');
            }
            written.append(ABBREVIATIONS.contains(word) ? word
                    : word.toLowerCase(Locale.ROOT));
        }
        if (written.length() > 0) {
            written.setCharAt(0, Character.toUpperCase(written.charAt(0)));
        }
        return written.toString();
    }

    /**
     * Returns the path of a term inside a group instance. A group and a business term are
     * two different kinds of path, and the identifier says which of the two this is.
     */
    static SemanticPath child(SemanticPath prefix, String term) {
        String text = prefix + "/" + term;
        return term.startsWith("BG") ? SemanticPath.group(text) : SemanticPath.of(text);
    }

    /**
     * Returns the instances of a group directly under a prefix that the document actually
     * carries a value in, in canonical order.
     *
     * @param prefix  the group instance to look under
     * @param groupId the identifier of the group
     * @return the instances
     */
    List<SemanticPath> instances(SemanticPath prefix, String groupId) {
        return instances(document, prefix, groupId);
    }

    /**
     * Returns the instances of a group of a document, which is the same question asked of
     * a document rather than of a layout — {@link PaymentCode} reads the payment
     * instructions of a document it is not drawing.
     *
     * @param document the document
     * @param prefix   the group instance to look under
     * @param groupId  the identifier of the group
     * @return the instances
     */
    static List<SemanticPath> instances(SemanticDocument document, SemanticPath prefix,
                                        String groupId) {
        Set<SemanticPath> found = new LinkedHashSet<>();
        int depth = prefix.segments().size();
        for (SemanticPath path : document.values().keySet()) {
            if (!path.startsWith(prefix)) {
                continue;
            }
            List<PathSegment> segments = path.segments();
            if (segments.size() <= depth
                    || !(segments.get(depth) instanceof PathSegment.Term term)
                    || term.kind() != TermKind.BG
                    || !term.id().equals(groupId)) {
                continue;
            }
            int end = depth + 1;
            if (end < segments.size() && segments.get(end) instanceof PathSegment.Index) {
                end++;
            }
            found.add(path.prefix(end));
        }
        return List.copyOf(found);
    }

    /**
     * One line of a block: the term it comes from, what it is called, what it says, and
     * whether it is set bold.
     *
     * @param term  the identifier of the business term the line comes from
     * @param label what the line is called
     * @param value what it says
     * @param bold  whether it is set in the bold face
     */
    record Field(String term, String label, String value, boolean bold) {

        /** Returns this field under another label. */
        Field renamed(String other) {
            return new Field(term, other, value, bold);
        }
    }

    /**
     * Returns the fields of a block with every label that more than one of them carries
     * made unambiguous.
     *
     * <p>The localization of the visualization labels the fields of a layout, and a layout
     * can afford to call two terms the same thing when they stand in two different tables:
     * BT-109 and BT-112 are both {@code Gesamtsumme} there, and so is BT-116. In a block
     * that lists them under one another that is not a label but a riddle.
     *
     * <p>Where the two are a figure without VAT and a figure with it, the localization has
     * the word for it and that word is what a reader needs: {@code Gesamtsumme netto} and
     * {@code Gesamtsumme brutto} say which is which without knowing any numbering, and are
     * what the HTML rendering of this module writes. Where it has none, the identifier of
     * the term settles it, which is also the identifier a reader would quote when asking
     * about the figure. A word that did not make two labels distinct is not enough on its
     * own, so those fields fall back to the identifier as well.
     *
     * @param fields the fields of the block
     * @return the fields, each under a label no other one in the block carries
     */
    List<Field> disambiguate(List<Field> fields) {
        List<Field> told = new ArrayList<>(fields.size());
        for (Field field : fields) {
            if (count(fields, field.label()) > 1) {
                told.add(labels.netOrGross(field.term())
                        .map(word -> field.renamed(field.label() + " " + word))
                        .orElse(field));
            } else {
                told.add(field);
            }
        }
        List<Field> distinct = new ArrayList<>(told.size());
        for (Field field : told) {
            distinct.add(count(told, field.label()) > 1
                    ? field.renamed(field.label() + " (" + field.term() + ")")
                    : field);
        }
        return distinct;
    }

    /** Returns how many fields of a block carry a label. */
    private static int count(List<Field> fields, String label) {
        int seen = 0;
        for (Field field : fields) {
            if (field.label().equals(label)) {
                seen++;
            }
        }
        return seen;
    }
}
