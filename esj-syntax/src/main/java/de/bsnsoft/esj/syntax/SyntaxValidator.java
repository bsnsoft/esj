package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XmlFrontDoor;
import de.bsnsoft.esj.xr.XrSyntax;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;

/**
 * Validates a UBL 2.1 or UN/CEFACT CII invoice against the official validation artefacts
 * of its profile.
 *
 * <p>Three blocks run, in this order, and the first one that fails is the last one that
 * runs:
 *
 * <ol>
 *   <li><strong>XML.</strong> Well-formedness with the parser of {@link XmlFrontDoor} —
 *       no document type declaration, no external entity, no XInclude — and the encoding
 *       the document declares against the bytes it carries. A document that is not
 *       well-formed has no elements to validate, and one whose encoding is not what it
 *       says has no text to read, so neither goes on to the schema.</li>
 *   <li><strong>XSD.</strong> The schema modules of the syntax, from the pack. A document
 *       that is not valid against its own schema does not go on to the rules: an element
 *       in a place the schema forbids makes every rule that looks for it say something
 *       about a document nobody sent.</li>
 *   <li><strong>Schematron.</strong> The compiled rule sets of the pack that apply to the
 *       document's syntax and profile — the EN 16931 artefacts of CEN/TC 434 and the
 *       artefacts of the core invoice usage specification the document names — run as
 *       data, and their report turned into findings.</li>
 * </ol>
 *
 * <p>The answer is a {@link SyntaxReport}: a verdict, the findings in a deterministic
 * order, and what ran and what did not with the reason. Two runs over the same bytes with
 * the same pack produce the same verdict, the same findings in the same order and the
 * same rule identifiers. One thing in a report is not this module's to fix: the sentence
 * of a schema finding comes from the validator of the platform, which writes it in the
 * default locale of the virtual machine and ignores the property that asks it for
 * another, so an application whose reports travel between machines fixes that locale for
 * its process as the {@code esj} command line does.
 *
 * <p>What this engine does not do is decide anything itself. Every rule is the
 * publisher's, every message is the publisher's wording, and both levels a finding
 * carries are the publishers' too: {@link SyntaxFinding#flag()} is the flag the artefact
 * set on the rule, and {@link SyntaxFinding#severity()} is what the core invoice usage
 * specification of the document says a rule of that name means for a document of its own
 * profile, which is the flag unless that specification levels it itself. The findings are
 * about the document's binding to its XML syntax; they are a layer of their own and are
 * never presented as conformance to the ESJ format, which layers L1 to L3 of the
 * specification define.
 *
 * <h2>Limits</h2>
 *
 * <p>A run reaches a verdict or it raises an exception; it never reports a limit as an
 * invalid document. An input larger than {@link SyntaxOptions#maxInputBytes()} and a run
 * still going at {@link SyntaxOptions#maxRuntime()} both raise
 * {@link SyntaxLimitException}, and a document of a syntax no pack binds raises
 * {@link SyntaxNotSupportedException}.
 *
 * <h2>Cost</h2>
 *
 * <p>Compiling the schema library and the rule sets is the expensive part and does not
 * depend on the document, so each artefact is compiled once per process and shared from
 * there. A process that validates one invoice and exits pays it; a process that validates
 * many pays it once, and {@link SyntaxReport#ran()} reports both halves separately so
 * that the difference is visible rather than assumed.
 */
public final class SyntaxValidator {

    private SyntaxValidator() {
        throw new AssertionError("no instances");
    }

    /**
     * Validates a document with the default options.
     *
     * @param xml the bytes of the document
     * @return the report
     * @throws SyntaxLimitException        if a limit of {@link SyntaxOptions#defaults()}
     *                                     was reached
     * @throws SyntaxNotSupportedException if no pack binds this document
     * @throws PackException               if a pack cannot be read or run
     * @throws NullPointerException        if {@code xml} is {@code null}
     */
    public static SyntaxReport validate(byte[] xml) {
        return validate(xml, SyntaxOptions.defaults());
    }

    /**
     * Validates a document.
     *
     * @param xml     the bytes of the document
     * @param options what this run is allowed to do
     * @return the report
     * @throws SyntaxLimitException        if a limit of the options was reached, in which
     *                                     case the document has no verdict
     * @throws SyntaxNotSupportedException if the root element belongs to no syntax the
     *                                     standard binds, or the pack carries nothing
     *                                     that applies to it
     * @throws PackException               if a pack cannot be read or run
     * @throws NullPointerException        if an argument is {@code null}
     */
    public static SyntaxReport validate(byte[] xml, SyntaxOptions options) {
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(options, "options");
        if (xml.length > options.maxInputBytes()) {
            throw new SyntaxLimitException("the document is " + xml.length + " bytes long,"
                    + " and this run was given " + options.maxInputBytes()
                    + ", so it has no verdict");
        }
        long start = System.nanoTime();
        Budget budget = Budget.of(options.maxRuntime());

        XmlCheck.Result parsed = budget.call("reading the document", () -> XmlCheck.check(xml));
        if (parsed.document().isEmpty()) {
            return report(parsed.findings(), List.of(), List.of(), Optional.empty(), "",
                    Optional.empty(), Optional.empty(), false, false, start);
        }

        XdmNode document = parsed.document().get();
        XdmNode root = XmlFrontDoor.rootElement(document);
        XrSyntax syntax = XmlFrontDoor.detect(root).orElseThrow(() ->
                new SyntaxNotSupportedException("the root element of this document is "
                        + root.getNodeName().getLocalName() + ", and the standard binds"
                        + " only UBL Invoice, UBL CreditNote and CrossIndustryInvoice"));
        String customizationId = Profiles.customizationId(root, syntax);
        PackSelection selection = options.pack()
                .map(pack -> pack.select(syntax, customizationId))
                .orElseGet(() -> Packs.select(syntax, customizationId));
        if (selection.applied().isEmpty()) {
            throw new SyntaxNotSupportedException("the pack " + selection.pack().directory()
                    + " carries no artefact for a " + Syntaxes.title(syntax)
                    + " of this profile");
        }

        List<SyntaxFinding> findings = new ArrayList<>(parsed.findings());
        List<ComponentRun> ran = new ArrayList<>();
        List<SkippedComponent> skipped = new ArrayList<>(selection.skipped());
        Pack pack = selection.pack();

        boolean schemaValid = true;
        for (PackComponent component : selection.applied(ComponentRole.XSD)) {
            String entry = component.entry(Syntaxes.token(syntax)).orElseThrow();
            XsdCheck.Result result =
                    XsdCheck.run(xml, pack, component, entry, syntax, budget);
            findings.addAll(result.findings());
            ran.add(new ComponentRun(component.name(), Engine.XSD, result.duration(),
                    result.compilation(), false));
            schemaValid &= result.findings().stream().noneMatch(SyntaxFinding::fatal);
        }

        for (PackComponent component : selection.applied(ComponentRole.SCHEMATRON_XSLT)) {
            if (!schemaValid) {
                skipped.add(new SkippedComponent(component.name(),
                        SkippedComponent.Reason.SCHEMA_INVALID,
                        "the document is not valid against the schema of its syntax, and a"
                        + " rule set run over it would describe a document nobody sent"));
                continue;
            }
            String entry = component.entry(Syntaxes.token(syntax)).orElseThrow();
            SchematronCheck.Result result = SchematronCheck.run(document, pack, component,
                    entry, selection.levels(), budget);
            findings.addAll(result.findings());
            ran.add(new ComponentRun(component.name(), Engine.SCHEMATRON, result.duration(),
                    result.compilation(), result.stopped()));
        }

        return report(findings, ran, skipped, Optional.of(syntax), customizationId,
                Optional.of(pack), selection.profileNote(),
                selection.profileRulesSkipped(), selection.profileRulesMissing(), start);
    }

    private static SyntaxReport report(List<SyntaxFinding> findings,
                                       List<ComponentRun> ran,
                                       List<SkippedComponent> skipped,
                                       Optional<XrSyntax> syntax,
                                       String customizationId,
                                       Optional<Pack> pack,
                                       Optional<String> profileNote,
                                       boolean profileRulesSkipped,
                                       boolean profileRulesMissing,
                                       long start) {
        List<SyntaxFinding> ordered = new ArrayList<>(findings);
        ordered.sort(SyntaxFinding.ORDER);
        return new SyntaxReport(verdict(ordered, profileRulesMissing), syntax,
                customizationId, pack, ordered, ran,
                skipped, profileNote, profileRulesSkipped, profileRulesMissing,
                Duration.ofNanos(System.nanoTime() - start));
    }

    /**
     * Derives the verdict from what was found and from what was left out.
     *
     * <p>A fatal finding outranks everything: a defect found is a defect whatever else did
     * not run. Where nothing fatal was found, a rule set the document asked for and this
     * pack does not carry leaves the answer open rather than affirmative — the engine has
     * not looked at what that specification requires, and a caller that read the affirmative
     * would be told that rules nobody ran were satisfied.
     */
    private static Verdict verdict(List<SyntaxFinding> findings, boolean profileRulesMissing) {
        if (findings.stream().anyMatch(SyntaxFinding::fatal)) {
            return Verdict.INVALID;
        }
        return profileRulesMissing ? Verdict.INDETERMINATE : Verdict.VALID;
    }
}
