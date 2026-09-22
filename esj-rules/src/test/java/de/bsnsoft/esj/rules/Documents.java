package de.bsnsoft.esj.rules;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/** Documents the tests of this module run the rules over. */
final class Documents {

    private Documents() {
    }

    /**
     * Returns a builder holding the smallest document that satisfies all three validation
     * layers: the mandatory terms of the core model and nothing else, with one invoice line
     * of a hundred.
     *
     * @return a builder for the smallest conformant document
     */
    static SemanticDocument.Builder minimal() {
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
     * Replaces the content at a path, which {@code put} refuses to do.
     *
     * @param builder the builder
     * @param path    the semantic path
     * @param content the content, in the canonical form its semantic data type requires
     * @return the same builder
     */
    static SemanticDocument.Builder set(SemanticDocument.Builder builder, String path, String content) {
        return builder.set(SemanticPath.of(path), SemanticValue.of(content));
    }

    /**
     * Returns the canonical decimal form of a number, which is the only form a value of a
     * numeric term may carry (specification, section 6.4).
     *
     * @param number the number
     * @return its canonical form
     */
    static String canonical(BigDecimal number) {
        BigDecimal stripped = number.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    /**
     * Adds one invoice line whose net amount is the quantity times the price, in the shape
     * the scale generator of {@code conformance/scale} produces.
     *
     * @param builder  the builder to add to
     * @param index    the occurrence index of the line
     * @param quantity the invoiced quantity
     * @param price    the item net price
     * @return the same builder
     */
    static SemanticDocument.Builder line(SemanticDocument.Builder builder, int index,
                                         String quantity, String price) {
        String at = "/BG-25/" + index;
        BigDecimal net = new BigDecimal(quantity).multiply(new BigDecimal(price))
                .setScale(2, RoundingMode.HALF_UP);
        return builder
                .put(at + "/BT-126", Integer.toString(index + 1))
                .put(at + "/BT-129", quantity)
                .put(at + "/BT-130", "C62")
                .put(at + "/BT-131", canonical(net))
                .put(at + "/BG-29/BT-146", price)
                .put(at + "/BG-30/BT-151", "Z")
                .put(at + "/BG-31/BT-153", "Service " + (index + 1));
    }

    /**
     * Builds a document of many invoice lines spread over many VAT breakdowns, with the
     * breakdowns and the totals computed from the lines so that no rule of the pack faults.
     *
     * <p>This is the second axis of the cost. An invoice may carry many lines and many VAT
     * breakdowns at once, and a rule that compares a breakdown with the lines of its category
     * has two counts to be linear in rather than one; a document built here therefore states
     * both, and the lines are spread over the rates so that every breakdown has something to
     * be compared with until the rates outnumber the lines.
     *
     * @param lines      how many invoice lines
     * @param breakdowns how many VAT breakdowns, each at a rate of its own
     * @return the document
     */
    static SemanticDocument breakdowns(int lines, int breakdowns) {
        SemanticDocument.Builder builder = SemanticDocument.builder()
                .put("/BT-1", "RE-2026-0001")
                .put("/BT-2", "2026-01-15")
                .put("/BT-3", "380")
                .put("/BT-5", "EUR")
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2017")
                .put("/BG-4/BT-27", "Example GmbH")
                .put("/BG-4/BT-31", "DE123456789")
                .put("/BG-4/BG-5/BT-40", "DE")
                .put("/BG-7/BT-44", "Muster AG")
                .put("/BG-7/BG-8/BT-55", "DE");
        BigDecimal net = new BigDecimal("12.5");
        BigDecimal[] taxable = new BigDecimal[breakdowns];
        Arrays.fill(taxable, BigDecimal.ZERO);
        for (int index = 0; index < lines; index++) {
            int bucket = index % breakdowns;
            String at = "/BG-25/" + index;
            builder.put(at + "/BT-126", Integer.toString(index + 1))
                    .put(at + "/BT-129", "1")
                    .put(at + "/BT-130", "C62")
                    .put(at + "/BT-131", canonical(net))
                    .put(at + "/BG-29/BT-146", canonical(net))
                    .put(at + "/BG-30/BT-151", "S")
                    .put(at + "/BG-30/BT-152", rate(bucket))
                    .put(at + "/BG-31/BT-153", "Service " + (index + 1));
            taxable[bucket] = taxable[bucket].add(net);
        }
        BigDecimal lineTotal = BigDecimal.ZERO;
        BigDecimal vatTotal = BigDecimal.ZERO;
        for (int bucket = 0; bucket < breakdowns; bucket++) {
            BigDecimal base = taxable[bucket];
            BigDecimal percentage = new BigDecimal(rate(bucket));
            BigDecimal vat = base.multiply(percentage)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            String at = "/BG-23/" + bucket;
            builder.put(at + "/BT-116", canonical(base.setScale(2, RoundingMode.HALF_UP)))
                    .put(at + "/BT-117", canonical(vat))
                    .put(at + "/BT-118", "S")
                    .put(at + "/BT-119", rate(bucket));
            lineTotal = lineTotal.add(base);
            vatTotal = vatTotal.add(vat);
        }
        String sum = canonical(lineTotal.setScale(2, RoundingMode.HALF_UP));
        String vat = canonical(vatTotal.setScale(2, RoundingMode.HALF_UP));
        String gross = canonical(lineTotal.add(vatTotal).setScale(2, RoundingMode.HALF_UP));
        return builder
                .put("/BG-22/BT-106", sum)
                .put("/BG-22/BT-109", sum)
                .put("/BG-22/BT-110", vat)
                .put("/BG-22/BT-112", gross)
                .put("/BG-22/BT-115", gross)
                .build();
    }

    /** Returns the VAT rate of one breakdown, as a percentage, distinct for every bucket. */
    private static String rate(int bucket) {
        return canonical(BigDecimal.valueOf(bucket + 1L, 2));
    }

    /**
     * Builds a document of many invoice lines, with the document totals recomputed from
     * them, as the scale generator of {@code conformance/scale} does.
     *
     * <p>The lines are built here rather than converted from a generated XML instance
     * because the XSLT import of a document of this size takes minutes and this measurement
     * is about the rule engine: it is the same semantic document either way, and the point
     * is what the engine costs once the document exists.
     *
     * @param lines how many invoice lines
     * @return the document
     */
    static SemanticDocument scale(int lines) {
        SemanticDocument.Builder builder = SemanticDocument.builder()
                .put("/BT-1", "RE-2026-0001")
                .put("/BT-2", "2026-01-15")
                .put("/BT-3", "380")
                .put("/BT-5", "EUR")
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2017")
                .put("/BG-4/BT-27", "Example GmbH")
                .put("/BG-4/BG-5/BT-40", "DE")
                .put("/BG-7/BT-44", "Muster AG")
                .put("/BG-7/BG-8/BT-55", "DE");
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < lines; index++) {
            line(builder, index, "1", "12.5");
            total = total.add(new BigDecimal("12.5"));
        }
        String sum = canonical(total.setScale(2, RoundingMode.HALF_UP));
        return builder
                .put("/BG-22/BT-106", sum)
                .put("/BG-22/BT-109", sum)
                .put("/BG-22/BT-112", sum)
                .put("/BG-22/BT-115", sum)
                .put("/BG-23/0/BT-116", sum)
                .put("/BG-23/0/BT-117", "0")
                .put("/BG-23/0/BT-118", "Z")
                .build();
    }
}
