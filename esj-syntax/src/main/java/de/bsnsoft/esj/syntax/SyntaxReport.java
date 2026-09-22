package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrSyntax;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the syntax engine found in one document, and what it ran to find it.
 *
 * <p>A report that listed only findings could not be read: a document with no finding
 * looks the same whether every artefact was applied to it or none was. So the report
 * carries both halves — the findings, and which components ran, which did not and why.
 *
 * <p>The findings of this engine are findings about the document's binding to its XML
 * syntax. They are a layer of their own and are never presented as conformance to the
 * ESJ format, which is defined by layers L1 to L3 of the specification alone.
 *
 * @param verdict         what the engine has to say: {@code INVALID} on a fatal finding,
 *                        {@code INDETERMINATE} where nothing fatal was found and a rule
 *                        set the document asked for did not run, {@code VALID} otherwise
 * @param syntax          the syntax of the document, empty where it could not be parsed
 * @param customizationId the customization identifier the document names in BT-24, empty
 *                        where it names none or could not be read
 * @param pack            the pack whose components ran, empty where none did
 * @param findings        every finding, in {@link SyntaxFinding#ORDER}
 * @param ran             what ran, with what it cost
 * @param skipped         what did not run, each with its reason
 * @param profileNote     a note in English where the document names a profile no
 *                        component of the pack recognizes
 * @param profileRulesSkipped whether a rule set of the pack was left out because of the
 *                        profile the document names. A verdict of {@link Verdict#VALID}
 *                        that follows it was reached without those rules, so a caller
 *                        that must know what a verdict covers reads this rather than the
 *                        note, which is a sentence for a person
 * @param profileRulesMissing whether the rule set that was left out was left out for a
 *                        specification the document names and the pack carries no rules
 *                        for. This is the half of {@code profileRulesSkipped} that decides
 *                        the verdict: leaving the CIUS rule sets unused for a document that
 *                        names EN 16931 and no CIUS is a complete check, and leaving them
 *                        unused for a document that named one is a gap
 * @param duration        how long the whole validation took
 */
public record SyntaxReport(Verdict verdict,
                           Optional<XrSyntax> syntax,
                           String customizationId,
                           Optional<Pack> pack,
                           List<SyntaxFinding> findings,
                           List<ComponentRun> ran,
                           List<SkippedComponent> skipped,
                           Optional<String> profileNote,
                           boolean profileRulesSkipped,
                           boolean profileRulesMissing,
                           Duration duration) {

    /**
     * Creates a report, copying the lists it is given.
     *
     * @param verdict         what the engine has to say: {@code INVALID} on a fatal finding,
     *                        {@code INDETERMINATE} where nothing fatal was found and a rule
     *                        set the document asked for did not run, {@code VALID} otherwise
     * @param syntax          the syntax of the document, empty where it could not be parsed
     * @param customizationId the customization identifier the document names in BT-24, empty
     *                        where it names none or could not be read
     * @param pack            the pack whose components ran, empty where none did
     * @param findings        every finding, in {@link SyntaxFinding#ORDER}
     * @param ran             what ran, with what it cost
     * @param skipped         what did not run, each with its reason
     * @param profileNote     a note in English where the document names a profile no
     *                        component of the pack recognizes
     * @param profileRulesSkipped whether a rule set of the pack was left out because of the
     *                        profile the document names. A verdict of {@link Verdict#VALID}
     *                        that follows it was reached without those rules, so a caller
     *                        that must know what a verdict covers reads this rather than the
     *                        note, which is a sentence for a person
     * @param profileRulesMissing whether the rule set that was left out was left out for a
     *                        specification the document names and the pack carries no rules
     *                        for. This is the half of {@code profileRulesSkipped} that decides
     *                        the verdict: leaving the CIUS rule sets unused for a document that
     *                        names EN 16931 and no CIUS is a complete check, and leaving them
     *                        unused for a document that named one is a gap
     * @param duration        how long the whole validation took
     * @throws NullPointerException if an argument is {@code null}
     */
    public SyntaxReport {
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(customizationId, "customizationId");
        Objects.requireNonNull(pack, "pack");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        ran = List.copyOf(Objects.requireNonNull(ran, "ran"));
        skipped = List.copyOf(Objects.requireNonNull(skipped, "skipped"));
        Objects.requireNonNull(profileNote, "profileNote");
        Objects.requireNonNull(duration, "duration");
    }

    /**
     * Returns the findings that decide the verdict.
     *
     * @return the fatal findings, in {@link SyntaxFinding#ORDER}
     */
    public List<SyntaxFinding> fatal() {
        return findings.stream().filter(SyntaxFinding::fatal).toList();
    }

    /**
     * Returns the findings that do not decide the verdict.
     *
     * @return the warnings and the findings reported for information, in
     *         {@link SyntaxFinding#ORDER}
     */
    public List<SyntaxFinding> warnings() {
        return findings.stream().filter(finding -> !finding.fatal()).toList();
    }

    /**
     * Returns the findings of one engine.
     *
     * @param engine the engine
     * @return its findings, in {@link SyntaxFinding#ORDER}
     */
    public List<SyntaxFinding> findings(Engine engine) {
        return findings.stream().filter(finding -> finding.engine() == engine).toList();
    }
}
