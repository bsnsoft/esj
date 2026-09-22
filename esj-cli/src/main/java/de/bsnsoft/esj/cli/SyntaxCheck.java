package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.syntax.ComponentRun;
import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.PackException;
import de.bsnsoft.esj.syntax.SyntaxFinding;
import de.bsnsoft.esj.syntax.SyntaxLimitException;
import de.bsnsoft.esj.syntax.SyntaxNotSupportedException;
import de.bsnsoft.esj.syntax.SyntaxOptions;
import de.bsnsoft.esj.syntax.SyntaxReport;
import de.bsnsoft.esj.syntax.SyntaxValidator;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the syntax engine had to say about one input, or why it had nothing to say.
 *
 * <p>The engine runs the official artefacts of the document's profile — the XML Schema
 * modules of its syntax and the compiled Schematron of EN 16931 and of the CIUS it names
 * — over the bytes as they arrived. It is the half of {@code esj validate} that reads the
 * document as XML; the structural layers of {@link Validation} read it as a semantic
 * document, after the importer has built one.
 *
 * <p>Both halves are needed and neither replaces the other. A semantic document says
 * nothing about the binding it came from: an element in a place the syntax binding
 * forbids is gone by the time the importer is finished, and a rule of the standard that
 * an artefact checks against the XML tree cannot be checked against a document that no
 * longer has one. The other way round, an ESJ document that was never XML has no binding
 * to check, which is exactly the case this record carries a reason for rather than a
 * report.
 *
 * <p>There are three such reasons, and each of them is printed rather than passed over.
 * A validator that skipped a check quietly would let a reader believe the check passed.
 *
 * @param report what the engine found, empty where it did not run
 * @param reason why it did not run, empty where it did
 */
record SyntaxCheck(Optional<SyntaxReport> report, Optional<String> reason) {

    /** There is no XML to check: the input was read as an ESJ document. */
    static final String NO_XML = "not applicable (no XML)";

    /** The caller asked for the structural layers alone. */
    static final String BY_OPTION = "skipped (--no-syntax)";

    /** {@code esj inspect} summarizes a document rather than validating its binding. */
    static final String NOT_INSPECTED =
            "not run (esj validate runs the official artefacts)";

    /**
     * The informational second pass of {@code --after-repair} reads bytes this run made,
     * and the official artefacts judge the bytes that were handed over.
     */
    static final String AFTER_REPAIR =
            "not run (the recoded bytes are not the document that was handed over)";

    /**
     * Creates a check, refusing the two states that are not states.
     *
     * @param report what the engine found, empty where it did not run
     * @param reason why it did not run, empty where it did
     */
    SyntaxCheck {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(reason, "reason");
        if (report.isPresent() == reason.isPresent()) {
            throw new IllegalArgumentException(
                    "a syntax check has either a report or a reason it has none");
        }
    }

    /**
     * Returns a check that did not run.
     *
     * @param reason why not, in English, for the report
     * @return the check
     */
    static SyntaxCheck notRun(String reason) {
        return new SyntaxCheck(Optional.empty(), Optional.of(reason));
    }

    /**
     * Runs the syntax engine over an input, where there is anything to run it on.
     *
     * <p>Every way the engine can fail to reach a verdict leaves this method as a
     * {@link CliException} with an exit code of its own, because none of them means the
     * document is invalid. A limit reached is {@link ExitCode#LIMIT}: the run found
     * nothing, not nothing wrong. A document of a syntax or profile the pack carries no
     * artefact for is {@link ExitCode#UNSUPPORTED}: the request was understood and cannot
     * be served. A pack that cannot be read or run is {@link ExitCode#INPUT}, because the
     * pack is a resource the caller named.
     *
     * <p>The engine is given what is left of the command's time rather than the whole of
     * it, and the message of a run that used it up names both numbers. The engine can only
     * speak of the budget it was handed, and a caller who set twelve seconds and is told
     * about the nineteen hundred milliseconds that were left of them would read the
     * sentence as a tool ignoring the option.
     *
     * @param input       the bytes and the name of the input
     * @param syntax      the syntax the bytes were recognized as
     * @param pack        the pack {@code --pack} named, or {@code null} to let the
     *                    document choose among the bundled ones
     * @param deadline    the time the whole command was given, and what is left of it
     * @param console     the streams of the process, for {@code --verbose}
     * @return what the engine found, or the reason it did not run
     * @throws CliException if the engine could not reach a verdict
     */
    static SyntaxCheck run(Input input,
                           InputSyntax syntax,
                           Pack pack,
                           Deadline deadline,
                           Console console) {
        if (!syntax.isXml()) {
            return notRun(NO_XML);
        }
        return run(input.bytes(), input.name(), pack, deadline, console);
    }

    /**
     * Runs the syntax engine over XML bytes.
     *
     * <p>It is the same engine, the same pack and the same artefacts wherever the bytes
     * came from. {@code esj validate} hands it the file it was given; for an ESJ input it
     * hands it the XML the document was written to, which is the only way an artefact can
     * read a document that never was XML ({@link WrittenCheck} says when that is the same
     * document and when it is not).
     *
     * @param xml      the XML to judge
     * @param name     what the report calls it
     * @param pack     the pack {@code --pack} named, or {@code null} to let the document
     *                 choose among the bundled ones
     * @param deadline the time the whole command was given, and what is left of it
     * @param console  the streams of the process, for {@code --verbose}
     * @return what the engine found
     * @throws CliException if the engine could not reach a verdict
     */
    static SyntaxCheck run(byte[] xml,
                           String name,
                           Pack pack,
                           Deadline deadline,
                           Console console) {
        Duration left = deadline.remaining(name + ": the official artefacts");
        SyntaxOptions options = SyntaxOptions.defaults().withMaxRuntime(left)
                .withMaxInputBytes(console.options().bounds().maxInputBytes());
        if (pack != null) {
            options = options.withPack(pack);
        }
        try {
            SyntaxReport report = SyntaxValidator.validate(xml, options);
            explain(report, name, console);
            return new SyntaxCheck(Optional.of(report), Optional.empty());
        } catch (SyntaxLimitException e) {
            // A clock names its own switch in the note below; a bound on the bytes is one
            // of the run's own bounds and is named the way every other one of them is.
            String raises = e.budget().isPresent() ? ""
                    : console.options().bounds().hint(Bound.of(e.getMessage()).orElse(null));
            throw CliException.limit(name + ": " + e.getMessage() + raises
                    + remainderNote(e, deadline), e);
        } catch (SyntaxNotSupportedException e) {
            throw CliException.unsupported(name + ": " + e.getMessage());
        } catch (PackException e) {
            throw CliException.input("the validation pack cannot be run: " + e.getMessage(), e);
        }
    }

    /**
     * Names the number the caller set beside the one the engine was handed, where the
     * limit that was reached was the time.
     *
     * <p>It is package-private so that a test can put the two numbers side by side without
     * having to make a document outlast a clock, which is not a thing a test can time.
     *
     * <p>Where it was not — an input larger than the engine accepts — there is no second
     * number and no word about the clock: a sentence about the clock under a message about
     * bytes would send a reader after the wrong option. The switch that raises the bound on
     * the input is named by {@link Bound} from the message the engine wrote, and leaving the
     * artefacts out is an answer to either bound, so that sentence is added to both.
     */
    static String remainderNote(SyntaxLimitException e, Deadline deadline) {
        return e.budget()
                .map(given -> "; the artefacts were given what was left of the "
                        + deadline.total().toMillis() + " ms --max-runtime gave the whole"
                        + " command once the document had been read; --max-runtime gives it"
                        + " more, or --no-syntax leaves the official artefacts out")
                .orElse("; --no-syntax leaves the official artefacts out");
    }

    /**
     * Says on the error stream what each artefact cost.
     *
     * <p>The two halves are reported apart because they are paid at different times:
     * compiling an artefact happens once per process, running it happens once per
     * document. A command line that validates one invoice and exits pays both, which is
     * why a single run looks so much more expensive than a batch does, and a number that
     * added them together would hide exactly that.
     *
     * <p>None of this is in {@code --output json}. A machine report of this tool is
     * compared, diffed and checked into repositories, so it carries what the document
     * decides and never what the machine was doing at the time.
     */
    private static void explain(SyntaxReport report, String name, Console console) {
        if (!console.options().verbose()) {
            return;
        }
        for (ComponentRun run : report.ran()) {
            console.verbose(name + ": " + run.component() + " (" + run.engine().token()
                    + ") ran in " + run.duration().toMillis() + " ms, compiled in "
                    + run.compilation().toMillis() + " ms");
        }
        console.verbose(name + ": the syntax engine took "
                + report.duration().toMillis() + " ms");
    }

    /** Tells whether the engine ran at all. */
    boolean checked() {
        return report.isPresent();
    }

    /**
     * Tells whether anything the engine ran made the document invalid.
     *
     * <p>A check that did not run answers no, the way a structural layer that did not run
     * does: {@link #checked()} is the question that separates "found nothing" from "was
     * not asked".
     *
     * @return whether a fatal finding was made
     */
    boolean failed() {
        return report.map(found -> !found.fatal().isEmpty()).orElse(false);
    }

    /**
     * Tells whether a rule set of the pack was left out because of the profile the
     * document names.
     *
     * <p>A check that did not run answers no: nothing was left out for a profile where
     * nothing was chosen by one, and the reason the engine did not run is reported on its
     * own.
     *
     * @return whether the verdict, if any, was reached without those rule sets
     */
    boolean profileRulesSkipped() {
        return report.map(SyntaxReport::profileRulesSkipped).orElse(false);
    }

    /**
     * Tells whether the rule set that was left out was left out for a specification the
     * document names and the pack carries no rules for.
     *
     * <p>It is the narrower half of {@link #profileRulesSkipped()} and the one a verdict
     * turns on. A document that names EN 16931 and no core invoice usage specification
     * also leaves the CIUS rule sets of a pack unused, and it was checked against
     * everything that applies to it; a document that named a specification nobody here
     * holds rules for asked to be judged by rules that did not run.
     *
     * @return whether a rule set the document asked for did not run
     */
    boolean profileRulesMissing() {
        return report.map(SyntaxReport::profileRulesMissing).orElse(false);
    }

    /** Returns the findings one component produced, in the order of the report. */
    List<SyntaxFinding> findings(String component) {
        return report.map(found -> found.findings().stream()
                        .filter(finding -> finding.component().equals(component))
                        .toList())
                .orElse(List.of());
    }
}
