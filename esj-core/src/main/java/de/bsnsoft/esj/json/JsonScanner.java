package de.bsnsoft.esj.json;

import java.nio.charset.StandardCharsets;

/**
 * The JSON scanner {@link EsjReader} walks a document with, one token at a time, over the
 * bytes of its UTF-8 encoding.
 *
 * <p>A stock parser cannot be used, and each reason is a requirement of the specification
 * rather than a preference:
 * <ul>
 *   <li>A byte sequence is read as UTF-8 and as nothing else (section 4.2, rule 2). A
 *       parser that guesses the encoding from the first bytes reads a document written in
 *       UTF-16 or UTF-32 without a byte order mark, which is valid UTF-8 and not a JSON text
 *       in it.</li>
 *   <li>A member name is judged before the value written under it (section 9.6: the first
 *       defect in the text wins). A parser that parses the value ahead while it hands over
 *       the name reports a broken value before a duplicate or undefined name.</li>
 *   <li>A member name that carries a lone surrogate is a finding with its name in the
 *       {@code subject}, and the same name in a subtree the reader walks past is no finding
 *       at all (sections 9.5 and 9.6). A parser that refuses such a name stops the read and
 *       hands nothing over.</li>
 *   <li>The limits of section 12.2 are counted in UTF-8 bytes and decided before a token is
 *       built, on every token of the document — a number in {@code values} as much as one in
 *       {@code extensions}. A parser counts in its own units, and only where it builds.</li>
 * </ul>
 *
 * <p>The scanner builds nothing it is not asked for. {@link #peek()} scans the next value
 * token: an object or an array is recognised by its opening bracket, and a string, a number
 * or a literal is scanned <em>whole</em>, so that a token that is not a complete JSON value —
 * {@code tru}, {@code 01}, {@code 1.}, a string with an escape JSON does not define — is
 * {@code ESJ-L1-JSON} wherever it stands, before anything is said about its type. What the
 * scan learns about a string — its length in UTF-8 bytes, the line endings section 6.8 will
 * fold, whether it carries a lone surrogate — is available before the string is built, so the
 * reader decides a bound and only then asks for the text. Nothing recurses here: the reader
 * walks containers with a stack of its own.
 *
 * <p>The bytes have been checked to be UTF-8 before a scanner is created (section 4.2), so
 * a byte below U+0080 is always the character it encodes and never part of a longer
 * sequence: a quotation mark, a backslash and every structural character can be recognised
 * byte by byte.
 *
 * <p>A defect of the JSON text is raised as {@link Malformed}, carrying the offset of the
 * token it was met in, counted in bytes from the start of the document (specification,
 * section 9.5).
 */
final class JsonScanner {

    /** What kind of JSON value a token begins. */
    enum Kind { OBJECT, ARRAY, STRING, NUMBER, TRUE, FALSE, NULL }

    /**
     * A byte sequence that is not a JSON text at this place. It carries the offset of the
     * token it was met in, counted in bytes from zero, and a phrase saying what is wrong,
     * which quotes at most one character of the document.
     */
    static final class Malformed extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** @serial the byte offset of the token the defect was met in */
        private final int offset;

        Malformed(String message, int offset) {
            super(message, null, false, false);
            this.offset = offset;
        }

        int offset() {
            return offset;
        }
    }

    private final byte[] bytes;
    private final int length;
    private int at;

    /** The kind of the token {@link #peek()} scanned and nothing consumed yet. */
    private Kind pending;
    /** Where the token last scanned begins. */
    private int start;
    /** Where the token last scanned ends, exclusive. */
    private int end;
    /** Whether the string last scanned carries an escape. */
    private boolean escaped;
    /** The length of the string last scanned, in UTF-8 bytes once decoded. */
    private long utf8;
    /** The number of CR LF pairs in the string last scanned, once decoded. */
    private long crlf;
    /** Whether the string last scanned carries a lone surrogate. */
    private boolean lone;
    /** Whether a member name was read and the colon after it not yet. */
    private boolean colon;

    JsonScanner(byte[] bytes, int length) {
        this.bytes = bytes;
        this.length = length;
    }

    /**
     * Returns the offset of the token last scanned, counted in bytes from the start of the
     * document: the opening quotation mark of a string or a name, the first character of a
     * number or a literal, the bracket of a container.
     *
     * @return the byte offset, from zero
     */
    int tokenStart() {
        return start;
    }

    /**
     * Returns the kind of the value that begins at the next token, scanning a string, a number
     * or a literal whole. The token stays where it is until it is consumed, read or opened.
     *
     * @return the kind of the value
     * @throws Malformed if no JSON value begins there, or the token is not a complete one
     */
    Kind peek() {
        if (pending != null) {
            return pending;
        }
        if (colon) {
            whitespace();
            if (at >= length || bytes[at] != ':') {
                throw new Malformed("a member name is followed by a colon", at);
            }
            at++;
            colon = false;
        }
        whitespace();
        start = at;
        if (at >= length) {
            throw new Malformed("the document ends where a value belongs", at);
        }
        switch (bytes[at]) {
            case '{' -> {
                end = at + 1;
                pending = Kind.OBJECT;
            }
            case '[' -> {
                end = at + 1;
                pending = Kind.ARRAY;
            }
            case '"' -> {
                scanString();
                pending = Kind.STRING;
            }
            default -> pending = word();
        }
        return pending;
    }

    /** Consumes the bracket of the object or array {@link #peek()} stands on. */
    void open() {
        Kind kind = peek();
        if (kind != Kind.OBJECT && kind != Kind.ARRAY) {
            throw new IllegalStateException("the scanner stands on " + kind);
        }
        at = end;
        pending = null;
    }

    /** Steps past the string, number or literal {@link #peek()} scanned, building nothing. */
    void consume() {
        Kind kind = peek();
        if (kind == Kind.OBJECT || kind == Kind.ARRAY) {
            throw new IllegalStateException("the scanner stands on " + kind);
        }
        at = end;
        pending = null;
    }

    /**
     * Returns the length of the token {@link #peek()} scanned, in bytes as the document spells
     * it. For a number this is the spelling section 12.2 bounds: a JSON number is ASCII, so
     * its characters are its UTF-8 bytes.
     *
     * @return the length of the token in bytes
     */
    long tokenLength() {
        return (long) end - start;
    }

    /**
     * Returns the length of the string or name last scanned, in bytes of its UTF-8 encoding
     * once its escapes are decoded. A lone surrogate counts as the three bytes its
     * replacement would take; a string carrying one is refused before the count decides
     * anything.
     *
     * @return the decoded length in UTF-8 bytes
     */
    long utf8Length() {
        return utf8;
    }

    /**
     * Returns the length of the string last scanned once its line endings are normalized
     * (specification, section 6.8): every CR LF pair is one byte shorter, a lone CR as long.
     *
     * @return the normalized length in UTF-8 bytes
     */
    long normalizedUtf8Length() {
        return utf8 - crlf;
    }

    /**
     * Tells whether the string or name last scanned carries a surrogate that is not part of a
     * pair.
     *
     * @return {@code true} if it does
     */
    boolean loneSurrogate() {
        return lone;
    }

    /**
     * Builds the string {@link #peek()} scanned and steps past it.
     *
     * @return the string, its escapes decoded and nothing else changed
     */
    String text() {
        if (peek() != Kind.STRING) {
            throw new IllegalStateException("the scanner stands on " + pending);
        }
        String text = build(start, end);
        at = end;
        pending = null;
        return text;
    }

    /**
     * Returns the number {@link #peek()} scanned as the document spells it and steps past it.
     *
     * @return the number token
     */
    String numberText() {
        if (peek() != Kind.NUMBER) {
            throw new IllegalStateException("the scanner stands on " + pending);
        }
        String text = new String(bytes, start, end - start, StandardCharsets.ISO_8859_1);
        at = end;
        pending = null;
        return text;
    }

    /**
     * Returns at most {@code limit} characters of the number {@link #peek()} scanned, without
     * consuming it, for a message that names the number.
     *
     * @param limit the most characters to return
     * @return the beginning of the number token
     */
    String numberExcerpt(int limit) {
        return new String(bytes, start, Math.min(end - start, limit),
                StandardCharsets.ISO_8859_1);
    }

    /**
     * Stands on the name of the next member of an open object, or consumes the brace that
     * closes it.
     *
     * @param first whether no member of the object has been read yet
     * @return {@code true} where a member follows, {@code false} where the object closed
     * @throws Malformed if neither a member nor the end of the object follows
     */
    boolean nextMember(boolean first) {
        whitespace();
        if (at < length && bytes[at] == '}') {
            at++;
            return false;
        }
        if (!first) {
            if (at >= length || bytes[at] != ',') {
                throw new Malformed("a member is followed by a comma or by the end of the object",
                        at);
            }
            at++;
            whitespace();
        }
        if (at >= length || bytes[at] != '"') {
            throw new Malformed("a member name is a JSON string", at);
        }
        return true;
    }

    /**
     * Stands on the next element of an open array, or consumes the bracket that closes it.
     *
     * @param first whether no element of the array has been read yet
     * @return {@code true} where an element follows, {@code false} where the array closed
     * @throws Malformed if neither an element nor the end of the array follows
     */
    boolean nextElement(boolean first) {
        whitespace();
        if (at < length && bytes[at] == ']') {
            at++;
            return false;
        }
        if (!first) {
            if (at >= length || bytes[at] != ',') {
                throw new Malformed(
                        "an element is followed by a comma or by the end of the array", at);
            }
            at++;
        }
        return true;
    }

    /**
     * Scans the member name {@link #nextMember(boolean)} stands on and steps past it without
     * building it. What the scan learns — its length in UTF-8 bytes, whether it carries a lone
     * surrogate, where it begins — is available until the next token is scanned, so that the
     * reader measures the name against its bound before it holds a character of it, and builds
     * it with {@link #nameText()} only where it is within. The colon after it is required by
     * the next {@link #peek()}, so that whatever is wrong with the name is said before
     * whatever is wrong with what follows it (specification, section 9.6).
     *
     * @throws Malformed if the name is not a JSON string
     */
    void scanName() {
        scanString();
        at = end;
        colon = true;
    }

    /**
     * Builds the member name {@link #scanName()} scanned last.
     *
     * @return the name, its escapes decoded
     */
    String nameText() {
        return build(start, end);
    }

    /**
     * Requires that nothing but whitespace follows the object the document consists of.
     *
     * @throws Malformed if something does
     */
    void end() {
        whitespace();
        if (at < length) {
            throw new Malformed("the document carries content after the object that closes it",
                    at);
        }
    }

    private void whitespace() {
        while (at < length) {
            byte b = bytes[at];
            if (b != ' ' && b != '\t' && b != '\n' && b != '\r') {
                return;
            }
            at++;
        }
    }

    /**
     * Scans the string that begins at the current byte, which is its opening quotation mark,
     * and records what the reader needs to know about it before it is built.
     */
    private void scanString() {
        start = at;
        int i = at + 1;
        boolean anyEscape = false;
        long size = 0;
        long pairs = 0;
        boolean loneFound = false;
        boolean afterCr = false;
        while (true) {
            if (i >= length) {
                throw new Malformed("a string is not closed before the end of the document",
                        start);
            }
            int b = bytes[i] & 0xFF;
            if (b == '"') {
                i++;
                break;
            }
            if (b != '\\') {
                if (b < 0x20) {
                    throw new Malformed("a string carries an unescaped control character",
                            start);
                }
                size++;
                afterCr = false;
                i++;
                continue;
            }
            anyEscape = true;
            if (i + 1 >= length) {
                throw new Malformed("a string is not closed before the end of the document",
                        start);
            }
            int unit;
            switch (bytes[i + 1]) {
                case '"' -> unit = '"';
                case '\\' -> unit = '\\';
                case '/' -> unit = '/';
                case 'b' -> unit = '\b';
                case 'f' -> unit = '\f';
                case 'n' -> unit = '\n';
                case 'r' -> unit = '\r';
                case 't' -> unit = '\t';
                case 'u' -> {
                    unit = hex4(i + 2);
                    if (unit < 0) {
                        throw new Malformed("a \\u escape carries four hexadecimal digits",
                                start);
                    }
                    i += 4;
                }
                default -> throw new Malformed(
                        "a string carries an escape sequence JSON does not define", start);
            }
            i += 2;
            if (Character.isHighSurrogate((char) unit)) {
                int low = i + 6 <= length && bytes[i] == '\\' && bytes[i + 1] == 'u'
                        ? hex4(i + 2) : -1;
                if (low >= 0 && Character.isLowSurrogate((char) low)) {
                    size += 4;
                    i += 6;
                } else {
                    size += 3;
                    loneFound = true;
                }
                afterCr = false;
                continue;
            }
            if (Character.isLowSurrogate((char) unit)) {
                size += 3;
                loneFound = true;
                afterCr = false;
                continue;
            }
            size += unit < 0x80 ? 1 : unit < 0x800 ? 2 : 3;
            if (unit == '\n' && afterCr) {
                pairs++;
            }
            afterCr = unit == '\r';
        }
        end = i;
        escaped = anyEscape;
        utf8 = size;
        crlf = pairs;
        lone = loneFound;
    }

    /**
     * Builds the string between {@code from}, its opening quotation mark, and {@code to},
     * the byte after its closing one. The runs between escapes are whole UTF-8 sequences,
     * because a backslash never occurs inside a multi-byte sequence.
     */
    private String build(int from, int to) {
        int first = from + 1;
        int last = to - 1;
        if (!escaped) {
            return new String(bytes, first, last - first, StandardCharsets.UTF_8);
        }
        StringBuilder out = new StringBuilder(Math.min(last - first, 1 << 16));
        int run = first;
        int i = first;
        while (i < last) {
            if (bytes[i] != '\\') {
                i++;
                continue;
            }
            if (i > run) {
                out.append(new String(bytes, run, i - run, StandardCharsets.UTF_8));
            }
            switch (bytes[i + 1]) {
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    out.append((char) hex4(i + 2));
                    i += 4;
                }
                default -> out.append((char) bytes[i + 1]);
            }
            i += 2;
            run = i;
        }
        if (last > run) {
            out.append(new String(bytes, run, last - run, StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    /** Returns the value of four hexadecimal digits at {@code from}, or {@code -1}. */
    private int hex4(int from) {
        if (from + 4 > length) {
            return -1;
        }
        int value = 0;
        for (int i = from; i < from + 4; i++) {
            int digit = Character.digit((char) (bytes[i] & 0xFF), 16);
            if (digit < 0 || bytes[i] < 0) {
                return -1;
            }
            value = (value << 4) | digit;
        }
        return value;
    }

    /**
     * Scans a token that is neither a string nor a container: a JSON number, read as far as
     * its grammar reaches, or one of the three literals, which a letter, a digit, {@code _}
     * or {@code $} straight after it would make another word. A number that breaks its
     * grammar before it is complete — {@code 01}, {@code 1.}, {@code -}, {@code 1e} — and a
     * word that is no literal — {@code tru}, {@code truex} — are malformed at their first
     * character. What follows a complete number is the next token's business.
     */
    private Kind word() {
        byte first = bytes[at];
        if (first == '-' || (first >= '0' && first <= '9')) {
            end = number(at);
            return Kind.NUMBER;
        }
        if (first == 't' || first == 'f' || first == 'n') {
            String literal = first == 't' ? "true" : first == 'f' ? "false" : "null";
            int after = at + literal.length();
            if (after > length || !matches(literal)
                    || (after < length && wordCharacter(bytes[after]))) {
                throw new Malformed("the token is not true, false or null", at);
            }
            end = after;
            return first == 't' ? Kind.TRUE : first == 'f' ? Kind.FALSE : Kind.NULL;
        }
        throw new Malformed("a JSON value does not begin with " + character(at), at);
    }

    private boolean matches(String literal) {
        for (int k = 0; k < literal.length(); k++) {
            if (bytes[at + k] != literal.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads the JSON number that begins at {@code from} as far as its grammar reaches (RFC
     * 8259) and returns where it ends.
     */
    private int number(int from) {
        int i = from;
        if (bytes[i] == '-') {
            i++;
        }
        int integer = i;
        i = digits(i);
        if (i == integer) {
            throw new Malformed("a JSON number carries at least one digit here", from);
        }
        if (i - integer > 1 && bytes[integer] == '0') {
            throw new Malformed("a JSON number carries no leading zero", from);
        }
        if (i < length && bytes[i] == '.') {
            int fraction = i + 1;
            i = digits(fraction);
            if (i == fraction) {
                throw new Malformed("a fraction carries at least one digit", from);
            }
        }
        if (i < length && (bytes[i] == 'e' || bytes[i] == 'E')) {
            i++;
            if (i < length && (bytes[i] == '+' || bytes[i] == '-')) {
                i++;
            }
            int exponent = i;
            i = digits(exponent);
            if (i == exponent) {
                throw new Malformed("an exponent carries at least one digit", from);
            }
        }
        return i;
    }

    private int digits(int from) {
        int i = from;
        while (i < length && bytes[i] >= '0' && bytes[i] <= '9') {
            i++;
        }
        return i;
    }

    /** Tells whether a byte continues a word: an ASCII letter or digit, {@code _} or {@code $}. */
    private static boolean wordCharacter(byte b) {
        return (b >= '0' && b <= '9') || (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z')
                || b == '_' || b == '$';
    }

    /**
     * Names the character at a byte offset for a message: quoted where it is printable ASCII
     * and as {@code U+XXXX} otherwise, so that a control character cannot travel into a log
     * line through a diagnostic (specification, section 12.6).
     */
    private String character(int offset) {
        int b = bytes[offset] & 0xFF;
        if (b >= 0x20 && b < 0x7F) {
            return "'" + (char) b + "'";
        }
        int codePoint = b < 0x80 ? b : new String(bytes, offset,
                Math.min(4, length - offset), StandardCharsets.UTF_8).codePointAt(0);
        return String.format("U+%04X", codePoint);
    }
}
