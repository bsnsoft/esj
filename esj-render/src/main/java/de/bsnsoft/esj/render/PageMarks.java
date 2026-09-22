package de.bsnsoft.esj.render;

/**
 * The marks a letter carries in its left margin so that it can be folded and filed.
 *
 * <p>They are printed matter and not content: two short rules where the paper is folded for
 * a window envelope and one where a two-hole punch goes, at the distances from the top edge
 * that DIN 5008 gives for them. A rendering carries them only where the template asks, and
 * on plain paper — a sender whose letterhead is printed with its own marks does not want a
 * second set.
 *
 * @param fold whether the two fold marks are printed
 * @param hole whether the punch mark is printed
 */
record PageMarks(boolean fold, boolean hole) {

    /** How far the marks stand from the left edge of the paper, in millimetres. */
    private static final float FROM_LEFT = 5f;

    /** How long a fold mark is, in millimetres. */
    private static final float FOLD_LENGTH = 5f;

    /** How long the punch mark is, in millimetres. */
    private static final float HOLE_LENGTH = 8f;

    /** How far below the top edge the first fold mark sits, in millimetres. */
    private static final float FIRST_FOLD = 105f;

    /** How far below it the second one sits, in millimetres. */
    private static final float SECOND_FOLD = 210f;

    /** How far below the top edge the punch mark sits, in millimetres. */
    private static final float PUNCH = 148.5f;

    /**
     * Returns the marks a template asked for, or {@code null} where it asked for none.
     *
     * @param fold whether the fold marks are printed
     * @param hole whether the punch mark is printed
     * @return the marks, or {@code null}
     */
    static PageMarks of(boolean fold, boolean hole) {
        return fold || hole ? new PageMarks(fold, hole) : null;
    }

    /**
     * Draws the marks on the page the sheet has just opened.
     *
     * @param sheet the sheet
     * @param size  the paper
     */
    void paint(Sheet sheet, PageSize size) {
        if (fold) {
            mark(sheet, size, FIRST_FOLD, FOLD_LENGTH);
            mark(sheet, size, SECOND_FOLD, FOLD_LENGTH);
        }
        if (hole) {
            mark(sheet, size, PUNCH, HOLE_LENGTH);
        }
    }

    /** Draws one mark, unless the paper is too short to carry it. */
    private static void mark(Sheet sheet, PageSize size, float belowTop, float length) {
        float y = size.height() - belowTop * Sheet.MM;
        if (y < 0) {
            return;
        }
        float x = FROM_LEFT * Sheet.MM;
        sheet.rule(x, x + length * Sheet.MM, y, sheet.palette().rule());
    }
}
