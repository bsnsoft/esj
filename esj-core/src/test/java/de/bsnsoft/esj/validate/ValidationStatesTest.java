package de.bsnsoft.esj.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.Fixtures;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.json.EsjWriter;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.json.ReadResult;
import de.bsnsoft.esj.model.Registry;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The three states of the specification, section 9.5 from bytes to verdict: a reader runs
 * layer L1, the structural validator runs L2 and L3, and the two results compose into the
 * one answer a caller reads. Neither component alone may say {@code VALID}.
 */
class ValidationStatesTest {

    private static final Registry CORE = Registry.en16931();
    private static final Set<ValidationLayer> MODEL =
            EnumSet.of(ValidationLayer.L2, ValidationLayer.L3);

    private final EsjReader reader = EsjReader.strict();

    @Test
    void aDocumentThatSatisfiesAllThreeLayersIsValid() {
        ValidationResult result = check(bytes(Fixtures.minimalInvoice().build()));

        assertEquals(ValidationStatus.VALID, result.status(), result.findings().toString());
        assertEquals(List.of(), result.findings());
        assertEquals(EnumSet.allOf(ValidationLayer.class), result.evaluated());
        assertEquals(Map.of(), result.notEvaluated());
        assertEquals(List.of(CORE.edition()), result.registries());
    }

    @Test
    void aDocumentThatFailsALayerIsInvalid() {
        ValidationResult result = check(bytes(Fixtures.minimalInvoice()
                .remove("/BG-25/0/BT-129")
                .build()));

        assertEquals(ValidationStatus.INVALID, result.status());
        assertEquals(List.of(FindingCode.ESJ_L3_MISSING_TERM),
                result.findings().stream().map(Finding::code).toList());
        assertEquals(EnumSet.allOf(ValidationLayer.class), result.evaluated());
    }

    /**
     * A limit is the reading party's policy and says nothing about the document
     * (specification, sections 3.1 and 12.2), so the run has no verdict: the reader stops,
     * the model layers never see a document, and the status is {@code INDETERMINATE} and
     * not {@code INVALID}.
     */
    @Test
    void aDocumentOverALimitLeavesTheRunWithoutAVerdict() {
        EsjReader narrow = EsjReader.withLimits(Limits.defaults().toBuilder()
                .maxValues(3)
                .build());

        ReadResult read = narrow.readWithFindings(bytes(Fixtures.minimalInvoice().build()));
        ValidationResult result = read.validation();

        assertFalse(read.document().isPresent());
        assertFalse(read.isWellFormed());
        assertEquals(List.of(FindingCode.ESJ_L1_LIMIT),
                result.findings().stream().map(Finding::code).toList());
        assertTrue(result.findings().get(0).isError());
        assertEquals(ValidationStatus.INDETERMINATE, result.status());
        assertEquals(Set.of(ValidationLayer.L1), result.evaluated());
        assertEquals(NotEvaluatedReason.LIMIT, result.notEvaluated().get(ValidationLayer.L2));
        assertEquals(NotEvaluatedReason.LIMIT, result.notEvaluated().get(ValidationLayer.L3));
    }

    /**
     * An extension term whose registry is not loaded leaves one path unmeasured. All three
     * layers ran, no error was found, and the result is still not {@code VALID}: what was
     * not evaluated is part of the answer (specification, sections 5.6 and 9.5).
     */
    @Test
    void anExtensionNamespaceWithoutARegistryLeavesTheResultIndeterminate() {
        byte[] document = bytes(Fixtures.minimalInvoice()
                .put("/BG-DEX-09/0/BT-DEX-001", "third party")
                .put("/BG-DEX-09/0/BT-DEX-002", "25.4")
                .put("/BG-DEX-09/0/BT-DEX-003", "handling fee paid by a third party")
                .build());

        ValidationResult result = check(document);

        assertEquals(List.of(FindingCode.ESJ_L2_NOT_CHECKED,
                        FindingCode.ESJ_L2_NOT_CHECKED,
                        FindingCode.ESJ_L2_NOT_CHECKED),
                result.findings().stream().map(Finding::code).toList());
        assertEquals(Severity.INFO, result.findings().get(0).severity());
        assertEquals(EnumSet.allOf(ValidationLayer.class), result.evaluated());
        assertEquals(ValidationStatus.INDETERMINATE, result.status());
        assertEquals(ValidationStatus.VALID,
                check(document, List.of(CORE.withExtension(Registry.xrechnungExtension())))
                        .status(),
                "the same document is conformant once the registry that describes the"
                        + " namespace is loaded");
    }

    /**
     * A reader written today reads a document of an edition published after it and says
     * what it can: layer L1 passed, the model layers were not checked, and no party may
     * call the document more or less than well formed (specification, sections 3.1, 4.4
     * and 9.2).
     */
    @Test
    void aDocumentOfAnUnknownEditionIsWellFormedAndNotJudgedFurther() {
        byte[] document = bytes(Fixtures.minimalInvoice()
                .semanticModel("EN16931-1:2026")
                .build());

        ReadResult read = reader.readWithFindings(document);
        ValidationResult result = check(document);

        assertTrue(read.isWellFormed());
        assertEquals(Set.of(ValidationLayer.L1), result.evaluated());
        assertEquals(List.of(FindingCode.ESJ_L2_EDITION_UNKNOWN),
                result.findings().stream().map(Finding::code).toList());
        assertEquals(NotEvaluatedReason.EDITION_UNKNOWN,
                result.notEvaluated().get(ValidationLayer.L2));
        assertEquals(NotEvaluatedReason.EDITION_UNKNOWN,
                result.notEvaluated().get(ValidationLayer.L3));
        assertEquals(ValidationStatus.INDETERMINATE, result.status());
        assertEquals(List.of(), result.registries());
    }

    /** Runs all three layers over the bytes with the core registry. */
    private ValidationResult check(byte[] document) {
        return check(document, List.of(CORE));
    }

    private ValidationResult check(byte[] document, List<Registry> registries) {
        ReadResult read = reader.readWithFindings(document);
        ValidationResult layerOne = read.validation();
        return read.document()
                .map(parsed -> layerOne.merge(
                        StructuralValidator.validate(parsed, registries, MODEL)))
                .orElse(layerOne);
    }

    private static byte[] bytes(SemanticDocument document) {
        return EsjWriter.canonical().toBytes(document);
    }
}
