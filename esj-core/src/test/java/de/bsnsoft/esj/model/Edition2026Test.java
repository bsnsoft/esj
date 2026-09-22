package de.bsnsoft.esj.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.TermKind;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Checks the registry of the 2026 edition against the facts of that edition and against
 * the registry of the 2017 edition.
 *
 * <p>Every test is skipped where the build carries no 2026 registry: the files of that
 * edition are separable and the Maven profile {@code without-edition-2026} leaves them
 * out, so a test that failed there would be testing the distribution rather than the
 * registry.
 */
class Edition2026Test {

    /** The terms the 2026 edition makes carry a mandatory identification scheme. */
    private static final List<String> SCHEME_NOW_MANDATORY =
            List.of("BT-29", "BT-30", "BT-46", "BT-47", "BT-60", "BT-61", "BT-71");

    /** The paths of the 2017 edition that are not paths of the 2026 edition. */
    private static final Map<String, List<String>> MOVED = Map.of(
            "BT-20", List.of("BG-33", "BT-20"),
            "BG-26", List.of("BG-25", "BG-37", "BG-26"),
            "BT-134", List.of("BG-25", "BG-37", "BG-26", "BT-134"),
            "BT-135", List.of("BG-25", "BG-37", "BG-26", "BT-135"));

    static boolean carries2026() {
        return EditionsTest.carries2026();
    }

    private static Registry registry() {
        return Registry.forEdition("2026");
    }

    @Test
    @EnabledIf("carries2026")
    void theRegistryHasTheTermsOfTableTwo() {
        List<Term> terms = registry().terms();
        assertEquals(255, terms.size());
        assertEquals(216, terms.stream().filter(t -> t.kind() == TermKind.BT).count());
        assertEquals(39, terms.stream().filter(Term::isGroup).count());
        assertEquals(19, terms.stream().mapToInt(t -> t.components().size()).sum());
    }

    @Test
    @EnabledIf("carries2026")
    void theTermsStandInTheOrderOfTableTwo() {
        List<Term> terms = registry().terms();
        for (int i = 0; i < terms.size(); i++) {
            assertEquals(i + 1, terms.get(i).order(), terms.get(i).id());
        }
    }

    @Test
    @EnabledIf("carries2026")
    void everyPathIsTheChainOfItsParents() {
        Registry registry = registry();
        for (Term term : registry.terms()) {
            List<String> expected = new ArrayList<>();
            Optional<String> parent = term.parent();
            while (parent.isPresent()) {
                Term above = registry.term(parent.get())
                        .orElseThrow(() -> new AssertionError(
                                term.id() + " hangs under " + term.parent().orElseThrow()
                                        + ", which the registry does not define"));
                expected.add(0, above.id());
                parent = above.parent();
            }
            expected.add(term.id());
            assertEquals(expected, term.path(), term.id());
            assertEquals(expected.size() - 1, term.depth(), term.id());
        }
    }

    @Test
    @EnabledIf("carries2026")
    void theEditionRemovesNoTermOfTheOneBeforeIt() {
        Registry newer = registry();
        for (Term term : Registry.en16931().terms()) {
            Term now = newer.term(term.id()).orElseThrow(
                    () -> new AssertionError(term.id() + " is gone from the 2026 edition"));
            assertEquals(term.kind(), now.kind(), term.id());
            assertEquals(term.slug(), now.slug(), term.id() + " keeps its name stem");
        }
    }

    @Test
    @EnabledIf("carries2026")
    void onlyTheRecordedPathsMoved() {
        Registry newer = registry();
        Map<String, List<String>> moved = new HashMap<>();
        for (Term term : Registry.en16931().terms()) {
            Term now = newer.term(term.id()).orElseThrow();
            if (!term.path().equals(now.path())) {
                moved.put(term.id(), now.path());
            }
        }
        assertEquals(MOVED, moved);
    }

    @Test
    @EnabledIf("carries2026")
    void theSlugsAreUniqueAmongSiblings() {
        Set<String> seen = new HashSet<>();
        for (Term term : registry().terms()) {
            String key = term.parent().orElse("") + "/" + term.slug();
            assertTrue(seen.add(key), "two children of " + term.parent().orElse("the root")
                    + " carry the stem " + term.slug());
        }
    }

    @Test
    @EnabledIf("carries2026")
    void theSevenTightenedSchemesAreMandatory() {
        for (String id : SCHEME_NOW_MANDATORY) {
            Component before = Registry.en16931().term(id).orElseThrow()
                    .component(Component.Role.SCHEME).orElseThrow();
            Component now = registry().term(id).orElseThrow()
                    .component(Component.Role.SCHEME).orElseThrow();
            assertFalse(before.isMandatory(), id);
            assertTrue(now.isMandatory(), id);
        }
    }

    @Test
    @EnabledIf("carries2026")
    void aDecimalLimitIsStatedAsANumberOrAsARuleAndNeverAsBoth() {
        for (Term term : registry().terms()) {
            SemanticType type = term.datatype().orElse(null);
            boolean decimal = type != null && type.isDecimal();
            assertFalse(term.maxDecimals().isPresent() && term.maxDecimalsRule().isPresent(),
                    term.id());
            if (!decimal) {
                assertTrue(term.maxDecimals().isEmpty(), term.id());
                assertTrue(term.maxDecimalsRule().isEmpty(), term.id());
            }
            term.maxDecimalsRule().ifPresent(rule -> assertTrue(
                    List.of("iso4217-minor-unit", "iso4217-minor-unit-plus-2").contains(rule),
                    term.id() + " states the rule " + rule));
            if (type == SemanticType.AMOUNT) {
                assertTrue(term.maxDecimals().isPresent() || term.maxDecimalsRule().isPresent(),
                        term.id() + " is an amount and Table 28 bounds every amount");
            }
        }
    }

    @Test
    @EnabledIf("carries2026")
    void theEditionBeforeItStatesEveryDecimalLimitAsANumber() {
        for (Term term : Registry.en16931().terms()) {
            assertTrue(term.maxDecimalsRule().isEmpty(), term.id());
            assertTrue(term.reqIds().isEmpty(), term.id());
        }
    }

    @Test
    @EnabledIf("carries2026")
    void theRequirementIdentifiersAreThereWhereTheTableGivesThem() {
        List<Term> withRequirements = registry().terms().stream()
                .filter(term -> !term.reqIds().isEmpty()).toList();
        assertTrue(withRequirements.size() > 240,
                "the 2026 table gives requirement identifiers on nearly every row");
        for (Term term : withRequirements) {
            for (String id : term.reqIds()) {
                assertTrue(id.matches("R[0-9]+[a-z]?"), term.id() + " names " + id);
            }
        }
    }

    @Test
    @EnabledIf("carries2026")
    void exactlyOneTermCarriesTheNewSemanticDataType() {
        List<String> times = registry().terms().stream()
                .filter(term -> term.datatype().orElse(null) == SemanticType.TIME)
                .map(Term::id).toList();
        assertEquals(List.of("BT-166"), times);
        assertTrue(Registry.en16931().terms().stream()
                .noneMatch(term -> term.datatype().orElse(null) == SemanticType.TIME));
    }

    @Test
    @EnabledIf("carries2026")
    void theRegistryValidatesAgainstTheRegistrySchema() {
        assertEquals(List.of(), SchemaRegistry
                .withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(text("/model/registry.schema.json"), InputFormat.JSON)
                .validate(text("/model/en16931/2026.json"), InputFormat.JSON));
    }

    static String text(String resource) {
        try (InputStream in = Edition2026Test.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
