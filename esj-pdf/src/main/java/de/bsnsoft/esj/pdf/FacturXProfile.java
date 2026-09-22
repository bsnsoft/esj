package de.bsnsoft.esj.pdf;

import java.util.Locale;
import java.util.Optional;

/**
 * A profile of the Factur-X and ZUGFeRD 2.x family.
 *
 * <p>The family is a set of profiles of one container format, and only some of them are
 * EN 16931 invoices. The two smallest are not: MINIMUM carries little more than the
 * totals, and BASIC WL carries no invoice line at all, which is the one thing every
 * EN 16931 invoice has. A tool that ran the rules of the standard against either would
 * report a list of missing mandatory elements that says nothing about the document —
 * the document was never claiming to be one. So the profile is read, reported, and the
 * caller is told that the question does not apply rather than answered wrongly.
 *
 * <p>The profile is written twice in a hybrid file: in the XMP packet as the Factur-X
 * property {@code ConformanceLevel}, and in the invoice itself as BT-24, the
 * specification identifier. This enumeration reads both spellings, and a container whose
 * two disagree draws a finding.
 *
 * <p>The names and the identifiers are facts of the public Factur-X and ZUGFeRD
 * specifications; {@code docs/pdf-input.md} records where they were taken from.
 */
public enum FacturXProfile {

    /** Little more than the totals and the parties. Not an EN 16931 invoice. */
    MINIMUM("MINIMUM", false),

    /** Everything but the invoice lines, which is what "without lines" means. Not an EN 16931 invoice. */
    BASIC_WL("BASIC WL", false),

    /** A subset of EN 16931 with invoice lines. */
    BASIC("BASIC", true),

    /** The core of EN 16931 itself. */
    EN_16931("EN 16931", true),

    /** EN 16931 with the extensions of the family on top. */
    EXTENDED("EXTENDED", true),

    /** The German national specification, carried as a profile of the same container. */
    XRECHNUNG("XRECHNUNG", true);

    private final String conformanceLevel;
    private final boolean en16931;

    FacturXProfile(String conformanceLevel, boolean en16931) {
        this.conformanceLevel = conformanceLevel;
        this.en16931 = en16931;
    }

    /**
     * Returns the spelling this profile has in the XMP property
     * {@code ConformanceLevel}.
     *
     * @return the conformance level, such as {@code BASIC WL}
     */
    public String conformanceLevel() {
        return conformanceLevel;
    }

    /**
     * Tells whether a document of this profile is an EN 16931 invoice, so that the rules
     * of the standard are the right question to ask of it.
     *
     * @return {@code false} for MINIMUM and BASIC WL, {@code true} for the rest
     */
    public boolean isEn16931Invoice() {
        return en16931;
    }

    /**
     * Recognizes a profile by the spelling of an XMP {@code ConformanceLevel}.
     *
     * <p>Case and inner whitespace are ignored, and the two spellings of BASIC WL that
     * occur in the field — with a space and with a hyphen — are both accepted, because
     * the point of reading the property is to find out what the producer meant.
     *
     * @param conformanceLevel the property as written, possibly {@code null}
     * @return the profile, or an empty optional where it names none of them
     */
    public static Optional<FacturXProfile> ofConformanceLevel(String conformanceLevel) {
        if (conformanceLevel == null) {
            return Optional.empty();
        }
        String normalized = conformanceLevel.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[\\s_-]+", " ");
        for (FacturXProfile profile : values()) {
            if (profile.conformanceLevel.equals(normalized)
                    || profile.conformanceLevel.replace(" ", "").equals(normalized)) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    /**
     * Recognizes a profile by the specification identifier of the invoice, BT-24.
     *
     * <p>BT-24 either is the identifier of the core of EN 16931 itself, or is a compound
     * of it and the identifier of the profile that restricts or extends it, written after
     * {@code #compliant#} or {@code #conformant#}. The last part is the one that names
     * the profile.
     *
     * @param specificationIdentifier the content of BT-24, possibly {@code null}
     * @return the profile, or an empty optional where the identifier names none of them
     */
    public static Optional<FacturXProfile> ofSpecificationIdentifier(
            String specificationIdentifier) {
        if (specificationIdentifier == null) {
            return Optional.empty();
        }
        String identifier = specificationIdentifier.trim().toLowerCase(Locale.ROOT);
        String tail = identifier.substring(identifier.lastIndexOf('#') + 1);
        if (tail.contains("xrechnung")) {
            return Optional.of(XRECHNUNG);
        }
        if (tail.endsWith(":minimum")) {
            return Optional.of(MINIMUM);
        }
        if (tail.endsWith(":basicwl") || tail.endsWith(":basic-wl")) {
            return Optional.of(BASIC_WL);
        }
        if (tail.endsWith(":basic")) {
            return Optional.of(BASIC);
        }
        if (tail.endsWith(":extended")) {
            return Optional.of(EXTENDED);
        }
        return "urn:cen.eu:en16931:2017".equals(identifier)
                ? Optional.of(EN_16931)
                : Optional.empty();
    }
}
