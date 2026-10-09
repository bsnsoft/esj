package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code docs/serve.md} held against the server: its curl transcripts are run against
 * {@code esj serve}, its tables name the tools, parameters, endpoints and statuses the server
 * has, and its configuration snippets start what they say they start.
 */
class ServeDocsTest {

    /** The page. */
    private static final String PAGE = "docs/serve.md";

    /** The address the page writes. */
    private static final String ADDRESS = "http://127.0.0.1:8080";

    /** A backticked word. */
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    @TempDir
    private Path temp;

    /** Every curl line of a console block, with the output the page shows for it. */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    void theTranscriptsAreWhatTheServerAnswers() throws Exception {
        Assumptions.assumeTrue(onPath("curl"), "curl is installed");
        List<String[]> transcripts = transcripts(ServeFixture.bytes(PAGE));
        assertEquals(3, transcripts.size(), "the transcripts of the page");
        Path root = ServeFixture.file("examples").getParent();
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            for (String[] transcript : transcripts) {
                String command = transcript[0].replace(ADDRESS, http.url());
                Process process = new ProcessBuilder("sh", "-c", command)
                        .directory(root.toFile())
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                assertTrue(process.waitFor(2, TimeUnit.MINUTES));
                assertEquals(0, process.exitValue(), command + "\n" + output);
                assertTrue(matches(transcript[1].lines().toList(), output.lines().toList()),
                        command + "\nexpected:\n" + transcript[1] + "\nwas:\n" + output);
            }
        }
    }

    @Test
    void theToolTableNamesEveryToolItsEndpointAndItsParameters() {
        Map<String, List<String>> rows = rows(table("| Tool |"));
        List<Tools.Tool> tools = Tools.all(Tools.Mode.HTTP, List.of("a-template"));
        assertEquals(tools.stream().map(Tools.Tool::name).toList(),
                new ArrayList<>(rows.keySet()));
        for (Tools.Tool tool : tools) {
            List<String> row = rows.get(tool.name());
            String endpoint = "upload".equals(tool.name()) ? "POST /api/documents"
                    : "POST /api/" + tool.name();
            assertEquals("`" + endpoint + "`", row.get(2).strip());
            Set<String> named = new TreeSet<>();
            Matcher matcher = CODE.matcher(row.get(3));
            while (matcher.find()) {
                named.add(matcher.group(1));
            }
            Set<String> params = new TreeSet<>();
            tool.params().forEach(param -> params.add(param.name()));
            assertEquals(params, named, "the parameters of " + tool.name());
        }
    }

    @Test
    void theStatusTableIsTheMappingOfTheServer() {
        Map<String, List<String>> rows = rows(table("| Child exit code |"));
        int covered = 0;
        for (Map.Entry<String, List<String>> row : rows.entrySet()) {
            int http = Integer.parseInt(row.getValue().get(3).strip());
            boolean result = "result".equals(row.getValue().get(4).strip());
            if (row.getKey().startsWith("any other")) {
                for (int code : new int[] {10, 99, 127, 134, 137, 143}) {
                    assertEquals(http, Outcome.Status.ofExitCode(code).http(), "exit " + code);
                }
                assertFalse(result);
                covered++;
                continue;
            }
            Matcher numbers = Pattern.compile("\\b([0-9])\\b").matcher(row.getKey());
            while (numbers.find()) {
                int code = Integer.parseInt(numbers.group(1));
                covered++;
                if (code == 0 || code == 1 || code == 9 || code == 8) {
                    assertEquals(200, http, "exit " + code);
                    assertTrue(result, "exit " + code);
                } else {
                    assertEquals(http, Outcome.Status.ofExitCode(code).http(), "exit " + code);
                    assertFalse(result, "exit " + code);
                }
            }
            if (row.getKey().contains("killed")) {
                assertEquals(Outcome.Status.LIMIT.http(), http);
            }
        }
        assertEquals(11, covered, "every code of the exit code table, and the rest");
    }

    @Test
    void theEndpointsOfThePageAreThoseOfTheOpenApiDescription() throws Exception {
        Set<String> page = new LinkedHashSet<>();
        for (List<String> row : rows(table("| Tool |")).values()) {
            page.add(row.get(2).strip().replace("`POST ", "").replace("`", ""));
        }
        page.add("/api/artifacts/{id}");
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            Jv.Obj openapi = ServeFixture.json(ServeFixture.get(http.url() + "/openapi.json"));
            assertEquals(new TreeSet<>(page), new TreeSet<>(((Jv.Obj) openapi.get("paths")
                    .orElseThrow()).members().keySet()));
        }
        assertTrue(text().contains("`GET /api/artifacts/<id>`"));
    }

    @Test
    void theConfigurationSnippetsStartTheStdioServer() {
        String page = text();
        int start = page.indexOf("```json\n") + "```json\n".length();
        Jv.Obj config = (Jv.Obj) Jv.parse(page.substring(start, page.indexOf("\n```", start))
                .getBytes(StandardCharsets.UTF_8), 4096);
        Jv.Obj server = (Jv.Obj) ((Jv.Obj) config.get("mcpServers").orElseThrow()).get("esj")
                .orElseThrow();
        assertTrue(server.string("command").orElseThrow().endsWith("/esj"));
        assertEquals(List.of(Jv.of("mcp")), ((Jv.Arr) server.get("args").orElseThrow()).items());
        assertTrue(page.contains("claude mcp add esj -- esj mcp"));
        assertTrue(page.contains("claude mcp add --transport http esj " + ADDRESS + "/mcp"));
    }

    @Test
    void theCompatibilityPageNamesTheServersAsAPreview() {
        String page = new String(ServeFixture.bytes("docs/compatibility.md"),
                StandardCharsets.UTF_8);
        assertTrue(page.contains("`esj serve`") && page.contains("`esj mcp`")
                && page.contains("serve.md"), "docs/compatibility.md names both commands");
    }

    private static String text() {
        return new String(ServeFixture.bytes(PAGE), StandardCharsets.UTF_8);
    }

    /** Returns the lines of the table whose header starts with the given text. */
    private static List<String> table(String header) {
        List<String> lines = new ArrayList<>();
        boolean in = false;
        for (String line : text().lines().toList()) {
            if (line.startsWith(header)) {
                in = true;
            } else if (in && !line.startsWith("|")) {
                break;
            } else if (in && !line.startsWith("|---")) {
                lines.add(line);
            }
        }
        assertFalse(lines.isEmpty(), "the page has the table " + header);
        return lines;
    }

    /** Splits table lines into cells, by the text of the first cell. */
    private static Map<String, List<String>> rows(List<String> lines) {
        Map<String, List<String>> rows = new LinkedHashMap<>();
        for (String line : lines) {
            List<String> cells = new ArrayList<>(List.of(line.split("(?<!\\\\)\\|", -1)));
            String key = cells.get(1).strip().replace("`", "");
            rows.put(key, cells);
        }
        return rows;
    }

    /** Returns every {@code $ curl} line of the console blocks with the output shown for it. */
    private static List<String[]> transcripts(byte[] page) {
        List<String[]> transcripts = new ArrayList<>();
        boolean console = false;
        StringBuilder command = null;
        StringBuilder output = null;
        boolean continued = false;
        for (String line : new String(page, StandardCharsets.UTF_8).lines().toList()) {
            if (line.equals("```console")) {
                console = true;
                continue;
            }
            if (console && line.equals("```")) {
                if (command != null) {
                    transcripts.add(new String[] {command.toString(), output.toString()});
                }
                console = false;
                command = null;
                continue;
            }
            if (!console) {
                continue;
            }
            if (continued) {
                command.append(' ').append(line.strip().replaceAll("\\\\$", ""));
                continued = line.endsWith("\\");
            } else if (line.startsWith("$ ")) {
                if (command != null) {
                    transcripts.add(new String[] {command.toString(), output.toString()});
                }
                command = new StringBuilder(line.substring(2).replaceAll("\\\\$", ""));
                output = new StringBuilder();
                continued = line.endsWith("\\");
            } else {
                output.append(line).append('\n');
            }
        }
        return transcripts;
    }

    /** Matches output against the lines of a page, a line of three dots standing for any. */
    static boolean matches(List<String> expected, List<String> actual) {
        return matches(expected, 0, actual, 0);
    }

    private static boolean matches(List<String> expected, int e, List<String> actual, int a) {
        if (e == expected.size()) {
            return a == actual.size();
        }
        if ("...".equals(expected.get(e))) {
            for (int skip = a; skip <= actual.size(); skip++) {
                if (matches(expected, e + 1, actual, skip)) {
                    return true;
                }
            }
            return false;
        }
        return a < actual.size() && expected.get(e).equals(actual.get(a))
                && matches(expected, e + 1, actual, a + 1);
    }

    private static boolean onPath(String command) {
        try {
            Process process = new ProcessBuilder("sh", "-c", "command -v " + command)
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
