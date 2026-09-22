package de.bsnsoft.esj.xr;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.SemanticPath;
import de.bsnsoft.esj.SemanticValue;
import de.bsnsoft.esj.json.Canonicalizer;
import de.bsnsoft.esj.json.EsjReader;
import de.bsnsoft.esj.model.Registry;
import de.bsnsoft.esj.validate.StructuralValidator;
import de.bsnsoft.esj.validate.ValidationResult;
import de.bsnsoft.esj.validate.ValidationStatus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

/**
 * The statements of {@code docs/storage.md}, run against a PostgreSQL of this test's own.
 *
 * <p>Every fenced {@code sql} block of the page is executed in the order the page prints it:
 * first what builds the schema, then the two documents through the insert the page shows, then
 * every query and the refresh, each asserted to answer what the page says it answers. A
 * statement the page cannot print in a form PostgreSQL accepts is therefore not on the page.
 *
 * <p>The Java between a comment naming a page and its section and the assertions after it is
 * the snippet as that page prints it; a change to one is a change to the other. Two pages are
 * covered: {@code docs/storage.md} and the database step of {@code docs/getting-started.md}.
 *
 * <p>The two documents are the UBL and the CII rendering of one business case of the
 * conformance corpus, read through the importer of this module. They are what the section
 * <b>Views</b> claims: two rows of {@code invoice}, one semantic digest, and two rows of
 * {@code invoice_summary} that differ in nothing but the key.
 *
 * <p>The server is started from the binaries of a Maven artefact — no Docker and no installed
 * PostgreSQL. Where the platform has none that can run, the whole class is skipped with the
 * reason, because a page cannot be held to a database that is not there.
 */
class StorageExamplesTest {

    /** The page whose statements are run. */
    private static final String PAGE = "docs/storage.md";

    /** The fence that opens a statement block. */
    private static final String FENCE = "```sql";

    /** The business case the corpus carries in both syntaxes, whose two documents agree. */
    private static final String STEM = "business-cases/standard/02.05a-INVOICE";

    /** The invoice number of that business case, which several queries look for. */
    private static final String INVOICE_NUMBER = "1234567";

    /** The seller of that business case, which the indexed query looks for. */
    private static final String SELLER = "[Seller name]";

    /** The documents of the corpus this test reads, imported once. */
    private static final Map<String, SemanticDocument> IMPORTED = new LinkedHashMap<>();

    /** The server, or {@code null} where this platform cannot run one. */
    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void startTheServer() {
        String unavailable;
        try {
            postgres = EmbeddedPostgres.start();
            return;
        } catch (IOException | RuntimeException e) {
            unavailable = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        throw new TestAbortedException(
                "no PostgreSQL binary for this platform, so " + PAGE + " is not checked here ("
                        + unavailable + ")");
    }

    @AfterAll
    static void stopTheServer() throws IOException {
        if (postgres != null) {
            postgres.close();
        }
    }

    /**
     * Runs the whole page: what it creates, what it writes and what it asks, with the
     * documents of the corpus behind it.
     */
    @Test
    void theSqlOfThePageRuns() throws SQLException {
        List<String> statements = statements();
        assertFalse(statements.isEmpty(), PAGE + " shows " + FENCE + " blocks");
        try (Connection connection = connection()) {
            schema(connection, statements);
            List<Long> stored = store(connection);
            int asked = 0;
            for (String statement : statements) {
                if (!isSchema(statement)) {
                    asked += run(connection, statement, stored.get(0)) ? 1 : 0;
                }
            }
            assertTrue(asked >= 8, PAGE + " asks at least eight questions, not " + asked);
        }
    }

    /**
     * The claim of <b>Views</b>: the UBL and the CII rendering of one business case are two
     * rows of {@code invoice} and one row of {@code invoice_summary} apart from the key.
     */
    @Test
    void theTwoSyntaxesOfOneInvoiceGiveTheSameRow() throws SQLException {
        try (Connection connection = connection()) {
            schema(connection, statements());
            List<Long> stored = store(connection);
            assertEquals(2, stored.size());

            List<List<String>> summaries = new ArrayList<>();
            for (Long id : stored) {
                summaries.add(rows(connection,
                        "SELECT * FROM invoice_summary WHERE id = ?", id).get(0));
            }
            List<String> ubl = summaries.get(0);
            List<String> cii = summaries.get(1);
            assertEquals(ubl.subList(1, ubl.size()), cii.subList(1, cii.size()));
            assertEquals(INVOICE_NUMBER, ubl.get(1).strip());
            assertEquals(1, rows(connection,
                    "SELECT DISTINCT semantic_digest FROM invoice", null).size());
            assertEquals(2, rows(connection,
                    "SELECT DISTINCT document_digest FROM invoice", null).size());
        }
    }

    /**
     * The database step of {@code docs/getting-started.md}: the Java side of the pipeline the
     * page names, on the canonical bytes of an imported invoice.
     */
    @Test
    void theGettingStartedPipelineRuns() {
        byte[] bytes = Canonicalizer.canonicalBytes(imported(STEM + "_ubl.xml"));

        // docs/getting-started.md: Put it into a database
        SemanticDocument document = EsjReader.strict().read(bytes);
        ValidationResult result = StructuralValidator.validate(document, Registry.en16931());
        if (result.status() == ValidationStatus.INVALID) {
            throw new IllegalArgumentException(result.findings().toString());
        }

        byte[] canonical = Canonicalizer.canonicalBytes(document);
        String semanticDigest = Canonicalizer.semanticDigest(document);
        String documentDigest = Canonicalizer.documentDigest(document);

        assertEquals(List.of(), result.findings());
        assertEquals(new String(bytes, UTF_8), new String(canonical, UTF_8));
        assertTrue(semanticDigest.matches("[0-9a-f]{64}"), semanticDigest);
        assertTrue(documentDigest.matches("[0-9a-f]{64}"), documentDigest);
    }

    /** The rows of the key/value table are the whole semantic content of the document. */
    @Test
    void theKeyValueRowsRebuildTheDocument() throws SQLException {
        try (Connection connection = connection()) {
            schema(connection, statements());
            long id = store(connection).get(0);
            SemanticDocument stored = imported(STEM + "_ubl.xml");

            SemanticDocument.Builder rebuilt = SemanticDocument.builder();
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT path, content, scheme, scheme_version, mime_code, filename"
                            + " FROM invoice_value WHERE document_id = ?")) {
                select.setLong(1, id);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        rebuilt.put(rows.getString(1), new SemanticValue(rows.getString(2),
                                rows.getString(3), rows.getString(4),
                                rows.getString(5), rows.getString(6)));
                    }
                }
            }

            assertEquals(stored.values(), rebuilt.build().values());
            assertEquals(Canonicalizer.semanticDigest(stored),
                    Canonicalizer.semanticDigest(rebuilt.build()));
        }
    }

    /** Asserts that the page gives the table a column for every component of a value. */
    @Test
    void theTableCarriesEveryComponentOfAValue() {
        String schema = String.join("\n", statements());
        for (String column : List.of("scheme", "scheme_version", "mime_code", "filename")) {
            assertTrue(schema.contains(column + " "), PAGE + " gives the table " + column);
        }
    }

    /**
     * Empties the database and runs the statements that build the schema, in the order the
     * page prints them.
     */
    private static void schema(Connection connection, List<String> statements)
            throws SQLException {
        try (Statement reset = connection.createStatement()) {
            reset.execute("DROP SCHEMA public CASCADE");
            reset.execute("CREATE SCHEMA public");
        }
        for (String statement : statements) {
            if (isSchema(statement)) {
                try (Statement execute = connection.createStatement()) {
                    execute.execute(statement);
                }
            }
        }
    }

    /**
     * Writes the two syntaxes of the business case with the two inserts the page prints.
     *
     * @param connection the database
     * @return the generated keys, the UBL document first
     */
    private static List<Long> store(Connection connection) throws SQLException {
        List<Long> stored = new ArrayList<>();
        for (String suffix : List.of("_ubl.xml", "_uncefact.xml")) {
            byte[] bytes = Canonicalizer.canonicalBytes(imported(STEM + suffix));

            // docs/storage.md: Validate before you store
            SemanticDocument document = EsjReader.strict().read(bytes);
            ValidationResult result = StructuralValidator.validate(document, Registry.en16931());
            if (result.status() == ValidationStatus.INVALID) {
                throw new IllegalArgumentException(result.findings().toString());
            }

            byte[] canonical = Canonicalizer.canonicalBytes(document);
            long id;
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO invoice (document, canonical_esj, semantic_digest, document_digest)"
                            + " VALUES (?::jsonb, ?, ?, ?) RETURNING id")) {
                insert.setString(1, new String(canonical, UTF_8));
                insert.setBytes(2, canonical);
                insert.setString(3, Canonicalizer.semanticDigest(document));
                insert.setString(4, Canonicalizer.documentDigest(document));
                try (ResultSet key = insert.executeQuery()) {
                    key.next();
                    id = key.getLong(1);
                }
            }

            // docs/storage.md: The key/value table
            try (PreparedStatement values = connection.prepareStatement(
                    "INSERT INTO invoice_value (document_id, path, content,"
                            + " scheme, scheme_version, mime_code, filename)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (Map.Entry<SemanticPath, SemanticValue> entry : document.values().entrySet()) {
                    SemanticValue value = entry.getValue();
                    values.setLong(1, id);
                    values.setString(2, entry.getKey().toString());
                    values.setString(3, value.canonicalContent());
                    values.setString(4, value.scheme());
                    values.setString(5, value.schemeVersion());
                    values.setString(6, value.mimeCode());
                    values.setString(7, value.filename());
                    values.addBatch();
                }
                values.executeBatch();
            }

            stored.add(id);
        }
        return stored;
    }

    /**
     * Runs one statement of the page that is not schema and asserts what the page says it
     * answers.
     *
     * @return {@code true} where the statement was a query
     */
    private static boolean run(Connection connection, String sql, long id) throws SQLException {
        if (sql.toUpperCase(Locale.ROOT).startsWith("REFRESH")) {
            try (Statement refresh = connection.createStatement()) {
                refresh.execute(sql);
            }
            assertEquals(2, rows(connection,
                    "SELECT * FROM invoice_summary_mv", null).size(), PAGE + ": " + sql);
            return false;
        }
        assertQuery(connection, sql, id);
        return true;
    }

    /** Runs one query of the page and asserts what the page says it answers. */
    private static void assertQuery(Connection connection, String sql, long id)
            throws SQLException {
        List<List<String>> rows = rows(connection, sql, parameter(sql, id));
        String where = PAGE + ": " + sql;
        assertFalse(rows.isEmpty(), where + "\nthe query answers nothing");
        SemanticDocument document = imported(STEM + "_ubl.xml");
        if (sql.contains("'/BG-25/%'")) {
            assertEquals(lineValues(document), rows.size(), where);
            assertTrue(rows.stream().allMatch(row -> row.get(0).startsWith("/BG-25/")), where);
        } else if (sql.contains("md5(?)")) {
            assertEquals(2, rows.size(), where);
            assertEquals(1, rows.get(0).size(), where);
        } else if (sql.contains("FROM invoice_value")) {
            assertEquals(2, rows.size(), where);
            assertTrue(rows.stream().allMatch(row -> row.get(row.size() - 1)
                    .strip().equals(INVOICE_NUMBER)), where);
        } else if (sql.contains("GROUP BY currency")) {
            assertEquals(1, rows.size(), where);
            assertEquals("2", rows.get(0).get(1), where);
            assertEquals(0, totalWithVat(document).multiply(new BigDecimal(2))
                    .compareTo(new BigDecimal(rows.get(0).get(2))), where);
        } else {
            assertEquals(2, rows.size(), where);
            assertTrue(rows.stream().allMatch(row -> row.contains(INVOICE_NUMBER)), where);
        }
    }

    /** Returns what the single placeholder of a query stands for, or {@code null}. */
    private static Object parameter(String sql, long id) {
        if (sql.contains("document_id = ?")) {
            return id;
        }
        if (sql.contains("md5(?)")) {
            return INVOICE_NUMBER;
        }
        return sql.contains("?") ? SELLER : null;
    }

    /** Returns the rows of a query, every column as text, binding a value if the sql has one. */
    private static List<List<String>> rows(Connection connection, String sql, Object bound)
            throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            for (int parameter = 1; parameter <= parameters(sql); parameter++) {
                query.setObject(parameter, bound);
            }
            try (ResultSet result = query.executeQuery()) {
                int columns = result.getMetaData().getColumnCount();
                while (result.next()) {
                    List<String> row = new ArrayList<>();
                    for (int column = 1; column <= columns; column++) {
                        row.add(result.getString(column));
                    }
                    rows.add(List.copyOf(row));
                }
            }
        }
        return rows;
    }

    /** Returns BT-112 of a document, the amount due for payment with value added tax. */
    private static BigDecimal totalWithVat(SemanticDocument document) {
        return document.value(SemanticPath.of("/BG-22/BT-112")).orElseThrow().asDecimal();
    }

    /** Returns how many values of a document stand under the invoice lines. */
    private static int lineValues(SemanticDocument document) {
        return (int) document.values().keySet().stream()
                .filter(path -> path.toString().startsWith("/BG-25/"))
                .count();
    }

    private static int parameters(String sql) {
        return (int) sql.chars().filter(character -> character == '?').count();
    }

    private static boolean isSchema(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        return upper.startsWith("CREATE") || upper.startsWith("ALTER");
    }

    /** Returns one instance of the corpus, read through the importer of this module. */
    private static SemanticDocument imported(String relativePath) {
        return IMPORTED.computeIfAbsent(relativePath,
                path -> new XrImporter().importXml(Conformance.instance(path)));
    }

    /** Opens the database for one test. */
    private static Connection connection() throws SQLException {
        return postgres.getPostgresDatabase().getConnection();
    }

    /**
     * Returns the statements of the page: every fenced {@code sql} block, split on the
     * semicolon that ends a statement, in the order the page prints them.
     */
    private static List<String> statements() {
        List<String> statements = new ArrayList<>();
        for (String block : blocks(Conformance.text("/" + PAGE))) {
            for (String statement : block.split(";")) {
                String trimmed = statement.strip();
                if (!trimmed.isEmpty()) {
                    statements.add(trimmed);
                }
            }
        }
        return statements;
    }

    /** Returns the bodies of the fenced sql blocks of a page. */
    private static List<String> blocks(String page) {
        List<String> blocks = new ArrayList<>();
        List<String> lines = List.of(page.split("\n", -1));
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).equals(FENCE)) {
                continue;
            }
            int end = i + 1;
            while (end < lines.size() && !lines.get(end).equals("```")) {
                end++;
            }
            assertTrue(end < lines.size(), "the block opened at line " + (i + 1) + " is closed");
            blocks.add(String.join("\n", lines.subList(i + 1, end)));
            i = end;
        }
        return blocks;
    }
}
