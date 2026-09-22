package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The figures the two pages a reader meets first carry, against the ledgers that compute
 * them.
 *
 * <p>{@code README.md} and {@code docs/conformance.md} repeat numbers that are measured
 * elsewhere — {@code conformance/writers/ubl-roundtrip.json}, {@code cii-roundtrip.json}
 * and {@code matrix.json}, each recomputed on every build by a test of
 * {@code esj-bindings}. A page that repeats a number is a page that can fall behind it,
 * and the failure mode of a repeated number is that nothing fails. This test is what
 * fails.
 */
class ConformanceFiguresTest {

    private static final String UBL = "conformance/writers/ubl-roundtrip.json";
    private static final String CII = "conformance/writers/cii-roundtrip.json";
    private static final String MATRIX = "conformance/writers/matrix.json";

    /** The sentence of the README that counts what the two writers produce. */
    @Test
    void theReadmeCountsWhatTheWritersProduce() {
        String readme = Fixtures.text("README.md");
        assertSentence(readme, number(CII, "corpus", "accepted")
                + " are accepted as a cross");
        assertSentence(readme, number(CII, "corpus", "identical")
                + " read back as the document they were written from");
        assertSentence(readme, number(UBL, "corpus", "accepted")
                + " are accepted as a\nUBL invoice");
        assertSentence(readme, "all " + number(UBL, "corpus", "identical") + " read back");
    }

    /** The two writer tables of the conformance page. */
    @Test
    void theConformancePageCountsWhatTheWritersProduce() {
        List<String> rows = Fixtures.text("docs/conformance.md").lines()
                .filter(line -> line.startsWith("|")).toList();
        assertEquals(List.of(String.valueOf(number(CII, "corpus", "accepted")),
                        String.valueOf(number(UBL, "corpus", "accepted"))),
                cells(rows, "`accepted` — nothing fatal from the artefacts", 0),
                "the writer table of docs/conformance.md");
        assertEquals(List.of(String.valueOf(number(CII, "corpus", "identical")),
                        String.valueOf(number(UBL, "corpus", "identical"))),
                cells(rows, "`identical` — reading back gives the document written", 0));
        assertEquals(List.of(String.valueOf(number(MATRIX, "totals", "ublToCiiAccepted")),
                        String.valueOf(number(MATRIX, "totals", "ciiToUblAccepted"))),
                cells(rows, "`accepted` — nothing fatal from the artefacts", 1),
                "the matrix table of docs/conformance.md");
        assertEquals(List.of(String.valueOf(number(MATRIX, "totals", "ublToCiiRefused")),
                        String.valueOf(number(MATRIX, "totals", "ciiToUblRefused"))),
                cells(rows, "`refused` — at least one fatal finding", 0));
    }

    /** Asserts that a page carries a phrase, whatever the line it is wrapped onto. */
    private static void assertSentence(String page, String phrase) {
        assertTrue(page.replace("\n", " ").contains(phrase.replace("\n", " ")),
                "the page carries \"" + phrase.replace("\n", " ") + "\"");
    }

    /** Returns the cells of one table row whose first cell opens with a label. */
    private static List<String> cells(List<String> rows, String label, int occurrence) {
        int seen = 0;
        for (String row : rows) {
            List<String> parts = new ArrayList<>(List.of(row.split("\\|")));
            parts.removeIf(String::isBlank);
            if (parts.isEmpty() || !parts.get(0).strip().equals(label)) {
                continue;
            }
            if (seen++ < occurrence) {
                continue;
            }
            return parts.subList(1, parts.size()).stream().map(String::strip).toList();
        }
        throw new AssertionError("no row of docs/conformance.md carries " + label
                + " for the " + (occurrence + 1) + ". time");
    }

    /** Returns one number of a ledger: the member of an object of the top-level object. */
    private static int number(String ledger, String object, String member) {
        try (JsonParser parser =
                     new JsonFactory().createParser(Fixtures.text(ledger))) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME
                        && object.equals(parser.currentName())) {
                    return member(parser, member, ledger + " " + object);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        throw new AssertionError(ledger + " carries no " + object);
    }

    /** Returns one member of the object the parser is about to enter. */
    private static int member(JsonParser parser, String member, String where)
            throws IOException {
        parser.nextToken();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String name = parser.currentName();
            parser.nextToken();
            if (member.equals(name)) {
                return parser.getIntValue();
            }
            parser.skipChildren();
        }
        throw new AssertionError(where + " carries no " + member);
    }
}
