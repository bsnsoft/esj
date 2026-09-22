package de.bsnsoft.esj;

/** Documents the tests build on. */
public final class Fixtures {

    private Fixtures() {
    }

    /**
     * Returns a builder holding the smallest document that satisfies all three validation
     * layers: the mandatory terms of the core model and nothing else. It is the content of
     * {@code examples/minimal.esj.json}.
     *
     * @return a builder for the smallest conformant document
     */
    public static SemanticDocument.Builder minimalInvoice() {
        return SemanticDocument.builder()
                .put("/BT-1", "RE-2026-0001")
                .put("/BT-2", "2026-01-15")
                .put("/BT-3", "380")
                .put("/BT-5", "EUR")
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2017")
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
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "100")
                .put("/BG-25/0/BG-29/BT-146", "100")
                .put("/BG-25/0/BG-30/BT-151", "Z")
                .put("/BG-25/0/BG-31/BT-153", "Consulting service");
    }

    /**
     * Returns the values a second invoice line needs to satisfy layer L3.
     *
     * @param builder the builder to add them to
     * @param index   the occurrence index of the line
     * @return the same builder
     */
    public static SemanticDocument.Builder addLine(SemanticDocument.Builder builder, int index) {
        String line = "/BG-25/" + index;
        return builder
                .put(line + "/BT-126", Integer.toString(index + 1))
                .put(line + "/BT-129", "1")
                .put(line + "/BT-130", "C62")
                .put(line + "/BT-131", "100")
                .put(line + "/BG-29/BT-146", "100")
                .put(line + "/BG-30/BT-151", "Z")
                .put(line + "/BG-31/BT-153", "Another service");
    }
}
