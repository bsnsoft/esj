package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.bsnsoft.esj.SemanticDocument;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A rendering is a function of the document, the language and the vendored stylesheet.
 *
 * <p>Nothing of the machine, the moment or the run takes part in it. That is worth a test
 * of its own rather than a sentence in a Javadoc, because it is what lets a rendering be
 * checked in, diffed, cached and compared between two runs of a pipeline — and because
 * a producer string or a timestamp is the kind of thing a dependency adds without being
 * asked.
 */
class HtmlDeterminismTest {

    static List<String> instances() {
        return Corpus.instances();
    }

    static List<String> examples() {
        return Corpus.EXAMPLES;
    }

    @ParameterizedTest
    @MethodSource("instances")
    void everyInstanceOfTheCorpusIsRenderedTheSameWayTwice(String instance) {
        SemanticDocument document = Corpus.instance(instance);
        HtmlRenderer renderer = new HtmlRenderer();

        assertEquals(renderer.render(document), renderer.render(document),
                instance + " is rendered character for character the same way twice");
    }

    @ParameterizedTest
    @MethodSource("examples")
    void everyExampleIsRenderedTheSameWayByTwoRenderers(String example) {
        SemanticDocument document = Corpus.example(example);

        for (RenderLanguage language : RenderLanguage.values()) {
            assertEquals(new HtmlRenderer().render(document, RenderOptions.in(language)),
                    new HtmlRenderer().render(document, RenderOptions.in(language)),
                    example + " in " + language + " does not depend on which renderer wrote it");
        }
    }
}
