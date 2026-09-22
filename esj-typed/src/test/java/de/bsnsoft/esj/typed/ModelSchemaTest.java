package de.bsnsoft.esj.typed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks {@code schema/esj-en16931-2017.schema.json}, the generated schema that narrows
 * the format schema to the business terms of the registry.
 *
 * <p>The generated schema refers to the format schema by its identifier, so the validator
 * is given both files.
 */
class ModelSchemaTest {

    /**
     * The negative fixtures the two schemas together reject. It is the list of
     * {@code examples/invalid/README.md} column "rejected by the schema", plus the defects
     * the per-term entries add over the format schema: an occurrence index on a term
     * declared once, a term the model does not have, a content that does not satisfy the
     * grammar of the semantic data type of its term, and a component the registry does not
     * list for it or does not find where it declares it mandatory. What stays out is what
     * no schema can see: the layer L3 defects, the business rule, a date that matches the
     * pattern without being a day, and the L1 defects SPEC.md section 9.1 lists as
     * reader-only.
     */
    private static final List<String> REJECTED = List.of(
            "b2c-decimal-trailing-zeros",
            "base64-non-canonical",
            "binary-without-filename",
            "decimal-too-long",
            "decimal-trailing-zeros",
            "duplicate-and-surrogate-in-value-object",
            "duplicate-below-bad-owner-token",
            "duplicate-path-of-value-objects",
            "empty-extensions",
            "empty-string-value",
            "empty-string-with-missing-term",
            "index-on-bt-1",
            "number-instead-of-string",
            "object-without-component",
            "owner-token-syntax",
            "path-leading-zero-index",
            "scheme-on-text-value",
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
            "unknown-term",
            "value-object-members-17",
            "value-not-a-string");

    private static Schema schema;

    private static Schema formatSchema;

    @BeforeAll
    static void loadTheSchemas() {
        String format = Examples.schema("esj.schema.json");
        String model = Examples.schema("esj-en16931-2017.schema.json");
        String formatId = Examples.schemaIdentifier(format);
        schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                        builder -> builder.schemas(Map.of(formatId, format)))
                .getSchema(model, InputFormat.JSON);
        formatSchema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(format, InputFormat.JSON);
    }

    /**
     * The examples the model schema covers. It is generated from the core registry of one
     * edition and defines no additional property, so a document carrying a term of an
     * extension registry — {@code b2c-gross}, with the four terms of
     * {@code model/b2c/0.1.json} — is outside what it describes and is checked against the
     * combined registry instead (specification, sections 5.6 and 11.1).
     */
    static List<String> examples() {
        return Examples.NAMES.stream().filter(name -> !name.equals("b2c-gross")).toList();
    }

    @Test
    void theModelSchemaOfTheCoreEditionRefusesATermOfAnExtensionRegistry() {
        List<Error> errors = validate(Examples.pretty("b2c-gross"));

        assertEquals(10, errors.size());
        assertTrue(errors.get(0).toString().contains("/BT-B2C-010"), errors.get(0).toString());
    }

    /**
     * The negative fixtures of the edition this schema was generated for. A schema of one
     * edition pins that edition, so a fixture of another one is refused for its
     * {@code semanticModel} before anything else is looked at, and it is measured against
     * its own edition's schema in {@code Edition2026SchemaTest} instead.
     *
     * @return the file names without their extension
     */
    static List<String> invalidFixtures() {
        List<String> names = new ArrayList<>();
        for (String name : Examples.invalidNames()) {
            String document = new String(Examples.invalid(name), StandardCharsets.UTF_8);
            if (document.contains("\"EN16931-1:2017+A1:2019/AC:2020\"")) {
                names.add(name);
            }
        }
        return names;
    }

    private static List<Error> validate(byte[] document) {
        return schema.validate(new String(document, StandardCharsets.UTF_8), InputFormat.JSON);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void everyExampleValidatesAgainstTheModelSchema(String name) {
        assertEquals(List.of(), validate(Examples.pretty(name)), name);
        assertEquals(List.of(), validate(Examples.canonical(name)), name);
    }

    @ParameterizedTest
    @MethodSource("invalidFixtures")
    void aFixtureIsRejectedExactlyWhereTheFixtureTableSaysSo(String name) {
        boolean rejected = !validate(Examples.invalid(name)).isEmpty();
        if (REJECTED.contains(name)) {
            assertTrue(rejected, name + " is expected to fail the model schema");
        } else {
            assertFalse(rejected, name + " carries a defect no schema can see");
        }
    }

    /**
     * The five documents of the conformance corpus that carry an extension term. They are
     * the ones the generated schema is not the right tool for: its path patterns come from
     * the core registry, so a path outside it matches none of them and
     * {@code additionalProperties} refuses it. SPEC.md sections 5.6 and 6.2 say that this
     * is a property of the schema and not a verdict on the document.
     */
    private static final List<String> CARRY_AN_EXTENSION_TERM = List.of(
            "business-cases/extension/04.01a-INVOICE_ubl.xml.esj.json",
            "business-cases/extension/04.02a-INVOICE_ubl.xml.esj.json",
            "business-cases/extension/04.03a-INVOICE_ubl.xml.esj.json",
            "business-cases/extension/04.04a-INVOICE_ubl.xml.esj.json",
            "business-cases/extension/05.01a-INVOICE_ubl.xml.esj.json");

    static List<String> conformanceDocuments() {
        return Examples.conformanceDocuments();
    }

    /**
     * Pins the scope of the generated schema against the whole conformance corpus: every
     * document that stays inside the core registry validates, and every document carrying
     * an extension term is refused, with the complaint naming the extension path.
     */
    @ParameterizedTest
    @MethodSource("conformanceDocuments")
    void theModelSchemaAcceptsTheCorpusExceptWhereAnExtensionTermAppears(String path) {
        List<Error> errors = validate(Examples.conformanceDocument(path));

        if (CARRY_AN_EXTENSION_TERM.contains(path)) {
            assertFalse(errors.isEmpty(),
                    path + " carries an extension term and is outside the scope of this schema");
        } else {
            assertEquals(List.of(), errors, path);
        }
    }

    /**
     * The other half of the same claim: those five documents are well formed and pass the
     * format schema, so the refusal above is the scope of the generated schema and not a
     * defect of the documents.
     */
    @ParameterizedTest
    @MethodSource("conformanceDocuments")
    void everyDocumentOfTheCorpusPassesTheFormatSchema(String path) {
        assertEquals(List.of(), formatSchema.validate(
                        new String(Examples.conformanceDocument(path), StandardCharsets.UTF_8),
                        InputFormat.JSON),
                path);
    }

    @Test
    void theModelSchemaNamesTheTermsAndRefusesEverythingElse() {
        String model = Examples.schema("esj-en16931-2017.schema.json");
        assertTrue(model.contains("\"^/BT-1$\""), "a term at the root");
        assertTrue(model.contains("\"^/BG-25/(?:0|[1-9][0-9]*)/BG-29/BT-146$\""),
                "a term under a repeatable group");
        assertFalse(model.contains("\"^/BG-31"), "no term is addressed without its parent chain");
        assertTrue(model.contains("\"additionalProperties\": false"), "unknown terms are refused");
        assertFalse(model.contains("DEX"), "ESJ 0.1 generates the core registry alone");
    }
}
