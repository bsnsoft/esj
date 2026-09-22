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
 * <p>The array is the one this class holds and is not copied on the way out; a caller
 * that modifies it modifies what every later reader of the same attachment sees.
 *
 * @param bytes     the decoded content, at most the bound this reader runs
 * @param truncated whether the stream carried more than the bound
 */
public record AttachmentContent(byte[] bytes, boolean truncated) {

    /**
     * Checks that the content is present.
     *
     * @param bytes     the decoded content, at most the bound this reader runs
     * @param truncated whether the stream carried more than the bound
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public AttachmentContent {
        Objects.requireNonNull(bytes, "bytes");
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
