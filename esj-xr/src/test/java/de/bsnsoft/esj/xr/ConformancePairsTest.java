package de.bsnsoft.esj.xr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The litmus test of the corpus: the same invoice, written once in UBL and once in CII,
 * has to arrive at the same semantic content.
 *
 * <p>Forty business cases of the suite exist in both syntaxes. EN 16931-1 says that both
 * files of a pair carry the same invoice; ESJ can check it, because it gives that content
 * one byte sequence and one digest. Where the two digests differ, the difference is the
 * set of semantic paths at which the two documents disagree — a value that differs, or a
 * value that only one side has.
 *
 * <p>The two documents of a pair are the ones checked in under {@code conformance/esj/},
 * which is what this repository says each instance of the corpus is as a semantic document.
 * That is deliberate: the litmus test is a claim about the corpus and about the format, not
 * about one reader, and the reader that writes those files is held against them in
 * {@code ReaderCorpusTest} while the XSLT path of this module is held against them in
 * {@code ConformanceCorpusTest}. A change in either reader therefore reaches this ledger
 * through the files rather than beside them.
 *
 * <p>The set of differing paths is not asserted to be empty, because for most pairs it is
 * not: the two files of the suite do not always say the same thing, and the syntax bindings
 * have one asymmetry of their own. It is asserted to be exactly the set recorded in
 * {@code conformance/ledger/pairs.json}, which {@code conformance/ledger/pairs.md}
 * explains difference by difference. A regression therefore fails this test, and so does an
 * improvement: a pair that starts to agree has to be recorded in the ledger before it
 * counts, and reviewing that change is reviewing the claim behind it.
 *
 * <p>The ledger claims more than a set of paths. It gives every difference a cause and a
 * kind, and it summarizes those in two tables of prose. Those tables are recomputed here
 * from the machine-readable ledger and compared with what the prose says, so that a count
 * typed by hand, or a difference that moves from one cause to another, fails the build
 * instead of quietly becoming untrue.
 */
class ConformancePairsTest {

    private static final String UBL_SUFFIX = "_ubl.xml";
    private static final String CII_SUFFIX = "_uncefact.xml";

    /** A row of the summary table of {@code pairs.md}: a measure and a number of pairs. */
    private static final Pattern SUMMARY_ROW =
            Pattern.compile("^\\| `([a-z]+)` — .*?\\| (\\d+) \\|$");

    /** A row of the cause table of {@code pairs.md}: cause, kind, pairs, differing paths. */
    private static final Pattern CAUSE_ROW = Pattern.compile(
            "^\\| `([a-z0-9-]+)` \\| (fixture|limitation|defect) \\| (\\d+) \\| (\\d+) \\|$");

    /** The recorded pairs, read once, in the order the ledger lists them. */
    private static final List<Pair> RECORDED = readLedger();

    static List<String> pairs() {
        return RECORDED.stream().map(Pair::stem).toList();
    }

    private static Pair recorded(String stem) {
        return RECORDED.stream().filter(pair -> pair.stem().equals(stem)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no such pair: " + stem));
    }

    @Test
    void theLedgerCoversEveryPairOfTheCorpus() {
        List<String> stems = new ArrayList<>();
        for (String instance : Conformance.corpus()) {
            if (instance.endsWith(UBL_SUFFIX)) {
                String stem = instance.substring(0, instance.length() - UBL_SUFFIX.length());
                if (Conformance.instances().containsKey(stem + CII_SUFFIX)) {
                    stems.add(stem);
                }
            }
        }
        assertFalse(stems.isEmpty(), "the corpus holds instances in both syntaxes");
        assertEquals(stems, pairs(),
                "conformance/ledger/pairs.json records exactly the pairs of the corpus");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pairs")
    void differsFromTheOtherSyntaxExactlyWhereTheLedgerSaysSo(String stem) {
        SemanticDocument ubl = EsjReader.strict().read(Conformance.esj(stem + UBL_SUFFIX));
        SemanticDocument cii = EsjReader.strict().read(Conformance.esj(stem + CII_SUFFIX));

        Pair pair = recorded(stem);
        boolean sameDigest = Canonicalizer.semanticDigest(ubl)
                .equals(Canonicalizer.semanticDigest(cii));
        assertEquals(pair.identical(), pair.paths().isEmpty(),
                "the recorded identical flag agrees with the recorded differences");
        assertEquals(pair.identical(), sameDigest,
                "the ledger records whether the two syntaxes arrive at the same digest");
        assertEquals(pair.paths(), differingPaths(ubl, cii),
                "the ledger records the paths at which the two syntaxes disagree");
    }

    /**
     * Every difference carries a cause, every cause carries one kind throughout, and the
     * two tables of {@code pairs.md} say what the machine-readable ledger says.
     */
    @Test
    void theProseCountsAreTheCountsOfTheLedger() {
        Map<String, String> kindOfCause = new TreeMap<>();
        Map<String, Set<String>> pairsPerCause = new TreeMap<>();
        Map<String, Integer> pathsPerCause = new TreeMap<>();
        Map<String, Set<String>> pairsPerKind = new TreeMap<>();
        int identical = 0;
        int fixtureOnly = 0;
        for (Pair pair : RECORDED) {
            if (pair.identical()) {
                identical++;
            } else if (pair.differences().stream().allMatch(d -> d.kind().equals("fixture"))) {
                fixtureOnly++;
            }
            for (Difference difference : pair.differences()) {
                String known = kindOfCause.putIfAbsent(difference.cause(), difference.kind());
                assertEquals(known == null ? difference.kind() : known, difference.kind(),
                        "the cause " + difference.cause() + " is of one kind throughout");
                pairsPerCause.computeIfAbsent(difference.cause(), key -> new LinkedHashSet<>())
                        .add(pair.stem());
                pathsPerCause.merge(difference.cause(), 1, Integer::sum);
                pairsPerKind.computeIfAbsent(difference.kind(), key -> new LinkedHashSet<>())
                        .add(pair.stem());
            }
        }

        Map<String, Integer> summary = new TreeMap<>();
        summary.put("identical", identical);
        summary.put("fixture", fixtureOnly);
        summary.put("limitation", pairsPerKind.getOrDefault("limitation", Set.of()).size());
        summary.put("defect", pairsPerKind.getOrDefault("defect", Set.of()).size());
        summary.put("total", RECORDED.size());
        assertEquals(summary, recordedSummary(),
                "the summary table of conformance/ledger/pairs.md counts what pairs.json holds");

        Map<String, String> causes = new TreeMap<>();
        kindOfCause.forEach((cause, kind) -> causes.put(cause,
                kind + " " + pairsPerCause.get(cause).size() + " " + pathsPerCause.get(cause)));
        assertEquals(causes, recordedCauses(),
                "the cause table of conformance/ledger/pairs.md counts what pairs.json holds");
    }

    /** Reads the summary table of the ledger prose: one number per measure. */
    private static Map<String, Integer> recordedSummary() {
        Map<String, Integer> summary = new TreeMap<>();
        for (String line : Conformance.ledger("pairs.md").split("\\R")) {
            Matcher matcher = SUMMARY_ROW.matcher(line);
            if (matcher.matches()) {
                summary.put(matcher.group(1), Integer.valueOf(matcher.group(2)));
            }
        }
        assertFalse(summary.isEmpty(), "the ledger carries a summary table in the documented shape");
        return summary;
    }

    /** Reads the cause table of the ledger prose: kind, pairs and paths per cause. */
    private static Map<String, String> recordedCauses() {
        Map<String, String> causes = new TreeMap<>();
        for (String line : Conformance.ledger("pairs.md").split("\\R")) {
            Matcher matcher = CAUSE_ROW.matcher(line);
            if (matcher.matches()) {
                causes.put(matcher.group(1),
                        matcher.group(2) + " " + matcher.group(3) + " " + matcher.group(4));
            }
        }
        assertFalse(causes.isEmpty(), "the ledger carries a cause table in the documented shape");
        return causes;
    }

    /** Returns the paths at which the two documents disagree, in canonical order. */
    private static List<String> differingPaths(SemanticDocument ubl, SemanticDocument cii) {
        SortedSet<SemanticPath> all = new TreeSet<>(SemanticPath.canonicalOrder());
        all.addAll(ubl.values().keySet());
        all.addAll(cii.values().keySet());

        List<String> differing = new ArrayList<>();
        for (SemanticPath path : all) {
            SemanticValue left = ubl.values().get(path);
            SemanticValue right = cii.values().get(path);
            if (!Objects.equals(left, right)) {
                differing.add(path.toString());
            }
        }
        return differing;
    }

    /**
     * Reads {@code conformance/ledger/pairs.json}: for every pair, the stem, whether the
     * two syntaxes agree, and every difference with the cause and the kind of cause the
     * ledger gives it.
     */
    private static List<Pair> readLedger() {
        List<Pair> pairs = new ArrayList<>();
        try (JsonParser parser = new JsonFactory()
                .createParser(Conformance.bytes(Conformance.ROOT + "ledger/pairs.json"))) {
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
                        pairs.add(new Pair(Objects.requireNonNull(stem, "stem before differences"),
                                Objects.requireNonNull(identical, "identical before differences"),
                                readDifferences(parser)));
                        stem = null;
                        identical = null;
                    }
                    default -> { }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (pairs.isEmpty()) {
            throw new IllegalStateException("conformance/ledger/pairs.json records no pair");
        }
        return List.copyOf(pairs);
    }

    /** Reads the differences of one pair: path, kind and cause per element. */
    private static List<Difference> readDifferences(JsonParser parser) throws IOException {
        List<Difference> differences = new ArrayList<>();
        parser.nextToken();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            Map<String, String> members = new LinkedHashMap<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                members.put(parser.currentName(), parser.nextTextValue());
            }
            differences.add(new Difference(
                    Objects.requireNonNull(members.get("path"), "a difference carries a path"),
                    Objects.requireNonNull(members.get("kind"), "a difference carries a kind"),
                    Objects.requireNonNull(members.get("cause"), "a difference carries a cause")));
        }
        return List.copyOf(differences);
    }

    /** One recorded difference: where the two syntaxes disagree, and why. */
    private record Difference(String path, String kind, String cause) {
    }

    /** One recorded pair of the ledger. */
    private record Pair(String stem, boolean identical, List<Difference> differences) {

        List<String> paths() {
            return differences.stream().map(Difference::path).toList();
        }
    }
}
