package de.bsnsoft.esj.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Checks the canonical decimal form a JSON number inside {@code extensions} is written in
 * (specification, section 7.6, rule 2). The same grammar constrains the content of a
 * decimal term, where it is checked at layer L2 and lives with the value; the tests for
 * that side are in {@code SemanticValueTest}.
 */
class DecimalsTest {

    @ParameterizedTest
    @CsvSource({
            "1e21,       1000000000000000000000",
            "1E21,       1000000000000000000000",
            "1e-6,       0.000001",
            "1e-7,       0.0000001",
            "-0,         0",
            "-0.0,       0",
            "0.0,        0",
            "12345678901234567890, 12345678901234567890",
            "1.0000000000000001,   1.0000000000000001",
            "100.00,     100",
            "42.0150,    42.015",
            "0.5,        0.5",
            "-42.015,    -42.015",
            "1.5e3,      1500",
            "1500e-3,    1.5",
            "0.0000001,  0.0000001",
            "-1e-3,      -0.001"})
    void aLexicalFormIsCanonicalizedByStringProcessing(String lexical, String canonical) {
        assertEquals(canonical, Decimals.canonicalize(lexical));
    }

    @Test
    void aNumberWhoseCanonicalFormIsTooLongHasNone() {
        assertNull(Decimals.canonicalize("1e400"));
        assertNull(Decimals.canonicalize("1e-400"));
        assertNull(Decimals.canonicalize("1e999999999999999999999"));
        assertNull(Decimals.canonicalize("-1e-999999999999999999999"));
    }

    @Test
    void theExponentShiftsThePointUpToTheBoundAndNoFurther() {
        assertEquals("1" + "0".repeat(63), Decimals.canonicalize("1e63"));
        assertNull(Decimals.canonicalize("1e64"));
    }

    /**
     * The window an exponent accumulator that overflows a signed long lands in. Read
     * modulo 2^64, these four rows leave the residues 5, 1, 0 and -1, so an
     * implementation that lets the accumulation wrap computes the canonical form of a
     * completely different number — {@code 1e106616204000961326445756421} becomes
     * {@code 100000} and {@code 1e110680464442257309696} becomes {@code 1} — and accepts
     * a token the 64-character bound of the specification, section 6.4 refuses.
     * Saturation is what keeps every exponent past the bound on the far side of it,
     * whatever its residue.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "106616204000961326445756421",
            "110680464442257309697",
            "110680464442257309696",
            "110680464442257309695"})
    void anExponentIsSaturatedRatherThanWrapped(String exponent) {
        assertNull(Decimals.canonicalize("1e" + exponent), exponent);
        assertNull(Decimals.canonicalize("1e-" + exponent), exponent);
        assertNull(Decimals.canonicalize("-1e" + exponent), exponent);
        assertNull(Decimals.canonicalize("123e" + exponent), exponent);
    }

    /**
     * The exponents around the edge of the {@code long} range, including the three that
     * once made the shifted decimal point a garbage magnitude the output was then sized
     * from — there the defect was an allocation of billions of zeros rather than a wrong
     * verdict. A nineteen-nine exponent, which wraps to a large negative accumulator, is
     * here for the same reason.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "9223372036854775806",
            "9223372036854775807",
            "9223372036854775808",
            "9223372036854775809",
            "9223372036854775810",
            "9999999999999999999"})
    void anExponentAtTheEdgeOfTheLongRangeIsRefusedRatherThanAllocatedFor(String exponent) {
        assertNull(Decimals.canonicalize("1e" + exponent), exponent);
        assertNull(Decimals.canonicalize("1e-" + exponent), exponent);
    }

    /**
     * An exponent of more than a million digits. The digits are all read, because a
     * malformed exponent has to be refused however long it is, and the accumulator has to
     * stay saturated across every one of them rather than wrap back into the acceptable
     * range.
     */
    @Test
    @Timeout(value = 60)
    void anExponentOfMoreThanAMillionDigitsSaturatesAndStaysSaturated() {
        String digits = "9".repeat(1_000_001);
        assertNull(Decimals.canonicalize("1e" + digits));
        assertNull(Decimals.canonicalize("1e-" + digits));
        assertNull(Decimals.canonicalize("-1e" + digits));
    }

    /**
     * The other side of the same loop: a million digits that carry no magnitude at all
     * must not saturate anything. A reader that stopped accumulating after a fixed number
     * of digits would answer this token with the wrong number.
     */
    @Test
    @Timeout(value = 60)
    void aMillionLeadingZerosInTheExponentChangeNothing() {
        assertEquals("10000000", Decimals.canonicalize("1e" + "0".repeat(1_000_000) + "7"));
        assertEquals("0.0000001", Decimals.canonicalize("1e-" + "0".repeat(1_000_000) + "7"));
    }

    @Test
    void aMalformedExponentIsRefusedHoweverLongItIs() {
        assertThrows(IllegalArgumentException.class,
                () -> Decimals.canonicalize("1e" + "9".repeat(64) + "x"));
        assertThrows(IllegalArgumentException.class, () -> Decimals.canonicalize("1e"));
    }

    @Test
    void theBoundIsSixtyFourCharactersAndIsCountedWithSignAndPoint() {
        String sixtyFour = "-" + "1".repeat(62) + "1";
        assertEquals(64, sixtyFour.length());
        assertEquals(sixtyFour, Decimals.canonicalize(sixtyFour));
        assertNull(Decimals.canonicalize(sixtyFour + "1"));
    }

    @Test
    void aZeroMantissaIsZeroWhateverTheExponent() {
        assertEquals("0", Decimals.canonicalize("0e400"));
        assertEquals("0", Decimals.canonicalize("-0.000e-400"));
    }

    @Test
    void theFormOfABigDecimalIsCanonicalizedTheSameWay() {
        assertEquals("1000000000000000000000",
                Decimals.canonicalize(new BigDecimal("1e21").toString()));
        assertEquals("0.000001", Decimals.canonicalize(new BigDecimal("1e-6").toString()));
        assertEquals("100", Decimals.canonicalize(new BigDecimal("100.00").toString()));
        assertEquals("0", Decimals.canonicalize(new BigDecimal("-0.00").toString()));
    }
}
