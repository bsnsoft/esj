package de.bsnsoft.esj.invoice.code;

import java.util.Objects;

/**
 * A code that the snapshot the enums of this package were generated from does not carry.
 *
 * <p>A code list outlives the snapshot a release froze. When a publisher adds a code, an
 * invoice that carries it is still an invoice, and refusing to represent it would make the
 * SDK the reason a document cannot be written. So every enum has a {@code custom(String)}
 * factory that yields one of these, and every enum refuses to yield one for a code it does
 * carry, so that a code has one representation and not two.
 *
 * @param type the enum the code was written for, for example {@code Unit}
 * @param code the code
 */
public record CustomCode(String type, String code) implements Coded {

    /**
     * Checks that both parts are present and that the code is not blank.
     *
     * @param type the enum the code was written for, for example {@code Unit}
     * @param code the code
     * @throws NullPointerException     if a part is {@code null}
     * @throws IllegalArgumentException if the code is blank
     */
    public CustomCode {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(code, "code");
        if (code.isBlank()) {
            throw new IllegalArgumentException("a code is not blank");
        }
    }

    /**
     * Returns the empty string: a code outside the snapshot has no name from it.
     *
     * @return the empty string
     */
    @Override
    public String publishedName() {
        return "";
    }

    /**
     * Returns the empty string: the code came from no snapshot.
     *
     * @return the empty string
     */
    @Override
    public String listId() {
        return "";
    }

    /**
     * Returns the code and the enum it was written for.
     *
     * @return for example {@code XYZ (Unit, outside the snapshot)}
     */
    @Override
    public String toString() {
        return code + " (" + type + ", outside the snapshot)";
    }
}
