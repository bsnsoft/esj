package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One table, three front doors: every tool, called with the same arguments over the REST
 * API, the MCP server over HTTP and the MCP server over the standard streams, gives the same
 * structured result. What may differ is only where a file it wrote is: a URL over HTTP, a
 * path over the standard streams.
 *
 * <p>The REST answer of {@code validate} is the command line's report itself, and the
 * structured result over MCP is drawn from that report; the comparison draws it from the
 * REST body the same way.
 */
class ToolParityTest {

    /** The members of a file description that name where the file is. */
    private static final Set<String> WHERE = Set.of("url", "path", "expires");

    @TempDir
    private Path temp;

    static Stream<Arguments> calls() {
        return Stream.of(
                Arguments.of("validate", "examples/standard-invoice.esj.json", "{}"),
                Arguments.of("validate", "conformance/pdf/factur-x.pdf",
                        "{\"report\":\"pdf\",\"lang\":\"en\"}"),
                Arguments.of("validate", "examples/invalid/arithmetic-mismatch.esj.json",
                        "{\"report\":\"html\"}"),
                Arguments.of("validate", "examples/b2c-gross.esj.json",
                        "{\"extension\":[\"b2c\"]}"),
                Arguments.of("summary",
                        "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml", "{}"),
                Arguments.of("get", "conformance/pdf/factur-x.pdf",
                        "{\"paths\":[\"/BT-1\",\"/BG-22\",\"/BG-25/*/BT-131\",\"/BT-22\"]}"),
                Arguments.of("convert", "examples/standard-invoice.esj.json", "{\"to\":\"ubl\"}"),
                Arguments.of("convert",
                        "conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml",
                        "{}"),
                Arguments.of("convert",
                        "conformance/kosit/business-cases/extension/04.01a-INVOICE_ubl.xml",
                        "{\"to\":\"cii\",\"fail_on_loss\":true,\"extension\":[\"xrechnung\"]}"),
                Arguments.of("render", "examples/standard-invoice.esj.json",
                        "{\"embed\":\"cii\",\"lang\":\"en\"}"),
                Arguments.of("render", "examples/standard-invoice.esj.json",
                        "{\"format\":\"html\"}"),
                Arguments.of("render", "examples/standard-invoice.esj.json",
                        "{\"layout\":\"generic\"}"),
                Arguments.of("extract", "conformance/pdf/factur-x.pdf", "{\"list\":true}"),
                Arguments.of("extract", "conformance/pdf/factur-x.pdf", "{}"),
                Arguments.of("inspect", "conformance/pdf/factur-x.pdf", "{}"));
    }

    /**
     * Calls one tool through the three front doors.
     *
     * @param tool      the tool
     * @param document  the file of the repository it reads
     * @param arguments its arguments
     */
    @ParameterizedTest(name = "{0} {1} {2}")
    @MethodSource("calls")
    void everyFrontDoorGivesTheSameResult(String tool, String document, String arguments)
            throws Exception {
        Jv.Obj given = (Jv.Obj) Jv.parse(arguments.getBytes(StandardCharsets.UTF_8), 4096);
        Jv.Obj rest;
        Jv.Obj mcpHttp;
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            HttpResponse<byte[]> response = ServeFixture.post(http.url() + "/api/" + tool
                    + query(given), ServeFixture.bytes(document));
            assertEquals(200, response.statusCode(), new String(response.body(),
                    StandardCharsets.UTF_8));
            rest = "validate".equals(tool) ? fromReport(response) : ServeFixture.json(response);

            String id = ServeFixture.json(ServeFixture.post(http.url() + "/api/documents",
                    ServeFixture.bytes(document))).string("id").orElseThrow();
            Jv.Builder withDocument = Jv.object().put(Tools.DOCUMENT, id);
            given.members().forEach(withDocument::put);
            mcpHttp = structured(ServeFixture.json(ServeFixture.post(http.url() + "/mcp",
                    call(tool, withDocument.build()).toBytes(), "Content-Type",
                    "application/json", "MCP-Protocol-Version", Mcp.LATEST)));
        }

        Jv.Builder withPath = Jv.object().put(Tools.PATH, ServeFixture.file(document).toString());
        given.members().forEach(withPath::put);
        Optional<String> name = firstFileName(rest);
        if (name.isPresent()) {
            Path directory = Files.createDirectories(temp.resolve("stdio-" + System.nanoTime()));
            withPath.put(Tools.OUT, directory.resolve(name.get()).toString());
        }
        List<Jv> answers = new ArrayList<>();
        byte[] input = (new String(call(tool, withPath.build()).toBytes(),
                StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);
        Stdio.run(ServeFixture.config(temp, ServeFixture.log()), new ByteArrayInputStream(input),
                line -> answers.add(Jv.parse(line, Integer.MAX_VALUE)));
        assertEquals(1, answers.size());
        Jv.Obj mcpStdio = structured((Jv.Obj) answers.get(0));

        String expected = text(withoutWhere(rest));
        assertEquals(expected, text(withoutWhere(mcpHttp)), "MCP over HTTP");
        assertEquals(expected, text(withoutWhere(mcpStdio)), "MCP over the standard streams");
        if (name.isPresent()) {
            String path = firstFile(mcpStdio).string("path").orElseThrow();
            assertEquals(firstFile(rest).string("sha256").orElseThrow(),
                    Store.sha256(Path.of(path)), "the file written to out is the artefact");
        }
    }

    @Test
    void theRestApiOffersEveryToolThatReadsADocumentAndMcpEveryToolOfItsTransport() {
        List<String> http = Tools.all(Tools.Mode.HTTP, List.of()).stream()
                .map(Tools.Tool::name).toList();
        List<String> stdio = Tools.all(Tools.Mode.STDIO, List.of()).stream()
                .map(Tools.Tool::name).toList();
        assertEquals(List.of("validate", "summary", "get", "convert", "render", "extract",
                "inspect", "upload"), http);
        assertEquals(http.subList(0, http.size() - 1), stdio, "no upload where files are"
                + " named by path");
    }

    private static Jv.Obj fromReport(HttpResponse<byte[]> response) throws Exception {
        Jv.Obj report = ServeFixture.json(response);
        int exit = switch (report.string("verdict").orElseThrow()) {
            case "VALID" -> 0;
            case "INVALID" -> 1;
            default -> 9;
        };
        Jv.Obj condensed = Results.validate(new ByteArrayInputStream(response.body()), exit)
                .structured();
        Optional<String> link = response.headers().firstValue("Link");
        if (link.isEmpty()) {
            return condensed;
        }
        // The files of a validation are in the Link header of the REST answer, and their
        // descriptions are the ones an artefact download confirms.
        String url = link.get().substring(1, link.get().indexOf('>'));
        HttpResponse<byte[]> file = ServeFixture.get(url);
        String type = file.headers().firstValue("Content-Type").orElseThrow()
                .replace("; charset=utf-8", "");
        String disposition = file.headers().firstValue("Content-Disposition").orElseThrow();
        Jv.Obj description = Jv.object()
                .put("role", "report")
                .put("name", disposition.substring(disposition.indexOf('"') + 1,
                        disposition.lastIndexOf('"')))
                .put("mediaType", type)
                .put("bytes", file.body().length)
                .put("sha256", java.util.HexFormat.of().formatHex(Store.sha256()
                        .digest(file.body())))
                .build();
        Jv.Builder builder = Jv.object();
        condensed.members().forEach(builder::put);
        return builder.put("files", Jv.array(List.of(description))).build();
    }

    private static Jv.Obj call(String tool, Jv.Obj arguments) {
        return Jv.object().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call")
                .put("params", Jv.object().put("name", tool).put("arguments", arguments)
                        .build())
                .build();
    }

    private static Jv.Obj structured(Jv.Obj answer) {
        Jv.Obj result = (Jv.Obj) answer.get("result").orElseThrow(() ->
                new AssertionError(text(answer)));
        assertTrue(result.get("isError").orElseThrow() instanceof Jv.Bool error
                && !error.value(), text(answer));
        return (Jv.Obj) result.get("structuredContent").orElseThrow();
    }

    private static Optional<String> firstFileName(Jv.Obj result) {
        return result.get("files").isPresent() ? firstFile(result).string("name")
                : Optional.empty();
    }

    private static Jv.Obj firstFile(Jv.Obj result) {
        return (Jv.Obj) ((Jv.Arr) result.get("files").orElseThrow()).items().get(0);
    }

    private static Jv withoutWhere(Jv value) {
        if (value instanceof Jv.Obj object) {
            Jv.Builder builder = Jv.object();
            for (Map.Entry<String, Jv> member : object.members().entrySet()) {
                if (!WHERE.contains(member.getKey())) {
                    builder.put(member.getKey(), withoutWhere(member.getValue()));
                }
            }
            return builder.build();
        }
        if (value instanceof Jv.Arr array) {
            List<Jv> items = new ArrayList<>();
            array.items().forEach(item -> items.add(withoutWhere(item)));
            return Jv.array(items);
        }
        return value;
    }

    private static String query(Jv.Obj arguments) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, Jv> member : arguments.members().entrySet()) {
            List<String> values = new ArrayList<>();
            if (member.getValue() instanceof Jv.Arr array) {
                array.items().forEach(item -> values.add(((Jv.Str) item).value()));
            } else if (member.getValue() instanceof Jv.Str str) {
                values.add(str.value());
            } else {
                values.add(text(member.getValue()));
            }
            for (String value : values) {
                query.append(query.length() == 0 ? '?' : '&').append(member.getKey()).append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
            }
        }
        return query.toString();
    }

    private static String text(Jv value) {
        return new String(value.toBytes(), StandardCharsets.UTF_8);
    }
}
