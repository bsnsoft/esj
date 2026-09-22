using System;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;

namespace En16931.SemanticJson.Rules.En16931;

/// <summary>
/// How close the two official artefacts of release 1.3.16 ask a VAT category taxable amount
/// to be to the parts of the invoice in that category.
/// </summary>
/// <remarks>
/// Where the two do not ask the same closeness of the same two figures, the rule faults from
/// the wider reading on and warns inside the zone only one artefact grants, naming the syntax
/// whose artefact faults a figure there. It never faults a figure the artefact of the
/// document's own syntax accepts, and a warning decides no verdict.
/// </remarks>
internal enum Tolerance
{
    /// <summary>Neither artefact grants any difference at all.</summary>
    Neither,

    /// <summary>The official CII artefact faults a difference below one unit of the currency.</summary>
    Ubl,

    /// <summary>The official UBL artefact faults a difference below one unit of the currency.</summary>
    Cii,

    /// <summary>The UBL artefact admits the difference and the CII assertion cannot report it.</summary>
    UblCiiCannotReport,
}

/// <summary>
/// The rules that compare the taxable amount of a VAT breakdown with the parts of the invoice
/// in that category (the <c>*-08</c> family).
/// </summary>
internal abstract class CategoryTaxableAmount : INativeRule
{
    private readonly string _code;
    private readonly string _name;
    private readonly bool _byRate;
    private readonly Tolerance _tolerance;

    private protected CategoryTaxableAmount(
        string id, string declaredAs, string code, string name, bool byRate, Tolerance tolerance, string source)
    {
        Id = id;
        DeclaredAs = declaredAs;
        _code = code;
        _name = name;
        _byRate = byRate;
        _tolerance = tolerance;
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
    public IReadOnlyList<string> Terms { get; } = new[]
    {
        "BT-92", "BT-95", "BT-96", "BT-99", "BT-102", "BT-103",
        "BT-116", "BT-118", "BT-119", "BT-131", "BT-151", "BT-152",
    };

    /// <inheritdoc />
    public IReadOnlyList<string> Roots => Vat.Roots;

    /// <inheritdoc />
    public string Source { get; }

    /// <inheritdoc />
    public string? Check(RuleContext context) => Decide(context, true);

    /// <inheritdoc />
    public string? Warn(RuleContext context) =>
        _tolerance == Tolerance.Neither ? null : Decide(context, false);

    private string? Decide(RuleContext context, bool fatal)
    {
        Vat.Totals totals = Vat.Sums(context);
        foreach (Vat.Item breakdown in Vat.VatBreakdowns(context))
        {
            if (!breakdown.Is(_code) || breakdown.Amount is null)
            {
                continue;
            }

            if (_byRate && breakdown.Rate is null)
            {
                continue;
            }

            BigDecimal? rate = breakdown.Rate;
            BigDecimal expected = _byRate ? totals.Of(_code, rate!.Value) : totals.Of(_code);
            BigDecimal apart = breakdown.Amount.Value.Subtract(expected).Abs();
            bool beyond = _tolerance == Tolerance.Neither
                ? apart.Sign != 0
                : apart.CompareTo(BigDecimal.One) >= 0;
            bool inside = !beyond && apart.Sign != 0;
            if (fatal ? !beyond : !inside)
            {
                continue;
            }

            return "The VAT breakdown at " + breakdown.Path + " is categorised \"" + _code + "\" ("
                + _name + ")" + (_byRate ? " at the rate " + rate!.Value.ToPlainString() + " per cent" : string.Empty)
                + " and states the taxable amount (BT-116) " + breakdown.Amount.Value.ToPlainString()
                + "; the invoice lines, document level charges and document level allowances of"
                + " that category come to " + expected.ToPlainString()
                + (fatal
                    ? _tolerance == Tolerance.Neither
                        ? "."
                        : ", which is a unit of the invoice currency or more away."
                    : Warning(_tolerance));
        }

        return null;
    }

    private static string Warning(Tolerance tolerance) => tolerance switch
    {
        Tolerance.Ubl => ", and the official CII artefact of release 1.3.16 faults a difference this small.",
        Tolerance.Cii => ", and the official UBL artefact of release 1.3.16 faults a difference this small.",
        Tolerance.UblCiiCannotReport => ", and the standard asks the two to be equal: the official"
            + " UBL artefact of release 1.3.16 admits a difference this small, and the CII assertion"
            + " of that release cannot report it.",
        _ => string.Empty,
    };
}

/// <summary>
/// The rules that ask for exactly one VAT breakdown of a category the invoice uses (the
/// <c>*-01</c> family).
/// </summary>
internal abstract class ExactlyOneBreakdown : INativeRule
{
    private readonly string _code;
    private readonly string _name;

    private protected ExactlyOneBreakdown(string id, string declaredAs, string code, string name, string source)
    {
        Id = id;
        DeclaredAs = declaredAs;
        _code = code;
        _name = name;
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
    public IReadOnlyList<string> Terms { get; } = new[] { "BT-95", "BT-102", "BT-118", "BT-151" };

    /// <inheritdoc />
    public IReadOnlyList<string> Roots => Vat.Roots;

    /// <inheritdoc />
    public string Source { get; }

    /// <inheritdoc />
    public string? Check(RuleContext context)
    {
        if (!Vat.Used(context, _code))
        {
            return null;
        }

        int breakdowns = Vat.Count(Vat.VatBreakdowns(context), _code);
        if (breakdowns == 1)
        {
            return null;
        }

        return "An invoice line, a document level allowance or a document level charge is"
            + " categorised \"" + _code + "\" (" + _name + "), and the invoice carries "
            + breakdowns.ToString(CultureInfo.InvariantCulture) + " VAT breakdown groups (BG-23)"
            + " with that VAT category code (BT-118) where exactly one belongs.";
    }
}

/// <summary>
/// The rules that ask an identifier of one term to name an identification scheme, which the
/// rule language cannot express because the scheme is a supplementary component and not a
/// value.
/// </summary>
internal abstract class SchemeIdentifier : INativeRule
{
    private readonly string _pattern;
    private readonly string _term;
    private readonly string _name;

    private protected SchemeIdentifier(
        string id, string declaredAs, string pattern, string term, string name, string source)
    {
        Id = id;
        DeclaredAs = declaredAs;
        _pattern = pattern;
        _term = term;
        _name = name;
        Source = source;
        Terms = new[] { term };
        Roots = new[] { pattern };
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
    public IReadOnlyList<string> Terms { get; }

    /// <inheritdoc />
    public IReadOnlyList<string> Roots { get; }

    /// <inheritdoc />
    public string Source { get; }

    /// <inheritdoc />
    public string? Check(RuleContext context)
    {
        foreach (KeyValuePair<SemanticPath, SemanticValue> value in context.Values(_pattern))
        {
            if (value.Value.Scheme is null)
            {
                return "The " + _name + " (" + _term + ") at " + value.Key + " is "
                    + context.Escape(value.Value.Content) + " and names no identification scheme;"
                    + " an identifier of this term is read together with its scheme.";
            }
        }

        return null;
    }
}

/// <summary>
/// The rules that ask the identification scheme of an identifier to be on a code list, which
/// the rule language cannot express for the same reason.
/// </summary>
internal abstract class SchemeInList : INativeRule
{
    /// <summary>One term whose scheme the rule looks at.</summary>
    /// <param name="Pattern">where the values stand</param>
    /// <param name="Term">the identifier of the term</param>
    /// <param name="Name">what the term is called, for the message</param>
    internal sealed record Scheme(string Pattern, string Term, string Name);

    private readonly string _listId;
    private readonly IReadOnlyList<Scheme> _schemes;

    private protected SchemeInList(
        string id, string declaredAs, string listId, string source, IReadOnlyList<Scheme> schemes)
    {
        Id = id;
        DeclaredAs = declaredAs;
        _listId = listId;
        Source = source;
        _schemes = schemes;
        Terms = schemes.Select(scheme => scheme.Term).ToList();
        Roots = schemes.Select(scheme => scheme.Pattern).ToList();
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
    public IReadOnlyList<string> Terms { get; }

    /// <inheritdoc />
    public IReadOnlyList<string> Roots { get; }

    /// <inheritdoc />
    public string Source { get; }

    /// <inheritdoc />
    public string? Check(RuleContext context)
    {
        CodeList list = context.CodeList(_listId);
        foreach (Scheme scheme in _schemes)
        {
            foreach (KeyValuePair<SemanticPath, SemanticValue> value in context.Values(scheme.Pattern))
            {
                string? stated = value.Value.Scheme;
                if (stated is null || list.Contains(stated))
                {
                    continue;
                }

                return "The " + scheme.Name + " (" + scheme.Term + ") at " + value.Key
                    + " names the identification scheme " + context.Escape(stated)
                    + ", which is not on the " + _listId + " snapshot this pack decides against.";
            }
        }

        return null;
    }
}
