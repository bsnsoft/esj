package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The checks of {@code dist/checks.sh}, run the way the packaging runs them.
 *
 * <p>Both are about the machine a build ran on rather than about the tool, so neither can be
 * observed from a published artefact afterwards: an image labelled with a revision its own
 * sources do not have, and an executable carrying the home directory of whoever built it,
 * both look right to everybody who did not build them. Here they are cheap — the functions
 * are sourced and called on a prepared directory.
 *
 * <p>Git is reached through {@code PATH}, so the revision cases prepare a {@code git} of
 * their own rather than a repository: the build needs no git installation, and which of the
 * two answers is measured is the test's decision and not the checkout's.
 */
@DisabledOnOs(OS.WINDOWS)
class PackagingChecksTest {

    /** How long a run of the shell is given before it is taken for hung. */
    private static final long PATIENCE_SECONDS = 120;

    /** The home directory the checked runs are given. */
    private static final String HOME = "/home/somebody";

    /** The revision the prepared {@code git} reports. */
    private static final String HEAD = "0f1e2d3c4b5a69788796a5b4c3d2e1f001234567";

    @TempDir
    private Path directory;

    @Test
    void theRevisionOfACleanTreeIsTheCommit() throws Exception {
        Result result = revision(false);
        assertEquals(0, result.exitCode(), result.err());
        assertEquals(HEAD, result.out().strip(), "a clean tree is named by its commit");
    }

    @Test
    void theRevisionOfATreeWithChangesCarriesTheDirtyMarker() throws Exception {
        Result result = revision(true);
        assertEquals(0, result.exitCode(), result.err());
        assertEquals(HEAD + "-dirty", result.out().strip(),
                "an artefact built from a modified tree may not claim the bare commit: what"
                        + " is inside it is not what that commit carries");
    }

    @Test
    void thereIsNoRevisionWhereThereIsNoRepository() throws Exception {
        Path prepared = script("git", "exit 128\n");
        Result result = run(prepared.getParent(),
                "esj_revision \"" + directory + "\"");
        assertEquals(0, result.exitCode(),
                "a build from an unpacked archive is not an error: " + result.err());
        assertEquals("", result.out().strip(), "nothing is claimed where nothing can be asked");
    }

    @Test
    void thePackagingLabelsTheImageWithTheCheckedRevision() {
        String script = Fixtures.text("dist/package.sh");
        assertTrue(script.contains("revision=$(esj_revision \"$root\")"),
                "dist/package.sh takes the label from esj_revision");
        assertFalse(script.contains("git rev-parse"),
                "and not from git directly, which labels a modified tree with a bare commit");
    }

    @Test
    void anArtefactCarryingAPathOfTheBuildMachineFailsTheCheck() throws Exception {
        Path artefact = Files.createDirectory(directory.resolve("artefact"));
        buried(artefact.resolve("esj"), HOME);
        Result result = check(artefact);
        assertEquals(1, result.exitCode(), "the packaging stops: " + result.out());
        assertTrue(result.err().contains("esj: the home directory"),
                "the failure names the file and which path it carries: " + result.err());
        assertFalse(result.err().contains(HOME),
                "and prints no path of the machine: " + result.err());
    }

    @Test
    void theDirectoryTheSourcesWereReadFromIsLookedForToo() throws Exception {
        Path artefact = Files.createDirectory(directory.resolve("artefact"));
        Files.writeString(artefact.resolve("esj"), "an executable of no fixed abode\n");
        buried(artefact.resolve("libjvm.dylib"), directory.toString());
        Result result = check(artefact);
        assertEquals(1, result.exitCode(),
                "every file beside the executable is scanned: " + result.out());
        assertTrue(result.err().contains("libjvm.dylib: the directory the sources"),
                result.err());
    }

    @Test
    void anArtefactWithoutThosePathsPassesAndWhatTheBuildRecordedIsLeftOut() throws Exception {
        Path artefact = Files.createDirectory(directory.resolve("artefact"));
        Files.writeString(artefact.resolve("esj"), "a binary without a path of this machine\n");
        Path metadata = Files.createDirectory(artefact.resolve("metadata"));
        buried(metadata.resolve("reachability-metadata.json"), HOME);
        Result result = check(artefact);
        assertEquals(0, result.exitCode(), result.err());
        assertTrue(result.out().contains("no path of the build machine in 1 files"),
                "what the build recorded about itself stays out of the count and out of the"
                        + " archive: " + result.out());
    }

    @Test
    void aPathOfOneSegmentIsNotLookedForAndIsSaidSo() throws Exception {
        Path artefact = Files.createDirectory(directory.resolve("artefact"));
        buried(artefact.resolve("THIRD_PARTY_LICENSE.txt"), "see /src/LICENSE");
        Result result = run(null, Map.of("HOME", "/root"),
                "esj_no_build_paths \"" + artefact + "\" /src");
        assertEquals(0, result.exitCode(),
                "a build inside a container has /root and /src, which a third-party licence"
                        + " may name in passing; the check may not fail over them: "
                        + result.err());
        assertTrue(result.err().contains("one path segment"),
                "and it says which path it did not look for: " + result.err());
    }

    /** Runs {@code esj_revision} against a prepared git that reports a clean or dirty tree. */
    private Result revision(boolean dirty) throws Exception {
        Path prepared = script("git", "case $1 in\n"
                + "  rev-parse) echo " + HEAD + " ;;\n"
                + "  status) " + (dirty ? "echo \" M dist/package.sh\"" : ":") + " ;;\n"
                + "esac\n");
        return run(prepared.getParent(), "esj_revision \"" + directory + "\"");
    }

    /**
     * Runs {@code esj_no_build_paths} over a directory, with a home directory of the test's
     * own: what the ambient one is decides nothing here, and on a machine whose home is one
     * path segment the check would rightly look for neither.
     */
    private Result check(Path artefact) throws Exception {
        return run(null, Map.of("HOME", HOME),
                "esj_no_build_paths \"" + artefact + "\" \"" + directory + "\"");
    }

    /**
     * Writes a file that carries the text between bytes no text editor shows.
     *
     * <p>Which is where a debug map keeps it: what the check reads is the bytes of a file,
     * not a form of it that a tool knowing the file's format was willing to print.
     */
    private static void buried(Path file, String text) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            out.write(new byte[] {0, 1, 2, 3});
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.write(new byte[] {0, 1, 2, 3});
        }
    }

    /** Writes an executable script into a directory of its own and returns it. */
    private Path script(String name, String body) throws IOException {
        Path folder = Files.createDirectories(directory.resolve("bin-" + name));
        Path file = folder.resolve(name);
        Files.writeString(file, "#!/bin/sh\n" + body);
        assertTrue(file.toFile().setExecutable(true), "the prepared " + name + " is executable");
        return file;
    }

    /** Writes {@code dist/checks.sh} out of the classpath and returns where it landed. */
    private Path checks() throws IOException {
        Path file = directory.resolve("checks.sh");
        if (!Files.exists(file)) {
            Files.write(file, Fixtures.bytes("dist/checks.sh"));
        }
        return file;
    }

    /**
     * Sources {@code dist/checks.sh} and runs one call of it.
     *
     * @param first a directory to search before the rest of {@code PATH}, or null
     * @param call  the call, as it is written in the packaging
     * @return what the shell left behind
     * @throws Exception if the shell cannot be started or does not finish
     */
    private Result run(Path first, String call) throws Exception {
        return run(first, Map.of(), call);
    }

    /**
     * Sources {@code dist/checks.sh} and runs one call of it.
     *
     * @param first       a directory to search before the rest of {@code PATH}, or null
     * @param environment what to put into the environment of the shell
     * @param call        the call, as it is written in the packaging
     * @return what the shell left behind
     * @throws Exception if the shell cannot be started or does not finish
     */
    private Result run(Path first, Map<String, String> environment, String call)
            throws Exception {
        ProcessBuilder builder =
                new ProcessBuilder(List.of("sh", "-c", ". \"" + checks() + "\"; " + call));
        builder.environment().putAll(environment);
        if (first != null) {
            builder.environment().put("PATH", first + ":" + System.getenv("PATH"));
        }
        Path out = Files.createTempFile(directory, "out-", ".txt");
        Path err = Files.createTempFile(directory, "err-", ".txt");
        Process process = builder.redirectOutput(out.toFile()).redirectError(err.toFile())
                .start();
        assertTrue(process.waitFor(PATIENCE_SECONDS, TimeUnit.SECONDS),
                "the shell finished: " + call);
        return new Result(process.exitValue(), Files.readString(out), Files.readString(err));
    }

    /**
     * What one call left behind.
     *
     * @param exitCode the code the shell left with
     * @param out      what it wrote to its standard output
     * @param err      what it wrote to its error stream
     */
    private record Result(int exitCode, String out, String err) {
    }
}
