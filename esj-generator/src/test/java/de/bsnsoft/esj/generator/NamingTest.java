package de.bsnsoft.esj.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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

    /**
     * A slug is language-neutral, and the reserved words of Java are this generator's
     * business (specification, section 10): a slug Java reserves, or one that would hide a
     * member every view carries, is escaped by a trailing underscore rather than refused,
     * and so is the singular of a repeatable group's slug. A slug never carries an
     * underscore, so the escaped name is the name of no other member.
     */
    @Test
    void aSlugJavaReservesIsEscapedRatherThanRefused() {
        Registry registry = Registry.en16931().withExtension(Registry.load(
                new ByteArrayInputStream(RESERVED_SLUGS.getBytes(StandardCharsets.UTF_8))));
        Naming naming = new Naming(registry);

        assertEquals("default_", naming.memberName(registry.term("BT-KW-001").orElseThrow()));
        assertEquals("returns", naming.memberName(registry.term("BG-KW-001").orElseThrow()));
        assertEquals("return_", naming.singularMemberName(registry.term("BG-KW-001").orElseThrow()));
        assertEquals("path_", naming.memberName(registry.term("BT-KW-002").orElseThrow()));
        assertEquals("Return", naming.typeName("BG-KW-001"),
                "a type name is upper camel case and never a keyword");
        assertEquals("class_", Naming.escaped("class"));
        assertEquals("true_", Naming.escaped("true"));
        assertEquals("toString_", Naming.escaped("toString"));
        assertEquals("invoiceNumber", Naming.escaped("invoiceNumber"));
    }

    /** An extension whose slugs are words Java reserves. */
    private static final String RESERVED_SLUGS = """
            {
              "model": "Keyword-Extension",
              "edition": "Keyword 0.1",
              "imports": [{"model": "EN16931-1", "edition": "EN 16931-1:2017+A1:2019/AC:2020"}],
              "terms": [
                {
                  "id": "BT-KW-001", "kind": "BT", "name": "Default", "slug": "default",
                  "parent": null, "path": ["BT-KW-001"], "min": 0, "max": 1,
                  "datatype": "Text", "components": [], "order": 1, "description": "A term."
                },
                {
                  "id": "BG-KW-001", "kind": "BG", "name": "Returns", "slug": "returns",
                  "parent": null, "path": ["BG-KW-001"], "min": 0, "max": "n",
                  "datatype": null, "components": [], "order": 2, "description": "A group."
                },
                {
                  "id": "BT-KW-002", "kind": "BT", "name": "Path", "slug": "path",
                  "parent": "BG-KW-001", "path": ["BG-KW-001", "BT-KW-002"], "min": 1, "max": 1,
                  "datatype": "Text", "components": [], "order": 3, "description": "A term."
                }
              ]
            }
            """;

    @Test
    void aMemberNameIsTheSlugOfTheRegistry() {
        Registry registry = Registry.en16931();
        assertEquals("invoiceNumber", CORE.memberName(registry.term("BT-1").orElseThrow()));
        assertEquals("netAmount", CORE.memberName(registry.term("BT-131").orElseThrow()));
        assertEquals("invoiceLines", CORE.memberName(registry.term("BG-25").orElseThrow()));
        assertEquals("addressLine3", CORE.memberName(registry.term("BT-162").orElseThrow()));
    }
}
