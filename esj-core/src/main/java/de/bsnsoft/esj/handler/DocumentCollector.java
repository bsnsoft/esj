package de.bsnsoft.esj.handler;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * A {@link SemanticHandler} that builds a {@link SemanticDocument} from the events it
 * receives.
 *
 * <p>The collector checks that the events nest: a group instance ends with the path it
 * began with, and a value lies in the group instance that is open when it arrives. A
 * stream that breaks that contract is a defect of its producer, not of a document, so it
 * is signalled by {@link IllegalStateException}.
 *
 * <p>The envelope is not carried by the events. A collector that has to reproduce one is
 * created with a builder that already holds it.
 */
public final class DocumentCollector implements SemanticHandler {

    private final SemanticDocument.Builder builder;
    private final Deque<SemanticPath> open = new ArrayDeque<>();

    /** Creates a collector for a document with the default envelope. */
    public DocumentCollector() {
        this(SemanticDocument.builder());
    }

    /**
     * Creates a collector that adds the values it receives to a builder.
     *
     * @param builder the builder, which may already carry the semantic model edition, the
     *                extensions and the provenance metadata
     * @throws NullPointerException if {@code builder} is {@code null}
     */
    public DocumentCollector(SemanticDocument.Builder builder) {
        this.builder = Objects.requireNonNull(builder, "builder");
    }

    @Override
    public void beginGroup(SemanticPath groupPath) {
        Objects.requireNonNull(groupPath, "groupPath");
        if (!groupPath.isGroupPath()) {
            throw new IllegalStateException("a group instance lies at a business group, not at " + groupPath);
        }
        SemanticPath enclosing = open.isEmpty() ? SemanticPath.root() : open.peekLast();
        if (!groupPath.parent().equals(enclosing)) {
            throw new IllegalStateException(
                    "the group instance " + groupPath + " does not lie in the open group instance " + enclosing);
        }
        open.addLast(groupPath);
    }

    @Override
    public void value(SemanticPath path, SemanticValue value) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(value, "value");
        SemanticPath enclosing = open.isEmpty() ? SemanticPath.root() : open.peekLast();
        if (!path.parent().equals(enclosing)) {
            throw new IllegalStateException(
                    "the value at " + path + " does not lie in the open group instance " + enclosing);
        }
        builder.put(path, value);
    }

    @Override
    public void endGroup(SemanticPath groupPath) {
        Objects.requireNonNull(groupPath, "groupPath");
        if (open.isEmpty() || !open.peekLast().equals(groupPath)) {
            throw new IllegalStateException("the group instance " + groupPath + " is not the open one");
        }
        open.removeLast();
    }

    /**
     * Builds the document from the events received so far.
     *
     * @return the document
     * @throws IllegalStateException if a group instance is still open
     */
    public SemanticDocument document() {
        if (!open.isEmpty()) {
            throw new IllegalStateException("the group instance " + open.peekLast() + " is still open");
        }
        return builder.build();
    }
}
