package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.model.Registry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The two FAQ pages, {@code docs/faq.md} and {@code docs/faq-de.md}, held against the tool
 * and against the pages they quote.
 *
 * <p>An FAQ is the page most likely to rot: it is short, it is written once, and it answers
 * a question with a command line that nobody runs again. So every line of it is run here.
 * A block tagged {@code sh} holds shell lines, and a line of one that begins with
 * {@code esj } is a command of this tool: the trailing comment is documentation and is cut
 * off, the placeholders are replaced by files of this repository, and what is left goes
 * through {@link Cli#run(String...)} in the process the test runs in. The code it leaves
 * with has to be the code the comment states — the digit after the word {@code exit},
 * {@code Exit} on the German page, and zero where the comment says nothing about it. A
 * renamed option, a command that grew a new requirement or a verdict that moved therefore
 * fails the build rather than the reader.
 *
 * <p>Two kinds of line in those blocks are not commands of this tool and are not run: the
 * {@code java -jar} line a service spawns it with, which {@code docs/deployment.md} shows
 * and {@code DeploymentExampleIT} holds, and the packaging script, which
 * {@code DistributionTest} holds. One command is skipped: {@code --verapdf} names an
 * installation of veraPDF, which this project never bundles, and what this tool does with
 * what such a validator says is asked in {@code VerapdfOptionTest}. That skip is counted,
 * once per page, so it cannot quietly grow. One line more goes unrun where a build leaves
 * an edition out: the one that names that edition with {@code --to}, which would fail for
 * the registry's absence rather than for the answer. A build that carries every edition
 * runs them all.
 *
 * <p>The placeholders the pages use stand for files of this repository, and the test passes
 * those: {@code invoice.xml} is the UBL of the first business case of the corpus,
 * {@code invoice.pdf} the hybrid PDF of the same invoice — which is what lets
 * {@code esj diff invoice.xml invoice.pdf} state that there is no difference —
 * {@code invoice.esj.json} the standard example, {@code letterhead.json} the letter
 * template with the files it reads beside it, and {@code pages.pdf} a PDF/A-3 file without
 * an invoice, which the test renders itself because that is the one thing
 * {@code esj embed} needs and no fixture is. An argument naming a file of {@code examples/}
 * or {@code conformance/} outright is copied the same way. A file the page has the tool
 * write, with {@code --out} or {@code --report}, goes into the temporary directory, and a
 * later command of the same block that names it reads it there.
 *
 * <p>The German page is not a translation that drifts: it shows the same commands in the
 * same order with the same exit codes, and the blocks it does not run — Java, SQL,
 * TypeScript, C# — stand on it byte for byte as they stand on the English page. Those
 * blocks are quoted rather than written: each one has to be an excerpt of a block of the
 * same language on a page a test of this repository already runs, so that an API that
 * changes is corrected in one place and the FAQ cannot answer with a call that no longer
 * compiles.
 */
class FaqExamplesTest {

    /** The page a reader meets first. */
    private static final String ENGLISH = "docs/faq.md";

    /** The same questions in German. */
    private static final String GERMAN = "docs/faq-de.md";

    /** The two pages this test holds. */
    private static final List<String> PAGES = List.of(ENGLISH, GERMAN);

    /**
     * The pages the blocks this test does not run are quoted from. Each of them is run by
     * a test of its own, which is what makes quoting them enough.
     */
    private static final List<String> SOURCES = List.of("README.md",
            "docs/getting-started.md", "docs/java-api.md", "docs/b2c.md", "docs/storage.md",
            "docs/bindings-ts.md", "docs/bindings-csharp.md");

    /** The three characters a fenced block opens and closes with. */
    private static final String FENCE = "```";

    /** The tag of a block that holds shell lines. */
    private static final String SHELL = "sh";

    /** The tags of the blocks that are quoted from another page rather than run. */
    private static final List<String> QUOTED = List.of("java", "sql", "ts", "csharp");

    /** What a line of a shell block begins with when it is a command of this tool. */
    private static final String COMMAND = "esj ";

    /** The option that names an installation of veraPDF, which this project never bundles. */
    private static final String VERAPDF = "--verapdf";

    /** How many lines of a page the veraPDF installation keeps the test from running. */
    private static final int SKIPPED = 1;

    /**
     * The option that names an edition. A distribution may be built without the registry of
     * an edition, and a line that names one this build does not carry is left out rather
     * than run: it would fail for the edition's absence and say nothing about the answer.
     * In a build that carries every edition nothing is left out here.
     */
    private static final String TO_EDITION = "--to";

    /** What a question line of these pages begins with. */
    private static final String QUESTION = "**";

    /** The word a comment names an exit code after, in either language. */
    private static final Pattern EXIT =
            Pattern.compile("\\bexit\\s+(\\d)", Pattern.CASE_INSENSITIVE);

    /** Every run of whitespace, which a quoted block is compared without. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** How much of a block a failure message shows. */
    private static final int HEAD = 80;

    /** The placeholders of the pages and the file of this repository each one stands for. */
    private static final Map<String, String> PLACEHOLDERS = Map.of(
            "invoice.xml", "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml",
            "invoice.pdf", "conformance/pdf/factur-x.pdf",
            "invoice.esj.json", "examples/standard-invoice.esj.json",
            "letterhead.json", "examples/templates/letter.json");

    /** The placeholder for a PDF/A-3 file without an invoice: the test renders that one. */
    private static final String PAGES_PDF = "pages.pdf";

    /** The document the pages of {@link #PAGES_PDF} are rendered from. */
    private static final String RENDERED_FROM = "examples/standard-invoice.esj.json";

    /** The directories of the repository a fixture argument may name outright. */
    private static final List<String> FIXTURES = List.of("examples/", "conformance/");

    /**
     * Where the render templates of the repository sit. A template names the letterhead,
     * the logo and the fonts it needs beside it and reads them out of its own directory,
     * so a command line that names one needs the directory and not the one file.
     */
    private static final String TEMPLATES = "examples/templates/";

    /** The options whose argument is a file the command writes rather than reads. */
    private static final Set<String> DESTINATIONS = Set.of("--out", "--report");

    @TempDir
    private Path directory;

    /** The files this block had the tool write, by the name the page shows for each. */
    private final Map<String, String> written = new LinkedHashMap<>();

    /** The shell blocks of both pages, in the order a reader meets them. */
    static List<Block> blocks() {
        List<Block> blocks = new ArrayList<>();
        for (String page : PAGES) {
            List<Block> found = new ArrayList<>();
            for (Fence fence : fences(page)) {
                if (SHELL.equals(fence.language())) {
                    assertFalse(fence.body().isEmpty(),
                            "a " + SHELL + " block of " + page + " shows a line");
                    found.add(new Block(page, fence.body()));
                }
            }
            assertFalse(found.isEmpty(), page + " shows " + FENCE + SHELL + " blocks");
            blocks.addAll(found);
        }
        return blocks;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blocks")
    void leavesWithTheExitCodeThePageStates(Block block) {
        for (String line : block.lines()) {
            if (!line.startsWith(COMMAND) || line.contains(VERAPDF)
                    || namesAnEditionThisBuildLacks(line)) {
                continue;
            }
            String command = withoutComment(line);
            int expected = exitCode(line);
            Cli.Run run = Cli.run(arguments(command).toArray(String[]::new));
            assertEquals(expected, run.exitCode(),
                    () -> report(block.page(), command, expected, run));
        }
    }

    /**
     * The one line a test cannot run is the one that needs a program this project does not
     * ship. Counting it holds that number where it is: a second unrun line would have to be
     * argued for here rather than added to a page.
     */
    @Test
    void runsEveryCommandButTheOneThatNeedsAnInstalledValidator() {
        for (String page : PAGES) {
            long skipped = commands(page).stream()
                    .filter(command -> command.line().contains(VERAPDF))
                    .count();
            assertEquals(SKIPPED, skipped, page + " leaves " + SKIPPED + " command unrun,"
                    + " the one that names a veraPDF installation");
        }
    }

    /**
     * The German page answers the same questions with the same commands. What may differ is
     * the question and the comment; what may not is the command, the order and the code it
     * leaves with, because a reader of one page is meant to be able to follow the other.
     */
    @Test
    void bothLanguagesShowTheSameCommands() {
        List<Command> english = commands(ENGLISH);
        List<Command> german = commands(GERMAN);
        assertEquals(english.stream().map(Command::line).toList(),
                german.stream().map(Command::line).toList(),
                GERMAN + " shows the commands of " + ENGLISH + ", in that order");
        assertEquals(english.stream().map(Command::exitCode).toList(),
                german.stream().map(Command::exitCode).toList(),
                GERMAN + " states the exit codes of " + ENGLISH + ", line by line");
        assertEquals(questions(ENGLISH), questions(GERMAN),
                GERMAN + " answers as many questions as " + ENGLISH);
    }

    /**
     * Every block the test does not run stands somewhere else, where it is run. The
     * comparison ignores how the lines are wrapped, because an FAQ breaks a line where its
     * column ends rather than where the source page does.
     */
    @Test
    void quotesEveryOtherBlockFromAPageThatIsRun() {
        Map<String, List<String>> sources = sources();
        for (String page : PAGES) {
            for (Snippet snippet : snippets(page)) {
                String collapsed = collapse(snippet.body());
                assertTrue(sources.getOrDefault(snippet.language(), List.of()).stream()
                                .anyMatch(body -> body.contains(collapsed)),
                        () -> page + ": the " + snippet.language() + " block '"
                                + head(collapsed) + "' is on none of " + SOURCES);
            }
        }
    }

    @Test
    void bothLanguagesShowTheSameSnippets() {
        List<Snippet> english = snippets(ENGLISH);
        List<Snippet> german = snippets(GERMAN);
        assertEquals(english.size(), german.size(),
                GERMAN + " shows as many quoted blocks as " + ENGLISH);
        for (int i = 0; i < english.size(); i++) {
            Snippet expected = english.get(i);
            Snippet actual = german.get(i);
            assertEquals(expected, actual,
                    () -> GERMAN + ": the " + actual.language() + " block '"
                            + head(collapse(actual.body())) + "' is not the one "
                            + ENGLISH + " shows in that place");
        }
    }

    /**
     * Returns the arguments of a command line, with every placeholder and every fixture
     * argument replaced by the path of the copy this test wrote.
     */
    private List<String> arguments(String command) {
        assertTrue(command.startsWith(COMMAND),
                "a line the test runs is a command of this tool: " + command);
        List<String> arguments = new ArrayList<>();
        String previous = "";
        for (String token : command.substring(COMMAND.length()).trim().split("\\s+")) {
            if (DESTINATIONS.contains(previous) && !Input.STDIN_ARGUMENT.equals(token)) {
                // A file the command writes: the page shows the name a reader would type,
                // and the test puts it in the temporary directory, which is the one place
                // a test may write. It is remembered so that a later command of the same
                // block that reads it finds it there.
                written.put(token, directory + "/" + token);
                arguments.add(directory + "/" + token);
            } else {
                arguments.add(argument(token));
            }
            previous = token;
        }
        return arguments;
    }

    /** Returns the one argument to pass for a token of a command line. */
    private String argument(String token) {
        String earlier = written.get(token);
        if (earlier != null) {
            return earlier;
        }
        if (PAGES_PDF.equals(token)) {
            return pages();
        }
        String placeholder = PLACEHOLDERS.get(token);
        if (placeholder != null) {
            return copy(placeholder);
        }
        return FIXTURES.stream().anyMatch(token::startsWith) ? copy(token) : token;
    }

    /**
     * Renders the file {@link #PAGES_PDF} stands for: pages with no invoice in them, which
     * is what {@code esj embed} is given and what no fixture of this repository is.
     */
    private String pages() {
        Path target = directory.resolve(PAGES_PDF);
        if (!Files.exists(target)) {
            Cli.Run run = Cli.run("render", copy(RENDERED_FROM), "--out", target.toString());
            assertEquals(ExitCode.SUCCESS, run.exitCode(),
                    "the test renders the pages " + PAGES_PDF + " stands for: " + run.err());
        }
        return target.toString();
    }

    /**
     * Writes a fixture of the classpath into the temporary directory under its own relative
     * path and returns the path to pass on a command line. A template is copied with the
     * whole directory it reads from.
     */
    private String copy(String resource) {
        if (resource.startsWith(TEMPLATES)) {
            if (!Files.exists(directory.resolve(TEMPLATES))) {
                Fixtures.directory(directory, TEMPLATES);
            }
            return directory + "/" + resource;
        }
        Path target = Path.of(directory + "/" + resource);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, Fixtures.bytes(resource));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target.toString();
    }

    /** Returns the commands of a page, in order, each with the code the page states. */
    private static List<Command> commands(String page) {
        List<Command> commands = new ArrayList<>();
        for (Fence fence : fences(page)) {
            if (!SHELL.equals(fence.language())) {
                continue;
            }
            for (String line : fence.body()) {
                if (line.startsWith(COMMAND)) {
                    commands.add(new Command(withoutComment(line), exitCode(line)));
                }
            }
        }
        assertFalse(commands.isEmpty(), page + " shows commands of this tool");
        return commands;
    }

    /** Returns the blocks of a page that are quoted rather than run, in order. */
    private static List<Snippet> snippets(String page) {
        List<Snippet> snippets = new ArrayList<>();
        for (Fence fence : fences(page)) {
            if (QUOTED.contains(fence.language())) {
                snippets.add(new Snippet(fence.language(), String.join("\n", fence.body())));
            }
        }
        assertFalse(snippets.isEmpty(), page + " shows blocks of " + QUOTED);
        return snippets;
    }

    /** Returns the blocks of the pages that are quoted from, by language, collapsed. */
    private static Map<String, List<String>> sources() {
        Map<String, List<String>> blocks = new LinkedHashMap<>();
        for (String page : SOURCES) {
            for (Fence fence : fences(page)) {
                if (QUOTED.contains(fence.language())) {
                    blocks.computeIfAbsent(fence.language(), language -> new ArrayList<>())
                            .add(collapse(String.join("\n", fence.body())));
                }
            }
        }
        return blocks;
    }

    /** Returns how many questions a page asks. */
    private static long questions(String page) {
        return Fixtures.text(page).lines().filter(line -> line.startsWith(QUESTION)).count();
    }

    /** Returns the fenced blocks of a page, with the tag each one was opened with. */
    private static List<Fence> fences(String page) {
        List<Fence> fences = new ArrayList<>();
        List<String> lines = List.of(Fixtures.text(page).split("\n", -1));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith(FENCE) || line.equals(FENCE)) {
                continue;
            }
            int end = i + 1;
            while (end < lines.size() && !lines.get(end).equals(FENCE)) {
                end++;
            }
            assertTrue(end < lines.size(),
                    "the block opened at line " + (i + 1) + " of " + page + " is closed");
            fences.add(new Fence(line.substring(FENCE.length()).trim(),
                    List.copyOf(lines.subList(i + 1, end))));
            i = end;
        }
        return fences;
    }

    /** Returns a line without the comment that documents it. */
    private static String withoutComment(String line) {
        int comment = line.indexOf('#');
        return (comment < 0 ? line : line.substring(0, comment)).trim();
    }

    /** Returns the code the comment of a line states, which is zero where it states none. */
    private static int exitCode(String line) {
        int comment = line.indexOf('#');
        if (comment < 0) {
            return ExitCode.SUCCESS;
        }
        Matcher matcher = EXIT.matcher(line.substring(comment + 1));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : ExitCode.SUCCESS;
    }

    /**
     * Tells whether a line names an edition with {@code --to} that this build does not
     * carry. The registries are data, and a distribution may leave one out; such a line is
     * then not an answer this build can be held to.
     */
    private static boolean namesAnEditionThisBuildLacks(String line) {
        String[] tokens = withoutComment(line).split("\\s+");
        for (int i = 0; i + 1 < tokens.length; i++) {
            if (TO_EDITION.equals(tokens[i]) && tokens[i + 1].matches("\\d{4}")) {
                return !Registry.editions().contains(tokens[i + 1]);
            }
        }
        return false;
    }

    /** Returns a text with every run of whitespace collapsed into one space. */
    private static String collapse(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").trim();
    }

    /** Returns the beginning of a block, which is what a failure message shows of it. */
    private static String head(String text) {
        return text.length() <= HEAD ? text : text.substring(0, HEAD) + "...";
    }

    /** Returns a failure message that shows the page, the command and what the tool wrote. */
    private static String report(String page, String command, int expected, Cli.Run run) {
        return page + " states exit " + expected + " for a command that left with "
                + run.exitCode() + "\n\ncommand: " + command
                + "\n\nthe tool wrote:\n" + run.text() + run.err();
    }

    /**
     * One fenced block of a page.
     *
     * @param language the tag the fence was opened with, empty where it carries none
     * @param body     the lines between the fences
     */
    record Fence(String language, List<String> body) { }

    /**
     * One block of shell lines, which is what this test runs.
     *
     * @param page  the page it stands on
     * @param lines its lines, commands of this tool and others
     */
    record Block(String page, List<String> lines) {

        @Override
        public String toString() {
            return page + ": " + withoutComment(lines.get(0));
        }
    }

    /**
     * One command of a page.
     *
     * @param line     the command line, without the comment that documents it
     * @param exitCode the code the comment states, which is zero where it states none
     */
    record Command(String line, int exitCode) { }

    /**
     * One block of a page that is quoted from another page rather than run here.
     *
     * @param language the tag the fence was opened with
     * @param body     the lines between the fences, as they stand
     */
    record Snippet(String language, String body) { }
}
