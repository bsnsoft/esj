package de.bsnsoft.esj.typed.runtime;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;

/**
 * Builds the editor of one group instance. The generated editors hand the constructor of
 * a child editor to {@link Editors} as a method reference, so that {@code Editors} can
 * append and remove instances without knowing which type it is building.
 *
 * @param <E> the editor type the factory builds
 */
@FunctionalInterface
public interface EditorFactory<E> {

    /**
     * Returns an editor over one group instance.
     *
     * @param builder the builder the editor writes into
     * @param path    the path of the group instance
     * @return the editor
     */
    E create(SemanticDocument.Builder builder, SemanticPath path);
}
