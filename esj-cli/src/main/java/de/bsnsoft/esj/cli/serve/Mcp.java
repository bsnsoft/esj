package de.bsnsoft.esj.cli.serve;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The Model Context Protocol: JSON-RPC 2.0 messages in, JSON-RPC 2.0 messages out, for both
 * transports.
 *
 * <p>This server speaks the revisions that open with an {@code initialize} handshake — the
 * newest of them, {@value #LATEST}, and the three before it — and answers {@code initialize},
 * {@code ping}, {@code tools/list} and {@code tools/call}; every notification is taken and
 * none is answered. It keeps no session: every request is answered from itself and the
 * settings of the process, which the revisions allow over HTTP ({@code Mcp-Session-Id} is
 * optional, and a server that issues none is never sent a {@code GET} stream or a
 * {@code DELETE}). What differs between the revisions and reaches a result is gated on the
 * one the client asked for: {@code structuredContent} and {@code resource_link} only from
 * 2025-06-18.
 *
 * <p>A request in the per-request form of 2026-07-28 is not served: {@code server/discover}
 * is an unknown method here, which tells a client that speaks both forms to fall back to
 * {@code initialize}, as that revision prescribes.
 *
 * <p>A {@code notifications/cancelled} ends the child of the call it names. Over the standard
 * streams a request id names one call, and the cancelled call is not answered, as the
 * revisions ask. Over HTTP, without a session, a request id names a call only together with
 * who sent it: a cancellation reaches the call of the same request id from the same address
 * with the same {@code Authorization}, and only where exactly one such call runs; the
 * cancelled request is answered with an error, because an HTTP request has to be answered.
 *
 * <p>Every text a result or an error carries for a model is written with
 * {@link Visible#text(String)}: a character a reader cannot see stands as its escape.
 */
final class Mcp {

    /** The newest revision this server speaks. */
    static final String LATEST = "2025-11-25";

    /** The revisions this server speaks, the newest first. */
    static final List<String> VERSIONS = List.of(LATEST, "2025-06-18", "2025-03-26",
            "2024-11-05");

    /** The revision a client that names none is taken to speak, over HTTP. */
    static final String ASSUMED = "2025-03-26";

    /** The most messages of one batch ({@link #refused(Jv.Arr)}). */
    static final int BATCH = 16;

    /** The first revision with {@code structuredContent} and {@code resource_link}. */
    private static final String STRUCTURED = "2025-06-18";

    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;
    static final int INTERNAL_ERROR = -32603;
    /** The error of a request the client cancelled, as the Language Server Protocol names it. */
    static final int REQUEST_CANCELLED = -32800;
    /**
     * The error of a message the server has no room for on its disk ({@code --max-disk}),
     * one of the codes JSON-RPC leaves to a server; over HTTP it comes with 503 and
     * {@code Retry-After}.
     */
    static final int SERVER_FULL = -32000;

    /**
     * How a call of this transport finds its document and delivers its files.
     *
     * @param source   the document of a call, from its arguments
     * @param delivery how the files of a call reach the caller, from its arguments
     */
    record Door(Function<Jv.Obj, Calls.Source> source,
                Function<Jv.Obj, Calls.Delivery> delivery) {
    }

    private final Calls calls;
    private final String version;
    private final Door door;
    private final Tools.Mode mode;

    /** The calls that run, by who sent them and their request id. */
    private final Map<String, Jobs.Cancel> running = new ConcurrentHashMap<>();

    /**
     * Prepares the protocol of one transport.
     *
     * @param calls   the calls of its front door
     * @param version the version of the tool
     * @param door    how a call finds its document and delivers its files
     * @param mode    the transport
     */
    Mcp(Calls calls, String version, Door door, Tools.Mode mode) {
        this.calls = calls;
        this.version = version;
        this.door = door;
        this.mode = mode;
    }

    /**
     * Answers one message.
     *
     * @param message  the message
     * @param protocol the revision the client speaks, as far as the transport knows it
     * @param scope    who sent it, as far as the transport tells callers apart: a request id
     *                 names a call within its scope
     * @return the response, or nothing for a notification, a response or a cancelled call
     */
    Optional<Jv> handle(Jv message, String protocol, String scope) {
        if (!(message instanceof Jv.Obj object)) {
            return Optional.of(error(Jv.NULL, INVALID_REQUEST, "a message is a JSON object"));
        }
        Jv id = object.get("id").orElse(null);
        Optional<String> method = object.string("method");
        if (!"2.0".equals(object.string("jsonrpc").orElse(null))) {
            return Optional.of(error(id == null ? Jv.NULL : id, INVALID_REQUEST,
                    "jsonrpc must be \"2.0\""));
        }
        if (method.isEmpty()) {
            if (object.get("result").isPresent() || object.get("error").isPresent()) {
                return Optional.empty();
            }
            return Optional.of(error(id == null ? Jv.NULL : id, INVALID_REQUEST,
                    "a request names a method"));
        }
        if (id == null) {
            if ("notifications/cancelled".equals(method.get())
                    && object.get("params").orElse(null) instanceof Jv.Obj params) {
                params.get("requestId").ifPresent(requestId -> cancel(scope, requestId));
            }
            return Optional.empty();
        }
        if (!(id instanceof Jv.Str || id instanceof Jv.Num)) {
            return Optional.of(error(Jv.NULL, INVALID_REQUEST, "an id is a string or a number"));
        }
        Jv params = object.get("params").orElse(Jv.object().build());
        if (!(params instanceof Jv.Obj parameters)) {
            return Optional.of(error(id, INVALID_PARAMS, "params is an object"));
        }
        try {
            return Optional.ofNullable(switch (method.get()) {
                case "initialize" -> result(id, initialize(parameters));
                case "ping" -> result(id, Jv.object().build());
                case "tools/list" -> result(id, list());
                case "tools/call" -> call(id, parameters, protocol, scope);
                default -> error(id, METHOD_NOT_FOUND, "this server has no method "
                        + Results.safe(Results.cut(method.get(), 80)) + "; it answers"
                        + " initialize, ping, tools/list and tools/call");
            });
        } catch (RuntimeException e) {
            return Optional.of(error(id, INTERNAL_ERROR, "internal error: " + e));
        }
    }

    /**
     * Returns the revision a negotiation comes to: the one the client asked for where this
     * server speaks it, and the newest this server speaks otherwise.
     *
     * @param requested the revision the client asked for
     * @return the revision of the session
     */
    static String negotiate(String requested) {
        return VERSIONS.contains(requested) ? requested : LATEST;
    }

    private Jv initialize(Jv.Obj params) {
        String negotiated = negotiate(params.string("protocolVersion").orElse(""));
        return Jv.object()
                .put("protocolVersion", negotiated)
                .put("capabilities", Jv.object()
                        .put("tools", Jv.object().put("listChanged", false).build())
                        .build())
                .put("serverInfo", Jv.object()
                        .put("name", "esj")
                        .put("title", "EN16931 Semantic JSON")
                        .put("version", version)
                        .build())
                .put("instructions", instructions())
                .build();
    }

    private String instructions() {
        return "Tools over EN 16931 electronic invoices: UBL 2.1, CII D16B, ESJ (EN16931"
                + " Semantic JSON) and hybrid PDFs such as ZUGFeRD and Factur-X. Every tool"
                + " reads the document as a file: "
                + (mode == Tools.Mode.STDIO ? "name it with path"
                        : "name an upload with document")
                + ". The verdict of validate is about the bytes of that file, so give the"
                + " unchanged original and never type, reconstruct or edit an invoice. Each"
                + " call starts esj as a process of its own with a heap ceiling and a time"
                + " limit; a limit gives no verdict, never INVALID.";
    }

    /** Returns the tool list of this transport. */
    Jv.Obj list() {
        List<Jv> tools = new ArrayList<>();
        for (Tools.Tool tool : calls.tools()) {
            tools.add(Jv.object()
                    .put("name", tool.name())
                    .put("title", tool.title())
                    .put("description", tool.description())
                    .put("inputSchema", Schemas.input(tool, frontDoor(tool)))
                    .put("annotations", Jv.object()
                            .put("title", tool.title())
                            .put("readOnlyHint", !"upload".equals(tool.name()))
                            .put("destructiveHint", false)
                            .put("idempotentHint", true)
                            .put("openWorldHint", false)
                            .build())
                    .build());
        }
        return Jv.object().put("tools", Jv.array(tools)).build();
    }

    /** Returns the parameters this transport adds to a tool. */
    List<Tools.Param> frontDoor(Tools.Tool tool) {
        List<Tools.Param> params = new ArrayList<>();
        if (!tool.document()) {
            return params;
        }
        if (mode == Tools.Mode.STDIO) {
            params.add(Tools.Param.text(Tools.PATH, "The file to read: an invoice as UBL, CII"
                    + " or ESJ, or a PDF carrying one. Relative to the working directory of"
                    + " the server.", true));
            String out = switch (tool.name()) {
                case "validate" -> "Where to write the report; needed with report pdf or html.";
                case "render" -> "Where to write the rendering.";
                case "convert" -> "Where to write the converted document. Without it, a"
                        + " document of up to 64 KiB is carried in the answer.";
                case "extract" -> "Where to write the invoice. Without it, an invoice of up to"
                        + " 64 KiB is carried in the answer.";
                default -> null;
            };
            if (out != null) {
                params.add(Tools.Param.text(Tools.OUT, out, "render".equals(tool.name())));
            }
            return params;
        }
        boolean paths = calls.takesPaths();
        params.add(Tools.Param.text(Tools.DOCUMENT, "The id of an uploaded document — the"
                + " result of upload, or of POST /api/documents — or of a file a call wrote,"
                + " such as the hybrid PDF of render." + (paths ? " Give this or path." : ""),
                !paths));
        if (paths) {
            params.add(Tools.Param.text(Tools.PATH, "A file inside a directory the operator of"
                    + " this server allowed. Give this or document.", false));
        }
        return params;
    }

    /** Cancels the call a request id names within a scope, where exactly one does. */
    private void cancel(String scope, Jv requestId) {
        Jobs.Cancel cancel = running.get(key(scope, requestId));
        if (cancel != null) {
            cancel.cancel();
        }
    }

    private static String key(String scope, Jv id) {
        return scope + "\u0000" + new String(id.toBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Runs a call of a tool, where it can be cancelled.
     *
     * @return its result, or {@code null} for a call that was cancelled and is not answered
     */
    private Jv call(Jv id, Jv.Obj params, String protocol, String scope) {
        String key = key(scope, id);
        Jobs.Cancel cancel = new Jobs.Cancel();
        // Two calls of one id in one scope cannot be told apart: neither can be cancelled.
        if (running.putIfAbsent(key, cancel) != null) {
            running.put(key, Jobs.Cancel.NEVER);
        }
        try {
            return call(id, params, protocol, cancel);
        } finally {
            if (!running.remove(key, cancel)) {
                running.remove(key, Jobs.Cancel.NEVER);
            }
        }
    }

    private Jv call(Jv id, Jv.Obj params, String protocol, Jobs.Cancel cancel) {
        Optional<String> name = params.string("name");
        if (name.isEmpty()) {
            return error(id, INVALID_PARAMS, "tools/call names a tool");
        }
        Optional<Tools.Tool> tool = Tools.find(calls.tools(), name.get());
        if (tool.isEmpty()) {
            return error(id, INVALID_PARAMS, "unknown tool: "
                    + Results.safe(Results.cut(name.get(), 80)));
        }
        Jv given = params.get("arguments").orElse(Jv.object().build());
        Outcome outcome;
        if (!(given instanceof Jv.Obj arguments)) {
            outcome = Outcome.error(Outcome.Status.ARGUMENTS, -1, "arguments is an object");
        } else {
            Jv.Builder own = Jv.object();
            for (var member : arguments.members().entrySet()) {
                if (!Calls.frontDoor().contains(member.getKey())) {
                    own.put(member.getKey(), member.getValue());
                }
            }
            List<String> taken = frontDoor(tool.get()).stream().map(Tools.Param::name).toList();
            Optional<String> stray = arguments.members().keySet().stream()
                    .filter(key -> Calls.frontDoor().contains(key) && !taken.contains(key))
                    .findFirst();
            boolean reportWithoutOut = mode == Tools.Mode.STDIO
                    && "validate".equals(tool.get().name())
                    && arguments.get(Tools.OUT).isEmpty()
                    && arguments.string("report")
                            .filter(report -> "pdf".equals(report) || "html".equals(report))
                            .isPresent();
            if (stray.isPresent()) {
                outcome = Outcome.error(Outcome.Status.ARGUMENTS, -1, tool.get().name()
                        + " takes no parameter " + stray.get() + " over this transport");
            } else if (reportWithoutOut) {
                outcome = Outcome.error(Outcome.Status.ARGUMENTS, -1, "validate with a report"
                        + " needs out, the file to write the report to");
            } else {
                // The result is shaped and written while the call holds its child's place:
                // what leaves the call is a spool, not a tree (Calls#call).
                Optional<Jv> written = calls.call(tool.get(), own.build(),
                        door.source().apply(arguments), door.delivery().apply(arguments),
                        Calls.Options.mcp(mode == Tools.Mode.HTTP, cancel),
                        done -> done.status() == Outcome.Status.CANCELLED ? Optional.empty()
                                : Optional.of(writtenResult(done, protocol)));
                if (written.isEmpty()) {
                    return mode == Tools.Mode.STDIO ? null
                            : error(id, REQUEST_CANCELLED, "the request was cancelled");
                }
                return result(id, written.get());
            }
        }
        return result(id, toolResult(outcome, protocol));
    }

    /**
     * Returns the result of a call written into a spool of this server, while the call holds
     * its child's place. A result that does not fit beside what the server holds on disk is
     * not kept: the call is refused for want of room ({@code --max-disk}) instead, and that
     * result is a few hundred bytes.
     *
     * @param outcome  what the call came to
     * @param protocol the revision the client speaks
     * @return the result as a {@link Jv.Raw}, or the refusal
     */
    Jv writtenResult(Outcome outcome, String protocol) {
        try {
            return new Jv.Raw(Spool.of(toolResult(outcome, protocol), false,
                    calls.store().disk()));
        } catch (Disk.Full full) {
            outcome.raw().ifPresent(calls.store().disk()::delete);
            return toolResult(Calls.noRoom(full), protocol);
        }
    }

    /**
     * Returns a message written into a spool of this server: what a batch keeps of a message
     * until its last one is made. A message that carries a written result already is kept as
     * it is — its result is a spool, and what is around it a few hundred bytes — and so is
     * one that does not fit beside what the server holds on disk: every other message is
     * one this server wrote itself, of a size that does not follow a document.
     *
     * @param value the message
     * @return the message, as a {@link Jv.Raw} where it was spooled
     */
    Jv written(Jv value) {
        if (Jv.carriesRaw(value)) {
            return value;
        }
        try {
            Spool spool = Spool.of(value, false, calls.store().disk());
            return new Jv.Raw(spool);
        } catch (Disk.Full full) {
            return value;
        }
    }

    /**
     * Tells why a batch is refused, where it is: a batch carries at most {@link #BATCH}
     * messages, and at most one of them calls a tool. A batch is answered as one message
     * once every message of it is, so every answer of it is held until the last; a call of a
     * tool is the one answer that can be large and take long, and its answer is held as a
     * spool ({@link #written(Jv)}).
     *
     * @param batch the batch
     * @return the reason, or nothing where the batch is taken
     */
    static Optional<String> refused(Jv.Arr batch) {
        long calls = batch.items().stream().filter(item -> item instanceof Jv.Obj object
                && "tools/call".equals(object.string("method").orElse(null))).count();
        if (batch.items().size() > BATCH || calls > 1) {
            return Optional.of("a batch carries at most " + BATCH + " messages and at most one"
                    + " tools/call; send the others as messages of their own");
        }
        return Optional.empty();
    }

    /**
     * Returns the result of {@code tools/call} for an outcome.
     *
     * @param outcome  the outcome
     * @param protocol the revision the client speaks
     * @return the result
     */
    static Jv.Obj toolResult(Outcome outcome, String protocol) {
        boolean structured = protocol.compareTo(STRUCTURED) >= 0;
        List<Jv> content = new ArrayList<>();
        content.add(text(outcome.text()));
        // The same data as text, because a client that reads no structured content reads
        // this; Open WebUI hands a model the content and nothing else.
        content.add(text(new String(outcome.structured().toBytes(), StandardCharsets.UTF_8)));
        if (structured && outcome.structured().get("files").orElse(null) instanceof Jv.Arr files) {
            for (Jv file : files.items()) {
                Jv.Obj description = (Jv.Obj) file;
                // A resource link names a URI, which a path on this server is not: where the
                // server does not know the address it was reached at, the path stands in the
                // text and the structured result only.
                Optional<String> url = description.string("url")
                        .filter(link -> link.startsWith("http://") || link.startsWith("https://"));
                if (url.isPresent()) {
                    content.add(Jv.object()
                            .put("type", "resource_link")
                            .put("uri", url.get())
                            .put("name", description.string("name").orElse("file"))
                            .put("mimeType", description.get("mediaType").orElse(Jv.NULL))
                            .put("size", description.get("bytes").orElse(Jv.NULL))
                            .build());
                }
            }
        }
        Jv.Builder result = Jv.object().put("content", Jv.array(content));
        if (structured) {
            result.put("structuredContent", outcome.structured());
        }
        result.put("isError", !outcome.ok());
        return result.build();
    }

    private static Jv text(String text) {
        return Jv.object().put("type", "text").put("text", Visible.text(text)).build();
    }

    static Jv result(Jv id, Jv result) {
        return Jv.object().put("jsonrpc", "2.0").put("id", id).put("result", result).build();
    }

    static Jv error(Jv id, int code, String message) {
        return Jv.object().put("jsonrpc", "2.0").put("id", id)
                .put("error", Jv.object().put("code", code).put("message", Visible.text(message))
                        .build())
                .build();
    }
}
