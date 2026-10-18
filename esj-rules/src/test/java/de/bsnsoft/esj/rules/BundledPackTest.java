package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The rule directory of the repository, as this build carries it. */
class BundledPackTest {

    private static final String PACK_ID = "en16931";
    /** The version of the pack this build compiles, which other tests of the module read. */
    static final String VERSION = "1.3.17";

    /** The version before it, which stays in the rule directory as it was written. */
    private static final String PREVIOUS = "1.3.16";

    /**
     * Returns the bytes of the bundled pack file, so that another test can put it beside the
     * schema of the rule language.
     *
     * @return the pack file
     */
    static String bundledPackFile() {
        String resource = "packs/" + PACK_ID + "/" + VERSION + "/pack.json";
        try (InputStream in = RulePacks.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("this build carries no " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theBundledPackIsReadAndNamesItself() {
        RulePack pack = RulePacks.bundled(PACK_ID, VERSION);

        assertEquals(PACK_ID, pack.id());
        assertEquals(VERSION, pack.version());
        assertEquals("en16931/1.3.17", pack.name());
        assertTrue(pack.verifiedAgainst().orElseThrow().contains("1.3.17"));
    }

    @Test
    void theBundledPackNamesItsRuleFilesItsCodeListsAndItsJavaRules() {
        RulePack pack = RulePacks.bundled(PACK_ID, VERSION);

        assertEquals(13, pack.files().size());
        assertEquals(17, pack.codeLists().size());
        assertEquals(28, pack.javaRules().size());
        assertTrue(pack.rules().size() > 180, "the pack carries " + pack.rules().size() + " rules");
    }

    /**
     * A released pack is never edited, so the one verified against the release before stays
     * readable beside the current one, and it still compiles with the Java rules it names:
     * the two of its classes that carry the identifiers that release gave the IGIC and IPSI
     * families are kept for it.
     */
    @Test
    void thePreviousPackStaysReadableAndCompilesWithTheClassesItNames() {
        RulePack previous = RulePacks.bundled(PACK_ID, PREVIOUS);
        RulePack current = RulePacks.bundled(PACK_ID, VERSION);

        assertEquals("en16931/1.3.16", previous.name());
        assertTrue(previous.verifiedAgainst().orElseThrow().contains("1.3.16"));
        assertEquals(current.rules().size(), previous.rules().size(),
                "the two packs carry the same rules under their own identifiers");
        assertTrue(previous.javaRuleClasses().contains(
                "de.bsnsoft.esj.rules.internal.en16931.BrAf08"));
        assertTrue(current.javaRuleClasses().contains(
                "de.bsnsoft.esj.rules.internal.en16931.BrIg08"));

        List<JavaRule> instances = new java.util.ArrayList<>();
        for (String name : previous.javaRuleClasses()) {
            try {
                instances.add((JavaRule) Class.forName(name).getConstructor().newInstance());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(name + " cannot be instantiated", e);
            }
        }
        RuleEngine engine = RuleEngine.compile(previous, Packs.REGISTRY,
                CodeLists.bundled(previous), JavaRules.of(instances));
        assertTrue(engine.ruleIds().contains("BR-AF-08"));
        assertTrue(engine.ruleIds().contains("BR-AG-01"));
        assertEquals(217, engine.ruleIds().size());
    }

    @Test
    void aPackThatNamesJavaRulesIsNotCompiledWithoutThem() {
        RulePack pack = RulePacks.bundled(PACK_ID, VERSION);

        RulePackException refused = assertThrows(RulePackException.class,
                () -> RuleEngine.compile(pack, Packs.REGISTRY));

        assertTrue(refused.getMessage().contains("names the Java rules"));
    }

    @Test
    void aPackThisBuildDoesNotCarryIsRefusedByName() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> RulePacks.bundled("xrechnung", "3.0.2"));

        assertTrue(refused.getMessage().contains("no rule pack xrechnung/3.0.2"));
    }

    @Test
    void theSchemaOfTheRuleLanguageTravelsWithTheModule() {
        try (InputStream in = RulePacks.class.getResourceAsStream("packs/rule.schema.json")) {
            assertTrue(in != null, "rules/rule.schema.json is not in the jar");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
