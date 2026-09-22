package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One run of one compiled pack over one document: the document, what the pack may ask it,
 * and the answers that are worth keeping.
 *
 * <p>Three pieces of state make the run linear and reproducible. The {@link DocumentIndex}
 * answers every question asked from the document root out of a single pass. The aggregate
 * memory answers a sum or a count over the whole document once for the whole run, so a pack
 * in which ten rules mention the sum of the line net amounts walks the lines once and not
 * ten times; only aggregates taken from the root are remembered, because an aggregate inside
 * a line is asked once per line and remembering those would grow with the invoice. The same
 * memory is open to a rule written in Java through {@link #shared}, which is how the fifteen
 * VAT rules of the EN 16931 pack share one join of the lines, the allowances, the charges and
 * the breakdowns instead of making fifteen. The read
 * list is what a rule looked at, in the order it looked, which is what a finding reports; a
 * shared answer carries the reads that made it and replays them to every rule it is handed
 * to, so that sharing an answer does not move one rule's reads into another rule's finding.
 *
 * <p>An evaluation belongs to one call of {@link RuleEngine#evaluate} and is not shared. The
 * engine itself is immutable and may be used from several threads; each call makes one of
 * these.
 */
final class Evaluation {

    private final SemanticDocument document;
    private final CodeLists codeLists;
    private final DocumentIndex index;
    private final Map<String, RuleValue> aggregates = new HashMap<>();
    private final Map<String, Memo> shared = new HashMap<>();

    private LinkedHashSet<String> reads = new LinkedHashSet<>();

    Evaluation(SemanticDocument document, CodeLists codeLists, DocumentIndex index) {
        this.document = document;
        this.codeLists = codeLists;
        this.index = index;
    }

    CodeLists codeLists() {
        return codeLists;
    }

    /** Starts a rule instance with a fresh read list. */
    void begin() {
        reads = new LinkedHashSet<>();
    }

    /** Records that a rule looked at a path or at a pattern. */
    void record(String pathOrPattern) {
        reads.add(pathOrPattern);
    }

    /** Returns what the rule looked at, in the order it looked. */
    List<String> reads() {
        return List.copyOf(reads);
    }

    /**
     * Reads the one value a pattern without an asterisk addresses.
     *
     * @param pattern the pattern
     * @param base    the instance the rule is evaluated at
     * @param type    the semantic data type the registry gives the term
     * @return the value, or {@link RuleValue#ABSENT} where the document does not carry it
     * @throws Undecided if the content does not spell what the type requires
     */
    RuleValue read(PathPattern pattern, SemanticPath base, SemanticType type) {
        SemanticPath path = pattern.concrete(base).orElseThrow();
        record(path.toString());
        return document.value(path).map(value -> convert(value, type, path)).orElse(RuleValue.ABSENT);
    }

    /**
     * Reads the one value a pattern without an asterisk addresses, without converting it.
     *
     * @param pattern the pattern
     * @param base    the instance the rule is evaluated at
     * @return the value, or an empty optional where the document does not carry it
     */
    Optional<SemanticValue> raw(PathPattern pattern, SemanticPath base) {
        SemanticPath path = pattern.concrete(base).orElseThrow();
        record(path.toString());
        return document.value(path);
    }

    /**
     * Converts the content of a value to what its semantic data type says it is.
     *
     * @param value the value
     * @param type  the semantic data type
     * @param path  the path, for the message of a failure
     * @return the converted value
     * @throws Undecided if the content does not spell what the type requires
     */
    static RuleValue convert(SemanticValue value, SemanticType type, SemanticPath path) {
        try {
            if (type.isDecimal()) {
                return RuleValue.of(value.asDecimal());
            }
            if (type == SemanticType.DATE) {
                return RuleValue.of(value.asDate());
            }
            return RuleValue.of(value.asString());
        } catch (EsjFormatException e) {
            throw new Undecided("the value at " + path + " is not a "
                    + type.registryDatatype() + ": " + e.getMessage());
        }
    }

    /**
     * Returns the values a pattern addresses inside an instance, in canonical order.
     *
     * @param pattern the pattern
     * @param base    the instance the rule is evaluated at
     * @return the matching values with their paths
     */
    List<Map.Entry<SemanticPath, SemanticValue>> matches(PathPattern pattern, SemanticPath base) {
        record(pattern.absoluteText(base));
        if (!pattern.hasWildcard()) {
            SemanticPath path = pattern.concrete(base).orElseThrow();
            return document.value(path)
                    .<List<Map.Entry<SemanticPath, SemanticValue>>>map(value -> List.of(Map.entry(path, value)))
                    .orElse(List.of());
        }
        if (base.isRoot()) {
            return index.valuesFor(pattern.absoluteText(base));
        }
        String key = PathPattern.key(base) + pattern.text();
        List<Map.Entry<SemanticPath, SemanticValue>> found = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : index.within(pattern.concretePrefix(base))) {
            if (PathPattern.key(entry.getKey()).equals(key)) {
                found.add(entry);
            }
        }
        return found;
    }

    /**
     * Returns the business group instances a pattern addresses inside an instance, in
     * canonical order.
     *
     * @param pattern the pattern, which ends at a business group
     * @param base    the instance the rule is evaluated at
     * @return the instances
     */
    List<SemanticPath> instances(PathPattern pattern, SemanticPath base) {
        record(pattern.absoluteText(base));
        if (!pattern.hasWildcard()) {
            SemanticPath path = pattern.concrete(base).orElseThrow();
            return hasAnythingUnder(path) ? List.of(path) : List.of();
        }
        if (base.isRoot()) {
            return index.instancesFor(pattern.absoluteText(base));
        }
        String key = PathPattern.key(base) + pattern.text();
        List<SemanticPath> found = new ArrayList<>();
        int depth = base.segments().size() + pattern.segmentCount();
        for (Map.Entry<SemanticPath, SemanticValue> entry : index.within(pattern.concretePrefix(base))) {
            SemanticPath path = entry.getKey();
            if (path.segments().size() < depth) {
                continue;
            }
            SemanticPath instance = path.prefix(depth);
            if (PathPattern.key(instance).equals(key)
                    && (found.isEmpty() || !found.get(found.size() - 1).equals(instance))) {
                found.add(instance);
            }
        }
        return found;
    }

    private boolean hasAnythingUnder(SemanticPath group) {
        return index.within(group).iterator().hasNext();
    }

    /**
     * Returns a remembered answer of a rule written in Java, or computes and remembers it.
     *
     * <p>It is the aggregate memory of the rule language, opened to the rules the language
     * cannot express. Those rules read the same four groups of the model over and over — the
     * lines, the allowances, the charges and the VAT breakdowns — and joining them is a pass
     * over the document each time. The join is an answer about the whole document, it does
     * not change while the run lasts, and fifteen rules asking for it fifteen times is
     * fifteen passes for one answer.
     *
     * @param <T>      what the answer is
     * @param key      what identifies the answer, the base path included
     * @param remember whether the answer is worth keeping, which it is only for an answer
     *                 about the document as a whole
     * @param compute  how to compute it
     * @return the answer
     */
    @SuppressWarnings("unchecked")
    <T> T shared(String key, boolean remember, Supplier<T> compute) {
        if (!remember) {
            return compute.get();
        }
        Memo known = shared.get(key);
        if (known != null) {
            reads.addAll(known.reads());
            return (T) known.answer();
        }
        LinkedHashSet<String> outer = reads;
        List<String> made;
        Object computed;
        reads = new LinkedHashSet<>();
        try {
            computed = compute.get();
            made = List.copyOf(reads);
        } finally {
            reads = outer;
        }
        reads.addAll(made);
        shared.put(key, new Memo(computed, made));
        return (T) computed;
    }

    /**
     * An answer about the whole document, together with what computing it read.
     *
     * <p>The reads travel with the answer because the answer does not. A view of the
     * document is computed by whichever rule asks for it first and handed to every later
     * one, and a finding says where its rule looked; without this the first rule would
     * report the reads of all of them and the rest would report none.
     *
     * @param answer what was computed
     * @param reads  the paths and patterns computing it read, in the order it read them
     */
    private record Memo(Object answer, List<String> reads) {
    }

    /**
     * Returns a remembered aggregate, or computes and remembers it.
     *
     * @param key      the absolute pattern and the operator, which together identify it
     * @param remember whether the answer is worth keeping, which it is only for an
     *                 aggregate taken from the document root
     * @param compute  how to compute it
     * @return the aggregate
     */
    RuleValue aggregate(String key, boolean remember, Supplier<RuleValue> compute) {
        if (!remember) {
            return compute.get();
        }
        RuleValue known = aggregates.get(key);
        if (known != null) {
            return known;
        }
        RuleValue computed = compute.get();
        aggregates.put(key, computed);
        return computed;
    }
}
