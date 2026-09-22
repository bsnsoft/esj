package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.handler.DocumentCollector;
import de.bsnsoft.esj.imports.ImportNote;
import de.bsnsoft.esj.imports.ImportReport;
import de.bsnsoft.esj.imports.ImportResult;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.Limits;
import de.bsnsoft.esj.model.Registry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.sf.saxon.s9api.XdmNode;

/**
 * Reads a UBL 2.1 invoice, a UBL 2.1 credit note or a UN/CEFACT CII D16B invoice into a
 * {@link SemanticDocument}.
 *
 * <p>The importer has no syntax knowledge of its own. It hands the document to the
 * stylesheets of the KoSIT XRechnung visualization, which produce the XR representation:
 * an XML tree whose elements carry in an {@code xr:id} attribute the business term or
 * business group of EN 16931-1 they stand for. From there the registry decides
 * everything: which identifiers exist, where they may sit, which of them carry an
 * occurrence index and which semantic data type each content spells.
 *
 * <p>Values are reported to a {@link de.bsnsoft.esj.handler.SemanticHandler}
 * in the canonical path order of the specification, section 7.4, with every group
 * instance opened and closed around the values it holds, and collected from there into
 * the document.
 *
 * <p>Every method comes in two forms. The plain one returns the document; the one whose
 * name ends in {@code WithReport} returns it together with the
 * {@link ImportReport}. The report covers what the mapper could not turn into a value:
 * an element the registry could not place, a content that spelled no value of its type,
 * a supplementary component the standard does not give that term. It does not cover what
 * the stylesheets never emitted, because the mapper never sees that. The known case is
 * the date: the CII templates read a date only where the format qualifier is
 * {@code 102}, the eight-digit calendar date, so a date written with another qualifier
 * reaches the XR representation as nothing at all and the document loses it silently. A
 * document read without looking at its report may be missing exactly the part a caller
 * cared about, and a report without a note is no promise that the source held nothing
 * more.
 *
 * <h2>What the importer refuses</h2>
 *
 * <p>The document is parsed before anything else happens, and the parser is closed off:
 *
 * <ul>
 *   <li>a document type declaration is refused outright, which takes external entities,
 *       parameter entities and entity expansion with it — an invoice that carries one
 *       ends in an {@link XrFormatException} even though it is legal XML;</li>
 *   <li>no external entity, external subset, DTD or schema is fetched;</li>
 *   <li>XInclude is not processed;</li>
 *   <li>the stylesheets come from the classpath of this module and from nowhere else, so
 *       an {@code xsl:include} reaches neither the file system nor the network, and the
 *       transformation dereferences no protocol at all;</li>
 *   <li>the input is bounded in size before it is parsed, see
 *       {@link #maxInputBytes()};</li>
 *   <li>an element whose semantic path would outgrow the limits the importer writes
 *       within is skipped together with everything below it, and the report says so, see
 *       {@link #readerLimits()};</li>
 *   <li>a value that would outgrow those limits, and a document that reaches one of
 *       them, are treated the same way and recorded as
 *       {@link ImportNote.Kind#LIMIT_REACHED}, see <em>What the importer writes
 *       within</em>.</li>
 * </ul>
 *
 * <h2>What the importer writes within</h2>
 *
 * <p>An importer is given the {@link Limits} of the reader its documents are written for,
 * and it holds to all of them: how long a semantic path may be, how many values a
 * document may carry, how long a string value and a binary value may be, how much binary
 * content a document may hold and how large the canonical document may be. A reader
 * running those limits therefore reads back what this importer produced; it does not
 * receive a document that looks complete and is refused whole, which is the failure a
 * caller cannot repair, because repairing it would mean truncating a business term.
 *
 * <p>What does not fit is left out and named in the report. A value that is too large by
 * itself is left out by itself. A bound on the whole document — the number of its values
 * or its own size — ends the import where it is reached, because nothing further could be
 * added anyway, and the note says that the rest of the source document was not read. A
 * caller that must have the whole document either raises the profile it imports with or
 * treats a {@link ImportNote.Kind#LIMIT_REACHED} note as a refusal of the input.
 *
 * <p>Two bounds are outside this account. The bound on the input is the importer's own and
 * raises {@link XrLimitException}; see {@link #maxInputBytes()}. And the bound on a string
 * value covers the {@code source.syntax} member as well (specification, section 12.2),
 * which this importer writes as two or three bytes; a profile whose string bound is below
 * that describes no document this importer can write and is not checked for.
 *
 * <h2>How deep a document may nest</h2>
 *
 * <p>The sub invoice line of the XRechnung extension carries further sub invoice lines,
 * to any depth the source syntax writes. A semantic path spends two segments per level,
 * and the limits of the specification, section 12.2 bound a path at sixteen segments, so
 * a reader running the defaults reads back six levels of sub invoice line and not a
 * seventh. This importer writes within the same bound by default: what lies deeper is
 * left out of the document and recorded as a {@link ImportNote.Kind#PATH_TOO_LONG} note
 * rather than written into a document that no such reader would accept. A caller that
 * reads and writes with a wider profile passes it to
 * {@link #XrImporter(Registry, long, Limits)} and gets the deeper levels.
 *
 * <h2>Encoding</h2>
 *
 * <p>Before the parser sees the bytes, they are checked against the encoding the document
 * declares, and a document that is not written in the charset it names is recoded into
 * UTF-8 — the commonest defect of an invoice in the field, and one that costs an XML
 * parser the whole document. What happens then is the importer's
 * {@link #encodingMode()}: {@link XrEncodingMode#REPAIR}, the default, recodes and
 * records {@link ImportNote.Kind#ENCODING_REPAIRED} with what was declared and what was
 * read, and {@link XrEncodingMode#STRICT} refuses the document with an
 * {@link XrEncodingException} carrying the same two facts. Neither is silent, and the
 * digest in the provenance of the result is over the bytes that were handed over rather
 * than over the recoded ones. {@link XmlBytes} says which charsets are recoded and which
 * are handed to the parser untouched.
 *
 * <h2>Normalizations</h2>
 *
 * <p>An importer runs the named normalizations of {@link XrNormalization} it was given,
 * which by default are all of {@link #DEFAULT_NORMALIZATIONS}. A normalization repairs a
 * place where the XR representation does not carry what the source syntax holds; it is
 * named and switchable so that a caller can see what was done to the document. The one of
 * this release splits the note subject code out of a UBL invoice note.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class XrImporter {

    /**
     * The provenance token of a document that was read from the XR representation
     * itself, which is neither of the two syntaxes the standard binds.
     */
    public static final String XR_PROVENANCE = "XR";

    /**
     * The largest input this importer accepts by default, in bytes: four mebibytes.
     *
     * <p>An EN 16931 invoice is a small document. The largest instance of the
     * conformance corpus, embedded attachment included, is under half a mebibyte, and
     * this bound sits an order of magnitude above that rather than at the size a machine
     * could hold, because the cost of reading a document does not grow with its size in
     * a straight line; see {@link #maxInputBytes()}.
     */
    public static final long DEFAULT_MAX_INPUT_BYTES = 4L * 1024L * 1024L;

    /**
     * The normalizations an importer runs unless it is given others: every one of
     * {@link XrNormalization}.
     */
    public static final Set<XrNormalization> DEFAULT_NORMALIZATIONS =
            Set.copyOf(EnumSet.allOf(XrNormalization.class));

    /**
     * The encoding mode an importer runs in unless it is given another:
     * {@link XrEncodingMode#REPAIR}.
     */
    public static final XrEncodingMode DEFAULT_ENCODING_MODE = XrEncodingMode.REPAIR;

    /**
     * The location an import note carries when it is about the document as a whole rather
     * than about an element of it.
     */
    private static final String DOCUMENT_LOCATION = "/";

    private static final Set<XrSyntax> UBL =
            EnumSet.of(XrSyntax.UBL_INVOICE, XrSyntax.UBL_CREDIT_NOTE);

    /** The provenance token of the syntax the note subject code normalization applies to. */
    private static final String UBL_PROVENANCE = "UBL";

    private final Registry registry;
    private final long maxInputBytes;
    private final Limits readerLimits;
    private final Set<XrNormalization> normalizations;
    private final XrEncodingMode encodingMode;

    /**
     * Creates an importer that knows the core model and the XRechnung extension, accepts
     * an input of up to {@link #DEFAULT_MAX_INPUT_BYTES} bytes, writes documents a reader
     * with {@link Limits#defaults()} reads and runs the
     * {@link #DEFAULT_NORMALIZATIONS}.
     */
    public XrImporter() {
        this(defaultRegistry(), DEFAULT_MAX_INPUT_BYTES);
    }

    /**
     * Creates an importer with a registry and an input bound of its caller's choosing.
     *
     * <p>The registry decides what the importer can represent. A registry without the
     * XRechnung extension turns every extension element of a document into a note rather
     * than into a value, which is the right behaviour for a reader that wants core terms
     * and nothing else.
     *
     * @param registry      the registry that decides the structure
     * @param maxInputBytes the largest input to accept, in bytes
     * @throws IllegalArgumentException if {@code maxInputBytes} is not positive
     * @throws NullPointerException     if {@code registry} is {@code null}
     */
    public XrImporter(Registry registry, long maxInputBytes) {
        this(registry, maxInputBytes, Limits.defaults());
    }

    /**
     * Creates an importer that writes documents for a reader of its caller's choosing and
     * runs the {@link #DEFAULT_NORMALIZATIONS}.
     *
     * <p>Every limit is used, not only the ones the shape of a document decides; see
     * <em>What the importer writes within</em> in the documentation of this class.
     *
     * @param registry      the registry that decides the structure
     * @param maxInputBytes the largest input to accept, in bytes
     * @param readerLimits  the limits of the reader the documents are written for
     * @throws IllegalArgumentException if {@code maxInputBytes} is not positive
     * @throws NullPointerException     if {@code registry} or {@code readerLimits} is
     *                                  {@code null}
     */
    public XrImporter(Registry registry, long maxInputBytes, Limits readerLimits) {
        this(registry, maxInputBytes, readerLimits, DEFAULT_NORMALIZATIONS);
    }

    /**
     * Creates an importer that writes documents for a reader of its caller's choosing and
     * runs the normalizations of its caller's choosing.
     *
     * @param registry       the registry that decides the structure
     * @param maxInputBytes  the largest input to accept, in bytes
     * @param readerLimits   the limits of the reader the documents are written for
     * @param normalizations the named corrections to apply, possibly none
     * @throws IllegalArgumentException if {@code maxInputBytes} is not positive
     * @throws NullPointerException     if an argument or an element of
     *                                  {@code normalizations} is {@code null}
     */
    public XrImporter(Registry registry,
                      long maxInputBytes,
                      Limits readerLimits,
                      Set<XrNormalization> normalizations) {
        this(registry, maxInputBytes, readerLimits, normalizations, DEFAULT_ENCODING_MODE);
    }

    private XrImporter(Registry registry,
                       long maxInputBytes,
                       Limits readerLimits,
                       Set<XrNormalization> normalizations,
                       XrEncodingMode encodingMode) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.readerLimits = Objects.requireNonNull(readerLimits, "readerLimits");
        this.normalizations =
                Set.copyOf(Objects.requireNonNull(normalizations, "normalizations"));
        this.encodingMode = Objects.requireNonNull(encodingMode, "encodingMode");
        if (maxInputBytes <= 0) {
            throw new IllegalArgumentException("an input bound is a positive number of bytes");
        }
        this.maxInputBytes = maxInputBytes;
    }

    /**
     * Returns an importer that is this one in every respect but the encoding mode.
     *
     * <p>The mode is set this way rather than through a constructor because it is the one
     * setting a caller changes per command rather than per deployment: the same importer
     * reads a document for a conversion and refuses it for a verdict.
     *
     * @param mode what to do with a document whose bytes are not written in the encoding
     *             it declares
     * @return the importer
     * @throws NullPointerException if {@code mode} is {@code null}
     */
    public XrImporter withEncodingMode(XrEncodingMode mode) {
        Objects.requireNonNull(mode, "mode");
        return mode == encodingMode ? this
                : new XrImporter(registry, maxInputBytes, readerLimits, normalizations, mode);
    }

    /**
     * Returns what this importer does with a document whose bytes are not written in the
     * encoding it declares.
     *
     * @return the encoding mode
     */
    public XrEncodingMode encodingMode() {
        return encodingMode;
    }

    /**
     * Returns the registry an importer uses unless it is given another one: the core
     * model of EN 16931-1 combined with the XRechnung extension.
     *
     * @return the combined registry
     */
    public static Registry defaultRegistry() {
        return DefaultRegistry.COMBINED;
    }

    /**
     * Returns the registry this importer asks about structure.
     *
     * @return the registry
     */
    public Registry registry() {
        return registry;
    }

    /**
     * Returns the largest input this importer accepts.
     *
     * <p>The bound is on bytes, and bytes are not what the work costs. Parsing and
     * mapping grow with the size of the document; the transformation by the vendored
     * stylesheets grows faster than that. Measured over synthetic UBL invoices that
     * differ only in the number of repeated document level notes — the cheapest input to
     * write, and therefore the one to measure the worst case on — one doubling of the
     * input cost between four and five times the time, and an invoice of 190 000 notes
     * that is still under this default held one thread busy for between eight and eleven
     * minutes on a current laptop before returning an ordinary document. Both figures
     * belong to the stylesheets rather than to this module, and nothing here can
     * interrupt the transformation once it has started.
     *
     * <p>A caller that raises this bound is therefore buying processor time and heap at a
     * worse rate than the number suggests; a caller that reads documents from strangers
     * is better served by a low bound and a rejected invoice than by a thread that is
     * busy for minutes, and a caller that needs a hard ceiling needs a timeout around the
     * call rather than a lower number here.
     *
     * @return the bound in bytes
     */
    public long maxInputBytes() {
        return maxInputBytes;
    }

    /**
     * Returns the limits of the reader this importer writes for.
     *
     * <p>They decide how deep a recursive group may nest, how many values a document may
     * carry, how large a value and the document itself may be, and therefore what the
     * importer leaves out rather than writing a document such a reader refuses; see
     * <em>What the importer writes within</em> in the documentation of this class.
     *
     * @return the reader limits
     */
    public Limits readerLimits() {
        return readerLimits;
    }

    /**
     * Returns the named normalizations this importer applies.
     *
     * @return the normalizations, possibly empty
     */
    public Set<XrNormalization> normalizations() {
        return normalizations;
    }

    /**
     * Reads a UBL 2.1 invoice or credit note.
     *
     * @param ubl the bytes of the document
     * @return the semantic document
     * @throws XrSyntaxException    if the root element is neither a UBL invoice nor a UBL
     *                              credit note
     * @throws XrFormatException    if the document is not well-formed XML this importer
     *                              accepts, or the transformation failed
     * @throws XrLimitException     if the document is larger than {@link #maxInputBytes()},
     *                              or its XR representation nests elements deeper than
     *                              this importer walks
     * @throws XrEncodingException  if the bytes are not written in the encoding the
     *                              document declares and this importer runs in
     *                              {@link XrEncodingMode#STRICT}
     * @throws NullPointerException if {@code ubl} is {@code null}
     */
    public SemanticDocument importUbl(byte[] ubl) {
        return importUblWithReport(ubl).document();
    }

    /**
     * Reads a UN/CEFACT CII D16B invoice.
     *
     * @param cii the bytes of the document
     * @return the semantic document
     * @throws XrSyntaxException    if the root element is no cross industry invoice
     * @throws XrFormatException    if the document is not well-formed XML this importer
     *                              accepts, or the transformation failed
     * @throws XrLimitException     if the document is larger than {@link #maxInputBytes()},
     *                              or its XR representation nests elements deeper than
     *                              this importer walks
     * @throws XrEncodingException  if the bytes are not written in the encoding the
     *                              document declares and this importer runs in
     *                              {@link XrEncodingMode#STRICT}
     * @throws NullPointerException if {@code cii} is {@code null}
     */
    public SemanticDocument importCii(byte[] cii) {
        return importCiiWithReport(cii).document();
    }

    /**
     * Reads a document of either syntax, recognized by its root element.
     *
     * @param xml the bytes of the document
     * @return the semantic document
     * @throws XrSyntaxException    if the root element belongs to no syntax this importer
     *                              reads
     * @throws XrFormatException    if the document is not well-formed XML this importer
     *                              accepts, or the transformation failed
     * @throws XrLimitException     if the document is larger than {@link #maxInputBytes()},
     *                              or its XR representation nests elements deeper than
     *                              this importer walks
     * @throws XrEncodingException  if the bytes are not written in the encoding the
     *                              document declares and this importer runs in
     *                              {@link XrEncodingMode#STRICT}
     * @throws NullPointerException if {@code xml} is {@code null}
     */
    public SemanticDocument importXml(byte[] xml) {
        return importXmlWithReport(xml).document();
    }

    /**
     * Reads a document that is already in the XR representation, as the stylesheets of
     * the KoSIT XRechnung visualization produce it.
     *
     * <p>The provenance of the result records {@link #XR_PROVENANCE}, because the bytes
     * that were read are neither UBL nor CII.
     *
     * @param xrXml the bytes of the XR document
     * @return the semantic document
     * @throws XrSyntaxException    if the root element is not {@code xr:invoice}
     * @throws XrFormatException    if the document is not well-formed XML this importer
     *                              accepts
     * @throws XrLimitException     if the document is larger than {@link #maxInputBytes()},
     *                              or its XR representation nests elements deeper than
     *                              this importer walks
     * @throws XrEncodingException  if the bytes are not written in the encoding the
     *                              document declares and this importer runs in
     *                              {@link XrEncodingMode#STRICT}
     * @throws NullPointerException if {@code xrXml} is {@code null}
     */
    public SemanticDocument fromXr(byte[] xrXml) {
        return fromXrWithReport(xrXml).document();
    }

    /**
     * Reads a UBL 2.1 invoice or credit note and keeps the report.
     *
     * @param ubl the bytes of the document
     * @return the document and the report
     * @throws XrSyntaxException    if the root element is neither a UBL invoice nor a UBL
     *                              credit note
     * @throws XrFormatException    if the document is not well-formed XML this importer
     *                              accepts, or the transformation failed
     * @throws XrLimitException     if the document is larger than {@link #maxInputBytes()},
     *                              or its XR representation nests elements deeper than
     *                              this importer walks
     * @throws XrEncodingException  if the bytes are not written in the encoding the
     *                              document declares and this importer runs in
     *                              {@link XrEncodingMode#STRICT}
     * @throws NullPointerException if {@code ubl} is {@code null}
     */
    public ImportResult importUblWithReport(byte[] ubl) {
        return read(ubl, UBL, "a UBL invoice or credit note");
    }

    /**
     * Reads a UN/CEFACT CII D16B invoice and keeps the report.
     *
     * @param cii the bytes of the document
     * @return the document and the report
     * @throws XrSyntaxException    if the root element is no cross industry invoice
     * @throws XrFormatException    if the document is not well-formed XML this importer
     *                              accepts, or the transformation failed
     * @throws XrLimitException     if the document is larger than {@link #maxInputBytes()},
     *                              or its XR representation nests elements deeper than
     *                              this importer walks
     * @throws XrEncodingException  if the bytes are not written in the encoding the
     *                              document declares and this importer runs in
     *                              {@link XrEncodingMode#STRICT}
     * @throws NullPointerException if {@code cii} is {@code null}
     */
    public ImportResult importCiiWithReport(byte[] cii) {
        return read(cii, EnumSet.of(XrSyntax.CII), "a cross industry invoice");
    }

    /**
     * Reads a document of either syntax and keeps the report.
     *
     * @param xml the bytes of the document
     * @return the document and the report
     * @throws XrSyntaxException    if the root element belongs to no syntax this importer
     *                              reads
     * @throws XrFormatException    if the document is not well-formed XML this importer
     *                              accepts, or the transformation failed
     * @throws XrLimitException     if the document is larger than {@link #maxInputBytes()},
     *                              or its XR representation nests elements deeper than
     *                              this importer walks
     * @throws XrEncodingException  if the bytes are not written in the encoding the
     *                              document declares and this importer runs in
     *                              {@link XrEncodingMode#STRICT}
     * @throws NullPointerException if {@code xml} is {@code null}
     */
    public ImportResult importXmlWithReport(byte[] xml) {
        return read(xml, EnumSet.allOf(XrSyntax.class), "a UBL invoice, a UBL credit note"
                + " or a cross industry invoice");
    }

    /**
     * Reads a document that is already in the XR representation and keeps the report.
     *
     * @param xrXml the bytes of the XR document
     * @return the document and the report
     * @throws XrSyntaxException    if the root element is not {@code xr:invoice}
     * @throws XrFormatException    if the document is not well-formed XML this importer
     *                              accepts
     * @throws XrLimitException     if the document is larger than {@link #maxInputBytes()},
     *                              or its XR representation nests elements deeper than
     *                              this importer walks
     * @throws XrEncodingException  if the bytes are not written in the encoding the
     *                              document declares and this importer runs in
     *                              {@link XrEncodingMode#STRICT}
     * @throws NullPointerException if {@code xrXml} is {@code null}
     */
    public ImportResult fromXrWithReport(byte[] xrXml) {
        bound(xrXml);
        Decoded decoded = frontDoor(xrXml);
        XdmNode root = XrTransformer.rootElement(XrTransformer.parse(decoded.bytes()));
        if (!XrTransformer.isXr(root)) {
            throw refuse(root, "the XR representation");
        }
        return collect(root, XR_PROVENANCE, xrXml, leading(decoded, List.of()));
    }

    private ImportResult read(byte[] xml, Set<XrSyntax> accepted, String expectation) {
        bound(xml);
        Decoded decoded = frontDoor(xml);
        XdmNode document = XrTransformer.parse(decoded.bytes());
        XdmNode root = XrTransformer.rootElement(document);
        Optional<XrSyntax> detected = XrTransformer.detect(root).filter(accepted::contains);
        if (detected.isEmpty()) {
            throw refuse(root, expectation);
        }
        XrSyntax syntax = detected.get();
        XdmNode xr = XrTransformer.rootElement(XrTransformer.transform(document, syntax));
        List<ImportNote> ahead = leading(decoded, XrCoverage.notes(root, syntax));
        return collect(xr, syntax.provenance(), xml, ahead);
    }

    /**
     * The bytes the parser is given, and the note that says they are not the bytes the
     * caller handed over.
     */
    private record Decoded(byte[] bytes, Optional<ImportNote> note) {
    }

    /**
     * Checks the bytes against the encoding the document declares, and recodes them or
     * refuses them according to {@link #encodingMode()}.
     *
     * <p>A recoded document is measured against the input bound a second time, because
     * recoding a single-byte charset into UTF-8 can double the length and the bound is
     * there to bound what the parser is handed.
     */
    private Decoded frontDoor(byte[] xml) {
        XmlEncodingReport report = XmlBytes.inspect(xml);
        readable(report);
        if (report.consistent() || !report.repairable()) {
            return new Decoded(xml, Optional.empty());
        }
        if (encodingMode == XrEncodingMode.STRICT) {
            throw new XrEncodingException("the bytes of this document are not written in the"
                    + " encoding it declares: " + report.describe(),
                    report.declaration().orElse(null), report.assumed());
        }
        byte[] repaired = XmlBytes.repair(xml, report);
        if (repaired.length > maxInputBytes) {
            throw new XrLimitException("the document is " + repaired.length + " bytes long"
                    + " once recoded into UTF-8, and this importer reads at most "
                    + maxInputBytes);
        }
        return new Decoded(repaired, Optional.of(new ImportNote(
                ImportNote.Kind.ENCODING_REPAIRED, DOCUMENT_LOCATION,
                "the bytes were recoded into UTF-8 before parsing: " + report.describe())));
    }

    /**
     * Refuses a document whose bytes announce an encoding this importer will not read,
     * naming that encoding.
     *
     * <p>Without this the parser is handed bytes it cannot decode and says that they are
     * not well-formed XML — the right exit code for the wrong reason, which sends the
     * reader of the message to look at the markup of a document whose markup is fine. Two
     * cases arrive here: UTF-32, which XML allows and the parser of this platform does not
     * read, and a declaration that names a charset this runtime has never heard of.
     *
     * @param report what the bytes of the document say about their encoding
     * @throws XrFormatException if the encoding is one this importer does not read
     */
    private static void readable(XmlEncodingReport report) {
        String charset = report.unreadable().orElse(null);
        if (charset == null) {
            return;
        }
        if (charset.startsWith("UTF-32")) {
            throw new XrFormatException("the bytes of this document are " + charset
                    + ", and this importer does not read UTF-32: the XML parser of this"
                    + " platform reads UTF-8, UTF-16 and the single-byte encodings, and a"
                    + " document in UTF-32 has to be recoded before it is handed over");
        }
        throw new XrFormatException("this document declares the encoding \"" + charset
                + "\", and no charset of that name is known to this runtime, so its bytes"
                + " were not decoded and its markup was never looked at");
    }

    /**
     * Puts the note of the front door, where the bytes were recoded, in front of the notes
     * about the elements the binding of the source syntax carried nowhere, so that the
     * report stays in the order the observations were made.
     *
     * @param decoded  what the front door made of the bytes
     * @param coverage the notes of {@link XrCoverage} for the source document
     * @return the notes that precede those of the mapper, in that order
     */
    private static List<ImportNote> leading(Decoded decoded, List<ImportNote> coverage) {
        if (decoded.note().isEmpty()) {
            return coverage;
        }
        List<ImportNote> notes = new ArrayList<>(coverage.size() + 1);
        notes.add(decoded.note().get());
        notes.addAll(coverage);
        return notes;
    }

    /**
     * Maps the XR representation into a semantic document, ahead of it the notes about the
     * source document itself.
     *
     * <p>The notes of {@link XrCoverage} come first because they are about elements that
     * never became XR: the mapper cannot have seen them, and a reader of the report should
     * meet the loss of a whole business group before the remarks about the values that did
     * arrive. The note of the front door precedes even those: it is about the bytes.
     */
    private ImportResult collect(XdmNode xrRoot, String provenance, byte[] source,
                                 List<ImportNote> ahead) {
        SemanticDocument.Source origin =
                SemanticDocument.Source.of(provenance, sha256(source));
        DocumentCollector collector =
                new DocumentCollector(SemanticDocument.builder().source(origin));
        ImportReport mapped = XrMapper.map(xrRoot, registry, readerLimits, envelopeBytes(origin),
                splitsNoteSubjectCode(provenance), collector);
        ImportReport report = ahead.isEmpty() ? mapped : merged(ahead, mapped);
        return new ImportResult(collector.document(), report);
    }

    private static ImportReport merged(List<ImportNote> ahead, ImportReport mapped) {
        List<ImportNote> all = new ArrayList<>(ahead);
        all.addAll(mapped.notes());
        return new ImportReport(all);
    }

    /**
     * Returns the bytes the canonical form of the result takes while it holds no value at
     * all. The mapper starts its measurement of the document there and adds what every
     * value costs, so that the size it holds to is the size of the document and not an
     * estimate of it.
     */
    private static long envelopeBytes(SemanticDocument.Source origin) {
        return Canonicalizer.canonicalBytes(
                SemanticDocument.builder().source(origin).build()).length;
    }

    /**
     * Tells whether the note subject code normalization applies to a document of this
     * provenance. It is a property of the UBL syntax binding, so it runs for a document
     * read as UBL and not for one read as CII — nor for one handed in as the XR
     * representation, where the importer cannot know which syntax produced it.
     */
    private boolean splitsNoteSubjectCode(String provenance) {
        return UBL_PROVENANCE.equals(provenance)
                && normalizations.contains(XrNormalization.UBL_NOTE_SUBJECT_CODE);
    }

    private void bound(byte[] xml) {
        Objects.requireNonNull(xml, "xml");
        if (xml.length > maxInputBytes) {
            throw new XrLimitException("the document is " + xml.length + " bytes long, and this"
                    + " importer reads at most " + maxInputBytes);
        }
    }

    private static XrSyntaxException refuse(XdmNode root, String expectation) {
        String namespace = root.getNodeName().getNamespace();
        String localName = root.getNodeName().getLocalName();
        return new XrSyntaxException("this importer was asked to read " + expectation
                + ", and the root element of the document is " + localName
                + (namespace.isEmpty() ? " in no namespace" : " in " + namespace),
                namespace, localName);
    }

    /**
     * Returns the digest that the provenance metadata of the specification, section 4.7
     * records: SHA-256 over the bytes that were read, in lowercase hexadecimal.
     */
    private static String sha256(byte[] source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime implements SHA-256", e);
        }
    }

    /** Holds the combined registry, which is built once and shared. */
    private static final class DefaultRegistry {

        private static final Registry COMBINED =
                Registry.en16931().withExtension(Registry.xrechnungExtension());

        private DefaultRegistry() {
            throw new AssertionError("no instances");
        }
    }
}
