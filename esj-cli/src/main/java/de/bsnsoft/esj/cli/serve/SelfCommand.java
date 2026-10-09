package de.bsnsoft.esj.cli.serve;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The command line a child process is started with: this same tool, with a heap ceiling.
 *
 * <p>The server never reads a document itself; every call starts the tool again, as its own
 * process, and that process is the one this process is. A native executable is started
 * again with {@code -Xmx}, which it takes at run time and which replaces the ceiling built
 * into it; the abort on a heap exhaustion is built in as well. A virtual machine is started
 * again with the Java executable that runs this one, the same jar, the ahead-of-time cache
 * and object header layout this one was started with — the cache refuses a virtual machine
 * that runs without the layout it was recorded with — and the three options of the process
 * boundary of {@code docs/deployment.md}. Every other option of this process, a system
 * property among them, stays with it.
 *
 * <p>Both answers come from {@link ProcessHandle.Info}, which reads what the operating system
 * recorded about this process, so they hold for the jar, the runtime image, the native
 * executable and the two container images alike, whichever launcher started them.
 */
final class SelfCommand {

    /** The class a virtual machine runs where it was not started with {@code -jar}. */
    private static final String MAIN = "de.bsnsoft.esj.cli.Main";

    /** The option of the ahead-of-time cache this process may have been started with. */
    private static final String AOT_CACHE = "-XX:AOTCache=";

    /** The option of a class data archive this process may have been started with. */
    private static final String SHARED_ARCHIVE = "-XX:SharedArchiveFile=";

    private SelfCommand() {
    }

    /**
     * Returns a path made absolute against the working directory of this process: a child
     * runs in a directory of its own, where a relative path would name nothing.
     */
    private static String absolute(String path) {
        return Path.of(path).toAbsolutePath().normalize().toString();
    }

    /**
     * Returns the command line of a child, before the command of the tool.
     *
     * @param heap the heap ceiling, as {@code -Xmx} takes it
     * @return the executable and its options
     */
    static List<String> of(String heap) {
        ProcessHandle.Info info = ProcessHandle.current().info();
        if (isNativeImage()) {
            String self = procSelf().orElseGet(() ->
                    executable(info.command(), System.getenv("PATH")));
            return List.of(self, "-Xmx" + heap);
        }
        String java = info.command()
                .filter(command -> Path.of(command).getFileName().toString().startsWith("java"))
                .map(command -> executable(Optional.of(command), System.getenv("PATH")))
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        List<String> arguments = info.arguments().map(List::of).orElse(List.of());
        return virtualMachine(java, arguments, System.getProperty("java.class.path"), heap);
    }

    /**
     * Builds the command line of a child of a virtual machine.
     *
     * @param java      the Java executable
     * @param arguments the arguments this process was started with, after the executable
     * @param classPath the class path of this process
     * @param heap      the heap ceiling of the child
     * @return the command line
     */
    static List<String> virtualMachine(String java, List<String> arguments, String classPath,
                                       String heap) {
        List<String> command = new ArrayList<>();
        command.add(java);
        // The initial heap of the process boundary, or the whole ceiling where that is less:
        // a virtual machine refuses to start with an initial heap above its maximum.
        command.add(bytes(heap) < 32L * 1024 * 1024 ? "-Xms" + heap : "-Xms32m");
        command.add("-Xmx" + heap);
        command.add("-XX:+ExitOnOutOfMemoryError");
        Optional<String> jar = Optional.empty();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if ("-jar".equals(argument) && i + 1 < arguments.size()) {
                jar = Optional.of(arguments.get(i + 1));
                break;
            }
            if (!argument.startsWith("-")) {
                break;
            }
            if (argument.startsWith(AOT_CACHE) || argument.startsWith(SHARED_ARCHIVE)) {
                int equals = argument.indexOf('=');
                command.add(argument.substring(0, equals + 1)
                        + absolute(argument.substring(equals + 1)));
            }
            if ("-XX:+UseCompactObjectHeaders".equals(argument)
                    || "-XX:-UseCompactObjectHeaders".equals(argument)) {
                command.add(argument);
            }
            if ("-cp".equals(argument) || "-classpath".equals(argument)
                    || "--class-path".equals(argument)) {
                i++;
            }
        }
        if (jar.isPresent()) {
            command.add("-jar");
            command.add(absolute(jar.get()));
        } else {
            List<String> entries = new ArrayList<>();
            for (String entry : classPath.split(File.pathSeparator)) {
                entries.add(entry.isEmpty() ? entry : absolute(entry));
            }
            command.add("-cp");
            command.add(String.join(File.pathSeparator, entries));
            command.add(MAIN);
        }
        return List.copyOf(command);
    }

    /** Returns the bytes of a heap size as {@code -Xmx} takes it. */
    static long bytes(String heap) {
        char unit = Character.toLowerCase(heap.charAt(heap.length() - 1));
        long scale = switch (unit) {
            case 'k' -> 1024L;
            case 'm' -> 1024L * 1024;
            case 'g' -> 1024L * 1024 * 1024;
            default -> 1L;
        };
        String digits = Character.isDigit(unit) ? heap : heap.substring(0, heap.length() - 1);
        return Long.parseLong(digits) * scale;
    }

    /** Returns the executable of this process as Linux records it, where it does. */
    private static Optional<String> procSelf() {
        Path self = Path.of("/proc/self/exe");
        if (!Files.isSymbolicLink(self)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readSymbolicLink(self).toString());
        } catch (java.io.IOException e) {
            return Optional.empty();
        }
    }

    /** Tells whether this process is a native executable. */
    static boolean isNativeImage() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    /**
     * Returns the absolute path of the executable of this process.
     *
     * <p>A child runs in a working directory of its own, so the executable has to be named
     * absolutely. The command the operating system recorded is taken as the shell would have
     * taken it: an absolute path as it is, a relative one against the working directory of
     * this process, and a bare name from the {@code PATH} — a native executable may be told
     * the name it was started by and not the file.
     *
     * @param command the command the operating system recorded, if any
     * @param path    the {@code PATH} of this process
     * @return the absolute path
     */
    static String executable(Optional<String> command, String path) {
        String name = command.orElseThrow(() -> new IllegalStateException("the executable of"
                + " this process cannot be found, so no child process can be started"));
        Path given = Path.of(name);
        if (given.isAbsolute()) {
            return given.toString();
        }
        if (name.contains(File.separator)) {
            return given.toAbsolutePath().normalize().toString();
        }
        for (String directory : (path == null ? "" : path).split(File.pathSeparator)) {
            if (!directory.isEmpty()) {
                Path candidate = Path.of(directory, name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toAbsolutePath().normalize().toString();
                }
            }
        }
        throw new IllegalStateException("the executable " + name + " of this process is on no"
                + " directory of the PATH, so no child process can be started");
    }
}
