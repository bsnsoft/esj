package de.bsnsoft.esj.rules.en16931;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.rules.RuleFinding;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One case per rule: a document the rule is silent on, and the same document with one thing
 * changed, which it speaks on.
 *
 * <p>A pack of two hundred rules in which one rule is written backwards reports nothing on the
 * corpus and nothing on any real invoice, and nobody notices until an invoice that should have
 * been reported is not. The corpus test proves that the pack is quiet about good documents; this
 * one proves that it is not quiet about everything. The two together are what a rule pack owes a
 * reader before anybody compares it with the official artefacts.
 *
 * <p>Each case asserts one thing about one rule. The broken document may well break other rules
 * as well — removing the seller VAT identifier breaks four — and the case says nothing about
 * those: it asserts that the rule under test is absent from the findings of the first document
 * and present in the findings of the second. What anchors the table is a separate assertion, that
 * {@link Invoices#standard()} carries no finding of any rule at all.
 *
 * <p>Two rules have no negative case and cannot have one, which
 * {@code conformance/rules/coverage.md} explains: the business groups they are about carry no
 * business term other than the ones they ask for, and a business group of an ESJ document exists
 * exactly when it carries a value.
 */
class En16931RuleCasesTest {

    /** A rule, a document it holds on, and a document it fails on. */
    record Case(String id, Invoice holds, Invoice fails) {

        @Override
        public String toString() {
            return id;
        }
    }

    /** The rules that cannot fail on a document this engine can be handed. */
    private static final Set<String> WITHOUT_A_NEGATIVE_CASE = Set.of("BR-CO-19", "BR-CO-20");

    private static Set<String> codes(Invoice invoice) {
        Set<String> codes = new LinkedHashSet<>();
        for (RuleFinding finding : Pack.ENGINE.evaluate(invoice.build())) {
            codes.add(finding.code());
        }
        return codes;
    }

    /** Returns every case of the table. */
    static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        integrity(cases);
        conditions(cases);
        decimals(cases);
        codeLists(cases);
        vat(cases);
        return cases;
    }

    private static void add(List<Case> cases, String id, Invoice holds, Invoice fails) {
        cases.add(new Case(id, holds, fails));
    }

    /** Cases for the integrity constraints of clause 6.4.1. */
    private static void integrity(List<Case> cases) {
        add(cases, "BR-01",
                Invoices.standard(),
                Invoices.standard().drop("/BG-2/BT-24"));
        add(cases, "BR-02",
                Invoices.standard(),
                Invoices.standard().drop("/BT-1"));
        add(cases, "BR-03",
                Invoices.standard(),
                Invoices.standard().drop("/BT-2"));
        add(cases, "BR-04",
                Invoices.standard(),
                Invoices.standard().drop("/BT-3"));
        add(cases, "BR-05",
                Invoices.standard(),
                Invoices.standard().drop("/BT-5"));
        add(cases, "BR-06",
                Invoices.standard(),
                Invoices.standard().drop("/BG-4/BT-27"));
        add(cases, "BR-07",
                Invoices.standard(),
                Invoices.standard().drop("/BG-7/BT-44"));
        add(cases, "BR-09",
                Invoices.standard(),
                Invoices.standard().drop("/BG-4/BG-5/BT-40"));
        add(cases, "BR-11",
                Invoices.standard(),
                Invoices.standard().drop("/BG-7/BG-8/BT-55"));
        add(cases, "BR-12",
                Invoices.standard(),
                Invoices.standard().drop("/BG-22/BT-106"));
        add(cases, "BR-13",
                Invoices.standard(),
                Invoices.standard().drop("/BG-22/BT-109"));
        add(cases, "BR-14",
                Invoices.standard(),
                Invoices.standard().drop("/BG-22/BT-112"));
        add(cases, "BR-15",
                Invoices.standard(),
                Invoices.standard().drop("/BG-22/BT-115"));
        add(cases, "BR-21",
                Invoices.standard(),
                Invoices.standard().drop("/BG-25/0/BT-126"));
        add(cases, "BR-22",
                Invoices.standard(),
                Invoices.standard().drop("/BG-25/0/BT-129"));
        add(cases, "BR-23",
                Invoices.standard(),
                Invoices.standard().drop("/BG-25/0/BT-130"));
        add(cases, "BR-24",
                Invoices.standard(),
                Invoices.standard().drop("/BG-25/0/BT-131"));
        add(cases, "BR-25",
                Invoices.standard(),
                Invoices.standard().drop("/BG-25/0/BG-31/BT-153"));
        add(cases, "BR-26",
                Invoices.standard(),
                Invoices.standard().drop("/BG-25/0/BG-29/BT-146"));
        add(cases, "BR-45",
                Invoices.standard(),
                Invoices.standard().drop("/BG-23/0/BT-116"));
        add(cases, "BR-46",
                Invoices.standard(),
                Invoices.standard().drop("/BG-23/0/BT-117"));
        add(cases, "BR-47",
                Invoices.standard(),
                Invoices.standard().drop("/BG-23/0/BT-118"));
        add(cases, "BR-48",
                Invoices.standard(),
                Invoices.standard().drop("/BG-23/0/BT-119"));
        add(cases, "BR-08",
                Invoices.standard(),
                Invoices.standard().dropUnder("/BG-4/BG-5"));
        add(cases, "BR-10",
                Invoices.standard(),
                Invoices.standard().dropUnder("/BG-7/BG-8"));
        add(cases, "BR-16",
                Invoices.standard(),
                Invoices.standard().dropUnder("/BG-25"));
        add(cases, "BR-17",
                Invoices.payee(),
                Invoices.payee().drop("/BG-10/BT-59"));
        add(cases, "BR-18",
                Invoices.taxRepresentative(),
                Invoices.taxRepresentative().drop("/BG-11/BT-62"));
        add(cases, "BR-19",
                Invoices.taxRepresentative(),
                Invoices.taxRepresentative().dropUnder("/BG-11/BG-12"));
        add(cases, "BR-20",
                Invoices.taxRepresentative(),
                Invoices.taxRepresentative().drop("/BG-11/BG-12/BT-69"));
        add(cases, "BR-27",
                Invoices.standard(),
                Invoices.standard().put("/BG-25/0/BG-29/BT-146", "-1"));
        add(cases, "BR-28",
                Invoices.standard().put("/BG-25/0/BG-29/BT-148", "120"),
                Invoices.standard().put("/BG-25/0/BG-29/BT-148", "-120"));
        add(cases, "BR-29",
                Invoices.delivery(),
                Invoices.delivery().put("/BG-13/BG-14/BT-74", "2025-12-01"));
        add(cases, "BR-30",
                Invoices.linePeriod(),
                Invoices.linePeriod().put("/BG-25/0/BG-26/BT-135", "2025-12-01"));
        add(cases, "BR-31",
                Invoices.allowance(),
                Invoices.allowance().drop("/BG-20/0/BT-92"));
        add(cases, "BR-32",
                Invoices.allowance(),
                Invoices.allowance().drop("/BG-20/0/BT-95"));
        add(cases, "BR-33",
                Invoices.allowance(),
                Invoices.allowance().drop("/BG-20/0/BT-97"));
        add(cases, "BR-36",
                Invoices.charge(),
                Invoices.charge().drop("/BG-21/0/BT-99"));
        add(cases, "BR-37",
                Invoices.charge(),
                Invoices.charge().drop("/BG-21/0/BT-102"));
        add(cases, "BR-38",
                Invoices.charge(),
                Invoices.charge().drop("/BG-21/0/BT-104"));
        add(cases, "BR-41",
                Invoices.lineAllowance(),
                Invoices.lineAllowance().drop("/BG-25/0/BG-27/0/BT-136"));
        add(cases, "BR-42",
                Invoices.lineAllowance(),
                Invoices.lineAllowance().drop("/BG-25/0/BG-27/0/BT-139"));
        add(cases, "BR-43",
                Invoices.lineCharge(),
                Invoices.lineCharge().drop("/BG-25/0/BG-28/0/BT-141"));
        add(cases, "BR-44",
                Invoices.lineCharge(),
                Invoices.lineCharge().drop("/BG-25/0/BG-28/0/BT-144"));
        add(cases, "BR-49",
                Invoices.payment(),
                Invoices.payment().drop("/BG-16/BT-81"));
        add(cases, "BR-50",
                Invoices.payment(),
                Invoices.payment().drop("/BG-16/BG-17/0/BT-84"));
        add(cases, "BR-51",
                Invoices.card(),
                Invoices.card().put("/BG-16/BG-18/BT-87", "4111111111111111"));
        add(cases, "BR-52",
                Invoices.attachment(),
                Invoices.attachment().drop("/BG-24/0/BT-122"));
        add(cases, "BR-53",
                Invoices.standard().put("/BT-6", "USD").put("/BG-22/BT-111", "17"),
                Invoices.standard().put("/BT-6", "USD"));
        add(cases, "BR-54",
                Invoices.attributes(),
                Invoices.attributes().drop("/BG-25/0/BG-31/BG-32/0/BT-161"));
        add(cases, "BR-55",
                Invoices.preceding(),
                Invoices.preceding().drop("/BG-3/0/BT-25"));
        add(cases, "BR-56",
                Invoices.taxRepresentative(),
                Invoices.taxRepresentative().drop("/BG-11/BT-63"));
        add(cases, "BR-57",
                Invoices.delivery(),
                Invoices.delivery().drop("/BG-13/BG-15/BT-80"));
        add(cases, "BR-61",
                Invoices.payment(),
                Invoices.payment().dropUnder("/BG-16/BG-17"));
        add(cases, "BR-62",
                Invoices.standard().scheme("/BG-4/BT-34", "seller@example.invalid", "EM"),
                Invoices.standard().put("/BG-4/BT-34", "seller@example.invalid"));
        add(cases, "BR-63",
                Invoices.standard().scheme("/BG-7/BT-49", "buyer@example.invalid", "EM"),
                Invoices.standard().put("/BG-7/BT-49", "buyer@example.invalid"));
        add(cases, "BR-64",
                Invoices.standard().scheme("/BG-25/0/BG-31/BT-157", "4012345678901", "0160"),
                Invoices.standard().put("/BG-25/0/BG-31/BT-157", "4012345678901"));
        add(cases, "BR-65",
                Invoices.standard().scheme("/BG-25/0/BG-31/BT-158/0", "65434568", "TST"),
                Invoices.standard().put("/BG-25/0/BG-31/BT-158/0", "65434568"));
    }

    /** Cases for the conditions of clause 6.4.2. */
    private static void conditions(List<Case> cases) {
        add(cases, "BR-CO-04",
                Invoices.standard(),
                Invoices.standard().drop("/BG-25/0/BG-30/BT-151"));
        add(cases, "BR-CO-25",
                Invoices.standard(),
                Invoices.standard().drop("/BT-9"));
        add(cases, "BR-CO-26",
                Invoices.standard(),
                Invoices.standard().drop("/BG-4/BT-31"));
        add(cases, "BR-CO-21",
                Invoices.allowance(),
                Invoices.allowance().drop("/BG-20/0/BT-97"));
        add(cases, "BR-CO-22",
                Invoices.charge(),
                Invoices.charge().drop("/BG-21/0/BT-104"));
        add(cases, "BR-CO-23",
                Invoices.lineAllowance(),
                Invoices.lineAllowance().drop("/BG-25/0/BG-27/0/BT-139"));
        add(cases, "BR-CO-24",
                Invoices.lineCharge(),
                Invoices.lineCharge().drop("/BG-25/0/BG-28/0/BT-144"));
        add(cases, "BR-CO-03",
                Invoices.standard().put("/BT-7", "2026-01-15"),
                Invoices.standard().put("/BT-7", "2026-01-15").put("/BT-8", "3"));
        add(cases, "BR-CO-09",
                Invoices.standard(),
                Invoices.standard().put("/BG-4/BT-31", "QQ123456789"));
        add(cases, "BR-CO-10",
                Invoices.standard(),
                Invoices.standard().put("/BG-22/BT-106", "99"));
        add(cases, "BR-CO-11",
                Invoices.allowance(),
                Invoices.allowance().put("/BG-22/BT-107", "9"));
        add(cases, "BR-CO-12",
                Invoices.charge(),
                Invoices.charge().put("/BG-22/BT-108", "9"));
        add(cases, "BR-CO-13",
                Invoices.standard(),
                Invoices.standard().put("/BG-22/BT-109", "99"));
        add(cases, "BR-CO-14",
                Invoices.standard(),
                Invoices.standard().put("/BG-22/BT-110", "18"));
        add(cases, "BR-CO-15",
                Invoices.standard(),
                Invoices.standard().put("/BG-22/BT-112", "118"));
        add(cases, "BR-CO-16",
                Invoices.standard(),
                Invoices.standard().put("/BG-22/BT-115", "118"));
        add(cases, "BR-CO-17",
                Invoices.standard(),
                Invoices.standard().put("/BG-23/0/BT-117", "25"));
        add(cases, "BR-CO-18",
                Invoices.standard(),
                Invoices.standard().dropUnder("/BG-23"));
    }

    /** Cases for the allowed number of decimals of clause 6.5.12. */
    private static void decimals(List<Case> cases) {
        add(cases, "BR-DEC-01",
                Invoices.allowance().put("/BG-20/0/BT-92", "10.01"),
                Invoices.allowance().put("/BG-20/0/BT-92", "10.001"));
        add(cases, "BR-DEC-02",
                Invoices.allowance().put("/BG-20/0/BT-93", "100.01"),
                Invoices.allowance().put("/BG-20/0/BT-93", "100.001"));
        add(cases, "BR-DEC-05",
                Invoices.charge().put("/BG-21/0/BT-99", "10.01"),
                Invoices.charge().put("/BG-21/0/BT-99", "10.001"));
        add(cases, "BR-DEC-06",
                Invoices.charge().put("/BG-21/0/BT-100", "100.01"),
                Invoices.charge().put("/BG-21/0/BT-100", "100.001"));
        add(cases, "BR-DEC-09",
                Invoices.standard().put("/BG-22/BT-106", "100.01"),
                Invoices.standard().put("/BG-22/BT-106", "100.001"));
        add(cases, "BR-DEC-10",
                Invoices.standard().put("/BG-22/BT-107", "10.01"),
                Invoices.standard().put("/BG-22/BT-107", "10.001"));
        add(cases, "BR-DEC-11",
                Invoices.standard().put("/BG-22/BT-108", "10.01"),
                Invoices.standard().put("/BG-22/BT-108", "10.001"));
        add(cases, "BR-DEC-12",
                Invoices.standard().put("/BG-22/BT-109", "100.01"),
                Invoices.standard().put("/BG-22/BT-109", "100.001"));
        add(cases, "BR-DEC-13",
                Invoices.standard().put("/BG-22/BT-110", "19.01"),
                Invoices.standard().put("/BG-22/BT-110", "19.001"));
        add(cases, "BR-DEC-14",
                Invoices.standard().put("/BG-22/BT-112", "119.01"),
                Invoices.standard().put("/BG-22/BT-112", "119.001"));
        add(cases, "BR-DEC-15",
                Invoices.standard().put("/BG-22/BT-111", "17.01"),
                Invoices.standard().put("/BG-22/BT-111", "17.001"));
        add(cases, "BR-DEC-16",
                Invoices.standard().put("/BG-22/BT-113", "10.01"),
                Invoices.standard().put("/BG-22/BT-113", "10.001"));
        add(cases, "BR-DEC-17",
                Invoices.standard().put("/BG-22/BT-114", "0.01"),
                Invoices.standard().put("/BG-22/BT-114", "0.001"));
        add(cases, "BR-DEC-18",
                Invoices.standard().put("/BG-22/BT-115", "119.01"),
                Invoices.standard().put("/BG-22/BT-115", "119.001"));
        add(cases, "BR-DEC-19",
                Invoices.standard().put("/BG-23/0/BT-116", "100.01"),
                Invoices.standard().put("/BG-23/0/BT-116", "100.001"));
        add(cases, "BR-DEC-20",
                Invoices.standard().put("/BG-23/0/BT-117", "19.01"),
                Invoices.standard().put("/BG-23/0/BT-117", "19.001"));
        add(cases, "BR-DEC-23",
                Invoices.standard().put("/BG-25/0/BT-131", "100.01"),
                Invoices.standard().put("/BG-25/0/BT-131", "100.001"));
        add(cases, "BR-DEC-24",
                Invoices.lineAllowance().put("/BG-25/0/BG-27/0/BT-136", "10.01"),
                Invoices.lineAllowance().put("/BG-25/0/BG-27/0/BT-136", "10.001"));
        add(cases, "BR-DEC-25",
                Invoices.lineAllowance().put("/BG-25/0/BG-27/0/BT-137", "110.01"),
                Invoices.lineAllowance().put("/BG-25/0/BG-27/0/BT-137", "110.001"));
        add(cases, "BR-DEC-27",
                Invoices.lineCharge().put("/BG-25/0/BG-28/0/BT-141", "10.01"),
                Invoices.lineCharge().put("/BG-25/0/BG-28/0/BT-141", "10.001"));
        add(cases, "BR-DEC-28",
                Invoices.lineCharge().put("/BG-25/0/BG-28/0/BT-142", "90.01"),
                Invoices.lineCharge().put("/BG-25/0/BG-28/0/BT-142", "90.001"));
    }

    /** Cases for the code lists of clause 6.3. */
    private static void codeLists(List<Case> cases) {
        add(cases, "BR-CL-01",
                Invoices.standard(),
                Invoices.standard().put("/BT-3", "999"));
        add(cases, "BR-CL-04",
                Invoices.standard(),
                Invoices.standard().put("/BT-5", "QQQ"));
        add(cases, "BR-CL-05",
                Invoices.standard().put("/BT-6", "USD"),
                Invoices.standard().put("/BT-6", "QQQ"));
        add(cases, "BR-CL-06",
                Invoices.standard().put("/BT-8", "35"),
                Invoices.standard().put("/BT-8", "5"));
        add(cases, "BR-CL-08",
                Invoices.note(),
                Invoices.note().put("/BG-1/0/BT-21", "ZZ9"));
        add(cases, "BR-CL-14",
                Invoices.standard(),
                Invoices.standard().put("/BG-4/BG-5/BT-40", "QQ"));
        add(cases, "BR-CL-15",
                Invoices.standard().put("/BG-25/0/BG-31/BT-159", "DE"),
                Invoices.standard().put("/BG-25/0/BG-31/BT-159", "QQ"));
        add(cases, "BR-CL-16",
                Invoices.payment(),
                Invoices.payment().put("/BG-16/BT-81", "999"));
        add(cases, "BR-CL-17",
                Invoices.allowance().put("/BG-20/0/BT-95", "S"),
                Invoices.allowance().put("/BG-20/0/BT-95", "Q"));
        add(cases, "BR-CL-18",
                Invoices.standard(),
                Invoices.standard().put("/BG-25/0/BG-30/BT-151", "Q"));
        add(cases, "BR-CL-19",
                Invoices.allowance().put("/BG-20/0/BT-98", "95"),
                Invoices.allowance().put("/BG-20/0/BT-98", "999"));
        add(cases, "BR-CL-20",
                Invoices.charge().put("/BG-21/0/BT-105", "FC"),
                Invoices.charge().put("/BG-21/0/BT-105", "ZZ99"));
        add(cases, "BR-CL-22",
                Invoices.zeroRated("E").put("/BG-23/0/BT-121", "VATEX-EU-132"),
                Invoices.zeroRated("E").put("/BG-23/0/BT-121", "VATEX-QQ-999"));
        add(cases, "BR-CL-23",
                Invoices.standard(),
                Invoices.standard().put("/BG-25/0/BT-130", "ZZ9"));
        add(cases, "BR-CL-25",
                Invoices.standard().scheme("/BG-4/BT-34", "seller@example.invalid", "EM"),
                Invoices.standard().scheme("/BG-4/BT-34", "seller@example.invalid", "9999"));
        add(cases, "BR-CL-07",
                Invoices.standard().scheme("/BT-18", "AB-4711", "AAJ"),
                Invoices.standard().scheme("/BT-18", "AB-4711", "ZZ9"));
        add(cases, "BR-CL-10",
                Invoices.standard().scheme("/BG-4/BT-29/0", "4098952000005", "0088"),
                Invoices.standard().scheme("/BG-4/BT-29/0", "4098952000005", "9999"));
        add(cases, "BR-CL-11",
                Invoices.standard().scheme("/BG-4/BT-30", "HRB 12345", "0198"),
                Invoices.standard().scheme("/BG-4/BT-30", "HRB 12345", "9999"));
        add(cases, "BR-CL-13",
                Invoices.standard().scheme("/BG-25/0/BG-31/BT-158/0", "65434568", "SRV"),
                Invoices.standard().scheme("/BG-25/0/BG-31/BT-158/0", "65434568", "ZZ9"));
        add(cases, "BR-CL-21",
                Invoices.standard().scheme("/BG-25/0/BG-31/BT-157", "4098952000005", "0088"),
                Invoices.standard().scheme("/BG-25/0/BG-31/BT-157", "4098952000005", "9999"));
        add(cases, "BR-CL-24",
                Invoices.attachment("application/pdf"),
                Invoices.attachment("application/zip"));
        add(cases, "BR-CL-26",
                Invoices.delivery().scheme("/BG-13/BT-71", "83745498753497", "0088"),
                Invoices.delivery().scheme("/BG-13/BT-71", "83745498753497", "9999"));
    }

    /** Cases for the VAT categories of clause 6.4.3. */
    private static void vat(List<Case> cases) {
        add(cases, "BR-S-01",
                Invoices.standard(),
                Invoices.standard().put("/BG-23/0/BT-118", "E"));
        add(cases, "BR-S-02",
                Invoices.standard(),
                Invoices.standard().drop("/BG-4/BT-31"));
        add(cases, "BR-S-03",
                Invoices.withAllowance(Invoices.standard(), "S", "19"),
                Invoices.withAllowance(Invoices.standard(), "S", "19").drop("/BG-4/BT-31"));
        add(cases, "BR-S-04",
                Invoices.withCharge(Invoices.standard(), "S", "19"),
                Invoices.withCharge(Invoices.standard(), "S", "19").drop("/BG-4/BT-31"));
        add(cases, "BR-S-05",
                Invoices.standard(),
                Invoices.standard().put("/BG-25/0/BG-30/BT-152", "0"));
        add(cases, "BR-S-06",
                Invoices.withAllowance(Invoices.standard(), "S", "19"),
                Invoices.withAllowance(Invoices.standard(), "S", "0"));
        add(cases, "BR-S-07",
                Invoices.withCharge(Invoices.standard(), "S", "19"),
                Invoices.withCharge(Invoices.standard(), "S", "0"));
        add(cases, "BR-S-08",
                Invoices.standard(),
                Invoices.standard().put("/BG-23/0/BT-116", "50"));
        add(cases, "BR-S-09",
                Invoices.standard(),
                Invoices.standard().put("/BG-23/0/BT-117", "50"));
        add(cases, "BR-S-10",
                Invoices.standard(),
                Invoices.standard().put("/BG-23/0/BT-120", "No VAT is levied."));
        add(cases, "BR-Z-01",
                Invoices.zeroRatedWithoutReason("Z"),
                Invoices.zeroRatedWithoutReason("Z").put("/BG-23/0/BT-118", "E"));
        add(cases, "BR-Z-02",
                Invoices.zeroRatedWithoutReason("Z"),
                Invoices.zeroRatedWithoutReason("Z").drop("/BG-4/BT-31"));
        add(cases, "BR-Z-03",
                Invoices.withAllowance(Invoices.zeroRatedWithoutReason("Z"), "Z", "0"),
                Invoices.withAllowance(Invoices.zeroRatedWithoutReason("Z"), "Z", "0").drop("/BG-4/BT-31"));
        add(cases, "BR-Z-04",
                Invoices.withCharge(Invoices.zeroRatedWithoutReason("Z"), "Z", "0"),
                Invoices.withCharge(Invoices.zeroRatedWithoutReason("Z"), "Z", "0").drop("/BG-4/BT-31"));
        add(cases, "BR-Z-05",
                Invoices.zeroRatedWithoutReason("Z"),
                Invoices.zeroRatedWithoutReason("Z").put("/BG-25/0/BG-30/BT-152", "19"));
        add(cases, "BR-Z-06",
                Invoices.withAllowance(Invoices.zeroRatedWithoutReason("Z"), "Z", "0"),
                Invoices.withAllowance(Invoices.zeroRatedWithoutReason("Z"), "Z", "19"));
        add(cases, "BR-Z-07",
                Invoices.withCharge(Invoices.zeroRatedWithoutReason("Z"), "Z", "0"),
                Invoices.withCharge(Invoices.zeroRatedWithoutReason("Z"), "Z", "19"));
        add(cases, "BR-Z-08",
                Invoices.zeroRatedWithoutReason("Z"),
                Invoices.zeroRatedWithoutReason("Z").put("/BG-23/0/BT-116", "50"));
        add(cases, "BR-Z-09",
                Invoices.zeroRatedWithoutReason("Z"),
                Invoices.zeroRatedWithoutReason("Z").put("/BG-23/0/BT-117", "50"));
        add(cases, "BR-Z-10",
                Invoices.zeroRatedWithoutReason("Z"),
                Invoices.zeroRatedWithoutReason("Z").put("/BG-23/0/BT-120", "No VAT is levied."));
        add(cases, "BR-E-01",
                Invoices.zeroRated("E"),
                Invoices.zeroRated("E").put("/BG-23/0/BT-118", "Z"));
        add(cases, "BR-E-02",
                Invoices.zeroRated("E"),
                Invoices.zeroRated("E").drop("/BG-4/BT-31"));
        add(cases, "BR-E-03",
                Invoices.withAllowance(Invoices.zeroRated("E"), "E", "0"),
                Invoices.withAllowance(Invoices.zeroRated("E"), "E", "0").drop("/BG-4/BT-31"));
        add(cases, "BR-E-04",
                Invoices.withCharge(Invoices.zeroRated("E"), "E", "0"),
                Invoices.withCharge(Invoices.zeroRated("E"), "E", "0").drop("/BG-4/BT-31"));
        add(cases, "BR-E-05",
                Invoices.zeroRated("E"),
                Invoices.zeroRated("E").put("/BG-25/0/BG-30/BT-152", "19"));
        add(cases, "BR-E-06",
                Invoices.withAllowance(Invoices.zeroRated("E"), "E", "0"),
                Invoices.withAllowance(Invoices.zeroRated("E"), "E", "19"));
        add(cases, "BR-E-07",
                Invoices.withCharge(Invoices.zeroRated("E"), "E", "0"),
                Invoices.withCharge(Invoices.zeroRated("E"), "E", "19"));
        add(cases, "BR-E-08",
                Invoices.zeroRated("E"),
                Invoices.zeroRated("E").put("/BG-23/0/BT-116", "50"));
        add(cases, "BR-E-09",
                Invoices.zeroRated("E"),
                Invoices.zeroRated("E").put("/BG-23/0/BT-117", "50"));
        add(cases, "BR-E-10",
                Invoices.zeroRated("E"),
                Invoices.zeroRated("E").drop("/BG-23/0/BT-120", "/BG-23/0/BT-121"));
        add(cases, "BR-AE-01",
                Invoices.reverseCharge(),
                Invoices.reverseCharge().put("/BG-23/0/BT-118", "E"));
        add(cases, "BR-AE-02",
                Invoices.reverseCharge(),
                Invoices.reverseCharge().drop("/BG-4/BT-31"));
        add(cases, "BR-AE-03",
                Invoices.withAllowance(Invoices.reverseCharge(), "AE", "0"),
                Invoices.withAllowance(Invoices.reverseCharge(), "AE", "0").drop("/BG-4/BT-31"));
        add(cases, "BR-AE-04",
                Invoices.withCharge(Invoices.reverseCharge(), "AE", "0"),
                Invoices.withCharge(Invoices.reverseCharge(), "AE", "0").drop("/BG-4/BT-31"));
        add(cases, "BR-AE-05",
                Invoices.reverseCharge(),
                Invoices.reverseCharge().put("/BG-25/0/BG-30/BT-152", "19"));
        add(cases, "BR-AE-06",
                Invoices.withAllowance(Invoices.reverseCharge(), "AE", "0"),
                Invoices.withAllowance(Invoices.reverseCharge(), "AE", "19"));
        add(cases, "BR-AE-07",
                Invoices.withCharge(Invoices.reverseCharge(), "AE", "0"),
                Invoices.withCharge(Invoices.reverseCharge(), "AE", "19"));
        add(cases, "BR-AE-08",
                Invoices.reverseCharge(),
                Invoices.reverseCharge().put("/BG-23/0/BT-116", "50"));
        add(cases, "BR-AE-09",
                Invoices.reverseCharge(),
                Invoices.reverseCharge().put("/BG-23/0/BT-117", "50"));
        add(cases, "BR-AE-10",
                Invoices.reverseCharge(),
                Invoices.reverseCharge().drop("/BG-23/0/BT-120", "/BG-23/0/BT-121"));
        add(cases, "BR-IC-01",
                Invoices.intraCommunity(),
                Invoices.intraCommunity().put("/BG-23/0/BT-118", "E"));
        add(cases, "BR-IC-02",
                Invoices.intraCommunity(),
                Invoices.intraCommunity().drop("/BG-4/BT-31"));
        add(cases, "BR-IC-03",
                Invoices.withAllowance(Invoices.intraCommunity(), "K", "0"),
                Invoices.withAllowance(Invoices.intraCommunity(), "K", "0").drop("/BG-4/BT-31"));
        add(cases, "BR-IC-04",
                Invoices.withCharge(Invoices.intraCommunity(), "K", "0"),
                Invoices.withCharge(Invoices.intraCommunity(), "K", "0").drop("/BG-4/BT-31"));
        add(cases, "BR-IC-05",
                Invoices.intraCommunity(),
                Invoices.intraCommunity().put("/BG-25/0/BG-30/BT-152", "19"));
        add(cases, "BR-IC-06",
                Invoices.withAllowance(Invoices.intraCommunity(), "K", "0"),
                Invoices.withAllowance(Invoices.intraCommunity(), "K", "19"));
        add(cases, "BR-IC-07",
                Invoices.withCharge(Invoices.intraCommunity(), "K", "0"),
                Invoices.withCharge(Invoices.intraCommunity(), "K", "19"));
        add(cases, "BR-IC-08",
                Invoices.intraCommunity(),
                Invoices.intraCommunity().put("/BG-23/0/BT-116", "50"));
        add(cases, "BR-IC-09",
                Invoices.intraCommunity(),
                Invoices.intraCommunity().put("/BG-23/0/BT-117", "50"));
        add(cases, "BR-IC-10",
                Invoices.intraCommunity(),
                Invoices.intraCommunity().drop("/BG-23/0/BT-120", "/BG-23/0/BT-121"));
        add(cases, "BR-G-01",
                Invoices.zeroRated("G"),
                Invoices.zeroRated("G").put("/BG-23/0/BT-118", "E"));
        add(cases, "BR-G-02",
                Invoices.zeroRated("G"),
                Invoices.zeroRated("G").drop("/BG-4/BT-31"));
        add(cases, "BR-G-03",
                Invoices.withAllowance(Invoices.zeroRated("G"), "G", "0"),
                Invoices.withAllowance(Invoices.zeroRated("G"), "G", "0").drop("/BG-4/BT-31"));
        add(cases, "BR-G-04",
                Invoices.withCharge(Invoices.zeroRated("G"), "G", "0"),
                Invoices.withCharge(Invoices.zeroRated("G"), "G", "0").drop("/BG-4/BT-31"));
        add(cases, "BR-G-05",
                Invoices.zeroRated("G"),
                Invoices.zeroRated("G").put("/BG-25/0/BG-30/BT-152", "19"));
        add(cases, "BR-G-06",
                Invoices.withAllowance(Invoices.zeroRated("G"), "G", "0"),
                Invoices.withAllowance(Invoices.zeroRated("G"), "G", "19"));
        add(cases, "BR-G-07",
                Invoices.withCharge(Invoices.zeroRated("G"), "G", "0"),
                Invoices.withCharge(Invoices.zeroRated("G"), "G", "19"));
        add(cases, "BR-G-08",
                Invoices.zeroRated("G"),
                Invoices.zeroRated("G").put("/BG-23/0/BT-116", "50"));
        add(cases, "BR-G-09",
                Invoices.zeroRated("G"),
                Invoices.zeroRated("G").put("/BG-23/0/BT-117", "50"));
        add(cases, "BR-G-10",
                Invoices.zeroRated("G"),
                Invoices.zeroRated("G").drop("/BG-23/0/BT-120", "/BG-23/0/BT-121"));
        add(cases, "BR-O-01",
                Invoices.notSubject(),
                Invoices.notSubject().put("/BG-23/0/BT-118", "E"));
        add(cases, "BR-O-02",
                Invoices.notSubject(),
                Invoices.notSubject().put("/BG-4/BT-31", "DE123456789"));
        add(cases, "BR-O-03",
                Invoices.withAllowance(Invoices.notSubject(), "O", null),
                Invoices.withAllowance(Invoices.notSubject(), "O", null).put("/BG-4/BT-31", "DE123456789"));
        add(cases, "BR-O-04",
                Invoices.withCharge(Invoices.notSubject(), "O", null),
                Invoices.withCharge(Invoices.notSubject(), "O", null).put("/BG-4/BT-31", "DE123456789"));
        add(cases, "BR-O-05",
                Invoices.notSubject(),
                Invoices.notSubject().put("/BG-25/0/BG-30/BT-152", "0"));
        add(cases, "BR-O-06",
                Invoices.withAllowance(Invoices.notSubject(), "O", null),
                Invoices.withAllowance(Invoices.notSubject(), "O", "0"));
        add(cases, "BR-O-07",
                Invoices.withCharge(Invoices.notSubject(), "O", null),
                Invoices.withCharge(Invoices.notSubject(), "O", "0"));
        add(cases, "BR-O-08",
                Invoices.notSubject(),
                Invoices.notSubject().put("/BG-23/0/BT-116", "50"));
        add(cases, "BR-O-09",
                Invoices.notSubject(),
                Invoices.notSubject().put("/BG-23/0/BT-117", "50"));
        add(cases, "BR-O-10",
                Invoices.notSubject(),
                Invoices.notSubject().drop("/BG-23/0/BT-120", "/BG-23/0/BT-121"));
        add(cases, "BR-AF-01",
                Invoices.regional("L"),
                Invoices.regional("L").put("/BG-23/0/BT-118", "E"));
        add(cases, "BR-AF-02",
                Invoices.regional("L"),
                Invoices.regional("L").drop("/BG-4/BT-31"));
        add(cases, "BR-AF-03",
                Invoices.withAllowance(Invoices.regional("L"), "L", "7"),
                Invoices.withAllowance(Invoices.regional("L"), "L", "7").drop("/BG-4/BT-31"));
        add(cases, "BR-AF-04",
                Invoices.withCharge(Invoices.regional("L"), "L", "7"),
                Invoices.withCharge(Invoices.regional("L"), "L", "7").drop("/BG-4/BT-31"));
        add(cases, "BR-AF-05",
                Invoices.regional("L"),
                Invoices.regional("L").put("/BG-25/0/BG-30/BT-152", "-1"));
        add(cases, "BR-AF-06",
                Invoices.withAllowance(Invoices.regional("L"), "L", "7"),
                Invoices.withAllowance(Invoices.regional("L"), "L", "-1"));
        add(cases, "BR-AF-07",
                Invoices.withCharge(Invoices.regional("L"), "L", "7"),
                Invoices.withCharge(Invoices.regional("L"), "L", "-1"));
        add(cases, "BR-AF-08",
                Invoices.regional("L"),
                Invoices.regional("L").put("/BG-23/0/BT-116", "50"));
        add(cases, "BR-AF-09",
                Invoices.regional("L"),
                Invoices.regional("L").put("/BG-23/0/BT-117", "50"));
        add(cases, "BR-AF-10",
                Invoices.regional("L"),
                Invoices.regional("L").put("/BG-23/0/BT-120", "No VAT is levied."));
        add(cases, "BR-AG-01",
                Invoices.regional("M"),
                Invoices.regional("M").put("/BG-23/0/BT-118", "E"));
        add(cases, "BR-AG-02",
                Invoices.regional("M"),
                Invoices.regional("M").drop("/BG-4/BT-31"));
        add(cases, "BR-AG-03",
                Invoices.withAllowance(Invoices.regional("M"), "M", "7"),
                Invoices.withAllowance(Invoices.regional("M"), "M", "7").drop("/BG-4/BT-31"));
        add(cases, "BR-AG-04",
                Invoices.withCharge(Invoices.regional("M"), "M", "7"),
                Invoices.withCharge(Invoices.regional("M"), "M", "7").drop("/BG-4/BT-31"));
        add(cases, "BR-AG-05",
                Invoices.regional("M"),
                Invoices.regional("M").put("/BG-25/0/BG-30/BT-152", "-1"));
        add(cases, "BR-AG-06",
                Invoices.withAllowance(Invoices.regional("M"), "M", "7"),
                Invoices.withAllowance(Invoices.regional("M"), "M", "-1"));
        add(cases, "BR-AG-07",
                Invoices.withCharge(Invoices.regional("M"), "M", "7"),
                Invoices.withCharge(Invoices.regional("M"), "M", "-1"));
        add(cases, "BR-AG-08",
                Invoices.regional("M"),
                Invoices.regional("M").put("/BG-23/0/BT-116", "50"));
        add(cases, "BR-AG-09",
                Invoices.regional("M"),
                Invoices.regional("M").put("/BG-23/0/BT-117", "50"));
        add(cases, "BR-AG-10",
                Invoices.regional("M"),
                Invoices.regional("M").put("/BG-23/0/BT-120", "No VAT is levied."));
        add(cases, "BR-IC-11",
                Invoices.intraCommunity(),
                Invoices.intraCommunity().drop("/BG-13/BT-72"));
        add(cases, "BR-IC-12",
                Invoices.intraCommunity(),
                Invoices.intraCommunity().drop("/BG-13/BG-15/BT-80"));
        add(cases, "BR-O-11",
                Invoices.notSubject(),
                Invoices.notSubject().put("/BG-23/1/BT-116", "0").put("/BG-23/1/BT-117", "0").put("/BG-23/1/BT-118", "E").put("/BG-23/1/BT-120", "Exempt."));
        add(cases, "BR-O-12",
                Invoices.notSubject(),
                Invoices.notSubject().put("/BG-25/1/BT-126", "2").put("/BG-25/1/BT-129", "1").put("/BG-25/1/BT-130", "C62").put("/BG-25/1/BT-131", "0").put("/BG-25/1/BG-29/BT-146", "0").put("/BG-25/1/BG-30/BT-151", "S").put("/BG-25/1/BG-31/BT-153", "Other service"));
        add(cases, "BR-O-13",
                Invoices.notSubject(),
                Invoices.withAllowance(Invoices.notSubject(), "S", "19"));
        add(cases, "BR-O-14",
                Invoices.notSubject(),
                Invoices.withCharge(Invoices.notSubject(), "S", "19"));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void theRuleIsSilentOnTheSoundDocumentAndSpeaksOnTheBrokenOne(Case testCase) {
        assertFalse(codes(testCase.holds()).contains(testCase.id()),
                testCase.id() + " fired on the document it holds on");
        assertTrue(codes(testCase.fails()).contains(testCase.id()),
                testCase.id() + " did not fire on the document it fails on");
    }

    /**
     * A finding says where its rule looked, and every rule of this pack does.
     *
     * <p>It is asserted here rather than left to the shape of the finding record, because the
     * fifteen VAT rules share one join of the invoice lines, the allowances, the charges and
     * the breakdowns: the view is computed by whichever of them is evaluated first and handed
     * to the rest. If the reads that made it did not travel with it, that one rule would
     * report the paths of all of them and the other fourteen would report none, which is
     * exactly the kind of defect no assertion about one rule catches.
     */
    @ParameterizedTest
    @MethodSource("cases")
    void theFindingSaysWhereTheRuleLooked(Case testCase) {
        for (RuleFinding finding : Pack.ENGINE.evaluate(testCase.fails().build())) {
            if (finding.code().equals(testCase.id())) {
                assertFalse(finding.paths().isEmpty(),
                        testCase.id() + " reported without naming a path it read");
            }
        }
    }

    @Test
    void everyRuleOfThePackHasACaseOrAReasonForNotHavingOne() {
        Set<String> withCase = new TreeSet<>();
        for (Case one : cases()) {
            assertTrue(withCase.add(one.id()), one.id() + " has two cases");
        }
        withCase.addAll(WITHOUT_A_NEGATIVE_CASE);

        assertEquals(new TreeSet<>(Pack.ENGINE.ruleIds()), withCase);
    }

    @Test
    void theStandardInvoiceCarriesNoFindingAtAll() {
        assertEquals(List.of(), Pack.ENGINE.evaluate(Invoices.standard().build()));
    }
}
