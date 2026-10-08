package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Holds the worked example of {@code SPEC.md}, appendix B to the fixture manifest.
 *
 * <p>The appendix prints two documents, the canonical bytes of each, their length, the two
 * digests and the object the semantic digest is taken over, with its length. The same documents
 * are files of {@code conformance/fixtures/annex-b/}, and the manifest records their digests and
 * the length of their canonical bytes as the reference implementation computes them. This test
 * reads the specification as text and compares every one of those numbers and byte sequences with
 * the files and with the manifest, so that the appendix cannot drift from what an implementation
 * answers and an implementer who checks the appendix by hand checks the manifest too.
 *
 * <p>The second document belongs to the later edition. Its half of the comparison runs where the
 * manifest part of that edition is checked in; the appendix prints it in either case, being prose.
 */
class AnnexBExampleTest {

    private static final Path SPECIFICATION = Path.of("..", "SPEC.md");

    private static final Path FIXTURES = Path.of("..", "conformance", "fixtures");

    private static final String CORE_DOCUMENT = "conformance/fixtures/annex-b/example";

    private static final String LATER_EDITION_DOCUMENT =
            "conformance/fixtures/annex-b/edition-2026-example";

    /** A fenced block of the appendix: its opening line names the language, if any. */
    private static final Pattern BLOCK = Pattern.compile("```(json)?\\n(.*?)\\n```", Pattern.DOTALL);

    static boolean theSpecificationIsThere() {
        return Files.exists(SPECIFICATION) && Files.exists(FIXTURES.resolve("manifest.json"));
    }

    static boolean theLaterEditionPartIsThere() {
        return theSpecificationIsThere()
                && Files.exists(FIXTURES.resolve("manifest-en16931-2026.json"));
    }

    @Test
    @EnabledIf("theSpecificationIsThere")
    void theFirstExampleOfTheAppendixIsWhatTheManifestRecords() {
        String appendix = appendix();
        String example = appendix.substring(0, appendix.indexOf("**The same content under"));
        check(example, CORE_DOCUMENT, "manifest.json");
    }

    @Test
    @EnabledIf("theLaterEditionPartIsThere")
    void theSecondExampleOfTheAppendixIsWhatTheManifestPartRecords() {
        String appendix = appendix();
        String example = appendix.substring(appendix.indexOf("**The same content under"));
        check(example, LATER_EDITION_DOCUMENT, "manifest-en16931-2026.json");
    }

    /**
     * Compares one example of the appendix: the pretty document with the file, the canonical
     * bytes with the twin, the lengths and the digests with the manifest, and the object of the
     * semantic digest with the canonical bytes it is cut from.
     */
    private static void check(String example, String document, String manifestFile) {
        List<String> blocks = new ArrayList<>();
        Matcher matcher = BLOCK.matcher(example);
        while (matcher.find()) {
            blocks.add(matcher.group(2));
        }
        assertEquals(4, blocks.size(), "the example prints a document, its canonical bytes, "
                + "its digests and the object of the semantic digest");
        String pretty = blocks.get(0);
        String canonical = blocks.get(1);
        String digests = blocks.get(2);
        String semanticObject = blocks.get(3);

        assertEquals(pretty + "\n", Fixtures.text(document + ".esj.json"),
                document + ".esj.json is the document the appendix prints");
        assertEquals(canonical, Fixtures.text(document + ".canonical.esj.json"),
                document + ".canonical.esj.json holds the canonical bytes the appendix prints");

        Map<String, String> entry = entry(manifestFile, document + ".esj.json");
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        assertEquals(Integer.parseInt(entry.get("canonicalBytes")), canonicalBytes.length);
        assertEquals(number(example, "Its canonical form is these\\s+(\\d+)\\s+bytes"), canonicalBytes.length,
                "the length the appendix states is the length of the bytes it prints");
        assertEquals(number(example, "SHA-256 over those\\s+(\\d+)\\s+bytes"), canonicalBytes.length);

        assertEquals("semantic digest  " + entry.get("semanticDigest") + "\n"
                        + "document digest  " + entry.get("documentDigest"), digests,
                "the digests the appendix prints are the ones the manifest records");
        assertEquals(entry.get("documentDigest"), sha256(canonicalBytes));

        byte[] semantic = semanticObject.getBytes(StandardCharsets.UTF_8);
        assertEquals(number(example, "over the\\s+(\\d+)\\s+bytes of the two-member object"), semantic.length,
                "the length the appendix states is the length of the object it prints");
        assertEquals(entry.get("semanticDigest"), sha256(semantic));
        int values = canonical.indexOf(",\"values\":");
        int model = canonical.indexOf("\"semanticModel\":");
        assertTrue(values > 0 && model > 0, canonical);
        assertEquals("{" + canonical.substring(model), semanticObject,
                "the object of the semantic digest is the edition and the values of the canonical bytes");
    }

    private static String appendix() {
        String specification = read(SPECIFICATION);
        int start = specification.indexOf("## Appendix B.");
        int end = specification.indexOf("## Appendix C.");
        assertTrue(start > 0 && end > start, "SPEC.md has appendix B before appendix C");
        return specification.substring(start, end);
    }

    private static int number(String text, String pattern) {
        Matcher matcher = Pattern.compile(pattern).matcher(text);
        assertTrue(matcher.find(), "the appendix states: " + pattern);
        return Integer.parseInt(matcher.group(1));
    }

    /** The scalar members of the entry of the {@code documents} section that names a file. */
    private static Map<String, String> entry(String manifestFile, String file) {
        try (JsonParser parser = new JsonFactory().createParser(FIXTURES.resolve(manifestFile).toFile())) {
            parser.nextToken();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String section = parser.currentName();
                parser.nextToken();
                if (!section.equals("documents")) {
                    parser.skipChildren();
                    continue;
                }
                while (parser.nextToken() == JsonToken.START_OBJECT) {
                    Map<String, String> members = new HashMap<>();
                    while (parser.nextToken() == JsonToken.FIELD_NAME) {
                        String name = parser.currentName();
                        JsonToken value = parser.nextToken();
                        if (value.isScalarValue()) {
                            members.put(name, parser.getText());
                        } else {
                            parser.skipChildren();
                        }
                    }
                    if (file.equals(members.get("file"))) {
                        return members;
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new AssertionError(manifestFile + " records no document " + file);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
