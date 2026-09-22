package de.bsnsoft.esj.invoice;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * When the invoice is to be paid: the payment due date (BT-9) and the payment terms in
 * words (BT-20).
 *
 * <p>{@link #days(int, String)} is the usual case and the reason this type exists: a
 * caller says thirty days and the due date is the issue date of the invoice plus thirty
 * days, counted where the invoice is built, because that is where the issue date is
 * known. {@link #dueDate(LocalDate)} states the day itself.
 *
 * <p>The payment terms are a value: every method returns a new object and the
 * original is unchanged.
 */
public final class PaymentTerms {

    private final Integer days;

    private final LocalDate dueDate;

    private final String text;

    private PaymentTerms(Integer days, LocalDate dueDate, String text) {
        this.days = days;
        this.dueDate = dueDate;
        this.text = text;
    }

    /**
     * Payable a number of days after the invoice was issued.
     *
     * @param days how many days after the issue date (BT-2) payment is due
     * @param text the payment terms in words (BT-20)
     * @return the payment terms
     * @throws IllegalArgumentException if the number of days is negative or the text is
     *                                  blank
     * @throws NullPointerException     if {@code text} is {@code null}
     */
    public static PaymentTerms days(int days, String text) {
        return days(days).text(text);
    }

    /**
     * Payable a number of days after the invoice was issued.
     *
     * @param days how many days after the issue date (BT-2) payment is due
     * @return the payment terms
     * @throws IllegalArgumentException if the number of days is negative
     */
    public static PaymentTerms days(int days) {
        if (days < 0) {
            throw new IllegalArgumentException(
                    "payment falls due on or after the issue date, not " + days + " days before");
        }
        return new PaymentTerms(days, null, null);
    }

    /**
     * Payable on a day.
     *
     * @param dueDate the payment due date (BT-9)
     * @return the payment terms
     * @throws NullPointerException if {@code dueDate} is {@code null}
     */
    public static PaymentTerms dueDate(LocalDate dueDate) {
        return new PaymentTerms(null, Objects.requireNonNull(dueDate, "dueDate"), null);
    }

    /**
     * Payable on a day, with the terms in words.
     *
     * @param dueDate the payment due date (BT-9)
     * @param text    the payment terms in words (BT-20)
     * @return the payment terms
     * @throws IllegalArgumentException if the text is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static PaymentTerms dueDate(LocalDate dueDate, String text) {
        return dueDate(dueDate).text(text);
    }

    /**
     * Payment terms that state no date, only words (BT-20).
     *
     * @param text the payment terms in words
     * @return the payment terms
     * @throws IllegalArgumentException if the text is blank
     * @throws NullPointerException     if {@code text} is {@code null}
     */
    public static PaymentTerms of(String text) {
        return new PaymentTerms(null, null, Amounts.text(text, "text"));
    }

    /**
     * Returns these terms with the words the invoice prints (BT-20).
     *
     * @param value the payment terms in words
     * @return new payment terms
     * @throws IllegalArgumentException if the text is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public PaymentTerms text(String value) {
        return new PaymentTerms(days, dueDate, Amounts.text(value, "value"));
    }

    /**
     * Returns the number of days after the issue date payment is due.
     *
     * @return the number of days, or an empty optional where a day was stated instead
     */
    public Optional<Integer> days() {
        return Optional.ofNullable(days);
    }

    /**
     * Returns the payment due date (BT-9) as it was stated.
     *
     * @return the day, or an empty optional where a number of days was stated instead
     */
    public Optional<LocalDate> dueDate() {
        return Optional.ofNullable(dueDate);
    }

    /**
     * Returns the payment terms in words (BT-20).
     *
     * @return the text, or an empty optional
     */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    /**
     * Returns the payment due date (BT-9) for an invoice issued on a day.
     *
     * @param issued the issue date of the invoice (BT-2)
     * @return the due date, or an empty optional where the terms state none
     * @throws NullPointerException if {@code issued} is {@code null}
     */
    public Optional<LocalDate> dueDateFor(LocalDate issued) {
        Objects.requireNonNull(issued, "issued");
        if (dueDate != null) {
            return Optional.of(dueDate);
        }
        return Optional.ofNullable(days).map(issued::plusDays);
    }
}
