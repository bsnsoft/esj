package de.bsnsoft.esj.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Checks {@code model/en16931/2026.paths} against the repository: every path it lists is
 * a file or a directory that is there, and every file that carries the facts of
 * EN 16931-1:2026 is either covered by one of them or stands in the list of files that are
 * deliberately not separable.
 *
 * <p>The second half is what the list is for. Whether a register built from that edition
 * may be published is an open question, and the answer must not be a search through the
 * tree: the profile {@code without-edition-2026} leaves exactly these paths out, and this
 * test is what keeps the list from falling behind the repository.
 *
 * <p>The whole repository is walked, not a fixed set of directories, and every file that is
 * not binary is read, whatever its name, so that a file of the edition added anywhere is
 * seen. A file carries the edition when it names that edition or when it names a business
 * term or group the 2026 registry adds over the 2017 one; the identifiers are read from the
 * two registries rather than written down here. What a walk cannot decide is whether a file
 * is one of the pages that state facts of the edition in prose — those are named in
 * {@link #NOT_SEPARABLE} with their reason, and adding a name there is the deliberate act
 * the list exists for.
 *
 * <p>This module is the one that may look at the tree — it is the build tool that writes
 * into it — and the paths below are relative to the repository root, one directory up.
 */
class Edition2026PathsTest {

    private static final Path REPOSITORY = Path.of("..");

    private static final Path LIST = REPOSITORY.resolve("model/en16931/2026.paths");

    /** The edition whose files this list separates. */
    private static final String EDITION = "2026";

    /** The file whose presence says that this tree carries the edition. */
    private static final String REGISTRY = "model/en16931/2026.json";

    /** Directories a walk of the repository does not descend into, by name. */
    private static final List<String> SKIPPED = List.of(".git", "target", "node_modules");

    /**
     * Build output a walk does not descend into either, named by its place in the
     * repository rather than by a bare directory name: what {@code dist/package.sh}
     * assembles and what the measurement instances are written to. Neither is part of the
     * repository — {@code .gitignore} keeps both out — and a smoke run of the packaged CLI
     * leaves documents of this edition in the first of them.
     */
    private static final List<String> SKIPPED_OUTPUT = List.of("dist/out",
            "conformance/scale/out");

    /** Where the bindings in other languages live. */
    private static final String BINDINGS = "bindings/";

    /**
     * The directories a binding builds into, which are skipped under {@link #BINDINGS} and
     * nowhere else.
     *
     * <p>They are what {@code target} is for a Maven module: no checkout carries them, and a
     * build writes them from the files this list is about. They are skipped by their place
     * and not by their name alone, because the repository keeps files of its own under
     * {@code bin} and {@code dist}. A generated view of the edition is not a place the
     * edition can be separated from: separating it from the registry it is generated out of
     * is what {@code model/en16931/2026.paths} already does, one directory up.
     */
    private static final List<String> BUILT = List.of("bin", "obj", "dist", "data", "generated");

    /**
     * Where the test sources of a binding live, which the walk passes over for the reason
     * it passes over {@code src/test} of a Maven module.
     */
    private static final List<String> TEST_SOURCES = List.of(
            "bindings/typescript/test",
            "bindings/csharp/En16931.SemanticJson.Tests");

    /**
     * The extensions of the files that are not text and are therefore not read. Everything
     * else is read, including a file with no extension at all, and a file that carries a NUL
     * byte in its first bytes is treated as binary whatever it is called.
     */
    private static final List<String> BINARY = List.of(".pdf", ".ttf", ".otf", ".woff",
            ".woff2", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".zip", ".gz", ".jar",
            ".class", ".bin");

    /** How many bytes of a file decide whether it is binary. */
    private static final int SNIFFED = 8192;

    /**
     * The files that name the edition and are not separable from the rest of the
     * repository, each for a stated reason.
     *
     * <ul>
     *   <li>The list itself and the build that leaves its paths out.</li>
     *   <li>The machinery, which loads whatever registries are present and carries no fact
     *       of the model.</li>
     *   <li>Prose. Writing down an identifier, a count or a grammar is not publishing a
     *       register built from the edition, which is what the open question is about. The
     *       attribution file belongs here for the same reason: it states this project's
     *       legal position, and the paragraph it gives the edition says of itself that it
     *       describes a distribution which carries the registry.</li>
     * </ul>
     */
    private static final List<String> NOT_SEPARABLE = List.of(
            "model/en16931/2026.paths",
            "pom.xml",
            "bin/without-edition-2026.sh",
            "esj-core/src/main/java/de/bsnsoft/esj/Esj.java",
            "esj-cli/src/main/java/de/bsnsoft/esj/cli/Editions.java",
            "esj-typed/src/main/java/de/bsnsoft/esj/typed/package-info.java",
            "NOTICE",
            "README.md",
            "SPEC.md",
            "CHANGELOG.md",
            "model/README.md",
            "examples/README.md",
            "examples/invalid/README.md",
            "docs/editions.md",
            "docs/sources.md",
            "docs/design-decisions.md",
            "docs/java-api.md",
            "docs/faq.md",
            "docs/faq-de.md");

    static boolean theListIsThere() {
        return Files.exists(LIST);
    }

    /**
     * Tells whether this tree carries the edition at all.
     *
     * <p>A distribution assembled without it — what {@code bin/without-edition-2026.sh}
     * builds, and what the profile {@code without-edition-2026} is for — keeps the list and
     * has none of the files it names. That the paths are then absent is the point of the
     * exercise rather than a defect, so the first test below asks this and the second does
     * not: nothing of the edition may lie outside the list in either tree, and in a tree
     * without the edition that is the check that the removal was complete.
     */
    static boolean theEditionIsThere() {
        return theListIsThere() && Files.exists(REPOSITORY.resolve(REGISTRY));
    }

    private static List<String> listed() throws IOException {
        List<String> paths = new ArrayList<>();
        for (String line : Files.readAllLines(LIST, StandardCharsets.UTF_8)) {
            String path = line.strip();
            if (!path.isEmpty() && !path.startsWith("#")) {
                paths.add(path);
            }
        }
        return paths;
    }

    @Test
    @EnabledIf("theEditionIsThere")
    void everyPathOfTheListIsThere() throws IOException {
        for (String path : listed()) {
            assertTrue(Files.exists(REPOSITORY.resolve(path)),
                    path + " is listed and is not in the repository");
        }
    }

    /**
     * Every name of the exception list is a file of this repository, so that a name left
     * behind by a page that was renamed or removed is found rather than kept.
     */
    @Test
    @EnabledIf("theListIsThere")
    void everyExceptionIsThere() {
        for (String path : NOT_SEPARABLE) {
            assertTrue(Files.exists(REPOSITORY.resolve(path)),
                    path + " is named as not separable and is not in the repository");
        }
    }

    /**
     * Nothing of the edition lies outside the list. The whole repository is walked; a file
     * carries facts of the edition when its text names that edition or one of the terms the
     * edition adds, and every such file has to be covered by a listed path or to stand in
     * {@link #NOT_SEPARABLE}.
     *
     * <p>Test sources are the one class of file the walk passes over. They are written to
     * run in both configurations — they skip, or they assert the absence — so leaving them
     * out of a build would take away the very check that the build without the edition
     * still works; the ones that belong to the edition are listed all the same.
     */
    @Test
    @EnabledIf("theListIsThere")
    void nothingOfTheEditionLiesOutsideTheList() throws IOException {
        List<String> listed = listed();
        List<String> uncovered = new ArrayList<>();
        for (String relative : namingTheEdition()) {
            if (covered(listed, relative) || NOT_SEPARABLE.contains(relative)) {
                continue;
            }
            uncovered.add(relative);
        }
        assertEquals(List.of(), uncovered,
                "these files carry facts of the 2026 edition and model/en16931/2026.paths"
                        + " neither lists them nor names them as not separable");
    }

    /** Tells whether a listed path is the file itself or a directory above it. */
    private static boolean covered(List<String> listed, String relative) {
        for (String path : listed) {
            if (relative.equals(path) || relative.startsWith(path + "/")) {
                return true;
            }
        }
        return false;
    }

    /** Walks the repository and returns every file whose text carries the edition. */
    private static List<String> namingTheEdition() throws IOException {
        Pattern added = addedByTheEdition();
        List<String> found = new ArrayList<>();
        Files.walkFileTree(REPOSITORY, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes a) {
                String name = directory.getFileName().toString();
                String relative = REPOSITORY.relativize(directory).toString();
                return SKIPPED.contains(name) || SKIPPED_OUTPUT.contains(relative)
                                || builtByABinding(relative, name)
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes a)
                    throws IOException {
                String relative = REPOSITORY.relativize(file).toString();
                if (carriesTheEdition(relative, file, added)) {
                    found.add(relative);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        found.sort(String::compareTo);
        return found;
    }

    /**
     * Tells whether a file carries facts of the 2026 edition: it names that edition, or it
     * names one of the business terms or groups that edition adds over the earlier one. The
     * second half is what finds a page that writes down a fact of the model without naming
     * the edition it belongs to.
     */
    private static boolean carriesTheEdition(String relative, Path file, Pattern added)
            throws IOException {
        if (relative.contains("/src/test/") || underTestSources(relative)) {
            return false;
        }
        String content = text(file);
        if (content == null) {
            return false;
        }
        if (content.contains("EN 16931-1:2026") || content.contains("EN16931-1:2026")) {
            return true;
        }
        return added != null && added.matcher(content).find();
    }

    /** Tells whether a directory is one a binding builds into. */
    private static boolean builtByABinding(String relative, String name) {
        return relative.startsWith(BINDINGS) && BUILT.contains(name);
    }

    /** Tells whether a file is a test source of a binding, which the walk passes over. */
    private static boolean underTestSources(String relative) {
        for (String directory : TEST_SOURCES) {
            if (relative.startsWith(directory + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the content of a file as text, or {@code null} where the file is binary. A
     * name without an extension is read like any other: the attribution file of this
     * repository has none, and so may the next file that carries the edition.
     */
    private static String text(Path file) throws IOException {
        String name = file.getFileName().toString();
        for (String extension : BINARY) {
            if (name.endsWith(extension)) {
                return null;
            }
        }
        byte[] bytes = Files.readAllBytes(file);
        for (int i = 0; i < Math.min(bytes.length, SNIFFED); i++) {
            if (bytes[i] == 0) {
                return null;
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * The identifiers the 2026 registry adds over the default edition, as one pattern that
     * matches a whole identifier rather than the beginning of a longer one, or {@code null}
     * where there is nothing to compare.
     *
     * <p>They are read from the two registries, so that a term added to either of them is
     * recognized without this test being edited. A distribution assembled without the
     * edition carries no registry of it, and there the name of the edition is the whole of
     * the check — which is what that tree needs, because the question it answers is whether
     * the removal was complete.
     */
    private static Pattern addedByTheEdition() {
        if (!Registry.editions().contains(EDITION)) {
            return null;
        }
        Set<String> earlier = new LinkedHashSet<>();
        for (Term term : Registry.forEdition(Registry.DEFAULT_EDITION).terms()) {
            earlier.add(term.id());
        }
        List<String> added = new ArrayList<>();
        for (Term term : Registry.forEdition(EDITION).terms()) {
            if (!earlier.contains(term.id())) {
                added.add(Pattern.quote(term.id()));
            }
        }
        if (added.isEmpty()) {
            return null;
        }
        return Pattern.compile("(?<![0-9A-Za-z])(" + String.join("|", added) + ")(?![0-9])");
    }
}
