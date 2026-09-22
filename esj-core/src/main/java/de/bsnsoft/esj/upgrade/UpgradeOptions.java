package de.bsnsoft.esj.upgrade;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.model.Registry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a caller decides about one upgrade.
 *
 * <p>Every option here exists because the run must not take that decision on its own. The
 * specification identifier is replaced only where the caller names the replacement; a
 * value is dropped only where the caller names its path; a result that does not satisfy
 * the model of the target edition is written only where the caller asked for a partial
 * one; and the provenance of the result is recorded only where the caller hands over the
 * bytes it derives from.
 *
 * <p>The defaults are {@link #defaults()}: nothing replaced, nothing dropped, nothing
 * partial, no provenance.
 */
public final class UpgradeOptions {

    private static final UpgradeOptions DEFAULTS = builder().build();

    private final String specification;
    private final List<SemanticPath> droppable;
    private final boolean strict;
    private final boolean partial;
    private final String sourceDigest;
    private final List<Registry> extensions;

    private UpgradeOptions(Builder builder) {
        this.specification = builder.specification;
        this.droppable = List.copyOf(builder.droppable);
        this.strict = builder.strict;
        this.partial = builder.partial;
        this.sourceDigest = builder.sourceDigest;
        this.extensions = List.copyOf(builder.extensions);
    }

    /**
     * Returns the options that decide nothing.
     *
     * @return the default options
     */
    public static UpgradeOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a builder.
     *
     * @return a builder holding the defaults
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the specification identifier the result carries at BT-24.
     *
     * @return the identifier, empty where the value of the source document stands
     */
    public Optional<String> specification() {
        return Optional.ofNullable(specification);
    }

    /**
     * Returns the paths the caller allows the run to drop.
     *
     * @return the paths; a group path covers everything under it
     */
    public List<SemanticPath> droppable() {
        return droppable;
    }

    /**
     * Tells whether only a transformation without an open point may be written.
     *
     * @return {@code true} where an open point is a refusal
     */
    public boolean strict() {
        return strict;
    }

    /**
     * Tells whether a result that does not satisfy the model of the target edition may be
     * written.
     *
     * @return {@code true} where such a result is written and reported rather than
     *         refused
     */
    public boolean partial() {
        return partial;
    }

    /**
     * Returns the digest of the bytes the result is derived from.
     *
     * @return 64 lowercase hexadecimal digits, empty where the run records no provenance
     */
    public Optional<String> sourceDigest() {
        return Optional.ofNullable(sourceDigest);
    }

    /**
     * Returns the extension registries the result is checked with.
     *
     * @return the registries, in the order they were named, empty where none was named
     */
    public List<Registry> extensions() {
        return extensions;
    }

    /**
     * Tells whether a path is one the caller allows the run to drop.
     *
     * @param path the path of a value of the source document
     * @return {@code true} where the caller named that path or a group above it
     */
    public boolean allowsDropping(SemanticPath path) {
        for (SemanticPath named : droppable) {
            if (path.startsWith(named)) {
                return true;
            }
        }
        return false;
    }

    /** Collects the decisions of one caller. */
    public static final class Builder {

        private final List<SemanticPath> droppable = new ArrayList<>();
        private String specification;
        private boolean strict;
        private boolean partial;
        private String sourceDigest;
        private final List<Registry> extensions = new ArrayList<>();

        private Builder() {
        }

        /**
         * Sets the specification identifier the result carries at BT-24, in place of the
         * one the source document carries.
         *
         * @param identifier the identifier of the specification the result claims
         * @return this builder
         * @throws NullPointerException if {@code identifier} is {@code null}
         */
        public Builder specification(String identifier) {
            this.specification = Objects.requireNonNull(identifier, "identifier");
            return this;
        }

        /**
         * Allows the run to drop the values at a path and under it.
         *
         * @param path a value path, or a group path covering everything under it
         * @return this builder
         * @throws NullPointerException if {@code path} is {@code null}
         */
        public Builder drop(SemanticPath path) {
            droppable.add(Objects.requireNonNull(path, "path"));
            return this;
        }

        /**
         * Asks for a refusal where the run leaves an open point.
         *
         * @param strict whether an open point is a refusal
         * @return this builder
         */
        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        /**
         * Allows a result that does not satisfy the model of the target edition for a
         * reason the mapping does not explain.
         *
         * @param partial whether such a result is written rather than refused
         * @return this builder
         */
        public Builder partial(boolean partial) {
            this.partial = partial;
            return this;
        }

        /**
         * Records the provenance of the result: the digest of the bytes it was derived
         * from (specification, section 4.7).
         *
         * @param bytes the bytes of the source document
         * @return this builder
         * @throws NullPointerException if {@code bytes} is {@code null}
         */
        public Builder source(byte[] bytes) {
            this.sourceDigest = sha256(Objects.requireNonNull(bytes, "bytes"));
            return this;
        }

        /**
         * Records the provenance of the result from a digest that was computed elsewhere.
         *
         * @param sha256 the SHA-256 of the bytes of the source document, as 64 lowercase
         *               hexadecimal digits
         * @return this builder
         * @throws NullPointerException if {@code sha256} is {@code null}
         */
        public Builder sourceDigest(String sha256) {
            this.sourceDigest = Objects.requireNonNull(sha256, "sha256");
            return this;
        }

        /**
         * Names an extension registry the result is checked with, where the target
         * edition has one. Calling it more than once names more than one extension, and
         * each of them is combined where it fits the target edition.
         *
         * @param registry the registry of the extension
         * @return this builder
         * @throws NullPointerException if {@code registry} is {@code null}
         */
        public Builder extension(Registry registry) {
            this.extensions.add(Objects.requireNonNull(registry, "registry"));
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the options
         */
        public UpgradeOptions build() {
            return new UpgradeOptions(this);
        }

        private static String sha256(byte[] bytes) {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("every Java runtime implements SHA-256", e);
            }
            StringBuilder text = new StringBuilder(64);
            for (byte b : digest.digest(bytes)) {
                text.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return text.toString();
        }
    }
}
