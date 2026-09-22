package de.bsnsoft.esj.pdf;

import java.util.List;
import java.util.Objects;

/**
 * Signals that a PDF carries no attachment whose bytes spell an electronic invoice.
 *
 * <p>This is the third of the three cases a PDF falls into, and the one where the answer
 * is that there is nothing to validate. The page may well show an invoice; a person reads
 * it and a machine does not, and this project does not guess at what the page says. The
 * attachments that <em>are</em> there are carried along, because "no invoice, and here is
 * what the file does hold" is the answer a user can act on.
 */
public final class NoInvoiceAttachmentException extends PdfException {

    private static final long serialVersionUID = 1L;

    /**
     * What the container did carry.
     *
     * <p>Transient because an attachment holds a decoded stream of an open document and
     * is not serializable; an exception of this class that has travelled through
     * serialization carries its message and no attachments.
     */
    private final transient List<LocatedAttachment> attachments;

    /**
     * The attachments whose content was never established, which is a different answer
     * from "not an invoice"; transient for the reason above.
     */
    private final transient List<LocatedAttachment> unestablished;

    /**
     * Creates an exception that names what the container did carry.
     *
     * @param message       the detail message, in English
     * @param attachments   the attachments that were found, possibly none
     * @param unestablished the ones among them this reader did not classify, possibly
     *                      none
     * @throws NullPointerException if an argument is {@code null}
     */
    public NoInvoiceAttachmentException(String message,
                                        List<LocatedAttachment> attachments,
                                        List<LocatedAttachment> unestablished) {
        super(message);
        this.attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
        this.unestablished =
                List.copyOf(Objects.requireNonNull(unestablished, "unestablished"));
    }

    /**
     * Returns the attachments the container carried, none of which is an invoice.
     *
     * @return the attachments, in the order they were enumerated
     */
    public List<LocatedAttachment> attachments() {
        return attachments;
    }

    /**
     * Returns the attachments this reader did not classify: the ones that begin an XML
     * document whose root element lies beyond the bytes it looks at, and the ones whose
     * stream it does not decode.
     *
     * <p>They are kept apart from the rest because the two say different things. "This
     * attachment is not an invoice" is a statement about the file; "what this attachment
     * holds was not established" is a statement about the reader, and a refusal that
     * mixed the two would assert something nobody checked.
     *
     * @return the unclassified attachments, in the order they were enumerated, possibly
     *         none
     */
    public List<LocatedAttachment> unestablished() {
        return unestablished;
    }
}
