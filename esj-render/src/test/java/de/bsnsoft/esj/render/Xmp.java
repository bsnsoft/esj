package de.bsnsoft.esj.render;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads the XMP packet of a rendering back out of the file.
 *
 * <p>The packet is written as a string by {@link Pdfa}, so a test that only searched the
 * string it wrote would be asking that class to agree with itself. What is read here is the
 * stream of the finished PDF, as a reader of the file finds it, and it is parsed as XML
 * where a test asks about a property rather than about the text.
 */
final class Xmp {

    private Xmp() {
        throw new AssertionError("no instances");
    }

    /** Returns the XMP packet of a rendering as the text of its stream. */
    static String of(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDMetadata metadata = document.getDocumentCatalog().getMetadata();
            assertNotNull(metadata, "the rendering carries an XMP packet");
            try (InputStream in = metadata.createInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the text of the first element of a name in the packet of a rendering. */
    static String property(byte[] pdf, String namespace, String name) {
        Document parsed = parsed(of(pdf));
        NodeList elements = parsed.getElementsByTagNameNS(namespace, name);
        return elements.getLength() == 0 ? null : elements.item(0).getTextContent().strip();
    }

    /** Parses a packet as XML, which is what a reader of the file will do with it. */
    private static Document parsed(String packet) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body(packet)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException("the XMP packet is not well-formed XML: " + packet, e);
        }
    }

    /**
     * Returns the packet without its processing instructions, which carry a byte order mark
     * inside an attribute value and are the packet's wrapper rather than its content.
     */
    private static byte[] body(String packet) {
        int start = packet.indexOf("<x:xmpmeta");
        int end = packet.lastIndexOf("</x:xmpmeta>");
        if (start < 0 || end < 0) {
            throw new IllegalStateException("the packet has no xmpmeta element: " + packet);
        }
        return packet.substring(start, end + "</x:xmpmeta>".length())
                .getBytes(StandardCharsets.UTF_8);
    }
}
