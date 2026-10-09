package de.bsnsoft.esj.cli.serve;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Removes a directory this process made, with everything in it.
 *
 * <p>It is only ever called on a directory {@link Store} or {@link Jobs} created in this
 * run. A symbolic link inside it is removed as a link and never followed, so nothing outside
 * the directory can be reached through it.
 */
final class Trees {

    private Trees() {
    }

    /**
     * Removes a directory and its content; a directory that is already gone is fine.
     *
     * @param directory the directory
     */
    static void delete(Path directory) {
        if (directory == null || !Files.exists(directory,
                java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                        throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException failure)
                        throws IOException {
                    if (failure instanceof NoSuchFileException) {
                        return FileVisitResult.CONTINUE;
                    }
                    throw failure;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException failure)
                        throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // A file that could not be removed is left to the operating system's temporary
            // directory; nothing here depends on it being gone.
        }
    }
}
