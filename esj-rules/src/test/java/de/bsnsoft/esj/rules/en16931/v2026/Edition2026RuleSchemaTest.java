package de.bsnsoft.esj.rules.en16931.v2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import de.bsnsoft.esj.rules.RulePacks;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The pack of EN 16931-1:2026 against {@code rules/rule.schema.json}: its manifest and every
 * rule file the manifest names, as this build carries them.
 *
 * <p>The compiler of the engine is what decides whether a pack runs, and it accepts this one
 * ({@link Edition2026PackTest}). The schema is what a reader and an editor see, and the two
 * have to agree on the shape of every file; a member the language gained — {@code undecided},
 * {@code oracle}, {@code shares} — that the schema did not would otherwise be found by the
 * first person to open a file in an editor. The rule files the pack shares with the pack of
 * the 2017 edition are that pack's files and are checked with it.
 */
class Edition2026RuleSchemaTest {

    private static final String PACK = "packs/" + En16931V2026.PACK_ID + "/"
            + En16931V2026.VERSION + "/";

    private static Schema schema;

    @BeforeAll
    static void loadTheSchema() {
        try (InputStream in = Edition2026RuleSchemaTest.class
                .getResourceAsStream("/rules/rule.schema.json")) {
            assertNotNull(in, "the schema of the rule language is on the test class path");
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theManifestValidatesAgainstTheSchemaOfTheRuleLanguage() {
        assertEquals(List.of(), validate(resource("pack.json")));
    }

    /** The rule files of the pack, as its manifest names them. */
    static List<String> ruleFiles() {
        List<String> files = new ArrayList<>();
        try (JsonParser parser = new JsonFactory().createParser(resource("pack.json"))) {
            parser.nextToken();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String member = parser.currentName();
                parser.nextToken();
                if (!"files".equals(member)) {
                    parser.skipChildren();
                    continue;
                }
                while (parser.nextToken() == JsonToken.VALUE_STRING) {
                    files.add(parser.getText());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertFalse(files.isEmpty(), "the manifest names no rule file");
        return files;
    }

    @ParameterizedTest
    @MethodSource("ruleFiles")
    void aRuleFileOfThePackValidatesAgainstTheSchemaOfTheRuleLanguage(String file) {
        assertEquals(List.of(), validate(resource(file)), file);
    }

    private static List<Error> validate(String json) {
        return schema.validate(json, InputFormat.JSON);
    }

    private static String resource(String name) {
        try (InputStream in = RulePacks.class.getResourceAsStream(PACK + name)) {
            assertNotNull(in, "this build carries no " + PACK + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
