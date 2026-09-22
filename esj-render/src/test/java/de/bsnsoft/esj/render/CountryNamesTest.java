package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The checked-in table of country names, and the generator it came from.
 *
 * <p>A rendering writes the same bytes on every machine, so the renderer reads the name of
 * a country out of a file of this repository rather than out of the locale data of whatever
 * runtime it happens to run on. The file was written once, by {@link CountryNames}, out of
 * the Unicode CLDR of the runtime it records in its header.
 *
 * <p>This test is the tie between the two, and it is deliberately one-sided. <b>On the
 * runtime the header names</b> it runs the generator again and compares, so that the file
 * cannot quietly stop being what that code writes. <b>On any other runtime</b> it does not:
 * the CLDR of a later release names a country differently — that is what a CLDR release
 * does — and a build that failed for it would be a build punishing somebody for upgrading
 * their JDK. What it checks there is what a reader needs either way: the file is the shape
 * of a table of names, it covers the countries an invoice of this corpus names, and the
 * renderer reads it.
 */
class CountryNamesTest {

    /** Where the tables of names live in the source tree, for the generator to write into. */
    private static final String DIRECTORY = "/de/bsnsoft/esj/render/names/";

    @Test
    void theCheckedInFileIsWhatTheGeneratorWritesOnTheRuntimeItRecords() {
        String checkedIn = new String(Corpus.bytes(DIRECTORY + CountryNames.FILE),
                java.nio.charset.StandardCharsets.UTF_8);
        String recorded = TemplateJson.text(TemplateJson.read(checkedIn.getBytes(
                java.nio.charset.StandardCharsets.UTF_8)), "javaRuntimeVersion",
                CountryNames.FILE);

        assertTrue(recorded != null && !recorded.isBlank(),
                "the file records the runtime it was generated on");
        assumeTrue(recorded.equals(CountryNames.runtimeVersion()),
                "this build runs on the runtime the file records (" + recorded + "), and not "
                        + CountryNames.runtimeVersion());
        assertEquals(checkedIn, CountryNames.generate(),
                CountryNames.FILE + " is what CountryNames writes on " + recorded
                        + "; to write it again, run CountryNames with the resource directory");
    }

    /** The file is a table of names of the shape this module reads, whatever the runtime. */
    @Test
    void theFileIsATableOfNamesInBothLanguages() {
        Object tree = TemplateJson.read(Corpus.bytes(DIRECTORY + CountryNames.FILE));

        assertEquals(CountryNames.FORMAT,
                TemplateJson.text(tree, "displayNames", CountryNames.FILE), "the marker");
        assertEquals("country", TemplateJson.text(tree, "list", CountryNames.FILE), "the list");
        assertTrue(TemplateJson.text(tree, "english", CountryNames.FILE).contains("CLDR"),
                "the header says where the names come from");
        assertTrue(TemplateJson.text(tree, "generator", CountryNames.FILE)
                        .contains("CountryNames"),
                "and which generator wrote them");
    }

    /** The countries the conformance corpus and the examples name all have a name. */
    @Test
    void everyCountryTheCorpusNamesIsInTheTable() {
        for (String code : List.of("DE", "AT", "CH", "FR", "IT", "NL", "BE", "LU", "DK",
                "SE", "NO", "GB", "US", "ES", "PL")) {
            for (RenderLanguage language : RenderLanguage.values()) {
                Optional<String> name =
                        DisplayNames.of(DisplayNames.CodeList.COUNTRY, code, language);
                assertTrue(name.isPresent() && !name.get().isBlank(),
                        code + " has a name in " + language);
            }
        }
        assertEquals(Optional.of("Deutschland"), DisplayNames.of(
                DisplayNames.CodeList.COUNTRY, "DE", RenderLanguage.GERMAN), "and DE is one");
        assertEquals(Optional.of("Germany"), DisplayNames.of(
                DisplayNames.CodeList.COUNTRY, "DE", RenderLanguage.ENGLISH), "in both");
    }

    /**
     * The generator reads the runtime and the renderer does not. This renders the same
     * country under a default locale of another language and asks for the same name back,
     * which is the property the file exists for.
     */
    @Test
    void theNameDoesNotFollowTheDefaultLocale() {
        Locale machine = Locale.getDefault();
        try {
            for (String tag : new String[] {"ar-EG", "ne-NP", "tr-TR"}) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                assertEquals(Optional.of("Deutschland"), DisplayNames.of(
                                DisplayNames.CodeList.COUNTRY, "DE", RenderLanguage.GERMAN),
                        "under the default locale " + tag);
            }
        } finally {
            Locale.setDefault(machine);
        }
    }
}
