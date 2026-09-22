package de.bsnsoft.esj.pdf;

import de.bsnsoft.esj.xr.XmlBytes;
import de.bsnsoft.esj.xr.XmlEncodingReport;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Optional;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads the root element out of the first bytes of an attachment, so that an attachment
 * can be classified without being decoded whole.
 *
 * <p>The bytes are decoded first and the parser is then given characters. The charset is
 * the one {@link XmlBytes} reads out of the byte order mark and the XML declaration, and
 * anything that does not decode in it becomes a replacement character. That is the right
 * way round here for two reasons: an attachment is arbitrary bytes, and a parser handed
 * arbitrary bytes writes its own complaint about them to the error stream of the process
 * before any caller can catch it, which a library does not get to do. A window that does
 * not spell an element is simply one this class returns nothing for.
 *
 * <p>The parser is closed off as everywhere else in this project: no document type
 * definition, no external entity, no reference that leaves the window.
 *
 * <p>Parsing stops at the first start element, which is the root, so the rest of the
 * attachment is never read here. That the window ends in the middle of the document is
 * therefore expected and is not a failure.
 */
final class XmlRoot {

    /**
     * The name of an element, with the namespace it is in.
     *
     * @param namespace the namespace URI, empty where the element is in none
     * @param localName the local name
     */
    record Name(String namespace, String localName) {
    }

    private XmlRoot() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the name of the root element of a window of bytes.
     *
     * @param head the first bytes of the document
     * @return the name, or an empty optional where the window carries no element this
     *         parser could reach
     */
    static Optional<Name> of(byte[] head) {
        XMLStreamReader reader;
        try {
            reader = hardened().createXMLStreamReader(new StringReader(decode(head)));
        } catch (XMLStreamException | FactoryConfigurationError e) {
            return Optional.empty();
        }
        try {
            while (reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
                if (!reader.hasNext()) {
                    return Optional.empty();
                }
                // The prolog: a declaration, comments, processing instructions.
                reader.next();
            }
            String namespace = reader.getNamespaceURI();
            return Optional.of(new Name(namespace == null ? "" : namespace,
                    reader.getLocalName()));
        } catch (XMLStreamException | RuntimeException e) {
            return Optional.empty();
        } finally {
            close(reader);
        }
    }

    /**
     * Tells whether a window begins an XML document at all, which is what separates an
     * attachment whose root element was merely out of reach of the window from one that
     * is not XML.
     *
     * @param head the first bytes of the document
     * @return {@code true} if the first byte that is not a byte order mark or whitespace
     *         begins a tag, in any of the encodings XML allows
     */
    static boolean looksLikeXml(byte[] head) {
        for (int i = 0; i < head.length && i < 64; i++) {
            int b = head[i] & 0xFF;
            if (b == 0x00 || b == 0xEF || b == 0xBB || b == 0xBF || b == 0xFE || b == 0xFF
                    || b == ' ' || b == '\t' || b == '\r' || b == '\n') {
                continue;
            }
            return b == '<';
        }
        return false;
    }

    /**
     * Decodes a window of bytes into characters, in the charset the document names, and
     * without a leading byte order mark, which is a fact about the bytes and not a
     * character of the document.
     *
     * <p>A byte the charset cannot spell becomes a replacement character rather than a
     * failure: the question this class answers is what the root element is called, and a
     * window that is not text at all answers it with nothing either way.
     */
    static String decode(byte[] bytes) {
        XmlEncodingReport report = XmlBytes.inspect(bytes);
        Charset charset;
        try {
            charset = Charset.forName(report.assumed());
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            charset = StandardCharsets.UTF_8;
        }
        String text;
        try {
            text = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            // A decoder that replaces reports nothing; this is unreachable and is here
            // because the signature declares it.
            return "";
        }
        return text.isEmpty() || text.charAt(0) != '\uFEFF' ? text : text.substring(1);
    }

    private static void close(XMLStreamReader reader) {
        try {
            reader.close();
        } catch (XMLStreamException e) {
            // The reader was reading a byte array; there is nothing to release that a
            // caller could do anything about.
        }
    }

    private static XMLInputFactory hardened() throws XMLStreamException {
        try {
            XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
            return factory;
        } catch (FactoryConfigurationError | IllegalArgumentException e) {
            throw new XMLStreamException("no XML parser that refuses document type"
                    + " definitions and external entities is available", e);
        }
    }
}
