package de.bsnsoft.esj.syntax;

/**
 * What kind of thing a finding is about.
 *
 * <p>A validation of an invoice produces findings from several rule sets at once, and a
 * reader who has to tell an arithmetic error of the standard from a binding rule of one
 * syntax from a national rule needs that distinction before the rule identifier. The
 * category is derived from the identifier the artefact reports; see {@link Categories}
 * for the table and for what happens to an identifier the table does not know.
 *
 * <p>The order of the constants is the second key a report sorts its findings by.
 */
public enum FindingCategory {

    /** The document as XML: well-formedness, the declared encoding, what the parser refuses. */
    XML("XML"),

    /** The UBL 2.1 schema modules. */
    UBL_XSD("UBL-XSD"),

    /** The UN/CEFACT CII D16B schema modules. */
    CII_XSD("CII-XSD"),

    /** A business rule of EN 16931. */
    EN_BR("EN-BR"),

    /** A decimal rule of EN 16931: how many fraction digits a value may spell. */
    EN_DEC("EN-DEC"),

    /** A code list rule of EN 16931: whether a code is one of the list it must come from. */
    EN_CL("EN-CL"),

    /** A rule about how EN 16931 is bound to UBL. */
    UBL_BINDING("UBL-BINDING"),

    /** A rule about how EN 16931 is bound to CII. */
    CII_BINDING("CII-BINDING"),

    /** A rule of the XRechnung core invoice usage specification. */
    XR_BR("XR-BR"),

    /** A rule of the XRechnung extension. */
    XR_EXT("XR-EXT"),

    /**
     * A finding whose rule identifier belongs to no family this table names. The
     * identifier travels with it unchanged, so nothing is lost by landing here.
     */
    OTHER("OTHER");

    private final String label;

    FindingCategory(String label) {
        this.label = label;
    }

    /**
     * Returns the label a report prints this category with.
     *
     * @return the label
     */
    public String label() {
        return label;
    }
}
