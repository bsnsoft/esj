package de.bsnsoft.esj;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks the one value record: its shape, its string rules and its typed accessors. */
class SemanticValueTest {

    @Test
    void aValueWithoutAComponentCarriesNothingButItsContent() {
        SemanticValue value = SemanticValue.of("RE-2026-0001");

        assertEquals("RE-2026-0001", value.content());
        assertEquals("RE-2026-0001", value.canonicalContent());
        assertEquals("RE-2026-0001", value.asString());
        assertFalse(value.hasComponents());
        assertNull(value.scheme());
        assertNull(value.schemeVersion());
        assertNull(value.mimeCode());
        assertNull(value.filename());
    }

    @Test
    void everyComponentMakesTheValueAnObject() {
        assertTrue(SemanticValue.identifier("0088123456785", "0088").hasComponents());
        assertTrue(SemanticValue.identifier("9873242", "TST", "19.05.01").hasComponents());
        assertTrue(SemanticValue.binary(new byte[] {1}, "application/pdf", "a.pdf")
                .hasComponents());
        assertFalse(SemanticValue.of("Example GmbH").hasComponents());
    }

    @Test
    void theContentIsKeptAsItStandsAndIsNeverRepaired() {
        assertEquals("100.00", SemanticValue.of("100.00").content());
        assertEquals("2026-02-30", SemanticValue.of("2026-02-30").content());
        assertEquals("QUJDRR==", SemanticValue.of("QUJDRR==").content());
        assertEquals("  spaced  ", SemanticValue.of("  spaced  ").content());
    }

    @Test
    void lineEndingsAreNormalizedInEveryStringOfAValue() {
        SemanticValue identifier = SemanticValue.identifier("a\r\nb", "s\rt", "v\r\nw");

        assertEquals("a\nb", identifier.content());
        assertEquals("s\nt", identifier.scheme());
        assertEquals("v\nw", identifier.schemeVersion());

        SemanticValue binary = new SemanticValue("QUJDRQ==", null, null,
                "text/plain\r\n1", "a\r\nb.txt");
        assertEquals("text/plain\n1", binary.mimeCode());
        assertEquals("a\nb.txt", binary.filename());
    }

    @Test
    void anEmptyStringIsRejectedWhereverItStands() {
        assertThrows(EsjFormatException.class, () -> SemanticValue.of(""));
        assertThrows(EsjFormatException.class, () -> SemanticValue.identifier("a", ""));
        assertThrows(EsjFormatException.class, () -> SemanticValue.identifier("a", "s", ""));
        assertThrows(EsjFormatException.class,
                () -> SemanticValue.binary(new byte[0], "application/pdf", "a.pdf"));
        assertThrows(EsjFormatException.class,
                () -> SemanticValue.binary(new byte[] {1}, "", "a.pdf"));
        assertThrows(EsjFormatException.class,
                () -> SemanticValue.binary(new byte[] {1}, "application/pdf", ""));
    }

    @Test
    void aSchemeVersionWithoutASchemeIsRejected() {
        assertThrows(EsjFormatException.class,
                () -> SemanticValue.identifier("a", null, "1.0"));
        assertThrows(EsjFormatException.class,
                () -> new SemanticValue("a", null, "1.0", null, null));

        SemanticValue both = SemanticValue.identifier("a", "0160", "1.0");
        assertEquals("0160", both.scheme());
        assertEquals("1.0", both.schemeVersion());
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0",
        "0.01, 0.01",
        "9.99, 9.99",
        "42.015, 42.015",
        "42.0150, 42.015",
        "999999999999.99999, 999999999999.99999",
        "100.00, 100",
        "1E2, 100",
        "-0, 0",
        "-0.00, 0",
        "-12.50, -12.5",
        "0.000, 0"
    })
    void aWriterTurnsANumberIntoTheCanonicalDecimalSpelling(String input, String canonical) {
        assertEquals(canonical, SemanticValue.ofDecimal(new BigDecimal(input)).content());
    }

    @Test
    void twoSpellingsOfOneNumberProduceOneValue() {
        assertEquals(SemanticValue.ofDecimal(new BigDecimal("100")),
                SemanticValue.ofDecimal(new BigDecimal("100.00")));
        assertEquals(SemanticValue.ofDecimal(new BigDecimal("100")).hashCode(),
                SemanticValue.ofDecimal(new BigDecimal("100.00")).hashCode());
        assertEquals(SemanticValue.ofDecimal(new BigDecimal("12.5")),
                SemanticValue.ofDecimal(new BigDecimal("12.50")));
    }

    @Test
    void aNumberWhoseCanonicalFormIsTooLongIsRefused() {
        BigDecimal tooLong = new BigDecimal("1" + "0".repeat(64));

        assertThrows(EsjFormatException.class, () -> SemanticValue.ofDecimal(tooLong));
        assertEquals("1" + "0".repeat(63),
                SemanticValue.ofDecimal(new BigDecimal("1" + "0".repeat(63))).content());
    }

    @Test
    void aNumberWithALargeExponentIsRefusedWithoutBeingWrittenOut() {
        BigDecimal huge = new BigDecimal("1E+1000000000");
        BigDecimal tiny = new BigDecimal("1E-1000000000");

        assertThrows(EsjFormatException.class, () -> SemanticValue.ofDecimal(huge));
        assertThrows(EsjFormatException.class, () -> SemanticValue.ofDecimal(tiny));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.01", "9.99", "42.015", "999999999999.99999", "-1.5", "100"})
    void aCanonicalDecimalIsReadBackDigitForDigit(String content) {
        assertEquals(new BigDecimal(content), SemanticValue.of(content).asDecimal());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1E2", "01", "100.00", "-0", "+1", "1,5", "0.", ".5", "1e-3", "1.10"})
    void aContentOutsideTheDecimalGrammarIsAnL2Finding(String content) {
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> SemanticValue.of(content).asDecimal());
        assertEquals("ESJ-L2-DECIMAL", thrown.code().orElseThrow().code());
    }

    @Test
    void aDecimalLongerThanSixtyFourCharactersIsADecimalFindingAndNotALimit() {
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> SemanticValue.of("1".repeat(65)).asDecimal());

        assertEquals("ESJ-L2-DECIMAL", thrown.code().orElseThrow().code());
        assertTrue(thrown.getMessage().contains("65 characters"), thrown.getMessage());
        assertEquals(new BigDecimal("1".repeat(64)),
                SemanticValue.of("1".repeat(64)).asDecimal());
    }

    @Test
    void theMessageOfADecimalFindingNamesTheClauseThatWasBroken() {
        assertTrue(message("100.00").contains("trailing zeros"), message("100.00"));
        assertTrue(message("1E2").contains("exponent"), message("1E2"));
        assertTrue(message("+1").contains("plus sign"), message("+1"));
        assertTrue(message("-0").contains("signs a zero"), message("-0"));
        assertTrue(message("007").contains("leading zero"), message("007"));
        assertTrue(message("100.").contains("decimal point"), message("100."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000-01-01", "2024-02-29", "9999-12-31", "2026-01-15"})
    void aDateOfTheCalendarIsRead(String content) {
        assertEquals(LocalDate.parse(content), SemanticValue.of(content).asDate());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "2026-02-30", "2023-02-29", "0999-01-01", "2026-13-01", "2026-00-10",
        "2026-1-1", "26-01-01", "2026-01-32", "2026/01/01", "2026-01-01T00:00:00Z"})
    void aContentOutsideTheDateGrammarOrOutsideTheCalendarIsAnL2Finding(String content) {
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> SemanticValue.of(content).asDate());
        assertEquals("ESJ-L2-DATE", thrown.code().orElseThrow().code());
    }

    @Test
    void aWriterTurnsADateIntoItsIsoSpelling() {
        assertEquals("2026-01-15", SemanticValue.ofDate(LocalDate.of(2026, 1, 15)).content());
        assertEquals("2024-02-29", SemanticValue.ofDate(LocalDate.of(2024, 2, 29)).content());
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 10000})
    void aYearOutsideTheRepresentableRangeIsRefused(int year) {
        LocalDate date = LocalDate.of(year, 1, 1);
        assertThrows(EsjFormatException.class, () -> SemanticValue.ofDate(date));
    }

    @ParameterizedTest
    @ValueSource(strings = {"06:15:02Z", "00:00:00Z", "23:59:59Z", "09:30:00+02:00",
        "09:30:00-05:30", "12:00:00+14:00", "12:00:00-14:00", "12:00:00+13:45"})
    void aTimeOfTheGrammarIsRead(String content) {
        assertEquals(OffsetTime.parse(content), SemanticValue.of(content).asTime());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "09:30:00", "09:30:00+00:00", "09:30:00-00:00", "09:30:00.5Z", "09:30:00,5Z",
        "24:00:00Z", "09:30:60Z", "09:60:00Z", "09:30:00+15:00", "09:30:00+02:60",
        "9:30:00Z", "09:30Z", "09:30:00z", "09:30:00 Z", "09:30:00+0200", "T09:30:00Z",
        "09:30:00+14:30"})
    void aContentOutsideTheTimeGrammarIsAnL2Finding(String content) {
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> SemanticValue.of(content).asTime());
        assertEquals("ESJ-L2-TIME", thrown.code().orElseThrow().code());
    }

    @Test
    void theMessageOfATimeFindingNamesTheClauseThatWasBroken() {
        assertTrue(timeMessage("09:30:00").contains("carries no offset"),
                timeMessage("09:30:00"));
        assertTrue(timeMessage("09:30:00+00:00").contains("written Z"),
                timeMessage("09:30:00+00:00"));
        assertTrue(timeMessage("09:30:00.5Z").contains("fractional seconds"),
                timeMessage("09:30:00.5Z"));
        assertTrue(timeMessage("24:00:00Z").contains("23:59:59"), timeMessage("24:00:00Z"));
        assertTrue(timeMessage("09:30:60Z").contains("leap second"), timeMessage("09:30:60Z"));
        assertTrue(timeMessage("09:30:00+15:00").contains("-14:00 and +14:00"),
                timeMessage("09:30:00+15:00"));
    }

    /**
     * The writing side of the type puts a time into the one spelling the grammar has:
     * UTC is {@code Z}, every other offset is written to the minute, and the round trip
     * through {@link SemanticValue#asTime()} gives the same time back.
     */
    @ParameterizedTest
    @ValueSource(strings = {"06:15:02Z", "00:00:00Z", "23:59:59Z", "09:30:00+02:00",
        "09:30:00-05:30", "12:00:00+14:00", "12:00:00-14:00", "12:00:00+13:45"})
    void aTimeIsWrittenInTheOneSpellingOfTheGrammar(String content) {
        SemanticValue value = SemanticValue.ofTime(OffsetTime.parse(content));

        assertEquals(content, value.content());
        assertEquals(OffsetTime.parse(content), value.asTime());
    }

    @Test
    void theZeroOffsetIsWrittenZWhicheverWayItWasHandedOver() {
        assertEquals("09:30:00Z",
                SemanticValue.ofTime(OffsetTime.parse("09:30:00+00:00")).content());
    }

    /**
     * What the writer refuses rather than rounding away: a fraction of a second, and an
     * offset that is not a whole number of minutes. Both would change the value.
     */
    @Test
    void aTimeTheGrammarHasNoSpellingForIsRefusedRatherThanCut() {
        OffsetTime fraction = OffsetTime.parse("09:30:00.5Z");
        OffsetTime seconds = OffsetTime.of(9, 30, 0, 0, ZoneOffset.ofTotalSeconds(3630));

        assertThrows(EsjFormatException.class, () -> SemanticValue.ofTime(fraction));
        assertThrows(EsjFormatException.class, () -> SemanticValue.ofTime(seconds));
    }

    /**
     * {@link ZoneOffset} reaches eighteen hours and the grammar of section 6.5 stops at
     * fourteen. The writer refuses the four offsets in between rather than writing content
     * its own validator would call invalid.
     */
    @ParameterizedTest
    @ValueSource(ints = {15, 16, 18, -15, -18})
    void anOffsetOutsideTheGrammarIsRefusedByTheWriter(int hours) {
        OffsetTime time = OffsetTime.of(9, 30, 0, 0, ZoneOffset.ofHours(hours));

        EsjFormatException refused = assertThrows(EsjFormatException.class,
                () -> SemanticValue.ofTime(time));

        assertTrue(refused.getMessage().contains("-14:00")
                        && refused.getMessage().contains("+14:00"),
                "the refusal names the range: " + refused.getMessage());
    }

    private static String timeMessage(String content) {
        return assertThrows(EsjFormatException.class, () -> SemanticValue.of(content).asTime())
                .getMessage();
    }

    @Test
    void aBinaryObjectEncodesItsBytesAndDecodesThemBack() {
        byte[] content = "ABCE".getBytes(StandardCharsets.US_ASCII);
        SemanticValue binary = SemanticValue.binary(content, "application/pdf", "invoice.pdf");

        assertEquals("QUJDRQ==", binary.content());
        assertEquals("application/pdf", binary.mimeCode());
        assertEquals("invoice.pdf", binary.filename());
        assertArrayEquals(content, binary.asBytes());
    }

    @Test
    void theBytesHandedOutAreAFreshArrayEveryTime() {
        SemanticValue binary = SemanticValue.binary(new byte[] {1, 2, 3}, "application/pdf", "a.pdf");

        byte[] first = binary.asBytes();
        first[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, binary.asBytes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUJDRR==", "QUJDR===", "QUJD RQ==", "QUJDRQ", "QUJDRQ=", "QU_DRQ=="})
    void aContentThatIsNotCanonicalBase64IsAnL2Finding(String content) {
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> SemanticValue.of(content).asBytes());

        assertEquals("ESJ-L2-BASE64", thrown.code().orElseThrow().code());
        assertFalse(thrown.getMessage().contains(content), thrown.getMessage());
    }

    @Test
    void twoValuesWithTheSameContentAndComponentsAreEqual() {
        SemanticValue one = SemanticValue.binary(new byte[] {1, 2}, "application/pdf", "a.pdf");
        SemanticValue other = SemanticValue.binary(new byte[] {1, 2}, "application/pdf", "a.pdf");
        SemanticValue different = SemanticValue.binary(new byte[] {1, 3}, "application/pdf", "a.pdf");

        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
        assertNotEquals(one, different);
        assertNotEquals(SemanticValue.of("a"), SemanticValue.identifier("a", "0088"));
    }

    @Test
    void theDescriptionOfAValueNamesItsComponentsAndExcerptsItsContent() {
        SemanticValue binary = SemanticValue.binary(new byte[3000], "application/pdf", "a.pdf");

        assertTrue(binary.toString().contains("a.pdf"), binary.toString());
        assertTrue(binary.toString().contains("4000 characters"), binary.toString());
        assertTrue(binary.toString().length() < 200, binary.toString());
        assertTrue(SemanticValue.of("a\nb").toString().contains("a\\nb"),
                SemanticValue.of("a\nb").toString());
    }

    private static String message(String content) {
        return assertThrows(EsjFormatException.class,
                () -> SemanticValue.of(content).asDecimal()).getMessage();
    }
}
