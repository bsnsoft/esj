package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every snippet a page of this repository prints is run by a test, and the test says which
 * page and which section it belongs to in a comment above the snippet:
 *
 * <pre>{@code // docs/java-api.md: Writing XML}</pre>
 *
 * <p>That comment is a promise, and until this test existed nothing kept it: six markers
 * still named sections of the README that the README lost when it became an entry point,
 * so a reader following one arrived nowhere and a page could drop a snippet without any
 * test noticing. This test reads the markers and the pages and holds the one against the
 * other — the page has to exist at the path the marker spells, and the section has to be a
 * heading of that page.
 *
 * <p>A marker names the page by its path from the root of the repository, so that a reader
 * can open it. A section may be followed by a qualifier after a comma or an em dash, which
 * is how a marker points into a section that prints more than one snippet. The terminator
 * of a snippet is {@code // end} and names no page.
 *
 * <p>What makes a comment a marker is that it names a page: the token before the colon
 * ends in {@code .md}, or it is the name of a page without that suffix. The second form is
 * caught so that the short spellings earlier phases used are corrected rather than
 * silently skipped; an ordinary comment whose first word happens to end in a colon is not
 * a marker and is left alone.
 */
class SnippetMarkerTest {

    /** Where the repository is; the build hands it over because a page is not a resource. */
    private static final String REPOSITORY_PROPERTY = "esj.repository";

    /** A comment whose first word ends in a colon: a marker if that word names a page. */
    private static final Pattern MARKER = Pattern.compile("^\\s*//\\s(\\S+):\\s(\\S.*?)\\s*$");

    /** The directories a page is never looked for in: build output and foreign packages. */
    private static final Set<String> SKIPPED = Set.of("target", "out", "node_modules", ".git");

    /** Fewer markers than this and the form has changed, which would disable this test. */
    private static final int AT_LEAST = 40;

    /** How many pages of that name a complaint offers before the list stops helping. */
    private static final int CANDIDATES = 5;

    @Test
    void everyMarkerNamesAPageAndAHeadingThatExist() throws IOException {
        Path repository = repository();
        Map<String, List<String>> pages = pages(repository);
        Set<String> pageNames = new LinkedHashSet<>();
        for (String page : pages.keySet()) {
            String file = page.substring(page.lastIndexOf('/') + 1);
            pageNames.add(file.substring(0, file.length() - ".md".length()));
        }

        List<String> wrong = new ArrayList<>();
        int markers = 0;
        for (Path source : testSources(repository)) {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int number = 1; number <= lines.size(); number++) {
                Matcher marker = MARKER.matcher(lines.get(number - 1));
                if (!marker.matches()) {
                    continue;
                }
                String page = marker.group(1);
                String section = marker.group(2);
                if (!page.endsWith(".md") && !pageNames.contains(page)) {
                    continue;
                }
                markers++;
                String wrongOne = check(pages, page, section);
                if (wrongOne != null) {
                    wrong.add(repository.relativize(source) + ":" + number + " — " + wrongOne);
                }
            }
        }

        assertEquals(List.of(), wrong,
                "a snippet marker names a page or a section that does not exist");
        int found = markers;
        assertTrue(found >= AT_LEAST, () -> "only " + found + " snippet markers were found,"
                + " and there were at least " + AT_LEAST + ": either the form of the marker has"
                + " changed, in which case this test has to follow it, or the snippets are no"
                + " longer pinned to the pages that print them");
    }

    /**
     * Returns what is wrong with one marker, or {@code null} where nothing is.
     *
     * @param pages   every page of the repository and its headings
     * @param page    the page the marker names
     * @param section the section the marker names
     * @return the complaint, or {@code null}
     */
    private static String check(Map<String, List<String>> pages, String page, String section) {
        List<String> headings = pages.get(page);
        if (headings == null) {
            String named = page.endsWith(".md") ? page : page + ".md";
            List<String> candidates = pages.keySet().stream()
                    .filter(known -> known.endsWith("/" + named) || known.equals(named))
                    .limit(CANDIDATES)
                    .toList();
            return "no page " + page + " in this repository"
                    + (candidates.isEmpty() ? "" : "; a marker names a page by its path from the"
                    + " root of the repository, and the candidates are " + candidates);
        }
        for (String heading : headings) {
            if (section.equals(heading)
                    || section.startsWith(heading + ", ")
                    || section.startsWith(heading + " — ")) {
                return null;
            }
        }
        return page + " has no section \"" + section + "\"; its headings are " + headings;
    }

    /**
     * Returns the headings of every page of the repository, by the path a marker names it
     * with. A line of a fenced block is not a heading however it begins.
     *
     * @param repository the root of the repository
     * @return the headings of each page, in the order the page prints them
     * @throws IOException if a page cannot be read
     */
    private static Map<String, List<String>> pages(Path repository) throws IOException {
        Map<String, List<String>> pages = new LinkedHashMap<>();
        for (Path page : walk(repository, ".md")) {
            List<String> headings = new ArrayList<>();
            boolean fenced = false;
            for (String line : Files.readAllLines(page, StandardCharsets.UTF_8)) {
                if (line.startsWith("```")) {
                    fenced = !fenced;
                } else if (!fenced && line.startsWith("#")) {
                    headings.add(line.replaceFirst("^#+\\s*", "").trim());
                }
            }
            pages.put(relative(repository, page), List.copyOf(headings));
        }
        return pages;
    }

    /**
     * Returns the test sources of every module of the reactor.
     *
     * @param repository the root of the repository
     * @return the source files, in a stable order
     * @throws IOException if the tree cannot be read
     */
    private static List<Path> testSources(Path repository) throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path java : walk(repository, ".java")) {
            if (relative(repository, java).contains("/src/test/java/")) {
                sources.add(java);
            }
        }
        return sources;
    }

    /**
     * Returns every file of the repository with that suffix, build output left out.
     *
     * @param repository the root of the repository
     * @param suffix     the file name suffix to keep
     * @return the files, in a stable order
     * @throws IOException if the tree cannot be read
     */
    private static List<Path> walk(Path repository, String suffix) throws IOException {
        try (Stream<Path> tree = Files.walk(repository)) {
            return tree.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(suffix))
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
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** Returns the path of a file from the root of the repository, with {@code /}. */
    private static String relative(Path repository, Path file) {
        return repository.relativize(file).toString().replace('\\', '/');
    }

    /** Returns the root of the repository, which the build hands over as a property. */
    private static Path repository() {
        String property = System.getProperty(REPOSITORY_PROPERTY);
        assertTrue(property != null && !property.isBlank(),
                () -> String.format(Locale.ROOT, "the build sets -D%s to the root of the"
                        + " repository, because the pages of a repository are not resources of a"
                        + " module", REPOSITORY_PROPERTY));
        Path repository = Path.of(property).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(repository.resolve("README.md")),
                () -> repository + " is no repository of this project: it carries no README.md");
        return repository;
    }
}
