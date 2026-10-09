package de.bsnsoft.esj.cli.serve;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The uploads and the artefacts of one server process, and nothing else.
 *
 * <p>Everything lies in one fresh temporary directory, made when the process starts and
 * removed when it ends. An entry is kept for {@code --ttl} and then removed; the number of
 * entries and their bytes together are held to {@code --max-stored} and
 * {@code --max-stored-bytes}, and an entry that would pass either is refused rather than
 * evicting one somebody may be about to read. An identifier is 128 random bits, so knowing
 * one is what entitles a caller to the entry — as the bearer token of the server does to
 * the server.
 *
 * <p>Everything in the directory, the entries and the files of the calls, is counted against
 * {@code --max-disk} ({@link Disk}); the entries are a part of it, held to their own bounds.
 */
final class Store implements AutoCloseable {

    /** What an identifier looks like: 32 lower-case hexadecimal digits. */
    static final Pattern ID = Pattern.compile("[0-9a-f]{32}");

    /** What an entry is. */
    enum Kind {
        /** A document a caller uploaded. */
        DOCUMENT,
        /** A file a child wrote: a rendering, a report, a converted document. */
        ARTIFACT
    }

    /**
     * One entry.
     *
     * @param id        its identifier
     * @param kind      what it is
     * @param name      the name it is downloaded under
     * @param mediaType its media type
     * @param bytes     its length
     * @param sha256    the SHA-256 of its bytes, in hexadecimal
     * @param expires   when it is removed
     * @param file      where it lies
     */
    record Entry(String id, Kind kind, String name, String mediaType, long bytes, String sha256,
                 Instant expires, Path file) {
    }

    /** The store has no room for another entry. */
    static final class Full extends Exception {
        private static final long serialVersionUID = 1L;

        Full(String message) {
            super(message);
        }
    }

    /** An upload was larger than the server takes. */
    static final class TooLarge extends Exception {
        private static final long serialVersionUID = 1L;

        TooLarge(String message) {
            super(message);
        }
    }

    private final ServeConfig config;
    private final Path root;
    private final Path entries;
    private final Disk disk;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> index = new LinkedHashMap<>();
    private long stored;

    /**
     * Makes the temporary directory of this process.
     *
     * @param config the settings
     * @param clock  the clock the time to live is measured with
     */
    Store(ServeConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
        try {
            Path made;
            try {
                made = Files.createTempDirectory(config.tempRoot(), "esj-serve-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
            } catch (UnsupportedOperationException e) {
                made = Files.createTempDirectory(config.tempRoot(), "esj-serve-");
            }
            this.root = made;
            this.entries = Files.createDirectory(root.resolve("store"));
            this.disk = new Disk(config.maxDisk(), Files.createDirectory(root.resolve("jobs")));
        } catch (IOException e) {
            throw new UncheckedIOException("the temporary directory could not be made", e);
        }
    }

    /** Returns the directory of this process. */
    Path root() {
        return root;
    }

    /** Returns the directory the working directories of the children are made in. */
    Path jobs() {
        return disk.directory();
    }

    /** Returns the account of what this process holds on disk. */
    Disk disk() {
        return disk;
    }

    /** Returns a new identifier. */
    String newId() {
        byte[] bits = new byte[16];
        random.nextBytes(bits);
        return HexFormat.of().formatHex(bits);
    }

    /**
     * Reads an upload into the store, refusing it as soon as it passes the bound.
     *
     * @param in        the bytes
     * @param name      the name it was given, if any
     * @return the entry
     * @throws TooLarge where the upload passes {@code --max-upload}
     * @throws Full     where the store has no room for it
     * @throws Disk.Full where it does not fit beside what the server holds on disk
     * @throws IOException where reading or writing fails
     */
    Entry upload(InputStream in, String name) throws TooLarge, Full, IOException {
        String id = newId();
        Path file = entries.resolve(id);
        MessageDigest digest = sha256();
        long count = 0;
        byte[] buffer = new byte[64 * 1024];
        try (OutputStream out = disk.write(file)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                count += read;
                if (count > config.maxUpload()) {
                    throw new TooLarge("the document is larger than the " + config.maxUpload()
                            + " bytes this server takes (--max-upload)");
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        } catch (TooLarge | IOException e) {
            disk.delete(file);
            throw e;
        }
        try {
            return add(new Entry(id, Kind.DOCUMENT, name == null ? "document" : name,
                    "application/octet-stream", count, HexFormat.of().formatHex(digest.digest()),
                    clock.instant().plus(config.ttl()), file));
        } catch (Full e) {
            disk.delete(file);
            throw e;
        }
    }

    /**
     * Moves a file a child wrote into the store.
     *
     * @param source    the file, which is moved
     * @param name      the name it is downloaded under
     * @param mediaType its media type
     * @return the entry
     * @throws Full where the store has no room for it
     * @throws IOException where it cannot be read or moved
     */
    Entry artifact(Path source, String name, String mediaType) throws Full, IOException {
        String id = newId();
        Path file = entries.resolve(id);
        String sha = sha256(source);
        long bytes = Files.size(source);
        disk.move(source, file);
        try {
            return add(new Entry(id, Kind.ARTIFACT, name, mediaType, bytes, sha,
                    clock.instant().plus(config.ttl()), file));
        } catch (Full e) {
            disk.delete(file);
            throw e;
        }
    }

    private synchronized Entry add(Entry entry) throws Full {
        sweep();
        if (index.size() >= config.maxStored()) {
            throw new Full("the server keeps " + config.maxStored() + " uploads and artefacts"
                    + " at once (--max-stored), and that many are kept now");
        }
        if (stored + entry.bytes() > config.maxStoredBytes()) {
            throw new Full("the server keeps " + config.maxStoredBytes() + " bytes of uploads"
                    + " and artefacts at once (--max-stored-bytes), and this one does not fit");
        }
        index.put(entry.id(), entry);
        stored += entry.bytes();
        return entry;
    }

    /**
     * Returns an entry that has not expired.
     *
     * @param id   its identifier
     * @param kind what it must be
     * @return the entry, or nothing
     */
    synchronized Optional<Entry> get(String id, Kind kind) {
        return get(id).filter(entry -> entry.kind() == kind);
    }

    /**
     * Returns an entry of either kind that has not expired.
     *
     * @param id its identifier
     * @return the entry, or nothing
     */
    synchronized Optional<Entry> get(String id) {
        sweep();
        if (id == null || !ID.matcher(id).matches()) {
            return Optional.empty();
        }
        return Optional.ofNullable(index.get(id));
    }

    /** Removes every entry whose time has run out. */
    synchronized void sweep() {
        Instant now = clock.instant();
        index.values().removeIf(entry -> {
            if (now.isBefore(entry.expires())) {
                return false;
            }
            stored -= entry.bytes();
            disk.delete(entry.file());
            return true;
        });
    }

    /** Returns how many entries are kept now. */
    synchronized int size() {
        return index.size();
    }

    /** Removes the directory of this process with everything in it. */
    @Override
    public synchronized void close() {
        index.clear();
        stored = 0;
        Trees.delete(root);
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
