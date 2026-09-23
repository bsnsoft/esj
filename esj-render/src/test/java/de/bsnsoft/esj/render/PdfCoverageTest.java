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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What of a document reaches its PDF in the generic layout.
 *
 * <p>The layout is named in every rendering here, because it is not the one a caller gets
 * without asking: that is the letter, and {@code LetterCoverageTest} makes the same claim
 * for it.
 *
 * <p>The claim this test makes is the one a user of the renderer needs: every value the
 * document carries is printed, and so is every supplementary component beside it. It is
 * checked on every instance of the conformance corpus, on every example of the repository,
 * in both languages, and then on a document that carries a value at every path the
 * registry has — which the corpus alone does not, because no real invoice uses every
 * business term of EN 16931-1 at once.
 *
 * <p>Found means found in the printed text, in the form {@link Formats} writes a value of
 * that semantic data type in — wrapped between two words, or broken inside a long one,
 * which {@link Pdf#shows} allows for. The order of the values is not checked: the layout
 * arranges an invoice in the sections a reader expects, and the canonical path order of
 * the document is not the order a reader reads it in.
 *
 * <p>The content of an attachment is the one value that is not printed, and that is a
 * decision rather than an omission: BT-125 appears with its file name, its media type and
 * its size, and {@code docs/rendering.md} says why the base64 itself does not.
 */
class PdfCoverageTest {

    private static final Registry REGISTRY = XrImporter.defaultRegistry();

    static List<String> instances() {
        return Corpus.instances();
    }

    static List<String> examples() {
        return Corpus.EXAMPLES;
    }

    @ParameterizedTest
    @MethodSource("instances")
    void everyValueOfAnInstanceIsInItsGermanRendering(String instance) {
        assertEverythingIsShown(Corpus.instance(instance), RenderLanguage.GERMAN, instance);
    }

    @ParameterizedTest
    @MethodSource("instances")
    void everyValueOfAnInstanceIsInItsEnglishRendering(String instance) {
        assertEverythingIsShown(Corpus.instance(instance), RenderLanguage.ENGLISH, instance);
    }

    @ParameterizedTest
    @MethodSource("examples")
    void everyValueOfAnExampleIsInItsRenderings(String example) {
        SemanticDocument document = Corpus.example(example);
        for (RenderLanguage language : RenderLanguage.values()) {
            assertEverythingIsShown(document, language, "examples/" + example);
        }
    }

    /**
     * A document with a value at every path of the registry — the core model and the
     * XRechnung extension, one occurrence of every repeatable group — is printed whole.
     * This is the statement the corpus cannot make: that the layout has a place for every
     * business term this project knows, and that what it has no section for still reaches
     * the page under the last heading.
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void everyTermOfTheRegistryIsInTheRendering(RenderLanguage language) {
        assertEverythingIsShown(Documents.everyTerm(), language, "a document of every term");
    }

    /**
     * How many values that document carries. A business term that stands in two groups
     * has two value paths, so the number is larger than the number of business terms of
     * the registry and smaller than nothing in particular — which is why it is pinned
     * here rather than left to be recounted by hand whenever a document says it.
     * {@code docs/rendering.md} quotes it.
     */
    @Test
    void theDocumentOfEveryTermCarriesTheNumberOfValuesTheDocumentationStates() {
        assertEquals(203, Documents.everyTerm().values().size(),
                "a value at every value path of the core model and the XRechnung extension");
    }

    /**
     * The same claim for an edition other than the default one: a document with a value at
     * every path of the 2026 registry is printed whole.
     *
     * <p>This is what makes the rendering of a new edition a property of the code rather
     * than a promise. The layout has designed sections for the groups of the model it was
     * written for and a final heading under which everything else stands with its label
     * and its semantic path, both read from the registry the renderer was given — so the
     * day a registry lands, every value of a document of that edition reaches the page,
     * and the sections are designed afterwards as they earn it.
     *
     * <p>Skipped where this build carries no registry of that edition.
     */
    @Test
    @EnabledIf("carries2026")
    void everyTermOfAnotherEditionIsInTheRenderingToo() {
        Registry registry = Registry.forEdition("2026");
        assertEverythingIsShown(registry, Documents.everyTerm(registry),
                RenderLanguage.GERMAN, "a document of every term of the 2026 edition");
    }

    static boolean carries2026() {
        return Registry.editions().contains("2026");
    }

    /**
     * The sub invoice lines of the XRechnung extension are printed under the invoice line
     * they belong to, numbered, and a sub line of a sub line carries the numbers of both.
     * They stand between the line and the VAT breakdown, which is where a reader of the
     * line looks for them.
     */
    @Test
    void subInvoiceLinesAreShownUnderTheirLine() {
        SemanticDocument document = Documents.withSubLines();

        String text = Pdf.flat(new PdfRenderer().render(document, generic(RenderLanguage.GERMAN)));

        int line = text.indexOf("Assembly, complete");
        int breakdown = text.indexOf(Word.VAT_BREAKDOWN.in(RenderLanguage.GERMAN));
        for (String value : new String[] {"1.1", "Bracket", "1.2", "Fitting",
                                          "1.2.1", "Adjustment"}) {
            int at = text.indexOf(value, line);
            assertTrue(at > line && at < breakdown,
                    "the sub line value " + value + " stands under its invoice line");
        }
        assertEverythingIsShown(document, RenderLanguage.GERMAN, "sub lines");
    }

    /**
     * An attachment is named and measured, and its content is not printed. A rendering is
     * therefore not larger for a large attachment, which is the opposite of what the HTML
     * rendering does and is worth knowing before it happens rather than afterwards.
     */
    @Test
    void anAttachmentIsNamedAndMeasuredButNotPrinted() {
        SemanticDocument document =
                Corpus.instance("business-cases/standard/01.15a-INVOICE_ubl.xml");
        SemanticValue attachment = document.value(SemanticPath.of("/BG-24/0/BT-125")).orElseThrow();

        byte[] pdf = new PdfRenderer().render(document, generic(RenderLanguage.GERMAN));
        String text = Pdf.flat(pdf);

        assertTrue(attachment.content().length() > 100_000,
                "the instance of the corpus carries a large attachment");
        assertTrue(text.contains(attachment.filename()), "the file name is printed");
        assertTrue(text.contains(attachment.mimeCode()), "the media type is printed");
        assertTrue(text.contains(Formats.bytes(attachment.asBytes().length,
                        RenderLanguage.GERMAN)),
                "the size in bytes is printed: " + text);
        assertTrue(!text.contains(attachment.content().substring(0, 64)),
                "the content of the attachment is not printed");
        assertTrue(pdf.length < attachment.content().length(),
                "and the rendering is smaller than the attachment it does not carry");
    }

    private void assertEverythingIsShown(SemanticDocument document, RenderLanguage language,
                                         String what) {
        assertEverythingIsShown(REGISTRY, document, language, what);
    }

    private void assertEverythingIsShown(Registry registry, SemanticDocument document,
                                         RenderLanguage language, String what) {
        byte[] pdf = new PdfRenderer(registry).render(document, generic(language));
        String text = Pdf.text(pdf);
        List<String> missing = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            SemanticPath path = entry.getKey();
            SemanticValue value = entry.getValue();
            SemanticType type = registry.datatype(path.term()).orElse(SemanticType.TEXT);
            if (type != SemanticType.BINARY_OBJECT
                    && !Pdf.shows(text, Formats.value(type, value.content(), language))) {
                missing.add(path + " = " + value.content());
            }
            for (String component : components(value)) {
                if (!Pdf.shows(text, component)) {
                    missing.add(path + " component = " + component);
                }
            }
        }
        assertEquals(List.of(), missing,
                what + " in " + language + ": every value is in the rendering");
    }

    /** Returns the options of the generic layout in a language, which this test is about. */
    private static RenderOptions generic(RenderLanguage language) {
        return RenderOptions.in(language).layout(Layout.GENERIC);
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
