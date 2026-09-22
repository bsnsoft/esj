package de.bsnsoft.esj;

import java.util.Objects;

/**
 * One segment of a semantic path: either a term segment such as {@code BT-131} or
 * {@code BG-DEX-01}, or an occurrence index such as {@code 0}
 * (specification, section 5.1).
 */
public sealed interface PathSegment {

    /**
     * Returns the segment as it appears in a path, without the separating solidus.
     *
     * @return the text of this segment
     */
    String text();

    /**
     * Returns a term segment for a term of the core model.
     *
     * @param kind   business term or business group
     * @param number the number of the identifier, without leading zeros
     * @return the term segment
     * @throws EsjFormatException if the number is not a core term number
     */
    static Term core(TermKind kind, String number) {
        return new Term(kind, null, number);
    }

    /**
     * Returns a term segment for a term of an extension.
     *
     * @param kind      business term or business group
     * @param namespace the uppercase namespace token of the extension
     * @param number    the number of the identifier, exactly as the extension registry
     *                  writes it
     * @return the term segment
     * @throws EsjFormatException if the namespace or the number is not well formed
     */
    static Term extension(TermKind kind, String namespace, String number) {
        return new Term(kind, Objects.requireNonNull(namespace, "namespace"), number);
    }

    /**
     * Returns an occurrence index segment.
     *
     * @param index the zero-based index
     * @return the index segment
     * @throws IllegalArgumentException if {@code index} is negative
     */
    static Index index(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("an occurrence index is not negative: " + index);
        }
        return new Index(Integer.toString(index));
    }

    /**
     * A term segment: the identifier of a business term or business group, with the
     * namespace of an extension where the term is not one of the core model.
     *
     * @param kind      business term or business group
     * @param namespace the uppercase namespace token of an extension, or {@code null} for
     *                  a term of the core model
     * @param number    the digits of the identifier; without leading zeros for a core
     *                  term, verbatim for an extension term
     */
    record Term(TermKind kind, String namespace, String number) implements PathSegment {

        /**
         * Validates the parts of the segment against the grammar of the specification,
         * section 5.1.
         *
         * @param kind      business term or business group
         * @param namespace the uppercase namespace token of an extension, or {@code null} for
         *                  a term of the core model
         * @param number    the digits of the identifier; without leading zeros for a core
         *                  term, verbatim for an extension term
         * @throws EsjFormatException if a part does not match the grammar
         */
        public Term {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(number, "number");
            if (namespace != null && !isNamespace(namespace)) {
                throw new EsjFormatException("not an extension namespace: " + namespace);
            }
            if (namespace == null) {
                if (!isCoreNumber(number)) {
                    throw new EsjFormatException(
                            "not a core term number, which has at least one digit and no leading zero: " + number);
                }
            } else if (!isExtensionNumber(number)) {
                throw new EsjFormatException(
                        "not an extension term number, which is one or more digits: " + number);
            }
        }

        /**
         * Returns the identifier of the term, for example {@code BT-131} or
         * {@code BT-DEX-001}.
         *
         * @return the term identifier
         */
        public String id() {
            return namespace == null
                    ? kind.prefix() + "-" + number
                    : kind.prefix() + "-" + namespace + "-" + number;
        }

        /**
         * Tells whether this segment names a term of an extension rather than of the core
         * model.
         *
         * @return {@code true} if the segment carries a namespace
         */
        public boolean isExtension() {
            return namespace != null;
        }

        @Override
        public String text() {
            return id();
        }

        @Override
        public String toString() {
            return id();
        }
    }

    /**
     * An occurrence index segment: a zero-based ordinal that distinguishes the
     * occurrences of a repeatable term or group (specification, section 5.3).
     *
     * @param digits the decimal digits of the index, without leading zeros
     */
    record Index(String digits) implements PathSegment {

        /**
         * Validates the digits against the {@code index} production of the specification,
         * section 5.1.
         *
         * @param digits the decimal digits of the index, without leading zeros
         * @throws EsjFormatException if the digits are empty or carry a leading zero
         */
        public Index {
            Objects.requireNonNull(digits, "digits");
            if (!isIndex(digits)) {
                throw new EsjFormatException(
                        "not an occurrence index, which is 0 or a digit string without a leading zero: " + digits);
            }
        }

        /**
         * Returns the index as an {@code int}.
         *
         * @return the zero-based index
         * @throws EsjLimitException if the index does not fit in an {@code int}; the
         *                           grammar puts no bound on the number of digits
         */
        public int value() {
            if (digits.length() > 9) {
                if (digits.length() <= 18) {
                    long wide = Long.parseLong(digits);
                    if (wide <= Integer.MAX_VALUE) {
                        return (int) wide;
                    }
                }
                throw new EsjLimitException("occurrence index does not fit in an int: " + digits);
            }
            return Integer.parseInt(digits);
        }

        @Override
        public String text() {
            return digits;
        }

        @Override
        public String toString() {
            return digits;
        }
    }

    /**
     * Tells whether a string is a core term number.
     *
     * @param value the string to test
     * @return {@code true} if the string is one or more digits without a leading zero
     */
    static boolean isCoreNumber(String value) {
        if (value.isEmpty() || value.charAt(0) == '0') {
            return false;
        }
        return isDigits(value);
    }

    /**
     * Tells whether a string is an extension term number.
     *
     * @param value the string to test
     * @return {@code true} if the string is one or more digits
     */
    static boolean isExtensionNumber(String value) {
        return !value.isEmpty() && isDigits(value);
    }

    /**
     * Tells whether a string is an extension namespace token.
     *
     * @param value the string to test
     * @return {@code true} if the string starts with an uppercase ASCII letter and
     *         continues with uppercase ASCII letters and digits
     */
    static boolean isNamespace(String value) {
        if (value.isEmpty() || !isUpper(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!isUpper(c) && !isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tells whether a string is an occurrence index.
     *
     * @param value the string to test
     * @return {@code true} if the string is {@code "0"} or a digit string without a
     *         leading zero
     */
    static boolean isIndex(String value) {
        if (value.isEmpty() || !isDigits(value)) {
            return false;
        }
        return value.length() == 1 || value.charAt(0) != '0';
    }

    /**
     * Tells whether a string consists of ASCII digits only.
     *
     * @param value the string to test
     * @return {@code true} if the string is not empty and every character is a digit
     */
    static boolean isDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isUpper(char c) {
        return c >= 'A' && c <= 'Z';
    }
}
