package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.ElectronicAddressScheme;
import java.util.Objects;

/**
 * The electronic address of a party, BT-34 for the seller and BT-49 for the buyer.
 *
 * <p>The address is an identifier and its identification scheme is mandatory: the scheme
 * says which register the address is to be looked up in. The scheme is a code of the
 * electronic address scheme list, which {@link ElectronicAddressScheme} spells.
 *
 * <p>The value comes first and the scheme second, as it does everywhere else in this
 * package: {@code Party.identifier(value, scheme)} and
 * {@code Party.legalRegistration(value, scheme)} are written the same way round.
 *
 * @param value  the address itself
 * @param scheme the identification scheme, BT-34-1 or BT-49-1
 */
public record ElectronicAddress(String value, Coded scheme) {

    /**
     * Checks that both parts are present and that the address is not blank.
     *
     * @param value  the address itself
     * @param scheme the identification scheme, BT-34-1 or BT-49-1
     * @throws IllegalArgumentException if the address is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public ElectronicAddress {
        Amounts.text(value, "value");
        Objects.requireNonNull(scheme, "scheme");
    }

    /**
     * Creates an electronic address.
     *
     * @param value  the address itself
     * @param scheme the identification scheme, for example {@code
     *               ElectronicAddressScheme.ELECTRONIC_MAIL}
     * @return the electronic address
     * @throws IllegalArgumentException if the address is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static ElectronicAddress of(String value, Coded scheme) {
        return new ElectronicAddress(value, scheme);
    }

    /**
     * Creates an electronic address under a scheme of the generated list.
     *
     * @param value  the address itself
     * @param scheme the identification scheme
     * @return the electronic address
     * @throws IllegalArgumentException if the address is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static ElectronicAddress of(String value, ElectronicAddressScheme scheme) {
        return new ElectronicAddress(value, scheme);
    }
}
