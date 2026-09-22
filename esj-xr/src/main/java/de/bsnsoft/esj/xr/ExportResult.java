package de.bsnsoft.esj.xr;

import java.util.Objects;

/**
 * The outcome of an export: the XR document that was written and the report of what did
 * not reach it.
 *
 * <p>The array is the export itself and is not copied, so this record is a carrier rather
 * than a value: two results are equal only where they hold the same array.
 *
 * @param xr     the bytes of the XR document, encoded in UTF-8
 * @param report the observations the exporter made
 */
public record ExportResult(byte[] xr, ExportReport report) {

    /**
     * Checks that neither member is {@code null}.
     *
     * @param xr     the bytes of the XR document, encoded in UTF-8
     * @param report the observations the exporter made
     * @throws NullPointerException if a member is {@code null}
     */
    public ExportResult {
        Objects.requireNonNull(xr, "xr");
        Objects.requireNonNull(report, "report");
    }
}
