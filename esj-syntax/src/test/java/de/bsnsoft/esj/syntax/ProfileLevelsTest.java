package de.bsnsoft.esj.syntax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.xr.XrSyntax;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * What the profile a document names makes of a rule, asked outside the syntax engine.
 */
class ProfileLevelsTest {

    /** The customization identifier of the XRechnung 3.0 core invoice usage specification. */
    private static final String XRECHNUNG =
            "urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0";

    /** The customization identifier of the XRechnung CVD profile. */
    private static final String CVD = XRECHNUNG
            + "#compliant#urn:xeinkauf.de:kosit:xrechnung:cvd_0.9";

    /** The customization identifier of the XRechnung extension. */
    private static final String EXTENSION = XRECHNUNG
            + "#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0";

    private final Pack pack = Packs.bundled("xrechnung/3.0.2/2026-08-31");

    @Test
    void answersWithTheTableOfTheSyntaxAndTheProfile() {
        ProfileLevels levels = ProfileLevels.of(pack, XrSyntax.UBL_INVOICE, CVD);

        assertEquals(Optional.of(Severity.INFORMATION), levels.level("BR-CL-13"));
        assertEquals(Optional.empty(), levels.level("BR-CO-10"),
                "a rule the profile says nothing about keeps the level it was raised at");
        assertEquals(CVD, levels.profile());
        assertTrue(levels.any());
    }

    @Test
    void levelsNothingForAProfileNoTableNames() {
        assertFalse(ProfileLevels.of(pack, XrSyntax.CII, "urn:cen.eu:en16931:2017").any());
        assertFalse(ProfileLevels.of(pack, "urn:cen.eu:en16931:2017").any());
        assertFalse(ProfileLevels.none().any());
        assertEquals("", ProfileLevels.none().profile());
    }

    /**
     * Without a syntax only what every table of the profile agrees on is taken. The
     * extension profile levels {@code BR-CL-10} down in both syntaxes and {@code BR-CO-16}
     * in the UBL scenario alone, so a document of no syntax is levelled by the first and
     * not by the second.
     */
    @Test
    void takesOnlyWhatEveryTableOfTheProfileAgreesOnWhereTheSyntaxIsUnknown() {
        ProfileLevels unknown = ProfileLevels.of(pack, EXTENSION);

        assertEquals(Optional.of(Severity.INFORMATION), unknown.level("BR-CL-10"));
        assertEquals(Optional.empty(), unknown.level("BR-CO-16"));
        assertEquals(Optional.of(Severity.INFORMATION),
                ProfileLevels.of(pack, XrSyntax.UBL_INVOICE, EXTENSION).level("BR-CO-16"),
                "the UBL scenario of the same profile does level it");
    }

    @Test
    void answersOutOfTheBundledPacksAsWell() {
        assertEquals(Optional.of(Severity.INFORMATION),
                Packs.levels(XrSyntax.CII, CVD).level("BR-CL-13"));
        assertEquals(Optional.of(Severity.INFORMATION),
                Packs.levels(CVD).level("BR-CL-13"));
        assertFalse(Packs.levels("urn:example:profile:nobody:knows").any());
    }
}
