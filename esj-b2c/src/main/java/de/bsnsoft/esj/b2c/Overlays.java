package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.typed.runtime.TermPaths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * The one thing an overlay does differently from the typed editor of the core model:
 * it writes into the group instances that editor made, and makes none of its own.
 *
 * <p>Everything else an accessor or a setter of a generated overlay does — building the
 * path of a child, reading a value of its semantic data type, writing one or taking it
 * away, listing the instances of a repeatable group — is the runtime of the typed view of
 * the core model, {@code de.bsnsoft.esj.typed.runtime}, and the overlay calls
 * it rather than repeating it: a value written through the one is read through the other
 * exactly because both build the path the same way.
 *
 * <p>What that runtime hands a caller for a repeatable group is an {@code EditorList},
 * which appends an instance and removes one. An overlay must not: how many invoice lines
 * an invoice has is a statement of the core model, and an extension that could add one
 * would be writing the invoice rather than annotating it. So the editors of a repeatable
 * group are handed out as a plain list of the instances that exist, and an index beyond
 * them is refused in those words.
 */
final class Overlays {

    private Overlays() {
    }

    /**
     * Returns one editor per instance of a repeatable business group the builder already
     * carries, in the order of their occurrence indices. No position beyond the last
     * existing instance is handed out, because an overlay appends no instance of a core
     * group.
     *
     * @param builder the builder the overlay writes into
     * @param parent  the path of the enclosing group instance
     * @param groupId the identifier of the business group
     * @param factory the constructor of the child editor
     * @param <E>     the editor type of the child
     * @return the editors, empty where the builder carries no instance
     */
    static <E> List<E> editors(SemanticDocument.Builder builder,
                               SemanticPath parent,
                               String groupId,
                               BiFunction<SemanticDocument.Builder, SemanticPath, E> factory) {
        SemanticPath step = TermPaths.group(parent, groupId);
        int occurrences = builder.occurrences(step);
        List<E> editors = new ArrayList<>(occurrences);
        for (int index = 0; index < occurrences; index++) {
            editors.add(factory.apply(builder, TermPaths.indexed(step, index)));
        }
        return List.copyOf(editors);
    }

    /**
     * Returns one of the editors an overlay handed out, and refuses an index the document
     * has no group instance at in the words of the overlay: a core group is written by the
     * typed editor of the core model, and the overlay only writes values into the instances
     * that editor made.
     *
     * @param editors the editors, one per instance the document carries
     * @param index   the zero-based occurrence index
     * @param groupId the identifier of the business group
     * @param <E>     the editor type
     * @return the editor at that index
     * @throws IndexOutOfBoundsException if the document carries no instance at that index
     */
    static <E> E at(List<E> editors, int index, String groupId) {
        if (index < 0 || index >= editors.size()) {
            throw new IndexOutOfBoundsException("the document carries no instance of " + groupId
                    + " at index " + index + ", it carries " + editors.size() + "; the typed"
                    + " editor of the core model writes the group instances, the overlay only"
                    + " the values of the extension inside them");
        }
        return editors.get(index);
    }
}
