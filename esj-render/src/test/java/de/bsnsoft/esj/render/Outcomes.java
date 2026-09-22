package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.report.Phrase;
import de.bsnsoft.esj.report.Text;
import de.bsnsoft.esj.report.ValidationOutcome;
import de.bsnsoft.esj.report.ValidationOutcome.Judged;
import de.bsnsoft.esj.report.ValidationOutcome.Subject;
import de.bsnsoft.esj.validate.Severity;
import de.bsnsoft.esj.validate.ValidationStatus;
import java.util.List;
import java.util.Optional;

/**
 * The runs the report tests are written against.
 *
 * <p>A {@link ValidationOutcome} is what a whole command came to, and the engines that
 * fill one in — the container reader, the official artefacts, the structural layers, the
 * rule pack — are in four other modules. This module renders an outcome and does not
 * produce one, so the outcomes here are written out by hand, from the shapes the command
 * line actually reports: a corpus instance nothing was found wrong with, the same instance
 * with defects that both engines name, a document that was never XML, an invoice taken
 * out of a PDF whose container is wrong about it, and a conformant container whose profile
 * carries no invoice to check at all.
 *
 * <p>What is not invented is the identity. The digests are taken over the documents of the
 * repository, so a golden report names the document it is a report about and would notice
 * the day that document changes.
 */
final class Outcomes {

    /** The instance of the corpus the first two runs are about. */
    static final String INSTANCE = "business-cases/standard/01.01a-INVOICE_ubl.xml";

    /** What the reports say produced them. It carries no build, so a golden is stable. */
    private static final String TOOL = "esj 0.1.0";

    /** The edition of the semantic model every document of these runs names. */
    private static final String MODEL = "EN16931-1:2017+A1:2019/AC:2020";

    /** The reader that built the document of an XML run, as a report names it. */
    private static final Text READER = Text.of(Phrase.READER_STREAMING, "streaming");

    /** The syntax of an invoice a run took out of a container. */
    private static final Text FROM_PDF = Text.of(Phrase.SYNTAX_FROM_ATTACHMENT, "CII");

    /** The pack the native rule engine ran, as a row of the check table names it. */
    private static final String RULE_PACK_ID = "en16931/1.3.16";

    /** The validation pack of the syntax engine, as a report names it. */
    private static final ValidationOutcome.Pack SYNTAX_PACK = new ValidationOutcome.Pack(
            "syntax", "xrechnung", "3.0.2", Optional.of("2026-08-31"),
            Optional.of("bundled"));

    /** The rule pack of the native engine. */
    private static final ValidationOutcome.Pack RULE_PACK = new ValidationOutcome.Pack(
            "rules", "en16931", "1.3.16", Optional.empty(), Optional.of("bundled"));

    private Outcomes() {
        throw new AssertionError("no instances");
    }

    /** Returns the document the first two runs are about. */
    static SemanticDocument instance() {
        return Corpus.instance(INSTANCE);
    }

    /**
     * A UBL invoice of the corpus that every check ran over and nothing was found wrong
     * with: the verdict a caller writing {@code esj validate x.xml && deploy} is after.
     *
     * @return the outcome
     */
    static ValidationOutcome valid() {
        SemanticDocument document = instance();
        return new ValidationOutcome(
                identity("01.01a-INVOICE_ubl.xml", "UBL Invoice",
                        "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:"
                                + "xrechnung_3.0", document),
                List.of(new ValidationOutcome.Block(ValidationOutcome.Block.Kind.SYNTAX,
                                List.of(ok(Phrase.ROW_XML),
                                        ValidationOutcome.Row.ok(Text.words("UBL 2.1 XSD")),
                                        ValidationOutcome.Row.ok(
                                                Text.words("EN 16931 UBL Schematron")),
                                        ValidationOutcome.Row.ok(
                                                Text.words("XRechnung UBL Schematron"))),
                                List.of()),
                        new ValidationOutcome.Block(ValidationOutcome.Block.Kind.SEMANTIC,
                                List.of(ok(Phrase.ROW_IMPORT),
                                        notApplicable(Phrase.ROW_FORMAT_L1,
                                                Phrase.REASON_INPUT_XML),
                                        ok(Phrase.ROW_MODEL_L2),
                                        ok(Phrase.ROW_CARDINALITY_L3),
                                        ValidationOutcome.Row.ok(rules())),
                                List.of())),
                Optional.of(ValidationStatus.VALID), Optional.empty(), List.of(), List.of());
    }

    /**
     * The same invoice with defects, each of them named by both engines: the official
     * Schematron over the XML it arrived as, and the native rule pack over the document
     * the importer built. Neither report is merged into the other, which is why one rule
     * identifier stands in two blocks and the block says which identifiers those are.
     *
     * <p>It is also the run where a core invoice usage specification levelled a finding of
     * an artefact down, and where the import could not carry everything the source said.
     *
     * @return the outcome
     */
    static ValidationOutcome mutated() {
        SemanticDocument document = instance();
        return new ValidationOutcome(
                identity("01.01a-INVOICE_ubl.xml", "UBL Invoice",
                        "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:"
                                + "xrechnung_3.0", document),
                List.of(new ValidationOutcome.Block(ValidationOutcome.Block.Kind.SYNTAX,
                                List.of(ok(Phrase.ROW_XML),
                                        ValidationOutcome.Row.ok(Text.words("UBL 2.1 XSD")),
                                        ValidationOutcome.Row.found(
                                                Text.words("EN 16931 UBL Schematron"), 1, 0),
                                        ValidationOutcome.Row.found(
                                                Text.words("XRechnung UBL Schematron"), 0, 1)),
                                List.of(new ValidationOutcome.Finding("EN-BR", "BR-CO-10",
                                                Severity.ERROR, "schematron",
                                                Optional.of("xrechnung/3.0.2/2026-08-31"),
                                                List.of("/*:Invoice[1]/*:LegalMonetaryTotal[1]"),
                                                "[BR-CO-10]-Sum of Invoice line net amount"
                                                        + " (BT-106) = Σ Invoice line net"
                                                        + " amount (BT-131)."),
                                        new ValidationOutcome.Finding("EN-CL", "BR-CL-13",
                                                Severity.INFO, Optional.of("fatal"),
                                                "schematron",
                                                Optional.of("xrechnung/3.0.2/2026-08-31"),
                                                List.of("/*:Invoice[1]/*:InvoiceLine[1]"),
                                                "[BR-CL-13]-Item classification identifier"
                                                        + " identification scheme identifier"
                                                        + " MUST be coded using one of the"
                                                        + " UNTDID 7143 list."),
                                        new ValidationOutcome.Finding("XR-BR", "BR-DE-15",
                                                Severity.WARNING, "schematron",
                                                Optional.of("xrechnung/3.0.2/2026-08-31"),
                                                List.of("/*:Invoice[1]"),
                                                "[BR-DE-15] Das Element \"Buyer reference\""
                                                        + " (BT-10) muss übermittelt werden."))),
                        new ValidationOutcome.Block(ValidationOutcome.Block.Kind.SEMANTIC,
                                List.of(ValidationOutcome.Row.found(
                                                Text.of(Phrase.ROW_IMPORT), 0, 1),
                                        notApplicable(Phrase.ROW_FORMAT_L1,
                                                Phrase.REASON_INPUT_XML),
                                        ok(Phrase.ROW_MODEL_L2),
                                        ok(Phrase.ROW_CARDINALITY_L3),
                                        ValidationOutcome.Row.found(rules(), 2, 0)),
                                List.of(new ValidationOutcome.Finding("import",
                                                "DUPLICATE_PATH", Severity.WARNING,
                                                "importer", Optional.empty(),
                                                List.of("/BG-16/BT-81"),
                                                "a second element carrying BT-81 resolved to"
                                                        + " a path that is already taken, so"
                                                        + " it was skipped"),
                                        new ValidationOutcome.Finding("EN-BR", "BR-CO-10",
                                                Severity.ERROR, "native",
                                                Optional.of(RULE_PACK_ID),
                                                List.of("/BG-22/BT-106", "/BG-25/*/BT-131"),
                                                "The sum of the invoice line net amounts"
                                                        + " (BT-131) is 314.86, and sum of"
                                                        + " invoice line net amount (BT-106)"
                                                        + " at /BG-22/BT-106 carries 315.86."),
                                        new ValidationOutcome.Finding("EN-CL", "BR-CL-13",
                                                Severity.ERROR, "native",
                                                Optional.of(RULE_PACK_ID),
                                                List.of("/BG-25/*/BG-31/BT-158/*"),
                                                "The item classification identifier (BT-158)"
                                                        + " names the identification scheme"
                                                        + " CVD, which is not on the"
                                                        + " untdid-7143 snapshot this pack"
                                                        + " decides against.")),
                                List.of("BR-CO-10", "BR-CL-13"))),
                Optional.of(ValidationStatus.INVALID), Optional.empty(), List.of(),
                List.of(Text.of(Phrase.TIMES,
                        "UNKNOWN_TERM at /BG-25/1: no registry of this build knows the term",
                        "3")));
    }

    /**
     * A document that was never XML. No official artefact will look at one, so the syntax
     * block is not applicable rather than absent, and the native rules are the only check
     * the arithmetic of the standard gets. This run was asked for less than the complete
     * check and therefore reaches the third state.
     *
     * @return the outcome
     */
    static ValidationOutcome esj() {
        SemanticDocument document = Corpus.example("standard-invoice");
        return new ValidationOutcome(
                new ValidationOutcome.Identity("standard-invoice.esj.json",
                        Text.words("ESJ"),
                        Optional.empty(), Optional.of(MODEL),
                        Optional.of("urn:cen.eu:en16931:2017"),
                        Optional.of(Corpus.sha256(
                                Corpus.bytes("/examples/standard-invoice.esj.json"))),
                        Optional.of(Canonicalizer.semanticDigest(document)),
                        Optional.of(Canonicalizer.documentDigest(document)),
                        Optional.empty(), List.of(), TOOL),
                List.of(new ValidationOutcome.Block(ValidationOutcome.Block.Kind.SYNTAX,
                                List.of(notApplicable(Phrase.ROW_PACK, Phrase.REASON_NO_XML)),
                                List.of()),
                        new ValidationOutcome.Block(ValidationOutcome.Block.Kind.SEMANTIC,
                                List.of(ok(Phrase.ROW_FORMAT_L1), ok(Phrase.ROW_MODEL_L2),
                                        ok(Phrase.ROW_CARDINALITY_L3),
                                        ValidationOutcome.Row.skipped(rules(),
                                                Text.words("--rules none"))),
                                List.of())),
                Optional.of(ValidationStatus.INDETERMINATE),
                Optional.of(Text.of(Phrase.DETAIL_MISSING,
                        "business-rules (skipped-by-caller)")),
                List.of(), List.of());
    }

    /**
     * An invoice taken out of a hybrid PDF whose container is wrong about it. The two
     * verdicts are two: the invoice inside may be sound while the file around it is not,
     * and the report says both under the one word. The PDF/A row says what this project
     * says about PDF/A everywhere — the file declares a conformance and nothing validated
     * it, which is why its status is that declaration and not {@code OK}.
     *
     * @return the outcome
     */
    static ValidationOutcome pdf() {
        SemanticDocument document = Corpus.example("minimal");
        return new ValidationOutcome(
                new ValidationOutcome.Identity("factur-x.pdf", FROM_PDF,
                        Optional.of(READER), Optional.of(MODEL), Optional.of("EN 16931"),
                        Optional.of("e0f1a2b3c4d5e6f70819a2b3c4d5e6f70819a2b3c4d5e6f70819"
                                + "a2b3c4d5e6f7"),
                        Optional.of(Canonicalizer.semanticDigest(document)),
                        Optional.of(Canonicalizer.documentDigest(document)),
                        Optional.of("9fd4a1c0b7e25638194a7c0d5e2f81b3c6a09d47e85f213b6c0a"
                                + "9d47e85f213b"),
                        List.of(SYNTAX_PACK, RULE_PACK), TOOL),
                List.of(new ValidationOutcome.Block(ValidationOutcome.Block.Kind.CONTAINER,
                                List.of(ok(Phrase.ROW_PDF_STRUCTURE), pdfa(),
                                        ValidationOutcome.Row.found(
                                                Text.of(Phrase.ROW_PDF_AF), 1, 0),
                                        ValidationOutcome.Row.found(
                                                Text.of(Phrase.ROW_PDF_XMP), 0, 1),
                                        ok(Phrase.ROW_PDF_EMBEDDED)),
                                List.of(new ValidationOutcome.Finding("PDF-AF",
                                                "PDF-AF-MISSING", Severity.ERROR, "container",
                                                Optional.empty(),
                                                List.of("attachment 1, factur-x.xml"),
                                                "the invoice attachment is not in the"
                                                        + " associated files array of the"
                                                        + " document"),
                                        new ValidationOutcome.Finding("PDF-XMP",
                                                "PDF-XMP-LEVEL", Severity.WARNING,
                                                "container", Optional.empty(), List.of(),
                                                "the XMP packet declares the conformance"
                                                        + " level \"EN 16931\" and the"
                                                        + " document names another"))),
                        new ValidationOutcome.Block(ValidationOutcome.Block.Kind.SYNTAX,
                                List.of(ok(Phrase.ROW_XML),
                                        ValidationOutcome.Row.ok(Text.words("CII D16B XSD")),
                                        ValidationOutcome.Row.ok(
                                                Text.words("EN 16931 CII Schematron")),
                                        ValidationOutcome.Row.skipped(
                                                Text.words("XRechnung CII Schematron"),
                                                Text.of(Phrase.SKIPPED_OTHER_PROFILE))),
                                List.of()),
                        new ValidationOutcome.Block(ValidationOutcome.Block.Kind.SEMANTIC,
                                List.of(ok(Phrase.ROW_IMPORT),
                                        notApplicable(Phrase.ROW_FORMAT_L1,
                                                Phrase.REASON_INPUT_XML),
                                        ok(Phrase.ROW_MODEL_L2),
                                        ok(Phrase.ROW_CARDINALITY_L3),
                                        ValidationOutcome.Row.ok(rules())),
                                List.of())),
                Optional.of(ValidationStatus.INVALID),
                Optional.of(Text.of(Phrase.DETAIL_CONTAINER_WRONG)),
                List.of(new Subject(Judged.CONTAINER, ValidationStatus.INVALID.name(),
                                Optional.empty()),
                        new Subject(Judged.INVOICE, ValidationStatus.VALID.name(),
                                Optional.empty())),
                List.of(Text.of(Phrase.PROVENANCE_ATTACHMENT, "1", "factur-x.xml")));
    }

    /**
     * A conformant Factur-X container whose profile carries no invoice line at all. The
     * headline is {@code INVALID}, because the tool was asked for an EN 16931 invoice and
     * this file is not one — and the two subject verdicts are what keep a reader from
     * booking a file nothing is wrong with as a bad invoice.
     *
     * @return the outcome
     */
    static ValidationOutcome minimum() {
        return new ValidationOutcome(
                new ValidationOutcome.Identity("minimum.pdf", FROM_PDF,
                        Optional.of(READER), Optional.of(MODEL), Optional.of("MINIMUM"),
                        Optional.of("11d2e3f405162738495a6b7c8d9e0f112233445566778899aabb"
                                + "ccddeeff0011"),
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        List.of(RULE_PACK), TOOL),
                List.of(new ValidationOutcome.Block(ValidationOutcome.Block.Kind.CONTAINER,
                                List.of(ok(Phrase.ROW_PDF_STRUCTURE), pdfa(),
                                        ok(Phrase.ROW_PDF_AF), ok(Phrase.ROW_PDF_XMP),
                                        ok(Phrase.ROW_PDF_EMBEDDED)),
                                List.of()),
                        new ValidationOutcome.Block(ValidationOutcome.Block.Kind.SEMANTIC,
                                List.of(notApplicable(Phrase.ROW_MODEL_L2,
                                                Phrase.REASON_NO_INVOICE_LINE),
                                        notApplicable(Phrase.ROW_CARDINALITY_L3,
                                                Phrase.REASON_NO_INVOICE_LINE),
                                        ValidationOutcome.Row.ok(rules())),
                                List.of())),
                Optional.of(ValidationStatus.INVALID),
                Optional.of(Text.of(Phrase.DETAIL_NOT_EN16931, "MINIMUM")),
                List.of(new Subject(Judged.CONTAINER, ValidationOutcome.OK, Optional.empty()),
                        new Subject(Judged.INVOICE, ValidationOutcome.NOT_CHECKED,
                                Optional.of(Text.of(Phrase.SUBJECT_PROFILE, "MINIMUM")))),
                List.of(Text.of(Phrase.PROVENANCE_ATTACHMENT, "1", "factur-x.xml")));
    }

    /** Returns the identity of a run over one XML instance of the corpus. */
    private static ValidationOutcome.Identity identity(String input, String syntax,
                                                       String profile,
                                                       SemanticDocument document) {
        return new ValidationOutcome.Identity(input, Text.words(syntax), Optional.of(READER),
                Optional.of(MODEL), Optional.of(profile),
                Optional.of(Corpus.sha256(Corpus.bytes("/conformance/kosit/" + INSTANCE))),
                Optional.of(Canonicalizer.semanticDigest(document)),
                Optional.of(Canonicalizer.documentDigest(document)),
                document.source().flatMap(SemanticDocument.Source::sha256),
                List.of(SYNTAX_PACK, RULE_PACK), TOOL);
    }

    /** Returns the row of the PDF/A conformance a container declares. */
    private static ValidationOutcome.Row pdfa() {
        return new ValidationOutcome.Row(Text.of(Phrase.ROW_PDFA),
                ValidationOutcome.Row.Status.DECLARED, 0, 0,
                Optional.of(Text.of(Phrase.PDFA_DECLARED, "PDF/A-3B")));
    }

    /** Returns the label of the row of the native rule engine, with its pack. */
    private static Text rules() {
        return Text.of(Phrase.ROW_RULES, RULE_PACK_ID);
    }

    /** Returns a row of this project's own that ran and found nothing. */
    private static ValidationOutcome.Row ok(Phrase label) {
        return ValidationOutcome.Row.ok(Text.of(label));
    }

    /** Returns a row that had nothing to run on, with the reason. */
    private static ValidationOutcome.Row notApplicable(Phrase label, Phrase reason) {
        return new ValidationOutcome.Row(Text.of(label),
                ValidationOutcome.Row.Status.NOT_APPLICABLE, 0, 0,
                Optional.of(Text.of(reason)));
    }
}
