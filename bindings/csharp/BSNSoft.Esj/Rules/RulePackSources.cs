using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using BSNSoft.Esj.Model;

namespace BSNSoft.Esj.Rules;

/// <summary>A rule pack this build carries, ready to be compiled for the edition it is written for.</summary>
public interface IRulePackSource
{
    /// <summary>Returns the edition the pack is written for, as the registry of it spells it.</summary>
    string Edition { get; }

    /// <summary>Returns the pack as this build carries it.</summary>
    /// <returns>the pack</returns>
    RulePack Pack();

    /// <summary>Returns the pack compiled against a registry of its edition.</summary>
    /// <param name="registry">the registry of the edition the documents name</param>
    /// <returns>the engine</returns>
    RuleEngine Engine(Registry registry);
}

/// <summary>
/// The rule packs this build carries, by the edition of the semantic model each is written for.
/// </summary>
/// <remarks>
/// Which editions a build holds a pack for is a property of the build. Every pack is a class of
/// this assembly that implements <see cref="IRulePackSource"/>, and nothing else names it, so a
/// distribution that leaves an edition out leaves out its pack, its rules written in code and the
/// class together, and finds one pack fewer. Not getting a pack for an edition is not a defect of
/// a document: the business rules are then a component of the check that did not run.
/// </remarks>
public static class RulePackSources
{
    private static readonly Lazy<IReadOnlyList<IRulePackSource>> Found = new(Discover);

    /// <summary>Returns every pack this build carries.</summary>
    /// <returns>the sources, one per edition</returns>
    public static IReadOnlyList<IRulePackSource> All() => Found.Value;

    /// <summary>Returns the pack of one edition, or <c>null</c> where this build carries none.</summary>
    /// <param name="edition">the edition, in the spelling of the registry that describes it</param>
    /// <returns>the source, or <c>null</c></returns>
    public static IRulePackSource? ForEdition(string edition)
    {
        ArgumentNullException.ThrowIfNull(edition);
        return All().FirstOrDefault(source => string.Equals(source.Edition, edition, StringComparison.Ordinal));
    }

    private static IReadOnlyList<IRulePackSource> Discover()
    {
        List<IRulePackSource> sources = new();
        foreach (Type type in typeof(RulePackSources).Assembly.GetTypes()
            .Where(type => type.IsClass && !type.IsAbstract && typeof(IRulePackSource).IsAssignableFrom(type))
            .OrderBy(type => type.FullName, StringComparer.Ordinal))
        {
            IRulePackSource source = (IRulePackSource)Activator.CreateInstance(type, nonPublic: true)!;
            if (sources.Any(known => known.Edition == source.Edition))
            {
                throw new RulePackException("this build carries two rule packs for the edition "
                    + source.Edition + "; an edition has one pack");
            }

            sources.Add(source);
        }

        return sources;
    }
}
