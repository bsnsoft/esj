package de.bsnsoft.esj.invoice;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * The period an invoice or one of its lines is for: the invoicing period (BG-14, BT-73
 * and BT-74) and the invoice line period (BG-26, BT-134 and BT-135).
 *
 * <p>Either end may stand alone, and where both are there the start is not after the end.
 *
 * <p>The name is not {@code Period}: the domain API is written for callers whose files
 * already import {@code java.time}, where a {@code Period} is a length of time and not a
 * pair of days.
 *
 * @param start the first day of the period (BT-73, BT-134)
 * @param end   the last day of the period (BT-74, BT-135)
 */
public record BillingPeriod(Optional<LocalDate> start, Optional<LocalDate> end) {

    /**
     * Checks that both parts are present and that the period is not empty or inverted.
     *
     * @param start the first day of the period (BT-73, BT-134)
     * @param end   the last day of the period (BT-74, BT-135)
     * @throws IllegalArgumentException if neither end is stated, or the start lies after
     *                                  the end
     * @throws NullPointerException     if a part is {@code null}
     */
    public BillingPeriod {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (start.isEmpty() && end.isEmpty()) {
            throw new IllegalArgumentException("a period states a start date, an end date or both");
        }
        if (start.isPresent() && end.isPresent() && start.get().isAfter(end.get())) {
            throw new IllegalArgumentException("a period starts on " + start.get()
                    + " and ends before it, on " + end.get());
        }
    }

    /**
     * Creates a period with both ends.
     *
     * @param start the first day
     * @param end   the last day
     * @return the period
     * @throws IllegalArgumentException if the start lies after the end
     * @throws NullPointerException     if a part is {@code null}
     */
    public static BillingPeriod of(LocalDate start, LocalDate end) {
        return new BillingPeriod(Optional.of(Objects.requireNonNull(start, "start")),
                Optional.of(Objects.requireNonNull(end, "end")));
    }

    /**
     * Creates a period that states only when it began.
     *
     * @param start the first day
     * @return the period
     * @throws NullPointerException if {@code start} is {@code null}
     */
    public static BillingPeriod from(LocalDate start) {
        return new BillingPeriod(Optional.of(Objects.requireNonNull(start, "start")),
                Optional.empty());
    }

    /**
     * Creates a period that states only when it ended.
     *
     * @param end the last day
     * @return the period
     * @throws NullPointerException if {@code end} is {@code null}
     */
    public static BillingPeriod until(LocalDate end) {
        return new BillingPeriod(Optional.empty(), Optional.of(Objects.requireNonNull(end, "end")));
    }
}
