package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticDocument;
import java.util.List;
import java.util.Optional;

/**
 * A rule the rule language cannot express, written in Java.
 *
 * <p>Most business rules of EN 16931 are arithmetic over a handful of terms and a few are
 * not, and a language large enough for the few would be a programming language embedded in a
 * data file. So the language stays small and closed, and what does not fit is written here,
 * under the same rule identifier, in the same pack, producing the same finding. A report
 * cannot tell the two apart and should not be able to: which side of that line a rule fell on
 * is an implementation detail of this project and not a fact about the invoice.
 *
 * <p>What a rule of either kind owes a report is the same: an identifier the standard gave
 * it, a severity, a context that says what the rule is a statement about, the terms it
 * reads — which is what a coverage table is built from and the only place a Java rule can say
 * so — and a clause reference. Those are declared here rather than in the manifest so that
 * they sit beside the code that uses them.
 *
 * <p>An implementation is used from the engine and may be used from several threads at once,
 * so it holds no state of its own between calls: everything it needs is in the
 * {@link RuleContext} it is handed.
 */
public interface JavaRule {

    /**
     * Returns the identifier of the rule, which is the code of every finding it produces.
     *
     * @return the identifier, for example {@code BR-CO-17}
     */
    String id();

    /**
     * Returns how much a finding of this rule weighs.
     *
     * @return {@link RuleSeverity#FATAL} or {@link RuleSeverity#WARNING}; never
     *         {@link RuleSeverity#INFO}, which is the engine's
     */
    RuleSeverity severity();

    /**
     * Returns the context the rule is evaluated in: a business group path pattern such as
     * {@code /BG-25/*}, or {@code /} for the document.
     *
     * @return the context pattern
     */
    String context();

    /**
     * Returns the identifiers of the business terms and groups the rule reads.
     *
     * @return the identifiers
     */
    List<String> terms();

    /**
     * Returns the path patterns this rule reads from the document root, so that the index of
     * a document can be built for them in the one pass it makes anyway.
     *
     * <p>It is a hint and never a restriction. A rule may read a pattern it did not declare
     * and gets the right answer either way; what a declaration buys is that the document is
     * not walked again to answer the first question about it, which on an invoice of several
     * hundred thousand values is the difference between one pass and one pass per pattern
     * nobody foresaw. A rule of the rule language needs no such declaration, because its
     * patterns are in the file the compiler reads; a rule written in Java writes them in its
     * own source, where nothing can read them.
     *
     * <p>Only patterns with an occurrence wildcard are worth declaring, and only by a rule
     * whose context is the document: a rule evaluated once per business group instance reads
     * its own subtree, which is not indexed from the root. Patterns of either other kind are
     * ignored here. The default is to declare none.
     *
     * @return the patterns, as the rule writes them
     */
    default List<String> roots() {
        return List.of();
    }

    /**
     * Returns the clause of the standard the rule states.
     *
     * <p>A reference and never a quotation: the text of the norm is not reproduced in this
     * repository, and the message of a rule is this project's own words.
     *
     * @return the reference, for example {@code EN 16931-1, 6.4.2, BR-CO-17}
     */
    String source();

    /**
     * Decides the rule for one business group instance.
     *
     * <p>An accessor of the context raises an internal signal when a value does not spell
     * what its semantic data type requires. A rule does not catch it: the engine does, and
     * reports that the rule was not decided rather than that the invoice failed it.
     *
     * <p>The one exception an implementation may raise is {@link RulePackException}, which is
     * what an accessor of the context throws when the rule wrote a path the registry does not
     * admit or asked for one value from a path that addresses many. It is a defect of the
     * rule and not of the invoice, and {@link RuleEngine#evaluate(SemanticDocument)} lets it
     * out unchanged rather than turning it into a finding. An implementation raises nothing
     * else: a condition it cannot decide is an empty answer, not an exception.
     *
     * @param context the invoice, at that instance
     * @return the message of the finding if the rule fails; an empty optional if it holds,
     *         and also if it cannot be decided on this document, which a rule says by
     *         answering nothing rather than by guessing
     * @throws RulePackException if the rule addresses a path the registry does not admit
     */
    Optional<String> check(RuleContext context);

    /**
     * Decides the second statement of the rule, which is weighed only where
     * {@link #check(RuleContext)} holds and whose failure is a warning rather than a fault.
     *
     * <p>It is what {@code warn} is in the rule language and it is used for the same thing:
     * a figure the official artefact of one syntax accepts and that of the other rejects.
     * A finding of a document meant for the lenient syntax would be wrong and silence about
     * a document meant for the strict one would be a surprise, so the rule says it at the
     * warning level and names the syntax whose artefact rejects the figure. Most rules carry
     * no second statement and answer nothing here.
     *
     * @param context the invoice, at that instance
     * @return the message of the warning, or an empty optional
     * @throws RulePackException if the rule addresses a path the registry does not admit
     */
    default Optional<String> warn(RuleContext context) {
        return Optional.empty();
    }
}
