using System;
using System.Text.Json;
using En16931.SemanticJson.Model;

namespace En16931.SemanticJson.Tests;

/// <summary>The registries the manifest names, as this binding carries them.</summary>
internal static class Registries
{
    /// <summary>Returns the registry this binding carries for a file the manifest names.</summary>
    /// <param name="file">the registry file, relative to the repository root</param>
    /// <returns>the registry</returns>
    internal static Registry Named(string file) => file switch
    {
        "model/en16931/2017.json" => Registry.ForEdition("2017"),
        "model/en16931/2026.json" => Registry.ForEdition("2026"),
        "model/xrechnung/3.0.2.json" => Registry.XRechnungExtension(),
        "model/b2c/0.1.json" => Registry.B2cExtension(),
        _ => throw new ArgumentException("the manifest names the registry " + file
            + ", which this binding does not carry", nameof(file)),
    };

    /// <summary>Returns the 2017 edition combined with the extension registries.</summary>
    internal static Registry Core2017 { get; } = Registry.ForEdition("2017")
        .WithExtension(Registry.XRechnungExtension())
        .WithExtension(Registry.B2cExtension());

    /// <summary>Returns the registry the sample paths of a manifest entry are measured against.</summary>
    /// <param name="semanticModel">the edition the entry names</param>
    /// <returns>a registry in which every step of a sample path exists</returns>
    internal static Registry Measuring(string semanticModel) =>
        semanticModel == "EN16931-1:2026" ? Registry.ForEdition("2026") : Core2017;
}

/// <summary>A value as the manifest writes it: a string, or the object of section 6.1.</summary>
internal static class Values
{
    /// <summary>Returns the value a manifest entry carries.</summary>
    /// <param name="written">the value as the manifest writes it</param>
    /// <returns>the value</returns>
    internal static SemanticValue Read(JsonElement written)
    {
        if (written.ValueKind == JsonValueKind.String)
        {
            return new SemanticValue(written.GetString()!);
        }

        return new SemanticValue(
            written.GetProperty("value").GetString()!,
            Member(written, "scheme"),
            Member(written, "schemeVersion"),
            Member(written, "mimeCode"),
            Member(written, "filename"));
    }

    private static string? Member(JsonElement written, string name) =>
        written.TryGetProperty(name, out JsonElement value) ? value.GetString() : null;
}
