package de.bsnsoft.esj.cli.serve;

import java.util.List;
import java.util.Optional;

/**
 * What one call came to, before a front door delivers it.
 *
 * <p>{@link Status} is the transport's half — whether the call ran and gave an answer — and
 * the structured result is the answer. A validation that found an invoice invalid is a call
 * that succeeded: its status is {@link Status#OK} and its verdict is in the result. A run
 * that reached a bound of its own is not a verdict of any kind, and has a status of its own.
 *
 * @param status     what happened to the call
 * @param exitCode   the exit code of the child, or -1 where no child ran or it was killed
 * @param text       a few lines for a model or a person
 * @param structured the result as data
 * @param raw        the file of what the child wrote to its standard output, where the
 *                   REST API passes it on unchanged; the front door that asked for it removes
 *                   it once it is sent
 * @param retryAfter the seconds after which a refused call may be tried again
 */
record Outcome(Status status, int exitCode, String text, Jv.Obj structured,
               Optional<java.nio.file.Path> raw, Optional<Long> retryAfter) {

    /** What happened to a call. */
    enum Status {
        /** The call ran and gave its answer. */
        OK(200, null),
        /** The arguments do not fit the tool. */
        ARGUMENTS(400, "bad-arguments"),
        /** A path names a file outside the directories the operator allowed. */
        FORBIDDEN(403, "forbidden"),
        /** The upload, the artefact or the file is not there, or has expired. */
        NOT_FOUND(404, "not-found"),
        /** The document is larger than the server takes. */
        TOO_LARGE(413, "too-large"),
        /** The document could not be read, recognized or parsed (exit code 2). */
        INPUT(422, "input"),
        /** The document needs a feature this version does not implement (exit code 4). */
        UNSUPPORTED(422, "unsupported"),
        /**
         * The child reached a resource bound or its deadline, ran out of heap, or was stopped
         * at {@code --job-timeout} (exit codes 7 and 3): no verdict.
         */
        LIMIT(507, "limit"),
        /** Every child is busy and the queue is full. */
        BUSY(503, "busy"),
        /** The store has no room for another upload or artefact. */
        FULL(503, "full"),
        /** The child ended with a code outside the table: a crash. */
        CRASHED(502, "crashed"),
        /** An internal error of the tool or of the server (exit codes 5 and 6). */
        INTERNAL(500, "internal"),
        /**
         * The client cancelled the call ({@code notifications/cancelled} of MCP): its child
         * was ended, and the answer is nobody's. The REST API has no way to cancel a call.
         */
        CANCELLED(499, "cancelled");

        private final int http;
        private final String code;

        Status(int http, String code) {
            this.http = http;
            this.code = code;
        }

        /** Returns the HTTP status of the REST API. */
        int http() {
            return http;
        }

        /** Returns the error code of a body that is not an answer. */
        String code() {
            return code;
        }

        /** Returns the status of a child's exit code, where it is not an answer. */
        static Status ofExitCode(int exitCode) {
            return switch (exitCode) {
                case 2 -> INPUT;
                case 4 -> UNSUPPORTED;
                case 3, 7 -> LIMIT;
                case 5, 6 -> INTERNAL;
                default -> CRASHED;
            };
        }
    }

    /**
     * Returns an outcome that is not an answer.
     *
     * @param status   what happened
     * @param exitCode the exit code of the child, or -1
     * @param message  one sentence for the caller
     * @return the outcome
     */
    static Outcome error(Status status, int exitCode, String message) {
        Jv.Obj body = Jv.object()
                .put("error", status.code())
                .put("message", message)
                .put("exitCode", exitCode < 0 ? Jv.NULL : Jv.of(exitCode))
                .build();
        return new Outcome(status, exitCode, message, body, Optional.empty(), Optional.empty());
    }

    /** Returns this outcome with a time after which it may be tried again. */
    Outcome retryAfter(long seconds) {
        return new Outcome(status, exitCode, text, structured, raw, Optional.of(seconds));
    }

    /** Tells whether the call gave its answer. */
    boolean ok() {
        return status == Status.OK;
    }

    /** Returns this outcome with the file of the child's standard output. */
    Outcome withRaw(java.nio.file.Path file) {
        return new Outcome(status, exitCode, text, structured, Optional.of(file), retryAfter);
    }

    /** Returns this outcome with members added to its structured result. */
    Outcome with(String name, Jv value) {
        Jv.Builder builder = Jv.object();
        structured.members().forEach(builder::put);
        builder.put(name, value);
        return new Outcome(status, exitCode, text, builder.build(), raw, retryAfter);
    }

    /** Returns this outcome with lines appended to its text. */
    Outcome withText(List<String> lines) {
        if (lines.isEmpty()) {
            return this;
        }
        return new Outcome(status, exitCode, text + "\n" + String.join("\n", lines), structured,
                raw, retryAfter);
    }
}
