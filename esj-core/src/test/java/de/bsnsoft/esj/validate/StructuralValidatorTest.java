package de.bsnsoft.esj.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.Fixtures;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.model.Registry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Checks the model layer and the cardinality layer against the registry. */
class StructuralValidatorTest {

    private static final Registry CORE = Registry.en16931();
    private static final Registry WITH_EXTENSION = CORE.withExtension(Registry.xrechnungExtension());

    @Test
    void theSmallestConformantDocumentPassesBothLayers() {
        SemanticDocument document = Fixtures.minimalInvoice().build();

        assertEquals(List.of(), validate(document, EnumSet.of(ValidationLayer.L2)));
        assertEquals(List.of(), validate(document, EnumSet.of(ValidationLayer.L2, ValidationLayer.L3)));
    }

    @Test
    void aTermTheRegistryDoesNotContainIsReported() {
        List<Finding> findings = validate(Fixtures.minimalInvoice()
                .put("/BT-9999", SemanticValue.of("invented"))
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(1, findings.size());
        assertEquals(FindingCode.ESJ_L2_UNKNOWN_TERM, findings.get(0).code());
        assertEquals(Severity.ERROR, findings.get(0).severity());
        assertEquals(SemanticPath.of("/BT-9999"), findings.get(0).path());
    }

    /**
     * The content grammars of the specification, section 6.2. Nothing but the registry
     * says that BT-106 carries a decimal, BT-2 a date and BT-125 base64, so nothing but
     * this layer can say that these contents are wrong.
     */
    @ParameterizedTest
    @CsvSource({
            "/BG-22/BT-106, 100.00,     ESJ-L2-DECIMAL",
            "/BG-22/BT-106, 1E2,        ESJ-L2-DECIMAL",
            "/BG-25/0/BT-129, 11111111111111111111111111111111111111111111111111111111111111111,"
                    + " ESJ-L2-DECIMAL",
            "/BT-2,         2026-02-30, ESJ-L2-DATE",
            "/BT-2,         26-01-01,   ESJ-L2-DATE"})
    void aContentOutsideTheGrammarOfItsRegistryDatatypeIsReported(String path,
                                                                  String content,
                                                                  String code) {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put(path, content)
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(1, findings.size(), findings.toString());
        assertEquals(code, findings.get(0).code().code());
        assertEquals(SemanticPath.of(path), findings.get(0).path());
    }

    @Test
    void aBinaryContentThatIsNotCanonicalBase64IsReported() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BG-24/0/BT-125", new SemanticValue("QUJDRR==", null, null,
                        "application/pdf", "a.pdf"))
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(FindingCode.ESJ_L2_BASE64), codes(findings));
        assertFalse(findings.get(0).message().contains("QUJDRR=="));
    }

    @Test
    void theContentOfATermWithNoGrammarIsNotJudged() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BG-4/BT-27", "100.00")
                .put("/BT-3", "2026-02-30")
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(), findings);
    }

    /**
     * The message of a content finding names the term, the semantic data type the registry
     * records for it and the clause of the grammar that was broken, and the content it
     * quotes is escaped (specification, section 9.5).
     */
    @Test
    void theMessageOfAContentFindingNamesTheTermTheTypeAndTheClause() {
        Finding finding = validate(SemanticDocument.builder()
                .put("/BG-22/BT-106", "100.00")
                .build(), EnumSet.of(ValidationLayer.L2)).get(0);

        assertTrue(finding.message().contains("BT-106"), finding.message());
        assertTrue(finding.message().contains("Amount"), finding.message());
        assertTrue(finding.message().contains("trailing zeros"), finding.message());
    }

    /**
     * The rule of the specification, section 6.4: a decimal spelled some other way is
     * reported here and is never made canonical on the way through. The value in the
     * document still reads {@code 100.00} after the validation.
     */
    @Test
    void aDecimalSpelledSomeOtherWayIsReportedAndNotRepaired() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-22/BT-106", "100.00")
                .build();

        assertEquals(List.of(FindingCode.ESJ_L2_DECIMAL),
                codes(validate(document, EnumSet.of(ValidationLayer.L2))));
        assertEquals("100.00",
                document.value(SemanticPath.of("/BG-22/BT-106")).orElseThrow().content());
    }

    @Test
    void anIndexOnATermThatCannotRepeatIsReported() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BT-1/0", SemanticValue.of("RE-1"))
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(FindingCode.ESJ_L2_INDEX_FORBIDDEN), codes(findings));
    }

    @Test
    void aMissingIndexOnARepeatableTermOrGroupIsReported() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BG-25/BT-126", SemanticValue.of("1"))
                .put("/BG-4/BT-29", SemanticValue.of("SELLER-1"))
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(FindingCode.ESJ_L2_INDEX_REQUIRED, FindingCode.ESJ_L2_INDEX_REQUIRED),
                codes(findings));
        assertEquals(SemanticPath.of("/BG-4/BT-29"), findings.get(0).path());
    }

    @Test
    void aGroupChainTheRegistryDoesNotRecordIsReported() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BG-29/BT-146", SemanticValue.ofDecimal(BigDecimal.ONE))
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(FindingCode.ESJ_L2_PARENT_CHAIN), codes(findings));
    }

    @Test
    void aSchemeOnATermWithoutASchemeComponentIsReported() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BT-1", SemanticValue.identifier("RE-1", "0088"))
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(FindingCode.ESJ_L2_COMPONENT_NOT_ALLOWED), codes(findings));
    }

    @Test
    void aSchemeVersionOnATermThatAllowsNoneIsReported() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BG-4/BT-30", SemanticValue.identifier("HRB 1", "0094", "1.0"))
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(FindingCode.ESJ_L2_COMPONENT_NOT_ALLOWED), codes(findings));
        assertTrue(findings.get(0).message().contains("schemeVersion"));
    }

    @Test
    void aMandatoryComponentThatIsAbsentIsReported() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BG-4/BT-34", SemanticValue.of("seller@example.org"))
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(FindingCode.ESJ_L2_COMPONENT_MISSING), codes(findings));
        assertTrue(findings.get(0).message().contains("scheme"));
    }

    @Test
    void theComponentsOfATermThatAllowsThemAreAccepted() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BG-25/0/BG-31/BT-158/0",
                        SemanticValue.identifier("65434568", "0160", "1.0"))
                .put("/BG-4/BT-34", SemanticValue.identifier("seller@example.org", "0088"))
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(), findings);
    }

    @Test
    void anAttachmentCarriesItsTwoMandatoryComponents() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BG-24/0/BT-125",
                        SemanticValue.binary(new byte[] {1, 2}, "application/pdf", "a.pdf"))
                .build(), EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(), findings);
    }

    /**
     * An extension namespace is permanent: once a term identifier has been published
     * within one, its meaning, datatype and structural semantics must not change
     * incompatibly, and an incompatible revision uses a new namespace (specification,
     * section 5.6). <strong>That rule is not tested here, and cannot be yet:</strong> it
     * is a statement about two versions of one registry, and this implementation ships
     * one version of one extension registry. The test it asks for — every identifier a
     * published version carries keeps its datatype and its parent in every later version
     * of the same namespace — arrives with the second version of a shipped registry, or
     * with the first fixture pair written for the purpose.
     */
    @Test
    void aPathThroughAnExtensionIsReportedAsNotCheckedWhenThatRegistryIsNotLoaded() {
        SemanticDocument document = Fixtures.minimalInvoice()
                .put("/BG-25/0/BG-DEX-01/0/BG-DEX-07/BT-146",
                        SemanticValue.ofDecimal(new BigDecimal("50")))
                .put("/BG-DEX-09/0/BT-DEX-001", SemanticValue.of("third party"))
                .build();

        List<Finding> findings = validate(document, EnumSet.of(ValidationLayer.L2));

        assertEquals(List.of(FindingCode.ESJ_L2_NOT_CHECKED, FindingCode.ESJ_L2_NOT_CHECKED),
                codes(findings));
        assertTrue(findings.stream().allMatch(finding -> finding.severity() == Severity.INFO));
        assertTrue(findings.stream().noneMatch(Finding::isError));
    }

    @Test
    void theSamePathIsCheckedWhenTheExtensionRegistryIsLoaded() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-25/0/BG-DEX-01/0/BG-DEX-07/BT-146",
                        SemanticValue.ofDecimal(new BigDecimal("50")))
                .put("/BG-DEX-09/0/BT-DEX-001", SemanticValue.of("third party"))
                .build();

        List<Finding> findings = StructuralValidator.validate(document, WITH_EXTENSION,
                EnumSet.of(ValidationLayer.L2)).findings();

        assertEquals(List.of(), findings);
    }

    /**
     * {@code examples/b2c-gross.esj.json} carries the four terms of the B2C extension beside
     * the net core terms. With the extension registry loaded it passes both layers; without
     * it the four paths are reported as not checked, which is the state the specification,
     * section 5.6 calls for and which makes the result indeterminate rather than valid.
     */
    @Test
    void theB2cExampleIsCheckedWhenItsRegistryIsLoadedAndNotCheckedWithoutIt() {
        SemanticDocument document = EsjReader.strict().read(example("b2c-gross"));
        Set<ValidationLayer> layers = EnumSet.of(ValidationLayer.L2, ValidationLayer.L3);

        assertEquals(List.of(), StructuralValidator.validate(
                document, CORE.withExtension(Registry.b2cExtension()), layers).findings());

        List<Finding> withoutTheRegistry =
                StructuralValidator.validate(document, CORE, layers).findings();

        assertEquals(List.of("/BT-B2C-010", "/BG-25/0/BT-B2C-001", "/BG-25/0/BT-B2C-002",
                        "/BG-25/0/BT-B2C-003", "/BG-25/1/BT-B2C-001", "/BG-25/1/BT-B2C-002",
                        "/BG-25/1/BT-B2C-003", "/BG-25/2/BT-B2C-001", "/BG-25/2/BT-B2C-002",
                        "/BG-25/2/BT-B2C-003"),
                withoutTheRegistry.stream().map(finding -> finding.path().toString()).toList());
        assertEquals(Set.of(FindingCode.ESJ_L2_NOT_CHECKED),
                Set.copyOf(codes(withoutTheRegistry)));
    }

    @Test
    void anExtensionGroupWithoutAnIndexIsReportedWhenItsRegistryIsLoaded() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-25/0/BG-DEX-01/BG-DEX-07/BT-146",
                        SemanticValue.ofDecimal(new BigDecimal("50")))
                .build();

        List<Finding> findings = StructuralValidator.validate(document, WITH_EXTENSION,
                EnumSet.of(ValidationLayer.L2)).findings();

        assertEquals(List.of(FindingCode.ESJ_L2_INDEX_REQUIRED), codes(findings));
    }

    @Test
    void theModelLayerDoesNotReportTheCardinalityLayer() {
        SemanticDocument empty = SemanticDocument.builder().build();

        assertEquals(List.of(), validate(empty, EnumSet.of(ValidationLayer.L2)));
        assertFalse(validate(empty, EnumSet.of(ValidationLayer.L2, ValidationLayer.L3)).isEmpty());
    }

    @Test
    void theCardinalityLayerCanBeAskedForAlone() {
        SemanticDocument document = Fixtures.minimalInvoice()
                .put("/BT-999", SemanticValue.of("a"))
                .remove("/BG-25/0/BT-130")
                .build();

        List<FindingCode> both = codes(validate(document,
                EnumSet.of(ValidationLayer.L2, ValidationLayer.L3)));
        List<FindingCode> model = codes(validate(document, EnumSet.of(ValidationLayer.L2)));
        List<FindingCode> cardinality = codes(validate(document, EnumSet.of(ValidationLayer.L3)));

        assertEquals(List.of(FindingCode.ESJ_L2_UNKNOWN_TERM), model);
        assertEquals(List.of(FindingCode.ESJ_L3_MISSING_TERM), cardinality);
        assertEquals(both,
                List.of(FindingCode.ESJ_L2_UNKNOWN_TERM, FindingCode.ESJ_L3_MISSING_TERM));
        assertTrue(cardinality.stream()
                .allMatch(code -> code.layer() == ValidationLayer.L3));
    }

    @Test
    void bothLayersAreCheckedWhenNoneIsNamed() {
        SemanticDocument document = Fixtures.minimalInvoice().build();

        assertEquals(StructuralValidator.validate(document, CORE).findings(),
                validate(document, EnumSet.of(ValidationLayer.L2, ValidationLayer.L3)));
    }

    @Test
    void aValidationAsksForAtLeastOneLayerAndNeverForTheReadersLayer() {
        SemanticDocument document = Fixtures.minimalInvoice().build();

        assertThrows(IllegalArgumentException.class,
                () -> validate(document, EnumSet.noneOf(ValidationLayer.class)));
        assertThrows(IllegalArgumentException.class,
                () -> validate(document, EnumSet.of(ValidationLayer.L1)));
        assertThrows(IllegalArgumentException.class,
                () -> validate(document, EnumSet.allOf(ValidationLayer.class)));
    }

    @Test
    void theMandatoryTermsOfTheRootAreCheckedAtTheRoot() {
        List<Finding> findings = validate(SemanticDocument.builder().build(),
                EnumSet.of(ValidationLayer.L2, ValidationLayer.L3));

        assertTrue(findings.stream().allMatch(finding -> finding.path().isRoot()));
        assertTrue(missing(findings, FindingCode.ESJ_L3_MISSING_TERM).contains("BT-1"));
        assertTrue(missing(findings, FindingCode.ESJ_L3_MISSING_TERM).contains("BT-5"));
        assertTrue(missing(findings, FindingCode.ESJ_L3_MISSING_GROUP).contains("BG-25"));
        assertTrue(missing(findings, FindingCode.ESJ_L3_MISSING_GROUP).contains("BG-4"));
    }

    @Test
    void theMandatoryTermsAreCheckedInEveryGroupInstance() {
        SemanticDocument document = Fixtures.minimalInvoice()
                .put("/BG-25/1/BT-126", SemanticValue.of("2"))
                .build();

        List<Finding> findings = validate(document, EnumSet.of(ValidationLayer.L2, ValidationLayer.L3));

        assertTrue(findings.stream()
                .allMatch(finding -> finding.path().equals(SemanticPath.group("/BG-25/1"))));
        assertTrue(missing(findings, FindingCode.ESJ_L3_MISSING_TERM).contains("BT-129"));
        assertTrue(missing(findings, FindingCode.ESJ_L3_MISSING_GROUP).contains("BG-29"));
        assertFalse(missing(findings, FindingCode.ESJ_L3_MISSING_TERM).contains("BT-126"));
    }

    @Test
    void aSecondCompleteLinePassesTheCardinalityLayer() {
        SemanticDocument document = Fixtures.addLine(Fixtures.minimalInvoice(), 1).build();

        assertEquals(List.of(), validate(document, EnumSet.of(ValidationLayer.L2, ValidationLayer.L3)));
    }

    @Test
    void aGapInTheOccurrenceIndicesIsReported() {
        SemanticDocument document = Fixtures.addLine(Fixtures.minimalInvoice(), 2).build();

        List<Finding> findings = validate(document, EnumSet.of(ValidationLayer.L2, ValidationLayer.L3));

        assertTrue(codes(findings).contains(FindingCode.ESJ_L3_INDEX_GAP));
        Finding gap = findings.stream()
                .filter(finding -> finding.code() == FindingCode.ESJ_L3_INDEX_GAP)
                .findFirst()
                .orElseThrow();
        assertTrue(gap.path().isRoot());
        assertTrue(gap.message().contains("BG-25"));
    }

    @Test
    void aTermOfTheDeepestGroupIsCheckedInItsOwnInstance() {
        SemanticDocument document = Fixtures.minimalInvoice()
                .put("/BG-25/0/BG-31/BG-32/0/BT-160", SemanticValue.of("Colour"))
                .build();

        List<Finding> findings = validate(document, EnumSet.of(ValidationLayer.L2, ValidationLayer.L3));

        assertEquals(List.of(FindingCode.ESJ_L3_MISSING_TERM), codes(findings));
        assertEquals(SemanticPath.group("/BG-25/0/BG-31/BG-32/0"), findings.get(0).path());
        assertTrue(findings.get(0).message().contains("BT-161"));
    }

    @Test
    void aPathTheModelLayerCouldNotPlaceTakesNoPartInTheCardinalityLayer() {
        SemanticDocument document = Fixtures.minimalInvoice()
                .remove("/BG-25/0/BT-126")
                .put("/BG-25/BT-126", SemanticValue.of("1"))
                .build();

        List<Finding> findings = validate(document, EnumSet.of(ValidationLayer.L2, ValidationLayer.L3));

        assertTrue(codes(findings).contains(FindingCode.ESJ_L2_INDEX_REQUIRED));
        assertTrue(codes(findings).contains(FindingCode.ESJ_L3_MISSING_TERM));
        assertTrue(findings.stream()
                .filter(finding -> finding.code() == FindingCode.ESJ_L3_MISSING_TERM)
                .allMatch(finding -> finding.path().equals(SemanticPath.group("/BG-25/0"))));
    }

    /**
     * The registry is chosen by the edition the document names and by nothing else
     * (specification, sections 4.4 and 10): a path is an address relative to an edition.
     */
    @Test
    void aDocumentIsValidatedAgainstTheRegistryOfTheEditionItNames() {
        assertEquals(List.of(), StructuralValidator.validate(Fixtures.minimalInvoice().build(),
                List.of(CORE, WITH_EXTENSION), EnumSet.of(ValidationLayer.L2)).findings());
        assertEquals(List.of(CORE.edition()),
                StructuralValidator.validate(Fixtures.minimalInvoice().build(), CORE).registries());
    }

    /**
     * An edition no registry describes is not a defect of the document: the validator
     * checks no path, says so once at document level and returns {@code INDETERMINATE}
     * (specification, sections 4.4, 9.2 and 9.5). Which editions this implementation
     * holds a registry for is a property of the implementation.
     */
    @Test
    void anEditionNoRegistryDescribesLeavesTheModelLayersUnevaluated() {
        SemanticDocument later = Fixtures.minimalInvoice()
                .semanticModel("EN16931-1:2026")
                .build();

        for (ValidationResult result : List.of(
                StructuralValidator.validate(later, CORE),
                StructuralValidator.validate(later, List.of(CORE, WITH_EXTENSION),
                        EnumSet.of(ValidationLayer.L2, ValidationLayer.L3)),
                StructuralValidator.validate(later, List.of(), EnumSet.of(ValidationLayer.L2,
                        ValidationLayer.L3)))) {
            assertEquals(ValidationStatus.INDETERMINATE, result.status(), result.toString());
            assertEquals(List.of(FindingCode.ESJ_L2_EDITION_UNKNOWN), codes(result.findings()));
            assertEquals(SemanticPath.root(), result.findings().get(0).path());
            assertEquals(Severity.INFO, result.findings().get(0).severity());
            assertTrue(result.findings().get(0).message().contains("EN16931-1:2026"),
                    result.findings().toString());
            assertEquals(NotEvaluatedReason.EDITION_UNKNOWN,
                    result.notEvaluated().get(ValidationLayer.L2));
            assertEquals(NotEvaluatedReason.EDITION_UNKNOWN,
                    result.notEvaluated().get(ValidationLayer.L3));
            assertEquals(Set.of(), result.evaluated());
            assertEquals(List.of(), result.registries());
        }
    }

    /**
     * The validator implements two of the three layers, so it names L1 as not evaluated
     * and its result is never {@code VALID} on its own (specification, section 3.5).
     */
    @Test
    void theValidatorNamesTheLayersItDidNotEvaluate() {
        ValidationResult result = StructuralValidator.validate(Fixtures.minimalInvoice().build(),
                CORE, EnumSet.of(ValidationLayer.L2));

        assertEquals(ValidationStatus.INDETERMINATE, result.status());
        assertEquals(List.of(), result.findings());
        assertEquals(Set.of(ValidationLayer.L2), result.evaluated());
        assertEquals(NotEvaluatedReason.NOT_REQUESTED, result.notEvaluated().get(ValidationLayer.L1));
        assertEquals(NotEvaluatedReason.NOT_REQUESTED, result.notEvaluated().get(ValidationLayer.L3));
    }

    @Test
    void theRegistryKnowsTheEditionInBothSpellings() {
        assertEquals("EN 16931-1:2017+A1:2019/AC:2020", CORE.edition());
        assertEquals("EN16931-1:2017+A1:2019/AC:2020", CORE.semanticModel());
        assertTrue(CORE.describes("EN16931-1:2017+A1:2019/AC:2020"));
        assertFalse(CORE.describes("EN16931-1:2026"));
        assertEquals(CORE.semanticModel(), WITH_EXTENSION.semanticModel());
    }

    @Test
    void aFindingCarriesTheCodeAndTheSeverityTheSpecificationFixes() {
        assertEquals("ESJ-L3-MISSING-TERM", FindingCode.ESJ_L3_MISSING_TERM.code());
        assertEquals(ValidationLayer.L3, FindingCode.ESJ_L3_MISSING_TERM.layer());
        assertEquals(Severity.ERROR, FindingCode.ESJ_L3_MISSING_TERM.defaultSeverity());
        assertEquals(ValidationLayer.L2, FindingCode.ESJ_L2_NOT_CHECKED.layer());
        assertEquals(Severity.INFO, FindingCode.ESJ_L2_NOT_CHECKED.defaultSeverity());
        assertEquals("info", Severity.INFO.token());

        Finding finding = Finding.of(SemanticPath.of("/BT-1"), FindingCode.ESJ_L2_DECIMAL, "text");
        assertEquals(Severity.ERROR, finding.severity());
        assertTrue(finding.toString().startsWith("ESJ-L2-DECIMAL [error] /BT-1: "));
        assertEquals(SemanticPath.root(), Finding.ofDocument(FindingCode.ESJ_L1_JSON, "x").path());
    }

    @Test
    void anExtensionGroupInstanceIsCheckedAgainstTheTermsItReuses() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-25/0/BG-DEX-01/0/BT-126", SemanticValue.of("1.1"))
                .build();

        List<Finding> findings = StructuralValidator.validate(document, WITH_EXTENSION,
                EnumSet.of(ValidationLayer.L2, ValidationLayer.L3)).findings();

        List<String> missingInSubLine = findings.stream()
                .filter(finding -> finding.path().equals(SemanticPath.group("/BG-25/0/BG-DEX-01/0")))
                .map(Finding::message)
                .toList();
        assertTrue(missingInSubLine.stream().anyMatch(message -> message.contains("BT-129")));
        assertTrue(missingInSubLine.stream().anyMatch(message -> message.contains("BG-DEX-06")));
    }

    /**
     * The maximum cardinality of every term of the shipped registries is 1 or n, so only
     * a registry written for the purpose reaches this branch. The specification, section
     * 9.3 defines the check over every registry and not only over the ones ESJ ships.
     */
    @Test
    void aTermBeyondAFiniteMaximumAboveOneIsReported() {
        Registry small = load("""
                {
                  "model": "Small",
                  "edition": "Small:2026",
                  "terms": [
                    {
                      "id": "BT-1", "kind": "BT", "name": "Number", "slug": "number",
                      "parent": null, "path": ["BT-1"], "depth": 0, "min": 0, "max": 2,
                      "datatype": "Text", "order": 1,
                      "components": [],
                      "description": "A term that occurs at most twice.", "notes": []
                    }
                  ]
                }
                """);
        SemanticDocument document = SemanticDocument.builder()
                .semanticModel("Small:2026")
                .put("/BT-1/0", SemanticValue.of("a"))
                .put("/BT-1/1", SemanticValue.of("b"))
                .put("/BT-1/2", SemanticValue.of("c"))
                .build();

        List<Finding> findings = StructuralValidator.validate(document, small,
                EnumSet.of(ValidationLayer.L2, ValidationLayer.L3)).findings();

        assertEquals(List.of(FindingCode.ESJ_L3_MAX_CARDINALITY), codes(findings));
        assertTrue(findings.get(0).message().contains("occurs 3 times"), findings.toString());
    }

    /**
     * The specification, section 9.3 counts the paths that passed L2 and no others, so a
     * term whose paths were refused there has no occurrence L3 can see and draws the
     * missing-term finding rather than one about a maximum.
     */
    @Test
    void aPathThatFailedTheModelLayerIsNoOccurrenceAtTheCardinalityLayer() {
        List<Finding> findings = validate(SemanticDocument.builder()
                .put("/BT-1/0", SemanticValue.of("RE-1"))
                .put("/BT-1/1", SemanticValue.of("RE-2"))
                .build(), EnumSet.of(ValidationLayer.L2, ValidationLayer.L3));

        assertEquals(2, findings.stream()
                .filter(finding -> finding.code() == FindingCode.ESJ_L2_INDEX_FORBIDDEN)
                .count(), findings.toString());
        assertTrue(missing(findings, FindingCode.ESJ_L3_MISSING_TERM).contains("BT-1"),
                findings.toString());
        assertFalse(codes(findings).contains(FindingCode.ESJ_L3_MAX_CARDINALITY));
    }

    private static Registry load(String json) {
        try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return Registry.load(in);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * The semantic data type Time exists in the 2026 edition alone, so the check of its
     * grammar is measured against that edition's registry. The test is skipped where the
     * build carries none: the files of that edition are separable (the Maven profile
     * {@code without-edition-2026}).
     */
    @ParameterizedTest
    @EnabledIf("carriesEdition2026")
    @CsvSource({
            "09:30:00+02:00, true",
            "06:15:02Z,      true",
            "09:30:00,       false",
            "09:30:00+00:00, false",
            "24:00:00Z,      false"})
    void theContentOfATimeTermIsCheckedAgainstTheTimeGrammar(String content, boolean accepted) {
        SemanticDocument document = SemanticDocument.builder()
                .semanticModel("EN16931-1:2026")
                .put("/BT-166", SemanticValue.of(content))
                .build();

        List<Finding> findings = StructuralValidator
                .validate(document, Registry.forEdition("2026"), EnumSet.of(ValidationLayer.L2))
                .findings();

        assertEquals(accepted ? List.of() : List.of(FindingCode.ESJ_L2_TIME), codes(findings),
                content);
    }

    static boolean carriesEdition2026() {
        return Registry.editions().contains("2026");
    }

    private static byte[] example(String name) {
        String resource = "/examples/" + name + ".esj.json";
        try (InputStream in = StructuralValidatorTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the test classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Finding> validate(SemanticDocument document, Set<ValidationLayer> layers) {
        return StructuralValidator.validate(document, CORE, layers).findings();
    }

    private static List<FindingCode> codes(List<Finding> findings) {
        return findings.stream().map(Finding::code).toList();
    }

    private static List<String> missing(List<Finding> findings, FindingCode code) {
        return findings.stream()
                .filter(finding -> finding.code() == code)
                .map(Finding::message)
                .map(message -> message.substring(0, message.indexOf(' ')))
                .toList();
    }
}
