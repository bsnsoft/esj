package de.bsnsoft.esj.cli;

/**
 * A condition the tool reports to its caller: one line on the error stream and one exit
 * code.
 *
 * <p>It carries no stack trace worth showing, because the situations it stands for are
 * situations of the input and of the request rather than defects of the tool. Where it
 * wraps another exception, that exception is the cause and is printed only under
 * {@code --debug}.
 *
 * <p>Four of them are told apart by their exit code, and the third is the one a caller
 * must not confuse with the others: {@link #input} is an input the tool could not read,
 * {@link #unsupported} a request this version does not serve, and {@link #limit} a bound
 * of this run that was reached — a document the tool says nothing about, because the
 * limits are the reading party's policy and no part of conformance (specification,
 * sections 3.1 and 12.2). A message of the third kind names the bound and the switch that
 * raises it; {@link Bounds#refusal(String, String)} writes it. {@link #output} is the
 * fourth: the work was done and the result did not reach its destination.
 */
final class CliException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient int exitCode;

    private CliException(int exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    /** Returns a condition of the input, which the tool leaves with {@link ExitCode#INPUT}. */
    static CliException input(String message) {
        return new CliException(ExitCode.INPUT, message, null);
    }

    /** Returns a condition of the input that another exception reported. */
    static CliException input(String message, Throwable cause) {
        return new CliException(ExitCode.INPUT, message, cause);
    }

    /**
     * Returns a bound of this run that was reached, which the tool leaves with
     * {@link ExitCode#LIMIT}.
     *
     * <p>Nothing is thereby said about the document: a reader configured with more reads
     * the same bytes, and a caller that meets this asks for more rather than recording an
     * invalid invoice.
     */
    static CliException limit(String message) {
        return new CliException(ExitCode.LIMIT, message, null);
    }

    /** Returns a bound of this run that another exception reported. */
    static CliException limit(String message, Throwable cause) {
        return new CliException(ExitCode.LIMIT, message, cause);
    }

    /**
     * Returns a refusal to have written the output: {@link ExitCode#OUTPUT}.
     *
     * <p>It is for a destination the command opened itself, such as the file a rendering
     * was asked to be written to, and it is no statement about the input. The command may
     * have done its work; what failed is the delivery of the result, and a caller that
     * received nothing must not read a successful exit code. A failure on the standard
     * output is not raised at all but remembered by {@link Console} and reported once,
     * because a command that has already written half of its bytes cannot take them back.
     *
     * @param message what could not be written, and why
     * @param cause   the failure underneath
     * @return the exception to throw
     */
    static CliException output(String message, Throwable cause) {
        return new CliException(ExitCode.OUTPUT, message, cause);
    }

    /** Returns a request the tool understands and does not serve in this version. */
    static CliException unsupported(String message) {
        return new CliException(ExitCode.UNSUPPORTED, message, null);
    }

    /** Returns the exit code this condition leaves with. */
    int exitCode() {
        return exitCode;
    }
}
