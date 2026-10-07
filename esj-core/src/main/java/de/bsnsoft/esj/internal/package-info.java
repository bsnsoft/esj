/**
 * Helpers the modules of this project share: the message escaping of the specification,
 * section 9.5, and the encoding repair of an XML document's bytes.
 *
 * <p>The package is internal: it is public because several modules use it, not because a
 * caller has business with it. It is not part of the API and may change in any release.
 * What a caller needs of it is in the API: the characters a message escapes are
 * {@link de.bsnsoft.esj.Esj#steersATerminal(int)}, and what the bytes of an XML document
 * say about their encoding is {@link de.bsnsoft.esj.xml.XmlEncodingReport#of(byte[])}.
 */
package de.bsnsoft.esj.internal;
