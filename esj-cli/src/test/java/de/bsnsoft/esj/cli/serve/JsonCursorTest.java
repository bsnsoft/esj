package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The cursor the server reads a child's answer and a client's message with: JSON as RFC 8259
 * has it, a string read only as far as it is asked for, and the rest of it read past.
 */
class JsonCursorTest {

    @Test
    void itWalksEveryKindOfToken() throws IOException {
        JsonCursor cursor = cursor("{\"a\": [1, -2.5e3, true, false, null, \"x\"], \"b\": {}}");
        List<String> tokens = new ArrayList<>();
        JsonCursor.Token token;
        while ((token = cursor.next()) != JsonCursor.Token.END) {
            tokens.add(switch (token) {
                case NAME -> "name:" + cursor.name();
                case NUMBER -> "number:" + cursor.number();
                case STRING -> "string:" + cursor.text(10).prefix();
                default -> token.name();
            });
        }
        assertEquals(List.of("BEGIN_OBJECT", "name:a", "BEGIN_ARRAY", "number:1",
                "number:-2.5e3", "TRUE", "FALSE", "NULL", "string:x", "END_ARRAY", "name:b",
                "BEGIN_OBJECT", "END_OBJECT", "END_OBJECT"), tokens);
    }

    @Test
    void aStringIsReadAsFarAsItIsAskedForAndMeasuredWhole() throws IOException {
        String text = "Grüße \\u202E\\uD83D\\uDE00 \\\"quoted\\\" \\\\ \\/ \\n\\t end";
        JsonCursor cursor = cursor("\"" + text + "\"");
        assertEquals(JsonCursor.Token.STRING, cursor.next());
        JsonCursor.Text whole = cursor.text(1000);
        String expected = "Grüße \u202E\uD83D\uDE00 \"quoted\" \\ / \n\t end";
        assertEquals(expected, whole.prefix());
        assertTrue(whole.complete());
        assertEquals(expected.length(), whole.length());

        JsonCursor cut = cursor("\"" + text + "\"");
        cut.next();
        JsonCursor.Text prefix = cut.text(8);
        // Seven characters fit before the pair; the pair would be the eighth and ninth unit.
        assertEquals("Grüße \u202E", prefix.prefix());
        assertEquals(expected.length(), prefix.length());
        assertFalse(prefix.complete());
        assertEquals("Grüße \u202E … (" + (expected.length() - 7) + " more characters)",
                prefix.cut());
        assertEquals(JsonCursor.Token.END, cut.next());
    }

    @Test
    void aStringNobodyAsksForIsReadPast() throws IOException {
        StringBuilder long_ = new StringBuilder("{\"skip\": \"");
        long_.append("x".repeat(3 * 1024 * 1024)).append("\", \"keep\": \"y\", \"nested\": ")
                .append("{\"a\": [\"").append("z".repeat(100_000)).append("\"]}}");
        JsonCursor cursor = cursor(long_.toString());
        cursor.next();
        assertEquals("skip", nextName(cursor));
        cursor.next();
        assertEquals("keep", nextName(cursor));
        cursor.next();
        assertEquals("y", cursor.text(10).prefix());
        assertEquals("nested", nextName(cursor));
        cursor.next();
        cursor.skip();
        assertEquals(JsonCursor.Token.END_OBJECT, cursor.next());
        assertEquals(JsonCursor.Token.END, cursor.next());
    }

    @Test
    void aStringStreamsToAFileWithinItsBound() throws IOException {
        JsonCursor cursor = cursor("\"ab\\u00e4" + "c".repeat(10) + "\"");
        cursor.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertEquals(13, cursor.textTo(out, 5));
        assertEquals("abäcc", out.toString(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "[1,]", "{\"a\":1,}", "{\"a\" 1}", "{a:1}", "01", "1.",
        "-", "1e", "tru", "nul", "\"a", "\"\\x\"", "\"\\u12G4\"", "[1 2]", "{} {}", "{\"a\":1}x",
        "'a'", "[\"\u0001\"]"})
    void whatIsNotJsonIsRefused(String text) {
        assertThrows(JsonCursor.Malformed.class, () -> walk(cursor(text)), text);
    }

    @Test
    void invalidUtf8IsRefused() {
        for (byte[] bytes : List.of(new byte[] {'"', (byte) 0xC3, '"'},
                new byte[] {'"', (byte) 0xC0, (byte) 0xAF, '"'},
                new byte[] {'"', (byte) 0xED, (byte) 0xA0, (byte) 0x80, '"'},
                new byte[] {'"', (byte) 0xFF, '"'})) {
            assertThrows(JsonCursor.Malformed.class, () -> walk(new JsonCursor(
                    new ByteArrayInputStream(bytes))));
        }
    }

    @Test
    void aTextNestedDeeperThanTheBoundIsRefused() throws IOException {
        String deep = "[".repeat(JsonCursor.MAX_DEPTH) + "]".repeat(JsonCursor.MAX_DEPTH);
        walk(cursor(deep));
        String deeper = "[" + deep + "]";
        assertThrows(JsonCursor.Malformed.class, () -> walk(cursor(deeper)));
    }

    @Test
    void namesAndNumbersPastTheirBoundsAreRefused() {
        assertThrows(JsonCursor.Malformed.class, () -> walk(cursor("{\""
                + "n".repeat(JsonCursor.MAX_NAME + 1) + "\": 1}")));
        assertThrows(JsonCursor.Malformed.class, () -> walk(cursor(
                "1".repeat(JsonCursor.MAX_NUMBER + 1))));
    }

    private static String nextName(JsonCursor cursor) throws IOException {
        assertEquals(JsonCursor.Token.NAME, cursor.next());
        return cursor.name();
    }

    private static void walk(JsonCursor cursor) throws IOException {
        while (cursor.next() != JsonCursor.Token.END) {
            // Every token, every string read past.
        }
    }

    private static JsonCursor cursor(String text) {
        InputStream in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        return new JsonCursor(in);
    }
}
