using System;

namespace En16931.SemanticJson;

/// <summary>
/// The semantic data types EN 16931-1, clause 6.5 defines, as a registry writes them.
/// </summary>
public enum SemanticType
{
    /// <summary>Any non-empty string; line breaks are kept.</summary>
    Text,

    /// <summary>An identifier, which may carry a scheme and a scheme version.</summary>
    Identifier,

    /// <summary>A code of the list the model fixes for the term.</summary>
    Code,

    /// <summary>A calendar date with no time of day and no offset.</summary>
    Date,

    /// <summary>A time of day with the offset it is stated in, new in the 2026 edition.</summary>
    Time,

    /// <summary>A monetary amount, without its currency.</summary>
    Amount,

    /// <summary>A unit price, without its currency.</summary>
    UnitPriceAmount,

    /// <summary>A quantity, without its unit of measure.</summary>
    Quantity,

    /// <summary>A percentage.</summary>
    Percentage,

    /// <summary>A reference to a document, which carries no component.</summary>
    DocumentReference,

    /// <summary>An attachment, base64 encoded, with a media type and a file name.</summary>
    BinaryObject,
}

/// <summary>What a semantic data type is called in a registry, and what it admits.</summary>
public static class SemanticTypes
{
    /// <summary>Returns the type a registry writes under that name.</summary>
    /// <param name="datatype">the <c>datatype</c> member of a term</param>
    /// <returns>the type</returns>
    /// <exception cref="EsjFormatException">if no type carries that name</exception>
    public static SemanticType Parse(string datatype) => datatype switch
    {
        "Text" => SemanticType.Text,
        "Identifier" => SemanticType.Identifier,
        "Code" => SemanticType.Code,
        "Date" => SemanticType.Date,
        "Time" => SemanticType.Time,
        "Amount" => SemanticType.Amount,
        "UnitPriceAmount" => SemanticType.UnitPriceAmount,
        "Quantity" => SemanticType.Quantity,
        "Percentage" => SemanticType.Percentage,
        "DocumentReference" => SemanticType.DocumentReference,
        "BinaryObject" => SemanticType.BinaryObject,
        _ => throw new EsjFormatException(null, "the registry names the semantic data type " + datatype
            + ", which EN 16931-1, clause 6.5 does not define"),
    };

    /// <summary>Returns the name a registry writes for a type.</summary>
    /// <param name="type">the type</param>
    /// <returns>the registry spelling</returns>
    public static string RegistryDatatype(this SemanticType type) => type.ToString();

    /// <summary>Tells whether the content of a term of this type is a decimal number.</summary>
    /// <param name="type">the type</param>
    /// <returns>whether the decimal grammar of section 6.4 applies</returns>
    public static bool IsDecimal(this SemanticType type) =>
        type is SemanticType.Amount or SemanticType.UnitPriceAmount
            or SemanticType.Quantity or SemanticType.Percentage;
}
