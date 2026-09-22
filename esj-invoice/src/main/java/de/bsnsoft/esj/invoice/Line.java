package de.bsnsoft.esj.invoice;

import de.bsnsoft.esj.invoice.code.Coded;
import de.bsnsoft.esj.invoice.code.Unit;
import de.bsnsoft.esj.typed.Identifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One invoice line (BG-25): what was supplied, how much of it, at which price and taxed
 * how.
 *
 * <p>A line is opened with the name of the item (BT-153) because that is what a reader of
 * the invoice sees first; the invoiced quantity (BT-129) and its unit of measure
 * (BT-130), the item net price (BT-146) and the VAT of the line (BG-30) follow, and
 * EN 16931-1 makes all four mandatory. The line net amount (BT-131) is not written here:
 * it is derived from the four, plus the line allowances and charges, when the invoice is
 * built.
 *
 * <p>The invoiced quantity, the item net price and the item price base quantity are of
 * the unlimited semantic data types (6.5, AC 8 to AC 10): they are written at the scale
 * the caller gave them and are never rounded, normalised or cut.
 *
 * <p>The line is a value: every method returns a new one and the original is
 * unchanged.
 */
public final class Line {

    private final String identifier;

    private final String name;

    private final String description;

    private final String note;

    private final BigDecimal quantity;

    private final Coded unit;

    private final BigDecimal netPrice;

    private final BigDecimal baseQuantity;

    private final Coded baseQuantityUnit;

    private final Vat vat;

    private final List<Allowance> allowances;

    private final List<Charge> charges;

    private final BillingPeriod period;

    private final Identifier objectIdentifier;

    private final String sellerIdentifier;

    private final String buyerIdentifier;

    private final String purchaseOrderLineReference;

    private final BigDecimal netAmount;

    private Line(Copy copy) {
        this.identifier = copy.identifier;
        this.name = copy.name;
        this.description = copy.description;
        this.note = copy.note;
        this.quantity = copy.quantity;
        this.unit = copy.unit;
        this.netPrice = copy.netPrice;
        this.baseQuantity = copy.baseQuantity;
        this.baseQuantityUnit = copy.baseQuantityUnit;
        this.vat = copy.vat;
        this.allowances = copy.allowances;
        this.charges = copy.charges;
        this.period = copy.period;
        this.objectIdentifier = copy.objectIdentifier;
        this.sellerIdentifier = copy.sellerIdentifier;
        this.buyerIdentifier = copy.buyerIdentifier;
        this.purchaseOrderLineReference = copy.purchaseOrderLineReference;
        this.netAmount = copy.netAmount;
    }

    /**
     * Opens a line for an item (BT-153).
     *
     * @param name the name of the item
     * @return the line
     * @throws IllegalArgumentException if the name is blank
     * @throws NullPointerException     if {@code name} is {@code null}
     */
    public static Line of(String name) {
        Copy copy = new Copy();
        copy.name = Amounts.text(name, "name");
        return new Line(copy);
    }

    /**
     * Returns this line with the identifier it is numbered under (BT-126). A line that
     * states none is numbered by its position when the invoice is built.
     *
     * @param value the line identifier
     * @return a new line
     * @throws IllegalArgumentException if the identifier is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Line identifier(String value) {
        Copy copy = copy();
        copy.identifier = Amounts.text(value, "value");
        return new Line(copy);
    }

    /**
     * Returns this line with a description of the item (BT-154).
     *
     * @param value the description
     * @return a new line
     * @throws IllegalArgumentException if the description is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Line description(String value) {
        Copy copy = copy();
        copy.description = Amounts.text(value, "value");
        return new Line(copy);
    }

    /**
     * Returns this line with a note on it (BT-127).
     *
     * @param value the note
     * @return a new line
     * @throws IllegalArgumentException if the note is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Line note(String value) {
        Copy copy = copy();
        copy.note = Amounts.text(value, "value");
        return new Line(copy);
    }

    /**
     * Returns this line with the invoiced quantity (BT-129) and its unit of measure
     * (BT-130).
     *
     * @param quantity the quantity, at the scale the caller writes it at
     * @param unit     the unit of measure, for example {@code Unit.PIECE}
     * @return a new line
     * @throws NullPointerException if a part is {@code null}
     */
    public Line quantity(BigDecimal quantity, Coded unit) {
        Copy copy = copy();
        copy.quantity = Objects.requireNonNull(quantity, "quantity");
        copy.unit = Objects.requireNonNull(unit, "unit");
        return new Line(copy);
    }

    /**
     * Returns this line with the invoiced quantity (BT-129) and a unit of measure
     * (BT-130) of the generated list.
     *
     * @param quantity the quantity, at the scale the caller writes it at
     * @param unit     the unit of measure
     * @return a new line
     * @throws NullPointerException if a part is {@code null}
     */
    public Line quantity(BigDecimal quantity, Unit unit) {
        return quantity(quantity, (Coded) unit);
    }

    /**
     * Returns this line with the invoiced quantity (BT-129) and its unit of measure
     * (BT-130).
     *
     * @param quantity the quantity, for example {@code 100}
     * @param unit     the unit of measure, for example {@code Unit.PIECE}
     * @return a new line
     * @throws NullPointerException if {@code unit} is {@code null}
     */
    public Line quantity(long quantity, Coded unit) {
        return quantity(BigDecimal.valueOf(quantity), unit);
    }

    /**
     * Returns this line with the invoiced quantity (BT-129) and a unit of measure
     * (BT-130) of the generated list.
     *
     * @param quantity the quantity, for example {@code 100}
     * @param unit     the unit of measure
     * @return a new line
     * @throws NullPointerException if {@code unit} is {@code null}
     */
    public Line quantity(long quantity, Unit unit) {
        return quantity(quantity, (Coded) unit);
    }

    /**
     * Returns this line with the invoiced quantity (BT-129) and its unit of measure
     * (BT-130).
     *
     * @param quantity the quantity as text, for example {@code "1.5"}
     * @param unit     the unit of measure, for example {@code Unit.KILOGRAM}
     * @return a new line
     * @throws IllegalArgumentException if the text is not a decimal number
     * @throws NullPointerException     if a part is {@code null}
     */
    public Line quantity(String quantity, Coded unit) {
        return quantity(Amounts.decimal(quantity, "quantity"), unit);
    }

    /**
     * Returns this line with the invoiced quantity (BT-129) and a unit of measure
     * (BT-130) of the generated list.
     *
     * @param quantity the quantity as text, for example {@code "1.5"}
     * @param unit     the unit of measure
     * @return a new line
     * @throws IllegalArgumentException if the text is not a decimal number
     * @throws NullPointerException     if a part is {@code null}
     */
    public Line quantity(String quantity, Unit unit) {
        return quantity(quantity, (Coded) unit);
    }

    /**
     * Returns this line with the item net price (BT-146), the price of one base quantity
     * of the item after any price discount.
     *
     * @param value the net price, at the scale the caller writes it at
     * @return a new line
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Line unitPrice(BigDecimal value) {
        Copy copy = copy();
        copy.netPrice = Objects.requireNonNull(value, "value");
        return new Line(copy);
    }

    /**
     * Returns this line with the item net price (BT-146).
     *
     * @param value the net price as text, for example {@code "12.345678"}
     * @return a new line
     * @throws IllegalArgumentException if the text is not a decimal number
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Line unitPrice(String value) {
        return unitPrice(Amounts.decimal(value, "value"));
    }

    /**
     * Returns this line with the item net price (BT-146).
     *
     * @param value the net price, for example {@code 12}
     * @return a new line
     */
    public Line unitPrice(long value) {
        return unitPrice(BigDecimal.valueOf(value));
    }

    /**
     * Returns this line with the item price base quantity (BT-149): the number of item
     * units the net price is the price of. A line that states none is priced per unit.
     *
     * @param value the base quantity, at the scale the caller writes it at
     * @return a new line
     * @throws IllegalArgumentException if the base quantity is zero or negative
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Line baseQuantity(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("an item price base quantity (BT-149) is positive,"
                    + " not " + value.toPlainString());
        }
        Copy copy = copy();
        copy.baseQuantity = value;
        return new Line(copy);
    }

    /**
     * Returns this line with the item price base quantity (BT-149).
     *
     * @param value the base quantity, for example {@code 1000}
     * @return a new line
     * @throws IllegalArgumentException if the base quantity is zero or negative
     */
    public Line baseQuantity(long value) {
        return baseQuantity(BigDecimal.valueOf(value));
    }

    /**
     * Returns this line with the item price base quantity (BT-149) and its own unit of
     * measure (BT-150), which EN 16931-1 asks to be the unit of BT-130.
     *
     * @param value the base quantity
     * @param unit  the unit of measure of the base quantity
     * @return a new line
     * @throws IllegalArgumentException if the base quantity is zero or negative
     * @throws NullPointerException     if a part is {@code null}
     */
    public Line baseQuantity(BigDecimal value, Coded unit) {
        Line line = baseQuantity(value);
        Copy copy = line.copy();
        copy.baseQuantityUnit = Objects.requireNonNull(unit, "unit");
        return new Line(copy);
    }

    /**
     * Returns this line with the item price base quantity (BT-149) and a unit of measure
     * (BT-150) of the generated list.
     *
     * @param value the base quantity
     * @param unit  the unit of measure of the base quantity
     * @return a new line
     * @throws IllegalArgumentException if the base quantity is zero or negative
     * @throws NullPointerException     if a part is {@code null}
     */
    public Line baseQuantity(BigDecimal value, Unit unit) {
        return baseQuantity(value, (Coded) unit);
    }

    /**
     * Returns this line with the VAT it is taxed under (BG-30, BT-151 and BT-152).
     *
     * @param value the VAT category and rate
     * @return a new line
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Line vat(Vat value) {
        Copy copy = copy();
        copy.vat = Objects.requireNonNull(value, "value");
        return new Line(copy);
    }

    /**
     * Returns this line with one more allowance on it (BG-27).
     *
     * @param value the allowance
     * @return a new line
     * @throws IllegalArgumentException if the allowance states a VAT, which a line
     *                                  allowance has no term for
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Line allowance(Allowance value) {
        Objects.requireNonNull(value, "value");
        if (value.vat().isPresent()) {
            throw new IllegalArgumentException("an invoice line allowance (BG-27) is taxed with its"
                    + " line and has no VAT category code or rate of its own; the VAT belongs on"
                    + " the line (BG-30) or on a document level allowance (BG-20)");
        }
        Copy copy = copy();
        copy.allowances = append(allowances, value);
        return new Line(copy);
    }

    /**
     * Returns this line with one more charge on it (BG-28).
     *
     * @param value the charge
     * @return a new line
     * @throws IllegalArgumentException if the charge states a VAT, which a line charge
     *                                  has no term for
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Line charge(Charge value) {
        Objects.requireNonNull(value, "value");
        if (value.vat().isPresent()) {
            throw new IllegalArgumentException("an invoice line charge (BG-28) is taxed with its"
                    + " line and has no VAT category code or rate of its own; the VAT belongs on"
                    + " the line (BG-30) or on a document level charge (BG-21)");
        }
        Copy copy = copy();
        copy.charges = append(charges, value);
        return new Line(copy);
    }

    /**
     * Returns this line with the period it is for (BG-26, BT-134 and BT-135).
     *
     * @param value the period
     * @return a new line
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public Line period(BillingPeriod value) {
        Copy copy = copy();
        copy.period = Objects.requireNonNull(value, "value");
        return new Line(copy);
    }

    /**
     * Returns this line with the identifier of the object it invoices (BT-128).
     *
     * @param value  the object identifier
     * @param scheme the identification scheme (BT-128-1), a code of UNTDID 1153
     * @return a new line
     * @throws IllegalArgumentException if a value is blank
     * @throws NullPointerException     if a part is {@code null}
     */
    public Line objectIdentifier(String value, String scheme) {
        Copy copy = copy();
        copy.objectIdentifier = Identifier.of(Amounts.text(value, "value"),
                Amounts.text(scheme, "scheme"));
        return new Line(copy);
    }

    /**
     * Returns this line with the seller's identifier for the item (BT-155).
     *
     * @param value the item seller identifier
     * @return a new line
     * @throws IllegalArgumentException if the identifier is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Line sellerIdentifier(String value) {
        Copy copy = copy();
        copy.sellerIdentifier = Amounts.text(value, "value");
        return new Line(copy);
    }

    /**
     * Returns this line with the buyer's identifier for the item (BT-156).
     *
     * @param value the item buyer identifier
     * @return a new line
     * @throws IllegalArgumentException if the identifier is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Line buyerIdentifier(String value) {
        Copy copy = copy();
        copy.buyerIdentifier = Amounts.text(value, "value");
        return new Line(copy);
    }

    /**
     * Returns this line with the line of the purchase order it answers (BT-132).
     *
     * @param value the referenced purchase order line reference
     * @return a new line
     * @throws IllegalArgumentException if the reference is blank
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public Line purchaseOrderLineReference(String value) {
        Copy copy = copy();
        copy.purchaseOrderLineReference = Amounts.text(value, "value");
        return new Line(copy);
    }

    /**
     * Returns the line identifier (BT-126).
     *
     * @return the identifier, or an empty optional where the line is numbered by position
     */
    public Optional<String> identifier() {
        return Optional.ofNullable(identifier);
    }

    /**
     * Returns the name of the item (BT-153).
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the description of the item (BT-154).
     *
     * @return the description, or an empty optional
     */
    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the note on the line (BT-127).
     *
     * @return the note, or an empty optional
     */
    public Optional<String> note() {
        return Optional.ofNullable(note);
    }

    /**
     * Returns the invoiced quantity (BT-129).
     *
     * @return the quantity, or an empty optional where the line states none
     */
    public Optional<BigDecimal> quantity() {
        return Optional.ofNullable(quantity);
    }

    /**
     * Returns the unit of measure of the invoiced quantity (BT-130).
     *
     * @return the unit, or an empty optional where the line states none
     */
    public Optional<Coded> unit() {
        return Optional.ofNullable(unit);
    }

    /**
     * Returns the item net price (BT-146).
     *
     * @return the price, or an empty optional where the line states none
     */
    public Optional<BigDecimal> unitPrice() {
        return Optional.ofNullable(netPrice);
    }

    /**
     * Returns the item price base quantity (BT-149).
     *
     * @return the base quantity, or an empty optional where the price is per unit
     */
    public Optional<BigDecimal> baseQuantity() {
        return Optional.ofNullable(baseQuantity);
    }

    /**
     * Returns the unit of measure of the item price base quantity (BT-150).
     *
     * @return the unit, or an empty optional
     */
    public Optional<Coded> baseQuantityUnit() {
        return Optional.ofNullable(baseQuantityUnit);
    }

    /**
     * Returns the VAT of the line (BG-30).
     *
     * @return the VAT, or an empty optional where the line states none
     */
    public Optional<Vat> vat() {
        return Optional.ofNullable(vat);
    }

    /**
     * Returns the allowances on the line (BG-27).
     *
     * @return the allowances, in the order they were written
     */
    public List<Allowance> allowances() {
        return allowances;
    }

    /**
     * Returns the charges on the line (BG-28).
     *
     * @return the charges, in the order they were written
     */
    public List<Charge> charges() {
        return charges;
    }

    /**
     * Returns the period the line is for (BG-26).
     *
     * @return the period, or an empty optional
     */
    public Optional<BillingPeriod> period() {
        return Optional.ofNullable(period);
    }

    /**
     * Returns the identifier of the object the line invoices (BT-128).
     *
     * @return the identifier and its scheme, or an empty optional
     */
    public Optional<Identifier> objectIdentifier() {
        return Optional.ofNullable(objectIdentifier);
    }

    /**
     * Returns the seller's identifier for the item (BT-155).
     *
     * @return the identifier, or an empty optional
     */
    public Optional<String> sellerIdentifier() {
        return Optional.ofNullable(sellerIdentifier);
    }

    /**
     * Returns the buyer's identifier for the item (BT-156).
     *
     * @return the identifier, or an empty optional
     */
    public Optional<String> buyerIdentifier() {
        return Optional.ofNullable(buyerIdentifier);
    }

    /**
     * Returns the referenced purchase order line reference (BT-132).
     *
     * @return the reference, or an empty optional
     */
    public Optional<String> purchaseOrderLineReference() {
        return Optional.ofNullable(purchaseOrderLineReference);
    }

    /**
     * Returns the invoice line net amount (BT-131) a document the line was read from
     * carries.
     *
     * <p>A line a caller has written states none: it is derived from the quantity, the
     * price and the allowances and charges of the line when the invoice is built.
     *
     * @return the net amount, or an empty optional
     */
    public Optional<BigDecimal> netAmount() {
        return Optional.ofNullable(netAmount);
    }

    /**
     * Returns what the line invoices and how much of it.
     *
     * @return for example {@code 100 H87 Sensor module SM-100}
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        if (quantity != null) {
            text.append(quantity.toPlainString()).append(' ');
        }
        if (unit != null) {
            text.append(unit.code()).append(' ');
        }
        return text.append(name).toString();
    }

    /**
     * The base the percentage of a line allowance or charge is taken of: the invoiced
     * quantity times the item net price over the item price base quantity, rounded half
     * up to two decimals once, because a base amount is of the type Amount.
     *
     * @return the base amount, or an empty optional where the line states no price or no
     *         quantity
     */
    Optional<BigDecimal> netBase() {
        if (quantity == null || netPrice == null) {
            return Optional.empty();
        }
        BigDecimal divisor = baseQuantity == null ? BigDecimal.ONE : baseQuantity;
        return Optional.of(netPrice.multiply(quantity)
                .divide(divisor, Amounts.AMOUNT_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Returns this line under the identifier a position in the invoice gives it.
     *
     * @param position the position of the line, counted from one
     * @return the line, unchanged where it already states an identifier
     */
    Line numbered(int position) {
        return identifier == null ? identifier(String.valueOf(position)) : this;
    }

    private static <T> List<T> append(List<T> list, T value) {
        List<T> more = new ArrayList<>(list);
        more.add(value);
        return List.copyOf(more);
    }

    private Copy copy() {
        Copy copy = new Copy();
        copy.identifier = identifier;
        copy.name = name;
        copy.description = description;
        copy.note = note;
        copy.quantity = quantity;
        copy.unit = unit;
        copy.netPrice = netPrice;
        copy.baseQuantity = baseQuantity;
        copy.baseQuantityUnit = baseQuantityUnit;
        copy.vat = vat;
        copy.allowances = allowances;
        copy.charges = charges;
        copy.period = period;
        copy.objectIdentifier = objectIdentifier;
        copy.sellerIdentifier = sellerIdentifier;
        copy.buyerIdentifier = buyerIdentifier;
        copy.purchaseOrderLineReference = purchaseOrderLineReference;
        copy.netAmount = netAmount;
        return copy;
    }

    /** The mutable state a new line is assembled from, so that no setter lists every field. */
    static final class Copy {

        String identifier;
        String name;
        String description;
        String note;
        BigDecimal quantity;
        Coded unit;
        BigDecimal netPrice;
        BigDecimal baseQuantity;
        Coded baseQuantityUnit;
        Vat vat;
        List<Allowance> allowances = List.of();
        List<Charge> charges = List.of();
        BillingPeriod period;
        Identifier objectIdentifier;
        String sellerIdentifier;
        String buyerIdentifier;
        String purchaseOrderLineReference;
        BigDecimal netAmount;

        Line line() {
            return new Line(this);
        }
    }
}
