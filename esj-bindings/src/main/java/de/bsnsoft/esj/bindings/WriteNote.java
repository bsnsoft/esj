package de.bsnsoft.esj.bindings;

import java.util.Objects;

/**
 * One observation the writer made about a document: something of the semantic document
 * that the XML syntax has no place for, or that it has a narrower place for than the
 * semantic model does.
 *
 * <p>A note is data, not an instruction and not a validation finding. A document that
 * writes without a note carried into the syntax everything it held. A document that writes
 * with one is a document the syntax cannot say the whole of, and the note says which part
 * and why — never silently, because a converter that drops a value without saying so is
 * worse than one that refuses.
 *
 * @param kind     what kind of observation this is
 * @param path     the semantic path concerned, or the empty string where the note is about
 *                 the document as a whole
 * @param message  a description in English, which does not reproduce the content of the
 *                 document beyond what a term identifier gives away
 */
public record WriteNote(WriteNote.Kind kind, String path, String message) {

    /** The kinds of observation a writer records. */
    public enum Kind {

        /**
         * The binding table gives the term no place in this syntax. The terms of a registry
         * the syntax binding does not cover are the case of this release: the XRechnung
         * extension binds its own terms, and the core terms its sub invoice lines carry,
         * for UBL Invoice alone, so a document that uses it cannot say those values in a
         * cross industry invoice.
         */
        TERM_NOT_BOUND,

        /**
         * No registry loaded by the binding table knows the term, so nothing says where in
         * the syntax it would go.
         */
        TERM_UNKNOWN,

        /**
         * The syntax admits fewer instances of a business group than the document carries,
         * so the instances beyond what it admits have nowhere to stand.
         */
        GROUP_NOT_REPEATABLE,

        /**
         * A supplementary component of a value has no place at the element the term is
         * written at, so the value was written without it.
         */
        COMPONENT_DROPPED,

        /**
         * The document holds data under {@code extensions}, which is data without a
         * business term and therefore without a place in any syntax binding.
         */
        EXTENSIONS_DROPPED,

        /**
         * Two values of the document resolved to one element of the syntax, so the second
         * of them was not written.
         *
         * <p>No table of this release binds two business terms to one XPath, and no
         * document of the conformance corpus or of {@code examples/} produces this note. It
         * is the writer's promise that it never writes over a value it has already written:
         * a table that grew such a pair would be found here rather than in a document that
         * quietly holds one value where it was given two.
         */
        VALUE_COLLIDED,

        /**
         * A value does not spell what its binding says it is — a date that is no date —
         * so it was written as it stands rather than in the form the syntax asks for.
         */
        VALUE_NOT_CONVERTED,

        /**
         * A value has a place in the syntax only in front of the content of another term
         * at the same element, and the document carries no such content, so it was not
         * written.
         *
         * <p>The note subject code of UBL is the case: the syntax has no element for it
         * and writes it as a {@code #AAI#} prefix of the note it belongs to. A prefix
         * without a note comes back as a note whose text is the prefix, so it is left out
         * instead.
         */
        VALUE_NEEDS_COMPANION,

        /**
         * The syntax writes the value inside the element of a business group the document
         * does not state, and the schema requires content of that element that only the
         * terms of that group carry, so the value has nowhere to stand.
         *
         * <p>The payment due date of a UBL credit note is the case of this release. The
         * semantic model states BT-9 outside BG-16, and UBL writes it inside
         * {@code cac:PaymentMeans}, whose type requires the payment means code that BT-81
         * of BG-16 carries. A credit note that states a due date and no payment
         * instructions is an ordinary EN 16931 document, and there is no element of a UBL
         * credit note that holds it: writing it alone would make a document the UBL schema
         * refuses, and supplying a payment means code would put into the document
         * something nobody stated.
         */
        VALUE_NEEDS_GROUP,

        /**
         * The syntax asks for an element or an attribute that no business term of the
         * semantic model names, and no convention of the binding table covers it, so the
         * writer left it out or wrote it empty and the document will not satisfy the
         * schema of that syntax.
         *
         * <p>Inventing a value would put into the document something nobody stated. Where
         * this project has decided what such an element is written with, the decision is a
         * convention of the binding table and the note is {@link #CONVENTION_APPLIED}
         * instead; this kind is what is left. The note is about the document rather than
         * about a semantic path, so it carries none.
         */
        ELEMENT_NOT_STATED,

        /**
         * The syntax requires an element whose content a business term of the semantic
         * model does carry, and this document does not state that term, so the element was
         * left out or written empty and the document will not satisfy the schema of that
         * syntax.
         *
         * <p>The total value added tax amount is the case of the conformance corpus: UBL
         * requires {@code cbc:TaxAmount} of every tax total and an ESJ document may leave
         * BT-110 out, which the business rules of the standard would fault as well. The
         * message names the term; the note is about the document rather than about a
         * semantic path, so it carries none.
         */
        TERM_NOT_STATED,

        /**
         * The syntax requires an element that no business term of this document states,
         * and the binding table says what this project writes there, so the writer wrote
         * that value.
         *
         * <p>It is the one note that says nothing fell short: the value carries no
         * business statement, nothing of the document was lost, and the written document
         * is one the syntax accepts. The message names the element, the value, what asks
         * for the element and where the value comes from, so that a caller can see what is
         * in the document that the invoice did not state.
         * {@link BindingTable#conventions()} carries the three of this release.
         */
        CONVENTION_APPLIED,

        /**
         * A value is character for character the value a convention of this binding writes
         * where the document states nothing, and it stands where that convention would
         * have written it, so a reader that knows the convention does not read it back as
         * the term it was written from.
         *
         * <p>A purchase order reference BT-13 whose content is the two letters {@code NA},
         * in a document that also states the sales order reference BT-14, is the case: the
         * written document says what it was given and the round trip loses the term. The
         * value is in the written document either way, so it is not a loss of the writing;
         * it is said because a caller who round trips has to hear it.
         */
        VALUE_READS_AS_CONVENTION,

        /**
         * A value carries a character the target syntax cannot hold, so the character was
         * left out and the rest of the value written.
         *
         * <p>The semantic model is the wider of the two here. A text value of an ESJ
         * document may hold any Unicode scalar value, control characters included
         * (specification, section 12.6), and XML 1.0 admits neither those nor the two
         * non-characters at the end of the basic multilingual plane, and has no escape for
         * them. The note names the term and the code points rather than the content.
         */
        CHARACTER_NOT_REPRESENTABLE;

        /**
         * Tells whether a note of this kind says that something the document states did
         * not reach the syntax.
         *
         * <p>Most of them do, and a caller who needs the two documents to say the same
         * thing has to stop where one of them is made. Five do not.
         * {@link #ELEMENT_NOT_STATED}, {@link #TERM_NOT_STATED} and
         * {@link #CONVENTION_APPLIED} are statements about the syntax rather than about
         * the document: it asks for something no business term of the document fills, and
         * nothing of the document was lost by it. {@link #VALUE_NOT_CONVERTED} says that a
         * value was written as it stands rather than in the form the binding asks for, and
         * {@link #VALUE_READS_AS_CONVENTION} that a value reads back as nothing; the value
         * is in the written document in both cases.
         *
         * @return {@code true} where a value, a component or a part of the document did
         *         not travel
         */
        public boolean isLoss() {
            return switch (this) {
                case ELEMENT_NOT_STATED, TERM_NOT_STATED, CONVENTION_APPLIED,
                        VALUE_NOT_CONVERTED, VALUE_READS_AS_CONVENTION -> false;
                default -> true;
            };
        }

        /**
         * Tells whether a note of this kind says that the written document falls short of
         * the semantic document or of the schema of its own syntax.
         *
         * <p>All of them do but {@link #CONVENTION_APPLIED}, which says that the syntax
         * asked for something and the binding table said what to write there. A document
         * written with conventions and nothing else is complete: it says everything the
         * semantic document says and its own syntax accepts it.
         *
         * @return {@code true} where something fell short
         */
        public boolean isShortfall() {
            return this != CONVENTION_APPLIED;
        }
    }

    /**
     * Checks that the parts of the note are present.
     *
     * @param kind     what kind of observation this is
     * @param path     the semantic path concerned, or the empty string where the note is about
     *                 the document as a whole
     * @param message  a description in English, which does not reproduce the content of the
     *                 document beyond what a term identifier gives away
     * @throws NullPointerException if a part is {@code null}
     */
    public WriteNote {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Returns the note as one line, which is what a report writes.
     *
     * @return the kind, the path where there is one, and the message
     */
    @Override
    public String toString() {
        return kind + (path.isEmpty() ? "" : " at " + path) + ": " + message;
    }
}
