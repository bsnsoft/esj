package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What a finding says, and what it does with a fragment of the document it quotes. */
class MessageTest {

    private static String messageOf(SemanticDocument document, String rule) {
        List<RuleFinding> findings = Packs.engine(Packs.file(rule)).evaluate(document);
        assertEquals(1, findings.size());
        return findings.get(0).message();
    }

    private static String rule(String assertion, String message) {
        return rule(assertion, message, "");
    }

    private static String rule(String assertion, String message, String bind) {
        return "{\"id\": \"BR-TEST\", \"severity\": \"fatal\", \"context\": \"/\","
                + " \"terms\": [\"BT-106\"], \"assert\": " + assertion + ","
                + (bind.isEmpty() ? "" : " \"bind\": " + bind + ",")
                + " \"message\": \"" + message + "\","
                + " \"source\": \"EN 16931-1, 6.4\"}";
    }

    @Test
    void aPlaceholderShowsTheValueAndAnotherShowsThePath() {
        String message = messageOf(Documents.minimal().build(), rule(
                "{\"const\": false}",
                "BT-106 at {@/BG-22/BT-106} carries {/BG-22/BT-106}."));

        assertEquals("BT-106 at /BG-22/BT-106 carries 100.", message);
    }

    @Test
    void aValueThatIsNotThereReadsAbsent() {
        String message = messageOf(Documents.minimal().build(), rule(
                "{\"const\": false}", "BT-114 carries {/BG-22/BT-114}."));

        assertEquals("BT-114 carries (absent).", message);
    }

    @Test
    void aBoundExpressionIsShownUnderItsName() {
        SemanticDocument.Builder builder = Documents.minimal();
        Documents.line(builder, 1, "1", "50");

        String message = messageOf(builder.build(), rule(
                "{\"const\": false}",
                "The lines come to {$lines}.",
                "{\"lines\": {\"sum\": \"/BG-25/*/BT-131\"}}"));

        assertEquals("The lines come to 150.", message);
    }

    @Test
    void theInstanceIsAPlaceholderOfItsOwn() {
        SemanticDocument.Builder builder = Documents.minimal();
        Documents.line(builder, 1, "1", "50");

        List<RuleFinding> findings = Packs.engine(Packs.file(
                "{\"id\": \"BR-TEST\", \"severity\": \"fatal\", \"context\": \"/BG-25/*\","
                        + " \"terms\": [\"BT-131\"], \"assert\": {\"const\": false},"
                        + " \"message\": \"The line at {.} is wrong.\","
                        + " \"source\": \"EN 16931-1, 6.4\"}")).evaluate(builder.build());

        assertEquals(List.of("The line at /BG-25/0 is wrong.", "The line at /BG-25/1 is wrong."),
                findings.stream().map(RuleFinding::message).toList());
    }

    @Test
    void theDocumentContextReadsAsASolidus() {
        String message = messageOf(Documents.minimal().build(), rule(
                "{\"const\": false}", "At {.}."));

        assertEquals("At /.", message);
    }

    @Test
    void aBraceIsWrittenTwice() {
        String message = messageOf(Documents.minimal().build(), rule(
                "{\"const\": false}", "A brace: {{ and }}."));

        assertEquals("A brace: { and }.", message);
    }

    @Test
    void aQuotedFragmentOfTheDocumentIsEscaped() {
        String odd = "RE\t2026" + (char) 0x202e + "0001\n\"x\"\\";
        SemanticDocument document = Documents.set(Documents.minimal(), "/BT-1", odd).build();

        String message = messageOf(document, rule("{\"const\": false}", "BT-1 carries {/BT-1}."));

        assertEquals("BT-1 carries RE\\t2026\\u202E0001\\n\\\"x\\\"\\\\.", message);
    }

    @Test
    void aMessageThatNamesATermNobodyHasIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> Packs.engine(Packs.file(rule("{\"const\": false}", "{/BT-9999}"))));

        assertTrue(refused.getMessage().contains("BT-9999"));
    }

    @Test
    void aMessageThatNamesAnUnboundNameIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> Packs.engine(Packs.file(rule("{\"const\": false}", "{$lines}"))));

        assertTrue(refused.getMessage().contains("which the rule does not bind"));
    }

    @Test
    void anUnclosedPlaceholderIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> Packs.engine(Packs.file(rule("{\"const\": false}", "{/BT-1"))));

        assertTrue(refused.getMessage().contains("never closes it"));
    }

    @Test
    void aFindingCarriesTheCategoryTheIdentifierDecidesAndThePackItCameFrom() {
        List<RuleFinding> findings = Packs.engine(Packs.file(
                Packs.rule("BR-CO-10", "{\"const\": false}"),
                Packs.rule("BR-DEC-12", "{\"const\": false}"),
                Packs.rule("BR-CL-01", "{\"const\": false}")))
                .evaluate(Documents.minimal().build());

        assertEquals(List.of(RuleCategory.EN_CL, RuleCategory.EN_BR, RuleCategory.EN_DEC),
                findings.stream().map(RuleFinding::category).toList());
        assertEquals(List.of("test", "test", "test"),
                findings.stream().map(RuleFinding::packId).toList());
        assertEquals(List.of("native", "native", "native"),
                findings.stream().map(RuleFinding::engine).toList());
    }
}
