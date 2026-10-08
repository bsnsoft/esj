package de.bsnsoft.esj.json;

import de.bsnsoft.esj.EsjFormatException;
import java.util.Objects;

/**
 * The resource bounds a reader enforces while it parses (specification, section 12.2).
 *
 * <p>Every length is counted in bytes of the UTF-8 encoding, never in characters, code
 * points or UTF-16 code units, so that the same document is acceptable to readers written
 * in different languages. String lengths are measured on the normalized value, after CR
 * LF and a lone CR have become LF (specification, section 6.8).
 *
 * <p>The defaults of {@link #defaults()} are the reference configuration: two
 * implementations that run them classify every byte sequence alike. A reader configured
 * with other bounds is expected to say so.
 *
 * <p>The 64-character bound on a decimal is deliberately not here. It belongs to the
 * decimal grammar of the specification, section 6.4, so it is a structural rule rather
 * than a configurable limit, and a decimal that exceeds it is reported as
 * {@code ESJ-L2-DECIMAL} rather than as {@code ESJ-L1-LIMIT}.
 *
 * <p>Instances are immutable; every {@code with} method returns new limits.
 */
public final class Limits {

    /** One mebibyte, the unit the string bounds of the specification are stated in. */
    private static final long MIB = 1024L * 1024L;

    /**
     * The largest value {@link #maxDocumentBytes()} accepts. A document is held in one
     * {@code byte[]} — {@code read(byte[])} cannot even express a larger one — and a
     * reader that streams reads one byte past its bound to notice an overrun, so the
     * bound plus that byte has to be a length an array can have.
     */
    public static final long MAX_DOCUMENT_BYTES = Integer.MAX_VALUE - 9L;

    /**
     * The levels of nesting the envelope itself occupies around an extension subtree.
     * {@link #maxExtensionDepth()} is counted from the value of an owner-token member
     * (specification, section 12.2), while the document as a whole nests this much
     * deeper: a reader that counts the depth of the whole document, as a stock streaming
     * parser does, counts the sum of the two.
     */
    static final int ENVELOPE_NESTING = 3;

    /**
     * The largest value {@link #maxExtensionDepth()} accepts. The depth of the whole
     * document is the bound plus {@link #ENVELOPE_NESTING}, and that sum has to stay a
     * depth an {@code int} can count — the twin of the reason {@link #MAX_DOCUMENT_BYTES}
     * exists. A bound this large is already far past any document a reader could hold;
     * refusing it here makes the refusal an {@link EsjFormatException} naming the knob,
     * rather than a count that wraps around to a negative depth.
     */
    public static final int MAX_EXTENSION_DEPTH = Integer.MAX_VALUE - ENVELOPE_NESTING;

    private static final Limits DEFAULTS = new Limits(
            64L * MIB, 100_000, 16, 16, 256, MIB, 32L * MIB, 48L * MIB, 32, 100_000);

    private final long maxDocumentBytes;
    private final int maxValues;
    private final int maxValueMembers;
    private final int maxPathSegments;
    private final int maxPathBytes;
    private final long maxStringBytes;
    private final long maxBinaryValueBytes;
    private final long maxTotalBinaryBytes;
    private final int maxExtensionDepth;
    private final int maxExtensionNodes;

    private Limits(long maxDocumentBytes,
                   int maxValues,
                   int maxValueMembers,
                   int maxPathSegments,
                   int maxPathBytes,
                   long maxStringBytes,
                   long maxBinaryValueBytes,
                   long maxTotalBinaryBytes,
                   int maxExtensionDepth,
                   int maxExtensionNodes) {
        positive(maxDocumentBytes, "maxDocumentBytes");
        positive(maxValues, "maxValues");
        positive(maxValueMembers, "maxValueMembers");
        positive(maxPathSegments, "maxPathSegments");
        positive(maxPathBytes, "maxPathBytes");
        positive(maxStringBytes, "maxStringBytes");
        positive(maxBinaryValueBytes, "maxBinaryValueBytes");
        positive(maxTotalBinaryBytes, "maxTotalBinaryBytes");
        positive(maxExtensionDepth, "maxExtensionDepth");
        positive(maxExtensionNodes, "maxExtensionNodes");
        if (maxDocumentBytes > MAX_DOCUMENT_BYTES) {
            throw new EsjFormatException("maxDocumentBytes is at most " + MAX_DOCUMENT_BYTES
                    + ", because a document is held in one byte array, not " + maxDocumentBytes);
        }
        if (maxExtensionDepth > MAX_EXTENSION_DEPTH) {
            throw new EsjFormatException("maxExtensionDepth is at most " + MAX_EXTENSION_DEPTH
                    + ", because the nesting of the envelope adds to it in the depth of the"
                    + " whole document, not " + maxExtensionDepth);
        }
        this.maxDocumentBytes = maxDocumentBytes;
        this.maxValues = maxValues;
        this.maxValueMembers = maxValueMembers;
        this.maxPathSegments = maxPathSegments;
        this.maxPathBytes = maxPathBytes;
        this.maxStringBytes = maxStringBytes;
        this.maxBinaryValueBytes = maxBinaryValueBytes;
        this.maxTotalBinaryBytes = maxTotalBinaryBytes;
        this.maxExtensionDepth = maxExtensionDepth;
        this.maxExtensionNodes = maxExtensionNodes;
    }

    /**
     * Returns the defaults of the specification, section 12.2: 64 MiB per document,
     * 100 000 members of {@code values}, 16 members per value object, 16 segments and
     * 256 bytes per path, 1 MiB per
     * string value and 32 MiB per base64 value, 48 MiB of decoded binary content per
     * document, and 32 levels of nesting and 100 000 nodes inside {@code extensions}.
     *
     * @return the reference configuration
     */
    public static Limits defaults() {
        return DEFAULTS;
    }

    /**
     * Returns the largest document, in bytes of the encoded document, at most
     * {@link #MAX_DOCUMENT_BYTES}.
     *
     * @return the bound
     */
    public long maxDocumentBytes() {
        return maxDocumentBytes;
    }

    /**
     * Returns the largest number of members of {@code values}.
     *
     * @return the bound
     */
    public int maxValues() {
        return maxValues;
    }

    /**
     * Returns the largest number of members of one value object.
     *
     * @return the bound
     */
    public int maxValueMembers() {
        return maxValueMembers;
    }

    /**
     * Returns the largest number of segments of one semantic path, counting index
     * segments.
     *
     * @return the bound
     */
    public int maxPathSegments() {
        return maxPathSegments;
    }

    /**
     * Returns the largest semantic path, in bytes of its UTF-8 encoding.
     *
     * @return the bound
     */
    public int maxPathBytes() {
        return maxPathBytes;
    }

    /**
     * Returns the largest string value, in bytes of the UTF-8 encoding of the normalized
     * value.
     *
     * @return the bound
     */
    public long maxStringBytes() {
        return maxStringBytes;
    }

    /**
     * Returns the largest content of a value carrying a binary component, in bytes of its
     * base64 encoding.
     *
     * @return the bound
     */
    public long maxBinaryValueBytes() {
        return maxBinaryValueBytes;
    }

    /**
     * Returns the largest sum of binary content of one document, in bytes after base64
     * decoding.
     *
     * @return the bound
     */
    public long maxTotalBinaryBytes() {
        return maxTotalBinaryBytes;
    }

    /**
     * Returns the deepest nesting inside {@code extensions}, in levels of object or array
     * below an owner token, at most {@link #MAX_EXTENSION_DEPTH}.
     *
     * @return the bound
     */
    public int maxExtensionDepth() {
        return maxExtensionDepth;
    }

    /**
     * Returns the largest number of nodes inside {@code extensions}, counting every scalar
     * and every container.
     *
     * @return the bound
     */
    public int maxExtensionNodes() {
        return maxExtensionNodes;
    }

    /**
     * Returns these limits with another value of {@link #maxDocumentBytes()}.
     *
     * @param value the bound
     * @return the limits
     * @throws EsjFormatException if {@code value} is zero or negative,
     *                            or exceeds {@link #MAX_DOCUMENT_BYTES}
     */
    public Limits withMaxDocumentBytes(long value) {
        return new Limits(value, maxValues, maxValueMembers, maxPathSegments, maxPathBytes,
                maxStringBytes, maxBinaryValueBytes, maxTotalBinaryBytes,
                maxExtensionDepth, maxExtensionNodes);
    }

    /**
     * Returns these limits with another value of {@link #maxValues()}.
     *
     * @param value the bound
     * @return the limits
     * @throws EsjFormatException if {@code value} is zero or negative
     */
    public Limits withMaxValues(int value) {
        return new Limits(maxDocumentBytes, value, maxValueMembers, maxPathSegments,
                maxPathBytes, maxStringBytes, maxBinaryValueBytes,
                maxTotalBinaryBytes, maxExtensionDepth, maxExtensionNodes);
    }

    /**
     * Returns these limits with another value of {@link #maxValueMembers()}.
     *
     * @param value the bound
     * @return the limits
     * @throws EsjFormatException if {@code value} is zero or negative
     */
    public Limits withMaxValueMembers(int value) {
        return new Limits(maxDocumentBytes, maxValues, value, maxPathSegments, maxPathBytes,
                maxStringBytes, maxBinaryValueBytes, maxTotalBinaryBytes,
                maxExtensionDepth, maxExtensionNodes);
    }

    /**
     * Returns these limits with another value of {@link #maxPathSegments()}.
     *
     * @param value the bound
     * @return the limits
     * @throws EsjFormatException if {@code value} is zero or negative
     */
    public Limits withMaxPathSegments(int value) {
        return new Limits(maxDocumentBytes, maxValues, maxValueMembers, value, maxPathBytes,
                maxStringBytes, maxBinaryValueBytes, maxTotalBinaryBytes,
                maxExtensionDepth, maxExtensionNodes);
    }

    /**
     * Returns these limits with another value of {@link #maxPathBytes()}.
     *
     * @param value the bound
     * @return the limits
     * @throws EsjFormatException if {@code value} is zero or negative
     */
    public Limits withMaxPathBytes(int value) {
        return new Limits(maxDocumentBytes, maxValues, maxValueMembers, maxPathSegments, value,
                maxStringBytes, maxBinaryValueBytes, maxTotalBinaryBytes,
                maxExtensionDepth, maxExtensionNodes);
    }

    /**
     * Returns these limits with another value of {@link #maxStringBytes()}.
     *
     * @param value the bound
     * @return the limits
     * @throws EsjFormatException if {@code value} is zero or negative
     */
    public Limits withMaxStringBytes(long value) {
        return new Limits(maxDocumentBytes, maxValues, maxValueMembers, maxPathSegments,
                maxPathBytes, value, maxBinaryValueBytes, maxTotalBinaryBytes,
                maxExtensionDepth, maxExtensionNodes);
    }

    /**
     * Returns these limits with another value of {@link #maxBinaryValueBytes()}.
     *
     * @param value the bound
     * @return the limits
     * @throws EsjFormatException if {@code value} is zero or negative
     */
    public Limits withMaxBinaryValueBytes(long value) {
        return new Limits(maxDocumentBytes, maxValues, maxValueMembers, maxPathSegments,
                maxPathBytes, maxStringBytes, value, maxTotalBinaryBytes,
                maxExtensionDepth, maxExtensionNodes);
    }

    /**
     * Returns these limits with another value of {@link #maxTotalBinaryBytes()}.
     *
     * @param value the bound
     * @return the limits
     * @throws EsjFormatException if {@code value} is zero or negative
     */
    public Limits withMaxTotalBinaryBytes(long value) {
        return new Limits(maxDocumentBytes, maxValues, maxValueMembers, maxPathSegments,
                maxPathBytes, maxStringBytes, maxBinaryValueBytes, value,
                maxExtensionDepth, maxExtensionNodes);
    }

    /**
     * Returns these limits with another value of {@link #maxExtensionDepth()}.
     *
     * @param value the bound
     * @return the limits
     * @throws EsjFormatException if {@code value} is zero or negative,
     *                            or exceeds {@link #MAX_EXTENSION_DEPTH}
     */
    public Limits withMaxExtensionDepth(int value) {
        return new Limits(maxDocumentBytes, maxValues, maxValueMembers, maxPathSegments,
                maxPathBytes, maxStringBytes, maxBinaryValueBytes,
                maxTotalBinaryBytes, value, maxExtensionNodes);
    }

    /**
     * Returns these limits with another value of {@link #maxExtensionNodes()}.
     *
     * @param value the bound
     * @return the limits
     * @throws EsjFormatException if {@code value} is zero or negative
     */
    public Limits withMaxExtensionNodes(int value) {
        return new Limits(maxDocumentBytes, maxValues, maxValueMembers, maxPathSegments,
                maxPathBytes, maxStringBytes, maxBinaryValueBytes,
                maxTotalBinaryBytes, maxExtensionDepth, value);
    }

    /**
     * Tells whether another object is limits with the same bounds.
     *
     * @param other the object to compare with
     * @return {@code true} if every bound is equal
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Limits that
                && maxDocumentBytes == that.maxDocumentBytes
                && maxValues == that.maxValues
                && maxValueMembers == that.maxValueMembers
                && maxPathSegments == that.maxPathSegments
                && maxPathBytes == that.maxPathBytes
                && maxStringBytes == that.maxStringBytes
                && maxBinaryValueBytes == that.maxBinaryValueBytes
                && maxTotalBinaryBytes == that.maxTotalBinaryBytes
                && maxExtensionDepth == that.maxExtensionDepth
                && maxExtensionNodes == that.maxExtensionNodes;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(maxDocumentBytes, maxValues, maxValueMembers, maxPathSegments,
                maxPathBytes, maxStringBytes, maxBinaryValueBytes,
                maxTotalBinaryBytes, maxExtensionDepth, maxExtensionNodes);
    }

    /**
     * Returns the bounds as one line, in the form a record writes itself.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return "Limits[maxDocumentBytes=" + maxDocumentBytes
                + ", maxValues=" + maxValues
                + ", maxValueMembers=" + maxValueMembers
                + ", maxPathSegments=" + maxPathSegments
                + ", maxPathBytes=" + maxPathBytes
                + ", maxStringBytes=" + maxStringBytes
                + ", maxBinaryValueBytes=" + maxBinaryValueBytes
                + ", maxTotalBinaryBytes=" + maxTotalBinaryBytes
                + ", maxExtensionDepth=" + maxExtensionDepth
                + ", maxExtensionNodes=" + maxExtensionNodes
                + "]";
    }

    private static void positive(long value, String what) {
        if (value <= 0) {
            throw new EsjFormatException(what + " is a positive number, not " + value);
        }
    }
}
