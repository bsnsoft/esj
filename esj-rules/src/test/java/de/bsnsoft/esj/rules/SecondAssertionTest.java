package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The second assertion of a rule: {@code warn}.
 *
 * <p>A rule states one thing and, where the two official artefacts of a release do not ask
 * for the same closeness of the same two figures, it has a second thing to say about the
 * zone between them. The first assertion decides the verdict and the second is a warning,
 * weighed only where the first one holds.
 */
class SecondAssertionTest {

    private static final String WARNING = """
            {"assert": {"le": [{"value": "/BG-22/BT-106"}, {"const": 100}]},
             "message": "WARNING at {@/BG-22/BT-106}"}
            """;

    private static final String TEMPLATE = """
            {"id": "test", "version": "1",
             "verifiedAgainst": "nothing; this pack is a test fixture",
             "description": "A rule with a second assertion.",
             "rules": [
               {"id": "BR-CO-10", "severity": "fatal", "context": "/",
                "terms": ["BT-106", "BT-131"],
                "assert": {"le": [{"value": "/BG-22/BT-106"}, {"const": 200}]},
                "warn": SECOND,
                "message": "FAULT at {@/BG-22/BT-106}",
                "source": "EN 16931-1, 6.4.2, BR-CO-10"}]}
            """;

    private static final String PACK = withWarning(WARNING);

    private static RulePack read(String json) {
        try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return RulePacks.read(in, "a test pack");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<RuleFinding> findings(String total) {
        SemanticDocument.Builder builder = Documents.minimal();
        Documents.set(builder, "/BG-22/BT-106", total);
        return RuleEngine.compile(read(PACK), Packs.REGISTRY, CodeLists.empty(), JavaRules.none())
                .evaluate(builder.build());
    }

    @Test
    void aDocumentBothAssertionsHoldOnCarriesNoFinding() {
        assertEquals(List.of(), findings("100"));
    }

    @Test
    void aDocumentOnlyTheFirstAssertionHoldsOnCarriesAWarning() {
        List<RuleFinding> findings = findings("150");

        assertEquals(1, findings.size(), findings.toString());
        assertEquals(RuleSeverity.WARNING, findings.get(0).severity());
        assertEquals("WARNING at /BG-22/BT-106", findings.get(0).message());
    }

    @Test
    void aDocumentTheFirstAssertionFailsOnCarriesTheFaultAndNotTheWarning() {
        List<RuleFinding> findings = findings("300");

        assertEquals(1, findings.size(), findings.toString());
        assertEquals(RuleSeverity.FATAL, findings.get(0).severity());
        assertEquals("FAULT at /BG-22/BT-106", findings.get(0).message());
    }

    @Test
    void aSecondAssertionWithoutAMessageIsRefused() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> read(withWarning("{\"assert\": {\"le\": [{\"value\": \"/BG-22/BT-106\"},"
                        + " {\"const\": 100}]}}")));

        assertTrue(refused.getMessage().contains("message"), refused.getMessage());
    }

    @Test
    void aSecondAssertionThatAssertsNothingIsRefused() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> read(withWarning("{\"message\": \"WARNING\"}")));

        assertTrue(refused.getMessage().contains("asserts nothing"), refused.getMessage());
    }

    @Test
    void aSecondAssertionWithAMemberOfItsOwnInventionIsRefused() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> read(withWarning("{\"assert\": {\"le\": [{\"value\": \"/BG-22/BT-106\"},"
                        + " {\"const\": 100}]}, \"message\": \"WARNING\","
                        + " \"severity\": \"fatal\"}")));

        assertTrue(refused.getMessage().contains("severity"), refused.getMessage());
    }

    @Test
    void aSecondAssertionThatIsNotATruthValueIsRefused() {
        RulePackException refused = assertThrows(RulePackException.class, () -> RuleEngine.compile(
                read(withWarning("{\"assert\": {\"value\": \"/BG-22/BT-106\"},"
                        + " \"message\": \"WARNING\"}")),
                Packs.REGISTRY, CodeLists.empty(), JavaRules.none()));

        assertTrue(refused.getMessage().contains("truth value"), refused.getMessage());
    }

    /** Returns the pack whose rule carries the second assertion written here. */
    private static String withWarning(String warning) {
        return TEMPLATE.replace("SECOND", warning);
    }
}
