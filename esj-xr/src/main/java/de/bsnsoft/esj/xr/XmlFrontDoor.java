package de.bsnsoft.esj.xr;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * The one door XML from outside comes through: a parser that fetches nothing and a
 * processor that executes nothing it was not given.
 *
 * <p>Every module of this project that meets bytes written by a stranger meets them
 * here, and there is one configuration rather than one per module. A second copy of
 * these settings is a second place to forget one of them, and the setting that is
 * forgotten is the one that mattered.
 *
 * <h2>What the parser refuses</h2>
 *
 * <ul>
 *   <li>a document type declaration, outright — which takes external entities, parameter
 *       entities and entity expansion with it, and refuses a declaration that declares
 *       nothing just the same, because a parser that read that one could read the
 *       others;</li>
 *   <li>every external entity, external subset, DTD and schema: none is fetched;</li>
 *   <li>XInclude: it is not processed, so an {@code xi:include} stays an element.</li>
 * </ul>
 *
 * <p>Each of these is required of the parser rather than attempted, so that a parser
 * implementation that did not support one of them is no parser this project uses.
 *
 * <h2>What the processor refuses</h2>
 *
 * <p>The Saxon processor of {@link #processor()} runs without extension functions,
 * without DTD validation, without XInclude, without {@code xsl:evaluate} and with no
 * protocol it may dereference. A stylesheet it runs therefore reads no file and reaches
 * no host, and what a stylesheet may include is whatever the resource resolver of the
 * compiler answers with and nothing else — every caller sets one.
 *
 * <p>The processor is shared. Saxon's own configuration is safe to use from several
 * threads, and the compiled stylesheets of a module are cached against it, so a
 * long-lived process compiles each of them once.
 */
public final class XmlFrontDoor {

    /**
     * The parser property that bounds how deeply elements may nest.
     *
     * <p>Its default differs between Java releases, so it is set here rather than
     * inherited: the bounds of this project are the same on every runtime it runs on, and
     * a document refused by one runtime and read by the next is not one of them.
     */
    private static final String MAX_ELEMENT_DEPTH = "jdk.xml.maxElementDepth";

    /**
     * What {@link #MAX_ELEMENT_DEPTH} is set to: nothing the parser enforces.
     *
     * <p>Nesting is bounded where this project can say what the bound means — the
     * importer walks a bounded number of elements deep and reports reaching that as a
     * limit, which the command line answers with the exit code for a limit rather than
     * with the one for a document it could not read. A parser that refused first would
     * turn that answer into the other one.
     */
    private static final String NO_PARSER_DEPTH_LIMIT = "0";

    private static final Processor PROCESSOR = newProcessor();

    private XmlFrontDoor() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the shared Saxon processor, configured as described above.
     *
     * @return the processor
     */
    public static Processor processor() {
        return PROCESSOR;
    }

    /**
     * Returns a new parser with the settings described above.
     *
     * <p>A reader is not safe to share between threads and carries the error handler of
     * one caller, so each caller asks for its own. A caller that wants the parser's
     * complaints rather than an exception installs an
     * {@link org.xml.sax.ErrorHandler} on the result before parsing with it.
     *
     * @return a parser that reads a document and fetches nothing
     * @throws XrFormatException if no parser with these settings is available
     */
    public static XMLReader newXmlReader() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            SAXParser parser = factory.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            parser.setProperty(MAX_ELEMENT_DEPTH, NO_PARSER_DEPTH_LIMIT);
            XMLReader reader = parser.getXMLReader();
            reader.setEntityResolver((publicId, systemId) ->
                    new InputSource(InputStream.nullInputStream()));
            return reader;
        } catch (ParserConfigurationException | SAXException e) {
            throw new XrFormatException("no XML parser with the settings this project"
                    + " requires is available", e);
        }
    }

    /**
     * Parses a document into a tree with a parser of {@link #newXmlReader()}.
     *
     * @param xml the bytes of the document
     * @return the document node
     * @throws XrFormatException    if the bytes are not well-formed XML this project
     *                              accepts, or carry a document type declaration
     * @throws NullPointerException if {@code xml} is {@code null}
     */
    public static XdmNode parse(byte[] xml) {
        return parse(xml, newXmlReader());
    }

    /**
     * Parses a document into a tree with a parser the caller configured.
     *
     * <p>The parser is one of {@link #newXmlReader()} with, at most, an error handler of
     * the caller's on it: a caller that collects the parser's complaints as findings
     * needs the line and column they carry, and an exception does not carry those.
     *
     * @param xml    the bytes of the document
     * @param reader the parser to read them with
     * @return the document node
     * @throws XrFormatException    if the bytes are not well-formed XML that parser
     *                              accepts
     * @throws NullPointerException if an argument is {@code null}
     */
    public static XdmNode parse(byte[] xml, XMLReader reader) {
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(reader, "reader");
        try {
            InputSource input = new InputSource(new ByteArrayInputStream(xml));
            return PROCESSOR.newDocumentBuilder().build(new SAXSource(reader, input));
        } catch (SaxonApiException e) {
            throw new XrFormatException("the document is not well-formed XML that this"
                    + " project accepts", e);
        }
    }

    /**
     * Returns the element that carries the content of a document node, or the node itself
     * if it already is an element.
     *
     * @param node a document or element node
     * @return the root element
     * @throws XrFormatException    if the document has no element
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public static XdmNode rootElement(XdmNode node) {
        Objects.requireNonNull(node, "node");
        if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
            return node;
        }
        for (XdmNode child : node.children()) {
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                return child;
            }
        }
        throw new XrFormatException("the document has no root element");
    }

    /**
     * Recognizes the syntax of a root element.
     *
     * @param root the root element
     * @return the syntax, or an empty optional if no syntax this project reads has that
     *         root element
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public static Optional<XrSyntax> detect(XdmNode root) {
        Objects.requireNonNull(root, "root");
        String namespace = root.getNodeName().getNamespace();
        String localName = root.getNodeName().getLocalName();
        for (XrSyntax syntax : XrSyntax.values()) {
            if (syntax.matches(namespace, localName)) {
                return Optional.of(syntax);
            }
        }
        return Optional.empty();
    }

    private static Processor newProcessor() {
        Processor processor = new Processor(false);
        processor.getUnderlyingConfiguration().setErrorReporterFactory(configuration -> error -> {
            // What went wrong reaches the caller in the exception the calling module
            // throws. Saxon would otherwise also write it to the error stream of the
            // process, and a library does not get to decide that a document's content is
            // logged.
        });
        processor.setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
        processor.setConfigurationProperty(Feature.TRACE_EXTERNAL_FUNCTIONS, false);
        processor.setConfigurationProperty(Feature.DTD_VALIDATION, false);
        processor.setConfigurationProperty(Feature.DTD_VALIDATION_RECOVERABLE, false);
        processor.setConfigurationProperty(Feature.XINCLUDE, false);
        // The value is the list of URI schemes that may be dereferenced, and "none" names
        // one that no URL handler serves: file, http, https, jar, classpath and data are
        // refused, by Saxon 13.0 as by 12.10. It is not the empty string, which 13.0 reads
        // as "allow nothing" but 12.10 read as "allow everything"; nor "#none", which 12.10
        // read as "nothing" and 13.0 reads as a scheme like any other.
        processor.setConfigurationProperty(Feature.ALLOWED_PROTOCOLS, "none");
        processor.setConfigurationProperty(Feature.DISABLE_XSL_EVALUATE, true);
        return processor;
    }
}
