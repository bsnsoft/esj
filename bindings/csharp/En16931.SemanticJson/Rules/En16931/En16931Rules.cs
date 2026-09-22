using System;
using System.Collections.Generic;
using En16931.SemanticJson.Model;

namespace En16931.SemanticJson.Rules.En16931;

/// <summary>
/// The EN 16931 rule pack this build carries: the rules of clause 6.4 over business terms,
/// with the rules the language cannot express written here under the names the manifest
/// declares them by.
/// </summary>
public static class En16931Pack
{
    /// <summary>The identifier of the pack.</summary>
    public const string PackId = "en16931";

    /// <summary>The version of the pack.</summary>
    public const string Version = "1.3.16";

    /// <summary>Returns the pack as this build carries it.</summary>
    /// <returns>the pack</returns>
    public static RulePack Pack() => RulePacks.Bundled(PackId, Version);

    /// <summary>Returns this binding's implementation of the rules the pack declares.</summary>
    /// <returns>the rules, under the names the manifest writes</returns>
    public static NativeRules NativeRules() => Rules.NativeRules.Of(new INativeRule[]
    {
        new Br62(), new Br63(), new Br64(), new Br65(),
        new BrCl07(), new BrCl10(), new BrCl11(), new BrCl13(),
        new BrCl21(), new BrCl24(), new BrCl25(), new BrCl26(),
        new BrCo09(),
        new BrS08(),
        new BrZ01(), new BrZ08(),
        new BrE01(), new BrE08(),
        new BrAe01(), new BrAe08(),
        new BrIc01(), new BrIc08(),
        new BrG01(), new BrG08(),
        new BrO01(), new BrO08(),
        new BrAf08(), new BrAg08(),
    });

    /// <summary>Returns the pack compiled against a registry.</summary>
    /// <param name="registry">the registry of the edition the documents will name</param>
    /// <returns>the engine</returns>
    public static RuleEngine Engine(Registry registry)
    {
        RulePack pack = Pack();
        return RuleEngine.Compile(pack, registry, CodeLists.Bundled(pack), NativeRules());
    }
}

/// <summary>BR-62: the seller electronic address names an identification scheme.</summary>
internal sealed class Br62 : SchemeIdentifier
{
    internal Br62() : base("BR-62", "Br62", "/BG-4/BT-34", "BT-34",
        "seller electronic address", "EN 16931-1, 6.4.1, Table 3, BR-62")
    {
    }
}

/// <summary>BR-63: the buyer electronic address names an identification scheme.</summary>
internal sealed class Br63 : SchemeIdentifier
{
    internal Br63() : base("BR-63", "Br63", "/BG-7/BT-49", "BT-49",
        "buyer electronic address", "EN 16931-1, 6.4.1, Table 3, BR-63")
    {
    }
}

/// <summary>BR-64: the item standard identifier names an identification scheme.</summary>
internal sealed class Br64 : SchemeIdentifier
{
    internal Br64() : base("BR-64", "Br64", "/BG-25/*/BG-31/BT-157", "BT-157",
        "item standard identifier", "EN 16931-1, 6.4.1, Table 3, BR-64")
    {
    }
}

/// <summary>BR-65: the item classification identifier names an identification scheme.</summary>
internal sealed class Br65 : SchemeIdentifier
{
    internal Br65() : base("BR-65", "Br65", "/BG-25/*/BG-31/BT-158/*", "BT-158",
        "item classification identifier", "EN 16931-1, 6.4.1, Table 3, BR-65")
    {
    }
}

/// <summary>BR-CL-07: the scheme of an object identifier is on UNTDID 1153.</summary>
internal sealed class BrCl07 : SchemeInList
{
    internal BrCl07() : base("BR-CL-07", "BrCl07", "untdid-1153",
        "EN 16931-1, 6.3, Table 2, BT-18 and BT-128",
        new[]
        {
            new Scheme("/BT-18", "BT-18", "invoiced object identifier"),
            new Scheme("/BG-25/*/BT-128", "BT-128", "invoice line object identifier"),
        })
    {
    }
}

/// <summary>BR-CL-10: the scheme of a party identifier is on ISO 6523 ICD.</summary>
internal sealed class BrCl10 : SchemeInList
{
    internal BrCl10() : base("BR-CL-10", "BrCl10", "iso-6523-icd",
        "EN 16931-1, 6.3, Table 2, BT-29, BT-46 and BT-60",
        new[]
        {
            new Scheme("/BG-4/BT-29/*", "BT-29", "seller identifier"),
            new Scheme("/BG-7/BT-46", "BT-46", "buyer identifier"),
            new Scheme("/BG-10/BT-60", "BT-60", "payee identifier"),
        })
    {
    }
}

/// <summary>BR-CL-11: the scheme of a legal registration identifier is on ISO 6523 ICD.</summary>
internal sealed class BrCl11 : SchemeInList
{
    internal BrCl11() : base("BR-CL-11", "BrCl11", "iso-6523-icd",
        "EN 16931-1, 6.3, Table 2, BT-30, BT-47 and BT-61",
        new[]
        {
            new Scheme("/BG-4/BT-30", "BT-30", "seller legal registration identifier"),
            new Scheme("/BG-7/BT-47", "BT-47", "buyer legal registration identifier"),
            new Scheme("/BG-10/BT-61", "BT-61", "payee legal registration identifier"),
        })
    {
    }
}

/// <summary>BR-CL-13: the scheme of an item classification is on UNTDID 7143.</summary>
internal sealed class BrCl13 : SchemeInList
{
    internal BrCl13() : base("BR-CL-13", "BrCl13", "untdid-7143",
        "EN 16931-1, 6.3, Table 2, BT-158",
        new[] { new Scheme("/BG-25/*/BG-31/BT-158/*", "BT-158", "item classification identifier") })
    {
    }
}

/// <summary>BR-CL-21: the scheme of an item standard identifier is on ISO 6523 ICD.</summary>
internal sealed class BrCl21 : SchemeInList
{
    internal BrCl21() : base("BR-CL-21", "BrCl21", "iso-6523-icd",
        "EN 16931-1, 6.3, Table 2, BT-157",
        new[] { new Scheme("/BG-25/*/BG-31/BT-157", "BT-157", "item standard identifier") })
    {
    }
}

/// <summary>BR-CL-26: the scheme of the deliver to location identifier is on ISO 6523 ICD.</summary>
internal sealed class BrCl26 : SchemeInList
{
    internal BrCl26() : base("BR-CL-26", "BrCl26", "iso-6523-icd",
        "EN 16931-1, 6.3, Table 2, BT-71",
        new[] { new Scheme("/BG-13/BT-71", "BT-71", "deliver to location identifier") })
    {
    }
}

/// <summary>BR-CL-24: the media type of an attachment is on the mime code list.</summary>
internal sealed class BrCl24 : INativeRule
{
    private const string Attachment = "/BG-24/*/BT-125";

    /// <inheritdoc />
    public string Id => "BR-CL-24";

    /// <inheritdoc />
    public string DeclaredAs => "BrCl24";

    /// <inheritdoc />
    public RuleSeverity Severity => RuleSeverity.Fatal;

    /// <inheritdoc />
    public string Context => "/";

    /// <inheritdoc />
    public IReadOnlyList<string> Terms { get; } = new[] { "BT-125" };

    /// <inheritdoc />
    public IReadOnlyList<string> Roots { get; } = new[] { Attachment };

    /// <inheritdoc />
    public string Source => "EN 16931-1, 6.5.11, Table 25, and 6.3, Table 2, BT-125";

    /// <inheritdoc />
    public string? Check(RuleContext context)
    {
        CodeList admitted = context.CodeList("mime-code");
        foreach (KeyValuePair<SemanticPath, SemanticValue> value in context.Values(Attachment))
        {
            string? mimeCode = value.Value.MimeCode;
            if (mimeCode is null || admitted.Contains(mimeCode))
            {
                continue;
            }

            return "The attached document (BT-125) at " + value.Key + " is of the media type "
                + context.Escape(mimeCode) + ", which is not on the mime-code snapshot this pack"
                + " decides against.";
        }

        return null;
    }
}

/// <summary>BR-CL-25: the scheme of an electronic address is on the CEF EAS list.</summary>
internal sealed class BrCl25 : INativeRule
{
    private static readonly (string Path, string Name)[] Addresses =
    {
        ("/BG-4/BT-34", "seller electronic address (BT-34)"),
        ("/BG-7/BT-49", "buyer electronic address (BT-49)"),
    };

    /// <inheritdoc />
    public string Id => "BR-CL-25";

    /// <inheritdoc />
    public string DeclaredAs => "BrCl25";

    /// <inheritdoc />
    public RuleSeverity Severity => RuleSeverity.Fatal;

    /// <inheritdoc />
    public string Context => "/";

    /// <inheritdoc />
    public IReadOnlyList<string> Terms { get; } = new[] { "BT-34", "BT-49" };

    /// <inheritdoc />
    public string Source => "EN 16931-1, 6.3, Table 2, BT-34 and BT-49";

    /// <inheritdoc />
    public string? Check(RuleContext context)
    {
        CodeList schemes = context.CodeList("eas");
        foreach ((string path, string name) in Addresses)
        {
            foreach (KeyValuePair<SemanticPath, SemanticValue> value in context.Values(path))
            {
                string? scheme = value.Value.Scheme;
                if (scheme is null || schemes.Contains(scheme))
                {
                    continue;
                }

                return "The " + name + " at " + value.Key + " names the identification scheme "
                    + context.Escape(scheme) + ", which is not on the eas snapshot this pack"
                    + " decides against.";
            }
        }

        return null;
    }
}

/// <summary>BR-CO-09: a VAT identifier begins with the country it was issued in.</summary>
internal sealed class BrCo09 : INativeRule
{
    private const string Greece = "EL";
    private const int PrefixLength = 2;

    private static readonly (string Path, string Name)[] Identifiers =
    {
        ("/BG-4/BT-31", "seller VAT identifier (BT-31)"),
        ("/BG-7/BT-48", "buyer VAT identifier (BT-48)"),
        ("/BG-11/BT-63", "seller tax representative VAT identifier (BT-63)"),
    };

    /// <inheritdoc />
    public string Id => "BR-CO-09";

    /// <inheritdoc />
    public string DeclaredAs => "BrCo09";

    /// <inheritdoc />
    public RuleSeverity Severity => RuleSeverity.Fatal;

    /// <inheritdoc />
    public string Context => "/";

    /// <inheritdoc />
    public IReadOnlyList<string> Terms { get; } = new[] { "BT-31", "BT-48", "BT-63" };

    /// <inheritdoc />
    public string Source => "EN 16931-1, 6.4.2, Table 4, BR-CO-9";

    /// <inheritdoc />
    public string? Check(RuleContext context)
    {
        CodeList countries = context.CodeList("iso-3166-1");
        foreach ((string path, string name) in Identifiers)
        {
            foreach (KeyValuePair<SemanticPath, string> value in context.Texts(path))
            {
                string identifier = value.Value;
                string prefix = identifier.Length < PrefixLength
                    ? identifier
                    : identifier.Substring(0, PrefixLength);
                if (string.Equals(prefix, Greece, StringComparison.Ordinal) || countries.Contains(prefix))
                {
                    continue;
                }

                return "The " + name + " at " + value.Key + " is " + context.Escape(identifier)
                    + ", and it begins with " + context.Escape(prefix) + ", which is not a country"
                    + " of the iso-3166-1 snapshot this pack decides against and is not the prefix"
                    + " EL that Greece uses.";
            }
        }

        return null;
    }
}

/// <summary>BR-S-08: the taxable amount of a standard rated breakdown.</summary>
internal sealed class BrS08 : CategoryTaxableAmount
{
    internal BrS08() : base("BR-S-08", "BrS08", "S", "standard rated",
        true, Tolerance.Ubl, "EN 16931-1, 6.4.3.3.2, Table 6, BR-S-8")
    {
    }
}

/// <summary>BR-Z-01: exactly one VAT breakdown for zero rated parts.</summary>
internal sealed class BrZ01 : ExactlyOneBreakdown
{
    internal BrZ01() : base("BR-Z-01", "BrZ01", "Z", "zero rated",
        "EN 16931-1, 6.4.3.4.2, Table 7, BR-Z-1")
    {
    }
}

/// <summary>BR-Z-08: the taxable amount of a zero rated breakdown.</summary>
internal sealed class BrZ08 : CategoryTaxableAmount
{
    internal BrZ08() : base("BR-Z-08", "BrZ08", "Z", "zero rated",
        false, Tolerance.Cii, "EN 16931-1, 6.4.3.4.2, Table 7, BR-Z-8")
    {
    }
}

/// <summary>BR-E-01: exactly one VAT breakdown for exempt parts.</summary>
internal sealed class BrE01 : ExactlyOneBreakdown
{
    internal BrE01() : base("BR-E-01", "BrE01", "E", "exempt from VAT",
        "EN 16931-1, 6.4.3.4.3, Table 8, BR-E-1")
    {
    }
}

/// <summary>BR-E-08: the taxable amount of an exempt breakdown.</summary>
internal sealed class BrE08 : CategoryTaxableAmount
{
    internal BrE08() : base("BR-E-08", "BrE08", "E", "exempt from VAT",
        false, Tolerance.Cii, "EN 16931-1, 6.4.3.4.3, Table 8, BR-E-8")
    {
    }
}

/// <summary>BR-AE-01: exactly one VAT breakdown for reverse charge parts.</summary>
internal sealed class BrAe01 : ExactlyOneBreakdown
{
    internal BrAe01() : base("BR-AE-01", "BrAe01", "AE", "reverse charge",
        "EN 16931-1, 6.4.3.4.4, Table 9, BR-AE-1")
    {
    }
}

/// <summary>BR-AE-08: the taxable amount of a reverse charge breakdown.</summary>
internal sealed class BrAe08 : CategoryTaxableAmount
{
    internal BrAe08() : base("BR-AE-08", "BrAe08", "AE", "reverse charge",
        false, Tolerance.Cii, "EN 16931-1, 6.4.3.4.4, Table 9, BR-AE-8")
    {
    }
}

/// <summary>BR-IC-01: exactly one VAT breakdown for intra-community supply.</summary>
internal sealed class BrIc01 : ExactlyOneBreakdown
{
    internal BrIc01() : base("BR-IC-01", "BrIc01", "K", "intra-community supply",
        "EN 16931-1, 6.4.3.4.5, Table 10, BR-IC-1")
    {
    }
}

/// <summary>BR-IC-08: the taxable amount of an intra-community supply breakdown.</summary>
internal sealed class BrIc08 : CategoryTaxableAmount
{
    internal BrIc08() : base("BR-IC-08", "BrIc08", "K", "intra-community supply",
        false, Tolerance.Cii, "EN 16931-1, 6.4.3.4.5, Table 10, BR-IC-8")
    {
    }
}

/// <summary>BR-G-01: exactly one VAT breakdown for export outside the EU.</summary>
internal sealed class BrG01 : ExactlyOneBreakdown
{
    internal BrG01() : base("BR-G-01", "BrG01", "G", "export outside the EU",
        "EN 16931-1, 6.4.3.4.6, Table 11, BR-G-1")
    {
    }
}

/// <summary>BR-G-08: the taxable amount of an export breakdown.</summary>
internal sealed class BrG08 : CategoryTaxableAmount
{
    internal BrG08() : base("BR-G-08", "BrG08", "G", "export outside the EU",
        false, Tolerance.Cii, "EN 16931-1, 6.4.3.4.6, Table 11, BR-G-8")
    {
    }
}

/// <summary>BR-O-01: exactly one VAT breakdown for parts not subject to VAT.</summary>
internal sealed class BrO01 : ExactlyOneBreakdown
{
    internal BrO01() : base("BR-O-01", "BrO01", "O", "not subject to VAT",
        "EN 16931-1, 6.4.3.4.7, Table 12, BR-O-1")
    {
    }
}

/// <summary>BR-O-08: the taxable amount of a breakdown not subject to VAT.</summary>
internal sealed class BrO08 : CategoryTaxableAmount
{
    internal BrO08() : base("BR-O-08", "BrO08", "O", "not subject to VAT",
        false, Tolerance.Neither, "EN 16931-1, 6.4.3.4.7, Table 12, BR-O-8")
    {
    }
}

/// <summary>BR-AF-08: the taxable amount of an IGIC breakdown.</summary>
internal sealed class BrAf08 : CategoryTaxableAmount
{
    internal BrAf08() : base("BR-AF-08", "BrAf08", "L",
        "IGIC, the general indirect tax of the Canary Islands",
        true, Tolerance.UblCiiCannotReport, "EN 16931-1, 6.4.3.4.8, Table 13, BR-IG-8")
    {
    }
}

/// <summary>BR-AG-08: the taxable amount of an IPSI breakdown.</summary>
internal sealed class BrAg08 : CategoryTaxableAmount
{
    internal BrAg08() : base("BR-AG-08", "BrAg08", "M",
        "IPSI, the tax on production, services and importation in Ceuta and Melilla",
        true, Tolerance.UblCiiCannotReport, "EN 16931-1, 6.4.3.4.8, Table 14, BR-IP-8")
    {
    }
}
