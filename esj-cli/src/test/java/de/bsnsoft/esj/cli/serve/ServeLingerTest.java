package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A request the server refuses before it has read its body is answered, and then what is left
 * of the body is read and thrown away before the connection is closed: a client gets the
 * status and the whole body of the answer, never a connection reset in their place. A body
 * announced past the bound is not read; one that does not come is waited for only briefly.
 */
class ServeLingerTest {

    private static final String TOKEN = "the-token-of-the-server";

    private static final int MIB = 1024 * 1024;

    @TempDir
    private Path temp;

    /**
     * Deterministic, whatever the operating system does with a reset: the answer arrives
     * before a byte of the body has been sent, the body is then sent in full, and the
     * connection ends with a close the client reads as the end of the stream. A server that
     * closed after the answer with the body unread would make the client's writes, or its
     * read, fail.
     */
    @Test
    void aRefusedBodyIsReadToItsEndAfterTheAnswer() throws Exception {
        List<String> log = ServeFixture.log();
        ServeConfig config = ServeFixture.config(temp, log).withToken(Optional.of(TOKEN));
        byte[] body = random(MIB);
        try (Http http = Http.start(config);
             Socket socket = new Socket("127.0.0.1", http.port())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /api/validate HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: "
                    + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Answer answer = Answer.read(socket.getInputStream());
            assertEquals(401, answer.status(), "answered before a byte of the body was sent");
            assertEquals("unauthorized", ServeFixture.json(answer.body()).string("error")
                    .orElseThrow());

            out.write(body);
            out.flush();
            socket.shutdownOutput();
            assertEquals(-1, socket.getInputStream().read(), "the server read the body and"
                    + " closed the connection, rather than resetting it");
            String line = awaitLine(log, "POST /api/validate 401 ");
            assertEquals(body.length, bytesIn(line), "the body was read to its end: " + line);
        }
    }

    /**
     * Uploads, MCP messages, calls and wrong paths with a body of a megabyte, each refused
     * before the body was read or while it was: every answer arrives with its status and its
     * whole body, however often it is tried.
     */
    @Test
    void everyEarlyRefusalOfALargeBodyArrivesWhole() throws Exception {
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withToken(Optional.of(TOKEN));
        byte[] body = random(MIB);
        byte[] notJson = new byte[MIB];
        Arrays.fill(notJson, (byte) 'x');
        String bearer = "Bearer " + TOKEN;
        try (Http http = Http.start(config)) {
            String url = http.url();
            record Door(String what, int status, String url, byte[] body, String... headers) {
            }
            List<Door> doors = List.of(
                    new Door("no token", 401, url + "/api/validate", body),
                    new Door("no token, an upload", 401, url + "/api/documents", body),
                    new Door("a foreign origin", 403, url + "/api/validate", body,
                            "Authorization", bearer, "Origin", "https://elsewhere.example"),
                    new Door("no operation", 404, url + "/api/nothing", body,
                            "Authorization", bearer),
                    new Door("not a method of the path", 405, url + "/api/artifacts/none", body,
                            "Authorization", bearer),
                    new Door("MCP without JSON", 415, url + "/mcp", body,
                            "Authorization", bearer, "Content-Type", "text/plain"),
                    new Door("MCP that is not JSON, refused while it is read", 400,
                            url + "/mcp", notJson, "Authorization", bearer,
                            "Content-Type", "application/json"));
            List<String> failures = new ArrayList<>();
            int times = 60;
            for (Door door : doors) {
                for (int i = 0; i < times; i++) {
                    try {
                        HttpResponse<byte[]> response = ServeFixture.post(door.url(),
                                door.body(), door.headers());
                        if (response.statusCode() != door.status()) {
                            failures.add(door.what() + ": " + response.statusCode());
                        } else {
                            // The whole body: it is one JSON object.
                            ServeFixture.json(response);
                        }
                    } catch (RuntimeException e) {
                        failures.add(door.what() + ": " + e);
                    }
                }
            }
            assertTrue(failures.isEmpty(), failures.size() + " of " + doors.size() * times
                    + " answers were lost: " + failures.subList(0, Math.min(10,
                    failures.size())));
            assertEquals(200, ServeFixture.get(url + "/openapi.json").statusCode());
        }
    }

    /**
     * A body that is announced and does not come is waited for no longer than the server
     * lingers — at most a few seconds, here the second of {@code --request-timeout} — and
     * the server answers the next request.
     */
    @Test
    void aBodyThatDoesNotComeIsWaitedForBriefly() throws Exception {
        List<String> log = ServeFixture.log();
        ServeConfig config = ServeFixture.config(temp, log).withToken(Optional.of(TOKEN));
        config = config.withStorage(config.maxUpload(), Duration.ofSeconds(1), config.ttl(),
                config.maxStored(), config.maxStoredBytes(), temp);
        try (Http http = Http.start(config);
             Socket socket = new Socket("127.0.0.1", http.port())) {
            socket.setSoTimeout(15_000);
            socket.getOutputStream().write(("POST /api/validate HTTP/1.1\r\nHost: 127.0.0.1"
                    + "\r\nContent-Length: " + MIB + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            Answer answer = Answer.read(socket.getInputStream());
            assertEquals(401, answer.status());
            long started = System.nanoTime();
            int read;
            try {
                read = socket.getInputStream().read();
            } catch (IOException e) {
                read = -1;
            }
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertEquals(-1, read, "the connection was closed");
            assertTrue(millis < 2_500, "closed after " + millis + " ms: the server lingers for"
                    + " a second here, and the JDK's own deadline is "
                    + ServeFixture.REQUEST_SECONDS + " s");
            String line = awaitLine(log, "POST /api/validate 401 ");
            assertEquals(0, bytesIn(line), line);
            for (int i = 0; i < 20; i++) {
                assertEquals(200, ServeFixture.get(http.url() + "/openapi.json").statusCode());
            }
            assertEquals(201, ServeFixture.post(http.url() + "/api/documents",
                    ServeFixture.bytes("examples/standard-invoice.esj.json"), "Authorization",
                    "Bearer " + TOKEN).statusCode(), "the server takes the next upload");
        }
    }

    /**
     * A body announced past the bound is not read: the answer comes before it, and the server
     * reads none of it — a client that announces more than the server takes gets no
     * throughput.
     */
    @Test
    void aBodyAnnouncedPastTheBoundIsNotRead() throws Exception {
        List<String> log = ServeFixture.log();
        ServeConfig config = ServeFixture.config(temp, log);
        config = config.withStorage(MIB, config.requestTimeout(), config.ttl(),
                config.maxStored(), config.maxStoredBytes(), temp);
        try (Http http = Http.start(config);
             Socket socket = new Socket("127.0.0.1", http.port())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(("POST /api/documents HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: "
                    + (MIB + 1) + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Answer answer = Answer.read(socket.getInputStream());
            assertEquals(413, answer.status());
            assertEquals("close", answer.headers().get("connection"));
            String line = awaitLine(log, "POST /api/documents 413 ");
            assertEquals(0, bytesIn(line), line);
        }
    }

    // ---------------------------------------------------------------------------------

    private static byte[] random(int length) {
        byte[] bytes = new byte[length];
        new Random(7).nextBytes(bytes);
        return bytes;
    }

    private static String awaitLine(List<String> log, String part) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            synchronized (log) {
                for (String line : log) {
                    if (line.contains(part)) {
                        return line;
                    }
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("no line with " + part + " in " + log);
    }

    private static long bytesIn(String line) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(" in=([0-9]+) ")
                .matcher(line);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
    }

    /** One HTTP answer, read to the end of its body by its {@code Content-Length}. */
    private record Answer(int status, Map<String, String> headers, byte[] body) {

        static Answer read(InputStream in) throws IOException {
            int status = -1;
            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = line(in)) != null) {
                if (line.isEmpty()) {
                    if (status >= 200) {
                        break;
                    }
                    continue;
                }
                if (line.startsWith("HTTP/1.1 ")) {
                    status = Integer.parseInt(line.substring(9, 12));
                    headers.clear();
                } else {
                    int colon = line.indexOf(':');
                    headers.put(line.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                            line.substring(colon + 1).strip());
                }
            }
            int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            byte[] body = in.readNBytes(length);
            assertEquals(length, body.length, "the whole body of the answer");
            return new Answer(status, headers, body);
        }

        private static String line(InputStream in) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int c;
            while ((c = in.read()) >= 0 && c != '\n') {
                if (c != '\r') {
                    line.write(c);
                }
            }
            return c < 0 && line.size() == 0 ? null : line.toString(StandardCharsets.US_ASCII);
        }
    }
}
