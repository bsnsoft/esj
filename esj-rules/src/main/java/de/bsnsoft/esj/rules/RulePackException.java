package de.bsnsoft.esj.rules;

/**
 * A rule pack that cannot be compiled.
 *
 * <p>This exception is never about an invoice. It says that a rule file is not a rule
 * file, that an expression uses an operator the language does not have, that a path names
 * a business term the registry does not know, or that a code list a rule asks for has no
 * snapshot in the pack. All of those are defects of the pack, which is the operator's own
 * material, and they are found once when the pack is compiled rather than once per
 * document.
 *
 * <p>A defect of a document is never signalled this way. It is a
 * {@link RuleFinding} ({@code SPEC.md} section 9.5), and a document with fifty problems
 * produces fifty findings in one pass.
 */
public final class RulePackException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public RulePackException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and a cause.
     *
     * @param message the detail message, in English
     * @param cause   the underlying failure
     */
    public RulePackException(String message, Throwable cause) {
        super(message, cause);
    }
}
