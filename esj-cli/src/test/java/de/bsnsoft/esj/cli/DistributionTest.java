package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The packaging held against the tool it packages.
 *
 * <p>The scripts under {@code dist/} are feature-agnostic on purpose: they take the list of
 * commands from the tool's own help and the runs to exercise them with from one case list,
 * so that a command added later is packaged, traced and compared without any of them being
 * edited. That only holds while the case list keeps up, and a case list that has fallen
 * behind is invisible — the packaging still succeeds, and the artefact is simply not
 * exercised where it differs. So the same check the smoke test makes at packaging time is
 * made here, at build time, where it is cheap.
 */
class DistributionTest {

    /** A case of {@code dist/cases.sh}: a name, then the arguments of one run. */
    private static final Pattern CASE = Pattern.compile("^([a-z0-9-]+) (--?\\S+|[a-z]+)\\b.*$");

    /** A command in the {@code Commands:} block of the help text. */
    private static final Pattern COMMAND = Pattern.compile("^ {2}([a-z][a-z-]*) {2,}\\S.*$");

    /** A row of the target table of {@code docs/install.md}. */
    private static final Pattern TARGET = Pattern.compile("^\\| `([a-z-]+)` \\| .*$");

    /** A target of the argument parser of {@code dist/package.sh}. */
    private static final Pattern TARGETS_OF_SCRIPT =
            Pattern.compile("^ {4}([a-z|-]+)\\) targets=\"\\$targets \\$1\".*$");

    /** An artefact of {@code dist/package.sh}, as the script names it. */
    private static final Pattern ARTEFACT_OF_SCRIPT =
            Pattern.compile("^\\s*(?:image|native|archive)=\\$out/(esj-\\$version[^\\s\"]*)$");

    /** A name of an artefact printed on {@code docs/install.md}. */
    private static final Pattern ARTEFACT_ON_PAGE =
            Pattern.compile("esj-(?:<version>|[0-9][0-9A-Za-z.+]*)[0-9A-Za-z.<>+-]*");

    /** An image tag printed on {@code docs/install.md}. */
    private static final Pattern TAG_ON_PAGE = Pattern.compile("esj:[0-9A-Za-z.<>+_-]+");

    /** The fence of the install path of the README. */
    private static final String SHELL = "```sh";

    /** A run of the packaging on a page, with the targets it names. */
    private static final Pattern PACKAGING_ON_PAGE =
            Pattern.compile("^dist/package\\.sh ([a-z- ]+?)\\s*(?:#.*)?$", Pattern.MULTILINE);

    /** A run of a packaged artefact on a page, with the directory it lies in. */
    private static final Pattern ARTEFACT_RUN_ON_PAGE =
            Pattern.compile("^dist/out/(\\S+)/esj \\S.*$", Pattern.MULTILINE);

    /** The document the Homebrew formula writes in its test block. */
    private static final Pattern FORMULA_DOCUMENT =
            Pattern.compile("write <<~JSON\\R(.*?)\\R *JSON\\R", Pattern.DOTALL);

    /** The version the Homebrew formula is written for. */
    private static final Pattern FORMULA_VERSION = Pattern.compile("^  version \"([^\"]+)\"$",
            Pattern.MULTILINE);

    /**
     * Every artefact and every image tag printed on {@code docs/install.md} is one the
     * packaging writes.
     *
     * <p>The console blocks of that page are the one set of transcripts no test runs — they
     * name release archives and files that exist only after a packaging run. What can be
     * held against the scripts is what they name: an archive or a directory the page tells
     * the reader to unzip, and the tag of an image the page tells them to run. A tag that
     * carries a version is one nobody can run without knowing which version was built, so
     * the page prints {@code esj:latest}, and the section that runs it names the target
     * that builds it.
     */
    @Test
    void everyArtefactAndTagPrintedOnTheInstallPageIsOneThePackagingWrites() {
        String script = Fixtures.text("dist/package.sh");
        List<Pattern> forms = artefactForms();
        String page = Fixtures.text("docs/install.md");
        int named = 0;
        Matcher names = ARTEFACT_ON_PAGE.matcher(page);
        while (names.find()) {
            String name = names.group();
            named++;
            assertTrue(forms.stream().anyMatch(form -> form.matcher(name).matches()),
                    "docs/install.md prints " + name + ", which dist/package.sh does not"
                            + " write; it writes " + forms);
        }
        assertTrue(named >= 5, "artefact names on docs/install.md: " + named);
        Matcher tags = TAG_ON_PAGE.matcher(page);
        while (tags.find()) {
            assertTrue(List.of("esj:latest", "esj:<version>").contains(tags.group()),
                    "docs/install.md prints " + tags.group() + "; the tag a reader can run"
                            + " without knowing the version is esj:latest");
        }
        assertTrue(script.contains("-t esj:latest"), "dist/package.sh tags the image esj:latest");
        assertTrue(page.contains("$ dist/package.sh docker"),
                "docs/install.md names the target that builds the container image before it"
                        + " prints a run of it");
    }

    /**
     * The install path the README shows names a target of the packaging and runs the artefact
     * that target writes.
     *
     * <p>The three lines are the first thing a reader types, and no test can run them: they
     * build a jar, then an executable, in minutes. What is held against the scripts is what
     * they name — a target {@code dist/package.sh} does not offer, or a path it does not
     * write, is a reader who stops at line two.
     */
    @Test
    void theInstallPathOfTheReadmeNamesATargetAndTheArtefactItWrites() {
        String block = block(Fixtures.text("README.md"), SHELL);
        Matcher invocation = PACKAGING_ON_PAGE.matcher(block);
        assertTrue(invocation.find(), "the install path of the README runs dist/package.sh");
        Set<String> targets = targetsOfTheScript();
        for (String target : invocation.group(1).trim().split("[\\s,]+")) {
            assertTrue(targets.contains(target),
                    "the README names the packaging target " + target + "; dist/package.sh"
                            + " offers " + targets);
        }
        Matcher run = ARTEFACT_RUN_ON_PAGE.matcher(block);
        assertTrue(run.find(), "the install path of the README runs the packaged tool");
        String directory = run.group(1);
        assertTrue(artefactForms().stream().anyMatch(form -> form.matcher(directory).matches()),
                "the README runs " + directory + ", which dist/package.sh does not write");
        assertTrue(block.contains("mvn -B verify"),
                "the install path of the README builds before it packages");
    }

    /**
     * The document of the Homebrew formula's test block is one the tool reads.
     *
     * <p>{@code brew test} runs that block against the installed executable, and a document
     * it refuses fails the tap rather than the formula's author.
     *
     * @param directory a directory of this test
     * @throws IOException if the document cannot be written
     */
    @Test
    void theDocumentOfTheHomebrewFormulaReadsBack(@TempDir Path directory) throws IOException {
        String formula = Fixtures.text("docs/homebrew/esj.rb");
        Matcher document = FORMULA_DOCUMENT.matcher(formula);
        assertTrue(document.find(), "docs/homebrew/esj.rb writes a document in its test block");
        Path file = directory.resolve("invoice.esj.json");
        Files.writeString(file, document.group(1).stripIndent() + "\n");
        Cli.Run run = Cli.run("get", file.toString(), "/BT-1");
        assertEquals(0, run.exitCode(), "esj get on the formula's document: " + run.err());
        assertEquals("RE-1", run.text().strip(), "the value the formula asserts");
        Matcher version = FORMULA_VERSION.matcher(formula);
        assertTrue(version.find(), "docs/homebrew/esj.rb names a version");
        // The formula installs a release, so it names the last one the changelog dates; between
        // releases the tree is a snapshot beyond it, and before the first one it is the snapshot.
        Matcher released = Pattern.compile("^## \\[(\\d+\\.\\d+\\.\\d+)\\] \u2014 \\d{4}-\\d{2}-\\d{2}",
                Pattern.MULTILINE).matcher(Fixtures.text("CHANGELOG.md"));
        String last = released.find() ? released.group(1) : null;
        assertTrue(version.group(1).equals(last)
                        || Cli.run("--version").text().contains(version.group(1)),
                "the formula installs " + version.group(1) + ", which is neither the last release"
                        + (last == null ? "" : " (" + last + ")") + " nor this build");
    }

    /** Returns the fenced block of a page that opens with the given fence. */
    private static String block(String page, String fence) {
        int start = page.indexOf(fence + "\n");
        assertTrue(start >= 0, "the page shows a " + fence + " block");
        int body = start + fence.length() + 1;
        int end = page.indexOf("\n```", body);
        assertTrue(end >= 0, "the " + fence + " block of the page is closed");
        return page.substring(body, end + 1);
    }

    /** Returns the forms of the artefact names {@code dist/package.sh} writes. */
    private static List<Pattern> artefactForms() {
        List<Pattern> forms = new ArrayList<>();
        for (String line : Fixtures.text("dist/package.sh").lines().toList()) {
            Matcher matcher = ARTEFACT_OF_SCRIPT.matcher(line);
            if (matcher.matches()) {
                forms.add(form(matcher.group(1)));
            }
        }
        assertTrue(forms.size() >= 3, "the artefacts dist/package.sh names: " + forms);
        return forms;
    }

    /** Returns the targets the argument parser of {@code dist/package.sh} accepts. */
    private static Set<String> targetsOfTheScript() {
        Set<String> targets = new TreeSet<>();
        for (String line : Fixtures.text("dist/package.sh").lines().toList()) {
            Matcher matcher = TARGETS_OF_SCRIPT.matcher(line);
            if (matcher.matches()) {
                targets.addAll(List.of(matcher.group(1).split("\\|")));
            }
        }
        assertTrue(targets.size() >= 5, "the targets of dist/package.sh were found: " + targets);
        return targets;
    }

    /** Turns one artefact name of {@code dist/package.sh} into what the page may print. */
    private static Pattern form(String name) {
        return Pattern.compile(name.replace(".", "\\.")
                .replace("$version", "(?:<version>|[0-9][0-9A-Za-z.+]*)")
                .replace("$os", "(?:<os>|linux|macos)")
                .replace("$arch", "(?:<arch>|arm64|x64)") + "(?:\\.zip)?");
    }

    @Test
    void everyCommandOfTheHelpIsExercisedByACase() {
        Set<String> exercised = new TreeSet<>();
        for (String line : Fixtures.text("dist/cases.sh").lines().toList()) {
            Matcher matcher = CASE.matcher(line);
            if (matcher.matches()) {
                exercised.add(matcher.group(2));
            }
        }
        Set<String> missing = new TreeSet<>(commandsOfTheHelp());
        missing.removeAll(exercised);
        assertTrue(missing.isEmpty(),
                "dist/cases.sh runs no case for " + missing
                        + ", so the packaged artefacts would never be compared with the jar"
                        + " on it; add one there");
    }

    @Test
    void everyTargetOfThePackagingScriptIsInTheTableOfTheInstallPage() {
        Set<String> documented = new TreeSet<>();
        boolean inTable = false;
        for (String line : Fixtures.text("docs/install.md").lines().toList()) {
            if (line.startsWith("| Target |")) {
                inTable = true;
            } else if (inTable && !line.startsWith("|")) {
                inTable = false;
            } else if (inTable) {
                Matcher matcher = TARGET.matcher(line);
                if (matcher.matches()) {
                    documented.add(matcher.group(1));
                }
            }
        }
        assertEquals(targetsOfTheScript(), documented,
                "docs/install.md documents the targets dist/package.sh offers");
    }

    /**
     * The scripts are shipped and are run by the release, so a syntax error in one of them
     * is a broken release. {@code sh -n} parses without running anything.
     *
     * @param script the script, as the archive carries it
     */
    @DisabledOnOs(OS.WINDOWS)
    @ParameterizedTest(name = "sh -n dist/{0}")
    @ValueSource(strings = {"package.sh", "smoke.sh", "cases.sh", "checks.sh",
                            "native-image.sh", "docker-entrypoint.sh"})
    void theScriptsOfTheDistributionParse(String script) throws Exception {
        Path file = Files.createTempFile("esj-dist-", ".sh");
        Files.write(file, Fixtures.bytes("dist/" + script));
        Process process = new ProcessBuilder("sh", "-n", file.toString())
                .redirectErrorStream(true)
                .start();
        String complaint = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "sh -n finished");
        assertEquals(0, process.exitValue(), "dist/" + script + ": " + complaint);
        Files.delete(file);
    }

    /** Returns the commands the tool's own help lists. */
    private static Set<String> commandsOfTheHelp() {
        Set<String> commands = new LinkedHashSet<>();
        boolean inBlock = false;
        for (String line : Cli.run("--help").text().lines().toList()) {
            if (line.startsWith("Commands:")) {
                inBlock = true;
            } else if (inBlock && line.startsWith("Exit codes:")) {
                break;
            } else if (inBlock) {
                Matcher matcher = COMMAND.matcher(line);
                if (matcher.matches()) {
                    commands.add(matcher.group(1));
                }
            }
        }
        assertEquals(11, commands.size(), "the commands of the help text: " + commands);
        return commands;
    }
}
