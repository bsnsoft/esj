package de.bsnsoft.esj.bindings;

import de.bsnsoft.esj.EsjFormatException;
import de.bsnsoft.esj.PathSegment;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticType;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.handler.SemanticHandler;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.model.Term;
import de.bsnsoft.esj.imports.ImportNote;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the terms a reader matched into the values of a semantic document.
 *
 * <p>The reader knows where a term is written in a syntax; this class knows where it
 * belongs in the semantic model. The two are not the same shape, and the difference is
 * what this class is for: a syntax nests the payee identifier inside the supplier party
 * and the standard puts it in the payee group, a syntax gives the invoice note no group
 * at all and the standard gives it BG-1. The position of an element in the document
 * therefore decides nothing by itself. What decides is the registry: for the term that was
 * matched it gives the chains of business groups the term may sit in, and the innermost
 * open group instance whose chain one of them continues is the instance the value goes
 * into. Groups the chain passes through on the way are opened implicitly.
 *
 * <p>Two kinds of implicit group occur, and both are in this corpus. A group the syntax
 * does not represent and the standard allows once — the process control group of UBL — is
 * opened silently. A group the syntax does not represent and the standard allows many
 * times — the invoice note group of UBL, which lives inside the note element — takes one
 * instance per element that produces a value, because the document holds nothing else to
 * count instances by. A group the table <em>does</em> bind is never opened implicitly: its
 * instances are elements, and an implicit one would have no element to be.
 *
 * <p>Occurrence indices, limits and notes follow the rules of the specification and match
 * what the XSLT path of {@code esj-xr} does, so that the two readers can be compared byte
 * for byte: an index is spent on what is recorded rather than on what is visited, a value
 * that does not fit the limits of the reader the document is written for is left out and
 * named in the report, and a bound on the whole document ends the read where it is
 * reached.
 */
final class SemanticEmitter {

    /**
     * The shape a note subject code takes inside a UBL note: a number sign, three capital
     * letters and a number sign, at the very beginning of the note. This is the prefix the
     * CEN validation artefacts read the code out of, and the binding table marks the term
     * written into it with {@link Flags#SUBJECT_CODE_PREFIX}.
     */
    private static final Pattern SUBJECT_CODE = Pattern.compile("^#([A-Z]{3})#(.*)$",
            Pattern.DOTALL);

    /** The eight-digit calendar date CII writes where the format qualifier is 102. */
    private static final Pattern DATE_102 = Pattern.compile("\\d{8}");

    /** Whitespace inside base64 content, which carries no bits. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Registry registry;
    private final Limits limits;
    private final boolean correcting;
    private final boolean strict;
    private final Set<String> boundGroups;
    private final List<ImportNote> notes = new ArrayList<>();
    private final Map<SemanticPath, SemanticValue> values = new TreeMap<>();
    private final Map<SemanticPath, Map<String, Integer>> occurrences = new HashMap<>();
    private final List<Instance> open = new ArrayList<>();

    /** The group instances that were closed at the level of the document itself. */
    private final Map<String, Instance> closedAtRoot = new LinkedHashMap<>();

    private long documentBytes;
    private long binaryBytes;
    private boolean full;

    /**
     * Creates an emitter.
     *
     * @param registry      the registry that decides the structure
     * @param limits        the limits of the reader the document is written for
     * @param envelopeBytes the length of the canonical form of the document the values are
     *                      written into, taken while it holds no value at all
     * @param correcting    whether the named corrections of {@link ReaderMode#REPAIR} are
     *                      applied
     * @param strict        whether a value that cannot be built ends the read instead of
     *                      becoming a note, which is what {@link ReaderMode#STRICT} asks
     * @param boundGroups   the business groups the binding table of this syntax gives an
     *                      element of their own, which are therefore never opened
     *                      implicitly
     */
    SemanticEmitter(Registry registry,
                    Limits limits,
                    long envelopeBytes,
                    boolean correcting,
                    boolean strict,
                    Set<String> boundGroups) {
        this.registry = registry;
        this.limits = limits;
        this.documentBytes = envelopeBytes;
        this.correcting = correcting;
        this.strict = strict;
        this.boundGroups = Set.copyOf(boundGroups);
    }

    /** Tells whether a bound on the whole document has been reached. */
    boolean isFull() {
        return full;
    }

    /** Returns the observations made so far, in the order they were made. */
    List<ImportNote> notes() {
        return notes;
    }

    /**
     * Opens one instance of a business group.
     *
     * <p>A group the registry cannot place here is recorded and an instance is opened all
     * the same, as a marker that holds no path: the reader closes what it opens, and the
     * values below an unplaceable group are unplaceable in their turn, which is what they
     * are.
     *
     * @param id the group identifier
     */
    void openGroup(String id) {
        Instance skipped = outermostSkipped();
        if (skipped != null) {
            skipped.enclosed++;
            open.add(Instance.skipped(id));
            return;
        }
        Optional<Term> known = registry.term(id);
        if (known.isEmpty()) {
            open.add(Instance.skipped(id, ImportNote.Kind.UNKNOWN_TERM,
                    "no loaded registry knows " + id + ", so the element and everything below"
                            + " it was skipped"));
            return;
        }
        Resolution resolution = resolve(id);
        if (resolution == null) {
            open.add(Instance.skipped(id, ImportNote.Kind.UNPLACEABLE,
                    "the registry records no parent chain that puts " + id + " here, so the"
                            + " element and everything below it was skipped"));
            return;
        }
        Term term = known.get();
        List<Instance> ancestors = new ArrayList<>();
        SemanticPath parent = instance(resolution, ancestors);
        int index = term.isRepeatable() ? peek(parent, id) : -1;
        SemanticPath path = extend(parent, term, index);
        if (!fits(path, term)) {
            open.add(Instance.skipped(id, null, null));
            return;
        }
        open.addAll(ancestors);
        Instance instance =
                new Instance(id, resolved(resolution.chain()), path, parent, index);
        instance.closed.clear();
        instance.pushed = ancestors.size() + 1;
        open.add(instance);
    }

    /**
     * Closes the instance of a business group that {@link #openGroup} opened, together
     * with the groups that were opened implicitly above it, spending the occurrence index
     * of each one that something was written below.
     *
     * @param id the group identifier
     */
    void closeGroup(String id) {
        if (open.isEmpty()) {
            return;
        }
        Instance last = open.get(open.size() - 1);
        int count = last.id.equals(id) ? last.pushed : 1;
        for (int i = 0; i < count && !open.isEmpty(); i++) {
            Instance instance = open.remove(open.size() - 1);
            if (instance.skippedKind != null) {
                notes.add(new ImportNote(instance.skippedKind, location(instance.id),
                        skippedMessage(instance)));
            }
            if (instance.produced && instance.index >= 0) {
                commit(instance.parent, instance.id);
            }
            if (instance.path != null) {
                closedAt().put(instance.id, instance);
            }
        }
    }

    /**
     * Writes one value.
     *
     * @param termId          the term identifier
     * @param content         the content of the element or attribute that carried it
     * @param components      the supplementary components the syntax carried, by role
     * @param dateFormat102   whether the content is a CII eight-digit calendar date
     * @param subjectCodeTerm the term whose value is written as a prefix of this one, or
     *                        {@code null} where none is
     * @param alternatives    whether the table binds this term to more than one XPath, so
     *                        that a second element carrying it is an alternative spelling
     *                        of the same value rather than a second value
     * @return {@code true} if something reached the document
     */
    boolean value(String termId,
                  String content,
                  Map<Component.Role, String> components,
                  boolean dateFormat102,
                  String subjectCodeTerm,
                  boolean alternatives) {
        Instance skipped = outermostSkipped();
        if (skipped != null) {
            // The note of the group this value sits in stands for the whole subtree, as
            // one note per value below a group the reader never entered would bury it.
            skipped.enclosed++;
            return false;
        }
        Optional<Term> known = registry.term(termId);
        if (known.isEmpty()) {
            notes.add(new ImportNote(ImportNote.Kind.UNKNOWN_TERM, location(termId),
                    "no loaded registry knows " + termId + ", so its value was skipped"));
            return false;
        }
        Term term = known.get();
        Resolution resolution = resolve(termId);
        if (resolution == null) {
            notes.add(new ImportNote(ImportNote.Kind.UNPLACEABLE, location(termId),
                    "the registry records no parent chain that puts " + termId + " here, so"
                            + " its value was skipped"));
            return false;
        }
        List<Instance> ancestors = new ArrayList<>();
        SemanticPath parent = instance(resolution, ancestors);
        int index = term.isRepeatable() ? peek(parent, termId) : -1;
        SemanticPath path = extend(parent, term, index);
        if (!fits(path, term)) {
            return false;
        }
        if (alternatives && values.containsKey(path)) {
            // The term is bound to several XPaths and one of them has already been read.
            // A syntax that offers two spellings of one value and writes both says the
            // same thing twice; the first is the value and the second is not a loss.
            return false;
        }
        SemanticValue value = build(term, content, components, dateFormat102, path);
        if (value == null) {
            return false;
        }
        boolean written = subjectCodeTerm != null && correcting
                && term.datatype().orElse(null) == SemanticType.TEXT
                ? withSubjectCode(path, value, term, subjectCodeTerm)
                : record(path, value, termId);
        if (written) {
            for (Instance ancestor : ancestors) {
                if (ancestor.index >= 0) {
                    commit(ancestor.parent, ancestor.id);
                }
            }
            if (index >= 0) {
                commit(parent, termId);
            }
        }
        return written;
    }

    /**
     * Records that an element carried the value the binding table of its syntax is
     * written with where a document states no such term, so the term was not read.
     *
     * @param termId  the term the element would have carried
     * @param value   the conventional value it carries instead
     * @param element the path of the element, as the binding table writes it
     * @param beside  the prefixed name of the sibling the convention is recognized beside
     */
    void convention(String termId, String value, String element, String beside) {
        notes.add(new ImportNote(ImportNote.Kind.CONVENTION_NOT_READ,
                ImportNote.Level.INFORMATION, location(termId), element + " carries "
                        + value + " beside " + beside + ", which is what this syntax is"
                        + " written with where a document states no " + termId + ", so it"
                        + " was not read as " + termId));
    }

    /**
     * Records that an element carried more content than a single value may, so that
     * nothing of it was kept.
     *
     * <p>The bound is the same one {@link #admits} applies, and the note reads the same;
     * what differs is that the reader met it while reading rather than after, so the value
     * never existed and the heap the read needed is the bound rather than the document.
     *
     * @param termId the term the element carried
     * @param binary whether the term carries binary content, which has a bound of its own
     * @param bound  the characters a value of this term may carry
     */
    void overlong(String termId, boolean binary, long bound) {
        notes.add(new ImportNote(ImportNote.Kind.LIMIT_REACHED, location(termId),
                "the " + (binary ? "binary" : "string") + " value of " + termId + " carries"
                        + " more than the " + bound + " bytes this reader writes within, so"
                        + " it was left out without being read whole"));
    }

    /**
     * Reports the collected values to a handler in canonical path order, opening and
     * closing every group instance exactly once around the values it holds.
     *
     * @param handler the handler
     */
    void emit(SemanticHandler handler) {
        List<SemanticPath> opened = new ArrayList<>();
        for (Map.Entry<SemanticPath, SemanticValue> entry : values.entrySet()) {
            List<SemanticPath> groups = entry.getKey().groupPaths();
            int shared = 0;
            while (shared < opened.size() && shared < groups.size()
                    && opened.get(shared).equals(groups.get(shared))) {
                shared++;
            }
            close(handler, opened, shared);
            for (int i = shared; i < groups.size(); i++) {
                handler.beginGroup(groups.get(i));
                opened.add(groups.get(i));
            }
            handler.value(entry.getKey(), entry.getValue());
        }
        close(handler, opened, 0);
    }

    private static void close(SemanticHandler handler, List<SemanticPath> opened, int depth) {
        while (opened.size() > depth) {
            handler.endGroup(opened.remove(opened.size() - 1));
        }
    }

    /**
     * Returns the outermost open instance the reader could not place, or {@code null}
     * where every open instance has a path.
     *
     * <p>Everything the document writes inside such an instance belongs to it, and the
     * note that instance carries stands for all of it. The <em>outermost</em> one is the
     * one asked for, so that a skipped group inside a skipped group adds to one count
     * rather than starting a second.
     */
    private Instance outermostSkipped() {
        for (Instance instance : open) {
            if (instance.path == null) {
                return instance;
            }
        }
        return null;
    }

    /**
     * Returns the sentence a skipped group leaves behind: what happened, how many term
     * identifiers went with it, and — where the identifier is one of an extension this
     * build ships — that loading that registry is what carries them.
     */
    private static String skippedMessage(Instance instance) {
        StringBuilder message = new StringBuilder(instance.skippedReason);
        if (instance.enclosed > 0) {
            message.append(", together with the ").append(instance.enclosed)
                    .append(instance.enclosed == 1 ? " term identifier" : " term identifiers")
                    .append(" it encloses");
        }
        if (instance.skippedKind == ImportNote.Kind.UNKNOWN_TERM
                && Registry.xrechnungExtension().term(instance.id).isPresent()) {
            message.append("; the registry of the XRechnung extension defines it");
        }
        return message.toString();
    }

    /**
     * Returns the open group instance a term belongs under, together with the registry
     * chain that puts it there.
     *
     * <p>The search runs from the innermost open instance outwards. A syntax nests
     * elements the standard does not nest — the payee identifier inside the supplier party
     * is the case to remember — so the element an instance was opened for is no proof that
     * the term belongs to it, and the innermost instance whose chain the term's chain
     * continues is the right answer. A term that continues none of them, the empty chain
     * at the document included, has no place here.
     *
     * <p>An open instance that holds no path is one the reader could not place — a group
     * no loaded registry knows, or one a limit left out — and the search stops there
     * rather than looking past it. Everything the document writes inside such a group
     * belongs to that group; handing it to the group above would put the identifier of a
     * sub invoice line on the invoice line that encloses it, which is a value in the wrong
     * place rather than a value that was kept.
     */
    private Resolution resolve(String id) {
        for (int level = open.size(); level >= 0; level--) {
            Instance instance = level == 0 ? null : open.get(level - 1);
            if (instance != null && instance.path == null) {
                return null;
            }
            List<String> chain = instance == null ? List.of() : instance.chain;
            Optional<List<String>> continued = continuation(chain, id);
            if (continued.isPresent()) {
                return new Resolution(continued.get(), chain.size(),
                        instance == null ? SemanticPath.root() : instance.path);
            }
            Resolution sibling = inClosedSibling(instance, id);
            if (sibling != null) {
                return sibling;
            }
        }
        return null;
    }

    /**
     * Returns the instance of a group that was closed here and that a term continues, if
     * there is one.
     *
     * <p>A syntax may write a term of a group outside the element of that group: in CII
     * the account of a credit transfer is one element of the payment means and the bank
     * that holds it is the next one along, so the group instance is already closed by the
     * time BT-86 arrives, and the standard nevertheless puts the two in one BG-17. An
     * instance that was closed at this level is therefore still an answer, and it is only
     * asked for after the open instance at this level has failed to be one — so a term
     * that belongs where it is written is never diverted into a sibling.
     */
    private Resolution inClosedSibling(Instance instance, String id) {
        Map<String, Instance> closed = instance == null ? closedAtRoot : instance.closed;
        if (closed.isEmpty()) {
            return null;
        }
        for (Instance sibling : closed.values()) {
            Optional<List<String>> continued = continuation(sibling.chain, id);
            if (continued.isPresent()) {
                return new Resolution(continued.get(), sibling.chain.size(), sibling.path);
            }
        }
        return null;
    }

    /**
     * Returns the parent chain the registry records for a term that continues the chain
     * resolved so far, if there is one.
     *
     * <p>A group between the two is opened implicitly, which is sound only where the
     * document has nothing to tell its instances apart by. A group the standard allows
     * more than once must therefore either be bound in this syntax, in which case its
     * element opens it and an implicit one would be wrong, or be one the syntax does not
     * represent, in which case the element carrying the value is the only instance there
     * is.
     */
    private Optional<List<String>> continuation(List<String> resolved, String id) {
        for (List<String> chain : registry.chains(id)) {
            if (chain.size() <= resolved.size()
                    || !chain.subList(0, resolved.size()).equals(resolved)) {
                continue;
            }
            if (chain.subList(resolved.size(), chain.size() - 1).stream()
                    .allMatch(this::isImplicit)) {
                return Optional.of(chain);
            }
        }
        return Optional.empty();
    }

    /** Tells whether a group may be opened without an element of its own. */
    private boolean isImplicit(String id) {
        return !registry.isRepeatable(id) || !boundGroups.contains(id);
    }

    /**
     * Returns the path of the position a term sits in: the path of the enclosing group
     * instance followed by the groups the registry chain passes through on the way, and
     * collects the instances that were opened on the way so that their occurrence indices
     * can be spent once a value has been written below them.
     */
    private SemanticPath instance(Resolution resolution, List<Instance> ancestors) {
        List<PathSegment> segments = new ArrayList<>(resolution.instancePath().segments());
        SemanticPath parent = resolution.instancePath();
        List<String> chain = resolution.chain();
        for (int i = resolution.resolved(); i < chain.size() - 1; i++) {
            String intermediate = chain.get(i);
            Term term = registry.term(intermediate).orElseThrow();
            int index = term.isRepeatable() ? peek(parent, intermediate) : -1;
            segments.add(segment(term));
            if (index >= 0) {
                segments.add(PathSegment.index(index));
            }
            SemanticPath path = SemanticPath.ofSegments(segments);
            ancestors.add(new Instance(intermediate, chain.subList(0, i + 1), path, parent,
                    index));
            parent = path;
        }
        return SemanticPath.ofSegments(segments);
    }

    /**
     * Returns the chain the children of a group instance resolve against. A group the
     * registry records inside itself gives its nested occurrence the chain of the
     * enclosing one, because an inner occurrence carries the same children in the same
     * positions as the outer one.
     */
    private static List<String> resolved(List<String> chain) {
        int last = chain.size() - 1;
        if (last > 0 && chain.get(last).equals(chain.get(last - 1))) {
            return chain.subList(0, last);
        }
        return chain;
    }

    /** Returns the path of one occurrence: its position, the term, and the index it takes. */
    private static SemanticPath extend(SemanticPath instance, Term term, int index) {
        List<PathSegment> segments = new ArrayList<>(instance.segments());
        segments.add(segment(term));
        if (index >= 0) {
            segments.add(PathSegment.index(index));
        }
        return SemanticPath.ofSegments(segments);
    }

    /** Returns the segment of a term: its identifier, split into the parts of the grammar. */
    private static PathSegment.Term segment(Term term) {
        String id = term.id();
        String rest = id.substring(id.indexOf('-') + 1);
        int namespaceEnd = rest.indexOf('-');
        if (namespaceEnd < 0) {
            return PathSegment.core(term.kind(), rest);
        }
        return PathSegment.extension(term.kind(), rest.substring(0, namespaceEnd),
                rest.substring(namespaceEnd + 1));
    }

    private int peek(SemanticPath instancePath, String id) {
        Map<String, Integer> counters = occurrences.get(instancePath);
        return counters == null ? 0 : counters.getOrDefault(id, 0);
    }

    private void commit(SemanticPath instancePath, String id) {
        occurrences.computeIfAbsent(instancePath, key -> new HashMap<>())
                .merge(id, 1, Integer::sum);
    }

    /**
     * Reports whether a path stays inside the bounds the reader of the result applies, and
     * writes a note where it does not. A group is measured with one segment and one byte
     * in reserve, because the shortest path of a value below it is that much longer.
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
                        + limits.maxPathBytes() + " bytes this reader writes within, so the"
                        + " element and everything below it was skipped"));
        return false;
    }

    /**
     * Builds one value from the content of an element and the components the syntax
     * carried, or returns {@code null} where the content spells no value of the semantic
     * data type the registry gives the term.
     *
     * <p>A time is kept as the string the element carries. The binding tables bind the
     * 2017 edition of the semantic model, whose registry names no term of that type, so
     * no value of it reaches this method from any instance this reader reads.
     */
    private SemanticValue build(Term term,
                                String content,
                                Map<Component.Role, String> components,
                                boolean dateFormat102,
                                SemanticPath path) {
        SemanticType type = term.datatype().orElse(null);
        if (type == null) {
            return malformed(path, "the registry gives " + term.id() + " no semantic data"
                    + " type, so its content could not be typed");
        }
        try {
            return switch (type) {
                case TEXT -> content.isEmpty() ? empty(term, path) : SemanticValue.of(content);
                case BINARY_OBJECT -> binary(content, components, term, path);
                case IDENTIFIER -> stripped(content, term, path,
                        value -> identifier(value, components, term, path));
                case CODE, DOCUMENT_REFERENCE, TIME -> stripped(content, term, path,
                        SemanticValue::of);
                case DATE -> stripped(content, term, path,
                        value -> SemanticValue.ofDate(date(value, dateFormat102)));
                case AMOUNT, UNIT_PRICE_AMOUNT, QUANTITY, PERCENTAGE -> stripped(content,
                        term, path, value -> SemanticValue.ofDecimal(decimal(value)));
            };
        } catch (EsjFormatException e) {
            return malformed(path, "the content of " + term.id() + " is no value of the"
                    + " registry datatype " + type.registryDatatype() + ": " + e.getMessage());
        }
    }

    /**
     * Records that a content spells no value of its semantic data type, or ends the read
     * where the reader was asked to be strict about that.
     */
    private SemanticValue malformed(SemanticPath path, String message) {
        if (strict) {
            throw new BindingFormatException("at " + path + ", " + message);
        }
        notes.add(new ImportNote(ImportNote.Kind.MALFORMED, path.toString(), message));
        return null;
    }

    /**
     * Builds a value from the content with the whitespace around it removed, and records
     * an element that carries nothing. Only the text type keeps that whitespace: it is the
     * type whose values carry line breaks, and the specification, section 6.8 does not trim
     * them.
     */
    private SemanticValue stripped(String content,
                                   Term term,
                                   SemanticPath path,
                                   Function<String, SemanticValue> build) {
        String value = content.strip();
        return value.isEmpty() ? empty(term, path) : build.apply(value);
    }

    /**
     * Records that an element carried nothing and returns no value. An empty element is no
     * defect of the source document: a term that is there but says nothing addresses
     * nothing, and the specification, section 6.1 has no empty value to write for it.
     */
    private SemanticValue empty(Term term, SemanticPath path) {
        notes.add(new ImportNote(ImportNote.Kind.EMPTY, path.toString(),
                "the element of " + term.id() + " carries no content, so no value was written"));
        return null;
    }

    /**
     * Builds an identifier with the supplementary components the registry allows for its
     * term. A component the standard does not give that term is dropped rather than
     * carried into a document that could not hold it.
     *
     * <p>A component the registry declares mandatory and the source did not carry is a
     * different case: the identifier itself arrived, the semantic model has a place for it,
     * and a reader that dropped it would take the defect out of reach of the layer whose
     * business it is. So the value is kept without the component, the loss is a note, the
     * model layer reports {@code ESJ-L2-COMPONENT-MISSING} at that path, and the business
     * rule of the standard that asks for the scheme — {@code BR-62} to {@code BR-65} —
     * reports under the identifier the official artefacts use.
     */
    private SemanticValue identifier(String value,
                                     Map<Component.Role, String> components,
                                     Term term,
                                     SemanticPath path) {
        String scheme = component(components, Component.Role.SCHEME, term, path);
        String schemeVersion = component(components, Component.Role.SCHEME_VERSION, term, path);
        if (scheme == null && schemeVersion != null) {
            notes.add(new ImportNote(ImportNote.Kind.COMPONENT_DROPPED, path.toString(),
                    "a scheme version without a scheme is no value of the Identifier type, so"
                            + " it was dropped"));
            schemeVersion = null;
        }
        for (Component declared : term.components()) {
            boolean present = declared.role() == Component.Role.SCHEME
                    ? scheme != null : schemeVersion != null;
            if (declared.isMandatory() && !present) {
                notes.add(new ImportNote(ImportNote.Kind.COMPONENT_MISSING, path.toString(),
                        "the registry declares the " + declared.role().jsonMember()
                                + " component of " + term.id() + " as mandatory, and the"
                                + " element arrived without it; the identifier is kept, and"
                                + " the missing component is a finding of the validator"));
            }
        }
        return SemanticValue.identifier(value, scheme, schemeVersion);
    }

    /**
     * Returns one supplementary component, or {@code null} where the syntax carried none
     * or the registry does not list it for that term.
     */
    private String component(Map<Component.Role, String> components,
                             Component.Role role,
                             Term term,
                             SemanticPath path) {
        String raw = components.get(role);
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.isEmpty()) {
            notes.add(new ImportNote(ImportNote.Kind.COMPONENT_DROPPED,
                    ImportNote.Level.INFORMATION, path.toString(),
                    "the " + role.jsonMember() + " of " + term.id() + " is empty, so it was"
                            + " dropped"));
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
     * Builds a binary object. Both supplementary components are mandatory in the standard,
     * so an attachment that arrives without one of them is described in the report rather
     * than written as a value the format cannot hold.
     */
    private SemanticValue binary(String content,
                                 Map<Component.Role, String> components,
                                 Term term,
                                 SemanticPath path) {
        String mimeCode = components.get(Component.Role.MIME_CODE);
        String filename = components.get(Component.Role.FILENAME);
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
            return malformed(path, "the content of " + term.id() + " is no base64 encoding"
                    + " of a file");
        }
        return SemanticValue.binary(bytes, mimeCode.strip(), filename.strip());
    }

    /**
     * Reads a calendar date, in the eight-digit form CII writes where the format qualifier
     * is 102 and in the complete calendar date representation everywhere else.
     */
    private static LocalDate date(String value, boolean format102) {
        if (format102) {
            if (!DATE_102.matcher(value).matches()) {
                throw new EsjFormatException("a date whose format qualifier is 102 is written"
                        + " as eight digits");
            }
            try {
                return LocalDate.of(Integer.parseInt(value.substring(0, 4)),
                        Integer.parseInt(value.substring(4, 6)),
                        Integer.parseInt(value.substring(6, 8)));
            } catch (DateTimeException e) {
                throw new EsjFormatException("the eight digits name no day of the calendar", e);
            }
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new EsjFormatException("a date is written as YYYY-MM-DD", e);
        }
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
     * Records a text value with the code that stands in front of it split off into the
     * term the binding table says carries it.
     *
     * <p>Four cases leave the text alone: a note that does not begin with the prefix, a
     * note that is nothing but the prefix, a group instance that already carries the code
     * from an element of its own, and a pair that would not fit the limits this reader
     * writes within. In each of them the note is kept whole rather than stripped of a code
     * that then goes missing.
     */
    private boolean withSubjectCode(SemanticPath path,
                                    SemanticValue text,
                                    Term term,
                                    String codeTermId) {
        Matcher prefix = SUBJECT_CODE.matcher(text.canonicalContent());
        Optional<Term> codeTerm = registry.term(codeTermId);
        if (!prefix.matches() || prefix.group(2).isEmpty() || codeTerm.isEmpty()) {
            return record(path, text, term.id());
        }
        SemanticPath codePath = extend(path.parent(), codeTerm.get(), -1);
        if (values.containsKey(codePath)) {
            return record(path, text, term.id());
        }
        SemanticValue code = SemanticValue.of(prefix.group(1));
        SemanticValue rest = SemanticValue.of(prefix.group(2));
        if (!admits(codePath, code, codeTermId) || !admits(path, rest, term.id())
                || !reserve(2, memberBytes(codePath, code) + memberBytes(path, rest), 0, path)) {
            return record(path, text, term.id());
        }
        put(codePath, code);
        put(path, rest);
        return true;
    }

    /** Records one value, unless it or the document it would grow falls outside the limits. */
    private boolean record(SemanticPath path, SemanticValue value, String id) {
        if (values.containsKey(path)) {
            notes.add(new ImportNote(ImportNote.Kind.DUPLICATE_PATH, path.toString(),
                    "a second element carrying " + id + " resolved to a path that is already"
                            + " taken, so it was skipped"));
            return false;
        }
        if (!admits(path, value, id)) {
            return false;
        }
        if (!reserve(1, memberBytes(path, value), decodedBytes(value), path)) {
            return false;
        }
        put(path, value);
        return true;
    }

    /** Stores one value and tells every open instance it lies under that it produced one. */
    private void put(SemanticPath path, SemanticValue value) {
        values.put(path, value);
        for (Instance instance : open) {
            if (instance.path != null && path.startsWith(instance.path)) {
                instance.produced = true;
            }
        }
    }

    /** Reports whether one value stays inside the bounds a reader applies to a single value. */
    private boolean admits(SemanticPath path, SemanticValue value, String id) {
        boolean binary = value.mimeCode() != null || value.filename() != null;
        long bound = binary ? limits.maxBinaryValueBytes() : limits.maxStringBytes();
        long content = utf8Length(value.canonicalContent());
        if (content > bound) {
            return tooLarge(path, "the " + (binary ? "binary" : "string") + " value of " + id
                    + " is " + content + " bytes long, more than the " + bound + " this reader"
                    + " writes within, so it was left out");
        }
        for (String component : new String[] {value.scheme(), value.schemeVersion(),
                value.mimeCode(), value.filename()}) {
            if (component != null && utf8Length(component) > limits.maxStringBytes()) {
                return tooLarge(path, "a supplementary component of " + id + " is "
                        + utf8Length(component) + " bytes long, more than the "
                        + limits.maxStringBytes() + " this reader writes within, so the value"
                        + " was left out");
            }
        }
        return true;
    }

    /**
     * Takes room for one or two values out of what is left of the document, and reports
     * whether there was room. A bound on the whole document ends the read where it is
     * reached; the bound on the binary content does not, because a further attachment may
     * still be small enough to fit.
     */
    private boolean reserve(int count, long bytes, long binary, SemanticPath where) {
        if (values.size() + count > limits.maxValues()) {
            full = true;
            return tooLarge(where, "the document reached the " + limits.maxValues() + " values"
                    + " this reader writes within, so this value and the rest of the source"
                    + " document were left out");
        }
        long separators = values.isEmpty() ? count - 1L : count;
        if (documentBytes + bytes + separators > limits.maxDocumentBytes()) {
            full = true;
            return tooLarge(where, "the canonical form of the document reached the "
                    + limits.maxDocumentBytes() + " bytes this reader writes within, so this"
                    + " value and the rest of the source document were left out");
        }
        if (binaryBytes + binary > limits.maxTotalBinaryBytes()) {
            return tooLarge(where, "the binary content of the document would exceed the "
                    + limits.maxTotalBinaryBytes() + " bytes this reader writes within, so"
                    + " this value was left out");
        }
        documentBytes += bytes + separators;
        binaryBytes += binary;
        return true;
    }

    private boolean tooLarge(SemanticPath path, String message) {
        notes.add(new ImportNote(ImportNote.Kind.LIMIT_REACHED, path.toString(), message));
        return false;
    }

    /**
     * Returns the decoded binary content a value adds to the document, read off the length
     * of the canonical base64 it carries.
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
     * Returns the bytes one member of {@code values} takes in the canonical form: the path
     * as a JSON string, the colon, and the value. The separating comma belongs to the
     * member before it and is added by {@link #reserve}.
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

    /** Returns the bytes a string takes as a JSON string in the canonical form. */
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

    private static long utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** Returns the instances closed at the level that is innermost now. */
    private Map<String, Instance> closedAt() {
        return open.isEmpty() ? closedAtRoot : open.get(open.size() - 1).closed;
    }

    /** Returns the chain of open group instances, for the location of a note. */
    private String location(String id) {
        StringBuilder location = new StringBuilder();
        for (Instance instance : open) {
            location.append(instance.id).append('/');
        }
        return location.append(id).toString();
    }

    /** Where a term belongs: the chain that puts it there and the instance it continues. */
    private record Resolution(List<String> chain, int resolved, SemanticPath instancePath) { }

    /** One open group instance. */
    private static final class Instance {

        private final String id;
        private final List<String> chain;
        private final SemanticPath path;
        private final SemanticPath parent;
        private final int index;
        private final Map<String, Instance> closed = new LinkedHashMap<>();
        private boolean produced;
        private int pushed = 1;

        /** The note this instance stands for, written when it closes, or {@code null}. */
        private ImportNote.Kind skippedKind;

        /** The sentence that note opens with, without the count of what was enclosed. */
        private String skippedReason;

        /** How many term identifiers were matched inside this instance. */
        private int enclosed;

        private Instance(String id,
                         List<String> chain,
                         SemanticPath path,
                         SemanticPath parent,
                         int index) {
            this.id = id;
            this.chain = chain;
            this.path = path;
            this.parent = parent;
            this.index = index;
        }

        /**
         * Returns a marker for a group the reader could not place, which holds no path.
         *
         * @param id     the group identifier
         * @param kind   the note to write when the instance closes, or {@code null} where
         *               the reason was already reported or is one the caller states itself
         * @param reason the sentence that note opens with
         */
        private static Instance skipped(String id, ImportNote.Kind kind, String reason) {
            Instance instance = new Instance(id, List.of(), null, null, -1);
            instance.skippedKind = kind;
            instance.skippedReason = reason;
            return instance;
        }

        /** Returns a marker that carries no note of its own. */
        private static Instance skipped(String id) {
            return skipped(id, null, null);
        }
    }
}
