package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code esj diff} against the ledger of the conformance corpus.
 *
 * <p>Forty business cases of the XRechnung test suite exist in both syntaxes, and
 * {@code conformance/ledger/pairs.json} records, pair by pair, the semantic paths at which
 * the two files disagree and why. That ledger is the strongest assertion available to this
 * command: the tool is handed the two XML files a person would hand it, and the paths it
 * prints have to be exactly the ones the ledger names — no more, and in the same order.
 *
 * <p>The test runs a selection rather than all forty pairs, because every pair costs two
 * XSLT transformations and {@code esj-xr} already runs the whole corpus through that path.
 * The selection is taken from the ledger in its own order, so it is the same selection on
 * every machine, and it covers both outcomes: pairs the ledger records as identical and
 * pairs it records differences for.
 */
class DiffCommandTest {

    /** How many pairs of each kind the selection takes from the ledger. */
    private static final int PER_KIND = 3;

    private static final String KOSIT = "conformance/kosit/";
    private static final String UBL_SUFFIX = "_ubl.xml";
    private static final String CII_SUFFIX = "_uncefact.xml";

    @TempDir
    private Path directory;

    /** The selection of pairs, read from the ledger once. */
    static List<Pair> pairs() {
        List<Pair> identical = new ArrayList<>();
        List<Pair> differing = new ArrayList<>();
        for (Pair pair : readLedger()) {
            List<Pair> bucket = pair.identical() ? identical : differing;
            if (bucket.size() < PER_KIND) {
                bucket.add(pair);
            }
        }
        List<Pair> selection = new ArrayList<>(identical);
        selection.addAll(differing);
        assertEquals(2 * PER_KIND, selection.size(),
                "the ledger carries pairs of both kinds to choose from");
        return selection;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairs")
    void printsExactlyThePathsTheLedgerRecords(Pair pair) {
        String ubl = Fixtures.file(directory, KOSIT + pair.stem() + UBL_SUFFIX);
        String cii = Fixtures.file(directory, KOSIT + pair.stem() + CII_SUFFIX);

        Cli.Run run = Cli.run("diff", ubl, cii);
        assertEquals(pair.paths(), printedPaths(run),
                "the paths the tool prints are the ones conformance/ledger/pairs.json records");
        assertEquals(pair.identical() ? ExitCode.SUCCESS : ExitCode.VALIDATION, run.exitCode());
        assertTrue(run.err().contains("semantic digest a: "), run.err());
        assertTrue(run.err().contains("semantic digest b: "), run.err());
    }

    @Test
    void countsInsteadOfNamingUnderSummary() {
        Pair pair = pairs().stream().filter(p -> !p.identical()).findFirst().orElseThrow();
        String ubl = Fixtures.file(directory, KOSIT + pair.stem() + UBL_SUFFIX);
        String cii = Fixtures.file(directory, KOSIT + pair.stem() + CII_SUFFIX);

        Cli.Run run = Cli.run("diff", ubl, cii, "--summary");
        assertEquals(ExitCode.VALIDATION, run.exitCode());
        assertTrue(run.text().startsWith(pair.paths().size() + " differing path"), run.text());
        assertFalse(run.text().contains("/BT-"), "a summary names no path");
    }

    @Test
    void findsNothingBetweenADocumentAndItself() {
        String file = Fixtures.file(directory, "examples/standard-invoice.esj.json");
        Cli.Run run = Cli.run("diff", file, file);
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals("", run.text());
    }

    @Test
    void comparesAnEsjDocumentWithTheXmlItWasBuiltFrom() {
        String xml = Fixtures.file(directory,
                KOSIT + "business-cases/standard/01.01a-INVOICE_ubl.xml");
        String esj = Fixtures.file(directory,
                "conformance/esj/business-cases/standard/01.01a-INVOICE_ubl.xml.esj.json");
        Cli.Run run = Cli.run("diff", xml, esj);
        assertEquals(ExitCode.SUCCESS, run.exitCode(), run.err());
        assertEquals("", run.text(), "the checked-in document says what the importer builds");
    }

    @Test
    void refusesToReadTheStandardInputTwice() {
        Cli.Run run = Cli.run(Fixtures.bytes("examples/minimal.esj.json"), "diff", "-", "-");
        assertEquals(ExitCode.INPUT, run.exitCode());
        assertTrue(run.err().contains("read only once"), run.err());
    }

    /**
     * Returns the paths of the printed lines, in the order they were printed, once each.
     * The two header lines that name the files are skipped.
     */
    private static List<String> printedPaths(Cli.Run run) {
        List<String> paths = new ArrayList<>();
        for (String line : run.lines()) {
            if (line.isEmpty() || line.startsWith("--- ") || line.startsWith("+++ ")) {
                continue;
            }
            String path = line.substring(1, line.indexOf(" = "));
            if (paths.isEmpty() || !paths.get(paths.size() - 1).equals(path)) {
                paths.add(path);
            }
        }
        return paths;
    }

    /**
     * Reads {@code conformance/ledger/pairs.json}: the stem of every pair, whether the two
     * syntaxes agree, and the paths at which they do not.
     */
    private static List<Pair> readLedger() {
        List<Pair> pairs = new ArrayList<>();
        try (JsonParser parser = new JsonFactory()
                .createParser(Fixtures.bytes("conformance/ledger/pairs.json"))) {
            String stem = null;
            Boolean identical = null;
            while (parser.nextToken() != null) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    continue;
                }
                switch (parser.currentName()) {
                    case "stem" -> stem = parser.nextTextValue();
                    case "identical" -> identical = parser.nextBooleanValue();
                    case "differences" -> {
                        pairs.add(new Pair(Objects.requireNonNull(stem, "stem"),
                                Objects.requireNonNull(identical, "identical"),
                                readPaths(parser)));
                        stem = null;
                        identical = null;
                    }
                    default -> { }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertFalse(pairs.isEmpty(), "conformance/ledger/pairs.json records pairs");
        return pairs;
    }

    private static List<String> readPaths(JsonParser parser) throws IOException {
        List<String> paths = new ArrayList<>();
        parser.nextToken();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            Map<String, String> members = new LinkedHashMap<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                members.put(parser.currentName(), parser.nextTextValue());
            }
            paths.add(Objects.requireNonNull(members.get("path"), "a difference has a path"));
        }
        return List.copyOf(paths);
    }

    /**
     * One pair of the ledger.
     *
     * @param stem      the path of the two instances without the syntax suffix
     * @param identical whether the ledger records the two syntaxes as agreeing
     * @param paths     the paths at which they disagree, in canonical path order
     */
    record Pair(String stem, boolean identical, List<String> paths) {

        @Override
        public String toString() {
            return stem;
        }
    }
}
