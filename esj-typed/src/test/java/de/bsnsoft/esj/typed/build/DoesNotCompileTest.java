package de.bsnsoft.esj.typed.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the step chain is for: the invoices it refuses are refused by the compiler, not at
 * run time. Each case below is a fragment that is compiled by {@code javac} from the test
 * and has to fail; the fragment that states everything the model asks for is compiled in
 * the same way and has to pass, so that the refusals cannot be an artefact of the harness.
 */
class DoesNotCompileTest {

    private static final String NUMBER = ".invoiceNumber(\"RE-1\")";
    private static final String ISSUE_DATE = ".issueDate(LocalDate.of(2026, 2, 3))";
    private static final String TYPE_CODE = ".typeCode(\"380\")";
    private static final String CURRENCY = ".currencyCode(\"EUR\")";
    private static final String SELLER =
            ".seller(s -> s.name(\"Example GmbH\").postalAddress(a -> a.countryCode(\"DE\")))";
    private static final String BUYER =
            ".buyer(b -> b.name(\"Muster AG\").postalAddress(a -> a.countryCode(\"DE\")))";
    private static final String LINE = ".invoiceLine(l -> l.identifier(\"1\")"
            + ".quantity(new BigDecimal(\"2\"), \"H87\")"
            + ".price(p -> p.netPrice(new BigDecimal(\"12.50\")))"
            + ".vat(v -> v.vatCategoryCode(\"S\").vatRate(new BigDecimal(\"19\")))"
            + ".item(i -> i.name(\"Sensor module SM-100\")))";

    @TempDir
    Path classes;

    @Test
    void theCompleteChainCompiles() {
        assertEquals(List.of(), compile(chain(NUMBER, ISSUE_DATE, TYPE_CODE, CURRENCY,
                SELLER, BUYER, LINE, ".build()")));
    }

    @Test
    void buildDoesNotExistBeforeTheSeller() {
        assertRefused("build()", chain(NUMBER, ISSUE_DATE, TYPE_CODE, CURRENCY, ".build()"));
    }

    @Test
    void buildDoesNotExistBeforeTheBuyer() {
        assertRefused("build()",
                chain(NUMBER, ISSUE_DATE, TYPE_CODE, CURRENCY, SELLER, ".build()"));
    }

    @Test
    void buildDoesNotExistBeforeAnInvoiceLine() {
        assertRefused("build()",
                chain(NUMBER, ISSUE_DATE, TYPE_CODE, CURRENCY, SELLER, BUYER, ".build()"));
    }

    @Test
    void aSecondInvoiceNumberDoesNotCompile() {
        assertRefused("invoiceNumber(java.lang.String)",
                chain(NUMBER, NUMBER, ISSUE_DATE, TYPE_CODE, CURRENCY, SELLER, BUYER, LINE,
                        ".build()"));
    }

    @Test
    void anInvoiceLineWithoutItsItemDoesNotCompile() {
        assertRefused("InvoiceLineSteps.Buildable", chain(NUMBER, ISSUE_DATE, TYPE_CODE,
                CURRENCY, SELLER, BUYER,
                ".invoiceLine(l -> l.identifier(\"1\")"
                        + ".quantity(new BigDecimal(\"2\"), \"H87\")"
                        + ".price(p -> p.netPrice(new BigDecimal(\"12.50\")))"
                        + ".vat(v -> v.vatCategoryCode(\"S\")))",
                ".build()"));
    }

    private void assertRefused(String mentioned, String source) {
        List<String> messages = compile(source);
        assertTrue(!messages.isEmpty(), "this fragment compiled and must not: " + source);
        assertTrue(messages.stream().anyMatch(message -> message.contains(mentioned)),
                "no message mentions " + mentioned + ": " + messages);
    }

    private static String chain(String... calls) {
        StringBuilder text = new StringBuilder("""
                package probe;

                import de.bsnsoft.esj.typed.build.InvoiceBuilder;
                import de.bsnsoft.esj.typed.build.Profile;
                import java.math.BigDecimal;
                import java.time.LocalDate;

                class Probe {
                    static Object probe() {
                        return InvoiceBuilder.create(Profile.EN16931)""");
        for (String call : calls) {
            text.append("\n                ").append(call);
        }
        return text + ";\n    }\n}\n";
    }

    /**
     * Compiles one fragment and returns what the compiler had to say, empty where it had
     * nothing.
     */
    private List<String> compile(String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "this test needs a JDK, not a JRE");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files =
                     compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            List<String> options = List.of("-classpath", classPath(),
                    "-d", classes.toString(), "-proc:none");
            boolean compiled = compiler.getTask(null, files, diagnostics, options, null,
                    List.of(new Fragment(source))).call();
            List<String> messages = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                    messages.add(diagnostic.getMessage(Locale.ROOT));
                }
            }
            assertEquals(messages.isEmpty(), compiled, "compiled: " + compiled + ", " + messages);
            return messages;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    /**
     * Returns the class path this test runs on, expanding the manifest of the one jar
     * that a forked test runner puts there in place of the entries themselves.
     */
    private static String classPath() {
        String path = System.getProperty("java.class.path");
        String[] entries = path.split(File.pathSeparator);
        if (entries.length != 1 || !entries[0].endsWith(".jar")) {
            return path;
        }
        try (JarFile jar = new JarFile(entries[0])) {
            String manifest = jar.getManifest() == null ? null
                    : jar.getManifest().getMainAttributes().getValue("Class-Path");
            if (manifest == null) {
                return path;
            }
            List<String> expanded = new ArrayList<>();
            for (String entry : manifest.split(" ")) {
                if (!entry.isBlank()) {
                    expanded.add(Path.of(URI.create(entry)).toString());
                }
            }
            return String.join(File.pathSeparator, expanded);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    /** One fragment, handed to the compiler without a file of its own. */
    private static final class Fragment extends SimpleJavaFileObject {

        private final String source;

        private Fragment(String source) {
            super(URI.create("string:///probe/Probe.java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
