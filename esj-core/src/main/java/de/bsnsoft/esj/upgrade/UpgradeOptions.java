package de.bsnsoft.esj.upgrade;

import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.model.MinorUnits;
import de.bsnsoft.esj.model.Registry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
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

    private static final UpgradeOptions DEFAULTS = new UpgradeOptions(null, List.of(),
            false, false, null, List.of(), null);

    private final String specification;
    private final List<SemanticPath> droppable;
    private final boolean strict;
    private final boolean partial;
    private final String sourceDigest;
    private final List<Registry> extensions;
    private final MinorUnits minorUnits;

    private UpgradeOptions(String specification, List<SemanticPath> droppable, boolean strict,
                           boolean partial, String sourceDigest, List<Registry> extensions,
                           MinorUnits minorUnits) {
        this.specification = specification;
        this.droppable = List.copyOf(droppable);
        this.strict = strict;
        this.partial = partial;
        this.sourceDigest = sourceDigest;
        this.extensions = List.copyOf(extensions);
        this.minorUnits = minorUnits;
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
     * Returns the minor units a fraction digit bound that follows the currency is evaluated
     * with.
     *
     * @return the minor units the caller handed over, empty where the run leaves such a
     *         bound unevaluated
     */
    public Optional<MinorUnits> minorUnits() {
        return Optional.ofNullable(minorUnits);
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

    /**
     * Returns these options with the specification identifier the result carries at
     * BT-24, in place of the one the source document carries.
     *
     * @param identifier the identifier of the specification the result claims
     * @return the options
     * @throws NullPointerException if {@code identifier} is {@code null}
     */
    public UpgradeOptions withSpecification(String identifier) {
        return new UpgradeOptions(Objects.requireNonNull(identifier, "identifier"), droppable,
                strict, partial, sourceDigest, extensions, minorUnits);
    }

    /**
     * Returns these options with the paths the run may drop the values at and under,
     * replacing any named before.
     *
     * @param paths value paths, or group paths covering everything under them
     * @return the options
     * @throws NullPointerException if {@code paths} is or holds {@code null}
     */
    public UpgradeOptions withDroppable(Collection<SemanticPath> paths) {
        return new UpgradeOptions(specification,
                List.copyOf(Objects.requireNonNull(paths, "paths")), strict, partial,
                sourceDigest, extensions, minorUnits);
    }

    /**
     * Returns these options asking, or not asking, for a refusal where the run leaves an
     * open point.
     *
     * @param value whether an open point is a refusal
     * @return the options
     */
    public UpgradeOptions withStrict(boolean value) {
        return new UpgradeOptions(specification, droppable, value, partial, sourceDigest,
                extensions, minorUnits);
    }

    /**
     * Returns these options allowing, or not allowing, a result that does not satisfy the
     * model of the target edition for a reason the mapping does not explain.
     *
     * @param value whether such a result is written rather than refused
     * @return the options
     */
    public UpgradeOptions withPartial(boolean value) {
        return new UpgradeOptions(specification, droppable, strict, value, sourceDigest,
                extensions, minorUnits);
    }

    /**
     * Returns these options recording the provenance of the result: the digest of the
     * bytes it was derived from (specification, section 4.7).
     *
     * @param bytes the bytes of the source document
     * @return the options
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public UpgradeOptions withSource(byte[] bytes) {
        return withSourceDigest(sha256(Objects.requireNonNull(bytes, "bytes")));
    }

    /**
     * Returns these options recording the provenance of the result from a digest that was
     * computed elsewhere.
     *
     * @param sha256 the SHA-256 of the bytes of the source document, as 64 lowercase
     *               hexadecimal digits
     * @return the options
     * @throws NullPointerException if {@code sha256} is {@code null}
     */
    public UpgradeOptions withSourceDigest(String sha256) {
        return new UpgradeOptions(specification, droppable, strict, partial,
                Objects.requireNonNull(sha256, "sha256"), extensions, minorUnits);
    }

    /**
     * Returns these options with the extension registries the result is checked with,
     * replacing any named before. Each of them is combined where it fits the target
     * edition.
     *
     * @param registries the registries of the extensions
     * @return the options
     * @throws NullPointerException if {@code registries} is or holds {@code null}
     */
    public UpgradeOptions withExtensions(Collection<Registry> registries) {
        return new UpgradeOptions(specification, droppable, strict, partial, sourceDigest,
                List.copyOf(Objects.requireNonNull(registries, "registries")), minorUnits);
    }

    /**
     * Returns these options with the minor units a bound that follows the currency in use
     * is evaluated with. A registry may bound the fraction digits of a term by the minor
     * unit of the currency rather than by a constant; which minor unit a currency has is a
     * fact of a dated snapshot a rule pack carries, and the run evaluates such a bound only
     * with one in hand. Nothing is rounded either way.
     *
     * @param units the minor units, with the snapshot they were read from as their source
     * @return the options
     * @throws NullPointerException if {@code units} is {@code null}
     */
    public UpgradeOptions withMinorUnits(MinorUnits units) {
        return new UpgradeOptions(specification, droppable, strict, partial, sourceDigest,
                extensions, Objects.requireNonNull(units, "units"));
    }

    /**
     * Tells whether another object is options with the same decisions.
     *
     * @param other the object to compare with
     * @return {@code true} if every decision is equal
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof UpgradeOptions that
                && Objects.equals(specification, that.specification)
                && droppable.equals(that.droppable)
                && strict == that.strict
                && partial == that.partial
                && Objects.equals(sourceDigest, that.sourceDigest)
                && extensions.equals(that.extensions)
                && Objects.equals(minorUnits, that.minorUnits);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(specification, droppable, strict, partial, sourceDigest,
                extensions, minorUnits);
    }

    /**
     * Returns the decisions as one line.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return "UpgradeOptions[specification=" + specification + ", droppable=" + droppable
                + ", strict=" + strict + ", partial=" + partial + ", sourceDigest="
                + sourceDigest + ", extensions=" + extensions.size() + ", minorUnits="
                + (minorUnits != null) + "]";
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
