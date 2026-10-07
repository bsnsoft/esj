package de.bsnsoft.esj.xml;

import java.util.Objects;
import java.util.Optional;

/**
 * A syntax of an XML invoice this project reads and writes, together with the root element
 * that identifies it.
 *
 * <p>The two UBL document types are separate constants because a reader and a writer treat
 * them apart — they have different root elements, and the XRechnung extension binds terms
 * for the invoice that the credit note has no place for — while both record {@code UBL} as
 * the provenance syntax of the document they produce.
 *
 * <p>This is the one list of syntaxes every module of this project uses: the readers, the
 * writers, the syntax engine and the PDF container all name a syntax with a constant of
 * this type. What a module needs beyond the root element — a stylesheet, a binding table,
 * a schema — it keeps to itself.
 */
public enum InvoiceSyntax {

    /** A UBL 2.1 invoice. */
    UBL_INVOICE("UBL",
            "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
            "Invoice"),

    /** A UBL 2.1 credit note. */
    UBL_CREDIT_NOTE("UBL",
            "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2",
            "CreditNote"),

    /** A UN/CEFACT cross industry invoice, CII D16B. */
    CII("CII",
            "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100",
            "CrossIndustryInvoice");

    private final String provenance;
    private final String namespace;
    private final String localName;

    InvoiceSyntax(String provenance, String namespace, String localName) {
        this.provenance = provenance;
        this.namespace = namespace;
        this.localName = localName;
    }

    /**
     * Recognizes the syntax of a root element.
     *
     * <p>Only the name of the element is asked, never its content, so this is the answer
     * to the question which syntax a document claims to be written in and not whether it
     * is a valid document of it.
     *
     * @param namespace the namespace of the root element, empty if it has none
     * @param localName the local name of the root element
     * @return the syntax, or an empty optional if no syntax this project reads has that
     *         root element
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Optional<InvoiceSyntax> of(String namespace, String localName) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(localName, "localName");
        for (InvoiceSyntax syntax : values()) {
            if (syntax.matches(namespace, localName)) {
                return Optional.of(syntax);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the token this syntax is recorded with in the provenance metadata of a
     * document read from it, the {@code source.syntax} member of the specification,
     * section 4.7.
     *
     * @return {@code UBL} or {@code CII}
     */
    public String provenance() {
        return provenance;
    }

    /**
     * Returns the namespace of the root element of a document in this syntax.
     *
     * @return the namespace URI
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Returns the local name of the root element of a document in this syntax.
     *
     * @return the local name
     */
    public String localName() {
        return localName;
    }

    /**
     * Tells whether a root element belongs to this syntax.
     *
     * @param namespace the namespace of the root element, empty if it has none
     * @param localName the local name of the root element
     * @return {@code true} if both match
     */
    public boolean matches(String namespace, String localName) {
        return this.namespace.equals(namespace) && this.localName.equals(localName);
    }
}
