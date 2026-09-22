package de.bsnsoft.esj.typed.build;

import de.bsnsoft.esj.validate.Finding;
import java.util.ArrayList;
import java.util.List;

/**
 * What a check of a half-written invoice found, in three kinds.
 *
 * <p>The kinds are kept apart because they are statements of different weight.
 * {@code findings} are the structural layers L2 and L3 of the specification and say
 * whether the document is a conformant ESJ document. {@code missing} names the members
 * the model, or the profile the builder was opened with, asks for and the invoice does
 * not carry; a profile narrowing is not a finding of the format and is therefore not
 * reported as one. {@code violations} are business rules and are never a statement about
 * conformance to the ESJ format; only a fatal one stops a build, and the rest are carried
 * so that a caller sees what the rules had to say.
 *
 * @param findings   what the structural validation of {@code esj-core} found
 * @param missing    the members the model or the profile asks for and the invoice lacks
 * @param violations what the business rules had to say, empty where none were run
 */
public record BuildReport(List<Finding> findings,
                          List<MissingTerm> missing,
                          List<RuleViolation> violations) {

    /**
     * Copies the three lists.
     *
     * @param findings   what the structural layers had to say
     * @param missing    the members the model or the profile asks for and the invoice lacks
     * @param violations what the business rules had to say, empty where none were run
     * @throws NullPointerException if a list is {@code null}
     */
    public BuildReport {
        findings = List.copyOf(findings);
        missing = List.copyOf(missing);
        violations = List.copyOf(violations);
    }

    /**
     * Tells whether the check found nothing that stops the build.
     *
     * <p>A finding that records something the validator did not evaluate rather than
     * something that is wrong ({@link de.bsnsoft.esj.validate.FindingCode
     * #recordsSomethingNotEvaluated()}) is not a defect of the invoice and does not stop
     * the build; it stays in the report.
     *
     * <p>A business rule violation stops the build only where the rule set calls it fatal.
     * A warning and a finding the rules could not decide stay in the report and refuse
     * nothing.
     *
     * @return whether there is no error finding about the document, no missing member and
     *         no fatal violation
     */
    public boolean ok() {
        return missing.isEmpty()
                && violations.stream().noneMatch(RuleViolation::fatal)
                && findings.stream().noneMatch(
                        finding -> finding.isError()
                                && !finding.code().recordsSomethingNotEvaluated());
    }

    /**
     * Returns one line per thing the check found, in the order of the three kinds.
     *
     * @return the lines, empty where the check found nothing
     */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (MissingTerm term : missing) {
            lines.add(term.toString());
        }
        for (Finding finding : findings) {
            lines.add(finding.toString());
        }
        for (RuleViolation violation : violations) {
            lines.add(violation.toString());
        }
        return List.copyOf(lines);
    }
}
