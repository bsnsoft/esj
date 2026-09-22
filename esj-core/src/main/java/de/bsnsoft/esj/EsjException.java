package de.bsnsoft.esj;

/**
 * Base class of the unchecked exceptions this implementation throws.
 *
 * <p>Invalid user data is reported as findings, not as exceptions
 * (specification, section 9.5). An {@code EsjException} therefore signals one of two
 * things: a value or path that this implementation was asked to construct although it
 * cannot exist in a conformant document ({@link EsjFormatException}), or a resource
 * bound that was reached ({@link EsjLimitException}). Input/output failures keep their
 * own exception types.
 */
public abstract sealed class EsjException extends RuntimeException
        permits EsjFormatException, EsjLimitException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    protected EsjException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    protected EsjException(String message, Throwable cause) {
        super(message, cause);
    }
}
