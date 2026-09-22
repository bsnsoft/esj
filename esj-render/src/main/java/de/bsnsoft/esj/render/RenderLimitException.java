package de.bsnsoft.esj.render;

/**
 * A rendering reached a resource bound of the run that asked for it.
 *
 * <p>The bound is the number of pages. A renderer draws until the document is drawn, and
 * the cost of a rendering is not the cost of reading the document it came from: one value
 * of a megabyte in a narrow column is hundreds of pages, and an invoice number of half a
 * megabyte stands in the footer of every one of them. Both documents are inside every
 * bound of a reader, because those bounds are about the input.
 *
 * <p>Like every other limit of this project it is <b>policy of the party that renders and
 * no statement about the invoice</b>: the same document renders under a configuration that
 * allows more pages, and both configurations are right. A caller that turns this into an
 * exit code uses the one it uses for every other limit — 7 in the command line tool — and
 * not a verdict.
 */
public class RenderLimitException extends RenderException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message which bound was reached, and at which number
     */
    public RenderLimitException(String message) {
        super(message);
    }
}
