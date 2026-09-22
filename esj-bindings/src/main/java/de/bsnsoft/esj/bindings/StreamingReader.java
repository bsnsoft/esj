package de.bsnsoft.esj.bindings;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.handler.DocumentCollector;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.model.Component;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportReport;
import de.bsnsoft.esj.imports.ImportResult;
import de.bsnsoft.esj.xr.XmlBytes;
import de.bsnsoft.esj.xr.XmlEncodingReport;
import de.bsnsoft.esj.xr.XrEncodingException;
import de.bsnsoft.esj.xr.XrEncodingMode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads a UBL 2.1 invoice, a UBL 2.1 credit note or a UN/CEFACT CII D16B invoice into a
 * {@link SemanticDocument} without building a tree of it.
 *
 * <p>The reader pulls the document one element at a time and matches the element against
 * the compiled binding table of its syntax (see {@link BindingTable}); where the table
 * gives a term that element, the content becomes a value and the value goes into the
 * document. What it holds while it works is one element and the values it has produced —
 * not the source document, not an intermediate tree, and not the invoice lines it has
 * already passed. An invoice of three hundred thousand lines therefore costs what one line
 * costs plus its own values, which is the whole reason this module exists; the XSLT path
 * of {@code esj-xr} builds three trees of such a document and takes minutes over it.
 *
 * <p>The syntax is recognized from the root element. What comes out is the same shape of
 * result as from the XSLT path: the document, and a report of what did not reach it.
 * {@code conformance/readers.md} states, per instance of the conformance corpus, where the
 * two readers agree and where they do not.
 *
 * <h2>What the reader refuses</h2>
 *
 * <ul>
 *   <li>a document type declaration, outright — which takes external entities, parameter
 *       entities and entity expansion with it;</li>
 *   <li>every external entity, external subset, DTD and schema: none is fetched;</li>
 *   <li>an input larger than {@link ReaderOptions#maxInputBytes()}, counted as the bytes
 *       arrive rather than after they have all arrived;</li>
 *   <li>elements nested deeper than {@link ReaderOptions#maxElementDepth()};</li>
 *   <li>an element larger than {@link ReaderOptions#maxBufferedBytes()} where the reader
 *       has to hold it whole to decide a predicate over it.</li>
 * </ul>
 *
 * <p>Each of these raises an exception of this module. Everything a document carries that
 * the reader could not place is not an exception but a note in the report.
 *
 * <h2>The encoding front door</h2>
 *
 * <p>{@link #read(byte[])} checks the bytes against the encoding the document declares
 * before the parser sees them, exactly as the XSLT path does, and
 * {@link ReaderOptions#encodingMode()} decides what happens where the two disagree:
 * {@link XrEncodingMode#REPAIR} recodes the document into UTF-8 and records
 * {@link ImportNote.Kind#ENCODING_REPAIRED} with what was declared and what the bytes are,
 * {@link XrEncodingMode#STRICT} refuses it with {@link XrEncodingException}. The
 * provenance digest is taken over the bytes the caller handed over rather than over the
 * recoded ones. {@link #read(InputStream)} does none of this: recoding needs the whole
 * document, and a reader whose point is that it holds one element does not hold one.
 *
 * <h2>What the reader writes within</h2>
 *
 * <p>The reader holds to every limit of {@link ReaderOptions#limits()} while it writes,
 * exactly as the XSLT path does: what does not fit is left out and named in the report,
 * and a bound on the whole document ends the read where it is reached rather than
 * producing a document that looks complete and that such a reader refuses whole.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class StreamingReader {

    /** The length of a SHA-256 digest, which fixes the length of the provenance member. */
    private static final int SHA256_BYTES = 32;

    /** Where a note about the document as a whole is located. */
    private static final String DOCUMENT_LOCATION = "/";

    private final ReaderOptions options;

    /** Creates a reader with {@link ReaderOptions#defaults()}. */
    public StreamingReader() {
        this(ReaderOptions.defaults());
    }

    /**
     * Creates a reader with options of its caller's choosing.
     *
     * @param options what the reader is allowed to do and what it writes within
     * @throws NullPointerException if {@code options} is {@code null}
     */
    public StreamingReader(ReaderOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    /**
     * Returns the options this reader was created with.
     *
     * @return the options
     */
    public ReaderOptions options() {
        return options;
    }

    /**
     * Reads a document held in memory.
     *
     * @param xml the bytes of the document
     * @return the document and the report of what did not reach it
     * @throws BindingSyntaxException if the root element belongs to no syntax this reader
     *                                reads
     * @throws BindingFormatException if the document is not well-formed XML this reader
     *                                accepts
     * @throws BindingLimitException  if the document asks for more bytes, more nesting or
     *                                more buffer than the reader grants it
     * @throws XrEncodingException    if the bytes are not written in the encoding the
     *                                document declares and this reader runs in
     *                                {@link XrEncodingMode#STRICT}
     * @throws NullPointerException   if {@code xml} is {@code null}
     */
    public ImportResult read(byte[] xml) {
        Objects.requireNonNull(xml, "xml");
        bound(xml.length, "");
        byte[] decoded = frontDoor(xml);
        if (decoded == xml) {
            return read(new ByteArrayInputStream(xml));
        }
        bound(decoded.length, " once recoded into UTF-8");
        ImportResult result = read(new ByteArrayInputStream(decoded), sha256().digest(xml));
        return new ImportResult(result.document(), recoded(result.report(), xml));
    }

    /**
     * Refuses a document longer than this reader reads, saying so in the words the other
     * reader of this repository uses, so that a caller meeting the bound is told the same
     * thing whichever reader ran and a tool over both can name the switch that raises it.
     *
     * @param length what is about to be read, in bytes
     * @param stage  where the length comes from, as a clause the sentence ends its first
     *               half with: empty for the bytes handed over, and a clause saying so for
     *               the recoded ones, which are longer than the file the caller sees
     */
    private void bound(int length, String stage) {
        if (length > options.maxInputBytes()) {
            throw new BindingLimitException("the document is " + length + " bytes long"
                    + stage + ", and this reader reads at most " + options.maxInputBytes());
        }
    }

    /**
     * Returns the bytes the parser is given: the ones handed over, or the document recoded
     * into UTF-8 where it is not written in the encoding it declares.
     *
     * <p>A charset this module does not read is refused here rather than by the parser,
     * which would report the markup of a document whose markup may be perfectly good.
     */
    private byte[] frontDoor(byte[] xml) {
        XmlEncodingReport report = XmlBytes.inspect(xml);
        String unreadable = report.unreadable().orElse(null);
        if (unreadable != null) {
            throw new BindingFormatException("the document is written in, or declares, the"
                    + " encoding " + unreadable + ", which this reader does not read");
        }
        if (report.consistent() || !report.repairable()) {
            return xml;
        }
        if (options.encodingMode() == XrEncodingMode.STRICT) {
            throw new XrEncodingException("the bytes of this document are not written in the"
                    + " encoding it declares: " + report.describe(),
                    report.declaration().orElse(null), report.assumed());
        }
        return XmlBytes.repair(xml, report);
    }

    /** Returns the report with the note that says the bytes were recoded before parsing. */
    private static ImportReport recoded(ImportReport report, byte[] xml) {
        List<ImportNote> notes = new ArrayList<>();
        notes.add(new ImportNote(ImportNote.Kind.ENCODING_REPAIRED, DOCUMENT_LOCATION,
                "the bytes were recoded into UTF-8 before parsing: "
                        + XmlBytes.inspect(xml).describe()));
        notes.addAll(report.notes());
        return new ImportReport(notes);
    }

    /**
     * Reads a document from a stream.
     *
     * <p>The stream is read once, from beginning to end, and is not closed: the caller
     * opened it and the caller closes it. Its bytes are digested as they pass, so the
     * provenance of the result carries the SHA-256 of the source without the source ever
     * being held.
     *
     * @param in the document, as bytes
     * @return the document and the report of what did not reach it
     * @throws BindingSyntaxException if the root element belongs to no syntax this reader
     *                                reads
     * @throws BindingFormatException if the document is not well-formed XML this reader
     *                                accepts
     * @throws BindingLimitException  if the document asks for more bytes, more nesting or
     *                                more buffer than the reader grants it
     * @throws UncheckedIOException   if the stream cannot be read
     * @throws NullPointerException   if {@code in} is {@code null}
     */
    public ImportResult read(InputStream in) {
        return read(in, null);
    }

    /**
     * Reads a document from a stream, with the provenance digest of bytes other than the
     * ones the parser is given.
     *
     * @param in     the document the parser reads
     * @param source the digest of the bytes the caller handed over, or {@code null} to
     *               digest the stream as it passes
     * @return the document and the report of what did not reach it
     */
    private ImportResult read(InputStream in, byte[] source) {
        Objects.requireNonNull(in, "in");
        MessageDigest digest = sha256();
        Counted counted = new Counted(in, options.maxInputBytes());
        DigestInputStream digested = new DigestInputStream(counted, digest);
        XMLStreamReader reader = open(digested);
        try {
            BindingSyntax syntax = detect(reader);
            Run run = new Run(syntax);
            run.walk(reader);
            counted.drain(digested);
            return run.result(syntax, source == null ? digest.digest() : source);
        } finally {
            close(reader);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime implements SHA-256", e);
        }
    }

    /**
     * Returns a parser that reads a document and fetches nothing.
     *
     * <p>The factory is the one the platform ships rather than whatever the class path
     * offers, because the properties set below are only as strong as the implementation
     * that honours them and a document from a stranger should not be read by a parser the
     * host application's class path chose. The XML front door of {@code esj-cli} says the
     * same thing the same way.
     *
     * <p>One thing this parser does that the rest of this project does not: an input whose
     * bytes are not the encoding it declares makes the default error reporter of the
     * platform parser write a line of its own to {@code System.err}, before this reader's
     * refusal and in the locale of the process. Setting {@code XMLInputFactory.REPORTER}
     * does not suppress it and the error handler of the underlying parser is not settable
     * on the factory. Decoding the bytes before the parser sees them would, but that needs
     * the declared encoding read off the first bytes first; {@code docs/deployment.md}
     * tells callers not to parse the error stream, so this is noise rather than a defect,
     * and it is recorded here so the next reader does not take it for an oversight.
     */
    private static XMLStreamReader open(InputStream in) {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.IS_VALIDATING, Boolean.FALSE);
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("this reader resolves no entity");
        });
        try {
            return factory.createXMLStreamReader(in);
        } catch (XMLStreamException e) {
            throw format(e);
        }
    }

    private static void close(XMLStreamReader reader) {
        try {
            reader.close();
        } catch (XMLStreamException e) {
            throw format(e);
        }
    }

    /** Advances to the root element and returns the syntax it belongs to. */
    private static BindingSyntax detect(XMLStreamReader reader) {
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD) {
                    throw new BindingFormatException("the document carries a document type"
                            + " declaration, which this reader refuses outright: a parser"
                            + " that read one could read the entities it may declare");
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String namespace = namespaceOf(reader);
                    String localName = reader.getLocalName();
                    for (BindingSyntax syntax : BindingSyntax.values()) {
                        if (syntax.matches(namespace, localName)) {
                            return syntax;
                        }
                    }
                    throw new BindingSyntaxException("this reader was asked to read a UBL"
                            + " invoice, a UBL credit note or a cross industry invoice, and"
                            + " the root element of the document is " + localName
                            + (namespace.isEmpty() ? " in no namespace" : " in " + namespace),
                            namespace, localName);
                }
            }
        } catch (XMLStreamException e) {
            throw format(e);
        }
        throw new BindingFormatException("the document has no root element");
    }

    private static String namespaceOf(XMLStreamReader reader) {
        String namespace = reader.getNamespaceURI();
        return namespace == null ? "" : namespace;
    }

    private static BindingFormatException format(XMLStreamException cause) {
        return new BindingFormatException("the document is not well-formed XML that this"
                + " reader accepts", cause);
    }

    /**
     * One read of one document. It holds the frames of the elements that are open, the
     * emitter that turns matched terms into values, and the content of the few elements a
     * predicate compares an attribute against.
     */
    private final class Run {

        private final MatchTrie trie;
        private final SemanticEmitter emitter;
        private final Frame[] frames;
        private final Map<String, String> referenceValues = new HashMap<>();
        private final Map<Integer, List<List<Name>>> referencesByDepth = new HashMap<>();
        private final List<String> pathNamespaces = new ArrayList<>();
        private final List<String> pathNames = new ArrayList<>();

        /**
         * The characters a single value may carry: the larger of the two bounds a value is
         * measured against, because the capture does not yet know which term an element
         * carries. It is a bound on the buffer and not the bound the value is admitted by;
         * {@code SemanticEmitter} still applies the right one of the two.
         */
        private final long valueCharacterBound;

        private int depth;
        private int skipping;
        private long heldCharacters;
        private int heldElements;

        private Run(BindingSyntax syntax) {
            this.trie = MatchTrie.of(syntax);
            this.emitter = new SemanticEmitter(options.registry(), options.limits(),
                    envelopeBytes(syntax), options.mode() == ReaderMode.REPAIR,
                    options.mode() == ReaderMode.STRICT, boundGroups(syntax));
            this.valueCharacterBound = Math.max(options.limits().maxStringBytes(),
                    options.limits().maxBinaryValueBytes());
            this.frames = new Frame[options.maxElementDepth() + 1];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = new Frame();
            }
            frames[0].active = List.of(trie.root());
            for (List<Name> reference : trie.references()) {
                referencesByDepth.computeIfAbsent(reference.size(), key -> new ArrayList<>())
                        .add(reference);
            }
        }

        /** Walks the whole document, the root element included. */
        private void walk(XMLStreamReader reader) {
            try {
                startStreamed(reader);
                while (depth > 0 || skipping > 0) {
                    if (!reader.hasNext()) {
                        throw new BindingFormatException("the document ends inside an element");
                    }
                    switch (reader.next()) {
                        case XMLStreamConstants.START_ELEMENT -> startStreamed(reader);
                        case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA,
                                XMLStreamConstants.SPACE -> characters(reader.getText());
                        case XMLStreamConstants.END_ELEMENT -> end();
                        default -> {
                            // A comment, a processing instruction or the end of the
                            // document carries nothing a business term is bound to.
                        }
                    }
                    if (emitter.isFull()) {
                        return;
                    }
                }
            } catch (XMLStreamException e) {
                throw format(e);
            }
        }

        /** Builds the result once the document has been read. */
        private ImportResult result(BindingSyntax syntax, byte[] digest) {
            SemanticDocument.Source origin = SemanticDocument.Source.of(syntax.provenance(),
                    HexFormat.of().formatHex(digest));
            DocumentCollector collector =
                    new DocumentCollector(SemanticDocument.builder().source(origin));
            emitter.emit(collector);
            return new ImportResult(collector.document(), new ImportReport(emitter.notes()));
        }

        /**
         * Enters one element of the stream. Where a candidate node can be decided only
         * once the element has been read whole, the element is taken into memory and
         * replayed from there.
         */
        private void startStreamed(XMLStreamReader reader) throws XMLStreamException {
            String namespace = namespaceOf(reader);
            String localName = reader.getLocalName();
            if (skipping > 0) {
                skipping++;
                return;
            }
            List<MatchTrie.Node> candidates = candidates(namespace, localName);
            if (candidates.isEmpty() && frames[depth].descendants.isEmpty()) {
                skipping = 1;
                return;
            }
            if (buffers(candidates)) {
                replay(capture(reader, 0));
                return;
            }
            enter(namespace, localName, candidates, null, reader);
        }

        /** Replays one element that was taken into memory, and everything below it. */
        private void replay(Buffered node) {
            if (skipping > 0) {
                skipping++;
            } else {
                List<MatchTrie.Node> candidates =
                        candidates(node.namespace(), node.localName());
                if (candidates.isEmpty() && frames[depth].descendants.isEmpty()) {
                    skipping = 1;
                } else {
                    enter(node.namespace(), node.localName(), candidates, node, null);
                }
            }
            if (skipping == 0) {
                characters(node.text());
                for (Buffered child : node.children()) {
                    if (emitter.isFull()) {
                        break;
                    }
                    replay(child);
                }
            }
            end();
        }

        /**
         * Pushes the frame of one element: the nodes it matched, the attributes and the
         * character content the reader will need of it, and the group instances it opens.
         */
        private void enter(String namespace,
                           String localName,
                           List<MatchTrie.Node> candidates,
                           Buffered node,
                           XMLStreamReader reader) {
            List<MatchTrie.Node> active = new ArrayList<>(candidates.size());
            for (MatchTrie.Node candidate : candidates) {
                if (holds(candidate.predicates(), node, reader)) {
                    active.add(candidate);
                }
            }
            if (active.isEmpty() && !candidates.isEmpty()) {
                unselected(candidates);
            }
            List<MatchTrie.Node> inherited = frames[depth].descendants;
            if (active.isEmpty() && inherited.isEmpty()) {
                skipping = 1;
                return;
            }
            if (depth + 1 >= frames.length) {
                throw new BindingLimitException("the document nests elements more than "
                        + options.maxElementDepth() + " deep, and this reader reads no"
                        + " deeper");
            }
            pathNamespaces.add(namespace);
            pathNames.add(localName);
            Frame frame = frames[++depth];
            frame.reset(active, node);
            frame.descendants = inherited;
            for (MatchTrie.Node matched : active) {
                if (!matched.reachable().isEmpty()) {
                    List<MatchTrie.Node> both = new ArrayList<>(frame.descendants);
                    both.addAll(matched.reachable());
                    frame.descendants = both;
                }
            }
            frame.reference = referenceAt();
            boolean wantsText = frame.reference != null;
            long bound = options.limits().maxStringBytes();
            boolean binary = false;
            for (MatchTrie.Node matched : active) {
                wantsText |= matched.needsText();
                for (MatchTrie.ValueTarget target : matched.valueTargets()) {
                    if (target.isBinary()) {
                        binary = true;
                        bound = Math.max(bound, options.limits().maxBinaryValueBytes());
                    }
                }
                if (matched.needsAttributes()) {
                    attributes(frame, node, reader);
                }
                for (String group : matched.groups()) {
                    emitter.openGroup(group);
                    frame.groups.add(group);
                }
            }
            frame.collectsText = wantsText;
            frame.textBound = bound;
            frame.binaryValue = binary;
            if (node != null && node.isOverlong()) {
                frame.overlong = true;
            }
        }

        /**
         * Adds the character content of the current element to what is collected of it,
         * and stops collecting the moment the content passes the bound the value would be
         * measured against anyway.
         *
         * <p>Without the bound here the whole content is built first and measured after,
         * so deciding that a value is too long to keep costs the heap the value would have
         * cost. The character count is a safe lower bound on the length of the UTF-8
         * encoding — every code unit is at least one byte — so a frame that passes it in
         * characters would have passed it in bytes, and {@link #end()} reports that one
         * term as {@code LIMIT_REACHED} without the value ever existing.
         */
        private void characters(String text) {
            if (skipping > 0) {
                return;
            }
            Frame frame = frames[depth];
            if (!frame.collectsText || frame.overlong) {
                return;
            }
            if (frame.text.length() + (long) text.length() > frame.textBound) {
                frame.overlong = true;
                frame.text.setLength(0);
                return;
            }
            frame.text.append(text);
        }

        /** Leaves one element, writing the values it carried and closing what it opened. */
        private void end() {
            if (skipping > 0) {
                skipping--;
                return;
            }
            Frame frame = frames[depth--];
            if (frame.overlong) {
                overlong(frame);
                return;
            }
            String content = frame.text.toString();
            for (MatchTrie.Node matched : frame.active) {
                for (MatchTrie.AttributeTarget target : matched.attributeTargets()) {
                    String value = frame.attributes.get(target.attribute());
                    if (value != null) {
                        emitter.value(target.termId(), value, noComponents(), false, null,
                                target.alternatives());
                    }
                }
                for (MatchTrie.ValueTarget target : matched.valueTargets()) {
                    if (conventional(matched, target, content, frame)) {
                        continue;
                    }
                    emitter.value(target.termId(),
                            target.isCodeList2475()
                                    ? TaxPointDateCode.toStandard(content) : content,
                            components(target, frame), target.isDateFormat102(),
                            matched.subjectCodeTerm(), target.alternatives());
                }
            }
            for (int i = frame.groups.size() - 1; i >= 0; i--) {
                emitter.closeGroup(frame.groups.get(i));
            }
            if (frame.reference != null) {
                referenceValues.put(frame.reference, content.strip());
            }
            pathNamespaces.remove(pathNamespaces.size() - 1);
            pathNames.remove(pathNames.size() - 1);
        }

        /**
         * Tells whether this element carries the value the binding table of its syntax is
         * written with where a document states no such term, rather than a statement of
         * the term it is bound to.
         *
         * <p>Three things have to hold, and all three are facts of the table: the element
         * is one a convention is written at, its content is character for character the
         * value of that convention, and the sibling the convention names stands beside it —
         * which is what says that the document was written for the syntax rather than for
         * the term. The value is then not read and the reader says so, because a reader
         * that drops a value without a word is the thing this module exists not to be.
         *
         * <p>The sibling may be written after this element, so the element above is one the
         * reader holds whole; {@link MatchTrie} marks it while it compiles the table.
         */
        private boolean conventional(MatchTrie.Node matched,
                                     MatchTrie.ValueTarget target,
                                     String content,
                                     Frame frame) {
            MatchTrie.ReadBack readBack = matched.convention();
            if (readBack == null
                    || !readBack.convention().term().equals(target.termId())
                    || !readBack.convention().value().equals(content.strip())) {
                return false;
            }
            Buffered element = frame.node;
            if (element == null || element.parent() == null
                    || element.parent().child(readBack.beside()) == null) {
                return false;
            }
            emitter.convention(target.termId(), readBack.convention().value(),
                    readBack.convention().element(), readBack.convention().notReadBeside());
            return true;
        }

        /**
         * Closes an element whose character content passed the bound on a single value.
         * The terms its content carried are reported one by one rather than written, the
         * groups it opened are closed, and a predicate that compares an attribute against
         * this element finds it absent, which is what it would find of a value the
         * document is not allowed to carry.
         *
         * <p>The terms its <em>attributes</em> carry are written, exactly as {@link #end()}
         * writes them. The bound that was passed is a bound on one value, and an attribute
         * is a different value: it arrived with the start tag, the parser has already
         * bounded it, and it is not the thing that was too long. A base quantity whose
         * content is refused still states its unit of measure, and dropping that unit with
         * no value and no note would be the silent loss this reader exists not to make.
         */
        private void overlong(Frame frame) {
            for (MatchTrie.Node matched : frame.active) {
                for (MatchTrie.AttributeTarget target : matched.attributeTargets()) {
                    String value = frame.attributes.get(target.attribute());
                    if (value != null) {
                        emitter.value(target.termId(), value, noComponents(), false, null,
                                target.alternatives());
                    }
                }
                for (MatchTrie.ValueTarget target : matched.valueTargets()) {
                    emitter.overlong(target.termId(), frame.binaryValue, frame.textBound);
                }
            }
            for (int i = frame.groups.size() - 1; i >= 0; i--) {
                emitter.closeGroup(frame.groups.get(i));
            }
            pathNamespaces.remove(pathNamespaces.size() - 1);
            pathNames.remove(pathNames.size() - 1);
        }

        /**
         * Returns the nodes that can match one element: the children of the nodes the
         * enclosing element matched, and the nodes an enclosing element reaches over the
         * descendant axis, which stay in scope for everything below it.
         */
        private List<MatchTrie.Node> candidates(String namespace, String localName) {
            String key = Name.key(namespace, localName);
            Frame frame = frames[depth];
            List<MatchTrie.Node> candidates = null;
            for (MatchTrie.Node open : frame.active) {
                candidates = join(candidates, open.childrenNamed(key));
            }
            for (MatchTrie.Node reachable : frame.descendants) {
                if (reachable.key().equals(key)) {
                    candidates = join(candidates, List.of(reachable));
                }
            }
            return candidates == null ? List.of() : candidates;
        }

        /** Returns the two lists as one, without copying where one of them is empty. */
        private static List<MatchTrie.Node> join(List<MatchTrie.Node> collected,
                                                 List<MatchTrie.Node> more) {
            if (more.isEmpty()) {
                return collected;
            }
            if (collected == null) {
                return more;
            }
            List<MatchTrie.Node> both = new ArrayList<>(collected);
            both.addAll(more);
            return both;
        }

        /** Tells whether one of the candidates can be decided only over the whole element. */
        private boolean buffers(List<MatchTrie.Node> candidates) {
            for (MatchTrie.Node candidate : candidates) {
                if (candidate.isBuffered()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Reports an element the binding of this syntax selects by a condition and this
         * document's element meets none of.
         *
         * <p>Every candidate of such an element carries a condition — a node without one
         * matches whatever arrives, so an empty set of matches means they all had one —
         * and the element therefore reached no business group and no term. That is a loss
         * like any other and belongs in the report: the source carried it, the semantic
         * document does not, and nothing downstream could otherwise tell. The note names
         * the element, what the binding reads to decide about it, and what is missing from
         * the document because the element was not selected.
         *
         * @param candidates the nodes the element could have matched
         */
        private void unselected(List<MatchTrie.Node> candidates) {
            MatchTrie.Node first = candidates.get(0);
            Set<String> selectors = new LinkedHashSet<>();
            Set<String> absent = new LinkedHashSet<>();
            for (MatchTrie.Node candidate : candidates) {
                selectors.add(candidate.selector());
                if (!candidate.absent().isEmpty()) {
                    absent.add(candidate.absent());
                }
            }
            emitter.notes().add(new ImportNote(ImportNote.Kind.UNPLACEABLE,
                    absent.isEmpty() ? first.xpath() : String.join(", ", absent),
                    "the source document carries " + first.xpath() + ", which the binding"
                            + " of this syntax selects by " + String.join(" and ", selectors)
                            + " and this element states no value that selects it, so "
                            + (absent.isEmpty() ? "nothing below it"
                                    : String.join(", ", absent))
                            + " did not reach the semantic document"));
        }

        /** Tells whether an element meets the condition of one node. */
        private boolean holds(List<Predicate> predicates,
                              Buffered node,
                              XMLStreamReader reader) {
            for (Predicate predicate : predicates) {
                if (!holds(predicate, node, reader)) {
                    return false;
                }
            }
            return true;
        }

        /** Tells whether one condition of a step holds of the element being read. */
        private boolean holds(Predicate predicate, Buffered node, XMLStreamReader reader) {
            return switch (predicate.kind()) {
                case ATTRIBUTE_PRESENT -> attribute(predicate.attribute(), node, reader) != null;
                case ATTRIBUTE_EQUALS -> predicate.literal()
                        .equals(stripped(attribute(predicate.attribute(), node, reader)));
                case ATTRIBUTE_NOT_EQUALS -> !predicate.literal()
                        .equals(stripped(attribute(predicate.attribute(), node, reader)));
                case ATTRIBUTE_REFERENCE -> {
                    String value = stripped(attribute(predicate.attribute(), node, reader));
                    yield value != null && value.equals(referenceValues.get(key(predicate.path())));
                }
                case CHILD_EQUALS, CHILD_NOT_EQUALS -> node != null && node.matches(predicate);
            };
        }

        private static String stripped(String value) {
            return value == null ? null : value.strip();
        }

        /** Returns one attribute of the element being entered, from either source. */
        private String attribute(String name, Buffered node, XMLStreamReader reader) {
            return node != null ? node.attributes().get(name)
                    : reader.getAttributeValue(null, name);
        }

        /** Collects the attributes of the element being entered into its frame. */
        private void attributes(Frame frame, Buffered node, XMLStreamReader reader) {
            if (frame.readAttributes) {
                return;
            }
            frame.readAttributes = true;
            if (node != null) {
                frame.attributes.putAll(node.attributes());
                return;
            }
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                frame.attributes.put(reader.getAttributeLocalName(i),
                        reader.getAttributeValue(i));
            }
        }

        /** Returns the supplementary components of one value, by role. */
        private Map<Component.Role, String> components(MatchTrie.ValueTarget target,
                                                       Frame frame) {
            if (target.components().isEmpty()) {
                return noComponents();
            }
            Map<Component.Role, String> components = noComponents();
            for (BindingTable.ComponentBinding binding : target.components()) {
                String value = component(binding, frame);
                if (value != null) {
                    components.put(binding.role(), value);
                }
            }
            return components;
        }

        /**
         * Returns one supplementary component: an attribute of the value element, or the
         * content of a sibling of it, which is how CII carries the scheme of a document
         * identifier.
         */
        private String component(BindingTable.ComponentBinding binding, Frame frame) {
            String xpath = binding.xpath();
            if (xpath.startsWith("@")) {
                return frame.attributes.get(xpath.substring(1));
            }
            if (frame.node == null || frame.node.parent() == null) {
                return null;
            }
            List<Name> path = siblingPath(xpath, frame);
            return path == null ? null : frame.node.parent().contentAt(path);
        }

        /**
         * Returns the sibling a component XPath of the form {@code ../prefix:Name} names,
         * with the prefix resolved against the namespaces of the element itself: a sibling
         * is written in the same namespace as the value it belongs to in both syntaxes,
         * and that is the one fact this needs.
         */
        private List<Name> siblingPath(String xpath, Frame frame) {
            String step = xpath.substring(3);
            int colon = step.indexOf(':');
            String local = colon < 0 ? step : step.substring(colon + 1);
            return List.of(new Name(frame.node.namespace(), local));
        }

        /**
         * Returns the key of the element being entered where a predicate somewhere
         * compares an attribute with its content, and {@code null} otherwise.
         */
        private String referenceAt() {
            List<List<Name>> references = referencesByDepth.get(pathNames.size());
            if (references == null) {
                return null;
            }
            for (List<Name> reference : references) {
                boolean same = true;
                for (int i = 0; i < reference.size() && same; i++) {
                    same = reference.get(i).matches(pathNamespaces.get(i), pathNames.get(i));
                }
                if (same) {
                    return key(reference);
                }
            }
            return null;
        }

        /**
         * Takes one element and everything below it into memory.
         *
         * <p>Three bounds hold this. The characters and the elements held are counted over
         * the whole subtree rather than per element — {@code heldCharacters} and
         * {@code heldElements} are fields of the run and are reset where the capture
         * starts — because a bound that counts one element at a time bounds nothing: a
         * document reference with three million empty children costs a gibibyte and not
         * one character. Passing either of those refuses the document, and the refusal
         * names which of the two was met, because a caller does something different about
         * content that is too large and about structure that is. The third bound is the
         * one on a single value, and it drops that value with a note instead of refusing
         * the document, exactly as it does on the streamed path.
         */
        private Buffered capture(XMLStreamReader reader, int level) throws XMLStreamException {
            if (depth + level + 1 >= frames.length) {
                throw new BindingLimitException("the document nests elements more than "
                        + options.maxElementDepth() + " deep, and this reader reads no"
                        + " deeper");
            }
            if (level == 0) {
                heldCharacters = 0;
                heldElements = 0;
            }
            if (++heldElements > options.maxBufferedElements()) {
                throw new BindingLimitException("an element this reader has to hold whole"
                        + " to decide a predicate over carries more than the "
                        + options.maxBufferedElements() + " elements it holds");
            }
            Map<String, String> attributes = new HashMap<>();
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
            }
            Buffered node = new Buffered(namespaceOf(reader), reader.getLocalName(),
                    attributes);
            long own = 0;
            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamConstants.START_ELEMENT ->
                            node.add(capture(reader, level + 1));
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA,
                            XMLStreamConstants.SPACE -> {
                        String text = reader.getText();
                        own += text.length();
                        if (!node.isOverlong() && own > valueCharacterBound) {
                            heldCharacters -= node.textLength();
                            node.markOverlong();
                        } else if (!node.isOverlong()) {
                            heldCharacters += text.length();
                            if (heldCharacters > options.maxBufferedBytes()) {
                                throw new BindingLimitException("an element this reader has"
                                        + " to hold whole to decide a predicate over"
                                        + " carries more than the "
                                        + options.maxBufferedBytes()
                                        + " characters it holds");
                            }
                            node.append(text);
                        }
                    }
                    case XMLStreamConstants.END_ELEMENT -> {
                        return node;
                    }
                    default -> {
                        // Nothing a business term is bound to.
                    }
                }
            }
            throw new BindingFormatException("the document ends inside an element");
        }

        private String key(List<Name> path) {
            StringBuilder key = new StringBuilder();
            for (Name name : path) {
                key.append(name.key());
            }
            return key.toString();
        }
    }

    /** Returns the identifiers of the business groups a table gives an element of their own. */
    private static Set<String> boundGroups(BindingSyntax syntax) {
        Set<String> groups = new LinkedHashSet<>();
        BindingTable table = BindingTable.of(syntax);
        for (BindingTable.Entry entry : table.entries()) {
            if (entry.isGroup() && entry.bound()) {
                groups.add(entry.id());
            }
        }
        return groups;
    }

    /**
     * Returns the bytes the canonical form of a document of this syntax takes while it
     * holds no value at all. The emitter starts its measurement of the document there and
     * adds what every value costs, so the size it holds to is the size of the document
     * and not an estimate of it. The digest is not known while the document is being read
     * and its length is, which is all the envelope depends on.
     */
    private static long envelopeBytes(BindingSyntax syntax) {
        SemanticDocument.Source origin = SemanticDocument.Source.of(syntax.provenance(),
                HexFormat.of().formatHex(new byte[SHA256_BYTES]));
        return Canonicalizer.canonicalBytes(
                SemanticDocument.builder().source(origin).build()).length;
    }

    private static Map<Component.Role, String> noComponents() {
        return new EnumMap<>(Component.Role.class);
    }

    /** What the reader knows about one element that is open. */
    private static final class Frame {

        private List<MatchTrie.Node> active = List.of();
        private List<MatchTrie.Node> descendants = List.of();
        private final Map<String, String> attributes = new HashMap<>();
        private final StringBuilder text = new StringBuilder();
        private final List<String> groups = new ArrayList<>(2);
        private Buffered node;
        private String reference;
        private boolean collectsText;
        private boolean readAttributes;
        private boolean overlong;
        private boolean binaryValue;
        private long textBound;

        /**
         * Prepares this frame for another element. Frames are reused down the depth of the
         * document rather than allocated per element: a document of three hundred thousand
         * lines opens millions of elements, and the reader is the one thing in the path
         * that gets to not allocate per element.
         */
        private void reset(List<MatchTrie.Node> matched, Buffered buffered) {
            active = matched;
            descendants = List.of();
            node = buffered;
            reference = null;
            collectsText = false;
            readAttributes = false;
            overlong = false;
            binaryValue = false;
            textBound = Long.MAX_VALUE;
            text.setLength(0);
            groups.clear();
            attributes.clear();
        }
    }

    /**
     * Counts the bytes that pass and refuses the one past the bound.
     *
     * <p>A stream has no length to ask for, so the bound is met while the document is
     * being read rather than before it: the reader stops at the first byte past the bound
     * and says so, which is what a caller who was handed a pipe can be told.
     */
    private static final class Counted extends InputStream {

        private final InputStream in;
        private final long bound;
        private long read;

        private Counted(InputStream in, long bound) {
            this.in = in;
            this.bound = bound;
        }

        @Override
        public int read() throws IOException {
            int value = in.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = in.read(buffer, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(long more) {
            read += more;
            if (read > bound) {
                throw new BindingLimitException("the document is longer than the " + bound
                        + " bytes this reader reads");
            }
        }

        /**
         * Reads what the parser left, so that the digest of the source covers the whole
         * document even where the read ended early at a limit of the document being
         * written.
         */
        private void drain(InputStream digested) {
            byte[] buffer = new byte[8192];
            try {
                while (digested.read(buffer) >= 0) {
                    // The bytes are digested by the stream they pass through.
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
