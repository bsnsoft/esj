package de.bsnsoft.esj.typed;

import de.bsnsoft.esj.EsjFormatException;

/**
 * The occurrences of a repeatable business term whose semantic data type is Identifier
 * and whose identification scheme the registry declares mandatory, with the overloads
 * that spell an identifier out rather than building the record first (specification,
 * section 6.6).
 *
 * <p>There is no overload that appends a bare identifier: the scheme is part of what the
 * term means, and a value without it is refused by validation layer L2. The handle of a
 * term whose scheme is optional is {@link IdentifierList}, which adds that overload.
 */
public interface SchemedIdentifierList extends ValueList<Identifier> {

    /**
     * Appends an identifier at the end of the list.
     *
     * @param value the identifier with the components it carries; the registry declares
     *              the scheme of this term mandatory, and an identifier that carries none
     *              is appended as it stands and reported at validation layer L2
     * @return this handle
     * @throws EsjFormatException   if the identifier cannot be written
     * @throws NullPointerException if {@code value} is {@code null}
     */
    @Override
    SchemedIdentifierList add(Identifier value);

    /**
     * Appends an identifier qualified by a scheme.
     *
     * @param value  the identifier
     * @param scheme the identification scheme
     * @return this handle
     * @throws EsjFormatException   if a string is empty
     * @throws NullPointerException if an argument is {@code null}
     */
    SchemedIdentifierList add(String value, String scheme);

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
    SchemedIdentifierList add(String value, String scheme, String schemeVersion);

    /**
     * Removes one occurrence and moves every later one down one index.
     *
     * @param index the zero-based occurrence index
     * @return this handle
     * @throws IndexOutOfBoundsException if the term has no occurrence at that index
     */
    @Override
    SchemedIdentifierList remove(int index);

    /**
     * Removes every occurrence of the term.
     *
     * @return this handle
     */
    @Override
    SchemedIdentifierList clear();
}
