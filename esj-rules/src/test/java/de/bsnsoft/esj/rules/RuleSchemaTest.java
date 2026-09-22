package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The rule language has two descriptions that have to agree: {@code rules/rule.schema.json},
 * which a reader and a text editor use, and the compiler of this module, which is what
 * actually decides. This test puts them beside each other.
 *
 * <p>The two are not the same instrument and are not expected to be. The schema sees the shape
 * of a file; the compiler sees the shape and the registry. So a malformed rule has to be
 * refused by both, and a rule that is well formed but names a business term nobody has is
 * refused by the compiler alone — which is the division of labour the schema's own description
 * states, checked here rather than asserted there.
 */
class RuleSchemaTest {

    private static Schema schema;

    @BeforeAll
    static void loadTheSchema() {
        try (InputStream in = RuleSchemaTest.class.getResourceAsStream("/rules/rule.schema.json")) {
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Error> validate(String json) {
        return schema.validate(json, InputFormat.JSON);
    }

    private static boolean compiles(String json) {
        try {
            Packs.engine(json);
            return true;
        } catch (RulePackException refused) {
            return false;
        }
    }

    /** Rule files that are a rule file: both descriptions accept them. */
    static List<String> wellFormed() {
        return List.of(
                Packs.file(Packs.rule("BR-CO-10",
                        "{\"eq\": [{\"value\": \"/BG-22/BT-106\"}, {\"sum\": \"/BG-25/*/BT-131\"}]}")),
                Packs.file(Packs.rule("BR-CO-25",
                        "{\"if\": {\"condition\": {\"gt\": [{\"value\": \"/BG-22/BT-115\"},"
                                + " {\"const\": \"0\"}]},"
                                + " \"then\": {\"or\": [{\"exists\": \"/BT-9\"}, {\"exists\": \"/BT-20\"}]}}}")),
                Packs.file(Packs.rule("BR-CO-17", "/BG-23/*",
                        "{\"eq\": [{\"value\": \"/BT-117\"}, {\"round\": [{\"div\": [{\"mul\":"
                                + " [{\"value\": \"/BT-116\"}, {\"value\": \"/BT-119\"}]},"
                                + " {\"const\": 100}]}, 2]}]}")),
                Packs.file(Packs.rule("BR-DEC-12", "{\"decimals\": [\"/BG-22/BT-106\", 2]}")),
                Packs.file(Packs.rule("BR-TEST",
                        "{\"forEach\": {\"group\": \"/BG-25/*\", \"assert\":"
                                + " {\"all\": {\"group\": \"/BG-27/*\","
                                + " \"assert\": {\"ge\": [{\"value\": \"/BT-136\"}, {\"const\": \"0\"}]}}}}}")),
                Packs.file(Packs.rule("BR-TEST",
                        "{\"and\": [{\"not\": {\"absent\": \"/BT-1\"}},"
                                + " {\"matches\": [{\"value\": \"/BT-1\"}, \"RE-.+\"]},"
                                + " {\"lt\": [{\"len\": {\"value\": \"/BT-1\"}}, {\"const\": 64}]},"
                                + " {\"ne\": [{\"count\": \"/BG-25/*\"}, {\"const\": 0}]},"
                                + " {\"le\": [{\"min\": \"/BG-25/*/BT-131\"},"
                                + " {\"max\": \"/BG-25/*/BT-131\"}]},"
                                + " {\"ge\": [{\"abs\": {\"sub\": [{\"value\": \"/BG-22/BT-112\"},"
                                + " {\"value\": \"/BG-22/BT-109\"}]}}, {\"const\": \"0\"}]},"
                                + " {\"any\": {\"group\": \"/BG-23/*\","
                                + " \"assert\": {\"exists\": \"/BT-118\"}}}]}")));
    }

    /** Rule files that are not: both descriptions refuse them. */
    static List<String> malformed() {
        return List.of(
                Packs.file(Packs.rule("BR-TEST", "{\"between\": [{\"const\": 1}, {\"const\": 2}]}")),
                Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BT-1\", \"absent\": \"/BT-9\"}")),
                Packs.file(Packs.rule("BR-TEST", "{\"eq\": [{\"value\": \"/BT-1\"}]}")),
                Packs.file(Packs.rule("BR-TEST", "{\"sub\": [{\"const\": 1}, {\"const\": 2}, {\"const\": 3}]}")),
                Packs.file(Packs.rule("BR-TEST", "{\"round\": [{\"value\": \"/BG-22/BT-106\"}]}")),
                Packs.file(Packs.rule("BR-TEST", "{\"round\": [{\"value\": \"/BG-22/BT-106\"}, 99]}")),
                Packs.file(Packs.rule("BR-TEST", "{\"if\": {\"condition\": {\"exists\": \"/BT-1\"}}}")),
                Packs.file(Packs.rule("BR-TEST", "{\"forEach\": {\"group\": \"/BG-25/*\"}}")),
                Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"BT-1\"}")),
                Packs.file(Packs.rule("BR-TEST", "{\"value\": \"/BG-22/BT-106\"}")
                        .replace("\"severity\": \"fatal\"", "\"severity\": \"maybe\"")),
                Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BT-1\"}")
                        .replace("\"source\":", "\"reference\":")),
                Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BT-1\"}")
                        .replace(", \"terms\": [\"BT-1\"]", "")),
                Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BT-1\"}")
                        .replace("\"id\": \"BR-TEST\"", "\"id\": \"br-test\"")));
    }

    /**
     * Rule files the schema cannot fault, because the defect is only visible with the
     * registry in hand: an unknown term, an impossible nesting, an asterisk on the wrong
     * step, a type mismatch, or a code list with no snapshot.
     */
    static List<String> wellFormedButUncompilable() {
        return List.of(
                Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BT-9999\"}")),
                Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BG-22/BT-131\"}")),
                Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BG-25/BT-131\"}")),
                Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BG-22/*/BT-106\"}")),
                Packs.file(Packs.rule("BR-TEST",
                        "{\"eq\": [{\"value\": \"/BT-2\"}, {\"value\": \"/BT-5\"}]}")),
                Packs.file(Packs.rule("BR-TEST", "{\"decimals\": [\"/BT-5\", 2]}")),
                Packs.file(Packs.rule("BR-TEST",
                        "{\"inList\": [{\"value\": \"/BT-5\"}, \"iso-4217\"]}")),
                Packs.file(Packs.rule("BR-TEST", "{\"value\": \"/BG-22/BT-106\"}")),
                Packs.file(Packs.rule("BR-TEST", "{\"exists\": \"/BT-1\"}")
                        .replace("does not hold.", "{/BT-9999}")));
    }

    @ParameterizedTest
    @MethodSource("wellFormed")
    void aRuleFileIsAcceptedByTheSchemaAndByTheCompiler(String json) {
        assertEquals(List.of(), validate(json), json);
        assertTrue(compiles(json), json);
    }

    @ParameterizedTest
    @MethodSource("malformed")
    void aMalformedRuleIsRejectedByTheSchemaAndByTheCompiler(String json) {
        assertFalse(validate(json).isEmpty(), "the schema accepted " + json);
        assertFalse(compiles(json), "the compiler accepted " + json);
    }

    @ParameterizedTest
    @MethodSource("wellFormedButUncompilable")
    void aDefectOnlyTheRegistryCanSeeIsTheCompilersToFind(String json) {
        assertEquals(List.of(), validate(json), "the schema faulted the shape of " + json);
        assertFalse(compiles(json), "the compiler accepted " + json);
    }

    @Test
    void theRuleDirectoryOfTheRepositoryValidatesAgainstItsOwnSchema() {
        assertEquals(List.of(), validate(BundledPackTest.bundledPackFile()));
    }

    /** The rule files of the bundled pack, as its manifest names them. */
    static List<String> bundledRuleFiles() {
        return List.of("br.json", "br-cl.json", "br-co.json", "br-dec.json", "vat-ae.json",
                "vat-e.json", "vat-g.json", "vat-ic.json", "vat-ig.json", "vat-ip.json",
                "vat-o.json", "vat-s.json", "vat-z.json");
    }

    @ParameterizedTest
    @MethodSource("bundledRuleFiles")
    void aRuleFileOfTheRepositoryValidatesAgainstItsOwnSchema(String name) {
        String resource = "packs/en16931/1.3.16/rules/" + name;
        try (InputStream in = RulePacks.class.getResourceAsStream(resource)) {
            assertTrue(in != null, "this build carries no " + resource);
            assertEquals(List.of(),
                    validate(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)),
                    resource);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
