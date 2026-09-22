package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.Esj;
import de.bsnsoft.esj.model.Registry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The help text, the version banner and what happens to a command line that says nothing
 * the tool can do.
 *
 * <p>The exit codes are asserted against the help text itself: a tool whose documented
 * codes and actual codes drift apart is worse than one that documents none, and the list
 * in the footer is the list a script is written from.
 */
class UsageTest {

    @Test
    void printsTheHelpTextWithTheCommandsAndTheExitCodes() {
        Cli.Run run = Cli.run("--help");
        assertEquals(ExitCode.SUCCESS, run.exitCode());
        String text = run.text();
        for (String command : new String[] {"convert", "upgrade", "validate", "render",
                                            "inspect", "extract", "get", "list", "diff",
                                            "canonicalize"}) {
            assertTrue(text.contains(command), "the help text lists " + command);
        }
        assertTrue(text.contains("Exit codes:"), text);
        assertTrue(text.contains("  " + ExitCode.SUCCESS + "  success"), text);
        assertTrue(text.contains("  " + ExitCode.VALIDATION + "  a validation found an error"),
                text);
        assertTrue(text.contains("  " + ExitCode.INPUT + "  the input could not be read"), text);
        assertTrue(text.contains("  " + ExitCode.CONSTRAINED + "  the conversion"), text);
        assertTrue(text.contains("  " + ExitCode.UNSUPPORTED + "  a feature"), text);
        assertTrue(text.contains("  " + ExitCode.INTERNAL + "  an internal error"), text);
        assertTrue(text.contains("  " + ExitCode.OUTPUT + "  the output could not be written"),
                text);
        assertTrue(text.contains("  " + ExitCode.LIMIT + "  a resource or time limit"), text);
        assertTrue(text.contains("  " + ExitCode.INDETERMINATE + "  nothing fatal was found"),
                text);
    }

    @ParameterizedTest(name = "esj {0} --help")
    @ValueSource(strings = {"convert", "upgrade", "validate", "render", "inspect", "get",
                            "list", "diff", "canonicalize"})
    void printsAHelpTextForEveryCommand(String command) {
        Cli.Run run = Cli.run(command, "--help");
        assertEquals(ExitCode.SUCCESS, run.exitCode());
        assertTrue(run.text().startsWith("Usage: esj " + command), run.text());
        assertTrue(run.text().contains("--verbose"), run.text());
        assertTrue(run.text().contains("--debug"), run.text());
        assertTrue(run.text().contains("--limits"), run.text());
        assertTrue(run.text().contains("--max-runtime"), run.text());
    }

    @Test
    void printsTheThreeVersionsAndTheEditionsThisBuildCarries() {
        Cli.Run run = Cli.run("--version");
        assertEquals(ExitCode.SUCCESS, run.exitCode());
        String[] lines = run.lines();
        assertTrue(lines[0].startsWith("esj "), lines[0]);
        assertEquals("ESJ format version " + Esj.VERSION, lines[1]);
        assertEquals("semantic model " + Esj.SEMANTIC_MODEL, lines[2]);
        assertEquals("semantic model registries " + String.join(", ", Registry.editions()),
                lines[3],
                "which editions a copy of the tool carries is a property of that copy");
    }

    @Test
    void refusesACommandLineThatNamesNothingToDo() {
        Cli.Run run = Cli.run();
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("Usage: esj"), run.err());
        assertEquals(0, run.out().length, "a usage text of a mistake goes to the error stream");
    }

    @Test
    void refusesACommandItDoesNotHave() {
        Cli.Run run = Cli.run("transmogrify", "x");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("error: "), run.err());
    }

    @Test
    void refusesAnOptionItDoesNotHave() {
        Cli.Run run = Cli.run("convert", "--lossless", "-");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("--lossless"), run.err());
    }

    @Test
    void refusesTwoFormsOfTheSameOutputAtOnce() {
        Cli.Run run = Cli.run("convert", "-", "--pretty", "--canonical");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("mutually exclusive"), run.err());
    }

    @Test
    void printsNoStackTraceWithoutDebugAndOneWithIt() {
        Cli.Run quiet = Cli.run("convert", "no-such-file.esj.json");
        assertEquals(ExitCode.INPUT, quiet.exitCode());
        assertEquals(1, quiet.err().lines().count(), quiet.err());

        Cli.Run loud = Cli.run("convert", "--debug", "no-such-file.esj.json");
        assertEquals(ExitCode.INPUT, loud.exitCode());
        assertTrue(loud.err().contains("CliException"), loud.err());
        assertTrue(loud.err().contains("\tat "), loud.err());
    }

    @Test
    void acceptsAGlobalFlagBeforeAndAfterTheCommand() {
        for (String[] args : new String[][] {
                {"--verbose", "convert", "no-such-file.esj.json"},
                {"convert", "--verbose", "no-such-file.esj.json"}}) {
            Cli.Run run = Cli.run(args);
            assertEquals(ExitCode.INPUT, run.exitCode());
            assertTrue(run.err().contains("no such file"), run.err());
        }
    }
}
