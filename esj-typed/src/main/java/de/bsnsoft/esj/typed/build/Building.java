package de.bsnsoft.esj.typed.build;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.typed.DerivationReport;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.typed.Totals;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.StructuralValidator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the terminal step of the document and {@link InvoiceDraft} both do: derive the
 * amounts, check the invoice, hand out the document. The two entries of the builder
 * differ in how an invoice is written, not in what happens when it is finished, so that
 * part lives here once.
 */
final class Building {

    private final InvoiceEditor editor;

    private final Profile<?> profile;

    private DerivationReport derivation;

    private Registry registry = Registry.en16931();

    Building(InvoiceEditor editor, Profile<?> profile) {
        this.editor = Objects.requireNonNull(editor, "editor");
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    /**
     * Names the registry the structural layers are checked against, which an invoice
     * carrying terms of an extension registry needs for those terms to be checked at all.
     *
     * @param newRegistry the registry, which defines at least the terms of the core model
     */
    void registry(Registry newRegistry) {
        this.registry = Objects.requireNonNull(newRegistry, "registry");
    }

    InvoiceEditor editor() {
        return editor;
    }

    void derive(Totals policy) {
        derivation = editor.derive(Objects.requireNonNull(policy, "policy"));
    }

    Optional<DerivationReport> derivationReport() {
        return Optional.ofNullable(derivation);
    }

    SemanticDocument document() {
        return editor.document();
    }

    /**
     * Checks the invoice as it stands.
     *
     * @param rules the business rules to run, or {@code null} to run none
     * @return what the check found
     */
    BuildReport validate(InvoiceRules rules) {
        SemanticDocument document = document();
        List<Finding> findings = StructuralValidator.validate(document, registry).findings();
        List<MissingTerm> missing = Completeness.check(document, profile, List.of());
        List<RuleViolation> violations = rules == null ? List.of() : rules.check(document);
        return new BuildReport(findings, missing, violations);
    }

    void validateOrThrow(InvoiceRules rules) {
        BuildReport report = validate(rules);
        if (!report.ok()) {
            throw new BuildException(report);
        }
    }

    /**
     * Derives and checks the invoice without refusing it, and returns what the check
     * found.
     *
     * @param rules the business rules to run, or {@code null} to run none
     * @return what the check found, which the caller weighs itself
     */
    BuildReport buildReport(InvoiceRules rules) {
        List<MissingTerm> missing =
                Completeness.check(document(), profile, profile.derivable());
        if (!missing.isEmpty()) {
            return new BuildReport(List.of(), missing, List.of());
        }
        derive(Totals.STANDARD);
        return validate(rules);
    }

    SemanticDocument build(InvoiceRules rules) {
        // What the derivation is about to write is not asked for yet; everything else is,
        // because a derivation over an invoice that states no line, no price or no VAT
        // category refuses with one term and the caller would rather see the list.
        List<MissingTerm> missing =
                Completeness.check(document(), profile, profile.derivable());
        if (!missing.isEmpty()) {
            throw new BuildException(new BuildReport(List.of(), missing, List.of()));
        }
        derive(Totals.STANDARD);
        validateOrThrow(rules);
        return document();
    }
}
