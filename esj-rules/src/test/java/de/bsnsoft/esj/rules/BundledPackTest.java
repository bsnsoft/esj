package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The rule directory of the repository, as this build carries it. */
class BundledPackTest {

    private static final String PACK_ID = "en16931";
    private static final String VERSION = "1.3.16";

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
        assertEquals("en16931/1.3.16", pack.name());
        assertTrue(pack.verifiedAgainst().contains("1.3.16"));
    }

    @Test
    void theBundledPackNamesItsRuleFilesItsCodeListsAndItsJavaRules() {
        RulePack pack = RulePacks.bundled(PACK_ID, VERSION);

        assertEquals(13, pack.files().size());
        assertEquals(17, pack.codeLists().size());
        assertEquals(28, pack.javaRules().size());
        assertTrue(pack.rules().size() > 180, "the pack carries " + pack.rules().size() + " rules");
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
