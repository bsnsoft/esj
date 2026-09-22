package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The generic layout: the invoice as the shape of the semantic model, on a {@link Sheet}.
 *
 * <p>This is one layout for every document of the model, driven by the term registry rather
 * than by a template: the sections below are the shape of the semantic model, the labels
 * come from the registry and from the vendored localization, and anything the layout has no
 * place of its own for is printed under a final heading with its label and its path. It is
 * the default, and it is the right picture for a proof — every term under its own name,
 * every code as the code the document carries. {@link LetterLayout} is the other one.
 *
 * <h2>The order, and why it is that order</h2>
 *
 * <p>Who, what, how much, and then how to pay: the parties and the identification of the
 * document, the notes and the delivery, the invoice lines with their net amounts and their
 * net unit prices, the allowances and charges of the document, the VAT breakdown per
 * category, the totals with the gross figures at the end, and after them the payment
 * instructions, the payee, the tax representative and the supporting documents.
 */
final class PdfLayout extends InvoiceLayout {

    /** The type size of the invoice number at the head of the first page. */
    private static final float TITLE_SIZE = 15f;

    /** The gap between the two columns of the party block. */
    private static final float COLUMN_GAP = 18f;

    /**
     * Prepares the generic layout of a document.
     *
     * @param document the document
     * @param registry the registry the types and the English labels come from
     * @param language the language of the rendering
     * @param sheet    the sheet to draw on
     * @param template the branded template, or {@code null} for the plain rendering
     */
    PdfLayout(SemanticDocument document, Registry registry, RenderLanguage language,
              Sheet sheet, RenderTemplate template) {
        super(document, registry, language, sheet, template);
    }

    /** Draws the whole document and writes the footers. */
    @Override
    void draw() {
        drawContent();
        sheet.finish(identity(), Word.PAGE_OF.in(language));
    }

    /**
     * Draws the document and leaves the sheet open.
     *
     * <p>It is separate from {@link #draw()} because this layout is not always the whole
     * file: a validation report draws its own pages first and the invoice after them, and
     * a footer is written once over every page of a file rather than once per layout that
     * contributed to it.
     */
    void drawContent() {
        reserve();
        title();
        parties();
        invoiceData();
        notes();
        delivery();
        lines();
        documentAllowancesAndCharges("BG-20", Word.ALLOWANCES);
        documentAllowancesAndCharges("BG-21", Word.CHARGES);
        vatBreakdown();
        totals();
        payment();
        collectedSection(Word.PAYEE, "BG-10");
        collectedSection(Word.TAX_REPRESENTATIVE, "BG-11");
        supportingDocuments();
        otherTerms(Word.OTHER_TERMS);
    }

    // ---------------------------------------------------------------- sections

    private void title() {
        SemanticPath path = SemanticPath.of("/BT-1");
        Optional<SemanticValue> number = document.value(path);
        if (number.isEmpty()) {
            return;
        }
        placed.add(path);
        sheet.block(labels.of("BT-1"), sheet.left(), sheet.width(),
                sheet.fonts().regular(), 7.6f, palette.muted());
        sheet.block(shown(path, number.get()), sheet.left(), sheet.width(),
                sheet.fonts().bold(), TITLE_SIZE, palette.heading());
        sheet.down(3f);
        sheet.rule();
    }

    private void parties() {
        List<Field> seller = collect(SemanticPath.group("/BG-4"));
        List<Field> buyer = collect(SemanticPath.group("/BG-7"));
        if (seller.isEmpty() && buyer.isEmpty()) {
            return;
        }
        float column = (sheet.width() - COLUMN_GAP) / 2;
        float headingHeight = Sheet.lineHeight(SECTION_SIZE) + 4f;
        float height = headingHeight
                + Math.max(definitionsHeight(disambiguate(seller), column),
                           definitionsHeight(disambiguate(buyer), column));
        sheet.down(SECTION_GAP);
        if (!sheet.fits(height)) {
            sheet.newPage();
        }
        if (!sheet.fits(height)) {
            // Two parties that do not fit on a page of their own are stacked instead,
            // because a column that runs off the paper would lose what is under it.
            stacked(Word.SELLER, seller);
            stacked(Word.BUYER, buyer);
            return;
        }
        float top = sheet.y();
        float right = sheet.left() + column + COLUMN_GAP;
        sheet.blockAt(Word.SELLER.in(language), sheet.left(), top, column,
                sheet.fonts().bold(), SECTION_SIZE, palette.heading());
        sheet.blockAt(Word.BUYER.in(language), right, top, column,
                sheet.fonts().bold(), SECTION_SIZE, palette.heading());
        sheet.rule(sheet.left(), sheet.left() + column, top - headingHeight + 3f, palette.rule());
        sheet.rule(right, right + column, top - headingHeight + 3f, palette.rule());
        definitionsAt(disambiguate(seller), sheet.left(), top - headingHeight, column);
        definitionsAt(disambiguate(buyer), right, top - headingHeight, column);
        sheet.down(height);
    }

    private void invoiceData() {
        List<Field> fields = new ArrayList<>();
        for (SemanticPath path : document.values().keySet()) {
            if (path.groupPaths().isEmpty() && !placed.contains(path)
                    && !"BT-20".equals(path.term())) {
                add(fields, path);
            }
        }
        for (SemanticPath control : instances(SemanticPath.root(), "BG-2")) {
            fields.addAll(collect(control));
        }
        for (SemanticPath preceding : instances(SemanticPath.root(), "BG-3")) {
            fields.addAll(collect(preceding));
        }
        stacked(Word.INVOICE_DATA, fields);
    }

    private void notes() {
        List<Field> fields = new ArrayList<>();
        for (SemanticPath note : instances(SemanticPath.root(), "BG-1")) {
            fields.addAll(collect(note));
        }
        stacked(Word.NOTES, fields);
    }

    private void delivery() {
        collectedSection(Word.DELIVERY, "BG-13");
    }

    private void payment() {
        List<Field> fields = new ArrayList<>();
        add(fields, SemanticPath.of("/BT-20"));
        for (SemanticPath instructions : instances(SemanticPath.root(), "BG-16")) {
            fields.addAll(collect(instructions));
        }
        stacked(Word.PAYMENT, fields);
    }

    private void vatBreakdown() {
        List<SemanticPath> groups = instances(SemanticPath.root(), "BG-23");
        if (groups.isEmpty()) {
            return;
        }
        List<String[]> rows = new ArrayList<>();
        List<List<String>> hanging = new ArrayList<>();
        for (SemanticPath group : groups) {
            rows.add(new String[] {
                    cell(group, "BT-118"),
                    cell(group, "BT-119"),
                    cell(group, "BT-116"),
                    cell(group, "BT-117")});
            hanging.add(detailsOf(collect(group)));
        }
        Table table = new Table(sheet,
                new String[] {Word.COLUMN_CATEGORY.in(language),
                              Word.COLUMN_RATE.in(language),
                              money(Word.COLUMN_TAXABLE),
                              money(Word.COLUMN_VAT_AMOUNT)},
                new float[] {2.4f, 1.2f, 1.6f, 1.6f},
                new boolean[] {false, true, true, true},
                Word.CONTINUED.in(language), 0, rows);
        tableUnder(Word.VAT_BREAKDOWN, table);
        for (int i = 0; i < rows.size(); i++) {
            table.row(rows.get(i), false, hanging.get(i));
        }
        table.end();
    }

    private void totals() {
        List<SemanticPath> groups = instances(SemanticPath.root(), "BG-22");
        List<Placement> displayed =
                carried(Placement.Position.TOTALS, List.of(SemanticPath.root()));
        if (groups.isEmpty() && displayed.isEmpty()) {
            return;
        }
        List<String[]> rows = new ArrayList<>();
        List<Boolean> bold = new ArrayList<>();
        for (SemanticPath group : groups) {
            for (Field field : disambiguate(collect(group))) {
                rows.add(new String[] {inItsOwnCurrency(field).label(), field.value()});
                bold.add(field.bold());
            }
        }
        for (Placement placement : displayed) {
            rows.add(new String[] {header(placement, false),
                                   placedCell(SemanticPath.root(), placement)});
            bold.add(false);
        }
        Table table = new Table(sheet,
                new String[] {"", money(Word.COLUMN_AMOUNT)},
                new float[] {3.6f, 1.4f},
                new boolean[] {false, true},
                Word.CONTINUED.in(language), 0, rows);
        tableUnder(Word.TOTALS, table);
        for (int i = 0; i < rows.size(); i++) {
            table.row(rows.get(i), bold.get(i));
        }
        table.end();
        if (!displayed.isEmpty()) {
            displayedNote();
        }
    }

    /**
     * Returns a row of the totals under a label that names its currency, where that is not
     * the currency the column header names.
     *
     * <p>The header of this table carries BT-5, the currency of the document, and every
     * figure under it is stated in that currency but one: BT-111 is the VAT total in the
     * accounting currency BT-6. A figure of one currency under a header that names another
     * is a wrong figure, so the row says which currency it is in and the header keeps
     * speaking for the rest.
     *
     * @param field the row
     * @return the row, under a label that names its currency where it has one of its own
     */
    private Field inItsOwnCurrency(Field field) {
        if (!"BT-111".equals(field.term()) || accountingCurrency.isEmpty()
                || accountingCurrency.equals(currency)) {
            return field;
        }
        return field.renamed(field.label() + " (" + accountingCurrency + ")");
    }

    private void supportingDocuments() {
        List<SemanticPath> groups = instances(SemanticPath.root(), "BG-24");
        if (groups.isEmpty()) {
            return;
        }
        List<Field> fields = new ArrayList<>();
        for (SemanticPath group : groups) {
            SemanticPath attachment = child(group, "BT-125");
            document.value(attachment).ifPresent(value -> placed.add(attachment));
            fields.addAll(collect(group));
            document.value(attachment).ifPresent(value ->
                    fields.add(new Field("BT-125", labels.of("BT-125"), attachment(value), false)));
        }
        stacked(Word.SUPPORTING_DOCUMENTS, fields);
    }
}
