package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.pdf.FacturXProfile;
import de.bsnsoft.esj.syntax.Pack;
import de.bsnsoft.esj.syntax.ProfileLevels;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationLayer;
import de.bsnsoft.esj.validate.ValidationResult;
import de.bsnsoft.esj.validate.ValidationStatus;
import de.bsnsoft.esj.imports.ImportNote;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Runs the three structural layers of the specification, section 9 over one input and
 * collects what they say.
 *
 * <p>The layers are not merged into one verdict, because they answer three questions
 * that fail for three different reasons: whether the bytes are a document, whether every
 * path and value is one the registry knows, and whether the document carries what the
 * model requires of it. A caller that only wants to know whether a converter wrote
 * something readable asks for L1; a caller that is about to book the invoice asks for
 * all three.
 *
 * <p>They do run in order, and an error at one layer stops the ones below it. A document
 * that fails L1 is not a document (specification, section 3.2), and the reader hands back
 * only the members that were sound — so a value L1 rejected is simply absent from what L2
 * and L3 see, and L3 would report the term it belongs to as missing. That statement is not
 * true: the term is in the file, with one character wrong, and sending its author to look
 * for a term that is not missing is worse than saying nothing. The tool already stops for
 * an envelope error; this is the same rule applied to a value error.
 *
 * <p>The same holds one layer down. A path L2 rejected — a term the registry does not
 * know, an occurrence index on a term that may occur once — is a path L3 cannot count
 * occurrences of, and L3 would answer that the term is missing while it stands in the
 * file. So L3 runs only where L2 found no error, and is reported as not checked
 * otherwise. A caller who wants the model layer alone asks for {@code --level l2}.
 *
 * <p>These three are one half of {@code esj validate}. The other half is the syntax
 * engine, which runs the official artefacts of the document's profile over the XML the
 * document arrived as; a {@link SyntaxCheck} carries what it found, or the reason it had
 * nothing to run on. The two halves are kept apart all the way into the report, because
 * they answer about two different things — a semantic document and an XML document — and
 * because the specification, section 9.4 is explicit that findings of a rule set are
 * their own layer and are never presented as ESJ conformance.
 *
 * <p>Beside them stands a third engine, and it is the one that answers for an input that
 * was never XML. {@link RuleCheck} runs the business rules of EN 16931-1, clause 6.4 as
 * rules of this project, over the business terms of the semantic document rather than over
 * the XPath of a syntax, so that one rule decides a UBL invoice, a CII invoice and an ESJ
 * document alike. Its findings are a layer of their own for the same reason the artefacts'
 * findings are: the specification, section 9.4 is explicit about it.
 *
 * <p>What this tool does <em>not</em> check is said as plainly as what it does, and it
 * is said every time rather than in the documentation only: the row of each engine names
 * what it ran or the reason it did not, and the tokens of {@code notChecked} carry the
 * same facts to a program. A validator that stays silent about what it left out invites
 * the reader to assume it covered everything.
 */
final class Validation {

    /** Layer L1 was not run because the input was not read from ESJ bytes. */
    static final String NOT_CHECKED_L1 = "format-l1";

    /** Layer L2 was not run: no document could be built, or layer L1 rejected one. */
    static final String NOT_CHECKED_L2 = "model-l2";

    /**
     * Layer L3 was not run: it was not asked for, no document could be built, or layer L1
     * or layer L2 rejected one.
     */
    static final String NOT_CHECKED_L3 = "cardinality-l3";

    /**
     * The business rules of EN 16931-1, clause 6.4 as rules of this tool's own engine,
     * where that engine did not run.
     *
     * <p>The token is about the native engine and about nothing else, and it is absent
     * from a run that checked them. Where the syntax engine ran an EN 16931 Schematron
     * component — {@code syntax.ran} of the JSON report names it — those rules were
     * checked over the XML by the artefact their publisher released as well, and those
     * findings are in {@code syntax.findings} under their own rule identifiers. The two
     * are independent of each other, and so is this token from either of them.
     */
    static final String NOT_CHECKED_BUSINESS_RULES = "business-rules";

    /**
     * The schema and Schematron of the syntax binding an XML input was written in, where
     * the syntax engine did not run over it.
     */
    static final String NOT_CHECKED_SYNTAX_BINDING = "syntax-binding";

    /**
     * The official artefacts of the profile over the XML an ESJ document was written to,
     * where they did not run over it.
     *
     * <p>It is a token of its own and not the one above, because the two are about
     * different bytes. {@link #NOT_CHECKED_SYNTAX_BINDING} is about the XML a document
     * arrived as; this one is about XML this run produced from a document that arrived as
     * ESJ, and a caller that treats the two alike would be claiming that a document was
     * judged in the form it was sent.
     */
    static final String NOT_CHECKED_WRITTEN_SYNTAX = "written-syntax";

    /**
     * The rule sets of the pack that were left out because of the profile the document
     * names, where the engine ran and only the schema of that pack applied to it.
     *
     * <p>It is a token rather than a sentence because of what follows from it. A document
     * that names a specification this pack carries no rules for is checked against its
     * schema and against nothing else, and the verdict that follows says only that. The
     * report says so in words — the rows name what was skipped and why, and the note under
     * them says "schema validation only" — but a pipeline reads neither, and a run that
     * ended {@code VALID} after one component of six must not look to a program like one
     * that ended {@code VALID} after all six.
     */
    static final String NOT_CHECKED_PROFILE_RULES = "profile-rules";

    /**
     * The invoice was not judged at all: its profile carries no invoice line, so the rules
     * of EN 16931 are not the question to ask of it.
     */
    static final String INVOICE_PROFILE = "profile-not-en16931";

    /** The bytes never became a document: they are not written in the encoding they declare. */
    static final String INVOICE_XML = "xml-encoding";

    /** A structural layer found an error. */
    static final String INVOICE_LAYER = "layer-error";

    /** An official artefact of the profile rejected the XML the document arrived as. */
    static final String INVOICE_SYNTAX = "syntax-finding";

    /**
     * A rule of the native pack rejected the document, at the level the verdict counts it
     * at — the profile's where the profile levels that rule, the rule's own otherwise.
     */
    static final String INVOICE_RULE = "rule-finding";

    /** The semantic path of BT-24, the specification identifier of the invoice. */
    private static final SemanticPath SPECIFICATION_IDENTIFIER =
            SemanticPath.of("/BG-2/BT-24");

    private Validation() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the core invoice usage specification the document names in BT-24.
     *
     * <p>It is read from the document rather than asked of the caller, for the reason the
     * syntax engine reads it from the XML: a document that says it follows a specification
     * is judged by what that specification says about a rule whether or not anybody
     * remembered to say so on a command line.
     *
     * @param document the document
     * @return the customization identifier, empty where the document names none
     */
    static String customizationId(SemanticDocument document) {
        return document.value(SPECIFICATION_IDENTIFIER)
                .map(SemanticValue::canonicalContent)
                .orElse("");
    }

    /**
     * Returns the exit code a report ends a command with, once a limit has been ruled out.
     *
     * <p>It is one place rather than one per command so that every command that reaches a
     * verdict reaches the same one. The three states of the specification, section 9.5 map
     * onto three codes: {@link ExitCode#SUCCESS} where the complete check for this kind of
     * input ran and nothing fatal was found, {@link ExitCode#VALIDATION} where anything
     * that ran found something fatal, and {@link ExitCode#INDETERMINATE} where neither
     * holds — nothing fatal, no limit, and a required component that did not run.
     *
     * @param report what ran and what it found
     * @return the exit code
     */
    static int exitCode(Report report) {
        return switch (report.status()) {
            case VALID -> ExitCode.SUCCESS;
            case INVALID -> ExitCode.VALIDATION;
            case INDETERMINATE -> ExitCode.INDETERMINATE;
        };
    }

    /**
     * Validates one input.
     *
     * @param loaded    the input, read
     * @param syntax    what the syntax engine found over the same input, or the reason it
     *                  did not run
     * @param written   whether the official artefacts run over the XML an ESJ document is
     *                  written to, and the reason to report where they do not
     * @param rules     whether the native rule engine runs over the document, and the
     *                  reason to report where it does not
     * @param level     the highest layer to run: {@link ValidationLayer#L2} stops after
     *                  the model layer, {@link ValidationLayer#L3} runs the cardinality
     *                  layer too
     * @param extension the extension registries this run loads
     * @return what every engine that ran found
     */
    static Report run(Loaded loaded,
                      SyntaxCheck syntax,
                      WrittenRequest written,
                      RuleRequest rules,
                      ValidationLayer level,
                      Extensions extension) {
        return run(loaded, syntax, written, rules, level, extension, Optional.empty());
    }

    /**
     * Validates one input, with what an external PDF/A validator said about its container.
     *
     * @param loaded    the input, read
     * @param syntax    what the syntax engine found over the same input, or the reason it
     *                  did not run
     * @param written   whether the official artefacts run over the XML an ESJ document is
     *                  written to, and the reason to report where they do not
     * @param rules     whether the native rule engine runs over the document, and the
     *                  reason to report where it does not
     * @param level     the highest layer to run
     * @param extension the extension registries this run loads
     * @param pdfa      what the validator of {@code --verapdf} said about the file, or an
     *                  empty optional where none was asked for
     * @return what the engines found
     */
    static Report run(Loaded loaded,
                      SyntaxCheck syntax,
                      WrittenRequest written,
                      RuleRequest rules,
                      ValidationLayer level,
                      Extensions extension,
                      Optional<Verapdf.Result> pdfa) {
        boolean esj = loaded.syntax() == InputSyntax.ESJ;
        Layer l1 = esj
                ? Layer.checked(loaded.formatFindings())
                : Layer.notChecked();

        Optional<FacturXProfile> profile = profile(loaded);
        // The two smallest profiles of the Factur-X family carry no invoice line and are
        // not EN 16931 invoices. Running the layers against one of them would answer a
        // question the document never asked, so the report says that instead.
        boolean en16931 = profile.map(FacturXProfile::isEn16931Invoice).orElse(Boolean.TRUE);
        Optional<SemanticDocument> document = loaded.document();
        Layer l2 = Layer.notChecked();
        Layer l3 = Layer.notChecked();
        if (document.isPresent() && l1.ok() && en16931) {
            List<Registry> registries = Editions.registries(extension);
            l2 = Layer.checked(StructuralValidator.validate(document.get(), registries,
                    EnumSet.of(ValidationLayer.L2)));
            // Where no registry describes the edition the document names, layer L2 reports
            // that once and measured nothing; there is then nothing for the cardinality
            // layer to count either, and running it would repeat the same finding under a
            // second heading.
            boolean edition = l2.notEvaluatedCause()
                    .filter(cause -> cause == Coverage.Cause.EDITION_UNKNOWN).isPresent();
            if (level == ValidationLayer.L3 && l2.ok() && !edition) {
                l3 = Layer.checked(StructuralValidator.validate(document.get(), registries,
                        EnumSet.of(ValidationLayer.L3)));
            }
        }

        RuleCheck ruleCheck =
                rules.evaluate(document, blocked(l1, l2), extension, loaded.syntax());
        WrittenCheck writtenCheck = written.evaluate(document, l2.notEvaluatedCause(),
                blocked(l1, l2));

        // A layer that ran and reported that it could not measure something covered less
        // than it was asked to, and a caller that filters this list for the layers it
        // cares about has to find it here as well as in the reasons of the report: the
        // two lists differ in what they are about, not in what they know.
        boolean limited = l1.findings().stream()
                .anyMatch(finding -> finding.code() == FindingCode.ESJ_L1_LIMIT);
        boolean modelGap = l2.notEvaluatedCause().isPresent();
        List<String> notChecked = new ArrayList<>();
        if (!l1.checked() || limited) {
            notChecked.add(NOT_CHECKED_L1);
        }
        if (!l2.checked() || limited || modelGap) {
            notChecked.add(NOT_CHECKED_L2);
        }
        if (!l3.checked() || limited || modelGap) {
            notChecked.add(NOT_CHECKED_L3);
        }
        if (!ruleCheck.checked()) {
            notChecked.add(NOT_CHECKED_BUSINESS_RULES);
        }
        if (syntax.profileRulesSkipped()) {
            notChecked.add(NOT_CHECKED_PROFILE_RULES);
        }
        if (loaded.syntax().isXml() && !syntax.checked()) {
            notChecked.add(NOT_CHECKED_SYNTAX_BINDING);
        }
        if (!loaded.syntax().isXml() && !writtenCheck.checked()) {
            notChecked.add(NOT_CHECKED_WRITTEN_SYNTAX);
        }
        return new Report(loaded.name(), loaded.syntax(),
                document.map(SemanticDocument::semanticModel),
                syntax, writtenCheck, ruleCheck, l1, l2, l3,
                List.copyOf(notChecked),
                loaded.report().notes(ImportNote.Level.WARNING),
                loaded.report().notes(ImportNote.Level.INFORMATION),
                loaded.syntaxFindings(), loaded.container(), profile, pdfa);
    }

    /**
     * Returns the profile of a document that came out of a container, which is what makes
     * the rules of EN 16931 either the right question to ask of it or the wrong one.
     */
    private static Optional<FacturXProfile> profile(Loaded loaded) {
        if (loaded.container().isEmpty() || loaded.document().isEmpty()) {
            return Optional.empty();
        }
        return loaded.container().orElseThrow().profile(loaded.document().orElseThrow());
    }

    /**
     * Returns the reason the business rules must not be read over this document, where
     * there is one.
     *
     * <p>Two things stop them, and they are different answers. A layer that rejected the
     * document leaves paths and values a rule is not written for, which
     * {@link #readableByARule(Layer)} decides. A layer that measured nothing — no registry
     * for the edition the document names, or a path only an extension registry defines —
     * leaves the rules nothing to resolve their own paths against, and that is a gap in the
     * coverage of this build rather than a defect of the invoice; {@link Coverage} carries
     * it under the model layer's own cause.
     *
     * @param format the format layer
     * @param model  the model layer
     * @return the reason, or empty where the rules may run
     */
    private static Optional<String> blocked(Layer format, Layer model) {
        if (model.notEvaluatedCause().isPresent()) {
            return Optional.of(RuleCheck.NOT_MEASURED);
        }
        return format.ok() && readableByARule(model)
                ? Optional.empty() : Optional.of(RuleCheck.AFTER_MODEL);
    }

    /**
     * Tells whether the model layer left a document the business rules can be read over.
     *
     * <p>The rule the layers follow among themselves is that a rule must not be run over a
     * document whose paths and values are not the ones it is written for: a term the registry
     * does not know, a value that does not spell its data type, an occurrence index on a term
     * that may occur once. A rule that summed over those would report an arithmetic failure
     * whose real cause is one line above it in the same report.
     *
     * <p>A supplementary component the registry declares mandatory and the document does not
     * carry is the one model finding that is not of that kind, and it is let through. The
     * value is there and is readable; what is absent is a component, and it is absent because
     * the sender left it out rather than because anything went wrong on the way in — the
     * importer keeps an identifier that arrived without its scheme precisely so that both
     * engines can see it. Four business rules of the standard are statements about exactly
     * this ({@code BR-62} to {@code BR-65}), and suppressing them here would mean that an
     * invoice whose defect the official artefacts name by its rule identifier is reported by
     * this tool under a structural code alone. The two findings are two names for one defect,
     * which is what the report is for.
     *
     * @param model what the model layer found
     * @return whether the business rules run
     */
    private static boolean readableByARule(Layer model) {
        for (Finding finding : model.errors()) {
            if (finding.code() != FindingCode.ESJ_L2_COMPONENT_MISSING) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the registry the rule pack of this build is compiled against: the default
     * edition, which is the edition the pack was written for.
     *
     * <p>The structural layers do not use it. They are handed every registry this build
     * carries ({@link Editions#registries(boolean)}) and select the one that describes the
     * edition the document names, because a path is an address relative to an edition. A
     * rule pack has no such choice: its paths were resolved against one registry when it
     * was compiled, and a document of another edition is one it does not run over —
     * {@link RuleCheck} says so instead.
     *
     * <p>Without {@code --extension xrechnung} it is the core model alone, and a path
     * through an extension group is reported as not checked rather than as wrong
     * (specification, section 5.6). The option reaches the importer too: without it an
     * extension element is not imported at all, and the note that says so is on the error
     * stream and in the report. The subject of this tool is EN 16931, and a default that
     * quietly carried more than that would be the wrong default; what must not happen is
     * that the loss goes unsaid.
     */
    static Registry registry(Extensions extension) {
        return extension.registry();
    }

    /**
     * Whether the official artefacts run over the XML an ESJ document is written to, and
     * what to report where they do not.
     *
     * <p>It is a request for the reason {@link RuleRequest} is one, and the answer turns
     * on the same thing: only {@link #run} knows what the structural layers made of the
     * document. A document the model layer could not measure is a document nothing says
     * how to write — the paths it holds are paths no loaded registry binds — and a syntax
     * built out of it would be judged by artefacts that had never been shown this invoice.
     */
    @FunctionalInterface
    interface WrittenRequest {

        /**
         * Returns what the artefacts found, or the reason they did not run.
         *
         * @param document the document the reader built, empty where there is none
         * @param gap      the reason the model layer measured nothing, empty where it
         *                 measured what it was given
         * @param blocked  the reason the structural layers leave no document worth
         *                 writing, empty where they left one
         * @return the check
         */
        WrittenCheck evaluate(Optional<SemanticDocument> document,
                              Optional<Coverage.Cause> gap,
                              Optional<String> blocked);

        /**
         * Returns a request that runs nothing and says why.
         *
         * @param target what the document would have been written as
         * @param cause  the closed-vocabulary cause for a program
         * @param reason the same in English
         * @return the request
         */
        static WrittenRequest notRun(String target, Coverage.Cause cause, String reason) {
            return (document, gap, blocked) ->
                    WrittenCheck.notRun(target, cause, reason);
        }
    }

    /**
     * Whether the native rule engine runs over this document, and what to report where it
     * does not.
     *
     * <p>It is a request rather than a flag because the answer depends on two things the
     * caller knows and one it does not. The caller knows which command this is and what
     * {@code --rules} said; only {@link #run} knows whether the structural layers left a
     * document a rule can be trusted to read.
     */
    @FunctionalInterface
    interface RuleRequest {

        /**
         * Returns what the engine found, or the reason it did not run.
         *
         * @param document  the document the importer or the reader built, empty where
         *                  there is none
         * @param blocked   the reason the structural layers leave no document a rule may be
         *                  read over, empty where they left one
         * @param extension the extension registries this run loads
         * @param syntax    what the input was written in, which decides which level table
         *                  of the pack states what this document's profile makes of a rule
         * @return the check
         */
        RuleCheck evaluate(Optional<SemanticDocument> document, Optional<String> blocked,
                           Extensions extension, InputSyntax syntax);

        /**
         * Returns a request that runs the pack over a document the structural layers
         * accepted.
         *
         * <p>The findings are counted at the levels the profile the document names gives
         * those rules, which is where the two engines are joined: the native pack states
         * the standard and the profile says what a rule of it costs a document of its own
         * profile, exactly as it does for a finding of an official artefact
         * ({@link RuleCheck.Levelled}). The pack that carries those tables is the one the
         * syntax engine was given, so both halves of a run read one set of levels.
         *
         * @param chosen the pack {@code --pack} named, or {@code null} to let the document
         *               choose among the bundled ones
         * @return the request
         */
        static RuleRequest pack(Pack chosen) {
            return (document, blocked, extension, syntax) -> {
                if (document.isEmpty()) {
                    return RuleCheck.notRun(RuleCheck.NO_DOCUMENT);
                }
                if (blocked.isPresent()) {
                    return RuleCheck.notRun(blocked.orElseThrow());
                }
                SemanticDocument built = document.orElseThrow();
                ProfileLevels levels =
                        SyntaxPacks.levels(chosen, syntax, customizationId(built));
                return RuleCheck.run(built, extension, levels);
            };
        }

        /**
         * Returns a request that runs nothing and says why.
         *
         * @param reason why not, in English, for the report
         * @return the request
         */
        static RuleRequest notRun(String reason) {
            return (document, blocked, extension, syntax) -> RuleCheck.notRun(reason);
        }
    }

    /**
     * What one layer has to say.
     *
     * @param checked  whether the layer ran at all
     * @param findings what it found, in the order it met them
     */
    record Layer(boolean checked, List<Finding> findings) {

        /**
         * Copies the findings.
         *
         * @param checked  whether the layer ran at all
         * @param findings what it found, in the order it met them
         */
        Layer {
            findings = List.copyOf(findings);
        }

        static Layer checked(List<Finding> findings) {
            return new Layer(true, findings);
        }

        /**
         * Returns a layer that ran, from what the structural validator has to say about
         * it. Only the findings are kept here: the coverage of the run as a whole is
         * {@link Coverage}, which weighs the structural layers against the official
         * artefacts as well.
         */
        static Layer checked(ValidationResult result) {
            return new Layer(true, result.findings());
        }

        static Layer notChecked() {
            return new Layer(false, List.of());
        }

        /**
         * Returns the findings that make the document fail the layer.
         *
         * <p>A finding whose code records something that could not be evaluated is not one
         * of them, whatever severity it carries (specification, section 9.5). The limit
         * finding is the case that matters: it is an error and it says that this reader
         * stopped, not that the invoice is wrong, and reading it as a defect is the one
         * mistake {@link ExitCode#LIMIT} exists to prevent.
         */
        List<Finding> errors() {
            return findings.stream()
                    .filter(Finding::isError)
                    .filter(finding -> !finding.code().recordsSomethingNotEvaluated())
                    .toList();
        }

        /**
         * Tells whether the layer found no error.
         *
         * <p>A finding that records something the validator could not evaluate is not one:
         * an extension path no loaded registry describes, and a document of an edition this
         * build has no registry for, are gaps in the coverage and not defects of the
         * document (specification, section 9.5), and they are answered by
         * {@link #notEvaluatedCause()} instead. A layer that did not run has no findings,
         * so it answers yes; {@link #checked()} is the question that separates the two.
         */
        boolean ok() {
            return errors().isEmpty();
        }

        /**
         * Returns why this layer measured less than it was asked to, where it did.
         *
         * <p>The three codes of {@code FindingCode.recordsSomethingNotEvaluated()} are the
         * specification's way of saying that a check could not be carried out, and two of
         * them reach a layer that otherwise found nothing: an edition no registry of this
         * build describes, and a path only an extension registry defines. Neither is a
         * defect of the document, and neither may be read as a check that passed. The third,
         * a limit, ends the run with {@link ExitCode#LIMIT} and never gets here.
         *
         * @return the cause, or empty where the layer measured everything it saw
         */
        Optional<Coverage.Cause> notEvaluatedCause() {
            for (Finding finding : findings) {
                if (finding.code() == FindingCode.ESJ_L2_EDITION_UNKNOWN) {
                    return Optional.of(Coverage.Cause.EDITION_UNKNOWN);
                }
                if (finding.code() == FindingCode.ESJ_L2_NOT_CHECKED) {
                    return Optional.of(Coverage.Cause.EXTENSION_REGISTRY_MISSING);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * What the three layers have to say about one input.
     *
     * @param input      what the input is called
     * @param syntax     the syntax it was written in
     * @param semanticModel the edition of the semantic model the document names, exactly
     *                   as the document writes it, empty where no document was built
     * @param syntaxCheck what the official artefacts of the profile found over the XML,
     *                   or the reason they were not run
     * @param written    what those artefacts found over the XML an ESJ document was written
     *                   to, or the reason they did not run over it
     * @param ruleCheck  what the native rule engine found over the semantic document, or
     *                   the reason it was not run
     * @param l1         the format layer
     * @param l2         the model layer
     * @param l3         the cardinality layer
     * @param notChecked the stable tokens of everything this run did not check, whether
     *                   it never looked at it or looked and could not measure it; every
     *                   component the coverage names as a gap is among them
     * @param conversion  what the importer had to say about content of the source that
     *                    did not reach the document, empty for an ESJ input
     * @param information what it had to say about the distance between the source syntax
     *                    and the semantic model, which is the same for every document of
     *                    that syntax and cost this one nothing
     * @param syntaxFindings what stands between the bytes and any document at all, which
     *                    is why the layers below were not reached
     * @param container   the PDF the document came out of, absent for a file that was one
     * @param profile     the profile of the container's family the document is written in
     * @param pdfaValidation what the validator of {@code --verapdf} said about the file,
     *                    empty where none was asked for and PDF/A conformance therefore
     *                    stayed outside what this run checked
     */
    record Report(String input,
                  InputSyntax syntax,
                  Optional<String> semanticModel,
                  SyntaxCheck syntaxCheck,
                  WrittenCheck written,
                  RuleCheck ruleCheck,
                  Layer l1,
                  Layer l2,
                  Layer l3,
                  List<String> notChecked,
                  List<ImportNote> conversion,
                  List<ImportNote> information,
                  List<XmlFinding> syntaxFindings,
                  Optional<Container> container,
                  Optional<FacturXProfile> profile,
                  Optional<Verapdf.Result> pdfaValidation) {

        /**
         * Copies the tokens and the notes.
         *
         * @param input      what the input is called
         * @param syntax     the syntax it was written in
         * @param syntaxCheck what the official artefacts of the profile found over the XML,
         *                   or the reason they were not run
         * @param ruleCheck  what the native rule engine found over the semantic document, or
         *                   the reason it was not run
         * @param l1         the format layer
         * @param l2         the model layer
         * @param l3         the cardinality layer
         * @param notChecked the stable tokens of everything this run did not check, whether
         *                   it never looked at it or looked and could not measure it; every
         *                   component the coverage names as a gap is among them
         * @param conversion  what the importer had to say about content of the source that
         *                    did not reach the document, empty for an ESJ input
         * @param information what it had to say about the distance between the source syntax
         *                    and the semantic model, which is the same for every document of
         *                    that syntax and cost this one nothing
         * @param syntaxFindings what stands between the bytes and any document at all, which
         *                    is why the layers below were not reached
         * @param container   the PDF the document came out of, absent for a file that was one
         * @param profile     the profile of the container's family the document is written in
         */
        Report {
            notChecked = List.copyOf(notChecked);
            conversion = List.copyOf(conversion);
            information = List.copyOf(information);
            syntaxFindings = List.copyOf(syntaxFindings);
        }

        /**
         * Tells whether the rules of EN 16931 are the right question to ask of this
         * document.
         *
         * <p>They are not for the two smallest profiles of the Factur-X family: MINIMUM
         * carries little more than the totals and BASIC WL carries no invoice line, so a
         * list of missing mandatory elements would say nothing about the document. Where
         * no profile is known at all they are: nothing said that this is not an EN 16931
         * invoice.
         *
         * @return {@code false} for MINIMUM and BASIC WL, {@code true} otherwise
         */
        boolean en16931Invoice() {
            return profile.map(FacturXProfile::isEn16931Invoice).orElse(Boolean.TRUE);
        }

        /**
         * Returns why the invoice was not found valid, where it was not.
         *
         * <p>The reasons are different answers and a caller acts differently on each: a
         * profile the rules do not apply to is not a defect of the document at all, bytes
         * that are not written in the encoding they declare never became a document, a
         * layer error is a defect the report names term by term, a fatal finding of an
         * official artefact is a defect of the XML the document arrived as, and a fatal
         * finding of the native pack is a business rule of EN 16931 the document breaks —
         * the one defect no artefact reports where the artefact of a syntax cannot fire.
         * The container's own verdict is not among them: a container that is wrong about
         * the invoice it carries says nothing about the invoice, which is why the two are
         * reported as two lines and as two members.
         *
         * <p>It answers over every engine {@link #failed()} answers over, so that the
         * {@code Invoice:} line, the {@code invoice} member of the JSON form and the exit
         * code are one answer; a hybrid whose only fatal finding is a native one would
         * otherwise print {@code INVALID} above {@code Invoice: VALID}.
         *
         * @return one of {@link #INVOICE_PROFILE}, {@link #INVOICE_XML},
         *         {@link #INVOICE_LAYER}, {@link #INVOICE_SYNTAX} and
         *         {@link #INVOICE_RULE}, or an empty optional where the invoice is valid
         */
        Optional<String> invoiceFailure() {
            if (!en16931Invoice()) {
                return Optional.of(INVOICE_PROFILE);
            }
            if (!syntaxFindings.isEmpty()) {
                return Optional.of(INVOICE_XML);
            }
            if (!errors().isEmpty()) {
                return Optional.of(INVOICE_LAYER);
            }
            if (syntaxCheck.failed() || written.failed()) {
                return Optional.of(INVOICE_SYNTAX);
            }
            return ruleCheck.failed() ? Optional.of(INVOICE_RULE) : Optional.empty();
        }

        /**
         * Tells whether the container this document came out of is wrong about it.
         *
         * <p>PDF/A conformance is part of that answer only where the caller asked for it.
         * This tool reads what a file declares about itself and says so in those words;
         * validating the declaration needs a validator this project does not publish, and
         * a run without {@code --verapdf} leaves the question outside what it checked
         * rather than answering it from the declaration. A run that named one asked the
         * question, so a file the validator rejects is a container that is wrong about
         * itself.
         */
        boolean containerFailed() {
            return container.map(Container::hasErrors).orElse(Boolean.FALSE)
                    || pdfaValidation.map(result -> !result.compliant()).orElse(Boolean.FALSE);
        }

        /** Returns every error finding of every layer, in layer order. */
        List<Finding> errors() {
            List<Finding> errors = new ArrayList<>(l1.errors());
            errors.addAll(l2.errors());
            errors.addAll(l3.errors());
            return List.copyOf(errors);
        }

        /**
         * Returns what the complete check for this input was and how much of it ran.
         *
         * @return the coverage of this run
         */
        Coverage coverage() {
            return Coverage.of(this);
        }

        /**
         * Returns the verdict over both engines, in the three states of the
         * specification, section 9.5.
         *
         * <p>{@code INVALID} outranks the rest: a defect found is a defect whatever else
         * was not reached. {@code VALID} is given only where the complete check for this
         * kind of input ran, which is {@link Coverage}. Everything between the two —
         * nothing fatal found, and a required component that did not run — is
         * {@code INDETERMINATE}, and the report names what and why.
         *
         * <p>A run stopped by a limit has no verdict at all and is not one of these three;
         * {@link #limit()} is asked first and the command leaves with
         * {@link ExitCode#LIMIT}.
         *
         * @return the verdict
         */
        ValidationStatus status() {
            if (failed()) {
                return ValidationStatus.INVALID;
            }
            return coverage().complete()
                    ? ValidationStatus.VALID : ValidationStatus.INDETERMINATE;
        }

        /**
         * Tells whether this run rejected what it was given.
         *
         * <p>It is one answer over every engine that ran and over several verdicts that
         * are kept apart in the report: a layer that found an error, a fatal finding of an
         * official artefact over the XML the document arrived as or over the XML it was
         * written to, a fatal finding of a native business rule, bytes that never
         * became a document, a container that is wrong about the invoice it carries, and a
         * profile the rules of EN 16931 do not apply to. A caller who runs
         * {@code esj validate x.xml && deploy} asked one question, gets one exit code, and
         * the report says which of them it was.
         */
        boolean failed() {
            return !errors().isEmpty() || syntaxCheck.failed() || written.failed()
                    || ruleCheck.failed() || !syntaxFindings.isEmpty() || containerFailed()
                    || !en16931Invoice();
        }

        /**
         * Returns the finding that says this run was configured to read less than the
         * document holds, where one was made.
         *
         * <p>Layer L1 reports a limit as an error finding, as the specification,
         * section 9.5 has it do, and the report prints it among the others — but the
         * verdict it carries is not "this invoice is invalid", it is "this reader stopped".
         * The command turns it into {@link ExitCode#LIMIT} so that a caller can tell a
         * document it must not book from a document it should have read with more; see
         * {@link Bounds}.
         *
         * @return the first limit finding, or empty
         */
        Optional<Finding> limit() {
            return l1.findings().stream()
                    .filter(finding -> finding.code() == FindingCode.ESJ_L1_LIMIT)
                    .findFirst();
        }
    }
}
