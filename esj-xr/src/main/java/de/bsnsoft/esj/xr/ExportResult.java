package de.bsnsoft.esj.xr;

import java.util.Objects;

/**
 * The outcome of an export: the XR document that was written and the report of what did
 * not reach it.
 *
 * <p>{@link #xr()} returns a copy of the document on every call, so that a caller cannot
 * change what another caller of the same result reads.
 */
public final class ExportResult {

    private final byte[] xr;
    private final ExportReport report;

    /**
     * Creates a result over an array the exporter wrote and hands over.
     *
     * @param xr     the bytes of the XR document, encoded in UTF-8; not copied
     * @param report the observations the exporter made
     * @throws NullPointerException if a member is {@code null}
     */
    ExportResult(byte[] xr, ExportReport report) {
        this.xr = Objects.requireNonNull(xr, "xr");
        this.report = Objects.requireNonNull(report, "report");
    }

    /**
     * Returns the bytes of the XR document, encoded in UTF-8.
     *
     * @return a fresh copy of the bytes
     */
    public byte[] xr() {
        return xr.clone();
    }

    /**
     * Returns the observations the exporter made.
     *
     * @return the report
     */
    public ExportReport report() {
        return report;
    }

    /**
     * Returns the size of the document and the report, for a message.
     *
     * @return a one-line description
     */
    @Override
    public String toString() {
        return xr.length + " bytes, " + report;
    }
}
