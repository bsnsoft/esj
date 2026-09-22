package de.bsnsoft.esj.syntax;

import de.bsnsoft.esj.xr.XmlFrontDoor;
import de.bsnsoft.esj.xr.XrFormatException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.sf.saxon.s9api.XdmNode;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * The first block: is this XML at all, and does it say what it is written in.
 *
 * <p>It runs before the schema and before the rules, and nothing runs after it if it
 * fails. Bytes that are not well-formed XML have no elements to validate, and bytes whose
 * encoding is not the one they declare have no text to read, so a schema finding or a
 * rule finding made on them would describe a document nobody sent.
 *
 * <p>The parser is the one of {@link XmlFrontDoor}: it refuses a document type
 * declaration outright, fetches no external entity, no external subset, no DTD and no
 * schema, and processes no XInclude. A document that carries one of those is refused
 * here, with the parser's own words, and never reaches the schema.
 */
final class XmlCheck {

    /** The code of a finding about the encoding of a document. */
    static final String ENCODING = "XML-ENCODING";

    /** The code of a finding about a document that is not XML this engine reads. */
    static final String NOT_WELL_FORMED = "XML-NOT-WELL-FORMED";

    private XmlCheck() {
        throw new AssertionError("no instances");
    }

    /**
     * The outcome: what was found, and the tree if one could be built.
     *
     * @param findings what was found, empty for a document that is what it says it is
     * @param document the parsed document, empty where it could not be parsed
     */
    record Result(List<SyntaxFinding> findings, Optional<XdmNode> document) {
    }

    /**
     * Checks a document and parses it.
     *
     * @param xml the bytes of the document
     * @return the outcome
     */
    static Result check(byte[] xml) {
        Optional<String> encoding = XmlEncoding.problem(xml);
        if (encoding.isPresent()) {
            return new Result(List.of(finding(ENCODING, encoding.get(), -1, -1)),
                    Optional.empty());
        }
        List<SyntaxFinding> findings = new ArrayList<>();
        XMLReader reader = XmlFrontDoor.newXmlReader();
        reader.setErrorHandler(new Collecting(findings));
        try {
            XdmNode document = XmlFrontDoor.parse(xml, reader);
            return new Result(List.copyOf(findings), Optional.of(document));
        } catch (XrFormatException e) {
            if (findings.isEmpty()) {
                findings.add(finding(NOT_WELL_FORMED, Text.normalize(e.getMessage()), -1, -1));
            }
            return new Result(List.copyOf(findings), Optional.empty());
        }
    }

    private static SyntaxFinding finding(String code, String message, int line, int column) {
        return new SyntaxFinding(Engine.PARSER, FindingCategory.XML, Severity.FATAL,
                Severity.FATAL, code, message, "", line, column, "", "", "", "");
    }

    /**
     * Keeps what the parser complains about instead of letting it end the read. A
     * document is read once, and a caller is better served by the three places it is
     * broken than by the first of them.
     */
    private record Collecting(List<SyntaxFinding> findings) implements ErrorHandler {

        @Override
        public void warning(SAXParseException e) {
            findings.add(new SyntaxFinding(Engine.PARSER, FindingCategory.XML,
                    Severity.WARNING, Severity.WARNING, NOT_WELL_FORMED,
                    Text.normalize(e.getMessage()), "", e.getLineNumber(),
                    e.getColumnNumber(), "", "", "", ""));
        }

        @Override
        public void error(SAXParseException e) {
            findings.add(finding(NOT_WELL_FORMED, Text.normalize(e.getMessage()),
                    e.getLineNumber(), e.getColumnNumber()));
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXParseException {
            findings.add(finding(NOT_WELL_FORMED, Text.normalize(e.getMessage()),
                    e.getLineNumber(), e.getColumnNumber()));
            throw e;
        }
    }
}
