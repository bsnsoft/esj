package de.bsnsoft.esj.typed.runtime;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;

/**
 * Builds the view over one group instance. The generated views hand the constructor of a
 * child view to {@link Views} as a method reference, so that {@code Views} can resolve
 * how many instances a group has without knowing which type it is resolving.
 *
 * @param <T> the view type the factory builds
 */
@FunctionalInterface
public interface ViewFactory<T> {

    /**
     * Returns a view over one group instance.
     *
     * @param document the document the view reads
     * @param path     the path of the group instance
     * @return the view
     */
    T create(SemanticDocument document, SemanticPath path);
}
