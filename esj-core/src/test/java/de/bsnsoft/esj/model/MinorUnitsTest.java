package de.bsnsoft.esj.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.TermKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** The minor units a caller hands in, and the fraction digit bound of a term they give. */
class MinorUnitsTest {

    private static final MinorUnits UNITS =
            MinorUnits.of(Map.of("EUR", 2, "JPY", 0, "KWD", 3), "three currencies of a test");

    @Test
    void aConstantBoundIsTheRegistrysWhateverTheCurrency() {
        Term term = amount(OptionalInt.of(2), Optional.empty());
        assertEquals(OptionalInt.of(2), UNITS.fractionDigits(term, "JPY"));
        assertEquals(OptionalInt.of(2), UNITS.fractionDigits(term, "XXX"));
    }

    @Test
    void aRuleBoundFollowsTheMinorUnitOfTheCurrency() {
        Term amount = amount(OptionalInt.empty(), Optional.of(MinorUnits.MINOR_UNIT));
        Term price = amount(OptionalInt.empty(), Optional.of(MinorUnits.MINOR_UNIT_PLUS_2));
        assertEquals(OptionalInt.of(2), UNITS.fractionDigits(amount, "EUR"));
        assertEquals(OptionalInt.of(0), UNITS.fractionDigits(amount, "JPY"));
        assertEquals(OptionalInt.of(3), UNITS.fractionDigits(amount, "KWD"));
        assertEquals(OptionalInt.of(2), UNITS.fractionDigits(price, "JPY"));
        assertEquals(OptionalInt.of(5), UNITS.fractionDigits(price, "KWD"));
    }

    @Test
    void aCurrencyTheSnapshotDoesNotKnowIsAnsweredWithNothing() {
        Term amount = amount(OptionalInt.empty(), Optional.of(MinorUnits.MINOR_UNIT));
        assertTrue(UNITS.fractionDigits(amount, "XAU").isEmpty());
        assertTrue(UNITS.minorUnit("eur").isEmpty(), "the comparison is exact");
    }

    @Test
    void aTermWithoutABoundHasNone() {
        assertTrue(UNITS.fractionDigits(amount(OptionalInt.empty(), Optional.empty()), "EUR")
                .isEmpty());
    }

    @Test
    void aRuleThisClassDoesNotKnowIsRefused() {
        Term term = amount(OptionalInt.empty(), Optional.of("per-mille"));
        assertThrows(IllegalArgumentException.class, () -> UNITS.fractionDigits(term, "EUR"));
    }

    @Test
    void negativeMinorUnitsNullsAndABlankSourceAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> MinorUnits.of(Map.of("EUR", -1), "a test"));
        assertThrows(IllegalArgumentException.class, () -> MinorUnits.of(Map.of(), " "));
        Map<String, Integer> withNull = new HashMap<>();
        withNull.put("EUR", null);
        assertThrows(NullPointerException.class, () -> MinorUnits.of(withNull, "a test"));
    }

    @Test
    void theSourceTravelsWithTheNumbers() {
        assertEquals("three currencies of a test", UNITS.source());
        assertTrue(UNITS.toString().contains("3 currencies"));
    }

    private static Term amount(OptionalInt maxDecimals, Optional<String> rule) {
        return new Term("BT-131", TermKind.BT, "Invoice line net amount", "invoiceLineNetAmount",
                Optional.of("BG-25"), List.of("BG-25", "BT-131"), 1, Cardinality.of(1, 1),
                Optional.of(SemanticType.AMOUNT), maxDecimals, rule, Optional.empty(), List.of(),
                List.of(), 1, List.of(), "The net amount of one line.", List.of());
    }
}
