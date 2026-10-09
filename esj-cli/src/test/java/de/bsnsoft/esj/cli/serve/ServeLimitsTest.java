package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The bounds of {@code esj serve}: what a request may cost, what a child may cost, and that
 * neither outlives the server or tells anybody what a document said.
 */
class ServeLimitsTest {

    /** A document of the repository that every child reads. */
    private static final String INVOICE = "examples/standard-invoice.esj.json";

    @TempDir
    private Path temp;

    @Test
    void anUploadPastTheBoundIsRefusedBeforeItIsRead() throws Exception {
        List<String> log = ServeFixture.log();
        ServeConfig config = ServeFixture.config(temp, log).withStorage(64 * 1024,
                Duration.ofSeconds(ServeFixture.REQUEST_SECONDS), Duration.ofMinutes(15), 64,
                64L * 1024 * 1024, temp);
        try (Http http = Http.start(config);
             Socket socket = new Socket("127.0.0.1", http.port())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /api/validate HTTP/1.1\r\nHost: 127.0.0.1\r\n"
                    + "Content-Length: 1000000000\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            assertEquals(413, status(socket.getInputStream()), "a declared length past the"
                    + " bound is answered before a byte of the body was sent");
        }
        try (Http http = Http.start(config);
             Socket socket = new Socket("127.0.0.1", http.port())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /api/documents HTTP/1.1\r\nHost: 127.0.0.1\r\n"
                    + "Transfer-Encoding: chunked\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            CompletableFuture<Long> sender = CompletableFuture.supplyAsync(() -> {
                long sent = 0;
                byte[] chunk = ("10000\r\n" + "x".repeat(0x10000) + "\r\n")
                        .getBytes(StandardCharsets.US_ASCII);
                try {
                    for (int i = 0; i < 800; i++) {
                        out.write(chunk);
                        sent += 0x10000;
                    }
                } catch (IOException e) {
                    // The server closed the connection: what this test wants.
                }
                return sent;
            });
            assertEquals(413, status(socket.getInputStream()), "a body without a length is"
                    + " cut off at the bound while it is read");
            sender.get(30, TimeUnit.SECONDS);
        }
        long read = log.stream().filter(line -> line.contains("POST /api/documents 413"))
                .mapToLong(ServeLimitsTest::bytesIn).max().orElseThrow();
        assertTrue(read <= 64 * 1024 + 64 * 1024, "the server read " + read + " bytes of"
                + " 50 MiB before it refused them");
    }

    @Test
    void aChildThatRunsOutOfItsHeapCostsItsCallAndNotTheServer() throws Exception {
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log());
        config = config.withProcess(ServeFixture.child("16m"), config.environment(), "default",
                config.log());
        try (Http http = Http.start(config)) {
            HttpResponse<byte[]> response = ServeFixture.post(http.url() + "/api/validate",
                    ServeFixture.bytes("conformance/pdf/factur-x.pdf"));
            assertEquals(507, response.statusCode(), new String(response.body(),
                    StandardCharsets.UTF_8));
            Jv.Obj body = ServeFixture.json(response);
            assertEquals("limit", body.string("error").orElseThrow());
            assertEquals("3", ((Jv.Num) body.get("exitCode").orElseThrow()).text(),
                    "the virtual machine aborted on its heap");
            assertFalse(body.get("verdict").isPresent(), "a limit is no verdict");
            assertEquals(200, ServeFixture.get(http.url() + "/openapi.json").statusCode(),
                    "and the server answers the next request");
            assertEquals(201, ServeFixture.post(http.url() + "/api/documents",
                    ServeFixture.bytes(INVOICE)).statusCode());
        }
    }

    @Test
    void aChildPastItsDeadlineStopsItselfWithNoVerdict() throws Exception {
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withJobs(1, 1, Duration.ofSeconds(10), "512m", Duration.ofMillis(1));
        try (Http http = Http.start(config)) {
            HttpResponse<byte[]> response = ServeFixture.post(http.url() + "/api/validate",
                    ServeFixture.bytes("conformance/pdf/factur-x.pdf"));
            assertEquals(507, response.statusCode());
            assertEquals("7", ((Jv.Num) ServeFixture.json(response).get("exitCode")
                    .orElseThrow()).text(), "the child's own --max-runtime");
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aChildThatDoesNotStopIsKilled() throws Exception {
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withJobs(1, 1, Duration.ofSeconds(10), "512m", Duration.ofSeconds(1));
        config = config.withProcess(List.of("sh", "-c", "exec sleep 60"), config.environment(),
                "default", config.log());
        try (Http http = Http.start(config)) {
            long started = System.nanoTime();
            HttpResponse<byte[]> response = ServeFixture.post(http.url() + "/api/validate",
                    ServeFixture.bytes(INVOICE));
            long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);
            assertEquals(507, response.statusCode());
            assertTrue(ServeFixture.json(response).string("message").orElseThrow()
                    .contains("--job-timeout"));
            assertTrue(seconds < 20, "killed after the deadline and its grace: " + seconds);
            assertEquals(0, http.running());
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aFullServerAnswersAtOnceWithRetryAfter() throws Exception {
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withJobs(1, 0, Duration.ofSeconds(1), "512m", Duration.ofSeconds(30));
        config = config.withProcess(List.of("sh", "-c", "exec sleep 4"), config.environment(),
                "default", config.log());
        try (Http http = Http.start(config)) {
            byte[] document = ServeFixture.bytes(INVOICE);
            CompletableFuture<HttpResponse<byte[]>> first = CompletableFuture.supplyAsync(
                    () -> ServeFixture.post(http.url() + "/api/validate", document));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (http.running() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(1, http.running(), "the first call holds the only child");
            HttpResponse<byte[]> second = ServeFixture.post(http.url() + "/api/validate",
                    document);
            assertEquals(503, second.statusCode());
            assertEquals("busy", ServeFixture.json(second).string("error").orElseThrow());
            assertTrue(second.headers().firstValue("Retry-After").isPresent());
            first.get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void aClientThatSendsSlowlyIsCutOff() throws Exception {
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            for (String partial : List.of(
                    "POST /api/validate HTTP/1.1\r\nHost: 127.0.0.1\r\n",
                    "POST /api/validate HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 100\r\n"
                            + "\r\n0123456789")) {
                try (Socket socket = new Socket("127.0.0.1", http.port())) {
                    socket.setSoTimeout((ServeFixture.REQUEST_SECONDS + 10) * 1000);
                    socket.getOutputStream().write(partial.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    long started = System.nanoTime();
                    int read;
                    try {
                        read = socket.getInputStream().read();
                    } catch (SocketTimeoutException e) {
                        throw new AssertionError("the connection was still open after "
                                + (ServeFixture.REQUEST_SECONDS + 10) + " s", e);
                    } catch (IOException e) {
                        read = -1;
                    }
                    long seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);
                    assertTrue(read == -1 || read == 'H', "closed, or answered");
                    assertTrue(seconds <= ServeFixture.REQUEST_SECONDS + 3, "cut off after "
                            + seconds + " s");
                }
            }
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aPathOutsideTheAllowedDirectoriesIsRefusedLinksIncluded() throws Exception {
        Path allowed = Files.createDirectories(temp.resolve("allowed"));
        Path outside = Files.write(temp.resolve("outside.esj.json"), ServeFixture.bytes(INVOICE));
        Files.write(allowed.resolve("inside.esj.json"), ServeFixture.bytes(INVOICE));
        Files.createSymbolicLink(allowed.resolve("link.esj.json"), outside);
        Files.createSymbolicLink(allowed.resolve("inner-link.esj.json"),
                allowed.resolve("inside.esj.json"));
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log());
        config = config.withAccess(List.of(), List.of(allowed), Optional.empty(), List.of(),
                Optional.empty());
        try (Http http = Http.start(config)) {
            String url = http.url() + "/api/summary?path=";
            assertEquals(200, ServeFixture.post(url + "inside.esj.json", new byte[0])
                    .statusCode());
            assertEquals(200, ServeFixture.post(url + "inner-link.esj.json", new byte[0])
                    .statusCode(), "a link that stays inside is followed");
            assertEquals(403, ServeFixture.post(url + "link.esj.json", new byte[0])
                    .statusCode(), "a link that leads outside");
            assertEquals(403, ServeFixture.post(url + "../outside.esj.json", new byte[0])
                    .statusCode(), "a relative path that climbs out");
            assertEquals(403, ServeFixture.post(url + outside, new byte[0]).statusCode(),
                    "an absolute path outside");
            assertEquals(404, ServeFixture.post(url + "missing.esj.json", new byte[0])
                    .statusCode());
        }
    }

    @Test
    void anUploadIsGoneAfterItsTimeToLive() throws Exception {
        MutableClock clock = new MutableClock();
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log());
        try (Store store = new Store(config, clock)) {
            Store.Entry entry = store.upload(new java.io.ByteArrayInputStream(
                    ServeFixture.bytes(INVOICE)), "invoice");
            assertTrue(store.get(entry.id(), Store.Kind.DOCUMENT).isPresent());
            assertTrue(Files.exists(entry.file()));
            clock.now = clock.now.plus(config.ttl()).plusSeconds(1);
            store.sweep();
            assertFalse(store.get(entry.id(), Store.Kind.DOCUMENT).isPresent());
            assertFalse(Files.exists(entry.file()), "the bytes are removed with the entry");
        }
        ServeConfig shortLived = config.withStorage(config.maxUpload(), config.requestTimeout(),
                Duration.ofSeconds(1), config.maxStored(), config.maxStoredBytes(), temp);
        try (Http http = Http.start(shortLived)) {
            String id = ServeFixture.json(ServeFixture.post(http.url() + "/api/documents",
                    ServeFixture.bytes(INVOICE))).string("id").orElseThrow();
            Path stored = http.temporaryDirectory().resolve("store").resolve(id);
            assertTrue(Files.exists(stored));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (Files.exists(stored) && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
            assertFalse(Files.exists(stored), "the sweeper removed the upload");
            assertEquals(404, ServeFixture.post(http.url() + "/api/summary?document=" + id,
                    new byte[0]).statusCode());
        }
    }

    @Test
    void theStoreKeepsNoMoreThanItsBounds() throws Exception {
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log());
        config = config.withStorage(config.maxUpload(), config.requestTimeout(), config.ttl(), 2,
                config.maxStoredBytes(), temp);
        try (Http http = Http.start(config)) {
            byte[] document = ServeFixture.bytes(INVOICE);
            assertEquals(201, ServeFixture.post(http.url() + "/api/documents", document)
                    .statusCode());
            assertEquals(201, ServeFixture.post(http.url() + "/api/documents", document)
                    .statusCode());
            HttpResponse<byte[]> full = ServeFixture.post(http.url() + "/api/documents",
                    document);
            assertEquals(503, full.statusCode());
            assertEquals("full", ServeFixture.json(full).string("error").orElseThrow());
            assertTrue(full.headers().firstValue("Retry-After").isPresent());
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void stoppingEndsTheChildrenAndRemovesTheTemporaryDirectory() throws Exception {
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log());
        config = config.withProcess(List.of("sh", "-c", "exec sleep 60"), config.environment(),
                "default", config.log());
        Http http = Http.start(config);
        Path directory = http.temporaryDirectory();
        String url = http.url();
        ServeFixture.post(url + "/api/documents", ServeFixture.bytes(INVOICE));
        CompletableFuture<HttpResponse<byte[]>> running = CompletableFuture.supplyAsync(
                () -> ServeFixture.post(url + "/api/validate", ServeFixture.bytes(INVOICE)));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (http.running() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(1, http.running());
        assertTrue(Files.exists(directory));
        long started = System.nanoTime();
        http.close();
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 15);
        assertEquals(0, http.running(), "the child was ended");
        assertFalse(Files.exists(directory), "the temporary directory is gone");
        // The child the server ended left with the exit code of its signal: the call is told
        // that the server is shutting down, not that its child crashed (502).
        HttpResponse<byte[]> answer = running.get(30, TimeUnit.SECONDS);
        assertEquals(503, answer.statusCode(), new String(answer.body(),
                StandardCharsets.UTF_8));
        Jv.Obj body = ServeFixture.json(answer);
        assertEquals("busy", body.string("error").orElseThrow());
        assertEquals("the server is shutting down", body.string("message").orElseThrow());
        assertTrue(answer.headers().firstValue("Retry-After").isPresent());
    }

    @Test
    void theLogNamesNoContentOfADocument() throws Exception {
        String marker = "MARKER-7f3a9c";
        byte[] document = new String(ServeFixture.bytes(INVOICE), StandardCharsets.UTF_8)
                .replace("RE-2026-0042", marker).getBytes(StandardCharsets.UTF_8);
        List<String> log = ServeFixture.log();
        try (Http http = Http.start(ServeFixture.config(temp, log))) {
            ServeFixture.post(http.url() + "/api/validate?report=html", document);
            ServeFixture.post(http.url() + "/api/get?paths=/BT-1", document);
            String id = ServeFixture.json(ServeFixture.post(http.url() + "/api/documents",
                    document)).string("id").orElseThrow();
            ServeFixture.post(http.url() + "/api/summary?document=" + id, new byte[0]);
            ServeFixture.post(http.url() + "/api/get?paths=/BT-1&marker=" + marker, document);
            ServeFixture.post(http.url() + "/mcp", ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":"
                    + "\"tools/call\",\"params\":{\"name\":\"get\",\"arguments\":{\"document\":\""
                    + id + "\",\"paths\":[\"/BT-1\"]}}}").getBytes(StandardCharsets.UTF_8),
                    "Content-Type", "application/json");
        }
        assertTrue(log.size() > 5, "there is a log: " + log);
        for (String line : log) {
            assertFalse(line.contains(marker), "a log line names the document: " + line);
            assertFalse(line.contains("?"), "a log line carries a query string: " + line);
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aChildSeesNeitherTheTokenNorTheEnvironmentOfTheServer() throws Exception {
        Path seen = temp.resolve("environment.txt");
        Map<String, String> environment = Map.of(
                "PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"),
                "HOME", "/home/somebody",
                "ESJ_PACKS", "/srv/packs",
                "ESJ_TOKEN_FILE", "/run/secrets/esj_token",
                "ESJ_TOKEN", "a-token-in-the-environment",
                "DATABASE_PASSWORD", "hunter2",
                "JAVA_TOOL_OPTIONS", "-Xmx16g");
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withToken(Optional.of("the-token-of-the-server"));
        config = config.withProcess(List.of("sh", "-c", "env > '" + seen + "'"), environment,
                "default", config.log());
        try (Http http = Http.start(config)) {
            ServeFixture.post(http.url() + "/api/summary", ServeFixture.bytes(INVOICE),
                    "Authorization", "Bearer the-token-of-the-server");
        }
        String env = Files.readString(seen);
        assertTrue(env.contains("PATH="), env);
        assertTrue(env.contains("HOME=/home/somebody"), env);
        assertTrue(env.contains("ESJ_PACKS=/srv/packs"), env);
        for (String secret : List.of("ESJ_TOKEN_FILE", "ESJ_TOKEN=", "hunter2",
                "JAVA_TOOL_OPTIONS", "the-token-of-the-server")) {
            assertFalse(env.contains(secret), "the child saw " + secret + ":\n" + env);
        }
    }

    /** Reads the status code of an HTTP answer, past any interim one. */
    private static int status(InputStream in) throws IOException {
        while (true) {
            StringBuilder line = new StringBuilder();
            int c;
            while ((c = in.read()) >= 0 && c != '\n') {
                line.append((char) c);
            }
            String text = line.toString().strip();
            if (text.startsWith("HTTP/1.1 ")) {
                int code = Integer.parseInt(text.substring(9, 12));
                if (code >= 200) {
                    return code;
                }
            }
            if (c < 0) {
                return -1;
            }
        }
    }

    private static long bytesIn(String line) {
        Matcher matcher = Pattern.compile(" in=([0-9]+) ").matcher(line);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : Long.MAX_VALUE;
    }

    /** A clock a test moves. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-09T08:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
