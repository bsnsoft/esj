/**
 * Reads the syntaxes EN 16931-1 binds — UBL 2.1 invoices and credit notes and UN/CEFACT
 * CII D16B invoices — into semantic documents, and writes a semantic document back out as
 * any of the three, driven by the binding tables of {@code model/bindings} and a streaming
 * XML parser.
 *
 * <p>The module of the XSLT bootstrap, {@code esj-xr}, transforms a document into the XR
 * representation and walks the tree that comes out of it. That path is the oracle this
 * one is checked against, and it is not the path for a large document: it builds the
 * source tree, the XR tree and the mapping in memory, so its cost grows with the whole
 * document rather than with one invoice line. This module reads the same two syntaxes
 * with a parser that holds one element at a time and a table that says which business
 * term each element carries, so an invoice of three hundred thousand lines costs what one
 * line costs plus the values it produces.
 *
 * <p>The binding tables are facts: a term identifier, the XPath the syntax writes it at,
 * the XPaths of its supplementary components and the flags that say how the lexical form
 * of the syntax differs from the semantic value. They are generated from the KoSIT SeMoX
 * model; {@code model/bindings/README.md} names the source, the release and every flag.
 *
 * <p>The same tables drive {@link de.bsnsoft.esj.bindings.CiiWriter} and
 * {@link de.bsnsoft.esj.bindings.UblWriter}: an XPath that says where a reader
 * finds a term says where a writer puts it, and the order two sibling elements stand in
 * comes from the schema modules of the validation pack rather than from an opinion of this
 * project. One engine serves both syntaxes, and which of the two UBL documents is written
 * is a fact of the invoice type code BT-3.
 *
 * <p>The comparison of the two readers over the conformance corpus is
 * {@code conformance/readers.md}; what the writers produce over the same corpus, measured
 * against the official validation artefacts and against reading the result back, is
 * {@code conformance/writers/cii-roundtrip.md} and {@code ubl-roundtrip.md}.
 *
 * <h2>A note on the report types</h2>
 *
 * <p>{@link de.bsnsoft.esj.imports.ImportNote},
 * {@link de.bsnsoft.esj.imports.ImportReport} and
 * {@link de.bsnsoft.esj.imports.ImportResult} describe the outcome of reading
 * a document into the semantic model, whichever reader did the reading, and they are
 * declared in {@code esj-core} where every reader of this project reaches them. What this
 * module still takes from {@code esj-xr} is the front door both readers enter through:
 * {@link de.bsnsoft.esj.xr.XmlBytes} and the encoding mode beside it, so that
 * a document whose bytes are not written in the encoding it declares is answered the same
 * way whichever reader was asked.
 */
package de.bsnsoft.esj.bindings;
