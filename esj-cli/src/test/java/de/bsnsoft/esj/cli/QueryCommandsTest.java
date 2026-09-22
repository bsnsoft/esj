package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjReader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code esj get}, {@code esj list} and {@code esj canonicalize}: the three commands that
 * take a document apart.
 *
 * <p>They are the ones a shell script uses, so what is asserted here is mostly what the
 * output looks like on a line: one value and nothing else, one tab-separated row per path,
 * and canonical bytes that end where the canonical form ends.
 */
class QueryCommandsTest {

    private static final String STANDARD = "examples/standard-invoice.esj.json";
    private static final String MINIMAL = "examples/minimal.esj.json";

    @TempDir
    private Path directory;

    @Test
    void getPrintsOneValueAndOneNewline() {
        Cli.Run run = Cli.run("get", Fixtures.file(directory, STANDARD), "/BG-22/BT-112");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals("2915.5\n", run.text());
        assertEquals("", run.err());
    }

    @Test
    void getPrintsTheWholeValueObjectInCanonicalFormOnRequest() {
        Cli.Run run = Cli.run("get", Fixtures.file(directory, STANDARD), "/BT-1", "--json");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals("\"RE-2026-0042\"\n", run.text());

        Cli.Run components = Cli.run("get", Fixtures.file(directory, STANDARD),
                "/BG-4/BT-29/0", "--json");
        assertEquals(ExitCode.SUCCESS, components.exitCode(), components.err());
        assertEquals("{\"value\":\"4399901000018\",\"scheme\":\"0088\"}\n", components.text());
    }

    @Test
    void getAnswersNoForAPathTheDocumentDoesNotCarry() {
        Cli.Run run = Cli.run("get", Fixtures.file(directory, MINIMAL), "/BT-10");
        assertEquals(ExitCode.VALIDATION, run.exitCode());
        assertEquals("", run.text(), "nothing is written to the standard output");
        assertTrue(run.err().contains("carries no value at /BT-10"), run.err());
    }

    @Test
    void getRefusesSomethingThatIsNotASemanticPath() {
        Cli.Run run = Cli.run("get", Fixtures.file(directory, MINIMAL), "BT-1");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("not a semantic path"), run.err());
    }

    @Test
    void listWritesOneRowPerValueInCanonicalOrder() {
        Cli.Run run = Cli.run("list", Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());

        SemanticDocument document = EsjReader.strict().read(Fixtures.bytes(MINIMAL));
        String[] lines = run.lines();
        assertEquals(document.values().size() + 1, lines.length,
                "one row per value, and the empty piece after the last newline");
        assertEquals("/BT-1\tIdentifier\tRE-2026-0001", lines[0]);
        assertEquals("", lines[lines.length - 1]);
    }

    @Test
    void listWritesTheSameThreeFieldsInJson() {
        Cli.Run run = Cli.run("list", Fixtures.file(directory, MINIMAL), "--format", "json");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().startsWith("[\n  {\n    \"path\": \"/BT-1\",\n"
                + "    \"datatype\": \"Identifier\",\n"
                + "    \"value\": \"RE-2026-0001\"\n  }"), run.text());
        assertTrue(run.text().endsWith("]\n"), run.text());
    }

    @Test
    void listDoesNotLetAValueRewriteTheLineItStandsOn() {
        byte[] fixture = ("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{"
                + "\"/BT-1\":\"RE-1\\u001b[2KFORGED\"}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Cli.Run run = Cli.run(fixture, "list", "-");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals("/BT-1\tIdentifier\tRE-1\\u001b[2KFORGED\n", run.text());
        assertEquals(1, run.lines().length - 1, "one value stays on one line");
    }

    @Test
    void listRefusesAFormatItDoesNotKnow() {
        Cli.Run run = Cli.run("list", Fixtures.file(directory, MINIMAL), "--format", "csv");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--format takes tsv or json"), run.err());
    }

    @Test
    void canonicalizeWritesTheGoldenBytesOfEveryExample() {
        for (String name : new String[] {"minimal", "standard-invoice", "multiple-lines",
                                         "allowances", "charges", "self-billed", "credit-note",
                                         "extended", "extension-depth"}) {
            Cli.Run run = Cli.run(Fixtures.bytes("examples/" + name + ".esj.json"),
                    "canonicalize", "-");
            assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
            assertArrayEquals(Fixtures.bytes("examples/" + name + ".canonical.esj.json"),
                    run.out(), name);
        }
    }

    @Test
    void canonicalizePrintsTheTwoDigestsOnRequest() {
        Cli.Run run = Cli.run("canonicalize", Fixtures.file(directory, MINIMAL), "--digest");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String[] lines = run.lines();
        assertTrue(lines[0].matches("semantic: [0-9a-f]{64}"), lines[0]);
        assertTrue(lines[1].matches("document: [0-9a-f]{64}"), lines[1]);
    }

    @Test
    void canonicalizeReadsXmlAsWell() {
        Cli.Run run = Cli.run("canonicalize", Fixtures.file(directory,
                "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml"),
                "--digest");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().startsWith("semantic: "), run.text());
    }
}
