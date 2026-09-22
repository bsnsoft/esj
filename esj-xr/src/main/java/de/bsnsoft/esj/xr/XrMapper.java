package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.handler.SemanticHandler;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportReport;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

/**
 * Walks an XR tree and reports what it finds to a {@link SemanticHandler}.
 *
 * <p>The mapper knows two things: the four attributes the XR representation carries, and
 * the registry. Everything else it asks: whether an identifier is a group or a term,
 * where in the document that term may sit, whether its segment carries an occurrence
 * index and which semantic data type its content spells.
 *
 * <p>The position of an element in the XR tree is not taken over unchanged, because the
 * two trees are not the same shape. The XR representation flattens a group here and
 * there — BG-14 sits beside BG-13 in it and inside it in the standard — so the mapper
 * takes the chain of enclosing groups it has resolved so far and asks the registry for a
 * parent chain that continues it. Groups the chain passes through on the way are opened
 * implicitly, which is sound exactly because a group that may occur more than once is
 * never one of them: an implicit occurrence would have no index to carry. That arithmetic
 * is {@link XrPlacement}, which the exporter walks in the other direction.
 *
 * <p>A group the registry records inside itself nests without bound in the registry: the
 * semantic path grows by one occurrence per level, while the chain the registry is asked
 * about stays the chain of the outermost one. The sub invoice line of the XRechnung
 * extension is the one such group of this release.
 *
 * <p>An element the registry cannot place, or whose content spells no value of its type,
 * is skipped and the reason is written into the report. The importer does not guess.
 *
 * <h2>What bounds the document</h2>
 *
 * <p>The mapper writes for a reader, and it measures what it writes against that reader's
 * {@link Limits} while it writes it: the two bounds on a semantic path, the number of
 * members of {@code values}, the length of every string value, the size of a binary
 * value and of all binary content together, and the size of the canonical document
 * itself. What does not fit is left out and named in the report as
 * {@link ImportNote.Kind#PATH_TOO_LONG} or {@link ImportNote.Kind#LIMIT_REACHED}, rather
 * than written into a document that looks complete and that such a reader refuses whole.
 * A bound on the whole document — its size or the number of its values — ends the walk
 * where it is reached, because every further element would only repeat the same note.
 *
 * <p>The size of the canonical document is counted exactly rather than estimated: the
 * caller passes the length of the canonical form of the same document without any value
 * (see {@link #map}), and every value adds the bytes its member takes in that form.
 *
 * <h2>Occurrence indices</h2>
 *
 * <p>An index is spent on what is recorded, not on what is visited. A value term takes
 * its index once its value is built and accepted; a group is walked under a provisional
 * index and keeps it only if at least one value was written below it. An element that
 * produced nothing therefore leaves no gap behind, which is what the specification,
 * section 5.4 asks of the indices.
 */
final class XrMapper {

    private static final QName ID = new QName("xr", XrTransformer.XR_NAMESPACE, "id");
    private static final QName SCHEME_IDENTIFIER = new QName("scheme_identifier");
    private static final QName SCHEME_VERSION_IDENTIFIER = new QName("scheme_version_identifier");
    private static final QName MIME_CODE = new QName("mime_code");
    private static final QName FILENAME = new QName("filename");

    /** The fraction digits an amount of EN 16931 carries (clause 6.5, Table 26). */
    private static final int AMOUNT_SCALE = 2;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * The shape a note subject code takes inside a UBL note: a number sign, three capital
     * letters and a number sign, at the very beginning of the note. This is the prefix
     * the CEN validation artefacts read the code out of; see
     * {@link XrNormalization#UBL_NOTE_SUBJECT_CODE} for the rule and its source.
     */
    private static final Pattern NOTE_SUBJECT_CODE =
            Pattern.compile("^#([A-Z]{3})#(.*)$", Pattern.DOTALL);

    /** The invoice note group, the one place where the note subject code prefix sits. */
    private static final String INVOICE_NOTE_GROUP = "BG-1";

    /** The note subject code, written into a UBL note as a prefix rather than on its own. */
    private static final String NOTE_SUBJECT_CODE_TERM = "BT-21";

    /** The invoice note itself. */
    private static final String NOTE_TERM = "BT-22";

    /**
     * The deepest XR element nesting this mapper walks. The walk is recursive, so a
     * document that nests without end would end the call in a {@link StackOverflowError}
     * rather than in an exception this module documents.
     *
     * <p>This bounds elements, not paths, and the two are not the same measure. An
     * element without an {@code xr:id} spends no path segment at all, an element that
     * carries one spends one or two, and a recursive group spends two per level. The
     * bound on the path is therefore a second bound, taken from the limits the importer
     * was given and applied where a path is built; see {@link #fits}. This one only has
     * to keep the stack whole, and it is stated in elements because that is what the
     * recursion counts.
     */
    private static final int MAX_ELEMENT_DEPTH = 64;

    private final Registry registry;
    private final Limits limits;
    private final boolean splitNoteSubjectCode;
    private final List<ImportNote> notes = new ArrayList<>();
    private final Map<SemanticPath, SemanticValue> values = new TreeMap<>();
    private final Map<SemanticPath, Map<String, Integer>> occurrences = new HashMap<>();

    /** The bytes the canonical form of the document built so far would take. */
    private long documentBytes;

    /** The decoded binary content of the document built so far, in bytes. */
    private long binaryBytes;

    /** Set once a bound on the whole document is reached; the walk then ends. */
    private boolean full;

    private XrMapper(Registry registry, Limits limits, long envelopeBytes,
                     boolean splitNoteSubjectCode) {
        this.registry = registry;
        this.limits = limits;
        this.documentBytes = envelopeBytes;
        this.splitNoteSubjectCode = splitNoteSubjectCode;
    }

    /**
     * Maps an XR tree to a handler.
     *
     * @param xrRoot               the {@code xr:invoice} element
     * @param registry             the registry that decides the structure, usually the
     *                             core registry combined with the registry of the
     *                             XRechnung extension
     * @param readerLimits         the limits of the reader the result is written for
     * @param envelopeBytes        the length of the canonical form of the document the
     *                             values are written into, taken while it holds no value
     *                             at all; the mapper adds what every value costs on top
     *                             of it and so knows the size of the document it is
     *                             building without serializing it
     * @param splitNoteSubjectCode whether to split the note subject code out of a
     *                             document level invoice note, see
     *                             {@link XrNormalization#UBL_NOTE_SUBJECT_CODE}
     * @param handler              the handler that receives the group and value events,
     *                             in the canonical path order of the specification,
     *                             section 7.4
     * @return the observations the mapper made
     */
    static ImportReport map(XdmNode xrRoot,
                            Registry registry,
                            Limits readerLimits,
                            long envelopeBytes,
                            boolean splitNoteSubjectCode,
                            SemanticHandler handler) {
        XrMapper mapper =
                new XrMapper(registry, readerLimits, envelopeBytes, splitNoteSubjectCode);
        mapper.walk(xrRoot, SemanticPath.root(), List.of(), 0);
        mapper.emit(handler);
        return new ImportReport(mapper.notes);
    }

    /**
     * Describes an element whose term identifier the registry does not know.
     *
     * <p>Two things are added to the bare fact, because without them the note is read as
     * something it does not say. The first is the size of what was abandoned: one note
     * stands for the whole subtree below the element, and a reader who is told "one
     * observation" has no way to tell a dropped leaf from five sixths of an invoice. This
     * is the one side that walks that subtree, so it counts the term identifiers in it
     * and says so.
     *
     * <p>The second is where the term is defined, where it is defined anywhere this
     * release ships. "No loaded registry knows it" is true and reads as "this importer
     * cannot do that", which for an extension term is the opposite of the truth: the
     * registry that defines it is in the same jar and was not given to the importer. What
     * the note does not name is a command line option, which belongs to the tool that has
     * one and not to this library.
     */
    private String unknownTerm(String id, XdmNode element) {
        StringBuilder message = new StringBuilder("no loaded registry knows ").append(id)
                .append(", so the element and everything below it was skipped");
        int enclosed = termsBelow(element);
        if (enclosed > 0) {
            message.append(", together with the ").append(enclosed)
                    .append(enclosed == 1 ? " term identifier" : " term identifiers")
                    .append(" it encloses");
        }
        if (Registry.xrechnungExtension().term(id).isPresent()) {
            message.append("; the registry of the XRechnung extension defines it");
        }
        return message.toString();
    }

    /**
     * Counts the elements below one element that carry a term identifier.
     *
     * <p>The walk is iterative rather than recursive: this runs over a subtree the mapper
     * has decided not to enter, so nothing has bounded its depth, and the bound that
     * keeps the mapper's own recursion whole says nothing about what lies under an
     * element it refuses. The number of elements is bounded by the size of the input,
     * which the importer bounded before parsing.
     */
    private static int termsBelow(XdmNode element) {
        int count = 0;
        Deque<XdmNode> pending = new ArrayDeque<>();
        pending.push(element);
        while (!pending.isEmpty()) {
            for (XdmNode child : pending.pop().children()) {
                if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                    if (child.getAttributeValue(ID) != null) {
                        count++;
                    }
                    pending.push(child);
                }
            }
        }
        return count;
    }

    /**
     * Walks the children of one XR element.
     *
     * @param parent        the element whose children are visited
     * @param instancePath  the semantic path of the group instance the children lie in
     * @param instanceChain the term identifiers of that path, from the root down
     * @param depth         how many XR elements enclose the children
     * @throws XrLimitException if the element nesting is deeper than
     *                          {@link #MAX_ELEMENT_DEPTH}
     */
    private void walk(XdmNode parent,
                      SemanticPath instancePath,
                      List<String> instanceChain,
                      int depth) {
        if (depth > MAX_ELEMENT_DEPTH) {
            throw new XrLimitException("the XR representation nests elements more than "
                    + MAX_ELEMENT_DEPTH + " deep, and this importer reads no deeper");
        }
        for (XdmNode child : parent.children()) {
            if (full) {
                return;
            }
            if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
                visit(child, instancePath, instanceChain, depth);
            }
        }
    }

    private void visit(XdmNode element,
                       SemanticPath instancePath,
                       List<String> instanceChain,
                       int depth) {
        String id = element.getAttributeValue(ID);
        if (id == null) {
            walk(element, instancePath, instanceChain, depth + 1);
            return;
        }
        Optional<Term> known = registry.term(id);
        if (known.isEmpty()) {
            notes.add(new ImportNote(ImportNote.Kind.UNKNOWN_TERM, location(instanceChain, id),
                    unknownTerm(id, element)));
            return;
        }
        Term term = known.get();
        Optional<List<String>> chain = XrPlacement.continuation(registry, instanceChain, id);
        if (chain.isEmpty()) {
            notes.add(new ImportNote(ImportNote.Kind.UNPLACEABLE, location(instanceChain, id),
                    "the registry records no parent chain that puts " + id + " here, so the"
                            + " element and everything below it was skipped"));
            return;
        }
        SemanticPath parent =
                XrPlacement.instance(registry, instancePath, chain.get(), instanceChain.size());
        int index = term.isRepeatable() ? peek(parent, id) : -1;
        SemanticPath path = XrPlacement.extend(parent, term, index);
        if (!fits(path, term)) {
            return;
        }
        boolean produced;
        if (term.isGroup()) {
            int before = values.size();
            walk(element, path, XrPlacement.resolved(chain.get()), depth + 1);
            produced = values.size() > before;
        } else {
            produced = value(element, term, path, chain.get());
        }
        if (produced && index >= 0) {
            commit(parent, id);
        }
    }

    /**
     * Reports whether a path the mapper is about to record stays inside the bounds the
     * reader of the result applies, and writes a note where it does not.
     *
     * <p>A group is measured with one segment and one byte in reserve. A group holds no
     * value itself, and the shortest path of a value below it is one segment and at least
     * one byte longer, so a group that fills the bound exactly leaves room for nothing
     * and the whole subtree goes rather than every value in it separately.
     *
     * <p>Only the end of a repeated sequence can be dropped this way, because the index
     * segments of one group instance grow with the occurrence and never shrink. Dropping
     * it therefore leaves the indices of what remains dense, which is what the
     * specification, section 5.4 asks of them.
     */
    private boolean fits(SemanticPath path, Term term) {
        int reserve = term.isGroup() ? 1 : 0;
        int segments = path.segments().size() + reserve;
        int bytes = path.toString().getBytes(StandardCharsets.UTF_8).length + reserve;
        if (segments <= limits.maxPathSegments() && bytes <= limits.maxPathBytes()) {
            return true;
        }
        String measured = term.isGroup()
                ? "the shortest path a value below " + term.id() + " could take"
                : "the path of " + term.id();
        notes.add(new ImportNote(ImportNote.Kind.PATH_TOO_LONG, path.toString(),
                measured + " has " + segments + " segments and " + bytes + " bytes, more than"
                        + " the " + limits.maxPathSegments() + " segments and "
                        + limits.maxPathBytes() + " bytes this importer writes within, so the"
                        + " element and everything below it was skipped"));
        return false;
    }

    /**
     * Returns the index the next occurrence of a term inside one group instance would
     * take, without spending it. The index is spent by {@link #commit} once the
     * occurrence has produced something, so that an element the mapper refuses leaves the
     * indices of its siblings dense.
     */
    private int peek(SemanticPath instancePath, String id) {
        Map<String, Integer> counters = occurrences.get(instancePath);
        return counters == null ? 0 : counters.getOrDefault(id, 0);
    }

    /** Spends the index {@link #peek} returned, after the occurrence produced a value. */
    private void commit(SemanticPath instancePath, String id) {
        occurrences.computeIfAbsent(instancePath, key -> new HashMap<>())
                .merge(id, 1, Integer::sum);
    }

    /**
     * Builds the value of one element and records it, and reports whether anything
     * reached the document.
     */
    private boolean value(XdmNode element, Term term, SemanticPath path, List<String> chain) {
        SemanticType type = term.datatype().orElse(null);
        if (type == null) {
            notes.add(new ImportNote(ImportNote.Kind.MALFORMED, path.toString(),
                    "the registry gives " + term.id() + " no semantic data type, so its"
                            + " content could not be typed"));
            return false;
        }
        SemanticValue value;
        try {
            value = build(type, element.getStringValue(), element, term, path);
        } catch (EsjFormatException e) {
            notes.add(new ImportNote(ImportNote.Kind.MALFORMED, path.toString(),
                    "the content of " + term.id() + " is no value of the registry datatype "
                            + type.registryDatatype() + ": " + e.getMessage()));
            return false;
        }
        if (value == null) {
            return false;
        }
        if (values.containsKey(path)) {
            notes.add(new ImportNote(ImportNote.Kind.DUPLICATE_PATH, path.toString(),
                    "a second element of the chain " + location(chain) + " resolved to a path"
                            + " that is already taken, so it was skipped"));
            return false;
        }
        if (splitNoteSubjectCode && isDocumentNote(term, chain) && type == SemanticType.TEXT) {
            return note(path, value, term);
        }
        return record(path, value, term.id());
    }

    /** Tells whether a term is the invoice note of the invoice itself, BG-1 / BT-22. */
    private static boolean isDocumentNote(Term term, List<String> chain) {
        return NOTE_TERM.equals(term.id())
                && chain.size() == 2
                && INVOICE_NOTE_GROUP.equals(chain.get(0));
    }

    /**
     * Records an invoice note, with the note subject code split off where the UBL syntax
     * binding wrote it into the text; see {@link XrNormalization#UBL_NOTE_SUBJECT_CODE}.
     *
     * <p>Four cases leave the text alone. A note that does not begin with the prefix is
     * an ordinary note. A note that is nothing but the prefix would leave BT-22 empty,
     * and the specification, section 6.1 has no empty value to write. A BG-1 instance
     * that already carries a BT-21 got it from an element of its own, which the
     * stylesheet recognized and which outranks a prefix. And where the two values
     * together do not fit the limits the importer writes within, the note is kept whole
     * rather than stripped of a code that then goes missing.
     */
    private boolean note(SemanticPath path, SemanticValue text, Term term) {
        Matcher prefix = NOTE_SUBJECT_CODE.matcher(text.canonicalContent());
        Optional<Term> codeTerm = registry.term(NOTE_SUBJECT_CODE_TERM);
        if (!prefix.matches() || prefix.group(2).isEmpty() || codeTerm.isEmpty()) {
            return record(path, text, term.id());
        }
        SemanticPath codePath = XrPlacement.extend(path.parent(), codeTerm.get(), -1);
        if (values.containsKey(codePath)) {
            return record(path, text, term.id());
        }
        SemanticValue code = SemanticValue.of(prefix.group(1));
        SemanticValue rest = SemanticValue.of(prefix.group(2));
        if (!admits(codePath, code, NOTE_SUBJECT_CODE_TERM)
                || !admits(path, rest, term.id())
                || !reserve(2, memberBytes(codePath, code) + memberBytes(path, rest), 0, path)) {
            return record(path, text, term.id());
        }
        values.put(codePath, code);
        values.put(path, rest);
        return true;
    }

    /**
     * Records one value, unless it or the document it would grow falls outside the limits
     * the importer writes within.
     */
    private boolean record(SemanticPath path, SemanticValue value, String id) {
        if (!admits(path, value, id)) {
            return false;
        }
        if (!reserve(1, memberBytes(path, value), decodedBytes(value), path)) {
            return false;
        }
        values.put(path, value);
        return true;
    }

    /**
     * Reports whether one value stays inside the bounds a reader applies to a single
     * value, and writes a note where it does not. Such a value is left out on its own;
     * the rest of the document is unaffected.
     */
    private boolean admits(SemanticPath path, SemanticValue value, String id) {
        boolean binary = value.mimeCode() != null || value.filename() != null;
        long bound = binary ? limits.maxBinaryValueBytes() : limits.maxStringBytes();
        long content = utf8Length(value.canonicalContent());
        if (content > bound) {
            // Which of the two bounds it is belongs in the sentence: the two are raised
            // separately, and a caller that has to ask which one it met has been told
            // nothing it can act on.
            return tooLarge(path, "the " + (binary ? "binary" : "string") + " value of " + id
                    + " is " + content + " bytes long, more than the " + bound + " this"
                    + " importer writes within, so it was left out");
        }
        for (String component : components(value)) {
            long length = utf8Length(component);
            if (length > limits.maxStringBytes()) {
                return tooLarge(path, "a supplementary component of " + id + " is " + length
                        + " bytes long, more than the " + limits.maxStringBytes() + " this"
                        + " importer writes within, so the value was left out");
            }
        }
        return true;
    }

    /**
     * Takes room for one or two values out of what is left of the document, and reports
     * whether there was room. A bound on the whole document ends the walk where it is
     * reached; the bound on the binary content of a document does not, because a further
     * attachment may still be small enough to fit.
     */
    private boolean reserve(int count, long bytes, long binary, SemanticPath where) {
        if (values.size() + count > limits.maxValues()) {
            full = true;
            return tooLarge(where, "the document reached the " + limits.maxValues() + " values"
                    + " this importer writes within, so this value and the rest of the source"
                    + " document were left out");
        }
        long separators = values.isEmpty() ? count - 1L : count;
        if (documentBytes + bytes + separators > limits.maxDocumentBytes()) {
            full = true;
            return tooLarge(where, "the canonical form of the document reached the "
                    + limits.maxDocumentBytes() + " bytes this importer writes within, so this"
                    + " value and the rest of the source document were left out");
        }
        if (binaryBytes + binary > limits.maxTotalBinaryBytes()) {
            return tooLarge(where, "the binary content of the document would exceed the "
                    + limits.maxTotalBinaryBytes() + " bytes this importer writes within, so"
                    + " this value was left out");
        }
        documentBytes += bytes + separators;
        binaryBytes += binary;
        return true;
    }

    /** Writes one limit note and returns {@code false}, which is what every caller wants. */
    private boolean tooLarge(SemanticPath path, String message) {
        notes.add(new ImportNote(ImportNote.Kind.LIMIT_REACHED, path.toString(), message));
        return false;
    }

    /** Returns the supplementary components of a value, in the order they are written. */
    private static List<String> components(SemanticValue value) {
        List<String> components = new ArrayList<>(4);
        for (String component : new String[] {value.scheme(), value.schemeVersion(),
                value.mimeCode(), value.filename()}) {
            if (component != null) {
                components.add(component);
            }
        }
        return components;
    }

    /**
     * Returns the decoded binary content a value adds to the document, in bytes. It is
     * read off the length of the canonical base64 the value carries, so that the bytes
     * are not decoded a second time to be counted.
     */
    private static long decodedBytes(SemanticValue value) {
        if (value.mimeCode() == null && value.filename() == null) {
            return 0L;
        }
        String base64 = value.canonicalContent();
        long padding = base64.endsWith("==") ? 2L : base64.endsWith("=") ? 1L : 0L;
        return base64.length() / 4L * 3L - padding;
    }

    /**
     * Returns the bytes one member of {@code values} takes in the canonical form: the
     * path as a JSON string, the colon, and the value — the content as a JSON string
     * where it carries no supplementary component, and otherwise the object with its
     * members in the order of the specification, section 7.3. The separating comma is
     * not counted here, because it belongs to the member before it; {@link #reserve}
     * adds it.
     */
    private static long memberBytes(SemanticPath path, SemanticValue value) {
        long bytes = jsonBytes(path.toString()) + 1;
        if (!value.hasComponents()) {
            return bytes + jsonBytes(value.canonicalContent());
        }
        bytes += 2;
        bytes += 8 + jsonBytes(value.canonicalContent());
        if (value.scheme() != null) {
            bytes += 10 + jsonBytes(value.scheme());
        }
        if (value.schemeVersion() != null) {
            bytes += 17 + jsonBytes(value.schemeVersion());
        }
        if (value.mimeCode() != null) {
            bytes += 12 + jsonBytes(value.mimeCode());
        }
        if (value.filename() != null) {
            bytes += 12 + jsonBytes(value.filename());
        }
        return bytes;
    }

    /**
     * Returns the bytes a string takes as a JSON string in the canonical form, the two
     * quotation marks included: the escaping of the specification, section 7.5 over the
     * UTF-8 encoding of the content. An unpaired surrogate has no encoding at all and is
     * counted as one byte here; the writer refuses it later, where it can say so.
     */
    private static long jsonBytes(String value) {
        long bytes = 2;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\' || c == '\b' || c == '\t' || c == '\n' || c == '\f'
                    || c == '\r') {
                bytes += 2;
            } else if (c < 0x20) {
                bytes += 6;
            } else if (c < 0x80) {
                bytes += 1;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else if (Character.isSurrogate(c)) {
                bytes += 1;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    /** Returns the bytes of the UTF-8 encoding of a string. */
    private static long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }


    /**
     * Builds one value, or returns {@code null} where the element carries nothing to
     * build one from. Only the text type keeps the whitespace around its content: it is
     * the type whose values carry line breaks, and the specification, section 6.8 does
     * not trim them. For every other type the surrounding whitespace is layout of the
     * source document and not part of the value.
     *
     * <p>A time is kept as the string the element carries. The XR schema binds the 2017
     * edition of the semantic model, whose registry names no term of that type, so no
     * value of it reaches this method from any document this mapper reads.
     */
    private SemanticValue build(SemanticType type,
                                String content,
                                XdmNode element,
                                Term term,
                                SemanticPath path) {
        return switch (type) {
            case TEXT -> content.isEmpty() ? empty(term, path) : SemanticValue.of(content);
            case BINARY_OBJECT -> binaryObject(content, element, term, path);
            case IDENTIFIER -> stripped(content, term, path, v -> identifier(v, element, term, path));
            case CODE, DOCUMENT_REFERENCE, TIME ->
                    stripped(content, term, path, SemanticValue::of);
            case DATE -> stripped(content, term, path, v -> SemanticValue.ofDate(date(v)));
            case AMOUNT -> stripped(content, term, path, v -> amount(v, term, path));
            case UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE ->
                    stripped(content, term, path, v -> SemanticValue.ofDecimal(decimal(v)));
        };
    }

    private SemanticValue stripped(String content,
                                   Term term,
                                   SemanticPath path,
                                   Function<String, SemanticValue> build) {
        String value = content.strip();
        return value.isEmpty() ? empty(term, path) : build.apply(value);
    }

    /**
     * Records that an element carried nothing and returns no value. An empty element is
     * not a defect of the source document: a term that is there but says nothing
     * addresses nothing, and the specification, section 6.1 has no empty value to write
     * for it.
     */
    private SemanticValue empty(Term term, SemanticPath path) {
        notes.add(new ImportNote(ImportNote.Kind.EMPTY, path.toString(),
                "the element of " + term.id() + " carries no content, so no value was written"));
        return null;
    }

    /**
     * Builds an identifier with the supplementary components the registry allows for its
     * term. The XR representation writes a scheme wherever the source syntax had one,
     * including at terms whose semantic data type in the standard has no scheme
     * component — the VAT identifiers are the usual case — and such a scheme is dropped
     * rather than carried into a document that could not hold it.
     *
     * <p>A component the registry declares mandatory and the source did not write is the
     * other way round: the identifier is kept and a note says the component is absent.
     * An importer does not judge a document, and dropping the term would hide the defect
     * from the two engines whose business it is — the structural layer reports
     * {@code ESJ-L2-COMPONENT-MISSING} at that path, and the business rule of the standard
     * that asks for the scheme reports it under the identifier the official artefacts use.
     * A term that never arrives cannot be reported by either.
     */
    private SemanticValue identifier(String value, XdmNode element, Term term, SemanticPath path) {
        String scheme = component(element, SCHEME_IDENTIFIER, Component.Role.SCHEME, term, path);
        String schemeVersion = component(element, SCHEME_VERSION_IDENTIFIER,
                Component.Role.SCHEME_VERSION, term, path);
        if (scheme == null && schemeVersion != null) {
            notes.add(new ImportNote(ImportNote.Kind.COMPONENT_DROPPED, path.toString(),
                    "a scheme version without a scheme is no value of the Identifier type,"
                            + " so it was dropped"));
            schemeVersion = null;
        }
        for (Component component : term.components()) {
            boolean present = component.role() == Component.Role.SCHEME
                    ? scheme != null
                    : schemeVersion != null;
            if (component.isMandatory() && !present) {
                notes.add(new ImportNote(ImportNote.Kind.COMPONENT_MISSING, path.toString(),
                        "the registry declares the " + component.role().jsonMember()
                                + " component of " + term.id() + " as mandatory, and the"
                                + " element arrived without it; the identifier is kept, and"
                                + " the missing component is a finding of the validator"));
            }
        }
        return SemanticValue.identifier(value, scheme, schemeVersion);
    }

    /**
     * Returns the value of a supplementary component, or {@code null} where the element
     * carries none or the registry does not list it for that term.
     *
     * <p>Both of the notes written here are informational. A scheme the registry does not
     * list is not a loss of this document but the standing difference between what the
     * source syntax writes and what the semantic data type of EN 16931 defines: the VAT
     * identifiers carry one in every well-formed CII invoice, so a warning about it would
     * fire on every well-formed CII invoice and say nothing about any of them. An empty
     * attribute carries nothing that could be lost.
     */
    private String component(XdmNode element,
                             QName attribute,
                             Component.Role role,
                             Term term,
                             SemanticPath path) {
        String raw = element.getAttributeValue(attribute);
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            notes.add(new ImportNote(ImportNote.Kind.COMPONENT_DROPPED,
                    ImportNote.Level.INFORMATION, path.toString(),
                    "the " + role.jsonMember() + " of " + term.id() + " is empty,"
                            + " so it was dropped"));
            return null;
        }
        if (term.component(role).isEmpty()) {
            notes.add(new ImportNote(ImportNote.Kind.COMPONENT_DROPPED,
                    ImportNote.Level.INFORMATION, path.toString(),
                    "the registry lists no " + role.jsonMember() + " component for "
                            + term.id() + ", so the one the source syntax carried was dropped"));
            return null;
        }
        return value;
    }

    /**
     * Builds a binary object.
     *
     * <p>This is the one place where a value the source carried does not reach the
     * document. A binary object of this format is its bytes together with its media type
     * and its file name (specification, section 6.7); there is no value of the type with
     * one of the three missing, so an attachment that arrives without one is described in
     * the report and nothing is written for it. The consequence is recorded rather than
     * hidden: {@code conformance/rules/not-applicable.md} names it, and it is why
     * {@code BR-CL-24} cannot be raised from an XML attachment whose media type is absent
     * from the standard's list <em>and</em> whose file name is missing.
     */
    private SemanticValue binaryObject(String content,
                                       XdmNode element,
                                       Term term,
                                       SemanticPath path) {
        String mimeCode = element.getAttributeValue(MIME_CODE);
        String filename = element.getAttributeValue(FILENAME);
        if (mimeCode == null || filename == null) {
            notes.add(new ImportNote(ImportNote.Kind.COMPONENT_MISSING, path.toString(),
                    "a binary object carries a mime code and a file name, and " + term.id()
                            + " arrived without " + (mimeCode == null ? "the mime code"
                            : "the file name")));
            return null;
        }
        String base64 = WHITESPACE.matcher(content).replaceAll("");
        if (base64.isEmpty()) {
            return empty(term, path);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            notes.add(new ImportNote(ImportNote.Kind.MALFORMED, path.toString(),
                    "the content of " + term.id() + " is no base64 encoding of a file"));
            return null;
        }
        return SemanticValue.binary(bytes, mimeCode.strip(), filename.strip());
    }

    /**
     * Reads a calendar date. The stylesheets normalize every date of both syntaxes to the
     * complete calendar date representation, including the eight-digit form CII writes.
     *
     * <p>Where they can tell that a source date is no date at all — the eight digits are
     * not digits, a month is 13, a day is 32 — they put a readable marker in its place,
     * and such a marker arrives here, is no date, and is reported as one malformed value.
     * Where the digits pass those range checks and still name no day of the calendar,
     * the 30th of February being the case to remember, the stylesheet builds the date
     * itself and fails; the transformation then ends in an {@link XrFormatException} and
     * the whole document is lost rather than one value. That is a property of the
     * vendored stylesheets, recorded in {@code conformance/ledger/pairs.md}.
     */
    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new EsjFormatException("a date is written as YYYY-MM-DD", e);
        }
    }

    /**
     * Builds an amount, and records where the source wrote fraction digits the type does
     * not carry.
     *
     * <p>An amount of EN 16931 has at most two fraction digits (clause 6.5, Table 26), and
     * the canonical decimal form of this format writes a number without trailing zeros
     * (specification, section 6.4). The two together mean that an amount written
     * {@code 319.860} in the source becomes {@code 319.86} here: the same number, one
     * notation shorter. Nothing is wrong with the number, but the notation is what the
     * decimal rules of the standard are about, and after this point no engine reading the
     * document can see that the source wrote a third digit. So the difference is written
     * into the report at the path it was made, and a reader comparing the two documents is
     * told rather than left to compare them by hand.
     *
     * <p>A third digit that is not a zero is a different matter and no note is written for
     * it: the value keeps that digit, the structural layer reports it and the business
     * rules of the standard report it, because nothing was lost on the way in.
     */
    private SemanticValue amount(String value, Term term, SemanticPath path) {
        BigDecimal number = decimal(value);
        SemanticValue built = SemanticValue.ofDecimal(number);
        if (number.scale() > AMOUNT_SCALE && built.asDecimal().scale() <= AMOUNT_SCALE) {
            notes.add(new ImportNote(ImportNote.Kind.SCALE_REDUCED,
                    ImportNote.Level.WARNING, path.toString(),
                    "the element of " + term.id() + " is written with " + number.scale()
                            + " fraction digits, which is more than an amount of this standard"
                            + " carries, and the digits beyond the second are zeros; the value"
                            + " is the same number written with " + built.asDecimal().scale()));
        }
        return built;
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new EsjFormatException("a decimal is written in digits, with at most one"
                    + " decimal point and an optional sign", e);
        }
    }

    /**
     * Reports the collected values in canonical path order, opening and closing every
     * group instance exactly once around the values it holds. The order is what makes
     * that possible without look-ahead: the instances of one group follow one another.
     */
    private void emit(SemanticHandler handler) {
        List<SemanticPath> open = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : values.entrySet()) {
            List<SemanticPath> groups = entry.getKey().groupPaths();
            int shared = 0;
            while (shared < open.size() && shared < groups.size()
                    && open.get(shared).equals(groups.get(shared))) {
                shared++;
            }
            close(handler, open, shared);
            for (int i = shared; i < groups.size(); i++) {
                handler.beginGroup(groups.get(i));
                open.add(groups.get(i));
            }
            handler.value(entry.getKey(), entry.getValue());
        }
        close(handler, open, 0);
    }

    private static void close(SemanticHandler handler, List<SemanticPath> open, int depth) {
        while (open.size() > depth) {
            handler.endGroup(open.remove(open.size() - 1));
        }
    }

    private static String location(List<String> chain, String id) {
        return chain.isEmpty() ? id : location(chain) + "/" + id;
    }

    private static String location(List<String> chain) {
        return String.join("/", chain);
    }
}
