package de.bsnsoft.esj.syntax;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import de.bsnsoft.esj.Preview;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Makes a validation pack from a {@link PackRecipe}: fetches the files the recipe names,
 * weighs each against the digest the recipe pins, compiles the Schematron files to XSLT,
 * copies the schema components out of a bundled pack and writes the manifest.
 *
 * <p>It is the only code of this project that opens a network connection, and it opens
 * one only to the base URL of a recipe this module carries, only over {@code https}, and
 * only because a caller asked for that recipe. Nothing a document says reaches it.
 *
 * <p>Nothing is written until everything has been fetched, weighed and compiled: a file
 * that is not the one the recipe pins is refused by name and leaves the target as it was.
 * The pack is then written into a directory of the pack directory whose name begins with a
 * dot, which a pack directory is not read from, and moved into place in one step. A target that
 * already holds the same pack — every file with the digest the new manifest would record —
 * is left alone; one that holds a different pack is refused unless the caller asked to
 * replace it, and even then only files its own manifest lists are removed.
 *
 * <p>The class is a preview: making a pack from a recipe is new in this release line, and
 * the class may change in any minor release.
 */
@Preview
public final class PackFetcher {

    /** The largest recipe file this module fetches, whatever a recipe says. */
    private static final long MAX_FILE_BYTES = 16L * 1024 * 1024;

    private static final JsonFactory JSON = new JsonFactory();

    private PackFetcher() {
        throw new AssertionError("no instances");
    }

    /**
     * How a file of a recipe is fetched. The one this module offers is {@link #https};
     * a test passes one that answers from memory.
     */
    @FunctionalInterface
    public interface Download {

        /**
         * Fetches the bytes behind a URL.
         *
         * @param url      the URL
         * @param maxBytes the most bytes the answer may have; a longer one is refused
         * @return the bytes
         * @throws IOException if the file cannot be fetched
         */
        byte[] get(URI url, long maxBytes) throws IOException;
    }

    /** What a fetch did with the target directory. */
    public enum Outcome {

        /** The pack was written where there was none. */
        WRITTEN,

        /** The target already held the same pack, and it was left alone. */
        UNCHANGED,

        /** The target held a different pack, and the caller asked to replace it. */
        REPLACED
    }

    /**
     * What one fetch did.
     *
     * @param outcome   what happened to the target
     * @param pack      the pack as it now stands in the target, read back from there
     * @param fetched   how many files were fetched
     * @param fetchTime how long fetching them took
     * @param compileTime how long compiling the Schematron files took
     * @param copied    the components copied out of bundled packs, as {@code pack component}
     */
    public record Result(Outcome outcome,
                         Pack pack,
                         int fetched,
                         Duration fetchTime,
                         Duration compileTime,
                         List<String> copied) {

        /**
         * Refuses a missing member.
         *
         * @param outcome   what happened to the target
         * @param pack      the pack as it now stands in the target, read back from there
         * @param fetched   how many files were fetched
         * @param fetchTime how long fetching them took
         * @param compileTime how long compiling the Schematron files took
         * @param copied    the components copied out of bundled packs
         */
        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(fetchTime, "fetchTime");
            Objects.requireNonNull(compileTime, "compileTime");
            copied = List.copyOf(copied);
        }
    }

    /**
     * Returns the download that goes over the network: {@code https} only, no redirect
     * followed, a bound on the connection and on the whole request, and an answer refused
     * as soon as it is longer than the file may be. A proxy is the one the Java runtime is
     * configured with ({@code https.proxyHost}, {@code https.proxyPort}).
     *
     * @param userAgent what the request calls the tool, for example {@code esj/0.9.4}
     * @param timeout   how long one file may take
     * @return the download
     */
    public static Download https(String userAgent, Duration timeout) {
        Objects.requireNonNull(userAgent, "userAgent");
        Objects.requireNonNull(timeout, "timeout");
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(timeout)
                .build();
        return (url, maxBytes) -> {
            if (!"https".equals(url.getScheme())) {
                throw new IOException("a recipe file is fetched over https, and " + url
                        + " is not");
            }
            HttpRequest request = HttpRequest.newBuilder(url)
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("fetching " + url + " was interrupted", e);
            }
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException(url + " answered with HTTP status "
                            + response.statusCode());
                }
                byte[] bytes = body.readNBytes((int) Math.min(maxBytes + 1, MAX_FILE_BYTES + 1));
                if (bytes.length > maxBytes) {
                    throw new IOException(url + " is longer than the " + maxBytes
                            + " bytes the recipe gives it");
                }
                return bytes;
            }
        };
    }

    /**
     * Makes the pack of a recipe in a pack directory.
     *
     * @param recipe    the recipe
     * @param into      the pack directory; the pack is written to
     *                  {@code <into>/<id>/<version>/<release>}, and the directory is made
     *                  where it is not there
     * @param replace   whether a different pack already in the target is replaced rather
     *                  than refused
     * @param download  how the files are fetched
     * @param retrieved the day the files are fetched, which the manifest records
     * @param tool      the name and version of the tool, which the manifest records as what
     *                  compiled the rule sets
     * @return what was done
     * @throws PackException        if a file cannot be fetched, is not the file the recipe
     *                              pins, cannot be compiled, or the target holds something
     *                              this method does not overwrite
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Result fetch(PackRecipe recipe,
                               Path into,
                               boolean replace,
                               Download download,
                               LocalDate retrieved,
                               String tool) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(into, "into");
        Objects.requireNonNull(download, "download");
        Objects.requireNonNull(retrieved, "retrieved");
        Objects.requireNonNull(tool, "tool");

        SortedMap<String, byte[]> files = new TreeMap<>();
        long fetchStart = System.nanoTime();
        Map<PackRecipe.Schematron, byte[]> sources = new LinkedHashMap<>();
        for (PackRecipe.Schematron rule : recipe.schematron()) {
            byte[] bytes = fetchOne(recipe, rule, download);
            sources.put(rule, bytes);
            files.put(rule.source(), bytes);
        }
        Duration fetchTime = Duration.ofNanos(System.nanoTime() - fetchStart);

        long compileStart = System.nanoTime();
        for (Map.Entry<PackRecipe.Schematron, byte[]> source : sources.entrySet()) {
            PackRecipe.Schematron rule = source.getKey();
            byte[] xslt = SchematronCompiler.compile(source.getValue(), rule.fileName());
            SchematronCompiler.check(xslt, rule.fileName());
            files.put(rule.compiled(), xslt);
        }
        Duration compileTime = Duration.ofNanos(System.nanoTime() - compileStart);

        List<PackComponent> copied = new ArrayList<>();
        List<String> copiedNames = new ArrayList<>();
        for (PackRecipe.Copy copy : recipe.copies()) {
            Pack bundled = Packs.bundled(copy.pack());
            PackComponent component = bundled.components().stream()
                    .filter(candidate -> candidate.name().equals(copy.component()))
                    .findFirst()
                    .orElseThrow(() -> new PackException("the recipe " + recipe.name()
                            + " copies the component " + copy.component() + " of "
                            + copy.pack() + ", which that pack does not have"));
            for (String path : withLicence(component)) {
                if (files.containsKey(path)) {
                    throw new PackException("the recipe " + recipe.name() + " puts two files"
                            + " at " + path);
                }
                files.put(path, bundled.read(path));
            }
            copied.add(component);
            copiedNames.add(copy.pack() + " " + copy.component());
        }

        files.put(NOTICE, notice(recipe).getBytes(StandardCharsets.UTF_8));
        SortedMap<String, String> inventory = new TreeMap<>();
        files.forEach((path, bytes) -> inventory.put(path, sha256(bytes)));
        byte[] manifest = manifest(recipe, copied, inventory, retrieved, tool);

        Path root = into.toAbsolutePath().normalize();
        Path target = root.resolve(recipe.id()).resolve(recipe.version())
                .resolve(recipe.release());
        Outcome outcome = place(recipe, root, target, files, manifest, inventory, replace);
        Pack pack = Packs.fromDirectory(target, PackSource.DIRECTORY);
        // Reading back what is now there, every file weighed against the manifest, is the
        // check that what was written is the pack that was compiled.
        pack.files().keySet().forEach(pack::read);
        return new Result(outcome, pack, sources.size(), fetchTime, compileTime, copiedNames);
    }

    /** The name of the notice a fetched pack carries. */
    static final String NOTICE = "NOTICE";

    private static byte[] fetchOne(PackRecipe recipe, PackRecipe.Schematron rule,
                                   Download download) {
        URI url = recipe.url(rule.file());
        if (rule.bytes() > MAX_FILE_BYTES) {
            throw new PackException("the recipe " + recipe.name() + " gives " + rule.file()
                    + " " + rule.bytes() + " bytes, more than a recipe file may have");
        }
        byte[] bytes;
        try {
            bytes = download.get(url, rule.bytes());
        } catch (IOException e) {
            throw new PackException("could not fetch " + url + ": " + e.getMessage()
                    + "; nothing was written", e);
        }
        String weighed = sha256(bytes);
        if (bytes.length != rule.bytes() || !weighed.equals(rule.sha256())) {
            throw new PackException("the file " + rule.file() + " fetched from " + url
                    + " is not the file the recipe " + recipe.name() + " pins: it has "
                    + bytes.length + " bytes and SHA-256 " + weighed + ", the recipe "
                    + rule.bytes() + " bytes and " + rule.sha256() + "; nothing was written");
        }
        return bytes;
    }

    /** Returns the files of a component together with its licence file. */
    private static List<String> withLicence(PackComponent component) {
        TreeSet<String> paths = new TreeSet<>(component.files());
        paths.add(component.licenseFile());
        return List.copyOf(paths);
    }

    /**
     * Puts the files into the target, or finds them there already.
     */
    private static Outcome place(PackRecipe recipe,
                                 Path into,
                                 Path target,
                                 SortedMap<String, byte[]> files,
                                 byte[] manifest,
                                 SortedMap<String, String> inventory,
                                 boolean replace) {
        Path parent = target.getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new PackException("the directory " + parent + " could not be made: "
                    + e.getMessage(), e);
        }
        Outcome outcome = Outcome.WRITTEN;
        if (Files.exists(target)) {
            if (!Files.isRegularFile(target.resolve(PackManifest.FILE))) {
                if (!isEmptyDirectory(target)) {
                    throw new PackException("the directory " + target + " exists and holds"
                            + " no pack; nothing was written");
                }
                delete(target);
            } else {
                Pack existing;
                try {
                    existing = Packs.fromDirectory(target, PackSource.DIRECTORY);
                } catch (PackException e) {
                    throw new PackException("the directory " + target + " holds a pack this"
                            + " module cannot read (" + e.getMessage() + "); nothing was"
                            + " written", e);
                }
                if (sameFiles(existing, inventory)) {
                    return Outcome.UNCHANGED;
                }
                if (!replace) {
                    throw new PackException("the directory " + target + " holds a different"
                            + " pack " + existing.directory() + "; --replace replaces it,"
                            + " and nothing was written");
                }
                List<String> strays = strays(existing, target);
                if (!strays.isEmpty()) {
                    throw new PackException("the directory " + target + " holds files its"
                            + " manifest does not list (" + String.join(", ", strays)
                            + "); a pack is replaced only where every file of it is one"
                            + " the pack lists, and nothing was replaced");
                }
                outcome = Outcome.REPLACED;
            }
        }
        Path staging = staging(into, recipe);
        try {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                write(staging.resolve(file.getKey()), file.getValue());
            }
            write(staging.resolve(PackManifest.FILE), manifest);
            if (outcome == Outcome.REPLACED) {
                Path old = staging(into, recipe);
                delete(old);
                move(target, old);
                try {
                    move(staging, target);
                } catch (IOException e) {
                    // The pack that was there goes back where it was, and the caller learns
                    // why the new one is not.
                    move(old, target);
                    throw e;
                }
                removeQuietly(old);
            } else {
                move(staging, target);
            }
        } catch (IOException e) {
            removeQuietly(staging);
            throw new PackException("the pack could not be written to " + target + ": "
                    + e.getMessage(), e);
        }
        return outcome;
    }

    /**
     * Tells whether a pack in the target is the pack about to be written: its manifest
     * records the same files with the same digests, and each file on disk weighs what the
     * manifest records. A file beside them that the manifest does not list is no part of
     * the pack and is not read by it, so it does not make the pack a different one.
     */
    private static boolean sameFiles(Pack existing, SortedMap<String, String> inventory) {
        if (!existing.files().equals(inventory)) {
            return false;
        }
        try {
            existing.files().keySet().forEach(existing::read);
        } catch (PackException e) {
            return false;
        }
        return true;
    }

    /** Returns the files under a pack directory that its manifest does not list. */
    private static List<String> strays(Pack pack, Path directory) {
        TreeSet<String> listed = new TreeSet<>(pack.files().keySet());
        listed.add(PackManifest.FILE);
        List<String> strays = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(directory)) {
            tree.filter(path -> !Files.isDirectory(path))
                    .map(path -> relative(directory, path))
                    .filter(path -> !listed.contains(path))
                    .forEach(strays::add);
        } catch (IOException e) {
            throw new PackException("the directory " + directory + " could not be read", e);
        }
        return strays;
    }

    /** Returns a fresh directory beside the packs, whose name begins with a dot. */
    private static Path staging(Path into, PackRecipe recipe) {
        try {
            return Files.createTempDirectory(into, ".esj-fetch-" + recipe.name() + "-");
        } catch (IOException e) {
            throw new PackException("no working directory could be made in " + into + ": "
                    + e.getMessage(), e);
        }
    }

    private static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to);
        } catch (FileAlreadyExistsException e) {
            throw new IOException(to + " appeared while the pack was being written", e);
        }
    }

    private static boolean isEmptyDirectory(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    /** Deletes an empty directory, which is all this is ever called with. */
    private static void delete(Path directory) {
        try {
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            throw new PackException("the directory " + directory + " could not be removed: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Removes a directory this class made or moved aside and whose every file it knows,
     * deepest entries first.
     */
    private static void removeQuietly(Path directory) {
        try (Stream<Path> tree = Files.walk(directory)) {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            // What is left is a directory whose name begins with a dot, which no pack
            // directory is read from; the caller's error is the one that matters.
        }
    }

    private static String relative(Path root, Path path) {
        StringBuilder text = new StringBuilder();
        for (Path segment : root.relativize(path)) {
            if (text.length() > 0) {
                text.append('/');
            }
            text.append(segment);
        }
        return text.toString();
    }

    /** Writes the manifest of the pack. */
    private static byte[] manifest(PackRecipe recipe,
                                   List<PackComponent> copied,
                                   SortedMap<String, String> inventory,
                                   LocalDate retrieved,
                                   String tool) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                        .withObjectEmptySeparator("")
                        .withArrayEmptySeparator(""))
                .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                .withArrayIndenter(new DefaultIndenter("  ", "\n"));
        try (JsonGenerator json = JSON.createGenerator(out, JsonEncoding.UTF8)) {
            json.setPrettyPrinter(printer);
            json.writeStartObject();
            json.writeStringField("format", "esj-validation-pack");
            json.writeStringField("formatVersion", "1");
            json.writeStringField("id", recipe.id());
            json.writeStringField("version", recipe.version());
            json.writeStringField("release", recipe.release());
            json.writeStringField("title", recipe.title());
            json.writeStringField("retrieved", retrieved.toString());
            json.writeStringField("note", recipe.note());
            json.writeObjectFieldStart("recipe");
            json.writeStringField("name", recipe.name());
            json.writeStringField("publisher", recipe.publisher());
            json.writeStringField("repository", recipe.repository());
            if (recipe.tag().isPresent()) {
                json.writeStringField("tag", recipe.tag().get());
            }
            if (recipe.branch().isPresent()) {
                json.writeStringField("branch", recipe.branch().get());
            }
            json.writeStringField("commit", recipe.commit());
            json.writeStringField("compiledWith", SchematronCompiler.SKELETON + ", "
                    + net.sf.saxon.Version.getProductTitle() + ", " + tool);
            json.writeEndObject();
            strings(json, "baseProfiles", recipe.baseProfiles());
            json.writeArrayFieldStart("components");
            for (PackComponent component : copied) {
                component(json, component.name(), component.role().token(),
                        List.copyOf(component.syntaxes()).stream().sorted().toList(),
                        component.profiles(), component.entries(), component.files(),
                        component.license(), component.licenseFile(), component.source(),
                        component.obtainedFrom(), component.unmodified());
            }
            for (PackRecipe.Schematron rule : recipe.schematron()) {
                Map<String, String> entries = new LinkedHashMap<>();
                rule.syntaxes().forEach(syntax -> entries.put(syntax, rule.compiled()));
                component(json, rule.name(), ComponentRole.SCHEMATRON_XSLT.token(),
                        rule.syntaxes(), rule.profiles(), entries,
                        List.of(rule.compiled(), rule.source()), rule.license(), NOTICE,
                        recipe.repository() + "/tree/" + recipe.treeish() + "/"
                                + rule.file().substring(0, rule.file().lastIndexOf('/') + 1),
                        recipe.url(rule.file()).toString(), false);
            }
            json.writeEndArray();
            json.writeArrayFieldStart("files");
            for (Map.Entry<String, String> file : inventory.entrySet()) {
                json.writeStartObject();
                json.writeStringField("path", file.getKey());
                json.writeStringField("sha256", file.getValue());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        } catch (IOException e) {
            throw new PackException("the manifest of " + recipe.identity()
                    + " could not be written", e);
        }
        out.write('\n');
        return out.toByteArray();
    }

    private static void component(JsonGenerator json,
                                  String name,
                                  String role,
                                  List<String> syntaxes,
                                  List<String> profiles,
                                  Map<String, String> entries,
                                  List<String> files,
                                  String license,
                                  String licenseFile,
                                  String source,
                                  String obtainedFrom,
                                  boolean unmodified) throws IOException {
        json.writeStartObject();
        json.writeStringField("name", name);
        json.writeStringField("role", role);
        json.writeObjectFieldStart("appliesTo");
        strings(json, "syntax", syntaxes);
        strings(json, "profile", profiles);
        json.writeEndObject();
        json.writeObjectFieldStart("entry");
        for (String syntax : syntaxes) {
            json.writeStringField(syntax, entries.get(syntax));
        }
        json.writeEndObject();
        strings(json, "files", files);
        json.writeStringField("license", license);
        json.writeStringField("licenseFile", licenseFile);
        json.writeStringField("source", source);
        json.writeStringField("obtainedFrom", obtainedFrom);
        json.writeBooleanField("unmodified", unmodified);
        json.writeEndObject();
    }

    private static void strings(JsonGenerator json, String name, List<String> values)
            throws IOException {
        json.writeArrayFieldStart(name);
        for (String value : values) {
            json.writeString(value);
        }
        json.writeEndArray();
    }

    /**
     * Writes the notice a fetched pack carries: what the files are, where each came from,
     * under which terms, and that the pack was made on this machine. It carries no date and
     * no version of the tool, so that two fetches of one recipe write the same bytes; the
     * manifest records what compiled the rule sets.
     */
    static String notice(PackRecipe recipe) {
        StringBuilder text = new StringBuilder();
        text.append(recipe.title()).append('\n').append('\n');
        text.append("This directory is a validation pack written by esj packs fetch from the")
                .append(" recipe ").append(recipe.name()).append(".\n")
                .append(recipe.note()).append("\n\n");
        text.append("Fetched from ").append(recipe.publisher()).append(", ")
                .append(recipe.repository()).append(", ").append(recipe.revision())
                .append(" (").append(recipe.releaseName()).append(", published ")
                .append(recipe.published()).append("), byte for byte and each weighed")
                .append(" against the SHA-256 the recipe pins:\n\n");
        for (PackRecipe.Schematron rule : recipe.schematron()) {
            text.append("  ").append(rule.source()).append('\n')
                    .append("    ").append(rule.about()).append('\n')
                    .append("    licence: ").append(rule.license()).append('\n')
                    .append("    from ").append(recipe.url(rule.file())).append('\n')
                    .append("    SHA-256 ").append(rule.sha256()).append('\n');
        }
        text.append('\n');
        text.append("Each of them was compiled on this machine to the .xslt file beside it,")
                .append(" with the ").append(SchematronCompiler.SKELETON)
                .append(". A compiled file is derived from the file it was compiled from")
                .append(" and is under the same terms.\n\n");
        text.append("The directories under xsd/ are copies of the schema components of the")
                .append(" pack");
        List<String> packs = recipe.copies().stream().map(PackRecipe.Copy::pack)
                .distinct().toList();
        text.append(packs.size() == 1 ? " " : "s ").append(String.join(", ", packs))
                .append(" that esj carries, with the notice of each schema set beside it.\n");
        return text.toString();
    }

    /** Returns the SHA-256 of some bytes, as lower case hexadecimal. */
    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("this platform has no SHA-256", e);
        }
    }
}
