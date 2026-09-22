package de.bsnsoft.esj.pdf;

import java.util.Objects;

/**
 * One observation about the container a PDF is, as opposed to the invoice inside it.
 *
 * <p>The two verdicts stay apart. An invoice may be perfectly valid inside a container
 * that says the wrong thing about it, and a container may be immaculate around an invoice
 * that is not. A caller shows both and says which is which; nothing here is a finding of
 * the specification, and the codes carry their own prefixes so that they can never be
 * confused with the {@code ESJ-} codes that specification reserves (section 9.6).
 *
 * <p>A message may quote the file — an attachment name, a media type, an XMP property —
 * and every such fragment is escaped as the specification, section 9.5 asks, so that a
 * finding about a hostile name is not the way that name reaches a terminal.
 *
 * @param category what part of the container the finding is about
 * @param code     a stable, machine-readable identifier of the kind of problem
 * @param severity how much the finding weighs
 * @param message  human-readable English text, with any quoted fragment escaped
 */
public record ContainerFinding(Category category,
                               String code,
                               Severity severity,
                               String message) {

    /**
     * Checks that no member is {@code null}.
     *
     * @param category what part of the container the finding is about
     * @param code     a stable, machine-readable identifier of the kind of problem
     * @param severity how much the finding weighs
     * @param message  human-readable English text, with any quoted fragment escaped
     * @throws NullPointerException if a member is {@code null}
     */
    public ContainerFinding {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    /** What part of the container a finding is about. */
    public enum Category {

        /** The object structure of the file: the cross-reference data, the trailer. */
        PDF_STRUCTURE("PDF-STRUCTURE"),

        /**
         * The catalog's associated files array: whether the invoice attachment is
         * declared to belong to the document, and in what relationship.
         */
        PDF_AF("PDF-AF"),

        /**
         * The XMP packet: the Factur-X extension schema, and whether what it says about
         * the attachment and the profile agrees with the attachment and the invoice.
         */
        PDF_XMP("PDF-XMP"),

        /** The embedded file dictionary: the declared media type, size and name. */
        PDF_EMBEDDED("PDF-EMBEDDED"),

        /**
         * The ESJ document attached beside the invoice: whether it can be read, and
         * whether it and the invoice are two accounts of one invoice
         * ({@link EsjAgreement}).
         */
        PDF_ESJ("PDF-ESJ");

        private final String id;

        Category(String id) {
            this.id = id;
        }

        /**
         * Returns the identifier a report writes for this category.
         *
         * @return the identifier, such as {@code PDF-XMP}
         */
        public String id() {
            return id;
        }
    }

    /** How much a finding weighs. */
    public enum Severity {

        /** The container is wrong about the document it carries. */
        ERROR,

        /** The container is not wrong, and something about it is worth saying. */
        WARNING,

        /**
         * A fact about the container, carrying no judgement at all — what the file
         * declares itself to be, and what this module did not check.
         */
        INFO
    }

    /**
     * Returns the finding as one line, in the form {@code SEVERITY code: message}.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return severity + " " + code + ": " + message;
    }
}
