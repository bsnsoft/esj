package de.bsnsoft.esj.cli.serve;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * The bytes of one answer, written once and sent once: the first {@link #MEMORY} of them in
 * the heap, the rest in a file of the server's temporary directory.
 *
 * <p>An answer is built while its call holds a child's place ({@link Capacity#CALL}) and
 * written into a spool before the place is given back; what the answer weighs after that,
 * while a client reads it at whatever speed it reads, is a spool — at most {@link #MEMORY}
 * bytes of heap, whatever the answer's length, and nothing of the tree it was written from.
 * A client that reads slowly or not at all therefore holds a thread and a file, never the
 * heap of a call. The file is counted against {@code --max-disk} as it is written
 * ({@link Disk}): an answer that does not fit is not written, and the call is refused rather
 * than its answer cut.
 */
final class Spool extends OutputStream {

    /** The most bytes of an answer that stay in the heap. */
    static final int MEMORY = 16 * 1024;

    private final Disk disk;
    private byte[] head = new byte[1024];
    private int count;
    private Path file;
    private OutputStream spilled;
    private long size;
    private boolean closed;

    /**
     * Starts an empty spool.
     *
     * @param disk where its file is made and counted, if it needs one
     */
    Spool(Disk disk) {
        this.disk = disk;
    }

    /**
     * Writes a JSON value into a new spool.
     *
     * @param value     the value
     * @param pretty    with two spaces of indentation and a final line feed, as the REST API
     *                  writes its bodies; compact otherwise, as MCP writes its messages
     * @param disk      where the spool's file is made and counted, if it needs one
     * @return the spool, closed
     * @throws Disk.Full where the answer does not fit beside what the server holds; nothing
     *                   of it is kept
     */
    static Spool of(Jv value, boolean pretty, Disk disk) throws Disk.Full {
        Spool spool = new Spool(disk);
        try {
            value.writeTo(spool, pretty);
            spool.close();
        } catch (IOException | RuntimeException e) {
            spool.release();
            if (e instanceof Disk.Full full) {
                throw full;
            }
            throw e instanceof IOException io ? new UncheckedIOException(io)
                    : (RuntimeException) e;
        }
        return spool;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        if (closed) {
            throw new IOException("the spool is closed");
        }
        if (spilled == null && count + length <= MEMORY) {
            if (count + length > head.length) {
                head = Arrays.copyOf(head, Math.min(MEMORY, Math.max(count + length,
                        2 * head.length)));
            }
            System.arraycopy(bytes, offset, head, count, length);
            count += length;
        } else {
            if (spilled == null) {
                file = disk.newFile("answer-", ".json");
                spilled = new BufferedOutputStream(disk.write(file), 16 * 1024);
                spilled.write(head, 0, count);
                head = null;
                count = 0;
            }
            spilled.write(bytes, offset, length);
        }
        size += length;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            if (spilled != null) {
                spilled.close();
            }
        }
    }

    /** Returns the number of bytes written. */
    long size() {
        return size;
    }

    /**
     * Writes the bytes of this spool to a stream.
     *
     * @param out the stream
     * @throws IOException where either fails
     */
    void copyTo(OutputStream out) throws IOException {
        if (!closed) {
            throw new IllegalStateException("the spool is still being written");
        }
        if (file == null) {
            if (head != null) {
                out.write(head, 0, count);
            }
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            in.transferTo(out);
        }
    }

    /** Gives back what this spool holds: its bytes, and its file. */
    void release() {
        closed = true;
        head = null;
        count = 0;
        try {
            if (spilled != null) {
                spilled.close();
            }
        } catch (IOException e) {
            // The file is removed below, or with the temporary directory at the end.
        }
        if (file != null) {
            disk.delete(file);
            file = null;
        }
    }
}
