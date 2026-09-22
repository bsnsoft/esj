package de.bsnsoft.esj.pdf;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * The Factur-X part of the XMP packet of a hybrid invoice.
 *
 * <p>A hybrid PDF says in its metadata that it carries an invoice, what the attachment is
 * called, which version of the container specification was followed and which profile the
 * invoice is written in. Those four properties are in no predefined XMP schema, so PDF/A
 * asks the file to describe the schema they are in before it uses it: the packet carries a
 * {@code pdfaExtension:schemas} bag that declares the namespace, its prefix and each
 * property with its type and category, and then an {@code rdf:Description} that carries
 * the four values.
 *
 * <p>Both blocks are written as fixed text and inserted before the end of the RDF, for the
 * reason the renderer's packet is written as text: a producer of this project is compared
 * byte for byte against a checked-in file, and a serializer would make the bytes depend on
 * whichever XML writer the runtime supplies. Nothing is escaped on the way in because
 * nothing variable goes in: the namespace, the prefix, the version and the attachment name
 * are the literals of one {@link HybridFlavour} and the conformance level is one of the
 * literals of {@link FacturXProfile}.
 *
 * <p>The insertion is textual and its preconditions are checked rather than assumed. A
 * packet that already carries the extension schema of another producer, one that already
 * declares this family of properties, one that is not UTF-8 and one whose RDF this class
 * cannot find the end of are all refused: merging into them would either lose what was
 * there or write a second value for a property that has one.
 */
final class FacturXXmp {

    /** What the property {@code DocumentType} carries in a hybrid invoice. */
    private static final String DOCUMENT_TYPE = "INVOICE";

    /** The end tag this class inserts in front of. */
    private static final String END_OF_RDF = "</rdf:RDF>";

    /** The namespace of the PDF/A extension schema container. */
    private static final String PDFA_EXTENSION = "http://www.aiim.org/pdfa/ns/extension/";

    /** The namespace of one declared schema inside that container. */
    private static final String PDFA_SCHEMA = "http://www.aiim.org/pdfa/ns/schema#";

    /** The namespace of one declared property of such a schema. */
    private static final String PDFA_PROPERTY = "http://www.aiim.org/pdfa/ns/property#";

    private FacturXXmp() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the packet with the Factur-X extension schema and its four properties
     * merged into it.
     *
     * @param packet  the XMP packet of the input file
     * @param flavour the container specification the file declares itself under
     * @param profile the profile the invoice is written in
     * @return the packet to write back
     * @throws EmbedRefusedException if this packet is not one the schema can be merged
     *                               into
     */
    static byte[] merged(byte[] packet, HybridFlavour flavour, FacturXProfile profile) {
        String text = utf8(packet);
        if (text.contains(FacturXMetadata.FACTUR_X_NAMESPACE)
                || text.contains(FacturXMetadata.ZUGFERD_2_NAMESPACE)) {
            throw new EmbedRefusedException("the XMP packet of this file already declares"
                    + " the properties of a hybrid invoice, so the file already claims to"
                    + " carry one and this module does not write a second claim over it");
        }
        if (text.contains(PDFA_EXTENSION)) {
            throw new EmbedRefusedException("the XMP packet of this file already carries a"
                    + " PDF/A extension schema declaration, and this module writes its own"
                    + " rather than merging into one it did not write");
        }
        int end = text.lastIndexOf(END_OF_RDF);
        if (end < 0) {
            throw new EmbedRefusedException("the XMP packet of this file does not end an"
                    + " RDF description with " + END_OF_RDF + ", so this module found no"
                    + " place to write the properties of a hybrid invoice into");
        }
        String merged = text.substring(0, end) + block(flavour, profile)
                + text.substring(end);
        return merged.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Returns the text that is inserted: the declaration of the schema, and the values.
     */
    private static String block(HybridFlavour flavour, FacturXProfile profile) {
        String prefix = flavour.prefix();
        return "  <rdf:Description rdf:about=\"\"\n"
                + "      xmlns:pdfaExtension=\"" + PDFA_EXTENSION + "\"\n"
                + "      xmlns:pdfaSchema=\"" + PDFA_SCHEMA + "\"\n"
                + "      xmlns:pdfaProperty=\"" + PDFA_PROPERTY + "\">\n"
                + "   <pdfaExtension:schemas>\n"
                + "    <rdf:Bag>\n"
                + "     <rdf:li rdf:parseType=\"Resource\">\n"
                + "      <pdfaSchema:schema>" + flavour.schemaDescription()
                + "</pdfaSchema:schema>\n"
                + "      <pdfaSchema:namespaceURI>" + flavour.namespace()
                + "</pdfaSchema:namespaceURI>\n"
                + "      <pdfaSchema:prefix>" + prefix + "</pdfaSchema:prefix>\n"
                + "      <pdfaSchema:property>\n"
                + "       <rdf:Seq>\n"
                + property("DocumentFileName", "name of the embedded XML invoice file")
                + property("DocumentType", "what kind of document the embedded file is")
                + property("Version", "the version of the XMP schema of this container")
                + property("ConformanceLevel", "the conformance level of the invoice")
                + "       </rdf:Seq>\n"
                + "      </pdfaSchema:property>\n"
                + "     </rdf:li>\n"
                + "    </rdf:Bag>\n"
                + "   </pdfaExtension:schemas>\n"
                + "  </rdf:Description>\n"
                + "  <rdf:Description rdf:about=\"\"\n"
                + "      xmlns:" + prefix + "=\"" + flavour.namespace() + "\">\n"
                + "   <" + prefix + ":DocumentType>" + DOCUMENT_TYPE
                + "</" + prefix + ":DocumentType>\n"
                + "   <" + prefix + ":DocumentFileName>" + flavour.attachmentName()
                + "</" + prefix + ":DocumentFileName>\n"
                + "   <" + prefix + ":Version>" + flavour.version()
                + "</" + prefix + ":Version>\n"
                + "   <" + prefix + ":ConformanceLevel>" + profile.conformanceLevel()
                + "</" + prefix + ":ConformanceLevel>\n"
                + "  </rdf:Description>\n";
    }

    /**
     * Returns one declared property of the schema. All four are external text: external
     * because they describe a file that travels with the document rather than the
     * document's own content, which is the category PDF/A defines for exactly that.
     */
    private static String property(String name, String description) {
        return "        <rdf:li rdf:parseType=\"Resource\">\n"
                + "         <pdfaProperty:name>" + name + "</pdfaProperty:name>\n"
                + "         <pdfaProperty:valueType>Text</pdfaProperty:valueType>\n"
                + "         <pdfaProperty:category>external</pdfaProperty:category>\n"
                + "         <pdfaProperty:description>" + description
                + "</pdfaProperty:description>\n"
                + "        </rdf:li>\n";
    }

    /**
     * Returns the packet as text, and refuses it where it is not UTF-8. PDF/A requires the
     * packet to be UTF-8, and a packet this class cannot read is one it must not rewrite.
     */
    private static String utf8(byte[] packet) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(packet))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new EmbedRefusedException("the XMP packet of this file is not written in"
                    + " UTF-8, which PDF/A requires, so this module did not rewrite it");
        }
    }
}
