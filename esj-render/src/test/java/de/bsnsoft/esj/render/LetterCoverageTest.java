package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.xr.XrImporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What of a document reaches its letter.
 *
 * <p>This is the statement of {@code PdfCoverageTest} for the second layout, and it is the
 * one that makes a second layout safe to offer: the letter puts what a reader expects
 * where a reader expects it, and everything it has no place of its own for stands under
 * its closing heading with its label and its semantic path. Every value of the document is
 * therefore on a page of the letter as well — over the whole conformance corpus, over the
 * examples of the repository, in both languages and on both papers.
 *
 * <p>A code the letter writes under a name counts through the line the closing heading
 * carries for it: {@code Einheit H87 = Stück} has the code in it, so a test that looks for
 * the content of the value finds it there. That is not a loophole but the rule the layout
 * keeps — a page never hides what the document says.
 *
 * <p>A value is looked for in the form the letter writes it, which is
 * {@link Formats#asWritten(SemanticType, String, RenderLanguage)}: this layout writes a
 * percentage with the decimal places the document wrote, so 19.00 is looked for as
 * {@code 19 %}. That keeps this a test of the layout rather than of the formatting.
 */
class LetterCoverageTest {

    private static final Registry REGISTRY = XrImporter.defaultRegistry();

    static List<String> instances() {
        return Corpus.instances();
    }

    static List<String> examples() {
        return Corpus.EXAMPLES;
    }

    @ParameterizedTest
    @MethodSource("instances")
    void everyValueOfAnInstanceIsInItsLetter(String instance) {
        SemanticDocument document = Corpus.instance(instance);
        for (RenderLanguage language : RenderLanguage.values()) {
            for (PageSize size : PageSize.values()) {
                assertEverythingIsShown(document, language, size, instance);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("examples")
    void everyValueOfAnExampleIsInItsLetter(String example) {
        SemanticDocument document = Corpus.example(example);
        for (RenderLanguage language : RenderLanguage.values()) {
            for (PageSize size : PageSize.values()) {
                assertEverythingIsShown(document, language, size, "examples/" + example);
            }
        }
    }

    /**
     * A document with a value at every path of the registry is printed whole by the letter
     * too. The corpus cannot make that statement: no real invoice uses every business term
     * of EN 16931-1 at once, and the letter has a place of its own for only a part of them.
     */
    @Test
    void everyTermOfTheRegistryIsInTheLetter() {
        for (RenderLanguage language : RenderLanguage.values()) {
            assertEverythingIsShown(Documents.everyTerm(), language, PageSize.A4,
                    "a document of every term");
        }
    }

    /** And so is a document rendered on the example letter template. */
    @Test
    void everyValueIsInTheLetterOnTheExampleTemplate() {
        SemanticDocument document = Corpus.example("standard-invoice");
        for (RenderLanguage language : RenderLanguage.values()) {
            byte[] pdf = new PdfRenderer().render(document, RenderOptions.in(language)
                    .with(Templates.example("letter.json")));
            assertEquals(List.of(), missing(document, Pdf.text(pdf), language),
                    "every value is in the branded letter in " + language);
        }
    }

    /**
     * The closing heading names every code the letter wrote a name for, once. Without that
     * line the page would say {@code Stück} where the document says {@code H87}, and a
     * reader comparing the page with the file would find a value that is not there.
     */
    @Test
    void aCodeWrittenUnderItsNameIsListedOnce() {
        String text = Pdf.flat(new PdfRenderer().render(Corpus.example("multiple-lines"),
                RenderOptions.defaults().layout(Layout.LETTER)));

        String line = Word.CODE_UNIT.in(RenderLanguage.GERMAN) + " H87 = Stück";
        assertTrue(text.contains(line), "the code line is there: " + text);
        assertEquals(text.indexOf(line), text.lastIndexOf(line),
                "and it is there once, however many lines use the unit");
    }

    /**
     * A document of the 2026 edition is drawn by the letter as completely as by the
     * generic layout, and the two are measured against each other rather than each against
     * itself.
     *
     * <p>{@code PdfCoverageTest} makes the claim for the generic layout over a document of
     * every term of that registry. This makes it for the letter over the example the
     * repository ships, and asserts the same list of missing values for both layouts, so
     * that a term the letter loses cannot hide behind a term the generic layout loses too.
     *
     * <p>Skipped where this build carries no registry of that edition.
     */
    @Test
    @EnabledIf("carries2026")
    void aDocumentOfTheOtherEditionIsInBothLayoutsAlike() {
        Registry registry = Registry.forEdition("2026");
        SemanticDocument document = Corpus.example("edition-2026");
        for (RenderLanguage language : RenderLanguage.values()) {
            assertEquals(List.of(), shownBy(registry, document, language, Layout.GENERIC),
                    "every value of the 2026 example is in the generic rendering in "
                            + language);
            assertEquals(List.of(), shownBy(registry, document, language, Layout.LETTER),
                    "every value of the 2026 example is in the letter in " + language);
        }
    }

    static boolean carries2026() {
        return Registry.editions().contains("2026");
    }

    /**
     * Returns the values a layout of a registry other than the default one leaves out,
     * each value looked for in the form that layout writes it: the letter writes a
     * percentage with the decimal places the document wrote, the generic layout with two.
     */
    private static List<String> shownBy(Registry registry, SemanticDocument document,
                                        RenderLanguage language, Layout layout) {
        byte[] pdf = new PdfRenderer(registry).render(document,
                RenderOptions.in(language).layout(layout));
        return missing(registry, document, Pdf.text(pdf), language, layout);
    }

    private void assertEverythingIsShown(SemanticDocument document, RenderLanguage language,
                                         PageSize size, String what) {
        byte[] pdf = new PdfRenderer().render(document,
                RenderOptions.in(language).on(size).layout(Layout.LETTER));
        assertEquals(List.of(), missing(document, Pdf.text(pdf), language),
                what + " in " + language + " on " + size + ": every value is in the letter");
    }

    /** Returns the values of a document that the text of its letter does not carry. */
    private static List<String> missing(SemanticDocument document, String text,
                                        RenderLanguage language) {
        return missing(REGISTRY, document, text, language, Layout.LETTER);
    }

    /** The same, for the registry of the document's own edition and a chosen layout. */
    private static List<String> missing(Registry registry, SemanticDocument document,
                                        String text, RenderLanguage language, Layout layout) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            SemanticPath path = entry.getKey();
            SemanticValue value = entry.getValue();
            SemanticType type = registry.datatype(path.term()).orElse(SemanticType.TEXT);
            String shown = layout == Layout.LETTER
                    ? Formats.asWritten(type, value.content(), language)
                    : Formats.value(type, value.content(), language);
            if (type != SemanticType.BINARY_OBJECT && !Pdf.shows(text, shown)) {
                missing.add(path + " = " + value.content());
            }
            for (String component : components(value)) {
                if (!Pdf.shows(text, component)) {
                    missing.add(path + " component = " + component);
                }
            }
        }
        return missing;
    }

    private static List<String> components(SemanticValue value) {
        List<String> components = new ArrayList<>();
        for (String component : new String[] {
                value.scheme(), value.schemeVersion(), value.mimeCode(), value.filename()}) {
            if (component != null) {
                components.add(component);
            }
        }
        return components;
    }
}
