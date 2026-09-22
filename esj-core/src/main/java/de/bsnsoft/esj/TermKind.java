package de.bsnsoft.esj;

import java.util.Objects;

/**
 * The two kinds of identifier the semantic model uses: a business term that carries a
 * value, and a business group that encloses terms and other groups.
 */
public enum TermKind {

    /** A business term, written {@code BT-n}; it carries a value. */
    BT("BT", 0),

    /** A business group, written {@code BG-n}; it encloses terms and other groups. */
    BG("BG", 1);

    private final String prefix;
    private final int canonicalRank;

    TermKind(String prefix, int canonicalRank) {
        this.prefix = prefix;
        this.canonicalRank = canonicalRank;
    }

    /**
     * Returns the two letters this kind is written with.
     *
     * @return {@code "BT"} or {@code "BG"}
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Returns the rank of this kind in the canonical path order, where a business term
     * sorts before a business group (specification, section 7.4, rule 1.1).
     *
     * @return {@code 0} for {@code BT} and {@code 1} for {@code BG}
     */
    public int canonicalRank() {
        return canonicalRank;
    }

    /**
     * Returns the kind written with the given two letters.
     *
     * @param prefix the two letters of a term identifier
     * @return the matching kind
     * @throws EsjFormatException   if the letters are neither {@code BT} nor {@code BG}
     * @throws NullPointerException if {@code prefix} is {@code null}
     */
    public static TermKind fromPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        for (TermKind kind : values()) {
            if (kind.prefix.equals(prefix)) {
                return kind;
            }
        }
        throw new EsjFormatException("not a term kind: " + prefix);
    }
}
