package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedMap;

/**
 * What a document looks like from above: for each pattern a rule of the pack asks about,
 * the values that match it and the group instances that exist.
 *
 * <p>This class is why the engine is linear in the number of invoice lines. Without it a
 * rule that sums the net amount of every line would walk the whole document once per rule,
 * and a rule evaluated once per line would walk it once per line — the second of which is
 * quadratic and is the mistake that makes a validator unusable at the size this project
 * designs for, three hundred thousand lines in one invoice.
 *
 * <p>Instead one pass over the sorted map answers every question the pack can ask from the
 * document root. The pass computes the key of each path — the path with every occurrence
 * index replaced by an asterisk — and files the entry under it, and files each business
 * group instance the path lies in under the key of that group. Only the keys the compiled
 * pack actually asks for are kept, which the compiler knows because a rule's patterns are
 * fixed when it is compiled; everything else is dropped as it is seen, so the index costs
 * one reference per value a rule can reach and nothing for the rest. A key the compiler did
 * not foresee — a Java rule writes its patterns in Java, where nothing can read them, and
 * declaring them ({@link JavaRule#roots()}) is a hint and not a duty — is added when it is
 * first asked for, so a question is always answered correctly and the compiler's foresight is
 * a saving rather than a requirement. Such a key costs one more pass, and only for itself:
 * what is already indexed is kept, so a surprise never repeats the work of the keys before
 * it.
 *
 * <p>A question that is not asked from the root — every allowance of <em>this</em> line —
 * is not answered here. Its answer lies in one subtree of the document, the sorted map
 * hands that subtree out in one range, and the cost of finding it is the size of the line
 * rather than the size of the invoice. {@link #within} is that range.
 *
 * <p>The pass is made once, on the first question that needs it, and never again for the
 * same document. The lists it builds are in the canonical order of the document, because
 * that is the order the sorted map hands them out in, so two runs over the same document
 * report in the same order.
 */
final class DocumentIndex {

    private final SortedMap<SemanticPath, SemanticValue> values;

    /** The value keys that are not in the maps yet, and are put there by the next pass. */
    private final Set<String> pendingValueKeys;

    /** The group keys that are not in the maps yet. */
    private final Set<String> pendingGroupKeys;

    /** Every value key a pass has already answered, whether or not the document carries one. */
    private final Set<String> indexedValueKeys = new HashSet<>();

    /** Every group key a pass has already answered. */
    private final Set<String> indexedGroupKeys = new HashSet<>();

    private final Map<String, List<Map.Entry<SemanticPath, SemanticValue>>> byValueKey =
            new HashMap<>();
    private final Map<String, List<SemanticPath>> byGroupKey = new HashMap<>();

    /**
     * Prepares the index of a document for the keys a compiled pack asks about.
     *
     * @param document  the document
     * @param valueKeys the keys of the value patterns the pack evaluates from the root
     * @param groupKeys the keys of the group patterns the pack enumerates from the root
     */
    DocumentIndex(SemanticDocument document, Set<String> valueKeys, Set<String> groupKeys) {
        this.values = document.values();
        this.pendingValueKeys = new LinkedHashSet<>(valueKeys);
        this.pendingGroupKeys = new LinkedHashSet<>(groupKeys);
    }

    /**
     * Returns the values whose path has this key, in canonical order.
     *
     * @param key the key of a value pattern, as {@link PathPattern#key} computes it
     * @return the matching entries, empty where the document has none
     */
    List<Map.Entry<SemanticPath, SemanticValue>> valuesFor(String key) {
        if (!indexedValueKeys.contains(key)) {
            pendingValueKeys.add(key);
        }
        build();
        return byValueKey.getOrDefault(key, List.of());
    }

    /**
     * Returns the business group instances whose path has this key, in canonical order.
     *
     * @param key the key of a group pattern
     * @return the instances, empty where the document has none
     */
    List<SemanticPath> instancesFor(String key) {
        if (!indexedGroupKeys.contains(key)) {
            pendingGroupKeys.add(key);
        }
        build();
        return byGroupKey.getOrDefault(key, List.of());
    }

    /**
     * Returns the entries of one subtree of the document, in canonical order.
     *
     * <p>The cost is the size of the subtree and not the size of the document: the paths
     * of a subtree are contiguous in the canonical path order, so the sorted map hands
     * them out as one range.
     *
     * @param prefix the group instance the subtree hangs under; the root for the whole
     *               document
     * @return the entries at or below the prefix
     */
    Iterable<Map.Entry<SemanticPath, SemanticValue>> within(SemanticPath prefix) {
        if (prefix.isRoot()) {
            return values.entrySet();
        }
        SortedMap<SemanticPath, SemanticValue> tail = values.tailMap(prefix);
        return () -> new PrefixIterator(tail.entrySet().iterator(), prefix);
    }

    /**
     * Files the entries of the document under the keys that are waiting for a pass.
     *
     * <p>The pass looks only for keys that are not answered yet: the maps already built are
     * kept, so a key the compiler did not foresee costs one pass for itself and does not make
     * the engine index the rest of the pack a second time.
     */
    private void build() {
        if (pendingValueKeys.isEmpty() && pendingGroupKeys.isEmpty()) {
            return;
        }
        for (Map.Entry<SemanticPath, SemanticValue> entry : values.entrySet()) {
            SemanticPath path = entry.getKey();
            if (!pendingValueKeys.isEmpty()) {
                String key = PathPattern.key(path);
                if (pendingValueKeys.contains(key)) {
                    byValueKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
                }
            }
            if (pendingGroupKeys.isEmpty()) {
                continue;
            }
            for (SemanticPath group : path.groupPaths()) {
                String key = PathPattern.key(group);
                if (!pendingGroupKeys.contains(key)) {
                    continue;
                }
                List<SemanticPath> instances =
                        byGroupKey.computeIfAbsent(key, ignored -> new ArrayList<>());
                if (instances.isEmpty() || !instances.get(instances.size() - 1).equals(group)) {
                    instances.add(group);
                }
            }
        }
        indexedValueKeys.addAll(pendingValueKeys);
        indexedGroupKeys.addAll(pendingGroupKeys);
        pendingValueKeys.clear();
        pendingGroupKeys.clear();
    }

    /**
     * The entries of one subtree: the tail of the sorted map, stopped at the first path
     * that has left the subtree.
     */
    private static final class PrefixIterator implements Iterator<Map.Entry<SemanticPath, SemanticValue>> {

        private final Iterator<Map.Entry<SemanticPath, SemanticValue>> tail;
        private final SemanticPath prefix;
        private Map.Entry<SemanticPath, SemanticValue> next;

        PrefixIterator(Iterator<Map.Entry<SemanticPath, SemanticValue>> tail,
                       SemanticPath prefix) {
            this.tail = tail;
            this.prefix = prefix;
            advance();
        }

        private void advance() {
            next = null;
            if (tail.hasNext()) {
                Map.Entry<SemanticPath, SemanticValue> candidate = tail.next();
                if (candidate.getKey().startsWith(prefix)) {
                    next = candidate;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Map.Entry<SemanticPath, SemanticValue> next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            Map.Entry<SemanticPath, SemanticValue> current = next;
            advance();
            return current;
        }
    }
}
