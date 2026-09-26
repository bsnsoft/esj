package de.bsnsoft.esj.rules.en16931.v2026;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;

/** Documents of EN 16931-1:2026 the rule cases of this package are built from. */
final class Documents2026 {

    /** The edition every document of these cases names. */
    static final String EDITION = Registry.forEdition("2026").semanticModel();

    private Documents2026() {
    }

    /**
     * Returns the smallest document of this edition the whole pack is silent on: the
     * mandatory terms of the model and one invoice line of a hundred euro at the zero rate.
     *
     * @return a builder for that document
     */
    static SemanticDocument.Builder minimal() {
        return bare().put("/BT-9", "2026-02-15").put("/BG-4/BT-31", "DE123456789");
    }

    /**
     * Returns the same document without the payment due date, which is what the case of the
     * rule about a positive amount due needs and nothing else does.
     *
     * @return a builder for that document
     */
    static SemanticDocument.Builder withoutDueDate() {
        return bare().put("/BG-4/BT-31", "DE123456789");
    }

    /**
     * Returns the same document without the seller VAT identifier, which is what the cases
     * of the rules that oblige one need.
     *
     * @return a builder for that document
     */
    static SemanticDocument.Builder withoutSellerTaxId() {
        return bare().put("/BT-9", "2026-02-15");
    }

    private static SemanticDocument.Builder bare() {
        return SemanticDocument.builder()
                .semanticModel(EDITION)
                .put("/BT-1", "RE-2026-0001")
                .put("/BT-2", "2026-01-15")
                .put("/BT-3", "380")
                .put("/BT-5", "EUR")
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2026")
                .put("/BG-4/BT-27", "Example GmbH")
                .put("/BG-4/BG-5/BT-40", "DE")
                .put("/BG-7/BT-44", "Muster AG")
                .put("/BG-7/BG-8/BT-55", "DE")
                .put("/BG-22/BT-106", "100")
                .put("/BG-22/BT-109", "100")
                .put("/BG-22/BT-112", "100")
                .put("/BG-22/BT-115", "100")
                .put("/BG-23/0/BT-116", "100")
                .put("/BG-23/0/BT-117", "0")
                .put("/BG-23/0/BT-118", "Z")
                .put("/BG-23/0/BT-119", "0")
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "100")
                .put("/BG-25/0/BG-29/BT-146", "100")
                .put("/BG-25/0/BG-29/BT-149", "1")
                .put("/BG-25/0/BG-30/BT-151", "Z")
                .put("/BG-25/0/BG-30/BT-152", "0")
                .put("/BG-25/0/BG-31/BT-153", "Consulting service");
    }

    /**
     * Returns the same document with one value replaced or added, which is what a case of
     * this package changes.
     *
     * @param builder the builder
     * @param path    the semantic path
     * @param content the content, in the canonical form its semantic data type requires
     * @return the same builder
     */
    static SemanticDocument.Builder set(SemanticDocument.Builder builder, String path,
                                        String content) {
        return builder.set(SemanticPath.of(path), SemanticValue.of(content));
    }

    /**
     * Returns the document with one value replaced or added.
     *
     * @param path    the semantic path
     * @param content the content
     * @return the document
     */
    static SemanticDocument with(String path, String content) {
        return set(minimal(), path, content).build();
    }

    /**
     * Returns the document with two values replaced or added.
     *
     * @param path    the semantic path
     * @param content the content
     * @param second  a second semantic path
     * @param other   its content
     * @return the document
     */
    static SemanticDocument with(String path, String content, String second, String other) {
        return set(set(minimal(), path, content), second, other).build();
    }
}
