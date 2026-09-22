package de.bsnsoft.esj.report;

import de.bsnsoft.esj.validate.Severity;
import de.bsnsoft.esj.validate.ValidationStatus;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything a validation run came to, in the form a report is written from.
 *
 * <p>It is not the result of one validator. {@code de.bsnsoft.esj.validate}
 * carries that — the structural layers and what they found — and a complete run puts
 * several such results beside each other: the checks of a PDF container, the official
 * schema and Schematron of the document's profile, the structural layers, and the
 * business rules of the standard over the semantic document. Those engines live in
 * modules that do not know each other, and a renderer must not have to depend on all of
 * them to put their answers on a page. So a run flattens what it found into this one
 * neutral shape, and the renderer reads nothing else.
 *
 * <p>The shape is the report as a reader meets it: <b>who was judged</b>
 * ({@link Identity}), <b>what ran and what each row came to</b> ({@link Block} and
 * {@link Row}), <b>what was found</b> ({@link Finding}, grouped under the block whose
 * engine produced it), and <b>the verdict</b> — the one word over everything, and the
 * {@link Subject} verdicts beside it where a run judged a file and the invoice inside it
 * apart. Nothing is derived here. The verdict is the one the run reached, the rows are the
 * rows it printed, and the findings stand in the order the run reports them — a renderer
 * that sorted them again would put a report in an order no other form of the same run
 * uses.
 *
 * <p>Every text of a report is a {@link Text}, which says whether the words are this
 * project's, and may be translated, or somebody else's, and may not.
 *
 * <p>An absent verdict is the fourth state and not a fourth word. A run a bound of its own
 * stopped reached none of the three states of the specification, section 9.5: it judged
 * nothing, and {@code Optional.empty()} says exactly that where a word would invite a
 * program to branch on it.
 *
 * <p>Instances are immutable and safe to share between threads.
 *
 * @param identity   what was judged, and by which artefacts
 * @param blocks     the check table, one block per engine that had something to say, in
 *                   the order a report prints them
 * @param verdict    the verdict over every engine, empty where a bound of the run stopped
 *                   it before it reached one
 * @param detail     what the verdict line says beyond the word — the components missing
 *                   from the check, or the bound that was met — empty where the word
 *                   stands alone
 * @param subjects   the verdicts of the subjects a run judged apart, in report order,
 *                   empty for a run with one subject
 * @param provenance what was done to the bytes before they were read, one sentence each:
 *                   an encoding that was repaired, an attachment that was taken out of a
 *                   container, an observation the import made that lost nothing. Empty
 *                   where the bytes were read as they arrived.
 */
public record ValidationOutcome(Identity identity,
                                List<Block> blocks,
                                Optional<ValidationStatus> verdict,
                                Optional<Text> detail,
                                List<Subject> subjects,
                                List<Text> provenance) {

    /**
     * What a report writes in place of a verdict where {@link #verdict()} is empty.
     *
     * <p>It is the fourth state and not a fourth word of the three: a run a bound of its
     * own stopped judged nothing, and the specification, section 9.5 has three states and
     * no more. The spelling lives here so that every form of one run — the lines a command
     * prints, its machine-readable report and the file a reader keeps — says it the same
     * way, and the three states are never translated for the same reason.
     */
    public static final String NO_VERDICT = "NO VERDICT";

    /** What a report writes where a subject was not checked at all. */
    public static final String NOT_CHECKED = "NOT CHECKED";

    /** What a report writes where the checks of a subject ran and found nothing. */
    public static final String OK = "OK";

    /**
     * Copies the lists and refuses a missing member.
     *
     * @param identity   what was judged, and by which artefacts
     * @param blocks     the check table, one block per engine that had something to say, in
     *                   the order a report prints them
     * @param verdict    the verdict over every engine, empty where a bound of the run stopped
     *                   it before it reached one
     * @param detail     what the verdict line says beyond the word — the components missing
     *                   from the check, or the bound that was met — empty where the word
     *                   stands alone
     * @param subjects   the verdicts of the subjects a run judged apart, in report order,
     *                   empty for a run with one subject
     * @param provenance what was done to the bytes before they were read, one sentence each:
     *                   an encoding that was repaired, an attachment that was taken out of a
     *                   container, an observation the import made that lost nothing. Empty
     *                   where the bytes were read as they arrived.
     */
    public ValidationOutcome {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(detail, "detail");
        blocks = List.copyOf(blocks);
        subjects = List.copyOf(subjects);
        provenance = List.copyOf(provenance);
    }

    /**
     * Returns every finding of every block, in block order.
     *
     * @return the findings
     */
    public List<Finding> findings() {
        return blocks.stream().flatMap(block -> block.findings().stream()).toList();
    }

    /**
     * The verdict of one of the things a run judged apart.
     *
     * <p>One exit code is all a caller gets, and one word is all the headline of a report
     * carries, but a run over a container answered two questions: an invoice may be sound
     * inside a file that is wrong about it, and a file may be immaculate around a profile
     * that carries no invoice at all. A report that printed the one word alone would let a
     * reader book a conformant container as a bad invoice, which is the mistake these two
     * lines exist to prevent.
     *
     * @param judged  which of them this is
     * @param verdict the word, in the spelling every form of the run uses for it
     * @param detail  what the word does not say — the profile that carries no invoice
     *                line, the components that did not run — empty where it stands alone
     */
    public record Subject(Judged judged, String verdict, Optional<Text> detail) {

        /**
         * Refuses a missing member.
         *
         * @param judged  which of them this is
         * @param verdict the word, in the spelling every form of the run uses for it
         * @param detail  what the word does not say — the profile that carries no invoice
         *                line, the components that did not run — empty where it stands alone
         */
        public Subject {
            Objects.requireNonNull(judged, "judged");
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /** What a subject verdict is about. */
    public enum Judged {

        /** The file an invoice was carried in, and what it says about itself. */
        CONTAINER,

        /** The invoice inside it. */
        INVOICE
    }

    /**
     * What was judged, and what judged it.
     *
     * <p>A report is read by somebody who did not run it, often long afterwards, so the
     * identity has to answer both halves of "is this report about the file I am holding":
     * the digests say which bytes, and the packs say which released rules. A digest is
     * absent rather than invented where the run never had one — there is no semantic
     * digest without a semantic document.
     *
     * <p>Two of the digests are digests of a document that was <em>derived</em> from the
     * input, so the reader that derived it is part of the answer and is named beside them:
     * the same XML read by two readers of this tool can give two semantic digests, and a
     * report that named neither reader could not be compared with the next one.
     *
     * @param input          the name of the input as the caller wrote it
     * @param syntax         the syntax the bytes turned out to be, in the words the run
     *                       uses for it, said of a container to have come out of one
     * @param reader         the reader that built the semantic document from the input,
     *                       where one did; absent for an input that was read rather than
     *                       imported
     * @param semanticModel  the edition of the semantic model the document names, where
     *                       a document was built; a path is an address relative to it
     * @param profile        the specification the document names in BT-24, or the
     *                       conformance level of the container, where either is known
     * @param inputSha256    the SHA-256 of the bytes that were handed over, as 64
     *                       lowercase hexadecimal digits
     * @param semanticDigest the semantic digest of the document (specification,
     *                       section 8.2), where a document was built
     * @param documentDigest the document digest (section 8.3), where a document was built
     * @param sourceSha256   the SHA-256 the document records for the source it was
     *                       imported from (section 4.7), where it records one
     * @param packs          the rule material that was run, in report order
     * @param tool           what produced the report, with its version
     */
    public record Identity(String input,
                           Text syntax,
                           Optional<Text> reader,
                           Optional<String> semanticModel,
                           Optional<String> profile,
                           Optional<String> inputSha256,
                           Optional<String> semanticDigest,
                           Optional<String> documentDigest,
                           Optional<String> sourceSha256,
                           List<Pack> packs,
                           String tool) {

        /**
         * Copies the packs and refuses a missing member.
         *
         * @param input          the name of the input as the caller wrote it
         * @param syntax         the syntax the bytes turned out to be, in the words the run
         *                       uses for it, said of a container to have come out of one
         * @param reader         the reader that built the semantic document from the input,
         *                       where one did; absent for an input that was read rather than
         *                       imported
         * @param semanticModel  the edition of the semantic model the document names, where
         *                       a document was built; a path is an address relative to it
         * @param profile        the specification the document names in BT-24, or the
         *                       conformance level of the container, where either is known
         * @param inputSha256    the SHA-256 of the bytes that were handed over, as 64
         *                       lowercase hexadecimal digits
         * @param semanticDigest the semantic digest of the document (specification,
         *                       section 8.2), where a document was built
         * @param documentDigest the document digest (section 8.3), where a document was built
         * @param sourceSha256   the SHA-256 the document records for the source it was
         *                       imported from (section 4.7), where it records one
         * @param packs          the rule material that was run, in report order
         * @param tool           what produced the report, with its version
         */
        public Identity {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(syntax, "syntax");
            Objects.requireNonNull(reader, "reader");
            Objects.requireNonNull(semanticModel, "semanticModel");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(inputSha256, "inputSha256");
            Objects.requireNonNull(semanticDigest, "semanticDigest");
            Objects.requireNonNull(documentDigest, "documentDigest");
            Objects.requireNonNull(sourceSha256, "sourceSha256");
            Objects.requireNonNull(tool, "tool");
            packs = List.copyOf(packs);
        }
    }

    /**
     * One body of released rules a run executed, named the way a report has to name it.
     *
     * <p>A rule is a rule of a release: the arithmetic of a standard is corrected between
     * releases of the artefacts that state it, so a report that named no version would be
     * unreadable the day the next one lands. {@code source} is the one thing about a pack
     * that its own manifest cannot be trusted with, because a directory a caller pointed
     * the tool at writes its own identity.
     *
     * @param role    what this pack was to the run, for example {@code syntax} or
     *                {@code rules}
     * @param id      the identifier of the pack
     * @param version its version
     * @param release the release of the artefacts it carries, where it names one
     * @param source  where it came from — bundled with the tool, or supplied by the
     *                caller — where the run knows
     */
    public record Pack(String role,
                       String id,
                       String version,
                       Optional<String> release,
                       Optional<String> source) {

        /**
         * Refuses a missing member.
         *
         * @param role    what this pack was to the run, for example {@code syntax} or
         *                {@code rules}
         * @param id      the identifier of the pack
         * @param version its version
         * @param release the release of the artefacts it carries, where it names one
         * @param source  where it came from — bundled with the tool, or supplied by the
         *                caller — where the run knows
         */
        public Pack {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(release, "release");
            Objects.requireNonNull(source, "source");
        }
    }

    /**
     * What one engine ran and what it found.
     *
     * <p>The blocks are kept apart all the way into the report because they answer about
     * different things — a file, an XML document, a semantic document — and because a
     * finding of a rule set is a layer of its own and is never presented as conformance to
     * the ESJ format (specification, section 9.4). The same rule identifier may therefore
     * appear in two blocks, and neither report is merged into the other.
     *
     * <p>That is also why {@code alsoReported} is here. Two engines reporting one defect
     * are two findings and stay two, but a reader counting articles would count the defect
     * twice; the identifiers both engines named say which of them are one defect seen
     * twice, without either report being merged into the other.
     *
     * @param kind         which engine this is
     * @param rows         the rows of the check table, in the order a report prints them
     * @param findings     what the engine found, in the order it reports them
     * @param alsoReported the rule identifiers of this block that another engine of the
     *                     same run reported as well, empty where none
     */
    public record Block(Kind kind, List<Row> rows, List<Finding> findings,
                        List<String> alsoReported) {

        /**
         * Copies the lists and refuses a missing member.
         *
         * @param kind         which engine this is
         * @param rows         the rows of the check table, in the order a report prints them
         * @param findings     what the engine found, in the order it reports them
         * @param alsoReported the rule identifiers of this block that another engine of the
         *                     same run reported as well, empty where none
         */
        public Block {
            Objects.requireNonNull(kind, "kind");
            rows = List.copyOf(rows);
            findings = List.copyOf(findings);
            alsoReported = List.copyOf(alsoReported);
        }

        /**
         * Creates a block no other engine of the run reported the same rules as.
         *
         * @param kind     which engine this is
         * @param rows     the rows of the check table
         * @param findings what the engine found
         */
        public Block(Kind kind, List<Row> rows, List<Finding> findings) {
            this(kind, rows, findings, List.of());
        }

        /** Which engine a block belongs to. */
        public enum Kind {

            /** The file an invoice was carried in: a PDF and what it says about itself. */
            CONTAINER("container"),

            /** The official schema and Schematron of the document's profile, over the XML. */
            SYNTAX("syntax"),

            /** The structural layers and the business rules, over the semantic document. */
            SEMANTIC("semantic");

            private final String token;

            Kind(String token) {
                this.token = token;
            }

            /**
             * Returns the stable lowercase name of this kind.
             *
             * @return the token, for example {@code syntax}
             */
            public String token() {
                return token;
            }
        }
    }

    /**
     * One row of the check table: something that ran, or did not, and what it came to.
     *
     * <p>A row that did not run is never printed as one that passed, and neither is one
     * that ran and could not measure what it was asked to measure. That is why the status
     * has six answers and not two.
     *
     * @param label    what the row is called
     * @param status   what it came to
     * @param errors   how many findings of it decide the verdict
     * @param warnings how many do not
     * @param detail   the reason it did not run, or what it observed beside the status,
     *                 empty where the status says it all
     */
    public record Row(Text label, Status status, int errors, int warnings,
                      Optional<Text> detail) {

        /**
         * Refuses a missing member and a negative count.
         *
         * @param label    what the row is called
         * @param status   what it came to
         * @param errors   how many findings of it decide the verdict
         * @param warnings how many do not
         * @param detail   the reason it did not run, or what it observed beside the status,
         *                 empty where the status says it all
         */
        public Row {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(detail, "detail");
            if (errors < 0 || warnings < 0) {
                throw new IllegalArgumentException("a row counts findings, not " + errors
                        + " errors and " + warnings + " warnings");
            }
        }

        /**
         * Returns a row that ran and found nothing.
         *
         * @param label what the row is called
         * @return the row
         */
        public static Row ok(Text label) {
            return new Row(label, Status.OK, 0, 0, Optional.empty());
        }

        /**
         * Returns a row that ran and found something.
         *
         * @param label    what the row is called
         * @param errors   how many findings decide the verdict
         * @param warnings how many do not
         * @return the row
         */
        public static Row found(Text label, int errors, int warnings) {
            return new Row(label, Status.FOUND, errors, warnings, Optional.empty());
        }

        /**
         * Returns a row that did not run.
         *
         * @param label  what the row is called
         * @param reason why it did not run
         * @return the row
         */
        public static Row skipped(Text label, Text reason) {
            return new Row(label, Status.SKIPPED, 0, 0, Optional.of(reason));
        }

        /** What a row of the check table came to. */
        public enum Status {

            /** It ran over the whole of what it was asked about and found nothing. */
            OK("ok"),

            /** It ran and found something; {@code errors} and {@code warnings} count it. */
            FOUND("found"),

            /** It did not run, and {@code detail} says why. */
            SKIPPED("skipped"),

            /** There was nothing for it to run on, so its absence is no gap. */
            NOT_APPLICABLE("not-applicable"),

            /** It ran over less than it was asked about, so it reached no answer. */
            NO_VERDICT("no-verdict"),

            /**
             * Nothing was checked: the row reports what the document declares about
             * itself, and the declaration is the whole of what it says. The status word
             * of such a row is the declaration, because a column that said
             * {@code ok} would read as a check that passed.
             */
            DECLARED("declared");

            private final String token;

            Status(String token) {
                this.token = token;
            }

            /**
             * Returns the stable lowercase name of this status.
             *
             * @return the token, for example {@code not-applicable}
             */
            public String token() {
                return token;
            }
        }
    }

    /**
     * One thing an engine had to say.
     *
     * <p>Every field is here because a report survives the run that wrote it. The
     * {@code engine} and the {@code pack} say who said it, so that a reader knows whether
     * to go to the standard or to this project and whether the sentence still applies
     * after the pack was replaced; the {@code locations} say where, in whatever terms that
     * engine addresses a document — a semantic path, an XPath expression, a place in a
     * file.
     *
     * <p>{@code flag} is the other half of a finding a core invoice usage specification
     * re-levelled. The severity is the level that specification asks for and decides the
     * verdict; the flag is the level the rule was given before it — by the artefact that
     * raised it, or, where {@code levelledBy} names the specification, by the standard the
     * rule belongs to. A report that printed one of the two levels would leave a reader
     * unable to see that the two publishers disagree, which is precisely what such a
     * finding is about, and one that did not say which body gave which level would leave
     * them unable to see who disagrees with whom.
     *
     * @param category   the family of rules the code belongs to
     * @param code       the identifier the rule set gives this rule
     * @param severity   how much it weighs for the verdict
     * @param flag       the level the finding carried before the specification of the
     *                   document re-levelled it, present only where one did
     * @param levelledBy the specification that re-levelled it, where the {@code flag} is
     *                   the level of the standard rather than of the artefact; empty for
     *                   a finding an artefact flagged itself
     * @param engine     what produced it, for example {@code schematron} or {@code native}
     * @param pack       the pack it belongs to, with its version, where it belongs to one
     * @param locations  where it is, in the terms of its engine, possibly empty
     * @param message    the sentence the engine wrote, in its own words
     */
    public record Finding(String category,
                          String code,
                          Severity severity,
                          Optional<String> flag,
                          Optional<String> levelledBy,
                          String engine,
                          Optional<String> pack,
                          List<String> locations,
                          String message) {

        /**
         * Copies the locations and refuses a missing member.
         *
         * @param category   the family of rules the code belongs to
         * @param code       the identifier the rule set gives this rule
         * @param severity   how much it weighs for the verdict
         * @param flag       the level the finding carried before the specification of the
         *                   document re-levelled it, present only where one did
         * @param levelledBy the specification that re-levelled it, where the {@code flag} is
         *                   the level of the standard rather than of the artefact; empty for
         *                   a finding an artefact flagged itself
         * @param engine     what produced it, for example {@code schematron} or {@code native}
         * @param pack       the pack it belongs to, with its version, where it belongs to one
         * @param locations  where it is, in the terms of its engine, possibly empty
         * @param message    the sentence the engine wrote, in its own words
         */
        public Finding {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(flag, "flag");
            Objects.requireNonNull(levelledBy, "levelledBy");
            Objects.requireNonNull(engine, "engine");
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(message, "message");
            locations = List.copyOf(locations);
            if (levelledBy.isPresent() && flag.isEmpty()) {
                throw new IllegalArgumentException(
                        "a finding a specification re-levelled carries the level it had");
            }
        }

        /**
         * Creates a finding an artefact flagged itself, or that nobody re-levelled.
         *
         * @param category  the family of rules the code belongs to
         * @param code      the identifier the rule set gives this rule
         * @param severity  how much it weighs for the verdict
         * @param flag      the level the artefact itself flagged, present only where the
         *                  specification of the document asked for another one
         * @param engine    what produced it
         * @param pack      the pack it belongs to, where it belongs to one
         * @param locations where it is, in the terms of its engine
         * @param message   the sentence the engine wrote
         */
        public Finding(String category, String code, Severity severity,
                       Optional<String> flag, String engine, Optional<String> pack,
                       List<String> locations, String message) {
            this(category, code, severity, flag, Optional.empty(), engine, pack, locations,
                    message);
        }

        /**
         * Creates a finding no specification re-levelled.
         *
         * @param category  the family of rules the code belongs to
         * @param code      the identifier the rule set gives this rule
         * @param severity  how much it weighs for the verdict
         * @param engine    what produced it
         * @param pack      the pack it belongs to, where it belongs to one
         * @param locations where it is, in the terms of its engine
         * @param message   the sentence the engine wrote
         */
        public Finding(String category, String code, Severity severity, String engine,
                       Optional<String> pack, List<String> locations, String message) {
            this(category, code, severity, Optional.empty(), Optional.empty(), engine,
                    pack, locations, message);
        }
    }
}
