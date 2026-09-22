using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;
using En16931.SemanticJson.Json;
using En16931.SemanticJson.Model;
using Xunit;
using V2017 = En16931.SemanticJson.Typed.V2017;

namespace En16931.SemanticJson.Tests;

/// <summary>
/// The generated read view: that it covers the registry it was generated from, and that it
/// reads a document through the names the registry gives its terms.
/// </summary>
public class TypedViewTests
{
    /// <summary>The 2017 view carries an accessor for every term of the 2017 registry.</summary>
    [Fact]
    public void ViewCoversTheRegistryItWasGeneratedFrom()
    {
        Assert.Equal(
            Registry.ForEdition("2017").Terms.Select(term => term.Id).OrderBy(id => id, StringComparer.Ordinal),
            V2017.Invoice.Covered.OrderBy(id => id, StringComparer.Ordinal));
    }

    /// <summary>
    /// Every further edition this build carries has a view that covers its registry. The view
    /// of a later edition is separable, so it is looked up rather than named: a distribution
    /// without that edition has neither the registry nor the file, and this test then has
    /// nothing to measure rather than nothing to compile.
    /// </summary>
    [Fact]
    public void EveryEditionCarriedHasAViewThatCoversIt()
    {
        foreach (string edition in Registry.Editions())
        {
            Type? view = typeof(Registry).Assembly.GetType("En16931.SemanticJson.Typed.V" + edition + ".Invoice");
            Assert.NotNull(view);
            IReadOnlyList<string> covered = (IReadOnlyList<string>)view!
                .GetProperty("Covered", BindingFlags.Public | BindingFlags.Static)!
                .GetValue(null)!;
            Assert.Equal(
                Registry.ForEdition(edition).Terms.Select(term => term.Id).OrderBy(id => id, StringComparer.Ordinal),
                covered.OrderBy(id => id, StringComparer.Ordinal));
        }
    }

    /// <summary>A view reads the values of a document at the names of their terms.</summary>
    [Fact]
    public void ViewReadsTheDocument()
    {
        V2017.Invoice invoice = new(EsjReader.Strict().Read(Fixtures.Bytes("examples/minimal.esj.json")));

        Assert.Equal("RE-2026-0001", invoice.InvoiceNumber?.Content);
        Assert.Equal(new DateOnly(2026, 1, 15), invoice.IssueDate?.AsDate());
        Assert.Equal("Example GmbH", invoice.Seller.Name?.Content);
        Assert.Equal("DE", invoice.Seller.PostalAddress.CountryCode?.Content);
        Assert.Equal("Muster AG", invoice.Buyer.Name?.Content);

        Assert.Single(invoice.InvoiceLines);
        V2017.InvoiceLine line = invoice.InvoiceLines[0];
        Assert.Equal("1", line.Identifier?.Content);
        Assert.Equal(BigDecimal.Parse("100"), line.NetAmount?.AsDecimal());
        Assert.Equal("Consulting service", line.Item.Name?.Content);
        Assert.Equal("Z", line.Vat.VatCategoryCode?.Content);
    }

    /// <summary>A term the document does not carry reads as nothing, and never raises.</summary>
    [Fact]
    public void ViewAnswersForWhatIsAbsent()
    {
        V2017.Invoice invoice = new(EsjReader.Strict().Read(Fixtures.Bytes("examples/minimal.esj.json")));

        Assert.Null(invoice.PaymentDueDate);
        Assert.Empty(invoice.Notes);
        Assert.False(invoice.HasDelivery);
        Assert.Empty(invoice.InvoiceLines[0].Item.ItemAttributes);
        Assert.Empty(invoice.Seller.Identifiers);
    }

    /// <summary>
    /// The registries and the rule pack this build carries are the files of the repository,
    /// byte for byte: a binding that answered from a copy of its own would answer about
    /// something else.
    /// </summary>
    /// <param name="name">the file</param>
    [Theory]
    [InlineData("model/en16931/2017.json")]
    [InlineData("model/xrechnung/3.0.2.json")]
    [InlineData("rules/en16931/1.3.16/pack.json")]
    [InlineData("rules/en16931/1.3.16/rules/br-co.json")]
    [InlineData("rules/en16931/1.3.16/codelists/untdid-5305/2026-09-19.json")]
    public void EmbeddedFileIsTheFileOfTheRepository(string name)
    {
        using Stream? embedded = typeof(Registry).Assembly.GetManifestResourceStream(name);
        Assert.NotNull(embedded);
        using MemoryStream read = new();
        embedded!.CopyTo(read);
        Assert.Equal(Fixtures.Bytes(name), read.ToArray());
    }
}
