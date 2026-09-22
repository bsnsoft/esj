package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The promise of {@link JavaRule}: a rule written in the rule language and the same rule
 * written in Java produce the same finding, and a report cannot tell them apart.
 */
class JavaRuleTest {

    private static final String LANGUAGE_PACK = """
            {"id": "test", "version": "1",
             "verifiedAgainst": "nothing; this pack is a test fixture",
             "description": "BR-CO-10 in the rule language.",
             "rules": [
               {"id": "BR-CO-10", "severity": "fatal", "context": "/",
                "terms": ["BT-106", "BT-131"],
                "assert": {"eq": [{"value": "/BG-22/BT-106"}, {"sum": "/BG-25/*/BT-131"}]},
                "bind": {"never": {"sum": "/BG-25/*/BT-131"}},
                "message": "MESSAGE",
                "source": "EN 16931-1, 6.4.2, BR-CO-10"}]}
            """.replace("MESSAGE", SumOfLineNetAmounts.MESSAGE);

    private static final String JAVA_PACK = """
            {"id": "test", "version": "1",
             "verifiedAgainst": "nothing; this pack is a test fixture",
             "description": "BR-CO-10 in Java.",
             "javaRules": ["de.bsnsoft.esj.rules.SumOfLineNetAmounts"],
             "rules": []}
            """;

    private static SemanticDocument mismatch() {
        SemanticDocument.Builder builder = Documents.minimal();
        Documents.line(builder, 1, "1", "50");
        return builder.build();
    }

    private static RulePack read(String json) {
        try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return RulePacks.read(in, "a test pack");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theSameRuleWrittenBothWaysProducesTheSameFinding() {
        SemanticDocument document = mismatch();
        Registry registry = Packs.REGISTRY;

        List<RuleFinding> language = RuleEngine
                .compile(read(LANGUAGE_PACK), registry, CodeLists.empty(), JavaRules.none())
                .evaluate(document);
        List<RuleFinding> java = RuleEngine
                .compile(read(JAVA_PACK), registry, CodeLists.empty(),
                        JavaRules.of(new SumOfLineNetAmounts()))
                .evaluate(document);

        assertEquals(1, language.size());
        assertEquals(language, java);
    }

    @Test
    void bothWaysAgreeThatASoundInvoiceIsSound() {
        SemanticDocument document = Documents.minimal().build();
        Registry registry = Packs.REGISTRY;

        assertEquals(List.of(), RuleEngine
                .compile(read(LANGUAGE_PACK), registry, CodeLists.empty(), JavaRules.none())
                .evaluate(document));
        assertEquals(List.of(), RuleEngine
                .compile(read(JAVA_PACK), registry, CodeLists.empty(),
                        JavaRules.of(new SumOfLineNetAmounts()))
                .evaluate(document));
    }

    @Test
    void aPackThatNamesAJavaRuleNobodyHandedOverDoesNotCompile() {
        RulePackException refused = assertThrows(RulePackException.class, () -> RuleEngine
                .compile(read(JAVA_PACK), Packs.REGISTRY, CodeLists.empty(), JavaRules.none()));

        assertTrue(refused.getMessage().contains("missing"), refused.getMessage());
    }

    @Test
    void aRuleNobodyDeclaredDoesNotCompileEither() {
        RulePackException refused = assertThrows(RulePackException.class, () -> RuleEngine
                .compile(read(LANGUAGE_PACK), Packs.REGISTRY, CodeLists.empty(),
                        JavaRules.of(new SumOfLineNetAmounts())));

        assertTrue(refused.getMessage().contains("not declared"), refused.getMessage());
    }

    @Test
    void aJavaRuleDeclaresTheTermsACoverageTableIsBuiltFrom() {
        RuleEngine engine = RuleEngine.compile(read(JAVA_PACK), Packs.REGISTRY, CodeLists.empty(),
                JavaRules.of(new SumOfLineNetAmounts()));

        assertEquals(List.of("BR-CO-10"), engine.ruleIds());
        assertEquals(List.of("BT-106", "BT-131"), engine.termsOf("BR-CO-10").orElseThrow());
        assertEquals("EN 16931-1, 6.4.2, BR-CO-10", engine.sourceOf("BR-CO-10").orElseThrow());
    }

    @Test
    void aPathAJavaRuleInventedFailsAsLoudlyAsOneInAFile() {
        JavaRule invented = new JavaRule() {
            @Override
            public String id() {
                return "BR-TEST";
            }

            @Override
            public RuleSeverity severity() {
                return RuleSeverity.FATAL;
            }

            @Override
            public String context() {
                return "/";
            }

            @Override
            public List<String> terms() {
                return List.of("BT-1");
            }

            @Override
            public String source() {
                return "EN 16931-1, 6.4";
            }

            @Override
            public java.util.Optional<String> check(RuleContext context) {
                context.decimal("/BT-9999");
                return java.util.Optional.empty();
            }
        };
        String pack = "{\"id\": \"test\", \"version\": \"1\","
                + " \"verifiedAgainst\": \"nothing\", \"description\": \"x\","
                + " \"javaRules\": [\"" + invented.getClass().getName() + "\"], \"rules\": []}";
        RuleEngine engine = RuleEngine.compile(read(pack), Packs.REGISTRY, CodeLists.empty(),
                JavaRules.of(invented));

        RulePackException refused = assertThrows(RulePackException.class,
                () -> engine.evaluate(Documents.minimal().build()));

        assertTrue(refused.getMessage().contains("BT-9999"));
    }
}
