package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.Country;
import de.bsnsoft.esj.invoice.code.ElectronicAddressScheme;
import de.bsnsoft.esj.typed.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A party of an invoice: the seller (BG-4), the buyer (BG-7) or the payee (BG-10).
 *
 * <p>One type for the three groups, because the questions a caller answers about them are
 * the same — who is this, under which numbers is it registered, where does it sit, whom
 * does one write to. Which of the answers a group has a term for differs, and a part the
 * group it is written into has no term for is refused by name rather than dropped: the
 * payee has a name (BT-59), an identifier (BT-60) and a legal registration identifier
 * (BT-61) and nothing else, the buyer has no tax registration identifier, and only the
 * seller has BT-32 and BT-33.
 *
 * <p>The party is a value: every method returns a new one and the original is unchanged.
 */
public final class Party {

    private final String name;

    private final String tradingName;

    private final List<Identifier> identifiers;

    private final Identifier legalRegistration;

    private final String vatId;

    private final String taxRegistration;

    private final String additionalLegalInformation;

    private final ElectronicAddress electronicAddress;

    private final Contact contact;

    private final Address address;

    private Party(String name, String tradingName, List<Identifier> identifiers,
                  Identifier legalRegistration, String vatId, String taxRegistration,
                  String additionalLegalInformation, ElectronicAddress electronicAddress,
                  Contact contact, Address address) {
        this.name = name;
        this.tradingName = tradingName;
        this.identifiers = identifiers;
        this.legalRegistration = legalRegistration;
        this.vatId = vatId;
        this.taxRegistration = taxRegistration;
        this.additionalLegalInformation = additionalLegalInformation;
        this.electronicAddress = electronicAddress;
        this.contact = contact;
        this.address = address;
    }

    /**
     * Creates a party under its registered name (BT-27, BT-44, BT-59).
     *
     * @param name the name
     * @return the party
     * @throws IllegalArgumentException if the name is blank
     * @throws NullPointerException     if {@code name} is {@code null}
     */
    public static Party named(String name) {
        return new Party(Amounts.text(name, "name"), null, List.of(), null, null, null, null, null,
                null, null);
    }

    /**
     * Returns this party with its trading name (BT-28, BT-45), the name it does business
     * under where that is not its registered name.
     *
     * @param value the trading name
     * @return a new party
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Party tradingName(String value) {
        return copy().tradingName(Amounts.text(value, "tradingName")).party();
    }

    /**
     * Returns this party with one more identifier (BT-29, BT-46, BT-60).
     *
     * @param value the identifier
     * @return a new party
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Party identifier(String value) {
        return identifier(Identifier.of(Amounts.text(value, "identifier")));
    }

    /**
     * Returns this party with one more identifier and the scheme it is to be read under
     * (BT-29-1, BT-46-1, BT-60-1).
     *
     * @param value  the identifier
     * @param scheme the identification scheme, a code of ISO 6523 ICD
     * @return a new party
     * @throws IllegalArgumentException if a value is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public Party identifier(String value, String scheme) {
        return identifier(Identifier.of(Amounts.text(value, "identifier"),
                Amounts.text(scheme, "scheme")));
    }

    /**
     * Returns this party with one more identifier (BT-29, BT-46, BT-60).
     *
     * @param value the identifier, with or without its scheme
     * @return a new party
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Party identifier(Identifier value) {
        List<Identifier> more = new ArrayList<>(identifiers);
        more.add(Objects.requireNonNull(value, "value"));
        return copy().identifiers(List.copyOf(more)).party();
    }

    /**
     * Returns this party with its legal registration identifier (BT-30, BT-47, BT-61),
     * the number it is entered in a commercial register under.
     *
     * @param value the identifier
     * @return a new party
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Party legalRegistration(String value) {
        return legalRegistration(Identifier.of(Amounts.text(value, "legalRegistration")));
    }

    /**
     * Returns this party with its legal registration identifier and the register it is
     * entered in (BT-30-1, BT-47-1, BT-61-1).
     *
     * @param value  the identifier
     * @param scheme the identification scheme, a code of ISO 6523 ICD
     * @return a new party
     * @throws IllegalArgumentException if a value is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public Party legalRegistration(String value, String scheme) {
        return legalRegistration(Identifier.of(Amounts.text(value, "legalRegistration"),
                Amounts.text(scheme, "scheme")));
    }

    /**
     * Returns this party with its legal registration identifier (BT-30, BT-47, BT-61).
     *
     * @param value the identifier, with or without its scheme
     * @return a new party
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Party legalRegistration(Identifier value) {
        return copy().legalRegistration(Objects.requireNonNull(value, "value")).party();
    }

    /**
     * Returns this party with its VAT identifier (BT-31, BT-48), which a VAT category
     * under which VAT is levied asks of the seller.
     *
     * @param value the VAT identifier, with its country prefix
     * @return a new party
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Party vatId(String value) {
        return copy().vatId(Amounts.text(value, "vatId")).party();
    }

    /**
     * Returns this party with its tax registration identifier (BT-32), a registration
     * other than the VAT one. Only the seller has this term.
     *
     * @param value the identifier
     * @return a new party
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Party taxRegistration(String value) {
        return copy().taxRegistration(Amounts.text(value, "taxRegistration")).party();
    }

    /**
     * Returns this party with the legal information its invoices have to carry (BT-33),
     * for example the court of its commercial register. Only the seller has this term.
     *
     * @param value the text
     * @return a new party
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Party additionalLegalInformation(String value) {
        return copy().additionalLegalInformation(Amounts.text(value, "additionalLegalInformation"))
                .party();
    }

    /**
     * Returns this party with its electronic address (BT-34, BT-49) and the scheme that
     * address is to be read under (BT-34-1, BT-49-1).
     *
     * @param value  the address
     * @param scheme the identification scheme, for example {@code
     *               ElectronicAddressScheme.ELECTRONIC_MAIL}
     * @return a new party
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public Party electronicAddress(String value, Coded scheme) {
        return electronicAddress(ElectronicAddress.of(value, scheme));
    }

    /**
     * Returns this party with its electronic address (BT-34, BT-49) under a scheme of the
     * generated list.
     *
     * @param value  the address
     * @param scheme the identification scheme
     * @return a new party
     * @throws IllegalArgumentException if the value is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public Party electronicAddress(String value, ElectronicAddressScheme scheme) {
        return electronicAddress(ElectronicAddress.of(value, scheme));
    }

    /**
     * Returns this party with its electronic address (BT-34, BT-49).
     *
     * @param value the address and its scheme
     * @return a new party
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Party electronicAddress(ElectronicAddress value) {
        return copy().electronicAddress(Objects.requireNonNull(value, "value")).party();
    }

    /**
     * Returns this party with a contact point (BG-6, BG-9): the name (BT-41, BT-56), the
     * telephone number (BT-42, BT-57) and the email address (BT-43, BT-58).
     *
     * <p>Three adjacent strings in the order name, telephone, email; nothing but the order
     * tells them apart, so a swapped telephone and email is written as it was given.
     * {@code contact(Contact.named(name).telephone(...).email(...))} names each of them.
     *
     * @param name      the contact point, department or person, or {@code null}
     * @param telephone the telephone number, or {@code null}
     * @param email     the email address, or {@code null}
     * @return a new party
     * @throws IllegalArgumentException if all three are {@code null}
     */
    public Party contact(String name, String telephone, String email) {
        return contact(Contact.of(name, telephone, email));
    }

    /**
     * Returns this party with a contact point (BG-6, BG-9).
     *
     * @param value the contact
     * @return a new party
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Party contact(Contact value) {
        return copy().contact(Objects.requireNonNull(value, "value")).party();
    }

    /**
     * Returns this party with its postal address (BG-5, BG-8).
     *
     * <p>The post code and the city are two adjacent strings, and the compiler cannot tell
     * them apart: {@code Address.in(Country.DE).line1(...).postCode(...).city(...)} names
     * each part and is the safer form where an address is assembled from fields of another
     * model.
     *
     * @param line1    the main address line (BT-35, BT-50)
     * @param postCode the post code (BT-38, BT-53)
     * @param city     the city (BT-37, BT-52)
     * @param country  the country (BT-40, BT-55)
     * @return a new party
     * @throws IllegalArgumentException if a string is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public Party address(String line1, String postCode, String city, Coded country) {
        return address(Address.of(line1, postCode, city, country));
    }

    /**
     * Returns this party with its postal address (BG-5, BG-8), in a country of the
     * generated list.
     *
     * @param line1    the main address line (BT-35, BT-50)
     * @param postCode the post code (BT-38, BT-53)
     * @param city     the city (BT-37, BT-52)
     * @param country  the country (BT-40, BT-55)
     * @return a new party
     * @throws IllegalArgumentException if a string is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public Party address(String line1, String postCode, String city, Country country) {
        return address(Address.of(line1, postCode, city, country));
    }

    /**
     * Returns this party with its postal address (BG-5, BG-8).
     *
     * @param value the address
     * @return a new party
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Party address(Address value) {
        return copy().address(Objects.requireNonNull(value, "value")).party();
    }

    /**
     * Returns the registered name (BT-27, BT-44, BT-59).
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the trading name (BT-28, BT-45).
     *
     * @return the trading name, or an empty optional
     */
    public Optional<String> tradingName() {
        return Optional.ofNullable(tradingName);
    }

    /**
     * Returns the identifiers (BT-29, BT-46, BT-60).
     *
     * @return the identifiers, in the order they were written, empty where there are none
     */
    public List<Identifier> identifiers() {
        return identifiers;
    }

    /**
     * Returns the legal registration identifier (BT-30, BT-47, BT-61).
     *
     * @return the identifier, or an empty optional
     */
    public Optional<Identifier> legalRegistration() {
        return Optional.ofNullable(legalRegistration);
    }

    /**
     * Returns the VAT identifier (BT-31, BT-48).
     *
     * @return the VAT identifier, or an empty optional
     */
    public Optional<String> vatId() {
        return Optional.ofNullable(vatId);
    }

    /**
     * Returns the tax registration identifier (BT-32).
     *
     * @return the identifier, or an empty optional
     */
    public Optional<String> taxRegistration() {
        return Optional.ofNullable(taxRegistration);
    }

    /**
     * Returns the additional legal information (BT-33).
     *
     * @return the text, or an empty optional
     */
    public Optional<String> additionalLegalInformation() {
        return Optional.ofNullable(additionalLegalInformation);
    }

    /**
     * Returns the electronic address (BT-34, BT-49).
     *
     * @return the address and its scheme, or an empty optional
     */
    public Optional<ElectronicAddress> electronicAddress() {
        return Optional.ofNullable(electronicAddress);
    }

    /**
     * Returns the contact point (BG-6, BG-9).
     *
     * @return the contact, or an empty optional
     */
    public Optional<Contact> contact() {
        return Optional.ofNullable(contact);
    }

    /**
     * Returns the postal address (BG-5, BG-8, BG-15).
     *
     * @return the address, or an empty optional
     */
    public Optional<Address> address() {
        return Optional.ofNullable(address);
    }

    /**
     * Returns the name of the party and, where it has one, its VAT identifier.
     *
     * @return for example {@code Example GmbH (DE123456789)}
     */
    @Override
    public String toString() {
        return vatId == null ? name : name + " (" + vatId + ")";
    }

    private Copy copy() {
        return new Copy(this);
    }

    /** The one place a part of a party is replaced, so that no setter lists ten fields. */
    private static final class Copy {

        private String name;
        private String tradingName;
        private List<Identifier> identifiers;
        private Identifier legalRegistration;
        private String vatId;
        private String taxRegistration;
        private String additionalLegalInformation;
        private ElectronicAddress electronicAddress;
        private Contact contact;
        private Address address;

        Copy(Party party) {
            this.name = party.name;
            this.tradingName = party.tradingName;
            this.identifiers = party.identifiers;
            this.legalRegistration = party.legalRegistration;
            this.vatId = party.vatId;
            this.taxRegistration = party.taxRegistration;
            this.additionalLegalInformation = party.additionalLegalInformation;
            this.electronicAddress = party.electronicAddress;
            this.contact = party.contact;
            this.address = party.address;
        }

        Copy tradingName(String value) {
            this.tradingName = value;
            return this;
        }

        Copy identifiers(List<Identifier> value) {
            this.identifiers = value;
            return this;
        }

        Copy legalRegistration(Identifier value) {
            this.legalRegistration = value;
            return this;
        }

        Copy vatId(String value) {
            this.vatId = value;
            return this;
        }

        Copy taxRegistration(String value) {
            this.taxRegistration = value;
            return this;
        }

        Copy additionalLegalInformation(String value) {
            this.additionalLegalInformation = value;
            return this;
        }

        Copy electronicAddress(ElectronicAddress value) {
            this.electronicAddress = value;
            return this;
        }

        Copy contact(Contact value) {
            this.contact = value;
            return this;
        }

        Copy address(Address value) {
            this.address = value;
            return this;
        }

        Party party() {
            return new Party(name, tradingName, identifiers, legalRegistration, vatId,
                    taxRegistration, additionalLegalInformation, electronicAddress, contact,
                    address);
        }
    }
}
