package de.bsnsoft.esj.xr;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.trans.XPathException;

/**
 * Transforms a document into the XR representation with the vendored KoSIT stylesheets.
 *
 * <p>The document is parsed and the stylesheets are run through {@link XmlFrontDoor},
 * which is where this project's parser and processor settings live: no document type
 * declaration, no external entity, no XInclude, no extension function, no
 * {@code xsl:evaluate} and no protocol that may be dereferenced.
 *
 * <p>What this class adds is the second half of that closure: the stylesheets are read
 * from the classpath of this module and from nowhere else, because the resolver answers
 * only the file names this module ships. An {@code xsl:include} therefore reaches
 * neither the file system nor the network, and a stylesheet that tried to write an
 * {@code xsl:result-document} is stopped rather than given a file.
 */
final class XrTransformer {

    /** The namespace of the XR representation the stylesheets produce. */
    static final String XR_NAMESPACE = "urn:ce.eu:en16931:2017:xoev-de:kosit:standard:xrechnung-1";

    /** The local name of the root element of the XR representation. */
    static final String XR_ROOT = "invoice";

    /** The directory of the vendored stylesheets, relative to this class. */
    private static final String RESOURCE_DIR = "kosit/";

    /**
     * The stylesheet files this module ships. The resolver answers these names and no
     * others, so the set is the whole of what a stylesheet may include.
     */
    private static final Set<String> STYLESHEETS = Set.of(
            "ubl-invoice-xr.xsl",
            "ubl-creditnote-xr.xsl",
            "cii-xr.xsl",
            "common-xr.xsl",
            "functions.xsl");

    /**
     * The base the resolver gives the stylesheets. It is a URI of a scheme nothing can
     * dereference, so an include resolves against it and reaches the resolver rather than
     * a file or a host.
     */
    private static final String BASE = "esj-kosit:/kosit/";

    private static final Processor PROCESSOR = XmlFrontDoor.processor();

    private static final Map<XrSyntax, XsltExecutable> COMPILED = new ConcurrentHashMap<>();

    private XrTransformer() {
        throw new AssertionError("no instances");
    }

    /**
     * Parses a document into a tree.
     *
     * @param xml the bytes of the document
     * @return the document node
     * @throws XrFormatException if the bytes are not well-formed XML that this importer
     *                           accepts, or carry a document type declaration
     */
    static XdmNode parse(byte[] xml) {
        return XmlFrontDoor.parse(xml);
    }

    /**
     * Transforms a document into the XR representation.
     *
     * @param document the document node of the source document
     * @param syntax   the syntax the document is written in, which picks the stylesheet
     * @return the document node of the XR representation
     * @throws XrFormatException if the transformation failed
     */
    static XdmNode transform(XdmNode document, XrSyntax syntax) {
        try {
            Xslt30Transformer transformer = executable(syntax).load30();
            transformer.setMessageHandler(message -> {
                // A stylesheet's xsl:message is diagnostic output of a third party; it is
                // neither part of the document nor of this module's report.
            });
            transformer.setResultDocumentHandler(uri -> {
                throw new IllegalStateException("this importer runs no xsl:result-document");
            });
            XdmDestination result = new XdmDestination();
            transformer.applyTemplates(document, result);
            return result.getXdmNode();
        } catch (SaxonApiException | IllegalStateException e) {
            throw new XrFormatException("the document could not be transformed into the XR"
                    + " representation", e);
        }
    }

    /**
     * Returns the element that carries the content of a document node, or the node itself
     * if it already is an element.
     *
     * @param node a document or element node
     * @return the root element
     * @throws XrFormatException if the document has no element
     */
    static XdmNode rootElement(XdmNode node) {
        return XmlFrontDoor.rootElement(node);
    }

    /**
     * Recognizes the syntax of a root element.
     *
     * @param root the root element
     * @return the syntax, or an empty optional if no syntax this module reads has that
     *         root element
     */
    static Optional<XrSyntax> detect(XdmNode root) {
        return XmlFrontDoor.detect(root);
    }

    /**
     * Tells whether a root element is the root of an XR representation.
     *
     * @param root the root element
     * @return {@code true} if it is {@code xr:invoice}
     */
    static boolean isXr(XdmNode root) {
        return XR_NAMESPACE.equals(root.getNodeName().getNamespace())
                && XR_ROOT.equals(root.getNodeName().getLocalName());
    }

    private static XsltExecutable executable(XrSyntax syntax) {
        return COMPILED.computeIfAbsent(syntax, XrTransformer::compile);
    }

    private static XsltExecutable compile(XrSyntax syntax) {
        XsltCompiler compiler = PROCESSOR.newXsltCompiler();
        compiler.setResourceResolver(XrTransformer::resolveStylesheet);
        try {
            return compiler.compile(stylesheetSource(syntax.stylesheet()));
        } catch (SaxonApiException e) {
            throw new XrFormatException("the stylesheet " + syntax.stylesheet()
                    + " of this module could not be compiled", e);
        }
    }

    /**
     * Resolves an {@code xsl:include} to a stylesheet this module ships. The name is the
     * last segment of the reference and has to be one of a fixed set; everything else is
     * refused, so no reference can leave the classpath of this module.
     */
    private static Source resolveStylesheet(ResourceRequest request) throws XPathException {
        String reference = request.uri != null ? request.uri : request.relativeUri;
        String name = reference == null ? "" : reference.substring(reference.lastIndexOf('/') + 1);
        if (!STYLESHEETS.contains(name)) {
            throw new XPathException("this importer resolves only the stylesheets it ships,"
                    + " and the reference " + name + " is not one of them");
        }
        return stylesheetSource(name);
    }

    private static Source stylesheetSource(String name) {
        InputStream in = XrTransformer.class.getResourceAsStream(RESOURCE_DIR + name);
        if (in == null) {
            throw new XrFormatException("the stylesheet " + name + " is not on the classpath");
        }
        StreamSource source = new StreamSource(in);
        source.setSystemId(BASE + name);
        return source;
    }
}
