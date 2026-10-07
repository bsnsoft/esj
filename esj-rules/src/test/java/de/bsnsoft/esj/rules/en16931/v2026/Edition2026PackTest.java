package de.bsnsoft.esj.rules.en16931.v2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleOracle;
import de.bsnsoft.esj.rules.RulePack;
import de.bsnsoft.esj.rules.RulePackException;
import de.bsnsoft.esj.rules.RulePackSource;
import de.bsnsoft.esj.rules.RulePackSources;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The pack of EN 16931-1:2026 as a whole: that it reads, that it compiles against the
 * registry of its own edition and against no other, and that it says per rule what stands
 * behind it.
 */
class Edition2026PackTest {

    private static final Registry REGISTRY = Registry.forEdition("2026");

    @Test
    void thePackNamesItsEditionAndNoArtefactRelease() {
        RulePack pack = new En16931V2026Pack().pack();

        assertEquals("en16931-2026/0.1", pack.name());
        assertEquals("EN 16931-1:2026", pack.edition());
        assertTrue(pack.verifiedAgainst().isEmpty());
    }

    @Test
    void theBuildOffersThePackUnderItsEdition() {
        List<String> editions = RulePackSources.editions();

        assertTrue(editions.contains("EN 16931-1:2026"), editions.toString());
        assertTrue(editions.contains("EN 16931-1:2017+A1:2019/AC:2020"), editions.toString());
    }

    @Test
    void thePackCompilesAgainstTheRegistryOfItsEdition() {
        RuleEngine engine = new En16931V2026Pack().engine(REGISTRY);

        assertFalse(engine.ruleIds().isEmpty());
        assertEquals(engine.ruleIds().size(),
                engine.pack().rules().size() + engine.pack().javaRules().size());
    }

    @Test
    void thePackIsRefusedAgainstTheRegistryOfAnotherEdition() {
        RulePackSource source = new En16931V2026Pack();

        RulePackException refused = assertThrows(RulePackException.class,
                () -> source.engine(Registry.en16931()));

        assertTrue(refused.getMessage().contains("is written for EN 16931-1:2026"),
                refused.getMessage());
    }

    /**
     * The table of {@code conformance/rules-2026/coverage.md} says of every rule of the edition
     * whether the pack carries it and with which oracle, and the pack agrees with every row.
     */
    @Test
    void theCoverageTableSaysWhatThePackCarries() {
        RuleEngine engine = new En16931V2026Pack().engine(REGISTRY);
        int rows = 0;
        for (String line : Evidence2026.text("/conformance/rules-2026/coverage.md").split("\n")) {
            String[] cells = line.split("\\|");
            if (!line.startsWith("| `BR-") || cells.length != 5) {
                continue;
            }
            rows++;
            String rule = cells[1].trim().replace("`", "");
            String oracle = cells[4].trim().replace("`", "");
            if (cells[2].trim().equals("not applicable")) {
                assertFalse(engine.ruleIds().contains(rule), rule + " is carried and the table"
                        + " says it is not applicable");
                continue;
            }
            assertEquals(oracle, engine.oracleOf(rule).map(RuleOracle::token).orElse("absent"),
                    rule + " in the coverage table");
        }
        assertEquals(199, rows, "the table has one row per rule of clause 6.4");
    }

    @Test
    void everyRuleSaysWhatStandsBehindIt() {
        RuleEngine engine = new En16931V2026Pack().engine(REGISTRY);

        for (String ruleId : engine.ruleIds()) {
            RuleOracle oracle = engine.oracleOf(ruleId).orElseThrow();
            assertTrue(oracle == RuleOracle.DOWNGRADE || oracle == RuleOracle.CASES,
                    ruleId + " rests on " + oracle.token()
                            + ", and no official artefact covers this edition");
        }
    }
}
