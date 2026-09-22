package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.Country;
import de.bsnsoft.esj.typed.Identifier;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Where and when what is invoiced was delivered: the delivery information (BG-13).
 *
 * <p>It states the name of the party delivered to (BT-70), the identifier of the place
 * (BT-71), the day of the delivery (BT-72) and the address of the place (BG-15). Every
 * part is optional; a delivery that states none of them is refused rather than written as
 * an empty group.
 *
 * <p>The delivery is a value: every method returns a new one and the original is
 * unchanged.
 */
public final class Delivery {

    private final String partyName;

    private final Identifier location;

    private final LocalDate date;

    private final Address address;

    private Delivery(String partyName, Identifier location, LocalDate date, Address address) {
        this.partyName = partyName;
        this.location = location;
        this.date = date;
        this.address = address;
    }

    /**
     * A delivery on a day (BT-72).
     *
     * @param date the day of the delivery
     * @return the delivery
     * @throws NullPointerException if {@code date} is {@code null}
     */
    public static Delivery on(LocalDate date) {
        return new Delivery(null, null, Objects.requireNonNull(date, "date"), null);
    }

    /**
     * A delivery to an address (BG-15).
     *
     * @param address the address delivered to
     * @return the delivery
     * @throws NullPointerException if {@code address} is {@code null}
     */
    public static Delivery to(Address address) {
        return new Delivery(null, null, null, Objects.requireNonNull(address, "address"));
    }

    /**
     * A delivery to a named party (BT-70).
     *
     * @param partyName the name of the party delivered to
     * @return the delivery
     * @throws IllegalArgumentException if the name is blank
     * @throws NullPointerException     if {@code partyName} is {@code null}
     */
    public static Delivery to(String partyName) {
        return new Delivery(Amounts.text(partyName, "partyName"), null, null, null);
    }

    /**
     * A delivery at a place identified by a code (BT-71).
     *
     * @param location the location identifier, with or without its scheme
     * @return the delivery
     * @throws NullPointerException if {@code location} is {@code null}
     */
    public static Delivery at(Identifier location) {
        return new Delivery(null, Objects.requireNonNull(location, "location"), null, null);
    }

    /**
     * Returns this delivery with the name of the party delivered to (BT-70).
     *
     * @param value the name
     * @return a new delivery
     * @throws IllegalArgumentException if the name is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Delivery partyName(String value) {
        return new Delivery(Amounts.text(value, "value"), location, date, address);
    }

    /**
     * Returns this delivery with the identifier of the place (BT-71).
     *
     * @param value the location identifier
     * @return a new delivery
     * @throws IllegalArgumentException if the identifier is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Delivery location(String value) {
        return location(Identifier.of(Amounts.text(value, "value")));
    }

    /**
     * Returns this delivery with the identifier of the place and the scheme it is to be
     * read under (BT-71-1).
     *
     * @param value  the location identifier
     * @param scheme the identification scheme, a code of ISO 6523 ICD
     * @return a new delivery
     * @throws IllegalArgumentException if a value is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public Delivery location(String value, String scheme) {
        return location(Identifier.of(Amounts.text(value, "value"),
                Amounts.text(scheme, "scheme")));
    }

    /**
     * Returns this delivery with the identifier of the place (BT-71).
     *
     * @param value the identifier, with or without its scheme
     * @return a new delivery
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Delivery location(Identifier value) {
        return new Delivery(partyName, Objects.requireNonNull(value, "value"), date, address);
    }

    /**
     * Returns this delivery with the day it happened (BT-72).
     *
     * @param value the day of the delivery
     * @return a new delivery
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Delivery date(LocalDate value) {
        return new Delivery(partyName, location, Objects.requireNonNull(value, "value"), address);
    }

    /**
     * Returns this delivery with the address delivered to (BG-15).
     *
     * @param value the address
     * @return a new delivery
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Delivery address(Address value) {
        return new Delivery(partyName, location, date, Objects.requireNonNull(value, "value"));
    }

    /**
     * Returns this delivery with the address delivered to (BG-15).
     *
     * @param line1    the main address line (BT-75)
     * @param postCode the post code (BT-78)
     * @param city     the city (BT-77)
     * @param country  the country (BT-80)
     * @return a new delivery
     * @throws IllegalArgumentException if a string is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public Delivery address(String line1, String postCode, String city, Coded country) {
        return address(Address.of(line1, postCode, city, country));
    }

    /**
     * Returns this delivery with the address of the place it went to (BG-15), in a country
     * of the generated list.
     *
     * @param line1    the main address line (BT-75)
     * @param postCode the post code (BT-78)
     * @param city     the city (BT-77)
     * @param country  the country (BT-80)
     * @return a new delivery
     * @throws IllegalArgumentException if a string is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public Delivery address(String line1, String postCode, String city, Country country) {
        return address(line1, postCode, city, (Coded) country);
    }

    /**
     * Returns the name of the party delivered to (BT-70).
     *
     * @return the name, or an empty optional
     */
    public Optional<String> partyName() {
        return Optional.ofNullable(partyName);
    }

    /**
     * Returns the identifier of the place (BT-71).
     *
     * @return the identifier, or an empty optional
     */
    public Optional<Identifier> location() {
        return Optional.ofNullable(location);
    }

    /**
     * Returns the day of the delivery (BT-72).
     *
     * @return the day, or an empty optional
     */
    public Optional<LocalDate> date() {
        return Optional.ofNullable(date);
    }

    /**
     * Returns the address delivered to (BG-15).
     *
     * @return the address, or an empty optional
     */
    public Optional<Address> address() {
        return Optional.ofNullable(address);
    }
}
