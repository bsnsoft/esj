package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticType;
import java.util.Map;
import java.util.Objects;

/**
 * A place a template gives to a term of a model extension.
 *
 * <p>This is the whole of the mechanism behind the gross layout, and it is deliberately
 * not a gross layout. A branded template says which extension terms it has a place for,
 * by identifier and by position; the renderer fills a place only where the document
 * actually carries that term, and leaves the position out of the page otherwise. A
 * template that names a term no document of its sender ever carries costs nothing and
 * changes nothing, and a renderer that does not have the extension's registry still shows
 * the figure, because the template brought the label and the data type with it.
 *
 * <p>Only an extension term may be placed. A core business term of EN 16931-1 already has
 * a place in this layout, and a template that moved one would be a template deciding what
 * the standard means.
 *
 * @param term     the identifier of the extension term, for example {@code BT-B2C-001}
 * @param position where in the layout the value goes
 * @param labels   what the column or the row is called, per language, empty to fall back
 *                 on the registry and then on the identifier
 * @param type     how the value is written down where the registry of the rendering does
 *                 not know the term, or {@code null} to write it as it stands
 */
record Placement(String term, Position position, Map<RenderLanguage, String> labels,
                 SemanticType type) {

    /** Where in the layout a placed value goes. */
    enum Position {

        /** A column beside the net unit price of an invoice line. */
        LINE_UNIT_PRICE("line.unitPrice"),

        /** A column beside the VAT category and rate of an invoice line. */
        LINE_VAT("line.vat"),

        /** A column beside the net amount of an invoice line. */
        LINE_AMOUNT("line.amount"),

        /** A row in the totals of the document. */
        TOTALS("totals");

        private final String token;

        Position(String token) {
            this.token = token;
        }

        /** Tells whether this position stands in the table of the invoice lines. */
        boolean isLine() {
            return this != TOTALS;
        }

        /**
         * Returns the position a template named.
         *
         * @param written the word in the template
         * @return the position
         * @throws TemplateException if it is none of the four
         */
        static Position of(String written) {
            for (Position position : values()) {
                if (position.token.equals(written)) {
                    return position;
                }
            }
            StringBuilder known = new StringBuilder();
            for (Position position : values()) {
                known.append(known.length() == 0 ? "" : ", ").append(position.token);
            }
            throw new TemplateException("extensionTerms: position is one of " + known
                    + ", not '" + written + "'");
        }
    }

    /**
     * Checks the members.
     *
     * @throws NullPointerException if the term, the position or the labels are
     *                              {@code null}
     */
    Placement {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(position, "position");
        labels = Map.copyOf(labels);
    }

    /**
     * Returns what this place is called in a language, or {@code null} where the template
     * did not say and the caller should ask the registry.
     *
     * @param language the language of the rendering
     * @return the label, or {@code null}
     */
    String label(RenderLanguage language) {
        return labels.get(language);
    }
}
