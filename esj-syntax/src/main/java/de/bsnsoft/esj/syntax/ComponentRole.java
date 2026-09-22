package de.bsnsoft.esj.syntax;

/**
 * What a component of a validation pack is, and therefore which engine runs it.
 *
 * <p>The role is a fact of the pack manifest rather than a guess from a file name: a
 * pack that one day carried a third kind of artefact would name it, and this module
 * would refuse the pack rather than run the file as one of these two.
 */
public enum ComponentRole {

    /**
     * XML Schema modules. The entry file is the schema of the document type, and the
     * other files of the component are the modules it imports.
     */
    XSD("xsd"),

    /**
     * A Schematron rule set, compiled to XSLT by its publisher. The entry file is the
     * stylesheet; running it over the document produces a Schematron validation report
     * in the SVRL vocabulary.
     */
    SCHEMATRON_XSLT("schematron-xslt");

    private final String token;

    ComponentRole(String token) {
        this.token = token;
    }

    /**
     * Returns the token this role is written with in a pack manifest.
     *
     * @return the token, {@code xsd} or {@code schematron-xslt}
     */
    public String token() {
        return token;
    }

    /**
     * Returns the role a manifest token names.
     *
     * @param token the token read from a manifest
     * @return the role
     * @throws PackException if no role is written with that token
     */
    public static ComponentRole of(String token) {
        for (ComponentRole role : values()) {
            if (role.token.equals(token)) {
                return role;
            }
        }
        throw new PackException("a validation pack component has the role " + token
                + ", and this module runs only " + XSD.token + " and "
                + SCHEMATRON_XSLT.token);
    }
}
