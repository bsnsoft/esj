package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.xr.XrImporter;

/**
 * What a reader of a PDF container is willing to spend on one file.
 *
 * <p>These are the reading party's policy and not a property of any document, in exactly
 * the sense of the specification, section 3.1: a container that outgrows one of them is
 * refused because processing it would cost more than its recipient agreed to spend, not
 * because anything about it is wrong. A refusal therefore says <em>not processed under
 * this configuration</em>, and a caller that meets one is right to pass the file on to a
 * party configured with more rather than to call it invalid.
 *
 * <p>The defaults are chosen against the shape of a real hybrid invoice. A Factur-X file
 * of a few pages with one XML attachment is well under a mebibyte; the bound on the PDF
 * sits far above that because a PDF also carries fonts and images that have nothing to do
 * with the invoice, while the bound on a decoded attachment is the bound the XML importer
 * already runs, because the attachment is the input of that importer and a larger one
 * would only be refused one step later.
 *
 * @param maxPdfBytes             the largest PDF to open, in bytes of the file
 * @param maxEmbeddedFiles        how many attachments are enumerated at all
 * @param maxAttachmentBytes      the largest attachment to decode, in bytes after the
 *                                stream filters have run
 * @param maxTotalAttachmentBytes how much decoded attachment content one container may
 *                                produce in total
 * @param maxXmpBytes             the largest XMP packet to read, in bytes
 * @param maxDecodedBytes         how many decoded bytes one container may produce in
 *                                total, counting the streams the library decodes to find
 *                                the objects of the file and the streams this reader
 *                                decodes itself
 * @param maxObjectStreamObjects  how many objects the object streams of one container may
 *                                declare together
 */
public record PdfLimits(long maxPdfBytes,
                        int maxEmbeddedFiles,
                        long maxAttachmentBytes,
                        long maxTotalAttachmentBytes,
                        long maxXmpBytes,
                        long maxDecodedBytes,
                        long maxObjectStreamObjects) {

    /** The default bound on the PDF itself: 64 mebibytes. */
    public static final long DEFAULT_MAX_PDF_BYTES = 64L * 1024L * 1024L;

    /** The default number of attachments enumerated: 64. */
    public static final int DEFAULT_MAX_EMBEDDED_FILES = 64;

    /**
     * The default bound on one decoded attachment: the bound the XML importer of
     * {@code esj-xr} reads within, because that importer is where the attachment goes.
     */
    public static final long DEFAULT_MAX_ATTACHMENT_BYTES = XrImporter.DEFAULT_MAX_INPUT_BYTES;

    /**
     * The default bound on all decoded attachments of one container together: four times
     * the bound on one of them. A hybrid invoice carries one invoice attachment and
     * sometimes a handful of small ones beside it; a file that decodes four full-size
     * attachments is not one.
     */
    public static final long DEFAULT_MAX_TOTAL_ATTACHMENT_BYTES =
            4L * DEFAULT_MAX_ATTACHMENT_BYTES;

    /** The default bound on the XMP packet: one mebibyte. */
    public static final long DEFAULT_MAX_XMP_BYTES = 1024L * 1024L;

    /**
     * The default bound on everything one container decodes: the bound on the file
     * itself.
     *
     * <p>A PDF holds the objects that find its own objects in compressed streams, and the
     * library decodes those while the file is opened. A container whose own scaffolding
     * decodes to more than the whole file is allowed to be is not an invoice, so the two
     * bounds are the same number by default.
     */
    public static final long DEFAULT_MAX_DECODED_BYTES = DEFAULT_MAX_PDF_BYTES;

    /**
     * The default bound on the objects the object streams of one container declare
     * together: a quarter of a million.
     *
     * <p>The bound on decoded bytes does not reach this, because the cost of an object is
     * not the bytes it was written with: a stream of three million empty strings decodes
     * to fifty-five megabytes and builds three million objects, which is an order of
     * magnitude more memory than the bytes it was measured by. The number an object stream
     * declares stands in its dictionary and is read at the same moment the stream is
     * measured, so the bound costs nothing to hold.
     *
     * <p>A quarter of a million is far above the shape of a hybrid invoice — a document of
     * a few hundred pages holds indirect objects in the thousands — and far below what a
     * file inside the byte bound can declare.
     */
    public static final long DEFAULT_MAX_OBJECT_STREAM_OBJECTS = 250_000L;

    private static final PdfLimits DEFAULTS = new PdfLimits(
            DEFAULT_MAX_PDF_BYTES,
            DEFAULT_MAX_EMBEDDED_FILES,
            DEFAULT_MAX_ATTACHMENT_BYTES,
            DEFAULT_MAX_TOTAL_ATTACHMENT_BYTES,
            DEFAULT_MAX_XMP_BYTES,
            DEFAULT_MAX_DECODED_BYTES,
            DEFAULT_MAX_OBJECT_STREAM_OBJECTS);

    /**
     * Checks that every bound is positive.
     *
     * <p>A bound of zero or less describes no container at all, so it is a defect in the
     * call rather than in any file, and it is refused where it is given rather than when
     * a document arrives (specification, section 12.2).
     *
     * @param maxPdfBytes             the largest PDF to open, in bytes of the file
     * @param maxEmbeddedFiles        how many attachments are enumerated at all
     * @param maxAttachmentBytes      the largest attachment to decode, in bytes after the
     *                                stream filters have run
     * @param maxTotalAttachmentBytes how much decoded attachment content one container may
     *                                produce in total
     * @param maxXmpBytes             the largest XMP packet to read, in bytes
     * @param maxDecodedBytes         how many decoded bytes one container may produce in
     *                                total, counting the streams the library decodes to find
     *                                the objects of the file and the streams this reader
     *                                decodes itself
     * @param maxObjectStreamObjects  how many objects the object streams of one container may
     *                                declare together
     * @throws IllegalArgumentException if a bound is not positive
     */
    public PdfLimits {
        positive(maxPdfBytes, "maxPdfBytes");
        positive(maxEmbeddedFiles, "maxEmbeddedFiles");
        positive(maxAttachmentBytes, "maxAttachmentBytes");
        positive(maxTotalAttachmentBytes, "maxTotalAttachmentBytes");
        positive(maxXmpBytes, "maxXmpBytes");
        positive(maxDecodedBytes, "maxDecodedBytes");
        positive(maxObjectStreamObjects, "maxObjectStreamObjects");
    }

    /**
     * Returns the defaults, which are the constants of this class.
     *
     * @return the default limits
     */
    public static PdfLimits defaults() {
        return DEFAULTS;
    }

    /**
     * Returns these limits with another bound on the PDF itself.
     *
     * @param bytes the largest PDF to open
     * @return the limits
     * @throws IllegalArgumentException if the bound is not positive
     */
    public PdfLimits withMaxPdfBytes(long bytes) {
        return new PdfLimits(bytes, maxEmbeddedFiles, maxAttachmentBytes,
                maxTotalAttachmentBytes, maxXmpBytes, maxDecodedBytes,
                maxObjectStreamObjects);
    }

    /**
     * Returns these limits with another bound on the number of attachments enumerated.
     *
     * @param files how many attachments to enumerate
     * @return the limits
     * @throws IllegalArgumentException if the bound is not positive
     */
    public PdfLimits withMaxEmbeddedFiles(int files) {
        return new PdfLimits(maxPdfBytes, files, maxAttachmentBytes,
                maxTotalAttachmentBytes, maxXmpBytes, maxDecodedBytes,
                maxObjectStreamObjects);
    }

    /**
     * Returns these limits with another bound on one decoded attachment.
     *
     * @param bytes the largest attachment to decode
     * @return the limits
     * @throws IllegalArgumentException if the bound is not positive
     */
    public PdfLimits withMaxAttachmentBytes(long bytes) {
        return new PdfLimits(maxPdfBytes, maxEmbeddedFiles, bytes,
                maxTotalAttachmentBytes, maxXmpBytes, maxDecodedBytes,
                maxObjectStreamObjects);
    }

    /**
     * Returns these limits with another bound on all decoded attachments together.
     *
     * @param bytes how much decoded content one container may produce
     * @return the limits
     * @throws IllegalArgumentException if the bound is not positive
     */
    public PdfLimits withMaxTotalAttachmentBytes(long bytes) {
        return new PdfLimits(maxPdfBytes, maxEmbeddedFiles, maxAttachmentBytes,
                bytes, maxXmpBytes, maxDecodedBytes, maxObjectStreamObjects);
    }

    /**
     * Returns these limits with another bound on the XMP packet.
     *
     * @param bytes the largest XMP packet to read
     * @return the limits
     * @throws IllegalArgumentException if the bound is not positive
     */
    public PdfLimits withMaxXmpBytes(long bytes) {
        return new PdfLimits(maxPdfBytes, maxEmbeddedFiles, maxAttachmentBytes,
                maxTotalAttachmentBytes, bytes, maxDecodedBytes, maxObjectStreamObjects);
    }

    /**
     * Returns these limits with another bound on everything one container decodes.
     *
     * @param bytes how many decoded bytes one container may produce in total
     * @return the limits
     * @throws IllegalArgumentException if the bound is not positive
     */
    public PdfLimits withMaxDecodedBytes(long bytes) {
        return new PdfLimits(maxPdfBytes, maxEmbeddedFiles, maxAttachmentBytes,
                maxTotalAttachmentBytes, maxXmpBytes, bytes, maxObjectStreamObjects);
    }

    /**
     * Returns these limits with another bound on the objects the object streams of one
     * container declare together.
     *
     * @param objects how many objects one container may declare
     * @return the limits
     * @throws IllegalArgumentException if the bound is not positive
     */
    public PdfLimits withMaxObjectStreamObjects(long objects) {
        return new PdfLimits(maxPdfBytes, maxEmbeddedFiles, maxAttachmentBytes,
                maxTotalAttachmentBytes, maxXmpBytes, maxDecodedBytes, objects);
    }

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " is a positive number of bytes");
        }
    }
}
