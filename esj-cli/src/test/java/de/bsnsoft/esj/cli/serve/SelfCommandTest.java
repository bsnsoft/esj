package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The command line of a child: this tool again, as the launcher of each artefact started it,
 * with the heap ceiling of a call and nothing of the environment it was not given.
 */
class SelfCommandTest {

    @Test
    void aChildOfTheRuntimeImageKeepsItsCacheAndItsJar() {
        List<String> launcher = List.of("-Xms32m", "-Xmx512m", "-XX:+ExitOnOutOfMemoryError",
                "-XX:+UseCompactObjectHeaders", "-XX:AOTCache=/opt/esj/app/esj.aot",
                "-Dsome.property=kept-here", "-jar", "/opt/esj/app/esj.jar", "serve",
                "--port", "8080");
        assertEquals(List.of("/opt/esj/bin/java", "-Xms32m", "-Xmx1g",
                        "-XX:+ExitOnOutOfMemoryError", "-XX:+UseCompactObjectHeaders",
                        "-XX:AOTCache=/opt/esj/app/esj.aot", "-jar", "/opt/esj/app/esj.jar"),
                SelfCommand.virtualMachine("/opt/esj/bin/java", launcher, "ignored", "1g"));
    }

    @Test
    void aChildOfAClassPathIsStartedByItsMainClassAndAbsolutePaths() {
        String classPath = String.join(java.io.File.pathSeparator, "classes", "/lib/a.jar");
        List<String> command = SelfCommand.virtualMachine("java", List.of("-cp", classPath,
                "de.bsnsoft.esj.cli.Main", "mcp"), classPath, "16m");
        assertEquals(List.of("java", "-Xms16m", "-Xmx16m", "-XX:+ExitOnOutOfMemoryError",
                "-cp", String.join(java.io.File.pathSeparator,
                        Path.of("classes").toAbsolutePath().toString(), "/lib/a.jar"),
                "de.bsnsoft.esj.cli.Main"), command, "a relative path names nothing in the"
                + " working directory of a child, and an initial heap above the ceiling would"
                + " not start");
    }

    @Test
    void aRelativeJarIsMadeAbsolute() {
        List<String> command = SelfCommand.virtualMachine("java", List.of("-jar",
                "app/esj.jar"), "app/esj.jar", "512m");
        assertEquals(Path.of("app/esj.jar").toAbsolutePath().toString(),
                command.get(command.size() - 1));
    }

    @Test
    void aChildIsGivenAChosenEnvironmentAndNothingElse() {
        Map<String, String> chosen = Jobs.environment(Map.of("PATH", "/bin", "LANG", "C.UTF-8",
                "ESJ_PACKS", "/packs", "ESJ_TOKEN_FILE", "/secret", "AWS_SECRET_ACCESS_KEY", "x",
                "JDK_JAVA_OPTIONS", "-Xmx8g", "ESJ_JAVA_OPTS", "-Xmx8g"));
        assertEquals(Map.of("PATH", "/bin", "LANG", "C.UTF-8", "ESJ_PACKS", "/packs"), chosen);
    }

    @Test
    void theExecutableIsNamedAbsolutelyAsTheShellWouldHaveFoundIt(
            @org.junit.jupiter.api.io.TempDir Path directory) throws Exception {
        Path bin = java.nio.file.Files.createDirectories(directory.resolve("bin"));
        Path esj = java.nio.file.Files.writeString(bin.resolve("esj"), "#!/bin/sh\n");
        assertEquals(true, esj.toFile().setExecutable(true));
        String path = String.join(java.io.File.pathSeparator, directory.resolve("nothing")
                .toString(), bin.toString());
        assertEquals(esj.toString(), SelfCommand.executable(java.util.Optional.of("esj"), path),
                "a bare name, from the PATH");
        assertEquals(esj.toString(), SelfCommand.executable(java.util.Optional.of(
                esj.toString()), path), "an absolute path, as it is");
        assertEquals(Path.of("dist/out/esj").toAbsolutePath().toString(), SelfCommand.executable(
                java.util.Optional.of("dist/out/esj".replace('/', java.io.File.separatorChar)),
                path), "a relative path, against the working directory");
    }

    @Test
    void aHeapSizeIsReadAsXmxReadsIt() {
        assertEquals(16L * 1024 * 1024, SelfCommand.bytes("16m"));
        assertEquals(1024L * 1024 * 1024, SelfCommand.bytes("1G"));
        assertEquals(4096, SelfCommand.bytes("4k"));
        assertEquals(1000, SelfCommand.bytes("1000"));
    }
}
