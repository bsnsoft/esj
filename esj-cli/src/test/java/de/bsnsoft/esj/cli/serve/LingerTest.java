package de.bsnsoft.esj.cli.serve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.ClosedByInterruptException;
import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The lingering close on its own, with an exchange whose streams the test holds: what is read
 * of a body after its answer, how much, for how long, and in which order with the closing of
 * the answer.
 */
class LingerTest {

    private static final long BOUND = 64 * 1024;

    private final ScheduledThreadPoolExecutor alarms = new ScheduledThreadPoolExecutor(1);

    @AfterEach
    void stop() {
        alarms.shutdownNow();
    }

    @Test
    void theRestOfABodyIsReadAndDroppedBeforeTheAnswerIsClosed() throws IOException {
        Original body = new Original(new ByteArrayInputStream(new byte[50_000]));
        Exchange exchange = new Exchange(body, "Content-Length", "50000");
        Linger linger = Linger.install(exchange, BOUND, Duration.ofSeconds(5), alarms);

        InputStream in = exchange.getRequestBody();
        assertEquals(10, in.readNBytes(10).length);
        in.close();
        assertFalse(body.closed, "closing the request's stream leaves the body to the server");
        OutputStream out = exchange.getResponseBody();
        out.write("{}".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(49_990, body.available(), "nothing is read before the answer is closed");
        out.close();
        assertEquals(0, exchange.answer.leftWhenClosed, "the body was read to its end before"
                + " the answer, and with it the connection, was closed");
        assertEquals("{}", exchange.answer.toString());
        assertEquals(50_000, linger.received());
        out.close();
        assertEquals(1, exchange.answer.closings, "closed once");
    }

    @Test
    void aBodyAnnouncedPastTheBoundIsNotRead() throws IOException {
        Original body = new Original(new ByteArrayInputStream(new byte[(int) BOUND + 1]));
        Exchange exchange = new Exchange(body, "Content-Length", Long.toString(BOUND + 1));
        Linger linger = Linger.install(exchange, BOUND, Duration.ofSeconds(5), alarms);
        exchange.getResponseBody().close();
        assertEquals(BOUND + 1, exchange.answer.leftWhenClosed);
        assertEquals(0, linger.received());
        assertEquals(0, alarms.getQueue().size() + alarms.getCompletedTaskCount(),
                "no alarm was set");
    }

    @Test
    void aBodyWithoutALengthIsReadNoFurtherThanTheBound() throws IOException {
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return 'x';
            }

            @Override
            public int read(byte[] bytes, int offset, int length) {
                java.util.Arrays.fill(bytes, offset, offset + length, (byte) 'x');
                return length;
            }
        };
        Exchange exchange = new Exchange(new Original(endless), "Transfer-Encoding", "chunked");
        Linger linger = Linger.install(exchange, BOUND, Duration.ofSeconds(5), alarms);
        exchange.getResponseBody().close();
        assertTrue(linger.received() > BOUND && linger.received() <= BOUND + Linger.BUFFER,
                "read " + linger.received());
    }

    @Test
    void aBodyThatDoesNotComeIsGivenUpWhenTheTimeIsUp() throws IOException {
        // Like a socket channel: an interrupt ends the read and stays set.
        InputStream stalled = new InputStream() {
            @Override
            public int read() throws IOException {
                return read(new byte[1], 0, 1);
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                while (!Thread.currentThread().isInterrupted()) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
                }
                throw new ClosedByInterruptException();
            }
        };
        Exchange exchange = new Exchange(new Original(stalled), "Content-Length", "1000");
        Linger linger = Linger.install(exchange, BOUND, Duration.ofMillis(200), alarms);
        long started = System.nanoTime();
        exchange.getResponseBody().close();
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertTrue(millis >= 150 && millis < 3_000, "gave up after " + millis + " ms");
        assertFalse(Thread.interrupted(), "the thread goes back to its pool uninterrupted");
        assertEquals(1, exchange.answer.closings);
        assertEquals(0, linger.received());
    }

    @Test
    void anAnswerWithoutABodyReadsTheRestFirst() throws IOException {
        Original body = new Original(new ByteArrayInputStream(new byte[3_000]));
        Exchange exchange = new Exchange(body, "Content-Length", "3000");
        Linger linger = Linger.install(exchange, BOUND, Duration.ofSeconds(5), alarms);
        linger.beforeEmptyAnswer();
        assertEquals(0, body.available());
        assertEquals(3_000, linger.received());
        assertFalse(Thread.interrupted());
    }

    /** The original request body, which only the server closes. */
    private static final class Original extends java.io.FilterInputStream {
        boolean closed;

        Original(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** The answer's original stream: what was written, and how much body was left at close. */
    private static final class Answer extends ByteArrayOutputStream {
        private final InputStream body;
        long leftWhenClosed = -1;
        int closings;

        Answer(InputStream body) {
            this.body = body;
        }

        @Override
        public void close() throws IOException {
            closings++;
            leftWhenClosed = body.available();
        }

        @Override
        public String toString() {
            return toString(java.nio.charset.StandardCharsets.US_ASCII);
        }
    }

    /** An exchange with the streams of the test, as the JDK's server gives them out. */
    private static final class Exchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        final Answer answer;
        private InputStream in;
        private OutputStream out;

        Exchange(InputStream body, String header, String value) {
            requestHeaders.add(header, value);
            this.in = body;
            this.answer = new Answer(body);
            this.out = answer;
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return URI.create("/api/validate");
        }

        @Override
        public String getRequestMethod() {
            return "POST";
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return in;
        }

        @Override
        public OutputStream getResponseBody() {
            return out;
        }

        @Override
        public void sendResponseHeaders(int code, long length) {
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 1);
        }

        @Override
        public int getResponseCode() {
            return -1;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 2);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            if (i != null) {
                in = i;
            }
            if (o != null) {
                out = o;
            }
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
