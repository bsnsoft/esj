package de.bsnsoft.esj.rules;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One thing a rule had to say about a document.
 *
 * <p>Unlike a finding of the syntax engine, the message is this project's own text, and
 * deliberately so: no rule of a published artefact is translated or restated here, and a
 * message that read like one would claim an authority this engine does not have. A message
 * names the business terms the rule is about and the values it saw, in this project's
 * words, so that a reader can tell what the invoice says without opening it.
 *
 * <p>{@code paths} is where the rule looked. A path the rule read once appears as the path
 * it read; a value the rule read as an aggregate — the sum over every invoice line, say —
 * appears as the pattern, {@code /BG-25/*}{@code /BT-131}, because a finding that listed
 * three hundred thousand paths would be unreadable and unbounded. The order is the order
 * the rule read them in, which is the order of the expression and therefore the same on
 * every run.
 *
 * <p>Every finding names where it came from, because a report has to survive the day a
 * pack is replaced: the identifier and the version of the pack, and the engine, which is
 * {@code native} for every finding of this module. A finding of this module is a finding
 * about the business rules of a standard and is never a statement about conformance to the
 * ESJ format, which is defined by layers L1 to L3 of the specification alone
 * ({@code SPEC.md} section 9.4).
 *
 * @param code        the rule identifier, for example {@code BR-CO-10}
 * @param category    what kind of thing the finding is about, derived from the identifier
 * @param severity    how much it weighs
 * @param message     human-readable English text, this project's own
 * @param paths       the semantic paths and path patterns the rule read
 * @param packId      the identifier of the pack the rule belongs to
 * @param packVersion the version of that pack
 * @param engine      which engine produced the finding; {@code native} here
 */
public record RuleFinding(String code,
                          RuleCategory category,
                          RuleSeverity severity,
                          String message,
                          List<String> paths,
                          String packId,
                          String packVersion,
                          String engine) {

    /** The engine name every finding of this module carries. */
    public static final String NATIVE_ENGINE = "native";

    /**
     * The order a report lists findings in: the rule identifier, then the first path the
     * rule read.
     *
     * <p>It mentions nothing that varies between runs, so two runs over the same document
     * with the same pack produce the same list in the same order. The remaining members
     * are compared after those two so that the order is total even where a rule fires
     * twice with the same identifier and the same first path.
     */
    public static final Comparator<RuleFinding> ORDER =
            Comparator.comparing(RuleFinding::code)
                    .thenComparing(RuleFinding::firstPath)
                    .thenComparing(RuleFinding::severity)
                    .thenComparing(RuleFinding::message);

    /**
     * Copies the path list and checks that every part is present.
     *
     * @param code        the rule identifier, for example {@code BR-CO-10}
     * @param category    what kind of thing the finding is about, derived from the identifier
     * @param severity    how much it weighs
     * @param message     human-readable English text, this project's own
     * @param paths       the semantic paths and path patterns the rule read
     * @param packId      the identifier of the pack the rule belongs to
     * @param packVersion the version of that pack
     * @param engine      which engine produced the finding; {@code native} here
     * @throws NullPointerException if a part is {@code null}
     */
    public RuleFinding {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(engine, "engine");
        paths = List.copyOf(paths);
    }

    /**
     * Tells whether this finding decides the verdict.
     *
     * @return whether the severity is {@link RuleSeverity#FATAL}
     */
    public boolean fatal() {
        return severity == RuleSeverity.FATAL;
    }

    /**
     * Returns the first path the rule read, or the empty string where it read none.
     *
     * @return the first path, for ordering and for a one-line report
     */
    public String firstPath() {
        return paths.isEmpty() ? "" : paths.get(0);
    }

    /**
     * Returns the finding as one line: the category, the identifier, the severity and the
     * message.
     *
     * @return a short description of the finding
     */
    @Override
    public String toString() {
        return category.token() + " " + code + " [" + severity.token() + "] " + message;
    }
}
