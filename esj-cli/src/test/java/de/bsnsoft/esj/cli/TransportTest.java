package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.bindings.BindingSyntax;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.bindings.WriteNote;
import de.bsnsoft.esj.bindings.WriteReport;
import de.bsnsoft.esj.bindings.WriterOptions;
import de.bsnsoft.esj.json.EsjReader;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Telling apart the two things a writer leaves behind.
 *
 * <p>A term of a registry that declares {@code "transport": "none"} was never meant to
 * reach a syntax, so the XML written without it is the whole invoice and the official
 * artefacts answer for the invoice. Anything else the writer could not place makes the XML
 * a different document, and a verdict over it would be about something else.
 *
 * <p>The writer makes the distinction, from the extension registries it was handed, and
 * says it in its report: a term untransported by design is a note of its own and no loss.
 * This class reads that answer. A run that did not load a registry hands the writer nothing
 * that registry declares, so its terms are losses to it.
 */
class TransportTest {

    /** The extensions of a run that named {@code --extension b2c}. */
    private static final Extensions B2C =
            new Extensions(EnumSet.of(Extensions.Extension.B2C));

    /** A document that lost nothing needs no declaration to be completely written. */
    @Test
    void answersWithNothingLeftBehindForAReportWithoutLosses() {
        assertEquals(Optional.of(List.of()), Transport.byDesign(report(0)));
    }

    /** Every term left behind is one the B2C registry declares untransported. */
    @Test
    void groupsTheTermsOfADeclaringRegistryUnderThatRegistry() {
        WriteReport report = report(0,
                byDesign("/BT-B2C-010"), byDesign("/BG-25/0/BT-B2C-001"),
                byDesign("/BG-25/1/BT-B2C-001"));

        List<WrittenCheck.ByDesign> answer = Transport.byDesign(report).orElseThrow();

        assertEquals(1, answer.size(), answer.toString());
        assertEquals("ESJ-B2C 0.1", answer.get(0).registry());
        assertEquals(List.of("BT-B2C-010", "BT-B2C-001"), answer.get(0).terms(),
                "one entry per term, however many values stood at it");
    }

    /** A term nothing says where to put leaves the row not applicable. */
    @Test
    void refusesATermTheWriterCouldNotPlace() {
        assertEquals(Optional.empty(), Transport.byDesign(report(1, lost("/BG-1/0/BT-22"))));
    }

    /** One term that was meant to travel is enough: the written XML is another document. */
    @Test
    void refusesAReportThatMixesTheTwo() {
        WriteReport report = report(1, byDesign("/BT-B2C-010"), lost("/BG-1/0/BT-22"));

        assertEquals(Optional.empty(), Transport.byDesign(report));
        assertEquals(1, Transport.entries(report).size(),
                "the terms left behind by design are named all the same");
    }

    /**
     * A value the writer counted as dropped without a note of its own is a value nobody
     * accounted for, and a run does not pass off such a document as completely written.
     */
    @Test
    void refusesAReportWhoseCountsDoNotAddUp() {
        assertEquals(Optional.empty(), Transport.byDesign(report(1, byDesign("/BT-B2C-010"))));
    }

    /** A note about the document as a whole that is a loss refuses the row as well. */
    @Test
    void refusesANoteThatNamesNoPath() {
        WriteReport report = new WriteReport(BindingSyntax.CII, 4, 0,
                List.of(new WriteNote(WriteNote.Kind.EXTENSIONS_DROPPED, "",
                        "data without a business term has no place in a syntax")));

        assertTrue(Transport.byDesign(report).isEmpty(),
                "the report carries a loss nothing declared");
    }

    /**
     * The writer is the one that knows: handed the registries a run loaded, it names the
     * terms of the B2C example as left behind by design; handed nothing, it cannot tell
     * them from terms it has no place for.
     */
    @Test
    void readsTheDistinctionTheWriterMadeFromTheRegistriesItWasHanded() {
        SemanticDocument document = EsjReader.strict()
                .read(Fixtures.bytes("examples/b2c-gross.esj.json"));

        WriteReport handed = CiiWriter.writeWithReport(document, WriterOptions.builder()
                .extensions(B2C.registries()).build()).report();
        WriteReport unhanded = CiiWriter.writeWithReport(document,
                WriterOptions.defaults()).report();

        assertEquals(List.of(new WrittenCheck.ByDesign("ESJ-B2C 0.1",
                        List.of("BT-B2C-010", "BT-B2C-001", "BT-B2C-002", "BT-B2C-003"))),
                Transport.byDesign(handed).orElseThrow());
        assertEquals(Optional.empty(), Transport.byDesign(unhanded));
    }

    /** A write report of the given shape. */
    private static WriteReport report(int dropped, WriteNote... notes) {
        return new WriteReport(BindingSyntax.CII, 12, dropped, List.of(notes));
    }

    /** The note a writer makes for a term its registry declares untransported. */
    private static WriteNote byDesign(String path) {
        return new WriteNote(WriteNote.Kind.TERM_BY_DESIGN, path,
                "left behind by design", "ESJ-B2C 0.1");
    }

    /** The note a writer makes for a term its binding table gives no place. */
    private static WriteNote lost(String path) {
        return new WriteNote(WriteNote.Kind.TERM_NOT_BOUND, path,
                "the binding table gives the term no place in this syntax");
    }
}
