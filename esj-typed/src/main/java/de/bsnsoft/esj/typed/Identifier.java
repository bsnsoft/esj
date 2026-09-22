package de.bsnsoft.esj.typed;

import java.util.Objects;
import java.util.Optional;

/**
 * An identifier as the typed view hands it over: the identifier itself, and the
 * supplementary components the registry lists for the business term it was read at — the
 * identification scheme and the version of that scheme (specification, section 6.6).
 *
 * <p>A document writes an identifier without a scheme as a plain JSON string and one with
 * a scheme as an object, and this record is the same value either way: a scheme the
 * document does not carry is an empty optional rather than a second shape the caller has
 * to branch on.
 *
 * @param value         the identifier, never empty
 * @param scheme        the identification scheme, or an empty optional
 * @param schemeVersion the version of that scheme, or an empty optional
 */
public record Identifier(String value, Optional<String> scheme, Optional<String> schemeVersion) {

    /**
     * Checks that every part is present.
     *
     * @param value         the identifier, never empty
     * @param scheme        the identification scheme, or an empty optional
     * @param schemeVersion the version of that scheme, or an empty optional
     * @throws NullPointerException if a part is {@code null}
     */
    public Identifier {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(schemeVersion, "schemeVersion");
    }

    /**
     * Creates an identifier that carries no supplementary component.
     *
     * @param value the identifier
     * @return the identifier
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static Identifier of(String value) {
        return new Identifier(value, Optional.empty(), Optional.empty());
    }

    /**
     * Creates an identifier qualified by a scheme.
     *
     * @param value  the identifier
     * @param scheme the identification scheme
     * @return the identifier
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Identifier of(String value, String scheme) {
        return new Identifier(value, Optional.of(scheme), Optional.empty());
    }

    /**
     * Creates an identifier qualified by a scheme and by the version of that scheme.
     *
     * @param value         the identifier
     * @param scheme        the identification scheme
     * @param schemeVersion the version of that scheme
     * @return the identifier
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Identifier of(String value, String scheme, String schemeVersion) {
        return new Identifier(value, Optional.of(scheme), Optional.of(schemeVersion));
    }
}
