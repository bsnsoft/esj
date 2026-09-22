package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.typed.build.BuildReport;
import de.bsnsoft.esj.typed.build.InvoiceRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * The one implementation of {@link InvoiceSteps}: it writes into a {@link Draft} and
 * returns whichever step of the chain the member it just wrote leads to.
 *
 * <p>The steps are interfaces so that the compiler can hide what an invoice does not
 * state yet; the invoice itself is the draft, and both entries of {@link Invoice} produce
 * the same document.
 */
final class InvoiceBuild implements InvoiceSteps.Start, InvoiceSteps.WithNumber,
        InvoiceSteps.WithIssueDate, InvoiceSteps.WithCurrency, InvoiceSteps.WithSeller,
        InvoiceSteps.Buildable {

    private final Draft draft;

    InvoiceBuild(Draft draft) {
        this.draft = draft;
    }

    @Override
    public InvoiceSteps.WithNumber number(String value) {
        draft.number(value);
        return this;
    }

    @Override
    public InvoiceSteps.WithIssueDate issued(LocalDate value) {
        draft.issued(value);
        return this;
    }

    @Override
    public InvoiceSteps.WithCurrency currency(Coded value) {
        draft.currency(value);
        return this;
    }

    @Override
    public InvoiceSteps.WithCurrency currency(CurrencyCode value) {
        draft.currency(value);
        return this;
    }

    @Override
    public InvoiceSteps.WithSeller seller(Party party) {
        draft.seller(party);
        return this;
    }

    @Override
    public InvoiceSteps.WithBuyer buyer(Party party) {
        draft.buyer(party);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable line(Line line) {
        draft.line(line);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable payment(PaymentMeans means) {
        draft.payment(means);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable payment(PaymentMeans means, PaymentTerms terms) {
        draft.payment(means, terms);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable paymentTerms(PaymentTerms terms) {
        draft.paymentTerms(terms);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable allowance(Allowance allowance) {
        draft.allowance(allowance);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable charge(Charge charge) {
        draft.charge(charge);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable note(String text) {
        draft.note(text);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable buyerReference(String value) {
        draft.buyerReference(value);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable purchaseOrderReference(String value) {
        draft.purchaseOrderReference(value);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable contractReference(String value) {
        draft.contractReference(value);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable projectReference(String value) {
        draft.projectReference(value);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable precedingInvoice(String number) {
        draft.precedingInvoice(number);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable precedingInvoice(String number, LocalDate issued) {
        draft.precedingInvoice(number, issued);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable paidAmount(BigDecimal value) {
        draft.paidAmount(value);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable roundingAmount(BigDecimal value) {
        draft.roundingAmount(value);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable payee(Party party) {
        draft.payee(party);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable delivery(Delivery delivery) {
        draft.delivery(delivery);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable period(BillingPeriod period) {
        draft.period(period);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable attachment(Attachment attachment) {
        draft.attachment(attachment);
        return this;
    }

    @Override
    public InvoiceSteps.Buildable edit(Consumer<InvoiceEditor> block) {
        draft.edit(block);
        return this;
    }

    @Override
    public Draft draft() {
        return draft;
    }

    @Override
    public SemanticDocument document() {
        return draft.document();
    }

    @Override
    public BuildReport validate() {
        return draft.validate();
    }

    @Override
    public BuildReport validate(InvoiceRules rules) {
        return draft.validate(rules);
    }

    @Override
    public SemanticDocument build() {
        return draft.build();
    }

    @Override
    public SemanticDocument build(InvoiceRules rules) {
        return draft.build(rules);
    }

    @Override
    public InvoiceResult buildReport() {
        return draft.buildReport();
    }

    @Override
    public InvoiceResult buildReport(InvoiceRules rules) {
        return draft.buildReport(rules);
    }
}
