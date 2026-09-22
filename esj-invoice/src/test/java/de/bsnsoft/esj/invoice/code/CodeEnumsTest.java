package de.bsnsoft.esj.invoice.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.rules.CodeList;
import de.bsnsoft.esj.rules.CodeLists;
import de.bsnsoft.esj.rules.RulePack;
import de.bsnsoft.esj.rules.RulePacks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Weighs the generated enums of this package against the code list snapshots they were
 * generated from.
 *
 * <p>The snapshots are read through the rule engine, from the same files and the same pack
 * version the rules decide against: an enum that had drifted from the list a rule asks
 * about would spell a code the validator then rejects.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CodeEnumsTest {

    private static final String PACK = "en16931";

    private static final String VERSION = "1.3.16";

    private final CodeLists lists = CodeLists.bundled(RulePacks.bundled(PACK, VERSION));

    private final Map<String, Coded[]> enums = enums();

    private static Map<String, Coded[]> enums() {
        Map<String, Coded[]> enums = new LinkedHashMap<>();
        enums.put("Unit", Unit.values());
        enums.put("InvoiceType", InvoiceType.values());
        enums.put("PaymentMeansCode", PaymentMeansCode.values());
        enums.put("VatCategory", VatCategory.values());
        enums.put("AllowanceReason", AllowanceReason.values());
        enums.put("ChargeReason", ChargeReason.values());
        enums.put("CurrencyCode", CurrencyCode.values());
        enums.put("Country", Country.values());
        enums.put("ElectronicAddressScheme", ElectronicAddressScheme.values());
        enums.put("VatExemptionReason", VatExemptionReason.values());
        return enums;
    }

    @Test
    void everyConstantCarriesACodeOfItsSnapshotWithTheNameThePublisherGivesIt() {
        for (Map.Entry<String, Coded[]> generated : enums.entrySet()) {
            for (Coded constant : generated.getValue()) {
                String listId = constant.listId();
                CodeList list = lists.require(listId);
                assertTrue(list.contains(constant.code()),
                        generated.getKey() + "." + constant + " carries the code "
                                + constant.code() + ", which " + listId + " does not have");
                assertEquals(list.describe(constant.code()).orElseThrow(),
                        constant.publishedName(),
                        generated.getKey() + "." + constant + " carries another name than "
                                + listId + " gives its code");
            }
        }
    }

    @Test
    void everyCodeOfEverySnapshotHasAConstant() {
        for (Map.Entry<String, Coded[]> generated : enums.entrySet()) {
            Set<String> codes = new LinkedHashSet<>();
            Set<String> listIds = new LinkedHashSet<>();
            for (Coded constant : generated.getValue()) {
                codes.add(constant.code());
                listIds.add(constant.listId());
            }
            int size = 0;
            for (String listId : listIds) {
                CodeList list = lists.require(listId);
                size += list.size();
                for (String code : list.entries().keySet()) {
                    assertTrue(codes.contains(code), generated.getKey()
                            + " has no constant for the code " + code + " of " + listId);
                }
            }
            assertEquals(size, generated.getValue().length,
                    generated.getKey() + " has one constant per code of " + listIds);
        }
    }

    @Test
    void theEnumsCoverTheCodeListsTheRulePackDecidesAgainst() {
        RulePack pack = RulePacks.bundled(PACK, VERSION);
        assertEquals(VERSION, pack.version(), "the enums are generated from this pack version");
        List<String> covered = new ArrayList<>();
        for (Coded[] constants : enums.values()) {
            for (Coded constant : constants) {
                String listId = constant.listId();
                if (!covered.contains(listId)) {
                    covered.add(listId);
                }
            }
        }
        for (String listId : covered) {
            assertTrue(pack.codeLists().containsKey(listId),
                    "the pack names the snapshot of " + listId + " the enums were generated from");
        }
    }

    @Test
    void theSpotValuesAreTheOnesTheListsPublish() {
        assertEquals("H87", Unit.PIECE.code());
        assertSame(Unit.PIECE, Unit.of("H87"));
        assertEquals("unece-rec20", Unit.PIECE.listId());
        assertEquals("XPP", Unit.PIECE_XPP.code(), "the same name in Rec 21 keeps its own code");
        assertEquals("unece-rec21", Unit.PIECE_XPP.listId());
        assertEquals("S", VatCategory.STANDARD.code());
        assertEquals("AE", VatCategory.REVERSE_CHARGE.code());
        assertEquals("58", PaymentMeansCode.SEPA_CREDIT_TRANSFER.code());
        assertEquals("380", InvoiceType.COMMERCIAL_INVOICE.code());
        assertEquals("381", InvoiceType.CREDIT_NOTE.code());
        assertEquals("EUR", CurrencyCode.EUR.code());
        assertEquals("DE", Country.DE.code());
        assertEquals("VATEX_EU_132", VatExemptionReason.of("VATEX-EU-132").toString());
        assertEquals("piece", Unit.PIECE.publishedName());
    }

    @Test
    void aCodeOutsideTheSnapshotIsRepresentableAndACodeInsideItIsNotTwice() {
        Coded custom = Unit.custom("ZZ9");
        assertInstanceOf(CustomCode.class, custom);
        assertEquals("ZZ9", custom.code());
        assertEquals("", custom.publishedName());
        assertEquals(new CustomCode("Unit", "ZZ9"), custom);
        assertEquals(custom, Unit.resolve("ZZ9"));
        assertSame(Unit.PIECE, Unit.resolve("H87"));

        assertThrows(IllegalArgumentException.class, () -> Unit.custom("H87"),
                "a code the snapshot carries has a constant and must not have a second form");
        assertThrows(IllegalArgumentException.class, () -> Unit.of("ZZ9"));
        assertThrows(IllegalArgumentException.class, () -> CurrencyCode.of("eur"),
                "a code list is a list of codes and not of their spellings");
        assertThrows(NullPointerException.class, () -> Unit.of(null));
        assertThrows(IllegalArgumentException.class, () -> new CustomCode("Unit", " "));
    }
}
