package de.bsnsoft.esj.cli;

import de.bsnsoft.esj.cli.serve.ServeConfig;
import de.bsnsoft.esj.cli.serve.Stdio;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * {@code esj mcp}: the tools of this command line as an MCP server over the standard
 * streams, for an agent on the same machine.
 *
 * <p>One JSON-RPC message per line in and out; the standard output carries nothing else,
 * and every line for a person goes to the error stream. Documents are named by path and a
 * file a tool writes goes to the {@code out} the call names. Every call runs this tool again
 * as a child process with a heap ceiling and a deadline, as {@code esj serve} does. The
 * server ends when the client closes the standard input.
 */
@Command(name = "mcp",
        description = "Serve the tools of esj as an MCP server over the standard streams, for"
                + " an agent on this machine: documents by path, results written to out. Every"
                + " call runs esj as a child process with --job-heap and --job-timeout."
                + " Preview. See docs/serve.md.",
        sortOptions = false)
final class McpCommand implements Callable<Integer> {

    @Option(order = 1000, names = {"-h", "--help"}, usageHelp = true,
            description = "Show this help text and exit.")
    private boolean helpRequested;

    @Mixin
    private JobFlags jobs;

    private final Console console;

    McpCommand(Console console) {
        this.console = console;
    }

    @Override
    public Integer call() {
        try {
            ServeConfig config = jobs.apply(ServeCommand.config(console,
                    VersionProvider.artifactVersion()));
            return Stdio.run(config, new BufferedInputStream(console.in()), new OutputStream() {
                @Override
                public void write(int b) {
                    console.bytes(new byte[] {(byte) b});
                }

                @Override
                public void write(byte[] bytes, int offset, int length) {
                    console.bytes(Arrays.copyOfRange(bytes, offset, offset + length));
                }

                @Override
                public void flush() {
                    console.flush();
                }
            });
        } catch (IllegalArgumentException e) {
            throw CliException.input(e.getMessage(), e);
        }
    }
}
