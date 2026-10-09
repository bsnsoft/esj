package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code esj serve} and {@code esj mcp} as the processes a user starts, from the built jar:
 * the token from its file, the request timeout of the command line, the warning of an open
 * address, the standard output of {@code esj mcp} that carries nothing but JSON-RPC, and a
 * {@code SIGTERM} that ends the children and leaves no temporary directory behind.
 */
class ServeProcessIT {

    /** The self-contained jar, next to the classes of this module. */
    private static final Path JAR = Path.of("target", "esj.jar").toAbsolutePath();

    private static final Pattern LISTENING = Pattern.compile("listening on (http://\\S+?) ");

    private static final Pattern TEMPORARY = Pattern.compile("temporary directory (\\S+)$");

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @TempDir
    private Path directory;

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aServerTakesItsTokenFromAFileAndLeavesNothingBehindOnSigterm() throws Exception {
        Path token = Files.writeString(directory.resolve("token"), "a-token-of-this-test\n");
        ProcessBuilder builder = new ProcessBuilder(java(), "-Xmx256m", "-jar", JAR.toString(),
                "serve", "--port", "0", "--request-timeout", "2s", "--max-jobs", "1")
                .redirectOutput(directory.resolve("stdout").toFile());
        builder.environment().put("ESJ_TOKEN_FILE", token.toString());
        builder.environment().remove("ESJ_SERVE_BIND");
        Process process = builder.start();
        try {
            BlockingQueue<String> lines = errorLines(process);
            String url = matched(lines, LISTENING);
            Path temporary = Path.of(matched(lines, TEMPORARY));
            assertTrue(Files.isDirectory(temporary));

            byte[] invoice = Files.readAllBytes(Path.of("..", "examples",
                    "standard-invoice.esj.json"));
            assertEquals(401, post(url + "/api/summary", invoice, null).statusCode());
            HttpResponse<String> summary = post(url + "/api/summary", invoice,
                    "a-token-of-this-test");
            assertEquals(200, summary.statusCode(), summary.body());
            assertTrue(summary.body().contains("RE-2026-0042"));
            assertEquals(201, post(url + "/api/documents", invoice, "a-token-of-this-test")
                    .statusCode());
            try (var store = Files.list(temporary.resolve("store"))) {
                assertEquals(1, store.count(), "the upload is kept");
            }

            URI uri = URI.create(url);
            try (Socket socket = new Socket(uri.getHost(), uri.getPort())) {
                socket.setSoTimeout(15_000);
                OutputStream out = socket.getOutputStream();
                out.write("POST /api/summary HTTP/1.1\r\nHost: x\r\n"
                        .getBytes(StandardCharsets.US_ASCII));
                out.flush();
                long started = System.nanoTime();
                int read;
                try {
                    read = socket.getInputStream().read();
                } catch (IOException e) {
                    read = -1;
                }
                assertEquals(-1, read, "the connection was closed");
                assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) <= 6,
                        "within --request-timeout and the tick of the timer");
            }

            process.destroy();
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "SIGTERM ends the server");
            assertEquals(143, process.exitValue());
            assertFalse(Files.exists(temporary), "the temporary directory is gone");
            assertEquals(0, Files.size(directory.resolve("stdout")), "the server writes its log"
                    + " to the error stream and nothing to the standard output");
        } finally {
            process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aServerOnAnOpenAddressWithoutATokenSaysSo() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(java(), "-Xmx256m", "-jar", JAR.toString(),
                "serve", "--port", "0")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("ESJ_SERVE_BIND", "0.0.0.0");
        builder.environment().remove("ESJ_TOKEN_FILE");
        Process process = builder.start();
        try {
            BlockingQueue<String> lines = errorLines(process);
            matched(lines, LISTENING);
            matched(lines, Pattern.compile("^(warning: listening on 0\\.0\\.0\\.0 without a"
                    + " token)"));
        } finally {
            process.destroy();
            process.waitFor(20, TimeUnit.SECONDS);
            process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void theStdioServerWritesNothingButJsonRpcToTheStandardOutput() throws Exception {
        Path invoice = Path.of("..", "examples", "standard-invoice.esj.json").toAbsolutePath();
        Path stdout = directory.resolve("stdout");
        Path stderr = directory.resolve("stderr");
        Process process = new ProcessBuilder(java(), "-Xmx256m", "-jar", JAR.toString(), "mcp")
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        try (OutputStream in = process.getOutputStream()) {
            in.write(("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                    + "{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":"
                    + "{\"name\":\"it\",\"version\":\"1\"}}}\n"
                    + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}\n"
                    + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"validate\",\"arguments\":{\"path\":\"" + invoice + "\"}}}\n")
                    .getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(process.waitFor(2, TimeUnit.MINUTES), "the server ends with its input");
        assertEquals(0, process.exitValue(), Files.readString(stderr));
        List<String> lines = Files.readAllLines(stdout);
        assertEquals(2, lines.size(), String.join("\n", lines));
        assertTrue(lines.get(0).startsWith("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{"));
        assertTrue(lines.get(1).startsWith("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{"));
        assertTrue(lines.get(1).contains("\"verdict\":\"VALID\""), lines.get(1));
        assertTrue(Files.readString(stderr).startsWith("esj mcp "));
    }

    /**
     * The promise of {@code Capacity}, as a test: a server whose heap is exactly what it asks
     * for two children and a queue of two answers the largest calls of every tool that reads
     * what a child wrote, four at once — two running, two waiting — on a document its children
     * read with {@code --limits large}: a validation with tens of thousands of findings, the
     * summary of thirty thousand lines, the values that make the largest result a call carries
     * (notes of 32 Ki characters that every result escapes), a conversion and the page of
     * {@code inspect}. Then as many clients as it has threads ask for that largest result and
     * read none of their answers, one after the other, so that every thread holds a finished
     * answer at once. The server lives through all of it and answers the next request.
     *
     * <p>Before the answers were written into spools within the place of their child, the
     * slow clients alone ended this server with an {@code OutOfMemoryError}; before the server
     * read what a child wrote as a stream, so did the four summaries.
     */
    @DisabledOnOs(OS.WINDOWS)
    @Test
    void aServerWithTheHeapItAsksForOutlivesTheLargestCallsOfEveryTool() throws Exception {
        int lines = 30_000;
        String invoice = Files.readString(Path.of("..", "examples", "standard-invoice.esj.json"));
        String head = invoice.substring(0, invoice.lastIndexOf('}', invoice.lastIndexOf('}') - 1))
                .stripTrailing();
        StringBuilder notes = new StringBuilder();
        for (int note = 1; note <= 12; note++) {
            notes.append(",\n    \"/BG-1/").append(note).append("/BT-22\": \"")
                    .append("\\u202E".repeat(32_000)).append('"');
        }
        StringBuilder values = new StringBuilder(head).append(notes);
        for (int line = 3; line < lines; line++) {
            String path = ",\n    \"/BG-25/" + line + "/";
            values.append(path).append("BT-126\": \"").append(line).append('"')
                    .append(path).append("BT-129\": \"1\"")
                    .append(path).append("BT-130\": \"H87\"")
                    .append(path).append("BT-131\": \"10.00\"")
                    .append(path).append("BG-29/BT-146\": \"10.00\"")
                    .append(path).append("BG-31/BT-153\": \"Item ").append(line).append('"');
        }
        byte[] large = values.append("\n  }\n}\n").toString().getBytes(StandardCharsets.UTF_8);
        assertTrue(large.length > 6_000_000, "a document of " + large.length + " bytes");
        byte[] small = (head + notes + "\n  }\n}\n").getBytes(StandardCharsets.UTF_8);

        long mib = 1024L * 1024;
        long needed = needed(2, 2);
        Path stdout = directory.resolve("stdout");
        ProcessBuilder builder = new ProcessBuilder(java(), "-Xmx" + ((needed + mib - 1) / mib)
                + "m", "-XX:+ExitOnOutOfMemoryError", "-jar", JAR.toString(), "serve",
                "--port", "0", "--max-jobs", "2", "--max-queue", "2", "--queue-wait", "180s",
                "--limits", "large", "--job-heap", "512m")
                .redirectOutput(stdout.toFile());
        builder.environment().remove("ESJ_SERVE_BIND");
        builder.environment().remove("ESJ_TOKEN_FILE");
        long started = System.nanoTime();
        Process process = builder.start();
        try {
            BlockingQueue<String> errors = errorLines(process);
            List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
            String url = matched(errors, LISTENING, seen);
            String id = string(post(url + "/api/documents", large, null).body(), "id");
            String notesId = string(post(url + "/api/documents", small, null).body(), "id");
            String mcp = "{\"jsonrpc\":\"2.0\",\"id\":%d,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"%s\",\"arguments\":{\"document\":\"%s\"%s}}}";

            List<HttpResponse<String>> validations = four(
                    () -> async(url + "/api/validate?document=" + id, new byte[0], null),
                    () -> async(url + "/mcp", mcp(mcp, 1, "validate", id, ""), JSON));
            assertTrue(validations.get(0).body().contains("\"verdict\": \"INVALID\""));
            assertTrue(validations.get(2).body().contains("\"complete\":false"),
                    "tens of thousands of findings, cut to the heaviest");
            List<HttpResponse<String>> summaries = four(
                    () -> async(url + "/api/summary?document=" + id, new byte[0], null),
                    () -> async(url + "/mcp", mcp(mcp, 2, "summary", id, ""), JSON));
            assertTrue(summaries.get(0).body().contains("\"lines\": " + lines),
                    summaries.get(0).body());
            assertTrue(summaries.get(2).body().contains("\"lines\":" + lines));
            List<HttpResponse<String>> gets = four(
                    () -> async(url + "/api/get?document=" + id + "&paths=/BG-1/*,/BG-25/*",
                            new byte[0], null),
                    () -> async(url + "/mcp", mcp(mcp, 3, "get", id,
                            ",\"paths\":[\"/BG-1/*\",\"/BG-25/*/BT-131\"]"), JSON));
            assertTrue(gets.get(0).body().contains("\\u202E\\u202E"), "the notes, escaped");
            assertTrue(gets.get(0).body().contains("\"truncated\": true"));
            assertTrue(gets.get(2).body().length() > 2_000_000, "the largest result: "
                    + gets.get(2).body().length() + " characters");
            List<HttpResponse<String>> conversions = four(
                    () -> async(url + "/api/convert?document=" + id + "&to=esj", new byte[0],
                            null),
                    () -> async(url + "/mcp", mcp(mcp, 4, "convert", id, ",\"to\":\"esj\""),
                            JSON));
            assertTrue(conversions.get(0).body().contains("/api/artifacts/"),
                    "a document past 64 KiB is an artefact, not an answer");
            four(() -> async(url + "/api/inspect?document=" + id, new byte[0], null),
                    () -> async(url + "/mcp", mcp(mcp, 5, "inspect", id, ""), JSON));

            // As many clients as the server has threads, each waiting for the largest result
            // and reading none of it, one after the other: every thread holds a finished
            // answer at once.
            int threads = 2 + 2 + 16;
            List<Socket> slow = new java.util.ArrayList<>();
            URI uri = URI.create(url);
            try {
                for (int client = 0; client < threads; client++) {
                    byte[] call = mcp(mcp, 100 + client, "get", notesId,
                            ",\"paths\":[\"/BG-1/*\"]");
                    Socket socket = new Socket();
                    socket.setReceiveBufferSize(4096);
                    socket.connect(new java.net.InetSocketAddress(uri.getHost(), uri.getPort()));
                    socket.getOutputStream().write(("POST /mcp HTTP/1.1\r\nHost: "
                            + uri.getAuthority() + "\r\nContent-Type: application/json\r\n"
                            + "MCP-Protocol-Version: 2025-11-25\r\nContent-Length: "
                            + call.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(call);
                    socket.getOutputStream().flush();
                    slow.add(socket);
                    // The four calls of get above, and this one: its child has run, and its
                    // answer is being written to a client that does not read.
                    awaitLines(errors, seen, "  child get", 4 + client + 1);
                }
                assertTrue(process.isAlive(), "the server lives while " + threads
                        + " clients hold their answers");
            } finally {
                for (Socket socket : slow) {
                    socket.close();
                }
            }

            assertEquals(200, CLIENT.send(HttpRequest.newBuilder(URI.create(url
                    + "/openapi.json")).build(), HttpResponse.BodyHandlers.ofString())
                    .statusCode(), "and answers the next request");
            assertTrue(process.isAlive(), "the server lives");
            for (String line : seen) {
                assertFalse(line.startsWith("warning: the heap"), "the server runs with the"
                        + " parallelism it was asked for: " + line);
            }
        } finally {
            process.destroy();
            process.waitFor(20, TimeUnit.SECONDS);
            process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            System.out.println("aServerWithTheHeapItAsksForOutlivesTheLargestCallsOfEveryTool: "
                    + TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) + " s");
        }
        String out = Files.readString(stdout);
        assertFalse(out.contains("OutOfMemoryError"), out);
    }

    private static final String JSON = "application/json";

    private static byte[] mcp(String format, int id, String tool, String document, String more) {
        return String.format(format, id, tool, document, more).getBytes(StandardCharsets.UTF_8);
    }

    /** Sends two calls of each of two kinds at once and returns their answers, each a 200. */
    private static List<HttpResponse<String>> four(
            java.util.function.Supplier<java.util.concurrent.CompletableFuture<HttpResponse<String>>> rest,
            java.util.function.Supplier<java.util.concurrent.CompletableFuture<HttpResponse<String>>> mcp)
            throws Exception {
        List<java.util.concurrent.CompletableFuture<HttpResponse<String>>> calls =
                List.of(rest.get(), rest.get(), mcp.get(), mcp.get());
        List<HttpResponse<String>> answers = new java.util.ArrayList<>();
        for (var call : calls) {
            HttpResponse<String> answer = call.get(10, TimeUnit.MINUTES);
            assertEquals(200, answer.statusCode(), answer.body());
            assertFalse(answer.body().contains("\"isError\":true"), answer.body());
            answers.add(answer);
        }
        return answers;
    }

    /**
     * Returns the heap a server asks for: {@code Capacity.needed}, read from the server's own
     * classes, so that the test runs at exactly the minimum whatever its constants are.
     */
    private static long needed(int jobs, int queue) throws ReflectiveOperationException {
        Class<?> capacity = Class.forName("de.bsnsoft.esj.cli.serve.Capacity");
        java.lang.reflect.Field extra = capacity.getDeclaredField("HTTP_EXTRA");
        extra.setAccessible(true);
        java.lang.reflect.Method needed = capacity.getDeclaredMethod("needed", int.class,
                int.class, int.class);
        needed.setAccessible(true);
        return (long) needed.invoke(null, jobs, queue, extra.getInt(null));
    }

    /** Waits until so many lines that begin with a prefix were written. */
    private static void awaitLines(BlockingQueue<String> lines, List<String> seen, String prefix,
                                   long count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (seen.stream().filter(line -> line.startsWith(prefix)).count() < count) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("no " + count + " lines " + prefix + " in " + seen);
            }
            String line = lines.poll(1, TimeUnit.SECONDS);
            if (line != null) {
                seen.add(line);
            }
        }
    }

    /** Waits for a line that matches, keeps every line read, and returns its first group. */
    private static String matched(BlockingQueue<String> lines, Pattern pattern,
                                  List<String> seen) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            String line = lines.poll(1, TimeUnit.SECONDS);
            if (line == null) {
                continue;
            }
            seen.add(line);
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("no line matched " + pattern);
    }

    private static java.util.concurrent.CompletableFuture<HttpResponse<String>> async(
            String url, byte[] body, String contentType) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
            request.header("MCP-Protocol-Version", "2025-11-25");
        }
        return CLIENT.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String string(String json, String member) {
        Matcher matcher = Pattern.compile("\"" + member + "\": ?\"([^\"]*)\"").matcher(json);
        assertTrue(matcher.find(), json);
        return matcher.group(1);
    }

    private static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static HttpResponse<String> post(String url, byte[] body, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Reads the error stream of a process on a thread of its own, line by line. */
    private static BlockingQueue<String> errorLines(Process process) {
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                }
            } catch (IOException e) {
                // The process ended.
            }
        });
        reader.setDaemon(true);
        reader.start();
        return lines;
    }

    /** Waits for a line that matches and returns its first group. */
    private static String matched(BlockingQueue<String> lines, Pattern pattern)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            String line = lines.poll(1, TimeUnit.SECONDS);
            if (line == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("no line matched " + pattern);
    }
}
