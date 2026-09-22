package de.bsnsoft.esj.bindings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * What the three binding tables say, read as the reader reads them.
 *
 * <p>The facts themselves are checked where they are produced, in the cross-check report of
 * {@code conformance/bindings/}. What is checked here is that this module reads them, that
 * it refuses an expression it does not understand rather than matching something else, and
 * that the one property the memory claim of {@link StreamingReader} rests on holds: no
 * element the reader has to hold whole lies on the way to an invoice line.
 */
class BindingTablesTest {

    @ParameterizedTest
    @EnumSource(BindingSyntax.class)
    void compiles(BindingSyntax syntax) {
        BindingTable table = BindingTable.of(syntax);
        assertEquals(syntax, table.syntax());
        assertFalse(table.termIds().isEmpty(), "the table carries terms");
        assertFalse(table.namespaces().isEmpty(), "the table names its namespaces");
        MatchTrie trie = MatchTrie.compile(table);
        assertEquals(syntax, trie.syntax());
    }

    /**
     * A flag is a fact about a binding, and a fact nobody can read is not one. The tables
     * carry the meaning of every flag they use in one line each, so a term that grows a
     * flag the table does not define is caught here rather than by a reader of the table
     * who guesses what it means.
     */
    @ParameterizedTest
    @EnumSource(BindingSyntax.class)
    void definesEveryFlagItUses(BindingSyntax syntax) {
        BindingTable table = BindingTable.of(syntax);
        assertEquals("2026-08-31", table.sourceRelease(),
                "the release of the source the facts were taken from");
        assertFalse(table.flagDefinitions().isEmpty());
        for (String id : table.termIds()) {
            for (String flag : table.flags(id)) {
                assertTrue(table.flagDefinitions().containsKey(flag),
                        "the table says what " + flag + " means, which " + id + " carries");
            }
        }
        for (String flag : table.flagDefinitions().keySet()) {
            assertTrue(table.termIds().stream().anyMatch(id -> table.flags(id).contains(flag)),
                    "the table uses the flag " + flag + " it defines");
        }
    }

    @ParameterizedTest
    @EnumSource(BindingSyntax.class)
    void bindsEveryTermOrSaysWhyNot(BindingSyntax syntax) {
        BindingTable table = BindingTable.of(syntax);
        for (String id : table.termIds()) {
            if (!table.isBound(id)) {
                assertFalse(table.flags(id).isEmpty(),
                        id + " is unbound, so the table says why");
                continue;
            }
            assertFalse(table.xpaths(id).isEmpty(), id + " is bound, so it has an XPath");
            for (String xpath : table.xpaths(id)) {
                assertTrue(xpath.startsWith("/"), xpath + " is absolute");
            }
        }
    }

    /**
     * Every convention of a table names an element the schema of that syntax declares,
     * inside an element the table binds something to, and none of them names a term the
     * table binds at that very element: a convention stands where a business term does
     * not.
     */
    @ParameterizedTest
    @EnumSource(BindingSyntax.class)
    void statesEveryConventionAtAnElementTheSchemaDeclares(BindingSyntax syntax) {
        BindingTable table = BindingTable.of(syntax);
        SchemaTable schema = SchemaTable.of(syntax);
        for (BindingTable.Convention convention : table.conventions()) {
            SchemaTable.Child declared = child(schema, convention);
            assertFalse(declared == null, convention.element()
                    + " is an element the schema of " + syntax + " declares");
            assertTrue(declared.type() == null || schema.type(declared.type()) == null
                            || schema.type(declared.type()).children().isEmpty(),
                    convention.element() + " carries a value rather than a structure");
            assertEquals(BindingTable.Condition.PARENT_WRITTEN_ELEMENT_ABSENT,
                    convention.condition());
            assertFalse(convention.source().isEmpty(),
                    convention.element() + " says where its value comes from");
        }
    }

    /**
     * The condition of a convention is a closed vocabulary, and a table that spells a
     * token outside it is refused where the table is read.
     *
     * <p>The condition decides what the writer does with the row. A release that read an
     * unknown token would apply the row with the meaning of the one condition it knows,
     * and the element it wrote into the document would be the wrong one at the wrong
     * time; the refusal names the table, the convention and the token instead.
     */
    @Test
    void refusesAConventionWhoseConditionIsNotInTheVocabulary() {
        BindingSyntax syntax = BindingSyntax.UBL_INVOICE;
        String table = new String(read(syntax), StandardCharsets.UTF_8)
                .replace(BindingTable.Condition.PARENT_WRITTEN_ELEMENT_ABSENT.token(),
                        "parent-written-element-present");

        BindingFormatException thrown = assertThrows(BindingFormatException.class,
                () -> BindingTable.load(
                        new ByteArrayInputStream(table.getBytes(StandardCharsets.UTF_8)),
                        syntax));

        assertTrue(thrown.getMessage().contains(syntax.toString()), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("cac:TaxScheme/cbc:ID"),
                thrown.getMessage());
        assertTrue(thrown.getMessage().contains("parent-written-element-present"),
                thrown.getMessage());
        assertTrue(thrown.getMessage().contains(
                BindingTable.Condition.PARENT_WRITTEN_ELEMENT_ABSENT.token()),
                "the refusal says what a condition is: " + thrown.getMessage());
    }

    /** Returns the binding table of one syntax as the bytes the module ships. */
    private static byte[] read(BindingSyntax syntax) {
        try (InputStream in = BindingTable.class.getResourceAsStream(syntax.table())) {
            assertFalse(in == null, syntax + " ships a binding table");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A convention that names a writer setting these options do not carry is refused. */
    @Test
    void refusesAConventionThatNamesAnUnknownWriterSetting() {
        assertThrows(BindingFormatException.class,
                () -> WriterOptions.defaults().conventionValue("nothingOfTheSort", "x"));
        assertEquals("FC",
                WriterOptions.defaults().conventionValue("taxRegistrationScheme", "x"));
        assertEquals("x", WriterOptions.defaults().conventionValue(null, "x"));
    }

    /** Returns the schema declaration of the element a convention is written at. */
    private static SchemaTable.Child child(SchemaTable schema,
            BindingTable.Convention convention) {
        String type = schema.rootType();
        List<String> steps = List.of(convention.element().substring(1).split("/"));
        SchemaTable.Child declared = null;
        for (String step : steps.subList(1, steps.size())) {
            SchemaTable.Type owner = schema.type(type);
            declared = owner == null ? null : owner.child(step);
            if (declared == null) {
                return null;
            }
            type = declared.type();
        }
        return declared;
    }

    /**
     * The one property the memory claim rests on. An element the reader holds whole is one
     * a predicate asks a question about that only its children can answer, or one whose
     * child carries a component written as a sibling; if such an element were an invoice
     * line, or anything that encloses one, the reader would hold a line — or a document —
     * rather than a few hundred bytes. The list is short and is written out here, so that
     * a table which puts a predicate higher up fails this test with the new path in hand.
     */
    @Test
    void holdsNothingThatEnclosesAnInvoiceLine() {
        assertEquals(List.of(
                "/Invoice/cac:AccountingCustomerParty/cac:Party/cac:PartyTaxScheme",
                "/Invoice/cac:AccountingSupplierParty/cac:Party/cac:PartyTaxScheme",
                "/Invoice/cac:AdditionalDocumentReference",
                "/Invoice/cac:AllowanceCharge",
                "/Invoice/cac:AllowanceCharge/cac:TaxCategory",
                "/Invoice/cac:InvoiceLine//cac:SubInvoiceLine/cac:AllowanceCharge",
                "/Invoice/cac:InvoiceLine//cac:SubInvoiceLine/cac:DocumentReference",
                "/Invoice/cac:InvoiceLine//cac:SubInvoiceLine/cac:Item"
                        + "/cac:ClassifiedTaxCategory",
                "/Invoice/cac:InvoiceLine//cac:SubInvoiceLine/cac:Price/cac:AllowanceCharge",
                "/Invoice/cac:InvoiceLine/cac:AllowanceCharge",
                "/Invoice/cac:InvoiceLine/cac:DocumentReference",
                "/Invoice/cac:InvoiceLine/cac:Item/cac:ClassifiedTaxCategory",
                "/Invoice/cac:InvoiceLine/cac:Price/cac:AllowanceCharge",
                "/Invoice/cac:OrderReference",
                "/Invoice/cac:TaxRepresentativeParty/cac:PartyTaxScheme",
                "/Invoice/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory"),
                MatchTrie.of(BindingSyntax.UBL_INVOICE).bufferedPaths());
        assertEquals(List.of(
                "/CreditNote/cac:AccountingCustomerParty/cac:Party/cac:PartyTaxScheme",
                "/CreditNote/cac:AccountingSupplierParty/cac:Party/cac:PartyTaxScheme",
                "/CreditNote/cac:AdditionalDocumentReference",
                "/CreditNote/cac:AllowanceCharge",
                "/CreditNote/cac:AllowanceCharge/cac:TaxCategory",
                "/CreditNote/cac:CreditNoteLine/cac:AllowanceCharge",
                "/CreditNote/cac:CreditNoteLine/cac:DocumentReference",
                "/CreditNote/cac:CreditNoteLine/cac:Item/cac:ClassifiedTaxCategory",
                "/CreditNote/cac:CreditNoteLine/cac:Price/cac:AllowanceCharge",
                "/CreditNote/cac:OrderReference",
                "/CreditNote/cac:TaxRepresentativeParty/cac:PartyTaxScheme",
                "/CreditNote/cac:TaxTotal/cac:TaxSubtotal/cac:TaxCategory"),
                MatchTrie.of(BindingSyntax.UBL_CREDIT_NOTE).bufferedPaths());
        String cii = "/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/ram:";
        String line = cii + "IncludedSupplyChainTradeLineItem/ram:SpecifiedLineTradeSettlement";
        assertEquals(List.of(
                cii + "ApplicableHeaderTradeAgreement/ram:AdditionalReferencedDocument",
                cii + "ApplicableHeaderTradeAgreement/ram:SpecifiedProcuringProject",
                cii + "ApplicableHeaderTradeSettlement/ram:ApplicableTradeTax",
                cii + "ApplicableHeaderTradeSettlement/ram:SpecifiedTradeAllowanceCharge",
                cii + "ApplicableHeaderTradeSettlement/ram:SpecifiedTradeAllowanceCharge"
                        + "/ram:CategoryTradeTax",
                line + "/ram:AdditionalReferencedDocument",
                line + "/ram:ApplicableTradeTax",
                line + "/ram:SpecifiedTradeAllowanceCharge"),
                MatchTrie.of(BindingSyntax.CII).bufferedPaths());
        for (BindingSyntax syntax : BindingSyntax.values()) {
            String lines = BindingTable.of(syntax).xpaths("BG-25").get(0);
            for (String held : MatchTrie.of(syntax).bufferedPaths()) {
                assertFalse(lines.equals(held) || lines.startsWith(held + "/"),
                        held + " encloses the invoice line of " + syntax);
            }
        }
    }

    @Test
    void refusesAnExpressionItDoesNotUnderstand() {
        Map<String, String> namespaces = Map.of("", "urn:example", "p", "urn:example:p");
        assertThrows(BindingFormatException.class,
                () -> Xpaths.steps("Invoice/cbc:ID", namespaces), "not absolute");
        assertThrows(BindingFormatException.class,
                () -> Xpaths.steps("/p:A[p:B", namespaces), "unclosed predicate");
        assertThrows(BindingFormatException.class,
                () -> Xpaths.steps("/p:A[p:B = 'x'] [p:C = 'y']/p:D", namespaces),
                "a step that is not its name and its predicates");
        assertThrows(BindingFormatException.class,
                () -> Xpaths.steps("/q:A", namespaces), "unknown prefix");
        assertThrows(BindingFormatException.class,
                () -> Xpaths.steps("/p:A[p:B]", namespaces), "a predicate without a test");
        assertThrows(BindingFormatException.class,
                () -> Xpaths.steps("/p:A//", namespaces), "an axis without a step");
    }

    @Test
    void readsTheShapesTheTablesWrite() {
        Map<String, String> namespaces = Map.of("", "urn:example", "p", "urn:example:p");
        List<BindingTable.Step> steps =
                Xpaths.steps("/Invoice/p:A[@schemeID = 'SEPA']//p:B[p:C/p:D != 'VAT']",
                        namespaces);
        assertEquals(3, steps.size());
        assertEquals("urn:example", steps.get(0).namespace());
        assertEquals(Predicate.Kind.ATTRIBUTE_EQUALS,
                steps.get(1).predicates().get(0).kind());
        assertFalse(steps.get(1).descendant());
        assertTrue(steps.get(2).descendant());
        assertEquals(Predicate.Kind.CHILD_NOT_EQUALS,
                steps.get(2).predicates().get(0).kind());
        assertEquals(2, steps.get(2).predicates().get(0).path().size());
        List<BindingTable.Step> two = Xpaths.steps(
                "/Invoice/p:A[p:B != 'x'][p:C != 'y']", namespaces);
        assertEquals(2, two.get(1).predicates().size());
        assertEquals("y", two.get(1).predicates().get(1).literal());
        assertEquals("unitCode", Xpaths.attribute("/Invoice/p:A/@unitCode"));
        assertEquals("/Invoice/p:A", Xpaths.withoutAttribute("/Invoice/p:A/@unitCode"));
    }
}
