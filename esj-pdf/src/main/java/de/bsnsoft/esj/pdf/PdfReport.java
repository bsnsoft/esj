package de.bsnsoft.esj.pdf;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the container had to say, beside the invoice that was read out of it.
 *
 * <p>It is a report about the PDF and not about the invoice. The findings of
 * {@code esj-core} describe the semantic document; the notes of {@code esj-xr} describe
 * the distance between the XML and that document; these describe the distance between the
 * container and the XML. A caller shows the three as three things, because a caller who is
 * handed one list cannot tell which of the three answers a line belongs to.
 *
 * @param attachments every attachment of the container with what its bytes turned out to
 *                    be, so that a report can say what a file holds and not only what was
 *                    read out of it
 * @param findings    the structural findings about the container
 * @param facturX     the Factur-X properties of the XMP packet, where it carries them
 * @param pdfa        the PDF/A conformance the file declares, which was not validated
 * @param profile     the profile the invoice is in, taken from BT-24 where the invoice was
 *                    read and from the XMP packet otherwise
 */
public record PdfReport(List<LocatedAttachment> attachments,
                        List<ContainerFinding> findings,
                        Optional<FacturXMetadata> facturX,
                        Optional<PdfaIdentification> pdfa,
                        Optional<FacturXProfile> profile) {

    /**
     * Copies the lists.
     *
     * @param attachments every attachment of the container with what its bytes turned out to
     *                    be, so that a report can say what a file holds and not only what was
     *                    read out of it
     * @param findings    the structural findings about the container
     * @param facturX     the Factur-X properties of the XMP packet, where it carries them
     * @param pdfa        the PDF/A conformance the file declares, which was not validated
     * @param profile     the profile the invoice is in, taken from BT-24 where the invoice was
     *                    read and from the XMP packet otherwise
     * @throws NullPointerException if a member or an element of a list is {@code null}
     */
    public PdfReport {
        attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
        Objects.requireNonNull(facturX, "facturX");
        Objects.requireNonNull(pdfa, "pdfa");
        Objects.requireNonNull(profile, "profile");
    }

    /**
     * Tells whether the container is wrong about the document it carries.
     *
     * <p>This is the container's own verdict and it is separate from the invoice's: a
     * caller that turns both into one exit status fails the run if either fails, and says
     * in its report which of the two it was.
     *
     * @return {@code true} if a finding has the severity
     *         {@link ContainerFinding.Severity#ERROR}
     */
    public boolean hasErrors() {
        return findings.stream()
                .anyMatch(finding -> finding.severity() == ContainerFinding.Severity.ERROR);
    }

    /**
     * Returns the findings of one category.
     *
     * @param category what part of the container the findings are about
     * @return the findings of that category, in the order they were made
     * @throws NullPointerException if {@code category} is {@code null}
     */
    public List<ContainerFinding> findings(ContainerFinding.Category category) {
        Objects.requireNonNull(category, "category");
        return findings.stream().filter(finding -> finding.category() == category).toList();
    }

    /**
     * Returns the finding of one code, where the report carries it.
     *
     * @param code the code
     * @return the first finding with that code, or an empty optional
     * @throws NullPointerException if {@code code} is {@code null}
     */
    public Optional<ContainerFinding> finding(String code) {
        Objects.requireNonNull(code, "code");
        return findings.stream().filter(finding -> finding.code().equals(code)).findFirst();
    }
}
