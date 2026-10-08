package de.bsnsoft.esj.json;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks {@code schema/esj.schema.json}, the machine-readable form of most of validation
 * layer L1 (specification, section 9.1), with a JSON Schema 2020-12 validator.
 */
class SchemaTest {

    /**
     * The negative fixtures the format schema alone rejects. The others carry a defect the
     * schema cannot see: a layer L2 or L3 defect, a business rule, or one of the L1 checks
     * the specification, section 9.1 lists as reader-only. The schema constrains the shape
     * of a value and nothing about its content, because it knows no business terms
     * (section 6.2), so no content fixture is in this list. The table is the one in
     * {@code examples/invalid/README.md}.
     *
     * <p>{@code value-object-members-17} is in the list although the reader refuses it as
     * a limit: a value object with seventeen members carries members the schema does not
     * define, and the schema sees that rather than the count. {@code value-depth-33} is there
     * for the same kind of reason: the schema sees an array where a string belongs, and the
     * reader the nesting bound it would have to walk past.
     */
    private static final List<String> REJECTED_BY_THE_SCHEMA = List.of(
            "duplicate-and-surrogate-in-value-object",
            "duplicate-below-bad-owner-token",
            "duplicate-path-of-value-objects",
            "empty-extensions",
            "empty-string-value",
            "empty-string-with-missing-term",
            "envelope-member-terminal-characters",
            "envelope-missing-values",
            "envelope-wrong-version",
            "number-instead-of-string",
            "object-without-component",
            "owner-token-syntax",
            "owner-token-terminal-characters",
            "path-leading-zero-index",
            "path-syntax-terminal-characters",
            "path-syntax-with-array-value",
            "scheme-version-without-scheme",
            "semantic-model-grammar",
            "source-empty-syntax",
            "source-sha256-uppercase",
            "source-unknown-member",
            "surrogate-and-duplicate-in-value-object",
            "surrogate-below-bad-owner-token",
            "surrogate-in-envelope-member-name",
            "surrogate-in-path",
            "surrogate-in-shapeless-value-object",
            "surrogate-in-source-member-name",
            "unknown-envelope-member",
            "unknown-object-member",
            "value-depth-32",
            "value-depth-33",
            "value-not-a-string",
            "value-object-members-17",
            "values-deep-array");

    private static Schema schema;

    @BeforeAll
    static void loadTheSchema() {
        try (InputStream in = Examples.schema()) {
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> examples() {
        return Examples.NAMES;
    }

    static List<String> invalidFixtures() {
        return Examples.invalidNames();
    }

    private static List<Error> validate(byte[] document) {
        return schema.validate(new String(document, StandardCharsets.UTF_8), InputFormat.JSON);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void everyExampleValidatesAgainstTheFormatSchema(String name) {
        assertEquals(List.of(), validate(Examples.pretty(name)), name);
        assertEquals(List.of(), validate(Examples.canonical(name)), name);
    }

    @ParameterizedTest
    @MethodSource("invalidFixtures")
    void aFixtureIsRejectedByTheSchemaExactlyWhereTheFixtureTableSaysSo(String name) {
        if (Examples.NOT_A_JSON_TEXT.contains(name)) {
            return;
        }
        boolean rejected = !validate(Examples.invalid(name)).isEmpty();
        if (REJECTED_BY_THE_SCHEMA.contains(name)) {
            assertTrue(rejected, name + " is expected to fail the schema");
        } else {
            assertFalse(rejected, name + " carries a defect the schema cannot see");
        }
    }

    /**
     * {@code semanticModel} is a string. A pattern constrains a string and says nothing
     * about a value of another JSON type, so without the type a number, {@code null}, an
     * array or an object passed the schema although a reader refuses each of them as
     * {@code ESJ-L1-ENVELOPE-VALUE} (specification, sections 4.1 and 9.1).
     */
    @ParameterizedTest
    @ValueSource(strings = {"5", "null", "[]", "{}", "true"})
    void aSemanticModelThatIsNoStringFailsTheSchema(String semanticModel) {
        assertFalse(validate(document(semanticModel, "{\"/BT-1\":\"x\"}", "")).isEmpty(),
                semanticModel);
    }

    /**
     * A path, an edition, an owner token or a digest followed by LF is not one, and the
     * schema refuses each of them (specification, section 5.1). The validator here reads
     * a pattern as ECMA-262 does; the next test is about the engines that do not.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "\"EN16931-1:2017+A1:2019/AC:2020\"|{\"/BT-1\\n\":\"x\"}|",
        "\"EN16931-1:2017+A1:2019/AC:2020\\n\"|{\"/BT-1\":\"x\"}|",
        "\"EN16931-1:2017+A1:2019/AC:2020\"|{\"/BT-1\":\"x\"}|,\"extensions\":{\"de.example\\n\":1}",
        "\"EN16931-1:2017+A1:2019/AC:2020\"|{\"/BT-1\":\"x\"}|,\"source\":{\"sha256\":\""
                + "0000000000000000000000000000000000000000000000000000000000000000\\n\"}"})
    void aStringOfAGrammarFollowedByALineFeedFailsTheSchema(String parts) {
        String[] part = parts.split("\\|", -1);
        assertFalse(validate(document(part[0], part[1], part[2])).isEmpty(), parts);
        String withoutTheLineFeed = parts.replace("\\n", "");
        String[] clean = withoutTheLineFeed.split("\\|", -1);
        assertEquals(List.of(), validate(document(clean[0], clean[1], clean[2])),
                "the same document without the line feed passes");
    }

    /**
     * Every pattern of the schema ends in {@code (?![\s\S])} — no character follows — and
     * none in {@code $}. java.util.regex, Python's {@code re} and other engines a JSON Schema
     * validator may use in place of ECMA-262 also match {@code $} before a final line feed, so
     * under them a pattern ending in {@code $} let a path followed by LF through
     * (specification, section 5.1). The path pattern shows both.
     */
    @Test
    void everyPatternEndsWhereTheStringEndsUnderEveryEngine() throws IOException {
        String text;
        try (InputStream in = Examples.schema()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher member = Pattern.compile("\"pattern\": \"((?:[^\"\\\\]|\\\\.)*)\"").matcher(text);
        List<String> patterns = new ArrayList<>();
        while (member.find()) {
            patterns.add(member.group(1).replace("\\\\", "\\"));
        }
        assertEquals(4, patterns.size(), "semanticModel, sha256, a path and an owner token");
        for (String pattern : patterns) {
            assertTrue(pattern.endsWith("(?![\\s\\S])"), pattern);
            assertFalse(pattern.contains("$"), pattern);
        }
        String path = patterns.stream().filter(p -> p.contains("/BT-")).findFirst().orElseThrow();
        assertTrue(Pattern.compile(path).matcher("/BT-1").find());
        assertFalse(Pattern.compile(path).matcher("/BT-1\n").find());
        assertTrue(Pattern.compile(path.replace("(?![\\s\\S])", "$")).matcher("/BT-1\n").find(),
                "the same pattern ending in $ accepts the line feed under java.util.regex");
    }

    /**
     * The limit on a string of {@code values} is measured after CR LF has become LF
     * (specification, section 6.8), and the schema counts the string as it is written. A
     * string a reader running the defaults accepts — 1 048 575 characters and a CR LF, one
     * mebibyte once normalized — therefore passes the schema, and the guard refuses only
     * past twice the limit.
     */
    @Test
    void theStringGuardAdmitsWhatTheNormalizedLimitAdmits() {
        String atTheLimit = "a".repeat(1_048_575) + "\\r\\n";
        assertEquals(List.of(),
                validate(document("\"EN16931-1:2017+A1:2019/AC:2020\"",
                        "{\"/BG-4/BT-27\":\"" + atTheLimit + "\"}", "")));
        String pastTheGuard = "a".repeat(2_097_153);
        assertFalse(validate(document("\"EN16931-1:2017+A1:2019/AC:2020\"",
                "{\"/BG-4/BT-27\":\"" + pastTheGuard + "\"}", "")).isEmpty());
    }

    private static byte[] document(String semanticModel, String values, String rest) {
        return ("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\",\"semanticModel\":"
                + semanticModel + ",\"values\":" + values + rest + "}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void whatTheWriterProducesValidatesAgainstTheFormatSchema(String name) {
        EsjReader reader = EsjReader.strict();
        assertEquals(List.of(),
                validate(EsjWriter.pretty().toBytes(reader.read(Examples.pretty(name)))), name);
        assertEquals(List.of(),
                validate(Canonicalizer.canonicalize(Examples.pretty(name))), name);
    }
}
