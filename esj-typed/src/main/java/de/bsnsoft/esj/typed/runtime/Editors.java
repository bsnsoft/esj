package de.bsnsoft.esj.typed.runtime;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.typed.BinaryObject;
import de.bsnsoft.esj.typed.EditorList;
import de.bsnsoft.esj.typed.Identifier;
import de.bsnsoft.esj.typed.IdentifierList;
import de.bsnsoft.esj.typed.SchemedIdentifierList;
import de.bsnsoft.esj.typed.ValueList;
import java.util.Optional;

/**
 * What the generated editors do when a setter is called: build the path of a child, write
 * a value there or take it away again, and hand back the handle of a repeatable term or
 * group.
 *
 * <p>Writing {@code null}, an empty string or an empty file removes the value instead of
 * storing an empty one: the specification knows no empty value (section 6.1), and a
 * caller that sets a field back to nothing means the invoice no longer carries it.
 *
 * <p>Nothing here creates a group. A group instance exists exactly when a value lies
 * under it (specification, section 4.5), so the editor of a group that is never written
 * into leaves no trace in the document.
 */
public final class Editors {

    private Editors() {
    }

    /**
     * Writes the value of a business term, or removes it where there is nothing to write.
     *
     * @param builder the builder the editor writes into
     * @param path    the value path
     * @param value   the Java value, or {@code null} to remove the value
     * @param writer  the writer of the registry datatype of the term
     * @param <T>     the Java type of the value
     */
    public static <T> void set(SemanticDocument.Builder builder,
                        SemanticPath path,
                        T value,
                        Writers.Writer<T> writer) {
        if (value == null || (value instanceof String text && text.isEmpty())) {
            builder.remove(path);
            return;
        }
        builder.set(path, writer.write().apply(value));
    }

    /**
     * Returns the identifier a setter overload spells out, or {@code null} where there is
     * nothing to write.
     *
     * @param value         the identifier, or {@code null} or the empty string to remove
     *                      the value
     * @param scheme        the identification scheme, or {@code null}
     * @param schemeVersion the version of that scheme, or {@code null}
     * @return the identifier, or {@code null}
     */
    public static Identifier identifier(String value, String scheme, String schemeVersion) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return new Identifier(value,
                Optional.ofNullable(scheme),
                Optional.ofNullable(schemeVersion));
    }

    /**
     * Returns an identifier a setter was handed whole, or {@code null} where there is
     * nothing to write. It is the overload that takes an identifier back from a view, so
     * that a scheme read out of one document is not lost on the way into another.
     *
     * @param value the identifier, or {@code null} or one with an empty value to remove
     *              the value
     * @return the identifier, or {@code null}
     */
    public static Identifier identifier(Identifier value) {
        return value == null || value.value().isEmpty() ? null : value;
    }

    /**
     * Returns an embedded file a setter was handed whole, or {@code null} where there is
     * nothing to write.
     *
     * @param value the file, or {@code null} or an empty file to remove the value
     * @return the file, or {@code null}
     */
    public static BinaryObject binary(BinaryObject value) {
        return value == null || value.bytes().length == 0 ? null : value;
    }

    /**
     * Returns the embedded file a setter overload spells out, or {@code null} where there
     * is nothing to write.
     *
     * @param bytes    the content of the file, or {@code null} or an empty array to
     *                 remove the value
     * @param mimeCode the media type of the file
     * @param filename the file name of the file
     * @return the file, or {@code null}
     */
    public static BinaryObject binary(byte[] bytes, String mimeCode, String filename) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return new BinaryObject(bytes, mimeCode, filename);
    }

    /**
     * Returns the handle of a repeatable business group.
     *
     * @param builder the builder the editor writes into
     * @param parent  the path of the enclosing group instance
     * @param groupId the identifier of the business group
     * @param factory the constructor of the child editor
     * @param <E>     the editor type of the child
     * @return the handle
     */
    public static <E> EditorList<E> groups(SemanticDocument.Builder builder,
                                    SemanticPath parent,
                                    String groupId,
                                    EditorFactory<E> factory) {
        return new GroupEditorList<>(builder, TermPaths.group(parent, groupId), factory);
    }

    /**
     * Returns the handle of a repeatable business term.
     *
     * @param builder the builder the editor writes into
     * @param parent  the path of the group instance
     * @param termId  the identifier of the business term
     * @param reader  the reader of the registry datatype of the term
     * @param writer  the writer of the registry datatype of the term
     * @param <T>     the Java type of one value
     * @return the handle
     */
    public static <T> ValueList<T> values(SemanticDocument.Builder builder,
                                   SemanticPath parent,
                                   String termId,
                                   Values.Reader<T> reader,
                                   Writers.Writer<T> writer) {
        return new TermValueList<>(builder, TermPaths.value(parent, termId), reader, writer);
    }

    /**
     * Returns the handle of a repeatable business term whose semantic data type is
     * Identifier.
     *
     * @param builder the builder the editor writes into
     * @param parent  the path of the group instance
     * @param termId  the identifier of the business term
     * @return the handle
     */
    public static IdentifierList identifiers(SemanticDocument.Builder builder,
                                      SemanticPath parent,
                                      String termId) {
        return new TermIdentifierList(builder, TermPaths.value(parent, termId));
    }

    /**
     * Returns the handle of a repeatable business term whose semantic data type is
     * Identifier and whose scheme the registry declares mandatory. The handle is the same
     * one, seen through the narrower interface: what the term's scheme costs the caller
     * is the overload that appends a bare identifier.
     *
     * @param builder the builder the editor writes into
     * @param parent  the path of the group instance
     * @param termId  the identifier of the business term
     * @return the handle
     */
    public static SchemedIdentifierList schemedIdentifiers(SemanticDocument.Builder builder,
                                                    SemanticPath parent,
                                                    String termId) {
        return new TermIdentifierList(builder, TermPaths.value(parent, termId));
    }

    /**
     * Checks an occurrence index against the number of occurrences that exist.
     *
     * @param index the zero-based occurrence index
     * @param size  how many occurrences there are
     * @param step  the path of the repeatable term or group, for the message
     * @return the index
     * @throws IndexOutOfBoundsException if the index lies outside the occurrences
     */
    static int checked(int index, int size, SemanticPath step) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(
                    step + " has " + size + " occurrences, so there is none at index " + index);
        }
        return index;
    }
}
