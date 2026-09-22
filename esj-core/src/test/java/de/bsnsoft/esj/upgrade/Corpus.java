package de.bsnsoft.esj.upgrade;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.EsjReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The documents the upgrade is measured over: the conformance corpus as semantic
 * documents, and the examples of the repository.
 *
 * <p>Both directories are on the test classpath, and the corpus is read from there as
 * files rather than as resources because the measurement needs their names.
 */
final class Corpus {

    private Corpus() {
    }

    /** The corpus instances as semantic documents, by their file name. */
    static List<String> corpus() {
        return names("/conformance/esj");
    }

    /**
     * The example documents of the repository, without the canonical golden files and
     * without the negative fixtures of {@code examples/invalid}, which are not documents.
     */
    static List<String> examples() {
        List<String> examples = new ArrayList<>();
        for (String name : names("/examples")) {
            if (name.endsWith(".esj.json") && !name.contains(".canonical.")
                    && !name.contains("/invalid/")) {
                examples.add(name);
            }
        }
        return examples;
    }

    /** Returns a document of the corpus or of the examples, read by its name. */
    static SemanticDocument document(String name) {
        return EsjReader.strict().read(bytes(name));
    }

    /** Returns the bytes of a document of the corpus or of the examples. */
    static byte[] bytes(String name) {
        try (InputStream in = Corpus.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is not on the test classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns a text file of the test classpath. */
    static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }

    /** Tells whether a file is on the test classpath. */
    static boolean has(String resource) {
        return Corpus.class.getResource(resource) != null;
    }

    private static List<String> names(String directory) {
        URL root = Corpus.class.getResource(directory);
        if (root == null) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(Path.of(root.toURI()))) {
            Path base = Path.of(root.toURI());
            List<String> names = new ArrayList<>();
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                names.add(directory + "/" + base.relativize(file).toString()
                        .replace(java.io.File.separatorChar, '/'));
            }
            return List.copyOf(names);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
