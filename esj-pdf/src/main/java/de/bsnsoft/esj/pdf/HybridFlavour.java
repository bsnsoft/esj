package de.bsnsoft.esj.pdf;

import java.util.Locale;
import java.util.Optional;

/**
 * Which published container specification a hybrid file declares itself under.
 *
 * <p>Four things travel together and no specification lets a producer mix them: what the
 * attachment is called, the namespace of the XMP extension schema that describes the four
 * properties, the prefix that namespace is bound to, and the {@code Version} written in
 * that schema. Factur-X 1.0 — and ZUGFeRD 2.1 and later, which is the same format under
 * the other name — calls the attachment {@code factur-x.xml} and declares the
 * {@code …:invoice:1p0#} namespace under {@code fx} with {@code 1.0}; ZUGFeRD 2.0 calls it
 * {@code zugferd-invoice.xml} and declares {@code …:invoice:2p0#} under {@code zf} with
 * {@code 2p0}. A file that took the name of one and the schema of the other would be a
 * file neither specification defines, which is why this is one choice and not four.
 *
 * <p>The profile is the other axis and is free of this one: every profile of the family
 * is written as the {@code ConformanceLevel} of whichever flavour the file declares.
 *
 * <p>{@code xrechnung.xml} is not here. It is a conventional name for an XRechnung inside
 * a PDF and no container specification defines an XMP declaration to go with it, so this
 * module has no schema it could honestly write beside it; an XRechnung is embedded as the
 * profile {@link FacturXProfile#XRECHNUNG} of one of the two flavours above. The names,
 * the namespaces, the prefixes and the versions are facts of the public specifications,
 * recorded with their sources in {@code docs/pdf-output.md}.
 */
public enum HybridFlavour {

    /** Factur-X 1.0, which ZUGFeRD 2.1 and later are the same format of. */
    FACTUR_X_1_0("factur-x.xml", FacturXMetadata.FACTUR_X_NAMESPACE, "fx", "1.0",
            "Factur-X PDFA Extension Schema"),

    /** ZUGFeRD 2.0, before the two were unified. */
    ZUGFERD_2_0("zugferd-invoice.xml", FacturXMetadata.ZUGFERD_2_NAMESPACE, "zf", "2p0",
            "ZUGFeRD PDFA Extension Schema");

    private final String attachmentName;
    private final String namespace;
    private final String prefix;
    private final String version;
    private final String schemaDescription;

    HybridFlavour(String attachmentName, String namespace, String prefix, String version,
                  String schemaDescription) {
        this.attachmentName = attachmentName;
        this.namespace = namespace;
        this.prefix = prefix;
        this.version = version;
        this.schemaDescription = schemaDescription;
    }

    /**
     * Returns what this flavour calls the embedded invoice.
     *
     * @return the attachment name, which is also the {@code DocumentFileName} property
     */
    public String attachmentName() {
        return attachmentName;
    }

    /**
     * Returns the namespace of the XMP extension schema this flavour declares.
     *
     * @return the namespace URI
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Returns the prefix this flavour's specification binds its namespace to. It is
     * written as the declared {@code pdfaSchema:prefix} and as the element prefix of the
     * four properties, which is the spelling a consumer that reads them by name looks for.
     *
     * @return the prefix, without a colon
     */
    public String prefix() {
        return prefix;
    }

    /**
     * Returns what this flavour writes as the {@code Version} property.
     *
     * @return the version, as the specification spells it
     */
    public String version() {
        return version;
    }

    /**
     * Returns what the {@code pdfaSchema:schema} element of the declaration says the
     * schema is, which is a description for a person and not an identifier.
     *
     * @return the description
     */
    public String schemaDescription() {
        return schemaDescription;
    }

    /**
     * Recognizes a flavour by the name its attachment carries.
     *
     * @param attachmentName the name, possibly {@code null}
     * @return the flavour, or an empty optional where no flavour writes that name
     */
    public static Optional<HybridFlavour> ofAttachmentName(String attachmentName) {
        if (attachmentName == null) {
            return Optional.empty();
        }
        String normalized = attachmentName.trim().toLowerCase(Locale.ROOT);
        for (HybridFlavour flavour : values()) {
            if (flavour.attachmentName.equals(normalized)) {
                return Optional.of(flavour);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the attachment names this module writes, in the order they are offered.
     *
     * @return the names, separated by a comma and a space
     */
    public static String attachmentNames() {
        StringBuilder names = new StringBuilder();
        for (HybridFlavour flavour : values()) {
            names.append(names.length() == 0 ? "" : ", ").append(flavour.attachmentName);
        }
        return names.toString();
    }
}
