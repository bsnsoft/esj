package de.bsnsoft.esj.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The cross-implementation check of the specification, section 3.4: canonicalizing a
 * pretty example reproduces its checked-in canonical twin byte for byte.
 */
class GoldenFilesTest {

    private final EsjReader reader = EsjReader.strict();

    static List<String> examples() {
        return Examples.NAMES;
    }

    static List<String> coreOnlyExamples() {
        return Examples.NAMES.stream().filter(name -> !name.equals("extended")).toList();
    }

    @ParameterizedTest
    @MethodSource("examples")
    void canonicalizingThePrettyExampleReproducesItsGoldenFile(String name) {
        assertArrayEquals(Examples.canonical(name), Canonicalizer.canonicalize(Examples.pretty(name)),
                name);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void canonicalizingTheGoldenFileChangesNothing(String name) {
        byte[] golden = Examples.canonical(name);
        assertArrayEquals(golden, Canonicalizer.canonicalize(golden), name);
        assertArrayEquals(golden, Canonicalizer.canonicalize(Canonicalizer.canonicalize(golden)),
                name);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void aGoldenFileIsOneLineOfUtf8WithNoTrailingNewline(String name) {
        byte[] golden = Examples.canonical(name);
        assertEquals('{', golden[0], name);
        assertEquals('}', golden[golden.length - 1], name);
        String text = new String(golden, StandardCharsets.UTF_8);
        assertEquals(-1, text.indexOf('\n'), name);
        assertEquals(-1, text.indexOf('\r'), name);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void thePrettyFormOfAGoldenFileReadsBackAsTheSameDocument(String name) {
        SemanticDocument fromGolden = reader.read(Examples.canonical(name));
        byte[] pretty = EsjWriter.pretty().toBytes(fromGolden);
        assertEquals(fromGolden, reader.read(pretty), name);
        assertArrayEquals(Examples.canonical(name), Canonicalizer.canonicalize(pretty), name);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void thePrettyFormIsIndentedWithTwoSpacesAndEndsWithOneNewline(String name) {
        String pretty = EsjWriter.pretty().toText(reader.read(Examples.canonical(name)));
        assertTrue(pretty.endsWith("}\n"), name);
        assertEquals(-1, pretty.indexOf('\r'), name);
        assertTrue(pretty.contains("\n  \"format\": \"EN16931-Semantic-JSON\",\n"), name);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void bothDigestsOfAGoldenFileAreSixtyFourLowercaseHexadecimalDigits(String name) {
        SemanticDocument document = reader.read(Examples.canonical(name));
        assertTrue(Canonicalizer.documentDigest(document).matches("[0-9a-f]{64}"), name);
        assertTrue(Canonicalizer.semanticDigest(document).matches("[0-9a-f]{64}"), name);
    }

    @ParameterizedTest
    @MethodSource("coreOnlyExamples")
    void thePrettyExampleInTheRepositoryIsWhatThisWriterWrites(String name) {
        assertArrayEquals(Examples.pretty(name),
                EsjWriter.pretty().toBytes(reader.read(Examples.pretty(name))), name);
    }

    @Test
    void thePrettyExampleWithExtensionsDiffersFromThisWriterOnlyInNumberSpelling() {
        byte[] stored = Examples.pretty("extended");
        byte[] written = EsjWriter.pretty().toBytes(reader.read(stored));
        assertNotEquals(new String(stored, StandardCharsets.UTF_8),
                new String(written, StandardCharsets.UTF_8));
        assertArrayEquals(Examples.canonical("extended"), Canonicalizer.canonicalize(written));
        assertEquals(reader.read(stored), reader.read(written));
    }
}
