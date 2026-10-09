package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the tests of the servers share: settings whose children are this module's own
 * classes on this test's class path, the files of the repository, and an HTTP client.
 *
 * <p>The bounds of the JDK's HTTP server are read once per process, when its classes are
 * first used, and the server sets them only where nobody did. This class sets the time a
 * request may take to arrive to three seconds before any server of these tests is made, so
 * that a client that sends a request slowly is cut off within a test's patience.
 */
final class ServeFixture {

    /** How long a request may take to arrive, in every server of these tests. */
    static final int REQUEST_SECONDS = 3;

    static {
        System.setProperty("sun.net.httpserver.maxReqTime", Integer.toString(REQUEST_SECONDS));
    }

    /** The class that starts the command line. */
    private static final String MAIN = "de.bsnsoft.esj.cli.Main";

    private ServeFixture() {
    }

    /**
     * Returns settings for a test: port 0 on loopback, children that run this module from the
     * class path of this test, and a log that collects its lines.
     *
     * @param temp the directory the temporary directory is made in
     * @param log  where the lines go
     * @return the settings
     */
    static ServeConfig config(Path temp, List<String> log) {
        return ServeConfig.defaults("0.0.0-TEST", log::add)
                .withListen("127.0.0.1", 0)
                .withJobs(2, 2, Duration.ofSeconds(10), "512m", Duration.ofMinutes(2))
                .withStorage(ServeConfig.DEFAULT_MAX_UPLOAD, Duration.ofSeconds(REQUEST_SECONDS),
                        Duration.ofMinutes(15), 64, 64L * 1024 * 1024, temp)
                .withProcess(child("512m"), Map.of("PATH", System.getenv().getOrDefault("PATH",
                        "/usr/bin:/bin")), "default", log::add);
    }

    /** Returns a log that may be written by several threads. */
    static List<String> log() {
        return Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Returns the command line of a child with the given heap.
     *
     * @param heap the heap ceiling
     * @return java, the options of the process boundary, the class path and the main class
     */
    static List<String> child(String heap) {
        return SelfCommand.virtualMachine(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), List.of(),
                System.getProperty("java.class.path"), heap);
    }

    /**
     * Returns a file of the repository, as the test class path carries it.
     *
     * @param resource the path relative to the root of the repository
     * @return the file
     */
    static Path file(String resource) {
        try {
            var url = ServeFixture.class.getResource("/" + resource);
            assertNotNull(url, resource + " is on the test class path");
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Returns a file of the repository as bytes. */
    static byte[] bytes(String resource) {
        try (InputStream in = ServeFixture.class.getResourceAsStream("/" + resource)) {
            assertNotNull(in, resource + " is on the test class path");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Runs the command line in this process, as {@code esj} would, and returns its standard
     * output.
     *
     * @param stdin the standard input
     * @param args  the command line
     * @return the exit code and the standard output
     */
    static Run cli(byte[] stdin, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = de.bsnsoft.esj.cli.Main.run(args, new ByteArrayInputStream(stdin), out, err);
        return new Run(code, out.toByteArray(), err.toString(StandardCharsets.UTF_8));
    }

    /** What a run of the command line left. */
    record Run(int code, byte[] out, String err) {
    }

    /** The HTTP client of the tests. */
    static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /** Sends a request and returns the answer with its body as bytes. */
    static HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Posts bytes to a URL. */
    static HttpResponse<byte[]> post(String url, byte[] body, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return send(builder.build());
    }

    /** Gets a URL. */
    static HttpResponse<byte[]> get(String url, String... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2)).GET();
        if (headers.length > 0) {
            builder.headers(headers);
        }
        return send(builder.build());
    }

    /** Reads a body as a JSON object. */
    static Jv.Obj json(HttpResponse<byte[]> response) {
        return (Jv.Obj) Jv.parse(response.body(), Integer.MAX_VALUE);
    }

    /** Reads bytes as a JSON object. */
    static Jv.Obj json(byte[] bytes) {
        return (Jv.Obj) Jv.parse(bytes, Integer.MAX_VALUE);
    }

    /** Returns a member of a JSON object as a string, or empty. */
    static Optional<String> string(Jv.Obj object, String name) {
        return object.string(name);
    }

    /** Returns the main class of the command line. */
    static String main() {
        return MAIN;
    }
}
