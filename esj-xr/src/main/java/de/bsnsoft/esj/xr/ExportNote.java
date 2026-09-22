package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.imports.ImportNote;
import java.util.Objects;

/**
 * One observation the exporter made about a document: something of it that the XR
 * representation has no place for.
 *
 * <p>A note is data, not an instruction and not a validation finding. Every note means the
 * same thing — the XR document the exporter wrote carries less than the ESJ document it
 * was given — so there is no level here, unlike in an {@link ImportNote}: a caller that
 * needs the export to be complete treats any note as a refusal.
 *
 * @param kind     what kind of observation this is
 * @param location the semantic path it was made at, or the owner token of an extension
 *                 subtree
 * @param message  a description in English, which does not reproduce the content of the
 *                 document beyond what a term identifier gives away
 */
public record ExportNote(ExportNote.Kind kind, String location, String message) {

    /** The kinds of observation an exporter records. */
    public enum Kind {

        /**
         * No element of the XR representation lies at that path, so the value was left
         * out. The term may be one the schema of the XR representation does not carry,
         * one no registry of the exporter knows, or one whose path is not a position the
         * registry records for it. An occurrence whose index has no predecessor is the
         * same case: the XR representation writes occurrences one after the other and
         * numbers them by counting, so it cannot express a gap.
         */
        NO_ELEMENT,

        /**
         * The content of a value, or one of its supplementary components, carries a
         * character that XML 1.0 cannot hold — a control character other than tabulator,
         * line feed and carriage return, or an unpaired surrogate. The value was left
         * out, because no escape exists for such a character and writing it would produce
         * a document no parser reads.
         */
        NOT_REPRESENTABLE,

        /**
         * A value carried a supplementary component that its element of the XR
         * representation cannot hold. The value itself was written; the component was
         * dropped.
         */
        COMPONENT_DROPPED,

        /**
         * The document carried an {@code extensions} subtree. The XR representation has
         * no place for data that belongs to no business term, so the subtree was left
         * out.
         */
        EXTENSIONS_DROPPED
    }

    /**
     * Checks that no member is {@code null}.
     *
     * @param kind     what kind of observation this is
     * @param location the semantic path it was made at, or the owner token of an extension
     *                 subtree
     * @param message  a description in English, which does not reproduce the content of the
     *                 document beyond what a term identifier gives away
     * @throws NullPointerException if a member is {@code null}
     */
    public ExportNote {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Returns the note as one line, in the form {@code KIND at location: message}.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return kind + " at " + location + ": " + message;
    }
}
