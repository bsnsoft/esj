package de.bsnsoft.esj.bindings;

import java.util.List;

/**
 * A condition an element has to meet for a step of a binding XPath to match it.
 *
 * <p>Six shapes occur in the tables of {@code model/bindings}, and they fall into two
 * groups the reader treats differently. An <em>attribute</em> predicate is decided the
 * moment the element starts, because the attributes arrive with the start tag. A
 * <em>child</em> predicate is decided only once the element has been read to its end,
 * because the element it asks about may be written after the one the XPath selects; the
 * reader therefore holds such an element — one document reference, one allowance, one
 * tax subtotal — in memory and decides afterwards. That is the whole reason the reader
 * buffers anything at all, and it is why the bound is one element and not the document.
 *
 * <p>The reference form, {@code @currencyID = /Invoice/cbc:DocumentCurrencyCode},
 * compares an attribute with the content of an element elsewhere in the document. It is
 * resolved against what the reader has read so far. Both syntaxes write the currency
 * codes before the amounts that are compared against them, because the sequence of the
 * schema puts them there, so one pass is enough; an element that has not arrived counts
 * as absent and the predicate is then false.
 *
 * @param kind      which of the six shapes this is
 * @param attribute the local name of the attribute an attribute predicate reads, or
 *                  {@code null} for a child predicate
 * @param path      the steps a child or reference predicate follows: from the element
 *                  itself for a child predicate, from the root of the document for a
 *                  reference predicate, and empty for the other shapes
 * @param literal   the string a comparing predicate compares against, or {@code null}
 */
record Predicate(Predicate.Kind kind, String attribute, List<Name> path, String literal) {

    /** The shapes of predicate the binding tables write. */
    enum Kind {

        /** {@code [@schemeID]}: the element carries the attribute. */
        ATTRIBUTE_PRESENT,

        /** {@code [@format = '102']}: the attribute has that content. */
        ATTRIBUTE_EQUALS,

        /**
         * {@code [not(@schemeID = 'SEPA')]}: the attribute does not have that content,
         * which an element without the attribute at all satisfies.
         */
        ATTRIBUTE_NOT_EQUALS,

        /**
         * {@code [@currencyID = /Invoice/cbc:DocumentCurrencyCode]}: the attribute has the
         * content of an element of the document.
         */
        ATTRIBUTE_REFERENCE,

        /** {@code [ram:TypeCode = '130']}: a descendant element has that content. */
        CHILD_EQUALS,

        /** {@code [cac:TaxScheme/cbc:ID != 'VAT']}: no descendant element has that content. */
        CHILD_NOT_EQUALS
    }

    /**
     * Copies the steps.
     *
     * @param kind      which of the six shapes this is
     * @param attribute the local name of the attribute an attribute predicate reads, or
     *                  {@code null} for a child predicate
     * @param path      the steps a child or reference predicate follows: from the element
     *                  itself for a child predicate, from the root of the document for a
     *                  reference predicate, and empty for the other shapes
     * @param literal   the string a comparing predicate compares against, or {@code null}
     */
    Predicate {
        path = List.copyOf(path);
    }

    /**
     * Tells whether this predicate can be decided only once the element has been read
     * whole, which is what makes the reader buffer that element.
     */
    boolean needsWholeElement() {
        return kind == Kind.CHILD_EQUALS || kind == Kind.CHILD_NOT_EQUALS;
    }
}
