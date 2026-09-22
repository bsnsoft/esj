using System;
using System.Collections.Generic;
using System.Linq;

namespace En16931.SemanticJson.Rules.En16931;

/// <summary>
/// What the VAT rules of the pack read: the invoice lines, the document level allowances and
/// charges and the VAT breakdowns, each as a category, a rate and an amount, and the sums of
/// the first three per category and per category and rate.
/// </summary>
/// <remarks>
/// The fifteen rules that use this read the same four groups of the model over and over, and
/// joining them is a pass over the document each time. The join is an answer about the whole
/// document and does not change while a run lasts, so it is computed by whichever rule asks
/// for it first and handed to every later one through the shared memory of the run.
/// </remarks>
internal static class Vat
{
    private static readonly string[] Lines =
    {
        "/BG-25/*/BG-30/BT-151", "/BG-25/*/BG-30/BT-152", "/BG-25/*/BT-131",
    };

    private static readonly string[] Allowances =
    {
        "/BG-20/*/BT-95", "/BG-20/*/BT-96", "/BG-20/*/BT-92",
    };

    private static readonly string[] Charges =
    {
        "/BG-21/*/BT-102", "/BG-21/*/BT-103", "/BG-21/*/BT-99",
    };

    private static readonly string[] Breakdowns =
    {
        "/BG-23/*/BT-118", "/BG-23/*/BT-119", "/BG-23/*/BT-116",
    };

    /// <summary>The patterns these rules evaluate from the document root.</summary>
    internal static IReadOnlyList<string> Roots { get; } =
        Lines.Concat(Allowances).Concat(Charges).Concat(Breakdowns).ToList();

    /// <summary>One part of an invoice that carries a VAT category: a line, an allowance,
    /// a charge or a breakdown.</summary>
    /// <param name="Path">the group instance it stands in</param>
    /// <param name="Category">the VAT category code, or <c>null</c> where it carries none</param>
    /// <param name="Rate">the VAT rate, or <c>null</c></param>
    /// <param name="Amount">the amount, or <c>null</c></param>
    internal sealed record Item(SemanticPath Path, string? Category, BigDecimal? Rate, BigDecimal? Amount)
    {
        internal bool Is(string code) => string.Equals(code, Category, StringComparison.Ordinal);
    }

    internal static IReadOnlyList<Item> InvoiceLines(RuleContext context) =>
        Join(context, "Vat.lines", 2, Lines);

    internal static IReadOnlyList<Item> DocumentAllowances(RuleContext context) =>
        Join(context, "Vat.allowances", 2, Allowances);

    internal static IReadOnlyList<Item> DocumentCharges(RuleContext context) =>
        Join(context, "Vat.charges", 2, Charges);

    internal static IReadOnlyList<Item> VatBreakdowns(RuleContext context) =>
        Join(context, "Vat.breakdowns", 2, Breakdowns);

    internal static Totals Sums(RuleContext context) =>
        context.Shared("Vat.totals", () => Totals.Of(
            InvoiceLines(context), DocumentCharges(context), DocumentAllowances(context)));

    /// <summary>What the parts of an invoice come to, per VAT category and per rate.</summary>
    internal sealed class Totals
    {
        private readonly Dictionary<string, BigDecimal> _byCategory;
        private readonly Dictionary<string, BigDecimal> _byCategoryAndRate;

        private Totals(Dictionary<string, BigDecimal> byCategory, Dictionary<string, BigDecimal> byCategoryAndRate)
        {
            _byCategory = byCategory;
            _byCategoryAndRate = byCategoryAndRate;
        }

        internal static Totals Of(
            IReadOnlyList<Item> lines, IReadOnlyList<Item> charges, IReadOnlyList<Item> allowances)
        {
            Dictionary<string, BigDecimal> byCategory = new(StringComparer.Ordinal);
            Dictionary<string, BigDecimal> byCategoryAndRate = new(StringComparer.Ordinal);
            Add(byCategory, byCategoryAndRate, lines, false);
            Add(byCategory, byCategoryAndRate, charges, false);
            Add(byCategory, byCategoryAndRate, allowances, true);
            return new Totals(byCategory, byCategoryAndRate);
        }

        /// <summary>Returns what the parts of one category come to, zero where there are none.</summary>
        internal BigDecimal Of(string code) =>
            _byCategory.TryGetValue(code, out BigDecimal total) ? total : BigDecimal.Zero;

        /// <summary>Returns what the parts of one category at one rate come to.</summary>
        internal BigDecimal Of(string code, BigDecimal rate) =>
            _byCategoryAndRate.TryGetValue(Key(code, rate), out BigDecimal total) ? total : BigDecimal.Zero;

        private static void Add(
            Dictionary<string, BigDecimal> byCategory,
            Dictionary<string, BigDecimal> byCategoryAndRate,
            IReadOnlyList<Item> items,
            bool subtract)
        {
            foreach (Item item in items)
            {
                if (item.Category is null || item.Amount is null)
                {
                    continue;
                }

                BigDecimal amount = subtract ? item.Amount.Value.Negate() : item.Amount.Value;
                Merge(byCategory, item.Category, amount);
                if (item.Rate is not null)
                {
                    Merge(byCategoryAndRate, Key(item.Category, item.Rate.Value), amount);
                }
            }
        }

        private static void Merge(Dictionary<string, BigDecimal> totals, string key, BigDecimal amount) =>
            totals[key] = totals.TryGetValue(key, out BigDecimal known) ? known.Add(amount) : amount;

        private static string Key(string code, BigDecimal rate) =>
            code + " @ " + rate.StripTrailingZeros().ToPlainString();
    }

    private static IReadOnlyList<Item> Join(
        RuleContext context, string key, int depth, IReadOnlyList<string> patterns) =>
        context.Shared(key, () => Joined(context, depth, patterns[0], patterns[1], patterns[2]));

    private static IReadOnlyList<Item> Joined(
        RuleContext context, int depth, string categoryPattern, string ratePattern, string amountPattern)
    {
        Dictionary<SemanticPath, string> categories = new();
        List<SemanticPath> order = new();
        foreach (KeyValuePair<SemanticPath, string> read in context.Texts(categoryPattern))
        {
            SemanticPath instance = read.Key.Prefix(depth);
            if (categories.TryAdd(instance, read.Value))
            {
                order.Add(instance);
            }
        }

        Dictionary<SemanticPath, BigDecimal> rates = Numbers(context, ratePattern, depth);
        Dictionary<SemanticPath, BigDecimal> amounts = Numbers(context, amountPattern, depth);
        foreach (SemanticPath instance in amounts.Keys)
        {
            if (!categories.ContainsKey(instance))
            {
                order.Add(instance);
            }
        }

        order.Sort((left, right) => left.CompareTo(right));
        List<Item> items = new(order.Count);
        foreach (SemanticPath instance in order)
        {
            items.Add(new Item(
                instance,
                categories.TryGetValue(instance, out string? category) ? category : null,
                rates.TryGetValue(instance, out BigDecimal rate) ? rate : null,
                amounts.TryGetValue(instance, out BigDecimal amount) ? amount : null));
        }

        return items;
    }

    private static Dictionary<SemanticPath, BigDecimal> Numbers(
        RuleContext context, string pattern, int depth)
    {
        Dictionary<SemanticPath, BigDecimal> found = new();
        foreach (KeyValuePair<SemanticPath, BigDecimal> read in context.Decimals(pattern))
        {
            found[read.Key.Prefix(depth)] = read.Value;
        }

        return found;
    }

    /// <summary>Returns how many parts carry a VAT category code.</summary>
    internal static int Count(IReadOnlyList<Item> items, string code) => items.Count(item => item.Is(code));

    /// <summary>Tells whether any line, allowance or charge carries a VAT category code.</summary>
    internal static bool Used(RuleContext context, string code) =>
        Count(InvoiceLines(context), code) > 0
        || Count(DocumentAllowances(context), code) > 0
        || Count(DocumentCharges(context), code) > 0;
}
