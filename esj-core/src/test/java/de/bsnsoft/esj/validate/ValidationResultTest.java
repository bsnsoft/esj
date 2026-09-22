package de.bsnsoft.esj.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticPath;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Checks the three states of a validation result (specification, sections 9.5 and 9.6). */
class ValidationResultTest {

    private static final Map<ValidationLayer, NotEvaluatedReason> ALL_EVALUATED = Map.of();

    /**
     * Every code of the specification, section 9.6 decides one of two states on its own:
     * the three that record something not evaluated make a result
     * {@code INDETERMINATE}, and every other error makes it {@code INVALID}. The
     * severities alone do not decide it, which is why the test asks the code and not the
     * severity.
     */
    @ParameterizedTest
    @EnumSource(FindingCode.class)
    void everyCodeDecidesTheStateTheSpecificationGivesIt(FindingCode code) {
        ValidationResult result = ValidationResult.of(List.of(finding(code)), ALL_EVALUATED);

        if (code.recordsSomethingNotEvaluated()) {
            assertEquals(ValidationStatus.INDETERMINATE, result.status(), code.code());
        } else {
            assertEquals(Severity.ERROR, code.defaultSeverity(), code.code());
            assertEquals(ValidationStatus.INVALID, result.status(), code.code());
        }
    }

    @Test
    void theThreeCodesThatRecordSomethingNotEvaluatedAreTheOnesTheSpecificationNames() {
        assertEquals(Set.of(FindingCode.ESJ_L1_LIMIT,
                        FindingCode.ESJ_L2_NOT_CHECKED,
                        FindingCode.ESJ_L2_EDITION_UNKNOWN),
                EnumSet.allOf(FindingCode.class).stream()
                        .filter(FindingCode::recordsSomethingNotEvaluated)
                        .collect(Collectors.toSet()));
        assertEquals("ESJ-L2-EDITION-UNKNOWN", FindingCode.ESJ_L2_EDITION_UNKNOWN.code());
        assertEquals(ValidationLayer.L2, FindingCode.ESJ_L2_EDITION_UNKNOWN.layer());
        assertEquals(Severity.INFO, FindingCode.ESJ_L2_EDITION_UNKNOWN.defaultSeverity());
    }

    /**
     * An empty finding list is not conformance (specification, section 9.5): the same
     * empty list is {@code VALID} where all three layers ran and {@code INDETERMINATE}
     * where one did not.
     */
    @Test
    void anEmptyFindingListIsNotConformance() {
        ValidationResult checked = ValidationResult.of(List.of(), ALL_EVALUATED);
        ValidationResult unchecked = ValidationResult.of(List.of(),
                Map.of(ValidationLayer.L2, NotEvaluatedReason.NOT_REQUESTED,
                        ValidationLayer.L3, NotEvaluatedReason.NOT_REQUESTED));

        assertEquals(ValidationStatus.VALID, checked.status());
        assertEquals(ValidationStatus.INDETERMINATE, unchecked.status());
        assertEquals(checked.findings(), unchecked.findings());
    }

    @Test
    void everyLayerIsEitherEvaluatedOrNamedWithAReason() {
        ValidationResult result = ValidationResult.of(List.of(),
                Map.of(ValidationLayer.L3, NotEvaluatedReason.PRECEDING_LAYER_FAILED));

        assertEquals(Set.of(ValidationLayer.L1, ValidationLayer.L2), result.evaluated());
        assertEquals(Map.of(ValidationLayer.L3, NotEvaluatedReason.PRECEDING_LAYER_FAILED),
                result.notEvaluated());
        assertEquals(ValidationStatus.INDETERMINATE, result.status());
    }

    /** A defect found is a defect whatever else was not reached. */
    @Test
    void anErrorOutranksWhatWasNotEvaluated() {
        ValidationResult result = ValidationResult.of(
                List.of(finding(FindingCode.ESJ_L1_LIMIT), finding(FindingCode.ESJ_L2_DATE)),
                Map.of(ValidationLayer.L3, NotEvaluatedReason.LIMIT));

        assertEquals(ValidationStatus.INVALID, result.status());
    }

    /**
     * {@code ESJ-L1-LIMIT} carries the severity {@code error} and still yields
     * {@code INDETERMINATE}: it says that this implementation stopped and not that the
     * document is wrong (specification, sections 3.1 and 9.5).
     */
    @Test
    void aLimitIsNoDefectOfTheDocument() {
        ValidationResult result =
                ValidationResult.of(List.of(finding(FindingCode.ESJ_L1_LIMIT)), ALL_EVALUATED);

        assertTrue(result.findings().get(0).isError());
        assertEquals(ValidationStatus.INDETERMINATE, result.status());
    }

    @Test
    void aWarningNeverChangesTheStatus() {
        Finding warning = new Finding(SemanticPath.of("/BT-1"), "", FindingCode.ESJ_L2_DATE,
                Severity.WARNING, "downgraded");

        assertEquals(ValidationStatus.VALID,
                ValidationResult.of(List.of(warning), ALL_EVALUATED).status());
        assertEquals(ValidationStatus.INDETERMINATE,
                ValidationResult.of(List.of(warning),
                        Map.of(ValidationLayer.L2, NotEvaluatedReason.NOT_REQUESTED)).status(),
                "a downgrade never buys a VALID: the layer the finding belongs to was not"
                        + " evaluated");
    }

    /**
     * The layers of two components add up to one verdict (specification, section 3.5):
     * neither a reader that ran L1 alone nor a validator that ran L2 and L3 may say
     * {@code VALID}, and the composed result does.
     */
    @Test
    void mergeComposesTheLayersOfTwoComponents() {
        ValidationResult layerOne = ValidationResult.of(List.of(),
                Map.of(ValidationLayer.L2, NotEvaluatedReason.NOT_REQUESTED,
                        ValidationLayer.L3, NotEvaluatedReason.NOT_REQUESTED));
        ValidationResult model = ValidationResult.of(List.of(),
                Map.of(ValidationLayer.L1, NotEvaluatedReason.NOT_REQUESTED),
                List.of("EN 16931-1:2017+A1:2019/AC:2020"));

        ValidationResult both = layerOne.merge(model);

        assertEquals(ValidationStatus.INDETERMINATE, layerOne.status());
        assertEquals(ValidationStatus.INDETERMINATE, model.status());
        assertEquals(ValidationStatus.VALID, both.status());
        assertEquals(EnumSet.allOf(ValidationLayer.class), both.evaluated());
        assertEquals(Map.of(), both.notEvaluated());
        assertEquals(List.of("EN 16931-1:2017+A1:2019/AC:2020"), both.registries());
    }

    @Test
    void mergeKeepsTheFindingsTheRegistriesAndTheStrongerReason() {
        ValidationResult left = ValidationResult.of(List.of(finding(FindingCode.ESJ_L1_LIMIT)),
                Map.of(ValidationLayer.L2, NotEvaluatedReason.NOT_REQUESTED,
                        ValidationLayer.L3, NotEvaluatedReason.NOT_REQUESTED),
                List.of("a"));
        ValidationResult right = ValidationResult.of(List.of(finding(FindingCode.ESJ_L2_DATE)),
                Map.of(ValidationLayer.L2, NotEvaluatedReason.EDITION_UNKNOWN,
                        ValidationLayer.L3, NotEvaluatedReason.LIMIT),
                List.of("a", "b"));

        ValidationResult both = left.merge(right);

        assertEquals(List.of(FindingCode.ESJ_L1_LIMIT, FindingCode.ESJ_L2_DATE),
                both.findings().stream().map(Finding::code).toList());
        assertEquals(NotEvaluatedReason.EDITION_UNKNOWN,
                both.notEvaluated().get(ValidationLayer.L2));
        assertEquals(NotEvaluatedReason.LIMIT, both.notEvaluated().get(ValidationLayer.L3));
        assertEquals(List.of("a", "b"), both.registries());
        assertEquals(ValidationStatus.INVALID, both.status());
    }

    /**
     * A run whose layer L1 failed never reached the edition question, so the reason it
     * established is the one it reports (specification, sections 9.3 and 9.5).
     */
    @Test
    void aFailedFirstLayerOutranksAnUnknownEdition() {
        ValidationResult read = ValidationResult.of(List.of(finding(FindingCode.ESJ_L1_PATH_SYNTAX)),
                Map.of(ValidationLayer.L2, NotEvaluatedReason.PRECEDING_LAYER_FAILED,
                        ValidationLayer.L3, NotEvaluatedReason.PRECEDING_LAYER_FAILED));
        ValidationResult model = ValidationResult.of(List.of(),
                Map.of(ValidationLayer.L1, NotEvaluatedReason.NOT_REQUESTED,
                        ValidationLayer.L2, NotEvaluatedReason.EDITION_UNKNOWN,
                        ValidationLayer.L3, NotEvaluatedReason.EDITION_UNKNOWN));

        ValidationResult both = read.merge(model);

        assertEquals(NotEvaluatedReason.PRECEDING_LAYER_FAILED,
                both.notEvaluated().get(ValidationLayer.L2));
        assertEquals(NotEvaluatedReason.PRECEDING_LAYER_FAILED,
                both.notEvaluated().get(ValidationLayer.L3));
    }

    @Test
    void theReasonsAreTheClosedVocabularyOfTheSpecification() {
        assertEquals(List.of("LIMIT", "PRECEDING-LAYER-FAILED", "EDITION-UNKNOWN", "NOT-REQUESTED"),
                EnumSet.allOf(NotEvaluatedReason.class).stream()
                        .map(NotEvaluatedReason::token)
                        .toList());
        assertEquals("EDITION-UNKNOWN", NotEvaluatedReason.EDITION_UNKNOWN.toString());
    }

    @Test
    void aResultIsImmutableAndSaysWhatItIs() {
        ValidationResult result = ValidationResult.of(List.of(finding(FindingCode.ESJ_L2_DATE)),
                Map.of(ValidationLayer.L1, NotEvaluatedReason.NOT_REQUESTED));

        assertThrows(UnsupportedOperationException.class, () -> result.findings().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.evaluated().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.notEvaluated().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.registries().clear());
        assertThrows(NullPointerException.class, () -> ValidationResult.of(null, Map.of()));
        assertTrue(result.toString().startsWith("ValidationResult[INVALID, findings=1"),
                result.toString());
        assertEquals(result, ValidationResult.of(List.of(finding(FindingCode.ESJ_L2_DATE)),
                Map.of(ValidationLayer.L1, NotEvaluatedReason.NOT_REQUESTED)));
        assertEquals(result.hashCode(), ValidationResult.of(
                List.of(finding(FindingCode.ESJ_L2_DATE)),
                Map.of(ValidationLayer.L1, NotEvaluatedReason.NOT_REQUESTED)).hashCode());
        assertNotEquals(result, ValidationResult.of(List.of(finding(FindingCode.ESJ_L2_DATE)),
                ALL_EVALUATED));
        assertNotEquals(result, (Object) "not a result");
    }

    private static Finding finding(FindingCode code) {
        return Finding.of(SemanticPath.of("/BT-1"), code, "the message of " + code.code());
    }
}
