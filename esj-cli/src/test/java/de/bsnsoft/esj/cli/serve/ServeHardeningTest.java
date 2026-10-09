package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The findings of the security review of {@code esj serve} and {@code esj mcp}, held: links
 * that a {@code Host} header cannot point elsewhere, a hard link that does not lead out of
 * {@code --allow-dir}, texts in which a model reads no character it cannot see, an upload
 * over MCP that does not pass through the heap, a cancellation that ends its child, and a
 * validation whose result had to be cut that hands over the complete report.
 */
class ServeHardeningTest {

    /** A document of the repository that every child reads. */
    private static final String INVOICE = "examples/standard-invoice.esj.json";

    /** What an artefact link is without a known address: the path on the server. */
    private static final Pattern RELATIVE = Pattern.compile("/api/artifacts/[0-9a-f]{32}");

    @TempDir
    private Path temp;

    // ---------------------------------------------------------------------------------
    // serve-2: links

    @Test
    void aForeignHostGivesLinksThatArePathsOnTheServer() throws Exception {
        byte[] document = ServeFixture.bytes(INVOICE);
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withHosts(List.of("esj-api:8080"))
                .withAccess(List.of("https://chat.example.com"), List.of(), Optional.empty(),
                        List.of(), Optional.empty());
        try (Http http = Http.start(config)) {
            int port = http.port();
            for (String host : List.of("evil.example", "attacker.test:9999",
                    "localhost:" + (port == 1 ? 2 : port - 1), "esj-api:8081", "")) {
                Raw rendered = raw(port, "POST", "/api/render?format=html", host, document);
                assertEquals(200, rendered.status(), rendered.body());
                String url = firstUrl(rendered);
                assertTrue(RELATIVE.matcher(url).matches(), host + ": " + url);
                assertEquals("<" + url + ">", rendered.header("Link").substring(0,
                        url.length() + 2), host);
                Jv.Obj self = ServeFixture.json(raw(port, "GET", "/", host, null).bytes());
                assertEquals("/openapi.json", self.string("openapi").orElseThrow(), host);
            }
            Map<String, String> known = Map.of(
                    "127.0.0.1:" + port, "http://127.0.0.1:" + port,
                    "localhost:" + port, "http://localhost:" + port,
                    "[::1]:" + port, "http://[::1]:" + port,
                    "esj-api:8080", "http://esj-api:8080",
                    "ESJ-API:8080", "http://ESJ-API:8080",
                    "chat.example.com", "https://chat.example.com");
            for (Map.Entry<String, String> host : known.entrySet()) {
                Raw rendered = raw(port, "POST", "/api/render?format=html", host.getKey(),
                        document);
                String url = firstUrl(rendered);
                assertTrue(url.startsWith(host.getValue() + "/api/artifacts/"),
                        host.getKey() + ": " + url);
            }
        }
        ServeConfig published = config.withAccess(List.of(), List.of(), Optional.empty(),
                List.of(), Optional.of("https://esj.example.org/"));
        try (Http http = Http.start(published)) {
            String url = firstUrl(raw(http.port(), "POST", "/api/render?format=html",
                    "evil.example", document));
            assertTrue(url.startsWith("https://esj.example.org/api/artifacts/"), url);
        }
    }

    @Test
    void anMcpResultForAForeignHostCarriesNoResourceLinkButThePath() throws Exception {
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            String id = upload(http, ServeFixture.bytes(INVOICE));
            byte[] call = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"render\",\"arguments\":{\"document\":\"" + id + "\"}}}")
                    .getBytes(StandardCharsets.UTF_8);
            Raw answer = raw(http.port(), "POST", "/mcp", "evil.example", call,
                    "Content-Type: application/json", "MCP-Protocol-Version: 2025-11-25");
            Jv.Obj result = (Jv.Obj) ServeFixture.json(answer.bytes()).get("result")
                    .orElseThrow();
            List<Jv> content = ((Jv.Arr) result.get("content").orElseThrow()).items();
            for (Jv item : content) {
                assertEquals("text", ((Jv.Obj) item).string("type").orElseThrow(),
                        "a resource link names a URI, which a path is not");
            }
            String text = ((Jv.Obj) content.get(0)).string("text").orElseThrow();
            assertTrue(Pattern.compile("rendering: /api/artifacts/[0-9a-f]{32}").matcher(text)
                    .find(), text);
            assertFalse(answer.body().contains("evil.example"), answer.body());
        }
    }

    // ---------------------------------------------------------------------------------
    // serve-3: hard links

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aHardLinkInAnAllowedDirectoryIsNotRead() throws Exception {
        Path allowed = Files.createDirectories(temp.resolve("allowed"));
        Path outside = Files.write(temp.resolve("outside.esj.json"), ServeFixture.bytes(INVOICE));
        Files.createLink(allowed.resolve("hard.esj.json"), outside);
        Files.write(allowed.resolve("single.esj.json"), ServeFixture.bytes(INVOICE));
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log());
        config = config.withAccess(List.of(), List.of(allowed), Optional.empty(), List.of(),
                Optional.empty());
        try (Http http = Http.start(config)) {
            HttpResponse<byte[]> hard = ServeFixture.post(http.url()
                    + "/api/summary?path=hard.esj.json", new byte[0]);
            assertEquals(403, hard.statusCode());
            assertTrue(ServeFixture.json(hard).string("message").orElseThrow()
                    .contains("hard links"));
            assertEquals(200, ServeFixture.post(http.url() + "/api/summary?path=single.esj.json",
                    new byte[0]).statusCode(), "a file with one name is read");
        }
    }

    // ---------------------------------------------------------------------------------
    // serve-4: what a model reads

    @Test
    void noTextOfAResultCarriesACharacterAReaderCannotSee() throws Exception {
        String hidden = "RE-‮-INJECT​⁦x⁩󠁁-\u0085\u0007END";
        String seller = "Example­ GmbH﻿️";
        String document = new String(ServeFixture.bytes(INVOICE), StandardCharsets.UTF_8)
                .replace("\"RE-2026-0042\"", json(hidden))
                .replace("\"Example GmbH\"", json(seller));
        Path file = Files.writeString(temp.resolve("hidden.esj.json"), document);
        List<Jv> answers = new ArrayList<>();
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            String id = upload(http, document.getBytes(StandardCharsets.UTF_8));
            for (String call : List.of(
                    "{\"name\":\"summary\",\"arguments\":{\"document\":\"" + id + "\"}}",
                    "{\"name\":\"get\",\"arguments\":{\"document\":\"" + id
                            + "\",\"paths\":[\"/BT-1\",\"/BG-4\"]}}",
                    "{\"name\":\"validate\",\"arguments\":{\"document\":\"" + id + "\"}}",
                    "{\"name\":\"inspect\",\"arguments\":{\"document\":\"" + id + "\"}}",
                    "{\"name\":\"no\\u202Etool\\u200B\",\"arguments\":{}}")) {
                answers.add(ServeFixture.json(ServeFixture.post(http.url() + "/mcp",
                        ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                                + call + "}").getBytes(StandardCharsets.UTF_8),
                        "Content-Type", "application/json", "MCP-Protocol-Version",
                        Mcp.LATEST)));
            }
            HttpResponse<byte[]> rest = ServeFixture.post(http.url() + "/api/get?paths=/BT-1",
                    document.getBytes(StandardCharsets.UTF_8));
            String wire = new String(rest.body(), StandardCharsets.UTF_8);
            assertVisible(wire, "the JSON of the REST API");
            assertEquals(hidden, ((Jv.Obj) ServeFixture.json(rest).get("values").orElseThrow())
                    .string("/BT-1").orElseThrow(), "the value comes back exactly");
        }
        ByteArrayOutputStream lines = new ByteArrayOutputStream();
        String input = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"get\",\"arguments\":{\"path\":" + json(file.toString())
                + ",\"paths\":[\"/BT-1\"]}}}\n";
        Stdio.run(ServeFixture.config(temp, ServeFixture.log()),
                new java.io.ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                line -> {
                    lines.writeBytes(line);
                    answers.add(Jv.parse(line, Integer.MAX_VALUE));
                });
        assertVisible(lines.toString(StandardCharsets.UTF_8), "a line of the standard output");
        for (Jv answer : answers) {
            Jv.Obj object = (Jv.Obj) answer;
            if (object.get("error").orElse(null) instanceof Jv.Obj error) {
                String message = error.string("message").orElseThrow();
                assertVisible(message, "an error");
                assertTrue(message.contains("\\u202E"), message);
                continue;
            }
            Jv.Obj result = (Jv.Obj) object.get("result").orElseThrow();
            for (Jv item : ((Jv.Arr) result.get("content").orElseThrow()).items()) {
                assertVisible(((Jv.Obj) item).string("text").orElse(""), "a text of a result");
            }
            Jv.Obj structured = (Jv.Obj) result.get("structuredContent").orElseThrow();
            if (structured.get("invoiceNumber").isPresent()) {
                assertEquals(hidden, structured.string("invoiceNumber").orElseThrow());
                assertEquals(seller, structured.string("seller").orElseThrow());
            }
            if (structured.get("values").orElse(null) instanceof Jv.Obj values) {
                assertEquals(hidden, values.string("/BT-1").orElseThrow(),
                        "the structured result carries the value exactly");
            }
        }
        assertEquals(6, answers.size());
    }

    @Test
    void everyCharacterAReaderCannotSeeIsEscaped() {
        for (int c : new int[] {0x00, 0x07, 0x09, 0x1B, 0x7F, 0x85, 0x9B, 0xAD, 0x061C, 0x180E,
            0x200B, 0x200D, 0x200E, 0x2028, 0x202E, 0x2060, 0x2066, 0x2069, 0xFE0F, 0xFEFF,
            0xFFF9, 0x3164, 0xE0001, 0xE0041, 0xE0100}) {
            assertTrue(Visible.hidden(c), Integer.toHexString(c));
            String text = Visible.text("a" + new String(Character.toChars(c)) + "b");
            assertVisible(text, Integer.toHexString(c));
        }
        for (int c : new int[] {'a', ' ', 0xE4, 0x20AC, 0x1F600, 0x05D0, 0x0301}) {
            assertFalse(Visible.hidden(c), Integer.toHexString(c));
        }
        assertEquals("a\nb", Visible.text("a\nb"), "a line feed stays a line feed");
        assertEquals("tag \\uDB40\\uDC41", Visible.text("tag 󠁁"));
        assertEquals("lone \\uD800", Visible.text("lone \uD800"));
    }

    // ---------------------------------------------------------------------------------
    // serve-1: an upload over MCP and the bound of a message

    @Test
    void anUploadOverMcpIsWrittenToTheStoreAsItArrives() throws Exception {
        byte[] bytes = new byte[6 * 1024 * 1024];
        new Random(7).nextBytes(bytes);
        String encoded = Base64.getMimeEncoder().encodeToString(bytes).replace("\r\n", "\\r\\n");
        byte[] call = ("{\"params\":{\"arguments\":{\"name\":\"big.bin\",\"content_base64\":\""
                + encoded + "\"},\"name\":\"upload\"},\"jsonrpc\":\"2.0\",\"id\":1,"
                + "\"method\":\"tools/call\"}").getBytes(StandardCharsets.UTF_8);
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            Jv.Obj answer = ServeFixture.json(ServeFixture.post(http.url() + "/mcp", call,
                    "Content-Type", "application/json", "MCP-Protocol-Version", Mcp.LATEST));
            Jv.Obj structured = (Jv.Obj) ((Jv.Obj) answer.get("result").orElseThrow())
                    .get("structuredContent").orElseThrow();
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bytes)), structured.string("sha256").orElseThrow());
            try (var files = Files.list(http.temporaryDirectory().resolve("jobs"))) {
                assertEquals(0, files.count(), "the encoded content is removed with its call");
            }

            byte[] notBase64 = ("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"upload\",\"arguments\":{\"content_base64\":"
                    + "\"QUJD=\"}}}").getBytes(StandardCharsets.UTF_8);
            Jv.Obj refused = (Jv.Obj) ((Jv.Obj) ServeFixture.json(ServeFixture.post(http.url()
                    + "/mcp", notBase64, "Content-Type", "application/json",
                    "MCP-Protocol-Version", Mcp.LATEST)).get("result").orElseThrow())
                    .get("structuredContent").orElseThrow();
            assertEquals("bad-arguments", refused.string("error").orElseThrow());
            assertTrue(refused.string("message").orElseThrow()
                    .startsWith("content_base64 is not base64"));

            byte[] long_ = ("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\",\"params\":"
                    + "{\"note\":\"" + "x".repeat(Messages.MAX_CHARACTERS + 1) + "\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            assertEquals(413, ServeFixture.post(http.url() + "/mcp", long_, "Content-Type",
                    "application/json").statusCode(), "a message beside an upload is short");
            String many = String.join(",", Collections.nCopies(Messages.MAX_VALUES + 1, "1"));
            assertEquals(413, ServeFixture.post(http.url() + "/mcp", ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":4,\"method\":\"ping\",\"params\":{\"n\":[" + many + "]}}")
                    .getBytes(StandardCharsets.UTF_8), "Content-Type", "application/json")
                    .statusCode());
        }
    }

    @Test
    void aMessageIsReadWithinItsBoundsWhateverItsShape() {
        assertThrows(Jv.JsonException.class, () -> Messages.read(stream(
                "{\"a\":1,\"a\":2}"), Optional.empty()), "a member twice");
        assertThrows(Messages.TooLarge.class, () -> Messages.read(stream("{\"content_base64\":\""
                + "A".repeat(Messages.MAX_CHARACTERS) + "\"}"), Optional.empty()),
                "content_base64 is a string like any other where no upload is taken");
    }

    // ---------------------------------------------------------------------------------
    // Cancellation

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aCancelledCallOverHttpEndsItsChild() throws Exception {
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withJobs(1, 1, Duration.ofSeconds(10), "512m", Duration.ofSeconds(60));
        config = config.withProcess(List.of("sh", "-c", "exec sleep 60"), config.environment(),
                "default", config.log());
        try (Http http = Http.start(config)) {
            String id = upload(http, ServeFixture.bytes(INVOICE));
            String url = http.url() + "/mcp";
            long started = System.nanoTime();
            CompletableFuture<HttpResponse<byte[]>> call = CompletableFuture.supplyAsync(() ->
                    ServeFixture.post(url, ("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":"
                            + "\"tools/call\",\"params\":{\"name\":\"summary\",\"arguments\":"
                            + "{\"document\":\"" + id + "\"}}}").getBytes(StandardCharsets.UTF_8),
                            "Content-Type", "application/json"));
            awaitRunning(http, 1);
            byte[] cancel = ("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\","
                    + "\"params\":{\"requestId\":7}}").getBytes(StandardCharsets.UTF_8);
            assertEquals(202, ServeFixture.post(url, cancel, "Content-Type", "application/json",
                    "Authorization", "Bearer somebody-else").statusCode());
            Thread.sleep(500);
            assertEquals(1, http.running(), "a cancellation of somebody else cancels nothing");
            assertEquals(202, ServeFixture.post(url, cancel, "Content-Type",
                    "application/json").statusCode());
            HttpResponse<byte[]> answer = call.get(15, TimeUnit.SECONDS);
            assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 15);
            Jv.Obj error = (Jv.Obj) ServeFixture.json(answer).get("error").orElseThrow();
            assertEquals(String.valueOf(Mcp.REQUEST_CANCELLED),
                    ((Jv.Num) error.get("code").orElseThrow()).text());
            awaitRunning(http, 0);
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aCancelledCallOverTheStandardStreamsEndsItsChildAndIsNotAnswered() throws Exception {
        Path started = temp.resolve("started");
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withJobs(1, 1, Duration.ofSeconds(10), "512m", Duration.ofSeconds(60));
        config = config.withProcess(List.of("sh", "-c", "touch '" + started + "'; exec sleep 60"),
                config.environment(), "default", config.log());
        PipedOutputStream feed = new PipedOutputStream();
        PipedInputStream in = new PipedInputStream(feed, 1 << 16);
        List<Jv> answers = Collections.synchronizedList(new ArrayList<>());
        ServeConfig settings = config;
        Thread server = new Thread(() -> Stdio.run(settings, in,
                line -> answers.add(Jv.parse(line, Integer.MAX_VALUE))));
        server.setDaemon(true);
        long begun = System.nanoTime();
        server.start();
        try (OutputStream out = feed) {
            out.write(("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"summary\",\"arguments\":{\"path\":"
                    + json(ServeFixture.file(INVOICE).toString()) + "}}}\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!Files.exists(started) && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(Files.exists(started), "the child started");
            out.write(("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":"
                    + "{\"requestId\":1,\"reason\":\"no longer needed\"}}\n"
                    + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            Thread.sleep(1500);
        }
        server.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(server.isAlive(), "the server ended with its input");
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - begun) < 25,
                "the child of 60 s was ended");
        assertEquals(1, answers.size(), "the ping, and nothing for the cancelled call: "
                + answers);
        assertEquals("2", ((Jv.Num) ((Jv.Obj) answers.get(0)).get("id").orElseThrow()).text());
    }

    // ---------------------------------------------------------------------------------
    // serve-1: a validation that had to be cut, and the heap of the server

    @Test
    void aValidationThatHadToBeCutHandsOverTheCompleteReport() throws Exception {
        String invoice = new String(ServeFixture.bytes(INVOICE), StandardCharsets.UTF_8);
        StringBuilder lines = new StringBuilder();
        for (int i = 10; i < 40; i++) {
            lines.append(",\n    \"/BG-25/").append(i).append("/BT-126\": \"").append(i)
                    .append('"');
        }
        int end = invoice.lastIndexOf('}', invoice.lastIndexOf('}') - 1);
        String document = invoice.substring(0, end).stripTrailing() + lines + "\n  }\n}\n";
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            String id = upload(http, document.getBytes(StandardCharsets.UTF_8));
            Jv.Obj result = (Jv.Obj) ServeFixture.json(ServeFixture.post(http.url() + "/mcp",
                    ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                            + "{\"name\":\"validate\",\"arguments\":{\"document\":\"" + id
                            + "\"}}}").getBytes(StandardCharsets.UTF_8), "Content-Type",
                    "application/json", "MCP-Protocol-Version", Mcp.LATEST)).get("result")
                    .orElseThrow();
            Jv.Obj structured = (Jv.Obj) result.get("structuredContent").orElseThrow();
            long total = Long.parseLong(((Jv.Num) structured.get("findingsTotal").orElseThrow())
                    .text());
            assertTrue(total > Results.FINDINGS, "findings: " + total);
            assertEquals(new Jv.Bool(false), structured.get("complete").orElseThrow());
            Jv.Obj file = (Jv.Obj) ((Jv.Arr) structured.get("files").orElseThrow()).items()
                    .get(0);
            assertEquals("complete-report", file.string("role").orElseThrow());
            HttpResponse<byte[]> report = ServeFixture.get(file.string("url").orElseThrow());
            assertEquals(200, report.statusCode());
            // The report of the command line, as the REST API passes it on: the children of
            // one server share their environment, and with it the language of the messages
            // of the XML Schema validator.
            HttpResponse<byte[]> rest = ServeFixture.post(http.url() + "/api/validate",
                    document.getBytes(StandardCharsets.UTF_8));
            assertEquals(new String(rest.body(), StandardCharsets.UTF_8),
                    new String(report.body(), StandardCharsets.UTF_8),
                    "the complete report is the command line's");
        }
    }

    @Test
    void theParallelismOfAServerFitsItsHeap() {
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withJobs(4, 4, Duration.ofSeconds(10), "512m", Duration.ofMinutes(1));
        List<String> warnings = new ArrayList<>();
        long mib = 1024L * 1024;
        assertEquals(4, Capacity.fit(config, 16, 1024 * mib, warnings::add).maxJobs());
        assertTrue(warnings.isEmpty());
        assertEquals(76 * mib, Capacity.needed(4, 4, 16), "32 MiB, 4 x 6 MiB, 20 x 1 MiB");
        ServeConfig fitted = Capacity.fit(config, 16, 66 * mib, warnings::add);
        assertEquals(2, fitted.maxJobs());
        assertEquals(4, fitted.maxQueue());
        assertTrue(warnings.get(0).startsWith("warning: the heap of this process, 66 MiB,"
                + " holds 2 children"), warnings.get(0));
        ServeConfig tight = Capacity.fit(config, 16, 56 * mib, warnings::add);
        assertEquals(1, tight.maxJobs());
        assertEquals(2, tight.maxQueue());
        assertTrue(Capacity.needed(tight.maxJobs(), tight.maxQueue(), 16) <= 56 * mib);
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> Capacity.fit(config, 16, 40 * mib, warnings::add));
        assertTrue(refused.getMessage().contains("it needs at least 54 MiB"),
                refused.getMessage());
        ServeConfig longQueue = Capacity.fit(config.withJobs(4, 64, Duration.ofSeconds(10),
                "512m", Duration.ofMinutes(1)), 16, 128 * mib, warnings::add);
        assertEquals(4, longQueue.maxJobs(), "a long queue gives way before a child does");
        assertEquals(56, longQueue.maxQueue());
    }

    // ---------------------------------------------------------------------------------

    /** An answer read from a socket. */
    private record Raw(int status, Map<String, String> headers, byte[] bytes) {
        String body() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        String header(String name) {
            return headers.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), "");
        }
    }

    /** Sends one request with a Host of the test's choosing; an empty one sends none. */
    private static Raw raw(int port, String method, String target, String host, byte[] body,
                           String... headers) throws IOException {
        ByteArrayOutputStream request = new ByteArrayOutputStream();
        StringBuilder head = new StringBuilder(method + " " + target + " HTTP/1.1\r\n");
        if (!host.isEmpty()) {
            head.append("Host: ").append(host).append("\r\n");
        }
        for (String header : headers) {
            head.append(header).append("\r\n");
        }
        head.append("Connection: close\r\n");
        if (body != null) {
            head.append("Content-Length: ").append(body.length).append("\r\n");
        }
        head.append("\r\n");
        request.writeBytes(head.toString().getBytes(StandardCharsets.US_ASCII));
        if (body != null) {
            request.writeBytes(body);
        }
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(120_000);
            socket.getOutputStream().write(request.toByteArray());
            socket.getOutputStream().flush();
            byte[] answer = socket.getInputStream().readAllBytes();
            int split = indexOf(answer, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            String[] lines = new String(answer, 0, split, StandardCharsets.US_ASCII)
                    .split("\r\n");
            Map<String, String> fields = new java.util.LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int colon = lines[i].indexOf(':');
                fields.putIfAbsent(lines[i].substring(0, colon).strip()
                        .toLowerCase(java.util.Locale.ROOT), lines[i].substring(colon + 1).strip());
            }
            byte[] content = java.util.Arrays.copyOfRange(answer, split + 4, answer.length);
            if ("chunked".equalsIgnoreCase(fields.get("transfer-encoding"))) {
                content = dechunk(content);
            }
            return new Raw(Integer.parseInt(lines[0].substring(9, 12)), fields, content);
        }
    }

    private static byte[] dechunk(byte[] chunked) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int at = 0;
        while (true) {
            int line = indexOf(java.util.Arrays.copyOfRange(chunked, at, chunked.length),
                    "\r\n".getBytes(StandardCharsets.US_ASCII));
            int size = Integer.parseInt(new String(chunked, at, line, StandardCharsets.US_ASCII)
                    .strip(), 16);
            if (size == 0) {
                return out.toByteArray();
            }
            out.write(chunked, at + line + 2, size);
            at += line + 2 + size + 2;
        }
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        for (int i = 0; i + pattern.length <= data.length; i++) {
            if (java.util.Arrays.equals(data, i, i + pattern.length, pattern, 0,
                    pattern.length)) {
                return i;
            }
        }
        throw new IllegalStateException("no " + new String(pattern, StandardCharsets.US_ASCII));
    }

    private static String firstUrl(Raw answer) {
        Jv.Obj result = ServeFixture.json(answer.bytes());
        return ((Jv.Obj) ((Jv.Arr) result.get("files").orElseThrow(() ->
                new AssertionError(answer.body()))).items().get(0)).string("url").orElseThrow();
    }

    private static String upload(Http http, byte[] document) {
        HttpResponse<byte[]> response = ServeFixture.post(http.url() + "/api/documents",
                document);
        assertEquals(201, response.statusCode());
        return ServeFixture.json(response).string("id").orElseThrow();
    }

    private static void awaitRunning(Http http, int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (http.running() != count && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(count, http.running());
    }

    private static void assertVisible(String text, String what) {
        text.codePoints().forEach(c -> assertFalse(c != '\n' && Visible.hidden(c),
                what + " carries U+" + Integer.toHexString(c) + ": " + Visible.text(text)));
    }

    private static String json(String text) {
        return new String(Jv.of(text).toBytes(), StandardCharsets.UTF_8);
    }

    private static InputStream stream(String text) {
        return new java.io.ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
