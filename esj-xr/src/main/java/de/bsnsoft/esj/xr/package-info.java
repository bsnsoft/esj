/**
 * Reads invoices written in UBL 2.1 or in UN/CEFACT CII D16B into ESJ documents, and writes
 * an ESJ document back as the semantic XR representation.
 *
 * <p>The module holds no syntax knowledge of its own. A document is first transformed
 * into the semantic XR representation of the KoSIT XRechnung visualization, an XML tree
 * whose elements carry the business term identifier they stand for in an {@code xr:id}
 * attribute. {@link de.bsnsoft.esj.xr.XrImporter} then walks that tree and
 * asks the registry of {@code esj-core} what each identifier is: a group or a term,
 * repeatable or not, which semantic data type it carries and which supplementary
 * components it allows. Nothing in this package knows what a {@code cbc:IssueDate} or a
 * {@code ram:TypeCode} is.
 *
 * <p>Unlike {@code esj-core}, this module depends on Saxon-HE, because the stylesheets
 * are written in XSLT 2.0.
 *
 * <p>The importer never fails on a construct it cannot place. It maps what it can, and
 * describes the rest in an {@link de.bsnsoft.esj.imports.ImportReport} that
 * travels beside the document.
 *
 * <p>{@link de.bsnsoft.esj.xr.XrExporter} goes the other way: it writes a
 * document as an XR document, with the element names, the attributes and the element order
 * of the XRechnung semantic model schema of the same project, taken from a table derived
 * from that schema at generation time. It says as little about a syntax as the importer
 * does, it leaves nothing out silently — an {@link de.bsnsoft.esj.xr.ExportReport}
 * names what the XR representation has no place for — and it writes the same document twice
 * as the same bytes.
 */
package de.bsnsoft.esj.xr;
