package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The whole conformance corpus through the command line, beside the verdict the official
 * validator gives each instance.
 *
 * <p>The corpus is the test suite the publisher of XRechnung ships, and the official
 * validator accepts every instance of it. A verdict of this tool that contradicts that on
 * a document nobody broke is a defect of this tool, whichever engine reached it and
 * however well each half of the run can be defended on its own: a caller writes
 * {@code esj validate x.xml && deploy} and gets one answer, and the answer has to be the
 * one the ecosystem gives.
 *
 * <p>The oracle is {@code conformance/syntax/ledger.json}, where the official verdict of every
 * instance is recorded, taken down by running the validator its publisher ships. Nothing
 * is downloaded here and the artefacts are not re-run: what is measured is the tool, with
 * the options a caller gets when they ask for nothing.
 *
 * <p>It is a broader question than the two ledgers ask, and that is why it is its own
 * test. {@code conformance/syntax/ledger.md} weighs the syntax engine against the
 * validator and {@code conformance/rules/ledger.md} weighs the rule pack against the
 * artefacts, each finding by finding; neither of them is about the one word the command
 * ends with, which is composed from both engines and from the levels the profile of the
 * document gives a rule.
 */
class CorpusVerdictTest {

    @TempDir
    static Path directory;

    /**
     * One instance of the corpus and what this tool has to answer about it.
     *
     * @param path     the instance, relative to {@code conformance/kosit/}
     * @param verdict  the word the report ends with
     * @param exitCode the code the command leaves with
     */
    record Instance(String path, String verdict, int exitCode) {

        @Override
        public String toString() {
            return path;
        }
    }

    /**
     * Returns the instances of the corpus with the verdict each of them has to reach.
     *
     * <p>Two shapes are recorded and the ledger carries both. An instance the official
     * validator accepts is {@code VALID} with exit code 0. An instance it declines for
     * want of a scenario — a document naming a core invoice usage specification its
     * configuration has no rules for — is {@code INDETERMINATE} with exit code 9 here,
     * because this tool checks what applies and then says what it could not reach; the
     * ledger records that as the one difference of intent between the two. Neither shape
     * is {@code INVALID}.
     */
    static List<Instance> corpus() {
        List<Instance> instances = new ArrayList<>();
        for (Map<?, ?> row : documents()) {
            if (!"corpus".equals(row.get("kind"))) {
                continue;
            }
            String path = (String) row.get("document");
            String official = (String) row.get("officialVerdict");
            assertTrue(List.of("accept", "reject").contains(official),
                    path + ": the ledger records what the official validator answered");
            instances.add("accept".equals(official)
                    ? new Instance(path, "VALID", ExitCode.SUCCESS)
                    : new Instance(path, "INDETERMINATE", ExitCode.INDETERMINATE));
        }
        return List.copyOf(instances);
    }

    /** Every instance of the corpus is one the ledger carries an official verdict for. */
    @Test
    void theLedgerAnswersForEveryInstanceOfTheCorpus() {
        assertEquals(new TreeSet<>(Fixtures.corpus()),
                new TreeSet<>(corpus().stream().map(Instance::path).toList()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void everyInstanceReachesTheVerdictTheOfficialValidatorGivesIt(Instance instance) {
        Cli.Run run = Cli.run("validate",
                Fixtures.file(directory, "conformance/kosit/" + instance.path()));

        assertEquals(instance.exitCode(), run.exitCode(),
                instance.path() + ": " + last(run) + "\n" + run.err());
        assertTrue(last(run).startsWith(instance.verdict()),
                instance.path() + ": the report ends with " + last(run));
    }

    /** Returns the last line of a report, which is the verdict. */
    private static String last(Cli.Run run) {
        String[] lines = run.lines();
        for (int at = lines.length - 1; at >= 0; at--) {
            if (!lines[at].isBlank()) {
                return lines[at];
            }
        }
        return "";
    }

    private static List<Map<?, ?>> documents() {
        Map<?, ?> ledger = (Map<?, ?>) Oracle.json(
                Oracle.bytes("/conformance/syntax/ledger.json"));
        Map<?, ?> oracle = (Map<?, ?>) ledger.get("oracle");
        List<Map<?, ?>> rows = new ArrayList<>();
        for (Object row : (List<?>) oracle.get("documents")) {
            rows.add((Map<?, ?>) row);
        }
        return rows;
    }
}
