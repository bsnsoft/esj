package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.bsnsoft.esj.SemanticType;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The pictures a value is written in, pinned.
 *
 * <p>The coverage test looks for a value in a rendering in the form {@link Formats} writes
 * it, which makes it a test of the layout rather than of the formatting: if the formatting
 * changed, that test would look for the new form and find it. This is the test that says
 * what the forms are, on values chosen for their edges — a negative amount, one with more
 * decimal places than two, one with none, a quantity that is a whole number, a date at the
 * turn of a year, and a content that is not of the type its term claims.
 */
class FormatsTest {

    @ParameterizedTest
    @CsvSource({
            "AMOUNT,            1234.5,      '1.234,50',     '1,234.50'",
            "AMOUNT,            0,           '0,00',         0.00",
            "AMOUNT,            -1234.56,    '-1.234,56',    '-1,234.56'",
            "AMOUNT,            1234567.891, '1.234.567,891','1,234,567.891'",
            "UNIT_PRICE_AMOUNT, 12.3456,     '12,3456',      12.3456",
            "QUANTITY,          10,          10,             10",
            "QUANTITY,          2.5,         '2,5',          2.5",
            "PERCENTAGE,        19,          '19,00 %',      '19.00 %'",
            "DATE,              2026-12-31,  31.12.2026,     2026-12-31",
            "DATE,              2026-01-05,  05.01.2026,     2026-01-05",
            "CODE,              380,         380,            380",
            "IDENTIFIER,        DE123456789, DE123456789,    DE123456789",
    })
    void writesAValueInThePictureOfItsLanguage(SemanticType type, String content,
                                               String german, String english) {
        assertEquals(german, Formats.value(type, content, RenderLanguage.GERMAN),
                content + " as " + type + " in German");
        assertEquals(english, Formats.value(type, content, RenderLanguage.ENGLISH),
                content + " as " + type + " in English");
    }

    /**
     * The letter layout writes a percentage with the decimal places the document wrote
     * and no others, and writes every other type the way the generic layout writes it.
     * The trailing zeros of the fraction are the only thing dropped: a rate of 19.125 per
     * cent keeps all of its digits, and none is rounded away.
     */
    @ParameterizedTest
    @CsvSource({
            "19,        '19 %',            '19 %'",
            "19.00,     '19 %',            '19 %'",
            "7.0,       '7 %',             '7 %'",
            "0.00,      '0 %',             '0 %'",
            "19.50,     '19,5 %',          '19.5 %'",
            "10.70,     '10,7 %',          '10.7 %'",
            "19.125,    '19,125 %',        '19.125 %'",
            "-2.50,     '-2,5 %',          '-2.5 %'",
            "1900.00,   '1.900 %',         '1,900 %'",
            "nineteen,  nineteen,          nineteen",
    })
    void writesAPercentageOfALetterAsTheDocumentWroteIt(String content, String german,
                                                        String english) {
        assertEquals(german, Formats.asWritten(SemanticType.PERCENTAGE, content,
                RenderLanguage.GERMAN), content + " as a percentage in German");
        assertEquals(english, Formats.asWritten(SemanticType.PERCENTAGE, content,
                RenderLanguage.ENGLISH), content + " as a percentage in English");
    }

    /** Every other type is written as {@link Formats#value} writes it. */
    @ParameterizedTest
    @CsvSource({"AMOUNT, 1234.5", "UNIT_PRICE_AMOUNT, 12.3456", "QUANTITY, 2.50",
                "DATE, 2026-12-31", "CODE, 380", "IDENTIFIER, DE123456789"})
    void writesEveryOtherTypeOfALetterAsTheGenericLayoutDoes(SemanticType type,
                                                             String content) {
        for (RenderLanguage language : RenderLanguage.values()) {
            assertEquals(Formats.value(type, content, language),
                    Formats.asWritten(type, content, language),
                    content + " as " + type + " in " + language.code());
        }
    }

    /**
     * A content that is not of the shape its semantic data type asks for is written as it
     * stands. A renderer is not a validator: an invoice with a date that is not a date
     * still has to be readable, and this project has a validator that says what is wrong
     * with it.
     */
    @ParameterizedTest
    @CsvSource({
            "DATE,       'the day after tomorrow'",
            "AMOUNT,     'about a hundred'",
            "PERCENTAGE, 'nineteen'",
            "QUANTITY,   ''",
    })
    void writesAValueThatIsNotOfItsTypeAsItStands(SemanticType type, String content) {
        assertEquals(content, Formats.value(type, content, RenderLanguage.GERMAN),
                content + " is written as it stands");
        assertEquals(content, Formats.value(type, content, RenderLanguage.ENGLISH),
                content + " is written as it stands");
    }

    @ParameterizedTest
    @CsvSource({
            "0,      '0 Bytes',       '0 bytes'",
            "1024,   '1.024 Bytes',   '1,024 bytes'",
            "204800, '204.800 Bytes', '204,800 bytes'",
    })
    void writesTheSizeOfAnAttachment(long bytes, String german, String english) {
        assertEquals(german, Formats.bytes(bytes, RenderLanguage.GERMAN), bytes + " in German");
        assertEquals(english, Formats.bytes(bytes, RenderLanguage.ENGLISH), bytes + " in English");
    }

    /**
     * A decimal of the semantic model carries an exponent in its lexical form, so
     * {@code 1E+2000000000} is a decimal by that grammar and writing it out would ask for
     * two thousand million digits. A renderer is not a validator and does not refuse such
     * a value; it writes it the way it writes every other content that is not of the
     * shape its type asks for — as it stands, at the cost of reading it.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1E+2000000000", "1E-2000000000", "-1E+999999999",
                            "1E+2147483647", "1E-2147483647"})
    void aDecimalNoReaderWouldReadIsPrintedAsItStands(String content) {
        for (SemanticType type : new SemanticType[] {SemanticType.AMOUNT,
                                                     SemanticType.UNIT_PRICE_AMOUNT,
                                                     SemanticType.QUANTITY,
                                                     SemanticType.PERCENTAGE}) {
            for (RenderLanguage language : RenderLanguage.values()) {
                String shown = Formats.value(type, content, language);
                assertEquals(content, shown.replace(" %", ""),
                        content + " as " + type + " in " + language.code());
                assertEquals(content, Formats.asWritten(type, content, language)
                                .replace(" %", ""),
                        content + " as " + type + " in a letter in " + language.code());
            }
        }
    }

    /** A decimal of an ordinary size is still written out, however many places it has. */
    @Test
    void aLongButOrdinaryDecimalIsStillWrittenOut() {
        String content = "0." + "1".repeat(1500);

        assertEquals("0," + "1".repeat(1500),
                Formats.value(SemanticType.QUANTITY, content, RenderLanguage.GERMAN),
                "a quantity of fifteen hundred decimal places is printed, not skipped");
    }

    /**
     * The pictures are the pictures of the language and of nothing else. A machine
     * configured for another script writes the same digits, because a rendering that
     * depended on the machine would differ between two machines that ran the same
     * document.
     */
    @Test
    void aDateIsWrittenInTheSameDigitsWhateverTheMachineIsConfiguredFor() {
        Locale machine = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"));

            assertEquals("31.12.2026",
                    Formats.value(SemanticType.DATE, "2026-12-31", RenderLanguage.GERMAN),
                    "the German date is written in the digits of the language");
        } finally {
            Locale.setDefault(machine);
        }
    }
}
