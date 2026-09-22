package de.bsnsoft.esj.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.TermKind;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Checks the registry: what it reads, what it answers and how it combines with an extension. */
class RegistryTest {

    @Test
    void theCoreRegistryIsReadOnceFromTheClasspath() {
        Registry registry = Registry.en16931();

        assertSame(registry, Registry.en16931());
        assertEquals("EN16931-1", registry.model());
        assertEquals("EN 16931-1:2017+A1:2019/AC:2020", registry.edition());
        assertEquals(Optional.of("0.1"), registry.version());
        assertFalse(registry.terms().isEmpty());
    }

    @Test
    void aRegistryFileWithoutAVersionIsStillARegistry() {
        Registry registry = load("""
                {
                  "model": "Small",
                  "edition": "Small 1.0",
                  "terms": []
                }
                """);

        assertEquals(Optional.empty(), registry.version());
    }

    @Test
    void theTermsComeInTheOrderOfTheTableOfTheModel() {
        List<Term> terms = Registry.en16931().terms();

        assertEquals("BT-1", terms.get(0).id());
        for (int i = 1; i < terms.size(); i++) {
            assertTrue(terms.get(i - 1).order() < terms.get(i).order(),
                    "the order of " + terms.get(i).id() + " follows " + terms.get(i - 1).id());
        }
    }

    @Test
    void aTermCarriesItsStructuralFacts() {
        Term invoiceNumber = Registry.en16931().term("BT-1").orElseThrow();

        assertEquals(TermKind.BT, invoiceNumber.kind());
        assertEquals(Optional.empty(), invoiceNumber.parent());
        assertEquals(List.of("BT-1"), invoiceNumber.path());
        assertEquals(Cardinality.of(1, 1), invoiceNumber.cardinality());
        assertEquals(Optional.of(SemanticType.IDENTIFIER), invoiceNumber.datatype());
        assertTrue(invoiceNumber.isMandatory());
        assertFalse(invoiceNumber.isRepeatable());
        assertFalse(invoiceNumber.isGroup());
    }

    @Test
    void aGroupCarriesNoSemanticDataType() {
        Term line = Registry.en16931().term("BG-25").orElseThrow();

        assertTrue(line.isGroup());
        assertEquals(Optional.empty(), line.datatype());
        assertEquals(Cardinality.unbounded(1), line.cardinality());
        assertTrue(line.cardinality().isUnbounded());
        assertEquals("1..n", line.cardinality().toString());
    }

    @Test
    void theRegistryAnswersTheQuestionsTheValidatorAsks() {
        Registry registry = Registry.en16931();

        assertEquals(Optional.of("BG-5"), registry.parentOf("BT-40"));
        assertEquals(Optional.empty(), registry.parentOf("BT-1"));
        assertTrue(registry.isRepeatable("BG-25"));
        assertTrue(registry.isRepeatable("BT-29"));
        assertTrue(registry.isRepeatable("BT-158"));
        assertFalse(registry.isRepeatable("BT-1"));
        assertEquals(Optional.of(SemanticType.UNIT_PRICE_AMOUNT), registry.datatype("BT-146"));
        assertEquals(Optional.empty(), registry.datatype("BG-29"));
        assertEquals(Optional.empty(), registry.term("BT-9999"));
        assertThrows(IllegalArgumentException.class, () -> registry.isRepeatable("BT-9999"));
    }

    @Test
    void theSupplementaryComponentsAreTheOnesTheModelDeclares() {
        Registry registry = Registry.en16931();

        assertEquals(List.of(), registry.components("BT-1"));

        List<Component> classification = registry.components("BT-158");
        assertEquals(2, classification.size());
        assertEquals(Component.Role.SCHEME, classification.get(0).role());
        assertTrue(classification.get(0).isMandatory());
        assertEquals(Component.Role.SCHEME_VERSION, classification.get(1).role());
        assertFalse(classification.get(1).isMandatory());

        Term attachment = registry.term("BT-125").orElseThrow();
        assertEquals(Optional.of(SemanticType.BINARY_OBJECT), attachment.datatype());
        assertTrue(attachment.component(Component.Role.MIME_CODE).orElseThrow().isMandatory());
        assertTrue(attachment.component(Component.Role.FILENAME).orElseThrow().isMandatory());
        assertEquals(Optional.empty(), attachment.component(Component.Role.SCHEME));
    }

    @Test
    void everySchemeComponentNamesTheCodeListItsCodeComesFrom() {
        Registry registry = Registry.en16931();

        assertEquals(Optional.of("ISO 6523 ICD"), schemeList(registry, "BT-29"));
        assertEquals(Optional.of("CEF EAS"), schemeList(registry, "BT-34"));
        assertEquals(Optional.of("UNTDID 1153"), schemeList(registry, "BT-18"));
        assertEquals(Optional.of("UNTDID 7143"), schemeList(registry, "BT-158"));
        assertEquals(Optional.empty(),
                registry.term("BT-158").orElseThrow()
                        .component(Component.Role.SCHEME_VERSION).orElseThrow().schemeList());

        int named = 0;
        for (Term term : registry.terms()) {
            for (Component component : term.components()) {
                if (component.role() == Component.Role.SCHEME) {
                    assertTrue(component.schemeList().isPresent(),
                            "the scheme of " + term.id() + " names its code list");
                    assertFalse(term.notes().isEmpty(), "the note of " + term.id() + " justifies it");
                    named++;
                } else {
                    assertEquals(Optional.empty(), component.schemeList(), term.id());
                }
            }
        }
        assertEquals(13, named, "the identifier terms whose scheme list the model fixes");
    }

    @Test
    void onlyAnIdentificationSchemeIsTakenFromACodeList() {
        assertThrows(EsjFormatException.class, () -> load("""
                {
                  "model": "Small",
                  "edition": "Small 1.0",
                  "terms": [
                    {
                      "id": "BT-1", "kind": "BT", "name": "Number", "slug": "number",
                      "parent": null, "path": ["BT-1"], "depth": 0, "min": 1, "max": 1,
                      "datatype": "Identifier", "order": 1,
                      "components": [
                        {"id": "BT-1-2", "role": "schemeVersion", "name": "n",
                         "schemeList": "ISO 6523 ICD", "min": 0, "max": 1}
                      ],
                      "description": "A term.", "notes": []
                    }
                  ]
                }
                """));
    }

    @Test
    void aComponentThatTheSemanticDataTypeDoesNotHaveIsRefused() {
        assertThrows(EsjFormatException.class, () -> load(oneTerm("Identifier",
                """
                {"id": "BT-1-1", "role": "mimeCode", "name": "m", "min": 1, "max": 1}""")));
        assertThrows(EsjFormatException.class, () -> load(oneTerm("Text",
                """
                {"id": "BT-1-1", "role": "scheme", "name": "s", "min": 0, "max": 1}""")));
    }

    @Test
    void aBinaryObjectCarriesBothOfItsComponentsAndBothAreMandatory() {
        assertThrows(EsjFormatException.class, () -> load(oneTerm("BinaryObject",
                """
                {"id": "BT-1-1", "role": "mimeCode", "name": "m", "min": 1, "max": 1}""")));
        assertThrows(EsjFormatException.class, () -> load(oneTerm("BinaryObject",
                """
                {"id": "BT-1-1", "role": "mimeCode", "name": "m", "min": 0, "max": 1},
                        {"id": "BT-1-2", "role": "filename", "name": "f", "min": 1, "max": 1}""")));
    }

    @Test
    void aBinaryObjectWithoutAnyComponentIsRefused() {
        assertThrows(EsjFormatException.class, () -> load(oneTerm("BinaryObject", "")));
    }

    @Test
    void aMandatorySchemeVersionBesideAnOptionalSchemeIsRefused() {
        assertThrows(EsjFormatException.class, () -> load(oneTerm("Identifier",
                """
                {"id": "BT-1-1", "role": "scheme", "name": "s", "min": 0, "max": 1},
                        {"id": "BT-1-2", "role": "schemeVersion", "name": "v", "min": 1, "max": 1}""")));
    }

    @Test
    void aSchemeVersionWithoutASchemeIsRefused() {
        assertThrows(EsjFormatException.class, () -> load(oneTerm("Identifier",
                """
                {"id": "BT-1-1", "role": "schemeVersion", "name": "v", "min": 0, "max": 1}""")));
    }

    @Test
    void theSameComponentRoleTwiceIsRefused() {
        assertThrows(EsjFormatException.class, () -> load(oneTerm("Identifier",
                """
                {"id": "BT-1-1", "role": "scheme", "name": "s", "min": 0, "max": 1},
                        {"id": "BT-1-2", "role": "scheme", "name": "t", "min": 0, "max": 1}""")));
    }

    /** Returns a one-term registry whose only term has the given datatype and components. */
    private static String oneTerm(String datatype, String components) {
        return """
                {
                  "model": "Small",
                  "edition": "Small 1.0",
                  "terms": [
                    {
                      "id": "BT-1", "kind": "BT", "name": "Number", "slug": "number",
                      "parent": null, "path": ["BT-1"], "depth": 0, "min": 1, "max": 1,
                      "datatype": "%s", "order": 1,
                      "components": [%s],
                      "description": "A term.", "notes": []
                    }
                  ]
                }
                """.formatted(datatype, components);
    }

    private static Optional<String> schemeList(Registry registry, String id) {
        return registry.term(id).orElseThrow().component(Component.Role.SCHEME)
                .orElseThrow().schemeList();
    }

    @Test
    void theChildrenOfAGroupAndOfTheRootAreKnown() {
        Registry registry = Registry.en16931();

        List<String> rootIds = registry.rootTerms().stream().map(Term::id).toList();
        assertTrue(rootIds.contains("BT-1"));
        assertTrue(rootIds.contains("BG-25"));
        assertFalse(rootIds.contains("BT-27"));
        assertEquals(rootIds, registry.children(null).stream().map(Term::id).toList());

        List<String> addressIds = registry.children("BG-5").stream().map(Term::id).toList();
        assertTrue(addressIds.contains("BT-40"));
        assertFalse(addressIds.contains("BT-27"));
    }

    @Test
    void aCoreTermHasOneParentChain() {
        assertEquals(List.of(List.of("BG-25", "BG-29", "BT-146")), Registry.en16931().chains("BT-146"));
        assertEquals(List.of(List.of("BT-1")), Registry.en16931().chains("BT-1"));
        assertEquals(List.of(), Registry.en16931().chains("BT-9999"));
    }

    @Test
    void anExtensionRegistryIsReadTheSameWay() {
        Registry extension = Registry.xrechnungExtension();

        assertSame(extension, Registry.xrechnungExtension());
        assertEquals("XRechnung 3.0.2", extension.edition());
        assertEquals(Optional.of(SemanticType.TEXT), extension.datatype("BT-DEX-001"));
        assertEquals(Optional.of("BG-25"), extension.parentOf("BG-DEX-01"));
    }

    @Test
    void anExtensionAddsASecondPositionForTheCoreTermsItReuses() {
        Registry combined = Registry.en16931().withExtension(Registry.xrechnungExtension());

        assertEquals("EN 16931-1:2017+A1:2019/AC:2020", combined.edition());
        assertEquals(List.of(
                        List.of("BG-25", "BG-29", "BT-146"),
                        List.of("BG-25", "BG-DEX-01", "BG-DEX-07", "BT-146")),
                combined.chains("BT-146"));
        assertEquals(Optional.of(SemanticType.UNIT_PRICE_AMOUNT), combined.datatype("BT-146"));

        List<String> subLineChildren = combined.children("BG-DEX-01").stream().map(Term::id).toList();
        assertTrue(subLineChildren.contains("BG-DEX-02"));
        assertTrue(subLineChildren.contains("BT-126"));
        assertTrue(subLineChildren.contains("BT-133"));
    }

    static List<Registry> shippedExtensions() {
        return List.of(Registry.xrechnungExtension(), Registry.b2cExtension());
    }

    @ParameterizedTest
    @MethodSource("shippedExtensions")
    void aShippedExtensionImportsTheEditionOfTheCoreRegistry(Registry extension) {
        Registry core = Registry.en16931();
        List<Registry.Import> imports = extension.imports();

        assertEquals(1, imports.size());
        assertEquals(core.model(), imports.get(0).model());
        assertEquals(core.edition(), imports.get(0).edition());
    }

    @ParameterizedTest
    @MethodSource("shippedExtensions")
    void aShippedExtensionCombinesWithTheCoreRegistry(Registry extension) {
        Registry combined = Registry.en16931().withExtension(extension);

        assertEquals("EN 16931-1:2017+A1:2019/AC:2020", combined.edition());
        for (Term term : extension.terms()) {
            assertEquals(Optional.of(term), combined.term(term.id()), term.id());
        }
    }

    @Test
    void theB2cExtensionCarriesTheFourDisplayedFigures() {
        Registry extension = Registry.b2cExtension();

        assertSame(extension, Registry.b2cExtension());
        assertEquals("ESJ-B2C", extension.model());
        assertEquals("ESJ-B2C 0.1", extension.edition());
        assertEquals(List.of("BT-B2C-001", "BT-B2C-002", "BT-B2C-003", "BT-B2C-010"),
                extension.terms().stream().map(Term::id).toList());
        assertEquals(Optional.of(SemanticType.UNIT_PRICE_AMOUNT),
                extension.datatype("BT-B2C-001"));
        assertEquals(Optional.of(SemanticType.AMOUNT), extension.datatype("BT-B2C-010"));
        assertEquals(Optional.of("BG-25"), extension.parentOf("BT-B2C-002"));
        assertEquals(Optional.empty(), extension.parentOf("BT-B2C-010"));
        assertFalse(extension.isRepeatable("BT-B2C-001"));
    }

    @Test
    void theB2cTermsSitUnderTheInvoiceLineOfTheCombinedRegistry() {
        Registry combined = Registry.en16931().withExtension(Registry.b2cExtension());

        assertEquals(List.of(List.of("BG-25", "BT-B2C-001")), combined.chains("BT-B2C-001"));
        assertEquals(List.of(List.of("BT-B2C-010")), combined.chains("BT-B2C-010"));
        List<String> lineChildren = combined.children("BG-25").stream().map(Term::id).toList();
        assertTrue(lineChildren.contains("BT-131"));
        assertTrue(lineChildren.contains("BT-B2C-003"));
    }

    @Test
    void theB2cExtensionWrittenAgainstAnotherEditionIsRefused() {
        Registry later = load(resource("/model/b2c/0.1.json")
                .replace("EN 16931-1:2017+A1:2019/AC:2020", "EN 16931-1:2026"));

        EsjFormatException refusal = assertThrows(EsjFormatException.class,
                () -> Registry.en16931().withExtension(later));

        assertTrue(refusal.getMessage().contains("EN 16931-1:2026"), refusal.getMessage());
    }

    @Test
    void anExtensionWrittenAgainstAnotherEditionIsRefused() {
        String otherEdition = """
                {
                  "format": "EN16931-Semantic-JSON-registry",
                  "version": "0.1",
                  "model": "Later-Extension",
                  "edition": "Later 1.0",
                  "imports": [{"model": "EN16931-1", "edition": "EN16931-1:2026"}],
                  "terms": [
                    {
                      "id": "BT-LTR-001", "kind": "BT", "name": "Later term", "slug": "laterTerm",
                      "parent": "BG-25", "path": ["BG-25", "BT-LTR-001"], "depth": 1,
                      "min": 0, "max": 1, "datatype": "Text", "components": [], "order": 1,
                      "description": "A term of an extension written against a later edition.",
                      "notes": []
                    }
                  ]
                }
                """;

        Registry later = load(otherEdition);

        assertThrows(EsjFormatException.class, () -> Registry.en16931().withExtension(later));
    }

    @Test
    void anExtensionDoesNotRedefineACoreTerm() {
        String redefinition = """
                {
                  "format": "EN16931-Semantic-JSON-registry",
                  "version": "0.1",
                  "model": "Broken",
                  "edition": "Broken 1.0",
                  "terms": [
                    {
                      "id": "BT-1", "kind": "BT", "name": "Invoice number", "slug": "invoiceNumber",
                      "parent": null, "path": ["BT-1"], "depth": 0, "min": 1, "max": 1,
                      "datatype": "Text", "components": [], "order": 1,
                      "description": "A redefinition of a core term.", "notes": []
                    }
                  ]
                }
                """;

        Registry broken = load(redefinition);

        assertThrows(EsjFormatException.class, () -> Registry.en16931().withExtension(broken));
    }

    @Test
    void aRegistryThatIsNotOneIsRejected() {
        assertThrows(EsjFormatException.class, () -> load("[]"));
        assertThrows(EsjFormatException.class, () -> load("{\"model\": \"X\"}"));
        assertThrows(EsjFormatException.class, () -> load("""
                {"model": "X", "edition": "X 1.0", "terms": [{"id": "BT-1"}]}
                """));
    }

    @Test
    void anUnknownMemberOfARegistryIsSkipped() {
        Registry registry = load("""
                {
                  "format": "EN16931-Semantic-JSON-registry",
                  "model": "Small",
                  "edition": "Small 1.0",
                  "notice": "ignored",
                  "terms": [
                    {
                      "id": "BT-1", "kind": "BT", "name": "Number", "slug": "number",
                      "parent": null, "path": ["BT-1"], "depth": 0, "min": 1, "max": "n",
                      "datatype": "Identifier", "components": [], "order": 1,
                      "description": "A term.", "notes": [], "unknownMember": {"a": [1, 2]}
                    }
                  ]
                }
                """);

        assertEquals(1, registry.terms().size());
        assertTrue(registry.isRepeatable("BT-1"));
        assertEquals("Small 1.0", registry.edition());
    }

    @Test
    void aRegistryThatCarriesAMemberNameTwiceIsRefused() {
        EsjFormatException thrown = assertThrows(EsjFormatException.class, () -> load("""
                {
                  "model": "Small",
                  "edition": "first",
                  "edition": "second",
                  "terms": []
                }
                """));

        assertTrue(thrown.getMessage().contains("twice"), thrown.getMessage());
    }

    @Test
    void anElementOfTermsThatIsNotAnObjectIsRefusedRatherThanTruncatingTheList() {
        assertThrows(EsjFormatException.class, () -> load("""
                {
                  "model": "Small",
                  "edition": "Small 1.0",
                  "terms": [
                    {
                      "id": "BT-1", "kind": "BT", "name": "Number", "slug": "number",
                      "parent": null, "path": ["BT-1"], "depth": 0, "min": 1, "max": 1,
                      "datatype": "Identifier", "components": [], "order": 1,
                      "description": "A term.", "notes": []
                    },
                    "not a term"
                  ]
                }
                """));
    }

    /**
     * A registry travels between parties like a document does, so a fragment of it that
     * reaches a message is truncated the way a document's is (specification,
     * section 12.6), and one string of it cannot fill the file.
     */
    @Test
    void aRegistryDoesNotDecideHowLongAMessageIs() {
        String role = "r".repeat(2_000_000);
        EsjFormatException thrown = assertThrows(EsjFormatException.class, () -> load("""
                {
                  "model": "Small",
                  "edition": "Small 1.0",
                  "terms": [
                    {
                      "id": "BT-1", "kind": "BT", "name": "Number", "slug": "number",
                      "parent": null, "path": ["BT-1"], "depth": 0, "min": 1, "max": 1,
                      "datatype": "Identifier", "order": 1,
                      "components": [
                        {"id": "c", "role": "%s", "name": "n", "min": 0, "max": 1}
                      ],
                      "description": "A term.", "notes": []
                    }
                  ]
                }
                """.formatted(role)));

        assertTrue(thrown.getMessage().length() < 200, "message length "
                + thrown.getMessage().length());
    }

    @Test
    void aStringLongerThanARegistryFileCarriesIsRefused() {
        String name = "n".repeat(100_000);
        EsjFormatException thrown = assertThrows(EsjFormatException.class, () -> load("""
                {
                  "model": "Small",
                  "edition": "%s",
                  "terms": []
                }
                """.formatted(name)));

        assertTrue(thrown.getMessage().length() < 200, "message length "
                + thrown.getMessage().length());
    }

    private static String resource(String name) {
        try (InputStream in = RegistryTest.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Registry load(String json) {
        try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return Registry.load(in);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
