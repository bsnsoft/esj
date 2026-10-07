package de.bsnsoft.esj.invoice.code;

/**
 * A code of a code list: the value a business term carries, and the name its publisher
 * gives that value.
 *
 * <p>The generated enums of this package implement it, and so does {@link CustomCode},
 * which stands for a code that the snapshot this version was generated from does not
 * carry. A method of the domain API that takes a code therefore takes {@code Coded} and
 * not the enum: a caller writes {@code Unit.PIECE} where the list has the code and
 * {@code Unit.custom("XYZ")} where a list has moved on and the invoice has to carry the
 * newer code anyway.
 *
 * <p>Nothing here decides whether a code is admissible. Membership in a code list is a
 * business rule, it is decided by a named rule of a named pack version against a dated
 * snapshot, and a report says so; an enum is a spelling aid and never a verdict.
 *
 * <p>The interface is sealed: a code no enum carries is a {@link CustomCode}, and a later
 * release may add methods here. The generated enums are listed by name, so a new code list
 * of the generator is added to {@code permits} with it.
 */
public sealed interface Coded permits AllowanceReason, ChargeReason, Country, CurrencyCode,
        CustomCode, ElectronicAddressScheme, InvoiceType, PaymentMeansCode, Unit, VatCategory,
        VatExemptionReason {

    /**
     * Returns the code as it is written into the document.
     *
     * @return the code
     */
    String code();

    /**
     * Returns the name the publisher of the list gives the code.
     *
     * @return the name, empty where the publisher gives none and for a custom code
     */
    String publishedName();

    /**
     * Returns the code list the code came from.
     *
     * @return the identifier of the list, for example {@code iso-4217}, and the empty
     *         string for a custom code, which came from no snapshot
     */
    String listId();
}
