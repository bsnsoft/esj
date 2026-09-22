package de.bsnsoft.esj.json;

import de.bsnsoft.esj.EsjFormatException;

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
 * @param maxDocumentBytes    the largest document, in bytes of the encoded document, at
 *                            most {@link #MAX_DOCUMENT_BYTES}
 * @param maxValues           the largest number of members of {@code values}
 * @param maxValueMembers     the largest number of members of one value object
 * @param maxPathSegments     the largest number of segments of one semantic path,
 *                            counting index segments
 * @param maxPathBytes        the largest semantic path, in bytes of its UTF-8 encoding
 * @param maxStringBytes      the largest string value, in bytes of the UTF-8 encoding of
 *                            the normalized value
 * @param maxBinaryValueBytes the largest content of a value carrying a binary
 *                            component, in bytes of its base64 encoding
 * @param maxTotalBinaryBytes the largest sum of binary content of one document, in bytes
 *                            after base64 decoding
 * @param maxExtensionDepth   the deepest nesting inside {@code extensions}, in levels of
 *                            object or array below an owner token, at most
 *                            {@link #MAX_EXTENSION_DEPTH}
 * @param maxExtensionNodes   the largest number of nodes inside {@code extensions},
 *                            counting every scalar and every container
 */
public record Limits(long maxDocumentBytes,
                     int maxValues,
                     int maxValueMembers,
                     int maxPathSegments,
                     int maxPathBytes,
                     long maxStringBytes,
                     long maxBinaryValueBytes,
                     long maxTotalBinaryBytes,
                     int maxExtensionDepth,
                     int maxExtensionNodes) {

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
     * (specification, section 12.2), while a streaming parser counts the containers of
     * the envelope as well, so a reader hands its parser the sum of the two.
     */
    static final int ENVELOPE_NESTING = 3;

    /**
     * The largest value {@link #maxExtensionDepth()} accepts. A reader adds
     * {@link #ENVELOPE_NESTING} to the bound before it configures its parser with it,
     * and that sum has to stay a nesting depth a parser can be given — the twin of the
     * reason {@link #MAX_DOCUMENT_BYTES} exists. A bound this large is already far past
     * any document a reader could hold; refusing it here makes the refusal an
     * {@link EsjFormatException} naming the knob, rather than whatever a parser throws
     * about a negative depth.
     */
    public static final int MAX_EXTENSION_DEPTH = Integer.MAX_VALUE - ENVELOPE_NESTING;

    private static final Limits DEFAULTS = new Limits(
            64L * MIB, 100_000, 16, 16, 256, MIB, 32L * MIB, 48L * MIB, 32, 100_000);

    /**
     * Checks that every bound is positive and that the document bound is one a reader can
     * hold.
     *
     * @param maxDocumentBytes    the largest document, in bytes of the encoded document, at
     *                            most {@link #MAX_DOCUMENT_BYTES}
     * @param maxValues           the largest number of members of {@code values}
     * @param maxValueMembers     the largest number of members of one value object
     * @param maxPathSegments     the largest number of segments of one semantic path,
     *                            counting index segments
     * @param maxPathBytes        the largest semantic path, in bytes of its UTF-8 encoding
     * @param maxStringBytes      the largest string value, in bytes of the UTF-8 encoding of
     *                            the normalized value
     * @param maxBinaryValueBytes the largest content of a value carrying a binary
     *                            component, in bytes of its base64 encoding
     * @param maxTotalBinaryBytes the largest sum of binary content of one document, in bytes
     *                            after base64 decoding
     * @param maxExtensionDepth   the deepest nesting inside {@code extensions}, in levels of
     *                            object or array below an owner token, at most
     *                            {@link #MAX_EXTENSION_DEPTH}
     * @param maxExtensionNodes   the largest number of nodes inside {@code extensions},
     *                            counting every scalar and every container
     * @throws EsjFormatException if a bound is zero or negative, if
     *                            {@code maxDocumentBytes} exceeds
     *                            {@link #MAX_DOCUMENT_BYTES}, or if
     *                            {@code maxExtensionDepth} exceeds
     *                            {@link #MAX_EXTENSION_DEPTH}
     */
    public Limits {
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
                    + ", because a reader adds the nesting of the envelope to it before it"
                    + " configures its parser, not " + maxExtensionDepth);
        }
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
     * Returns a builder seeded with the defaults.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder(DEFAULTS);
    }

    /**
     * Returns a builder seeded with these limits, for changing one of them.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    private static void positive(long value, String what) {
        if (value <= 0) {
            throw new EsjFormatException(what + " is a positive number, not " + value);
        }
    }

    /** Builds a {@link Limits}. A builder is not thread safe and is reusable. */
    public static final class Builder {

        private long maxDocumentBytes;
        private int maxValues;
        private int maxValueMembers;
        private int maxPathSegments;
        private int maxPathBytes;
        private long maxStringBytes;
        private long maxBinaryValueBytes;
        private long maxTotalBinaryBytes;
        private int maxExtensionDepth;
        private int maxExtensionNodes;

        private Builder(Limits seed) {
            this.maxDocumentBytes = seed.maxDocumentBytes;
            this.maxValues = seed.maxValues;
            this.maxValueMembers = seed.maxValueMembers;
            this.maxPathSegments = seed.maxPathSegments;
            this.maxPathBytes = seed.maxPathBytes;
            this.maxStringBytes = seed.maxStringBytes;
            this.maxBinaryValueBytes = seed.maxBinaryValueBytes;
            this.maxTotalBinaryBytes = seed.maxTotalBinaryBytes;
            this.maxExtensionDepth = seed.maxExtensionDepth;
            this.maxExtensionNodes = seed.maxExtensionNodes;
        }

        /**
         * Sets the largest document, in bytes, at most
         * {@link Limits#MAX_DOCUMENT_BYTES}.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder maxDocumentBytes(long value) {
            this.maxDocumentBytes = value;
            return this;
        }

        /**
         * Sets the largest number of members of {@code values}.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder maxValues(int value) {
            this.maxValues = value;
            return this;
        }

        /**
         * Sets the largest number of members of one value object.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder maxValueMembers(int value) {
            this.maxValueMembers = value;
            return this;
        }

        /**
         * Sets the largest number of segments of one semantic path.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder maxPathSegments(int value) {
            this.maxPathSegments = value;
            return this;
        }

        /**
         * Sets the largest semantic path, in bytes of its UTF-8 encoding.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder maxPathBytes(int value) {
            this.maxPathBytes = value;
            return this;
        }

        /**
         * Sets the largest string value, in bytes of the UTF-8 encoding of the normalized
         * value.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder maxStringBytes(long value) {
            this.maxStringBytes = value;
            return this;
        }

        /**
         * Sets the largest content of a value carrying a binary component, in bytes of
         * its base64 encoding.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder maxBinaryValueBytes(long value) {
            this.maxBinaryValueBytes = value;
            return this;
        }

        /**
         * Sets the largest sum of decoded binary content of one document, in bytes.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder maxTotalBinaryBytes(long value) {
            this.maxTotalBinaryBytes = value;
            return this;
        }

        /**
         * Sets the deepest nesting inside {@code extensions}, in levels of object or
         * array below an owner token, at most {@link Limits#MAX_EXTENSION_DEPTH}.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder maxExtensionDepth(int value) {
            this.maxExtensionDepth = value;
            return this;
        }

        /**
         * Sets the largest number of nodes inside {@code extensions}, counting every
         * scalar and every container.
         *
         * @param value the bound
         * @return this builder
         */
        public Builder maxExtensionNodes(int value) {
            this.maxExtensionNodes = value;
            return this;
        }

        /**
         * Builds the limits.
         *
         * @return the limits
         * @throws EsjFormatException if a bound is zero or negative, if
         *                            {@code maxDocumentBytes} exceeds
         *                            {@link Limits#MAX_DOCUMENT_BYTES}, or if
         *                            {@code maxExtensionDepth} exceeds
         *                            {@link Limits#MAX_EXTENSION_DEPTH}
         */
        public Limits build() {
            return new Limits(maxDocumentBytes, maxValues, maxValueMembers, maxPathSegments,
                    maxPathBytes, maxStringBytes, maxBinaryValueBytes, maxTotalBinaryBytes,
                    maxExtensionDepth, maxExtensionNodes);
        }
    }
}
