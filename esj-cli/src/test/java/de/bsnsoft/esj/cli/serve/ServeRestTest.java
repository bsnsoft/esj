package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The REST API of {@code esj serve}, held against the command line it runs.
 *
 * <p>The answer of {@code POST /api/validate} is the report of {@code esj validate
 * --output json} over the same bytes, byte for byte, for every kind of input: XML of both
 * syntaxes, a hybrid PDF and an ESJ document. The rest is the transport: a document as the
 * body or by the id of an upload, the parameters in the query or as a JSON body, the
 * statuses of a request the server refuses, and the artefacts it hands out.
 */
class ServeRestTest {

    @TempDir
    private Path temp;

    /**
     * The REST answer of a validation is the command line's report, byte for byte.
     *
     * @param input a file of the repository
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "conformance/kosit/business-cases/standard/01.01a-INVOICE_ubl.xml",
        "conformance/kosit/business-cases/standard/01.01a-INVOICE_uncefact.xml",
        "conformance/pdf/factur-x.pdf",
        "examples/standard-invoice.esj.json",
        "examples/invalid/arithmetic-mismatch.esj.json"})
    void theValidationIsTheReportOfTheCommandLine(String input) throws Exception {
        byte[] document = ServeFixture.bytes(input);
        byte[] expected = commandLine(document, "validate", "-", "--output", "json");
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            HttpResponse<byte[]> response = ServeFixture.post(http.url() + "/api/validate",
                    document);
            assertEquals(200, response.statusCode(), new String(response.body(),
                    StandardCharsets.UTF_8));
            assertEquals("application/json",
                    response.headers().firstValue("Content-Type").orElse(""));
            assertArrayEquals(expected, response.body(), "the REST body is the report of"
                    + " esj validate - --output json");
        }
    }

    @Test
    void aDocumentIsTheBodyTheIdOfAnUploadOrAnAllowedPath() throws Exception {
        byte[] document = ServeFixture.bytes("examples/standard-invoice.esj.json");
        Path allowed = Files.createDirectories(temp.resolve("allowed"));
        Files.write(allowed.resolve("invoice.esj.json"), document);
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log());
        config = config.withAccess(List.of(), List.of(allowed), Optional.empty(), List.of(),
                Optional.empty());
        try (Http http = Http.start(config)) {
            String byBody = new String(ServeFixture.post(http.url() + "/api/summary", document)
                    .body(), StandardCharsets.UTF_8);

            HttpResponse<byte[]> upload = ServeFixture.post(http.url() + "/api/documents",
                    document);
            assertEquals(201, upload.statusCode());
            Jv.Obj stored = ServeFixture.json(upload);
            assertEquals(document.length, Long.parseLong(((Jv.Num) stored.get("bytes")
                    .orElseThrow()).text()));
            assertEquals(Store.sha256(ServeFixture.file("examples/standard-invoice.esj.json")),
                    stored.string("sha256").orElseThrow());
            String id = stored.string("id").orElseThrow();
            String byId = new String(ServeFixture.post(http.url() + "/api/summary?document="
                    + id, new byte[0]).body(), StandardCharsets.UTF_8);

            String byJson = new String(ServeFixture.post(http.url() + "/api/summary",
                    ("{\"document\":\"" + id + "\"}").getBytes(StandardCharsets.UTF_8),
                    "Content-Type", "application/json").body(), StandardCharsets.UTF_8);

            String byPath = new String(ServeFixture.post(http.url()
                    + "/api/summary?path=invoice.esj.json", new byte[0]).body(),
                    StandardCharsets.UTF_8);

            assertTrue(byBody.contains("\"invoiceNumber\": \"RE-2026-0042\""), byBody);
            assertEquals(byBody, byId);
            assertEquals(byBody, byJson, "Open WebUI sends the parameters as a JSON body");
            assertEquals(byBody, byPath);
        }
    }

    @Test
    void anEsjDocumentPostedAsJsonIsTheDocumentAndNotTheParameters() throws Exception {
        byte[] document = ServeFixture.bytes("examples/standard-invoice.esj.json");
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            HttpResponse<byte[]> response = ServeFixture.post(http.url()
                    + "/api/get?paths=/BT-1", document, "Content-Type", "application/json");
            assertEquals(200, response.statusCode());
            assertEquals("RE-2026-0042", ((Jv.Obj) ServeFixture.json(response).get("values")
                    .orElseThrow()).string("/BT-1").orElseThrow());
        }
    }

    @Test
    void theServerRefusesWhatIsNotARequestOfTheApi() throws Exception {
        byte[] document = ServeFixture.bytes("examples/standard-invoice.esj.json");
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            String url = http.url();
            assertEquals(400, ServeFixture.post(url + "/api/validate?nonsense=1", document)
                    .statusCode(), "an unknown parameter");
            assertEquals(400, ServeFixture.post(url + "/api/validate?report=docx", document)
                    .statusCode(), "a value outside the choices");
            assertEquals(400, ServeFixture.post(url + "/api/validate", new byte[0])
                    .statusCode(), "no document");
            assertEquals(400, ServeFixture.post(url + "/api/validate?document="
                    + "0123456789abcdef0123456789abcdef", document).statusCode(),
                    "a body and an upload at once");
            assertEquals(404, ServeFixture.post(url + "/api/validate?document="
                    + "0123456789abcdef0123456789abcdef", new byte[0]).statusCode(),
                    "an upload that is not kept");
            assertEquals(400, ServeFixture.post(url + "/api/summary?path=/etc/hosts",
                    new byte[0]).statusCode(), "a path, where no directory is allowed");
            assertEquals(404, ServeFixture.post(url + "/api/nothing", document).statusCode());
            assertEquals(404, ServeFixture.post(url + "/api/upload", document).statusCode(),
                    "the upload of MCP is POST /api/documents here");
            assertEquals(405, ServeFixture.get(url + "/api/validate").statusCode());
            assertEquals(404, ServeFixture.get(url + "/elsewhere").statusCode());
            assertEquals(404, ServeFixture.get(url + "/api/artifacts/"
                    + "0123456789abcdef0123456789abcdef").statusCode());
            HttpResponse<byte[]> unreadable = ServeFixture.post(url + "/api/validate",
                    "this is no invoice".getBytes(StandardCharsets.UTF_8));
            assertEquals(422, unreadable.statusCode());
            Jv.Obj body = ServeFixture.json(unreadable);
            assertEquals("input", body.string("error").orElseThrow());
            assertEquals("2", ((Jv.Num) body.get("exitCode").orElseThrow()).text());
        }
    }

    @Test
    void anArtefactIsDownloadedOnceItIsWrittenAndNeverRunsInTheOriginOfTheServer()
            throws Exception {
        byte[] document = ServeFixture.bytes("examples/standard-invoice.esj.json");
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            HttpResponse<byte[]> rendered = ServeFixture.post(http.url()
                    + "/api/render?format=html", document);
            assertEquals(200, rendered.statusCode());
            Jv.Obj result = ServeFixture.json(rendered);
            Jv.Obj file = (Jv.Obj) ((Jv.Arr) result.get("files").orElseThrow()).items().get(0);
            String url = file.string("url").orElseThrow();
            assertTrue(rendered.headers().firstValue("Link").orElse("").contains(url));
            HttpResponse<byte[]> artefact = ServeFixture.get(url);
            assertEquals(200, artefact.statusCode());
            assertEquals("text/html; charset=utf-8",
                    artefact.headers().firstValue("Content-Type").orElse(""));
            assertTrue(artefact.headers().firstValue("Content-Disposition").orElse("")
                    .startsWith("attachment"));
            assertTrue(artefact.headers().firstValue("Content-Security-Policy").orElse("")
                    .startsWith("sandbox"));
            assertEquals(file.string("sha256").orElseThrow(), sha256(artefact.body()));
            byte[] cli = commandLineFile(document, "render", "-", "--out", "out.html", "--html",
                    "--lang", "de");
            assertArrayEquals(cli, artefact.body(), "the artefact is what esj render writes");
        }
    }

    @Test
    void aRenderedHybridIsValidatedByTheIdOfItsArtefact() throws Exception {
        byte[] document = ServeFixture.bytes("examples/standard-invoice.esj.json");
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            Jv.Obj rendered = ServeFixture.json(ServeFixture.post(http.url()
                    + "/api/render?embed=cii", document));
            String url = ((Jv.Obj) ((Jv.Arr) rendered.get("files").orElseThrow()).items().get(0))
                    .string("url").orElseThrow();
            String id = url.substring(url.lastIndexOf('/') + 1);
            HttpResponse<byte[]> validated = ServeFixture.post(http.url()
                    + "/api/validate?document=" + id, new byte[0]);
            assertEquals(200, validated.statusCode());
            Jv.Obj report = ServeFixture.json(validated);
            assertEquals("VALID", report.string("verdict").orElseThrow());
            assertEquals(Jv.of(true), ((Jv.Obj) report.get("container").orElseThrow()).get("ok")
                    .orElseThrow(), "the container is the PDF the render wrote");
        }
    }

    @Test
    void aValidationReportIsAnArtefactBesideTheUnchangedReport() throws Exception {
        byte[] document = ServeFixture.bytes("examples/standard-invoice.esj.json");
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            HttpResponse<byte[]> response = ServeFixture.post(http.url()
                    + "/api/validate?report=pdf&lang=en", document);
            assertEquals(200, response.statusCode());
            assertArrayEquals(commandLine(document, "validate", "-", "--output", "json"),
                    response.body(), "the body stays the report of the command line");
            String link = response.headers().firstValue("Link").orElseThrow();
            assertTrue(link.contains("rel=\"report\"") && link.contains("application/pdf"),
                    link);
            String url = link.substring(1, link.indexOf('>'));
            HttpResponse<byte[]> report = ServeFixture.get(url);
            assertEquals(200, report.statusCode());
            assertEquals("%PDF", new String(report.body(), 0, 4, StandardCharsets.US_ASCII));
        }
    }

    @Test
    void theSelfDescriptionAndTheOpenApiDescriptionArePublicAndTheRestNeedsTheToken()
            throws Exception {
        byte[] document = ServeFixture.bytes("examples/standard-invoice.esj.json");
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withToken(Optional.of("s3cr3t-t0ken"));
        try (Http http = Http.start(config)) {
            String url = http.url();
            assertEquals(200, ServeFixture.get(url + "/").statusCode());
            assertEquals(200, ServeFixture.get(url + "/openapi.json").statusCode());
            HttpResponse<byte[]> missing = ServeFixture.post(url + "/api/summary", document);
            assertEquals(401, missing.statusCode());
            assertTrue(missing.headers().firstValue("WWW-Authenticate").orElse("")
                    .startsWith("Bearer"));
            assertEquals(401, ServeFixture.post(url + "/api/summary", document,
                    "Authorization", "Bearer wrong").statusCode());
            assertEquals(401, ServeFixture.post(url + "/mcp", "{}".getBytes(
                    StandardCharsets.UTF_8), "Content-Type", "application/json").statusCode());
            assertEquals(200, ServeFixture.post(url + "/api/summary", document,
                    "Authorization", "Bearer s3cr3t-t0ken").statusCode());
        }
    }

    @Test
    void aForeignBrowserOriginIsRefusedAndAnAllowedOneGetsCors() throws Exception {
        byte[] document = ServeFixture.bytes("examples/standard-invoice.esj.json");
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log());
        config = config.withAccess(List.of("https://chat.example.com"), List.of(),
                Optional.empty(), List.of(), Optional.empty());
        try (Http http = Http.start(config)) {
            String url = http.url();
            assertEquals(403, ServeFixture.post(url + "/api/summary", document,
                    "Origin", "https://attacker.example").statusCode());
            assertEquals(403, ServeFixture.post(url + "/mcp", "{}".getBytes(
                    StandardCharsets.UTF_8), "Content-Type", "application/json",
                    "Origin", "http://rebound.example:8080").statusCode());
            assertEquals(403, ServeFixture.post(url + "/api/summary", document,
                    "Origin", "null").statusCode());
            HttpResponse<byte[]> loopback = ServeFixture.post(url + "/api/summary", document,
                    "Origin", "http://localhost:3000");
            assertEquals(200, loopback.statusCode());
            assertFalse(loopback.headers().firstValue("Access-Control-Allow-Origin")
                    .isPresent(), "a loopback origin is let through, and given no CORS");
            HttpResponse<byte[]> allowed = ServeFixture.post(url + "/api/summary", document,
                    "Origin", "https://chat.example.com");
            assertEquals(200, allowed.statusCode());
            assertEquals("https://chat.example.com", allowed.headers()
                    .firstValue("Access-Control-Allow-Origin").orElse(""));
        }
    }

    /** Runs the command line as a process of its own, as the server does. */
    private byte[] commandLine(byte[] stdin, String... args) throws IOException,
            InterruptedException {
        Path input = Files.write(temp.resolve("stdin-" + System.nanoTime()), stdin);
        Path output = temp.resolve("stdout-" + System.nanoTime());
        List<String> command = new ArrayList<>(ServeFixture.child("512m"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .redirectInput(input.toFile())
                .redirectOutput(output.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        assertTrue(process.waitFor(2, TimeUnit.MINUTES));
        return Files.readAllBytes(output);
    }

    /** Runs the command line in a directory of its own and returns the file it wrote. */
    private byte[] commandLineFile(byte[] stdin, String... args) throws IOException,
            InterruptedException {
        Path directory = Files.createTempDirectory(temp, "cli-");
        Path input = Files.write(directory.resolve("stdin"), stdin);
        List<String> command = new ArrayList<>(ServeFixture.child("512m"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectInput(input.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        assertTrue(process.waitFor(2, TimeUnit.MINUTES));
        return Files.readAllBytes(directory.resolve("out.html"));
    }

    private static String sha256(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(Store.sha256().digest(bytes));
    }
}
