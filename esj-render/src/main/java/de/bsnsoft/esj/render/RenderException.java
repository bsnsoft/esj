package de.bsnsoft.esj.render;

/**
 * A rendering could not be produced.
 *
 * <p>This is not a statement about the invoice. A document that is wrong in some way still
 * renders — a renderer that refused an invoice because a mandatory term was missing would
 * be a validator, and this project has one of those. What this exception reports is that
 * the machinery failed: a resource of this module is not on the classpath, or the
 * stylesheet raised an error while it ran.
 */
public class RenderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message what could not be produced, and why
     */
    public RenderException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message what could not be produced, and why
     * @param cause   the failure underneath
     */
    public RenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
