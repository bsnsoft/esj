package de.bsnsoft.esj.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Checks that the TypeScript package carries the version of this project.
 *
 * <p>The C# binding reads the version from the root {@code pom.xml} when it is built, so it
 * cannot fall behind. The npm package has to name it in {@code package.json} and in its lock
 * file, and a release that sets a new version on the Maven project has to set it there too:
 * {@code node bindings/typescript/scripts/sync-version.mjs} does. This test fails the Maven
 * build until it has, so the version is set before a release is tagged rather than noticed
 * by the job that tests the bindings after it.
 *
 * <p>This module is the one that may look at the tree, and the paths below are relative to
 * the repository root, one directory up.
 */
class BindingVersionTest {

    private static final Path REPOSITORY = Path.of("..");

    private static final Pattern PROJECT_VERSION = Pattern.compile(
            "<artifactId>en16931-semantic-json</artifactId>\\s*<version>([^<]+)</version>");

    /** The member {@code "version": "…"} of a JSON object, as npm writes it. */
    private static final Pattern NPM_VERSION = Pattern.compile("\"version\": \"([^\"]+)\"");

    @Test
    void theTypeScriptPackageCarriesTheVersionOfTheProject() throws IOException {
        Matcher project = PROJECT_VERSION.matcher(read("pom.xml"));
        assertTrue(project.find(), "the root pom.xml names the version of the project");
        String version = project.group(1);

        String manifest = read("bindings/typescript/package.json");
        assertEquals(List.of(version), versions(manifest, 1),
                "bindings/typescript/package.json; run node bindings/typescript/scripts/"
                        + "sync-version.mjs");
        String lock = read("bindings/typescript/package-lock.json");
        assertEquals(List.of(version, version), versions(lock, 2),
                "bindings/typescript/package-lock.json; run node bindings/typescript/scripts/"
                        + "sync-version.mjs");
    }

    /** The first versions a JSON file names: the package's own, before any dependency's. */
    private static List<String> versions(String json, int count) {
        List<String> found = new ArrayList<>();
        Matcher matcher = NPM_VERSION.matcher(json);
        while (found.size() < count && matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String read(String file) throws IOException {
        return Files.readString(REPOSITORY.resolve(file), StandardCharsets.UTF_8);
    }
}
