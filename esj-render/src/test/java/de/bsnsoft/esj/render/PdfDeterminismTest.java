package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.bsnsoft.esj.SemanticDocument;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A rendering is a function of the document, the language and the paper, and of nothing
 * else.
 *
 * <p>Two statements are checked here, and the second is the one that costs something. The
 * first is that rendering a document twice in one run gives the same bytes. The second is
 * that those bytes are the ones checked in beside this test, which were written by another
 * run of another build on another day — a clock, a locale, a hash that iterates or a font
 * subsetter that numbered its glyphs by chance would all be caught by that and by nothing
 * else.
 *
 * <p>A golden file that fails is not automatically a bug. It says that the rendering has
 * changed, and whoever changed it decides whether that was the intention; if it was, the
 * two files are written again. What may not happen is that they change without anybody
 * noticing.
 */
class PdfDeterminismTest {

    /** Where the two checked-in renderings sit on the test classpath. */
    private static final String GOLDEN = "/golden/";

    static java.util.List<String> instances() {
        return Corpus.instances();
    }

    @ParameterizedTest
    @MethodSource("instances")
    void renderingAnInstanceTwiceGivesTheSameBytes(String instance) {
        SemanticDocument document = Corpus.instance(instance);

        assertArrayEquals(new PdfRenderer().render(document),
                new PdfRenderer().render(document),
                instance + " renders to the same bytes twice");
    }

    @Test
    void renderingInBothLanguagesAndOnBothPapersIsStableToo() {
        SemanticDocument document = Corpus.example("standard-invoice");
        for (RenderLanguage language : RenderLanguage.values()) {
            for (PageSize size : PageSize.values()) {
                RenderOptions options = RenderOptions.in(language).on(size);
                assertArrayEquals(new PdfRenderer().render(document, options),
                        new PdfRenderer().render(document, options),
                        language + " on " + size + " renders to the same bytes twice");
            }
        }
    }

    /**
     * The file identifier of a rendering is a digest of that rendering, which is the job
     * ISO 32000-1, 14.4 gives it: a number hashed with the information dictionary would
     * make every rendering of one invoice number the same file to an archive that
     * deduplicates by it, whatever the language, the paper or the template.
     */
    @Test
    void renderingsThatDifferCarryDifferentFileIdentifiers() {
        SemanticDocument document = Corpus.example("standard-invoice");
        Set<String> identifiers = new LinkedHashSet<>();
        for (RenderLanguage language : RenderLanguage.values()) {
            for (PageSize size : PageSize.values()) {
                identifiers.add(identifier(new PdfRenderer()
                        .render(document, RenderOptions.in(language).on(size))));
            }
        }
        identifiers.add(identifier(new PdfRenderer().render(document,
                RenderOptions.defaults().with(Templates.example("letterhead.json")))));
        identifiers.add(identifier(new PdfRenderer().render(Corpus.example("minimal"))));

        assertEquals(6, identifiers.size(),
                "six renderings that differ, six identifiers: " + identifiers);
        assertEquals(identifier(new PdfRenderer().render(document)),
                identifier(new PdfRenderer().render(document)),
                "and the same rendering twice is the same identifier");
    }

    /** Returns both halves of the file identifier of a PDF, as hexadecimal. */
    private static String identifier(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            COSArray array = document.getDocument().getTrailer().getCOSArray(COSName.ID);
            assertEquals(2, array.size(), "a PDF carries two halves of an identifier");
            String first = ((COSString) array.get(0)).toHexString();
            assertEquals(first, ((COSString) array.get(1)).toHexString(),
                    "a file nothing has rewritten carries the same value twice");
            return first;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theRenderingOfMinimalIsTheOneCheckedIn() {
        assertGolden("minimal-de-a4.pdf", Corpus.example("minimal"), RenderOptions.defaults());
    }

    @Test
    void theRenderingOfTheStandardInvoiceIsTheOneCheckedIn() {
        assertGolden("standard-invoice-en-letter.pdf", Corpus.example("standard-invoice"),
                RenderOptions.in(RenderLanguage.ENGLISH).on(PageSize.LETTER));
    }

    /**
     * And so are the two renderings of the letter layout: the branded one a sender posts,
     * and the plain one on the other paper. The first carries a letterhead, a logo, a
     * colour scheme and the fold and punch marks of the example template, so it is the file
     * that would notice a change to any of those; the second is the layout alone.
     */
    @Test
    void theLettersAreTheOnesCheckedIn() {
        assertGolden("standard-invoice-letter-de-a4.pdf", Corpus.example("standard-invoice"),
                RenderOptions.defaults().with(Templates.example("letter.json")));
        assertGolden("standard-invoice-letter-en-letter.pdf",
                Corpus.example("standard-invoice"),
                RenderOptions.in(RenderLanguage.ENGLISH).on(PageSize.LETTER)
                        .layout(Layout.LETTER));
    }

    @ParameterizedTest
    @MethodSource("instances")
    void renderingAnInstanceAsALetterTwiceGivesTheSameBytes(String instance) {
        SemanticDocument document = Corpus.instance(instance);
        RenderOptions options = RenderOptions.defaults().layout(Layout.LETTER);

        assertArrayEquals(new PdfRenderer().render(document, options),
                new PdfRenderer().render(document, options),
                instance + " renders to the same letter twice");
    }

    /**
     * Nothing of the moment reaches the file: no creation date, no modification date, a
     * producer that names the tool without naming a build, and an XMP packet that carries
     * no date either — the packet is where one would come back in, because an archival file
     * usually says when it was written. The information dictionary is read back with PDFBox
     * rather than searched for as text, because a PDF writes its objects compressed and a
     * search through the bytes would find nothing and prove nothing.
     */
    @Test
    void theDocumentCarriesNoDateAndAFixedProducer() throws IOException {
        try (PDDocument pdf = Loader.loadPDF(new PdfRenderer().render(Corpus.example("minimal")))) {
            PDDocumentInformation information = pdf.getDocumentInformation();
            assertNull(information.getCreationDate(), "no creation date");
            assertNull(information.getModificationDate(), "no modification date");
            assertEquals("EN16931 Semantic JSON (esj-render)", information.getProducer(),
                    "the producer is a fixed string");
            assertEquals(information.getProducer(), information.getCreator(),
                    "and so is the creator");
        }
        String packet = Xmp.of(new PdfRenderer().render(Corpus.example("minimal")));
        for (String property : new String[] {
                "xmp:CreateDate", "xmp:ModifyDate", "xmp:MetadataDate"}) {
            assertFalse(packet.contains(property), "the XMP packet carries no " + property);
        }
    }

    /**
     * The machine the rendering runs on takes no part in it either. A default locale of
     * another script is the one property of a machine that reaches a number through a
     * formatter that was not told which language it is writing, and it would put
     * Arabic-Indic digits into a German date and into a page number without changing
     * anything else — which is exactly the kind of difference a golden file exists to
     * catch and a build on one machine never sees.
     */
    @Test
    void theRenderingIsTheSameUnderAnyDefaultLocale() {
        Locale machine = Locale.getDefault();
        try {
            for (String tag : new String[] {"ar-EG", "ne-NP", "tr-TR"}) {
                Locale.setDefault(Locale.forLanguageTag(tag));
                assertGolden("minimal-de-a4.pdf", Corpus.example("minimal"),
                        RenderOptions.defaults());
                assertGolden("standard-invoice-en-letter.pdf",
                        Corpus.example("standard-invoice"),
                        RenderOptions.in(RenderLanguage.ENGLISH).on(PageSize.LETTER));
                assertGolden("standard-invoice-letter-de-a4.pdf",
                        Corpus.example("standard-invoice"),
                        RenderOptions.defaults().with(Templates.example("letter.json")));
                assertGolden("standard-invoice-letter-en-letter.pdf",
                        Corpus.example("standard-invoice"),
                        RenderOptions.in(RenderLanguage.ENGLISH).on(PageSize.LETTER)
                                .layout(Layout.LETTER));
            }
        } finally {
            Locale.setDefault(machine);
        }
    }

    private void assertGolden(String name, SemanticDocument document, RenderOptions options) {
        byte[] rendered = new PdfRenderer().render(document, options);
        byte[] golden = Corpus.bytes(GOLDEN + name);

        assertEquals(Corpus.sha256(golden), Corpus.sha256(rendered),
                name + " is the rendering this build produces; if the change was intended, "
                        + "write the file again");
        assertArrayEquals(golden, rendered, name + " is unchanged, byte for byte");
    }
}
