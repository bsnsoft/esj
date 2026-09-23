package de.bsnsoft.esj.render;

/**
 * Which of the two page layouts a PDF rendering is drawn in.
 *
 * <p>Both show the same document and both are net: every term occurrence of the document
 * reaches a page, nothing is derived, and a figure of a model extension is marked as one
 * that was displayed. What differs is where a value stands and how a code is written.
 *
 * <p>The {@linkplain #LETTER letter} layout is the shape of a business letter: an address
 * window, the reference line under it, a line table, a narrow block of totals, the payment
 * details, and the codes a reader does not read written under their names — with everything
 * the letter has no place of its own for under a closing heading, so that nothing of the
 * document is lost either way. It is what an invoice rendered for its recipient is, and it
 * is the default, {@link RenderOptions#DEFAULT_LAYOUT}. The {@linkplain #GENERIC generic}
 * layout is the shape of the semantic model: sections in the order of the model, every term
 * under its own label, every code as the code it is. That is the right picture for a proof,
 * and the validation report draws the invoice it carries in it.
 */
public enum Layout {

    /** The registry-driven layout: the shape of the semantic model. */
    GENERIC,

    /** The customer-facing layout, and the default: the shape of a business letter. */
    LETTER
}
