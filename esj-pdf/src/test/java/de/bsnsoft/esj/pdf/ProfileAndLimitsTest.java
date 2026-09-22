package de.bsnsoft.esj.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The two spellings of a profile, and the bounds a reader runs. */
class ProfileAndLimitsTest {

    @Test
    void readsAProfileFromTheConformanceLevelOfTheXmpPacket() {
        assertEquals(Optional.of(FacturXProfile.MINIMUM),
                FacturXProfile.ofConformanceLevel("MINIMUM"));
        assertEquals(Optional.of(FacturXProfile.BASIC_WL),
                FacturXProfile.ofConformanceLevel("BASIC WL"));
        assertEquals(Optional.of(FacturXProfile.BASIC_WL),
                FacturXProfile.ofConformanceLevel("basic-wl"));
        assertEquals(Optional.of(FacturXProfile.EN_16931),
                FacturXProfile.ofConformanceLevel("  en 16931 "));
        assertEquals(Optional.of(FacturXProfile.EN_16931),
                FacturXProfile.ofConformanceLevel("EN16931"));
        assertEquals(Optional.of(FacturXProfile.XRECHNUNG),
                FacturXProfile.ofConformanceLevel("XRECHNUNG"));
        assertEquals(Optional.empty(), FacturXProfile.ofConformanceLevel("COMFORT"));
        assertEquals(Optional.empty(), FacturXProfile.ofConformanceLevel(null));
    }

    @Test
    void readsAProfileFromTheSpecificationIdentifierOfTheInvoice() {
        assertEquals(Optional.of(FacturXProfile.EN_16931),
                FacturXProfile.ofSpecificationIdentifier("urn:cen.eu:en16931:2017"));
        assertEquals(Optional.of(FacturXProfile.MINIMUM),
                FacturXProfile.ofSpecificationIdentifier("urn:factur-x.eu:1p0:minimum"));
        assertEquals(Optional.of(FacturXProfile.BASIC_WL),
                FacturXProfile.ofSpecificationIdentifier("urn:factur-x.eu:1p0:basicwl"));
        assertEquals(Optional.of(FacturXProfile.BASIC),
                FacturXProfile.ofSpecificationIdentifier(
                        "urn:cen.eu:en16931:2017#compliant#urn:factur-x.eu:1p0:basic"));
        assertEquals(Optional.of(FacturXProfile.EXTENDED),
                FacturXProfile.ofSpecificationIdentifier(
                        "urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended"));
        assertEquals(Optional.of(FacturXProfile.XRECHNUNG),
                FacturXProfile.ofSpecificationIdentifier(
                        "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard"
                                + ":xrechnung_3.0"));
        assertEquals(Optional.empty(),
                FacturXProfile.ofSpecificationIdentifier("urn:example:something-else"));
        assertEquals(Optional.empty(), FacturXProfile.ofSpecificationIdentifier(null));
    }

    @Test
    void knowsWhichProfilesTheRulesOfTheStandardApplyTo() {
        assertFalse(FacturXProfile.MINIMUM.isEn16931Invoice());
        assertFalse(FacturXProfile.BASIC_WL.isEn16931Invoice());
        assertTrue(FacturXProfile.BASIC.isEn16931Invoice());
        assertTrue(FacturXProfile.EN_16931.isEn16931Invoice());
        assertTrue(FacturXProfile.EXTENDED.isEn16931Invoice());
        assertTrue(FacturXProfile.XRECHNUNG.isEn16931Invoice());
    }

    @Test
    void refusesABoundThatDescribesNoFile() {
        assertThrows(IllegalArgumentException.class,
                () -> PdfLimits.defaults().withMaxPdfBytes(0));
        assertThrows(IllegalArgumentException.class,
                () -> PdfLimits.defaults().withMaxEmbeddedFiles(-1));
        assertThrows(IllegalArgumentException.class,
                () -> PdfLimits.defaults().withMaxAttachmentBytes(0));
        assertThrows(IllegalArgumentException.class,
                () -> PdfLimits.defaults().withMaxTotalAttachmentBytes(0));
        assertThrows(IllegalArgumentException.class,
                () -> PdfLimits.defaults().withMaxXmpBytes(0));
    }

    @Test
    void keepsTheDefaultsWhereTheDocumentationSaysTheyAre() {
        PdfLimits limits = PdfLimits.defaults();

        assertEquals(64L * 1024 * 1024, limits.maxPdfBytes());
        assertEquals(64, limits.maxEmbeddedFiles());
        assertEquals(4L * 1024 * 1024, limits.maxAttachmentBytes());
        assertEquals(16L * 1024 * 1024, limits.maxTotalAttachmentBytes());
        assertEquals(1024L * 1024, limits.maxXmpBytes());
    }
}
