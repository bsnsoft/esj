package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.bindings.WriterOptions;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine.TypeConversionException;

/**
 * The resource bounds of a run: the switches that set them, the profiles they come from,
 * and the exit code a document that outgrows one leaves with.
 *
 * <p>The exit code is the point of the whole group. A limit is the reading party's policy
 * and takes no part in conformance (specification, sections 3.1 and 12.2), so a caller
 * must be able to tell "this run was configured to read less than the document holds" from
 * "this invoice is invalid" without reading English — and from "these bytes are not a
 * document", which is the answer the tool gave before exit code 7 existed.
 */
class LimitsTest {

    /** A document of the repository that carries more than a handful of values. */
    private static final String INVOICE = "examples/standard-invoice.esj.json";

    /** One corpus invoice, for the bound on the XML front door. */
    private static final String UBL =
            "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml";

    @TempDir
    private Path directory;

    @Test
    void theDefaultProfileIsTheReferenceConfiguration() {
        Bounds bounds = Bounds.profile(Bounds.DEFAULT_PROFILE);
        assertEquals(Limits.defaults(), bounds.readerLimits());
        assertEquals(XrImporter.DEFAULT_MAX_INPUT_BYTES, bounds.maxInputBytes());
        assertEquals(WriterOptions.DEFAULT_MAX_OUTPUT_BYTES, bounds.maxOutputBytes());
    }

    @Test
    void theLargeProfileIsSizedForInvoicesOfHundredsOfThousandsOfLines() {
        Bounds bounds = Bounds.profile(Bounds.LARGE_PROFILE);
        assertEquals(256L * 1024 * 1024, bounds.maxInputBytes());
        assertEquals(1024L * 1024 * 1024, bounds.maxOutputBytes(),
                "a cross industry invoice is larger than the invoice it came from");
        assertEquals(512L * 1024 * 1024, bounds.maxDocumentBytes());
        assertEquals(8_000_000, bounds.readerLimits().maxValues());
        assertEquals(8_000_000, bounds.readerLimits().maxExtensionNodes());
        assertEquals(Limits.defaults().maxStringBytes(), bounds.readerLimits().maxStringBytes(),
                "a larger invoice is more values, not longer ones");
        assertEquals(Limits.defaults().maxBinaryValueBytes(),
                bounds.readerLimits().maxBinaryValueBytes());
    }

    @Test
    void refusesAProfileThisVersionDoesNotShip() {
        CliException refused = assertThrows(CliException.class, () -> Bounds.profile("huge"));
        assertEquals(ExitCode.INPUT, refused.exitCode(), "a mistyped request, not a limit");
        assertTrue(refused.getMessage().contains("large"), refused.getMessage());
    }

    @ParameterizedTest(name = "{0} is {1} bytes")
    @CsvSource({"1024, 1024", "1k, 1024", "1K, 1024", "2M, 2097152", "3m, 3145728",
                "1G, 1073741824", "0, 0"})
    void readsAByteCountWithAndWithoutASuffix(String written, long expected) {
        assertEquals(expected, Numbers.bytes(written));
    }

    @ParameterizedTest(name = "{0} is not a byte count")
    @ValueSource(strings = {"xx", "", "1kk", "-1", "1.5M", "k", "9999999999999999999"})
    void refusesAValueThatIsNotAByteCount(String written) {
        assertThrows(TypeConversionException.class, () -> Numbers.bytes(written));
    }

    @Test
    void namesTheSwitchOnTheOptionThatWasWrittenWrong() {
        Cli.Run run = Cli.run("convert", "--max-values", "xx", INVOICE);
        assertEquals(ExitCode.INPUT, run.exitCode(), "a mistyped request, not a limit");
        assertTrue(run.err().contains("--max-values"), run.err());
    }

    @Test
    void refusesADocumentWithMoreValuesThanTheRunReads() {
        Cli.Run run = Cli.run("convert", "--max-values", "5", file(INVOICE));
        assertLimit(run, "--max-values");
        assertTrue(run.err().contains("values carries more than 5"), run.err());
    }

    /**
     * A run a limit stopped reaches none of the three states and says none of the three
     * words: exit code 7 has the single meaning that a bound was met, and the answer to it
     * is a larger bound and never a rejection of the document. The cause names the limit
     * and not the caller, who asked for the whole check.
     */
    @Test
    void reportsALimitAsNoVerdictAtAllWithTheLimitAsItsCause() {
        Cli.Run text = Cli.run("validate", "--max-values", "5", file(INVOICE));
        assertEquals(ExitCode.LIMIT, text.exitCode(), text.err());
        assertTrue(text.text().contains(
                        Reports.NO_VERDICT + " — a limit of this run was reached"),
                text.text());
        assertFalse(text.text().contains("INDETERMINATE"),
                "a run that judged nothing offers no word to branch on: " + text.text());
        assertFalse(text.text().contains("skipped-by-caller"), text.text());

        Cli.Run json = Cli.run("validate", "--max-values", "5", "--output", "json",
                file(INVOICE));
        assertEquals(ExitCode.LIMIT, json.exitCode(), json.err());
        assertTrue(json.text().contains("\"verdict\": null"), json.text());
        assertTrue(json.text().contains("limit-reached"), json.text());
        assertFalse(json.text().contains("skipped-by-caller"), json.text());
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "--max-path-segments, 2, examples/standard-invoice.esj.json",
        "--max-string-bytes, 4, examples/standard-invoice.esj.json",
        "--max-binary-bytes, 8, examples/multiple-lines.esj.json",
        "--max-extension-nodes, 1, examples/extension-depth.esj.json"})
    void namesEveryBoundItWasStoppedByAndTheSwitchThatRaisesIt(String option, String value,
                                                               String document) {
        assertLimit(Cli.run("convert", option, value, file(document)), option);
    }

    /**
     * The bytes a conversion writes are their own bound. A cross industry invoice is
     * larger than the UBL invoice it came from, so a conversion measured against the
     * bound on its input would be refused while every bound of the run still had room —
     * and a refusal has to name a switch, which the writer's bound had none of until it
     * got one.
     */
    @Test
    void boundsWhatAConversionWritesSeparatelyFromWhatItReads() {
        Cli.Run refused = Cli.run("convert", "--to", "cii", "--max-output-bytes", "512",
                file(INVOICE));
        assertLimit(refused, "--max-output-bytes");
        assertTrue(refused.err().contains("was not written"), refused.err());

        long size = Fixtures.bytes(UBL).length;
        Cli.Run written = Cli.run("convert", "--to", "cii", "--max-input-bytes",
                Long.toString(size + 1), file(UBL));
        assertEquals(ExitCode.SUCCESS, written.exitCode(), written.err());
        assertTrue(written.out().length > size,
                "the cross industry invoice is longer than the UBL invoice it came from,"
                        + " and the bound on the input did not refuse it");
    }

    @Test
    void refusesAnXmlInputLargerThanTheRunReads() {
        Cli.Run run = Cli.run("convert", "--max-input-bytes", "1k", file(UBL));
        assertLimit(run, "--max-input-bytes");
        assertTrue(run.err().contains("1024 bytes of XML"), run.err());
    }

    @Test
    void refusesADocumentOnTheStandardInputLargerThanTheRunReads() {
        Cli.Run run = Cli.run(Fixtures.bytes(INVOICE), "convert", "--max-document-bytes", "1k",
                Input.STDIN_ARGUMENT);
        assertLimit(run, "--max-document-bytes");
        assertTrue(run.err().contains(Input.STDIN_NAME), run.err());
    }

    @Test
    void keepsExitCodeTwoForBytesThatAreNoDocumentAtAll() {
        Cli.Run run = Cli.run("neither JSON nor XML".getBytes(StandardCharsets.UTF_8),
                "convert", Input.STDIN_ARGUMENT);
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
    }

    @Test
    void keepsExitCodeTwoForAnEsjDocumentThatIsMalformed() {
        Cli.Run run = Cli.run("{\"format\":".getBytes(StandardCharsets.UTF_8), "convert",
                Input.STDIN_ARGUMENT);
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
    }

    @Test
    void validateSaysNoVerdictRatherThanInvalidWhenItRanOutOfLimit() {
        Cli.Run run = Cli.run("validate", "--max-values", "5", file(INVOICE));
        assertNotEquals(ExitCode.VALIDATION, run.exitCode(),
                "a reader that stopped has said nothing about the invoice");
        assertLimit(run, "--max-values");
    }

    @Test
    void readsTheDocumentAgainOnceTheBoundIsWideEnough() {
        Cli.Run run = Cli.run("convert", "--max-values", "5000", file(INVOICE));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
    }

    @Test
    void appliesAnOverrideOnTopOfTheProfileWhicheverOrderTheyWereWrittenIn() {
        assertLimit(Cli.run("--limits", "large", "convert", "--max-values", "5", file(INVOICE)),
                "--max-values");
        assertLimit(Cli.run("convert", "--max-values", "5", "--limits", "large", file(INVOICE)),
                "--max-values");
    }

    @Test
    void doesNotOfferTheLargeProfileToARunThatIsAlreadyOnIt() {
        Cli.Run run = Cli.run("--limits", "large", "convert", "--max-values", "5",
                file(INVOICE));
        assertTrue(run.err().contains("--max-values raises that bound"), run.err());
        assertFalse(run.err().contains("--limits large"),
                "a hint that names what was already asked for teaches nothing: " + run.err());
    }

    @Test
    void readsADocumentOfTheOrdinarySizeUnderTheLargeProfileToo() {
        Cli.Run run = Cli.run("--limits", "large", "convert", file(INVOICE));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
    }

    @Test
    void takesALimitSwitchBeforeAndAfterTheCommand() {
        for (String[] args : new String[][] {
                {"--max-values", "5", "convert", file(INVOICE)},
                {"convert", "--max-values", "5", file(INVOICE)}}) {
            assertLimit(Cli.run(args), "--max-values");
        }
    }

    @Test
    void offersTheLargeProfileOnlyForTheBoundsItActuallyRaises() {
        Cli.Run values = Cli.run("convert", "--max-values", "5", file(INVOICE));
        assertTrue(values.err().contains("--limits large"),
                "the large profile raises the bound on the number of values: " + values.err());

        Cli.Run strings = Cli.run("convert", "--max-string-bytes", "4", file(INVOICE));
        assertEquals(ExitCode.LIMIT, strings.exitCode(), strings.err());
        assertTrue(strings.err().contains("--max-string-bytes raises that bound"),
                strings.err());
        assertFalse(strings.err().contains("--limits large"),
                "the large profile leaves the size of one value where it is, so offering it "
                        + "would cost the caller a second run for the same answer: "
                        + strings.err());
    }

    @Test
    void saysPlainlyWhereNoConfigurationOfThisVersionRaisesTheBound() {
        String nested = "{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{},"
                + "\"extensions\":{\"de.example.vendor\":" + "[".repeat(64) + "]".repeat(64) + "}}";
        Cli.Run run = Cli.run(nested.getBytes(StandardCharsets.UTF_8),
                "convert", Input.STDIN_ARGUMENT);

        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
        assertTrue(run.err().contains("no profile and no switch of this version raises"),
                "a caller at a fixed bound decides between retrying and passing the document "
                        + "on, and retrying would meet the same bound: " + run.err());
    }

    @Test
    void refusesAnImportThatABoundOfThisRunWouldHaveCutDown() throws IOException {
        Path invoice = withALongNote();

        Cli.Run refused = Cli.run("convert", invoice.toString());
        assertEquals(ExitCode.LIMIT, refused.exitCode(),
                "a bound that left a business term out of the document has edited the "
                        + "invoice, and a caller that branches on the exit code alone would "
                        + "have stored it as the one it received: " + refused.err());
        assertTrue(refused.err().contains("--max-string-bytes"),
                "the refusal names the switch that raises the bound: " + refused.err());
        assertEquals(0, refused.out().length,
                "and nothing of the cut-down document reaches the standard output");

        Cli.Run read = Cli.run("convert", "--max-string-bytes", "4M", invoice.toString());
        assertEquals(ExitCode.SUCCESS, read.exitCode(), read.err());
        assertTrue(read.text().contains("BT-22"),
                "the switch the refusal named is the one that brings the term back");
    }

    /**
     * Writes a corpus invoice whose first note is longer than the bound on a string value,
     * which is the shape of a document an importer would otherwise write without it.
     */
    private Path withALongNote() throws IOException {
        String invoice = new String(Fixtures.bytes(UBL), StandardCharsets.UTF_8);
        String note = "<cbc:Note>";
        assertTrue(invoice.contains(note), "the corpus invoice carries a note");
        Path file = directory.resolve("long-note.xml");
        Files.writeString(file, invoice.replaceFirst(note, note + "x".repeat(2 * 1024 * 1024)),
                StandardCharsets.UTF_8);
        return file;
    }

    /** Asserts that a run was stopped by a bound and said which switch raises it. */
    private static void assertLimit(Cli.Run run, String option) {
        assertEquals(ExitCode.LIMIT, run.exitCode(), run.err());
        assertTrue(run.err().contains(option), "the switch that raises it: " + run.err());
    }

    /** Writes a fixture of the classpath into a directory and returns its path. */
    private String file(String resource) {
        return Fixtures.file(directory, resource);
    }
}
