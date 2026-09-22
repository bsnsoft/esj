package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The lead use case from the command line: make a PDF a person can read that carries the
 * invoice a machine reads, and prove that what came out is sound.
 *
 * <p>Three steps, and this is the test that runs all three through the tool rather than
 * through the libraries: a document goes in, {@code esj render --template … --embed cii}
 * writes the branded PDF/A-3 with the cross industry invoice inside it, and
 * {@code esj validate} reads that file back and says what the container and the invoice
 * each are. {@code esj embed} is the same middle step over a PDF that came from somewhere
 * else, and it is checked here beside it.
 *
 * <p>What is asserted is the tool's own verdict on its own output: the container block
 * has nothing to report and the invoice is valid, over the official artefacts of its
 * profile. A producer whose files its own reader complains about is a producer or a
 * reader that is wrong, and this is where that would show.
 *
 * <p>PDF/A conformance is not asserted here, and the report the tool writes says as much:
 * the declaration is read out of the file and nothing in this build checks it. The
 * evidence for it is in {@code esj-render}, where every instance of the corpus is rendered,
 * embedded and validated against veraPDF, and {@code --verapdf} is how a caller gets the
 * same answer from the command line; {@code VerapdfOptionTest} is that switch.
 */
class HybridInvoiceCommandsTest {

    /** A document of this repository's own examples, in the format's own syntax. */
    private static final String EXAMPLE = "examples/standard-invoice.esj.json";

    /** Where the render templates of the repository sit on the test classpath. */
    private static final String TEMPLATES = "examples/templates/";

    /** The template the lead use case stands on: the letterhead in the letter layout. */
    private static final String TEMPLATE = "letter.json";

    /** The template of the generic layout, kept here beside it: a letterhead as images. */
    private static final String GENERIC_TEMPLATE = "image.json";

    /** The business cases this test renders, embeds and validates. */
    private static final String INSTANCES = "business-cases/standard/";

    /**
     * One in six of them.
     *
     * <p>The whole folder passes, and running the whole folder would pay for the official
     * Schematron sixty-six times to learn it once. A stride takes the spread of the folder
     * — both syntaxes, every business case group — at a cost every build can pay. The
     * extension cases are left out for a different reason: they carry terms that need
     * {@code --extension xrechnung}, which is a test about that option and not about a
     * container.
     */
    private static final int STRIDE = 6;

    /** The line the report ends the container's verdict with. */
    private static final String CONTAINER_OK = "Container:        OK";

    /** The line it ends the invoice's verdict with. */
    private static final String INVOICE_VALID = "Invoice:          VALID";

    @TempDir
    private Path directory;

    /** The instances this test runs, in the order the corpus lists them. */
    static List<String> instances() {
        List<String> taken = new ArrayList<>();
        List<String> corpus = Fixtures.corpus().stream()
                .filter(instance -> instance.startsWith(INSTANCES))
                .toList();
        for (int i = 0; i < corpus.size(); i += STRIDE) {
            taken.add(corpus.get(i));
        }
        return taken;
    }

    /**
     * Every instance taken from the corpus: rendered, embedded into its own rendering,
     * and validated as the file it became.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("instances")
    void aRenderedAndEmbeddedCorpusInstanceValidatesAsAHybridInvoice(String instance) {
        String source = Fixtures.write(directory, name(instance),
                Fixtures.bytes("conformance/kosit/" + instance));
        String pdf = directory.resolve(name(instance) + ".pdf").toString();

        Cli.Run rendered = Cli.run("render", source, "--embed", "cii", "--out", pdf);
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(),
                "rendering and embedding " + instance + ": " + rendered.err());

        Cli.Run validated = Cli.run("validate", pdf);
        assertEquals(ExitCode.SUCCESS, validated.exitCode(),
                "validating the file it wrote: " + validated.text() + validated.err());
        assertTrue(validated.text().contains(CONTAINER_OK),
                "the container this tool wrote, read by this tool: " + validated.text());
        assertTrue(validated.text().contains(INVOICE_VALID),
                "and the invoice inside it: " + validated.text());
    }

    /**
     * The lead use case, on a letterhead: an ESJ document becomes a branded PDF/A-3 with
     * the invoice inside it, and the file proves itself.
     *
     * <p>The template is the one the README shows, which draws the letter layout. The
     * generic layout goes the same way and is asserted beside it, because the container
     * and the invoice inside it are what this test is about and neither depends on where
     * a value stands on the page.
     */
    @Test
    void theBrandedHybridInvoiceOfOneCommandValidates() {
        Path templates = Fixtures.directory(directory, TEMPLATES);
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pdf = directory.resolve("branded.pdf").toString();

        Cli.Run rendered = Cli.run("render", invoice,
                "--template", templates.resolve(TEMPLATE).toString(),
                "--embed", "cii", "--lang", "en", "--out", pdf);
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());

        Cli.Run validated = Cli.run("validate", pdf);
        assertEquals(ExitCode.SUCCESS, validated.exitCode(),
                validated.text() + validated.err());
        assertTrue(validated.text().contains("Detected:         PDF (hybrid invoice"),
                "the file is read back as a container: " + validated.text());
        assertTrue(validated.text().contains("PDF/A-3 declared           yes, PDF/A-3B"
                + " — declared, not validated"),
                "and says what it is, in the words that say nothing checked it: "
                        + validated.text());
        assertTrue(validated.text().contains(CONTAINER_OK)
                && validated.text().contains(INVOICE_VALID), validated.text());

        String generic = directory.resolve("generic.pdf").toString();
        assertEquals(ExitCode.SUCCESS, Cli.run("render", invoice,
                "--template", templates.resolve(GENERIC_TEMPLATE).toString(),
                "--embed", "cii", "--lang", "en", "--out", generic).exitCode());
        Cli.Run both = Cli.run("validate", generic);
        assertTrue(both.text().contains(CONTAINER_OK)
                && both.text().contains(INVOICE_VALID),
                "the generic layout of the same document, the same way: " + both.text());
    }

    /** {@code esj embed} over pages this tool rendered in a run of its own. */
    @Test
    void embedWritesTheInvoiceIntoAPdfItWasNotAskedToRender() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        String hybrid = directory.resolve("hybrid.pdf").toString();
        assertEquals(ExitCode.SUCCESS,
                Cli.run("render", invoice, "--out", pages).exitCode());

        Cli.Run embedded = Cli.run("embed", pages, invoice, "--out", hybrid);
        assertEquals(ExitCode.SUCCESS, embedded.exitCode(), embedded.err());

        Cli.Run validated = Cli.run("validate", hybrid);
        assertEquals(ExitCode.SUCCESS, validated.exitCode(),
                validated.text() + validated.err());
        assertTrue(validated.text().contains("Embedded invoice: \"factur-x.xml\""),
                "under the name the container format gives it: " + validated.text());
        assertTrue(validated.text().contains(CONTAINER_OK)
                && validated.text().contains(INVOICE_VALID), validated.text());
    }

    /** The two routes to the same file are the same file, byte for byte. */
    @Test
    void renderingAndEmbeddingInOneCommandGivesWhatTheTwoCommandsGive() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        String twoSteps = directory.resolve("two-steps.pdf").toString();
        String oneStep = directory.resolve("one-step.pdf").toString();
        Cli.run("render", invoice, "--out", pages);
        Cli.run("embed", pages, invoice, "--out", twoSteps);
        Cli.run("render", invoice, "--embed", "cii", "--out", oneStep);

        assertArrayEquals(read(twoSteps), read(oneStep),
                "the rendering and the embedding are each a function of the document, so"
                        + " the order they are asked for in changes nothing");
    }

    /** A file that already carries an invoice is refused rather than given a second one. */
    @Test
    void aFileThatAlreadyCarriesAnInvoiceIsRefused() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String hybrid = directory.resolve("hybrid.pdf").toString();
        Cli.run("render", invoice, "--embed", "cii", "--out", hybrid);

        Cli.Run again = Cli.run("embed", hybrid, invoice,
                "--out", directory.resolve("twice.pdf").toString());
        assertEquals(ExitCode.INPUT, again.exitCode(), again.err());
        assertTrue(again.err().contains("already carries"), again.err());
    }

    /** A PDF that is not a PDF/A-3 file is refused, and the message says what it is. */
    @Test
    void aPdfThatDeclaresNoPdfaConformanceIsRefused() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String plain = Fixtures.write(directory, "plain.pdf", TestPdfs.builder().build());

        Cli.Run refused = Cli.run("embed", plain, invoice,
                "--out", directory.resolve("out.pdf").toString());
        assertEquals(ExitCode.INPUT, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("declares no PDF/A conformance"), refused.err());
    }

    /** A profile the caller writes out has to be the profile the document is written in. */
    @Test
    void aProfileTheDocumentDoesNotNameIsRefused() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        Cli.run("render", invoice, "--out", pages);

        Cli.Run refused = Cli.run("embed", pages, invoice, "--profile", "XRECHNUNG",
                "--out", directory.resolve("out.pdf").toString());
        assertEquals(ExitCode.INPUT, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("two different things"), refused.err());
    }

    /** A profile that is no EN 16931 invoice is an answer about the command line. */
    @Test
    void aProfileThisToolDoesNotWriteIsAnswerAboutTheCommandLine() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        Cli.run("render", invoice, "--out", pages);

        Cli.Run refused = Cli.run("embed", pages, invoice, "--profile", "MINIMUM",
                "--out", directory.resolve("out.pdf").toString());
        assertEquals(ExitCode.INPUT, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("--profile takes"), refused.err());
    }

    /** An attachment name no consumer looks for is refused before anything is written. */
    @Test
    void anAttachmentNameNoConsumerLooksForIsRefused() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        Cli.run("render", invoice, "--out", pages);

        Cli.Run refused = Cli.run("embed", pages, invoice, "--name", "invoice.xml",
                "--out", directory.resolve("out.pdf").toString());
        assertEquals(ExitCode.INPUT, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("found by no consumer"), refused.err());
    }

    /** An HTML page is no container, so a run that asks for both is refused. */
    @Test
    void embeddingIntoAnHtmlPageIsRefusedRatherThanIgnored() {
        String invoice = Fixtures.file(directory, EXAMPLE);

        Cli.Run refused = Cli.run("render", invoice, "--html", "--embed", "cii",
                "--out", directory.resolve("out.html").toString());
        assertEquals(ExitCode.INPUT, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("an HTML page is not one"), refused.err());
    }

    /** {@code --embed} writes a cross industry invoice and knows no other target yet. */
    @Test
    void anEmbeddingTargetThisVersionHasNotGotIsRefused() {
        String invoice = Fixtures.file(directory, EXAMPLE);

        Cli.Run refused = Cli.run("render", invoice, "--embed", "ubl",
                "--out", directory.resolve("out.pdf").toString());
        assertEquals(ExitCode.INPUT, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("--embed takes cii"), refused.err());
    }

    /**
     * The conventional name of an XRechnung in a PDF is refused, and the message says
     * why: no container specification says which XMP declaration belongs beside it, and
     * this tool writes no declaration it cannot name a specification for.
     */
    @Test
    void theConventionalXRechnungNameIsRefusedWithTheReason() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        Cli.run("render", invoice, "--out", pages);

        Cli.Run refused = Cli.run("embed", pages, invoice, "--name", "xrechnung.xml",
                "--out", directory.resolve("out.pdf").toString());
        assertEquals(ExitCode.INPUT, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("--profile XRECHNUNG"), refused.err());
    }

    /**
     * The other container flavour writes its own name and its own schema, and the two
     * travel together: the file says {@code zugferd-invoice.xml} where it declares the
     * ZUGFeRD 2.0 namespace and nowhere else.
     */
    @Test
    void theOtherFlavourWritesItsOwnNameAndItsOwnSchema() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        Cli.run("render", invoice, "--out", pages);
        String out = directory.resolve("zugferd.pdf").toString();

        Cli.Run run = Cli.run("embed", pages, invoice, "--name", "zugferd-invoice.xml",
                "--out", out);
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());

        Cli.Run inspected = Cli.run("inspect", out);
        assertTrue(inspected.text().contains("zugferd-invoice.xml"), inspected.text());
        String xmp = new String(read(out), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(xmp.contains("urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#"),
                "the declaration is the one that name belongs to");
        assertFalse(xmp.contains("urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#"),
                "and the other one is nowhere in the file");
    }

    /**
     * What the cross industry invoice had no place for is said, by both commands that
     * write one into a container. The document here carries a term of no published model,
     * which the binding table has no entry for; the file is written all the same, and the
     * caller is told what is not in it.
     */
    @Test
    void whatTheAttachmentHadNoPlaceForIsSaidByBothCommands() {
        String invoice = Fixtures.write(directory, "b2c.esj.json",
                CONSUMER_INVOICE.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String pages = directory.resolve("pages.pdf").toString();
        Cli.run("render", invoice, "--out", pages);

        Cli.Run embedded = Cli.run("embed", pages, invoice,
                "--out", directory.resolve("embedded.pdf").toString());
        assertEquals(ExitCode.SUCCESS, embedded.exitCode(), embedded.err());
        assertTrue(embedded.err().contains("no place in this syntax"), embedded.err());
        assertTrue(embedded.err().contains("BT-B2C-010"), embedded.err());

        Cli.Run rendered = Cli.run("render", invoice, "--embed", "cii",
                "--out", directory.resolve("hybrid.pdf").toString());
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());
        assertTrue(rendered.err().contains("BT-B2C-010"), rendered.err());
    }

    /** A document that travels whole says nothing, because there is nothing to say. */
    @Test
    void adocumentThatTravelsWholeIsEmbeddedWithoutAWord() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        Cli.run("render", invoice, "--out", pages);

        Cli.Run run = Cli.run("embed", pages, invoice,
                "--out", directory.resolve("out.pdf").toString());
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(run.err().contains("no place in this syntax"), run.err());
    }

    /** An invoice carrying a term of no published model, as ESJ. */
    private static final String CONSUMER_INVOICE = """
            {
              "format": "EN16931-Semantic-JSON",
              "version": "0.1",
              "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",
              "values": {
                "/BT-1": "RE-2026-0001",
                "/BT-2": "2026-01-15",
                "/BT-3": "380",
                "/BT-5": "EUR",
                "/BG-2/BT-24": "urn:cen.eu:en16931:2017",
                "/BG-4/BT-27": "Example GmbH",
                "/BG-4/BG-5/BT-40": "DE",
                "/BG-7/BT-44": "Muster AG",
                "/BG-7/BG-8/BT-55": "DE",
                "/BG-22/BT-106": "100",
                "/BG-22/BT-109": "100",
                "/BG-22/BT-112": "119",
                "/BG-22/BT-115": "119",
                "/BG-23/0/BT-116": "100",
                "/BG-23/0/BT-117": "19",
                "/BG-23/0/BT-118": "S",
                "/BG-23/0/BT-119": "19",
                "/BG-25/0/BT-126": "1",
                "/BG-25/0/BT-129": "1",
                "/BG-25/0/BT-130": "C62",
                "/BG-25/0/BT-131": "100",
                "/BG-25/0/BG-29/BT-146": "100",
                "/BG-25/0/BG-30/BT-151": "S",
                "/BG-25/0/BG-30/BT-152": "19",
                "/BG-25/0/BG-31/BT-153": "Shower fitting",
                "/BT-B2C-010": "119.00"
              }
            }
            """;

    /** Returns the bytes of a file the tool wrote. */
    private static byte[] read(String file) {
        try {
            return Files.readAllBytes(Path.of(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the file name an instance of the corpus is written under. */
    private static String name(String instance) {
        return instance.substring(instance.lastIndexOf('/') + 1);
    }
}
