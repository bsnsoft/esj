package de.bsnsoft.esj.bindings;

import de.bsnsoft.esj.EsjException;
import de.bsnsoft.esj.PathSegment;
import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.model.Component;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Writes a semantic document as a document of one XML syntax, from the binding table of
 * that syntax and the element order of its schema.
 *
 * <p>The writer is the binding table read the other way round. For the reader an XPath of
 * {@code model/bindings} says which business term an element carries; for the writer it
 * says which element a business term is written as, which attributes its supplementary
 * components become, and which conditions the element has to meet — a type code, a charge
 * indicator, a format qualifier — for the syntax to mean by it what the standard means by
 * the term. Nothing about the syntax is stated twice: the paths come from the table, and
 * the order two sibling elements stand in comes from the schema modules of the validation
 * pack through {@link SchemaTable}.
 *
 * <p>One engine serves both syntaxes. {@link CiiWriter} and {@link UblWriter} are the two
 * doors into it, and what differs between them is what the two tables and the two schemas
 * say, not what the writer does — with the three exceptions this class names where it
 * makes them: the element the payment account identifier of a cross industry invoice is
 * written at, the currency a UBL amount carries, and the structural elements a UBL
 * aggregate requires.
 *
 * <p>Three things the tables alone cannot settle, and the writer settles them from the
 * schema rather than from an opinion:
 *
 * <ul>
 *   <li><strong>Which spelling.</strong> A term bound to more than one XPath is written at
 *       the first of them the schema of this release admits and that has a place for every
 *       supplementary component the value carries. That rule, and not a list of exceptions,
 *       is what puts the payment service provider identifier in the element the schema has
 *       rather than in the one the source model names, and what keeps the unit code of a
 *       price base quantity on the attribute the schema spells.</li>
 *   <li><strong>Which occurrence.</strong> The occurrence index of a business group is
 *       written on the group's own element where the schema lets that element repeat, and
 *       otherwise on the innermost element above it that does. Where the index moves up —
 *       a credit transfer is one account per payment means, and the schema admits one
 *       account — the terms of the group above it are written into every instance of that
 *       element, because the schema asks each instance for them.</li>
 *   <li><strong>What must be there.</strong> The sections a document cannot leave out are
 *       written whether or not a value falls into them, and which those are is read off
 *       the schema: an element the schema requires whose type asks for nothing in turn is
 *       structure, and structure is the writer's to supply.</li>
 * </ul>
 *
 * <p>Whatever the syntax has no place for is named in a {@link WriteReport} rather than
 * dropped quietly. The terms of an extension the syntax binding does not cover, a business
 * group the schema admits fewer of than the document carries, the {@code extensions}
 * member, which holds data that has no business term at all, and an element the schema
 * requires and no business term names are the cases this release meets. A character the
 * semantic model admits and XML 1.0 does not is the same case one level down: it is left
 * out and named, and {@link XmlCharacters} says why that rather than a refusal.
 *
 * <p>The output is UTF-8, deterministic — two runs over the same document produce the same
 * bytes — and, unless {@link WriterOptions#indent()} says otherwise, one element to a line.
 *
 * <h2>Cost</h2>
 *
 * <p>Writing builds the element tree of the whole document before it serializes it, so its
 * memory cost grows with the document rather than with one invoice line. That is the
 * opposite of {@link StreamingReader} and it is deliberate for now: the order the syntax
 * writes elements in is not the order the semantic model holds them in, so nothing can be
 * written until everything that goes before it has arrived. A result of this shape is a
 * {@code byte[]} in any case.
 */
final class SyntaxWriter {



    /** The eight-digit form CII writes a date in where the format qualifier is 102. */
    private static final DateTimeFormatter FORMAT_102 = DateTimeFormatter.ofPattern("uuuuMMdd");

    /**
     * The form of an international bank account number: two letters for the country, two
     * check digits and up to thirty more characters, as ISO 13616 registers it. It is what
     * tells the two elements the cross industry invoice offers for BT-84 apart; no rule of
     * this project validates an IBAN, and this decides where a value is written and
     * nothing else.
     */
    private static final Pattern IBAN = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");

    /** The attribute a UBL amount carries the currency of the document in. */
    private static final String CURRENCY_ID = "currencyID";

    /** The business term the currency of a UBL amount is taken from. */
    private static final SemanticPath DOCUMENT_CURRENCY = SemanticPath.of("/BT-5");

    private SyntaxWriter() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns an XPath of a binding table with its conditions removed, which is the path
     * of the element or attribute itself.
     *
     * @param xpath the path
     * @return the path without its predicates
     */
    private static String withoutPredicates(String xpath) {
        if (xpath.indexOf('[') < 0) {
            return xpath;
        }
        StringBuilder plain = new StringBuilder(xpath.length());
        int depth = 0;
        for (int i = 0; i < xpath.length(); i++) {
            char character = xpath.charAt(i);
            if (character == '[') {
                depth++;
            } else if (character == ']') {
                depth--;
            } else if (depth == 0) {
                plain.append(character);
            }
        }
        return plain.toString();
    }

    /**
     * Writes a document in one syntax and reports what did not reach it.
     *
     * @param syntax   the syntax and the document type to write
     * @param document the semantic document
     * @param options  what this run may do and how it shapes its output
     * @return the document and the report
     * @throws BindingEditionException if the document names an edition of the semantic
     *                                 model other than the one the binding table of that
     *                                 syntax was written against
     */
    static WriteResult write(BindingSyntax syntax,
                             SemanticDocument document,
                             WriterOptions options) {
        return new Run(syntax, document, options).write();
    }

    /** One conversion, from the values of a document to the bytes of an XML syntax. */
    private static final class Run {

        private final BindingSyntax syntax;
        private final SemanticDocument document;
        private final WriterOptions options;
        private final BindingTable table;
        private final SchemaTable schema;
        private final List<WriteNote> notes = new ArrayList<>();
        private final List<Reference> references = new ArrayList<>();

        /** The elements a code is written in front of the content of, in the order met. */
        private final List<Prefixed> prefixed = new ArrayList<>();

        /** Where the occurrence index of each repeating business group of this document is
         * written, by the identifier of that group. */
        private final Map<String, Slot> slots = new HashMap<>();

        /**
         * The same slots by the place they are at, so that a term whose path passes through
         * one finds it without knowing which group made it.
         */
        private final Map<String, Slot> slotsByKey = new HashMap<>();

        /**
         * The same slots by the place alone, for a business term whose own path lies in no
         * business group at that place.
         *
         * <p>A syntax may write such a term inside an element a business group owns: the
         * payment due date of a UBL credit note is written inside the payment means, and
         * BT-9 stands outside BG-16 in the semantic model. The term belongs in the element
         * the group has, not in one of its own, and this is how it finds it. The first slot
         * registered at a place wins, and the order the slots are registered in is the
         * canonical path order of the document, so two runs agree.
         */
        private final Map<String, Slot> slotsByPlace = new LinkedHashMap<>();

        /** How many instances each slot has, so that the terms above it reach every one. */
        private final Map<String, Integer> slotCounts = new HashMap<>();

        /** The business groups the document states, worked out when first asked for. */
        private Set<String> stated;

        /** The term the table writes at each path, worked out when first asked for. */
        private Map<String, String> termsByPath;

        /** The business groups of this table by the element path they are written at. */
        private Map<String, List<String>> groupElements;

        private XmlElement root;
        private Indicator allowanceIndicator;
        private boolean allowanceKnown;
        private int created;
        private int written;
        private int dropped;

        private Run(BindingSyntax syntax, SemanticDocument document, WriterOptions options) {
            this.syntax = syntax;
            this.document = document;
            this.options = options;
            this.table = BindingTable.of(syntax);
            this.schema = SchemaTable.of(syntax);
        }

        private WriteResult write() {
            if (!table.describes(document.semanticModel())) {
                throw new BindingEditionException("the document names the edition "
                        + document.semanticModel() + " and the " + syntax.provenance()
                        + " binding table binds " + table.semanticModel() + "; a business"
                        + " term of the document has no address in a table of another"
                        + " edition, and this writer drops none to make one fit");
            }
            plan();
            root = new XmlElement(schema.rootElement(), schema.rootType(), 0, created++);
            skeleton(root);
            for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
                place(entry.getKey(), entry.getValue());
            }
            indicate(root);
            fold();
            if (syntax != BindingSyntax.CII) {
                structure(root);
                currency(root);
            }
            for (Reference reference : references) {
                reference.resolve(root);
            }
            convention(root, "/" + root.name());
            if (syntax != BindingSyntax.CII) {
                // After the references, because an attribute a reference fills is an
                // attribute the document states, and one it cannot fill is one it does not.
                unstated(root, "/" + root.name());
            }
            if (!document.extensions().isEmpty()) {
                notes.add(new WriteNote(WriteNote.Kind.EXTENSIONS_DROPPED, "",
                        "the document carries " + document.extensions().size()
                                + " extension object(s) under extensions, which hold data"
                                + " that no business term names and that therefore has no"
                                + " place in a syntax binding"));
            }
            return new WriteResult(serialize(),
                    new WriteReport(syntax, written, dropped, notes));
        }

        /**
         * Works out, before anything is written, where the occurrence index of every
         * repeating business group of this document is written.
         *
         * <p>It has to happen first because of the one case where the index does not land
         * on the group's own element: the terms of the group above it are then written into
         * every instance of the element that carries the index, and a writer that placed
         * them as it met them would not yet know how many instances there are.
         */
        private void plan() {
            for (SemanticPath path : document.values().keySet()) {
                for (SemanticPath group : path.groupPaths()) {
                    plan(group);
                }
            }
        }

        private void plan(SemanticPath group) {
            Chain chain = chain(group);
            if (chain == null || group.index().isEmpty()) {
                return;
            }
            BindingTable.Entry entry = table.entry(group.term());
            if (entry == null || !entry.bound()) {
                return;
            }
            List<BindingTable.Step> steps =
                    expand(entry.paths().get(0).steps(), chain.depth());
            Placement placement = new Placement(steps);
            if (!placement.anchor(chain, this)) {
                return;
            }
            Anchor anchor = placement.anchorOf(group.toString());
            if (anchor == null) {
                return;
            }
            // Where the index lands on an element the group shares with the group above it,
            // the terms of that group above belong to every instance of the element, because
            // the schema asks each of them for what it asks the one for.
            boolean shared = anchor.position() != steps.size() - 1;
            Slot slot = new Slot(anchor.position(), steps.get(anchor.position()).localName(),
                    anchor.owner(), shared);
            slots.put(group.term(), slot);
            Slot known = slotsByKey.get(slot.key());
            if (known == null || (shared && !known.shared())) {
                slotsByKey.put(slot.key(), slot);
            }
            slotCounts.merge(slot.key(), group.index().get().value() + 1, Math::max);
            slotsByPlace.putIfAbsent(slot.position() + "|" + slot.element(), slot);
        }

        /**
         * Writes into every allowance or charge element that does not say which of the two
         * it is that it is an allowance.
         *
         * <p>Both syntaxes have one element type for an allowance and a charge and tell
         * them apart with an indicator, and the binding table states the indicator
         * wherever the standard binds a business group to that type. A table may bind a
         * business term to an element of that type without a group: the item price
         * discount of a cross industry invoice is bound to an applied allowance or charge
         * of the gross price and states no indicator. A discount is an allowance, and a
         * syntax has no way of reading an element that does not say so, so the writer says
         * it. Which element carries the indicator, what an allowance writes in it and
         * which schema type this is are all read off the binding table and the schema; only
         * the choice of the allowance form is the writer's.
         */
        private void indicate(XmlElement element) {
            Indicator indicator = allowance();
            if (indicator != null && indicator.type().equals(element.type())
                    && element.occurrence(prefixed(indicator.first().namespace(),
                            indicator.first().localName()), -1).isEmpty()) {
                satisfy(element, indicator.predicate());
            }
            for (XmlElement child : element.children()) {
                indicate(child);
            }
        }

        /**
         * Returns the condition of the binding table that says an element of the allowance
         * or charge type is an allowance, with the schema type it stands on, or
         * {@code null} where the table says no such thing.
         */
        private Indicator allowance() {
            if (allowanceKnown) {
                return allowanceIndicator;
            }
            allowanceKnown = true;
            for (BindingTable.Entry entry : table.entries()) {
                if (!entry.isGroup() || !entry.bound()) {
                    continue;
                }
                List<BindingTable.Step> steps = entry.paths().get(0).steps();
                BindingTable.Step last = steps.get(steps.size() - 1);
                for (Predicate predicate : last.predicates()) {
                    if (predicate.kind() != Predicate.Kind.CHILD_EQUALS
                            || !"false".equals(predicate.literal())
                            || predicate.path().isEmpty()) {
                        continue;
                    }
                    SchemaTable.Child declared = childOf(typeAbove(steps), last);
                    if (declared != null && declared.type() != null) {
                        allowanceIndicator = new Indicator(declared.type(), predicate);
                        return allowanceIndicator;
                    }
                }
            }
            return null;
        }

        /**
         * Writes the code a syntax puts in front of the content of another term's element
         * into that content.
         *
         * <p>UBL has one element for the note and no element for the subject code of the
         * note, and the binding writes the code as {@code #AAI#} in front of the text. The
         * reader splits it out again, and it splits nothing out of an element that is the
         * prefix and nothing else, so a code without the note it belongs in front of has
         * nowhere to go: it is left out and named rather than written into an element that
         * would come back as a note whose text is the code.
         */
        private void fold() {
            for (Prefixed entry : prefixed) {
                XmlElement element = entry.element();
                if (element.text() == null || element.text().isEmpty()) {
                    element.subjectCode(null);
                    if (element.bare()) {
                        element.detach();
                    }
                    notes.add(new WriteNote(WriteNote.Kind.VALUE_NEEDS_COMPANION,
                            entry.path().toString(), "this syntax writes the value in front"
                            + " of the content of another term at the same element, and the"
                            + " document carries no such content, so it was not written"));
                    written--;
                    dropped++;
                    continue;
                }
                element.text("#" + element.subjectCode() + "#" + element.text());
                element.subjectCode(null);
            }
        }

        /**
         * Writes the elements an aggregate of this syntax requires and that hold structure
         * rather than content, wherever one is missing.
         *
         * <p>{@link #skeleton} does this for the document element before anything is
         * written, which is all a cross industry invoice asks for. UBL asks for it
         * everywhere: a party tax scheme requires a tax scheme, a tax category requires
         * one too, and an invoice line requires an item. Each of those types asks for
         * nothing in turn, so the element is structure and the writer supplies it; a type
         * that requires content is left alone and the schema is left to say so.
         */
        private void structure(XmlElement element) {
            skeleton(element);
            for (XmlElement child : List.copyOf(element.children())) {
                structure(child);
            }
        }

        /**
         * Writes the currency of the document onto every amount that does not carry one.
         *
         * <p>UBL declares the currency of an amount a required attribute of the amount and
         * the semantic model states it once, as BT-5. Two amounts say otherwise and the
         * table says so itself, by comparing the attribute with an element of the document:
         * the value added tax total in the accounting currency is stated in BT-6, and
         * {@link Reference} resolves both. What is left is every other amount, and the
         * currency of every other amount is the currency of the invoice.
         */
        private void currency(XmlElement element) {
            SchemaTable.Type type = schema.type(element.type());
            if (type != null && type.hasAttribute(CURRENCY_ID) && !element.isEmpty()
                    && element.attribute(CURRENCY_ID) == null
                    && element.referenceTarget(CURRENCY_ID) == null) {
                document.value(DOCUMENT_CURRENCY).ifPresent(
                        value -> element.attribute(CURRENCY_ID, value.canonicalContent()));
            }
            for (XmlElement child : element.children()) {
                currency(child);
            }
        }

        /**
         * Names every element of the document that the schema asks for, that no convention
         * of the binding table fills, and that the semantic model does not fill either.
         *
         * <p>Two shapes, and the binding table tells them apart. The element may be one a
         * business term of the semantic model <em>does</em> carry and this document does
         * not state — the total value added tax amount BT-110 is the case of the
         * conformance corpus — which is {@link WriteNote.Kind#TERM_NOT_STATED}. Or no
         * business term names it at all, which is
         * {@link WriteNote.Kind#ELEMENT_NOT_STATED}. An element the writer supplied for
         * structure may stand <em>empty</em> for either reason and is named the same way.
         *
         * <p>An attribute the schema requires is the same thing one level down. UBL asks
         * every amount for the currency it is stated in and the semantic model states the
         * currency once, as BT-5, so a document that leaves BT-5 out leaves every amount of
         * the syntax without an attribute the schema asks for, and no term of the table
         * names that attribute.
         *
         * <p>Inventing a value would put into the document something nobody stated, so the
         * writer names the element instead and
         * {@code conformance/writers/ubl-roundtrip.md} names the documents it happens on.
         */
        private void unstated(XmlElement element, String path) {
            SchemaTable.Type type = schema.type(element.type());
            if (type != null && !element.isEmpty()) {
                for (SchemaTable.Child child : type.children()) {
                    if (child.required() && !element.hasChildNamed(child.name())) {
                        missing(path + "/" + child.name(), "the schema of this syntax"
                                + " requires " + child.name() + " in " + path,
                                "so it was left out");
                    }
                }
                for (String attribute : type.requiredAttributes()) {
                    if (element.attribute(attribute) == null) {
                        missing(path + "/@" + attribute, "the schema of this syntax"
                                + " requires the attribute " + attribute + " of " + path,
                                "so it was left out");
                    }
                }
            }
            if (element.isEmpty() && element != root) {
                missing(path, "the schema of this syntax asks for " + path,
                        "so it was written empty");
            }
            for (XmlElement child : element.children()) {
                unstated(child, path + "/" + child.name());
            }
        }

        /**
         * Names one element or attribute the syntax asks for and the document does not
         * fill, as the shape the binding table says it is.
         *
         * @param bound    the path the binding table would give the element or attribute
         * @param what     what the syntax asks for, as the note opens
         * @param outcome  what the writer did instead, as the note ends
         */
        private void missing(String bound, String what, String outcome) {
            String term = termAt(bound);
            if (term == null) {
                notes.add(new WriteNote(WriteNote.Kind.ELEMENT_NOT_STATED, "", what
                        + " and no business term of the semantic model names it, "
                        + outcome));
                return;
            }
            notes.add(new WriteNote(WriteNote.Kind.TERM_NOT_STATED, "", what
                    + " and the document does not state " + term + ", which carries it, "
                    + outcome));
        }

        /**
         * Returns the business term the binding table writes at one path, or {@code null}
         * where it writes none there.
         */
        private String termAt(String path) {
            if (termsByPath == null) {
                Map<String, String> byPath = new HashMap<>();
                for (BindingTable.Entry entry : table.entries()) {
                    for (BindingTable.Path bound : entry.paths()) {
                        byPath.putIfAbsent(withoutPredicates(bound.xpath()), entry.id());
                    }
                }
                termsByPath = byPath;
            }
            return termsByPath.get(path);
        }

        /**
         * Writes the values this syntax is written with where it requires an element that
         * no business term of the document states.
         *
         * <p>Every one of them is a row of {@link BindingTable#conventions()} — element,
         * condition, value, what asks for the element and where the value comes from — and
         * every one that is applied is a note of the report. The value carries no business
         * statement, so the document is complete with it: {@code esj validate} runs the
         * official artefacts over a document that needed only conventions instead of
         * standing down.
         *
         * <p>The same walk meets the other side of a convention: a document whose own value
         * at that element is character for character the conventional one. The written
         * document says it, and a reader that knows the convention does not read it back,
         * so the writer says so.
         */
        private void convention(XmlElement element, String path) {
            for (BindingTable.Convention convention : table.conventions()) {
                if (!convention.parent().equals(path)) {
                    continue;
                }
                String value = options.conventionValue(convention.option(),
                        convention.value());
                XmlElement stated = childNamed(element, convention.name());
                if (applies(convention, stated)) {
                    writeConvention(element, convention, value, path);
                } else if (stated != null && convention.notReadBeside() != null
                        && value.equals(stated.text())
                        && childNamed(element, convention.notReadBeside()) != null) {
                    shadowed(convention, value);
                }
            }
            for (XmlElement child : List.copyOf(element.children())) {
                convention(child, path + "/" + child.name());
            }
        }

        /**
         * Tells whether the condition of a convention holds where the walk stands.
         *
         * <p>The walk has already matched {@link BindingTable.Convention#parent()}, so
         * the element above is written; what is left is what the condition says about the
         * element itself. The switch is over the closed vocabulary of
         * {@link BindingTable.Condition} and carries no default, so a condition added to
         * that vocabulary stops this class from compiling instead of being applied with
         * the meaning of the one condition of this release.
         *
         * @param convention the convention
         * @param stated     the element the document already carries there, or
         *                   {@code null} where it carries none
         * @return whether the value of the convention is written here
         */
        private static boolean applies(BindingTable.Convention convention,
                                       XmlElement stated) {
            return switch (convention.condition()) {
                case PARENT_WRITTEN_ELEMENT_ABSENT -> stated == null;
            };
        }

        /** Writes the value of one convention into the element that stands without it. */
        private void writeConvention(XmlElement element,
                                     BindingTable.Convention convention,
                                     String value,
                                     String path) {
            SchemaTable.Type type = schema.type(element.type());
            SchemaTable.Child declared =
                    type == null ? null : type.child(convention.name());
            if (declared == null) {
                throw new BindingFormatException("a convention of the binding table of "
                        + syntax + " writes " + convention.element() + ", and the schema of"
                        + " this syntax declares no such element");
            }
            XmlElement written = element.add(-1, new XmlElement(declared.name(),
                    declared.type(), declared.order(), created++));
            written.text(value);
            notes.add(new WriteNote(WriteNote.Kind.CONVENTION_APPLIED, "", path + "/"
                    + convention.name() + " is required by " + convention.requiredBy()
                    + " and no business term of this document states it, so it was written"
                    + " as " + value + ". " + convention.source()));
        }

        /** Says that a value of the document reads back as the convention at its element. */
        private void shadowed(BindingTable.Convention convention, String value) {
            for (SemanticPath path : document.values().keySet()) {
                if (!convention.term().equals(path.term())) {
                    continue;
                }
                notes.add(new WriteNote(WriteNote.Kind.VALUE_READS_AS_CONVENTION,
                        path.toString(), convention.term() + " is the value " + value
                        + ", which is what this syntax is written with at "
                        + convention.element() + " where a document states no "
                        + convention.term() + ", so a reader that knows the convention does"
                        + " not read it back as " + convention.term()));
            }
        }

        /** Returns the first child of one name, or {@code null} where there is none. */
        private static XmlElement childNamed(XmlElement element, String name) {
            for (XmlElement child : element.children()) {
                if (child.name().equals(name)) {
                    return child;
                }
            }
            return null;
        }

        /** Returns the schema type of the element one step above the end of a path. */
        private String typeAbove(List<BindingTable.Step> steps) {
            String type = schema.rootType();
            for (int i = 1; i < steps.size() - 1; i++) {
                SchemaTable.Child child = childOf(type, steps.get(i));
                if (child == null) {
                    return null;
                }
                type = child.type();
            }
            return type;
        }

        /** Writes the elements the schema requires and that hold structure rather than content. */
        private void skeleton(XmlElement element) {
            SchemaTable.Type type = schema.type(element.type());
            if (type == null) {
                return;
            }
            for (SchemaTable.Child child : type.children()) {
                if (child.required() && child.type() != null
                        && schema.isSkeletal(child.type())
                        && !element.hasChildNamed(child.name())) {
                    skeleton(element.add(-1, new XmlElement(child.name(), child.type(),
                            child.order(), created++)));
                }
            }
        }

        /** Writes one value of the document, or records why it was not written. */
        private void place(SemanticPath path, SemanticValue value) {
            String termId = path.term();
            BindingTable.Entry entry = table.entry(termId);
            if (entry == null) {
                drop(WriteNote.Kind.TERM_UNKNOWN, path, "the binding table of this syntax"
                        + " carries no entry for " + termId);
                return;
            }
            if (!entry.bound()) {
                drop(WriteNote.Kind.TERM_NOT_BOUND, path, "the binding table gives " + termId
                        + " no place in this syntax" + because(entry.flags()));
                return;
            }
            Chain chain = chain(path);
            if (chain == null) {
                drop(WriteNote.Kind.TERM_NOT_BOUND, path, "a business group above " + termId
                        + " has no place in this syntax, so neither has the term");
                return;
            }
            BindingTable.Path bound = choose(entry, value, path, chain);
            if (bound == null) {
                drop(WriteNote.Kind.TERM_NOT_BOUND, path, "no XPath the binding table gives "
                        + termId + " names a place in this syntax that can be written");
                return;
            }
            Placement placement = new Placement(expand(bound.steps(), chain.depth()),
                    bound.attribute() == null);
            if (!placement.anchor(chain, this)) {
                drop(WriteNote.Kind.GROUP_NOT_REPEATABLE, path, placement.refusal);
                return;
            }
            String nowhere = placement.outsideItsGroup(this);
            if (nowhere != null) {
                drop(WriteNote.Kind.VALUE_NEEDS_GROUP, path, nowhere);
                return;
            }
            List<XmlElement> targets = placement.walk(root, this);
            if (targets.isEmpty()) {
                drop(WriteNote.Kind.TERM_NOT_BOUND, path, "the schema of this syntax has no"
                        + " element at " + bound.xpath());
                return;
            }
            boolean any = false;
            for (XmlElement target : targets) {
                any |= content(target, bound, value, path);
            }
            if (any) {
                written++;
            } else {
                dropped++;
            }
        }

        /**
         * Returns the XPath the term is written at, or {@code null} where the schema has
         * none of the ones the table gives it.
         *
         * <p>A candidate has to pass two tests and then wins a third. It has to be one the
         * schema of this release has every element and attribute of, which is what puts the
         * payment service provider identifier in the element the schema has rather than in
         * the one the source model names. It has to have somewhere to put every
         * supplementary component the value carries, which is what sends a party identifier
         * with an identification scheme to the element that takes one. Of those that do,
         * the one that reaches deepest into what the document already holds is taken, which
         * is what writes the base quantity of a line price inside the price that line
         * states.
         *
         * <p>Where no candidate has room for every component, the first the schema admits
         * is taken and every component that then has nowhere to go is named in the report.
         */
        private BindingTable.Path choose(BindingTable.Entry entry,
                                         SemanticValue value,
                                         SemanticPath path,
                                         Chain chain) {
            BindingTable.Path admitted = null;
            BindingTable.Path anyAdmitted = null;
            BindingTable.Path chosen = null;
            int reached = -1;
            for (BindingTable.Path candidate : entry.paths()) {
                if (!applies(candidate, chain.depth()) || !admits(candidate, chain.depth())) {
                    continue;
                }
                if (anyAdmitted == null) {
                    anyAdmitted = candidate;
                }
                if (!suits(candidate, value)) {
                    continue;
                }
                if (admitted == null) {
                    admitted = candidate;
                }
                if (!carries(candidate, value)) {
                    continue;
                }
                Placement placement = new Placement(
                        expand(candidate.steps(), chain.depth()),
                        candidate.attribute() == null);
                if (!placement.anchor(chain, this)) {
                    continue;
                }
                int standing = placement.standing(root, this);
                if (standing > reached) {
                    reached = standing;
                    chosen = candidate;
                }
            }
            if (chosen != null) {
                return chosen;
            }
            if (admitted == null) {
                admitted = anyAdmitted;
            }
            if (admitted != null) {
                for (Component.Role role : Component.Role.values()) {
                    if (componentOf(value, role) != null && !binds(admitted, role)) {
                        notes.add(new WriteNote(WriteNote.Kind.COMPONENT_DROPPED,
                                path.toString(), "the syntax has no place for the "
                                + role.jsonMember() + " of " + entry.id() + " at "
                                + admitted.xpath() + ", so the value was written without it"));
                    }
                }
            }
            return admitted;
        }

        /**
         * Tells whether a value has the form the element of a candidate is written for.
         *
         * <p>Where the table binds a term to more than one XPath, the writer normally
         * picks by what the value carries and by what already stands. One term defeats
         * that, and it is the only one in the three tables that does: the semantic model
         * has a single payment account identifier, BT-84, and the syntax has two elements
         * for it — {@code ram:IBANID} and {@code ram:ProprietaryID} — that carry no
         * supplementary component and stand side by side, so nothing in the semantic
         * document says which. Without a rule the first of the two wins and every account
         * identifier is written as an IBAN, which asserts of the document something it
         * never said. The rule is the one the CEN mapping intends: an identifier with the
         * form of an IBAN is written at {@code ram:IBANID}, and every other one at
         * {@code ram:ProprietaryID}. {@code conformance/writers/cii-roundtrip.md} records
         * it, because a round trip cannot see it: the reader maps both elements back to
         * BT-84.
         */
        private boolean suits(BindingTable.Path candidate, SemanticValue value) {
            List<BindingTable.Step> steps = candidate.steps();
            if (syntax != BindingSyntax.CII || candidate.attribute() != null
                    || !"IBANID".equals(steps.get(steps.size() - 1).localName())) {
                return true;
            }
            return IBAN.matcher(value.canonicalContent().toUpperCase(Locale.ROOT)).matches();
        }

        /**
         * Tells whether the schema has every element and attribute of an XPath, the ones a
         * supplementary component of the term is written at included, and whether every
         * condition on it is one a writer can meet.
         */
        private boolean admits(BindingTable.Path candidate, int depth) {
            String type = schema.rootType();
            List<BindingTable.Step> steps = expand(candidate.steps(), depth);
            for (int i = 1; i < steps.size(); i++) {
                SchemaTable.Child child = childOf(type, steps.get(i));
                if (child == null) {
                    return false;
                }
                type = child.type();
            }
            if (candidate.attribute() != null) {
                SchemaTable.Type owner = schema.type(type);
                if (owner == null || !owner.hasAttribute(candidate.attribute())) {
                    return false;
                }
            }
            for (BindingTable.ComponentBinding component : candidate.components()) {
                if (!admitsComponent(steps, component)) {
                    return false;
                }
            }
            return true;
        }

        /** Tells whether the schema has the element or attribute a component is written at. */
        private boolean admitsComponent(List<BindingTable.Step> steps,
                                        BindingTable.ComponentBinding component) {
            Relative relative = Relative.of(component.xpath());
            int depth = steps.size() - relative.up();
            if (depth < 1) {
                return false;
            }
            String type = schema.rootType();
            for (int i = 1; i < depth; i++) {
                SchemaTable.Child child = childOf(type, steps.get(i));
                if (child == null) {
                    return false;
                }
                type = child.type();
            }
            for (String name : relative.elements()) {
                SchemaTable.Type owner = schema.type(type);
                SchemaTable.Child child = owner == null ? null : owner.child(name);
                if (child == null) {
                    return false;
                }
                type = child.type();
            }
            if (relative.attribute() == null) {
                return true;
            }
            SchemaTable.Type owner = schema.type(type);
            return owner != null && owner.hasAttribute(relative.attribute());
        }

        /** Tells whether an XPath has a place for every component the value carries. */
        private boolean carries(BindingTable.Path candidate, SemanticValue value) {
            for (Component.Role role : Component.Role.values()) {
                if (componentOf(value, role) != null && !binds(candidate, role)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean binds(BindingTable.Path candidate, Component.Role role) {
            for (BindingTable.ComponentBinding component : candidate.components()) {
                if (component.role() == role) {
                    return true;
                }
            }
            return false;
        }

        /** Writes the content, the attributes and the components of one value. */
        private boolean content(XmlElement target,
                                BindingTable.Path bound,
                                SemanticValue value,
                                SemanticPath path) {
            String written = text(bound, value, path);
            String text = representable(written, path, "the value");
            if (!written.isEmpty() && text.isEmpty() && bound.attribute() == null
                    && !bound.flags().contains(Flags.SUBJECT_CODE_PREFIX)) {
                // Nothing of the value survives into this syntax, so there is nothing to
                // write. The element the walk made for it would stand empty, which the
                // validation artefacts of both syntaxes refuse; it is taken back out, and
                // the note the scrubbing made says why the value did not travel.
                if (target.bare()) {
                    target.detach();
                }
                return false;
            }
            if (bound.flags().contains(Flags.SUBJECT_CODE_PREFIX)) {
                if (target.subjectCode() != null) {
                    return collided(path, "the element " + bound.xpath() + " already carries"
                            + " a code in front of its content");
                }
                target.subjectCode(text);
                prefixed.add(new Prefixed(target, path));
                return true;
            }
            if (bound.attribute() != null) {
                if (target.attribute(bound.attribute()) != null) {
                    return collided(path, "the attribute " + bound.attribute() + " of "
                            + bound.xpath() + " already carries a value");
                }
                target.attribute(bound.attribute(), text);
            } else {
                if (target.text() != null) {
                    return collided(path, "the element " + bound.xpath() + " already carries"
                            + " a value");
                }
                target.text(text);
            }
            for (BindingTable.ComponentBinding component : bound.components()) {
                String content = componentOf(value, component.role());
                if (content != null) {
                    component(target, component, representable(content, path,
                            "the " + component.role().jsonMember() + " of the value"));
                }
            }
            return true;
        }

        /**
         * Returns a string without the characters the target syntax cannot carry, and
         * names the value it stood in where there were any.
         *
         * <p>The rule and the reason for it are in {@link XmlCharacters}. The note carries
         * the code points and not the content, the way every other note of this writer
         * carries a term identifier and not a value.
         */
        private String representable(String text, SemanticPath path, String part) {
            if (!XmlCharacters.hasUnrepresentable(text)) {
                return text;
            }
            String kept = XmlCharacters.scrub(text);
            notes.add(new WriteNote(WriteNote.Kind.CHARACTER_NOT_REPRESENTABLE,
                    path.toString(), part + " carries " + XmlCharacters.describe(text)
                    + ", which no XML 1.0 document can hold and no escape of that syntax"
                    + " can stand for, so " + (kept.isEmpty()
                            ? "nothing of it could be written"
                            : "it was left out and the rest was written")));
            return kept;
        }

        /** Returns the supplementary component of a value, or {@code null} where it has none. */
        private static String componentOf(SemanticValue value, Component.Role role) {
            return switch (role) {
                case SCHEME -> value.scheme();
                case SCHEME_VERSION -> value.schemeVersion();
                case MIME_CODE -> value.mimeCode();
                case FILENAME -> value.filename();
            };
        }

        /** Writes one supplementary component, beside the value or on an element of its own. */
        private void component(XmlElement target,
                               BindingTable.ComponentBinding binding,
                               String content) {
            Relative relative = Relative.of(binding.xpath());
            XmlElement element = target;
            for (int i = 0; i < relative.up(); i++) {
                element = element.parent();
            }
            for (String name : relative.elements()) {
                element = descend(element, name);
            }
            if (relative.attribute() == null) {
                element.text(content);
            } else {
                element.attribute(relative.attribute(), content);
            }
        }

        /** Returns the lexical form the syntax writes a value in. */
        private String text(BindingTable.Path bound, SemanticValue value, SemanticPath path) {
            if (bound.flags().contains(Flags.CODE_LIST_2475)) {
                String code = TaxPointDateCode.toSyntax(value.canonicalContent());
                if (code.equals(value.canonicalContent())) {
                    notes.add(new WriteNote(WriteNote.Kind.VALUE_NOT_CONVERTED,
                            path.toString(), "the value is written at an element this"
                                    + " syntax states in UNTDID 2475 and it is no code of"
                                    + " the list the standard gives the term, so it was"
                                    + " written as it stands"));
                }
                return code;
            }
            if (!bound.flags().contains(Flags.DATE_FORMAT_102)) {
                return value.canonicalContent();
            }
            try {
                LocalDate date = value.asDate();
                return FORMAT_102.format(date);
            } catch (EsjException e) {
                notes.add(new WriteNote(WriteNote.Kind.VALUE_NOT_CONVERTED, path.toString(),
                        "the value is written at a date element whose format qualifier is"
                                + " 102 and it spells no calendar date, so it was written as"
                                + " it stands"));
                return value.canonicalContent();
            }
        }

        private boolean collided(SemanticPath path, String what) {
            notes.add(new WriteNote(WriteNote.Kind.VALUE_COLLIDED, path.toString(),
                    what + ", so this value was not written"));
            return false;
        }

        private void drop(WriteNote.Kind kind, SemanticPath path, String message) {
            notes.add(new WriteNote(kind, path.toString(), message));
            dropped++;
        }

        /**
         * Tells whether the document states any value of a business group.
         *
         * <p>It is what says whether an element that group owns has anything else in it.
         * A syntax may write a term inside such an element although the semantic model
         * states the term outside the group, and where the document carries no instance of
         * the group at all, that element would hold this value alone.
         */
        private boolean states(String group) {
            if (stated == null) {
                stated = new HashSet<>();
                for (SemanticPath path : document.values().keySet()) {
                    for (SemanticPath above : path.groupPaths()) {
                        stated.add(above.term());
                    }
                }
            }
            return stated.contains(group);
        }

        /**
         * Returns the business groups whose element the path stands at after a number of
         * steps, in the order the table states them, or an empty list where no group of
         * the table is written at that element.
         *
         * <p>More than one group can be written at one element: UBL writes the document
         * level allowances and the document level charges at the same
         * {@code cac:AllowanceCharge} and tells them apart by a condition on it. The
         * answer is every one of them, so that a caller asking whether the document states
         * the group whose element this is cannot be answered about the wrong one.
         */
        private List<String> groupsAt(List<BindingTable.Step> steps, int position) {
            if (groupElements == null) {
                groupElements = new LinkedHashMap<>();
                for (BindingTable.Entry entry : table.entries()) {
                    if (!entry.isGroup() || !entry.bound()) {
                        continue;
                    }
                    for (BindingTable.Path path : entry.paths()) {
                        groupElements.computeIfAbsent(
                                elements(path.steps(), path.steps().size() - 1),
                                place -> new ArrayList<>()).add(entry.id());
                    }
                }
            }
            return groupElements.getOrDefault(elements(steps, position), List.of());
        }

        /**
         * Returns an element the schema requires of the element a path stands at after a
         * number of steps and that nothing else will supply, or {@code null} where there
         * is none.
         *
         * <p>The element the path goes on to is not one of them, and neither is one
         * {@link #skeleton} supplies, which is every required element whose own type asks
         * for nothing. What is left is content: an element the schema requires and only a
         * business term can fill.
         */
        private String requiredContent(List<BindingTable.Step> steps,
                                       int position,
                                       String next) {
            String type = schema.rootType();
            for (int i = 1; i <= position; i++) {
                SchemaTable.Child child = childOf(type, steps.get(i));
                if (child == null) {
                    return null;
                }
                type = child.type();
            }
            SchemaTable.Type declared = schema.type(type);
            if (declared == null) {
                return null;
            }
            for (SchemaTable.Child child : declared.children()) {
                if (child.required() && !child.name().equals(next)
                        && !(child.type() != null && schema.isSkeletal(child.type()))) {
                    return child.name();
                }
            }
            return null;
        }

        /** Returns the element path of the first steps of a path, for comparing two. */
        private static String elements(List<BindingTable.Step> steps, int last) {
            StringBuilder text = new StringBuilder();
            for (int i = 1; i <= last; i++) {
                text.append('/').append(steps.get(i).localName());
            }
            return text.toString();
        }

        /** Returns the reason a term has no place, where the table gives one. */
        private String because(Iterable<String> flags) {
            for (String flag : flags) {
                String definition = table.flagDefinitions().get(flag);
                if (definition != null) {
                    return ": " + definition;
                }
            }
            return "";
        }

        /**
         * Returns the chain of business groups a path lies in, or {@code null} where one of
         * them has no place in this syntax.
         *
         * <p>A business term that occurs more than once inside the same group instance is
         * in the chain as well, because its occurrence index has to be written on an
         * element just as a group's has: two seller identifiers are two elements, and which
         * of them a value goes into is what the index says. It is the last link and it owns
         * no element, because the terms below it are its own supplementary components and
         * they belong wherever the value does.
         */
        private Chain chain(SemanticPath path) {
            List<Group> groups = new ArrayList<>();
            List<PathSegment> segments = path.segments();
            int depth = 0;
            for (int i = 0; i < segments.size(); i++) {
                if (!(segments.get(i) instanceof PathSegment.Term term)) {
                    continue;
                }
                int index = i + 1 < segments.size()
                        && segments.get(i + 1) instanceof PathSegment.Index at
                        ? at.value() : -1;
                boolean last = i + (index < 0 ? 1 : 2) == segments.size();
                if (last && !path.isGroupPath() && index < 0) {
                    continue;
                }
                BindingTable.Entry entry = table.entry(term.id());
                if (entry == null) {
                    return null;
                }
                String key = path.prefix(index < 0 ? i + 1 : i + 2).toString();
                if (!entry.bound()) {
                    if (!entry.isGroup() || !entry.flags().contains(Flags.NOT_REPRESENTED)) {
                        return null;
                    }
                    // A group the syntax states no element of at all, while the terms
                    // inside it are bound where the standard puts them: the invoice note
                    // and the process control of UBL. The group is transparent, and its
                    // occurrence index lands on the innermost element of the term's own
                    // path that the schema lets repeat, the way a repeated term's does.
                    groups.add(new Group(term.id(), index, List.of(), key, false));
                    continue;
                }
                BindingTable.Path bound = entry.paths().get(0);
                if (nests(bound)) {
                    depth++;
                }
                groups.add(new Group(term.id(), index, expand(bound.steps(), depth), key,
                        last && !path.isGroupPath()));
            }
            return new Chain(groups, depth);
        }

        /** Tells whether an XPath reaches its own element over the descendant axis. */
        private static boolean nests(BindingTable.Path bound) {
            List<BindingTable.Step> steps = bound.steps();
            return bound.attribute() == null && !steps.isEmpty()
                    && steps.get(steps.size() - 1).descendant();
        }

        /**
         * Returns the steps of an XPath with every descendant step written out as that
         * many child steps.
         *
         * <p>{@code cac:InvoiceLine//cac:SubInvoiceLine} at a depth of two is an invoice
         * line, a sub invoice line and a sub invoice line of that one. The depth is a fact
         * of the semantic path — how many nested groups it lies inside — and not of the
         * table, which states one expression for every depth.
         */
        private static List<BindingTable.Step> expand(List<BindingTable.Step> steps,
                                                      int depth) {
            List<BindingTable.Step> expanded = new ArrayList<>(steps.size() + depth);
            for (BindingTable.Step step : steps) {
                if (!step.descendant()) {
                    expanded.add(step);
                    continue;
                }
                for (int i = 0; i < Math.max(1, depth); i++) {
                    expanded.add(new BindingTable.Step(step.namespace(), step.localName(),
                            step.predicates(), false));
                }
            }
            return expanded;
        }

        /**
         * Tells whether an XPath is the one to write a value of this nesting depth at.
         *
         * <p>A core term an extension group reuses is bound twice by the same table: once
         * where the standard puts it and once inside the group that nests. Which of the two
         * a value takes is decided by whether its own path lies inside that group, and by
         * nothing else.
         */
        private static boolean applies(BindingTable.Path candidate, int depth) {
            boolean nested = false;
            for (BindingTable.Step step : candidate.steps()) {
                nested |= step.descendant();
            }
            return nested == depth > 0;
        }

        /** Returns the child the schema gives a type under the name of a step. */
        private SchemaTable.Child childOf(String type, BindingTable.Step step) {
            SchemaTable.Type owner = schema.type(type);
            return owner == null ? null : owner.child(prefixed(step));
        }

        /** Returns the prefixed name of a step, as the schema table writes element names. */
        private String prefixed(BindingTable.Step step) {
            return prefixed(step.namespace(), step.localName());
        }

        /**
         * Returns the prefixed form of an expanded name, with the prefix the binding table
         * of this syntax gives that namespace. The schema table is written with the same
         * prefixes, and a test of this module holds the two to it.
         */
        private String prefixed(String namespace, String localName) {
            for (Map.Entry<String, String> entry : table.namespaces().entrySet()) {
                if (entry.getValue().equals(namespace)) {
                    return entry.getKey().isEmpty() ? localName
                            : entry.getKey() + ":" + localName;
                }
            }
            return localName;
        }

        /**
         * Returns the one child of an element under a name, creating it where it is not
         * there yet. It is the walk for an element the syntax writes once: the predicates
         * inside a binding path and the elements a supplementary component hangs off.
         */
        private XmlElement descend(XmlElement element, String name) {
            List<XmlElement> found = element.occurrence(name, -1);
            return found.isEmpty() ? create(element, name, -1) : found.get(0);
        }

        /** Creates a child of an element under an occurrence of a name. */
        private XmlElement create(XmlElement element, String name, int instance) {
            SchemaTable.Type type = schema.type(element.type());
            SchemaTable.Child declared = type == null ? null : type.child(name);
            return element.add(instance, new XmlElement(name,
                    declared == null ? null : declared.type(),
                    declared == null ? Integer.MAX_VALUE : declared.order(), created++));
        }

        /**
         * Tells whether an element can carry what a condition on a step asks of it: it
         * either already says what the condition says, or it does not say anything about
         * the question and can be made to.
         *
         * <p>This is what decides whether two business terms bound to elements of the same
         * name are one element or two. A term bound without a condition fits any element of
         * its name, which is how the rate of a category trade tax joins the category code
         * that names it. Two terms bound with conditions that cannot both hold — a document
         * reference of type code 50 and one of type code 130 — do not fit each other, and
         * the second gets an element of its own.
         */
        private boolean fits(XmlElement element, BindingTable.Step step) {
            for (Predicate predicate : step.predicates()) {
                if (!fits(element, predicate)) {
                    return false;
                }
            }
            return true;
        }

        /** Tells whether an element can carry what one condition asks of it. */
        private boolean fits(XmlElement element, Predicate predicate) {
            return switch (predicate.kind()) {
                case ATTRIBUTE_PRESENT -> true;
                case ATTRIBUTE_EQUALS ->
                        !element.excludes("@" + predicate.attribute(), predicate.literal())
                        && (element.attribute(predicate.attribute()) == null
                                || element.attribute(predicate.attribute())
                                        .equals(predicate.literal()));
                case ATTRIBUTE_NOT_EQUALS ->
                        !predicate.literal().equals(element.attribute(predicate.attribute()));
                case ATTRIBUTE_REFERENCE -> element.referenceTarget(predicate.attribute())
                        == null || element.referenceTarget(predicate.attribute())
                                .equals(target(predicate));
                case CHILD_EQUALS -> {
                    if (element.excludes(key(predicate.path()), predicate.literal())) {
                        yield false;
                    }
                    XmlElement child = element;
                    for (Name name : predicate.path()) {
                        List<XmlElement> found = child.occurrence(
                                prefixed(name.namespace(), name.localName()), -1);
                        if (found.isEmpty()) {
                            yield true;
                        }
                        child = found.get(0);
                    }
                    yield child.text() == null || child.text().equals(predicate.literal());
                }
                case CHILD_NOT_EQUALS -> {
                    XmlElement child = element;
                    for (Name name : predicate.path()) {
                        List<XmlElement> found = child.occurrence(
                                prefixed(name.namespace(), name.localName()), -1);
                        if (found.isEmpty()) {
                            yield true;
                        }
                        child = found.get(0);
                    }
                    yield !predicate.literal().equals(child.text());
                }
            };
        }

        /** Returns the path of a child predicate as one text, which names it on an element. */
        private String key(List<Name> path) {
            StringBuilder text = new StringBuilder();
            for (Name name : path) {
                if (text.length() > 0) {
                    text.append('/');
                }
                text.append(prefixed(name.namespace(), name.localName()));
            }
            return text.toString();
        }

        /** Returns the element a reference predicate names, as one text. */
        private String target(Predicate predicate) {
            StringBuilder path = new StringBuilder();
            for (Name name : predicate.path()) {
                path.append('/').append(prefixed(name.namespace(), name.localName()));
            }
            return path.toString();
        }

        /** Writes into an element what the predicate on its step says has to be true of it. */
        private void satisfy(XmlElement element, BindingTable.Step step) {
            for (Predicate predicate : step.predicates()) {
                satisfy(element, predicate);
            }
        }

        /** Writes into an element what one condition says has to be true of it. */
        private void satisfy(XmlElement element, Predicate predicate) {
            switch (predicate.kind()) {
                case ATTRIBUTE_PRESENT -> {
                    // The attribute is the supplementary component of the value, and the
                    // value writes it. Nothing has to be added for the element to qualify.
                }
                case ATTRIBUTE_EQUALS -> element.attribute(predicate.attribute(),
                        predicate.literal());
                case ATTRIBUTE_NOT_EQUALS -> {
                    // An element that carries no such attribute already satisfies the
                    // condition, and the value writes the attribute only where its own
                    // supplementary component says so. What the attribute may not become
                    // is remembered, so that a term bound to the same element with the
                    // opposite condition gets an element of its own.
                    element.exclude("@" + predicate.attribute(), predicate.literal());
                }
                case ATTRIBUTE_REFERENCE -> {
                    if (element.referenceTarget(predicate.attribute()) == null) {
                        element.referenceTarget(predicate.attribute(), target(predicate));
                        references.add(new Reference(element, predicate.attribute(),
                                predicate.path().stream()
                                        .map(name -> prefixed(name.namespace(),
                                                name.localName()))
                                        .toList()));
                    }
                }
                case CHILD_EQUALS -> {
                    XmlElement child = element;
                    for (Name name : predicate.path()) {
                        child = descend(child, prefixed(name.namespace(), name.localName()));
                    }
                    if (child.text() == null) {
                        child.text(predicate.literal());
                    }
                }
                case CHILD_NOT_EQUALS -> {
                    // An element that does not carry the child at all satisfies the
                    // condition, so nothing is written for it. What the element must not
                    // come to carry is remembered, so that a term bound to the same
                    // element with the opposite condition gets an element of its own
                    // instead of writing the forbidden content into this one.
                    element.exclude(key(predicate.path()), predicate.literal());
                }
            }
        }

        /**
         * Writes the element tree out as the bytes of a document.
         *
         * <p>The bytes are built directly rather than as characters that are then turned
         * into a string and encoded, and the bound is compared as they are produced rather
         * than once they all exist: a bound that can only be reached by producing the
         * output it refuses is no bound at all, and the three copies the older shape held
         * at once were the largest single cost of writing a large invoice.
         */
        private byte[] serialize() {
            Utf8Sink out = new Utf8Sink(options.maxOutputBytes());
            out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            if (options.indent()) {
                out.append('\n');
            }
            declare(root);
            root.write(out, 0, options.indent());
            return out.toByteArray();
        }

        /** Declares every namespace the syntax uses on the document element. */
        private void declare(XmlElement element) {
            for (Map.Entry<String, String> namespace
                    : new TreeMap<>(table.namespaces()).entrySet()) {
                element.attribute(namespace.getKey().isEmpty() ? "xmlns"
                        : "xmlns:" + namespace.getKey(), namespace.getValue());
            }
        }
    }

    /**
     * One link of a semantic path: a business group it lies in, or the business term at its
     * end where that term occurs more than once, with the element path the table binds it
     * to.
     *
     * @param id    the term or group identifier
     * @param index the occurrence index, or {@code -1} where the model admits one
     * @param steps the element path the binding table gives it, with every descendant step
     *              written out, and empty where the syntax states no element of the group
     * @param key   the semantic path of this occurrence, which names the element it owns
     * @param term  whether this link is the term at the end rather than a group above it
     */
    private record Group(String id,
                         int index,
                         List<BindingTable.Step> steps,
                         String key,
                         boolean term) { }

    /**
     * The business groups a semantic path lies in, from the outermost inwards, with the
     * number of times the path lies inside a group the syntax nests in itself.
     *
     * <p>A syntax binding may reach a group over the descendant axis, which is how the
     * XRechnung extension binds the sub invoice line: one XPath for a line inside a line
     * inside a line, to any depth. The semantic path says which depth, and the writer
     * writes that XPath out to it.
     */
    private record Chain(List<Group> groups, int depth) { }

    /**
     * The condition that says an element of one schema type is an allowance rather than a
     * charge, with that type.
     */
    private record Indicator(String type, Predicate predicate) {

        /** Returns the first step of the path the condition asks about. */
        Name first() {
            return predicate.path().get(0);
        }
    }

    /** One element a code is written in front of the content of, with the term it is. */
    private record Prefixed(XmlElement element, SemanticPath path) { }

    /**
     * Where the occurrence index of one repeating business group is written: the position
     * in the element path, the name of the element at that position, the instance of the
     * business group above it that the element belongs to, and whether that element is
     * shared with that group above rather than being the group's own.
     */
    private record Slot(int position, String element, String owner, boolean shared) {

        /** Returns the key the writer finds a slot and counts its instances by. */
        String key() {
            return key(owner, position, element);
        }

        /** Returns the key of a position on an element path. */
        static String key(String owner, int position, String element) {
            return owner + "|" + position + "|" + element;
        }
    }

    /** Where the index of one business group landed, and under which instance above it. */
    private record Anchor(int position, String owner) { }

    /**
     * An XPath relative to the element a supplementary component belongs to: how many steps
     * it goes up, the elements it goes down and the attribute it ends in.
     */
    private record Relative(int up, List<String> elements, String attribute) {

        private static Relative of(String xpath) {
            int up = 0;
            List<String> elements = new ArrayList<>();
            String attribute = null;
            for (String part : xpath.split("/")) {
                if (part.equals("..")) {
                    up++;
                } else if (part.startsWith("@")) {
                    attribute = part.substring(1);
                } else if (!part.isEmpty()) {
                    elements.add(part);
                }
            }
            return new Relative(up, List.copyOf(elements), attribute);
        }
    }

    /** One attribute whose value is the content of an element elsewhere in the document. */
    private record Reference(XmlElement element, String attribute, List<String> path) {

        /**
         * Copies the content of the element the reference names into the attribute. The
         * element is looked up once everything has been written, because the document may
         * name it after the value that refers to it.
         */
        private void resolve(XmlElement root) {
            XmlElement found = root;
            for (int i = 1; i < path.size() && found != null; i++) {
                List<XmlElement> children = found.occurrence(path.get(i), -1);
                found = children.isEmpty() ? null : children.get(0);
            }
            if (found != null && found.text() != null) {
                element.attribute(attribute, found.text());
            }
        }
    }

    /**
     * The walk of one element path: which element of it carries the occurrence index of
     * which business group, and which element of the tree each step lands on.
     */
    private static final class Placement {

        private final List<BindingTable.Step> steps;
        private final int[] anchors;
        private final String[] owners;
        private final int[] passed;
        private final Map<String, Anchor> byGroup = new LinkedHashMap<>();
        private final boolean ownContent;
        private String refusal;

        private Placement(List<BindingTable.Step> steps) {
            this(steps, true);
        }

        /**
         * @param steps      the element path, with every descendant step written out
         * @param ownContent whether the value is the content of the element the path ends
         *                   at, rather than one of its attributes
         */
        private Placement(List<BindingTable.Step> steps, boolean ownContent) {
            this.steps = steps;
            this.ownContent = ownContent;
            this.anchors = new int[steps.size()];
            this.owners = new String[steps.size()];
            this.passed = new int[steps.size()];
            Arrays.fill(anchors, -1);
        }

        /** Returns where the index of one group landed, or {@code null} where it did not. */
        private Anchor anchorOf(String groupKey) {
            return byGroup.get(groupKey);
        }

        /** Returns the instance of the business group the element at a position belongs to. */
        private String ownerAt(int position) {
            return owners[position] == null ? "" : owners[position];
        }

        /**
         * Works out which element of the path carries the occurrence index of each business
         * group above the term, and reports whether the syntax has room for all of them.
         */
        private boolean anchor(Chain chain, Run run) {
            int last = 0;
            for (Group group : chain.groups()) {
                if (group.steps().isEmpty()) {
                    // A group this syntax states no element of. Its instances are
                    // instances of the innermost element the term's own path repeats at.
                    anchorInnermost(group, run, last);
                    if (refusal != null) {
                        return false;
                    }
                    Anchor placed = byGroup.get(group.key());
                    if (placed != null) {
                        last = Math.max(last, placed.position());
                    }
                    continue;
                }
                if (group.term()) {
                    // The last link is the term itself, and the element its occurrence
                    // index belongs on is the one the path ends at, whichever of the XPaths
                    // the table gives the term the writer chose.
                    anchorInnermost(group, run, last);
                    if (refusal != null) {
                        return false;
                    }
                    Anchor placed = byGroup.get(group.key());
                    if (placed != null) {
                        last = Math.max(last, placed.position());
                    }
                    continue;
                }
                if (!prefixOf(group.steps(), steps)) {
                    // The group's element is not on this term's path, which is how a syntax
                    // writes a term of a group somewhere else entirely. Where the group's
                    // index is written on an element this path does share, the term still
                    // belongs to that instance of it.
                    Slot slot = run.slots.get(group.id());
                    if (group.index() >= 0 && slot != null && slot.position() < steps.size()
                            && steps.get(slot.position()).localName().equals(slot.element())
                            && slot.owner().equals(ownerAt(slot.position()))) {
                        anchors[slot.position()] = group.index();
                        byGroup.put(group.key(), new Anchor(slot.position(), slot.owner()));
                        last = Math.max(last, slot.position());
                    }
                    continue;
                }
                int at = group.steps().size() - 1;
                if (group.index() < 0) {
                    owners[at] = group.key();
                    continue;
                }
                int position = repeatable(run, last, at);
                if (position < 0) {
                    if (group.index() > 0) {
                        refusal = "the schema of this syntax admits one " + group.id()
                                + " where the document carries more, so instance "
                                + group.index() + " has nowhere to stand";
                        return false;
                    }
                    // One instance and one element: the schema has room for it although it
                    // has room for no second one.
                    position = at;
                } else {
                    anchors[position] = group.index();
                    last = position;
                }
                byGroup.put(group.key(), new Anchor(position, ownerAt(position)));
                owners[at] = group.key();
            }
            return true;
        }

        /**
         * Works out which element carries the occurrence index of the business term at the
         * end of the path.
         *
         * <p>A term that occurs twice in one group instance is two elements of the syntax,
         * and the innermost element of its path that the schema lets repeat is the one that
         * is written twice: a second seller identifier is a second identifier element and
         * not a second seller. The term owns no element, because the elements below it
         * carry its own supplementary components and belong where its value does.
         */
        private void anchorInnermost(Group group, Run run, int last) {
            int at = steps.size() - 1;
            if (group.index() < 0) {
                return;
            }
            int position = innermostRepeatable(run, at + 1);
            if (position <= last) {
                position = -1;
            }
            if (position < 0) {
                if (group.index() > 0) {
                    refusal = "the schema of this syntax admits one " + group.id()
                            + " where the document carries more, so occurrence "
                            + group.index() + " has nowhere to stand";
                    return;
                }
                position = at;
            } else {
                anchors[position] = group.index();
            }
            byGroup.put(group.key(), new Anchor(position, ownerAt(position)));
        }

        /**
         * Returns why this syntax has no place for the value, where it would write it
         * alone inside the element of a business group the document does not state, or
         * {@code null} where the value has a place.
         *
         * <p>A syntax may nest a term where the semantic model does not: the payment due
         * date of a UBL credit note is written inside the payment means although BT-9
         * stands outside BG-16. Where the document states the group, the value goes into
         * the element the group has. Where it does not, the element would hold this value
         * alone — and where the schema requires content of that element as well, no
         * document of this syntax can hold the value at all. Writing it alone would make
         * an XML document the schema refuses, and filling what the schema asks for would
         * put into the document something nobody stated, so the value is left out and
         * named instead.
         *
         * <p>An element whose type requires nothing beyond what this value writes is not
         * this case: the value stands there on its own and the schema is satisfied.
         */
        private String outsideItsGroup(Run run) {
            for (int i = 1; i < steps.size() - 1; i++) {
                if (owners[i] != null || anchors[i] >= 0) {
                    continue;
                }
                List<String> groups = run.groupsAt(steps, i);
                if (groups.isEmpty() || groups.stream().anyMatch(run::states)) {
                    continue;
                }
                String content = run.requiredContent(steps, i,
                        run.prefixed(steps.get(i + 1)));
                if (content != null) {
                    String group = String.join(" or ", groups);
                    return "this syntax writes the value inside " + run.prefixed(steps.get(i))
                            + ", the element of " + group + ", and the schema requires "
                            + content + " of that element; the document states no " + group
                            + ", so there is no place in this syntax the value can be"
                            + " written";
                }
            }
            return null;
        }

        /** Returns the outermost element between two positions that the schema lets repeat. */
        private int repeatable(Run run, int from, int to) {
            String type = run.schema.rootType();
            int outermost = -1;
            int own = -1;
            for (int i = 1; i <= to; i++) {
                SchemaTable.Child child = run.childOf(type, steps.get(i));
                if (child == null) {
                    return -1;
                }
                if (i > from && child.repeatable()) {
                    if (outermost < 0) {
                        outermost = i;
                    }
                    own = i == to ? i : own;
                }
                type = child.type();
            }
            // The group's own element is the one that repeats where the schema lets it: a
            // second tax breakdown is a second subtotal and not a second tax total. Only
            // where it does not does the index move up to an element that does.
            return own >= 0 ? own : outermost;
        }

        /**
         * Walks the path from the document element down, creating what is not there yet,
         * and returns the elements the value is written on.
         *
         * <p>At each step the walk looks among the children of that name and occurrence for
         * one the condition on the step can hold of, and makes a new one where there is
         * none. There is more than one element at the end where the element that carries a
         * group's occurrence index is shared with the group above it: a term of that group
         * above belongs to every instance of the element, because the schema asks each of
         * them for it.
         *
         * <p>A walk can find that the element it needs would be a second child of a name
         * the schema admits once — two tax registrations of a party are one identifier
         * each, and the identifier is where the two differ. The walk then begins again and
         * passes over one more instance of the innermost element above it that the schema
         * does let repeat, which is where the difference belongs. What the abandoned walk
         * had created is taken back out, so an attempt leaves nothing behind.
         */
        private List<XmlElement> walk(XmlElement root, Run run) {
            for (int attempt = 0; attempt <= steps.size(); attempt++) {
                List<Created> created = new ArrayList<>();
                List<XmlElement> level = new ArrayList<>(List.of(root));
                int conflict = -1;
                for (int i = 1; i < steps.size() && conflict < 0; i++) {
                    BindingTable.Step step = steps.get(i);
                    String name = run.prefixed(step);
                    List<Integer> instances = instances(run, i);
                    List<XmlElement> next = new ArrayList<>(level.size() * instances.size());
                    for (XmlElement element : level) {
                        SchemaTable.Type type = run.schema.type(element.type());
                        SchemaTable.Child declared = type == null ? null : type.child(name);
                        if (declared == null) {
                            undo(created);
                            return List.of();
                        }
                        for (int instance : instances) {
                            XmlElement child = match(run, element, name, instance, step, i, true);
                            if (child == null) {
                                if (!declared.repeatable() && element.hasChildNamed(name)) {
                                    conflict = i;
                                    break;
                                }
                                child = run.create(element, name, instance);
                                child.owner(owners[i]);
                                created.add(new Created(element, instance, child));
                            }
                            run.satisfy(child, step);
                            next.add(child);
                        }
                        if (conflict >= 0) {
                            break;
                        }
                    }
                    level = next;
                }
                if (conflict < 0) {
                    return level;
                }
                undo(created);
                int branch = innermostRepeatable(run, conflict);
                if (branch < 0) {
                    // Nothing above it repeats, so there is nowhere else to put it. The
                    // element is written where it stands and the schema is left to say so.
                    return force(root, run);
                }
                passed[branch]++;
            }
            return List.of();
        }

        /**
         * Returns the child of an element the value belongs in, or {@code null} where the
         * walk has to make one.
         *
         * <p>An element that was made for one instance of a business group belongs to that
         * instance and to nothing else, so a term of another instance passes it by. An
         * element that was made for no group in particular is free, and a term of a group
         * takes it over: that is how a term the syntax nests inside a group the model
         * places it outside of — the tax point date inside the first tax breakdown — and
         * the terms of the breakdown itself end up in one element whichever of them the
         * document writes first.
         */
        private XmlElement match(Run run,
                                 XmlElement element,
                                 String name,
                                 int instance,
                                 BindingTable.Step step,
                                 int position,
                                 boolean claim) {
            String owner = owners[position];
            List<XmlElement> candidates = element.occurrence(name, instance);
            List<XmlElement> owned = new ArrayList<>(candidates.size());
            List<XmlElement> free = new ArrayList<>(candidates.size());
            for (XmlElement candidate : candidates) {
                if (candidate.owner() != null && owner != null
                        && !candidate.owner().equals(owner)) {
                    continue;
                }
                if (!run.fits(candidate, step)) {
                    continue;
                }
                if (owner != null && owner.equals(candidate.owner())) {
                    owned.add(candidate);
                } else {
                    free.add(candidate);
                }
            }
            owned.addAll(free);
            int skip = passed[position];
            for (XmlElement candidate : owned) {
                if (skip > 0) {
                    skip--;
                    continue;
                }
                if (claim && owner != null && candidate.owner() == null) {
                    candidate.owner(owner);
                }
                return candidate;
            }
            return null;
        }

        /** Walks with a new element wherever one is wanted, which is the last resort. */
        private List<XmlElement> force(XmlElement root, Run run) {
            List<XmlElement> level = new ArrayList<>(List.of(root));
            for (int i = 1; i < steps.size(); i++) {
                BindingTable.Step step = steps.get(i);
                String name = run.prefixed(step);
                List<XmlElement> next = new ArrayList<>();
                for (XmlElement element : level) {
                    for (int instance : instances(run, i)) {
                        XmlElement child = match(run, element, name, instance, step, i, true);
                        if (child == null) {
                            child = run.create(element, name, instance);
                            child.owner(owners[i]);
                        }
                        run.satisfy(child, step);
                        next.add(child);
                    }
                }
                level = next;
            }
            return level;
        }

        /**
         * Returns how many steps of this path the document already has elements for,
         * without making any.
         *
         * <p>Where a term is bound to more than one place, this is what decides between
         * them: the base quantity of a line price is written inside the gross price where
         * the line states a gross price and inside the net price where it states a net one.
         * Which of the two the line states is a fact of the document, and it stands there
         * by the time the quantity is written, because the canonical order of the semantic
         * paths puts a price before the quantity of that price.
         */
        private int standing(XmlElement root, Run run) {
            List<XmlElement> level = new ArrayList<>(List.of(root));
            int depth = 0;
            for (int i = 1; i < steps.size(); i++) {
                BindingTable.Step step = steps.get(i);
                String name = run.prefixed(step);
                List<XmlElement> next = new ArrayList<>();
                for (XmlElement element : level) {
                    for (int instance : instances(run, i)) {
                        XmlElement child = match(run, element, name, instance, step, i,
                                false);
                        if (child != null) {
                            next.add(child);
                        }
                    }
                }
                if (next.isEmpty()) {
                    return depth;
                }
                level = next;
                depth = i;
            }
            return depth;
        }

        /** Returns the innermost element above a position that the schema lets repeat. */
        private int innermostRepeatable(Run run, int position) {
            String type = run.schema.rootType();
            int found = -1;
            for (int i = 1; i < position; i++) {
                SchemaTable.Child child = run.childOf(type, steps.get(i));
                if (child == null) {
                    return found;
                }
                if (child.repeatable()) {
                    found = i;
                }
                type = child.type();
            }
            return found;
        }

        /**
         * Tells whether a value of the business group above a shared element belongs in
         * every instance of that element or only in the first.
         *
         * <p>The element belongs in every instance, because the schema asks each instance
         * for it: a syntax that writes one payment means per account asks each of them for
         * the payment means code, and a document with two accounts states it twice and
         * says nothing new by it. A supplementary component of such a value does not: it
         * qualifies the one value the semantic document states, and writing it again would
         * assert it of the second instance as well. UBL says so itself, and its validation
         * artefact refuses a second payment means text.
         */
        private boolean spreads() {
            return ownContent;
        }

        /** Returns the instances of the element at a position this value is written into. */
        private List<Integer> instances(Run run, int position) {
            if (anchors[position] >= 0) {
                return List.of(anchors[position]);
            }
            String element = steps.get(position).localName();
            Slot slot = run.slotsByKey.get(Slot.key(ownerAt(position), position, element));
            if (slot == null) {
                if (owners[position] == null
                        && run.slotsByPlace.containsKey(position + "|" + element)) {
                    // No business group of this value's own path owns the element here, and
                    // a group of the document writes its occurrence index on it. The value
                    // belongs to no instance of that group, so it belongs to the first: a
                    // payment due date written inside the payment means is the due date of
                    // the document and not of one account.
                    return List.of(0);
                }
                return List.of(-1);
            }
            if (!slot.shared() || !spreads()) {
                // The element repeats because a business group repeats, and this value
                // belongs to no instance of that group, so it belongs to the first.
                return List.of(0);
            }
            int count = run.slotCounts.getOrDefault(slot.key(), 1);
            List<Integer> instances = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                instances.add(i);
            }
            return Collections.unmodifiableList(instances);
        }

        /** Takes back out what an abandoned walk had created. */
        private static void undo(List<Created> created) {
            for (int i = created.size() - 1; i >= 0; i--) {
                Created made = created.get(i);
                made.parent().remove(made.instance(), made.child());
            }
        }

        /** Tells whether one element path is a prefix of another. */
        private static boolean prefixOf(List<BindingTable.Step> prefix,
                                        List<BindingTable.Step> steps) {
            if (prefix.size() > steps.size()) {
                return false;
            }
            for (int i = 0; i < prefix.size(); i++) {
                BindingTable.Step a = prefix.get(i);
                BindingTable.Step b = steps.get(i);
                if (!a.namespace().equals(b.namespace())
                        || !a.localName().equals(b.localName())) {
                    return false;
                }
            }
            return true;
        }

        /** One element a walk created, so that an abandoned walk can take it back out. */
        private record Created(XmlElement parent, int instance, XmlElement child) { }
    }
}
