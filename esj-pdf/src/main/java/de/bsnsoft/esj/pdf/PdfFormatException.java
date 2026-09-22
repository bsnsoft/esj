package de.bsnsoft.esj.pdf;

/**
 * Signals that a byte sequence is no PDF this module can read: it does not begin with the
 * file header, it is truncated, its object structure is damaged past what PDFBox can
 * recover, or its catalog is not there.
 *
 * <p>The message describes what went wrong and does not reproduce the file, because the
 * content of a document does not get to decide how long a log line is.
 */
public final class PdfFormatException extends PdfException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public PdfFormatException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    public PdfFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
