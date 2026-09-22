package de.bsnsoft.esj.syntax;

/**
 * Signals that the engine has no artefacts for this document, and therefore reached no
 * verdict.
 *
 * <p>Two documents raise it: one whose root element belongs to neither of the syntaxes
 * the standard binds, and one of such a syntax for which the selected pack carries no
 * component at all. Neither is an invalid invoice; both are requests this installation
 * cannot serve.
 */
public final class SyntaxNotSupportedException extends SyntaxException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public SyntaxNotSupportedException(String message) {
        super(message);
    }
}
