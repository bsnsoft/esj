package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The element order the writer puts a UBL document in, against the schema modules it is
 * taken from.
 *
 * <p>The order of two sibling elements is a fact of the UBL 2.1 schema and of nothing else.
 * It is checked in as a resource because the writer must not read a schema module at run
 * time, and it is regenerated here on every build, for the reason {@link CiiSchemaTest}
 * gives.
 */
class UblSchemaTest {

    /** Where the regenerated table is written when it differs from the resource. */
    private static final Path REGENERATED = Path.of("target", "ubl-schema.json");

    @Test
    void theTableIsTheOneTheSchemaModulesSay() {
        String generated = UblSchemaSource.generate();
        String resource = new String(Corpus.bytes(
                "/de/bsnsoft/esj/bindings/ubl-schema.json"),
                StandardCharsets.UTF_8);
        if (!generated.equals(resource)) {
            write(generated);
            assertEquals(resource, generated, "the UBL schema table no longer describes the"
                    + " schema modules of the pack; the regenerated table was written to "
                    + REGENERATED.toAbsolutePath());
        }
    }

    /**
     * The one element of the two document types the table does not carry. Its declaration
     * is in the extension module of the library, which this table does not read, and no
     * business term of the semantic model is bound anywhere below it.
     */
    @Test
    void leavesOutTheOneElementItsModulesDoNotDeclare() {
        assertEquals(List.of("UBLExtensions", "UBLExtensions"),
                UblSchemaSource.generateAndList(),
                "the elements no module of this table declares");
    }

    @Test
    void carriesBothDocumentElements() {
        SchemaTable invoice = SchemaTable.of(BindingSyntax.UBL_INVOICE);
        SchemaTable creditNote = SchemaTable.of(BindingSyntax.UBL_CREDIT_NOTE);
        assertEquals("Invoice", invoice.rootElement());
        assertEquals("CreditNote", creditNote.rootElement());
        assertNotNull(invoice.type(invoice.rootType()));
        assertNotNull(creditNote.type(creditNote.rootType()));
        assertNotNull(invoice.type("cac:InvoiceLineType"));
        assertNull(invoice.type("cac:CatalogueLineType"),
                "the table carries the types the two documents reach and no others");
    }

    /**
     * The prefixes the two tables of this syntax share have to mean the same thing, because
     * the writer names an element with the prefix of the binding table and looks it up in
     * the schema table. The document namespace is the one prefix they do not share: the
     * binding table writes the document element without a prefix, and the schema table has
     * two document elements and names neither namespace.
     */
    @Test
    void agreesWithTheBindingTablesOnTheNamespaces() {
        for (BindingSyntax syntax : List.of(BindingSyntax.UBL_INVOICE,
                BindingSyntax.UBL_CREDIT_NOTE)) {
            Map<String, String> schema = SchemaTable.of(syntax).namespaces();
            for (Map.Entry<String, String> prefix
                    : BindingTable.of(syntax).namespaces().entrySet()) {
                if (prefix.getKey().isEmpty()) {
                    assertEquals(syntax.namespace(), prefix.getValue());
                    continue;
                }
                if (schema.containsKey(prefix.getKey())) {
                    assertEquals(prefix.getValue(), schema.get(prefix.getKey()),
                            syntax + " and its schema table agree on " + prefix.getKey());
                }
            }
        }
    }

    /**
     * Which elements the writer may create for structure alone: the ones the schema
     * requires and whose type asks for nothing in turn. UBL asks for them inside the
     * document and not only at its root, which is why the writer fills them everywhere.
     */
    @Test
    void namesTheTypesThatMayStandEmpty() {
        SchemaTable schema = SchemaTable.of(BindingSyntax.UBL_INVOICE);
        assertTrue(schema.isSkeletal("cac:TaxSchemeType"));
        assertTrue(schema.isSkeletal("cac:PartyTaxSchemeType"));
        assertTrue(schema.isSkeletal("cac:TaxCategoryType"));
        assertTrue(schema.isSkeletal("cac:ItemType"));
        assertFalse(schema.isSkeletal("cac:InvoiceLineType"),
                "an invoice line requires an identifier and an amount, which are content");
        assertFalse(schema.isSkeletal("cac:CardAccountType"),
                "a card account requires a network identifier, which is content");
        assertFalse(schema.isSkeletal("cbc:IDType"),
                "a value type is simple content and an empty one says nothing");
    }

    @Test
    void knowsTheAttributesTheValueTypesCarry() {
        SchemaTable schema = SchemaTable.of(BindingSyntax.UBL_INVOICE);
        assertTrue(schema.type("cbc:AmountType").hasAttribute("currencyID"),
                "the currency is an attribute inherited from the core component type");
        assertTrue(schema.type("cbc:InvoicedQuantityType").hasAttribute("unitCode"));
        assertTrue(schema.type("cbc:ItemClassificationCodeType").hasAttribute("listID"));
        assertTrue(schema.type("cbc:ItemClassificationCodeType")
                .hasAttribute("listVersionID"));
        assertTrue(schema.type("cbc:PaymentMeansCodeType").hasAttribute("name"));
        assertFalse(schema.type("cbc:IDType").hasAttribute("currencyID"));
    }

    @Test
    void knowsWhichElementsMayRepeat() {
        SchemaTable schema = SchemaTable.of(BindingSyntax.UBL_INVOICE);
        assertTrue(schema.type(schema.rootType()).child("cac:InvoiceLine").repeatable());
        assertTrue(schema.type(schema.rootType()).child("cac:TaxTotal").repeatable());
        assertTrue(schema.type("cac:TaxTotalType").child("cac:TaxSubtotal").repeatable());
        assertFalse(schema.type("cac:PaymentMeansType").child("cac:PayeeFinancialAccount")
                .repeatable());
        assertFalse(schema.type(schema.rootType()).child("cbc:ID").repeatable());
    }

    private static void write(String generated) {
        try {
            Files.createDirectories(REGENERATED.getParent());
            Files.writeString(REGENERATED, generated, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
