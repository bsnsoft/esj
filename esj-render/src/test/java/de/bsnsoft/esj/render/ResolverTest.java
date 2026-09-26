package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URI;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.trans.XPathException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the stylesheet may reach, asked of the resolvers directly.
 *
 * <p>That a rendering comes out at all shows that the nine files the stylesheet needs are
 * answered. What it does not show is what happens to a reference to anything else, and that
 * is the half worth testing: the stylesheet runs in a process that has a file system and a
 * network, and it is these two methods that keep it away from both.
 */
class ResolverTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "esj-html.xsl",
            "xrechnung-html.xsl",
            "common-xr.xsl",
            "functions.xsl",
            "l10n/de.xml",
            "l10n/en.xml"})
    void theStylesheetsAndLocalizationsAreAnswered(String name) throws XPathException {
        assertNotNull(KositHtml.resolveResource(request(KositHtml.reference(name))),
                name + " is a file this module ships");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/etc/passwd",
            "file:///etc/passwd",
            "https://example.org/stylesheet.xsl",
            "l10n/fr.xml",
            "xrechnung-viewer.js"})
    void everyOtherDocumentIsRefused(String reference) {
        assertThrows(XPathException.class, () -> KositHtml.resolveResource(request(reference)),
                reference + " is not a document this module answers");
    }

    @Test
    void aReferenceRelativeToTheBaseThatEscapesItIsRefused() {
        assertThrows(XPathException.class,
                () -> KositHtml.resolveResource(request(
                        KositHtml.reference("../../../../etc/passwd"))),
                "the base of this module is not a directory to walk out of");
    }

    @ParameterizedTest
    @ValueSource(strings = {"xrechnung-viewer.css", "xrechnung-viewer.js", "FileSaver-v2.0.5.js"})
    void theInlinedFilesAreAnswered(String name) throws XPathException {
        assertNotNull(
                KositHtml.resolveText(URI.create(KositHtml.reference(name)), "UTF-8", null, false),
                name + " is a file the stylesheet inlines");
    }

    /**
     * {@code unparsed-text()} may be asked to replace a character XML does not permit
     * instead of reporting it, and the resolver answers that request with the same reader
     * as any other. That is right only while the files carry no such character.
     */
    @ParameterizedTest
    @ValueSource(strings = {"xrechnung-viewer.css", "xrechnung-viewer.js", "FileSaver-v2.0.5.js"})
    void theInlinedFilesCarryOnlyCharactersXmlPermits(String name)
            throws XPathException, IOException {
        StringWriter text = new StringWriter();
        try (Reader in = KositHtml.resolveText(
                URI.create(KositHtml.reference(name)), "UTF-8", null, true)) {
            in.transferTo(text);
        }
        text.toString().codePoints().forEach(c -> assertTrue(xmlPermits(c),
                name + " carries U+" + Integer.toHexString(c) + ", which XML does not permit"));
    }

    /** The Char production of XML 1.0, which {@code unparsed-text()} holds its result to. */
    private static boolean xmlPermits(int c) {
        return c == 0x9 || c == 0xA || c == 0xD
                || (c >= 0x20 && c <= 0xD7FF)
                || (c >= 0xE000 && c <= 0xFFFD)
                || (c >= 0x10000 && c <= 0x10FFFF);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "https://example.org/script.js",
            "esj-kosit-html:/kosit/functions.xsl",
            "esj-kosit-html:/kosit/l10n/de.xml"})
    void everyOtherTextIsRefused(String reference) {
        assertThrows(XPathException.class,
                () -> KositHtml.resolveText(URI.create(reference), "UTF-8", null, false),
                reference + " is not a file this module inlines");
    }

    private static ResourceRequest request(String uri) {
        ResourceRequest request = new ResourceRequest();
        request.uri = uri;
        return request;
    }
}
