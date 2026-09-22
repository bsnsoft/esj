package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.color.ICC_Profile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Checks that the vendored ICC profile is the file the README beside it describes, and that
 * it is the kind of profile a PDF/A output intent may point at.
 *
 * <p>The profile travels into every rendering this module writes, which makes it the one
 * third-party file here that a reader of an invoice ends up with a copy of. So it is
 * identified by its digest, its terms are recorded beside it and in the NOTICE of the
 * repository, and its header is read rather than assumed. That the renderer embeds this file
 * and not a rewritten one is {@link PdfaTest}.
 */
class VendoredProfileTest {

    private static final String DIRECTORY = "/de/bsnsoft/esj/render/icc/";

    private static final String FILE = "sRGB2014.icc";

    private static final String DIGEST =
            "384b832de3412066743b52a75ee906b6fb9fb8d9e09e936fc2c43223815c6e0a";

    @Test
    void shipsTheFileTheReadmeDescribes() {
        byte[] profile = Corpus.bytes(DIRECTORY + FILE);

        assertEquals(DIGEST, Corpus.sha256(profile), "the shipped profile is the recorded one");
        assertEquals(3024, profile.length, "of the recorded size");
    }

    @Test
    void recordsTheOriginAndTheTermsInTheReadme() {
        String readme = Corpus.text(DIRECTORY + "README.md");

        assertTrue(readme.contains(FILE), "the README names the file");
        assertTrue(readme.contains(DIGEST), "and records its digest");
        assertTrue(readme.contains("International Color Consortium"), "and its publisher");
        assertTrue(readme.contains("may be copied,"), "and quotes its terms");
    }

    @Test
    void theNoticeOfTheRepositoryNamesTheProfile() {
        String notice = Corpus.notice();

        assertTrue(notice.contains(FILE), "the NOTICE names the profile");
        assertTrue(notice.contains("International Color Consortium"), "and its publisher");
    }

    /**
     * What the header says it is: a display profile of three RGB components against the
     * XYZ connection space. An output intent whose destination profile were a printer
     * profile of four components would describe a different page than the one drawn.
     */
    @Test
    void isADisplayProfileOfThreeComponents() {
        byte[] bytes = Corpus.bytes(DIRECTORY + FILE);
        ICC_Profile profile = ICC_Profile.getInstance(bytes);

        assertEquals(ICC_Profile.CLASS_DISPLAY, profile.getProfileClass(), "a display profile");
        assertEquals(java.awt.color.ColorSpace.TYPE_RGB, profile.getColorSpaceType(), "of RGB");
        assertEquals(java.awt.color.ColorSpace.TYPE_XYZ, profile.getPCSType(),
                "against the XYZ connection space");
        assertEquals(3, profile.getNumComponents(), "with three components");
        assertEquals("acsp", new String(Arrays.copyOfRange(bytes, 36, 40),
                        StandardCharsets.US_ASCII),
                "and the signature every ICC profile carries");
    }
}
