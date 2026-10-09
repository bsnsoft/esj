package de.bsnsoft.esj.cli.serve;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The tools, once: their names, their descriptions, their parameters and the command of
 * {@code esj} each becomes.
 *
 * <p>The REST API, the MCP server over HTTP and the MCP server over the standard streams are
 * three front doors to this one table. A front door adds how a document reaches the tool —
 * the request body or the identifier of an upload over HTTP, a local path over the standard
 * streams — and how a file the tool writes reaches the caller; everything else is here, and
 * {@link Calls} runs it. What a tool says about an invoice is never this table's: it is the
 * report of the child process, passed on, cut where it is long and never reinterpreted.
 */
final class Tools {

    /** How a front door reaches a tool. */
    enum Mode {
        /** The REST API and the MCP server over HTTP: documents by upload or body. */
        HTTP,
        /** The MCP server over the standard streams: documents and results by local path. */
        STDIO
    }

    /** The type of a parameter, as JSON Schema names it. */
    enum Type {
        /** A string. */
        STRING("string"),
        /** {@code true} or {@code false}. */
        BOOLEAN("boolean"),
        /** A whole number. */
        INTEGER("integer"),
        /** An array of strings. */
        STRINGS("array");

        private final String schema;

        Type(String schema) {
            this.schema = schema;
        }

        String schema() {
            return schema;
        }
    }

    /**
     * A parameter of a tool.
     *
     * @param name        its name, the same over every front door
     * @param type        its type
     * @param description what it does, for a person and for a model
     * @param choices     the values it takes, where there is a closed list
     * @param fallback    the value it has where none is given
     * @param required    whether it must be given
     */
    record Param(String name, Type type, String description, List<String> choices,
                 Optional<String> fallback, boolean required) {

        Param {
            choices = List.copyOf(choices);
        }

        static Param choice(String name, String description, String fallback,
                            String... choices) {
            return new Param(name, Type.STRING, description, List.of(choices),
                    Optional.of(fallback), false);
        }

        static Param flag(String name, String description) {
            return new Param(name, Type.BOOLEAN, description, List.of(), Optional.of("false"),
                    false);
        }

        static Param text(String name, String description, boolean required) {
            return new Param(name, Type.STRING, description, List.of(), Optional.empty(),
                    required);
        }
    }

    /** A file a tool writes, as the child names it in its working directory. */
    record Output(String role, String file, String mediaType) {
    }

    /**
     * One run of {@code esj}: its arguments and the files it is expected to write.
     *
     * @param arguments the command and its arguments; the document is the standard input
     * @param outputs   the files it writes
     */
    record Plan(List<String> arguments, List<Output> outputs) {
        Plan {
            arguments = List.copyOf(arguments);
            outputs = List.copyOf(outputs);
        }
    }

    /**
     * One tool.
     *
     * @param name        its name: the MCP tool, the REST path {@code /api/<name>} and the
     *                    OpenAPI {@code operationId}
     * @param title       its title, for a person
     * @param description what it does, for a person and for a model
     * @param params      its own parameters, beside the ones of the front door
     * @param document    whether it reads a document
     * @param httpOnly    whether only the HTTP front doors offer it
     * @param writes      the roles of the files it may write, which take an {@code out}
     *                    over the standard streams
     */
    record Tool(String name, String title, String description, List<Param> params,
                boolean document, boolean httpOnly, List<String> writes) {
        Tool {
            params = List.copyOf(params);
            writes = List.copyOf(writes);
        }

        Optional<Param> param(String parameter) {
            return params.stream().filter(p -> p.name().equals(parameter)).findFirst();
        }
    }

    /** The parameter of an uploaded document. */
    static final String DOCUMENT = "document";

    /** The parameter of a local or allowed file. */
    static final String PATH = "path";

    /** The parameter of the file a call writes, over the standard streams. */
    static final String OUT = "out";

    /** A business term path, as the {@code get} tool takes one. */
    static final Pattern SEMANTIC_PATH =
            Pattern.compile("(/(BG|BT)-[A-Za-z0-9]+(-[A-Za-z0-9]+)*(/([0-9]+|\\*))?)+");

    /** The most paths one {@code get} takes. */
    static final int MAX_PATHS = 64;

    /** What a template name may be. */
    static final Pattern TEMPLATE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final Param EXTENSION = new Param("extension", Type.STRINGS,
            "Extension registries to load, so that their terms are read rather than reported"
                    + " as unknown: xrechnung (the sub invoice lines of XRechnung), b2c (the gross"
                    + " figures a consumer was shown).",
            List.of("xrechnung", "b2c"), Optional.empty(), false);

    private static final Param LANG = Param.choice("lang",
            "The language of the labels, dates and decimal separator of a rendering or report.",
            "de", "de", "en");

    private Tools() {
    }

    /**
     * Returns the tools a front door offers.
     *
     * @param mode      the front door
     * @param templates the names of the templates of {@code --templates}
     * @return the tools, in the order they are listed
     */
    static List<Tool> all(Mode mode, List<String> templates) {
        List<Tool> tools = new ArrayList<>();
        tools.add(new Tool("validate", "Validate an e-invoice",
                "Validate an EN 16931 e-invoice — UBL 2.1, UN/CEFACT CII D16B, ESJ, or a hybrid"
                        + " PDF (ZUGFeRD 2, Factur-X) — with the official XML Schema and"
                        + " Schematron of its profile and the business rules of EN 16931."
                        + " Returns the verdict VALID, INVALID or INDETERMINATE, the reasons for"
                        + " an INDETERMINATE, and the most severe findings with rule, path,"
                        + " severity and message; report=pdf or html adds the validation report"
                        + " as a file. The verdict is about the bytes of the file: give the"
                        + " unchanged original, never a retyped invoice.",
                List.of(Param.choice("report", "Also write the validation report as a file.",
                                "none", "none", "pdf", "html"),
                        LANG, EXTENSION),
                true, false, List.of("report")));
        tools.add(new Tool("summary", "Summarise an e-invoice",
                "The key facts of an e-invoice as the document states them: number, issue"
                        + " date, type code, currency, seller, buyer, the totals and the number"
                        + " of lines. Reads the document; does not validate it.",
                List.of(EXTENSION), true, false, List.of()));
        tools.add(new Tool("get", "Read values by business term",
                "The values of an e-invoice at EN 16931 semantic paths: /BT-1 is the invoice"
                        + " number, /BG-22/BT-112 the total with VAT, /BG-4 every value of the"
                        + " seller, /BG-25/*/BT-131 the net amount of every line. A value with"
                        + " components (a scheme, a MIME code) is an object.",
                List.of(new Param("paths", Type.STRINGS, "The semantic paths, at most "
                                + MAX_PATHS + ".", List.of(), Optional.empty(), true),
                        EXTENSION),
                true, false, List.of()));
        tools.add(new Tool("convert", "Convert an e-invoice",
                "Convert an e-invoice to ESJ (EN16931 Semantic JSON: one flat map keyed by the"
                        + " business terms), to a UBL 2.1 invoice or credit note, or to a CII"
                        + " D16B invoice, through the EN 16931 semantic model. Every value the"
                        + " target syntax has no place for is listed; with fail_on_loss nothing"
                        + " is written where anything would be lost.",
                List.of(Param.choice("to", "The target.", "esj", "esj", "ubl", "cii"),
                        Param.flag("fail_on_loss", "Write nothing where the target has no place"
                                + " for part of the document."),
                        Param.choice("ubl_document", "Which UBL document to write; auto follows"
                                + " the invoice type code BT-3.", "auto", "auto", "invoice",
                                "creditnote"),
                        EXTENSION),
                true, false, List.of("document")));
        List<Param> render = new ArrayList<>(List.of(
                Param.choice("format", "A PDF/A-3b file, or one self-contained HTML page.",
                        "pdf", "pdf", "html"),
                Param.choice("layout", "The page layout of the PDF: a business letter (DIN 5008"
                        + " address field, reference line, EPC QR code), or the generic layout"
                        + " with every term under its own label.", "letter", "letter",
                        "generic"),
                Param.choice("embed", "cii writes the invoice into the PDF as Factur-X /"
                        + " ZUGFeRD, so that the file is a hybrid invoice.", "none", "none",
                        "cii"),
                LANG));
        if (!templates.isEmpty()) {
            render.add(new Param("template", Type.STRING, "A branded template of this server:"
                    + " letterhead, logo, colours.", templates, Optional.empty(), false));
        }
        render.add(EXTENSION);
        tools.add(new Tool("render", "Render an e-invoice",
                "Render an e-invoice for a person to read: a PDF/A-3b business letter or the"
                        + " generic layout, optionally with the invoice embedded as Factur-X so"
                        + " that the PDF is a hybrid invoice, or one HTML page.",
                render, true, false, List.of("rendering")));
        tools.add(new Tool("extract", "Take the invoice out of a PDF",
                "Take the electronic invoice — the XML a hybrid PDF such as ZUGFeRD or"
                        + " Factur-X carries — out of a PDF, unchanged; or, with list, list"
                        + " every attachment of the PDF.",
                List.of(Param.flag("list", "List the attachments instead."),
                        Param.text("attachment", "The attachment to take, by its name, where"
                                + " the PDF carries several invoices.", false)),
                true, false, List.of("invoice")));
        tools.add(new Tool("inspect", "Inspect an e-invoice",
                "One page about an e-invoice for a person: what it is and how it was read, who"
                        + " it is between, what it comes to, its two digests, the container of"
                        + " a PDF and the structural checks. It never says VALID: validate"
                        + " gives the verdict.",
                List.of(EXTENSION), true, false, List.of()));
        if (mode == Mode.HTTP) {
            tools.add(new Tool("upload", "Upload a document",
                    "Store a document for the other tools and return its id. Only the"
                            + " unchanged original bytes of a file make sense here, base64"
                            + " encoded: never type, reconstruct or edit an invoice — a"
                            + " validation of retyped content says nothing about the original."
                            + " A program uploads with POST /api/documents instead.",
                    List.of(Param.text("content_base64", "The bytes of the file, base64"
                                    + " encoded.", true),
                            Param.text("name", "The file name, for the record.", false)),
                    false, true, List.of()));
        }
        return List.copyOf(tools);
    }

    /**
     * Returns a tool by its name.
     *
     * @param tools the tools of a front door
     * @param name  the name
     * @return the tool, or nothing
     */
    static Optional<Tool> find(List<Tool> tools, String name) {
        return tools.stream().filter(tool -> tool.name().equals(name)).findFirst();
    }

    /**
     * Builds the command of {@code esj} a call becomes.
     *
     * @param tool      the tool
     * @param arguments the validated arguments
     * @param templates the template directory, where there is one
     * @param packs     the pack directories of the server
     * @return the plan
     */
    static Plan plan(Tool tool, Arguments arguments, Optional<Path> templates, List<Path> packs) {
        List<String> command = new ArrayList<>();
        List<Output> outputs = new ArrayList<>();
        switch (tool.name()) {
            case "validate" -> {
                command.addAll(List.of("validate", "-", "--output", "json"));
                String report = arguments.string("report");
                if (!"none".equals(report)) {
                    String file = "report." + report;
                    command.addAll(List.of("--report", file, "--report-format", report,
                            "--report-lang", arguments.string("lang")));
                    outputs.add(new Output("report", file, mediaType(report)));
                }
                packs(command, packs);
            }
            case "summary", "get" -> command.addAll(List.of("convert", "-", "--canonical"));
            case "convert" -> {
                String to = arguments.string("to");
                String file = "esj".equals(to) ? "invoice.esj.json" : "invoice." + to + ".xml";
                command.addAll(List.of("convert", "-", "--to", to, "--out", file, "--output",
                        "json"));
                if ("ubl".equals(to)) {
                    command.addAll(List.of("--ubl-document", arguments.string("ubl_document")));
                }
                if (arguments.flag("fail_on_loss") && !"esj".equals(to)) {
                    command.add("--fail-on-loss");
                }
                outputs.add(new Output("document", file,
                        "esj".equals(to) ? "application/json" : "application/xml"));
            }
            case "render" -> {
                boolean html = "html".equals(arguments.string("format"));
                String file = html ? "invoice.html" : "invoice.pdf";
                command.addAll(List.of("render", "-", "--out", file, "--lang",
                        arguments.string("lang")));
                if (html) {
                    command.add("--html");
                } else {
                    command.addAll(List.of("--layout", arguments.string("layout")));
                    if ("cii".equals(arguments.string("embed"))) {
                        command.addAll(List.of("--embed", "cii"));
                    }
                    Optional<String> template = arguments.optional("template");
                    if (template.isPresent() && templates.isPresent()) {
                        command.addAll(List.of("--template",
                                templates.get().resolve(template.get() + ".json").toString()));
                    }
                }
                outputs.add(new Output("rendering", file, html ? "text/html" : "application/pdf"));
            }
            case "extract" -> {
                command.addAll(List.of("extract", "-"));
                if (arguments.flag("list")) {
                    command.add("--list");
                } else {
                    command.addAll(List.of("--out", "invoice.xml"));
                    outputs.add(new Output("invoice", "invoice.xml", "application/xml"));
                }
                arguments.optional("attachment").ifPresent(name -> {
                    command.add("--attachment");
                    command.add(name);
                });
            }
            case "inspect" -> {
                command.addAll(List.of("inspect", "-"));
                packs(command, packs);
            }
            default -> throw new IllegalArgumentException("no command for " + tool.name());
        }
        List<String> extensions = arguments.strings("extension");
        if (!extensions.isEmpty() && tool.param("extension").isPresent()) {
            command.add("--extension");
            command.add(String.join(",", extensions));
        }
        return new Plan(command, outputs);
    }

    private static void packs(List<String> command, List<Path> packs) {
        for (Path pack : packs) {
            command.add("--packs");
            command.add(pack.toString());
        }
    }

    /** Returns the media type of a report or rendering format. */
    static String mediaType(String format) {
        return "pdf".equals(format) ? "application/pdf" : "text/html";
    }

    /**
     * The arguments of one call, checked against the parameters of its tool.
     *
     * @param values the value of every parameter that was given or has a default
     */
    record Arguments(Map<String, Jv> values) {

        Arguments {
            values = Map.copyOf(values);
        }

        String string(String name) {
            Jv value = values.get(name);
            return value instanceof Jv.Str str ? str.value() : null;
        }

        Optional<String> optional(String name) {
            return Optional.ofNullable(string(name));
        }

        boolean flag(String name) {
            return values.get(name) instanceof Jv.Bool bool && bool.value();
        }

        List<String> strings(String name) {
            if (values.get(name) instanceof Jv.Arr array) {
                List<String> strings = new ArrayList<>();
                for (Jv item : array.items()) {
                    strings.add(((Jv.Str) item).value());
                }
                return strings;
            }
            return List.of();
        }
    }
}
