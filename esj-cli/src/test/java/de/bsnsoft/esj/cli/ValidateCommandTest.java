package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.bsnsoft.esj.syntax.SyntaxLimitException;
import de.bsnsoft.esj.syntax.SyntaxOptions;
import de.bsnsoft.esj.syntax.SyntaxValidator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * {@code esj validate}: the structural layers, the exit code, and the sentences about
 * what was not checked.
 *
 * <p>The negative fixtures of {@code examples/invalid/} carry one defect each and record
 * the finding code that catches it, so they make a table of what the command has to
 * report and with which code. What the tool says about the checks it did not run is
 * asserted as carefully as what it finds: the row of the native rule engine has to be
 * there every time, naming the pack, with either what it found or the reason it found
 * nothing.
 *
 * <p>What the syntax engine does over an XML input is asserted in
 * {@link SyntaxValidationTest}; this class is about the semantic half and about the shape
 * of the report.
 */
class ValidateCommandTest {

    private static final String MINIMAL = "examples/minimal.esj.json";
    private static final String STANDARD = "examples/standard-invoice.esj.json";
    private static final String UBL = "conformance/kosit/business-cases/standard/"
            + "01.01a-INVOICE_ubl.xml";
    private static final String CII = "conformance/kosit/business-cases/standard/"
            + "02.05a-INVOICE_uncefact.xml";
    private static final String EXTENSION = "conformance/kosit/business-cases/extension/"
            + "04.01a-INVOICE_ubl.xml";

    @TempDir
    private Path directory;

    /**
     * A report of a very large invoice is a report, not the invoice again.
     *
     * <p>Nothing in a document bounds how many findings it produces: every invoice line can
     * break the same rule, and a hundred thousand lines then break it a hundred thousand
     * times. The count on the row is always the true one; the list below it stops after
     * twenty and says how many it left out, and {@code --verbose} prints them all.
     */
    @Test
    void cutsTheListOfFindingsAndSaysHowManyItLeftOut() {
        String file = Fixtures.write(directory, "many.esj.json", manyNegativePrices(25));

        Cli.Run cut = Cli.run("validate", "--no-syntax", file);
        Cli.Run whole = Cli.run("validate", "--no-syntax", "--verbose", file);

        assertEquals(ExitCode.VALIDATION, cut.exitCode(), cut.err());
        int all = count(whole.text(), " [fatal]");
        assertEquals(25, count(whole.text(), "BR-27 ["), whole.text());
        assertTrue(cut.text().contains(all + " errors"),
                "the count on the row is the whole count: " + cut.text());
        assertEquals(20, count(cut.text(), " [fatal]"), cut.text());
        assertTrue(cut.text().contains("... and " + (all - 20)
                        + " more; run with --verbose for all of them"), cut.text());
        assertFalse(whole.text().contains("more; run with --verbose"), whole.text());
    }

    /** Returns how many times a text carries a fragment. */
    private static int count(String text, String fragment) {
        int found = 0;
        for (int at = text.indexOf(fragment); at >= 0;
                at = text.indexOf(fragment, at + fragment.length())) {
            found++;
        }
        return found;
    }

    /**
     * Returns an ESJ document whose every invoice line states a negative item net price,
     * which is one finding of {@code BR-27} per line and nothing else about the line.
     *
     * @param lines how many invoice lines
     * @return the document, UTF-8
     */
    private static byte[] manyNegativePrices(int lines) {
        StringBuilder json = new StringBuilder()
                .append("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\",")
                .append("\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{")
                .append("\"/BT-1\":\"RE-2026-0001\",\"/BT-2\":\"2026-01-15\",")
                .append("\"/BT-3\":\"380\",\"/BT-5\":\"EUR\",")
                .append("\"/BG-2/BT-24\":\"urn:cen.eu:en16931:2017\",")
                .append("\"/BG-4/BT-27\":\"Example GmbH\",\"/BG-4/BG-5/BT-40\":\"DE\",")
                .append("\"/BG-7/BT-44\":\"Muster AG\",\"/BG-7/BG-8/BT-55\":\"DE\",")
                .append("\"/BG-22/BT-106\":\"").append(lines * 100).append("\",")
                .append("\"/BG-22/BT-109\":\"").append(lines * 100).append("\",")
                .append("\"/BG-22/BT-110\":\"0\",")
                .append("\"/BG-22/BT-112\":\"").append(lines * 100).append("\",")
                .append("\"/BG-22/BT-115\":\"").append(lines * 100).append("\",")
                .append("\"/BG-23/0/BT-116\":\"").append(lines * 100).append("\",")
                .append("\"/BG-23/0/BT-117\":\"0\",\"/BG-23/0/BT-118\":\"Z\",")
                .append("\"/BG-23/0/BT-119\":\"0\"");
        for (int line = 0; line < lines; line++) {
            String at = "/BG-25/" + line;
            json.append(",\"").append(at).append("/BT-126\":\"").append(line + 1).append('"')
                    .append(",\"").append(at).append("/BT-129\":\"1\"")
                    .append(",\"").append(at).append("/BT-130\":\"C62\"")
                    .append(",\"").append(at).append("/BT-131\":\"100\"")
                    .append(",\"").append(at).append("/BG-29/BT-146\":\"-1\"")
                    .append(",\"").append(at).append("/BG-30/BT-151\":\"Z\"")
                    .append(",\"").append(at).append("/BG-30/BT-152\":\"0\"")
                    .append(",\"").append(at).append("/BG-31/BT-153\":\"Service ")
                    .append(line + 1).append('"');
        }
        return json.append("}}").toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void acceptsAnExampleAndNamesEveryComponentOfTheCheck() {
        Cli.Run run = Cli.run("validate", Fixtures.file(directory, STANDARD));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("ESJ format (L1):           OK"), run.text());
        assertTrue(run.text().contains("Model (L2):                OK"), run.text());
        assertTrue(run.text().contains("Cardinality (L3):          OK"), run.text());
        assertTrue(run.text().contains(RuleCheck.LABEL + ": OK"), run.text());
        assertTrue(run.text().contains(SyntaxCheck.NO_XML),
                "an ESJ input has no XML to run the official artefacts over: " + run.text());
        assertTrue(run.text().endsWith("VALID\n"),
                "the complete check for an ESJ input is the layers and the business rules,"
                        + " and both ran: " + run.text());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "object-without-component.esj.json, ESJ-L1-VALUE-SHAPE",
        "unknown-object-member.esj.json, ESJ-L1-VALUE-MEMBER",
        "duplicate-member.esj.json,    ESJ-L1-DUPLICATE-MEMBER",
        "decimal-trailing-zeros.esj.json, ESJ-L2-DECIMAL",
        "calendar-impossible-date.esj.json, ESJ-L2-DATE",
        "index-on-bt-1.esj.json,       ESJ-L2-INDEX-FORBIDDEN",
        "unknown-term.esj.json,        ESJ-L2-UNKNOWN-TERM",
        "scheme-on-text-value.esj.json, ESJ-L2-COMPONENT-NOT-ALLOWED",
        "index-gap.esj.json,           ESJ-L3-INDEX-GAP",
        "missing-mandatory-term.esj.json, ESJ-L3-MISSING-TERM"})
    void reportsTheFindingTheNegativeFixtureWasWrittenFor(String fixture, String code) {
        Cli.Run run = Cli.run("validate",
                Fixtures.file(directory, "examples/invalid/" + fixture));
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains(code), run.text());
    }

    /**
     * The arithmetic of an ESJ document that was never XML is checked, which nothing
     * checked before.
     *
     * <p>This is the fixture the README used to name as the thing the tool could not
     * catch: every structural layer passes it, no official artefact will ever look at it
     * because there is no XML to look at, and the sum it breaks is a rule of the standard.
     * The native engine is the answer to exactly that case, so the verdict is now
     * {@code INVALID} and the rule that decided it is named.
     */
    @Test
    void checksTheArithmeticOfADocumentNoArtefactWillEverSee() {
        Cli.Run run = Cli.run("validate",
                Fixtures.file(directory, "examples/invalid/arithmetic-mismatch.esj.json"));
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains("ESJ format (L1):           OK"), run.text());
        assertTrue(run.text().contains("Cardinality (L3):          OK"), run.text());
        assertTrue(run.text().contains("BR-CO-16"),
                "the amount due for payment does not follow from the totals: " + run.text());
        assertTrue(run.text().contains(SyntaxCheck.NO_XML), run.text());
        assertTrue(run.text().endsWith("INVALID\n"), run.text());
    }

    /**
     * {@code --rules none} leaves them out, and the row says so rather than reading OK.
     *
     * <p>{@code --no-syntax} is beside it because the two rows are independent and both
     * are required for an ESJ input: without it the official artefacts would run over the
     * document written out as a cross industry invoice, find the same broken sum and make
     * the run {@code INVALID}, which is the right answer to a different question than the
     * one this test asks.
     */
    @Test
    void leavesTheBusinessRulesOutWhenAskedTo() {
        Cli.Run run = Cli.run("validate", "--rules", "none", "--no-syntax",
                Fixtures.file(directory, "examples/invalid/arithmetic-mismatch.esj.json"));
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(),
                "the rules are a required component of the check for an ESJ input, so a"
                        + " caller that leaves them out reaches no verdict: " + run.err());
        assertTrue(run.text().contains(RuleCheck.LABEL + ": " + RuleCheck.BY_OPTION),
                run.text());
        assertTrue(run.text().endsWith("INDETERMINATE — nothing fatal found;"
                + " missing from the check: written-syntax (skipped-by-caller),"
                + " business-rules (skipped-by-caller)\n"),
                run.text());
        assertFalse(run.text().contains("BR-CO-10"), run.text());

        Cli.Run json = Cli.run("validate", "--rules", "none", "--no-syntax",
                "--output", "json",
                Fixtures.file(directory, "examples/invalid/arithmetic-mismatch.esj.json"));
        assertTrue(json.text().contains("\"" + Validation.NOT_CHECKED_BUSINESS_RULES + "\""),
                "a run that left them out lists them as unchecked: " + json.text());
        assertTrue(json.text().contains("\"checked\": false"), json.text());
    }

    /** The default is to run them, and then nothing is listed as unchecked for them. */
    @Test
    void runsTheBusinessRulesWithoutBeingAskedTo() {
        Cli.Run run = Cli.run("validate", "--output", "json",
                Fixtures.file(directory, "examples/standard-invoice.esj.json"));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(run.text().contains("\"" + Validation.NOT_CHECKED_BUSINESS_RULES + "\""),
                "the rules were checked, so nothing says they were not: " + run.text());
        assertTrue(run.text().contains("\"id\": \"en16931\""), run.text());
        assertTrue(run.text().contains("\"version\": \"1.3.16\""), run.text());
    }

    /** A rule pack this version does not carry is refused in the words of the option. */
    @Test
    void refusesARulePackItDoesNotKnow() {
        Cli.Run run = Cli.run("validate", "--rules", "xrechnung",
                Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--rules takes none or en16931"), run.err());
    }

    /**
     * A document a structural layer rejected is not handed to the rules.
     *
     * <p>A term the registry does not know is a term a rule cannot read, and a rule that
     * reported an arithmetic failure over it would be naming the second of two defects
     * whose cause is the first. The row says the rules did not run and why.
     */
    @Test
    void doesNotRunTheRulesOverADocumentTheModelLayerRejected() {
        Cli.Run run = Cli.run(Fixtures.bytes("examples/invalid/unknown-term.esj.json"),
                "validate", "-");
        assertEquals(ExitCode.VALIDATION, run.exitCode());
        assertTrue(run.text().contains(RuleCheck.LABEL + ": " + RuleCheck.AFTER_MODEL),
                run.text());
    }

    /**
     * The same rule reported by both engines is printed twice and named as such.
     *
     * <p>The two are independent evidence about two forms of one invoice — the artefact
     * reads the XML, the native pack reads what the import carried into the model — and a
     * report that merged them would be choosing which witness to believe.
     */
    @Test
    void printsARuleBothEnginesReportedTwiceAndSaysSo() {
        byte[] mutated = new String(Fixtures.bytes(UBL), java.nio.charset.StandardCharsets.UTF_8)
                .replace("<cbc:LineExtensionAmount currencyID=\"EUR\">314.86"
                                + "</cbc:LineExtensionAmount>",
                        "<cbc:LineExtensionAmount currencyID=\"EUR\">315.86"
                                + "</cbc:LineExtensionAmount>")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String file = Fixtures.write(directory, "mutated.xml", mutated);

        Cli.Run run = Cli.run("validate", file);

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains("[BR-CO-10]"),
                "the artefact reported it in its own words: " + run.text());
        assertTrue(run.text().contains("BR-CO-10 [fatal] /BG-22/BT-106"),
                "and the native rule in ours: " + run.text());
        assertTrue(run.text().contains(Reports.ALSO_REPORTED), run.text());
        assertTrue(run.text().contains(Reports.ALSO_REPORTED + "BR-CO-10"), run.text());

        Cli.Run json = Cli.run("validate", "--output", "json", file);
        assertTrue(json.text().contains("\"engine\": \"native\""), json.text());
        assertTrue(json.text().contains("\"engine\": \"schematron\""), json.text());
    }

    @Test
    void stopsAfterTheModelLayerWhenAskedFor() {
        Cli.Run run = Cli.run("validate", "--level", "l2", "--rules", "none",
                "--no-syntax",
                Fixtures.file(directory, "examples/invalid/index-gap.esj.json"));
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("Cardinality (L3):          not checked"), run.text());
        assertTrue(run.text().contains("cardinality-l3 (skipped-by-caller)"),
                "a caller that asked for less gets the third state and the reason: "
                        + run.text());
    }

    @Test
    void refusesASyntaxItCannotWriteTheDocumentTo() {
        Cli.Run run = Cli.run("validate", "--via", "xml", Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--via takes cii or ubl"), run.err());
    }

    @Test
    void refusesALevelItDoesNotKnow() {
        Cli.Run run = Cli.run("validate", "--level", "l4", Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--level takes l2 or l3"), run.err());
    }

    @Test
    void doesNotRunTheFormatLayerOverADocumentItBuiltRatherThanRead() {
        Cli.Run run = Cli.run("validate", Fixtures.file(directory, UBL));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("ESJ format (L1):           not checked"), run.text());
        assertTrue(run.text().contains(RuleCheck.LABEL + ": OK"),
                "the native rules ran over the imported document too: " + run.text());
    }

    /**
     * An identifier that arrived without the scheme its term requires is named twice, by
     * both engines, under both of their names for it.
     *
     * <p>This is the whole of why the importer keeps such a value instead of dropping it.
     * The model layer reports {@code ESJ-L2-COMPONENT-MISSING} at the path, and the rule
     * pack reports {@code BR-62}, which is the identifier the official validation artefacts
     * use for the same defect — so the report of this tool can be put beside theirs. A model
     * error normally stops the rule pack; a missing supplementary component is the one that
     * does not, because the value is there and the rule is a statement about its absence.
     *
     * <p>The reader is named because only one of the two keeps the value today: the
     * stylesheet path does, the streaming reader of the binding tables drops it and reports
     * the component as missing, which {@code conformance/readers.md} records as a difference
     * between them.
     */
    @Test
    void namesAMissingIdentificationSchemeUnderBothEnginesNames() {
        String source = Fixtures.text(UBL)
                .replace("<cbc:EndpointID schemeID=\"EM\">", "<cbc:EndpointID>");
        String file = Fixtures.write(directory, "no-scheme.xml",
                source.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Cli.Run run = Cli.run("validate", "--no-syntax", "--importer", "xslt", file);

        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains("ESJ-L2-COMPONENT-MISSING"),
                "the structural layer names the component: " + run.text());
        assertTrue(run.text().contains("BR-62"),
                "and the rule pack names the rule the artefacts name: " + run.text());
    }

    @Test
    void saysTheSyntaxBindingWasNotCheckedWhereItWasAskedToLeaveItOut() {
        Cli.Run run = Cli.run("validate", "--no-syntax", Fixtures.file(directory, UBL));
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains(SyntaxCheck.BY_OPTION), run.text());
        assertTrue(run.text().contains(RuleCheck.LABEL + ": OK"),
                "the native rules are the semantic half and run without the artefacts: "
                        + run.text());
        assertTrue(run.text().contains("INDETERMINATE"),
                "the official artefacts are what a verdict on an XML input needs: "
                        + run.text());
    }

    /**
     * A run that left the official artefacts out reaches no verdict, in the same words and
     * with the same exit code in both forms of the report.
     *
     * <p>The rows above are honest either way, but the last line and the exit code are what
     * a reader and a pipeline take away, and {@code VALID} from a run that skipped a
     * component of the check is a claim it has not earned: the same document can be
     * rejected by a run without the switch. The earlier wording {@code STRUCTURALLY VALID}
     * is gone, because a second vocabulary for the third state is a second thing to get
     * wrong; the state has a name, and the components that did not run are named beside it.
     */
    @Test
    void reachesNoVerdictWhereItWasAskedToLeaveTheArtefactsOut() {
        Cli.Run run = Cli.run("validate", "--no-syntax", Fixtures.file(directory, UBL));
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().endsWith("INDETERMINATE — nothing fatal found;"
                + " missing from the check:"
                        + " syntax-binding (skipped-by-caller)\n"), run.text());
        assertFalse(run.text().contains("STRUCTURALLY"),
                "the qualified wording is gone: " + run.text());

        Cli.Run json = Cli.run("validate", "--no-syntax", "--output", "json",
                Fixtures.file(directory, UBL));
        assertEquals(ExitCode.INDETERMINATE, json.exitCode(), json.err());
        assertTrue(json.text().contains("\"verdict\": \"INDETERMINATE\""), json.text());
        assertTrue(json.text().contains("\"cause\": \"skipped-by-caller\""),
                "the cause is a token from a closed vocabulary: " + json.text());

        Cli.Run esj = Cli.run(Fixtures.bytes(STANDARD), "validate", "-");
        assertEquals(ExitCode.SUCCESS, esj.exitCode(), esj.err());
        assertFalse(esj.text().contains("STRUCTURALLY"),
                "an ESJ input has no binding for an artefact to check, so the layers and"
                        + " the business rules are the whole of the check and the word is"
                        + " earned: " + esj.text());
    }

    /**
     * An ESJ document reaches {@code VALID}, because the official artefacts read it too.
     *
     * <p>It is the gap this phase closed. No artefact will look at a document that was
     * never XML, so the document is written out through a binding table in memory and the
     * artefacts of its profile run over the result. The row is in the report under its own
     * name, and with it the complete check for an ESJ input can actually run.
     */
    @Test
    void runsTheOfficialArtefactsOverTheDocumentWrittenAsCii() {
        Cli.Run run = Cli.run("validate", Fixtures.file(directory, STANDARD));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("official artefacts over the written CII:"),
                run.text());
        assertTrue(run.text().contains("CII D16B XSD:              OK"), run.text());
        assertTrue(run.text().contains("EN 16931 CII Schematron:   OK"), run.text());
        assertTrue(run.text().endsWith("VALID\n"), run.text());

        Cli.Run ubl = Cli.run("validate", "--via", "ubl", Fixtures.file(directory, STANDARD));
        assertEquals(ExitCode.SUCCESS, ubl.exitCode(), ubl.err());
        assertTrue(ubl.text().contains("official artefacts over the written UBL invoice:"),
                ubl.text());

        Cli.Run json = Cli.run("validate", "--output", "json",
                Fixtures.file(directory, STANDARD));
        assertTrue(json.text().contains("\"target\": \"CII\""), json.text());
        assertFalse(json.text().contains("\"" + Validation.NOT_CHECKED_WRITTEN_SYNTAX + "\""),
                "a run the artefacts reached does not list the row as unchecked: "
                        + json.text());
    }

    /**
     * A rule of the standard an ESJ document breaks is reported by the artefacts as well.
     *
     * <p>{@code examples/minimal.esj.json} states no VAT category rate and nothing to
     * identify the seller by, which the native pack and the EN 16931 Schematron both
     * fault. The verdict is {@code INVALID} either way; what this test holds is that the
     * artefact reached the document at all and reported under its own rule identifier.
     */
    @Test
    void reportsWhatAnArtefactFindsInTheWrittenDocument() {
        Cli.Run run = Cli.run("validate", "--rules", "none",
                Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertTrue(run.text().contains("official artefacts over the written CII:"),
                run.text());
        assertTrue(run.text().contains("[BR-48]"),
                "the artefact reported it in its own words: " + run.text());
        assertTrue(run.text().endsWith("INVALID\n"), run.text());
    }

    /**
     * A term the writer had no place for makes the row not applicable, not a verdict.
     *
     * <p>The extension instances of the corpus carry sub invoice lines of the XRechnung
     * extension, which the source model binds for UBL Invoice alone. The cross industry
     * invoice written from one of them is a different document — its amounts no longer add
     * up, which is what {@code conformance/writers/cii-roundtrip.md} records — so an
     * artefact's verdict over it would be about something else, and the run says so
     * instead of reporting it.
     */
    @Test
    void refusesToJudgeADocumentTheSyntaxCouldNotCarry() {
        String extension = "conformance/esj/business-cases/extension/"
                + "04.01a-INVOICE_ubl.xml.esj.json";
        Cli.Run run = Cli.run("validate", "--extension", "xrechnung",
                Fixtures.file(directory, extension));
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("no place in this syntax"), run.text());
        assertTrue(run.text().contains("written-syntax (term-not-in-syntax)"),
                "the cause is a token from a closed vocabulary: " + run.text());

        Cli.Run json = Cli.run("validate", "--extension", "xrechnung", "--output", "json",
                Fixtures.file(directory, extension));
        assertTrue(json.text().contains("\"cause\": \"term-not-in-syntax\""), json.text());
        assertTrue(json.text().contains("\"" + Validation.NOT_CHECKED_WRITTEN_SYNTAX + "\""),
                json.text());
    }

    /**
     * A written document larger than the syntax engine of this run accepts.
     *
     * <p>The bound is the run's own {@code --max-input-bytes}, and it lands on a document
     * several times smaller than itself, because a cross industry invoice is about four
     * times the size of the ESJ it is written from. That is a property of the syntax and
     * of the bound and not of the invoice, so the row is the one that does not run and the
     * three that did keep their answer: {@code INDETERMINATE} with the cause in the
     * report, rather than {@code LIMIT} and no verdict at all. The same run with the bound
     * raised reaches the artefacts.
     */
    @Test
    void reportsAWrittenDocumentLargerThanTheEngineAcceptsWithoutLosingTheVerdict() {
        String file = Fixtures.file(directory, STANDARD);
        Cli.Run run = Cli.run("validate", "--max-input-bytes", "4096", file);
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("written-syntax (written-over-bound)"),
                "the cause is a token from a closed vocabulary: " + run.text());
        assertTrue(run.text().contains("--max-input-bytes raises that bound"),
                "and the switch that raises it is the one that does: " + run.text());

        assertEquals(ExitCode.SUCCESS, Cli.run("validate", "--max-input-bytes", "4000000",
                file).exitCode());
    }

    /**
     * An element the syntax requires and the semantic model has no term for is written
     * with the value the binding table states, so the artefacts judge the document.
     *
     * <p>UBL asks every party tax scheme for a tax scheme and EN 16931-1 has a term for
     * the value added tax scheme alone. The {@code conventions} member of the binding
     * table says what is written there, the value carries no business statement, and the
     * document is complete — so the row runs, over both syntaxes, and an invoice the
     * official validator accepts reaches {@code VALID} either way.
     */
    @Test
    void judgesARenditionWhoseSuppliedElementsTheBindingTableStates() {
        String file = Fixtures.file(directory, "conformance/esj/technical-cases/cius/"
                + "01.02_comprehensive_test_uncefact.xml.esj.json");
        Cli.Run run = Cli.run("validate", "--via", "ubl", file);
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(run.text().contains("UBL-SR-53"),
                "and the element the writer supplied makes nothing fire: " + run.text());

        assertEquals(ExitCode.SUCCESS, Cli.run("validate", "--via", "cii", file).exitCode());
    }

    /**
     * An element the syntax requires whose content a business term does carry and this
     * document does not state.
     *
     * <p>UBL requires {@code cbc:TaxAmount} of every tax total and this document states no
     * BT-110, so the writer leaves the element out and the schema modules refuse the
     * result. The refusal is about the rendition rather than about the invoice — the
     * native rules of the same run have already faulted the missing total — so the row
     * does not run and the reason names the gap; {@code --via cii} writes the same
     * document into a syntax that does not ask for it.
     */
    @Test
    void refusesToJudgeARenditionOfATermTheDocumentDoesNotState() {
        String file = Fixtures.file(directory, "conformance/esj/technical-cases/cius/"
                + "01.05_minimal_test_uncefact.xml.esj.json");
        Cli.Run run = Cli.run("validate", "--via", "ubl", file);
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("written-syntax (term-not-stated)"),
                "the cause is a token from a closed vocabulary: " + run.text());
        assertTrue(run.text().contains("a business term of the semantic model carries and"
                        + " this document does not state"), run.text());
        assertFalse(run.text().contains("cvc-complex-type.2.4.a"),
                "and no finding about an element the writer could not write is reported as"
                        + " a finding about the invoice: " + run.text());
    }

    /**
     * A written document longer than this run lets the writer produce.
     *
     * <p>It is the bound one step in front of the one
     * {@link #reportsAWrittenDocumentLargerThanTheEngineAcceptsWithoutLosingTheVerdict}
     * reaches, and it is the same kind of fact: a bound on the XML the document is written
     * to, not on the document that was handed over. So it is the same answer — the row
     * that did not run, and the verdict the reader, the structural layers and the native
     * rules had already reached — rather than {@code LIMIT} and no verdict at all.
     */
    @Test
    void reportsAWrittenDocumentLongerThanTheWriterMayProduceWithoutLosingTheVerdict() {
        String file = Fixtures.file(directory, STANDARD);
        Cli.Run run = Cli.run("validate", "--max-output-bytes", "1000", file);
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("written-syntax (written-over-bound)"),
                "the cause is a token from a closed vocabulary: " + run.text());
        assertTrue(run.text().contains("--max-output-bytes raises that bound"),
                "and the switch that raises it is the one that does: " + run.text());
        assertTrue(run.text().contains("EN 16931 business rules"),
                "the other components keep their answer: " + run.text());

        assertEquals(ExitCode.SUCCESS, Cli.run("validate", file).exitCode());
    }

    /**
     * A clock that runs out while the artefacts read the written document.
     *
     * <p>Two limits end that step and they are not the same fact. A written document
     * larger than a bound of this run leaves every other component with its answer, so the
     * run reports it and ends {@code INDETERMINATE}. A clock that ran out leaves the
     * document without a verdict, and exit code {@link ExitCode#LIMIT} is the one word
     * this tool has for that; a timeout that answered 9 because of where in the command it
     * landed would make the exit code a matter of milliseconds.
     *
     * <p>The step is chosen against the steps rather than by making the budget small
     * enough to expire anywhere: reading this document takes single milliseconds and
     * compiling the rule sets of the pack takes hundreds, so a budget in that range is
     * spent inside the artefacts. Every budget is asserted the same way, so a machine fast
     * enough to finish one of them inside it reports {@code VALID} and is no
     * counter-example.
     */
    @Test
    void keepsTheLimitCodeWhereTheClockRunsOutOverTheWrittenDocument() {
        String file = Fixtures.file(directory, STANDARD);
        Cli.Run immediate = Cli.run("validate", "--max-runtime", "1ms", file);
        assertEquals(ExitCode.LIMIT, immediate.exitCode(), immediate.err());
        for (int budget = 100; budget <= 900; budget += 100) {
            Cli.Run run = Cli.run("validate", "--max-runtime", budget + "ms", file);
            if (run.exitCode() == ExitCode.SUCCESS) {
                continue;
            }
            assertEquals(ExitCode.LIMIT, run.exitCode(),
                    "a budget of " + budget + " ms ran out: " + run.err() + run.text());
            assertEquals(0, run.out().length, "a run without a verdict writes no report");
        }
    }

    /**
     * The two limits of that step, told apart where the command tells them apart.
     *
     * <p>The syntax engine reaches one exit code for both: an input larger than it accepts
     * and a run longer than the time it was given. Only the first is a fact about the
     * written document, and the budget the exception carries is what says which of the two
     * happened. A limit the deadline of the command raised itself carries no such
     * exception at all, and is a clock as well.
     */
    @Test
    void tellsAClockThatRanOutFromABoundOnTheWrittenBytes() {
        SyntaxLimitException clock = assertThrows(SyntaxLimitException.class,
                () -> SyntaxValidator.validate(Fixtures.bytes(UBL),
                        SyntaxOptions.defaults().withMaxRuntime(Duration.ofMillis(1))));
        assertTrue(clock.budget().isPresent(), clock.getMessage());
        assertFalse(ValidateCommand.overBound(CliException.limit("out of time", clock)),
                "a clock leaves no verdict for any row to keep");
        assertFalse(ValidateCommand.overBound(CliException.limit("out of time")),
                "and neither does the deadline of the whole command");

        SyntaxLimitException bytes = new SyntaxLimitException("the document is longer than"
                + " this validation accepts");
        assertTrue(bytes.budget().isEmpty(), bytes.getMessage());
        assertTrue(ValidateCommand.overBound(CliException.limit("too large", bytes)),
                "a bound on the written bytes is a row that did not run");
    }

    @Test
    void namesTheBindingItDidNotCheckInTheJsonReport() {
        Cli.Run run = Cli.run("validate", "--no-syntax", "--output", "json",
                Fixtures.file(directory, UBL));
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"" + Validation.NOT_CHECKED_SYNTAX_BINDING + "\""),
                run.text());
        assertFalse(Cli.run("validate", "--output", "json", Fixtures.file(directory, UBL))
                        .text().contains("\"" + Validation.NOT_CHECKED_SYNTAX_BINDING + "\""),
                "a run that did check the binding does not list it as unchecked");
    }

    @Test
    void writesAStableReportInJson() {
        Cli.Run run = Cli.run(Fixtures.bytes(STANDARD), "validate", "-", "--output", "json");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals(Fixtures.text("golden/validate-standard-invoice.json"), run.text());
        assertEquals(List.of("input", "detected", "semanticModel",
                        "container", "invoice", "checked", "ok", "reason", "xml",
                        "syntax", "checked", "ok", "reason", "customizationId", "pack",
                        "profileNote", "profileRulesSkipped", "ran", "skipped", "findings",
                        "written", "required", "checked", "target", "reason", "byDesign",
                        "syntax", "checked", "ok", "reason", "customizationId",
                        "pack", "directory", "id", "version", "release", "source",
                        "profileNote", "profileRulesSkipped",
                        "ran", "component", "engine", "stopped",
                        "component", "engine", "stopped",
                        "skipped", "component", "reason", "component", "reason",
                        "component", "reason", "component", "reason", "findings",
                        "layers",
                        "l1", "checked", "ok", "findings",
                        "l2", "checked", "ok", "findings",
                        "l3", "checked", "ok", "findings",
                        "rules", "checked", "ok", "reason", "pack", "id", "version",
                        "findings",
                        "notChecked", "reasons",
                        "warnings", "information", "verdict"),
                memberNames(run.out()),
                "the members of the report are the ones the interface promises");
    }

    /**
     * A structural example carrying only the mandatory terms is reported on by the rules.
     *
     * <p>{@code examples/minimal.esj.json} is the smallest document the cardinalities
     * admit, and the business rules of the standard ask for more than the cardinalities
     * do — a rate for a VAT breakdown, something to identify the seller by, a date or
     * terms to pay by. The example is left as it is, because what it is for is the shape
     * of a document; {@code examples/README.md} says which examples are of that kind and
     * what the rules therefore report on them, and this golden file is what they report.
     */
    @Test
    void reportsTheBusinessRulesAStructuralExampleDoesNotKeep() {
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "validate", "-", "--output", "json");
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.err());
        assertEquals(Fixtures.text("golden/validate-minimal.json"), run.text());
    }

    @Test
    void writesTheFindingsOfALayerIntoTheJsonReport() {
        byte[] fixture = Fixtures.bytes("examples/invalid/unknown-term.esj.json");
        Cli.Run run = Cli.run(fixture, "validate", "-", "--output", "json");
        assertEquals(ExitCode.VALIDATION, run.exitCode());
        assertTrue(run.text().contains("\"code\": \"ESJ-L2-UNKNOWN-TERM\""), run.text());
        assertTrue(run.text().contains("\"severity\": \"error\""), run.text());
        assertTrue(run.text().contains("\"l2\": {\n      \"checked\": true,\n      \"ok\": false"),
                run.text());
    }

    @Test
    void stopsAtTheLayerThatFoundAnErrorAndSaysTheRestWasNotChecked() {
        Cli.Run run = Cli.run(Fixtures.bytes("examples/invalid/object-without-component.esj.json"),
                "validate", "-");
        assertEquals(ExitCode.VALIDATION, run.exitCode());
        assertTrue(run.text().contains("ESJ format (L1):           1 error"), run.text());
        assertTrue(run.text().contains("Model (L2):                not checked"), run.text());
        assertTrue(run.text().contains("Cardinality (L3):          not checked"), run.text());
        assertFalse(run.text().contains("ESJ-L3-"),
                "a layer that did not run has nothing to report: " + run.text());
    }

    @Test
    void doesNotCountCardinalitiesOfAModelTheLayerBelowRejected() {
        Cli.Run run = Cli.run(Fixtures.bytes("examples/invalid/index-on-bt-1.esj.json"),
                "validate", "-");
        assertEquals(ExitCode.VALIDATION, run.exitCode());
        assertTrue(run.text().contains("ESJ-L2-INDEX-FORBIDDEN"), run.text());
        assertTrue(run.text().contains("Cardinality (L3):          not checked"), run.text());
        assertFalse(run.text().contains("is missing at the root of the document"),
                "BT-1 is in the file, at the wrong path: " + run.text());
    }

    @Test
    void namesTheLayersItDidNotRunInTheJsonReport() {
        Cli.Run run = Cli.run(Fixtures.bytes("examples/invalid/object-without-component.esj.json"),
                "validate", "-", "--output", "json");
        assertEquals(ExitCode.VALIDATION, run.exitCode());
        assertTrue(run.text().contains("\"model-l2\""), run.text());
        assertTrue(run.text().contains("\"cardinality-l3\""), run.text());
        assertTrue(run.text().contains("\"l3\": {\n      \"checked\": false,\n      \"ok\": null"),
                run.text());
    }

    @Test
    void neverClaimsThatEverythingReachedTheDocument() {
        Cli.Run run = Cli.run("validate", Fixtures.file(directory, CII));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertFalse(run.text().contains("everything"), run.text());
        assertFalse(run.text().contains("value of the source"), run.text());
        assertFalse(run.text().contains("values of the source"), run.text());
    }

    @Test
    void countsNotesRatherThanValuesWhenTheImporterSkippedSomething() {
        Cli.Run run = Cli.run("validate", Fixtures.file(directory, EXTENSION));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("observations about content of the source that did not"
                + " reach the document"), run.text());
        assertFalse(run.text().contains("values of the source"),
                "one note can stand for a whole subtree: " + run.text());
    }

    /**
     * The XSLT path is the reader that has information-level notes to report — it drops
     * the supplementary components the standard does not give a term and says so — so it
     * is the one this property is measured on.
     */
    @Test
    void makesNoClaimAboutLossWhereTheOnlyNotesAreInformationLevel() {
        Cli.Run run = Cli.run("--importer", "xslt", "validate", Fixtures.file(directory, CII));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("notes on the distance between this syntax and"
                + " EN 16931"), run.text());
        assertFalse(run.text().contains("lost"),
                "the same document dropped the scheme of BT-31, so no line may say nothing"
                        + " was lost: " + run.text());
    }

    @Test
    void doesNotAskForVerboseInTheOutputOfAVerboseRun() {
        Cli.Run quiet = Cli.run("--importer", "xslt", "validate", Fixtures.file(directory, CII));
        assertTrue(quiet.text().contains("(run with --verbose)"), quiet.text());
        Cli.Run loud = Cli.run("--verbose", "--importer", "xslt", "validate",
                Fixtures.file(directory, CII));
        assertEquals(ExitCode.SUCCESS, loud.exitCode(), loud.err());
        assertFalse(loud.text().contains("(run with --verbose)"), loud.text());
        assertTrue(loud.text().contains("(see the standard error stream)"), loud.text());
    }

    @Test
    void reportsHowLongTheRunTookUnderVerbose() {
        Cli.Run run = Cli.run("--verbose", "validate", Fixtures.file(directory, STANDARD));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("info: finished in "), run.err());
        assertTrue(run.err().contains(" ms"), run.err());
    }

    @Test
    void carriesTheTwoLevelsOfImportNoteInTwoArrays() {
        Cli.Run run = Cli.run("--importer", "xslt", "validate", "--output", "json",
                Fixtures.file(directory, CII));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"warnings\": []"), run.text());
        assertTrue(run.text().contains("\"kind\": \"COMPONENT_DROPPED\""),
                "the information array carries what the warnings array must not: " + run.text());
    }

    @Test
    void escapesDocumentContentItQuotesInAFinding() {
        byte[] fixture = ("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{"
                + "\"/BT-2\":\"2026-01-0\\u001b[2K1\"}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Cli.Run run = Cli.run(fixture, "validate", "-");
        assertEquals(ExitCode.VALIDATION, run.exitCode());
        assertTrue(run.text().contains("ESJ-L2-DATE"), run.text());
        assertFalse(run.text().contains("\u001b"),
                "an escape sequence of the document must not reach the terminal");
        assertTrue(run.text().contains("\\u001b"), run.text());
    }

    /**
     * A document of an edition this build has no registry for is read, canonicalized and
     * reported on, and the model layers say that they measured nothing.
     *
     * <p>Which editions an implementation holds a registry for is a property of the
     * implementation and not of the document (specification, sections 4.4 and 9.2), so
     * neither {@code VALID} nor {@code INVALID} is available here: the first would claim a
     * check nobody ran and the second would blame the document for this build's age.
     */
    @Test
    void reachesNoVerdictOnADocumentOfAnEditionItHasNoRegistryFor() {
        byte[] future = ("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2099\",\"values\":{"
                + "\"/BT-1\":\"RE-2099-0001\"}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Cli.Run run = Cli.run(future, "validate", "-");

        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("ESJ-L2-EDITION-UNKNOWN"), run.text());
        assertTrue(run.text().contains("Model (L2):                no verdict"),
                "a layer that measured nothing does not report OK: " + run.text());
        assertTrue(run.text().contains("model-l2 (edition-unknown)")
                        && run.text().contains("cardinality-l3 (edition-unknown)"),
                "without a registry the cardinality layer has nothing to count either: "
                        + run.text());
    }

    /**
     * A path only an extension registry defines, with no such registry loaded, is a check
     * that did not happen and not a check that passed.
     */
    @Test
    void reachesNoVerdictWhereAnExtensionPathWasNotMeasured() {
        byte[] extended = ("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{"
                + "\"/BG-25/0/BG-DEX-01/0/BG-DEX-07/BT-146\":\"12.5\"}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Cli.Run without = Cli.run(extended, "validate", "--level", "l2", "-",
                "--output", "json");

        assertEquals(ExitCode.INDETERMINATE, without.exitCode(), without.err());
        assertTrue(without.text().contains("\"cause\": \"extension-registry-missing\""),
                without.text());
        assertTrue(without.text().contains("ESJ-L2-NOT-CHECKED"), without.text());
    }

    /**
     * A layer that found an error says so in both forms, whatever else the run could not
     * measure.
     *
     * <p>The coverage gap and the defect are two statements, and only one of them is about
     * the invoice. A document that carries an extension path no loaded registry describes
     * leaves the cardinality layer short of the whole document — so the layer is a gap of
     * the coverage — and the same document can be missing a mandatory term, which the layer
     * did find. The text row prints the count; {@code layers.l3.ok} therefore reads
     * {@code false} rather than {@code null}, because a program branching on it must not
     * miss a layer that definitely failed.
     */
    @Test
    void reportsAnErrorOfALayerTheCoverageAlsoNamesAsAGap() {
        byte[] extended = ("{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{"
                + "\"/BG-25/0/BG-DEX-01/0/BG-DEX-07/BT-146\":\"12.5\"}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Cli.Run lines = Cli.run(extended, "validate", "-");
        Cli.Run json = Cli.run(extended, "validate", "-", "--output", "json");

        assertEquals(ExitCode.VALIDATION, lines.exitCode(), lines.err());
        assertTrue(lines.text().contains("ESJ-L3-MISSING-TERM"), lines.text());
        assertTrue(lines.text().contains("Cardinality (L3):          ")
                        && !lines.text().contains("Cardinality (L3):          no verdict"),
                "the text row counts the errors: " + lines.text());
        assertEquals("false", layerOk(json.text(), "l3"),
                "one answer in both forms: " + json.text());
        assertEquals("null", layerOk(json.text(), "l2"),
                "the model layer found no error and measured less than the document: "
                        + json.text());
    }

    /** Returns the {@code ok} member of one layer of a JSON report, as it is written. */
    private static String layerOk(String json, String layer) {
        int at = json.indexOf("\"" + layer + "\": {");
        int ok = json.indexOf("\"ok\": ", at);
        int end = json.indexOf(',', ok);
        return json.substring(ok + "\"ok\": ".length(), end);
    }

    @Test
    void refusesAnOutputFormItDoesNotKnow() {
        Cli.Run run = Cli.run("validate", "--output", "xml", Fixtures.file(directory, MINIMAL));
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--output takes text or json"), run.err());
    }

    /** Returns every member name of a JSON document, in the order it writes them. */
    private static List<String> memberNames(byte[] json) {
        List<String> names = new ArrayList<>();
        try (JsonParser parser = new JsonFactory().createParser(json)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME) {
                    names.add(parser.currentName());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }
}
