package de.bsnsoft.esj.render;

/**
 * The machinery of a rendering failed: a resource of this module is not on the classpath,
 * a vendored font or profile could not be read, or the engine underneath could not write
 * what it was asked to.
 *
 * <p>It is no statement about the invoice and none about the caller's template. A caller
 * that meets it has met a defect of this project or of the installation it runs in, and
 * the cause names the failure underneath.
 */
public final class RenderEngineException extends RenderException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message what could not be produced, and why
     */
    public RenderEngineException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message what could not be produced, and why
     * @param cause   the failure underneath
     */
    public RenderEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
