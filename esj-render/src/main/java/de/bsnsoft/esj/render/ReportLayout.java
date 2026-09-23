package de.bsnsoft.esj.render;

import de.bsnsoft.esj.report.Text;
import de.bsnsoft.esj.report.ValidationOutcome;
import java.util.List;

/**
 * The validation report, laid out on a {@link Sheet}.
 *
 * <p>It is the same report the HTML page carries, in the same order and with the same
 * words: the verdict and what was judged, the check table, the findings under the engine
 * that produced each, and what was done to the bytes before they were read.
 * {@link ReportContent} decides every one of those texts, so the two forms cannot drift
 * apart.
 *
 * <p>The pages of the invoice follow this layout on the same sheet rather than in a second
 * document, which is why nothing here closes the sheet: {@link ReportRenderer} draws this,
 * then hands the sheet to {@link PdfLayout} or finishes it itself. A report and the invoice
 * it is about are one file, and the footer runs through both.
 */
final class ReportLayout {

    /** The type size of the title. */
    private static final float TITLE_SIZE = 15f;

    /** The type size of the verdict. */
    private static final float VERDICT_SIZE = 13f;

    /** The type size of a section heading. */
    private static final float SECTION_SIZE = 10f;

    /** The type size of a label and of the value beside it. */
    private static final float FIELD_SIZE = 8.4f;

    /** The type size of the place a finding names, which is the smallest thing here. */
    private static final float WHERE_SIZE = 7.6f;

    /** The air between two sections. */
    private static final float SECTION_GAP = 11f;

    /** The air between two findings. */
    private static final float FINDING_GAP = 5f;

    /** The gap between a label and the value beside it. */
    private static final float LABEL_GAP = 6f;

    /** How much of the width the label of an identity row takes. */
    private static final float IDENTITY_SHARE = 0.26f;

    /** How much of the width the label of a check row takes. */
    private static final float CHECK_SHARE = 0.58f;

    private final ValidationOutcome outcome;
    private final ReportOptions options;
    private final RenderLanguage language;
    private final Sheet sheet;

    /**
     * Prepares the layout of a report.
     *
     * @param outcome what the run came to
     * @param options the language, and the moment the caller passed
     * @param sheet   the sheet to draw on
     */
    ReportLayout(ValidationOutcome outcome, ReportOptions options, Sheet sheet) {
        this.outcome = outcome;
        this.options = options;
        this.language = options.language();
        this.sheet = sheet;
    }

    /** Draws the report, leaving the sheet open for whatever follows it. */
    void draw() {
        title();
        identity();
        checks();
        findings();
        provenance();
    }

    /**
     * Returns what the footer of every page of this file carries on the left: the name of
     * the report and the input it is about, the input shortened to the room the footer
     * leaves it beside the page count.
     *
     * <p>A caller names the input by a path, and a path is as long as a directory tree is
     * deep: one of a hundred and thirty characters ran into the page count. So a path too
     * long for the room is shortened in its middle, an ellipsis standing for what was left
     * out. The beginning of the path stays, and so does its file name, which is what a
     * reader knows a file by; only a file name wider than the room itself is shortened in
     * its middle as well. The identity block of the report names the input whole, and so
     * does the HTML form.
     *
     * @param room how wide the text may be, as {@link Sheet#footerRoom(String)} says
     * @return the text
     */
    String footer(float room) {
        Fonts.Face face = sheet.fonts().regular();
        String name = face.showable(ReportWord.TITLE.in(language) + " — ");
        String input = face.showable(ReportContent.plain(outcome.identity().input()));
        return name + shortened(input, room - face.width(name, Sheet.FOOTER_SIZE), face);
    }

    /**
     * Returns a path shortened in its middle to a width, with an ellipsis where the middle
     * was: its file name whole where the room holds that and at least one character of the
     * beginning, and otherwise as much of its end as of its beginning.
     *
     * @param path the path, already in the characters the face can show
     * @param room how wide it may be, in points
     * @param face the face the footer is set in
     * @return the path, or the path shortened
     */
    private static String shortened(String path, float room, Fonts.Face face) {
        float size = Sheet.FOOTER_SIZE;
        if (face.width(path, size) <= room) {
            return path;
        }
        String ellipsis = face.showable("…");
        float left = room - face.width(ellipsis, size);
        int[] points = path.codePoints().toArray();
        if (left <= 0 || points.length == 0) {
            return ellipsis;
        }
        float[] widths = new float[points.length];
        for (int i = 0; i < points.length; i++) {
            widths[i] = face.width(new String(points, i, 1), size);
        }
        float tailRoom = left / 2f;
        int separator = lastSeparator(points);
        if (separator > 0) {
            float name = 0;
            for (int i = separator; i < points.length; i++) {
                name += widths[i];
            }
            if (name + widths[0] <= left) {
                // A little over the sum, so that adding the widths up again from the other
                // end cannot lose the separator to the last digit of a float.
                tailRoom = Math.max(tailRoom, name + 0.01f);
            }
        }
        int tail = points.length;
        float used = 0;
        while (tail > 0 && used + widths[tail - 1] <= tailRoom) {
            used += widths[--tail];
        }
        int head = 0;
        float taken = 0;
        while (head < tail && taken + widths[head] <= left - used) {
            taken += widths[head++];
        }
        String written = new String(points, 0, head) + ellipsis
                + new String(points, tail, points.length - tail);
        // The widths were added up one character at a time and the text is measured whole,
        // so the two may differ in the last digit of a float; a character more comes off
        // where they do.
        while (face.width(written, size) > room && (head > 0 || tail < points.length)) {
            if (head > 0) {
                head--;
            } else {
                tail++;
            }
            written = new String(points, 0, head) + ellipsis
                    + new String(points, tail, points.length - tail);
        }
        return written;
    }

    /** Returns where the last directory separator of a path stands, or -1 for none. */
    private static int lastSeparator(int[] points) {
        for (int i = points.length - 1; i >= 0; i--) {
            if (points[i] == '/' || points[i] == '\\') {
                return i;
            }
        }
        return -1;
    }

    /**
     * Writes the section about the invoice where the pages of it cannot say it
     * themselves: the sentence that stands in its place, and what did not reach it.
     *
     * <p>Both forms of the report write the same sentences here, out of
     * {@link ReportContent}, because a reader of the PDF and a reader of the page have to
     * be told the same thing about the same document.
     *
     * @param invoice what this report says about the invoice
     */
    void invoiceSection(ReportInvoice invoice) {
        List<String> notes = ReportContent.exportNotes(invoice.notes(), language);
        if (invoice.refusal().isEmpty() && invoice.oversize().isEmpty()
                && invoice.oversizeValues().isEmpty() && notes.isEmpty()) {
            return;
        }
        heading(ReportWord.INVOICE.in(language));
        invoice.refusal().ifPresent(reason -> sentence(
                ReportWord.INVOICE_REFUSED.in(language) + " " + ReportContent.plain(reason)));
        invoice.oversize().ifPresent(characters -> sentence(ReportWord.INVOICE_TOO_LARGE
                .in(language, List.of(Integer.toString(characters)))));
        invoice.oversizeValues().ifPresent(values -> sentence(
                ReportWord.INVOICE_TOO_LARGE_DOCUMENT
                        .in(language, List.of(Integer.toString(values)))));
        if (!notes.isEmpty()) {
            sentence((invoice.notes().size() == 1 ? ReportWord.EXPORT_NOTE
                    : ReportWord.EXPORT_NOTES).in(language,
                            List.of(Integer.toString(invoice.notes().size()))));
            for (String note : notes) {
                sentence("— " + note);
            }
        }
    }

    /** Writes one sentence of the report's own, in the type of a field. */
    private void sentence(String text) {
        sheet.block(text, sheet.left(), sheet.width(), sheet.fonts().regular(),
                FIELD_SIZE, sheet.palette().text());
    }

    private void title() {
        sheet.block(ReportWord.TITLE.in(language), sheet.left(), sheet.width(),
                sheet.fonts().bold(), TITLE_SIZE, sheet.palette().text());
        sheet.down(4f);
        sheet.rule();
        sheet.down(SECTION_GAP);
        sheet.block(ReportWord.VERDICT.in(language), sheet.left(), sheet.width(),
                sheet.fonts().regular(), FIELD_SIZE, sheet.palette().muted());
        sheet.block(ReportContent.verdict(outcome), sheet.left(), sheet.width(),
                sheet.fonts().bold(), VERDICT_SIZE, sheet.palette().text());
        outcome.detail().ifPresent(detail ->
                sheet.block(ReportContent.resolve(detail, language), sheet.left(),
                        sheet.width(), sheet.fonts().regular(), FIELD_SIZE,
                        sheet.palette().muted()));
        for (ReportContent.Line line : ReportContent.subjects(outcome, language)) {
            sheet.down(2f);
            pair(line.label() + ":", line.value(), IDENTITY_SHARE);
        }
    }

    private void identity() {
        heading(ReportWord.IDENTITY.in(language));
        for (ReportContent.Line line : ReportContent.identity(outcome, options)) {
            pair(line.label(), line.value(), IDENTITY_SHARE);
        }
    }

    private void checks() {
        heading(ReportWord.CHECKS.in(language));
        for (ValidationOutcome.Block block : outcome.blocks()) {
            sheet.down(4f);
            sheet.require(Sheet.lineHeight(FIELD_SIZE) * 2);
            sheet.block(ReportContent.heading(block.kind(), language), sheet.left(),
                    sheet.width(), sheet.fonts().bold(), FIELD_SIZE, sheet.palette().text());
            for (ValidationOutcome.Row row : block.rows()) {
                pair(ReportContent.resolve(row.label(), language),
                        ReportContent.status(row, language), CHECK_SHARE);
            }
        }
    }

    private void findings() {
        heading(ReportWord.FINDINGS.in(language));
        if (outcome.findings().isEmpty()) {
            sheet.block(ReportWord.NOTHING_FOUND.in(language), sheet.left(), sheet.width(),
                    sheet.fonts().regular(), FIELD_SIZE, sheet.palette().muted());
            return;
        }
        for (ValidationOutcome.Block block : outcome.blocks()) {
            if (block.findings().isEmpty()) {
                continue;
            }
            sheet.down(4f);
            sheet.require(Sheet.lineHeight(FIELD_SIZE) * 3);
            sheet.block(ReportContent.heading(block.kind(), language), sheet.left(),
                    sheet.width(), sheet.fonts().bold(), FIELD_SIZE, sheet.palette().text());
            for (ValidationOutcome.Finding finding : ReportContent.findings(block)) {
                finding(finding);
            }
            for (String note : ReportContent.notes(block, language)) {
                sheet.down(FINDING_GAP);
                sheet.block(note, sheet.left(), sheet.width(), sheet.fonts().regular(),
                        FIELD_SIZE, sheet.palette().muted());
            }
        }
    }

    private void finding(ValidationOutcome.Finding finding) {
        sheet.down(FINDING_GAP);
        sheet.require(Sheet.lineHeight(FIELD_SIZE) * 2);
        sheet.block(ReportContent.head(finding, language), sheet.left(), sheet.width(),
                sheet.fonts().bold(), FIELD_SIZE, sheet.palette().text());
        String where = ReportContent.where(finding, language);
        if (!where.isEmpty()) {
            sheet.block(where, sheet.left(), sheet.width(), sheet.fonts().regular(),
                    WHERE_SIZE, sheet.palette().muted());
        }
        sheet.block(ReportContent.message(finding, language), sheet.left(), sheet.width(),
                sheet.fonts().regular(), FIELD_SIZE, sheet.palette().text());
    }

    private void provenance() {
        if (outcome.provenance().isEmpty()) {
            return;
        }
        heading(ReportWord.PROVENANCE.in(language));
        for (Text note : outcome.provenance()) {
            sheet.block("— " + ReportContent.resolve(note, language), sheet.left(),
                    sheet.width(), sheet.fonts().regular(), FIELD_SIZE, sheet.palette().text());
        }
    }

    /** Writes a section heading with the rule under it, starting a page where it must. */
    private void heading(String text) {
        sheet.down(SECTION_GAP);
        sheet.require(Sheet.lineHeight(SECTION_SIZE) + Sheet.RULE_HEIGHT + 6f
                + Sheet.lineHeight(FIELD_SIZE));
        sheet.block(text, sheet.left(), sheet.width(), sheet.fonts().bold(), SECTION_SIZE,
                sheet.palette().text());
        sheet.down(2f);
        sheet.rule();
        sheet.down(4f);
    }

    /**
     * Writes a label and a value beside each other, and moves the cursor below them.
     *
     * <p>A pair taller than a whole page is stacked instead. Nothing in a report bounds how
     * long a value is — the label of a check row and the place a finding names both come out
     * of a document a stranger wrote — and a two-column block that did not fit on a page of
     * its own would run off the paper and take what is under it with it.
     */
    private void pair(String label, String value, float labelShare) {
        Fonts.Face face = sheet.fonts().regular();
        float labelWidth = sheet.width() * labelShare - LABEL_GAP;
        float valueWidth = sheet.width() - labelWidth - LABEL_GAP;
        float height = Math.max(Sheet.measure(label, face, FIELD_SIZE, labelWidth),
                Sheet.measure(value, face, FIELD_SIZE, valueWidth));
        if (height > sheet.usableHeight()) {
            sheet.block(label, sheet.left(), sheet.width(), face, FIELD_SIZE,
                    sheet.palette().muted());
            sheet.block(value, sheet.left(), sheet.width(), face, FIELD_SIZE,
                    sheet.palette().text());
            return;
        }
        sheet.require(height);
        float top = sheet.y();
        sheet.blockAt(label, sheet.left(), top, labelWidth, face, FIELD_SIZE,
                sheet.palette().muted());
        sheet.blockAt(value, sheet.left() + labelWidth + LABEL_GAP, top, valueWidth, face,
                FIELD_SIZE, sheet.palette().text());
        sheet.down(height);
    }
}
