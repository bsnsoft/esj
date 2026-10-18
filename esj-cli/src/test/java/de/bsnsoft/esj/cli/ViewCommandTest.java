package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import de.bsnsoft.esj.model.Registry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code esj view}: the page, the files beside it, the states it shows and the codes it
 * leaves with, and the desktop it opens the page on.
 *
 * <p>Every run here is made on a machine of the test's own ({@link Desktop.Machine}): its
 * temporary directory is a directory of the test and its launcher records the programs the
 * command would start, so no test writes outside its own directories and none starts a
 * browser.
 *
 * <p>The goldens are {@code index.html} of six states in both languages, with the two
 * things in it that are not a property of the input replaced: the directory the page lies
 * in, written {@code ${directory}}, and the version of the build, written {@code ${version}}
 * — and the directory a test put its input into, written {@code ${input}}. Run this test
 * with {@code -Desj.view.writeGoldens=true} to write them again after an intended change,
 * and read the difference before committing it.
 */
class ViewCommandTest {

    private static final String UBL =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml";

    private static final String CII =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml";

    private static final String HYBRID = "conformance/pdf/factur-x.pdf";

    private static final String EXAMPLE = "examples/standard-invoice.esj.json";

    /** The policy every page states in its head. */
    private static final String POLICY = "<meta http-equiv=\"Content-Security-Policy\""
            + " content=\"script-src 'none'; base-uri 'none'; form-action 'none'\">";

    /** What the tool of this build calls itself. */
    private static final String TOOL = "esj " + version();

    @TempDir
    private Path in;

    @TempDir
    private Path out;

    @TempDir
    private Path temporary;

    private final Recording launcher = new Recording();

    // ------------------------------------------------------------------- goldens

    static List<String> goldens() {
        List<String> names = new ArrayList<>();
        for (String state : List.of("read", "hybrid", "no-invoice", "several", "encrypted",
                                    "limit")) {
            for (String language : List.of("de", "en")) {
                names.add(state + "-" + language);
            }
        }
        return names;
    }

    @ParameterizedTest(name = "view-{0}.html")
    @MethodSource("goldens")
    void thePageIsTheOneCheckedIn(String golden) throws IOException {
        String language = golden.substring(golden.length() - 2);
        String state = golden.substring(0, golden.length() - 3);
        Path directory = out.resolve(golden);
        List<String> args = new ArrayList<>(List.of("view", "--out", directory.toString(),
                "--lang", language));
        int expected;
        switch (state) {
            case "read" -> {
                args.add(copy(EXAMPLE));
                expected = ExitCode.SUCCESS;
            }
            case "hybrid" -> {
                args.add("--report");
                args.add(copy(HYBRID));
                expected = ExitCode.SUCCESS;
            }
            case "no-invoice" -> {
                args.add(write("scan.pdf", TestPdfs.builder().build()));
                expected = ExitCode.INPUT;
            }
            case "several" -> {
                args.add(write("two.pdf", several(2)));
                expected = ExitCode.INPUT;
            }
            case "encrypted" -> {
                args.add(write("locked.pdf", TestPdfs.builder().attach(TestPdfs.FACTUR_X,
                        Fixtures.bytes(CII)).userPassword("secret").build()));
                expected = ExitCode.INPUT;
            }
            case "limit" -> {
                args.add("--max-input-bytes");
                args.add("1000");
                args.add(copy(UBL));
                expected = ExitCode.LIMIT;
            }
            default -> throw new IllegalArgumentException(state);
        }
        Cli.Run run = view(args.toArray(String[]::new));

        assertEquals(expected, run.exitCode(), run.err());
        String page = normalized(Files.readString(directory.resolve("index.html")), directory);
        String name = "golden/view-" + golden + ".html";
        if (Boolean.getBoolean("esj.view.writeGoldens")) {
            Path target = Path.of("src/test/resources", name);
            Files.createDirectories(target.getParent());
            Files.writeString(target, page);
        }
        assertEquals(Fixtures.text(name), page, name + " is unchanged; if the change was"
                + " intended, write it again (see the class comment)");
    }

    /** Two runs of one input into one directory write the same page. */
    @Test
    void theSameInputGivesTheSameBytes() throws IOException {
        String input = copy(HYBRID);
        Path directory = out.resolve("twice");
        view("view", "--out", directory.toString(), "--lang", "de", "--report", input);
        byte[] first = Files.readAllBytes(directory.resolve("index.html"));
        view("view", "--out", directory.toString(), "--lang", "de", "--report", input);
        assertArrayEquals(first, Files.readAllBytes(directory.resolve("index.html")));
    }

    // ------------------------------------------------------------------- the files

    /**
     * Every file beside the page is the bytes of the command it stands for, for the same
     * input and the same language: the received copy is the input, the letter is
     * {@code esj render}, the HTML page {@code esj render --html}, the reports
     * {@code esj validate --report}.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {HYBRID, UBL, CII, EXAMPLE})
    void everyFileBesideThePageIsTheOneItsCommandWrites(String fixture) throws IOException {
        for (String language : List.of("de", "en")) {
            String input = copy(fixture);
            Path directory = out.resolve(language + "-" + Path.of(fixture).getFileName());
            Cli.Run run = view("view", "--out", directory.toString(), "--lang", language,
                    "--report", input);
            assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
            String stem = ViewCommand.stem(Path.of(fixture).getFileName().toString());

            Path letter = out.resolve(stem + "-" + language + ".letter.pdf");
            assertEquals(ExitCode.SUCCESS, Cli.run("render", input, "--lang", language,
                    "--out", letter.toString()).exitCode());
            assertSameBytes(letter, directory.resolve(stem + ".letter.pdf"));

            Path html = out.resolve(stem + "-" + language + ".kosit.html");
            assertEquals(ExitCode.SUCCESS, Cli.run("render", input, "--html", "--lang", language,
                    "--out", html.toString()).exitCode());
            assertSameBytes(html, directory.resolve(stem + ".kosit.html"));

            for (String form : List.of("html", "pdf")) {
                Path report = out.resolve(stem + "-" + language + ".report." + form);
                assertEquals(ExitCode.SUCCESS, Cli.run("validate", input, "--report-lang",
                        language, "--report", report.toString()).exitCode());
                assertSameBytes(report, directory.resolve(stem + ".report." + form));
            }
            if (fixture.endsWith(".pdf")) {
                assertSameBytes(Path.of(input), directory.resolve("received")
                        .resolve(Path.of(input).getFileName()));
            } else {
                assertFalse(Files.exists(directory.resolve("received")),
                        "a file that is no PDF is not copied: the page has no view of it");
            }
        }
    }

    @Test
    void theLetterOnATemplateIsTheOneEsjRenderDrawsOnIt() throws IOException {
        Path templates = Fixtures.directory(in, "examples/templates/");
        String template = templates.resolve("letter.json").toString();
        String input = copy(EXAMPLE);
        Path directory = out.resolve("branded");
        assertEquals(ExitCode.SUCCESS, view("view", "--out", directory.toString(), "--lang",
                "de", "--template", template, input).exitCode());
        Path letter = out.resolve("branded.pdf");
        assertEquals(ExitCode.SUCCESS, Cli.run("render", input, "--lang", "de", "--template",
                template, "--out", letter.toString()).exitCode());
        assertSameBytes(letter, directory.resolve("standard-invoice.esj.letter.pdf"));
    }

    /**
     * {@code --no-payment-code} leaves the code out of the letter the page shows, as
     * {@code esj render --no-payment-code} does: the letter is the bytes of that command, and
     * not the bytes of the letter with the code.
     */
    @Test
    void theLetterWithoutAPaymentCodeIsTheOneEsjRenderDrawsWithoutIt() throws IOException {
        String input = copy(EXAMPLE);
        Path with = out.resolve("with-code");
        Path without = out.resolve("without-code");
        assertEquals(ExitCode.SUCCESS, view("view", "--out", with.toString(), "--lang", "de",
                input).exitCode());
        assertEquals(ExitCode.SUCCESS, view("view", "--out", without.toString(), "--lang", "de",
                "--no-payment-code", input).exitCode());
        Path rendered = out.resolve("no-code.pdf");
        assertEquals(ExitCode.SUCCESS, Cli.run("render", input, "--lang", "de",
                "--no-payment-code", "--out", rendered.toString()).exitCode());
        assertSameBytes(rendered, without.resolve("standard-invoice.esj.letter.pdf"));
        assertFalse(java.util.Arrays.equals(Files.readAllBytes(rendered),
                        Files.readAllBytes(with.resolve("standard-invoice.esj.letter.pdf"))),
                "the letter of the example draws a code, and the option takes it out");
    }

    @Test
    void withoutReportNoReportIsWrittenAndTheInvoiceIsNotChecked() throws IOException {
        Path directory = out.resolve("plain");
        Cli.Run run = view("view", "--out", directory.toString(), "--lang", "en",
                copy(UBL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of("01.01a-INVOICE_ubl.kosit.html",
                            "01.01a-INVOICE_ubl.letter.pdf", "index.html"),
                    files.map(path -> path.getFileName().toString()).sorted().toList());
        }
        String page = Files.readString(directory.resolve("index.html"));
        assertTrue(page.contains("<b>NOT CHECKED</b>"), page);
        assertFalse(page.contains("id=\"tab-report\""), page);
        assertTrue(page.contains("<input type=\"radio\" name=\"tab\" id=\"tab-invoice\" checked>"),
                "without a PDF the invoice is the first tab: " + page);
        assertTrue(page.contains("<iframe sandbox=\"allow-scripts\""
                + " src=\"01.01a-INVOICE_ubl.kosit.html\""), page);
    }

    // ------------------------------------------------------------------- the page

    /** No page of any state carries a script, and every one states the policy. */
    @Test
    void noPageRunsAScriptAndEveryPageSaysSo() throws IOException {
        List<String[]> runs = new ArrayList<>(List.of(
                new String[] {copy(EXAMPLE)},
                new String[] {"--report", copy(HYBRID)},
                new String[] {write("scan.pdf", TestPdfs.builder().build())},
                new String[] {write("five.pdf", several(5))},
                new String[] {write("old.pdf", TestPdfs.facturX(TestPdfs.ZUGFERD_1,
                        TestPdfs.ZUGFERD_1_INVOICE, "BASIC"))},
                new String[] {write("broken.xml", "<Invoice".getBytes(StandardCharsets.UTF_8))},
                new String[] {"--report", "--max-values", "3", copy(EXAMPLE)}));
        if (carries2026()) {
            runs.add(new String[] {copy("examples/edition-2026.esj.json")});
        }
        int number = 0;
        for (String[] arguments : runs) {
            Path directory = out.resolve("page-" + number++);
            List<String> args = new ArrayList<>(List.of("view", "--out", directory.toString()));
            args.addAll(List.of(arguments));
            view(args.toArray(String[]::new));
            String page = Files.readString(directory.resolve("index.html"));
            assertTrue(page.contains(POLICY), "the policy is in the head: " + page);
            assertFalse(page.contains("<script"), "no script on the page: " + page);
            assertFalse(page.contains("srcdoc"), "no frame of the page's own: " + page);
            assertFalse(page.contains("<object") || page.contains("<embed"), page);
            assertFalse(page.contains("http://") || page.contains("https://"),
                    "nothing outside the directory is named: " + page);
            assertFalse(page.contains(".kosit.html\"") && !page.contains(
                    "<iframe sandbox=\"allow-scripts\" src=\""), "the KoSIT view is framed"
                    + " in a sandbox of its own: " + page);
        }
    }

    @Test
    void aZugferd1InvoiceIsADeadEndWithItsCode() throws IOException {
        Path directory = out.resolve("zugferd");
        Cli.Run run = view("view", "--out", directory.toString(), "--lang", "en",
                write("old.pdf", TestPdfs.facturX(TestPdfs.ZUGFERD_1,
                        TestPdfs.ZUGFERD_1_INVOICE, "BASIC")));
        assertEquals(ExitCode.UNSUPPORTED, run.exitCode(), run.err());
        String page = Files.readString(directory.resolve("index.html"));
        assertTrue(page.contains("<p class=\"state-head\">The e-invoice is in ZUGFeRD 1.0,"
                + " which does not conform to EN 16931.</p>"), page);
        assertTrue(page.contains("<p>Ask the sender for an EN 16931 e-invoice.</p>"), page);
        assertTrue(Files.isRegularFile(directory.resolve("received/old.pdf")),
                "the received file is shown");
        assertTrue(run.err().startsWith("error: "), run.err());
    }

    @Test
    void aFileThatIsNoInvoiceIsADeadEndThatSaysWhatToAskFor() throws IOException {
        Path directory = out.resolve("broken");
        Cli.Run run = view("view", "--out", directory.toString(), "--lang", "de",
                write("broken.xml", "<Invoice".getBytes(StandardCharsets.UTF_8)));
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        String page = Files.readString(directory.resolve("index.html"));
        assertTrue(page.contains("<p class=\"state-head\">Diese Datei ist keine lesbare"
                + " E-Rechnung.</p>\n<p>Bitten Sie den Absender, die E-Rechnung erneut zu"
                + " senden.</p>"), page);
        assertFalse(page.contains("<pre"), "the message of the run is for the terminal: " + page);
        assertTrue(page.contains("<title>broken.xml · Diese Datei ist keine lesbare E-Rechnung"
                + " · esj</title>"), page);
        assertFalse(Files.exists(directory.resolve("received")), "no PDF, nothing received");
        assertFalse(page.contains("<main"), "a page without a view has no tabs");
    }

    @Test
    void anEmptyFileIsADeadEndAndNotAMistakeOfTheCommandLine() throws IOException {
        Path directory = out.resolve("empty");
        Cli.Run run = view("view", "--out", directory.toString(), write("empty.xml",
                new byte[0]));
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(Files.isRegularFile(directory.resolve("index.html")));
    }

    @Test
    void everyCandidateIsARowWithTheCommandThatOpensIt() throws IOException {
        Path directory = out.resolve("five");
        Cli.Run run = view("view", "--out", directory.toString(), "--lang", "en",
                write("five.pdf", several(5)));
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        String page = Files.readString(directory.resolve("index.html"));
        assertTrue(page.contains("<p class=\"state-head\">This PDF contains more than one"
                + " possible e-invoice.</p>\n<table class=\"candidates\">"), page);
        assertTrue(page.contains("</table>\n<p>Ask the sender which one applies.</p>"), page);
        assertFalse(page.contains("<details"), "the table is the guidance and stands open");
        for (int position = 1; position <= 5; position++) {
            assertTrue(page.contains("esj view --report --attachment-index " + position + " "),
                    "row " + position + " carries its command: " + page);
        }
    }

    @Test
    void aCandidateNamedWithAttachmentIndexIsRead() throws IOException {
        Path directory = out.resolve("picked");
        Cli.Run run = view("view", "--out", directory.toString(), "--lang", "en",
                "--attachment-index", "2", write("two.pdf", several(2)));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = Files.readString(directory.resolve("index.html"));
        assertTrue(page.contains("<label for=\"tab-invoice\">Invoice</label>"), page);
        assertTrue(page.contains("<p class=\"parties\">"), page);
    }

    @Test
    void anEncryptedFileIsNotCopied() throws IOException {
        Path directory = out.resolve("locked");
        view("view", "--out", directory.toString(), write("locked.pdf", TestPdfs.builder()
                .attach(TestPdfs.FACTUR_X, Fixtures.bytes(CII)).userPassword("x").build()));
        assertFalse(Files.exists(directory.resolve("received")),
                "a file this tool refused to open is not framed");
        assertFalse(Files.readString(directory.resolve("index.html")).contains("<iframe"));
    }

    @Test
    void aLimitOfAnEsjInputKeepsTheReportAsItWasWritten() throws IOException {
        Path directory = out.resolve("values");
        Cli.Run run = view("view", "--out", directory.toString(), "--lang", "en", "--report",
                "--max-values", "3", copy(EXAMPLE));
        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
        String page = Files.readString(directory.resolve("index.html"));
        assertTrue(page.contains("<p class=\"state-head\">The file is larger than this check"
                + " allows.</p>\n<p><code>--max-values</code></p>"), page);
        assertTrue(page.contains("id=\"tab-report\" checked>"),
                "the report is the one tab: " + page);
        assertTrue(page.contains("<p class=\"finding-head\">ESJ-L1-LIMIT [error]"), page);
        assertTrue(Files.isRegularFile(directory.resolve("standard-invoice.esj.report.pdf")));
    }

    @Test
    void aLetterOverItsPagesIsALimitWithoutReportAndTheVerdictWithIt() throws IOException {
        String input = copy("examples/multiple-lines.esj.json");
        Path plain = out.resolve("pages");
        Cli.Run run = view("view", "--out", plain.toString(), "--lang", "en", "--max-pages",
                "1", input);
        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
        String page = Files.readString(plain.resolve("index.html"));
        assertTrue(page.contains("<p>The invoice runs to more pages than this view allows.</p>"
                + "\n<p><code>--max-pages</code></p>"), page);

        Path checked = out.resolve("pages-report");
        Cli.Run withReport = view("view", "--out", checked.toString(), "--lang", "en",
                "--max-pages", "1", "--report", input);
        assertEquals(Cli.run("validate", input).exitCode(), withReport.exitCode(),
                withReport.err());
    }

    /** A build without the edition 2026 (profile {@code without-edition-2026}) has no such example. */
    static boolean carries2026() {
        return Registry.editionKeys().contains("2026");
    }

    @Test
    @EnabledIf("carries2026")
    void anEditionTheKositViewDoesNotRenderHasNoTabForIt() throws IOException {
        Path directory = out.resolve("2026");
        Cli.Run run = view("view", "--out", directory.toString(), "--lang", "en",
                copy("examples/edition-2026.esj.json"));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = Files.readString(directory.resolve("index.html"));
        assertFalse(page.contains("id=\"tab-kosit\""), "no tab for a file not written: " + page);
        assertTrue(page.contains("<iframe src=\"edition-2026.esj.letter.pdf\""),
                "the letter renders it: " + page);
    }

    @Test
    void aLetterOfAnEditionThisBuildDoesNotCarryIsRefusedOnThePage() throws IOException {
        String document = Fixtures.text(EXAMPLE).replace("EN16931-1:2017+A1:2019/AC:2020",
                "EN16931-1:2099");
        Path directory = out.resolve("2099");
        Cli.Run run = view("view", "--out", directory.toString(), "--lang", "en",
                write("future.esj.json", document.getBytes(StandardCharsets.UTF_8)));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = Files.readString(directory.resolve("index.html"));
        assertTrue(page.contains("<p>This invoice cannot be shown here.</p>"), page);
        assertTrue(run.err().contains("this build carries no registry of the edition"
                + " EN16931-1:2099"), run.err());
    }

    @Test
    void aStrictRunOverBytesNotInTheirEncodingIsInvalidAndDrawsNoLetter() throws IOException {
        Path directory = out.resolve("latin1");
        String input = write("latin1.xml", latin1());
        Cli.Run run = view("view", "--out", directory.toString(), "--lang", "en", "--report",
                input);
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        String page = Files.readString(directory.resolve("index.html"));
        assertTrue(page.contains("<b>INVALID</b>"), page);
        assertFalse(page.contains("id=\"tab-invoice\""), "no document, no invoice: " + page);

        Path repaired = out.resolve("latin1-repaired");
        Cli.Run read = view("view", "--out", repaired.toString(), "--lang", "en", input);
        assertEquals(ExitCode.SUCCESS, read.exitCode(), read.err());
        assertEquals(1, read.err().lines().filter(line -> line.startsWith("warning:")).count(),
                read.err());
    }

    @Test
    void aBasicWlContainerIsReadAndNotJudgedAsAnInvoice() throws IOException {
        byte[] invoice = TestPdfs.withSpecificationIdentifier(Fixtures.bytes(CII),
                "urn:factur-x.eu:1p0:basicwl");
        String input = write("basicwl.pdf",
                TestPdfs.facturX(TestPdfs.FACTUR_X, invoice, "BASIC WL"));
        Path plain = out.resolve("basicwl");
        assertEquals(ExitCode.SUCCESS, view("view", "--out", plain.toString(), "--lang", "en",
                input).exitCode());
        assertTrue(Files.readString(plain.resolve("index.html"))
                .contains("profile BASIC WL: not an EN 16931 invoice"));
        Path checked = out.resolve("basicwl-report");
        assertEquals(ExitCode.VALIDATION, view("view", "--out", checked.toString(), "--lang",
                "en", "--report", input).exitCode());
    }

    @Test
    void aCreditNoteClosesWithTheAmountCredited() throws IOException {
        Path directory = out.resolve("credit");
        view("view", "--out", directory.toString(), "--lang", "de",
                copy("examples/credit-note.esj.json"));
        String page = Files.readString(directory.resolve("index.html"));
        assertTrue(page.contains("<dt>Gutschriftsbetrag</dt><dd>355,81 EUR</dd>"), page);
        assertTrue(page.contains("<p class=\"kind\">Gutschrift</p>"), page);
    }

    @Test
    void aReferenceToAFileNameIsPercentEncoded() throws IOException {
        // A file system whose names the platform encodes without an umlaut cannot hold the
        // name this test is about; the reference is encoded the same way regardless.
        assumeTrue(java.nio.charset.Charset.forName(System.getProperty("sun.jnu.encoding",
                "UTF-8")).newEncoder().canEncode("ü"), "file names can carry an umlaut here");
        Path directory = out.resolve("names");
        String input = write("Rechnung #4711 100% ü.pdf", Fixtures.bytes(HYBRID));
        Cli.Run run = view("view", "--out", directory.toString(), "--report", input);
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = Files.readString(directory.resolve("index.html"));
        assertTrue(page.contains("<iframe src=\"received/Rechnung%20%234711%20100%25%20%C3%BC.pdf\""),
                page);
        assertTrue(Files.isRegularFile(directory.resolve("received/Rechnung #4711 100% ü.pdf")));
        assertTrue(page.contains("src=\"Rechnung%20_4711%20100_%20%C3%BC.letter.pdf\""), page);
        assertTrue(page.contains("href=\"Rechnung%20_4711%20100_%20%C3%BC.report.pdf\""
                + " download>"), "the one link of the page: " + page);
    }

    // ------------------------------------------------------------------- refusals without a page

    @Test
    void aNameThatIsNoReadableFileOpensNoPage() {
        Cli.Run run = view("view", "--out", out.resolve("none").toString(),
                in.resolve("no-such-file.xml").toString());
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("no such file"), run.err());
        assertFalse(Files.exists(out.resolve("none")), "no directory, no page");

        Cli.Run directory = view("view", "--out", out.resolve("dir").toString(), in.toString());
        assertEquals(ExitCode.INPUT, directory.exitCode());
        assertFalse(Files.exists(out.resolve("dir")));
    }

    @Test
    void theStandardInputIsNoFileToShow() {
        Cli.Run run = view("view", "-");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("name a file"), run.err());
    }

    @Test
    void aTemplateThatIsNotOneOpensNoPage() throws IOException {
        String template = write("template.json", "{".getBytes(StandardCharsets.UTF_8));
        Cli.Run run = view("view", "--out", out.resolve("t").toString(), "--template",
                template, copy(EXAMPLE));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("cannot use the render template"), run.err());
        assertFalse(Files.exists(out.resolve("t")));
    }

    @Test
    void aDirectoryThatCannotBeCreatedIsAnOutputFailure() throws IOException {
        String blocked = write("blocked", new byte[] {1});
        Cli.Run run = view("view", "--out", blocked + "/page", copy(EXAMPLE));
        assertEquals(ExitCode.OUTPUT, run.exitCode(), run.err());
    }

    // ------------------------------------------------------------------- the output

    @Test
    void theStandardOutputIsTheAbsolutePathOfThePage() throws IOException {
        Path directory = out.resolve("path");
        Cli.Run run = view("view", "--out", directory.toString(), copy(EXAMPLE));
        assertEquals(directory.resolve("index.html").toAbsolutePath() + "\n", run.text());
        assertEquals("", run.err());
    }

    @Test
    void theJsonObjectNamesThePageItsFilesAndTheValidation() throws IOException {
        Path directory = out.resolve("json");
        Cli.Run run = view("view", "--out", directory.toString(), "--output", "json",
                "--report", copy(HYBRID));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String json = run.text();
        assertTrue(json.contains("\"page\": \"" + directory.resolve("index.html") + "\""), json);
        assertTrue(json.contains("\"directory\": \"" + directory + "\""), json);
        assertTrue(json.contains("\"opened\": false"), json);
        assertTrue(json.contains("\"state\": \"read\""), json);
        assertTrue(json.contains("\"received\": \""
                + directory.resolve("received/factur-x.pdf") + "\""), json);
        assertTrue(json.contains("\"reportPdf\": \"" + directory.resolve("factur-x.report.pdf")
                + "\""), json);
        assertTrue(json.contains("\"message\": null"), json);
        assertTrue(json.contains("\"validation\": {"), json);
        assertTrue(json.contains("\"verdict\": \"VALID\""), json);

        Path dead = out.resolve("json-dead");
        Cli.Run refused = view("view", "--out", dead.toString(), "--output", "json",
                write("scan.pdf", TestPdfs.builder().build()));
        assertTrue(refused.text().contains("\"state\": \"no-invoice\""), refused.text());
        assertTrue(refused.text().contains("\"letter\": null"), refused.text());
        assertTrue(refused.text().contains("\"validation\": null"), refused.text());
        assertTrue(refused.text().contains("\"message\": \""), refused.text());
    }

    // ------------------------------------------------------------------- the desktop

    @Test
    void withoutOutThePageGoesIntoTheTemporaryDirectoryAndIsOpened() throws IOException {
        Cli.Run run = Cli.runOn(machine("Mac OS X"), Map.of(), "view", copy(EXAMPLE));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        Path page = Path.of(run.text().strip());
        assertTrue(page.startsWith(temporary), page.toString());
        assertTrue(page.getParent().getFileName().toString()
                .startsWith("esj-view-standard-invoice.esj-"), page.toString());
        assertEquals(List.of(List.of("open", page.toString())), launcher.opened);
    }

    @Test
    void aBrowserThatCannotBeStartedLeavesAWarningAndTheCode() throws IOException {
        launcher.opens = false;
        Cli.Run run = Cli.runOn(machine("Windows 11"), Map.of(), "view", copy(EXAMPLE));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = run.text().strip();
        assertEquals(List.of(List.of("rundll32", "url.dll,FileProtocolHandler", page)),
                launcher.opened);
        assertEquals("warning: could not open a browser; the page is at " + page + "\n",
                run.err());
    }

    @Test
    void onLinuxWithoutADisplayThePageIsWrittenAndNotOpened() throws IOException {
        Cli.Run run = Cli.runOn(machine("Linux"), Map.of(), "view", copy(EXAMPLE));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(launcher.opened.isEmpty(), launcher.opened.toString());
        assertEquals("", run.err());
    }

    @Test
    void onLinuxThePageGoesBelowTheDownloadsAndXdgOpenOpensIt() throws IOException {
        Path downloads = Files.createDirectories(in.resolve("Downloads"));
        launcher.downloads = Optional.of(downloads + "\n");
        Cli.Run run = Cli.runOn(machine("Linux"), Map.of("WAYLAND_DISPLAY", "wayland-0"),
                "view", copy(EXAMPLE));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        Path page = Path.of(run.text().strip());
        assertEquals(downloads.resolve("esj-view"), page.getParent().getParent());
        assertEquals(List.of(List.of("xdg-open", page.toString())), launcher.opened);
    }

    @Test
    void onLinuxWithoutADownloadsDirectoryThePageGoesIntoTheTemporaryDirectory() throws IOException {
        launcher.downloads = Optional.empty();
        Cli.Run run = Cli.runOn(machine("Linux"), Map.of("DISPLAY", ":0"), "view",
                copy(EXAMPLE));
        assertTrue(Path.of(run.text().strip()).startsWith(temporary), run.text());
    }

    @Test
    void withOutNothingIsOpened() throws IOException {
        Cli.runOn(machine("Mac OS X"), Map.of(), "view", "--out",
                out.resolve("quiet").toString(), copy(EXAMPLE));
        assertTrue(launcher.opened.isEmpty(), launcher.opened.toString());
    }

    @Test
    void theLanguageIsTheLocaleOfTheUserUnlessLangNamesOne() throws IOException {
        String input = copy(EXAMPLE);
        assertEquals("de", language(Map.of("LANG", "de_DE.UTF-8"), "Mac OS X", input));
        assertEquals("en", language(Map.of("LANG", "en_GB.UTF-8"), "Mac OS X", input));
        assertEquals("en", language(Map.of("LC_ALL", "C", "LANG", "de_DE.UTF-8"), "Linux",
                input));
        assertEquals("de", language(Map.of("LC_ALL", "", "LC_MESSAGES", "de_AT@euro"), "Linux",
                input));
        assertEquals("en", language(Map.of(), "Mac OS X", input));
        assertEquals("de", language(Map.of(), "Windows 11", input),
                "on Windows the locale of the user, which the machine says is German");
        assertEquals("en", language(Map.of("LANG", "de_DE.UTF-8"), "Mac OS X", input, "--lang",
                "en"));
    }

    @Test
    void aStemCarriesNoSeparatorNoControlAndNoReservedCharacter() {
        assertEquals("Rechnung_4711", ViewCommand.stem("Rechnung#4711.pdf"));
        assertEquals("a_b_c_d", ViewCommand.stem("a<b>c?d.xml"));
        assertEquals("tab_here", ViewCommand.stem("tab\there.xml"));
        assertEquals("invoice.esj", ViewCommand.stem("invoice.esj.json"));
        assertEquals(".hidden", ViewCommand.stem(".hidden"));
        assertEquals("x".repeat(80), ViewCommand.stem("x".repeat(200) + ".pdf"));
        assertEquals("100_ ü", ViewCommand.stem("100% ü.pdf"));
    }

    @Test
    void aCommandOnThePageIsAWordOfTheShell() {
        assertEquals("invoice.pdf", ViewCommand.quoted("invoice.pdf"));
        assertEquals("'Rechnung 4711.pdf'", ViewCommand.quoted("Rechnung 4711.pdf"));
        assertEquals("'it'\\''s.pdf'", ViewCommand.quoted("it's.pdf"));
    }

    // ------------------------------------------------------------------- helpers

    /** Returns the language a page was written in, from its {@code lang} attribute. */
    private String language(Map<String, String> environment, String os, String input,
                            String... more) throws IOException {
        Path directory = out.resolve("lang-" + environment.hashCode() + "-" + os.hashCode()
                + "-" + more.length);
        List<String> args = new ArrayList<>(List.of("view", "--out", directory.toString()));
        args.addAll(List.of(more));
        args.add(input);
        Desktop.Machine machine = new Desktop.Machine(os, temporary, "de", launcher);
        Cli.Run run = Cli.runOn(machine, environment, args.toArray(String[]::new));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String page = Files.readString(directory.resolve("index.html"));
        int at = page.indexOf("<html lang=\"") + "<html lang=\"".length();
        return page.substring(at, at + 2);
    }

    private Desktop.Machine machine(String os) {
        return new Desktop.Machine(os, temporary, "en", launcher);
    }

    /** Runs {@code esj view} on the machine of this test. */
    private Cli.Run view(String... args) {
        return Cli.runOn(machine("Mac OS X"), Map.of(), args);
    }

    private String normalized(String page, Path directory) {
        return page.replace(directory.toString(), "${directory}")
                .replace(in.toString(), "${input}")
                .replace(TOOL, "esj ${version}");
    }

    private String copy(String resource) {
        return Fixtures.file(in, resource);
    }

    private String write(String name, byte[] content) {
        return Fixtures.write(in, name, content);
    }

    /** Returns a PDF with as many cross industry invoices as asked, none saying it is the one. */
    private static byte[] several(int count) {
        TestPdfs.Builder builder = TestPdfs.builder();
        for (int i = 1; i <= count; i++) {
            builder.attach("invoice-" + i + ".xml", Fixtures.bytes(CII));
        }
        return builder.build();
    }

    private static byte[] latin1() {
        String text = new String(Fixtures.bytes(CII), StandardCharsets.UTF_8)
                .replace('…', '.');
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static void assertSameBytes(Path expected, Path actual) {
        try {
            assertArrayEquals(Files.readAllBytes(expected), Files.readAllBytes(actual),
                    actual + " is the bytes of " + expected);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String version() {
        for (String line : Cli.run("--version").text().split("\n")) {
            if (line.startsWith("esj ")) {
                return line.substring("esj ".length()).trim();
            }
        }
        throw new IllegalStateException("esj --version names the version of the build");
    }

    /** A launcher that records what it was asked to start and starts nothing. */
    private static final class Recording implements Desktop.Launcher {

        private final List<List<String>> opened = new ArrayList<>();
        private boolean opens = true;
        private Optional<String> downloads = Optional.empty();

        @Override
        public boolean open(List<String> command) {
            opened.add(List.copyOf(command));
            return opens;
        }

        @Override
        public Optional<String> ask(List<String> command) {
            assertEquals(List.of("xdg-user-dir", "DOWNLOAD"), command);
            return downloads;
        }
    }
}
