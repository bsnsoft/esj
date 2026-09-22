package de.bsnsoft.esj.render;

/**
 * A colour the PDF rendering draws with, in the sRGB space the output intent of the file
 * names.
 *
 * <p>A grey — the three components equal — is written with the one-component operator of
 * PDF rather than with the three-component one. That is not an optimization: the generic
 * layout draws in greys only, and writing them the way it always has is what keeps its
 * files byte for byte what they were before a template could bring colours at all.
 *
 * @param red   the red component, 0 to 1
 * @param green the green component, 0 to 1
 * @param blue  the blue component, 0 to 1
 */
record Ink(float red, float green, float blue) {

    /** Black, which the text of a rendering is written in. */
    static final Ink BLACK = grey(0f);

    /**
     * Returns a grey.
     *
     * @param level 0 for black, 1 for white
     * @return the colour
     */
    static Ink grey(float level) {
        return new Ink(level, level, level);
    }

    /**
     * Returns the colour a template wrote as {@code #rrggbb}.
     *
     * @param text  the six hexadecimal digits behind a number sign
     * @param where what the colour is for, for the message of a refusal
     * @return the colour
     * @throws TemplateException if the text is not six hexadecimal digits behind a number
     *                           sign
     */
    static Ink parse(String text, String where) {
        if (text == null || text.length() != 7 || text.charAt(0) != '#') {
            throw new TemplateException(where + " is a colour written as #rrggbb, not '"
                    + text + "'");
        }
        int value = 0;
        for (int i = 1; i < text.length(); i++) {
            int digit = Character.digit(text.charAt(i), 16);
            if (digit < 0) {
                throw new TemplateException(where + " is a colour written as #rrggbb, not '"
                        + text + "'");
            }
            value = value * 16 + digit;
        }
        return new Ink((value >> 16 & 0xff) / 255f, (value >> 8 & 0xff) / 255f,
                (value & 0xff) / 255f);
    }

    /** Tells whether the three components are the same, which PDF writes in one number. */
    boolean isGrey() {
        return red == green && green == blue;
    }
}
