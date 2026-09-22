package de.bsnsoft.esj.render;

/**
 * The language a rendering is labelled in.
 *
 * <p>The labels, the date picture and the decimal picture of a rendering come from the
 * localization files of the KoSIT XRechnung visualization, which carries two of them. The
 * language therefore decides more than the words: {@code 1.234,56} and {@code 31.12.2026}
 * in German, {@code 1,234.56} and {@code 2026-12-31} in English. It says nothing about the
 * content of the invoice, which is rendered in the language it was written in.
 */
public enum RenderLanguage {

    /** German, the language the localization of the visualization is authored in. */
    GERMAN("de"),

    /** English. */
    ENGLISH("en");

    private final String code;

    RenderLanguage(String code) {
        this.code = code;
    }

    /**
     * Returns the two-letter code the stylesheet knows this language by, which is also the
     * name of its localization file.
     *
     * @return {@code de} or {@code en}
     */
    public String code() {
        return code;
    }
}
