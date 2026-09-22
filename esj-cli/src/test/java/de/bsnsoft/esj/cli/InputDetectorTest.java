package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The one piece of syntax knowledge the command line owns.
 *
 * <p>Detection is checked here rather than only through the commands, because it has to
 * answer for byte sequences that no command could do anything with: an empty input, a
 * file of text, an XML document of a syntax nobody asked about. It also has to recognize
 * a root element without reading the rest of the document, which is why the fixtures here
 * are root elements and not invoices — whether the importer can then read the document is
 * the importer's question and is asked in {@code esj-xr}.
 */
class InputDetectorTest {

    private static final String UBL_INVOICE_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    private static final String UBL_CREDIT_NOTE_NS =
            "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    private static final String CII_NS =
            "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";

    @Test
    void recognizesAnEsjDocumentByItsOpeningBrace() {
        assertEquals(Optional.of(InputSyntax.ESJ), detect("{\"format\":\"x\"}"));
    }

    @Test
    void skipsWhitespaceBeforeTheFirstSignificantByte() {
        assertEquals(Optional.of(InputSyntax.ESJ), detect("\n\n   \t{}"));
    }

    @Test
    void skipsMoreWhitespaceThanAnyBufferHolds() {
        assertEquals(Optional.of(InputSyntax.ESJ), detect(" ".repeat(5000) + "{}"),
                "leading whitespace is unbounded in JSON, so the scan has no cap either");
    }

    @Test
    void writesAByteOfTheInputByItsValueAndNotAsACharacter() {
        String described = InputDetector.describe(new byte[] {(byte) 0xC3, (byte) 0xA4});
        assertTrue(described.contains("0xC3"), described);
        assertFalse(described.contains("\uFFC3"), "a byte is not a character: " + described);
    }

    @Test
    void doesNotWriteAControlByteOfTheInputIntoADiagnostic() {
        String described = InputDetector.describe(new byte[] {0x1B, '['});
        assertTrue(described.contains("0x1B"), described);
        assertFalse(described.contains("\u001b"),
                "the one byte quoted before any parser has looked at it: " + described);
    }

    @Test
    void decidesFromAPrefixWhetherAnInputBeginsAnXmlDocument() {
        assertEquals(Optional.of(Boolean.TRUE), beginsXml("  <Invoice"));
        assertEquals(Optional.of(Boolean.FALSE), beginsXml("\n{\"format\""));
        assertEquals(Optional.empty(), beginsXml("   \t\n"),
                "a prefix of nothing but whitespace decides nothing");
    }

    private static Optional<Boolean> beginsXml(String prefix) {
        byte[] bytes = prefix.getBytes(StandardCharsets.UTF_8);
        return InputDetector.beginsXml(bytes, bytes.length);
    }

    @Test
    void recognizesAPdfByItsHeaderAndByNothingElse() {
        assertTrue(InputDetector.isPdf("%PDF-1.7\n1 0 obj".getBytes(StandardCharsets.UTF_8)));
        assertFalse(InputDetector.isPdf("  %PDF-1.7".getBytes(StandardCharsets.UTF_8)),
                "the header of a PDF stands at the first byte, and a reader that looked past"
                        + " leading bytes would accept a file whose header is in the middle");
        assertFalse(InputDetector.isPdf("%PD".getBytes(StandardCharsets.UTF_8)));
        assertFalse(InputDetector.isPdf(new byte[0]));
    }

    @Test
    void decidesFromAPrefixWhetherAnInputIsAPdf() {
        assertEquals(Optional.of(Boolean.TRUE), beginsPdf("%PDF-1.4"));
        assertEquals(Optional.of(Boolean.FALSE), beginsPdf("<Invoice"));
        assertEquals(Optional.empty(), beginsPdf("%PD"),
                "a prefix shorter than the header decides nothing");
    }

    private static Optional<Boolean> beginsPdf(String prefix) {
        byte[] bytes = prefix.getBytes(StandardCharsets.UTF_8);
        return InputDetector.beginsPdf(bytes, bytes.length);
    }

    @Test
    void skipsAByteOrderMark() {
        byte[] marked = ("﻿<Invoice xmlns=\"" + UBL_INVOICE_NS + "\"/>")
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(Optional.of(InputSyntax.UBL_INVOICE), InputDetector.detect(marked));
    }

    @Test
    void recognizesAnUnmarkedWideDocumentInBothByteOrders() {
        // The same document in the two byte orders of UTF-16, with no mark in front of
        // it. A detector that scanned bytes would read the document of the one and the
        // padding of the other, and answer two different things about one document.
        String document = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><Invoice xmlns=\""
                + UBL_INVOICE_NS + "\"/>";
        for (String charset : List.of("UTF-16BE", "UTF-16LE")) {
            byte[] bytes = document.getBytes(Charset.forName(charset));

            assertEquals(Optional.of(InputSyntax.UBL_INVOICE), InputDetector.detect(bytes),
                    charset);
            assertEquals(Optional.of(Boolean.TRUE),
                    InputDetector.beginsXml(bytes, bytes.length), charset);
        }
    }

    @Test
    void waitsForTheThirdByteOfAnUnmarkedWideDocument() {
        // One zero byte says nothing yet: it is the first half of a character in UTF-16BE
        // and the beginning of no single-byte document.
        assertEquals(Optional.empty(), InputDetector.beginsXml(new byte[] {0x00}, 1));
        assertEquals(Optional.empty(), InputDetector.beginsXml(new byte[] {0x00, 0x3C}, 2));
        assertEquals(Optional.of(Boolean.TRUE),
                InputDetector.beginsXml(new byte[] {0x00, 0x3C, 0x00, 'I'}, 4));
    }

    @Test
    void recognizesTheThreeRootElements() {
        assertEquals(Optional.of(InputSyntax.UBL_INVOICE),
                detect("<Invoice xmlns=\"" + UBL_INVOICE_NS + "\"/>"));
        assertEquals(Optional.of(InputSyntax.UBL_CREDIT_NOTE),
                detect("<CreditNote xmlns=\"" + UBL_CREDIT_NOTE_NS + "\"/>"));
        assertEquals(Optional.of(InputSyntax.CII),
                detect("<rsm:CrossIndustryInvoice xmlns:rsm=\"" + CII_NS + "\"/>"));
    }

    @Test
    void reachesPastADeclarationAndAComment() {
        assertEquals(Optional.of(InputSyntax.UBL_INVOICE),
                detect("<?xml version=\"1.0\"?><!-- a comment --><Invoice xmlns=\""
                        + UBL_INVOICE_NS + "\"/>"));
    }

    @Test
    void recognizesNothingInARootElementOfAnotherVocabulary() {
        assertEquals(Optional.empty(), detect("<Invoice xmlns=\"urn:example:other\"/>"));
        assertEquals(Optional.empty(), detect("<html><body/></html>"));
    }

    @Test
    void recognizesNothingInTextAnEmptyInputOrBrokenXml() {
        assertEquals(Optional.empty(), detect("hello"));
        assertEquals(Optional.empty(), detect(""));
        assertEquals(Optional.empty(), detect("   \n  "));
        assertEquals(Optional.empty(), detect("<Invoice"));
    }

    @Test
    void describesWhatItSawForVerboseOutput() {
        assertTrue(InputDetector.describe("  {}".getBytes(StandardCharsets.UTF_8))
                .contains("first significant byte 0x7B '{' at offset 2"));
        assertTrue(InputDetector.describe(
                        ("<Invoice xmlns=\"" + UBL_INVOICE_NS + "\"/>")
                                .getBytes(StandardCharsets.UTF_8))
                .contains("root element {" + UBL_INVOICE_NS + "}Invoice"));
    }

    private static Optional<InputSyntax> detect(String content) {
        return InputDetector.detect(content.getBytes(StandardCharsets.UTF_8));
    }
}
