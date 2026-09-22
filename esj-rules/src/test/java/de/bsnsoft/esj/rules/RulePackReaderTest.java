package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The reader of a rule file, which is strict in the way the rest of this project is strict: a
 * member nobody reads is either a mistake or a rule that will never run, so it is refused
 * rather than ignored.
 */
class RulePackReaderTest {

    private static RulePackException refuse(String json) {
        return assertThrows(RulePackException.class, () -> Packs.read(json));
    }

    @Test
    void aPackIsReadWithItsHeaderAndItsRules() {
        RulePack pack = Packs.read(Packs.file(
                Packs.rule("BR-CO-10", "{\"exists\": \"/BT-1\"}"),
                Packs.rule("BR-CO-11", "{\"exists\": \"/BT-2\"}")));

        assertEquals("test", pack.id());
        assertEquals("1", pack.version());
        assertEquals("test/1", pack.name());
        assertEquals(List.of("BR-CO-10", "BR-CO-11"),
                pack.rules().stream().map(RuleDefinition::id).toList());
        assertEquals(RuleSeverity.FATAL, pack.rules().get(0).severity());
        assertEquals("/", pack.rules().get(0).context());
        assertEquals(List.of("BT-1"), pack.rules().get(0).terms());
        assertEquals(RuleCategory.EN_BR, pack.rules().get(0).category());
        assertEquals(Map.of(), pack.codeLists());
        assertEquals(List.of(), pack.javaRules());
    }

    @Test
    void aMemberTheFormatDoesNotDefineIsRefused() {
        assertTrue(refuse(Packs.file().replace("\"rules\": []", "\"author\": \"x\", \"rules\": []"))
                .getMessage().contains("author"));
    }

    @Test
    void theSameRuleTwiceIsRefused() {
        assertTrue(refuse(Packs.file(
                Packs.rule("BR-CO-10", "{\"exists\": \"/BT-1\"}"),
                Packs.rule("BR-CO-10", "{\"exists\": \"/BT-2\"}")))
                .getMessage().contains("twice"));
    }

    @Test
    void theSameJavaRuleTwiceIsRefused() {
        String pack = "{\"id\": \"test\", \"version\": \"1\", \"verifiedAgainst\": \"nothing\","
                + " \"description\": \"x\", \"javaRules\": [\"a.B\", \"a.B\"], \"rules\": []}";

        assertTrue(refuse(pack).getMessage().contains("twice"));
    }

    @Test
    void aSeverityTheLanguageDoesNotHaveIsRefused() {
        String pack = Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BT-1\"}"))
                .replace("\"severity\": \"fatal\"", "\"severity\": \"info\"");

        assertTrue(refuse(pack).getMessage().contains("a rule declares fatal or warning"));
    }

    @Test
    void aPackWithNeitherRulesNorRuleFilesIsRefused() {
        String pack = "{\"id\": \"test\", \"version\": \"1\", \"verifiedAgainst\": \"nothing\","
                + " \"description\": \"x\"}";

        assertTrue(refuse(pack).getMessage().contains("has no rules member and names no rule file"));
    }

    @Test
    void aRuleFileWithAPathTheFormatDoesNotAdmitIsRefused() {
        String pack = "{\"id\": \"test\", \"version\": \"1\", \"verifiedAgainst\": \"nothing\","
                + " \"description\": \"x\", \"files\": [\"../elsewhere.json\"]}";

        assertTrue(refuse(pack).getMessage().contains("which is not a path of the form"));
    }

    @Test
    void aRuleFileNamedTwiceIsRefused() {
        String pack = "{\"id\": \"test\", \"version\": \"1\", \"verifiedAgainst\": \"nothing\","
                + " \"description\": \"x\", \"files\": [\"rules/br.json\", \"rules/br.json\"]}";

        assertTrue(refuse(pack).getMessage().contains("names the rule file rules/br.json twice"));
    }

    @Test
    void aFractionalNumberIsRefusedBecauseADecimalIsAString() {
        String pack = Packs.file(Packs.rule("BR-TEST",
                "{\"eq\": [{\"value\": \"/BG-22/BT-106\"}, {\"const\": 84.03}]}"));

        assertTrue(refuse(pack).getMessage().contains("a decimal is written as a string"));
    }

    @Test
    void nullIsRefusedBecauseAMemberWithoutAValueIsLeftOut() {
        String pack = Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BT-1\"}"))
                .replace("\"source\": \"EN 16931-1, 6.4\"", "\"source\": null");

        assertTrue(refuse(pack).getMessage().contains("carries null"));
    }

    @Test
    void aMemberNamedTwiceIsRefused() {
        String pack = Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BT-1\"}"))
                .replace("\"terms\": [\"BT-1\"]", "\"terms\": [\"BT-1\"], \"terms\": [\"BT-2\"]");

        assertTrue(refuse(pack).getMessage().contains("twice"));
    }

    @Test
    void aRuleIdentifierOutsideTheShapeTheStandardsUseIsRefused() {
        String pack = Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BT-1\"}"))
                .replace("\"id\": \"BR-TEST\"", "\"id\": \"br-test\"");

        assertTrue(refuse(pack).getMessage().contains("the identifier of a rule"));
    }

    @Test
    void bytesThatAreNotJsonAreRefused() {
        assertTrue(refuse("not a rule file").getMessage().contains("could not be read"));
    }

    @Test
    void anEmptyFileIsRefused() {
        assertTrue(refuse("").getMessage().contains("is empty"));
    }

    @Test
    void moreThanOneJsonValueIsRefused() {
        assertTrue(refuse(Packs.file() + " {}").getMessage().contains("more than one JSON value"));
    }
}
