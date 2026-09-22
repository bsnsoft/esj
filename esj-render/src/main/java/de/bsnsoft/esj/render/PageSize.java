package de.bsnsoft.esj.render;

/**
 * The paper a PDF rendering is laid out for.
 *
 * <p>The two sizes here are the two an invoice is printed on in practice, and the layout
 * is written to work on either: it measures its columns against the width of the page it
 * was given rather than against a fixed grid, and a table that does not fit on one page
 * continues on the next with its header repeated. Nothing else about the rendering
 * depends on the choice.
 *
 * <p>The measurements are in PostScript points, {@code 1/72} of an inch, which is the unit
 * a PDF is written in.
 */
public enum PageSize {

    /** ISO A4, 210 mm × 297 mm, the size an invoice is printed on in Europe. */
    A4(595.276f, 841.89f),

    /** US Letter, 8.5 in × 11 in. */
    LETTER(612f, 792f);

    private final float width;
    private final float height;

    PageSize(float width, float height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Returns the width of the page in points.
     *
     * @return the width
     */
    public float width() {
        return width;
    }

    /**
     * Returns the height of the page in points.
     *
     * @return the height
     */
    public float height() {
        return height;
    }
}
