package de.bsnsoft.esj.bindings;

import java.util.Objects;

/**
 * What a run of a writer of this module is allowed to do and how it shapes its output.
 *
 * <p>Four things are settings rather than rules of the format. The <em>indentation</em>
 * decides whether the document is written one element to a line, which is what a person
 * reads and a diff compares, or as one line, which is what a channel that pays for every
 * byte wants; both are the same document to a parser and both are deterministic. The
 * <em>bound on the output</em> is the writer's half of the limits the reader applies to its
 * input: a semantic document is data from somewhere, and a process that converts one must
 * be able to say how large the result may get before it starts. The <em>tax registration
 * scheme</em> is the one convention of a binding table whose value is a caller's to choose:
 * UBL asks which tax a registration belongs to and the semantic model says only that BT-32
 * is not the value added tax, so the writer states a code and the caller may state another
 * one.
 *
 * <p>Instances are immutable. {@link #defaults()} is the one a caller who has no opinion
 * gets, and it is the one the tests of this module measure.
 */
public final class WriterOptions {

    /**
     * The largest document a writer produces by default, in bytes.
     *
     * <p>Sixty-four mebibytes is the size of the largest semantic document the reader
     * limits of the specification, section 12.2 accept by default. A cross industry
     * invoice is longer than the semantic document it carries, so this bound is generous
     * rather than tight; it is there to stop a runaway rather than to size a buffer.
     */
    public static final long DEFAULT_MAX_OUTPUT_BYTES = 64L * 1024L * 1024L;

    /**
     * The tax registration scheme identifier the UBL writer states for BT-32 unless the
     * caller names another one.
     *
     * <p>{@code FC} is the UNTDID 1153 code for a fiscal number and it is what the cross
     * industry invoice binding of the same source model fixes for this term, so a document
     * converted from that syntax says the same thing in both.
     */
    public static final String DEFAULT_TAX_REGISTRATION_SCHEME = "FC";

    /**
     * The scheme identifier BT-31 is written with, which BT-32 is by definition not and
     * which is therefore refused as the value of {@link #taxRegistrationScheme()}.
     */
    private static final String VAT = "VAT";

    /** The name a binding table calls the setting {@link #taxRegistrationScheme()} by. */
    private static final String TAX_REGISTRATION_SCHEME = "taxRegistrationScheme";

    private final boolean indent;
    private final long maxOutputBytes;
    private final UblWriter.DocumentType document;
    private final String taxRegistrationScheme;

    private WriterOptions(boolean indent,
                          long maxOutputBytes,
                          UblWriter.DocumentType document,
                          String taxRegistrationScheme) {
        this.indent = indent;
        this.maxOutputBytes = maxOutputBytes;
        this.document = document;
        this.taxRegistrationScheme = taxRegistrationScheme;
    }

    /**
     * Returns the options a caller with no opinion gets: an indented document and the
     * default bound on its size.
     *
     * @return the defaults
     */
    public static WriterOptions defaults() {
        return Defaults.INSTANCE;
    }

    /**
     * Returns a builder with the defaults set.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a builder that starts from these options.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        return new Builder().indent(indent).maxOutputBytes(maxOutputBytes)
                .document(document).taxRegistrationScheme(taxRegistrationScheme);
    }

    /**
     * Tells whether the document is written one element to a line.
     *
     * @return {@code true} if the output is indented
     */
    public boolean indent() {
        return indent;
    }

    /**
     * Returns the largest document this run may produce.
     *
     * @return the bound in bytes
     */
    public long maxOutputBytes() {
        return maxOutputBytes;
    }

    /**
     * Returns which of the two UBL documents {@link UblWriter} writes. It says nothing
     * about a cross industry invoice, which is one document.
     *
     * @return the document type, {@link UblWriter.DocumentType#AUTO} by default
     */
    public UblWriter.DocumentType document() {
        return document;
    }

    /**
     * Returns the tax registration scheme identifier the UBL writer states for BT-32.
     *
     * @return the identifier, {@link #DEFAULT_TAX_REGISTRATION_SCHEME} by default
     */
    public String taxRegistrationScheme() {
        return taxRegistrationScheme;
    }

    /**
     * Returns the value this run writes for one convention of a binding table: the value
     * of the table, unless the convention names a setting of these options.
     *
     * @param option the setting the convention names, or {@code null} where it names none
     * @param value  the value the binding table states
     * @return the value to write
     * @throws BindingFormatException if the convention names a setting these options do
     *                                not carry
     */
    String conventionValue(String option, String value) {
        if (option == null) {
            return value;
        }
        if (TAX_REGISTRATION_SCHEME.equals(option)) {
            return taxRegistrationScheme;
        }
        throw new BindingFormatException("a convention of a binding table names the writer"
                + " setting " + option + ", which these options do not carry");
    }

    /**
     * Returns the options as one line, which is what a message about them needs.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return "WriterOptions[indent=" + indent + ", maxOutputBytes=" + maxOutputBytes
                + ", document=" + document + ", taxRegistrationScheme="
                + taxRegistrationScheme + "]";
    }

    private static final class Defaults {

        private static final WriterOptions INSTANCE = builder().build();

        private Defaults() {
            throw new AssertionError("no instances");
        }
    }

    /** Builds {@link WriterOptions}. */
    public static final class Builder {

        private boolean indent = true;
        private long maxOutputBytes = DEFAULT_MAX_OUTPUT_BYTES;
        private UblWriter.DocumentType document = UblWriter.DocumentType.AUTO;
        private String taxRegistrationScheme = DEFAULT_TAX_REGISTRATION_SCHEME;

        private Builder() {
        }

        /**
         * Sets whether the document is written one element to a line.
         *
         * @param value {@code true} to indent
         * @return this builder
         */
        public Builder indent(boolean value) {
            this.indent = value;
            return this;
        }

        /**
         * Sets the largest document this run may produce.
         *
         * @param value the bound in bytes
         * @return this builder
         * @throws IllegalArgumentException if the bound is not positive
         */
        public Builder maxOutputBytes(long value) {
            if (value <= 0) {
                throw new IllegalArgumentException("a bound on the output is positive: "
                        + value);
            }
            this.maxOutputBytes = value;
            return this;
        }

        /**
         * Sets which of the two UBL documents to write.
         *
         * @param value the document type
         * @return this builder
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public Builder document(UblWriter.DocumentType value) {
            this.document = Objects.requireNonNull(value, "document");
            return this;
        }

        /**
         * Sets the tax registration scheme identifier the UBL writer states for BT-32.
         *
         * <p>The semantic model says of BT-32 only that it is not the value added tax
         * identifier, and UBL requires every party tax scheme to name the tax it belongs
         * to, so a code is stated. {@code VAT} is refused: an element that carries it is
         * BT-31 to every reader of that syntax, and writing BT-32 into it would say
         * something the document does not.
         *
         * @param value the identifier
         * @return this builder
         * @throws IllegalArgumentException if the identifier is blank or is {@code VAT}
         * @throws NullPointerException     if {@code value} is {@code null}
         */
        public Builder taxRegistrationScheme(String value) {
            Objects.requireNonNull(value, "taxRegistrationScheme");
            if (value.isBlank()) {
                throw new IllegalArgumentException(
                        "a tax registration scheme identifier is not blank");
            }
            if (VAT.equals(value)) {
                throw new IllegalArgumentException("VAT is the scheme of BT-31, the value"
                        + " added tax identifier, and BT-32 is by definition a"
                        + " registration for another tax");
            }
            this.taxRegistrationScheme = value;
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public WriterOptions build() {
            return new WriterOptions(indent, maxOutputBytes, document,
                    taxRegistrationScheme);
        }
    }
}
