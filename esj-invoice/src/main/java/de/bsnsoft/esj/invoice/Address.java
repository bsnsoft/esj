package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.Country;
import java.util.Objects;
import java.util.Optional;

/**
 * A postal address: BG-5 for the seller, BG-8 for the buyer, BG-15 for the place an
 * invoice was delivered to.
 *
 * <p>The country code is the only part EN 16931-1 makes mandatory (BT-40, BT-55, BT-80);
 * the street lines, the city, the post code and the country subdivision are optional in
 * the standard and some of them are required by a profile. The address is a value: every
 * method returns a new one and the original is unchanged.
 */
public final class Address {

    private final Coded country;

    private final String line1;

    private final String line2;

    private final String line3;

    private final String city;

    private final String postCode;

    private final String countrySubdivision;

    private Address(Coded country, String line1, String line2, String line3, String city,
                    String postCode, String countrySubdivision) {
        this.country = country;
        this.line1 = line1;
        this.line2 = line2;
        this.line3 = line3;
        this.city = city;
        this.postCode = postCode;
        this.countrySubdivision = countrySubdivision;
    }

    /**
     * Creates an address that states nothing but the country (BT-40, BT-55, BT-80).
     *
     * @param country the country, for example {@code Country.DE}
     * @return the address
     * @throws NullPointerException if {@code country} is {@code null}
     */
    public static Address in(Coded country) {
        return new Address(Objects.requireNonNull(country, "country"),
                null, null, null, null, null, null);
    }

    /**
     * Creates an address that states nothing but a country of the generated list (BT-40,
     * BT-55, BT-80).
     *
     * @param country the country
     * @return the address
     * @throws NullPointerException if {@code country} is {@code null}
     */
    public static Address in(Country country) {
        return in((Coded) country);
    }

    /**
     * Creates the address most invoices state: one street line, a post code, a city and a
     * country.
     *
     * @param line1    the main address line (BT-35, BT-50, BT-75)
     * @param postCode the post code (BT-38, BT-53, BT-78)
     * @param city     the city (BT-37, BT-52, BT-77)
     * @param country  the country (BT-40, BT-55, BT-80)
     * @return the address
     * @throws IllegalArgumentException if a string is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Address of(String line1, String postCode, String city, Coded country) {
        return in(country).line1(line1).postCode(postCode).city(city);
    }

    /**
     * Creates the address most invoices state, in a country of the generated list.
     *
     * @param line1    the main address line (BT-35, BT-50, BT-75)
     * @param postCode the post code (BT-38, BT-53, BT-78)
     * @param city     the city (BT-37, BT-52, BT-77)
     * @param country  the country (BT-40, BT-55, BT-80)
     * @return the address
     * @throws IllegalArgumentException if a string is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public static Address of(String line1, String postCode, String city, Country country) {
        return of(line1, postCode, city, (Coded) country);
    }

    /**
     * Returns this address with its main address line (BT-35, BT-50, BT-75).
     *
     * @param value the address line
     * @return a new address
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Address line1(String value) {
        return new Address(country, Amounts.text(value, "line1"), line2, line3, city, postCode,
                countrySubdivision);
    }

    /**
     * Returns this address with its second address line (BT-36, BT-51, BT-76).
     *
     * @param value the address line
     * @return a new address
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Address line2(String value) {
        return new Address(country, line1, Amounts.text(value, "line2"), line3, city, postCode,
                countrySubdivision);
    }

    /**
     * Returns this address with its third address line (BT-162, BT-163, BT-165).
     *
     * @param value the address line
     * @return a new address
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Address line3(String value) {
        return new Address(country, line1, line2, Amounts.text(value, "line3"), city, postCode,
                countrySubdivision);
    }

    /**
     * Returns this address with its city (BT-37, BT-52, BT-77).
     *
     * @param value the city
     * @return a new address
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Address city(String value) {
        return new Address(country, line1, line2, line3, Amounts.text(value, "city"), postCode,
                countrySubdivision);
    }

    /**
     * Returns this address with its post code (BT-38, BT-53, BT-78).
     *
     * @param value the post code
     * @return a new address
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Address postCode(String value) {
        return new Address(country, line1, line2, line3, city, Amounts.text(value, "postCode"),
                countrySubdivision);
    }

    /**
     * Returns this address with its country subdivision (BT-39, BT-54, BT-79).
     *
     * @param value the region, province or state
     * @return a new address
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Address countrySubdivision(String value) {
        return new Address(country, line1, line2, line3, city, postCode,
                Amounts.text(value, "countrySubdivision"));
    }

    /**
     * Returns the country.
     *
     * @return the country code, a constant of {@code Country} or a custom code
     */
    public Coded country() {
        return country;
    }

    /**
     * Returns the main address line.
     *
     * @return the address line, or an empty optional
     */
    public Optional<String> line1() {
        return Optional.ofNullable(line1);
    }

    /**
     * Returns the second address line.
     *
     * @return the address line, or an empty optional
     */
    public Optional<String> line2() {
        return Optional.ofNullable(line2);
    }

    /**
     * Returns the third address line.
     *
     * @return the address line, or an empty optional
     */
    public Optional<String> line3() {
        return Optional.ofNullable(line3);
    }

    /**
     * Returns the city.
     *
     * @return the city, or an empty optional
     */
    public Optional<String> city() {
        return Optional.ofNullable(city);
    }

    /**
     * Returns the post code.
     *
     * @return the post code, or an empty optional
     */
    public Optional<String> postCode() {
        return Optional.ofNullable(postCode);
    }

    /**
     * Returns the country subdivision.
     *
     * @return the region, province or state, or an empty optional
     */
    public Optional<String> countrySubdivision() {
        return Optional.ofNullable(countrySubdivision);
    }

    /**
     * Returns the address as one line.
     *
     * @return for example {@code Musterweg 12, 10117 Beispielstadt, DE}
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (String part : new String[] {line1, line2, line3}) {
            if (part != null) {
                text.append(part).append(", ");
            }
        }
        if (postCode != null) {
            text.append(postCode).append(' ');
        }
        if (city != null) {
            text.append(city).append(", ");
        }
        return text.append(country.code()).toString();
    }
}
