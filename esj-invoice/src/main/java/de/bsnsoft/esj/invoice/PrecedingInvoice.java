package de.bsnsoft.esj.invoice;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * The invoice a document corrects or refers to: a preceding invoice reference (BG-3).
 *
 * <p>A credit note names here the invoice it credits. The number (BT-25) is mandatory in
 * the group and the issue date (BT-26) is asked for where the number alone does not say
 * which invoice is meant.
 *
 * <p>It is a value: every method returns a new one and the original is unchanged.
 *
 * @param number the number of the preceding invoice (BT-25)
 * @param issued the day it was issued (BT-26), or an empty optional
 */
public record PrecedingInvoice(String number, Optional<LocalDate> issued) {

    /**
     * Checks that both parts are present and that the number is not blank.
     *
     * @param number the number of the preceding invoice (BT-25)
     * @param issued the day it was issued (BT-26), or an empty optional
     * @throws IllegalArgumentException if the number is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public PrecedingInvoice {
        number = Amounts.text(number, "number");
        Objects.requireNonNull(issued, "issued");
    }

    /**
     * Creates a reference that states only the number of the preceding invoice.
     *
     * @param number the number
     * @return the reference
     * @throws IllegalArgumentException if the number is blank
     * @throws NullPointerException     if {@code number} is {@code null}
     */
    public static PrecedingInvoice of(String number) {
        return new PrecedingInvoice(number, Optional.empty());
    }

    /**
     * Creates a reference that states the number and the day the preceding invoice was
     * issued.
     *
     * @param number the number
     * @param issued the day it was issued
     * @return the reference
     * @throws IllegalArgumentException if the number is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static PrecedingInvoice of(String number, LocalDate issued) {
        return new PrecedingInvoice(number,
                Optional.of(Objects.requireNonNull(issued, "issued")));
    }

    /**
     * Returns the number of the preceding invoice and, where it states one, its issue
     * date.
     *
     * @return for example {@code RE-2026-0001 of 2026-03-02}
     */
    @Override
    public String toString() {
        return issued.map(day -> number + " of " + day).orElse(number);
    }
}
