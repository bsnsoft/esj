package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.invoice.code.AllowanceReason;
import de.bsnsoft.esj.invoice.code.ChargeReason;
import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.Country;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.invoice.code.ElectronicAddressScheme;
import de.bsnsoft.esj.invoice.code.InvoiceType;
import de.bsnsoft.esj.invoice.code.PaymentMeansCode;
import de.bsnsoft.esj.invoice.code.Unit;
import de.bsnsoft.esj.invoice.code.VatCategory;
import de.bsnsoft.esj.invoice.code.VatExemptionReason;
import de.bsnsoft.esj.typed.En16931;
import de.bsnsoft.esj.typed.Identifier;
import de.bsnsoft.esj.typed.build.Profile;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An invoice in the words of the domain: the way into the domain API, and the way an
 * invoice read back from a document looks.
 *
 * <p>{@link #create(Profile)} opens the step chain, in which the compiler asks for what
 * EN 16931-1 asks of every invoice:
 *
 * <pre>{@code
 * SemanticDocument document = Invoice.create(Profile.EN16931)
 *     .number("RE-2026-0211").issued(LocalDate.of(2026, 5, 12)).currency(CurrencyCode.EUR)
 *     .seller(Party.named("Example GmbH").vatId("DE123456789")
 *         .address("Musterweg 12", "10117", "Beispielstadt", Country.DE))
 *     .buyer(Party.named("Muster AG").address("Beispielallee 3", "20095", "Musterstadt",
 *         Country.DE))
 *     .payment(PaymentMeans.sepaCreditTransfer("DE89370400440532013000"),
 *              PaymentTerms.days(30, "Payable within 30 days without deduction."))
 *     .line(Line.of("Sensor module SM-100").quantity(100, Unit.PIECE).unitPrice("12")
 *         .vat(Vat.standard(19)))
 *     .build();
 * }</pre>
 *
 * <p>{@link #draft(Profile)} writes the same invoice in any order, for a caller mapping
 * from a model of its own. {@link #creditNote(Profile)} is the step chain with the
 * invoice type code 381. All three write through the typed editor of {@code esj-typed}
 * and the constrained builder over it, and {@code build()} derives the amounts, checks
 * the document and the business rules of EN 16931-1, and returns the document or refuses
 * naming what it found.
 *
 * <p>{@link #read(SemanticDocument)} is the other direction: the same words over a
 * document that already exists, whichever syntax it was imported from. A code the snapshot
 * this release was generated from does not carry comes back as a custom code rather than
 * as nothing.
 */
public final class Invoice {

    /** The invoice type code of a commercial invoice. */
    private static final Coded COMMERCIAL_INVOICE = InvoiceType.COMMERCIAL_INVOICE;

    /** The invoice type code of a credit note. */
    private static final Coded CREDIT_NOTE = InvoiceType.CREDIT_NOTE;

    private final SemanticDocument document;

    private final de.bsnsoft.esj.typed.Invoice view;

    private Invoice(SemanticDocument document) {
        this.document = document;
        this.view = En16931.view(document);
    }

    /**
     * Opens the step chain of a profile over a new commercial invoice (BT-3 is 380).
     *
     * @param profile the profile, for instance {@code Profile.EN16931}
     * @return the first step
     * @throws NullPointerException if {@code profile} is {@code null}
     */
    public static InvoiceSteps.Start create(Profile<?> profile) {
        return new InvoiceBuild(new Draft(profile, COMMERCIAL_INVOICE));
    }

    /**
     * Opens the step chain of a profile over a new credit note (BT-3 is 381).
     *
     * <p>The invoice being credited is named with
     * {@link InvoiceSteps.WithBuyer#precedingInvoice(String, LocalDate)}, which writes
     * BG-3 with BT-25 and BT-26.
     *
     * @param profile the profile, for instance {@code Profile.EN16931}
     * @return the first step
     * @throws NullPointerException if {@code profile} is {@code null}
     */
    public static InvoiceSteps.Start creditNote(Profile<?> profile) {
        return new InvoiceBuild(new Draft(profile, CREDIT_NOTE));
    }

    /**
     * Opens a draft of a profile over a new commercial invoice, to be written in any
     * order. {@link Draft#type(Coded)} makes it another kind of document.
     *
     * @param profile the profile, for instance {@code Profile.EN16931}
     * @return the draft
     * @throws NullPointerException if {@code profile} is {@code null}
     */
    public static Draft draft(Profile<?> profile) {
        return new Draft(profile, COMMERCIAL_INVOICE);
    }

    /**
     * Reads a document as an invoice.
     *
     * <p>The document is expected to carry what EN 16931-1 asks of every invoice; a
     * mandatory term it does not carry is a
     * {@link de.bsnsoft.esj.typed.MissingValueException} when the part of the
     * invoice it belongs to is read, not a silent absence. Codes come back resolved to
     * the constants of {@code de.bsnsoft.esj.invoice.code}, and a code the
     * snapshot does not carry comes back as a
     * {@link de.bsnsoft.esj.invoice.code.CustomCode}.
     *
     * @param document the document
     * @return the invoice
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public static Invoice read(SemanticDocument document) {
        return new Invoice(Objects.requireNonNull(document, "document"));
    }

    /**
     * Returns the document this invoice was read from.
     *
     * @return the document
     */
    public SemanticDocument document() {
        return document;
    }

    /**
     * Returns the invoice number (BT-1).
     *
     * @return the number
     */
    public String number() {
        return view.invoiceNumber().value();
    }

    /**
     * Returns the issue date (BT-2).
     *
     * @return the day the invoice was issued
     */
    public LocalDate issued() {
        return view.issueDate();
    }

    /**
     * Returns the invoice type code (BT-3).
     *
     * @return the type, a constant of {@code InvoiceType} or a custom code
     */
    public Coded type() {
        return InvoiceType.resolve(view.typeCode());
    }

    /**
     * Returns the currency of the invoice (BT-5).
     *
     * @return the currency, a constant of {@code CurrencyCode} or a custom code
     */
    public Coded currency() {
        return CurrencyCode.resolve(view.currencyCode());
    }

    /**
     * Returns the payment due date (BT-9).
     *
     * @return the day, or an empty optional
     */
    public Optional<LocalDate> dueDate() {
        return view.paymentDueDate();
    }

    /**
     * Returns the buyer reference (BT-10).
     *
     * @return the reference, or an empty optional
     */
    public Optional<String> buyerReference() {
        return view.buyerReference();
    }

    /**
     * Returns the project reference (BT-11).
     *
     * @return the reference, or an empty optional
     */
    public Optional<String> projectReference() {
        return view.projectReference();
    }

    /**
     * Returns the contract reference (BT-12).
     *
     * @return the reference, or an empty optional
     */
    public Optional<String> contractReference() {
        return view.contractReference();
    }

    /**
     * Returns the purchase order reference (BT-13).
     *
     * @return the reference, or an empty optional
     */
    public Optional<String> purchaseOrderReference() {
        return view.purchaseOrderReference();
    }

    /**
     * Returns the preceding invoice references (BG-3), the invoices this document corrects
     * or refers to.
     *
     * @return the references, in the order the document carries them
     */
    public List<PrecedingInvoice> precedingInvoices() {
        List<PrecedingInvoice> references = new ArrayList<>();
        for (de.bsnsoft.esj.typed.PrecedingInvoiceReference source
                : view.precedingInvoiceReferences()) {
            references.add(new PrecedingInvoice(source.reference(), source.issueDate()));
        }
        return List.copyOf(references);
    }

    /**
     * Returns the additional supporting documents (BG-24).
     *
     * @return the attachments, in the order the document carries them
     */
    public List<Attachment> attachments() {
        List<Attachment> attachments = new ArrayList<>();
        for (de.bsnsoft.esj.typed.AdditionalSupportingDocument source
                : view.additionalSupportingDocuments()) {
            Attachment attachment = source.attachment()
                    .map(binary -> Attachment.embedded(source.reference(), binary.bytes(),
                            binary.mimeCode(), binary.filename()))
                    .orElseGet(() -> Attachment.referencing(source.reference()));
            if (source.description().isPresent()) {
                attachment = attachment.description(source.description().orElseThrow());
            }
            if (source.externalLocation().isPresent()) {
                attachment = attachment.location(source.externalLocation().orElseThrow());
            }
            attachments.add(attachment);
        }
        return List.copyOf(attachments);
    }

    /**
     * Returns the invoice notes (BT-22).
     *
     * @return the notes, in the order the document carries them
     */
    public List<String> notes() {
        return view.notes().stream()
                .map(de.bsnsoft.esj.typed.Note::note)
                .toList();
    }

    /**
     * Returns the seller (BG-4).
     *
     * @return the seller
     */
    public Party seller() {
        de.bsnsoft.esj.typed.Seller seller = view.seller();
        Party party = Party.named(seller.name());
        if (seller.tradingName().isPresent()) {
            party = party.tradingName(seller.tradingName().orElseThrow());
        }
        for (Identifier identifier : seller.identifiers()) {
            party = party.identifier(identifier);
        }
        if (seller.legalRegistrationIdentifier().isPresent()) {
            party = party.legalRegistration(seller.legalRegistrationIdentifier().orElseThrow());
        }
        if (seller.vatIdentifier().isPresent()) {
            party = party.vatId(seller.vatIdentifier().orElseThrow().value());
        }
        if (seller.taxRegistrationIdentifier().isPresent()) {
            party = party.taxRegistration(seller.taxRegistrationIdentifier().orElseThrow().value());
        }
        if (seller.additionalLegalInformation().isPresent()) {
            party = party.additionalLegalInformation(
                    seller.additionalLegalInformation().orElseThrow());
        }
        Optional<ElectronicAddress> sellerAddress =
                seller.electronicAddress().flatMap(Invoice::electronicAddress);
        if (sellerAddress.isPresent()) {
            party = party.electronicAddress(sellerAddress.orElseThrow());
        }
        de.bsnsoft.esj.typed.SellerPostalAddress postal = seller.postalAddress();
        party = party.address(address(postal.countryCode(), postal.addressLine1(),
                postal.addressLine2(), postal.addressLine3(), postal.city(), postal.postCode(),
                postal.countrySubdivision()));
        if (seller.contact().isPresent()) {
            de.bsnsoft.esj.typed.SellerContact contact = seller.contact().orElseThrow();
            party = party.contact(new Contact(contact.name(), contact.telephone(),
                    contact.email()));
        }
        return party;
    }

    /**
     * Returns the buyer (BG-7).
     *
     * @return the buyer
     */
    public Party buyer() {
        de.bsnsoft.esj.typed.Buyer buyer = view.buyer();
        Party party = Party.named(buyer.name());
        if (buyer.tradingName().isPresent()) {
            party = party.tradingName(buyer.tradingName().orElseThrow());
        }
        if (buyer.identifier().isPresent()) {
            party = party.identifier(buyer.identifier().orElseThrow());
        }
        if (buyer.legalRegistrationIdentifier().isPresent()) {
            party = party.legalRegistration(buyer.legalRegistrationIdentifier().orElseThrow());
        }
        if (buyer.vatIdentifier().isPresent()) {
            party = party.vatId(buyer.vatIdentifier().orElseThrow().value());
        }
        Optional<ElectronicAddress> buyerAddress =
                buyer.electronicAddress().flatMap(Invoice::electronicAddress);
        if (buyerAddress.isPresent()) {
            party = party.electronicAddress(buyerAddress.orElseThrow());
        }
        de.bsnsoft.esj.typed.BuyerPostalAddress postal = buyer.postalAddress();
        party = party.address(address(postal.countryCode(), postal.addressLine1(),
                postal.addressLine2(), postal.addressLine3(), postal.city(), postal.postCode(),
                postal.countrySubdivision()));
        if (buyer.contact().isPresent()) {
            de.bsnsoft.esj.typed.BuyerContact contact = buyer.contact().orElseThrow();
            party = party.contact(new Contact(contact.name(), contact.telephone(),
                    contact.email()));
        }
        return party;
    }

    /**
     * Returns the payee (BG-10).
     *
     * @return the party to be paid where that is not the seller, or an empty optional
     */
    public Optional<Party> payee() {
        return view.payee().map(payee -> {
            Party party = Party.named(payee.name());
            if (payee.identifier().isPresent()) {
                party = party.identifier(payee.identifier().orElseThrow());
            }
            if (payee.legalRegistrationIdentifier().isPresent()) {
                party = party.legalRegistration(payee.legalRegistrationIdentifier().orElseThrow());
            }
            return party;
        });
    }

    /**
     * Returns the delivery information (BG-13).
     *
     * @return where and when what is invoiced was delivered, or an empty optional
     */
    public Optional<Delivery> delivery() {
        return view.delivery().flatMap(source -> {
            Delivery delivery = null;
            if (source.partyName().isPresent()) {
                delivery = Delivery.to(source.partyName().orElseThrow());
            }
            if (source.address().isPresent()) {
                de.bsnsoft.esj.typed.Address postal = source.address().orElseThrow();
                Address read = address(postal.countryCode(), postal.addressLine1(),
                        postal.addressLine2(), postal.addressLine3(), postal.city(),
                        postal.postCode(), postal.countrySubdivision());
                delivery = delivery == null ? Delivery.to(read) : delivery.address(read);
            }
            if (source.actualDeliveryDate().isPresent()) {
                LocalDate date = source.actualDeliveryDate().orElseThrow();
                delivery = delivery == null ? Delivery.on(date) : delivery.date(date);
            }
            if (source.locationIdentifier().isPresent()) {
                Identifier location = source.locationIdentifier().orElseThrow();
                delivery = delivery == null
                        ? Delivery.at(location) : delivery.location(location);
            }
            return Optional.ofNullable(delivery);
        });
    }

    /**
     * Returns the invoicing period (BG-14).
     *
     * @return the period the invoice is for, or an empty optional
     */
    public Optional<BillingPeriod> period() {
        return view.delivery()
                .flatMap(de.bsnsoft.esj.typed.Delivery::invoicingPeriod)
                .filter(period -> period.startDate().isPresent() || period.endDate().isPresent())
                .map(period -> new BillingPeriod(period.startDate(), period.endDate()));
    }

    /**
     * Returns the payment instructions (BG-16).
     *
     * @return how the invoice is to be paid, or an empty optional
     */
    public Optional<PaymentMeans> payment() {
        return view.paymentInstructions().map(source -> {
            String account = null;
            String accountName = null;
            String serviceProvider = null;
            if (!source.creditTransfers().isEmpty()) {
                de.bsnsoft.esj.typed.CreditTransfer transfer =
                        source.creditTransfers().get(0);
                account = transfer.accountIdentifier().value();
                accountName = transfer.accountName().orElse(null);
                serviceProvider =
                        transfer.serviceProviderIdentifier().map(Identifier::value).orElse(null);
            }
            String cardNumber = source.card()
                    .map(de.bsnsoft.esj.typed.Card::primaryAccountNumber).orElse(null);
            String cardHolder = source.card()
                    .flatMap(de.bsnsoft.esj.typed.Card::holderName).orElse(null);
            Optional<de.bsnsoft.esj.typed.DirectDebit> debit = source.directDebit();
            return PaymentMeans.read(PaymentMeansCode.resolve(source.paymentMeansTypeCode()),
                    source.paymentMeansText().orElse(null),
                    source.remittanceInformation().orElse(null),
                    account, accountName, serviceProvider, cardNumber, cardHolder,
                    debit.flatMap(d -> d.mandateReferenceIdentifier().map(Identifier::value))
                            .orElse(null),
                    debit.flatMap(d -> d.bankAssignedCreditorIdentifier().map(Identifier::value))
                            .orElse(null),
                    debit.flatMap(d -> d.debitedAccountIdentifier().map(Identifier::value))
                            .orElse(null));
        });
    }

    /**
     * Returns the payment terms (BT-9, BT-20) as the document states them.
     *
     * @return the terms, or an empty optional where the document states neither
     */
    public Optional<PaymentTerms> paymentTerms() {
        Optional<LocalDate> due = view.paymentDueDate();
        Optional<String> text = view.paymentTerms();
        if (due.isEmpty()) {
            return text.map(PaymentTerms::of);
        }
        PaymentTerms terms = PaymentTerms.dueDate(due.orElseThrow());
        return Optional.of(text.map(terms::text).orElse(terms));
    }

    /**
     * Returns the document level allowances (BG-20).
     *
     * @return the allowances, in the order the document carries them
     */
    public List<Allowance> allowances() {
        List<Allowance> allowances = new ArrayList<>();
        for (de.bsnsoft.esj.typed.DocumentLevelAllowance source
                : view.documentLevelAllowances()) {
            allowances.add(Allowance.read(source.amount(), source.percentage().orElse(null),
                    source.baseAmount().orElse(null), source.reason().orElse(null),
                    source.reasonCode().map(AllowanceReason::resolve).orElse(null),
                    Vat.of(VatCategory.resolve(source.vatCategoryCode()),
                            source.vatRate().orElse(null))));
        }
        return List.copyOf(allowances);
    }

    /**
     * Returns the document level charges (BG-21).
     *
     * @return the charges, in the order the document carries them
     */
    public List<Charge> charges() {
        List<Charge> charges = new ArrayList<>();
        for (de.bsnsoft.esj.typed.DocumentLevelCharge source
                : view.documentLevelCharges()) {
            charges.add(Charge.read(source.amount(), source.percentage().orElse(null),
                    source.baseAmount().orElse(null), source.reason().orElse(null),
                    source.reasonCode().map(ChargeReason::resolve).orElse(null),
                    Vat.of(VatCategory.resolve(source.vatCategoryCode()),
                            source.vatRate().orElse(null))));
        }
        return List.copyOf(charges);
    }

    /**
     * Returns the invoice lines (BG-25) with their codes resolved.
     *
     * @return the lines, in the order the document carries them
     */
    public List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        for (de.bsnsoft.esj.typed.InvoiceLine source : view.invoiceLines()) {
            lines.add(line(source));
        }
        return List.copyOf(lines);
    }

    /**
     * Returns the VAT breakdown (BG-23).
     *
     * @return one entry per VAT category and rate, in the order the document carries them
     */
    public List<VatAmount> vatBreakdown() {
        List<VatAmount> breakdown = new ArrayList<>();
        for (de.bsnsoft.esj.typed.VatBreakdown source : view.vatBreakdowns()) {
            Vat vat = Vat.of(VatCategory.resolve(source.vatCategoryCode()),
                    source.vatRate().orElse(null));
            if (source.exemptionReasonCode().isPresent()) {
                vat = vat.exemptionReason(
                        VatExemptionReason.resolve(source.exemptionReasonCode().orElseThrow()));
            }
            if (source.exemptionReasonText().isPresent()) {
                vat = vat.exemptionReason(source.exemptionReasonText().orElseThrow());
            }
            breakdown.add(new VatAmount(vat, source.taxableAmount(), source.taxAmount()));
        }
        return List.copyOf(breakdown);
    }

    /**
     * Returns the document totals (BG-22).
     *
     * @return what the invoice adds up to, or an empty optional where it carries no
     *         totals
     */
    public Optional<InvoiceTotals> totals() {
        de.bsnsoft.esj.typed.DocumentTotals totals = view.documentTotals();
        if (document.value(SemanticPath.of("/BG-22/BT-106")).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new InvoiceTotals(totals.sumOfLineNetAmounts(), totals.sumOfAllowances(),
                totals.sumOfCharges(), totals.totalWithoutVat(), totals.totalVatAmount(),
                totals.totalWithVat(), totals.paidAmount(), totals.roundingAmount(),
                totals.amountDueForPayment()));
    }

    /**
     * Returns the invoice number and the day it was issued.
     *
     * @return for example {@code RE-2026-0211 of 2026-05-12}
     */
    @Override
    public String toString() {
        return number() + " of " + issued();
    }

    private static Line line(de.bsnsoft.esj.typed.InvoiceLine source) {
        Line.Copy copy = new Line.Copy();
        de.bsnsoft.esj.typed.Item item = source.item();
        copy.identifier = source.identifier().value();
        copy.name = item.name();
        copy.description = item.description().orElse(null);
        copy.note = source.note().orElse(null);
        copy.quantity = source.quantity();
        copy.unit = Unit.resolve(source.quantityUnitCode());
        copy.netAmount = source.netAmount();
        de.bsnsoft.esj.typed.Price price = source.price();
        copy.netPrice = price.netPrice();
        copy.baseQuantity = price.baseQuantity().orElse(null);
        copy.baseQuantityUnit = price.baseQuantityUnitCode().map(Unit::resolve).orElse(null);
        copy.vat = Vat.of(VatCategory.resolve(source.vat().vatCategoryCode()),
                source.vat().vatRate().orElse(null));
        copy.objectIdentifier = source.objectIdentifier().orElse(null);
        copy.sellerIdentifier = item.sellerIdentifier().map(Identifier::value).orElse(null);
        copy.buyerIdentifier = item.buyerIdentifier().map(Identifier::value).orElse(null);
        copy.purchaseOrderLineReference = source.purchaseOrderLineReference().orElse(null);
        copy.period = source.period()
                .filter(period -> period.startDate().isPresent() || period.endDate().isPresent())
                .map(period -> new BillingPeriod(period.startDate(), period.endDate()))
                .orElse(null);
        List<Allowance> allowances = new ArrayList<>();
        for (de.bsnsoft.esj.typed.Allowance allowance : source.allowances()) {
            allowances.add(Allowance.read(allowance.amount(), allowance.percentage().orElse(null),
                    allowance.baseAmount().orElse(null), allowance.reason().orElse(null),
                    allowance.reasonCode().map(AllowanceReason::resolve).orElse(null), null));
        }
        copy.allowances = List.copyOf(allowances);
        List<Charge> charges = new ArrayList<>();
        for (de.bsnsoft.esj.typed.Charge charge : source.charges()) {
            charges.add(Charge.read(charge.amount(), charge.percentage().orElse(null),
                    charge.baseAmount().orElse(null), charge.reason().orElse(null),
                    charge.reasonCode().map(ChargeReason::resolve).orElse(null), null));
        }
        copy.charges = List.copyOf(charges);
        return copy.line();
    }

    private static Address address(String country, Optional<String> line1, Optional<String> line2,
                                   Optional<String> line3, Optional<String> city,
                                   Optional<String> postCode, Optional<String> subdivision) {
        Address address = Address.in(Country.resolve(country));
        if (line1.isPresent()) {
            address = address.line1(line1.orElseThrow());
        }
        if (line2.isPresent()) {
            address = address.line2(line2.orElseThrow());
        }
        if (line3.isPresent()) {
            address = address.line3(line3.orElseThrow());
        }
        if (city.isPresent()) {
            address = address.city(city.orElseThrow());
        }
        if (postCode.isPresent()) {
            address = address.postCode(postCode.orElseThrow());
        }
        if (subdivision.isPresent()) {
            address = address.countrySubdivision(subdivision.orElseThrow());
        }
        return address;
    }

    /**
     * The electronic address of a party, where it states the scheme EN 16931-1 asks of
     * it; an address written without one is no electronic address this layer can resolve.
     */
    private static Optional<ElectronicAddress> electronicAddress(Identifier identifier) {
        return identifier.scheme()
                .map(scheme -> ElectronicAddress.of(identifier.value(),
                        ElectronicAddressScheme.resolve(scheme)));
    }
}
