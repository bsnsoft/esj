package de.bsnsoft.esj.typed;

import de.bsnsoft.esj.EsjFormatException;

/**
 * The occurrences of a repeatable business term, as an editor writes them: a handle that
 * appends a value, reads one back by its occurrence index, removes one and empties the
 * term.
 *
 * <p>The occurrence indices stay dense: removing one occurrence moves every later one
 * down (specification, section 5.4).
 *
 * @param <T> the Java type of one value, as the semantic data type of the term maps it
 */
public interface ValueList<T> {

    /**
     * Appends a value at the end of the list.
     *
     * @param value the value to write
     * @return this handle
     * @throws EsjFormatException   if the value cannot be written, for example because a
     *                              string is empty
     * @throws NullPointerException if {@code value} is {@code null}
     */
    ValueList<T> add(T value);

    /**
     * Reads one occurrence back.
     *
     * @param index the zero-based occurrence index
     * @return the value at that index
     * @throws IndexOutOfBoundsException if the term has no occurrence at that index
     * @throws ValueTypeException        if the value there is no value of the semantic
     *                                   data type the registry records for the term
     */
    T get(int index);

    /**
     * Returns how many occurrences the term has.
     *
     * @return the number of occurrences, counted from index zero upwards
     */
    int size();

    /**
     * Removes one occurrence and moves every later one down one index.
     *
     * @param index the zero-based occurrence index
     * @return this handle
     * @throws IndexOutOfBoundsException if the term has no occurrence at that index
     */
    ValueList<T> remove(int index);

    /**
     * Removes every occurrence of the term.
     *
     * @return this handle
     */
    ValueList<T> clear();
}
