package de.bsnsoft.esj.typed.runtime;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.typed.MissingValueException;
import de.bsnsoft.esj.typed.ValueTypeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;

/**
 * What the generated views do when an accessor is called: build the path of a child,
 * read the value the document carries there, and count the instances of a repeatable
 * group.
 *
 * <p>The occurrences of a repeatable term or group are counted from index zero upwards
 * and stop at the first index the document does not carry. Indices are dense in a
 * conformant document (specification, section 5.4); where they are not, a view shows the
 * occurrences up to the gap, and validation layer L3 is what reports the gap.
 */
public final class Views {

    private Views() {
    }

    /**
     * Reads the value of a mandatory business term.
     *
     * @param document the document the view reads
     * @param path     the value path
     * @param reader   the reader of the registry datatype of the term
     * @param <T>      the Java type of the value
     * @return the value
     * @throws MissingValueException if the document carries no value there
     * @throws ValueTypeException    if the value there is no value of that semantic data
     *                               type
     */
    public static <T> T required(SemanticDocument document,
                                 SemanticPath path,
                                 Values.Reader<T> reader) {
        SemanticValue value = document.value(path).orElseThrow(() -> new MissingValueException(path));
        return read(value, reader, path);
    }

    /**
     * Reads the value of an optional business term.
     *
     * @param document the document the view reads
     * @param path     the value path
     * @param reader   the reader of the registry datatype of the term
     * @param <T>      the Java type of the value
     * @return the value, or an empty optional where the document carries none
     * @throws ValueTypeException if the value there is no value of that semantic data type
     */
    public static <T> Optional<T> optional(SemanticDocument document,
                                    SemanticPath path,
                                    Values.Reader<T> reader) {
        return document.value(path).map(value -> read(value, reader, path));
    }

    /**
     * Reads the occurrences of a repeatable business term.
     *
     * @param document the document the view reads
     * @param parent   the path of the group instance
     * @param termId   the identifier of the business term
     * @param reader   the reader of the registry datatype of the term
     * @param <T>      the Java type of the value
     * @return the values, in the order of their occurrence indices
     * @throws ValueTypeException if one of the values is no value of that semantic data
     *                            type
     */
    public static <T> List<T> repeated(SemanticDocument document,
                                SemanticPath parent,
                                String termId,
                                Values.Reader<T> reader) {
        List<T> values = new ArrayList<>();
        for (int index = 0; ; index++) {
            SemanticPath path = TermPaths.indexed(TermPaths.value(parent, termId), index);
            Optional<SemanticValue> value = document.value(path);
            if (value.isEmpty()) {
                return List.copyOf(values);
            }
            values.add(read(value.get(), reader, path));
        }
    }

    /**
     * Returns the view over an optional business group.
     *
     * @param document the document the view reads
     * @param parent   the path of the enclosing group instance
     * @param groupId  the identifier of the business group
     * @param factory  the constructor of the child view
     * @param <T>      the view type of the child
     * @return the view, or an empty optional where the document carries no value under
     *         the group
     */
    public static <T> Optional<T> optionalGroup(SemanticDocument document,
                                         SemanticPath parent,
                                         String groupId,
                                         ViewFactory<T> factory) {
        SemanticPath path = TermPaths.group(parent, groupId);
        return present(document, path) ? Optional.of(factory.create(document, path)) : Optional.empty();
    }

    /**
     * Returns one view per instance of a repeatable business group.
     *
     * @param document the document the view reads
     * @param parent   the path of the enclosing group instance
     * @param groupId  the identifier of the business group
     * @param factory  the constructor of the child view
     * @param <T>      the view type of the child
     * @return the views, in the order of their occurrence indices
     */
    public static <T> List<T> groups(SemanticDocument document,
                              SemanticPath parent,
                              String groupId,
                              ViewFactory<T> factory) {
        List<T> views = new ArrayList<>();
        for (int index = 0; ; index++) {
            SemanticPath path = TermPaths.indexed(TermPaths.group(parent, groupId), index);
            if (!present(document, path)) {
                return List.copyOf(views);
            }
            views.add(factory.create(document, path));
        }
    }

    /**
     * Tells whether a group instance exists, which it does exactly when the document
     * carries at least one value under it (specification, section 4.5).
     *
     * @param document the document the view reads
     * @param path     the group instance path
     * @return {@code true} if a value lies under the path
     */
    private static boolean present(SemanticDocument document, SemanticPath path) {
        SortedMap<SemanticPath, SemanticValue> below = document.values().tailMap(path);
        return !below.isEmpty() && below.firstKey().startsWith(path);
    }

    /**
     * Reads one value as the Java value of its semantic data type, and says where a
     * content that does not satisfy the grammar of that type was met.
     *
     * @param value  the value the document carries
     * @param reader the reader of the registry datatype of the term
     * @param path   the path the value lies at
     * @param <T>    the Java type of the value
     * @return the value as a Java value
     * @throws ValueTypeException if the content does not satisfy the grammar of the type
     */
    static <T> T read(SemanticValue value, Values.Reader<T> reader, SemanticPath path) {
        try {
            return reader.read().apply(value);
        } catch (EsjFormatException e) {
            throw new ValueTypeException(path, reader.type(), e);
        }
    }
}
