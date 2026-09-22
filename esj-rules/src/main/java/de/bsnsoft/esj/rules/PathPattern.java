package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.PathSegment;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.TermKind;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A path as a rule writes it: a chain of business term identifiers in which a repeatable
 * one is followed by {@code *} instead of by an occurrence index.
 *
 * <p>A rule never writes an occurrence index. It cannot: a rule is a statement about every
 * invoice, and the number of lines an invoice has is not known when the rule is written.
 * {@code *} is therefore not a convenience but the only form a repeatable step has in the
 * language, and the index rule of {@code SPEC.md} section 5.3 decides where one stands: a
 * step whose term the registry declares repeatable carries {@code *} and a step whose term
 * it does not carries nothing. A pattern that gets that wrong is refused when the pack is
 * compiled, which is the same check the structural validator makes on a document, made
 * once against the registry instead of once per invoice.
 *
 * <p>A pattern is relative to the context of its rule. At the document context the two are
 * the same thing; inside {@code /BG-25/*} the pattern {@code /BT-131} means the net amount
 * of the line the rule is looking at, and {@code /BG-27/*}{@code /BT-136} means every
 * allowance amount of that line. The chain the registry sees is the context's chain
 * followed by the pattern's, which is why an unknown term and an impossible nesting are
 * both caught here.
 */
final class PathPattern {

    private final List<String> terms;
    private final List<Boolean> wildcards;
    private final String text;
    private final boolean hasWildcard;
    private final boolean endsAtGroup;

    private PathPattern(List<String> terms, List<Boolean> wildcards, String text,
                        boolean hasWildcard, boolean endsAtGroup) {
        this.terms = terms;
        this.wildcards = wildcards;
        this.text = text;
        this.hasWildcard = hasWildcard;
        this.endsAtGroup = endsAtGroup;
    }

    /**
     * Compiles a pattern written in a rule, with the chain of the enclosing context
     * before it.
     *
     * @param written     the pattern as the rule writes it, starting with a solidus
     * @param contextTerm the term identifiers of the enclosing context, outermost first
     * @param registry    the registry the terms are resolved against
     * @param what        what the pattern is, for the message of a failure
     * @return the compiled pattern
     * @throws RulePackException if the pattern is not a path, names a term the registry
     *                           does not know, nests in a way the registry does not
     *                           record, or breaks the index rule
     */
    static PathPattern compile(String written, List<String> contextTerm, Registry registry, String what) {
        if (written.isEmpty() || written.charAt(0) != '/') {
            throw new RulePackException(what + ": a path starts with a solidus, " + written + " does not");
        }
        List<String> steps = new ArrayList<>();
        List<Boolean> stars = new ArrayList<>();
        for (String token : written.substring(1).split("/", -1)) {
            if (token.equals("*")) {
                if (stars.isEmpty() || stars.get(stars.size() - 1)) {
                    throw new RulePackException(what + ": an asterisk follows a term, " + written + " does not");
                }
                stars.set(stars.size() - 1, Boolean.TRUE);
                continue;
            }
            if (token.isEmpty()) {
                throw new RulePackException(what + ": empty step in " + written);
            }
            steps.add(token);
            stars.add(Boolean.FALSE);
        }
        if (steps.isEmpty()) {
            throw new RulePackException(what + ": " + written + " names no term");
        }
        check(steps, stars, contextTerm, registry, what, written);
        return new PathPattern(List.copyOf(steps), List.copyOf(stars), written,
                stars.contains(Boolean.TRUE), isGroup(registry, steps.get(steps.size() - 1)));
    }

    private static void check(List<String> steps, List<Boolean> stars, List<String> contextTerm,
                              Registry registry, String what, String written) {
        List<String> chain = new ArrayList<>(contextTerm);
        chain.addAll(steps);
        for (int i = 0; i < steps.size(); i++) {
            String id = steps.get(i);
            Term term = registry.term(id).orElseThrow(() -> new RulePackException(
                    what + ": " + id + " is not a term of " + registry.semanticModel()));
            if (i < steps.size() - 1 && term.kind() != TermKind.BG) {
                throw new RulePackException(what + ": only the last step of " + written
                        + " may name a business term, " + id + " is one");
            }
            boolean repeatable = term.isRepeatable();
            if (repeatable != stars.get(i)) {
                throw new RulePackException(what + ": " + id + (repeatable
                        ? " is repeatable and is written " + id + "/* in a path"
                        : " occurs at most once and is written without an asterisk")
                        + ", " + written + " does the other");
            }
        }
        if (!registry.chains(chain.get(chain.size() - 1)).contains(chain)) {
            throw new RulePackException(what + ": " + String.join("/", chain)
                    + " is not a nesting the registry records");
        }
    }

    private static boolean isGroup(Registry registry, String id) {
        return registry.term(id).map(term -> term.kind() == TermKind.BG).orElse(Boolean.FALSE);
    }

    /**
     * Returns the pattern as the rule wrote it.
     *
     * @return the pattern text, for a message and for a finding
     */
    String text() {
        return text;
    }

    /**
     * Tells whether the pattern can match more than one path inside one context instance.
     *
     * @return whether any step carries an asterisk
     */
    boolean hasWildcard() {
        return hasWildcard;
    }

    /**
     * Tells whether the pattern names business group instances rather than values.
     *
     * @return whether the last step names a business group
     */
    boolean endsAtGroup() {
        return endsAtGroup;
    }

    /**
     * Returns the term identifiers of the pattern, outermost first.
     *
     * @return the steps
     */
    List<String> terms() {
        return terms;
    }

    /**
     * Returns how many path segments the pattern adds to the context: one per step, and
     * one more for each step that carries an asterisk.
     *
     * @return the number of segments
     */
    int segmentCount() {
        int count = terms.size();
        for (Boolean star : wildcards) {
            if (star) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the identifier of the term the pattern ends at.
     *
     * @return the last step
     */
    String lastTerm() {
        return terms.get(terms.size() - 1);
    }

    /**
     * Returns the pattern as it addresses the document from the root, given the instance
     * the rule is looking at.
     *
     * @param base the context instance, the root for a document rule
     * @return the absolute pattern text, with the indices of the base and the asterisks of
     *         the pattern
     */
    String absoluteText(SemanticPath base) {
        return base.toString() + text;
    }

    /**
     * Returns the one path this pattern addresses inside a context instance.
     *
     * @param base the context instance
     * @return the path, or an empty optional if the pattern carries an asterisk and
     *         therefore addresses more than one
     */
    Optional<SemanticPath> concrete(SemanticPath base) {
        if (hasWildcard) {
            return Optional.empty();
        }
        String absolute = absoluteText(base);
        return Optional.of(endsAtGroup ? SemanticPath.group(absolute) : SemanticPath.of(absolute));
    }

    /**
     * Returns the longest prefix of the absolute pattern that carries no asterisk, which
     * is the subtree every match of the pattern lies in.
     *
     * @param base the context instance
     * @return the prefix path
     */
    SemanticPath concretePrefix(SemanticPath base) {
        StringBuilder builder = new StringBuilder(base.toString());
        for (int i = 0; i < terms.size(); i++) {
            if (wildcards.get(i)) {
                break;
            }
            builder.append('/').append(terms.get(i));
        }
        String prefix = builder.toString();
        if (prefix.isEmpty()) {
            return SemanticPath.root();
        }
        return prefix.equals(base.toString()) ? base : parse(prefix);
    }

    private static SemanticPath parse(String text) {
        int last = text.lastIndexOf('/');
        return text.startsWith("BT-", last + 1) ? SemanticPath.of(text) : SemanticPath.group(text);
    }

    /**
     * Returns the key a path has in the index of a document: the path with every
     * occurrence index replaced by an asterisk.
     *
     * @param path the path
     * @return the key
     */
    static String key(SemanticPath path) {
        StringBuilder builder = new StringBuilder(path.toString().length());
        for (PathSegment segment : path.segments()) {
            builder.append('/').append(segment instanceof PathSegment.Index ? "*" : segment.text());
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return text;
    }
}
