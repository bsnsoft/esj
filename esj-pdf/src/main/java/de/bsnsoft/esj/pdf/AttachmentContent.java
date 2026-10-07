package de.bsnsoft.esj.pdf;

import java.util.Objects;

/**
 * The decoded bytes of one attachment, and whether there were more of them than this
 * reader was willing to hold.
 *
 * <p>A PDF stream is compressed, so its length in the file says nothing about its length
 * once decoded: a few kilobytes of Flate can inflate to gigabytes. The decoding therefore
 * stops at the bound of {@link PdfLimits#maxAttachmentBytes()} and says that it stopped,
 * rather than buffering whatever arrives. A truncated attachment is never imported: half
 * an invoice is not an invoice, and the caller is told that a bound was met rather than
 * handed a document that looks complete.
 *
 * <p>{@link #bytes()} returns a copy of the content on every call, so that a caller cannot
 * change what a later reader of the same attachment sees.
 */
public final class AttachmentContent {

    private final byte[] bytes;
    private final boolean truncated;

    /**
     * Creates the content over an array the decoder wrote and hands over.
     *
     * @param bytes     the decoded content, at most the bound this reader runs; not copied
     * @param truncated whether the stream carried more than the bound
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    AttachmentContent(byte[] bytes, boolean truncated) {
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.truncated = truncated;
    }

    /**
     * Returns the decoded content, at most the bound this reader runs.
     *
     * @return a fresh copy of the bytes
     */
    public byte[] bytes() {
        return bytes.clone();
    }

    /**
     * Returns the content without copying it, for the readers of this package.
     *
     * @return the array this object holds
     */
    byte[] array() {
        return bytes;
    }

    /**
     * Tells whether the stream carried more than the bound.
     *
     * @return {@code true} if the content was cut off at the bound
     */
    public boolean truncated() {
        return truncated;
    }

    /**
     * Returns how many bytes were decoded.
     *
     * @return the length of the content
     */
    public int length() {
        return bytes.length;
    }

    /**
     * Returns a description for a message: the length, and whether it is the whole of the
     * attachment.
     *
     * @return one phrase in English
     */
    @Override
    public String toString() {
        return truncated ? bytes.length + " bytes and cut off there" : bytes.length + " bytes";
    }
}
