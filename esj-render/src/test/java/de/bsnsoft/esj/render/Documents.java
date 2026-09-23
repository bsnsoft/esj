package de.bsnsoft.esj.render;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import de.bsnsoft.esj.xr.XrImporter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The documents the tests of this module build rather than read.
 *
 * <p>The conformance corpus and the examples of the repository are real invoices and
 * answer what a renderer does with one. They cannot answer three other questions, because
 * no real invoice asks them: what a rendering does with every business term of the model
 * at once, what it does with the sub invoice lines of the XRechnung extension, and whether
 * it keeps its hands off a gross figure it was never given.
 */
final class Documents {

    private static final Registry REGISTRY = XrImporter.defaultRegistry();

    private Documents() {
        throw new AssertionError("no instances");
    }

    /**
     * Builds a document that carries a value at every path of the registry. Groups nest as
     * the registry says they do, a repeatable group gets one occurrence, and the one group
     * of the XRechnung extension that contains itself is stopped where it would repeat.
     * Every content is unique, so that a value found in a rendering is that value and not
     * another one that happens to read the same.
     *
     * @return the document
     */
    static SemanticDocument everyTerm() {
        return everyTerm(REGISTRY);
    }

    /**
     * Builds the same document over a registry of the caller's choosing, which is how the
     * question is asked of an edition other than the default one: the rendering is driven
     * by the registry of the document's own edition, so the document that exercises all of
     * it is built from that registry too.
     *
     * @param registry the registry whose paths the document carries a value at
     * @return the document, naming the edition of that registry
     */
    static SemanticDocument everyTerm(Registry registry) {
        SemanticDocument.Builder builder =
                SemanticDocument.builder().semanticModel(registry.semanticModel());
        int[] counter = {0};
        for (Term term : registry.rootTerms()) {
            place(registry, term, "", builder, new ArrayList<>(), counter);
        }
        return builder.build();
    }

    /**
     * Builds an invoice of one line that carries two sub invoice lines of the XRechnung
     * extension, one of which carries a sub line of its own. The amounts of a sub line are
     * its own; nothing in the rendering adds them up.
     *
     * @return the document
     */
    static SemanticDocument withSubLines() {
        SemanticDocument.Builder builder = skeleton();
        builder.put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "300.00")
                .put("/BG-25/0/BG-29/BT-146", "300.00")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-30/BT-152", "19")
                .put("/BG-25/0/BG-31/BT-153", "Assembly, complete")
                .put("/BG-25/0/BG-DEX-01/0/BT-126", "1.1")
                .put("/BG-25/0/BG-DEX-01/0/BT-129", "2")
                .put("/BG-25/0/BG-DEX-01/0/BT-130", "H87")
                .put("/BG-25/0/BG-DEX-01/0/BT-131", "120.00")
                .put("/BG-25/0/BG-DEX-01/0/BG-DEX-07/BT-146", "60.00")
                .put("/BG-25/0/BG-DEX-01/0/BG-DEX-06/BT-151", "S")
                .put("/BG-25/0/BG-DEX-01/0/BG-DEX-02/BT-153", "Bracket")
                .put("/BG-25/0/BG-DEX-01/1/BT-126", "1.2")
                .put("/BG-25/0/BG-DEX-01/1/BT-129", "3")
                .put("/BG-25/0/BG-DEX-01/1/BT-130", "HUR")
                .put("/BG-25/0/BG-DEX-01/1/BT-131", "180.00")
                .put("/BG-25/0/BG-DEX-01/1/BG-DEX-07/BT-146", "60.00")
                .put("/BG-25/0/BG-DEX-01/1/BG-DEX-06/BT-151", "S")
                .put("/BG-25/0/BG-DEX-01/1/BG-DEX-02/BT-153", "Fitting")
                .put("/BG-25/0/BG-DEX-01/1/BG-DEX-01/0/BT-126", "1.2.1")
                .put("/BG-25/0/BG-DEX-01/1/BG-DEX-01/0/BT-129", "1")
                .put("/BG-25/0/BG-DEX-01/1/BG-DEX-01/0/BT-130", "HUR")
                .put("/BG-25/0/BG-DEX-01/1/BG-DEX-01/0/BT-131", "60.00")
                .put("/BG-25/0/BG-DEX-01/1/BG-DEX-01/0/BG-DEX-07/BT-146", "60.00")
                .put("/BG-25/0/BG-DEX-01/1/BG-DEX-01/0/BG-DEX-06/BT-151", "S")
                .put("/BG-25/0/BG-DEX-01/1/BG-DEX-01/0/BG-DEX-02/BT-153", "Adjustment");
        totals(builder, "300.00", "57.00", "357.00", "19");
        return builder.build();
    }

    /**
     * Builds an invoice of one line: two units at 100.00 each, 200.00 net, 19 per cent.
     * Two figures follow from that and stand nowhere in the document — the gross of the
     * line, 238.00, which appears only as the total, and the gross unit price, 119.00,
     * which appears nowhere at all. A renderer that computed either would print it, and a
     * test can look for it.
     *
     * @return the document
     */
    static SemanticDocument oneLineAtNineteenPerCent() {
        SemanticDocument.Builder builder = skeleton();
        builder.put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "2")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "200.00")
                .put("/BG-25/0/BG-29/BT-146", "100.00")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-30/BT-152", "19")
                .put("/BG-25/0/BG-31/BT-153", "Consulting service");
        totals(builder, "200.00", "38.00", "238.00", "19");
        builder.put("/BG-22/BT-114", "0.00");
        return builder.build();
    }

    /**
     * Builds an invoice of one line whose price group carries only the terms named, so
     * that a rendering can be asked what it does with a group the layout has no complete
     * reading for. The price (BT-146) says what one unit costs, the base quantity
     * (BT-149) and its unit (BT-150) say what that price is for, and a document may state
     * any of the three without the others.
     *
     * @param price        the net price of an item, or {@code null} for a group without one
     * @param baseQuantity the quantity the price is for, or {@code null}
     * @param baseUnit     the unit of that quantity, or {@code null}
     * @return the document
     */
    static SemanticDocument withPartialPriceDetails(String price, String baseQuantity,
                                                    String baseUnit) {
        SemanticDocument.Builder builder = skeleton();
        builder.put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "2")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "200.00")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-30/BT-152", "19")
                .put("/BG-25/0/BG-31/BT-153", "Consulting service");
        if (price != null) {
            builder.put("/BG-25/0/BG-29/BT-146", price);
        }
        if (baseQuantity != null) {
            builder.put("/BG-25/0/BG-29/BT-149", baseQuantity);
        }
        if (baseUnit != null) {
            builder.put("/BG-25/0/BG-29/BT-150", baseUnit);
        }
        totals(builder, "200.00", "38.00", "238.00", "19");
        return builder.build();
    }

    /**
     * Builds an invoice of as many lines as asked for, each of them with a description
     * that hangs under its row. The identifier of a line and the token in its description
     * are different words, so that a test can ask on which page each of the two landed.
     *
     * @param lines how many lines the invoice has
     * @return the document
     */
    static SemanticDocument withDetailedLines(int lines) {
        SemanticDocument.Builder builder = skeleton();
        for (int line = 0; line < lines; line++) {
            String prefix = "/BG-25/" + line;
            builder.put(prefix + "/BT-126", "ROW" + line)
                    .put(prefix + "/BT-129", "1")
                    .put(prefix + "/BT-130", "C62")
                    .put(prefix + "/BT-131", "100.00")
                    .put(prefix + "/BG-29/BT-146", "100.00")
                    .put(prefix + "/BG-30/BT-151", "S")
                    .put(prefix + "/BG-30/BT-152", "19")
                    .put(prefix + "/BG-31/BT-153", "Item")
                    .put(prefix + "/BG-31/BT-154", "NOTE" + line);
        }
        totals(builder, "100.00", "19.00", "119.00", "19");
        return builder.build();
    }

    /**
     * Builds an invoice whose lines carry none, one and two lines hanging under their row
     * in turn, so that a rendering can be asked whether its rows stand at one rhythm.
     *
     * <p>A row without hanging lines and a row with them are the two cases the rhythm is
     * about, and a row with two of them measures how tall one hanging line is, which is
     * what tells a rhythm apart from a missing hairline without asking the layout.
     *
     * @param lines how many lines the invoice has
     * @return the document
     */
    static SemanticDocument withLinesDetailedInTurn(int lines) {
        SemanticDocument.Builder builder = skeleton();
        for (int line = 0; line < lines; line++) {
            String prefix = "/BG-25/" + line;
            builder.put(prefix + "/BT-126", "ROW" + line)
                    .put(prefix + "/BT-129", "1")
                    .put(prefix + "/BT-130", "C62")
                    .put(prefix + "/BT-131", "100.00")
                    .put(prefix + "/BG-29/BT-146", "100.00")
                    .put(prefix + "/BG-30/BT-151", "S")
                    .put(prefix + "/BG-30/BT-152", "19")
                    .put(prefix + "/BG-31/BT-153", "Item");
            int hanging = line % 3;
            if (hanging >= 1) {
                builder.put(prefix + "/BG-31/BT-154", "NOTE" + line);
            }
            if (hanging >= 2) {
                builder.put(prefix + "/BG-31/BG-32/0/BT-160", "MARK" + line)
                        .put(prefix + "/BG-31/BG-32/0/BT-161", "VALUE" + line);
            }
        }
        totals(builder, "100.00", "19.00", "119.00", "19");
        return builder.build();
    }

    /**
     * Builds an invoice of one line whose description is long enough to run over several
     * pages, so that a rendering can be asked what it writes above the part of it that
     * opens a page the row of the line never reached.
     *
     * @param words how many words the description carries
     * @return the document
     */
    static SemanticDocument withALineDescribedAtLength(int words) {
        StringBuilder description = new StringBuilder();
        for (int word = 0; word < words; word++) {
            description.append(word == 0 ? "" : " ").append("word").append(word);
        }
        SemanticDocument.Builder builder = skeleton();
        builder.put("/BG-25/0/BT-126", "ROW1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "100.00")
                .put("/BG-25/0/BG-29/BT-146", "100.00")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-30/BT-152", "19")
                .put("/BG-25/0/BG-31/BT-153", "Item")
                .put("/BG-25/0/BG-31/BT-154", description.toString());
        totals(builder, "100.00", "19.00", "119.00", "19");
        return builder.build();
    }

    /**
     * Builds an invoice of one line whose <em>item name</em> is long enough to run over
     * several pages, so that a rendering can be asked what it writes above the part of a
     * row that opens a page the head of the row never reached. The description of
     * {@link #withALineDescribedAtLength(int)} hangs under the row and flows on its own;
     * this text stands in a cell of the row itself.
     *
     * @param words how many words the item name carries
     * @return the document
     */
    static SemanticDocument withALineNamedAtLength(int words) {
        StringBuilder name = new StringBuilder();
        for (int word = 0; word < words; word++) {
            name.append(word == 0 ? "" : " ").append("word").append(word);
        }
        SemanticDocument.Builder builder = skeleton();
        builder.put("/BG-25/0/BT-126", "ROW1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "100.00")
                .put("/BG-25/0/BG-29/BT-146", "100.00")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-30/BT-152", "19")
                .put("/BG-25/0/BG-31/BT-153", name.toString());
        totals(builder, "100.00", "19.00", "119.00", "19");
        return builder.build();
    }

    /**
     * Builds a consumer invoice: two lines priced gross, and the figures the buyer was
     * shown recorded as terms of the B2C extension beside the net terms of the standard.
     * The core of it is a conformant net invoice — 99.99 gross at 19 per cent is 84.03 net
     * — and nothing of the extension follows arithmetically from anything of the core.
     *
     * @return the document
     */
    static SemanticDocument grossB2cInvoice() {
        SemanticDocument.Builder builder = skeleton();
        builder.put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "3")
                .put("/BG-25/0/BT-130", "H87")
                .put("/BG-25/0/BT-131", "252.09")
                .put("/BG-25/0/BG-29/BT-146", "84.03")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-30/BT-152", "19")
                .put("/BG-25/0/BG-31/BT-153", "Shower fitting")
                .put("/BG-25/0/BT-B2C-001", "99.99")
                .put("/BG-25/0/BT-B2C-002", "299.97")
                .put("/BG-25/0/BT-B2C-003", "47.88")
                .put("/BG-25/1/BT-126", "2")
                .put("/BG-25/1/BT-129", "1")
                .put("/BG-25/1/BT-130", "H87")
                .put("/BG-25/1/BT-131", "16.80")
                .put("/BG-25/1/BG-29/BT-146", "16.80")
                .put("/BG-25/1/BG-30/BT-151", "S")
                .put("/BG-25/1/BG-30/BT-152", "19")
                .put("/BG-25/1/BG-31/BT-153", "Sealing tape")
                .put("/BG-25/1/BT-B2C-001", "19.99")
                .put("/BG-25/1/BT-B2C-002", "19.99");
        totals(builder, "268.89", "51.09", "319.98", "19");
        builder.put("/BT-B2C-010", "319.96");
        return builder.build();
    }

    /**
     * Builds the invoice of {@link #oneLineAtNineteenPerCent()} with one more value on its
     * line: a term of an extension whose name reads like a gross figure. Nothing of the
     * core model says it, nothing follows from it, and a rendering has to print it as what
     * it is — a value of an extension — rather than as a figure of the standard.
     *
     * @param grossUnitPrice the content of the extension term
     * @return the document
     */
    static SemanticDocument withAGrossUnitPriceExtension(String grossUnitPrice) {
        SemanticDocument.Builder builder = SemanticDocument.builder();
        oneLineAtNineteenPerCent().values().forEach(builder::put);
        builder.put("/BG-25/0/BT-B2C-001", grossUnitPrice);
        return builder.build();
    }

    /** Returns the envelope every built document shares: parties, dates, currency. */
    private static SemanticDocument.Builder skeleton() {
        return SemanticDocument.builder()
                .put("/BT-1", "RE-2026-0815")
                .put("/BT-2", "2026-03-01")
                .put("/BT-3", "380")
                .put("/BT-5", "EUR")
                .put("/BG-2/BT-24", "urn:cen.eu:en16931:2017")
                .put("/BG-4/BT-27", "Example GmbH")
                .put("/BG-4/BG-5/BT-40", "DE")
                .put("/BG-7/BT-44", "Muster AG")
                .put("/BG-7/BG-8/BT-55", "DE");
    }

    /** Adds one VAT category and the totals that follow from it. */
    private static void totals(SemanticDocument.Builder builder, String net, String vat,
                               String gross, String rate) {
        builder.put("/BG-23/0/BT-116", net)
                .put("/BG-23/0/BT-117", vat)
                .put("/BG-23/0/BT-118", "S")
                .put("/BG-23/0/BT-119", rate)
                .put("/BG-22/BT-106", net)
                .put("/BG-22/BT-109", net)
                .put("/BG-22/BT-110", vat)
                .put("/BG-22/BT-112", gross)
                .put("/BG-22/BT-115", gross);
    }

    private static void place(Registry registry, Term term, String prefix,
                              SemanticDocument.Builder builder,
                              List<String> open, int[] counter) {
        String path = prefix + "/" + term.id() + (term.isRepeatable() ? "/0" : "");
        if (!term.isGroup()) {
            builder.put(SemanticPath.of(path), value(term, ++counter[0]));
            return;
        }
        if (open.contains(term.id())) {
            return;
        }
        open.add(term.id());
        for (Term child : registry.children(term.id())) {
            place(registry, child, path, builder, open, counter);
        }
        open.remove(term.id());
    }

    /**
     * Builds an invoice whose seller states so much about itself that the foot of a letter
     * could not hold it: a legal information of some thousand characters, beside the
     * address and the registers a foot carries anyway.
     *
     * <p>No instance of the corpus asks this, and the question it asks is the one a foot
     * that is furniture of a page has to answer: a band is reserved before the page is
     * filled, so a band nobody could reserve has to become something else rather than a
     * block drawn over the letter.
     *
     * @return the document
     */
    static SemanticDocument withMoreSellerDetailsThanAFootHolds() {
        StringBuilder legal = new StringBuilder();
        for (int sentence = 0; sentence < 60; sentence++) {
            legal.append("Managing directors Alex Beispiel and Kim Muster, ")
                    .append("registered at the district court of Beispielstadt. ");
        }
        return skeleton()
                .put("/BG-4/BT-30", "HRB 12345")
                .put("/BG-4/BT-33", legal.toString())
                .put("/BG-4/BG-5/BT-35", "Musterweg 12")
                .put("/BG-4/BG-5/BT-37", "Beispielstadt")
                .put("/BG-4/BG-5/BT-38", "10117")
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "300.00")
                .put("/BG-25/0/BG-29/BT-146", "300.00")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-31/BT-153", "Assembly")
                .build();
    }

    /**
     * Builds an invoice whose remittance information is longer than the payment code may
     * carry: two hundred characters where the guideline allows a hundred and forty.
     *
     * <p>No instance of the corpus states one, and what it asks is what the letter does
     * when the code carries less than the page beside it says.
     *
     * @return the document
     */
    static SemanticDocument withARemittanceTextLongerThanACodeCarries() {
        return skeleton()
                .put("/BG-16/BT-81", "58")
                .put("/BG-16/BT-83", "Reference ".repeat(19) + "Reference0")
                .put("/BG-16/BG-17/0/BT-84", "DE89370400440532013000")
                .put("/BG-16/BG-17/0/BT-85", "Example GmbH")
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "300.00")
                .put("/BG-25/0/BG-29/BT-146", "300.00")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-31/BT-153", "Assembly")
                .put("/BG-22/BT-115", "357.00")
                .build();
    }

    /**
     * Builds an invoice whose VAT breakdown has so many categories that the block of
     * totals is taller than a page and has to flow over one.
     *
     * <p>Every category is one rate and one line of the invoice, so the document adds up:
     * the figures are what the rendering prints, and what the case is about is where the
     * block breaks rather than what it says.
     *
     * @param categories how many categories the breakdown carries
     * @return the document
     */
    static SemanticDocument withManyVatCategories(int categories) {
        SemanticDocument.Builder builder = skeleton();
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        for (int category = 0; category < categories; category++) {
            String rate = String.valueOf(category + 1);
            BigDecimal base = new BigDecimal("100.00");
            BigDecimal tax = base.multiply(new BigDecimal(rate))
                    .movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
            String prefix = "/BG-25/" + category;
            builder.put(prefix + "/BT-126", "ROW" + category)
                    .put(prefix + "/BT-129", "1")
                    .put(prefix + "/BT-130", "C62")
                    .put(prefix + "/BT-131", base.toPlainString())
                    .put(prefix + "/BG-29/BT-146", base.toPlainString())
                    .put(prefix + "/BG-30/BT-151", "S")
                    .put(prefix + "/BG-30/BT-152", rate)
                    .put(prefix + "/BG-31/BT-153", "Item " + category);
            builder.put("/BG-23/" + category + "/BT-116", base.toPlainString())
                    .put("/BG-23/" + category + "/BT-117", tax.toPlainString())
                    .put("/BG-23/" + category + "/BT-118", "S")
                    .put("/BG-23/" + category + "/BT-119", rate);
            net = net.add(base);
            vat = vat.add(tax);
        }
        builder.put("/BG-22/BT-106", net.toPlainString())
                .put("/BG-22/BT-109", net.toPlainString())
                .put("/BG-22/BT-110", vat.toPlainString())
                .put("/BG-22/BT-112", net.add(vat).toPlainString())
                .put("/BG-22/BT-115", net.add(vat).toPlainString());
        return builder.build();
    }

    /**
     * Builds an invoice that states two payment instructions, each a credit transfer into
     * one account of its own.
     *
     * <p>The case the numbering of the payment block is about, and the one it used to miss:
     * with one account under each instruction, nothing under an instruction repeats, so
     * nothing there is numbered, and the page carried two rows called <i>IBAN</i> and a
     * code that said which of them it paid into by saying nothing.
     *
     * <p>BG-16 is 0..1 in every registry of this build, so this document is one the model
     * refuses: it is built here to be drawn, not to be validated, and the layout draws it
     * because a layout draws what it is handed.
     *
     * @return the document
     */
    static SemanticDocument withTwoCreditTransferInstructions() {
        SemanticDocument.Builder builder = skeleton()
                .put("/BG-16/0/BT-81", "58")
                .put("/BG-16/0/BG-17/0/BT-84", "DE89370400440532013000")
                .put("/BG-16/0/BG-17/0/BT-85", "Example GmbH")
                .put("/BG-16/1/BT-81", "30")
                .put("/BG-16/1/BG-17/0/BT-84", "DE02120300000000202051")
                .put("/BG-16/1/BG-17/0/BT-85", "Example GmbH")
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "100.00")
                .put("/BG-25/0/BG-29/BT-146", "100.00")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-30/BT-152", "19")
                .put("/BG-25/0/BG-31/BT-153", "Item");
        totals(builder, "100.00", "19.00", "119.00", "19");
        return builder.build();
    }

    /**
     * Builds a final construction invoice that refers to a number of preceding invoices
     * (BG-3), each with its issue date except the third, which is stated without one — so
     * that a line naming them writes both forms.
     *
     * @param invoices how many preceding invoices the document names
     * @return the document
     */
    static SemanticDocument withPrecedingInvoices(int invoices) {
        SemanticDocument.Builder builder = skeleton()
                .set(SemanticPath.of("/BT-3"), SemanticValue.of("877"))
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "100.00")
                .put("/BG-25/0/BG-29/BT-146", "100.00")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-30/BT-152", "19")
                .put("/BG-25/0/BG-31/BT-153", "Item");
        for (int invoice = 0; invoice < invoices; invoice++) {
            builder.put("/BG-3/" + invoice + "/BT-25", "RE-2026-000" + (invoice + 1));
            if (invoice != 2) {
                builder.put("/BG-3/" + invoice + "/BT-26",
                        "2026-0" + (invoice + 1) + "-01");
            }
        }
        totals(builder, "100.00", "19.00", "119.00", "19");
        return builder.build();
    }

    /**
     * Builds an invoice that states several accounts of one credit transfer, each with a
     * holder whose name is too long for the width beside the payment code.
     *
     * <p>The code is drawn beside the first rows of the payment block and the rest of the
     * block stands under it. The names are long enough to need the room the code takes:
     * beside it they wrap, under it they must not.
     *
     * @param accounts how many accounts the instruction has
     * @return the document
     */
    static SemanticDocument withSeveralCreditTransferAccounts(int accounts) {
        List<String> ibans = List.of("DE89370400440532013000", "DE02120300000000202051",
                "DE02100500000054540402", "DE02500105170137075030",
                "DE12500105170648489890", "DE02300209000106531065");
        SemanticDocument.Builder builder = skeleton()
                .put("/BG-16/BT-81", "58")
                .put("/BG-25/0/BT-126", "1")
                .put("/BG-25/0/BT-129", "1")
                .put("/BG-25/0/BT-130", "C62")
                .put("/BG-25/0/BT-131", "100.00")
                .put("/BG-25/0/BG-29/BT-146", "100.00")
                .put("/BG-25/0/BG-30/BT-151", "S")
                .put("/BG-25/0/BG-30/BT-152", "19")
                .put("/BG-25/0/BG-31/BT-153", "Item");
        for (int account = 0; account < accounts; account++) {
            String prefix = "/BG-16/BG-17/" + account;
            builder.put(prefix + "/BT-84", ibans.get(account % ibans.size()))
                    .put(prefix + "/BT-85", "Kontoinhaber Beispiel Systeme und Anlagen "
                            + "Gesellschaft mit beschraenkter Haftung HOLDER" + account);
        }
        totals(builder, "100.00", "19.00", "119.00", "19");
        return builder.build();
    }

    private static SemanticValue value(Term term, int counter) {
        SemanticType type = term.datatype().orElse(SemanticType.TEXT);
        String scheme = null;
        String schemeVersion = null;
        String mimeCode = null;
        String filename = null;
        for (Component component : term.components()) {
            switch (component.role()) {
                case SCHEME -> scheme = "SCHEME-" + term.id() + "-" + counter;
                case SCHEME_VERSION -> schemeVersion = "SCHEMEVERSION-" + term.id() + "-" + counter;
                case MIME_CODE -> mimeCode = "application/pdf";
                case FILENAME -> filename = "attachment-" + term.id() + "-" + counter + ".pdf";
            }
        }
        String content = switch (type) {
            case DATE -> String.format("20%02d-%02d-%02d",
                    10 + counter % 80, 1 + counter % 12, 1 + counter % 28);
            case TIME -> String.format("%02d:%02d:%02dZ",
                    counter % 24, counter % 60, (counter + 7) % 60);
            case AMOUNT, UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE ->
                    (100000 + counter) + "." + String.format("%02d", counter % 100);
            case BINARY_OBJECT -> Base64.getEncoder().encodeToString(
                    ("attachment-" + term.id() + "-" + counter).getBytes(StandardCharsets.UTF_8));
            default -> "VALUE-" + term.id() + "-" + counter;
        };
        return new SemanticValue(content, scheme, schemeVersion, mimeCode, filename);
    }
}
