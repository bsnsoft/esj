package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.CurrencyCode;
import de.bsnsoft.esj.typed.InvoiceEditor;
import de.bsnsoft.esj.typed.build.BuildReport;
import de.bsnsoft.esj.typed.build.InvoiceRules;
import de.bsnsoft.esj.typed.build.Profile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * The chain {@link Invoice#create(Profile)} opens: one step per member EN 16931-1 asks of
 * every invoice, in the order a person writes an invoice in, and a terminal step that
 * exists only once the last of them has been written.
 *
 * <p>The steps are the invoice number (BT-1), the issue date (BT-2), the currency (BT-5),
 * the seller (BG-4), the buyer (BG-7) and the first invoice line (BG-25). The invoice
 * type code (BT-3) and the specification identifier (BT-24) are written when the chain is
 * opened, the totals and the VAT breakdown are derived when it is built, and everything
 * else hangs off the last two steps in any order.
 *
 * <p>What a profile asks for beyond EN 16931-1 is checked when the invoice is built and
 * named by term; the chain itself is the same for every profile. A group the model allows
 * at most once is written once here too: the seller and the buyer are steps and cannot be
 * repeated at all, and {@link Buildable#draft()} refuses a second seller, buyer, payee,
 * delivery, invoicing period or set of payment instructions rather than merging it into
 * the first.
 */
public interface InvoiceSteps {

    /** The step an invoice starts at. */
    interface Start {

        /**
         * Writes the invoice number (BT-1).
         *
         * @param value the number the seller gives the invoice
         * @return the next step
         * @throws IllegalArgumentException if the number is blank
         * @throws NullPointerException     if {@code value} is {@code null}
         */
        WithNumber number(String value);
    }

    /** The step after the invoice number. */
    interface WithNumber {

        /**
         * Writes the issue date (BT-2).
         *
         * @param value the day the invoice was issued
         * @return the next step
         * @throws NullPointerException if {@code value} is {@code null}
         */
        WithIssueDate issued(LocalDate value);
    }

    /** The step after the issue date. */
    interface WithIssueDate {

        /**
         * Writes the currency of the invoice (BT-5).
         *
         * @param value the currency, for example {@code CurrencyCode.EUR}
         * @return the next step
         * @throws NullPointerException if {@code value} is {@code null}
         */
        WithCurrency currency(Coded value);

        /**
         * Writes the currency of the invoice (BT-5), a code of the generated list.
         *
         * @param value the currency
         * @return the next step
         * @throws NullPointerException if {@code value} is {@code null}
         */
        WithCurrency currency(CurrencyCode value);
    }

    /** The step after the currency. */
    interface WithCurrency {

        /**
         * Writes the seller (BG-4).
         *
         * @param party the seller
         * @return the next step
         * @throws NullPointerException if {@code party} is {@code null}
         */
        WithSeller seller(Party party);
    }

    /** The step after the seller. */
    interface WithSeller {

        /**
         * Writes the buyer (BG-7).
         *
         * @param party the buyer
         * @return the next step
         * @throws IllegalArgumentException if the party states a term the buyer has none
         *                                  of
         * @throws NullPointerException     if {@code party} is {@code null}
         */
        WithBuyer buyer(Party party);
    }

    /**
     * The step after the buyer: everything EN 16931-1 leaves optional, and the first
     * invoice line, which is what opens {@link Buildable}.
     */
    interface WithBuyer {

        /**
         * Writes one invoice line (BG-25), numbered by its position where it states no
         * identifier of its own.
         *
         * @param line the line
         * @return the terminal step
         * @throws IllegalArgumentException if a percentage on the line has no base amount
         *                                  and none can be taken from the line
         * @throws NullPointerException     if {@code line} is {@code null}
         */
        Buildable line(Line line);

        /**
         * Writes the payment instructions (BG-16).
         *
         * @param means how the invoice is to be paid
         * @return this step
         * @throws NullPointerException if {@code means} is {@code null}
         */
        WithBuyer payment(PaymentMeans means);

        /**
         * Writes the payment instructions (BG-16) and the payment terms (BT-9, BT-20).
         *
         * @param means how the invoice is to be paid
         * @param terms when it is to be paid
         * @return this step
         * @throws NullPointerException if a part is {@code null}
         */
        WithBuyer payment(PaymentMeans means, PaymentTerms terms);

        /**
         * Writes the payment terms: the payment due date (BT-9) and the terms in words
         * (BT-20).
         *
         * @param terms when the invoice is to be paid
         * @return this step
         * @throws NullPointerException if {@code terms} is {@code null}
         */
        WithBuyer paymentTerms(PaymentTerms terms);

        /**
         * Writes one document level allowance (BG-20).
         *
         * @param allowance the allowance, with the VAT it is taxed under
         * @return this step
         * @throws IllegalArgumentException if the allowance states no VAT, or a
         *                                  percentage without a base amount
         * @throws NullPointerException     if {@code allowance} is {@code null}
         */
        WithBuyer allowance(Allowance allowance);

        /**
         * Writes one document level charge (BG-21).
         *
         * @param charge the charge, with the VAT it is taxed under
         * @return this step
         * @throws IllegalArgumentException if the charge states no VAT, or a percentage
         *                                  without a base amount
         * @throws NullPointerException     if {@code charge} is {@code null}
         */
        WithBuyer charge(Charge charge);

        /**
         * Writes one invoice note (BG-1, BT-22).
         *
         * @param text the note
         * @return this step
         * @throws IllegalArgumentException if the note is blank
         * @throws NullPointerException     if {@code text} is {@code null}
         */
        WithBuyer note(String text);

        /**
         * Writes the buyer reference (BT-10).
         *
         * @param value the reference
         * @return this step
         * @throws IllegalArgumentException if the reference is blank
         * @throws NullPointerException     if {@code value} is {@code null}
         */
        WithBuyer buyerReference(String value);

        /**
         * Writes the purchase order reference (BT-13).
         *
         * @param value the reference
         * @return this step
         * @throws IllegalArgumentException if the reference is blank
         * @throws NullPointerException     if {@code value} is {@code null}
         */
        WithBuyer purchaseOrderReference(String value);

        /**
         * Writes the contract reference (BT-12).
         *
         * @param value the reference
         * @return this step
         * @throws IllegalArgumentException if the reference is blank
         * @throws NullPointerException     if {@code value} is {@code null}
         */
        WithBuyer contractReference(String value);

        /**
         * Writes the project reference (BT-11).
         *
         * @param value the reference
         * @return this step
         * @throws IllegalArgumentException if the reference is blank
         * @throws NullPointerException     if {@code value} is {@code null}
         */
        WithBuyer projectReference(String value);

        /**
         * Writes one preceding invoice reference (BG-3), the invoice this document
         * corrects or refers to (BT-25). A credit note names here what it credits.
         *
         * @param number the number of the preceding invoice
         * @return this step
         * @throws IllegalArgumentException if the number is blank
         * @throws NullPointerException     if {@code number} is {@code null}
         */
        WithBuyer precedingInvoice(String number);

        /**
         * Writes one preceding invoice reference (BG-3) with the day that invoice was
         * issued (BT-25, BT-26).
         *
         * @param number the number of the preceding invoice
         * @param issued the day it was issued, or {@code null}
         * @return this step
         * @throws IllegalArgumentException if the number is blank
         * @throws NullPointerException     if {@code number} is {@code null}
         */
        WithBuyer precedingInvoice(String number, LocalDate issued);

        /**
         * Writes the amount already paid (BT-113), which the derivation subtracts from the
         * total with VAT to arrive at the amount due for payment (BT-115).
         *
         * @param value the amount paid, in the currency of the invoice
         * @return this step
         * @throws NullPointerException if {@code value} is {@code null}
         */
        WithBuyer paidAmount(BigDecimal value);

        /**
         * Writes the rounding amount (BT-114), which the derivation adds to the total with
         * VAT to arrive at the amount due for payment (BT-115).
         *
         * @param value the amount rounded up or down, in the currency of the invoice
         * @return this step
         * @throws NullPointerException if {@code value} is {@code null}
         */
        WithBuyer roundingAmount(BigDecimal value);

        /**
         * Writes the payee (BG-10), the party to be paid where that is not the seller.
         *
         * @param party the payee
         * @return this step
         * @throws IllegalArgumentException if the party states a term the payee has none
         *                                  of
         * @throws NullPointerException     if {@code party} is {@code null}
         */
        WithBuyer payee(Party party);

        /**
         * Writes the delivery information (BG-13) with its address (BG-15).
         *
         * @param delivery where and when what is invoiced was delivered
         * @return this step
         * @throws NullPointerException if {@code delivery} is {@code null}
         */
        WithBuyer delivery(Delivery delivery);

        /**
         * Writes the invoicing period (BG-14, BT-73 and BT-74).
         *
         * @param period the period the invoice is for
         * @return this step
         * @throws NullPointerException if {@code period} is {@code null}
         */
        WithBuyer period(BillingPeriod period);

        /**
         * Writes one additional supporting document (BG-24).
         *
         * @param attachment the document
         * @return this step
         * @throws NullPointerException if {@code attachment} is {@code null}
         */
        WithBuyer attachment(Attachment attachment);

        /**
         * Hands out the typed editor for the terms the domain layer has no word for.
         *
         * @param block what to write
         * @return this step
         * @throws NullPointerException if {@code block} is {@code null}
         */
        WithBuyer edit(Consumer<InvoiceEditor> block);
    }

    /**
     * The terminal step: an invoice that states everything EN 16931-1 asks of every
     * invoice, and can therefore be derived, checked and handed over.
     */
    interface Buildable extends WithBuyer {

        @Override
        Buildable line(Line line);

        @Override
        Buildable payment(PaymentMeans means);

        @Override
        Buildable payment(PaymentMeans means, PaymentTerms terms);

        @Override
        Buildable paymentTerms(PaymentTerms terms);

        @Override
        Buildable allowance(Allowance allowance);

        @Override
        Buildable charge(Charge charge);

        @Override
        Buildable note(String text);

        @Override
        Buildable buyerReference(String value);

        @Override
        Buildable purchaseOrderReference(String value);

        @Override
        Buildable contractReference(String value);

        @Override
        Buildable projectReference(String value);

        @Override
        Buildable precedingInvoice(String number);

        @Override
        Buildable precedingInvoice(String number, LocalDate issued);

        @Override
        Buildable paidAmount(BigDecimal value);

        @Override
        Buildable roundingAmount(BigDecimal value);

        @Override
        Buildable payee(Party party);

        @Override
        Buildable delivery(Delivery delivery);

        @Override
        Buildable period(BillingPeriod period);

        @Override
        Buildable attachment(Attachment attachment);

        @Override
        Buildable edit(Consumer<InvoiceEditor> block);

        /**
         * Returns the draft behind this chain, which writes the same invoice in any
         * order.
         *
         * @return the draft
         */
        Draft draft();

        /**
         * Returns the invoice as it has been written so far, without deriving and without
         * checking anything.
         *
         * @return an immutable document in canonical path order
         */
        SemanticDocument document();

        /**
         * Checks the invoice as it stands: the structural layers L2 and L3, the members
         * the model and the profile ask for, and the business rules of EN 16931-1.
         *
         * @return what the check found
         */
        BuildReport validate();

        /**
         * Checks the invoice as {@link #validate()} does, with a rule set of the caller's
         * choosing.
         *
         * @param rules the business rules to run
         * @return what the check found
         * @throws NullPointerException if {@code rules} is {@code null}
         */
        BuildReport validate(InvoiceRules rules);

        /**
         * Derives the amounts, checks the invoice and returns it.
         *
         * @return an immutable document in canonical path order
         * @throws IllegalStateException if payment terms were stated as a number of days
         *                               and no issue date (BT-2) was written for them to
         *                               count from
         * @throws de.bsnsoft.esj.typed.DerivationException if the amounts
         *                                                             cannot be derived
         * @throws de.bsnsoft.esj.typed.build.BuildException if the check
         *                                                               found anything
         */
        SemanticDocument build();

        /**
         * Derives the amounts, checks the invoice against a rule set of the caller's
         * choosing and returns it.
         *
         * @param rules the business rules to run
         * @return an immutable document in canonical path order
         * @throws IllegalStateException if payment terms were stated as a number of days
         *                               and no issue date (BT-2) was written for them to
         *                               count from
         * @throws de.bsnsoft.esj.typed.DerivationException if the amounts
         *                                                             cannot be derived
         * @throws de.bsnsoft.esj.typed.build.BuildException if the check
         *                                                               found anything
         * @throws NullPointerException  if {@code rules} is {@code null}
         */
        SemanticDocument build(InvoiceRules rules);

        /**
         * Derives the amounts and checks the invoice without refusing it.
         *
         * @return the document and what the check found
         * @throws IllegalStateException if payment terms were stated as a number of days
         *                               and no issue date (BT-2) was written for them to
         *                               count from
         * @throws de.bsnsoft.esj.typed.DerivationException if the amounts
         *                                                             cannot be derived
         */
        InvoiceResult buildReport();

        /**
         * Derives the amounts and checks the invoice against a rule set of the caller's
         * choosing, without refusing it.
         *
         * @param rules the business rules to run
         * @return the document and what the check found
         * @throws IllegalStateException if payment terms were stated as a number of days
         *                               and no issue date (BT-2) was written for them to
         *                               count from
         * @throws de.bsnsoft.esj.typed.DerivationException if the amounts
         *                                                             cannot be derived
         * @throws NullPointerException  if {@code rules} is {@code null}
         */
        InvoiceResult buildReport(InvoiceRules rules);
    }
}
