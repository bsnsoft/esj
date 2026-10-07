using System;
using System.IO;
using System.Reflection;
using System.Text.RegularExpressions;
using Xunit;

namespace BSNSoft.Esj.Tests;

/// <summary>
/// The package carries the name its publisher gives it and the version of the Maven project it
/// is part of, which the build reads from the root <c>pom.xml</c> rather than from a second
/// place that names the number.
/// </summary>
public class PackageTests
{
    /// <summary>The assembly carries the version of the project this checkout builds.</summary>
    [Fact]
    public void ItCarriesTheVersionOfTheProject()
    {
        string pom = File.ReadAllText(Path.Combine(Fixtures.Repository, "pom.xml"));
        Match project = Regex.Match(pom,
            "<artifactId>en16931-semantic-json</artifactId>\\s*<version>([^<]+)</version>",
            RegexOptions.None, TimeSpan.FromSeconds(1));
        Assert.True(project.Success, "the root pom.xml names the version of the project");

        string? version = typeof(Esj).Assembly
            .GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion;

        Assert.NotNull(version);
        Assert.Equal(project.Groups[1].Value, version!.Split('+')[0]);
    }

    /// <summary>The assembly, and with it the package, is named by its publisher.</summary>
    [Fact]
    public void ItIsNamedByItsPublisher()
    {
        Assert.Equal("BSNSoft.Esj", typeof(Esj).Assembly.GetName().Name);
        Assert.Equal("BSNSoft.Esj", typeof(Esj).Namespace);
    }
}
