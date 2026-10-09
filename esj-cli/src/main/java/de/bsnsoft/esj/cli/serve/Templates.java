package de.bsnsoft.esj.cli.serve;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The render templates of {@code --templates}, by name.
 *
 * <p>A template is a JSON file that names other files — a letterhead, a logo — which the
 * renderer reads beside it and nowhere else. A caller therefore names a template and never
 * a path: the names are the {@code .json} files of the one directory the operator chose,
 * read once when the server starts, and they are the closed list the {@code template}
 * parameter takes.
 */
final class Templates {

    private Templates() {
    }

    /**
     * Returns the names of the templates of a directory, in order.
     *
     * @param directory the directory, where there is one
     * @return the names, each without its {@code .json}
     */
    static List<String> names(Optional<Path> directory) {
        if (directory.isEmpty()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory.get(), "*.json")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                String stem = name.substring(0, name.length() - ".json".length());
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && Tools.TEMPLATE_NAME.matcher(stem).matches()) {
                    names.add(stem);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the templates of " + directory.get()
                    + " cannot be read", e);
        }
        names.sort(null);
        return List.copyOf(names);
    }
}
