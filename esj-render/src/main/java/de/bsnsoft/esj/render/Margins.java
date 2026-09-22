package de.bsnsoft.esj.render;

/**
 * The white a page keeps around its text, in PostScript points.
 *
 * <p>A letterhead is printed matter, and the text of the invoice has to stay out of it.
 * The margins are therefore the one measurement a template really has to be able to set,
 * and it sets two of them: one for the first page, where a letterhead carries the address
 * block and the logo, and one for the pages that follow, where it usually carries much
 * less.
 *
 * <p>The left and the right margin are the same on both, and a template that writes two
 * different ones is refused. A table lays its columns out once and keeps them across every
 * page it runs onto, so a text width that changed at a page break would move the columns
 * of the second page away from the header above them.
 *
 * @param left   the white left of the text
 * @param right  the white right of the text
 * @param top    the white above the text
 * @param bottom the white below the text, which the page footer sits in
 */
record Margins(float left, float right, float top, float bottom) {

    /** The margin the generic layout keeps left and right of its text. */
    private static final float DEFAULT_SIDE = 48f;

    /** The margin it keeps above its text. */
    private static final float DEFAULT_TOP = 48f;

    /** The margin it keeps below its text. */
    private static final float DEFAULT_BOTTOM = 52f;

    /**
     * How far below the top of the bottom margin the footer of a page sits.
     *
     * <p>Below the top of the margin, and not above the edge of the paper: a template
     * whose letterhead prints a foot of its own states that distance in {@code bottom},
     * and the footer of the page moves up with it.
     */
    static final float FOOTER_INSET = 22f;

    /** How far the descenders of the footer reach below that baseline. */
    private static final float FOOTER_DESCENT = 3f;

    /**
     * The smallest bottom margin that still holds the page footer. The footer sits inside
     * the bottom margin rather than in the text area, so a margin narrower than this one
     * would write the invoice number and the page number onto the bottom edge of the
     * paper or past it, on every page.
     */
    static final float MINIMUM_BOTTOM = FOOTER_INSET + FOOTER_DESCENT;

    /**
     * Checks that the page has room for text at all, and that its foot has room for the
     * footer.
     *
     * @throws TemplateException if a margin is negative, or the bottom margin is smaller
     *                           than {@link #MINIMUM_BOTTOM}
     */
    Margins {
        if (left < 0 || right < 0 || top < 0 || bottom < 0) {
            throw new TemplateException("a page margin is a number of points and not negative");
        }
        if (bottom < MINIMUM_BOTTOM) {
            throw new TemplateException("margins: bottom is " + points(bottom) + " and the"
                    + " page footer — the invoice number and the page number — sits inside"
                    + " the bottom margin, " + points(FOOTER_INSET) + " points below its"
                    + " top, so a bottom margin smaller than " + points(MINIMUM_BOTTOM)
                    + " points would put it onto the bottom edge of the paper or past it");
        }
    }

    /** Returns a measurement the way a message names it: without a trailing zero. */
    private static String points(float value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * Returns the margins of the generic layout.
     *
     * @return the default margins
     */
    static Margins defaults() {
        return new Margins(DEFAULT_SIDE, DEFAULT_SIDE, DEFAULT_TOP, DEFAULT_BOTTOM);
    }

    /**
     * Returns these margins with the ones a template states put over them, member by
     * member, so that a template that names only a top margin keeps the rest.
     *
     * @param top    the top margin, or {@code null} to keep this one
     * @param bottom the bottom margin, or {@code null} to keep this one
     * @param left   the left margin, or {@code null} to keep this one
     * @param right  the right margin, or {@code null} to keep this one
     * @return the margins
     */
    Margins with(Float top, Float bottom, Float left, Float right) {
        return new Margins(left == null ? this.left : left,
                right == null ? this.right : right,
                top == null ? this.top : top,
                bottom == null ? this.bottom : bottom);
    }
}
