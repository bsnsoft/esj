package de.bsnsoft.esj.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the claim of {@code README.md} and {@code model/README.md} that every registry
 * file validates against {@code model/registry.schema.json}. The registry of the 2026
 * edition is checked by {@code Edition2026Test}, because a build may leave that edition
 * out.
 */
class RegistrySchemaTest {

    private static Schema schema;

    @BeforeAll
    static void loadTheSchema() {
        schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(text("/model/registry.schema.json"), InputFormat.JSON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en16931/2017.json", "xrechnung/3.0.2.json", "b2c/0.1.json"})
    void aRegistryFileValidatesAgainstTheRegistrySchema(String name) {
        assertEquals(List.of(), schema.validate(text("/model/" + name), InputFormat.JSON), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en16931/2017.json", "xrechnung/3.0.2.json", "b2c/0.1.json"})
    void aRegistryFileThatLosesARequiredMemberFailsTheSchema(String name) {
        String broken = text("/model/" + name).replaceFirst("\"kind\": \"B[TG]\",", "");
        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                name + " without the kind of its first term is expected to fail the schema");
    }

    @Test
    void aCodeListNamedOnAComponentThatIsNoSchemeFailsTheSchema() {
        String broken = text("/model/en16931/2017.json").replaceFirst(
                "\"role\": \"schemeVersion\",",
                "\"role\": \"schemeVersion\", \"schemeList\": \"ISO 6523 ICD\",");
        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "only an identification scheme is taken from a code list");
    }

    @Test
    void aComponentThatTheSemanticDataTypeDoesNotHaveFailsTheSchema() {
        String broken = withFirstTermComponents(
                "{\"id\": \"BT-1-1\", \"role\": \"mimeCode\", \"name\": \"x\","
                        + " \"min\": 1, \"max\": 1}");
        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "an identifier has no media type component");
    }

    @Test
    void aBinaryObjectThatLosesOneOfItsTwoComponentsFailsTheSchema() {
        String broken = text("/model/en16931/2017.json").replaceFirst(
                ",\\s*\\{\\s*\"id\": \"BT-125-2\",[^}]*\\}", "");
        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "a binary object carries both mimeCode and filename");
    }

    @Test
    void aBinaryObjectComponentThatIsOptionalFailsTheSchema() {
        String broken = text("/model/en16931/2017.json").replaceFirst(
                "\"name\": \"Attached document Mime code\",\\s*\"min\": 1",
                "\"name\": \"Attached document Mime code\", \"min\": 0");
        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "both components of a binary object are mandatory");
    }

    @Test
    void aSchemeVersionWithoutASchemeFailsTheSchema() {
        String broken = withFirstTermComponents(
                "{\"id\": \"BT-1-1\", \"role\": \"schemeVersion\", \"name\": \"x\","
                        + " \"min\": 0, \"max\": 1}");
        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "a scheme version stands only beside a scheme");
    }

    @Test
    void theSameComponentRoleTwiceFailsTheSchema() {
        String broken = withFirstTermComponents(
                "{\"id\": \"BT-1-1\", \"role\": \"scheme\", \"name\": \"x\","
                        + " \"min\": 0, \"max\": 1},"
                        + "{\"id\": \"BT-1-2\", \"role\": \"scheme\", \"name\": \"y\","
                        + " \"min\": 0, \"max\": 1}");
        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "a value object carries one member per role");
    }

    /**
     * A registry that declares its terms untransported says why: the note is what a reader
     * of the file and of a validation report has to go on, and nothing else states it.
     */
    @Test
    void aTransportDeclarationWithoutItsNoteFailsTheSchema() {
        String broken = text("/model/b2c/0.1.json")
                .replaceFirst("\n  \"transportNote\": \"[^\"]*\",", "");

        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "transportNote is required beside transport");
    }

    /** The member says that the terms do not travel, and has no other value today. */
    @Test
    void aTransportMemberWithAnotherValueFailsTheSchema() {
        String broken = text("/model/b2c/0.1.json")
                .replace("\"transport\": \"none\"", "\"transport\": \"ubl\"");

        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "the only value of transport is none");
    }

    /** A core registry is what a syntax binds, and states nothing about transport. */
    @Test
    void aRegistryOfAModelOfItsOwnDoesNotDeclareItsTermsUntransported() {
        String broken = text("/model/en16931/2017.json").replaceFirst(
                "\"license\":", "\"transport\": \"none\", \"transportNote\": \"x\", \"license\":");

        assertFalse(schema.validate(broken, InputFormat.JSON).isEmpty(),
                "a registry without imports declares no such thing");
    }

    /**
     * Returns the core registry with the components of its first term, BT-1, replaced.
     * BT-1 is an identifier, so it may carry a scheme and a scheme version and nothing
     * else.
     */
    private static String withFirstTermComponents(String components) {
        return text("/model/en16931/2017.json")
                .replaceFirst("\"components\": \\[\\]", "\"components\": [" + components + "]");
    }

    private static String text(String resource) {
        try (InputStream in = RegistrySchemaTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " is not on the test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
