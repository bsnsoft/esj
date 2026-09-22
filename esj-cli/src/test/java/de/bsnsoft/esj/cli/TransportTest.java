package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.bindings.BindingSyntax;
import de.bsnsoft.esj.bindings.WriteNote;
import de.bsnsoft.esj.bindings.WriteReport;
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
 * <p>The distinction is made from the extension registries the run loaded and from the
 * counts of the write report, never from a term identifier's spelling: a run that did not
 * load a registry has nothing that registry declares to go on.
 */
class TransportTest {

    /** The extensions of a run that named {@code --extension b2c}. */
    private static final Extensions B2C =
            new Extensions(EnumSet.of(Extensions.Extension.B2C));

    /** The extensions of a run that named {@code --extension xrechnung}. */
    private static final Extensions XRECHNUNG =
            new Extensions(EnumSet.of(Extensions.Extension.XRECHNUNG));

    /** A document that lost nothing needs no declaration to be completely written. */
    @Test
    void answersWithNothingLeftBehindForAReportWithoutLosses() {
        Optional<List<WrittenCheck.ByDesign>> answer =
                Transport.byDesign(report(0), Extensions.none());

        assertEquals(Optional.of(List.of()), answer);
    }

    /** Every term left behind is one the B2C registry declares untransported. */
    @Test
    void groupsTheTermsOfADeclaringRegistryUnderThatRegistry() {
        WriteReport report = report(3,
                note("/BT-B2C-010"), note("/BG-25/0/BT-B2C-001"), note("/BG-25/1/BT-B2C-001"));

        List<WrittenCheck.ByDesign> answer =
                Transport.byDesign(report, B2C).orElseThrow();

        assertEquals(1, answer.size(), answer.toString());
        assertEquals("ESJ-B2C 0.1", answer.get(0).registry());
        assertEquals(List.of("BT-B2C-010", "BT-B2C-001"), answer.get(0).terms(),
                "one entry per term, however many values stood at it");
    }

    /** A registry that declares nothing leaves the row not applicable, as before. */
    @Test
    void refusesATermOfARegistryWithoutTheDeclaration() {
        WriteReport report = report(1, note("/BG-DEX-01/0/BT-DEX-001"));

        assertEquals(Optional.empty(), Transport.byDesign(report, XRECHNUNG));
    }

    /** One term that was meant to travel is enough: the written XML is another document. */
    @Test
    void refusesAReportThatMixesTheTwo() {
        WriteReport report = report(2, note("/BT-B2C-010"), note("/BG-1/0/BT-22"));

        assertEquals(Optional.empty(), Transport.byDesign(report,
                new Extensions(EnumSet.allOf(Extensions.Extension.class))));
    }

    /** The declaration is only in reach where the run loaded the registry that made it. */
    @Test
    void refusesATermOfARegistryThisRunDidNotLoad() {
        WriteReport report = report(1, note("/BT-B2C-010"));

        assertEquals(Optional.empty(), Transport.byDesign(report, Extensions.none()));
    }

    /**
     * A value the writer counted as dropped without a note of its own is a value nobody
     * accounted for, and a run does not pass off such a document as completely written.
     */
    @Test
    void refusesAReportWhoseCountsDoNotAddUp() {
        WriteReport report = report(2, note("/BT-B2C-010"));

        assertEquals(Optional.empty(), Transport.byDesign(report, B2C));
    }

    /** A note about the document as a whole names no term and belongs to no registry. */
    @Test
    void refusesANoteThatNamesNoPath() {
        WriteReport report = new WriteReport(BindingSyntax.CII, 4, 1,
                List.of(new WriteNote(WriteNote.Kind.EXTENSIONS_DROPPED, "",
                        "data without a business term has no place in a syntax")));

        assertTrue(Transport.byDesign(report, B2C).isEmpty(),
                "the report carries a loss nothing declared");
    }

    /** A write report of the given shape: values dropped, one note each. */
    private static WriteReport report(int dropped, WriteNote... notes) {
        return new WriteReport(BindingSyntax.CII, 12, dropped, List.of(notes));
    }

    /** The note a writer makes for a term its binding table carries no entry for. */
    private static WriteNote note(String path) {
        return new WriteNote(WriteNote.Kind.TERM_UNKNOWN, path,
                "the binding table of this syntax carries no entry for "
                        + path.substring(path.lastIndexOf('/') + 1));
    }
}
