package de.bsnsoft.esj.rules.en16931;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.rules.CodeList;
import de.bsnsoft.esj.rules.RulePack;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The pack against its own coverage table.
 *
 * <p>A rule pack that says "the business rules of EN 16931" and does not say which ones is a claim
 * nobody can check, so {@code conformance/rules/coverage.md} says which ones, rule by rule, and
 * this test holds the table to the pack. Every identifier of the official validation artefacts of
 * release 1.3.16 appears in the table exactly once; every one the table calls implemented is a
 * rule of this engine; every one it does not is named in {@code not-applicable.md} with a reason.
 * A rule added to the pack without a row, or a row without a rule, fails here.
 */
class En16931PackTest {

    /** One row of the coverage table: the rule and how the table says it is covered. */
    private static final Pattern ROW = Pattern.compile("^\\| `(BR-[A-Z0-9-]+)` \\|(?: [a-z]+ \\|)? "
            + "(native-json|native-java|not applicable|deferred) \\|");

    private static List<String[]> rows(String page) {
        List<String[]> rows = new ArrayList<>();
        for (String line : page.split("\\R")) {
            Matcher matcher = ROW.matcher(line);
            if (matcher.find()) {
                rows.add(new String[] {matcher.group(1), matcher.group(2)});
            }
        }
        return rows;
    }

    private static List<String[]> coverage() {
        return rows(Pack.text("/conformance/rules/coverage.md"));
    }

    @Test
    void everyRuleTheTableCallsImplementedIsARuleOfThisEngine() {
        Set<String> implemented = new TreeSet<>();
        for (String[] row : coverage()) {
            if (row[1].startsWith("native-")) {
                implemented.add(row[0]);
            }
        }

        assertTrue(new TreeSet<>(Pack.ENGINE.ruleIds()).containsAll(implemented),
                "the coverage table names rules this engine does not carry");
    }

    @Test
    void everyRuleTheTableDoesNotCallImplementedIsNamedWithAReason() {
        String reasons = Pack.text("/conformance/rules/not-applicable.md");
        for (String[] row : coverage()) {
            if (row[1].startsWith("native-")) {
                continue;
            }
            assertTrue(reasons.contains("`" + row[0] + "`"),
                    row[0] + " is " + row[1] + " in the coverage table and not explained in"
                            + " conformance/rules/not-applicable.md");
            assertFalse(Pack.ENGINE.ruleIds().contains(row[0]),
                    row[0] + " is " + row[1] + " in the coverage table and is in the pack");
        }
    }

    @Test
    void noRuleAppearsTwiceInTheTable() {
        Set<String> seen = new LinkedHashSet<>();
        for (String[] row : coverage()) {
            assertTrue(seen.add(row[0]), row[0] + " has two rows in the coverage table");
        }

        assertEquals(223, seen.size(), "release 1.3.16 carries 201 model rules and 22 code list rules");
    }

    @Test
    void everyRuleOfTheEngineHasARowExceptTheOneTheArtefactsDoNotCarry() {
        Set<String> inTable = new TreeSet<>();
        for (String[] row : coverage()) {
            inTable.add(row[0]);
        }
        Set<String> missing = new TreeSet<>(Pack.ENGINE.ruleIds());
        missing.removeAll(inTable);

        assertEquals(Set.of("BR-CO-25"), missing, "BR-CO-25 is a rule of EN 16931-1, clause 6.4.2,"
                + " that release 1.3.16 does not carry; every other rule of the pack answers to an"
                + " identifier of the release and has a row");
    }

    @Test
    void theManifestNamesTheFilesTheRulesAreIn() {
        RulePack pack = En16931.pack();

        assertEquals(List.of("rules/br-cl.json", "rules/br-co.json", "rules/br-dec.json",
                        "rules/br.json", "rules/vat-ae.json", "rules/vat-e.json", "rules/vat-g.json",
                        "rules/vat-ic.json", "rules/vat-ig.json", "rules/vat-ip.json",
                        "rules/vat-o.json", "rules/vat-s.json", "rules/vat-z.json"),
                pack.files());
        assertEquals(28, pack.javaRules().size());
        assertEquals(189, pack.rules().size());
    }

    @Test
    void everyCodeListTheManifestNamesIsLoadedAndSaysWhereItCameFrom() {
        RulePack pack = En16931.pack();

        assertEquals(pack.codeLists().keySet(), Pack.ENGINE.codeLists().listIds());
        for (String listId : pack.codeLists().keySet()) {
            CodeList list = Pack.ENGINE.codeLists().require(listId);
            assertEquals(listId, list.listId());
            assertEquals(pack.codeLists().get(listId), list.retrieved());
            assertTrue(list.size() > 0, listId + " is empty");
            assertFalse(list.publisher().isBlank(), listId + " names no publisher");
            assertFalse(list.source().isBlank(), listId + " names no source");
        }
    }

    @Test
    void theSnapshotsCarryTheCodesTheRulesAreAboutAndNotTheOnesTheyAreNot() {
        assertTrue(Pack.ENGINE.codeLists().require("untdid-5305").contains("AE"));
        assertFalse(Pack.ENGINE.codeLists().require("untdid-5305").contains("B"));
        assertTrue(Pack.ENGINE.codeLists().require("iso-4217").contains("EUR"));
        assertTrue(Pack.ENGINE.codeLists().require("iso-3166-1").contains("DE"));
        assertTrue(Pack.ENGINE.codeLists().require("unece-rec20").contains("C62"));
        assertTrue(Pack.ENGINE.codeLists().require("unece-rec21").contains("XBX"));
        assertTrue(Pack.ENGINE.codeLists().require("vatex").contains("VATEX-EU-132"));
        assertTrue(Pack.ENGINE.codeLists().require("eas").contains("EM"));
        assertTrue(Pack.ENGINE.codeLists().require("iso-6523-icd").contains("0088"));
        assertFalse(Pack.ENGINE.codeLists().require("iso-6523-icd").contains("SEPA"));
        assertTrue(Pack.ENGINE.codeLists().require("untdid-1153").contains("AAJ"));
        assertTrue(Pack.ENGINE.codeLists().require("untdid-7143").contains("SRV"));
        assertTrue(Pack.ENGINE.codeLists().require("mime-code").contains("application/pdf"));
        assertFalse(Pack.ENGINE.codeLists().require("mime-code").contains("application/zip"));
    }

    @Test
    void everyRuleNamesTheClauseItStates() {
        for (String ruleId : Pack.ENGINE.ruleIds()) {
            String source = Pack.ENGINE.sourceOf(ruleId).orElseThrow();
            assertTrue(source.startsWith("EN 16931-1, "), ruleId + " names " + source);
            assertFalse(Pack.ENGINE.termsOf(ruleId).orElseThrow().isEmpty(),
                    ruleId + " declares no term");
        }
    }
}
