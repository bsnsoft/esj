using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using En16931.SemanticJson;
using En16931.SemanticJson.Json;
using En16931.SemanticJson.Model;
using En16931.SemanticJson.Rules;
using En16931.SemanticJson.Rules.En16931;
using En16931.SemanticJson.Validation;

namespace En16931.SemanticJson.Fixtures;

/// <summary>
/// The request protocol of <c>conformance/fixtures/run.py</c>: one JSON object per line in,
/// one JSON object per line out, so that the runner of the fixture manifest can measure this
/// binding against the same material as the reference implementation.
/// </summary>
/// <remarks>
/// Six requests exist — <c>editions</c>, <c>digest</c>, <c>canonicalize</c>, <c>validate</c>,
/// and <c>rules</c> — and the runner's own documentation states what each answers. A document
/// is named by a path relative to the repository root, or passed inline; an inline document is
/// written back to bytes and read like any other, because layer L1 is decided by bytes. A
/// <c>rules</c> request is answered with the pack this binding carries, unless it names a pack
/// of the rule language by its path in the repository.
/// </remarks>
public sealed class Protocol
{
    private readonly string _repository;
    private readonly Lazy<RuleEngine> _pack;
    private readonly Lazy<IReadOnlyList<Registry>> _registries;
    private readonly Dictionary<string, RuleEngine> _named = new(StringComparer.Ordinal);

    /// <summary>Answers requests about the fixtures of one checkout.</summary>
    /// <param name="repository">the root of the checkout</param>
    public Protocol(string repository)
    {
        ArgumentNullException.ThrowIfNull(repository);
        _repository = repository;
        _registries = new Lazy<IReadOnlyList<Registry>>(() => Validator.Registries());
        _pack = new Lazy<RuleEngine>(() => En16931Pack.Engine(
            Registry.ForEdition(Registry.DefaultEdition).WithExtension(Registry.XRechnungExtension())));
    }

    /// <summary>Answers one request.</summary>
    /// <param name="request">the request, as one JSON object</param>
    /// <returns>the answer, as one JSON object on one line</returns>
    public string Answer(string request)
    {
        ArgumentNullException.ThrowIfNull(request);
        using JsonDocument json = JsonDocument.Parse(request);
        JsonElement asked = json.RootElement;
        string operation = asked.GetProperty("op").GetString()!;
        return operation switch
        {
            "editions" => Editions(),
            "digest" => Digest(Bytes(asked)),
            "canonicalize" => Canonicalize(Bytes(asked)),
            "validate" => Validate(Bytes(asked)),
            "rules" => Rules(Bytes(asked), asked),
            _ => throw new ArgumentException("no request of this protocol is called " + operation, nameof(request)),
        };
    }

    /// <summary>
    /// Reads requests from a stream and writes one answer per line.
    /// </summary>
    /// <remarks>
    /// A request this binding cannot answer — a <c>digest</c> of a document the reader
    /// refuses is the ordinary one — is answered with an error object and the next line is
    /// read, as the other adapters of this protocol do. A process that ended instead would
    /// report one failing case to the runner as a broken pipe over the whole run.
    /// </remarks>
    /// <param name="input">the requests</param>
    /// <param name="output">the answers</param>
    public void Run(TextReader input, TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(input);
        ArgumentNullException.ThrowIfNull(output);
        while (input.ReadLine() is string request)
        {
            if (request.Length == 0)
            {
                continue;
            }

            output.WriteLine(Reply(request));
            output.Flush();
        }
    }

    private string Reply(string request)
    {
        try
        {
            return Answer(request);
        }
        catch (Exception failure) when (failure is not OutOfMemoryException)
        {
            return Write(writer => writer.WriteString("error", failure.Message));
        }
    }

    private byte[] Bytes(JsonElement asked)
    {
        if (asked.TryGetProperty("file", out JsonElement file))
        {
            return File.ReadAllBytes(Path.Combine(_repository, file.GetString()!));
        }

        return new UTF8Encoding(false).GetBytes(asked.GetProperty("document").GetRawText());
    }

    private string Editions() => Write(writer =>
    {
        writer.WriteStartArray("semanticModels");
        foreach (Registry registry in _registries.Value)
        {
            writer.WriteStringValue(registry.SemanticModel);
        }

        writer.WriteEndArray();
    });

    private static string Digest(byte[] bytes)
    {
        SemanticDocument document = EsjReader.Strict().Read(bytes);
        return Write(writer =>
        {
            writer.WriteString("semanticDigest", Canonicalizer.SemanticDigest(document));
            writer.WriteString("documentDigest", Canonicalizer.DocumentDigest(document));
            writer.WriteNumber("canonicalBytes", Canonicalizer.CanonicalBytes(document).Length);
            writer.WriteNumber("values", document.Values.Count);
        });
    }

    private static string Canonicalize(byte[] bytes)
    {
        SemanticDocument document = EsjReader.Strict().Read(bytes);
        return Write(writer => writer.WriteString("canonical", EsjWriter.Canonical().ToText(document)));
    }

    private string Validate(byte[] bytes)
    {
        ValidationResult result = Validator.Validate(bytes, _registries.Value);
        return Write(writer =>
        {
            writer.WriteStartArray("findings");
            foreach (Finding finding in result.Findings)
            {
                writer.WriteStartObject();
                writer.WriteString("path", finding.Path.Text);
                writer.WriteString("code", finding.Code.Code);
                writer.WriteEndObject();
            }

            writer.WriteEndArray();
            writer.WriteString("status", result.Status.ToString().ToUpperInvariant());
        });
    }

    private string Rules(byte[] bytes, JsonElement asked)
    {
        SemanticDocument document = EsjReader.Strict().Read(bytes);
        RuleEngine engine = asked.TryGetProperty("pack", out JsonElement named)
            ? Named(named.GetString()!, document.SemanticModel)
            : _pack.Value;
        IReadOnlyList<RuleFinding> findings = engine.Evaluate(document);
        return Write(writer =>
        {
            Codes(writer, "rules", findings.Select(finding => finding.Code));
            Codes(writer, "warnings", findings
                .Where(finding => finding.Severity == RuleSeverity.Warning)
                .Select(finding => finding.Code));
        });
    }

    /// <summary>
    /// Returns the engine of a pack of the rule language a rules request names by its path in
    /// the repository, compiled against the registry of the edition the document names.
    /// </summary>
    private RuleEngine Named(string file, string semanticModel)
    {
        string key = file + "|" + semanticModel;
        if (!_named.TryGetValue(key, out RuleEngine? engine))
        {
            Registry registry = _registries.Value.FirstOrDefault(known => known.Describes(semanticModel))
                ?? throw new ArgumentException(
                    "this binding carries no registry of " + semanticModel, nameof(semanticModel));
            using FileStream input = File.OpenRead(Path.Combine(_repository, file));
            engine = RuleEngine.Compile(RulePacks.Read(input, file), registry);
            _named[key] = engine;
        }

        return engine;
    }

    private static void Codes(Utf8JsonWriter writer, string member, IEnumerable<string> codes)
    {
        writer.WriteStartArray(member);
        foreach (string code in codes.Distinct(StringComparer.Ordinal).OrderBy(code => code, StringComparer.Ordinal))
        {
            writer.WriteStringValue(code);
        }

        writer.WriteEndArray();
    }

    private static string Write(Action<Utf8JsonWriter> members)
    {
        using MemoryStream bytes = new();
        using (Utf8JsonWriter writer = new(bytes))
        {
            writer.WriteStartObject();
            members(writer);
            writer.WriteEndObject();
        }

        return new UTF8Encoding(false).GetString(bytes.ToArray());
    }
}
