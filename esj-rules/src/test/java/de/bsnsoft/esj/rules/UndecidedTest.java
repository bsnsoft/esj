package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.validate.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the engine does with a value that does not spell what its semantic data type requires,
 * and with a rule that names a case it has no answer for.
 *
 * <p>It does not guess and it does not blame the rule. The structural validator reports the
 * defect at layer L2 with the path it is at; a rule that reads it says so once, with no
 * judgement attached, and every other rule is decided as usual. A rule whose {@code undecided}
 * case holds says the same, with the reason it gives, and weighs nothing else.
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
        assertEquals(Severity.INFO, finding.severity());
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

        assertEquals(List.of(Severity.INFO, Severity.ERROR),
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
        assertEquals(List.of(Severity.ERROR, Severity.INFO, Severity.ERROR),
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
        assertEquals(Severity.INFO, findings.get(0).severity());
    }

    /**
     * A rule about a line that divides by its quantity, and says it is not decided where the
     * quantity is zero rather than holding silently.
     */
    private static final String PER_UNIT = Packs.rule("BR-TEST", "/BG-25/*",
            "{\"eq\": [{\"value\": \"/BG-29/BT-146\"}, {\"div\": [{\"value\": \"/BT-131\"},"
                    + " {\"value\": \"/BT-129\"}]}]}")
            .replace("\"message\":", "\"undecided\": {\"when\": {\"eq\": [{\"value\": \"/BT-129\"},"
                    + " {\"const\": \"0\"}]}, \"message\": \"the quantity at {@/BT-129} is"
                    + " {/BT-129}, and a price per unit of no units is no number\"}, \"message\":");

    private static List<RuleFinding> perUnit(String quantity, String net) {
        SemanticDocument document = Documents.set(Documents.set(Documents.minimal(),
                "/BG-25/0/BT-129", quantity), "/BG-25/0/BT-131", net).build();
        return Packs.engine(Packs.file(PER_UNIT)).evaluate(document);
    }

    @Test
    void whereTheCaseARuleNamesHoldsItIsNotDecidedAndSaysWhy() {
        List<RuleFinding> findings = perUnit("0", "100");

        assertEquals(1, findings.size());
        RuleFinding finding = findings.get(0);
        assertEquals(Severity.INFO, finding.severity());
        assertEquals("BR-TEST", finding.code());
        assertEquals("not decided: the quantity at /BG-25/0/BT-129 is 0, and a price per unit of"
                + " no units is no number", finding.message());
        assertTrue(finding.paths().contains("/BG-25/0/BT-129"), finding.paths().toString());
    }

    @Test
    void whereTheCaseDoesNotHoldTheAssertionDecides() {
        assertEquals(List.of(), perUnit("1", "100"));
        assertEquals(List.of(Severity.ERROR),
                perUnit("2", "100").stream().map(RuleFinding::severity).toList());
    }

    @Test
    void aCaseThatCannotBeDecidedIsNotTrue() {
        SemanticDocument.Builder builder = Documents.minimal();
        builder.remove("/BG-25/0/BT-129");

        assertEquals(List.of(), Packs.engine(Packs.file(PER_UNIT)).evaluate(builder.build()));
    }
}
