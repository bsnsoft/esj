package de.bsnsoft.esj.bindings;

import java.util.Arrays;

/**
 * Collects the UTF-8 bytes of a document as they are produced, within a bound.
 *
 * <p>A writer that builds the whole output as characters, turns it into a string and then
 * encodes that string holds three copies of a large invoice at once — and can only find
 * out that the output is too large by producing all of it first. This holds one growing
 * array of bytes instead, and refuses the moment the bound is passed rather than at the
 * end, so the heap this class needs is the bound and not the document. It is not the heap
 * the run needs: {@link SyntaxWriter} builds the element tree of the whole document before
 * it serializes it, and its <em>Cost</em> section is where a caller sizing a process reads
 * what that costs.
 *
 * <p>The encoding is done here rather than by a {@code CharsetEncoder} because the input
 * arrives one small string at a time and a surrogate pair may not be split across two of
 * them: the two halves of a pair always reach this class inside one string, since every
 * string handed to it is a name, an attribute value or the content of one element.
 *
 * <p>What it does with a code unit that is no character of its own is what a
 * {@code CharsetEncoder} with its default action does: a surrogate that is not half of a
 * well-formed pair is written as {@code U+FFFD}, so that the bytes are UTF-8 whatever
 * arrives. That is a backstop and not the answer: the writer takes every character XML
 * 1.0 cannot carry — a lone surrogate among them — out of the value before it reaches
 * this class, and says so in its report. See {@link XmlCharacters}.
 */
final class Utf8Sink {

    private static final int INITIAL_CAPACITY = 64 * 1024;

    /** What a code unit that is no character of its own is encoded as: {@code U+FFFD}. */
    private static final char REPLACEMENT = '�';

    private final long bound;
    private byte[] bytes = new byte[INITIAL_CAPACITY];
    private int size;

    /**
     * Creates a sink.
     *
     * @param bound the bytes the output may take
     */
    Utf8Sink(long bound) {
        this.bound = bound;
    }

    /**
     * Appends one character of the basic latin block, which is one byte. It is for the
     * punctuation of the syntax — the angle brackets, the quotation marks, the line
     * breaks — and refuses anything else, because a character outside that block reaches
     * this class as part of a string and may be half of a surrogate pair.
     *
     * @throws IllegalArgumentException if the character is not a basic latin one
     */
    Utf8Sink append(char c) {
        if (c >= 0x80) {
            throw new IllegalArgumentException("a character outside the basic latin block"
                    + " is written as part of a string, not on its own");
        }
        room(1);
        bytes[size++] = (byte) c;
        return this;
    }

    /**
     * Appends the one character a string carries at an index, encoded as UTF-8.
     *
     * @param text  the string
     * @param index where the character starts
     * @return how many further code units it took: one for a surrogate pair, zero
     *         otherwise, so that a loop over the string adds it to its own index
     */
    int appendAt(String text, int index) {
        return encode(text, index);
    }

    /** Appends a string as UTF-8. */
    Utf8Sink append(String text) {
        for (int i = 0; i < text.length(); i++) {
            i += encode(text, i);
        }
        return this;
    }

    /** Appends a string a number of times, for the indentation. */
    void repeat(String text, int times) {
        for (int i = 0; i < times; i++) {
            append(text);
        }
    }

    /** Returns the bytes written so far. */
    byte[] toByteArray() {
        return size == bytes.length ? bytes : Arrays.copyOf(bytes, size);
    }

    /**
     * Encodes the character at one index and returns how many further code units it took:
     * one for a surrogate pair, zero otherwise.
     */
    private int encode(String text, int index) {
        char c = text.charAt(index);
        if (c < 0x80) {
            room(1);
            bytes[size++] = (byte) c;
            return 0;
        }
        if (c < 0x800) {
            room(2);
            bytes[size++] = (byte) (0xC0 | (c >> 6));
            bytes[size++] = (byte) (0x80 | (c & 0x3F));
            return 0;
        }
        if (Character.isHighSurrogate(c) && index + 1 < text.length()
                && Character.isLowSurrogate(text.charAt(index + 1))) {
            int point = Character.toCodePoint(c, text.charAt(index + 1));
            room(4);
            bytes[size++] = (byte) (0xF0 | (point >> 18));
            bytes[size++] = (byte) (0x80 | ((point >> 12) & 0x3F));
            bytes[size++] = (byte) (0x80 | ((point >> 6) & 0x3F));
            bytes[size++] = (byte) (0x80 | (point & 0x3F));
            return 1;
        }
        char point = Character.isSurrogate(c) ? REPLACEMENT : c;
        room(3);
        bytes[size++] = (byte) (0xE0 | (point >> 12));
        bytes[size++] = (byte) (0x80 | ((point >> 6) & 0x3F));
        bytes[size++] = (byte) (0x80 | (point & 0x3F));
        return 0;
    }

    /** Makes room for a number of bytes, and refuses where the bound is passed. */
    private void room(int more) {
        if (size + (long) more > bound) {
            throw new BindingLimitException("the document is longer than the "
                    + bound + " bytes this run was given, so it was not written");
        }
        if (size + more <= bytes.length) {
            return;
        }
        int capacity = bytes.length;
        while (capacity < size + more) {
            capacity = capacity > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : capacity * 2;
        }
        // Doubling past the bound would hold an array of twice the output nobody can fill.
        // The bound is what the run may write, so it is also the largest array worth having.
        bytes = Arrays.copyOf(bytes,
                (int) Math.min(capacity, Math.min(bound, Integer.MAX_VALUE)));
    }
}
