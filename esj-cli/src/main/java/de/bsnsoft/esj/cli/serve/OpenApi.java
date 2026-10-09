package de.bsnsoft.esj.cli.serve;

import java.util.ArrayList;
import java.util.List;

/**
 * The OpenAPI 3.1 description of the REST API, made from the tool table.
 *
 * <p>Every tool that reads a document is one operation, {@code POST /api/<tool>}, with its
 * parameters in the query, the document as the request body or named by {@code document},
 * and the {@code operationId} the tool's name, which is what an OpenAPI tool server client
 * such as Open WebUI offers a model. Uploading and downloading are operations for programs
 * and carry no {@code operationId}, so that such a client does not offer them to a model:
 * a model has no original bytes to upload, and a PDF is no answer for it to read.
 */
final class OpenApi {

    /** The statuses an operation of a tool may answer with beside 200. */
    private static final List<Outcome.Status> ERRORS = List.of(Outcome.Status.ARGUMENTS,
            Outcome.Status.FORBIDDEN, Outcome.Status.NOT_FOUND, Outcome.Status.TOO_LARGE,
            Outcome.Status.INPUT, Outcome.Status.INTERNAL, Outcome.Status.CRASHED,
            Outcome.Status.BUSY, Outcome.Status.LIMIT);

    private OpenApi() {
    }

    /**
     * Returns the description.
     *
     * @param calls  the calls of the HTTP front door
     * @param config the settings
     * @param token  whether the server asks for a token
     * @return the OpenAPI document
     */
    static Jv.Obj document(Calls calls, ServeConfig config, boolean token) {
        Jv.Builder paths = Jv.object();
        paths.put("/api/documents", Jv.object().put("post", upload(config)).build());
        paths.put("/api/artifacts/{id}", Jv.object().put("get", artifact()).build());
        for (Tools.Tool tool : calls.tools()) {
            if (tool.document()) {
                paths.put("/api/" + tool.name(), Jv.object()
                        .put("post", operation(tool, calls)).build());
            }
        }
        Jv.Builder document = Jv.object()
                .put("openapi", "3.1.0")
                .put("info", Jv.object()
                        .put("title", "esj")
                        .put("version", config.version())
                        .put("summary", "EN 16931 e-invoices through the semantic model.")
                        .put("description", "Validate, summarise, read, convert, render,"
                                + " extract and inspect EN 16931 e-invoices — UBL 2.1, CII D16B,"
                                + " ESJ and hybrid PDFs. Every call runs the esj command line"
                                + " tool as a process of its own. A document is the request"
                                + " body, or the id of an upload. HTTP statuses report the"
                                + " transport; the body of a 200 is the answer, a verdict"
                                + " included. Preview: this API may change in any minor"
                                + " release.")
                        .put("license", Jv.object().put("name", "Apache-2.0")
                                .put("identifier", "Apache-2.0").build())
                        .build())
                .put("paths", paths.build())
                .put("components", components(token));
        if (token) {
            document.put("security", Jv.array(List.of(Jv.object()
                    .put("bearer", Jv.array(List.of())).build())));
        }
        return document.build();
    }

    private static Jv.Obj operation(Tools.Tool tool, Calls calls) {
        List<Jv> parameters = new ArrayList<>();
        parameters.add(query(Tools.Param.text(Tools.DOCUMENT, "The id of an upload (POST"
                + " /api/documents) or of an artefact a call wrote. Give this, path, or the"
                + " document as the request body.", false)));
        if (calls.takesPaths()) {
            parameters.add(query(Tools.Param.text(Tools.PATH, "A file inside a directory the"
                    + " operator allowed (--allow-dir).", false)));
        }
        for (Tools.Param param : tool.params()) {
            parameters.add(query(param));
        }
        Jv.Builder responses = Jv.object()
                .put("200", Jv.object()
                        .put("description", "validate".equals(tool.name())
                                ? "The report of esj validate --output json, byte for byte."
                                : "The result.")
                        .put("content", Jv.object().put("application/json", Jv.object()
                                .put("schema", Jv.object().put("type", "object").build())
                                .build()).build())
                        .build());
        for (Outcome.Status status : ERRORS) {
            responses.put(Integer.toString(status.http()), error(describe(status)));
        }
        return Jv.object()
                .put("operationId", tool.name())
                .put("summary", tool.title())
                .put("description", tool.description())
                .put("parameters", Jv.array(parameters))
                .put("requestBody", Jv.object()
                        .put("required", false)
                        .put("description", "The document: UBL, CII, ESJ or a PDF, as its"
                                + " bytes. A JSON object that names only parameters of this"
                                + " operation is taken as those parameters instead.")
                        .put("content", Jv.object().put("application/octet-stream", Jv.object()
                                .put("schema", Jv.object().put("type", "string")
                                        .put("contentMediaType", "application/octet-stream")
                                        .build())
                                .build()).build())
                        .build())
                .put("responses", responses.build())
                .build();
    }

    private static String describe(Outcome.Status status) {
        return switch (status) {
            case ARGUMENTS -> "The parameters do not fit the operation.";
            case FORBIDDEN -> "The path lies outside the allowed directories.";
            case NOT_FOUND -> "The upload or the file is not there, or has expired.";
            case TOO_LARGE -> "The document is larger than --max-upload.";
            case INPUT -> "The document could not be read, recognized or parsed, or needs a"
                    + " feature this version does not implement (exit codes 2 and 4).";
            case INTERNAL -> "An internal error (exit codes 5 and 6).";
            case CRASHED -> "The child process ended with a code outside the table.";
            case BUSY -> "Every child is busy and the queue is full, or the store is full;"
                    + " see Retry-After.";
            case LIMIT -> "No verdict: the child reached a resource bound or its deadline,"
                    + " or ran out of heap (exit codes 7 and 3).";
            default -> status.name();
        };
    }

    private static Jv query(Tools.Param param) {
        Jv.Builder parameter = Jv.object()
                .put("name", param.name())
                .put("in", "query")
                .put("required", param.required())
                .put("description", param.description())
                .put("schema", Schemas.property(param));
        if (param.type() == Tools.Type.STRINGS) {
            parameter.put("style", "form").put("explode", true);
        }
        return parameter.build();
    }

    private static Jv.Obj upload(ServeConfig config) {
        return Jv.object()
                .put("summary", "Upload a document")
                .put("description", "Stores a document for --ttl and returns its id, its"
                        + " SHA-256 and its length; at most " + config.maxUpload() + " bytes.")
                .put("requestBody", Jv.object()
                        .put("required", true)
                        .put("content", Jv.object().put("application/octet-stream", Jv.object()
                                .put("schema", Jv.object().put("type", "string")
                                        .put("contentMediaType", "application/octet-stream")
                                        .build())
                                .build()).build())
                        .build())
                .put("responses", Jv.object()
                        .put("201", Jv.object()
                                .put("description", "Stored.")
                                .put("content", Jv.object().put("application/json", Jv.object()
                                        .put("schema", ref("Upload")).build()).build())
                                .build())
                        .put("413", error("The document is larger than --max-upload."))
                        .put("503", error("The store is full; see Retry-After."))
                        .build())
                .build();
    }

    private static Jv.Obj artifact() {
        return Jv.object()
                .put("summary", "Download an artefact")
                .put("description", "A file a call wrote — a rendering, a report, a converted"
                        + " document — kept for --ttl.")
                .put("parameters", Jv.array(List.of(Jv.object()
                        .put("name", "id")
                        .put("in", "path")
                        .put("required", true)
                        .put("schema", Jv.object().put("type", "string")
                                .put("pattern", "^[0-9a-f]{32}$").build())
                        .build())))
                .put("responses", Jv.object()
                        .put("200", Jv.object()
                                .put("description", "The file.")
                                .put("content", Jv.object()
                                        .put("application/pdf", Jv.object().build())
                                        .put("text/html", Jv.object().build())
                                        .put("application/xml", Jv.object().build())
                                        .put("application/json", Jv.object().build())
                                        .build())
                                .build())
                        .put("404", error("No such artefact, or it has expired."))
                        .build())
                .build();
    }

    private static Jv.Obj error(String description) {
        return Jv.object()
                .put("description", description)
                .put("content", Jv.object().put("application/json", Jv.object()
                        .put("schema", ref("Error")).build()).build())
                .build();
    }

    private static Jv.Obj ref(String name) {
        return Jv.object().put("$ref", "#/components/schemas/" + name).build();
    }

    private static Jv.Obj components(boolean token) {
        Jv.Builder components = Jv.object().put("schemas", Jv.object()
                .put("Error", Jv.object()
                        .put("type", "object")
                        .put("required", Jv.array(List.of(Jv.of("error"), Jv.of("message"))))
                        .put("properties", Jv.object()
                                .put("error", Jv.object().put("type", "string").build())
                                .put("message", Jv.object().put("type", "string").build())
                                .put("exitCode", Jv.object().put("type",
                                        Jv.array(List.of(Jv.of("integer"), Jv.of("null"))))
                                        .build())
                                .build())
                        .build())
                .put("Upload", Jv.object()
                        .put("type", "object")
                        .put("required", Jv.array(List.of(Jv.of("id"), Jv.of("sha256"),
                                Jv.of("bytes"), Jv.of("expires"))))
                        .put("properties", Jv.object()
                                .put("id", Jv.object().put("type", "string").build())
                                .put("sha256", Jv.object().put("type", "string").build())
                                .put("bytes", Jv.object().put("type", "integer").build())
                                .put("expires", Jv.object().put("type", "string")
                                        .put("format", "date-time").build())
                                .build())
                        .build())
                .build());
        if (token) {
            components.put("securitySchemes", Jv.object().put("bearer", Jv.object()
                    .put("type", "http").put("scheme", "bearer").build()).build());
        }
        return components.build();
    }
}
