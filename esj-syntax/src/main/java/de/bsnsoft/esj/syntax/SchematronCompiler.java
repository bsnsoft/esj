package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XmlFrontDoor;
import de.bsnsoft.esj.xr.XrFormatException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;

/**
 * Compiles an ISO Schematron schema to the XSLT the syntax engine runs.
 *
 * <p>The compilation is the one the compiled artefacts of the bundled packs show the output
 * form of: the XSLT 2 implementation of ISO Schematron, the "skeleton", in its three
 * stages — {@code iso_dsdl_include.xsl} assembles the schema, {@code iso_abstract_expand.xsl}
 * expands abstract patterns, {@code iso_svrl_for_xslt2.xsl} (with
 * {@code iso_schematron_skeleton_for_saxon.xsl}, which it imports) writes the stylesheet that
 * reports in SVRL. The four files are carried as data beside this class under their own
 * licence ({@code schematron/LICENSE}); nothing of a schema is translated by hand.
 *
 * <p>It exists for packs whose publisher releases the Schematron but no compiled form that
 * may be redistributed, which {@code esj packs fetch} then compiles on the machine that
 * will run it. The compilation runs on the processor of {@link XmlFrontDoor}: no extension
 * function, no {@code xsl:evaluate}, no protocol that may be dereferenced, and a resolver
 * that answers the four stylesheets of the skeleton and nothing else. A schema that
 * includes another file is therefore refused rather than assembled: a recipe names every
 * file it fetches and pins its digest, and a file it did not name is one nobody checked.
 *
 * <p>The output is a function of the schema, of the skeleton and of the processor: the same
 * three produce the same bytes, which is what lets a pack record the digest of what it
 * compiled and lets a second fetch recognize the pack it already wrote.
 */
final class SchematronCompiler {

    /** The name of the skeleton, as a manifest records what compiled a rule set. */
    static final String SKELETON = "ISO Schematron XSLT 2 skeleton, 2010 release"
            + " (the copy of ph-schematron-xslt 8.0.6)";

    /** Where the four stylesheets are, as a class path resource directory. */
    private static final String RESOURCES = "de/bsnsoft/esj/syntax/schematron/";

    /** The scheme the skeleton and the schema are loaded under; nothing dereferences it. */
    private static final String SCHEME = "esj-schematron";

    /** The base of the skeleton's own stylesheets under that scheme. */
    private static final String SKELETON_BASE = SCHEME + ":/skeleton/";

    /** The three stages, in the order they run. */
    private static final List<String> STAGES = List.of("iso_dsdl_include.xsl",
            "iso_abstract_expand.xsl", "iso_svrl_for_xslt2.xsl");

    /** Every stylesheet of the skeleton, which is every file the resolver answers. */
    static final List<String> FILES = List.of("iso_dsdl_include.xsl",
            "iso_abstract_expand.xsl", "iso_svrl_for_xslt2.xsl",
            "iso_schematron_skeleton_for_saxon.xsl");

    private static final Map<String, XsltExecutable> STAGE_CACHE = new ConcurrentHashMap<>();

    private SchematronCompiler() {
        throw new AssertionError("no instances");
    }

    /**
     * Compiles one Schematron schema.
     *
     * @param schematron the bytes of the schema
     * @param name       what messages call it, typically its file name
     * @return the bytes of the compiled stylesheet, UTF-8, not indented
     * @throws PackException        if the schema is not well-formed XML this project
     *                              accepts, includes another file, or cannot be compiled
     * @throws NullPointerException if an argument is {@code null}
     */
    static byte[] compile(byte[] schematron, String name) {
        Objects.requireNonNull(schematron, "schematron");
        Objects.requireNonNull(name, "name");
        XdmNode schema = parse(schematron, name);
        XdmNode stage = schema;
        for (String sheet : STAGES) {
            stage = run(sheet, stage, name);
        }
        return serialize(stage, name);
    }

    /**
     * Compiles the result of {@link #compile(byte[], String)} as the syntax engine will, so
     * that a stylesheet it cannot run is refused where it was made rather than on the first
     * invoice it meets.
     *
     * @param xslt the compiled stylesheet
     * @param name what messages call it
     * @throws PackException if the processor refuses it
     */
    static void check(byte[] xslt, String name) {
        XsltCompiler compiler = XmlFrontDoor.processor().newXsltCompiler();
        List<String> errors = new ArrayList<>();
        compiler.setErrorReporter(error -> {
            // The first error reaches the caller in the exception below; the processor
            // would otherwise also write every one of them to the error stream.
            if (!error.isWarning()) {
                errors.add(error.getMessage());
            }
        });
        compiler.setResourceResolver(request -> {
            throw new XPathException("a compiled rule set reads no other file, and " + name
                    + " asked for " + request.uri);
        });
        try {
            compiler.compile(new StreamSource(new ByteArrayInputStream(xslt),
                    SCHEME + ":/compiled/" + name));
        } catch (SaxonApiException e) {
            throw new PackException("the rule set compiled from " + name
                    + " is not a stylesheet the syntax engine can run: "
                    + (errors.isEmpty() ? e.getMessage() : errors.get(0)), e);
        }
    }

    private static XdmNode parse(byte[] schematron, String name) {
        InputSource input = new InputSource(new ByteArrayInputStream(schematron));
        input.setSystemId(SCHEME + ":/source/" + name);
        try {
            return XmlFrontDoor.processor().newDocumentBuilder()
                    .build(new SAXSource(XmlFrontDoor.newXmlReader(), input));
        } catch (SaxonApiException | XrFormatException e) {
            throw new PackException("the Schematron " + name + " is not well-formed XML this"
                    + " project accepts: " + e.getMessage(), e);
        }
    }

    private static XdmNode run(String sheet, XdmNode input, String name) {
        try {
            Xslt30Transformer transformer = stage(sheet).load30();
            transformer.setErrorReporter(error -> {
                // A dynamic error stops the stage and reaches the caller as its exception.
            });
            transformer.setResourceResolver(SchematronCompiler::resolve);
            transformer.setMessageHandler(message -> {
                // The skeleton reports what it notices about a schema with xsl:message. A
                // message that stops it is an error the transformation raises; the others
                // are advice for a person editing the schema and change nothing it writes.
            });
            transformer.setResultDocumentHandler(uri -> {
                throw new IllegalStateException("the Schematron skeleton writes no second"
                        + " document");
            });
            XdmDestination result = new XdmDestination();
            transformer.transform(input.asSource(), result);
            return result.getXdmNode();
        } catch (SaxonApiException | IllegalStateException e) {
            throw new PackException("the Schematron " + name + " could not be compiled ("
                    + sheet + "): " + e.getMessage(), e);
        }
    }

    private static byte[] serialize(XdmNode stylesheet, String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Serializer serializer = XmlFrontDoor.processor().newSerializer(out);
        serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
        serializer.setOutputProperty(Serializer.Property.ENCODING, "UTF-8");
        serializer.setOutputProperty(Serializer.Property.INDENT, "no");
        serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "no");
        try {
            serializer.serializeNode(stylesheet);
        } catch (SaxonApiException e) {
            throw new PackException("the stylesheet compiled from " + name
                    + " could not be written", e);
        }
        return out.toByteArray();
    }

    /** Returns one stage of the skeleton, compiled once per process. */
    private static XsltExecutable stage(String sheet) {
        return STAGE_CACHE.computeIfAbsent(sheet, key -> {
            XsltCompiler compiler = XmlFrontDoor.processor().newXsltCompiler();
            compiler.setErrorReporter(error -> {
                // As in check(): the exception carries what went wrong.
            });
            compiler.setResourceResolver(SchematronCompiler::resolve);
            try {
                return compiler.compile(skeleton(key));
            } catch (SaxonApiException e) {
                throw new PackException("the Schematron skeleton " + key + " of this module"
                        + " could not be compiled", e);
            }
        });
    }

    /**
     * Answers an {@code xsl:import} of the skeleton and a {@code document()} it calls — on
     * itself, for the text of its own messages — and refuses everything else, which is how
     * an {@code sch:include} of a schema is refused.
     */
    private static Source resolve(ResourceRequest request) throws XPathException {
        String uri = request.uri != null ? request.uri : String.valueOf(request.relativeUri);
        if (uri.startsWith(SKELETON_BASE)) {
            String file = uri.substring(SKELETON_BASE.length());
            if (FILES.contains(file)) {
                return skeleton(file);
            }
        }
        throw new XPathException("a Schematron compiled by this module reads no other file,"
                + " and this one asked for " + uri);
    }

    private static Source skeleton(String file) {
        try (InputStream in = SchematronCompiler.class.getClassLoader()
                .getResourceAsStream(RESOURCES + file)) {
            if (in == null) {
                throw new PackException("the Schematron skeleton file " + file
                        + " is not on the class path");
            }
            return new StreamSource(new ByteArrayInputStream(in.readAllBytes()),
                    SKELETON_BASE + file);
        } catch (IOException e) {
            throw new PackException("the Schematron skeleton file " + file
                    + " could not be read", e);
        }
    }
}
