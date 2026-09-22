package de.bsnsoft.esj.typed.build;

import java.util.List;
import java.util.Objects;

/**
 * Signals that an invoice was asked for and the check of it found something.
 *
 * <p>The message lists what was found, one line each; {@link #report()} carries the same
 * as data. A caller that would rather look than be interrupted uses {@code validate()} on
 * the terminal step instead, which reports and does not throw.
 */
public final class BuildException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient BuildReport report;

    /**
     * Creates the exception.
     *
     * @param report what the check found
     * @throws NullPointerException if {@code report} is {@code null}
     */
    public BuildException(BuildReport report) {
        super(message(report));
        this.report = report;
    }

    /**
     * Returns what the check found.
     *
     * @return the report
     */
    public BuildReport report() {
        return report;
    }

    private static String message(BuildReport report) {
        List<String> lines = Objects.requireNonNull(report, "report").lines();
        StringBuilder text = new StringBuilder("this invoice is not ready: ")
                .append(lines.size()).append(lines.size() == 1 ? " finding" : " findings");
        for (String line : lines) {
            text.append("\n  ").append(line);
        }
        return text.toString();
    }
}
