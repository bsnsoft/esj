package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The OpenAPI description of {@code esj serve}: an OpenAPI 3.1 document in the shape the
 * specification gives it, covering exactly the endpoints of the tool table, with every
 * parameter of every tool.
 *
 * <p>The structure is checked here against the rules of the OpenAPI Specification 3.1.0
 * that a description of this kind can break: the version, the info object, path items whose
 * keys are methods, operations with responses that each have a description, parameters with a
 * name, a location and a schema, path parameters that are required and appear in the path,
 * unique operation identifiers, and references that resolve.
 */
class OpenApiTest {

    /** The methods a path item may carry. */
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "options",
            "head", "patch", "trace");

    @TempDir
    private Path temp;

    @Test
    void theDescriptionIsAnOpenApi31DocumentOfExactlyTheEndpointsOfTheTable() throws Exception {
        try (Http http = Http.start(ServeFixture.config(temp, ServeFixture.log()))) {
            Jv.Obj document = ServeFixture.json(ServeFixture.get(http.url() + "/openapi.json"));
            structure(document);
            Jv.Obj paths = (Jv.Obj) document.get("paths").orElseThrow();
            Set<String> expected = new TreeSet<>(Set.of("/api/documents",
                    "/api/artifacts/{id}"));
            Set<String> operations = new TreeSet<>();
            for (Tools.Tool tool : Tools.all(Tools.Mode.HTTP, List.of())) {
                if (tool.document()) {
                    expected.add("/api/" + tool.name());
                    operations.add(tool.name());
                }
            }
            assertEquals(expected, new TreeSet<>(paths.members().keySet()));
            assertEquals(operations, operationIds(paths), "a model is offered every tool that"
                    + " reads a document, and neither the upload nor the download");
            for (Tools.Tool tool : Tools.all(Tools.Mode.HTTP, List.of())) {
                if (!tool.document()) {
                    continue;
                }
                Jv.Obj post = (Jv.Obj) ((Jv.Obj) paths.get("/api/" + tool.name()).orElseThrow())
                        .get("post").orElseThrow();
                Set<String> names = new TreeSet<>();
                for (Jv parameter : ((Jv.Arr) post.get("parameters").orElseThrow()).items()) {
                    names.add(((Jv.Obj) parameter).string("name").orElseThrow());
                }
                Set<String> params = new TreeSet<>(Set.of(Tools.DOCUMENT));
                tool.params().forEach(param -> params.add(param.name()));
                assertEquals(params, names, "the parameters of " + tool.name());
                assertTrue(post.get("description").isPresent());
            }
            assertFalse(document.get("security").isPresent(), "no token, no security");
        }
    }

    @Test
    void aTokenAndAllowedDirectoriesAreDescribed() throws Exception {
        Path allowed = Files.createDirectories(temp.resolve("allowed"));
        ServeConfig config = ServeFixture.config(temp, ServeFixture.log())
                .withToken(Optional.of("token"));
        config = config.withAccess(List.of(), List.of(allowed), Optional.empty(), List.of(),
                Optional.empty());
        try (Http http = Http.start(config)) {
            Jv.Obj document = ServeFixture.json(ServeFixture.get(http.url() + "/openapi.json"));
            structure(document);
            Jv.Obj schemes = (Jv.Obj) ((Jv.Obj) document.get("components").orElseThrow())
                    .get("securitySchemes").orElseThrow();
            assertEquals("bearer", ((Jv.Obj) schemes.get("bearer").orElseThrow())
                    .string("scheme").orElseThrow());
            assertTrue(document.get("security").isPresent());
            Jv.Obj validate = (Jv.Obj) ((Jv.Obj) ((Jv.Obj) document.get("paths").orElseThrow())
                    .get("/api/validate").orElseThrow()).get("post").orElseThrow();
            assertTrue(((Jv.Arr) validate.get("parameters").orElseThrow()).items().stream()
                    .anyMatch(p -> "path".equals(((Jv.Obj) p).string("name").orElse(""))));
        }
    }

    /** Checks the rules of OpenAPI 3.1.0 a description of this kind can break. */
    private static void structure(Jv.Obj document) {
        assertEquals("3.1.0", document.string("openapi").orElseThrow());
        Jv.Obj info = (Jv.Obj) document.get("info").orElseThrow();
        assertTrue(info.string("title").isPresent() && info.string("version").isPresent());
        Jv.Obj paths = (Jv.Obj) document.get("paths").orElseThrow();
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, Jv> item : paths.members().entrySet()) {
            assertTrue(item.getKey().startsWith("/"), item.getKey());
            for (Map.Entry<String, Jv> operation : ((Jv.Obj) item.getValue()).members()
                    .entrySet()) {
                assertTrue(METHODS.contains(operation.getKey()), operation.getKey());
                Jv.Obj op = (Jv.Obj) operation.getValue();
                op.string("operationId").ifPresent(id -> assertTrue(ids.add(id),
                        "operationId " + id + " is unique"));
                Jv.Obj responses = (Jv.Obj) op.get("responses").orElseThrow();
                assertFalse(responses.members().isEmpty());
                for (Map.Entry<String, Jv> response : responses.members().entrySet()) {
                    assertTrue(response.getKey().matches("[1-5][0-9][0-9]|default"));
                    assertTrue(((Jv.Obj) response.getValue()).string("description")
                            .isPresent(), item.getKey() + " " + response.getKey());
                }
                Set<String> located = new HashSet<>();
                for (Jv parameter : op.get("parameters").map(p -> ((Jv.Arr) p).items())
                        .orElse(List.of())) {
                    Jv.Obj param = (Jv.Obj) parameter;
                    String name = param.string("name").orElseThrow();
                    String in = param.string("in").orElseThrow();
                    assertTrue(Set.of("query", "path", "header", "cookie").contains(in));
                    assertTrue(param.get("schema").isPresent(), name);
                    assertTrue(located.add(in + ":" + name), "a parameter appears once");
                    if ("path".equals(in)) {
                        assertEquals(Jv.of(true), param.get("required").orElseThrow());
                        assertTrue(item.getKey().contains("{" + name + "}"));
                    }
                }
                op.get("requestBody").ifPresent(body -> assertTrue(((Jv.Obj) body)
                        .get("content").isPresent()));
            }
        }
        references(document, document);
    }

    /** Checks that every {@code $ref} resolves inside the document. */
    private static void references(Jv value, Jv.Obj document) {
        if (value instanceof Jv.Obj object) {
            Optional<String> ref = object.string("$ref");
            if (ref.isPresent()) {
                assertTrue(ref.get().startsWith("#/"), ref.get());
                Jv target = document;
                for (String part : ref.get().substring(2).split("/")) {
                    target = ((Jv.Obj) target).get(part).orElseThrow(() ->
                            new AssertionError(ref.get() + " resolves"));
                }
            }
            object.members().values().forEach(member -> references(member, document));
        } else if (value instanceof Jv.Arr array) {
            array.items().forEach(item -> references(item, document));
        }
    }

    private static Set<String> operationIds(Jv.Obj paths) {
        Set<String> ids = new TreeSet<>();
        List<Jv> operations = new ArrayList<>();
        paths.members().values().forEach(item -> operations.addAll(((Jv.Obj) item).members()
                .values()));
        for (Jv operation : operations) {
            ((Jv.Obj) operation).string("operationId").ifPresent(ids::add);
        }
        return ids;
    }
}
