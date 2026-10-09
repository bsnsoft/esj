package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a result keeps of a child's answer: the verdict and the heaviest findings of a report
 * of any length, the values asked for of a document of any size, and nothing of either beyond
 * the bounds of {@link Results} — a value past them is named and not carried, a list past
 * them is cut and says so.
 */
class ResultsTest {

    @Test
    void aReportWithManyFindingsKeepsTheHeaviestAndCountsTheRest() throws IOException {
        StringBuilder rules = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            if (i > 0) {
                rules.append(',');
            }
            String severity = i % 3 == 0 ? "warning" : "information";
            if (i == 250) {
                severity = "fatal";
            }
            rules.append("{\"code\":\"R-").append(i).append("\",\"severity\":\"").append(severity)
                    .append("\",\"path\":\"/BG-25/").append(i).append("/BT-131\",\"message\":\"")
                    .append("m".repeat(i == 250 ? 5000 : 10)).append("\",\"other\":\"")
                    .append("o".repeat(2000)).append("\"}");
        }
        String report = "{\"verdict\":\"INVALID\",\"detected\":\"esj\",\"semanticModel\":\"x\","
                + "\"container\":null,\"rules\":{\"findings\":[" + rules + "]},"
                + "\"layers\":{\"l2\":{\"findings\":[{\"code\":\"L2\",\"severity\":\"warning\","
                + "\"paths\":[" + String.join(",", Collections.nCopies(40, "\"/BT-1\"")) + "],"
                + "\"message\":\"m\"}]}},\"reasons\":[],\"notChecked\":[]}";
        Outcome outcome = Results.validate(stream(report), 1);
        Jv.Obj result = outcome.structured();
        assertEquals("INVALID", result.string("verdict").orElseThrow());
        assertEquals("301", ((Jv.Num) result.get("findingsTotal").orElseThrow()).text());
        List<Jv> findings = ((Jv.Arr) result.get("findings").orElseThrow()).items();
        assertEquals(Results.FINDINGS, findings.size());
        Jv.Obj heaviest = (Jv.Obj) findings.get(0);
        assertEquals("R-250", heaviest.string("code").orElseThrow());
        assertTrue(heaviest.string("message").orElseThrow().endsWith(" … (4600 more characters)"),
                "a long message is cut and says so");
        // The warnings come next: the one of the model layer first, as its check is listed
        // before the rules, and then those of the rules in the order of the report.
        Jv.Obj second = (Jv.Obj) findings.get(1);
        assertEquals("L2", second.string("code").orElseThrow());
        assertEquals("16", ((Jv.Arr) second.get("paths").orElseThrow()).items().size() + "");
        assertEquals("40", ((Jv.Num) second.get("pathsTotal").orElseThrow()).text());
        assertEquals("R-0", ((Jv.Obj) findings.get(2)).string("code").orElseThrow());
        assertEquals("R-3", ((Jv.Obj) findings.get(3)).string("code").orElseThrow());
        assertEquals(new Jv.Bool(false), result.get("complete").orElseThrow());
        Jv.Obj counts = (Jv.Obj) result.get("findingCounts").orElseThrow();
        assertEquals(List.of("fatal", "warning", "information"),
                new ArrayList<>(counts.members().keySet()));
        assertTrue(outcome.text().contains("296 more findings; the structured result lists the"
                + " 20 heaviest"), outcome.text());
    }

    @Test
    void aSmallReportIsCompleteAndSaysNothingAboutIt() throws IOException {
        Outcome outcome = Results.validate(stream("{\"verdict\":\"VALID\",\"container\":"
                + "{\"ok\":true,\"kind\":\"pdf\",\"findings\":[]},\"reasons\":[]}"), 0);
        assertTrue(outcome.structured().get("complete").isEmpty());
        assertEquals(new Jv.Bool(true), ((Jv.Obj) outcome.structured().get("container")
                .orElseThrow()).get("ok").orElseThrow());
    }

    @Test
    void aValueLongerThanAResultCarriesIsNamedAndNotCarried() throws IOException {
        String note = "n".repeat(Results.VALUE + 1);
        String document = "{\"format\":\"EN16931-Semantic-JSON\",\"version\":\"0.1\","
                + "\"semanticModel\":\"EN16931-1:2017+A1:2019/AC:2020\",\"values\":{"
                + "\"/BT-1\":\"" + "1".repeat(Results.VALUE + 5) + "\",\"/BT-2\":\"2026-01-01\","
                + "\"/BG-1/0/BT-22\":\"" + note + "\",\"/BG-4/BT-27\":{\"value\":\"Seller\"}}}";
        Jv.Obj summary = Results.summary(stream(document)).structured();
        assertEquals(Jv.NULL, summary.get("invoiceNumber").orElseThrow());
        assertEquals("2026-01-01", summary.string("issueDate").orElseThrow());
        assertEquals("Seller", summary.string("seller").orElseThrow());
        Jv.Obj omitted = (Jv.Obj) ((Jv.Arr) summary.get("omitted").orElseThrow()).items().get(0);
        assertEquals("/BT-1", omitted.string("path").orElseThrow());
        assertEquals(Long.toString(Results.VALUE + 5L),
                ((Jv.Num) omitted.get("characters").orElseThrow()).text());

        Outcome get = Results.get(stream(document), List.of("/BG-1", "/BT-2", "/BT-99"));
        Jv.Obj values = (Jv.Obj) get.structured().get("values").orElseThrow();
        assertEquals(List.of("/BT-2"), new ArrayList<>(values.members().keySet()));
        assertEquals(List.of(Jv.of("/BT-99")),
                ((Jv.Arr) get.structured().get("missing").orElseThrow()).items());
        assertTrue(get.text().contains("/BG-1/0/BT-22: a value of " + (Results.VALUE + 1)
                + " characters, longer than a result carries"), get.text());
    }

    @Test
    void aDocumentOfAnySizeCostsTheValuesAskedForAndNoMore() throws IOException {
        // About a million values, streamed: a result keeps the first VALUES of those asked for
        // and counts the lines of the whole document.
        int lines = 200_000;
        InputStream document = new SequenceInputStream(stream("{\"semanticModel\":\"m\","
                + "\"values\":{\"/BT-1\":\"RE-1\""), new java.io.InputStream() {
                    private int line;
                    private byte[] chunk = new byte[0];
                    private int at;

                    @Override
                    public int read() {
                        if (at == chunk.length) {
                            if (line == lines) {
                                return -1;
                            }
                            String p = ",\"/BG-25/" + line + "/";
                            chunk = (p + "BT-126\":\"" + line + "\"" + p + "BT-129\":\"1\"" + p
                                    + "BT-131\":\"" + "9".repeat(200) + "\"" + p + "BG-31/BT-153\""
                                    + ":\"Item\"" + p + "BG-31/BT-154\":\"d\""
                                    + (line == lines - 1 ? "}}" : ""))
                                    .getBytes(StandardCharsets.UTF_8);
                            at = 0;
                            line++;
                        }
                        return chunk[at++] & 0xFF;
                    }
                });
        Outcome summary = Results.summary(document);
        assertEquals("200000", ((Jv.Num) summary.structured().get("lines").orElseThrow())
                .text());
        assertEquals("1000001", ((Jv.Num) summary.structured().get("values").orElseThrow())
                .text());
        assertEquals("RE-1", summary.structured().string("invoiceNumber").orElseThrow());
    }

    @Test
    void aGetPastItsBoundsIsCutAndSaysSo() throws IOException {
        StringBuilder values = new StringBuilder("{\"values\":{");
        for (int i = 0; i < 2 * Results.VALUES; i++) {
            values.append(i == 0 ? "" : ",").append("\"/BG-25/").append(i)
                    .append("/BT-131\":\"").append(i).append('"');
        }
        values.append("}}");
        Jv.Obj many = Results.get(stream(values.toString()), List.of("/BG-25/*/BT-131"))
                .structured();
        assertEquals(Results.VALUES, ((Jv.Obj) many.get("values").orElseThrow()).members()
                .size());
        assertEquals(new Jv.Bool(true), many.get("truncated").orElseThrow());

        StringBuilder large = new StringBuilder("{\"values\":{");
        String value = "v".repeat(Results.VALUE - 100);
        int count = Results.KEPT / value.length() + 3;
        for (int i = 0; i < count; i++) {
            large.append(i == 0 ? "" : ",").append("\"/BG-1/").append(i).append("/BT-22\":\"")
                    .append(value).append('"');
        }
        large.append("}}");
        Jv.Obj kept = Results.get(stream(large.toString()), List.of("/BG-1")).structured();
        int carried = ((Jv.Obj) kept.get("values").orElseThrow()).members().size();
        assertTrue(carried < count && carried >= Results.KEPT / Results.VALUE,
                "values until the room of a result is spent: " + carried);
        assertEquals(new Jv.Bool(true), kept.get("truncated").orElseThrow());
        assertFalse(kept.get("omitted").isPresent(), "none was too long by itself");
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
