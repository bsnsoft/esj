package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XrImporter;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * What one run of the syntax engine is allowed to do.
 *
 * <p>Two of the three are limits, and a limit is policy rather than a property of a
 * document: a caller who reads invoices from strangers sets them low and refuses a
 * document rather than spending minutes on it, and a caller who processes its own
 * archive raises them. Reaching either of them ends the run in a
 * {@link SyntaxLimitException} and not in a verdict, so a document nobody managed to
 * check never reads as an invalid one.
 *
 * <p>Instances are immutable; every {@code with} method returns a new one.
 */
public final class SyntaxOptions {

    /**
     * The largest input a run accepts unless it is told otherwise: the bound the importer
     * of this project reads within, four mebibytes. It is taken from there rather than
     * written again, because a document the validator accepts and the importer refuses,
     * or the other way round, is a difference nobody asked for. An EN 16931 invoice is a
     * small document, and the cost of the artefacts does not grow with the size of the
     * input in a straight line.
     */
    public static final long DEFAULT_MAX_INPUT_BYTES = XrImporter.DEFAULT_MAX_INPUT_BYTES;

    /**
     * How long a run may take unless it is told otherwise: five minutes.
     *
     * <p>It is a ceiling and not a budget. An ordinary invoice is validated in well under
     * a second, and a default this far above that is there for the document that is
     * pathological rather than for the one that is large; a caller that wants a tight
     * answer sets a tight number.
     */
    public static final Duration DEFAULT_MAX_RUNTIME = Duration.ofMinutes(5);

    private static final SyntaxOptions DEFAULTS =
            new SyntaxOptions(null, DEFAULT_MAX_INPUT_BYTES, DEFAULT_MAX_RUNTIME);

    private final Pack pack;
    private final long maxInputBytes;
    private final Duration maxRuntime;

    private SyntaxOptions(Pack pack, long maxInputBytes, Duration maxRuntime) {
        this.pack = pack;
        this.maxInputBytes = maxInputBytes;
        this.maxRuntime = maxRuntime;
    }

    /**
     * Returns the options a run uses unless it is given others: the bundled pack that
     * recognizes the profile of the document, {@link #DEFAULT_MAX_INPUT_BYTES} and
     * {@link #DEFAULT_MAX_RUNTIME}.
     *
     * @return the default options
     */
    public static SyntaxOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns the pack this run uses, or an empty optional where the run chooses one of
     * the bundled packs by the profile of the document.
     *
     * @return the pack
     */
    public Optional<Pack> pack() {
        return Optional.ofNullable(pack);
    }

    /**
     * Returns the largest input this run accepts, in bytes.
     *
     * @return the bound
     */
    public long maxInputBytes() {
        return maxInputBytes;
    }

    /**
     * Returns how long this run may take.
     *
     * @return the bound
     */
    public Duration maxRuntime() {
        return maxRuntime;
    }

    /**
     * Returns options that run one pack rather than choosing among the bundled ones.
     *
     * @param pack the pack to run
     * @return the new options
     * @throws NullPointerException if {@code pack} is {@code null}
     */
    public SyntaxOptions withPack(Pack pack) {
        return new SyntaxOptions(Objects.requireNonNull(pack, "pack"), maxInputBytes,
                maxRuntime);
    }

    /**
     * Returns options with another bound on the input.
     *
     * @param maxInputBytes the largest input to accept, in bytes
     * @return the new options
     * @throws IllegalArgumentException if the bound is not positive
     */
    public SyntaxOptions withMaxInputBytes(long maxInputBytes) {
        if (maxInputBytes <= 0) {
            throw new IllegalArgumentException("an input bound is a positive number of bytes");
        }
        return new SyntaxOptions(pack, maxInputBytes, maxRuntime);
    }

    /**
     * Returns options with another bound on the time.
     *
     * @param maxRuntime how long a run may take
     * @return the new options
     * @throws IllegalArgumentException if the bound is not positive
     * @throws NullPointerException     if {@code maxRuntime} is {@code null}
     */
    public SyntaxOptions withMaxRuntime(Duration maxRuntime) {
        Objects.requireNonNull(maxRuntime, "maxRuntime");
        if (maxRuntime.isZero() || maxRuntime.isNegative()) {
            throw new IllegalArgumentException("a time bound is a positive duration");
        }
        return new SyntaxOptions(pack, maxInputBytes, maxRuntime);
    }
}
