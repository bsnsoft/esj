package de.bsnsoft.esj.model;

import static de.bsnsoft.esj.model.JsonTree.list;
import static de.bsnsoft.esj.model.JsonTree.map;
import static de.bsnsoft.esj.model.JsonTree.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Checks {@code model/en16931/upgrade-2017-2026.json} against the two registries it
 * names. The mapping is data an upgrade reads instead of carrying the migration in code,
 * so every statement in it has to be a statement the registries also make; a mapping that
 * drifts from them would move values to addresses that do not exist.
 */
class UpgradeMappingTest {

    private static Object mapping;

    static boolean carries2026() {
        return EditionsTest.carries2026();
    }

    private static Object mapping() {
        if (mapping == null) {
            mapping = JsonTree.of(Edition2026Test.text("/model/en16931/upgrade-2017-2026.json"));
        }
        return mapping;
    }

    private static List<String> path(Object entry, String member) {
        List<String> ids = new ArrayList<>();
        for (Object id : list(entry, member)) {
            ids.add((String) id);
        }
        return ids;
    }

    @Test
    @EnabledIf("carries2026")
    void theMappingValidatesAgainstItsSchema() {
        assertEquals(List.of(), SchemaRegistry
                .withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(Edition2026Test.text("/model/en16931/upgrade.schema.json"),
                        InputFormat.JSON)
                .validate(Edition2026Test.text("/model/en16931/upgrade-2017-2026.json"),
                        InputFormat.JSON));
    }

    @Test
    @EnabledIf("carries2026")
    void theMappingNamesTheTwoRegistriesAndTheEditionStringsADocumentWrites() {
        Object doc = mapping();
        assertEquals("EN16931-Semantic-JSON-upgrade", text(doc, "format"));
        assertEquals(Registry.en16931().model(), text(doc, "model"));
        for (Map.Entry<String, Registry> side : Map.of(
                "from", Registry.forEdition("2017"),
                "to", Registry.forEdition("2026")).entrySet()) {
            Map<String, Object> end = map(doc, side.getKey());
            assertEquals(side.getValue().edition(), text(end, "edition"));
            assertEquals(side.getValue().edition().replace(" ", ""),
                    text(end, "semanticModel"));
            assertTrue(side.getValue().describes(text(end, "semanticModel")));
        }
    }

    @Test
    @EnabledIf("carries2026")
    void everyPathRewriteIsAPathTheTwoRegistriesGive() {
        Registry older = Registry.forEdition("2017");
        Registry newer = Registry.forEdition("2026");
        for (Object entry : list(mapping(), "pathRewrites")) {
            String id = text(entry, "term");
            assertEquals(older.term(id).orElseThrow().path(), path(entry, "from"), id);
            assertEquals(newer.term(id).orElseThrow().path(), path(entry, "to"), id);
            if ("index-added".equals(text(entry, "change"))) {
                assertEquals(path(entry, "from"), path(entry, "to"), id);
                assertFalse(older.isRepeatable(id), id);
                assertTrue(newer.isRepeatable(id), id);
            } else {
                assertFalse(path(entry, "from").equals(path(entry, "to")), id);
            }
        }
    }

    @Test
    @EnabledIf("carries2026")
    void thePathRewritesAreEveryPathOfOneEditionThatIsNotAPathOfTheOther() {
        Registry older = Registry.forEdition("2017");
        Registry newer = Registry.forEdition("2026");
        Set<String> expected = new TreeSet<>();
        for (Term term : older.terms()) {
            Term now = newer.term(term.id()).orElseThrow();
            if (!term.path().equals(now.path()) || term.isRepeatable() != now.isRepeatable()) {
                expected.add(term.id());
            }
        }
        Set<String> listed = new TreeSet<>();
        for (Object entry : list(mapping(), "pathRewrites")) {
            listed.add(text(entry, "term"));
        }
        assertEquals(expected, listed);
    }

    @Test
    @EnabledIf("carries2026")
    void theAddedTermsAreTheTermsOnlyTheTargetEditionHas() {
        Registry older = Registry.forEdition("2017");
        Registry newer = Registry.forEdition("2026");
        Set<String> expected = new LinkedHashSet<>();
        for (Term term : newer.terms()) {
            if (older.term(term.id()).isEmpty()) {
                expected.add(term.id());
            }
        }
        Set<String> listed = new LinkedHashSet<>();
        for (Object entry : list(mapping(), "addedTerms")) {
            String id = text(entry, "term");
            listed.add(id);
            Term term = newer.term(id).orElseThrow();
            assertEquals(term.path(), path(entry, "path"), id);
            assertEquals(term.kind().name(), text(entry, "kind"), id);
            assertEquals(cardinality(term), text(entry, "cardinality"), id);
        }
        assertEquals(expected, listed);
        assertEquals(List.of(), list(mapping(), "removedTerms"),
                "the 2026 edition removes no term of the 2017 edition");
    }

    @Test
    @EnabledIf("carries2026")
    void everyMandatoryNewTermSaysWhichGroupItIsMandatoryIn() {
        Registry newer = Registry.forEdition("2026");
        for (Object entry : list(mapping(), "addedTerms")) {
            Term term = newer.term(text(entry, "term")).orElseThrow();
            if (!term.isMandatory()) {
                assertEquals(null, text(entry, "mandatoryInside"), term.id());
                continue;
            }
            String group = text(entry, "mandatoryInside");
            assertEquals(term.parent().orElse(null), group, term.id());
            assertEquals(Boolean.TRUE, JsonTree.get(entry, "parentOptional"),
                    term.id() + " is mandatory inside " + group + ", and a document of the"
                            + " older edition stays complete only while that group is optional");
        }
    }

    @Test
    @EnabledIf("carries2026")
    void everyCardinalityAndTypeChangeIsOneTheRegistriesMake() {
        Registry older = Registry.forEdition("2017");
        Registry newer = Registry.forEdition("2026");
        for (Object entry : list(mapping(), "cardinalityChanges")) {
            String id = text(entry, "term");
            assertEquals(cardinality(older.term(id).orElseThrow()), text(entry, "from"), id);
            assertEquals(cardinality(newer.term(id).orElseThrow()), text(entry, "to"), id);
        }
        for (Object entry : list(mapping(), "datatypeChanges")) {
            String id = text(entry, "term");
            assertEquals(older.datatype(id).orElseThrow().registryDatatype(),
                    text(entry, "from"), id);
            assertEquals(newer.datatype(id).orElseThrow().registryDatatype(),
                    text(entry, "to"), id);
        }
    }

    @Test
    @EnabledIf("carries2026")
    void everyComponentChangeIsOneTheRegistriesMake() {
        Registry older = Registry.forEdition("2017");
        Registry newer = Registry.forEdition("2026");
        for (Object entry : list(mapping(), "componentChanges")) {
            String id = text(entry, "term");
            Component.Role role = Component.Role.findByMember(text(entry, "role")).orElseThrow();
            switch (text(entry, "change")) {
                case "added" -> {
                    assertTrue(older.term(id).orElseThrow().component(role).isEmpty(), id);
                    assertTrue(newer.term(id).orElseThrow().component(role).isPresent(), id);
                }
                case "tightened" -> {
                    assertFalse(older.term(id).orElseThrow().component(role)
                            .orElseThrow().isMandatory(), id);
                    assertTrue(newer.term(id).orElseThrow().component(role)
                            .orElseThrow().isMandatory(), id);
                }
                default -> throw new AssertionError(
                        id + " records the change " + text(entry, "change"));
            }
        }
    }

    @Test
    @EnabledIf("carries2026")
    void everyRenamedTermKeepsTheStemOfTheOlderEdition() {
        Registry older = Registry.forEdition("2017");
        Registry newer = Registry.forEdition("2026");
        for (Object entry : list(mapping(), "renamedTerms")) {
            String id = text(entry, "term");
            assertEquals(older.term(id).orElseThrow().name(), text(entry, "from"), id);
            assertEquals(newer.term(id).orElseThrow().name(), text(entry, "to"), id);
            assertEquals(older.term(id).orElseThrow().slug(), text(entry, "slug"), id);
            assertEquals(text(entry, "from").equalsIgnoreCase(text(entry, "to")),
                    JsonTree.get(entry, "caseOnly"), id);
        }
    }

    @Test
    @EnabledIf("carries2026")
    void everyOpenPointIsReportedAndNoneIsRepaired() {
        for (Object entry : list(mapping(), "openPoints")) {
            assertEquals("report", text(entry, "behaviour"), text(entry, "id"));
            for (Object id : list(entry, "terms")) {
                assertTrue(Registry.forEdition("2026").term((String) id).isPresent(),
                        (String) id);
            }
        }
        assertFalse(list(mapping(), "openPoints").isEmpty());
        assertFalse(list(map(mapping(), "reverse"), "refusals").isEmpty());
    }

    @Test
    @EnabledIf("carries2026")
    void everyRefusalOfTheDowngradeNamesTermsTheNewerEditionHas() {
        for (Object entry : list(map(mapping(), "reverse"), "refusals")) {
            for (Object id : list(entry, "terms")) {
                assertTrue(Registry.forEdition("2026").term((String) id).isPresent(),
                        (String) id + " is named by the refusal " + text(entry, "id"));
            }
        }
    }

    private static String cardinality(Term term) {
        return term.cardinality().min() + ".." + (term.isRepeatable()
                ? "n" : String.valueOf(term.cardinality().max()));
    }
}
