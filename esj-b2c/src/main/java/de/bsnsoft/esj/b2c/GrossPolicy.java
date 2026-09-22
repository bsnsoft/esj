package de.bsnsoft.esj.b2c;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What one gross authoring policy does before the totals are derived: read the gross figures
 * the invoice carries as terms of the B2C extension, and write the net core terms they stand
 * for.
 *
 * <p>A policy writes prices and no totals. {@code GrossAuthoring} runs the derivation of
 * EN 16931-1 afterwards, once, for all three of them, and takes the rounding step that carries
 * the amount due for payment to the gross total the customer agreed to.
 *
 * <p>A policy writes no term of the extension. The extension states what was shown to or
 * agreed with the customer, and that is the caller's to state; a policy reads those figures
 * and derives the net invoice from them.
 */
interface GrossPolicy {

    /**
     * Returns the name of the policy, as a report and a refusal print it.
     *
     * @return the name, for instance {@code GROSS_UNIT_AUTHORING}
     */
    String name();

    /**
     * Writes the item net prices of every invoice line into the builder of the run.
     *
     * @param run the run, which carries the builder, the options and the record of what was
     *            written
     * @return the invoice line net amount the policy expects the derivation to arrive at, per
     *         line index, for the lines where the policy can say it; the run checks those
     *         after the derivation
     * @throws PolicyPreconditionException if the invoice does not state what the policy needs
     */
    Map<Integer, BigDecimal> prices(AuthoringRun run);
}
