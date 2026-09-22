package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks {@code schema/render-template.schema.json} against the templates of the
 * repository, with a JSON Schema 2020-12 validator.
 *
 * <p>The schema is published and {@code docs/templates.md} sends a template author to it
 * as the shape of the file. Nothing else in the build reads it, so without this it could
 * describe a format this module no longer accepts: the two other files under
 * {@code schema/} are checked the same way by {@code esj-core} and {@code esj-typed}.
 *
 * <p>What is checked is both directions of the one thing a published schema has to get
 * right: every example template validates against it, and every one of them is a template
 * {@link RenderTemplate} reads. On the shape of the file the schema is the stricter of the
 * two — it names every member and allows no other, where the reader takes the members it
 * knows — so a member this module gains without the schema gaining it fails here. On the
 * values of a member the reader may be the stricter one, and where it is, the two numbers
 * are asserted to be one number rather than two that were written to agree.
 */
class RenderTemplateSchemaTest {

    /** Where the schema sits on the test classpath. */
    private static final String SCHEMA = "/schema/render-template.schema.json";

    private static Schema schema;

    @BeforeAll
    static void loadTheSchema() {
        try (InputStream in = RenderTemplateSchemaTest.class.getResourceAsStream(SCHEMA)) {
            assertNotNull(in, "the render template schema is on the test classpath");
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> templates() {
        List<String> templates = new ArrayList<>();
        try (var files = Files.list(Templates.directory())) {
            files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .sorted()
                    .forEach(templates::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertFalse(templates.isEmpty(), "the repository carries example templates");
        return templates;
    }

    @ParameterizedTest
    @MethodSource("templates")
    void everyExampleTemplateValidatesAgainstTheSchema(String name) {
        assertEquals(List.of(), validate(read(name)), name);
    }

    @ParameterizedTest
    @MethodSource("templates")
    void andIsATemplateThisModuleReads(String name) {
        assertNotNull(Templates.example(name).name(), name);
    }

    /** A member the schema does not name is a member the schema refuses. */
    @Test
    void aMemberTheSchemaDoesNotNameIsRefusedByIt() {
        assertFalse(validate("{\"template\": \"esj-render-template/0.1\","
                        + " \"watermark\": \"x\"}").isEmpty(),
                "the schema allows no member it does not name");
    }

    /**
     * The bottom margin is bounded in both, and by the same number. The footer of a page
     * sits inside the bottom margin, so {@link Margins} refuses a margin that would write
     * it onto the bottom edge of the paper; a schema that let such a template through
     * would send a template author to a reader that then refuses it.
     */
    @Test
    void theBottomMarginIsBoundedByTheSameNumberInBoth() {
        String tooLittle = withBottomMargin(Margins.MINIMUM_BOTTOM - 1);
        assertFalse(validate(tooLittle).isEmpty(),
                "the schema refuses the bottom margin the reader refuses");
        assertThrows(TemplateException.class, () -> readTemplate(tooLittle));

        String theLimit = withBottomMargin(Margins.MINIMUM_BOTTOM);
        assertEquals(List.of(), validate(theLimit),
                "the schema allows the smallest bottom margin the reader allows");
        assertEquals(Margins.MINIMUM_BOTTOM, readTemplate(theLimit).marginsFirst().bottom());
    }

    /** Returns the smallest template that states a bottom margin on both page kinds. */
    private static String withBottomMargin(float bottom) {
        return "{\"template\": \"esj-render-template/0.1\", \"margins\":"
                + " {\"first\": {\"bottom\": " + bottom + "},"
                + " \"following\": {\"bottom\": " + bottom + "}}}";
    }

    /** Reads a template that names no file. */
    private static RenderTemplate readTemplate(String template) {
        return RenderTemplate.of(template.getBytes(StandardCharsets.UTF_8), reference -> {
            throw new AssertionError("this template names no file: " + reference);
        });
    }

    private static String read(String name) {
        try {
            return Files.readString(Templates.directory().resolve(name),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Error> validate(String template) {
        return schema.validate(template, InputFormat.JSON);
    }
}
