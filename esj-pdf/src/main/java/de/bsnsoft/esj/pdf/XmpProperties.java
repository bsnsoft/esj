package de.bsnsoft.esj.pdf;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * The properties this module reads out of an XMP packet.
 *
 * <p>The packet is XML from a stranger, so it is parsed with the same settings every
 * other parser in this project runs: no document type definition, no external entity, no
 * reference that leaves the packet. A packet that cannot be parsed is a packet with no
 * properties, not an error: the invoice is in the attachments, and a producer that wrote
 * a malformed metadata stream has not made the invoice unreadable.
 *
 * <p>The packet is decoded into characters before the parser sees it, for the reason
 * {@link XmlRoot} gives: a parser handed arbitrary bytes writes its own complaint about
 * them to the error stream of the process, and a library does not get to do that.
 *
 * <p>XMP writes a property either as an element with text or as an attribute of an
 * {@code rdf:Description}, and both forms mean the same thing, so both are read.
 */
final class XmpProperties {

    /** The namespace of the PDF/A identification schema. */
    private static final String PDFA_ID = "http://www.aiim.org/pdfa/ns/id/";

    private static final XmpProperties NONE = new XmpProperties(null, null);

    /** How many elements of a packet are walked before the walk gives up. */
    private static final int MAX_ELEMENTS = 100_000;

    private final FacturXMetadata facturX;
    private final PdfaIdentification pdfa;

    private XmpProperties(FacturXMetadata facturX, PdfaIdentification pdfa) {
        this.facturX = facturX;
        this.pdfa = pdfa;
    }

    /** Returns the properties of a document that has no XMP packet. */
    static XmpProperties none() {
        return NONE;
    }

    /** Reads the properties out of a packet. */
    static XmpProperties parse(byte[] packet) {
        Map<String, String> facturX = new HashMap<>();
        Map<String, String> zugferd = new HashMap<>();
        Map<String, String> pdfa = new HashMap<>();
        try {
            read(packet, facturX, zugferd, pdfa);
        } catch (XMLStreamException | FactoryConfigurationError e) {
            // A packet this module cannot parse is a packet it read no property out of.
            // The invoice is in the attachments and is unaffected.
        }
        Map<String, String> family = facturX.isEmpty() ? zugferd : facturX;
        String namespace = facturX.isEmpty()
                ? FacturXMetadata.ZUGFERD_2_NAMESPACE
                : FacturXMetadata.FACTUR_X_NAMESPACE;
        return new XmpProperties(
                family.isEmpty() ? null : new FacturXMetadata(namespace,
                        value(family, "DocumentType"),
                        value(family, "DocumentFileName"),
                        value(family, "Version"),
                        value(family, "ConformanceLevel")),
                identification(pdfa));
    }

    Optional<FacturXMetadata> facturX() {
        return Optional.ofNullable(facturX);
    }

    Optional<PdfaIdentification> pdfa() {
        return Optional.ofNullable(pdfa);
    }

    private static void read(byte[] packet,
                             Map<String, String> facturX,
                             Map<String, String> zugferd,
                             Map<String, String> pdfa) throws XMLStreamException {
        XMLStreamReader reader = hardened()
                .createXMLStreamReader(new StringReader(XmlRoot.decode(packet)));
        try {
            int elements = 0;
            while (reader.hasNext() && elements < MAX_ELEMENTS) {
                if (reader.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                elements++;
                for (int i = 0; i < reader.getAttributeCount(); i++) {
                    Map<String, String> target = target(reader.getAttributeNamespace(i),
                            facturX, zugferd, pdfa);
                    if (target != null) {
                        target.putIfAbsent(reader.getAttributeLocalName(i),
                                reader.getAttributeValue(i));
                    }
                }
                Map<String, String> target =
                        target(reader.getNamespaceURI(), facturX, zugferd, pdfa);
                if (target != null) {
                    String name = reader.getLocalName();
                    try {
                        target.putIfAbsent(name, reader.getElementText());
                    } catch (XMLStreamException e) {
                        // A property written as a structure rather than as text. The
                        // properties this module reads are text by their schema, so
                        // there is nothing here it wants.
                    }
                }
            }
        } finally {
            reader.close();
        }
    }

    private static Map<String, String> target(String namespace,
                                              Map<String, String> facturX,
                                              Map<String, String> zugferd,
                                              Map<String, String> pdfa) {
        if (FacturXMetadata.FACTUR_X_NAMESPACE.equals(namespace)) {
            return facturX;
        }
        if (FacturXMetadata.ZUGFERD_2_NAMESPACE.equals(namespace)) {
            return zugferd;
        }
        return PDFA_ID.equals(namespace) ? pdfa : null;
    }

    private static Optional<String> value(Map<String, String> properties, String name) {
        String value = properties.get(name);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static PdfaIdentification identification(Map<String, String> pdfa) {
        Optional<String> part = value(pdfa, "part");
        if (part.isEmpty()) {
            return null;
        }
        try {
            return new PdfaIdentification(Integer.parseInt(part.get()),
                    value(pdfa, "conformance"));
        } catch (NumberFormatException e) {
            // A part that is not a number declares nothing this module can report.
            return null;
        }
    }

    /**
     * Returns a parser that refuses document type definitions and external entities.
     * These are bytes from a stranger, so the two settings are required rather than
     * attempted: a parser that did not support one of them would make the packet one this
     * module reads unguarded, and reading no properties is the safer answer.
     */
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
