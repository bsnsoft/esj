package de.bsnsoft.esj.internal;

import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.xml.EncodingMode;
import de.bsnsoft.esj.xml.XmlEncodingException;
import de.bsnsoft.esj.xml.XmlEncodingReport;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks at the bytes of an XML document before a parser does, and recodes them where the
 * encoding they are written in is not the one they claim.
 *
 * <p>The problem this answers is one of the commonest defects of an electronic invoice in
 * the field: a document written by a tool that encoded a party name in ISO-8859-1 and
 * declared {@code encoding="UTF-8"}, or the reverse. An XML parser refuses such a
 * document, correctly, and the refusal says nothing a user can act on. This class says
 * what the bytes are, what the document claims, and — where the two disagree and the
 * charset is one of the four named below — produces UTF-8 bytes with the declaration
 * rewritten to match.
 *
 * <p><strong>Repair is not validation.</strong> A document that had to be recoded was
 * defective, and the importer records that as the import note
 * {@link ImportNote.Kind#ENCODING_REPAIRED} rather than passing it over; an importer in
 * {@link EncodingMode#STRICT} refuses it outright. Nothing here decides which of the
 * two happens.
 *
 * <p>Four charsets are recoded and no others: UTF-8, UTF-16 in either byte order,
 * ISO-8859-1 and Windows-1252. A byte sequence that is not valid UTF-8 where UTF-8 was
 * claimed is read as Windows-1252 when it carries a byte between {@code 0x80} and
 * {@code 0x9F} that Windows-1252 defines, and as ISO-8859-1 otherwise; the report says
 * which of the two was assumed, because the two differ exactly in that range and the
 * assumption is a guess about the writer rather than a fact about the bytes. A document
 * that declares Windows-1252 and carries one of the five bytes it leaves undefined is read
 * as ISO-8859-1, which defines them.
 *
 * <p>Nothing is guessed at beyond these. A document that declares some other charset — a
 * Japanese one, a Latin one of another number, ASCII — is handed to the XML parser as it
 * stands where its bytes decode in that charset, and where they do not it is read as UTF-8
 * if they are UTF-8 and is otherwise not read at all: the report says that the bytes do not
 * {@link XmlEncodingReport#decodes() decode}, and an importer refuses them in either mode.
 * The same question is asked of a byte order mark and of a document that names no
 * encoding. A parser handed such bytes would read them in the charset they claim and turn
 * what it cannot decode into a replacement character, or write a complaint of its own to
 * the error stream of the process before it refused them; either way the content of the
 * document would be decided by the parser of the day rather than by the bytes.
 */
public final class XmlBytes {

    /**
     * How many bytes are read while looking for the XML declaration. A declaration is
     * short by its grammar, and one that does not end within this window is treated as
     * absent rather than searched for to any length.
     */
    private static final int DECLARATION_WINDOW = 512;

    /** The encoding pseudo-attribute of an XML declaration. */
    private static final Pattern ENCODING = Pattern.compile(
            "\\bencoding\\s*=\\s*([\"'])([A-Za-z][A-Za-z0-9._-]*)\\1");

    /** The bytes Windows-1252 leaves undefined in the range it fills and ISO-8859-1 does not. */
    private static final Set<Integer> UNDEFINED_IN_1252 = Set.of(0x81, 0x8D, 0x8F, 0x90, 0x9D);

    /** The size of the buffer {@link #decodes(byte[], Charset)} decodes into, in chars. */
    private static final int DECODE_BUFFER = 8192;

    /** The character a byte order mark decodes to, which is not part of the document. */
    private static final char ZERO_WIDTH_NO_BREAK_SPACE = '\uFEFF';

    private XmlBytes() {
        throw new AssertionError("no instances");
    }

    /**
     * Reports what the bytes of a document say about their encoding and what they are.
     *
     * <p>Nothing is decoded into a document here and nothing is allocated in proportion
     * to the input: the byte order mark and the first four bytes are read, the XML
     * declaration is read from a short window, and whether the rest decodes in the charset
     * it is taken for is decided by a scan over the bytes — the UTF-8 one below, or a
     * decoder that writes into one buffer of fixed size and keeps nothing it decoded.
     *
     * @param xml the bytes of the document
     * @return the report
     * @throws NullPointerException if {@code xml} is {@code null}
     */
    public static XmlEncodingReport inspect(byte[] xml) {
        Objects.requireNonNull(xml, "xml");
        Optional<String> mark = byteOrderMark(xml);
        Optional<String> pattern = mark.isPresent() ? Optional.empty() : byPattern(xml);
        Optional<String> declaration =
                declaredEncoding(xml, mark.or(() -> pattern).orElse("ISO-8859-1"));
        if (mark.isPresent()) {
            String actual = mark.get();
            return checked(xml, mark, declaration, actual,
                    declaration.isEmpty() || sameCharset(declaration.get(), actual));
        }
        if (pattern.isPresent()) {
            // A document in UTF-16 or UTF-32 without a byte order mark has to declare
            // itself, and one that declares something else is wrong about its own bytes.
            String actual = pattern.get();
            return checked(xml, mark, declaration, actual,
                    declaration.isPresent() && sameCharset(declaration.get(), actual));
        }
        String documented = declaration.orElse("UTF-8");
        if ("UTF-8".equals(documented)) {
            return isUtf8(xml)
                    ? new XmlEncodingReport(mark, declaration, "UTF-8", true)
                    : new XmlEncodingReport(mark, declaration, singleByteGuess(xml), false);
        }
        if ("ISO-8859-1".equals(documented) || "windows-1252".equals(documented)) {
            // Every byte sequence decodes in ISO-8859-1, so the bytes cannot contradict
            // that declaration by failing to decode. What contradicts it is being valid
            // UTF-8 with a character outside ASCII: that is a sequence no writer of
            // Latin-1 produces by accident. Windows-1252 leaves five bytes undefined, and
            // a document that declares it and carries one of them is read in the charset
            // that defines them rather than with a replacement character.
            if (isUtf8(xml) && hasNonAscii(xml)) {
                return new XmlEncodingReport(mark, declaration, "UTF-8", false);
            }
            return "windows-1252".equals(documented) && hasUndefinedIn1252(xml)
                    ? new XmlEncodingReport(mark, declaration, "ISO-8859-1", false)
                    : new XmlEncodingReport(mark, declaration, documented, true);
        }
        if (isWideFamily(documented)) {
            // Declared UTF-16 or UTF-32, and the bytes are neither: a single-byte
            // sequence with no byte order mark and no wide pattern in front of it.
            return new XmlEncodingReport(mark, declaration,
                    isUtf8(xml) ? "UTF-8" : singleByteGuess(xml), false);
        }
        Optional<Charset> charset = supported(documented);
        if (charset.isEmpty() || decodes(xml, charset.get())) {
            return new XmlEncodingReport(mark, declaration, documented, true);
        }
        // Declared some other charset, and the bytes are not written in it. Bytes that are
        // UTF-8 with a character outside ASCII were written by a UTF-8 writer that
        // declared the wrong name, which is the one repair this case admits; anything else
        // is a guess and is not made.
        return isUtf8(xml) && hasNonAscii(xml)
                ? new XmlEncodingReport(mark, declaration, "UTF-8", false)
                : new XmlEncodingReport(mark, declaration, documented, false, false);
    }

    /**
     * Returns the report of a document whose charset its first bytes name, after asking
     * whether the rest of the bytes decode in it: a byte order mark or a wide pattern is a
     * fact about the first bytes and not about the others.
     */
    private static XmlEncodingReport checked(byte[] xml,
                                             Optional<String> mark,
                                             Optional<String> declaration,
                                             String actual,
                                             boolean agrees) {
        Optional<Charset> charset = supported(actual);
        boolean decodes = charset.isEmpty() || actual.startsWith("UTF-32")
                || ("UTF-8".equals(actual) ? isUtf8(xml) : decodes(xml, charset.get()));
        return new XmlEncodingReport(mark, declaration, actual, agrees && decodes, decodes);
    }

    /** Returns the charset of a name, where this runtime has a decoder for it. */
    private static Optional<Charset> supported(String name) {
        try {
            return Charset.isSupported(name) ? Optional.of(Charset.forName(name))
                    : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Tells whether the bytes decode in a charset without one sequence that is no
     * character of it. The decoder writes into one buffer of fixed size, which is emptied
     * whenever it fills, so the scan costs time in proportion to the input and no memory.
     */
    private static boolean decodes(byte[] xml, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer in = ByteBuffer.wrap(xml);
        CharBuffer out = CharBuffer.allocate(DECODE_BUFFER);
        while (true) {
            CoderResult result = decoder.decode(in, out, true);
            if (result.isError()) {
                return false;
            }
            if (result.isUnderflow()) {
                break;
            }
            out.clear();
        }
        while (true) {
            out.clear();
            CoderResult result = decoder.flush(out);
            if (result.isError()) {
                return false;
            }
            if (result.isUnderflow()) {
                return true;
            }
        }
    }

    /**
     * Recodes a document into UTF-8 and rewrites its declaration to match.
     *
     * <p>The bytes are decoded in the charset the report says they are, a leading byte
     * order mark is dropped, the {@code encoding} pseudo-attribute of the XML
     * declaration is rewritten to {@code UTF-8} where the document carries one, and the
     * result is encoded in UTF-8. A document without a declaration needs none: UTF-8 is
     * what an XML document without one is.
     *
     * <p>Bytes that the report calls consistent are returned unchanged, so that a
     * caller which repairs unconditionally never touches a document that was right to
     * begin with.
     *
     * @param xml    the bytes of the document
     * @param report the report {@link #inspect(byte[])} made of those bytes
     * @return the UTF-8 bytes, or {@code xml} itself where the report is consistent
     * @throws IllegalArgumentException if the report is inconsistent and not
     *                                  {@link XmlEncodingReport#repairable()}
     * @throws XmlEncodingException    if the bytes do not decode in the charset the
     *                                  report names, which a report that was made of
     *                                  these bytes rules out
     * @throws NullPointerException     if an argument is {@code null}
     */
    public static byte[] repair(byte[] xml, XmlEncodingReport report) {
        Objects.requireNonNull(xml, "xml");
        Objects.requireNonNull(report, "report");
        if (report.consistent()) {
            return xml;
        }
        if (!report.repairable()) {
            throw new IllegalArgumentException("this module recodes UTF-8, UTF-16,"
                    + " ISO-8859-1 and Windows-1252, and the bytes are " + report.assumed());
        }
        String text = decode(xml, report);
        if (!text.isEmpty() && text.charAt(0) == ZERO_WIDTH_NO_BREAK_SPACE) {
            text = text.substring(1);
        }
        return declaredAsUtf8(text).getBytes(StandardCharsets.UTF_8);
    }

    /** Rewrites the encoding pseudo-attribute of the XML declaration to {@code UTF-8}. */
    private static String declaredAsUtf8(String text) {
        int end = declarationEnd(text);
        if (end < 0) {
            return text;
        }
        String declaration = text.substring(0, end);
        Matcher matcher = ENCODING.matcher(declaration);
        if (!matcher.find()) {
            return text;
        }
        return text.substring(0, matcher.start(2)) + "UTF-8" + text.substring(matcher.end(2));
    }

    /**
     * Returns the offset just past the {@code ?>} of the XML declaration, or {@code -1}
     * where the text carries no declaration that ends inside the window.
     */
    private static int declarationEnd(String text) {
        if (!text.startsWith("<?xml") || text.length() < 6 || !isSpace(text.charAt(5))) {
            return -1;
        }
        int end = text.indexOf("?>", 5);
        return end < 0 || end > DECLARATION_WINDOW ? -1 : end + 2;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    /**
     * Reads the encoding pseudo-attribute out of the first bytes of the document.
     *
     * @param probe the charset to read the window in, which decides only whether the
     *              declaration is legible at all
     * @return the canonical name of the charset the declaration names, the name as
     *         written where this runtime knows no such charset, or an empty optional
     */
    private static Optional<String> declaredEncoding(byte[] xml, String probe) {
        Charset charset = Charset.forName(probe);
        int width = probe.startsWith("UTF-32") ? 4 : probe.startsWith("UTF-16") ? 2 : 1;
        int length = Math.min(xml.length, DECLARATION_WINDOW) / width * width;
        String text;
        try {
            text = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.IGNORE)
                    .onUnmappableCharacter(CodingErrorAction.IGNORE)
                    .decode(ByteBuffer.wrap(xml, 0, length))
                    .toString();
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
        if (!text.isEmpty() && text.charAt(0) == ZERO_WIDTH_NO_BREAK_SPACE) {
            text = text.substring(1);
        }
        int end = declarationEnd(text);
        if (end < 0) {
            return Optional.empty();
        }
        Matcher matcher = ENCODING.matcher(text.substring(0, end));
        return matcher.find() ? Optional.of(canonical(matcher.group(2))) : Optional.empty();
    }

    /** Returns the canonical name of a charset, or the name as written where there is none. */
    private static String canonical(String name) {
        try {
            return Charset.forName(name).name();
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return name;
        }
    }

    /**
     * Tells whether a declared charset names the charset the bytes are in. A document in
     * UTF-16LE may declare {@code UTF-16}, which is the name of the family and correct.
     */
    private static boolean sameCharset(String declared, String actual) {
        return declared.equals(actual)
                || (actual.startsWith(declared) && isWideFamily(declared));
    }

    private static boolean isWideFamily(String name) {
        return name.startsWith("UTF-16") || name.startsWith("UTF-32");
    }

    /** Returns the charset a byte order mark names, if the document carries one. */
    private static Optional<String> byteOrderMark(byte[] xml) {
        if (startsWith(xml, 0xEF, 0xBB, 0xBF)) {
            return Optional.of("UTF-8");
        }
        if (startsWith(xml, 0x00, 0x00, 0xFE, 0xFF)) {
            return Optional.of("UTF-32BE");
        }
        if (startsWith(xml, 0xFF, 0xFE, 0x00, 0x00)) {
            return Optional.of("UTF-32LE");
        }
        if (startsWith(xml, 0xFE, 0xFF)) {
            return Optional.of("UTF-16BE");
        }
        if (startsWith(xml, 0xFF, 0xFE)) {
            return Optional.of("UTF-16LE");
        }
        return Optional.empty();
    }

    /**
     * Returns the charset the first four bytes spell for a document without a byte order
     * mark. An XML document begins with {@code <}, so the zero bytes around that one
     * character name the width and the byte order.
     */
    private static Optional<String> byPattern(byte[] xml) {
        if (startsWith(xml, 0x00, 0x00, 0x00, 0x3C)) {
            return Optional.of("UTF-32BE");
        }
        if (startsWith(xml, 0x3C, 0x00, 0x00, 0x00)) {
            return Optional.of("UTF-32LE");
        }
        if (startsWith(xml, 0x00, 0x3C, 0x00)) {
            return Optional.of("UTF-16BE");
        }
        if (startsWith(xml, 0x3C, 0x00) && xml.length > 2 && xml[2] != 0x00) {
            return Optional.of("UTF-16LE");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] xml, int... bytes) {
        if (xml.length < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if ((xml[i] & 0xFF) != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Names the single-byte charset a sequence that is not valid UTF-8 is read in.
     *
     * <p>ISO-8859-1 and Windows-1252 agree everywhere except between {@code 0x80} and
     * {@code 0x9F}, where the first has control characters and the second has the typographic
     * characters a word processor writes: the quotation marks, the dashes, the euro sign.
     * A document that carries a byte in that range almost certainly came from the second,
     * and one that does not is read in the first, where the choice makes no difference.
     */
    private static String singleByteGuess(byte[] xml) {
        boolean upperControl = false;
        for (byte b : xml) {
            int value = b & 0xFF;
            if (value >= 0x80 && value <= 0x9F) {
                if (UNDEFINED_IN_1252.contains(value)) {
                    return "ISO-8859-1";
                }
                upperControl = true;
            }
        }
        return upperControl ? "windows-1252" : "ISO-8859-1";
    }

    /** Tells whether the document carries one of the five bytes Windows-1252 leaves undefined. */
    private static boolean hasUndefinedIn1252(byte[] xml) {
        for (byte b : xml) {
            int value = b & 0xFF;
            if (value >= 0x81 && value <= 0x9D && UNDEFINED_IN_1252.contains(value)) {
                return true;
            }
        }
        return false;
    }

    /** Tells whether the document carries a byte outside ASCII. */
    private static boolean hasNonAscii(byte[] xml) {
        for (byte b : xml) {
            if ((b & 0x80) != 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether the bytes are valid UTF-8: no overlong form, no encoded surrogate,
     * nothing above U+10FFFF and no truncated sequence. The scan allocates nothing, which
     * is what makes it usable on an input of the size the importer accepts.
     */
    private static boolean isUtf8(byte[] xml) {
        int i = 0;
        while (i < xml.length) {
            int first = xml[i] & 0xFF;
            if (first < 0x80) {
                i++;
                continue;
            }
            int length;
            int lowest;
            int code;
            if (first >= 0xC2 && first <= 0xDF) {
                length = 2;
                lowest = 0x80;
                code = first & 0x1F;
            } else if (first >= 0xE0 && first <= 0xEF) {
                length = 3;
                lowest = 0x800;
                code = first & 0x0F;
            } else if (first >= 0xF0 && first <= 0xF4) {
                length = 4;
                lowest = 0x10000;
                code = first & 0x07;
            } else {
                return false;
            }
            if (i + length > xml.length) {
                return false;
            }
            for (int k = 1; k < length; k++) {
                int next = xml[i + k] & 0xFF;
                if ((next & 0xC0) != 0x80) {
                    return false;
                }
                code = (code << 6) | (next & 0x3F);
            }
            if (code < lowest || code > 0x10FFFF || (code >= 0xD800 && code <= 0xDFFF)) {
                return false;
            }
            i += length;
        }
        return true;
    }

    /** Decodes the whole document, refusing anything the charset cannot spell. */
    private static String decode(byte[] xml, XmlEncodingReport report) {
        Charset charset = Charset.forName(report.assumed());
        try {
            CharBuffer decoded = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(xml));
            return decoded.toString();
        } catch (CharacterCodingException e) {
            XmlEncodingException refused = new XmlEncodingException(
                    "the document does not decode in " + charset.name()
                            + ", which is the charset its bytes were taken for",
                    report.documented(), report.assumed(), false);
            refused.initCause(e);
            throw refused;
        }
    }
}
