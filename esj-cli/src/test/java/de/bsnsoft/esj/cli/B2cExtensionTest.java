package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.model.Registry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code --extension b2c} through the command line.
 *
 * <p>The registry of the B2C extension ships in the same jar as the core one, and the
 * option loads it the way it loads the XRechnung extension: the terms are read as values,
 * checked by the structural layers and shown by the commands that show values. Without the
 * option a path no loaded registry describes is a gap in the coverage of the run and not a
 * finding about the invoice (specification, sections 9.2 and 11.1).
 *
 * <p>The registry declares {@code "transport": "none"}: its terms are bound by no transport
 * syntax by design, and a gross-priced invoice travels as the net values of the core terms
 * with the difference in BT-114. So the CII written from such a document is the whole
 * invoice, the official artefacts judge it, and the run is {@code VALID}. Without the
 * option the four paths are measured against nothing and the run is
 * {@code INDETERMINATE} for that reason.
 *
 * <p>The branded template {@code examples/templates/gross.json} is rendered here against
 * that registry rather than against a fixture, which is the whole of what the extension
 * adds to a rendering: the figures the buyer was shown stand in their places, marked as
 * displayed, and the net amounts, the VAT breakdown and the totals of the standard stay on
 * the page.
 */
class B2cExtensionTest {

    /** The consumer invoice of the repository, which carries the four B2C terms. */
    private static final String EXAMPLE = "examples/b2c-gross.esj.json";

    /** The figures of the standard, which a gross rendering does not replace. */
    private static final List<String> NET = List.of("128,97", "24,50", "153,47", "84,03");

    /** The figures the buyer was shown, which only the extension carries. */
    private static final List<String> DISPLAYED = List.of("99,99", "39,98", "15,96", "13,50");

    @TempDir
    private Path directory;

    /**
     * With the registry loaded the four terms are measured, nothing is found, and the
     * official artefacts run over the CII the document is written to. That XML carries the
     * net values and BT-114 and is the whole invoice, so the row counts and the verdict is
     * {@code VALID}; the four terms that stayed behind are named beside it.
     */
    @Test
    void checksTheExampleWithTheExtensionLoaded() {
        Cli.Run run = Cli.run("validate", "--extension", "b2c", example());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(run.text().contains("ESJ-L2-NOT-CHECKED"), run.text());
        assertFalse(run.text().contains("extension-registry-missing"), run.text());
        assertFalse(run.text().contains("term-not-in-syntax"), run.text());
        assertTrue(run.text().contains("4 terms of ESJ-B2C 0.1 stay in the ESJ document by"
                + " design: BT-B2C-010, BT-B2C-001, BT-B2C-002, BT-B2C-003"), run.text());
        assertTrue(run.text().contains("Model (L2):                OK"), run.text());
        assertTrue(run.text().endsWith("VALID\n"), run.text());
    }

    /**
     * The same run as a machine reads it: the written row is checked, no cause stands
     * against it, and the terms that stayed in the document are an array of their own,
     * under the registry that declared them untransported.
     */
    @Test
    void namesTheTermsThatStayedInTheDocumentInTheJsonForm() {
        Cli.Run run = Cli.run("validate", "--extension", "b2c", "--output", "json",
                example());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"verdict\": \"VALID\""), run.text());
        assertTrue(run.text().contains("\"registry\": \"ESJ-B2C 0.1\""), run.text());
        for (String term : List.of("BT-B2C-001", "BT-B2C-002", "BT-B2C-003", "BT-B2C-010")) {
            assertTrue(run.text().contains("\"" + term + "\""),
                    "the array names " + term + ": " + run.text());
        }
    }

    /**
     * The declaration is a fact of the registry file and of no other: the B2C registry
     * makes it, the XRechnung extension does not, and a core registry — the thing a syntax
     * binds — never does.
     */
    @Test
    void leavesTheDeclarationToTheRegistry() {
        assertTrue(Registry.b2cExtension().withoutTransport(),
                "the B2C registry declares that its terms do not travel");
        assertFalse(Registry.xrechnungExtension().withoutTransport(),
                "the XRechnung extension is bound by a syntax and declares nothing");
        assertFalse(Registry.en16931().withoutTransport(),
                "a core registry is the thing a syntax binds");
    }

    /**
     * Without the option the four terms are paths no loaded registry describes: the model
     * layer says so once per path, the layers below it measure nothing, and the verdict is
     * the one the specification gives a run that did not check everything.
     */
    @Test
    void leavesTheExampleIndeterminateWithoutTheExtension() {
        Cli.Run run = Cli.run("validate", example());

        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("INDETERMINATE"), run.text());
        assertTrue(run.text().contains("extension-registry-missing"), run.text());
        assertTrue(run.text().contains("the registry that defines BT-B2C-010 is not loaded"),
                run.text());
    }

    /** Both registries describe terms of one core model, and one run may load both. */
    @Test
    void loadsTheTwoExtensionsTogether() {
        Cli.Run run = Cli.run("validate", "--extension", "xrechnung,b2c", example());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(run.text().contains("ESJ-L2-NOT-CHECKED"), run.text());
        assertFalse(run.text().contains("extension-registry-missing"), run.text());
        assertTrue(run.text().contains("Model (L2):                OK"), run.text());
    }

    /**
     * The report says it in both languages: a row of its own for the registry that
     * declared its terms untransported, and the terms it left in the ESJ document.
     */
    @Test
    void namesTheTermsThatStayedInTheDocumentInTheReport() throws IOException {
        String german = report("de");
        String english = report("en");

        assertTrue(german.contains("Begriffe aus ESJ-B2C 0.1 ohne Transportbindung"), german);
        assertTrue(german.contains("planmäßig nur im ESJ-Dokument: BT-B2C-010, BT-B2C-001,"
                + " BT-B2C-002, BT-B2C-003"), german);
        assertTrue(english.contains("Terms of ESJ-B2C 0.1 without a transport binding"),
                english);
        assertTrue(english.contains("by design in the ESJ document only: BT-B2C-010,"
                + " BT-B2C-001, BT-B2C-002, BT-B2C-003"), english);
    }

    /** Writes the report of the example in one language and returns it. */
    private String report(String language) throws IOException {
        Path target = directory.resolve("report-" + language + ".html");
        Cli.Run run = Cli.run("validate", "--extension", "b2c", "--report",
                target.toString(), "--report-lang", language, example());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    /** A name this build does not ship is an answer about the command line. */
    @Test
    void refusesAnExtensionItDoesNotShip() {
        Cli.Run run = Cli.run("validate", "--extension", "nope", example());

        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--extension takes xrechnung or b2c"), run.err());
    }

    /** {@code list} writes the semantic data type of the registry that defines the term. */
    @Test
    void listsTheExtensionTermsWithTheirTypes() {
        Cli.Run run = Cli.run("list", "--extension", "b2c", example());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("/BG-25/0/BT-B2C-001\tUnitPriceAmount\t99.99"),
                run.text());
        assertTrue(run.text().contains("/BT-B2C-010\tAmount\t153.47"), run.text());
    }

    /**
     * A term no loaded registry describes has no type to print, and the column says so.
     * The value is still the document's and is still printed: {@code list} shows what the
     * file carries, and only the registry column depends on what was loaded.
     */
    @Test
    void listsATermOfNoLoadedRegistryWithoutAType() {
        Cli.Run run = Cli.run("list", example());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("/BT-B2C-010\t-\t153.47"), run.text());
    }

    @Test
    void readsOneExtensionValueWithGet() {
        Cli.Run run = Cli.run("get", example(), "/BG-25/1/BT-B2C-002", "--extension", "b2c");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals("39.98\n", run.text());
    }

    /**
     * {@code inspect} runs the structural layers and not the rules, so its code is the one
     * it gives any document; what the extension changes is the model layer, which measures
     * the four paths instead of reporting them as not checked.
     */
    @Test
    void inspectsTheExampleWithTheExtensionLoaded() {
        Cli.Run core = Cli.run("inspect",
                Fixtures.file(directory, "examples/standard-invoice.esj.json"));

        Cli.Run run = Cli.run("inspect", "--extension", "b2c", example());

        assertEquals(core.exitCode(), run.exitCode(), run.err());
        assertTrue(run.text().contains("Model (L2):                OK"), run.text());
        assertTrue(run.text().contains("Total with VAT (BT-112):    153.47"), run.text());
    }

    /**
     * The branded template on the registry of the extension: every placed figure is on the
     * page under its label and marked as displayed, the figures of the standard are there
     * beside them, and the note says what a displayed figure is.
     */
    @Test
    void rendersTheGrossTemplateAgainstTheRegistry() throws IOException {
        Cli.Run run = Cli.run("render", example(), "--extension", "b2c",
                "--template", template(), "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String text = flat(run.out());
        for (String net : NET) {
            assertTrue(text.contains(net), "the figure " + net + " of the standard is on "
                    + "the page");
        }
        for (String displayed : DISPLAYED) {
            assertTrue(text.contains(displayed),
                    "the displayed figure " + displayed + " is on the page");
        }
        String tight = text.replaceAll("\\s+", "");
        assertTrue(tight.contains("Einzelpreisbrutto(angezeigt,EUR)"), text);
        assertTrue(tight.contains("USt.-BetragderPosition(angezeigt,EUR)"), text);
        assertTrue(tight.contains("Betragbrutto(angezeigt,EUR)"), text);
        assertTrue(tight.contains("Bruttogesamtbetrag(angezeigt)"), text);
        assertTrue(text.contains("Aufschlüsselung der Umsatzsteuer"),
                "the VAT breakdown of the standard stays on the page");
        assertTrue(text.contains("Gesamtsumme brutto"),
                "and so do the totals of the standard");
    }

    /**
     * BT-114 is the invoice rounding amount of EN 16931-1 and no balancing account of the
     * extension: the template writes into no core term, so a document without BT-114 is
     * rendered without a rounding row however many displayed figures it carries.
     */
    @Test
    void writesNoRoundingAmountTheDocumentDoesNotCarry() throws IOException {
        Cli.Run run = Cli.run("render", example(), "--extension", "b2c",
                "--template", template(), "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(flat(run.out()).contains("Rundungsbetrag"),
                "the document carries no BT-114 and the rendering invents none");
    }

    /**
     * The same template over a document without the extension: no column, no row, no note.
     * One template renders a consumer invoice and a business invoice.
     */
    @Test
    void leavesTheTemplatePlacesEmptyForABusinessInvoice() throws IOException {
        Cli.Run run = Cli.run("render", Fixtures.file(directory,
                        "examples/standard-invoice.esj.json"),
                "--template", template(), "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(flat(run.out()).contains("(angezeigt"),
                "nothing on the page is marked as displayed");
    }

    /**
     * Without a template the generic layout prints the four values as what they are:
     * values of an extension, marked with its namespace and never inside a totals row of
     * the core model.
     */
    @Test
    void marksTheTermsWithTheirNamespaceInTheGenericLayout() {
        Cli.Run run = Cli.run("render", example(), "--extension", "b2c", "--out", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        String text = flat(run.out());
        assertTrue(text.contains("(B2C)"), "the values are marked as an extension's");
        assertFalse(text.contains("(angezeigt"),
                "and no place of the branded template appears without one");
    }

    /** Writes the consumer invoice into the temporary directory and names it. */
    private String example() {
        return Fixtures.file(directory, EXAMPLE);
    }

    /**
     * Copies the gross template and the letterhead it names into a directory of their own
     * and names the template, because a template is read with the files it names beside
     * it.
     */
    private String template() throws IOException {
        Path into = Files.createDirectory(directory.resolve("templates"));
        for (String file : new String[] {"gross.json", "letterhead.pdf"}) {
            Files.write(into.resolve(file), Fixtures.bytes("examples/templates/" + file));
        }
        return into.resolve("gross.json").toString();
    }

    /** Returns the text of a rendering with the runs of whitespace collapsed. */
    private static String flat(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ").strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The registry the option loads, used to keep the example honest about its terms. */
    @Test
    void theExampleCarriesEveryTermOfTheRegistry() {
        Registry registry = Registry.en16931().withExtension(Registry.b2cExtension());
        Cli.Run run = Cli.run("list", "--extension", "b2c", example());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        for (String term : List.of("BT-B2C-001", "BT-B2C-002", "BT-B2C-003", "BT-B2C-010")) {
            assertTrue(registry.term(term).isPresent(), "the registry defines " + term);
            assertTrue(run.text().contains(term),
                    "and " + EXAMPLE + " shows what it looks like");
        }
    }
}
