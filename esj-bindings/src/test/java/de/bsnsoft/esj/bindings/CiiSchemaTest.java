package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The element order the writer puts a document in, against the schema modules it is taken
 * from.
 *
 * <p>The order of two sibling elements is a fact of the CII schema and of nothing else. It
 * is checked in as a resource because the writer must not read a schema module at run time,
 * and it is regenerated here on every build because a checked-in derivative of a file that
 * can be replaced is worth exactly as much as the test that recomputes it.
 */
class CiiSchemaTest {

    /** Where the regenerated table is written when it differs from the resource. */
    private static final Path REGENERATED = Path.of("target", "cii-schema.json");

    @Test
    void theTableIsTheOneTheSchemaModulesSay() {
        BindingTable table = BindingTable.of(BindingSyntax.CII);
        String generated = CiiSchemaSource.generate(table.namespaces());
        String resource = new String(Corpus.bytes(
                "/de/bsnsoft/esj/bindings/cii-schema.json"),
                StandardCharsets.UTF_8);
        if (!generated.equals(resource)) {
            write(generated);
            assertEquals(resource, generated, "the CII schema table no longer describes the"
                    + " schema modules of the pack; the regenerated table was written to "
                    + REGENERATED.toAbsolutePath());
        }
    }

    @Test
    void theTableAndTheBindingTableAgreeOnTheSyntax() {
        BindingTable table = BindingTable.of(BindingSyntax.CII);
        SchemaTable schema = SchemaTable.of(BindingSyntax.CII);
        assertEquals(table.namespaces(), schema.namespaces(),
                "the two tables of this syntax name the same namespaces");
        assertEquals("rsm:CrossIndustryInvoice", schema.rootElement());
        assertNotNull(schema.type(schema.rootType()));
        assertEquals(163, schema.typeNames().size(), "complex types of the syntax");
    }

    /**
     * Which elements the writer may create for structure alone: the ones the schema
     * requires and whose type asks for nothing in turn.
     *
     * <p>The three header sections of a cross industry invoice are of that kind, and the
     * exchanged document is not, because it requires an identifier and an identifier is
     * content. The document element's own type is therefore not skeletal either, which
     * costs nothing: the writer asks the question of each required child separately, and
     * the exchanged document is written as soon as the invoice number is.
     */
    @Test
    void namesTheTypesThatMayStandEmpty() {
        SchemaTable schema = SchemaTable.of(BindingSyntax.CII);
        assertTrue(schema.isSkeletal("ram:SupplyChainTradeTransactionType"));
        assertTrue(schema.isSkeletal("ram:HeaderTradeAgreementType"));
        assertTrue(schema.isSkeletal("ram:HeaderTradeDeliveryType"));
        assertTrue(schema.isSkeletal("ram:HeaderTradeSettlementType"));
        assertTrue(schema.isSkeletal("ram:ExchangedDocumentContextType"));
        assertFalse(schema.isSkeletal("ram:ExchangedDocumentType"),
                "the exchanged document requires an identifier, which is content");
        assertFalse(schema.isSkeletal("rsm:CrossIndustryInvoiceType"),
                "the document element requires the exchanged document, which is not"
                        + " skeletal either");
        assertFalse(schema.isSkeletal("udt:DateTimeType"),
                "a date is a choice of two spellings and an empty one is neither");
    }

    /**
     * The two facts of the schema that decide which of a term's XPaths the writer takes:
     * the syntax has no element of the name the source model binds BT-86 to, and no
     * attribute of the name it binds the second spelling of BT-150 to.
     */
    @Test
    void knowsWhichNamesTheSyntaxHas() {
        SchemaTable schema = SchemaTable.of(BindingSyntax.CII);
        SchemaTable.Type means = schema.type("ram:TradeSettlementPaymentMeansType");
        assertNotNull(means.child("ram:PayeeSpecifiedCreditorFinancialInstitution"));
        assertEquals(null, means.child("ram:PayeeSpecifiedDebtorFinancialInstitution"));
        assertTrue(schema.type("udt:QuantityType").hasAttribute("unitCode"));
        assertFalse(schema.type("udt:QuantityType").hasAttribute("UnitCode"));
    }

    @Test
    void knowsWhichElementsMayRepeat() {
        SchemaTable schema = SchemaTable.of(BindingSyntax.CII);
        assertTrue(schema.type("ram:SupplyChainTradeTransactionType")
                .child("ram:IncludedSupplyChainTradeLineItem").repeatable());
        assertTrue(schema.type("ram:HeaderTradeSettlementType")
                .child("ram:SpecifiedTradeSettlementPaymentMeans").repeatable());
        assertFalse(schema.type("ram:TradeSettlementPaymentMeansType")
                .child("ram:PayeePartyCreditorFinancialAccount").repeatable());
        assertFalse(schema.type("ram:HeaderTradeSettlementType")
                .child("ram:InvoiceReferencedDocument").repeatable());
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
