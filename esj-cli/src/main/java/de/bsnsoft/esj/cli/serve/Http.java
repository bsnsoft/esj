package de.bsnsoft.esj.cli.serve;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The HTTP server of {@code esj serve}: the REST API under {@code /api}, its OpenAPI
 * description at {@code /openapi.json}, and the MCP server at {@code /mcp}.
 *
 * <p>It is the HTTP server of the JDK, bounded where that server is not bounded by itself:
 * the time a request may take to arrive and an answer to leave, the size and number of its
 * header fields, the number of connections and of idle ones, and a body, which is cut off at
 * {@code --max-upload} while it is read rather than after. A request refused before its body
 * was read is answered first, and the rest of its body is then read and dropped before the
 * connection is closed ({@link Linger}), so that the client gets the answer rather than a
 * reset. Nothing in a request is evaluated here: a document goes to a child process, and what
 * the child says comes back.
 *
 * <p>Every request to {@code /api} and {@code /mcp} passes two checks before anything else.
 * A browser origin that is neither a loopback address nor one of {@code --allow-origin} is
 * refused with 403, which keeps a web page a browser happens to show — and a host name that
 * was made to resolve to this machine — from using a server that listens on loopback; and
 * where a token was configured, a request without it is refused with 401. The OpenAPI
 * description and the self-description at {@code /} are public, so that a client can be
 * configured before it holds the token; neither says anything a release does not.
 *
 * <p>A link this server hands out — to an artefact, to its own endpoints — is absolute only
 * where the server knows the address it was reached at: {@code --public-url}, or a
 * {@code Host} it answers to (a loopback name or the bind address with its own port, the
 * host of an {@code --allow-origin}, an {@code --allow-host}). Any other {@code Host} is a
 * header anybody can write, and a link built from it would send a client somewhere else; the
 * link is then the path on this server, which the client resolves against the URL it used.
 *
 * <p>One line per request goes to the log: the moment, the method, the path without its
 * query, the status, the bytes in and out, the duration, the exit code of the child and the
 * first twelve digits of the SHA-256 of the document. Nothing of a document, of a query
 * string or of a header is written.
 */
public final class Http implements AutoCloseable {

    /** The media type of every body this server writes but an artefact. */
    private static final String JSON = "application/json";

    /**
     * The longest MCP message beyond the content of an upload, in bytes: every character of
     * {@link Messages#MAX_CHARACTERS} escaped, and room for the structure around them.
     */
    private static final long MCP_OVERHEAD = 6L * Messages.MAX_CHARACTERS + 64 * 1024;

    /** A {@code Host} header: a name or an address in brackets, and a port. */
    private static final java.util.regex.Pattern HOST = java.util.regex.Pattern.compile(
            "([A-Za-z0-9.-]+|\\[[0-9A-Fa-f:.]+\\])(?::([0-9]{1,5}))?");

    /** The loopback names a {@code Host} header may give with the port of this server. */
    private static final java.util.Set<String> LOOPBACK = java.util.Set.of("localhost",
            "127.0.0.1", "::1");

    private final ServeConfig config;
    private final HttpServer server;
    private final Store store;
    private final Jobs jobs;
    private final Calls calls;
    private final Mcp mcp;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService sweeper;
    private final Optional<byte[]> token;
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final java.util.concurrent.CountDownLatch stopped =
            new java.util.concurrent.CountDownLatch(1);
    private final Thread hook;

    /** The base URL of the request being handled, for the links of an MCP result. */
    private final ThreadLocal<Optional<String>> currentBase =
            ThreadLocal.withInitial(Optional::empty);

    private Http(ServeConfig config) throws IOException {
        this.config = config;
        configureJdkServer(config);
        this.store = new Store(config, Clock.systemUTC());
        this.jobs = new Jobs(config, store.disk());
        this.calls = new Calls(config, store, jobs, Tools.Mode.HTTP);
        this.token = config.token().map(Http::digest);
        int threads = config.maxJobs() + config.maxQueue() + Capacity.HTTP_EXTRA;
        AtomicInteger count = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), runnable -> {
                    Thread thread = new Thread(runnable, "esj-serve-" + count.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        this.executor.allowCoreThreadTimeOut(true);
        this.server = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName(config.bind()), config.port()), 64);
        this.server.setExecutor(executor);
        this.server.createContext("/", this::handle);
        this.mcp = new Mcp(calls, config.version(), new Mcp.Door(
                arguments -> source(arguments.string(Tools.DOCUMENT).orElse(null),
                        arguments.string(Tools.PATH).orElse(null)),
                arguments -> calls.toStore(currentBase.get())), Tools.Mode.HTTP);
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "esj-serve-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        // It also keeps the ends of lingering closes, which are cancelled far more often than
        // they ring.
        scheduler.setRemoveOnCancelPolicy(true);
        this.sweeper = scheduler;
        long period = Math.max(1, Math.min(30, config.ttl().toSeconds() / 2));
        this.sweeper.scheduleAtFixedRate(store::sweep, period, period, TimeUnit.SECONDS);
        this.hook = new Thread(this::shutdown, "esj-serve-shutdown");
    }

    /**
     * Starts a server and returns it running.
     *
     * @param config the settings
     * @return the server
     * @throws IOException where the address cannot be bound
     */
    public static Http start(ServeConfig config) throws IOException {
        config = Capacity.fit(config, Capacity.HTTP_EXTRA, Runtime.getRuntime().maxMemory(),
                config.log());
        Http http = new Http(config);
        http.server.start();
        Runtime.getRuntime().addShutdownHook(http.hook);
        String url = http.url();
        config.log().accept("esj serve " + config.version() + ": listening on " + url
                + " — REST " + url + "/api, OpenAPI " + url + "/openapi.json, MCP " + url
                + "/mcp");
        config.log().accept("children: " + String.join(" ", http.jobs.prefix()) + "; at most "
                + config.maxJobs() + " at once, " + config.maxQueue() + " waiting, heap "
                + config.jobHeap() + ", " + config.jobTimeout().toSeconds() + " s each;"
                + " temporary directory " + http.store.root());
        Capacity.report(config, Capacity.HTTP_EXTRA, http.store, config.log());
        if (!loopback(config.bind()) && config.token().isEmpty()) {
            config.log().accept("warning: listening on " + config.bind() + " without a token:"
                    + " anyone who reaches this port can use it; --token-file sets one");
        }
        return http;
    }

    /**
     * Returns the port the server listens on.
     *
     * @return the port, the one the system chose where 0 was asked for
     */
    public int port() {
        return server.getAddress().getPort();
    }

    /**
     * Returns the URL the server listens on.
     *
     * @return the URL, with a loopback address where the server listens on every interface
     */
    public String url() {
        String host = config.bind();
        if (host.contains(":")) {
            host = "[" + host + "]";
        }
        if ("0.0.0.0".equals(config.bind()) || "[::]".equals(host) || "[::0]".equals(host)) {
            host = "127.0.0.1";
        }
        return "http://" + host + ":" + port();
    }

    /** Returns the temporary directory of this process. */
    Path temporaryDirectory() {
        return store.root();
    }

    /** Returns the store, for a test. */
    Store store() {
        return store;
    }

    /** Returns how many children run now, for a test. */
    int running() {
        return jobs.running();
    }

    /**
     * Waits until the server has been stopped.
     *
     * @throws InterruptedException where the waiting thread is interrupted
     */
    public void await() throws InterruptedException {
        stopped.await();
    }

    /** Stops the server: no new requests, every child ended, the temporary directory gone. */
    @Override
    public void close() {
        shutdown();
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException e) {
            // The virtual machine is shutting down, and the hook runs anyway.
        }
    }

    private void shutdown() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        config.log().accept("stopping: no new requests; ending " + jobs.running()
                + " running children and removing " + store.root());
        jobs.close();
        server.stop(2);
        sweeper.shutdownNow();
        executor.shutdownNow();
        store.close();
        stopped.countDown();
    }

    /**
     * Sets the bounds of the JDK's HTTP server, where the operator did not set them.
     *
     * <p>They are read once per process, when the server's classes are first used, which is
     * why they are system properties and why they are set before the server is made.
     */
    private static void configureJdkServer(ServeConfig config) {
        long requestSeconds = Math.max(1, config.requestTimeout().toSeconds());
        long responseSeconds = config.queueWait().plus(config.jobTimeout())
                .plus(Jobs.KILL_GRACE).toSeconds() + 120;
        int connections = Math.max(64, 4 * (config.maxJobs() + config.maxQueue()
                + Capacity.HTTP_EXTRA));
        Map<String, String> bounds = new LinkedHashMap<>();
        bounds.put("sun.net.httpserver.maxReqTime", Long.toString(requestSeconds));
        bounds.put("sun.net.httpserver.maxRspTime", Long.toString(responseSeconds));
        bounds.put("sun.net.httpserver.maxReqHeaders", "64");
        bounds.put("sun.net.httpserver.maxReqHeaderSize", Integer.toString(32 * 1024));
        bounds.put("sun.net.httpserver.idleInterval", "30");
        bounds.put("sun.net.httpserver.maxIdleConnections", "32");
        bounds.put("jdk.httpserver.maxConnections", Integer.toString(connections));
        // The JDK's server writes an answer as two packets, its head and then its body. With
        // Nagle's algorithm the body waits until the head is acknowledged, which a client
        // delays — by 40 ms on Linux — on a connection that stays open: one kept alive, or
        // one open while the rest of a refused body is read (Linger).
        bounds.put("sun.net.httpserver.nodelay", "true");
        bounds.forEach((name, value) -> {
            if (System.getProperty(name) == null) {
                System.setProperty(name, value);
            }
        });
    }

    private static boolean loopback(String bind) {
        try {
            return InetAddress.getByName(bind).isLoopbackAddress();
        } catch (IOException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------------------
    // Requests

    /** What one request came to, for the log line. */
    private static final class Record {
        final Linger linger;
        int status;
        long out;
        int exit = -1;
        String sha = "";

        Record(Linger linger) {
            this.linger = linger;
        }
    }

    private void handle(HttpExchange exchange) {
        long started = System.nanoTime();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getRawPath();
        Record record = new Record(Linger.install(exchange, "/mcp".equals(path) ? mcpMost()
                : config.maxUpload(), lingerTime(), sweeper));
        try {
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            route(exchange, method, path, record);
        } catch (RuntimeException | IOException e) {
            config.log().accept("error: " + method + " " + loggable(path) + ": " + e);
            try {
                respond(exchange, record, 500, Outcome.error(Outcome.Status.INTERNAL, -1,
                        "internal error").structured());
            } catch (IOException | RuntimeException ignored) {
                // The connection is gone.
            }
        } finally {
            exchange.close();
            long millis = (System.nanoTime() - started) / 1_000_000;
            config.log().accept(Instant.now() + " " + loggable(method) + " " + loggable(path)
                    + " " + record.status + " in=" + record.linger.received() + " out="
                    + record.out + " " + millis + "ms"
                    + (record.exit >= 0 ? " exit=" + record.exit : "")
                    + (record.sha.isEmpty() ? "" : " sha256=" + record.sha));
        }
    }

    private static String loggable(String path) {
        String safe = Results.safe(Results.cut(path == null ? "" : path, 120)).replace('\n', ' ');
        if (safe.startsWith("/api/artifacts/") && safe.length() > "/api/artifacts/".length() + 8) {
            return safe.substring(0, "/api/artifacts/".length() + 8) + "…";
        }
        return safe;
    }

    private void route(HttpExchange exchange, String method, String path, Record record)
            throws IOException {
        if (stopping.get()) {
            exchange.getResponseHeaders().set("Retry-After", "5");
            respond(exchange, record, 503, Outcome.error(Outcome.Status.BUSY, -1,
                    "the server is shutting down").structured());
            return;
        }
        if ("/".equals(path) || "/openapi.json".equals(path)) {
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                notAllowed(exchange, record, "GET");
                return;
            }
            Jv body = "/".equals(path) ? self(base(exchange).orElse(""))
                    : OpenApi.document(calls, config, token.isPresent());
            respond(exchange, record, 200, body);
            return;
        }
        boolean api = path.startsWith("/api/");
        boolean mcpPath = "/mcp".equals(path);
        if (!api && !mcpPath) {
            respond(exchange, record, 404, Outcome.error(Outcome.Status.NOT_FOUND, -1,
                    "nothing here; GET / lists what there is").structured());
            return;
        }
        Optional<String> origin = Optional.ofNullable(
                exchange.getRequestHeaders().getFirst("Origin"));
        if (origin.isPresent() && !originAllowed(origin.get())) {
            respond(exchange, record, 403, Jv.object().put("error", "origin")
                    .put("message", "requests from this origin are refused; the operator"
                            + " allows others with --allow-origin").build());
            return;
        }
        origin.filter(this::originListed).ifPresent(allowed -> cors(exchange, allowed));
        if ("OPTIONS".equals(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, POST, OPTIONS");
            empty(exchange, record, 204);
            return;
        }
        if (token.isPresent() && !authorized(exchange)) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer realm=\"esj\"");
            respond(exchange, record, 401, Jv.object().put("error", "unauthorized")
                    .put("message", "this server needs Authorization: Bearer <token>").build());
            return;
        }
        currentBase.set(base(exchange));
        try {
            if (mcpPath) {
                mcp(exchange, method, record, scope(exchange));
            } else {
                api(exchange, method, path.substring("/api/".length()), record);
            }
        } finally {
            currentBase.remove();
        }
    }

    private boolean authorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        return MessageDigest.isEqual(token.get(), digest(header.substring(7).strip()));
    }

    private static byte[] digest(String text) {
        return Store.sha256().digest(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Tells whether a browser origin may use this server: a loopback one or a listed one. */
    boolean originAllowed(String origin) {
        if (originListed(origin)) {
            return true;
        }
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (host == null || !("http".equals(uri.getScheme())
                    || "https".equals(uri.getScheme()))) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return "localhost".equals(host) || host.endsWith(".localhost")
                    || "127.0.0.1".equals(host) || "[::1]".equals(host) || "::1".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean originListed(String origin) {
        return config.allowedOrigins().contains("*")
                || config.allowedOrigins().contains(origin);
    }

    private static void cors(HttpExchange exchange, String origin) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", origin);
        headers.set("Vary", "Origin");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept,"
                + " MCP-Protocol-Version, X-Session-Id");
        headers.set("Access-Control-Expose-Headers", "Link, Retry-After");
        headers.set("Access-Control-Max-Age", "600");
    }

    /**
     * Returns the URL the client reached this server at, for the links of a result, where the
     * server knows it: {@code --public-url}, or a {@code Host} this server answers to.
     */
    private Optional<String> base(HttpExchange exchange) {
        if (config.publicUrl().isPresent()) {
            return config.publicUrl();
        }
        String host = exchange.getRequestHeaders().getFirst("Host");
        return host == null ? Optional.empty() : known(host.strip());
    }

    /**
     * Returns the base URL of a {@code Host} this server answers to, or nothing for any
     * other.
     *
     * @param host the value of the header
     * @return the base URL, without a trailing slash
     */
    Optional<String> known(String host) {
        java.util.regex.Matcher matcher = HOST.matcher(host);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String name = matcher.group(1).toLowerCase(Locale.ROOT);
        if (name.startsWith("[")) {
            name = name.substring(1, name.length() - 1);
        }
        int given = matcher.group(2) == null ? 80 : Integer.parseInt(matcher.group(2));
        String bind = config.bind().toLowerCase(Locale.ROOT);
        boolean wildcard = "0.0.0.0".equals(bind) || "::".equals(bind) || "::0".equals(bind);
        if (given == port() && (LOOPBACK.contains(name) || name.endsWith(".localhost")
                || (!wildcard && name.equals(bind)))) {
            return Optional.of("http://" + host);
        }
        for (String origin : config.allowedOrigins()) {
            if (sameAuthority(origin, name, matcher.group(2))) {
                return Optional.of(origin.endsWith("/") ? origin.substring(0,
                        origin.length() - 1) : origin);
            }
        }
        for (String allowed : config.allowedHosts()) {
            if (allowed.equalsIgnoreCase(host)) {
                return Optional.of("http://" + host);
            }
        }
        return Optional.empty();
    }

    /** Tells whether a browser origin names a host and a port. */
    private static boolean sameAuthority(String origin, String name, String port) {
        try {
            URI uri = URI.create(origin);
            if (uri.getHost() == null || uri.getScheme() == null) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("[")) {
                host = host.substring(1, host.length() - 1);
            }
            int own = uri.getPort() >= 0 ? uri.getPort()
                    : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            int given = port == null ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80)
                    : Integer.parseInt(port);
            return host.equals(name) && own == given;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Returns who sent a request, as far as HTTP tells: the address it came from and its
     * credentials. Within one, a request id names one call of MCP.
     */
    private static String scope(HttpExchange exchange) {
        String authorization = Optional.ofNullable(exchange.getRequestHeaders()
                .getFirst("Authorization")).orElse("");
        return exchange.getRemoteAddress().getAddress().getHostAddress() + "|"
                + HexFormat.of().formatHex(digest(authorization));
    }

    private Jv self(String base) {
        return Jv.object()
                .put("name", "esj")
                .put("version", config.version())
                .put("description", "EN 16931 e-invoices through the semantic model: validate,"
                        + " summarise, read, convert, render, extract and inspect.")
                .put("openapi", base + "/openapi.json")
                .put("api", base + "/api")
                .put("mcp", base + "/mcp")
                .put("tokenRequired", token.isPresent())
                .build();
    }

    // ---------------------------------------------------------------------------------
    // REST

    private void api(HttpExchange exchange, String method, String rest, Record record)
            throws IOException {
        if ("documents".equals(rest)) {
            if (!"POST".equals(method)) {
                notAllowed(exchange, record, "POST");
                return;
            }
            upload(exchange, record);
            return;
        }
        if (rest.startsWith("artifacts/")) {
            if (!"GET".equals(method)) {
                notAllowed(exchange, record, "GET");
                return;
            }
            artifact(exchange, rest.substring("artifacts/".length()), record);
            return;
        }
        Optional<Tools.Tool> tool = Tools.find(calls.tools(), rest)
                .filter(found -> found.document());
        if (tool.isEmpty()) {
            respond(exchange, record, 404, Outcome.error(Outcome.Status.NOT_FOUND, -1,
                    "no operation /api/" + Results.safe(Results.cut(rest, 40))
                            + "; /openapi.json lists them").structured());
            return;
        }
        if (!"POST".equals(method)) {
            notAllowed(exchange, record, "POST");
            return;
        }
        tool(exchange, tool.get(), record);
    }

    private void upload(HttpExchange exchange, Record record) throws IOException {
        if (tooLong(exchange, config.maxUpload(), record) || noRoom(exchange, record)) {
            return;
        }
        try {
            Store.Entry entry = store.upload(exchange.getRequestBody(), null);
            record.sha = entry.sha256().substring(0, 12);
            respond(exchange, record, 201, Calls.uploaded(entry).structured());
        } catch (Store.TooLarge e) {
            tooLarge(exchange, record, e.getMessage());
        } catch (Store.Full e) {
            exchange.getResponseHeaders().set("Retry-After",
                    Long.toString(Math.max(1, config.ttl().toSeconds())));
            respond(exchange, record, 503, Outcome.error(Outcome.Status.FULL, -1,
                    e.getMessage()).structured());
        } catch (Disk.Full full) {
            full(exchange, record, full);
        }
    }

    private void artifact(HttpExchange exchange, String id, Record record) throws IOException {
        Optional<Store.Entry> entry = store.get(id, Store.Kind.ARTIFACT);
        if (entry.isEmpty()) {
            respond(exchange, record, 404, Outcome.error(Outcome.Status.NOT_FOUND, -1,
                    "no artefact " + Results.safe(Results.cut(id, 40)) + " is kept here;"
                            + " artefacts are kept for " + config.ttl().toMinutes()
                            + " minutes").structured());
            return;
        }
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", entry.get().mediaType()
                + (entry.get().mediaType().startsWith("text/") ? "; charset=utf-8" : ""));
        headers.set("Content-Disposition", "attachment; filename=\"" + entry.get().name() + "\"");
        // A rendering or report written by this tool runs no script in this origin.
        headers.set("Content-Security-Policy", "sandbox; default-src 'none'; style-src"
                + " 'unsafe-inline'; img-src data:");
        record.status = 200;
        record.out = entry.get().bytes();
        exchange.sendResponseHeaders(200, entry.get().bytes());
        try (OutputStream out = exchange.getResponseBody()) {
            Files.copy(entry.get().file(), out);
        }
    }

    private void tool(HttpExchange exchange, Tools.Tool tool, Record record) throws IOException {
        Map<String, List<String>> query = query(exchange.getRequestURI().getRawQuery());
        if (tooLong(exchange, config.maxUpload(), record) || noRoom(exchange, record)) {
            return;
        }
        Body body;
        try {
            body = Body.read(exchange, store.disk(), config.maxUpload());
        } catch (Store.TooLarge e) {
            tooLarge(exchange, record, e.getMessage());
            return;
        } catch (Disk.Full full) {
            full(exchange, record, full);
            return;
        }
        try {
            Jv.Builder arguments = Jv.object();
            Optional<String> document = Optional.empty();
            Optional<String> path = Optional.empty();
            Optional<Path> documentBody = Optional.empty();
            for (Map.Entry<String, List<String>> parameter : query.entrySet()) {
                String name = parameter.getKey();
                List<String> values = parameter.getValue();
                if (Tools.DOCUMENT.equals(name)) {
                    document = Optional.of(values.get(values.size() - 1));
                } else if (Tools.PATH.equals(name)) {
                    path = Optional.of(values.get(values.size() - 1));
                } else {
                    Optional<Tools.Param> param = tool.param(name);
                    if (param.isEmpty()) {
                        respond(exchange, record, 400, Outcome.error(Outcome.Status.ARGUMENTS,
                                -1, tool.name() + " takes no parameter "
                                        + Results.safe(Results.cut(name, 40))).structured());
                        return;
                    }
                    Optional<Jv> value = queryValue(param.get(), values);
                    if (value.isEmpty()) {
                        respond(exchange, record, 400, Outcome.error(Outcome.Status.ARGUMENTS,
                                -1, tool.name() + " " + name + " is true or false")
                                .structured());
                        return;
                    }
                    arguments.put(name, value.get());
                }
            }
            if (body.file().isPresent()) {
                Optional<Jv.Obj> parameters = body.parameters(tool);
                if (parameters.isPresent()) {
                    for (Map.Entry<String, Jv> member : parameters.get().members().entrySet()) {
                        if (Tools.DOCUMENT.equals(member.getKey())
                                && member.getValue() instanceof Jv.Str str) {
                            document = Optional.of(str.value());
                        } else if (Tools.PATH.equals(member.getKey())
                                && member.getValue() instanceof Jv.Str str) {
                            path = Optional.of(str.value());
                        } else {
                            arguments.put(member.getKey(), member.getValue());
                        }
                    }
                } else {
                    documentBody = body.file();
                    record.sha = body.sha256().substring(0, 12);
                }
            }
            int given = (documentBody.isPresent() ? 1 : 0) + (document.isPresent() ? 1 : 0)
                    + (path.isPresent() ? 1 : 0);
            if (given > 1) {
                respond(exchange, record, 400, Outcome.error(Outcome.Status.ARGUMENTS, -1,
                        "give the document once: as the request body, as document=<id> or as"
                                + " path=<file>").structured());
                return;
            }
            Calls.Source source = documentBody.isPresent()
                    ? new Calls.Source.Body(documentBody.get())
                    : source(document.orElse(null), path.orElse(null));
            if (source instanceof Calls.Source.Upload upload) {
                store.get(upload.id())
                        .ifPresent(entry -> record.sha = entry.sha256().substring(0, 12));
            }
            Reply reply = calls.call(tool, arguments.build(), source,
                    calls.toStore(base(exchange)), Calls.Options.REST,
                    outcome -> Reply.of(outcome, store.disk()));
            record.exit = reply.exitCode();
            try {
                answer(exchange, record, reply);
            } finally {
                reply.release();
            }
        } finally {
            body.delete();
        }
    }

    /**
     * What the REST API sends for one call, made from its outcome while the call still held
     * its child's place: the body is a spool or the child's own report, and nothing of the
     * result it was written from is kept.
     *
     * @param status     the HTTP status
     * @param exitCode   the exit code of the child, or -1
     * @param retryAfter the seconds after which a refused call may be tried again
     * @param links      the {@code Link} header fields of the files the call wrote
     * @param raw        the child's report itself, sent as it is
     * @param body       the body otherwise
     */
    private record Reply(int status, int exitCode, Optional<Long> retryAfter, List<String> links,
                         Optional<Path> raw, Optional<Spool> body, Disk disk) {

        /**
         * Makes the reply of an outcome; an answer that does not fit its spool is not sent,
         * and the call is refused for want of room instead ({@code --max-disk}).
         */
        static Reply of(Outcome outcome, Disk disk) {
            try {
                return make(outcome, disk);
            } catch (Disk.Full full) {
                outcome.raw().ifPresent(disk::delete);
                try {
                    return make(Calls.noRoom(full), disk);
                } catch (Disk.Full impossible) {
                    // An error of a few hundred bytes stays in the heap part of a spool.
                    throw new IllegalStateException(impossible);
                }
            }
        }

        private static Reply make(Outcome outcome, Disk disk) throws Disk.Full {
            List<String> links = new ArrayList<>();
            if (outcome.structured().get("files").orElse(null) instanceof Jv.Arr files) {
                for (Jv file : files.items()) {
                    Jv.Obj description = (Jv.Obj) file;
                    links.add("<" + description.string("url").orElse("") + ">; rel=\""
                            + description.string("role").orElse("related") + "\"; type=\""
                            + description.string("mediaType").orElse("") + "\"");
                }
            }
            boolean report = outcome.ok() && outcome.raw().isPresent();
            return new Reply(report ? 200 : outcome.status().http(), outcome.exitCode(),
                    outcome.retryAfter(), links, outcome.raw(), report ? Optional.empty()
                            : Optional.of(Spool.of(outcome.structured(), true, disk)), disk);
        }

        void release() {
            raw.ifPresent(disk::delete);
            body.ifPresent(Spool::release);
        }
    }

    private void answer(HttpExchange exchange, Record record, Reply reply) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        reply.retryAfter().ifPresent(seconds -> headers.set("Retry-After",
                Long.toString(seconds)));
        reply.links().forEach(link -> headers.add("Link", link));
        if (reply.body().isEmpty()) {
            // The child's report itself, from its file: byte for byte, and never in the heap.
            Path raw = reply.raw().orElseThrow();
            long length = Files.size(raw);
            headers.set("Content-Type", JSON);
            record.status = 200;
            record.out = length;
            exchange.sendResponseHeaders(200, length);
            try (OutputStream out = exchange.getResponseBody()) {
                Files.copy(raw, out);
            }
            return;
        }
        send(exchange, record, reply.status(), reply.body().get());
    }

    private Calls.Source source(String document, String path) {
        if (document != null) {
            return new Calls.Source.Upload(document);
        }
        if (path != null) {
            return new Calls.Source.File(path);
        }
        return new Calls.Source.None();
    }

    /** Converts the values of a query parameter to the type of the parameter. */
    private static Optional<Jv> queryValue(Tools.Param param, List<String> values) {
        String last = values.get(values.size() - 1);
        return switch (param.type()) {
            case BOOLEAN -> "true".equals(last) || last.isEmpty() ? Optional.of(Jv.of(true))
                    : "false".equals(last) ? Optional.of(Jv.of(false)) : Optional.empty();
            case INTEGER -> last.matches("-?[0-9]{1,9}") ? Optional.of(new Jv.Num(last))
                    : Optional.empty();
            case STRINGS -> {
                List<Jv> items = new ArrayList<>();
                for (String value : values) {
                    for (String part : value.split(",")) {
                        if (!part.isBlank()) {
                            items.add(Jv.of(part.strip()));
                        }
                    }
                }
                yield Optional.of(Jv.array(items));
            }
            case STRING -> Optional.of(Jv.of(last));
        };
    }

    /** Splits a query string into its parameters, in order, each with all its values. */
    static Map<String, List<String>> query(String raw) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return parameters;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = decode(equals < 0 ? pair : pair.substring(0, equals));
            String value = equals < 0 ? "" : decode(pair.substring(equals + 1));
            parameters.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
        }
        return parameters;
    }

    private static String decode(String text) {
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return text;
        }
    }

    // ---------------------------------------------------------------------------------
    // MCP

    private void mcp(HttpExchange exchange, String method, Record record, String scope)
            throws IOException {
        if (!"POST".equals(method)) {
            // No server-initiated stream and no session to end: GET and DELETE are 405, which
            // the revisions name as the answer of a server that offers neither.
            notAllowed(exchange, record, "POST");
            return;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            respond(exchange, record, 415, Mcp.error(Jv.NULL, Mcp.INVALID_REQUEST,
                    "an MCP message is sent as application/json"));
            return;
        }
        String header = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
        if (header != null && !Mcp.VERSIONS.contains(header)) {
            respond(exchange, record, 400, Mcp.error(Jv.NULL, Mcp.INVALID_REQUEST,
                    "this server speaks the MCP revisions " + String.join(", ", Mcp.VERSIONS)
                            + ", opened with initialize; not "
                            + Results.safe(Results.cut(header, 40))));
            return;
        }
        String protocol = header == null ? Mcp.ASSUMED : header;
        long upload = Calls.base64Length(config.maxUpload());
        long most = mcpMost();
        if (tooLong(exchange, most, record)) {
            return;
        }
        // The message is read as it arrives: the content of an upload goes to a file, and
        // everything else is held to the bounds of Messages.
        List<Path> files = new ArrayList<>();
        try {
            Jv message;
            try (InputStream in = new Bounded(exchange.getRequestBody(), most)) {
                message = Messages.read(in, Optional.of(new Messages.Uploads(store.disk(),
                        upload, files)));
            } catch (Disk.Full full) {
                // The content of an upload, which goes to a file as it is read, did not fit.
                // What of it was written is given back before the answer, which waits for the
                // rest of the body.
                release(files);
                exchange.getResponseHeaders().set("Connection", "close");
                exchange.getResponseHeaders().set("Retry-After",
                        Long.toString(Disk.RETRY_AFTER));
                respond(exchange, record, 503, Mcp.error(Jv.NULL, Mcp.SERVER_FULL,
                        full.getMessage()));
                return;
            } catch (Bounded.Past e) {
                release(files);
                tooLarge(exchange, record, "the message is larger than the " + most
                        + " bytes this server takes");
                return;
            } catch (Messages.TooLarge e) {
                release(files);
                tooLarge(exchange, record, e.getMessage());
                return;
            } catch (Jv.JsonException e) {
                release(files);
                respond(exchange, record, 400, Mcp.error(Jv.NULL, Mcp.PARSE_ERROR,
                        e.getMessage()));
                return;
            }
            if (message instanceof Jv.Arr batch) {
                // Batches were part of 2025-03-26 and were removed in 2025-06-18.
                if (protocol.compareTo("2025-06-18") >= 0 || batch.items().isEmpty()) {
                    respond(exchange, record, 400, Mcp.error(Jv.NULL, Mcp.INVALID_REQUEST,
                            "a batch is not a message of MCP " + protocol));
                    return;
                }
                Optional<String> refused = Mcp.refused(batch);
                if (refused.isPresent()) {
                    respond(exchange, record, 400, Mcp.error(Jv.NULL, Mcp.INVALID_REQUEST,
                            refused.get()));
                    return;
                }
                // Every answer of a batch is held until the last is made: as a spool each.
                List<Jv> responses = new ArrayList<>();
                try {
                    for (Jv item : batch.items()) {
                        mcp.handle(item, protocol, scope)
                                .ifPresent(response -> responses.add(mcp.written(response)));
                    }
                    if (responses.isEmpty()) {
                        accepted(exchange, record);
                    } else {
                        message(exchange, record, Jv.array(responses));
                    }
                } finally {
                    responses.forEach(Jv::release);
                }
                return;
            }
            Optional<Jv> response = mcp.handle(message, protocol, scope);
            if (response.isEmpty()) {
                accepted(exchange, record);
                return;
            }
            try {
                message(exchange, record, response.get());
            } finally {
                Jv.release(response.get());
            }
        } finally {
            release(files);
        }
    }

    /**
     * Returns the largest MCP message this server takes, in bytes: the content of an upload
     * in base64 and the rest of a message.
     */
    private long mcpMost() {
        return Calls.base64Length(config.maxUpload()) + MCP_OVERHEAD;
    }

    /** Gives back the files of the uploads of a message. */
    private void release(List<Path> files) {
        files.forEach(store.disk()::delete);
        files.clear();
    }

    private static void accepted(HttpExchange exchange, Record record) throws IOException {
        empty(exchange, record, 202);
    }

    // ---------------------------------------------------------------------------------
    // Bodies and answers

    /**
     * Refuses a body whose declared length is past a bound, before a byte of it is read; the
     * body is not read after the answer either ({@link Linger}).
     */
    private boolean tooLong(HttpExchange exchange, long most, Record record) throws IOException {
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length == null) {
            return false;
        }
        try {
            if (Long.parseLong(length.strip()) > most) {
                tooLarge(exchange, record, "the request body is " + length.strip() + " bytes,"
                        + " more than the " + most + " this server takes (--max-upload)");
                return true;
            }
        } catch (NumberFormatException e) {
            respond(exchange, record, 400, Outcome.error(Outcome.Status.ARGUMENTS, -1,
                    "Content-Length is not a number").structured());
            return true;
        }
        return false;
    }

    /**
     * Refuses a body whose declared length does not fit beside what the server holds on
     * disk, before a byte of it is read.
     */
    private boolean noRoom(HttpExchange exchange, Record record) throws IOException {
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        if (length == null) {
            return false;
        }
        try {
            long declared = Long.parseLong(length.strip());
            if (declared > store.disk().free()) {
                full(exchange, record, new Disk.Full("the server holds at most "
                        + Disk.mib(store.disk().limit()) + " in its temporary directory"
                        + " (--max-disk) and has no room for a body of " + declared
                        + " bytes now; try again later"));
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    /** Refuses a request for want of room on the disk: 503, to be tried again later. */
    private void full(HttpExchange exchange, Record record, Disk.Full full) throws IOException {
        // The connection is closed after the answer, once the rest of the body has been read
        // and dropped, within the bounds of a lingering close (Linger).
        exchange.getResponseHeaders().set("Connection", "close");
        exchange.getResponseHeaders().set("Retry-After", Long.toString(Disk.RETRY_AFTER));
        respond(exchange, record, 503, Calls.noRoom(full).structured());
    }

    private void tooLarge(HttpExchange exchange, Record record, String message)
            throws IOException {
        // The connection is closed after the answer; the rest of a body within the bound of
        // its door is read and dropped first (Linger), one announced past it is not.
        exchange.getResponseHeaders().set("Connection", "close");
        respond(exchange, record, 413, Outcome.error(Outcome.Status.TOO_LARGE, -1, message)
                .structured());
    }

    private void notAllowed(HttpExchange exchange, Record record, String allow)
            throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        respond(exchange, record, 405, Jv.object().put("error", "method-not-allowed")
                .put("message", "this path takes " + allow).build());
    }

    private void respond(HttpExchange exchange, Record record, int status, Jv body)
            throws IOException {
        send(exchange, record, status, body, true);
    }

    /**
     * Sends a JSON body, written into a spool first: a client that reads slowly holds a
     * thread and at most {@link Spool#MEMORY} bytes of heap, not the body. A body that does
     * not fit beside what the server holds on disk is written as it is sent instead — every
     * body this server writes itself is a tree it holds anyway.
     */
    private void send(HttpExchange exchange, Record record, int status, Jv body, boolean pretty)
            throws IOException {
        Spool spool;
        try {
            spool = Spool.of(body, pretty, store.disk());
        } catch (Disk.Full full) {
            written(exchange, record, status, body, pretty);
            return;
        }
        try {
            send(exchange, record, status, spool);
        } finally {
            spool.release();
        }
    }

    /**
     * Sends an MCP message. One that carries a result is sent as it is written, without a
     * spool of its own: the result is a spool already ({@link Jv.Raw}, written while its call
     * held its child's place), and what is around it is a few hundred bytes — a second spool
     * would hold every result on the disk twice. Any other message is sent as every body is.
     */
    private void message(HttpExchange exchange, Record record, Jv message) throws IOException {
        if (Jv.carriesRaw(message)) {
            written(exchange, record, 200, message, false);
        } else {
            send(exchange, record, 200, message, false);
        }
    }

    /** Sends a JSON value as it is written, its length counted by writing it once before. */
    private static void written(HttpExchange exchange, Record record, int status, Jv body,
                                boolean pretty) throws IOException {
        Counter counter = new Counter();
        body.writeTo(counter, pretty);
        exchange.getResponseHeaders().set("Content-Type", JSON);
        record.out = counter.count;
        if ("HEAD".equals(exchange.getRequestMethod())) {
            empty(exchange, record, status);
            return;
        }
        record.status = status;
        exchange.sendResponseHeaders(status, counter.count);
        try (OutputStream out = new java.io.BufferedOutputStream(exchange.getResponseBody(),
                16 * 1024)) {
            body.writeTo(out, pretty);
        }
    }

    /** Counts the bytes written to it, and keeps none. */
    private static final class Counter extends OutputStream {
        private long count;

        @Override
        public void write(int b) {
            count++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            count += length;
        }
    }

    private static void send(HttpExchange exchange, Record record, int status, Spool body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", JSON);
        record.out = body.size();
        if ("HEAD".equals(exchange.getRequestMethod())) {
            empty(exchange, record, status);
            return;
        }
        record.status = status;
        exchange.sendResponseHeaders(status, body.size());
        try (OutputStream out = exchange.getResponseBody()) {
            body.copyTo(out);
        }
    }

    /**
     * Sends the headers of an answer without a body. The JDK's server ends the exchange as it
     * sends them, so the rest of the request body is read and dropped before.
     */
    private static void empty(HttpExchange exchange, Record record, int status)
            throws IOException {
        record.linger.beforeEmptyAnswer();
        record.status = status;
        exchange.sendResponseHeaders(status, -1);
    }

    /** Returns how long the rest of a refused body is read: a few seconds at most. */
    private Duration lingerTime() {
        return config.requestTimeout().compareTo(Linger.TIME) < 0 ? config.requestTimeout()
                : Linger.TIME;
    }

    /** A body that may carry no more than so many bytes; past them, reading fails. */
    private static final class Bounded extends java.io.FilterInputStream {

        /** The body went past its bound. */
        static final class Past extends IOException {
            private static final long serialVersionUID = 1L;

            Past() {
                super("past the bound");
            }
        }

        private final long most;
        private long read;

        Bounded(InputStream in, long most) {
            super(in);
            this.most = most;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0 && ++read > most) {
                throw new Past();
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = super.read(buffer, offset, length);
            if (count > 0) {
                read += count;
                if (read > most) {
                    throw new Past();
                }
            }
            return count;
        }
    }

    /**
     * A request body, read into a file of this process within the bound.
     *
     * @param file        the file, where the request had a body
     * @param sha256      the SHA-256 of the body
     * @param contentType the media type the request named
     * @param disk        the account the file is counted in
     */
    private record Body(Optional<Path> file, String sha256, String contentType, Disk disk) {

        static Body read(HttpExchange exchange, Disk disk, long most)
                throws IOException, Store.TooLarge {
            String contentType = Optional.ofNullable(
                    exchange.getRequestHeaders().getFirst("Content-Type")).orElse("");
            Path file = disk.newFile("body-", ".in");
            MessageDigest digest = Store.sha256();
            long count = 0;
            try (InputStream in = exchange.getRequestBody();
                 OutputStream out = disk.write(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    count += read;
                    if (count > most) {
                        throw new Store.TooLarge("the request body is larger than the " + most
                                + " bytes this server takes (--max-upload)");
                    }
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            } catch (Store.TooLarge | IOException e) {
                disk.delete(file);
                throw e;
            }
            if (count == 0) {
                disk.delete(file);
                return new Body(Optional.empty(), "", contentType, disk);
            }
            return new Body(Optional.of(file), HexFormat.of().formatHex(digest.digest()),
                    contentType, disk);
        }

        /**
         * Returns the body as the parameters of the operation, where it is a JSON object that
         * names nothing but parameters of the operation; any other body is the document.
         */
        Optional<Jv.Obj> parameters(Tools.Tool tool) {
            if (file.isEmpty() || !contentType.toLowerCase(Locale.ROOT).startsWith(JSON)) {
                return Optional.empty();
            }
            // Read within the bounds of a message, so that a body of 64 KiB of tiny values
            // is not a tree of millions of bytes.
            try (InputStream in = Files.newInputStream(file.get())) {
                if (Files.size(file.get()) > 64 * 1024) {
                    return Optional.empty();
                }
                if (Messages.read(in, Optional.empty()) instanceof Jv.Obj object
                        && object.members().keySet().stream().allMatch(name ->
                        tool.param(name).isPresent() || Tools.DOCUMENT.equals(name)
                                || Tools.PATH.equals(name))) {
                    return Optional.of(object);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (Jv.JsonException | Messages.TooLarge e) {
                return Optional.empty();
            }
            return Optional.empty();
        }

        void delete() {
            file.ifPresent(disk::delete);
        }
    }
}
