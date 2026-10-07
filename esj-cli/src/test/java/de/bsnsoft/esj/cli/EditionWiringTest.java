package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.model.Registry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * What every command does with a document of an edition other than the default one.
 *
 * <p>The rule the whole tool follows is one sentence: a command either serves the edition
 * the document names or refuses the document, and it never serves part of it. A path is an
 * address relative to an edition ({@code SPEC.md}, sections 4.4 and 10), so a component
 * written for one edition that is handed a document of another would place the terms the
 * two have in common and lose the rest — which for a writer means an invoice arriving at
 * its receiver short of business content, and for a rendering means a page whose reader
 * cannot tell what is missing.
 *
 * <p>So: the structural layers select the registry of the document's own edition and
 * measure it; the PDF rendering, in either layout, is driven by that same registry and
 * draws every value; the rule pack, the binding tables and the vendored stylesheets are
 * written for one edition and say so; and {@code get}, {@code list}, {@code diff} and
 * {@code canonicalize} are edition-blind by construction and work unchanged.
 *
 * <p>Every test that needs the 2026 edition is skipped where this build carries no
 * registry of it, which is what the profile {@code without-edition-2026} produces. The
 * last two are not skipped: they are the ones that check that an edition-blind command
 * stays edition-blind, and that a build without the edition says so rather than guessing.
 */
class EditionWiringTest {

    private static final String EDITION_2026 = "examples/edition-2026.esj.json";

    private static final String MINIMAL = "examples/minimal.esj.json";

    private static final String STANDARD = "examples/standard-invoice.esj.json";

    @TempDir
    private Path directory;

    static boolean carries2026() {
        return Registry.editions().contains("2026");
    }

    @Test
    @EnabledIf("carries2026")
    void inspectNamesTheEditionAndReadsTheDocumentByIt() {
        Cli.Run run = Cli.run(Fixtures.bytes(EDITION_2026), "inspect", "-");
        assertTrue(run.text().contains("Semantic model:             EN16931-1:2026"),
                run.text());
        assertTrue(run.text().contains("Invoice lines:              1"), run.text());
        assertFalse(run.text().contains("no registry in this build"), run.text());
    }

    /**
     * The document is measured against the registry of its own edition and the rule pack
     * of that edition runs over it, so the structural layers and the business rules both
     * answer. What cannot run is the component that hands the document to the official
     * validation artefacts: none is published for this edition, and no syntax binding of
     * the terms it adds is published either, so the document reaches no written form an
     * artefact could read. The verdict is therefore the third state and the exit code 9,
     * with the cause in the closed vocabulary — never {@code VALID}.
     */
    @Test
    @EnabledIf("carries2026")
    void validateRunsThePackOfTheEditionAndStillReachesNoVerdict() {
        Cli.Run run = Cli.run(Fixtures.bytes(EDITION_2026), "validate", "-");
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains("Semantic model:   EN16931-1:2026"), run.text());
        assertTrue(run.text().contains("Model (L2):                OK"), run.text());
        assertTrue(run.text().contains("Cardinality (L3):          OK"), run.text());
        assertTrue(run.text().contains("pack en16931-2026/0.1"), run.text());
        assertTrue(run.text().contains("no syntax binding is published for this edition"),
                run.text());
        assertTrue(run.text().contains("INDETERMINATE"), run.text());
        assertTrue(run.text().contains("written-syntax (no-artefacts-for-edition)"), run.text());
    }

    @Test
    @EnabledIf("carries2026")
    void theJsonReportCarriesTheEditionAndTheCause() {
        Cli.Run run = Cli.run(Fixtures.bytes(EDITION_2026),
                "validate", "-", "--output", "json");
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("\"semanticModel\": \"EN16931-1:2026\""), run.text());
        assertTrue(run.text().contains("\"cause\": \"no-artefacts-for-edition\""), run.text());
        assertTrue(run.text().contains("\"verdict\": \"INDETERMINATE\""), run.text());
    }

    /**
     * No official artefact stands behind the pack of this edition, and every place that
     * names the pack says so: the row of the text form, the pack object of the JSON form
     * and the row of the report file.
     */
    @Test
    @EnabledIf("carries2026")
    void everyFormOfTheReportSaysThePackIsNotCorroborated() throws Exception {
        Cli.Run text = Cli.run(Fixtures.bytes(EDITION_2026), "validate", "-");
        assertTrue(text.text().contains("EN 16931 business rules (native, pack"
                + " en16931-2026/0.1, " + RuleCheck.UNCORROBORATED + "): OK"), text.text());
        Cli.Run json = Cli.run(Fixtures.bytes(EDITION_2026),
                "validate", "-", "--output", "json");
        assertTrue(json.text().contains("\"edition\": \"EN 16931-1:2026\""), json.text());
        assertTrue(json.text().contains("\"corroborated\": false"), json.text());
        Path report = directory.resolve("report.html");
        Cli.Run file = Cli.run(Fixtures.bytes(EDITION_2026), "validate", "-",
                "--report", report.toString(), "--report-lang", "en");
        assertEquals(ExitCode.INDETERMINATE, file.exitCode(), file.err());
        assertTrue(Files.readString(report).contains("pack en16931-2026/0.1, "
                + RuleCheck.UNCORROBORATED), "the report file names it too");
    }

    /** A fatal finding of the pack of this edition decides the verdict. */
    @Test
    @EnabledIf("carries2026")
    void aFatalFindingOfThePackOfTheEditionMakesTheDocumentInvalid() {
        String document = new String(Fixtures.bytes(EDITION_2026), StandardCharsets.UTF_8)
                .replace("\"/BG-22/BT-112\": \"1190\"", "\"/BG-22/BT-112\": \"1191\"");
        Cli.Run run = Cli.run(document.getBytes(StandardCharsets.UTF_8), "validate", "-");
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains("BR-CO-15 [fatal]"), run.text());
        assertTrue(run.text().strip().endsWith("INVALID"), run.text());
    }

    /**
     * A document of an edition this project ships as a preview is said to be one in the
     * text a person reads; the JSON output keeps the edition as the document names it.
     */
    @Test
    @EnabledIf("carries2026")
    void saysThatTheEditionOfADocumentIsAPreview() {
        Cli.Run validated = Cli.run(Fixtures.bytes(EDITION_2026), "validate", "-");
        assertTrue(validated.text().contains("Semantic model:   EN16931-1:2026 (preview)"),
                validated.text());
        Cli.Run inspected = Cli.run(Fixtures.bytes(EDITION_2026), "inspect", "-");
        assertTrue(inspected.text().contains("EN16931-1:2026 (preview)"), inspected.text());
        Cli.Run json = Cli.run(Fixtures.bytes(EDITION_2026), "validate", "-", "--output", "json");
        assertTrue(json.text().contains("\"semanticModel\": \"EN16931-1:2026\""), json.text());
        Cli.Run standard = Cli.run(Fixtures.bytes(MINIMAL), "validate", "-");
        assertFalse(standard.text().contains("(preview)"), standard.text());
    }

    /** Left out by the caller, the row still names the pack the edition would have run. */
    @Test
    @EnabledIf("carries2026")
    void rulesNoneNamesThePackOfTheDocumentsEdition() {
        Cli.Run run = Cli.run(Fixtures.bytes(EDITION_2026), "validate", "-", "--rules", "none");
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.err());
        assertTrue(run.text().contains("pack en16931-2026/0.1, " + RuleCheck.UNCORROBORATED
                + "): " + RuleCheck.BY_OPTION), run.text());
        assertFalse(run.text().contains(RuleCheck.PACK), run.text());
    }

    /**
     * The listing names every rule pack this build carries, with its edition, and marks the
     * pack of an edition this project ships as a preview.
     */
    @Test
    void theListingNamesTheRulePacksWithTheirEdition() {
        Cli.Run run = Cli.run("--list-packs");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains(RuleCheck.PACK + System.lineSeparator() + "  "
                + "EN 16931-1:2017+A1:2019/AC:2020; measured against"), run.text());
        if (carries2026()) {
            assertTrue(run.text().contains("en16931-2026/0.1 (preview)" + System.lineSeparator()
                    + "  EN 16931-1:2026; " + RuleCheck.UNCORROBORATED), run.text());
            assertTrue(run.text().contains(" downgrade, ") && run.text().contains(" cases"),
                    run.text());
        } else {
            assertFalse(run.text().contains("en16931-2026"), run.text());
        }
    }

    /** The same document under the default edition is checked whole and is valid. */
    @Test
    void aDocumentOfTheDefaultEditionIsStillCheckedByThePack() {
        Cli.Run run = Cli.run(Fixtures.bytes(STANDARD), "validate", "-");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains("Semantic model:   "
                + Registry.en16931().semanticModel()), run.text());
        assertTrue(run.text().contains(RuleCheck.LABEL + ": OK"), run.text());
    }

    @Test
    @EnabledIf("carries2026")
    void convertToCiiRefusesRatherThanDroppingWhatTheTableCannotBind() {
        Cli.Run run = Cli.run(Fixtures.bytes(EDITION_2026), "convert", "-", "--to", "cii");
        assertEquals(ExitCode.UNSUPPORTED, run.exitCode(), run.err());
        assertTrue(run.err().contains("EN16931-1:2026"), run.err());
        assertTrue(run.err().contains("CII binding table"), run.err());
        assertTrue(run.err().contains("esj upgrade"), run.err());
        assertEquals(0, run.out().length, "nothing of the document was written");
    }

    /** Writing it as ESJ is not a binding and is not refused. */
    @Test
    @EnabledIf("carries2026")
    void convertToEsjWritesTheDocumentOfEveryEdition() {
        Cli.Run run = Cli.run(Fixtures.bytes(EDITION_2026), "convert", "-", "--to", "esj");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.text().contains("EN16931-1:2026"), run.text());
    }

    @Test
    @EnabledIf("carries2026")
    void theHtmlRenderingRefusesWhatTheVendoredStylesheetsCannotShow() {
        Path out = directory.resolve("page.html");
        Cli.Run run = Cli.run(Fixtures.bytes(EDITION_2026), "render", "-", "--html",
                "--out", out.toString());
        assertEquals(ExitCode.UNSUPPORTED, run.exitCode(), run.err());
        assertTrue(run.err().contains("EN16931-1:2026"), run.err());
        assertTrue(run.err().contains("visualization stylesheets"), run.err());
        assertFalse(Files.exists(out), "no half of a page was written");
    }

    /**
     * The PDF is this project's own layout and is driven by the registry of the document's
     * own edition, so it renders rather than refuses. That every value reaches the page is
     * checked in {@code esj-render}, where the whole registry can be put into one document.
     */
    @Test
    @EnabledIf("carries2026")
    void thePdfRenderingDrawsADocumentOfEitherEdition() {
        Path out = directory.resolve("invoice.pdf");
        Cli.Run run = Cli.run(Fixtures.bytes(EDITION_2026), "render", "-",
                "--out", out.toString());
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(Files.exists(out), run.err());
    }

    /**
     * {@code get} resolves a path against the registry of the document's own edition, so
     * the sentence about a path that addresses nothing is about that edition: {@code /BT-20}
     * is a payment term text at the root of a 2017 document and is no path at all in a 2026
     * one, where the term sits under the new payment terms group.
     */
    @Test
    @EnabledIf("carries2026")
    void getAnswersForTheEditionTheDocumentNames() {
        Cli.Run found = Cli.run(Fixtures.bytes(EDITION_2026), "get", "-", "/BG-33/0/BT-20");
        assertEquals(ExitCode.SUCCESS, found.exitCode(), found.err());
        assertTrue(found.text().startsWith("Payable within 30 days"), found.text());

        Cli.Run moved = Cli.run(Fixtures.bytes(EDITION_2026), "get", "-", "/BT-20");
        assertEquals(ExitCode.VALIDATION, moved.exitCode(), moved.err());
        assertTrue(moved.err().contains("BT-20"), moved.err());
    }

    @Test
    @EnabledIf("carries2026")
    void listAndCanonicalizeAreBlindToTheEdition() {
        Cli.Run list = Cli.run(Fixtures.bytes(EDITION_2026), "list", "-");
        assertEquals(ExitCode.SUCCESS, list.exitCode(), list.err());
        assertTrue(list.text().contains("/BT-166"), list.text());

        Cli.Run canonical = Cli.run(Fixtures.bytes(EDITION_2026), "canonicalize", "-");
        assertEquals(ExitCode.SUCCESS, canonical.exitCode(), canonical.err());
        assertTrue(canonical.text().contains("EN16931-1:2026"), canonical.text());
    }

    /**
     * {@code diff} compares two documents of different editions — comparing an invoice with
     * its own upgrade is a reason to run it — and says that it is doing so, because a path
     * the two editions place differently is reported as a difference and the cause is then
     * the edition rather than the invoice.
     */
    @Test
    @EnabledIf("carries2026")
    void diffAcrossEditionsSaysSo() {
        String other = Fixtures.file(directory, EDITION_2026);
        Cli.Run run = Cli.run(Fixtures.bytes(MINIMAL), "diff", "-", other);
        assertTrue(run.err().contains("names the edition"), run.err());
        assertTrue(run.err().contains("EN16931-1:2026"), run.err());
    }

    /**
     * A build that carries no registry of the edition a document names measures nothing
     * against it, says so in the identity line and in the cause, and reaches the third
     * state rather than calling the document wrong.
     */
    @Test
    void anEditionThisBuildDoesNotCarryIsTheThirdStateAndNotAnInvalidDocument() {
        byte[] document = Fixtures.bytes(STANDARD);
        String text = new String(document, StandardCharsets.UTF_8)
                .replace(Registry.en16931().semanticModel(), "EN16931-1:2999");
        Cli.Run run = Cli.run(text.getBytes(StandardCharsets.UTF_8),
                "validate", "-");
        assertEquals(ExitCode.INDETERMINATE, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains(
                "Semantic model:   EN16931-1:2999 (no registry in this build)"), run.text());
        assertTrue(run.text().contains("edition-unknown"), run.text());
    }
}
