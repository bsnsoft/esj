package de.bsnsoft.esj.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks {@code examples/invalid/}: every fixture is rejected, and by the layer its table
 * in {@code examples/invalid/README.md} names.
 *
 * <p>The reader answers for layer L1 only, so it is the wrong tool for the fixtures whose
 * defect the registry decides: a term that does not exist, a content that does not fit the
 * semantic data type of its term, a missing component, an index gap. Those are well-formed
 * documents (specification, section 3.1), and the structural validator is what rejects
 * them. The one fixture that no layer rejects is the arithmetic mismatch, which is a
 * business rule and therefore a separate layer this version does not check (section 9.4).
 */
class InvalidFixturesTest {

    /** The fixtures the reader rejects, with the finding code of the specification, section 9.6. */
    private static final Map<String, String> REJECTED_BY_THE_READER = rejectedByTheReader();

    /** The fixtures the reader refuses as a limit of the specification, section 12.2. */
    private static final Set<String> REJECTED_BY_A_LIMIT =
            Set.of("extension-depth-33", "value-object-members-17");

    /** The fixtures that pass layer L1 and fail against the registry. */
    private static final Map<String, String> REJECTED_BY_THE_VALIDATOR = rejectedByTheValidator();

    /** The one fixture that is structurally sound and violates a business rule instead. */
    private static final String BUSINESS_RULE_ONLY = "arithmetic-mismatch";

    private final EsjReader reader = EsjReader.strict();

    private static Map<String, String> rejectedByTheReader() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("duplicate-and-surrogate-in-value-object", "ESJ-L1-DUPLICATE-MEMBER");
        table.put("duplicate-below-bad-owner-token", "ESJ-L1-OWNER-TOKEN");
        table.put("duplicate-in-two-value-objects", "ESJ-L1-DUPLICATE-MEMBER");
        table.put("duplicate-in-value-object", "ESJ-L1-DUPLICATE-MEMBER");
        table.put("duplicate-member", "ESJ-L1-DUPLICATE-MEMBER");
        table.put("duplicate-path-of-value-objects", "ESJ-L1-VALUE-SHAPE");
        table.put("empty-extensions", "ESJ-L1-ENVELOPE-VALUE");
        table.put("empty-string-value", "ESJ-L1-EMPTY-STRING");
        table.put("empty-string-with-missing-term", "ESJ-L1-EMPTY-STRING");
        table.put("extension-number-too-long", "ESJ-L1-EXT-NUMBER");
        table.put("lone-surrogate", "ESJ-L1-SURROGATE");
        table.put("number-instead-of-string", "ESJ-L1-JSON-TYPE");
        table.put("object-without-component", "ESJ-L1-VALUE-SHAPE");
        table.put("owner-token-syntax", "ESJ-L1-OWNER-TOKEN");
        table.put("path-leading-zero-index", "ESJ-L1-PATH-SYNTAX");
        table.put("scheme-version-without-scheme", "ESJ-L1-VALUE-MEMBER");
        table.put("source-empty-syntax", "ESJ-L1-ENVELOPE-VALUE");
        table.put("source-lone-surrogate", "ESJ-L1-SURROGATE");
        table.put("source-sha256-uppercase", "ESJ-L1-ENVELOPE-VALUE");
        table.put("surrogate-and-duplicate-in-value-object", "ESJ-L1-SURROGATE");
        table.put("surrogate-below-bad-owner-token", "ESJ-L1-OWNER-TOKEN");
        table.put("surrogate-in-envelope-member-name", "ESJ-L1-SURROGATE");
        table.put("surrogate-in-path", "ESJ-L1-SURROGATE");
        table.put("surrogate-in-shapeless-value-object", "ESJ-L1-SURROGATE");
        table.put("surrogate-in-source-member-name", "ESJ-L1-SURROGATE");
        table.put("unknown-envelope-member", "ESJ-L1-ENVELOPE-MEMBER");
        table.put("unknown-object-member", "ESJ-L1-VALUE-MEMBER");
        table.put("value-not-a-string", "ESJ-L1-VALUE-SHAPE");
        return Map.copyOf(table);
    }

    private static Map<String, String> rejectedByTheValidator() {
        Map<String, String> table = new LinkedHashMap<>();
        table.put("b2c-decimal-trailing-zeros", "ESJ-L2-DECIMAL");
        table.put("base64-non-canonical", "ESJ-L2-BASE64");
        table.put("binary-without-filename", "ESJ-L2-COMPONENT-MISSING");
        table.put("calendar-impossible-date", "ESJ-L2-DATE");
        table.put("edition-2026-time-without-offset", "ESJ-L2-TIME");
        table.put("decimal-too-long", "ESJ-L2-DECIMAL");
        table.put("decimal-trailing-zeros", "ESJ-L2-DECIMAL");
        table.put("index-gap", "ESJ-L3-INDEX-GAP");
        table.put("index-on-bt-1", "ESJ-L2-INDEX-FORBIDDEN");
        table.put("missing-mandatory-term", "ESJ-L3-MISSING-TERM");
        table.put("scheme-on-text-value", "ESJ-L2-COMPONENT-NOT-ALLOWED");
        table.put("unknown-term", "ESJ-L2-UNKNOWN-TERM");
        return Map.copyOf(table);
    }

    static List<String> fixtures() {
        return Examples.invalidNames();
    }

    /**
     * The registry a fixture is measured against: the one of the edition the fixture
     * names, with the extension registries of the repository that import it. A path is an
     * address relative to an edition (specification, section 10), so a fixture of the 2026
     * edition is judged by that edition's registry and by no other; and a fixture that
     * carries an extension term is measured rather than reported as not checked
     * (section 5.6), which is what a reader of this repository loads.
     *
     * @param document the fixture, as the reader read it
     * @return the registry of its edition
     */
    private static Registry registryOf(SemanticDocument document) {
        Registry core = Registry.forSemanticModel(document.semanticModel())
                .orElseThrow(() -> new AssertionError(
                        "no registry for " + document.semanticModel()));
        return core.describes(Registry.en16931().semanticModel())
                ? core.withExtension(Registry.xrechnungExtension())
                        .withExtension(Registry.b2cExtension())
                : core;
    }

    static List<String> limitFixtures() {
        return List.copyOf(REJECTED_BY_A_LIMIT);
    }

    @Test
    void everyFixtureOfTheRepositoryIsCoveredByThisTest() {
        for (String name : Examples.invalidNames()) {
            assertTrue(REJECTED_BY_THE_READER.containsKey(name)
                            || REJECTED_BY_THE_VALIDATOR.containsKey(name)
                            || REJECTED_BY_A_LIMIT.contains(name)
                            || name.equals(BUSINESS_RULE_ONLY),
                    name + " is a fixture this test does not know");
        }
    }

    @ParameterizedTest
    @MethodSource("limitFixtures")
    void aLimitFixtureIsRefusedAsALimit(String name) {
        EsjLimitException thrown = assertThrows(EsjLimitException.class,
                () -> reader.read(Examples.invalid(name)), name);
        assertEquals("ESJ-L1-LIMIT", thrown.code().code(), name);
        ReadResult result = reader.readWithFindings(Examples.invalid(name));
        assertFalse(result.isWellFormed(), name);
        assertTrue(result.findings().stream()
                .anyMatch(finding -> finding.code().code().equals("ESJ-L1-LIMIT")), name);
    }

    @Test
    void theTwinOfTheDepthFixtureIsAccepted() {
        assertEquals(1, reader.read(Examples.pretty("extension-depth")).extensions().size());
    }

    /**
     * The member bound of the specification, section 12.2 fires before the member set is
     * judged: the sixteen members of the fixture are read, the seventeenth is refused, and
     * nothing the object carries past the bound is held. A reader configured with a bound
     * of two stops two members in.
     */
    @Test
    void theMemberBoundStopsAValueObjectBeforeItIsJudged() {
        EsjReader small = EsjReader.withLimits(
                Limits.builder().maxValueMembers(2).build());

        EsjLimitException thrown = assertThrows(EsjLimitException.class,
                () -> small.read(Examples.invalid("value-object-members-17")));

        assertEquals("ESJ-L1-LIMIT", thrown.code().code());
        assertTrue(thrown.getMessage().contains("more than 2 members"), thrown.getMessage());
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void aFixtureIsRejectedByTheLayerItsTableNames(String name) {
        if (REJECTED_BY_A_LIMIT.contains(name)) {
            return;
        }
        byte[] document = Examples.invalid(name);
        String expected = REJECTED_BY_THE_READER.get(name);
        if (expected != null) {
            EsjFormatException thrown = assertThrows(
                    EsjFormatException.class, () -> reader.read(document), name);
            assertEquals(expected, thrown.code().orElseThrow().code(), name);
            ReadResult result = reader.readWithFindings(document);
            assertFalse(result.isWellFormed(), name);
            assertTrue(result.findings().stream()
                            .anyMatch(finding -> finding.code().code().equals(expected)),
                    name + " reported " + result.findings());
            return;
        }
        SemanticDocument parsed = reader.read(document);
        List<Finding> errors = StructuralValidator
                .validate(parsed, registryOf(parsed), EnumSet.of(ValidationLayer.L2, ValidationLayer.L3))
                .findings().stream()
                .filter(Finding::isError)
                .toList();
        if (name.equals(BUSINESS_RULE_ONLY)) {
            assertEquals(List.of(), errors, name);
            return;
        }
        assertTrue(errors.stream()
                        .anyMatch(finding -> finding.code().code()
                                .equals(REJECTED_BY_THE_VALIDATOR.get(name))),
                name + " reported " + errors);
    }

    /**
     * The content fixtures moved layer with the value shape. Without a type token in the
     * document, nothing but the registry says that BT-106 carries a decimal and BT-2 a
     * date, so a party without one accepts them — and must, because it has not been given
     * what it would need to judge them (specification, section 6.2).
     */
    @ParameterizedTest
    @MethodSource("fixtures")
    void aFixtureWhoseDefectNeedsTheRegistryIsWellFormed(String name) {
        if (REJECTED_BY_THE_READER.containsKey(name) || REJECTED_BY_A_LIMIT.contains(name)) {
            return;
        }
        assertTrue(reader.readWithFindings(Examples.invalid(name)).isWellFormed(), name);
    }

    @Test
    void noFixtureButTheTwoLimitOnesReachesALimitOfTheSpecification() {
        for (String name : Examples.invalidNames()) {
            if (REJECTED_BY_A_LIMIT.contains(name)) {
                continue;
            }
            byte[] document = Examples.invalid(name);
            try {
                reader.read(document);
            } catch (EsjLimitException e) {
                throw new AssertionError(name + " must not be a limit violation", e);
            } catch (EsjFormatException e) {
                assertEquals("ESJ-L1", e.code().orElseThrow().code().substring(0, 6), name);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void aFixtureThatPassesLayerOneStillCanonicalizesToStableBytes(String name) {
        if (REJECTED_BY_THE_READER.containsKey(name) || REJECTED_BY_A_LIMIT.contains(name)) {
            return;
        }
        byte[] canonical = Canonicalizer.canonicalize(Examples.invalid(name));
        assertArrayEquals(canonical, Canonicalizer.canonicalize(canonical), name);
    }

    /**
     * The rule of the specification, section 6.4, pinned to the file that carries it:
     * {@code 100.00} in a document is an error and is never made into {@code 100} on the
     * way through. A canonicalizer has no registry, so it cannot even tell that the string
     * is a decimal.
     */
    @Test
    void theDecimalFixtureIsCanonicalizedWithoutBeingRepaired() {
        String canonical = new String(
                Canonicalizer.canonicalize(Examples.invalid("decimal-trailing-zeros")),
                StandardCharsets.UTF_8);

        assertTrue(canonical.contains("\"/BG-22/BT-106\":\"100.00\""), canonical);
    }
}
