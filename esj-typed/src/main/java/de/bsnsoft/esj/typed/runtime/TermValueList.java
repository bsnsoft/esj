package de.bsnsoft.esj.typed.runtime;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.typed.MissingValueException;
import de.bsnsoft.esj.typed.ValueList;
import java.util.Objects;

/** Writes the occurrences of one repeatable business term into a document builder. */
class TermValueList<T> implements ValueList<T> {

    /** The builder every occurrence is written into. */
    final SemanticDocument.Builder builder;

    /** The path of the repeatable term, without an occurrence index. */
    final SemanticPath step;

    private final Values.Reader<T> reader;
    private final Writers.Writer<T> writer;

    TermValueList(SemanticDocument.Builder builder,
                  SemanticPath step,
                  Values.Reader<T> reader,
                  Writers.Writer<T> writer) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.step = Objects.requireNonNull(step, "step");
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public ValueList<T> add(T value) {
        append(value);
        return this;
    }

    @Override
    public T get(int index) {
        SemanticPath path = TermPaths.indexed(step, Editors.checked(index, size(), step));
        SemanticValue value = builder.value(path).orElseThrow(
                () -> new MissingValueException(path));
        return Views.read(value, reader, path);
    }

    @Override
    public int size() {
        return builder.occurrences(step);
    }

    @Override
    public ValueList<T> remove(int index) {
        removeAt(index);
        return this;
    }

    @Override
    public ValueList<T> clear() {
        builder.removeUnder(step);
        return this;
    }

    @Override
    public String toString() {
        return "ValueList[" + step + ", " + size() + "]";
    }

    /**
     * Appends one value, so that a subtype can append and still return its own type.
     *
     * @param value the value to write
     */
    final void append(T value) {
        Objects.requireNonNull(value, "value");
        builder.set(TermPaths.indexed(step, size()), writer.write().apply(value));
    }

    /**
     * Removes one occurrence, so that a subtype can remove and still return its own type.
     *
     * @param index the zero-based occurrence index
     */
    final void removeAt(int index) {
        builder.removeOccurrence(TermPaths.indexed(step, Editors.checked(index, size(), step)));
    }
}
