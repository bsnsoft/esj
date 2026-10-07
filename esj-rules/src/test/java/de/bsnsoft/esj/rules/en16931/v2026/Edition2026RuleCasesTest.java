package de.bsnsoft.esj.rules.en16931.v2026;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.rules.RuleEngine;
import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.validate.Severity;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static de.bsnsoft.esj.rules.en16931.v2026.Documents2026.minimal;
import static de.bsnsoft.esj.rules.en16931.v2026.Documents2026.set;
import static de.bsnsoft.esj.rules.en16931.v2026.Documents2026.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A case for every rule this pack writes out rather than takes over: a document the rule is
 * silent on and the same document with one thing changed, which it speaks on.
 *
 * <p>No official validation artefact is published for this edition, so a case is the whole of
 * the evidence for a rule whose oracle is {@code cases}. What a case shows is that the engine
 * decides what the rule was written to say; that what was written is what the standard means
 * is a reading of the text and is not something a test can establish. The arithmetic of the
 * rules that compute is written out in the name of the case.
 *
 * <p>The rules this pack takes over from the pack of the 2017 edition are not repeated here.
 * Their cases are the cases of that pack, and what this pack adds to them is that the shared
 * rule still resolves against the registry of this edition, which {@link Edition2026PackTest}
 * checks by compiling the whole pack.
 */
class Edition2026RuleCasesTest {

    private static final RuleEngine ENGINE = new En16931V2026Pack().engine(Registry.forEdition("2026"));

    /** One case: a document the rule is silent on and one it speaks on. */
    private record Case(String id, String what, SemanticDocument holds, SemanticDocument fails) {
    }

    @Test
    void theWholePackIsSilentOnTheDocumentTheCasesStartFrom() {
        List<String> spoke = new ArrayList<>();
        for (RuleFinding finding : ENGINE.evaluate(minimal().build())) {
            spoke.add(finding.code() + ": " + finding.message());
        }

        assertEquals(List.of(), spoke);
    }

    @TestFactory
    List<DynamicTest> everyCaseDecidesItsRule() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Case one : cases()) {
            tests.add(DynamicTest.dynamicTest(one.id() + ": " + one.what(), () -> {
                assertFalse(spoke(one.id(), one.holds()),
                        one.id() + " speaks on the document it should be silent on");
                assertTrue(spoke(one.id(), one.fails()),
                        one.id() + " is silent on the document it should speak on");
            }));
        }
        return tests;
    }

    private static boolean spoke(String id, SemanticDocument document) {
        for (RuleFinding finding : ENGINE.evaluate(document)) {
            if (finding.code().equals(id) && finding.severity() != Severity.INFO) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the documents

    /** A document level charge or tax, with the three members a case wants to vary. */
    private static SemanticDocument.Builder charge(String amount, String category, String reason) {
        SemanticDocument.Builder builder = minimal();
        if (amount != null) {
            set(builder, "/BG-21/0/BT-99", amount);
        }
        if (category != null) {
            set(builder, "/BG-21/0/BT-102", category);
        }
        if (reason != null) {
            set(builder, "/BG-21/0/BT-104", reason);
        }
        return builder;
    }

    /** An invoice line charge or tax. */
    private static SemanticDocument.Builder lineCharge(String amount, String reason, String taxCode) {
        SemanticDocument.Builder builder = minimal();
        if (amount != null) {
            set(builder, "/BG-25/0/BG-28/0/BT-141", amount);
        }
        if (reason != null) {
            set(builder, "/BG-25/0/BG-28/0/BT-144", reason);
        }
        if (taxCode != null) {
            set(builder, "/BG-25/0/BG-28/0/BT-193", taxCode);
        }
        return builder;
    }

    /** A second VAT breakdown, so that a case can leave a term out of one of them. */
    private static SemanticDocument.Builder breakdown(String category, String taxAmount) {
        SemanticDocument.Builder builder = set(set(minimal(),
                "/BG-23/1/BT-116", "0"), "/BG-23/1/BT-118", category);
        if (taxAmount != null) {
            set(builder, "/BG-23/1/BT-117", taxAmount);
        }
        return builder;
    }

    /** The invoice with everything it takes to state one VAT category on its only line. */
    private static SemanticDocument.Builder category(String code, String rate) {
        SemanticDocument.Builder builder = set(set(minimal(),
                "/BG-25/0/BG-30/BT-151", code), "/BG-23/0/BT-118", code);
        if (rate != null) {
            set(set(builder, "/BG-25/0/BG-30/BT-152", rate), "/BG-23/0/BT-119", rate);
        }
        return builder;
    }

    private static SemanticDocument.Builder buyerVat(SemanticDocument.Builder builder) {
        return set(builder, "/BG-7/BT-48", "DE987654321");
    }

    private static SemanticDocument identifier(String path, String content, String scheme) {
        return minimal().set(SemanticPath.of(path),
                scheme == null ? SemanticValue.of(content)
                        : SemanticValue.identifier(content, scheme)).build();
    }

    // ---------------------------------------------------------------------- the cases

    private static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        integrity(cases);
        conditions(cases);
        decimals(cases);
        vat(cases);
        return cases;
    }

    private static void add(List<Case> cases, String id, String what, SemanticDocument holds,
                            SemanticDocument fails) {
        cases.add(new Case(id, what, holds, fails));
    }

    private static void integrity(List<Case> cases) {
        add(cases, "BR-36", "a charge or tax states an amount",
                charge("5", "Z", "Handling").build(), charge(null, "Z", "Handling").build());
        add(cases, "BR-37", "a charge or tax states a VAT category code",
                charge("5", "Z", "Handling").build(), charge("5", null, "Handling").build());
        add(cases, "BR-38", "a charge or tax states a reason or a reason code",
                charge("5", "Z", "Handling").build(), charge("5", "Z", null).build());
        add(cases, "BR-43", "a line charge or tax states an amount",
                lineCharge("5", "Handling", null).build(),
                lineCharge(null, "Handling", null).build());
        add(cases, "BR-44", "a non-VAT tax type code is a reason of its own",
                lineCharge("5", null, "CAR").build(), lineCharge("5", null, null).build());
        add(cases, "BR-46", "a margin scheme breakdown needs no tax amount",
                breakdown("D", null).build(), breakdown("Z", null).build());
        add(cases, "BR-50", "an account group states an account identifier",
                set(set(minimal(), "/BG-16/BT-81", "30"),
                        "/BG-16/BG-17/0/BT-84", "DE02120300000000202051").build(),
                set(set(minimal(), "/BG-16/BT-81", "30"),
                        "/BG-16/BG-17/0/BT-85", "Example GmbH").build());
        add(cases, "BR-53", "an accounting currency obliges the total in it",
                with("/BT-6", "USD", "/BG-22/BT-111", "0"), with("/BT-6", "USD"));
        add(cases, "BR-54", "an attribute code stands in for the attribute name",
                set(set(minimal(), "/BG-25/0/BG-31/BG-32/0/BT-161", "red"),
                        "/BG-25/0/BG-31/BG-32/0/BT-211", "COL").build(),
                with("/BG-25/0/BG-31/BG-32/0/BT-161", "red"));
        add(cases, "BR-67", "a negative line amount needs a negative quantity or allowances",
                set(set(minimal(), "/BG-25/0/BT-131", "-100"),
                        "/BG-25/0/BT-129", "-1").build(),
                with("/BG-25/0/BT-131", "-100"));
        add(cases, "BR-68", "one exemption code carries one exemption text",
                set(set(set(set(minimal(),
                        "/BG-23/0/BT-121", "VATEX-EU-132"), "/BG-23/0/BT-120", "Exempt"),
                        "/BG-25/0/BG-30/BT-195", "VATEX-EU-132"),
                        "/BG-25/0/BG-30/BT-194", "Exempt").build(),
                set(set(set(set(minimal(),
                        "/BG-23/0/BT-121", "VATEX-EU-132"), "/BG-23/0/BT-120", "Exempt"),
                        "/BG-25/0/BG-30/BT-195", "VATEX-EU-132"),
                        "/BG-25/0/BG-30/BT-194", "Something else").build());
        add(cases, "BR-69", "the seller identifier carries a scheme",
                identifier("/BG-4/BT-29/0", "12345", "0088"),
                identifier("/BG-4/BT-29/0", "12345", null));
        add(cases, "BR-70", "the seller legal registration identifier carries a scheme",
                identifier("/BG-4/BT-30", "HRB 1234", "0198"),
                identifier("/BG-4/BT-30", "HRB 1234", null));
        add(cases, "BR-71", "the buyer identifier carries a scheme",
                identifier("/BG-7/BT-46/0", "54321", "0088"),
                identifier("/BG-7/BT-46/0", "54321", null));
        add(cases, "BR-72", "the buyer legal registration identifier carries a scheme",
                identifier("/BG-7/BT-47", "HRB 4321", "0198"),
                identifier("/BG-7/BT-47", "HRB 4321", null));
        add(cases, "BR-73", "the payee identifier carries a scheme",
                identifier("/BG-10/BT-60", "P-1", "0088"),
                identifier("/BG-10/BT-60", "P-1", null));
        add(cases, "BR-74", "the payee legal registration identifier carries a scheme",
                identifier("/BG-10/BT-61", "HRB 9", "0198"),
                identifier("/BG-10/BT-61", "HRB 9", null));
        add(cases, "BR-75", "the deliver to location identifier carries a scheme",
                identifier("/BG-13/BT-71", "L-1", "0088"),
                identifier("/BG-13/BT-71", "L-1", null));
        add(cases, "BR-76", "an intra-community invoice states a payment account",
                set(set(set(category("K", "0"), "/BG-16/BT-81", "30"),
                        "/BG-16/BG-17/0/BT-84", "DE02120300000000202051"),
                        "/BG-23/0/BT-117", "0").build(),
                category("K", "0").build());
    }

    private static void conditions(List<Case> cases) {
        add(cases, "BR-CO-13", "the total without VAT is the line sum less allowances plus charges",
                minimal().build(), with("/BG-22/BT-109", "90"));
        add(cases, "BR-CO-14", "the total VAT amount is the sum of the category tax amounts",
                with("/BG-22/BT-110", "0"), with("/BG-22/BT-110", "5"));
        add(cases, "BR-CO-16", "the amount due is the total with VAT less what was paid",
                minimal().build(), with("/BG-22/BT-115", "90"));
        add(cases, "BR-CO-22", "a charge or tax states a reason, a reason code or a tax code",
                charge("5", "Z", "Handling").build(), charge("5", "Z", null).build());
        add(cases, "BR-CO-24", "a line charge or tax states a reason, a reason code or a tax code",
                lineCharge("5", "Handling", null).build(), lineCharge("5", null, null).build());
        add(cases, "BR-CO-25", "a positive amount due needs a due date or a payment term text",
                set(Documents2026.withoutDueDate(), "/BG-33/0/BT-20", "Net 30").build(),
                Documents2026.withoutDueDate().build());
        add(cases, "BR-CO-28", "a purchase order is referenced at one level",
                with("/BT-13", "PO-1"), with("/BT-13", "PO-1", "/BG-25/0/BT-188", "PO-1"));
        add(cases, "BR-CO-29", "a despatch advice is referenced at one level",
                with("/BT-16", "DA-1"), with("/BT-16", "DA-1", "/BG-25/0/BT-189", "DA-1"));
        add(cases, "BR-CO-30", "a receiving advice is referenced at one level",
                with("/BT-15", "RA-1"), with("/BT-15", "RA-1", "/BG-25/0/BT-191", "RA-1"));
        add(cases, "BR-CO-31", "the price base quantity is not negative",
                with("/BG-25/0/BG-29/BT-149", "1"), with("/BG-25/0/BG-29/BT-149", "-1"));
        add(cases, "BR-CO-32", "100 divided by a base of 1 times a quantity of 1 is 100",
                minimal().build(), with("/BG-25/0/BT-131", "90"));
        add(cases, "BR-CO-34", "a charge states a reason code or a non-VAT tax code, not both",
                set(charge("5", "Z", "Handling"), "/BG-21/0/BT-105", "AAA").build(),
                set(set(charge("5", "Z", "Handling"), "/BG-21/0/BT-105", "AAA"),
                        "/BG-21/0/BT-177", "CAR").build());
        add(cases, "BR-CO-35", "a line charge states a reason code or a tax type code, not both",
                set(lineCharge("5", "Handling", null), "/BG-25/0/BG-28/0/BT-145", "AAA").build(),
                set(set(lineCharge("5", "Handling", null), "/BG-25/0/BG-28/0/BT-145", "AAA"),
                        "/BG-25/0/BG-28/0/BT-193", "CAR").build());
        add(cases, "BR-CO-36", "ten per cent of a total with VAT of 100 is a discount of 10",
                discount("10", "10"), discount("10", "5"));
        add(cases, "BR-CO-38", "ten per cent of a base of 100 is an allowance of 10",
                allowance("10", "100", "10"), allowance("5", "100", "10"));
        add(cases, "BR-CO-39", "ten per cent of a base of 100 is a charge of 10",
                documentCharge("10", "100", "10"), documentCharge("5", "100", "10"));
        add(cases, "BR-CO-40", "delivery information stands at one level",
                with("/BG-13/BT-72", "2026-01-10"),
                with("/BG-13/BT-72", "2026-01-10", "/BG-25/0/BG-37/BT-187", "2026-01-10"));
        add(cases, "BR-CO-45", "a delivery note is referenced at one level",
                with("/BT-197", "DN-1"), with("/BT-197", "DN-1", "/BG-25/0/BT-198", "DN-1"));
        add(cases, "BR-CO-46", "a sales order is referenced at one level",
                with("/BT-14", "SO-1"), with("/BT-14", "SO-1", "/BG-25/0/BT-200", "SO-1"));
        add(cases, "BR-CO-47", "a preceding invoice is referenced at one level",
                with("/BG-3/0/BT-25", "RE-2025-9"),
                with("/BG-3/0/BT-25", "RE-2025-9", "/BG-25/0/BG-39/0/BT-217", "RE-2025-9"));
        add(cases, "BR-CO-48", "a total VAT amount of 0 at any rate is 0 in the other currency",
                accounting("0"), accounting("5"));
        add(cases, "BR-CO-51", "the price base quantity is measured as the invoiced quantity is",
                with("/BG-25/0/BG-29/BT-150", "C62"), with("/BG-25/0/BG-29/BT-150", "KGM"));
        add(cases, "BR-CO-09", "a VAT identifier begins with a country the list knows",
                minimal().build(), with("/BG-4/BT-31", "QQ123456789"));
    }

    private static SemanticDocument discount(String percentage, String amount) {
        return set(set(set(minimal(), "/BG-33/0/BG-35/0/BT-170", "2026-02-01"),
                "/BG-33/0/BG-35/0/BT-171", percentage),
                "/BG-33/0/BG-35/0/BT-172", amount).build();
    }

    private static SemanticDocument allowance(String amount, String base, String percentage) {
        return set(set(set(set(set(minimal(), "/BG-20/0/BT-92", amount),
                "/BG-20/0/BT-93", base), "/BG-20/0/BT-94", percentage),
                "/BG-20/0/BT-95", "Z"), "/BG-20/0/BT-97", "Discount").build();
    }

    private static SemanticDocument documentCharge(String amount, String base, String percentage) {
        return set(set(set(set(set(minimal(), "/BG-21/0/BT-99", amount),
                "/BG-21/0/BT-100", base), "/BG-21/0/BT-101", percentage),
                "/BG-21/0/BT-102", "Z"), "/BG-21/0/BT-104", "Handling").build();
    }

    private static SemanticDocument accounting(String inAccountingCurrency) {
        return set(set(set(set(minimal(), "/BT-6", "USD"), "/BT-167", "2"),
                "/BG-22/BT-110", "0"), "/BG-22/BT-111", inAccountingCurrency).build();
    }

    private static void decimals(List<Case> cases) {
        add(cases, "BR-DEC-23", "an amount in euro carries two fraction digits",
                with("/BG-25/0/BT-131", "100"), with("/BG-25/0/BT-131", "100.001"));
        add(cases, "BR-DEC-40", "a VAT category rate carries two fraction digits",
                with("/BG-23/0/BT-119", "19"), with("/BG-23/0/BT-119", "19.001"));
    }

    private static void vat(List<Case> cases) {
        add(cases, "BR-S-01", "a standard rated line needs a standard rated breakdown",
                (category("S", "19")).build(),
                (set(minimal(), "/BG-25/0/BG-30/BT-151", "S")).build());
        add(cases, "BR-S-04", "a standard rated charge obliges a seller tax identifier",
                set(set(charge("5", "S", "Handling"),
                        "/BG-21/0/BT-103", "19"), "/BG-23/0/BT-118", "S").build(),
                set(set(set(set(Documents2026.withoutSellerTaxId(), "/BG-21/0/BT-99", "5"),
                        "/BG-21/0/BT-102", "S"), "/BG-21/0/BT-103", "19"),
                        "/BG-23/0/BT-118", "S").build());
        add(cases, "BR-S-07", "a standard rated charge carries a rate above zero",
                (set(set(charge("5", "S", "Handling"),
                        "/BG-21/0/BT-103", "19"), "/BG-23/0/BT-118", "S")).build(),
                (set(set(charge("5", "S", "Handling"),
                        "/BG-21/0/BT-103", "0"), "/BG-23/0/BT-118", "S")).build());
        add(cases, "BR-S-09", "19 per cent of a taxable amount of 100 is a tax amount of 19",
                standard("19"), standard("18"));
        add(cases, "BR-S-08", "the taxable amount is what the lines of the category come to",
                standard("19"),
                set(set((category("S", "19")), "/BG-23/0/BT-117", "19"),
                        "/BG-23/0/BT-116", "90").build());
        add(cases, "BR-Z-01", "a zero rated line needs a zero rated breakdown",
                minimal().build(), with("/BG-23/0/BT-118", "E"));
        add(cases, "BR-Z-08", "the zero rated taxable amount is what its lines come to",
                minimal().build(), with("/BG-23/0/BT-116", "90"));
        add(cases, "BR-E-01", "an exempt line needs a breakdown with its exemption reason",
                exempt("Exempt", "Exempt"), exempt("Exempt", "Another reason"));
        add(cases, "BR-E-10", "an exempt breakdown states an exemption reason",
                exempt("Exempt", "Exempt"), exempt(null, null));
        add(cases, "BR-IC-12", "an intra-community invoice states a deliver to country",
                set(set(buyerVat((category("K", "0"))),
                        "/BG-13/BG-15/BT-80", "FR"), "/BG-23/0/BT-120", "Intra-community").build(),
                set(buyerVat((category("K", "0"))),
                        "/BG-23/0/BT-120", "Intra-community").build());
        add(cases, "BR-N-02", "a line under a national scheme obliges a seller tax identifier",
                set(set(minimal(), "/BG-25/0/BG-30/BT-151", "N"),
                        "/BG-23/0/BT-118", "N").build(),
                set(set(Documents2026.withoutSellerTaxId(), "/BG-25/0/BG-30/BT-151", "N"),
                        "/BG-23/0/BT-118", "N").build());
        add(cases, "BR-IG-01", "a Canary Islands line needs a breakdown of that category",
                (category("L", "7")).build(),
                (set(minimal(), "/BG-25/0/BG-30/BT-151", "L")).build());
        add(cases, "BR-IG-05", "a Canary Islands line carries a rate of zero or more",
                (category("L", "7")).build(),
                (set(category("L", "7"), "/BG-25/0/BG-30/BT-152", "-1")).build());
        add(cases, "BR-IP-01", "a Ceuta and Melilla line needs a breakdown of that category",
                (category("M", "7")).build(),
                (set(minimal(), "/BG-25/0/BG-30/BT-151", "M")).build());
    }

    /** A standard rated invoice of 100 at 19 per cent, with the tax amount a case varies. */
    private static SemanticDocument standard(String taxAmount) {
        return set(set((category("S", "19")), "/BG-23/0/BT-117", taxAmount),
                "/BG-22/BT-110", taxAmount).build();
    }

    /** An exempt invoice whose breakdown and line state the exemption reason a case gives. */
    private static SemanticDocument exempt(String breakdownReason, String lineReason) {
        SemanticDocument.Builder builder = (category("E", "0"));
        if (breakdownReason != null) {
            set(builder, "/BG-23/0/BT-120", breakdownReason);
        }
        if (lineReason != null) {
            set(builder, "/BG-25/0/BG-30/BT-194", lineReason);
        }
        return builder.build();
    }
}
