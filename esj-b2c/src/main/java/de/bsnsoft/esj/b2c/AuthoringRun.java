package de.bsnsoft.esj.b2c;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One run of one gross authoring policy over one document builder: the arithmetic the three
 * policies share, the preconditions they share, and the record of what the run wrote.
 *
 * <p>Decimals follow the typing of EN 16931-1, 6.5. Every intermediate result is an exact
 * {@link BigDecimal}. An amount is rounded half up to two decimals once, at the result; a
 * price is of unlimited scale and is cut only where the run computes one itself, at the scale
 * the options name, and every such cut is recorded.
 */
final class AuthoringRun {

    /** The scale of the semantic data type Amount: two fraction digits (Table 26). */
    static final int AMOUNT_SCALE = 2;

    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** The VAT category codes of UNTDID 5305 under which VAT is levied at a rate. */
    private static final List<String> RATED_CATEGORIES = List.of("S", "L", "M");

    /**
     * The VAT category codes under which EN 16931-1 fixes the rate of a line at zero and the
     * line states it all the same (BR-E-05, BR-Z-05, BR-AE-05, BR-K-05, BR-G-05). Category O
     * is not among them: under BR-O-05 a line of that category carries no rate at all.
     */
    private static final List<String> ZERO_RATED_CATEGORIES = List.of("E", "Z", "AE", "K", "G");

    private static final SemanticPath LINES = SemanticPath.group("/BG-25");

    private final SemanticDocument.Builder builder;

    private final String policy;

    private final AuthoringOptions options;

    private final List<AuthoringReport.Written> written = new ArrayList<>();

    private final List<AuthoringReport.Cut> cuts = new ArrayList<>();

    private final List<String> notes = new ArrayList<>();

    AuthoringRun(SemanticDocument.Builder builder, String policy, AuthoringOptions options) {
        this.builder = builder;
        this.policy = policy;
        this.options = options;
    }

    SemanticDocument.Builder builder() {
        return builder;
    }

    AuthoringOptions options() {
        return options;
    }

    List<AuthoringReport.Written> written() {
        return written;
    }

    List<AuthoringReport.Cut> cuts() {
        return cuts;
    }

    List<String> notes() {
        return notes;
    }

    void note(String text) {
        notes.add(text);
    }

    /**
     * Returns how many invoice lines the invoice carries, and refuses an invoice with none.
     *
     * @return the number of instances of BG-25
     */
    int lineCount() {
        int count = builder.occurrences(LINES);
        if (count == 0) {
            throw refusal("at least one invoice line",
                    "the invoice carries no invoice line (BG-25), and there is nothing to price",
                    SemanticPath.root(), "BG-25");
        }
        return count;
    }

    /**
     * Returns the path of one invoice line.
     *
     * @param index the zero-based occurrence index
     * @return the group instance path
     */
    static SemanticPath line(int index) {
        return SemanticPath.group("/BG-25/" + index);
    }

    /**
     * Returns the VAT rate of an invoice line, as the amounts of that line are grossed up by.
     *
     * @param line the group instance path of the invoice line
     * @return the rate in per cent, zero where the category levies none
     */
    BigDecimal rate(SemanticPath line) {
        String category = text(line, "BG-30/BT-151").orElseThrow(() -> refusal(
                "a VAT category code on every line",
                "the invoice line at " + line + " states no invoiced item VAT category code"
                        + " (BT-151), so there is no rate to convert its gross figure by",
                line, "BT-151"));
        Optional<BigDecimal> rate = decimal(line, "BG-30/BT-152");
        if (rate.isEmpty() && RATED_CATEGORIES.contains(category)) {
            throw refusal("a VAT rate on every line of a category that is levied at one",
                    "the invoice line at " + line + " is categorised \"" + category + "\", under"
                            + " which VAT is levied at a rate, and states no invoiced item VAT"
                            + " rate (BT-152)",
                    line, "BT-152");
        }
        if (rate.isEmpty() && ZERO_RATED_CATEGORIES.contains(category)) {
            throw refusal("a VAT rate on every line of a category whose rate is zero",
                    "the invoice line at " + line + " is categorised \"" + category + "\", under"
                            + " which EN 16931-1 fixes the invoiced item VAT rate (BT-152) at"
                            + " zero and asks the line to state it, and the line states none;"
                            + " deriving the line without it would write an invoice its own"
                            + " business rules reject",
                    line, "BT-152");
        }
        return rate.orElse(BigDecimal.ZERO);
    }

    /**
     * Returns the item price base quantity of an invoice line, and refuses one other than
     * where the options admit it.
     *
     * @param line the group instance path of the invoice line
     * @return the base quantity, one where the line states none
     */
    BigDecimal baseQuantity(SemanticPath line) {
        BigDecimal base = decimal(line, "BG-29/BT-149").orElse(BigDecimal.ONE);
        if (base.signum() <= 0) {
            throw refusal("a positive item price base quantity",
                    "the invoice line at " + line + " states an item price base quantity"
                            + " (BT-149) of " + base.toPlainString() + ", which no price can be"
                            + " the price of",
                    line, "BT-149");
        }
        if (base.compareTo(BigDecimal.ONE) != 0 && !options.baseQuantity()) {
            throw refusal("no item price base quantity other than one",
                    "the invoice line at " + line + " states an item price base quantity"
                            + " (BT-149) of " + base.toPlainString() + ", so the gross figure it"
                            + " carries is the price of that many units and not of one;"
                            + " AuthoringOptions.withBaseQuantity(true) says that this is meant",
                    line, "BT-149");
        }
        return base;
    }

    /**
     * Returns the invoiced quantity of an invoice line.
     *
     * @param line the group instance path of the invoice line
     * @return the quantity
     */
    BigDecimal quantity(SemanticPath line) {
        BigDecimal quantity = decimal(line, "BT-129").orElseThrow(() -> refusal(
                "an invoiced quantity on every line",
                "the invoice line at " + line + " states no invoiced quantity (BT-129)",
                line, "BT-129"));
        if (quantity.signum() == 0) {
            throw refusal("a non-zero invoiced quantity",
                    "the invoice line at " + line + " states an invoiced quantity (BT-129) of"
                            + " zero, so no unit price follows from its line total",
                    line, "BT-129");
        }
        return quantity;
    }

    /**
     * Refuses a line allowance or line charge where the options do not admit one.
     *
     * @param line the group instance path of the invoice line
     */
    void checkLineAdjustments(SemanticPath line) {
        if (options.lineAllowancesAndCharges()) {
            return;
        }
        refuseGroup(line, "BG-27", "an invoice line allowance",
                "no line allowance and no line charge");
        refuseGroup(line, "BG-28", "an invoice line charge",
                "no line allowance and no line charge");
    }

    private void refuseGroup(SemanticPath line, String groupId, String what,
                             String precondition) {
        SemanticPath group = SemanticPath.group(line + "/" + groupId);
        if (builder.occurrences(group) > 0) {
            throw refusal(precondition,
                    "the invoice line at " + line + " carries " + what + " (" + groupId + "),"
                            + " and the gross figure it states does not say whether it is the"
                            + " figure before or after it;"
                            + " AuthoringOptions.withLineAllowancesAndCharges(true) says that "
                            + adjustmentReading(),
                    line, groupId);
        }
    }

    /**
     * Returns what {@code withLineAllowancesAndCharges(true)} means for the policy of this
     * run: a displayed unit price is the price before the line allowances and charges, and a
     * displayed line total, or a share of an agreed total, is the figure after them.
     *
     * @return the phrase, without a leading or trailing stop
     */
    String adjustmentReading() {
        return "GROSS_UNIT_AUTHORING".equals(policy)
                ? "the gross figure is the price of one unit before the line allowances and"
                        + " charges, which are applied afterwards as the net amounts they are"
                : "the gross figure is the total of the line after the line allowances and"
                        + " charges, so what they deduct and add is taken out of the net amount"
                        + " it comes to before the item net price (BT-146) follows from it";
    }

    /**
     * Returns what the line charges of an invoice line add and its line allowances deduct,
     * as one net amount.
     *
     * @param line the group instance path of the invoice line
     * @return the sum of the line charge amounts (BT-141) less the sum of the line allowance
     *         amounts (BT-136), zero where the line carries neither
     */
    BigDecimal lineAdjustments(SemanticPath line) {
        return sum(line, "BG-28", "BT-141").subtract(sum(line, "BG-27", "BT-136"));
    }

    private BigDecimal sum(SemanticPath line, String groupId, String termId) {
        SemanticPath group = SemanticPath.group(line + "/" + groupId);
        BigDecimal sum = BigDecimal.ZERO;
        int count = builder.occurrences(group);
        for (int index = 0; index < count; index++) {
            SemanticPath instance = SemanticPath.group(group + "/" + index);
            sum = sum.add(decimal(instance, termId).orElseThrow(() -> refusal(
                    "an amount on every line allowance and line charge",
                    "the group at " + instance + " states no amount (" + termId + "), so the"
                            + " net figure of its line does not follow from the gross one",
                    instance, termId)));
        }
        return sum;
    }

    /**
     * Writes the item net price an invoice line priced gross as a whole stands for.
     *
     * <p>The gross figure is the total of the line after its line allowances and charges. The
     * line net amount is the one rounding: the exact quotient of that figure and the rate,
     * half up to two decimals, because Amount is of fixed scale; where the quotient did not
     * terminate, the run says so in a note, because the item net price that follows is chosen
     * to carry the rounded amount back and the rounding is visible nowhere else. The item net
     * price is what the net amount, less what the line charges add and plus what its
     * allowances deduct, comes to per unit, cut at the scale the options name.
     *
     * @param line   the group instance path of the invoice line
     * @param gross  the gross total of the line
     * @param source how that gross figure was arrived at, as one English phrase
     * @return the invoice line net amount the derivation is expected to arrive at
     */
    BigDecimal fromGrossLineTotal(SemanticPath line, BigDecimal gross, String source) {
        BigDecimal quantity = quantity(line);
        checkLineAdjustments(line);
        BigDecimal base = baseQuantity(line);
        BigDecimal rate = rate(line);
        Quotient exact = net(gross, rate, AMOUNT_SCALE);
        BigDecimal net = amount(exact.value());
        if (exact.cut()) {
            note("the invoice line net amount (BT-131) of the line at " + line + " is "
                    + source + " at the invoiced item VAT rate (BT-152) " + rate.toPlainString()
                    + " per cent, which does not terminate at the two fraction digits of the"
                    + " semantic data type Amount and was rounded half up to "
                    + net.toPlainString() + "; the item net price (BT-146) below is the price"
                    + " that carries that rounded amount back over the invoiced quantity");
        }
        BigDecimal adjustments = lineAdjustments(line);
        Quotient price = quotient(net.subtract(adjustments).multiply(base), quantity,
                options.netPriceScale());
        String how = source + " at the invoiced item VAT rate (BT-152) " + rate.toPlainString()
                + " per cent, which comes to the invoice line net amount (BT-131) "
                + net.toPlainString();
        if (adjustments.signum() != 0) {
            how = how + ", less the " + adjustments.toPlainString() + " the line charges"
                    + " (BT-141) add and the line allowances (BT-136) deduct";
        }
        writePrice(path(line, "BG-29/BT-146"), "BT-146", price,
                how + ", over the invoiced quantity (BT-129) " + quantity.toPlainString());
        return net;
    }

    /**
     * Reads a decimal value under a group instance.
     *
     * @param parent   the group instance path
     * @param relative the path of the value inside it, for instance {@code BG-30/BT-152}
     * @return the value, or an empty optional where the document carries none
     */
    Optional<BigDecimal> decimal(SemanticPath parent, String relative) {
        return decimal(path(parent, relative));
    }

    /**
     * Reads a decimal value at a path.
     *
     * @param path the value path
     * @return the value, or an empty optional where the document carries none
     */
    Optional<BigDecimal> decimal(SemanticPath path) {
        return builder.value(path).map(SemanticValue::asDecimal);
    }

    private Optional<String> text(SemanticPath parent, String relative) {
        return builder.value(path(parent, relative)).map(SemanticValue::asString);
    }

    /**
     * Returns the path of a value under a group instance.
     *
     * @param parent   the group instance path
     * @param relative the path of the value inside it
     * @return the value path
     */
    static SemanticPath path(SemanticPath parent, String relative) {
        return SemanticPath.of(parent + "/" + relative);
    }

    /**
     * Rounds an amount to the two fraction digits of the semantic data type Amount.
     *
     * @param value the exact amount
     * @return the amount, rounded half up
     */
    static BigDecimal amount(BigDecimal value) {
        return value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Returns the net figure a gross figure stands for at a VAT rate, exactly where the
     * quotient terminates at the given scale and cut half up where it does not.
     *
     * @param gross the gross figure
     * @param rate  the VAT rate in per cent
     * @param scale the number of fraction digits to keep
     * @return the net figure and whether it was cut
     */
    static Quotient net(BigDecimal gross, BigDecimal rate, int scale) {
        return quotient(gross.multiply(HUNDRED), HUNDRED.add(rate), scale);
    }

    /**
     * Divides exactly where the quotient terminates at the given scale, and cuts half up
     * where it does not.
     *
     * @param numerator   the numerator
     * @param denominator the denominator, which is not zero
     * @param scale       the number of fraction digits to keep
     * @return the quotient and whether it was cut
     */
    static Quotient quotient(BigDecimal numerator, BigDecimal denominator, int scale) {
        BigDecimal value = numerator.divide(denominator, scale, RoundingMode.HALF_UP);
        boolean cut = value.multiply(denominator).compareTo(numerator) != 0;
        return new Quotient(value, cut);
    }

    /**
     * Writes a value the policy computed and records it.
     *
     * @param path  the value path
     * @param term  the identifier of the business term
     * @param value the value
     * @param how   how it was arrived at
     */
    void write(SemanticPath path, String term, BigDecimal value, String how) {
        builder.set(path, SemanticValue.ofDecimal(value));
        written.add(new AuthoringReport.Written(path, term, value, how));
    }

    /**
     * Writes a price the policy computed, and records the cut where the exact quotient did
     * not terminate at the scale the options name.
     *
     * @param path     the value path
     * @param term     the identifier of the business term
     * @param quotient the price and whether it was cut
     * @param how      how it was arrived at
     */
    void writePrice(SemanticPath path, String term, Quotient quotient, String how) {
        write(path, term, quotient.value(), how);
        if (quotient.cut()) {
            cuts.add(new AuthoringReport.Cut(path, term, quotient.value(),
                    options.netPriceScale(), how));
        }
    }

    /**
     * Returns a refusal of this policy.
     *
     * @param precondition the precondition that failed
     * @param message      what the policy cannot do and why
     * @param path         where in the invoice the refusal is
     * @param term         which business term or group it is about
     * @return the exception, for the caller to throw
     */
    PolicyPreconditionException refusal(String precondition, String message, SemanticPath path,
                                        String term) {
        return new PolicyPreconditionException(policy, precondition, message, path, term);
    }

    /**
     * A quotient and whether the exact one had more fraction digits than were kept.
     *
     * @param value the quotient as it was kept
     * @param cut   whether the exact quotient was cut half up to get there
     */
    record Quotient(BigDecimal value, boolean cut) {
    }
}
