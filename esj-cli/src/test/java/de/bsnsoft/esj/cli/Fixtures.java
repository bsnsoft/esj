package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The material the tests read: the examples of the repository, the conformance corpus and
 * the golden files of this module, all from the classpath, where the build copies them to.
 *
 * <p>The tool reads files from a file system rather than from a classpath, so a fixture
 * that a test wants to pass as a file name is written into a temporary directory first.
 * A test whose output has to be compared byte for byte passes the fixture on the standard
 * input instead, because the name of a temporary file is different on every run and would
 * be printed in the output.
 */
final class Fixtures {

    /** One line of the checksum block: a lowercase digest, two spaces, a relative path. */
    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}  (\\S.*)$");

    private Fixtures() {
        throw new AssertionError("no instances");
    }

    /** Returns a resource of the test classpath as bytes. */
    static byte[] bytes(String resource) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/" + resource)) {
            assertNotNull(in, "the resource " + resource + " is on the test classpath");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns a resource of the test classpath as text. */
    static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }

    /**
     * Writes a resource into a directory and returns the path to pass on a command line.
     *
     * @param directory the directory to write into, normally a {@code @TempDir}
     * @param resource  the resource of the test classpath
     * @return the path of the written file
     */
    static String file(Path directory, String resource) {
        Path target = directory.resolve(resource.substring(resource.lastIndexOf('/') + 1));
        try {
            Files.write(target, bytes(resource));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target.toString();
    }

    /**
     * Returns the relative paths of the conformance corpus, in the order
     * {@code conformance/kosit/README.md} lists them with their digests. That block is
     * the attribution and the enumeration at once, so a file cannot be in one and out of
     * the other.
     *
     * @return the paths below {@code conformance/kosit/}
     */
    static List<String> corpus() {
        List<String> instances = new ArrayList<>();
        for (String line : text("conformance/kosit/README.md").split("\\R")) {
            Matcher matcher = CHECKSUM.matcher(line);
            if (matcher.matches()) {
                instances.add(matcher.group(1));
            }
        }
        assertFalse(instances.isEmpty(), "conformance/kosit/README.md lists the instances");
        return instances;
    }

    /**
     * Copies a whole directory of the test classpath into a directory and returns the
     * path of the copy.
     *
     * <p>Some fixtures are not one file. A render template names the letterhead, the logo
     * and the fonts it needs beside it and reads them out of its own directory, so a test
     * that passes one on a command line has to hand the tool the directory rather than
     * the one file.
     *
     * @param into      the directory to copy into, normally a {@code @TempDir}
     * @param resources the directory of the test classpath, ending in a slash
     * @return the path of the copy
     */
    static Path directory(Path into, String resources) {
        Path target = into.resolve(resources);
        try {
            Path source = Path.of(Fixtures.class.getResource("/" + resources).toURI());
            Files.createDirectories(target);
            try (Stream<Path> files = Files.list(source)) {
                for (Path file : files.toList()) {
                    Files.copy(file, target.resolve(file.getFileName().toString()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("the classpath directory " + resources
                    + " is not a usable location", e);
        }
        return target;
    }

    /**
     * Writes bytes into a directory and returns the path to pass on a command line.
     *
     * @param directory the directory to write into
     * @param name      the file name
     * @param content   the content
     * @return the path of the written file
     */
    static String write(Path directory, String name, byte[] content) {
        Path target = directory.resolve(name);
        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target.toString();
    }
}
