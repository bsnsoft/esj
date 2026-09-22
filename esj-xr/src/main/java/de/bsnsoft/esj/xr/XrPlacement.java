package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.PathSegment;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How a position in the XR tree becomes a semantic path, and back.
 *
 * <p>The two trees are not the same shape. The XR representation flattens a group here and
 * there — BG-14 sits beside BG-13 in it and inside it in the standard — so neither side
 * can take the nesting of the other one over unchanged. What both sides do instead is ask
 * the registry: given the chain of enclosing groups resolved so far and the identifier of
 * the next element, is there a parent chain that continues it? Groups the chain passes
 * through on the way are opened implicitly, which is sound exactly because a group that
 * may occur more than once is never one of them: an implicit occurrence would have no
 * index to carry.
 *
 * <p>This class holds that arithmetic once, because the importer walks it in one direction
 * and the exporter in the other, and two copies of it would be two answers to the question
 * where a term sits.
 */
final class XrPlacement {

    private XrPlacement() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the parent chain the registry records for a term that continues the chain
     * resolved so far, if there is one. Groups between the two are opened implicitly, so a
     * chain whose intermediate groups are not all singletons is no continuation.
     *
     * @param registry the registry that decides the structure
     * @param resolved the term identifiers of the enclosing group instance, from the root
     * @param id       the identifier of the term the element carries
     * @return the chain from the root down to and including {@code id}, or an empty
     *         optional where the registry records no such chain
     */
    static Optional<List<String>> continuation(Registry registry, List<String> resolved, String id) {
        for (List<String> chain : registry.chains(id)) {
            if (chain.size() <= resolved.size()
                    || !chain.subList(0, resolved.size()).equals(resolved)) {
                continue;
            }
            if (chain.subList(resolved.size(), chain.size() - 1).stream()
                    .noneMatch(registry::isRepeatable)) {
                return Optional.of(chain);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the chain that the children of a group instance resolve against.
     *
     * <p>A group the registry records inside itself — the sub invoice line of the
     * XRechnung extension is the one such group of this release — gives its nested
     * occurrence the chain of the enclosing one with its own identifier appended. The path
     * grows by that occurrence; the chain does not, because an inner occurrence carries
     * the same children in the same positions as the outer one. Collapsing it here is what
     * makes the nesting unbounded: a registry file enumerates chains, and no enumeration
     * reaches every depth a document may write.
     *
     * @param chain the chain of a group instance
     * @return the chain its children resolve against
     */
    static List<String> resolved(List<String> chain) {
        int last = chain.size() - 1;
        if (last > 0 && chain.get(last).equals(chain.get(last - 1))) {
            return chain.subList(0, last);
        }
        return chain;
    }

    /**
     * Returns the path of the position a term sits in: the path of the enclosing group
     * instance followed by the groups the registry chain passes through on the way. It is
     * the path occurrences of that term are counted under.
     *
     * @param registry     the registry that decides the structure
     * @param instancePath the path of the enclosing group instance
     * @param chain        the chain {@link #continuation} returned for the term
     * @param resolved     the number of identifiers of that chain the enclosing instance
     *                     already spends
     * @return the path the term's own segment is appended to
     */
    static SemanticPath instance(Registry registry,
                                 SemanticPath instancePath,
                                 List<String> chain,
                                 int resolved) {
        List<PathSegment> segments = new ArrayList<>(instancePath.segments());
        for (String intermediate : chain.subList(resolved, chain.size() - 1)) {
            segments.add(segment(registry.term(intermediate).orElseThrow()));
        }
        return SemanticPath.ofSegments(segments);
    }

    /**
     * Returns the path of one occurrence: its position, the term, and the index it takes.
     *
     * @param instance the path {@link #instance} returned
     * @param term     the term of the occurrence
     * @param index    the occurrence index, or a negative number for a term that carries
     *                 none
     * @return the path of that occurrence
     */
    static SemanticPath extend(SemanticPath instance, Term term, int index) {
        List<PathSegment> segments = new ArrayList<>(instance.segments());
        segments.add(segment(term));
        if (index >= 0) {
            segments.add(PathSegment.index(index));
        }
        return SemanticPath.ofSegments(segments);
    }

    /**
     * Returns the segment of a term: the identifier of the standard, split into the parts
     * the path grammar of the specification, section 5.1 gives it.
     *
     * @param term the term
     * @return its path segment
     */
    static PathSegment.Term segment(Term term) {
        String id = term.id();
        String rest = id.substring(id.indexOf('-') + 1);
        int namespaceEnd = rest.indexOf('-');
        if (namespaceEnd < 0) {
            return PathSegment.core(term.kind(), rest);
        }
        return PathSegment.extension(term.kind(),
                rest.substring(0, namespaceEnd),
                rest.substring(namespaceEnd + 1));
    }
}
