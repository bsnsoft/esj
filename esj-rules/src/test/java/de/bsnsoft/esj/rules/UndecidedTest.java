package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What the engine does with a value that does not spell what its semantic data type requires.
 *
 * <p>It does not guess and it does not blame the rule. The structural validator reports the
 * defect at layer L2 with the path it is at; a rule that reads it says so once, with no
 * judgement attached, and every other rule is decided as usual.
 */
class UndecidedTest {

    private static SemanticDocument withABrokenAmount() {
        return Documents.set(Documents.minimal(), "/BG-22/BT-106", "1.000,00").build();
    }

    @Test
    void aValueThatIsNotADecimalMakesOneInfoFindingAndNoVerdict() {
        List<RuleFinding> findings = Packs.run(withABrokenAmount(),
                "{\"eq\": [{\"value\": \"/BG-22/BT-106\"}, {\"const\": \"100\"}]}");

        assertEquals(1, findings.size());
        RuleFinding finding = findings.get(0);
        assertEquals(RuleSeverity.INFO, finding.severity());
        assertEquals("BR-TEST", finding.code());
        assertTrue(finding.message().startsWith("not decided:"), finding.message());
        assertTrue(finding.message().contains("/BG-22/BT-106"), finding.message());
        assertTrue(finding.message().contains("Amount"), finding.message());
    }

    @Test
    void anInfoFindingDoesNotDecideAVerdict() {
        List<RuleFinding> findings = Packs.run(withABrokenAmount(),
                "{\"eq\": [{\"value\": \"/BG-22/BT-106\"}, {\"const\": \"100\"}]}");

        assertEquals(List.of(), findings.stream().filter(RuleFinding::fatal).toList());
    }

    @Test
    void theOtherRulesAreStillDecided() {
        List<RuleFinding> findings = Packs.engine(Packs.file(
                Packs.rule("BR-A", "{\"eq\": [{\"value\": \"/BG-22/BT-106\"}, {\"const\": \"100\"}]}"),
                Packs.rule("BR-B", "{\"exists\": \"/BT-9\"}")))
                .evaluate(withABrokenAmount());

        assertEquals(List.of(RuleSeverity.INFO, RuleSeverity.FATAL),
                findings.stream().map(RuleFinding::severity).toList());
        assertEquals(List.of("BR-A", "BR-B"), findings.stream().map(RuleFinding::code).toList());
    }

    @Test
    void theSameRuleIsStillDecidedAtEveryOtherInstance() {
        SemanticDocument.Builder builder = Documents.minimal();
        Documents.line(builder, 1, "1", "100");
        Documents.line(builder, 2, "1", "100");
        SemanticDocument document = Documents.set(builder, "/BG-25/1/BT-131", "one hundred").build();

        List<RuleFinding> findings = Packs.run(document, "/BG-25/*",
                "{\"gt\": [{\"value\": \"/BT-131\"}, {\"const\": \"150\"}]}");

        assertEquals(3, findings.size());
        assertEquals(List.of(RuleSeverity.FATAL, RuleSeverity.INFO, RuleSeverity.FATAL),
                findings.stream().map(RuleFinding::severity).toList());
    }

    @Test
    void aBrokenValueInsideAnAggregateStopsThatRuleAlone() {
        SemanticDocument.Builder builder = Documents.minimal();
        Documents.line(builder, 1, "1", "100");
        SemanticDocument document = Documents.set(builder, "/BG-25/1/BT-131", "one hundred").build();

        List<RuleFinding> findings = Packs.run(document,
                "{\"eq\": [{\"sum\": \"/BG-25/*/BT-131\"}, {\"const\": \"200\"}]}");

        assertEquals(1, findings.size());
        assertEquals(RuleSeverity.INFO, findings.get(0).severity());
    }
}
