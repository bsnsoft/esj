package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What the engine says about documents that were broken on purpose.
 *
 * <p>The corpus says what the artefacts do with documents that are right, and a test
 * suite that only ever sees those cannot tell a working validator from one that reports
 * nothing. The mutation set of {@code conformance/syntax/mutations/} is the other half:
 * every mutation takes an instance the artefacts accept, makes one change to it, and says
 * which rules are expected to fire at which level.
 *
 * <p>Both halves of the expectation matter. The rule identifiers say that the right rule
 * ran; the two levels say which of them decides the verdict, and they are not always the
 * same — the flag is what the artefact set on the rule, and the severity is what the
 * profile of the document says a rule of that name means for a document of its profile.
 *
 * <p>These expectations were taken down against the official validator: every one of
 * them is a rule identifier and a flag that validator reported for the same bytes, and
 * {@code conformance/syntax/ledger.md} records that comparison with its figures. The
 * validator is not needed here. This test re-runs the engine over the set and fails on
 * any change to what it answers, which is what makes the ledger a claim about the code in
 * the repository rather than about a run somebody once did.
 */
class SyntaxMutationTest {

    static List<String> mutations() {
        return Mutations.ids();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void reportsWhatTheMutationSetRecords(String id) {
        Mutations.Mutation mutation = Mutations.of(id);

        SyntaxReport report = SyntaxValidator.validate(Mutations.apply(mutation));

        assertEquals(mutation.expected(), lines(report),
                id + " produces the findings the mutation set records");
        assertEquals(mutation.verdict(), report.verdict().name(),
                id + " has the verdict the mutation set records");
        assertEquals(mutation.verdict().equals("INVALID"), !report.fatal().isEmpty(),
                id + ": the verdict is invalid exactly when a finding is fatal");
        assertEquals(mutation.verdict().equals("INDETERMINATE"), report.profileRulesMissing(),
                id + ": the verdict is indeterminate exactly where a rule set the document"
                        + " asked for did not run");
        assertEquals(mutation.profileNote(), report.profileNote().orElse(""),
                id + " leaves the note the mutation set records about the rule sets that"
                        + " did not run for its profile");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void changesTheInstanceItIsMadeFrom(String id) {
        Mutations.Mutation mutation = Mutations.of(id);

        assertNotEquals(new String(Corpus.instance(mutation.source())),
                new String(Mutations.apply(mutation)),
                id + " changes the instance it is made from");
    }

    /**
     * The instances the set is built on are documents the artefacts accept, so everything
     * a mutation reports is the mutation and nothing that was already there. Where an
     * instance does carry a finding of its own, that finding is in the expectation too,
     * which is why the expectations are complete lists rather than the one rule each
     * mutation is aimed at.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("mutations")
    void isMadeFromAnInstanceTheArtefactsAccept(String id) {
        Mutations.Mutation mutation = Mutations.of(id);

        SyntaxReport report = SyntaxValidator.validate(Corpus.instance(mutation.source()));

        assertEquals(Verdict.VALID, report.verdict(),
                id + " is made from " + mutation.source() + ", which is accepted");
    }

    /**
     * A set that missed a block of the engine would leave that block untested however
     * many mutations it had, so the set says for each mutation what it is aimed at and
     * this asks whether the blocks are all there.
     */
    @Test
    void reachesEveryBlockOfTheEngine() {
        Set<String> aimedAt = new TreeSet<>();
        Mutations.all().forEach(mutation -> aimedAt.add(mutation.category()));

        assertEquals(Set.of("UBL-XSD", "CII-XSD", "EN-BR", "EN-DEC", "EN-CL",
                        "UBL-BINDING", "CII-BINDING", "XR-BR", "PROFILE"), aimedAt,
                "the set reaches the schema of both syntaxes, the business, decimal and"
                        + " code list rules of the standard, the binding of both syntaxes,"
                        + " the rules of the specification and the choice of artefacts");
        assertTrue(Mutations.all().size() >= 40,
                "the set is large enough to be a regression test rather than an example,"
                        + " and it has " + Mutations.all().size() + " mutations");
    }

    /**
     * Every level a profile gives a rule is exercised by at least one document, corpus or
     * mutation. Without this the tables of the pack could be wrong in either direction
     * and nothing would say so.
     */
    @Test
    void exercisesTheLevelsTheProfilesGiveRules() {
        List<SyntaxFinding> releveled = new ArrayList<>();
        for (Mutations.Mutation mutation : Mutations.all()) {
            SyntaxValidator.validate(Mutations.apply(mutation)).findings().stream()
                    .filter(SyntaxFinding::releveled)
                    .forEach(releveled::add);
        }

        assertFalse(releveled.isEmpty(), "at least one mutation meets a rule its profile"
                + " levels differently from the artefact");
        assertTrue(releveled.stream().anyMatch(finding -> finding.flag() == Severity.FATAL
                        && finding.severity() != Severity.FATAL),
                "and one of them is a rule the profile levels down, which is the case that"
                        + " decides a verdict");
    }

    /** One expected finding as one line, which is what a failure prints. */
    private static List<String> lines(SyntaxReport report) {
        List<String> lines = new ArrayList<>();
        for (SyntaxFinding finding : report.findings()) {
            lines.add(Mutations.line(finding.code(), finding.severity().token(),
                    finding.flag().token(), finding.category().label()));
        }
        return lines;
    }
}
