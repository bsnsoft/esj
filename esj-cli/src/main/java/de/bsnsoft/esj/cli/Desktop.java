package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.render.RenderLanguage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * What {@code esj view} asks of the machine it runs on: the language of the person who
 * runs it, where a page goes when nobody said, whether there is a screen, and the program
 * that opens a page there.
 *
 * <p>Everything here is a property of the desktop and nothing of the invoice, and all of it
 * goes through one {@link Machine}: the name of the operating system, its temporary
 * directory, the language of the user, and the {@link Launcher} that starts a program. The
 * tool that a user runs passes the machine it runs on; a test passes one of its own, so that
 * a page is never written into the temporary directory of the build and no browser is ever
 * started by one.
 */
final class Desktop {

    /** The directory every page of a Linux desktop goes into, below the downloads. */
    static final String VIEW_DIRECTORY = "esj-view";

    /** How long a program that opens a page is waited for before it is left running. */
    private static final long OPEN_SECONDS = 10;

    /** How long {@code xdg-user-dir} may take before the temporary directory is used. */
    private static final long ASK_SECONDS = 5;

    /** The operating systems the page is opened on in a way of their own. */
    enum Platform {

        /** macOS: {@code open}, and the temporary directory. */
        MACOS,

        /** Linux: {@code xdg-open} where there is a display, and the downloads. */
        LINUX,

        /** Windows: the URL handler of the shell, and the temporary directory. */
        WINDOWS,

        /** Anything else: nothing is opened. */
        OTHER;

        /** Returns the platform an {@code os.name} names. */
        static Platform of(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.startsWith("mac") || lower.startsWith("darwin")) {
                return MACOS;
            }
            if (lower.startsWith("linux")) {
                return LINUX;
            }
            return lower.startsWith("windows") ? WINDOWS : OTHER;
        }
    }

    /**
     * Starts the programs a page is opened with and the downloads directory is asked of.
     */
    interface Launcher {

        /**
         * Starts a program that opens something and is not waited for beyond a few seconds:
         * its output goes nowhere, and a program still running then is left to run.
         *
         * @param command the program and its arguments
         * @return whether it was started and did not fail
         */
        boolean open(List<String> command);

        /**
         * Runs a program and returns what it printed, where it ended with code 0.
         *
         * @param command the program and its arguments
         * @return its standard output, or empty where it could not be run or failed
         */
        Optional<String> ask(List<String> command);

        /** The programs of this machine. */
        Launcher SYSTEM = new Launcher() {

            @Override
            public boolean open(List<String> command) {
                try {
                    Process process = new ProcessBuilder(command)
                            .redirectInput(Redirect.from(nullFile()))
                            .redirectOutput(Redirect.DISCARD)
                            .redirectError(Redirect.DISCARD)
                            .start();
                    // A launcher that hands the page to a browser already running ends at
                    // once; one that starts the browser may stay with it. Either has
                    // opened the page, so the one answer waited for is a failure.
                    return !process.waitFor(OPEN_SECONDS, TimeUnit.SECONDS)
                            || process.exitValue() == 0;
                } catch (IOException e) {
                    return false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            @Override
            public Optional<String> ask(List<String> command) {
                try {
                    Process process = new ProcessBuilder(command)
                            .redirectInput(Redirect.from(nullFile()))
                            .redirectError(Redirect.DISCARD)
                            .start();
                    byte[] output;
                    try (InputStream in = process.getInputStream()) {
                        output = in.readNBytes(4096);
                    }
                    if (!process.waitFor(ASK_SECONDS, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        return Optional.empty();
                    }
                    return process.exitValue() == 0
                            ? Optional.of(new String(output, Charset.defaultCharset()))
                            : Optional.empty();
                } catch (IOException e) {
                    return Optional.empty();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }
            }

            private java.io.File nullFile() {
                return new java.io.File(Platform.of(System.getProperty("os.name", ""))
                        == Platform.WINDOWS ? "NUL" : "/dev/null");
            }
        };
    }

    /**
     * The machine a page is written on and opened from.
     *
     * @param os           the operating system, as {@code os.name} names it
     * @param temporary    the temporary directory
     * @param userLanguage the language of the user's locale, as {@code user.language} names
     *                     it, which is what Windows says where no variable says it
     * @param launcher     what starts a program
     */
    record Machine(String os, Path temporary, String userLanguage, Launcher launcher) {

        /** Checks the parts. */
        Machine {
            Objects.requireNonNull(os, "os");
            Objects.requireNonNull(temporary, "temporary");
            Objects.requireNonNull(userLanguage, "userLanguage");
            Objects.requireNonNull(launcher, "launcher");
        }

        /**
         * Returns the machine this process runs on.
         *
         * @return the machine
         */
        static Machine running() {
            return new Machine(System.getProperty("os.name", ""),
                    Path.of(System.getProperty("java.io.tmpdir", ".")),
                    System.getProperty("user.language", ""), Launcher.SYSTEM);
        }

        Platform platform() {
            return Platform.of(os);
        }
    }

    private Desktop() {
        throw new AssertionError("no instances");
    }

    /**
     * Returns the language of the person who runs the tool, where {@code --lang} names none:
     * the first of {@code LC_ALL}, {@code LC_MESSAGES} and {@code LANG} that is set — on
     * Windows, where none of them is, the language of the user's locale — and German where
     * that is German, English otherwise.
     *
     * @param options the options of this run, with its environment and its machine
     * @return the language of the page
     */
    static RenderLanguage language(GlobalOptions options) {
        for (String variable : List.of("LC_ALL", "LC_MESSAGES", "LANG")) {
            Optional<String> value = options.environment(variable).filter(v -> !v.isEmpty());
            if (value.isPresent()) {
                return german(value.orElseThrow()) ? RenderLanguage.GERMAN
                        : RenderLanguage.ENGLISH;
            }
        }
        Machine machine = options.machine();
        if (machine.platform() == Platform.WINDOWS && german(machine.userLanguage())) {
            return RenderLanguage.GERMAN;
        }
        return RenderLanguage.ENGLISH;
    }

    /** Tells whether a locale name, {@code de_DE.UTF-8} or {@code de}, is German. */
    static boolean german(String locale) {
        String lower = locale.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("de")) {
            return false;
        }
        return lower.length() == 2 || "_-.@".indexOf(lower.charAt(2)) >= 0;
    }

    /**
     * Returns the directory a page without {@code --out} goes into, created and new: on
     * Linux below {@code esj-view} in the downloads directory, where a browser in a snap or
     * a Flatpak can read it — the temporary directory of such a browser is its own, and a
     * hidden directory of the home it does not read — and in the temporary directory
     * elsewhere, or where Linux names no downloads directory.
     *
     * @param options the options of this run, with its machine
     * @param stem    the name of the input, made safe for a file name
     * @return the directory
     * @throws IOException if it cannot be created
     */
    static Path directory(GlobalOptions options, String stem) throws IOException {
        Machine machine = options.machine();
        Path parent = machine.temporary();
        if (machine.platform() == Platform.LINUX) {
            Optional<Path> downloads = downloads(machine);
            if (downloads.isPresent()) {
                parent = Files.createDirectories(downloads.orElseThrow().resolve(VIEW_DIRECTORY));
            }
        }
        return Files.createTempDirectory(parent, "esj-view-" + stem + "-");
    }

    /** Returns the downloads directory {@code xdg-user-dir} names, where it names one. */
    private static Optional<Path> downloads(Machine machine) {
        Optional<String> answer = machine.launcher().ask(List.of("xdg-user-dir", "DOWNLOAD"));
        if (answer.isEmpty()) {
            return Optional.empty();
        }
        String line = answer.orElseThrow().strip();
        try {
            Path path = Path.of(line);
            return !line.isEmpty() && path.isAbsolute() && Files.isDirectory(path)
                    ? Optional.of(path) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /**
     * Tells whether there is a screen to open a page on: on Linux one a display variable
     * names, on macOS and Windows the desktop, and nowhere else.
     *
     * @param options the options of this run, with its environment and its machine
     * @return whether a page is opened
     */
    static boolean display(GlobalOptions options) {
        return switch (options.machine().platform()) {
            case LINUX -> options.environment("DISPLAY").filter(v -> !v.isEmpty()).isPresent()
                    || options.environment("WAYLAND_DISPLAY").filter(v -> !v.isEmpty())
                            .isPresent();
            case MACOS, WINDOWS -> true;
            case OTHER -> false;
        };
    }

    /**
     * Opens a page in the browser of the desktop.
     *
     * @param options the options of this run, with its machine
     * @param page    the page
     * @return whether a program opened it
     */
    static boolean open(GlobalOptions options, Path page) {
        Machine machine = options.machine();
        String file = page.toString();
        List<String> command = switch (machine.platform()) {
            case MACOS -> List.of("open", file);
            case LINUX -> List.of("xdg-open", file);
            case WINDOWS -> List.of("rundll32", "url.dll,FileProtocolHandler", file);
            case OTHER -> List.of();
        };
        return !command.isEmpty() && machine.launcher().open(command);
    }
}
