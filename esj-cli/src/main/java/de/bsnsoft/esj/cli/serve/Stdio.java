package de.bsnsoft.esj.cli.serve;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The MCP server over the standard streams: one JSON-RPC message per line in, one per line
 * out, and nothing else on the standard output.
 *
 * <p>Documents are named by local path — the client runs on the same machine and reads its
 * files anyway — and the file a tool writes goes to the {@code out} the call names. Every
 * {@code tools/call} runs on a thread of its own, so that a {@code ping} is answered while a
 * validation runs; at most {@code --max-jobs} children run at once and {@code --max-queue}
 * calls wait, and a call beyond those is answered at once with an error that says so. A
 * {@code notifications/cancelled} ends the child of the call it names, which is then not
 * answered. The server ends when the client closes the standard input, after the calls that
 * are running have been answered, and removes its temporary directory on the way out.
 */
public final class Stdio {

    /**
     * The longest line taken, in bytes: a message of {@link Messages#MAX_CHARACTERS}
     * characters, each of them escaped.
     */
    static final int MAX_LINE = 6 * Messages.MAX_CHARACTERS + 64 * 1024;

    private Stdio() {
    }

    /**
     * Serves MCP over the given streams until the input ends, handing every message to a
     * consumer as one line: for a test, which reads the messages one by one.
     *
     * @param config the settings
     * @param in     the standard input
     * @param lines  takes one line of the standard output, without its line feed
     * @return the exit code of the process
     */
    public static int run(ServeConfig config, InputStream in, Consumer<byte[]> lines) {
        return run(config, in, new OutputStream() {
            private final ByteArrayOutputStream line = new ByteArrayOutputStream();

            @Override
            public void write(int b) {
                if (b == '\n') {
                    lines.accept(line.toByteArray());
                    line.reset();
                } else {
                    line.write(b);
                }
            }

            @Override
            public void write(byte[] bytes, int offset, int length) {
                for (int i = offset; i < offset + length; i++) {
                    write(bytes[i]);
                }
            }
        });
    }

    /**
     * Serves MCP over the given streams until the input ends.
     *
     * <p>A message is written to the standard output from the spool it was written into,
     * one at a time and flushed after its line feed, so that a client that reads slowly or
     * not at all holds one message's copy buffer, and every other message waits as a spool
     * ({@link Capacity}). The result of a call is a spool already ({@link Jv.Raw}); a
     * message that carries one is written as it is, rather than copied into a second spool,
     * and so is a message that does not fit beside what the server holds on disk.
     *
     * @param config the settings
     * @param in     the standard input
     * @param out    the standard output
     * @return the exit code of the process
     */
    public static int run(ServeConfig config, InputStream in, OutputStream out) {
        config = Capacity.fit(config, Capacity.STDIO_EXTRA, Runtime.getRuntime().maxMemory(),
                config.log());
        Store store = new Store(config, Clock.systemUTC());
        Jobs jobs = new Jobs(config, store.disk());
        Thread cleanup = new Thread(() -> {
            jobs.close();
            store.close();
        }, "esj-mcp-cleanup");
        Runtime.getRuntime().addShutdownHook(cleanup);
        Calls calls = new Calls(config, store, jobs, Tools.Mode.STDIO);
        Mcp mcp = new Mcp(calls, config.version(), new Mcp.Door(
                arguments -> arguments.string(Tools.PATH)
                        .<Calls.Source>map(Calls.Source.File::new)
                        .orElse(new Calls.Source.None()),
                arguments -> calls.toPath(arguments.string(Tools.OUT))), Tools.Mode.STDIO);
        AtomicReference<String> protocol = new AtomicReference<>(Mcp.LATEST);
        Object lock = new Object();
        // One message is written at a time; the others wait as spools.
        Consumer<Jv> send = message -> {
            Spool spool = null;
            try {
                if (!Jv.carriesRaw(message)) {
                    try {
                        spool = Spool.of(message, false, store.disk());
                    } catch (Disk.Full full) {
                        spool = null;
                    }
                }
                synchronized (lock) {
                    if (spool != null) {
                        spool.copyTo(out);
                    } else {
                        message.writeTo(out, false);
                    }
                    out.write('\n');
                    out.flush();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                if (spool != null) {
                    spool.release();
                }
                Jv.release(message);
            }
        };
        ThreadPoolExecutor workers = new ThreadPoolExecutor(0,
                config.maxJobs() + config.maxQueue() + 1, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "esj-mcp-call");
                    thread.setDaemon(true);
                    return thread;
                });
        config.log().accept("esj mcp " + config.version() + ": MCP over the standard streams,"
                + " revisions " + String.join(", ", Mcp.VERSIONS));
        Capacity.report(config, Capacity.STDIO_EXTRA, store, config.log());
        try {
            Optional<byte[]> line;
            while ((line = readLine(in)).isPresent()) {
                byte[] bytes = line.get();
                if (isBlank(bytes)) {
                    continue;
                }
                if (bytes.length > MAX_LINE) {
                    send.accept(Mcp.error(Jv.NULL, Mcp.PARSE_ERROR, "the message is longer than "
                            + MAX_LINE + " bytes"));
                    continue;
                }
                Jv message;
                try {
                    message = Messages.read(new java.io.ByteArrayInputStream(bytes),
                            Optional.empty());
                } catch (Jv.JsonException e) {
                    send.accept(Mcp.error(Jv.NULL, Mcp.PARSE_ERROR, e.getMessage()));
                    continue;
                } catch (Messages.TooLarge e) {
                    send.accept(Mcp.error(Jv.NULL, Mcp.PARSE_ERROR, e.getMessage()));
                    continue;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (message instanceof Jv.Obj object
                        && "initialize".equals(object.string("method").orElse(null))
                        && object.get("params").orElse(null) instanceof Jv.Obj params) {
                    protocol.set(Mcp.negotiate(params.string("protocolVersion").orElse("")));
                }
                if (isCall(message)) {
                    Jv request = message;
                    try {
                        workers.execute(() -> handle(mcp, request, protocol.get(), send));
                    } catch (RejectedExecutionException e) {
                        Jv id = ((Jv.Obj) message).get("id").orElse(Jv.NULL);
                        send.accept(Mcp.result(id, Mcp.toolResult(Outcome.error(
                                Outcome.Status.BUSY, -1, "the server is busy: every child and"
                                        + " every place in the queue is taken"),
                                protocol.get())));
                    }
                } else {
                    handle(mcp, message, protocol.get(), send);
                }
            }
        } finally {
            workers.shutdown();
            try {
                workers.awaitTermination(config.jobTimeout().plus(Jobs.KILL_GRACE).toMillis()
                        + 5000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            jobs.close();
            store.close();
            try {
                Runtime.getRuntime().removeShutdownHook(cleanup);
            } catch (IllegalStateException e) {
                // Already shutting down: the hook runs anyway.
            }
        }
        return 0;
    }

    private static void handle(Mcp mcp, Jv message, String protocol, Consumer<Jv> send) {
        if (message instanceof Jv.Arr batch) {
            if (batch.items().isEmpty()) {
                send.accept(Mcp.error(Jv.NULL, Mcp.INVALID_REQUEST, "an empty batch"));
                return;
            }
            Optional<String> refused = Mcp.refused(batch);
            if (refused.isPresent()) {
                send.accept(Mcp.error(Jv.NULL, Mcp.INVALID_REQUEST, refused.get()));
                return;
            }
            // Every answer of a batch is held until the last is made: as a spool each.
            List<Jv> responses = new ArrayList<>();
            try {
                for (Jv item : batch.items()) {
                    mcp.handle(item, protocol, "")
                            .ifPresent(response -> responses.add(mcp.written(response)));
                }
            } catch (RuntimeException e) {
                responses.forEach(Jv::release);
                throw e;
            }
            if (!responses.isEmpty()) {
                send.accept(Jv.array(responses));
            }
            return;
        }
        mcp.handle(message, protocol, "").ifPresent(send);
    }

    /** Tells whether a message is a call of a tool, or a batch, which may run long. */
    private static boolean isCall(Jv message) {
        if (message instanceof Jv.Arr) {
            return true;
        }
        return message instanceof Jv.Obj object
                && "tools/call".equals(object.string("method").orElse(null))
                && object.get("id").isPresent();
    }

    private static boolean isBlank(byte[] bytes) {
        for (byte b : bytes) {
            if (b != ' ' && b != '\t' && b != '\r') {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads one line, without its line feed; a line longer than {@link #MAX_LINE} is read to
     * its end and returned one byte longer than the bound, so that it can be refused.
     */
    static Optional<byte[]> readLine(InputStream in) {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        boolean any = false;
        boolean over = false;
        try {
            int b;
            while ((b = in.read()) >= 0) {
                any = true;
                if (b == '\n') {
                    break;
                }
                if (line.size() <= MAX_LINE) {
                    line.write(b);
                } else {
                    over = true;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!any) {
            return Optional.empty();
        }
        return Optional.of(over ? new byte[MAX_LINE + 1] : line.toByteArray());
    }
}
