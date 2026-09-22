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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What of a document reaches its rendering.
 *
 * <p>The claim this test makes is the one a user of the renderer needs: every value the
 * document carries is in the rendering, and so is every supplementary component beside it.
 * It is checked on every instance of the conformance corpus, on every example of the
 * repository, in both languages, and then on a document that carries a value at every path
 * the registry has — which the corpus alone does not, because no real invoice uses every
 * business term of EN 16931-1 at once.
 *
 * <p>Found means found in the displayed text, in one of the forms {@link Presentation}
 * lists for the semantic data type of the term. The order of the values is not checked and
 * could not be: the stylesheet arranges an invoice over five tabs of its own design, and
 * the canonical path order of the document is not the order a reader reads it in.
 */
class HtmlCoverageTest {

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
     * XRechnung extension, one occurrence of every repeatable group, built by
     * {@link Documents#everyTerm()} — reaches the rendering whole. This is the statement the corpus cannot make: it is the statement
     * that the stylesheet has a place for every business term this project knows.
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void everyTermOfTheRegistryIsInTheRendering(RenderLanguage language) {
        assertEverythingIsShown(Documents.everyTerm(), language, "a document of every term");
    }

    /** Nothing of that document stays behind on the way into the XR representation. */
    @Test
    void everyTermOfTheRegistryHasAnElementOfTheXrRepresentation() {
        assertEquals(List.of(),
                new HtmlRenderer().renderWithReport(Documents.everyTerm(), RenderOptions.defaults())
                        .report().notes(),
                "every term of the registry reaches the XR representation");
    }

    /**
     * An attachment is written into the rendering whole, in a hidden element the download
     * link reads out — which is how a self-contained file can offer a file to save. A
     * rendering is therefore at least as large as the attachments of the invoice, and a
     * caller who renders a document with a large attachment should know that before it
     * happens rather than afterwards.
     */
    @Test
    void anAttachmentIsCarriedIntoTheRenderingWhole() {
        SemanticDocument document =
                Corpus.instance("business-cases/standard/01.15a-INVOICE_ubl.xml");
        SemanticValue attachment = document.value(SemanticPath.of("/BG-24/0/BT-125")).orElseThrow();

        String html = new HtmlRenderer().render(document);

        assertTrue(attachment.content().length() > 100_000,
                "the instance of the corpus carries a large attachment");
        assertEquals(1, Html.count(html, attachment.content()),
                "the attachment stands once in the rendering, in full");
        assertTrue(html.length() > attachment.content().length(),
                "the rendering is at least as large as the attachment it carries");
    }

    private void assertEverythingIsShown(SemanticDocument document, RenderLanguage language,
                                         String what) {
        String text = Html.text(new HtmlRenderer().render(document, RenderOptions.in(language)));
        List<String> missing = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            SemanticPath path = entry.getKey();
            SemanticValue value = entry.getValue();
            SemanticType type = REGISTRY.datatype(path.term()).orElse(SemanticType.TEXT);
            Set<String> forms = Presentation.of(path.term(), type, value.content(), language);
            if (forms.stream().noneMatch(text::contains)) {
                missing.add(path + " = " + forms);
            }
            for (String component : components(value)) {
                if (!text.contains(Presentation.flatten(component))) {
                    missing.add(path + " component = " + component);
                }
            }
        }
        assertEquals(List.of(), missing,
                what + " in " + language + ": every value is in the rendering");
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
