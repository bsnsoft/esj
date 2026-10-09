package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the server holds on disk is bounded by {@code --max-disk}: clients that ask for large
 * validation reports and read none of them hold their answers on the disk, one after the
 * other, until the next call does not fit; that call is refused with 503 and
 * {@code Retry-After} before its child starts, the directory never holds more than the bound,
 * the server lives, and once the clients are gone it takes calls again.
 */
@DisabledOnOs(OS.WINDOWS)
class ServeDiskTest {

    private static final String INVOICE = "examples/standard-invoice.esj.json";

    @TempDir
    private Path temp;

    @Test
    void clientsThatReadNoAnswerFillTheDiskToItsBoundAndNoFurther() throws Exception {
        Path report = temp.resolve("report.json");
        Files.write(report, report());
        long size = Files.size(report);
        assertTrue(size > 2_000_000, "a validation report of " + size + " bytes");

        long maxUpload = 1024 * 1024;
        long maxDisk = Jobs.MAX_WRITTEN + Capacity.callFiles(maxUpload) + size * 7 / 2;
        List<String> log = ServeFixture.log();
        ServeConfig config = ServeFixture.config(temp, log)
                .withJobs(1, 1, Duration.ofSeconds(5), "64m", Duration.ofMinutes(1));
        config = config.withStorage(maxUpload, config.requestTimeout(), config.ttl(),
                config.maxStored(), config.maxStoredBytes(), temp).withDisk(maxDisk);
        // A child that writes the report of an invalid invoice, as validate does.
        config = config.withProcess(List.of("sh", "-c", "cat '" + report + "'; exit 1"),
                config.environment(), "default", config.log());
        byte[] document = ServeFixture.bytes(INVOICE);

        try (Http http = Http.start(config)) {
            Disk disk = http.store().disk();
            Sampler sampler = new Sampler(http.temporaryDirectory(), disk);
            Thread sampling = new Thread(sampler, "sampler");
            sampling.setDaemon(true);
            sampling.start();
            List<Socket> slow = new ArrayList<>();
            try {
                int held = 0;
                int refusedAt = -1;
                for (int client = 1; client <= 8 && refusedAt < 0; client++) {
                    slow.add(slowClient(http.port(), document));
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                    while (System.nanoTime() < deadline) {
                        if (count(log, "  child validate ") == held + 1) {
                            held++;
                            break;
                        }
                        if (count(log, "POST /api/validate 503 ") > 0) {
                            refusedAt = client;
                            break;
                        }
                        Thread.sleep(10);
                    }
                    // The answer is on its way to a client that does not read it: its file
                    // stays until the client reads it or goes.
                    awaitHeld(disk, held * size);
                }
                assertTrue(held >= 2 && refusedAt > held, "answers held: " + held
                        + ", refused at client " + refusedAt);

                HttpResponse<byte[]> refused = ServeFixture.post(http.url() + "/api/validate",
                        document);
                assertEquals(503, refused.statusCode(), new String(refused.body(),
                        StandardCharsets.UTF_8));
                Jv.Obj body = ServeFixture.json(refused);
                assertEquals("full", body.string("error").orElseThrow());
                assertTrue(body.string("message").orElseThrow().contains("--max-disk"),
                        body.toString());
                assertEquals(String.valueOf(Disk.RETRY_AFTER),
                        refused.headers().firstValue("Retry-After").orElseThrow());
                assertEquals(held, count(log, "  child validate "),
                        "a refused call starts no child");
                assertEquals(200, ServeFixture.get(http.url() + "/openapi.json").statusCode(),
                        "the server lives");
                assertTrue(sampler.most.get() <= maxDisk, "the directory held "
                        + sampler.most.get() + " bytes, more than " + maxDisk);
                assertTrue(sampler.counted.get() <= maxDisk, "the account came to "
                        + sampler.counted.get() + " bytes");
                assertTrue(sampler.most.get() >= held * size, "the answers were on the disk: "
                        + sampler.most.get());
                System.out.println("clientsThatReadNoAnswerFillTheDiskToItsBoundAndNoFurther: "
                        + held + " answers of " + size + " bytes held, client " + refusedAt
                        + " refused; the directory held at most " + sampler.most.get()
                        + " bytes, the account " + sampler.counted.get() + ", of " + maxDisk);
            } finally {
                for (Socket socket : slow) {
                    socket.close();
                }
            }

            // The clients are gone: so are their answers, and calls are taken again.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (disk.used() > 0 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertEquals(0, disk.used(), "every answer given back");
            HttpResponse<byte[]> taken = ServeFixture.post(http.url() + "/api/validate",
                    document);
            assertEquals(200, taken.statusCode());
            assertEquals("INVALID", ServeFixture.json(taken).string("verdict").orElseThrow());
            sampler.stop.set(true);
            sampling.join(5_000);
            assertTrue(sampler.most.get() <= maxDisk, "the directory held "
                    + sampler.most.get() + " bytes, more than " + maxDisk);
            assertFalse(sampler.failure.get(), "the directory could be measured");
        }
    }

    /**
     * Uploads kept for later fill the disk as well: once they have, every door refuses what
     * does not fit — an upload by its declared length before it is read, the content of an
     * upload over MCP as it is read, a call before its child starts — with 503 and
     * {@code Retry-After}, or over MCP with a result that says so; nothing is cut.
     */
    @Test
    void aDiskFilledWithUploadsRefusesAtEveryDoor() throws Exception {
        long mib = 1024 * 1024;
        long maxDisk = Jobs.MAX_WRITTEN + Capacity.callFiles(mib) + mib;
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log());
        config = config.withStorage(mib, config.requestTimeout(), config.ttl(), 1000, maxDisk,
                temp).withDisk(maxDisk);
        byte[] upload = new byte[(int) mib];
        new java.util.Random(7).nextBytes(upload);
        byte[] invoice = ServeFixture.bytes(INVOICE);
        try (Http http = Http.start(config)) {
            Disk disk = http.store().disk();
            String id = ServeFixture.json(ServeFixture.post(http.url() + "/api/documents",
                    invoice)).string("id").orElseThrow();
            int kept = 0;
            HttpResponse<byte[]> refused = null;
            while (refused == null && kept < 200) {
                HttpResponse<byte[]> response = ServeFixture.post(http.url() + "/api/documents",
                        upload);
                if (response.statusCode() == 201) {
                    kept++;
                } else {
                    refused = response;
                }
            }
            assertTrue(kept > 60, kept + " uploads kept");
            assertEquals(503, refused.statusCode(), new String(refused.body(),
                    StandardCharsets.UTF_8));
            assertEquals("full", ServeFixture.json(refused).string("error").orElseThrow());
            assertTrue(refused.headers().firstValue("Retry-After").isPresent());
            assertTrue(disk.used() <= maxDisk && disk.free() < mib, disk.used() + " of "
                    + maxDisk);

            String content = java.util.Base64.getEncoder().encodeToString(upload);
            HttpResponse<byte[]> mcpUpload = ServeFixture.post(http.url() + "/mcp",
                    ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":"
                            + "{\"name\":\"upload\",\"arguments\":{\"content_base64\":\""
                            + content + "\"}}}").getBytes(StandardCharsets.US_ASCII),
                    "Content-Type", "application/json");
            assertEquals(503, mcpUpload.statusCode());
            assertTrue(mcpUpload.headers().firstValue("Retry-After").isPresent());
            Jv.Obj error = (Jv.Obj) ServeFixture.json(mcpUpload).get("error").orElseThrow();
            assertEquals("-32000", ((Jv.Num) error.get("code").orElseThrow()).text());

            HttpResponse<byte[]> call = ServeFixture.post(http.url() + "/api/validate",
                    invoice);
            assertEquals(503, call.statusCode());
            assertEquals("full", ServeFixture.json(call).string("error").orElseThrow());
            assertTrue(call.headers().firstValue("Retry-After").isPresent());

            HttpResponse<byte[]> tool = ServeFixture.post(http.url() + "/mcp",
                    ("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
                            + "{\"name\":\"validate\",\"arguments\":{\"document\":\"" + id
                            + "\"}}}").getBytes(StandardCharsets.US_ASCII),
                    "Content-Type", "application/json", "MCP-Protocol-Version", "2025-11-25");
            assertEquals(200, tool.statusCode());
            Jv.Obj result = (Jv.Obj) ServeFixture.json(tool).get("result").orElseThrow();
            assertEquals(Jv.of(true), result.get("isError").orElseThrow());
            assertEquals("full", ((Jv.Obj) result.get("structuredContent").orElseThrow())
                    .string("error").orElseThrow());
            assertEquals(200, ServeFixture.get(http.url() + "/openapi.json").statusCode(),
                    "the server lives");
            assertTrue(disk.used() <= maxDisk);
        }
    }

    /** The report of {@code esj validate} on an invoice with a few thousand wrong lines. */
    private static byte[] report() {
        String invoice = new String(ServeFixture.bytes(INVOICE), StandardCharsets.UTF_8);
        String head = invoice.substring(0, invoice.lastIndexOf('}', invoice.lastIndexOf('}') - 1))
                .stripTrailing();
        StringBuilder values = new StringBuilder(head);
        for (int line = 3; line < 6_000; line++) {
            String path = ",\n    \"/BG-25/" + line + "/";
            values.append(path).append("BT-126\": \"").append(line).append('"')
                    .append(path).append("BT-129\": \"1\"")
                    .append(path).append("BT-130\": \"XXX\"")
                    .append(path).append("BT-131\": \"10.00\"")
                    .append(path).append("BG-29/BT-146\": \"10.00\"")
                    .append(path).append("BG-31/BT-153\": \"Item ").append(line).append('"');
        }
        byte[] document = values.append("\n  }\n}\n").toString()
                .getBytes(StandardCharsets.UTF_8);
        ServeFixture.Run run = ServeFixture.cli(document, "validate", "-", "--output", "json");
        assertEquals(1, run.code(), run.err());
        return run.out();
    }

    /** Sends a validation and reads nothing of its answer. */
    private static Socket slowClient(int port, byte[] document) throws IOException {
        Socket socket = new Socket();
        socket.setReceiveBufferSize(4096);
        socket.connect(new InetSocketAddress("127.0.0.1", port));
        OutputStream out = socket.getOutputStream();
        out.write(("POST /api/validate HTTP/1.1\r\nHost: 127.0.0.1:" + port
                + "\r\nContent-Length: " + document.length + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(document);
        out.flush();
        return socket;
    }

    private static void awaitHeld(Disk disk, long bytes) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (disk.used() < bytes && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static long count(List<String> log, String part) {
        synchronized (log) {
            return log.stream().filter(line -> line.contains(part)).count();
        }
    }

    /** Measures the temporary directory and the account of it, as often as it can. */
    private static final class Sampler implements Runnable {
        private final Path directory;
        private final Disk disk;
        final AtomicLong most = new AtomicLong();
        final AtomicLong counted = new AtomicLong();
        final AtomicBoolean stop = new AtomicBoolean();
        final AtomicBoolean failure = new AtomicBoolean();

        Sampler(Path directory, Disk disk) {
            this.directory = directory;
            this.disk = disk;
        }

        @Override
        public void run() {
            while (!stop.get()) {
                counted.accumulateAndGet(disk.used(), Math::max);
                most.accumulateAndGet(measure(), Math::max);
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        private long measure() {
            AtomicLong bytes = new AtomicLong();
            try {
                Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        bytes.addAndGet(attributes.size());
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                failure.set(true);
            }
            return bytes.get();
        }
    }
}
