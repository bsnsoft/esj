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
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
     * define, and the schema sees that rather than the count.
     */
    private static final List<String> REJECTED_BY_THE_SCHEMA = List.of(
            "duplicate-and-surrogate-in-value-object",
            "duplicate-below-bad-owner-token",
            "duplicate-path-of-value-objects",
            "empty-extensions",
            "empty-string-value",
            "empty-string-with-missing-term",
            "number-instead-of-string",
            "object-without-component",
            "owner-token-syntax",
            "path-leading-zero-index",
            "scheme-version-without-scheme",
            "source-empty-syntax",
            "source-sha256-uppercase",
            "surrogate-and-duplicate-in-value-object",
            "surrogate-below-bad-owner-token",
            "surrogate-in-envelope-member-name",
            "surrogate-in-path",
            "surrogate-in-shapeless-value-object",
            "surrogate-in-source-member-name",
            "unknown-envelope-member",
            "unknown-object-member",
            "value-object-members-17",
            "value-not-a-string");

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
        boolean rejected = !validate(Examples.invalid(name)).isEmpty();
        if (REJECTED_BY_THE_SCHEMA.contains(name)) {
            assertTrue(rejected, name + " is expected to fail the schema");
        } else {
            assertFalse(rejected, name + " carries a defect the schema cannot see");
        }
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
