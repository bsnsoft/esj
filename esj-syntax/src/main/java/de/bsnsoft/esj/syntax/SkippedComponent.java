package de.bsnsoft.esj.syntax;

import java.util.Objects;

/**
 * A component of the selected pack that did not run, and why.
 *
 * <p>A report that lists only what ran cannot be read: a document with no finding looks
 * the same whether every rule set was applied to it or none was. So the components that
 * were left out travel beside the ones that ran, each with the reason.
 *
 * <p>The reason travels twice, and on purpose. {@link Reason} is what a program branches
 * on and what a report written in another language than this one looks the sentence up
 * by; {@link #message()} is the English sentence the text form prints, so that a caller
 * reading lines on a terminal is told the same thing without a table of translations.
 *
 * @param component the name of the component, as the pack manifest names it
 * @param reason    which of the reasons it is
 * @param message   why it did not run, in English
 */
public record SkippedComponent(String component, Reason reason, String message) {

    /**
     * Creates a record of a component that did not run.
     *
     * @param component the name of the component
     * @param reason    which of the reasons it is
     * @param message   why it did not run, in English
     * @throws NullPointerException if an argument is {@code null}
     */
    public SkippedComponent {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(message, "message");
    }

    /** Why a component of the selected pack did not run. */
    public enum Reason {

        /** It validates a syntax the document is not written in. */
        OTHER_SYNTAX,

        /** It validates a profile the document does not name. */
        OTHER_PROFILE,

        /** The document failed the schema of its syntax, so no rule set was run over it. */
        SCHEMA_INVALID
    }
}
