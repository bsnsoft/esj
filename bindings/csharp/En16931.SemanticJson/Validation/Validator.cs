using System;
using System.Collections.Generic;
using En16931.SemanticJson.Json;
using En16931.SemanticJson.Model;

namespace En16931.SemanticJson.Validation;

/// <summary>
/// The three layers of the specification, section 9 over one input: the reader decides L1
/// and the structural validator decides L2 and L3 against the registry of the edition the
/// document names.
/// </summary>
/// <remarks>
/// The two are composed here rather than by every caller, because the status of the result
/// follows from both: a document whose bytes failed L1 is not measured at the model layers,
/// and a run that never saw bytes cannot answer L1 at all (specification, sections 3.5
/// and 9.5).
/// </remarks>
public static class Validator
{
    private static readonly Lazy<IReadOnlyList<Registry>> Bundled = new(Load);

    /// <summary>
    /// Returns the registries this build carries: the core editions, the 2017 one combined
    /// with the XRechnung extension so that extension terms are checked rather than reported
    /// as not checked.
    /// </summary>
    /// <returns>one registry per edition</returns>
    public static IReadOnlyList<Registry> Registries() => Bundled.Value;

    /// <summary>Validates the bytes of a document at all three layers.</summary>
    /// <param name="bytes">the document</param>
    /// <param name="registries">the registries to use, or <c>null</c> for the bundled ones</param>
    /// <param name="limits">the bounds to read under, or <c>null</c> for the defaults</param>
    /// <returns>the result of the run</returns>
    public static ValidationResult Validate(
        byte[] bytes,
        IEnumerable<Registry>? registries = null,
        Limits? limits = null)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        ReadResult read = EsjReader.WithLimits(limits ?? Limits.Defaults).ReadWithFindings(bytes);
        ValidationResult format = read.Validation();
        if (!read.IsWellFormed)
        {
            return format;
        }

        return format.Merge(Validate(read.Document!, registries));
    }

    /// <summary>
    /// Validates a document at the model layers. Layer L1 is decided by bytes, and a document
    /// handed over as an object has none, so the result names L1 as not evaluated and is
    /// never <c>VALID</c> on its own (specification, section 3.5).
    /// </summary>
    /// <param name="document">the document</param>
    /// <param name="registries">the registries to use, or <c>null</c> for the bundled ones</param>
    /// <returns>the result of layers L2 and L3</returns>
    public static ValidationResult Validate(SemanticDocument document, IEnumerable<Registry>? registries = null)
    {
        ArgumentNullException.ThrowIfNull(document);
        return StructuralValidator.Validate(
            document,
            registries ?? Registries(),
            new[] { ValidationLayer.L2, ValidationLayer.L3 });
    }

    private static IReadOnlyList<Registry> Load()
    {
        List<Registry> registries = new();
        foreach (string edition in Registry.Editions())
        {
            Registry core = Registry.ForEdition(edition);
            registries.Add(string.Equals(edition, Registry.DefaultEdition, StringComparison.Ordinal)
                ? core.WithExtension(Registry.XRechnungExtension()).WithExtension(Registry.B2cExtension())
                : core);
        }

        return registries;
    }
}
