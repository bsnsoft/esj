package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The command lines of {@code esj serve} and {@code esj mcp}: their help, the settings they
 * refuse before anything listens, and {@code esj mcp} answering over the streams of the
 * process. What the servers do is the subject of the tests in {@code …cli.serve}.
 */
class ServeCommandTest {

    @TempDir
    private Path directory;

    @Test
    void bothCommandsPrintTheirHelp() {
        Cli.Run serve = Cli.run("serve", "--help");
        assertEquals(ExitCode.SUCCESS, serve.exitCode());
        for (String option : new String[] {"--bind", "--port", "--token-file", "--allow-origin",
                                           "--allow-dir", "--templates", "--packs", "--limits",
                                           "--max-jobs", "--max-queue", "--job-heap",
                                           "--job-timeout", "--max-upload", "--ttl"}) {
            assertTrue(serve.text().contains(option), "esj serve --help names " + option);
        }
        Cli.Run mcp = Cli.run("mcp", "--help");
        assertEquals(ExitCode.SUCCESS, mcp.exitCode());
        assertTrue(mcp.text().startsWith("Usage: esj mcp"), mcp.text());
        assertTrue(Cli.run("--help").text().contains("serve"));
    }

    @Test
    void aSettingThatCannotHoldIsRefusedBeforeAnythingListens() throws Exception {
        assertRefused(Cli.run("serve", "--job-heap", "a lot"), "--job-heap");
        assertRefused(Cli.run("serve", "--port", "70000"), "--port");
        assertRefused(Cli.run("serve", "--max-jobs", "0"), "--max-jobs");
        assertRefused(Cli.run("serve", "--limits", "huge"), "--limits");
        assertRefused(Cli.run("serve", "--token-file", directory.resolve("missing").toString()),
                "token file");
        Path empty = Files.writeString(directory.resolve("empty"), "\n");
        assertRefused(Cli.run("serve", "--token-file", empty.toString()), "holds no token");
        assertRefused(Cli.runWith(Map.of("ESJ_TOKEN_FILE", empty.toString()), "serve"),
                "holds no token");
        assertRefused(Cli.run("serve", "--allow-dir", directory.resolve("nowhere").toString()),
                "--allow-dir");
        assertRefused(Cli.run("mcp", "--templates", directory.resolve("nowhere").toString()),
                "--templates");
    }

    @Test
    void theStdioServerAnswersOnTheStandardOutputAndSpeaksOnTheErrorStream() {
        byte[] input = ("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"}\n"
                + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n")
                .getBytes(StandardCharsets.UTF_8);
        Cli.Run run = Cli.run(input, "mcp");
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{}}\n", run.text());
        assertTrue(run.err().startsWith("esj mcp "), run.err());
    }

    private static void assertRefused(Cli.Run run, String about) {
        assertEquals(ExitCode.INPUT, run.exitCode(), run.err());
        assertTrue(run.err().contains(about), run.err());
    }
}
