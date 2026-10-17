package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    /** The image the release workflow pushes, as each of its jobs names it. */
    private static final Pattern PUSHED_IMAGE =
            Pattern.compile("^ +ESJ_IMAGE: (\\S+)$", Pattern.MULTILINE);

    /** An image of the GitHub container registry, wherever a page names one. */
    private static final Pattern REGISTRY_IMAGE =
            Pattern.compile("ghcr\\.io/[a-z0-9-]+/[a-z0-9._-]*[a-z0-9]");

    /** The fence of the install path of the README. */
    private static final String SHELL = "```sh";

    /** A run of the packaging on a page, with the targets it names. */
    private static final Pattern PACKAGING_ON_PAGE =
            Pattern.compile("^dist/package\\.sh ([a-z- ]+?)\\s*(?:#.*)?$", Pattern.MULTILINE);

    /** A run of a packaged artefact on a page, with the directory it lies in. */
    private static final Pattern ARTEFACT_RUN_ON_PAGE =
            Pattern.compile("^dist/out/(\\S+)/esj \\S.*$", Pattern.MULTILINE);

    /** The pages the platform-independent archive carries, as {@code dist/package.sh} lists them. */
    private static final Pattern PAGES_OF_THE_ARCHIVE =
            Pattern.compile("^ *for page in ([a-z0-9. -]+); do$", Pattern.MULTILINE);

    /** The version the stand-in for the jar answers with. */
    private static final String STAND_IN_VERSION = "0.0.0-TEST";

    /** The document the Homebrew formula writes in its test block. */
    private static final Pattern FORMULA_DOCUMENT =
            Pattern.compile("write <<~JSON\\R(.*?)\\R *JSON\\R", Pattern.DOTALL);

    /** The version the Homebrew formula is written for. */
    private static final Pattern FORMULA_VERSION = Pattern.compile("^  version \"([^\"]+)\"$",
            Pattern.MULTILINE);

    /** A Linux package of {@code dist/packages.sh}, as the script names it. */
    private static final Pattern PACKAGE_OF_SCRIPT =
            Pattern.compile("^\\s*(?:deb|rpm)\\) target=\\$out/(esj[-_][^\\s;]+) ;;$");

    /** The name of a Linux package printed on {@code docs/install.md}. */
    private static final Pattern PACKAGE_ON_PAGE =
            Pattern.compile("esj[-_](?:<version>|[0-9])[0-9A-Za-z.<>+~_|-]*\\.(?:deb|rpm)");

    /** The oldest C library the native Linux executable runs on, as {@code dist/checks.sh} sets it. */
    private static final Pattern GLIBC_FLOOR =
            Pattern.compile("^ESJ_GLIBC_FLOOR=([0-9]+\\.[0-9]+)$", Pattern.MULTILINE);

    /** The address of the package repositories, the default of {@code dist/repositories.sh}. */
    private static final Pattern REPOSITORY_URL =
            Pattern.compile("^url=\\$\\{ESJ_REPOSITORY_URL:-(https://[^}]+)}$", Pattern.MULTILINE);

    /** The release key the repositories job of the release workflow signs with. */
    private static final Pattern SIGNING_KEY =
            Pattern.compile("^ +ESJ_SIGNING_KEY: ([0-9A-F]{40})$", Pattern.MULTILINE);

    /** A console block of {@code docs/install.md} that dist/repositories-test.sh runs. */
    private static final String REPOSITORY_BLOCK = "<!-- repositories-test: ";

    /**
     * Every artefact and every image tag printed on {@code docs/install.md} is one the
     * packaging writes.
     *
     * <p>The console blocks of that page are the one set of transcripts no test runs — they
     * name release archives and files that exist only after a packaging run. What can be
     * held against the scripts is what they name: an archive or a directory the page tells
     * the reader to unzip, and the tag of an image the page tells them to run. A tag with a
     * version number in it is stale after the next release, so the page prints
     * {@code latest} and {@code <version>}, the two tags the packaging writes and a release
     * publishes, and it names the target that builds the image of a checkout.
     */
    @Test
    void everyArtefactAndTagPrintedOnTheInstallPageIsOneThePackagingWrites() {
        String script = Fixtures.text("dist/package.sh");
        List<Pattern> forms = artefactForms();
        String page = Fixtures.text("docs/install.md");
        List<Pattern> packageForms = packageForms();
        Matcher packages = PACKAGE_ON_PAGE.matcher(page);
        int packagesNamed = 0;
        while (packages.find()) {
            String name = packages.group();
            packagesNamed++;
            assertTrue(packageForms.stream().anyMatch(form -> form.matcher(name).matches()),
                    "docs/install.md prints " + name + ", which dist/packages.sh does not"
                            + " write; it writes " + packageForms);
        }
        assertTrue(packagesNamed >= 4, "package names on docs/install.md: " + packagesNamed);
        page = PACKAGE_ON_PAGE.matcher(page).replaceAll("");
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
                    "docs/install.md prints " + tags.group() + "; the tags a reader can use"
                            + " without knowing the version are latest and <version>");
        }
        assertTrue(script.contains("image_name=${ESJ_IMAGE:-esj}")
                        && script.contains("-t \"$image_name:latest\""),
                "dist/package.sh names the image esj unless ESJ_IMAGE names another, and tags"
                        + " it latest");
        assertTrue(page.contains("$ dist/package.sh docker"),
                "docs/install.md names the target that builds the container image of a checkout");
    }

    /**
     * The image the install page and the README tell a reader to pull is the one the release
     * pushes.
     *
     * <p>The release workflow names that image in {@code ESJ_IMAGE}, once for the job that
     * pushes each platform and once for the job that joins them. A page that names another
     * one sends the reader to a package that does not exist, and no run of the tool would
     * notice.
     */
    @Test
    void theImageThePagesPullIsTheOneTheReleasePushes() {
        Set<String> pushed = new TreeSet<>();
        Matcher named = PUSHED_IMAGE.matcher(Fixtures.text(".github/workflows/release.yml"));
        while (named.find()) {
            pushed.add(named.group(1));
        }
        assertEquals(1, pushed.size(), "the jobs of release.yml push one image: " + pushed);
        String image = pushed.iterator().next();
        assertTrue(Fixtures.text("docs/install.md").contains("$ docker pull " + image + ":latest"),
                "docs/install.md pulls " + image + ":latest");
        for (String page : List.of("docs/install.md", "README.md")) {
            Matcher found = REGISTRY_IMAGE.matcher(Fixtures.text(page));
            int images = 0;
            while (found.find()) {
                images++;
                assertEquals(image, found.group(),
                        page + " names an image the release does not push");
            }
            assertTrue(images > 0, page + " names the image a release publishes");
        }
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
        Matcher released = Pattern.compile("^## \\[(\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.]+)?)\\] \u2014 \\d{4}-\\d{2}-\\d{2}",
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

    /**
     * Returns the forms of the package names {@code dist/packages.sh} writes, with the
     * processors either spelled out or given as the choice the page prints.
     */
    private static List<Pattern> packageForms() {
        List<Pattern> forms = new ArrayList<>();
        for (String line : Fixtures.text("dist/packages.sh").lines().toList()) {
            Matcher matcher = PACKAGE_OF_SCRIPT.matcher(line);
            if (matcher.matches()) {
                forms.add(Pattern.compile(matcher.group(1).replace(".", "\\.")
                        .replace("${pkgver}", "$pkgver")
                        .replace("$pkgver", "(?:<version>|[0-9][0-9A-Za-z.+~]*)")
                        .replace("$deb_arch", "(?:amd64|arm64|<amd64\\|arm64>|<arch>)")
                        .replace("$rpm_arch", "(?:x86_64|aarch64|<x86_64\\|aarch64>|<arch>)")));
            }
        }
        assertEquals(2, forms.size(), "the packages dist/packages.sh names: " + forms);
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
                            "native-image.sh", "docker-entrypoint.sh", "serve-smoke.sh",
                            "graalvm.sh", "nfpm.sh", "packages.sh", "packages-smoke.sh",
                            "repositories.sh", "repositories-test.sh"})
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

    /**
     * A relative {@code --out} names a directory under the working directory, for the
     * targets that name it from another one as well: {@code zip} writes the archive from
     * inside its staging directory, and left with exit code 15 where {@code --out} was
     * relative. The packaging runs here over a tree of its own — the scripts, a stand-in for
     * the jar, the files the archive carries — with a stand-in for the JDK that answers the
     * two questions the script asks of one, from a working directory outside that tree.
     *
     * @param directory a directory of this test
     * @throws Exception if the shell cannot be started, does not finish, or the archive
     *                   cannot be read
     */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aRelativeOutIsUnderTheWorkingDirectory(@TempDir Path directory) throws Exception {
        Path root = Files.createDirectories(directory.resolve("repository"));
        Path dist = Files.createDirectories(root.resolve("dist"));
        for (String script : List.of("package.sh", "cases.sh", "checks.sh")) {
            Files.write(dist.resolve(script), Fixtures.bytes("dist/" + script));
        }
        Path pom = root.resolve("pom.xml");
        Files.writeString(pom, "<project.build.outputTimestamp>2026-09-22T20:42:11Z"
                + "</project.build.outputTimestamp>\n");
        for (String file : List.of("LICENSE", "NOTICE", "README.md")) {
            Files.writeString(root.resolve(file), file + "\n");
        }
        Path docs = Files.createDirectories(root.resolve("docs"));
        Matcher pages = PAGES_OF_THE_ARCHIVE.matcher(Fixtures.text("dist/package.sh"));
        assertTrue(pages.find(), "dist/package.sh lists the pages of the archive");
        for (String page : pages.group(1).split(" ")) {
            Files.writeString(docs.resolve(page), page + "\n");
        }
        Path jar = Files.createDirectories(root.resolve("esj-cli/target")).resolve("esj.jar");
        Files.writeString(jar, "a stand-in for the jar\n");
        // Older than the jar, so that the packaging finds it up to date and builds nothing.
        Files.setLastModifiedTime(pom, FileTime.fromMillis(0));
        Path jdk = Files.createDirectories(directory.resolve("jdk/bin"));
        Files.writeString(jdk.resolve("java"), "#!/bin/sh\n"
                + "case $1 in\n"
                + "  -version) echo 'openjdk version \"25\"' >&2 ;;\n"
                + "  -jar) echo 'esj " + STAND_IN_VERSION + "' ;;\n"
                + "esac\n");
        assertTrue(jdk.resolve("java").toFile().setExecutable(true),
                "the stand-in for java is executable");
        Path work = Files.createDirectories(directory.resolve("work"));
        Path log = directory.resolve("package.log");

        ProcessBuilder builder = new ProcessBuilder("sh",
                dist.resolve("package.sh").toString(), "zip", "--out", "out")
                .directory(work.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        builder.environment().put("ESJ_JDK25_HOME", jdk.getParent().toString());
        Process process = builder.start();
        boolean finished = process.waitFor(2, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
        }

        assertTrue(finished, "dist/package.sh finished: " + Files.readString(log));
        assertEquals(0, process.exitValue(), Files.readString(log));
        Path archive = work.resolve("out/esj-" + STAND_IN_VERSION + ".zip");
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertNotNull(zip.getEntry("esj-" + STAND_IN_VERSION + "/lib/esj.jar"),
                    "the archive carries the jar");
        }
        assertTrue(Files.isRegularFile(work.resolve("out/esj-" + STAND_IN_VERSION
                + ".zip.sha256")), "and its checksum stands beside it");
        assertFalse(Files.exists(root.resolve("out")), "nothing went under the repository");
    }

    /**
     * The version a package carries and the suite it goes to follow from the version of the
     * tool: a release as it is and to {@code stable}, a pre-release with {@code ~} for its
     * hyphen, which apt and dnf sort before the release, and to {@code testing}. The rpm has
     * the release number 1, the deb no revision. Anything else is refused with exit code 2.
     *
     * @param version the version of the tool
     * @param deb     the version of the .deb, or {@code refused}
     * @param rpm     the version and release of the .rpm
     * @param suite   the suite of the repositories
     * @param directory a directory of this test
     * @throws Exception if the shell cannot be started or does not finish
     */
    @DisabledOnOs(OS.WINDOWS)
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "0.9.8, 0.9.8, 0.9.8-1, stable",
        "1.0.0-rc.1, 1.0.0~rc.1, 1.0.0~rc.1-1, testing",
        "1.2.3-SNAPSHOT, 1.2.3~SNAPSHOT, 1.2.3~SNAPSHOT-1, testing",
        "v1.0.0, refused, , ",
        "1.0, refused, , ",
        "1.0.0-rc-1, refused, , "
    })
    void aVersionMapsToThePackageVersionsAndTheSuite(String version, String deb, String rpm,
            String suite, @TempDir Path directory) throws Exception {
        Path dist = Files.createDirectories(directory.resolve("dist"));
        for (String script : List.of("packages.sh", "checks.sh")) {
            Files.write(dist.resolve(script), Fixtures.bytes("dist/" + script));
        }
        Process process = new ProcessBuilder("sh", dist.resolve("packages.sh").toString(),
                "--versions", version).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "dist/packages.sh finished");
        if ("refused".equals(deb)) {
            assertEquals(2, process.exitValue(), "dist/packages.sh --versions " + version + ": "
                    + output);
        } else {
            assertEquals(0, process.exitValue(), output);
            assertEquals("deb " + deb + "\nrpm " + rpm + "\nsuite " + suite + "\n", output);
        }
    }

    /**
     * Every archive and package is attached under the name GitHub stores it under, with a
     * checksum file that names it so.
     *
     * <p>GitHub stores a {@code ~} in the name of a release asset as a dot: the package
     * {@code esj_1.0.0~rc.2_amd64.deb} of a pre-release is downloaded as
     * {@code esj_1.0.0.rc.2_amd64.deb}, and a checksum file that names the {@code ~} name
     * fails {@code sha256sum -c}, as those of 1.0.0-rc.2 did. The release job therefore gives
     * the assets those names before it checks them a last time and attaches them; the
     * contents of a package stay as they are, and the names of a release, which have no
     * {@code ~}, stay as they are byte for byte.
     *
     * @param version the version of the tool the assets are of
     * @param directory a directory of this test
     * @throws Exception if the shell cannot be started or does not finish
     */
    @DisabledOnOs(OS.WINDOWS)
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"1.0.0-rc.2", "0.9.8"})
    void everyAssetIsAttachedUnderTheNameGitHubStoresItUnder(String version,
            @TempDir Path directory) throws Exception {
        String packageVersion = version.replaceFirst("-", "~");
        List<String> files = List.of("esj-" + version + ".zip",
                "esj_" + packageVersion + "_amd64.deb", "esj_" + packageVersion + "_arm64.deb",
                "esj-" + packageVersion + "-1.x86_64.rpm",
                "esj-" + packageVersion + "-1.aarch64.rpm");
        Path assets = Files.createDirectories(directory.resolve("assets"));
        Map<String, byte[]> contents = new TreeMap<>();
        for (String file : files) {
            byte[] bytes = ("the contents of " + file + "\n").getBytes(StandardCharsets.UTF_8);
            contents.put(file, bytes);
            Files.write(assets.resolve(file), bytes);
            Files.writeString(assets.resolve(file + ".sha256"), sha256(bytes) + "  " + file + "\n");
        }
        Map<String, byte[]> before = new TreeMap<>();
        for (Path file : listing(assets)) {
            before.put(file.getFileName().toString(), Files.readAllBytes(file));
        }

        String output = releaseNames(directory, assets, 0);

        Set<String> expected = new TreeSet<>();
        for (String file : files) {
            String attached = file.replace('~', '.');
            expected.add(attached);
            expected.add(attached + ".sha256");
            assertArrayEquals(contents.get(file), Files.readAllBytes(assets.resolve(attached)),
                    attached + " carries the bytes of " + file);
            assertEquals(sha256(contents.get(file)) + "  " + attached + "\n",
                    Files.readString(assets.resolve(attached + ".sha256")),
                    attached + ".sha256 is the one line that names " + attached);
        }
        Set<String> names = new TreeSet<>();
        for (Path file : listing(assets)) {
            names.add(file.getFileName().toString());
        }
        assertEquals(expected, names, output);
        if (version.contains("-")) {
            assertTrue(names.contains("esj_1.0.0.rc.2_amd64.deb")
                    && names.contains("esj-1.0.0.rc.2-1.x86_64.rpm"), "renamed: " + names);
        } else {
            assertEquals(before.keySet(), names, "a release keeps every name");
            for (Map.Entry<String, byte[]> file : before.entrySet()) {
                assertArrayEquals(file.getValue(), Files.readAllBytes(assets.resolve(file.getKey())),
                        file.getKey() + " of a release stays as it is");
            }
        }

        // The release job gives the assets those names, checks them again and only then
        // attests and attaches them; a draft is published only under the delivered names.
        String workflow = Fixtures.text(".github/workflows/release.yml");
        int renamed = workflow.indexOf("sh dist/packages.sh --release-names assets\n"
                + "          check assets\n");
        assertTrue(renamed > 0, "release.yml renames the assets and checks them again");
        assertTrue(renamed < workflow.indexOf("- name: Attest the provenance")
                        && renamed < workflow.indexOf("- name: Attach them"),
                "release.yml renames the assets before it attests and attaches them");
        assertTrue(workflow.contains("--json assets --jq '.assets[].name'"),
                "release.yml compares the names of the attached assets with the delivered ones");
    }

    /**
     * A checksum file that does not name the file beside it is refused, and so is a renamed
     * name that is already taken: the release job stops before anything is attached.
     *
     * @param directory a directory of this test
     * @throws Exception if the shell cannot be started or does not finish
     */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aChecksumThatNamesAnotherFileIsRefused(@TempDir Path directory) throws Exception {
        Path assets = Files.createDirectories(directory.resolve("assets"));
        byte[] bytes = "a package\n".getBytes(StandardCharsets.UTF_8);
        Files.write(assets.resolve("esj_1.0.0~rc.2_amd64.deb"), bytes);
        Files.writeString(assets.resolve("esj_1.0.0~rc.2_amd64.deb.sha256"),
                sha256(bytes) + "  esj_1.0.0~rc.2_arm64.deb\n");
        String output = releaseNames(directory, assets, 1);
        assertTrue(output.contains("is not the one line that names esj_1.0.0~rc.2_amd64.deb"),
                output);

        Path taken = Files.createDirectories(directory.resolve("taken"));
        Files.write(taken.resolve("esj_1.0.0~rc.2_amd64.deb"), bytes);
        Files.write(taken.resolve("esj_1.0.0.rc.2_amd64.deb"), bytes);
        output = releaseNames(directory, taken, 1);
        assertTrue(output.contains("are both in"), output);
    }

    /** Runs {@code dist/packages.sh --release-names} on a directory and returns its output. */
    private static String releaseNames(Path directory, Path assets, int exitCode)
            throws Exception {
        Path dist = Files.createDirectories(directory.resolve("dist"));
        for (String script : List.of("packages.sh", "checks.sh")) {
            Files.write(dist.resolve(script), Fixtures.bytes("dist/" + script));
        }
        Process process = new ProcessBuilder("sh", dist.resolve("packages.sh").toString(),
                "--release-names", assets.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "dist/packages.sh finished");
        assertEquals(exitCode, process.exitValue(), output);
        return output;
    }

    /** Returns the files of a directory. */
    private static List<Path> listing(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.sorted().toList();
        }
    }

    /** Returns the SHA-256 of some bytes, in lower-case hex. */
    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /**
     * The C library the packages require, the one the build refuses to exceed and the one
     * the pages name are one value, {@code ESJ_GLIBC_FLOOR} of {@code dist/checks.sh}.
     */
    @Test
    void theGlibcFloorIsOneValueForTheBuildThePackagesAndThePages() {
        Matcher floor = GLIBC_FLOOR.matcher(Fixtures.text("dist/checks.sh"));
        assertTrue(floor.find(), "dist/checks.sh sets ESJ_GLIBC_FLOOR");
        String version = floor.group(1);
        String packages = Fixtures.text("dist/packages.sh");
        assertTrue(packages.contains("'libc6 (>= $ESJ_GLIBC_FLOOR)'")
                        && packages.contains("'glibc >= $ESJ_GLIBC_FLOOR'"),
                "the .deb and the .rpm require the floor of dist/checks.sh");
        assertTrue(packages.contains("esj_glibc_floor \"$native\""),
                "dist/packages.sh checks every executable against the floor before it packs it");
        assertTrue(Fixtures.text("dist/native-image.sh").contains("esj_glibc_floor \"$target\""),
                "dist/native-image.sh checks the Linux executable it builds against the floor");
        assertTrue(Fixtures.text("docs/install.md").contains("GNU C library " + version + " or newer"),
                "docs/install.md names glibc " + version);
        assertTrue(Fixtures.text("dist/repositories-index.html").contains("glibc " + version + " or newer"),
                "the page of the package site names glibc " + version);
    }

    /**
     * The commands the install page and the page of the package site give a reader name the
     * site {@code dist/repositories.sh} writes, at the address it writes it for, with the
     * suites and the .repo files it writes; the release workflow writes that site from the
     * suite of the tag and pushes it to the branch GitHub Pages serves; and the key the
     * workflow signs with is the one the pages tell a reader to compare.
     */
    @Test
    void theRepositoryPagesNameTheSiteTheReleasePublishes() {
        String script = Fixtures.text("dist/repositories.sh");
        Matcher url = REPOSITORY_URL.matcher(script);
        assertTrue(url.find(), "dist/repositories.sh has a default address");
        String site = url.group(1);
        assertTrue(Fixtures.text("dist/repositories-test.sh").contains("public=" + site + "\n"),
                "dist/repositories-test.sh exchanges the address the pages print");
        for (String file : List.of("rpm/esj.repo", "rpm/esj-testing.repo")) {
            assertTrue(script.contains("> \"$site/" + file + "\""),
                    "dist/repositories.sh writes " + file);
        }
        assertTrue(script.contains("cp \"$work/key.gpg\" \"$site/bsnsoft-esj.gpg\""),
                "dist/repositories.sh writes the keyring at the root of the site");
        assertTrue(script.contains("case $suite in stable|testing) ;;"),
                "dist/repositories.sh knows the suites stable and testing");

        Map<String, String> blocks = repositoryBlocks();
        assertEquals(List.of("apt stable", "apt testing", "dnf stable", "dnf testing"),
                List.copyOf(blocks.keySet()), "the blocks dist/repositories-test.sh runs");
        assertTrue(blocks.get("apt stable").contains("curl -fsSL " + site + "/bsnsoft-esj.gpg |"),
                "the apt block fetches the keyring of the site");
        assertTrue(blocks.get("apt stable").contains(site + " stable main'"),
                "the apt block adds the suite stable");
        assertTrue(blocks.get("apt stable").endsWith("sudo apt-get install esj\n"),
                "the apt block ends with the installation");
        assertTrue(blocks.get("apt testing").contains(site + " testing main'"),
                "the testing block adds the suite testing");
        assertTrue(blocks.get("dnf stable").contains(site + "/rpm/esj.repo\n"),
                "the dnf block fetches esj.repo");
        assertTrue(blocks.get("dnf stable").endsWith("sudo dnf install esj\n"),
                "the dnf block ends with the installation");
        assertTrue(blocks.get("dnf testing").contains(site + "/rpm/esj-testing.repo\n"),
                "the testing block fetches esj-testing.repo");

        // The page of the site prints the same two installations.
        String index = Fixtures.text("dist/repositories-index.html")
                .replace("@URL@", site).replace("&amp;", "&").replace("&gt;", ">");
        for (String kind : List.of("apt stable", "dnf stable")) {
            assertTrue(index.contains(blocks.get(kind).strip()),
                    "the page of the package site prints the " + kind + " block of docs/install.md");
        }

        // No page sends a reader to another address of the same site.
        for (String page : List.of("docs/install.md", "README.md", "SECURITY.md")) {
            assertFalse(Fixtures.text(page).contains("github.io"),
                    page + " names the github.io address of the site instead of " + site);
        }
        assertFalse(index.contains("github.io"), "the page of the site names " + site);

        // The release writes the site for the suite of its tag and pushes the branch Pages serves.
        String workflow = Fixtures.text(".github/workflows/release.yml");
        assertTrue(workflow.contains("suite=$(sh source/dist/packages.sh --versions \"$version\""),
                "release.yml takes the suite of the tag from dist/packages.sh");
        assertTrue(workflow.contains("sh source/dist/repositories.sh packages site \"$suite\""),
                "release.yml writes the site with dist/repositories.sh");
        assertTrue(workflow.contains("git push origin HEAD:gh-pages"),
                "release.yml pushes the branch gh-pages");

        // One key: the workflow signs with the one the pages name.
        Matcher key = SIGNING_KEY.matcher(workflow);
        assertTrue(key.find(), "release.yml names the key the repositories are signed with");
        for (String page : List.of("docs/install.md", "SECURITY.md")) {
            assertTrue(Fixtures.text(page).contains(key.group(1)),
                    page + " names the key the repositories are signed with, " + key.group(1));
        }
    }

    /** Returns the console blocks of {@code docs/install.md} behind a repositories-test marker. */
    private static Map<String, String> repositoryBlocks() {
        Map<String, String> blocks = new LinkedHashMap<>();
        List<String> lines = Fixtures.text("docs/install.md").lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.startsWith(REPOSITORY_BLOCK)) {
                continue;
            }
            String name = line.substring(REPOSITORY_BLOCK.length(), line.length() - " -->".length());
            assertEquals("```console", lines.get(i + 1), "a console block follows " + line);
            StringBuilder block = new StringBuilder();
            for (int j = i + 2; !lines.get(j).equals("```"); j++) {
                assertTrue(lines.get(j).startsWith("$ "), "a command of " + name + ": " + lines.get(j));
                block.append(lines.get(j).substring(2)).append('\n');
            }
            blocks.put(name, block.toString());
        }
        return blocks;
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
        assertEquals(14, commands.size(), "the commands of the help text: " + commands);
        return commands;
    }
}
