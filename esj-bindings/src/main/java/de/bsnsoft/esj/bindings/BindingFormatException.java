package de.bsnsoft.esj.bindings;

/**
 * Signals that something this module was handed is not what it claims to be: a document
 * that is not well-formed XML this module accepts, a binding table that says something
 * the reader cannot represent, or — where the reader was asked to be strict — a value
 * that does not spell the semantic data type its term declares.
 */
public final class BindingFormatException extends BindingException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public BindingFormatException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    public BindingFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
