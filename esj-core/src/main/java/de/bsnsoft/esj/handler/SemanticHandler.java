package de.bsnsoft.esj.handler;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;

/**
 * Receives the content of a document as a stream of events: a group instance begins, its
 * values arrive, the group instance ends.
 *
 * <p>The events nest: between {@link #beginGroup(SemanticPath)} and the matching
 * {@link #endGroup(SemanticPath)} come the values of that group instance and the events
 * of the group instances inside it. {@link Replay} produces them in the canonical path
 * order of the specification, section 7.4.
 *
 * <p>Groups are not written down in a document; a group instance exists exactly when a
 * value lies under it (specification, section 4.5). A handler therefore never sees an
 * empty group instance.
 */
public interface SemanticHandler {

    /**
     * Called when a group instance begins.
     *
     * @param groupPath the path of the group instance, for example {@code /BG-25/0}
     */
    void beginGroup(SemanticPath groupPath);

    /**
     * Called for one value.
     *
     * @param path  the path of the business term occurrence
     * @param value the value
     */
    void value(SemanticPath path, SemanticValue value);

    /**
     * Called when a group instance ends.
     *
     * @param groupPath the path of the group instance that began with the matching
     *                  {@link #beginGroup(SemanticPath)}
     */
    void endGroup(SemanticPath groupPath);
}
