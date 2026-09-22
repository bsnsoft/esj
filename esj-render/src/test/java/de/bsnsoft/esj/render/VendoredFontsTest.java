package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;

/**
 * Checks that the vendored fonts are the ones the README beside them describes, and that
 * what the renderer embeds is a subset of them rather than a name it hopes the reader has.
 */
class VendoredFontsTest {

    private static final String DIRECTORY = "/de/bsnsoft/esj/render/fonts/";

    private static final Map<String, String> DIGESTS = digests();

    private static Map<String, String> digests() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("LiberationSans-Bold.ttf",
                "788abee4c806d660e8aee46689dd8540cd4bb98da03dcc9d171ce3efd99a9173");
        expected.put("LiberationSans-Regular.ttf",
                "76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8");
        expected.put("LICENSE",
                "93fed46019c38bbe566b479d22148e2e8a1e85ada614accb0211c37b2c61c19b");
        return Map.copyOf(expected);
    }

    @Test
    void shipsTheFilesTheReadmeDescribes() {
        DIGESTS.forEach((name, digest) ->
                assertEquals(digest, Corpus.sha256(Corpus.bytes(DIRECTORY + name)),
                        "the shipped " + name + " is the one the README records"));
    }

    @Test
    void recordsEveryDigestInTheReadme() {
        String readme = Corpus.text(DIRECTORY + "README.md");

        DIGESTS.forEach((name, digest) -> {
            assertTrue(readme.contains(name), "the README names " + name);
            assertTrue(readme.contains(digest), "the README records the digest of " + name);
        });
        assertTrue(readme.contains("2.1.5"), "the README records the release");
        assertTrue(readme.contains("SIL Open Font License"), "and the licence");
    }

    @Test
    void theLicenceIsTheOpenFontLicence() {
        String licence = Corpus.text(DIRECTORY + "LICENSE");

        assertTrue(licence.contains("SIL OPEN FONT LICENSE Version 1.1"),
                "the licence beside the fonts is the OFL 1.1");
        assertTrue(licence.contains("Reserved Font Name"), "with its reserved font name clause");
    }

    @Test
    void theNoticeOfTheRepositoryNamesTheFonts() {
        String notice = Corpus.notice();

        assertTrue(notice.contains("LiberationSans-Regular.ttf"), "the NOTICE names the fonts");
        assertTrue(notice.contains("SIL Open Font License"), "and their licence");
        assertTrue(notice.contains("Apache PDFBox"), "and the library that writes the PDF");
    }

    /**
     * What is in the PDF is an embedded subset of both faces and no standard-14 font. A
     * rendering that referred to Helvetica would look different on every machine and could
     * not carry an umlaut reliably; one that embedded the whole file would be four hundred
     * kilobytes of font for a one-page invoice.
     */
    @Test
    void embedsASubsetOfBothFacesAndNoStandardFont() throws IOException {
        byte[] rendered = new PdfRenderer().render(Corpus.example("standard-invoice"));

        List<String> names = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(rendered)) {
            PDResources resources = pdf.getPage(0).getResources();
            for (COSName name : resources.getFontNames()) {
                PDFont font = resources.getFont(name);
                names.add(font.getName());
                assertTrue(font.isEmbedded(), font.getName() + " is embedded in the file");
                assertFalse(font.isStandard14(), font.getName() + " is not a standard-14 font");
            }
        }
        assertEquals(2, names.size(), "two faces are used: " + names);
        for (String name : names) {
            assertTrue(name.matches("[A-Z]{6}\\+LiberationSans(-Bold)?"),
                    name + " is a subset of a vendored face, under a subset tag");
        }
        assertTrue(names.stream().anyMatch(name -> name.endsWith("-Bold")),
                "and one of them is the bold face: " + names);
        assertTrue(rendered.length
                        < 2 * Corpus.bytes(DIRECTORY + "LiberationSans-Regular.ttf").length,
                "the subsets are smaller than the two files they come from");
    }
}
