package de.bsnsoft.esj.bindings;

/**
 * Signals that a document names an edition of the semantic model that the binding table
 * of this writer was not written against.
 *
 * <p>A binding table maps the business terms of one edition onto the elements of one
 * syntax version, and a semantic path is an address relative to an edition
 * ({@code SPEC.md}, sections 4.4 and 10). Writing a document of another edition through it
 * would place the terms the two editions share and drop the rest, which is the failure
 * this project is built against: a business term that leaves the semantic model and
 * arrives nowhere. So the writer refuses the document whole, names both editions, and
 * leaves the choice to the caller — write it as the edition the table binds first, or
 * wait for a table of its own.
 *
 * <p>It is about the declared edition of the document and not about its current content.
 * A document of a later edition every one of whose values happens to sit at a path the
 * table binds is refused too: a caller that received a conversion today and a refusal
 * tomorrow for the same command has learned nothing it can act on.
 */
public final class BindingEditionException extends BindingException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message, in English
     */
    public BindingEditionException(String message) {
        super(message);
    }
}
