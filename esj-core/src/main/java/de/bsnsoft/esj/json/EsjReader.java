package de.bsnsoft.esj.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import de.bsnsoft.esj.Esj;
import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.validate.Finding;
import de.bsnsoft.esj.validate.FindingCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reads an ESJ document from bytes and enforces validation layer L1 while it does
 * (specification, sections 3.2 and 9.1).
 *
 * <p>The reader is strict on purpose. It rejects a byte order mark, anything that is not
 * UTF-8, a duplicate member name at any depth, a member the specification does not define
 * at any level of the envelope, a {@code semanticModel} that is not an edition string, a
 * JSON number, boolean, {@code null} or array anywhere inside {@code values}, a value whose
 * shape is neither a JSON string nor a value object with a supplementary component, an
 * unpaired surrogate and every limit of section 12.2. It never trims, collapses, reorders or normalizes a value;
 * the one transformation it applies is the line ending normalization of section 6.8, which
 * it must.
 *
 * <p><strong>It needs no registry, and it makes no check that would need one.</strong>
 * Whether the content of a value is a canonical decimal, a date of the calendar or
 * canonical base64 is decided by the semantic data type the registry records for the term,
 * so those checks belong to layer L2 and to
 * {@code de.bsnsoft.esj.validate.StructuralValidator} (specification,
 * section 6.2). The content passes through this reader exactly as the document spells it,
 * {@code 100.00} included: nothing here repairs a spelling (section 6.4).
 *
 * <p><strong>Exceptions and findings.</strong> The specification asks a validator to
 * report findings rather than to throw (section 9.5) and a reader to reject what fails
 * L1 (section 3.2). Both are available here, and they are the same check:
 * <ul>
 *   <li>{@link #read(byte[])} rejects. It throws {@link EsjFormatException} with the
 *       finding code of section 9.6 and a location written as a member access —
 *       {@code values["/BG-4/BT-27"].value} — for the first problem it meets, and
 *       {@link EsjLimitException} for a limit of section 12.2. A document that fails L1
 *       is not a document, so there is nothing to hand back.</li>
 *   <li>{@link #readWithFindings(byte[])} reports. It collects every problem that is local
 *       to one member of {@code values} and carries on, so that a document with fifty
 *       defects produces fifty findings in one pass, and it returns the document built
 *       from the members that were sound. It throws nothing for a defective document: a
 *       problem that makes parsing impossible — a byte sequence that is not UTF-8 or not
 *       JSON, a byte order mark, a duplicate member name, a broken envelope, a defect
 *       inside {@code extensions} and a limit — ends the parse and is the last finding of
 *       the result, which then carries no document.</li>
 * </ul>
 * An input/output failure is an {@link UncheckedIOException} in both.
 *
 * <p>The two shapes carry the same finding. {@link EsjFormatException#code()} is the code
 * of section 9.6 that {@link #readWithFindings(byte[])} would have reported for the same
 * input, and {@link EsjFormatException#location()} names the same place; a limit is
 * {@code ESJ-L1-LIMIT} in either shape, as an {@link EsjLimitException} on the one side
 * and as a finding on the other. A caller that must handle both portably catches the
 * exception and branches on its code, exactly as it would branch on a finding's, and
 * never on the English message. That is what the specification, section 9.5 means by
 * reporting and throwing being both conformant: the answer is the same, and only its shape
 * differs.
 *
 * <p><strong>What a message carries.</strong> Document content in a message is truncated
 * to a short excerpt, because a message is written to a log and a hostile document would
 * otherwise decide how long that line is (section 12.6). A finding's {@code subject}
 * carries the same place whole: it is a structured field a program reads rather than a
 * line a person reads, and it is what tells two findings apart whose path is empty
 * (section 9.5). For the same reason the reader
 * relies on {@code jackson-core} leaving {@code INCLUDE_SOURCE_IN_LOCATION} off, which
 * keeps the parser's own exceptions from quoting the bytes they failed on; an
 * implementation that enables that feature would undo this.
 *
 * <p>The reader neither decides nor claims conformance: it answers whether a byte sequence
 * is <em>well formed</em>. Whether the terms exist, whether their content fits them and
 * whether the mandatory ones are present is layers L2 and L3.
 *
 * <p><strong>It reads every edition.</strong> Layer L1 asks of {@code semanticModel} that
 * it satisfy the edition grammar of the specification, section 4.4 and nothing more;
 * whether a registry for that edition exists is an L2 question and belongs to
 * {@code de.bsnsoft.esj.validate.StructuralValidator}, which answers it with
 * {@code ESJ-L2-EDITION-UNKNOWN} and the status {@code INDETERMINATE}. So this reader
 * reads, canonicalizes, digests, stores and forwards a document of an edition published
 * after it, and claims about that document only what it can see. Refusing it would lose
 * content in the one situation the format exists to survive.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class EsjReader {

    private static final Set<String> VALUE_MEMBERS =
            Set.of("value", "scheme", "schemeVersion", "mimeCode", "filename");

    /**
     * The members whose presence makes a value an object rather than a string
     * (specification, section 6.1, rules 2 and 3).
     */
    private static final Set<String> COMPONENT_MEMBERS =
            Set.of("scheme", "schemeVersion", "mimeCode", "filename");

    /** The longest fragment of document content a message reproduces. */
    private static final int MESSAGE_EXCERPT = 80;

    /**
     * The longest location a message carries. Every segment of a location is held to
     * {@link #MESSAGE_EXCERPT} on its own, and the whole chain to this, so that a document
     * cannot decide how long a log line is however deeply it nests (specification,
     * section 12.6).
     */
    private static final int LOCATION_EXCERPT = 512;

    private static final int CHUNK = 8192;

    private final Limits limits;
    private final JsonFactory factory;

    private EsjReader(Limits limits) {
        this.limits = limits;
        this.factory = buildFactory(limits);
    }

    /**
     * Returns a reader with the limits of the specification, section 12.2.
     *
     * @return a reader with the reference configuration
     */
    public static EsjReader strict() {
        return new EsjReader(Limits.defaults());
    }

    /**
     * Returns a reader with the given limits.
     *
     * @param limits the resource bounds to enforce
     * @return a reader
     * @throws NullPointerException if {@code limits} is {@code null}
     */
    public static EsjReader withLimits(Limits limits) {
        return new EsjReader(Objects.requireNonNull(limits, "limits"));
    }

    /**
     * Returns the limits this reader enforces.
     *
     * @return the resource bounds
     */
    public Limits limits() {
        return limits;
    }

    /**
     * Reads a document and rejects a byte sequence that fails layer L1.
     *
     * @param bytes the document
     * @return the document
     * @throws EsjFormatException   if the byte sequence fails layer L1; the exception
     *                              carries the finding code of the specification,
     *                              section 9.6 and the place the problem sits in
     * @throws EsjLimitException    if a limit of the specification, section 12.2 is
     *                              exceeded
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public SemanticDocument read(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return new Parse(bytes, bytes.length, null).run().orElseThrow();
    }

    /**
     * Reads a document from a stream and rejects a byte sequence that fails layer L1. The
     * stream is read to its end and is not closed.
     *
     * @param in the stream to read from
     * @return the document
     * @throws EsjFormatException   if the byte sequence fails layer L1
     * @throws EsjLimitException    if a limit of the specification, section 12.2 is
     *                              exceeded
     * @throws UncheckedIOException if the stream fails
     * @throws NullPointerException if {@code in} is {@code null}
     */
    public SemanticDocument read(InputStream in) {
        Buffered buffered = drain(in);
        return new Parse(buffered.bytes(), buffered.length(), null).run().orElseThrow();
    }

    /**
     * Reads a document and reports what it met as findings instead of rejecting.
     *
     * @param bytes the document
     * @return the document, where one could be built, and the findings
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public ReadResult readWithFindings(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return readWithFindings(bytes, bytes.length);
    }

    /**
     * Reads a document from a stream and reports what it met as findings instead of
     * rejecting. The stream is read to its end and is not closed.
     *
     * @param in the stream to read from
     * @return the document, where one could be built, and the findings
     * @throws UncheckedIOException if the stream fails
     * @throws NullPointerException if {@code in} is {@code null}
     */
    public ReadResult readWithFindings(InputStream in) {
        Buffered buffered;
        try {
            buffered = drain(in);
        } catch (EsjLimitException e) {
            return new ReadResult(Optional.empty(), List.of(Finding.of(
                    SemanticPath.root(), FindingCode.ESJ_L1_LIMIT, e.getMessage())));
        }
        return readWithFindings(buffered.bytes(), buffered.length());
    }

    private ReadResult readWithFindings(byte[] bytes, int length) {
        List<Finding> findings = new ArrayList<>();
        Optional<SemanticDocument> document;
        try {
            document = new Parse(bytes, length, findings).run();
        } catch (Stop stop) {
            document = Optional.empty();
        } catch (EsjLimitException limit) {
            document = Optional.empty();
        }
        return new ReadResult(document, findings);
    }

    /**
     * Reads a stream into one buffer that is never larger than the document limit plus the
     * one byte it takes to notice the overrun.
     *
     * <p>{@link Limits} holds {@code maxDocumentBytes} to
     * {@link Limits#MAX_DOCUMENT_BYTES}, so {@code bound + 1} is a length a single
     * {@code byte[]} can have: the arithmetic below neither wraps around nor asks for an
     * array the virtual machine cannot allocate, whatever bound a caller configured.
     */
    private Buffered drain(InputStream in) {
        Objects.requireNonNull(in, "in");
        long bound = limits.maxDocumentBytes();
        int room = (int) (bound + 1);
        byte[] buffer = new byte[Math.min(room, CHUNK)];
        int length = 0;
        try {
            while (true) {
                if (length == buffer.length) {
                    if (length > bound) {
                        throw new EsjLimitException("the document exceeds the bound of "
                                + bound + " bytes", "", null);
                    }
                    buffer = Arrays.copyOf(buffer,
                            (int) Math.min(room, buffer.length * 2L));
                }
                int read = in.read(buffer, length, buffer.length - length);
                if (read < 0) {
                    return new Buffered(buffer, length);
                }
                length += read;
                if (length > bound) {
                    throw new EsjLimitException("the document exceeds the bound of "
                            + bound + " bytes", "", null);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("reading the document failed", e);
        }
    }

    /**
     * Configures the parser so that it refuses a token before it builds it, which is what
     * the specification, section 12.2 asks of a streaming parser. A character count is
     * never larger than the count of the UTF-8 bytes the same string takes, so a bound
     * stated in bytes never rejects a token the reader would have accepted.
     *
     * <p>Duplicate member names are detected by the reader itself, at every object level,
     * so that the finding code of the specification, section 9.6 does not depend on how a
     * parser words its own diagnosis.
     *
     * <p>The number constraint is a coarse pre-allocation guard and not the bound of the
     * specification, section 12.2: a parser counts the characters of a number token in its
     * own way, and two readers that both run the defaults must agree on the boundary to
     * the token. The reader therefore measures the token itself once it holds it
     * ({@code Parse.number}), and this constraint only keeps a hopeless token from being
     * assembled at all.
     */
    private static JsonFactory buildFactory(Limits limits) {
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxDocumentLength(limits.maxDocumentBytes())
                .maxNestingDepth(limits.maxExtensionDepth() + Limits.ENVELOPE_NESTING)
                .maxNumberLength(clamp(limits.maxStringBytes()))
                .maxStringLength(clamp(Math.max(limits.maxStringBytes(), limits.maxBinaryValueBytes())))
                .maxNameLength(clamp(Math.max(limits.maxStringBytes(), limits.maxPathBytes())))
                .build();
        return new JsonFactoryBuilder()
                .disable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
                .streamReadConstraints(constraints)
                .build();
    }

    private static int clamp(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    /**
     * Names the JSON type of the token the parser stands on, so that a message can say
     * what the document carries instead of restating the rule it broke.
     *
     * @param parser the parser, standing on the token
     * @param token  the token it stands on
     * @return a noun phrase such as {@code the number 10} or {@code an array}
     */
    private static String jsonType(JsonParser parser, JsonToken token) throws IOException {
        if (token == null) {
            return "the end of the document";
        }
        return switch (token) {
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> "the number " + excerpt(parser.getText());
            case VALUE_TRUE -> "the boolean true";
            case VALUE_FALSE -> "the boolean false";
            case VALUE_NULL -> "null";
            case START_ARRAY -> "an array";
            case START_OBJECT -> "an object";
            case VALUE_STRING -> "a string";
            default -> "something else";
        };
    }

    /**
     * Returns a fragment of document content in the form a message may carry it: short
     * enough that a hostile document cannot decide how long a log line is, and escaped so
     * that it cannot decide what that line looks like either (specification, section
     * 12.6). This is the one place document content enters a message, so every embedder
     * of the reader is covered by it and none of them has to remember the rule.
     */
    private static String excerpt(String value) {
        return Esj.forMessage(value, MESSAGE_EXCERPT);
    }

    /** A byte sequence and the number of its bytes that are the document. */
    private record Buffered(byte[] bytes, int length) {
    }

    /** Signals that parsing cannot continue. Never leaves the reader. */
    private static final class Stop extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Stop() {
            super(null, null, false, false);
        }
    }

    /**
     * A place in the document, held as a chain of steps and written out as a member
     * access only where a message needs one. Building the string eagerly for every node
     * of a large {@code extensions} subtree would cost more than reading the document.
     */
    private static final class Where {

        /** A member name, an array index, or the root fragment the chain starts from. */
        private enum Step { ROOT, MEMBER, INDEX }

        private final Where parent;
        private final Step step;
        private final String segment;

        private Where(Where parent, Step step, String segment) {
            this.parent = parent;
            this.step = step;
            this.segment = segment;
        }

        static Where of(String root) {
            return new Where(null, Step.ROOT, root);
        }

        Where child(String name) {
            return new Where(this, Step.MEMBER, name);
        }

        Where child(int index) {
            return new Where(this, Step.INDEX, Integer.toString(index));
        }

        /**
         * Writes the chain as a member access — {@code extensions["owner"].a[0].b} —
         * with every member name escaped and whole, because this is the finding's subject
         * and a subject is what tells two findings apart (specification, section 9.5). A
         * member name inside {@code extensions} is document content and may be as long as
         * the string bound allows, so where this text reaches a message it is held to
         * {@link #LOCATION_EXCERPT} there and the document does not get to decide how long
         * a log line is (section 12.6).
         *
         * @return the location, written the way a program would address the place
         */
        String text() {
            Deque<Where> nodes = new ArrayDeque<>();
            for (Where node = this; node != null; node = node.parent) {
                nodes.addFirst(node);
            }
            StringBuilder out = new StringBuilder();
            for (Where node : nodes) {
                switch (node.step) {
                    case ROOT -> out.append(node.segment);
                    case INDEX -> out.append('[').append(node.segment).append(']');
                    case MEMBER -> out.append("[\"").append(Esj.forSubject(node.segment))
                            .append("\"]");
                }
            }
            return out.toString();
        }
    }

    /**
     * One member of a value object, as the parser handed it over: its name, its JSON
     * token, its text where it is a string, and the description of its JSON type. The
     * description is taken while the parser still stands on the token, because the whole
     * object is read before any of its members is judged.
     */
    private record Member(String name, JsonToken token, String text, String jsonType) {
    }

    /** One container of an {@code extensions} subtree that the reader has opened. */
    private static final class Container {

        private final boolean object;
        private final Where where;
        private final int depth;
        private final Map<String, ExtensionValue> members;
        private final List<ExtensionValue> elements;
        private String pending;

        Container(boolean object, Where where, int depth) {
            this.object = object;
            this.where = where;
            this.depth = depth;
            this.members = object ? new LinkedHashMap<>() : null;
            this.elements = object ? null : new ArrayList<>();
        }

        boolean object() {
            return object;
        }

        Where where() {
            return where;
        }

        int depth() {
            return depth;
        }

        int size() {
            return elements.size();
        }

        boolean has(String name) {
            return members.containsKey(name);
        }

        void expect(String name) {
            pending = name;
        }

        void add(ExtensionValue value) {
            if (object) {
                members.put(pending, value);
                pending = null;
            } else {
                elements.add(value);
            }
        }

        ExtensionValue build() {
            return object ? ExtensionValue.object(members) : ExtensionValue.array(elements);
        }
    }

    /**
     * One run over one byte sequence. The collector is {@code null} when the reader was
     * asked to reject, and the list of findings when it was asked to report.
     */
    private final class Parse {

        private final byte[] bytes;
        private final int length;
        private final List<Finding> collector;
        private final SemanticDocument.Builder builder = SemanticDocument.builder();
        private int valueCount;
        private long binaryBytes;
        private int extensionNodes;
        private boolean surrogateReported;
        private SemanticPath currentPath;

        Parse(byte[] bytes, int length, List<Finding> collector) {
            this.bytes = bytes;
            this.length = length;
            this.collector = collector;
        }

        Optional<SemanticDocument> run() {
            checkEncoding();
            try (JsonParser parser = factory.createParser(bytes, 0, length)) {
                try {
                    readEnvelope(parser);
                } catch (StreamConstraintsException e) {
                    // Two bounds reach this one exception and one switch raises only the
                    // first of them, so the two are reported apart: a reader told to raise
                    // a bound that nothing raises runs the same document again for the
                    // same answer.
                    String constraint = e.getMessage() == null ? "" : e.getMessage();
                    throw limit((constraint.contains("nesting depth")
                            ? "a place in the document nests deeper than this reader accepts"
                            : "a token of the document is longer than this reader accepts")
                            + "; the reader stopped at byte " + stoppedAt(parser)
                            + " of the document", "");
                }
            } catch (StreamReadException e) {
                throw malformed(e);
            } catch (IOException e) {
                throw new UncheckedIOException("reading the document failed", e);
            }
            return Optional.of(builder.build());
        }

        /**
         * Returns how far into the document the parser had read when a bound of its own
         * stopped it.
         *
         * <p>It is the only place such a finding can name. The bound that was met is a
         * bound of the parser, so the token it was met in was never handed over: a member
         * name longer than the name bound is refused before the reader holds a character
         * of it, there is no name to write into {@code subject}, and a finding that named
         * nothing at all would send its reader to look through the whole file. The offset
         * is a fact the parser does hold, it is one number whatever kind of token or
         * nesting broke the bound, and it is where a caller opens the document
         * (specification, section 9.5).
         *
         * @param parser the parser, stopped
         * @return the byte offset it stopped at, counted from zero, or {@code -1} where
         *         the parser reports none
         */
        private long stoppedAt(JsonParser parser) {
            JsonLocation location = parser.currentLocation();
            return location == null ? -1 : location.getByteOffset();
        }

        private void checkEncoding() {
            if (length > limits.maxDocumentBytes()) {
                throw limit("the document exceeds the bound of " + limits.maxDocumentBytes()
                        + " bytes", "");
            }
            if (length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                    && (bytes[2] & 0xFF) == 0xBF) {
                throw fatal(FindingCode.ESJ_L1_ENCODING, "",
                        "the document starts with a byte order mark, which is not whitespace"
                                + " and not part of a JSON text");
            }
            if (!Texts.isValidUtf8(bytes, length)) {
                throw fatal(FindingCode.ESJ_L1_ENCODING, "",
                        "the document is not encoded in UTF-8");
            }
        }

        private void readEnvelope(JsonParser parser) throws IOException {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw fatal(FindingCode.ESJ_L1_JSON, "",
                        "the top level of a document is a JSON object");
            }
            Set<String> seen = new HashSet<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                if (!seen.add(name)) {
                    throw duplicate(name, "");
                }
                switch (name) {
                    case "format" -> fixed(parser, name, Esj.FORMAT);
                    case "version" -> fixed(parser, name, Esj.VERSION);
                    case "semanticModel" -> builder.semanticModel(edition(parser));
                    case "values" -> readValues(parser);
                    case "extensions" -> readExtensions(parser);
                    case "source" -> readSource(parser);
                    default -> throw fatal(FindingCode.ESJ_L1_ENVELOPE_MEMBER, excerpt(name),
                            "the envelope has no member named " + excerpt(name));
                }
            }
            for (String required : List.of("format", "version", "semanticModel", "values")) {
                if (!seen.contains(required)) {
                    throw fatal(FindingCode.ESJ_L1_ENVELOPE_MEMBER, "",
                            "the envelope member " + required + " is required");
                }
            }
            if (parser.nextToken() != null) {
                throw fatal(FindingCode.ESJ_L1_JSON, "",
                        "the document carries content after the object that closes it");
            }
        }

        /**
         * Reads the {@code semanticModel} member and holds it to the edition grammar of
         * the specification, section 4.4 and to nothing else. Whether a registry for that
         * edition exists is an L2 question, so a document of an edition this
         * implementation knows nothing about is read like any other.
         */
        private String edition(JsonParser parser) throws IOException {
            JsonToken token = parser.nextToken();
            if (token != JsonToken.VALUE_STRING) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "semanticModel",
                        "the envelope member semanticModel is " + jsonType(parser, token)
                                + ", not a string");
            }
            String value = parser.getText();
            requireUnicode(value, "semanticModel");
            if (!Esj.isEdition(value)) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "semanticModel",
                        "the envelope member semanticModel carries " + excerpt(value)
                                + ", which is not an edition of the semantic model;"
                                + " one is written " + Esj.SEMANTIC_MODEL);
            }
            return value;
        }

        private String fixed(JsonParser parser, String member, String expected) throws IOException {
            JsonToken token = parser.nextToken();
            if (token != JsonToken.VALUE_STRING) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, member,
                        "the envelope member " + member + " is " + jsonType(parser, token)
                                + ", not a string");
            }
            String value = parser.getText();
            requireUnicode(value, member);
            if (!expected.equals(value)) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, member,
                        "the envelope member " + member + " carries " + excerpt(value)
                                + "; its one value is " + expected);
            }
            return value;
        }

        private void readSource(JsonParser parser) throws IOException {
            JsonToken opening = parser.nextToken();
            if (opening != JsonToken.START_OBJECT) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "source",
                        "source is " + jsonType(parser, opening) + ", not a JSON object");
            }
            Set<String> seen = new HashSet<>();
            String syntax = null;
            String sha256 = null;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                String where = "source." + Esj.forSubject(name);
                if (!seen.add(name)) {
                    throw duplicate(name, "source");
                }
                if (!name.equals("syntax") && !name.equals("sha256")) {
                    throw fatal(FindingCode.ESJ_L1_ENVELOPE_MEMBER, where,
                            "source has no member named " + excerpt(name));
                }
                JsonToken token = parser.nextToken();
                if (token != JsonToken.VALUE_STRING) {
                    throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, where,
                            name + " is " + jsonType(parser, token) + ", not a string");
                }
                String value = parser.getText();
                requireUnicode(value, where);
                if (value.isEmpty()) {
                    throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, where,
                            name + " is the empty string");
                }
                if (name.equals("syntax")) {
                    if (Texts.utf8Length(value) > limits.maxStringBytes()) {
                        throw limit("source.syntax is longer than " + limits.maxStringBytes()
                                + " bytes", where);
                    }
                    syntax = value;
                } else {
                    if (!Texts.isLowercaseSha256(value)) {
                        throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, where,
                                "sha256 " + Texts.sha256Violation(value));
                    }
                    sha256 = value;
                }
            }
            if (syntax == null && sha256 == null) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "source",
                        "source carries neither syntax nor sha256; a source object that would"
                                + " be empty is absent instead");
            }
            builder.source(new SemanticDocument.Source(
                    Optional.ofNullable(syntax), Optional.ofNullable(sha256)));
        }

        private void readValues(JsonParser parser) throws IOException {
            JsonToken opening = parser.nextToken();
            if (opening != JsonToken.START_OBJECT) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "values",
                        "values is " + jsonType(parser, opening) + ", not a JSON object");
            }
            Set<String> seen = new HashSet<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                if (!seen.add(name)) {
                    throw duplicate(name, "values");
                }
                if (++valueCount > limits.maxValues()) {
                    throw limit("values carries more than " + limits.maxValues() + " members",
                            valuesWhere(name));
                }
                if (Texts.utf8Length(name) > limits.maxPathBytes()) {
                    throw limit("a semantic path is longer than " + limits.maxPathBytes()
                            + " bytes", valuesWhere(name));
                }
                String where = valuesWhere(name);
                SemanticPath path = readPath(name, where);
                currentPath = path;
                SemanticValue value = readValue(parser, where);
                if (path != null && value != null) {
                    builder.put(path, value);
                }
                currentPath = null;
            }
        }

        private String valuesWhere(String name) {
            return "values[\"" + Esj.forSubject(name) + "\"]";
        }

        private String memberWhere(String where, String name) {
            return where + "." + Esj.forSubject(name);
        }

        private String ownerWhere(String owner) {
            return "extensions[\"" + Esj.forSubject(owner) + "\"]";
        }

        private SemanticPath readPath(String name, String where) {
            SemanticPath path;
            try {
                path = SemanticPath.of(name);
            } catch (EsjFormatException e) {
                report(FindingCode.ESJ_L1_PATH_SYNTAX, where, excerpt(e.getMessage()));
                return null;
            }
            if (path.segments().size() > limits.maxPathSegments()) {
                throw limit("a semantic path has more than " + limits.maxPathSegments()
                        + " segments", where);
            }
            return path;
        }

        /**
         * Reads one member of {@code values} and returns the value it holds, or
         * {@code null} when the member was reported as defective. The shape rules of the
         * specification, section 6.1 are the whole of what is checked here: a value is a
         * JSON string or a value object, and what the content spells is layer L2's
         * question (section 6.2).
         */
        private SemanticValue readValue(JsonParser parser, String where) throws IOException {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.VALUE_STRING) {
                String content = string(parser.getText(), where, "this value", false);
                return content == null ? null : SemanticValue.of(content);
            }
            if (token != JsonToken.START_OBJECT) {
                String type = jsonType(parser, token);
                skipValue(parser, token, where, 1);
                report(FindingCode.ESJ_L1_JSON_TYPE, where,
                        "this member of values is " + type
                                + ", not a string and not a value object");
                return null;
            }
            return readValueObject(parser, where);
        }

        /**
         * Reads a value written as a JSON object. The members are collected before any of
         * them is judged, because the specification, section 9.6 decides the code from
         * the object and not from the member a reader happens to meet first: an object
         * with no supplementary component is {@code ESJ-L1-VALUE-SHAPE} whatever else it
         * carries, the shape of a member outranks the member set, and the member set
         * outranks the content of a member. A lone surrogate stands outside that order
         * and is reported beside the code the order gives, because the two checks read
         * two different strings.
         *
         * <p>Collecting is bounded while it happens, as the specification, section 12.2
         * asks of every container: the members of one value object are counted as they
         * arrive, so that a value object with a million members is refused instead of
         * held. At most five members are ever legal, so the default bound leaves every
         * document the specification allows untouched.
         */
        private SemanticValue readValueObject(JsonParser parser, String where) throws IOException {
            List<Member> members = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            boolean component = false;
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                if (!seen.add(name)) {
                    throw duplicate(name, where);
                }
                if (members.size() >= limits.maxValueMembers()) {
                    throw limit("a value object carries more than "
                            + limits.maxValueMembers() + " members", where);
                }
                component |= COMPONENT_MEMBERS.contains(name);
                JsonToken token = parser.nextToken();
                members.add(new Member(name, token,
                        token == JsonToken.VALUE_STRING ? parser.getText() : null,
                        jsonType(parser, token)));
                skipValue(parser, token, where, 2);
            }
            boolean surrogate = reportSurrogates(members, where);
            if (!component) {
                report(FindingCode.ESJ_L1_VALUE_SHAPE, where,
                        "this value is an object and carries no supplementary component;"
                                + " a value that is nothing but content is written as a"
                                + " JSON string");
                return null;
            }
            for (Member member : members) {
                if (member.token() == JsonToken.START_OBJECT) {
                    report(FindingCode.ESJ_L1_VALUE_SHAPE, memberWhere(where, member.name()),
                            excerpt(member.name()) + " is an object, not a string");
                    return null;
                }
            }
            for (Member member : members) {
                if (member.token() != JsonToken.VALUE_STRING) {
                    report(FindingCode.ESJ_L1_JSON_TYPE, memberWhere(where, member.name()),
                            excerpt(member.name()) + " is " + member.jsonType()
                                    + ", not a string");
                    return null;
                }
            }
            Map<String, String> byName = new LinkedHashMap<>();
            for (Member member : members) {
                String name = member.name();
                if (!VALUE_MEMBERS.contains(name)) {
                    report(FindingCode.ESJ_L1_VALUE_MEMBER, memberWhere(where, name),
                            "a value object has no member named " + excerpt(name));
                    return null;
                }
                byName.put(name, member.text());
            }
            if (!byName.containsKey("value")) {
                report(FindingCode.ESJ_L1_VALUE_MEMBER, where,
                        "this value object carries no value member");
                return null;
            }
            if (byName.containsKey("schemeVersion") && !byName.containsKey("scheme")) {
                report(FindingCode.ESJ_L1_VALUE_MEMBER, where + ".schemeVersion",
                        "schemeVersion is present only beside scheme");
                return null;
            }
            return build(byName, where, surrogate);
        }

        /**
         * Reports every member string of a value object that carries a lone surrogate,
         * and tells whether one was found.
         *
         * <p>The specification, section 9.6 gives {@code ESJ-L1-SURROGATE} precedence over
         * every code whose check reads the content of the same string, and leaves a check
         * that reads another string untouched by it. The shape of a member, the shape of
         * the object and the member set are such checks, so a value object whose shape or
         * whose set is wrong and one of whose strings has no UTF-8 encoding draws both
         * codes: the one says what to mend, the other says that the bytes cannot be handed
         * on at all. This runs before any of them for that reason.
         *
         * <p>A member that is itself a JSON object is not descended into, because the
         * shape is what is reported and the subtree below it is not examined further.
         */
        private boolean reportSurrogates(List<Member> members, String where) {
            boolean found = false;
            for (Member member : members) {
                if (member.token() == JsonToken.VALUE_STRING
                        && Texts.hasLoneSurrogate(member.text())) {
                    report(FindingCode.ESJ_L1_SURROGATE, memberWhere(where, member.name()),
                            "a string carries an unpaired surrogate");
                    found = true;
                }
            }
            return found;
        }

        /**
         * Checks the strings of a value object and builds the value, or returns
         * {@code null} when a string was reported here or a lone surrogate was reported
         * before. The content of a value that carries a binary component is held to the
         * larger of the two string bounds of the specification, section 12.2: a reader
         * has no registry and cannot know the semantic data type of the term, and the
         * presence of such a component is what it can see.
         */
        private SemanticValue build(Map<String, String> members,
                                    String where,
                                    boolean surrogate) {
            boolean binary = members.containsKey("mimeCode") || members.containsKey("filename");
            Map<String, String> checked = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : members.entrySet()) {
                String name = entry.getKey();
                String content = content(entry.getValue(), memberWhere(where, name), name,
                        binary && name.equals("value"));
                if (content == null) {
                    return null;
                }
                checked.put(name, content);
            }
            if (surrogate) {
                return null;
            }
            if (binary) {
                countBinary(checked.get("value"), where);
            }
            return new SemanticValue(checked.get("value"), checked.get("scheme"),
                    checked.get("schemeVersion"), checked.get("mimeCode"),
                    checked.get("filename"));
        }

        /**
         * Checks one string inside {@code values}: no lone surrogate, not empty, and
         * within the bound of the specification, section 12.2, measured on the normalized
         * value.
         *
         * @return the normalized string, or {@code null} when a finding was reported
         */
        private String string(String raw, String where, String what, boolean binary) {
            if (Texts.hasLoneSurrogate(raw)) {
                report(FindingCode.ESJ_L1_SURROGATE, where,
                        "a string carries an unpaired surrogate");
                return null;
            }
            return content(raw, where, what, binary);
        }

        /**
         * Checks one string inside {@code values} whose surrogates have already been
         * looked at: not empty, and within the bound of the specification, section 12.2,
         * measured on the normalized value.
         *
         * @return the normalized string, or {@code null} when a finding was reported
         */
        private String content(String raw, String where, String what, boolean binary) {
            String value = Esj.normalizeLineEndings(raw);
            if (value.isEmpty()) {
                report(FindingCode.ESJ_L1_EMPTY_STRING, where, what + " is the empty string");
                return null;
            }
            long bound = binary ? limits.maxBinaryValueBytes() : limits.maxStringBytes();
            if (Texts.utf8Length(value) > bound) {
                // The two bounds are named apart, because they are raised apart: a
                // message that called a base64 attachment a string value would send its
                // reader to the wrong knob.
                throw limit((binary ? "a binary value is longer than " : "a string value is"
                        + " longer than ") + bound + " bytes", where);
            }
            return value;
        }

        /**
         * Adds what the content of a binary value would decode to against the bound of
         * the specification, section 12.2. The size is taken from the encoded string
         * rather than by decoding it, which section 12.5 asks for, and it needs no
         * judgement about whether the string is canonical base64 — that is layer L2's
         * question.
         */
        private void countBinary(String content, String where) {
            long decoded = Texts.decodedBase64Length(content);
            if (binaryBytes + decoded > limits.maxTotalBinaryBytes()) {
                throw limit("the decoded binary content of the document exceeds the bound of "
                        + limits.maxTotalBinaryBytes() + " bytes", where);
            }
            binaryBytes += decoded;
        }

        private void readExtensions(JsonParser parser) throws IOException {
            JsonToken opening = parser.nextToken();
            if (opening != JsonToken.START_OBJECT) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "extensions",
                        "extensions is " + jsonType(parser, opening) + ", not a JSON object");
            }
            Set<String> seen = new HashSet<>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String owner = parser.currentName();
                if (!seen.add(owner)) {
                    throw duplicate(owner, "extensions");
                }
                if (!Texts.isOwnerToken(owner)) {
                    throw fatal(FindingCode.ESJ_L1_OWNER_TOKEN, ownerWhere(owner),
                            "the owner token " + excerpt(owner) + " "
                                    + Texts.ownerTokenViolation(owner));
                }
                parser.nextToken();
                builder.extension(owner,
                        readExtensionValue(parser, Where.of(ownerWhere(owner)), 1));
            }
            if (seen.isEmpty()) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "extensions",
                        "extensions carries no owner; an extensions object that would be empty"
                                + " is absent instead");
            }
        }

        /**
         * Reads one extension subtree. The walk is iterative, with the containers it has
         * opened held in a deque rather than in stack frames: the nesting bound of the
         * specification, section 12.2 is configurable, so a recursive reader would answer
         * a bound a caller raised with a {@link StackOverflowError} instead of with a
         * document or a finding. It is the same reason the writer walks the tree
         * iteratively.
         */
        private ExtensionValue readExtensionValue(JsonParser parser, Where root, int rootDepth)
                throws IOException {
            Deque<Container> open = new ArrayDeque<>();
            Where where = root;
            int depth = rootDepth;
            ExtensionValue finished = null;
            while (true) {
                if (finished == null) {
                    JsonToken token = parser.currentToken();
                    countExtensionNode(where);
                    if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                        if (depth > limits.maxExtensionDepth()) {
                            throw limit("extensions is nested deeper than "
                                    + limits.maxExtensionDepth() + " levels", where.text());
                        }
                        open.push(new Container(token == JsonToken.START_OBJECT, where, depth));
                    } else {
                        finished = scalar(parser, token, where);
                    }
                }
                if (finished != null) {
                    if (open.isEmpty()) {
                        return finished;
                    }
                    open.peek().add(finished);
                    finished = null;
                }
                Container container = open.peek();
                JsonToken next = parser.nextToken();
                if (container.object()) {
                    if (next != JsonToken.FIELD_NAME) {
                        finished = container.build();
                        open.pop();
                        continue;
                    }
                    String name = parser.currentName();
                    Where memberWhere = container.where().child(name);
                    checkExtensionString(name, memberWhere);
                    if (container.has(name)) {
                        throw duplicate(name, container.where().text());
                    }
                    container.expect(name);
                    parser.nextToken();
                    where = memberWhere;
                } else {
                    if (next == JsonToken.END_ARRAY) {
                        finished = container.build();
                        open.pop();
                        continue;
                    }
                    where = container.where().child(container.size());
                }
                depth = container.depth() + 1;
            }
        }

        /** Counts one node of {@code extensions} against the bound of section 12.2. */
        private void countExtensionNode(Where where) {
            if (++extensionNodes > limits.maxExtensionNodes()) {
                throw limit("extensions carries more than " + limits.maxExtensionNodes()
                        + " nodes", where.text());
            }
        }

        private ExtensionValue scalar(JsonParser parser, JsonToken token, Where where)
                throws IOException {
            return switch (token) {
                case VALUE_STRING -> {
                    String value = parser.getText();
                    checkExtensionString(value, where);
                    yield ExtensionValue.of(value);
                }
                case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> ExtensionValue.of(number(parser, where));
                case VALUE_TRUE -> ExtensionValue.of(true);
                case VALUE_FALSE -> ExtensionValue.of(false);
                case VALUE_NULL -> ExtensionValue.nullValue();
                default -> throw new IllegalStateException("unreachable JSON token " + token);
            };
        }

        private void checkExtensionString(String value, Where where) {
            if (Texts.hasLoneSurrogate(value)) {
                throw fatal(FindingCode.ESJ_L1_SURROGATE, where.text(),
                        "a string carries an unpaired surrogate");
            }
            if (Texts.utf8Length(value) > limits.maxStringBytes()) {
                throw limit("a string inside extensions is longer than " + limits.maxStringBytes()
                        + " bytes", where.text());
            }
        }

        /**
         * Reads one number of {@code extensions}. The spelling is measured here rather
         * than left to the parser constraint, because the specification, section 12.2
         * bounds the token at the string bound exactly and a parser counts a number token
         * in its own way. A JSON number is ASCII, so its characters are its UTF-8 bytes.
         */
        private BigDecimal number(JsonParser parser, Where where) throws IOException {
            String lexical = parser.getText();
            if (lexical.length() > limits.maxStringBytes()) {
                throw limit("a number inside extensions is spelled in more than "
                        + limits.maxStringBytes() + " bytes", where.text());
            }
            String canonical = Decimals.canonicalize(lexical);
            if (canonical == null) {
                throw fatal(FindingCode.ESJ_L1_EXT_NUMBER, where.text(),
                        "a number inside extensions has a canonical decimal form of more than "
                                + Decimals.MAX_LENGTH + " characters");
            }
            return new BigDecimal(canonical);
        }

        /**
         * Walks past a container written where the specification allows none, and bounds
         * that walk.
         *
         * <p>A member of {@code values} is a string or a value object and a member of a
         * value object is a string (section 6.1), so an object or an array in either place
         * is an error whatever its depth — but the reader has to get past it to reach the
         * next member, and the specification, section 12.2 extends the nesting bound to
         * that walk. The bound is counted the way it is counted inside {@code extensions}:
         * the envelope's own two objects are not levels, so the value of a member of
         * {@code values} is level 1 and the value of a member of a value object is level 2.
         * A container past the bound is {@code ESJ-L1-LIMIT} and not the shape code the
         * member would otherwise have drawn, because the reader stopped rather than
         * finished judging (section 9.6).
         *
         * @param parser the parser, standing on the first token of the value
         * @param token  that token
         * @param where  the member access the finding names
         * @param level  the level the container the walk starts at occupies
         * @throws IOException          if the input cannot be read
         * @throws EsjLimitException    if the walk would pass the nesting bound
         */
        private void skipValue(JsonParser parser, JsonToken token, String where, int level)
                throws IOException {
            if (token != JsonToken.START_OBJECT && token != JsonToken.START_ARRAY) {
                return;
            }
            int depth = level;
            int open = 0;
            JsonToken current = token;
            while (true) {
                if (current == JsonToken.START_OBJECT || current == JsonToken.START_ARRAY) {
                    if (depth > limits.maxExtensionDepth()) {
                        throw limit("a value nests deeper than "
                                + limits.maxExtensionDepth() + " levels", where);
                    }
                    open++;
                    depth++;
                } else if (current == JsonToken.END_OBJECT || current == JsonToken.END_ARRAY) {
                    open--;
                    depth--;
                    if (open == 0) {
                        return;
                    }
                }
                current = parser.nextToken();
            }
        }

        private RuntimeException duplicate(String name, String where) {
            return fatal(FindingCode.ESJ_L1_DUPLICATE_MEMBER, where,
                    "the member name " + excerpt(name) + " occurs twice in one object");
        }

        private EsjLimitException limit(String message, String where) {
            if (collector != null) {
                collector.add(finding(FindingCode.ESJ_L1_LIMIT, where, message));
            }
            return new EsjLimitException(message, where, null);
        }

        /**
         * Classifies a failure of the parser. A lone surrogate is one of them: a parser
         * that decodes member names to strings refuses a name carrying one before the
         * reader ever sees it, and the specification, section 9.6 names that defect
         * {@code ESJ-L1-SURROGATE} rather than "not a JSON text" — an escaped surrogate is
         * well-formed JSON and only ill-formed Unicode. The bytes are asked, not the
         * parser's wording.
         *
         * <p>Two conditions keep the question honest. Only the bytes the parser actually
         * consumed are scanned, so a surrogate that sits past the byte the parser stopped
         * at cannot decide the code of a failure it had no part in; and a surrogate that
         * was already reported at its own path is not reported a second time at the level
         * of the document, so one defect produces one finding and the syntax error beside
         * it keeps its own code.
         */
        private RuntimeException malformed(StreamReadException cause) {
            if (!surrogateReported && Texts.hasLoneSurrogateEscape(bytes, consumed(cause))) {
                return fatal(FindingCode.ESJ_L1_SURROGATE, "",
                        "a string carries an unpaired surrogate");
            }
            String text = "the document is not a JSON text: "
                    + excerpt(String.valueOf(cause.getOriginalMessage()))
                    + position(cause);
            if (collector != null) {
                collector.add(finding(FindingCode.ESJ_L1_JSON, "", text));
                return new Stop();
            }
            return new EsjFormatException(text, FindingCode.ESJ_L1_JSON, "", cause);
        }

        /**
         * Returns the place the parser failed at, as a line and a column, or the empty
         * string where it does not say.
         *
         * <p>The excerpt of the parser's own wording is taken before this is appended,
         * because {@code jackson-core} writes the position into the end of that wording
         * and an excerpt of the whole composed message would cut off exactly the two
         * numbers a person editing the document needs. The numbers are the parser's own
         * and carry no document content.
         */
        private static String position(StreamReadException cause) {
            if (cause.getLocation() == null) {
                return "";
            }
            int line = cause.getLocation().getLineNr();
            int column = cause.getLocation().getColumnNr();
            if (line < 1 || column < 1) {
                return "";
            }
            return " (line " + line + ", column " + column + ")";
        }

        /**
         * Returns the number of bytes the parser had consumed when it failed, or the
         * whole document where it does not say.
         */
        private int consumed(StreamReadException cause) {
            long offset = cause.getLocation() == null
                    ? -1
                    : cause.getLocation().getByteOffset();
            return offset < 0 || offset > length ? length : (int) offset;
        }

        /**
         * Screens one string of the envelope for an unpaired surrogate before any check
         * that reads its content runs. The specification, section 6.8 states the rule
         * over every string of the document, and section 9.6 gives
         * {@code ESJ-L1-SURROGATE} precedence over every code whose check reads the
         * content of the same string: a string that has no UTF-8 encoding spells no
         * syntax name, no hexadecimal digest and no fixed value, so a grammar cannot be
         * the first thing said about it. Screening here also keeps the reader's verdict
         * and the canonical form in step — a document the reader accepts is one the
         * canonicalizer can encode (section 3.4).
         */
        private void requireUnicode(String value, String where) {
            if (Texts.hasLoneSurrogate(value)) {
                throw fatal(FindingCode.ESJ_L1_SURROGATE, where,
                        "a string carries an unpaired surrogate");
            }
        }

        /** Reports a problem that ends the parse, and returns what to throw for it. */
        private RuntimeException fatal(FindingCode code, String where, String message) {
            if (collector != null) {
                collector.add(finding(code, where, message));
                return new Stop();
            }
            return new EsjFormatException(message, code, where);
        }

        /** Reports a problem that is local to one member of {@code values}. */
        private void report(FindingCode code, String where, String message) {
            if (code == FindingCode.ESJ_L1_SURROGATE) {
                surrogateReported = true;
            }
            if (collector != null) {
                collector.add(finding(code, where, message));
                return;
            }
            throw new EsjFormatException(message, code, where);
        }

        /**
         * Returns the finding for a problem met at {@code where}.
         *
         * <p>The place the reader knows is a member access into the document as it was
         * received — {@code values["/BG-4/BT-29"].scheme} — and it is carried as the
         * finding's subject as well as in the message, because a member name that is no
         * semantic path leaves the finding's path empty and a program then has nothing
         * else to act on (specification, section 9.5). The subject carries the name whole,
         * so that two long names alike in their first characters give two findings a
         * program can tell apart; the copy in the message is held to
         * {@link #LOCATION_EXCERPT}, because that one is a log line (section 12.6).
         */
        private Finding finding(FindingCode code, String where, String message) {
            String text = where.isEmpty()
                    ? message
                    : message + " (at " + Esj.abbreviated(where, LOCATION_EXCERPT) + ")";
            SemanticPath path = currentPath == null ? SemanticPath.root() : currentPath;
            return Finding.about(path, where, code, text);
        }
    }
}
