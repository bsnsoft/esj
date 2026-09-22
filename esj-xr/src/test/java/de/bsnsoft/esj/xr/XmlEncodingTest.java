package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportResult;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The XML front door: what the importer makes of a document whose bytes are not written
 * in the encoding it declares, in both of its modes.
 */
class XmlEncodingTest {

    /** The path of the seller name, which every variant below carries an umlaut in. */
    private static final String SELLER = "/BG-4/BT-27";

    /** The name the fixture carries, and the one the variants replace it with. */
    private static final String PLAIN = "Example GmbH";
    private static final String UMLAUT = "Beispiel Grünhöfe GmbH";

    private final XrImporter repairing = new XrImporter();
    private final XrImporter strict = new XrImporter().withEncodingMode(XrEncodingMode.STRICT);

    @Test
    void readsLatin1BytesThatDeclareUtf8() {
        byte[] document = encode(named(UMLAUT), "ISO-8859-1");

        XmlEncodingReport report = XmlBytes.inspect(document);
        assertEquals(Optional.empty(), report.byteOrderMark());
        assertEquals(Optional.of("UTF-8"), report.declaration());
        assertEquals("ISO-8859-1", report.assumed());
        assertFalse(report.consistent());
        assertTrue(report.repairable());

        ImportResult result = repairing.importUblWithReport(document);
        assertEquals(SemanticValue.of(UMLAUT), value(result.document(), SELLER));
        assertEquals(1, result.report().notes(ImportNote.Kind.ENCODING_REPAIRED).size());
        ImportNote note = result.report().notes(ImportNote.Kind.ENCODING_REPAIRED).get(0);
        assertEquals(ImportNote.Level.WARNING, note.level());
        assertTrue(note.message().contains("declared UTF-8"), note.message());
        assertTrue(note.message().contains("read as ISO-8859-1"), note.message());
    }

    @Test
    void refusesLatin1BytesThatDeclareUtf8WhenStrict() {
        byte[] document = encode(named(UMLAUT), "ISO-8859-1");

        XrEncodingException thrown =
                assertThrows(XrEncodingException.class, () -> strict.importUbl(document));
        assertEquals(Optional.of("UTF-8"), thrown.declared());
        assertEquals("ISO-8859-1", thrown.assumed());
    }

    @Test
    void readsUtf8BytesThatDeclareLatin1() {
        byte[] document = encode(declaring(named(UMLAUT), "ISO-8859-1"), "UTF-8");

        XmlEncodingReport report = XmlBytes.inspect(document);
        assertEquals(Optional.of("ISO-8859-1"), report.declaration());
        assertEquals("UTF-8", report.assumed());
        assertFalse(report.consistent());

        ImportResult result = repairing.importUblWithReport(document);
        assertEquals(SemanticValue.of(UMLAUT), value(result.document(), SELLER));
        assertEquals(1, result.report().notes(ImportNote.Kind.ENCODING_REPAIRED).size());
        assertThrows(XrEncodingException.class, () -> strict.importUbl(document));
    }

    @Test
    void readsWindows1252BytesThatDeclareUtf8() {
        // The two characters the range 0x80 to 0x9F exists for: a word processor's
        // quotation marks, which ISO-8859-1 has no place for at all.
        String quoted = "„Beispiel“ Grünhöfe GmbH";
        byte[] document = encode(named(quoted), "windows-1252");

        XmlEncodingReport report = XmlBytes.inspect(document);
        assertEquals("windows-1252", report.assumed());
        assertFalse(report.consistent());

        ImportResult result = repairing.importUblWithReport(document);
        assertEquals(SemanticValue.of(quoted), value(result.document(), SELLER));
        assertEquals(1, result.report().notes(ImportNote.Kind.ENCODING_REPAIRED).size());
    }

    @Test
    void readsLatin1BytesWithoutAnyDeclaration() {
        byte[] document = encode(withoutDeclaration(named(UMLAUT)), "ISO-8859-1");

        XmlEncodingReport report = XmlBytes.inspect(document);
        assertEquals(Optional.empty(), report.declaration());
        assertEquals("UTF-8", report.documented());
        assertEquals("ISO-8859-1", report.assumed());
        assertFalse(report.consistent());

        ImportResult result = repairing.importUblWithReport(document);
        assertEquals(SemanticValue.of(UMLAUT), value(result.document(), SELLER));
        assertEquals(1, result.report().notes(ImportNote.Kind.ENCODING_REPAIRED).size());

        // No declaration is added: UTF-8 is what an XML document without one is.
        byte[] repaired = XmlBytes.repair(document, report);
        assertFalse(new String(repaired, StandardCharsets.UTF_8).contains("<?xml"));
    }

    @Test
    void readsUtf16WithAByteOrderMarkWithoutCallingItADefect() {
        byte[] document = encode(declaring(named(UMLAUT), "UTF-16"), "UTF-16");

        XmlEncodingReport report = XmlBytes.inspect(document);
        assertEquals(Optional.of("UTF-16BE"), report.byteOrderMark());
        assertEquals(Optional.of("UTF-16"), report.declaration());
        assertTrue(report.consistent(), report.describe());

        // A consistent document is handed to the parser as it stands, in either mode.
        for (XrImporter importer : List.of(repairing, strict)) {
            ImportResult result = importer.importUblWithReport(document);
            assertEquals(SemanticValue.of(UMLAUT), value(result.document(), SELLER));
            assertEquals(List.of(), result.report().notes(ImportNote.Kind.ENCODING_REPAIRED));
        }
    }

    @Test
    void reportsAUtf16ByteOrderMarkAgainstAUtf8Declaration() {
        byte[] document = encode(named(UMLAUT), "UTF-16");

        XmlEncodingReport report = XmlBytes.inspect(document);
        assertEquals(Optional.of("UTF-16BE"), report.byteOrderMark());
        assertEquals(Optional.of("UTF-8"), report.declaration());
        assertEquals("UTF-16BE", report.assumed());
        assertFalse(report.consistent());

        ImportResult result = repairing.importUblWithReport(document);
        assertEquals(SemanticValue.of(UMLAUT), value(result.document(), SELLER));
        assertEquals(1, result.report().notes(ImportNote.Kind.ENCODING_REPAIRED).size());
    }

    @Test
    void namesUtf32RatherThanCallingTheMarkupIllFormed() {
        // XML allows UTF-32 and the parser of this platform does not read it. Handing the
        // bytes over anyway answers "not well-formed XML", which is the right exit code
        // for the wrong reason: it sends the reader to look at markup that is fine.
        for (String charset : List.of("UTF-32BE", "UTF-32LE")) {
            byte[] document = encode(declaring(named(UMLAUT), charset), charset);

            XrFormatException thrown = assertThrows(XrFormatException.class,
                    () -> repairing.importUblWithReport(document));

            assertTrue(thrown.getMessage().contains("UTF-32"), thrown.getMessage());
            assertFalse(thrown.getMessage().contains("well-formed"), thrown.getMessage());
        }
    }

    @Test
    void namesADeclaredCharsetThisRuntimeDoesNotKnow() {
        byte[] document = encode(declaring(named(PLAIN), "X-NO-SUCH-CHARSET"), "UTF-8");

        XrFormatException thrown = assertThrows(XrFormatException.class,
                () -> repairing.importUblWithReport(document));

        assertTrue(thrown.getMessage().contains("X-NO-SUCH-CHARSET"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("known to this runtime"),
                thrown.getMessage());
    }

    @Test
    void leavesAValidUtf8DocumentUntouched() {
        byte[] document = Instances.bytes("/ubl/credit-note.xml");

        XmlEncodingReport report = XmlBytes.inspect(document);
        assertEquals(Optional.empty(), report.byteOrderMark());
        assertEquals(Optional.of("UTF-8"), report.declaration());
        assertEquals("UTF-8", report.assumed());
        assertTrue(report.consistent());
        assertFalse(report.repairable());
        assertSame(document, XmlBytes.repair(document, report));

        ImportResult result = repairing.importUblWithReport(document);
        assertEquals(SemanticValue.of(PLAIN), value(result.document(), SELLER));
        assertEquals(List.of(), result.report().notes(ImportNote.Kind.ENCODING_REPAIRED));
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void neverTouchesAnInstanceOfTheCorpus(String instance) {
        XmlEncodingReport report = XmlBytes.inspect(Conformance.instance(instance));

        assertTrue(report.consistent(), instance + ": " + report.describe());
        assertFalse(report.repairable(), instance);
    }

    @Test
    void keepsTheDigestOfTheBytesThatWereHandedOver() {
        byte[] document = encode(named(UMLAUT), "ISO-8859-1");

        SemanticDocument imported = repairing.importUbl(document);

        assertEquals(Conformance.sha256(document),
                imported.source().orElseThrow().sha256().orElseThrow());
    }

    @Test
    void refusesToRepairACharsetItDoesNotRecode() {
        byte[] document = encode(declaring(named(PLAIN), "ISO-8859-7"), "ISO-8859-7");

        XmlEncodingReport report = XmlBytes.inspect(document);
        assertEquals(Optional.of("ISO-8859-7"), report.declaration());
        // Nothing is judged and nothing is guessed at: the parser is the party that knows
        // the whole list of charsets.
        assertTrue(report.consistent());
        assertFalse(report.repairable());
    }

    static List<String> corpus() {
        return Conformance.corpus();
    }

    /** The credit note fixture with the seller name replaced by one that carries umlauts. */
    private static String named(String seller) {
        return new String(Instances.bytes("/ubl/credit-note.xml"), StandardCharsets.UTF_8)
                .replace(PLAIN, seller);
    }

    private static String declaring(String document, String charset) {
        return document.replace("encoding=\"UTF-8\"", "encoding=\"" + charset + "\"");
    }

    private static String withoutDeclaration(String document) {
        return document.substring(document.indexOf("?>") + 2).stripLeading();
    }

    private static byte[] encode(String document, String charset) {
        return document.getBytes(Charset.forName(charset));
    }

    private static SemanticValue value(SemanticDocument document, String path) {
        return document.value(SemanticPath.of(path)).orElseThrow();
    }
}
