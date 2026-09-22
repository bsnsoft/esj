package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.PaymentMeansCode;
import java.util.Objects;
import java.util.Optional;

/**
 * How the invoice is to be paid: the payment instructions (BG-16).
 *
 * <p>One factory per way of paying, each of which writes the payment means type code
 * (BT-81) that goes with it and the account the way of paying needs: a credit transfer
 * writes the account identifier (BT-84) of BG-17, a card payment the primary account
 * number (BT-87) of BG-18, a direct debit the mandate reference (BT-89), the creditor
 * identifier (BT-90) and the debited account (BT-91) of BG-19.
 *
 * <p>An account number is text, not a number: it is written exactly as it was given, with
 * no spacing removed and no letter case changed, because the register it is looked up in
 * decides what it looks like and this layer does not.
 *
 * <p>The payment instruction is a value: every method returns a new one and the original is
 * unchanged.
 */
public final class PaymentMeans {

    private final Coded code;

    private final String text;

    private final String remittanceInformation;

    private final String account;

    private final String accountName;

    private final String serviceProvider;

    private final String cardNumber;

    private final String cardHolder;

    private final String mandateReference;

    private final String creditorIdentifier;

    private final String debitedAccount;

    private PaymentMeans(Coded code, String text, String remittanceInformation, String account,
                         String accountName, String serviceProvider, String cardNumber,
                         String cardHolder, String mandateReference, String creditorIdentifier,
                         String debitedAccount) {
        this.code = code;
        this.text = text;
        this.remittanceInformation = remittanceInformation;
        this.account = account;
        this.accountName = accountName;
        this.serviceProvider = serviceProvider;
        this.cardNumber = cardNumber;
        this.cardHolder = cardHolder;
        this.mandateReference = mandateReference;
        this.creditorIdentifier = creditorIdentifier;
        this.debitedAccount = debitedAccount;
    }

    /**
     * A SEPA credit transfer, payment means type code {@code 58}, to an IBAN.
     *
     * @param iban the account identifier (BT-84)
     * @return the payment instructions
     * @throws IllegalArgumentException if the account is blank
     * @throws NullPointerException     if {@code iban} is {@code null}
     */
    public static PaymentMeans sepaCreditTransfer(String iban) {
        return transfer(PaymentMeansCode.SEPA_CREDIT_TRANSFER, iban, null);
    }

    /**
     * A SEPA credit transfer, payment means type code {@code 58}, to an IBAN at a bank.
     *
     * @param iban the account identifier (BT-84)
     * @param bic  the payment service provider identifier (BT-86)
     * @return the payment instructions
     * @throws IllegalArgumentException if a value is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static PaymentMeans sepaCreditTransfer(String iban, String bic) {
        return transfer(PaymentMeansCode.SEPA_CREDIT_TRANSFER, iban,
                Amounts.text(bic, "bic"));
    }

    /**
     * A credit transfer, payment means type code {@code 30}, to an account.
     *
     * @param account the account identifier (BT-84)
     * @return the payment instructions
     * @throws IllegalArgumentException if the account is blank
     * @throws NullPointerException     if {@code account} is {@code null}
     */
    public static PaymentMeans creditTransfer(String account) {
        return transfer(PaymentMeansCode.CREDIT_TRANSFER, account, null);
    }

    /**
     * A credit transfer, payment means type code {@code 30}, to an account at a provider.
     *
     * @param account         the account identifier (BT-84)
     * @param serviceProvider the payment service provider identifier (BT-86)
     * @return the payment instructions
     * @throws IllegalArgumentException if a value is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static PaymentMeans creditTransfer(String account, String serviceProvider) {
        return transfer(PaymentMeansCode.CREDIT_TRANSFER, account,
                Amounts.text(serviceProvider, "serviceProvider"));
    }

    /**
     * A card payment, payment means type code {@code 48}.
     *
     * @param cardNumber the primary account number (BT-87); EN 16931-1 asks that no more
     *                   than the last four to six digits of it be stated
     * @param holderName the name of the card holder (BT-88), or {@code null}
     * @return the payment instructions
     * @throws IllegalArgumentException if the card number is blank
     * @throws NullPointerException     if {@code cardNumber} is {@code null}
     */
    public static PaymentMeans card(String cardNumber, String holderName) {
        return new PaymentMeans(PaymentMeansCode.BANK_CARD, null, null, null, null, null,
                Amounts.text(cardNumber, "cardNumber"), holderName, null, null, null);
    }

    /**
     * A SEPA direct debit, payment means type code {@code 59}.
     *
     * @param mandateReference   the mandate reference identifier (BT-89)
     * @param creditorIdentifier the bank assigned creditor identifier (BT-90)
     * @param debitedAccount     the debited account identifier (BT-91)
     * @return the payment instructions
     * @throws IllegalArgumentException if a value is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static PaymentMeans directDebit(String mandateReference, String creditorIdentifier,
                                           String debitedAccount) {
        return new PaymentMeans(PaymentMeansCode.SEPA_DIRECT_DEBIT, null, null, null, null, null,
                null, null, Amounts.text(mandateReference, "mandateReference"),
                Amounts.text(creditorIdentifier, "creditorIdentifier"),
                Amounts.text(debitedAccount, "debitedAccount"));
    }

    /**
     * A way of paying that carries nothing but its code (BT-81), for a case the factories
     * above do not name.
     *
     * @param code the payment means type code, a code of UNTDID 4461
     * @return the payment instructions
     * @throws NullPointerException if {@code code} is {@code null}
     */
    public static PaymentMeans of(Coded code) {
        return new PaymentMeans(Objects.requireNonNull(code, "code"), null, null, null, null, null,
                null, null, null, null, null);
    }

    /**
     * A way of paying of the generated list that carries nothing but its code (BT-81).
     *
     * @param code the payment means type code
     * @return the payment instructions
     * @throws NullPointerException if {@code code} is {@code null}
     */
    public static PaymentMeans of(PaymentMeansCode code) {
        return of((Coded) code);
    }

    /**
     * Rebuilds payment instructions read from a document, with whatever they state and
     * nothing added.
     *
     * @param code                  the payment means type code (BT-81)
     * @param text                  the way of paying in words (BT-82), or {@code null}
     * @param remittanceInformation the remittance information (BT-83), or {@code null}
     * @param account               the account identifier (BT-84), or {@code null}
     * @param accountName           the payment account name (BT-85), or {@code null}
     * @param serviceProvider       the provider identifier (BT-86), or {@code null}
     * @param cardNumber            the primary account number (BT-87), or {@code null}
     * @param cardHolder            the card holder name (BT-88), or {@code null}
     * @param mandateReference      the mandate reference (BT-89), or {@code null}
     * @param creditorIdentifier    the creditor identifier (BT-90), or {@code null}
     * @param debitedAccount        the debited account (BT-91), or {@code null}
     * @return the payment instructions
     */
    static PaymentMeans read(Coded code, String text, String remittanceInformation, String account,
                             String accountName, String serviceProvider, String cardNumber,
                             String cardHolder, String mandateReference, String creditorIdentifier,
                             String debitedAccount) {
        return new PaymentMeans(code, text, remittanceInformation, account, accountName,
                serviceProvider, cardNumber, cardHolder, mandateReference, creditorIdentifier,
                debitedAccount);
    }

    private static PaymentMeans transfer(Coded code, String account, String serviceProvider) {
        return new PaymentMeans(code, null, null, Amounts.text(account, "account"), null,
                serviceProvider, null, null, null, null, null);
    }

    /**
     * Returns these instructions with the way of paying in words (BT-82).
     *
     * @param value the payment means text
     * @return new payment instructions
     * @throws IllegalArgumentException if the text is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public PaymentMeans text(String value) {
        return copy().text(Amounts.text(value, "value")).means();
    }

    /**
     * Returns these instructions with the reference the payer is to quote (BT-83).
     *
     * @param value the remittance information
     * @return new payment instructions
     * @throws IllegalArgumentException if the text is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public PaymentMeans remittanceInformation(String value) {
        return copy().remittanceInformation(Amounts.text(value, "value")).means();
    }

    /**
     * Returns these instructions with the name the account is held under (BT-85).
     *
     * @param value the payment account name
     * @return new payment instructions
     * @throws IllegalArgumentException if the text is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public PaymentMeans accountName(String value) {
        return copy().accountName(Amounts.text(value, "value")).means();
    }

    /**
     * Returns the payment means type code (BT-81).
     *
     * @return the code, a constant of {@code PaymentMeansCode} or a custom code
     */
    public Coded code() {
        return code;
    }

    /**
     * Returns the way of paying in words (BT-82).
     *
     * @return the text, or an empty optional
     */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    /**
     * Returns the reference the payer is to quote (BT-83).
     *
     * @return the remittance information, or an empty optional
     */
    public Optional<String> remittanceInformation() {
        return Optional.ofNullable(remittanceInformation);
    }

    /**
     * Returns the account paid into (BT-84).
     *
     * @return the account identifier, or an empty optional
     */
    public Optional<String> account() {
        return Optional.ofNullable(account);
    }

    /**
     * Returns the name the account is held under (BT-85).
     *
     * @return the account name, or an empty optional
     */
    public Optional<String> accountName() {
        return Optional.ofNullable(accountName);
    }

    /**
     * Returns the payment service provider of the account (BT-86).
     *
     * @return the provider identifier, or an empty optional
     */
    public Optional<String> serviceProvider() {
        return Optional.ofNullable(serviceProvider);
    }

    /**
     * Returns the primary account number of the card (BT-87).
     *
     * @return the card number, or an empty optional
     */
    public Optional<String> cardNumber() {
        return Optional.ofNullable(cardNumber);
    }

    /**
     * Returns the name of the card holder (BT-88).
     *
     * @return the name, or an empty optional
     */
    public Optional<String> cardHolder() {
        return Optional.ofNullable(cardHolder);
    }

    /**
     * Returns the mandate reference identifier of the direct debit (BT-89).
     *
     * @return the reference, or an empty optional
     */
    public Optional<String> mandateReference() {
        return Optional.ofNullable(mandateReference);
    }

    /**
     * Returns the bank assigned creditor identifier of the direct debit (BT-90).
     *
     * @return the identifier, or an empty optional
     */
    public Optional<String> creditorIdentifier() {
        return Optional.ofNullable(creditorIdentifier);
    }

    /**
     * Returns the debited account identifier of the direct debit (BT-91).
     *
     * @return the account identifier, or an empty optional
     */
    public Optional<String> debitedAccount() {
        return Optional.ofNullable(debitedAccount);
    }

    /**
     * Returns the way of paying and, where there is one, the account.
     *
     * @return for example {@code 58 to DE89370400440532013000}
     */
    @Override
    public String toString() {
        return account == null ? code.code() : code.code() + " to " + account;
    }

    private Copy copy() {
        return new Copy(this);
    }

    /** The one place a part of the instructions is replaced, so that no setter lists them all. */
    private static final class Copy {

        private final PaymentMeans means;
        private String text;
        private String remittanceInformation;
        private String accountName;

        Copy(PaymentMeans means) {
            this.means = means;
            this.text = means.text;
            this.remittanceInformation = means.remittanceInformation;
            this.accountName = means.accountName;
        }

        Copy text(String value) {
            this.text = value;
            return this;
        }

        Copy remittanceInformation(String value) {
            this.remittanceInformation = value;
            return this;
        }

        Copy accountName(String value) {
            this.accountName = value;
            return this;
        }

        PaymentMeans means() {
            return new PaymentMeans(means.code, text, remittanceInformation, means.account,
                    accountName, means.serviceProvider, means.cardNumber, means.cardHolder,
                    means.mandateReference, means.creditorIdentifier, means.debitedAccount);
        }
    }
}
