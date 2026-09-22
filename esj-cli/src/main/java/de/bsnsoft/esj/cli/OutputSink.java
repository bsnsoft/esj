package de.bsnsoft.esj.cli;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.Objects;

/**
 * The stream a document is written to, together with the one thing about it a refused
 * write has to be read against: whether there is a reader on the other end that can walk
 * away.
 *
 * <p>The two cases deserve opposite answers. {@code esj list x | head -1} has never meant
 * anything but "I have seen enough", and a script written against an exit code must not
 * have that code depend on whether the payload fitted in the pipe buffer before the
 * reader left. A full disk is the other way round: what the caller received is not what
 * the tool produced, and a successful exit code beside a truncated document would say
 * that it was.
 *
 * <p>The Java class library offers no way to tell them apart from the failure itself. A
 * broken pipe and a full disk both arrive as a plain {@link IOException} whose message is
 * the platform's, written in the platform's language, so a tool that reads that message
 * leaves with different codes on two hosts that differ only in their message catalogue.
 *
 * <p>So the stream is asked instead, once, before anything is written, and what it is
 * asked is whether it can be sought. A destination that can be sought is a file or a
 * device with a position: nothing is waiting at the end of it, and a write fails there
 * only because writing failed. A destination that cannot be sought is a pipe, a socket or
 * a terminal: there is a process at the end of it, that process leaving is the ordinary
 * reason a write to it stops working, and no other reason is plausible enough to be worth
 * an exit code. The question is put as a seek of no distance, {@link
 * FileChannel#position(long)} to the position the channel already has, which returns on a
 * file and raises on a pipe in every language.
 */
final class OutputSink extends OutputStream {

    private final OutputStream stream;
    private final boolean consumerCanLeave;

    /**
     * Wraps a stream and says how a refused write to it is to be read.
     *
     * @param stream           the stream that receives the bytes
     * @param consumerCanLeave {@code true} where a refused write means the reader is
     *                         gone, {@code false} where it means writing failed
     * @throws NullPointerException if {@code stream} is {@code null}
     */
    OutputSink(OutputStream stream, boolean consumerCanLeave) {
        this.stream = Objects.requireNonNull(stream, "stream");
        this.consumerCanLeave = consumerCanLeave;
    }

    /**
     * Returns the standard output of the process, buffered, having asked the descriptor
     * what it is attached to.
     *
     * <p>The descriptor is wrapped in a {@link FileOutputStream} rather than taken from
     * {@link System#out}, because a {@link java.io.PrintStream} swallows every
     * {@link IOException} and records only that one happened. Buffering is kept, because
     * a line at a time through a file descriptor is a system call per line.
     *
     * @param descriptor the descriptor to write to
     * @param buffer     the size of the buffer in front of it, in bytes
     * @return the standard output of the process
     */
    static OutputSink of(FileDescriptor descriptor, int buffer) {
        FileOutputStream file = new FileOutputStream(descriptor);
        return new OutputSink(new BufferedOutputStream(file, buffer), !seekable(file));
    }

    /**
     * Tells whether a refused write to this stream means the reader is gone rather than
     * that writing failed.
     *
     * @return {@code true} where the far end of this stream is a process that can leave
     */
    boolean consumerCanLeave() {
        return consumerCanLeave;
    }

    /** Tells whether a stream has a position, which a pipe, a socket and a terminal do not. */
    private static boolean seekable(FileOutputStream file) {
        try {
            FileChannel channel = file.getChannel();
            channel.position(channel.position());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void write(int b) throws IOException {
        stream.write(b);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        stream.write(bytes, offset, length);
    }

    @Override
    public void flush() throws IOException {
        stream.flush();
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
