package de.bsnsoft.esj.rules;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What an expression of the rule language evaluates to.
 *
 * <p>There are four kinds of value and one absence. The absence is the interesting part.
 * An invoice is a document in which most terms are optional, so an expression that reads
 * one asks a question that may have no answer, and an engine has to decide what that
 * means. Treating a missing amount as zero would invent a figure the invoice does not
 * carry; treating a missing value as a rule failure would make one omission fail every
 * rule that mentions the term, and the cardinality layer has already said so once.
 *
 * <p>So absence propagates, in the manner of a three-valued logic. An arithmetic operator,
 * a comparison, a pattern match and a list membership with an absent operand are absent.
 * {@code and} is false as soon as one operand is false, and absent otherwise unless every
 * operand is true; {@code or} is the mirror. Only {@code exists}, {@code absent},
 * {@code count} and an aggregate over no instance are total: they answer about presence
 * and are never absent themselves.
 *
 * <p>An assertion that comes out absent is a rule that could not be decided, and a rule
 * that could not be decided reports nothing. The rule author who wants a missing term to
 * fail writes that, with {@code exists}.
 */
final class RuleValue {

    /** The one absent value. */
    static final RuleValue ABSENT = new RuleValue(null, null, null, null);

    /** The one true value. */
    static final RuleValue TRUE = new RuleValue(null, null, null, Boolean.TRUE);

    /** The one false value. */
    static final RuleValue FALSE = new RuleValue(null, null, null, Boolean.FALSE);

    private final BigDecimal decimal;
    private final LocalDate date;
    private final String text;
    private final Boolean bool;

    private RuleValue(BigDecimal decimal, LocalDate date, String text, Boolean bool) {
        this.decimal = decimal;
        this.date = date;
        this.text = text;
        this.bool = bool;
    }

    static RuleValue of(BigDecimal value) {
        return new RuleValue(value, null, null, null);
    }

    static RuleValue of(LocalDate value) {
        return new RuleValue(null, value, null, null);
    }

    static RuleValue of(String value) {
        return new RuleValue(null, null, value, null);
    }

    static RuleValue of(boolean value) {
        return value ? TRUE : FALSE;
    }

    static RuleValue of(long value) {
        return of(BigDecimal.valueOf(value));
    }

    boolean isAbsent() {
        return decimal == null && date == null && text == null && bool == null;
    }

    boolean isDecimal() {
        return decimal != null;
    }

    boolean isDate() {
        return date != null;
    }

    boolean isText() {
        return text != null;
    }

    boolean isBoolean() {
        return bool != null;
    }

    BigDecimal decimal() {
        return decimal;
    }

    LocalDate date() {
        return date;
    }

    String text() {
        return text;
    }

    /**
     * Returns the truth of a boolean value, or {@code null} where the value is absent.
     *
     * @return {@link Boolean#TRUE}, {@link Boolean#FALSE} or {@code null}
     */
    Boolean truth() {
        return bool;
    }

    /**
     * Returns the value as it appears in a message.
     *
     * <p>A decimal keeps the scale the arithmetic produced, because that is what the rule
     * compared; a caller who wants two decimals asks the rule to round. A date is its ISO
     * form. Text is escaped and cut to the excerpt length of {@link Texts}, so that a rule
     * that quotes a business term quotes enough of it to be recognised and never the whole
     * of a value a sender made large. An absent value reads as the two words that say so,
     * which no business term value can be mistaken for, since a message escapes and quotes
     * what it takes out of the document.
     *
     * @return the text for a message
     */
    String display() {
        if (decimal != null) {
            return decimal.toPlainString();
        }
        if (date != null) {
            return date.toString();
        }
        if (text != null) {
            return Texts.excerpt(text);
        }
        if (bool != null) {
            return bool.toString();
        }
        return "(absent)";
    }

    @Override
    public String toString() {
        return display();
    }
}
