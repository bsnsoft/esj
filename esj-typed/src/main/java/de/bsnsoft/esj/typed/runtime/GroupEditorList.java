package de.bsnsoft.esj.typed.runtime;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.typed.EditorList;
import java.util.Objects;
import java.util.function.Consumer;

/** Writes the instances of one repeatable business group into a document builder. */
final class GroupEditorList<E> implements EditorList<E> {

    private final SemanticDocument.Builder builder;
    private final SemanticPath step;
    private final EditorFactory<E> factory;

    GroupEditorList(SemanticDocument.Builder builder, SemanticPath step, EditorFactory<E> factory) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.step = Objects.requireNonNull(step, "step");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    @Override
    public E add() {
        return factory.create(builder, TermPaths.indexed(step, size()));
    }

    @Override
    public EditorList<E> add(Consumer<E> block) {
        Objects.requireNonNull(block, "block").accept(add());
        return this;
    }

    @Override
    public E get(int index) {
        return factory.create(builder, TermPaths.indexed(step, Editors.checked(index, size(), step)));
    }

    @Override
    public int size() {
        return builder.occurrences(step);
    }

    @Override
    public EditorList<E> remove(int index) {
        builder.removeOccurrence(TermPaths.indexed(step, Editors.checked(index, size(), step)));
        return this;
    }

    @Override
    public EditorList<E> clear() {
        builder.removeUnder(step);
        return this;
    }

    @Override
    public String toString() {
        return "EditorList[" + step + ", " + size() + "]";
    }
}
