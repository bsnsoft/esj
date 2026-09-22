package de.bsnsoft.esj.syntax;

/**
 * Base class of the unchecked exceptions the syntax engine throws.
 *
 * <p>An exception here is never a verdict about the document. What is wrong with a
 * document is a finding, and findings are returned; an exception says that no verdict was
 * reached — the run hit a limit it was given ({@link SyntaxLimitException}), or the
 * document is one no pack of this engine binds
 * ({@link SyntaxNotSupportedException}). A caller that turned one of these into
 * "invalid" would be reporting its own configuration as a fault of the sender.
 */
public abstract sealed class SyntaxException extends RuntimeException
        permits SyntaxLimitException, SyntaxNotSupportedException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    protected SyntaxException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    protected SyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
