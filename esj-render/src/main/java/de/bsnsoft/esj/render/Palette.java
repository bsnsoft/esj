package de.bsnsoft.esj.render;

import java.util.Objects;

/**
 * The six colours a PDF rendering draws with.
 *
 * <p>The generic layout uses the greys it has always used, and {@link #defaults()} is
 * exactly those greys: a rendering without a template is the same file it was before this
 * class existed. A branded template replaces any of the six, and nothing else about the
 * layout changes with them — a colour scheme is a colour scheme and not a second layout.
 *
 * @param text            the text of a value, and of anything else that is not a label
 * @param muted           a label, a detail line under a table row, the page footer
 * @param rule            a hairline between two blocks
 * @param heading         a section heading and the invoice number at the head of the page
 * @param tableHeaderFill the band a table's column header sits on
 * @param tableHeaderText the column header itself
 */
record Palette(Ink text, Ink muted, Ink rule, Ink heading, Ink tableHeaderFill,
               Ink tableHeaderText) {

    /** The grey a label is written in. */
    private static final float LABEL_GREY = 0.38f;

    /** The grey of a rule between two blocks. */
    private static final float RULE_GREY = 0.72f;

    /** The grey a table header sits on. */
    private static final float HEADER_FILL_GREY = 0.91f;

    /**
     * Checks every colour.
     *
     * @throws NullPointerException if one is {@code null}
     */
    Palette {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(muted, "muted");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(tableHeaderFill, "tableHeaderFill");
        Objects.requireNonNull(tableHeaderText, "tableHeaderText");
    }

    /**
     * Returns the greys of the generic layout.
     *
     * @return the default palette
     */
    static Palette defaults() {
        return new Palette(Ink.BLACK, Ink.grey(LABEL_GREY), Ink.grey(RULE_GREY), Ink.BLACK,
                Ink.grey(HEADER_FILL_GREY), Ink.BLACK);
    }
}
