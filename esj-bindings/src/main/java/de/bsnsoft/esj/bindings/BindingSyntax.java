package de.bsnsoft.esj.bindings;

/**
 * A syntax this module reads, with the root element that identifies it and the binding
 * table that says where each business term sits in it.
 *
 * <p>The two UBL document types are separate constants because they are bound by separate
 * tables — the XPaths differ in their root element, and the XRechnung extension binds for
 * the invoice only — while both record {@code UBL} as the provenance syntax of the
 * document they produce.
 */
public enum BindingSyntax {

    /** A UBL 2.1 invoice. */
    UBL_INVOICE("UBL",
            "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
            "Invoice",
            "ubl-invoice.json"),

    /** A UBL 2.1 credit note. */
    UBL_CREDIT_NOTE("UBL",
            "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2",
            "CreditNote",
            "ubl-creditnote.json"),

    /** A UN/CEFACT cross industry invoice, CII D16B. */
    CII("CII",
            "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100",
            "CrossIndustryInvoice",
            "cii.json");

    private final String provenance;
    private final String namespace;
    private final String localName;
    private final String table;

    BindingSyntax(String provenance, String namespace, String localName, String table) {
        this.provenance = provenance;
        this.namespace = namespace;
        this.localName = localName;
        this.table = table;
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

    /** Returns the file name of the binding table of this syntax. */
    String table() {
        return table;
    }
}
