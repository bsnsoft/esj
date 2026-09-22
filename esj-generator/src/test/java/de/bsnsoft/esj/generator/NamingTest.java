package de.bsnsoft.esj.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Checks the three rules that turn a registry slug into the name of a view type. */
class NamingTest {

    private static final Naming CORE = new Naming(Registry.en16931());

    @ParameterizedTest
    @CsvSource({
        "BG-4, Seller",
        "BG-7, Buyer",
        "BG-22, DocumentTotals",
        "BG-29, Price",
        "BG-31, Item"})
    void aTypeNameIsTheUpperCamelCaseFormOfTheSlug(String id, String expected) {
        assertEquals(expected, CORE.typeName(id));
    }

    @ParameterizedTest
    @CsvSource({
        "BG-1, Note",
        "BG-17, CreditTransfer",
        "BG-23, VatBreakdown",
        "BG-25, InvoiceLine",
        "BG-27, Allowance",
        "BG-32, ItemAttribute"})
    void aRepeatableGroupIsNamedAfterOneInstanceOfIt(String id, String expected) {
        assertEquals(expected, CORE.typeName(id));
    }

    @ParameterizedTest
    @CsvSource({
        "BG-5, SellerPostalAddress",
        "BG-8, BuyerPostalAddress",
        "BG-12, SellerTaxRepresentativePostalAddress",
        "BG-6, SellerContact",
        "BG-9, BuyerContact"})
    void aSlugThatSeveralGroupsShareIsQualifiedByTheEnclosingGroup(String id, String expected) {
        assertEquals(expected, CORE.typeName(id));
    }

    @Test
    void everyTypeNameIsUniqueAndIsNotOneOfTheNamesThePackageAlreadyUses() {
        Set<String> names = new HashSet<>();
        for (Term term : Registry.en16931().terms()) {
            if (!term.isGroup()) {
                continue;
            }
            String name = CORE.typeName(term.id());
            assertTrue(names.add(name), "two groups are named " + name);
            assertNotEquals(Naming.ROOT_TYPE, name);
            assertTrue(!name.endsWith(Naming.VIEW_SUFFIX), name + " collides with an implementation");
        }
        assertTrue(names.contains("Vat"), "BG-30 is named after its slug");
    }

    @Test
    void anExtensionGroupIsNamedByTheSameRules() {
        Naming combined = new Naming(Registry.en16931().withExtension(Registry.xrechnungExtension()));
        assertEquals("SubInvoiceLine", combined.typeName("BG-DEX-01"));
        assertEquals("SubInvoiceLineItem", combined.typeName("BG-DEX-02"));
        assertEquals("ThirdPartyPayment", combined.typeName("BG-DEX-09"));
        assertEquals("InvoiceLineItem", combined.typeName("BG-31"),
                "a clash qualifies both sides, so loading an extension can rename a core type");
    }

    @Test
    void aValueTypeIsTheJavaTypeOfTheSemanticDataType() {
        assertEquals("String", Naming.valueType(SemanticType.TEXT));
        assertEquals("String", Naming.valueType(SemanticType.CODE));
        assertEquals("String", Naming.valueType(SemanticType.DOCUMENT_REFERENCE));
        assertEquals("Identifier", Naming.valueType(SemanticType.IDENTIFIER));
        assertEquals("LocalDate", Naming.valueType(SemanticType.DATE));
        assertEquals("BigDecimal", Naming.valueType(SemanticType.UNIT_PRICE_AMOUNT));
        assertEquals("BinaryObject", Naming.valueType(SemanticType.BINARY_OBJECT));
    }

    @Test
    void aValueTypeOfTheTypedViewOrOfJavaLangNeedsNoImport() {
        assertEquals(Optional.of("java.time.LocalDate"), Naming.valueImport(SemanticType.DATE));
        assertEquals(Optional.of("java.math.BigDecimal"), Naming.valueImport(SemanticType.AMOUNT));
        assertEquals(Optional.empty(), Naming.valueImport(SemanticType.TEXT));
        assertEquals(Optional.empty(), Naming.valueImport(SemanticType.IDENTIFIER));
        assertEquals(Optional.empty(), Naming.valueImport(SemanticType.BINARY_OBJECT));
    }

    @Test
    void aValueReaderIsNamedAfterTheSemanticDataType() {
        assertEquals("Values.TEXT", Naming.valueReader(SemanticType.TEXT));
        assertEquals("Values.UNIT_PRICE_AMOUNT", Naming.valueReader(SemanticType.UNIT_PRICE_AMOUNT));
        assertEquals("Values.BINARY_OBJECT", Naming.valueReader(SemanticType.BINARY_OBJECT));
    }

    @Test
    void aMemberNameIsTheSlugOfTheRegistry() {
        Registry registry = Registry.en16931();
        assertEquals("invoiceNumber", CORE.memberName(registry.term("BT-1").orElseThrow()));
        assertEquals("netAmount", CORE.memberName(registry.term("BT-131").orElseThrow()));
        assertEquals("invoiceLines", CORE.memberName(registry.term("BG-25").orElseThrow()));
        assertEquals("addressLine3", CORE.memberName(registry.term("BT-162").orElseThrow()));
    }
}
