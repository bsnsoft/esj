package de.bsnsoft.esj.typed.build;

import java.util.List;
import java.util.Objects;

/**
 * One business rule an invoice breaks, as an {@link InvoiceRules} reports it.
 *
 * <p>It is the shape this package needs and nothing more: which rule, in whose words,
 * where it looked, and whether it stops a build. A rule engine has a richer finding of its
 * own — the severity in full, the pack and its version, which engine ran it — and the
 * adapter that hands its findings over says which of them are fatal.
 *
 * <p>A violation that is not fatal is a statement about the invoice that does not refuse
 * it: the rule holds and something about the invoice is worth saying, or the rule could not
 * be decided at all. It belongs in the report either way, because a caller that never sees
 * it cannot act on it.
 *
 * @param rule    the rule identifier, for example {@code BR-CO-10}
 * @param message human-readable English text
 * @param paths   the semantic paths and path patterns the rule read
 * @param fatal   whether the invoice fails the rule, and the build is refused
 */
public record RuleViolation(String rule, String message, List<String> paths, boolean fatal) {

    /**
     * Copies the path list and checks that every part is present.
     *
     * @param rule    the rule identifier, for example {@code BR-CO-10}
     * @param message human-readable English text
     * @param paths   the semantic paths and path patterns the rule read
     * @param fatal   whether the invoice fails the rule, and the build is refused
     * @throws NullPointerException if a part is {@code null}
     */
    public RuleViolation {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(message, "message");
        paths = List.copyOf(paths);
    }

    /**
     * Records a fatal violation, which is what a rule set that weighs nothing reports.
     *
     * @param rule    the rule identifier, for example {@code BR-CO-10}
     * @param message human-readable English text
     * @param paths   the semantic paths and path patterns the rule read
     * @throws NullPointerException if a part is {@code null}
     */
    public RuleViolation(String rule, String message, List<String> paths) {
        this(rule, message, paths, true);
    }

    /**
     * Returns the violation as one line: the rule, the message, and a note where the rule
     * does not refuse the invoice.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return fatal ? rule + ": " + message : rule + " (not fatal): " + message;
    }
}
