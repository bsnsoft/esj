using System;
using System.Collections.Generic;
using System.Linq;
using En16931.SemanticJson.Model;
using En16931.SemanticJson.Rules.En16931;

namespace En16931.SemanticJson.Rules.En16931V2026;

/// <summary>
/// The EN 16931-1:2026 rule pack this build carries: the rules of that edition over business
/// terms, with the rules the language cannot express written here under the names the manifest
/// declares them by.
/// </summary>
/// <remarks>
/// <para>Eight of those rules are the rules of the pack of the 2017 edition, unchanged: the four
/// that ask a scheme of the electronic addresses and of the item identifiers, and the four that
/// ask a stated scheme to be on its list. The rest are this edition's.</para>
/// <para>No official validation artefact exists for this edition. What stands behind each rule
/// is the <c>oracle</c> of the manifest, and no finding of this pack is corroborated by an
/// official artefact. This file carries facts of that edition and is left out of a distribution
/// that does not ship it (<c>model/en16931/2026.paths</c>), together with the pack it serves.</para>
/// </remarks>
public static class En16931V2026Pack
{
    /// <summary>The identifier of the pack.</summary>
    public const string PackId = "en16931-2026";

    /// <summary>The version of the pack.</summary>
    public const string Version = "0.1";

    /// <summary>Returns the pack as this build carries it.</summary>
    /// <returns>the pack</returns>
    public static RulePack Pack() => RulePacks.Bundled(PackId, Version);

    /// <summary>Returns this binding's implementation of the rules the pack declares.</summary>
    /// <returns>the rules, under the names the manifest writes</returns>
    public static NativeRules NativeRules() => Rules.NativeRules.Of(new INativeRule[]
    {
        new Br62(), new Br63(), new Br64(), new Br65(),
        new BrCl07(), new BrCl11(), new BrCl13(), new BrCl25(),
        new Br68(),
        new SchemeStated("BR-69", "Br69", "/BG-4/BT-29/*", "BT-29", "seller identifier"),
        new SchemeStated("BR-70", "Br70", "/BG-4/BT-30", "BT-30", "seller legal registration identifier"),
        new SchemeStated("BR-71", "Br71", "/BG-7/BT-46/*", "BT-46", "buyer identifier"),
        new SchemeStated("BR-72", "Br72", "/BG-7/BT-47", "BT-47", "buyer legal registration identifier"),
        new SchemeStated("BR-73", "Br73", "/BG-10/BT-60", "BT-60", "payee identifier"),
        new SchemeStated("BR-74", "Br74", "/BG-10/BT-61", "BT-61", "payee legal registration identifier"),
        new SchemeStated("BR-75", "Br75", "/BG-13/BT-71", "BT-71", "deliver to location identifier"),
        new VatIdentifierPrefix(),
        new TotalVatAmount(),
        new BreakdownPerCombination(),
        new AccountingConversion("BR-CO-49", "BrCo49", "BT-116", "taxable amount", false),
        new AccountingConversion("BR-CO-50", "BrCo50", "BT-117", "tax amount", true),
        new TaxableAmount("BR-S-08", "BrS08", "S", "standard or reduced rate", true,
            "EN 16931-1:2026, 6.4.3.3.2, Table 6, BR-S-8"),
        new TaxableAmount("BR-Z-08", "BrZ08", "Z", "zero rated", false,
            "EN 16931-1:2026, 6.4.3.4.2, Table 7, BR-Z-8"),
        new TaxableAmount("BR-E-08", "BrE08", "E", "exempt from VAT", false,
            "EN 16931-1:2026, 6.4.3.4.4, Table 8, BR-E-8"),
        new TaxableAmount("BR-AE-08", "BrAe08", "AE", "reverse charge", false,
            "EN 16931-1:2026, 6.4.3.4.6, Table 9, BR-AE-8"),
        new TaxableAmount("BR-IC-08", "BrIc08", "K", "intra-community supply", false,
            "EN 16931-1:2026, 6.4.3.4.8, Table 10, BR-IC-8"),
        new TaxableAmount("BR-G-08", "BrG08", "G", "export outside the EU", false,
            "EN 16931-1:2026, 6.4.3.4.10, Table 11, BR-G-8"),
        new TaxableAmount("BR-O-08", "BrO08", "O", "not subject to VAT", false,
            "EN 16931-1:2026, 6.4.3.4.12, Table 12, BR-O-8"),
        new TaxableAmount("BR-IG-08", "BrIg08", "L", "Canary Islands tax", false,
            "EN 16931-1:2026, 6.4.3.4.15, Table 14, BR-IG-8"),
        new TaxableAmount("BR-IP-08", "BrIp08", "M", "Ceuta and Melilla tax", false,
            "EN 16931-1:2026, 6.4.3.4.15, Table 15, BR-IP-8"),
        new BreakdownPerReason("BR-E-01", "BrE01", "E", "exempt from VAT", false,
            "EN 16931-1:2026, 6.4.3.4.4, Table 8, BR-E-1"),
        new BreakdownPerReason("BR-AE-01", "BrAe01", "AE", "reverse charge", false,
            "EN 16931-1:2026, 6.4.3.4.6, Table 9, BR-AE-1"),
        new BreakdownPerReason("BR-IC-01", "BrIc01", "K", "intra-community supply", true,
            "EN 16931-1:2026, 6.4.3.4.8, Table 10, BR-IC-1"),
        new BreakdownPerReason("BR-G-01", "BrG01", "G", "export outside the EU", false,
            "EN 16931-1:2026, 6.4.3.4.10, Table 11, BR-G-1"),
        new BreakdownPerReason("BR-O-01", "BrO01", "O", "not subject to VAT", false,
            "EN 16931-1:2026, 6.4.3.4.12, Table 12, BR-O-1"),
    });

    /// <summary>Returns the pack compiled against a registry of its edition.</summary>
    /// <param name="registry">the registry of the edition the documents will name</param>
    /// <returns>the engine</returns>
    public static RuleEngine Engine(Registry registry)
    {
        RulePack pack = Pack();
        return RuleEngine.Compile(pack, registry, CodeLists.Bundled(pack), NativeRules());
    }
}

/// <summary>The pack of the 2026 edition as one of the packs of this build.</summary>
internal sealed class En16931V2026PackSource : IRulePackSource
{
    private readonly Lazy<RulePack> _pack = new(En16931V2026Pack.Pack);

    /// <inheritdoc />
    public string Edition => _pack.Value.Edition;

    /// <inheritdoc />
    public RulePack Pack() => _pack.Value;

    /// <inheritdoc />
    public RuleEngine Engine(Registry registry) => En16931V2026Pack.Engine(registry);
}

/// <summary>A party identifier of this edition carries the scheme it was issued under.</summary>
internal sealed class SchemeStated : SchemeIdentifier
{
    internal SchemeStated(string id, string declaredAs, string pattern, string term, string name)
        : base(id, declaredAs, pattern, term, name, "EN 16931-1:2026, 6.4.1, Table 3, " + id)
    {
    }
}

/// <summary>
/// The parts of an invoice the VAT rules of this edition are about, read once per run: a VAT
/// category, a rate, an amount, an exemption reason code, an exemption reason text and a goods
/// or services code of every line, allowance, charge and breakdown.
/// </summary>
internal static class VatParts
{
    private const char Separator = '\u001f';

    private static readonly string[] Breakdowns =
    {
        "/BG-23/*/BT-118", "/BG-23/*/BT-119", "/BG-23/*/BT-116",
        "/BG-23/*/BT-121", "/BG-23/*/BT-120", "/BG-23/*/BT-210",
    };

    private static readonly string[] Lines =
    {
        "/BG-25/*/BG-30/BT-151", "/BG-25/*/BG-30/BT-152", "/BG-25/*/BT-131",
        "/BG-25/*/BG-30/BT-195", "/BG-25/*/BG-30/BT-194", "/BG-25/*/BG-31/BT-196",
    };

    private static readonly string[] Allowances =
    {
        "/BG-20/*/BT-95", "/BG-20/*/BT-96", "/BG-20/*/BT-92",
        "/BG-20/*/BT-174", "/BG-20/*/BT-173", "/BG-20/*/BT-213",
    };

    private static readonly string[] Charges =
    {
        "/BG-21/*/BT-102", "/BG-21/*/BT-103", "/BG-21/*/BT-99",
        "/BG-21/*/BT-176", "/BG-21/*/BT-175", "/BG-21/*/BT-214",
    };

    /// <summary>The business terms every rule of this family declares that it reads.</summary>
    internal static IReadOnlyList<string> Terms { get; } = new[]
    {
        "BT-5", "BT-92", "BT-95", "BT-96", "BT-99", "BT-102", "BT-103", "BT-116", "BT-117",
        "BT-118", "BT-119", "BT-120", "BT-121", "BT-131", "BT-151", "BT-152", "BT-173", "BT-174",
        "BT-175", "BT-176", "BT-184", "BT-194", "BT-195", "BT-196", "BT-210", "BT-213", "BT-214",
    };

    private const string BreakdownCurrency = "/BG-23/*/BT-184";

    private const string BreakdownTax = "/BG-23/*/BT-117";

    /// <summary>The patterns these rules evaluate from the document root.</summary>
    internal static IReadOnlyList<string> Roots { get; } =
        Breakdowns.Concat(Lines).Concat(Allowances).Concat(Charges)
            .Append(BreakdownTax).Append(BreakdownCurrency).ToList();

    /// <summary>One instance of a group that carries a VAT category.</summary>
    /// <param name="Path">the group instance</param>
    /// <param name="Category">the VAT category code, or <c>null</c></param>
    /// <param name="Rate">the VAT rate, or <c>null</c></param>
    /// <param name="Amount">the amount, or <c>null</c></param>
    /// <param name="ExemptionCode">the exemption reason code, or <c>null</c></param>
    /// <param name="ExemptionText">the exemption reason text, or <c>null</c></param>
    /// <param name="GoodsCode">the goods or services code, or <c>null</c></param>
    internal sealed record Part(
        SemanticPath Path, string? Category, BigDecimal? Rate, BigDecimal? Amount,
        string? ExemptionCode, string? ExemptionText, string? GoodsCode)
    {
        internal bool Is(string code) => string.Equals(code, Category, StringComparison.Ordinal);

        internal bool StatesAReason => ExemptionCode is not null || ExemptionText is not null || GoodsCode is not null;

        internal string Key(bool divided) => divided
            ? Join(Category, ExemptionCode, ExemptionText, GoodsCode)
            : Join(Category);

        internal string KeyWithRate(bool divided) =>
            Key(divided) + Separator + "@" + (Rate is null ? string.Empty : Rate.Value.StripTrailingZeros().ToPlainString());

        internal string Reason()
        {
            List<string> said = new();
            if (ExemptionCode is not null)
            {
                said.Add("the exemption reason and specification code " + ExemptionCode);
            }

            if (ExemptionText is not null)
            {
                said.Add("an exemption reason text");
            }

            if (GoodsCode is not null)
            {
                said.Add("the goods or services code " + GoodsCode);
            }

            return said.Count == 0 ? string.Empty : " with " + string.Join(", ", said);
        }

        private static string Join(params string?[] values) =>
            string.Concat(values.Select(value => (value ?? string.Empty) + Separator));
    }

    /// <summary>
    /// Returns the VAT breakdowns in the invoice currency: those that name no currency (BT-184)
    /// or name the invoice currency (BT-5). A breakdown in the VAT accounting currency is not one
    /// of them; <see cref="Accounting"/> returns those.
    /// </summary>
    internal static IReadOnlyList<Part> VatBreakdowns(RuleContext context) => context.Shared(
        "VatParts.breakdowns",
        () => (IReadOnlyList<Part>)AllBreakdowns(context).Where(breakdown => InInvoiceCurrency(context, breakdown)).ToList());

    /// <summary>Returns the VAT breakdowns that name a currency other than the invoice currency.</summary>
    internal static IReadOnlyList<Part> Accounting(RuleContext context) => context.Shared(
        "VatParts.accounting",
        () => (IReadOnlyList<Part>)AllBreakdowns(context).Where(breakdown => !InInvoiceCurrency(context, breakdown)).ToList());

    /// <summary>Returns the currency a breakdown names for its amounts, or <c>null</c>.</summary>
    internal static string? CurrencyOf(RuleContext context, Part breakdown) =>
        Currencies(context).TryGetValue(breakdown.Path, out string? currency) ? currency : null;

    /// <summary>Returns the tax amount a breakdown states, or <c>null</c>.</summary>
    internal static BigDecimal? TaxAmount(RuleContext context, Part breakdown) =>
        context.Shared("VatParts.taxAmounts", () => Numbers(context, BreakdownTax))
            .TryGetValue(breakdown.Path, out BigDecimal amount) ? amount : null;

    private static IReadOnlyList<Part> AllBreakdowns(RuleContext context) => Join(context, "VatParts.allBreakdowns", Breakdowns);

    private static Dictionary<SemanticPath, string> Currencies(RuleContext context) =>
        context.Shared("VatParts.currencies", () => Texts(context, BreakdownCurrency));

    private static bool InInvoiceCurrency(RuleContext context, Part breakdown)
    {
        string? currency = CurrencyOf(context, breakdown);
        return currency is null
            || string.Equals(currency, context.Texts("/BT-5").Select(read => read.Value).FirstOrDefault(), StringComparison.Ordinal);
    }

    internal static IReadOnlyList<Part> InvoiceLines(RuleContext context) => Join(context, "VatParts.lines", Lines);

    internal static IReadOnlyList<Part> DocumentAllowances(RuleContext context) => Join(context, "VatParts.allowances", Allowances);

    internal static IReadOnlyList<Part> DocumentCharges(RuleContext context) => Join(context, "VatParts.charges", Charges);

    /// <summary>
    /// Returns the VAT categories this invoice divides by exemption reason: those of which a
    /// line, an allowance or a charge states one of the three terms. Where none is stated, a
    /// part belongs to a breakdown by category alone.
    /// </summary>
    internal static IReadOnlySet<string> Divided(RuleContext context) => context.Shared("VatParts.divided", () =>
    {
        HashSet<string> codes = new(StringComparer.Ordinal);
        foreach (IReadOnlyList<Part> group in new[] { InvoiceLines(context), DocumentAllowances(context), DocumentCharges(context) })
        {
            foreach (Part part in group)
            {
                if (part.Category is not null && part.StatesAReason)
                {
                    codes.Add(part.Category);
                }
            }
        }

        return (IReadOnlySet<string>)codes;
    });

    /// <summary>Returns the lines, allowances and charges of one VAT category, in document order.</summary>
    internal static IReadOnlyList<Part> Of(RuleContext context, string code) =>
        new[] { InvoiceLines(context), DocumentAllowances(context), DocumentCharges(context) }
            .SelectMany(group => group.Where(part => part.Is(code)))
            .ToList();

    /// <summary>Returns what the parts under one breakdown come to, exactly.</summary>
    internal static BigDecimal Under(RuleContext context, Part breakdown, bool byRate)
    {
        (Dictionary<string, BigDecimal> byKey, Dictionary<string, BigDecimal> byKeyAndRate) = context.Shared(
            "VatParts.totals", () => Totals(context));
        bool divided = Divided(context).Contains(breakdown.Category!);
        return byRate
            ? byKeyAndRate.TryGetValue(breakdown.KeyWithRate(divided), out BigDecimal rated) ? rated : BigDecimal.Zero
            : byKey.TryGetValue(breakdown.Key(divided), out BigDecimal total) ? total : BigDecimal.Zero;
    }

    /// <summary>
    /// Returns the number of fraction digits the currency of the invoice admits, or <c>null</c>
    /// where the invoice names no currency or the snapshot gives it none.
    /// </summary>
    internal static int? MinorUnit(RuleContext context)
    {
        string? currency = context.Texts("/BT-5").Select(read => read.Value).FirstOrDefault();
        return currency is null ? null : context.CodeList("iso-4217").MinorUnit(currency);
    }

    /// <summary>
    /// What a rule compares two parts on, as a key of a hash set: the category, the rate where it
    /// is compared, and — where the invoice divides the category — the exemption reason code, the
    /// exemption reason text and, where it is compared, the goods or services code. Two keys are
    /// equal exactly where the parts agree on each of those, ordinally; the rate carries no
    /// trailing zeros, so that 19 and 19.00 are one key, as they are one number.
    /// </summary>
    /// <param name="Category">the VAT category code</param>
    /// <param name="Rate">the rate without trailing zeros, or <c>null</c> where it is not compared</param>
    /// <param name="ExemptionCode">the exemption reason code, where compared</param>
    /// <param name="ExemptionText">the exemption reason text, where compared</param>
    /// <param name="GoodsCode">the goods or services code, where compared</param>
    internal sealed record Combination(
        string? Category, string? Rate, string? ExemptionCode, string? ExemptionText, string? GoodsCode)
    {
        /// <summary>Returns what a part states, as far as a rule compares it.</summary>
        /// <param name="part">the part</param>
        /// <param name="withRate">whether the rate is part of the key</param>
        /// <param name="divided">whether the invoice divides the category of the part</param>
        /// <param name="withGoodsCode">whether the goods or services code is part of the key where divided</param>
        /// <returns>the key</returns>
        internal static Combination Of(Part part, bool withRate, bool divided, bool withGoodsCode)
        {
            string? rate = withRate && part.Rate is not null ? part.Rate.Value.StripTrailingZeros().ToPlainString() : null;
            return divided
                ? new Combination(part.Category, rate, part.ExemptionCode, part.ExemptionText, withGoodsCode ? part.GoodsCode : null)
                : new Combination(part.Category, rate, null, null, null);
        }
    }

    /// <summary>
    /// Returns the combinations the VAT breakdowns in the invoice currency state, each once: a
    /// breakdown with its rate, for a part that states one, and without it, for a part that states
    /// none. Whether a part has a breakdown of its own is then one look-up, which is what makes the
    /// question cost the number of parts plus the number of breakdowns and not their product.
    /// </summary>
    internal static IReadOnlySet<Combination> Combinations(RuleContext context) => context.Shared(
        "VatParts.combinations",
        () =>
        {
            IReadOnlySet<string> divided = Divided(context);
            HashSet<Combination> stated = new();
            foreach (Part breakdown in VatBreakdowns(context))
            {
                if (breakdown.Category is null)
                {
                    continue;
                }

                bool split = divided.Contains(breakdown.Category);
                stated.Add(Combination.Of(breakdown, false, split, true));
                stated.Add(Combination.Of(breakdown, true, split, true));
            }

            return (IReadOnlySet<Combination>)stated;
        });

    /// <summary>
    /// Returns the VAT breakdowns in the invoice currency by the key a breakdown in the VAT
    /// accounting currency is paired on; where two state the same key, the first in document order.
    /// </summary>
    internal static IReadOnlyDictionary<string, Part> Partners(RuleContext context) => context.Shared(
        "VatParts.partners",
        () =>
        {
            Dictionary<string, Part> first = new(StringComparer.Ordinal);
            foreach (Part breakdown in VatBreakdowns(context))
            {
                first.TryAdd(breakdown.KeyWithRate(true), breakdown);
            }

            return (IReadOnlyDictionary<string, Part>)first;
        });

    private static (Dictionary<string, BigDecimal>, Dictionary<string, BigDecimal>) Totals(RuleContext context)
    {
        IReadOnlySet<string> divided = Divided(context);
        Dictionary<string, BigDecimal> byKey = new(StringComparer.Ordinal);
        Dictionary<string, BigDecimal> byKeyAndRate = new(StringComparer.Ordinal);
        void Add(IReadOnlyList<Part> parts, bool subtract)
        {
            foreach (Part part in parts)
            {
                if (part.Category is null || part.Amount is null)
                {
                    continue;
                }

                bool split = divided.Contains(part.Category);
                BigDecimal amount = subtract ? part.Amount.Value.Negate() : part.Amount.Value;
                string key = part.Key(split);
                byKey[key] = byKey.TryGetValue(key, out BigDecimal known) ? known.Add(amount) : amount;
                if (part.Rate is not null)
                {
                    string rated = part.KeyWithRate(split);
                    byKeyAndRate[rated] = byKeyAndRate.TryGetValue(rated, out BigDecimal at) ? at.Add(amount) : amount;
                }
            }
        }

        Add(InvoiceLines(context), false);
        Add(DocumentCharges(context), false);
        Add(DocumentAllowances(context), true);
        return (byKey, byKeyAndRate);
    }

    private static IReadOnlyList<Part> Join(RuleContext context, string key, IReadOnlyList<string> patterns) =>
        context.Shared(key, () =>
        {
            Dictionary<SemanticPath, string> categories = Texts(context, patterns[0]);
            Dictionary<SemanticPath, BigDecimal> rates = Numbers(context, patterns[1]);
            Dictionary<SemanticPath, BigDecimal> amounts = Numbers(context, patterns[2]);
            Dictionary<SemanticPath, string> codes = Texts(context, patterns[3]);
            Dictionary<SemanticPath, string> reasons = Texts(context, patterns[4]);
            Dictionary<SemanticPath, string> goods = Texts(context, patterns[5]);
            List<SemanticPath> order = categories.Keys.Union(amounts.Keys).ToList();
            order.Sort((left, right) => left.CompareTo(right));
            return (IReadOnlyList<Part>)order.Select(instance => new Part(
                instance,
                categories.TryGetValue(instance, out string? category) ? category : null,
                rates.TryGetValue(instance, out BigDecimal rate) ? rate : null,
                amounts.TryGetValue(instance, out BigDecimal amount) ? amount : null,
                codes.TryGetValue(instance, out string? code) ? code : null,
                reasons.TryGetValue(instance, out string? reason) ? reason : null,
                goods.TryGetValue(instance, out string? good) ? good : null)).ToList();
        });

    private static Dictionary<SemanticPath, string> Texts(RuleContext context, string pattern)
    {
        Dictionary<SemanticPath, string> found = new();
        foreach (KeyValuePair<SemanticPath, string> read in context.Texts(pattern))
        {
            found[read.Key.Prefix(2)] = read.Value;
        }

        return found;
    }

    private static Dictionary<SemanticPath, BigDecimal> Numbers(RuleContext context, string pattern)
    {
        Dictionary<SemanticPath, BigDecimal> found = new();
        foreach (KeyValuePair<SemanticPath, BigDecimal> read in context.Decimals(pattern))
        {
            found[read.Key.Prefix(2)] = read.Value;
        }

        return found;
    }
}

/// <summary>A rule of this family: fatal, about the document, reading the VAT parts.</summary>
internal abstract class VatRule : INativeRule
{
    private protected VatRule(string id, string declaredAs, string source)
    {
        Id = id;
        DeclaredAs = declaredAs;
        Source = source;
    }

    /// <inheritdoc />
    public string Id { get; }

    /// <inheritdoc />
    public string DeclaredAs { get; }

    /// <inheritdoc />
    public RuleSeverity Severity => RuleSeverity.Fatal;

    /// <inheritdoc />
    public string Context => "/";

    /// <inheritdoc />
    public IReadOnlyList<string> Terms => VatParts.Terms;

    /// <inheritdoc />
    public IReadOnlyList<string> Roots => VatParts.Roots;

    /// <inheritdoc />
    public string Source { get; }

    /// <inheritdoc />
    public abstract string? Check(RuleContext context);
}

/// <summary>
/// The taxable amount of a VAT breakdown is what the parts of the invoice under it come to,
/// rounded once, half up, to the minor unit of the invoice currency, and compared exactly.
/// </summary>
internal sealed class TaxableAmount : VatRule
{
    private readonly string _code;
    private readonly string _name;
    private readonly bool _byRate;

    internal TaxableAmount(string id, string declaredAs, string code, string name, bool byRate, string source)
        : base(id, declaredAs, source)
    {
        _code = code;
        _name = name;
        _byRate = byRate;
    }

    /// <inheritdoc />
    public override string? Check(RuleContext context)
    {
        int? digits = VatParts.MinorUnit(context);
        if (digits is null)
        {
            return null;
        }

        foreach (VatParts.Part breakdown in VatParts.VatBreakdowns(context))
        {
            if (!breakdown.Is(_code) || breakdown.Amount is null || (_byRate && breakdown.Rate is null))
            {
                continue;
            }

            BigDecimal expected = VatParts.Under(context, breakdown, _byRate).SetScale(digits.Value);
            if (breakdown.Amount.Value.CompareTo(expected) == 0)
            {
                continue;
            }

            return "The VAT breakdown at " + breakdown.Path + " is categorised \"" + _code + "\" ("
                + _name + ")"
                + (_byRate ? " at the rate " + breakdown.Rate!.Value.ToPlainString() + " per cent" : string.Empty)
                + breakdown.Reason()
                + " and states the taxable amount (BT-116) " + breakdown.Amount.Value.ToPlainString()
                + "; the invoice lines, document level charges and taxes and document level"
                + " allowances under that breakdown come to " + expected.ToPlainString() + ".";
        }

        return null;
    }
}

/// <summary>
/// An invoice that categorises a part under a category in which no tax is levied carries a VAT
/// breakdown of that category for every exemption reason it used, and for the intra-community
/// category for every goods or services code as well.
/// </summary>
internal sealed class BreakdownPerReason : VatRule
{
    private readonly string _code;
    private readonly string _name;
    private readonly bool _withGoodsCode;

    internal BreakdownPerReason(string id, string declaredAs, string code, string name, bool withGoodsCode, string source)
        : base(id, declaredAs, source)
    {
        _code = code;
        _name = name;
        _withGoodsCode = withGoodsCode;
    }

    /// <inheritdoc />
    public override string? Check(RuleContext context)
    {
        bool divided = VatParts.Divided(context).Contains(_code);
        HashSet<VatParts.Combination> stated = new();
        foreach (VatParts.Part breakdown in VatParts.VatBreakdowns(context))
        {
            if (breakdown.Is(_code))
            {
                stated.Add(VatParts.Combination.Of(breakdown, false, divided, _withGoodsCode));
            }
        }

        foreach (VatParts.Part part in VatParts.Of(context, _code))
        {
            if (!stated.Contains(VatParts.Combination.Of(part, false, divided, _withGoodsCode)))
            {
                return "The invoice states " + part.Path + " under the VAT category \"" + _code
                    + "\" (" + _name + ")" + part.Reason()
                    + ", and no VAT breakdown (BG-23) of that category states the same exemption"
                    + " reason; this edition asks for one breakdown per reason.";
            }
        }

        return null;
    }
}

/// <summary>
/// BR-CO-14: the tax amounts of the VAT breakdowns in the invoice currency add up to the invoice
/// total VAT amount (BT-110), rounded once to the minor unit of that currency and compared
/// exactly.
/// </summary>
internal sealed class TotalVatAmount : INativeRule
{
    private static readonly IReadOnlyList<string> Declared = VatParts.Terms.Append("BT-110").ToList();

    /// <inheritdoc />
    public string Id => "BR-CO-14";

    /// <inheritdoc />
    public string DeclaredAs => "BrCo14";

    /// <inheritdoc />
    public RuleSeverity Severity => RuleSeverity.Fatal;

    /// <inheritdoc />
    public string Context => "/";

    /// <inheritdoc />
    public IReadOnlyList<string> Terms => Declared;

    /// <inheritdoc />
    public IReadOnlyList<string> Roots => VatParts.Roots;

    /// <inheritdoc />
    public string Source => "EN 16931-1:2026, 6.4.2, Table 4, BR-CO-14";

    /// <inheritdoc />
    public string? Check(RuleContext context)
    {
        BigDecimal? stated = context.Decimal("/BG-22/BT-110");
        int? digits = VatParts.MinorUnit(context);
        if (stated is null || digits is null)
        {
            return null;
        }

        BigDecimal sum = BigDecimal.Zero;
        foreach (VatParts.Part breakdown in VatParts.VatBreakdowns(context))
        {
            sum = sum.Add(VatParts.TaxAmount(context, breakdown) ?? BigDecimal.Zero);
        }

        BigDecimal expected = sum.SetScale(digits.Value);
        if (stated.Value.CompareTo(expected) == 0)
        {
            return null;
        }

        return "The VAT category tax amounts (BT-117) of the VAT breakdowns in the invoice"
            + " currency add up to " + expected.ToPlainString() + ", and the invoice total VAT amount"
            + " (BT-110) carries " + stated.Value.ToPlainString() + ".";
    }
}

/// <summary>
/// BR-CO-18: every line, document level allowance and document level charge or tax finds a VAT
/// breakdown in the invoice currency of its category, of its rate where it states one, and —
/// where the invoice divides the category — of its exemption reason and goods code.
/// </summary>
internal sealed class BreakdownPerCombination : VatRule
{
    internal BreakdownPerCombination()
        : base("BR-CO-18", "BrCo18", "EN 16931-1:2026, 6.4.2, Table 4, BR-CO-18")
    {
    }

    /// <inheritdoc />
    public override string? Check(RuleContext context)
    {
        IReadOnlySet<VatParts.Combination> stated = VatParts.Combinations(context);
        IReadOnlySet<string> divided = VatParts.Divided(context);
        foreach (IReadOnlyList<VatParts.Part> group in new[] { VatParts.InvoiceLines(context), VatParts.DocumentAllowances(context), VatParts.DocumentCharges(context) })
        {
            foreach (VatParts.Part part in group)
            {
                if (part.Category is null)
                {
                    continue;
                }

                if (!stated.Contains(VatParts.Combination.Of(part, true, divided.Contains(part.Category), true)))
                {
                    return "The invoice states " + part.Path + " under the VAT category \"" + part.Category + "\""
                        + (part.Rate is null ? string.Empty : " at the rate " + part.Rate.Value.ToPlainString() + " per cent")
                        + part.Reason() + ", and no VAT breakdown (BG-23) in the invoice currency states"
                        + " that combination; this edition asks for one breakdown per combination of"
                        + " category, rate, exemption reason and goods or services code.";
                }
            }
        }

        return null;
    }
}

/// <summary>
/// BR-CO-49 and BR-CO-50: an amount of a VAT breakdown in the VAT accounting currency is the
/// amount of the breakdown in the invoice currency that states the same combination, multiplied
/// by the exchange rate (BT-167), rounded once to the minor unit of the accounting currency,
/// within the tolerance of clause 6.5.14 on the amount provided.
/// </summary>
internal sealed class AccountingConversion : INativeRule
{
    private static readonly BigDecimal Thousandth = BigDecimal.Parse("0.001");
    private static readonly BigDecimal Floor = BigDecimal.Parse("0.01");
    private static readonly IReadOnlyList<string> Declared = VatParts.Terms.Concat(new[] { "BT-6", "BT-167" }).ToList();

    private readonly string _term;
    private readonly string _name;
    private readonly bool _tax;

    internal AccountingConversion(string id, string declaredAs, string term, string name, bool tax)
    {
        Id = id;
        DeclaredAs = declaredAs;
        _term = term;
        _name = name;
        _tax = tax;
    }

    /// <inheritdoc />
    public string Id { get; }

    /// <inheritdoc />
    public string DeclaredAs { get; }

    /// <inheritdoc />
    public RuleSeverity Severity => RuleSeverity.Fatal;

    /// <inheritdoc />
    public string Context => "/";

    /// <inheritdoc />
    public IReadOnlyList<string> Terms => Declared;

    /// <inheritdoc />
    public IReadOnlyList<string> Roots => VatParts.Roots;

    /// <inheritdoc />
    public string Source => "EN 16931-1:2026, 6.4.2, Table 4, " + Id;

    /// <inheritdoc />
    public string? Check(RuleContext context)
    {
        BigDecimal? rate = context.Decimal("/BT-167");
        string? currency = context.Text("/BT-6");
        if (rate is null || currency is null)
        {
            return null;
        }

        int? digits = context.CodeList("iso-4217").MinorUnit(currency);
        if (digits is null)
        {
            return null;
        }

        IReadOnlyDictionary<string, VatParts.Part> partners = VatParts.Partners(context);
        foreach (VatParts.Part converted in VatParts.Accounting(context))
        {
            if (!string.Equals(VatParts.CurrencyOf(context, converted), currency, StringComparison.Ordinal))
            {
                continue;
            }

            if (!partners.TryGetValue(converted.KeyWithRate(true), out VatParts.Part? partner))
            {
                continue;
            }

            BigDecimal? stated = Amount(context, converted);
            BigDecimal? original = Amount(context, partner);
            if (stated is null || original is null)
            {
                continue;
            }

            BigDecimal expected = original.Value.Multiply(rate.Value).SetScale(digits.Value);
            BigDecimal share = Thousandth.Multiply(stated.Value.Abs());
            BigDecimal widest = share.CompareTo(Floor) > 0 ? share : Floor;
            BigDecimal tolerance = widest.CompareTo(BigDecimal.One) < 0 ? widest : BigDecimal.One;
            if (stated.Value.Subtract(expected).Abs().CompareTo(tolerance) <= 0)
            {
                continue;
            }

            return "The VAT breakdown at " + converted.Path + " states the " + _name + " (" + _term + ") "
                + stated.Value.ToPlainString() + " in the VAT accounting currency " + context.Escape(currency)
                + ", and the breakdown at " + partner.Path + " states " + original.Value.ToPlainString()
                + " in the invoice currency, which at the exchange rate (BT-167) " + rate.Value.ToPlainString()
                + " comes to " + expected.ToPlainString() + ".";
        }

        return null;
    }

    private BigDecimal? Amount(RuleContext context, VatParts.Part breakdown) =>
        _tax ? VatParts.TaxAmount(context, breakdown) : breakdown.Amount;
}

/// <summary>BR-68: one exemption reason code is explained by one exemption reason text throughout.</summary>
internal sealed class Br68 : INativeRule
{
    private static readonly string[] Codes =
    {
        "/BG-23/*/BT-121", "/BG-25/*/BG-30/BT-195", "/BG-20/*/BT-174", "/BG-21/*/BT-176",
    };

    private static readonly string[] Reasons =
    {
        "/BG-23/*/BT-120", "/BG-25/*/BG-30/BT-194", "/BG-20/*/BT-173", "/BG-21/*/BT-175",
    };

    /// <inheritdoc />
    public string Id => "BR-68";

    /// <inheritdoc />
    public string DeclaredAs => "Br68";

    /// <inheritdoc />
    public RuleSeverity Severity => RuleSeverity.Fatal;

    /// <inheritdoc />
    public string Context => "/";

    /// <inheritdoc />
    public IReadOnlyList<string> Terms { get; } = new[]
    {
        "BT-120", "BT-121", "BT-173", "BT-174", "BT-175", "BT-176", "BT-194", "BT-195",
    };

    /// <inheritdoc />
    public IReadOnlyList<string> Roots { get; } = Codes.Concat(Reasons).ToList();

    /// <inheritdoc />
    public string Source => "EN 16931-1:2026, 6.4.1, Table 3, BR-68";

    /// <inheritdoc />
    public string? Check(RuleContext context)
    {
        Dictionary<string, (SemanticPath Path, string Text)> seen = new(StringComparer.Ordinal);
        for (int i = 0; i < Codes.Length; i++)
        {
            Dictionary<SemanticPath, string> texts = new();
            foreach (KeyValuePair<SemanticPath, string> read in context.Texts(Reasons[i]))
            {
                texts[Parent(read.Key)] = read.Value;
            }

            foreach (KeyValuePair<SemanticPath, string> read in context.Texts(Codes[i]))
            {
                if (!texts.TryGetValue(Parent(read.Key), out string? text))
                {
                    continue;
                }

                if (!seen.TryGetValue(read.Value, out (SemanticPath Path, string Text) first))
                {
                    seen[read.Value] = (read.Key, text);
                    continue;
                }

                if (!string.Equals(first.Text, text, StringComparison.Ordinal))
                {
                    return "The exemption reason and specification code " + context.Escape(read.Value)
                        + " is explained at " + first.Path + " by one exemption reason text and at "
                        + read.Key + " by another; one code carries one text throughout an invoice.";
                }
            }
        }

        return null;
    }

    private static SemanticPath Parent(SemanticPath path) => path.Prefix(path.Segments.Count - 1);
}

/// <summary>BR-CO-09: a VAT identifier begins with the prefix of the state that issued it.</summary>
internal sealed class VatIdentifierPrefix : INativeRule
{
    private const int PrefixLength = 2;

    private static readonly HashSet<string> BesideTheList = new(StringComparer.Ordinal)
    {
        "EL", "XI", "1A", "EU", "IM", "IN",
    };

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
    public string Source => "EN 16931-1:2026, 6.4.2, Table 4, BR-CO-9";

    /// <inheritdoc />
    public string? Check(RuleContext context)
    {
        CodeList countries = context.CodeList("iso-3166-1");
        foreach ((string path, string name) in Identifiers)
        {
            foreach (KeyValuePair<SemanticPath, string> value in context.Texts(path))
            {
                string identifier = value.Value;
                string prefix = identifier.Length < PrefixLength ? identifier : identifier.Substring(0, PrefixLength);
                if (BesideTheList.Contains(prefix) || countries.Contains(prefix))
                {
                    continue;
                }

                return "The " + name + " at " + value.Key + " is " + context.Escape(identifier)
                    + ", and it begins with " + context.Escape(prefix) + ", which is not a country of"
                    + " the iso-3166-1 snapshot this pack decides against and is none of the prefixes"
                    + " this edition names beside it.";
            }
        }

        return null;
    }
}
