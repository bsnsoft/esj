package de.bsnsoft.esj.rules;

/**
 * What kind of thing an expression of the rule language produces, as far as the compiler
 * can tell without a document.
 *
 * <p>The rule language has no type declarations, and it needs none: the type of a value
 * comes from the registry, which records the semantic data type of every business term once
 * ({@code SPEC.md} section 6.2). A path to BT-131 is a decimal because the registry says
 * Amount, a path to BT-2 is a date because it says Date, and a path to BT-1 is text. An
 * operator then knows what it is handed, so an arithmetic operator over a date and a code
 * list membership test over an amount are refused when the pack is compiled rather than
 * producing something surprising once per invoice.
 *
 * <p>{@link #ANY} is what a literal starts out as. {@code {"const": "S"}} is a code beside a
 * code and a date beside a date, and the compiler fixes which when it sees the other side of
 * the comparison: a literal compared with a decimal is parsed as a decimal <em>then</em>, so
 * a literal that is not the number it is compared against is a defect of the pack and is
 * found once.
 */
enum RuleType {

    /** An exact decimal number. */
    DECIMAL,

    /** A calendar date. */
    DATE,

    /** Text: the content of a value whose semantic data type is none of the numeric ones. */
    TEXT,

    /** A truth value, or the absence of one. */
    BOOLEAN,

    /** A literal whose type the compiler takes from what it stands beside. */
    ANY;

    /**
     * Returns the token this type is named by in the message of a compile failure.
     *
     * @return the token
     */
    String token() {
        return switch (this) {
            case DECIMAL -> "a decimal";
            case DATE -> "a date";
            case TEXT -> "text";
            case BOOLEAN -> "a truth value";
            case ANY -> "a literal";
        };
    }
}
