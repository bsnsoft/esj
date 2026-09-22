package de.bsnsoft.esj.typed;

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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks {@code schema/esj-en16931-2026.schema.json}: the generated schema of the second
 * edition names that edition's terms, holds a time to the grammar of the specification,
 * section 6.5, and refuses a document of the other edition.
 *
 * <p>Every test is skipped where the build carries no schema of that edition. The files of
 * the 2026 edition are separable, and the Maven profile {@code without-edition-2026}
 * leaves them out of the build.
 */
class Edition2026SchemaTest {

    /** The worked example of the specification, Appendix B, under the 2026 edition. */
    private static final String APPENDIX_B_2026 = """
            {
              "format": "EN16931-Semantic-JSON",
              "version": "0.1",
              "semanticModel": "EN16931-1:2026",
              "values": {
                "/BT-1": "RE-2026-0001",
                "/BT-2": "2026-01-15",
                "/BT-166": "%s",
                "/BG-4/BT-29/0": { "value": "0088123456785", "scheme": "0088" },
                "/BG-25/0/BT-131": "100"
              }
            }
            """;

    private static final String FILE = "esj-en16931-2026.schema.json";

    private static Schema schema;

    static boolean carriesTheSchema() {
        return Edition2026SchemaTest.class.getResource("/schema/" + FILE) != null;
    }

    @BeforeAll
    static void loadTheSchema() {
        if (!carriesTheSchema()) {
            return;
        }
        String format = Examples.schema("esj.schema.json");
        schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                        builder -> builder.schemas(
                                Map.of(Examples.schemaIdentifier(format), format)))
                .getSchema(Examples.schema(FILE), InputFormat.JSON);
    }

    private static List<Error> validate(String document) {
        return schema.validate(document, InputFormat.JSON);
    }

    private static String withTime(String time) {
        return APPENDIX_B_2026.formatted(time);
    }

    /**
     * Reads a document of {@code examples/} from the test classpath.
     *
     * @param name the file name below {@code examples/}
     * @return the document, decoded as UTF-8
     */
    private static String example(String name) {
        try (InputStream in = Edition2026SchemaTest.class
                .getResourceAsStream("/examples/" + name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @EnabledIf("carriesTheSchema")
    void theWorkedExampleOfTheSecondEditionValidates() {
        assertEquals(List.of(), validate(withTime("09:30:00+02:00")));
        assertEquals(List.of(), validate(withTime("06:15:02Z")));
        assertEquals(List.of(), validate(withTime("23:59:59-05:30")));
    }

    /**
     * The spellings the time grammar of the specification, section 6.5 leaves out: a time
     * without an offset, the numeric spelling of the zero offset, fractional seconds, the
     * hour 24, a leap second and an offset beyond 14 hours.
     */
    @ParameterizedTest
    @EnabledIf("carriesTheSchema")
    @ValueSource(strings = {"09:30:00", "09:30:00+00:00", "09:30:00-00:00", "09:30:00.500Z",
        "24:00:00Z", "09:30:60Z", "09:30:00+15:00", "09:30:00+14:30", "9:30:00Z", "09:30Z",
        "09:30:00 Z"})
    void aTimeOutsideTheGrammarIsRefused(String time) {
        assertFalse(validate(withTime(time)).isEmpty(), time);
    }

    @Test
    @EnabledIf("carriesTheSchema")
    void theSchemaOfOneEditionRefusesADocumentOfTheOther() {
        String document = withTime("09:30:00+02:00")
                .replace("EN16931-1:2026", "EN16931-1:2017+A1:2019/AC:2020");

        assertFalse(validate(document).isEmpty(),
                "a generated schema pins the edition it was generated for");
    }

    /**
     * The example of the repository and the negative fixture beside it, measured against
     * the schema of their own edition: the example passes, and the fixture whose invoice
     * issue time carries no offset does not.
     */
    @Test
    @EnabledIf("carriesTheSchema")
    void theExampleOfThisEditionValidatesAndItsNegativeFixtureDoesNot() {
        assertEquals(List.of(), validate(example("edition-2026.esj.json")));
        assertEquals(List.of(), validate(example("edition-2026.canonical.esj.json")));
        assertFalse(validate(example("invalid/edition-2026-time-without-offset.esj.json")).isEmpty(),
                "a time without an offset is outside the grammar of section 6.5");
    }

    @Test
    @EnabledIf("carriesTheSchema")
    void theSchemaNamesTheTermsOfItsOwnEdition() {
        String model = Examples.schema(FILE);

        assertTrue(model.contains("\"^/BT-166$\""), "the term of the new semantic data type Time");
        assertTrue(model.contains("\"^/BG-33/(?:0|[1-9][0-9]*)/BT-20$\""),
                "a term the 2026 edition places inside a group");
        assertFalse(model.contains("\"^/BT-20$\""),
                "and no longer at the root, where the 2017 edition has it");
        assertTrue(model.contains("\"const\": \"EN16931-1:2026\""), "it pins its own edition");
    }
}
