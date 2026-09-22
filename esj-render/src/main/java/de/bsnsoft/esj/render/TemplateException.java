package de.bsnsoft.esj.render;

/**
 * A branded template could not be read.
 *
 * <p>It is a statement about the template and never about the invoice: a member that is
 * not what the schema says, a file the template refers to that is not beside it, a colour
 * that is not a colour, a letterhead that is neither a PDF nor an image this renderer
 * reads. A template belongs to the caller rather than to the sender of an invoice, so this
 * is a configuration error and the message names the member it is about.
 */
public class TemplateException extends RenderException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message what is wrong with the template
     */
    public TemplateException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message what is wrong with the template
     * @param cause   the failure underneath
     */
    public TemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
