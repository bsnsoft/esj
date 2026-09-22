// Generated from model/en16931/2026.json by bindings/csharp/tools/generate-typed-view.py.
// The registry is the source of the terms; this file is checked in and a test holds it to it.
using System;
using System.Collections.Generic;

namespace En16931.SemanticJson.Typed.V2026;

/// <summary>
/// The invoice of EN 16931-1:2026, read through the names the registry gives its terms.
/// </summary>
/// <remarks>
/// Every property answers from the document it was handed and none of them writes. A term
/// the document does not carry reads as <c>null</c>, and a repeatable one as an empty list;
/// the content of a value is held to its semantic data type only where a caller asks for it,
/// because deciding that is layer L2 and belongs to the validator.
/// </remarks>
public sealed class Invoice : TypedView
{
    /// <summary>Reads a document.</summary>
    /// <param name="document">the document</param>
    public Invoice(SemanticDocument document)
        : this(document, new PathIndex(document), SemanticPath.Root())
    {
    }

    internal Invoice(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Invoice number (BT-1), 1..1.</summary>
    public SemanticValue? InvoiceNumber => Read("BT-1");

    /// <summary>Invoice issue date (BT-2), 1..1.</summary>
    public SemanticValue? IssueDate => Read("BT-2");

    /// <summary>Invoice issue time (BT-166), 0..1.</summary>
    public SemanticValue? IssueTime => Read("BT-166");

    /// <summary>Invoice type code (BT-3), 1..1.</summary>
    public SemanticValue? TypeCode => Read("BT-3");

    /// <summary>Invoice currency code (BT-5), 1..1.</summary>
    public SemanticValue? CurrencyCode => Read("BT-5");

    /// <summary>VAT accounting currency code (BT-6), 0..1.</summary>
    public SemanticValue? VatAccountingCurrencyCode => Read("BT-6");

    /// <summary>VAT accounting currency exchange rate (BT-167), 0..1.</summary>
    public SemanticValue? VatAccountingCurrencyExchangeRate => Read("BT-167");

    /// <summary>Value added tax point date (BT-7), 0..1.</summary>
    public SemanticValue? TaxPointDate => Read("BT-7");

    /// <summary>Value added tax point date code (BT-8), 0..1.</summary>
    public SemanticValue? TaxPointDateCode => Read("BT-8");

    /// <summary>Payment due date (BT-9), 0..1.</summary>
    public SemanticValue? PaymentDueDate => Read("BT-9");

    /// <summary>Buyer reference (BT-10), 0..n.</summary>
    public IReadOnlyList<SemanticValue> BuyerReference => ReadAll("BT-10");

    /// <summary>Project reference (BT-11), 0..1.</summary>
    public SemanticValue? ProjectReference => Read("BT-11");

    /// <summary>Contract reference (BT-12), 0..1.</summary>
    public SemanticValue? ContractReference => Read("BT-12");

    /// <summary>Purchase order reference (BT-13), 0..1.</summary>
    public SemanticValue? PurchaseOrderReference => Read("BT-13");

    /// <summary>Sales order reference (BT-14), 0..1.</summary>
    public SemanticValue? SalesOrderReference => Read("BT-14");

    /// <summary>Receiving advice reference (BT-15), 0..1.</summary>
    public SemanticValue? ReceivingAdviceReference => Read("BT-15");

    /// <summary>Despatch advice reference (BT-16), 0..1.</summary>
    public SemanticValue? DespatchAdviceReference => Read("BT-16");

    /// <summary>Delivery note reference (BT-197), 0..1.</summary>
    public SemanticValue? DeliveryNoteReference => Read("BT-197");

    /// <summary>Tender or lot reference (BT-17), 0..1.</summary>
    public SemanticValue? TenderOrLotReference => Read("BT-17");

    /// <summary>Invoiced object identifier (BT-18), 0..1.</summary>
    public SemanticValue? InvoicedObjectIdentifier => Read("BT-18");

    /// <summary>Buyer accounting reference (BT-19), 0..1.</summary>
    public SemanticValue? BuyerAccountingReference => Read("BT-19");

    /// <summary>PAYMENT TERMS (BG-33), 0..n.</summary>
    public IReadOnlyList<PaymentTerm> PaymentTerms => GroupList("BG-33",
        (document, index, path) => new PaymentTerm(document, index, path));

    /// <summary>INVOICE NOTE (BG-1), 0..n.</summary>
    public IReadOnlyList<Note> Notes => GroupList("BG-1",
        (document, index, path) => new Note(document, index, path));

    /// <summary>Tells whether the document carries PROCESS CONTROL.</summary>
    public bool HasProcessControl => GroupPresent("BG-2");

    /// <summary>PROCESS CONTROL (BG-2), 1..1.</summary>
    public ProcessControl ProcessControl => new(Document, Index, Step("BG-2"));

    /// <summary>PRECEDING INVOICE REFERENCE (BG-3), 0..n.</summary>
    public IReadOnlyList<PrecedingInvoiceReference> PrecedingInvoiceReferences => GroupList("BG-3",
        (document, index, path) => new PrecedingInvoiceReference(document, index, path));

    /// <summary>Tells whether the document carries SELLER.</summary>
    public bool HasSeller => GroupPresent("BG-4");

    /// <summary>SELLER (BG-4), 1..1.</summary>
    public Seller Seller => new(Document, Index, Step("BG-4"));

    /// <summary>Tells whether the document carries BUYER.</summary>
    public bool HasBuyer => GroupPresent("BG-7");

    /// <summary>BUYER (BG-7), 1..1.</summary>
    public Buyer Buyer => new(Document, Index, Step("BG-7"));

    /// <summary>Tells whether the document carries PAYEE.</summary>
    public bool HasPayee => GroupPresent("BG-10");

    /// <summary>PAYEE (BG-10), 0..1.</summary>
    public Payee Payee => new(Document, Index, Step("BG-10"));

    /// <summary>Tells whether the document carries SELLER TAX REPRESENTATIVE PARTY.</summary>
    public bool HasSellerTaxRepresentative => GroupPresent("BG-11");

    /// <summary>SELLER TAX REPRESENTATIVE PARTY (BG-11), 0..1.</summary>
    public SellerTaxRepresentative SellerTaxRepresentative => new(Document, Index, Step("BG-11"));

    /// <summary>Tells whether the document carries DELIVERY INFORMATION.</summary>
    public bool HasDelivery => GroupPresent("BG-13");

    /// <summary>DELIVERY INFORMATION (BG-13), 0..1.</summary>
    public Delivery Delivery => new(Document, Index, Step("BG-13"));

    /// <summary>Tells whether the document carries PAYMENT INSTRUCTIONS.</summary>
    public bool HasPaymentInstructions => GroupPresent("BG-16");

    /// <summary>PAYMENT INSTRUCTIONS (BG-16), 0..1.</summary>
    public PaymentInstructions PaymentInstructions => new(Document, Index, Step("BG-16"));

    /// <summary>DOCUMENT LEVEL ALLOWANCES (BG-20), 0..n.</summary>
    public IReadOnlyList<DocumentLevelAllowance> DocumentLevelAllowances => GroupList("BG-20",
        (document, index, path) => new DocumentLevelAllowance(document, index, path));

    /// <summary>DOCUMENT LEVEL CHARGES AND TAXES (BG-21), 0..n.</summary>
    public IReadOnlyList<DocumentLevelCharge> DocumentLevelCharges => GroupList("BG-21",
        (document, index, path) => new DocumentLevelCharge(document, index, path));

    /// <summary>Tells whether the document carries DOCUMENT TOTALS.</summary>
    public bool HasDocumentTotals => GroupPresent("BG-22");

    /// <summary>DOCUMENT TOTALS (BG-22), 1..1.</summary>
    public DocumentTotals DocumentTotals => new(Document, Index, Step("BG-22"));

    /// <summary>VAT BREAKDOWN (BG-23), 1..n.</summary>
    public IReadOnlyList<VatBreakdown> VatBreakdowns => GroupList("BG-23",
        (document, index, path) => new VatBreakdown(document, index, path));

    /// <summary>ADDITIONAL SUPPORTING DOCUMENTS (BG-24), 0..n.</summary>
    public IReadOnlyList<AdditionalSupportingDocument> AdditionalSupportingDocuments => GroupList("BG-24",
        (document, index, path) => new AdditionalSupportingDocument(document, index, path));

    /// <summary>INVOICE LINE (BG-25), 1..n.</summary>
    public IReadOnlyList<InvoiceLine> InvoiceLines => GroupList("BG-25",
        (document, index, path) => new InvoiceLine(document, index, path));

    /// <summary>The identifiers of the terms this view carries an accessor for.</summary>
    public static IReadOnlyList<string> Covered { get; } = new[]
    {
        "BT-1",
        "BT-2",
        "BT-166",
        "BT-3",
        "BT-5",
        "BT-6",
        "BT-167",
        "BT-7",
        "BT-8",
        "BT-9",
        "BT-10",
        "BT-11",
        "BT-12",
        "BT-13",
        "BT-14",
        "BT-15",
        "BT-16",
        "BT-197",
        "BT-17",
        "BT-18",
        "BT-19",
        "BG-33",
        "BG-1",
        "BG-2",
        "BG-3",
        "BG-4",
        "BG-7",
        "BG-10",
        "BG-11",
        "BG-13",
        "BG-16",
        "BG-20",
        "BG-21",
        "BG-22",
        "BG-23",
        "BG-24",
        "BG-25",
        "BT-20",
        "BG-35",
        "BG-36",
        "BT-170",
        "BT-171",
        "BT-172",
        "BT-181",
        "BT-182",
        "BT-183",
        "BT-21",
        "BT-22",
        "BT-23",
        "BT-24",
        "BT-25",
        "BT-26",
        "BT-202",
        "BT-27",
        "BT-28",
        "BT-29",
        "BT-30",
        "BT-31",
        "BT-32",
        "BT-33",
        "BT-34",
        "BG-5",
        "BG-6",
        "BT-35",
        "BT-36",
        "BT-162",
        "BT-37",
        "BT-38",
        "BT-39",
        "BT-40",
        "BT-41",
        "BT-42",
        "BT-43",
        "BT-44",
        "BT-45",
        "BT-46",
        "BT-47",
        "BT-48",
        "BT-49",
        "BG-8",
        "BG-9",
        "BT-50",
        "BT-51",
        "BT-163",
        "BT-52",
        "BT-53",
        "BT-54",
        "BT-55",
        "BT-56",
        "BT-57",
        "BT-58",
        "BT-59",
        "BT-60",
        "BT-61",
        "BT-62",
        "BT-63",
        "BG-12",
        "BT-64",
        "BT-65",
        "BT-164",
        "BT-66",
        "BT-67",
        "BT-68",
        "BT-69",
        "BT-70",
        "BT-71",
        "BT-72",
        "BG-14",
        "BG-15",
        "BT-73",
        "BT-74",
        "BT-75",
        "BT-76",
        "BT-165",
        "BT-77",
        "BT-78",
        "BT-79",
        "BT-80",
        "BT-81",
        "BT-82",
        "BT-83",
        "BG-17",
        "BG-18",
        "BG-19",
        "BT-84",
        "BT-85",
        "BT-86",
        "BT-87",
        "BT-88",
        "BT-89",
        "BT-90",
        "BT-91",
        "BT-216",
        "BT-215",
        "BT-92",
        "BT-93",
        "BT-94",
        "BT-95",
        "BT-96",
        "BT-173",
        "BT-174",
        "BT-97",
        "BT-98",
        "BT-213",
        "BT-99",
        "BT-100",
        "BT-101",
        "BT-102",
        "BT-103",
        "BT-175",
        "BT-176",
        "BT-104",
        "BT-105",
        "BT-177",
        "BT-214",
        "BT-106",
        "BT-107",
        "BT-108",
        "BT-109",
        "BT-110",
        "BT-111",
        "BT-112",
        "BT-113",
        "BT-114",
        "BT-115",
        "BG-34",
        "BT-179",
        "BT-180",
        "BT-184",
        "BT-116",
        "BT-117",
        "BT-118",
        "BT-119",
        "BT-120",
        "BT-121",
        "BT-210",
        "BT-122",
        "BT-123",
        "BT-124",
        "BT-125",
        "BT-126",
        "BT-127",
        "BT-128",
        "BT-129",
        "BT-130",
        "BT-131",
        "BT-188",
        "BT-132",
        "BT-200",
        "BT-201",
        "BT-189",
        "BT-190",
        "BT-191",
        "BT-192",
        "BT-198",
        "BT-199",
        "BT-133",
        "BG-39",
        "BG-37",
        "BG-27",
        "BG-28",
        "BG-29",
        "BG-30",
        "BG-31",
        "BT-217",
        "BT-218",
        "BT-219",
        "BT-220",
        "BT-185",
        "BT-186",
        "BT-187",
        "BG-38",
        "BG-26",
        "BT-203",
        "BT-204",
        "BT-205",
        "BT-206",
        "BT-207",
        "BT-208",
        "BT-209",
        "BT-134",
        "BT-135",
        "BT-136",
        "BT-137",
        "BT-138",
        "BT-139",
        "BT-140",
        "BT-141",
        "BT-142",
        "BT-143",
        "BT-144",
        "BT-145",
        "BT-193",
        "BT-146",
        "BT-147",
        "BT-148",
        "BT-149",
        "BT-150",
        "BT-151",
        "BT-152",
        "BT-194",
        "BT-195",
        "BT-153",
        "BT-154",
        "BT-155",
        "BT-156",
        "BT-157",
        "BT-196",
        "BT-158",
        "BT-159",
        "BG-32",
        "BT-211",
        "BT-160",
        "BT-161",
        "BT-212",
    };
}

/// <summary>PAYMENT TERMS (BG-33), as one instance of the group.</summary>
public sealed class PaymentTerm : TypedView
{
    internal PaymentTerm(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Payment term text (BT-20), 0..1.</summary>
    public SemanticValue? PaymentTerms => Read("BT-20");

    /// <summary>EARLY PAYMENT DISCOUNT (BG-35), 0..n.</summary>
    public IReadOnlyList<EarlyPaymentDiscount> EarlyPaymentDiscounts => GroupList("BG-35",
        (document, index, path) => new EarlyPaymentDiscount(document, index, path));

    /// <summary>LATE PAYMENT PENALTY (BG-36), 0..n.</summary>
    public IReadOnlyList<LatePaymentPenalty> LatePaymentPenalties => GroupList("BG-36",
        (document, index, path) => new LatePaymentPenalty(document, index, path));
}

/// <summary>EARLY PAYMENT DISCOUNT (BG-35), as one instance of the group.</summary>
public sealed class EarlyPaymentDiscount : TypedView
{
    internal EarlyPaymentDiscount(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Discount end date (BT-170), 1..1.</summary>
    public SemanticValue? EndDate => Read("BT-170");

    /// <summary>Discount percentage (BT-171), 0..1.</summary>
    public SemanticValue? Percentage => Read("BT-171");

    /// <summary>Discount amount (BT-172), 0..1.</summary>
    public SemanticValue? Amount => Read("BT-172");
}

/// <summary>LATE PAYMENT PENALTY (BG-36), as one instance of the group.</summary>
public sealed class LatePaymentPenalty : TypedView
{
    internal LatePaymentPenalty(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Penalty start date (BT-181), 1..1.</summary>
    public SemanticValue? StartDate => Read("BT-181");

    /// <summary>Penalty yearly interest percentage (BT-182), 0..1.</summary>
    public SemanticValue? YearlyInterestPercentage => Read("BT-182");

    /// <summary>Penalty amount (BT-183), 0..1.</summary>
    public SemanticValue? Amount => Read("BT-183");
}

/// <summary>INVOICE NOTE (BG-1), as one instance of the group.</summary>
public sealed class Note : TypedView
{
    internal Note(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Invoice note subject code (BT-21), 0..1.</summary>
    public SemanticValue? SubjectCode => Read("BT-21");

    /// <summary>Invoice note (BT-22), 1..1.</summary>
    public SemanticValue? NoteValue => Read("BT-22");
}

/// <summary>PROCESS CONTROL (BG-2), as one instance of the group.</summary>
public sealed class ProcessControl : TypedView
{
    internal ProcessControl(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Business process type (BT-23), 0..1.</summary>
    public SemanticValue? BusinessProcessType => Read("BT-23");

    /// <summary>Specification identifier (BT-24), 1..1.</summary>
    public SemanticValue? SpecificationIdentifier => Read("BT-24");
}

/// <summary>PRECEDING INVOICE REFERENCE (BG-3), as one instance of the group.</summary>
public sealed class PrecedingInvoiceReference : TypedView
{
    internal PrecedingInvoiceReference(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Preceding invoice reference (BT-25), 1..1.</summary>
    public SemanticValue? Reference => Read("BT-25");

    /// <summary>Preceding invoice issue date (BT-26), 0..1.</summary>
    public SemanticValue? IssueDate => Read("BT-26");

    /// <summary>Preceding invoice type code (BT-202), 0..1.</summary>
    public SemanticValue? TypeCode => Read("BT-202");
}

/// <summary>SELLER (BG-4), as one instance of the group.</summary>
public sealed class Seller : TypedView
{
    internal Seller(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Seller name (BT-27), 1..1.</summary>
    public SemanticValue? Name => Read("BT-27");

    /// <summary>Seller trading name (BT-28), 0..1.</summary>
    public SemanticValue? TradingName => Read("BT-28");

    /// <summary>Seller identifier (BT-29), 0..n.</summary>
    public IReadOnlyList<SemanticValue> Identifiers => ReadAll("BT-29");

    /// <summary>Seller legal registration identifier (BT-30), 0..1.</summary>
    public SemanticValue? LegalRegistrationIdentifier => Read("BT-30");

    /// <summary>Seller VAT identifier (BT-31), 0..1.</summary>
    public SemanticValue? VatIdentifier => Read("BT-31");

    /// <summary>Seller tax registration identifier (BT-32), 0..1.</summary>
    public SemanticValue? TaxRegistrationIdentifier => Read("BT-32");

    /// <summary>Seller additional legal information (BT-33), 0..1.</summary>
    public SemanticValue? AdditionalLegalInformation => Read("BT-33");

    /// <summary>Seller electronic address (BT-34), 0..1.</summary>
    public SemanticValue? ElectronicAddress => Read("BT-34");

    /// <summary>Tells whether the document carries SELLER POSTAL ADDRESS.</summary>
    public bool HasPostalAddress => GroupPresent("BG-5");

    /// <summary>SELLER POSTAL ADDRESS (BG-5), 1..1.</summary>
    public PostalAddress PostalAddress => new(Document, Index, Step("BG-5"));

    /// <summary>Tells whether the document carries SELLER CONTACT.</summary>
    public bool HasContact => GroupPresent("BG-6");

    /// <summary>SELLER CONTACT (BG-6), 0..1.</summary>
    public Contact Contact => new(Document, Index, Step("BG-6"));
}

/// <summary>SELLER POSTAL ADDRESS (BG-5), as one instance of the group.</summary>
public sealed class PostalAddress : TypedView
{
    internal PostalAddress(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Seller address line 1 (BT-35), 0..1.</summary>
    public SemanticValue? AddressLine1 => Read("BT-35");

    /// <summary>Seller address line 2 (BT-36), 0..1.</summary>
    public SemanticValue? AddressLine2 => Read("BT-36");

    /// <summary>Seller address line 3 (BT-162), 0..1.</summary>
    public SemanticValue? AddressLine3 => Read("BT-162");

    /// <summary>Seller city (BT-37), 0..1.</summary>
    public SemanticValue? City => Read("BT-37");

    /// <summary>Seller post code (BT-38), 0..1.</summary>
    public SemanticValue? PostCode => Read("BT-38");

    /// <summary>Seller country subdivision (BT-39), 0..1.</summary>
    public SemanticValue? CountrySubdivision => Read("BT-39");

    /// <summary>Seller country code (BT-40), 1..1.</summary>
    public SemanticValue? CountryCode => Read("BT-40");
}

/// <summary>SELLER CONTACT (BG-6), as one instance of the group.</summary>
public sealed class Contact : TypedView
{
    internal Contact(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Seller contact point (BT-41), 0..1.</summary>
    public SemanticValue? Name => Read("BT-41");

    /// <summary>Seller contact telephone number (BT-42), 0..1.</summary>
    public SemanticValue? Telephone => Read("BT-42");

    /// <summary>Seller contact email address (BT-43), 0..1.</summary>
    public SemanticValue? Email => Read("BT-43");
}

/// <summary>BUYER (BG-7), as one instance of the group.</summary>
public sealed class Buyer : TypedView
{
    internal Buyer(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Buyer name (BT-44), 1..1.</summary>
    public SemanticValue? Name => Read("BT-44");

    /// <summary>Buyer trading name (BT-45), 0..1.</summary>
    public SemanticValue? TradingName => Read("BT-45");

    /// <summary>Buyer identifier (BT-46), 0..n.</summary>
    public IReadOnlyList<SemanticValue> Identifier => ReadAll("BT-46");

    /// <summary>Buyer legal registration identifier (BT-47), 0..1.</summary>
    public SemanticValue? LegalRegistrationIdentifier => Read("BT-47");

    /// <summary>Buyer VAT identifier (BT-48), 0..1.</summary>
    public SemanticValue? VatIdentifier => Read("BT-48");

    /// <summary>Buyer electronic address (BT-49), 0..1.</summary>
    public SemanticValue? ElectronicAddress => Read("BT-49");

    /// <summary>Tells whether the document carries BUYER POSTAL ADDRESS.</summary>
    public bool HasPostalAddress => GroupPresent("BG-8");

    /// <summary>BUYER POSTAL ADDRESS (BG-8), 1..1.</summary>
    public BuyerPostalAddress PostalAddress => new(Document, Index, Step("BG-8"));

    /// <summary>Tells whether the document carries BUYER CONTACT.</summary>
    public bool HasContact => GroupPresent("BG-9");

    /// <summary>BUYER CONTACT (BG-9), 0..1.</summary>
    public BuyerContact Contact => new(Document, Index, Step("BG-9"));
}

/// <summary>BUYER POSTAL ADDRESS (BG-8), as one instance of the group.</summary>
public sealed class BuyerPostalAddress : TypedView
{
    internal BuyerPostalAddress(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Buyer address line 1 (BT-50), 0..1.</summary>
    public SemanticValue? AddressLine1 => Read("BT-50");

    /// <summary>Buyer address line 2 (BT-51), 0..1.</summary>
    public SemanticValue? AddressLine2 => Read("BT-51");

    /// <summary>Buyer address line 3 (BT-163), 0..1.</summary>
    public SemanticValue? AddressLine3 => Read("BT-163");

    /// <summary>Buyer city (BT-52), 0..1.</summary>
    public SemanticValue? City => Read("BT-52");

    /// <summary>Buyer post code (BT-53), 0..1.</summary>
    public SemanticValue? PostCode => Read("BT-53");

    /// <summary>Buyer country subdivision (BT-54), 0..1.</summary>
    public SemanticValue? CountrySubdivision => Read("BT-54");

    /// <summary>Buyer country code (BT-55), 1..1.</summary>
    public SemanticValue? CountryCode => Read("BT-55");
}

/// <summary>BUYER CONTACT (BG-9), as one instance of the group.</summary>
public sealed class BuyerContact : TypedView
{
    internal BuyerContact(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Buyer contact point (BT-56), 0..1.</summary>
    public SemanticValue? Name => Read("BT-56");

    /// <summary>Buyer contact telephone number (BT-57), 0..1.</summary>
    public SemanticValue? Telephone => Read("BT-57");

    /// <summary>Buyer contact email address (BT-58), 0..1.</summary>
    public SemanticValue? Email => Read("BT-58");
}

/// <summary>PAYEE (BG-10), as one instance of the group.</summary>
public sealed class Payee : TypedView
{
    internal Payee(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Payee name (BT-59), 1..1.</summary>
    public SemanticValue? Name => Read("BT-59");

    /// <summary>Payee identifier (BT-60), 0..1.</summary>
    public SemanticValue? Identifier => Read("BT-60");

    /// <summary>Payee legal registration identifier (BT-61), 0..1.</summary>
    public SemanticValue? LegalRegistrationIdentifier => Read("BT-61");
}

/// <summary>SELLER TAX REPRESENTATIVE PARTY (BG-11), as one instance of the group.</summary>
public sealed class SellerTaxRepresentative : TypedView
{
    internal SellerTaxRepresentative(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Seller tax representative name (BT-62), 1..1.</summary>
    public SemanticValue? Name => Read("BT-62");

    /// <summary>Seller tax representative VAT identifier (BT-63), 1..1.</summary>
    public SemanticValue? VatIdentifier => Read("BT-63");

    /// <summary>Tells whether the document carries SELLER TAX REPRESENTATIVE POSTAL ADDRESS.</summary>
    public bool HasPostalAddress => GroupPresent("BG-12");

    /// <summary>SELLER TAX REPRESENTATIVE POSTAL ADDRESS (BG-12), 1..1.</summary>
    public SellerTaxRepresentativePostalAddress PostalAddress => new(Document, Index, Step("BG-12"));
}

/// <summary>SELLER TAX REPRESENTATIVE POSTAL ADDRESS (BG-12), as one instance of the group.</summary>
public sealed class SellerTaxRepresentativePostalAddress : TypedView
{
    internal SellerTaxRepresentativePostalAddress(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Tax representative address line 1 (BT-64), 0..1.</summary>
    public SemanticValue? AddressLine1 => Read("BT-64");

    /// <summary>Tax representative address line 2 (BT-65), 0..1.</summary>
    public SemanticValue? AddressLine2 => Read("BT-65");

    /// <summary>Tax representative address line 3 (BT-164), 0..1.</summary>
    public SemanticValue? AddressLine3 => Read("BT-164");

    /// <summary>Tax representative city (BT-66), 0..1.</summary>
    public SemanticValue? City => Read("BT-66");

    /// <summary>Tax representative post code (BT-67), 0..1.</summary>
    public SemanticValue? PostCode => Read("BT-67");

    /// <summary>Tax representative country subdivision (BT-68), 0..1.</summary>
    public SemanticValue? CountrySubdivision => Read("BT-68");

    /// <summary>Tax representative country code (BT-69), 1..1.</summary>
    public SemanticValue? CountryCode => Read("BT-69");
}

/// <summary>DELIVERY INFORMATION (BG-13), as one instance of the group.</summary>
public sealed class Delivery : TypedView
{
    internal Delivery(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Deliver to party name (BT-70), 0..1.</summary>
    public SemanticValue? PartyName => Read("BT-70");

    /// <summary>Deliver to location identifier (BT-71), 0..1.</summary>
    public SemanticValue? LocationIdentifier => Read("BT-71");

    /// <summary>Actual delivery date (BT-72), 0..1.</summary>
    public SemanticValue? ActualDeliveryDate => Read("BT-72");

    /// <summary>Tells whether the document carries INVOICING PERIOD.</summary>
    public bool HasInvoicingPeriod => GroupPresent("BG-14");

    /// <summary>INVOICING PERIOD (BG-14), 0..1.</summary>
    public InvoicingPeriod InvoicingPeriod => new(Document, Index, Step("BG-14"));

    /// <summary>Tells whether the document carries DELIVER TO ADDRESS.</summary>
    public bool HasAddress => GroupPresent("BG-15");

    /// <summary>DELIVER TO ADDRESS (BG-15), 0..1.</summary>
    public Address Address => new(Document, Index, Step("BG-15"));
}

/// <summary>INVOICING PERIOD (BG-14), as one instance of the group.</summary>
public sealed class InvoicingPeriod : TypedView
{
    internal InvoicingPeriod(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Invoicing period start date (BT-73), 0..1.</summary>
    public SemanticValue? StartDate => Read("BT-73");

    /// <summary>Invoicing period end date (BT-74), 0..1.</summary>
    public SemanticValue? EndDate => Read("BT-74");
}

/// <summary>DELIVER TO ADDRESS (BG-15), as one instance of the group.</summary>
public sealed class Address : TypedView
{
    internal Address(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Deliver to address line 1 (BT-75), 0..1.</summary>
    public SemanticValue? AddressLine1 => Read("BT-75");

    /// <summary>Deliver to address line 2 (BT-76), 0..1.</summary>
    public SemanticValue? AddressLine2 => Read("BT-76");

    /// <summary>Deliver to address line 3 (BT-165), 0..1.</summary>
    public SemanticValue? AddressLine3 => Read("BT-165");

    /// <summary>Deliver to city (BT-77), 0..1.</summary>
    public SemanticValue? City => Read("BT-77");

    /// <summary>Deliver to post code (BT-78), 0..1.</summary>
    public SemanticValue? PostCode => Read("BT-78");

    /// <summary>Deliver to country subdivision (BT-79), 0..1.</summary>
    public SemanticValue? CountrySubdivision => Read("BT-79");

    /// <summary>Deliver to country code (BT-80), 1..1.</summary>
    public SemanticValue? CountryCode => Read("BT-80");
}

/// <summary>PAYMENT INSTRUCTIONS (BG-16), as one instance of the group.</summary>
public sealed class PaymentInstructions : TypedView
{
    internal PaymentInstructions(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Payment means type code (BT-81), 1..1.</summary>
    public SemanticValue? PaymentMeansTypeCode => Read("BT-81");

    /// <summary>Payment means text (BT-82), 0..1.</summary>
    public SemanticValue? PaymentMeansText => Read("BT-82");

    /// <summary>Remittance information (BT-83), 0..1.</summary>
    public SemanticValue? RemittanceInformation => Read("BT-83");

    /// <summary>CREDIT TRANSFER (BG-17), 0..n.</summary>
    public IReadOnlyList<CreditTransfer> CreditTransfers => GroupList("BG-17",
        (document, index, path) => new CreditTransfer(document, index, path));

    /// <summary>Tells whether the document carries PAYMENT CARD INFORMATION.</summary>
    public bool HasCard => GroupPresent("BG-18");

    /// <summary>PAYMENT CARD INFORMATION (BG-18), 0..1.</summary>
    public Card Card => new(Document, Index, Step("BG-18"));

    /// <summary>Tells whether the document carries DIRECT DEBIT.</summary>
    public bool HasDirectDebit => GroupPresent("BG-19");

    /// <summary>DIRECT DEBIT (BG-19), 0..1.</summary>
    public DirectDebit DirectDebit => new(Document, Index, Step("BG-19"));
}

/// <summary>CREDIT TRANSFER (BG-17), as one instance of the group.</summary>
public sealed class CreditTransfer : TypedView
{
    internal CreditTransfer(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Payment account identifier (BT-84), 1..1.</summary>
    public SemanticValue? AccountIdentifier => Read("BT-84");

    /// <summary>Payment account name (BT-85), 0..1.</summary>
    public SemanticValue? AccountName => Read("BT-85");

    /// <summary>Payment service provider identifier (BT-86), 0..1.</summary>
    public SemanticValue? ServiceProviderIdentifier => Read("BT-86");
}

/// <summary>PAYMENT CARD INFORMATION (BG-18), as one instance of the group.</summary>
public sealed class Card : TypedView
{
    internal Card(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Payment card primary account number (BT-87), 1..1.</summary>
    public SemanticValue? PrimaryAccountNumber => Read("BT-87");

    /// <summary>Payment card holder name (BT-88), 0..1.</summary>
    public SemanticValue? HolderName => Read("BT-88");
}

/// <summary>DIRECT DEBIT (BG-19), as one instance of the group.</summary>
public sealed class DirectDebit : TypedView
{
    internal DirectDebit(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Mandate reference identifier (BT-89), 0..1.</summary>
    public SemanticValue? MandateReferenceIdentifier => Read("BT-89");

    /// <summary>Bank assigned creditor identifier (BT-90), 0..1.</summary>
    public SemanticValue? BankAssignedCreditorIdentifier => Read("BT-90");

    /// <summary>Debited account identifier (BT-91), 0..1.</summary>
    public SemanticValue? DebitedAccountIdentifier => Read("BT-91");

    /// <summary>Debited account name (BT-216), 0..1.</summary>
    public SemanticValue? AccountName => Read("BT-216");

    /// <summary>Debited account payment service provider identifier (BT-215), 0..1.</summary>
    public SemanticValue? ServiceProviderIdentifier => Read("BT-215");
}

/// <summary>DOCUMENT LEVEL ALLOWANCES (BG-20), as one instance of the group.</summary>
public sealed class DocumentLevelAllowance : TypedView
{
    internal DocumentLevelAllowance(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Document level allowance amount (BT-92), 1..1.</summary>
    public SemanticValue? Amount => Read("BT-92");

    /// <summary>Document level allowance base amount (BT-93), 0..1.</summary>
    public SemanticValue? BaseAmount => Read("BT-93");

    /// <summary>Document level allowance percentage (BT-94), 0..1.</summary>
    public SemanticValue? Percentage => Read("BT-94");

    /// <summary>Document level allowance VAT category code (BT-95), 1..1.</summary>
    public SemanticValue? VatCategoryCode => Read("BT-95");

    /// <summary>Document level allowance VAT rate (BT-96), 0..1.</summary>
    public SemanticValue? VatRate => Read("BT-96");

    /// <summary>Document level allowance exemption reason text (BT-173), 0..1.</summary>
    public SemanticValue? ExemptionReasonText => Read("BT-173");

    /// <summary>Document level allowance VAT exemption reason and specification code (BT-174), 0..1.</summary>
    public SemanticValue? ExemptionReasonCode => Read("BT-174");

    /// <summary>Document level allowance reason (BT-97), 0..1.</summary>
    public SemanticValue? Reason => Read("BT-97");

    /// <summary>Document level allowance reason code (BT-98), 0..1.</summary>
    public SemanticValue? ReasonCode => Read("BT-98");

    /// <summary>Document level allowance goods/services code (BT-213), 0..1.</summary>
    public SemanticValue? GoodsServicesCode => Read("BT-213");
}

/// <summary>DOCUMENT LEVEL CHARGES AND TAXES (BG-21), as one instance of the group.</summary>
public sealed class DocumentLevelCharge : TypedView
{
    internal DocumentLevelCharge(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Document level charge or tax amount (BT-99), 1..1.</summary>
    public SemanticValue? Amount => Read("BT-99");

    /// <summary>Document level charge or tax base amount (BT-100), 0..1.</summary>
    public SemanticValue? BaseAmount => Read("BT-100");

    /// <summary>Document level charge or tax percentage (BT-101), 0..1.</summary>
    public SemanticValue? Percentage => Read("BT-101");

    /// <summary>Document level charge or tax VAT category code (BT-102), 1..1.</summary>
    public SemanticValue? VatCategoryCode => Read("BT-102");

    /// <summary>Document level charge or tax VAT rate (BT-103), 0..1.</summary>
    public SemanticValue? VatRate => Read("BT-103");

    /// <summary>Document level charge or tax exemption reason text (BT-175), 0..1.</summary>
    public SemanticValue? ExemptionReasonText => Read("BT-175");

    /// <summary>VAT exemption reason and specification code of the document level charge or tax (BT-176), 0..1.</summary>
    public SemanticValue? ExemptionReasonCode => Read("BT-176");

    /// <summary>Document level charge or tax reason (BT-104), 0..1.</summary>
    public SemanticValue? Reason => Read("BT-104");

    /// <summary>Document level charge reason code (BT-105), 0..1.</summary>
    public SemanticValue? ReasonCode => Read("BT-105");

    /// <summary>Document level non-VAT tax code (BT-177), 0..1.</summary>
    public SemanticValue? NonVatTaxTypeCode => Read("BT-177");

    /// <summary>Document level charge goods/services code (BT-214), 0..1.</summary>
    public SemanticValue? GoodsServicesCode => Read("BT-214");
}

/// <summary>DOCUMENT TOTALS (BG-22), as one instance of the group.</summary>
public sealed class DocumentTotals : TypedView
{
    internal DocumentTotals(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Sum of invoice line net amount (BT-106), 1..1.</summary>
    public SemanticValue? SumOfLineNetAmounts => Read("BT-106");

    /// <summary>Sum of allowances on document level (BT-107), 0..1.</summary>
    public SemanticValue? SumOfAllowances => Read("BT-107");

    /// <summary>Sum of charges and taxes on document level (BT-108), 0..1.</summary>
    public SemanticValue? SumOfCharges => Read("BT-108");

    /// <summary>Invoice total amount without VAT (BT-109), 1..1.</summary>
    public SemanticValue? TotalWithoutVat => Read("BT-109");

    /// <summary>Invoice total VAT amount (BT-110), 0..1.</summary>
    public SemanticValue? TotalVatAmount => Read("BT-110");

    /// <summary>Invoice total VAT amount in VAT accounting currency (BT-111), 0..1.</summary>
    public SemanticValue? TotalVatAmountInAccountingCurrency => Read("BT-111");

    /// <summary>Invoice total amount with VAT (BT-112), 1..1.</summary>
    public SemanticValue? TotalWithVat => Read("BT-112");

    /// <summary>Paid amount (BT-113), 0..1.</summary>
    public SemanticValue? PaidAmount => Read("BT-113");

    /// <summary>Rounding amount (BT-114), 0..1.</summary>
    public SemanticValue? RoundingAmount => Read("BT-114");

    /// <summary>Amount due for payment (BT-115), 1..1.</summary>
    public SemanticValue? AmountDueForPayment => Read("BT-115");

    /// <summary>CHARGES ON BEHALF OF A THIRD PARTY (BG-34), 0..n.</summary>
    public IReadOnlyList<ThirdPartyCharge> ThirdPartyCharges => GroupList("BG-34",
        (document, index, path) => new ThirdPartyCharge(document, index, path));
}

/// <summary>CHARGES ON BEHALF OF A THIRD PARTY (BG-34), as one instance of the group.</summary>
public sealed class ThirdPartyCharge : TypedView
{
    internal ThirdPartyCharge(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Charge amount collected on behalf of a third party (BT-179), 1..1.</summary>
    public SemanticValue? Amount => Read("BT-179");

    /// <summary>Charges specification (BT-180), 1..1.</summary>
    public SemanticValue? Specification => Read("BT-180");
}

/// <summary>VAT BREAKDOWN (BG-23), as one instance of the group.</summary>
public sealed class VatBreakdown : TypedView
{
    internal VatBreakdown(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>VAT breakdown currency (BT-184), 0..1.</summary>
    public SemanticValue? CurrencyCode => Read("BT-184");

    /// <summary>VAT category taxable amount (BT-116), 1..1.</summary>
    public SemanticValue? TaxableAmount => Read("BT-116");

    /// <summary>VAT category tax amount (BT-117), 0..1.</summary>
    public SemanticValue? TaxAmount => Read("BT-117");

    /// <summary>VAT category code (BT-118), 1..1.</summary>
    public SemanticValue? VatCategoryCode => Read("BT-118");

    /// <summary>VAT category rate (BT-119), 0..1.</summary>
    public SemanticValue? VatRate => Read("BT-119");

    /// <summary>VAT exemption reason text (BT-120), 0..1.</summary>
    public SemanticValue? ExemptionReasonText => Read("BT-120");

    /// <summary>VAT exemption reason and specification code (BT-121), 0..1.</summary>
    public SemanticValue? ExemptionReasonCode => Read("BT-121");

    /// <summary>VAT breakdown goods/services code (BT-210), 0..1.</summary>
    public SemanticValue? GoodsServicesCode => Read("BT-210");
}

/// <summary>ADDITIONAL SUPPORTING DOCUMENTS (BG-24), as one instance of the group.</summary>
public sealed class AdditionalSupportingDocument : TypedView
{
    internal AdditionalSupportingDocument(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Supporting document reference (BT-122), 1..1.</summary>
    public SemanticValue? Reference => Read("BT-122");

    /// <summary>Supporting document description (BT-123), 0..1.</summary>
    public SemanticValue? Description => Read("BT-123");

    /// <summary>External document location (BT-124), 0..1.</summary>
    public SemanticValue? ExternalLocation => Read("BT-124");

    /// <summary>Attached document (BT-125), 0..1.</summary>
    public SemanticValue? Attachment => Read("BT-125");
}

/// <summary>INVOICE LINE (BG-25), as one instance of the group.</summary>
public sealed class InvoiceLine : TypedView
{
    internal InvoiceLine(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Invoice line identifier (BT-126), 1..1.</summary>
    public SemanticValue? Identifier => Read("BT-126");

    /// <summary>Invoice line note (BT-127), 0..1.</summary>
    public SemanticValue? Note => Read("BT-127");

    /// <summary>Invoice line object identifier (BT-128), 0..1.</summary>
    public SemanticValue? ObjectIdentifier => Read("BT-128");

    /// <summary>Invoiced quantity (BT-129), 1..1.</summary>
    public SemanticValue? Quantity => Read("BT-129");

    /// <summary>Invoiced quantity unit of measure code (BT-130), 1..1.</summary>
    public SemanticValue? QuantityUnitCode => Read("BT-130");

    /// <summary>Invoice line net amount (BT-131), 1..1.</summary>
    public SemanticValue? NetAmount => Read("BT-131");

    /// <summary>Invoice line purchase order reference (BT-188), 0..1.</summary>
    public SemanticValue? PurchaseOrderReference => Read("BT-188");

    /// <summary>Invoice line purchase order line reference (BT-132), 0..1.</summary>
    public SemanticValue? PurchaseOrderLineReference => Read("BT-132");

    /// <summary>Invoice line sales order reference (BT-200), 0..1.</summary>
    public SemanticValue? SalesOrderReference => Read("BT-200");

    /// <summary>Invoice line sales order line reference (BT-201), 0..1.</summary>
    public SemanticValue? SalesOrderLineReference => Read("BT-201");

    /// <summary>Invoice line despatch advice reference (BT-189), 0..1.</summary>
    public SemanticValue? DespatchAdviceReference => Read("BT-189");

    /// <summary>Invoice line despatch advice line reference (BT-190), 0..1.</summary>
    public SemanticValue? DespatchAdviceLineReference => Read("BT-190");

    /// <summary>Invoice line receiving advice reference (BT-191), 0..1.</summary>
    public SemanticValue? ReceivingAdviceReference => Read("BT-191");

    /// <summary>Invoice line receiving advice line reference (BT-192), 0..1.</summary>
    public SemanticValue? ReceivingAdviceLineReference => Read("BT-192");

    /// <summary>Invoice line delivery note reference (BT-198), 0..1.</summary>
    public SemanticValue? DeliveryNoteReference => Read("BT-198");

    /// <summary>Invoice line delivery note line reference (BT-199), 0..1.</summary>
    public SemanticValue? DeliveryNoteLineReference => Read("BT-199");

    /// <summary>Invoice line Buyer accounting reference (BT-133), 0..1.</summary>
    public SemanticValue? BuyerAccountingReference => Read("BT-133");

    /// <summary>LINE-LEVEL PRECEDING INVOICE REFERENCE (BG-39), 0..n.</summary>
    public IReadOnlyList<InvoiceLinePrecedingInvoiceReference> PrecedingInvoiceReferences => GroupList("BG-39",
        (document, index, path) => new InvoiceLinePrecedingInvoiceReference(document, index, path));

    /// <summary>Tells whether the document carries INVOICE LINE DELIVERY INFORMATION.</summary>
    public bool HasDelivery => GroupPresent("BG-37");

    /// <summary>INVOICE LINE DELIVERY INFORMATION (BG-37), 0..1.</summary>
    public InvoiceLineDelivery Delivery => new(Document, Index, Step("BG-37"));

    /// <summary>INVOICE LINE ALLOWANCES (BG-27), 0..n.</summary>
    public IReadOnlyList<Allowance> Allowances => GroupList("BG-27",
        (document, index, path) => new Allowance(document, index, path));

    /// <summary>INVOICE LINE CHARGES AND TAXES (BG-28), 0..n.</summary>
    public IReadOnlyList<Charge> Charges => GroupList("BG-28",
        (document, index, path) => new Charge(document, index, path));

    /// <summary>Tells whether the document carries PRICE DETAILS.</summary>
    public bool HasPrice => GroupPresent("BG-29");

    /// <summary>PRICE DETAILS (BG-29), 1..1.</summary>
    public Price Price => new(Document, Index, Step("BG-29"));

    /// <summary>Tells whether the document carries LINE VAT INFORMATION.</summary>
    public bool HasVat => GroupPresent("BG-30");

    /// <summary>LINE VAT INFORMATION (BG-30), 1..1.</summary>
    public Vat Vat => new(Document, Index, Step("BG-30"));

    /// <summary>Tells whether the document carries ITEM INFORMATION.</summary>
    public bool HasItem => GroupPresent("BG-31");

    /// <summary>ITEM INFORMATION (BG-31), 1..1.</summary>
    public Item Item => new(Document, Index, Step("BG-31"));
}

/// <summary>LINE-LEVEL PRECEDING INVOICE REFERENCE (BG-39), as one instance of the group.</summary>
public sealed class InvoiceLinePrecedingInvoiceReference : TypedView
{
    internal InvoiceLinePrecedingInvoiceReference(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Line-level preceding invoice reference (BT-217), 1..1.</summary>
    public SemanticValue? Reference => Read("BT-217");

    /// <summary>Line-level preceding invoice issue date (BT-218), 0..1.</summary>
    public SemanticValue? IssueDate => Read("BT-218");

    /// <summary>Line-level preceding invoice type code (BT-219), 0..1.</summary>
    public SemanticValue? TypeCode => Read("BT-219");

    /// <summary>Line-level preceding invoice line reference (BT-220), 0..1.</summary>
    public SemanticValue? LineReference => Read("BT-220");
}

/// <summary>INVOICE LINE DELIVERY INFORMATION (BG-37), as one instance of the group.</summary>
public sealed class InvoiceLineDelivery : TypedView
{
    internal InvoiceLineDelivery(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Invoice line deliver to party name (BT-185), 0..1.</summary>
    public SemanticValue? PartyName => Read("BT-185");

    /// <summary>Invoice line deliver to location identifier (BT-186), 0..1.</summary>
    public SemanticValue? LocationIdentifier => Read("BT-186");

    /// <summary>Invoice line actual delivery date (BT-187), 0..1.</summary>
    public SemanticValue? ActualDeliveryDate => Read("BT-187");

    /// <summary>Tells whether the document carries INVOICE LINE DELIVER TO ADDRESS.</summary>
    public bool HasAddress => GroupPresent("BG-38");

    /// <summary>INVOICE LINE DELIVER TO ADDRESS (BG-38), 0..1.</summary>
    public DeliveryAddress Address => new(Document, Index, Step("BG-38"));

    /// <summary>Tells whether the document carries INVOICE LINE PERIOD.</summary>
    public bool HasPeriod => GroupPresent("BG-26");

    /// <summary>INVOICE LINE PERIOD (BG-26), 0..1.</summary>
    public Period Period => new(Document, Index, Step("BG-26"));
}

/// <summary>INVOICE LINE DELIVER TO ADDRESS (BG-38), as one instance of the group.</summary>
public sealed class DeliveryAddress : TypedView
{
    internal DeliveryAddress(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Invoice line deliver to address line 1 (BT-203), 0..1.</summary>
    public SemanticValue? AddressLine1 => Read("BT-203");

    /// <summary>Invoice line deliver to address line 2 (BT-204), 0..1.</summary>
    public SemanticValue? AddressLine2 => Read("BT-204");

    /// <summary>Invoice line deliver to address line 3 (BT-205), 0..1.</summary>
    public SemanticValue? AddressLine3 => Read("BT-205");

    /// <summary>Invoice line deliver to city (BT-206), 0..1.</summary>
    public SemanticValue? City => Read("BT-206");

    /// <summary>Invoice line deliver to post code (BT-207), 0..1.</summary>
    public SemanticValue? PostCode => Read("BT-207");

    /// <summary>Invoice line deliver to country subdivision (BT-208), 0..1.</summary>
    public SemanticValue? CountrySubdivision => Read("BT-208");

    /// <summary>Invoice line deliver to country code (BT-209), 1..1.</summary>
    public SemanticValue? CountryCode => Read("BT-209");
}

/// <summary>INVOICE LINE PERIOD (BG-26), as one instance of the group.</summary>
public sealed class Period : TypedView
{
    internal Period(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Invoice line period start date (BT-134), 0..1.</summary>
    public SemanticValue? StartDate => Read("BT-134");

    /// <summary>Invoice line period end date (BT-135), 0..1.</summary>
    public SemanticValue? EndDate => Read("BT-135");
}

/// <summary>INVOICE LINE ALLOWANCES (BG-27), as one instance of the group.</summary>
public sealed class Allowance : TypedView
{
    internal Allowance(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Invoice line allowance amount (BT-136), 1..1.</summary>
    public SemanticValue? Amount => Read("BT-136");

    /// <summary>Invoice line allowance base amount (BT-137), 0..1.</summary>
    public SemanticValue? BaseAmount => Read("BT-137");

    /// <summary>Invoice line allowance percentage (BT-138), 0..1.</summary>
    public SemanticValue? Percentage => Read("BT-138");

    /// <summary>Invoice line allowance reason (BT-139), 0..1.</summary>
    public SemanticValue? Reason => Read("BT-139");

    /// <summary>Invoice line allowance reason code (BT-140), 0..1.</summary>
    public SemanticValue? ReasonCode => Read("BT-140");
}

/// <summary>INVOICE LINE CHARGES AND TAXES (BG-28), as one instance of the group.</summary>
public sealed class Charge : TypedView
{
    internal Charge(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Invoice line charge or tax amount (BT-141), 1..1.</summary>
    public SemanticValue? Amount => Read("BT-141");

    /// <summary>Invoice line charge or tax base amount (BT-142), 0..1.</summary>
    public SemanticValue? BaseAmount => Read("BT-142");

    /// <summary>Invoice line charge or tax percentage (BT-143), 0..1.</summary>
    public SemanticValue? Percentage => Read("BT-143");

    /// <summary>Invoice line charge or tax reason (BT-144), 0..1.</summary>
    public SemanticValue? Reason => Read("BT-144");

    /// <summary>Invoice line charge reason code (BT-145), 0..1.</summary>
    public SemanticValue? ReasonCode => Read("BT-145");

    /// <summary>Invoice line-level non-VAT tax type code (BT-193), 0..1.</summary>
    public SemanticValue? NonVatTaxTypeCode => Read("BT-193");
}

/// <summary>PRICE DETAILS (BG-29), as one instance of the group.</summary>
public sealed class Price : TypedView
{
    internal Price(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Item net price (BT-146), 1..1.</summary>
    public SemanticValue? NetPrice => Read("BT-146");

    /// <summary>Item price discount (BT-147), 0..1.</summary>
    public SemanticValue? Discount => Read("BT-147");

    /// <summary>Item gross price (BT-148), 0..1.</summary>
    public SemanticValue? GrossPrice => Read("BT-148");

    /// <summary>Item price base quantity (BT-149), 0..1.</summary>
    public SemanticValue? BaseQuantity => Read("BT-149");

    /// <summary>Item price base quantity unit of measure code (BT-150), 0..1.</summary>
    public SemanticValue? BaseQuantityUnitCode => Read("BT-150");
}

/// <summary>LINE VAT INFORMATION (BG-30), as one instance of the group.</summary>
public sealed class Vat : TypedView
{
    internal Vat(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Invoiced item VAT category code (BT-151), 1..1.</summary>
    public SemanticValue? VatCategoryCode => Read("BT-151");

    /// <summary>Invoiced item VAT rate (BT-152), 0..1.</summary>
    public SemanticValue? VatRate => Read("BT-152");

    /// <summary>Invoiced item exemption reason text (BT-194), 0..1.</summary>
    public SemanticValue? ExemptionReasonText => Read("BT-194");

    /// <summary>Invoiced item VAT exemption reason and specification code (BT-195), 0..1.</summary>
    public SemanticValue? ExemptionReasonCode => Read("BT-195");
}

/// <summary>ITEM INFORMATION (BG-31), as one instance of the group.</summary>
public sealed class Item : TypedView
{
    internal Item(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Item name (BT-153), 1..1.</summary>
    public SemanticValue? Name => Read("BT-153");

    /// <summary>Item description (BT-154), 0..1.</summary>
    public SemanticValue? Description => Read("BT-154");

    /// <summary>Item Seller's identifier (BT-155), 0..1.</summary>
    public SemanticValue? SellerIdentifier => Read("BT-155");

    /// <summary>Item Buyer's identifier (BT-156), 0..1.</summary>
    public SemanticValue? BuyerIdentifier => Read("BT-156");

    /// <summary>Item standard identifier (BT-157), 0..1.</summary>
    public SemanticValue? StandardIdentifier => Read("BT-157");

    /// <summary>Goods/services code (BT-196), 0..1.</summary>
    public SemanticValue? GoodsServicesCode => Read("BT-196");

    /// <summary>Item classification identifier (BT-158), 0..n.</summary>
    public IReadOnlyList<SemanticValue> ClassificationIdentifiers => ReadAll("BT-158");

    /// <summary>Item country of origin (BT-159), 0..1.</summary>
    public SemanticValue? CountryOfOrigin => Read("BT-159");

    /// <summary>ITEM ATTRIBUTE (BG-32), 0..n.</summary>
    public IReadOnlyList<ItemAttribute> ItemAttributes => GroupList("BG-32",
        (document, index, path) => new ItemAttribute(document, index, path));
}

/// <summary>ITEM ATTRIBUTE (BG-32), as one instance of the group.</summary>
public sealed class ItemAttribute : TypedView
{
    internal ItemAttribute(SemanticDocument document, PathIndex index, SemanticPath path)
        : base(document, index, path)
    {
    }

    /// <summary>Item attribute code (BT-211), 0..1.</summary>
    public SemanticValue? Code => Read("BT-211");

    /// <summary>Item attribute name (BT-160), 0..1.</summary>
    public SemanticValue? Name => Read("BT-160");

    /// <summary>Item attribute value (BT-161), 1..1.</summary>
    public SemanticValue? Value => Read("BT-161");

    /// <summary>Item attribute value unit of measure code (BT-212), 0..1.</summary>
    public SemanticValue? ValueUnitCode => Read("BT-212");
}
