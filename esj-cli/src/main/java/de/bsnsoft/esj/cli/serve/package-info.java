/**
 * {@code esj serve} and {@code esj mcp}: the tools of the command line as a REST API with its
 * OpenAPI description and as an MCP server over Streamable HTTP and over the standard streams.
 *
 * <p>One table of tools ({@code Tools}), one way to run a call ({@code Calls}), and three
 * front doors to them: {@link de.bsnsoft.esj.cli.serve.Http} for REST and MCP over HTTP,
 * {@link de.bsnsoft.esj.cli.serve.Stdio} for MCP over the standard streams. No class here
 * reads a document; every call starts the command line again as a child process with a heap
 * ceiling and a deadline ({@code Jobs}). The package is a preview and part of the tool, not a
 * library: its public types are public for the commands of {@code de.bsnsoft.esj.cli} only.
 */
package de.bsnsoft.esj.cli.serve;
