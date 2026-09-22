package de.bsnsoft.esj.typed;

import de.bsnsoft.esj.EsjFormatException;

/**
 * The occurrences of a repeatable business term whose semantic data type is Identifier
 * and whose identification scheme the registry leaves optional, with the overloads that
 * spell an identifier out rather than building the record first (specification, section
 * 6.6).
 *
 * <p>Which supplementary components a term allows is decided per term by the registry.
 * The overloads that name a scheme and a scheme version are offered here for every such
 * term; a component the registry does not list for the term is refused by validation
 * layer L2, as it is for a document written any other way. The one overload this
 * interface adds to {@link SchemedIdentifierList} is the one that appends a bare
 * identifier, which a term with a mandatory scheme does not offer.
 */
public interface IdentifierList extends SchemedIdentifierList {

    /**
     * Appends an identifier at the end of the list.
     *
     * @param value the identifier with the components it carries
     * @return this handle
     * @throws EsjFormatException   if the identifier cannot be written
     * @throws NullPointerException if {@code value} is {@code null}
     */
    @Override
    IdentifierList add(Identifier value);

    /**
     * Appends an identifier that carries no supplementary component.
     *
     * @param value the identifier
     * @return this handle
     * @throws EsjFormatException   if the identifier is empty
     * @throws NullPointerException if {@code value} is {@code null}
     */
    IdentifierList add(String value);

    /**
     * Appends an identifier qualified by a scheme.
     *
     * @param value  the identifier
     * @param scheme the identification scheme
     * @return this handle
     * @throws EsjFormatException   if a string is empty
     * @throws NullPointerException if an argument is {@code null}
     */
    IdentifierList add(String value, String scheme);

    /**
     * Appends an identifier qualified by a scheme and by the version of that scheme.
     *
     * @param value         the identifier
     * @param scheme        the identification scheme
     * @param schemeVersion the version of that scheme
     * @return this handle
     * @throws EsjFormatException   if a string is empty
     * @throws NullPointerException if an argument is {@code null}
     */
    IdentifierList add(String value, String scheme, String schemeVersion);

    /**
     * Removes one occurrence and moves every later one down one index.
     *
     * @param index the zero-based occurrence index
     * @return this handle
     * @throws IndexOutOfBoundsException if the term has no occurrence at that index
     */
    @Override
    IdentifierList remove(int index);

    /**
     * Removes every occurrence of the term.
     *
     * @return this handle
     */
    @Override
    IdentifierList clear();
}
