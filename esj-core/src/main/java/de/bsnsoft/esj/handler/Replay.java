package de.bsnsoft.esj.handler;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Replays the values of a document to a {@link SemanticHandler} in the canonical path
 * order of the specification, section 7.4, opening and closing every group instance
 * around the values it holds.
 *
 * <p>A group instance is one distinct group prefix, for example {@code /BG-25/1} or
 * {@code /BG-4/BG-5}. Because the values arrive in canonical order, the instances of one
 * group arrive one after another and nest correctly without look-ahead.
 *
 * <p>The replay covers the values of the document. The envelope — the semantic model
 * edition, {@code extensions} and {@code source} — is not an event; a
 * {@link DocumentCollector} that has to reproduce it is seeded with it.
 */
public final class Replay {

    private Replay() {
        throw new AssertionError("no instances");
    }

    /**
     * Replays a document to a handler.
     *
     * @param document the document to replay
     * @param handler  the handler that receives the events
     * @throws NullPointerException if an argument is {@code null}
     */
    public static void replay(SemanticDocument document, SemanticHandler handler) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(handler, "handler");
        Deque<SemanticPath> open = new ArrayDeque<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            List<SemanticPath> groups = entry.getKey().groupPaths();
            int shared = 0;
            for (SemanticPath group : open) {
                if (shared < groups.size() && groups.get(shared).equals(group)) {
                    shared++;
                } else {
                    break;
                }
            }
            while (open.size() > shared) {
                handler.endGroup(open.removeLast());
            }
            for (int i = shared; i < groups.size(); i++) {
                SemanticPath group = groups.get(i);
                handler.beginGroup(group);
                open.addLast(group);
            }
            handler.value(entry.getKey(), entry.getValue());
        }
        while (!open.isEmpty()) {
            handler.endGroup(open.removeLast());
        }
    }
}
