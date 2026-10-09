package de.bsnsoft.esj.cli.serve;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the server holds on disk, and the bound on it, {@code --max-disk}.
 *
 * <p>Everything the server keeps lies in its temporary directory ({@link Store}), and every
 * byte of it is counted here before it is there. A file the server writes itself — a request
 * body, the content of an upload, the copy of a file named by path, an answer that waits for
 * its client, an upload kept for later — takes its bytes as they are written, and a write
 * that does not fit fails with {@link Full} before a byte of it is on the disk. A child,
 * whose files the server does not write, is given its share before it starts:
 * {@link Jobs#MAX_WRITTEN} bytes, the most it may write — its standard output, its error
 * stream and its files together. Once it has ended, what it wrote is counted file by file in
 * place of the share. A file that is moved keeps its count; one that is removed gives it
 * back, after it is gone.
 *
 * <p>So the directory holds no more than the bound, unless a child writes a file past its
 * share between two looks at its directory ({@link Jobs}); its standard output passes
 * through the server and is held to the share exactly. Where the bound is reached, a call is
 * refused rather than a file cut: a request body or an upload that does not fit, a child
 * whose share does not fit before it starts, an answer that does not fit its spool.
 */
final class Disk {

    /** The seconds after which a call refused for want of room may be tried again. */
    static final long RETRY_AFTER = 30;

    private static final long MIB = 1024L * 1024;

    /** The bound was reached: the bytes asked for do not fit. */
    static final class Full extends IOException {
        private static final long serialVersionUID = 1L;

        Full(String message) {
            super(message);
        }
    }

    private final long limit;
    private final Path directory;
    private final Map<Path, Long> files = new HashMap<>();
    private final Map<Path, Long> shares = new HashMap<>();
    private long used;

    /**
     * Starts an empty account.
     *
     * @param limit     the most bytes held at once
     * @param directory the directory the server makes its own files in
     */
    Disk(long limit, Path directory) {
        if (limit < 1) {
            throw new IllegalArgumentException("--max-disk must be at least one byte");
        }
        this.limit = limit;
        this.directory = directory;
    }

    /** Returns the most bytes held at once. */
    long limit() {
        return limit;
    }

    /** Returns the directory the server makes its own files in. */
    Path directory() {
        return directory;
    }

    /** Returns the bytes held now: those of the files, and the shares of running children. */
    synchronized long used() {
        return used;
    }

    /** Returns the bytes that are free now. */
    synchronized long free() {
        return limit - used;
    }

    /**
     * Makes a new, empty file in the directory of the server's own files, counted from now.
     *
     * @param prefix the beginning of its name
     * @param suffix the end of its name
     * @return the file
     * @throws IOException where it cannot be made
     */
    Path newFile(String prefix, String suffix) throws IOException {
        Path file = Files.createTempFile(directory, prefix, suffix);
        synchronized (this) {
            files.putIfAbsent(file, 0L);
        }
        return file;
    }

    /**
     * Opens a file for writing, every byte counted before it is written: a write that does
     * not fit fails with {@link Full}, and nothing of it reaches the file.
     *
     * @param file the file, made empty if it is not
     * @return the stream
     * @throws IOException where it cannot be opened
     */
    OutputStream write(Path file) throws IOException {
        OutputStream out = Files.newOutputStream(file);
        synchronized (this) {
            Long held = files.put(file, 0L);
            if (held != null) {
                used -= held;
            }
        }
        return new Counted(out, file);
    }

    /** A stream that takes the bytes of every write before it passes them on. */
    private final class Counted extends FilterOutputStream {
        private final Path file;

        Counted(OutputStream out, Path file) {
            super(out);
            this.file = file;
        }

        @Override
        public void write(int b) throws IOException {
            take(file, 1);
            out.write(b);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            take(file, length);
            out.write(bytes, offset, length);
        }
    }

    /**
     * Counts bytes of a file before they are written.
     *
     * @param file  the file
     * @param bytes how many
     * @throws Full where they do not fit
     */
    synchronized void take(Path file, long bytes) throws Full {
        if (bytes > limit - used) {
            throw full(bytes);
        }
        used += bytes;
        files.merge(file, bytes, Long::sum);
    }

    /**
     * Removes a file and gives back its bytes, once it is gone; a file that is not there
     * is fine.
     *
     * @param file the file
     */
    void delete(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // Still there, and still counted; it goes with the directory at the end.
            return;
        }
        synchronized (this) {
            Long held = files.remove(file);
            if (held != null) {
                used -= held;
            }
        }
    }

    /**
     * Moves a file within the temporary directory; its count moves with it.
     *
     * @param from the file
     * @param to   its new name
     * @throws IOException where it cannot be moved
     */
    synchronized void move(Path from, Path to) throws IOException {
        Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        Long held = files.remove(from);
        Long replaced = files.put(to, held == null ? 0L : held);
        if (replaced != null) {
            used -= replaced;
        }
    }

    /**
     * Gives the working directory of a child its share, before the child starts.
     *
     * @param directory the directory
     * @param bytes     the share
     * @throws Full where it does not fit
     */
    synchronized void reserve(Path directory, long bytes) throws Full {
        if (bytes > limit - used) {
            throw full(bytes);
        }
        used += bytes;
        shares.merge(directory, bytes, Long::sum);
    }

    /**
     * Counts what a child left in its working directory, file by file, in place of its share.
     *
     * @param directory the directory
     * @return the bytes of its files
     */
    long settle(Path directory) {
        Map<Path, Long> sizes = sizes(directory);
        long total = 0;
        synchronized (this) {
            Long share = shares.remove(directory);
            if (share != null) {
                used -= share;
            }
            for (Map.Entry<Path, Long> size : sizes.entrySet()) {
                Long held = files.put(size.getKey(), size.getValue());
                used += size.getValue() - (held == null ? 0 : held);
                total += size.getValue();
            }
        }
        return total;
    }

    /**
     * Removes a directory with everything in it, and gives back what it held: its files
     * and a share it still has.
     *
     * @param directory the directory
     */
    void deleteTree(Path directory) {
        Trees.delete(directory);
        synchronized (this) {
            Long share = shares.remove(directory);
            if (share != null) {
                used -= share;
            }
            files.entrySet().removeIf(entry -> {
                if (!entry.getKey().startsWith(directory) || Files.exists(entry.getKey())) {
                    return false;
                }
                used -= entry.getValue();
                return true;
            });
        }
    }

    /**
     * Returns the bytes of the regular files below a directory: what a child wrote.
     *
     * @param directory the directory
     * @return the bytes
     */
    static long size(Path directory) {
        return sizes(directory).values().stream().mapToLong(Long::longValue).sum();
    }

    private static Map<Path, Long> sizes(Path directory) {
        Map<Path, Long> sizes = new LinkedHashMap<>();
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile()) {
                        sizes.put(file, attributes.size());
                    }
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
            });
        } catch (NoSuchFileException e) {
            return sizes;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sizes;
    }

    private Full full(long bytes) {
        return new Full("the server holds at most " + mib(limit) + " in its temporary directory"
                + " (--max-disk), " + mib(used) + " of it now, and has no room for "
                + mib(bytes) + " more; try again later");
    }

    /**
     * Writes a count of bytes in mebibytes, to one decimal where it is not whole.
     *
     * @param bytes the bytes
     * @return the text
     */
    static String mib(long bytes) {
        if (bytes % MIB == 0) {
            return bytes / MIB + " MiB";
        }
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (double) MIB);
    }
}
