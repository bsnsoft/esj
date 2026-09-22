package de.bsnsoft.esj.pdf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Looks at the bytes of a PDF before a parser does, so that a file which only opens
 * because PDFBox repaired it on the way in can be said to have been repaired.
 *
 * <p>The observations are deliberately made here rather than taken from the parser. A
 * parser that recovers a damaged file is doing its job, and it tells its caller about it
 * through a logging framework and not through a return value; reading that back would tie
 * this module to which logging framework PDFBox uses this year. What is checked instead
 * is what the file itself says: whether the offset the trailer gives for the
 * cross-reference data points at cross-reference data, and whether the file ends the way
 * a PDF ends. Both are decided by the bytes and neither needs the parser's opinion.
 */
final class PdfStructure {

    /** How far back from the end of the file the trailer is looked for. */
    private static final int TAIL = 2048;

    private PdfStructure() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns what the bytes of a file say about their own structure.
     *
     * @param pdf the bytes of the file
     * @return the findings, possibly none
     */
    static List<ContainerFinding> inspect(byte[] pdf) {
        List<ContainerFinding> findings = new ArrayList<>();
        String tail = tail(pdf);
        if (!tail.contains("%%EOF")) {
            findings.add(new ContainerFinding(ContainerFinding.Category.PDF_STRUCTURE,
                    "PDF-STRUCTURE-EOF", ContainerFinding.Severity.WARNING,
                    "the file does not end with the end-of-file marker, so it is either"
                            + " truncated or was written by a producer that does not"
                            + " finish a file"));
        }
        long offset = startxref(tail);
        if (offset < 0) {
            findings.add(new ContainerFinding(ContainerFinding.Category.PDF_STRUCTURE,
                    "PDF-STRUCTURE-XREF", ContainerFinding.Severity.WARNING,
                    "the file names no offset for its cross-reference data, so its objects"
                            + " can only be found by scanning it"));
        } else if (!pointsAtCrossReferenceData(pdf, offset)) {
            findings.add(new ContainerFinding(ContainerFinding.Category.PDF_STRUCTURE,
                    "PDF-STRUCTURE-XREF", ContainerFinding.Severity.WARNING,
                    "the offset the file gives for its cross-reference data, " + offset
                            + ", points at neither a cross-reference table nor an object,"
                            + " so the objects were found by scanning the file"));
        }
        return findings;
    }

    /** Returns the last bytes of the file as text, read one byte to one character. */
    private static String tail(byte[] pdf) {
        int from = Math.max(0, pdf.length - TAIL);
        return new String(pdf, from, pdf.length - from, StandardCharsets.ISO_8859_1);
    }

    /**
     * Returns the offset the last {@code startxref} keyword names, or {@code -1} where
     * the file names none or names something that is not a number.
     */
    private static long startxref(String tail) {
        int keyword = tail.lastIndexOf("startxref");
        if (keyword < 0) {
            return -1;
        }
        int i = keyword + "startxref".length();
        while (i < tail.length() && isSpace(tail.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < tail.length() && tail.charAt(i) >= '0' && tail.charAt(i) <= '9') {
            i++;
        }
        if (i == start || i - start > 18) {
            return -1;
        }
        return Long.parseLong(tail.substring(start, i));
    }

    /**
     * Tells whether an offset points at the beginning of a cross-reference section: the
     * keyword {@code xref} of a table, or the {@code N G obj} header of the stream that
     * replaces it.
     */
    private static boolean pointsAtCrossReferenceData(byte[] pdf, long offset) {
        if (offset >= pdf.length) {
            return false;
        }
        int at = (int) offset;
        int length = Math.min(64, pdf.length - at);
        String head = new String(pdf, at, length, StandardCharsets.ISO_8859_1);
        return head.startsWith("xref") || head.matches("(?s)\\d+\\s+\\d+\\s+obj.*");
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\r' || c == '\n' || c == '\t' || c == '\f' || c == '\0';
    }
}
