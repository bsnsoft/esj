package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * MCP conversations, written down as data in {@code serve/conversations.json} and held
 * against both transports: every message is sent over Streamable HTTP and over the standard
 * streams, and both have to answer it as the file says.
 *
 * <p>In the file, {@code "$doc": "<file>"} among the arguments of a call stands for the
 * document, which the HTTP transport names by the id of an upload and the stdio transport
 * by its path. In an expectation, {@code "$any"} matches anything, {@code "$absent"} a member
 * that is not there, {@code "$contains:<text>"} a string that contains the text, and
 * {@code "$more"} as the last item of an array the items beyond those listed. An expectation
 * of {@code null} is a message that is not answered. A step may expect one thing of the HTTP
 * transport ({@code expectHttp}) and another of the stdio transport ({@code expectStdio}).
 */
class McpConversationTest {

    /** The file of the conversations. */
    private static final String CONVERSATIONS = "serve/conversations.json";

    @TempDir
    private Path temp;

    static Stream<Arguments> conversations() {
        List<Arguments> all = new ArrayList<>();
        Jv.Arr file = (Jv.Arr) Jv.parse(ServeFixture.bytes(CONVERSATIONS), Integer.MAX_VALUE);
        for (Jv conversation : file.items()) {
            Jv.Obj object = (Jv.Obj) conversation;
            for (String transport : List.of("http", "stdio")) {
                all.add(Arguments.of(object.string("name").orElseThrow(), transport, object));
            }
        }
        return all.stream();
    }

    /**
     * Holds one conversation against one transport.
     *
     * @param name         the name of the conversation
     * @param transport    {@code http} or {@code stdio}
     * @param conversation the conversation
     */
    @ParameterizedTest(name = "{1}: {0}")
    @MethodSource("conversations")
    void theServerAnswersAsTheConversationSays(String name, String transport,
                                               Jv.Obj conversation) throws Exception {
        List<Jv> steps = ((Jv.Arr) conversation.get("steps").orElseThrow()).items();
        if ("http".equals(transport)) {
            http(steps);
        } else {
            stdio(steps);
        }
    }

    private void http(List<Jv> steps) throws Exception {
        try (Http server = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            Map<String, String> uploads = new LinkedHashMap<>();
            String protocol = null;
            for (Jv step : steps) {
                Jv.Obj object = (Jv.Obj) step;
                byte[] body = object.string("sendRaw")
                        .map(raw -> raw.getBytes(StandardCharsets.UTF_8))
                        .orElseGet(() -> documents(object.get("send").orElseThrow(), name -> {
                            return Jv.object().put(Tools.DOCUMENT, uploads.computeIfAbsent(name,
                                    file -> upload(server, file))).build();
                        }).toBytes());
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.url()
                                + "/mcp"))
                        .timeout(Duration.ofMinutes(2))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body));
                if (protocol != null) {
                    request.header("MCP-Protocol-Version", protocol);
                }
                HttpResponse<byte[]> response = ServeFixture.send(request.build());
                Jv expected = expectation(object, "expectHttp");
                if (expected instanceof Jv.Null) {
                    assertEquals(202, response.statusCode(), "a message that is not answered");
                    assertEquals(0, response.body().length);
                    continue;
                }
                assertTrue(response.statusCode() == 200 || response.statusCode() == 400,
                        "status " + response.statusCode());
                Jv actual = Jv.parse(response.body(), Integer.MAX_VALUE);
                match(expected, actual, "$");
                if (actual instanceof Jv.Obj answer && answer.get("result").orElse(null)
                        instanceof Jv.Obj result && result.string("protocolVersion").isPresent()) {
                    protocol = result.string("protocolVersion").get();
                }
            }
        }
    }

    private void stdio(List<Jv> steps) {
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        List<Jv> expected = new ArrayList<>();
        for (Jv step : steps) {
            Jv.Obj object = (Jv.Obj) step;
            byte[] line = object.string("sendRaw")
                    .map(raw -> raw.getBytes(StandardCharsets.UTF_8))
                    .orElseGet(() -> documents(object.get("send").orElseThrow(), name ->
                            Jv.object().put(Tools.PATH, ServeFixture.file(name).toString())
                                    .build()).toBytes());
            input.writeBytes(line);
            input.write('\n');
            Jv expectation = expectation(object, "expectStdio");
            if (!(expectation instanceof Jv.Null)) {
                expected.add(expectation);
            }
        }
        List<Jv> answers = new ArrayList<>();
        int code = Stdio.run(ServeFixture.config(temp, ServeFixture.log()),
                new ByteArrayInputStream(input.toByteArray()),
                line -> {
                    synchronized (answers) {
                        answers.add(Jv.parse(line, Integer.MAX_VALUE));
                    }
                });
        assertEquals(0, code);
        assertEquals(expected.size(), answers.size(), "one answer per request: " + answers);
        // An answer without an id answers a message that could not be read; those come in the
        // order of the messages, every other answer is found by its id.
        List<Jv> anonymous = answers.stream()
                .filter(answer -> ((Jv.Obj) answer).get("id").orElse(Jv.NULL) instanceof Jv.Null)
                .toList();
        int next = 0;
        for (Jv expectation : expected) {
            Jv id = ((Jv.Obj) expectation).get("id").orElseThrow();
            if (id instanceof Jv.Null) {
                match(expectation, anonymous.get(next++), "$");
                continue;
            }
            List<Jv> withId = answers.stream().filter(answer -> sameId(
                    ((Jv.Obj) answer).get("id").orElse(Jv.NULL), id)).toList();
            assertEquals(1, withId.size(), "one answer with the id " + text(id) + ": " + answers);
            match(expectation, withId.get(0), "$");
        }
    }

    @Test
    void theHttpTransportRefusesWhatStreamableHttpDoesNotCarry() throws Exception {
        try (Http server = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            String url = server.url() + "/mcp";
            assertEquals(405, ServeFixture.get(url).statusCode(), "no server-initiated stream");
            HttpResponse<byte[]> delete = ServeFixture.send(HttpRequest.newBuilder(URI.create(url))
                    .DELETE().build());
            assertEquals(405, delete.statusCode(), "no session to end");
            byte[] ping = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"
                    .getBytes(StandardCharsets.UTF_8);
            assertEquals(415, ServeFixture.post(url, ping, "Content-Type", "text/plain")
                    .statusCode());
            HttpResponse<byte[]> modern = ServeFixture.post(url, ping, "Content-Type",
                    "application/json", "MCP-Protocol-Version", "2026-07-28");
            assertEquals(400, modern.statusCode(), "a revision this server does not speak");
            assertEquals("-32600", ((Jv.Num) ((Jv.Obj) ServeFixture.json(modern).get("error")
                    .orElseThrow()).get("code").orElseThrow()).text(),
                    "not the error of 2026-07-28, so that a client of both eras falls back to"
                            + " initialize");
            HttpResponse<byte[]> answered = ServeFixture.post(url, ping, "Content-Type",
                    "application/json");
            assertEquals(200, answered.statusCode());
            assertTrue(answered.headers().firstValue("Mcp-Session-Id").isEmpty(),
                    "the server issues no session");
            byte[] batch = ("[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"},"
                    + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"},"
                    + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}]")
                    .getBytes(StandardCharsets.UTF_8);
            HttpResponse<byte[]> batched = ServeFixture.post(url, batch, "Content-Type",
                    "application/json", "MCP-Protocol-Version", "2025-03-26");
            assertEquals(200, batched.statusCode());
            assertEquals(2, ((Jv.Arr) Jv.parse(batched.body(), Integer.MAX_VALUE)).items()
                    .size(), "2025-03-26 takes a batch");
            assertEquals(400, ServeFixture.post(url, batch, "Content-Type", "application/json",
                    "MCP-Protocol-Version", "2025-06-18").statusCode(), "2025-06-18 does not");
            assertEquals(202, ServeFixture.post(url, ("[{\"jsonrpc\":\"2.0\",\"method\":"
                    + "\"notifications/initialized\"}]").getBytes(StandardCharsets.UTF_8),
                    "Content-Type", "application/json").statusCode());
            // A batch is answered once its last message is: it carries at most one call of a
            // tool and Mcp.BATCH messages, so that what it holds stays that of one call.
            String call = "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"upload\",\"arguments\":{\"content_base64\":\"QUJD\"}}}";
            HttpResponse<byte[]> twoCalls = ServeFixture.post(url, ("[" + String.format(call, 1)
                    + "," + String.format(call, 2) + "]").getBytes(StandardCharsets.UTF_8),
                    "Content-Type", "application/json");
            assertEquals(400, twoCalls.statusCode());
            assertTrue(new String(twoCalls.body(), StandardCharsets.UTF_8)
                    .contains("at most one tools/call"));
            String pings = String.join(",", java.util.Collections.nCopies(Mcp.BATCH + 1,
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"));
            assertEquals(400, ServeFixture.post(url, ("[" + pings + "]").getBytes(
                    StandardCharsets.UTF_8), "Content-Type", "application/json").statusCode());
            HttpResponse<byte[]> oneCall = ServeFixture.post(url, ("[" + String.format(call, 1)
                    + ",{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}]")
                    .getBytes(StandardCharsets.UTF_8), "Content-Type", "application/json");
            assertEquals(200, oneCall.statusCode());
            List<Jv> answers = ((Jv.Arr) Jv.parse(oneCall.body(), Integer.MAX_VALUE)).items();
            assertEquals(2, answers.size());
            assertTrue(((Jv.Obj) answers.get(0)).get("result").isPresent(), answers.toString());
        }
    }

    @Test
    void aBatchOverTheStandardStreamsCarriesAtMostOneCall() {
        Path invoice = ServeFixture.file("examples/standard-invoice.esj.json");
        String call = "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"summary\",\"arguments\":{\"path\":\"" + invoice + "\"}}}";
        String input = "[" + String.format(call, 1) + "," + String.format(call, 2) + "]\n"
                + "[" + String.format(call, 3) + ",{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":"
                + "\"ping\"}]\n";
        List<Jv> answers = new ArrayList<>();
        assertEquals(0, Stdio.run(ServeFixture.config(temp, ServeFixture.log()),
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                line -> answers.add(Jv.parse(line, Integer.MAX_VALUE))));
        assertEquals(2, answers.size(), answers.toString());
        Jv.Obj refused = (Jv.Obj) answers.get(0);
        assertEquals("-32600", ((Jv.Num) ((Jv.Obj) refused.get("error").orElseThrow())
                .get("code").orElseThrow()).text());
        List<Jv> batch = ((Jv.Arr) answers.get(1)).items();
        assertEquals(2, batch.size());
        assertTrue(new String(batch.get(0).toBytes(), StandardCharsets.UTF_8)
                .contains("RE-2026-0042"), batch.toString());
    }

    @Test
    void aResultOverHttpLinksTheFileItWrote() throws Exception {
        try (Http server = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            String id = upload(server, "examples/standard-invoice.esj.json");
            byte[] call = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"render\",\"arguments\":{\"document\":\"" + id + "\"}}}")
                    .getBytes(StandardCharsets.UTF_8);
            Jv.Obj result = (Jv.Obj) ServeFixture.json(ServeFixture.post(server.url() + "/mcp",
                    call, "Content-Type", "application/json", "MCP-Protocol-Version",
                    "2025-11-25")).get("result").orElseThrow();
            List<Jv> content = ((Jv.Arr) result.get("content").orElseThrow()).items();
            Jv.Obj link = (Jv.Obj) content.get(content.size() - 1);
            assertEquals("resource_link", link.string("type").orElseThrow());
            assertEquals("application/pdf", link.string("mimeType").orElseThrow());
            String uri = link.string("uri").orElseThrow();
            String first = ((Jv.Obj) content.get(0)).string("text").orElseThrow();
            assertTrue(first.contains(uri),
                    "the link stands in the text as well, for a client that drops links");
            HttpResponse<byte[]> pdf = ServeFixture.get(uri);
            assertEquals(200, pdf.statusCode());
            assertEquals("%PDF", new String(pdf.body(), 0, 4, StandardCharsets.US_ASCII));
        }
    }

    /** Returns what a step expects of one transport: its own expectation, or the common one. */
    private static Jv expectation(Jv.Obj step, String transport) {
        return step.get(transport).or(() -> step.get("expect")).orElseThrow();
    }

    private static String upload(Http server, String file) {
        HttpResponse<byte[]> response = ServeFixture.post(server.url() + "/api/documents",
                ServeFixture.bytes(file));
        assertEquals(201, response.statusCode());
        return ServeFixture.json(response).string("id").orElseThrow();
    }

    /** Replaces every {@code "$doc"} member by what a transport names the document with. */
    private static Jv documents(Jv value, java.util.function.Function<String, Jv.Obj> door) {
        if (value instanceof Jv.Obj object) {
            Jv.Builder builder = Jv.object();
            for (Map.Entry<String, Jv> member : object.members().entrySet()) {
                if ("$doc".equals(member.getKey())) {
                    door.apply(((Jv.Str) member.getValue()).value()).members()
                            .forEach(builder::put);
                } else {
                    builder.put(member.getKey(), documents(member.getValue(), door));
                }
            }
            return builder.build();
        }
        if (value instanceof Jv.Arr array) {
            List<Jv> items = new ArrayList<>();
            array.items().forEach(item -> items.add(documents(item, door)));
            return Jv.array(items);
        }
        return value;
    }

    /** Matches an answer against an expectation, as the class comment describes. */
    static void match(Jv expected, Jv actual, String where) {
        if (expected instanceof Jv.Str str) {
            String text = str.value();
            if ("$any".equals(text)) {
                return;
            }
            if (text.startsWith("$contains:")) {
                assertTrue(actual instanceof Jv.Str found
                                && found.value().contains(text.substring("$contains:".length())),
                        where + ": expected a text containing '" + text.substring(10)
                                + "', found " + text(actual));
                return;
            }
        }
        if (expected instanceof Jv.Obj object) {
            if (!(actual instanceof Jv.Obj found)) {
                fail(where + ": expected an object, found " + text(actual));
                return;
            }
            for (Map.Entry<String, Jv> member : object.members().entrySet()) {
                Optional<Jv> value = found.get(member.getKey());
                if (member.getValue() instanceof Jv.Str str && "$absent".equals(str.value())) {
                    assertTrue(value.isEmpty(), where + "." + member.getKey() + " is absent");
                    continue;
                }
                assertTrue(value.isPresent(), where + "." + member.getKey() + " is missing in "
                        + text(actual));
                match(member.getValue(), value.get(), where + "." + member.getKey());
            }
            return;
        }
        if (expected instanceof Jv.Arr array) {
            if (!(actual instanceof Jv.Arr found)) {
                fail(where + ": expected an array, found " + text(actual));
                return;
            }
            List<Jv> items = array.items();
            boolean more = !items.isEmpty() && items.get(items.size() - 1) instanceof Jv.Str last
                    && "$more".equals(last.value());
            int count = more ? items.size() - 1 : items.size();
            if (more) {
                assertTrue(found.items().size() >= count, where + ": at least " + count
                        + " items in " + text(actual));
            } else {
                assertEquals(count, found.items().size(), where + ": " + text(actual));
            }
            for (int i = 0; i < count; i++) {
                match(items.get(i), found.items().get(i), where + "[" + i + "]");
            }
            return;
        }
        if (expected instanceof Jv.Num number && actual instanceof Jv.Num found) {
            assertEquals(0, new BigDecimal(number.text()).compareTo(new BigDecimal(found.text())),
                    where);
            return;
        }
        assertEquals(text(expected), text(actual), where);
    }

    private static boolean sameId(Jv a, Jv b) {
        if (a instanceof Jv.Num x && b instanceof Jv.Num y) {
            return new BigDecimal(x.text()).compareTo(new BigDecimal(y.text())) == 0;
        }
        return text(a).equals(text(b));
    }

    private static String text(Jv value) {
        return new String(value.toBytes(), StandardCharsets.UTF_8);
    }
}
