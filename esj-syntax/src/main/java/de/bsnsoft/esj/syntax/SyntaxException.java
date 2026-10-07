package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.EsjException;
import de.bsnsoft.esj.EsjLimitException;

/**
 * Base class of the unchecked exceptions the syntax engine throws.
 *
 * <p>An exception here is never a verdict about the document. What is wrong with a
 * document is a finding, and findings are returned; an exception says that no verdict was
 * reached because the document is one no pack of this engine binds
 * ({@link SyntaxNotSupportedException}). A run that hit a limit it was given reached no
 * verdict either, and ends in an {@link EsjLimitException}, the type every module of this
 * project raises for a bound. A caller that turned one of these into "invalid" would be
 * reporting its own configuration as a fault of the sender.
 */
public abstract sealed class SyntaxException extends EsjException
        permits SyntaxNotSupportedException {

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
