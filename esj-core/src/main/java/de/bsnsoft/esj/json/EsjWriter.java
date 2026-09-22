package de.bsnsoft.esj.json;

import de.bsnsoft.esj.Esj;
import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.validate.FindingCode;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Writes a {@link SemanticDocument} as JSON, in the canonical form of the specification,
 * section 7 or in the pretty form of section 7.7.
 *
 * <p>Both forms carry the same content in the same order; they differ only in layout. The
 * canonical form is UTF-8 without a byte order mark, without insignificant whitespace and
 * without a trailing newline, and it is the only form the digests of section 8 are taken
 * over. The pretty form indents by two spaces, writes one member and one array element
 * per line, ends every line with LF and closes the file with one.
 *
 * <p>The escaping is this implementation's own (specification, section 7.5). No JSON
 * library decides it, because a library's defaults — an escaped solidus, an escaped
 * non-ASCII code point — would change the bytes and therefore both digests.
 *
 * <p>A JSON number inside {@code extensions} is written in the canonical decimal form of
 * section 7.6, rule 2 in both forms. The pretty form is allowed to keep the spelling it
 * read; this writer does not keep it, because a pretty file that is reproducible from the
 * content alone is worth more than one that remembers its input.
 */
public final class EsjWriter {

    private static final int INITIAL_SIZE = 4096;

    private final Style style;

    private EsjWriter(Style style) {
        this.style = style;
    }

    /**
     * Returns a writer for the canonical form of the specification, section 7.
     *
     * @return the canonical writer
     */
    public static EsjWriter canonical() {
        return new EsjWriter(Style.CANONICAL);
    }

    /**
     * Returns a writer for the pretty form of the specification, section 7.7.
     *
     * @return the pretty writer
     */
    public static EsjWriter pretty() {
        return new EsjWriter(Style.PRETTY);
    }

    /**
     * Returns a writer for one of the two forms.
     *
     * @param style the form to write
     * @return the writer
     * @throws NullPointerException if {@code style} is {@code null}
     */
    public static EsjWriter of(Style style) {
        return new EsjWriter(Objects.requireNonNull(style, "style"));
    }

    /**
     * Returns the form this writer writes.
     *
     * @return the style
     */
    public Style style() {
        return style;
    }

    /**
     * Writes a document to a byte stream. The stream is not closed.
     *
     * @param document the document to write
     * @param out      the stream to write to
     * @throws EsjFormatException   if a string of the document carries an unpaired
     *                              surrogate, or a number inside {@code extensions} has no
     *                              canonical decimal form of at most 64 characters
     * @throws UncheckedIOException if the stream fails
     * @throws NullPointerException if an argument is {@code null}
     */
    public void write(SemanticDocument document, OutputStream out) {
        Objects.requireNonNull(out, "out");
        try {
            out.write(toBytes(document));
        } catch (IOException e) {
            throw new UncheckedIOException("writing the document failed", e);
        }
    }

    /**
     * Writes a document to a character stream. The stream is not closed. The bytes of the
     * specification are produced first and then decoded, so that the result is the same
     * text either way; a caller that needs the exact bytes uses
     * {@link #write(SemanticDocument, OutputStream)} or {@link #toBytes(SemanticDocument)}.
     *
     * @param document the document to write
     * @param out      the stream to write to
     * @throws EsjFormatException   if a string of the document carries an unpaired
     *                              surrogate, or a number inside {@code extensions} has no
     *                              canonical decimal form of at most 64 characters
     * @throws UncheckedIOException if the stream fails
     * @throws NullPointerException if an argument is {@code null}
     */
    public void write(SemanticDocument document, Writer out) {
        Objects.requireNonNull(out, "out");
        try {
            out.write(toText(document));
        } catch (IOException e) {
            throw new UncheckedIOException("writing the document failed", e);
        }
    }

    /**
     * Returns the document as bytes.
     *
     * @param document the document to write
     * @return the UTF-8 bytes of the serialization, without a byte order mark
     * @throws EsjFormatException   if a string of the document carries an unpaired
     *                              surrogate, or a number inside {@code extensions} has no
     *                              canonical decimal form of at most 64 characters
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public byte[] toBytes(SemanticDocument document) {
        Objects.requireNonNull(document, "document");
        JsonBytes out = new JsonBytes(INITIAL_SIZE);
        writeDocument(document, out);
        return out.toByteArray();
    }

    /**
     * Returns the document as text.
     *
     * @param document the document to write
     * @return the serialization as a string
     * @throws EsjFormatException   if a string of the document carries an unpaired
     *                              surrogate, or a number inside {@code extensions} has no
     *                              canonical decimal form of at most 64 characters
     * @throws NullPointerException if {@code document} is {@code null}
     */
    public String toText(SemanticDocument document) {
        return new String(toBytes(document), StandardCharsets.UTF_8);
    }

    /**
     * Returns the semantic identity of a document, serialized as the standalone
     * two-member object of the specification, section 8.2: the edition and the values, in
     * that order. It is the byte sequence the semantic digest is taken over.
     *
     * <p>The edition is inside it because a path is an address relative to an edition: two
     * documents carrying the same strings under two editions would otherwise collide in
     * one digest, and a deduplication key built on it would be silently wrong for exactly
     * the documents that straddle an edition change.
     *
     * @param document the document to write
     * @return the bytes of the semantic identity object
     */
    byte[] semanticToBytes(SemanticDocument document) {
        Objects.requireNonNull(document, "document");
        JsonBytes out = new JsonBytes(INITIAL_SIZE);
        out.ascii('{');
        member(out, 1, true, "semanticModel");
        out.string(document.semanticModel());
        member(out, 1, false, "values");
        writeValues(document, out, 1);
        close(out, 0, '}');
        return out.toByteArray();
    }

    private void writeDocument(SemanticDocument document, JsonBytes out) {
        out.ascii('{');
        member(out, 1, true, "format");
        out.string(Esj.FORMAT);
        member(out, 1, false, "version");
        out.string(Esj.VERSION);
        member(out, 1, false, "semanticModel");
        out.string(document.semanticModel());
        member(out, 1, false, "values");
        writeValues(document, out, 1);
        if (!document.extensions().isEmpty()) {
            member(out, 1, false, "extensions");
            writeExtensions(document.extensions(), out, 1);
        }
        document.source().ifPresent(source -> {
            member(out, 1, false, "source");
            writeSource(source, out, 1);
        });
        close(out, 0, '}');
        if (style == Style.PRETTY) {
            out.ascii('\n');
        }
    }

    private void writeValues(SemanticDocument document, JsonBytes out, int depth) {
        if (document.values().isEmpty()) {
            out.ascii("{}");
            return;
        }
        out.ascii('{');
        boolean first = true;
        for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
            member(out, depth + 1, first, entry.getKey().toString());
            writeValue(entry.getValue(), out, depth + 1);
            first = false;
        }
        close(out, depth, '}');
    }

    /**
     * Writes one value. A value that carries no supplementary component is a JSON string
     * and a value that carries one is a JSON object, with the members in the order of the
     * specification, section 7.3, rule 3. The content is written as it stands: this writer
     * serializes a document and never rewrites what a value holds (section 6.4).
     */
    private void writeValue(SemanticValue value, JsonBytes out, int depth) {
        if (!value.hasComponents()) {
            out.string(value.canonicalContent());
            return;
        }
        out.ascii('{');
        member(out, depth + 1, true, "value");
        out.string(value.canonicalContent());
        component(out, depth, "scheme", value.scheme());
        component(out, depth, "schemeVersion", value.schemeVersion());
        component(out, depth, "mimeCode", value.mimeCode());
        component(out, depth, "filename", value.filename());
        close(out, depth, '}');
    }

    private void component(JsonBytes out, int depth, String name, String value) {
        if (value != null) {
            member(out, depth + 1, false, name);
            out.string(value);
        }
    }

    private void writeSource(SemanticDocument.Source source, JsonBytes out, int depth) {
        out.ascii('{');
        boolean first = true;
        if (source.syntax().isPresent()) {
            member(out, depth + 1, true, "syntax");
            out.string(source.syntax().orElseThrow());
            first = false;
        }
        if (source.sha256().isPresent()) {
            member(out, depth + 1, first, "sha256");
            out.string(source.sha256().orElseThrow());
        }
        close(out, depth, '}');
    }

    private void writeExtensions(Map<String, ExtensionValue> extensions, JsonBytes out, int depth) {
        out.ascii('{');
        boolean first = true;
        for (Map.Entry<String, ExtensionValue> entry : extensions.entrySet()) {
            member(out, depth + 1, first, entry.getKey());
            writeExtensionValue(entry.getValue(), out, depth + 1);
            first = false;
        }
        close(out, depth, '}');
    }

    /**
     * Writes one extension subtree. The walk is iterative: a document assembled through
     * the public API may nest deeper than a document the reader would have accepted
     * (specification, section 12.2), and a recursive writer would answer that with a
     * {@link StackOverflowError} rather than with a serialization.
     */
    private void writeExtensionValue(ExtensionValue value, JsonBytes out, int depth) {
        Deque<Frame> open = new ArrayDeque<>();
        ExtensionValue pending = value;
        int pendingDepth = depth;
        while (true) {
            Frame opened = writeNode(pending, out, pendingDepth);
            if (opened != null) {
                open.push(opened);
            }
            pending = null;
            while (pending == null) {
                if (open.isEmpty()) {
                    return;
                }
                Frame frame = open.peek();
                if (!frame.hasNext()) {
                    close(out, frame.depth(), frame.bracket());
                    open.pop();
                    continue;
                }
                pendingDepth = frame.depth() + 1;
                pending = frame.next(out);
            }
        }
    }

    /**
     * Writes a node and returns the frame of a container that has children, or
     * {@code null} for a scalar and for an empty container.
     */
    private Frame writeNode(ExtensionValue value, JsonBytes out, int depth) {
        if (value instanceof ExtensionValue.ObjectValue object) {
            if (object.members().isEmpty()) {
                out.ascii("{}");
                return null;
            }
            out.ascii('{');
            return Frame.of(object.members().entrySet().iterator(), null, '}', depth, this);
        }
        if (value instanceof ExtensionValue.ArrayValue array) {
            if (array.elements().isEmpty()) {
                out.ascii("[]");
                return null;
            }
            out.ascii('[');
            return Frame.of(null, array.elements().iterator(), ']', depth, this);
        }
        if (value instanceof ExtensionValue.StringValue string) {
            out.string(string.value());
        } else if (value instanceof ExtensionValue.NumberValue number) {
            out.ascii(canonicalNumber(number.value()));
        } else if (value instanceof ExtensionValue.BooleanValue bool) {
            out.ascii(bool.value() ? "true" : "false");
        } else {
            out.ascii("null");
        }
        return null;
    }

    /** One container the writer has opened and not yet closed. */
    private static final class Frame {

        private final Iterator<Map.Entry<String, ExtensionValue>> members;
        private final Iterator<ExtensionValue> elements;
        private final char bracket;
        private final int depth;
        private final EsjWriter writer;
        private boolean first = true;

        private Frame(Iterator<Map.Entry<String, ExtensionValue>> members,
                      Iterator<ExtensionValue> elements,
                      char bracket,
                      int depth,
                      EsjWriter writer) {
            this.members = members;
            this.elements = elements;
            this.bracket = bracket;
            this.depth = depth;
            this.writer = writer;
        }

        static Frame of(Iterator<Map.Entry<String, ExtensionValue>> members,
                        Iterator<ExtensionValue> elements,
                        char bracket,
                        int depth,
                        EsjWriter writer) {
            return new Frame(members, elements, bracket, depth, writer);
        }

        char bracket() {
            return bracket;
        }

        int depth() {
            return depth;
        }

        boolean hasNext() {
            return members != null ? members.hasNext() : elements.hasNext();
        }

        /** Writes the separator and the member name, and returns the child to write. */
        ExtensionValue next(JsonBytes out) {
            ExtensionValue value;
            if (members != null) {
                Map.Entry<String, ExtensionValue> entry = members.next();
                writer.member(out, depth + 1, first, entry.getKey());
                value = entry.getValue();
            } else {
                writer.element(out, depth + 1, first);
                value = elements.next();
            }
            first = false;
            return value;
        }
    }

    private static String canonicalNumber(BigDecimal value) {
        String canonical = Decimals.canonicalize(value.toString());
        if (canonical == null) {
            throw new EsjFormatException(
                    "a number inside extensions has a canonical decimal form of more than "
                            + Decimals.MAX_LENGTH + " characters",
                    FindingCode.ESJ_L1_EXT_NUMBER, null);
        }
        return canonical;
    }

    private void member(JsonBytes out, int depth, boolean first, String name) {
        element(out, depth, first);
        out.string(name);
        out.ascii(':');
        if (style == Style.PRETTY) {
            out.ascii(' ');
        }
    }

    private void element(JsonBytes out, int depth, boolean first) {
        if (!first) {
            out.ascii(',');
        }
        indent(out, depth);
    }

    private void close(JsonBytes out, int depth, char bracket) {
        indent(out, depth);
        out.ascii(bracket);
    }

    private void indent(JsonBytes out, int depth) {
        if (style != Style.PRETTY) {
            return;
        }
        out.ascii('\n');
        for (int i = 0; i < depth; i++) {
            out.ascii("  ");
        }
    }

    /** The two serializations the specification defines. */
    public enum Style {

        /**
         * The canonical form of the specification, section 7: one byte sequence per
         * document content, with no insignificant whitespace and no trailing newline.
         */
        CANONICAL,

        /**
         * The pretty form of the specification, section 7.7: the same content and the same
         * order, indented by two spaces, one member per line, LF line endings and a
         * trailing newline.
         */
        PRETTY
    }
}
