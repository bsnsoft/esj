package de.bsnsoft.esj.xr;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.model.Registry;
import java.util.Objects;

/**
 * Writes an ESJ document as an XR document: the reverse of what
 * {@link XrImporter#fromXr(byte[])} reads.
 *
 * <p>The XR representation is the semantic XML the stylesheets of the KoSIT XRechnung
 * visualization produce — an element per business term or business group, carrying the
 * identifier of EN 16931-1 it stands for in an {@code xr:id} attribute. This exporter
 * writes that representation from the semantic model: the element names, the attributes
 * of the supplementary components and the order of the elements inside a group are the
 * ones the XRechnung semantic model schema of that project defines, and the exporter
 * takes them from a table derived from that schema once, at generation time. No schema is
 * read while a document is written, and this class knows no UBL and no CII: what it knows
 * is the shape of the XR representation and the registry.
 *
 * <p>It is the second half of a round trip. A document that came out of the importer goes
 * back in through {@link XrImporter#fromXr(byte[])} with the same values — for every
 * instance of the conformance corpus and every example of the repository, checked on
 * every build; {@code conformance/README.md} records what the trip cannot carry.
 *
 * <h2>What the XR representation does not carry</h2>
 *
 * <p>Three parts of an ESJ document have no place in it, and two of them are named in the
 * {@link ExportReport}:
 *
 * <ul>
 *   <li>the {@code extensions} subtree, which holds data that belongs to no business term
 *       ({@link ExportNote.Kind#EXTENSIONS_DROPPED});</li>
 *   <li>a value at a path the XR representation has no element for
 *       ({@link ExportNote.Kind#NO_ELEMENT}), a value whose content carries a character
 *       XML 1.0 cannot hold ({@link ExportNote.Kind#NOT_REPRESENTABLE}) and a
 *       supplementary component the element of its term cannot carry
 *       ({@link ExportNote.Kind#COMPONENT_DROPPED});</li>
 *   <li>the {@code source} member, which records where the ESJ document was read from and
 *       is not a statement about the invoice (specification, section 4.7). It is not in
 *       the report, because nothing of the invoice is lost with it: the importer records
 *       a new one, naming the XR document, when it reads the result back.</li>
 * </ul>
 *
 * <p>An empty report therefore means that the XR document carries every value of the ESJ
 * document, and reading it back gives those values again.
 *
 * <h2>The note subject code</h2>
 *
 * <p>{@link XrNormalization#UBL_NOTE_SUBJECT_CODE} needs no reverse here. It exists
 * because the UBL syntax binding writes BT-21 as a prefix inside the note BT-22; the XR
 * representation has an element of its own for BT-21, which is what the importer of a CII
 * document reads, so the exporter writes the two terms into the two elements and the
 * importer of the result reads them back unchanged. A writer of UBL is where the join
 * belongs, and there is none in this release.
 *
 * <h2>Determinism</h2>
 *
 * <p>The same document written twice gives the same bytes. Nothing of the machine, the
 * moment or the run takes part in the result: no timestamp, no producer string, no
 * identifier that counts up and no iteration order that a hash decides.
 *
 * <p>The exporter writes what it is handed and bounds nothing itself. The document is
 * already in memory, and how deep its paths nest is what a reader decided when it read it
 * (specification, section 12.2); the sub invoice line of the XRechnung extension is the one
 * group that nests at all.
 *
 * <p>Instances are immutable and safe to share between threads.
 */
public final class XrExporter {

    private final Registry registry;

    /**
     * Creates an exporter that knows the core model and the XRechnung extension, which is
     * the registry {@link XrImporter#defaultRegistry()} returns.
     */
    public XrExporter() {
        this(XrImporter.defaultRegistry());
    }

    /**
     * Creates an exporter with a registry of its caller's choosing.
     *
     * <p>The registry decides what the exporter can place. A registry without the
     * XRechnung extension writes no element of that extension and names every value below
     * one in the report, which is the right behaviour for a caller that wants an XR
     * document of core terms and nothing else.
     *
     * @param registry the registry that decides the structure
     * @throws NullPointerException if {@code registry} is {@code null}
     */
    public XrExporter(Registry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Returns the registry this exporter asks about structure.
     *
     * @return the registry
     */
    public Registry registry() {
        return registry;
    }

    /**
     * Writes a document as an XR document.
     *
     * @param document the document to write
     * @return the bytes of the XR document, encoded in UTF-8
     * @throws IllegalArgumentException if the document names an edition of the semantic
     *                                  model that the registry of this exporter does not
     *                                  describe, because a semantic path is an address
     *                                  relative to an edition (specification, section 4.4)
     * @throws NullPointerException     if {@code document} is {@code null}
     */
    public byte[] toXr(SemanticDocument document) {
        return toXrWithReport(document).xr();
    }

    /**
     * Writes a document as an XR document and keeps the report.
     *
     * @param document the document to write
     * @return the XR document and the report of what did not reach it
     * @throws IllegalArgumentException if the document names an edition of the semantic
     *                                  model that the registry of this exporter does not
     *                                  describe
     * @throws NullPointerException     if {@code document} is {@code null}
     */
    public ExportResult toXrWithReport(SemanticDocument document) {
        Objects.requireNonNull(document, "document");
        if (!registry.describes(document.semanticModel())) {
            throw new IllegalArgumentException("the document names the semantic model "
                    + document.semanticModel() + " and this exporter writes documents of "
                    + registry.semanticModel());
        }
        return XrEmitter.emit(document, registry);
    }
}
