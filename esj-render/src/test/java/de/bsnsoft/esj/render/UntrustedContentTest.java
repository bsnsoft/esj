package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * What an invoice from a stranger can put into its own rendering.
 *
 * <p>An invoice is a document somebody else wrote, and a rendering of it is HTML that
 * somebody may open in a browser. This test states what the baseline rendering does with
 * content that was written to be more than content: markup stays text, a location the
 * rendering would have to follow is written as text unless its scheme is one a document may
 * ask a reader to follow, a value on its way into a script call is escaped for the string
 * literal it stands in, and a character that directs the reading order does not reach the
 * page. The last three are the work of {@code xsl/esj-html.xsl}, which imports the vendored
 * stylesheet and overrides the one rule that writes those places; the vendored bytes are
 * untouched.
 */
class UntrustedContentTest {

    /** Markup and the characters that would end an attribute or a string, in one value. */
    private static final String MARKUP = "<script>alert('x')</script> & \" ' <b>bold</b>";

    /** A location and a file name that are only what they say they are. */
    private static final String HARMLESS_LOCATION = "https://example.org/document.pdf";

    private static final String HARMLESS_FILENAME = "document.pdf";

    @Test
    void markupInTheTextOfAnInvoiceStaysText() {
        String html = new HtmlRenderer().render(withMarkup());

        assertEquals(2, Html.count(html, "<script"),
                "the two scripts the stylesheet inlines are still the only ones");
        assertFalse(html.contains("<script>alert('x')"),
                "the script element of the invoice did not become a script element");
        assertTrue(html.contains("&lt;script&gt;"),
                "it became the text it is");
        assertFalse(html.contains("<b>bold</b>"),
                "and neither did the invoice get to choose a font");
        assertTrue(Html.text(html).contains(MARKUP),
                "a reader sees exactly what the invoice wrote");
    }

    /**
     * BT-124, the location of an external document, decides where a link goes, so a
     * location of a scheme a reader would not want to follow is written as the text it is
     * and not as a link. The location is still on the page: a rendering that dropped it
     * would hide from the reader what the invoice says.
     */
    @Test
    void anExternalDocumentLocationOfAnUnknownSchemeIsNotALink() {
        String html = new HtmlRenderer().render(withMarkup());

        assertFalse(html.contains("href=\"javascript:"),
                "no link of the page goes where BT-124 asked it to go");
        assertTrue(Html.text(html).contains("javascript:alert('pwned')"),
                "and the reader still sees what BT-124 says");
    }

    /** A location of an ordinary scheme is a link, which is what the field is for. */
    @Test
    void anOrdinaryExternalDocumentLocationIsStillALink() {
        String html = new HtmlRenderer().render(
                fixture("plain text", HARMLESS_LOCATION, HARMLESS_FILENAME));

        assertTrue(html.contains("href=\"" + HARMLESS_LOCATION + "\""),
                "an http location is linked as it always was");
    }

    /**
     * The download link of an attachment carries BT-122, the media type and the file name
     * into the arguments of a script call, inside single quotes. An HTML serializer does
     * not escape an apostrophe inside an attribute delimited by double quotes, so an
     * apostrophe of the document would end the string literal it sits in and what follows
     * would be script. The three values are escaped for that literal instead.
     */
    @Test
    void anAttachmentCannotEndTheScriptCallItIsPassedTo() {
        String html = new HtmlRenderer().render(withMarkup());

        assertTrue(html.contains("onClick=\"downloadData("),
                "an attachment is offered through a script call");
        assertFalse(html.contains("'file'); alert('pwned'); //.pdf'"),
                "the apostrophe of the file name does not reach that call unescaped");
        assertTrue(html.contains("'file\\'); alert(\\'pwned\\'); //.pdf'"),
                "it reaches it escaped, so the file name is still the file name");
    }

    /**
     * A character that directs the reading order is invisible and makes a page read
     * differently from the document it was made from, so neither rendering passes one on.
     */
    @Test
    void aReadingOrderOverrideDoesNotReachThePage() {
        String reversed = "invoice\u202Efdp.txt";
        String html = new HtmlRenderer().render(
                fixture(reversed, HARMLESS_LOCATION, HARMLESS_FILENAME));

        assertFalse(html.contains("\u202E"),
                "the override did not reach the page");
        assertTrue(Html.text(html).contains("invoice fdp.txt"),
                "and what it sat in is still there, a space in its place");
    }

    /**
     * The same document twice, once with hostile content and once with content of the same
     * shape that is only text, gives renderings with the same elements in them. Whatever
     * the invoice wrote landed inside those elements and not between them.
     */
    @Test
    void theStylesheetIsTheOnlyAuthorOfTheMarkup() {
        String hostile = new HtmlRenderer().render(fixture(MARKUP, HARMLESS_LOCATION,
                HARMLESS_FILENAME));
        String harmless = new HtmlRenderer().render(fixture("plain text", HARMLESS_LOCATION,
                HARMLESS_FILENAME));

        assertEquals(Html.count(harmless, "<div"), Html.count(hostile, "<div"),
                "hostile content added no element to the rendering");
        assertEquals(Html.count(harmless, "<a "), Html.count(hostile, "<a "),
                "and no link");
    }

    private static SemanticDocument withMarkup() {
        return fixture(MARKUP, "javascript:alert('pwned')", "file'); alert('pwned'); //.pdf");
    }

    private static SemanticDocument fixture(String text, String location, String filename) {
        return Corpus.example("standard-invoice").toBuilder()
                .set(SemanticPath.of("/BG-1/0/BT-22"), SemanticValue.of(text))
                .set(SemanticPath.of("/BG-24/0/BT-122"), SemanticValue.of("reference"))
                .set(SemanticPath.of("/BG-24/0/BT-123"), SemanticValue.of("description " + text))
                .set(SemanticPath.of("/BG-24/0/BT-124"), SemanticValue.of(location))
                .set(SemanticPath.of("/BG-24/0/BT-125"), new SemanticValue(
                        Base64.getEncoder().encodeToString(
                                "attachment".getBytes(StandardCharsets.UTF_8)),
                        null, null, "application/pdf", filename))
                .build();
    }
}
