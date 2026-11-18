package de.bsnsoft.esj.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Holds every module of the repository to one way of decoding base64 a document carried:
 * through {@link Base64Text}, which hands the runtime's decoder only whole four-character
 * groups (CVE-2026-114938).
 *
 * <p>Two other places may take a decoder of the runtime: {@code Base64Text} itself, and
 * {@code SemanticValue.asBytes()}, which checks the canonical padded form of the
 * specification before it decodes. A decoder that is only wrapped around a stream is
 * allowed anywhere: the stream decodes character by character and never calls the
 * routine the runtime replaces.
 */
class Base64DecodeSitesTest {

    private static final Path REPOSITORY = Path.of("..");

    private static final Pattern DECODER =
            Pattern.compile("get(?:Mime|Url)?Decoder\\s*\\(\\s*\\)(\\s*\\.\\s*(\\w+))?");

    private static final Set<String> ALLOWED = Set.of(
            "esj-core/src/main/java/de/bsnsoft/esj/internal/Base64Text.java",
            "esj-core/src/main/java/de/bsnsoft/esj/SemanticValue.java");

    @Test
    void noModuleDecodesBase64PastTheCheck() throws IOException {
        List<String> found = new ArrayList<>();
        int files;
        try (Stream<Path> modules = Files.list(REPOSITORY)) {
            List<Path> sources = modules
                    .map(module -> module.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .flatMap(Base64DecodeSitesTest::javaFiles)
                    .toList();
            files = sources.size();
            for (Path source : sources) {
                String name = REPOSITORY.relativize(source).toString().replace('\\', '/');
                if (ALLOWED.contains(name)) {
                    continue;
                }
                Matcher decoder = DECODER.matcher(
                        Files.readString(source, StandardCharsets.UTF_8));
                while (decoder.find()) {
                    if (!"wrap".equals(decoder.group(2))) {
                        found.add(name + ": " + decoder.group());
                    }
                }
            }
        }
        assertTrue(files > 100, () -> "the scan found only " + files + " source files");
        assertEquals(List.of(), found);
    }

    private static Stream<Path> javaFiles(Path directory) {
        try {
            return Files.walk(directory).filter(path -> path.toString().endsWith(".java"))
                    .toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
