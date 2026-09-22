package de.bsnsoft.esj.pdf;

/**
 * Signals that a PDF is encrypted, and is therefore not read as a container of an
 * electronic invoice.
 *
 * <p>The refusal is outright and comes before anything is decrypted, including for a file
 * that would open with the empty user password. PDF/A forbids encryption — ISO 19005
 * allows no {@code /Encrypt} key in the trailer — and a hybrid invoice is a PDF/A file, so
 * an encrypted container is not one. This module takes no password either, so that no part
 * of this project ever holds one.
 */
public final class PdfAccessException extends PdfException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public PdfAccessException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    public PdfAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
