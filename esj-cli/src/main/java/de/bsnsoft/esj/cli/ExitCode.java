package de.bsnsoft.esj.cli;

/**
 * The exit codes of the {@code esj} command line tool.
 *
 * <p>They are part of the interface of the tool: a script that branches on them is
 * entitled to the same meaning in every later version, so a code is never given a second
 * meaning and a new situation gets a new code. The same list is printed by
 * {@code esj --help}.
 *
 * <table>
 *   <caption>The exit codes and what they mean</caption>
 *   <tr><th>Code</th><th>Meaning</th></tr>
 *   <tr><td>0</td><td>the command did what it was asked to do</td></tr>
 *   <tr><td>1</td><td>a validation found an error, the profile of a container puts the
 *                     rules of EN 16931 out of scope, two documents differ, or
 *                     {@code esj get} found no value at the path it was asked for</td></tr>
 *   <tr><td>2</td><td>the input could not be read, recognized or parsed, or the command
 *                     line could not be parsed</td></tr>
 *   <tr><td>3</td><td>not a code of this tool: it is what the virtual machine leaves with
 *                     under {@code -XX:+ExitOnOutOfMemoryError}, so it is a crash and no
 *                     verdict on the document</td></tr>
 *   <tr><td>4</td><td>a feature that this version does not implement</td></tr>
 *   <tr><td>5</td><td>an internal error: a defect of the tool</td></tr>
 *   <tr><td>6</td><td>the output could not be written in full</td></tr>
 *   <tr><td>7</td><td>a resource or time limit of this run was reached; no verdict on the
 *                     document</td></tr>
 *   <tr><td>8</td><td>the conversion cannot be completed under the constraints asked
 *                     for: {@code esj convert --fail-on-loss} found part of the document
 *                     the target syntax has no place for, and nothing was written</td></tr>
 *   <tr><td>9</td><td>nothing fatal was found and a component of the complete check did
 *                     not run or did not complete: no verdict, and the report names which
 *                     and why</td></tr>
 * </table>
 *
 * <p>Code 3 is in the table although the tool never returns it, because the deployment
 * this tool is written for puts {@code -XX:+ExitOnOutOfMemoryError} on every command line
 * and that is the status the virtual machine aborts with. It writes its own
 * {@code Terminating due to java.lang.OutOfMemoryError} line to the standard output and
 * nothing to the error stream, so a caller that took 3 for an answer about the document
 * would be reading a crash as a verdict. That is why no code of this class is 3.
 *
 * <p>Codes 0, 1 and 9 are the three states a check ends in, and 7 is the run that reached
 * none of them. {@link #SUCCESS} is a claim about coverage as well as about findings: it is
 * given only where the complete check for that kind of input ran. A pipeline that
 * deliberately runs a reduced check — {@code --no-syntax}, {@code --level l2} — writes
 * itself against 9 and reads the {@code reasons} of the report to see what it gave up.
 *
 * <p>Codes 1 and 7 are the two the table keeps apart on purpose: a resource failure is not
 * a statement about the invoice. A document that was refused because this run was
 * configured to read less than it holds, or was stopped because it took longer than this
 * run allows, is not thereby invalid, and a caller that recorded it as invalid would have
 * booked a policy of its own reading as a defect of somebody else's invoice. Code 7 says
 * the tool reached no verdict; the answer to it is more resources, a larger profile or
 * another reader, never a rejection of the document.
 */
public final class ExitCode {

    /**
     * The command did what it was asked to do.
     *
     * <p>For a command that reaches a verdict this is the whole of the answer: the complete
     * check for that kind of input ran, and nothing fatal was found. What the complete
     * check is, is decided by the tool per input kind and written down in
     * {@code docs/validation.md}; a run that left part of it out leaves with
     * {@link #INDETERMINATE} instead, whatever it found.
     */
    public static final int SUCCESS = 0;

    /**
     * A validation found at least one error finding, the profile of a container puts the
     * rules of EN 16931 out of scope, {@code diff} found the two documents to differ, or
     * {@code esj get} found no value at the path it was asked for. The input was read and
     * understood; the answer is no, or — for the profile — that the question this tool
     * was asked is not the question to ask of that document.
     */
    public static final int VALIDATION = 1;

    /**
     * The input could not be read, recognized or parsed: an unreadable file, a byte
     * sequence that is neither a JSON object nor an XML document of a syntax this tool
     * reads, or a document that fails to parse. A document that was readable and was
     * refused on a bound of this run leaves with {@link #LIMIT} instead.
     *
     * <p>A command line the parser could not make sense of — an unknown option, a missing
     * argument, a subcommand that does not exist — leaves with this code as well. Nothing
     * was read and nothing was decided, which is what this code says; the tool prints the
     * complaint and the usage text on the error stream, as it does for an unreadable file.
     */
    public static final int INPUT = 2;

    /**
     * A feature this version does not implement, such as a ZUGFeRD 1.0 attachment: the
     * request is well formed and the tool cannot serve it.
     */
    public static final int UNSUPPORTED = 4;

    /**
     * An internal error. A defect of the tool rather than of the input; run the command
     * again with {@code --debug} to see where it happened.
     */
    public static final int INTERNAL = 5;

    /**
     * The output could not be written in full: a full disk, a stream that refused a write.
     *
     * <p>It has a code of its own because the bytes the caller received are not the bytes
     * the command produced, and no other code says that. It outranks the verdict of the
     * command: a validation that found nothing is of no use to a caller who did not
     * receive the whole report, so a run whose output failed leaves with this code even
     * where the command itself succeeded.
     *
     * <p>A consumer that closed the pipe is not one of these. Nothing failed, the reader
     * has what it asked for, and an exit code a script is written against must not depend
     * on whether the payload fitted in the pipe buffer before that reader left; see
     * {@link Console}.
     */
    public static final int OUTPUT = 6;

    /**
     * A resource or time limit of this run was reached, and the tool therefore says
     * nothing about the document.
     *
     * <p>The limits of the specification, section 12.2 are the reading party's policy and
     * take no part in conformance (section 3.1): the same byte sequence is a conformant
     * document for a reader configured differently, and both readers are right. The same
     * holds for the bound on an XML input and for {@code --max-runtime}. So a document
     * that outgrew one of them leaves with this code and not with {@link #INPUT}, which
     * would say the bytes were unreadable, and not with {@link #VALIDATION}, which would
     * say the invoice is invalid.
     *
     * <p>The separation from {@link #VALIDATION} matters most where the work is long
     * rather than large. The official Schematron of a profile can take minutes over a big
     * invoice, and a run stopped halfway through it has found no error — it has found
     * nothing at all. A pipeline that read that as code 1 would quarantine a sound
     * invoice, and one that read it as code 0 would pass an unchecked one.
     *
     * <p>Every refusal with this code names the bound it met and the switch that raises
     * it, so that a caller can decide between running the command again with more and
     * passing the document on to a party that reads more.
     *
     * <p>A heap or a stack that ran out reaches this code as well, where the tool is still
     * able to say so: the ceiling was chosen by whoever started the process, so it is a
     * resource of this run like any bound of the table, and {@link #INTERNAL} would claim
     * a defect the tool does not have. That recovery is unreliable by nature, which is why
     * {@code -XX:+ExitOnOutOfMemoryError} belongs on every command line and why a caller
     * must treat code 3 the same way; this code is what the run that was started without
     * the switch gets instead of a wrong answer.
     */
    public static final int LIMIT = 7;

    /**
     * The conversion cannot be completed under the constraints that were asked for.
     *
     * <p>It is the code of a strict conversion into a syntax that cannot carry the whole
     * source: {@code esj convert --to cii|ubl --fail-on-loss} leaves with it where the
     * writer's report holds a loss — a value, a component or a part of the document the
     * target syntax has no place for — and writes nothing, neither to {@code --out} nor to
     * the standard output. The input was read and understood and is not thereby invalid;
     * the target syntax is what cannot carry it. A value written by convention and a term
     * whose registry keeps it out of every syntax are not losses and do not reach this
     * code. It was reserved for this case before it was used, and it is 8 rather than 3
     * because 3 is what the virtual machine aborts with on a heap exhaustion, and a code
     * that collides with a crash is a code no caller can read.
     */
    public static final int CONSTRAINED = 8;

    /**
     * Nothing fatal was found, no limit was reached, and a component of the complete check
     * did not run or did not complete, so this run reached no verdict on the document.
     *
     * <p>It is the third state of the specification, section 9.5 as a command line sees
     * it. "Nothing is wrong" and "I could not tell" are different answers, and a tool that
     * folded them into {@link #SUCCESS} would be read as having checked what it did not
     * check: the exit code is what a pipeline branches on, and an exit code has no
     * footnotes.
     *
     * <p>The causes are a closed, documented vocabulary, and every report names which of
     * them applied to which component — the {@code reasons} member of {@code --output json}
     * and the last line of the text report. A caller that meant to run a reduced check
     * writes itself against this code; a caller that expects the whole check treats it as
     * a run to repeat with what it was missing.
     *
     * <p>It is 9 because 6 is the output failure, 7 is the limit and 8 is the constrained
     * conversion, and a code that carries two meanings is a code nobody can branch on.
     */
    public static final int INDETERMINATE = 9;

    private ExitCode() {
        throw new AssertionError("no instances");
    }
}
