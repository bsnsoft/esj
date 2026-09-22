package de.bsnsoft.esj.typed;

import java.util.Arrays;
import java.util.Objects;

/**
 * An embedded file as the typed view hands it over: the decoded bytes, the media type and
 * the file name (specification, section 6.7). Both components are mandatory in the
 * standard and therefore here.
 *
 * <p>The document carries the file as canonical base64; this record carries what that
 * base64 decodes to. The caller receives a copy of the bytes and may do what it likes
 * with them, except open, render or execute what they hold: an attachment is a file a
 * stranger sent (specification, section 12.5).
 *
 * @param bytes    the content of the file, never empty
 * @param mimeCode the media type of the file
 * @param filename the file name of the file
 */
public record BinaryObject(byte[] bytes, String mimeCode, String filename) {

    /**
     * Copies the bytes and checks that every part is present.
     *
     * @param bytes    the content of the file, never empty
     * @param mimeCode the media type of the file
     * @param filename the file name of the file
     * @throws NullPointerException if a part is {@code null}
     */
    public BinaryObject {
        bytes = Objects.requireNonNull(bytes, "bytes").clone();
        Objects.requireNonNull(mimeCode, "mimeCode");
        Objects.requireNonNull(filename, "filename");
    }

    /**
     * Returns the content of the file.
     *
     * @return a fresh copy of the bytes
     */
    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    /**
     * Compares the content and both components, so that two records the same file would
     * produce are equal.
     *
     * @param obj the object to compare with
     * @return {@code true} if the other object is a binary object with the same content
     *         and the same components
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof BinaryObject other
                && Arrays.equals(bytes, other.bytes)
                && mimeCode.equals(other.mimeCode)
                && filename.equals(other.filename);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code of the content and both components
     */
    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(bytes), mimeCode, filename);
    }

    /**
     * Returns a description that names the components and the size of the file rather
     * than its content: an attachment may be thirty megabytes, and a description of it
     * reaches a log line (specification, section 12.6).
     *
     * @return a short description of the file
     */
    @Override
    public String toString() {
        return "BinaryObject[" + bytes.length + " bytes, mimeCode=\"" + mimeCode
                + "\", filename=\"" + filename + "\"]";
    }
}
