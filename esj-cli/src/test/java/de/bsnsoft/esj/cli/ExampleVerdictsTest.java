package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What {@code esj validate} says about every document of {@code examples/}.
 *
 * <p>An example is read by someone who runs the first command of the README over it, so what
 * the tool answers about it is part of what the example teaches. The table of
 * {@code examples/README.md} writes that answer down per file, and this test runs the command
 * over each one and compares — the verdict word the tool prints and the exit code it leaves
 * with. A document that stops being valid, and a row that stops being true, both stop the
 * build.
 *
 * <p>The list of files is taken from the directory rather than from the table, and the table is
 * then required to name every one of them, so an example cannot be added without saying what
 * the tool makes of it. The one document of the later edition is absent from a build that
 * leaves that edition out, and only that one may be named by the table and missing from the
 * directory.
 *
 * <p>Three of the examples are documents about the structure of the format rather than invoices
 * anybody would send, and the business rules reject them; {@code examples/README.md} says which
 * and why, and the rows below are how that statement is kept honest.
 */
class ExampleVerdictsTest {

    /** The directory of the examples, as the build copies it onto the test classpath. */
    private static final String EXAMPLES = "examples";

    /** The one document that a build without the later edition does not carry. */
    private static final String LATER_EDITION = "edition-2026.esj.json";

    /** A row of the table: a file in backticks, then its verdict in backticks. */
    private static final Pattern ROW =
            Pattern.compile("^\\| `([^`]+\\.esj\\.json)` \\| `(VALID|INVALID|INDETERMINATE)` \\|");

    /** The exit code that goes with each verdict word (docs/cli.md). */
    private static final Map<String, Integer> CODES = Map.of(
            "VALID", ExitCode.SUCCESS,
            "INVALID", ExitCode.VALIDATION,
            "INDETERMINATE", ExitCode.INDETERMINATE);

    @TempDir
    private Path directory;

    /** The verdicts the table of {@code examples/README.md} states, in the order it states them. */
    private static Map<String, String> table() {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String line : Fixtures.text(EXAMPLES + "/README.md").split("\\R")) {
            Matcher matcher = ROW.matcher(line);
            if (matcher.find()) {
                rows.put(matcher.group(1), matcher.group(2));
            }
        }
        assertFalse(rows.isEmpty(), "examples/README.md carries the table of verdicts");
        return rows;
    }

    /** The pretty documents of {@code examples/} itself, canonical twins and negatives aside. */
    private static List<String> documents() {
        URL url = ExampleVerdictsTest.class.getResource("/" + EXAMPLES);
        assertTrue(url != null, "the examples are on the test classpath");
        Path root;
        try {
            root = Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> root.relativize(file).toString().replace(File.separatorChar, '/'))
                    .filter(file -> file.endsWith(".esj.json"))
                    .filter(file -> !file.endsWith(".canonical.esj.json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String verdictLine(String output) {
        String[] lines = output.split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            if (!lines[index].isBlank()) {
                return lines[index].strip();
            }
        }
        return "";
    }

    @Test
    void theTableNamesEveryExampleAndNothingElse() {
        Map<String, String> table = table();
        List<String> present = documents();

        assertEquals(List.of(), present.stream().filter(file -> !table.containsKey(file)).toList(),
                "examples/README.md states a verdict for every document of examples/");
        List<String> missing = new ArrayList<>(table.keySet());
        missing.removeAll(present);
        assertEquals(present.contains(LATER_EDITION) ? List.of() : List.of(LATER_EDITION), missing,
                "the table names only documents this build carries; the one document of the later"
                        + " edition is the one a build without that edition leaves out");
    }

    @Test
    void everyExampleGetsTheVerdictTheTableStates() {
        Map<String, String> table = table();

        for (String file : documents()) {
            String verdict = table.get(file);
            Cli.Run run = Cli.run("validate", Fixtures.file(directory, EXAMPLES + "/" + file));

            String line = verdictLine(run.text());
            assertTrue(line.equals(verdict) || line.startsWith(verdict + " "),
                    file + " is documented as " + verdict + " and the tool says: " + line);
            assertEquals(CODES.get(verdict), run.exitCode(), file + ": " + run.err());
        }
    }

    /**
     * The smallest valid document is smallest by measurement: no term of it can go.
     *
     * <p>A document that calls itself the smallest valid one earns the name only if removing
     * any single value ends the verdict, so this removes each of them in turn and requires
     * that the tool no longer says valid.
     */
    @Test
    void theSmallestValidDocumentNeedsEveryTermItStates() {
        String source = Fixtures.text(EXAMPLES + "/smallest-valid.esj.json");
        List<String> paths = new ArrayList<>();
        Matcher matcher = Pattern.compile("^ {4}\"(/[^\"]+)\": ", Pattern.MULTILINE).matcher(source);
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        assertEquals(27, paths.size(), "the document states 27 values");

        for (String path : paths) {
            byte[] shortened = withoutValue(source, path)
                    .getBytes(StandardCharsets.UTF_8);

            Cli.Run run = Cli.run(shortened, "validate", "-");

            assertNotEquals(ExitCode.SUCCESS, run.exitCode(),
                    "without " + path + " the document is no longer valid: " + run.text());
        }
    }

    /** Returns the document with the one line of that path removed, still well-formed JSON. */
    private static String withoutValue(String source, String path) {
        List<String> kept = new ArrayList<>();
        for (String line : source.split("\\R")) {
            if (!line.startsWith("    \"" + path + "\": ")) {
                kept.add(line);
            }
        }
        assertEquals(source.split("\\R").length - 1, kept.size(), path + " is stated once");
        for (int index = 0; index < kept.size() - 1; index++) {
            if (kept.get(index + 1).strip().equals("}") && kept.get(index).endsWith(",")) {
                kept.set(index, kept.get(index).substring(0, kept.get(index).length() - 1));
            }
        }
        return String.join("\n", kept);
    }
}
