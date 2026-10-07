package de.bsnsoft.esj.render.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The payload of the payment code: what it carries, and when there is none.
 *
 * <p>Every case here is a statement of the guideline EPC069-12 read back off a document.
 * The elements are asked for as one text rather than one by one, because the payload is
 * one text to whoever reads the code and an element in the wrong line is an amount read
 * as a name.
 *
 * <p>The conditions are the other half. A code is a promise that a banking application
 * will accept what it finds, so the cases that produce nothing matter as much as the ones
 * that produce a symbol: a direct debit, a currency that is not the euro, an account
 * identifier whose check digits do not hold, an amount outside the range, a payload past
 * the bound.
 */
class PaymentCodeTest {

    /** A test IBAN whose check digits hold. */
    private static final String IBAN = "DE89370400440532013000";

    /** The document the cases vary: a credit transfer with everything stated. */
    private static Map<String, String> invoice() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("/BT-1", "RE-2026-0042");
        values.put("/BT-5", "EUR");
        values.put("/BG-4/BT-27", "Example GmbH");
        values.put("/BG-16/BT-81", "58");
        values.put("/BG-16/BT-83", "Invoice RE-2026-0042");
        values.put("/BG-16/BG-17/0/BT-84", IBAN);
        values.put("/BG-16/BG-17/0/BT-85", "Example Systems GmbH");
        values.put("/BG-16/BG-17/0/BT-86", "COBADEFFXXX");
        values.put("/BG-22/BT-115", "2915.50");
        return values;
    }

    /**
     * Returns that document with paths set to other values, an empty one meaning that the
     * document does not state the term at all.
     *
     * @param pairs a path and the value it takes, in turn
     * @return the document
     */
    private static SemanticDocument with(String... pairs) {
        Map<String, String> values = invoice();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return document(values);
    }

    /** Returns a document of the paths a map states a value at. */
    private static SemanticDocument document(Map<String, String> values) {
        SemanticDocument.Builder builder = SemanticDocument.builder();
        values.forEach((path, content) -> {
            if (!content.isEmpty()) {
                builder.put(path, content);
            }
        });
        return builder.build();
    }

    /** Returns the code of a document, which the case then asks about. */
    private static Optional<EpcQrCode> code(SemanticDocument invoice) {
        return EpcQrCode.of(invoice);
    }

    /** Returns the payload of a document, and fails where there is no code. */
    private static String payload(SemanticDocument invoice) {
        return code(invoice).orElseThrow(() -> new AssertionError("no code")).payload();
    }

    // ---------------------------------------------------------------- what it carries

    @Test
    void thePayloadIsTheElementsOfTheGuidelineInItsOrder() {
        assertEquals(String.join("\n", "BCD", "002", "1", "SCT", "COBADEFFXXX",
                        "Example Systems GmbH", IBAN, "EUR2915.50", "", "",
                        "Invoice RE-2026-0042"),
                payload(with()),
                "the payload of the document as the guideline orders its elements");
    }

    /**
     * The elements at the end that say nothing are left off rather than written as empty
     * lines, which is what the guideline allows and what a shorter symbol is made of.
     */
    @Test
    void anEmptyRemittanceEndsThePayloadAtTheAmount() {
        assertEquals(String.join("\n", "BCD", "002", "1", "SCT", "COBADEFFXXX",
                        "Example Systems GmbH", IBAN, "EUR2915.50"),
                payload(with("/BG-16/BT-83", "", "/BT-1", "")),
                "nothing trails the amount where there is nothing to say");
    }

    /** The BIC may be empty in this version of the payload, and its line stays. */
    @Test
    void anAbsentBicLeavesItsLineEmpty() {
        assertEquals(String.join("\n", "BCD", "002", "1", "SCT", "",
                        "Example Systems GmbH", IBAN, "EUR2915.50", "", "",
                        "Invoice RE-2026-0042"),
                payload(with("/BG-16/BG-17/0/BT-86", "")),
                "the line of the BIC is there and empty");
    }

    /** The beneficiary is the account name, then the payee, then the seller. */
    @Test
    void theBeneficiaryFallsBackFromTheAccountToThePayeeToTheSeller() {
        assertTrue(payload(with()).contains("\nExample Systems GmbH\n"),
                "the account name of BT-85 where the document states one");
        assertTrue(payload(with("/BG-16/BG-17/0/BT-85", "",
                        "/BG-10/BT-59", "Example Factoring GmbH"))
                        .contains("\nExample Factoring GmbH\n"),
                "the payee of BT-59 where it does not");
        assertTrue(payload(with("/BG-16/BG-17/0/BT-85", ""))
                        .contains("\nExample GmbH\n"),
                "and the seller of BT-27 where there is no payee either");
    }

    /** With no name anywhere there is no beneficiary, and so no code. */
    @Test
    void aDocumentWithoutAnyNameGetsNoCode() {
        assertFalse(code(with("/BG-16/BG-17/0/BT-85", "", "/BG-4/BT-27", ""))
                .isPresent(), "a payload without a beneficiary is no payload");
    }

    /** The remittance information is BT-83, and the invoice number where there is none. */
    @Test
    void theRemittanceFallsBackToTheInvoiceNumber() {
        assertTrue(payload(with("/BG-16/BT-83", "")).endsWith("\nRE-2026-0042"),
                "the invoice number stands where the document states no remittance text");
    }

    /**
     * An IBAN is written on paper in groups of four and in the payload as one word. A
     * document that carries the printed form is read, and what the code carries is the
     * identifier.
     */
    @Test
    void spacesOfAPrintedIbanDoNotReachThePayload() {
        assertTrue(payload(with("/BG-16/BG-17/0/BT-84", "DE89 3704 0044 0532 0130 00"))
                        .contains("\n" + IBAN + "\n"),
                "the identifier without the grouping a reader sees");
    }

    /** An umlaut reaches the payload as itself, and is two bytes of the bound. */
    @Test
    void aNameWithUmlautsIsCarriedAsItStands() {
        String name = "Bäckerei Groß & Söhne KG";

        String payload = payload(with("/BG-16/BG-17/0/BT-85", name));

        assertTrue(payload.contains("\n" + name + "\n"), "the name as the document wrote it");
        assertEquals(payload.length() + 3,
                payload.getBytes(StandardCharsets.UTF_8).length,
                "and three characters of it are two bytes each");
    }

    // ---------------------------------------------------------------- the amount

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "999999999.99", "1", "12.5", "2915.50"})
    void anAmountInsideTheRangeIsCarriedAsTheDocumentWroteIt(String amount) {
        assertTrue(payload(with("/BG-22/BT-115", amount))
                        .contains("\nEUR" + amount + "\n"),
                amount + " is written behind the code of the currency, unchanged");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "1000000000.00", "-5.00", "2915.500", "", "2.5e3"})
    void anAmountOutsideTheRangeOrTheShapeGetsNoCode(String amount) {
        assertFalse(code(with("/BG-22/BT-115", amount)).isPresent(),
                amount + " is no amount of the guideline, so there is no code");
    }

    // ---------------------------------------------------------------- the conditions

    @ParameterizedTest
    @ValueSource(strings = {"30", "58"})
    void aCreditTransferGetsACode(String means) {
        assertTrue(code(with("/BG-16/BT-81", means)).isPresent(),
                means + " is a credit transfer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"49", "59", "10", "48", "97", ""})
    void everyOtherMeansOfPaymentGetsNone(String means) {
        assertFalse(code(with("/BG-16/BT-81", means)).isPresent(),
                means + " is not a credit transfer, so there is no code");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHF", "USD", "SEK", ""})
    void anInvoiceInAnotherCurrencyGetsNone(String currency) {
        assertFalse(code(with("/BT-5", currency)).isPresent(),
                "the guideline carries euro, not " + currency);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "DE89370400440532013001",  // the check digits do not hold
        "DE00370400440532013000",  // nor do these
        "1234567890",              // a domestic account number
        "de89370400440532013000",  // the letters are capitals
        "DE89"})                   // and there is a body behind them
    void anAccountIdentifierThatIsNotAWellFormedIbanGetsNone(String account) {
        assertFalse(code(with("/BG-16/BG-17/0/BT-84", account)).isPresent(),
                account + " is not a well-formed IBAN");
    }

    /** A few identifiers of other countries, to say that the check is the general one. */
    @ParameterizedTest
    @ValueSource(strings = {"DE89370400440532013000", "FR1420041010050500013M02606",
        "GB29NWBK60161331926819", "NL91ABNA0417164300", "AT611904300234573201"})
    void theCheckDigitsOfIso13616AreWhatIsTested(String account) {
        assertTrue(EpcQrCode.isIban(account), account + " holds the check of ISO 13616");
    }

    /** A payment instruction that is not a credit transfer does not stop a later one. */
    @Test
    void theFirstCreditTransferIsTheOneThatIsRead() {
        SemanticDocument both = SemanticDocument.builder()
                .put("/BT-1", "RE-2026-0042")
                .put("/BT-5", "EUR")
                .put("/BG-4/BT-27", "Example GmbH")
                .put("/BG-22/BT-115", "10.00")
                .put("/BG-16/0/BT-81", "49")
                .put("/BG-16/0/BG-17/0/BT-84", "DE89370400440532013000")
                .put("/BG-16/1/BT-81", "30")
                .put("/BG-16/1/BG-17/0/BT-84", "NL91ABNA0417164300")
                .build();

        assertTrue(payload(both).contains("\nNL91ABNA0417164300\n"),
                "the account of the credit transfer, not the one of the direct debit");
    }

    /** A credit transfer that names no account at all is no credit transfer to pay into. */
    @Test
    void aCreditTransferWithoutAnAccountGetsNone() {
        assertFalse(EpcQrCode.of(SemanticDocument.builder()
                        .put("/BT-1", "RE-2026-0042")
                        .put("/BT-5", "EUR")
                        .put("/BG-4/BT-27", "Example GmbH")
                        .put("/BG-22/BT-115", "10.00")
                        .put("/BG-16/BT-81", "58")
                        .build()).isPresent(),
                "there is no account to write into the payload");
    }

    /** An element that would be two elements to a reader of the code is no element. */
    @Test
    void anElementCarryingALineBreakGetsNoCode() {
        assertFalse(code(with("/BG-16/BT-83", "Invoice RE-2026-0042\nand more"))
                .isPresent(), "a line break inside an element would shift every line after it");
    }

    // ---------------------------------------------------------------- the characters

    /**
     * Every character no element may carry: the seven ways of ending a line, which a
     * reader that ends a line where Unicode does would read as one, the tabulator and the
     * other controls of C0 and C1, U+007F, the characters that direct the reading order,
     * and half of a surrogate pair, which UTF-8 cannot encode.
     */
    static Stream<String> notWritable() {
        return Stream.of("\n", "\r", "\r\n", "\u000b", "\u000c", "\u0085", "\u2028",
                "\u2029", "\t", "\u0000", "\u001b", "\u007f", "\u009b", "\u061c",
                "\u200e", "\u202e", "\u2066", "\ud800");
    }

    /**
     * The attack the strict payload is about: an account name that carries the line ends
     * of a reader that splits where Unicode does, with an account and an amount of its own
     * behind them. Such a reader would take the injected account for the IBAN and the
     * injected figure for the amount, while the letter prints the seller's account beside
     * the code. There is no code, and the letter is told why.
     *
     * @param character what separates the injected elements
     */
    @ParameterizedTest
    @MethodSource("notWritable")
    void aNameThatCarriesOneGetsNoCodeAndSaysWhy(String character) {
        EpcQrCode.Decision decision = EpcQrCode.decide(with("/BG-16/BG-17/0/BT-85",
                "Example GmbH" + character + "DE02120300000000202051" + character + "EUR1.00"));

        assertFalse(decision.code().isPresent(), "no code rather than a code that misleads");
        assertEquals(Optional.of(EpcQrCode.Remark.NAME_NOT_WRITABLE), decision.withheld(),
                "and the reason is the name");
    }

    /**
     * The remittance information is withheld the same way. It is not replaced by the
     * invoice number, as a text too long for the code is: the number would be a reference
     * the payer did not see on the page where the text was meant to be.
     *
     * @param character what the text carries
     */
    @ParameterizedTest
    @MethodSource("notWritable")
    void aRemittanceTextThatCarriesOneGetsNoCodeAndSaysWhy(String character) {
        EpcQrCode.Decision decision = EpcQrCode.decide(
                with("/BG-16/BT-83", "Invoice" + character + "RE-2026-0042"));

        assertFalse(decision.code().isPresent(), "no code");
        assertEquals(Optional.of(EpcQrCode.Remark.REMITTANCE_NOT_WRITABLE),
                decision.withheld(), "and the reason is the remittance information");
    }

    /**
     * And the invoice number, where it is what the code would carry as the reference:
     * because the document states no remittance text, or one too long for the code.
     *
     * @param character what the number carries
     */
    @ParameterizedTest
    @MethodSource("notWritable")
    void anInvoiceNumberThatWouldBeTheReferenceAndCarriesOneGetsNoCode(String character) {
        String number = "RE-2026" + character + "0042";
        for (String stated : List.of("", "Reference ".repeat(19) + "Reference0")) {
            EpcQrCode.Decision decision =
                    EpcQrCode.decide(with("/BG-16/BT-83", stated, "/BT-1", number));

            assertFalse(decision.code().isPresent(), "no code");
            assertEquals(Optional.of(EpcQrCode.Remark.NUMBER_NOT_WRITABLE),
                    decision.withheld(), "and the reason is the invoice number");
        }
    }

    /** An invoice number the code does not carry may say what it likes. */
    @Test
    void anInvoiceNumberTheCodeDoesNotCarryIsNoReason() {
        EpcQrCode.Decision decision = EpcQrCode.decide(with("/BT-1", "RE-2026\n0042"));

        assertTrue(decision.code().isPresent(), "the remittance text is the reference");
        assertEquals(Optional.empty(), decision.withheld());
    }

    /**
     * The other conditions of the guideline cost the code without a word, because they are
     * what the document is and the letter has nothing to explain.
     */
    @Test
    void aDocumentTheGuidelineIsNotForIsOwedNoReason() {
        assertEquals(new EpcQrCode.Decision(Optional.empty(), Optional.empty()),
                EpcQrCode.decide(with("/BG-16/BT-81", "59",
                        "/BG-16/BG-17/0/BT-85", "Example\nGmbH")),
                "a direct debit gets no code, whatever its account name carries");
    }

    /** What may stand in an element: every printable character of every script. */
    @ParameterizedTest
    @ValueSource(strings = {"Bäckerei Groß & Söhne KG", "Ελληνικά Α.Ε.", "株式会社例",
        "O'Brien \"Quotes\" <tag>", "Example GmbH DE02120300000000202051 EUR1.00",
        "Emoji \ud83d\ude00 GmbH", "non\u00a0breaking"})
    void printableTextOfAnyScriptIsWritable(String text) {
        assertTrue(EpcQrCode.writable(text), text);
    }

    // ---------------------------------------------------------------- the lengths

    @Test
    void aNameLongerThanTheGuidelineAllowsIsWrittenToThatLength() {
        String name = "Beispiel ".repeat(12).strip();

        EpcQrCode code = code(with("/BG-16/BG-17/0/BT-85", name)).orElseThrow();

        assertTrue(name.length() > EpcQrCode.NAME_LIMIT, "the name is longer than the limit");
        assertTrue(code.payload().contains(
                        "\n" + name.substring(0, EpcQrCode.NAME_LIMIT) + "\n"),
                "the first 70 characters of it are in the payload");
        assertEquals(List.of(EpcQrCode.Remark.BENEFICIARY_CUT), code.remarks(),
                "and the code says that it was written to that length");
    }

    /**
     * A remittance information past the length is not written short: the code names the
     * invoice instead.
     *
     * <p>What the payer's bank transmits with the money is what the seller reconciles the
     * payment on. A reference cut in the middle of a word reconciles against nothing,
     * while the invoice number is the reference the seller has been using all along and
     * the one the guideline's own fallback names.
     */
    @Test
    void aRemittanceTextLongerThanTheGuidelineAllowsGivesWayToTheInvoiceNumber() {
        String text = "Reference ".repeat(19) + "Reference0";

        EpcQrCode code = code(with("/BG-16/BT-83", text)).orElseThrow();

        assertEquals(200, text.length(), "two hundred characters of remittance information");
        assertTrue(code.payload().endsWith("\nRE-2026-0042"),
                "the invoice number closes the payload: " + code.payload());
        assertFalse(code.payload().contains(text.substring(0, 40)),
                "and no part of the text is in it");
        assertEquals(List.of(EpcQrCode.Remark.REMITTANCE_REPLACED), code.remarks(),
                "and it says so");
    }

    /** With no invoice number to put in its place there is nothing left to pay against. */
    @Test
    void aRemittanceTextTooLongAndNoInvoiceNumberGetsNoCode() {
        assertFalse(code(with("/BG-16/BT-83", "Reference ".repeat(19) + "Reference0",
                        "/BT-1", "")).isPresent(),
                "the code would carry no reference the seller could reconcile on");
    }

    /** Nothing is cut where nothing is over the length, and then there is nothing to say. */
    @Test
    void anInvoiceInsideTheLengthsSaysNothingAboutThem() {
        assertEquals(List.of(), code(with()).orElseThrow().remarks(),
                "every element fits, so no element was written short");
    }

    // ---------------------------------------------------------------- the bank identifier

    /**
     * A bank identifier that is not a BIC is left out of the payload rather than copied
     * into it.
     *
     * <p>The corpus carries the case: an instance that states the word {@code [BIC]} where
     * the identifier belongs. A banking application refuses a payload whose fifth element
     * is not a BIC, and refusing the payload is refusing the code. The guideline lets the
     * element be empty in this version, so the code keeps everything else and the letter
     * says which element it lost.
     *
     * @param stated what the document states as the bank identifier
     */
    @ParameterizedTest
    @ValueSource(strings = {"[BIC]", "COBADEFF1", "cobadeffxxx", "COBADEFFXXXX", "12345678"})
    void aBankIdentifierThatIsNotABicIsLeftOutOfThePayload(String stated) {
        EpcQrCode code = code(with("/BG-16/BG-17/0/BT-86", stated)).orElseThrow();

        assertTrue(code.payload().startsWith("BCD\n002\n1\nSCT\n\n"),
                "the element is empty: " + code.payload());
        assertFalse(code.payload().contains(stated), "and the identifier is not in it");
        assertEquals(List.of(EpcQrCode.Remark.BIC_LEFT_OUT), code.remarks(),
                "and the code says so");
    }

    /** A BIC of eight or of eleven characters is one, and stands in the payload. */
    @ParameterizedTest
    @ValueSource(strings = {"COBADEFF", "COBADEFFXXX", "BYLADEM1001"})
    void aWellFormedBicStandsInThePayload(String bic) {
        EpcQrCode code = code(with("/BG-16/BG-17/0/BT-86", bic)).orElseThrow();

        assertTrue(code.payload().contains("\nSCT\n" + bic + "\n"),
                bic + " stands in the payload: " + code.payload());
        assertEquals(List.of(), code.remarks(), "and there is nothing to say about it");
    }

    /**
     * The lengths of the elements are not the bound on the payload. Seventy characters of
     * umlauts and a hundred and forty more are two hundred and ten characters and four
     * hundred and twenty bytes, which is past what the symbol may carry.
     */
    @Test
    void aPayloadPastTheBoundGetsNoCode() {
        assertFalse(code(with("/BG-16/BG-17/0/BT-85", "ä".repeat(EpcQrCode.NAME_LIMIT),
                        "/BG-16/BT-83", "ö".repeat(EpcQrCode.REMITTANCE_LIMIT)))
                        .isPresent(),
                "the elements are inside their lengths and the payload is not inside its bound");
    }

    // ---------------------------------------------------------------- the symbol

    @Test
    void theSymbolIsInsideTheVersionTheGuidelineAllows() {
        EpcQrCode code = code(with()).orElseThrow();

        assertTrue(code.version() <= EpcQrCode.MAX_VERSION,
                "version " + code.version() + " is inside the bound of the guideline");
        assertEquals(code.modules(), code.cells().length, "a square of modules");
        assertTrue(code.cells()[0][0] && code.cells()[0][6],
                "whose first row opens with the corner pattern every symbol carries");
    }

    /** The matrix a caller gets is its own, so that a layout cannot change the code. */
    @Test
    void theMatrixIsACopy() {
        EpcQrCode code = code(with()).orElseThrow();

        boolean[][] first = code.cells();
        first[0][0] = !first[0][0];

        assertTrue(code.cells()[0][0], "the code is unchanged by what a caller did to a copy");
    }

    /** The same document gives the same payload and the same symbol, run after run. */
    @Test
    void theCodeOfADocumentIsAlwaysTheSameCode() {
        EpcQrCode once = code(with()).orElseThrow();
        EpcQrCode again = code(with()).orElseThrow();

        assertEquals(once.payload(), again.payload(), "the same payload");
        for (int row = 0; row < once.modules(); row++) {
            assertEquals(List.of(box(once.cells()[row])), List.of(box(again.cells()[row])),
                    "and row " + row + " of the same symbol");
        }
    }

    /** Returns a row of modules as objects, so that a failure prints what differed. */
    private static Boolean[] box(boolean[] row) {
        Boolean[] boxed = new Boolean[row.length];
        for (int i = 0; i < row.length; i++) {
            boxed[i] = row[i];
        }
        return boxed;
    }
}
