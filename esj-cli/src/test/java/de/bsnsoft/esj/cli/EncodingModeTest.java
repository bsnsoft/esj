package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * The two ways this tool answers a document whose bytes are not written in the encoding it
 * declares, and why they are two.
 *
 * <p>{@code esj validate} gives a verdict on the bytes it was handed, so it repairs
 * nothing: a document that says UTF-8 and is Latin-1 is invalid as it stands, whatever it
 * would say once recoded, and a validator that quietly recoded it first would answer a
 * question nobody asked. Every other command exists to get at the content, so it recodes,
 * says on the error stream that it did, and records it in the report — because a repair
 * that leaves no trace is indistinguishable from bytes that never needed one.
 */
class EncodingModeTest {

    private static final String CII =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml";

    /** A phrase of the corpus invoice that only survives the recoding intact. */
    private static final String UMLAUT = "Geschäftsbedingungen";

    @Test
    void everyOtherCommandRecodesAndSaysSo() {
        Cli.Run run = Cli.run(latin1(), "list", "-");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("encoding repaired: declared UTF-8, read as ISO-8859-1"),
                run.err());
        assertTrue(run.text().contains(UMLAUT),
                "the recoded document carries the characters the bytes held");
    }

    @Test
    void theOtherReaderRepairsAndSaysSoAsWell() {
        Cli.Run run = Cli.run(latin1(), "--importer", "xslt", "list", "-");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("encoding repaired: declared UTF-8, read as ISO-8859-1"),
                run.err());
        assertTrue(run.text().contains(UMLAUT),
                "the reader a run was given does not change what a repair produces");
    }

    @Test
    void strictTurnsTheRepairOffInTheOtherReaderToo() {
        Cli.Run run = Cli.run(latin1(), "--importer", "xslt", "convert", "-", "--strict");
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("not written in the encoding it declares"), run.err());
    }

    @Test
    void aRecodingIsNotAReportOfLostContent() {
        Cli.Run run = Cli.run(latin1(), "convert", "-");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(run.err().contains("did not reach the document"), run.err());
    }

    @Test
    void strictTurnsTheRepairOffEverywhere() {
        Cli.Run run = Cli.run(latin1(), "convert", "-", "--strict");
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("not written in the encoding it declares"), run.err());
        assertTrue(run.err().contains("without --strict"), run.err());
    }

    @Test
    void validateIsStrictWithoutBeingAsked() {
        Cli.Run run = Cli.run(latin1(), "validate", "-");
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err() + run.text());
        String text = run.text();
        assertTrue(text.contains("XML bytes:                 1 error"), text);
        assertTrue(text.contains("XML-ENCODING [error]"), text);
        assertTrue(text.contains("declared UTF-8, and the bytes are ISO-8859-1"), text);
        assertTrue(text.contains("Model (L2):                not checked"), text);
    }

    @Test
    void afterRepairIsInformationAndNotAVerdict() {
        Cli.Run run = Cli.run(latin1(), "validate", "-", "--after-repair");
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err() + run.text());
        String text = run.text();
        assertTrue(text.contains("after repair (informational)"), text);
        assertTrue(text.indexOf("Model (L2):                OK") > text.indexOf("after repair"), text);
        assertTrue(text.contains("Verdict:          INVALID"), text);
    }

    @Test
    void jsonCarriesTheSyntaxFinding() {
        Cli.Run run = Cli.run(latin1(), "validate", "-", "--output", "json");
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        String json = run.text();
        assertTrue(json.contains("\"xml\": {"), json);
        assertTrue(json.contains("\"ok\": false"), json);
        assertTrue(json.contains("\"category\": \"XML\""), json);
        assertTrue(json.contains("\"code\": \"XML-ENCODING\""), json);
    }

    @Test
    void theNoteOfARepairIsInTheReportOfEveryOtherCommand() {
        Cli.Run run = Cli.run(latin1(), "inspect", "-");
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err() + run.text());
        assertTrue(run.err().contains("encoding repaired"), run.err());
    }

    @Test
    void bytesThatAreWhatTheySayTheyAreSayNothingAtAll() {
        Cli.Run run = Cli.run(Fixtures.bytes(CII), "convert", "-");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(run.err().contains("encoding repaired"), run.err());
    }

    @Test
    void aContainerCarriesTheSameSplitThroughToItsAttachment() {
        Cli.Run run = Cli.run(TestPdfs.facturX(latin1()), "validate", "-");
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err() + run.text());
        assertTrue(run.text().contains("XML-ENCODING [error]"), run.text());
        assertTrue(run.text().contains("Invoice:          INVALID"), run.text());
        assertTrue(run.text().contains("Container:        OK"), run.text());
    }

    @Test
    void aRepairThatGrowsPastTheBoundNamesTheSwitchInBothReaders() {
        byte[] bytes = latin1();
        String bound = String.valueOf(bytes.length);
        for (String[] reader : new String[][] {{}, {"--importer", "xslt"}}) {
            Cli.Run run = Cli.run(bytes, arguments(reader, bound));
            assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
            assertTrue(run.err().contains("once recoded into UTF-8"), run.err());
            assertTrue(run.err().contains("--max-input-bytes raises that bound"), run.err());
        }
    }

    /**
     * Bytes that decode neither in the charset they declare nor in one this tool recodes
     * from are refused by every command that reads them, by both readers and in both modes:
     * read anyway, they would carry replacement characters the sender never wrote, and the
     * repair note would have nothing true to say.
     */
    @Test
    void bytesThatDecodeInNoCharsetThisToolReadsAreReadByNoCommand() {
        byte[] bytes = undecodableShiftJis();
        List<String[]> commands = List.of(
                new String[] {"convert", "-"},
                new String[] {"convert", "--strict", "-"},
                new String[] {"--importer", "xslt", "convert", "-"},
                new String[] {"--importer", "xslt", "convert", "--strict", "-"},
                new String[] {"inspect", "-"},
                new String[] {"list", "-"},
                new String[] {"get", "-", "/BT-1"},
                new String[] {"canonicalize", "-"},
                new String[] {"render", "-", "--html", "--out", "-"});
        for (String[] command : commands) {
            Cli.Run run = withoutTheErrorStreamOfTheProcess(() -> Cli.run(bytes, command));
            String line = String.join(" ", command);
            assertEquals(ExitCode.INPUT, run.exitCode(), line + ": " + run.err());
            assertTrue(run.err().contains("declared Shift_JIS"), line + ": " + run.err());
            assertTrue(run.err().contains("so it was not read"), line + ": " + run.err());
            assertFalse(run.err().contains("without --strict"), line + ": " + run.err());
            assertFalse(run.text().contains("�"), line + ": " + run.text());
        }
    }

    @Test
    void validateCallsSuchBytesInvalidAndOffersNoRecoding() {
        Cli.Run run = Cli.run(undecodableShiftJis(), "validate", "--after-repair", "-");

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err() + run.text());
        assertTrue(run.text().contains("XML-ENCODING [error]"), run.text());
        assertTrue(run.text().contains("no recoding of it is offered"), run.text());
        assertTrue(run.text().contains("Model (L2):                not checked"), run.text());
        assertTrue(run.text().contains("the recoded bytes could not be read either"),
                run.text());
    }

    @Test
    void aContainerCarriesSuchBytesThroughToTheSameRefusal() {
        byte[] pdf = TestPdfs.facturX(undecodableShiftJis());

        Cli.Run convert = Cli.run(pdf, "convert", "-");
        Cli.Run validate = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.INPUT, convert.exitCode(), convert.err());
        assertTrue(convert.err().contains("so it was not read"), convert.err());
        assertEquals(ExitCode.VALIDATION, validate.exitCode(), validate.err());
        assertTrue(validate.text().contains("XML-ENCODING [error]"), validate.text());
        assertTrue(validate.text().contains("Container:        OK"), validate.text());
    }

    /**
     * No XML parser of the platform is handed bytes it cannot decode, so none of them
     * writes a line of its own to the error stream of the process: neither the parser that
     * finds the root element, nor the two readers, nor the syntax engine. A byte that does
     * not decode in a comment before the root element was the case that reached the first
     * of them, and with it the run said the document was of no syntax it knew.
     */
    @Test
    void noParserWritesToTheErrorStreamOfTheProcess() {
        String text = new String(Fixtures.bytes(CII), StandardCharsets.UTF_8);
        int root = text.indexOf("?>") + 2;
        byte[] commentBeforeTheRoot = concat(concat(
                text.substring(0, root).getBytes(StandardCharsets.UTF_8),
                new byte[] {'<', '!', '-', '-', ' ', (byte) 0xFC, ' ', '-', '-', '>'}),
                text.substring(root).getBytes(StandardCharsets.UTF_8));
        byte[] asciiWithLatin1 = latin1Declaring("US-ASCII");
        List<String[]> commands = List.of(
                new String[] {"convert", "-"},
                new String[] {"--importer", "xslt", "convert", "-"},
                new String[] {"convert", "--strict", "-"},
                new String[] {"inspect", "-"},
                new String[] {"validate", "-"},
                new String[] {"--importer", "xslt", "validate", "-"});
        for (byte[] bytes : List.of(commentBeforeTheRoot, asciiWithLatin1)) {
            for (String[] command : commands) {
                ByteArrayOutputStream process = new ByteArrayOutputStream();
                Cli.Run run = withErrorStreamOfTheProcess(process, () -> Cli.run(bytes, command));
                String line = String.join(" ", command);
                assertEquals("", process.toString(StandardCharsets.UTF_8), line);
                assertFalse(run.err().contains("neither an ESJ document"), line + ": "
                        + run.err());
            }
        }
        Cli.Run repaired = Cli.run(commentBeforeTheRoot, "convert", "-");
        assertEquals(ExitCode.SUCCESS, repaired.exitCode(), repaired.err());
        assertTrue(repaired.err().contains("encoding repaired: declared UTF-8"), repaired.err());
    }

    /**
     * Returns the corpus invoice declaring Shift_JIS, with a lead byte that starts no
     * character of it in a business term: the bytes are neither Shift_JIS nor UTF-8.
     */
    private static byte[] undecodableShiftJis() {
        String text = new String(Fixtures.bytes(CII), StandardCharsets.UTF_8)
                .replace("encoding=\"UTF-8\"", "encoding=\"Shift_JIS\"");
        int at = text.indexOf(UMLAUT);
        return concat(concat(text.substring(0, at).getBytes(StandardCharsets.UTF_8),
                        new byte[] {(byte) 0x82, (byte) 0xFF}),
                text.substring(at).getBytes(StandardCharsets.UTF_8));
    }

    /** Returns the Latin-1 bytes of {@link #latin1()} under another declaration. */
    private static byte[] latin1Declaring(String charset) {
        return new String(latin1(), StandardCharsets.ISO_8859_1)
                .replace("encoding=\"UTF-8\"", "encoding=\"" + charset + "\"")
                .getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    /** Runs something with the error stream of the process going nowhere. */
    private static Cli.Run withoutTheErrorStreamOfTheProcess(Supplier<Cli.Run> run) {
        return withErrorStreamOfTheProcess(new ByteArrayOutputStream(), run);
    }

    /**
     * Runs something with the error stream of the process captured: a library that writes
     * there bypasses the streams the tool was given, which is what is asked about here.
     */
    private static Cli.Run withErrorStreamOfTheProcess(ByteArrayOutputStream captured,
                                                       Supplier<Cli.Run> run) {
        PrintStream original = System.err;
        try {
            System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
            return run.get();
        } finally {
            System.setErr(original);
        }
    }

    /** Returns the command line of the test above, for the reader it names. */
    private static String[] arguments(String[] reader, String bound) {
        List<String> arguments = new ArrayList<>(List.of(reader));
        arguments.addAll(List.of("--max-input-bytes", bound, "convert", "-"));
        return arguments.toArray(String[]::new);
    }

    /**
     * Returns the corpus invoice written in ISO-8859-1 while still declaring UTF-8, which
     * is what a writer produces that encodes with the platform's charset and copies the
     * declaration from a template.
     *
     * <p>The one character the corpus carries that Latin-1 has no place for is replaced
     * first, so that the bytes are a document some writer could actually have produced
     * rather than one this test mangled.
     */
    private static byte[] latin1() {
        String text = new String(Fixtures.bytes(CII), StandardCharsets.UTF_8)
                .replace('…', '.');
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }
}
