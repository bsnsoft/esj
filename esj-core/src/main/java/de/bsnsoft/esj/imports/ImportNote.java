package de.bsnsoft.esj.imports;

import java.util.Objects;

/**
 * One observation the importer made about a document: something it could not place,
 * could not read, or dropped on the way into the semantic model.
 *
 * <p>A note is data, not an instruction and not a validation finding. The findings of
 * {@code esj-core} describe a document that exists; these notes describe the difference
 * between the input and the document that was built from it.
 *
 * @param kind     what kind of observation this is
 * @param level    how much attention the note deserves
 * @param location where it was made: the semantic path where the reader has one, and
 *                 otherwise the chain of identifiers or element names from the root
 *                 of the source down to the place concerned, separated by {@code /}
 * @param message  a description in English, which does not reproduce the content of the
 *                 document beyond what a term identifier gives away
 */
public record ImportNote(ImportNote.Kind kind,
                         ImportNote.Level level,
                         String location,
                         String message) {

    /**
     * How much attention a note deserves.
     *
     * <p>The distinction is not about how serious the observation sounds but about
     * whether the reader of the document lost anything by it. A note that fires on every
     * well-formed document of a syntax says something about the syntax binding rather
     * than about this document, and a channel that carries such a note beside a real loss
     * teaches its reader to stop looking at the channel.
     */
    public enum Level {

        /**
         * Nothing the semantic model can hold was lost. The observation describes the
         * distance between the source syntax and EN 16931 — a component the standard
         * does not define at that term, an element that was empty to begin with — and a
         * reader of the result is looking at everything the sender wrote that EN 16931
         * has a place for.
         */
        INFORMATION,

        /**
         * Content of the source document did not reach the semantic document, or reached
         * it only after the bytes were read in a charset other than the one the document
         * declared. Either way a reader of the result is looking at something other than
         * what the sender wrote, and has to be told. It includes what a downstream engine
         * can no longer see, such as fraction digits the semantic data type does not
         * carry.
         */
        WARNING
    }

    /** The kinds of observation an importer records. */
    public enum Kind {

        /** An element carried a term identifier that no loaded registry knows. */
        UNKNOWN_TERM,

        /**
         * An element carried a known term identifier in a position the registry does not
         * record a parent chain for. The element and everything below it was skipped.
         */
        UNPLACEABLE,

        /**
         * An element lay at a semantic path longer than the bound the importer writes
         * within, so it and everything below it was skipped. A group is measured with
         * the shortest path a value below it could take, because a group that fills the
         * bound exactly leaves room for nothing.
         */
        PATH_TOO_LONG,

        /**
         * A value did not fit inside the limits of the reader the importer writes for,
         * or the document as a whole reached one of them.
         *
         * <p>A value that is too large on its own — a string longer than the bound on a
         * string value, an attachment larger than the bound on a binary value — is left
         * out by itself and the rest of the document is unaffected. Where the document
         * reaches the bound on the number of its values or on its own size, nothing more
         * can be added to it, so the import ends there and the note says so: the values
         * that are in the document are the ones that were read before the bound.
         */
        LIMIT_REACHED,

        /** An element carried no content, so no value was written for it. */
        EMPTY,

        /**
         * The content of an element did not spell a value of the semantic data type the
         * registry gives its term, so no value was written for it.
         */
        MALFORMED,

        /**
         * A supplementary component was present that the registry does not list for that
         * term, so it was dropped while the value itself was kept.
         */
        COMPONENT_DROPPED,

        /**
         * A supplementary component the semantic data type requires was missing.
         *
         * <p>The value itself is kept where the format can hold it. An importer does not
         * decide whether a document is right, and a term the sender wrote is a term the
         * validator has to be able to see: the structural layer reports the missing
         * component ({@code ESJ-L2-COMPONENT-MISSING}) and the business rule of the
         * standard that asks for it reports it too, which is only possible if the value
         * reached the document. Where the format cannot hold the value at all — a binary
         * object is its bytes together with its media type and its file name — nothing is
         * written for the element and this note is the whole of what is left.
         */
        COMPONENT_MISSING,

        /**
         * The source wrote more fraction digits than the semantic data type of the term
         * admits, and the digits beyond it were zeros, so the canonical decimal form of
         * the specification, section 6.4 does not carry them.
         *
         * <p>The number is the same number: {@code 319.860} and {@code 319.86} are one
         * value, and the document says so. What is lost is the notation, and the notation
         * is what the decimal rules of the standard are about, so a reader comparing this
         * document with the source is told here rather than left to find out from a
         * validator that no longer can see it. That is why the note is a warning and not
         * an observation about the syntax: it is about what this sender wrote, and the
         * verdict of an engine reading the result downstream depends on it.
         */
        SCALE_REDUCED,

        /**
         * Two elements resolved to the same semantic path. The first occurrence was kept
         * and the second one skipped.
         */
        DUPLICATE_PATH,

        /**
         * The element carried exactly the value the binding table of its syntax is written
         * with where a document states no such business term, and the condition that
         * convention names held, so it was not read as the term.
         *
         * <p>The purchase order reference of a UBL document is the case of this release:
         * UBL requires {@code cac:OrderReference/cbc:ID} as soon as the sales order
         * reference BT-14 is written, and a document that states no purchase order
         * reference carries the conventional value there. Reading it as BT-13 would put
         * into the semantic document a reference nobody made. Nothing the semantic model
         * can hold was lost, so the note is an observation about the syntax;
         * {@code conformance/readers.md} records it as a difference between the two readers
         * of this project.
         */
        CONVENTION_NOT_READ,

        /**
         * The bytes of the document were not written in the encoding it declared, and the
         * importer recoded them into UTF-8 before parsing.
         *
         * <p>The note names what the document declared and what its bytes were read as,
         * because a recode is a guess about the writer of the document and the reader of
         * the result is entitled to see it. An importer in {@code XrEncodingMode.STRICT}
         * refuses such a document instead of recording this note.
         *
         * <p>The digest in the provenance of the document is over the bytes that were
         * handed over, not over the recoded ones, so the note is also what tells a caller
         * that the two differ.
         */
        ENCODING_REPAIRED
    }

    /**
     * Checks that no member is {@code null}.
     *
     * @param kind     what kind of observation this is
     * @param level    how much attention the note deserves
     * @param location where it was made: the semantic path where the reader has one, and
     *                 otherwise the chain of identifiers or element names from the root
     *                 of the source down to the place concerned, separated by {@code /}
     * @param message  a description in English, which does not reproduce the content of the
     *                 document beyond what a term identifier gives away
     * @throws NullPointerException if a member is {@code null}
     */
    public ImportNote {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Creates a note at {@link Level#WARNING}, the level of every observation that means
     * content did not reach the document.
     *
     * @param kind     what kind of observation this is
     * @param location where it was made
     * @param message  a description in English
     * @throws NullPointerException if an argument is {@code null}
     */
    public ImportNote(Kind kind, String location, String message) {
        this(kind, Level.WARNING, location, message);
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
