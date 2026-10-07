package de.bsnsoft.esj.cli;

/**
 * Marks a command that reads a document: every one of them holds a deadline, whether or not
 * the caller wrote one.
 *
 * <p>The cost of reading a document is decided by whoever wrote it. An importer can be
 * handed a document built to be slow, a renderer one built to be large, and a command that
 * waits for its standard input waits for as long as the other end of the pipe likes. So
 * {@link Main} arms the watchdog of {@code --max-runtime} at
 * {@link GlobalOptions#DEFAULT_MAX_RUNTIME} before such a command runs, where the caller
 * named no number; it ends the run with {@link ExitCode#LIMIT} and no verdict. A command
 * that reads no document — the listing and the fetching of validation packs — is not
 * marked and keeps no deadline of its own.
 */
interface ReadsADocument {
}
