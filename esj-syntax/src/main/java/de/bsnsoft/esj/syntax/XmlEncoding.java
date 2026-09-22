package de.bsnsoft.esj.syntax;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks whether a document's bytes spell what its declaration says they spell.
 *
 * <p>This is strict and it repairs nothing. A document that declares UTF-8 and carries a
 * byte sequence that is no UTF-8 is a document whose sender and receiver disagree about
 * what its text is, and every value in it is then in doubt — a name, a bank account, an
 * amount's separator. Guessing the intended encoding would turn that doubt into a
 * confident wrong answer, and doing it inside a validator would hide the fault from the
 * one report that exists to show it. Repair belongs where a human decides to accept a
 * broken document, not here.
 *
 * <p>A parser would refuse most of these too, but not all: the check is made before the
 * parser so that the report names the encoding rather than the first byte that could not
 * be read, and so that the answer does not depend on how forgiving the parser of the day
 * is.
 */
final class XmlEncoding {

    /** The UTF-8 byte order mark. */
    private static final byte[] UTF_8_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** How many bytes of the beginning of a document the declaration is looked for in. */
    private static final int PROLOG_BYTES = 256;

    private static final Pattern DECLARATION =
            Pattern.compile("<\\?xml\\s[^?>]*?encoding\\s*=\\s*[\"']([^\"']+)[\"']");

    private XmlEncoding() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns what is wrong with the encoding of a document, if anything is.
     *
     * @param xml the bytes of the document
     * @return the fault in English, or an empty optional if the bytes spell what they
     *         declare
     */
    static Optional<String> problem(byte[] xml) {
        Charset mark = byteOrderMark(xml);
        Charset shape = mark != null ? mark : withoutMark(xml);
        String declared = declaration(xml, shape);
        if (shape != null) {
            String how = mark != null ? "begins with a byte order mark of " : "is written in ";
            if (declared != null && !agrees(shape, declared)) {
                return Optional.of("the document " + how + shape.name()
                        + " and declares the encoding " + declared);
            }
            return decodes(xml, mark != null && shape == StandardCharsets.UTF_8
                    ? UTF_8_MARK.length : 0, shape, shape.name());
        }
        if (declared == null) {
            return decodes(xml, 0, StandardCharsets.UTF_8, "UTF-8, which a document that"
                    + " declares no encoding and carries no byte order mark is");
        }
        Charset charset;
        try {
            charset = Charset.forName(declared);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return Optional.of("the document declares the encoding " + declared
                    + ", which this run has no decoder for");
        }
        return decodes(xml, 0, charset, declared);
    }

    /** Returns the charset a byte order mark names, or {@code null} if there is none. */
    private static Charset byteOrderMark(byte[] xml) {
        if (startsWith(xml, UTF_8_MARK)) {
            return StandardCharsets.UTF_8;
        }
        if (xml.length >= 2 && (xml[0] & 0xFF) == 0xFE && (xml[1] & 0xFF) == 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (xml.length >= 2 && (xml[0] & 0xFF) == 0xFF && (xml[1] & 0xFF) == 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        return null;
    }

    /**
     * Returns the charset a document without a byte order mark is plainly written in, or
     * {@code null} where its first bytes are the ASCII-compatible ones that every
     * single-byte encoding and UTF-8 share.
     */
    private static Charset withoutMark(byte[] xml) {
        if (xml.length >= 4 && xml[0] == 0x00 && xml[1] == 0x3C && xml[2] == 0x00) {
            return StandardCharsets.UTF_16BE;
        }
        if (xml.length >= 4 && xml[0] == 0x3C && xml[1] == 0x00 && xml[2] == 0x3F) {
            return StandardCharsets.UTF_16LE;
        }
        return null;
    }

    /**
     * Returns the encoding the XML declaration names, or {@code null} if the document
     * declares none.
     */
    private static String declaration(byte[] xml, Charset shape) {
        int length = Math.min(xml.length, PROLOG_BYTES);
        Charset reading = shape != null && shape != StandardCharsets.UTF_8
                ? shape : StandardCharsets.ISO_8859_1;
        String prolog = new String(xml, 0, length, reading);
        Matcher matcher = DECLARATION.matcher(prolog);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Tells whether a declared encoding name agrees with the shape the bytes have. The
     * UTF-16 names are one family: a document whose bytes are UTF-16 big endian may
     * declare {@code UTF-16}, which is what a byte order mark is for.
     */
    private static boolean agrees(Charset shape, String declared) {
        Charset named;
        try {
            named = Charset.forName(declared);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return false;
        }
        if (isUtf16(shape)) {
            return isUtf16(named);
        }
        return shape.equals(named);
    }

    private static boolean isUtf16(Charset charset) {
        return charset.name().startsWith("UTF-16");
    }

    /** Decodes the whole document and reports the first byte that does not belong. */
    private static Optional<String> decodes(byte[] xml, int from, Charset charset,
                                            String named) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(xml, from, xml.length - from));
            return Optional.empty();
        } catch (MalformedInputException e) {
            return Optional.of("the document declares the encoding " + named
                    + " and carries a byte sequence of " + e.getInputLength()
                    + " byte(s) that is not " + charset.name());
        } catch (CharacterCodingException e) {
            return Optional.of("the document declares the encoding " + named
                    + " and carries a byte sequence that no character of " + charset.name()
                    + " is written with");
        }
    }

    private static boolean startsWith(byte[] xml, byte[] prefix) {
        if (xml.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (xml[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
