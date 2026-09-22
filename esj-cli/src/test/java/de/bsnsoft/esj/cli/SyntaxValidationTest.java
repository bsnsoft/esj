package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.Packs;
import de.bsnsoft.esj.syntax.SyntaxLimitException;
import de.bsnsoft.esj.syntax.SyntaxOptions;
import de.bsnsoft.esj.syntax.SyntaxValidator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code esj validate} over an XML input: the half of the report the official validation
 * artefacts fill, and the switches that choose, replace or leave out the pack that
 * carries them.
 *
 * <p>The corpus instances are the positive cases — the artefacts of their own profile
 * have nothing fatal to say about them, and the command leaves with 0 — and a mutation of
 * one of them is the negative case. The mutation is arithmetic rather than structural on
 * purpose: it breaks a business rule of EN 16931, which is exactly the class of defect
 * this tool could not report before the packs existed, and the report has to name the
 * rule identifier its publisher gave it rather than one of this project's own.
 *
 * <p>What is asserted about the artefacts themselves is deliberately thin: their rules,
 * their wording and their severities are their publishers' and are checked in
 * {@code esj-syntax} against the whole corpus. What is checked here is that the command
 * runs them, reports what they said, and reaches the right exit code.
 */
class SyntaxValidationTest {

    private static final String UBL = "conformance/kosit/business-cases/standard/"
            + "01.01a-INVOICE_ubl.xml";
    private static final String CII = "conformance/kosit/business-cases/standard/"
            + "02.05a-INVOICE_uncefact.xml";
    private static final String ESJ = "examples/standard-invoice.esj.json";

    /** An instance of the CVD profile, which levels a rule of the CEN artefacts down. */
    private static final String CVD = "conformance/kosit/technical-cases/cvd/"
            + "02.01a-cvd_INVOICE_ubl.xml";

    /** The profile that instance names, which is the profile whose levels apply to it. */
    private static final String CVD_PROFILE = "urn:cen.eu:en16931:2017#compliant#"
            + "urn:xeinkauf.de:kosit:xrechnung_3.0#compliant#"
            + "urn:xeinkauf.de:kosit:xrechnung:cvd_0.9";

    /** The pack this build carries, which is the one every example below is run with. */
    private static final String PACK = "xrechnung/3.0.2/2026-08-31";

    /** How many copies of one invoice line make an import that takes seconds. */
    private static final int LINES = 1500;

    /** The budget that the steps before the import meet and the import does not. */
    private static final int BUDGET_MILLIS = 100;

    /** An input bound the repeated document is far below, so its size bounds nothing. */
    private static final String ROOM = "16M";

    @TempDir
    private Path directory;

    @Test
    void runsTheOfficialArtefactsOfTheProfileOverAUblInvoice() {
        Cli.Run run = Cli.run("validate", Fixtures.file(directory, UBL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("XML:                       OK"), run.text());
        assertTrue(run.text().contains("UBL 2.1 XSD:               OK"), run.text());
        assertTrue(run.text().contains("EN 16931 UBL Schematron:   OK"), run.text());
        assertTrue(run.text().contains("XRechnung UBL Schematron:"), run.text());
        assertTrue(run.text().contains("(pack " + PACK + ")"), run.text());
    }

    @Test
    void runsTheArtefactsOfTheOtherSyntaxOverACrossIndustryInvoice() {
        Cli.Run run = Cli.run("validate", Fixtures.file(directory, CII));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("CII D16B XSD:              OK"), run.text());
        assertTrue(run.text().contains("EN 16931 CII Schematron:   OK"), run.text());
        assertFalse(run.text().contains("UBL 2.1 XSD"),
                "the artefacts of the other syntax are not rows of this report: " + run.text());
    }

    @Test
    void listsTheArtefactsOfTheOtherSyntaxUnderVerbose() {
        Cli.Run run = Cli.run("--verbose", "validate", Fixtures.file(directory, CII));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("UBL 2.1 XSD:"),
                "--verbose shows what the pack carried and did not use: " + run.text());
        assertTrue(run.text().contains("skipped: it validates no"), run.text());
    }

    @Test
    void reportsWhatEachArtefactCostOnTheErrorStreamUnderVerbose() {
        Cli.Run run = Cli.run("--verbose", "validate", Fixtures.file(directory, UBL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("en16931-ubl-schematron (schematron) ran in "),
                run.err());
        assertTrue(run.err().contains("compiled in "), run.err());
        assertFalse(run.text().contains(" ms"),
                "a duration is never in the report itself: " + run.text());
    }

    @Test
    void reportsABusinessRuleTheOfficialSchematronCatchesAndLeavesWithOne() {
        Cli.Run run = Cli.run("validate", mutated());
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains("BR-CO-16"),
                "the rule identifier is the publisher's own: " + run.text());
        assertTrue(run.text().contains("[fatal]"), run.text());
        assertTrue(run.text().contains("EN 16931 UBL Schematron:   1 error"), run.text());
        assertTrue(run.text().endsWith("INVALID\n"), run.text());
        assertTrue(run.text().contains("Model (L2):                OK"),
                "the structural layers still pass: the document is sound, the sum is not");
    }

    @Test
    void carriesEveryFieldOfAFindingIntoTheJsonReport() {
        Cli.Run run = Cli.run("validate", "--output", "json", mutated());
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"code\": \"BR-CO-16\""), run.text());
        assertTrue(run.text().contains("\"engine\": \"schematron\""), run.text());
        assertTrue(run.text().contains("\"category\": \"EN-BR\""), run.text());
        assertTrue(run.text().contains("\"severity\": \"fatal\""), run.text());
        assertTrue(run.text().contains("\"flag\": \"fatal\""), run.text());
        assertTrue(run.text().contains("\"component\": \"en16931-ubl-schematron\""), run.text());
        assertTrue(run.text().contains("\"packId\": \"xrechnung\""), run.text());
        assertTrue(run.text().contains("\"packVersion\": \"3.0.2\""), run.text());
        assertTrue(run.text().contains("\"packRelease\": \"2026-08-31\""), run.text());
        assertTrue(run.text().contains("\"verdict\": \"INVALID\""), run.text());
    }

    /**
     * A profile may level a rule of an artefact differently from the artefact, and the
     * CVD profile levels down the one that objects to its own item classification scheme.
     * Both levels are reported: the one the verdict is made on, and the artefact's own,
     * which is what a comparison against another tool running the same artefact is made
     * on.
     */
    @Test
    void reportsBothLevelsOfARuleTheProfileLevelsItself() {
        Cli.Run run = Cli.run("validate", Fixtures.file(directory, CVD));

        assertEquals(ExitCode.SUCCESS, run.exitCode(),
                "the profile that introduced the scheme accepts the document: " + run.err());
        assertTrue(run.text().contains(
                        "BR-CL-13 [information, flagged fatal by the artefact]"),
                "the line says both, so a reader can see it was not this tool that"
                        + " decided: " + run.text());
        assertTrue(run.text().endsWith("VALID\n"), run.text());
    }

    /**
     * The native engine states the rule of the standard; the verdict states what the
     * profile of the document makes of it.
     *
     * <p>The pack of the native engine is the EN 16931 pack and knows no profile: an
     * identification scheme is one of the published list, and that is what its finding
     * says, at the level the standard gives the rule. What a core invoice usage
     * specification that defined a scheme of its own says about that rule for documents of
     * its profile is a statement about the rule and not about the artefact that raises it,
     * so the verdict applies it to this finding as it does to the artefact's — and the
     * line prints both levels and names the profile, so that nobody has to guess who
     * decided what.
     */
    @Test
    void levelsANativeFindingByTheProfileTheDocumentNames() {
        Cli.Run run = Cli.run("validate", Fixtures.file(directory, CVD));

        assertEquals(ExitCode.SUCCESS, run.exitCode(),
                "the two engines end at the verdict the official validator gives: "
                        + run.err());
        assertTrue(run.text().contains(
                        "BR-CL-13 [information, flagged fatal by the artefact]"),
                "the artefact's finding keeps the level the profile gave it: " + run.text());
        assertTrue(run.text().contains("BR-CL-13 [info, fatal by the standard, levelled by"
                        + " the profile " + CVD_PROFILE + "] /BG-25/*/BG-31/BT-158/*"),
                "and the native finding says all three: " + run.text());
    }

    /**
     * A document that was never XML is levelled by its profile too. The levels are a
     * statement of the specification the document names in BT-24, which an ESJ document
     * names like any other; where no syntax chooses the table of the pack, only what every
     * table of that profile agrees on is applied.
     */
    @Test
    void levelsANativeFindingForADocumentThatWasNeverXml() {
        Cli.Run converted = Cli.run("convert", Fixtures.file(directory, CVD));
        assertEquals(ExitCode.SUCCESS, converted.exitCode(), converted.err());

        Cli.Run run = Cli.run(converted.out(), "validate", "-");

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("BR-CL-13 [info, fatal by the standard, levelled by"
                        + " the profile " + CVD_PROFILE + "]"),
                "the profile of the document decides, not the syntax it arrived in: "
                        + run.text());
    }

    @Test
    void carriesBothLevelsIntoTheJsonReport() {
        Cli.Run run = Cli.run("validate", "--output", "json",
                Fixtures.file(directory, CVD));

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"code\": \"BR-CL-13\""), run.text());
        assertTrue(run.text().contains("\"severity\": \"information\""), run.text());
        assertTrue(run.text().contains("\"flag\": \"fatal\""), run.text());
        assertTrue(run.text().contains("\"engine\": \"native\""), run.text());
        assertTrue(run.text().contains("\"severity\": \"info\""),
                "the native finding carries the level of the profile: " + run.text());
    }

    @Test
    void namesWhatRanAndWhatDidNotInTheJsonReport() {
        Cli.Run run = Cli.run("validate", "--output", "json", Fixtures.file(directory, UBL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"directory\": \"" + PACK + "\""), run.text());
        assertTrue(run.text().contains("\"component\": \"ubl-2.1-xsd\""), run.text());
        assertTrue(run.text().contains("\"engine\": \"xsd\""), run.text());
        assertTrue(run.text().contains("\"component\": \"cii-d16b-xsd\""),
                "the machine report lists every component the pack did not use: " + run.text());
        assertTrue(run.text().contains("\"reason\": \"it validates no UBL 2.1 Invoice\""),
                run.text());
    }

    @Test
    void saysThatAnEsjDocumentHasNoBindingToCheck() {
        Cli.Run run = Cli.run(Fixtures.bytes(ESJ), "validate", "-", "--output", "json");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"checked\": false"), run.text());
        assertTrue(run.text().contains("\"reason\": \"" + SyntaxCheck.NO_XML + "\""), run.text());
        assertTrue(run.text().contains("\"pack\": null"), run.text());
    }

    @Test
    void runsABundledPackTheCallerNamesByItsIdentity() {
        Cli.Run run = Cli.run("validate", "--pack", PACK, Fixtures.file(directory, UBL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("(pack " + PACK + ")"), run.text());
    }

    @Test
    void runsAPackFromADirectoryTheCallerPointsAt() {
        Cli.Run run = Cli.run("validate", "--pack", unpacked().toString(),
                Fixtures.file(directory, UBL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("EN 16931 UBL Schematron:   OK"), run.text());
    }

    @Test
    void refusesAPackItCannotFind() {
        Cli.Run run = Cli.run("validate", "--pack", "xrechnung/9.9.9/1970-01-01",
                Fixtures.file(directory, UBL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--pack xrechnung/9.9.9/1970-01-01"), run.err());
    }

    /**
     * A document whose profile brought no rules with it reaches no verdict, and both forms
     * of the report say so.
     *
     * <p>The text form has said it all along — the rows name what was skipped and why, the
     * note under them says "schema validation only", and the business-rules line says that
     * no official Schematron ran — but a pipeline reads none of those, and neither the
     * exit code nor the {@code verdict} member used to carry it. A {@code VALID} after one
     * component of six was byte for byte the {@code VALID} of a document that was checked
     * against everything, and {@code esj validate x.xml && deploy} could not tell them
     * apart. It is the one class of document the oracle of {@code docs/validation.md} and
     * the official validator answer differently: that one refuses it for want of a
     * scenario, this one checks what applies and says what it could not reach.
     */
    @Test
    void namesTheRuleSetsThatWereLeftOutForTheProfile() throws IOException {
        Path unknown = directory.resolve("unknown-profile.xml");
        Files.write(unknown, new String(Fixtures.bytes(UBL), StandardCharsets.UTF_8)
                .replaceAll("<cbc:CustomizationID>[^<]*</cbc:CustomizationID>",
                        "<cbc:CustomizationID>urn:example.org:own:1</cbc:CustomizationID>")
                .getBytes(StandardCharsets.UTF_8));

        Cli.Run run = Cli.run("validate", "--output", "json", unknown.toString());

        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"verdict\": \"INDETERMINATE\""),
                "a document that asked to be judged by rules nobody here holds was not"
                        + " judged: " + run.text());
        assertTrue(run.text().contains("\"cause\": \"no-rules-for-profile\""), run.text());
        assertTrue(run.text().contains("\"" + Validation.NOT_CHECKED_PROFILE_RULES + "\""),
                "notChecked names the rule sets the profile left out: " + run.text());
        assertTrue(run.text().contains("\"profileRulesSkipped\": true"),
                "and the syntax object carries the same fact beside the note: "
                        + run.text());
        assertFalse(Cli.run("validate", "--output", "json", Fixtures.file(directory, UBL))
                        .text().contains("\"" + Validation.NOT_CHECKED_PROFILE_RULES + "\""),
                "a document of a profile the pack knows has nothing to report here");
    }

    @Test
    void refusesADirectoryThatHoldsNoPack() {
        Cli.Run run = Cli.run("validate", "--pack", directory.toString(),
                Fixtures.file(directory, UBL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("pack.json"), run.err());
    }

    @Test
    void leavesWithoutAVerdictWhenTheTimeItWasGivenRanOut() {
        Cli.Run run = Cli.run("validate", "--max-runtime", "1ms",
                Fixtures.file(directory, UBL));
        assertEquals(ExitCode.LIMIT, run.exitCode(),
                "a run that was stopped has found nothing, not nothing wrong: " + run.err());
        assertTrue(run.err().contains("no verdict"), run.err());
        assertTrue(run.err().contains("--max-runtime"), run.err());
        assertEquals(0, run.out().length, "a run without a verdict writes no report");
    }

    /**
     * The bound covers the whole command, not the official artefacts alone.
     *
     * <p>A bound that covered the official artefacts alone would be no bound at all on the
     * documents it is set for: reading an awkward document into the semantic model is the
     * expensive half of those runs, it happens first, and a caller who set a number
     * because the invoice came from a stranger is entitled to have it mean something.
     *
     * <p>The reader the run is stopped in is the XSLT path, because that is the expensive
     * one: the default reader reads an invoice of this size in well under a millisecond,
     * which is why it is the default, and it is the wrong instrument for this assertion
     * rather than a counter-example to it.
     *
     * <p>Which step a run stops in is what is asserted here, so the budget is chosen
     * against the steps rather than made small enough to expire anywhere: the document is
     * {@link #LINES} copies of one invoice line, whose import through that path takes
     * seconds, while resolving the pack and reading the file takes single milliseconds.
     * Measured from a cold process: the import of this document runs for about four
     * seconds, and a budget of five milliseconds is already spent inside it rather than
     * before it, so {@value #BUDGET_MILLIS} ms sits more than an order of magnitude from
     * either side. A machine fast enough to import this document inside the budget fails
     * this test rather than flickering: the run then leaves with 0.
     */
    @Test
    void countsTheImportAgainstTheTimeItWasGiven() throws IOException {
        // One run over the small invoice first, so that the budget below is spent on the
        // work of the import and not on loading the classes of the steps before it.
        Cli.run("--importer", "xslt", "validate", "--no-syntax", Fixtures.file(directory, UBL));
        String many = manyLines();

        Cli.Run run = Cli.run("--importer", "xslt", "validate", "--max-input-bytes", ROOM,
                "--max-runtime", BUDGET_MILLIS + "ms", many);

        assertEquals(ExitCode.LIMIT, run.exitCode(),
                "the import outlasts a budget it is measured against: " + run.err());
        assertTrue(run.err().contains("reading the document into the semantic model"),
                "the message names the half the time ran out in: " + run.err());
        assertEquals(0, run.out().length, "a run without a verdict writes no report");
        assertEquals(ExitCode.LIMIT,
                Cli.run("--importer", "xslt", "validate", "--no-syntax", "--max-input-bytes",
                        ROOM, "--max-runtime", BUDGET_MILLIS + "ms", many).exitCode(),
                "and it is the same half with --no-syntax, which leaves the other one out");
    }

    /**
     * Writes an invoice of {@link #LINES} copies of one line and returns its path.
     *
     * <p>Repeating the line is what makes the import expensive without making the file
     * large. Its size is nobody's bound here: the runs read it within {@value #ROOM},
     * several times what it needs, so what they reach is the import and not a refusal for
     * the size of an input this test says nothing about.
     */
    private String manyLines() throws IOException {
        String xml = Fixtures.text(UBL);
        int start = xml.indexOf("<cac:InvoiceLine>");
        int end = xml.lastIndexOf("</cac:InvoiceLine>") + "</cac:InvoiceLine>".length();
        String line = xml.substring(start, end);
        Path file = directory.resolve("many-lines.xml");
        Files.write(file, (xml.substring(0, start) + line.repeat(LINES)
                + xml.substring(end)).getBytes(StandardCharsets.UTF_8));
        return file.toString();
    }

    @Test
    void refusesATimeBoundItCannotRead() {
        Cli.Run run = Cli.run("validate", "--max-runtime", "soon",
                Fixtures.file(directory, UBL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--max-runtime"), run.err());
        assertTrue(run.err().contains("takes a duration"), run.err());
    }

    @Test
    void refusesATimeBoundOfNoTime() {
        Cli.Run run = Cli.run("validate", "--max-runtime", "0", Fixtures.file(directory, UBL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("positive duration"), run.err());
    }

    @Test
    void readsATimeBoundInEveryUnitItOffers() {
        for (String bound : new String[] {"120000ms", "120s", "2m", "120"}) {
            Cli.Run run = Cli.run("validate", "--max-runtime", bound,
                    Fixtures.file(directory, UBL));
            assertEquals(ExitCode.SUCCESS, run.exitCode(), bound + ": " + run.err());
        }
    }

    @Test
    void listsTheBundledPacksWithTheirComponentsAndLicences() {
        Cli.Run run = Cli.run("--list-packs");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains(PACK), run.text());
        assertTrue(run.text().contains("en16931-ubl-schematron (schematron-xslt, EUPL-1.2)"),
                run.text());
        assertTrue(run.text().contains("xrechnung-cii-schematron (schematron-xslt,"
                + " Apache-2.0)"), run.text());
        assertTrue(run.text().contains("packs/SOURCES.md"),
                "the listing says where the provenance is recorded: " + run.text());
    }

    @Test
    void namesThePackThatWouldApplyWhenItSummarizesAnXmlInvoice() {
        Cli.Run run = Cli.run("inspect", Fixtures.file(directory, UBL));
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("Validation pack:            " + PACK), run.text());
        assertTrue(run.text().contains(SyntaxCheck.NOT_INSPECTED),
                "a summary says that it named the artefacts rather than ran them: "
                        + run.text());
    }

    /**
     * The bound covers the read as well, because a bounded number of bytes is not a
     * bounded wait.
     *
     * <p>A pipe whose writer stops writing without closing the descriptor hands the reader
     * a wait with no end in it, and the bytes it did write are well under every size bound
     * the tool has. Before the clock covered the read, the only thing that ended such a run
     * was the caller killing it; the page promises one number over the whole command, and
     * the read is where a command that came from a stranger begins.
     */
    @Test
    void countsTheReadAgainstTheTimeItWasGiven() {
        Cli.Run run = Cli.run(silentAfter("<?xml version=\"1.0\"?><Invoice xmlns=\"urn:oasis"
                        + ":names:specification:ubl:schema:xsd:Invoice-2\"><cbc:ID>"),
                "validate", "-", "--max-runtime", "300ms");
        assertEquals(ExitCode.LIMIT, run.exitCode(),
                "a read nobody ended is a document without a verdict: " + run.err());
        assertTrue(run.err().contains("reading the input"),
                "the message names the step the time ran out in: " + run.err());
        assertEquals(0, run.out().length, "a run without a verdict writes no report");
    }

    /**
     * Where the artefacts run out of time, the message names the number the caller set as
     * well as the number the artefacts had.
     *
     * <p>They are not the same number and cannot be: the artefacts are given what the read
     * and the import left of the command's time. An operator who set twelve seconds and is
     * told about the two hundred milliseconds that were left of them reads the sentence as
     * a tool ignoring the option, which is the one conclusion the message must not invite.
     */
    @Test
    void namesTheTimeTheCallerSetBesideTheTimeTheArtefactsHad() {
        SyntaxLimitException outOfTime = assertThrows(SyntaxLimitException.class,
                () -> SyntaxValidator.validate(Fixtures.bytes(UBL),
                        SyntaxOptions.defaults().withMaxRuntime(Duration.ofMillis(1))));

        assertTrue(outOfTime.budget().isPresent(),
                "a time limit carries the budget it was given: " + outOfTime.getMessage());
        String note = SyntaxCheck.remainderNote(outOfTime, Deadline.of(Duration.ofSeconds(12)));
        assertTrue(note.contains("12000 ms"),
                "the number the caller set is in the message: " + note);
        assertTrue(outOfTime.getMessage().contains("1 ms"),
                "and the number the artefacts had is too: " + outOfTime.getMessage());
    }

    /**
     * A rule set that stopped is reported as one, and the engine beside it still answers.
     *
     * <p>The rule sets of a profile run together, and the one that carries the business
     * rules is the one a value no arithmetic can carry stops. Its failure is a finding of
     * its own rather than a silence, and it is not a reason for the native rule engine to
     * say nothing: that engine read the same invoice through the importer, it is not the
     * artefact, and the two are reported apart precisely so that one of them failing does
     * not take the other's answer with it.
     */
    @Test
    void reportsARuleSetThatStoppedAndStillAnswersWithTheNativeEngine() {
        String overflowing = Fixtures.write(directory, "overflow.xml",
                new String(Fixtures.bytes(UBL), StandardCharsets.UTF_8)
                        .replace(">314.86<", ">1" + "0".repeat(320) + "<")
                        .getBytes(StandardCharsets.UTF_8));

        Cli.Run run = Cli.run("validate", overflowing);

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains("ARTEFACT-STOPPED"), run.text());
        assertTrue(run.text().contains(RuleCheck.LABEL + ":"),
                "the native engine read the document the importer built: " + run.text());
        assertFalse(run.text().contains(RuleCheck.LABEL + ": not run"),
                "a stopped artefact is not a reason for the native engine to be silent: "
                        + run.text());
        assertTrue(Cli.run("validate", "--output", "json", overflowing).text()
                        .contains("\"stopped\": true"),
                "and a program is told the same thing");
    }

    /**
     * A verdict reached with a supplied pack is marked as one, in both forms of the report.
     *
     * <p>A pack directory writes its own manifest, so it can call itself the release this
     * build carries. The caller who pointed at it knows what is in it; the report is read
     * by people who did not run it, and a report that named only the identity would
     * attribute its findings to the vendored release whatever it ran.
     */
    @Test
    void saysWhereTheArtefactsCameFromWhereTheCallerSuppliedThem() {
        String invoice = Fixtures.file(directory, UBL);
        Cli.Run supplied = Cli.run("validate", "--pack", unpacked().toString(), invoice);
        assertEquals(ExitCode.SUCCESS, supplied.exitCode(), supplied.err());
        assertTrue(supplied.text().contains("(pack " + PACK + ", supplied with --pack)"),
                supplied.text());
        assertTrue(Cli.run("validate", "--output", "json", "--pack", unpacked().toString(),
                        invoice).text().contains("\"source\": \"supplied\""),
                "and a program is told the same thing");

        Cli.Run bundled = Cli.run("validate", "--output", "json", invoice);
        assertTrue(bundled.text().contains("\"source\": \"bundled\""), bundled.text());
        assertFalse(Cli.run("validate", invoice).text().contains("supplied"),
                "the pack of this build is named without a qualification");
    }

    /**
     * Returns a standard input that produces one prefix and then stops producing without
     * ending, which is what a pipe whose writer has gone quiet looks like to a reader.
     */
    private static InputStream silentAfter(String prefix) {
        byte[] bytes = prefix.getBytes(StandardCharsets.UTF_8);
        return new InputStream() {

            private int position;

            @Override
            public int read() throws IOException {
                if (position < bytes.length) {
                    return bytes[position++] & 0xff;
                }
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("the reader was abandoned", e);
                }
                return -1;
            }
        };
    }

    /** Writes the corpus invoice with one total one cent out, and returns its path. */
    private String mutated() {
        String source = new String(Fixtures.bytes(UBL), StandardCharsets.UTF_8)
                .replace("<cbc:PayableAmount currencyID=\"EUR\">336.9<",
                        "<cbc:PayableAmount currencyID=\"EUR\">336.91<");
        assertTrue(source.contains("336.91"), "the fixture still carries the amount to break");
        return Fixtures.write(directory, "mutated.xml",
                source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes the bundled pack out as a directory and returns it, which is what a caller
     * who points {@code --pack} at a newer release has on disk.
     */
    private Path unpacked() {
        Pack bundled = Packs.bundled(PACK);
        Path root = directory.resolve("pack");
        try {
            for (String file : bundled.files().keySet()) {
                Path target = root.resolve(file);
                Files.createDirectories(target.getParent());
                Files.write(target, bundled.read(file));
            }
            Files.write(root.resolve("pack.json"), manifest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return root;
    }

    /**
     * Every byte bound of the run reaches the official artefacts, and not only the front
     * door of the command.
     *
     * <p>The engine has a bound on its input of its own, and it used to be left at the
     * four mebibytes of its defaults while the front door was raised: an XML of five
     * megabytes then passed the door under {@code --limits large} and was refused by the
     * engine, so the profile the caller asked for produced exit 7 and a hint naming the
     * clock, over a document the run was configured to read.
     *
     * <p>The instance is a corpus invoice with four and a half megabytes of invoice notes
     * in front of it: notes are 0..n in this binding and carry no arithmetic, so the
     * document stays the sound invoice it was and the size is the only thing about it that
     * changed. Under the profile it reaches a verdict with every artefact of its profile
     * run over it; without the profile the refusal is the front door's and names the
     * switch that raises the bound on bytes, not the one that raises the clock.
     */
    @Test
    void everyByteBoundOfTheRunReachesTheOfficialArtefacts() {
        String file = Fixtures.write(directory, "many-notes.xml", withNotes(4_500_000));

        Cli.Run raised = Cli.run("validate", "--limits", "large", file);
        Cli.Run refused = Cli.run("validate", file);

        assertEquals(ExitCode.SUCCESS, raised.exitCode(), raised.err() + raised.text());
        assertTrue(raised.text().contains("EN 16931 UBL Schematron:   OK"),
                "the artefacts ran over all of it: " + raised.text());
        assertEquals(ExitCode.LIMIT, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("--max-input-bytes raises that bound"),
                "the refusal names the bound it met: " + refused.err());
        assertFalse(refused.err().contains("--max-runtime"),
                "and not the clock, which is a different bound: " + refused.err());
    }

    /**
     * Returns a corpus invoice grown past the default bound on an XML input by repeating
     * the invoice note it carries, which this binding admits any number of.
     *
     * @param size how large the instance is to be, in bytes
     * @return the instance, UTF-8
     */
    private static byte[] withNotes(int size) {
        String source = new String(Fixtures.bytes(UBL), StandardCharsets.UTF_8);
        int at = source.indexOf("<cbc:Note>");
        assertTrue(at > 0, "the corpus instance carries an invoice note");
        String note = "<cbc:Note>" + "filler ".repeat(128).trim() + "</cbc:Note>";
        StringBuilder grown = new StringBuilder(size + source.length());
        grown.append(source, 0, at);
        while (grown.length() < size) {
            grown.append(note);
        }
        return grown.append(source, at, source.length()).toString()
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The manifest of the bundled pack, read from the class path it is packaged on. It is
     * the one file of a pack the pack's own inventory does not list, because the inventory
     * is inside it.
     */
    private static byte[] manifest() throws IOException {
        String resource = "/" + Packs.ROOT + "/" + PACK + "/pack.json";
        try (InputStream in = SyntaxValidationTest.class.getResourceAsStream(resource)) {
            assertTrue(in != null, "the manifest of " + PACK + " is on the class path");
            return in.readAllBytes();
        }
    }
}
