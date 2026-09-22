package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.imports.ImportNote;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
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
 * {@link XrEncodingMode#STRICT} refuses it outright. Nothing here decides which of the
 * two happens.
 *
 * <p>Four charsets are recoded and no others: UTF-8, UTF-16 in either byte order,
 * ISO-8859-1 and Windows-1252. A byte sequence that is not valid UTF-8 where UTF-8 was
 * claimed is read as Windows-1252 when it carries a byte between {@code 0x80} and
 * {@code 0x9F} that Windows-1252 defines, and as ISO-8859-1 otherwise; the report says
 * which of the two was assumed, because the two differ exactly in that range and the
 * assumption is a guess about the writer rather than a fact about the bytes. Nothing is
 * guessed at beyond these: a document that declares some other charset is handed to the
 * XML parser as it stands, which is the party that knows the whole list.
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
     * declaration is read from a short window, and whether the rest is valid UTF-8 is
     * decided by a scan over the bytes.
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
            return new XmlEncodingReport(mark, declaration, actual,
                    declaration.isEmpty() || sameCharset(declaration.get(), actual));
        }
        if (pattern.isPresent()) {
            // A document in UTF-16 or UTF-32 without a byte order mark has to declare
            // itself, and one that declares something else is wrong about its own bytes.
            String actual = pattern.get();
            return new XmlEncodingReport(mark, declaration, actual,
                    declaration.isPresent() && sameCharset(declaration.get(), actual));
        }
        String documented = declaration.orElse("UTF-8");
        if ("UTF-8".equals(documented)) {
            return isUtf8(xml)
                    ? new XmlEncodingReport(mark, declaration, "UTF-8", true)
                    : new XmlEncodingReport(mark, declaration, singleByteGuess(xml), false);
        }
        if ("ISO-8859-1".equals(documented) || "windows-1252".equals(documented)) {
            // Every byte sequence decodes in a single-byte charset, so the bytes cannot
            // contradict such a declaration by failing to decode. What contradicts it is
            // being valid UTF-8 with a character outside ASCII: that is a sequence no
            // writer of Latin-1 produces by accident.
            return isUtf8(xml) && hasNonAscii(xml)
                    ? new XmlEncodingReport(mark, declaration, "UTF-8", false)
                    : new XmlEncodingReport(mark, declaration, documented, true);
        }
        if (isWideFamily(documented)) {
            // Declared UTF-16 or UTF-32, and the bytes are neither: a single-byte
            // sequence with no byte order mark and no wide pattern in front of it.
            return new XmlEncodingReport(mark, declaration,
                    isUtf8(xml) ? "UTF-8" : singleByteGuess(xml), false);
        }
        return new XmlEncodingReport(mark, declaration, documented, true);
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
     * @throws XrFormatException        if the bytes do not decode in the charset the
     *                                  report names
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
        String text = decode(xml, Charset.forName(report.assumed()));
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
    private static String decode(byte[] xml, Charset charset) {
        try {
            CharBuffer decoded = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(xml));
            return decoded.toString();
        } catch (CharacterCodingException e) {
            throw new XrFormatException("the document does not decode in " + charset.name()
                    + ", which is the charset its bytes were taken for", e);
        }
    }
}
