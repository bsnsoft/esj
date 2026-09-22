package de.bsnsoft.esj.typed.build;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Names the members an invoice has to carry and does not, by identifier and by name.
 *
 * <p>It walks the group instances the document has and asks the registry, and the profile
 * the builder was opened with, what each of them must contain. A group instance that is
 * not there is not walked into: an optional group that was left out is complete by
 * leaving it out, and a mandatory one that was left out is reported once, as the missing
 * group it is.
 */
final class Completeness {

    private Completeness() {
    }

    /**
     * Returns the members the model or the profile asks for and the document lacks.
     *
     * @param document the document to check
     * @param profile  the profile the builder was opened with
     * @param ignored  the identifiers to leave out, which is what a check before the
     *                 derivation passes so that it does not ask for what the derivation
     *                 is about to write
     * @return the missing members, in canonical path order
     */
    static List<MissingTerm> check(SemanticDocument document,
                                   Profile<?> profile,
                                   Collection<String> ignored) {
        Registry registry = Registry.en16931();
        Map<SemanticPath, String> instances = new TreeMap<>();
        Map<SemanticPath, Set<String>> present = new TreeMap<>();
        instances.put(SemanticPath.root(), null);
        for (SemanticPath path : document.values().keySet()) {
            for (SemanticPath group : path.groupPaths()) {
                instances.put(group, group.term());
                present.computeIfAbsent(group.parent(), where -> new LinkedHashSet<>())
                        .add(group.term());
            }
            present.computeIfAbsent(path.parent(), where -> new LinkedHashSet<>())
                    .add(path.term());
        }
        List<MissingTerm> missing = new ArrayList<>();
        for (Map.Entry<SemanticPath, String> instance : instances.entrySet()) {
            Set<String> here = present.getOrDefault(instance.getKey(), Set.of());
            List<Term> children = instance.getValue() == null
                    ? registry.rootTerms()
                    : registry.children(instance.getValue());
            for (Term child : children) {
                if (!here.contains(child.id()) && !ignored.contains(child.id())
                        && (child.isMandatory() || profile.requires(child.id()))) {
                    missing.add(new MissingTerm(child.id(), child.name(), instance.getKey()));
                }
            }
        }
        return List.copyOf(missing);
    }
}
