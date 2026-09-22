package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.Esj;
import de.bsnsoft.esj.model.Registry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/**
 * What {@code esj --version} prints: the version of the tool, the version of the format it
 * implements, the edition of the semantic model this tool writes by default, and the
 * editions it holds a registry for.
 *
 * <p>The first three move independently and conflating them is the mistake the
 * specification warns about: the artefact version is the build, the format version is the
 * version of the specification, and the semantic model edition is the edition of
 * EN 16931-1.
 *
 * <p>The fourth is a property of the build rather than a version. A registry is data and a
 * distribution may leave one out, so which editions a copy of this tool can read, check,
 * render and upgrade is a question only that copy can answer; every other line of every
 * report follows from it.
 */
final class VersionProvider implements picocli.CommandLine.IVersionProvider {

    /** Written by the build, so that the number is never typed twice. */
    private static final String RESOURCE = "version.properties";

    @Override
    public String[] getVersion() {
        return new String[] {
            "esj " + artifactVersion(),
            "ESJ format version " + Esj.VERSION,
            "semantic model " + Esj.SEMANTIC_MODEL,
            "semantic model registries " + String.join(", ", Registry.editions())};
    }

    /**
     * Returns what a report names as the tool that produced it.
     *
     * <p>The name and the version and nothing else: a report of this tool is compared,
     * diffed and checked in, so it carries no build number, no host and no moment.
     *
     * @return {@code "esj <version>"}
     */
    static String tool() {
        return "esj " + artifactVersion();
    }

    private static String artifactVersion() {
        Properties properties = new Properties();
        try (InputStream in = VersionProvider.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return "unknown";
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties.getProperty("version", "unknown");
    }
}
