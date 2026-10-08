package de.bsnsoft.esj.json;

import de.bsnsoft.esj.Esj;
import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.EsjLimitException.Bound;
import de.bsnsoft.esj.EsjLimitException;
import de.bsnsoft.esj.ExtensionValue;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.internal.Messages;
import de.bsnsoft.esj.json.JsonScanner.Kind;
import de.bsnsoft.esj.json.JsonScanner.Malformed;
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
 * UTF-8 — it reads the bytes as UTF-8 and never guesses another encoding, so a document
 * written in UTF-16 or UTF-32 without a byte order mark is a byte sequence that is no JSON
 * text — a duplicate member name in every object it judges, a member the specification
 * does not define at any level of the envelope, a {@code semanticModel} that is not an
 * edition string, a JSON number, boolean, {@code null} or array anywhere inside
 * {@code values}, a value whose shape is neither a JSON string nor a value object with a
 * supplementary component, an unpaired surrogate and every limit of section 12.2. It never
 * trims, collapses, reorders or normalizes a value; the one transformation it applies is
 * the line ending normalization of section 6.8, which it must.
 *
 * <p><strong>It needs no registry, and it makes no check that would need one.</strong>
 * Whether the content of a value is a canonical decimal, a date of the calendar or
 * canonical base64 is decided by the semantic data type the registry records for the term,
 * so those checks belong to layer L2 and to
 * {@code de.bsnsoft.esj.validate.StructuralValidator} (specification,
 * section 6.2). The content passes through this reader exactly as the document spells it,
 * {@code 100.00} included: nothing here repairs a spelling (section 6.4).
 *
 * <p><strong>The order it judges in is the order of the text.</strong> The document is
 * walked once, token by token, and every member name is judged before the value written
 * under it, so that the first defect in the text is the first finding (section 9.6): a
 * duplicate or undefined name is reported before whatever is wrong with its value, and a
 * token at the place of a value that is no complete JSON value — {@code tru}, {@code 01} —
 * is {@code ESJ-L1-JSON} before anything is said about its type. What the reader walks past
 * because the member above it is already wrong is checked for JSON and for the limits of
 * section 12.2 and for nothing else.
 *
 * <p><strong>Exceptions and findings.</strong> The specification asks a validator to
 * report findings rather than to throw (section 9.5) and a reader to reject what fails
 * L1 (section 3.2). Both are available here, and they are the same check:
 * <ul>
 *   <li>{@link #read(byte[])} rejects. It throws {@link EsjFormatException} for the first
 *       problem it meets, and {@link EsjLimitException} for a limit of section 12.2. Either
 *       carries the code, the path and the subject the finding would have carried. A
 *       document that fails L1 is not a document, so there is nothing to hand back.</li>
 *   <li>{@link #readWithFindings(byte[])} reports. It collects every problem that is local
 *       to one member of {@code values} and carries on, so that a document with fifty
 *       defects produces fifty findings in one pass, and it returns the document built
 *       from the members that were sound. It throws nothing for a defective document: a
 *       problem that makes parsing impossible — a byte sequence that is not UTF-8 or not
 *       JSON, a byte order mark, a duplicate member name, a broken envelope, a defect
 *       inside {@code extensions} and a limit — ends the parse and is the last finding of
 *       the result, which then carries no document. Where required envelope members are
 *       missing, each of them is one finding.</li>
 * </ul>
 * An input/output failure is an {@link UncheckedIOException} in both.
 *
 * <p>The two shapes carry the same finding. {@link EsjFormatException#code()},
 * {@link EsjFormatException#path()} and {@link EsjFormatException#subject()} are what
 * {@link #readWithFindings(byte[])} would have reported for the same input; a limit is
 * {@code ESJ-L1-LIMIT} in either shape, as an {@link EsjLimitException} on the one side and
 * as a finding on the other. A caller that must handle both portably catches the exception
 * and branches on its code, exactly as it would branch on a finding's, and never on the
 * English message. That is what the specification, section 9.5 means by reporting and
 * throwing being both conformant: the answer is the same, and only its shape differs.
 *
 * <p><strong>What a finding carries.</strong> The subject of a finding is a member access
 * from the root of the document (specification, section 9.5): a name the specification
 * defines in dotted form — {@code source.syntax}, {@code values["/BT-29/0"].scheme} — and a
 * name the document chose in brackets, as a JSON string with the escapes of section 9.5 —
 * {@code values["/BT-1"]}, {@code extensions["de.example"]["a"][0]}, {@code ["profile"]}.
 * It is never shortened. Document content in a <em>message</em> is truncated to a short
 * excerpt, because a message is written to a log and a hostile document would otherwise
 * decide how long that line is (section 12.6). A message that places a defect in the byte
 * sequence names the offset of the token it was met in, counted in bytes from zero.
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

    /** The members of the envelope (specification, section 4.1). */
    private static final Set<String> ENVELOPE_MEMBERS =
            Set.of("format", "version", "semanticModel", "values", "extensions", "source");

    /** The members of the envelope a document cannot do without, in the order they are named. */
    private static final List<String> REQUIRED_MEMBERS =
            List.of("format", "version", "semanticModel", "values");

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

    private EsjReader(Limits limits) {
        this.limits = limits;
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
     *                              section 9.6, the path and the subject of the finding
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

    /** Names the bound on the size of a document, in bytes. */
    private static Bound documentBytes(long bound) {
        return new Bound("maxDocumentBytes", bound, "bytes");
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
                                + bound + " bytes", documentBytes(bound), "", null);
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
                            + bound + " bytes", documentBytes(bound), "", null);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("reading the document failed", e);
        }
    }

    /**
     * Returns a fragment of document content in the form a message may carry it: short
     * enough that a hostile document cannot decide how long a log line is, and escaped so
     * that it cannot decide what that line looks like either (specification, section
     * 12.6). This is the one place document content enters a message, so every embedder
     * of the reader is covered by it and none of them has to remember the rule.
     */
    private static String excerpt(String value) {
        return Messages.forMessage(value, MESSAGE_EXCERPT);
    }

    /**
     * Writes the member access of a member whose name the document chose: the name as a
     * JSON string with the escapes of the specification, section 9.5, in brackets after
     * the access of the object it is a member of.
     *
     * @param object the member access of the object, the empty string for the envelope
     * @param name   the member name as the document carries it
     * @return the member access, for example {@code values["/BT-1"]}
     */
    private static String bracket(String object, String name) {
        return object + "[\"" + Messages.forSubject(name) + "\"]";
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
     * access only where a finding needs one. Building the string eagerly for every node
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
         * Writes the chain as a member access — {@code extensions["owner"]["a"][0]} —
         * with every member name escaped and whole, because this is the finding's subject
         * and a subject is what tells two findings apart (specification, section 9.5).
         * Every name below an owner token is one the document chose, so every one of them
         * is written in brackets. A member name inside {@code extensions} is document
         * content and may be as long as the string bound allows, so where this text
         * reaches a message it is held to {@link #LOCATION_EXCERPT} there and the
         * document does not get to decide how long a log line is (section 12.6).
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
                    case MEMBER -> out.append("[\"").append(Messages.forSubject(node.segment))
                            .append("\"]");
                }
            }
            return out.toString();
        }
    }

    /**
     * One member of a value object, as the scanner handed it over: its name, its JSON
     * kind, its text where it is a string, and the description of its JSON type. The
     * description is taken while the scanner still stands on the token, because the whole
     * object is read before any of its members is judged.
     */
    private record Member(String name, Kind kind, String text, String jsonType) {
    }

    /** One container of an {@code extensions} subtree that the reader has opened. */
    private static final class Container {

        private final boolean object;
        private final Where where;
        private final int depth;
        private final Map<String, ExtensionValue> members;
        private final List<ExtensionValue> elements;
        private String pending;
        private boolean first = true;

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

        boolean first() {
            return first;
        }

        void started() {
            first = false;
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
        private final JsonScanner scanner;
        private final SemanticDocument.Builder builder = SemanticDocument.builder();
        private int valueCount;
        private long binaryBytes;
        private int extensionNodes;
        private SemanticPath currentPath;

        Parse(byte[] bytes, int length, List<Finding> collector) {
            this.bytes = bytes;
            this.length = length;
            this.collector = collector;
            this.scanner = new JsonScanner(bytes, length);
        }

        Optional<SemanticDocument> run() {
            checkEncoding();
            try {
                readEnvelope();
            } catch (Malformed e) {
                throw malformed(e);
            }
            return Optional.of(builder.build());
        }

        /**
         * Decides what is decided over the whole byte sequence before a token is read: its
         * size, a byte order mark, and whether it is UTF-8 at all (specification, sections
         * 4.2 and 12.2). Each of these stands alone, because a byte sequence that fails one
         * of them is not read.
         */
        private void checkEncoding() {
            if (length > limits.maxDocumentBytes()) {
                throw limit("the document exceeds the bound of " + limits.maxDocumentBytes()
                        + " bytes", documentBytes(limits.maxDocumentBytes()), "", -1);
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

        private void readEnvelope() {
            if (scanner.peek() != Kind.OBJECT) {
                throw malformed(new Malformed("the top level of a document is a JSON object",
                        scanner.tokenStart()));
            }
            scanner.open();
            Set<String> seen = new HashSet<>();
            boolean first = true;
            while (scanner.nextMember(first)) {
                first = false;
                String name = scanner.name();
                String where = ENVELOPE_MEMBERS.contains(name) ? name : bracket("", name);
                checkName(where);
                if (!seen.add(name)) {
                    throw duplicate(name, where);
                }
                switch (name) {
                    case "format" -> fixed(name, Esj.FORMAT);
                    case "version" -> fixed(name, Esj.formatVersion());
                    case "semanticModel" -> builder.semanticModel(edition());
                    case "values" -> readValues();
                    case "extensions" -> readExtensions();
                    case "source" -> readSource();
                    default -> throw fatal(FindingCode.ESJ_L1_ENVELOPE_MEMBER, where,
                            "the envelope has no member named " + excerpt(name));
                }
            }
            requireMembers(seen);
            scanner.end();
        }

        /**
         * Reports every required envelope member the document lacks, one finding each and
         * in the order {@code format}, {@code version}, {@code semanticModel},
         * {@code values}, with the member's name as the subject (specification, section
         * 9.5). A reader asked to reject throws for the first of them.
         */
        private void requireMembers(Set<String> seen) {
            List<String> missing = REQUIRED_MEMBERS.stream()
                    .filter(required -> !seen.contains(required))
                    .toList();
            if (missing.isEmpty()) {
                return;
            }
            for (String required : missing) {
                String message = "the envelope member " + required + " is required";
                if (collector == null) {
                    throw new EsjFormatException(message, FindingCode.ESJ_L1_ENVELOPE_MEMBER,
                            SemanticPath.root(), required, null);
                }
                collector.add(finding(FindingCode.ESJ_L1_ENVELOPE_MEMBER, required, message));
            }
            throw new Stop();
        }

        /**
         * Reads the {@code semanticModel} member and holds it to the edition grammar of
         * the specification, section 4.4 and to nothing else. Whether a registry for that
         * edition exists is an L2 question, so a document of an edition this
         * implementation knows nothing about is read like any other.
         */
        private String edition() {
            String value = envelopeString("semanticModel");
            if (!Esj.isEdition(value)) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "semanticModel",
                        "the envelope member semanticModel carries " + excerpt(value)
                                + ", which is not an edition of the semantic model;"
                                + " one is written " + Esj.defaultSemanticModel());
            }
            return value;
        }

        private void fixed(String member, String expected) {
            String value = envelopeString(member);
            if (!expected.equals(value)) {
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, member,
                        "the envelope member " + member + " carries " + excerpt(value)
                                + "; its one value is " + expected);
            }
        }

        /**
         * Reads one envelope member that is a JSON string and holds it to the string bound
         * of the specification, section 12.2, after screening it for an unpaired surrogate:
         * a string that has no UTF-8 encoding spells no fixed value and no edition, so a
         * grammar cannot be the first thing said about it (sections 6.8 and 9.6).
         */
        private String envelopeString(String member) {
            Kind kind = scanner.peek();
            if (kind != Kind.STRING) {
                boundNumber(kind, member);
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, member,
                        "the envelope member " + member + " is " + describe(kind)
                                + ", not a string");
            }
            if (scanner.loneSurrogate()) {
                throw fatal(FindingCode.ESJ_L1_SURROGATE, member,
                        "a string carries an unpaired surrogate");
            }
            if (scanner.utf8Length() > limits.maxStringBytes()) {
                throw limit("a string value is longer than " + limits.maxStringBytes()
                        + " bytes", stringBytes(), member, scanner.tokenStart());
            }
            return scanner.text();
        }

        private void readSource() {
            Kind opening = scanner.peek();
            if (opening != Kind.OBJECT) {
                boundNumber(opening, "source");
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "source",
                        "source is " + describe(opening) + ", not a JSON object");
            }
            scanner.open();
            Set<String> seen = new HashSet<>();
            String syntax = null;
            String sha256 = null;
            boolean first = true;
            while (scanner.nextMember(first)) {
                first = false;
                String name = scanner.name();
                boolean defined = name.equals("syntax") || name.equals("sha256");
                String where = defined ? "source." + name : bracket("source", name);
                checkName(where);
                if (!seen.add(name)) {
                    throw duplicate(name, where);
                }
                if (!defined) {
                    throw fatal(FindingCode.ESJ_L1_ENVELOPE_MEMBER, where,
                            "source has no member named " + excerpt(name));
                }
                Kind kind = scanner.peek();
                if (kind != Kind.STRING) {
                    boundNumber(kind, where);
                    throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, where,
                            name + " is " + describe(kind) + ", not a string");
                }
                if (scanner.loneSurrogate()) {
                    throw fatal(FindingCode.ESJ_L1_SURROGATE, where,
                            "a string carries an unpaired surrogate");
                }
                if (scanner.utf8Length() == 0) {
                    throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, where,
                            name + " is the empty string");
                }
                if (scanner.utf8Length() > limits.maxStringBytes()) {
                    throw limit("a string value is longer than " + limits.maxStringBytes()
                            + " bytes", stringBytes(), where, scanner.tokenStart());
                }
                String value = scanner.text();
                if (name.equals("syntax")) {
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

        private void readValues() {
            Kind opening = scanner.peek();
            if (opening != Kind.OBJECT) {
                boundNumber(opening, "values");
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "values",
                        "values is " + describe(opening) + ", not a JSON object");
            }
            scanner.open();
            Set<String> seen = new HashSet<>();
            boolean first = true;
            while (scanner.nextMember(first)) {
                first = false;
                String name = scanner.name();
                int nameAt = scanner.tokenStart();
                long nameBytes = scanner.utf8Length();
                String where = bracket("values", name);
                checkName(where);
                if (!seen.add(name)) {
                    throw duplicate(name, where);
                }
                if (++valueCount > limits.maxValues()) {
                    throw limit("values carries more than " + limits.maxValues() + " members",
                            new Bound("maxValues", limits.maxValues(), "members"), where,
                            nameAt);
                }
                if (nameBytes > limits.maxPathBytes()) {
                    throw limit("a semantic path is longer than " + limits.maxPathBytes()
                            + " bytes", new Bound("maxPathBytes", limits.maxPathBytes(), "bytes"),
                            where, nameAt);
                }
                SemanticPath path = readPath(name, where, nameAt);
                currentPath = path;
                SemanticValue value = readValue(where);
                if (path != null && value != null) {
                    builder.put(path, value);
                }
                currentPath = null;
            }
        }

        private SemanticPath readPath(String name, String where, int nameAt) {
            SemanticPath path;
            try {
                path = SemanticPath.of(name);
            } catch (EsjFormatException e) {
                report(FindingCode.ESJ_L1_PATH_SYNTAX, where, excerpt(e.getMessage()));
                return null;
            }
            if (path.segments().size() > limits.maxPathSegments()) {
                throw limit("a semantic path has more than " + limits.maxPathSegments()
                        + " segments",
                        new Bound("maxPathSegments", limits.maxPathSegments(), "segments"),
                        where, nameAt);
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
        private SemanticValue readValue(String where) {
            Kind kind = scanner.peek();
            switch (kind) {
                case STRING -> {
                    if (scanner.loneSurrogate()) {
                        scanner.consume();
                        report(FindingCode.ESJ_L1_SURROGATE, where,
                                "a string carries an unpaired surrogate");
                        return null;
                    }
                    if (scanner.normalizedUtf8Length() > limits.maxStringBytes()) {
                        throw limit("a string value is longer than " + limits.maxStringBytes()
                                + " bytes", stringBytes(), where, scanner.tokenStart());
                    }
                    String content = Esj.normalizeLineEndings(scanner.text());
                    if (content.isEmpty()) {
                        report(FindingCode.ESJ_L1_EMPTY_STRING, where,
                                "this value is the empty string");
                        return null;
                    }
                    return SemanticValue.of(content);
                }
                case OBJECT -> {
                    return readValueObject(where);
                }
                case ARRAY -> {
                    skipValue(where, 1);
                    report(FindingCode.ESJ_L1_JSON_TYPE, where, "this member of values is an"
                            + " array, not a string and not a value object");
                    return null;
                }
                default -> {
                    boundNumber(kind, where);
                    String type = describe(kind);
                    scanner.consume();
                    report(FindingCode.ESJ_L1_JSON_TYPE, where, "this member of values is "
                            + type + ", not a string and not a value object");
                    return null;
                }
            }
        }

        /**
         * Writes the member access of one member of a value object: in dotted form for the
         * five members the specification defines there, and in brackets for any other name
         * (specification, section 9.5).
         */
        private String memberWhere(String where, String name) {
            return VALUE_MEMBERS.contains(name) ? where + "." + name : bracket(where, name);
        }

        /**
         * Reads a value written as a JSON object. The members are collected before any of
         * them is judged, because the specification, section 9.6 decides the code from
         * the object and not from the member a reader happens to meet first: an object
         * with no supplementary component is {@code ESJ-L1-VALUE-SHAPE} whatever else it
         * carries, the shape of a member outranks the member set, and the member set
         * outranks the content of a member. A lone surrogate in a member's string stands
         * outside that order and is reported beside the code the order gives, because the
         * two checks read two different strings. A member <em>name</em> is judged where
         * the text reaches it, before the value written under it: a name with a lone
         * surrogate and a name that occurs twice leave no object for the order to judge.
         *
         * <p>Collecting is bounded while it happens, as the specification, section 12.2
         * asks of every container: the members of one value object are counted as they
         * arrive, so that a value object with a million members is refused instead of
         * held, and a string longer than either string bound allows is refused before it
         * is built. At most five members are ever legal, so the default bound leaves every
         * document the specification allows untouched.
         */
        private SemanticValue readValueObject(String where) {
            scanner.open();
            List<Member> members = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            boolean component = false;
            boolean first = true;
            while (scanner.nextMember(first)) {
                first = false;
                String name = scanner.name();
                int nameAt = scanner.tokenStart();
                String member = memberWhere(where, name);
                checkName(member, where);
                if (!seen.add(name)) {
                    throw duplicate(name, where);
                }
                if (members.size() >= limits.maxValueMembers()) {
                    throw limit("a value object carries more than "
                            + limits.maxValueMembers() + " members",
                            new Bound("maxValueMembers", limits.maxValueMembers(), "members"),
                            where, nameAt);
                }
                component |= COMPONENT_MEMBERS.contains(name);
                Kind kind = scanner.peek();
                switch (kind) {
                    case STRING -> {
                        boundMemberString(name, member);
                        members.add(new Member(name, kind, scanner.text(), "a string"));
                    }
                    case OBJECT, ARRAY -> {
                        String type = describe(kind);
                        skipValue(member, 2);
                        members.add(new Member(name, kind, null, type));
                    }
                    default -> {
                        boundNumber(kind, member);
                        String type = describe(kind);
                        scanner.consume();
                        members.add(new Member(name, kind, null, type));
                    }
                }
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
                if (member.kind() == Kind.OBJECT) {
                    report(FindingCode.ESJ_L1_VALUE_SHAPE, memberWhere(where, member.name()),
                            excerpt(member.name()) + " is an object, not a string");
                    return null;
                }
            }
            for (Member member : members) {
                if (member.kind() != Kind.STRING) {
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
         * Refuses a string of a value object before it is built where no bound of the
         * specification, section 12.2 could take it: a supplementary component is held to
         * the string bound, and the {@code value} member to the larger of the two string
         * bounds, because whether it is the content of a binary object is decided by the
         * members that may still follow. The exact bound of the {@code value} member is
         * applied once the object is read.
         */
        private void boundMemberString(String name, String member) {
            boolean value = name.equals("value");
            long bound = value
                    ? Math.max(limits.maxStringBytes(), limits.maxBinaryValueBytes())
                    : limits.maxStringBytes();
            if (scanner.normalizedUtf8Length() > bound) {
                boolean binary = value && limits.maxBinaryValueBytes() > limits.maxStringBytes();
                throw limit((binary ? "a binary value is longer than " : "a string value is"
                        + " longer than ") + bound + " bytes",
                        new Bound(binary ? "maxBinaryValueBytes" : "maxStringBytes", bound,
                                "bytes"), member, scanner.tokenStart());
            }
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
         * <p>A member that is itself a JSON object or array is not descended into, because
         * the shape is what is reported and the subtree below it is not examined further.
         */
        private boolean reportSurrogates(List<Member> members, String where) {
            boolean found = false;
            for (Member member : members) {
                if (member.kind() == Kind.STRING && Texts.hasLoneSurrogate(member.text())) {
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
                        + " longer than ") + bound + " bytes",
                        new Bound(binary ? "maxBinaryValueBytes" : "maxStringBytes", bound,
                                "bytes"), where, -1);
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
                        + limits.maxTotalBinaryBytes() + " bytes",
                        new Bound("maxTotalBinaryBytes", limits.maxTotalBinaryBytes(), "bytes"),
                        where, -1);
            }
            binaryBytes += decoded;
        }

        private void readExtensions() {
            Kind opening = scanner.peek();
            if (opening != Kind.OBJECT) {
                boundNumber(opening, "extensions");
                throw fatal(FindingCode.ESJ_L1_ENVELOPE_VALUE, "extensions",
                        "extensions is " + describe(opening) + ", not a JSON object");
            }
            scanner.open();
            Set<String> seen = new HashSet<>();
            boolean first = true;
            while (scanner.nextMember(first)) {
                first = false;
                String owner = scanner.name();
                String where = bracket("extensions", owner);
                checkName(where);
                if (!seen.add(owner)) {
                    throw duplicate(owner, where);
                }
                if (!Texts.isOwnerToken(owner)) {
                    throw fatal(FindingCode.ESJ_L1_OWNER_TOKEN, where,
                            "the owner token " + excerpt(owner) + " "
                                    + Texts.ownerTokenViolation(owner));
                }
                builder.extension(owner, readExtensionValue(Where.of(where)));
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
        private ExtensionValue readExtensionValue(Where root) {
            Deque<Container> open = new ArrayDeque<>();
            Where where = root;
            int depth = 1;
            ExtensionValue finished = null;
            while (true) {
                if (finished == null) {
                    countExtensionNode(where);
                    Kind kind = scanner.peek();
                    if (kind == Kind.OBJECT || kind == Kind.ARRAY) {
                        if (depth > limits.maxExtensionDepth()) {
                            throw limit("extensions is nested deeper than "
                                    + limits.maxExtensionDepth() + " levels",
                                    extensionDepth(), where.text(), scanner.tokenStart());
                        }
                        scanner.open();
                        open.push(new Container(kind == Kind.OBJECT, where, depth));
                    } else {
                        finished = scalar(kind, where);
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
                if (container.object()) {
                    if (!scanner.nextMember(container.first())) {
                        finished = container.build();
                        open.pop();
                        continue;
                    }
                    String name = scanner.name();
                    Where member = container.where().child(name);
                    checkName(member);
                    if (container.has(name)) {
                        throw duplicate(name, member.text());
                    }
                    container.expect(name);
                    where = member;
                } else {
                    if (!scanner.nextElement(container.first())) {
                        finished = container.build();
                        open.pop();
                        continue;
                    }
                    where = container.where().child(container.size());
                }
                container.started();
                depth = container.depth() + 1;
            }
        }

        /** Counts one node of {@code extensions} against the bound of section 12.2. */
        private void countExtensionNode(Where where) {
            if (++extensionNodes > limits.maxExtensionNodes()) {
                throw limit("extensions carries more than " + limits.maxExtensionNodes()
                        + " nodes",
                        new Bound("maxExtensionNodes", limits.maxExtensionNodes(), "nodes"),
                        where.text(), -1);
            }
        }

        private ExtensionValue scalar(Kind kind, Where where) {
            switch (kind) {
                case STRING -> {
                    if (scanner.loneSurrogate()) {
                        throw fatal(FindingCode.ESJ_L1_SURROGATE, where.text(),
                                "a string carries an unpaired surrogate");
                    }
                    if (scanner.utf8Length() > limits.maxStringBytes()) {
                        throw limit("a string inside extensions is longer than "
                                + limits.maxStringBytes() + " bytes", stringBytes(),
                                where.text(), scanner.tokenStart());
                    }
                    return ExtensionValue.of(scanner.text());
                }
                case NUMBER -> {
                    return ExtensionValue.of(number(where));
                }
                case TRUE -> {
                    scanner.consume();
                    return ExtensionValue.of(true);
                }
                case FALSE -> {
                    scanner.consume();
                    return ExtensionValue.of(false);
                }
                case NULL -> {
                    scanner.consume();
                    return ExtensionValue.nullValue();
                }
                default -> throw new IllegalStateException("not a scalar: " + kind);
            }
        }

        /**
         * Reads one number of {@code extensions}. The spelling is measured before the
         * number is built, because the specification, section 12.2 bounds the token at the
         * string bound exactly: a spelling of any length may canonicalize to a short
         * number, so the bound on the canonical form cannot size what the reader holds.
         */
        private BigDecimal number(Where where) {
            if (scanner.tokenLength() > limits.maxStringBytes()) {
                throw limit("a number inside extensions is spelled in more than "
                        + limits.maxStringBytes() + " bytes", stringBytes(), where.text(),
                        scanner.tokenStart());
            }
            String canonical = Decimals.canonicalize(scanner.numberText());
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
         * <p>Nothing below the member is judged but what the walk itself needs: that the
         * text is JSON, and the bounds on depth, on the spelling of a number and on a member
         * name. A string there is checked to be JSON and never built, so the bound on what a
         * reader holds does not reach it; the bound on the document does. A lone surrogate
         * and a repeated member name in the subtree are no findings (section 9.6), and a
         * bound met there names the member the walk is past, not a place inside it, because
         * that member is what the reader judged.
         *
         * @param where the member access of the member whose value is walked past
         * @param level the level the container the walk starts at occupies
         * @throws EsjLimitException if the walk meets a bound of section 12.2
         */
        private void skipValue(String where, int level) {
            Deque<boolean[]> open = new ArrayDeque<>();
            int depth = level;
            while (true) {
                Kind kind = scanner.peek();
                if (kind == Kind.OBJECT || kind == Kind.ARRAY) {
                    if (depth > limits.maxExtensionDepth()) {
                        throw limit("a value nests deeper than " + limits.maxExtensionDepth()
                                + " levels", extensionDepth(), where, scanner.tokenStart());
                    }
                    scanner.open();
                    open.push(new boolean[] {kind == Kind.OBJECT, true});
                    depth++;
                } else {
                    boundNumber(kind, where);
                    scanner.consume();
                }
                while (true) {
                    if (open.isEmpty()) {
                        return;
                    }
                    boolean[] top = open.peek();
                    boolean more = top[0]
                            ? scanner.nextMember(top[1])
                            : scanner.nextElement(top[1]);
                    if (more) {
                        top[1] = false;
                        if (top[0]) {
                            scanner.skipName();
                            if (scanner.utf8Length() > limits.maxStringBytes()) {
                                throw limit("a member name is longer than "
                                        + limits.maxStringBytes() + " bytes", stringBytes(),
                                        where, scanner.tokenStart());
                            }
                        }
                        break;
                    }
                    open.pop();
                    depth--;
                }
            }
        }

        /**
         * Holds a number token to the string bound of the specification, section 12.2
         * wherever it stands: at a place where its type is already wrong, and in a subtree
         * the reader walks past, as much as inside {@code extensions}. The bound is on the
         * spelling, decided before the number is built and before its type is judged,
         * because the reader stops there rather than finishing the judgement (section 9.6).
         * A string at such a place is checked to be JSON and is never built, so the bound on
         * what a reader holds does not reach it.
         */
        private void boundNumber(Kind kind, String where) {
            if (kind == Kind.NUMBER && scanner.tokenLength() > limits.maxStringBytes()) {
                throw limit("a number is spelled in more than " + limits.maxStringBytes()
                        + " bytes", stringBytes(), where, scanner.tokenStart());
            }
        }

        /**
         * Judges the member name the scanner just read against the two rules that hold
         * against a name wherever it stands: it is no longer than the string bound of
         * section 12.2, counted in UTF-8 bytes, and it carries no unpaired surrogate, which
         * would leave it with no UTF-8 encoding (section 6.8). Either ends the read. The bound
         * is asked first, because it decides whether the name is held at all.
         *
         * @param where the member access of the member the name belongs to, which is the
         *              subject of either finding
         */
        private void checkName(String where) {
            checkName(where, where);
        }

        /**
         * Judges a member name as {@link #checkName(String)} does, naming a surrogate in it by
         * another subject than the bound: inside a value object, a finding about a member
         * name is a finding about the value object (specification, section 9.5).
         *
         * @param where  the member access of the member the name belongs to, which a bound
         *               the name reaches names
         * @param judged what a surrogate in the name is reported about
         */
        private void checkName(String where, String judged) {
            if (scanner.utf8Length() > limits.maxStringBytes()) {
                throw limit("a member name is longer than " + limits.maxStringBytes()
                        + " bytes", stringBytes(), where, scanner.tokenStart());
            }
            if (scanner.loneSurrogate()) {
                throw fatal(FindingCode.ESJ_L1_SURROGATE, judged,
                        "a member name carries an unpaired surrogate");
            }
        }

        /** Judges a member name below an owner token, writing its place only if needed. */
        private void checkName(Where where) {
            if (scanner.loneSurrogate() || scanner.utf8Length() > limits.maxStringBytes()) {
                checkName(where.text());
            }
        }

        /**
         * Names the JSON type of the token the scanner stands on, so that a message can say
         * what the document carries instead of restating the rule it broke.
         *
         * @param kind the kind of the token
         * @return a noun phrase such as {@code the number 10} or {@code an array}
         */
        private String describe(Kind kind) {
            return switch (kind) {
                case NUMBER -> "the number " + excerpt(scanner.numberExcerpt(MESSAGE_EXCERPT + 1));
                case TRUE -> "the boolean true";
                case FALSE -> "the boolean false";
                case NULL -> "null";
                case ARRAY -> "an array";
                case OBJECT -> "an object";
                case STRING -> "a string";
            };
        }

        private RuntimeException duplicate(String name, String where) {
            return fatal(FindingCode.ESJ_L1_DUPLICATE_MEMBER, where,
                    "the member name " + excerpt(name) + " occurs twice in one object");
        }

        /**
         * Reports a bound of the specification, section 12.2 and returns what to throw for
         * it.
         *
         * @param message what was exceeded
         * @param bound   the bound, named as {@link Limits} names it
         * @param where   the member access of the member whose name or value reached the
         *                bound, or the empty string where none did
         * @param offset  the byte offset of the token that reached it, or {@code -1}
         * @return the exception to throw
         */
        private EsjLimitException limit(String message, Bound bound, String where, long offset) {
            String text = offset < 0 ? message : message + ", at byte " + offset;
            if (collector != null) {
                collector.add(finding(FindingCode.ESJ_L1_LIMIT, where, text));
            }
            return new EsjLimitException(text, bound, path(FindingCode.ESJ_L1_LIMIT), where,
                    null);
        }

        private Bound stringBytes() {
            return new Bound("maxStringBytes", limits.maxStringBytes(), "bytes");
        }

        private Bound extensionDepth() {
            return new Bound("maxExtensionDepth", limits.maxExtensionDepth(), "levels");
        }

        /**
         * Reports a byte sequence that is no JSON text. It is a finding about the document
         * and not about a member, so its path and its subject are empty, and its message
         * names the byte offset of the token it was met in (specification, sections 9.5
         * and 9.6).
         */
        private RuntimeException malformed(Malformed cause) {
            String text = "the document is not a JSON text: " + cause.getMessage()
                    + ", at byte " + cause.offset();
            return fatal(FindingCode.ESJ_L1_JSON, "", text);
        }

        /** Reports a problem that ends the parse, and returns what to throw for it. */
        private RuntimeException fatal(FindingCode code, String where, String message) {
            if (collector != null) {
                collector.add(finding(code, where, message));
                return new Stop();
            }
            return new EsjFormatException(message, code, path(code), where, null);
        }

        /** Reports a problem that is local to one member of {@code values}. */
        private void report(FindingCode code, String where, String message) {
            if (collector != null) {
                collector.add(finding(code, where, message));
                return;
            }
            throw new EsjFormatException(message, code, path(code), where, null);
        }

        /**
         * Returns the path a finding of this code carries here: the member of
         * {@code values} the reader is inside where its name is a path, and the root path
         * where it is inside none and for {@code ESJ-L1-JSON}, which is about the document
         * as a whole (specification, section 9.5).
         */
        private SemanticPath path(FindingCode code) {
            return currentPath == null || code == FindingCode.ESJ_L1_JSON
                    ? SemanticPath.root()
                    : currentPath;
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
                    : message + " (at " + Messages.abbreviated(where, LOCATION_EXCERPT) + ")";
            return Finding.about(path(code), where, code, text);
        }
    }
}
