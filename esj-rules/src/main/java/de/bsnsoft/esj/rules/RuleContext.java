package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a rule written in Java sees: one invoice, at one business group instance, through the
 * same door a rule of the rule language reads it through.
 *
 * <p>The door matters more than it looks. Every read made here is recorded, in the order it
 * was made, and that record is what the finding reports as the paths the rule looked at. A
 * Java rule that reads through this class therefore produces a finding of exactly the shape a
 * rule of the language produces, which is what lets the two be compared — and the project
 * does compare them: a rule written both ways is expected to produce the same finding, and a
 * test says so.
 *
 * <p>The paths are written relative to the context of the rule, as in the rule language: at
 * the document context they are absolute, and inside {@code /BG-25/*} the path
 * {@code /BT-131} is the net amount of the line the rule is looking at. They are resolved
 * against the registry the first time they are used, so a path a Java rule invented fails
 * with the same message a rule file would have got.
 *
 * <p>A Java rule exists for what the language cannot say, not for what it would rather say
 * differently. The language has thirty-one operators and no way to add another from a
 * file, and a rule that reaches past them — a rule that has to group the lines by something
 * the document does not name, or compare a set with a set — is the reason this exists.
 */
public final class RuleContext {

    private final Evaluation evaluation;
    private final SemanticPath base;
    private final Resolver resolver;

    RuleContext(Evaluation evaluation, SemanticPath base, Resolver resolver) {
        this.evaluation = evaluation;
        this.base = base;
        this.resolver = resolver;
    }

    /**
     * Returns the business group instance the rule is looking at.
     *
     * @return the instance path, the root for a rule whose context is the document
     */
    public SemanticPath base() {
        return base;
    }

    /**
     * Returns the registry the document is measured against.
     *
     * @return the registry
     */
    public Registry registry() {
        return resolver.registry();
    }

    /**
     * Returns the absolute path a relative one addresses in this instance.
     *
     * @param path the path, relative to the context of the rule
     * @return the absolute path
     * @throws RulePackException    if the path addresses a group or more than one value
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public SemanticPath resolve(String path) {
        Objects.requireNonNull(path, "path");
        return resolver.singleValuePath(path).concrete(base).orElseThrow();
    }

    /**
     * Tells whether the document carries anything at a path or pattern.
     *
     * @param path the path or pattern, relative to the context of the rule
     * @return whether at least one value or business group instance is there
     * @throws RulePackException    if the path is not one the registry admits
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public boolean exists(String path) {
        Objects.requireNonNull(path, "path");
        PathPattern pattern = resolver.pattern(path);
        return pattern.endsAtGroup()
                ? !evaluation.instances(pattern, base).isEmpty()
                : !evaluation.matches(pattern, base).isEmpty();
    }

    /**
     * Reads the content of one value exactly as the document carries it.
     *
     * <p>Nothing is converted: a decimal comes back spelled the way the document spells it,
     * which is what a rule about the spelling of a number needs and what a message should
     * show. {@link #decimal} is the accessor that reads a number as a number.
     *
     * @param path the path, relative to the context of the rule
     * @return the content, or an empty optional where the document does not carry the value
     * @throws RulePackException    if the path is not one value
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public Optional<String> text(String path) {
        return value(path).map(SemanticValue::asString);
    }

    /**
     * Reads one value with its supplementary components, as the document carries it.
     *
     * @param path the path, relative to the context of the rule
     * @return the value, or an empty optional where the document does not carry it
     * @throws RulePackException    if the path is not one value
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public Optional<SemanticValue> value(String path) {
        Objects.requireNonNull(path, "path");
        return evaluation.raw(resolver.singleValuePath(path), base);
    }

    /**
     * Reads one value as an exact decimal.
     *
     * @param path the path, relative to the context of the rule
     * @return the number, or an empty optional where the document does not carry the value
     * @throws RulePackException    if the path is not one value, or names a term whose
     *                              semantic data type is not numeric
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public Optional<BigDecimal> decimal(String path) {
        return typed(path, SemanticType::isDecimal, "numeric").map(RuleValue::decimal);
    }

    /**
     * Reads one value as a calendar date.
     *
     * @param path the path, relative to the context of the rule
     * @return the date, or an empty optional where the document does not carry the value
     * @throws RulePackException    if the path is not one value, or names a term whose
     *                              semantic data type is not Date
     * @throws NullPointerException if {@code path} is {@code null}
     */
    public Optional<LocalDate> date(String path) {
        return typed(path, type -> type == SemanticType.DATE, "a date").map(RuleValue::date);
    }

    /**
     * Adds up the values a pattern addresses, exactly.
     *
     * @param pattern the pattern, relative to the context of the rule
     * @return the sum, which is zero where the pattern addresses nothing
     * @throws RulePackException    if the pattern does not address values of a numeric term
     * @throws NullPointerException if {@code pattern} is {@code null}
     */
    public BigDecimal sum(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        PathPattern compiled = resolver.pattern(pattern);
        SemanticType type = resolver.datatypeOf(compiled);
        if (!type.isDecimal()) {
            throw new RulePackException(compiled.lastTerm() + " is " + type.registryDatatype()
                    + ", and a sum is taken over a numeric term");
        }
        evaluation.record(compiled.absoluteText(base));
        return evaluation.aggregate("sum " + compiled.absoluteText(base), base.isRoot(), () -> {
            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<SemanticPath, SemanticValue> match : evaluation.matches(compiled, base)) {
                total = total.add(Evaluation.convert(match.getValue(), type, match.getKey()).decimal());
            }
            return RuleValue.of(total);
        }).decimal();
    }

    /**
     * Counts the values or the business group instances a pattern addresses.
     *
     * @param pattern the pattern, relative to the context of the rule
     * @return how many there are
     * @throws RulePackException    if the pattern is not one the registry admits
     * @throws NullPointerException if {@code pattern} is {@code null}
     */
    public int count(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        PathPattern compiled = resolver.pattern(pattern);
        evaluation.record(compiled.absoluteText(base));
        return compiled.endsAtGroup()
                ? evaluation.instances(compiled, base).size()
                : evaluation.matches(compiled, base).size();
    }

    /**
     * Returns the business group instances a pattern addresses, in canonical order.
     *
     * @param pattern the pattern, relative to the context of the rule, ending at a business
     *                group
     * @return the instances
     * @throws RulePackException    if the pattern does not end at a business group
     * @throws NullPointerException if {@code pattern} is {@code null}
     */
    public List<SemanticPath> instances(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        PathPattern compiled = resolver.pattern(pattern);
        if (!compiled.endsAtGroup()) {
            throw new RulePackException(compiled.lastTerm() + " is a business term, and"
                    + " instances are of a business group");
        }
        return evaluation.instances(compiled, base);
    }

    /**
     * Returns the values a pattern addresses, in canonical order, with their paths.
     *
     * @param pattern the pattern, relative to the context of the rule
     * @return the paths and the contents, as the document carries them
     * @throws RulePackException    if the pattern does not address values
     * @throws NullPointerException if {@code pattern} is {@code null}
     */
    public List<Map.Entry<SemanticPath, SemanticValue>> values(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        PathPattern compiled = resolver.pattern(pattern);
        if (compiled.endsAtGroup()) {
            throw new RulePackException(compiled.lastTerm()
                    + " is a business group, and only a business term carries a value");
        }
        return List.copyOf(evaluation.matches(compiled, base));
    }

    /**
     * Returns the values a pattern addresses as exact decimals, with their paths.
     *
     * <p>It is what a rule that has to put several repeatable groups beside each other
     * needs: the net amounts of every invoice line with the path each one is at, so that
     * they can be joined with the category codes of the same lines. Reading them one by one
     * is not possible, because a rule may not write an occurrence index.
     *
     * @param pattern the pattern, relative to the context of the rule
     * @return the paths and the numbers, in canonical order
     * @throws RulePackException    if the pattern does not address values of a numeric term
     * @throws NullPointerException if {@code pattern} is {@code null}
     */
    public List<Map.Entry<SemanticPath, BigDecimal>> decimals(String pattern) {
        return converted(pattern, SemanticType::isDecimal, "numeric", RuleValue::decimal);
    }

    /**
     * Returns the contents of the values a pattern addresses, with their paths.
     *
     * @param pattern the pattern, relative to the context of the rule
     * @return the paths and the contents, in canonical order
     * @throws RulePackException    if the pattern addresses business group instances
     * @throws NullPointerException if {@code pattern} is {@code null}
     */
    public List<Map.Entry<SemanticPath, String>> texts(String pattern) {
        return converted(pattern, type -> true, "any", RuleValue::text);
    }

    private <T> List<Map.Entry<SemanticPath, T>> converted(String pattern,
                                                           Predicate<SemanticType> admits,
                                                           String what,
                                                           java.util.function.Function<RuleValue, T> read) {
        Objects.requireNonNull(pattern, "pattern");
        PathPattern compiled = resolver.pattern(pattern);
        if (compiled.endsAtGroup()) {
            throw new RulePackException(compiled.lastTerm()
                    + " is a business group, and only a business term carries a value");
        }
        SemanticType type = resolver.datatypeOf(compiled);
        if (!admits.test(type)) {
            throw new RulePackException(compiled.lastTerm() + " is " + type.registryDatatype()
                    + ", and this accessor reads " + what);
        }
        List<Map.Entry<SemanticPath, T>> values = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> match : evaluation.matches(compiled, base)) {
            values.add(Map.entry(match.getKey(),
                    read.apply(Evaluation.convert(match.getValue(), type, match.getKey()))));
        }
        return List.copyOf(values);
    }

    /**
     * Returns an answer about the whole document that every rule of the run shares.
     *
     * <p>A rule written in Java often needs a view of the document that costs a pass to
     * build and that does not change while the run lasts: the invoice lines joined with the
     * VAT category of each, say. Several rules need the same view, and without this each of
     * them would build it again. The answer is computed on the first call of a run and
     * handed to every later caller with the same key.
     *
     * <p>Two conditions, and the caller owes both. The answer must be a function of the
     * document alone, because it is handed to rules that did not compute it; and it must be
     * immutable once computed, for the same reason. An answer is remembered only where the
     * rule stands at the document — a view built inside an invoice line is asked once per
     * line, and remembering those would grow with the invoice rather than with the pack.
     *
     * <p>The reads travel with the answer. Whatever the computation read through this class
     * is remembered beside what it computed and replayed to every later caller, so a rule
     * that was handed the answer reports the same paths as the rule that happened to compute
     * it. Without that, sharing would move one rule's reads into another rule's finding and
     * leave the rest of the family with none.
     *
     * @param <T>     what the answer is
     * @param key     what identifies it, unique within the pack; the class that computes it
     *                is a good prefix
     * @param compute how to compute it, called at most once per run
     * @return the answer
     * @throws NullPointerException if {@code key} or {@code compute} is {@code null}
     */
    public <T> T shared(String key, Supplier<T> compute) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(compute, "compute");
        return evaluation.shared(base + " " + key, base.isRoot(), compute);
    }

    /**
     * Returns a code list snapshot the pack carries.
     *
     * @param listId the identifier the pack manifest names the list by
     * @return the snapshot
     * @throws RulePackException    if the pack carries no snapshot of that list
     * @throws NullPointerException if {@code listId} is {@code null}
     */
    public CodeList codeList(String listId) {
        Objects.requireNonNull(listId, "listId");
        return evaluation.codeLists().require(listId);
    }

    /**
     * Renders a message with the placeholders of the rule language.
     *
     * <p>This is how a Java rule says the same thing a rule file says: the template is the
     * one a rule file would have written, {@code {/BT-131}} is the value at that path and
     * {@code {.}} is the instance. The placeholder for a bound expression is the one thing
     * that is missing, because a binding is a member of a rule file and a Java rule computes
     * what it wants to show.
     *
     * @param template the template
     * @return the message, with the values of this instance in it
     * @throws RulePackException    if the template names something that cannot be resolved
     * @throws NullPointerException if {@code template} is {@code null}
     */
    public String format(String template) {
        Objects.requireNonNull(template, "template");
        return resolver.template(template).expand(evaluation, base);
    }

    /**
     * Returns a fragment of the document in the form a message may carry it: escaped, as
     * {@code SPEC.md} section 9.5 requires, and cut to the excerpt length the rest of this
     * tool quotes with (section 12.6).
     *
     * <p>Both halves matter and a rule gets them together, because a rule that concatenates
     * a value into its own message has no other place to ask for either. The escaping keeps
     * a value from steering the terminal the report is read on; the cut keeps a report of a
     * large invoice from being as large as the invoice.
     *
     * @param text the text as the document carries it
     * @return the text as a message may carry it, at most eighty characters of content with
     *         an ellipsis where it was cut
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public String escape(String text) {
        Objects.requireNonNull(text, "text");
        return Texts.excerpt(text);
    }

    private Optional<RuleValue> typed(String path, Predicate<SemanticType> admits, String what) {
        Objects.requireNonNull(path, "path");
        PathPattern pattern = resolver.singleValuePath(path);
        SemanticType type = resolver.datatypeOf(pattern);
        if (!admits.test(type)) {
            throw new RulePackException(pattern.lastTerm() + " is " + type.registryDatatype()
                    + ", and this accessor reads " + what);
        }
        RuleValue value = evaluation.read(pattern, base, type);
        return value.isAbsent() ? Optional.empty() : Optional.of(value);
    }

    /**
     * Returns the instance the rule is looking at, which is how a context prints.
     *
     * @return the instance path
     */
    @Override
    public String toString() {
        return base.isRoot() ? "/" : base.toString();
    }
}
