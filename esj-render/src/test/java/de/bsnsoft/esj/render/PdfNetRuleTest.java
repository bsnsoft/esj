package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The rendering is net, and it derives nothing.
 *
 * <p>EN 16931-1 core terms keep their net semantics, and a renderer that quietly turned
 * them into what a consumer is used to seeing would be making the document say something
 * it does not. The document under test says 200.00 net for its one line at 19 per cent.
 * Its gross, 238.00, is in it once — as the total, where the standard puts it. The gross
 * of its unit price, 119.00, is not in it at all. Neither may appear beside the line.
 *
 * <p>BT-114 is checked separately, because it is the term a layout is most tempted to
 * misuse: it is the rounding amount of the standard, and it is labelled as that rather
 * than as a difference the renderer needed to make its own arithmetic come out.
 *
 * <p>Every case runs on the generic layout, named as such because the letter is what a
 * caller gets without asking, and on each example template, because the branded layout is
 * the one that adds a column beside the net figures and a row among the totals: a change
 * to the layout that moved a placed figure into a core row, or that let a gross figure
 * stand beside a line the document says nothing gross about, has to fail here and not in
 * somebody's rendering.
 *
 * <p>An extension is the other half of the rule. Its values are printed — a value of the
 * document is on a page — under the name its own registry gives them and in the place
 * their group gives them, marked with the namespace of the term so that a reader can see
 * they are not the standard's, and never in a row of the core totals. The two documents
 * that ask this are the sub invoice lines of the XRechnung extension and an invented
 * extension whose one term is called a gross unit price, which is the shape phase 6b will
 * bring.
 */
class PdfNetRuleTest {

    private static final Registry REGISTRY = XrImporter.defaultRegistry();

    /** The content of the extension term: a figure no core term of the document states. */
    private static final String GROSS_UNIT_PRICE = "123.45";

    /** The layouts this rule holds on: the generic one, and every example template. */
    private static final List<String> LAYOUTS = layouts();

    /** Returns the generic layout and the three example templates, for a parameterization. */
    private static List<String> layouts() {
        List<String> layouts = new ArrayList<>();
        layouts.add(null);
        layouts.addAll(List.of("letterhead.json", "image.json", "gross.json"));
        return layouts;
    }

    /** The cases: every language on every layout. */
    static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        for (RenderLanguage language : RenderLanguage.values()) {
            for (String template : LAYOUTS) {
                cases.add(new Case(language, template));
            }
        }
        return cases;
    }

    /**
     * One case: a language, and the example template it renders on or {@code null} for
     * the generic layout.
     *
     * @param language the language of the labels
     * @param template the file name of the template, or {@code null} for none
     */
    record Case(RenderLanguage language, String template) {

        /** Returns the options this case renders with. */
        RenderOptions options() {
            RenderOptions options = RenderOptions.in(language);
            return template == null ? options.layout(Layout.GENERIC)
                    : options.with(Templates.example(template));
        }

        @Override
        public String toString() {
            return language + " on " + (template == null ? "the generic layout" : template);
        }
    }

    @ParameterizedTest
    @MethodSource("cases")
    void noGrossFigureStandsBesideALine(Case layout) {
        RenderLanguage language = layout.language();
        SemanticDocument document = Documents.oneLineAtNineteenPerCent();

        String text = Pdf.flat(new PdfRenderer().render(document, layout.options()));
        String lines = between(text, Word.LINES.in(language), Word.VAT_BREAKDOWN.in(language));

        assertTrue(lines.contains(Formats.decimal("200.00", 2, language)),
                "the net line amount is beside the line");
        assertTrue(lines.contains(Formats.decimal("100.00", 2, language)),
                "and so is the net unit price");
        assertFalse(lines.contains(Formats.decimal("238.00", 2, language)),
                "the gross of the line is not: " + lines);
        assertFalse(text.contains(Formats.decimal("119.00", 2, language)),
                "and the gross of the unit price is nowhere at all: " + text);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void theGrossTotalIsInTheTotalsAndComesAfterTheNetOnes(Case layout) {
        RenderLanguage language = layout.language();
        SemanticDocument document = Documents.oneLineAtNineteenPerCent();

        String text = Pdf.flat(new PdfRenderer().render(document, layout.options()));

        assertTrue(text.indexOf(Word.VAT_BREAKDOWN.in(language))
                        < text.indexOf(Word.TOTALS.in(language)),
                "the VAT breakdown comes before the totals");
        assertTrue(text.lastIndexOf(Formats.decimal("200.00", 2, language))
                        < text.lastIndexOf(Formats.decimal("238.00", 2, language)),
                "and the net totals come before the gross ones");
    }

    @ParameterizedTest
    @MethodSource("cases")
    void theRoundingAmountIsLabelledAsTheRoundingAmount(Case layout) {
        RenderLanguage language = layout.language();
        SemanticDocument document = Documents.oneLineAtNineteenPerCent();
        String label = Labels.of(REGISTRY, language).of("BT-114");

        String text = Pdf.flat(new PdfRenderer().render(document, layout.options()));

        assertTrue(text.contains(label + " " + Formats.decimal("0.00", 2, language)),
                "BT-114 is shown under the label of the rounding amount: " + text);
    }

    /**
     * A value that sits in a group of an extension is labelled with the namespace of that
     * group, so that a reader of a sub invoice line can see whose model it belongs to.
     */
    @Test
    void aValueInsideAnExtensionGroupNamesTheNamespaceOfTheGroup() {
        String text = Pdf.flat(new PdfRenderer().render(Documents.withSubLines(),
                RenderOptions.in(RenderLanguage.ENGLISH).layout(Layout.GENERIC)));

        assertTrue(text.contains("Sub invoice line (DEX) 1 · Invoice line net amount:"),
                "a value under a group of the XRechnung extension carries its namespace: "
                        + text);
    }

    /**
     * An extension term whose name reads like a figure of the standard never reaches a row
     * of the core totals — on the generic layout, and on a template that gives it a place
     * of its own beside the lines.
     */
    @ParameterizedTest
    @MethodSource("cases")
    void anExtensionTermThatReadsLikeAGrossFigureStaysOutOfTheCoreTotals(Case layout) {
        RenderLanguage language = layout.language();
        SemanticDocument document = Documents.withAGrossUnitPriceExtension(GROSS_UNIT_PRICE);
        Registry registry = Registry.en16931().withExtension(consumerExtension());
        String figure = Formats.decimal(GROSS_UNIT_PRICE, 2, language);

        String text = Pdf.flat(new PdfRenderer(registry)
                .render(document, layout.options()));

        assertTrue(text.contains(figure), "the value of the document is on the page: " + text);
        assertFalse(text.substring(text.indexOf(Word.TOTALS.in(language))).contains(figure),
                "and no row of the core totals carries it: " + text);
    }

    /**
     * Where no template gives the term a place, it is printed with the namespace of its
     * own model behind the name, under the line it belongs to. Where one does, the place
     * carries the template's own label and the mark that says the figure was displayed,
     * which is what {@code GrossLayoutTest} asks.
     */
    @ParameterizedTest
    @EnumSource(RenderLanguage.class)
    void anExtensionTermIsPrintedUnderItsOwnNameWhereNoTemplatePlacesIt(
            RenderLanguage language) {
        SemanticDocument document = Documents.withAGrossUnitPriceExtension(GROSS_UNIT_PRICE);
        Registry registry = Registry.en16931().withExtension(consumerExtension());
        String figure = Formats.decimal(GROSS_UNIT_PRICE, 2, language);

        String text = Pdf.flat(new PdfRenderer(registry)
                .render(document, RenderOptions.in(language).layout(Layout.GENERIC)));

        assertTrue(text.contains("Gross unit price (B2C): " + figure),
                "the extension term is printed under its own name and its namespace: " + text);
    }

    /**
     * Returns the registry of an invented extension: one amount term under the invoice
     * line, called a gross unit price, in the namespace {@code B2C}. It is written here
     * rather than read from the repository because the point of it is that the renderer
     * has never seen it — an extension the project does not ship is the case a shipped one
     * cannot test.
     */
    private static Registry consumerExtension() {
        String registry = """
                {
                  "format": "EN16931-Semantic-JSON-registry",
                  "version": "0.1",
                  "model": "Consumer-Extension",
                  "edition": "Consumer extension 0.1",
                  "imports": [
                    {
                      "model": "EN16931-1",
                      "edition": "EN 16931-1:2017+A1:2019/AC:2020"
                    }
                  ],
                  "license": "Apache-2.0",
                  "notice": "Written for a test of this module.",
                  "generated": "2026-09-20",
                  "sourceStatement": "Invented for a test of this module.",
                  "terms": [
                    {
                      "id": "BT-B2C-001",
                      "kind": "BT",
                      "name": "Gross unit price",
                      "slug": "grossUnitPrice",
                      "parent": "BG-25",
                      "path": ["BG-25", "BT-B2C-001"],
                      "min": 0,
                      "max": 1,
                      "datatype": "Amount",
                      "components": [],
                      "order": 1,
                      "description": "The unit price of the line with VAT, as a consumer reads it."
                    }
                  ]
                }
                """;
        return Registry.load(new ByteArrayInputStream(
                registry.getBytes(StandardCharsets.UTF_8)));
    }

    private static String between(String text, String from, String to) {
        int start = text.indexOf(from);
        int end = text.indexOf(to, start + 1);
        return text.substring(start < 0 ? 0 : start, end < 0 ? text.length() : end);
    }
}
