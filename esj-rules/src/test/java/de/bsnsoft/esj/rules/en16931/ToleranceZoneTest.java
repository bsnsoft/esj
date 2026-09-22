package de.bsnsoft.esj.rules.en16931;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.rules.RuleFinding;
import de.bsnsoft.esj.rules.RuleSeverity;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What the pack says inside the tolerance only one official artefact of release 1.3.16
 * grants.
 *
 * <p>Ten rules compare a stated amount with a figure the invoice implies, and for nine of
 * them the two artefacts do not ask the same closeness: one admits a difference below one
 * unit of the invoice currency, or a difference of exactly one unit, and the other does not.
 * A pack over business terms cannot be two things at once, so it faults what both artefacts
 * fault and warns inside the zone only one of them grants, naming the syntax whose artefact
 * faults the figure there.
 *
 * <p>Four points are measured per rule: no difference at all, a difference inside the zone,
 * the edge, and beyond it. The same four points are measured on real instances of both
 * syntaxes against the artefacts themselves in {@code OracleTest}; here the arithmetic is
 * measured on a document built in memory, where the figures are plain.
 */
class ToleranceZoneTest {

    private static Optional<RuleFinding> finding(Invoice invoice, String id) {
        return Pack.ENGINE.evaluate(invoice.build()).stream()
                .filter(finding -> finding.code().equals(id))
                .findFirst();
    }

    private static void silent(Invoice invoice, String id) {
        assertEquals(Optional.empty(), finding(invoice, id).map(RuleFinding::toString));
    }

    private static void warns(Invoice invoice, String id, String strict) {
        RuleFinding finding = finding(invoice, id).orElseThrow(
                () -> new AssertionError(id + " says nothing inside the zone"));
        assertEquals(RuleSeverity.WARNING, finding.severity(), finding.message());
        assertTrue(finding.message().contains("official " + strict + " artefact"),
                id + " does not name the artefact that faults the figure: " + finding.message());
    }

    private static void faults(Invoice invoice, String id) {
        RuleFinding finding = finding(invoice, id).orElseThrow(
                () -> new AssertionError(id + " says nothing beyond the tolerance"));
        assertEquals(RuleSeverity.FATAL, finding.severity(), finding.message());
    }

    /** The UBL artefact grants the tolerance of BR-S-08, so the warning names the CII one. */
    @Test
    void theTaxableAmountOfAStandardRatedBreakdown() {
        silent(Invoices.standard(), "BR-S-08");
        warns(Invoices.standard().put("/BG-23/0/BT-116", "100.5"), "BR-S-08", "CII");
        faults(Invoices.standard().put("/BG-23/0/BT-116", "101"), "BR-S-08");
        faults(Invoices.standard().put("/BG-23/0/BT-116", "150"), "BR-S-08");
    }

    /** The CII artefact grants the tolerance of BR-Z-08, so the warning names the UBL one. */
    @Test
    void theTaxableAmountOfAZeroRatedBreakdown() {
        Invoice sound = Invoices.zeroRatedWithoutReason("Z");
        silent(sound, "BR-Z-08");
        warns(sound.put("/BG-23/0/BT-116", "100.5"), "BR-Z-08", "UBL");
        faults(sound.put("/BG-23/0/BT-116", "101"), "BR-Z-08");
        faults(sound.put("/BG-23/0/BT-116", "150"), "BR-Z-08");
    }

    /** Both artefacts ask BR-O-08 for equality, so it has no zone and nothing to warn about. */
    @Test
    void theTaxableAmountOfABreakdownNotSubjectToVat() {
        Invoice sound = Invoices.notSubject();
        silent(sound, "BR-O-08");
        faults(sound.put("/BG-23/0/BT-116", "100.5"), "BR-O-08");
        faults(sound.put("/BG-23/0/BT-116", "150"), "BR-O-08");
    }

    /**
     * The two artefacts draw the boundary of BR-CO-17 in different places: the CII one admits
     * a difference of exactly one unit and the UBL one does not.
     */
    @Test
    void theVatAmountOfABreakdown() {
        silent(Invoices.standard(), "BR-CO-17");
        silent(Invoices.standard().put("/BG-23/0/BT-117", "19.5"), "BR-CO-17");
        warns(Invoices.standard().put("/BG-23/0/BT-117", "20"), "BR-CO-17", "UBL");
        faults(Invoices.standard().put("/BG-23/0/BT-117", "25"), "BR-CO-17");
    }
}
