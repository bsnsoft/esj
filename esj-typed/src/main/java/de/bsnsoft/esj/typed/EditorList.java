package de.bsnsoft.esj.typed;

import java.util.function.Consumer;

/**
 * The instances of a repeatable business group, as an editor writes them: a handle that
 * appends an instance, reaches one by its occurrence index, removes one and empties the
 * group.
 *
 * <p>A group instance exists exactly when a value lies under it (specification,
 * section 4.5), and this handle keeps that rule rather than working around it. There is
 * no create step: {@link #add()} hands back the editor of the position after the last
 * existing instance, and that position becomes an instance the moment a value is written
 * into it. Two calls to {@code add()} with nothing written in between therefore address
 * the same position — which is why the usual way to append is {@link #add(Consumer)},
 * where the block writes before the next call.
 *
 * @param <E> the editor type of one instance
 */
public interface EditorList<E> {

    /**
     * Returns the editor of a new instance at the end of the list.
     *
     * @return the editor of the position after the last existing instance
     */
    E add();

    /**
     * Appends an instance and writes it in a block.
     *
     * @param block what to write into the new instance
     * @return this handle
     * @throws NullPointerException if {@code block} is {@code null}
     */
    EditorList<E> add(Consumer<E> block);

    /**
     * Returns the editor of one existing instance.
     *
     * @param index the zero-based occurrence index
     * @return the editor of that instance
     * @throws IndexOutOfBoundsException if the list has no instance at that index
     */
    E get(int index);

    /**
     * Returns how many instances the group has.
     *
     * @return the number of occurrences, counted from index zero upwards
     */
    int size();

    /**
     * Removes one instance with everything inside it and moves every later instance one
     * index down, so that the occurrence indices stay dense.
     *
     * @param index the zero-based occurrence index
     * @return this handle
     * @throws IndexOutOfBoundsException if the list has no instance at that index
     */
    EditorList<E> remove(int index);

    /**
     * Removes every instance of the group.
     *
     * @return this handle
     */
    EditorList<E> clear();
}
