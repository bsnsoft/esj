package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code esj validate --verapdf}: the switch that turns the PDF/A row of the report from a
 * declaration into a verdict.
 *
 * <p>This tool reads what a file declares about itself and says so in those words. The
 * reference PDF/A validator is under a copyleft licence and is in no artefact this project
 * publishes, so validating the claim means a validator the caller installed, run as a
 * process of its own. What is tested here is that side of it: the installation is resolved,
 * the process is started, its report is read, the three facts reach the report of this tool
 * in both its forms, and a file the validator rejects is a container that is wrong about
 * itself rather than an invoice that is wrong.
 *
 * <p>The validator here is a script that prints a recorded report. The question these tests
 * ask is what this tool does with what a validator says, and installing one to answer it
 * would make the build depend on a program that is not in it. What a real validator says
 * about the files this project writes is asked where the real one runs: {@code esj-render}
 * validates every instance of the corpus, rendered and embedded, against veraPDF itself.
 *
 * <p>The same switch is on the two commands that write the claim — {@code esj embed} and
 * {@code esj render --embed cii} — where it checks the file the invoice goes into before
 * anything is written into it, and a file the validator rejects is refused.
 *
 * <p>They need a shell, so they run where there is one.
 */
@EnabledOnOs({OS.LINUX, OS.MAC})
class VerapdfOptionTest {

    /** A document of this repository's own examples. */
    private static final String EXAMPLE = "examples/standard-invoice.esj.json";

    /** The version the recorded report names. */
    private static final String VERSION = "1.30.2";

    /** The profile the recorded report names. */
    private static final String PROFILE = "PDF/A-3B validation profile";

    @TempDir
    private Path directory;

    /** A report of a file the validator accepts. */
    private static String passing() {
        return report("true", 0, 0);
    }

    /** A report of a file the validator rejects, with two broken rules behind it. */
    private static String failing() {
        return report("false", 2, 7);
    }

    /**
     * Returns a veraPDF XML report of the shape the greenfield validator writes: the
     * versions of its components, and one validation report per file with the counts
     * behind the verdict.
     */
    private static String report(String compliant, int failedRules, int failedChecks) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <report>
                  <buildInformation>
                    <releaseDetails id="core" version="%s" buildDate="2026-01-01"/>
                    <releaseDetails id="validation-model" version="%s"/>
                  </buildInformation>
                  <jobs>
                    <job>
                      <validationReport profileName="%s" isCompliant="%s">
                        <details passedRules="139" failedRules="%d" passedChecks="9000"
                                 failedChecks="%d"/>
                      </validationReport>
                    </job>
                  </jobs>
                </report>
                """.formatted(VERSION, VERSION, PROFILE, compliant, failedRules,
                        failedChecks);
    }

    /** A validator that says the file is compliant fills the row with what it said. */
    @Test
    void aFileTheValidatorAcceptsIsReportedWithTheValidatorVersionAndProfile() {
        String pdf = hybrid();
        String validator = validator(passing(), 0);

        Cli.Run run = Cli.run("validate", pdf, "--verapdf", validator);
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains("PDF/A-3 declared           yes, PDF/A-3B — veraPDF "
                        + VERSION + ", \"" + PROFILE + "\": PASS"),
                "the row says whose answer it is: " + run.text());
        assertTrue(run.text().contains("Container:        OK"), run.text());
    }

    /** Without the switch the row is the declaration it always was. */
    @Test
    void withoutTheSwitchTheRowSaysThatNothingValidatedTheClaim() {
        Cli.Run run = Cli.run("validate", hybrid());
        assertTrue(run.text().contains("PDF/A-3 declared           yes, PDF/A-3B —"
                        + " declared, not validated"),
                "the claim is read and nothing checked it: " + run.text());
    }

    /**
     * A file the validator rejects is a container that is wrong about itself. The invoice
     * inside it is untouched by that, and the report keeps the two apart.
     */
    @Test
    void aFileTheValidatorRejectsMakesTheContainerInvalidAndNotTheInvoice() {
        String pdf = hybrid();
        String validator = validator(failing(), 1);

        Cli.Run run = Cli.run("validate", pdf, "--verapdf", validator);
        assertEquals(ExitCode.VALIDATION, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains(": FAIL (2 failed rules, 7 failed checks)"),
                "with the counts the validator gave: " + run.text());
        assertTrue(run.text().contains("Container:        INVALID"), run.text());
        assertTrue(run.text().contains("Invoice:          VALID"),
                "and the invoice inside it is still the invoice it was: " + run.text());
    }

    /**
     * The report file says what the run said. Without the switch its PDF/A row is the
     * declaration; with one it carries the validator, its version and its verdict, and a
     * file the validator rejects is an invalid container there as in the printed lines.
     */
    @Test
    void theReportFileCarriesWhatTheValidatorSaid() {
        String pdf = hybrid();
        Path declared = directory.resolve("declared.html");
        Path accepted = directory.resolve("accepted.html");
        Path rejected = directory.resolve("rejected.html");

        Cli.run("validate", pdf, "--report", declared.toString(), "--report-lang", "en");
        Cli.run("validate", pdf, "--verapdf", validator(passing(), 0),
                "--report", accepted.toString(), "--report-lang", "en");
        Cli.run("validate", pdf, "--verapdf", validator(failing(), 1),
                "--report", rejected.toString(), "--report-lang", "en");

        assertTrue(read(declared).contains("declared, not validated"),
                "nothing checked the claim, and the report says so");
        assertTrue(read(accepted).contains("veraPDF " + VERSION),
                "the report names whose answer it is");
        assertTrue(read(accepted).contains("PASS"), "and what the answer was");
        assertTrue(read(accepted).contains("<span>Container:</span> OK"),
                "the container of an accepted file is sound in the report");
        assertTrue(read(rejected).contains("FAIL (2 failed rules, 7 failed checks)"),
                "with the counts the validator gave");
        assertTrue(read(rejected).contains("<span>Container:</span> INVALID"),
                "and a file the validator rejects is wrong about itself in the report too");
    }

    /** Returns the text of a file this test wrote. */
    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The JSON report carries the same three facts, under members a program reads. */
    @Test
    void theJsonReportCarriesWhatTheValidatorSaid() {
        String pdf = hybrid();
        String validator = validator(passing(), 0);

        Cli.Run run = Cli.run("validate", pdf, "--verapdf", validator, "--output", "json");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.text() + run.err());
        assertTrue(run.text().contains("\"validated\": true"), run.text());
        assertTrue(run.text().contains("\"name\": \"veraPDF\""), run.text());
        assertTrue(run.text().contains("\"version\": \"" + VERSION + "\""), run.text());
        assertTrue(run.text().contains("\"profile\": \"" + PROFILE + "\""), run.text());
        assertTrue(run.text().contains("\"compliant\": true"), run.text());
    }

    /** A run without the switch says in the machine report that nothing validated. */
    @Test
    void theJsonReportOfARunWithoutTheSwitchSaysSo() {
        Cli.Run run = Cli.run("validate", hybrid(), "--output", "json");
        assertTrue(run.text().contains("\"validated\": false"), run.text());
        assertTrue(run.text().contains("\"pdfaValidator\": null"), run.text());
    }

    /** An installation that is not one is an answer about the command line. */
    @Test
    void aPathThatHoldsNoValidatorIsRefused() {
        Cli.Run run = Cli.run("validate", hybrid(), "--verapdf",
                directory.resolve("nowhere").toString());
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("--verapdf names"), run.err());
    }

    /** A directory that is an installation without the executable says which one. */
    @Test
    void aDirectoryWithoutTheExecutableSaysWhatItLooksFor() {
        Cli.Run run = Cli.run("validate", hybrid(), "--verapdf", directory.toString());
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("which holds no verapdf"), run.err());
    }

    /** A validator that writes no report this tool can read is said to have written none. */
    @Test
    void aValidatorThatWritesNoReportIsReportedAsSuch() {
        Cli.Run run = Cli.run("validate", hybrid(), "--verapdf",
                validator("not a report at all", 0));
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains("no report this tool can read"), run.err());
    }

    /** An input that is no container has no PDF/A conformance to validate. */
    @Test
    void anInputThatIsNoContainerIsToldThatNothingWasValidated() {
        String invoice = Fixtures.file(directory, EXAMPLE);

        Cli.Run run = Cli.run("validate", invoice, "--verapdf", validator(passing(), 0));
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("is not one; nothing was validated"), run.err());
    }

    /** A validator that outlives the time this run was given leaves no verdict. */
    @Test
    void aValidatorThatOutlivesTheDeadlineIsALimitAndNoVerdict() {
        Cli.Run run = Cli.run("validate", hybrid(), "--verapdf", sleeper(),
                "--max-runtime", "1s");
        assertEquals(ExitCode.LIMIT, run.exitCode(), run.text() + run.err());
    }

    /**
     * {@code esj embed} writes the PDF/A-3 claim of the file it produces, so it is a
     * command that can be asked to check the claim it inherits. A file the validator
     * rejects is refused before anything is written into it.
     */
    @Test
    void embedRefusesAFileTheValidatorRejects() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        assertEquals(ExitCode.SUCCESS, Cli.run("render", invoice, "--out", pages).exitCode());

        Cli.Run refused = Cli.run("embed", pages, invoice, "--verapdf",
                validator(failing(), 1), "--out", directory.resolve("out.pdf").toString());

        assertEquals(ExitCode.INPUT, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("does not agree"), refused.err());
        assertTrue(refused.err().contains("FAIL (2 failed rules"), refused.err());
    }

    /** A file the validator accepts is embedded into, as it would be without the switch. */
    @Test
    void embedWritesTheFileTheValidatorAccepts() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pages = directory.resolve("pages.pdf").toString();
        assertEquals(ExitCode.SUCCESS, Cli.run("render", invoice, "--out", pages).exitCode());
        String out = directory.resolve("out.pdf").toString();

        Cli.Run run = Cli.run("embed", pages, invoice, "--verapdf", validator(passing(), 0),
                "--out", out);

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(Files.exists(Path.of(out)), "the hybrid invoice was written");
    }

    /** {@code render --embed cii} checks its own rendering before it writes into it. */
    @Test
    void renderAndEmbedChecksTheRenderingItEmbedsInto() {
        String invoice = Fixtures.file(directory, EXAMPLE);

        Cli.Run refused = Cli.run("render", invoice, "--embed", "cii", "--verapdf",
                validator(failing(), 1), "--out", directory.resolve("out.pdf").toString());

        assertEquals(ExitCode.INPUT, refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("does not agree"), refused.err());
    }

    /** A run that embeds nothing has nothing for the validator, and is told so. */
    @Test
    void renderWithoutEmbeddingSaysThatNothingWasValidated() {
        String invoice = Fixtures.file(directory, EXAMPLE);

        Cli.Run run = Cli.run("render", invoice, "--verapdf", validator(passing(), 0),
                "--out", directory.resolve("out.pdf").toString());

        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertTrue(run.err().contains("nothing was validated"), run.err());
    }

    /** Renders the example and embeds it, which is the file these tests validate. */
    private String hybrid() {
        String invoice = Fixtures.file(directory, EXAMPLE);
        String pdf = directory.resolve("hybrid.pdf").toString();
        Cli.Run rendered = Cli.run("render", invoice, "--embed", "cii", "--out", pdf);
        assertEquals(ExitCode.SUCCESS, rendered.exitCode(), rendered.err());
        return pdf;
    }

    /** Writes a validator that prints the given report and leaves with the given code. */
    private String validator(String report, int exitCode) {
        return script("verapdf-" + exitCode + "-" + report.length(),
                "cat <<'ESJ_REPORT'\n" + report + "\nESJ_REPORT\nexit " + exitCode + "\n");
    }

    /** Writes a validator that never answers inside the time this run allows. */
    private String sleeper() {
        return script("verapdf-sleep", "sleep 30\n");
    }

    /** Writes an executable shell script into the temporary directory. */
    private String script(String name, String body) {
        Path file = directory.resolve(name);
        try {
            Files.writeString(file, "#!/bin/sh\n" + body, StandardCharsets.UTF_8);
            Files.setPosixFilePermissions(file,
                    PosixFilePermissions.fromString("rwx------"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file.toString();
    }
}
