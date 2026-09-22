package de.bsnsoft.esj.cli;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Decides which of the three syntaxes a byte sequence is written in.
 *
 * <p>This is the whole of the syntax knowledge the command line owns, and it is
 * deliberately shallow: the first byte that is not whitespace decides between JSON and
 * XML, and for XML the root element decides between the three documents {@code esj-xr}
 * reads. Nothing here looks at the content of a document; a byte sequence that starts
 * with an opening brace is handed to the ESJ reader and a byte sequence that starts with
 * a less-than sign is handed to the importer, and each of them says what is wrong with
 * it in its own words.
 *
 * <p>A byte order mark is skipped before the first byte is looked at, so that a UTF-8
 * mark in front of an XML declaration does not hide the document — XML allows it. It is
 * skipped for JSON as well, and the ESJ reader then refuses the document, because the
 * specification, section 4.2 rejects a mark in front of an ESJ document and a message
 * about the mark is more use than one about an unrecognized format.
 *
 * <p>The root element is read with the stream parser of the platform, asked by name so
 * that no other implementation on the class path can answer, and with document type
 * definitions and external entities turned off. These are bytes from a stranger and the
 * detector meets them before anything else does, so the two settings are required rather
 * than attempted: a parser that did not support one of them, or that is not there at all,
 * makes the input one this tool does not read rather than one it reads unguarded. That is
 * the stance {@code XrTransformer} takes for the same problem one module further on.
 *
 * <p>Recognition works on an encoding whose ASCII characters are single bytes, which
 * UTF-8 and every ISO 8859 part are and UTF-16 is not. A document written in UTF-16 is the
 * exception, in both byte orders and with or without a byte order mark: the mark, or the
 * pattern of zero bytes around the {@code <} an XML document begins with, says which
 * encoding it is, the prefix is decoded in that encoding, and the root element is read
 * from the characters. The two byte orders are recognized together because there is no
 * reason for them to be answered differently, and a reader that scanned bytes would read
 * the padding of the one and the document of the other. An ESJ document is UTF-8 by the
 * specification, section 4.2. A document in any other encoding whose ASCII is not single
 * bytes — UTF-32, which XML allows — is one the tool says it recognized nothing of rather
 * than guessing at; {@code --from} reads such a document, and the importer then says which
 * encoding it will not read.
 */
final class InputDetector {

    /** The UTF-8 byte order mark. */
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * The byte order marks of the encodings whose ASCII characters are not one byte, with
     * the charset each of them announces. The two four-byte marks stand first: the first
     * two bytes of the UTF-32LE mark are the UTF-16LE mark.
     */
    private static final Map<String, byte[]> WIDE_BOMS = new LinkedHashMap<>(Map.of());

    static {
        WIDE_BOMS.put("UTF-32BE", new byte[] {0x00, 0x00, (byte) 0xFE, (byte) 0xFF});
        WIDE_BOMS.put("UTF-32LE", new byte[] {(byte) 0xFF, (byte) 0xFE, 0x00, 0x00});
        WIDE_BOMS.put("UTF-16BE", new byte[] {(byte) 0xFE, (byte) 0xFF});
        WIDE_BOMS.put("UTF-16LE", new byte[] {(byte) 0xFF, (byte) 0xFE});
    }

    /** The first bytes of a PDF file (PDF 32000-1, 7.5.2). */
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};

    /**
     * How many bytes of an input that announces a wide encoding are decoded to find its
     * root element.
     *
     * <p>Decoding is not free and the question is answered by the first element: the
     * characters cost twice what the bytes do, and an input decoded whole costs three
     * times its length — the bytes, the buffer of characters and the string — against a
     * bound that was chosen for the bytes. A root element with the namespace declarations
     * of UBL or CII on it is a few hundred characters, and a prolog longer than this is a
     * document this detector reports as unrecognized rather than one it pays for;
     * {@code --from} reads it.
     */
    private static final int WIDE_PREFIX = 65536;

    private InputDetector() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the syntax of a byte sequence.
     *
     * @param content the bytes that were read
     * @return the syntax, or an empty optional if the input is neither a JSON object nor
     *         an XML document of a syntax this tool reads
     */
    static Optional<InputSyntax> detect(byte[] content) {
        Optional<String> wide = wideText(content);
        if (wide.isPresent()) {
            // The characters are not single bytes, so the scan below would read the
            // padding of a wide encoding rather than the document.
            return rootElement(wide.orElseThrow());
        }
        int start = skipByteOrderMark(content);
        int first = firstSignificant(content, start);
        if (first < 0) {
            return Optional.empty();
        }
        byte b = content[first];
        if (b == '{') {
            return Optional.of(InputSyntax.ESJ);
        }
        if (b == '<') {
            return rootElement(content, start);
        }
        return Optional.empty();
    }

    /**
     * Returns a description of what the detector saw, for {@code --verbose}.
     *
     * @param content the bytes that were read
     * @return one line naming the first significant byte by its value and, for XML, the
     *         root element
     */
    static String describe(byte[] content) {
        Optional<String> wide = wideText(content);
        if (wide.isPresent()) {
            try (Reader reader = new Reader(wide.orElseThrow())) {
                return "byte order mark of a wide encoding, root element {"
                        + reader.namespace() + "}" + reader.localName();
            } catch (XMLStreamException e) {
                return "byte order mark of a wide encoding, root element unreadable";
            }
        }
        int start = skipByteOrderMark(content);
        String mark = start > 0 ? "byte order mark, then " : "";
        int first = firstSignificant(content, start);
        if (first < 0) {
            return mark + "nothing but whitespace";
        }
        String leading = mark + "first significant byte " + describeByte(content[first])
                + " at offset " + first;
        if (content[first] != '<') {
            return leading;
        }
        try (Reader reader = new Reader(content, start)) {
            return leading + ", root element {" + reader.namespace() + "}" + reader.localName();
        } catch (XMLStreamException e) {
            return leading + ", root element unreadable";
        }
    }

    /**
     * Tells whether the bytes read so far begin an XML document, from a prefix of the
     * input rather than from all of it.
     *
     * <p>{@link Input} asks this before it has read everything, so that the bound it
     * holds the read to can be the bound of the side that will parse the bytes: an XML
     * input the importer refuses at a few megabytes need not first be collected at sixty
     * four. The answer is the same shallow one {@link #detect(byte[])} gives — the first
     * byte that is not whitespace, after a byte order mark — and it is deliberately not a
     * claim that the document is well formed.
     *
     * @param prefix the bytes read so far
     * @param length how many of them are the input
     * @return whether the input begins an XML document, or an empty optional while the
     *         prefix carries nothing but a byte order mark and whitespace
     */
    static Optional<Boolean> beginsXml(byte[] prefix, int length) {
        if (incompleteMark(prefix, length)) {
            return Optional.empty();
        }
        Optional<Charset> wide = wideCharset(prefix, length);
        if (wide.isPresent()) {
            return beginsXmlWide(prefix, length, wide.orElseThrow());
        }
        int start = beginsWith(prefix, length, BOM) ? BOM.length : 0;
        for (int i = start; i < length; i++) {
            byte b = prefix[i];
            if (b != ' ' && b != '\t' && b != '\r' && b != '\n') {
                return Optional.of(b == '<');
            }
        }
        return Optional.empty();
    }

    /**
     * Answers {@link #beginsXml} for an input whose byte order mark announces an encoding
     * whose characters are not single bytes.
     *
     * <p>The answer has to be the same one the whole detector gives, and it has to be
     * given here: a caller that read the first byte of such an input would read the padding
     * of the encoding rather than the document, conclude that the input is not XML, and
     * then hold an XML document to the bound of an ESJ document — sixteen times the one
     * the importer of the same document is read within. That is a bound the input decides
     * by choosing its encoding, which is no bound at all.
     */
    private static Optional<Boolean> beginsXmlWide(byte[] prefix, int length, Charset charset) {
        String text = decode(prefix, length, charset);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\uFEFF' || c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                continue;
            }
            return Optional.of(c == '<');
        }
        return Optional.empty();
    }

    /**
     * Tells whether the bytes read so far are a beginning of a byte order mark that is
     * longer than them, so that the answer is not yet known.
     *
     * <p>The four-byte marks are the reason this is not one comparison: the mark of
     * UTF-16LE is the first half of the mark of UTF-32LE, so two bytes that spell the one
     * may still turn out to be the other. A document without a mark is here too: its
     * encoding is spelled by three bytes, and a prefix shorter than that says nothing.
     */
    private static boolean incompleteMark(byte[] prefix, int length) {
        if (isBeginningOf(prefix, length, BOM)) {
            return true;
        }
        for (byte[] mark : WIDE_BOMS.values()) {
            if (isBeginningOf(prefix, length, mark)) {
                return true;
            }
        }
        // The pattern of an unmarked wide document needs three bytes to be read, and the
        // first of them is a byte no single-byte document begins with.
        return length < 3 && length > 0 && prefix[0] == 0x00;
    }

    /** Tells whether the bytes read so far are a strict beginning of a mark. */
    private static boolean isBeginningOf(byte[] prefix, int length, byte[] mark) {
        if (length >= mark.length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (prefix[i] != mark[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tells whether the bytes are a PDF, which is decided by the file header alone.
     *
     * <p>A hybrid invoice is a PDF with the invoice inside it, so a PDF is not a foreign
     * input here but a container to open: the bytes go to {@code esj-pdf}, the invoice
     * that comes out goes down the same path as a file of that syntax. The header has to
     * stand at the first byte, with no byte order mark in front of it: a PDF has no
     * encoding for a mark to declare, and a reader that looked past leading bytes would
     * accept a file whose header is somewhere in the middle.
     *
     * @param content the bytes that were read
     * @return {@code true} if the bytes begin with the PDF file header
     */
    static boolean isPdf(byte[] content) {
        return beginsWith(content, content.length, PDF);
    }

    /**
     * Tells whether a prefix of the input begins the PDF file header, for a reader that
     * has to choose a bound before it has read the whole input.
     *
     * @param prefix the first bytes of the input
     * @param length how many of them were read
     * @return whether the input is a PDF, or an empty optional where the prefix is too
     *         short to say
     */
    static Optional<Boolean> beginsPdf(byte[] prefix, int length) {
        int common = Math.min(length, PDF.length);
        for (int i = 0; i < common; i++) {
            if (prefix[i] != PDF[i]) {
                return Optional.of(Boolean.FALSE);
            }
        }
        return length >= PDF.length ? Optional.of(Boolean.TRUE) : Optional.empty();
    }

    /**
     * Writes one byte of the input for a diagnostic: its value, and its ASCII spelling
     * where it has a printable one.
     *
     * <p>A byte is not a character. Casting it to {@code char} sign-extends it, so
     * {@code 0xC3} would be written as U+FFC3, a character that is nowhere in the file,
     * and {@code 0x1B} would be written as a raw escape — into a terminal, from the one
     * byte of a hostile document the tool quotes before any parser has looked at it
     * (specification, section 12.6).
     */
    private static String describeByte(byte b) {
        int value = b & 0xFF;
        String hex = String.format("0x%02X", value);
        return value >= 0x20 && value < 0x7F ? hex + " '" + (char) value + "'" : hex;
    }

    /** Tells whether the first {@code length} bytes of a buffer begin with a mark. */
    private static boolean beginsWith(byte[] content, int length, byte[] magic) {
        if (length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static Optional<InputSyntax> rootElement(String text) {
        try (Reader reader = new Reader(text)) {
            return InputSyntax.ofRootElement(reader.namespace(), reader.localName());
        } catch (XMLStreamException e) {
            return Optional.empty();
        }
    }

    private static Optional<InputSyntax> rootElement(byte[] content, int start) {
        try (Reader reader = new Reader(content, start)) {
            return InputSyntax.ofRootElement(reader.namespace(), reader.localName());
        } catch (XMLStreamException e) {
            // Not well-formed enough to name a root element. The importer is the place
            // that reports why, so the detector only says that it recognized nothing.
            return Optional.empty();
        }
    }

    /**
     * Tells whether the input begins with the byte order mark of UTF-16 or UTF-32.
     *
     * @param content the bytes that were read
     * @return {@code true} where one of those marks stands at the first byte
     */
    static boolean isWide(byte[] content) {
        return wideMark(content, content.length).isPresent();
    }

    /**
     * Returns the charset the leading byte order mark of the first {@code length} bytes
     * announces, where it is a wide one.
     */
    private static Optional<Charset> wideMark(byte[] content, int length) {
        for (Map.Entry<String, byte[]> mark : WIDE_BOMS.entrySet()) {
            if (beginsWith(content, length, mark.getValue())) {
                return charset(mark.getKey());
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the charset the first bytes of a document spell without a byte order mark.
     *
     * <p>An XML document begins with {@code <}, so the zero bytes around that one
     * character name the width and the byte order (XML 1.0, appendix F). Only UTF-16 is
     * answered here: it is the encoding a writer reaches for after UTF-8, this tool reads
     * it, and the two byte orders have to be answered alike. UTF-32 is left unrecognized,
     * because recognizing it would only move the refusal from this detector to a parser
     * that does not read it either.
     */
    private static Optional<Charset> widePattern(byte[] content, int length) {
        if (beginsWithBytes(content, length, 0x00, 0x3C, 0x00)) {
            return charset("UTF-16BE");
        }
        if (beginsWithBytes(content, length, 0x3C, 0x00) && length > 2 && content[2] != 0x00) {
            return charset("UTF-16LE");
        }
        return Optional.empty();
    }

    /**
     * Returns the charset the first {@code length} bytes announce, by a mark or by the
     * pattern of an unmarked wide document.
     */
    private static Optional<Charset> wideCharset(byte[] content, int length) {
        Optional<Charset> mark = wideMark(content, length);
        return mark.isPresent() ? mark : widePattern(content, length);
    }

    private static Optional<Charset> charset(String name) {
        try {
            return Optional.of(Charset.forName(name));
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return Optional.empty();
        }
    }

    /** Tells whether the first {@code length} bytes begin with these byte values. */
    private static boolean beginsWithBytes(byte[] content, int length, int... bytes) {
        if (length < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if ((content[i] & 0xFF) != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the characters of an input that announces a wide encoding, decoded here
     * rather than by the parser.
     *
     * <p>Only the first {@link #WIDE_PREFIX} bytes are decoded, because only the root
     * element is being asked for. A prefix cut in the middle of a character costs a
     * replacement character at the end of it, which the decoder below produces anyway and
     * which no question here is decided by.
     *
     * @param content the bytes that were read
     * @return the characters of the prefix without a leading mark, or an empty optional
     *         where the input announces no wide encoding
     */
    private static Optional<String> wideText(byte[] content) {
        Optional<Charset> charset = wideCharset(content, content.length);
        if (charset.isEmpty()) {
            return Optional.empty();
        }
        String text = decode(content, content.length, charset.orElseThrow());
        return Optional.of(text.isEmpty() || text.charAt(0) != '\uFEFF'
                ? text : text.substring(1));
    }

    /**
     * Decodes a bounded prefix of a buffer, replacing what the charset cannot spell.
     *
     * <p>The parser is given characters and never these bytes, for the reason the rest of
     * this project decodes first: a parser handed bytes it cannot make sense of writes its
     * own complaint onto the error stream of the process, in the language of the machine,
     * before any caller can catch the exception.
     */
    private static String decode(byte[] content, int length, Charset charset) {
        int bounded = Math.min(length, WIDE_PREFIX);
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .decode(ByteBuffer.wrap(content, 0, bounded))
                    .toString();
        } catch (CharacterCodingException e) {
            // A decoder that replaces reports nothing; this is here because the signature
            // declares it.
            return "";
        }
    }

    private static int skipByteOrderMark(byte[] content) {
        if (content.length >= BOM.length
                && content[0] == BOM[0] && content[1] == BOM[1] && content[2] == BOM[2]) {
            return BOM.length;
        }
        return 0;
    }

    /**
     * Returns the offset of the first byte that is not whitespace, or {@code -1}.
     *
     * <p>The whole input is scanned. Leading whitespace is insignificant in JSON and
     * unbounded in the specification, section 4, so a bound here would refuse a document
     * that the reader of {@code esj-core} reads, and would refuse it with a sentence
     * about a syntax rather than about a bound. The input is already held to the document
     * size of section 12.2 by {@link Input}, which is what makes the scan cheap enough to
     * be unbounded.
     */
    private static int firstSignificant(byte[] content, int start) {
        for (int i = start; i < content.length; i++) {
            byte b = content[i];
            if (b != ' ' && b != '\t' && b != '\r' && b != '\n') {
                return i;
            }
        }
        return -1;
    }

    /** A stream parser positioned on the root element, closed by the caller. */
    private static final class Reader implements AutoCloseable {

        private final XMLStreamReader reader;

        Reader(byte[] content, int start) throws XMLStreamException {
            this(hardened().createXMLStreamReader(
                    new ByteArrayInputStream(content, start, content.length - start)));
        }

        Reader(String text) throws XMLStreamException {
            this(hardened().createXMLStreamReader(new StringReader(text)));
        }

        private Reader(XMLStreamReader reader) throws XMLStreamException {
            this.reader = reader;
            while (reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
                if (!reader.hasNext()) {
                    throw new XMLStreamException("the document carries no element");
                }
                // The prolog: a declaration, comments, processing instructions.
                reader.next();
            }
        }

        /**
         * Returns a parser factory that refuses document type definitions and external
         * entities.
         *
         * @throws XMLStreamException if no such factory can be built, which makes the
         *                            input one this tool does not read
         */
        private static XMLInputFactory hardened() throws XMLStreamException {
            try {
                XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
                factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
                factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,
                        Boolean.FALSE);
                factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
                return factory;
            } catch (FactoryConfigurationError | IllegalArgumentException e) {
                throw new XMLStreamException(
                        "no XML parser that refuses document type definitions and external"
                                + " entities is available", e);
            }
        }

        String namespace() {
            String namespace = reader.getNamespaceURI();
            return namespace == null ? "" : namespace;
        }

        String localName() {
            return reader.getLocalName();
        }

        @Override
        public void close() throws XMLStreamException {
            reader.close();
        }
    }
}
