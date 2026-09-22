package de.bsnsoft.esj.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.bsnsoft.esj.SemanticDocument;
import de.bsnsoft.esj.bindings.CiiWriter;
import de.bsnsoft.esj.invoice.Invoice;
import de.bsnsoft.esj.pdf.EmbeddedFile;
import de.bsnsoft.esj.pdf.FacturX;
import de.bsnsoft.esj.pdf.PdfContainer;
import de.bsnsoft.esj.render.PdfRenderer;
import de.bsnsoft.esj.rules.en16931.En16931;
import de.bsnsoft.esj.syntax.SyntaxValidator;
import de.bsnsoft.esj.typed.build.Profile;
import de.bsnsoft.esj.xr.XrImporter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles {@code examples/java/HybridInvoice.java} and runs its {@code main}, so that the
 * program the README and {@code examples/README.md} link is a program that works.
 *
 * <p>The source is read from the test classpath, where the build puts the repository's
 * {@code examples/} directory: the file compiled here is the file a reader opens, and not a
 * second copy of it that could drift away from the first. The program writes into a
 * temporary directory of its own, which it takes as its first argument.
 */
class HybridInvoiceExampleTest {

    /** The example, as the build copies the repository's directory onto the classpath. */
    private static final String SOURCE = "/examples/java/HybridInvoice.java";

    /** The name of its only class, which is in no package. */
    private static final String CLASS = "HybridInvoice";

    /** The language level the library modules are written to, and the example with them. */
    private static final String RELEASE = "17";

    /**
     * One class per module the example names, so that the classpath of the compilation is
     * derived from the running test and not spelled out as paths.
     */
    private static final List<Class<?>> MODULES = List.of(
            SemanticDocument.class,   // esj-core
            Profile.class,            // esj-typed
            Invoice.class,            // esj-invoice
            CiiWriter.class,          // esj-bindings
            XrImporter.class,         // esj-xr
            SyntaxValidator.class,    // esj-syntax
            En16931.class,            // esj-rules
            FacturX.class,            // esj-pdf
            PdfRenderer.class);       // esj-render

    @TempDir
    Path directory;

    @Test
    void theExampleCompilesRunsAndSaysWhatThePagesSayItSays() throws Exception {
        Path classes = Files.createDirectory(directory.resolve("classes"));
        Path output = Files.createDirectory(directory.resolve("out"));

        compile(source(), classes);
        List<String> printed = run(classes, output);

        assertTrue(printed.contains("attachment: factur-x.xml (text/xml, Alternative)"),
                "the file carries the invoice as an associated Factur-X attachment: " + printed);
        assertTrue(printed.contains(
                        "attachment: invoice.esj.json (application/json, Supplement)"),
                "and the same invoice as an ESJ document beside it: " + printed);
        assertEquals(List.of(),
                printed.stream().filter(line -> line.startsWith("write note:")).toList(),
                "this invoice reaches the cross industry invoice whole");
        assertEquals(after(printed, "digest written: "), after(printed, "digest read:    "),
                "what went into the container is what comes out of it");
        assertEquals(64, after(printed, "digest written: ").length(),
                "the semantic digest is a SHA-256 in hexadecimal");
        assertTrue(printed.stream().anyMatch(line -> line.startsWith("refused: BR-S-02:")),
                "build() refuses a standard rated line without a seller VAT identifier: " + printed);
        assertTrue(printed.stream().anyMatch(line -> line.startsWith("refused: BR-CO-26:")),
                "and names the second rule that seller breaks: " + printed);
        assertEquals("0", after(printed, "rule findings: "),
                "the invoice the example builds breaks no rule of the native pack");
        assertTrue(printed.contains("ran: cii-d16b-xsd")
                        && printed.contains("ran: en16931-cii-schematron"),
                "both official artefacts ran over the written bytes: " + printed);
        assertEquals("VALID", after(printed, "official verdict: "),
                "and neither of them found anything");

        byte[] pdf = Files.readAllBytes(output.resolve("invoice.pdf"));
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.ISO_8859_1),
                "the program wrote a PDF");
        try (PdfContainer container = PdfContainer.open(pdf)) {
            assertTrue(container.embeddedFiles().stream()
                            .map(EmbeddedFile::name).anyMatch("factur-x.xml"::equals),
                    "with the invoice inside it");
        }
        assertTrue(Files.size(output.resolve("invoice.esj.json")) > 0,
                "and the ESJ document beside it");
    }

    /** Reads the example out of the test classpath. */
    private static String source() {
        try (InputStream in = HybridInvoiceExampleTest.class.getResourceAsStream(SOURCE)) {
            assertNotNull(in, "the example " + SOURCE + " is on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Compiles the example into a directory, and fails with the diagnostics if it cannot. */
    private static void compile(String source, Path classes) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "the tests run on a JDK, so a compiler is at hand");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject unit = new SimpleJavaFileObject(
                URI.create("string:///" + CLASS + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        List<String> options = List.of("--release", RELEASE,
                "-classpath", classpath(),
                "-d", classes.toString());
        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
            boolean compiled = compiler
                    .getTask(null, files, diagnostics, options, null, List.of(unit))
                    .call();
            assertTrue(compiled, () -> "the example does not compile: " + diagnostics
                    .getDiagnostics().stream().map(Object::toString).toList());
        }
    }

    /** The class path of the modules the example names, taken from the running test. */
    private static String classpath() {
        Set<String> paths = new LinkedHashSet<>();
        for (Class<?> anchor : MODULES) {
            URL location = anchor.getProtectionDomain().getCodeSource().getLocation();
            try {
                paths.add(Path.of(location.toURI()).toString());
            } catch (URISyntaxException e) {
                throw new IllegalStateException("the module of " + anchor + " is at " + location
                        + ", which is no path this test can hand to a compiler", e);
            }
        }
        return String.join(System.getProperty("path.separator"), paths);
    }

    /**
     * Runs {@code main} with the output directory as its argument and returns what it
     * printed. The class loader closes again, and the standard output is put back.
     */
    private static List<String> run(Path classes, Path output) throws Exception {
        URL[] classpath = {classes.toUri().toURL()};
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        PrintStream before = System.out;
        try (URLClassLoader loader = new URLClassLoader(classpath,
                HybridInvoiceExampleTest.class.getClassLoader())) {
            Method main = Class.forName(CLASS, true, loader).getMethod("main", String[].class);
            System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
            try {
                main.invoke(null, (Object) new String[] {output.toString()});
            } catch (InvocationTargetException e) {
                throw new IllegalStateException("the example failed", e.getCause());
            } finally {
                System.setOut(before);
            }
        }
        return printed.toString(StandardCharsets.UTF_8).lines().toList();
    }

    /** Returns what the one line beginning with a prefix carries after it. */
    private static String after(List<String> printed, String prefix) {
        List<String> matching = printed.stream().filter(line -> line.startsWith(prefix)).toList();
        assertEquals(1, matching.size(), "one line begins with " + prefix + ": " + printed);
        return matching.get(0).substring(prefix.length());
    }
}
