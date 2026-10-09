package de.bsnsoft.esj.cli.serve;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The body of one request, and what is left of it once the answer has been sent: read and
 * thrown away before the connection is closed, within a bound of bytes and of time — a
 * lingering close.
 *
 * <p>A request refused before its body was read — no token, a foreign origin, no room on the
 * disk, a message that is not JSON — still has bytes on their way when its answer goes out. A
 * server that closes the connection with bytes of the request unread makes its TCP stack send
 * a reset — Linux at once, dropping what of the answer it has not sent yet — and a reset may
 * erase what the client has received and not yet read: the client sees a broken connection
 * where it should see 401 or 503 (RFC 9112, section 9.6, "Tear-down"). The JDK's server reads
 * 64 KiB of such a body before it closes ({@code sun.net.httpserver.drainAmount}), which is
 * less than a document.
 *
 * <p>So the answer goes out first and whole — a client that reads while it sends has it at
 * once and may stop sending — and before the answer's stream is closed, and with it the
 * connection, what the client still sends is read into a buffer of {@link #BUFFER} bytes and
 * dropped: nothing of it is kept, written or counted against {@code --max-disk}. It is read
 * to the end the request announced, a body without a length up to the bound of its door, and
 * for at most {@link #TIME}, or the request timeout where that is shorter. A body announced
 * past the bound is not read at all; a client that announces gigabytes gets its answer and a
 * closed connection, as before. When the time is up the connection is closed, which is the
 * only way to end a read the JDK's server blocks in, and a client too slow for it may lose
 * the answer, as before.
 *
 * <p>The request's stream is replaced by one that counts what the handler reads and that
 * closing does not close: the JDK's server closes the original when the answer ends, and a
 * handler that closes its stream on a refusal — every {@code try} with resources does — would
 * otherwise have the JDK read its 64 KiB and end the body before it could be lingered over.
 */
final class Linger {

    /** The longest a refused request's body is read after its answer. */
    static final Duration TIME = Duration.ofSeconds(5);

    /** The bytes of the buffer the rest of a body is read into and dropped from. */
    static final int BUFFER = 8 * 1024;

    private final long declared;
    private final long bound;
    private final Duration time;
    private final ScheduledExecutorService alarms;
    private final Body body;
    private long discarded;
    private boolean lingered;

    private Linger(HttpExchange exchange, long bound, Duration time,
                   ScheduledExecutorService alarms) {
        this.declared = declared(exchange.getRequestHeaders());
        this.bound = bound;
        this.time = time;
        this.alarms = alarms;
        this.body = new Body(exchange.getRequestBody());
    }

    /**
     * Gives an exchange the streams of a lingering close.
     *
     * @param exchange the exchange, before anything of it was read or sent
     * @param bound    the largest body its door takes
     * @param time     how long the rest of a body is read at most
     * @param alarms   where the end of that time is kept
     * @return what the exchange reads and lingers over
     */
    static Linger install(HttpExchange exchange, long bound, Duration time,
                          ScheduledExecutorService alarms) {
        Linger linger = new Linger(exchange, bound, time, alarms);
        exchange.setStreams(linger.body, linger.new Answer(exchange.getResponseBody()));
        return linger;
    }

    /**
     * Returns how many bytes of the request body arrived: those the handler read and those
     * read and dropped after the answer.
     *
     * @return the bytes
     */
    long received() {
        return body.read + discarded;
    }

    /**
     * Reads the rest of the body now, for an answer without a body: the JDK's server ends the
     * exchange as it sends the headers of such an answer, so there is no later.
     */
    void beforeEmptyAnswer() {
        rest();
    }

    /**
     * Returns the length the request announced: its {@code Content-Length}, or -1 where the
     * body is chunked, as the JDK's server reads them.
     */
    private static long declared(Headers headers) {
        if (headers.getFirst("Transfer-Encoding") != null) {
            return -1;
        }
        String length = headers.getFirst("Content-Length");
        if (length == null) {
            return 0;
        }
        try {
            return Long.parseLong(length.strip());
        } catch (NumberFormatException e) {
            // The JDK's server refuses such a request before a handler sees it.
            return Long.MAX_VALUE;
        }
    }

    /** Reads what is left of the body and drops it, within the bounds; once. */
    private void rest() {
        if (lingered || body.ended) {
            return;
        }
        lingered = true;
        boolean chunked = declared < 0;
        if (!chunked && (declared > bound || declared - body.read <= 0)) {
            // Announced past the bound: not read. Or read to its announced end, which the
            // JDK's server sees for itself without waiting.
            return;
        }
        if (chunked && body.read > bound) {
            return;
        }
        Alarm alarm = new Alarm(Thread.currentThread());
        ScheduledFuture<?> ringing;
        try {
            ringing = alarms.schedule(alarm, Math.max(1, time.toMillis()), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // The server is stopping and closes every connection.
            return;
        }
        byte[] buffer = new byte[BUFFER];
        try {
            int read;
            while ((read = body.original().read(buffer, 0, buffer.length)) >= 0) {
                discarded += read;
                if (body.read + discarded > bound) {
                    // A chunked body past the bound of its door.
                    return;
                }
            }
            body.ended = true;
        } catch (IOException e) {
            // The client closed its side, or the time was up and the connection was closed.
        } finally {
            ringing.cancel(false);
            alarm.disarm();
        }
    }

    /**
     * Ends the reading of the rest of a body when its time is up: interrupting the thread
     * that reads closes the connection, the one way to end a blocking read of the JDK's
     * server. Once disarmed it rings no more, and an interrupt it caused is cleared.
     */
    private static final class Alarm implements Runnable {
        private final Thread reader;
        private boolean armed = true;
        private boolean rang;

        Alarm(Thread reader) {
            this.reader = reader;
        }

        @Override
        public synchronized void run() {
            if (armed) {
                rang = true;
                reader.interrupt();
            }
        }

        /** Called by the reading thread itself when it is done. */
        synchronized void disarm() {
            armed = false;
            if (rang) {
                // The pool's thread goes on to other requests.
                Thread.interrupted();
            }
        }
    }

    /** The request body as the handler reads it: counted, and not closed by closing it. */
    private static final class Body extends FilterInputStream {
        private long read;
        private boolean ended;

        Body(InputStream original) {
            super(original);
        }

        InputStream original() {
            return in;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b < 0) {
                ended = true;
            } else {
                read++;
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = in.read(buffer, offset, length);
            if (count < 0) {
                ended = true;
            } else {
                read += count;
            }
            return count;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = in.skip(n);
            read += Math.max(0, skipped);
            return skipped;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public void close() {
            // The JDK's server closes the original when the answer ends.
        }
    }

    /** The answer's stream: closing it reads the rest of the body first, then closes. */
    private final class Answer extends FilterOutputStream {
        private boolean closed;

        Answer(OutputStream original) {
            super(original);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                out.flush();
            } finally {
                rest();
                out.close();
            }
        }
    }
}
