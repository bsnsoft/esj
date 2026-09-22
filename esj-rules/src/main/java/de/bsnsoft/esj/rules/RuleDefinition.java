package de.bsnsoft.esj.rules;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One rule as its file writes it, before it is compiled against a registry.
 *
 * <p>The members below are the whole of a rule, and each of them answers a question a
 * reader of a validation report asks. {@code id} is what the finding is called, and it is
 * the identifier the standard gives the rule rather than one this project invented, so that
 * a report can be compared with a report of any other tool. {@code severity} is how much it
 * weighs. {@code context} says what the rule is a statement about — the document, or every
 * instance of a business group — and therefore how often it is evaluated. {@code terms}
 * lists the business terms the rule reads, which is documentation for a person and the
 * column a coverage table is built from. {@code assert} is the statement itself.
 * {@code message} is what the finding says, with the values the rule saw put into it.
 * {@code source} is the clause of the standard the statement comes from, and it is a
 * reference and never a quotation: the norm text is not reproduced in this repository.
 * {@code note}, which most rules leave out, says why the rule reads the way it does where
 * the statement admits more than one reading. {@code warn}, which most rules leave out as
 * well, is a second assertion with its own message: it is weighed only where the first one
 * holds and its failure is a warning rather than a fault.
 *
 * <p>The expression and the message bindings are held but not exposed. A caller has no use
 * for an uncompiled expression tree, and the compiled form belongs to
 * {@link RuleEngine}.
 */
public final class RuleDefinition {

    private final String id;
    private final RuleSeverity severity;
    private final String context;
    private final List<String> terms;
    private final Json assertion;
    private final Map<String, Json> bindings;
    private final String message;
    private final String source;
    private final String note;
    private final Warning warning;

    /**
     * The second assertion of a rule: a statement that is weighed only where the first one
     * holds, and whose failure is a warning rather than a fault.
     *
     * @param assertion the statement, as the rule file writes it
     * @param message   the template of the message the warning carries
     */
    record Warning(Json assertion, String message) {
    }

    RuleDefinition(String id, RuleSeverity severity, String context, List<String> terms,
                   Json assertion, Map<String, Json> bindings, String message, String source,
                   String note, Warning warning) {
        this.id = Objects.requireNonNull(id, "id");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.context = Objects.requireNonNull(context, "context");
        this.terms = List.copyOf(terms);
        this.assertion = Objects.requireNonNull(assertion, "assertion");
        this.bindings = Map.copyOf(bindings);
        this.message = Objects.requireNonNull(message, "message");
        this.source = Objects.requireNonNull(source, "source");
        this.note = note;
        this.warning = warning;
    }

    /**
     * Returns the identifier of the rule, which is the code of every finding it produces.
     *
     * @return the identifier, for example {@code BR-CO-10}
     */
    public String id() {
        return id;
    }

    /**
     * Returns how much a finding of this rule weighs.
     *
     * @return {@link RuleSeverity#FATAL} or {@link RuleSeverity#WARNING}
     */
    public RuleSeverity severity() {
        return severity;
    }

    /**
     * Returns the context the rule is evaluated in: a business group path pattern such as
     * {@code /BG-25/*}, or {@code /} for the document.
     *
     * @return the context pattern
     */
    public String context() {
        return context;
    }

    /**
     * Returns the identifiers of the business terms and groups the rule reads.
     *
     * @return the identifiers, as the rule file lists them
     */
    public List<String> terms() {
        return terms;
    }

    /**
     * Returns the template of the message a finding of this rule carries.
     *
     * @return the template, with its placeholders unexpanded
     */
    public String message() {
        return message;
    }

    /**
     * Returns the clause of the standard the rule states.
     *
     * @return the reference, for example {@code EN 16931-1, 6.4.2, BR-CO-10}
     */
    public String source() {
        return source;
    }

    /**
     * Returns why the rule reads the way it does, where the statement of the standard
     * admits more than one reading.
     *
     * <p>A tolerance, a rounding or a place where the official validation artefacts settle
     * something the norm text leaves open is recorded here rather than in a comment,
     * because it is the part of a rule a reader is entitled to question. Most rules need
     * no such explanation and carry none.
     *
     * @return the note, or an empty optional
     */
    public Optional<String> note() {
        return Optional.ofNullable(note);
    }

    /**
     * Returns the category a finding of this rule carries, which follows from the
     * identifier.
     *
     * @return the category
     */
    public RuleCategory category() {
        return RuleCategory.of(id);
    }

    Json assertion() {
        return assertion;
    }

    /**
     * Returns the second assertion of the rule, where it carries one.
     *
     * @return the second assertion, or an empty optional
     */
    Optional<Warning> warning() {
        return Optional.ofNullable(warning);
    }

    Map<String, Json> bindings() {
        return bindings;
    }

    /**
     * Returns the identifier and the context, which is what identifies a rule in a message.
     *
     * @return a short description of the rule
     */
    @Override
    public String toString() {
        return id + " at " + context;
    }
}
