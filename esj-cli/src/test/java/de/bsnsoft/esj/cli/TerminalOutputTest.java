package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.bsnsoft.esj.Esj;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the commands write when the document carries characters that steer a terminal:
 * a C0 control starting an escape sequence, the delete character, the C1 controls CSI and
 * NEXT LINE, the line and paragraph separators, and the bidirectional controls U+061C,
 * U+200E, U+202E and U+2066.
 *
 * <p>Every command is held to one property rather than to a spelling: no byte it writes on
 * either stream is one of those characters, apart from the line feeds and tabs of its own
 * layout. The spelling is then checked where the escaping is reversible — the lines of
 * {@code esj list}, a JSON report parsed back — and the one deliberate exception, {@code esj
 * get}, is checked to stay raw.
 */
class TerminalOutputTest {

    /** The characters of {@link #HOSTILE}, one of every class, in a JSON string. */
    private static final String HOSTILE_JSON = "X\\u001b[2K\\u007f\\u009b\\u0085\\u2028"
            + "\\u2029\\u061c\\u200e\\u202e\\u2066Y";

    /** The same characters as the document carries them. */
    private static final String HOSTILE = "X" + (char) 0x1b + "[2K" + (char) 0x7f
            + (char) 0x9b + (char) 0x85 + (char) 0x2028 + (char) 0x2029 + (char) 0x061c
            + (char) 0x200e + (char) 0x202e + (char) 0x2066 + "Y";

    /** The one-line form {@link ValueText#oneLine(String)} writes them in. */
    private static final String ESCAPED = "X\\u001b[2K\\u007f\\u009b\\u0085\\u2028"
            + "\\u2029\\u061c\\u200e\\u202e\\u2066Y";

    @TempDir
    private Path directory;

    /** A valid invoice with the hostile text as its number and as the seller's name. */
    private static byte[] hostileInvoice() {
        String document = Fixtures.text("examples/standard-invoice.esj.json")
                .replace("\"RE-2026-0042\"", "\"" + HOSTILE_JSON + "\"");
        assertTrue(document.contains(HOSTILE_JSON), "the fixture carries the invoice number");
        return document.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void theHostileTextIsWhatTheDocumentCarries() {
        Cli.Run run = Cli.run(hostileInvoice(), "get", "-", "/BT-1");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals(HOSTILE + "\n", run.text(),
                "esj get writes a value raw, for $(...), and says so in docs/cli.md");
    }

    @Test
    void listWritesEveryClassAsAnEscapeOnOneLine() {
        Cli.Run run = Cli.run(hostileInvoice(), "list", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTerminalSafe(run);
        assertTrue(run.text().contains("/BT-1\tIdentifier\t" + ESCAPED + "\n"), run.text());
    }

    @Test
    void listInJsonWritesEveryClassAsAJsonEscapeAndReadsBackAsTheDocument() {
        Cli.Run run = Cli.run(hostileInvoice(), "list", "-", "--format", "json");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTerminalSafe(run);
        assertTrue(run.text().contains("\"X\\u001B[2K\\u007F\\u009B\\u0085\\u2028\\u2029"
                + "\\u061C\\u200E\\u202E\\u2066Y\""), run.text());
        assertTrue(strings(run.text()).contains(HOSTILE),
                "the JSON report parses back to the characters the document carries");
    }

    @Test
    void inspectWritesNoCharacterThatSteersATerminal() {
        Cli.Run run = Cli.run(hostileInvoice(), "inspect", "-");

        assertTerminalSafe(run);
        assertTrue(run.text().contains(ESCAPED), run.text());
    }

    @Test
    void diffWritesNoCharacterThatSteersATerminal() {
        String hostile = Fixtures.write(directory, "hostile.esj.json", hostileInvoice());
        String plain = Fixtures.file(directory, "examples/standard-invoice.esj.json");

        Cli.Run run = Cli.run("diff", plain, hostile);

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTerminalSafe(run);
        assertTrue(run.text().contains(ESCAPED), run.text());
    }

    @Test
    void validateEscapesAValueItsFindingsQuote() {
        byte[] document = new String(hostileInvoice(), StandardCharsets.UTF_8)
                .replace("\"2026-02-03\"", "\"2026-02" + HOSTILE_JSON + "\"")
                .getBytes(StandardCharsets.UTF_8);

        Cli.Run text = Cli.run(document, "validate", "-");
        Cli.Run json = Cli.run(document, "validate", "-", "--output", "json");

        assertEquals(ExitCode.VALIDATION, text.exitCode(), text.err());
        assertTerminalSafe(text);
        assertTrue(text.text().contains("ESJ-L2-DATE"), text.text());
        assertEquals(ExitCode.VALIDATION, json.exitCode(), json.err());
        assertTerminalSafe(json);
        assertTrue(strings(json.text()).stream()
                        .anyMatch(message -> message.contains("2026-02X\\u001b[2K")),
                "the message quotes the value escaped, by the specification, section 9.5");
    }

    @Test
    void extractListsAnAttachmentNameWithoutLettingItSteerTheTerminal() {
        byte[] pdf = TestPdfs.builder()
                .attach("factur-x" + HOSTILE + ".xml",
                        Fixtures.bytes("conformance/kosit/business-cases/standard/"
                                + "01.01a-INVOICE_uncefact.xml"))
                .build();

        Cli.Run run = Cli.run(pdf, "extract", "-", "--list");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTerminalSafe(run);
        assertTrue(run.text().contains("\"factur-x" + ESCAPED + ".xml\""), run.text());
    }

    /**
     * A command that writes a document writes its content as the document carries it — that
     * is data and not a line for a terminal — and its notes on the error stream, which are
     * held to the property like every other line.
     */
    @Test
    void theNotesOfTheCommandsThatWriteADocumentStayOnTheirLines() {
        byte[] bytes = new String(hostileInvoice(), StandardCharsets.UTF_8)
                .replace("\"Payable within 30 days without deduction.\"",
                        "\"" + HOSTILE_JSON + "\"")
                .getBytes(StandardCharsets.UTF_8);
        for (List<String> command : List.of(List.of("convert", "--to", "cii", "-"),
                List.of("convert", "--to", "ubl", "-"),
                List.of("upgrade", "--to", "2017", "-"),
                List.of("canonicalize", "--digest", "-"))) {
            Cli.Run run = Cli.run(bytes, command.toArray(String[]::new));

            assertTrue(run.exitCode() == ExitCode.SUCCESS || run.exitCode() == ExitCode.INPUT,
                    command + ": " + run.err());
            assertTerminalSafe(new Cli.Run(run.exitCode(), new byte[0], run.err()));
        }
    }

    @Test
    void theLastGuardEscapesWhatReachesALineUnescapedAndKeepsTheLayout() {
        String line = "a" + (char) 0x1b + "b\tc\nd\re" + (char) 0x9b + "f" + (char) 0x2028
                + "g" + (char) 0x202e + "h\\i";

        assertEquals("a\\u001bb\tc\nd\\re\\u009bf\\u2028g\\u202eh\\i",
                Console.terminalSafe(line));
        assertEquals("plain text — with an em dash and ü", Console.terminalSafe(
                "plain text — with an em dash and ü"));
    }

    @Test
    void aDiagnosticOfTheToolIsGuardedAsWell() {
        Cli.Run run = Cli.run("get", "examples/does-not-exist" + HOSTILE + ".esj.json", "/BT-1");

        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTerminalSafe(run);
        assertArrayEquals(new byte[0], run.out());
    }

    /**
     * Asserts that neither stream carries a character that steers a terminal, other than the
     * line feed and the tab the tool lays its output out with.
     */
    private static void assertTerminalSafe(Cli.Run run) {
        for (String stream : List.of(run.text(), run.err())) {
            for (int i = 0; i < stream.length(); i++) {
                char c = stream.charAt(i);
                if (c != '\n' && c != '\t' && Esj.steersATerminal(c)) {
                    throw new AssertionError(String.format(Locale.ROOT,
                            "U+%04X at offset %d of %s", (int) c, i, stream));
                }
            }
        }
    }

    /** Returns every string value of a JSON text, in document order. */
    private static List<String> strings(String json) {
        List<String> values = new ArrayList<>();
        try (JsonParser parser = new JsonFactory().createParser(json)) {
            for (JsonToken token = parser.nextToken(); token != null; token = parser.nextToken()) {
                if (token == JsonToken.VALUE_STRING) {
                    values.add(parser.getText());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return values;
    }
}
