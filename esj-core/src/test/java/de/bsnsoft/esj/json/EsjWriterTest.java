package de.bsnsoft.esj.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.Fixtures;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticValue;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Checks the two serializations of the specification, sections 7 and 7.7. */
class EsjWriterTest {

    private static final String HEAD =
            "{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                    + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",";

    @Test
    void theCanonicalFormHasNoWhitespaceAndNoTrailingNewline() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", SemanticValue.of("RE-1"))
                .build();
        String text = EsjWriter.canonical().toText(document);
        assertEquals(HEAD + "\"values\":{\"/BT-1\":\"RE-1\"}}", text);
    }

    @Test
    void theTopLevelMembersKeepTheOrderOfTheSpecification() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", SemanticValue.of("RE-1"))
                .source("UBL", "a".repeat(64))
                .extension("de.example.vendor", ExtensionValue.of("x"))
                .build();
        String text = EsjWriter.canonical().toText(document);
        assertTrue(text.startsWith(HEAD + "\"values\":"), text);
        assertTrue(text.indexOf("\"values\":") < text.indexOf("\"extensions\":"));
        assertTrue(text.indexOf("\"extensions\":") < text.indexOf("\"source\":"));
        assertTrue(text.endsWith("\"source\":{\"syntax\":\"UBL\",\"sha256\":\"" + "a".repeat(64) + "\"}}"));
    }

    @Test
    void theMembersOfAValueObjectKeepTheOrderOfTheSpecification() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-25/0/BG-31/BT-158/0",
                        SemanticValue.identifier("4711", "0160", "2"))
                .put("/BG-24/0/BT-125",
                        SemanticValue.binary("ABCE".getBytes(StandardCharsets.UTF_8),
                                "application/pdf", "note.pdf"))
                .build();

        String text = EsjWriter.canonical().toText(document);

        assertTrue(text.contains("{\"value\":\"4711\",\"scheme\":\"0160\","
                + "\"schemeVersion\":\"2\"}"), text);
        assertTrue(text.contains("{\"value\":\"QUJDRQ==\",\"mimeCode\":\"application/pdf\","
                + "\"filename\":\"note.pdf\"}"), text);
    }

    /**
     * The shape of a value is decided by its content (specification, section 6.1): a value
     * with no supplementary component is a JSON string, one with a component is an object,
     * and no writer has a choice between the two.
     */
    @Test
    void theContentDecidesWhetherAValueIsAStringOrAnObject() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-1", "RE-1")
                .put("/BG-4/BT-29/0", SemanticValue.identifier("0088123456785", "0088"))
                .build();

        String text = EsjWriter.canonical().toText(document);

        assertTrue(text.contains("\"/BT-1\":\"RE-1\""), text);
        assertTrue(text.contains("\"/BG-4/BT-29/0\":{\"value\":\"0088123456785\","
                + "\"scheme\":\"0088\"}"), text);
    }

    /**
     * A writer serializes a document and never rewrites a value: a content outside the
     * grammar its term requires travels through unchanged and is refused at layer L2
     * (specification, sections 3.4 and 6.4).
     */
    @Test
    void theContentOfAValueIsWrittenAsItStands() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-22/BT-106", "100.00")
                .put("/BT-2", "2026-02-30")
                .build();

        String text = EsjWriter.canonical().toText(document);

        assertTrue(text.contains("\"/BT-2\":\"2026-02-30\""), text);
        assertTrue(text.contains("\"/BG-22/BT-106\":\"100.00\""), text);
    }

    @Test
    void stringsAreEscapedExactlyAsTheSpecificationSays() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-25/0/BG-31/BT-153",
                        SemanticValue.of("a\"b\\c\nd\te\u0001f€"))
                .build();
        String text = EsjWriter.canonical().toText(document);
        assertTrue(text.contains("\"a\\\"b\\\\c\\nd\\te\\u0001f€\""), text);
    }

    @Test
    void theSolidusAndEveryCodePointAboveAsciiAreWrittenLiterally() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-2/BT-24", SemanticValue.of("urn:cen.eu:en16931:2017"))
                .put("/BG-4/BT-27", SemanticValue.of("Ä中💶/ "))
                .build();
        String text = EsjWriter.canonical().toText(document);
        assertTrue(text.contains("urn:cen.eu:en16931:2017"), text);
        assertTrue(text.contains("\"Ä中💶/ \""), text);
    }

    @Test
    void theControlCharactersBelowSpaceUseTheirOwnEscapes() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-4/BT-27", SemanticValue.of("\b\f\u0000\u001f\u007f"))
                .build();
        String text = EsjWriter.canonical().toText(document);
        assertTrue(text.contains("\"\\b\\f\\u0000\\u001f\""), text);
    }

    @Test
    void theCanonicalBytesAreUtf8WithoutAByteOrderMark() {
        byte[] bytes = EsjWriter.canonical().toBytes(SemanticDocument.builder()
                .put("/BG-4/BT-27", SemanticValue.of("Ä"))
                .build());
        assertEquals('{', bytes[0]);
        assertEquals('}', bytes[bytes.length - 1]);
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("Ä"));
    }

    @Test
    void thePrettyFormIndentsByTwoSpacesAndEndsWithOneNewline() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BT-2", SemanticValue.ofDate(LocalDate.of(2026, 1, 15)))
                .build();

        assertEquals("""
                {
                  "format": "EN16931-Semantic-JSON",
                  "version": "0.1",
                  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
                  "values": {
                    "/BT-2": "2026-01-15"
                  }
                }
                """, EsjWriter.pretty().toText(document));
    }

    @Test
    void anEmptyValuesObjectIsWrittenOnOneLineInBothForms() {
        SemanticDocument document = SemanticDocument.builder().build();
        assertTrue(EsjWriter.canonical().toText(document).endsWith("\"values\":{}}"));
        assertTrue(EsjWriter.pretty().toText(document).contains("\"values\": {}\n}"));
    }

    @Test
    void anArrayInsideExtensionsKeepsItsOrderAndIsWrittenOnePerLine() {
        SemanticDocument document = SemanticDocument.builder()
                .extension("de.example.vendor", ExtensionValue.object(Map.of(
                        "order", ExtensionValue.array(List.of(
                                ExtensionValue.of(new BigDecimal("3")),
                                ExtensionValue.of(new BigDecimal("1")),
                                ExtensionValue.of(new BigDecimal("2")))),
                        "empty", ExtensionValue.array(List.of()),
                        "nothing", ExtensionValue.object(Map.of()))))
                .build();
        assertTrue(EsjWriter.canonical().toText(document).contains(
                "{\"empty\":[],\"nothing\":{},\"order\":[3,1,2]}"),
                EsjWriter.canonical().toText(document));
        assertTrue(EsjWriter.pretty().toText(document).contains(
                "      \"empty\": [],\n"
                        + "      \"nothing\": {},\n"
                        + "      \"order\": [\n"
                        + "        3,\n"
                        + "        1,\n"
                        + "        2\n"
                        + "      ]\n"), EsjWriter.pretty().toText(document));
    }

    @Test
    void aNumberInsideExtensionsIsWrittenInItsCanonicalDecimalForm() {
        SemanticDocument document = SemanticDocument.builder()
                .extension("de.example.vendor", ExtensionValue.object(Map.of(
                        "big", ExtensionValue.of(new BigDecimal("1e21")),
                        "small", ExtensionValue.of(new BigDecimal("1e-7")),
                        "zero", ExtensionValue.of(new BigDecimal("-0.0")),
                        "exact", ExtensionValue.of(new BigDecimal("1.0000000000000001")))))
                .build();
        assertTrue(EsjWriter.canonical().toText(document).contains(
                "{\"big\":1000000000000000000000,\"exact\":1.0000000000000001,"
                        + "\"small\":0.0000001,\"zero\":0}"),
                EsjWriter.canonical().toText(document));
    }

    @Test
    void aNumberInsideExtensionsWithNoCanonicalFormIsRefused() {
        SemanticDocument document = SemanticDocument.builder()
                .extension("de.example.vendor", ExtensionValue.of(new BigDecimal("1e400")))
                .build();
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> EsjWriter.canonical().toBytes(document));
        assertEquals("ESJ-L1-EXT-NUMBER", thrown.code().orElseThrow().code());
    }

    @Test
    void aLoneSurrogateHasNoUtf8EncodingAndIsRefused() {
        SemanticDocument document = SemanticDocument.builder()
                .put("/BG-4/BT-27", SemanticValue.of("a\ud800b"))
                .build();
        EsjFormatException thrown = assertThrows(EsjFormatException.class,
                () -> EsjWriter.canonical().toBytes(document));
        assertEquals("ESJ-L1-SURROGATE", thrown.code().orElseThrow().code());
    }

    @Test
    void theStreamAndTheWriterProduceTheSameText() throws Exception {
        SemanticDocument document = Fixtures.minimalInvoice().build();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        EsjWriter.canonical().write(document, stream);
        StringWriter writer = new StringWriter();
        EsjWriter.canonical().write(document, writer);
        assertArrayEquals(EsjWriter.canonical().toBytes(document), stream.toByteArray());
        assertEquals(new String(stream.toByteArray(), StandardCharsets.UTF_8), writer.toString());
    }

    @Test
    void theStyleIsReported() {
        assertEquals(EsjWriter.Style.CANONICAL, EsjWriter.canonical().style());
        assertEquals(EsjWriter.Style.PRETTY, EsjWriter.of(EsjWriter.Style.PRETTY).style());
    }
}
