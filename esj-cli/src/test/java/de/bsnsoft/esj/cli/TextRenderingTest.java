package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.bsnsoft.esj.model.Registry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The invoice as text: {@code esj render --text} and {@code esj view --text}.
 *
 * <p>The goldens are the text of every example of the repository in both languages, at the
 * default width. Run this test with {@code -Desj.view.writeGoldens=true} to write them again
 * after an intended change, and read the difference before committing it. What of a document
 * reaches the text, and at which widths, is {@code InvoiceTextTest} of {@code esj-render}.
 */
class TextRenderingTest {

    private static final String HYBRID = "conformance/pdf/factur-x.pdf";

    private static final String EXAMPLE = "examples/standard-invoice.esj.json";

    @TempDir
    private Path in;

    @TempDir
    private Path out;

    static List<String> goldens() {
        List<String> names = new ArrayList<>();
        for (String example : List.of("minimal", "smallest-valid", "standard-invoice",
                "multiple-lines", "allowances", "charges", "self-billed", "credit-note",
                "extended", "extension-depth", "b2c-gross", "edition-2026")) {
            for (String language : List.of("de", "en")) {
                names.add(example + "-" + language);
            }
        }
        return names;
    }

    @ParameterizedTest(name = "text-{0}.txt")
    @MethodSource("goldens")
    void theTextIsTheOneCheckedIn(String golden) throws IOException {
        String example = golden.substring(0, golden.length() - 3);
        String language = golden.substring(golden.length() - 2);
        assumeTrue(!example.equals("edition-2026") || carries2026(),
                "a build without the edition 2026 has no such example");
        List<String> args = new ArrayList<>(List.of("render", "--text", "--lang", language,
                "--out", "-"));
        if (example.equals("b2c-gross")) {
            args.add("--extension");
            args.add("b2c");
        }
        args.add(copy("examples/" + example + ".esj.json"));
        Cli.Run run = Cli.run(args.toArray(String[]::new));

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String name = "golden/text-" + golden + ".txt";
        if (Boolean.getBoolean("esj.view.writeGoldens")) {
            Path target = Path.of("src/test/resources", name);
            Files.createDirectories(target.getParent());
            Files.write(target, run.out());
        }
        assertEquals(Fixtures.text(name), run.text(), name + " is unchanged; if the change was"
                + " intended, write it again (see the class comment)");
    }

    static boolean carries2026() {
        return Registry.editionKeys().contains("2026");
    }

    // ------------------------------------------------------------------- render --text

    @Test
    void theTextGoesToTheFileItWasGiven() throws IOException {
        Path target = out.resolve("invoice.txt");
        Cli.Run run = Cli.run("render", "--text", "--out", target.toString(), copy(EXAMPLE));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals("", run.text());
        assertArrayEquals(Cli.run("render", "--text", "--out", "-", copy(EXAMPLE)).out(),
                Files.readAllBytes(target));
    }

    /** The width is --width, else COLUMNS where it is 60 or more, else 100. */
    @Test
    void theWidthIsTheOptionOrTheTerminalOrOneHundred() {
        String input = copy("examples/extended.esj.json");
        Cli.Run standard = Cli.runWith(Map.of(), "render", "--text", "--out", "-", input);
        int widest = widest(standard);
        assertTrue(widest > 72 && widest <= 100, "the default is 100: " + widest);
        Cli.Run terminal = Cli.runWith(Map.of("COLUMNS", "72"), "render", "--text", "--out",
                "-", input);
        assertTrue(widest(terminal) <= 72, terminal.text());
        assertArrayEquals(standard.out(), Cli.runWith(Map.of("COLUMNS", "50"), "render",
                "--text", "--out", "-", input).out(),
                "a terminal narrower than 60 is given the default");
        assertArrayEquals(Cli.runWith(Map.of(), "render", "--text", "--width", "60", "--out",
                "-", input).out(), Cli.runWith(Map.of("COLUMNS", "200"), "render", "--text",
                "--width", "60", "--out", "-", input).out(), "--width wins over COLUMNS");
    }

    @Test
    void aWidthOutOfBoundsIsRefused() {
        Cli.Run run = Cli.run("render", "--text", "--width", "39", "--out", "-", copy(EXAMPLE));
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("--width takes a number of characters from 40 to 1000"),
                run.err());
    }

    @Test
    void theTextIsNoPageAndNoContainer() {
        Cli.Run html = Cli.run("render", "--text", "--html", "--out", "-", copy(EXAMPLE));
        assertEquals(ExitCode.INPUT, html.exitCode(), html.err());
        Cli.Run embed = Cli.run("render", "--text", "--embed", "cii", "--out", "-",
                copy(EXAMPLE));
        assertEquals(ExitCode.INPUT, embed.exitCode(), embed.err());
        assertTrue(embed.err().contains("a text is not one"), embed.err());
    }

    @Test
    void anOptionOfThePdfIsToldItTookNoEffect() {
        String input = copy(EXAMPLE);
        Cli.Run plain = Cli.run("render", "--text", "--out", "-", input);
        Cli.Run paper = Cli.run("render", "--text", "--page", "LETTER", "--out", "-", input);
        assertEquals(ExitCode.SUCCESS, paper.exitCode(), paper.err());
        assertArrayEquals(plain.out(), paper.out());
        assertTrue(paper.err().contains("warning: --page is a property of the PDF; the text has"
                + " none"), paper.err());
        Cli.Run code = Cli.run("render", "--text", "--no-payment-code", "--out", "-", input);
        assertArrayEquals(plain.out(), code.out());
        assertEquals("", code.err(), "the text never draws a code, and says nothing about one");
    }

    @Test
    void theTextOfAHybridIsTheTextOfTheInvoiceItCarries() {
        Cli.Run hybrid = Cli.run("render", "--text", "--out", "-", copy(HYBRID));
        Cli.Run xml = Cli.run("render", "--text", "--out", "-",
                copy("conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml"));
        assertEquals(ExitCode.SUCCESS, hybrid.exitCode(), hybrid.err());
        assertTrue(hybrid.text().contains("Rechnung 123456XX\n"), hybrid.text());
        assertEquals(ExitCode.SUCCESS, xml.exitCode(), xml.err());
    }

    // ------------------------------------------------------------------- view --text

    @Test
    void viewPrintsTheTextAndWritesNoPage() throws IOException {
        String input = copy(EXAMPLE);
        Path directory = out.resolve("page");
        Cli.Run view = Cli.run("view", "--text", "--lang", "en", "--out", directory.toString(),
                input);
        assertEquals(ExitCode.SUCCESS, view.exitCode(), view.err());
        assertArrayEquals(Cli.run("render", "--text", "--lang", "en", "--out", "-", input).out(),
                view.out());
        assertFalse(Files.exists(directory), "no directory, no page");
        assertTrue(view.err().contains("warning: --out is the directory of the page"),
                view.err());
    }

    /** With --report the lines of esj validate stand under the text, below a rule. */
    @Test
    void viewWithReportPrintsTheLinesOfValidateUnderARule() {
        String input = copy(HYBRID);
        Cli.Run view = Cli.run("view", "--text", "--report", "--lang", "de", "--width", "80",
                input);
        Cli.Run validate = Cli.run("validate", input);
        Cli.Run text = Cli.run("render", "--text", "--lang", "de", "--width", "80", "--out", "-",
                input);
        assertEquals(ExitCode.SUCCESS, view.exitCode(), view.err());
        assertEquals(text.text() + "\n" + "-".repeat(80) + "\n\n" + validate.text(), view.text());
    }

    @Test
    void viewRefusesTextAndJsonTogether() {
        Cli.Run run = Cli.run("view", "--text", "--output", "json", copy(EXAMPLE));
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertEquals("", run.text());
    }

    /** A file that leads nowhere prints the line the page would open with. */
    @Test
    void aDeadEndPrintsTheLineOfThePage() {
        Cli.Run run = Cli.run("view", "--text", "--lang", "en",
                Fixtures.write(in, "scan.pdf", TestPdfs.builder().build()));
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertEquals("This PDF contains no e-invoice. Ask the sender for the e-invoice.\n",
                run.text());
        assertTrue(run.err().startsWith("error: "), run.err());

        Cli.Run broken = Cli.run("view", "--text", "--lang", "de", Fixtures.write(in,
                "broken.xml", "<Invoice".getBytes(StandardCharsets.UTF_8)));
        assertEquals(ExitCode.INPUT, broken.exitCode(), broken.err());
        assertEquals("Diese Datei ist keine lesbare E-Rechnung. Bitten Sie den Absender, die"
                + " E-Rechnung erneut zu senden.\n", broken.text());
    }

    // ------------------------------------------------------------------- helpers

    private String copy(String resource) {
        return Fixtures.file(in, resource);
    }

    private static int widest(Cli.Run run) {
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        int widest = 0;
        for (String line : run.lines()) {
            widest = Math.max(widest, line.codePointCount(0, line.length()));
        }
        return widest;
    }
}
