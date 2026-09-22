package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Checks that the vendored visualization is the one the README beside it describes. The
 * claim that these are unmodified copies of a tagged release is only worth something while
 * a digest backs it.
 *
 * <p>The licence text of FileSaver.js is pinned here too, although it is the one file of
 * that directory this project wrote rather than copied: the MIT licence asks for it to
 * travel with every copy of the software, so it is shipped, and a file that is shipped
 * for a reason is a file whose disappearance should fail a build.
 */
class VendoredVisualizationTest {

    private static final String DIRECTORY = "/de/bsnsoft/esj/render/kosit/";

    private static final Map<String, String> DIGESTS = digests();

    private static Map<String, String> digests() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("FileSaver-v2.0.5.js",
                "6060c139808ad689ae6f055ea65eb8eaa90314fcceda72af727a369a7e69f263");
        expected.put("common-xr.xsl",
                "3155e48fe19dd23ff74e3f191721e28c7a51a5e76a093fb1e2ee60bfe08edc41");
        expected.put("functions.xsl",
                "1e8e2c414a19d63007408856649037c8654f5c7391618c48010865dac60db3ed");
        expected.put("l10n/de.xml",
                "6f29bb1b02ace03e0753315e455d7516f871123967e2668c54954e242c4c2d67");
        expected.put("l10n/en.xml",
                "7966d747319d92f918f58420e309303f3b178ca4b68faad479e88b45ce7252d2");
        expected.put("xrechnung-html.xsl",
                "7a8bdd9f2653bfa1042d86a963af2071a9168cfbf0aef5be27975abedef20ccc");
        expected.put("xrechnung-viewer.css",
                "f480e173816b701522eb15eccd782ca10e439d9a34838a8688f45b6eed1ebed5");
        expected.put("xrechnung-viewer.js",
                "fb31206bb89009f358891d6123573d425f6c310527b0c7f64ee041e9993c8d92");
        expected.put("LICENSE",
                "e0d7665e91531aebb79e4feaa415796f076b0f93e9eddd6d1d05efe9d93808ac");
        expected.put("FileSaver-LICENSE.txt",
                "9deccce57a52c1f86f06e4944bf8979aa1590c34a4974d69e94b9e915afd0db4");
        return Map.copyOf(expected);
    }

    /** The permission notice the MIT licence of FileSaver.js asks to travel with it. */
    @Test
    void shipsThePermissionNoticeOfTheOneFileUnderTheMitLicence() {
        String licence = Corpus.text(DIRECTORY + "FileSaver-LICENSE.txt");

        assertTrue(licence.contains("Eli Grey"), "it carries the copyright notice");
        assertTrue(licence.contains("Permission is hereby granted, free of charge"),
                "and the permission notice");
        assertTrue(licence.contains("shall be included in all"),
                "which is the clause that asks for it to be here");
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
        assertTrue(readme.contains("v2026-08-31"), "the README records the tag");
    }

    /**
     * The two stylesheets this module shares with {@code esj-xr} are the same files there.
     * They are copied twice on purpose, and the purpose is defeated the moment the two
     * copies are two different files.
     */
    @Test
    void theSharedStylesheetsAreTheSameFilesAsInTheImporter() {
        for (String name : new String[] {"common-xr.xsl", "functions.xsl"}) {
            assertEquals(Corpus.sha256(Corpus.bytes(
                            "/de/bsnsoft/esj/xr/kosit/" + name)),
                    Corpus.sha256(Corpus.bytes(DIRECTORY + name)),
                    name + " is one file vendored in two modules, not two files");
        }
    }

    /**
     * The stylesheet reaches outside itself in exactly three ways, and the resolvers of
     * this module answer exactly those three. A newer tag that added a fourth would have
     * to be noticed, and this is where it is noticed.
     */
    @Test
    void theStylesheetReachesOutsideItselfOnlyInTheWaysTheResolversAnswer() {
        String stylesheet = new String(Corpus.bytes(DIRECTORY + "xrechnung-html.xsl"),
                StandardCharsets.UTF_8);

        assertEquals(1, Html.count(stylesheet, "<xsl:import"), "it imports one stylesheet");
        assertEquals(0, Html.count(stylesheet, "<xsl:include"), "and includes none itself");
        assertEquals(3, Html.count(stylesheet, "unparsed-text("),
                "it inlines three files");
        assertEquals(0, Html.count(stylesheet, " doc("),
                "and reads no document of its own; the localization is read by functions.xsl");
    }

    /**
     * Every element of the XR representation is named in the stylesheet. The table of
     * {@code esj-xr} is derived from the schema of the same release of the same project,
     * so this compares the two sides of one model: what an ESJ document can be written as,
     * and what the rendering has a place for.
     */
    @Test
    void theStylesheetNamesEveryElementOfTheXrRepresentation() {
        String stylesheet = new String(Corpus.bytes(DIRECTORY + "xrechnung-html.xsl"),
                StandardCharsets.UTF_8);

        for (String line : Corpus.text(
                "/de/bsnsoft/esj/xr/xr-elements.tsv").split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t");
            assertTrue(stylesheet.matches("(?s).*xr:" + fields[2] + "(?![A-Za-z0-9_]).*"),
                    "the stylesheet names the element " + fields[2] + " of " + fields[1]);
        }
    }
}
