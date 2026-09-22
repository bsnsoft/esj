package de.bsnsoft.esj.syntax;

import java.util.Comparator;
import java.util.Objects;

/**
 * One thing an artefact said about a document.
 *
 * <p>The message is the artefact's own text, with its whitespace normalized and nothing
 * else done to it. It is not translated, not shortened and not restated in this
 * project's words: the bodies that own the rules own their wording, and a report that
 * paraphrased a rule would be a second source for something that has exactly one. The
 * same goes for the location, which is the expression the artefact reports, as it
 * reports it.
 *
 * <p>Every finding names where it came from, because a report has to survive the day a
 * pack is replaced: the pack, its version, its release and the component inside it.
 * A finding of the parser belongs to no pack, and its four origin members are then empty.
 *
 * <p>A finding carries two levels. The {@code flag} is the one the artefact set on the
 * rule and is never touched, so a comparison against another tool running the same
 * artefact is made on it. The {@code severity} is the one the profile of the document
 * asks for, which is the flag unless the profile levels that rule itself, and it is the
 * one the verdict is made on. {@link PackLevels} says why a profile may.
 *
 * @param engine      which check produced the finding
 * @param category    what kind of thing the finding is about
 * @param severity    how serious it is for a document of this profile, which decides the
 *                    verdict
 * @param flag        how serious the artefact itself levelled the rule
 * @param code        the rule identifier, verbatim, or the key of a parser or schema
 *                    failure
 * @param message     the text the artefact produced, whitespace normalized
 * @param location    the expression the artefact reports, empty where it reports none
 * @param line        the line of the document the finding is at, or {@code -1}
 * @param column      the column of the document the finding is at, or {@code -1}
 * @param packId      the identifier of the pack that produced it, empty for the parser
 * @param packVersion the profile version of that pack, empty for the parser
 * @param packRelease the release of that pack, empty for the parser
 * @param component   the component inside that pack, empty for the parser
 */
public record SyntaxFinding(Engine engine,
                            FindingCategory category,
                            Severity severity,
                            Severity flag,
                            String code,
                            String message,
                            String location,
                            int line,
                            int column,
                            String packId,
                            String packVersion,
                            String packRelease,
                            String component) {

    /**
     * The order a report lists findings in: the engine, then the category, then the rule
     * identifier, then the location, and the message, line and column after those.
     *
     * <p>It is a total order on the content of a finding and mentions nothing that varies
     * between runs, so two runs over the same bytes with the same pack produce the same
     * list in the same order.
     */
    public static final Comparator<SyntaxFinding> ORDER =
            Comparator.comparing(SyntaxFinding::engine)
                    .thenComparing(SyntaxFinding::category)
                    .thenComparing(SyntaxFinding::code)
                    .thenComparing(SyntaxFinding::location)
                    .thenComparing(SyntaxFinding::message)
                    .thenComparingInt(SyntaxFinding::line)
                    .thenComparingInt(SyntaxFinding::column);

    /**
     * Creates a finding.
     *
     * @param engine      which check produced the finding
     * @param category    what kind of thing the finding is about
     * @param severity    how serious it is for a document of this profile, which decides the
     *                    verdict
     * @param flag        how serious the artefact itself levelled the rule
     * @param code        the rule identifier, verbatim, or the key of a parser or schema
     *                    failure
     * @param message     the text the artefact produced, whitespace normalized
     * @param location    the expression the artefact reports, empty where it reports none
     * @param line        the line of the document the finding is at, or {@code -1}
     * @param column      the column of the document the finding is at, or {@code -1}
     * @param packId      the identifier of the pack that produced it, empty for the parser
     * @param packVersion the profile version of that pack, empty for the parser
     * @param packRelease the release of that pack, empty for the parser
     * @param component   the component inside that pack, empty for the parser
     * @throws NullPointerException if an argument is {@code null}
     */
    public SyntaxFinding {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(flag, "flag");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(packVersion, "packVersion");
        Objects.requireNonNull(packRelease, "packRelease");
        Objects.requireNonNull(component, "component");
    }

    /**
     * Tells whether this finding decides the verdict.
     *
     * @return whether its severity is {@link Severity#FATAL}
     */
    public boolean fatal() {
        return severity == Severity.FATAL;
    }

    /**
     * Tells whether the profile of the document levels this rule differently from the
     * artefact that reports it.
     *
     * @return whether {@link #severity()} and {@link #flag()} differ
     */
    public boolean releveled() {
        return severity != flag;
    }
}
