package de.bsnsoft.esj.upgrade;

import de.bsnsoft.esj.SemanticPath;
import java.util.Objects;
import java.util.Optional;

/**
 * One thing an upgrade has to say about one document: an address it rewrote, a point it
 * cannot decide, a value it was allowed to drop, or a reason it refused.
 *
 * <p>The severity is carried by the note rather than by its kind, because one kind can
 * weigh two things: a result that does not satisfy the model of the target edition is a
 * refusal, and the same observation is an open point where the caller asked for a partial
 * result.
 *
 * @param kind     what kind of observation this is
 * @param severity how much it weighs in this run
 * @param path     the path it is about, absent where it is about the whole document
 * @param message  the observation, in one sentence
 * @param point    the identifier of the open point or refusal of the mapping file that
 *                 states the general case, absent where the mapping states none
 */
public record UpgradeNote(Kind kind,
                          Severity severity,
                          Optional<SemanticPath> path,
                          String message,
                          Optional<String> point) {

    /**
     * Checks that every part is there.
     *
     * @param kind     what kind of observation this is
     * @param severity how much it weighs in this run
     * @param path     the path it is about, absent where it is about the whole document
     * @param message  the observation, in one sentence
     * @param point    the identifier of the open point or refusal of the mapping file that
     *                 states the general case, absent where the mapping states none
     * @throws NullPointerException if a part is {@code null}
     */
    public UpgradeNote {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(point, "point");
    }

    /**
     * Creates a note about one path, with the severity its kind carries.
     *
     * @param kind    what kind of observation this is
     * @param path    the path it is about
     * @param message the observation
     * @return the note
     */
    public static UpgradeNote about(Kind kind, SemanticPath path, String message) {
        return new UpgradeNote(kind, kind.severity(), Optional.of(path), message,
                Optional.ofNullable(kind.point()));
    }

    /**
     * Creates a note about one path, stating which point of the mapping it belongs to.
     *
     * @param kind    what kind of observation this is
     * @param path    the path it is about
     * @param message the observation
     * @param point   the identifier of the point of the mapping file
     * @return the note
     */
    public static UpgradeNote about(Kind kind, SemanticPath path, String message, String point) {
        return new UpgradeNote(kind, kind.severity(), Optional.of(path), message,
                Optional.of(point));
    }

    /**
     * Creates a note about one path that weighs less than its kind usually does.
     *
     * @param kind     what kind of observation this is
     * @param severity how much it weighs in this run
     * @param path     the path it is about
     * @param message  the observation
     * @return the note
     */
    public static UpgradeNote at(Kind kind, Severity severity, SemanticPath path,
                                 String message) {
        return new UpgradeNote(kind, severity, Optional.of(path), message,
                Optional.ofNullable(kind.point()));
    }

    /**
     * Creates a note about the whole document.
     *
     * @param kind    what kind of observation this is
     * @param message the observation
     * @return the note
     */
    public static UpgradeNote ofDocument(Kind kind, String message) {
        return new UpgradeNote(kind, kind.severity(), Optional.empty(), message,
                Optional.ofNullable(kind.point()));
    }

    @Override
    public String toString() {
        return kind.token() + path.map(p -> " " + p).orElse("") + ": " + message;
    }

    /** How much a note weighs. */
    public enum Severity {

        /** Something the run did that the caller may want to know about. */
        INFORMATION,

        /**
         * Something the upgrade cannot decide on its own, or a loss the caller allowed.
         * The document is written and the point is the caller's to close.
         */
        OPEN_POINT,

        /** A reason the run wrote nothing. */
        REFUSAL
    }

    /** What an upgrade observes. */
    public enum Kind {

        /** A value moved to another address, because the target edition gives it one. */
        PATH_REWRITTEN("path-rewritten", Severity.INFORMATION, null),

        /**
         * Values at extension terms were carried unchanged. An extension registry names
         * the edition it was written against, so the two editions are not checked alike,
         * and extension content is never a reason for this run to refuse.
         */
        EXTENSION_CARRIED("extension-carried", Severity.OPEN_POINT, null),

        /** A component the target edition requires is absent and cannot be derived. */
        SCHEME_MISSING("scheme-missing", Severity.OPEN_POINT, "scheme-component-missing"),

        /** The specification identifier was left as it stands. */
        SPECIFICATION_IDENTIFIER("specification-identifier", Severity.OPEN_POINT,
                "specification-identifier"),

        /** The specification identifier was replaced, because the caller named one. */
        SPECIFICATION_REPLACED("specification-replaced", Severity.INFORMATION, null),

        /** A value has more fraction digits than the target edition allows it. */
        DECIMALS_OUT_OF_BOUNDS("decimals-out-of-bounds", Severity.OPEN_POINT, "decimal-bounds"),

        /**
         * The document carries values whose bound depends on the currency in use. That
         * bound is a fact of a versioned rule pack and this run does not evaluate it.
         */
        DECIMALS_NOT_EVALUATED("decimals-not-evaluated", Severity.INFORMATION, "decimal-bounds"),

        /** A value was dropped because the caller named its path. */
        VALUE_DROPPED("value-dropped", Severity.OPEN_POINT, "unmapped-term"),

        /** A value has no address in the target edition and the caller named no drop. */
        UNMAPPED("unmapped", Severity.REFUSAL, "unmapped-term"),

        /**
         * Two values would arrive at one address, or an occurrence index has no address,
         * because the target edition addresses fewer occurrences than the document holds.
         */
        SEVERAL_OCCURRENCES("several-occurrences", Severity.REFUSAL, "several-occurrences"),

        /**
         * A path of the document carries an occurrence index the model of the document's
         * own edition does not give it, or carries none where that model requires one.
         * Writing the address the target edition wants would repair the document on the
         * way, and an upgrade repairs nothing.
         */
        SOURCE_INDEX("source-index", Severity.REFUSAL, null),

        /**
         * The result does not satisfy the model of the target edition for a reason the
         * mapping does not explain.
         */
        MODEL_FINDING("model-finding", Severity.REFUSAL, null),

        /**
         * The result does not satisfy the model of the target edition and the source
         * document does not satisfy the model of its own edition in the same way. The
         * upgrade carried the document as it found it; closing the point is the caller's.
         */
        CARRIED_FINDING("carried-finding", Severity.OPEN_POINT, null),

        /** The run was asked for a document with no open point and found one. */
        STRICT("strict", Severity.REFUSAL, null);

        private final String token;
        private final Severity severity;
        private final String point;

        Kind(String token, Severity severity, String point) {
            this.token = token;
            this.severity = severity;
            this.point = point;
        }

        /**
         * Returns the token a report writes for this kind.
         *
         * @return the token, in lowercase with hyphens
         */
        public String token() {
            return token;
        }

        /**
         * Returns how much a note of this kind weighs unless the run says otherwise.
         *
         * @return the severity
         */
        public Severity severity() {
            return severity;
        }

        /**
         * Returns the point of the mapping file this kind belongs to.
         *
         * @return the identifier, or {@code null} where the mapping states no general
         *         case for it
         */
        String point() {
            return point;
        }
    }
}
