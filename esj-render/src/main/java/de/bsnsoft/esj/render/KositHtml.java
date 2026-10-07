package de.bsnsoft.esj.render;

import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.xr.internal.XmlFrontDoor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.trans.XPathException;

/**
 * Runs the HTML stylesheet of this module over an XR document.
 *
 * <p>The entry point is {@code xsl/esj-html.xsl}, which is this project's: it imports the
 * vendored {@code xrechnung-html.xsl} of the KoSIT XRechnung visualization and overrides
 * the one template rule of it that writes a value of the document into a link target and
 * into the arguments of a script call. The vendored files keep upstream's bytes; import
 * precedence is what puts the safer rule in front.
 *
 * <p>The stylesheet does not live off its own file alone. It imports two further
 * stylesheets, it reads its labels out of a localization file with {@code doc()}, and it
 * inlines a style sheet and two scripts with {@code unparsed-text()}. Each of those is a
 * reference that a run-of-the-mill XSLT engine would resolve against a file system, and
 * this class is the reason none of them does: the compiler and the transformer are given
 * resolvers that answer a fixed list of names out of the classpath of this module and
 * refuse everything else.
 *
 * <p>The processor underneath is the one of
 * {@code de.bsnsoft.esj.xr.internal.XmlFrontDoor}, which is where this project's
 * settings live: no extension function, no DTD, no XInclude, no {@code xsl:evaluate} and
 * no protocol it may dereference. The resolvers of this class are set on the compiler and
 * on the transformer rather than on that shared processor, so nothing here widens what the
 * importer of {@code esj-xr} may reach.
 */
final class KositHtml {

    /**
     * The base the resolvers give the stylesheet. Its scheme is one nothing can
     * dereference and its path is absolute, so a relative reference inside a stylesheet
     * resolves against it in the ordinary way and lands back at the resolvers.
     */
    private static final String BASE = "esj-kosit-html:/kosit/";

    /**
     * The entry point of the transformation: the stylesheet of this project, which
     * imports the vendored one and overrides one of its template rules.
     */
    private static final String ENTRY_POINT = "esj-html.xsl";

    /**
     * The stylesheet files this module ships, and where each of them lies on the
     * classpath; an {@code xsl:import} reaches these only. All of them are named below
     * the same base, so a relative reference from one to another resolves without any of
     * them knowing where the other is kept.
     */
    private static final Map<String, String> STYLESHEETS = stylesheets();

    /** The files the stylesheet inlines with {@code unparsed-text()}. */
    private static final Set<String> TEXTS = Set.of(
            "xrechnung-viewer.css",
            "xrechnung-viewer.js",
            "FileSaver-v2.0.5.js");

    /** The files the stylesheet reads with {@code doc()}: one localization per language. */
    private static final Set<String> DOCUMENTS = Set.of(
            "l10n/de.xml",
            "l10n/en.xml");

    /** The name of the stylesheet parameter that picks the localization. */
    private static final QName LANG = new QName("lang");

    private KositHtml() {
        throw new AssertionError("no instances");
    }

    private static Map<String, String> stylesheets() {
        Map<String, String> sheets = new LinkedHashMap<>();
        sheets.put(ENTRY_POINT, "xsl/" + ENTRY_POINT);
        sheets.put("xrechnung-html.xsl", "kosit/xrechnung-html.xsl");
        sheets.put("common-xr.xsl", "kosit/common-xr.xsl");
        sheets.put("functions.xsl", "kosit/functions.xsl");
        return Map.copyOf(sheets);
    }

    /**
     * Transforms an XR document into the HTML of the visualization, and stops where the
     * HTML reaches a bound.
     *
     * <p>The bound is measured on what the serializer writes, in bytes of UTF-8, while it
     * writes it: the page is held in memory until it is finished, and a page that is
     * stopped where it reached the bound has cost no more than the bound.
     *
     * @param xr       the document node of the XR document
     * @param language the language the labels are written in
     * @param maxBytes how many bytes the HTML may have
     * @return the HTML, as the stylesheet's own serialization settings write it
     * @throws EsjLimitException if the HTML reached the bound
     * @throws RenderException   if the stylesheet raised an error
     */
    static String transform(XdmNode xr, RenderLanguage language, long maxBytes) {
        Bounded html = new Bounded(maxBytes);
        try {
            return transform(xr, language, html);
        } catch (RuntimeException e) {
            if (html.reached()) {
                throw new EsjLimitException("the HTML rendering reached the bound of "
                        + maxBytes + " bytes this run was given, so it was not written",
                        new EsjLimitException.Bound("maxHtmlBytes", maxBytes, "bytes"));
            }
            throw e;
        }
    }

    private static String transform(XdmNode xr, RenderLanguage language, Bounded html) {
        try {
            Xslt30Transformer transformer = Holder.EXECUTABLE.load30();
            transformer.setResourceResolver(KositHtml::resolveResource);
            transformer.setUnparsedTextResolver(KositHtml::resolveText);
            transformer.setMessageHandler(message -> {
                // A stylesheet's xsl:message is diagnostic output of a third party. It is
                // neither part of the rendering nor something a library gets to log.
            });
            transformer.setResultDocumentHandler(uri -> {
                throw new IllegalStateException("this renderer runs no xsl:result-document");
            });
            transformer.setStylesheetParameters(
                    Map.<QName, XdmValue>of(LANG, new XdmAtomicValue(language.code())));
            Serializer destination = processor().newSerializer(html);
            transformer.applyTemplates(xr, destination);
            return html.toString();
        } catch (SaxonApiException e) {
            // The stylesheet reads a typed value as its type, so what stops it is all but
            // always a value of the document whose content is not of that shape. The
            // message of the engine names it, and this exception says whose fault it is.
            throw new RenderContentException(
                    "the document could not be rendered as HTML", e);
        } catch (IllegalStateException e) {
            throw new RenderEngineException("the document could not be rendered as HTML", e);
        }
    }

    private static Processor processor() {
        return XmlFrontDoor.processor();
    }

    /**
     * Where the serializer writes the page: a buffer that counts what it is given in bytes
     * of UTF-8 and refuses to take more than its bound.
     *
     * <p>A character of the basic plane counts as the one, two or three bytes UTF-8 writes
     * it in, and each half of a surrogate pair as two, so a pair is the four bytes it is
     * written as. The refusal is an {@link IOException}, which the serializer reports as a
     * failure of its destination, and the buffer remembers it so that the caller can tell
     * that failure from an error of the stylesheet.
     */
    private static final class Bounded extends Writer {

        private final StringBuilder text = new StringBuilder();
        private final long bound;
        private long bytes;
        private boolean reached;

        private Bounded(long bound) {
            this.bound = bound;
        }

        @Override
        public void write(char[] buffer, int offset, int length) throws IOException {
            long more = 0;
            for (int i = offset; i < offset + length; i++) {
                more += weight(buffer[i]);
            }
            take(more);
            text.append(buffer, offset, length);
        }

        @Override
        public void write(String string, int offset, int length) throws IOException {
            long more = 0;
            for (int i = offset; i < offset + length; i++) {
                more += weight(string.charAt(i));
            }
            take(more);
            text.append(string, offset, offset + length);
        }

        @Override
        public void write(int c) throws IOException {
            take(weight((char) c));
            text.append((char) c);
        }

        /** Returns how many bytes of UTF-8 a character of the page is, a half pair two. */
        private static int weight(char c) {
            return c < 0x80 ? 1 : c < 0x800 || Character.isSurrogate(c) ? 2 : 3;
        }

        /** Counts bytes that are about to be taken, or refuses them past the bound. */
        private void take(long more) throws IOException {
            if (reached || bytes + more > bound) {
                reached = true;
                throw new IOException("the HTML rendering reached the bound of " + bound
                        + " bytes");
            }
            bytes += more;
        }

        @Override
        public void flush() {
            // Nothing is written anywhere until the page is finished.
        }

        @Override
        public void close() {
            // The text stays readable after the serializer is done with it.
        }

        /** Tells whether the page ran into the bound. */
        boolean reached() {
            return reached;
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }

    /**
     * Answers an {@code xsl:import} at compile time and a {@code doc()} at run time. The
     * name is what is left of the reference after the base of this module, and it has to
     * be one of the files this module ships.
     *
     * <p>Package-private rather than private so that a test can ask it what it refuses;
     * a resolver that answered more than it should would otherwise be a thing this module
     * argues about rather than a thing it demonstrates.
     */
    static Source resolveResource(ResourceRequest request) throws XPathException {
        String name = nameOf(request);
        if (!STYLESHEETS.containsKey(name) && !DOCUMENTS.contains(name)) {
            throw new XPathException("this renderer resolves only the files it ships, and "
                    + describe(request) + " is not one of them");
        }
        return source(name);
    }

    /**
     * Answers an {@code unparsed-text()}. The three files the stylesheet inlines are the
     * whole of what it may read this way. Package-private for the same reason as
     * {@link #resolveResource(ResourceRequest)}.
     *
     * <p>{@code fallback} asks for a character XML does not permit to be replaced by
     * U+FFFD rather than reported. The three files carry no such character, which
     * {@code ResolverTest} holds them to, so the reader is the same either way.
     */
    static Reader resolveText(URI uri, String encoding, Configuration configuration,
            boolean fallback) throws XPathException {
        String name = relative(uri == null ? null : uri.toString());
        if (name == null || !TEXTS.contains(name)) {
            throw new XPathException("this renderer inlines only the files it ships, and "
                    + uri + " is not one of them");
        }
        return new InputStreamReader(stream(name), StandardCharsets.UTF_8);
    }

    private static String nameOf(ResourceRequest request) {
        String absolute = relative(request.uri);
        if (absolute != null) {
            return absolute;
        }
        String relative = request.relativeUri;
        return relative == null ? "" : relative;
    }

    private static String describe(ResourceRequest request) {
        return request.uri != null ? request.uri : String.valueOf(request.relativeUri);
    }

    /** Turns a reference into the URI the resolvers know a shipped file by. */
    static String reference(String name) {
        return BASE + name;
    }

    /** Returns the part of a reference below the base of this module, or {@code null}. */
    private static String relative(String reference) {
        return reference != null && reference.startsWith(BASE)
                ? reference.substring(BASE.length())
                : null;
    }

    private static Source source(String name) {
        StreamSource source = new StreamSource(stream(name));
        source.setSystemId(BASE + name);
        return source;
    }

    private static InputStream stream(String name) {
        InputStream in = KositHtml.class.getResourceAsStream(
                STYLESHEETS.getOrDefault(name, "kosit/" + name));
        if (in == null) {
            throw new RenderEngineException("the vendored file " + name + " is not on the classpath");
        }
        return in;
    }

    /** Compiles the stylesheet once, on first use, and holds it for every later rendering. */
    private static final class Holder {

        private static final XsltExecutable EXECUTABLE = compile();

        private Holder() {
            throw new AssertionError("no instances");
        }

        private static XsltExecutable compile() {
            XsltCompiler compiler = processor().newXsltCompiler();
            compiler.setResourceResolver(KositHtml::resolveResource);
            try {
                return compiler.compile(source(ENTRY_POINT));
            } catch (SaxonApiException e) {
                throw new RenderEngineException("the stylesheet " + ENTRY_POINT
                        + " of this module could not be compiled", e);
            }
        }
    }
}
