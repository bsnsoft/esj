package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XmlFrontDoor;
import de.bsnsoft.esj.xr.XrSyntax;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * The second block: the document against the XML Schema modules of its syntax.
 *
 * <p>The schema is compiled from the pack, once per process and per component, and
 * shared from there: compiling the UBL 2.1 library is the most expensive thing this
 * module does, and it does not depend on the document. What it is shared under is where
 * the file came from and what is in it — {@link Pack#cacheKey(String)} — and never the
 * identity a pack declares about itself, so no supplied pack can answer for a packaged
 * one. The compiled schema is safe to use from several threads; a validator built from it
 * is not, so each document gets its own.
 *
 * <p>The message of a schema finding is the platform validator's own sentence, and the
 * identifier of the validity constraint that was broken is recovered from the front of
 * it. That identifier is what a report is compared on, so the pattern that reads it
 * allows for the spacing the localized forms of the message use: a French message writes
 * a space before the colon, and a finding whose code fell back to {@link #INVALID} on one
 * machine and not on another would make two reports of the same document differ.
 *
 * <p>The sentence itself is asked for in English, and the platform's validator accepts
 * the property for it and ignores it — the messages follow the default locale of the
 * virtual machine. The property is set anyway, for a processor that honours it, and the
 * application decides the rest: the {@code esj} command line fixes the locale of its
 * process, so its reports read the same everywhere.
 *
 * <p>The factory and the validator resolve schema references through {@link PackResolver}
 * and have external access to DTDs and schemas turned off, so nothing outside the pack is
 * read — not a reference inside a schema, and not an {@code xsi:schemaLocation} a
 * document names. The document itself is read with the parser of {@link XmlFrontDoor},
 * which is the same door it came through.
 */
final class XsdCheck {

    /** The code of a schema finding whose message names no constraint of the standard. */
    static final String INVALID = "XSD-INVALID";

    /**
     * The identifier the platform's schema validator puts in front of its messages: the
     * name of the validity constraint of the XML Schema recommendation that was broken.
     */
    private static final Pattern CONSTRAINT =
            Pattern.compile("^([a-z0-9]+-[A-Za-z0-9._-]+)\\s*:\\s");

    /**
     * The property the schema validator of the platform takes the language of its
     * messages from. It is an implementation property rather than one the standard
     * defines, so it is set where it is understood and left alone where it is not.
     */
    private static final String LOCALE = "http://apache.org/xml/properties/locale";

    private static final Map<String, Schema> COMPILED = new ConcurrentHashMap<>();

    private XsdCheck() {
        throw new AssertionError("no instances");
    }

    /**
     * The outcome of one component.
     *
     * @param findings    what the schema said, each of them fatal
     * @param duration    how long the validation took
     * @param compilation how long compiling the schema took, zero where it was already
     *                    compiled in this process
     */
    record Result(List<SyntaxFinding> findings, Duration duration, Duration compilation) {
    }

    /**
     * Validates a document against one schema component of a pack.
     *
     * @param xml       the bytes of the document
     * @param pack      the pack the component belongs to
     * @param component the component
     * @param entry     the schema of the document type, as a path inside the pack
     * @param syntax    the syntax of the document, which names the category of a finding
     * @param budget    the time the whole validation was given
     * @return the outcome
     * @throws SyntaxLimitException if the validation was still running at the deadline
     */
    static Result run(byte[] xml, Pack pack, PackComponent component, String entry,
                      XrSyntax syntax, Budget budget) {
        long compileStart = System.nanoTime();
        boolean[] compiled = {false};
        Schema schema = COMPILED.computeIfAbsent(pack.cacheKey(entry), key -> {
            compiled[0] = true;
            return budget.call("compiling the schema " + entry,
                    () -> compile(pack, component, entry));
        });
        Duration compilation = compiled[0]
                ? Duration.ofNanos(System.nanoTime() - compileStart) : Duration.ZERO;

        long start = System.nanoTime();
        List<SyntaxFinding> findings = new ArrayList<>();
        budget.call("the schema " + entry, () -> {
            validate(xml, schema, pack, component, syntax, findings);
            return null;
        });
        return new Result(List.copyOf(findings), Duration.ofNanos(System.nanoTime() - start),
                compilation);
    }

    private static Schema compile(Pack pack, PackComponent component, String entry) {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException e) {
            throw new PackException("no schema factory with the settings this module"
                    + " requires is available", e);
        }
        english(factory::setProperty);
        factory.setResourceResolver(new PackResolver(pack, component.files()));
        factory.setErrorHandler(new Refusing());
        StreamSource source = new StreamSource(new ByteArrayInputStream(pack.read(entry)),
                pack.uri(entry));
        try {
            return factory.newSchema(source);
        } catch (SAXException e) {
            throw new PackException("the schema " + entry + " of the pack "
                    + pack.directory() + " could not be compiled", e);
        }
    }

    private static void validate(byte[] xml, Schema schema, Pack pack,
                                 PackComponent component, XrSyntax syntax,
                                 List<SyntaxFinding> findings) {
        Validator validator = schema.newValidator();
        try {
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (SAXException e) {
            throw new PackException("no schema validator with the settings this module"
                    + " requires is available", e);
        }
        english(validator::setProperty);
        validator.setResourceResolver(new PackResolver(pack, component.files()));
        validator.setErrorHandler(new Collecting(findings, pack, component, syntax));
        SAXSource source = new SAXSource(XmlFrontDoor.newXmlReader(),
                new InputSource(new ByteArrayInputStream(xml)));
        try {
            validator.validate(source);
        } catch (SAXException e) {
            // The handler kept every complaint the validator made, including the one it
            // stopped on, so there is nothing to add here.
            if (findings.isEmpty()) {
                findings.add(finding(Text.normalize(e.getMessage()), -1, -1, pack, component,
                        syntax));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the document could not be read for schema"
                    + " validation", e);
        }
    }

    /**
     * Asks a schema factory or a validator for English messages.
     *
     * <p>A processor that does not know the property writes its messages the way it
     * writes them; there is nothing further this module can do about that without a
     * library of its own, and the identifier in front of the sentence is recovered by a
     * pattern that tolerates the spacing the localized forms use.
     */
    private static void english(Property property) {
        try {
            property.set(LOCALE, Locale.ENGLISH);
        } catch (SAXNotRecognizedException | SAXNotSupportedException e) {
            // This processor writes its messages in the language of the machine.
        }
    }

    /** Setting one property of a schema factory or of a validator. */
    @FunctionalInterface
    private interface Property {
        void set(String name, Object value)
                throws SAXNotRecognizedException, SAXNotSupportedException;
    }

    private static SyntaxFinding finding(String message, int line, int column, Pack pack,
                                         PackComponent component, XrSyntax syntax) {
        return new SyntaxFinding(Engine.XSD, Categories.ofSchema(syntax), Severity.FATAL,
                Severity.FATAL, code(message), message, "", line, column, pack.id(),
                pack.version(), pack.release(), component.name());
    }

    /**
     * Returns the identifier of the validity constraint the message names, which the
     * platform writes in front of the sentence, or a constant where it names none.
     */
    private static String code(String message) {
        Matcher matcher = CONSTRAINT.matcher(message);
        return matcher.find() ? matcher.group(1) : INVALID;
    }

    /** Collects what the schema says about a document. Everything it says is fatal. */
    private record Collecting(List<SyntaxFinding> findings, Pack pack,
                              PackComponent component, XrSyntax syntax)
            implements ErrorHandler {

        @Override
        public void warning(SAXParseException e) {
            add(e);
        }

        @Override
        public void error(SAXParseException e) {
            add(e);
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXParseException {
            add(e);
            throw e;
        }

        private void add(SAXParseException e) {
            findings.add(finding(Text.normalize(e.getMessage()), e.getLineNumber(),
                    e.getColumnNumber(), pack, component, syntax));
        }
    }

    /**
     * Refuses a schema of the pack that does not compile. It is a fault of the
     * installation rather than of a document, so it is an exception and never a finding.
     */
    private static final class Refusing implements ErrorHandler {

        @Override
        public void warning(SAXParseException e) {
            // A warning about a published schema is not this project's to act on.
        }

        @Override
        public void error(SAXParseException e) throws SAXParseException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXParseException {
            throw e;
        }
    }

}
