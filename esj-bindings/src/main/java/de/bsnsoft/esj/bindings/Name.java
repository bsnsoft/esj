package de.bsnsoft.esj.bindings;

/**
 * The expanded name of an element: its namespace and its local name.
 *
 * <p>A binding XPath is written with prefixes and the table names what each prefix stands
 * for, so the prefixes are resolved once, when the table is compiled. A document is
 * matched on the expanded name and never on the prefix it happens to use, which is what
 * the XML namespaces specification asks of anything that reads them.
 *
 * @param namespace the namespace URI, empty for a name in no namespace
 * @param localName the local name
 */
record Name(String namespace, String localName) {

    /** The character that joins the two parts of a key; it occurs in neither of them. */
    private static final char SEPARATOR = (char) 0;

    /** Tells whether this name is the one a parser reports for an element. */
    boolean matches(String namespace, String localName) {
        return this.localName.equals(localName) && this.namespace.equals(namespace);
    }

    /** Returns the name as one key, for the maps of the compiled trie. */
    String key() {
        return key(namespace, localName);
    }

    /** Returns the key of an expanded name a parser reported. */
    static String key(String namespace, String localName) {
        return namespace + SEPARATOR + localName;
    }
}
