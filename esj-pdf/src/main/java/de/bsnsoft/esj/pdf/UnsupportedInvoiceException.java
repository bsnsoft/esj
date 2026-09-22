package de.bsnsoft.esj.pdf;

import java.util.Objects;

/**
 * Signals that the invoice a container carries is written in a format this project has
 * decided not to support.
 *
 * <p>The one case in this release is ZUGFeRD 1.0, whose attachment
 * {@code ZUGFeRD-invoice.xml} carries the root element {@code CrossIndustryDocument} of
 * CII D14B. It is not a binding of EN 16931 — it predates the standard — so nothing in
 * this project could map it without inventing a semantic model for it, and inventing one
 * is not what this project is for. The format is recognized, named and refused, which is
 * a different answer from "no invoice here" and is worth its own exception and its own
 * exit code.
 */
public final class UnsupportedInvoiceException extends PdfException {

    private static final long serialVersionUID = 1L;

    /**
     * The attachment that carries the unsupported invoice.
     *
     * <p>Transient because an attachment holds a decoded stream of an open document and
     * is not serializable; an exception of this class that has travelled through
     * serialization carries its message and no attachment.
     */
    private final transient LocatedAttachment attachment;

    /**
     * Creates an exception that names the attachment.
     *
     * @param message    the detail message, in English
     * @param attachment the attachment that carries the unsupported invoice
     * @throws NullPointerException if an argument is {@code null}
     */
    public UnsupportedInvoiceException(String message, LocatedAttachment attachment) {
        super(message);
        this.attachment = Objects.requireNonNull(attachment, "attachment");
    }

    /**
     * Returns the attachment that carries the unsupported invoice.
     *
     * @return the attachment
     */
    public LocatedAttachment attachment() {
        return attachment;
    }
}
