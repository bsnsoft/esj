package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.pdf.EmbeddedFile;
import de.bsnsoft.esj.pdf.PdfContainer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The hybrid invoice carries one embedded file, the invoice XML, and a hybrid written by
 * 0.9.0 to 0.9.3 is read as what it is.
 *
 * <p>Those versions put the same invoice beside the XML a second time, as an ESJ document
 * under {@code invoice.esj.json}, declared as a supplement. Such a file is an ordinary
 * hybrid invoice with one more attachment that is not the invoice, like a logo or a
 * delivery note: the commands that list attachments list it, nothing reads it, and no
 * verdict, row, finding or reason is about it. A file whose only attachment is such a
 * JSON file carries no invoice.
 */
class EarlierHybridsTest {

    private static final String EXAMPLE = "examples/standard-invoice.esj.json";

    /** The canonical bytes of the example, which is what those versions attached. */
    private static final String CANONICAL = "examples/standard-invoice.canonical.esj.json";

    /** The consumer invoice, whose four B2C terms the cross industry invoice does not bind. */
    private static final String B2C = "examples/b2c-gross.esj.json";

    /** The name those versions gave the ESJ document inside the PDF. */
    private static final String JSON = "invoice.esj.json";

    /** What no output of this build says about a container any more. */
    private static final List<String> GONE = List.of("PDF-ESJ", "ESJ document attached",
            "ESJ-Dokument beigelegt", "checked against the invoice", "ESJ document, not the",
            "attached beside it", "\"esj\"");

    @TempDir
    Path directory;

    @Test
    void validateJudgesTheInvoiceAndIgnoresTheJsonBesideIt() {
        String pdf = write("earlier.pdf", earlier());
        Path html = directory.resolve("report.html");
        Path report = directory.resolve("report.pdf");

        Cli.Run text = Cli.run("validate", pdf);
        Cli.Run json = Cli.run("validate", pdf, "--output", "json");
        Cli.Run english = Cli.run("validate", pdf, "--report", html.toString(),
                "--report-lang", "en");
        Cli.Run german = Cli.run("validate", pdf, "--report", report.toString());

        for (Cli.Run run : List.of(text, json, english, german)) {
            assertEquals(ExitCode.SUCCESS, run.exitCode(), run.text() + run.err());
        }
        assertTrue(text.text().contains("Embedded invoice: \"factur-x.xml\""), text.text());
        assertTrue(text.text().contains("Container:        OK"), text.text());
        assertTrue(text.text().contains("Invoice:          VALID"), text.text());
        assertTrue(json.text().contains("\"verdict\": \"VALID\""), json.text());
        Map<String, String> outputs = Map.of(
                "the lines", text.text() + text.err(),
                "the JSON", json.text() + json.err(),
                "the HTML report", read(html),
                "the PDF report", pdfText(report));
        outputs.forEach((what, output) -> GONE.forEach(word ->
                assertFalse(output.contains(word), what + " says " + word + ": " + output)));
    }

    @Test
    void theListingCommandsShowItAsAnAttachmentLikeAnyOther() {
        byte[] pdf = earlier();

        Cli.Run list = Cli.run(pdf, "extract", "-", "--list");
        Cli.Run inspect = Cli.run(pdf, "inspect", "-");
        Cli.Run extract = Cli.run(pdf, "extract", "-");
        Cli.Run named = Cli.run(pdf, "extract", "-", "--attachment", JSON);

        assertEquals(ExitCode.SUCCESS, list.exitCode(), list.err());
        assertTrue(list.text().contains("Attachments:  2"), list.text());
        String line = "  2  \"invoice.esj.json\" (not XML, ";
        assertTrue(list.text().contains(line), list.text());
        assertTrue(list.text().contains(", media type \"application/json\","
                + " AFRelationship \"Supplement\", in /AF"), list.text());
        assertEquals(ExitCode.INDETERMINATE, inspect.exitCode(), inspect.err());
        assertTrue(inspect.text().contains(line), inspect.text());
        assertEquals(ExitCode.SUCCESS, extract.exitCode(), extract.err());
        assertTrue(new String(extract.out(), StandardCharsets.UTF_8)
                .contains("CrossIndustryInvoice"), "extract hands out the invoice XML");
        assertEquals(ExitCode.SUCCESS, named.exitCode(), named.err());
        assertEquals(new String(Fixtures.bytes(CANONICAL), StandardCharsets.UTF_8),
                new String(named.out(), StandardCharsets.UTF_8),
                "and the JSON file where a caller names it, as any attachment");
        for (Cli.Run run : List.of(list, inspect)) {
            GONE.forEach(word -> assertFalse(run.text().contains(word), run.text()));
        }
    }

    /** A PDF whose one attachment is such a JSON file is a PDF with a logo and no invoice. */
    @Test
    void aFileCarryingOnlyTheJsonCarriesNoInvoice() {
        byte[] pdf = TestPdfs.builder()
                .attach(new TestPdfs.Attachment(JSON, Fixtures.bytes(CANONICAL),
                        "application/json", "Supplement", true))
                .build();

        Cli.Run validate = Cli.run(pdf, "validate", "-");

        assertEquals(ExitCode.INPUT, validate.exitCode(), validate.err());
        assertTrue(validate.err().contains("the PDF contains no structured invoice"
                + " representation; none of its attachments is an electronic invoice"),
                validate.err());
        assertTrue(validate.err().contains("\"invoice.esj.json\" (not XML"), validate.err());
        GONE.forEach(word -> assertFalse(validate.err().contains(word), validate.err()));
    }

    /** Both commands that write a hybrid write the invoice XML and nothing beside it. */
    @Test
    void embedAndRenderWriteTheInvoiceXmlAlone() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        Path pages = directory.resolve("pages.pdf");
        Path embedded = directory.resolve("embedded.pdf");
        Path rendered = directory.resolve("rendered.pdf");
        assertEquals(ExitCode.SUCCESS,
                Cli.run("render", invoice, "--out", pages.toString()).exitCode());

        Cli.Run embed = Cli.run("embed", pages.toString(), invoice,
                "--out", embedded.toString());
        Cli.Run render = Cli.run("render", invoice, "--embed", "cii",
                "--out", rendered.toString());

        for (Cli.Run run : List.of(embed, render)) {
            assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
            assertEquals("", run.err(), "a document that travels whole is written in silence");
        }
        for (Path file : List.of(embedded, rendered)) {
            assertEquals(List.of(TestPdfs.FACTUR_X), names(file), file.toString());
        }
    }

    /**
     * The consumer invoice goes into the container as the cross industry invoice of its
     * net core terms, and that file is valid as it stands: the four B2C terms are named
     * on the error stream as staying in the ESJ document, which is the caller's file.
     */
    @Test
    void theConsumerInvoiceIsAHybridOfItsXmlAndValid() {
        String invoice = Fixtures.file(directory, B2C);
        Path hybrid = directory.resolve("b2c.pdf");

        Cli.Run rendered = Cli.run("render", invoice, "--extension", "b2c", "--embed", "cii",
                "--out", hybrid.toString());
        Cli.Run validated = Cli.run("validate", hybrid.toString());

        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());
        assertEquals(List.of(TestPdfs.FACTUR_X), names(hybrid));
        assertEquals(ExitCode.SUCCESS, validated.exitCode(),
                validated.text() + validated.err());
        assertTrue(validated.text().contains("Container:        OK"), validated.text());
        assertTrue(validated.text().contains("Invoice:          VALID"), validated.text());
        assertFalse(validated.text().contains("B2C"), validated.text());
    }

    /** No switch is left over, and the help of the two commands says nothing about one. */
    @Test
    void theHelpOfTheWritingCommandsOffersNoSecondAttachment() {
        for (String command : List.of("embed", "render")) {
            Cli.Run help = Cli.run(command, "--help");
            assertEquals(ExitCode.SUCCESS, help.exitCode(), help.err());
            for (String word : List.of("--no-esj", "--with-esj", JSON, "beside the XML")) {
                assertFalse(help.text().contains(word), command + " --help: " + help.text());
            }
        }
        Cli.Run refused = Cli.run("render", Fixtures.file(directory, EXAMPLE), "--embed", "cii",
                "--no-esj", "--out", directory.resolve("out.pdf").toString());
        assertNotEquals(ExitCode.SUCCESS, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("--no-esj"), refused.err());
    }

    /** Returns a hybrid of this build with the ESJ document enclosed as 0.9.0 to 0.9.3 did. */
    private byte[] earlier() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        Path hybrid = directory.resolve("hybrid.pdf");
        Cli.Run rendered = Cli.run("render", invoice, "--embed", "cii",
                "--out", hybrid.toString());
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());
        byte[] enclosed = TestPdfs.enclose(bytes(hybrid), new TestPdfs.Attachment(JSON,
                Fixtures.bytes(CANONICAL), "application/json", "Supplement", true));
        assertEquals(List.of(TestPdfs.FACTUR_X, JSON), names(enclosed));
        return enclosed;
    }

    /** Returns the names of the embedded files of a PDF, in the order the file lists them. */
    private static List<String> names(Path file) {
        return names(bytes(file));
    }

    private static List<String> names(byte[] pdf) {
        try (PdfContainer container = PdfContainer.open(pdf)) {
            return container.embeddedFiles().stream().map(EmbeddedFile::name).toList();
        }
    }

    private String write(String name, byte[] content) {
        return Fixtures.write(directory, name, content);
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        return new String(bytes(file), StandardCharsets.UTF_8);
    }

    /** Returns the text of a PDF, whitespace collapsed. */
    private static String pdfText(Path file) {
        try (PDDocument document = Loader.loadPDF(bytes(file))) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ").strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
