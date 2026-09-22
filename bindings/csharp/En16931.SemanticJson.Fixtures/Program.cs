using System;
using System.IO;

namespace En16931.SemanticJson.Fixtures;

/// <summary>
/// The command <c>conformance/fixtures/run.py --binding</c> starts: it reads one request per
/// line from the standard input and writes one answer per line to the standard output.
/// </summary>
public static class Program
{
    /// <summary>Runs the protocol.</summary>
    /// <param name="args">the root of the checkout, where the fixtures are read from</param>
    /// <returns>zero, unless a request could not be answered at all</returns>
    public static int Main(string[] args)
    {
        ArgumentNullException.ThrowIfNull(args);
        string repository = args.Length > 0
            ? args[0]
            : Environment.GetEnvironmentVariable("ESJ_REPOSITORY") ?? Directory.GetCurrentDirectory();
        new Protocol(repository).Run(Console.In, Console.Out);
        return 0;
    }
}
