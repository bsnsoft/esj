package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Rule packs the tests of this module write inline. */
final class Packs {

    static final Registry REGISTRY = Registry.en16931();

    private Packs() {
    }

    /**
     * Wraps rules in a pack file.
     *
     * @param rules the rules, as they stand in a file
     * @return the pack file
     */
    static String file(String... rules) {
        return "{\"id\": \"test\", \"version\": \"1\", \"edition\": \"EN 16931-1:2017+A1:2019/AC:2020\","
                + " \"verifiedAgainst\": \"nothing; this pack is a test fixture\","
                + " \"description\": \"A pack the tests write.\","
                + " \"rules\": [" + String.join(",", rules) + "]}";
    }

    /**
     * Writes one rule with the context of the document and one assertion.
     *
     * @param id        the rule identifier
     * @param assertion the assertion, as it stands in a file
     * @return the rule
     */
    static String rule(String id, String assertion) {
        return rule(id, "/", assertion);
    }

    /**
     * Writes one rule.
     *
     * @param id        the rule identifier
     * @param context   the context pattern
     * @param assertion the assertion, as it stands in a file
     * @return the rule
     */
    static String rule(String id, String context, String assertion) {
        return "{\"id\": \"" + id + "\", \"severity\": \"fatal\", \"oracle\": \"cases\", \"context\": \"" + context + "\","
                + " \"terms\": [\"BT-1\"], \"assert\": " + assertion + ","
                + " \"message\": \"" + id + " does not hold.\","
                + " \"source\": \"EN 16931-1, 6.4\"}";
    }

    /**
     * Reads a pack from a string.
     *
     * @param json the pack file
     * @return the pack
     */
    static RulePack read(String json) {
        try (InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
            return RulePacks.read(in, "a test pack");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Compiles a pack of one document rule and runs it.
     *
     * @param document  the document
     * @param assertion the assertion of the one rule
     * @return the findings
     */
    static List<RuleFinding> run(SemanticDocument document, String assertion) {
        return engine(file(rule("BR-TEST", assertion))).evaluate(document);
    }

    /**
     * Compiles a pack of one rule with a context and runs it.
     *
     * @param document  the document
     * @param context   the context pattern
     * @param assertion the assertion of the one rule
     * @return the findings
     */
    static List<RuleFinding> run(SemanticDocument document, String context, String assertion) {
        return engine(file(rule("BR-TEST", context, assertion))).evaluate(document);
    }

    /**
     * Compiles a pack file.
     *
     * @param json the pack file
     * @return the engine
     */
    static RuleEngine engine(String json) {
        return RuleEngine.compile(read(json), REGISTRY, CodeLists.empty(), JavaRules.none());
    }

    /**
     * Tells whether the one rule of a pack fired on a document.
     *
     * @param document  the document
     * @param assertion the assertion of the one rule
     * @return whether a finding was produced
     */
    static boolean fails(SemanticDocument document, String assertion) {
        return !run(document, assertion).isEmpty();
    }

    /**
     * Tells whether the one rule of a pack fired on a document, at a context.
     *
     * @param document  the document
     * @param context   the context pattern
     * @param assertion the assertion of the one rule
     * @return whether a finding was produced
     */
    static boolean fails(SemanticDocument document, String context, String assertion) {
        return !run(document, context, assertion).isEmpty();
    }

    /**
     * A currency list with the minor units the tests of the scale operators ask for.
     *
     * @return the snapshots, with {@code iso-4217} in them
     */
    static CodeLists currencies() {
        return CodeLists.of(List.of(new CodeList("iso-4217", "Currency codes", "a test fixture",
                "a test fixture", "2026-09-21",
                Map.of("EUR", "Euro", "JPY", "Yen"), Map.of("EUR", 2, "JPY", 0))));
    }

    /**
     * Tells whether the one rule of a pack fired on a document, with snapshots in hand.
     *
     * @param document  the document
     * @param assertion the assertion of the one rule
     * @param lists     the code list snapshots the rule may ask about
     * @return whether a finding was produced
     */
    static boolean fails(SemanticDocument document, String assertion, CodeLists lists) {
        return !RuleEngine.compile(read(file(rule("BR-TEST", assertion))), REGISTRY, lists,
                JavaRules.none()).evaluate(document).isEmpty();
    }
}
