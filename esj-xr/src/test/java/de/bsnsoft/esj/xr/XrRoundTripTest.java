package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.imports.ImportResult;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The round trip: every instance of the conformance corpus and every example of the
 * repository is imported, written back as an XR document and read again, and the values it
 * carries the second time are the values it carried the first time.
 *
 * <p>What is compared is the canonical form of the semantic identity of the document, the
 * object {@code {"semanticModel", "values"}} of the specification, section 8.2, byte for
 * byte. The whole canonical document cannot be compared, and for one reason that is not a
 * limitation of the exporter: the {@code source} member records the bytes a document was
 * read from, and the second read was of other bytes than the first. Everything that is a
 * statement about the invoice is inside what is compared.
 *
 * <p>{@code conformance/README.md} says in words what this test says in assertions.
 */
class XrRoundTripTest {

    /** The examples of the repository, in the order of {@code examples/README.md}. */
    private static final List<String> EXAMPLES = List.of(
            "minimal",
            "standard-invoice",
            "multiple-lines",
            "allowances",
            "charges",
            "self-billed",
            "credit-note",
            "b2c-gross",
            "extended",
            "extension-depth");

    /** The examples that carry an {@code extensions} subtree, which the XR cannot hold. */
    private static final List<String> WITH_EXTENSIONS = List.of("extended", "extension-depth");

    static List<String> corpus() {
        return Conformance.corpus();
    }

    /**
     * The examples whose values the XR representation holds. {@code b2c-gross} carries four
     * terms of an extension registry the XR model has no element for; it has a test of its
     * own below, which is that the exporter names every one of them instead of losing it
     * quietly.
     */
    static List<String> examples() {
        return EXAMPLES.stream().filter(name -> !name.equals("b2c-gross")).toList();
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void everyInstanceOfTheCorpusSurvivesTheRoundTrip(String instance) {
        SemanticDocument imported = new XrImporter().importXml(Conformance.instance(instance));

        ExportResult exported = new XrExporter().toXrWithReport(imported);

        assertEquals(List.of(), exported.report().notes(),
                instance + " reached the XR representation whole");
        assertEquals(text(Canonicalizer.canonicalSemanticBytes(imported)),
                text(Canonicalizer.canonicalSemanticBytes(reread(exported))),
                instance + " carries the same values after the round trip");
    }

    @ParameterizedTest
    @MethodSource("corpus")
    void everyInstanceOfTheCorpusIsWrittenTheSameWayTwice(String instance) {
        SemanticDocument imported = new XrImporter().importXml(Conformance.instance(instance));
        XrExporter exporter = new XrExporter();

        assertArrayEquals(exporter.toXr(imported), exporter.toXr(imported),
                instance + " is written byte for byte the same way twice");
    }

    @ParameterizedTest
    @MethodSource("examples")
    void everyExampleSurvivesTheRoundTrip(String example) {
        SemanticDocument document = EsjReader.strict().read(Instances.bytes(
                "/examples/" + example + ".esj.json"));

        ExportResult exported = new XrExporter().toXrWithReport(document);

        assertEquals(WITH_EXTENSIONS.contains(example)
                        ? exported.report().notes(ExportNote.Kind.EXTENSIONS_DROPPED)
                        : List.of(),
                exported.report().notes(),
                example + " reached the XR representation apart from its extension subtree");
        assertEquals(text(Canonicalizer.canonicalSemanticBytes(document)),
                text(Canonicalizer.canonicalSemanticBytes(reread(exported))),
                example + " carries the same values after the round trip");
    }

    @Test
    void theExtensionSubtreeOfAnExampleIsNamedInTheReport() {
        SemanticDocument document = EsjReader.strict().read(
                Instances.bytes("/examples/extended.esj.json"));

        ExportReport report = new XrExporter().toXrWithReport(document).report();

        assertEquals(2, report.notes(ExportNote.Kind.EXTENSIONS_DROPPED).size(),
                "one note per owner of an extension subtree");
        assertTrue(report.notes().stream()
                        .allMatch(note -> note.kind() == ExportNote.Kind.EXTENSIONS_DROPPED),
                "nothing else of that example stayed behind");
    }

    @Test
    void theExtensionTermsOfAnExampleAreNamedOneByOneInTheReport() {
        SemanticDocument document = EsjReader.strict().read(
                Instances.bytes("/examples/b2c-gross.esj.json"));

        ExportResult exported = new XrExporter().toXrWithReport(document);

        assertEquals(List.of("/BT-B2C-010", "/BG-25/0/BT-B2C-001", "/BG-25/0/BT-B2C-002",
                        "/BG-25/0/BT-B2C-003", "/BG-25/1/BT-B2C-001", "/BG-25/1/BT-B2C-002",
                        "/BG-25/1/BT-B2C-003", "/BG-25/2/BT-B2C-001", "/BG-25/2/BT-B2C-002",
                        "/BG-25/2/BT-B2C-003"),
                exported.report().notes(ExportNote.Kind.NO_ELEMENT).stream()
                        .map(ExportNote::location).toList());
        assertEquals(exported.report().notes(ExportNote.Kind.NO_ELEMENT),
                exported.report().notes(),
                "nothing else of that example stayed behind");

        SemanticDocument reread = reread(exported);

        assertTrue(reread.values().keySet().stream()
                        .noneMatch(path -> path.toString().contains("B2C")),
                "the XR representation carries no term of the extension");
        assertEquals(document.value(SemanticPath.of("/BG-25/0/BT-131")),
                reread.value(SemanticPath.of("/BG-25/0/BT-131")),
                "the core terms of the invoice survive the round trip");
    }

    private static SemanticDocument reread(ExportResult exported) {
        ImportResult result = new XrImporter().fromXrWithReport(exported.xr());
        assertEquals(List.of(), result.report().notes(),
                "the XR document this exporter wrote reads back without a note");
        return result.document();
    }

    private static String text(byte[] canonical) {
        return new String(canonical, StandardCharsets.UTF_8);
    }
}
