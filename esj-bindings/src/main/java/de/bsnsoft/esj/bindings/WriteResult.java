package de.bsnsoft.esj.bindings;

import java.util.Objects;

/**
 * The outcome of writing a semantic document into an XML syntax: the bytes and the report
 * of what did not reach them.
 *
 * <p>The two belong together. A caller that takes the bytes and throws the report away has
 * a document that may be missing something nobody will notice again, which is why the
 * report is not an optional extra of a second method but the other half of one result.
 *
 * <p>This is a class rather than a record because it carries bytes: a record would compare
 * two results by the identity of their arrays, which is a comparison nobody wants and
 * everybody would assume to be a comparison of the documents.
 */
public final class WriteResult {

    private final byte[] xml;
    private final WriteReport report;

    /**
     * Creates a result.
     *
     * @param xml    the document, encoded in UTF-8
     * @param report what became of the semantic document on the way in
     * @throws NullPointerException if an argument is {@code null}
     */
    public WriteResult(byte[] xml, WriteReport report) {
        this.xml = Objects.requireNonNull(xml, "xml").clone();
        this.report = Objects.requireNonNull(report, "report");
    }

    /**
     * Returns the document.
     *
     * @return a copy of the bytes, encoded in UTF-8
     */
    public byte[] xml() {
        return xml.clone();
    }

    /**
     * Returns the report.
     *
     * @return what became of the semantic document on the way in
     */
    public WriteReport report() {
        return report;
    }

    /**
     * Returns the length and the report, which is what a message about a result needs.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return xml.length + " bytes, " + report;
    }
}
