/**
 * The {@code esj} command line tool.
 *
 * <p>Nine commands over one pipeline: read bytes, recognize the syntax, build a semantic
 * document with {@code esj-core} or {@code esj-xr}, and write the answer — as a document, a
 * report, a line or, for {@code render}, a page somebody reads. The only
 * knowledge this package holds about documents is which of three syntaxes a byte sequence
 * is written in, and even that reaches no further than the root element.
 *
 * <p>{@link de.bsnsoft.esj.cli.Main} and
 * {@link de.bsnsoft.esj.cli.ExitCode} are the public surface. Everything else
 * is an implementation of the command line and may change with it.
 */
package de.bsnsoft.esj.cli;
