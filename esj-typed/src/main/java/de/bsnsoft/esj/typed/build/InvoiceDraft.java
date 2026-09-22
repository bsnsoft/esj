package de.bsnsoft.esj.typed.build;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.typed.DerivationReport;
import de.bsnsoft.esj.typed.En16931;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.typed.Totals;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * An invoice written in any order, finished the way the step chain finishes one.
 *
 * <p>The members are those of the typed editor, which has a setter per business term and
 * no order of its own; the completeness the step chain checks while the code is compiled
 * is checked here when {@link #build()} runs, and every member the model or the profile
 * asks for and the invoice lacks is named by identifier and by name.
 *
 * <pre>{@code
 * InvoiceDraft draft = InvoiceBuilder.draft(Profile.EN16931);
 * draft.edit(invoice -> invoice.currencyCode("EUR").invoiceNumber("RE-2026-0211"));
 * SemanticDocument document = draft.build();
 * }</pre>
 */
public final class InvoiceDraft {

    private final InvoiceEditor editor;

    private final Building building;

    InvoiceDraft(Profile<?> profile) {
        this.editor = En16931.newInvoice();
        profile.fix(editor);
        this.building = new Building(editor, profile);
    }

    /**
     * Returns the typed editor of this draft, which has a setter per business term.
     *
     * @return the editor this draft writes through
     */
    public InvoiceEditor invoice() {
        return editor;
    }

    /**
     * Writes into the typed editor of this draft and returns the draft, so that writing
     * and finishing are one chain.
     *
     * @param block what to write
     * @return this draft
     * @throws NullPointerException if {@code block} is {@code null}
     */
    public InvoiceDraft edit(Consumer<InvoiceEditor> block) {
        Objects.requireNonNull(block, "block").accept(editor);
        return this;
    }

    /**
     * Derives the amounts this invoice adds up to with {@code Totals.STANDARD} and writes
     * them into it.
     *
     * @return this draft
     * @throws de.bsnsoft.esj.typed.DerivationException if the invoice does
     *                                                             not state what the
     *                                                             policy needs
     */
    public InvoiceDraft derive() {
        building.derive(Totals.STANDARD);
        return this;
    }

    /**
     * Derives the amounts this invoice adds up to and writes them into it.
     *
     * @param policy the derivation policy
     * @return this draft
     * @throws de.bsnsoft.esj.typed.DerivationException if the invoice does
     *                                                             not state what the
     *                                                             policy needs
     * @throws NullPointerException                                if {@code policy} is
     *                                                             {@code null}
     */
    public InvoiceDraft derive(Totals policy) {
        building.derive(policy);
        return this;
    }

    /**
     * Returns what the last derivation wrote and where it rounded.
     *
     * @return the report of the last derivation, or an empty optional where none has run
     */
    public Optional<DerivationReport> derivationReport() {
        return building.derivationReport();
    }

    /**
     * Names the registry the structural layers of every check of this draft measure the
     * invoice against. The default is the registry of the core model; an invoice that
     * carries terms of an extension registry names the core registry with that extension
     * loaded, so that those terms are checked instead of reported as not checked.
     *
     * @param registry the registry
     * @return this draft
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    public InvoiceDraft registry(Registry registry) {
        building.registry(registry);
        return this;
    }

    /**
     * Checks the invoice as it stands against the structural layers L2 and L3 of the
     * specification and against the profile this draft was opened with.
     *
     * @return what the check found
     */
    public BuildReport validate() {
        return building.validate(null);
    }

    /**
     * Checks the invoice as {@link #validate()} does and runs a set of business rules
     * over it as well.
     *
     * @param rules the business rules to run
     * @return what the check found
     * @throws NullPointerException if {@code rules} is {@code null}
     */
    public BuildReport validate(InvoiceRules rules) {
        return building.validate(Objects.requireNonNull(rules, "rules"));
    }

    /**
     * Checks the invoice as {@link #validate()} does and refuses where the check found
     * anything.
     *
     * @return this draft
     * @throws BuildException if the check found anything, carrying the report
     */
    public InvoiceDraft validateOrThrow() {
        building.validateOrThrow(null);
        return this;
    }

    /**
     * Checks the invoice as {@link #validate(InvoiceRules)} does and refuses where the
     * check found anything.
     *
     * @param rules the business rules to run
     * @return this draft
     * @throws BuildException       if the check found anything, carrying the report
     * @throws NullPointerException if {@code rules} is {@code null}
     */
    public InvoiceDraft validateOrThrow(InvoiceRules rules) {
        building.validateOrThrow(Objects.requireNonNull(rules, "rules"));
        return this;
    }

    /**
     * Returns the invoice as it has been written so far, without deriving and without
     * checking anything.
     *
     * @return an immutable document in canonical path order
     */
    public SemanticDocument document() {
        return building.document();
    }

    /**
     * Derives, checks and returns the invoice.
     *
     * @return an immutable document in canonical path order
     * @throws de.bsnsoft.esj.typed.DerivationException if the amounts cannot
     *                                                             be derived
     * @throws BuildException                                      if the check found
     *                                                             anything, carrying the
     *                                                             report
     */
    public SemanticDocument build() {
        return building.build(null);
    }

    /**
     * Derives, checks and returns the invoice, running a set of business rules as part of
     * the check.
     *
     * @param rules the business rules to run
     * @return an immutable document in canonical path order
     * @throws de.bsnsoft.esj.typed.DerivationException if the amounts cannot
     *                                                             be derived
     * @throws BuildException                                      if the check found
     *                                                             anything, carrying the
     *                                                             report
     * @throws NullPointerException                                if {@code rules} is
     *                                                             {@code null}
     */
    public SemanticDocument build(InvoiceRules rules) {
        return building.build(Objects.requireNonNull(rules, "rules"));
    }

    /**
     * Derives and checks the invoice as {@link #build(InvoiceRules)} does, and reports
     * instead of refusing.
     *
     * <p>The document is {@link #document()} either way: the finished invoice where the
     * report is {@link BuildReport#ok() ok}, and what had been written when the check
     * stopped where it is not.
     *
     * @param rules the business rules to run
     * @return what the check found
     * @throws de.bsnsoft.esj.typed.DerivationException if the amounts cannot
     *                                                             be derived
     * @throws NullPointerException                                if {@code rules} is
     *                                                             {@code null}
     */
    public BuildReport buildReport(InvoiceRules rules) {
        return building.buildReport(Objects.requireNonNull(rules, "rules"));
    }
}
