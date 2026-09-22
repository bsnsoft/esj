package de.bsnsoft.esj.validate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What a validator has to say about a document (specification, section 9.5): a status,
 * the findings, and a statement of what was covered.
 *
 * <p><strong>An empty finding list is not conformance.</strong> A validator that
 * evaluated nothing also reports nothing, so a caller that reads the findings without the
 * status cannot tell a checked document from an unchecked one. {@link #status()} is where
 * the verdict is said, and it is the only place it is said.
 *
 * <p>The status follows from the findings and from the coverage, and this class derives
 * it rather than taking it:
 * <ul>
 *   <li>{@link ValidationStatus#INVALID} — some finding is an error and is not one of the
 *       codes that record something not evaluated
 *       ({@link FindingCode#recordsSomethingNotEvaluated()}). A defect found is a defect
 *       whatever else was not reached, so this state outranks the next one.</li>
 *   <li>{@link ValidationStatus#INDETERMINATE} — no such error, and something was not
 *       evaluated: a layer is named in {@link #notEvaluated()}, or a finding records that
 *       a check could not be carried out.</li>
 *   <li>{@link ValidationStatus#VALID} — all three layers were evaluated and neither of
 *       the above holds.</li>
 * </ul>
 *
 * <p>The severities alone do not decide it. {@code ESJ-L1-LIMIT} is an error and yields
 * {@code INDETERMINATE}, because it says that this implementation stopped and not that
 * the document is wrong (specification, sections 3.1 and 12.2); {@code ESJ-L2-NOT-CHECKED}
 * and {@code ESJ-L2-EDITION-UNKNOWN} carry the severity {@code info} and yield it for the
 * same reason.
 *
 * <p>Every one of the three layers appears exactly once, either in {@link #evaluated()} or
 * in {@link #notEvaluated()}. A component that covers one layer therefore states what it
 * did <em>not</em> do as well, and {@link #merge(ValidationResult)} composes the results of
 * two components — a reader that ran L1 and a validator that ran L2 and L3 — into the one
 * result a caller reads.
 *
 * <p>Instances are immutable.
 */
public final class ValidationResult {

    private final ValidationStatus status;
    private final List<Finding> findings;
    private final Set<ValidationLayer> evaluated;
    private final Map<ValidationLayer, NotEvaluatedReason> notEvaluated;
    private final List<String> registries;

    private ValidationResult(List<Finding> findings,
                             Map<ValidationLayer, NotEvaluatedReason> notEvaluated,
                             List<String> registries) {
        this.findings = findings;
        this.notEvaluated = notEvaluated;
        this.registries = registries;
        Set<ValidationLayer> covered = EnumSet.allOf(ValidationLayer.class);
        covered.removeAll(notEvaluated.keySet());
        this.evaluated = Collections.unmodifiableSet(covered);
        this.status = statusOf(findings, notEvaluated);
    }

    /**
     * Creates a result from the findings and the layers that were not evaluated. Every
     * layer the map does not name counts as evaluated, and the status follows from the
     * two.
     *
     * @param findings     the findings, in the order the validator produced them
     * @param notEvaluated the layers that were not evaluated, each with its reason
     * @return the result
     * @throws NullPointerException if an argument, an element or a map entry is
     *                              {@code null}
     */
    public static ValidationResult of(Collection<Finding> findings,
                                      Map<ValidationLayer, NotEvaluatedReason> notEvaluated) {
        return of(findings, notEvaluated, List.of());
    }

    /**
     * Creates a result that also names the registries the run measured against.
     *
     * @param findings     the findings, in the order the validator produced them
     * @param notEvaluated the layers that were not evaluated, each with its reason
     * @param registries   the registries the run used, named as they name themselves; it
     *                     is empty where none was available
     * @return the result
     * @throws NullPointerException if an argument, an element or a map entry is
     *                              {@code null}
     */
    public static ValidationResult of(Collection<Finding> findings,
                                      Map<ValidationLayer, NotEvaluatedReason> notEvaluated,
                                      Collection<String> registries) {
        Objects.requireNonNull(findings, "findings");
        Objects.requireNonNull(notEvaluated, "notEvaluated");
        Objects.requireNonNull(registries, "registries");
        Map<ValidationLayer, NotEvaluatedReason> reasons = new EnumMap<>(ValidationLayer.class);
        notEvaluated.forEach((layer, reason) -> reasons.put(
                Objects.requireNonNull(layer, "layer"),
                Objects.requireNonNull(reason, "reason")));
        return new ValidationResult(List.copyOf(findings),
                Collections.unmodifiableMap(reasons),
                List.copyOf(new LinkedHashSet<>(registries)));
    }

    /**
     * Returns the verdict.
     *
     * @return the status, never {@code null}
     */
    public ValidationStatus status() {
        return status;
    }

    /**
     * Returns the findings, in the order they were produced.
     *
     * @return an unmodifiable list, possibly empty
     */
    public List<Finding> findings() {
        return findings;
    }

    /**
     * Returns the layers that were evaluated.
     *
     * @return an unmodifiable set, possibly empty
     */
    public Set<ValidationLayer> evaluated() {
        return evaluated;
    }

    /**
     * Returns the layers that were not evaluated, each with the reason.
     *
     * @return an unmodifiable map, possibly empty
     */
    public Map<ValidationLayer, NotEvaluatedReason> notEvaluated() {
        return notEvaluated;
    }

    /**
     * Returns the registries the run measured against, in the spelling they carry
     * themselves. It is empty where no registry was available for the edition the
     * document names, and where the result covers layer L1 alone, which needs none.
     *
     * @return an unmodifiable list, possibly empty
     */
    public List<String> registries() {
        return registries;
    }

    /**
     * Composes this result with another about the same document, which is how the layers
     * of separate components add up to one answer: a reader reports layer L1 and a
     * structural validator reports L2 and L3, and neither of them alone may say
     * {@code VALID} (specification, sections 3.5 and 9.5).
     *
     * <p>A layer either result evaluated counts as evaluated. A layer neither evaluated
     * keeps the stronger of the two reasons, which is the one declared first in
     * {@link NotEvaluatedReason}. The findings of this result come first, the registries
     * of both are kept in that order without repetition, and the status is derived afresh
     * from the whole.
     *
     * @param other the other result about the same document
     * @return the composed result
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public ValidationResult merge(ValidationResult other) {
        Objects.requireNonNull(other, "other");
        List<Finding> together = new ArrayList<>(findings);
        together.addAll(other.findings);
        Map<ValidationLayer, NotEvaluatedReason> reasons = new EnumMap<>(ValidationLayer.class);
        for (ValidationLayer layer : ValidationLayer.values()) {
            NotEvaluatedReason mine = notEvaluated.get(layer);
            NotEvaluatedReason theirs = other.notEvaluated.get(layer);
            if (mine != null && theirs != null) {
                reasons.put(layer, mine.compareTo(theirs) <= 0 ? mine : theirs);
            }
        }
        List<String> named = new ArrayList<>(registries);
        named.addAll(other.registries);
        return of(together, reasons, named);
    }

    /**
     * Tells whether two results carry the same findings, the same coverage and the same
     * registries. The status follows from those, so it is not compared separately.
     *
     * @param object the object to compare with
     * @return {@code true} if the two results say the same thing
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        return object instanceof ValidationResult other
                && findings.equals(other.findings)
                && notEvaluated.equals(other.notEvaluated)
                && registries.equals(other.registries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(findings, notEvaluated, registries);
    }

    /**
     * Returns the status, the number of findings and the coverage as one line.
     *
     * @return a short description of the result
     */
    @Override
    public String toString() {
        return "ValidationResult[" + status + ", findings=" + findings.size()
                + ", evaluated=" + evaluated + ", notEvaluated=" + notEvaluated + "]";
    }

    /**
     * Derives the status of the specification, section 9.5 from the findings and the
     * coverage.
     */
    private static ValidationStatus statusOf(List<Finding> findings,
                                             Map<ValidationLayer, NotEvaluatedReason> notEvaluated) {
        boolean unresolved = !notEvaluated.isEmpty();
        for (Finding finding : findings) {
            if (finding.code().recordsSomethingNotEvaluated()) {
                unresolved = true;
            } else if (finding.isError()) {
                return ValidationStatus.INVALID;
            }
        }
        return unresolved ? ValidationStatus.INDETERMINATE : ValidationStatus.VALID;
    }
}
