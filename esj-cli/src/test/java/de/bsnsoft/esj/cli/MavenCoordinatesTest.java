package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * What a build of someone else writes down: the modules this project publishes, the bill of
 * materials that carries their version, and the coordinates the pages print. Three things can
 * go stale between one release and the next without any other test noticing — a module added
 * to the reactor and forgotten in the bill of materials, the command line tool slipping into
 * the deployment although it ships as a release asset, and a version left behind on a page.
 * This test holds all three against {@code pom.xml}.
 */
class MavenCoordinatesTest {

    /** Where the repository is; the build hands it over because a page is not a resource. */
    private static final String REPOSITORY_PROPERTY = "esj.repository";

    /** The version of the reactor, handed over the same way. */
    private static final String VERSION_PROPERTY = "esj.version";

    /** A module of the aggregator. */
    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");

    /** An artifact identifier, wherever one stands. */
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>([^<]+)</artifactId>");

    /** An artifact the deployment leaves out. */
    private static final Pattern EXCLUDED = Pattern.compile("<excludeArtifact>([^<]+)</excludeArtifact>");

    /** A coordinate of this project printed as a dependency. */
    private static final Pattern XML_COORDINATE = Pattern.compile(
            "<groupId>de\\.bsnsoft\\.esj</groupId>\\s*<artifactId>([^<]+)</artifactId>"
                    + "(?:\\s*<version>([^<]+)</version>)?");

    /** A coordinate of this project printed in the short form of a sentence. */
    private static final Pattern SHORT_COORDINATE =
            Pattern.compile("de\\.bsnsoft\\.esj:([A-Za-z0-9._-]+)(?::([0-9][A-Za-z0-9.+-]*))?");

    /** The module that is built like every other and is on no repository. */
    private static final String THE_TOOL = "esj-cli";

    /** The module that carries the version of the others. */
    private static final String THE_BOM = "esj-bom";

    /** The directories a page is never looked for in: build output and foreign packages. */
    private static final Set<String> SKIPPED = Set.of("target", "out", "node_modules", ".git");

    @Test
    void theBillOfMaterialsCarriesEveryPublishedModule() {
        Set<String> published = new LinkedHashSet<>(modules());
        published.remove(THE_TOOL);
        published.remove(THE_BOM);

        Set<String> managed = new LinkedHashSet<>();
        String bom = read(repository().resolve(THE_BOM).resolve("pom.xml"));
        Matcher artifact = ARTIFACT_ID.matcher(block(bom, "dependencyManagement"));
        while (artifact.find()) {
            managed.add(artifact.group(1));
        }

        assertEquals(published, managed, THE_BOM + " has to manage every published module");
    }

    @Test
    void theDeploymentLeavesOutTheCommandLineTool() {
        String root = read(repository().resolve("pom.xml"));
        Set<String> excluded = new LinkedHashSet<>();
        Matcher exclusion = EXCLUDED.matcher(block(root, "excludeArtifacts"));
        while (exclusion.find()) {
            excluded.add(exclusion.group(1));
        }

        assertEquals(Set.of(THE_TOOL), excluded, "the deployment leaves out the tool and nothing else");
    }

    /**
     * The version of the last release, read from the first dated heading of the changelog;
     * {@code null} while nothing has been released.
     */
    private static String lastRelease(Path repository) throws IOException {
        Pattern dated = Pattern.compile("^## \\[(\\d+\\.\\d+\\.\\d+)\\] \u2014 \\d{4}-\\d{2}-\\d{2}", Pattern.MULTILINE);
        Matcher found = dated.matcher(read(repository.resolve("CHANGELOG.md")));
        return found.find() ? found.group(1) : null;
    }

    @Test
    void everyCoordinateOnAPageNamesAModuleAndAVersionOfThisProject() throws IOException {
        Path repository = repository();
        Set<String> modules = modules();
        String snapshot = System.getProperty(VERSION_PROPERTY);
        assertTrue(snapshot != null && !snapshot.isBlank(), VERSION_PROPERTY + " is not set");
        String released = snapshot.endsWith("-SNAPSHOT")
                ? snapshot.substring(0, snapshot.length() - "-SNAPSHOT".length())
                : snapshot;
        // What a reader can depend on today: the version the last dated entry of the
        // changelog names. A page that offers it is right until the next release.
        String last = lastRelease(repository);

        List<String> wrong = new ArrayList<>();
        int coordinates = 0;
        for (Path page : pages(repository)) {
            String text = read(page);
            String name = repository.relativize(page).toString();
            for (Pattern form : List.of(XML_COORDINATE, SHORT_COORDINATE)) {
                Matcher found = form.matcher(text);
                while (found.find()) {
                    coordinates++;
                    String artifact = found.group(1);
                    String version = found.group(2);
                    if (!modules.contains(artifact)) {
                        wrong.add(name + ": " + artifact + " is no module of this project");
                    } else if (THE_TOOL.equals(artifact)) {
                        wrong.add(name + ": " + artifact + " is on no repository");
                    }
                    if (version != null && !version.equals(released) && !version.equals(snapshot)
                            && !version.equals(last)) {
                        wrong.add(name + ": " + artifact + " is offered as " + version
                                + ", and this project is at " + snapshot
                                + (last == null ? "" : " with " + last + " released"));
                    }
                }
            }
        }

        assertEquals(List.of(), wrong, "coordinates a reader would copy");
        assertTrue(coordinates >= 3, "found only " + coordinates + " coordinates; has the form changed?");
    }

    /** The modules of the aggregator, in the order it names them. */
    private static Set<String> modules() {
        Set<String> modules = new LinkedHashSet<>();
        Matcher module = MODULE.matcher(read(repository().resolve("pom.xml")));
        while (module.find()) {
            modules.add(module.group(1));
        }
        assertTrue(modules.size() > 5, "the aggregator names " + modules.size() + " modules");
        return modules;
    }

    /** The content of one element of a POM, with the whitespace between tags removed. */
    private static String block(String pom, String element) {
        int open = pom.indexOf('<' + element + '>');
        int close = pom.indexOf("</" + element + '>');
        assertTrue(open >= 0 && close > open, element + " is not in the POM");
        return pom.substring(open, close).replaceAll(">\\s+<", "><");
    }

    /** Every page of the repository. */
    private static List<Path> pages(Path repository) throws IOException {
        try (Stream<Path> tree = Files.walk(repository)) {
            return tree.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".md"))
                    .filter(file -> {
                        for (Path part : repository.relativize(file)) {
                            if (SKIPPED.contains(part.toString())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .sorted()
                    .toList();
        }
    }

    /** The repository the build handed over. */
    private static Path repository() {
        String property = System.getProperty(REPOSITORY_PROPERTY);
        assertTrue(property != null && !property.isBlank(), REPOSITORY_PROPERTY + " is not set");
        return Path.of(property).toAbsolutePath().normalize();
    }

    /** One file, as text. */
    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
