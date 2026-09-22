package de.bsnsoft.esj.syntax;

/**
 * Signals that a validation pack cannot be used: its manifest is not a manifest this
 * module reads, a file it names is not there, or a pack that was asked for by name is
 * not packaged.
 *
 * <p>It is not a verdict about a document. A pack is part of the artefact rather than of
 * the input, so a failure here is a failure of the installation and never an invalid
 * invoice.
 */
public final class PackException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public PackException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    public PackException(String message, Throwable cause) {
        super(message, cause);
    }
}
