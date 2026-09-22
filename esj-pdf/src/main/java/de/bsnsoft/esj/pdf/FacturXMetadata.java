package de.bsnsoft.esj.pdf;

import java.util.Objects;
import java.util.Optional;

/**
 * What the XMP packet of a hybrid PDF says about the invoice attached to it.
 *
 * <p>A Factur-X or ZUGFeRD 2.x file carries an XMP extension schema with four properties:
 * that the document is an invoice, what the attachment is called, which version of the
 * container specification was followed, and which profile the invoice is written in. The
 * point of them is that a consumer can find the invoice without opening it. Here they are
 * read and reported, and nothing is decided by them: the attachment is found by its bytes,
 * and a property that disagrees with the bytes draws a finding.
 *
 * <p>The namespace tells the two members of the family apart. ZUGFeRD 2.0 used its own,
 * and Factur-X — which ZUGFeRD 2.1 and later are — uses
 * {@code urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#}.
 *
 * @param namespace        the namespace of the extension schema the properties were read
 *                         from
 * @param documentType     the property {@code DocumentType}, conventionally
 *                         {@code INVOICE}
 * @param documentFileName the property {@code DocumentFileName}: what the attachment is
 *                         called
 * @param version          the property {@code Version} of the container specification
 * @param conformanceLevel the property {@code ConformanceLevel}: the profile, as written
 */
public record FacturXMetadata(String namespace,
                              Optional<String> documentType,
                              Optional<String> documentFileName,
                              Optional<String> version,
                              Optional<String> conformanceLevel) {

    /** The namespace of the Factur-X extension schema, which ZUGFeRD 2.1 and later use. */
    public static final String FACTUR_X_NAMESPACE =
            "urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#";

    /** The namespace ZUGFeRD 2.0 used before the two were unified. */
    public static final String ZUGFERD_2_NAMESPACE =
            "urn:zugferd:pdfa:CrossIndustryDocument:invoice:2p0#";

    /**
     * Checks that no member is {@code null}.
     *
     * @param namespace        the namespace of the extension schema the properties were read
     *                         from
     * @param documentType     the property {@code DocumentType}, conventionally
     *                         {@code INVOICE}
     * @param documentFileName the property {@code DocumentFileName}: what the attachment is
     *                         called
     * @param version          the property {@code Version} of the container specification
     * @param conformanceLevel the property {@code ConformanceLevel}: the profile, as written
     * @throws NullPointerException if a member is {@code null}
     */
    public FacturXMetadata {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(documentFileName, "documentFileName");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(conformanceLevel, "conformanceLevel");
    }

    /**
     * Returns the profile the {@code ConformanceLevel} property names.
     *
     * @return the profile, or an empty optional where the property is absent or names
     *         none this module knows
     */
    public Optional<FacturXProfile> profile() {
        return conformanceLevel.flatMap(FacturXProfile::ofConformanceLevel);
    }
}
