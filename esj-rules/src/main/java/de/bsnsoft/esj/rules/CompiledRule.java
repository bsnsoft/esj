package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticPath;
import java.util.List;
import java.util.Optional;

/**
 * One rule, ready to run: what it is called, how much it weighs, where it is evaluated, and
 * the one question it answers.
 *
 * <p>Both kinds of rule end up here, and the engine cannot tell them apart afterwards. That
 * is what makes the promise of {@link JavaRule} true rather than merely intended: the
 * severity, the category, the pack, the engine name, the read list and the ordering of a
 * finding are decided in one place, so a rule expressed in the rule language and the same
 * rule expressed in Java produce the same finding by construction and not by care.
 */
final class CompiledRule {

    /**
     * What a rule had to say about one business group instance: how much it weighs and
     * why.
     *
     * <p>A rule may answer at either of two severities. Most answer at the one they
     * declare; a rule that carries a second assertion answers at the warning level where
     * the first assertion holds and the second does not.
     */
    record Outcome(RuleSeverity severity, String message) {
    }

    /** The one question a rule answers about one business group instance. */
    @FunctionalInterface
    interface Check {
        Optional<Outcome> apply(Evaluation evaluation, SemanticPath base);
    }

    private final String id;
    private final RuleSeverity severity;
    private final PathPattern context;
    private final List<String> terms;
    private final String source;
    private final Check check;

    CompiledRule(String id, RuleSeverity severity, PathPattern context, List<String> terms,
                 String source, Check check) {
        this.id = id;
        this.severity = severity;
        this.context = context;
        this.terms = List.copyOf(terms);
        this.source = source;
        this.check = check;
    }

    String id() {
        return id;
    }

    RuleSeverity severity() {
        return severity;
    }

    /** Returns the context pattern, or {@code null} where the rule is about the document. */
    PathPattern context() {
        return context;
    }

    List<String> terms() {
        return terms;
    }

    String source() {
        return source;
    }

    Optional<Outcome> check(Evaluation evaluation, SemanticPath base) {
        return check.apply(evaluation, base);
    }
}
