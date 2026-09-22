package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.invoice.Draft;
import de.bsnsoft.esj.invoice.Invoice;
import de.bsnsoft.esj.invoice.Rules;
import de.bsnsoft.esj.invoice.code.InvoiceType;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.typed.build.InvoiceRules;
import de.bsnsoft.esj.typed.build.Profile;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The domain API for a shop that prices with VAT included.
 *
 * <pre>{@code
 * GrossResult result = GrossInvoice.draft(Profile.EN16931)
 *         .edit(draft -> draft
 *                 .number("RE-2026-0731")
 *                 .issued(LocalDate.of(2026, 4, 14))
 *                 .currency(CurrencyCode.EUR)
 *                 .seller(seller)
 *                 .buyer(buyer)
 *                 .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000")))
 *         .line(GrossItem.perUnit(Line.of("Shower fitting SF-20")
 *                 .quantity(1, Unit.PIECE).vat(Vat.standard(19)), "99.99"))
 *         .displayedGrossTotal("99.99")
 *         .build(GrossAuthoring.GROSS_UNIT_AUTHORING);
 * }</pre>
 *
 * <p>It wraps {@link Invoice#draft(Profile)} and hands back what the domain API hands back:
 * the same {@code InvoiceResult} with the same {@code BuildReport}, and beside it the
 * {@link AuthoringReport} of the policy. {@link #edit(Consumer)} is the draft itself, so
 * everything the domain API can write is written here and nothing is reinvented.
 *
 * <p>The dependency points one way. {@code esj-invoice} knows nothing of this extension: an
 * application that prices net carries none of it, and this class is the only place where the
 * two meet.
 *
 * <p>{@link #build(GrossAuthoring)} runs the policy over the invoice, which writes the item
 * net prices, derives the line amounts, the VAT breakdown and the totals, and sets the invoice
 * rounding amount (BT-114) so that the invoice total with VAT comes to the gross total the
 * customer agreed to. The check of the domain API — the structural layers, the members the
 * profile asks for and the business rules of EN 16931-1 — then runs over the result, against
 * the core registry with this extension loaded, so that its four terms are checked like any
 * other.
 */
public final class GrossInvoice {

    private final Draft draft;

    private final InvoiceEditor editor;

    private GrossInvoice(Draft draft) {
        this.draft = draft.registry(B2c.registry());
        InvoiceEditor[] slot = new InvoiceEditor[1];
        draft.edit(invoice -> slot[0] = invoice);
        this.editor = slot[0];
    }

    /**
     * Opens a commercial invoice (BT-3 is 380) of a profile.
     *
     * @param profile the profile, for instance {@code Profile.EN16931}
     * @return the invoice
     * @throws NullPointerException if {@code profile} is {@code null}
     */
    public static GrossInvoice draft(Profile<?> profile) {
        return new GrossInvoice(Invoice.draft(profile));
    }

    /**
     * Opens a credit note (BT-3 is 381) of a profile.
     *
     * @param profile the profile, for instance {@code Profile.EN16931}
     * @return the credit note
     * @throws NullPointerException if {@code profile} is {@code null}
     */
    public static GrossInvoice creditNote(Profile<?> profile) {
        GrossInvoice invoice = new GrossInvoice(Invoice.draft(profile));
        invoice.draft.type(InvoiceType.CREDIT_NOTE);
        return invoice;
    }

    /**
     * Returns the draft underneath, which takes every member of the domain API.
     *
     * @return the draft
     */
    public Draft draft() {
        return draft;
    }

    /**
     * Writes into the draft and returns this invoice, so that the members of the domain API
     * and the gross lines stand in one chain.
     *
     * @param block what to write
     * @return this invoice
     * @throws NullPointerException if {@code block} is {@code null}
     */
    public GrossInvoice edit(Consumer<Draft> block) {
        Objects.requireNonNull(block, "block").accept(draft);
        return this;
    }

    /**
     * Writes one invoice line and the gross figures the customer was shown for it.
     *
     * @param item the line and its figures
     * @return this invoice
     * @throws NullPointerException if {@code item} is {@code null}
     */
    public GrossInvoice line(GrossItem item) {
        Objects.requireNonNull(item, "item");
        draft.line(item.line());
        Gross.Line line = Gross.on(editor).line(editor.invoiceLines().size() - 1);
        item.displayedGrossUnitPrice().ifPresent(line::displayedGrossUnitPrice);
        item.displayedGrossLineTotal().ifPresent(line::displayedGrossLineTotal);
        item.displayedLineVatAmount().ifPresent(line::displayedLineVatAmount);
        return this;
    }

    /**
     * Writes the displayed invoice gross total (BT-B2C-010): the total with VAT the customer
     * was shown or agreed to, which the policy carries the amount due for payment to.
     *
     * @param value the figure
     * @return this invoice
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public GrossInvoice displayedGrossTotal(BigDecimal value) {
        Gross.on(editor).displayedGrossTotal(Objects.requireNonNull(value, "value"));
        return this;
    }

    /**
     * Writes the displayed invoice gross total (BT-B2C-010) from its decimal spelling.
     *
     * @param value the figure
     * @return this invoice
     * @throws NumberFormatException if the text is no decimal number
     * @throws NullPointerException  if {@code value} is {@code null}
     */
    public GrossInvoice displayedGrossTotal(String value) {
        return displayedGrossTotal(new BigDecimal(Objects.requireNonNull(value, "value")));
    }

    /**
     * Returns the invoice as it has been written so far, without deriving and without
     * checking anything.
     *
     * @return an immutable document in canonical path order
     */
    public SemanticDocument document() {
        return draft.document();
    }

    /**
     * Derives the net invoice from the gross figures with a policy, checks it against the
     * business rules of EN 16931-1 and returns both reports.
     *
     * @param policy the gross authoring policy
     * @return the invoice, what the check found and what the policy did
     * @throws PolicyPreconditionException if the invoice does not state what the policy needs
     * @throws de.bsnsoft.esj.typed.DerivationException if the totals cannot be
     *                                                             derived
     * @throws NullPointerException        if {@code policy} is {@code null}
     */
    public GrossResult build(GrossAuthoring policy) {
        return build(policy, Rules.en16931());
    }

    /**
     * Derives the net invoice with a policy and checks it against a rule set of the caller's
     * choosing.
     *
     * @param policy the gross authoring policy
     * @param rules  the business rules to run
     * @return the invoice, what the check found and what the policy did
     * @throws PolicyPreconditionException if the invoice does not state what the policy needs
     * @throws de.bsnsoft.esj.typed.DerivationException if the totals cannot be
     *                                                             derived
     * @throws NullPointerException        if an argument is {@code null}
     */
    public GrossResult build(GrossAuthoring policy, InvoiceRules rules) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(rules, "rules");
        AuthoringReport authoring = policy.apply(editor.builder());
        return new GrossResult(draft.buildReport(rules), authoring);
    }

    @Override
    public String toString() {
        return "GrossInvoice[" + draft + "]";
    }
}
