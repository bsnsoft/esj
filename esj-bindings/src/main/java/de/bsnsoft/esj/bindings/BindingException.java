package de.bsnsoft.esj.bindings;

/**
 * Base class of the unchecked exceptions this module throws.
 *
 * <p>A document that cannot be read at all raises an exception: its root element belongs
 * to no syntax this module reads ({@link BindingSyntaxException}), it is not well-formed
 * XML this module accepts, or a binding table is malformed, or a value does not spell its
 * semantic data type and the reader was asked to be strict about that
 * ({@link BindingFormatException}), or it asks for more bytes, more nesting or more
 * buffer than the reader grants it ({@link BindingLimitException}). Everything a document
 * contains that the reader could not place is not an exception but a note in the report.
 *
 * <p>A writer raises one more: a document of an edition of the semantic model that its
 * binding table was not written against ({@link BindingEditionException}). That one is
 * about the document as a whole rather than about anything in it.
 */
public abstract sealed class BindingException extends RuntimeException
        permits BindingEditionException, BindingFormatException, BindingLimitException,
                BindingSyntaxException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    protected BindingException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    protected BindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
