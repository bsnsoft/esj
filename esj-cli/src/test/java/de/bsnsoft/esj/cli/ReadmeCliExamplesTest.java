package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every command line example of the four pages that show this tool, run: {@code README.md},
 * {@code docs/getting-started.md}, {@code docs/cli.md} and {@code docs/b2c.md}.
 *
 * <p>The pages show what the tool prints. This test runs the same commands through
 * {@link Main#run(String[], java.io.InputStream, java.io.OutputStream, java.io.OutputStream)}
 * and compares the two, so that the section cannot describe a tool that behaves differently —
 * a wrong exit code, a renamed option, a moved line, a digest that changed because the
 * canonical form did. No subprocess is started and nothing is regenerated: the README is the
 * expectation, and a change to the tool that the page does not follow fails the build.
 *
 * <p>The pages are read from the classpath, where the build copies them to, and the examples
 * are the fenced {@code console} blocks inside them. A block is a transcript:
 *
 * <ul>
 *   <li>a line that begins with {@code $ } is a command, continued on the next line where it
 *       ends with a backslash;
 *   <li>the lines after it, up to the next command or the end of the block, are what the
 *       command wrote — the standard output first, then the standard error stream, which is
 *       the order the tool flushes them in;
 *   <li>a line of exactly three dots stands for output the page leaves out: the next expected
 *       line is then searched for rather than required at that position, and a block that ends
 *       with one says nothing about the rest of the output.
 * </ul>
 *
 * <p>Three command forms are understood, and they are the three the pages use:
 * {@code esj <arguments>}, {@code cat <file> | esj <arguments>}, which puts the bytes of that
 * file on the standard input, and {@code echo $?}, whose one line of expected output is the
 * exit code of the command before it.
 *
 * <p>An argument naming a file of {@code examples/} or {@code conformance/} is written into a
 * temporary directory under the same relative path and passed as the absolute path of that
 * copy, because the tool reads a file system and a test knows no path outside its module. A
 * file the page has the tool <em>write</em>, with {@code --out} or {@code --report}, goes into
 * the same directory,
 * and a later command of the same block that names it reads it there. The tool prints the
 * argument it was given, so the directory is stripped from the output again before the
 * comparison, which is what lets the page show the short name a reader would type at the root
 * of the repository.
 */
class ReadmeCliExamplesTest {

    /** The pages whose transcripts are run, in the order a reader meets them. */
    private static final List<String> PAGES = List.of("README.md",
            "docs/getting-started.md", "docs/cli.md", "docs/b2c.md");

    /** The page that carries the complete reference, the exit codes included. */
    private static final String REFERENCE = "docs/cli.md";

    /** The fence that opens a transcript. */
    private static final String FENCE = "```console";

    /** The line that stands for output the page leaves out. */
    private static final String ELISION = "...";

    /** The prompt a command line begins with. */
    private static final String PROMPT = "$ ";

    /** The command that prints the exit code of the command before it. */
    private static final String ECHO_STATUS = "echo $?";

    /** The heading the exit code table of the reference stands under. */
    private static final String EXIT_CODES = "## Exit codes";

    /** The directories of the repository a fixture argument may name. */
    private static final List<String> FIXTURES = List.of("examples/", "conformance/");

    /**
     * Where the render templates of the repository sit. A template names the files it
     * needs beside it — a letterhead, a logo, a font — and reads them out of its own
     * directory, so a command line that names one needs the directory and not the one
     * file.
     */
    private static final String TEMPLATES = "examples/templates/";

    /** The subcommands the section is expected to show at work. */
    private static final Set<String> COMMANDS = Set.of("convert", "validate", "render",
            "embed", "inspect", "extract", "get", "list", "diff", "canonicalize");

    /** The options whose argument is a file the command writes rather than reads. */
    private static final Set<String> DESTINATIONS = Set.of("--out", "--report");

    @TempDir
    private Path directory;

    /** The files this block had the tool write, by the name the page shows for each. */
    private final Map<String, String> written = new LinkedHashMap<>();

    /** The transcripts of every page, in the order they are printed. */
    static List<Block> blocks() {
        List<Block> blocks = new ArrayList<>();
        for (String page : PAGES) {
            List<Block> found = parse(page, Fixtures.text(page));
            assertFalse(found.isEmpty(), page + " shows " + FENCE + " examples");
            blocks.addAll(found);
        }
        return blocks;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blocks")
    void printsWhatTheReadmeSaysItPrints(Block block) {
        int status = 0;
        for (Step step : block.steps()) {
            if (ECHO_STATUS.equals(step.command())) {
                assertEquals(List.of(String.valueOf(status)), step.expected(),
                        "the page shows the exit code the command before it reached");
                continue;
            }
            Cli.Run run = execute(step.command());
            status = run.exitCode();
            assertMatches(step, strip(run.text() + run.err()));
        }
    }

    @Test
    void showsEveryCommandOfTheToolAtWork() {
        Set<String> shown = new LinkedHashSet<>();
        for (Block block : blocks()) {
            for (Step step : block.steps()) {
                subcommand(step.command()).filter(COMMANDS::contains).ifPresent(shown::add);
            }
        }
        assertEquals(COMMANDS, shown, PAGES + " show every command at work");
    }

    /**
     * The complete table stands in {@code docs/cli.md}; the README points at it and names the
     * two codes a caller is most likely to confuse. The reference is what is checked here,
     * because it is the page a script is written from.
     */
    @Test
    void listsEveryExitCodeTheToolCanLeaveWith() {
        String section = section(Fixtures.text(REFERENCE), EXIT_CODES);
        for (int code : new int[] {ExitCode.SUCCESS, ExitCode.VALIDATION, ExitCode.INPUT,
                                   ExitCode.UNSUPPORTED, ExitCode.INTERNAL, ExitCode.OUTPUT,
                                   ExitCode.LIMIT, ExitCode.CONSTRAINED,
                                   ExitCode.INDETERMINATE}) {
            assertTrue(section.contains("| " + code + " | "),
                    "the table under " + EXIT_CODES + " of " + REFERENCE + " lists " + code);
        }
    }

    /** Returns the subcommand a command line names, where it names one. */
    private static Optional<String> subcommand(String command) {
        int esj = command.indexOf("esj ");
        if (esj < 0) {
            return Optional.empty();
        }
        String[] tokens = command.substring(esj + "esj ".length()).trim().split("\\s+");
        return tokens.length == 0 || tokens[0].isEmpty()
                ? Optional.empty()
                : Optional.of(tokens[0]);
    }

    /** Runs one command line of a transcript and returns what it produced. */
    private Cli.Run execute(String command) {
        int pipe = command.indexOf('|');
        if (pipe < 0) {
            return Cli.run(arguments(command).toArray(String[]::new));
        }
        List<String> producer = List.of(command.substring(0, pipe).trim().split("\\s+"));
        assertEquals("cat", producer.get(0), "the only producer the page pipes from is cat");
        assertEquals(2, producer.size(), "cat is given one file");
        byte[] stdin = Fixtures.bytes(producer.get(1));
        return Cli.run(stdin, arguments(command.substring(pipe + 1)).toArray(String[]::new));
    }

    /**
     * Returns the arguments of an {@code esj} command line, with every fixture argument
     * replaced by the path of the copy this test wrote.
     */
    private List<String> arguments(String command) {
        String text = command.trim();
        assertTrue(text.startsWith("esj "), "a command of the page is an esj command: " + text);
        List<String> arguments = new ArrayList<>();
        String previous = "";
        for (String token : text.substring("esj ".length()).trim().split("\\s+")) {
            if (DESTINATIONS.contains(previous) && !Input.STDIN_ARGUMENT.equals(token)) {
                // A file the command writes: the page shows the name a reader would type,
                // and the test puts it in the temporary directory, which is the one place a
                // test may write. It is remembered so that a later command of the same
                // block that reads it finds it there, and the prefix is stripped from the
                // output again.
                written.put(token, directory + "/" + token);
                arguments.add(directory + "/" + token);
            } else {
                arguments.add(isFixture(token) ? copy(token)
                        : written.getOrDefault(token, token));
            }
            previous = token;
        }
        return arguments;
    }

    private static boolean isFixture(String token) {
        return FIXTURES.stream().anyMatch(token::startsWith);
    }

    /**
     * Writes a fixture of the classpath into the temporary directory under its own relative
     * path and returns the path to pass on a command line.
     */
    private String copy(String resource) {
        String argument = directory + "/" + resource;
        Path target = Path.of(argument);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, Fixtures.bytes(resource));
            if (resource.startsWith(TEMPLATES)) {
                copyBeside(target.getParent());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return argument;
    }

    /** Copies every file of the template directory beside the template that was named. */
    private void copyBeside(Path into) throws IOException {
        Path source;
        try {
            source = Path.of(ReadmeCliExamplesTest.class.getResource("/" + TEMPLATES).toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        try (java.util.stream.Stream<Path> files = Files.list(source)) {
            for (Path file : files.toList()) {
                Files.write(into.resolve(file.getFileName().toString()),
                        Files.readAllBytes(file));
            }
        }
    }

    /** Removes the temporary directory from the output, leaving the paths the page shows. */
    private String strip(String output) {
        return output.replace(directory + "/", "");
    }

    /** Asserts that the output is what the transcript shows, honouring its elisions. */
    private static void assertMatches(Step step, String output) {
        List<String> actual = lines(output);
        int at = 0;
        boolean elided = false;
        for (String expected : step.expected()) {
            if (ELISION.equals(expected)) {
                elided = true;
                continue;
            }
            if (elided) {
                int found = actual.subList(at, actual.size()).indexOf(expected);
                assertTrue(found >= 0, () -> report(step, output,
                        "the line '" + expected + "' is not in what the command wrote"));
                at += found + 1;
                elided = false;
            } else {
                int position = at;
                assertTrue(at < actual.size(), () -> report(step, output,
                        "the command wrote nothing where the page shows '" + expected + "'"));
                assertEquals(expected, actual.get(at),
                        () -> report(step, output, "line " + (position + 1) + " differs"));
                at++;
            }
        }
        if (!elided) {
            int written = at;
            assertEquals(actual.size(), at, () -> report(step, output,
                    "the command wrote " + (actual.size() - written) + " line(s) the page does"
                            + " not show; end the block with '" + ELISION + "' to leave them out"));
        }
    }

    /** Returns the lines of an output, without the empty one a trailing line feed makes. */
    private static List<String> lines(String output) {
        if (output.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(List.of(output.split("\n", -1)));
        if (lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /** Returns the text of one section of a page. */
    private static String section(String page, String heading) {
        int start = page.indexOf(heading);
        assertTrue(start >= 0, REFERENCE + " has a section '" + heading + "'");
        int end = page.indexOf("\n## ", start + heading.length());
        return end < 0 ? page.substring(start) : page.substring(start, end);
    }

    /** Returns the transcripts of a page, one per fenced block. */
    private static List<Block> parse(String page, String section) {
        List<Block> blocks = new ArrayList<>();
        List<String> lines = List.of(section.split("\n", -1));
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).equals(FENCE)) {
                continue;
            }
            int end = i + 1;
            while (end < lines.size() && !lines.get(end).equals("```")) {
                end++;
            }
            assertTrue(end < lines.size(),
                    "the block opened at line " + (i + 1) + " of " + page + " is closed");
            blocks.add(block(page, lines.subList(i + 1, end)));
            i = end;
        }
        return blocks;
    }

    /** Returns one transcript: its commands, each with the output the page shows for it. */
    private static Block block(String page, List<String> body) {
        List<Step> steps = new ArrayList<>();
        StringBuilder command = null;
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            String line = body.get(i);
            if (!line.startsWith(PROMPT)) {
                assertTrue(command != null, "a block begins with a command: " + line);
                expected.add(line);
                continue;
            }
            if (command != null) {
                steps.add(new Step(command.toString(), List.copyOf(expected)));
                expected.clear();
            }
            command = new StringBuilder(line.substring(PROMPT.length()));
            while (command.charAt(command.length() - 1) == '\\') {
                command.setLength(command.length() - 1);
                line = body.get(++i);
                command.append(line.trim());
            }
        }
        assertTrue(command != null, "a block shows at least one command");
        steps.add(new Step(command.toString(), List.copyOf(expected)));
        return new Block(page, List.copyOf(steps));
    }

    /** Returns a failure message that shows the command, the page and what was written. */
    private static String report(Step step, String output, String what) {
        return what + "\n\ncommand: " + step.command()
                + "\n\nthe page shows:\n" + String.join("\n", step.expected())
                + "\n\nthe command wrote:\n" + output;
    }

    /**
     * One command of a transcript.
     *
     * @param command  the command line, without the prompt and with continuations joined
     * @param expected the lines the page shows for it, elisions included
     */
    record Step(String command, List<String> expected) { }

    /**
     * One fenced block of a page.
     *
     * @param page  the page it stands on
     * @param steps the commands it shows, in order
     */
    record Block(String page, List<Step> steps) {

        @Override
        public String toString() {
            return page + ": " + steps.get(0).command();
        }
    }
}
