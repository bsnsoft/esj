package de.bsnsoft.esj.b2c;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * What a gross authoring policy varies.
 *
 * <p>Three of the four are the "unless you tell me how" of the policies: a line allowance or
 * charge and a price base quantity other than one change what a displayed gross figure means,
 * and a policy refuses them until the caller says that the documented rule is the intended
 * one. The fourth is the scale the item net price is cut at.
 *
 * @param netPriceScale             the number of fraction digits the item net price (BT-146)
 *                                  is cut at, at least the six of
 *                                  {@link #DEFAULT_NET_PRICE_SCALE}
 * @param lineAllowancesAndCharges  whether a line allowance (BG-27) or line charge (BG-28)
 *                                  may stand on a line the policy derives; where it does, the
 *                                  gross figure is the price before them and they are applied
 *                                  as net amounts by the formula of EN 16931-1, Annex A.1.1
 * @param baseQuantity              whether an item price base quantity (BT-149) other than
 *                                  one may stand on a line the policy derives; where it does,
 *                                  the displayed gross figure is the price of one base
 *                                  quantity, not of one unit
 * @param maxRoundingAmount         the largest rounding amount (BT-114) the policy may write,
 *                                  or an empty optional for the bound the run derives from
 *                                  itself: a cent per invoice line and a cent per VAT
 *                                  breakdown, which is what its half-up steps to two fraction
 *                                  digits can add up to. A difference beyond the limit is
 *                                  refused rather than called a rounding, so that a mistyped
 *                                  displayed total cannot turn BT-114 into a balancing
 *                                  account. There is no way to switch the limit off; a caller
 *                                  who needs a larger one names it.
 */
public record AuthoringOptions(int netPriceScale,
                               boolean lineAllowancesAndCharges,
                               boolean baseQuantity,
                               Optional<BigDecimal> maxRoundingAmount) {

    /**
     * The scale an item net price derived from a gross price is cut at where the caller
     * names none: six fraction digits.
     *
     * <p>Item Net Price is of the unlimited semantic data type Unit Price Amount (EN 16931-1,
     * 6.5, AC 8), which exists because a net price cut from a gross price rarely terminates.
     * Six digits is the floor of this library and not a rule of the standard; a caller may
     * raise it and cannot lower it.
     */
    public static final int DEFAULT_NET_PRICE_SCALE = 6;

    private static final AuthoringOptions STANDARD =
            new AuthoringOptions(DEFAULT_NET_PRICE_SCALE, false, false, Optional.empty());

    /**
     * Checks the scale and the limit.
     *
     * @param netPriceScale             the number of fraction digits the item net price (BT-146)
     *                                  is cut at, at least the six of
     *                                  {@link #DEFAULT_NET_PRICE_SCALE}
     * @param lineAllowancesAndCharges  whether a line allowance (BG-27) or line charge (BG-28)
     *                                  may stand on a line the policy derives; where it does, the
     *                                  gross figure is the price before them and they are applied
     *                                  as net amounts by the formula of EN 16931-1, Annex A.1.1
     * @param baseQuantity              whether an item price base quantity (BT-149) other than
     *                                  one may stand on a line the policy derives; where it does,
     *                                  the displayed gross figure is the price of one base
     *                                  quantity, not of one unit
     * @param maxRoundingAmount         the largest rounding amount (BT-114) the policy may write,
     *                                  or an empty optional for the bound the run derives from
     *                                  itself: a cent per invoice line and a cent per VAT
     *                                  breakdown, which is what its half-up steps to two fraction
     *                                  digits can add up to. A difference beyond the limit is
     *                                  refused rather than called a rounding, so that a mistyped
     *                                  displayed total cannot turn BT-114 into a balancing
     *                                  account. There is no way to switch the limit off; a caller
     *                                  who needs a larger one names it.
     * @throws IllegalArgumentException if the scale is below
     *                                  {@link #DEFAULT_NET_PRICE_SCALE}, or if the limit is
     *                                  negative
     * @throws NullPointerException     if {@code maxRoundingAmount} is {@code null}
     */
    public AuthoringOptions {
        if (netPriceScale < DEFAULT_NET_PRICE_SCALE) {
            throw new IllegalArgumentException("the item net price (BT-146) is cut at "
                    + DEFAULT_NET_PRICE_SCALE + " fraction digits or more, and " + netPriceScale
                    + " is fewer");
        }
        Objects.requireNonNull(maxRoundingAmount, "maxRoundingAmount");
        if (maxRoundingAmount.filter(limit -> limit.signum() < 0).isPresent()) {
            throw new IllegalArgumentException("the largest rounding amount a policy may write"
                    + " is not negative, and " + maxRoundingAmount.orElseThrow().toPlainString()
                    + " is");
        }
    }

    /**
     * Returns the options every policy constant carries: six fraction digits, no line
     * allowance or charge, no base quantity other than one, and the rounding amount bounded
     * by what the half-up steps of the run add up to.
     *
     * @return the standard options
     */
    public static AuthoringOptions standard() {
        return STANDARD;
    }

    /**
     * Returns these options with another scale for the item net price.
     *
     * @param scale the number of fraction digits, at least
     *              {@link #DEFAULT_NET_PRICE_SCALE}
     * @return the options
     * @throws IllegalArgumentException if the scale is below
     *                                  {@link #DEFAULT_NET_PRICE_SCALE}
     */
    public AuthoringOptions withNetPriceScale(int scale) {
        return new AuthoringOptions(scale, lineAllowancesAndCharges, baseQuantity,
                maxRoundingAmount);
    }

    /**
     * Returns these options with line allowances and charges admitted or refused.
     *
     * @param admitted whether a line allowance (BG-27) or line charge (BG-28) may stand on a
     *                 line the policy derives
     * @return the options
     */
    public AuthoringOptions withLineAllowancesAndCharges(boolean admitted) {
        return new AuthoringOptions(netPriceScale, admitted, baseQuantity, maxRoundingAmount);
    }

    /**
     * Returns these options with a price base quantity other than one admitted or refused.
     *
     * @param admitted whether an item price base quantity (BT-149) other than one may stand
     *                 on a line the policy derives
     * @return the options
     */
    public AuthoringOptions withBaseQuantity(boolean admitted) {
        return new AuthoringOptions(netPriceScale, lineAllowancesAndCharges, admitted,
                maxRoundingAmount);
    }

    /**
     * Returns these options with a limit on the rounding amount.
     *
     * @param limit the largest rounding amount (BT-114) the policy may write, or
     *              {@code null} for the bound the run derives from itself
     * @return the options
     * @throws IllegalArgumentException if the limit is negative
     */
    public AuthoringOptions withMaxRoundingAmount(BigDecimal limit) {
        return new AuthoringOptions(netPriceScale, lineAllowancesAndCharges, baseQuantity,
                Optional.ofNullable(limit));
    }
}
