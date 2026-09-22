package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertNotNull(KositHtml.resolveText(URI.create(KositHtml.reference(name)), "UTF-8", null),
                name + " is a file the stylesheet inlines");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "https://example.org/script.js",
            "esj-kosit-html:/kosit/functions.xsl",
            "esj-kosit-html:/kosit/l10n/de.xml"})
    void everyOtherTextIsRefused(String reference) {
        assertThrows(XPathException.class,
                () -> KositHtml.resolveText(URI.create(reference), "UTF-8", null),
                reference + " is not a file this module inlines");
    }

    private static ResourceRequest request(String uri) {
        ResourceRequest request = new ResourceRequest();
        request.uri = uri;
        return request;
    }
}
