package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.invoice.code.InvoiceType;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.typed.AllowanceEditor;
import de.bsnsoft.esj.typed.BuyerEditor;
import de.bsnsoft.esj.typed.ChargeEditor;
import de.bsnsoft.esj.typed.DerivationReport;
import de.bsnsoft.esj.typed.Identifier;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.typed.InvoiceLineEditor;
import de.bsnsoft.esj.typed.PayeeEditor;
import de.bsnsoft.esj.typed.SellerEditor;
import de.bsnsoft.esj.typed.Totals;
import de.bsnsoft.esj.typed.build.BuildReport;
import de.bsnsoft.esj.typed.build.InvoiceBuilder;
import de.bsnsoft.esj.typed.build.InvoiceDraft;
import de.bsnsoft.esj.typed.build.InvoiceRules;
import de.bsnsoft.esj.typed.build.Profile;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * An invoice written in the words of the domain, in any order.
 *
 * <p>It is what {@link Invoice#draft(Profile)} opens, and what
 * {@link Invoice#create(Profile)} writes through: the same methods, without the order the
 * step chain enforces, for a caller that maps from a model of its own and rarely has the
 * members in the order of EN 16931-1.
 *
 * <p>Everything it writes, it writes through the typed editor of {@code esj-typed} and
 * the constrained builder of {@code esj-typed.build}; {@link #edit(Consumer)} hands that
 * editor out for the terms the domain layer has no word for. Nothing is computed here
 * except the two amounts a percentage stands for and the payment due date a number of
 * days stands for; the line net amounts, the VAT breakdown and the totals are derived by
 * {@code Totals.STANDARD} when the invoice is built.
 *
 * <p>A group the model allows at most once — the seller (BG-4), the buyer (BG-7), the
 * payee (BG-10), the delivery information (BG-13), the invoicing period (BG-14) and the
 * payment instructions (BG-16) — is written once. A second write is refused naming the
 * group rather than merged into the first, because merging two parties gives one party
 * carrying both their identifiers and merging two payment instructions gives one carrying
 * both their accounts. {@link #edit(Consumer)} reaches past this guard, as it reaches past
 * everything else this layer decides.
 */
public final class Draft {

    private final InvoiceDraft delegate;

    private final InvoiceEditor invoice;

    private final Set<String> exemptions = new LinkedHashSet<>();

    private final Map<String, String> groups = new LinkedHashMap<>();

    private LocalDate issued;

    private Integer pendingDays;

    Draft(Profile<?> profile, Coded type) {
        this.delegate = InvoiceBuilder.draft(profile);
        this.invoice = delegate.invoice();
        this.invoice.typeCode(type.code());
    }

    /**
     * Writes the invoice number (BT-1).
     *
     * @param value the number the seller gives the invoice
     * @return this draft
     * @throws IllegalArgumentException if the number is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Draft number(String value) {
        invoice.invoiceNumber(Amounts.text(value, "value"));
        return this;
    }

    /**
     * Writes the issue date (BT-2), and with it the payment due date of payment terms
     * that were stated as a number of days.
     *
     * @param value the day the invoice was issued
     * @return this draft
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Draft issued(LocalDate value) {
        invoice.issueDate(Objects.requireNonNull(value, "value"));
        issued = value;
        applyPendingDueDate();
        return this;
    }

    /**
     * Writes the currency of the invoice (BT-5).
     *
     * @param value the currency, for example {@code CurrencyCode.EUR}
     * @return this draft
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Draft currency(Coded value) {
        invoice.currencyCode(Objects.requireNonNull(value, "value").code());
        return this;
    }

    /**
     * Writes the currency of the invoice (BT-5), a code of the generated list.
     *
     * @param value the currency
     * @return this draft
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Draft currency(CurrencyCode value) {
        return currency((Coded) value);
    }

    /**
     * Writes the invoice type code (BT-3), for a document type neither
     * {@link Invoice#create(Profile)} nor {@link Invoice#creditNote(Profile)} names.
     *
     * @param value the type code, a code of UNTDID 1001
     * @return this draft
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Draft type(Coded value) {
        invoice.typeCode(Objects.requireNonNull(value, "value").code());
        return this;
    }

    /**
     * Writes the invoice type code (BT-3), a code of the generated list.
     *
     * @param value the type code
     * @return this draft
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Draft type(InvoiceType value) {
        return type((Coded) value);
    }

    /**
     * Writes the seller (BG-4) with its address (BG-5) and its contact point (BG-6).
     *
     * <p>An invoice has one seller: a second call is refused, not merged into the first.
     *
     * @param party the seller
     * @return this draft
     * @throws IllegalStateException if a seller has already been written
     * @throws NullPointerException  if {@code party} is {@code null}
     */
    public Draft seller(Party party) {
        Objects.requireNonNull(party, "party");
        once("BG-4", "SELLER");
        SellerEditor seller = invoice.seller();
        seller.name(party.name());
        party.tradingName().ifPresent(seller::tradingName);
        for (Identifier identifier : party.identifiers()) {
            seller.identifiers().add(identifier);
        }
        party.legalRegistration().ifPresent(seller::legalRegistrationIdentifier);
        party.vatId().ifPresent(seller::vatIdentifier);
        party.taxRegistration().ifPresent(seller::taxRegistrationIdentifier);
        party.additionalLegalInformation().ifPresent(seller::additionalLegalInformation);
        party.electronicAddress().ifPresent(address ->
                seller.electronicAddress(address.value(), address.scheme().code()));
        party.address().ifPresent(address -> seller.postalAddress(a -> {
            a.countryCode(address.country().code());
            address.line1().ifPresent(a::addressLine1);
            address.line2().ifPresent(a::addressLine2);
            address.line3().ifPresent(a::addressLine3);
            address.city().ifPresent(a::city);
            address.postCode().ifPresent(a::postCode);
            address.countrySubdivision().ifPresent(a::countrySubdivision);
        }));
        party.contact().ifPresent(contact -> seller.contact(c -> {
            contact.name().ifPresent(c::name);
            contact.telephone().ifPresent(c::telephone);
            contact.email().ifPresent(c::email);
        }));
        return this;
    }

    /**
     * Writes the buyer (BG-7) with its address (BG-8) and its contact point (BG-9).
     *
     * @param party the buyer
     * @return this draft
     * @throws IllegalArgumentException if the party states more than one identifier, a
     *                                  tax registration identifier (BT-32) or additional
     *                                  legal information (BT-33), which the buyer has no
     *                                  term for
     * @throws IllegalStateException    if a buyer has already been written
     * @throws NullPointerException     if {@code party} is {@code null}
     */
    public Draft buyer(Party party) {
        Objects.requireNonNull(party, "party");
        once("BG-7", "BUYER");
        refuse(party.identifiers().size() > 1, "the buyer has one identifier (BT-46) and this"
                + " party states " + party.identifiers().size());
        refuse(party.taxRegistration().isPresent(), "a tax registration identifier (BT-32) is a"
                + " term of the seller, and the buyer has none");
        refuse(party.additionalLegalInformation().isPresent(), "additional legal information"
                + " (BT-33) is a term of the seller, and the buyer has none");
        BuyerEditor buyer = invoice.buyer();
        buyer.name(party.name());
        party.tradingName().ifPresent(buyer::tradingName);
        for (Identifier identifier : party.identifiers()) {
            buyer.identifier(identifier);
        }
        party.legalRegistration().ifPresent(buyer::legalRegistrationIdentifier);
        party.vatId().ifPresent(buyer::vatIdentifier);
        party.electronicAddress().ifPresent(address ->
                buyer.electronicAddress(address.value(), address.scheme().code()));
        party.address().ifPresent(address -> buyer.postalAddress(a -> {
            a.countryCode(address.country().code());
            address.line1().ifPresent(a::addressLine1);
            address.line2().ifPresent(a::addressLine2);
            address.line3().ifPresent(a::addressLine3);
            address.city().ifPresent(a::city);
            address.postCode().ifPresent(a::postCode);
            address.countrySubdivision().ifPresent(a::countrySubdivision);
        }));
        party.contact().ifPresent(contact -> buyer.contact(c -> {
            contact.name().ifPresent(c::name);
            contact.telephone().ifPresent(c::telephone);
            contact.email().ifPresent(c::email);
        }));
        return this;
    }

    /**
     * Writes the payee (BG-10), the party to be paid where that is not the seller.
     *
     * @param party the payee; it has a name (BT-59), one identifier (BT-60) and a legal
     *              registration identifier (BT-61) and nothing else
     * @return this draft
     * @throws IllegalArgumentException if the party states a term the payee has none of
     * @throws IllegalStateException    if a payee has already been written
     * @throws NullPointerException     if {@code party} is {@code null}
     */
    public Draft payee(Party party) {
        Objects.requireNonNull(party, "party");
        once("BG-10", "PAYEE");
        refuse(party.identifiers().size() > 1, "the payee has one identifier (BT-60) and this party"
                + " states " + party.identifiers().size());
        refuse(party.vatId().isPresent(), "the payee has no VAT identifier (BT-31, BT-48)");
        refuse(party.taxRegistration().isPresent(),
                "the payee has no tax registration identifier (BT-32)");
        refuse(party.additionalLegalInformation().isPresent(),
                "the payee has no additional legal information (BT-33)");
        refuse(party.tradingName().isPresent(), "the payee has no trading name (BT-28, BT-45)");
        refuse(party.electronicAddress().isPresent(),
                "the payee has no electronic address (BT-34, BT-49)");
        refuse(party.contact().isPresent(), "the payee has no contact point (BG-6, BG-9)");
        refuse(party.address().isPresent(), "the payee has no postal address (BG-5, BG-8)");
        PayeeEditor payee = invoice.payee();
        payee.name(party.name());
        for (Identifier identifier : party.identifiers()) {
            payee.identifier(identifier);
        }
        party.legalRegistration().ifPresent(payee::legalRegistrationIdentifier);
        return this;
    }

    /**
     * Writes the payment instructions (BG-16).
     *
     * <p>An invoice has one set of payment instructions: a second call is refused, not
     * merged into the first.
     *
     * @param means how the invoice is to be paid
     * @return this draft
     * @throws IllegalStateException if payment instructions have already been written
     * @throws NullPointerException  if {@code means} is {@code null}
     */
    public Draft payment(PaymentMeans means) {
        Objects.requireNonNull(means, "means");
        once("BG-16", "PAYMENT INSTRUCTIONS");
        invoice.paymentInstructions(p -> {
            p.paymentMeansTypeCode(means.code().code());
            means.text().ifPresent(p::paymentMeansText);
            means.remittanceInformation().ifPresent(p::remittanceInformation);
            means.account().ifPresent(account -> p.creditTransfer(t -> {
                t.accountIdentifier(account);
                means.accountName().ifPresent(t::accountName);
                means.serviceProvider().ifPresent(t::serviceProviderIdentifier);
            }));
            means.cardNumber().ifPresent(number -> p.card(c -> {
                c.primaryAccountNumber(number);
                means.cardHolder().ifPresent(c::holderName);
            }));
            if (means.mandateReference().isPresent() || means.creditorIdentifier().isPresent()
                    || means.debitedAccount().isPresent()) {
                p.directDebit(d -> {
                    means.mandateReference().ifPresent(d::mandateReferenceIdentifier);
                    means.creditorIdentifier().ifPresent(d::bankAssignedCreditorIdentifier);
                    means.debitedAccount().ifPresent(d::debitedAccountIdentifier);
                });
            }
        });
        return this;
    }

    /**
     * Writes the payment instructions (BG-16) and the payment terms (BT-9, BT-20).
     *
     * @param means how the invoice is to be paid
     * @param terms when it is to be paid
     * @return this draft
     * @throws NullPointerException if a part is {@code null}
     */
    public Draft payment(PaymentMeans means, PaymentTerms terms) {
        return payment(means).paymentTerms(terms);
    }

    /**
     * Writes the payment terms: the payment due date (BT-9) and the terms in words
     * (BT-20).
     *
     * <p>Terms stated as a number of days are counted from the issue date (BT-2). Where
     * the issue date is not written yet, the due date is written as soon as it is.
     *
     * @param terms when the invoice is to be paid
     * @return this draft
     * @throws NullPointerException if {@code terms} is {@code null}
     */
    public Draft paymentTerms(PaymentTerms terms) {
        Objects.requireNonNull(terms, "terms");
        terms.text().ifPresent(invoice::paymentTerms);
        if (terms.dueDate().isPresent()) {
            invoice.paymentDueDate(terms.dueDate().get());
            pendingDays = null;
        } else if (terms.days().isPresent()) {
            pendingDays = terms.days().get();
            applyPendingDueDate();
        }
        return this;
    }

    /**
     * Writes one invoice line (BG-25), numbered by its position where it states no
     * identifier of its own.
     *
     * <p>The number is one more than the number of lines the invoice already carries, so
     * that a line written through {@link #edit(Consumer)} is counted too. An identifier
     * (BT-126) two lines share is named when the invoice is built.
     *
     * @param line the line
     * @return this draft
     * @throws IllegalArgumentException if a percentage on the line has no base amount and
     *                                  none can be taken from the line
     * @throws NullPointerException     if {@code line} is {@code null}
     */
    public Draft line(Line line) {
        Line numbered = Objects.requireNonNull(line, "line")
                .numbered(invoice.invoiceLines().size() + 1);
        invoice.invoiceLine(editor -> writeLine(editor, numbered));
        numbered.vat().ifPresent(this::rememberExemption);
        return this;
    }

    /**
     * Writes one document level allowance (BG-20).
     *
     * @param allowance the allowance; it states the VAT it is taxed under (BT-95, BT-96)
     * @return this draft
     * @throws IllegalArgumentException if the allowance states no VAT, or a percentage
     *                                  without a base amount
     * @throws NullPointerException     if {@code allowance} is {@code null}
     */
    public Draft allowance(Allowance allowance) {
        Objects.requireNonNull(allowance, "allowance");
        Vat vat = allowance.vat().orElseThrow(() -> new IllegalArgumentException(
                "a document level allowance (BG-20) states the VAT category code (BT-95) it is"
                        + " taxed under; Allowance.vat(...) writes it"));
        BigDecimal amount = amountOf(allowance.amount().orElse(null),
                allowance.percentage().orElse(null), allowance.base().orElse(null),
                "document level allowance", "BT-93");
        invoice.documentLevelAllowance(a -> {
            a.amount(amount);
            allowance.base().ifPresent(a::baseAmount);
            allowance.percentage().ifPresent(a::percentage);
            a.vatCategoryCode(vat.category().code());
            vat.rate().ifPresent(a::vatRate);
            allowance.reason().ifPresent(a::reason);
            allowance.reasonCode().ifPresent(code -> a.reasonCode(code.code()));
        });
        rememberExemption(vat);
        return this;
    }

    /**
     * Writes one document level charge (BG-21).
     *
     * @param charge the charge; it states the VAT it is taxed under (BT-102, BT-103)
     * @return this draft
     * @throws IllegalArgumentException if the charge states no VAT, or a percentage
     *                                  without a base amount
     * @throws NullPointerException     if {@code charge} is {@code null}
     */
    public Draft charge(Charge charge) {
        Objects.requireNonNull(charge, "charge");
        Vat vat = charge.vat().orElseThrow(() -> new IllegalArgumentException(
                "a document level charge (BG-21) states the VAT category code (BT-102) it is taxed"
                        + " under; Charge.vat(...) writes it"));
        BigDecimal amount = amountOf(charge.amount().orElse(null),
                charge.percentage().orElse(null), charge.base().orElse(null),
                "document level charge", "BT-100");
        invoice.documentLevelCharge(c -> {
            c.amount(amount);
            charge.base().ifPresent(c::baseAmount);
            charge.percentage().ifPresent(c::percentage);
            c.vatCategoryCode(vat.category().code());
            vat.rate().ifPresent(c::vatRate);
            charge.reason().ifPresent(c::reason);
            charge.reasonCode().ifPresent(code -> c.reasonCode(code.code()));
        });
        rememberExemption(vat);
        return this;
    }

    /**
     * Writes one invoice note (BG-1, BT-22).
     *
     * @param text the note
     * @return this draft
     * @throws IllegalArgumentException if the note is blank
     * @throws NullPointerException     if {@code text} is {@code null}
     */
    public Draft note(String text) {
        invoice.note(n -> n.note(Amounts.text(text, "text")));
        return this;
    }

    /**
     * Writes the buyer reference (BT-10), the value the buyer asks to find on the
     * invoice; XRechnung requires it.
     *
     * @param value the reference
     * @return this draft
     * @throws IllegalArgumentException if the reference is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Draft buyerReference(String value) {
        invoice.buyerReference(Amounts.text(value, "value"));
        return this;
    }

    /**
     * Writes the purchase order reference (BT-13).
     *
     * @param value the reference
     * @return this draft
     * @throws IllegalArgumentException if the reference is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Draft purchaseOrderReference(String value) {
        invoice.purchaseOrderReference(Amounts.text(value, "value"));
        return this;
    }

    /**
     * Writes the contract reference (BT-12).
     *
     * @param value the reference
     * @return this draft
     * @throws IllegalArgumentException if the reference is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Draft contractReference(String value) {
        invoice.contractReference(Amounts.text(value, "value"));
        return this;
    }

    /**
     * Writes the project reference (BT-11).
     *
     * @param value the reference
     * @return this draft
     * @throws IllegalArgumentException if the reference is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Draft projectReference(String value) {
        invoice.projectReference(Amounts.text(value, "value"));
        return this;
    }

    /**
     * Writes one preceding invoice reference (BG-3): the number of the invoice this
     * document corrects or refers to (BT-25).
     *
     * <p>A credit note names the invoice it credits here. The group is repeatable, so a
     * document that settles several invoices calls this once per invoice.
     *
     * @param number the number of the preceding invoice
     * @return this draft
     * @throws IllegalArgumentException if the number is blank
     * @throws NullPointerException     if {@code number} is {@code null}
     */
    public Draft precedingInvoice(String number) {
        return precedingInvoice(number, null);
    }

    /**
     * Writes one preceding invoice reference (BG-3) with the day that invoice was issued
     * (BT-25, BT-26), which EN 16931-1 asks for where the number alone is ambiguous.
     *
     * @param number the number of the preceding invoice
     * @param issued the day it was issued, or {@code null}
     * @return this draft
     * @throws IllegalArgumentException if the number is blank
     * @throws NullPointerException     if {@code number} is {@code null}
     */
    public Draft precedingInvoice(String number, LocalDate issued) {
        String reference = Amounts.text(number, "number");
        invoice.precedingInvoiceReference(r -> {
            r.reference(reference);
            if (issued != null) {
                r.issueDate(issued);
            }
        });
        return this;
    }

    /**
     * Writes the amount already paid (BT-113), which the derivation subtracts from the
     * total with VAT to arrive at the amount due for payment (BT-115).
     *
     * @param value the amount paid, in the currency of the invoice
     * @return this draft
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Draft paidAmount(BigDecimal value) {
        invoice.documentTotals(t -> t.paidAmount(Objects.requireNonNull(value, "value")));
        return this;
    }

    /**
     * Writes the rounding amount (BT-114), which the derivation adds to the total with VAT
     * to arrive at the amount due for payment (BT-115).
     *
     * @param value the amount rounded up or down, in the currency of the invoice
     * @return this draft
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Draft roundingAmount(BigDecimal value) {
        invoice.documentTotals(t -> t.roundingAmount(Objects.requireNonNull(value, "value")));
        return this;
    }

    /**
     * Writes the delivery information (BG-13) with its address (BG-15).
     *
     * @param delivery where and when what is invoiced was delivered
     * @return this draft
     * @throws IllegalStateException if delivery information has already been written
     * @throws NullPointerException  if {@code delivery} is {@code null}
     */
    public Draft delivery(Delivery delivery) {
        Objects.requireNonNull(delivery, "delivery");
        once("BG-13", "DELIVERY INFORMATION");
        invoice.delivery(d -> {
            delivery.partyName().ifPresent(d::partyName);
            delivery.location().ifPresent(d::locationIdentifier);
            delivery.date().ifPresent(d::actualDeliveryDate);
            delivery.address().ifPresent(address -> d.address(a -> {
                a.countryCode(address.country().code());
                address.line1().ifPresent(a::addressLine1);
                address.line2().ifPresent(a::addressLine2);
                address.line3().ifPresent(a::addressLine3);
                address.city().ifPresent(a::city);
                address.postCode().ifPresent(a::postCode);
                address.countrySubdivision().ifPresent(a::countrySubdivision);
            }));
        });
        return this;
    }

    /**
     * Writes the invoicing period (BG-14, BT-73 and BT-74), which sits inside the
     * delivery information.
     *
     * @param period the period the invoice is for
     * @return this draft
     * @throws IllegalStateException if an invoicing period has already been written
     * @throws NullPointerException  if {@code period} is {@code null}
     */
    public Draft period(BillingPeriod period) {
        Objects.requireNonNull(period, "period");
        once("BG-14", "INVOICING PERIOD");
        invoice.delivery(d -> d.invoicingPeriod(p -> {
            period.start().ifPresent(p::startDate);
            period.end().ifPresent(p::endDate);
        }));
        return this;
    }

    /**
     * Writes one additional supporting document (BG-24).
     *
     * @param attachment the document
     * @return this draft
     * @throws NullPointerException if {@code attachment} is {@code null}
     */
    public Draft attachment(Attachment attachment) {
        Objects.requireNonNull(attachment, "attachment");
        invoice.additionalSupportingDocument(d -> {
            d.reference(attachment.reference());
            attachment.description().ifPresent(d::description);
            attachment.location().ifPresent(d::externalLocation);
            attachment.content().ifPresent(bytes -> d.attachment(bytes,
                    attachment.mimeCode().orElseThrow(), attachment.filename().orElseThrow()));
        });
        return this;
    }

    /**
     * Hands out the typed editor for the terms the domain layer has no word for.
     *
     * <p>The domain API is a convenience over the editor and not a second model: every
     * effect of it is expressible through the editor, and so is everything it leaves out.
     *
     * @param block what to write
     * @return this draft
     * @throws NullPointerException if {@code block} is {@code null}
     */
    public Draft edit(Consumer<InvoiceEditor> block) {
        Objects.requireNonNull(block, "block").accept(invoice);
        return this;
    }

    /**
     * Derives the line net amounts, the VAT breakdown and the totals with
     * {@code Totals.STANDARD}.
     *
     * @return this draft
     * @throws de.bsnsoft.esj.typed.DerivationException if the invoice does
     *                                                             not state what the
     *                                                             policy needs
     */
    public Draft derive() {
        delegate.derive(Totals.STANDARD);
        return this;
    }

    /**
     * Derives the amounts with a policy of the caller's choosing.
     *
     * @param policy the derivation policy
     * @return this draft
     * @throws de.bsnsoft.esj.typed.DerivationException if the invoice does
     *                                                             not state what the
     *                                                             policy needs
     * @throws NullPointerException                                if {@code policy} is
     *                                                             {@code null}
     */
    public Draft derive(Totals policy) {
        delegate.derive(policy);
        return this;
    }

    /**
     * Returns what the last derivation wrote and where it rounded.
     *
     * @return the report of the last derivation, or an empty optional where none has run
     */
    public Optional<DerivationReport> derivationReport() {
        return delegate.derivationReport();
    }

    /**
     * Checks the invoice as it stands: the structural layers L2 and L3, the members the
     * model and the profile ask for, and the business rules of EN 16931-1.
     *
     * @return what the check found
     */
    public BuildReport validate() {
        return delegate.validate(Rules.en16931());
    }

    /**
     * Checks the invoice as {@link #validate()} does, with a rule set of the caller's
     * choosing.
     *
     * @param rules the business rules to run
     * @return what the check found
     * @throws NullPointerException if {@code rules} is {@code null}
     */
    public BuildReport validate(InvoiceRules rules) {
        return delegate.validate(rules);
    }

    /**
     * Returns the invoice as it has been written so far, without deriving and without
     * checking anything.
     *
     * @return an immutable document in canonical path order
     */
    public SemanticDocument document() {
        return delegate.document();
    }

    /**
     * Derives the amounts, checks the invoice and returns it.
     *
     * @return an immutable document in canonical path order
     * @throws IllegalStateException if payment terms were stated as a number of days and
     *                               no issue date (BT-2) was written for them to count
     *                               from, or if two invoice lines carry the same
     *                               identifier (BT-126)
     * @throws de.bsnsoft.esj.typed.DerivationException if the amounts cannot
     *                                                             be derived
     * @throws de.bsnsoft.esj.typed.build.BuildException if the check found
     *                                                              anything
     */
    public SemanticDocument build() {
        return build(Rules.en16931());
    }

    /**
     * Derives the amounts, checks the invoice against a rule set of the caller's choosing
     * and returns it.
     *
     * @param rules the business rules to run
     * @return an immutable document in canonical path order
     * @throws IllegalStateException if payment terms were stated as a number of days and
     *                               no issue date (BT-2) was written for them to count
     *                               from, or if two invoice lines carry the same
     *                               identifier (BT-126)
     * @throws de.bsnsoft.esj.typed.DerivationException if the amounts cannot
     *                                                             be derived
     * @throws de.bsnsoft.esj.typed.build.BuildException if the check found
     *                                                              anything
     * @throws NullPointerException  if {@code rules} is {@code null}
     */
    public SemanticDocument build(InvoiceRules rules) {
        Objects.requireNonNull(rules, "rules");
        finish();
        return delegate.build(rules);
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
    public Draft registry(Registry registry) {
        delegate.registry(registry);
        return this;
    }

    /**
     * Derives the amounts and checks the invoice without refusing it.
     *
     * @return the document and what the check found
     * @throws IllegalStateException if payment terms were stated as a number of days and
     *                               no issue date (BT-2) was written for them to count
     *                               from, or if two invoice lines carry the same
     *                               identifier (BT-126)
     * @throws de.bsnsoft.esj.typed.DerivationException if the amounts cannot
     *                                                             be derived
     */
    public InvoiceResult buildReport() {
        return buildReport(Rules.en16931());
    }

    /**
     * Derives the amounts and checks the invoice against a rule set of the caller's
     * choosing, without refusing it.
     *
     * @param rules the business rules to run
     * @return the document and what the check found
     * @throws IllegalStateException if payment terms were stated as a number of days and
     *                               no issue date (BT-2) was written for them to count
     *                               from, or if two invoice lines carry the same
     *                               identifier (BT-126)
     * @throws de.bsnsoft.esj.typed.DerivationException if the amounts cannot
     *                                                             be derived
     * @throws NullPointerException  if {@code rules} is {@code null}
     */
    public InvoiceResult buildReport(InvoiceRules rules) {
        Objects.requireNonNull(rules, "rules");
        finish();
        BuildReport report = delegate.buildReport(rules);
        return new InvoiceResult(delegate.document(), report);
    }

    private void finish() {
        if (pendingDays != null) {
            throw new IllegalStateException("the payment terms fall due " + pendingDays + " days"
                    + " after the issue date (BT-2), and this invoice states no issue date to"
                    + " count from");
        }
        refuseDuplicateLineIdentifiers();
    }

    /**
     * Refuses two invoice lines under one identifier. BT-126 is unique within the invoice
     * by its definition, no business rule of the pack says so, and this layer is the one
     * place that sees every line it wrote and every line written past it.
     */
    private void refuseDuplicateLineIdentifiers() {
        SemanticDocument written = delegate.document();
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < invoice.invoiceLines().size(); index++) {
            Optional<String> identifier = written
                    .value(SemanticPath.of("/BG-25/" + index + "/BT-126"))
                    .map(SemanticValue::canonicalContent);
            if (identifier.isPresent() && !seen.add(identifier.orElseThrow())) {
                throw new IllegalStateException("invoice line " + (index + 1) + " carries the"
                        + " invoice line identifier (BT-126) " + identifier.orElseThrow()
                        + ", which an earlier line of this invoice already carries; BT-126 is"
                        + " unique within the invoice");
            }
        }
    }

    /**
     * Records that a group the model allows at most once has been written, and refuses the
     * second write naming the group.
     */
    private void once(String group, String name) {
        if (groups.put(group, name) != null) {
            throw new IllegalStateException(group + " " + name + " occurs at most once in an"
                    + " invoice and has already been written through this draft; a second one is"
                    + " refused rather than merged into the first");
        }
    }

    private void applyPendingDueDate() {
        if (pendingDays != null && issued != null) {
            invoice.paymentDueDate(issued.plusDays(pendingDays));
            pendingDays = null;
        }
    }

    private void writeLine(InvoiceLineEditor editor, Line line) {
        line.identifier().ifPresent(editor::identifier);
        line.note().ifPresent(editor::note);
        line.objectIdentifier().ifPresent(editor::objectIdentifier);
        line.purchaseOrderLineReference().ifPresent(editor::purchaseOrderLineReference);
        line.quantity().ifPresent(editor::quantity);
        line.unit().ifPresent(unit -> editor.quantityUnitCode(unit.code()));
        if (line.unitPrice().isPresent() || line.baseQuantity().isPresent()) {
            editor.price(p -> {
                line.unitPrice().ifPresent(p::netPrice);
                line.baseQuantity().ifPresent(p::baseQuantity);
                line.baseQuantityUnit().ifPresent(unit -> p.baseQuantityUnitCode(unit.code()));
            });
        }
        line.vat().ifPresent(vat -> editor.vat(v -> {
            v.vatCategoryCode(vat.category().code());
            vat.rate().ifPresent(v::vatRate);
        }));
        editor.item(i -> {
            i.name(line.name());
            line.description().ifPresent(i::description);
            line.sellerIdentifier().ifPresent(i::sellerIdentifier);
            line.buyerIdentifier().ifPresent(i::buyerIdentifier);
        });
        line.period().ifPresent(period -> editor.period(p -> {
            period.start().ifPresent(p::startDate);
            period.end().ifPresent(p::endDate);
        }));
        for (Allowance allowance : line.allowances()) {
            BigDecimal base = allowance.base().or(line::netBase).orElse(null);
            BigDecimal amount = amountOf(allowance.amount().orElse(null),
                    allowance.percentage().orElse(null), base, "invoice line allowance", "BT-137");
            editor.allowance(a -> writeAdjustment(a, amount, base, allowance));
        }
        for (Charge charge : line.charges()) {
            BigDecimal base = charge.base().or(line::netBase).orElse(null);
            BigDecimal amount = amountOf(charge.amount().orElse(null),
                    charge.percentage().orElse(null), base, "invoice line charge", "BT-142");
            editor.charge(c -> writeAdjustment(c, amount, base, charge));
        }
    }

    private static void writeAdjustment(AllowanceEditor editor, BigDecimal amount, BigDecimal base,
                                        Allowance allowance) {
        editor.amount(amount);
        if (allowance.percentage().isPresent()) {
            editor.baseAmount(base);
            editor.percentage(allowance.percentage().orElseThrow());
        } else {
            allowance.base().ifPresent(editor::baseAmount);
        }
        allowance.reason().ifPresent(editor::reason);
        allowance.reasonCode().ifPresent(code -> editor.reasonCode(code.code()));
    }

    private static void writeAdjustment(ChargeEditor editor, BigDecimal amount, BigDecimal base,
                                        Charge charge) {
        editor.amount(amount);
        if (charge.percentage().isPresent()) {
            editor.baseAmount(base);
            editor.percentage(charge.percentage().orElseThrow());
        } else {
            charge.base().ifPresent(editor::baseAmount);
        }
        charge.reason().ifPresent(editor::reason);
        charge.reasonCode().ifPresent(code -> editor.reasonCode(code.code()));
    }

    /**
     * The amount an allowance or a charge deducts or adds: the one it states, or the one
     * its percentage of the base amount comes to, rounded half up to two decimals once,
     * because the result is of the semantic data type Amount.
     */
    private static BigDecimal amountOf(BigDecimal amount, BigDecimal percentage, BigDecimal base,
                                       String what, String baseTerm) {
        if (amount != null) {
            return amount;
        }
        if (base == null) {
            throw new IllegalArgumentException("the " + what + " is " + percentage.toPlainString()
                    + " per cent of a base amount (" + baseTerm + ") that neither it nor its line"
                    + " states; base(...) writes one");
        }
        return base.multiply(percentage)
                .divide(BigDecimal.valueOf(100), Amounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Writes the VAT exemption reason of a category into the VAT breakdown (BG-23), where
     * the derivation finds it again and keeps it: BT-120 and BT-121 are the one thing in
     * a breakdown that cannot be computed.
     */
    private void rememberExemption(Vat vat) {
        if (vat.exemptionReasonCode().isEmpty() && vat.exemptionReasonText().isEmpty()) {
            return;
        }
        if (!exemptions.add(vat.key())) {
            return;
        }
        invoice.vatBreakdown(b -> {
            b.vatCategoryCode(vat.category().code());
            vat.rate().ifPresent(b::vatRate);
            vat.exemptionReasonText().ifPresent(b::exemptionReasonText);
            vat.exemptionReasonCode().ifPresent(code -> b.exemptionReasonCode(code.code()));
        });
    }

    private static void refuse(boolean wrong, String message) {
        if (wrong) {
            throw new IllegalArgumentException(message);
        }
    }
}
