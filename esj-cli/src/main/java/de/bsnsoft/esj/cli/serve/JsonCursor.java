package de.bsnsoft.esj.cli.serve;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A JSON text read one token at a time from a stream, holding nothing it is not asked for.
 *
 * <p>This is how the server reads what a child wrote and what a client sent without the
 * length of either reaching its heap. A string is not read when its token is: the caller
 * asks for at most so many of its characters ({@link #text(int)}), streams it to a file
 * ({@link #textTo(OutputStream, long)}), or moves on, and the cursor skips the rest of it
 * byte by byte. What is held at any moment is a buffer of the stream, the stack of the
 * containers the cursor is in, a member name of at most {@link #MAX_NAME} characters and a
 * number of at most {@link #MAX_NUMBER}; a member name or a number past those, a nesting
 * past {@link #MAX_DEPTH}, and every text that is not JSON (RFC 8259) in UTF-8 are refused
 * with {@link Malformed}.
 */
final class JsonCursor {

    /** What the cursor stands on. */
    enum Token {
        /** An object begins. */
        BEGIN_OBJECT,
        /** The object ends. */
        END_OBJECT,
        /** An array begins. */
        BEGIN_ARRAY,
        /** The array ends. */
        END_ARRAY,
        /** The name of a member, {@link #name()}; its value is the next token. */
        NAME,
        /** A string value, read with {@link #text(int)} or {@link #textTo}, or skipped. */
        STRING,
        /** A number, {@link #number()}. */
        NUMBER,
        /** {@code true}. */
        TRUE,
        /** {@code false}. */
        FALSE,
        /** {@code null}. */
        NULL,
        /** The text has ended, after its one value. */
        END
    }

    /** A text that is not JSON, or not within the bounds of this cursor. */
    static final class Malformed extends IOException {
        private static final long serialVersionUID = 1L;

        Malformed(String message) {
            super(message);
        }
    }

    /**
     * Part of a string: its first characters and how many it has.
     *
     * @param prefix the characters that were kept, every one of them where it is complete
     * @param length the number of characters of the whole string, in UTF-16 units
     */
    record Text(String prefix, long length) {

        /** Tells whether the whole string was kept. */
        boolean complete() {
            return prefix.length() == length;
        }

        /** Returns the string, or its prefix with how much was left out. */
        String cut() {
            return complete() ? prefix : prefix + " … (" + (length - prefix.length())
                    + " more characters)";
        }
    }

    /** The deepest nesting a text may have. */
    static final int MAX_DEPTH = Jv.MAX_DEPTH;

    /** The longest member name, in characters. */
    static final int MAX_NAME = 4096;

    /** The longest number, in characters. */
    static final int MAX_NUMBER = 64;

    private static final int OBJECT_OPEN = 0;
    private static final int AFTER_NAME = 1;
    private static final int AFTER_VALUE = 2;

    private final InputStream in;
    private final byte[] buffer = new byte[16 * 1024];
    private int position;
    private int limit;
    private long offset;

    private final boolean[] object = new boolean[MAX_DEPTH + 1];
    private final int[] stage = new int[MAX_DEPTH + 1];
    private int depth;

    private Token current;
    private boolean pending;
    private String name;
    private String number;

    /**
     * Reads a JSON text from a stream.
     *
     * @param in the bytes, UTF-8
     */
    JsonCursor(InputStream in) {
        this.in = in;
    }

    /** Returns the number of bytes read so far. */
    long offset() {
        return offset - (limit - position);
    }

    /** Returns the token the cursor stands on. */
    Token current() {
        return current;
    }

    /** Returns how deep in containers the cursor stands: 0 outside every one. */
    int depth() {
        return depth;
    }

    /** Returns the member name the cursor stands on. */
    String name() {
        if (current != Token.NAME) {
            throw new IllegalStateException("not on a name but on " + current);
        }
        return name;
    }

    /** Returns the number the cursor stands on, as it was written. */
    String number() {
        if (current != Token.NUMBER) {
            throw new IllegalStateException("not on a number but on " + current);
        }
        return number;
    }

    /**
     * Moves to the next token, past what is left of a string the caller did not read.
     *
     * @return the token
     * @throws IOException where the stream fails, or the text is not JSON
     */
    Token next() throws IOException {
        if (pending) {
            pending = false;
            string(null, 0, null, 0);
        }
        current = advance();
        return current;
    }

    private Token advance() throws IOException {
        if (depth == 0) {
            whitespace();
            if (stage[0] == AFTER_VALUE) {
                if (peek() >= 0) {
                    throw new Malformed("the text carries more than one JSON value");
                }
                return Token.END;
            }
            return value();
        }
        if (object[depth]) {
            if (stage[depth] == AFTER_NAME) {
                whitespace();
                return value();
            }
            whitespace();
            int c = peek();
            if (c == '}') {
                position++;
                depth--;
                return Token.END_OBJECT;
            }
            if (stage[depth] == AFTER_VALUE) {
                if (c != ',') {
                    throw new Malformed("a member is followed by a comma or the end of its"
                            + " object, at byte " + offset());
                }
                position++;
                whitespace();
                c = peek();
            }
            if (c != '"') {
                throw new Malformed("a member begins with its name in quotation marks, at"
                        + " byte " + offset());
            }
            position++;
            StringBuilder text = new StringBuilder();
            long length = string(text, MAX_NAME, null, 0);
            if (length > MAX_NAME) {
                throw new Malformed("a member name is longer than " + MAX_NAME
                        + " characters");
            }
            whitespace();
            if (peek() != ':') {
                throw new Malformed("a member name is followed by a colon, at byte "
                        + offset());
            }
            position++;
            name = text.toString();
            stage[depth] = AFTER_NAME;
            return Token.NAME;
        }
        whitespace();
        int c = peek();
        if (c == ']') {
            position++;
            depth--;
            return Token.END_ARRAY;
        }
        if (stage[depth] == AFTER_VALUE) {
            if (c != ',') {
                throw new Malformed("an item is followed by a comma or the end of its array,"
                        + " at byte " + offset());
            }
            position++;
            whitespace();
        }
        return value();
    }

    private Token value() throws IOException {
        int c = peek();
        if (c < 0) {
            throw new Malformed("the text ends where a value belongs");
        }
        stage[depth] = AFTER_VALUE;
        switch (c) {
            case '{', '[' -> {
                if (depth == MAX_DEPTH) {
                    throw new Malformed("the text is nested deeper than " + MAX_DEPTH
                            + " levels");
                }
                position++;
                depth++;
                object[depth] = c == '{';
                stage[depth] = OBJECT_OPEN;
                return c == '{' ? Token.BEGIN_OBJECT : Token.BEGIN_ARRAY;
            }
            case '"' -> {
                position++;
                pending = true;
                return Token.STRING;
            }
            case 't' -> {
                literal("true");
                return Token.TRUE;
            }
            case 'f' -> {
                literal("false");
                return Token.FALSE;
            }
            case 'n' -> {
                literal("null");
                return Token.NULL;
            }
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    number = numberText();
                    return Token.NUMBER;
                }
                throw new Malformed("no JSON value begins with " + describe(c) + ", at byte "
                        + offset());
            }
        }
    }

    /**
     * Reads the string the cursor stands on, keeping at most so many of its characters.
     *
     * @param max the most characters to keep
     * @return what was kept and the length of the whole string
     * @throws IOException where the stream fails, or the string is not JSON
     */
    Text text(int max) throws IOException {
        if (current != Token.STRING || !pending) {
            throw new IllegalStateException("not on an unread string but on " + current);
        }
        pending = false;
        StringBuilder text = new StringBuilder(Math.min(max, 256));
        long length = string(text, max, null, 0);
        return new Text(text.toString(), length);
    }

    /**
     * Writes the string the cursor stands on to a stream, as UTF-8, up to a number of
     * characters, and reads past the rest.
     *
     * @param out the stream
     * @param max the most characters to write
     * @return the length of the whole string, in characters
     * @throws IOException where a stream fails, or the string is not JSON
     */
    long textTo(OutputStream out, long max) throws IOException {
        if (current != Token.STRING || !pending) {
            throw new IllegalStateException("not on an unread string but on " + current);
        }
        pending = false;
        return string(null, 0, out, max);
    }

    /**
     * Skips the value the cursor stands on: a string, or a container with everything in it.
     * On a name, the value of the member is skipped.
     *
     * @throws IOException where the stream fails, or the text is not JSON
     */
    void skip() throws IOException {
        if (current == Token.NAME) {
            next();
        }
        if (current == Token.BEGIN_OBJECT || current == Token.BEGIN_ARRAY) {
            int level = depth;
            while (depth >= level) {
                if (next() == Token.END) {
                    throw new Malformed("the text ends inside a container");
                }
            }
        } else if (current == Token.STRING && pending) {
            pending = false;
            string(null, 0, null, 0);
        }
    }

    /** The prefix being kept of the string being read, or {@code null}. */
    private StringBuilder keep;
    private int keepMax;
    private boolean keeping;
    /** The stream the string being read is written to, or {@code null}. */
    private OutputStream out;
    private long outMax;
    private boolean writing;
    private long length;

    /**
     * Reads a string up to its closing quotation mark. A pair of surrogates written as two
     * escapes is one character, as it is when it is written as UTF-8.
     *
     * @return its length in UTF-16 units
     */
    private long string(StringBuilder keepInto, int max, OutputStream writeTo, long writeMax)
            throws IOException {
        keep = keepInto;
        keepMax = max;
        keeping = keepInto != null;
        out = writeTo;
        outMax = writeMax;
        writing = writeTo != null;
        length = 0;
        int held = -1;
        try {
            while (true) {
                int b = read();
                if (b < 0) {
                    throw new Malformed("the text ends inside a string");
                }
                if (b == '"') {
                    if (held >= 0) {
                        emit(held);
                    }
                    return length;
                }
                int codePoint;
                if (b == '\\') {
                    int e = read();
                    codePoint = switch (e) {
                        case '"', '\\', '/' -> e;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> hex4();
                        default -> throw new Malformed("a string carries an escape JSON does"
                                + " not define, at byte " + offset());
                    };
                } else if (b < 0x20) {
                    throw new Malformed("a string carries a control character that is not"
                            + " escaped, at byte " + offset());
                } else if (b < 0x80) {
                    codePoint = b;
                } else {
                    codePoint = utf8(b);
                }
                if (held >= 0) {
                    if (Character.isLowSurrogate((char) codePoint) && codePoint <= 0xFFFF) {
                        codePoint = Character.toCodePoint((char) held, (char) codePoint);
                    } else {
                        emit(held);
                    }
                    held = -1;
                }
                if (codePoint <= 0xFFFF && Character.isHighSurrogate((char) codePoint)) {
                    held = codePoint;
                } else {
                    emit(codePoint);
                }
            }
        } finally {
            keep = null;
            out = null;
        }
    }

    /** Takes one character of the string being read. */
    private void emit(int codePoint) throws IOException {
        int units = Character.charCount(codePoint);
        // A prefix stops at the first character that does not fit, so that what is kept is
        // the beginning of the string and a surrogate pair is never split.
        keeping = keeping && keep.length() + units <= keepMax;
        if (keeping) {
            keep.appendCodePoint(codePoint);
        }
        writing = writing && length + units <= outMax;
        if (writing) {
            write(out, codePoint);
        }
        length += units;
    }

    private static void write(OutputStream out, int codePoint) throws IOException {
        if (codePoint < 0x80) {
            out.write(codePoint);
        } else if (codePoint < 0x800) {
            out.write(0xC0 | (codePoint >> 6));
            out.write(0x80 | (codePoint & 0x3F));
        } else if (codePoint < 0x10000) {
            out.write(0xE0 | (codePoint >> 12));
            out.write(0x80 | ((codePoint >> 6) & 0x3F));
            out.write(0x80 | (codePoint & 0x3F));
        } else {
            out.write(0xF0 | (codePoint >> 18));
            out.write(0x80 | ((codePoint >> 12) & 0x3F));
            out.write(0x80 | ((codePoint >> 6) & 0x3F));
            out.write(0x80 | (codePoint & 0x3F));
        }
    }

    private int hex4() throws IOException {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int c = read();
            int digit = Character.digit(c < 0 ? -1 : c, 16);
            if (c < 0 || c > 0x7F || digit < 0) {
                throw new Malformed("a \\u escape is followed by four hexadecimal digits, at"
                        + " byte " + offset());
            }
            value = value * 16 + digit;
        }
        return value;
    }

    /** Decodes the code point a lead byte of UTF-8 begins. */
    private int utf8(int lead) throws IOException {
        int count;
        int codePoint;
        int min;
        if (lead >= 0xC2 && lead <= 0xDF) {
            count = 1;
            codePoint = lead & 0x1F;
            min = 0x80;
        } else if (lead >= 0xE0 && lead <= 0xEF) {
            count = 2;
            codePoint = lead & 0x0F;
            min = 0x800;
        } else if (lead >= 0xF0 && lead <= 0xF4) {
            count = 3;
            codePoint = lead & 0x07;
            min = 0x10000;
        } else {
            throw new Malformed("the text is not UTF-8, at byte " + offset());
        }
        for (int i = 0; i < count; i++) {
            int b = read();
            if (b < 0 || (b & 0xC0) != 0x80) {
                throw new Malformed("the text is not UTF-8, at byte " + offset());
            }
            codePoint = (codePoint << 6) | (b & 0x3F);
        }
        if (codePoint < min || codePoint > 0x10FFFF
                || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new Malformed("the text is not UTF-8, at byte " + offset());
        }
        return codePoint;
    }

    private String numberText() throws IOException {
        StringBuilder text = new StringBuilder();
        int c = peek();
        if (c == '-') {
            text.append('-');
            position++;
        }
        c = peek();
        if (c == '0') {
            text.append('0');
            position++;
        } else if (c >= '1' && c <= '9') {
            digits(text);
        } else {
            throw new Malformed("a number has digits, at byte " + offset());
        }
        if (peek() == '.') {
            text.append('.');
            position++;
            if (!isDigit(peek())) {
                throw new Malformed("a decimal point is followed by digits, at byte "
                        + offset());
            }
            digits(text);
        }
        c = peek();
        if (c == 'e' || c == 'E') {
            text.append((char) c);
            position++;
            c = peek();
            if (c == '+' || c == '-') {
                text.append((char) c);
                position++;
            }
            if (!isDigit(peek())) {
                throw new Malformed("an exponent has digits, at byte " + offset());
            }
            digits(text);
        }
        return text.toString();
    }

    private void digits(StringBuilder text) throws IOException {
        while (isDigit(peek())) {
            if (text.length() >= MAX_NUMBER) {
                throw new Malformed("a number is longer than " + MAX_NUMBER + " characters");
            }
            text.append((char) read());
        }
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private void literal(String word) throws IOException {
        for (int i = 0; i < word.length(); i++) {
            if (read() != word.charAt(i)) {
                throw new Malformed("no JSON value begins like this, at byte " + offset());
            }
        }
    }

    private void whitespace() throws IOException {
        while (true) {
            int c = peek();
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                position++;
            } else {
                return;
            }
        }
    }

    private int peek() throws IOException {
        if (position == limit && !fill()) {
            return -1;
        }
        return buffer[position] & 0xFF;
    }

    private int read() throws IOException {
        if (position == limit && !fill()) {
            return -1;
        }
        return buffer[position++] & 0xFF;
    }

    private boolean fill() throws IOException {
        int read = 0;
        while (read == 0) {
            read = in.read(buffer, 0, buffer.length);
        }
        if (read < 0) {
            return false;
        }
        position = 0;
        limit = read;
        offset += read;
        return true;
    }

    private static String describe(int c) {
        return c >= 0x21 && c <= 0x7E ? "'" + (char) c + "'"
                : String.format(java.util.Locale.ROOT, "the byte 0x%02X", c);
    }
}
