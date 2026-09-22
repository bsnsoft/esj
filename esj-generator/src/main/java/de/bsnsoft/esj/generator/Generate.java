package de.bsnsoft.esj.generator;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import de.bsnsoft.esj.model.Registry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Writes the checked-in artefacts that are derived from a term registry: the sources of
 * the typed view {@code esj-typed}, the constrained builder under {@code typed.build},
 * the code list enums of {@code esj-invoice} and the model schema of one edition, for
 * example {@code schema/esj-en16931-2017.schema.json}.
 *
 * <p>Run it from the repository root with
 * {@code mvn -B -Pgenerate -pl esj-generator -am process-classes}, which is where the
 * arguments below are filled in; the {@code generate} profile is what carries the
 * execution, so a command without it finds no such execution. It is a build tool: no module
 * depends on it, and nothing it emits depends on when, where or by whom it ran. Running
 * it twice over the same registry produces the same bytes, which is what lets the build
 * check that the checked-in files are the ones the registry yields.
 *
 * <p>The arguments are:
 *
 * <pre>
 *   --schema &lt;file&gt;             the model schema to write
 *   --format-schema &lt;file&gt;      the format schema the model schema extends
 *   --typed &lt;directory&gt;         the source root of esj-typed, optional
 *   --typed-package &lt;name&gt;      the package the typed view is emitted into
 *   --edition &lt;key&gt;             the edition key of the core registry, default 2017
 *   --derivable &lt;file&gt;          the terms a derivation policy writes
 *   --profiles &lt;directory&gt;      the profile overlays the builder is generated for
 *   --enums &lt;directory&gt;         the source root of esj-invoice
 *   --enum-facts &lt;file&gt;         the configuration the code list enums are generated from
 *   --rules &lt;directory&gt;         the rule directory holding the packs and their snapshots
 *   --extension &lt;file&gt;          an extension registry to include, optional
 *   --overlay &lt;directory&gt;       the source root an overlay over that extension is
 *                                  written into, optional and only with --extension
 *   --overlay-prefix &lt;name&gt;     the name of the entry point class of that overlay,
 *                                  which also prefixes every type of it, for example B2c
 * </pre>
 *
 * <p>{@code --edition} names one of the core registries the build carries
 * ({@code Registry.editions()}), and one run writes the artefacts of one edition. The
 * repository checks in a typed view per edition, each in a package of its own, so a run
 * over another edition names that package with {@code --typed-package}; a run that writes
 * a model schema alone is left without {@code --typed}.
 *
 * <p>Three layers are written for the default edition only, and a run over another one
 * leaves their options out: the constrained builder ({@code --derivable}, {@code --profiles})
 * and the code list enums of the domain API ({@code --enums}, {@code --enum-facts},
 * {@code --rules}). A builder is a statement about which terms a profile makes mandatory,
 * and no profile of another edition is written; {@code docs/editions.md} says so per layer.
 * Either group is given whole or not at all.
 *
 * <p>The typed view of the repository is generated from the core registry of an edition
 * alone, so the runs that write it name no {@code --extension}. A run of its own names one
 * together with {@code --overlay}: it writes nothing into {@code esj-typed} and emits the
 * overlay of that extension — the small view and editor that show its terms and nothing
 * else — into their own module. {@code --extension} without {@code --overlay} includes the
 * extension in the typed view instead, which no run of this repository does and which a
 * project shipping its own view uses.
 *
 * <p>An overlay reaches its registry through the static method of
 * {@code de.bsnsoft.esj.model.Registry} named after its prefix — {@code B2c}
 * yields {@code Registry.b2cExtension()} — and it is emitted into the package of the
 * typed view with the last segment replaced by the prefix in lower case. An extension is
 * written against one edition and the overlay is emitted over that one, so an overlay run
 * leaves {@code --edition} at the edition the extension imports.
 *
 * <p>The class writes no console output. A problem it cannot handle — an argument that is
 * missing, a registry that contradicts itself, a file it cannot write — ends the run with
 * an exception.
 */
public final class Generate {

    private static final String BUILD_PACKAGE_PATH = "de/bsnsoft/esj/typed/build";

    private static final String CODE_PACKAGE_PATH = "de/bsnsoft/esj/invoice/code";

    private Generate() {
    }

    /**
     * Runs the generator.
     *
     * @param args the arguments described in the class comment
     * @throws IOException              if a file cannot be read or written
     * @throws IllegalArgumentException if an argument is missing or unknown
     */
    public static void main(String[] args) throws IOException {
        generate(Options.parse(args));
    }

    /**
     * Emits every generated artefact the options ask for.
     *
     * @param options where to read from and where to write to
     * @throws IOException if a file cannot be read or written
     */
    static void generate(Options options) throws IOException {
        Optional<Registry> extension = extension(options);
        Registry core = Registry.forEdition(options.edition());
        Registry registry = extension.map(core::withExtension).orElse(core);
        String version = registry.version().orElseThrow(() -> new IllegalStateException(
                "the registry carries no version, which the header of every generated file names"));

        if (options.overlayDirectory().isPresent()) {
            overlay(options, registry, extension.orElseThrow(() -> new IllegalArgumentException(
                    "the generator writes the overlay of the registry --extension names")),
                    version);
            return;
        }

        String header = JavaText.header(registry.edition(), version);

        if (options.typedDirectory().isPresent()) {
            Path typedRoot = options.typedDirectory().get();
            String typedPackage = options.typedPackage();
            Map<String, String> sources = new TypedSources(registry, header, typedPackage).sources();
            writeSources(typedRoot.resolve(typedPackage.replace('.', '/')), sources,
                    JavaText.HEADER_MARKER);

            if (options.derivable().isPresent()) {
                Map<String, String> builder = new BuilderSources(registry,
                        BuildFacts.derivable(options.derivable().get()),
                        BuildFacts.profiles(options.profiles().orElseThrow()),
                        header).sources();
                writeSources(typedRoot.resolve(BUILD_PACKAGE_PATH), builder,
                        JavaText.HEADER_MARKER);
            }
        }

        if (options.enumDirectory().isPresent()) {
            writeSources(options.enumDirectory().get().resolve(CODE_PACKAGE_PATH),
                    enums(options, registry), JavaText.CODE_LIST_HEADER_MARKER);
        }

        String formatSchemaId = schemaIdentifier(options.formatSchema());
        String comment = "Generated by esj-generator from EN16931 registry " + registry.edition()
                + ", registry version " + version + ". Do not edit.";
        String fileName = options.schemaFile().getFileName().toString();
        String registryFile = "model/en16931/" + options.edition() + ".json";
        write(options.schemaFile(),
                new ModelSchema(registry, fileName, registryFile, formatSchemaId, comment).source());
    }

    private static Map<String, String> enums(Options options, Registry registry) throws IOException {
        EnumFacts.Configuration configuration = EnumFacts.read(options.enumFacts().orElseThrow());
        Path pack = options.rules().orElseThrow()
                .resolve(configuration.packId()).resolve(configuration.packVersion());
        Map<String, String> dates = EnumFacts.snapshotDates(pack.resolve("pack.json"));
        Map<String, EnumFacts.Snapshot> snapshots = new LinkedHashMap<>();
        for (Map.Entry<String, String> named : dates.entrySet()) {
            snapshots.put(named.getKey(), EnumFacts.snapshot(
                    pack.resolve("codelists").resolve(named.getKey())
                            .resolve(named.getValue() + ".json")));
        }
        return new EnumSources(configuration, snapshots, registry).sources();
    }

    /**
     * Emits the overlay of one extension registry. It is a run of its own: the typed view,
     * the constrained builder, the code list enums and the model schema of the repository
     * describe the core model, and a run that writes the overlay leaves all four alone.
     *
     * @param options  where to write to
     * @param registry the core registry with the extension loaded
     * @param loaded   the extension registry alone
     * @param version  the version of the core registry file
     * @throws IOException if a file cannot be written
     */
    private static void overlay(Options options, Registry registry, Registry loaded, String version)
            throws IOException {
        String prefix = options.overlayPrefix().orElseThrow(() -> new IllegalArgumentException(
                "the generator needs the option --overlay-prefix beside --overlay"));
        String packageName = OverlaySources.packageOf(prefix);
        String header = JavaText.header(registry.edition() + " with " + loaded.edition(), version);
        Map<String, String> sources =
                new OverlaySources(registry, loaded, packageName, prefix, header).sources();
        writeSources(options.overlayDirectory().orElseThrow()
                .resolve(packageName.replace('.', '/')), sources, JavaText.HEADER_MARKER);
    }

    private static Optional<Registry> extension(Options options) throws IOException {
        if (options.extension().isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(options.extension().get())) {
            return Optional.of(Registry.load(in));
        }
    }

    private static void writeSources(Path directory, Map<String, String> sources, String marker)
            throws IOException {
        Files.createDirectories(directory);
        for (Map.Entry<String, String> source : sources.entrySet()) {
            write(directory.resolve(source.getKey()), source.getValue());
        }
        for (Path stale : staleSources(directory, sources.keySet(), marker)) {
            Files.delete(stale);
        }
    }

    private static List<Path> staleSources(Path directory, Set<String> written, String marker)
            throws IOException {
        List<Path> stale = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".java") || written.contains(name) || !Files.isRegularFile(file)) {
                    continue;
                }
                if (Files.readString(file, StandardCharsets.UTF_8).startsWith(marker)) {
                    stale.add(file);
                }
            }
        }
        return stale;
    }

    private static void write(Path file, String content) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (Files.exists(file) && Arrays.equals(Files.readAllBytes(file), bytes)) {
            return;
        }
        Files.write(file, bytes);
    }

    private static String schemaIdentifier(Path formatSchema) throws IOException {
        try (InputStream in = Files.newInputStream(formatSchema);
             JsonParser parser = new JsonFactory().createParser(in)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalStateException("the format schema is a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                if ("$id".equals(field)) {
                    return parser.getText();
                }
                parser.skipChildren();
            }
            throw new IllegalStateException("the format schema carries no $id");
        }
    }

    /**
     * The arguments of one run.
     *
     * @param typedDirectory the source root the typed view is written into, or an empty
     *                       optional where the run writes the model schema alone
     * @param typedPackage   the package the typed view is emitted into, which is the one
     *                       of the default edition unless the run names another
     * @param schemaFile     the model schema that is written
     * @param formatSchema   the format schema the model schema extends
     * @param edition        the edition key of the core registry the run reads
     * @param derivable      the file naming the terms a derivation policy writes, or an
     *                       empty optional where the run writes no constrained builder
     * @param profiles       the directory holding the profile overlays, present with
     *                       {@code derivable} and absent without it
     * @param enumDirectory  the source root the code list enums are written into, or an
     *                       empty optional where the run writes none
     * @param enumFacts      the configuration the code list enums are generated from,
     *                       present with {@code enumDirectory} and absent without it
     * @param rules          the rule directory holding the packs and their snapshots,
     *                       present with {@code enumDirectory} and absent without it
     * @param extension      an extension registry to include, if one was named
     * @param overlayDirectory the source root the overlay of that extension is written
     *                       into, or an empty optional where the run writes no overlay
     * @param overlayPrefix  the name of the entry point class of that overlay, present
     *                       with {@code overlayDirectory} and absent without it
     */
    record Options(Optional<Path> typedDirectory,
                   String typedPackage,
                   Path schemaFile,
                   Path formatSchema,
                   String edition,
                   Optional<Path> derivable,
                   Optional<Path> profiles,
                   Optional<Path> enumDirectory,
                   Optional<Path> enumFacts,
                   Optional<Path> rules,
                   Optional<Path> extension,
                   Optional<Path> overlayDirectory,
                   Optional<String> overlayPrefix) {

        /**
         * Copies the arguments, checks that each one is present and that the two optional
         * groups are each given whole.
         *
         * @throws NullPointerException     if an argument is {@code null}
         * @throws IllegalArgumentException if one option of a group is given without the others
         */
        Options {
            Objects.requireNonNull(typedDirectory, "typedDirectory");
            Objects.requireNonNull(typedPackage, "typedPackage");
            Objects.requireNonNull(schemaFile, "schemaFile");
            Objects.requireNonNull(formatSchema, "formatSchema");
            Objects.requireNonNull(edition, "edition");
            Objects.requireNonNull(derivable, "derivable");
            Objects.requireNonNull(profiles, "profiles");
            Objects.requireNonNull(enumDirectory, "enumDirectory");
            Objects.requireNonNull(enumFacts, "enumFacts");
            Objects.requireNonNull(rules, "rules");
            Objects.requireNonNull(extension, "extension");
            Objects.requireNonNull(overlayDirectory, "overlayDirectory");
            Objects.requireNonNull(overlayPrefix, "overlayPrefix");
            if (derivable.isPresent() != profiles.isPresent()) {
                throw new IllegalArgumentException(
                        "the constrained builder needs both --derivable and --profiles");
            }
            if (derivable.isPresent() && typedDirectory.isEmpty()) {
                throw new IllegalArgumentException(
                        "the constrained builder is written into the source root --typed names");
            }
            if (enumDirectory.isPresent() != enumFacts.isPresent()
                    || enumDirectory.isPresent() != rules.isPresent()) {
                throw new IllegalArgumentException(
                        "the code list enums need --enums, --enum-facts and --rules together");
            }
            if (overlayDirectory.isPresent() && extension.isEmpty()) {
                throw new IllegalArgumentException(
                        "an overlay is generated from the registry --extension names");
            }
            if (overlayDirectory.isPresent() != overlayPrefix.isPresent()) {
                throw new IllegalArgumentException(
                        "an overlay needs both --overlay and --overlay-prefix");
            }
        }

        /**
         * Parses the command line.
         *
         * @param args the arguments
         * @return the options
         * @throws IllegalArgumentException if an option is missing, unknown or without a value
         */
        static Options parse(String[] args) {
            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("the option " + args[i] + " takes a value");
                }
                values.put(args[i], args[i + 1]);
            }
            Optional<Path> typed = optional(values, "--typed");
            String typedPackage = Optional.ofNullable(values.remove("--typed-package"))
                    .orElse(TypedSources.DEFAULT_PACKAGE);
            Path schema = required(values, "--schema");
            Path formatSchema = required(values, "--format-schema");
            String edition = Optional.ofNullable(values.remove("--edition"))
                    .orElse(Registry.DEFAULT_EDITION);
            Optional<Path> derivable = optional(values, "--derivable");
            Optional<Path> profiles = optional(values, "--profiles");
            Optional<Path> enums = optional(values, "--enums");
            Optional<Path> enumFacts = optional(values, "--enum-facts");
            Optional<Path> rules = optional(values, "--rules");
            Optional<Path> extension = optional(values, "--extension");
            Optional<Path> overlay = optional(values, "--overlay");
            Optional<String> prefix = Optional.ofNullable(values.remove("--overlay-prefix"));
            if (!values.isEmpty()) {
                throw new IllegalArgumentException("not an option of the generator: " + values.keySet());
            }
            return new Options(typed, typedPackage, schema, formatSchema, edition, derivable,
                    profiles, enums, enumFacts, rules, extension, overlay, prefix);
        }

        private static Path required(Map<String, String> values, String option) {
            String value = values.remove(option);
            if (value == null) {
                throw new IllegalArgumentException("the generator needs the option " + option);
            }
            return Path.of(value);
        }

        private static Optional<Path> optional(Map<String, String> values, String option) {
            return Optional.ofNullable(values.remove(option)).map(Path::of);
        }
    }
}
