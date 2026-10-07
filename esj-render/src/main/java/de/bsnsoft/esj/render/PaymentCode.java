package de.bsnsoft.esj.render;

/**
 * Whether the letter layout draws the EPC QR code of a credit transfer the document states.
 *
 * <p>The generic layout draws no code whatever this says.
 */
public enum PaymentCode {

    /**
     * The template decides, through its {@code letter.paymentCode} member; where there is
     * no template or it says nothing, the code is drawn.
     */
    TEMPLATE,

    /** The code is drawn, whatever the template says. */
    DRAW,

    /** The code is left out, whatever the template says. */
    OMIT
}
