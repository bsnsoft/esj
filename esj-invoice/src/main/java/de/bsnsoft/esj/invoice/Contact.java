package de.bsnsoft.esj.invoice;

import java.util.Objects;
import java.util.Optional;

/**
 * A contact point of a party: BG-6 for the seller and BG-9 for the buyer.
 *
 * <p>Writes the contact point name (BT-41, BT-56), the telephone number (BT-42, BT-57)
 * and the email address (BT-43, BT-58). Each of the three is optional in EN 16931-1 and
 * all three are required of a seller by XRechnung; a contact that carries none of them is
 * refused rather than written as an empty group.
 *
 * <p>Two forms: {@link #of(String, String, String)} takes the three in that order, and
 * {@link #named(String)} with {@link #telephone(String)} and {@link #email(String)} names
 * each of them. The contact is a value: every method returns a new one.
 *
 * @param name      the contact point, department or person
 * @param telephone the telephone number
 * @param email     the email address
 */
public record Contact(Optional<String> name, Optional<String> telephone, Optional<String> email) {

    /**
     * Checks that every part is present and that the contact says something.
     *
     * @param name      the contact point, department or person
     * @param telephone the telephone number
     * @param email     the email address
     * @throws IllegalArgumentException if all three parts are empty
     * @throws NullPointerException     if a part is {@code null}
     */
    public Contact {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(telephone, "telephone");
        Objects.requireNonNull(email, "email");
        if (name.isEmpty() && telephone.isEmpty() && email.isEmpty()) {
            throw new IllegalArgumentException("a contact states a name (BT-41, BT-56), a telephone"
                    + " number (BT-42, BT-57) or an email address (BT-43, BT-58)");
        }
    }

    /**
     * Creates a contact point from three strings in the order name, telephone, email.
     *
     * <p>Nothing but the position tells the three apart, so a telephone number given as an
     * email address is written as it was given. {@link #named(String)} names each part.
     *
     * @param name      the contact point, department or person, or {@code null}
     * @param telephone the telephone number, or {@code null}
     * @param email     the email address, or {@code null}
     * @return the contact
     * @throws IllegalArgumentException if all three are {@code null}
     */
    public static Contact of(String name, String telephone, String email) {
        return new Contact(Optional.ofNullable(name),
                Optional.ofNullable(telephone),
                Optional.ofNullable(email));
    }

    /**
     * Creates a contact point that states its name (BT-41, BT-56), to which the telephone
     * number and the email address are added by the method that names them:
     *
     * <pre>{@code
     * Contact.named("Sales").telephone("+49 711 1234").email("sales@widget.example");
     * }</pre>
     *
     * @param name the contact point, department or person
     * @return the contact
     * @throws IllegalArgumentException if the name is blank
     * @throws NullPointerException     if {@code name} is {@code null}
     */
    public static Contact named(String name) {
        return new Contact(Optional.of(Amounts.text(name, "name")), Optional.empty(),
                Optional.empty());
    }

    /**
     * Returns this contact with a telephone number (BT-42, BT-57).
     *
     * @param value the telephone number
     * @return a new contact
     * @throws IllegalArgumentException if the number is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Contact telephone(String value) {
        return new Contact(name, Optional.of(Amounts.text(value, "value")), email);
    }

    /**
     * Returns this contact with an email address (BT-43, BT-58).
     *
     * @param value the email address
     * @return a new contact
     * @throws IllegalArgumentException if the address is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Contact email(String value) {
        return new Contact(name, telephone, Optional.of(Amounts.text(value, "value")));
    }
}
