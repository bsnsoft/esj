package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.model.Registry;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What {@link FacturX#embed} writes and what it is willing to spend.
 *
 * <p>Two of the four are written into the file and a consumer reads them there: the
 * profile becomes the XMP property {@code ConformanceLevel}, and the flavour decides the
 * name of the embedded file, the {@code DocumentFileName} property, the namespace of the
 * XMP extension schema and its {@code Version} — the three that {@link HybridFlavour}
 * keeps together because no specification lets a producer mix them. The other two are
 * this run's own business: the bounds the input is opened under, and a PDF/A validator
 * the caller may lend for the run.
 *
 * <p>The profile is one of the four that are EN 16931 invoices. MINIMUM and BASIC WL are
 * not — the first carries little more than the totals and the second carries no invoice
 * line — so a document of this project is never one of them and this module does not
 * write the claim.
 *
 * <p>The fifth is the one thing written beside the invoice: the same document as an ESJ
 * attachment ({@link EsjAttachment}). It is on by default and is written only where the
 * attachment and the invoice XML are two accounts of one invoice; see {@link FacturX}.
 *
 * <p>The sixth is a fact only the caller knows: the extension registries the terms of the
 * document come from. The writer of the attachment is handed them, so that a value of a
 * term whose registry declares {@code "transport": "none"} is reported as left behind by
 * design rather than as a loss, as {@code WriterOptions.extensions()} says.
 *
 * @param profile    the profile the invoice is written in, which the specification
 *                   identifier of the document has to name as well
 * @param flavour    the container specification the file declares itself under
 * @param limits     what opening the input PDF may cost
 * @param check      a PDF/A validator for the input, or an empty optional to go by what
 *                   the input declares about itself
 * @param esj        whether the ESJ document of the invoice is attached beside the XML
 * @param extensions the extension registries the terms of the document come from, none by
 *                   default
 */
public record EmbedOptions(FacturXProfile profile,
                           HybridFlavour flavour,
                           PdfLimits limits,
                           Optional<PdfaCheck> check,
                           boolean esj,
                           List<Registry> extensions) {

    private static final EmbedOptions DEFAULTS = new EmbedOptions(FacturXProfile.EN_16931,
            HybridFlavour.FACTUR_X_1_0, PdfLimits.defaults(), Optional.empty(), true,
            List.of());

    /**
     * Checks the profile and copies the registries.
     *
     * @param profile    the profile the invoice is written in, which the specification
     *                   identifier of the document has to name as well
     * @param flavour    the container specification the file declares itself under
     * @param limits     what opening the input PDF may cost
     * @param check      a PDF/A validator for the input, or an empty optional to go by what
     *                   the input declares about itself
     * @param esj        whether the ESJ document of the invoice is attached beside the XML
     * @param extensions the extension registries the terms of the document come from
     * @throws IllegalArgumentException if the profile is not an EN 16931 invoice
     * @throws NullPointerException     if a member or a registry is {@code null}
     */
    public EmbedOptions {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(flavour, "flavour");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(check, "check");
        extensions = List.copyOf(Objects.requireNonNull(extensions, "extensions"));
        if (!profile.isEn16931Invoice()) {
            throw new IllegalArgumentException("the profile "
                    + Messages.quoted(profile.conformanceLevel()) + " is not an EN 16931"
                    + " invoice, and this module embeds no other kind");
        }
    }

    /**
     * Creates options that hand the writer no extension registry.
     *
     * @param profile the profile the invoice is written in, which the specification
     *                identifier of the document has to name as well
     * @param flavour the container specification the file declares itself under
     * @param limits  what opening the input PDF may cost
     * @param check   a PDF/A validator for the input, or an empty optional to go by what the
     *                input declares about itself
     * @param esj     whether the ESJ document of the invoice is attached beside the XML
     * @throws IllegalArgumentException if the profile is not an EN 16931 invoice
     * @throws NullPointerException     if a member is {@code null}
     */
    public EmbedOptions(FacturXProfile profile,
                        HybridFlavour flavour,
                        PdfLimits limits,
                        Optional<PdfaCheck> check,
                        boolean esj) {
        this(profile, flavour, limits, check, esj, List.of());
    }

    /**
     * Returns the defaults: the profile EN 16931, the flavour Factur-X 1.0, the default
     * limits, the declaration of the input taken as it is written, the ESJ document
     * attached beside the invoice, and no extension registry.
     *
     * @return the defaults
     */
    public static EmbedOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns the defaults with a profile of the caller's choosing.
     *
     * @param profile the profile
     * @return the options
     * @throws IllegalArgumentException if the profile is not an EN 16931 invoice
     * @throws NullPointerException     if {@code profile} is {@code null}
     */
    public static EmbedOptions of(FacturXProfile profile) {
        return DEFAULTS.withProfile(profile);
    }

    /**
     * Returns what the embedded invoice is called, which is the flavour's own name for
     * it.
     *
     * @return the attachment name
     */
    public String attachmentName() {
        return flavour.attachmentName();
    }

    /**
     * Returns these options with another profile.
     *
     * @param value the profile
     * @return the options
     * @throws IllegalArgumentException if the profile is not an EN 16931 invoice
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    public EmbedOptions withProfile(FacturXProfile value) {
        return new EmbedOptions(value, flavour, limits, check, esj, extensions);
    }

    /**
     * Returns these options with another container flavour, which changes the attachment
     * name and the XMP schema together.
     *
     * @param value the flavour
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public EmbedOptions withFlavour(HybridFlavour value) {
        return new EmbedOptions(profile, value, limits, check, esj, extensions);
    }

    /**
     * Returns these options with other limits on the input.
     *
     * @param value the limits
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public EmbedOptions withLimits(PdfLimits value) {
        return new EmbedOptions(profile, flavour, value, check, esj, extensions);
    }

    /**
     * Returns these options with the ESJ attachment on or off.
     *
     * <p>Off is for a caller whose consumer is disturbed by a second embedded file, and
     * it is the only way to get the file without it: where the switch is on and the rule
     * of {@link EsjAgreement} does not hold, nothing is attached either, and the result
     * says which of the two it was.
     *
     * @param value whether the ESJ document is attached beside the invoice
     * @return the options
     */
    public EmbedOptions withEsj(boolean value) {
        return new EmbedOptions(profile, flavour, limits, check, value, extensions);
    }

    /**
     * Returns these options with a PDF/A validator for the input, so that the run rests
     * on a validation rather than on what the input declares about itself.
     *
     * @param value the validator
     * @return the options
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public EmbedOptions checkedWith(PdfaCheck value) {
        return new EmbedOptions(profile, flavour, limits,
                Optional.of(Objects.requireNonNull(value, "value")), esj, extensions);
    }

    /**
     * Returns these options with the extension registries the terms of the document come
     * from, replacing any given before.
     *
     * <p>Hand over the extension registries themselves, as {@code Registry.b2cExtension()}
     * returns one, and not a registry combined with them: the declaration that terms do not
     * travel belongs to the file that defines them.
     *
     * @param value the registries
     * @return the options
     * @throws NullPointerException if {@code value} is or holds {@code null}
     */
    public EmbedOptions withExtensions(Collection<Registry> value) {
        return new EmbedOptions(profile, flavour, limits, check, esj,
                List.copyOf(Objects.requireNonNull(value, "value")));
    }
}
