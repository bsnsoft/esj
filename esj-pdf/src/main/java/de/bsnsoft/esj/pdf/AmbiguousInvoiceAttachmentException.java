package de.bsnsoft.esj.pdf;

import java.util.List;
import java.util.Objects;

/**
 * Signals that a PDF carries more than one attachment whose bytes spell an electronic
 * invoice, and that nothing said which of them to read.
 *
 * <p>Picking the first match is what an extraction library may do and what a validator
 * must not: two attachments are two invoices, one of them may be valid and the other not,
 * and a tool that prints one verdict has to say which document the verdict is about. So
 * this module never chooses silently. The candidates travel with the exception so that a
 * caller can list them and ask.
 */
public final class AmbiguousInvoiceAttachmentException extends PdfException {

    private static final long serialVersionUID = 1L;

    /**
     * The attachments whose bytes spell an invoice.
     *
     * <p>Transient because an attachment holds a decoded stream of an open document and
     * is not serializable; an exception of this class that has travelled through
     * serialization carries its message and no candidates.
     */
    private final transient List<LocatedAttachment> candidates;

    /**
     * Creates an exception that names the candidates.
     *
     * @param message    the detail message, in English
     * @param candidates the attachments whose bytes spell an invoice, at least two
     * @throws NullPointerException if an argument is {@code null}
     */
    public AmbiguousInvoiceAttachmentException(String message,
                                               List<LocatedAttachment> candidates) {
        super(message);
        this.candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    }

    /**
     * Returns the attachments a caller has to choose between.
     *
     * @return the candidates, in the order they were enumerated
     */
    public List<LocatedAttachment> candidates() {
        return candidates;
    }
}
