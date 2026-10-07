package de.bsnsoft.esj.render;

import de.bsnsoft.esj.EsjException;

/**
 * A rendering could not be produced.
 *
 * <p>This is not a statement about the invoice. A document that is wrong in some way still
 * renders — a renderer that refused an invoice because a mandatory term was missing would
 * be a validator, and this project has one of those. Three things stop a rendering, and
 * each has a type of its own, because the caller does something different about each:
 *
 * <ul>
 *   <li>the machinery failed: a resource of this module is not on the classpath, or the
 *       engine could not write what it was asked to ({@link RenderEngineException}) —
 *       a defect of this project or of its installation;</li>
 *   <li>a branded template could not be read ({@link TemplateException}) — the caller's
 *       configuration;</li>
 *   <li>the content of a value stopped the stylesheet of the HTML rendering
 *       ({@link RenderContentException}) — the one statement about the document.</li>
 * </ul>
 *
 * <p>A rendering that reaches a bound of the run — the pages of a PDF, the bytes of an
 * HTML page — is not of this type. It ends in an {@link de.bsnsoft.esj.EsjLimitException},
 * the type every module of this project raises for a bound, because a limit is the policy
 * of the party that renders and no statement about the invoice.
 */
public abstract sealed class RenderException extends EsjException
        permits RenderContentException, RenderEngineException, TemplateException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message what could not be produced, and why
     */
    protected RenderException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message what could not be produced, and why
     * @param cause   the failure underneath
     */
    protected RenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
