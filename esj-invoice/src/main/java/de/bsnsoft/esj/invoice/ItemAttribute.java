package de.bsnsoft.esj.invoice;

import java.util.Objects;

/**
 * One attribute of the invoiced item (BG-32): a property of the item stated as a name
 * (BT-160) and a value (BT-161), both free text.
 *
 * <p>{@link Line#attribute(String, String)} writes one and {@link Line#attributes()}
 * returns them, in the order the line states them. The cross industry invoice carries an
 * attribute as {@code ram:ApplicableProductCharacteristic} with {@code ram:Description}
 * and {@code ram:Value}, UBL as {@code cac:AdditionalItemProperty} with {@code cbc:Name}
 * and {@code cbc:Value}.
 *
 * @param name  the item attribute name (BT-160)
 * @param value the item attribute value (BT-161)
 */
public record ItemAttribute(String name, String value) {

    /**
     * Checks that both parts are present. They are kept as the line states them: an
     * attribute read from a document is not refused for its text.
     *
     * @param name  the item attribute name (BT-160)
     * @param value the item attribute value (BT-161)
     * @throws NullPointerException if a part is {@code null}
     */
    public ItemAttribute {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
    }

    /**
     * Returns the attribute as a reader of the invoice sees it.
     *
     * @return for example {@code Colour: blue}
     */
    @Override
    public String toString() {
        return name + ": " + value;
    }
}
