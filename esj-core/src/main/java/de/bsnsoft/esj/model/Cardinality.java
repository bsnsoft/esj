package de.bsnsoft.esj.model;

/**
 * The declared cardinality of a business term or business group: how often it may occur
 * inside one instance of its parent.
 *
 * <p>The declared maximum decides the shape of a path: a term or group that may occur
 * more than once always carries an occurrence index, whatever a particular document
 * happens to contain (specification, section 5.3).
 *
 * @param min the minimum number of occurrences; 1 means the term is mandatory
 * @param max the maximum number of occurrences, or {@link #UNBOUNDED} for the {@code n}
 *            of the registry
 */
public record Cardinality(int min, int max) {

    /** The maximum of a term the registry declares as {@code n}. */
    public static final int UNBOUNDED = Integer.MAX_VALUE;

    /**
     * Checks that the two numbers form a cardinality.
     *
     * @param min the minimum number of occurrences; 1 means the term is mandatory
     * @param max the maximum number of occurrences, or {@link #UNBOUNDED} for the {@code n}
     *            of the registry
     * @throws IllegalArgumentException if the minimum is negative or the maximum is below
     *                                  1 or below the minimum
     */
    public Cardinality {
        if (min < 0) {
            throw new IllegalArgumentException("a minimum cardinality is not negative: " + min);
        }
        if (max < 1 || max < min) {
            throw new IllegalArgumentException("a maximum cardinality of " + max + " is below the minimum " + min);
        }
    }

    /**
     * Returns a cardinality with a bounded maximum.
     *
     * @param min the minimum number of occurrences
     * @param max the maximum number of occurrences
     * @return the cardinality
     */
    public static Cardinality of(int min, int max) {
        return new Cardinality(min, max);
    }

    /**
     * Returns a cardinality the registry writes with {@code n} as its maximum.
     *
     * @param min the minimum number of occurrences
     * @return the cardinality
     */
    public static Cardinality unbounded(int min) {
        return new Cardinality(min, UNBOUNDED);
    }

    /**
     * Tells whether the term or group must be present.
     *
     * @return {@code true} if the minimum is at least 1
     */
    public boolean isMandatory() {
        return min >= 1;
    }

    /**
     * Tells whether the term or group may occur more than once and therefore carries an
     * occurrence index in every path (specification, section 5.3).
     *
     * @return {@code true} if the maximum is greater than 1
     */
    public boolean isRepeatable() {
        return max > 1;
    }

    /**
     * Tells whether the registry declares the maximum as {@code n}.
     *
     * @return {@code true} if the maximum is unbounded
     */
    public boolean isUnbounded() {
        return max == UNBOUNDED;
    }

    /**
     * Returns the cardinality in the notation of the registry, for example {@code 1..n}.
     *
     * @return the cardinality as text
     */
    @Override
    public String toString() {
        return min + ".." + (isUnbounded() ? "n" : Integer.toString(max));
    }
}
