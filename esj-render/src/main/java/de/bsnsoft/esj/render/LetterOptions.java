package de.bsnsoft.esj.render;

import java.util.Objects;

/**
 * The few decisions the letter layout leaves to the party that renders.
 *
 * <p>A business letter is printed matter before it is a file: it goes into a window
 * envelope, it is folded, it is filed in a binder, and the sender's own letterhead may
 * already carry the details that otherwise stand along its foot. None of that is a
 * property of the invoice, so none of it is a property of the document — it belongs to the
 * template, beside the letterhead and the logo it goes with.
 *
 * <p>The defaults are a letter that is printed on plain paper and posted: the address field
 * of DIN 5008 form B, no marks on the paper, the number, the dates and the references in a
 * reference line under the address zone, and the sender's details along the foot of the
 * letter.
 *
 * @param window        which address field the recipient is written into
 * @param foldMarks     whether the two fold marks are printed in the left margin
 * @param holeMark      whether the punch mark is printed in the left margin
 * @param sellerDetails where the seller's own business details go
 * @param information   where the number, the dates and the references stand
 * @param printedHead   how far below the top edge the printed head of the letterhead
 *                      reaches, in points, which the information block starts below
 * @param paymentCode   whether the payment block carries the EPC QR code of a credit
 *                      transfer the document states
 */
record LetterOptions(Window window, boolean foldMarks, boolean holeMark,
                     SellerDetails sellerDetails, Information information,
                     float printedHead, boolean paymentCode) {

    /**
     * The deepest printed head a template may declare, in points.
     *
     * <p>Beyond it the block would have nothing left to stand in: a head of 400 points is
     * half of an A4 sheet, and a letterhead that prints half a page is a letterhead whose
     * information belongs in the reference line rather than in a block.
     */
    static final float MAX_PRINTED_HEAD = 400f;

    /** The address field the recipient is written into. */
    enum Window {

        /**
         * The address field of DIN 5008 form B: 85 mm by 45 mm, 20 mm from the left edge
         * and 45 mm from the top, which is the one a letter with a tall letterhead uses.
         */
        DIN_5008_B("din5008-b", 45f),

        /** The address field of form A, which begins 27 mm from the top. */
        DIN_5008_A("din5008-a", 27f),

        /**
         * No address field. The recipient is written at the head of the letter in the
         * ordinary flow, which is what a letter that is not posted needs — and what a
         * letterhead that prints its own address field asks for.
         */
        NONE("none", 0f);

        private final String token;
        private final float top;

        Window(String token, float top) {
            this.token = token;
            this.top = top;
        }

        /** Returns how far below the top edge of the paper the field begins, in mm. */
        float topInMillimetres() {
            return top;
        }

        /**
         * Returns the form a template named.
         *
         * @param written the word in the template
         * @return the form
         * @throws TemplateException if it is none of the three
         */
        static Window of(String written) {
            for (Window window : values()) {
                if (window.token.equals(written)) {
                    return window;
                }
            }
            StringBuilder known = new StringBuilder();
            for (Window window : values()) {
                known.append(known.length() == 0 ? "" : ", ").append(window.token);
            }
            throw new TemplateException("letter: addressWindow is one of " + known
                    + ", not '" + written + "'");
        }
    }

    /** Where the number, the dates and the references of the letter stand. */
    enum Information {

        /**
         * In a reference line: in the flow under the address zone and over the title,
         * across the text width, small labels above their values in equal columns. It is
         * the default, because it needs nothing of the paper but the width the margins of
         * the template give it — a letterhead that prints a contact block at the top
         * right, or a decorated edge, leaves it untouched.
         */
        LINE("line"),

        /**
         * In a block beside the address field, at the place DIN 5008 gives it. It is for
         * paper whose top right is free, and a template that uses it says with
         * {@code printedHead} how far down its own printed head reaches.
         */
        BLOCK("block");

        private final String token;

        Information(String token) {
            this.token = token;
        }

        /**
         * Returns the place a template named.
         *
         * @param written the word in the template
         * @return the place
         * @throws TemplateException if it is neither
         */
        static Information of(String written) {
            for (Information where : values()) {
                if (where.token.equals(written)) {
                    return where;
                }
            }
            throw new TemplateException("letter: information is line or block, not '"
                    + written + "'");
        }
    }

    /** Where the seller's own business details go. */
    enum SellerDetails {

        /** Along the foot of the letter, in columns, which is where a letter carries them. */
        FOOTER("footer"),

        /**
         * Nowhere of their own: the letterhead already prints them, so they stand under
         * the closing heading with everything else the letter has no place for. Nothing is
         * dropped either way.
         */
        DETAILS("details");

        private final String token;

        SellerDetails(String token) {
            this.token = token;
        }

        /**
         * Returns the choice a template named.
         *
         * @param written the word in the template
         * @return the choice
         * @throws TemplateException if it is neither
         */
        static SellerDetails of(String written) {
            for (SellerDetails where : values()) {
                if (where.token.equals(written)) {
                    return where;
                }
            }
            throw new TemplateException("letter: sellerDetails is footer or details, not '"
                    + written + "'");
        }
    }

    /**
     * Checks the members.
     *
     * @throws NullPointerException if one is {@code null}
     * @throws TemplateException    if the printed head is negative or deeper than
     *                              {@link #MAX_PRINTED_HEAD}
     */
    LetterOptions {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(sellerDetails, "sellerDetails");
        Objects.requireNonNull(information, "information");
        if (printedHead < 0 || printedHead > MAX_PRINTED_HEAD) {
            throw new TemplateException("letter: printedHead is how far below the top edge"
                    + " of the paper the printed head of the letterhead reaches, in points,"
                    + " between 0 and " + Math.round(MAX_PRINTED_HEAD) + ", not "
                    + printedHead);
        }
    }

    /**
     * Returns the letter a template said nothing about: form B, no marks, the reference
     * line, the seller's details along the foot, and the code of a credit transfer in the
     * payment block.
     *
     * @return the defaults
     */
    static LetterOptions defaults() {
        return new LetterOptions(Window.DIN_5008_B, false, false, SellerDetails.FOOTER,
                Information.LINE, 0f, true);
    }

    /**
     * Returns these options with the payment code turned on or off, which is how the
     * decision of a caller wins over the one of a template.
     *
     * @param value whether the payment block carries the code
     * @return the options
     */
    LetterOptions withPaymentCode(boolean value) {
        return new LetterOptions(window, foldMarks, holeMark, sellerDetails, information,
                printedHead, value);
    }
}
