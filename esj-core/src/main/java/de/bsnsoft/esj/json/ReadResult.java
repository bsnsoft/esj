package de.bsnsoft.esj.json;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.validate.NotEvaluatedReason;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.validate.ValidationResult;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a reader has to say about a byte sequence when it was asked to report rather than
 * to reject: the document, where one could be built, and the layer L1 findings it met
 * (specification, section 9.5).
 *
 * <p>The document is present whenever the reader got to the end of the input. It then
 * holds every value the reader could build, and the findings name the members it had to
 * leave out. The document is absent when a problem made parsing impossible — malformed
 * JSON, a duplicate member name, a broken envelope, a limit — and the last finding names
 * that problem.
 *
 * @param document the document, or an empty optional if parsing could not be completed
 * @param findings the findings, in the order the reader met them, oldest first
 */
public record ReadResult(Optional<SemanticDocument> document, List<Finding> findings) {

    /**
     * Copies the findings.
     *
     * @param document the document, or an empty optional if parsing could not be completed
     * @param findings the findings, in the order the reader met them, oldest first
     * @throws NullPointerException if a part is {@code null}
     */
    public ReadResult {
        Objects.requireNonNull(document, "document");
        findings = List.copyOf(findings);
    }

    /**
     * Tells whether the byte sequence is a well-formed document: a document was built and
     * no finding is an error.
     *
     * <p>This is an answer about layer L1 and about nothing else. Whether the document is
     * conformant is layers L2 and L3, and the verdict over all three is a
     * {@link ValidationResult}, which {@link #validation()} starts. A limit of the
     * specification, section 12.2 ends the parse, so a result carrying
     * {@code ESJ-L1-LIMIT} carries no document and is not well formed here; that is a
     * statement about this reader's configuration and not about the document
     * (section 3.1), and it is why a limit leaves the composed status
     * {@code INDETERMINATE} rather than {@code INVALID}.
     *
     * @return {@code true} if the input satisfies layer L1
     */
    public boolean isWellFormed() {
        return document.isPresent() && findings.stream().noneMatch(Finding::isError);
    }

    /**
     * Returns this read as a validation result covering layer L1 alone (specification,
     * section 9.5), so that it composes with the result of a structural validator through
     * {@link ValidationResult#merge(ValidationResult)} and the three layers add up to one
     * verdict.
     *
     * <p>Layer L1 counts as evaluated, because the reader ran it over the bytes. The model
     * layers are named as not evaluated, with the reason the parse gives them, in the
     * order of precedence the specification, section 9.5 fixes:
     * {@link NotEvaluatedReason#LIMIT} where a limit of section 12.2 ended the parse;
     * {@link NotEvaluatedReason#PRECEDING_LAYER_FAILED} where layer L1 found an error,
     * whether or not a document could still be built from what was left; and
     * {@link NotEvaluatedReason#NOT_REQUESTED} where the read was clean, since the caller
     * may still hand the document to a validator. The result is therefore never
     * {@code VALID} on its own: one layer checked is not three.
     *
     * <p>The middle case is the one that matters for a caller that composes this result
     * with a structural validator's. A member layer L1 rejected is absent from the
     * document the reader hands back, so the model layers would measure a document the
     * sender did not send and could report a term as missing while it stands in the file
     * (section 9.3). Naming them here as not evaluated says that in the result; running
     * them over such a document anyway does not become right by it, which is why the
     * documented composition validates only what the reader found well formed.
     *
     * @return the result of layer L1 over these bytes
     */
    public ValidationResult validation() {
        NotEvaluatedReason reason;
        if (findings.stream()
                .anyMatch(finding -> finding.code() == FindingCode.ESJ_L1_LIMIT)) {
            reason = NotEvaluatedReason.LIMIT;
        } else if (!isWellFormed()) {
            reason = NotEvaluatedReason.PRECEDING_LAYER_FAILED;
        } else {
            reason = NotEvaluatedReason.NOT_REQUESTED;
        }
        Map<ValidationLayer, NotEvaluatedReason> notEvaluated =
                new EnumMap<>(ValidationLayer.class);
        notEvaluated.put(ValidationLayer.L2, reason);
        notEvaluated.put(ValidationLayer.L3, reason);
        return ValidationResult.of(findings, notEvaluated);
    }

    /**
     * Returns the document, or throws if there is none.
     *
     * @return the document
     * @throws java.util.NoSuchElementException if parsing could not be completed
     */
    public SemanticDocument orElseThrow() {
        return document.orElseThrow();
    }
}
