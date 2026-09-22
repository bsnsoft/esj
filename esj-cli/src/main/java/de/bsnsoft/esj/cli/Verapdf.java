package de.bsnsoft.esj.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Runs a veraPDF installation over the bytes of a PDF and reads back what it said.
 *
 * <p>This tool reads the PDF/A conformance a file declares about itself and calls it a
 * declaration everywhere it prints it, because that is all it is. Validating the claim
 * needs a PDF/A validator, and the reference implementation is under a copyleft licence:
 * it is in no artefact this project publishes, and bundling or downloading one would put
 * this tool in the business of shipping somebody else's validator. So the validator is
 * the caller's, it is named by {@code --verapdf}, it runs as a process of its own, and
 * what it said travels into the report beside the declaration.
 *
 * <p>The run is a process and not a library call for the same reasons the deployment
 * documentation gives for this tool: a validator reading a file from a stranger gets its
 * own address space, its own failure, and a timeout held from the outside. Nothing is
 * fetched from a network, nothing is installed, and an installation that is not there is
 * an answer about this command line rather than about the invoice.
 *
 * <p>The report is read as XML rather than as text, because the text form is written for
 * a person and changes between releases. Three facts are taken out of it — the profile
 * the validator ran, its version, and whether the file is compliant — and they are what
 * the container block of {@code esj validate} prints beside the declaration.
 */
final class Verapdf {

    /** The flavour asked for: part 3 of ISO 19005, conformance level B. */
    private static final String FLAVOUR = "3b";

    /** The name of the executable inside an installation directory. */
    private static final String EXECUTABLE = "verapdf";

    /** The name it has on Windows. */
    private static final String EXECUTABLE_WINDOWS = "verapdf.bat";

    /** The element of the report that carries the verdict. */
    private static final String VALIDATION_REPORT = "validationReport";

    /** The element that carries the counts behind the verdict. */
    private static final String DETAILS = "details";

    /** The element that names a component of the validator and its version. */
    private static final String RELEASE_DETAILS = "releaseDetails";

    /** The components whose version is taken as the version of the validator, in order. */
    private static final List<String> VERSION_OF = List.of("core", "validation-model");

    /** What is said of a validator that names no version of itself. */
    private static final String VERSION_UNKNOWN = "version not stated";

    private Verapdf() {
        throw new AssertionError("no instances");
    }

    /**
     * Runs the validator over the bytes and returns what it found.
     *
     * @param installation what {@code --verapdf} named: an installation directory or the
     *                     executable itself
     * @param pdf          the bytes of the file, as they were handed to this run
     * @param within       how long the validator may take
     * @param console      the streams of this run, for the cost under {@code --verbose}
     * @return the profile, the version and the verdict
     * @throws CliException if the installation is not one, if the validator could not be
     *                      started, if it wrote no report this tool can read, or if it
     *                      was still running at the deadline
     */
    static Result validate(String installation, byte[] pdf, Duration within, Console console) {
        Path executable = executable(installation);
        Path directory = workspace();
        try {
            Path file = directory.resolve("input.pdf");
            Path report = directory.resolve("report.xml");
            Path diagnostics = directory.resolve("diagnostics.txt");
            Files.write(file, pdf);
            long started = System.nanoTime();
            run(executable, file, report, diagnostics, within);
            console.verbose("veraPDF finished in " + (System.nanoTime() - started) / 1_000_000
                    + " ms");
            return read(report, diagnostics, executable);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            discard(directory);
        }
    }

    /**
     * Starts the validator and waits for it, no longer than it was given.
     *
     * <p>Both of its streams go to files rather than to pipes. A validator that writes
     * more than a pipe holds while nobody drains it would stop there and wait for a
     * reader that is itself waiting for the process to end, and a deadlock is not a
     * failure mode a timeout should have to rescue.
     */
    private static void run(Path executable,
                            Path file,
                            Path report,
                            Path diagnostics,
                            Duration within) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(executable.toString(),
                "--format", "xml", "--flavour", FLAVOUR, file.toString());
        builder.redirectOutput(report.toFile());
        builder.redirectError(diagnostics.toFile());
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw CliException.input("the validator " + executable + " could not be started: "
                    + e.getMessage(), e);
        }
        try {
            if (!process.waitFor(within.toNanos(), TimeUnit.NANOSECONDS)) {
                process.destroyForcibly();
                throw CliException.limit("the validator " + executable + " was still running"
                        + " after " + within.toMillis() + " ms, which is the time this run"
                        + " was given, so there is no PDF/A verdict on this file;"
                        + " --max-runtime gives it more");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw CliException.limit("the thread waiting for the validator " + executable
                    + " was interrupted", e);
        }
    }

    /** Reads the three facts out of the report the validator wrote. */
    private static Result read(Path report, Path diagnostics, Path executable)
            throws IOException {
        Element root = parse(report, diagnostics, executable);
        Element verdict = first(root, VALIDATION_REPORT).orElseThrow(() ->
                CliException.input("the validator " + executable + " wrote no validation"
                        + " report for this file" + said(diagnostics)));
        Element details = first(verdict, DETAILS).orElse(null);
        return new Result(version(root), verdict.getAttribute("profileName"),
                Boolean.parseBoolean(verdict.getAttribute("isCompliant")),
                count(details, "failedRules"), count(details, "failedChecks"));
    }

    /**
     * Parses the report, with the parser told to resolve nothing.
     *
     * <p>The file was written by a process this run started, but it is still a document
     * this tool did not write, and a validator's report is no place to acquire an
     * external entity from.
     */
    private static Element parse(Path report, Path diagnostics, Path executable)
            throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> null);
            // Without a handler of its own the parser prints its complaint onto the error
            // stream of this process, in the language of the machine, before it throws.
            // The message a caller gets is the one below, in this tool's words.
            builder.setErrorHandler(new SilentErrors());
            return builder.parse(report.toFile()).getDocumentElement();
        } catch (ParserConfigurationException | SAXException e) {
            throw CliException.input("the validator " + executable + " wrote no report this"
                    + " tool can read" + said(diagnostics), e);
        }
    }

    /** Returns the version of the validator, out of the first component that names one. */
    private static String version(Element root) {
        NodeList components = root.getElementsByTagName(RELEASE_DETAILS);
        for (String component : VERSION_OF) {
            for (int i = 0; i < components.getLength(); i++) {
                Element release = (Element) components.item(i);
                if (component.equals(release.getAttribute("id"))
                        && !release.getAttribute("version").isEmpty()) {
                    return release.getAttribute("version");
                }
            }
        }
        return VERSION_UNKNOWN;
    }

    /** Returns the first descendant of that name, where the report carries one. */
    private static Optional<Element> first(Element parent, String name) {
        NodeList elements = parent.getElementsByTagName(name);
        return elements.getLength() == 0
                ? Optional.empty()
                : Optional.of((Element) elements.item(0));
    }

    /** Returns a count of the report, or zero where it carries none. */
    private static int count(Element details, String attribute) {
        if (details == null) {
            return 0;
        }
        try {
            String value = details.getAttribute(attribute);
            return value.isEmpty() ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Returns what the validator wrote on its error stream, for a message about it. */
    private static String said(Path diagnostics) {
        try {
            String text = Files.readString(diagnostics, StandardCharsets.UTF_8).strip();
            return text.isEmpty() ? "" : ": " + text.lines().findFirst().orElse("");
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Returns the executable of an installation.
     *
     * <p>A caller names the installation directory, which is what an installer leaves
     * behind, or the executable inside it, which is what a caller with a validator on the
     * path already has. Both are accepted; anything else is an answer about this command
     * line, given before a document is read.
     */
    private static Path executable(String installation) {
        Path path;
        try {
            path = Path.of(installation);
        } catch (InvalidPathException e) {
            throw CliException.input("--verapdf is not a usable path: " + installation, e);
        }
        if (Files.isRegularFile(path)) {
            return path.toAbsolutePath();
        }
        if (Files.isDirectory(path)) {
            for (String name : List.of(EXECUTABLE, EXECUTABLE_WINDOWS)) {
                Path candidate = path.resolve(name);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath();
                }
            }
            throw CliException.input("--verapdf names the directory " + installation
                    + ", which holds no " + EXECUTABLE + " or " + EXECUTABLE_WINDOWS
                    + "; name the installation directory of veraPDF or its executable");
        }
        throw CliException.input("--verapdf names " + installation + ", which is neither a"
                + " file nor a directory; name the installation directory of veraPDF or"
                + " its executable");
    }

    /** Returns a directory of this run's own, for the file and the two streams. */
    private static Path workspace() {
        try {
            return Files.createTempDirectory("esj-verapdf");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Removes the directory this run created, and nothing else.
     *
     * <p>Every path removed here was written by this method's own run into a directory
     * this method's own run created, and a failure to remove one is not a verdict about
     * anything: the report has been read by then.
     */
    private static void discard(Path directory) {
        try (var entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                Files.deleteIfExists(entry);
            }
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            // Nothing to say: a temporary file left behind is the operating system's to
            // reclaim, and the answer about the document does not depend on it.
        }
    }

    /**
     * An error handler that says nothing and lets the parser stop where it would stop.
     *
     * <p>A report that is not XML is answered by {@link #parse}, in English, naming the
     * validator and the first line it wrote on its error stream. The parser's default
     * handler would print its own sentence onto this process's error stream first, in
     * whatever language the machine is configured for.
     */
    private static final class SilentErrors implements ErrorHandler {

        @Override
        public void warning(SAXParseException exception) {
            // A report this tool can still read: nothing to say about it.
        }

        @Override
        public void error(SAXParseException exception) {
            // Likewise; validity is not asked of the report, only well-formedness.
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }

    /**
     * What a validator said about one file.
     *
     * @param version      the version of the validator that ran
     * @param profile      the validation profile it ran, as it names it
     * @param compliant    whether the file conforms to that profile
     * @param failedRules  how many rules of the profile the file broke
     * @param failedChecks how many individual checks failed
     */
    record Result(String version,
                  String profile,
                  boolean compliant,
                  int failedRules,
                  int failedChecks) {

        /** The name this tool prints the validator under. */
        static final String VALIDATOR = "veraPDF";

        /**
         * Returns the row of the container block: who ran, what they ran, what they said.
         *
         * @return the sentence, for the line beside the declaration
         */
        String describe() {
            String profileName = profile == null || profile.isEmpty()
                    ? "the flavour " + FLAVOUR.toUpperCase(Locale.ROOT)
                    : ValueText.quoted(profile);
            return VALIDATOR + " " + version + ", " + profileName + ": "
                    + (compliant ? "PASS" : "FAIL (" + failedRules + " failed rules, "
                            + failedChecks + " failed checks)");
        }
    }
}
