package de.bsnsoft.esj;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * The content of one member of {@code extensions}: arbitrary JSON, held as an immutable
 * tree (specification, section 4.6).
 *
 * <p>The tree is deliberately small and carries no parser types. A number is a
 * {@link BigDecimal}, which keeps every digit the sender wrote: ESJ never converts a
 * number to binary floating point, in {@code extensions} no more than anywhere else
 * (specification, section 7.6). The members of an object are held in Unicode code point
 * order, which is the order the canonical form and the pretty form both use.
 *
 * <p>A reader must treat this subtree as opaque data: it is never interpreted, executed
 * or resolved (specification, section 12.4).
 *
 * <p>A tree built through this interface may nest deeper than the reader of a document
 * would have accepted, because the nesting bound of the specification, section 12.2 is a
 * reader limit and not a rule about the model. {@link ObjectValue} and {@link ArrayValue}
 * therefore compare, hash and print themselves with an explicit deque rather than by
 * recursion, so that none of the three answers a deep tree with a
 * {@link StackOverflowError}.
 */
public sealed interface ExtensionValue {

    /**
     * Returns the comparator for member names inside {@code extensions}: Unicode code
     * point order, which is also the lexicographic order of the UTF-8 bytes the canonical
     * form is made of (specification, section 7.6).
     *
     * @return the member name order
     */
    static Comparator<String> memberOrder() {
        return CodePoints::compare;
    }

    /**
     * Wraps a string.
     *
     * @param value the string
     * @return the extension value
     */
    static StringValue of(String value) {
        return new StringValue(value);
    }

    /**
     * Wraps a number.
     *
     * @param value the number
     * @return the extension value
     */
    static NumberValue of(BigDecimal value) {
        return new NumberValue(value);
    }

    /**
     * Wraps a boolean.
     *
     * @param value the boolean
     * @return the extension value
     */
    static BooleanValue of(boolean value) {
        return new BooleanValue(value);
    }

    /**
     * Returns the JSON null.
     *
     * @return the extension value that stands for {@code null}
     */
    static NullValue nullValue() {
        return NullValue.NULL;
    }

    /**
     * Wraps an object.
     *
     * @param members the members, which are copied and sorted by code point
     * @return the extension value
     */
    static ObjectValue object(Map<String, ExtensionValue> members) {
        return new ObjectValue(members);
    }

    /**
     * Wraps an array.
     *
     * @param elements the elements, which are copied and keep their order
     * @return the extension value
     */
    static ArrayValue array(List<ExtensionValue> elements) {
        return new ArrayValue(elements);
    }

    /**
     * A JSON object.
     *
     * @param members the members, in Unicode code point order of their names
     */
    record ObjectValue(Map<String, ExtensionValue> members) implements ExtensionValue {

        /**
         * Copies the members into a map ordered by code point.
         *
         * @param members the members, in Unicode code point order of their names
         * @throws NullPointerException if a name or a member is {@code null}
         */
        public ObjectValue {
            Objects.requireNonNull(members, "members");
            TreeMap<String, ExtensionValue> sorted = new TreeMap<>(memberOrder());
            members.forEach((name, value) -> sorted.put(
                    Objects.requireNonNull(name, "member name"),
                    Objects.requireNonNull(value, "member value")));
            members = Collections.unmodifiableMap(sorted);
        }

        /**
         * Compares two objects member by member, walking both trees with an explicit
         * deque.
         *
         * @param other the object to compare with
         * @return {@code true} if the two trees hold the same members in the same order
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof ObjectValue && Trees.equal(this, (ObjectValue) other);
        }

        /**
         * Returns a hash over the whole tree, computed with an explicit deque.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Trees.hash(this);
        }

        /**
         * Returns the tree in the shape a record prints, written with an explicit deque.
         *
         * @return the printed tree
         */
        @Override
        public String toString() {
            return Trees.print(this);
        }
    }

    /**
     * A JSON array.
     *
     * @param elements the elements, in the order the document gives them
     */
    record ArrayValue(List<ExtensionValue> elements) implements ExtensionValue {

        /**
         * Copies the elements.
         *
         * @param elements the elements, in the order the document gives them
         * @throws NullPointerException if an element is {@code null}
         */
        public ArrayValue {
            elements = List.copyOf(elements);
        }

        /**
         * Compares two arrays element by element, walking both trees with an explicit
         * deque.
         *
         * @param other the array to compare with
         * @return {@code true} if the two trees hold the same elements in the same order
         */
        @Override
        public boolean equals(Object other) {
            return other instanceof ArrayValue && Trees.equal(this, (ArrayValue) other);
        }

        /**
         * Returns a hash over the whole tree, computed with an explicit deque.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Trees.hash(this);
        }

        /**
         * Returns the tree in the shape a record prints, written with an explicit deque.
         *
         * @return the printed tree
         */
        @Override
        public String toString() {
            return Trees.print(this);
        }
    }

    /**
     * A JSON string. Strings inside {@code extensions} are passed through unchanged; the
     * line ending normalization of the specification, section 6.8 does not reach them.
     *
     * @param value the string
     */
    record StringValue(String value) implements ExtensionValue {

        /**
         * Requires a string.
         *
         * @param value the string
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public StringValue {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON number, held exactly as the document spells it, digit for digit.
     *
     * @param value the number
     */
    record NumberValue(BigDecimal value) implements ExtensionValue {

        /**
         * Requires a number.
         *
         * @param value the number
         * @throws NullPointerException if {@code value} is {@code null}
         */
        public NumberValue {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON boolean.
     *
     * @param value the boolean
     */
    record BooleanValue(boolean value) implements ExtensionValue {
    }

    /** The JSON null. */
    enum NullValue implements ExtensionValue {

        /** The single instance. */
        NULL
    }
}
