package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.cli.serve.Http;
import de.bsnsoft.esj.cli.serve.ServeConfig;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * {@code esj serve}: the tools of this command line as an HTTP server — a REST API with its
 * OpenAPI description, and an MCP server over Streamable HTTP.
 *
 * <p>The server reads no document itself. Every call starts this tool again as a child
 * process with a heap ceiling and a deadline, so that a document that exhausts the one or
 * the other costs that call and never the server ({@code docs/deployment.md}). It runs
 * until it is stopped; on {@code SIGTERM} it takes no new requests, ends the children that
 * run and removes its temporary directory.
 */
@Command(name = "serve",
        description = "Serve the tools of esj over HTTP: a REST API under /api with its"
                + " OpenAPI description at /openapi.json, and MCP (Streamable HTTP) at /mcp."
                + " Every call runs esj as a child process with --job-heap and"
                + " --job-timeout. Preview. See docs/serve.md.",
        sortOptions = false,
        mixinStandardHelpOptions = false)
final class ServeCommand implements Callable<Integer> {

    @Option(order = 1000, names = {"-h", "--help"}, usageHelp = true,
            description = "Show this help text and exit.")
    private boolean helpRequested;

    @Option(order = 10, names = "--bind", paramLabel = "<address>",
            description = "The address to listen on. Default: ESJ_SERVE_BIND, or 127.0.0.1;"
                    + " the container images set ESJ_SERVE_BIND to 0.0.0.0.")
    private String bind;

    @Option(order = 20, names = "--port", paramLabel = "<port>", defaultValue = "8080",
            description = "The port to listen on; 0 lets the system choose. Default: 8080.")
    private int port;

    @Option(order = 30, names = "--token-file", paramLabel = "<file>",
            description = "A file holding the bearer token every request to /api and /mcp"
                    + " has to carry. Default: ESJ_TOKEN_FILE. The token is never taken as an"
                    + " argument, which every process of the machine could read.")
    private Path tokenFile;

    @Option(order = 40, names = "--allow-origin", paramLabel = "<origin>",
            description = "A browser origin allowed to call this server beside loopback ones,"
                    + " such as https://chat.example.com, and given CORS headers; repeatable.")
    private List<String> allowOrigins = new ArrayList<>();

    @Option(order = 50, names = "--public-url", paramLabel = "<url>",
            description = "The URL clients reach this server at, for the links to artefacts."
                    + " Default: the Host of a request where it is a loopback name or the bind"
                    + " address with this port, the host of an --allow-origin or an"
                    + " --allow-host; else a link is the path on this server.")
    private String publicUrl;

    @Option(order = 55, names = "--allow-host", paramLabel = "<host[:port]>",
            description = "A Host header, such as esj-api:8080, from which absolute links are"
                    + " built; repeatable. A Host that is none of these gives links that are"
                    + " paths on this server, never a URL that header names.")
    private List<String> allowHosts = new ArrayList<>();

    @Mixin
    private JobFlags jobs;

    @Option(order = 300, names = "--request-timeout", paramLabel = "<duration>",
            converter = Numbers.Runtime.class,
            description = "How long a request may take to arrive, headers and body. Default:"
                    + " 60s.")
    private Duration requestTimeout = ServeConfig.DEFAULT_REQUEST_TIMEOUT;

    @Option(order = 310, names = "--ttl", paramLabel = "<duration>",
            converter = Numbers.Runtime.class,
            description = "How long an upload or an artefact is kept. Default: 15m.")
    private Duration ttl = ServeConfig.DEFAULT_TTL;

    @Option(order = 320, names = "--max-stored", paramLabel = "<count>",
            description = "How many uploads and artefacts are kept at once. Default: 256.")
    private int maxStored = ServeConfig.DEFAULT_MAX_STORED;

    @Option(order = 330, names = "--max-stored-bytes", paramLabel = "<bytes>",
            converter = Numbers.ByteCount.class,
            description = "How many bytes of them are kept at once, within --max-disk."
                    + " Default: 256M.")
    private long maxStoredBytes = ServeConfig.DEFAULT_MAX_STORED_BYTES;

    private final Console console;

    ServeCommand(Console console) {
        this.console = console;
    }

    @Override
    public Integer call() throws IOException, InterruptedException {
        ServeConfig config = config(console, VersionProvider.artifactVersion());
        String address = bind != null ? bind
                : console.options().environment("ESJ_SERVE_BIND").orElse("127.0.0.1");
        Optional<Path> file = Optional.ofNullable(tokenFile).or(() -> console.options()
                .environment("ESJ_TOKEN_FILE").filter(name -> !name.isEmpty()).map(Path::of));
        Http server;
        try {
            config = jobs.apply(config.withListen(address, port)
                    .withToken(file.map(ServeCommand::token))
                    .withAccess(List.copyOf(allowOrigins), List.of(), Optional.empty(),
                            List.of(), Optional.ofNullable(publicUrl))
                    .withHosts(List.copyOf(allowHosts))
                    .withStorage(ServeConfig.DEFAULT_MAX_UPLOAD, requestTimeout, ttl, maxStored,
                            maxStoredBytes, Path.of(System.getProperty("java.io.tmpdir"))));
            server = Http.start(config);
        } catch (IllegalArgumentException e) {
            throw CliException.input(e.getMessage(), e);
        } catch (IOException e) {
            throw CliException.input("cannot listen on " + address + ":" + port + ": "
                    + e.getMessage(), e);
        }
        server.await();
        return ExitCode.SUCCESS;
    }

    /** Builds the settings every command of this kind starts from. */
    static ServeConfig config(Console console, String version) {
        PrintWriter writer = console.errWriter();
        Consumer<String> log = line -> {
            synchronized (writer) {
                writer.println(Console.terminalSafe(line));
                writer.flush();
            }
        };
        return ServeConfig.defaults(version, log)
                .withProcess(List.of(), console.options().environment(), "default", log);
    }

    /** Reads the token from its file. */
    private static String token(Path file) {
        String token;
        try {
            token = Files.readString(file, StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw CliException.input("the token file " + file + " cannot be read: "
                    + e.getMessage(), e);
        }
        if (token.isEmpty() || token.length() > 4096 || token.chars().anyMatch(c -> c <= ' ')) {
            throw CliException.input("the token file " + file + " holds no token: one line of"
                    + " 1 to 4096 characters without spaces");
        }
        return token;
    }
}
