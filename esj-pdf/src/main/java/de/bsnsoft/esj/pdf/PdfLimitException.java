package de.bsnsoft.esj.pdf;

/**
 * Signals that a container asks for more than the {@link PdfLimits} it was opened with
 * grant it.
 *
 * <p>Every message names the bound that was met, so that a caller can decide between
 * running again with more and passing the file on to a party that reads more. A refusal
 * with this exception is never a verdict on the invoice: the reader stopped, it did not
 * finish judging (specification, section 3.1).
 */
public final class PdfLimitException extends PdfException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public PdfLimitException(String message) {
        super(message);
    }
}
