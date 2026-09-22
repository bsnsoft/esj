package de.bsnsoft.esj.pdf;

/**
 * Base class of the unchecked exceptions this module throws.
 *
 * <p>A container this module cannot get an invoice out of raises one of these, and each
 * of them answers a different question, because the four answers do not mean the same
 * thing to the caller that has to act on them:
 *
 * <ul>
 *   <li>the bytes are no PDF this module can read, or the file is encrypted in a way that
 *       keeps it shut ({@link PdfFormatException}, {@link PdfAccessException}) — the
 *       input is unusable;</li>
 *   <li>the container carries no invoice, or more than one and no instruction which
 *       ({@link NoInvoiceAttachmentException},
 *       {@link AmbiguousInvoiceAttachmentException}) — the input is readable and the
 *       question is not answerable from it alone;</li>
 *   <li>the container carries an invoice in a format this project has decided not to
 *       support ({@link UnsupportedInvoiceException}) — nothing about the file is
 *       wrong;</li>
 *   <li>a bound of {@link PdfLimits} was reached ({@link PdfLimitException}) — this
 *       reader, as configured, declines to go on, which is a statement about the
 *       configuration and not about the file, and a caller must never present it as a
 *       verdict on the invoice.</li>
 * </ul>
 *
 * <p>{@link EmbedRefusedException} is the one of these that is not about reading: it says
 * that a file this module read is not a file it will write an invoice into.
 */
public abstract sealed class PdfException extends RuntimeException
        permits AmbiguousInvoiceAttachmentException,
                EmbedRefusedException,
                NoInvoiceAttachmentException,
                PdfAccessException,
                PdfFormatException,
                PdfLimitException,
                UnsupportedInvoiceException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    protected PdfException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    protected PdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
