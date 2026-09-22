package de.bsnsoft.esj.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every operator of the rule language on cases whose answer was worked out by hand.
 *
 * <p>The assertions are written the way a rule author writes them — an expression that must
 * hold — so a test that expects a rule to pass asserts that no finding came out, and one that
 * expects it to fail asserts that one did.
 */
class OperatorTest {

    private static final SemanticDocument MINIMAL = Documents.minimal().build();

    @Test
    void valueReadsTheContentTheRegistryTypes() {
        assertFalse(Packs.fails(MINIMAL,
                "{\"eq\": [{\"value\": \"/BG-22/BT-106\"}, {\"const\": \"100\"}]}"));
        assertTrue(Packs.fails(MINIMAL,
                "{\"eq\": [{\"value\": \"/BG-22/BT-106\"}, {\"const\": \"101\"}]}"));
    }

    @Test
    void aLiteralBesideADecimalIsANumberAndNotItsSpelling() {
        SemanticDocument document =
                Documents.set(Documents.minimal(), "/BG-22/BT-106", "100.1").build();

        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"value\": \"/BG-22/BT-106\"}, {\"const\": \"100.10\"}]}"));
        assertFalse(Packs.fails(document,
                "{\"gt\": [{\"value\": \"/BG-22/BT-106\"}, {\"const\": \"100.099\"}]}"));
    }

    @Test
    void aLiteralBesideADecimalThatIsNotOneIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class, () -> Packs.run(MINIMAL,
                "{\"eq\": [{\"value\": \"/BG-22/BT-106\"}, {\"const\": \"a hundred\"}]}"));

        assertTrue(refused.getMessage().contains("a hundred"));
    }

    @Test
    void existsAndAbsentAnswerAboutPresenceAndAreNeverUndecided() {
        assertFalse(Packs.fails(MINIMAL, "{\"exists\": \"/BG-22/BT-106\"}"));
        assertTrue(Packs.fails(MINIMAL, "{\"exists\": \"/BG-22/BT-114\"}"));
        assertFalse(Packs.fails(MINIMAL, "{\"absent\": \"/BG-22/BT-114\"}"));
        assertFalse(Packs.fails(MINIMAL, "{\"exists\": \"/BG-25/*\"}"));
        assertFalse(Packs.fails(MINIMAL, "{\"absent\": \"/BG-25/*/BG-27/*\"}"));
    }

    @Test
    void theSixComparisonsOnDecimals() {
        assertFalse(Packs.fails(MINIMAL, compare("eq", "100")));
        assertFalse(Packs.fails(MINIMAL, compare("ne", "99")));
        assertFalse(Packs.fails(MINIMAL, compare("lt", "101")));
        assertFalse(Packs.fails(MINIMAL, compare("le", "100")));
        assertFalse(Packs.fails(MINIMAL, compare("gt", "99")));
        assertFalse(Packs.fails(MINIMAL, compare("ge", "100")));
        assertTrue(Packs.fails(MINIMAL, compare("lt", "100")));
        assertTrue(Packs.fails(MINIMAL, compare("gt", "100")));
    }

    private static String compare(String operator, String literal) {
        return "{\"" + operator + "\": [{\"value\": \"/BG-22/BT-106\"}, {\"const\": \"" + literal + "\"}]}";
    }

    @Test
    void datesCompareAsDatesAndNotAsText() {
        SemanticDocument document = Documents.minimal().put("/BT-9", "2026-02-03").build();

        assertFalse(Packs.fails(document,
                "{\"lt\": [{\"value\": \"/BT-2\"}, {\"value\": \"/BT-9\"}]}"));
        assertFalse(Packs.fails(document,
                "{\"gt\": [{\"value\": \"/BT-9\"}, {\"const\": \"2026-01-31\"}]}"));
        assertTrue(Packs.fails(document,
                "{\"lt\": [{\"value\": \"/BT-9\"}, {\"value\": \"/BT-2\"}]}"));
    }

    @Test
    void textComparesByCodePoint() {
        assertFalse(Packs.fails(MINIMAL,
                "{\"eq\": [{\"value\": \"/BT-5\"}, {\"const\": \"EUR\"}]}"));
        assertFalse(Packs.fails(MINIMAL,
                "{\"lt\": [{\"value\": \"/BT-5\"}, {\"const\": \"EUS\"}]}"));
    }

    @Test
    void arithmeticIsExact() {
        SemanticDocument document = Documents.set(
                Documents.set(Documents.minimal(), "/BG-22/BT-109", "0.1"),
                "/BG-22/BT-110", "0.2").build();

        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"add\": [{\"value\": \"/BG-22/BT-109\"}, {\"value\": \"/BG-22/BT-110\"}]},"
                        + " {\"const\": \"0.3\"}]}"));
    }

    @Test
    void theFourArithmeticOperators() {
        SemanticDocument document = Documents.minimal()
                .put("/BG-22/BT-113", "40")
                .put("/BG-22/BT-114", "-2.5")
                .build();

        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"sub\": [{\"value\": \"/BG-22/BT-112\"}, {\"value\": \"/BG-22/BT-113\"}]},"
                        + " {\"const\": \"60\"}]}"));
        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"mul\": [{\"value\": \"/BG-22/BT-113\"}, {\"const\": 3}]},"
                        + " {\"const\": \"120\"}]}"));
        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"div\": [{\"value\": \"/BG-22/BT-113\"}, {\"const\": 8}]},"
                        + " {\"const\": \"5\"}]}"));
        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"abs\": {\"value\": \"/BG-22/BT-114\"}}, {\"const\": \"2.5\"}]}"));
    }

    @Test
    void divisionByZeroIsUndecidedAndReportsNothing() {
        SemanticDocument document = Documents.minimal().put("/BG-22/BT-113", "0").build();

        assertEquals(List.of(), Packs.run(document,
                "{\"eq\": [{\"div\": [{\"value\": \"/BG-22/BT-112\"}, {\"value\": \"/BG-22/BT-113\"}]},"
                        + " {\"const\": \"1\"}]}"));
    }

    @Test
    void aNonTerminatingQuotientIsComputedToTheWorkingPrecision() {
        SemanticDocument document = Documents.minimal().put("/BG-22/BT-113", "3").build();

        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"round\": [{\"div\": [{\"value\": \"/BG-22/BT-112\"},"
                        + " {\"value\": \"/BG-22/BT-113\"}]}, 2]}, {\"const\": \"33.33\"}]}"));
    }

    @Test
    void roundIsHalfUp() {
        SemanticDocument document = Documents.minimal().put("/BG-22/BT-113", "0.125").build();

        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"round\": [{\"value\": \"/BG-22/BT-113\"}, 2]}, {\"const\": \"0.13\"}]}"));
        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"round\": [{\"value\": \"/BG-22/BT-113\"}, 0]}, {\"const\": \"0\"}]}"));
    }

    @Test
    void roundHalfUpGoesAwayFromZeroOnANegativeNumber() {
        SemanticDocument document = Documents.minimal().put("/BG-22/BT-114", "-0.125").build();

        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"round\": [{\"value\": \"/BG-22/BT-114\"}, 2]}, {\"const\": \"-0.13\"}]}"));
    }

    @Test
    void decimalsCountsTheFractionDigitsTheNumberNeeds() {
        SemanticDocument whole = Documents.minimal().build();
        SemanticDocument two = Documents.set(Documents.minimal(), "/BG-22/BT-106", "100.01").build();
        SemanticDocument three = Documents.set(Documents.minimal(), "/BG-22/BT-106", "100.001").build();

        assertFalse(Packs.fails(whole, "{\"decimals\": [\"/BG-22/BT-106\", 0]}"));
        assertFalse(Packs.fails(two, "{\"decimals\": [\"/BG-22/BT-106\", 2]}"));
        assertTrue(Packs.fails(two, "{\"decimals\": [\"/BG-22/BT-106\", 1]}"));
        assertTrue(Packs.fails(three, "{\"decimals\": [\"/BG-22/BT-106\", 2]}"));
    }

    @Test
    void decimalsRefusesATermWhoseScaleTheStandardLeavesOpen() {
        assertRefusesDecimals("/BG-25/*", "/BG-29/BT-146");
        assertRefusesDecimals("/BG-25/*", "/BT-129");
        assertRefusesDecimals("/BG-23/*", "/BT-119");
    }

    private static void assertRefusesDecimals(String context, String path) {
        RulePackException refused = assertThrows(RulePackException.class, () -> Packs.run(
                MINIMAL, context, "{\"decimals\": [\"" + path + "\", 2]}"));

        assertTrue(refused.getMessage().contains("decimals applies to Amount alone"),
                refused.getMessage());
    }

    @Test
    void decimalsAppliesToAnAmountAlone() {
        RulePackException refused = assertThrows(RulePackException.class, () -> Packs.run(MINIMAL,
                "{\"decimals\": [\"/BT-5\", 2]}"));

        assertTrue(refused.getMessage().contains("decimals applies to Amount alone"));
    }

    @Test
    void matchesIsAnchored() {
        assertFalse(Packs.fails(MINIMAL,
                "{\"matches\": [{\"value\": \"/BT-1\"}, \"RE-[0-9]{4}-[0-9]{4}\"]}"));
        assertTrue(Packs.fails(MINIMAL,
                "{\"matches\": [{\"value\": \"/BT-1\"}, \"RE-[0-9]{4}\"]}"));
    }

    @Test
    void lenCountsCodePoints() {
        SemanticDocument document = Documents.minimal().put("/BT-20", "Zahlbar in 14 Tagen").build();

        assertFalse(Packs.fails(document,
                "{\"eq\": [{\"len\": {\"value\": \"/BT-20\"}}, {\"const\": 19}]}"));
    }

    @Test
    void theThreeValuedLogicOfAndOrNot() {
        assertFalse(Packs.fails(MINIMAL,
                "{\"and\": [{\"exists\": \"/BT-1\"}, {\"exists\": \"/BT-2\"}]}"));
        assertTrue(Packs.fails(MINIMAL,
                "{\"and\": [{\"exists\": \"/BT-1\"}, {\"exists\": \"/BT-9\"}]}"));
        assertFalse(Packs.fails(MINIMAL,
                "{\"or\": [{\"exists\": \"/BT-9\"}, {\"exists\": \"/BT-1\"}]}"));
        assertFalse(Packs.fails(MINIMAL, "{\"not\": {\"exists\": \"/BT-9\"}}"));
    }

    @Test
    void anAndWithAnUndecidedOperandAndNoFalseOneIsUndecided() {
        assertEquals(List.of(), Packs.run(MINIMAL,
                "{\"and\": [{\"exists\": \"/BT-1\"},"
                        + " {\"eq\": [{\"value\": \"/BG-22/BT-114\"}, {\"const\": \"0\"}]}]}"));
    }

    @Test
    void anAndWithAFalseOperandIsFalseWhateverElseIsUndecided() {
        assertEquals(1, Packs.run(MINIMAL,
                "{\"and\": [{\"exists\": \"/BT-9\"},"
                        + " {\"eq\": [{\"value\": \"/BG-22/BT-114\"}, {\"const\": \"0\"}]}]}").size());
    }

    @Test
    void ifWithoutAnElseSaysNothingAboutWhatItsConditionDoesNotCover() {
        assertFalse(Packs.fails(MINIMAL,
                "{\"if\": {\"condition\": {\"exists\": \"/BT-9\"}, \"then\": {\"exists\": \"/BT-20\"}}}"));
        assertTrue(Packs.fails(MINIMAL,
                "{\"if\": {\"condition\": {\"exists\": \"/BT-1\"}, \"then\": {\"exists\": \"/BT-20\"}}}"));
    }

    @Test
    void ifChoosesBetweenTwoValues() {
        assertFalse(Packs.fails(MINIMAL,
                "{\"eq\": [{\"if\": {\"condition\": {\"exists\": \"/BG-22/BT-113\"},"
                        + " \"then\": {\"value\": \"/BG-22/BT-113\"}, \"else\": {\"const\": \"0\"}}},"
                        + " {\"const\": \"0\"}]}"));
    }

    @Test
    void anAssertionThatIsNotATruthValueIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> Packs.run(MINIMAL, "{\"value\": \"/BG-22/BT-106\"}"));

        assertTrue(refused.getMessage().contains("an assertion is a truth value"));
    }

    @Test
    void anUnknownOperatorIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> Packs.run(MINIMAL, "{\"between\": [{\"const\": 1}, {\"const\": 2}]}"));

        assertTrue(refused.getMessage().contains("not an operator of this language"));
    }

    @Test
    void anExpressionWithTwoOperatorsIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class, () -> Packs.run(MINIMAL,
                "{\"exists\": \"/BT-1\", \"absent\": \"/BT-9\"}"));

        assertTrue(refused.getMessage().contains("exactly one"));
    }

    @Test
    void aTermTheRegistryDoesNotKnowIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> Packs.run(MINIMAL, "{\"exists\": \"/BT-9999\"}"));

        assertTrue(refused.getMessage().contains("BT-9999"));
    }

    @Test
    void anAsteriskWhereTheRegistryHasNoRepetitionIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> Packs.run(MINIMAL, "{\"exists\": \"/BG-22/*/BT-106\"}"));

        assertTrue(refused.getMessage().contains("without an asterisk"));
    }

    @Test
    void aMissingAsteriskWhereTheRegistryRepeatsIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> Packs.run(MINIMAL, "{\"exists\": \"/BG-25/BT-131\"}"));

        assertTrue(refused.getMessage().contains("is repeatable"));
    }

    @Test
    void aNestingTheRegistryDoesNotRecordIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class,
                () -> Packs.run(MINIMAL, "{\"exists\": \"/BG-22/BT-131\"}"));

        assertTrue(refused.getMessage().contains("not a nesting the registry records"));
    }

    @Test
    void arithmeticOverADateIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class, () -> Packs.run(MINIMAL,
                "{\"eq\": [{\"add\": [{\"value\": \"/BT-2\"}, {\"const\": 1}]}, {\"const\": 1}]}"));

        assertTrue(refused.getMessage().contains("stands where a decimal belongs"));
    }

    @Test
    void comparingADateWithTextIsADefectOfThePack() {
        RulePackException refused = assertThrows(RulePackException.class, () -> Packs.run(MINIMAL,
                "{\"eq\": [{\"value\": \"/BT-2\"}, {\"value\": \"/BT-5\"}]}"));

        assertTrue(refused.getMessage().contains("not compared or combined"));
    }
}
