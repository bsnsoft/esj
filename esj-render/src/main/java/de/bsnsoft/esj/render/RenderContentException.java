package de.bsnsoft.esj.render;

/**
 * The content of a value stopped the rendering.
 *
 * <p>This is the one {@link RenderException} that is a statement about the document. The
 * HTML rendering is produced by a stylesheet that reads a typed value as the type says —
 * an amount as a number, a date as a date — and a content that is not a lexical form of
 * that type stops it where a renderer of this project's own would print it as it stands.
 * A document is read before it is rendered but not validated, so such a value reaches a
 * renderer, and {@code esj validate} is what says which value it is and why.
 *
 * <p>A caller that shows renderings of documents it did not write catches this and says
 * that the document could not be rendered, rather than reporting a defect of the tool.
 * The message of the engine underneath is the cause, and it names the value.
 */
public class RenderContentException extends RenderException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message and the failure underneath.
     *
     * @param message what could not be produced
     * @param cause   the failure of the engine, whose message names the value
     */
    public RenderContentException(String message, Throwable cause) {
        super(message, cause);
    }
}
