/**
 * The front door of an XML invoice: which syntax a document is written in, and what its
 * bytes say about their encoding.
 *
 * <p>Both readers of an XML invoice enter through this package — the streaming reader of
 * {@code esj-bindings} and the stylesheet importer of {@code esj-xr} — and so do the syntax
 * engine and the PDF container, which name a syntax with {@link
 * de.bsnsoft.esj.xml.InvoiceSyntax}. Nothing here parses a document: the syntax is
 * recognized from the name of the root element, and the encoding from the byte order mark
 * and the XML declaration. A reader decides with {@link de.bsnsoft.esj.xml.EncodingMode}
 * what it does with bytes that are not written in the encoding they declare, and refuses
 * them with {@link de.bsnsoft.esj.xml.XmlEncodingException}.
 */
package de.bsnsoft.esj.xml;
